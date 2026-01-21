(ns mission-17a-hex-quantile-reclassify
  (:require [clojure.string :as str]))

(defn- usage []
  (str/join
   "\n"
   ["Reclassify mission_17a hex CSV using quantile thresholds."
    ""
    "Usage:"
    "  bb -cp futon5/src futon5/scripts/mission_17a_hex_quantile_reclassify.clj [options]"
    ""
    "Options:"
    "  --in PATH           Input CSV (default /tmp/mission-17a-hex-compare-v0.csv)."
    "  --labels PATH       Notebook III labels (optional)."
    "  --out PATH          Output CSV (default /tmp/mission-17a-hex-compare-v0-quantile.csv)."
    "  --low Q             Low quantile (default 0.2)."
    "  --high Q            High quantile (default 0.8)."
    "  --help              Show this message."]))

(defn- parse-double* [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

(defn- parse-long* [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- normalize-seed [s]
  (or (parse-long* s)
      (when-let [d (parse-double* s)]
        (long d))))

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

          (= "--labels" flag)
          (recur (rest more) (assoc opts :labels (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--low" flag)
          (recur (rest more) (assoc opts :low (parse-double* (first more))))

          (= "--high" flag)
          (recur (rest more) (assoc opts :high (parse-double* (first more))))

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

(defn- read-csv [path]
  (let [lines (str/split-lines (slurp path))
        header (parse-csv-line (first lines))
        rows (map parse-csv-line (rest lines))]
    {:header header
     :rows (map (fn [cols] (zipmap header cols)) rows)}))

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
                   (let [seed (normalize-seed (first cells))
                         label (second cells)]
                     (when seed
                       [seed label])))))
         (into {}))))

(defn- quantile [xs q]
  (let [xs (vec (sort xs))
        n (count xs)]
    (when (pos? n)
      (let [idx (int (Math/floor (* (double q) (dec n))))]
        (nth xs idx)))))

(defn- thresholds-for-rows [rows low high]
  (let [pressures (keep #(parse-double* (get % "pressure")) rows)
        selectivities (keep #(parse-double* (get % "selectivity")) rows)
        structures (keep #(parse-double* (get % "structure")) rows)]
    {:p-low (or (quantile pressures low) 0.2)
     :p-high (or (quantile pressures high) 0.8)
     :s-low (or (quantile selectivities low) 0.2)
     :s-high (or (quantile selectivities high) 0.8)
     :u-low (or (quantile structures low) 0.2)
     :u-high (or (quantile structures high) 0.8)}))

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
     :transitions transitions
     :hist freqs}))

(defn- features->class
  [{:keys [pressure selectivity structure]} thresholds]
  (let [p (double (or pressure 0.0))
        s (double (or selectivity 0.0))
        u (double (or structure 0.0))
        {:keys [p-low p-high s-low s-high u-low u-high]} thresholds]
    (cond
      (and (>= p p-high) (>= s s-high)) :qian
      (and (<= p p-low) (<= s s-low)) :kun
      (and (<= p p-high) (>= p p-low) (>= u u-high)) :tai
      (and (<= p p-high) (>= p p-low) (<= u u-low)) :pi
      :else :neutral)))

(defn -main [& args]
  (let [{:keys [help unknown in labels out low high]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [in (or in "/tmp/mission-17a-hex-compare-v0.csv")
            labels-path labels
            out (or out "/tmp/mission-17a-hex-compare-v0-quantile.csv")
            low (double (or low 0.2))
            high (double (or high 0.8))
            {:keys [rows]} (read-csv in)
            label-map (when labels-path (read-labels labels-path))
            window-rows (filter #(= "window" (get % "summary_scope")) rows)
            global-thresholds (thresholds-for-rows window-rows low high)
            thresholds-by-label (when label-map
                                  (into {}
                                        (map (fn [[label group-rows]]
                                               [label (thresholds-for-rows group-rows low high)])
                                             (group-by (fn [row]
                                                         (let [seed (normalize-seed (get row "seed"))]
                                                           (get label-map seed "unknown")))
                                                       window-rows))))
            window-out (map (fn [row]
                              (let [seed (normalize-seed (get row "seed"))
                                    label (if label-map (get label-map seed "unknown") "unknown")
                                    thresholds (or (get thresholds-by-label label) global-thresholds)
                                    clazz (features->class {:pressure (parse-double* (get row "pressure"))
                                                            :selectivity (parse-double* (get row "selectivity"))
                                                            :structure (parse-double* (get row "structure"))}
                                                           thresholds)]
                                (assoc row
                                       "label" label
                                       "class" (name clazz))))
                            window-rows)
            by-group (group-by (fn [row]
                                 [(get row "seed") (get row "variant") (get row "window_size")])
                               window-out)
            summary-out (for [[[seed variant window] members] by-group]
                          (let [sorted (sort-by #(or (parse-long* (get % "window_idx")) 0) members)
                                label (get (first sorted) "label" "unknown")
                                thresholds (or (get thresholds-by-label label) global-thresholds)
                                classes (map #(keyword (get % "class" "neutral"))
                                             (map (fn [row]
                                                    (assoc row "class"
                                                           (name (features->class {:pressure (parse-double* (get row "pressure"))
                                                                                   :selectivity (parse-double* (get row "selectivity"))
                                                                                   :structure (parse-double* (get row "structure"))}
                                                                                  thresholds))))
                                                  sorted))
                                summary (dominance-summary classes)]
                            {"seed" seed
                             "label" label
                             "variant" variant
                             "summary_scope" "summary"
                             "window_size" window
                             "window_idx" ""
                             "pressure" ""
                             "selectivity" ""
                             "structure" ""
                             "entropy" ""
                             "class" (name (:dominant summary))
                             "dominant" (name (:dominant summary))
                             "dominance_margin" (:dominance-margin summary)
                             "stability" (:stability summary)
                             "transitions" (pr-str (:transitions summary))
                             "hist" (pr-str (:hist summary))}))
            header ["seed" "label" "variant" "summary_scope" "window_size" "window_idx"
                    "pressure" "selectivity" "structure" "entropy" "class"
                    "dominant" "dominance_margin" "stability" "transitions" "hist"]
            rows-out (concat window-out summary-out)]
        (spit out
              (str (csv-row header)
                   "\n"
                   (str/join
                    "\n"
                    (map (fn [row]
                           (csv-row [(get row "seed")
                                     (get row "label" "unknown")
                                     (get row "variant")
                                     (get row "summary_scope" "window")
                                     (get row "window_size")
                                     (get row "window_idx")
                                     (get row "pressure" "")
                                     (get row "selectivity" "")
                                     (get row "structure" "")
                                     (get row "entropy" "")
                                     (get row "class" "")
                                     (get row "dominant" "")
                                     (get row "dominance_margin" "")
                                     (get row "stability" "")
                                     (get row "transitions" "")
                                     (get row "hist" "")]))
                         rows-out))))
        (println "Wrote" out)
        (println "Global thresholds:" global-thresholds)
        (when thresholds-by-label
          (println "Label thresholds:" thresholds-by-label))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
