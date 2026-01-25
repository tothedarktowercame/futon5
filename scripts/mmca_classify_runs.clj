(ns mmca-classify-runs
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]))

(def ^:private default-white-threshold 0.85)
(def ^:private default-black-threshold 0.85)
(def ^:private default-genotype-bit-mode :majority)

(defn- usage []
  (str/join
   "\n"
   ["Classify MMCA runs as collapsed/frozen/candidate based on AGENTS.md heuristics."
    ""
    "Usage:"
    "  bb -cp src:resources scripts/mmca_classify_runs.clj [options]"
    ""
    "Options:"
    "  --runs-dir PATH        Directory containing run EDN files (required)."
    "  --out PATH             Output EDN summary (optional)."
    "  --markdown PATH        Output Markdown report (optional)."
    "  --white-threshold X    Collapse-white threshold (default 0.85)."
    "  --black-threshold X    Collapse-black threshold (default 0.85)."
    "  --genotype-bit-mode M  Map genotype sigils to bits using :majority, :first, or :parity."
    "  --help                 Show this message."]))

(defn- parse-double* [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

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

          (= "--white-threshold" flag)
          (recur (rest more) (assoc opts :white-threshold (parse-double* (first more))))

          (= "--black-threshold" flag)
          (recur (rest more) (assoc opts :black-threshold (parse-double* (first more))))

          (= "--genotype-bit-mode" flag)
          (recur (rest more) (assoc opts :genotype-bit-mode (some-> (first more) keyword)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- edn-files [dir]
  (->> (.listFiles (io/file dir))
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".edn"))
       (sort-by #(.getName %))))

(defn- sigil->bit
  [sigil mode]
  (let [bits (ca/bits-for (str sigil))
        ones (count (filter #(= \1 %) bits))]
    (case mode
      :first (nth bits 0)
      :parity (if (odd? ones) \1 \0)
      :majority (if (>= ones 4) \1 \0)
      (if (>= ones 4) \1 \0))))

(defn- genotype->bits-row
  [genotype mode]
  (apply str (map #(sigil->bit % mode) (seq (or genotype "")))))

(defn- row-history
  [run mode]
  (cond
    (seq (:phe-history run)) {:rows (:phe-history run) :source :phenotype}
    (seq (:gen-history run)) {:rows (mapv #(genotype->bits-row % mode) (:gen-history run))
                              :source :genotype}
    :else {:rows nil :source :none}))

(defn- ratio
  [row ch]
  (when (and row (pos? (count row)))
    (/ (double (count (filter #(= ch %) row))) (double (count row)))))

(defn- frozen?
  [rows generations]
  (let [cnt (count rows)]
    (when (>= cnt 2)
      (let [gens (long (or generations (dec cnt)))
            n (min 10 (max 2 (quot gens 2)))
            n (min n cnt)]
        (when (>= n 2)
          (apply = (take-last n rows)))))))

(defn- classify-run
  [run opts]
  (let [{:keys [white-threshold black-threshold genotype-bit-mode]} opts
        white-threshold (double (or white-threshold default-white-threshold))
        black-threshold (double (or black-threshold default-black-threshold))
        genotype-bit-mode (or genotype-bit-mode default-genotype-bit-mode)
        {:keys [rows source]} (row-history run genotype-bit-mode)
        last-row (when (seq rows) (peek rows))
        white (ratio last-row \1)
        black (ratio last-row \0)
        frozen (boolean (and rows (frozen? rows (:generations run))))
        status (cond
                 (nil? last-row) :unknown
                 (and white (> white white-threshold)) :collapsed-white
                 (and black (> black black-threshold)) :collapsed-black
                 frozen :frozen
                 :else :candidate)]
    {:status status
     :seed (:seed run)
     :length (when last-row (count last-row))
     :white-ratio white
     :black-ratio black
     :frozen? frozen
     :row-source source}))

(defn- summarize
  [rows]
  (let [counts (frequencies (map :status rows))]
    {:total (count rows)
     :collapsed-white (get counts :collapsed-white 0)
     :collapsed-black (get counts :collapsed-black 0)
     :frozen (get counts :frozen 0)
     :candidate (get counts :candidate 0)
     :unknown (get counts :unknown 0)}))

(defn- markdown-report
  [summary rows]
  (let [header ["| file | seed | status | white | black | source |"
                "|------|------|--------|-------|-------|--------|"]
        lines (map (fn [{:keys [file seed status white-ratio black-ratio row-source]}]
                     (format "| %s | %s | %s | %.3f | %.3f | %s |"
                             file
                             (or seed "")
                             (name status)
                             (double (or white-ratio 0.0))
                             (double (or black-ratio 0.0))
                             (name row-source)))
                   rows)
        body (concat
              ["## Run Classification Summary"
               ""
               (format "- Total runs: %d" (:total summary))
               (format "- Collapsed (white): %d" (:collapsed-white summary))
               (format "- Collapsed (black): %d" (:collapsed-black summary))
               (format "- Frozen: %d" (:frozen summary))
               (format "- Candidates: %d" (:candidate summary))
               (format "- Unknown: %d" (:unknown summary))
               ""
               "## Classification Table"
               ""]
              header
              lines
              [""])]
    (str/join "\n" body)))

(defn -main [& args]
  (let [{:keys [help unknown runs-dir out markdown] :as opts} (parse-args args)]
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
            rows (mapv (fn [f]
                         (let [run (edn/read-string (slurp f))
                               info (classify-run run opts)]
                           (assoc info :file (.getName f))))
                       files)
            summary (summarize rows)
            payload {:summary summary
                     :rows rows
                     :opts (select-keys opts [:white-threshold :black-threshold :genotype-bit-mode])}]
        (println (format "Classified %d runs from %s" (:total summary) runs-dir))
        (when out
          (spit out (pr-str payload))
          (println "Wrote" out))
        (when markdown
          (spit markdown (markdown-report summary rows))
          (println "Wrote" markdown))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
