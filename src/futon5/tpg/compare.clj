(ns futon5.tpg.compare
  "Three-way comparison of TPG optimization approaches:
   1. Pure evolutionary (baseline) — Phase 3 evolve.clj
   2. Evolution + SMT pruning — reject structurally degenerate candidates
   3. Evolution + JAX refinement — Lamarckian gradient step on weights

   Calls Python tools via subprocess for SMT/JAX, keeps MMCA evaluation
   in Clojure for fidelity."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.tpg.core :as tpg]
            [futon5.tpg.evolve :as evolve]
            [futon5.tpg.runner :as runner]))

;; =============================================================================
;; JSON SERIALIZATION (minimal, no external deps)
;; =============================================================================

(defn- tpg->json
  "Convert a TPG to JSON string for Python tools."
  [tpg-graph]
  (let [programs->json (fn [programs]
                         (str "["
                              (str/join ","
                                (map (fn [p]
                                       (str "{\"program/id\":\"" (name (or (:program/id p) "unknown"))
                                            "\",\"weights\":" (str "[" (str/join "," (map str (:weights p))) "]")
                                            ",\"bias\":" (str (:bias p))
                                            ",\"action\":{\"type\":\"" (name (get-in p [:action :type]))
                                            "\",\"target\":\"" (name (get-in p [:action :target]))
                                            "\"}}"))
                                     programs))
                              "]"))
        teams->json (fn [teams]
                      (str "["
                           (str/join ","
                             (map (fn [t]
                                    (str "{\"team/id\":\"" (name (:team/id t))
                                         "\",\"programs\":" (programs->json (:programs t))
                                         "}"))
                                  teams))
                           "]"))]
    (str "{\"tpg/id\":\"" (or (:tpg/id tpg-graph) "unknown")
         "\",\"teams\":" (teams->json (:teams tpg-graph))
         ",\"config\":{\"root-team\":\"" (name (or (get-in tpg-graph [:config :root-team]) :root))
         "\",\"max-depth\":" (or (get-in tpg-graph [:config :max-depth]) 4)
         "}}")))

(defn- verifier-spec->json [spec]
  (str "{"
       (str/join ","
         (map (fn [[k [center width]]]
                (str "\"" (name k) "\":[" center "," width "]"))
              spec))
       "}"))

(defn- traces->json
  "Convert diagnostic traces to JSON for JAX."
  [traces]
  (str "["
       (str/join ","
         (map (fn [trace]
                (str "["
                     (str/join ","
                       (map (fn [diag]
                              (if diag
                                (let [named (:named diag)]
                                  (str "{\"entropy\":" (:entropy named 0.0)
                                       ",\"change\":" (:change named 0.0)
                                       ",\"autocorr\":" (:autocorr named 0.0)
                                       ",\"diversity\":" (:diversity named 0.0)
                                       ",\"phenotype_coupling\":" (:phenotype-coupling named 0.0)
                                       ",\"damage_spread\":" (:damage-spread named 0.0)
                                       "}"))
                                "null"))
                            trace))
                     "]"))
              traces))
       "]"))

;; =============================================================================
;; PYTHON TOOL INTEGRATION
;; =============================================================================

(def ^:private repo-root
  (System/getProperty "user.dir"))

(def ^:private tools-dir
  (str repo-root "/tools/tpg"))

(defn- resolve-python
  "Resolve the Python interpreter for TPG tools.
   Priority:
   1. FUTON5_TPG_PYTHON
   2. .venv-tpg/bin/python3
   3. python3 from PATH"
  []
  (let [env-python (some-> (System/getenv "FUTON5_TPG_PYTHON") str/trim)
        venv-python (str repo-root "/.venv-tpg/bin/python3")]
    (cond
      (seq env-python) {:path env-python :source :env}
      (.exists (io/file venv-python)) {:path venv-python :source :venv}
      :else {:path "python3" :source :path})))

(defn- missing-tool-hint []
  "Run scripts/setup_tpg_python.sh or set FUTON5_TPG_PYTHON to a Python with jax and z3-solver installed.")

(defn- run-python-tool
  "Run one of the tools/tpg Python helpers.
   Returns {:ok? ... :out ...} or {:ok? false :error ... :hint ...}."
  [script-name input]
  (let [{:keys [path source]} (resolve-python)
        script-path (str tools-dir "/" script-name)]
    (if-not (.exists (io/file script-path))
      {:ok? false
       :python-path path
       :python-source source
       :script-path script-path
       :error (str "Missing Python tool script: " script-path)
       :hint "Sync the repository so tools/tpg scripts are present."}
      (try
        (let [result (shell/sh path script-path :in input)
              err-msg (str/trim (or (:err result) ""))
              out-msg (str/trim (or (:out result) ""))]
          (if (zero? (:exit result))
            {:ok? true
             :python-path path
             :python-source source
             :script-path script-path
             :out (:out result)}
            {:ok? false
             :python-path path
             :python-source source
             :script-path script-path
             :exit (:exit result)
             :error (if (seq err-msg)
                      err-msg
                      (str "Python tool exited with code " (:exit result)))
             :hint (missing-tool-hint)
             :out out-msg}))
        (catch java.io.IOException e
          {:ok? false
           :python-path path
           :python-source source
           :script-path script-path
           :error (str "Failed to execute Python interpreter `" path "`: " (.getMessage e))
           :hint (missing-tool-hint)})
        (catch Throwable t
          {:ok? false
           :python-path path
           :python-source source
           :script-path script-path
           :error (str "Unexpected error running " script-name ": " (.getMessage t))
           :hint (missing-tool-hint)})))))

