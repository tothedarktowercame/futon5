(ns scirepro.engine-test
  (:require [clojure.test :refer [deftest is testing]]
            [scirepro.engine :as engine]))

(deftest rule-round-trips
  (testing "all byte rules round-trip through the 256ca.el bit strings"
    (is (every? true?
                (for [rule (range 256)]
                  (= rule (engine/bit-string->rule (engine/rule->bit-string rule))))))))

(deftest s3-1-worked-example
  (testing "256ca.el comment: 01101110 x 01010100 x 01010101 -> 00101011"
    (is (= "00101011"
           (engine/rule->bit-string
            (engine/multiply-cell (engine/bit-string->rule "01101110")
                                  (engine/bit-string->rule "01010100")
                                  (engine/bit-string->rule "01010101")))))))

(deftest blend-generic-space
  (testing "left/right agreement censors the local rule"
    (is (= 0 (engine/blend-cell 0 255 0)))
    (is (= 255 (engine/blend-cell 255 0 255))))
  (testing "left/right disagreement falls back to the center local rule"
    (is (= "01101111"
           (engine/rule->bit-string
            (engine/blend-cell (engine/bit-string->rule "01101110")
                               (engine/bit-string->rule "01010100")
                               (engine/bit-string->rule "01010101")))))))

(deftest deterministic-evolution
  (let [ic (engine/seeded-ic 150200130 32)]
    (is (= ic (engine/seeded-ic 150200130 32)))
    (is (= (engine/evolve ic 12)
           (engine/evolve ic 12 :multiply)))))

(deftest measurement-helpers
  (testing "row-repeat stasis and stable-band window timing"
    (let [rows [[1 2] [2 3] [2 3] [2 3] [4 5]]]
      (is (= 2 (engine/first-stasis-time rows)))
      (is (= 1 (engine/first-band-time rows 3)))))
  (testing "entropy and change rate"
    (is (= 0.0 (engine/shannon-entropy [])))
    (is (= 0.0 (engine/shannon-entropy [7 7 7])))
    (is (< (abs (- 1.0 (engine/shannon-entropy [0 1]))) 1.0E-9))
    (is (= 0.5 (engine/change-rate [0 1 1 0] [0 0 1 1]))))
  (testing "mutual information and deterministic row rotation"
    (is (= [2 3 1] (engine/rotate-row [1 2 3] 1)))
    (is (= 0.0 (engine/mutual-information [] [])))
    (is (< (abs (- 1.0 (engine/mutual-information [0 0 1 1] [0 0 1 1])))
           1.0E-9))
    (is (< (engine/mutual-information [0 0 1 1] [0 1 0 1])
           1.0E-9))))

(deftest c7-proof
  (let [{:keys [cases passed ok? wolfram-descending]}
        (engine/blending-censored-rule-23-proof)]
    (testing "blending = Rule 23 censored by local logic, in the elisp bit order"
      (is (= 8192 cases))
      (is (= cases passed))
      (is (true? ok?)))
    (testing "the S5.3 'Rule 23' label fails under Wolfram descending order (convention-dependent, cf. A1)"
      (is (false? (:ok? wolfram-descending)))
      (is (< (:passed wolfram-descending) (:cases wolfram-descending))))))
