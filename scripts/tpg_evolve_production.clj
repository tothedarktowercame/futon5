#!/usr/bin/env bb
;; Production TPG Evolution with Checkpointing
;;
;; Runs scaled-up TPG evolution (16+16 pop, 50+ gens) with:
;; - Coupling-aware fitness (mean-coupling, coupling-cv, hotspot-fraction)
;; - Wiring operators (addself, msb, bit5)
;; - Evolved temporal schedules
;; - Checkpoint save/resume for long runs
;;
;; Usage:
;;   bb -cp src:resources scripts/tpg_evolve_production.clj
;;   bb -cp src:resources scripts/tpg_evolve_production.clj config.edn
;;
;; Checkpoint files are saved to :checkpoint-dir every :checkpoint-every generations.
;; On restart, the latest checkpoint is loaded automatically.

(require '[futon5.ca.core :as ca]
         '[futon5.tpg.core :as tpg]
         '[futon5.tpg.runner :as runner]
         '[futon5.tpg.evolve :as evolve]
         '[futon5.tpg.verifiers :as verifiers]
         '[futon5.tpg.diagnostics :as diag]
         '[futon5.wiring.runtime :as wrt]
         '[futon5.mmca.bitplane-analysis :as bitplane]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

;; =============================================================================
;; CONFIGURATION
;; =============================================================================

(def default-production-config
  {:mu 16
   :lambda 16
   :eval-runs 5
   :eval-generations 50
   :genotype-length 64
   :evo-generations 50
   :seed 352362012
   :verifier-spec {:entropy      [0.6 0.35]
                   :change       [0.2 0.2]
                   :autocorr     [0.6 0.3]
                   :diversity    [0.4 0.3]
                   :mean-coupling [0.08 0.06]
                   :coupling-cv  [0.5 0.3]}
   :spatial-coupling? false       ;; set true for hotspot-fraction fitness
   :coupling-stability? false     ;; set true for cross-run stability fitness
   :checkpoint-dir "out/tpg-evo-production"
   :checkpoint-every 5
   :verbose? true
   :verbose-eval? true
   :verbose-eval-runs? true
   :verbose-eval-gen-every 1})

;; Load user config from CLI arg if provided
(def user-config
  (when-let [path (first *command-line-args*)]
    (try
      (read-string (slurp path))
      (catch Exception e
        (println "Warning: could not read config from" path ":" (.getMessage e))
        nil))))

(def config (merge default-production-config user-config))

;; Load wiring operators
(println "\n======================================================================")
(println "  TPG PRODUCTION EVOLUTION")
(println "======================================================================\n")

(println "Loading wiring operators...")
(def wiring-operators
  (runner/load-wiring-operators
   {:wiring-addself  "data/wiring-rules/hybrid-110-addself.edn"
    :wiring-xorself "data/wiring-rules/hybrid-110-xorself.edn"
    :wiring-msb      "data/wiring-rules/hybrid-110-msb.edn"
    :wiring-bit5     "data/wiring-rules/hybrid-110-bit5.edn"}))
(println (str "  Loaded: " (vec (keys wiring-operators))))

(def config (assoc config :wiring-operators wiring-operators))

;; =============================================================================
;; CHECKPOINT I/O
;; =============================================================================

(defn ensure-dir [dir]
  (let [f (io/file dir)]
    (when-not (.exists f)
      (.mkdirs f))))

(defn checkpoint-path [dir gen]
  (str dir "/checkpoint-gen-" (format "%04d" gen) ".edn"))

(defn save-checkpoint [dir gen population history config-seed]
  (ensure-dir dir)
  (let [path (checkpoint-path dir gen)
        ;; Strip transient data to keep checkpoint small
        strip-tpg (fn [tpg]
                    (dissoc tpg :eval-runs :pareto-rank))
        data {:generation gen
              :population (mapv strip-tpg population)
              :history history
              :config-seed config-seed}]
    (spit path (pr-str data))
    (printf "  [checkpoint] Saved gen %d to %s (%d bytes)\n"
            gen path (.length (io/file path)))
    (flush)))

(defn find-latest-checkpoint [dir]
  (let [f (io/file dir)]
    (when (.exists f)
      (let [files (->> (.listFiles f)
                       (filter #(str/starts-with? (.getName %) "checkpoint-gen-"))
                       (filter #(str/ends-with? (.getName %) ".edn"))
                       (sort-by #(.getName %) #(compare %2 %1)))]
        (first files)))))

(defn load-checkpoint [file]
  (println (str "  [checkpoint] Loading " (.getPath file)))
  (let [data (read-string (slurp file))]
    (printf "  [checkpoint] Resuming from generation %d (%d individuals)\n"
            (:generation data) (count (:population data)))
    (flush)
    data))

;; =============================================================================
;; MAIN LOOP
;; =============================================================================

(let [{:keys [mu lambda evo-generations seed checkpoint-dir checkpoint-every
              verbose-eval? verbose-eval-runs? verbose-eval-gen-every]} config
      config-for-evolve (merge evolve/default-config config)

      ;; Check for existing checkpoint
      latest (find-latest-checkpoint checkpoint-dir)
      checkpoint (when latest (load-checkpoint latest))

      ;; Resume or start fresh
      start-gen (if checkpoint (inc (:generation checkpoint)) 0)
      history (or (:history checkpoint) [])
      config-seed (or seed 42)

      ;; RNG: deterministic per generation
      make-rng (fn [gen] (java.util.Random. (long (+ config-seed (* gen 1000000)))))

      ;; Initialize population
      population
      (if checkpoint
        ;; Re-evaluate loaded population (satisfaction vectors may differ with new config)
        (let [rng (make-rng start-gen)]
          (println "  Re-evaluating loaded population...")
          (flush)
          (mapv (fn [idx individual]
                  (let [_ (when verbose-eval?
                            (if verbose-eval-runs?
                              (printf "    [resume] %d/%d%n"
                                      (inc idx) (count (:population checkpoint)))
                              (printf "    [resume] %d/%d ... "
                                      (inc idx) (count (:population checkpoint))))
                            (flush))
                        t0 (System/currentTimeMillis)
                        evaluated (evolve/evaluate-tpg individual config-for-evolve rng)
                        elapsed (/ (- (System/currentTimeMillis) t0) 1000.0)]
                    (when verbose-eval?
                      (if verbose-eval-runs?
                        (printf "    [resume] %d/%d done in %.1fs%n"
                                (inc idx) (count (:population checkpoint)) elapsed)
                        (printf "done in %.1fs%n" elapsed))
                      (flush))
                    evaluated))
                (range (count (:population checkpoint)))
                (:population checkpoint)))
        ;; Fresh start
        (let [rng (make-rng 0)
              pop (evolve/initial-population rng config-for-evolve)]
          (println (str "\nPopulation: " mu " parents + " lambda " offspring"))
          (println (str "Eval: " (:eval-runs config-for-evolve) " runs x "
                        (:eval-generations config-for-evolve) " generations"))
          (println (str "Evolution: " evo-generations " generations"))
          (println (str "Checkpoint: every " checkpoint-every " generations to " checkpoint-dir))
          (when verbose-eval?
            (println "Per-candidate eval progress: enabled"))
          (when verbose-eval-runs?
            (println (str "Inner eval heartbeat: every " verbose-eval-gen-every " generations")))
          (println)
          (if verbose-eval?
            (println "Evaluating initial population:")
            (print "Evaluating initial population... "))
          (flush)
          (let [evaluated (mapv (fn [idx individual]
                                  (let [_ (when verbose-eval?
                                            (if verbose-eval-runs?
                                              (printf "    [init] %d/%d%n" (inc idx) (count pop))
                                              (printf "    [init] %d/%d ... "
                                                      (inc idx) (count pop)))
                                            (flush))
                                        t0 (System/currentTimeMillis)
                                        result (evolve/evaluate-tpg individual config-for-evolve rng)
                                        elapsed (/ (- (System/currentTimeMillis) t0) 1000.0)]
                                    (when verbose-eval?
                                      (if verbose-eval-runs?
                                        (printf "    [init] %d/%d done in %.1fs%n"
                                                (inc idx) (count pop) elapsed)
                                        (printf "done in %.1fs%n" elapsed))
                                      (flush))
                                    result))
                                (range (count pop))
                                pop)]
            (when-not verbose-eval?
              (println "done."))
            evaluated)))]

  (println)
  (printf "%-6s %-8s %-8s %-6s %-6s %-10s %-12s %s\n"
          "Gen" "Best" "Mean" "Front" "Teams" "Time" "ETA" "Sched?")
  (println "------------------------------------------------------------------------")
  (flush)

  ;; Evolution loop
  (loop [population population
         gen start-gen
         history history
         gen-times []]
    (if (>= gen evo-generations)
      ;; Done — save final results
      (let [best (first (sort-by (comp - :overall-satisfaction) population))]
        (println)
        (println "======================================================================")
        (println "  EVOLUTION COMPLETE")
        (println "======================================================================")
        (printf "\nBest overall satisfaction: %.3f\n" (double (:overall-satisfaction best)))
        (println "Satisfaction vector:")
        (doseq [[k v] (sort-by key (:satisfaction-vector best))]
          (printf "  %-20s %.3f\n" (name k) (double v)))
        (printf "Teams: %d  Programs: %d\n"
                (count (:teams best))
                (reduce + (map #(count (:programs %)) (:teams best))))
        (when (:temporal-schedule best)
          (println "Temporal schedule:")
          (doseq [{:keys [operator steps]} (:temporal-schedule best)]
            (printf "  %-20s %d steps\n" (name operator) steps)))

        ;; Save best TPG
        (ensure-dir checkpoint-dir)
        (let [best-path (str checkpoint-dir "/best-tpg.edn")]
          (spit best-path (pr-str (dissoc best :eval-runs :pareto-rank)))
          (println (str "\nBest TPG saved to " best-path)))

        ;; Save final checkpoint
        (save-checkpoint checkpoint-dir gen population history config-seed)

        ;; Summary stats
        (when (seq gen-times)
          (let [mean-t (/ (reduce + 0.0 gen-times) (count gen-times))]
            (printf "\nMean time per generation: %.1fs\n" mean-t)
            (printf "Total evolution time: %.1fmin\n"
                    (/ (reduce + 0.0 gen-times) 60.0))))
        (flush))

      ;; Run one generation
      (let [t0 (System/currentTimeMillis)
            rng (make-rng gen)
            {:keys [population gen-record]}
            (evolve/evolve-one-generation population gen config-for-evolve rng)
            t1 (System/currentTimeMillis)
            elapsed-s (/ (- t1 t0) 1000.0)
            gen-times' (conj gen-times elapsed-s)
            remaining (- evo-generations gen 1)
            mean-t (/ (reduce + 0.0 gen-times') (count gen-times'))
            eta-min (/ (* mean-t remaining) 60.0)
            best (first (sort-by (comp - :overall-satisfaction) population))
            has-sched? (boolean (:temporal-schedule best))
            history' (conj history gen-record)]

        (printf "%-6d %-8.3f %-8.3f %-6d %-6d %-10.1f %-12.1f %s\n"
                gen
                (double (:best-overall gen-record))
                (double (:mean-overall gen-record))
                (:front-size gen-record)
                (count (:teams best))
                elapsed-s
                eta-min
                (if has-sched? "yes" "no"))
        (flush)

        ;; Checkpoint
        (when (and (pos? gen) (zero? (mod (inc gen) checkpoint-every)))
          (save-checkpoint checkpoint-dir (inc gen) population history' config-seed))

        (recur population (inc gen) history' gen-times')))))

(println "\nDone.")
