(ns sweep-pt
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.filament :as filament]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.register-shift :as register-shift]
            [futon5.mmca.runtime :as mmca]
            [futon5.scoring :as scoring]))

(defn- usage []
  (str/join
   "\n"
   ["Sweep update-prob x match-threshold for a fixed exotype (default 工)."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/sweep_pt.clj [options]"
    ""
    "Options:"
    "  --sigil S       Sigil (default 工)."
    "  --step P        Step size (default 0.1)."
    "  --seeds N       Seeds per grid point (default 5)."
    "  --weights PATH  Preference weights EDN (default /tmp/mission-17a-pref-weights.edn)."
    "  --out PATH      Output CSV (default /tmp/mission-17a-sweep-<ts>.csv)."
    "  --help          Show this message."]))

(defn- parse-double [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

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

          (= "--sigil" flag)
          (recur (rest more) (assoc opts :sigil (first more)))

          (= "--step" flag)
          (recur (rest more) (assoc opts :step (parse-double (first more))))

          (= "--seeds" flag)
          (recur (rest more) (assoc opts :seeds (parse-int (first more))))

          (= "--weights" flag)
          (recur (rest more) (assoc opts :weights (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

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

(defn- phe->grid [s]
  [(mapv (fn [ch] (if (= ch \1) 1 0)) (seq (or s "")))])

(defn- phe->frames [phe-history]
  (mapv phe->grid phe-history))

(defn- score-run [run]
  (let [summary (metrics/summarize-run run)
        shift (register-shift/register-shift-summary run)
        filament-score (double (or (:score (filament/analyze-run (phe->frames (:phe-history run)) {})) 0.0))]
    {:summary summary
     :raw {:short (double (or (:composite-score summary) 0.0))
           :envelope (envelope-score summary)
           :triad 0.0
           :shift (:shift/composite shift)
           :filament filament-score}}))

(defn- deltas [base exo]
  {:delta-short (- (get-in exo [:raw :short]) (get-in base [:raw :short]))
   :delta-envelope (- (get-in exo [:raw :envelope]) (get-in base [:raw :envelope]))
   :delta-triad (- (get-in exo [:raw :triad]) (get-in base [:raw :triad]))
   :delta-shift (- (get-in exo [:raw :shift]) (get-in base [:raw :shift]))
   :delta-filament (- (get-in exo [:raw :filament]) (get-in base [:raw :filament]))
   :envelope (get-in exo [:raw :envelope])})

(defn -main [& args]
  (let [{:keys [help unknown sigil step seeds weights out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [sigil (or sigil "工")
            step (double (or step 0.1))
            seeds (int (or seeds 5))
            weights-path (or weights "/tmp/mission-17a-pref-weights.edn")
            weights-data (edn/read-string (slurp weights-path))
            weight-vec (:w (:model weights-data))
            weight-map {:delta-short (nth weight-vec 0)
                        :delta-envelope (nth weight-vec 1)
                        :delta-triad (nth weight-vec 2)
                        :delta-shift (nth weight-vec 3)
                        :delta-filament (nth weight-vec 4)}
            base-cfg (:config (edn/read-string (slurp "futon5/resources/mission-17a-refine-baseline.edn")))
            exo-cfg (:config (edn/read-string (slurp "futon5/resources/mission-17a-refine-exotic.edn")))
            gen-len (count (:genotype base-cfg))
            phe-len (count (:phenotype base-cfg))
            gens (:generations base-cfg)
            rng (java.util.Random. 4242)
            grid (for [u (range 0.0 1.0001 step)
                       m (range 0.0 1.0001 step)]
                   [u m])
            out (or out (format "/tmp/mission-17a-sweep-%d.csv" (System/currentTimeMillis)))
            header "update_prob,match_threshold,pass_rate,mean_rank,error_count"
            total (count grid)
            start-ms (System/currentTimeMillis)]
        (with-open [w (io/writer out)]
          (.write w header)
          (.write w "\n")
          (doseq [[idx [u m]] (map-indexed vector grid)]
            (let [scores (for [_ (range seeds)]
                           (let [seed (rng-int rng Integer/MAX_VALUE)]
                             (try
                               (let [grng (java.util.Random. seed)
                                     genotype (rng-sigil-string grng gen-len)
                                     phenotype (rng-phenotype-string grng phe-len)
                                     exo (assoc (:exotype exo-cfg)
                                                :sigil sigil
                                                :params (assoc (:params (:exotype exo-cfg))
                                                               :update-prob u
                                                               :match-threshold m))
                                     base-run (mmca/run-mmca (assoc base-cfg
                                                                    :genotype genotype
                                                                    :phenotype phenotype
                                                                    :seed seed
                                                                    :generations gens))
                                     exo-run (mmca/run-mmca (assoc exo-cfg
                                                                   :genotype genotype
                                                                   :phenotype phenotype
                                                                   :seed seed
                                                                   :generations gens
                                                                   :exotype (exotype/resolve-exotype exo)))]
                                 (let [base (score-run base-run)
                                       exo (score-run exo-run)
                                       raw (deltas base exo)
                                       scored (scoring/gate-rank-score {:summary (:summary exo)
                                                                        :raw raw
                                                                        :weights weight-map})]
                                   {:passed? (get-in scored [:gate :passed?])
                                    :score (get-in scored [:rank :score])
                                    :error? false}))
                               (catch Exception e
                                 (println (format "warn u=%.2f m=%.2f seed=%d error=%s"
                                                  u m seed (.getMessage e)))
                                 {:passed? false
                                  :score 0.0
                                  :error? true}))))
                  error-count (count (filter :error? scores))
                  pass-rate (if (seq scores)
                              (/ (count (filter :passed? scores)) (double (count scores)))
                              0.0)
                  mean-rank (if (seq scores)
                              (/ (reduce + 0.0 (map :score scores)) (double (count scores)))
                              0.0)
                  line (format "%.2f,%.2f,%.3f,%.3f,%d" u m pass-rate mean-rank error-count)
                  elapsed-s (/ (- (System/currentTimeMillis) start-ms) 1000.0)
                  done (inc idx)]
              (.write w line)
              (.write w "\n")
              (.flush w)
              (println (format "progress %d/%d u=%.2f m=%.2f pass=%.3f rank=%.3f elapsed=%.1fs"
                               done total u m pass-rate mean-rank elapsed-s))
              (flush)))
        (println "Wrote" out))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