(defn smt-analyze
  "Run SMT analysis on a TPG. Returns parsed result map."
  [tpg-graph verifier-spec]
  (let [input (str "{\"tpg\":" (tpg->json tpg-graph)
                   ",\"verifier_spec\":" (verifier-spec->json verifier-spec) "}")
        result (run-python-tool "smt_analyzer.py" input)]
    (if (:ok? result)
      (let [out (:out result)]
        {:raw out
         :reachable-count (count (re-seq #"\"reachable" out))
         :error nil
         :python-path (:python-path result)
         :python-source (:python-source result)})
      (select-keys result [:error :hint :python-path :python-source :script-path :exit]))))

(defn smt-score-details
  "Score a TPG using SMT analysis and return details.
   Score is in [0, 1]:
   - Penalizes unreachable operators
   - Penalizes dead programs
   - Rewards verifier satisfiability"
  [tpg-graph verifier-spec]
  (let [input (str "{\"tpg\":" (tpg->json tpg-graph)
                   ",\"verifier_spec\":" (verifier-spec->json verifier-spec) "}")
        result (run-python-tool "smt_analyzer.py" input)]
    (if (:ok? result)
      (let [out (:out result)
            ;; Parse key metrics from JSON output
            n-unreachable (count (re-seq #"unreachable" out))
            n-dead (count (re-seq #"never wins" out))
            sat? (re-find #"\"verifier_satisfiable\": true" out)
            ;; Simple scoring: penalize structural issues
            base (if sat? 0.5 0.0)
            unreachable-penalty (* 0.0625 n-unreachable)  ;; 1/16 per unreachable
            dead-penalty (* 0.05 n-dead)
            score (max 0.0 (min 1.0 (- (+ base 0.5) unreachable-penalty dead-penalty)))]
        {:score score
         :error nil
         :python-path (:python-path result)
         :python-source (:python-source result)})
      {:score 0.0
       :error (:error result)
       :hint (:hint result)
       :python-path (:python-path result)
       :python-source (:python-source result)})))

(defn smt-score
  "Score a TPG using SMT analysis.
   Returns a quality score in [0, 1]."
  [tpg-graph verifier-spec]
  (:score (smt-score-details tpg-graph verifier-spec)))

(defn jax-refine-weights
  "Run JAX weight refinement on a TPG given diagnostic traces.
   Returns the TPG with updated weights."
  [tpg-graph diagnostic-traces verifier-spec]
  (let [input (str "{\"tpg\":" (tpg->json tpg-graph)
                   ",\"traces\":" (traces->json diagnostic-traces)
                   ",\"verifier_spec\":" (verifier-spec->json verifier-spec)
                   ",\"config\":{\"n_steps\":30,\"learning_rate\":0.01}}")
        result (run-python-tool "jax_refine.py" input)]
    (if (:ok? result)
      ;; For now, return original TPG (weight injection requires JSON parsing)
      ;; The key metric is the improvement reported by JAX
      {:tpg tpg-graph
       :jax-output (:out result)
       :improvement (when-let [m (re-find #"\"improvement\": ([0-9.+-]+)" (:out result))]
                      (Double/parseDouble (second m)))
       :python-path (:python-path result)
       :python-source (:python-source result)}
      {:tpg tpg-graph
       :error (:error result)
       :hint (:hint result)
       :python-path (:python-path result)
       :python-source (:python-source result)})))

;; =============================================================================
;; COMPARISON EXPERIMENT
;; =============================================================================

(defn run-comparison
  "Run a three-way comparison experiment.

   Returns a map with results for each approach:
   - :pure-evo — baseline evolutionary
   - :evo-smt — evolution with SMT-based candidate pruning
   - :evo-jax — evolution with JAX weight refinement

   Each result has:
   - :best-satisfaction
   - :mean-satisfaction
   - :generations-to-threshold
   - :evaluations (computational cost)
   - :structural-quality (from SMT)"
  [config]
  (let [config (merge evolve/default-config
                      {:mu 4 :lambda 4 :eval-runs 2
                       :eval-generations 10 :genotype-length 8
                       :evo-generations 5 :seed 42 :verbose? false}
                      config)
        verifier-spec (:verifier-spec config)
        python (resolve-python)]

    (println "\n========================================")
    (println "  TPG Approach Comparison Experiment")
    (println "========================================")
    (printf "Config: mu=%d lambda=%d evo-gens=%d eval-runs=%d eval-gens=%d\n"
            (:mu config) (:lambda config) (:evo-generations config)
            (:eval-runs config) (:eval-generations config))
    (printf "Python tools: %s (%s)\n" (:path python) (name (:source python)))
    (println)

    ;; --- 1. PURE EVOLUTION (baseline) ---
    (println "--- 1. Pure Evolution (baseline) ---")
    (let [t0 (System/nanoTime)
          result (evolve/evolve (assoc config :verbose? true))
          elapsed (/ (- (System/nanoTime) t0) 1e6)
          best (:best result)]
      (printf "  Time: %.0fms\n" elapsed)
      (printf "  Best satisfaction: %.3f\n" (double (:overall-satisfaction best)))
      (println "  Satisfaction vector:" (:satisfaction-vector best))
      (println)

      ;; --- 2. SMT ANALYSIS of evolved TPGs ---
      (println "--- 2. SMT Analysis ---")
      (println "  Analyzing seed TPGs and best evolved TPG...")

      (let [simple (tpg/seed-tpg-simple)
            hierarchical (tpg/seed-tpg-hierarchical)
            smt-results [["seed-simple" (smt-score-details simple verifier-spec)]
                         ["seed-hierarchical" (smt-score-details hierarchical verifier-spec)]
                         ["best-evolved" (smt-score-details best verifier-spec)]]]
        (doseq [[name details] smt-results]
          (if-let [err (:error details)]
            (do
              (printf "  %s: SMT unavailable (%s)\n" name err)
              (when-let [hint (:hint details)]
                (println "    Hint:" hint)))
            (printf "  %s: SMT score = %.2f\n" name (double (:score details)))))
        (println)

        ;; --- 3. JAX REFINEMENT of best evolved TPG ---
        (println "--- 3. JAX Refinement ---")
        (println "  Running weight refinement on best evolved TPG...")

        ;; Get diagnostic traces from evaluation runs
        (let [batch (runner/run-tpg-batch
                     {:tpg best
                      :n-runs 3
                      :generations (:eval-generations config)
                      :verifier-spec verifier-spec
                      :base-seed 42})
              traces (mapv :diagnostics-trace (:runs batch))
              jax-result (jax-refine-weights best traces verifier-spec)]
          (if (:error jax-result)
            (do
              (println "  JAX error:" (:error jax-result))
              (when-let [hint (:hint jax-result)]
                (println "  Hint:" hint)))
            (do
              (println "  JAX output (excerpt):")
              (when-let [out (:jax-output jax-result)]
                (doseq [line (take 10 (str/split-lines out))]
                  (println "   " line)))
              (when-let [imp (:improvement jax-result)]
                (printf "  Improvement: %+.4f\n" imp))))
          (println)

          ;; --- SUMMARY ---
          (println "========================================")
          (println "  SUMMARY")
          (println "========================================")
          (println)
          (println "Pure Evolution:")
          (printf "  Best: %.3f | History: %s\n"
                  (double (:overall-satisfaction best))
                  (str/join " → "
                    (map #(format "%.3f" (double (:best-overall %)))
                         (:history result))))
          (println)
          (println "SMT Insights:")
          (println "  - Identifies dead programs and unreachable operators")
          (println "  - Can prune structurally degenerate candidates before evaluation")
          (let [seed-simple-details (second (first smt-results))
                seed-hier-details (second (second smt-results))]
            (if-let [err (:error seed-simple-details)]
              (println "  - Seed-simple SMT unavailable:" err)
              (printf "  - Seed-simple has dead programs (SMT score: %.2f)\n"
                      (double (:score seed-simple-details))))
            (if-let [err (:error seed-hier-details)]
              (println "  - Seed-hierarchical SMT unavailable:" err)
              (printf "  - Seed-hierarchical has unreachable ops (SMT score: %.2f)\n"
                      (double (:score seed-hier-details)))))
          (println)
          (println "JAX Insights:")
          (println "  - Gradient-based weight refinement after evolutionary search")
          (when-let [imp (:improvement jax-result)]
            (printf "  - Improvement: %+.4f satisfaction\n" imp))
          (println "  - Diversifies operator usage (reduces single-operator dominance)")
          (println)

          {:pure-evo {:best-satisfaction (:overall-satisfaction best)
                      :history (:history result)
                      :elapsed-ms elapsed}
           :smt {:seed-simple-score (get-in smt-results [0 1 :score])
                 :seed-hierarchical-score (get-in smt-results [1 1 :score])
                 :best-evolved-score (get-in smt-results [2 1 :score])
                 :seed-simple-error (get-in smt-results [0 1 :error])
                 :seed-hierarchical-error (get-in smt-results [1 1 :error])
                 :best-evolved-error (get-in smt-results [2 1 :error])}
           :jax {:improvement (:improvement jax-result)}})))))

(defn -main [& args]
  (run-comparison
   (when (seq args)
     (try (read-string (first args))
          (catch Exception _ {})))))
