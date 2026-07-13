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

;;; --- Mutation engine tests (slice 4a) ---------------------------------------

(deftest mutation-stream-determinism
  (testing "same seed produces the same mutation stream (uniform mode)"
    (is (= (engine/generate-mutation-stream 42 16 50 0.1 :uniform)
           (engine/generate-mutation-stream 42 16 50 0.1 :uniform))))
  (testing "same seed produces the same mutation stream (first-bit mode)"
    (is (= (engine/generate-mutation-stream 99 8 30 0.2 :first-bit)
           (engine/generate-mutation-stream 99 8 30 0.2 :first-bit))))
  (testing "different seeds produce different streams"
    (is (not= (engine/generate-mutation-stream 1 16 50 0.5)
              (engine/generate-mutation-stream 2 16 50 0.5))))
  (testing "rate 0.0 produces no events"
    (is (empty? (engine/generate-mutation-stream 42 16 50 0.0))))
  (testing "rate 1.0 produces one event per cell per generation (uniform)"
    (let [events (engine/generate-mutation-stream 42 4 3 1.0 :uniform)]
      (is (= 12 (count events)))
      (is (= (set (for [g (range 1 4) c (range 4)] [g c]))
             (set (map (juxt :generation :cell) events)))))))

(deftest flip-application
  (testing "flip-bit toggles the correct allele (MSB-first indexing)"
    ;; Rule 0 = 00000000; flipping allele 0 (MSB) gives 10000000 = 128
    (is (= 128 (engine/flip-bit 0 0)))
    ;; flipping allele 7 (LSB) gives 00000001 = 1
    (is (= 1 (engine/flip-bit 0 7)))
    ;; flipping allele 0 of rule 255 (11111111) gives 01111111 = 127
    (is (= 127 (engine/flip-bit 255 0))))
  (testing "flip-bit is its own inverse"
    (is (= 42 (engine/flip-bit (engine/flip-bit 42 3) 3))))
  (testing "apply-flips chains multiple flips left to right"
    ;; Rule 0, flip alleles 0 and 7 → 10000001 = 129
    (is (= 129 (engine/apply-flips 0 [0 7]))))
  (testing "allele index out of range throws"
    (is (thrown? clojure.lang.ExceptionInfo (engine/flip-bit 0 8)))
    (is (thrown? clojure.lang.ExceptionInfo (engine/flip-bit 0 -1)))))

(deftest first-bit-only-variant
  (testing "first-bit stream always uses allele 0"
    (let [events (engine/generate-mutation-stream 7 10 20 0.3 :first-bit)]
      (is (every? #(= 0 (:allele %)) events))))
  (testing "first-bit-only mutation only ever flips the MSB (Figure-8 skewed variant)"
    ;; evolve-with-first-bit-mutation should produce the same result as
    ;; evolve-with-mutation with a first-bit stream of the same seed/rate.
    (let [ic (engine/seeded-ic 150200130 16)]
      (is (= (engine/evolve-with-first-bit-mutation ic 20 7 0.3)
             (engine/evolve-with-mutation
              ic 20
              (engine/generate-mutation-stream 7 16 20 0.3 :first-bit)))))))

(deftest evolve-with-mutation-determinism
  (testing "evolve-with-mutation is deterministic given (IC, stream)"
    (let [ic (engine/seeded-ic 150200130 32)
          stream (engine/generate-mutation-stream 55 32 40 0.1)]
      (is (= (engine/evolve-with-mutation ic 40 stream)
             (engine/evolve-with-mutation ic 40 stream)))))
  (testing "an empty mutation stream reduces to plain blend evolution"
    (let [ic (engine/seeded-ic 150200130 16)]
      (is (= (engine/evolve ic 10 :blend)
             (engine/evolve-with-mutation ic 10 [])))))
  (testing "mutation is applied after the blend step (A6: after evolve)"
    ;; If mutation were before the step, the blended result would differ.
    ;; With a rate-1.0 first-bit stream, every cell's MSB flips after blend.
    (let [ic [0 0 0 0]  ; width 4, all rule-0
          stream [{:generation 1 :cell 0 :allele 0}]
          result (engine/evolve-with-mutation ic 1 stream)
          row-1 (nth result 1)]
      ;; The blend of all-zeros is all-zeros; then cell 0's allele 0 flips
      ;; → 128.
      (is (= 128 (nth row-1 0)))
      (is (= 0 (nth row-1 1)))))
  (testing "pre-indexed event-map works the same as raw event list"
    (let [ic (engine/seeded-ic 150200130 16)
          stream (engine/generate-mutation-stream 33 16 20 0.2)
          emap (engine/stream->event-map stream)]
      (is (= (engine/evolve-with-mutation ic 20 stream)
             (engine/evolve-with-mutation ic 20 emap))))))

;;; --- Balance-mutation tests (slice 4b) --------------------------------------

(deftest balance-mutation-determinism
  (testing "evolve-with-balance-mutation is deterministic given (IC, seed)"
    (let [ic (engine/seeded-ic 150200150 24)]
      (is (= (engine/evolve-with-balance-mutation ic 30 42 :multiply)
             (engine/evolve-with-balance-mutation ic 30 42 :multiply)))))
  (testing "different seeds produce different results"
    (let [ic (engine/seeded-ic 150200150 24)]
      (is (not= (engine/evolve-with-balance-mutation ic 20 1 :multiply)
                (engine/evolve-with-balance-mutation ic 20 2 :multiply))))))

(deftest balance-mutation-homeostasis
  (testing "popcount in [2,6] never triggers mutation when the rule doesn't change"
    ;; Balance-mutation is only evaluated when popcount > 6 or < 2.
    ;; We test this directly: a rule with popcount 4 never changes.
    ;; Rule 60 = 00111100, popcount 4.
    (let [rng (java.util.Random. 42)
          result (engine/balance-mutate-rule rng 60)]
      (is (= 60 result) "popcount-4 rule unchanged (gate not evaluated)")))
  (testing "balance-mutation only fires at popcount extremes"
    ;; Over many trials, rules with popcount in [2,6] are never mutated
    ;; regardless of the RNG state.
    (let [rng (java.util.Random. 999)
          rules-in-range [60 90 106 120 150 170 180 195 204 225 240]
          all-unchanged? (every? identity
                                 (for [_ (range 100)
                                       rule rules-in-range]
                                   (= rule (engine/balance-mutate-rule rng rule))))]
      (is all-unchanged? "no popcount-[2,6] rule was mutated"))))

(deftest balance-mutation-gate-rate
  (testing "gate rate is approximately 1/20 = 5% over many trials"
    ;; Create a rule with popcount 7 (e.g. 254 = 11111110) and run many
    ;; balance-mutation steps with different RNG seeds, counting how often
    ;; the rule changes.
    (let [rule 254  ; popcount 7 > 6, so gate is evaluated
          trials 1000
          rng (java.util.Random. 12345)
          changes (count (filter #(not= rule %)
                                 (for [_ (range trials)]
                                   (engine/balance-mutate-rule rng rule))))
          observed-rate (/ changes (double trials))
          expected 0.05]
      ;; Allow generous tolerance (binomial): 0.05 ± 0.04
      (is (< (abs (- observed-rate expected)) 0.04)
          (str "observed rate " observed-rate " expected " expected)))))
