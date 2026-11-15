(ns futon5.exotype.e8-bug-grid-test
  "P-008b §7 item 6, E8-1: the :e8-bug mixed keyword-and-vector initial grid
  and the engine stepping it (SHEET reference; zai-12, futon5 branch
  p008-s8-vocabulary; registration in the exotype repo LEDGER 'e8bug').

  All vectors in the futon5 index (Wolfram) order. The grid is built
  SCRIPT-SIDE: the menu is the 12 keyword kinds with the bug and its three
  Gamma-conjugates APPENDED (positions 12-15), and cells are assigned exactly
  as initial-grid's :heterogeneous-fixed assigns among the 12 -- one
  ca/with-seed scope, ca/rnd-nth per cell. NO src/ change is needed for the
  grid or the stepping; the s8-predictor is faked via with-redefs on the
  efe/s8-predictor var (the e8-guard-test method; the real defonce atom is
  untouched), because the real pooled predictor (item 4) is not frozen and
  has NO rows for vector exotypes.

  Two JVM-ordering hazards this file is written around (measured, reported in
  results/illustrations/e8bug.txt):
  1. self-tuning/s8-score-memo MEMOISES scores per (exotype, activity,
     diversity): once a vector is scored under one predictor, a later
     predictor swap is invisible for the same key. The no-predictor test
     therefore uses a FRESH vector (never scored earlier in this JVM).
  2. with-redefs does NOT restore its var if its body throws: every
     with-redefs body below is exception-free by construction."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as st]))

(def bug [1 2 4 5 3 6 7 7])
(def c [7 6 5 4 3 2 1 0])
(def m [0 4 2 6 1 5 3 7])

(defn compose [g f] (mapv #(nth g (nth f %)) (range 8)))

(defn conj-g [g f]
  (let [ginv (vec (for [i (range 8)] (.indexOf ^java.util.List g i)))]
    (mapv #(nth g (nth f (nth ginv %))) (range 8))))

(def bug-conj {:id bug
               :c (conj-g c bug)
               :m (conj-g m bug)
               :cm (conj-g (compose c m) bug)})

(def width 60)

(defn menu-grid
  "Cells assigned among MENU exactly as initial-grid's :heterogeneous-fixed:
  one ca/with-seed scope, ca/rnd-nth per cell."
  [seed w menu]
  (ca/with-seed seed
    (vec (repeatedly w #(ca/rnd-nth menu)))))

(def mixed-menu (vec (concat grid/exotype-kinds (vals bug-conj))))

(defn- state-with
  "A minimal metaca-state (the e8-mutation-operator-test shape) whose
  exotypes are GRID-VEC. No :epistemic-coefficient / :blend-action?, so
  keyword candidates take the cached fast path and a candidate's score
  depends only on (exotype, activity, diversity) -- the local-configuration
  comparison below needs exactly that. No :policy-precision: the winner is
  the argmin, so adoption tracking is deterministic."
  [seed grid-vec]
  (ca/with-seed seed
    (let [genotype (vec (ca/random-sigil-string (count grid-vec)))]
      {:arm :efe-full
       :seed seed
       :time 0
       :self-tuning-arm :fixed-0.55
       :lambda-step-size 0.0
       :lambdas (vec (repeat (count grid-vec) 0.55))
       :genotype genotype
       :previous-genotype genotype
       :phenotype (ca/random-phenotype-string (count grid-vec))
       :exotypes grid-vec})))

(def mixed-state (state-with 2026115400 (menu-grid 2026115400 width mixed-menu)))
(def keyword-state (state-with 2026115400 (menu-grid 2026115400 width grid/exotype-kinds)))

(def ^:private fake-row {:activity 0.0 :diversity 1.0 :hunger 0.0})

(defn- with-fake-predictor [thunk]
  (with-redefs [efe/s8-predictor (atom (constantly fake-row))]
    (thunk)))

(def kw? (complement vector?))

(defn- transmit-on
  "st/transmit with the End(8) flag bound on. Never throws under the fake
  predictor (probe-verified), so it is safe inside with-redefs."
  [state]
  (binding [grid/*allow-end8?* true]
    (st/transmit state)))

;; --- (a) conjugates: g.bug.g^-1, distinct, one fg-class ----------------------

(deftest conjugates-and-gamma
  (testing "R-c pins: c, m, c.m = m.c, m.m = id"
    (let [id (vec (range 8))]
      (is (= c [7 6 5 4 3 2 1 0]))
      (is (= m [0 4 2 6 1 5 3 7]))
      (is (= (compose c m) (compose m c)))
      (is (= (compose m m) id))))
  (testing "conjugates pinned (b3664da), 4 distinct, one fg-class"
    (is (= [1 2 4 5 3 6 7 7] (:id bug-conj)))
    (is (= [0 0 1 4 2 3 5 6] (:c bug-conj)))
    (is (= [4 6 1 7 2 3 5 7] (:m bug-conj)))
    (is (= [0 2 4 5 0 6 1 3] (:cm bug-conj)))
    (is (= 4 (count (distinct (vals bug-conj)))))
    (is (= 1 (count (distinct (map grid/fg-class (vals bug-conj)))))
        "all four conjugates one functional-graph class")
    (is (= [0 0 1 2 3 4 5 6] (grid/fg-class bug)))))

;; --- (b) construction reduces to today's grid -------------------------------

(deftest grid-construction-reduces
  (is (= (menu-grid 2026115300 width grid/exotype-kinds)
         (ca/with-seed 2026115300 (grid/initial-grid :heterogeneous-fixed width))))
  (is (= (menu-grid 2026115401 width grid/exotype-kinds)
         (ca/with-seed 2026115401 (grid/initial-grid :heterogeneous-fixed width))))
  (let [g (:exotypes mixed-state)]
    (is (some kw? g))
    (is (some vector? g))
    (is (= mixed-menu (vec (concat grid/exotype-kinds (vals bug-conj))))
        "the 4 extra entries enter the draw as menu positions 12-15")))

;; --- (c) flag on: stepping works, every cell has scored candidates ----------

(deftest mixed-grid-steps-flag-on
  (with-fake-predictor
    (fn []
      (let [step (fn [s] (binding [grid/*allow-end8?* true] (st/step s)))
            final (nth (iterate step mixed-state) 20)
            decisions (:decisions (transmit-on final))]
        (is (= width (count (:exotypes final))))
        (is (= width (count decisions)))
        (is (every? #(= 3 (count (:candidates %))) decisions)
            "hold + adopt-left + adopt-right for every cell")
        (is (every? #(every? (fn [cnd] (and (number? (:total cnd))
                                            (map? (:prediction cnd))))
                             (:candidates %))
                    decisions)
            "every candidate carries a numeric total and a prediction row")))))

;; --- (d) flag off: throws before any decision is usable ---------------------

(deftest mixed-grid-flag-off-throws
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"S8 exotype is not a permutation of 0\.\.7"
       (st/transmit mixed-state))))

;; --- (e) model rows per type; no silent keyword fallback ---------------------

(deftest model-rows-per-type
  (with-fake-predictor
    (fn []
      (let [decisions (:decisions (transmit-on mixed-state))
            all-cands (mapcat :candidates decisions)
            vec-cands (filter (comp vector? :candidate-exotype) all-cands)
            kw-cands (filter (comp kw? :candidate-exotype) all-cands)]
        (is (pos? (count vec-cands)) "vector candidates offered")
        (is (pos? (count kw-cands)) "keyword candidates offered")
        ;; the fake returns the marker row; a keyword score-cache row never
        ;; carries activity exactly 0.0 with hunger exactly 0.0 for a vector
        (is (every? #(and (zero? (get-in % [:prediction :activity]))
                          (zero? (get-in % [:prediction :hunger])))
                    vec-cands)
            "vector candidates score from the installed predictor row")
        (is (some #(= :builder (:candidate-exotype %)) all-cands)
            "keyword candidates come from the keyword cache (checked fully
             in keyword-scores-unchanged)"))))
  (testing "a vector candidate with NO predictor throws; fresh vector so the
            s8-score memo cannot already hold its row"
    ;; f-cold is not one of the 16 menu vectors, so nothing earlier in this
    ;; JVM scored it (memo hazard 1 in the ns docstring)
    (let [f-cold [1 1 2 3 4 5 6 6]
          cold-state (assoc mixed-state :exotypes (mapv #(if (vector? %) f-cold %)
                                                        (:exotypes mixed-state)))]
      (is (some #{f-cold} (:exotypes cold-state)))
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"no pooled predictor installed"
           (with-redefs [efe/s8-predictor (atom nil)]
             (binding [grid/*allow-end8?* true]
               (st/transmit cold-state))))))))

;; --- (f) keyword scores identical in mixed vs all-keyword grid; bad cache ----

(deftest keyword-scores-unchanged-and-cache-keys
  (with-fake-predictor
    (fn []
      (testing "keyword candidate scores are identical in mixed and all-keyword grids"
        (let [m-grid (:exotypes mixed-state)
              k-grid (:exotypes keyword-state)
              offers (fn [g i] (set (map #(nth g (mod % width))
                                         [(dec i) i (inc i)])))
              i (some (fn [j]
                        (when (and (contains? (offers m-grid j) :builder)
                                   (contains? (offers k-grid j) :builder))
                          j))
                      (range width))]
          (is i "both grids offer :builder to some cell")
          (let [decM (:decisions (transmit-on mixed-state))
                decK (:decisions (st/transmit keyword-state))
                at (fn [ds j] (some #(when (= j (:index %)) %) ds))
                candM (some #(when (= :builder (:candidate-exotype %)) %)
                            (:candidates (at decM i)))
                candK (some #(when (= :builder (:candidate-exotype %)) %)
                            (:candidates (at decK i)))]
            (is (and candM candK))
            (is (= (dissoc candM :policy :source)
                   (dissoc candK :policy :source))
                "the keyword candidate's score is identical in the mixed and
                 all-keyword grids (same observation, same exotype)"))))
      (testing "bad case: a cache keyed WITHOUT the exotype would collide"
        (let [cache @@#'st/score-cache
              keys-without-type (frequencies
                                  (map (fn [[[_e a d]]] [a d]) cache))]
          (is (every? (fn [[[e _ _]]] (keyword? e)) cache)
              "the real cache key includes the exotype itself")
          (is (some (fn [[_ n]] (> n 1)) keys-without-type)
              "12 kinds share only 6 (activity, diversity) pairs, so a
               type-blind key would return one kind's row for another")
          ;; constructed collision: a type-blind cache returns ONE kind's row
          ;; for every exotype at that observation -- here whichever kind
          ;; hashed last; it demonstrably differs from :builder's own row,
          ;; i.e. it silently mis-scores some other kind (and would the bug)
          (let [blind (into {} (map (fn [[[_e a d] v]] [[a d] v]) cache))]
            (is (not= (get blind [0.0 (/ 1.0 3.0)])
                      (get cache [:builder 0.0 (/ 1.0 3.0)]))
                "the type-blind lookup at :builder's own observation does not
                 even return :builder's row -- one row serves all 12 kinds")
            (is (contains? blind [0.0 (/ 1.0 3.0)]))))))))

;; --- (g) adoption across the type boundary -----------------------------------

(deftest adoption-across-types
  (with-fake-predictor
    (fn []
      (let [step (fn [s] (binding [grid/*allow-end8?* true] (st/step s)))
            states (take 20 (rest (iterate step mixed-state)))
            changes (fn [before after]
                      (keep (fn [i]
                              (let [o (nth (:exotypes before) i)
                                    n (nth (:exotypes after) i)]
                                (when (not= o n) [o n])))
                            (range width)))
            all-changes (mapcat changes (cons mixed-state states) states)
            kw->vec (count (filter (fn [[o n]] (and (kw? o) (vector? n)))
                                   all-changes))
            vec->kw (count (filter (fn [[o n]] (and (vector? o) (kw? n)))
                                   all-changes))]
        (is (pos? kw->vec) "a keyword cell adopts a vector neighbour")
        ;; measured: vec->kw is 0 over this trajectory -- with the attractive
        ;; fake row the vectors always win the argmin, so a vector cell never
        ;; adopts a keyword. The forced 3-cell case below covers vec->kw.
        (is (zero? vec->kw)))))
  (testing "forced 3-cell case: a vector cell adopts a keyword neighbour"
    ;; with the ATTRACTIVE fake row the vectors always win, so vec->kw never
    ;; occurs in the trajectory (measured). Force it: an UNATTRACTIVE fake row
    ;; (high hunger, low diversity) plus a keyword (:chaos, the structural
    ;; argmin) between two vector cells. f-cold is a fresh vector, so the
    ;; s8-score memo cannot hold an earlier row for it (memo hazard 1).
    (let [f-cold [1 1 2 3 4 5 6 6]
          s (assoc mixed-state
                   :exotypes [f-cold :chaos f-cold]
                   :lambdas [0.55 0.55 0.55]
                   :genotype (mapv #(nth (:genotype mixed-state) %) [0 1 2])
                   :previous-genotype (mapv #(nth (:genotype mixed-state) %) [0 1 2])
                   :phenotype (subs (:phenotype mixed-state) 0 3))
          next-exotypes (with-redefs [efe/s8-predictor
                                      (atom (constantly {:activity 2/3 :diversity 1/3
                                                         :hunger 1.0}))]
                          (binding [grid/*allow-end8?* true]
                            (:exotypes (st/transmit s))))]
      (is (= [:chaos :chaos :chaos] next-exotypes)
          "both vector cells adopt the :chaos keyword neighbour"))))
