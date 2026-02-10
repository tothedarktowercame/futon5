(ns hex-feature-scan
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.hexagram.metrics :as hex]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as mmca-metrics]
            [futon5.mmca.runtime :as mmca]
            [futon5.scripts.output :as out]))

(defn- usage []
  (str/join
   "\n"
   ["Scan per-window hex features for genotype vs phenotype histories."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/hex_feature_scan.clj [options]"
    ""
    "Options:"
    "  --seeds CSV          Comma-separated seeds (default 1..20)."
    "  --seeds-file PATH    File with one seed per line."
    "  --windows CSV        Window sizes (default 10,30,60)."
    "  --out-prefix PATH    Output prefix (default /tmp/hex-feature-scan-<ts>)."
    "  --help               Show this message."]))

(defn- parse-long* [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--seeds" flag)
          (recur (rest more) (assoc opts :seeds (first more)))

          (= "--seeds-file" flag)
          (recur (rest more) (assoc opts :seeds-file (first more)))

          (= "--windows" flag)
          (recur (rest more) (assoc opts :windows (first more)))

          (= "--out-prefix" flag)
          (recur (rest more) (assoc opts :out-prefix (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- split-csv [s]
  (when (seq s)
    (->> (str/split s #",")
         (map str/trim)
         (remove str/blank?))))

(defn- parse-seeds [{:keys [seeds seeds-file]}]
  (cond
    (seq seeds)
    (vec (keep parse-long* (split-csv seeds)))

    (seq seeds-file)
    (->> (slurp seeds-file)
         str/split-lines
         (map str/trim)
         (remove str/blank?)
         (keep parse-long*)
         vec)

    :else
    (vec (range 1 21))))

(defn- parse-windows [windows]
  (let [items (or (split-csv windows) ["10" "30" "60"])]
    (vec (keep (fn [s]
                 (let [n (parse-long* s)]
                   (when (and n (pos? n)) (int n))))
               items))))

(defn- window-slices [xs n]
  (when (and (seq xs) (pos? n))
    (loop [idx 0
           out []]
      (if (>= idx (count xs))
        out
        (let [end (min (count xs) (+ idx n))]
          (recur end (conj out (subvec xs idx end))))))))

(defn- mean [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn- variance [xs]
  (let [m (mean xs)]
    (when (and (seq xs) m)
      (mean (map (fn [x] (let [d (- (double x) m)] (* d d))) xs)))))

(defn- stddev [xs]
  (let [v (variance xs)]
    (when v (Math/sqrt v))))

(defn- median [xs]
  (when (seq xs)
    (let [sorted (vec (sort xs))
          n (count sorted)
          mid (quot n 2)]
      (if (odd? n)
        (nth sorted mid)
        (/ (+ (nth sorted (dec mid)) (nth sorted mid)) 2.0)))))

(defn- phenotype-activity [phe]
  (let [chars (seq (or phe ""))
        len (count chars)]
    (if (pos? len)
      (/ (double (count (filter #(= % \1) chars))) len)
      0.0)))

(defn- phenotype-motif-diversity [phe]
  (let [chars (vec (seq (or phe "")))]
    (if (< (count chars) 2)
      0
      (count (set (map (fn [[a b]] (str a b)) (partition 2 1 chars)))))))

(defn- phenotype-window-features [window]
  (let [activity (map phenotype-activity window)
        entropy (map mmca-metrics/shannon-entropy window)
        motifs (map phenotype-motif-diversity window)
        deltas (keep (fn [[a b]] (mmca-metrics/hamming-rate a b))
                     (map vector window (rest window)))
        change (if (seq deltas) deltas [0.0])]
    {:activity-mean (double (or (mean activity) 0.0))
     :entropy-mean (double (or (mean entropy) 0.0))
     :motif-mean (double (or (mean motifs) 0.0))
     :change-mean (double (or (mean change) 0.0))}))

(defn- window-signature [history]
  (let [transition (hex/run->transition-matrix {:gen-history history})
        signature (hex/transition-matrix->signature transition)
        clazz (hex/signature->hexagram-class signature)]
    (assoc signature
           :class clazz
           :fitness (* 100.0 (hex/hexagram-fitness clazz)))))

(defn- class-stats [classes]
  (let [freqs (frequencies classes)
        sorted (sort-by val > freqs)
        [top-class top-count] (first sorted)
        second-count (or (second (map second sorted)) 0)
        total (double (max 1 (count classes)))
        dominance (/ (- (double top-count) (double second-count)) total)
        stability (/ (double top-count) total)]
    {:freqs freqs
     :dominant top-class
     :dominance-margin dominance
     :stability stability}))

(defn- summarize-series [xs]
  {:mean (double (or (mean xs) 0.0))
   :std (double (or (stddev xs) 0.0))
   :min (double (or (when (seq xs) (apply min xs)) 0.0))
   :max (double (or (when (seq xs) (apply max xs)) 0.0))})

(defn- choose-window [windows strategy]
  (when (seq windows)
    (case strategy
      :max-score (apply max-key (fn [w] (double (get w :score 0.0))) windows)
      :median-activity
      (let [median-activity (median (map (comp :activity-mean :features) windows))]
        (apply min-key (fn [w] (Math/abs (- (double (get-in w [:features :activity-mean]))
                                           (double (or median-activity 0.0))))) windows))
      :metastable
      (let [eligible (filter (fn [w] (pos? (double (get-in w [:features :change-mean] 0.0)))) windows)
            pool (if (seq eligible) eligible windows)]
        (apply max-key (fn [w] (double (get-in w [:features :entropy-mean] 0.0))) pool))
      (first windows))))

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

(defn- csv-row [cols]
  (->> cols
       (map (fn [v]
              (cond
                (keyword? v) (name v)
                (string? v) (str "\"" (str/replace v #"\"" "\"\"") "\"")
                (number? v) (format "%.6f" (double v))
                :else (str v))))
       (str/join ",")))

(defn- collect-run-data [seed run variant window-sizes]
  (reduce (fn [acc window]
            (let [gen-windows (window-slices (vec (:gen-history run)) window)
                  phe-windows (window-slices (vec (:phe-history run)) window)
                  gen-series (map-indexed
                              (fn [idx w]
                                (let [sig (window-signature w)]
                                  (merge {:signal :genotype
                                          :window-size window
                                          :window-idx idx}
                                         sig)))
                              gen-windows)
                  phe-series (map-indexed
                              (fn [idx w]
                                (let [sig (window-signature w)
                                      features (phenotype-window-features w)]
                                  {:signal :phenotype
                                   :window-size window
                                   :window-idx idx
                                   :score (:spectral-gap sig)
                                   :signature sig
                                   :features features
                                   :class (:class sig)
                                   :fitness (:fitness sig)}))
                              phe-windows)
                  gen-classes (map :class gen-series)
                  phe-classes (map :class phe-series)
                  gen-stats (class-stats gen-classes)
                  phe-stats (class-stats phe-classes)
                  gen-alpha (map :alpha-estimate gen-series)
                  gen-gap (map :spectral-gap gen-series)
                  gen-rank (map :projection-rank gen-series)
                  phe-alpha (map (comp :alpha-estimate :signature) phe-series)
                  phe-gap (map (comp :spectral-gap :signature) phe-series)
                  phe-rank (map (comp :projection-rank :signature) phe-series)
                  phe-activity (map (comp :activity-mean :features) phe-series)
                  phe-entropy (map (comp :entropy-mean :features) phe-series)
                  phe-change (map (comp :change-mean :features) phe-series)
                  phe-motif (map (comp :motif-mean :features) phe-series)
                  max-score (choose-window phe-series :max-score)
                  median-activity (choose-window phe-series :median-activity)
                  metastable (choose-window phe-series :metastable)
                  selectors [[:max-score max-score]
                             [:median-activity median-activity]
                             [:metastable metastable]]]
              (-> acc
                  (update :window-rows into
                          (concat
                           (map (fn [entry]
                                  {:seed seed
                                   :variant variant
                                   :signal :genotype
                                   :window-size window
                                   :window-idx (:window-idx entry)
                                   :alpha (:alpha-estimate entry)
                                   :gap (:spectral-gap entry)
                                   :rank (:projection-rank entry)
                                   :class (:class entry)
                                   :fitness (:fitness entry)})
                                gen-series)
                           (map (fn [entry]
                                  {:seed seed
                                   :variant variant
                                   :signal :phenotype
                                   :window-size window
                                   :window-idx (:window-idx entry)
                                   :alpha (get-in entry [:signature :alpha-estimate])
                                   :gap (get-in entry [:signature :spectral-gap])
                                   :rank (get-in entry [:signature :projection-rank])
                                   :class (:class entry)
                                   :fitness (:fitness entry)
                                   :activity (get-in entry [:features :activity-mean])
                                   :entropy (get-in entry [:features :entropy-mean])
                                   :change (get-in entry [:features :change-mean])
                                   :motif (get-in entry [:features :motif-mean])})
                                phe-series)))
                  (update :summary-rows conj
                          {:seed seed
                           :variant variant
                           :signal :genotype
                           :window-size window
                           :window-count (count gen-series)
                           :alpha (summarize-series gen-alpha)
                           :gap (summarize-series gen-gap)
                           :rank (summarize-series gen-rank)
                           :class-stats gen-stats})
                  (update :summary-rows conj
                          {:seed seed
                           :variant variant
                           :signal :phenotype
                           :window-size window
                           :window-count (count phe-series)
                           :alpha (summarize-series phe-alpha)
                           :gap (summarize-series phe-gap)
                           :rank (summarize-series phe-rank)
                           :activity (summarize-series phe-activity)
                           :entropy (summarize-series phe-entropy)
                           :change (summarize-series phe-change)
                           :motif (summarize-series phe-motif)
                           :class-stats phe-stats})
                  (update :selection-rows into
                          (keep (fn [[label entry]]
                                  (when entry
                                    {:seed seed
                                     :variant variant
                                     :window-size window
                                     :selector label
                                     :window-idx (:window-idx entry)
                                     :class (:class entry)
                                     :fitness (:fitness entry)
                                     :activity (get-in entry [:features :activity-mean])
                                     :entropy (get-in entry [:features :entropy-mean])
                                     :change (get-in entry [:features :change-mean])}))
                                selectors)))))
          {:window-rows [] :summary-rows [] :selection-rows []}
          window-sizes))

(defn- write-windows [path rows]
  (out/spit-text!
   path
   (str (csv-row ["seed" "variant" "signal" "window_size" "window_idx"
                  "alpha" "gap" "rank" "class" "fitness"
                  "activity" "entropy" "change" "motif"])
        "\n"
        (str/join
         "\n"
         (map (fn [row]
                (csv-row [(get row :seed)
                          (name (:variant row))
                          (name (:signal row))
                          (get row :window-size)
                          (get row :window-idx)
                          (get row :alpha)
                          (get row :gap)
                          (get row :rank)
                          (get row :class)
                          (get row :fitness)
                          (get row :activity "")
                          (get row :entropy "")
                          (get row :change "")
                          (get row :motif "")]))
              rows)))))

(defn- write-summary [path rows]
  (let [stat (fn [m k] (if (map? m) (get m k "") ""))]
    (out/spit-text!
     path
     (str (csv-row ["seed" "variant" "signal" "window_size" "window_count"
                    "alpha_mean" "alpha_std" "alpha_min" "alpha_max"
                    "gap_mean" "gap_std" "gap_min" "gap_max"
                    "rank_mean" "rank_std" "rank_min" "rank_max"
                    "activity_mean" "activity_std" "activity_min" "activity_max"
                    "entropy_mean" "entropy_std" "entropy_min" "entropy_max"
                    "change_mean" "change_std" "change_min" "change_max"
                    "motif_mean" "motif_std" "motif_min" "motif_max"
                    "dominant_class" "dominance_margin" "stability" "class_hist"])
          "\n"
          (str/join
           "\n"
           (map (fn [row]
                  (let [alpha (:alpha row)
                        gap (:gap row)
                        rank (:rank row)
                        activity (:activity row)
                        entropy (:entropy row)
                        change (:change row)
                        motif (:motif row)
                        {:keys [dominant dominance-margin stability freqs]} (:class-stats row)]
                    (csv-row [(get row :seed)
                              (name (:variant row))
                              (name (:signal row))
                              (get row :window-size)
                              (get row :window-count)
                              (stat alpha :mean) (stat alpha :std) (stat alpha :min) (stat alpha :max)
                              (stat gap :mean) (stat gap :std) (stat gap :min) (stat gap :max)
                              (stat rank :mean) (stat rank :std) (stat rank :min) (stat rank :max)
                              (stat activity :mean) (stat activity :std) (stat activity :min) (stat activity :max)
                              (stat entropy :mean) (stat entropy :std) (stat entropy :min) (stat entropy :max)
                              (stat change :mean) (stat change :std) (stat change :min) (stat change :max)
                              (stat motif :mean) (stat motif :std) (stat motif :min) (stat motif :max)
                              dominant dominance-margin stability (pr-str freqs)])))
                rows)))))))

(defn- write-selection [path rows]
  (out/spit-text!
   path
   (str (csv-row ["seed" "variant" "window_size" "selector" "window_idx"
                  "class" "fitness" "activity" "entropy" "change"])
        "\n"
        (str/join
         "\n"
         (map (fn [row]
                (csv-row [(get row :seed)
                          (name (:variant row))
                          (get row :window-size)
                          (name (:selector row))
                          (get row :window-idx)
                          (get row :class)
                          (get row :fitness)
                          (get row :activity)
                          (get row :entropy)
                          (get row :change)]))
              rows)))))

(defn -main [& args]
  (let [{:keys [help unknown out-prefix] :as opts} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [seeds (parse-seeds opts)
            window-sizes (parse-windows (:windows opts))
            out-prefix (or out-prefix (format "/tmp/hex-feature-scan-%d"
                                              (System/currentTimeMillis)))
            results (reduce (fn [acc seed]
                              (let [{:keys [baseline exotic]} (run-pair seed)
                                    base (collect-run-data seed baseline :baseline window-sizes)
                                    exo (collect-run-data seed exotic :exotic window-sizes)]
                                (-> acc
                                    (update :window-rows into (:window-rows base))
                                    (update :window-rows into (:window-rows exo))
                                    (update :summary-rows into (:summary-rows base))
                                    (update :summary-rows into (:summary-rows exo))
                                    (update :selection-rows into (:selection-rows base))
                                    (update :selection-rows into (:selection-rows exo)))))
                            {:window-rows [] :summary-rows [] :selection-rows []}
                            seeds)
            windows-out (str out-prefix "-windows.csv")
            summary-out (str out-prefix "-summary.csv")
            selection-out (str out-prefix "-selection.csv")]
        (write-windows windows-out (:window-rows results))
        (write-summary summary-out (:summary-rows results))
        (write-selection selection-out (:selection-rows results))
        (println "Outputs prefix:" (out/abs-path out-prefix)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
