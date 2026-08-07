(ns policy-x-v1b
  "Follow-up to policy_x_v1.clj — decision-level and horizon-resolved tests.

  1. Within-cell pairwise ranking: among cells where own/left/right kinds are all
     distinct, does sign(X(L)-X(R)) agree with sign(Y(L)-Y(R)) above chance?
  2. Horizon decomposition: Y at t in {2,5,10,20,40,60}, first phenotype-divergence
     time, realized first-step rule divergence at t=1; calibration of the exact
     one-step injection probability X_inj = |K_div|/8 against realized t=1 divergence.

  Only cand != own rows are simulated (the others are exact zeros).

  clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M scripts/policy_x_v1b.clj"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.xenotype.generator :as gen]))

(def width 60)
(def horizon 60)
(def checkpoints [2 5 10 20 40 60])
(def seed-set-a [11 22 33 44 55 66])
(def seed-set-b [707 808 909 1111 1212 1313])

(def patterns ca/truth-table-3)

(def kind->positional
  (into {} (for [kind grid/exotype-kinds]
             [kind (gen/sigma-positional (get grid/propagators kind))])))

(def kind->rate
  (into {} (for [kind grid/exotype-kinds]
             [kind (gen/rule-change-rate (get grid/propagators kind))])))

(defn pattern-weights [phenotype index]
  (let [w (count phenotype)
        triples (for [offset (range -5 6)
                      :let [k (mod (+ index offset) w)]]
                  (str (nth phenotype (mod (dec k) w))
                       (nth phenotype k)
                       (nth phenotype (mod (inc k) w))))
        counts (frequencies triples)]
    (into {} (for [n patterns] [n (/ (double (get counts n 0)) 11.0)]))))

(defn x-now [b sp-a sp-b weights]
  (/ (reduce + 0.0
             (for [k (range 8)
                   :let [ak (nth sp-a k) bk (nth sp-b k)]
                   :when (not= ak bk)]
               (+ (if (= (nth b ak) (nth b k)) (get weights (nth patterns ak) 0.0) 0.0)
                  (if (= (nth b bk) (nth b k)) (get weights (nth patterns bk) 0.0) 0.0))))
     8.0))

(defn x-pair [sp-a sp-b]
  (/ (reduce + 0.0
             (for [k (range 8)
                   :let [ak (nth sp-a k) bk (nth sp-b k)]
                   :when (not= ak bk)]
               (if (or (= ak k) (= bk k)) 1.0 0.75)))
     8.0))

(defn x-inj
  "Exact P over the uniform draw k that the two rules diverge at the FIRST step,
  given the actual byte b: |K_div| / 8."
  [b sp-a sp-b]
  (/ (double
      (count (filter (fn [k]
                       (let [ak (nth sp-a k) bk (nth sp-b k)]
                         (and (not= ak bk)
                              (or (= (nth b ak) (nth b k))
                                  (= (nth b bk) (nth b k))))))
                     (range 8))))
     8.0))

(defn init-state [seed]
  (ca/with-seed seed
    {:arm :heterogeneous-fixed :seed seed :time 0
     :exotypes (grid/initial-grid :heterogeneous-fixed width)
     :genotype (vec (ca/random-sigil-string width))
     :phenotype (ca/random-phenotype-string width)}))

(defn hamming [a b] (count (filter true? (map not= a b))))

(defn twin-measure
  "Run the perturbed twin against precomputed BASE-STATES (index = t).
  Returns {:geno-div-t1 bool :first-div-t int-or-61 :ham {t ham}}."
  [state i cand base-states]
  (let [pert0 (assoc state :exotypes (assoc (:exotypes state) i cand))
        cps (set checkpoints)]
    (loop [p pert0 t 0 first-div nil hams {}]
      (if (= t horizon)
        {:geno-div-t1 (not= (nth (:genotype (nth base-states 1)) i)
                            (nth (:genotype (grid/step pert0)) i))
         :first-div-t (or first-div (inc horizon))
         :ham hams}
        (let [p' (grid/step p)
              t' (inc t)
              h (hamming (:phenotype (nth base-states t')) (:phenotype p'))
              first-div (or first-div (when (pos? h) t'))
              hams (if (cps t') (assoc hams t' h) hams)]
          (recur p' t' first-div hams))))))

(defn rows-for-seed [set-name seed]
  (let [state (init-state seed)
        base-states (vec (take (inc horizon) (iterate grid/step state)))
        exo (:exotypes state)
        geno (:genotype state)
        phe (:phenotype state)]
    (vec
     (apply concat
            (for [i (range width)]
              (let [own (nth exo i)
                    sp-own (kind->positional own)
                    b (ca/bits-for (str (nth geno i)))
                    weights (pattern-weights phe i)]
                (for [[policy cand] [[:adopt-left (nth exo (mod (dec i) width))]
                                     [:adopt-right (nth exo (mod (inc i) width))]]
                      :when (not= cand own)]
                  (let [sp-cand (kind->positional cand)
                        m (twin-measure state i cand base-states)]
                    (merge
                     {:set set-name :seed seed :i i :policy policy
                      :own own :cand cand
                      :x-now (x-now b sp-own sp-cand weights)
                      :x-pair (x-pair sp-own sp-cand)
                      :x-inj (x-inj b sp-own sp-cand)
                      :rate-cand (double (kind->rate cand))
                      :drate (Math/abs (double (- (kind->rate cand) (kind->rate own))))
                      :geno-div-t1 (:geno-div-t1 m)
                      :first-div-t (:first-div-t m)}
                     (into {} (for [[t h] (:ham m)] [(keyword (str "y" t)) h])))))))))))

(defn mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))

(defn pearson [xs ys]
  (let [mx (mean xs) my (mean ys)
        cov (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sx (Math/sqrt (reduce + (map #(let [d (- % mx)] (* d d)) xs)))
        sy (Math/sqrt (reduce + (map #(let [d (- % my)] (* d d)) ys)))
        denom (* sx sy)]
    (if (zero? denom) ##NaN (/ cov denom))))

(defn analyze [rows]
  ;; --- calibration of x-inj against realized t=1 rule divergence -------------
  (println "\n=== calibration: X_inj (exact one-step divergence prob) vs realized t=1 ===")
  (let [groups (sort-by key (group-by :x-inj rows))]
    (doseq [[xv rs] groups]
      (println (format "  X_inj=%.3f  realized divergence freq=%.3f  (n=%d)"
                       xv (mean (map #(if (:geno-div-t1 %) 1.0 0.0) rs)) (count rs))))
    (println (format "  pooled: r(X_inj, 1{t=1 divergence}) = %+.4f  overall freq=%.3f"
                     (pearson (map :x-inj rows)
                              (map #(if (:geno-div-t1 %) 1.0 0.0) rows))
                     (mean (map #(if (:geno-div-t1 %) 1.0 0.0) rows)))))
  ;; --- horizon decomposition -------------------------------------------------
  (println "\n=== r(X, Y_t) by horizon (cand != own only) ===")
  (doseq [set-name [:A :B :pooled]]
    (let [rs (if (= set-name :pooled) rows (filterv #(= (:set %) set-name) rows))]
      (doseq [xkey [:x-now :x-pair :x-inj]]
        (println (format "  set %-6s %-7s %s"
                         (name set-name) (name xkey)
                         (apply str
                                (for [t checkpoints]
                                  (format " t=%d: %+.3f" t
                                          (pearson (map xkey rs)
                                                   (map #(get % (keyword (str "y" t))) rs))))))))))
  ;; --- divergence onset ------------------------------------------------------
  (println "\n=== divergence onset (cand != own only) ===")
  (doseq [t [2 5 10 20 40 60]]
    (let [ind (map #(if (<= (:first-div-t %) t) 1.0 0.0) rows)]
      (println (format "  P(diverged by t=%2d) = %.3f   r(x-inj, 1{div by t}) = %+.3f   r(x-pair, .) = %+.3f   r(x-now, .) = %+.3f"
                       t (mean ind)
                       (pearson (map :x-inj rows) ind)
                       (pearson (map :x-pair rows) ind)
                       (pearson (map :x-now rows) ind)))))
  (println (format "  r(X, -first_div_t): x-inj %+.3f  x-pair %+.3f  x-now %+.3f  drate %+.3f"
                   (pearson (map :x-inj rows) (map #(- (:first-div-t %)) rows))
                   (pearson (map :x-pair rows) (map #(- (:first-div-t %)) rows))
                   (pearson (map :x-now rows) (map #(- (:first-div-t %)) rows))
                   (pearson (map :drate rows) (map #(- (:first-div-t %)) rows))))
  ;; --- within-cell pairwise ranking -----------------------------------------
  (println "\n=== within-cell pairwise ranking: sign(X_L - X_R) vs sign(Y_L - Y_R) ===")
  (let [cells (group-by (juxt :set :seed :i) rows)
        pairs (for [[_ rs] cells
                    :let [l (first (filter #(= (:policy %) :adopt-left) rs))
                          r (first (filter #(= (:policy %) :adopt-right) rs))]
                    :when (and l r (not= (:cand l) (:cand r)))]
                [l r])
        rank-test
        (fn [xkey ykey]
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
            (format "%-7s vs %-4s: agree %d / %d = %.1f%%  (z=%+.2f; ties: y=%d x=%d; cells=%d)"
                    (name xkey) (name ykey) a n
                    (if (pos? n) (* 100.0 (/ a (double n))) 0.0)
                    z (get f :tie-y 0) (get f :tie-x 0) (count pairs))))]
    (doseq [ykey [:y5 :y10 :y60]
            xkey [:x-now :x-pair :x-inj :drate]]
      (println " " (rank-test xkey ykey)))
    ;; also: first-div-t ranking (earlier divergence = bigger consequence)
    (let [scored (for [[l r] pairs
                       :let [dx (- (double (:x-inj l)) (double (:x-inj r)))
                             dy (- (:first-div-t r) (:first-div-t l))]] ; earlier = larger
                   (cond (zero? dy) :tie-y
                         (zero? dx) :tie-x
                         (pos? (* dx dy)) :agree
                         :else :disagree))
          f (frequencies scored)
          a (get f :agree 0) d (get f :disagree 0) n (+ a d)]
      (println (format "  x-inj vs earlier-first-divergence: agree %d / %d = %.1f%%  (ties: y=%d x=%d)"
                       a n (if (pos? n) (* 100.0 (/ a (double n))) 0.0)
                       (get f :tie-y 0) (get f :tie-x 0))))))

(defn -main [& _]
  (let [cache "analysis/policy-x-v1b-rows.edn"
        rows (if (.exists (java.io.File. cache))
               (do (println "loading cached rows from" cache)
                   (clojure.edn/read-string (slurp cache)))
               (let [jobs (concat (map (fn [s] [:A s]) seed-set-a)
                                  (map (fn [s] [:B s]) seed-set-b))
                     rows (vec (apply concat
                                      (doall (pmap (fn [[set-name s]]
                                                     (rows-for-seed set-name s))
                                                   jobs))))]
                 (.mkdirs (java.io.File. "analysis"))
                 (spit cache (pr-str rows))
                 rows))]
    (println (format "rows (cand != own, adopt only): %d" (count rows)))
    (analyze rows)))

(apply -main *command-line-args*)
