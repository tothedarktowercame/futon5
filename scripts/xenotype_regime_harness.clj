(ns xenotype-regime-harness
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.metrics :as metrics]
            [futon5.xenotype.interpret :as interpret]))

(def ^:private default-config "data/xenotype-regime-harness.edn")

(defn- usage []
  (str/join
   "\n"
   ["Run a fixed-seed xenotype generator harness across multiple wirings."
    ""
    "Usage:"
    "  clojure -M -m xenotype-regime-harness [options]"
    ""
    "Options:"
    "  --config PATH        Harness config EDN (default data/xenotype-regime-harness.edn)."
    "  --out-dir PATH       Output directory (default /tmp/futon5-xenotype-harness-<ts>)."
    "  --length N           Override genotype length."
    "  --generations N      Override generations."
    "  --seed N             Run a single seed (overrides config :seeds)."
    "  --help               Show this message."]))

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

(defn- load-diagram
  [{:keys [wiring index]}]
  (let [path (or wiring "")
        data (edn/read-string (slurp path))]
    (cond
      (and (map? data) (:candidates data))
      (let [idx (long (or index 0))
            candidates (:candidates data)
            safe-idx (max 0 (min (dec (count candidates)) idx))]
        (nth candidates safe-idx))

      (and (map? data) (:diagram data))
      (:diagram data)

      (map? data)
      data

      :else
      (throw (ex-info "Unsupported wiring data" {:path path})))))

(defn- bit-at [s idx]
  (if (and s (<= 0 idx) (< idx (count s)))
    (nth s idx)
    \0))

(defn- phenotype-family-at [phe-row phe-next idx]
  (if (and phe-row phe-next)
    (str (bit-at phe-row (dec idx))
         (bit-at phe-row idx)
         (bit-at phe-row (inc idx))
         (bit-at phe-next idx))
    "0000"))

(defn- output->sigil [output]
  (cond
    (string? output) output
    (map? output) (or (:out output) (:sigil output) (:result output) (first (vals output)))
    :else ca/default-sigil))

(defn- derive-metrics [gen-history]
  (let [curr (peek gen-history)
        prev (when (> (count gen-history) 1)
               (nth gen-history (- (count gen-history) 2)))
        len (count curr)
        change (when (and prev (pos? len))
                 (/ (double (ca/hamming-distance prev curr)) len))
        counts (frequencies (seq (or curr "")))
        entropy (metrics/shannon-entropy curr)
        generation (dec (count gen-history))]
    {:generation generation
     :length len
     :unique-sigils (count counts)
     :sigil-counts counts
     :entropy entropy
     :change-rate change}))

(defn- evolve-once
  [{:keys [diagram genotype phenotype prev-genotype tick seed]}]
  (let [len (count genotype)
        letters (vec (map str (seq genotype)))
        prev-letters (vec (map str (seq (or prev-genotype genotype))))
        phe-row (or phenotype (apply str (repeat len "0")))
        phe-next (when phenotype
                   (ca/evolve-phenotype-against-genotype genotype phenotype))
        default ca/default-sigil
        next-letters
        (mapv (fn [idx]
                (let [pred (get letters (dec idx) default)
                      self (get letters idx)
                      succ (get letters (inc idx) default)
                      prev (get prev-letters idx self)
                      phe (phenotype-family-at phe-row phe-next idx)
                      ctx {:pred pred :self self :succ succ :prev prev :phe phe}
                      state {:seed seed :tick tick :x idx}
                      result (interpret/evaluate-diagram diagram {:ctx ctx :state state})]
                  (output->sigil (:output result))))
              (range len))
        next-genotype (apply str next-letters)]
    {:genotype next-genotype
     :phenotype phe-next}))

(defn- run-model
  [{:keys [diagram seed generations base-genotype base-phenotype]}]
  (loop [tick 0
         gen-history [base-genotype]
         phe-history (when base-phenotype [base-phenotype])
         metrics-history [(derive-metrics [base-genotype])]
         prev-genotype nil]
    (if (= tick generations)
      {:gen-history gen-history
       :phe-history phe-history
       :metrics-history metrics-history
       :generations generations
       :seed seed}
      (let [{:keys [genotype phenotype]}
            (evolve-once {:diagram diagram
                          :genotype (peek gen-history)
                          :phenotype (when phe-history (peek phe-history))
                          :prev-genotype prev-genotype
                          :tick tick
                          :seed seed})
            gen-history' (conj gen-history genotype)
            phe-history' (when phe-history (conj phe-history phenotype))
            metrics-history' (conj metrics-history (derive-metrics gen-history'))]
        (recur (inc tick) gen-history' phe-history' metrics-history' (peek gen-history))))))

(defn- write-run!
  [{:keys [out-dir label result meta]}]
  (let [path (io/file out-dir (str label ".edn"))
        payload (assoc result :meta meta :summary (metrics/summarize-run result))]
    (spit path (pr-str payload))
    path))

(defn -main [& args]
  (let [{:keys [help unknown config out-dir length generations seed]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [config-path (or config default-config)
            config (edn/read-string (slurp config-path))
            length (int (or length (:length config) 50))
            generations (int (or generations (:generations config) 60))
            seeds (if seed [seed] (or (:seeds config) [4242]))
            models (or (:models config) [])
            out-dir (or out-dir (format "/tmp/futon5-xenotype-harness-%d" (System/currentTimeMillis)))]
        (.mkdirs (io/file out-dir))
        (doseq [seed seeds]
          (let [rng (java.util.Random. (long seed))
                base-genotype (rng-sigil-string rng length)
                base-phenotype (rng-phenotype-string rng length)]
            (doseq [model models]
              (let [diagram (load-diagram model)
                    label (sanitize-label (str (:label model) "-seed-" seed))
                    meta {:model-id (:id model)
                          :model-label (:label model)
                          :wiring (:wiring model)
                          :seed seed
                          :length length
                          :generations generations}
                    result (run-model {:diagram diagram
                                       :seed seed
                                       :length length
                                       :generations generations
                                       :base-genotype base-genotype
                                       :base-phenotype base-phenotype})]
                (write-run! {:out-dir out-dir
                             :label label
                             :result result
                             :meta meta})
                (println "Wrote" label)))))
        (println "Done.")))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
