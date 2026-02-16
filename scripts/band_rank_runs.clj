(ns band-rank-runs
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.mmca.band-analysis :as band]))

(defn- usage []
  (str/join
   "\n"
   ["Rank MMCA runs by vertical band structure (moderate-activity columns)."
    ""
    "Usage:"
    "  bb -cp src:resources:scripts scripts/band_rank_runs.clj [options]"
    ""
    "Options:"
    "  --runs-dir PATH   Directory containing run EDN files (required)."
    "  --out PATH        Output EDN summary (optional)."
    "  --markdown PATH   Output Markdown report (optional)."
    "  --limit N         Limit rows in output (optional)."
    "  --help            Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--runs-dir" flag)
          (recur (rest more) (assoc opts :runs-dir (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--markdown" flag)
          (recur (rest more) (assoc opts :markdown (first more)))

          (= "--limit" flag)
          (recur (rest more) (assoc opts :limit (parse-int (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- edn-files [dir]
  (->> (.listFiles (io/file dir))
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".edn"))
       (remove #(str/includes? (.getName %) "classification"))
       (sort-by #(.getName %))))

(defn- analyze-file [path]
  (let [run (edn/read-string {:readers {'object (fn [_] nil)}} (slurp path))
        analysis (band/analyze-run run)]
    (when analysis
      (assoc analysis :file (.getName (io/file path))))))

(defn- summarize [rows]
  (let [counts (frequencies (map :interpretation rows))]
    {:total (count rows)
     :has-active-bands (get counts :has-active-bands 0)
     :mostly-chaotic (get counts :mostly-chaotic 0)
     :mostly-frozen (get counts :mostly-frozen 0)
     :sparse-activity (get counts :sparse-activity 0)}))

(defn- markdown-report [summary rows]
  (let [header ["| file | band-score | moderate% | chaotic% | frozen% | bands | widest | interp |"
                "|------|-----------|-----------|----------|---------|-------|--------|--------|"]
        lines (map (fn [{:keys [file band-score moderate-ratio chaotic-ratio frozen-ratio
                                active-bands widest-band interpretation]}]
                     (format "| %s | %.3f | %.1f | %.1f | %.1f | %d | %d | %s |"
                             file
                             (double (or band-score 0.0))
                             (* 100.0 (double (or moderate-ratio 0.0)))
                             (* 100.0 (double (or chaotic-ratio 0.0)))
                             (* 100.0 (double (or frozen-ratio 0.0)))
                             (long (or active-bands 0))
                             (long (or widest-band 0))
                             (name (or interpretation :unknown))))
                   rows)
        body (concat
              ["## Band Ranking Summary"
               ""
               (format "- Total runs: %d" (:total summary))
               (format "- Has active bands: %d" (:has-active-bands summary))
               (format "- Mostly chaotic: %d" (:mostly-chaotic summary))
               (format "- Mostly frozen: %d" (:mostly-frozen summary))
               (format "- Sparse activity: %d" (:sparse-activity summary))
               ""
               "## Ranked Runs"
               ""]
              header
              lines
              [""])]
    (str/join "\n" body)))

(defn -main [& args]
  (let [{:keys [help unknown runs-dir out markdown limit] :as opts} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      (str/blank? runs-dir) (do (println "--runs-dir is required")
                                (println)
                                (println (usage)))
      :else
      (let [files (edn-files runs-dir)
            rows (->> files
                      (map analyze-file)
                      (remove nil?)
                      (sort-by (comp - :band-score))
                      (vec))
            rows (if (and limit (pos? limit)) (subvec rows 0 (min (count rows) limit)) rows)
            summary (summarize rows)
            payload {:summary summary
                     :rows rows
                     :opts (select-keys opts [:limit])}]
        (println (format "Ranked %d runs from %s" (:total summary) runs-dir))
        (when out
          (spit out (pr-str payload))
          (println "Wrote" out))
        (when markdown
          (spit markdown (markdown-report summary rows))
          (println "Wrote" markdown))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
