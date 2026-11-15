(ns futon5.exotype.exact-rows-test
  "The exact rows are checked against independent, slower computations of the
   same reference ensemble (see the namespace docstring of `exact-rows`)."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.exact-rows :as exact]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]
            [futon5.xenotype.generator :as gen]))

;; --- an independent model of one step, written with strings and sets --------

(defn- bits [rule] (let [s (Integer/toBinaryString rule)] (str (apply str (repeat (- 8 (count s)) "0")) s)))
(defn- write [rule-bits position value] (str (subs rule-bits 0 position) value (subs rule-bits (inc position))))
(defn- flip [c] (if (= \0 c) \1 \0))
(defn- next-bit [rule-bits l c r] (nth rule-bits (- 7 (+ (* 4 l) (* 2 c) r))))

(defn- sigma-next
  "Distribution (a map to probability) of the centre's next rule."
  [sigma rule-bits]
  (apply merge-with + (for [k (range 8)] {(write rule-bits (nth sigma k) (flip (nth rule-bits k))) 1/8})))

(defn- background-next [rule-bits]
  (apply merge-with + (for [k (range 8) p (range 8)] {(write rule-bits p (flip (nth rule-bits k))) 1/64})))

(defn- expected-distinct [sigma l c r]
  (let [dl (background-next (bits l)) dc (sigma-next sigma (bits c)) dr (background-next (bits r))]
    (reduce + (for [[x px] dl [y py] dc [z pz] dr] (* px py pz (count (distinct [x y z])))))))

