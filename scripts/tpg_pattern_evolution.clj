#!/usr/bin/env bb
;; TPG evolution with pattern wirings as the action space.
;;
;; This script wires compiled pattern wirings (from pattern_to_wiring.py)
;; into the TPG evolutionary framework. Instead of abstract hexagram-family
;; operators, TPG programs now route to concrete wiring diagrams that
;; define the local physics.
;;
;; The 8 operators map to 8 hexagram wirings spanning the parameter space:
;;   :expansion      → hex-03 屯 Zhun     (scramble, th=0.4, up=0.6)
;;   :conservation   → hex-11 泰 Tai      (majority, th=0.5, up=0.5)
;;   :adaptation     → hex-18 蠱 Gu       (xor-neighbor, th=0.5, up=0.55)
;;   :momentum       → hex-28 大過 Daguo  (swap-halves, th=0.45, up=0.6)
;;   :conditional    → hex-38 睽 Kui      (xor-neighbor, th=0.45, up=0.55)
;;   :differentiation→ hex-42 益 Yi       (rotate-right, th=0.5, up=0.6)
;;   :transformation → hex-49 革 Ge       (scramble, th=0.4, up=0.7)
;;   :consolidation  → hex-57 巽 Xun      (rotate-right, th=0.6, up=0.35)
;;
;; Usage:
;;   cd /home/joe/code/futon5
;;   bb -cp src:resources scripts/tpg_pattern_evolution.clj
;;
;; Or with custom config:
;;   bb -cp src:resources scripts/tpg_pattern_evolution.clj '{:evo-generations 5 :eval-runs 3}'

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[futon5.tpg.core :as tpg]
         '[futon5.tpg.evolve :as evolve]
         '[futon5.tpg.runner :as runner]
         '[futon5.wiring.runtime :as wrt])

;; =============================================================================
;; WIRING OPERATOR MAP
;; =============================================================================

(def wiring-paths
  "Map from operator-id to compiled wiring file path.
   One hexagram per family, chosen to span the parameter space."
  {:expansion       "resources/xenotype-wirings/compiled-iching-hexagram-03-zhun.edn"
   :conservation    "resources/xenotype-wirings/compiled-iching-hexagram-11-tai.edn"
   :adaptation      "resources/xenotype-wirings/compiled-iching-hexagram-18-gu.edn"
   :momentum        "resources/xenotype-wirings/compiled-iching-hexagram-28-daguo.edn"
   :conditional     "resources/xenotype-wirings/compiled-iching-hexagram-38-kui.edn"
   :differentiation "resources/xenotype-wirings/compiled-iching-hexagram-42-yi.edn"
   :transformation  "resources/xenotype-wirings/compiled-iching-hexagram-49-ge.edn"
   :consolidation   "resources/xenotype-wirings/compiled-iching-hexagram-57-xun.edn"})

(defn load-all-wirings
  "Load all 8 hexagram wirings from disk. Returns operator map suitable
   for passing to TPG runner as :wiring-operators."
  []
  (println "Loading pattern wirings...")
  (let [ops (into {}
              (map (fn [[op-id path]]
                     (let [wiring (edn/read-string (slurp path))
                           meta-info (:meta wiring)]
                       (printf "  %-18s → %s (%s, th=%.2f, up=%.2f)%n"
                               (name op-id)
                               (:id meta-info)
                               (name (:mix-mode meta-info))
                               (double (:match-threshold meta-info))
                               (double (:update-prob meta-info)))
                       [op-id wiring]))
                   wiring-paths))]
    (println (str "  Loaded " (count ops) " wiring operators."))
    (println)
    ops))

;; =============================================================================
;; EVOLUTION CONFIG
;; =============================================================================

(def base-config
  "Base evolution config, tuned for pattern-wiring runs.
   Conservative settings for initial testing — scale up once validated."
  {:mu 8                  ; parent population size
   :lambda 8             ; offspring per generation
   :eval-runs 3          ; MMCA runs per TPG evaluation (3 for speed)
   :eval-generations 50  ; generations per MMCA run
   :genotype-length 32   ; sigil string length
   :evo-generations 10   ; evolutionary generations (start small)
   :seed 42
   :verbose? true
   :verbose-eval? true
   :verbose-eval-runs? false
   :verbose-eval-gen-every 25
   :verbose-eval-phases? false})

;; =============================================================================
;; MAIN
;; =============================================================================

(defn -main [& args]
  (let [;; Parse optional config override from command line
        user-config (when (seq args)
                      (try
                        (edn/read-string (first args))
                        (catch Exception e
                          (println "Warning: could not parse config:" (.getMessage e))
                          {})))
        config (merge base-config user-config)

        ;; Load pattern wirings
        wiring-ops (load-all-wirings)

        ;; Merge wirings into config (evolve.clj passes them through)
        config (assoc config :wiring-operators wiring-ops)

        ;; Print config summary
        _ (println "=== TPG Pattern Evolution ===")
        _ (println (str "Population: " (:mu config) "+" (:lambda config)
                        " | Eval: " (:eval-runs config) "×" (:eval-generations config)
                        "gen | Evo: " (:evo-generations config) " generations"))
        _ (println (str "Seed: " (:seed config)))
        _ (println)

        ;; Run evolution
        t0 (System/currentTimeMillis)
        result (evolve/evolve config)
        elapsed (/ (- (System/currentTimeMillis) t0) 1000.0)

        ;; Extract results
        best (:best result)
        history (:history result)]

    ;; Print results
    (println)
    (println "=== Results ===")
    (printf "Total time: %.1f seconds%n" elapsed)
    (println)

    ;; Best TPG summary
    (println "Best TPG:" (:tpg/id best))
    (printf "  Overall satisfaction: %.3f%n" (double (:overall-satisfaction best)))
    (println "  Satisfaction vector:")
    (doseq [[k v] (sort-by key (:satisfaction-vector best))]
      (printf "    %-20s %.3f%n" (name k) (double v)))
    (println)

    ;; TPG structure
    (println "  Teams:" (count (:teams best)))
    (println "  Programs:" (reduce + (map #(count (:programs %)) (:teams best))))
    (when (:temporal-schedule best)
      (println "  Temporal schedule:" (:temporal-schedule best)))
    (println)

    ;; Evolution trace
    (println "--- Evolution History ---")
    (doseq [{:keys [generation best-overall mean-overall front-size]} history]
      (printf "  Gen %2d | best %.3f | mean %.3f | front %d%n"
              generation (double best-overall) (double mean-overall) front-size))
    (println)

    ;; Save best TPG to file
    (let [output-path (str "results/tpg-pattern-evolution-"
                           (System/currentTimeMillis) ".edn")]
      (io/make-parents output-path)
      (spit output-path
            (pr-str {:best (dissoc best :satisfaction-vector :overall-satisfaction
                                   :eval-runs :pareto-rank)
                     :best-satisfaction (:satisfaction-vector best)
                     :best-overall (:overall-satisfaction best)
                     :history history
                     :config (dissoc config :wiring-operators)
                     :wiring-paths wiring-paths
                     :elapsed-s elapsed}))
      (println "Saved best TPG to:" output-path))
    (println)

    ;; Return result for REPL use
    result))

(-main (first *command-line-args*))
