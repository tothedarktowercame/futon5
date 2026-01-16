(ns futon5.mmca.genoevolve
  "Short-horizon genotype evolution loop."
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.runtime :as mmca]))

(def ^:private default-length 50)
(def ^:private default-generations 30)
(def ^:private default-runs 200)
(def ^:private default-pop 16)
(def ^:private default-update-every 100)
(def ^:private default-mutation-rate 0.1)

(defn- usage []
  (str/join
   "\n"
   ["Genotype evolution"
    ""
    "Usage:"
    "  bb -cp src:resources -m futon5.mmca.genoevolve [options]"
    ""
    "Options:"
    "  --runs N               Genotype evaluations (default 200)."
    "  --length N             Genotype length (default 50)."
    "  --generations N        Generations per run (default 30)."
    "  --pop N                Genotype population size (default 16)."
    "  --update-every N       Update cadence (default 100)."
    "  --mutation-rate R      Per-site mutation rate (default 0.1)."
    "  --freeze-genotype      Keep genotype fixed during runs."
    "  --log PATH             Append EDN log entries (optional)."
    "  --seed N               RNG seed."]))

(defn- parse-int [s]
  (try
    (Long/parseLong s)
    (catch Exception _
      nil)))

(defn- parse-double [s]
  (try
    (Double/parseDouble s)
    (catch Exception _
      nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--runs" flag)
          (recur (rest more) (assoc opts :runs (parse-int (first more))))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--pop" flag)
          (recur (rest more) (assoc opts :pop (parse-int (first more))))

          (= "--update-every" flag)
          (recur (rest more) (assoc opts :update-every (parse-int (first more))))

          (= "--mutation-rate" flag)
          (recur (rest more) (assoc opts :mutation-rate (parse-double (first more))))

          (= "--freeze-genotype" flag)
          (recur more (assoc opts :freeze-genotype true))

          (= "--log" flag)
          (recur (rest more) (assoc opts :log (first more)))

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

(defn- mutate-genotype
  [^java.util.Random rng genotype length mutation-rate]
  (let [sigils (mapv :sigil (ca/sigil-entries))
        rate (double (or mutation-rate default-mutation-rate))]
    (if (or (nil? genotype) (not= (count genotype) length))
      (rng-sigil-string rng length)
      (apply str
             (map (fn [ch]
                    (if (< (.nextDouble rng) rate)
                      (nth sigils (rng-int rng (count sigils)))
                      ch))
                  genotype)))))

(defn- evaluate-genotype
  [genotype length generations ^java.util.Random rng freeze-genotype]
  (let [phenotype (rng-phenotype-string rng length)
        seed (rng-int rng Integer/MAX_VALUE)
        result (mmca/run-mmca {:genotype genotype
                               :phenotype phenotype
                               :generations generations
                               :kernel :mutating-template
                               :operators []
                               :freeze-genotype freeze-genotype
                               :seed seed})
        summary (metrics/summarize-run result)
        score (double (or (:composite-score summary) 0.0))]
    {:result result
     :summary summary
     :score score
     :seed seed
     :phenotype phenotype}))

(defn- log-entry [run-id genotype length generations eval freeze-genotype]
  {:schema/version 1
   :experiment/id :genoevolve
   :run/id run-id
   :seed (:seed eval)
   :length length
   :generations generations
   :kernel :mutating-template
   :freeze-genotype (boolean freeze-genotype)
   :genotype genotype
   :phenotype (:phenotype eval)
   :score {:composite (:score eval)}
   :summary (:summary eval)})

(defn- append-log! [path entry]
  (spit path (str (pr-str entry) "\n") :append true))

(defn- evolve-population
  [^java.util.Random rng population batch length mutation-rate]
  (let [by-genotype (group-by :genotype batch)
        scored (mapv (fn [genotype]
                       (let [runs (get by-genotype genotype)
                             scores (mapv (comp :composite :score) runs)
                             mean (if (seq scores)
                                    (/ (reduce + 0.0 scores) (double (count scores)))
                                    0.0)]
                         {:genotype genotype :fitness mean}))
                     population)
        ranked (sort-by :fitness > scored)
        survivors (vec (take (max 1 (quot (count ranked) 2)) ranked))
        offspring (vec (repeatedly (- (count ranked) (count survivors))
                                  #(let [parent (:genotype (rand-nth survivors))]
                                     (mutate-genotype rng parent length mutation-rate))))]
    (vec (concat (map :genotype survivors) offspring))))

(defn evolve-genotypes
  [{:keys [runs length generations pop update-every seed log mutation-rate freeze-genotype]}]
  (let [runs (or runs default-runs)
        length (or length default-length)
        generations (or generations default-generations)
        pop (or pop default-pop)
        update-every (or update-every default-update-every)
        mutation-rate (or mutation-rate default-mutation-rate)
        rng (java.util.Random. (long (or seed 4242)))]
    (loop [i 0
           population (vec (repeatedly pop #(rng-sigil-string rng length)))
           batch []]
      (if (= i runs)
        {:population population}
        (let [genotype (rand-nth population)
              eval (evaluate-genotype genotype length generations rng freeze-genotype)
              entry (log-entry (inc i) genotype length generations eval freeze-genotype)
              batch' (conj batch entry)
              update? (>= (count batch') update-every)
              population' (if update?
                            (evolve-population rng population batch' length mutation-rate)
                            population)
              batch'' (if update? [] batch')]
          (when log
            (append-log! log entry))
          (recur (inc i) population' batch''))))))

(defn -main [& args]
  (let [{:keys [help unknown] :as opts} (parse-args args)]
    (cond
      help
      (println (usage))

      unknown
      (do
        (println "Unknown option:" unknown)
        (println (usage)))

      :else
      (do
        (evolve-genotypes opts)
        (when-let [log (:log opts)]
          (println "Log written to" log))))))
