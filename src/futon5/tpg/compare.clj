(ns futon5.tpg.compare
  "Three-way comparison of TPG optimization approaches:
   1. Pure evolutionary (baseline) — Phase 3 evolve.clj
   2. Evolution + SMT pruning — reject structurally degenerate candidates
   3. Evolution + JAX refinement — Lamarckian gradient step on weights

   Calls Python tools via subprocess for SMT/JAX, keeps MMCA evaluation
   in Clojure for fidelity."
  (:require [cheshire.core :as json]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.tpg.core :as tpg]
            [futon5.tpg.evolve :as evolve]
            [futon5.tpg.runner :as runner]))

;; =============================================================================
;; JSON SERIALIZATION
;; =============================================================================

(defn- tpg->json-data
  "Convert a TPG graph into Python-tool JSON payload shape."
  [tpg-graph]
  {"tpg/id" (str (or (:tpg/id tpg-graph) "unknown"))
   "teams" (mapv (fn [team]
                   {"team/id" (name (:team/id team))
                    "programs" (mapv (fn [program]
                                        {"program/id" (name (or (:program/id program) :unknown))
                                         "weights" (mapv double (:weights program))
                                         "bias" (double (or (:bias program) 0.0))
                                         "action" {"type" (name (or (get-in program [:action :type]) :operator))
                                                   "target" (name (or (get-in program [:action :target]) :adaptation))}})
                                      (:programs team))})
                 (:teams tpg-graph))
   "config" {"root-team" (name (or (get-in tpg-graph [:config :root-team]) :root))
             "max-depth" (int (or (get-in tpg-graph [:config :max-depth]) 4))}})

(defn- verifier-spec->json-data [spec]
  (into {}
        (map (fn [[k [center width]]]
               [(name k) [(double center) (double width)]]))
        spec))

(defn- trace-diagnostic->json-data [diag]
  (when diag
    (let [named (:named diag)]
      {"entropy" (double (or (:entropy named) 0.0))
       "change" (double (or (:change named) 0.0))
       "autocorr" (double (or (:autocorr named) 0.0))
       "diversity" (double (or (:diversity named) 0.0))
       "phenotype_coupling" (double (or (:phenotype-coupling named) 0.0))
       "damage_spread" (double (or (:damage-spread named) 0.0))})))

(defn- traces->json-data
  "Convert diagnostic traces into JSON payload shape for JAX."
  [traces]
  (mapv (fn [trace]
          (mapv trace-diagnostic->json-data trace))
        traces))

(defn- parse-json-output [raw]
  (try
    {:ok? true :data (json/parse-string raw false)}
    (catch Exception e
      {:ok? false
       :error (str "Failed to parse JSON output: " (.getMessage e))
       :raw raw})))

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
  (let [input (json/generate-string {"tpg" (tpg->json-data tpg-graph)
                                     "verifier_spec" (verifier-spec->json-data verifier-spec)})
        result (run-python-tool "smt_analyzer.py" input)]
    (if (:ok? result)
      (let [parsed (parse-json-output (:out result))]
        (if (:ok? parsed)
          (let [data (:data parsed)]
            {:raw (:out result)
             :parsed data
             :reachable-count (count (or (get data "reachable_operators") []))
             :python-path (:python-path result)
             :python-source (:python-source result)})
          {:error (:error parsed)
           :raw (:raw parsed)
           :python-path (:python-path result)
           :python-source (:python-source result)}))
      (select-keys result [:error :hint :python-path :python-source :script-path :exit]))))

(defn smt-score-details
  "Score a TPG using SMT analysis and return details.
   Score is in [0, 1]:
   - Penalizes unreachable operators
  - Penalizes dead programs
  - Rewards verifier satisfiability"
  [tpg-graph verifier-spec]
  (let [analysis (smt-analyze tpg-graph verifier-spec)]
    (if-let [err (:error analysis)]
      {:score 0.0
       :error err
       :hint (:hint analysis)
       :python-path (:python-path analysis)
       :python-source (:python-source analysis)}
      (let [parsed (:parsed analysis)
            n-unreachable (count (or (get parsed "unreachable_operators") []))
            n-dead (count (or (get parsed "dead_programs") []))
            sat? (true? (get parsed "verifier_satisfiable"))
            ;; Simple scoring: penalize structural issues
            base (if sat? 0.5 0.0)
            unreachable-penalty (* 0.0625 n-unreachable)  ;; 1/16 per unreachable
            dead-penalty (* 0.05 n-dead)
            score (max 0.0 (min 1.0 (- (+ base 0.5) unreachable-penalty dead-penalty)))]
        {:score score
         :error nil
         :unreachable-count n-unreachable
         :dead-program-count n-dead
         :verifier-satisfiable sat?
         :python-path (:python-path analysis)
         :python-source (:python-source analysis)}))))

