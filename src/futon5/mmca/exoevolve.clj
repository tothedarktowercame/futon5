(ns futon5.mmca.exoevolve
  "Short-horizon exotype evolution loop."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.runtime :as mmca]
            [futon5.mmca.xenotype :as xenotype]
            [futon5.exotic.ratchet :as ratchet]
            [futon5.exotic.curriculum :as curriculum]))

(def ^:private default-length 50)
(def ^:private default-generations 30)
(def ^:private default-runs 400)
(def ^:private default-pop 32)
(def ^:private default-update-every 100)

(defn- usage []
  (str/join
   "\n"
   ["Exotype evolution"
    ""
    "Usage:"
    "  bb -cp src:resources -m futon5.mmca.exoevolve [options]"
    ""
    "Options:"
    "  --runs N               Exotype evaluations (default 400)."
    "  --length N             Genotype length (default 50)."
    "  --generations N        Generations per run (default 30)."
    "  --pop N                Exotype population size (default 32)."
    "  --update-every N       Update cadence (default 100)."
    "  --tier KW              Exotype tier: local, super, or both (default both)."
    "  --context-depth N      Recursive-local context depth (default 1)."
    "  --xeno-spec PATH       EDN xenotype spec or vector of specs (optional)."
    "  --xeno-weight W        Blend xenotype score into short score (0-1)."
    "  --curriculum-gate      Clamp ratchet deltas below threshold (optional)."
    "  --log PATH             Append EDN log entries (optional)."
    "  --tap                 Emit tap> events for runs/windows (optional)."
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

          (= "--tier" flag)
          (recur (rest more) (assoc opts :tier (some-> (first more) keyword)))

          (= "--context-depth" flag)
          (recur (rest more) (assoc opts :context-depth (parse-int (first more))))

          (= "--xeno-spec" flag)
          (recur (rest more) (assoc opts :xeno-spec (first more)))

          (= "--xeno-weight" flag)
          (recur (rest more) (assoc opts :xeno-weight (parse-double (first more))))

          (= "--curriculum-gate" flag)
          (recur more (assoc opts :curriculum-gate true))

          (= "--log" flag)
          (recur (rest more) (assoc opts :log (first more)))

          (= "--tap" flag)
          (recur more (assoc opts :tap true))

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

(defn- mutate-exotype [^java.util.Random rng exotype tier]
  (let [roll (.nextDouble rng)
        sigil (if (< roll 0.5)
                (:sigil (pick-exotype rng tier))
                (:sigil exotype))
        tier (cond
               (not= tier :both) tier
               (< roll 0.75) (if (= :local (:tier exotype)) :super :local)
               :else (or (:tier exotype) :local))]
    (exotype/resolve-exotype {:sigil sigil :tier tier})))

(defn- load-xeno-specs [path]
  (when path
    (let [data (edn/read-string (slurp path))]
      (cond
        (map? data) [data]
        (vector? data) data
        :else nil))))

(defn- blend-score [short-score xeno-score weight]
  (let [w (double (or weight 0.0))
        x (double (or xeno-score 0.0))
        s (double (or short-score 0.0))]
    (+ (* (- 1.0 w) s)
       (* w x))))

(defn- score-xenotype [xeno-specs result ratchet-context]
  (when (seq xeno-specs)
    (let [scores (mapv (fn [spec]
                         (let [spec (cond-> spec
                                      ratchet-context (assoc :ratchet ratchet-context))]
                           (:score (xenotype/score-run spec result))))
                       xeno-specs)
          mean (/ (reduce + 0.0 scores) (double (count scores)))]
      {:scores scores
       :mean mean})))

(defn- evaluate-exotype
  [exotype length generations ^java.util.Random rng xeno-specs xeno-weight context-depth ratchet-context]
  (let [genotype (rng-sigil-string rng length)
        phenotype (rng-phenotype-string rng length)
        seed (rng-int rng Integer/MAX_VALUE)
        result (mmca/run-mmca {:genotype genotype
                               :phenotype phenotype
                               :generations generations
                               :kernel :mutating-template
                               :operators []
                               :exotype exotype
                               :exotype-context-depth context-depth
                               :seed seed})
        summary (metrics/summarize-run result)
        short-score (double (or (:composite-score summary) 0.0))
        xeno (score-xenotype xeno-specs result ratchet-context)
        xeno-score (when xeno (* 100.0 (double (or (:mean xeno) 0.0))))
        final-score (blend-score short-score xeno-score xeno-weight)]
    {:result result
     :summary summary
     :short-score short-score
     :xeno xeno
     :final-score final-score
     :seed seed}))

(defn- program-template [exotype]
  (if (= :super (:tier exotype))
    :contextual-mutate+mix
    :contextual-mutate))

(defn- log-entry [run-id exotype length generations eval context-depth ratchet-context]
  {:schema/version 1
   :experiment/id :exoevolve
   :event :run
   :run/id run-id
   :seed (:seed eval)
   :length length
   :generations generations
   :context-depth context-depth
   :kernel :mutating-template
   :exotype (select-keys exotype [:sigil :tier :params])
   :program-template (program-template exotype)
   :score {:short (:short-score eval)
           :xeno (get-in eval [:xeno :mean])
           :final (:final-score eval)}
   :ratchet ratchet-context
   :summary (:summary eval)})

(defn- append-log! [path entry]
  (spit path (str (pr-str entry) "\n") :append true))

(defn- avg [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn- collapse-stats [entries]
  (let [dead-change 0.05
        dead-entropy 0.2
        confetti-change 0.45
        confetti-entropy 0.8
        summaries (map :summary entries)
        dead? (fn [s] (and (<= (double (or (:avg-change s) 0.0)) dead-change)
                           (<= (double (or (:avg-entropy-n s) 0.0)) dead-entropy)))
        confetti? (fn [s] (and (>= (double (or (:avg-change s) 0.0)) confetti-change)
                               (>= (double (or (:avg-entropy-n s) 0.0)) confetti-entropy)))
        dead-count (count (filter dead? summaries))
        confetti-count (count (filter confetti? summaries))
        total (count summaries)]
    {:dead-count dead-count
     :dead-rate (if (pos? total) (/ dead-count (double total)) 0.0)
     :confetti-count confetti-count
     :confetti-rate (if (pos? total) (/ confetti-count (double total)) 0.0)}))

(defn- summarize-batch [entries]
  (let [scores (mapv :score entries)
        finals (mapv :final scores)
        sorted (sort finals)
        n (count finals)
        idx (fn [q] (nth sorted (int (Math/floor (* (max 0.0 (min 1.0 q)) (dec n))))))]
    (merge {:count (count finals)
            :mean (avg finals)
            :q50 (when (seq finals) (idx 0.5))
            :q90 (when (seq finals) (idx 0.9))
            :best (apply max finals)}
           (collapse-stats entries))))

(defn- window-log-entry [window stats delta]
  {:schema/version 1
   :experiment/id :exoevolve
   :event :window
   :window window
   :stats stats
   :delta delta})

(defn- tap-run-entry [entry]
  (select-keys entry
               [:schema/version :experiment/id :event :run/id :seed
                :length :generations :context-depth :kernel :exotype
                :program-template :score :ratchet :summary]))

(defn- tap-window-entry [entry]
  (select-keys entry
               [:schema/version :experiment/id :event :window :stats :delta]))

(defn- evolve-population
  [^java.util.Random rng population batch tier]
  (let [by-exotype (group-by (fn [entry]
                               (select-keys (:exotype entry) [:sigil :tier]))
                             batch)
        scored (mapv (fn [exo]
                       (let [runs (get by-exotype (select-keys exo [:sigil :tier]))
                             finals (mapv (comp :final :score) runs)
                             mean (if (seq finals)
                                    (/ (reduce + 0.0 finals) (double (count finals)))
                                    0.0)]
                         (assoc exo :fitness mean)))
                     population)
        ranked (sort-by :fitness > scored)
        survivors (vec (take (max 1 (quot (count ranked) 2)) ranked))
        offspring (vec (repeatedly (- (count ranked) (count survivors))
                                  #(mutate-exotype rng (rand-nth survivors) tier)))]
    (vec (concat (map #(dissoc % :fitness) survivors) offspring))))

(defn evolve-exotypes
  [{:keys [runs length generations pop update-every tier seed xeno-spec xeno-weight log context-depth curriculum-gate tap]}]
  (let [runs (or runs default-runs)
        length (or length default-length)
        generations (or generations default-generations)
        pop (or pop default-pop)
        update-every (or update-every default-update-every)
        tier (or tier :both)
        context-depth (max 1 (int (or context-depth 1)))
        xeno-weight (double (or xeno-weight 0.0))
        rng (java.util.Random. (long (or seed 4242)))
        xeno-specs (load-xeno-specs xeno-spec)]
    (loop [i 0
           window 0
           prev-window nil
           ratchet-state (ratchet/init-state)
           ratchet-context nil
           population (vec (repeatedly pop #(pick-exotype rng tier)))
           batch []]
      (if (= i runs)
        {:population population}
        (let [exotype (rand-nth population)
              eval (evaluate-exotype exotype length generations rng xeno-specs xeno-weight context-depth ratchet-context)
              entry (log-entry (inc i) exotype length generations eval context-depth ratchet-context)
              batch' (conj batch entry)
              update? (>= (count batch') update-every)
              stats (when update? (summarize-batch batch'))
              delta (when (and update? prev-window)
                      {:delta-mean (- (:mean stats) (:mean prev-window))
                       :delta-q50 (- (:q50 stats) (:q50 prev-window))})
              population' (if update?
                            (evolve-population rng population batch' tier)
                            population)
              batch'' (if update? [] batch')
              window' (if update? (inc window) window)
              prev-window' (if update? stats prev-window)
              ratchet-state' (if update?
                               (ratchet/update-window ratchet-state stats)
                               ratchet-state)
              ratchet-context' (when (and update? prev-window)
                                 (let [threshold (curriculum/curriculum-threshold window' nil)
                                       delta (- (double (:mean stats)) (double (:mean prev-window)))
                                       gate? (and curriculum-gate (< delta threshold))]
                                   {:prev-score (:mean prev-window)
                                    :curr-score (:mean stats)
                                    :gate (when gate? :blocked)
                                    :curriculum {:threshold threshold
                                                 :window window'}}))]
          (when log
            (append-log! log entry))
          (when (and log update?)
            (append-log! log (window-log-entry window' stats delta)))
          (when tap
            (tap> (tap-run-entry entry)))
          (when (and tap update?)
            (tap> (tap-window-entry (window-log-entry window' stats delta))))
          (when update?
            (let [{:keys [mean best count]} (summarize-batch batch')]
              (println (format "exo update @ %d | mean %.2f | best %.2f | n %d"
                               (inc i)
                               (double (or mean 0.0))
                               (double (or best 0.0))
                               (long (or count 0))))))
          (recur (inc i) window' prev-window' ratchet-state' ratchet-context' population' batch''))))))

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
      (do
        (evolve-exotypes opts)
        (println "Done.")))))
