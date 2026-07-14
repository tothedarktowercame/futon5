(ns futon5.mmca.predictive-information
  "Predictive-information evaluator-comb for categorical CA spacetime grids.

   All occupants share one estimator and vary only its source selection:
   :self-past (AIS), :nearest-neighbor (local TE), or an
   {:type :offset :d d :tau tau} source (distance/lagged TE). MetaCA histories
   additionally expose the :bitplane, [:coarse n], and :full-cell alphabets.
   Entropies include the Miller-Madow finite-sample correction."
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
  "Estimate I(X;Y) in bits from paired categorical samples."
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
  "Estimate I(X;Y|Z) in bits from aligned categorical samples."
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
      (throw (ex-info "grid must be a non-empty rectangle"
                      {:rows (count rows) :widths widths})))
    rows))

(defn- normalize-source-hole [source-hole]
  (cond
    (= source-hole :self-past)
    {:type :self-past}

    (= source-hole :nearest-neighbor)
    {:type :nearest-neighbor}

    (and (map? source-hole) (= :offset (:type source-hole)))
    (let [d (long (:d source-hole))
          tau (long (:tau source-hole))]
      (when (zero? d)
        (throw (ex-info "offset source requires nonzero d" {:d d :tau tau})))
      (when-not (pos? tau)
        (throw (ex-info "offset source requires positive tau" {:d d :tau tau})))
      {:type :offset :d d :tau tau})

    :else
    (throw (ex-info "unknown predictive-information sourceHole"
                    {:source-hole source-hole}))))

