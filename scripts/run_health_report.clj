(ns run-health-report
  "Universal run health classifier.

   Classifies runs into:
   - hot: Chaotic (>60% change) - galaxy-like
   - barcode: Collapsed stripes (>70% frozen) - stripe-like
   - eoc: Edge of Chaos candidate - coral-reef-like
   - cooling/frozen/collapsed: Various collapse modes

   Usage:
     bb -cp src:resources scripts/run_health_report.clj --runs-dir /path/to/runs
     bb -cp src:resources scripts/run_health_report.clj --run /path/to/run.edn"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.mmca.run-health :as health]))

(defn- usage []
  (str/join
   "\n"
   ["Universal Run Health Classifier"
    ""
    "Usage:"
    "  bb -cp src:resources scripts/run_health_report.clj [options]"
    ""
    "Options:"
    "  --runs-dir PATH    Directory containing run EDN files"
    "  --run PATH         Single run EDN file"
    "  --csv PATH         Output CSV report"
    "  --markdown PATH    Output Markdown report"
    "  --edn PATH         Output EDN with full metrics"
    "  --summary          Print summary only (default: print all)"
    "  --help             Show this message"
    ""
    "Classifications:"
    "  hot         - Chaotic (>60% change rate) - galaxy-like"
    "  barcode     - Frozen columns (>70% frozen) - stripe-like"
    "  cooling     - Trending toward barcode (50-70% frozen)"
    "  frozen      - Nearly static (<5% change)"
    "  eoc         - Edge of Chaos (15-45% change, <50% frozen)"
    "  collapsed-* - All white or all black"
    "  periodic    - Row periodicity detected"
    "  unknown     - Doesn't fit categories"]))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag) (recur nil (assoc opts :help true))
          (= "--runs-dir" flag) (recur (rest more) (assoc opts :runs-dir (first more)))
          (= "--run" flag) (recur (rest more) (assoc opts :run (first more)))
          (= "--csv" flag) (recur (rest more) (assoc opts :csv (first more)))
          (= "--markdown" flag) (recur (rest more) (assoc opts :markdown (first more)))
          (= "--edn" flag) (recur (rest more) (assoc opts :edn (first more)))
          (= "--summary" flag) (recur more (assoc opts :summary-only true))
          :else (recur more (assoc opts :unknown flag))))
      opts)))

