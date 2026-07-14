(ns futon5.mmca.predictive-information
  "Predictive-information discriminators for binary CA spacetime grids.

   Active information storage (AIS) is I(X past-k; X next), computed for each
   spatial cell and then averaged. Transfer entropy (TE) is the spatial
   complement I(source past; destination next | destination past-k), averaged
   over directed nearest-neighbor links. Entropies are reported both as
   plug-in estimates and with the Miller-Madow finite-sample correction.
   MetaCA histories use the same estimators after the standard eight-bitplane
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

(defn conditional-mutual-information-estimate
  "Estimate I(X;Y|Z) in bits from aligned categorical samples.

   Uses H(X,Z)+H(Y,Z)-H(Z)-H(X,Y,Z), with both plug-in and Miller-Madow
   entropy estimates. The corrected estimate is clamped at zero when the
   finite-sample correction slightly overshoots."
  [xs ys zs]
  (when-not (= (count xs) (count ys) (count zs))
    (throw (ex-info "conditional-MI samples must have equal length"
                    {:x (count xs) :y (count ys) :z (count zs)})))
  (let [hxz (entropy-estimate (map vector xs zs))
        hyz (entropy-estimate (map vector ys zs))
        hz (entropy-estimate zs)
        hxyz (entropy-estimate (map vector xs ys zs))
        cmi (fn [estimator]
              (max 0.0 (- (+ (get hxz estimator) (get hyz estimator))
                          (get hz estimator)
                          (get hxyz estimator))))]
    {:plugin (cmi :plugin)
     :miller-madow (cmi :miller-madow)
     :samples (count xs)
     :source-destination-past-states (:observed-states hxz)
     :next-destination-past-states (:observed-states hyz)
     :destination-past-states (:observed-states hz)
     :joint-states (:observed-states hxyz)}))

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

(defn transfer-entropy
  "Compute local nearest-neighbor transfer entropy on a binary grid.

   For every non-boundary destination cell and each source offset -1/+1,
   estimate I(source[t-1]; destination[t] | destination[t-k..t-1]). The
   destination history length k matches AIS; source history length one is the
   standard local-TE convention for a first-order CA neighborhood.

   Returns raw/corrected means and every directed-link estimate. Fixed boundary
   cells are excluded rather than treated as informative sources."
  ([grid] (transfer-entropy grid {}))
  ([grid {:keys [k burn-in] :or {k 8}}]
   (let [grid (validate-grid! grid)
         k (long k)
         burn-in (long (or burn-in k))
         times (count grid)
         width (count (first grid))
         start (max k burn-in)]
     (when-not (pos? k)
       (throw (ex-info "TE destination window k must be positive" {:k k})))
     (when (< width 3)
       (throw (ex-info "TE grid requires an interior cell and two neighbors"
                       {:width width})))
     (when (>= start times)
       (throw (ex-info "grid is too short for TE window and burn-in"
                       {:times times :k k :burn-in burn-in})))
     (let [per-link
           (mapv
            (fn [[destination offset]]
              (let [source (+ destination offset)
                    samples
                    (mapv (fn [t]
                            {:source-past (nth (nth grid (dec t)) source)
                             :destination-next (nth (nth grid t) destination)
                             :destination-past
                             (mapv #(nth (nth grid %) destination)
                                   (range (- t k) t))})
                          (range start times))
                    estimate
                    (conditional-mutual-information-estimate
                     (mapv :source-past samples)
                     (mapv :destination-next samples)
                     (mapv :destination-past samples))]
                (assoc estimate
                       :source source
                       :destination destination
                       :offset offset)))
            (for [destination (range 1 (dec width))
                  offset [-1 1]]
              [destination offset]))
           avg (fn [key]
                 (/ (reduce + 0.0 (map key per-link))
                    (double (count per-link))))]
       {:measure :transfer-entropy
        :k k
        :source-history 1
        :burn-in burn-in
        :time-steps times
        :width width
        :samples-per-link (- times start)
        :directed-links (count per-link)
        :te-plugin (avg :plugin)
        :te-corrected (avg :miller-madow)
        :per-link per-link}))))

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

(defn score-metaca-transfer-entropy
  "Apply nearest-neighbor TE to every binary bitplane of a MetaCA history.
   Reports the predeclared all-plane mean and the maximum only as a diagnostic."
  ([history] (score-metaca-transfer-entropy history {}))
  ([history opts]
   (let [planes (bitplane/decompose-history history)
         scores (mapv #(transfer-entropy % opts) planes)
         corrected (mapv :te-corrected scores)]
     {:measure :bitplane-transfer-entropy
      :plane-count (count scores)
      :mean-te-corrected (/ (reduce + 0.0 corrected) (double (count corrected)))
      :max-te-corrected (apply max corrected)
      :per-plane (mapv (fn [idx score] (assoc score :plane idx))
                       (range)
                       scores)})))
