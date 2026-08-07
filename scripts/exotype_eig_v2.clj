(ns exotype-eig-v2
  "BUILD PACKET E2c — first-order EIG candidate, tested against damage.

  X(i) = sum over 8 patterns n of w(n) * [rule_i(n) != blended_i(n)]

  How often would adopting the neighbour-blend change what I do, weighted by the
  patterns I actually encounter locally. X is in [0,1].

  Tested against two damage measures:
    Y_action — damage from replacing genotype[i] with the blend (matched perturbation)
    Y_flip   — damage from flipping the first bit (control)

  clojure -M scripts/exotype_eig_v2.clj"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]))

(def patterns ca/truth-table-3)  ; ["111" "110" ... "000"]

(defn- pattern-weights
  "Local frequency of each 3-cell phenotype pattern over positions [i-5, i+5].
  Returns a map from pattern-string to weight (sums to 1 over 11 positions)."
  [phenotype index]
  (let [w (count phenotype)
        positions (for [offset (range -5 6)] (mod (+ index offset) w))
        triples (for [k positions]
                  (str (nth phenotype (mod (dec k) w))
                       (nth phenotype k)
                       (nth phenotype (mod (inc k) w))))
        counts (frequencies triples)]
    (into {} (for [n patterns] [n (/ (double (get counts n 0)) 11.0)]))))

(defn- first-order-eig
  "X(i): weighted fraction of patterns where the blend would change the output."
  [state index]
  (let [g (:genotype state)
        w (count g)
        rule-sigil (str (nth g index))
        left (nth g (mod (dec index) w))
        right (nth g (mod (inc index) w))
        blended (grid/blend-rule left rule-sigil right)
        rule-table (ca/local-rule-table rule-sigil)
        blend-table (ca/local-rule-table (str blended))
        weights (pattern-weights (:phenotype state) index)]
    (reduce + (for [n patterns]
                (* (get weights n 0.0)
                   (if (not= (get rule-table n) (get blend-table n)) 1.0 0.0))))))

(defn- damage-from
  "Phenotype Hamming distance at t=horizon between STATE and STATE with
  genotype[index] set to NEW-SIGIL."
  [state index new-sigil horizon]
  (let [pert (assoc (:genotype state) index new-sigil)]
    (loop [b state p (assoc state :genotype pert) t 0]
      (if (= t horizon)
        (count (filter true? (map not= (:phenotype b) (:phenotype p))))
        (recur (grid/step b) (grid/step p) (inc t))))))

(defn- y-flip
  "Control: first-bit-flip damage (same as exotype_local_damage.clj)."
  [state index horizon]
  (let [sigil (nth (:genotype state) index)
        bits (ca/bits-for (str sigil))
        flipped (ca/sigil-for (str (if (= \0 (first bits)) \1 \0) (subs bits 1)))]
    (damage-from state index flipped horizon)))

(defn- y-action
  "Matched: replace genotype[i] with the neighbour-blend, measure damage."
  [state index horizon]
  (let [g (:genotype state)
        w (count g)
        rule-sigil (str (nth g index))
        left (nth g (mod (dec index) w))
        right (nth g (mod (inc index) w))
        blended (grid/blend-rule left rule-sigil right)]
    (damage-from state index blended horizon)))

(defn- pairs-for-seed [seed]
  (let [w 60
        state (ca/with-seed seed
                {:arm :heterogeneous-fixed :seed seed :time 0
                 :exotypes (grid/initial-grid :heterogeneous-fixed w)
                 :genotype (vec (ca/random-sigil-string w))
                 :phenotype (ca/random-phenotype-string w)})]
    (for [i (range w)]
      (let [x (first-order-eig state i)]
        {:seed seed :x x
         :y-action (y-action state i 60)
         :y-flip (y-flip state i 60)}))))

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
        n (count all-pairs)
        xs (vec (map :x all-pairs))
        ya (vec (map :y-action all-pairs))
        yf (vec (map :y-flip all-pairs))]
    ;; 1. Overall correlations
    (println (format "1. r(X, Y_action) = %.6f  (n = %d)" (pearson xs ya) n))
    (println (format "   r(X, Y_flip)   = %.6f  (n = %d)" (pearson xs yf) n))
    ;; 2. Per-seed r(X, Y_action)
    (print "2. Per-seed r(X, Y_action):")
    (doseq [seed seeds]
      (let [ps (filter #(= (:seed %) seed) all-pairs)]
        (print (format " %.3f" (pearson (map :x ps) (map :y-action ps))))))
    (println)
    ;; 3. Mean Y_action by X-tercile
    (let [sorted (sort-by :x all-pairs)
          t1 (quot n 3) t2 (* 2 t1)
          mean-ya (fn [ps] (if (seq ps) (/ (reduce + (map :y-action ps)) (double (count ps))) 0.0))]
      (println (format "3. Y_action by X-tercile: low=%.4f  mid=%.4f  high=%.4f"
                       (mean-ya (take t1 sorted))
                       (mean-ya (take (- t2 t1) (drop t1 sorted)))
                       (mean-ya (drop t2 sorted)))))
    ;; 4. Distribution of X
    (let [mx (/ (reduce + xs) (double n))
          sd (let [m mx] (Math/sqrt (/ (reduce + (map (fn [x] (* (- x m) (- x m))) xs)) (double n))))
          zeros (count (filter zero? xs))]
      (println (format "4. X: mean=%.4f sd=%.4f min=%.4f max=%.4f  X=0: %d of %d (%.1f%%)"
                       mx sd (double (apply min xs)) (double (apply max xs))
                       zeros n (* 100.0 (/ zeros (double n))))))))

(apply -main *command-line-args*)
