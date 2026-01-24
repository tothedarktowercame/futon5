(ns futon5.mmca.xenoevolve
  "Slow outer loop that evolves xenotypes against exotype runs."
  (:require [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.runtime :as mmca]
            [futon5.mmca.xenotype :as xenotype]))

(def ^:private default-length 50)
(def ^:private default-generations 50)
(def ^:private default-runs 500)
(def ^:private default-xeno-pop 12)
(def ^:private default-update-every 100)

(defn- usage []
  (str/join
   "\n"
   ["Xenotype evolution"
    ""
    "Usage:"
    "  bb -cp src:resources -m futon5.mmca.xenoevolve [options]"
    ""
    "Options:"
    "  --runs N               Exotype evaluations (default 500)."
    "  --length N             Genotype length (default 50)."
    "  --generations N        Generations per run (default 50)."
    "  --xeno-pop N           Xenotype population size (default 12)."
    "  --update-every N       Xenotype update cadence (default 100)."
    "  --tier KW              Exotype tier: local, super, or both (default both)."
    "  --seed N               RNG seed."]))

(defn- parse-int [s]
  (try
    (Long/parseLong s)
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

          (= "--xeno-pop" flag)
          (recur (rest more) (assoc opts :xeno-pop (parse-int (first more))))

          (= "--update-every" flag)
          (recur (rest more) (assoc opts :update-every (parse-int (first more))))

          (= "--tier" flag)
          (recur (rest more) (assoc opts :tier (some-> (first more) keyword)))

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

(defn- pick-exotype [^java.util.Random rng tier]
  (let [sigils (mapv :sigil (ca/sigil-entries))
        sigil (nth sigils (rng-int rng (count sigils)))]
    (case tier
      :local (exotype/lift sigil)
      :super (exotype/promote sigil)
      :both (if (< (.nextDouble rng) 0.5)
              (exotype/lift sigil)
              (exotype/promote sigil))
      (exotype/lift sigil))))

(defn- run-exotype
  [exotype length generations ^java.util.Random rng]
  (let [genotype (rng-sigil-string rng length)
        phenotype (rng-phenotype-string rng length)
        seed (rng-int rng Integer/MAX_VALUE)]
    (mmca/run-mmca {:genotype genotype
                    :phenotype phenotype
                    :generations generations
                    :kernel :mutating-template
                    :operators []
                    :exotype exotype
                    :seed seed})))

(defn- score-xenotypes [xenotypes batch]
  (mapv (fn [xeno]
          (merge xeno (xenotype/fitness xeno batch)))
        xenotypes))

(defn- strip-score [xeno]
  (dissoc xeno :fitness :mean :variance :count))

(defn- evolve-population [^java.util.Random rng xenotypes batch]
  (let [ranked (sort-by :fitness > (score-xenotypes xenotypes batch))
        survivors (vec (map strip-score (take (max 1 (quot (count ranked) 2)) ranked)))
        spawn-count (- (count ranked) (count survivors))
        offspring (vec (repeatedly spawn-count
                                  #(xenotype/mutate rng (rand-nth survivors))))]
    (vec (concat survivors offspring))))

(defn evolve-xenotypes
  [{:keys [runs length generations seed xeno-pop update-every tier]}]
  (let [runs (or runs default-runs)
        length (or length default-length)
        generations (or generations default-generations)
        xeno-pop (or xeno-pop default-xeno-pop)
        update-every (or update-every default-update-every)
        tier (or tier :both)
        rng (java.util.Random. (long (or seed 4242)))]
    (loop [i 0
           xenotypes (vec (repeatedly xeno-pop #(xenotype/random-spec rng)))
           batch []
           history []]
      (if (= i runs)
        {:xenotypes xenotypes
         :history history}
        (let [exotype (pick-exotype rng tier)
              result (run-exotype exotype length generations rng)
              batch' (conj batch result)
              update? (>= (count batch') update-every)
              xenotypes' (if update?
                           (evolve-population rng xenotypes batch')
                           xenotypes)
              history' (if update?
                         (conj history {:evaluations (+ i 1)
                                        :top (first (sort-by :fitness > (score-xenotypes xenotypes batch')))
                                        :population xenotypes'})
                         history)
              batch'' (if update? [] batch')]
          (when update?
            (let [{:keys [fitness mean variance count]} (:top (last history'))]
              (println (format "xeno update @ %d | fitness %.3f | mean %.3f | var %.3f | n %d"
                               (+ i 1)
                               (double (or fitness 0.0))
                               (double (or mean 0.0))
                               (double (or variance 0.0))
                               (long (or count 0))))))
          (recur (inc i) xenotypes' batch'' history'))))))

(defn -main [& args]
  (let [{:keys [help unknown] :as opts} (parse-args args)]
    (cond
      help
      (println (usage))

      unknown
      (do
        (println "Unknown option:" unknown)
        (println)
        (println (usage)))

      :else
      (let [result (evolve-xenotypes opts)
            top (-> result :history last :top)]
        (println "Done.")
        (when top
          (println (format "Top xenotype fitness %.3f" (double (:fitness top)))))))))
