(ns vertical-band-analysis
  "Analyze spatial structure in MMCA runs by examining vertical bands (columns over time).

   The intuition: EoC (coral reef) has many columns maintaining moderate activity.
   Hot/galaxy has most columns dying off, leaving sparse active regions."
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

(defn shannon-entropy
  "Compute Shannon entropy of a sequence of values."
  [values]
  (let [n (count values)
        freqs (frequencies values)]
    (if (< n 2)
      0.0
      (->> freqs
           vals
           (map #(let [p (/ % n)]
                   (if (pos? p)
                     (* -1.0 p (Math/log p))
                     0.0)))
           (reduce +)))))

(defn column-entropy
  "Entropy of a single column over time."
  [history idx]
  (shannon-entropy (column-values history idx)))

(defn column-change-rate
  "Fraction of generations where column idx changes value."
  [history idx]
  (let [col (column-values history idx)
        pairs (partition 2 1 col)
        changes (count (filter (fn [[a b]] (not= a b)) pairs))]
    (if (< (count col) 2)
      0.0
      (double (/ changes (dec (count col)))))))

(defn column-run-lengths
  "Compute run lengths (consecutive same values) for a column.
   Returns map with :mean-run-length and :max-run-length."
  [history idx]
  (let [col (column-values history idx)
        runs (partition-by identity col)
        lengths (map count runs)]
    {:mean-run-length (if (seq lengths) (double (/ (reduce + lengths) (count lengths))) 0.0)
     :max-run-length (if (seq lengths) (apply max lengths) 0)}))

(defn analyze-column
  "Full analysis of a single column."
  [history idx]
  (let [entropy (column-entropy history idx)
        change-rate (column-change-rate history idx)
        runs (column-run-lengths history idx)]
    {:column idx
     :entropy entropy
     :change-rate change-rate
     :mean-run-length (:mean-run-length runs)
     :max-run-length (:max-run-length runs)
     ;; Classify column
     :classification (cond
                       (< change-rate 0.05) :frozen
                       (> change-rate 0.45) :chaotic
                       (< 0.15 change-rate 0.45) :moderate
                       :else :low-activity)}))

(defn analyze-all-columns
  "Analyze all columns in a history."
  [history]
  (let [len (count (first history))]
    (mapv #(analyze-column history %) (range len))))

(defn band-summary
  "Summarize vertical band characteristics for a run."
  [column-analyses]
  (let [n (count column-analyses)
        by-class (group-by :classification column-analyses)
        frozen-count (count (:frozen by-class))
        chaotic-count (count (:chaotic by-class))
        moderate-count (count (:moderate by-class))
        low-count (count (:low-activity by-class))
        entropies (map :entropy column-analyses)
        change-rates (map :change-rate column-analyses)]
    {:total-columns n
     :frozen-columns frozen-count
     :chaotic-columns chaotic-count
     :moderate-columns moderate-count
     :low-activity-columns low-count
     :frozen-ratio (double (/ frozen-count n))
     :moderate-ratio (double (/ moderate-count n))
     :chaotic-ratio (double (/ chaotic-count n))
     :mean-entropy (/ (reduce + entropies) n)
     :mean-change-rate (/ (reduce + change-rates) n)
     ;; Key metric: ratio of columns with "interesting" activity
     :band-score (double (/ moderate-count n))
     ;; Interpretation
     :interpretation (cond
                       (> frozen-count (* 0.7 n)) :mostly-frozen
                       (> chaotic-count (* 0.5 n)) :mostly-chaotic
                       (> moderate-count (* 0.3 n)) :has-active-bands
                       :else :sparse-activity)}))

(defn find-active-bands
  "Find contiguous regions of moderate-activity columns."
  [column-analyses]
  (let [moderate? #(= :moderate (:classification %))
        indexed (map-indexed vector column-analyses)
        moderate-indices (map first (filter #(moderate? (second %)) indexed))]
    ;; Group into contiguous bands
    (when (seq moderate-indices)
      (reduce (fn [bands idx]
                (if (and (seq bands)
                         (= idx (inc (last (last bands)))))
                  (update bands (dec (count bands)) conj idx)
                  (conj bands [idx])))
              []
              moderate-indices))))

(defn analyze-run
  "Full vertical band analysis for a run."
  [run]
  (let [phe-hist (:phe-history run)
        gen-hist (:gen-history run)
        ;; Use phenotype if available, else genotype
        history (if (seq phe-hist) phe-hist gen-hist)
        history-type (if (seq phe-hist) :phenotype :genotype)]
    (when (seq history)
      (let [col-analyses (analyze-all-columns history)
            summary (band-summary col-analyses)
            bands (find-active-bands col-analyses)]
        (assoc summary
               :history-type history-type
               :generations (count history)
               :active-bands (count bands)
               :band-widths (map count bands)
               :widest-band (if (seq bands) (apply max (map count bands)) 0))))))

(defn analyze-file
  "Analyze a single run EDN file."
  [path]
  (let [run (edn/read-string {:readers {'object (fn [_] nil)}} (slurp path))
        analysis (analyze-run run)]
    (assoc analysis :file (str path))))

(defn print-analysis [analysis]
  (println (format "  Columns: %d total, %d moderate (%.1f%%), %d frozen (%.1f%%), %d chaotic (%.1f%%)"
                   (:total-columns analysis)
                   (:moderate-columns analysis)
                   (* 100 (:moderate-ratio analysis))
                   (:frozen-columns analysis)
                   (* 100 (:frozen-ratio analysis))
                   (:chaotic-columns analysis)
                   (* 100 (:chaotic-ratio analysis))))
  (println (format "  Mean entropy: %.3f, Mean change-rate: %.3f"
                   (:mean-entropy analysis)
                   (:mean-change-rate analysis)))
  (println (format "  Active bands: %d, Widest: %d columns"
                   (:active-bands analysis)
                   (:widest-band analysis)))
  (println (format "  Band score: %.3f, Interpretation: %s"
                   (:band-score analysis)
                   (name (:interpretation analysis)))))

(defn -main [& args]
  (let [runs-dir (first args)]
    (if (nil? runs-dir)
      (println "Usage: bb scripts/vertical_band_analysis.clj <runs-dir>")
      (let [files (->> (io/file runs-dir)
                       (.listFiles)
                       (filter #(and (.endsWith (.getName %) ".edn")
                                     (not (.contains (.getName %) "classification"))))
                       (sort-by #(.getName %)))]
        (doseq [f files]
          (println (str "=== " (.getName f) " ==="))
          (let [analysis (analyze-file f)]
            (if analysis
              (print-analysis analysis)
              (println "  (no history data)"))
            (println)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
