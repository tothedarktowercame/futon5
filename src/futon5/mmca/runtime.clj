(ns futon5.mmca.runtime
  "MetaMetaCA runtime that wires compiled pattern operators into the CA loop.

   IMPORTANT: As of 2026-01-24, there are two exotype modes:

   :inline (deprecated) — Old global kernel steering. One context is sampled
                          and used to modify the GLOBAL kernel. All cells
                          get the same physics.

   :local-physics       — New per-cell physics. Each cell computes its own
                          36-bit context and applies the corresponding
                          physics rule. Different cells may use different
                          kernels. This is the correct implementation.

   Use :exotype-mode :local-physics for new runs."
  (:require [futon5.ca.core :as ca]
            [futon5.ct.tensor-mmca :as tensor-mmca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.functor :as functor]
            [futon5.mmca.local-physics :as local-physics]
            [futon5.mmca.operators :as operators]))

(def ^:private default-generations 32)
(def ^:private default-kernel :mutating-template)
(def ^:private default-mode :god)
(def ^:private default-engine :mmca)
(def ^:private supported-engines #{:mmca :tensor})

(defn- classic-mode? [mode]
  (= mode :classic))

(defn- ensure-genotype [genotype]
  (when-not (seq genotype)
    (throw (ex-info "MMCA runtime requires a starting genotype"
                    {:genotype genotype})))
  genotype)

(defn- normalize-engine [engine]
  (let [engine (cond
                 (keyword? engine) engine
                 (string? engine) (keyword engine)
                 (nil? engine) default-engine
                 :else engine)]
    (when-not (contains? supported-engines engine)
      (throw (ex-info "Unsupported MMCA engine"
                      {:engine engine
                       :supported (vec (sort supported-engines))})))
    engine))

(defn- rule-int->sigil [n]
  (when (and (integer? n) (<= 0 (long n) 255))
    (let [bits (Integer/toBinaryString (int n))
          pad (max 0 (- 8 (count bits)))
          padded (str (apply str (repeat pad "0")) bits)]
      (ca/sigil-for padded))))

(defn- resolve-tensor-rule-sigil [{:keys [rule-sigil tensor-rule-sigil global-rule]}]
  (or rule-sigil
      tensor-rule-sigil
      (rule-int->sigil global-rule)))

(def ^:private tensor-unsupported-keys
  [:kernel :kernel-spec :kernel-fn :operators :learned-operators :pattern-sigils
   :operator-scope :exotype :exotype-mode :bend-mode :lock-kernel
   :freeze-genotype :genotype-gate :genotype-gate-signal :pulses-enabled])

(defn- present? [v]
  (cond
    (nil? v) false
    (false? v) false
    (and (string? v) (empty? v)) false
    (and (sequential? v) (empty? v)) false
    :else true))

(defn- tensor-unsupported-opts [opts]
  (->> tensor-unsupported-keys
       (keep (fn [k]
               (when (present? (get opts k))
                 k)))
       vec))

(defn- run-mmca-tensor
  [{:keys [genotype generations phenotype mode step-opts diagram output-key seed lesion rule-plan] :as opts}]
  (let [unsupported (tensor-unsupported-opts opts)
        _ (when (seq unsupported)
            (throw (ex-info "Tensor engine does not support requested MMCA options"
                            {:engine :tensor
                             :unsupported unsupported})))
        genotype (ensure-genotype genotype)
        generations (or generations default-generations)
        mode (or mode default-mode)
        rule-sigil (resolve-tensor-rule-sigil opts)
        _ (when-not (seq rule-sigil)
            (throw (ex-info "Tensor engine requires :rule-sigil (or numeric :global-rule)"
                            {:engine :tensor
                             :required [:rule-sigil]
                             :accepted-fallback [:global-rule]})))
        run (tensor-mmca/run-tensor-mmca {:genotype genotype
                                          :rule-sigil rule-sigil
                                          :generations generations
                                          :phenotype phenotype
                                          :lesion lesion
                                          :rule-plan rule-plan
                                          :step-opts step-opts
                                          :diagram diagram
                                          :output-key output-key
                                          :seed seed})]
    {:engine :tensor
     :kernel :tensor-bitplane
     :kernel-spec nil
     :kernel-fn nil
     :generations generations
     :mode mode
     :exotype nil
     :exotype-mode nil
     :exotype-system :tensor
     :exotype-metadata {:exotype-system :tensor
                        :timestamp (System/currentTimeMillis)
                        :version "2026-02-21"}
     :global-rule nil
     :bend-mode nil
     :local-physics-runs nil
     :exotype-nominations nil
     :exotype-mutations nil
     :exotype-contexts nil
     :operators []
     :meta-states {}
     :gen-history (:gen-history run)
     :phe-history (:phe-history run)
     :metrics-history (:metrics-history run)
     :summary (:summary run)
     :episode-summary (:episode-summary run)
     :proposals nil
     :lesion (:lesion run)
     :tensor {:rule-sigil rule-sigil
              :diagram-type (:diagram-type run)
              :backend (:tensor-backend run)}}))

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
               :kernel-spec nil
               :kernel-fn (ca/kernel-fn kernel)))

      (fn? delta)
      (assoc state :kernel :custom :kernel-fn delta)

      (map? delta)
      (let [kernel-delta (or (:kernel-spec delta) (:kernel delta))
            kernel-spec (when (ca/kernel-spec? kernel-delta)
                          (ca/normalize-kernel-spec kernel-delta))
            state' (cond-> state
                     kernel-delta (assoc :kernel kernel-delta :kernel-spec kernel-spec)
                     (:kernel-fn delta) (assoc :kernel-fn (:kernel-fn delta)))]
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

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- rng-phenotype-string [^java.util.Random rng length]
  (apply str (repeatedly length #(rng-int rng 2))))

(defn- sample-exotype-context
  [state ^java.util.Random rng]
  (let [mode (or (:exotype-context-mode state) :history)
        gen-history (get-in state [:history :genotypes])
        phe-history (get-in state [:history :phenotypes])]
    (if (= mode :random)
      (let [len (count (or (:genotype state) ""))
            phe-len (count (or (:phenotype state) ""))]
        (when (pos? len)
          (let [g0 (rng-sigil-string rng len)
                g1 (rng-sigil-string rng len)
                p0 (when (pos? phe-len) (rng-phenotype-string rng phe-len))
                p1 (when (pos? phe-len) (rng-phenotype-string rng phe-len))]
            (exotype/sample-context [g0 g1] (when p0 [p0 p1]) rng))))
      (exotype/sample-context gen-history phe-history rng))))

(defn- sample-exotype-contexts
  [state ^java.util.Random rng]
  (let [depth (max 1 (int (or (:exotype-context-depth state) 1)))
        mode (or (:exotype-context-mode state) :history)]
    (if (= mode :random)
      (vec (keep (fn [_] (sample-exotype-context state rng)) (range depth)))
      (let [gen-history (get-in state [:history :genotypes])
            phe-history (get-in state [:history :phenotypes])
            rows (count gen-history)
            cols (count (or (first gen-history) ""))]
        (when (and (> rows 1) (pos? cols))
          (let [t0 (rng-int rng (dec rows))
                x0 (rng-int rng cols)]
            (->> (range depth)
                 (map (fn [i]
                        (exotype/sample-context-at gen-history phe-history (+ t0 i) x0 rng)))
                 (take-while some?)
                 vec)))))))

(defn- resolve-exotype-context
  [state ^java.util.Random rng tick]
  (if (:exotype-context-replay? state)
    [(get (:exotype-contexts state) tick) state]
    (let [ctxs (sample-exotype-contexts state rng)
          state' (if (:exotype-context-capture? state)
                   (update state :exotype-contexts conj ctxs)
                   state)]
      [ctxs state'])))

(defn- apply-exotype-chain
  [kernel-spec exotype ctxs ^java.util.Random rng]
  (reduce (fn [spec ctx]
            (if (and spec ctx)
              (exotype/apply-exotype spec exotype ctx rng)
              spec))
          kernel-spec
          ctxs))

(defn- update-kernel-by-exotype
  [state exotype ^java.util.Random rng tick]
  (if (or (nil? exotype) (:lock-kernel state))
    state
    (let [[ctxs state'] (resolve-exotype-context state rng tick)
          kernel (or (:kernel-spec state') (:kernel state'))
          kernel-spec (when kernel
                        (try
                          (ca/kernel-spec-for kernel)
                          (catch Exception _ nil)))]
      (if-let [next-kernel (and kernel-spec
                                (seq ctxs)
                                (apply-exotype-chain kernel-spec exotype ctxs rng))]
        (let [prev-id (when kernel-spec (ca/kernel-id kernel-spec))
              next-id (when next-kernel (ca/kernel-id next-kernel))
              mutation? (and prev-id next-id (not= prev-id next-id))
              state'' (cond-> state'
                        mutation? (update :exotype-mutations (fnil conj [])
                                          {:tick tick
                                           :from prev-id
                                           :to next-id
                                           :exotype (select-keys exotype [:sigil :tier :params])}))]
          (assoc state''
                 :kernel next-kernel
                 :kernel-spec next-kernel
                 :kernel-fn (ca/kernel-fn next-kernel)))
        state'))))

(defn- nominate-kernel-by-exotype
  [state exotype ^java.util.Random rng tick]
  (if (or (nil? exotype) (:lock-kernel state))
    state
    (let [[ctxs state'] (resolve-exotype-context state rng tick)
          kernel (or (:kernel-spec state') (:kernel state'))
          kernel-spec (when kernel
                        (try
                          (ca/kernel-spec-for kernel)
                          (catch Exception _ nil)))
          next-kernel (and kernel-spec
                           (seq ctxs)
                           (apply-exotype-chain kernel-spec exotype ctxs rng))]
      (if next-kernel
        (update state' :exotype-nominations
                (fnil conj [])
                {:tick tick
                 :kernel next-kernel
                 :kernel-id (ca/kernel-id next-kernel)})
        state'))))

(defn- apply-exotype-mode
  [state exotype rng tick exotype-mode]
  (case exotype-mode
    :inline (update-kernel-by-exotype state exotype rng tick)
    :nominate (nominate-kernel-by-exotype state exotype rng tick)
    ;; :local-physics is handled by shared local-physics advance helpers.
    :local-physics state
    state))

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
      (select-keys [:generation :genotype :phenotype :history :metrics :metrics-history :kernel :kernel-spec :kernel-fn])
      (assoc :meta-states metas)))

(defn- prepare-initial-state [{:keys [genotype phenotype kernel kernel-spec kernel-fn lock-kernel freeze-genotype
                                      genotype-gate genotype-gate-signal exotype-contexts
                                      capture-exotype-contexts exotype-context-mode
                                      exotype-context-depth]}]
  (let [kernel* (or kernel-spec kernel default-kernel)
        kernel-spec (when (ca/kernel-spec? kernel*)
                      (ca/normalize-kernel-spec kernel*))
        replay? (seq exotype-contexts)
        capture? (true? capture-exotype-contexts)
        contexts (cond
                   replay? (vec exotype-contexts)
                   capture? []
                   :else nil)]
    (-> {:generation 0
         :genotype genotype
         :phenotype phenotype
         :kernel (if kernel-fn :custom kernel*)
         :kernel-spec (when-not kernel-fn kernel-spec)
         :kernel-fn (or kernel-fn (ca/kernel-fn kernel*))
         :lock-kernel (boolean lock-kernel)
         :freeze-genotype (boolean freeze-genotype)
         :genotype-gate (boolean genotype-gate)
         :genotype-gate-signal (or genotype-gate-signal \1)
         :exotype-contexts contexts
         :exotype-context-replay? (boolean replay?)
         :exotype-context-capture? (boolean capture?)
         :exotype-context-mode (or exotype-context-mode :history)
         :exotype-context-depth (max 1 (int (or exotype-context-depth 1)))
         :history {:genotypes [genotype]
                   :phenotypes (when phenotype [phenotype])}
         :metrics {}
         :metrics-history []}
        (refresh-metrics true))))

(defn run-mmca
  "Run a MetaMetaCA simulation alongside the CA kernel.

  opts keys:
  - :engine (optional) :mmca (default) or :tensor
  - :genotype (required)
  - :phenotype (optional)
  - :generations (default 32)
  - :kernel (default :mutating-template)
  - :pattern-sigils (optional vector of sigil strings)
  - :operators (optional pre-compiled operator maps)
  - :lesion (optional map with :tick, :target, :half, :mode)
  - :exotype (optional exotype spec or sigil)
  - :seed (optional RNG seed for exotype updates)
  - :exotype-contexts (optional vector of contexts for lock-2 replay)
  - :capture-exotype-contexts (optional, store sampled contexts for lock-2)
  - :exotype-context-mode (optional :history or :random)
  - :exotype-context-depth (optional recursion depth for context sampling)
  - :exotype-mode (optional :inline, :nominate, or :local-physics; default :inline)
      - :inline (DEPRECATED) - samples random context to modify global kernel
      - :nominate - same as inline but doesn't apply immediately
      - :local-physics - NEW: each cell computes its own 36-bit physics rule
  - :global-rule (optional, for :local-physics mode) - global rule (0-255 or keyword)
  - :bend-mode (optional, for :local-physics mode) - :sequential, :blend, or :matrix

  Tensor engine (:engine :tensor) keys:
  - :rule-sigil (required, or provide numeric :global-rule)
  - :step-opts, :diagram, :output-key (optional; passed to tensor runner)

  Returns a map containing genotype history, phenotype history, metrics, and
  the final meta-state for each operator."
  [{:keys [genotype generations mode lesion pulses-enabled engine] :as opts}]
  (let [engine (normalize-engine engine)]
    (if (= engine :tensor)
      (run-mmca-tensor opts)
      (let [genotype (ensure-genotype genotype)
            generations (or generations default-generations)
            operators (resolve-operators opts genotype)
            mode (or mode default-mode)
            pulses-enabled (true? pulses-enabled)
            exotype (exotype/resolve-exotype (:exotype opts))
            exotype-mode (or (:exotype-mode opts) :inline)
            use-local-physics? (= exotype-mode :local-physics)
            global-rule (:global-rule opts)
            bend-mode (or (:bend-mode opts) :blend)
            exotype-rng (when (and exotype (not use-local-physics?))
                          (java.util.Random. (long (or (:seed opts) (System/nanoTime)))))
            lesion (when lesion (merge {:tick (quot generations 2)
                                        :half :left
                                        :mode :zero}
                                       lesion))
            initial-state (prepare-initial-state (assoc opts :genotype genotype))
            {:keys [state metas proposals]}
            (binding [operators/*pulses-enabled* pulses-enabled
                      exotype/*exotype-system* (if use-local-physics?
                                                 :local-physics
                                                 :global-kernel)]
              (let [world (build-world initial-state {})]
                (run-operators operators [:init] world initial-state {} mode {})))
            ;; After init hooks, refresh metrics in case they mutated the world
            state (refresh-metrics state)
            result
            (binding [operators/*pulses-enabled* pulses-enabled
                      exotype/*exotype-system* (if use-local-physics?
                                                 :local-physics
                                                 :global-kernel)]
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
                        ;; Apply old exotype mode OR skip if using local physics
                        state (if (and exotype (not use-local-physics?))
                                (apply-exotype-mode state exotype exotype-rng tick exotype-mode)
                                state)
                        ;; Advance world with appropriate physics
                        state (if use-local-physics?
                                (local-physics/advance-state state global-rule bend-mode
                                                             {:track-local? true})
                                (advance-world state))
                        state (refresh-metrics state true)]
                    (recur state metas proposals (inc tick) (or lesion-applied? lesion-now?) operators)))))]
        (let [{:keys [state metas proposals operators]} result
              {:keys [history metrics-history kernel kernel-spec]} state]
        {:engine :mmca
           :kernel kernel
           :kernel-spec kernel-spec
           :kernel-fn (:kernel-fn state)
           :generations generations
           :mode mode
           :exotype (when exotype (select-keys exotype [:sigil :tier :params]))
           :exotype-mode exotype-mode
           :exotype-system (if use-local-physics? :local-physics :global-kernel)
           :exotype-metadata {:exotype-system (if use-local-physics? :local-physics :global-kernel)
                              :timestamp (System/currentTimeMillis)
                              :version "2026-01-24"}
           :global-rule (when use-local-physics? global-rule)
           :bend-mode (when use-local-physics? bend-mode)
           :local-physics-runs (not-empty (:local-physics-runs state))
           :exotype-nominations (not-empty (:exotype-nominations state))
           :exotype-mutations (not-empty (:exotype-mutations state))
           :exotype-contexts (not-empty (:exotype-contexts state))
            :operators (map #(select-keys % [:sigil :pattern :context :parameters :functor])
                            operators)
            :meta-states metas
            :gen-history (:genotypes history)
            :phe-history (:phenotypes history)
           :metrics-history metrics-history
           :proposals (not-empty proposals)
            :lesion (when lesion (select-keys lesion [:tick :target :half :mode]))})))))

(defn- tensor-stream-state [result tick]
  (let [gen-history (vec (or (:gen-history result) []))
        phe-history (vec (or (:phe-history result) []))
        metrics-history (vec (or (:metrics-history result) []))
        last-idx (max 0 (dec (count gen-history)))
        idx (min (max 0 tick) last-idx)
        hist-end (inc idx)
        phe-end (min hist-end (count phe-history))
        phenotype (when (< idx (count phe-history))
                    (nth phe-history idx))
        history {:genotypes (subvec gen-history 0 hist-end)
                 :phenotypes (when (pos? phe-end)
                               (subvec phe-history 0 phe-end))}]
    {:generation idx
     :genotype (nth gen-history idx "")
     :phenotype phenotype
     :history history
     :metrics (when (< idx (count metrics-history))
                (nth metrics-history idx))
     :metrics-history (if (pos? (count metrics-history))
                        (subvec metrics-history 0 (min hist-end (count metrics-history)))
                        [])
     :kernel (:kernel result)
     :kernel-spec (:kernel-spec result)
     :kernel-fn (:kernel-fn result)}))

(defn- emit-tensor-stream! [result step-fn]
  (when (and step-fn (seq (:gen-history result)))
    (let [metas {}
          proposals {}
          operators []]
      (step-fn {:phase :init
                :tick 0
                :state (tensor-stream-state result 0)
                :metas metas
                :proposals proposals
                :operators operators})
      (doseq [tick (range 1 (count (:gen-history result)))]
        (step-fn {:phase :tick
                  :tick tick
                  :state (tensor-stream-state result tick)
                  :metas metas
                  :proposals proposals
                  :operators operators})))))

(defn run-mmca-stream
  "Run a MetaMetaCA simulation and call step-fn with each generation state.

  step-fn receives a map with :phase (:init or :tick), :tick, :state, :metas,
  :proposals, and :operators.

  When :engine :tensor is selected, states are synthesized from tensor history."
  [{:keys [genotype generations mode lesion pulses-enabled engine] :as opts} step-fn]
  (let [engine (normalize-engine engine)]
    (if (= engine :tensor)
      (let [result (run-mmca opts)]
        (emit-tensor-stream! result step-fn)
        result)
      (let [genotype (ensure-genotype genotype)
            generations (or generations default-generations)
            operators (resolve-operators opts genotype)
            mode (or mode default-mode)
            pulses-enabled (true? pulses-enabled)
            exotype (exotype/resolve-exotype (:exotype opts))
            exotype-mode (or (:exotype-mode opts) :inline)
            exotype-rng (when exotype
                          (java.util.Random. (long (or (:seed opts) (System/nanoTime)))))
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
                          state (if exotype
                                  (apply-exotype-mode state exotype exotype-rng tick exotype-mode)
                                  state)
                          state (advance-world state)
                          state (refresh-metrics state true)
                          next-tick (inc tick)]
                      (emit :tick next-tick state metas proposals operators)
                      (recur state metas proposals next-tick (or lesion-applied? lesion-now?) operators)))))]
          (let [{:keys [state metas proposals operators]} result
                {:keys [history metrics-history kernel]} state]
            {:engine :mmca
             :kernel kernel
             :kernel-fn (:kernel-fn state)
             :generations generations
             :mode mode
             :exotype (when exotype (select-keys exotype [:sigil :tier :params]))
             :exotype-mode exotype-mode
             :exotype-nominations (not-empty (:exotype-nominations state))
             :exotype-mutations (not-empty (:exotype-mutations state))
             :exotype-contexts (not-empty (:exotype-contexts state))
             :operators (map #(select-keys % [:sigil :pattern :context :parameters :functor])
                             operators)
             :meta-states metas
             :gen-history (:genotypes history)
             :phe-history (:phenotypes history)
             :metrics-history metrics-history
             :proposals (not-empty proposals)
             :lesion (when lesion (select-keys lesion [:tick :target :half :mode]))}))))))
