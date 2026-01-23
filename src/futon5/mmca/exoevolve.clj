(ns futon5.mmca.exoevolve
  "Short-horizon exotype evolution loop."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.register-shift :as register-shift]
            [futon5.mmca.trigram :as trigram]
            [futon5.mmca.runtime :as mmca]
            [futon5.mmca.xenotype :as xenotype]
            [futon5.hexagram.metrics :as hex-metrics]
            [futon5.hexagram.logging :as hex-log]
            [futon5.exotic.ratchet :as ratchet]
            [futon5.exotic.curriculum :as curriculum]
            [futon5.exotic.ratchet-library :as ratchet-lib]))

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
    "  --hexagram-weight W    Blend hexagram fitness into score (0-1)."
    "  --score-mode MODE      legacy, triad, or shift (default legacy)."
    "  --update-prob P        Override exotype update-prob (optional)."
    "  --match-threshold P    Override exotype match-threshold (optional)."
    "  --envelope-center P    Entropy envelope center (0-1, default 0.6)."
    "  --envelope-width P     Entropy envelope width (0-1, default 0.25)."
    "  --envelope-change-center P  Change envelope center (0-1, default 0.2)."
    "  --envelope-change-width P   Change envelope width (0-1, default 0.15)."
    "  --envelope-change      Include avg-change in envelope score (default true)."
    "  --hexagram-log PATH    Append PSR/PUR entries to EDN log (optional)."
    "  --iiching-root PATH    iiching library root (optional)."
    "  --iiching-manifest PATH Exotype manifest for iiching lookup (optional)."
    "  --curriculum-gate      Clamp ratchet deltas below threshold (optional)."
    "  --log PATH             Append EDN log entries (optional)."
    "  --heartbeat N          Print/log progress every N runs (optional)."
    "  --on-error MODE        fail or continue (default fail)."
    "  --tap                 Emit tap> events for runs/windows (optional)."
    "  --seed N               RNG seed."]))

(defn- parse-int [s]
  (try
    (Long/parseLong s)
    (catch Exception _
      nil)))

(defn- parse-double* [s]
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
          (recur (rest more) (assoc opts :xeno-weight (parse-double* (first more))))

          (= "--hexagram-weight" flag)
          (recur (rest more) (assoc opts :hexagram-weight (parse-double* (first more))))

          (= "--score-mode" flag)
          (recur (rest more) (assoc opts :score-mode (some-> (first more) keyword)))

          (= "--update-prob" flag)
          (recur (rest more) (assoc opts :update-prob (parse-double* (first more))))

          (= "--match-threshold" flag)
          (recur (rest more) (assoc opts :match-threshold (parse-double* (first more))))

          (= "--envelope-center" flag)
          (recur (rest more) (assoc opts :envelope-center (parse-double* (first more))))

          (= "--envelope-width" flag)
          (recur (rest more) (assoc opts :envelope-width (parse-double* (first more))))

          (= "--envelope-change-center" flag)
          (recur (rest more) (assoc opts :envelope-change-center (parse-double* (first more))))

          (= "--envelope-change-width" flag)
          (recur (rest more) (assoc opts :envelope-change-width (parse-double* (first more))))

          (= "--envelope-change" flag)
          (recur more (assoc opts :envelope-change true))

          (= "--hexagram-log" flag)
          (recur (rest more) (assoc opts :hexagram-log (first more)))

          (= "--iiching-root" flag)
          (recur (rest more) (assoc opts :iiching-root (first more)))

          (= "--iiching-manifest" flag)
          (recur (rest more) (assoc opts :iiching-manifest (first more)))

          (= "--curriculum-gate" flag)
          (recur more (assoc opts :curriculum-gate true))

          (= "--log" flag)
          (recur (rest more) (assoc opts :log (first more)))

          (= "--heartbeat" flag)
          (recur (rest more) (assoc opts :heartbeat (parse-int (first more))))

          (= "--on-error" flag)
          (recur (rest more) (assoc opts :on-error (some-> (first more) keyword)))

          (= "--tap" flag)
          (recur more (assoc opts :tap true))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-nth [^java.util.Random rng coll]
  (when (seq coll)
    (nth coll (rng-int rng (count coll)))))

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

