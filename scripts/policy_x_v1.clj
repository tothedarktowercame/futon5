(ns policy-x-v1
  "Policy-specific epistemic quantity X(i, pi) — measurement harness.
  See TN-policy-specific-epistemic-quantity.md (the ask) and
  TN-policy-specific-fable-answer.md (registered candidates + predictions).

  Candidates:
    X_now(i,pi)  = (1/8) sum over k where A(k)!=B(k) of
                     w(n_{A(k)}) * [b[A(k)]==b[k]]  +  w(n_{B(k)}) * [b[B(k)]==b[k]]
    X_pair(i,pi) = (1/8) sum over k where A(k)!=B(k) of (1 if A(k)=k or B(k)=k else 3/4)
  with A = own sigma positional map, B = candidate sigma positional map,
  b = own rule byte, w = 11-cell-window local pattern frequency.

  Y(i,pi) = phenotype Hamming at t=60 between baseline and the trajectory with
  exotypes[i] replaced by the sigma that pi would adopt (same state, same seed).
  Y_flip  = control: damage from an unrelated first-genotype-bit flip.

  clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M scripts/policy_x_v1.clj"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.xenotype.generator :as gen]))

(def width 60)
(def horizon 60)
(def seed-set-a [11 22 33 44 55 66])
(def seed-set-b [707 808 909 1111 1212 1313])

(def patterns ca/truth-table-3)

;; --- per-kind precomputation -------------------------------------------------

(def kind->positional
  (into {} (for [kind grid/exotype-kinds]
             [kind (gen/sigma-positional (get grid/propagators kind))])))

(def kind->rate
  (into {} (for [kind grid/exotype-kinds]
             [kind (gen/rule-change-rate (get grid/propagators kind))])))

;; sanity: every positional map addressable (proper permutations)
(doseq [[kind sp] kind->positional]
  (when (some neg? sp)
    (throw (ex-info "unaddressable sigma position" {:kind kind :sp sp}))))

;; --- candidates --------------------------------------------------------------

(defn pattern-weights
  "Local frequency of each 3-cell phenotype pattern over positions [i-5, i+5]."
  [phenotype index]
  (let [w (count phenotype)
        triples (for [offset (range -5 6)
                      :let [k (mod (+ index offset) w)]]
                  (str (nth phenotype (mod (dec k) w))
                       (nth phenotype k)
                       (nth phenotype (mod (inc k) w))))
        counts (frequencies triples)]
    (into {} (for [n patterns] [n (/ (double (get counts n 0)) 11.0)]))))

(defn x-now
  "Expected expressed first-divergence mass. b is the own rule byte (bit string),
  sp-a/sp-b positional maps, weights the local pattern distribution."
  [b sp-a sp-b weights]
  (/ (reduce
      + 0.0
      (for [k (range 8)
            :let [ak (nth sp-a k) bk (nth sp-b k)]
            :when (not= ak bk)]
        (+ (if (= (nth b ak) (nth b k)) (get weights (nth patterns ak) 0.0) 0.0)
           (if (= (nth b bk) (nth b k)) (get weights (nth patterns bk) 0.0) 0.0))))
     8.0))

(defn x-pair
  "Byte-averaged injection rate for the sigma pair."
  [sp-a sp-b]
  (/ (reduce
      + 0.0
      (for [k (range 8)
            :let [ak (nth sp-a k) bk (nth sp-b k)]
            :when (not= ak bk)]
        (if (or (= ak k) (= bk k)) 1.0 0.75)))
     8.0))

;; --- trajectories ------------------------------------------------------------

(defn init-state [seed]
  (ca/with-seed seed
    {:arm :heterogeneous-fixed :seed seed :time 0
     :exotypes (grid/initial-grid :heterogeneous-fixed width)
     :genotype (vec (ca/random-sigil-string width))
     :phenotype (ca/random-phenotype-string width)}))

(defn horizon-phenotype [state]
  (:phenotype (grid/run-steps state horizon)))

(defn hamming [a b]
  (count (filter true? (map not= a b))))

(defn y-policy
  "Damage from replacing exotypes[i] with CAND-KIND, vs precomputed baseline."
  [state i cand-kind base-phe]
  (hamming base-phe
           (horizon-phenotype (assoc state :exotypes
                                     (assoc (:exotypes state) i cand-kind)))))

(defn y-flip
  "Control: damage from flipping the first bit of genotype[i]."
  [state i base-phe]
  (let [bits (ca/bits-for (str (nth (:genotype state) i)))
        flipped (ca/sigil-for (str (if (= \0 (first bits)) \1 \0) (subs bits 1)))]
    (hamming base-phe
             (horizon-phenotype (assoc state :genotype
                                       (assoc (:genotype state) i flipped))))))

;; --- rows --------------------------------------------------------------------

(defn rows-for-seed [set-name seed]
  (let [state (init-state seed)
        base-phe (horizon-phenotype state)
        exo (:exotypes state)
        geno (:genotype state)
        phe (:phenotype state)]
    (vec
     (apply concat
            (for [i (range width)]
              (let [own (nth exo i)
                    sp-own (kind->positional own)
                    b (ca/bits-for (str (nth geno i)))
                    weights (pattern-weights phe i)
                    yf (y-flip state i base-phe)]
                (for [[policy cand] [[:hold own]
                                     [:adopt-left (nth exo (mod (dec i) width))]
                                     [:adopt-right (nth exo (mod (inc i) width))]]]
                  (let [sp-cand (kind->positional cand)
                        same? (= cand own)]
                    {:set set-name :seed seed :i i :policy policy :same? same?
                     :x-now (if same? 0.0 (x-now b sp-own sp-cand weights))
                     :x-pair (if same? 0.0 (x-pair sp-own sp-cand))
                     :rate-cand (double (kind->rate cand))
                     :rate-own (double (kind->rate own))
                     :drate (Math/abs (double (- (kind->rate cand) (kind->rate own))))
                     :y (if same? 0 (y-policy state i cand base-phe))
                     :y-flip yf}))))))))

;; --- stats -------------------------------------------------------------------

(defn mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))

