(ns scirepro.exo-test
  (:require [clojure.test :refer [deftest testing is]]
            [scirepro.exo :as exo]))

(deftest diversity-test
  (testing "diversity of identical sigils"
    (is (== 1/3 (exo/diversity ["一" "一" "一"]))))
  (testing "diversity of all-different sigils"
    (is (= 1.0 (exo/diversity ["一" "二" "三"]))))
  (testing "diversity of two-different sigils"
    (is (< 0.66 (exo/diversity ["一" "二" "一"]) 0.67)))
  (testing "diversity of empty"
    (is (= 0.0 (exo/diversity [])))))

(deftest bit-xor-test
  (testing "XOR of identical sigils gives zero-sigil"
    (is (= exo/default-sigil (exo/bit-xor-sigils "一" "一"))))
  (testing "XOR of 一 (00000000) and 乙 (00000001) gives 乙"
    (is (= "乙" (exo/bit-xor-sigils "一" "乙"))))
  (testing "XOR is symmetric"
    (is (= (exo/bit-xor-sigils "丁" "十")
           (exo/bit-xor-sigils "十" "丁")))))

(deftest threshold-gate-test
  (testing "diversity above 0.5 routes to creative (above)"
    (is (= :creative
           (exo/threshold-gate 0.67 0.5 :creative :legacy))))
  (testing "diversity below 0.5 routes to legacy (below)"
    (is (= :legacy
           (exo/threshold-gate 0.33 0.5 :creative :legacy))))
  (testing "diversity exactly 0.5 routes to creative (>=)"
    (is (= :creative
           (exo/threshold-gate 0.5 0.5 :creative :legacy))))
  (testing "diversity 1.0 routes to creative"
    (is (= :creative
           (exo/threshold-gate 1.0 0.5 :creative :legacy)))))

(deftest gate-routing-test
  (testing "low diversity (all same) → legacy path"
    (let [div (exo/diversity ["一" "一" "一"])
          result (exo/threshold-gate div 0.5 :creative :legacy)]
      (is (= :legacy result)
          "All-same neighborhood should route to legacy")))
  (testing "high diversity (all different) → creative path"
    (let [div (exo/diversity ["一" "二" "三"])
          result (exo/threshold-gate div 0.5 :creative :legacy)]
      (is (= :creative result)
          "All-different neighborhood should route to creative"))))

(deftest xor-path-correctness-test
  (testing "XOR path matches the creative component of L5-creative"
    (is (= "一" (exo/bit-xor-sigils "一" "一")))
    (is (= "乙" (exo/bit-xor-sigils "一" "乙")))
    (is (= "丁" (exo/bit-xor-sigils "乙" "二")))))

(deftest determinism-test
  (testing "same IC produces same creative path output"
    (let [ic "一二十丁五四三六八七"
          f1 (fn [pred self succ]
               (exo/threshold-gate
                (exo/diversity [pred self succ])
                0.5
                (exo/bit-xor-sigils pred succ)
                self))
          r1 (exo/evolve-row-exo ic f1)
          r2 (exo/evolve-row-exo ic f1)]
      (is (= r1 r2) "Evolution must be deterministic"))))

(deftest sigil-roundtrip-test
  (testing "sigil→bits→sigil roundtrip"
    (doseq [sigil ["一" "乙" "二" "丁" "十" "工"]]
      (is (= sigil (exo/bits->sigil (exo/sigil->bits sigil)))
          (str "Roundtrip failed for " sigil))))
  (testing "int→sigil→int roundtrip"
    (doseq [n [0 1 2 3 127 128 255]]
      (is (= n (exo/sigil->int (exo/int->sigil n)))
          (str "Roundtrip failed for " n)))))

(deftest ic-generation-test
  (testing "same seed gives same IC"
    (is (= (exo/gen-exo-ic 20 42)
           (exo/gen-exo-ic 20 42))))
  (testing "different seeds give different ICs"
    (is (not= (exo/gen-exo-ic 20 42)
              (exo/gen-exo-ic 20 43)))))
