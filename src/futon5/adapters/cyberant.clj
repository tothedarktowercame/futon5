(ns futon5.adapters.cyberant
  "Adapter: CT schemas / exotypes → AIF cyberant configs.

   The goal is domain-general transfer of 'patterns of improvisation'.
   Even sigils that seem trivial (一 = 00000000, 乐 = 11111111) may have
   meaningful interpretations in the target domain - we just haven't
   found them yet. Cf. I Ching: ䷀ (乾) and ䷁ (坤) are foundational,
   not degenerate.

   The adapter maps:
   - Sigil → exotype params (existing, via futon5.mmca.exotype)
   - Exotype params → CT interpretation (mode structure, transitions)
   - CT interpretation → AIF policy config (cyberant terminals)"
  (:require [clojure.edn :as edn]
            [futon5.adapters.notions :as notions]
            [futon5.mmca.exotype :as exotype]))

;; =============================================================================
;; Sigil → Exotype (already exists, re-expose)
;; =============================================================================

(defn sigil->exotype
  "Lift a sigil to its exotype params."
  [sigil]
  (exotype/lift sigil))

;; =============================================================================
;; Exotype → CT Interpretation
;; =============================================================================

(defn- rotation->mode-bias
  "Rotation (0-3) suggests which mode the exotype 'prefers'.
   0 = gather-focused, 1 = return-focused, 2 = maintain-focused, 3 = adaptive."
  [rotation]
  (case (int rotation)
    0 :outbound
    1 :homebound
    2 :maintain
    3 :adaptive))

(defn- threshold->constraint-strength
  "Match threshold (0-1) suggests how strictly constraints are enforced.
   Low = permissive (explore), High = strict (exploit)."
  [threshold]
  (cond
    (< threshold 0.3) :permissive
    (< threshold 0.7) :moderate
    :else :strict))

(defn- update-prob->transition-rate
  "Update probability suggests how often mode transitions happen.
   Low = stable, High = volatile."
  [prob]
  (cond
    (< prob 0.3) :stable
    (< prob 0.7) :moderate
    :else :volatile))

(defn- mix-mode->coordination
  "Mix mode suggests how the exotype coordinates with neighbors/history."
  [mode]
  (case mode
    :none :independent
    :rotate-left :follow-gradient
    :rotate-right :counter-gradient
    :reverse :contrarian
    :xor-neighbor :differentiate
    :majority :conform
    :swap-halves :oscillate
    :scramble :randomize
    :independent))

(defn exotype->ct-interpretation
  "Interpret exotype params as CT structure.

   This is the key step: what does this pattern of params *mean*
   as a morphism / mode structure / adaptation strategy?"
  [{:keys [sigil tier params]}]
  (let [{:keys [rotation match-threshold invert-on-phenotype?
                update-prob mix-mode mix-shift]} params]
    {:sigil sigil
     :tier tier

     ;; VISION: what modes does this exotype recognize?
     :vision
     {:mode-bias (rotation->mode-bias rotation)
      :constraint-strength (threshold->constraint-strength match-threshold)
      :phenotype-responsive? invert-on-phenotype?}

     ;; PLAN: how does it intend to transition?
     :plan
     {:transition-rate (update-prob->transition-rate update-prob)
      :coordination (mix-mode->coordination mix-mode)
      :shift-phase mix-shift}

     ;; ADAPT: when does it switch strategies?
     :adapt
     {:trigger-threshold match-threshold
      :invert-on-context? invert-on-phenotype?
      :volatility update-prob}}))

;; =============================================================================
;; CT Interpretation → Cyberant Config
;; =============================================================================

(defn- mode-bias->policy-priors
  "Convert mode bias to AIF policy priors (C-prior weighting)."
  [mode-bias]
  (case mode-bias
    :outbound   {:forage 0.6 :return 0.2 :hold 0.1 :pheromone 0.1}
    :homebound  {:forage 0.2 :return 0.6 :hold 0.1 :pheromone 0.1}
    :maintain   {:forage 0.2 :return 0.2 :hold 0.3 :pheromone 0.3}
    :adaptive   {:forage 0.25 :return 0.25 :hold 0.25 :pheromone 0.25}
    {:forage 0.25 :return 0.25 :hold 0.25 :pheromone 0.25}))

