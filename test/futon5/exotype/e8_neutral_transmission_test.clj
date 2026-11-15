(ns futon5.exotype.e8-neutral-transmission-test
  "P-008b §8 baseline (rulings R-a, R-b, DESIGN-P008b main dbb6c60):
  :transmission :neutral -- the winner is drawn uniformly from exactly the
  candidates the cell has (3, or 4 with :blend-action?), independent of the
  scores. Registration: exotype LEDGER c1/neutral-transmission (664ece8),
  committed before these tests ran.

  Keyword cells score from the real frozen cache; vectors (after conversion)
  need the not-frozen pooled predictor, faked via with-redefs on
  efe/s8-predictor (e8-guard-test method). with-redefs bodies here are
  exception-free by construction."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as st]))

(def ^:private width 60)

(defn- base-state
  "12-kind grid assigned as :heterogeneous-fixed assigns them, in the
  e8-mutation-keyword-twin-test state shape (fast keyword path)."
  [seed blend?]
  (ca/with-seed seed
    (let [genotype (vec (ca/random-sigil-string width))]
      (cond-> {:arm :efe-full
               :seed seed
               :time 0
               :self-tuning-arm :fixed-0.55
               :lambda-step-size 0.0
               :lambdas (vec (repeat width 0.55))
               :genotype genotype
               :previous-genotype genotype
               :phenotype (ca/random-phenotype-string width)
               :exotypes (vec (repeatedly width #(ca/rnd-nth grid/exotype-kinds)))}
        blend? (assoc :blend-action? true)))))

(def ^:private fake-row {:activity 0.0 :diversity 1.0 :hunger 0.0})

(defn- with-fake-predictor [thunk]
  (with-redefs [efe/s8-predictor (atom (constantly fake-row))]
    (thunk)))

(defn- run
  "Steps the state T steps, returning all decision maps (the initial state
  carries none, so take T+1 states for T steps of decisions)."
  [state t]
  (mapcat (fn [s] (:efe-decisions s))
          (take (inc t) (iterate st/step state))))

;; --- 1 + 2: candidate order pinned; chi-square uniformity --------------------

(deftest candidate-order-and-uniformity
  (with-fake-predictor
    (fn []
      (let [draws (fn [blend?]
                    (keep :transmission
                          (run (assoc (base-state 2026115800 blend?)
                                      :transmission :neutral)
                               200)))
            n3 (filter #(= 3 (:n %)) (draws false))
            n4 (filter #(= 4 (:n %)) (draws true))
            chi2 (fn [ds n]
                   (let [e (/ (count ds) n)]
                     (reduce + (for [k (range n)]
                                 (let [o (count (filter #(= k (:draw %)) ds))]
                                   (/ (* (- o e) (- o e)) e))))))]
        (testing "candidate order the draw indexes into (pinned)"
          ;; [:hold :adopt-left :adopt-right] (+ :blend last): the draw k
          ;; selects (nth candidates k); check via one step's decisions
          (let [ds (:efe-decisions (st/step (assoc (base-state 2026115800 true)
                                                   :transmission :neutral)))]
            (is (= [:hold :adopt-left :adopt-right :blend]
                   (mapv :policy (:candidates (first ds)))))
            (is (every? (fn [d]
                          (= (:policy (nth (:candidates d)
                                           (:draw (:transmission d))))
                             (:policy (:winner d))))
                        ds)
                "the drawn index selects that candidate")))
        (testing "n is 3 without :blend-action?, 4 with it"
          (is (= 3 (:n (first n3))))
          (is (= 4 (:n (first n4)))))
        (testing (str "draw uniform over 0..n-1, width 60 x 200 steps: "
                      "n=3 chi2 " (format "%.1f" (double (chi2 n3 3))) " (df 2), "
                      "n=4 chi2 " (format "%.1f" (double (chi2 n4 4))) " (df 3)")
          (is (= (* width 200) (count n3)) "one draw per cell per step")
          (is (= (* width 200) (count n4)))
          ;; chi-square 4-sigma-ish bounds: df 2 mean 2 sd 2.0 -> 10;
          ;; df 3 mean 3 sd 2.45 -> 13
          (is (< (chi2 n3 3) 10.0))
          (is (< (chi2 n4 4) 13.0)))))))

;; --- 3: independence from scores (the bad case) ------------------------------

(deftest neutral-draw-is-independent-of-scores
  (with-fake-predictor
    (fn []
      (let [ds (run (assoc (base-state 2026115800 true)
                           :transmission :neutral)
                    200)
            same (count (filter (fn [d] (= (:policy (:winner d))
                                           (:policy (:score-winner d))))
                                ds))
            total (count ds)
            share (/ same (double total))
            ;; binomial p=1/4, n=12000: SE = sqrt(.25*.75/12000) = 0.00395
            se 0.00395]
        (is (pos? (count (filter (fn [d] (not= (:winner d) (:score-winner d)))
                                  ds)))
            "the draw differs from selection somewhere")
        (is (<= (- 0.25 (* 4 se)) share (+ 0.25 (* 4 se)))
            (str "winner = score-winner share " (format "%.4f" share)
                 " outside 1/4 +- 4SE (an implementation that draws but still"
                 " takes the argmin scores 1.0)"))
        (testing "without the option the winner IS the score winner"
          (let [plain (run (base-state 2026115800 true) 20)]
            (is (every? (fn [d] (and (nil? (:transmission d))
                                      (nil? (:score-winner d))))
                        plain)
            (is (every? (fn [d] (= (:policy (:winner d))
                                   (:policy (first (sort-by :total (:candidates d))))))
                        plain)))))))))



;; --- 4: adjacent seeds; stream distinctness -----------------------------------

(deftest adjacent-seeds-and-stream-distinctness
  (with-fake-predictor
    (fn []
      (let [draws (fn [seed]
                    (mapv (fn [d] [(:n (:transmission d)) (:draw (:transmission d))])
                          (take width (:efe-decisions
                                      (st/step (assoc (base-state seed true)
                                                      :transmission :neutral))))))
            a (draws 2026115800)
            b (draws 2026115801)
            overlap (count (filter true? (map = a b)))]
        (is (not= a b) "adjacent seeds give different draw fields")
        (is (<= (- 15 (* 4 3.35)) overlap (+ 15 (* 4 3.35)))
            (str "equal draws at chance 1/4: " overlap " of " width
                 " (expected 15, SE 3.35)")))))
  (testing "neutral stream key != mutation stream key for the same
            (seed, t, i): no equal values over 10000 keys"
    (let [keys (for [t (range 100)
                     i (range 100)]
                 [2026115800 t width i])
          neutral (map (fn [[s t w i]] (st/neutral-draw-seed s t w i)) keys)
          mutation (map (fn [[s t w i]] (st/mutation-draw-seed s t w i)) keys)]
      (is (= 10000 (count keys)))
      (is (zero? (count (filter true? (map = neutral mutation)))))))
  (testing "unknown :transmission values throw before any decision"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"unknown :transmission value"
         (st/transmit (assoc (base-state 2026115800 true)
                             :transmission :score))))))

;; --- 5: mutation streams do not interact ---------------------------------------

(deftest mutation-schedule-identical-under-neutral
  (with-fake-predictor
    (fn []
      (let [schedule (fn [state]
                       (map (fn [d]
                              (let [h (:mutation-hit d)]
                                [(if h true false)
                                 (when h (select-keys (:op h) [:kind :a :b]))]))
                            (run state 100)))
            s-plain (schedule (assoc (base-state 2026115800 true)
                                     :mutation :s8-transposition :mu 0.01))
            s-neutral (schedule (assoc (base-state 2026115800 true)
                                       :mutation :s8-transposition :mu 0.01
                                       :transmission :neutral))]
        (is (= (count s-plain) (count s-neutral)))
        (is (= s-plain s-neutral)
            "hit pattern and operator draws identical with and without the
             neutral option (the streams do not interact)")
        (is (some true? (map first s-neutral)) "at least one hit occurred")))))
