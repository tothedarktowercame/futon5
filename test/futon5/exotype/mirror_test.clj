(ns futon5.exotype.mirror-test
  "P-008 §2/§10.1 mirror checks on the EXACT terms (DESIGN-P008).

  Mirror m acts on the neighbourhood value x = 4l+2c+r by swapping l and r:
  1<->4 and 3<->6. On a rule it conjugates the table, r^m(x) = r(m(x)); on an
  exotype (a permutation of neighbourhoods) it conjugates the permutation,
  sigma -> m o sigma o m; on a ring it reverses the order, which exchanges the
  left and right candidates of every cell.

  Everything here works in ca/truth-table-3 index space: index i names the
  neighbourhood with value x = 7 - i, so m on indices swaps the index pairs
  {1,4} and {3,6} (values {6,3} and {4,1}).

  Checks (>= 200 random cases each):
  1. gen/rule-change-rate is mirror-invariant: rate(m sigma m) = rate(sigma).
  2. policy-epistemic/pair-value is mirror-invariant on both arguments
     (own and candidate conjugated), and symmetric under own<->candidate.
  3. policy-epistemic/blend-value is mirror-equivariant on the state:
     blend(state, i) = blend(mirror state, width-1-i), the mirrored state
     having the ring reversed and each rule conjugated (left and right
     neighbours thereby exchanged).
  4. chain-risk/risk is mirror-equivariant: risk(sigma, b) =
     risk(m sigma m, m(b)).
  5. rule-permute is mirror-equivariant, draw by draw: applying sigma at
     draw k to rule b, then mirroring, equals applying m sigma m at draw
     m(k) to the mirrored rule. The k-explicit core used here is checked
     against the real private gen/rule-permute by seeding the ca RNG so its
     first draw is exactly k."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.chain-risk :as chain-risk]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as policy-epistemic]
            [futon5.xenotype.generator :as gen])
  (:import [java.util SplittableRandom]))

(def ^:private tt (vec ca/truth-table-3))

;; value-level mirror: 1<->4, 3<->6 on x = 4l+2c+r
(def ^:private m-val {0 0, 1 4, 2 2, 3 6, 4 1, 5 5, 6 3, 7 7})

;; index-level mirror: index i names value 7-i
(def ^:private m-idx (vec (for [i (range 8)] (- 7 (m-val (- 7 i))))))

