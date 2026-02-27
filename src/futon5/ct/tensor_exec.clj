(ns futon5.ct.tensor-exec
  "Executable interpreter for CT tensor diagrams over MMCA tensor operators."
  (:require [futon5.ca.core :as ca]
            [futon5.ct.tensor :as tensor]
            [futon5.ct.tensor-diagrams :as diagrams]))

(defonce primitive-registry
  (atom {}))

(declare ensure-defaults!)

(defn register-primitive!
  "Register or replace a primitive implementation.

   f signature:
   - inputs: vector of values in diagram-domain order
   - state: full state map
   Returns either:
   - vector of output values in codomain order, or
   - map keyed by codomain keywords."
  [name f]
  (swap! primitive-registry assoc name f)
  f)

(defn primitive-impl
  [name]
  (get @primitive-registry name))

(defn clear-primitives!
  []
  (reset! primitive-registry {}))

(defn- output-map
  [raw codomain]
  (cond
    (map? raw)
    (let [missing (vec (remove #(contains? raw %) codomain))]
      (when (seq missing)
        (throw (ex-info "Primitive output map missing codomain keys"
                        {:codomain codomain
                         :missing missing
                         :output-keys (keys raw)})))
      (select-keys raw codomain))

    (sequential? raw)
    (let [vals (vec raw)]
      (when (not= (count vals) (count codomain))
        (throw (ex-info "Primitive output arity mismatch"
                        {:codomain codomain
                         :codomain-count (count codomain)
                         :value-count (count vals)})))
      (zipmap codomain vals))

    :else
    (throw (ex-info "Primitive returned unsupported output type"
                    {:output raw
                     :type (type raw)}))))

(defn- merge-disjoint
  [a b]
  (reduce (fn [acc [k v]]
            (if (contains? acc k)
              (if (= (get acc k) v)
                acc
                (throw (ex-info "Tensor branch key collision with conflicting values"
                                {:key k
                                 :left (get acc k)
                                 :right v})))
              (assoc acc k v)))
          a
          b))

(defn compile-diagram
  "Compile a Diagram to {:domain :codomain :run}.

   run signature:
   - state map -> output map (keys are codomain keywords)."
  [diagram]
  (ensure-defaults!)
  (letfn [(compile* [d]
            (case (:type d)
              :primitive
              (let [name (get-in d [:data :name])
                    domain (vec (:domain d))
                    codomain (vec (:codomain d))]
                {:domain domain
                 :codomain codomain
                 :run (fn [state]
                        (let [missing (vec (remove #(contains? state %) (distinct domain)))]
                          (when (seq missing)
                            (throw (ex-info "Primitive missing required inputs"
                                            {:primitive name
                                             :domain domain
                                             :missing missing
                                             :state-keys (keys state)})))
                          (let [impl (or (primitive-impl name)
                                         (throw (ex-info "No primitive implementation registered"
                                                         {:primitive name})))
                                inputs (mapv #(get state %) domain)
                                raw (impl inputs state)]
                            (output-map raw codomain))))})

              :identity
              (let [domain (vec (:domain d))
                    codomain (vec (:codomain d))]
                {:domain domain
                 :codomain codomain
                 :run (fn [state]
                        (let [missing (vec (remove #(contains? state %) (distinct domain)))]
                          (when (seq missing)
                            (throw (ex-info "Identity diagram missing required inputs"
                                            {:domain domain
                                             :missing missing
                                             :state-keys (keys state)})))
                          (zipmap codomain (mapv #(get state %) domain))))})

              :compose
              (let [parts (mapv compile* (get-in d [:data :parts]))]
                {:domain (vec (:domain d))
                 :codomain (vec (:codomain d))
                 :run (fn [state]
                        (let [final-state
                              (reduce (fn [st part]
                                        (merge st ((:run part) st)))
                                      state
                                      parts)]
                          (select-keys final-state (:codomain d))))})

              :tensor
              (let [parts (mapv compile* (get-in d [:data :parts]))]
                {:domain (vec (:domain d))
                 :codomain (vec (:codomain d))
                 :run (fn [state]
                        (let [outputs (mapv (fn [part] ((:run part) state)) parts)
                              merged (reduce merge-disjoint {} outputs)]
                          (select-keys merged (:codomain d))))})

              (throw (ex-info "Unsupported diagram type"
                              {:type (:type d)
                               :diagram d}))))]
    (compile* diagram)))

(defn run-diagram
  "Execute a compiled diagram (or raw Diagram) against a state map."
  [compiled-or-diagram state]
  (let [compiled (if (and (map? compiled-or-diagram) (fn? (:run compiled-or-diagram)))
                   compiled-or-diagram
                   (compile-diagram compiled-or-diagram))
        missing (vec (remove #(contains? state %) (distinct (:domain compiled))))]
    (when (seq missing)
      (throw (ex-info "Diagram missing required input keys"
                      {:domain (:domain compiled)
                       :missing missing
                       :state-keys (keys state)})))
    ((:run compiled) state)))

;; =============================================================================
;; Tensor Primitive Defaults
;; =============================================================================

(defn register-default-primitives!
  []
  (register-primitive! :pass-sigil-row
                       (fn [[row] _] [row]))
  (register-primitive! :sigil-row->tensor
                       (fn [[row] _] [(tensor/sigil-row->tensor row)]))
  (register-primitive! :tensor->bitplanes
                       (fn [[t] _] [(tensor/tensor->bitplanes t)]))
  (register-primitive! :step-bitplanes
                       (fn [[bitplanes] state]
                         (let [rule-sigil (or (:rule-sigil state)
                                              (throw (ex-info "step-bitplanes requires :rule-sigil in state"
                                                              {:state-keys (keys state)})))
                               step-opts (or (:step-opts state) {})]
                           [(tensor/step-bitplanes bitplanes rule-sigil step-opts)])))
  (register-primitive! :bitplanes->tensor
                       (fn [[bitplanes] _] [(tensor/bitplanes->tensor bitplanes)]))
  (register-primitive! :tensor->sigil-row
                       (fn [[t] _] [(tensor/tensor->sigil-row t)]))
  (register-primitive! :gate-sigil-row
                       (fn [[old-row new-row phenotype] _]
                         [(tensor/gate-sigil-row-by-phenotype old-row new-row phenotype)])))

(defonce ^:private defaults-installed?
  (delay (register-default-primitives!)))

(defn- ensure-defaults! []
  @defaults-installed?)

;; =============================================================================
;; Canonical Tensor Pipelines
;; =============================================================================

(def sigil-step-diagram diagrams/sigil-step-diagram)
(def sigil-step-gated-diagram diagrams/sigil-step-gated-diagram)
(def sigil-step-with-branch-diagram diagrams/sigil-step-with-branch-diagram)
(def tensor-diagram-library diagrams/diagram-library)

(defn available-diagrams
  "Return ids for built-in reusable tensor diagrams."
  []
  (diagrams/available-diagrams))

(defn run-tensor-ca
  "Run an executable tensor diagram as a CA evolution loop.

   opts:
   - :genotype (required) initial sigil row
   - :rule-sigil (required unless custom diagram ignores it)
   - :generations (default 32)
   - :phenotype (optional) enables gating path if present
   - :lesion (optional) map with :tick/:target/:half/:mode
   - :rule-plan (optional) map or entries overriding rule sigil by tick
   - :step-opts (optional) passed to tensor step-bitplane
   - :diagram (optional) custom diagram
   - :output-key (optional) key to read next row from diagram output"
  [{:keys [genotype rule-sigil generations phenotype step-opts diagram output-key lesion rule-plan]
    :or {generations 32}}]
  (ensure-defaults!)
  (when-not (seq genotype)
    (throw (ex-info "run-tensor-ca requires :genotype" {:opts [:genotype]})))
  (let [diagram (or diagram (if phenotype sigil-step-gated-diagram sigil-step-diagram))
        output-key (or output-key (if phenotype :gated-row :new-row))
        compiled (compile-diagram diagram)
        phenotype (when phenotype (tensor/tensor->phenotype (tensor/phenotype->tensor phenotype)))
        _ (when (and phenotype (not= (count genotype) (count phenotype)))
            (throw (ex-info "Phenotype width must match genotype width"
                            {:genotype-width (count genotype)
                             :phenotype-width (count phenotype)})))
        normalize-target (fn [target]
                           (keyword (name (or target (if phenotype :phenotype :genotype)))))
        normalize-half (fn [half]
                         (keyword (name (or half :left))))
        normalize-lesion (fn [lesion]
                           (when lesion
                             (when-not (map? lesion)
                               (throw (ex-info "run-tensor-ca :lesion must be a map"
                                               {:lesion lesion})))
                             {:tick (long (or (:tick lesion) (quot generations 2)))
                              :target (normalize-target (:target lesion))
                              :half (normalize-half (:half lesion))
                              :mode (keyword (name (or (:mode lesion) :zero)))}))
        lesion (normalize-lesion lesion)
        zap-half (fn [s fill half]
                   (let [s (or s "")
                         len (count s)
                         mid (quot len 2)
                         left (subs s 0 mid)
                         right (subs s mid)
                         fill-left (apply str (repeat (count left) fill))
                         fill-right (apply str (repeat (count right) fill))]
                     (case half
                       :right (str left fill-right)
                       :left (str fill-left right)
                       (str fill-left right))))
        lesion-apply (fn [row phe]
                       (if-not lesion
                         [row phe]
                         (let [{:keys [target half mode]} lesion
                               genotype-fill (if (= mode :zero) ca/default-sigil ca/default-sigil)]
                           (case target
                             :phenotype [row (when phe (zap-half phe \0 half))]
                             :genotype [(zap-half row genotype-fill half) phe]
                             :both [(zap-half row genotype-fill half)
                                    (when phe (zap-half phe \0 half))]
                             [row phe]))))
        normalize-rule (fn [x]
                         (cond
                           (nil? x) nil
                           (string? x) x
                           (char? x) (str x)
                           (keyword? x) (name x)
                           (integer? x)
                           (let [bits (Integer/toBinaryString (int x))
                                 pad (max 0 (- 8 (count bits)))
                                 padded (str (apply str (repeat pad "0")) bits)]
                             (ca/sigil-for padded))
                           :else (str x)))
        rule-plan-map
        (cond
          (nil? rule-plan) {}
          (map? rule-plan)
          (into {}
                (keep (fn [[tick r]]
                        (when (number? tick)
                          [(long tick) (normalize-rule r)])))
                rule-plan)
          (sequential? rule-plan)
          (reduce (fn [acc entry]
                    (cond
                      (and (vector? entry) (= 2 (count entry)) (number? (first entry)))
                      (assoc acc (long (first entry)) (normalize-rule (second entry)))
                      (and (map? entry) (number? (:tick entry)))
                      (assoc acc (long (:tick entry))
                             (normalize-rule (or (:rule-sigil entry)
                                                 (:rule entry))))
                      :else acc))
                  {}
                  rule-plan)
          :else
          (throw (ex-info "run-tensor-ca :rule-plan must be map or sequence"
                          {:rule-plan rule-plan})))
        resolve-rule (fn [tick]
                       (or (get rule-plan-map (long tick))
                           rule-sigil))]
    (loop [gen 0
           current genotype
           active-phenotype phenotype
           history [genotype]
           lesion-applied? false]
      (if (>= gen generations)
        {:gen-history history
         :generations generations
         :diagram-type (:type diagram)
         :rule-sigil rule-sigil
         :rule-plan (when (seq rule-plan-map) rule-plan-map)
         :phenotype active-phenotype
         :lesion (when lesion
                   (select-keys lesion [:tick :target :half :mode]))
         :tensor-backend (or (:backend step-opts) :clj)}
        (let [lesion-now? (and lesion (not lesion-applied?) (= (long gen) (:tick lesion)))
              [current active-phenotype] (if lesion-now?
                                           (lesion-apply current active-phenotype)
                                           [current active-phenotype])
              state (cond-> {:sigil-row current
                             :rule-sigil (resolve-rule gen)
                             :step-opts step-opts}
                      active-phenotype (assoc :phenotype active-phenotype))
              out (run-diagram compiled state)
              next-row (get out output-key)]
          (when-not (string? next-row)
            (throw (ex-info "Diagram did not produce next sigil row"
                            {:output-key output-key
                             :output out})))
          (recur (inc gen)
                 next-row
                 active-phenotype
                 (conj history next-row)
                 (or lesion-applied? lesion-now?)))))))
