(ns scirepro.predictive-information-experiment-test
  (:require [clojure.test :refer [deftest is testing]]
            [scirepro.coherence-experiment :as coherence]
            [scirepro.baldwin :as baldwin]
            [scirepro.engine :as engine]
            [scirepro.mutating-template :as mutating-template]
            [scirepro.predictive-information-experiment :as experiment]
            [scirepro.weighted-blend-experiment :as weighted]))

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

(deftest weighted-family-has-exact-parent-endpoints
  (let [genotype (engine/seeded-ic 42 16)
        phenotype (engine/seeded-phenotype-ic 43 16)
        steps 8
        seed 44
        template (mutating-template/coupled-contextual-evolve
                  genotype phenotype steps seed)
        baldwin (baldwin/baldwin-evolve genotype phenotype steps
                                        (experiment/bounded-rng seed))]
    (is (= template (weighted/weighted-evolve genotype phenotype steps seed 1.0)))
    (is (= baldwin (weighted/weighted-evolve genotype phenotype steps seed 0.0)))
    (let [hybrid (weighted/weighted-evolve genotype phenotype steps seed 0.5)]
      (is (not= (:genotype template) (:genotype hybrid)))
      (is (not= (:genotype baldwin) (:genotype hybrid))))))

(deftest coherence-eye-check-uses-predeclared-ci-order
  (let [summary
        {:bitplane {0.9 {:mean 0.8 :ci95 0.1}
                    0.5 {:mean 0.4 :ci95 0.1}}
         :coarse-8 {0.9 {:mean 0.5 :ci95 0.1}
                    0.5 {:mean 0.5 :ci95 0.1}}
         :full-cell {0.9 {:mean 0.2 :ci95 0.1}
                     0.5 {:mean 0.4 :ci95 0.1}}}
        checks (coherence/eye-check summary)]
    (is (true? (get-in checks [:bitplane :passes?])))
    (is (false? (get-in checks [:coarse-8 :passes?])))
    (is (false? (get-in checks [:full-cell :passes?])))))
