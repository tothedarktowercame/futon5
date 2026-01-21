(ns replay-fidelity
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.runtime :as mmca]))

(defn- usage []
  (str/join
   "\n"
   ["Replay fidelity checker."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/replay_fidelity.clj --run PATH --config PATH"
    ""
    "Options:"
    "  --run PATH     EDN run history with :gen-history/:phe-history."
    "  --config PATH  EDN file with :config containing genotype/phenotype/seed/exotype."
    "  --ticks LIST   Comma-separated ticks to hash (default 0,25,50,100)."
    "  --help         Show this message."]))

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

          (= "--run" flag)
          (recur (rest more) (assoc opts :run (first more)))

          (= "--config" flag)
          (recur (rest more) (assoc opts :config (first more)))

          (= "--ticks" flag)
          (recur (rest more) (assoc opts :ticks (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- parse-ticks [s]
  (if (seq s)
    (->> (str/split s #",")
         (map str/trim)
         (remove str/blank?)
         (map parse-int)
         (remove nil?)
         vec)
    [0 25 50 100]))

(defn- hash-at [history tick]
  (when (and (seq history) (<= 0 tick) (< tick (count history)))
    (hash (nth history tick))))

(defn -main [& args]
  (let [{:keys [help unknown run config ticks]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      (or (nil? run) (nil? config)) (do (println "Missing --run or --config") (println) (println (usage)))
      :else
      (let [ticks (parse-ticks ticks)
            run-data (edn/read-string (slurp run))
            cfg-data (edn/read-string (slurp config))
            cfg (:config cfg-data)
            exo (exotype/resolve-exotype (:exotype cfg))
            replay (mmca/run-mmca (assoc cfg :exotype exo))
            hist (:gen-history run-data)
            phe (:phe-history run-data)
            rhist (:gen-history replay)
            rphe (:phe-history replay)
            rows (map (fn [t]
                        {:tick t
                         :gen (hash-at hist t)
                         :gen-replay (hash-at rhist t)
                         :phe (hash-at phe t)
                         :phe-replay (hash-at rphe t)})
                      ticks)
            ok? (every? (fn [{:keys [gen gen-replay phe phe-replay]}]
                          (and (= gen gen-replay) (= phe phe-replay)))
                        rows)]
        (println (format "Replay %s" (if ok? "PASS" "FAIL")))
        (doseq [{:keys [tick gen gen-replay phe phe-replay]} rows]
          (println (format "t=%d gen %s vs %s | phe %s vs %s"
                           tick gen gen-replay phe phe-replay)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