(defn- constraint-strength->precision
  "Convert constraint strength to observation precision (Pi-o)."
  [strength]
  (case strength
    :permissive 0.3
    :moderate 0.6
    :strict 0.9
    0.6))

(defn- transition-rate->tau
  "Convert transition rate to policy temperature (tau).
   Stable = low tau (committed), volatile = high tau (exploratory)."
  [rate]
  (case rate
    :stable 0.2
    :moderate 0.5
    :volatile 0.8
    0.5))

(defn- coordination->pattern-behavior
  "Convert coordination mode to pattern-sense behavior."
  [coord]
  (case coord
    :independent {:trail-follow 0.0 :gradient-use 0.0}
    :follow-gradient {:trail-follow 0.7 :gradient-use 0.8}
    :counter-gradient {:trail-follow -0.3 :gradient-use -0.5}
    :contrarian {:trail-follow -0.5 :gradient-use -0.3}
    :differentiate {:trail-follow 0.0 :gradient-use 0.0 :novelty-seek 0.8}
    :conform {:trail-follow 0.8 :gradient-use 0.5}
    :oscillate {:trail-follow 0.3 :gradient-use 0.3 :phase-shift true}
    :randomize {:trail-follow 0.0 :gradient-use 0.0 :random-weight 0.5}
    {:trail-follow 0.3 :gradient-use 0.3}))

(defn ct-interpretation->cyberant-config
  "Generate AIF cyberant config from CT interpretation."
  [{:keys [sigil tier vision plan adapt] :as interp}]
  {:species (keyword (str "cyber-" sigil))
   :sigil sigil
   :tier tier

   ;; Policy structure
   :policy-priors (mode-bias->policy-priors (:mode-bias vision))
   :default-mode (case (:mode-bias vision)
                   :outbound :outbound
                   :homebound :homebound
                   :maintain :maintain
                   :adaptive :outbound)

   ;; Precision / temperature
   :precision {:Pi-o (constraint-strength->precision (:constraint-strength vision))
               :tau (transition-rate->tau (:transition-rate plan))}

   ;; Pattern behavior
   :pattern-sense (coordination->pattern-behavior (:coordination plan))

   ;; Adaptation triggers
   :adapt-config {:threshold (:trigger-threshold adapt)
                  :invert-on-context? (:invert-on-context? adapt)
                  :volatility (:volatility adapt)}

   ;; Phenotype responsiveness (hunger/cargo coupling)
   :phenotype-coupling {:enabled? (:phenotype-responsive? vision)
                        :hunger-weight (if (:phenotype-responsive? vision) 0.4 0.1)
                        :cargo-weight (if (:phenotype-responsive? vision) 0.5 0.2)}

   ;; CT provenance (for interpretability)
   :ct-provenance interp})

;; =============================================================================
;; Full Pipeline
;; =============================================================================

(defn sigil->cyberant
  "Full pipeline: sigil → exotype → CT interpretation → cyberant config."
  [sigil]
  (-> sigil
      sigil->exotype
      exotype->ct-interpretation
      ct-interpretation->cyberant-config))

(defn- base-config
  "Create a default cyberant config when no sigil or exotype is available."
  [sigil]
  {:species (keyword (str "cyber-" (or sigil "pattern")))
   :sigil sigil
   :tier :pattern
   :policy-priors (mode-bias->policy-priors :adaptive)
   :default-mode :outbound
   :precision {:Pi-o 0.6 :tau 0.5}
   :pattern-sense (coordination->pattern-behavior :independent)
   :adapt-config {}
   :phenotype-coupling {:enabled? false :hunger-weight 0.1 :cargo-weight 0.2}})

(defn- apply-ant-interpretation
  "Overlay an ant-interpretation block onto a cyberant config."
  [config ant]
  (let [policy (:policy ant)
        precision (:precision ant)
        pattern-sense (:pattern-sense ant)
        adapt-trigger (:adapt-trigger ant)
        policy-priors (or (:policy-priors policy)
                          (when-let [bias (:mode-bias policy)]
                            (mode-bias->policy-priors bias)))]
    (cond-> config
      policy-priors (assoc :policy-priors policy-priors)
      (:mode-bias policy) (assoc :default-mode (:mode-bias policy))
      (number? (:Pi-o precision)) (assoc-in [:precision :Pi-o] (:Pi-o precision))
      (number? (:tau precision)) (assoc-in [:precision :tau] (:tau precision))
      (map? pattern-sense) (assoc :pattern-sense pattern-sense)
      (seq adapt-trigger) (update :adapt-config merge
                                  {:trigger (:condition adapt-trigger)
                                   :switch-to (:switch-to adapt-trigger)
                                   :interpretation (:interpretation adapt-trigger)})
      (:failure-mode ant) (assoc-in [:ant-provenance :failure-mode] (:failure-mode ant))
      true (assoc :ant-provenance ant))))

