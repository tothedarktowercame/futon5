#!/usr/bin/env bb
;; TPG Coupling-Aware Evolution Test
;;
;; Tests the full integration of:
;; 1. Wiring operators in TPG routing
;; 2. Coupling-aware verifier fitness
;; 3. Temporal schedule mode
;;
;; Usage:
;;   bb -cp src:resources scripts/tpg_coupling_evolve.clj

(require '[futon5.ca.core :as ca]
         '[futon5.tpg.core :as tpg]
         '[futon5.tpg.runner :as runner]
         '[futon5.tpg.evolve :as evolve]
         '[futon5.tpg.verifiers :as verifiers]
         '[futon5.tpg.diagnostics :as diag]
         '[futon5.wiring.runtime :as wrt]
         '[futon5.mmca.bitplane-analysis :as bitplane])

(defn fmt [x d] (if (number? x) (format (str "%." d "f") (double x)) (str x)))

;; =============================================================================
;; CONFIGURATION
;; =============================================================================

(def seed 352362012)
(def width 64)
(def generations 50)

;; Load wiring operators
(println "\n======================================================================")
(println "  TPG COUPLING-AWARE EVOLUTION TEST")
(println "======================================================================\n")

(println "Loading wiring operators...")
(def wiring-operators
  (runner/load-wiring-operators
   {:wiring-addself  "data/wiring-rules/hybrid-110-addself.edn"
    :wiring-xorself "data/wiring-rules/hybrid-110-xorself.edn"
    :wiring-msb      "data/wiring-rules/hybrid-110-msb.edn"
    :wiring-bit5     "data/wiring-rules/hybrid-110-bit5.edn"}))
(println (str "  Loaded: " (vec (keys wiring-operators))))

;; Coupling-aware verifier spec
(def coupling-spec
  {:entropy  [0.6 0.35]        ; moderate-high entropy
   :change   [0.2 0.2]         ; moderate change
   :autocorr [0.6 0.3]         ; temporal structure
   :diversity [0.4 0.3]        ; symbol diversity
   :mean-coupling [0.08 0.06]  ; target mild-moderate coupling
   :coupling-cv [0.5 0.3]})    ; prefer some variance (not uniform)

;; =============================================================================
;; TEST 1: Single wiring operator run
;; =============================================================================

(println "\n--- Test 1: Single wiring operator dispatch ---")
(let [genotype (wrt/random-genotype width seed)
      simple-tpg (tpg/make-tpg
                  "wiring-test"
                  [(tpg/make-team
                    :root
                    [(tpg/make-program :p1 [0.0 0.0 0.0 0.0 0.0 0.0] 0.5
                                       :operator :wiring-addself)])]
                  {:root-team :root})
      result (runner/run-tpg
              {:genotype genotype
               :generations generations
               :tpg simple-tpg
               :verifier-spec coupling-spec
               :seed seed
               :wiring-operators wiring-operators})]
  (println "  Wiring-AddSelf TPG run complete.")
  (printf "  Generations: %d  Routing decisions: %d\n"
          (:generations result) (count (:routing-trace result)))

  ;; Check that wiring path was taken
  (let [ops (frequencies (map :operator-id (:routing-trace result)))]
    (printf "  Operator usage: %s\n" (pr-str ops))
    (assert (pos? (get ops :wiring-addself 0))
            "Expected wiring-addself to be used!"))

  ;; Check coupling in extended diagnostics
  (let [ext (get-in (first (:diagnostics-trace result)) [:extended])]
    (printf "  Extended diagnostics: mean-coupling=%.4f  coupling-cv=%.4f\n"
            (double (or (:mean-coupling ext) 0.0))
            (double (or (:coupling-cv ext) 0.0))))

  ;; Show satisfaction vector
  (println "  Satisfaction vector:")
  (doseq [[k v] (sort-by key (get-in result [:verifier-result :satisfaction-vector]))]
    (printf "    %-20s %.3f\n" (name k) (double v)))
  (printf "  Overall: %.3f\n" (double (get-in result [:verifier-result :overall-satisfaction]))))

;; =============================================================================
;; TEST 2: Temporal schedule
;; =============================================================================

(println "\n--- Test 2: Temporal schedule ---")
(let [genotype (wrt/random-genotype width seed)
      ;; Use a simple TPG that always routes to conservation
      simple-tpg (tpg/make-tpg
                  "schedule-test"
                  [(tpg/make-team
                    :root
                    [(tpg/make-program :p1 [0.0 0.0 0.0 0.0 0.0 0.0] 0.5
                                       :operator :conservation)])]
                  {:root-team :root})
      schedule [{:operator :wiring-addself :steps 4}
                {:operator :conservation :steps 1}]
      result (runner/run-tpg
              {:genotype genotype
               :generations 20
               :tpg simple-tpg
               :verifier-spec coupling-spec
               :seed seed
               :wiring-operators wiring-operators
               :temporal-schedule schedule})]
  (println "  Temporal schedule run complete.")
  (let [ops (map :operator-id (:routing-trace result))
        first-10 (take 10 ops)]
    (printf "  First 10 operators: %s\n" (pr-str (vec first-10)))
    ;; Verify pattern: 4x wiring-addself, 1x conservation, repeat
    (let [expected [:wiring-addself :wiring-addself :wiring-addself :wiring-addself
                    :conservation]]
      (assert (= (take 5 ops) expected)
              (str "Expected schedule pattern, got: " (take 5 ops))))
    (println "  Schedule pattern verified!")))

;; =============================================================================
;; TEST 3: Short TPG evolution with coupling fitness
;; =============================================================================

(println "\n--- Test 3: Short TPG evolution with coupling fitness ---")
(let [config {:mu 4
              :lambda 4
              :eval-runs 2
              :eval-generations 30
              :genotype-length width
              :evo-generations 3
              :verifier-spec coupling-spec
              :seed seed
              :wiring-operators wiring-operators
              :verbose? true}
      result (evolve/evolve config)
      best (:best result)]
  (println "\n  Evolution complete.")
  (printf "  Best overall satisfaction: %.3f\n" (double (:overall-satisfaction best)))
  (println "  Best satisfaction vector:")
  (doseq [[k v] (sort-by key (:satisfaction-vector best))]
    (printf "    %-20s %.3f\n" (name k) (double v)))
  (printf "  Teams: %d  Programs: %d\n"
          (count (:teams best))
          (reduce + (map #(count (:programs %)) (:teams best))))

  ;; Report operator usage across best TPG's teams
  (println "  Operator targets in best TPG:")
  (let [all-actions (for [team (:teams best)
                          prog (:programs team)]
                      (get-in prog [:action :target]))
        action-freq (frequencies all-actions)]
    (doseq [[op cnt] (sort-by (comp - val) action-freq)]
      (printf "    %-20s %d\n" (name op) cnt))))

;; =============================================================================
;; TEST 4: Coupling comparison — wiring vs exotype
;; =============================================================================

(println "\n--- Test 4: Coupling comparison ---")
(let [genotype (wrt/random-genotype width seed)
      ;; Run with pure exotype (conservation)
      exo-tpg (tpg/seed-tpg-simple)
      exo-result (runner/run-tpg
                  {:genotype genotype :generations generations
                   :tpg exo-tpg :verifier-spec coupling-spec :seed seed})
      ;; Run with wiring-addself
      wiring-tpg (tpg/make-tpg
                  "wiring-only"
                  [(tpg/make-team
                    :root
                    [(tpg/make-program :p1 [0.0 0.0 0.0 0.0 0.0 0.0] 0.5
                                       :operator :wiring-addself)])]
                  {:root-team :root})
      wiring-result (runner/run-tpg
                     {:genotype genotype :generations generations
                      :tpg wiring-tpg :verifier-spec coupling-spec :seed seed
                      :wiring-operators wiring-operators})
      ;; Direct coupling analysis
      exo-coupling (bitplane/coupling-spectrum (:gen-history exo-result)
                                               {:spatial? false :temporal? false})
      wiring-coupling (bitplane/coupling-spectrum (:gen-history wiring-result)
                                                   {:spatial? false :temporal? false})]
  (printf "  %-20s  MI=%-8s  CV=%-8s  Summary=%s\n"
          "Exotype (simple)"
          (fmt (:mean-coupling exo-coupling) 4)
          (fmt (:coupling-cv exo-coupling) 3)
          (name (or (:summary exo-coupling) :unknown)))
  (printf "  %-20s  MI=%-8s  CV=%-8s  Summary=%s\n"
          "Wiring (addself)"
          (fmt (:mean-coupling wiring-coupling) 4)
          (fmt (:coupling-cv wiring-coupling) 3)
          (name (or (:summary wiring-coupling) :unknown)))

  (let [ratio (if (pos? (:mean-coupling exo-coupling))
                (/ (:mean-coupling wiring-coupling) (:mean-coupling exo-coupling))
                ##Inf)]
    (printf "  Coupling ratio (wiring/exotype): %.1fx\n" (double ratio))))

(println "\n======================================================================")
(println "  ALL TESTS PASSED")
(println "======================================================================\n")