(defn- slow-row
  "Exact row for SIGMA at bin [a d], by enumerating everything with ratios.
   Only for d = 1 or 2, where it is small enough."
  [sigma a d]
  (let [cases (for [phen (range 32)
                    :let [[p0 p1 p2 p3 p4] (mapv #(bit-and (bit-shift-right phen %) 1) [4 3 2 1 0])]
                    :when (= a (+ (if (= p1 p2) 0 1) (if (= p3 p2) 0 1)))
                    c (range 256)
                    [l r] (case d
                            1 [[c c]]
                            2 (for [y (range 256) :when (not= y c) pair [[c y] [y c] [y y]]] pair))]
                (let [nl (next-bit (bits l) p0 p1 p2) nc (next-bit (bits c) p1 p2 p3) nr (next-bit (bits r) p2 p3 p4)
                      static (/ (count (filter #(not= (nth (bits c) (nth sigma %)) (nth (bits c) %)) (range 8))) 8)]
                  {:activity (/ (+ (if (= nl nc) 0 1) (if (= nr nc) 0 1)) 3)
                   :hunger (if (= nl nc nr) static 0)}))
        n (count cases)]
    {:activity (double (/ (reduce + (map :activity cases)) n))
     :hunger (double (/ (reduce + (map :hunger cases)) n))}))

(def ^:private chaos (gen/sigma-positional (get grid/propagators :chaos)))

(deftest hunger-and-activity-match-a-slow-enumeration
  (doseq [[a d] [[0 1] [1 1] [2 1] [1 2]]]
    (let [slow (slow-row chaos a d)
          fast (get (exact/rows :chaos) [a d])]
      (is (< (Math/abs (- (:activity slow) (:activity fast))) 1e-12) (str "activity " [a d]))
      (is (< (Math/abs (- (:hunger slow) (:hunger fast))) 1e-12) (str "hunger " [a d])))))

(deftest diversity-matches-a-slow-enumeration-where-it-is-small
  (testing "d = 1: all 256 triples, with exact ratios"
    (let [slow (/ (reduce + (for [x (range 256)] (expected-distinct chaos x x x))) 256 3)]
      (is (< (Math/abs (- (double slow) (:diversity (get (exact/rows :chaos) [0 1])))) 1e-12))))
  (testing "single triples agree with the integer routine"
    (let [mc @@#'exact/background-counts
          nc (#'exact/operator-counts chaos)]
      (doseq [[l c r] [[3 200 77] [110 110 121] [0 255 0] [18 18 18] [5 6 7]]]
        (is (= (* 32768 (expected-distinct chaos l c r))
               (#'exact/distinct-numerator mc nc l c r)))))))

(deftest the-three-diversity-classes-add-up-to-all-triples
  (testing "d = 3 is computed by a shortcut; d = 1 and d = 2 visit every triple.
            Their total must equal the sum over all 256^3 triples, which has its
            own direct form with no exclusions."
    (let [mc ^longs @@#'exact/background-counts
          nc ^longs (#'exact/operator-counts chaos)
          parts (#'exact/diversity-numerators chaos)
          col (fn [^longs m y] (reduce + (for [x (range 256)] (aget m (+ (* 256 x) y)))))
          s (mapv #(col mc %) (range 256))
          n (mapv #(col nc %) (range 256))
          all (+ (- (* 3 32768 256 256 256)
                    (* 2 64 256 (reduce + (map * s n)))
                    (* 8 256 (reduce + (map * s s))))
                 (reduce + (map (fn [sy ny] (* sy sy ny)) s n)))]
      (is (= all (+ (parts 1) (parts 2) (parts 3)))))))

(deftest a-simulation-of-the-ensemble-agrees
  (testing "distinct rules, uniform bits: sample the process itself (bin [1 3])"
    (let [rng (java.util.Random. 20261115)
          n 400000
          sums (loop [i 0 hunger 0.0 diversity 0.0 used 0]
                 (if (= i n)
                   [(/ hunger used) (/ diversity used 3.0)]
                   (let [l (.nextInt rng 256) c (.nextInt rng 256) r (.nextInt rng 256)
                         p (vec (repeatedly 5 #(.nextInt rng 2)))
                         a (+ (if (= (p 1) (p 2)) 0 1) (if (= (p 3) (p 2)) 0 1))]
                     (if (or (not= a 1) (= l c) (= c r) (= l r))
                       (recur (inc i) hunger diversity used)
                       (let [k (.nextInt rng 8)
                             nc (write (bits c) (nth chaos k) (flip (nth (bits c) k)))
                             bg (fn [x] (write (bits x) (.nextInt rng 8) (flip (nth (bits x) (.nextInt rng 8)))))
                             nl (bg l) nr (bg r)
                             boring (= (next-bit (bits l) (p 0) (p 1) (p 2))
                                       (next-bit (bits c) (p 1) (p 2) (p 3))
                                       (next-bit (bits r) (p 2) (p 3) (p 4)))]
                         (recur (inc i)
                                (+ hunger (if (and boring (= nc (bits c))) 1.0 0.0))
                                (+ diversity (count (distinct [nl nc nr])))
                                (inc used)))))))
          row (get (exact/rows :chaos) [1 3])]
      (is (< (Math/abs (- (first sums) (:hunger row))) 0.004))
      (is (< (Math/abs (- (second sums) (:diversity row))) 0.001)))))

(deftest what-the-rows-say-about-the-named-kinds
  (testing "identity never leaves a rule unchanged, so it is never hungry"
    (is (every? #(zero? (:hunger %)) (vals (exact/rows :identity)))))
  (testing "a vector and its named kind have the same rows"
    (is (= (exact/rows :odd53) (exact/rows (gen/sigma-positional (get grid/propagators :odd53))))))
  (testing "rows do not depend on any menu: no menu is needed to score a vector"
    (is (map? (efe/predict [2 6 7 0 3 1 4 5] {:activity (/ 1.0 3.0) :diversity 1.0} :derived nil :exact))))
  (testing "an unknown rows mode is refused"
    (is (thrown? clojure.lang.ExceptionInfo
                 (efe/predict :chaos {:activity 0.0 :diversity 1.0} :derived nil :exat)))))

(deftest exact-rows-are-a-mode-of-the-state
  (let [width 40
        base (ca/with-seed 3
               (let [g (vec (ca/random-sigil-string width))]
                 {:arm :efe-full :seed 3 :time 0 :self-tuning-arm :fixed-0.55 :lambda-step-size 0.0
                  :lambdas (vec (repeat width 0.55)) :genotype g :previous-genotype g
                  :phenotype (ca/random-phenotype-string width)
                  :exotypes (grid/initial-grid :heterogeneous-fixed width)
                  :blend-action? true :epistemic-coefficient 0.1 :blend-strength 0.0
                  :apply-probability 1.0 :policy-precision 8.0}))
        exact-state (assoc base :observation-rows :exact)
        run (fn [s] (mapv :exotypes (take 40 (iterate tuning/step s))))]
    (is (= :exact (:observation-rows (tuning/step exact-state))))
    (is (not (contains? (tuning/step base) :observation-rows)))
    (is (not= (run base) (run exact-state)) "the mode changes the scores, so the run")))