(defn- score-xenotype [xeno-specs result ratchet-context ratchet-library]
  (when (seq xeno-specs)
    (let [scores (mapv (fn [spec]
                         (let [spec (cond-> spec
                                      ratchet-context (assoc :ratchet ratchet-context)
                                      ratchet-library (assoc :ratchet-library ratchet-library
                                                             :ct-template (:ct-template ratchet-library)))]
                           (:score (xenotype/score-run spec result))))
                       xeno-specs)
          mean (/ (reduce + 0.0 scores) (double (count scores)))]
      {:scores scores
       :mean mean})))

(defn- band-score [x center width]
  (let [x (double (or x 0.0))
        center (double (or center 0.0))
        width (double (or width 1.0))]
    (if (pos? width)
      (max 0.0 (- 1.0 (/ (Math/abs (- x center)) width)))
      0.0)))

(defn- envelope-score
  [summary {:keys [entropy-center entropy-width change-center change-width include-change?]}]
  (let [entropy (band-score (:avg-entropy-n summary) entropy-center entropy-width)
        change (band-score (:avg-change summary) change-center change-width)
        parts (if include-change? [entropy change] [entropy])
        avg (if (seq parts) (/ (reduce + 0.0 parts) (double (count parts))) 0.0)]
    (* 100.0 avg)))

(defn- triad-score [summary hex-score]
  (let [gen-score (double (or (:gen-composite-score summary) (:composite-score summary) 0.0))
        phe-score (double (or (:phe-composite-score summary) (:composite-score summary) 0.0))
        exo-score (double (or hex-score 0.0))]
    (/ (+ gen-score phe-score exo-score) 3.0)))

(defn- apply-exotype-overrides
  [exotype {:keys [update-prob match-threshold]}]
  (let [params (cond-> (:params exotype)
                 (some? update-prob) (assoc :update-prob update-prob)
                 (some? match-threshold) (assoc :match-threshold match-threshold))]
    (assoc exotype :params params)))

(defn- evaluate-exotype
  [exotype length generations ^java.util.Random rng xeno-specs xeno-weight context-depth ratchet-context ratchet-library hex-opts score-mode envelope-opts exotype-overrides]
  (let [exotype (apply-exotype-overrides exotype exotype-overrides)
        genotype (rng-sigil-string rng length)
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
        shift (register-shift/register-shift-summary result)
        shift-score (double (or (:shift/composite shift) 0.0))
        trigram (trigram/score-early-late (:phe-history result))
        trigram-score (double (or (:score trigram) 0.0))
        envelope (envelope-score summary envelope-opts)
        xeno (score-xenotype xeno-specs result ratchet-context ratchet-library)
        xeno-score (when xeno (* 100.0 (double (or (:mean xeno) 0.0))))
        hex-transition (hex-metrics/run->transition-matrix result)
        hex-signature (hex-metrics/transition-matrix->signature hex-transition)
        hex-class (hex-metrics/signature->hexagram-class hex-signature)
        hex-score (* 100.0 (hex-metrics/hexagram-fitness hex-class))
        base-score (cond
                     (= score-mode :triad) (triad-score summary hex-score)
                     (= score-mode :shift) shift-score
                     :else short-score)
        xeno-blend (blend-score base-score xeno-score xeno-weight)
        final-score (if (or (= score-mode :triad) (= score-mode :shift))
                      xeno-blend
                      (blend-score xeno-blend hex-score (:hexagram-weight hex-opts)))
        predicted (hex-metrics/params->hexagram-class (:params exotype))]
    (when (seq (:hexagram-log hex-opts))
      (hex-log/append-entry!
       (:hexagram-log hex-opts)
       (hex-log/psr-entry {:sigil (:sigil exotype)
                           :exotype-params (:params exotype)
                           :predicted-hexagram predicted
                           :reason "params-heuristic"
                           :generation generations
                           :seed seed}))
      (hex-log/append-entry!
       (:hexagram-log hex-opts)
       (hex-log/pur-entry {:sigil (:sigil exotype)
                           :actual-dynamics (hex-metrics/run->metrics result)
                           :actual-hexagram hex-class
                           :match? (= predicted hex-class)
                           :generation generations
                           :seed seed})))
    {:result result
     :summary summary
     :short-score short-score
     :triad-score (when (= score-mode :triad) (triad-score summary hex-score))
     :shift shift
     :shift-score (when (= score-mode :shift) shift-score)
     :trigram trigram
     :trigram-score trigram-score
     :envelope-score envelope
     :xeno xeno
     :hexagram {:class hex-class
                :signature hex-signature
                :score hex-score
                :predicted predicted}
     :final-score final-score
     :seed seed
     :exotype exotype}))

