(ns futon5.mmca.runtime
  "MetaMetaCA runtime that wires compiled pattern operators into the CA loop."
  (:require [futon5.ca.core :as ca]
            [futon5.mmca.functor :as functor]
            [futon5.mmca.operators :as operators]))

(def ^:private default-generations 32)
(def ^:private default-kernel :mutating-template)
(def ^:private default-mode :god)

(defn- classic-mode? [mode]
  (= mode :classic))

(defn- ensure-genotype [genotype]
  (when-not (seq genotype)
    (throw (ex-info "MMCA runtime requires a starting genotype"
                    {:genotype genotype})))
  genotype)

(def ^:private operator-cache
  (delay (into {} (map (juxt :sigil identity) (functor/compile-patterns)))))

(defn- index-by-sigil [operators]
  (into {}
        (keep (fn [op]
                (when-let [sigil (:sigil op)]
                  [sigil op])))
        operators))

(defn- prefer-operator [compiled learned]
  (cond
    (nil? learned) compiled
    (nil? compiled) learned
    (= (:impl compiled) :pending) learned
    :else compiled))

(defn- merge-operator-index [compiled learned]
  (if (seq learned)
    (reduce (fn [acc [sigil op]]
              (let [existing (get acc sigil)]
                (assoc acc sigil (prefer-operator existing op))))
            compiled
            (index-by-sigil learned))
    compiled))

