(ns futon5.exotype.policy-expansion-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-expansion :as expansion]))

(defn- state [mu]
  (ca/with-seed 73
    (let [genotype (vec (ca/random-sigil-string 8))]
      {:arm :efe-full :seed 73 :time 0 :lambda 0.55 :tau 0.3 :mu mu
       :prevalence-radius 1
       :genotype genotype :previous-genotype genotype
       :phenotype "01010101"
       :exotypes (vec (repeat 8 :identity))})))

(deftest policy-space-is-hold-plus-every-kind
  (let [policies (expansion/policy-set (state 0.1) 0)]
    (is (= 13 (count policies)))
    (is (= :hold (:policy (first policies))))
    (is (= (set grid/exotype-kinds)
           (set (map :kind (rest policies)))))))

(deftest mu-is-exactly-the-locally-absent-habit-floor
  (doseq [[mu expected] [[0.0 0.0] [0.03 0.03] [1.0 1.0]]]
    (let [decision (expansion/cell-decision :efe-full (state mu) 0)
          chaos (first (filter #(= :chaos (:candidate-exotype %))
                               (:candidates decision)))]
      (is (= expected (:habit-mass chaos)))
      (is (= (pos? mu) (pos? (:probability chaos)))))))

(deftest extinction-is-absorbing-only-at-zero-floor
  (let [zero (expansion/cell-decision :efe-full (state 0.0) 0)
        positive (expansion/cell-decision :efe-full (state 0.3) 0)]
    (doseq [kind (remove #{:identity} grid/exotype-kinds)]
      (let [probability-for
            (fn [decision]
              (:probability
               (first (filter #(= kind (:candidate-exotype %))
                              (:candidates decision)))))]
        (is (zero? (probability-for zero)))
        (is (pos? (probability-for positive)))))))

(deftest expanded-policy-run-is-deterministic
  (let [run #(take 30 (iterate expansion/step (state 0.1)))]
    (is (= (run) (run)))
    (testing "the expanded policy is auditable"
      (is (= :kind-policy-posterior
             (get-in (second (run))
                     [:policy-expansion-decisions 0 :selection]))))))

(deftest compact-step-is-semantically-identical
  (doseq [mu [0.0 0.01 0.3 1.0]
          lambda [0.4 0.55 0.7]
          :let [input (assoc (state mu) :lambda lambda)
                audited (map #(dissoc % :efe-decisions
                                      :policy-expansion-decisions)
                             (take 31 (iterate expansion/step input)))
                compact (take 31 (iterate expansion/step-compact input))]]
    (is (= audited compact))))

(deftest invalid-temperature-or-floor-refuses
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"tau must be nonnegative"
                        (expansion/cell-decision
                         :efe-full (assoc (state 0.1) :tau -0.1) 0)))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"mu must be nonnegative"
                        (expansion/cell-decision :efe-full (state -0.1) 0))))
