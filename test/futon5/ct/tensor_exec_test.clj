(ns futon5.ct.tensor-exec-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ct.dsl :as dsl]
            [futon5.ct.tensor :as tensor]
            [futon5.ct.tensor-exec :as exec]
            [futon5.mmca.meta-lift :as meta-lift]))

(deftest primitive-and-compose-execution
  (testing "compiled step diagram matches direct tensor stepping"
    (let [row "一二三四五六七八"
          compiled (exec/compile-diagram exec/sigil-step-diagram)
          out (exec/run-diagram compiled {:sigil-row row
                                          :rule-sigil "手"
                                          :step-opts {:wrap? false :boundary-bit 0}})]
      (is (= (tensor/step-sigil-row row "手" {:wrap? false :boundary-bit 0})
             (:new-row out))))))

(deftest tensor-branch-and-gating
  (testing "tensor branch + gate diagram works end-to-end"
    (let [row "一一一一"
          out (exec/run-diagram exec/sigil-step-gated-diagram
                                {:sigil-row row
                                 :rule-sigil "乐"
                                 :phenotype "1010"
                                 :step-opts {:wrap? false :boundary-bit 0}})]
      ;; next-row under rule "乐" is all 乐, then gate keeps old on 1 bits.
      (is (= "一乐一乐" (:gated-row out))))))

(deftest meta-lift-parity-via-compiled-diagram
  (testing "compiled tensor path can match existing meta-lift output"
    (let [row "一二三四五六七八"
          out (exec/run-diagram exec/sigil-step-diagram
                                {:sigil-row row
                                 :rule-sigil "手"
                                 :step-opts {:wrap? false :boundary-bit 0}})]
      (is (= (meta-lift/lift-sigil-string row "手")
             (:new-row out))))))

(deftest run-tensor-ca-smoke
  (testing "runner returns deterministic history and expected length"
    (let [opts {:genotype "一二三四五六七八"
                :rule-sigil "手"
                :generations 5
                :step-opts {:wrap? false :boundary-bit 0}}
          r1 (exec/run-tensor-ca opts)
          r2 (exec/run-tensor-ca opts)]
      (is (= 6 (count (:gen-history r1))))
      (is (= (:gen-history r1) (:gen-history r2)))))

  (testing "runner supports gating path when phenotype is provided"
    (let [result (exec/run-tensor-ca {:genotype "一一一一"
                                      :rule-sigil "乐"
                                      :phenotype "1010"
                                      :generations 1
                                      :step-opts {:wrap? false :boundary-bit 0}})]
      (is (= ["一一一一" "一乐一乐"] (:gen-history result))))))

(deftest run-tensor-ca-rule-plan-and-lesion
  (testing "rule-plan can override the base rule per tick"
    (let [row "一二三四五六七八"
          opts {:genotype row
                :rule-sigil "一"
                :rule-plan {0 "乐"}
                :generations 2
                :step-opts {:wrap? false :boundary-bit 0}}
          step0 (tensor/step-sigil-row row "乐" {:wrap? false :boundary-bit 0})
          step1 (tensor/step-sigil-row step0 "一" {:wrap? false :boundary-bit 0})
          result (exec/run-tensor-ca opts)]
      (is (= [row step0 step1] (:gen-history result)))
      (is (= {0 "乐"} (:rule-plan result)))))

  (testing "lesion changes tensor trajectory and is reported in run metadata"
    (let [opts {:genotype "一二三四五六七八"
                :rule-sigil "手"
                :generations 4
                :step-opts {:wrap? false :boundary-bit 0}}
          lesion {:tick 1 :target :genotype :half :left :mode :zero}
          baseline (exec/run-tensor-ca opts)
          with-lesion (exec/run-tensor-ca (assoc opts :lesion lesion))]
      (is (not= (:gen-history baseline) (:gen-history with-lesion)))
      (is (= lesion (:lesion with-lesion))))))

(deftest custom-diagram-compilation
  (testing "custom tensor diagram with identity and primitive compiles and runs"
    (let [diagram (dsl/tensor-diagrams
                   (dsl/identity-diagram :sigil-row)
                   (dsl/primitive-diagram {:name :pass-sigil-row
                                           :domain [:sigil-row]
                                           :codomain [:copied-row]}))
          out (exec/run-diagram diagram {:sigil-row "一二三四"})]
      (is (= "一二三四" (:sigil-row out)))
      (is (= "一二三四" (:copied-row out))))))