(defn- merge-operator-lists [base learned]
  (if (seq learned)
    (let [base-idx (index-by-sigil base)
          learned-idx (index-by-sigil learned)
          merged-idx (reduce (fn [acc [sigil op]]
                               (let [existing (get acc sigil)]
                                 (assoc acc sigil (prefer-operator existing op))))
                             base-idx
                             learned-idx)
          base-ordered (map (fn [op] (get merged-idx (:sigil op))) base)
          learned-only (remove #(contains? base-idx (:sigil %)) learned)]
      (vec (concat base-ordered learned-only)))
    (vec base)))

(defn- select-operators [sigils learned]
  (let [idx (merge-operator-index @operator-cache learned)]
    (->> sigils
         (map idx)
         (remove nil?)
         vec)))

(defn- distinct-sigils [sigils]
  (vec (distinct (remove nil? sigils))))

(defn- resolve-operators
  [{:keys [operators learned-operators pattern-sigils operator-scope] :as opts} genotype]
  (let [learned (vec (remove nil? learned-operators))]
    (cond
      (contains? opts :operators) (merge-operator-lists (vec operators) learned)
      :else
      (let [genotype-sigils (when (seq genotype) (map str genotype))
            sigils (case operator-scope
                     :genotype (distinct-sigils (concat pattern-sigils genotype-sigils))
                     :pattern (distinct-sigils pattern-sigils)
                     :all nil
                     (when (seq pattern-sigils)
                       (distinct-sigils pattern-sigils)))]
        (if (seq sigils)
          (select-operators sigils learned)
          (merge-operator-lists (vec (functor/compile-patterns)) learned))))))

(defn- dynamic-operators? [opts]
  (= (:operator-scope opts) :genotype))

(defn- log2 [x]
  (/ (Math/log x) (Math/log 2.0)))

(defn- shannon-entropy [s]
  (let [chars (seq (or s ""))
        total (double (count chars))]
    (if (pos? total)
      (- (reduce (fn [acc [_ cnt]]
                   (let [p (/ cnt total)]
                     (if (pos? p)
                       (+ acc (* p (log2 p)))
                       acc)))
                 0.0
                 (frequencies chars)))
      0.0)))

(defn- previous-entry [v]
  (when (> (count v) 1)
    (nth v (- (count v) 2))))

(defn- change-rate [history]
  (let [entries (:genotypes history)
        curr (peek entries)
        prev (previous-entry entries)
        len (count curr)]
    (when (and prev (pos? len))
      (/ (double (ca/hamming-distance prev curr)) len))))

(defn- derive-metrics [{:keys [genotype history generation]}]
  (let [chars (seq (or genotype ""))
        counts (frequencies chars)
        entropy (shannon-entropy genotype)
        change (change-rate history)]
    {:generation generation
     :length (count genotype)
     :unique-sigils (count counts)
     :sigil-counts counts
     :entropy entropy
     :change-rate change}))

(defn- replace-last [coll value]
  (let [v (vec coll)]
    (if (seq v)
      (assoc v (dec (count v)) value)
      [value])))

(defn- assoc-current-genotype [state value]
  (-> state
      (assoc :genotype value)
      (update-in [:history :genotypes] replace-last value)))

(defn- assoc-current-phenotype [state value]
  (-> state
      (assoc :phenotype value)
      (update-in [:history :phenotypes]
                 (fn [hist]
                   (if hist
                     (replace-last hist value)
                     [value])))))

(defn- refresh-metrics
  ([state] (refresh-metrics state false))
  ([state append?]
   (let [derived (derive-metrics state)
         merged (merge (:metrics state) derived)
         hist (vec (or (:metrics-history state) []))
         hist' (if append?
                 (conj hist merged)
                 (if (seq hist)
                   (assoc hist (dec (count hist)) merged)
                   [merged]))]
     (assoc state
            :metrics merged
            :metrics-history hist'))))

(defn- merge-metrics
  ([state custom] (merge-metrics state custom false))
  ([state custom append?]
   (let [merged (merge (:metrics state) custom)
         hist (vec (or (:metrics-history state) []))
         hist' (if append?
                 (conj hist merged)
                 (if (seq hist)
                   (assoc hist (dec (count hist)) merged)
                   [merged]))]
     (assoc state
            :metrics merged
            :metrics-history hist'))))

(defn- apply-rule-delta [state delta]
  (if (:lock-kernel state)
    state
    (cond
      (keyword? delta)
      (let [kernel delta]
        (assoc state
               :kernel kernel
               :kernel-fn (ca/kernel-fn kernel)))

      (fn? delta)
      (assoc state :kernel :custom :kernel-fn delta)

      (map? delta)
      (let [state' (cond-> state
                     (:kernel delta) (assoc :kernel (:kernel delta))
                     (:kernel-fn delta) (assoc :kernel-fn (:kernel-fn delta)))
            kernel-delta (:kernel delta)]
        (if (and kernel-delta (not (:kernel-fn delta)))
          (assoc state' :kernel-fn (ca/kernel-fn kernel-delta))
          state'))

      :else state)))

(defn- apply-grid-delta [state delta]
  (let [freeze? (:freeze-genotype state)]
    (cond
      (string? delta)
      (if freeze? state (assoc-current-genotype state delta))

      (fn? delta)
      (if freeze?
        state
        (assoc-current-genotype state (delta (:genotype state))))

      (map? delta)
      (let [state' (if (and (not freeze?) (:genotype delta))
                     (assoc-current-genotype state (:genotype delta))
                     state)]
        (if-let [phe (:phenotype delta)]
          (assoc-current-phenotype state' phe)
          state'))

      :else state)))

(defn- gate-genotype-by-phenotype
  [genotype next-gen phenotype signal]
  (if (and (string? genotype)
           (string? next-gen)
           (string? phenotype)
           (= (count genotype) (count next-gen) (count phenotype)))
    (apply str
           (map (fn [old new bit]
                  (if (= bit signal) old new))
                genotype
                next-gen
                phenotype))
    next-gen))

(defn- recordable-delta [{:keys [rule grid]}]
  (let [delta (into {} (remove (comp nil? val)
                               {:rule rule :grid grid}))]
    (not-empty delta)))

(defn- apply-effects [state {:keys [rule grid metrics] :as effects} mode]
  (if (classic-mode? mode)
    (let [state' (if metrics (merge-metrics state metrics) state)
          proposal (recordable-delta effects)]
      {:state state'
       :proposal proposal})
    (let [state' (cond-> state
                    grid (apply-grid-delta grid)
                    rule (apply-rule-delta rule))
          state'' (refresh-metrics state')
          state''' (if metrics
                     (merge-metrics state'' metrics)
                     state'')]
      {:state state'''
       :proposal nil})))

(defn- normalize-result [result]
  (cond
    (nil? result) {}
    (map? result) result
    :else {:value result}))

(defn- invoke-hook [{:keys [hooks parameters]} stage world meta]
  (if-let [f (get hooks stage)]
    (normalize-result (f :world world :meta meta :params parameters))
    {}))

(defn- run-hooks [operator stages world meta state mode]
  (reduce (fn [{:keys [meta state proposals]} stage]
            (let [result (invoke-hook operator stage world meta)
                  next-meta (if (contains? result :meta) (:meta result) meta)
                  effects (dissoc result :meta)
                  {:keys [state proposal]} (if (seq effects)
                                             (apply-effects state effects mode)
                                             {:state state :proposal nil})
                  proposals' (if proposal
                               (conj proposals (assoc proposal :stage stage))
                               proposals)]
              {:meta next-meta
               :state state
               :proposals proposals'}))
          {:meta meta :state state :proposals []}
          stages))

(defn- run-operators [operators stages world state metas mode proposals]
  (reduce (fn [{:keys [state metas proposals]} {:keys [sigil] :as operator}]
            (let [meta-state (get metas sigil {})
                  {:keys [meta state] :as hook-result} (run-hooks operator stages world meta-state state mode)
                  op-proposals (:proposals hook-result)
                  updated-proposals (if (seq op-proposals)
                                      (update proposals sigil (fnil into []) op-proposals)
                                      proposals)]
              {:state state
               :metas (assoc metas sigil meta)
               :proposals updated-proposals}))
          {:state state :metas metas :proposals proposals}
          operators))

(defn- advance-world [state]
  (binding [ca/*evolve-sigil-fn* (:kernel-fn state)]
    (if (:phenotype state)
      (if (:freeze-genotype state)
        (let [next-phe (ca/evolve-phenotype-against-genotype (:genotype state)
                                                            (:phenotype state))]
          (-> state
              (assoc :generation (inc (:generation state))
                     :phenotype next-phe)
              (update-in [:history :genotypes] conj (:genotype state))
              (update-in [:history :phenotypes]
                         (fn [hist]
                           (if hist
                             (conj hist next-phe)
                             [next-phe])))))
        (let [[next-gen next-phe]
              (ca/co-evolve-phenotype-and-genotype (:genotype state)
                                                   (:phenotype state))
              gated-gen (if (:genotype-gate state)
                          (gate-genotype-by-phenotype (:genotype state)
                                                      next-gen
                                                      next-phe
                                                      (or (:genotype-gate-signal state) \1))
                          next-gen)]
          (-> state
              (assoc :generation (inc (:generation state))
                     :genotype gated-gen
                     :phenotype next-phe)
              (update-in [:history :genotypes] conj gated-gen)
              (update-in [:history :phenotypes]
                         (fn [hist]
                           (if hist
                             (conj hist next-phe)
                             [next-phe]))))))
      (if (:freeze-genotype state)
        (-> state
            (assoc :generation (inc (:generation state)))
            (update-in [:history :genotypes] conj (:genotype state)))
        (let [next-gen (ca/evolve-sigil-string (:genotype state))]
          (-> state
              (assoc :generation (inc (:generation state))
                     :genotype next-gen)
              (update-in [:history :genotypes] conj next-gen)))))))

(defn- zap-half [s fill half]
  (let [len (count s)
        mid (quot len 2)
        left (subs s 0 mid)
        right (subs s mid)
        fill-left (apply str (repeat (count left) fill))
        fill-right (apply str (repeat (count right) fill))]
    (case half
      :right (str left fill-right)
      :left (str fill-left right)
      (str fill-left right))))

(defn- apply-lesion
  [state {:keys [target half mode]}]
  (let [target (or target (if (:phenotype state) :phenotype :genotype))
        half (or half :left)
        mode (or mode :zero)
        genotype-fill (if (= mode :zero) ca/default-sigil ca/default-sigil)]
    (case target
      :phenotype
      (if-let [phe (:phenotype state)]
        (assoc-current-phenotype state
                                 (zap-half phe \0 half))
        state)
      :genotype
      (assoc-current-genotype state
                              (zap-half (:genotype state) genotype-fill half))
      :both
      (let [state' (if-let [phe (:phenotype state)]
                     (assoc-current-phenotype state
                                              (zap-half phe \0 half))
                     state)]
        (assoc-current-genotype state'
                                (zap-half (:genotype state) genotype-fill half)))
      state)))

(defn- build-world [state metas]
  (-> state
      (select-keys [:generation :genotype :phenotype :history :metrics :metrics-history :kernel :kernel-fn])
      (assoc :meta-states metas)))

(defn- prepare-initial-state [{:keys [genotype phenotype kernel lock-kernel freeze-genotype
                                      genotype-gate genotype-gate-signal]}]
  (let [kernel* (or kernel default-kernel)]
    (-> {:generation 0
         :genotype genotype
         :phenotype phenotype
         :kernel kernel*
         :kernel-fn (ca/kernel-fn kernel*)
         :lock-kernel (boolean lock-kernel)
         :freeze-genotype (boolean freeze-genotype)
         :genotype-gate (boolean genotype-gate)
         :genotype-gate-signal (or genotype-gate-signal \1)
         :history {:genotypes [genotype]
                   :phenotypes (when phenotype [phenotype])}
         :metrics {}
         :metrics-history []}
        (refresh-metrics true))))

(defn run-mmca
  "Run a MetaMetaCA simulation alongside the CA kernel.

  opts keys:
  - :genotype (required)
  - :phenotype (optional)
  - :generations (default 32)
  - :kernel (default :mutating-template)
  - :pattern-sigils (optional vector of sigil strings)
  - :operators (optional pre-compiled operator maps)
  - :lesion (optional map with :tick, :target, :half, :mode)

  Returns a map containing genotype history, phenotype history, metrics, and
  the final meta-state for each operator."
  [{:keys [genotype generations mode lesion pulses-enabled] :as opts}]
  (let [genotype (ensure-genotype genotype)
        generations (or generations default-generations)
        operators (resolve-operators opts genotype)
        mode (or mode default-mode)
        pulses-enabled (not (false? pulses-enabled))
        lesion (when lesion (merge {:tick (quot generations 2)
                                    :half :left
                                    :mode :zero}
                                   lesion))
        initial-state (prepare-initial-state (assoc opts :genotype genotype))
        {:keys [state metas proposals]}
        (binding [operators/*pulses-enabled* pulses-enabled]
          (let [world (build-world initial-state {})]
            (run-operators operators [:init] world initial-state {} mode {})))
        ;; After init hooks, refresh metrics in case they mutated the world
        state (refresh-metrics state)
        result
        (binding [operators/*pulses-enabled* pulses-enabled]
          (loop [state state
                 metas metas
                 proposals proposals
                 tick 0
                 lesion-applied? false
                 operators operators]
            (if (= tick generations)
              {:state state :metas metas :proposals proposals :operators operators}
              (let [operators (if (dynamic-operators? opts)
                                (resolve-operators opts (:genotype state))
                                operators)
                    world (build-world state metas)
                    {:keys [state metas proposals]} (run-operators operators [:observe :decide :act]
                                                                 world state metas mode proposals)
                    state (refresh-metrics state)
                    lesion-now? (and lesion (not lesion-applied?) (= tick (:tick lesion)))
                    state (if lesion-now?
                            (refresh-metrics (apply-lesion state lesion) true)
                            state)
                    state (advance-world state)
                    state (refresh-metrics state true)]
                (recur state metas proposals (inc tick) (or lesion-applied? lesion-now?) operators)))))]
    (let [{:keys [state metas proposals operators]} result
          {:keys [history metrics-history kernel]} state]
      {:kernel kernel
       :generations generations
       :mode mode
       :operators (map #(select-keys % [:sigil :pattern :context :parameters :functor])
                       operators)
       :meta-states metas
       :gen-history (:genotypes history)
       :phe-history (:phenotypes history)
       :metrics-history metrics-history
       :proposals (not-empty proposals)
       :lesion (when lesion (select-keys lesion [:tick :target :half :mode]))})))

(defn run-mmca-stream
  "Run a MetaMetaCA simulation and call step-fn with each generation state.

  step-fn receives a map with :phase (:init or :tick), :tick, :state, :metas,
  :proposals, and :operators."
  [{:keys [genotype generations mode lesion pulses-enabled] :as opts} step-fn]
  (let [genotype (ensure-genotype genotype)
        generations (or generations default-generations)
        operators (resolve-operators opts genotype)
        mode (or mode default-mode)
        pulses-enabled (not (false? pulses-enabled))
        lesion (when lesion (merge {:tick (quot generations 2)
                                    :half :left
                                    :mode :zero}
                                   lesion))
        initial-state (prepare-initial-state (assoc opts :genotype genotype))
        emit (fn [phase tick state metas proposals operators]
               (when step-fn
                 (step-fn {:phase phase
                           :tick tick
                           :state state
                           :metas metas
                           :proposals proposals
                           :operators operators})))
        {:keys [state metas proposals]}
        (binding [operators/*pulses-enabled* pulses-enabled]
          (let [world (build-world initial-state {})]
            (run-operators operators [:init] world initial-state {} mode {})))
        ;; After init hooks, refresh metrics in case they mutated the world
        state (refresh-metrics state)]
    (emit :init 0 state metas proposals operators)
    (let [result
          (binding [operators/*pulses-enabled* pulses-enabled]
            (loop [state state
                   metas metas
                   proposals proposals
                   tick 0
                   lesion-applied? false
                   operators operators]
              (if (= tick generations)
                {:state state :metas metas :proposals proposals :operators operators}
                (let [operators (if (dynamic-operators? opts)
                                  (resolve-operators opts (:genotype state))
                                  operators)
                      world (build-world state metas)
                      {:keys [state metas proposals]} (run-operators operators [:observe :decide :act]
                                                                   world state metas mode proposals)
                      state (refresh-metrics state)
                      lesion-now? (and lesion (not lesion-applied?) (= tick (:tick lesion)))
                      state (if lesion-now?
                              (refresh-metrics (apply-lesion state lesion) true)
                              state)
                      state (advance-world state)
                      state (refresh-metrics state true)
                      next-tick (inc tick)]
                  (emit :tick next-tick state metas proposals operators)
                  (recur state metas proposals next-tick (or lesion-applied? lesion-now?) operators)))))]
      (let [{:keys [state metas proposals operators]} result
            {:keys [history metrics-history kernel]} state]
        {:kernel kernel
         :generations generations
         :mode mode
         :operators (map #(select-keys % [:sigil :pattern :context :parameters :functor])
                         operators)
         :meta-states metas
         :gen-history (:genotypes history)
         :phe-history (:phenotypes history)
         :metrics-history metrics-history
         :proposals (not-empty proposals)
         :lesion (when lesion (select-keys lesion [:tick :target :half :mode]))}))))
