(ns futon5.exotype.e8-classes-test
  "P-008b §7 item 2 (classes): fg-signature, fg-class, cycle-type guard,
  random-e8-of-stratum. Conjugation implemented: g[i] = p(f(p^-1(i)))
  (relabel positions by p)."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]))

(defn- conj-g
  "Conjugate f by relabelling p: g[p(k)] = p(f(k)), i.e. g[i] = p(f(p^-1(i)))."
  [f p]
  (let [inv (long-array 8)]
    (dotimes [k 8] (aset inv (long (nth p k)) k))
    (vec (for [i (range 8)]
           (nth p (long (nth f (aget inv i))))))))

(def f-bug [0 0 1 2 3 4 5 6])

(deftest fg-class-constant-on-orbit
  (testing "20 seeded f, 20 seeded conjugators: fg-class is orbit-constant"
    (let [fs (ca/with-seed 20261120
               (vec (repeatedly 20 (fn []
                                     (vec (repeatedly 8 #(ca/rnd-int 8)))))))
          ps (ca/with-seed 20261121
               (vec (repeatedly 20 #(#'grid/random-s8))))]
      (doseq [f fs]
        (let [c (grid/fg-class f)]
          (doseq [p ps]
            (is (= c (grid/fg-class (conj-g f p)))
                (str "orbit break: f=" f " p=" p))))))))

(deftest fg-class-separates
  (testing "200 seeded f: different fg-signature => different fg-class"
    (let [fs (ca/with-seed 20261122
               (vec (repeatedly 200 (fn []
                                      (vec (repeatedly 8 #(ca/rnd-int 8)))))))
          by-sig (group-by grid/fg-signature fs)]
      (doseq [[_ group] by-sig]
        (when (> (count group) 1)
          (is (= 1 (count (distinct (map grid/fg-class group))))
              (str "same signature, several classes: " group))))))
  (testing "hand-built pair: same rank and in-degree multiset, non-conjugate"
    ;; both rank 7, in-degrees {2,1,1,1,1,1,1,0}; f2 has three fixed points
    ;; and two components, f-bug has one of each.
    (let [f2 [0 1 2 0 3 4 5 6]]
      (is (= (:rank (grid/fg-signature f-bug))
             (:rank (grid/fg-signature f2))))
      (is (= (:in-degree-multiset (grid/fg-signature f-bug))
             (:in-degree-multiset (grid/fg-signature f2))))
      (is (not= (grid/fg-signature f-bug) (grid/fg-signature f2)))
      (is (not= (grid/fg-class f-bug) (grid/fg-class f2))))))

(deftest fg-class-agrees-with-cycle-type-on-permutations
  (testing "200 seeded permutation pairs: fg-class equality <=> cycle-type equality"
    (let [pairs (ca/with-seed 20261123
                  (vec (repeatedly 200 (fn [] [(#(vec (vec %)) (#'grid/random-s8))
                                               (#'grid/random-s8)]))))]
      (doseq [[p q] pairs]
        (is (= (= (grid/fg-class p) (grid/fg-class q))
               (= (grid/cycle-type p) (grid/cycle-type q)))
            (str "disagreement: " p " " q))))))

(deftest cycle-type-guards-against-non-permutations
  (testing "cycle-type on a non-permutation throws"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"cycle-type requires a permutation"
         (grid/cycle-type [0 0 1 2 3 4 5 6])))))

(deftest random-e8-of-stratum-draws-and-determinism
  (testing "200 draws per stratum carry the stratum's (rank, cycle-part)"
    (doseq [[rank cp] [[1 [1]] [7 [1]] [8 [8]] [8 [1 1 1 1 1 1 1 1]]]]
      (doseq [f (ca/with-seed (+ 20261130 rank)
                  (vec (repeatedly 200 #(grid/random-e8-of-stratum rank cp))))]
        (let [sig (grid/fg-signature f)]
          (is (= rank (:rank sig)))
          (is (= cp (:cycle-part sig)))))))
  (testing "same seed gives the same draws"
    (let [draw (fn [seed] (ca/with-seed seed
                            (vec (repeatedly 10 #(grid/random-e8-of-stratum 7 [1])))))]
      (is (= (draw 20261140) (draw 20261140)))
      (is (not= (draw 20261140) (draw 20261141))))))