(defn- program-template [exotype]
  (if (= :super (:tier exotype))
    :contextual-mutate+mix
    :contextual-mutate))

(defn- log-entry [run-id exotype length generations eval context-depth ratchet-context ratchet-library]
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
           :hex (get-in eval [:hexagram :score])
           :triad (:triad-score eval)
           :shift (:shift-score eval)
           :trigram (:trigram-score eval)
           :envelope (:envelope-score eval)
           :final (:final-score eval)}
   :ratchet ratchet-context
   :ratchet-library ratchet-library
   :hexagram (select-keys (:hexagram eval) [:class :predicted])
   :summary (:summary eval)
   :shift (:shift eval)
   :trigram (:trigram eval)})

(defn- append-log! [path entry]
  (spit path (str (pr-str entry) "\n") :append true))

(defn- avg [xs]
  (when (seq xs)
    (/ (reduce + 0.0 xs) (double (count xs)))))

(defn- stddev
  [xs]
  (when (seq xs)
    (let [m (avg xs)
          var (/ (reduce + 0.0 (map (fn [x]
                                      (let [d (- (double x) (double m))]
                                        (* d d)))
                                    xs))
                 (double (count xs)))]
      (Math/sqrt var))))

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
            :stddev (stddev finals)
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
                                  #(mutate-exotype rng (rng-nth rng survivors) tier)))]
    (vec (concat (map #(dissoc % :fitness) survivors) offspring))))

(defn- default-iiching-root []
  (let [candidates ["../futon3/library/iiching" "futon3/library/iiching"]]
    (some (fn [path]
            (when (.exists (io/file path)) path))
          candidates)))

(defn evolve-exotypes
  [{:keys [runs length generations pop update-every tier seed xeno-spec xeno-weight log
           context-depth curriculum-gate tap hexagram-weight hexagram-log score-mode
           envelope-center envelope-width envelope-change-center envelope-change-width
           envelope-change update-prob match-threshold
           iiching-root iiching-manifest heartbeat on-error argv]}]
  (let [runs (or runs default-runs)
        length (or length default-length)
        generations (or generations default-generations)
        pop (or pop default-pop)
        update-every (or update-every default-update-every)
        tier (or tier :both)
        context-depth (max 1 (int (or context-depth 1)))
        xeno-weight (double (or xeno-weight 0.0))
        score-mode (or score-mode :legacy)
        envelope-opts {:entropy-center (or envelope-center 0.6)
                       :entropy-width (or envelope-width 0.25)
                       :change-center (or envelope-change-center 0.2)
                       :change-width (or envelope-change-width 0.15)
                       :include-change? (if (contains? #{true false} envelope-change)
                                          envelope-change
                                          true)}
        rng (java.util.Random. (long (or seed 4242)))
        xeno-specs (load-xeno-specs xeno-spec)
        hex-opts {:hexagram-weight (double (or hexagram-weight 0.0))
                  :hexagram-log hexagram-log}
        iiching-root (or iiching-root (default-iiching-root))
        iiching-manifest (or iiching-manifest "futon5/resources/exotype-program-manifest.edn")
        library-opts {:iiching-root iiching-root
                      :manifest iiching-manifest}
        on-error (or on-error :fail)
        heartbeat (when (and heartbeat (pos? heartbeat)) heartbeat)
        bundle {:schema/version 1
                :experiment/id :exoevolve
                :event :bundle
                :timestamp (System/currentTimeMillis)
                :cwd (.getAbsolutePath (io/file "."))
                :argv argv
                :log log}
        run-meta {:schema/version 1
                  :experiment/id :exoevolve
                  :event :meta
                  :seed (long (or seed 4242))
                  :opts {:runs runs
                         :length length
                         :generations generations
                         :pop pop
                         :update-every update-every
                         :tier tier
                         :context-depth context-depth
                         :xeno-spec xeno-spec
                         :xeno-weight xeno-weight
                         :score-mode score-mode
                         :hexagram-weight (:hexagram-weight hex-opts)
                         :hexagram-log hexagram-log
                         :envelope envelope-opts
                         :update-prob update-prob
                         :match-threshold match-threshold
                         :curriculum-gate (boolean curriculum-gate)
                         :iiching-root iiching-root
                         :iiching-manifest iiching-manifest
                         :heartbeat heartbeat
                         :on-error on-error}}]
    (when log
      (append-log! log bundle)
      (append-log! log run-meta))
    (loop [i 0
           window 0
           prev-window nil
           ratchet-state (ratchet/init-state)
           ratchet-context nil
           population (vec (repeatedly pop #(pick-exotype rng tier)))
           batch []
           errors 0]
      (if (= i runs)
        (do
          (when log
            (append-log! log {:schema/version 1
                              :experiment/id :exoevolve
                              :event :done
                              :runs runs
                              :windows window
                              :errors errors}))
          {:population population})
        (let [exotype (rng-nth rng population)
              ratchet-library (when iiching-root
                                (ratchet-lib/evidence-for library-opts (:sigil exotype) (:tier exotype)))
              exotype-overrides {:update-prob update-prob
                                 :match-threshold match-threshold}
              ;; Run evaluation in try/catch, return result map
              result (try
                       (let [eval (evaluate-exotype exotype length generations rng xeno-specs xeno-weight context-depth ratchet-context ratchet-library hex-opts score-mode envelope-opts exotype-overrides)
                             entry (log-entry (inc i) (:exotype eval) length generations eval context-depth ratchet-context ratchet-library)
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
                                                (let [threshold (curriculum/curriculum-threshold window' nil)]
                                                  (ratchet/ratchet-context prev-window stats
                                                                           {:threshold threshold
                                                                            :window window'
                                                                            :gate? curriculum-gate})))]
                         (when log
                           (append-log! log entry))
                         (when (and log update?)
                           (append-log! log (window-log-entry window' stats delta)))
                         (when (and log heartbeat (zero? (mod (inc i) heartbeat)))
                           (append-log! log {:schema/version 1
                                             :experiment/id :exoevolve
                                             :event :checkpoint
                                             :run/id (inc i)
                                             :window window'
                                             :errors errors}))
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
                         (when (and heartbeat (zero? (mod (inc i) heartbeat)))
                           (println (format "exo heartbeat @ %d | window %d | errors %d"
                                            (inc i) window' errors)))
                         {:ok true
                          :i (inc i)
                          :window window'
                          :prev-window prev-window'
                          :ratchet-state ratchet-state'
                          :ratchet-context ratchet-context'
                          :population population'
                          :batch batch''
                          :errors errors})
                       (catch Exception e
                         (let [entry {:schema/version 1
                                      :experiment/id :exoevolve
                                      :event :error
                                      :run/id (inc i)
                                      :exotype (select-keys exotype [:sigil :tier :params])
                                      :error {:class (str (class e))
                                              :message (.getMessage e)}}]
                           (when log
                             (append-log! log entry))
                           (when tap
                             (tap> entry))
                           (if (= on-error :continue)
                             (do
                               (println (format "exo error @ %d | %s"
                                                (inc i) (.getMessage e)))
                               {:ok true
                                :i (inc i)
                                :window window
                                :prev-window prev-window
                                :ratchet-state ratchet-state
                                :ratchet-context ratchet-context
                                :population population
                                :batch batch
                                :errors (inc errors)})
                             {:ok false :error e}))))]
          ;; Recur outside try/catch based on result
          (if (:ok result)
            (recur (:i result) (:window result) (:prev-window result)
                   (:ratchet-state result) (:ratchet-context result)
                   (:population result) (:batch result) (:errors result))
            (throw (:error result))))))))

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
        (evolve-exotypes (assoc opts :argv args))
        (println "Done.")))))
