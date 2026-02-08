(ns futon5.ct.mission
  "Category-theoretic validation for mission architecture diagrams.

   Models missions as morphisms in a category where:
   - Objects = port types (data shapes at mission boundaries)
   - Morphisms = components (internal transformations)
   - Composition = connecting outputs of mission A to inputs of mission B

   Validates:
   - Completeness: every output port reachable from some input
   - Coverage: every component reaches at least one output (no dead components)
   - Type safety: wire types match at connection points
   - No orphan inputs: every input connects to something
   - Composition: mission A → mission B requires matching port types

   EDN format:
   {:mission/id :futon1a-rebuild
    :mission/state :active
    :ports {:input  [{:id :I1 :name \"spec\" :type :edn-document :source \"human\"}]
            :output [{:id :O1 :name \"api\" :type :http-endpoint :consumer \"client\"
                      :spec-ref \"2.6\"}]}
    :components [{:id :C1 :name \"Layer\" :inputs [:type] :outputs [:type]}]
    :edges [{:from :I1 :to :C1 :type :edn-document}
            {:from :C1 :to :O1 :type :http-endpoint}]}"
  (:require [clojure.set]))

;;; ============================================================
;;; Port Types (Objects in the Mission Category)
;;; ============================================================

(def port-types
  "The objects in our mission category — shapes of data at boundaries."
  #{:edn-document       ; structured EDN specification
    :http-endpoint      ; REST/HTTP API surface
    :http-request       ; inbound HTTP request
    :http-response      ; outbound HTTP response
    :xtdb-node          ; XTDB database handle
    :xtdb-tx            ; XTDB transaction
    :xtdb-entity        ; stored entity
    :clj-namespace      ; Clojure code module
    :ring-handler       ; Ring request handler
    :model-descriptor   ; self-describing model
    :type-registry      ; catalog of entity/relation types
    :proof-path         ; durable-write audit trail
    :error-response     ; structured error
    :config             ; system configuration
    :migration-data     ; futon1 LMDB data
    :test-suite         ; test namespace
    :cli-command})      ; command-line invocation

(def type-coercions
  "Implicit type promotions allowed in mission wiring."
  {[:http-request :ring-handler]   true   ; request feeds handler
   [:ring-handler :http-response]  true   ; handler produces response
   [:xtdb-tx :xtdb-entity]        true   ; tx produces entity
   [:xtdb-entity :xtdb-tx]        true   ; entity goes into tx
   [:model-descriptor :type-registry] true ; descriptor registers into registry
   [:edn-document :config]        true   ; EDN can be config
   [:config :edn-document]        true}) ; config is EDN

(defn types-compatible?
  "Check if from-type can wire to where to-type is expected."
  [from-type to-type]
  (or (= from-type to-type)
      (get type-coercions [from-type to-type] false)))

;;; ============================================================
;;; Mission Diagram Construction
;;; ============================================================

(defn mission-diagram
  "Create a mission diagram from an EDN spec.
   Validates basic structure and returns normalized form."
  [{:keys [mission/id mission/state ports components edges]
    :or {state :greenfield}
    :as spec}]
  (let [input-ids  (set (map :id (:input ports)))
        output-ids (set (map :id (:output ports)))
        comp-ids   (set (map :id components))
        all-ids    (clojure.set/union input-ids output-ids comp-ids)]
    {:mission/id    id
     :mission/state state
     :ports         ports
     :components    components
     :edges         edges
     :index         {:input-ids  input-ids
                     :output-ids output-ids
                     :comp-ids   comp-ids
                     :all-ids    all-ids
                     :node-map   (into {} (concat
                                           (map (fn [p] [(:id p) (assoc p :role :input)]) (:input ports))
                                           (map (fn [p] [(:id p) (assoc p :role :output)]) (:output ports))
                                           (map (fn [c] [(:id c) (assoc c :role :component)]) components)))}}))

;;; ============================================================
;;; Graph Traversal
;;; ============================================================

(defn- build-adjacency
  "Build forward adjacency map from edges."
  [edges]
  (reduce (fn [m e] (update m (:from e) (fnil conj []) (:to e)))
          {}
          edges))

(defn- build-reverse-adjacency
  "Build reverse adjacency map from edges."
  [edges]
  (reduce (fn [m e] (update m (:to e) (fnil conj []) (:from e)))
          {}
          edges))

(defn- reachable-from
  "Set of all nodes reachable from start-ids via forward edges."
  [adj start-ids]
  (loop [queue (vec start-ids)
         visited #{}]
    (if (empty? queue)
      visited
      (let [node (first queue)
            rest-q (subvec queue 1)]
        (if (visited node)
          (recur rest-q visited)
          (recur (into rest-q (get adj node []))
                 (conj visited node)))))))

(defn- reachable-backwards
  "Set of all nodes that can reach any of target-ids via reverse edges."
  [rev-adj target-ids]
  (reachable-from rev-adj target-ids))

;;; ============================================================
;;; Validation: Completeness
;;; ============================================================

(defn validate-completeness
  "Every output port must be reachable from at least one input port.
   An output with no path from any input is unimplementable.

   Returns {:valid bool :unreachable-outputs [...]}"
  [diagram]
  (let [{:keys [input-ids output-ids]} (:index diagram)
        adj (build-adjacency (:edges diagram))
        reachable (reachable-from adj input-ids)
        unreachable (remove reachable output-ids)]
    {:valid (empty? unreachable)
     :check :completeness
     :unreachable-outputs (vec unreachable)}))

;;; ============================================================
;;; Validation: Coverage
;;; ============================================================

(defn validate-coverage
  "Every internal component must have at least one path to an output port.
   A component with no path to any output is dead code or a missing output.

   Returns {:valid bool :dead-components [...]}"
  [diagram]
  (let [{:keys [comp-ids output-ids]} (:index diagram)
        rev-adj (build-reverse-adjacency (:edges diagram))
        reaches-output (reachable-backwards rev-adj output-ids)
        dead (remove reaches-output comp-ids)]
    {:valid (empty? dead)
     :check :coverage
     :dead-components (vec dead)}))

;;; ============================================================
;;; Validation: No Orphan Inputs
;;; ============================================================

(defn validate-no-orphan-inputs
  "Every input port must connect to at least one component or output.
   An input that connects to nothing is either unnecessary or a missing edge.

   Returns {:valid bool :orphan-inputs [...]}"
  [diagram]
  (let [{:keys [input-ids]} (:index diagram)
        adj (build-adjacency (:edges diagram))
        orphans (filter (fn [id] (empty? (get adj id []))) input-ids)]
    {:valid (empty? orphans)
     :check :no-orphan-inputs
     :orphan-inputs (vec orphans)}))

;;; ============================================================
;;; Validation: Type Safety
;;; ============================================================

(defn validate-type-safety
  "Every edge with a declared type must match the port types at both ends.
   Edges without types are unchecked (permissive for early-stage diagrams).

   Returns {:valid bool :type-errors [...]}"
  [diagram]
  (let [node-map (get-in diagram [:index :node-map])
        errors (atom [])]
    (doseq [edge (:edges diagram)]
      (let [from-node (get node-map (:from edge))
            to-node   (get node-map (:to edge))
            edge-type (:type edge)]
        ;; Check edge type against source's output type
        (when (and edge-type (:type from-node))
          (when-not (types-compatible? (:type from-node) edge-type)
            (swap! errors conj
                   {:edge edge
                    :problem :source-type-mismatch
                    :source-type (:type from-node)
                    :edge-type edge-type
                    :message (str (:id from-node) " outputs " (:type from-node)
                                  " but edge declares " edge-type)})))
        ;; Check edge type against destination's expected input type
        (when (and edge-type (:type to-node))
          (when-not (types-compatible? edge-type (:type to-node))
            (swap! errors conj
                   {:edge edge
                    :problem :dest-type-mismatch
                    :edge-type edge-type
                    :dest-type (:type to-node)
                    :message (str "Edge carries " edge-type
                                  " but " (:id to-node) " expects " (:type to-node))})))))
    {:valid (empty? @errors)
     :check :type-safety
     :type-errors @errors}))

;;; ============================================================
;;; Validation: Spec Coverage (output ports have spec refs)
;;; ============================================================

(defn validate-spec-coverage
  "Every output port should have a :spec-ref pointing to where it's specified.
   Outputs without spec-refs are stop-the-line violations per
   futon-theory/mission-interface-signature.

   Returns {:valid bool :unspecified-outputs [...]}"
  [diagram]
  (let [outputs (get-in diagram [:ports :output])
        unspecified (filter (fn [p] (nil? (:spec-ref p))) outputs)]
    {:valid (empty? unspecified)
     :check :spec-coverage
     :unspecified-outputs (vec (map :id unspecified))}))

;;; ============================================================
;;; Validate All
;;; ============================================================

(defn validate
  "Run all validations on a mission diagram.
   Returns {:all-valid bool :checks [...]}"
  [diagram]
  (let [checks [(validate-completeness diagram)
                (validate-coverage diagram)
                (validate-no-orphan-inputs diagram)
                (validate-type-safety diagram)
                (validate-spec-coverage diagram)]]
    {:all-valid (every? :valid checks)
     :checks checks
     :mission/id (:mission/id diagram)}))

;;; ============================================================
;;; Mission Composition
;;; ============================================================

(defn composable?
  "Can mission-a's outputs feed mission-b's inputs?
   Returns the set of matching port pairs, or empty if not composable."
  [diagram-a diagram-b]
  (let [a-outputs (get-in diagram-a [:ports :output])
        b-inputs  (get-in diagram-b [:ports :input])
        matches (for [out a-outputs
                      inp b-inputs
                      :when (types-compatible? (:type out) (:type inp))]
                  {:from-mission (:mission/id diagram-a)
                   :from-port (:id out)
                   :from-type (:type out)
                   :to-mission (:mission/id diagram-b)
                   :to-port (:id inp)
                   :to-type (:type inp)})]
    (vec matches)))

(defn compose-missions
  "Compose mission-a → mission-b by wiring matching output/input ports.
   Returns a new diagram representing the composed system, or nil if
   the missions share no compatible ports."
  [diagram-a diagram-b]
  (let [port-matches (composable? diagram-a diagram-b)]
    (when (seq port-matches)
      (let [matched-a-ports (set (map :from-port port-matches))
            matched-b-ports (set (map :to-port port-matches))
            ;; Remaining unmatched ports become the composed boundary
            remaining-a-outputs (remove #(matched-a-ports (:id %))
                                        (get-in diagram-a [:ports :output]))
            remaining-b-inputs  (remove #(matched-b-ports (:id %))
                                        (get-in diagram-b [:ports :input]))
            ;; All components from both missions
            all-components (concat
                            (map #(update % :id (fn [id] (keyword (str (name (:mission/id diagram-a)) "." (name id)))))
                                 (:components diagram-a))
                            (map #(update % :id (fn [id] (keyword (str (name (:mission/id diagram-b)) "." (name id)))))
                                 (:components diagram-b)))
            ;; Prefix edges
            prefix-edge (fn [mission-id edge]
                          (let [pfx (name mission-id)]
                            (-> edge
                                (update :from (fn [id] (keyword (str pfx "." (name id)))))
                                (update :to   (fn [id] (keyword (str pfx "." (name id))))))))
            a-edges (map (partial prefix-edge (:mission/id diagram-a)) (:edges diagram-a))
            b-edges (map (partial prefix-edge (:mission/id diagram-b)) (:edges diagram-b))
            ;; Composition edges (a-output → b-input)
            comp-edges (map (fn [{:keys [from-port to-port from-type]}]
                              {:from (keyword (str (name (:mission/id diagram-a)) "." (name from-port)))
                               :to   (keyword (str (name (:mission/id diagram-b)) "." (name to-port)))
                               :type from-type})
                            port-matches)]
        (mission-diagram
          {:mission/id (keyword (str (name (:mission/id diagram-a))
                                     "→"
                                     (name (:mission/id diagram-b))))
           :mission/state :composed
           :ports {:input  (concat (get-in diagram-a [:ports :input])
                                   (vec remaining-b-inputs))
                   :output (concat (vec remaining-a-outputs)
                                   (get-in diagram-b [:ports :output]))}
           :components (vec all-components)
           :edges (vec (concat a-edges b-edges comp-edges))})))))

;;; ============================================================
;;; Rendering (EDN → Mermaid for human review)
;;; ============================================================

(defn diagram->mermaid
  "Render a mission diagram as a mermaid graph LR string.
   This is the human-readable shadow of the machine-checkable EDN."
  [diagram]
  (let [sb (StringBuilder.)]
    (.append sb "graph LR\n")
    ;; Input subgraph
    (.append sb "    subgraph inputs\n")
    (doseq [p (get-in diagram [:ports :input])]
      (.append sb (format "        %s[\"%s<br/>%s\"]\n"
                          (name (:id p)) (:name p) (name (:type p)))))
    (.append sb "    end\n\n")
    ;; Components subgraph
    (.append sb (format "    subgraph %s\n" (name (:mission/id diagram))))
    (doseq [c (:components diagram)]
      (.append sb (format "        %s[%s]\n" (name (:id c)) (:name c))))
    ;; Internal edges (component→component)
    (let [comp-ids (get-in diagram [:index :comp-ids])]
      (doseq [e (:edges diagram)
              :when (and (comp-ids (:from e)) (comp-ids (:to e)))]
        (.append sb (format "        %s --> %s\n" (name (:from e)) (name (:to e))))))
    (.append sb "    end\n\n")
    ;; Output subgraph
    (.append sb "    subgraph outputs\n")
    (doseq [p (get-in diagram [:ports :output])]
      (.append sb (format "        %s[\"%s<br/>%s\"]\n"
                          (name (:id p)) (:name p)
                          (if (:spec-ref p)
                            (str (name (:type p)) " / " (:spec-ref p))
                            (name (:type p))))))
    (.append sb "    end\n\n")
    ;; Cross-boundary edges
    (let [{:keys [input-ids output-ids comp-ids]} (:index diagram)]
      (doseq [e (:edges diagram)
              :when (or (input-ids (:from e))
                        (output-ids (:to e)))]
        (.append sb (format "    %s --> %s\n" (name (:from e)) (name (:to e))))))
    (.toString sb)))

;;; ============================================================
;;; Summary
;;; ============================================================

(defn summary
  "Human-readable summary of a mission diagram."
  [diagram]
  (let [v (validate diagram)]
    {:mission/id (:mission/id diagram)
     :mission/state (:mission/state diagram)
     :input-count (count (get-in diagram [:ports :input]))
     :output-count (count (get-in diagram [:ports :output]))
     :component-count (count (:components diagram))
     :edge-count (count (:edges diagram))
     :all-valid (:all-valid v)
     :failed-checks (vec (keep (fn [c] (when-not (:valid c) (:check c)))
                                (:checks v)))}))