(defn mirror-rule-bits
  "r^m(x) = r(m(x)): bit i of the mirrored rule is bit m(i) of the rule.
  BITS is a string in truth-table-3 order."
  [^String bits]
  (apply str (map #(nth bits (nth m-idx %)) (range 8))))

(defn mirror-byte
  "Mirror of an integer rule byte in the same convention."
  ^long [^long b]
  (reduce (fn [acc i]
            (if (bit-test b (- 7 (nth m-idx i)))
              (bit-set acc (- 7 i))
              acc))
          0
          (range 8)))

(defn mirror-s8
  "S8 vector of m o sigma o m."
  [p]
  (mapv (fn [i] (nth m-idx (nth p (nth m-idx i)))) (range 8)))

(defn mirror-sigil [sigil]
  (ca/sigil-for (mirror-rule-bits (ca/bits-for (str sigil)))))

(defn random-s8 [^SplittableRandom rng]
  (let [a (long-array (range 8))]
    (doseq [i (range 7 0 -1)]
      (let [j (.nextInt rng (inc i)) t (aget a i)]
        (aset a i (aget a j)) (aset a j t)))
    (vec a)))

;; --- k-explicit core of gen/rule-permute (docstring-pinned semantics) -----

(defn permute-at
  "What gen/rule-permute does when its draw is exactly k: write (not bit k)
  into position di, where di is the truth-table-3 index of sigma(tt[k])
  (identity, hence a no-write of the same position, when tt[k] is not in
  sigma's domain)."
  [^String rule-bits sigma k]
  (let [src (nth tt k)
        dst (get sigma src src)
        di (.indexOf ^java.util.List tt dst)
        v (if (= \0 (nth rule-bits k)) \1 \0)]
    (if (neg? di)
      rule-bits
      (str (subs rule-bits 0 di) v (subs rule-bits (inc di))))))

;; seeds whose FIRST (ca/rnd-int 8) draw is each k, found by search through
;; the real RNG; lets the test pin the core to the real rule-permute
(def ^:private seed-for-k
  (let [found (volatile! {})]
    (loop [s 0]
      (when (< (count @found) 8)
        (let [k (ca/with-seed s (ca/rnd-int 8))]
          (when-not (@found k) (vswap! found assoc k s)))
        (recur (inc s))))
    @found))

(deftest permute-core-matches-real-rule-permute
  (testing "the k-explicit core is the real gen/rule-permute at that draw"
    (let [rng (SplittableRandom. 424242)
          cases (for [_ (range 200)]
                  (let [p (random-s8 rng)
                        sigma (grid/sigma-of p)
                        bits (ca/bits-for (ca/sigil-for
                                           (apply str (repeatedly 8 #(.nextInt rng 2)))))]
                    [bits sigma (mod (.nextInt rng 8) 8)]))]
      (doseq [[bits sigma k] cases]
        (is (= (permute-at bits sigma k)
               (ca/with-seed (get seed-for-k k)
                 (#'gen/rule-permute bits sigma))))))))

(deftest rule-change-rate-mirror-invariance
  (testing "rate(m sigma m) = rate(sigma), >= 200 random sigmas"
    (let [rng (SplittableRandom. 20261115)]
      (doseq [_ (range 200)]
        (let [p (random-s8 rng)
              rate (gen/rule-change-rate (grid/sigma-of p))
              rate-m (gen/rule-change-rate (grid/sigma-of (mirror-s8 p)))]
          (is (= rate rate-m)))))))

(deftest pair-value-mirror-invariance
  (testing "X(m own m, m cand m) = X(own, cand), and X is symmetric, >= 200 pairs"
    (let [rng (SplittableRandom. 20261116)]
      (doseq [_ (range 200)]
        (let [own (random-s8 rng)
              cand (random-s8 rng)
              x (policy-epistemic/pair-value own cand)]
          (is (= x (policy-epistemic/pair-value (mirror-s8 own) (mirror-s8 cand))))
          (is (= x (policy-epistemic/pair-value cand own))))))))

(deftest chain-risk-mirror-equivariance
  (testing "risk(m sigma m, m b) = risk(sigma, b), >= 200 (sigma, byte) cases"
    (let [rng (SplittableRandom. 20261117)]
      (doseq [_ (range 200)]
        (let [p (random-s8 rng)
              b (.nextInt rng 256)
              r (chain-risk/risk p b)
              r-m (chain-risk/risk (mirror-s8 p) (mirror-byte b))]
          ;; the chain-risk column is a float sum; mirroring permutes the
          ;; summation order, so compare to within one-ULP-scale tolerance
          (is (<= (Math/abs (- (double r) (double r-m))) 1e-12)))))))

(deftest blend-value-mirror-equivariance
  (testing "blend(state, i) = blend(mirror state, width-1-i), >= 200 states"
    (let [rng (SplittableRandom. 20261118)]
      (doseq [_ (range 200)]
        (let [width 30
              i (.nextInt rng width)
              genotype (vec (repeatedly width #(ca/random-sigil)))
              phenotype (ca/random-phenotype-string width)
              ;; mirrored state: ring reversed, rules conjugated, phenotype reversed
              mirror-genotype (vec (reverse (map mirror-sigil genotype)))
              mirror-phenotype (apply str (reverse phenotype))
              state {:genotype genotype :phenotype phenotype}
              mirror-state {:genotype mirror-genotype :phenotype mirror-phenotype}
              v (policy-epistemic/blend-value state i)
              v-m (policy-epistemic/blend-value mirror-state (- width 1 i))]
          ;; weighted float sum; mirror permutes the order of summation only
          (is (<= (Math/abs (- (double v) (double v-m))) 1e-12)))))))

(deftest rule-permute-mirror-equivariance
  (testing "m(permute(sigma,k,b)) = permute(m sigma m, m(k), m(b)), >= 200 cases"
    (let [rng (SplittableRandom. 20261119)]
      (doseq [_ (range 200)]
        (let [p (random-s8 rng)
              sigma (grid/sigma-of p)
              bits (ca/bits-for (ca/sigil-for
                                 (apply str (repeatedly 8 #(.nextInt rng 2)))))
              k (mod (.nextInt rng 8) 8)
              ;; apply sigma at draw k, then mirror
              lhs (mirror-rule-bits (permute-at bits sigma k))
              ;; apply m sigma m at draw m(k) to the mirrored rule
              rhs (permute-at (mirror-rule-bits bits)
                              (grid/sigma-of (mirror-s8 p))
                              (nth m-idx k))]
          (is (= lhs rhs)))))))
