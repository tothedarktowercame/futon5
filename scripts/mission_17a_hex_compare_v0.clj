(ns mission-17a-hex-compare-v0
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as mmca-metrics]
            [futon5.mmca.runtime :as mmca]))

(defn- usage []
  (str/join
   "\n"
   ["Windowed hex classifier using terminal metrics (pressure/selectivity/structure)."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/mission_17a_hex_compare_v0.clj [options]"
    ""
    "Options:"
    "  --log PATH       Input org log with seed table (required)."
    "  --seed-col N     1-based column index for seed (default 2)."
    "  --windows CSV    Window sizes (default 10,30,60)."
    "  --out PATH       Output CSV (default /tmp/mission-17a-hex-compare-v0.csv)."
    "  --help           Show this message."]))

(defn- parse-long* [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-int* [s]
  (try (Integer/parseInt s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--log" flag)
          (recur (rest more) (assoc opts :log (first more)))

          (= "--seed-col" flag)
          (recur (rest more) (assoc opts :seed-col (parse-int* (first more))))

          (= "--windows" flag)
          (recur (rest more) (assoc opts :windows (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- split-csv [s]
  (when (seq s)
    (->> (str/split s #",")
         (map str/trim)
         (remove str/blank?))))

(defn- parse-windows [windows]
  (let [items (or (split-csv windows) ["10" "30" "60"])]
    (vec (keep (fn [s]
                 (let [n (parse-int* s)]
                   (when (and n (pos? n)) n)))
               items))))

(defn- read-seeds-from-org [path seed-col]
  (let [idx (dec (max 1 (int seed-col)))]
    (->> (slurp path)
         str/split-lines
         (filter #(str/starts-with? (str/trim %) "|"))
         (map (fn [line]
                (->> (str/split line #"\|")
                     (map str/trim)
                     (remove str/blank?)
                     (vec))))
         (keep (fn [cells]
                 (when (< idx (count cells))
                   (parse-long* (nth cells idx)))))
         vec)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- rng-phenotype-string [^java.util.Random rng length]
  (apply str (repeatedly length #(rng-int rng 2))))

(defn- run-pair [seed]
  (let [baseline-cfg (:config (edn/read-string (slurp "futon5/resources/mission-17a-refine-baseline.edn")))
        exotic-cfg (:config (edn/read-string (slurp "futon5/resources/mission-17a-refine-exotic.edn")))
        gen-len (count (:genotype baseline-cfg))
        phe-len (count (:phenotype baseline-cfg))
        grng (java.util.Random. (long seed))
        genotype (rng-sigil-string grng gen-len)
        phenotype (rng-phenotype-string grng phe-len)
        baseline (mmca/run-mmca (assoc baseline-cfg
                                       :genotype genotype
                                       :phenotype phenotype
                                       :seed seed))
        exotic (mmca/run-mmca (assoc exotic-cfg
                                     :genotype genotype
                                     :phenotype phenotype
                                     :seed seed
                                     :exotype (exotype/resolve-exotype (:exotype exotic-cfg))))]
    {:baseline baseline
     :exotic exotic}))

(defn- clamp01 [x]
  (cond
    (nil? x) 0.0
    (< x 0.0) 0.0
    (> x 1.0) 1.0
    :else (double x)))

(defn- mean [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn- window-slices [xs n]
  (when (and (seq xs) (pos? n))
    (loop [idx 0
           out []]
      (if (>= idx (count xs))
        out
        (let [end (min (count xs) (+ idx n))]
          (recur end (conj out (subvec xs idx end))))))))

(defn windowed-metrics
  "Return per-window metrics maps derived from metrics-history/gen-history."
  [{:keys [metrics-history gen-history]} window]
  (let [metric-windows (window-slices (vec metrics-history) window)
        gen-windows (window-slices (vec gen-history) window)]
    (mapv (fn [idx mwin]
            (let [change (mean (keep :change-rate mwin))
                  unique-ratios (keep (fn [{:keys [unique-sigils length]}]
                                        (when (and unique-sigils length (pos? length))
                                          (/ (double unique-sigils) (double length))))
                                      mwin)
                  entropy (mean (keep :entropy mwin))
                  avg-unique (mean unique-ratios)
                  selectivity (when (number? avg-unique) (- 1.0 avg-unique))
                  autocorr (mmca-metrics/autocorr-metrics (nth gen-windows idx []))
                  structure (:temporal-autocorr autocorr)]
              {:window-idx idx
               :pressure (clamp01 change)
               :selectivity (clamp01 selectivity)
               :structure (clamp01 structure)
               :entropy (clamp01 entropy)}))
          (range (count metric-windows))
          metric-windows)))

(defn features->class
  "Classify a feature map into a hex class using pressure/selectivity/structure."
  [{:keys [pressure selectivity structure]}]
  (let [p (double (or pressure 0.0))
        s (double (or selectivity 0.0))
        u (double (or structure 0.0))]
    (cond
      (and (>= p 0.7) (>= s 0.6)) :qian
      (and (<= p 0.25) (<= s 0.4)) :kun
      (and (<= 0.3 p 0.6) (>= u 0.6)) :tai
      (and (<= 0.3 p 0.6) (<= u 0.3)) :pi
      :else :neutral)))

(defn classes->summary
  "Summarize classes with histogram, dominance, and transitions."
  [classes]
  (let [freqs (frequencies classes)
        sorted (sort-by val > freqs)
        [top-class top-count] (first sorted)
        second-count (or (second (map second sorted)) 0)
        total (double (max 1 (count classes)))
        dominance (/ (- (double top-count) (double second-count)) total)
        stability (/ (double top-count) total)
        transitions (frequencies (map vector classes (rest classes)))]
    {:hist freqs
     :dominant top-class
     :dominance-margin dominance
     :stability stability
     :transitions transitions}))

(defn- csv-row [cols]
  (->> cols
       (map (fn [v]
              (cond
                (keyword? v) (name v)
                (string? v) (str "\"" (str/replace v #"\"" "\"\"") "\"")
                (number? v) (format "%.6f" (double v))
                :else (str v))))
       (str/join ",")))

(defn- run-variant [seed variant run window-sizes]
  (mapcat (fn [window]
            (let [wm (windowed-metrics run window)
                  classes (mapv features->class wm)
                  summary (classes->summary classes)
                  window-rows (map (fn [m c]
                                     {:seed seed
                                      :variant variant
                                      :summary-scope :window
                                      :window-size window
                                      :window-idx (:window-idx m)
                                      :pressure (:pressure m)
                                      :selectivity (:selectivity m)
                                      :structure (:structure m)
                                      :entropy (:entropy m)
                                      :class c})
                                   wm
                                   classes)
                  summary-row {:seed seed
                               :variant variant
                               :summary-scope :summary
                               :window-size window
                               :window-idx ""
                               :pressure ""
                               :selectivity ""
                               :structure ""
                               :entropy ""
                               :class (:dominant summary)
                               :dominant (:dominant summary)
                               :dominance-margin (:dominance-margin summary)
                               :stability (:stability summary)
                               :transitions (pr-str (:transitions summary))
                               :hist (pr-str (:hist summary))}]
              (concat window-rows [summary-row])))
          window-sizes))

(defn -main [& args]
  (let [{:keys [help unknown log out seed-col windows]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (str/blank? log) (do (println "Missing --log") (println) (println (usage)))
      :else
      (let [seed-col (or seed-col 2)
            window-sizes (parse-windows windows)
            out (or out "/tmp/mission-17a-hex-compare-v0.csv")
            seeds (read-seeds-from-org log seed-col)
            rows (mapcat (fn [seed]
                           (let [{:keys [baseline exotic]} (run-pair seed)]
                             (concat (run-variant seed :baseline baseline window-sizes)
                                     (run-variant seed :exotic exotic window-sizes))))
                         seeds)
            header ["seed" "variant" "summary_scope" "window_size" "window_idx"
                    "pressure" "selectivity" "structure" "entropy" "class"
                    "dominant" "dominance_margin" "stability" "transitions" "hist"]]
        (spit out
              (str (csv-row header)
                   "\n"
                   (str/join
                    "\n"
                    (map (fn [row]
                           (csv-row [(get row :seed)
                                     (name (:variant row))
                                     (name (:summary-scope row))
                                     (get row :window-size)
                                     (get row :window-idx)
                                     (get row :pressure "")
                                     (get row :selectivity "")
                                     (get row :structure "")
                                     (get row :entropy "")
                                     (get row :class "")
                                     (get row :dominant "")
                                     (get row :dominance-margin "")
                                     (get row :stability "")
                                     (get row :transitions "")
                                     (get row :hist "")]))
                         rows))))
        (println "Wrote" out)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
