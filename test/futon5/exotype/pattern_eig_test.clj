(ns futon5.exotype.pattern-eig-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.pattern-eig :as pattern]
            [futon5.exotype.policy-expansion :as expansion]))

(defn- state []
  (ca/with-seed 17
    (let [width 12 genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full :pattern-arm :next-C-plus-eig :seed 17 :time 0
       :lambda 0.55 :tau 0.3 :mu 0.1 :prevalence-radius 1
       :genotype genotype :previous-genotype genotype
       :phenotype "001101001011"
       :exotypes (grid/initial-grid :heterogeneous-fixed width)})))

(deftest baseline-is-the-committed-slice-five-decision
  (let [s (state)]
    (is (= (expansion/cell-decision :efe-full s 4)
           (pattern/cell-decision :baseline s 4)))))

(deftest eig-is-current-local-and-deterministic
  (let [s (state)
        decision (pattern/cell-decision :next-C-plus-eig s 4)]
    (testing "no history, counters, damage, or global measurements are added"
      (is (= decision (pattern/cell-decision :next-C-plus-eig s 4)))
      (is (not-any? #(contains? s %) [:history :counter :damage :entropy :kind-count])))
    (testing "the decision exposes every term for the within-decision audit"
      (is (every? #(every? (fn [key] (contains? % key))
                           [:risk :ambiguity :conatus :eig])
                  (:candidates decision))))))

(deftest corrected-eig-values-unobserved-candidates-most
  (let [s (state)
        absent (first (remove (set (:exotypes s)) grid/exotype-kinds))]
    (when absent
      (is (= (Math/log 2.0) (pattern/corrected-local-eig s 4 1 absent))))
    (is (= 0.0 (pattern/local-eig
                (assoc s :exotypes (vec (repeat 12 :builder))) 4 1 :chaos)))
    (is (= (Math/log 2.0)
           (pattern/corrected-local-eig
            (assoc s :exotypes (vec (repeat 12 :builder))) 4 1 :chaos)))))
