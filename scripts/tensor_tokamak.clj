(ns scripts.tensor-tokamak
  "Single-run tensor tokamak experiment with adaptive interest definitions."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.ct.exotype-tilt :as exo-tilt]
            [futon5.ct.interest :as interest]
            [futon5.ct.tensor-stepper :as stepper]))

(def ^:private default-seed 7)
(def ^:private default-length 128)
(def ^:private default-generations 20)
(def ^:private default-rule-sigil "手")
(def ^:private default-explore-rate 0.35)
(def ^:private default-geno-every 1)
(def ^:private default-pheno-every 2)
(def ^:private default-exo-every 2)
(def ^:private default-geno-flips 2)
(def ^:private default-pheno-flips 4)
(def ^:private default-exo-assimilate-threshold 0.02)
(def ^:private default-exo-assimilate-streak 2)
(def ^:private default-exo-assimilate-rate 0.12)
(def ^:private default-exo-stagnation-threshold 3)
(def ^:private default-exo-novelty-floor 0.03)
(def ^:private default-exo-total-floor 0.20)
(def ^:private default-exo-credit-window 4)
(def ^:private default-exo-credit-blend 0.25)

(def ^:private all-sigils
  (mapv :sigil (ca/sigil-entries)))

(defn- usage []
  (str/join
   "\n"
   ["usage: bb -m scripts.tensor-tokamak [options]"
    ""
    "Options:"
    "  --seed N                RNG seed (default 7)."
    "  --length N              Genotype width when random (default 128)."
    "  --generations N         Tokamak generations (default 20)."
    "  --init-genotype STRING  Initial genotype."
    "  --init-rule-sigil SIGIL Initial rule sigil (default 手)."
    "  --phenotype STRING      Fixed phenotype mask."
    "  --with-phenotype        Use deterministic random phenotype."
    "  --pheno-evolve          Evolve phenotype from genotype each generation."
    "  --diagram-id ID         Initial diagram id."
    "  --explore-rate P        Exotype tilt attempt probability 0..1 (default 0.35)."
    "  --geno-every N          Genotype mutation cadence (default 1)."
    "  --pheno-every N         Phenotype mutation cadence (default 2)."
    "  --exo-every N           Exotype mutation cadence (default 2)."
    "  --geno-flips N          Cells to mutate per genotype intervention (default 2)."
    "  --pheno-flips N         Bits to mutate per phenotype intervention (default 4)."
    "  --exo-assim-thresh X    Exotype credit threshold for assimilation (default 0.02)."
    "  --exo-assim-streak N    Consecutive hits required to assimilate (default 2)."
    "  --exo-assim-rate X      Assimilation learning rate (default 0.12)."
    "  --exo-stagnation N      Stagnation streak trigger for exotype (default 3)."
    "  --exo-novelty-floor X   Novelty trigger floor for exotype (default 0.03)."
    "  --exo-total-floor X     Total-score trigger floor for exotype (default 0.20)."
    "  --exo-credit-window N   Window size for delayed exotype credit (default 4)."
    "  --exo-credit-blend X    Immediate-vs-delayed exotype credit blend (default 0.25)."
    "  --out PATH              Output EDN path."
    "  --report PATH           Output Markdown report path."
    "  --help                  Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-float [s]
  (try (Double/parseDouble s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (case flag
          "--seed" (recur (rest more) (assoc opts :seed (parse-int (first more))))
          "--length" (recur (rest more) (assoc opts :length (parse-int (first more))))
          "--generations" (recur (rest more) (assoc opts :generations (parse-int (first more))))
          "--init-genotype" (recur (rest more) (assoc opts :init-genotype (first more)))
          "--init-rule-sigil" (recur (rest more) (assoc opts :init-rule-sigil (first more)))
          "--phenotype" (recur (rest more) (assoc opts :phenotype (first more)))
          "--with-phenotype" (recur more (assoc opts :with-phenotype true))
          "--pheno-evolve" (recur more (assoc opts :pheno-evolve true))
          "--diagram-id" (recur (rest more) (assoc opts :diagram-id (keyword (first more))))
          "--explore-rate" (recur (rest more) (assoc opts :explore-rate (parse-float (first more))))
          "--geno-every" (recur (rest more) (assoc opts :geno-every (parse-int (first more))))
          "--pheno-every" (recur (rest more) (assoc opts :pheno-every (parse-int (first more))))
          "--exo-every" (recur (rest more) (assoc opts :exo-every (parse-int (first more))))
          "--geno-flips" (recur (rest more) (assoc opts :geno-flips (parse-int (first more))))
          "--pheno-flips" (recur (rest more) (assoc opts :pheno-flips (parse-int (first more))))
          "--exo-assim-thresh" (recur (rest more) (assoc opts :exo-assim-thresh (parse-float (first more))))
          "--exo-assim-streak" (recur (rest more) (assoc opts :exo-assim-streak (parse-int (first more))))
          "--exo-assim-rate" (recur (rest more) (assoc opts :exo-assim-rate (parse-float (first more))))
          "--exo-stagnation" (recur (rest more) (assoc opts :exo-stagnation (parse-int (first more))))
          "--exo-novelty-floor" (recur (rest more) (assoc opts :exo-novelty-floor (parse-float (first more))))
          "--exo-total-floor" (recur (rest more) (assoc opts :exo-total-floor (parse-float (first more))))
          "--exo-credit-window" (recur (rest more) (assoc opts :exo-credit-window (parse-int (first more))))
          "--exo-credit-blend" (recur (rest more) (assoc opts :exo-credit-blend (parse-float (first more))))
          "--out" (recur (rest more) (assoc opts :out (first more)))
          "--report" (recur (rest more) (assoc opts :report (first more)))
          "--help" (recur more (assoc opts :help true))
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- ensure-parent! [path]
  (when-let [p (.getParentFile (io/file path))]
    (.mkdirs p))
  path)

(defn- random-sigil [^java.util.Random rng]
  (nth all-sigils (.nextInt rng (count all-sigils))))

(defn- random-genotype [^java.util.Random rng length]
  (apply str (repeatedly (long length) #(random-sigil rng))))

(defn- random-phenotype [^java.util.Random rng length]
  (apply str (repeatedly (long length) #(if (zero? (.nextInt rng 2)) \0 \1))))

(defn- clamp01 [x]
  (let [x (double (or x 0.0))]
    (cond
      (< x 0.0) 0.0
      (> x 1.0) 1.0
      :else x)))

(declare different-first)

(defn- mutate-genotype-row
  [row ^java.util.Random rng flips]
  (let [cells (vec (seq (str (or row ""))))
        n (count cells)
        flips (long (max 0 (or flips 0)))]
    (if (or (zero? n) (zero? flips))
      (apply str cells)
      (let [k (min n flips)
            idxs (loop [acc #{}]
                   (if (= (count acc) k)
                     acc
                     (recur (conj acc (.nextInt rng n)))))]
        (apply str
               (reduce (fn [acc idx]
                         (let [current (str (nth acc idx))
                               replacement (or (different-first current all-sigils)
                                               current)]
                           (assoc acc idx replacement)))
                       cells
                       idxs))))))

(defn- mutate-phenotype-row
  [row ^java.util.Random rng flips]
  (let [bits (vec (seq (str (or row ""))))
        n (count bits)
        flips (long (max 0 (or flips 0)))]
    (if (or (zero? n) (zero? flips))
      (apply str bits)
      (let [k (min n flips)
            idxs (loop [acc #{}]
                   (if (= (count acc) k)
                     acc
                     (recur (conj acc (.nextInt rng n)))))]
        (apply str
               (reduce (fn [acc idx]
                         (assoc acc idx (if (= \1 (nth acc idx)) \0 \1)))
                       bits
                       idxs))))))

(defn- different-first
  [current xs]
  (first (remove #(= % current) xs)))

(def ^:private exo-reason-keys
  ["stagnation" "regime" "novelty+total-floor" "rewind" "none"])

(defn- normalize-positive-map
  [m]
  (let [m (into {} (map (fn [[k v]] [k (max 0.01 (double (or v 0.0)))]) m))
        tot (reduce + 0.0 (vals m))]
    (if (pos? tot)
      (into {} (map (fn [[k v]] [k (/ (double v) tot)]) m))
      m)))

(defn- initial-exo-genotype
  [_physics]
  {:version 1
   :program (merge exo-tilt/default-program
                   {:pattern-weights (normalize-positive-map
                                      (:pattern-weights exo-tilt/default-program))})
   :reason-bias (normalize-positive-map
                 (zipmap exo-reason-keys
                         [0.34 0.30 0.20 0.10 0.06]))
   :streak {:pos 0 :neg 0}})

(defn- reason-key
  [reason]
  (some-> (str reason)
          (str/replace #"^rewind/" "")))

(defn- update-bias
  [m k delta]
  (let [k (if (keyword? k) k (str k))
        m (or m {})
        v (+ (double (or (get m k) 0.0)) (double delta))
        v (max 0.01 v)]
    (assoc m k v)))

(defn- severity-scale
  [severity]
  (case severity
    :high 1.0
    :medium 0.75
    :low 0.5
    0.0))

(defn- decode-exo-program
  [exo-genotype trigger]
  (let [program0 (merge exo-tilt/default-program (or (:program exo-genotype) {}))
        weights (normalize-positive-map
                 (or (:pattern-weights program0)
                     (:pattern-weights exo-tilt/default-program)))
        reason (reason-key (:reason trigger))
        reason-bias (double (or (get-in exo-genotype [:reason-bias reason]) 0.2))
        gain (clamp01 (* (severity-scale (:severity trigger)) reason-bias))
        strength-mult (+ 0.65 (* 0.70 gain))
        cell-mult (+ 0.50 (* 0.80 gain))]
    (-> program0
        (assoc :pattern-weights weights)
        (update :global-strength
                (fn [x]
                  (clamp01 (* (double (or x 0.0))
                              strength-mult))))
        (update :max-cells-ratio
                (fn [x]
                  (let [x (double (or x 0.0))]
                    (clamp01 (* x cell-mult))))))))

(defn- targeted-patterns
  [mutation]
  (let [hits (or (get-in mutation [:exotype :tilt :hits]) {})
        triggered (or (get-in mutation [:exotype :tilt :triggered]) {})]
    (->> (concat (keys hits) (keys triggered))
         distinct
         vec)))

(defn- assimilate-exo-genotype
  [exo-genotype mutation {:keys [threshold streak-needed rate]
                          :or {threshold default-exo-assimilate-threshold
                               streak-needed default-exo-assimilate-streak
                               rate default-exo-assimilate-rate}}]
  (let [exo-genotype (or exo-genotype (initial-exo-genotype nil))
        exo-attempted? (boolean (or (get-in mutation [:exotype :attempted?])
                                    (get-in mutation [:exotype :applied?])))
        exo-selected? (boolean (get-in mutation [:exotype :selected?]))
        attempt-delta (double (or (get-in mutation [:exotype :attempt-delta]) 0.0))
        selected-credit (double (or (get-in mutation [:credit :by-layer :exotype]) 0.0))
        exo-credit (if exo-selected? selected-credit attempt-delta)
        pos-hit? (and exo-attempted? (> exo-credit (double threshold)))
        neg-hit? (and exo-attempted? (< exo-credit (- (double threshold))))
        old-streak (or (:streak exo-genotype) {:pos 0 :neg 0})
        streak (cond
                 pos-hit? {:pos (inc (long (:pos old-streak)))
                           :neg 0}
                 neg-hit? {:pos 0
                           :neg (inc (long (:neg old-streak)))}
                 :else {:pos 0 :neg 0})
        exo-genotype (assoc exo-genotype :streak streak)
        direction (cond
                    (>= (:pos streak) (long streak-needed)) :toward
                    (>= (:neg streak) (long streak-needed)) :away
                    :else nil)]
    (if-not direction
      {:exo-genotype exo-genotype
       :assimilation {:applied? false
                      :direction :none
                      :attempted? exo-attempted?
                      :selected? exo-selected?
                      :streak streak
                      :credit exo-credit
                      :version-before (:version exo-genotype)
                      :version-after (:version exo-genotype)}}
      (let [alpha (max 0.01 (double rate))
            rkey (reason-key (get-in mutation [:exotype :reason]))
            pids (or (seq (targeted-patterns mutation))
                     (keys (or (get-in exo-genotype [:program :pattern-weights]) {})))
            hits (or (get-in mutation [:exotype :tilt :hits]) {})
            sign (if (= direction :toward) 1.0 -1.0)
            exo-genotype'
            (-> exo-genotype
                (update :reason-bias update-bias rkey (* sign alpha))
                (update :reason-bias normalize-positive-map)
                (update-in [:program :pattern-weights]
                           (fn [w]
                             (->> pids
                                  (reduce (fn [acc pid]
                                            (let [m (max 1 (long (or (get hits pid) 1)))]
                                              (update-bias acc pid (* sign alpha (double m)))))
                                          (or w {}))
                                  normalize-positive-map)))
                (update-in [:program :global-strength]
                           (fn [x]
                             (clamp01 (+ (double (or x 0.0))
                                         (* sign alpha 0.06)))))
                (update-in [:program :max-cells-ratio]
                           (fn [x]
                             (clamp01 (+ (double (or x 0.0))
                                         (* sign alpha 0.04)))))
                (update :version (fnil inc 1))
                (assoc :streak {:pos 0 :neg 0}))]
        {:exo-genotype exo-genotype'
         :assimilation {:applied? true
                        :direction direction
                        :attempted? exo-attempted?
                        :selected? exo-selected?
                        :reason rkey
                        :streak streak
                        :credit exo-credit
                        :version-before (:version exo-genotype)
                        :version-after (:version exo-genotype')}}))))

(defn- exo-trigger-signal
  [{:keys [eval descriptor stagnation-count novelty-floor total-floor stagnation-threshold]}]
  (let [regime (:regime descriptor)
        n (double (or (:novelty eval) 1.0))
        t (double (or (:total eval) 1.0))
        stagnation-count (long (or stagnation-count 0))
        novelty-floor (double (or novelty-floor default-exo-novelty-floor))
        total-floor (double (or total-floor default-exo-total-floor))
        stagnation-threshold (long (or stagnation-threshold default-exo-stagnation-threshold))]
    (cond
      (>= stagnation-count stagnation-threshold)
      {:trigger? true :severity :high :reason :stagnation}

      (#{:freeze :magma} regime)
      {:trigger? true :severity :medium :reason :regime}

      (and (< n novelty-floor) (< t total-floor))
      {:trigger? true :severity :low :reason :novelty+total-floor}

      :else
      {:trigger? false :severity :none :reason :none})))

(defn- every-n?
  [g n]
  (let [n (long (or n 0))]
    (and (pos? n) (zero? (mod g n)))))

(defn- maybe-evolve-phenotype-candidate
  [state event pheno-evolve?]
  (if (and pheno-evolve? (seq (:phe-history state)))
    (let [current-phe (last (:phe-history state))
          next-phe (ca/evolve-phenotype-against-genotype (:row-before event) current-phe)
          state' (stepper/set-current-phenotype state next-phe)
          summary' (stepper/history-summary state')]
      {:state state'
       :event (assoc event
                     :summary (:summary summary')
                     :episode (:episode summary')
                     :phenotype-after next-phe)})
    {:state state
     :event event}))

(defn- apply-layer-mutations
  [state g prev-eval prev-descriptor exo-genotype stagnation-count ^java.util.Random rng
   {:keys [explore-rate geno-every pheno-every exo-every geno-flips pheno-flips
           exo-stagnation exo-novelty-floor exo-total-floor]}]
  (let [geno-cadence? (every-n? g geno-every)
        pheno-cadence? (and (seq (:phe-history state)) (every-n? g pheno-every))
        exo-cadence? (every-n? g exo-every)
        current-row (or (last (:gen-history state)) "")
        geno-row (if geno-cadence?
                   (mutate-genotype-row current-row rng geno-flips)
                   current-row)
        geno-applied? (and geno-cadence? (not= geno-row current-row))
        state (if geno-applied?
                (stepper/set-current-genotype state geno-row)
                state)
        current-phe (when (seq (:phe-history state))
                      (last (:phe-history state)))
        phe-row (if (and pheno-cadence? current-phe)
                  (mutate-phenotype-row current-phe rng pheno-flips)
                  current-phe)
        pheno-applied? (and pheno-cadence? current-phe (not= phe-row current-phe))
        state (if pheno-applied?
                (stepper/set-current-phenotype state phe-row)
                state)
        trigger (exo-trigger-signal {:eval prev-eval
                                     :descriptor prev-descriptor
                                     :stagnation-count stagnation-count
                                     :novelty-floor exo-novelty-floor
                                     :total-floor exo-total-floor
                                     :stagnation-threshold exo-stagnation})
        exo-program (decode-exo-program exo-genotype trigger)
        exo-attempt? (and exo-cadence?
                          (true? (:trigger? trigger))
                          (< (.nextDouble rng) (max 0.05 (clamp01 explore-rate))))]
    {:state state
     :layers {:genotype {:applied? geno-applied?
                         :cadence? geno-cadence?
                         :flips geno-flips
                         :before current-row
                         :after geno-row}
              :phenotype {:applied? pheno-applied?
                          :cadence? pheno-cadence?
                          :flips pheno-flips
                          :before current-phe
                          :after phe-row}
              :exotype {:applied? false
                        :attempted? exo-attempt?
                        :selected? false
                        :cadence? exo-cadence?
                        :trigger trigger
                        :reason (name (or (:reason trigger) :none))
                        :before (:program exo-genotype)
                        :after (:program exo-genotype)
                        :program exo-program
                        :tilt nil}}}))

(defn- delayed-exo-delta
  [total-history current-total window]
  (let [window (max 1 (long (or window default-exo-credit-window)))
        tail (take-last window (vec (or total-history [])))]
    (if (seq tail)
      (- (double current-total)
         (/ (reduce + 0.0 tail) (double (count tail))))
      (double current-total))))

(defn- assign-layer-credit
  [delta exo-delta layers cumulative {:keys [exo-credit-blend]
                                      :or {exo-credit-blend default-exo-credit-blend}}]
  (let [active (->> layers
                    (keep (fn [[layer {:keys [applied?]}]]
                            (when applied? layer)))
                    vec)
        n (max 1 (count active))
        base-share (/ (double delta) (double n))
        delayed-share (/ (double exo-delta) (double n))
        blend (clamp01 exo-credit-blend)
        exo-share (+ (* blend base-share)
                     (* (- 1.0 blend) delayed-share))
        by-layer (into {}
                       (map (fn [layer]
                              [layer (if (= layer :exotype) exo-share base-share)]))
                       active)
        cumulative' (merge-with + (or cumulative {}) by-layer)]
    {:delta-total (double delta)
     :exo-delta (double exo-delta)
     :active active
     :by-layer by-layer
     :cumulative cumulative'}))

(defn- exo-accept-decision
  [{:keys [eval-base eval-tilt trigger]}]
  (let [delta-total (- (double (:total eval-tilt))
                       (double (:total eval-base)))
        delta-quality (- (double (:quality eval-tilt))
                         (double (:quality eval-base)))
        delta-novelty (- (double (:novelty eval-tilt))
                         (double (:novelty eval-base)))
        delta-surprise (- (double (:surprise eval-tilt))
                          (double (:surprise eval-base)))
        severity (or (:severity trigger) :none)
        slack (case severity
                :high 0.030
                :medium 0.015
                :low 0.006
                0.0)
        exploratory-gain (+ (* 0.7 (max 0.0 delta-novelty))
                            (* 0.3 (max 0.0 delta-surprise)))]
    (cond
      (>= delta-total 0.0)
      {:accept? true
       :mode :strict-improve}

      (and (true? (:trigger? trigger))
           (>= delta-total (- slack))
           (>= delta-quality -0.030)
           (or (= severity :high)
               (>= exploratory-gain 0.010)))
      {:accept? true
       :mode :bounded-explore}

      :else
      {:accept? false
       :mode :reject})))

(defn- fmt [x]
  (format "%.3f" (double (or x 0.0))))

(defn- md-report
  [result]
  (let [{:keys [config summary final definition-history ledger exo-genotype-history]} result
        credit (:layer-credit-cumulative summary)
        exo-attempts (count (filter #(get-in % [:mutation :exotype :attempted?]) ledger))
        exo-selected (count (filter #(get-in % [:mutation :exotype :selected?]) ledger))
        exo-cells (reduce + 0 (map #(long (or (get-in % [:mutation :exotype :tilt :changed-cells]) 0))
                                   ledger))
        last-exo (last exo-genotype-history)
        lines
        (concat
         ["# Tensor Tokamak Session"
          ""
          (str "- Seed: `" (:seed config) "`")
          (str "- Length: `" (:length config) "`")
          (str "- Generations requested: `" (:generations config) "`")
          (str "- Phenotype enabled: `" (boolean (:phenotype config)) "`")
          (str "- Explore rate: `" (fmt (:explore-rate config)) "`")
          (str "- Schedule: geno/phen/exo = `"
               (:geno-every config) "/"
               (:pheno-every config) "/"
               (:exo-every config) "`")
          (str "- Mutation size: geno/phen = `"
               (:geno-flips config) "/"
               (:pheno-flips config) "`")
          (str "- Exo trigger: stagnation `" (:exo-stagnation config)
               "`, novelty-floor `" (fmt (:exo-novelty-floor config))
               "`, total-floor `" (fmt (:exo-total-floor config)) "`")
          (str "- Exo credit: window `" (:exo-credit-window config)
               "`, blend `" (fmt (:exo-credit-blend config)) "`")
          (str "- Exo assimilation: threshold `" (fmt (:exo-assim-thresh config))
               "`, streak `" (:exo-assim-streak config)
               "`, rate `" (fmt (:exo-assim-rate config)) "`")
          ""
          "## Outcome"
          ""
          (str "- Best total score: `" (fmt (:best-total summary)) "`")
          (str "- Best generation: `" (:best-generation summary) "`")
          (str "- Final total score: `" (fmt (:final-total summary)) "`")
          (str "- Final regime: `" (name (or (:final-regime summary) :unknown)) "`")
          (str "- Rewinds used: `" (:rewinds summary) "`")
          (str "- Definition versions: `" (count definition-history) "`")
          (str "- Exo-genotype versions: `" (count exo-genotype-history) "`")
          (str "- Exo attempts/selected: `" exo-attempts "/" exo-selected "`")
          (str "- Exo changed cells total: `" exo-cells "`")
          (str "- Layer credit cumulative: `" (pr-str credit) "`")
          ""
          "## Final Physics"
          ""
          (str "- Rule sigil: `" (get-in final [:physics :rule-sigil]) "`")
          (str "- Diagram: `" (name (get-in final [:physics :diagram-id])) "`")
          (str "- Step opts: `" (pr-str (get-in final [:physics :step-opts])) "`")
          (str "- Exo-gene top pattern weights: `"
               (->> (get-in last-exo [:program :pattern-weights] {})
                    (sort-by val >)
                    (take 5)
                    pr-str) "`")
          (str "- Exo-gene global-strength: `"
               (fmt (get-in last-exo [:program :global-strength])) "`")
          (str "- Exo-gene max-cells-ratio: `"
               (fmt (get-in last-exo [:program :max-cells-ratio])) "`")
          (str "- Exo-gene reason biases: `"
               (pr-str (get last-exo :reason-bias {})) "`")
          ""
          "## Ledger"
          ""
          "| g | rule | diagram | total | quality | novelty | surprise | regime | geno | pheno | exo-plastic | exo-assim | xeno | delta | exo-delta | rewind | interest-v | exo-gene-v |"
          "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"]
         (map (fn [{:keys [generation physics eval descriptor mutation rewind definition]}]
                (format "| %d | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s | %d | %d |"
                        generation
                        (:rule-sigil physics)
                        (name (:diagram-id physics))
                        (fmt (:total eval))
                        (fmt (:quality eval))
                        (fmt (:novelty eval))
                        (fmt (:surprise eval))
                        (name (or (:regime descriptor) :unknown))
                        (if (get-in mutation [:genotype :applied?]) "yes" "no")
                        (if (get-in mutation [:phenotype :applied?]) "yes" "no")
                        (str (or (get-in mutation [:exotype :reason]) "-")
                             " ["
                             (name (or (get-in mutation [:exotype :trigger :severity]) :none))
                             "/"
                             (name (or (get-in mutation [:exotype :trigger :reason]) :none))
                             "] a="
                             (if (get-in mutation [:exotype :attempted?]) "y" "n")
                             " s="
                             (if (get-in mutation [:exotype :selected?]) "y" "n")
                             " m="
                             (name (or (get-in mutation [:exotype :decision :mode]) :na))
                             " c="
                             (long (or (get-in mutation [:exotype :tilt :changed-cells]) 0)))
                        (name (or (get-in mutation [:exo-genotype :direction]) :none))
                        (if (get-in mutation [:xenotype :applied?]) "yes" "no")
                        (fmt (get-in mutation [:credit :delta-total]))
                        (fmt (get-in mutation [:credit :exo-delta]))
                        (if (get rewind :used?) "yes" "no")
                        (:version definition)
                        (long (or (get-in mutation [:exo-genotype :version-after]) 0))))
              ledger)
         ["" "## Notes" ""]
         (mapcat (fn [{:keys [generation notes]}]
                   (map (fn [n] (str "- g" generation ": " n)) notes))
                 ledger))]
    (str/join "\n" lines)))

(defn- run-tokamak
  [{:keys [seed length generations init-genotype init-rule-sigil phenotype with-phenotype pheno-evolve
           diagram-id explore-rate geno-every pheno-every exo-every
           geno-flips pheno-flips
           exo-assim-thresh exo-assim-streak exo-assim-rate
           exo-stagnation exo-novelty-floor exo-total-floor
           exo-credit-window exo-credit-blend]}]
  (let [seed (long (or seed default-seed))
        length (long (or length default-length))
        generations (long (or generations default-generations))
        explore-rate (clamp01 (or explore-rate default-explore-rate))
        geno-every (max 1 (long (or geno-every default-geno-every)))
        pheno-every (max 1 (long (or pheno-every default-pheno-every)))
        exo-every (max 1 (long (or exo-every default-exo-every)))
        geno-flips (max 0 (long (or geno-flips default-geno-flips)))
        pheno-flips (max 0 (long (or pheno-flips default-pheno-flips)))
        exo-assim-thresh (double (or exo-assim-thresh default-exo-assimilate-threshold))
        exo-assim-streak (max 1 (long (or exo-assim-streak default-exo-assimilate-streak)))
        exo-assim-rate (double (or exo-assim-rate default-exo-assimilate-rate))
        exo-stagnation (max 1 (long (or exo-stagnation default-exo-stagnation-threshold)))
        exo-novelty-floor (clamp01 (or exo-novelty-floor default-exo-novelty-floor))
        exo-total-floor (clamp01 (or exo-total-floor default-exo-total-floor))
        exo-credit-window (max 1 (long (or exo-credit-window default-exo-credit-window)))
        exo-credit-blend (clamp01 (or exo-credit-blend default-exo-credit-blend))
        rng (java.util.Random. seed)
        init-genotype (or init-genotype (random-genotype rng length))
        init-phenotype (or phenotype (when with-phenotype
                                       (random-phenotype rng (count init-genotype))))
        init-physics {:rule-sigil (or init-rule-sigil default-rule-sigil)
                      :diagram-id (or diagram-id (if init-phenotype :sigil-step-gated :sigil-step))
                      :step-opts {:backend :clj :wrap? false :boundary-bit 0}}
        state0 (stepper/init-stepper {:seed seed
                                      :genotype init-genotype
                                      :phenotype init-phenotype
                                      :physics init-physics})
        exo0 (initial-exo-genotype init-physics)]
    (loop [g 1
           state state0
           exo-genotype exo0
           definition (interest/initial-definition)
           archive []
           prev-descriptor nil
           prev-eval nil
           prev-total nil
           stagnation-count 0
           total-history []
           rewinds 0
           credit-cumulative {}
           definition-history [definition]
           exo-genotype-history [exo0]
           ledger []]
      (if (> g generations)
        (let [final-summary (stepper/history-summary state)
              best (apply max-key (fn [x] (double (get-in x [:eval :total] 0.0)))
                          (or (seq ledger) [{:generation 0 :eval {:total 0.0}}]))]
          {:config {:seed seed
                    :length length
                    :generations generations
                    :phenotype init-phenotype
                    :pheno-evolve? (boolean pheno-evolve)
                    :explore-rate explore-rate
                    :geno-every geno-every
                    :pheno-every pheno-every
                    :exo-every exo-every
                    :geno-flips geno-flips
                    :pheno-flips pheno-flips
                    :exo-assim-thresh exo-assim-thresh
                    :exo-assim-streak exo-assim-streak
                    :exo-assim-rate exo-assim-rate
                    :exo-stagnation exo-stagnation
                    :exo-novelty-floor exo-novelty-floor
                    :exo-total-floor exo-total-floor
                    :exo-credit-window exo-credit-window
                    :exo-credit-blend exo-credit-blend
                    :init-physics init-physics}
           :summary {:best-total (get-in best [:eval :total] 0.0)
                     :best-generation (:generation best)
                     :final-total (or prev-total 0.0)
                     :final-regime (:regime prev-descriptor)
                     :rewinds rewinds
                     :layer-credit-cumulative credit-cumulative}
           :final {:generation (:generation state)
                   :physics (:physics state)
                   :history-summary final-summary
                   :exo-genotype exo-genotype
                   :gen-history (:gen-history state)
                   :phe-history (:phe-history state)
                   :metrics-history (:metrics-history state)}
           :ledger ledger
           :definition-history definition-history
           :exo-genotype-history exo-genotype-history})
        (let [{:keys [state layers]}
              (apply-layer-mutations state g prev-eval prev-descriptor exo-genotype stagnation-count rng
                                     {:explore-rate explore-rate
                                      :geno-every geno-every
                                      :pheno-every pheno-every
                                      :exo-every exo-every
                                      :geno-flips geno-flips
                                      :pheno-flips pheno-flips
                                      :exo-stagnation exo-stagnation
                                      :exo-novelty-floor exo-novelty-floor
                                      :exo-total-floor exo-total-floor})
              physics-before (:physics state)
              {:keys [state event]} (stepper/step-once state)
              {:keys [state event]} (maybe-evolve-phenotype-candidate state event pheno-evolve)
              descriptor-base (interest/descriptor {:generation g
                                                    :summary (:summary event)
                                                    :episode (:episode event)})
              eval-base (interest/evaluate definition archive prev-descriptor descriptor-base)
              phenotype-now (when (seq (:phe-history state))
                              (last (:phe-history state)))
              exo-attempted? (boolean (get-in layers [:exotype :attempted?]))
              tilt-result (if exo-attempted?
                            (exo-tilt/apply-tilts (:row-before event)
                                                  (:row-after event)
                                                  phenotype-now
                                                  (get-in layers [:exotype :program])
                                                  rng)
                            {:row (:row-after event)
                             :changed-cells 0
                             :hits {}
                             :triggered {}
                             :max-cells 0
                             :program (get-in layers [:exotype :program])})
              tilt-changed? (pos? (long (or (:changed-cells tilt-result) 0)))
              state-tilt (if tilt-changed?
                           (stepper/set-current-genotype state (:row tilt-result))
                           state)
              summary-tilt (when tilt-changed?
                             (stepper/history-summary state-tilt))
              event-tilt-raw (if tilt-changed?
                               (-> event
                                   (assoc :row-after (:row tilt-result)
                                          :summary (:summary summary-tilt)
                                          :episode (:episode summary-tilt)
                                          :tilt tilt-result))
                               (assoc event :tilt tilt-result))
              {:keys [state event]} (maybe-evolve-phenotype-candidate state-tilt event-tilt-raw pheno-evolve)
              state-tilt state
              event-tilt event
              descriptor-tilt (if tilt-changed?
                                (interest/descriptor {:generation g
                                                      :summary (:summary event-tilt)
                                                      :episode (:episode event-tilt)})
                                descriptor-base)
              eval-tilt (if tilt-changed?
                         (interest/evaluate definition archive prev-descriptor descriptor-tilt)
                         eval-base)
              attempt-delta (if exo-attempted?
                              (- (double (:total eval-tilt))
                                 (double (:total eval-base)))
                              0.0)
              decision (if tilt-changed?
                         (exo-accept-decision {:eval-base eval-base
                                               :eval-tilt eval-tilt
                                               :trigger (get-in layers [:exotype :trigger])})
                         {:accept? false :mode :no-change})
              choose-tilt? (and tilt-changed? (:accept? decision))
              rewind-used? (and exo-attempted? tilt-changed? (not choose-tilt?))
              layers* (-> layers
                          (assoc-in [:exotype :applied?] choose-tilt?)
                          (assoc-in [:exotype :selected?] choose-tilt?)
                          (assoc-in [:exotype :decision] decision)
                          (assoc-in [:exotype :attempt-delta] attempt-delta)
                          (assoc-in [:exotype :tilt] tilt-result)
                          (assoc-in [:exotype :reason]
                                    (if exo-attempted?
                                      (str "tilt/" (or (get-in layers [:exotype :reason]) "none"))
                                      "tilt/inactive")))
              [state* event* descriptor* eval* rewind-info rewinds]
              (if choose-tilt?
                [state-tilt
                 event-tilt
                 descriptor-tilt
                 eval-tilt
                 nil
                 rewinds]
                [state
                 event
                 descriptor-base
                 eval-base
                 (when rewind-used?
                   {:used? true
                    :from-total (:total eval-tilt)
                    :to-total (:total eval-base)
                    :mutation (get-in layers* [:exotype :reason])})
                 (if rewind-used? (inc rewinds) rewinds)])
              adapt (interest/adapt-definition definition eval* descriptor*)
              definition' (:definition adapt)
              notes (:notes adapt)
              xenotype {:applied? true
                        :version-before (:version definition)
                        :version-after (:version definition')
                        :notes notes}
              delta (double (if (number? prev-total)
                              (- (double (:total eval*)) (double prev-total))
                              (:total eval*)))
              exo-delta (delayed-exo-delta total-history (:total eval*) exo-credit-window)
              credit (assign-layer-credit delta
                                          exo-delta
                                          (assoc layers* :xenotype xenotype)
                                          credit-cumulative
                                          {:exo-credit-blend exo-credit-blend})
              layers+ (assoc layers*
                             :xenotype xenotype
                             :credit credit)
              {:keys [exo-genotype assimilation]}
              (assimilate-exo-genotype exo-genotype
                                       layers+
                                       {:threshold exo-assim-thresh
                                        :streak-needed exo-assim-streak
                                        :rate exo-assim-rate})
              mutation (assoc layers*
                              :xenotype xenotype
                              :credit credit
                              :exo-genotype assimilation)
              stagnation-count' (if (and (number? prev-total)
                                         (<= (- (double (:total eval*)) (double prev-total))
                                             0.005))
                                  (inc stagnation-count)
                                  0)
              state-next state*
              entry {:generation g
                     :physics physics-before
                     :event event*
                     :descriptor descriptor*
                     :eval eval*
                     :mutation mutation
                     :rewind rewind-info
                     :definition definition
                     :notes notes}]
          (recur (inc g)
                 state-next
                 exo-genotype
                 definition'
                 (conj archive descriptor*)
                 descriptor*
                 eval*
                 (:total eval*)
                 stagnation-count'
                 (conj total-history (:total eval*))
                 rewinds
                 (:cumulative credit)
                 (conj definition-history definition')
                 (conj exo-genotype-history exo-genotype)
                 (conj ledger entry)))))))

(defn- write-edn! [path data]
  (ensure-parent! path)
  (spit path (pr-str data))
  path)

(defn- write-text! [path text]
  (ensure-parent! path)
  (spit path text)
  path)

(defn -main [& args]
  (let [{:keys [help unknown out report] :as opts} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do
                (println "Unknown option:" unknown)
                (println)
                (println (usage))
                (System/exit 2))
      :else
      (let [result (run-tokamak opts)
            out-path (or out "out/tokamak/tensor-tokamak.edn")
            report-path (or report "out/tokamak/tensor-tokamak.md")]
        (println "Wrote" (write-edn! out-path result))
        (println "Wrote" (write-text! report-path (md-report result)))
        (println (str "Best total=" (fmt (get-in result [:summary :best-total]))
                      " at generation " (get-in result [:summary :best-generation])
                      " | final total=" (fmt (get-in result [:summary :final-total]))
                      " | rewinds=" (get-in result [:summary :rewinds])))))))
