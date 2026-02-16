(ns mission-17a-hex-summary-v0
  (:require [clojure.string :as str]
            [futon5.scripts.output :as out]))

(defn- usage []
  (str/join
   "\n"
   ["Summarize per-seed/variant/window dominance from mission_17a hex CSV."
    ""
    "Usage:"
    "  bb -cp futon5/src futon5/scripts/mission_17a_hex_summary_v0.clj [options]"
    ""
    "Options:"
    "  --in PATH   Input CSV (default /tmp/mission-17a-hex-compare-v0.csv)."
    "  --out PATH  Output CSV (default /tmp/mission-17a-hex-compare-v0-summary.csv)."
    "  --help      Show this message."]))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--in" flag)
          (recur (rest more) (assoc opts :in (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- csv-row [cols]
  (->> cols
       (map (fn [v]
              (cond
                (keyword? v) (name v)
                (string? v) (str "\"" (str/replace v #"\"" "\"\"") "\"")
                (number? v) (format "%.6f" (double v))
                :else (str v))))
       (str/join ",")))

(defn- parse-csv-line [line]
  (->> (str/split line #",")
       (map str/trim)
       (map (fn [s]
              (if (and (>= (count s) 2)
                       (= (first s) \")
                       (= (last s) \"))
                (subs s 1 (dec (count s)))
                s)))))

(defn- parse-long* [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- read-csv [path]
  (let [lines (str/split-lines (slurp path))
        header (parse-csv-line (first lines))
        rows (map parse-csv-line (rest lines))]
    (map (fn [cols]
           (zipmap header cols))
         rows)))

(defn- dominance-summary [classes]
  (let [freqs (frequencies classes)
        sorted (sort-by val > freqs)
        [top-class top-count] (first sorted)
        second-count (or (second (map second sorted)) 0)
        total (double (max 1 (count classes)))
        dominance (/ (- (double top-count) (double second-count)) total)
        stability (/ (double top-count) total)
        transitions (frequencies (map vector classes (rest classes)))]
    {:dominant top-class
     :dominance-margin dominance
     :stability stability
     :hist freqs
     :transitions transitions}))

(defn- group-key [row]
  [(get row "seed") (get row "variant") (get row "window_size")])

(defn -main [& args]
  (let [{:keys [help unknown in out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [in (or in "/tmp/mission-17a-hex-compare-v0.csv")
            out (or out "/tmp/mission-17a-hex-compare-v0-summary.csv")
            rows (filter #(= "window" (get % "summary_scope")) (read-csv in))
            grouped (group-by group-key rows)
            summary-rows (for [[[seed variant window] members] grouped]
                           (let [sorted (sort-by #(or (parse-long* (get % "window_idx")) 0) members)
                                 classes (map #(keyword (get % "class" "neutral")) sorted)
                                 summary (dominance-summary classes)]
                             {:seed seed
                              :variant variant
                              :window_size window
                              :dominant (:dominant summary)
                              :dominance_margin (:dominance-margin summary)
                              :stability (:stability summary)
                              :transitions (pr-str (:transitions summary))
                              :hist (pr-str (:hist summary))}))]
        (out/spit-text!
         out
         (str (csv-row ["seed" "variant" "window_size"
                        "dominant" "dominance_margin" "stability"
                        "transitions" "hist"])
              "\n"
              (str/join
               "\n"
               (map (fn [row]
                      (csv-row [(get row :seed)
                                (get row :variant)
                                (get row :window_size)
                                (name (:dominant row))
                                (get row :dominance_margin)
                                (get row :stability)
                                (get row :transitions)
                                (get row :hist)]))
                    summary-rows)))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
