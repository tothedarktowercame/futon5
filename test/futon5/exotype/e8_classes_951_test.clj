(ns futon5.exotype.e8-classes-951-test
  "P-008b §12(e) / §7 item 2: the 951-class check by orbit marking.

  Independent of fg-class: conjugation orbits of S8 acting on End(8)
  (g = p o f o p^-1, i.e. g[i] = p(f(p^-1(i)))) are counted by marking a
  bitset over all 8^8 = 16,777,216 maps. No fg-class value feeds the
  enumeration; fg-class is only CHECKED against the result at the end.

  Index encoding (stated per the packet): a map f is the integer
      index(f) = sum_k f(k) * 8^(7-k)
  so f(0) is the MOST significant base-8 digit. Increasing index order is
  therefore lexicographic order on the vector [f(0) ... f(7)] -- the same
  order fg-class minimises over (Clojure compare on the vector). This is
  deliberate: it lets the strong check (fg-class r = r for every orbit
  representative r) be asserted; see the 3(d) test."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]))

(set! *warn-on-reflection* true)

(def ^:private n-maps 16777216)                 ;; 8^8
(def ^:private n-perms 40320)                   ;; 8!
(def ^:private bits (long-array 2097152))       ;; 16,777,216 bits
(def ^:private perm-arrays
  "All 40,320 permutations and their inverses as flat long-arrays."
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
  (let [w (int (bit-shift-right i 6))]
    (not (zero? (bit-and (aget bs w)
                          (bit-shift-left 1 (int (bit-and i 63))))))))

(defn- mark! [^longs bs ^long i]
  (let [w (int (bit-shift-right i 6))]
    (aset bs w (bit-or (aget bs w)
                       (bit-shift-left 1 (int (bit-and i 63)))))))

(defn- decode
  "Index -> vector f, under index = sum_k f(k) * 8^(7-k)."
  [i]
  (let [f (long-array 8)]
    (dotimes [k 8] (aset f k (long (mod (quot i (bit-shift-left 1 (* 3 (- 7 k)))) 8))))
    (vec f)))

(defn- encode
  "Vector f -> index."
  [f]
  (reduce (fn [acc v] (+ (* acc 8) (long v))) 0 f))

(defn- conj-index
  "Index of the conjugate g = p o f o p^-1 of the map with index FI,
  for permutation slot C (offset (* 8 c) into the flat arrays)."
  [^longs ps ^longs invs ^longs farr c]
  (let [off (long (* 8 c))
        g (long-array 8)]
    (dotimes [i 8]
      (let [pk (aget invs (+ off (long i)))
            fk (aget farr pk)]
        (aset g i (aget ps (+ off fk)))))
    (encode g)))

(defn orbit-stats
  "One pass of orbit marking. Returns a map with the orbit count, the sum of
  orbit sizes, orbit counts by rank (image size) of the representative, the
  permutation-orbit count, and the 951 representatives."
  []
  (java.util.Arrays/fill ^longs bits 0)
  (let [counts-by-rank (long-array 9)
        [ps invs] @perm-arrays
        reps (volatile! [])
        perm-orbits (volatile! 0)
        size-sum (volatile! 0)]
    (dotimes [i n-maps]
      (when-not (marked? bits i)
        (let [f (decode i)
              farr (long-array f)
              rank (count (distinct f))]
          (vswap! reps conj f)
          (aset counts-by-rank rank (inc (aget counts-by-rank rank)))
          (when (= 8 rank) (vswap! perm-orbits inc))
          (dotimes [c n-perms]
            (let [j (conj-index ps invs farr c)]
              (when-not (marked? bits j)
                (mark! bits j)
                (vswap! size-sum inc))))))
      (mark! bits i))
    {:orbits (count @reps)
     :size-sum @size-sum
     :perm-orbits @perm-orbits
     :by-rank (vec (rest (vec counts-by-rank)))
     :reps @reps}))

(defn- conj-g
  "Conjugate vector g[i] = p(f(p^-1(i)))."
  [f p]
  (let [inv (long-array 8)]
    (dotimes [k 8] (aset inv (long (nth p k)) k))
    (vec (for [i (range 8)] (nth p (long (nth f (aget inv i))))))))

(deftest orbit-marking-gives-951-classes
  (let [{:keys [orbits size-sum perm-orbits by-rank reps]} (orbit-stats)]
    (testing "(a) the number of conjugation orbits is 951"
      (is (= 951 orbits) (str "orbit count was " orbits)))
    (testing "(b) orbit sizes sum to 8^8"
      (is (= n-maps size-sum)))
    (testing "permutation orbits: the 22 partitions of 8"
      (is (= 22 perm-orbits)))
    (testing "every representative is unmarked-first, i.e. index-lex-least in its orbit"
      (is (every? grid/perm? (filter #(= 8 (count (distinct %))) reps)))
      (is (= 8 (count by-rank))))
    (testing "(c) fg-class is constant on each orbit and separates all 951"
      (let [values (map grid/fg-class reps)
            conjugators (ca/with-seed 20261150
                          (vec (repeatedly 3 #(#'grid/random-s8))))]
        (is (= 951 (count (distinct values))))
        (doseq [r reps]
          (doseq [p conjugators]
            (is (= (grid/fg-class r) (grid/fg-class (conj-g r p)))
                (str "orbit break at r=" r " p=" p))))))
    (testing "(d) index order = vector lex order, so fg-class r = r for all 951"
      ;; index = sum f(k)*8^(7-k) makes f(0) the most significant digit, so
      ;; the first unmarked member of an orbit is the lexicographically least
      ;; vector in it -- exactly fg-class's minimiser.
      (is (every? identity
                  (map #(= % (grid/fg-class %)) reps))))))
