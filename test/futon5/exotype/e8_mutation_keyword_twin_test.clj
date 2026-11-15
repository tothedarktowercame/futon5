(ns futon5.exotype.e8-mutation-keyword-twin-test
  "P-008b §7 item 6 (ruling 2026-11-15, exotype main 5176228): on a mutation
  hit a keyword kind is REPLACED by its S8 twin (grid/kind->s8) and the
  operator is applied to that vector. Registration: exotype LEDGER
  c1/mutation-keyword-twin (bd4a23d), committed before these tests ran.

  Everything runs against the real futon5/exotype code with the s8-predictor
  faked via with-redefs on the efe/s8-predictor var (the e8-guard-test
  method): the real pooled predictor (item 4) is not frozen and has no rows
  for vector exotypes. The keyword score cache is the real frozen one
  (self-tuning/score-cache delay, populated through efe/score-policy).

  with-redefs bodies here are exception-free by construction (the throwing
  tests are not wrapped -- with-redefs does not restore its var on throw)."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as st]))

(def ^:private odd53-twin [2 0 1 4 7 3 5 6])

(def ^:private draw-state
  {:seed 2026115500 :time 0 :mu 1.0 :mutation :s8-transposition
   :exotypes (vec (repeat 8 :identity))})

(defn- replayed-pair
  "Recompute the operator pair from the stream: rate draw, then the
  :s8-transposition draws, inside the same keyed scope."
  [seed time width index]
  (ca/with-seed (st/mutation-draw-seed seed time width index)
    (let [_rate (ca/rnd-int 10000)
          a (ca/rnd-int 8)
          b0 (ca/rnd-int 7)]
      [a (if (>= b0 a) (inc b0) b0)])))

(defn- replayed-kj
  "Recompute (k, j) from the stream: rate draw, then the :e8-point draws."
  [seed time width index]
  (ca/with-seed (st/mutation-draw-seed seed time width index)
    (let [_rate (ca/rnd-int 10000)]
      [(ca/rnd-int 8) (ca/rnd-int 8)])))

;; --- 4a: forced :odd53 hit under :s8-transposition ---------------------------

(deftest forced-hit-odd53-transposition
  (let [hit (st/mutation-draw draw-state 0 :odd53)
        [a b] (replayed-pair 2026115500 0 8 0)]
    (testing "the record shape"
      (is (= :odd53 (:pre hit)) ":pre stays the keyword")
      (is (= odd53-twin (:pre-vector hit)) ":pre-vector is the pinned twin")
      (is (vector? (:post hit)) ":post is a vector")
      (is (= {:kind :s8-transposition :a a :b b} (:op hit))
          ":op as today, with the pair drawn from the stream"))
    (testing ":post is apply-transposition of the TWIN with the drawn pair"
      (is (= (st/apply-transposition odd53-twin a b) (:post hit)))
      (is (= odd53-twin (grid/kind->s8 :odd53)) "twin cross-check"))))

;; --- 4b: the same under :e8-point, flag on; flag off throws early ------------

(deftest forced-hit-odd53-e8-point
  (let [hit (binding [grid/*allow-end8?* true]
              (st/mutation-draw (assoc draw-state :mutation :e8-point) 0 :odd53))
        [k j] (replayed-kj 2026115500 0 8 0)]
    (is (= :odd53 (:pre hit)))
    (is (= odd53-twin (:pre-vector hit)))
    (is (= (st/apply-point odd53-twin k j) (:post hit)))
    (is (= {:kind :e8-point :k k :j j} (:op hit)))))

;; --- 4b: flag off throws before any decision (transmit-level) ----------------

(def ^:private width 12)

(defn- keyword-state
  "The e8-bug-grid state-with shape: exotypes are the 12 keyword kinds, no
  shaping keys, so keyword candidates take the real frozen fast path."
  [seed]
  (ca/with-seed seed
    (let [genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full
       :seed seed
       :time 0
       :self-tuning-arm :fixed-0.55
       :lambda-step-size 0.0
       :lambdas (vec (repeat width 0.55))
       :genotype genotype
       :previous-genotype genotype
       :phenotype (ca/random-phenotype-string width)
       :exotypes (vec grid/exotype-kinds)})))

(deftest e8-point-flag-off-throws-before-any-decision
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo #"\*allow-end8\?\*"
       (st/transmit (assoc (keyword-state 2026115600)
                           :mutation :e8-point :mu 1.0)))))

;; --- 4c: the mutated vector acts from the next step --------------------------

(def ^:private fake-row {:activity 0.0 :diversity 1.0 :hunger 0.0})

(defn- with-fake-predictor [thunk]
  (with-redefs [efe/s8-predictor (atom (constantly fake-row))]
    (thunk)))

(deftest converted-cell-acts-one-step-later
  (with-fake-predictor
    (fn []
      (let [base (keyword-state 2026115600)
            mutated0 (assoc base :mutation :s8-transposition :mu 1.0)
            st1 (st/step mutated0)
            ct1 (st/step base)
            st2 (st/step st1)]
        (testing "t=1 genotype/phenotype are the UNMUTATED run's (hit after winner)"
          (is (= (:genotype ct1) (:genotype st1)))
          (is (= (:phenotype ct1) (:phenotype st1))))
        (testing "every hit record is a conversion (mu 1.0, all 12 kinds twin-able)"
          (is (= width (count (filter :mutation-hit (:efe-decisions st1)))))
          (is (every? (fn [d]
                        (let [h (:mutation-hit d)]
                          (and (keyword? (:pre h))
                               (vector? (:pre-vector h))
                               (vector? (:post h))
                               (not= (:pre-vector h) (:post h)))))
                      (filter :mutation-hit (:efe-decisions st1)))))
        (testing "the cells are VECTOR cells from t=1 on"
          (is (every? vector? (:exotypes st1)))
          (is (every? vector? (:exotypes st2))))
        (testing "the vectors act from t=1 to t=2: each t=2 decision's own
                  candidate is the converted vector"
          (is (every? (fn [d]
                        (some (fn [c] (vector? (:candidate-exotype c)))
                              (:candidates d)))
                      (:efe-decisions st2))))))))

;; --- 4d: 200 steps from the 12 kinds, mutation on: zero skipped --------------

(def ^:private long-width 60)

(defn- long-state [seed]
  (ca/with-seed seed
    (let [genotype (vec (ca/random-sigil-string long-width))]
      {:arm :efe-full
       :seed seed
       :time 0
       :self-tuning-arm :fixed-0.55
       :lambda-step-size 0.0
       :lambdas (vec (repeat long-width 0.55))
       :genotype genotype
       :previous-genotype genotype
       :phenotype (ca/random-phenotype-string long-width)
       ;; the 12 kinds assigned as :heterogeneous-fixed assigns them:
       ;; one ca/with-seed scope, rnd-nth per cell
       :exotypes (vec (repeatedly long-width #(ca/rnd-nth grid/exotype-kinds)))})))

(defn- hit-stats [seed]
  (with-fake-predictor
    (fn []
      (let [state0 (assoc (long-state seed)
                          :mutation :s8-transposition :mu 0.01)
            steps (take 200 (iterate st/step state0))
            hits (mapcat (fn [s] (keep :mutation-hit (:efe-decisions s))) steps)
            skipped (filter (fn [h] (= :skipped-keyword (get-in h [:op :kind]))) hits)
            conversions (filter (fn [h] (and (keyword? (:pre h)) (vector? (:post h))))
                                hits)
            vector-hits (filter (fn [h] (vector? (:pre h))) hits)]
        {:skipped (count skipped)
         :conversions (count conversions)
         :vector-hits (count vector-hits)
         :hits (count hits)}))))

(deftest two-hundred-steps-from-kinds-zero-skipped
  (let [stats (hit-stats 2026115700)]
    (testing "zero :skipped-keyword records; conversions reported"
      (is (zero? (:skipped stats))
          (str "skipped records: " (:skipped stats)))
      (is (pos? (:conversions stats))
          (str "conversion count (seed 2026115700, mu 0.01, 200 steps, "
               "width 60): " (:conversions stats) " of " (:hits stats)
               " hits; the remaining " (- (:hits stats) (:conversions stats))
               " hits are mutations of ALREADY-CONVERTED (vector) winners"))
      (is (= (:hits stats) (+ (:skipped stats) (:conversions stats)
                              (:vector-hits stats)))))
    (testing "row-source: :keyword-cache before the first conversion (t=0)"
      (with-fake-predictor
        (fn []
          (is (= [:keyword-cache]
                 (distinct
                  (map :row-source
                       (:efe-decisions
                        (st/step (assoc (long-state 2026115700)
                                        :mutation :s8-transposition :mu 0.01))))))))))
    (testing "row-source absent when mutation is off"
      (with-fake-predictor
        (fn []
          (is (every? (complement :row-source)
                      (:efe-decisions (st/step (long-state 2026115700))))))))
    (testing "row-source :s8-predictor for a vector winner"
      ;; one step from kinds at mu 1.0 converts every cell; the t=1 decisions
      ;; (mutation still on) score the converted vectors
      (with-fake-predictor
        (fn []
          (let [st1 (st/step (assoc (long-state 2026115700)
                                    :mutation :s8-transposition :mu 1.0))
                st2 (st/step st1)]
            (is (= [:s8-predictor]
                   (distinct (map :row-source (:efe-decisions st2)))))))))))

;; --- 4e: mutation off unchanged -- covered by e8-mutation-schedule-test
;; (off-is-byte-identical-g1, G1 957e93e21609fb79) and the schedule draws
;; (c/d/e), rerun untouched in the same JVM batch as this namespace.

;; --- 4f: the bad cases for item 2 ---------------------------------------------

(deftest fake-kind-stays-and-is-recorded-skipped
  (testing "a keyword with no entry in grid/propagators: the rate draw
            happened, the kind is left as it is"
    (let [hit (st/mutation-draw draw-state 0 :no-such-kind)]
      (is (some? hit) "mu 1.0: the rate draw hit")
      (is (= :no-such-kind (:pre hit)))
      (is (= :no-such-kind (:post hit)) "the kind is left as it is")
      (is (nil? (:pre-vector hit)))
      (is (= {:kind :skipped-keyword} (:op hit)))))
  (testing "a kind whose twin is not a permutation (non-bijective sigma):
            skipped, kind left as it is"
    ;; a constant sigma: every neighbourhood maps to the same truth-table
    ;; entry, so kind->s8 is a constant vector -- not a permutation
    (with-redefs [grid/propagators (assoc grid/propagators
                                          :fake-nonperm
                                          (zipmap (vec ca/truth-table-3)
                                                  (repeat (nth (vec ca/truth-table-3) 0))))]
      (let [twin (grid/kind->s8 :fake-nonperm)]
        (is (not (grid/perm? twin)) "the fake twin is not a permutation")
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo #"defined on S8 only"
             (st/apply-transposition twin 0 1))
            "sanity: the operator is not defined on the fake twin")
        (let [hit (st/mutation-draw draw-state 0 :fake-nonperm)]
          (is (= :fake-nonperm (:pre hit)))
          (is (= :fake-nonperm (:post hit)))
          (is (= {:kind :skipped-keyword} (:op hit))))))))
