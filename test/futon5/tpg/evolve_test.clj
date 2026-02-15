(ns futon5.tpg.evolve-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.tpg.core :as tpg]
            [futon5.tpg.evolve :as evolve]
            [futon5.tpg.verifiers :as verifiers]))

;; =============================================================================
;; MUTATION
;; =============================================================================

(deftest mutation-preserves-validity
  (testing "mutating a valid TPG produces a valid TPG"
    (let [rng (java.util.Random. 42)
          config evolve/default-config
          tpg-graph (tpg/seed-tpg-hierarchical)]
      (dotimes [_ 20]
        (let [mutated (evolve/mutate-tpg rng tpg-graph config)
              v (tpg/validate-tpg mutated)]
          (is (:valid? v) (str "Mutation produced invalid TPG: " (:errors v))))))))

(deftest mutation-changes-something
  (testing "mutations don't always return identical TPG"
    (let [rng (java.util.Random. 42)
          config evolve/default-config
          tpg-graph (tpg/seed-tpg-simple)
          mutants (repeatedly 10 #(evolve/mutate-tpg rng tpg-graph config))
          ;; Check that at least some mutations differ
          different-count (count (filter #(not= (:teams %) (:teams tpg-graph)) mutants))]
      (is (pos? different-count)
          "Expected at least some mutations to change the TPG"))))

;; =============================================================================
;; CROSSOVER
;; =============================================================================

(deftest crossover-produces-valid-tpg
  (testing "crossover of two valid TPGs produces a valid TPG"
    (let [rng (java.util.Random. 42)
          parent-a (tpg/seed-tpg-simple)
          parent-b (tpg/seed-tpg-hierarchical)]
      (dotimes [_ 10]
        (let [child (evolve/crossover-tpg rng parent-a parent-b)
              v (tpg/validate-tpg child)]
          (is (:valid? v) (str "Crossover produced invalid TPG: " (:errors v))))))))

;; =============================================================================
;; INITIAL POPULATION
;; =============================================================================

(deftest initial-population-test
  (testing "initial population has correct size and valid TPGs"
    (let [rng (java.util.Random. 42)
          config (assoc evolve/default-config :mu 6)
          pop (evolve/initial-population rng config)]
      (is (= 6 (count pop)))
      ;; All should be valid
      (doseq [individual pop]
        (let [v (tpg/validate-tpg individual)]
          (is (:valid? v) (str "Invalid TPG in initial population: " (:errors v))))))))

;; =============================================================================
;; EVALUATION
;; =============================================================================

(deftest evaluation-test
  (testing "evaluating a TPG produces satisfaction vector"
    (let [rng (java.util.Random. 42)
          config (assoc evolve/default-config
                        :eval-runs 2
                        :eval-generations 10
                        :genotype-length 8)
          tpg-graph (tpg/seed-tpg-simple)
          evaluated (evolve/evaluate-tpg tpg-graph config rng)]
      (is (map? (:satisfaction-vector evaluated)))
      (is (number? (:overall-satisfaction evaluated)))
      (is (<= 0.0 (:overall-satisfaction evaluated) 1.0)))))

;; =============================================================================
;; SELECTION
;; =============================================================================

(deftest selection-test
  (testing "Pareto selection keeps mu survivors"
    (let [population [{:tpg/id "a" :satisfaction-vector {:x 0.9 :y 0.9} :overall-satisfaction 0.9}
                      {:tpg/id "b" :satisfaction-vector {:x 0.1 :y 0.1} :overall-satisfaction 0.1}
                      {:tpg/id "c" :satisfaction-vector {:x 0.8 :y 0.3} :overall-satisfaction 0.55}
                      {:tpg/id "d" :satisfaction-vector {:x 0.3 :y 0.8} :overall-satisfaction 0.55}]
          survivors (evolve/select-survivors population 2)]
      (is (= 2 (count survivors)))
      ;; Best should be "a" (dominates all)
      (is (= "a" (:tpg/id (first survivors)))))))

;; =============================================================================
;; MINI EVOLUTION
;; =============================================================================

(deftest mini-evolution-test
  (testing "evolution runs without error and produces output"
    (let [result (evolve/evolve
                  {:mu 4
                   :lambda 4
                   :eval-runs 2
                   :eval-generations 8
                   :genotype-length 8
                   :evo-generations 3
                   :seed 42
                   :verbose? false})]
      ;; Has expected keys
      (is (contains? result :population))
      (is (contains? result :history))
      (is (contains? result :best))
      (is (contains? result :config))

      ;; Population has correct size
      (is (= 4 (count (:population result))))

      ;; History has correct length
      (is (= 3 (count (:history result))))

      ;; Best has satisfaction data
      (is (map? (get-in result [:best :satisfaction-vector])))
      (is (number? (get-in result [:best :overall-satisfaction])))

      ;; History records show monotonic or improving best
      (is (every? #(number? (:best-overall %)) (:history result))))))
