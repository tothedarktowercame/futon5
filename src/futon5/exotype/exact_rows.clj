(ns futon5.exotype.exact-rows
  "Observation rows for an exotype computed exactly, with no measurement and no
   dependence on the menu (exotype DESIGN-general 2b).

   A row answers: given the cell's operator sigma and its current local
   observation bin (activity count a, diversity count d), what are the expected
   next activity, next diversity and next hunger? The measured table
   (`efe/conditional-model`, `efe/menu-model`) estimates this from runs. Here it
   is the exact expectation over a stated REFERENCE ENSEMBLE:

   - Phenotype: the five bits at cells i-2..i+2 are uniform, conditioned on the
     activity count a (how many of the two neighbours differ from the centre).
   - Rules: the three rules at cells i-1, i, i+1 are uniform over the 256
     bytes, conditioned on the diversity count d (how many are distinct).
   - Next phenotype: each cell applies its own rule to its neighbourhood, as
     `grid/step` does.
   - Next rule of the centre: sigma writes NOT bit[k] at position sigma(k) for
     a uniform draw k, as `gen/rule-permute` does.
   - Next rule of each neighbour: the same write under an operator drawn
     uniformly from S8, which makes the written position uniform and
     independent of k. This is the fixed background; it favours no menu.

   Definitions follow the measured table: activity' = (neighbours whose next
   bit differs from the centre's)/3; diversity' = (distinct next rules among
   the three)/3; hunger' = P(the centre's rule is unchanged AND the three next
   bits are equal).

   Everything is counted in integers and divided once, so two operators with
   the same counts get the same double, and a tie is an exact tie.

   What this leaves out, on purpose: anything an operator does to its
   surroundings over many steps. The measured rows carry that; these do not."
  (:require [futon5.exotype.grid :as grid]
            [futon5.xenotype.generator :as gen]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* false)

(defn- bit ^long [^long rule ^long pos]
  (bit-and (unsigned-bit-shift-right rule (- 7 pos)) 1))

(defn- with-bit ^long [^long rule ^long pos ^long value]
  (let [mask (bit-shift-left 1 (- 7 pos))]
    (if (== 1 value) (bit-or rule mask) (bit-and rule (bit-not mask)))))

(def ^:private phen-class-size {0 8 1 16 2 8})
(def ^:private pair-class-size {1 1 2 (* 3 255) 3 (* 255 254)})

(def ^:private phenotype-tables
  "Sigma-independent counts, by exhaustive enumeration of the centre rule, the
   five phenotype bits and the two neighbour rules.
   :boring [a][d][centre-rule] -> number of (phenotype, neighbour-rule) cases
   in which the three next bits are equal;
   :activity [a][d] -> total over all cases of the number of neighbours whose
   next bit differs from the centre's."
  (delay
    (let [boring (long-array (* 3 3 256))
          activity (long-array (* 3 3))]
      (dotimes [phen 32]
        (let [p0 (bit-and (bit-shift-right phen 4) 1) p1 (bit-and (bit-shift-right phen 3) 1)
              p2 (bit-and (bit-shift-right phen 2) 1) p3 (bit-and (bit-shift-right phen 1) 1)
              p4 (bit-and phen 1)
              a (+ (if (== p1 p2) 0 1) (if (== p3 p2) 0 1))
              ml (- 7 (+ (* 4 p0) (* 2 p1) p2))
              mc (- 7 (+ (* 4 p1) (* 2 p2) p3))
              mr (- 7 (+ (* 4 p2) (* 2 p3) p4))]
          (dotimes [rc 256]
            (let [v (bit rc mc)]
              (dotimes [rl 256]
                (let [bl (bit rl ml)]
                  (dotimes [rr 256]
                    (let [br (bit rr mr)
                          d (cond (and (== rl rc) (== rr rc)) 1
                                  (or (== rl rc) (== rr rc) (== rl rr)) 2
                                  :else 3)
                          cell (+ (* 3 a) (dec d))]
                      (when (and (== bl v) (== br v))
                        (let [i (+ (* 256 cell) rc)]
                          (aset boring i (inc (aget boring i)))))
                      (aset activity cell
                            (+ (aget activity cell)
                               (if (== bl v) 0 1) (if (== br v) 0 1)))))))))))
      {:boring boring :activity activity})))

(def ^:private background-counts
  "Mc[x][y]: of the 64 equally likely (draw k, written position p) pairs, how
   many turn rule x into rule y. The neighbour's write under a uniform operator."
  (delay
    (let [m (long-array (* 256 256))]
      (dotimes [x 256]
        (dotimes [k 8]
          (dotimes [p 8]
            (let [y (with-bit x p (- 1 (bit x k)))
                  i (+ (* 256 x) y)]
              (aset m i (inc (aget m i)))))))
      m)))

(defn- operator-counts
  "Nc[x][y]: of the 8 draws k, how many turn rule x into rule y under SIGMA
   (a positional vector)."
  ^longs [sigma]
  (let [n (long-array (* 256 256))]
    (dotimes [x 256]
      (dotimes [k 8]
        (let [y (with-bit x (long (nth sigma k)) (- 1 (bit x k)))
              i (+ (* 256 x) y)]
          (aset n i (inc (aget n i))))))
    n))

