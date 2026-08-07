(ns interrupter-landscape
  "Exact score-landscape analysis for TN-interrupter-fable-answer.md.

   Everything here is a pure-function computation over efe/score-policy — no
   trajectories, no RNG. Reproduces the decision rule of self_tuning/cell-decision
   exactly (lambda fixed at 0.55 as in the v2 design, where lambda-step-size = 0).

   Outputs:
   1. risk(kind, p) table  = KL(p*rate || 0.15)
   2. X_pair structure     (values against :identity; range over differing pairs)
   3. wall dynamics        for each p, arm, ordered kind pair (A,B): at a sharp
                           A A | B B wall, does the wall SWAP (both wall cells
                           adopt across: stable interleaving), does B SWEEP left,
                           does A SWEEP right, or is it FROZEN? Classified per
                           observation bin, aggregated over the 9 bins.

   Run: clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M scripts/interrupter_landscape.clj"
  (:require [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as pe]
            [futon5.xenotype.generator :as gen]))

(def kinds grid/exotype-kinds)
(def ps [0.1 0.15 0.3 0.6 1.0])
(def lambda 0.55)
(def kappa 0.47821902791182086)
(def bonuses {0.3 0.15, 0.6 0.8, 1.0 0.1})
(def observations
  (for [a [0.0 (/ 1.0 3.0) (/ 2.0 3.0)]
        d [(/ 1.0 3.0) (/ 2.0 3.0) 1.0]]
    {:activity a :diversity d}))

(defn- total
  "Score candidate kind CAND for a cell whose own kind is OWN, under ARM.
   ADOPT? true when this candidate arrives via an adopt policy from a
   neighbour (source != index)."
  [arm p own cand adopt? obs]
  (let [opts (cond-> {:lambda lambda :apply-probability p}
               (= arm :epistemic)
               (assoc :epistemic-coefficient kappa
                      :epistemic-value (pe/pair-value own cand))
               (= arm :matched-churn)
               (assoc :adoption-bonus (get bonuses p 0.0)
                      :adoption? (and adopt? (not= cand own))))]
    (:total (efe/score-policy :efe-full cand obs opts))))

(defn- winner
  "Stable argmin over [hold left right] candidate kinds, tie prefers earlier."
  [arm p own left-kind right-kind obs]
  (let [cs [[:hold (total arm p own own false obs) own]
            [:adopt-left (total arm p own left-kind true obs) left-kind]
            [:adopt-right (total arm p own right-kind true obs) right-kind]]
        best (apply min (map second cs))]
    (first (filter #(= (second %) best) cs))))

(defn- wall-class
  "Sharp wall ... A A | B B ...  Wall cells: (left A, own A, right B) and
   (left A, own B, right B). Returns :swap / :b-sweeps / :a-sweeps / :frozen."
  [arm p a b obs]
  (let [a-cell (winner arm p a a b obs)   ; does the A-side cell take B?
        b-cell (winner arm p b a b obs)   ; does the B-side cell take A?
        a-takes-b (= b (nth a-cell 2))
        b-takes-a (= a (nth b-cell 2))]
    (cond
      (and a-takes-b b-takes-a) :swap
      a-takes-b :b-sweeps
      b-takes-a :a-sweeps
      :else :frozen)))

(defn- fmt [x] (format "%.4f" (double x)))

(defn -main []
  ;; 1. risk table
  (println "== risk(kind, p) = KL(p*rate || 0.15), kinds sorted by rate ==")
  (let [rate #(gen/rule-change-rate (get grid/propagators %))
        sorted (sort-by rate kinds)]
    (println (apply str "kind        rate    " (map #(format "p=%-8s" %) ps)))
    (doseq [k sorted]
      (println (apply str (format "%-11s %.4f  " (name k) (rate k))
                      (map (fn [p]
                             (let [obs {:activity (/ 1.0 3.0) :diversity (/ 2.0 3.0)}
                                   s (efe/score-policy :efe-full k obs
                                                       {:lambda 0.0 :apply-probability p})]
                               (format "%-9s" (fmt (:risk s)))))
                           ps)))))
  ;; ambiguity+conatus residual (p-independent rows; rule-change entropy varies with p*rate)
  (println "\n== total score (risk+ambiguity+0.55*conatus) at obs a=1/3 d=2/3 ==")
  (println (apply str "kind        " (map #(format "p=%-8s" %) ps)))
  (doseq [k (sort-by #(total :off 1.0 % % false {:activity (/ 1.0 3.0) :diversity (/ 2.0 3.0)}) kinds)]
    (println (apply str (format "%-11s " (name k))
                    (map (fn [p] (format "%-9s" (fmt (total :off p k k false
                                                            {:activity (/ 1.0 3.0) :diversity (/ 2.0 3.0)}))))
                         ps))))
  ;; 2. X_pair structure
  (println "\n== X_pair against :identity, and range over differing pairs ==")
  (doseq [k kinds]
    (println (format "%-11s X(k,id)=%s X(id,k)=%s" (name k)
                     (fmt (pe/pair-value k :identity))
                     (fmt (pe/pair-value :identity k)))))
  (let [vals (for [a kinds b kinds :when (not= a b)] (pe/pair-value a b))]
    (println (format "differing pairs: min %s max %s mean %s (n=%d)"
                     (fmt (apply min vals)) (fmt (apply max vals))
                     (fmt (/ (reduce + vals) (count vals))) (count vals))))
  ;; 3. wall dynamics
  (println "\n== wall dynamics: counts over ordered pairs x 9 obs bins ==")
  (doseq [p [0.3 0.6 1.0]
          arm [:off :epistemic :matched-churn]]
    (let [classes (for [a kinds b kinds :when (not= a b) obs observations]
                    (wall-class arm p a b obs))
          f (frequencies classes)]
      (println (format "p=%.2f %-14s swap %4d  a-sweeps %4d  b-sweeps %4d  frozen %4d"
                       p (name arm)
                       (get f :swap 0) (get f :a-sweeps 0)
                       (get f :b-sweeps 0) (get f :frozen 0)))))
  ;; which pairs swap (stable interleavings), majority over obs
  (println "\n== pairs that SWAP in >=5 of 9 obs bins (stable interleavings) ==")
  (doseq [p [0.3 0.6 1.0]
          arm [:epistemic :matched-churn]]
    (let [pairs (for [a kinds b kinds
                      :when (not= a b)
                      :let [n (count (filter #(= :swap (wall-class arm p a b %))
                                             observations))]
                      :when (>= n 5)]
                  [(name a) (name b)])
          ;; unordered representation for readability
          unordered (distinct (map (fn [[a b]] (vec (sort [a b]))) pairs))
          with-id (filter #(some #{"identity"} %) unordered)]
      (println (format "p=%.2f %-14s swap-pairs %d (unordered %d, involving identity %d)"
                       p (name arm) (count pairs) (count unordered) (count with-id)))
      (when (<= (count unordered) 24)
        (println "   " (vec unordered)))))
  ;; 4. the identity bridge: exact gap vs kappa*X at each p
  (println "\n== identity bridge: can adopting :identity beat holding a fix-0 kind? ==")
  (doseq [p ps
          :let [obs {:activity (/ 1.0 3.0) :diversity (/ 2.0 3.0)}
                own :odd53
                hold-t (total :epistemic p own own false obs)
                id-t (total :epistemic p own :identity true obs)]]
    (println (format "p=%.2f  hold(:odd53) %s  adopt(:identity) %s  -> %s"
                     p (fmt hold-t) (fmt id-t)
                     (if (< id-t hold-t) "BRIDGED" "blocked")))))

(-main)
