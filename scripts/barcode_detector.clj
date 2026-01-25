(ns barcode-detector
  "Detect 'barcode collapse' — when the system degenerates into
   low-dimensional periodic attractors instead of sustained EoC.

   Signs of barcode collapse:
   1. Low exotype diversity (only 3-4 active rules)
   2. Row periodicity (repeating patterns)
   3. High horizontal autocorrelation (stripes)"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

;; === Exotype Diversity ===

(defn count-unique-exotypes
  "Count unique exotype values across all cells and generations.
   Low count (< 5) suggests collapse to few rules."
  [exo-history]
  (when (seq exo-history)
    (let [all-values (mapcat seq exo-history)]
      (count (set all-values)))))

(defn exotype-diversity-per-generation
  "Count unique exotypes per generation."
  [exo-history]
  (mapv #(count (set %)) exo-history))

(defn exotype-collapse?
  "Check if exotype diversity has collapsed.
   Returns true if final generations have < threshold unique exotypes."
  [exo-history threshold]
  (when (seq exo-history)
    (let [last-10 (take-last 10 exo-history)
          diversities (map #(count (set %)) last-10)
          avg-diversity (/ (reduce + diversities) (count diversities))]
      (< avg-diversity threshold))))

;; === Row Periodicity ===

(defn find-period
  "Find the smallest period P where row[i] == row[i+P] for most rows.
   Returns {:period P :strength ratio} or nil if no strong period found."
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

(defn row-periodicity
  "Detect if phenotype rows repeat with a short period.
   Returns {:periodic? bool :period P :strength ratio}"
  [phe-history]
  (if-let [result (find-period phe-history 20 0.7)]
    (assoc result :periodic? true)
    {:periodic? false}))

;; === Horizontal Autocorrelation (Barcode Detection) ===

(defn horizontal-runs
  "Count runs of consecutive identical values in a row.
   Barcode = few long runs. Coral = many short runs."
  [row]
  (when (seq row)
    (let [runs (partition-by identity row)]
      {:run-count (count runs)
       :mean-run-length (double (/ (count row) (count runs)))
       :max-run-length (apply max (map count runs))})))

(defn barcode-score
  "Score how 'barcode-like' a row is.
   High score = long horizontal runs = stripes.
   Returns 0-1 where 1 = pure barcode."
  [row]
  (let [{:keys [mean-run-length]} (horizontal-runs row)
        max-possible (count row)]
    (/ mean-run-length max-possible)))

(defn average-barcode-score
  "Average barcode score across all rows in history."
  [history]
  (when (seq history)
    (let [scores (map barcode-score history)]
      (/ (reduce + scores) (count scores)))))

(defn horizontal-autocorrelation
  "Measure horizontal autocorrelation for last N rows.
   High value = barcode stripes."
  [history n]
  (let [last-rows (take-last n history)
        scores (map barcode-score last-rows)]
    {:mean-barcode-score (when (seq scores) (/ (reduce + scores) (count scores)))
     :max-barcode-score (when (seq scores) (apply max scores))}))

;; === Vertical Freezing (The Real Barcode) ===

(defn column-frozen?
  "Check if a column has the same value throughout the last N rows."
  [history col-idx n]
  (let [last-n (take-last n history)
        col-vals (map #(nth % col-idx) last-n)]
    (apply = col-vals)))

(defn vertical-freeze-analysis
  "Analyze how many columns are frozen (creating vertical barcode stripes).
   This is the main barcode indicator."
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

;; === Change Rate (Hot Detection) ===

(defn hamming-distance
  "Count differing elements between two sequences."
  [a b]
  (count (filter identity (map not= a b))))

(defn change-rate
  "Calculate fraction of cells that changed between consecutive generations."
  [row-a row-b]
  (when (and (seq row-a) (seq row-b))
    (double (/ (hamming-distance row-a row-b) (count row-a)))))

(defn per-generation-change-rates
  "Calculate change rate for each consecutive pair of generations."
  [history]
  (when (> (count history) 1)
    (mapv (fn [[a b]] (change-rate a b))
          (partition 2 1 history))))

(defn change-rate-stats
  "Statistics on change rates across the run."
  [history]
  (when-let [rates (per-generation-change-rates history)]
    (let [last-20 (take-last 20 rates)
          mean-rate (/ (reduce + last-20) (count last-20))
          early-20 (take 20 (drop 5 rates))  ;; skip first 5 (initialization noise)
          early-mean (when (seq early-20) (/ (reduce + early-20) (count early-20)))]
      {:final-change-rate (double mean-rate)
       :early-change-rate (when early-mean (double early-mean))
       :min-rate (apply min rates)
       :max-rate (apply max rates)})))

;; === Combined Detector ===

(defn analyze-run
  "Full barcode/periodicity/chaos analysis for a run."
  [run]
  (let [phe-hist (:phe-history run)
        gen-hist (:gen-history run)
        exo-hist (or (:exo-history run)
                     (get-in run [:exotype-metadata :history])
                     [])
        history (if (seq phe-hist) phe-hist gen-hist)]
    (when (seq history)
      (let [periodicity (row-periodicity history)
            horiz-auto (horizontal-autocorrelation history 20)
            vert-freeze (vertical-freeze-analysis history 20)
            change-stats (change-rate-stats history)
            exo-unique (count-unique-exotypes exo-hist)
            exo-collapsed (exotype-collapse? exo-hist 5)
            final-rate (:final-change-rate change-stats)]
        {:row-periodicity periodicity
         :horizontal-autocorr horiz-auto
         :vertical-freeze vert-freeze
         :change-rate-stats change-stats
         :exotype-unique-count exo-unique
         :exotype-collapsed? exo-collapsed
         ;; Overall assessment - check BOTH failure modes
         :diagnosis
         (cond
           ;; HOT: Too much change (chaotic/galaxy)
           (and final-rate (> final-rate 0.6))
           {:status :hot
            :detail (format "%.1f%% cells changing per gen (chaotic)" (* 100 final-rate))}

           ;; COLD: Vertical freezing (barcode collapse)
           (and vert-freeze (> (:frozen-ratio vert-freeze) 0.7))
           {:status :barcode
            :detail (format "%.0f%% columns frozen, %d stripes"
                            (* 100 (:frozen-ratio vert-freeze))
                            (:stripe-count vert-freeze))}

           ;; COLD: Row periodicity
           (:periodic? periodicity)
           {:status :periodic
            :detail (format "Period %d attractor" (:period periodicity))}

           ;; BORDERLINE: Moderate freeze (50-70%)
           (and vert-freeze (> (:frozen-ratio vert-freeze) 0.5))
           {:status :cooling
            :detail (format "%.0f%% columns frozen (trending toward barcode)"
                            (* 100 (:frozen-ratio vert-freeze)))}

           ;; BORDERLINE: Low change rate but not frozen columns
           (and final-rate (< final-rate 0.1))
           {:status :frozen
            :detail (format "Only %.1f%% cells changing (frozen)" (* 100 final-rate))}

           ;; EoC CANDIDATE: Moderate change rate, not frozen
           (and final-rate (< 0.15 final-rate 0.45)
                (or (nil? vert-freeze) (< (:frozen-ratio vert-freeze) 0.5)))
           {:status :eoc-candidate
            :detail (format "%.1f%% change rate, %.0f%% columns active"
                            (* 100 final-rate)
                            (* 100 (- 1 (or (:frozen-ratio vert-freeze) 0))))}

           :else
           {:status :unknown
            :detail (format "Change=%.1f%%, Frozen=%.0f%%"
                            (* 100 (or final-rate 0))
                            (* 100 (or (:frozen-ratio vert-freeze) 0)))})}))))

(defn print-analysis [analysis label]
  ;; Change rate (hot detection)
  (let [cs (:change-rate-stats analysis)]
    (println (format "  Change rate: %.1f%% final, %.1f%% early"
                     (* 100 (or (:final-change-rate cs) 0))
                     (* 100 (or (:early-change-rate cs) 0)))))
  ;; Vertical freeze (barcode detection)
  (let [vf (:vertical-freeze analysis)]
    (println (format "  Vertical freeze: %.0f%% columns frozen (%d of %d)"
                     (if vf (* 100 (:frozen-ratio vf)) 0.0)
                     (or (:frozen-columns vf) 0)
                     (or (:total-columns vf) 0)))
    (when (and vf (> (:stripe-count vf) 0))
      (println (format "    Stripes: %d (max width %d, mean %.1f)"
                       (:stripe-count vf)
                       (:max-stripe-width vf)
                       (:mean-stripe-width vf)))))
  ;; Row periodicity
  (println (format "  Row periodicity: %s"
                   (if (get-in analysis [:row-periodicity :periodic?])
                     (format "YES (period=%d, strength=%.2f)"
                             (get-in analysis [:row-periodicity :period])
                             (get-in analysis [:row-periodicity :strength]))
                     "no")))
  ;; Exotype diversity
  (println (format "  Unique exotypes: %s"
                   (or (:exotype-unique-count analysis) "N/A")))
  ;; Final diagnosis
  (let [diag (:diagnosis analysis)
        status-str (name (:status diag))
        status-fmt (case (:status diag)
                     :hot "HOT"
                     :barcode "BARCODE"
                     :periodic "PERIODIC"
                     :cooling "COOLING"
                     :frozen "FROZEN"
                     :eoc-candidate "EoC-CANDIDATE"
                     (clojure.string/upper-case status-str))]
    (println (format "  DIAGNOSIS: %s - %s" status-fmt (:detail diag)))))

(defn -main [& args]
  (let [runs-dir (first args)]
    (if (nil? runs-dir)
      (println "Usage: bb scripts/barcode_detector.clj <runs-dir>")
      (let [files (->> (io/file runs-dir)
                       (.listFiles)
                       (filter #(and (.endsWith (.getName %) ".edn")
                                     (not (.contains (.getName %) "classification"))))
                       (sort-by #(.getName %)))]
        (doseq [f files]
          (println (str "=== " (.getName f) " ==="))
          (let [run (edn/read-string {:readers {'object (fn [_] nil)}} (slurp f))
                analysis (analyze-run run)]
            (if analysis
              (print-analysis analysis (.getName f))
              (println "  (no history data)"))
            (println)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
