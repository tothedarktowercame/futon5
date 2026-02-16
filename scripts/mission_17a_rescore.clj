(ns mission-17a-rescore
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.runtime :as mmca]
            [futon5.mmca.register-shift :as register-shift]
            [futon5.mmca.filament :as filament]
            [futon5.hexagram.metrics :as hex-metrics]
            [futon5.scripts.output :as out]))

(defn- usage []
  (str/join
   "\n"
   ["Rescore Mission 17a runs with an ensemble of metrics."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/mission_17a_rescore.clj --log PATH [options]"
    ""
    "Options:"
    "  --log PATH      Exoevolve log path."
    "  --out PATH      Output EDN path (default /tmp/mission-17a-rescore-<ts>.edn)."
    "  --replay        Re-run MMCA with seeded genotype/phenotype (deterministic, not exact)."
    "  --envelope-center P"
    "  --envelope-width P"
    "  --envelope-change-center P"
    "  --envelope-change-width P"
    "  --envelope-change  Include avg-change in envelope score (default true)."
    "  --help          Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-double [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

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

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          (= "--replay" flag)
          (recur more (assoc opts :replay true))

          (= "--envelope-center" flag)
          (recur (rest more) (assoc opts :envelope-center (parse-double (first more))))

          (= "--envelope-width" flag)
          (recur (rest more) (assoc opts :envelope-width (parse-double (first more))))

          (= "--envelope-change-center" flag)
          (recur (rest more) (assoc opts :envelope-change-center (parse-double (first more))))

          (= "--envelope-change-width" flag)
          (recur (rest more) (assoc opts :envelope-change-width (parse-double (first more))))

          (= "--envelope-change" flag)
          (recur more (assoc opts :envelope-change true))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- read-lines [path]
  (->> (slurp path)
       str/split-lines
       (map str/trim)
       (remove str/blank?)
       (map edn/read-string)
       (filter #(= :run (:event %)))
       vec))

(defn- band-score [x center width]
  (let [x (double (or x 0.0))
        center (double (or center 0.0))
        width (double (or width 1.0))]
    (if (pos? width)
      (max 0.0 (- 1.0 (/ (Math/abs (- x center)) width)))
      0.0)))

(defn- envelope-score [summary opts]
  (let [entropy (band-score (:avg-entropy-n summary)
                            (:entropy-center opts)
                            (:entropy-width opts))
        change (band-score (:avg-change summary)
                           (:change-center opts)
                           (:change-width opts))
        parts (if (:include-change? opts) [entropy change] [entropy])
        avg (if (seq parts) (/ (reduce + 0.0 parts) (double (count parts))) 0.0)]
    (* 100.0 avg)))

(defn- triad-score [summary hex-score]
  (let [gen-score (double (or (:gen/composite-score summary) (:composite-score summary) 0.0))
        phe-score (double (or (:phe/composite-score summary) (:composite-score summary) 0.0))
        exo-score (double (or hex-score 0.0))]
    (/ (+ gen-score phe-score exo-score) 3.0)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- rng-phenotype-string [^java.util.Random rng length]
  (apply str (repeatedly length #(rng-int rng 2))))

(defn- phe->grid [s]
  [(mapv (fn [ch] (if (= ch \1) 1 0)) (seq (or s "")))])

(defn- phe->frames [phe-history]
  (mapv phe->grid phe-history))

(defn- replay-run [entry]
  (let [seed (long (or (:seed entry) 4242))
        length (int (or (:length entry) 20))
        generations (int (or (:generations entry) 20))
        rng (java.util.Random. seed)
        genotype (rng-sigil-string rng length)
        phenotype (rng-phenotype-string rng length)
        exo (exotype/resolve-exotype (:exotype entry))]
    (mmca/run-mmca {:genotype genotype
                    :phenotype phenotype
                    :generations generations
                    :kernel :mutating-template
                    :exotype exo
                    :seed seed})))

(defn- score-entry [entry opts replay?]
  (let [summary (:summary entry)
        short-score (double (or (:composite-score summary) 0.0))
        env-score (envelope-score summary opts)]
    (if replay?
      (let [result (replay-run entry)
            replay-summary (metrics/summarize-run result)
            hex-transition (hex-metrics/run->transition-matrix result)
            hex-signature (hex-metrics/transition-matrix->signature hex-transition)
            hex-class (hex-metrics/signature->hexagram-class hex-signature)
            hex-score (* 100.0 (hex-metrics/hexagram-fitness hex-class))
            triad (triad-score replay-summary hex-score)
            shift (register-shift/register-shift-summary result)
            filament (filament/analyze-run (phe->frames (:phe-history result)) {})
            fil-score (double (or (:score filament) 0.0))]
        {:short short-score
         :envelope env-score
         :triad triad
         :shift (:shift/composite shift)
         :filament fil-score
         :hex {:class hex-class :signature hex-signature :score hex-score}
         :replay-summary replay-summary})
      {:short short-score
       :envelope env-score
       :triad (triad-score summary 0.0)})))

(defn -main [& args]
  (let [{:keys [help unknown log out replay envelope-center envelope-width envelope-change-center
                envelope-change-width envelope-change]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (nil? log) (do (println "Missing --log PATH") (println) (println (usage)))
      :else
      (let [opts {:entropy-center (or envelope-center 0.6)
                  :entropy-width (or envelope-width 0.25)
                  :change-center (or envelope-change-center 0.2)
                  :change-width (or envelope-change-width 0.15)
                  :include-change? (if (contains? #{true false} envelope-change)
                                     envelope-change
                                     true)}
            entries (read-lines log)
            results (mapv (fn [entry]
                            {:run/id (:run/id entry)
                             :seed (:seed entry)
                             :exotype (:exotype entry)
                             :scores (score-entry entry opts replay)
                             :source-summary (:summary entry)})
                          entries)
            out (or out (format "/tmp/mission-17a-rescore-%d.edn" (System/currentTimeMillis)))]
        (out/spit-text! out (pr-str {:log log
                                     :replay replay
                                     :envelope-opts opts
                                     :results results})))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
