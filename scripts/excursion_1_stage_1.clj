(ns excursion-1-stage-1
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.hexagram.metrics :as hex-metrics]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.filament :as filament]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.register-shift :as register-shift]
            [futon5.mmca.runtime :as mmca]
            [futon5.scripts.output :as out]))

(defn- usage []
  (str/join
   "\n"
   ["Excursion 1 Stage 1: Tai anchor batch"
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/excursion_1_stage_1.clj [options]"
    ""
    "Options:"
    "  --seeds PATH       EDN vector of seeds (required)."
    "  --arms PATH        EDN map of arms (required)."
    "  --length N         Genotype/phenotype length (default 80)."
    "  --generations N    Generations (default 100)."
    "  --out-dir PATH     Output directory (default /tmp/excursion-1-stage-1)."
    "  --scores PATH      Scores CSV (default <out-dir>/scores.csv)."
    "  --inputs PATH      Optional inputs list for HIT (default <out-dir>/inputs.txt)."
    "  --help             Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag) (recur more (assoc opts :help true))
          (= "--seeds" flag) (recur (rest more) (assoc opts :seeds (first more)))
          (= "--arms" flag) (recur (rest more) (assoc opts :arms (first more)))
          (= "--length" flag) (recur (rest more) (assoc opts :length (parse-int (first more))))
          (= "--generations" flag) (recur (rest more) (assoc opts :generations (parse-int (first more))))
          (= "--out-dir" flag) (recur (rest more) (assoc opts :out-dir (first more)))
          (= "--scores" flag) (recur (rest more) (assoc opts :scores (first more)))
          (= "--inputs" flag) (recur (rest more) (assoc opts :inputs (first more)))
          :else (recur more (assoc opts :unknown flag))))
      opts)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- rng-phenotype-string [^java.util.Random rng length]
  (apply str (repeatedly length #(rng-int rng 2))))

(defn- band-score [x center width]
  (let [x (double (or x 0.0))
        center (double (or center 0.0))
        width (double (or width 1.0))]
    (if (pos? width)
      (max 0.0 (- 1.0 (/ (Math/abs (- x center)) width)))
      0.0)))

(defn- envelope-score [summary]
  (let [ent (band-score (:avg-entropy-n summary) 0.6 0.25)
        chg (band-score (:avg-change summary) 0.2 0.15)]
    (* 100.0 (/ (+ ent chg) 2.0))))

(defn- triad-score [summary hex-score]
  (let [gen-score (double (or (:gen/composite-score summary) (:composite-score summary) 0.0))
        phe-score (double (or (:phe/composite-score summary) (:composite-score summary) 0.0))
        exo-score (double (or hex-score 0.0))]
    (/ (+ gen-score phe-score exo-score) 3.0)))

(defn- phe->grid [s]
  [(mapv (fn [ch] (if (= ch \1) 1 0)) (seq (or s "")))])

(defn- phe->frames [phe-history]
  (mapv phe->grid phe-history))

(defn- score-run [run]
  (let [summary (metrics/summarize-run run)
        hex-transition (hex-metrics/run->transition-matrix run)
        hex-signature (hex-metrics/transition-matrix->signature hex-transition)
        hex-class (hex-metrics/signature->hexagram-class hex-signature)
        hex-score (* 100.0 (hex-metrics/hexagram-fitness hex-class))
        shift (register-shift/register-shift-summary run)
        filament-score (double (or (:score (filament/analyze-run (phe->frames (:phe-history run)) {})) 0.0))]
    {:short (double (or (:composite-score summary) 0.0))
     :envelope (envelope-score summary)
     :triad (triad-score summary hex-score)
     :shift (:shift/composite shift)
     :filament filament-score
     :hex {:class hex-class :score hex-score}}))

(defn- pick-sigil
  [^java.util.Random rng sigil-family]
  (case sigil-family
    :gong "工"
    :random (:sigil (nth (ca/sigil-entries) (rng-int rng (count (ca/sigil-entries)))))
    "工"))

(defn- sample-range [^java.util.Random rng [lo hi]]
  (+ (double lo) (* (.nextDouble rng) (- (double hi) (double lo)))))

(defn- sample-envelope [^java.util.Random rng {:keys [center width]}]
  (let [u (.nextDouble rng)
        v (.nextDouble rng)
        tri (/ (+ u v) 2.0)
        lo (- (double center) (double width))
        hi (+ (double center) (double width))
        x (+ lo (* tri (- hi lo)))]
    (max 0.0 (min 1.0 x))))

(defn- sample-param [^java.util.Random rng arm k]
  (if-let [env (get-in arm [:envelope k])]
    (sample-envelope rng env)
    (sample-range rng (k arm))))

(defn- build-exotype [sigil update-prob match-threshold]
  (let [base (exotype/lift sigil)
        params (assoc (:params base)
                      :update-prob update-prob
                      :match-threshold match-threshold)]
    (assoc base :params params)))

(defn- run-arm
  [seed arm {:keys [length generations]}]
  (let [rng (java.util.Random. (long seed))
        genotype (rng-sigil-string rng length)
        phenotype (rng-phenotype-string rng length)
        exo-enabled (not= false (:exotype-enabled arm))
        sigil (when exo-enabled
                (pick-sigil rng (:sigil-family arm)))
        update-prob (when exo-enabled
                      (sample-param rng arm :update-prob))
        match-threshold (when exo-enabled
                          (sample-param rng arm :match-threshold))
        exotype (when exo-enabled
                  (build-exotype sigil update-prob match-threshold))
        run (mmca/run-mmca {:genotype genotype
                            :phenotype phenotype
                            :generations generations
                            :kernel :mutating-template
                            :operators []
                            :exotype exotype
                            :seed seed})
        scores (score-run run)]
    {:seed seed
     :sigil sigil
     :update-prob update-prob
     :match-threshold match-threshold
     :scores scores
     :run run}))

(defn- write-csv [path rows]
  (let [header ["seed" "arm" "sigil" "update_prob" "match_threshold"
                "short" "envelope" "triad" "shift" "filament" "hex_score"]
        lines (cons (str/join "," header)
                    (map (fn [{:keys [seed arm sigil update-prob match-threshold scores]}]
                           (format "%d,%s,%s,%.4f,%.4f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f"
                                   (long seed)
                                   (name arm)
                                   (or sigil "")
                                   (double (or update-prob 0.0))
                                   (double (or match-threshold 0.0))
                                   (double (or (:short scores) 0.0))
                                   (double (or (:envelope scores) 0.0))
                                   (double (or (:triad scores) 0.0))
                                   (double (or (:shift scores) 0.0))
                                   (double (or (:filament scores) 0.0))
                                   (double (or (get-in scores [:hex :score]) 0.0))))
                         rows))]
    (out/spit-text! path (str/join "\n" lines))))

(defn -main [& args]
  (let [{:keys [help unknown seeds arms length generations out-dir scores inputs]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (or (nil? seeds) (nil? arms)) (println (usage))
      :else
      (let [length (int (or length 80))
            generations (int (or generations 100))
            seeds (edn/read-string (slurp seeds))
            arms (edn/read-string (slurp arms))
            out-dir (or out-dir "/tmp/excursion-1-stage-1")
            scores (or scores (str (io/file out-dir "scores.csv")))
            inputs (or inputs (str (io/file out-dir "inputs.txt")))]
        (out/warn-overwrite-dir! out-dir)
        (.mkdirs (io/file out-dir))
        (let [rows
              (mapcat
               (fn [seed]
                 (map (fn [[arm-name arm]]
                        (let [result (run-arm seed arm {:length length :generations generations})
                              base (format "excursion-1-stage-1-%s-seed-%d" (name arm-name) (long seed))
                              out-path (str (io/file out-dir (str base ".edn")))]
                          (out/warn-overwrite-file! out-path)
                          (spit out-path (pr-str (:run result)))
                          (assoc result :arm arm-name :path out-path)))
                      arms))
               seeds)]
          (write-csv scores rows)
          (out/spit-text! inputs (str/join "\n" (map :path rows)))
          (println "Runs saved to" (out/abs-path out-dir)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
