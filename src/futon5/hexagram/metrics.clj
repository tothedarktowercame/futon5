(ns futon5.hexagram.metrics
  "Metrics and classification helpers for hexagram-based dynamics."
  (:require [futon5.mmca.metrics :as mmca-metrics]))

(defn- avg [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn- clamp-01 [x]
  (cond
    (not (number? x)) 0.0
    (< x 0.0) 0.0
    (> x 1.0) 1.0
    :else (double x)))

(defn- classify-regime
  [{:keys [avg-change avg-entropy-n first-stasis-step]}]
  (let [avg-change (double (or avg-change 0.0))
        avg-entropy-n (double (or avg-entropy-n 0.0))]
    (cond
      (and first-stasis-step (<= (long first-stasis-step) 6)) :collapse
      (and (<= avg-change 0.05) (<= avg-entropy-n 0.2)) :static
      (and (>= avg-change 0.45) (>= avg-entropy-n 0.8)) :chaos
      (and (<= 0.15 avg-change 0.35) (<= 0.4 avg-entropy-n 0.7)) :eoc
      :else :mixed)))

(defn run->metrics
  "Extract high-level EoC metrics from an MMCA run."
  [mmca-run]
  (let [summary (mmca-metrics/summarize-run mmca-run)
        metrics-history (:metrics-history mmca-run)
        gen-history (:gen-history mmca-run)
        final-gen (last gen-history)
        entropy-spatial (mmca-metrics/shannon-entropy final-gen)
        entropy-temporal (avg (keep :entropy metrics-history))
        motif-diversity (or (get (last metrics-history) :unique-sigils)
                            (count (distinct (seq (or final-gen "")))))
        regime (classify-regime summary)
        params (get-in mmca-run [:exotype :params])]
    {:entropy-spatial entropy-spatial
     :entropy-temporal entropy-temporal
     :motif-diversity motif-diversity
     :regime regime
     :update-prob (:update-prob params)
     :match-threshold (:match-threshold params)}))

(defn- transpose [matrix]
  (apply mapv vector matrix))

(defn- mat-vec [matrix v]
  (mapv (fn [row]
          (reduce + 0.0 (map * row v)))
        matrix))

(defn- v-norm [v]
  (Math/sqrt (reduce + 0.0 (map (fn [x] (* (double x) (double x))) v))))

(defn- normalize-v [v]
  (let [n (v-norm v)]
    (if (pos? n)
      (mapv #(/ (double %) n) v)
      v)))

(defn- v-mean [v]
  (if (seq v)
    (/ (reduce + 0.0 v) (double (count v)))
    0.0))

(defn- rayleigh-quotient [matrix v]
  (let [mv (mat-vec matrix v)
        num (reduce + 0.0 (map * v mv))
        den (reduce + 0.0 (map * v v))]
    (if (pos? den) (/ num den) 0.0)))

(defn- power-iterate
  [matrix v0 steps zero-sum?]
  (loop [v (vec v0)
         i 0]
    (if (>= i steps)
      (normalize-v v)
      (let [v' (mat-vec matrix v)
            v' (if zero-sum?
                 (let [m (v-mean v')]
                   (mapv #(- (double %) m) v'))
                 v')
            v' (normalize-v v')]
        (recur v' (inc i))))))

(defn- stationary-distribution [matrix]
  (let [n (count matrix)
        v0 (vec (repeat n (/ 1.0 (double (max 1 n)))))]
    (power-iterate (transpose matrix) v0 60 false)))

(defn- second-eigenvalue [matrix]
  (let [n (count matrix)
        v0 (mapv (fn [i] (- (double i) (/ (double (dec (max 1 n))) 2.0)))
                 (range n))
        v0 (let [m (v-mean v0)] (mapv #(- (double %) m) v0))
        v (power-iterate matrix v0 60 true)]
    (rayleigh-quotient matrix v)))

(defn run->transition-matrix
  "Build a coarse-grained transition matrix from a run history."
  ([mmca-run] (run->transition-matrix mmca-run {}))
  ([mmca-run {:keys [series max-states] :or {series :gen-history max-states 32}}]
   (let [history (vec (remove nil? (get mmca-run series)))
         freqs (frequencies history)
         states (->> freqs (sort-by val >) (map key) (take max-states) vec)
         idx (zipmap states (range))
         n (count states)
         counts (vec (repeat n (vec (repeat n 0.0))))
         counts (reduce (fn [acc [a b]]
                          (if-let [i (idx a)]
                            (if-let [j (idx b)]
                              (update-in acc [i j] + 1.0)
                              acc)
                            acc))
                        counts
                        (partition 2 1 history))
         matrix (mapv (fn [row idx]
                        (let [total (reduce + 0.0 row)]
                          (if (pos? total)
                            (mapv #(/ (double %) total) row)
                            (mapv (fn [j] (if (= j idx) 1.0 0.0)) (range n)))))
                      counts (range n))]
     {:states states
      :matrix matrix
      :counts counts})))

(defn transition-matrix->signature
  "Extract a lightweight tensor signature from a transition matrix."
  [transition]
  (let [matrix (:matrix transition)
        n (count matrix)
        n (max 1 n)
        stationary (stationary-distribution matrix)
        entropy (- (reduce + 0.0 (map (fn [p]
                                        (if (pos? p)
                                          (* p (Math/log p))
                                          0.0))
                                      stationary)))
        entropy-n (if (> n 1) (/ entropy (Math/log n)) 0.0)
        effective-rank (Math/exp entropy)
        lambda2 (second-eigenvalue matrix)
        spectral-gap (max 0.0 (- 1.0 (Math/abs (double lambda2))))
        alpha (clamp-01 (- 1.0 entropy-n))]
    {:spectral-gap spectral-gap
     :projection-rank effective-rank
     :alpha-estimate alpha
     :stationary stationary}))

(defn signature->hexagram-class
  "Classify a tensor signature into a coarse hexagram label."
  [{:keys [alpha-estimate spectral-gap projection-rank]}]
  (let [alpha (double (or alpha-estimate 0.0))
        gap (double (or spectral-gap 0.0))
        rank (double (or projection-rank 0.0))]
    (cond
      (> alpha 0.8) :qian
      (< alpha 0.2) :kun
      (and (<= 0.3 alpha) (<= alpha 0.7) (>= gap 0.15)) :tai
      (and (<= 0.3 alpha) (<= alpha 0.7) (<= gap 0.05) (<= rank 2.0)) :pi
      :else :neutral)))

(defn hexagram-fitness
  "Fitness value for a hexagram class."
  ([hex-class] (hexagram-fitness hex-class nil))
  ([hex-class weights]
   (let [weights (merge {:tai 1.0
                         :qian 0.3
                         :kun 0.3
                         :pi 0.1
                         :neutral 0.5}
                        (or weights {}))]
     (double (or (get weights hex-class) (:neutral weights))))))

(defn params->hexagram-class
  "Predict a coarse hexagram class from exotype params."
  [{:keys [update-prob match-threshold]}]
  (let [u (double (or update-prob 0.0))
        m (double (or match-threshold 0.0))]
    (cond
      (and (<= 0.3 u) (<= u 0.7) (<= 0.3 m) (<= m 0.7)) :tai
      (and (<= u 0.25) (<= m 0.25)) :kun
      (and (>= u 0.75) (>= m 0.75)) :qian
      :else :neutral)))
