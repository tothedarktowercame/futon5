(ns futon5.xenotype.category
  "Category theory validation for xenotype wirings.

   Models wirings as morphisms in a category where:
   - Objects = port types (sigil, sigil-list, bits, scalar, etc.)
   - Morphisms = components (transformations between types)
   - Composition = connecting outputs to inputs

   Validates:
   - Identity: id_A ∘ f = f = f ∘ id_B (identity wirings)
   - Associativity: (f ∘ g) ∘ h = f ∘ (g ∘ h)"
  (:require [futon5.exotic.category :as cat]
            [futon5.xenotype.wiring :as wiring]
            [clojure.edn :as edn]))

;;; ============================================================
;;; Type System (Objects in the Category)
;;; ============================================================

(def port-types
  "The objects in our wiring category - all valid port types."
  #{:sigil :sigil-list :bits :scalar :scalar-list
    :int :bool :bool-list :context :state :freq})

;;; ============================================================
;;; Identity Morphisms
;;; ============================================================

(def identity-components
  "Identity morphism for each type - passes through unchanged.
   These satisfy: id_A ∘ f = f = f ∘ id_A"
  {:id-sigil      {:inputs [[:x :sigil]]      :outputs [[:x :sigil]]      :doc "Identity on sigil"}
   :id-sigil-list {:inputs [[:x :sigil-list]] :outputs [[:x :sigil-list]] :doc "Identity on sigil-list"}
   :id-bits       {:inputs [[:x :bits]]       :outputs [[:x :bits]]       :doc "Identity on bits"}
   :id-scalar     {:inputs [[:x :scalar]]     :outputs [[:x :scalar]]     :doc "Identity on scalar"}
   :id-scalar-list {:inputs [[:x :scalar-list]] :outputs [[:x :scalar-list]] :doc "Identity on scalar-list"}
   :id-int        {:inputs [[:x :int]]        :outputs [[:x :int]]        :doc "Identity on int"}
   :id-bool       {:inputs [[:x :bool]]       :outputs [[:x :bool]]       :doc "Identity on bool"}
   :id-bool-list  {:inputs [[:x :bool-list]]  :outputs [[:x :bool-list]]  :doc "Identity on bool-list"}
   :id-context    {:inputs [[:x :context]]    :outputs [[:x :context]]    :doc "Identity on context"}
   :id-state      {:inputs [[:x :state]]      :outputs [[:x :state]]      :doc "Identity on state"}
   :id-freq       {:inputs [[:x :freq]]       :outputs [[:x :freq]]       :doc "Identity on freq"}})

(defn identity-for-type
  "Get the identity component ID for a given type."
  [type-kw]
  (keyword (str "id-" (name type-kw))))

;;; ============================================================
;;; Morphism Extraction
;;; ============================================================

(defn component->morphism
  "Extract morphism info from a component definition.

   For components with multiple inputs/outputs, we extract
   the 'primary' morphism (first input type -> first output type).
   This simplifies to the standard category model."
  [comp-id comp-def]
  (let [inputs (:inputs comp-def)
        outputs (:outputs comp-def)]
    (when (and (seq inputs) (seq outputs))
      {:id comp-id
       :dom (second (first inputs))   ; first input type
       :cod (second (first outputs))  ; first output type
       :full-inputs inputs
       :full-outputs outputs})))

(defn load-all-morphisms
  "Load all components and convert to morphism definitions."
  []
  (let [lib (wiring/load-components)
        components (:components lib)]
    (into {}
          (keep (fn [[comp-id comp-def]]
                  (when-let [morph (component->morphism comp-id comp-def)]
                    [comp-id morph]))
                components))))

;;; ============================================================
;;; Composition Validation
;;; ============================================================

(defn composable?
  "Check if two morphisms can be composed: f ∘ g exists when cod(g) = dom(f).

   For wiring diagrams, this means the output type of g matches
   an input type of f."
  [morph-f morph-g]
  (= (:dom morph-f) (:cod morph-g)))

(defn compose-morphisms
  "Compose two morphisms f ∘ g (apply g first, then f).
   Returns a new morphism with dom(g) and cod(f).
   Returns nil if not composable."
  [morph-f morph-g]
  (when (composable? morph-f morph-g)
    {:id (keyword (str (name (:id morph-g)) ";" (name (:id morph-f))))
     :dom (:dom morph-g)
     :cod (:cod morph-f)
     :composed-from [(:id morph-g) (:id morph-f)]}))

;;; ============================================================
;;; Category Construction
;;; ============================================================

(defn build-wiring-category
  "Construct a Category record for the wiring system.

   This builds the compose-table for all valid compositions."
  []
  (let [morphisms (load-all-morphisms)
        ;; Add identity morphisms
        all-morphisms (merge morphisms
                             (into {} (map (fn [[k v]]
                                             [k (component->morphism k v)])
                                           identity-components)))
        ;; Build identities map
        identities (into {} (map (fn [t] [t (identity-for-type t)])
                                 port-types))
        ;; Build compose table for all valid compositions
        compose-table (into {}
                            (for [[id-f morph-f] all-morphisms
                                  [id-g morph-g] all-morphisms
                                  :when (composable? morph-f morph-g)]
                              [[id-f id-g]
                               (:id (compose-morphisms morph-f morph-g))]))]
    (cat/->Category
     port-types
     all-morphisms
     identities
     compose-table)))

;;; ============================================================
;;; Law Verification
;;; ============================================================

