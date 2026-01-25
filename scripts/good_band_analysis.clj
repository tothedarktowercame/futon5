(ns good-band-analysis
  "Analyze what's happening inside the 'good' (moderate activity) bands.
   Goal: understand what dynamics produce EoC-like behavior so we can
   encode those patterns into new wirings."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- char->bit [c]
  (case c \0 0 \1 1 (if (< (int c) 128) 0 1)))

(defn- string->bits [s]
  (mapv char->bit s))

(defn column-values
  "Extract values for column idx across all generations."
  [history idx]
  (mapv #(nth (string->bits %) idx) history))

(defn column-change-rate
  "Fraction of generations where column idx changes value."
  [history idx]
  (let [col (column-values history idx)
        pairs (partition 2 1 col)
        changes (count (filter (fn [[a b]] (not= a b)) pairs))]
    (if (< (count col) 2)
      0.0
      (double (/ changes (dec (count col)))))))

(defn find-moderate-columns
  "Find columns with change rate in moderate zone (0.15-0.45)."
  [history]
  (let [len (count (first history))]
    (for [i (range len)
          :let [cr (column-change-rate history i)]
          :when (< 0.15 cr 0.45)]
      {:column i :change-rate cr})))

;; === Pattern Analysis ===

(defn run-length-encoding
  "Convert a column's values to run-length encoding.
   E.g., [1 1 1 0 0 1] -> [[1 3] [0 2] [1 1]]"
  [values]
  (->> values
       (partition-by identity)
       (mapv (fn [run] [(first run) (count run)]))))

(defn oscillation-period
  "Detect if the column has a regular oscillation pattern.
   Returns the period if detected, nil otherwise."
  [values]
  (let [rle (run-length-encoding values)
        lengths (map second rle)]
    ;; Check for regular alternation
    (when (> (count lengths) 4)
      (let [diffs (map - (rest lengths) lengths)]
        (when (every? #(< (Math/abs %) 2) (take 10 diffs))
          ;; Roughly regular
          (let [avg-length (/ (reduce + (take 10 lengths)) (min 10 (count lengths)))]
            {:type :oscillating
             :avg-period (* 2 avg-length)}))))))

(defn burst-pattern
  "Detect burst patterns - periods of activity separated by stability."
  [values]
  (let [rle (run-length-encoding values)
        run-lengths (map second rle)]
    (when (seq run-lengths)
      (let [long-runs (filter #(> % 5) run-lengths)
            short-runs (filter #(<= % 3) run-lengths)]
        (when (and (seq long-runs) (seq short-runs))
          {:type :bursty
           :long-run-count (count long-runs)
           :short-run-count (count short-runs)
           :avg-long-run (/ (reduce + long-runs) (count long-runs))})))))

;; === Neighborhood Analysis ===

(defn neighborhood-context
  "For a moderate column, analyze what its neighbors are doing."
  [history col-idx]
  (let [len (count (first history))
        left-idx (max 0 (dec col-idx))
        right-idx (min (dec len) (inc col-idx))
        self-cr (column-change-rate history col-idx)
        left-cr (column-change-rate history left-idx)
        right-cr (column-change-rate history right-idx)]
    {:self col-idx
     :self-change-rate self-cr
     :left-change-rate left-cr
     :right-change-rate right-cr
     :left-type (cond (< left-cr 0.05) :frozen
                      (> left-cr 0.45) :chaotic
                      :else :moderate)
     :right-type (cond (< right-cr 0.05) :frozen
                       (> right-cr 0.45) :chaotic
                       :else :moderate)}))

;; === Cross-Layer Analysis ===

(defn layer-correlation
  "Compare genotype and phenotype patterns for a column.
   Do they change together, or independently?"
  [gen-hist phe-hist col-idx]
  (when (and (seq gen-hist) (seq phe-hist)
             (= (count gen-hist) (count phe-hist)))
    (let [gen-col (column-values gen-hist col-idx)
          phe-col (column-values phe-hist col-idx)
          pairs (map vector gen-col phe-col)
          ;; Count different types of transitions
          both-change (count (filter (fn [[[g1 p1] [g2 p2]]]
                                       (and (not= g1 g2) (not= p1 p2)))
                                     (partition 2 1 pairs)))
          gen-only (count (filter (fn [[[g1 p1] [g2 p2]]]
                                    (and (not= g1 g2) (= p1 p2)))
                                  (partition 2 1 pairs)))
          phe-only (count (filter (fn [[[g1 p1] [g2 p2]]]
                                    (and (= g1 g2) (not= p1 p2)))
                                  (partition 2 1 pairs)))
          neither (count (filter (fn [[[g1 p1] [g2 p2]]]
                                   (and (= g1 g2) (= p1 p2)))
                                 (partition 2 1 pairs)))
          total (+ both-change gen-only phe-only neither)]
      {:both-change-pct (when (pos? total) (double (/ both-change total)))
       :gen-only-pct (when (pos? total) (double (/ gen-only total)))
       :phe-only-pct (when (pos? total) (double (/ phe-only total)))
       :correlation-type (cond
                           (> both-change (max gen-only phe-only)) :coupled
                           (> gen-only phe-only) :gen-leads
                           (> phe-only gen-only) :phe-leads
                           :else :independent)})))

;; === Sigil Analysis ===

(defn extract-sigils-at-column
  "Get the sequence of sigils at a specific column across generations."
  [gen-hist col-idx]
  (mapv #(nth % col-idx nil) gen-hist))

(defn sigil-transition-matrix
  "Build a transition matrix for sigil changes at a column.
   Returns which sigils tend to follow which."
  [gen-hist col-idx]
  (let [sigils (extract-sigils-at-column gen-hist col-idx)
        pairs (partition 2 1 sigils)
        transitions (frequencies pairs)]
    {:unique-sigils (count (set sigils))
     :unique-transitions (count transitions)
     :top-transitions (->> transitions
                           (sort-by val >)
                           (take 5)
                           (mapv (fn [[[from to] cnt]] {:from from :to to :count cnt})))}))

;; === Main Analysis ===

(defn analyze-good-band
  "Full analysis of a single 'good' (moderate) column."
  [run col-idx]
  (let [gen-hist (:gen-history run)
        phe-hist (:phe-history run)
        history (if (seq phe-hist) phe-hist gen-hist)
        col-vals (column-values history col-idx)]
    {:column col-idx
     :change-rate (column-change-rate history col-idx)
     :pattern (or (oscillation-period col-vals)
                  (burst-pattern col-vals)
                  {:type :irregular})
     :neighborhood (neighborhood-context history col-idx)
     :layer-correlation (when (and (seq gen-hist) (seq phe-hist))
                          (layer-correlation gen-hist phe-hist col-idx))
     :sigil-dynamics (when (seq gen-hist)
                       (sigil-transition-matrix gen-hist col-idx))}))

(defn analyze-run-good-bands
  "Analyze all moderate columns in a run."
  [run]
  (let [phe-hist (:phe-history run)
        gen-hist (:gen-history run)
        history (if (seq phe-hist) phe-hist gen-hist)
        moderate-cols (find-moderate-columns history)]
    {:total-columns (count (first history))
     :moderate-count (count moderate-cols)
     :analyses (mapv #(analyze-good-band run (:column %)) moderate-cols)}))

(defn summarize-good-bands
  "Aggregate patterns across all good bands in a run."
  [band-analyses]
  (let [analyses (:analyses band-analyses)]
    (when (seq analyses)
      (let [patterns (map #(get-in % [:pattern :type]) analyses)
            pattern-freqs (frequencies patterns)
            neighborhoods (map :neighborhood analyses)
            left-types (frequencies (map :left-type neighborhoods))
            right-types (frequencies (map :right-type neighborhoods))
            correlations (keep :layer-correlation analyses)
            corr-types (frequencies (map :correlation-type correlations))]
        {:pattern-distribution pattern-freqs
         :left-neighbor-types left-types
         :right-neighbor-types right-types
         :layer-correlation-types corr-types
         ;; Key insight: what's the typical context for a good band?
         :typical-context {:pattern (first (apply max-key val pattern-freqs))
                           :left-neighbor (first (apply max-key val left-types))
                           :right-neighbor (first (apply max-key val right-types))}}))))

(defn print-summary [summary moderate-count]
  (println (format "  Moderate columns: %d" moderate-count))
  (println "  Pattern types:" (:pattern-distribution summary))
  (println "  Left neighbors:" (:left-neighbor-types summary))
  (println "  Right neighbors:" (:right-neighbor-types summary))
  (when (:layer-correlation-types summary)
    (println "  Layer correlation:" (:layer-correlation-types summary)))
  (println "  Typical good band context:" (:typical-context summary)))

(defn -main [& args]
  (let [runs-dir (first args)]
    (if (nil? runs-dir)
      (println "Usage: bb scripts/good_band_analysis.clj <runs-dir>")
      (let [files (->> (io/file runs-dir)
                       (.listFiles)
                       (filter #(and (.endsWith (.getName %) ".edn")
                                     (not (.contains (.getName %) "classification"))))
                       (sort-by #(.getName %)))]
        (doseq [f files]
          (println (str "=== " (.getName f) " ==="))
          (let [run (edn/read-string {:readers {'object (fn [_] nil)}} (slurp f))
                band-analysis (analyze-run-good-bands run)
                summary (summarize-good-bands band-analysis)]
            (if summary
              (print-summary summary (:moderate-count band-analysis))
              (println "  No moderate columns found"))
            (println)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
