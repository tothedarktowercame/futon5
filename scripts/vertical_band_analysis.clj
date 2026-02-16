(ns vertical-band-analysis
  "Analyze spatial structure in MMCA runs by examining vertical bands (columns over time)."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [futon5.mmca.band-analysis :as band]))

(defn analyze-file
  "Analyze a single run EDN file."
  [path]
  (let [run (edn/read-string {:readers {'object (fn [_] nil)}} (slurp path))
        analysis (band/analyze-run run)]
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
                   (name (:interpretation analysis))))
  (when-let [periodic? (:row-periodic? analysis)]
    (println (format "  Row periodicity: %s"
                     (if periodic?
                       (format "YES (period=%d, strength=%.2f)"
                               (long (or (:row-period analysis) 0))
                               (double (or (:row-period-strength analysis) 0.0)))
                       "no")))))

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
