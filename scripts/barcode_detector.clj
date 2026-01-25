(ns barcode-detector
  "Detect 'barcode collapse' — spatial stripe regimes (vertical freezing).

   Signs of barcode collapse:
   1. Low exotype diversity (only 3-4 active rules)
   2. High horizontal autocorrelation (stripes)
   3. Vertical column freezing (dominant signal)"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

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

;; === Combined Detector ===

(defn analyze-run
  "Full barcode analysis for a run."
  [run]
  (let [phe-hist (:phe-history run)
        gen-hist (:gen-history run)
        exo-hist (or (:exo-history run)
                     (get-in run [:exotype-metadata :history])
                     [])
        history (if (seq phe-hist) phe-hist gen-hist)]
    (when (seq history)
      (let [horiz-auto (horizontal-autocorrelation history 20)
            vert-freeze (vertical-freeze-analysis history 20)
            exo-unique (count-unique-exotypes exo-hist)
            exo-collapsed (exotype-collapse? exo-hist 5)]
        {:horizontal-autocorr horiz-auto
         :vertical-freeze vert-freeze
         :exotype-unique-count exo-unique
         :exotype-collapsed? exo-collapsed
         ;; Overall assessment
         :barcode-diagnosis
         (cond
           ;; Primary indicator: vertical freezing (the main barcode pattern)
           (and vert-freeze (> (:frozen-ratio vert-freeze) 0.7))
           {:status :vertical-barcode
            :detail (format "%.0f%% columns frozen, %d stripes (max width %d)"
                            (* 100 (:frozen-ratio vert-freeze))
                            (:stripe-count vert-freeze)
                            (:max-stripe-width vert-freeze))}

           (and (:mean-barcode-score horiz-auto)
                (> (:mean-barcode-score horiz-auto) 0.15))
           {:status :horizontal-stripes
            :detail (format "Barcode score %.3f" (:mean-barcode-score horiz-auto))}

           exo-collapsed
           {:status :exotype-collapse
            :detail (format "Only %d unique exotypes" (or exo-unique 0))}

           :else
           {:status :healthy
            :detail "No barcode patterns detected"})}))))

(defn print-analysis [analysis]
  (let [vf (:vertical-freeze analysis)]
    (println (format "  Vertical freeze: %.0f%% columns frozen (%d of %d)"
                     (if vf (* 100 (:frozen-ratio vf)) 0.0)
                     (or (:frozen-columns vf) 0)
                     (or (:total-columns vf) 0)))
    (when vf
      (println (format "    Stripes: %d (max width %d, mean %.1f)"
                       (:stripe-count vf)
                       (:max-stripe-width vf)
                       (:mean-stripe-width vf)))))
  (println (format "  Unique exotypes: %s"
                   (or (:exotype-unique-count analysis) "N/A")))
  (println (format "  DIAGNOSIS: %s - %s"
                   (name (get-in analysis [:barcode-diagnosis :status]))
                   (get-in analysis [:barcode-diagnosis :detail]))))

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
              (print-analysis analysis)
              (println "  (no history data)"))
            (println)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
