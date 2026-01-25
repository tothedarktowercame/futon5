(ns xenotype-regime-batch
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.runtime :as mmca]))

(def ^:private default-config "data/xenotype-regime-batch-20.edn")

(defn- usage []
  (str/join
   "\n"
   ["Run a batch of comparable MMCA regimes with shared init per seed."
    ""
    "Usage:"
    "  bb -cp src:resources:data scripts/xenotype_regime_batch.clj [options]"
    ""
    "Options:"
    "  --config PATH    Batch config EDN (default data/xenotype-regime-batch-20.edn)."
    "  --out-dir PATH   Output directory (default /tmp/futon5-xenotype-batch-<ts>)."
    "  --length N       Override genotype length."
    "  --generations N  Override generations."
    "  --seed N         Run a single seed (overrides config :seeds)."
    "  --help           Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{{"--help" "-h"}} flag)
          (recur more (assoc opts :help true))

          (= "--config" flag)
          (recur (rest more) (assoc opts :config (first more)))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

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

(defn- sanitize-label [s]
  (-> s
      (str/replace #"\s+" "-")
      (str/replace #"[^a-zA-Z0-9._-]" "")))

(defn- write-run! [out-dir label result meta]
  (let [path (io/file out-dir (str label ".edn"))]
    (spit path (pr-str (assoc result :meta meta)))
    path))

(defn -main [& args]
  (let [{:keys [help unknown config out-dir length generations seed]} (parse-args args)]
    (when help
      (println (usage))
      (System/exit 0))
    (when unknown
      (println "Unknown option:" unknown)
      (println)
      (println (usage))
      (System/exit 1))
    (let [config-path (or config default-config)
          config (edn/read-string (slurp config-path))
          length (int (or length (:length config) 50))
          generations (int (or generations (:generations config) 60))
          seeds (if seed [seed] (or (:seeds config) [4242]))
          models (or (:models config) [])
          out-dir (or out-dir (format "/tmp/futon5-xenotype-batch-%d" (System/currentTimeMillis)))]
      (.mkdirs (io/file out-dir))
      (doseq [seed seeds]
        (let [rng (java.util.Random. (long seed))
              base-genotype (rng-sigil-string rng length)
              base-phenotype (rng-phenotype-string rng length)]
          (doseq [model models]
            (let [label (sanitize-label (str (:label model) "-seed-" seed))
                  result (mmca/run-mmca {:genotype base-genotype
                                         :phenotype base-phenotype
                                         :generations generations
                                         :kernel (or (:kernel model) :mutating-template)
                                         :operators []
                                         :exotype (:exotype model)
                                         :exotype-mode (:exotype-mode model)
                                         :global-rule (:global-rule model)
                                         :bend-mode (:bend-mode model)
                                         :seed seed})
                  meta {:model-id (:id model)
                        :model-label (:label model)
                        :wiring (:wiring model)
                        :seed seed
                        :length length
                        :generations generations
                        :kernel (or (:kernel model) :mutating-template)
                        :exotype (:exotype model)
                        :exotype-mode (:exotype-mode model)}]
              (write-run! out-dir label result meta)
              (println "Wrote" label)))))
      (println "Done."))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
