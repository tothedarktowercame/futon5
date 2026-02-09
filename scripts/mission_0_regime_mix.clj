(ns mission-0-regime-mix
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
   ["Run a mixed-regime batch and score each run with the ensemble."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/mission_0_regime_mix.clj [options]"
    ""
    "Options:"
    "  --regimes PATH     Regime EDN (default futon5/resources/regimes-typology.edn)."
    "  --runs N           Total runs (default 20)."
    "  --seed N           RNG seed (default 4242)."
    "  --length N         Genotype length (default 50)."
    "  --generations N    Generations (default 60)."
    "  --out-dir PATH     Output directory for run EDN (default /tmp/mission-0-regime-mix-<ts>)."
    "  --log PATH         Combined log path (default /tmp/mission-0-regime-mix-<ts>.edn)."
    "  --scores PATH      Scores CSV path (default /tmp/mission-0-regime-mix-<ts>-scores.csv)."
    "  --inputs PATH      HIT inputs list (default /tmp/mission-0-regime-mix-<ts>-inputs.txt)."
    "  --help             Show this message."]))

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

          (= "--regimes" flag)
          (recur (rest more) (assoc opts :regimes (first more)))

          (= "--runs" flag)
          (recur (rest more) (assoc opts :runs (parse-int (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--log" flag)
          (recur (rest more) (assoc opts :log (first more)))

          (= "--scores" flag)
          (recur (rest more) (assoc opts :scores (first more)))

          (= "--inputs" flag)
          (recur (rest more) (assoc opts :inputs (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
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
    {:summary summary
     :hex {:class hex-class :score hex-score}
     :scores {:short (double (or (:composite-score summary) 0.0))
              :envelope (envelope-score summary)
              :triad (triad-score summary hex-score)
              :shift (double (or (:shift/composite shift) 0.0))
              :filament filament-score}}))

(defn- rank-map
  [entries key]
  (let [sorted (sort-by #(get-in % [:scores key]) > entries)]
    (into {} (map-indexed (fn [idx entry] [(:run/id entry) (inc idx)]) sorted))))

(defn- write-csv!
  [path entries ranks]
  (let [header (str/join "," ["run_id" "regime" "seed"
                              "short" "envelope" "triad" "shift" "filament"
                              "rank_short" "rank_envelope" "rank_triad" "rank_shift" "rank_filament"
                              "vote_count"])
        lines (map (fn [entry]
                     (let [scores (:scores entry)
                           rid (:run/id entry)
                           rshort (get-in ranks [:short rid])
                           renv (get-in ranks [:envelope rid])
                           rtriad (get-in ranks [:triad rid])
                           rshift (get-in ranks [:shift rid])
                           rfil (get-in ranks [:filament rid])
                           vote-count (count (filter #(= 1 %) [rshort renv rtriad rshift rfil]))]
                       (str/join "," [(str rid)
                                      (get-in entry [:regime :name])
                                      (str (:seed entry))
                                      (format "%.3f" (double (or (:short scores) 0.0)))
                                      (format "%.3f" (double (or (:envelope scores) 0.0)))
                                      (format "%.3f" (double (or (:triad scores) 0.0)))
                                      (format "%.3f" (double (or (:shift scores) 0.0)))
                                      (format "%.3f" (double (or (:filament scores) 0.0)))
                                      (str rshort)
                                      (str renv)
                                      (str rtriad)
                                      (str rshift)
                                      (str rfil)
                                      (str vote-count)])))
                   entries)]
    (out/spit-text! path (str header "\n" (str/join "\n" lines) "\n"))))

(defn -main [& args]
  (let [{:keys [help unknown regimes runs seed length generations out-dir log scores inputs]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [ts (System/currentTimeMillis)
            regimes-path (or regimes "futon5/resources/regimes-typology.edn")
            regimes (get (edn/read-string (slurp regimes-path)) :regimes)
            runs (int (or runs 20))
            seed (long (or seed 4242))
            length (int (or length 50))
            generations (int (or generations 60))
            out-dir (or out-dir (format "/tmp/mission-0-regime-mix-%d" ts))
            log (or log (format "/tmp/mission-0-regime-mix-%d.edn" ts))
            scores (or scores (format "/tmp/mission-0-regime-mix-%d-scores.csv" ts))
            inputs (or inputs (format "/tmp/mission-0-regime-mix-%d-inputs.txt" ts))
            rng (java.util.Random. seed)]
        (out/warn-overwrite-dir! out-dir)
        (out/warn-append-file! log)
        (.mkdirs (io/file out-dir))
        (let [entries
              (mapv
               (fn [idx]
                 (let [regime (nth regimes (mod idx (count regimes)))
                       run-seed (rng-int rng Integer/MAX_VALUE)
                       grng (java.util.Random. run-seed)
                       base-genotype (rng-sigil-string grng length)
                       base-phenotype (rng-phenotype-string grng length)
                       genotype (or (:genotype regime) base-genotype)
                       phenotype (or (:phenotype regime) base-phenotype)
                       generations (int (or (:generations regime) generations))
                       exo (exotype/resolve-exotype (:exotype regime))
                       run (mmca/run-mmca {:genotype genotype
                                           :phenotype phenotype
                                           :generations generations
                                           :kernel :mutating-template
                                           :operators []
                                           :exotype exo
                                           :seed run-seed})
                       scored (score-run run)
                       run-id (inc idx)
                       base (format "regime-mix-%02d-%s-seed-%d" run-id (:name regime) run-seed)
                       path (str (io/file out-dir (str base ".edn")))]
                   (out/warn-overwrite-file! path)
                   (spit path (pr-str (assoc run
                                             :hit/meta {:label base
                                                        :seed run-seed
                                                        :regime (:name regime)
                                                        :length (count (str genotype))
                                                        :generations generations
                                                        :exotype (:exotype regime)})))
                   (let [entry {:schema/version 1
                                :event :run
                                :run/id run-id
                                :seed run-seed
                                :regime (select-keys regime [:name :label])
                                :length (count (str genotype))
                                :generations generations
                                :exotype (:exotype regime)
                                :scores (:scores scored)
                                :summary (:summary scored)
                                :hex (:hex scored)
                                :run/path path}]
                     (spit log (str (pr-str entry) "\n") :append true)
                     entry)))
               (range runs))
              ranks {:short (rank-map entries :short)
                     :envelope (rank-map entries :envelope)
                     :triad (rank-map entries :triad)
                     :shift (rank-map entries :shift)
                     :filament (rank-map entries :filament)}]
          (write-csv! scores entries ranks)
          (out/spit-text! inputs (str/join "\n" (map :run/path entries)))
          (println "Log:" (out/abs-path log))
          (println "Scores:" (out/abs-path scores))
          (println "Inputs:" (out/abs-path inputs))
          (println "Runs:" (out/abs-path out-dir)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