(defn- distinct-numerator
  "32768 x the expected number of distinct next rules for the ordered rule
   triple (L, C, R)."
  [^longs mc ^longs nc l c r]
  (let [l (long l) c (long c) r (long r)]
  (loop [y 0 emn-l 0 emn-r 0 emm 0 t 0]
    (if (== y 256)
      (+ (- (* 3 32768) (* 64 emn-l) (* 64 emn-r) (* 8 emm)) t)
      (let [ml (aget mc (+ (* 256 l) y))
            mr (aget mc (+ (* 256 r) y))
            n (aget nc (+ (* 256 c) y))]
        (recur (inc y)
               (+ emn-l (* ml n)) (+ emn-r (* mr n)) (+ emm (* ml mr))
               (+ t (* ml n mr))))))))

(defn- diversity-numerators
  "For d = 1, 2, 3: the sum over all rule triples with d distinct rules of
   32768 x the expected number of distinct next rules."
  [sigma]
  (let [^longs mc @background-counts
        nc (operator-counts sigma)
        d1 (loop [x 0 acc 0]
             (if (== x 256) acc (recur (inc x) (+ acc (long (distinct-numerator mc nc x x x))))))
        d2 (loop [x 0 acc 0]
             (if (== x 256)
               acc
               (recur (inc x)
                      (long
                       (loop [y 0 acc acc]
                         (if (== y 256)
                           acc
                           (recur (inc y)
                                  (if (== x y)
                                    acc
                                    (+ acc
                                       (long (distinct-numerator mc nc x x y))
                                       (long (distinct-numerator mc nc y x x))
                                       (long (distinct-numerator mc nc y x y)))))))))))
        ;; d = 3 without visiting 16.6 million triples. S[y] and Q[y] are the
        ;; column sums of Mc and of its squares.
        s (long-array 256) q (long-array 256)
        _ (dotimes [x 256]
            (dotimes [y 256]
              (let [m (aget mc (+ (* 256 x) y))]
                (aset s y (+ (aget s y) m))
                (aset q y (+ (aget q y) (* m m))))))
        triples (* 256 255 254)
        a (loop [c 0 acc 0]            ; sum over L != C of E_MN(L, C)
            (if (== c 256)
              acc
              (recur (inc c)
                     (long
                      (loop [y 0 acc acc]
                        (if (== y 256)
                          acc
                          (recur (inc y)
                                 (+ acc (* (aget nc (+ (* 256 c) y))
                                           (- (aget s y) (aget mc (+ (* 256 c) y))))))))))))
        b (loop [y 0 acc 0]            ; sum over L != R of E_MM(L, R)
            (if (== y 256) acc
                (recur (inc y) (+ acc (- (* (aget s y) (aget s y)) (aget q y))))))
        t (loop [c 0 acc 0]            ; sum over distinct triples of T3
            (if (== c 256)
              acc
              (recur (inc c)
                     (long
                      (loop [y 0 acc acc]
                        (if (== y 256)
                          acc
                          (let [m (aget mc (+ (* 256 c) y))
                                rest-s (- (aget s y) m)
                                g (- (* rest-s rest-s) (- (aget q y) (* m m)))]
                            (recur (inc y) (+ acc (* (aget nc (+ (* 256 c) y)) g))))))))))
        d3 (+ (- (* 3 32768 triples) (* 2 64 254 a) (* 8 254 b)) t)]
    {1 d1 2 d2 3 d3}))

(defn- static-counts
  "For each rule, the number of draws k (of 8) that leave it unchanged."
  ^longs [sigma]
  (let [out (long-array 256)]
    (dotimes [x 256]
      (aset out x (long (count (filter (fn [k] (not= (bit x (long (nth sigma k))) (bit x k)))
                                       (range 8))))))
    out))

(defn- compute-rows [sigma]
  (let [{:keys [^longs boring ^longs activity]} @phenotype-tables
        statics (static-counts sigma)
        diversity (diversity-numerators sigma)
        triple-count {1 256 2 (* 3 256 255) 3 (* 256 255 254)}]
    (into {}
          (for [a [0 1 2] d [1 2 3]
                :let [cell (+ (* 3 a) (dec d))
                      cases (* 256 (long (phen-class-size a)) (long (pair-class-size d)))
                      hunger (loop [x 0 acc 0]
                               (if (== x 256)
                                 acc
                                 (recur (inc x)
                                        (+ acc (* (aget statics x)
                                                  (aget boring (+ (* 256 cell) x)))))))]]
            [[a d]
             {:activity (/ (double (aget activity cell)) (double (* 3 cases)))
              :diversity (/ (double (long (diversity d)))
                            (double (* 3 32768 (long (triple-count d)))))
              :hunger (/ (double hunger) (double (* 8 cases)))
              ;; the integer counts, so that ties can be checked exactly
              :counts {:hunger hunger :diversity (diversity d)}}]))))

(def ^:private rows-of (memoize compute-rows))

(defn rows
  "All nine exact rows for EXOTYPE (a named kind or a permutation vector),
   keyed by [activity-count diversity-count]."
  [exotype]
  (rows-of (gen/sigma-positional (grid/sigma-of exotype))))

(defn row
  "The exact row for EXOTYPE at the bin of OBSERVATION."
  [exotype {:keys [activity diversity]}]
  (get (rows exotype)
       [(Math/round (* 3.0 (double activity))) (Math/round (* 3.0 (double diversity)))]))
