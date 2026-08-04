(ns futon5.exotype.selection-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.selection :as selection]))

(deftest preference-fitness-inherits-the-existing-c-targets
  (is (identical? efe/preferences selection/preference-targets))
  (is (= {:rule-change 0.15 :hunger 0.05} efe/preferences)))

(deftest zero-strength-is-exact-identity
  (let [genotypes [:a :b :c :d]
        fitness [0.0 1.0 0.0 1.0]]
    (is (identical? genotypes
                    (selection/select-genotypes genotypes fitness 0.0 17)))))

(deftest nonzero-selection-concentrates-the-genotype-distribution
  (let [genotypes [:fit :other :fit :other :fit :other :fit :other]
        fitness [1.0 0.0 1.0 0.0 1.0 0.0 1.0 0.0]
        selected (selection/select-genotypes genotypes fitness 1.0 23)]
    (testing "every low-fitness cell has two fit neighbours"
      (is (= [:fit] (distinct selected))))
    (is (< (count (distinct selected)) (count (distinct genotypes))))))

(deftest fitness-implementations-are-multimethod-cases
  (let [window {:steps 2
                :expressed-changes [0 2]
                :hunger [0 2]
                :divergence [0.0 2.0]}]
    (is (= 2 (count (selection/fitness-values :preferences window))))
    (is (= [0.0 1.0] (selection/fitness-values :divergence window)))))