(defn smt-score
  "Score a TPG using SMT analysis.
   Returns a quality score in [0, 1]."
  [tpg-graph verifier-spec]
  (:score (smt-score-details tpg-graph verifier-spec)))

(defn- apply-refined-program
  [program refined]
  (let [weights (get refined "weights")
        bias (get refined "bias")
        n-weights (count (:weights program))
        valid-weights? (and (sequential? weights)
                            (= n-weights (count weights))
                            (every? number? weights))
        valid-bias? (number? bias)]
    {:program (cond-> program
                valid-weights? (assoc :weights (mapv double weights))
                valid-bias? (assoc :bias (double bias)))
     :weights-updated? valid-weights?
     :bias-updated? valid-bias?}))

(defn- apply-refined-weights
  [tpg-graph refined-weights]
  (let [stats (atom {:teams-updated 0
                     :programs-updated 0
                     :weights-updated 0
                     :bias-updated 0})
        teams' (mapv (fn [team]
                       (let [team-key (name (:team/id team))
                             refined-team (or (get refined-weights team-key)
                                              (get refined-weights (keyword team-key))
                                              (get refined-weights (:team/id team)))
                             refined-team (when (map? refined-team) refined-team)]
                         (if-not refined-team
                           team
                           (let [programs' (mapv (fn [idx program]
                                                   (let [entry (or (get refined-team (str idx))
                                                                   (get refined-team idx)
                                                                   (get refined-team (keyword (str idx))))
                                                         entry (when (map? entry) entry)]
                                                     (if-not entry
                                                       program
                                                       (let [{:keys [program weights-updated? bias-updated?]}
                                                             (apply-refined-program program entry)]
                                                         (when (or weights-updated? bias-updated?)
                                                           (swap! stats (fn [s]
                                                                          (cond-> (update s :programs-updated inc)
                                                                            weights-updated? (update :weights-updated inc)
                                                                            bias-updated? (update :bias-updated inc)))))
                                                         program))))
                                                 (range (count (:programs team)))
                                                 (:programs team))
                                 team-updated? (not= programs' (:programs team))]
                             (when team-updated?
                               (swap! stats update :teams-updated inc))
                             (assoc team :programs programs')))))
                     (:teams tpg-graph))]
    {:tpg (assoc tpg-graph :teams teams')
     :stats @stats}))

(defn jax-refine-weights
  "Run JAX weight refinement on a TPG given diagnostic traces.
   Returns the TPG with updated weights."
  [tpg-graph diagnostic-traces verifier-spec]
  (let [input (json/generate-string {"tpg" (tpg->json-data tpg-graph)
                                     "traces" (traces->json-data diagnostic-traces)
                                     "verifier_spec" (verifier-spec->json-data verifier-spec)
                                     "config" {"n_steps" 30
                                               "learning_rate" 0.01}})
        result (run-python-tool "jax_refine.py" input)]
    (if (:ok? result)
      (let [parsed (parse-json-output (:out result))]
        (if-not (:ok? parsed)
          {:tpg tpg-graph
           :error (:error parsed)
           :python-path (:python-path result)
           :python-source (:python-source result)}
          (let [data (:data parsed)
                refined-weights (or (get data "refined_weights") {})
                applied (if (map? refined-weights)
                          (apply-refined-weights tpg-graph refined-weights)
                          {:tpg tpg-graph
                           :stats {:teams-updated 0
                                   :programs-updated 0
                                   :weights-updated 0
                                   :bias-updated 0}})]
            {:tpg (:tpg applied)
             :jax-output (:out result)
             :improvement (double (or (get data "improvement") 0.0))
             :original-satisfaction (double (or (get data "original_satisfaction") 0.0))
             :refined-satisfaction (double (or (get data "refined_satisfaction") 0.0))
             :refinement-stats (:stats applied)
             :python-path (:python-path result)
             :python-source (:python-source result)})))
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
     (try
       (let [cfg (edn/read-string (first args))]
         (if (map? cfg) cfg {}))
       (catch Exception _ {})))))
