(ns mission-17a-hex-label-correlation
  (:require [clojure.string :as str]))

(defn- usage []
  (str/join
   "\n"
   ["Correlate hex window summaries with notebook III labels."
    ""
    "Usage:"
    "  bb -cp futon5/src futon5/scripts/mission_17a_hex_label_correlation.clj [options]"
    ""
    "Options:"
    "  --hex PATH     Input CSV (default /tmp/mission-17a-hex-compare-v0.csv)."
    "  --labels PATH  Notebook III org file (default futon5/resources/exotic-programming-notebook-iii.org)."
    "  --out PATH     Output CSV (default /tmp/mission-17a-hex-compare-v0-label-corr.csv)."
    "  --help         Show this message."]))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--hex" flag)
          (recur (rest more) (assoc opts :hex (first more)))

          (= "--labels" flag)
          (recur (rest more) (assoc opts :labels (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- parse-csv-line [line]
  (->> (str/split line #",")
       (map str/trim)
       (map (fn [s]
              (if (and (>= (count s) 2)
                       (= (first s) \" )
                       (= (last s) \"))
                (subs s 1 (dec (count s)))
                s)))))

(defn- read-csv [path]
  (let [lines (str/split-lines (slurp path))
        header (parse-csv-line (first lines))
        rows (map parse-csv-line (rest lines))]
    (map (fn [cols]
           (zipmap header cols))
         rows)))

(defn- csv-row [cols]
  (->> cols
       (map (fn [v]
              (cond
                (keyword? v) (name v)
                (string? v) (str "\"" (str/replace v #"\"" "\"\"") "\"")
                (number? v) (format "%.6f" (double v))
                :else (str v))))
       (str/join ",")))

(defn- parse-long* [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-double* [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

(defn- normalize-seed [s]
  (or (parse-long* s)
      (when-let [d (parse-double* s)]
        (long d))))

(defn- read-labels [path]
  (let [lines (str/split-lines (slurp path))
        start (->> lines
                   (map-indexed vector)
                   (filter (fn [[_ line]]
                             (str/starts-with? (str/trim line) "| seed | label |")))
                   (map first)
                   first)]
    (when (nil? start)
      (throw (ex-info "Could not find label table" {:path path})))
    (->> (drop (inc start) lines)
         (take-while #(str/starts-with? (str/trim %) "|"))
         (remove #(re-find #"^\|[-+]+\|$" (str/trim %)))
         (map (fn [line]
                (->> (str/split line #"\|")
                     (map str/trim)
                     (remove str/blank?)
                     (vec))))
         (keep (fn [cells]
                 (when (>= (count cells) 2)
                   (let [seed (parse-long* (first cells))
                         label (second cells)]
                     (when seed
                       [seed label])))))
         (into {}))))

(defn- tai-max-streak [classes]
  (loop [xs classes
         current 0
         best 0]
    (if (seq xs)
      (let [head (first xs)
            current (if (= head :tai) (inc current) 0)
            best (max best current)]
        (recur (rest xs) current best))
      best)))

(defn- dominance-margin [classes]
  (let [freqs (frequencies classes)
        sorted (sort-by val > freqs)
        [top-class top-count] (first sorted)
        second-count (or (second (map second sorted)) 0)
        total (double (max 1 (count classes)))]
    {:dominant top-class
     :dominance-margin (/ (- (double top-count) (double second-count)) total)
     :hist freqs}))

(defn- summarize-run [classes]
  (let [total (double (max 1 (count classes)))
        tai-count (count (filter #(= % :tai) classes))
        margin (dominance-margin classes)]
    {:tai-fraction (/ tai-count total)
     :tai-max-streak (tai-max-streak classes)
     :dominant (:dominant margin)
     :dominance-margin (:dominance-margin margin)
     :hist (:hist margin)}))

(defn- group-key [row]
  [(or (some-> (get row "seed") normalize-seed str) (get row "seed"))
   (get row "variant")
   (get row "window_size")])

(defn -main [& args]
  (let [{:keys [help unknown hex labels out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [hex (or hex "/tmp/mission-17a-hex-compare-v0.csv")
            labels-path (or labels "futon5/resources/exotic-programming-notebook-iii.org")
            out (or out "/tmp/mission-17a-hex-compare-v0-label-corr.csv")
            label-map (read-labels labels-path)
            rows (filter #(= "window" (get % "summary_scope")) (read-csv hex))
            grouped (group-by group-key rows)
            per-run (for [[[seed variant window] members] grouped]
                      (let [sorted (sort-by #(or (parse-long* (get % "window_idx")) 0) members)
                            classes (map #(keyword (get % "class" "neutral")) sorted)
                            summary (summarize-run classes)
                            seed-num (normalize-seed seed)
                            label (get label-map seed-num "unknown")]
                        {:summary_scope "run"
                         :seed seed
                         :label label
                         :variant variant
                         :window_size window
                         :tai_fraction (:tai-fraction summary)
                         :tai_max_streak (:tai-max-streak summary)
                         :dominant (:dominant summary)
                         :dominance_margin (:dominance-margin summary)
                         :hist (pr-str (:hist summary))}))
            group-rows (for [[[label variant window] members]
                             (group-by (fn [row]
                                         [(get row :label) (get row :variant) (get row :window_size)])
                                       per-run)]
                         (let [tai-frac (map :tai_fraction members)
                               tai-streak (map :tai_max_streak members)
                               dom-margin (map :dominance_margin members)
                               avg (fn [xs] (if (seq xs) (/ (reduce + 0.0 xs) (double (count xs))) 0.0))]
                           {:summary_scope "group"
                            :seed ""
                            :label label
                            :variant variant
                            :window_size window
                            :tai_fraction (avg tai-frac)
                            :tai_max_streak (avg tai-streak)
                            :dominant ""
                            :dominance_margin (avg dom-margin)
                            :hist ""}))
            header ["summary_scope" "seed" "label" "variant" "window_size"
                    "tai_fraction" "tai_max_streak" "dominant" "dominance_margin" "hist"]
            rows-out (concat per-run group-rows)]
        (spit out
              (str (csv-row header)
                   "\n"
                   (str/join
                    "\n"
                    (map (fn [row]
                           (csv-row [(get row :summary_scope)
                                     (get row :seed)
                                     (get row :label)
                                     (get row :variant)
                                     (get row :window_size)
                                     (get row :tai_fraction)
                                     (get row :tai_max_streak)
                                     (let [d (:dominant row)]
                                       (if (keyword? d) (name d) d))
                                     (get row :dominance_margin)
                                     (get row :hist)]))
                         rows-out))))
        (println "Wrote" out)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
