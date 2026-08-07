(ns policy-x-v1c
  "Incremental-value analysis over cached v1b rows: does X add predictive value
  beyond |drate| = |rate(sigma_cand) - rate(sigma_own)|?

  clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M scripts/policy_x_v1c.clj"
  (:require [clojure.edn :as edn]))

(defn mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))

(defn pearson [xs ys]
  (let [mx (mean xs) my (mean ys)
        cov (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sx (Math/sqrt (reduce + (map #(let [d (- % mx)] (* d d)) xs)))
        sy (Math/sqrt (reduce + (map #(let [d (- % my)] (* d d)) ys)))
        denom (* sx sy)]
    (if (zero? denom) ##NaN (/ cov denom))))

(defn resid-1
  "Residuals of ys on [1, u] (simple OLS)."
  [ys u]
  (let [mu (mean u) my (mean ys)
        beta (/ (reduce + (map #(* (- %1 mu) (- %2 my)) u ys))
                (reduce + (map #(let [d (- % mu)] (* d d)) u)))]
    (mapv (fn [y uu] (- y my (* beta (- uu mu)))) ys u)))

(defn partial-r [xs ys u]
  (pearson (resid-1 xs u) (resid-1 ys u)))

(defn -main [& _]
  (let [rows (edn/read-string (slurp "analysis/policy-x-v1b-rows.edn"))]
    (println (format "rows: %d (cand != own, adopt only)" (count rows)))
    (println "\n=== inter-candidate structure ===")
    (println (format "  r(x-inj, drate)  = %+.4f" (pearson (map :x-inj rows) (map :drate rows))))
    (println (format "  r(x-inj, x-pair) = %+.4f" (pearson (map :x-inj rows) (map :x-pair rows))))
    (println (format "  r(x-inj, x-now)  = %+.4f" (pearson (map :x-inj rows) (map :x-now rows))))
    (println "\n=== partial r(X, Y_t | drate), cand != own ===")
    (doseq [set-name [:A :B :pooled]]
      (let [rs (if (= set-name :pooled) rows (filterv #(= (:set %) set-name) rows))]
        (doseq [xkey [:x-now :x-pair :x-inj]]
          (println (format "  set %-6s %-7s %s"
                           (name set-name) (name xkey)
                           (apply str
                                  (for [t [5 10 20 60]]
                                    (format " t=%d: %+.3f" t
                                            (partial-r (mapv xkey rs)
                                                       (mapv #(get % (keyword (str "y" t))) rs)
                                                       (mapv :drate rs))))))))))
    (println "\n=== drate given X (the reverse) ===")
    (doseq [t [10 60]]
      (println (format "  partial r(drate, Y%d | x-inj) = %+.3f"
                       t (partial-r (mapv :drate rows)
                                    (mapv #(get % (keyword (str "y" t))) rows)
                                    (mapv :x-inj rows)))))
    (println "\n=== within-cell ranking where drate is TIED (dx_drate = 0) ===")
    (let [cells (group-by (juxt :set :seed :i) rows)
          pairs (for [[_ rs] cells
                      :let [l (first (filter #(= (:policy %) :adopt-left) rs))
                            r (first (filter #(= (:policy %) :adopt-right) rs))]
                      :when (and l r (not= (:cand l) (:cand r))
                                 (zero? (- (:drate l) (:drate r))))]
                  [l r])]
      (println (format "  drate-tied pairs with distinct candidates: %d" (count pairs)))
      (doseq [[xkey ykey] [[:x-inj :y5] [:x-inj :y10] [:x-inj :y60]
                           [:x-pair :y10] [:x-now :y10]]]
        (let [scored (for [[l r] pairs
                           :let [dx (- (double (xkey l)) (double (xkey r)))
                                 dy (- (double (get l ykey)) (double (get r ykey)))]]
                       (cond (zero? dy) :tie-y
                             (zero? dx) :tie-x
                             (pos? (* dx dy)) :agree
                             :else :disagree))
              f (frequencies scored)
              a (get f :agree 0) d (get f :disagree 0) n (+ a d)
              z (if (pos? n) (/ (- a (/ n 2.0)) (Math/sqrt (/ n 4.0))) 0.0)]
          (println (format "  %-7s vs %-4s: agree %d / %d = %.1f%%  (z=%+.2f; ties: y=%d x=%d)"
                           (name xkey) (name ykey) a n
                           (if (pos? n) (* 100.0 (/ a (double n))) 0.0)
                           z (get f :tie-y 0) (get f :tie-x 0))))))))

(apply -main *command-line-args*)
