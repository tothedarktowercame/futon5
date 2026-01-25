(ns mmca-classify-runs
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]))

(def ^:private default-white-threshold 0.85)
(def ^:private default-black-threshold 0.85)
(def ^:private default-genotype-bit-mode :majority)
(def ^:private default-hot-entropy-threshold 0.9)
(def ^:private default-hot-change-threshold 0.9)
(def ^:private default-burst-run-threshold 6)
(def ^:private default-burst-share-threshold 0.1)

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
    "  --hot-entropy X        Hotness entropy-n threshold (default 0.9)."
    "  --hot-change X         Hotness avg-change threshold (default 0.9)."
    "  --burst-run X          Long-run length needed to count as bursty (default 6)."
    "  --burst-share X        Share of columns with long runs to qualify as bursty (default 0.1)."
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

          (= "--hot-entropy" flag)
          (recur (rest more) (assoc opts :hot-entropy-threshold (parse-double* (first more))))

          (= "--hot-change" flag)
          (recur (rest more) (assoc opts :hot-change-threshold (parse-double* (first more))))

          (= "--burst-run" flag)
          (recur (rest more) (assoc opts :burst-run-threshold (parse-double* (first more))))

          (= "--burst-share" flag)
          (recur (rest more) (assoc opts :burst-share-threshold (parse-double* (first more))))

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

(defn- max-run-length
  [rows idx]
  (let [rows (vec rows)
        total (count rows)]
    (loop [i 0
           prev nil
           run 0
           max-run 0]
      (if (< i total)
        (let [row ^String (nth rows i)
              ch (.charAt row idx)
              same? (= ch prev)
              run' (if same? (inc run) 1)
              max-run' (if same? max-run (max max-run run))]
          (recur (inc i) ch run' max-run'))
        (max max-run run)))))

(defn- burstiness
  [rows opts]
  (let [rows (vec rows)]
    (if (seq rows)
      (let [width (count (first rows))
            run-threshold (long (or (:burst-run-threshold opts) default-burst-run-threshold))
            share-threshold (double (or (:burst-share-threshold opts) default-burst-share-threshold))
            max-runs (mapv #(max-run-length rows %) (range width))
            long-count (count (filter #(>= % run-threshold) max-runs))
            share (if (pos? width) (/ (double long-count) (double width)) 0.0)]
        {:bursty? (>= share share-threshold)
         :long-run-share share
         :long-run-threshold run-threshold
         :long-run-count long-count
         :width width})
      {:bursty? false
       :long-run-share nil
       :long-run-threshold (long (or (:burst-run-threshold opts) default-burst-run-threshold))
       :long-run-count 0
       :width 0})))

(defn- hotness
  [summary rows opts]
  (let [entropy (:avg-entropy-n summary)
        change (:avg-change summary)
        hot-entropy (double (or (:hot-entropy-threshold opts) default-hot-entropy-threshold))
        hot-change (double (or (:hot-change-threshold opts) default-hot-change-threshold))]
    (if (and (number? entropy)
             (number? change)
             (>= (double entropy) hot-entropy)
             (>= (double change) hot-change))
      (let [{:keys [bursty?] :as burst} (burstiness rows opts)
            hot? (and (seq rows) (not bursty?))]
        (merge burst
               {:hot? hot?
                :entropy entropy
                :change change
                :hot-reason (when hot? :high-entropy-change-nonbursty)}))
      {:hot? false
       :entropy entropy
       :change change})))

(defn- classify-run
  [run opts]
  (let [{:keys [white-threshold black-threshold genotype-bit-mode]} opts
        white-threshold (double (or white-threshold default-white-threshold))
        black-threshold (double (or black-threshold default-black-threshold))
        genotype-bit-mode (or genotype-bit-mode default-genotype-bit-mode)
        {:keys [rows source]} (row-history run genotype-bit-mode)
        summary (:summary run)
        last-row (when (seq rows) (peek rows))
        white (ratio last-row \1)
        black (ratio last-row \0)
        frozen (boolean (and rows (frozen? rows (:generations run))))
        hot (hotness summary rows opts)
        status (cond
                 (nil? last-row) :unknown
                 (and white (> white white-threshold)) :collapsed-white
                 (and black (> black black-threshold)) :collapsed-black
                 frozen :frozen
                 (:hot? hot) :hot
                 :else :candidate)]
    {:status status
     :seed (:seed run)
     :length (when last-row (count last-row))
     :white-ratio white
     :black-ratio black
     :frozen? frozen
     :row-source source
     :hot? (:hot? hot)
     :hot-reason (:hot-reason hot)
     :hot-entropy (:entropy hot)
     :hot-change (:change hot)
     :long-run-share (:long-run-share hot)
     :long-run-threshold (:long-run-threshold hot)}))

(defn- summarize
  [rows]
  (let [counts (frequencies (map :status rows))]
    {:total (count rows)
     :collapsed-white (get counts :collapsed-white 0)
     :collapsed-black (get counts :collapsed-black 0)
     :frozen (get counts :frozen 0)
     :hot (get counts :hot 0)
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
               (format "- Hot: %d" (:hot summary))
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
                     :opts (select-keys opts
                                        [:white-threshold :black-threshold :genotype-bit-mode
                                         :hot-entropy-threshold :hot-change-threshold
                                         :burst-run-threshold :burst-share-threshold])}]
        (println (format "Classified %d runs from %s" (:total summary) runs-dir))
        (when out
          (spit out (pr-str payload))
          (println "Wrote" out))
        (when markdown
          (spit markdown (markdown-report summary rows))
          (println "Wrote" markdown))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