(defn- edn-files [dir]
  (->> (.listFiles (io/file dir))
       (filter #(.isFile %))
       (filter #(and (str/ends-with? (.getName %) ".edn")
                     (not (str/includes? (.getName %) "classification"))))
       (sort-by #(.getName %))))

(defn- read-run [path]
  (try
    (edn/read-string {:readers {'object (fn [_] nil)}} (slurp path))
    (catch Exception e
      (println "Warning: Could not read" path ":" (.getMessage e))
      nil)))

(defn- analyze-file [f]
  (let [run (read-run f)]
    (when run
      (let [h (health/assess-health run)]
        (when h
          (assoc h
                 :file (.getName f)
                 :path (.getPath f)
                 :seed (:seed run)
                 :label (or (:label run) (:id run))))))))

(defn- summarize [results]
  (let [counts (frequencies (map :classification results))
        total (count results)]
    {:total total
     :hot (get counts :hot 0)
     :barcode (get counts :barcode 0)
     :cooling (get counts :cooling 0)
     :frozen (get counts :frozen 0)
     :eoc (get counts :eoc 0)
     :periodic (get counts :periodic 0)
     :collapsed-white (get counts :collapsed-white 0)
     :collapsed-black (get counts :collapsed-black 0)
     :unknown (get counts :unknown 0)}))

(defn- print-table [results]
  (println)
  (println (format "%-40s %-16s %-8s %-8s %-8s %s"
                   "File" "Status" "Change%" "Frozen%" "Width" "Detail"))
  (println (str/join "" (repeat 110 "-")))
  (doseq [{:keys [file classification detail metrics]} results]
    (let [change-pct (* 100 (or (get-in metrics [:change-rate :final-change-rate]) 0))
          frozen-pct (* 100 (or (get-in metrics [:frozen-columns :frozen-ratio]) 0))
          width (or (get-in metrics [:frozen-columns :total-columns]) 0)]
      (println (format "%-40s %-16s %6.1f%% %6.1f%% %8d   %s"
                       (or file "?")
                       (name classification)
                       change-pct
                       frozen-pct
                       width
                       detail)))))

(defn- print-summary [summary]
  (println)
  (println "=== Run Health Summary ===")
  (println)
  (println (format "Total runs: %d" (:total summary)))
  (println)
  (println "Classification breakdown:")
  (when (pos? (:hot summary))
    (println (format "  HOT (chaotic):      %3d  (%.1f%%)" (:hot summary) (* 100.0 (/ (:hot summary) (:total summary))))))
  (when (pos? (:eoc summary))
    (println (format "  EoC (candidate):    %3d  (%.1f%%)" (:eoc summary) (* 100.0 (/ (:eoc summary) (:total summary))))))
  (when (pos? (:cooling summary))
    (println (format "  COOLING:            %3d  (%.1f%%)" (:cooling summary) (* 100.0 (/ (:cooling summary) (:total summary))))))
  (when (pos? (:barcode summary))
    (println (format "  BARCODE (stripes):  %3d  (%.1f%%)" (:barcode summary) (* 100.0 (/ (:barcode summary) (:total summary))))))
  (when (pos? (:frozen summary))
    (println (format "  FROZEN:             %3d  (%.1f%%)" (:frozen summary) (* 100.0 (/ (:frozen summary) (:total summary))))))
  (when (pos? (:periodic summary))
    (println (format "  PERIODIC:           %3d  (%.1f%%)" (:periodic summary) (* 100.0 (/ (:periodic summary) (:total summary))))))
  (when (pos? (+ (:collapsed-white summary) (:collapsed-black summary)))
    (println (format "  COLLAPSED:          %3d  (%.1f%%)"
                     (+ (:collapsed-white summary) (:collapsed-black summary))
                     (* 100.0 (/ (+ (:collapsed-white summary) (:collapsed-black summary)) (:total summary))))))
  (when (pos? (:unknown summary))
    (println (format "  UNKNOWN:            %3d  (%.1f%%)" (:unknown summary) (* 100.0 (/ (:unknown summary) (:total summary)))))))

(defn- csv-report [results]
  (let [header "file,seed,label,classification,detail,change_rate_early,change_rate_final,frozen_ratio,white_ratio,width,generations"
        lines (for [{:keys [file seed label classification detail metrics generations width]} results]
                (str/join ","
                          [file
                           (or seed "")
                           (or label "")
                           (name classification)
                           (str "\"" detail "\"")
                           (or (get-in metrics [:change-rate :early-change-rate]) "")
                           (or (get-in metrics [:change-rate :final-change-rate]) "")
                           (or (get-in metrics [:frozen-columns :frozen-ratio]) "")
                           (or (get-in metrics [:color-ratios :white-ratio]) "")
                           (or (get-in metrics [:frozen-columns :total-columns]) width "")
                           (or generations "")]))]
    (str/join "\n" (cons header lines))))

(defn- markdown-report [summary results]
  (let [lines [(str "# Run Health Report")
               ""
               "## Summary"
               ""
               (format "- **Total runs**: %d" (:total summary))
               (format "- **HOT** (chaotic): %d" (:hot summary))
               (format "- **EoC** (candidate): %d" (:eoc summary))
               (format "- **COOLING**: %d" (:cooling summary))
               (format "- **BARCODE** (stripes): %d" (:barcode summary))
               (format "- **FROZEN**: %d" (:frozen summary))
               (format "- **PERIODIC**: %d" (:periodic summary))
               (format "- **COLLAPSED**: %d" (+ (:collapsed-white summary) (:collapsed-black summary)))
               ""
               "## Classification Table"
               ""
               "| File | Status | Change% | Frozen% | Detail |"
               "|------|--------|---------|---------|--------|"]]
    (str/join "\n"
              (concat lines
                      (for [{:keys [file classification detail metrics]} results]
                        (format "| %s | %s | %.1f%% | %.1f%% | %s |"
                                (or file "?")
                                (name classification)
                                (* 100 (or (get-in metrics [:change-rate :final-change-rate]) 0))
                                (* 100 (or (get-in metrics [:frozen-columns :frozen-ratio]) 0))
                                detail))))))

(defn -main [& args]
  (let [{:keys [help unknown runs-dir run csv markdown edn summary-only]} (parse-args args)]
    (cond
      help
      (println (usage))

      unknown
      (do (println "Unknown option:" unknown)
          (println)
          (println (usage)))

      (and (str/blank? runs-dir) (str/blank? run))
      (do (println "Either --runs-dir or --run is required")
          (println)
          (println (usage)))

      :else
      (let [results (if run
                      ;; Single file
                      (let [f (io/file run)
                            r (analyze-file f)]
                        (if r [r] []))
                      ;; Directory
                      (->> (edn-files runs-dir)
                           (map analyze-file)
                           (filter some?)
                           vec))
            summary (summarize results)]

        ;; Console output
        (if summary-only
          (print-summary summary)
          (do
            (print-table results)
            (print-summary summary)))

        ;; File outputs
        (when csv
          (spit csv (csv-report results))
          (println)
          (println "Wrote CSV:" csv))

        (when markdown
          (spit markdown (markdown-report summary results))
          (println "Wrote Markdown:" markdown))

        (when edn
          (spit edn (pr-str {:summary summary :results results}))
          (println "Wrote EDN:" edn))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
