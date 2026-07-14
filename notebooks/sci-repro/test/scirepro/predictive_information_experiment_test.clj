(ns scirepro.predictive-information-experiment-test
  (:require [clojure.test :refer [deftest is testing]]
            [scirepro.baldwin :as baldwin]
            [scirepro.engine :as engine]
            [scirepro.predictive-information-experiment :as experiment]))

(deftest extracted-baldwin-mutation-seam
  (testing "boundaries do not invoke Baldwin mutation"
    (is (= 42 (baldwin/mutate-combined-rule 42 nil
                                            (fn [_] (throw (Exception. "unused")))))))
  (testing "context gate and bounded bit draws mutate the combined rule"
    (is (not= 42 (baldwin/mutate-combined-rule 42 "0000" (constantly 0))))))

(deftest blend-is-an-executable-third-dynamic
  (let [genotype (engine/seeded-ic 42 16)
        phenotype (engine/seeded-phenotype-ic 43 16)
        blend (experiment/blend-evolve genotype phenotype 8 44)]
    (is (= 9 (count (:genotype blend))))
    (is (= 16 (count (last (:genotype blend)))))
    (is (not= genotype (last (:genotype blend))))))