(defn sd [xs]
  (let [m (mean xs)]
    (Math/sqrt (mean (map #(let [d (- % m)] (* d d)) xs)))))

(defn pearson [xs ys]
  (let [n (count xs)
        mx (mean xs) my (mean ys)
        cov (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        sx (Math/sqrt (reduce + (map #(let [d (- % mx)] (* d d)) xs)))
        sy (Math/sqrt (reduce + (map #(let [d (- % my)] (* d d)) ys)))
        denom (* sx sy)]
    (if (zero? denom) ##NaN (/ cov denom))))

(defn solve3
  "Gaussian elimination for a 3x3 system. a = vector of rows, v = rhs."
  [a v]
  (let [n 3
        m (loop [m (mapv #(vec (concat %1 [%2])) a v) col 0]
            (if (= col n)
              m
              (let [piv (apply max-key #(Math/abs (double (get-in m [% col])))
                               (range col n))
                    m (assoc m col (m piv) piv (m col))
                    pv (get-in m [col col])
                    m (assoc m col (mapv #(/ % pv) (m col)))
                    m (reduce (fn [m r]
                                (if (= r col)
                                  m
                                  (let [f (get-in m [r col])]
                                    (assoc m r (mapv - (m r) (mapv #(* f %) (m col)))))))
                              m (range n))]
                (recur m (inc col)))))]
    (mapv #(peek (m %)) (range n))))

(defn residuals
  "Residuals of ys regressed on [1, u, v] (OLS via normal equations)."
  [ys u v]
  (let [n (double (count ys))
        s (fn [f] (reduce + 0.0 (map f ys u v)))
        ;; design columns: 1, u, v
        a [[n (reduce + 0.0 u) (reduce + 0.0 v)]
           [(reduce + 0.0 u) (reduce + 0.0 (map #(* % %) u)) (reduce + 0.0 (map * u v))]
           [(reduce + 0.0 v) (reduce + 0.0 (map * u v)) (reduce + 0.0 (map #(* % %) v))]]
        b [(reduce + 0.0 ys) (s (fn [y uu _] (* y uu))) (s (fn [y _ vv] (* y vv)))]
        [c0 c1 c2] (solve3 a b)]
    (mapv (fn [y uu vv] (- y c0 (* c1 uu) (* c2 vv))) ys u v)))

(defn partial-r
  "r(x, y | u, v): correlate residuals of x and y after regressing on u, v."
  [xs ys u v]
  (pearson (residuals xs u v) (residuals ys u v)))

(defn report-corr [label rows xkey]
  (let [xs (mapv xkey rows) ys (mapv :y rows)]
    (println (format "  %-34s r(%s, Y) = %+.4f   (n=%d)"
                     label (name xkey) (pearson xs ys) (count rows)))))

(defn tercile-report [rows xkey]
  (let [sorted (sort-by xkey rows)
        n (count rows)
        t1 (quot n 3) t2 (* 2 t1)
        m (fn [ps] (mean (map :y ps)))]
    (println (format "  Y by %s-tercile: low=%.3f mid=%.3f high=%.3f"
                     (name xkey)
                     (m (take t1 sorted))
                     (m (take (- t2 t1) (drop t1 sorted)))
                     (m (drop t2 sorted))))))

(defn spread-report
  "Mean within-cell SD across the three candidates, for KEYFN over rows."
  [rows keyfn label]
  (let [cells (group-by (juxt :set :seed :i) rows)
        sds (for [[_ rs] cells] (sd (map keyfn rs)))]
    (println (format "  %-12s mean within-cell SD across 3 candidates = %.4f  (zero-SD cells: %d of %d)"
                     label (mean sds)
                     (count (filter #(< % 1e-12) sds)) (count sds)))))

(defn analyze [all-rows]
  (let [adopt (filterv #(not= (:policy %) :hold) all-rows)
        adopt-diff (filterv #(not (:same? %)) adopt)]
    (doseq [set-name [:A :B :pooled]]
      (let [rows (if (= set-name :pooled) adopt
                     (filterv #(= (:set %) set-name) adopt))
            rows-diff (filterv #(not (:same? %)) rows)]
        (println (format "\n=== seed set %s (adopt policies) ===" (name set-name)))
        (doseq [xkey [:x-now :x-pair]]
          (doseq [pol [:adopt-left :adopt-right]]
            (report-corr (str (name pol)) (filterv #(= (:policy %) pol) rows) xkey))
          (report-corr "pooled (both adopt)" rows xkey)
          (report-corr "pooled, cand != own only" rows-diff xkey))
        (println "  -- rate-only impostors --")
        (report-corr "rate(sigma_pi) as predictor" rows :rate-cand)
        (report-corr "|drate| as predictor" rows :drate)))
    (println "\n=== controls: r(X, Y_flip) — unrelated perturbation (pooled adopt) ===")
    (doseq [xkey [:x-now :x-pair]]
      (let [xs (mapv xkey adopt) ys (mapv :y-flip adopt)]
        (println (format "  r(%s, Y_flip) = %+.4f" (name xkey) (pearson xs ys)))))
    (println "\n=== redundancy checks (pooled adopt) ===")
    (doseq [xkey [:x-now :x-pair]]
      (println (format "  r(%s, rate(sigma_pi)) = %+.4f   r(%s, |drate|) = %+.4f"
                       (name xkey)
                       (pearson (mapv xkey adopt) (mapv :rate-cand adopt))
                       (name xkey)
                       (pearson (mapv xkey adopt) (mapv :drate adopt)))))
    (println "\n=== partial correlation r(X, Y | rate(sigma_pi), |drate|) ===")
    (doseq [set-name [:A :B :pooled]]
      (let [rows (if (= set-name :pooled) adopt
                     (filterv #(= (:set %) set-name) adopt))]
        (doseq [xkey [:x-now :x-pair]]
          (println (format "  set %-6s partial r(%s, Y | rates) = %+.4f  (n=%d)"
                           (name set-name) (name xkey)
                           (partial-r (mapv xkey rows) (mapv :y rows)
                                      (mapv :rate-cand rows) (mapv :drate rows))
                           (count rows))))))
    (println "\n=== spread check: within-cell SD across {hold, adopt-left, adopt-right} ===")
    (spread-report all-rows :x-now "X_now")
    (spread-report all-rows :x-pair "X_pair")
    (spread-report all-rows :rate-cand "rate(sigma)")
    (println "\n=== terciles (pooled adopt, cand != own) ===")
    (tercile-report adopt-diff :x-now)
    (tercile-report adopt-diff :x-pair)
    (println "\n=== descriptive ===")
    (let [ys (mapv :y adopt)]
      (println (format "  Y (adopt): mean=%.3f sd=%.3f zero-frac=%.3f  same-sigma frac=%.3f"
                       (mean ys) (sd ys)
                       (/ (double (count (filter zero? ys))) (count ys))
                       (/ (double (count (filter :same? adopt))) (count adopt)))))
    (let [xs (mapv :x-now adopt)]
      (println (format "  X_now (adopt): mean=%.4f sd=%.4f min=%.4f max=%.4f zero-frac=%.3f"
                       (mean xs) (sd xs) (apply min xs) (apply max xs)
                       (/ (double (count (filter #(< % 1e-12) xs))) (count xs)))))))

(defn -main [& _]
  (let [cache "analysis/policy-x-v1-rows.edn"
        all (if (.exists (java.io.File. cache))
              (do (println "loading cached rows from" cache)
                  (clojure.edn/read-string (slurp cache)))
              (let [rows-a (vec (mapcat #(rows-for-seed :A %) seed-set-a))
                    rows-b (vec (mapcat #(rows-for-seed :B %) seed-set-b))
                    all (into rows-a rows-b)]
                (.mkdirs (java.io.File. "analysis"))
                (spit cache (pr-str all))
                all))]
    (println (format "rows: %d  (width=%d horizon=%d, seeds A=%s B=%s)"
                     (count all) width horizon seed-set-a seed-set-b))
    (analyze all)))

(apply -main *command-line-args*)