(defn- module-policy
  "Extract policy settings from an ant-interpretation block."
  [ant]
  (let [policy (:policy ant)
        policy-priors (or (:policy-priors policy)
                          (when-let [bias (:mode-bias policy)]
                            (mode-bias->policy-priors bias)))]
    (cond-> {}
      policy-priors (assoc :policy-priors policy-priors)
      (:mode-bias policy) (assoc :default-mode (:mode-bias policy)))))

(defn- module-precision
  "Extract precision settings from an ant-interpretation block."
  [ant]
  (let [precision (:precision ant)]
    (cond-> {}
      (number? (:Pi-o precision)) (assoc-in [:precision :Pi-o] (:Pi-o precision))
      (number? (:tau precision)) (assoc-in [:precision :tau] (:tau precision)))))

(defn- module-pattern-sense
  "Extract pattern-sense settings from an ant-interpretation block."
  [ant]
  (let [pattern-sense (:pattern-sense ant)]
    (if (map? pattern-sense)
      {:pattern-sense pattern-sense}
      {})))

(defn- module-adapt
  "Extract adapt settings from an ant-interpretation block."
  [ant]
  (let [adapt-trigger (:adapt-trigger ant)]
    (if (seq adapt-trigger)
      {:adapt-config {:trigger (:condition adapt-trigger)
                      :switch-to (:switch-to adapt-trigger)
                      :interpretation (:interpretation adapt-trigger)}}
      {})))

(defn- module-from-pattern
  "Build a module contribution from a pattern id."
  [pattern-id module-key]
  (let [pattern (notions/pattern-by-id pattern-id)
        ant (:ant-interpretation pattern)
        sigil (get-in pattern [:mmca-interpretation :sigil-encoding :sigil])
        derived (when sigil (sigil->cyberant sigil))
        module (case module-key
                 :policy (or (module-policy ant)
                             (select-keys derived [:policy-priors :default-mode]))
                 :precision (or (module-precision ant)
                                (select-keys derived [:precision]))
                 :pattern-sense (or (module-pattern-sense ant)
                                    (select-keys derived [:pattern-sense]))
                 :adapt (or (module-adapt ant)
                            (select-keys derived [:adapt-config]))
                 {})]
    {:pattern-id pattern-id
     :pattern-title (:title pattern)
     :pattern-source (:path pattern)
     :module module-key
     :contribution module
     :ant-interpretation ant
     :ct-interpretation (:ct-interpretation pattern)}))

(defn pattern-id->cyberant
  "Build a cyberant config from a flexiarg pattern id.
   Uses ant-interpretation when present and mmca sigil if available."
  [pattern-id]
  (when-let [pattern (notions/pattern-by-id pattern-id)]
    (let [sigil (get-in pattern [:mmca-interpretation :sigil-encoding :sigil])
          base (if sigil
                 (sigil->cyberant sigil)
                 (base-config nil))
          ant (:ant-interpretation pattern)
          ct (:ct-interpretation pattern)]
      (cond-> base
        ant (apply-ant-interpretation ant)
        ct (assoc-in [:ct-provenance :pattern-ct] ct)
        true (assoc :pattern-id pattern-id
                    :pattern-title (:title pattern)
                    :pattern-source (:path pattern))))))

