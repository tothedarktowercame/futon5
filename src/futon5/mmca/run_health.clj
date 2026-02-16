(ns futon5.mmca.run-health
  "Universal run health classification.

   Classifies runs into regimes:
   - :hot       - Chaotic, >60% cells changing per gen (galaxy-like)
   - :barcode   - Collapsed, >70% columns frozen (stripe-like)
   - :cooling   - Trending toward barcode, 50-70% frozen
   - :frozen    - Nearly static, <5% change rate
   - :eoc       - Edge of Chaos candidate, 15-45% change, <50% frozen
   - :unknown   - Doesn't fit other categories

   This unifies the various detection approaches:
   - Change rate (hot detection)
   - Frozen column ratio (barcode detection)
   - White/black ratio (collapse detection)")

;; === Change Rate Metrics ===

(defn hamming-distance
  "Count differing elements between two sequences."
  [a b]
  (count (filter identity (map not= a b))))

(defn change-rate
  "Calculate fraction of cells that changed between consecutive generations."
  [row-a row-b]
  (when (and (seq row-a) (seq row-b) (= (count row-a) (count row-b)))
    (double (/ (hamming-distance row-a row-b) (count row-a)))))

(defn per-generation-change-rates
  "Calculate change rate for each consecutive pair of generations."
  [history]
  (when (> (count history) 1)
    (mapv (fn [[a b]] (change-rate a b))
          (partition 2 1 history))))

(defn change-rate-stats
  "Statistics on change rates across the run.
   Returns early (gen 5-25) and final (last 20) rates."
  [history]
  (when-let [rates (per-generation-change-rates history)]
    (let [last-20 (take-last 20 rates)
          mean-rate (when (seq last-20) (/ (reduce + last-20) (count last-20)))
          early-20 (take 20 (drop 5 rates))
          early-mean (when (seq early-20) (/ (reduce + early-20) (count early-20)))]
      {:final-change-rate (when mean-rate (double mean-rate))
       :early-change-rate (when early-mean (double early-mean))
       :all-rates rates})))

;; === Frozen Column Detection (Barcode) ===

(defn column-frozen?
  "Check if a column has the same value throughout the last N rows."
  [history col-idx n]
  (let [last-n (take-last n history)]
    (when (and (seq last-n) (every? #(> (count %) col-idx) last-n))
      (let [col-vals (map #(nth % col-idx) last-n)]
        (apply = col-vals)))))

(defn frozen-column-analysis
  "Analyze how many columns are frozen (creating vertical barcode stripes)."
  [history n]
  (when (and (seq history) (>= (count history) n))
    (let [width (count (first history))
          frozen-mask (mapv #(column-frozen? history % n) (range width))
          frozen-count (count (filter identity frozen-mask))
          ;; Find contiguous frozen regions (the barcode stripes)
          runs (partition-by identity frozen-mask)
          frozen-runs (filter #(first %) runs)
          stripe-widths (map count frozen-runs)]
      {:frozen-columns frozen-count
       :total-columns width
       :frozen-ratio (double (/ frozen-count width))
       :stripe-count (count frozen-runs)
       :max-stripe-width (if (seq stripe-widths) (apply max stripe-widths) 0)
       :mean-stripe-width (if (seq stripe-widths)
                            (double (/ (reduce + stripe-widths) (count stripe-widths)))
                            0.0)})))

;; === White/Black Ratio ===

(defn color-ratios
  "Calculate white (1) and black (0) ratios for final row."
  [history]
  (when (seq history)
    (let [last-row (last history)
          total (count last-row)
          white (count (filter #{\1 1 true} last-row))
          black (count (filter #{\0 0 false} last-row))]
      {:white-ratio (double (/ white total))
       :black-ratio (double (/ black total))})))

;; === Row Periodicity ===

(defn find-period
  "Find the smallest period P where row[i] == row[i+P] for most rows."
  [history max-period min-strength]
  (when (> (count history) (* 2 max-period))
    (let [check-period (fn [p]
                         (let [pairs (partition 2 1 (partition-all p history))
                               matches (count (filter (fn [[a b]]
                                                        (and (seq a) (seq b)
                                                             (= (first a) (first b))))
                                                      pairs))
                               total (count pairs)]
                           (when (pos? total)
                             {:period p :strength (double (/ matches total))})))]
      (->> (range 2 (inc max-period))
           (map check-period)
           (filter #(and % (> (:strength %) min-strength)))
           first))))

;; === Unified Health Assessment ===

(defn assess-health
  "Comprehensive health assessment for a run.
   Returns metrics and classification."
  [run & {:keys [frozen-window] :or {frozen-window 20}}]
  (let [;; Extract history (prefer phenotype, fallback to genotype)
        phe-hist (:phe-history run)
        gen-hist (:gen-history run)
        history (if (seq phe-hist) phe-hist gen-hist)
        history-source (cond (seq phe-hist) :phenotype
                             (seq gen-hist) :genotype
                             :else :none)]
    (when (seq history)
      (let [;; Compute all metrics
            change-stats (change-rate-stats history)
            frozen-stats (frozen-column-analysis history frozen-window)
            colors (color-ratios history)
            periodicity (find-period history 20 0.7)

            ;; Extract key values
            final-rate (:final-change-rate change-stats)
            early-rate (:early-change-rate change-stats)
            frozen-ratio (or (:frozen-ratio frozen-stats) 0)
            white-ratio (or (:white-ratio colors) 0)
            black-ratio (or (:black-ratio colors) 0)

            ;; Classify
            classification
            (cond
              ;; HOT: Too chaotic (galaxy)
              (and final-rate (> final-rate 0.6))
              :hot

              ;; COLLAPSED: All white or all black
              (> white-ratio 0.9)
              :collapsed-white

              (> black-ratio 0.9)
              :collapsed-black

              ;; BARCODE: Vertical freezing (stripes)
              (> frozen-ratio 0.7)
              :barcode

              ;; PERIODIC: Row periodicity
              periodicity
              :periodic

              ;; COOLING: Trending toward barcode
              (> frozen-ratio 0.5)
              :cooling

              ;; FROZEN: Nearly static
              (and final-rate (< final-rate 0.05))
              :frozen

              ;; EoC CANDIDATE: Sweet spot
              (and final-rate
                   (< 0.15 final-rate 0.45)
                   (< frozen-ratio 0.5))
              :eoc

              :else
              :unknown)

            ;; Generate detail message
            detail
            (case classification
              :hot (format "%.1f%% cells changing per gen (chaotic)" (* 100 final-rate))
              :collapsed-white (format "%.0f%% white (collapsed)" (* 100 white-ratio))
              :collapsed-black (format "%.0f%% black (collapsed)" (* 100 black-ratio))
              :barcode (format "%.0f%% columns frozen, %d stripes"
                               (* 100 frozen-ratio) (:stripe-count frozen-stats))
              :periodic (format "Period %d attractor" (:period periodicity))
              :cooling (format "%.0f%% columns frozen (cooling)" (* 100 frozen-ratio))
              :frozen (format "%.1f%% change rate (frozen)" (* 100 (or final-rate 0)))
              :eoc (format "%.1f%% change, %.0f%% active columns"
                           (* 100 final-rate) (* 100 (- 1 frozen-ratio)))
              "Unclassified")]

        {:classification classification
         :detail detail
         :history-source history-source
         :generations (count history)
         :width (count (first history))
         :metrics {:change-rate change-stats
                   :frozen-columns frozen-stats
                   :color-ratios colors
                   :periodicity periodicity}}))))

(defn health-summary
  "One-line summary for a run."
  [health]
  (format "%s: %s"
          (name (:classification health))
          (:detail health)))
