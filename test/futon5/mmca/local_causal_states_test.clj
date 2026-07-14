(ns futon5.mmca.local-causal-states-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.mmca.local-causal-states :as lcs]))

(deftest light-cone-geometry
  (let [grid (mapv (fn [t] (mapv (fn [x] [t x]) (range 9))) (range 9))]
    (is (= 8 (count (lcs/past-light-cone grid 4 4 2))))
    (is (= [[3 3] [3 4] [3 5] [2 2] [2 3] [2 4] [2 5] [2 6]]
           (lcs/past-light-cone grid 4 4 2)))
    (is (= [[5 3] [5 4] [5 5]]
           (lcs/future-light-cone grid 4 4 1)))))

(deftest morph-splits-are-significance-tested
  (testing "identical predictive morphs merge"
    (is (:equivalent? (lcs/morph-comparison {[0] 100} {[0] 80} 0.01))))
  (testing "disjoint predictive morphs split"
    (is (false? (:equivalent?
                 (lcs/morph-comparison {[0] 100 [1] 0}
                                       {[0] 0 [1] 100} 0.01))))))

(deftest frozen-field-reconstructs-one-predictive-state
  (let [grid (vec (repeat 30 (vec (repeat 20 0))))
        {:keys [model field]}
        (lcs/reconstruct grid {:past-depth 2 :future-depth 1
                               :training-time-range [5 20]
                               :alpha 0.01 :min-support 10})]
    (is (= 1 (count (:states model))))
    (is (= #{0} (set (keep identity (mapcat identity field)))))
    (is (nil? (get-in field [0 0])))))
