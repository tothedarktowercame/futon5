(ns futon5.ct.tensor-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ct.tensor :as tensor]
            [futon5.ct.tensor-jax :as tensor-jax]
            [futon5.mmca.meta-lift :as meta-lift]))

(deftest sigil-tensor-roundtrip
  (testing "sigil row roundtrips through width x 8 tensor representation"
    (let [row "一二三四五六七八"
          tensor-repr (tensor/sigil-row->tensor row)]
      (is (= row (tensor/tensor->sigil-row tensor-repr)))))

  (testing "bitplane transpose roundtrips"
    (let [row "一二三四五六七八"
          genotype (tensor/sigil-row->tensor row)
          planes (tensor/tensor->bitplanes genotype)]
      (is (= genotype (tensor/bitplanes->tensor planes)))
      (is (= 8 (count planes)))
      (is (every? #(= (count row) (count %)) planes)))))

(deftest phenotype-tensor-roundtrip
  (testing "phenotype row roundtrips through tensor representation"
    (let [phe "01011010"
          t (tensor/phenotype->tensor phe)]
      (is (= phe (tensor/tensor->phenotype t))))))

(deftest deterministic-rule-steps
  (testing "all-zero rule collapses every cell to all-zero sigil"
    (let [row "一二三四五六七八"
          next-row (tensor/step-sigil-row row "一")] ;; 00000000 rule
      (is (= (apply str (repeat (count row) "一")) next-row))))

  (testing "all-one rule collapses every cell to all-one sigil"
    (let [row "一二三四五六七八"
          next-row (tensor/step-sigil-row row "乐")] ;; 11111111 rule
      (is (= (apply str (repeat (count row) "乐")) next-row)))))

(deftest phenotype-gating
  (testing "phenotype bit 1 keeps old cell; 0 takes new cell"
    (let [old-row "一一一一"
          new-row "乐乐乐乐"
          phe "1010"]
      (is (= "一乐一乐"
             (tensor/gate-sigil-row-by-phenotype old-row new-row phe))))))

(deftest meta-lift-equivalence
  (testing "tensor stepping matches existing meta_lift semantics with open boundaries"
    (let [row "一二三四五六七八"
          rule "手"
          tensor-next (tensor/step-sigil-row row rule {:wrap? false :boundary-bit 0})
          lifted-next (meta-lift/lift-sigil-string row rule)]
      (is (= lifted-next tensor-next)))))

(deftest jax-backend-dispatch
  (testing "step-bitplanes dispatches to JAX backend when requested"
    (let [planes [[0 1 0 1]
                  [1 0 1 0]
                  [0 0 1 1]
                  [1 1 0 0]
                  [0 1 1 0]
                  [1 0 0 1]
                  [0 0 0 1]
                  [1 1 1 0]]
          called (atom nil)]
      (with-redefs [tensor-jax/step-bitplanes-jax
                    (fn [bitplanes rule-sigil opts]
                      (reset! called {:bitplanes bitplanes
                                      :rule-sigil rule-sigil
                                      :opts opts})
                      bitplanes)]
        (is (= planes (tensor/step-bitplanes planes "手" {:backend :jax
                                                          :wrap? false
                                                          :boundary-bit 1})))
        (is (= {:bitplanes planes
                :rule-sigil "手"
                :opts {:wrap? false :boundary-bit 1}}
               @called))))))
