(ns futon5.mmca.predictive-information
  "Predictive-information discriminators for binary CA spacetime grids.

   Active information storage (AIS) is I(X past-k; X next), computed for each
   spatial cell and then averaged. Entropies are reported both as plug-in
   estimates and with the Miller-Madow finite-sample correction. MetaCA
   histories use the same estimator after the standard eight-bitplane
   projection from bitplane-analysis."
  (:require [futon5.mmca.bitplane-analysis :as bitplane]))

(defn- log2 [x]
  (/ (Math/log (double x)) (Math/log 2.0)))

(defn entropy-estimate
  "Return plug-in and Miller-Madow entropy estimates (bits) for categorical XS."
  [xs]
  (let [n (count xs)
        counts (vals (frequencies xs))
        observed (count counts)]
    (if (zero? n)
      {:plugin 0.0 :miller-madow 0.0 :samples 0 :observed-states 0}
      (let [plugin (- (reduce + 0.0
                              (map (fn [count]
                                     (let [p (/ (double count) n)]
                                       (* p (log2 p))))
                                   counts)))
            correction (/ (dec observed)
                          (* 2.0 n (Math/log 2.0)))]
        {:plugin plugin
         :miller-madow (+ plugin correction)
         :samples n
         :observed-states observed}))))

(defn mutual-information-estimate
  "Estimate I(X;Y) in bits from paired categorical samples.
   The corrected estimate applies Miller-Madow to H(X)+H(Y)-H(X,Y) and is
   clamped at zero because finite-sample corrections can slightly overshoot."
  [xs ys]
  (when-not (= (count xs) (count ys))
    (throw (ex-info "paired samples must have equal length"
                    {:x (count xs) :y (count ys)})))
  (let [hx (entropy-estimate xs)
        hy (entropy-estimate ys)
        hxy (entropy-estimate (map vector xs ys))
        mi (fn [estimator]
             (max 0.0 (- (+ (get hx estimator) (get hy estimator))
                         (get hxy estimator))))]
    {:plugin (mi :plugin)
     :miller-madow (mi :miller-madow)
     :samples (count xs)
     :past-states (:observed-states hx)
     :next-states (:observed-states hy)
     :joint-states (:observed-states hxy)}))

(defn- validate-grid! [grid]
  (let [rows (mapv vec grid)
        widths (set (map count rows))]
    (when (or (empty? rows) (not= 1 (count widths)) (zero? (first widths)))
      (throw (ex-info "grid must be a non-empty rectangle" {:rows (count rows)
                                                             :widths widths})))
    (when-not (every? #{0 1} (mapcat identity rows))
      (throw (ex-info "predictive-information grid must be binary" {})))
    rows))

(defn active-information-storage
  "Compute per-cell AIS on a binary [time][cell] grid.

   Options:
   - :k       number of past states (default 8)
   - :burn-in first time index eligible as the predicted state (default k)

   Returns mean corrected/raw AIS and the complete per-cell estimates so
   downstream analyses can inspect spatial heterogeneity and sample support."
  ([grid] (active-information-storage grid {}))
  ([grid {:keys [k burn-in] :or {k 8}}]
   (let [grid (validate-grid! grid)
         k (long k)
         burn-in (long (or burn-in k))
         times (count grid)
         width (count (first grid))
         start (max k burn-in)]
     (when-not (pos? k)
       (throw (ex-info "AIS past window k must be positive" {:k k})))
     (when (>= start times)
       (throw (ex-info "grid is too short for AIS window and burn-in"
                       {:times times :k k :burn-in burn-in})))
     (let [per-cell
           (mapv
            (fn [x]
              (let [pairs (mapv (fn [t]
                                  [(mapv #(nth (nth grid %) x) (range (- t k) t))
                                   (nth (nth grid t) x)])
                                (range start times))
                    estimate (mutual-information-estimate (mapv first pairs)
                                                          (mapv second pairs))]
                (assoc estimate :cell x)))
            (range width))
           avg (fn [key]
                 (/ (reduce + 0.0 (map key per-cell)) (double width)))]
       {:measure :active-information-storage
        :k k
        :burn-in burn-in
        :time-steps times
        :width width
        :samples-per-cell (- times start)
        :ais-plugin (avg :plugin)
        :ais-corrected (avg :miller-madow)
        :per-cell per-cell}))))

(defn score-metaca-history
  "Apply AIS to every binary bitplane of a MetaCA sigil/rule history.
   HISTORY rows may be sigil strings, matching bitplane-analysis' substrate.
   The aggregate reports the mean and maximum plane scores without selecting a
   plane post hoc."
  ([history] (score-metaca-history history {}))
  ([history opts]
   (let [planes (bitplane/decompose-history history)
         scores (mapv #(active-information-storage % opts) planes)
         corrected (mapv :ais-corrected scores)]
     {:measure :bitplane-active-information-storage
      :plane-count (count scores)
      :mean-ais-corrected (/ (reduce + 0.0 corrected) (double (count corrected)))
      :max-ais-corrected (apply max corrected)
      :per-plane (mapv (fn [idx score] (assoc score :plane idx))
                       (range)
                       scores)})))
