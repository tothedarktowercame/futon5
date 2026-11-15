(ns futon5.exotype.e8-mutation-operator-test
  "P-008b §7 item 5, step 5b-B: the two mutation operators.

  Tests c-k of the packet. Composition convention (DESIGN-P008b §7 item 5):
  in the futon5 vector p[i] is the image of neighbourhood i, so τ∘σ is
  p′[i] = τ(p[i]) -- a swap of the VALUES a and b, not of the entries at
  positions a and b.

  Tests h, i and j run small grids through the real futon5/exotype
  self-tuning/step (no engine script): the state is a minimal metaca-state
  with the s8-predictor faked via with-redefs on the efe/s8-predictor var
  (the method of e8-guard-test; the real defonce atom is untouched)."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as st]))

(def ^:private identity-v [0 1 2 3 4 5 6 7])

(defn- perm-sign
  "Sign of permutation P (a vector that is a permutation of 0..7): +1/-1 via
  the cycle count, sign = (-1)^(n - cycles)."
  [p]
  (let [seen (boolean-array 8)
        cycles (loop [i 0 c 0]
                 (if (= i 8)
                   c
                   (if (aget seen i)
                     (recur (inc i) c)
                     (do (loop [j i]
                           (when-not (aget seen j)
                             (aset seen j true)
                             (recur (nth p j))))
                         (recur (inc i) (inc c))))))]
    (if (even? (- 8 cycles)) 1 -1)))

(defn- random-perm [seed]
  (ca/with-seed seed (#'grid/random-s8)))

(defn- mutation-draw-hit
  "One guaranteed hit (mu 1.0) for exotype P with mutation OP, from a draw
  stream keyed on DRAW-KEY."
  [op draw-key p]
  (st/mutation-draw {:seed draw-key :time 0 :mutation op :mu 1.0
                     :exotypes (vec (repeat 8 :identity))}
                    0 p))

;; --- c + k: composition pin, hand case --------------------------------------

(deftest composition-pin-value-swap-not-position-swap
  (testing "p = [1 2 3 4 5 6 7 0], tau = (0 3) gives [1 2 0 4 5 6 7 3]"
    (let [p [1 2 3 4 5 6 7 0]
          value-swap (st/apply-transposition p 0 3)
          ;; the WRONG reading: swap the ENTRIES at positions 0 and 3
          position-swap (assoc (assoc (vec p) 3 (nth p 0)) 0 (nth p 3))]
      (is (= [1 2 0 4 5 6 7 3] value-swap))
      ;; hand-checked: every value 0 -> 3 and 3 -> 0, entries 1,2,4,5,6,7
      ;; untouched; [1 2 0 4 5 6 7 3] is still a permutation of 0..7.
      (is (= [4 2 3 1 5 6 7 0] position-swap))
      (is (not= value-swap position-swap)
          "the position-swap reading must NOT equal the value-swap answer")
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"defined on S8 only"
           (st/apply-transposition [0 0 1 2 3 4 5 6] 0 3))))))

;; --- d: transposition keeps permutations, flips parity ----------------------

(deftest transposition-keeps-perms-and-flips-parity
  (let [hits (for [n (range 1000)
                   :let [p (random-perm (+ 91000 n))
                         {:keys [post op]} (mutation-draw-hit
                                            :s8-transposition (+ 61000 n) p)]]
               [p post op])]
    (is (= 1000 (count hits)))
    (is (every? (fn [[_ post _]] (grid/perm? post)) hits)
        "every result is a permutation")
    (is (every? (fn [[p post _]] (= (- (perm-sign p)) (perm-sign post))) hits)
        "every result has the OPPOSITE sign to its input")))

;; --- e: the transposition is uniform over the 28 pairs ----------------------

(deftest transposition-is-uniform-over-28-pairs
  (let [hits (for [n (range 28000)]
               (mutation-draw-hit :s8-transposition (+ 62000 n) identity-v))
        counts (frequencies (map (fn [{:keys [op]}]
                                   (let [a (:a op) b (:b op)]
                                     (if (< a b) [a b] [b a])))
                                 hits))
        ;; multinomial: n=28000, p=1/28, mean 1000, SE = sqrt(1000*27/28) = 31.1
        se 31.1]
    (is (= 28 (count counts)) (str "distinct unordered pairs: " (count counts)))
    (is (every? (fn [pair] (not= (first pair) (second pair))) (keys counts))
        "a != b always")
    (doseq [pair (keys counts)]
      (is (<= (- 1000 (* 4 se)) (counts pair) (+ 1000 (* 4 se)))
          (str "pair " pair " count " (counts pair) " outside 1000 +- 4SE ("
               (* 4 se) ")")))
    (is (= 28000 (reduce + (vals counts))))))

;; --- f: stationarity by symmetry ---------------------------------------------

(deftest transposition-commutes-with-relabelling
  (testing "mutate-then-relabel = relabel-then-mutate with (g(a) g(b))"
    (let [checks (for [n (range 50)
                       :let [g (random-perm (+ 93000 n))
                             p (random-perm (+ 94000 n))
                             a (mod n 8)
                             b (mod (+ n 3) 8)]]
                   [(mapv g (st/apply-transposition p a b))
                    (st/apply-transposition (mapv g p) (nth g a) (nth g b))])]
      (is (every? (fn [[x y]] (= x y)) checks))))
  ;; Why this gives the uniform stationary distribution on S8: the operator
  ;; is equivariant under every relabelling g in S8 (this test), i.e. the
  ;; chain commutes with the conjugation action of S8 on itself. Starting
  ;; from any permutation, the distribution after t steps is conjugation-
  ;; equivariant in the initial point; the only probability distribution on
  ;; S8 invariant under all conjugations AND reachable by the chain (random
  ;; transpositions generate S8, so the chain is irreducible) is the uniform
  ;; one -- every conjugacy class is visited proportionally to its size and
  ;; within a class uniformly.
  )

;; --- g: point mutation statistics -------------------------------------------

(deftest point-mutation-leaves-s8-with-probability-7-8
  (let [hits (for [n (range 10000)]
               (mutation-draw-hit :e8-point (+ 65000 n) (random-perm (+ 95000 n))))
        results (map (fn [{:keys [pre post op]}]
                       {:pre pre :post post :op op
                        :left-s8? (not (grid/perm? post))})
                     hits)
        frac-left (/ (count (filter :left-s8? results)) 10000.0)
        ;; SE of 7/8 over 10000 = sqrt(7/8*1/8/10000) = 0.00331
        se 0.00331
        expected 10000.0
        kj-counts (frequencies (map (fn [{:keys [op]}] [(:k op) (:j op)]) results))
        chi2 (reduce + (for [k (range 8) j (range 8)]
                         (let [o (get kj-counts [k j] 0)
                               e (/ expected 64)]
                           (/ (* (- o e) (- o e)) e))))
        ;; chi-square dof 63: mean 63, sd sqrt(2*63) = 11.2; 4 sd bound ~ 108
        chi2-bound 108.0
        diffs (map (fn [{:keys [pre post]}]
                     (count (filter identity (map not= pre post)))) results)]
    (is (<= (- 0.875 (* 4 se)) frac-left (+ 0.875 (* 4 se)))
        (str "fraction leaving S8 " frac-left " outside 7/8 +- 4SE (" (* 4 se) ")"))
    (is (= 64 (count kj-counts)) "all 64 (k, j) pairs seen")
    (is (< chi2 chi2-bound)
        (str "chi-square " chi2 " over bound " chi2-bound
             " (dof 63, mean 63, sd 11.2)"))
    (is (every? #(<= % 1) diffs)
        "result differs from input in at most one position")))

;; --- h: the End(8) flag gates :e8-point --------------------------------------

(def ^:private small-width 12)

(defn- small-state
  "A minimal metaca-state for SMALL-WIDTH cells with vector identity
  exotypes (metaca-state's shape, small enough for quick steps)."
  [seed]
  (ca/with-seed seed
    (let [genotype (vec (ca/random-sigil-string small-width))]
      {:arm :efe-full
       :seed seed
       :time 0
       :self-tuning-arm :fixed-0.55
       :lambda-step-size 0.0
       :lambdas (vec (repeat small-width 0.55))
       :genotype genotype
       :previous-genotype genotype
       :phenotype (ca/random-phenotype-string small-width)
       :exotypes (vec (repeat small-width identity-v))
       :blend-action? true
       :epistemic-coefficient 0.1
       :blend-strength 0.0
       :apply-probability 1.0
       :policy-precision 10.0})))

(def ^:private fake-predictor
  (constantly {:activity 0.5 :diversity 0.5 :hunger 0.5}))

(defn- with-fake-predictor [thunk]
  (with-redefs [efe/s8-predictor (atom fake-predictor)]
    (thunk)))

(deftest e8-point-flag-gating
  (testing "flag off: transmit throws at the first call, zero steps completed"
    (with-fake-predictor
      (fn []
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo #"\*allow-end8\?\*"
             (st/transmit (assoc (small-state 2026102600)
                                 :mutation :e8-point :mu 1.0)))))))
  (testing "flag on: 20 steps run; non-injective vectors pass through step"
    (with-fake-predictor
      (fn []
        (let [final (binding [grid/*allow-end8?* true]
                       (nth (iterate st/step (assoc (small-state 2026102600)
                                                    :mutation :e8-point :mu 1.0))
                            20))]
          (is (some (complement grid/perm?) (:exotypes final))
              "at mu 1.0 some vectors have left S8 after 20 steps")
          (is (every? grid/s8? (:exotypes final)))
          ;; the same final vectors with the flag off throw from the guards
          ;; (mutation config removed so check-mutation! is not the thrower)
          (is (thrown-with-msg?
               clojure.lang.ExceptionInfo
               #"S8 exotype is not a permutation of 0\.\.7"
               (st/step (assoc final :mutation nil)))))))))

;; --- i: a mutated map acts one step later ------------------------------------

(deftest mutated-map-acts-one-step-later
  (with-fake-predictor
    (fn []
      (let [hit-seed (some (fn [s]
                             (when (st/mutation-draw {:seed s :time 0
                                                      :mutation :s8-transposition
                                                      :mu 1.0
                                                      :exotypes (vec (repeat small-width :identity))}
                                                     0 identity-v)
                               s))
                           (range 2026102600 2026102700))
            _ (assert (some? hit-seed) "no hitting seed found in the scan range")
            mutated0 (assoc (small-state hit-seed)
                            :mutation :s8-transposition :mu 1.0)
            clean0 (small-state hit-seed)
            st1 (st/step mutated0)
            ct1 (st/step clean0)
            st2 (st/step st1)]
        (testing "genotype and phenotype at t=1 are the UNMUTATED run's"
          (is (= (:genotype ct1) (:genotype st1)))
          (is (= (:phenotype ct1) (:phenotype st1))))
        (testing "the mutated vector is in :exotypes at t=1 (not before)"
          (is (some (fn [d] (let [h (:mutation-hit d)]
                              (and h (not= (:pre h) (:post h)))))
                    (:efe-decisions st1))
              "the t=0 decisions record a hit with pre != post")
          (is (some (fn [e] (not= identity-v e)) (:exotypes st1))
              "a non-identity vector is present in the t=1 exotypes"))
        (testing "it acts from t=1 to t=2: cell 1's t=2 candidates see it"
          (let [neighbour-decision (some (fn [d] (when (= 1 (:index d)) d))
                                         (:efe-decisions st2))]
            (is (some (fn [c] (and (vector? (:candidate-exotype c))
                                   (not= identity-v (:candidate-exotype c))))
                      (:candidates neighbour-decision))
                "the mutated vector appears among a neighbour's candidates")))
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo #"defined on S8 only"
             (st/mutation-draw {:seed hit-seed :time 0 :mutation :s8-transposition
                                :mu 1.0 :exotypes (vec (repeat small-width :identity))}
                               0 [0 0 1 2 3 4 5 6]))
            "transposition refuses a non-permutation winner")))))

;; --- j: keyword winners are CONVERTED to their S8 twin and mutated -------------
;; (ruling 2026-11-15, exotype main 5176228: the :skipped-keyword placeholder
;; for the 12 selectable kinds is removed; see e8-mutation-keyword-twin-test
;; for the full coverage. This test now pins the operator-level expectation.)

(deftest keyword-winner-is-skipped-and-recorded
  (with-fake-predictor
    (fn []
      (let [kw0 (-> (small-state 2026102600)
                    (assoc :exotypes (mapv (fn [i] (nth (take 3 grid/exotype-kinds)
                                                        (mod i 3)))
                                           (range small-width))
                           :mutation :s8-transposition :mu 1.0))
            after (st/step kw0)
            hits (filter :mutation-hit (:efe-decisions after))]
        (is (= small-width (count hits)) "at mu 1.0 every cell hits")
        (is (every? #(keyword? (get-in % [:mutation-hit :pre])) hits)
            ":pre stays the keyword")
        (is (every? #(= (grid/kind->s8 (get-in % [:mutation-hit :pre]))
                        (get-in % [:mutation-hit :pre-vector])) hits)
            ":pre-vector is the winner's twin")
        (is (every? #(= (:post (:mutation-hit %))
                        (st/apply-transposition (:pre-vector (:mutation-hit %))
                                                (-> % :mutation-hit :op :a)
                                                (-> % :mutation-hit :op :b)))
                    hits)
            ":post is the transposed twin, per the recorded :op pair")
        (is (every? vector? (:exotypes after))
            "converted cells are vector cells from the next step")))))
