(ns exotype-eig-vs-damage
  "BUILD PACKET E2b — does corrected-local-eig track per-cell damage?

  Pairs corrected-local-eig (the codebase's epistemic quantity) with per-cell
  damage at horizon 60, measured on the same state at t=0. Uses a heterogeneous
  exotype ring so that X varies meaningfully across cells.

  clojure -M scripts/exotype_eig_vs_damage.clj"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.pattern-eig :as eig]))

(defn- local-damage
  "Per-cell damage: phenotype Hamming distance at t=horizon between the baseline
  trajectory and one with the genotype at INDEX perturbed (first bit flipped).
  Same logic as scripts/exotype_local_damage.clj."
  [state index horizon]
  (let [sigil (nth (:genotype state) index)
        bits (ca/bits-for (str sigil))
        flipped (ca/sigil-for (str (if (= \0 (first bits)) \1 \0)
                                   (subs bits 1)))
        perturbed (assoc (:genotype state) index flipped)]
    (loop [b state p (assoc state :genotype perturbed) t 0]
      (if (= t horizon)
        (count (filter true? (map not= (:phenotype b) (:phenotype p))))
        (recur (grid/step b) (grid/step p) (inc t))))))

(defn- pairs-for-seed [seed]
  (let [w 60
        state (ca/with-seed seed
                {:arm :heterogeneous-fixed :seed seed :time 0
                 :exotypes (grid/initial-grid :heterogeneous-fixed w)
                 :genotype (vec (ca/random-sigil-string w))
                 :phenotype (ca/random-phenotype-string w)})]
    (for [i (range w)]
      (let [kind (nth (:exotypes state) i)
            x (eig/corrected-local-eig state i 1 kind)
            y (local-damage state i 60)]
        {:seed seed :x x :y y}))))

(defn- pearson [xs ys]
  (let [n (count xs)
        mx (/ (reduce + xs) (double n))
        my (/ (reduce + ys) (double n))
        cov (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sx (Math/sqrt (reduce + (map #(* (- % mx) (- % mx)) xs)))
        sy (Math/sqrt (reduce + (map #(* (- % my) (- % my)) ys)))
        denom (* sx sy)]
    (if (zero? denom) 0.0 (/ cov denom))))

(defn -main [& _]
  (let [seeds [11 22 33 44 55 66 77 88 99 111 222 333]
        all-pairs (mapcat pairs-for-seed seeds)
        xs (map :x all-pairs)
        ys (map :y all-pairs)
        n (count all-pairs)]
    ;; 1. Overall Pearson r
    (let [r (pearson xs ys)]
      (println (format "1. Pearson r = %.6f  (n = %d)" r n)))
    ;; 2. Per-seed r
    (print "2. Per-seed r:")
    (doseq [seed seeds]
      (let [ps (filter #(= (:seed %) seed) all-pairs)
            r (pearson (map :x ps) (map :y ps))]
        (print (format " %.3f" r))))
    (println)
    ;; 3. Mean X, mean Y, Y by X-tercile
    (let [mx (/ (reduce + xs) (double n))
          my (/ (reduce + ys) (double n))
          sorted (sort-by :x all-pairs)
          t1 (quot n 3)
          t2 (* 2 t1)
          low (take t1 sorted)
          mid (take (- t2 t1) (drop t1 sorted))
          high (drop t2 sorted)
          mean-y (fn [ps] (if (seq ps) (/ (reduce + (map :y ps)) (double (count ps))) 0.0))]
      (println (format "3. Mean X = %.4f   Mean Y = %.4f" mx my))
      (println (format "   Y by X-tercile: low=%.4f  mid=%.4f  high=%.4f"
                       (mean-y low) (mean-y mid) (mean-y high))))
    ;; 4. Cells with X = ln(2) exactly
    (let [ln2 (Math/log 2.0)
          at-max (filter #(== (:x %) ln2) all-pairs)
          others (filter #(not (== (:x %) ln2)) all-pairs)]
      (println (format "4. X = ln(2): %d of %d (%.1f%%)" (count at-max) n
                       (* 100.0 (/ (count at-max) (double n))))
               (if (seq at-max)
                 (format "mean Y at-max = %.4f  vs  others = %.4f"
                         (/ (reduce + (map :y at-max)) (double (count at-max)))
                         (if (seq others)
                           (/ (reduce + (map :y others)) (double (count others)))
                           0.0))
                 "")))))

(apply -main *command-line-args*)
