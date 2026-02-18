(ns futon5.tpg.runner-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.tpg.core :as tpg]
            [futon5.tpg.runner :as runner]
            [futon5.tpg.verifiers :as verifiers]
            [futon5.ca.core :as ca]))

;; =============================================================================
;; BASIC RUN
;; =============================================================================

(deftest basic-run-test
  (testing "TPG run produces valid output structure"
    (let [result (runner/run-tpg
                  {:genotype "一二三四五六七八"
                   :generations 10
                   :tpg (tpg/seed-tpg-simple)
                   :seed 42})]
      ;; Check output keys
      (is (contains? result :gen-history))
      (is (contains? result :phe-history))
      (is (contains? result :diagnostics-trace))
      (is (contains? result :routing-trace))
      (is (contains? result :verifier-result))

      ;; Gen history has correct length (initial + generations)
      (is (= 11 (count (:gen-history result))))

      ;; Diagnostics trace has correct length (generations + final)
      (is (= 11 (count (:diagnostics-trace result))))

      ;; Routing trace has correct length (one per generation)
      (is (= 10 (count (:routing-trace result))))

      ;; Each routing entry has an operator-id
      (is (every? :operator-id (:routing-trace result)))

      ;; Verifier result has satisfaction vector
      (is (map? (get-in result [:verifier-result :satisfaction-vector])))))

  ;; NOTE: Full determinism requires seeded RNG in kernel functions.
  ;; The local-physics-kernels currently use (rand) which is unseeded.
  ;; Routing decisions ARE deterministic (same diagnostics → same route),
  ;; but the CA evolution itself has stochastic elements.
  ;; TODO: Thread seeded RNG through local-physics-kernels for Phase 3.
  (testing "TPG routing is deterministic given same diagnostics"
    (let [d (double-array [0.5 0.3 0.7 0.4 0.2 0.5])
          tpg-graph (tpg/seed-tpg-hierarchical)
          r1 (tpg/route tpg-graph d)
          r2 (tpg/route tpg-graph d)]
      (is (= (:operator-id r1) (:operator-id r2)))
      (is (= (:route r1) (:route r2))))))

;; =============================================================================
;; HIERARCHICAL TPG
;; =============================================================================

(deftest hierarchical-run-test
  (testing "hierarchical TPG produces deeper routes"
    (let [result (runner/run-tpg
                  {:genotype (apply str (map :sigil (take 16 (ca/sigil-entries))))
                   :generations 20
                   :tpg (tpg/seed-tpg-hierarchical)
                   :seed 42})]
      ;; Should have some routes with depth > 0
      (is (some #(> (:depth %) 0) (:routing-trace result)))

      ;; Should not have any fallbacks on this seed
      (is (not-any? :fallback? (:routing-trace result))))))

;; =============================================================================
;; VERIFIER EVALUATION
;; =============================================================================

(deftest verifier-integration-test
  (testing "verifier satisfaction is computed for all specified verifiers"
    (let [result (runner/run-tpg
                  {:genotype "一二三四五六七八"
                   :generations 20
                   :tpg (tpg/seed-tpg-hierarchical)
                   :verifier-spec {:entropy [0.6 0.35]
                                   :change [0.2 0.2]}
                   :seed 42})
          sat-vec (get-in result [:verifier-result :satisfaction-vector])]
      ;; Satisfaction vector has entries for each verifier
      (is (contains? sat-vec :entropy))
      (is (contains? sat-vec :change))

      ;; Values are in [0, 1]
      (is (<= 0.0 (:entropy sat-vec) 1.0))
      (is (<= 0.0 (:change sat-vec) 1.0))

      ;; Overall satisfaction is in [0, 1]
      (is (<= 0.0 (get-in result [:verifier-result :overall-satisfaction]) 1.0)))))

;; =============================================================================
;; ROUTING SUMMARY
;; =============================================================================

(deftest routing-summary-test
  (testing "routing summary computes operator entropy and usage"
    (let [result (runner/run-tpg
                  {:genotype (ca/random-sigil-string 16)
                   :generations 30
                   :tpg (tpg/seed-tpg-hierarchical)
                   :seed 42})
          summary (runner/summarize-routing (:routing-trace result))]
      ;; Has expected keys
      (is (contains? summary :operator-frequency))
      (is (contains? summary :operator-entropy))
      (is (contains? summary :fallback-rate))
      (is (contains? summary :mean-depth))

      ;; Operator entropy is non-negative
      (is (>= (:operator-entropy summary) 0.0))

      ;; Total decisions matches generations
      (is (= 30 (:total-decisions summary))))))

;; =============================================================================
;; BATCH RUNNER
;; =============================================================================

(deftest batch-run-test
  (testing "batch runner produces aggregate statistics"
    (let [result (runner/run-tpg-batch
                  {:tpg (tpg/seed-tpg-simple)
                   :n-runs 3
                   :generations 10
                   :base-seed 42})]
      (is (= 3 (:n-runs result)))
      (is (= 3 (count (:runs result))))
      (is (map? (:mean-satisfaction result)))
      (is (number? (:overall-mean result)))

      ;; Each run is a valid run result
      (doseq [run (:runs result)]
        (is (= 11 (count (:gen-history run))))))))

;; =============================================================================
;; PHENOTYPE SUPPORT
;; =============================================================================

(deftest phenotype-run-test
  (testing "TPG run works with phenotype"
    (let [genotype "一二三四五六七八"
          phenotype "01010101"
          result (runner/run-tpg
                  {:genotype genotype
                   :phenotype phenotype
                   :generations 10
                   :tpg (tpg/seed-tpg-simple)
                   :seed 42})]
      ;; Phenotype history should be present
      (is (seq (:phe-history result)))
      (is (= 11 (count (:phe-history result))))

      ;; Phenotype coupling diagnostic should be non-zero somewhere
      (is (some (fn [d] (when d (pos? (:phenotype-coupling (:named d)))))
                (:diagnostics-trace result))))))

;; =============================================================================
;; PARETO DOMINANCE
;; =============================================================================

(deftest pareto-test
  (testing "Pareto dominance"
    (is (verifiers/dominates? {:a 0.8 :b 0.6} {:a 0.7 :b 0.5}))
    (is (not (verifiers/dominates? {:a 0.8 :b 0.4} {:a 0.7 :b 0.5})))
    (is (not (verifiers/dominates? {:a 0.5 :b 0.5} {:a 0.5 :b 0.5}))))

  (testing "Pareto ranking"
    (let [vecs [{:a 0.9 :b 0.9}   ;; front 0 (dominates all)
                {:a 0.1 :b 0.1}   ;; dominated by all
                {:a 0.8 :b 0.3}   ;; non-dominated by 0, dominates 1
                {:a 0.3 :b 0.8}]  ;; non-dominated by 0, dominates 1
          ranked (verifiers/pareto-rank vecs)]
      ;; Best solution has rank 0
      (is (= 0 (:rank (first ranked))))
      ;; Worst solution has highest rank
      (is (= (apply max (map :rank ranked))
             (:rank (second ranked)))))))