(defn- source-links [width source-hole]
  (case (:type source-hole)
    :self-past
    (mapv (fn [destination]
            {:destination destination :source destination :d 0 :tau 0})
          (range width))

    :nearest-neighbor
    (mapv (fn [[destination d]]
            {:destination destination :source (+ destination d) :d d :tau 1})
          (for [destination (range 1 (dec width)) d [-1 1]]
            [destination d]))

    :offset
    (let [{:keys [d tau]} source-hole]
      (mapv (fn [destination]
              {:destination destination
               :source (+ destination d)
               :d d
               :tau tau})
            (filter #(< -1 (+ % d) width) (range width))))))

(defn- source-observation [grid source t tau source-history]
  (if (= source-history 1)
    (nth (nth grid (- t tau)) source)
    (mapv #(nth (nth grid %) source)
          (range (inc (- t tau source-history))
                 (inc (- t tau))))))

(defn predictive-information
  "Run one evaluator-comb occupant on a categorical [time][cell] grid.

   Options form the two variation seams:
   - :source-hole    :self-past, :nearest-neighbor, or
                     {:type :offset :d signed-distance :tau positive-lag}
   - :k              destination-past length (default 8)
   - :source-history source-past length for TE occupants (default 1)
   - :burn-in        first eligible destination time (default k)

   :self-past estimates I(destination-past; destination-next). Transfer
   occupants estimate I(source-past; destination-next | destination-past).
   The sampling, correction, and aggregation paths are otherwise identical."
  ([grid source-hole] (predictive-information grid source-hole {}))
  ([grid source-hole {:keys [k source-history burn-in]
                      :or {k 8 source-history 1}}]
   (let [grid (validate-grid! grid)
         source-hole (normalize-source-hole source-hole)
         k (long k)
         source-history (long source-history)
         burn-in (long (or burn-in k))
         times (count grid)
         width (count (first grid))
         transfer? (not= :self-past (:type source-hole))
         links (source-links width source-hole)
         max-tau (case (:type source-hole)
                   :self-past 0
                   :nearest-neighbor 1
                   :offset (:tau source-hole))
         source-start (if transfer? (+ max-tau (dec source-history)) 0)
         start (max k burn-in source-start)]
     (when-not (pos? k)
       (throw (ex-info "destination-past k must be positive" {:k k})))
     (when-not (pos? source-history)
       (throw (ex-info "source-history must be positive"
                       {:source-history source-history})))
     (when (empty? links)
       (throw (ex-info "sourceHole has no valid links at this grid width"
                       {:width width :source-hole source-hole})))
     (when (>= start times)
       (throw (ex-info "grid is too short for estimator windows and burn-in"
                       {:times times :k k :source-history source-history
                        :source-hole source-hole :burn-in burn-in})))
     (let [per-source
           (mapv
            (fn [{:keys [source destination d tau] :as link}]
              (let [samples
                    (mapv (fn [t]
                            (let [destination-past
                                  (mapv #(nth (nth grid %) destination)
                                        (range (- t k) t))]
                              {:source-past
                               (if transfer?
                                 (source-observation grid source t tau source-history)
                                 destination-past)
                               :destination-next (nth (nth grid t) destination)
                               :destination-past destination-past}))
                          (range start times))
                    estimate
                    (if transfer?
                      (conditional-mutual-information-estimate
                       (mapv :source-past samples)
                       (mapv :destination-next samples)
                       (mapv :destination-past samples))
                      (mutual-information-estimate
                       (mapv :source-past samples)
                       (mapv :destination-next samples)))]
                (assoc estimate
                       :source source
                       :destination destination
                       :offset d
                       :tau tau
                       :link link)))
            links)
           avg (fn [key]
                 (/ (reduce + 0.0 (map key per-source))
                    (double (count per-source))))]
       {:measure :predictive-information
        :source-hole source-hole
        :k k
        :source-history (if transfer? source-history k)
        :burn-in burn-in
        :time-steps times
        :width width
        :samples-per-source (- times start)
        :source-count (count per-source)
        :score-plugin (avg :plugin)
        :score-corrected (avg :miller-madow)
        :per-source per-source}))))

(defn active-information-storage
  "AIS occupant: sourceHole = self-past. Legacy result keys are preserved."
  ([grid] (active-information-storage grid {}))
  ([grid opts]
   (let [result (predictive-information grid :self-past opts)]
     (assoc result
            :measure :active-information-storage
            :samples-per-cell (:samples-per-source result)
            :ais-plugin (:score-plugin result)
            :ais-corrected (:score-corrected result)
            :per-cell (:per-source result)))))

(defn transfer-entropy
  "Nearest-neighbor TE occupant. Legacy result keys are preserved."
  ([grid] (transfer-entropy grid {}))
  ([grid opts]
   (let [result (predictive-information grid :nearest-neighbor opts)]
     (assoc result
            :measure :transfer-entropy
            :samples-per-link (:samples-per-source result)
            :directed-links (:source-count result)
            :te-plugin (:score-plugin result)
            :te-corrected (:score-corrected result)
            :per-link (:per-source result)))))

(defn distance-transfer-entropy
  "Distance/lagged TE occupant at signed spatial offset D and temporal lag TAU."
  ([grid d tau] (distance-transfer-entropy grid d tau {}))
  ([grid d tau opts]
   (let [result (predictive-information
                 grid {:type :offset :d d :tau tau} opts)]
     (assoc result
            :measure :distance-transfer-entropy
            :samples-per-link (:samples-per-source result)
            :directed-links (:source-count result)
            :te-plugin (:score-plugin result)
            :te-corrected (:score-corrected result)
            :per-link (:per-source result)))))

(defn- sigil->cell [sigil]
  (reduce (fn [value bit] (+ (* 2 value) bit))
          0
          (bitplane/sigil->bits sigil)))

(defn- full-cell-grid [history]
  (mapv (fn [row]
          (mapv (fn [cell]
                  (cond
                    (number? cell) (long cell)
                    (char? cell) (sigil->cell cell)
                    :else (sigil->cell (first (str cell)))))
                row))
        history))

(defn- normalize-alphabet [alphabet]
  (cond
    (= alphabet :bitplane) {:type :bitplane}
    (or (= alphabet :full-cell) (= alphabet :fullCell)) {:type :full-cell}
    (and (vector? alphabet) (= :coarse (first alphabet)))
    {:type :coarse :bins (long (second alphabet))}
    (and (map? alphabet) (= :coarse (:type alphabet)))
    {:type :coarse :bins (long (:bins alphabet))}
    :else (throw (ex-info "unknown alphabet fill" {:alphabet alphabet}))))

(defn project-history
  "Fill the alphabet seam for a MetaCA history.

   Returns one projection for :full-cell or [:coarse n], and all eight binary
   projections for :bitplane. Coarse bins are equal-width over cell values
   0..255."
  [history alphabet]
  (let [{:keys [type bins] :as alphabet} (normalize-alphabet alphabet)]
    (case type
      :bitplane
      (mapv (fn [plane grid]
              {:projection {:alphabet :bitplane :plane plane} :grid grid})
            (range)
            (bitplane/decompose-history history))

      :full-cell
      [{:projection {:alphabet :full-cell}
        :grid (full-cell-grid history)}]

      :coarse
      (do
        (when-not (< 1 bins 257)
          (throw (ex-info "coarse alphabet needs 2..256 bins"
                          {:alphabet alphabet})))
        [{:projection {:alphabet :coarse :bins bins}
          :grid (mapv (fn [row]
                        (mapv #(min (dec bins) (quot (* % bins) 256)) row))
                      (full-cell-grid history))}]))))

(defn score-history
  "Run an evaluator-comb occupant on a MetaCA history through ALPHABET."
  ([history source-hole alphabet]
   (score-history history source-hole alphabet {}))
  ([history source-hole alphabet opts]
   (let [projections (project-history history alphabet)
         scores (mapv (fn [{:keys [projection grid]}]
                        (assoc (predictive-information grid source-hole opts)
                               :projection projection))
                      projections)
         corrected (mapv :score-corrected scores)]
     {:measure :metaca-predictive-information
      :source-hole (normalize-source-hole source-hole)
      :alphabet (normalize-alphabet alphabet)
      :projection-count (count scores)
      :mean-score-corrected (/ (reduce + 0.0 corrected)
                               (double (count corrected)))
      :max-score-corrected (apply max corrected)
      :per-projection scores})))

(defn score-metaca-history
  "Apply the AIS occupant to every bitplane. Legacy result keys are preserved."
  ([history] (score-metaca-history history {}))
  ([history opts]
   (let [result (score-history history :self-past :bitplane opts)
         scores (:per-projection result)]
     (assoc result
            :measure :bitplane-active-information-storage
            :plane-count (:projection-count result)
            :mean-ais-corrected (:mean-score-corrected result)
            :max-ais-corrected (:max-score-corrected result)
            :per-plane (mapv (fn [score]
                               (assoc score :plane (get-in score [:projection :plane])
                                      :ais-corrected (:score-corrected score)))
                             scores)))))

(defn score-metaca-transfer-entropy
  "Apply nearest-neighbor TE to every bitplane. Legacy keys are preserved."
  ([history] (score-metaca-transfer-entropy history {}))
  ([history opts]
   (let [result (score-history history :nearest-neighbor :bitplane opts)
         scores (:per-projection result)]
     (assoc result
            :measure :bitplane-transfer-entropy
            :plane-count (:projection-count result)
            :mean-te-corrected (:mean-score-corrected result)
            :max-te-corrected (:max-score-corrected result)
            :per-plane (mapv (fn [score]
                               (assoc score :plane (get-in score [:projection :plane])
                                      :te-corrected (:score-corrected score)))
                             scores)))))

(defn score-metaca-distance-transfer-entropy
  "Run distance/lagged TE through a selected MetaCA alphabet fill."
  ([history d tau alphabet]
   (score-metaca-distance-transfer-entropy history d tau alphabet {}))
  ([history d tau alphabet opts]
   (let [result (score-history history {:type :offset :d d :tau tau}
                               alphabet opts)]
     (assoc result
            :measure :metaca-distance-transfer-entropy
            :mean-te-corrected (:mean-score-corrected result)
            :max-te-corrected (:max-score-corrected result)))))
