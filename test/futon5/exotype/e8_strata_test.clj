(ns futon5.exotype.e8-strata-test
  "P-008b §4 Allocation + §7 item 6: enumerate the strata (rank,
  cycle-part partition) of End(8), their sizes and class counts, and the
  sqrt-allocation table (about 150 maps, minimum 2 per stratum).

  All vectors are in the futon5 index convention (Wolfram order):
  index(f) = sum_k f(k) * 8^(7-k).

  The enumeration is the orbit-marking pass of e8-classes-951-test
  (commit 7da77cc), extended to tally per-stratum sizes and class
  counts: strata are invariant under conjugation (rank and the cycle
  type of the recurrent set are isomorphism invariants), so each
  conjugation orbit lies wholly inside one stratum and orbit sizes sum
  to stratum sizes.

  strata-stats takes a few minutes (one 8^8 pass + 951 x 40,320
  conjugations); it is the same cost class as e8-classes-951-test."
  (:require [clojure.test :refer [deftest is testing]]))

(set! *warn-on-reflection* true)

(def ^:private n-maps 16777216)                 ;; 8^8
(def ^:private n-perms 40320)                   ;; 8!
(def ^:private bits (long-array 2097152))       ;; 16,777,216 bits

(def ^:private perm-arrays
  "All 40,320 permutations and their inverses as flat long-arrays
  (as in e8-classes-951-test)."
  (delay
    (let [ps (long-array (* 8 n-perms))
          invs (long-array (* 8 n-perms))
          idx (atom 0)]
      (letfn [(go [used perm]
                (if (= 8 (count perm))
                  (let [c (long (dec (swap! idx inc)))
                        p (long-array 8) q (long-array 8)]
                    (dotimes [k 8] (aset p k (long (nth perm k))))
                    (dotimes [k 8] (aset q (aget p k) k))
                    (dotimes [k 8]
                      (aset ps (+ (* 8 c) k) (aget p k))
                      (aset invs (+ (* 8 c) k) (aget q k))))
                  (dotimes [j 8]
                    (when-not (used j)
                      (go (conj used j) (conj perm j))))))]
        (go #{} [])
        [ps invs]))))

(defn- marked? [^longs bs ^long i]
  (not (zero? (bit-and (aget bs (int (bit-shift-right i 6)))
                       (bit-shift-left 1 (int (bit-and i 63)))))))

(defn- mark! [^longs bs ^long i]
  (let [w (int (bit-shift-right i 6))]
    (aset bs w (bit-or (aget bs w)
                       (bit-shift-left 1 (int (bit-and i 63)))))))

(defn- decode
  "Index -> vector f, under index = sum_k f(k) * 8^(7-k)."
  [i]
  (let [f (long-array 8)]
    (dotimes [k 8] (aset f k (rem (quot i (long (Math/pow 8 (- 7 k)))) 8)))
    (vec f)))

(defn- encode [f]
  (reduce (fn [acc v] (+ (* acc 8) (long v))) 0 f))

(defn- conj-index
  "Index of the conjugate g = p o f o p^-1 (as in e8-classes-951-test)."
  [^longs ps ^longs invs ^longs farr c]
  (let [off (long (* 8 c))
        g (long-array 8)]
    (dotimes [i 8]
      (let [pk (aget invs (+ off (long i)))
            fk (aget farr pk)]
        (aset g i (aget ps (+ off fk)))))
    (encode g)))

(defn stratum-key
  "The stratum key [rank cycle-part] of a vector f: rank = image size,
  cycle-part = the descending multiset of cycle lengths of f restricted
  to its recurrent set (same definition as grid's private
  cycle-parts-of, reimplemented here so no private var is touched)."
  [f]
  (let [fa (long-array (map long f))
        rank (count (distinct f))
        on-cycle (boolean-array 8)
        parts (volatile! [])]
    (dotimes [k 8]
      (when-not (aget on-cycle k)
        (let [local (volatile! {k 0})]
          (loop [j (aget fa k) n 1]
            (cond (aget on-cycle j) nil
                  (contains? @local j)
                  (vswap! parts conj (- n (get @local j)))
                  :else (do (vswap! local assoc j n)
                            (recur (aget fa j) (inc n)))))
          (doseq [j (keys @local)] (aset on-cycle j true)))))
    [rank (vec (sort > @parts))]))

(defn strata-stats
  "One orbit-marking pass over all 8^8 maps. Returns
  {:strata sorted vector of [key {:size :classes}]
   :total-maps :total-classes :by-rank-classes :by-rank-sizes
   :perm-strata}. Strata are conjugation-invariant, so each orbit lies in
  one stratum: its size adds to that stratum and it is one class there."
  []
  (java.util.Arrays/fill ^longs bits 0)
  (let [[ps invs] @perm-arrays
        strata (volatile! {})
        by-rank-classes (long-array 9)
        by-rank-sizes (long-array 9)
        perm-strata (volatile! 0)
        total-maps (volatile! 0)
        total-classes (volatile! 0)]
    (dotimes [i n-maps]
      (when-not (marked? bits i)
        (let [f (decode i)
              farr (long-array f)
              key (stratum-key f)
              [rank _] key
              size (volatile! 1)]
          (dotimes [c n-perms]
            (let [j (conj-index ps invs farr c)]
              (when (and (not= j i) (not (marked? bits j)))
                (mark! bits j)
                (vswap! size inc))))
          (mark! bits i)
          (let [s @size]
            (vswap! strata update key (fn [m] {:size (+ (or (:size m) 0) s)
                                               :classes (inc (or (:classes m) 0))}))
            (aset by-rank-classes rank (inc (aget by-rank-classes rank)))
            (aset by-rank-sizes rank (+ (aget by-rank-sizes rank) s))
            (vswap! total-maps + s)
            (vswap! total-classes inc)
            (when (= 8 rank) (vswap! perm-strata inc)))))
      (mark! bits i))
    {:strata (vec (sort-by (comp :size val) > @strata))
     :total-maps @total-maps
     :total-classes @total-classes
     :perm-strata @perm-strata
     :by-rank-classes (vec (rest (vec by-rank-classes)))
     :by-rank-sizes (vec (rest (vec by-rank-sizes)))}))

(defn allocate
  "The §4 allocation: n_s = max(2, ROUND(alpha * sqrt(size_s))) with
  round-half-up. Returns {:counts :total :alpha}. With :round :floor the
  alternative rounding is used (no stratum goes below the minimum)."
  [strata alpha & {:keys [round] :or {round :round}}]
  (let [rnd (if (= :floor round) long #(Math/round (double %)))
        counts (into {}
                     (map (fn [[k {:keys [size]}]]
                            [k (max 2 (rnd (* alpha (Math/sqrt (double size)))))]))
                     strata)]
    {:counts counts
     :total (reduce + (vals counts))
     :alpha alpha}))

(defn alpha-for-target
  "Smallest alpha (bisection to 1e-9) whose round-half-up allocation
  total is >= TARGET."
  [strata target]
  (loop [lo 0.0 hi 1.0]
    (if (< (- hi lo) 1e-9)
      hi
      (let [mid (/ (+ lo hi) 2.0)]
        (if (>= (:total (allocate strata mid)) target)
          (recur lo mid)
          (recur mid hi))))))

(defn strata-table [stats]
  (concat ["| rank | cycle-part | size | classes |" "|---|---|---|---|"]
          (map (fn [[[rank part] {:keys [size classes]}]]
                 (str "| " rank " | " (pr-str part) " | " size " | " classes " |"))
               (:strata stats))))

;; --- the checks -------------------------------------------------------------

(deftest strata-enumeration-checks
  (let [{:keys [strata total-maps total-classes perm-strata
                by-rank-classes by-rank-sizes]} (strata-stats)]
    (testing "(a) stratum sizes sum to 8^8 = 16,777,216"
      (is (= 16777216 total-maps))
      (is (= 16777216 (reduce + (map (comp :size val) strata)))))
    (testing "(b) class counts sum to 951"
      (is (= 951 total-classes))
      (is (= 951 (reduce + (map (comp :classes val) strata)))))
    (testing "rank-8 strata are the 22 cycle types, summing to 8! = 40,320"
      (is (= 22 perm-strata))
      (let [r8 (filter (fn [[[r _]]] (= 8 r)) strata)]
        (is (= 22 (count r8)))
        (is (= 40320 (reduce + (map (comp :size val) r8))))))
    (testing "classes by rank match the 7da77cc orbit-marking result"
      (is (= [1 14 75 221 314 229 75 22] by-rank-classes)))
    (testing "sizes by rank match §2's Burnside distribution"
      (is (= [8 7112 324576 2857680 7056000 5362560 1128960 40320]
             by-rank-sizes)))
    (let [alpha (alpha-for-target (into {} strata) 150)
          {:keys [counts total]} (allocate (into {} strata) alpha)]
      (testing "allocation under the rule AS WRITTEN: minimum 2 dominates,
                total = 2 x (#strata) = 268, which CONTRADICTS §4's 'about 150'"
        (is (= (* 2 (count strata)) total) (str "total " total))
        (is (= 268 total) (str "134 strata x 2; got " (count strata) " strata"))
        (is (> total 150) "the ~150 aim of §4 is unreachable with minimum 2")
        (is (every? #(>= (val %) 2) counts))
        (is (= total (reduce + (vals counts))))
        (testing "the bug's stratum (rank 7, cycle-part [1]) has count >= 2"
          (is (= [7 [1]] (stratum-key [1 2 4 5 3 6 7 7])))
          (is (>= (get counts (stratum-key [1 2 4 5 3 6 7 7])) 2))))
      (testing "bad case: minimum 0 must fail the at-least-2 assertion"
        (let [zero-min (into {} (map (fn [[k v]] [k (long (Math/round
                                                            (* alpha (Math/sqrt (double (:size v))))))])
                                     strata))]
          (is (some #(zero? (val %)) zero-min)
              "some stratum rounds to 0 without the minimum")
          (is (not (every? #(>= (val %) 2) zero-min))
              "the min-0 allocation must FAIL the at-least-2 assertion"))))))