(defn pattern-program->cyberant
  "Compose a cyberant config from module-specific pattern ids.

   Example:
   {:policy \"iching/hexagram-44-gou\"
    :precision \"iching/hexagram-52-gen\"
    :pattern-sense \"iching/hexagram-57-xun\"
    :adapt \"iching/hexagram-43-guai\"}
  "
  [module->pattern-id & {:keys [base-sigil] :or {base-sigil nil}}]
  (let [base (if base-sigil
               (sigil->cyberant base-sigil)
               (base-config base-sigil))
        modules (->> module->pattern-id
                     (map (fn [[module-key pattern-id]]
                            [module-key (module-from-pattern pattern-id module-key)]))
                     (into {}))
        merged (reduce
                (fn [cfg [_ {:keys [contribution]}]]
                  (merge cfg contribution))
                base
                modules)
        provenance (into {}
                         (map (fn [[k v]]
                                [k (select-keys v [:pattern-id :pattern-title :pattern-source])])
                              modules))]
    (-> merged
        (assoc :pattern-program module->pattern-id)
        (assoc :pattern-modules provenance)
        (assoc :pattern-module-details modules))))

(defn batch-convert
  "Convert a collection of sigils to cyberant configs."
  [sigils]
  (mapv sigil->cyberant sigils))

(defn batch-patterns
  "Convert a collection of pattern ids to cyberant configs."
  [pattern-ids]
  (->> pattern-ids
       (mapv pattern-id->cyberant)
       (remove nil?)
       vec))

;; =============================================================================
;; Special Cases: The "Degenerate" Sigils
;; =============================================================================

(defn interpret-foundational-sigils
  "Special interpretations for sigils that seem 'trivial' but may be foundational.

   一 (00000000) - 'The Receptive' / Pure Yin / ䷁ (坤)
   → In ants: maximum receptivity to environment, no internal bias
   → CT: identity morphism, pure observation without intervention

   乐 (11111111) - 'The Creative' / Pure Yang / ䷀ (乾)
   → In ants: maximum assertion, full internal drive
   → CT: maximal morphism, intervention regardless of observation

   These aren't kill-switch/no-op - they're limit cases that reveal
   what the intermediate sigils are interpolating between."
  []
  {:foundational
   [{:sigil "一"
     :bits "00000000"
     :iching {:hexagram "䷁" :name "坤" :meaning "The Receptive"}
     :ct-interpretation "Identity morphism / pure observation"
     :ant-interpretation "Maximum environmental receptivity, no internal bias"
     :config (sigil->cyberant "一")}

    {:sigil "乐"
     :bits "11111111"
     :iching {:hexagram "䷀" :name "乾" :meaning "The Creative"}
     :ct-interpretation "Maximal morphism / pure intervention"
     :ant-interpretation "Maximum assertion, full internal drive"
     :config (sigil->cyberant "乐")}]

   :interpolation-note
   "All other sigils interpolate between these poles.
    The 'meaning' of a sigil is its position in this space."})

;; =============================================================================
;; Wiring Diagram → Cyberant Config
;; =============================================================================
;;
;; Wiring diagrams are compositional structures with nodes and edges.
;; Key components to recognize:
;;   - :legacy-kernel-step  → legacy path with exotype params (baseline behavior)
;;   - :bit-xor, :bit-and   → creative/mutation operations (differentiate)
;;   - :threshold-sigil     → gates/conditional switching (adapt triggers)
;;   - :diversity           → diversity measurement (novelty sensing)
;;   - :context-*           → context extraction (observations)

(defn- find-nodes-by-component
  "Find all nodes with a specific component type."
  [diagram component-kw]
  (filter #(= component-kw (:component %)) (:nodes diagram)))

(defn- find-nodes-by-pattern
  "Find nodes whose component name matches a pattern."
  [diagram pattern]
  (filter #(and (:component %)
                (re-find pattern (name (:component %))))
          (:nodes diagram)))

(defn- extract-legacy-node
  "Extract legacy kernel node and its params."
  [diagram]
  (first (find-nodes-by-component diagram :legacy-kernel-step)))

(defn- extract-creative-nodes
  "Find creative/mutation nodes (xor, and, mutate, etc)."
  [diagram]
  (find-nodes-by-pattern diagram #"xor|and|mutate|creative"))

(defn- extract-gate-nodes
  "Find gate/threshold nodes."
  [diagram]
  (find-nodes-by-pattern diagram #"threshold|gate"))

(defn- extract-diversity-nodes
  "Find diversity measurement nodes."
  [diagram]
  (find-nodes-by-component diagram :diversity))

(defn- has-component?
  "Check if diagram has a component of given type."
  [diagram component-kw]
  (seq (find-nodes-by-component diagram component-kw)))

(defn- has-pattern?
  "Check if diagram has a component matching pattern."
  [diagram pattern]
  (seq (find-nodes-by-pattern diagram pattern)))

;; --- Wiring Structure Analysis ---

(defn analyze-wiring-structure
  "Analyze a wiring diagram's structural patterns.
   Returns a map describing the wiring's behavioral architecture."
  [wiring]
  (let [diagram (:diagram wiring)
        legacy-node (extract-legacy-node diagram)
        creative-nodes (extract-creative-nodes diagram)
        gate-nodes (extract-gate-nodes diagram)
        diversity-nodes (extract-diversity-nodes diagram)]
    {:id (get-in wiring [:meta :id])
     :label (get-in wiring [:meta :label])

     ;; Component presence
     :has-legacy? (some? legacy-node)
     :has-creative? (seq creative-nodes)
     :has-gate? (seq gate-nodes)
     :has-diversity? (seq diversity-nodes)

     ;; Legacy path details
     :legacy (when legacy-node
               (let [params (:params legacy-node)]
                 {:sigil (:exotype-sigil params)
                  :tier (:exotype-tier params)
                  :exotype-params (:exotype-params params)}))

     ;; Creative path details
     :creative {:count (count creative-nodes)
                :types (mapv :component creative-nodes)}

     ;; Gate details
     :gate {:count (count gate-nodes)
            :types (mapv :component gate-nodes)}

     ;; Diversity sensing
     :diversity {:count (count diversity-nodes)}

     ;; Structural classification
     :pattern (cond
                (and (seq gate-nodes) (seq creative-nodes) (some? legacy-node))
                :boundary-guardian  ; gate chooses between legacy and creative

                (and (seq creative-nodes) (some? legacy-node))
                :hybrid             ; both paths but no gate

                (seq creative-nodes)
                :creative-only      ; pure creative, no legacy

                (some? legacy-node)
                :legacy-only        ; pure legacy wrapper

                :else
                :unknown)}))

;; --- Wiring → CT Interpretation ---

(defn wiring->ct-interpretation
  "Interpret a wiring diagram as CT structure.
   Maps wiring components to behavioral patterns."
  [wiring]
  (let [analysis (analyze-wiring-structure wiring)
        legacy-params (get-in analysis [:legacy :exotype-params])
        pattern (:pattern analysis)]

    {:wiring-id (:id analysis)
     :pattern pattern

     ;; VISION: what modes/situations does this wiring recognize?
     :vision
     (cond-> {:diversity-aware? (:has-diversity? analysis)
              :conditional? (:has-gate? analysis)}
       ;; If has legacy, inherit its mode bias
       legacy-params
       (assoc :mode-bias (rotation->mode-bias (or (:rotation legacy-params) 0))
              :constraint-strength (threshold->constraint-strength
                                    (or (:match-threshold legacy-params) 0.5))))

     ;; PLAN: how does it intend to behave?
     :plan
     (cond-> {}
       ;; Legacy path contributes baseline behavior
       legacy-params
       (assoc :baseline-coordination (mix-mode->coordination
                                       (or (:mix-mode legacy-params) :none))
              :baseline-transition-rate (update-prob->transition-rate
                                          (or (:update-prob legacy-params) 0.5)))
       ;; Creative path adds differentiation capability
       (:has-creative? analysis)
       (assoc :creative-coordination :differentiate
              :creative-mode :novelty-seek))

     ;; ADAPT: when does it switch between paths?
     :adapt
     (cond-> {:switchable? (:has-gate? analysis)}
       (:has-gate? analysis)
       (assoc :trigger (if (:has-diversity? analysis) :diversity-high :threshold)
              :switch-threshold 0.5)  ; default, could extract from wiring
       (:has-diversity? analysis)
       (assoc :diversity-driven? true))}))

;; --- CT Interpretation → Cyberant Config (for wirings) ---

(defn wiring-ct->cyberant-config
  "Convert wiring CT interpretation to cyberant config."
  [{:keys [wiring-id pattern vision plan adapt] :as interp}]
  (let [;; Base policy from legacy path (if present)
        base-policy (if (:mode-bias vision)
                      (mode-bias->policy-priors (:mode-bias vision))
                      {:forage 0.25 :return 0.25 :hold 0.25 :pheromone 0.25})

        ;; Base pattern-sense from legacy coordination
        base-pattern-sense (coordination->pattern-behavior
                            (or (:baseline-coordination plan) :independent))

        ;; Creative pattern-sense (for switching)
        creative-pattern-sense (when (:creative-coordination plan)
                                 (coordination->pattern-behavior
                                  (:creative-coordination plan)))]

    {:species (keyword (str "cyber-wiring-" (or (name wiring-id) "unknown")))
     :wiring-id wiring-id
     :wiring-pattern pattern

     ;; Policy structure (from legacy baseline)
     :policy-priors base-policy
     :default-mode (or (:mode-bias vision) :adaptive)

     ;; Precision / temperature
     :precision {:Pi-o (constraint-strength->precision
                        (or (:constraint-strength vision) :moderate))
                 :tau (transition-rate->tau
                       (or (:baseline-transition-rate plan) :moderate))}

     ;; Pattern behavior (baseline)
     :pattern-sense base-pattern-sense

     ;; Adaptation config (gate behavior)
     :adapt-config
     (when (:switchable? adapt)
       {:enabled? true
        :trigger (or (:trigger adapt) :novelty-high)
        :threshold (or (:switch-threshold adapt) 0.5)
        :diversity-driven? (:diversity-driven? adapt)
        ;; What to switch TO when trigger fires
        :switch-to (when creative-pattern-sense
                     {:pattern-sense creative-pattern-sense})})

     ;; CT provenance
     :ct-provenance interp}))

;; --- Main Wiring Pipeline ---

(defn wiring->cyberant
  "Full pipeline: wiring diagram → analysis → CT interpretation → cyberant config.

   This is the generalized version of sigil->cyberant that handles
   compositional wiring structures with gates, creative paths, and diversity sensing."
  [wiring]
  (-> wiring
      wiring->ct-interpretation
      wiring-ct->cyberant-config))

(defn load-wiring
  "Load a wiring from an EDN file."
  [path]
  (try
    (edn/read-string {:readers {'object (fn [_] nil)}}
                     (slurp path))
    (catch Exception e
      (println "Warning: Could not load wiring" path ":" (.getMessage e))
      nil)))

(defn wiring-file->cyberant
  "Load a wiring file and convert to cyberant config."
  [path]
  (when-let [wiring (load-wiring path)]
    (wiring->cyberant wiring)))

;; --- Unified Entry Point ---

(defn convert->cyberant
  "Unified conversion: accepts sigil string, wiring map, or file path.

   Examples:
     (convert->cyberant \"工\")                         ; sigil
     (convert->cyberant {:diagram {...} :meta {...}})  ; wiring map
     (convert->cyberant \"data/wiring-ladder/level-5-creative.edn\")  ; file"
  [input]
  (cond
    ;; String: either sigil or file path
    (string? input)
    (if (or (.endsWith input ".edn")
            (.contains input "/"))
      (wiring-file->cyberant input)
      (sigil->cyberant input))

    ;; Map with :diagram key: wiring
    (and (map? input) (:diagram input))
    (wiring->cyberant input)

    ;; Map with :sigil key: assume exotype-like
    (and (map? input) (:sigil input))
    (sigil->cyberant (:sigil input))

    :else
    (throw (ex-info "Unknown input type for cyberant conversion"
                    {:input input :type (type input)}))))

(comment
  ;; === Sigil examples (existing) ===
  (sigil->cyberant "土")
  ;; => {:species :cyber-土, :sigil "土", :tier :local, ...}

  (batch-convert ["土" "工" "上" "下"])

  ;; === Wiring examples (new) ===

  ;; Load and convert L5-creative
  (wiring-file->cyberant "data/wiring-ladder/level-5-creative.edn")
  ;; => {:species :cyber-wiring-level-5-creative,
  ;;     :wiring-pattern :boundary-guardian,
  ;;     :adapt-config {:enabled? true, :trigger :diversity-high, ...}, ...}

  ;; Analyze wiring structure
  (-> (load-wiring "data/wiring-ladder/level-5-creative.edn")
      analyze-wiring-structure)
  ;; => {:pattern :boundary-guardian, :has-legacy? true, :has-creative? true, ...}

  ;; Unified entry point
  (convert->cyberant "工")                                    ; sigil
  (convert->cyberant "data/wiring-ladder/level-5-creative.edn")  ; file

  ;; Compare L5-creative vs pure sigil
  (let [l5 (convert->cyberant "data/wiring-ladder/level-5-creative.edn")
        gong (convert->cyberant "工")]
    {:l5-pattern (:wiring-pattern l5)
     :l5-adapt (:adapt-config l5)
     :gong-pattern (:wiring-pattern gong)  ; nil (it's a sigil, not wiring)
     :gong-adapt (:adapt-config gong)})    ; nil
  )