(defn verify-identity-laws
  "Verify that identity morphisms satisfy:
   id_A ∘ f = f (for all f with dom(f) = A)
   f ∘ id_B = f (for all f with cod(f) = B)

   Returns {:passed bool :failures [...]}"
  []
  (let [morphisms (load-all-morphisms)
        failures (atom [])]

    ;; Check id_A ∘ f = f (left identity)
    (doseq [[comp-id morph] morphisms]
      (let [dom-type (:dom morph)
            id-comp (identity-for-type dom-type)
            composed (compose-morphisms morph
                                        {:id id-comp :dom dom-type :cod dom-type})]
        (when-not (= (:dom composed) (:dom morph))
          (swap! failures conj {:law :left-identity
                                :component comp-id
                                :expected (:dom morph)
                                :got (:dom composed)}))))

    ;; Check f ∘ id_B = f (right identity)
    (doseq [[comp-id morph] morphisms]
      (let [cod-type (:cod morph)
            id-comp (identity-for-type cod-type)
            composed (compose-morphisms {:id id-comp :dom cod-type :cod cod-type}
                                        morph)]
        (when-not (= (:cod composed) (:cod morph))
          (swap! failures conj {:law :right-identity
                                :component comp-id
                                :expected (:cod morph)
                                :got (:cod composed)}))))

    {:passed (empty? @failures)
     :failures @failures}))

(defn verify-associativity
  "Verify that composition is associative:
   (f ∘ g) ∘ h = f ∘ (g ∘ h)

   Returns {:passed bool :failures [...]}"
  []
  (let [morphisms (load-all-morphisms)
        morph-list (vals morphisms)
        failures (atom [])]

    ;; Sample triples where composition is defined
    (doseq [h morph-list
            g morph-list
            f morph-list
            :when (and (composable? g h)
                       (composable? f g))]
      (let [;; (f ∘ g) ∘ h
            fg (compose-morphisms f g)
            fg-h (when fg (compose-morphisms fg h))
            ;; f ∘ (g ∘ h)
            gh (compose-morphisms g h)
            f-gh (when gh (compose-morphisms f gh))]
        (when (and fg-h f-gh)
          (when-not (and (= (:dom fg-h) (:dom f-gh))
                         (= (:cod fg-h) (:cod f-gh)))
            (swap! failures conj {:law :associativity
                                  :f (:id f)
                                  :g (:id g)
                                  :h (:id h)
                                  :left-result fg-h
                                  :right-result f-gh})))))

    {:passed (empty? @failures)
     :failures @failures}))

(defn verify-all-laws
  "Run all category law verifications.
   Returns {:identity {...} :associativity {...} :all-passed bool}"
  []
  (let [identity-result (verify-identity-laws)
        assoc-result (verify-associativity)]
    {:identity identity-result
     :associativity assoc-result
     :all-passed (and (:passed identity-result)
                      (:passed assoc-result))}))

;;; ============================================================
;;; Wiring Diagram Validation
;;; ============================================================

(def type-coercions
  "Valid type coercions that are implicitly allowed.
   Maps [from-type to-type] -> true for allowed coercions."
  {[:sigil :sigil-list] true      ; single sigil can be treated as list
   [:int :scalar] true            ; int can be used where scalar expected
   [:scalar :int] true            ; scalar can be truncated to int
   [:bool :int] true              ; bool -> 0/1
   [:int :bool] true})            ; int -> truthy

(defn types-compatible?
  "Check if from-type can be used where to-type is expected."
  [from-type to-type]
  (or (= from-type to-type)
      (get type-coercions [from-type to-type])))

(defn validate-wiring-types
  "Validate that a wiring diagram's edges have compatible types.

   Returns {:valid bool :errors [...]}"
  [diagram]
  (let [lib (wiring/load-components)
        components (:components lib)
        nodes (into {} (map (fn [n] [(:id n) n]) (:nodes diagram)))
        errors (atom [])]

    (doseq [edge (:edges diagram)]
      (when (and (:from edge) (:to edge))
        (let [from-node (get nodes (:from edge))
              to-node (get nodes (:to edge))
              from-comp (get components (:component from-node))
              to-comp (get components (:component to-node))]

          (when (and from-comp to-comp)
            (let [from-port (:from-port edge)
                  to-port (:to-port edge)
                  ;; Find output type from source
                  from-output (some (fn [[port type]]
                                      (when (= port from-port) type))
                                    (:outputs from-comp))
                  ;; Find input type at destination
                  to-input (some (fn [[port type]]
                                   (when (= port to-port) type))
                                 (:inputs to-comp))]

              (when (and from-output to-input
                         (not (types-compatible? from-output to-input)))
                (swap! errors conj {:edge edge
                                    :from-type from-output
                                    :to-type to-input
                                    :message (str "Type mismatch: " from-output " -> " to-input)})))))))

    {:valid (empty? @errors)
     :errors @errors}))

(defn validate-wiring-file
  "Load and validate a wiring EDN file.
   Returns {:valid bool :type-errors [...] :law-check {...}}"
  [path]
  (let [wiring (edn/read-string (slurp path))
        diagram (:diagram wiring)
        type-check (validate-wiring-types diagram)]
    {:path path
     :meta (:meta wiring)
     :type-check type-check
     :valid (:valid type-check)}))

;;; ============================================================
;;; Convenience Functions
;;; ============================================================

(defn category-summary
  "Get a summary of the wiring category."
  []
  (let [morphisms (load-all-morphisms)]
    {:object-count (count port-types)
     :morphism-count (count morphisms)
     :objects port-types
     :morphisms-by-type (group-by :dom (vals morphisms))}))
