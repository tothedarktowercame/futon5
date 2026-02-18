(ns futon5.ct.mission
  "Category-theoretic validation for mission architecture diagrams.

   Models missions as morphisms in a category where:
   - Objects = port types (data shapes at mission boundaries)
   - Morphisms = components (internal transformations)
   - Composition = connecting outputs of mission A to inputs of mission B

   Validates (structural):
   - Completeness: every output port reachable from some input
   - Coverage: every component reaches at least one output (no dead components)
   - Type safety: wire types match at connection points
   - No orphan inputs: every input connects to something
   - Spec coverage: every output has a spec-ref

   Validates (invariant — from Chapter 0 meta-theory):
   - I3 Timescale ordering: slow constrains fast, never fast→slow
   - I4 Exogeneity: no path from outputs to constraint inputs
   - I6 Compositional closure: no single-point-of-failure components

   Composition: mission A → mission B requires matching port types

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
    :evidence-entry     ; typed evidence record (EvidenceEntry shape)
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
   [:config :edn-document]        true   ; config is EDN
   [:xtdb-entity :evidence-entry] true   ; evidence entries are storable entities
   [:evidence-entry :xtdb-entity] true}) ; evidence entries can be stored

(defn types-compatible?
  "Check if from-type can wire to where to-type is expected."
  [from-type to-type]
  (or (= from-type to-type)
      (get type-coercions [from-type to-type] false)))

;;; ============================================================
;;; Timescales (Invariant I3)
;;; ============================================================

(def timescale-order
  "Ordered timescales from fastest to slowest.
   Index = speed rank. Higher rank = slower."
  [:social :fast :medium :slow :glacial])

(def timescale-rank
  "Map timescale keyword to numeric rank for comparison."
  (into {} (map-indexed (fn [i ts] [ts i]) timescale-order)))

(defn timescale-<=
  "Is timescale a the same speed or faster than timescale b?"
  [a b]
  (<= (get timescale-rank a 0)
      (get timescale-rank b 0)))

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
                                           (map (fn [p] [(:id p) (merge p {:role :input})]) (:input ports))
                                           (map (fn [p] [(:id p) (merge p {:role :output})]) (:output ports))
                                           (map (fn [c] [(:id c) (merge c {:role :component})]) components)))}}))

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

(defn- node-can-produce?
  "Can this node produce the given type?
   - Ports: edge type must be compatible with port :type
   - Components: edge type must be in :produces set (if declared),
     otherwise unchecked (permissive for early-stage diagrams)"
  [node edge-type]
  (if (= :component (:role node))
    (if-let [produces (:produces node)]
      (some #(types-compatible? % edge-type) produces)
      true) ; no :produces declared → permissive
    ;; Port: check :type
    (if (:type node)
      (types-compatible? (:type node) edge-type)
      true)))

(defn- node-can-accept?
  "Can this node accept the given type?
   - Ports: edge type must be compatible with port :type
   - Components: edge type must be in :accepts set (if declared),
     otherwise unchecked (permissive for early-stage diagrams)"
  [node edge-type]
  (if (= :component (:role node))
    (if-let [accepts (:accepts node)]
      (some #(types-compatible? edge-type %) accepts)
      true) ; no :accepts declared → permissive
    ;; Port: check :type
    (if (:type node)
      (types-compatible? edge-type (:type node))
      true)))

(defn validate-type-safety
  "Every edge with a declared type must match the port types at both ends.
   Edges without types are unchecked (permissive for early-stage diagrams).

   Components are morphisms — they have :accepts (domain) and :produces
   (codomain) type sets, separate from their identity :type. If a component
   declares neither, its edges are unchecked.

   Returns {:valid bool :type-errors [...]}"
  [diagram]
  (let [node-map (get-in diagram [:index :node-map])
        errors (atom [])]
    (doseq [edge (:edges diagram)]
      (let [from-node (get node-map (:from edge))
            to-node   (get node-map (:to edge))
            edge-type (:type edge)]
        ;; Check edge type against source's output capability
        (when (and edge-type from-node)
          (when-not (node-can-produce? from-node edge-type)
            (swap! errors conj
                   {:edge edge
                    :problem :source-type-mismatch
                    :produces (:produces from-node)
                    :edge-type edge-type
                    :message (str (:id from-node) " produces "
                                  (or (:produces from-node) (:type from-node))
                                  " but edge declares " edge-type)})))
        ;; Check edge type against destination's input capability
        (when (and edge-type to-node)
          (when-not (node-can-accept? to-node edge-type)
            (swap! errors conj
                   {:edge edge
                    :problem :dest-type-mismatch
                    :edge-type edge-type
                    :accepts (:accepts to-node)
                    :message (str "Edge carries " edge-type
                                  " but " (:id to-node) " accepts "
                                  (or (:accepts to-node) (:type to-node)))})))))
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
;;; Invariant I3: Timescale Ordering
;;; ============================================================

(defn validate-timescale-ordering
  "Invariant I3: timescale separation.

   For edges where both endpoints declare :timescale:
   - Normal data edges: any direction is fine (fast→medium→slow is
     normal data flow, e.g., request → storage)
   - Edges TO a :constraint node: source must be same-or-slower
     than target. Fast dynamics must not write to constraint nodes.

   Returns {:valid bool :violations [...]}"
  [diagram]
  (let [node-map (get-in diagram [:index :node-map])
        violations (atom [])]
    (doseq [edge (:edges diagram)]
      (let [from-node (get node-map (:from edge))
            to-node   (get node-map (:to edge))
            from-ts   (:timescale from-node)
            to-ts     (:timescale to-node)]
        ;; Only flag: fast node writing to a constraint node
        (when (and from-ts to-ts
                   (timescale-rank from-ts) (timescale-rank to-ts)
                   (:constraint to-node)
                   (< (get timescale-rank from-ts 0)
                      (get timescale-rank to-ts 0)))
          (swap! violations conj
                 {:edge edge
                  :from-timescale from-ts
                  :to-timescale to-ts
                  :message (str (:from edge) " (" (name from-ts) ") writing to constraint "
                                (:to edge) " (" (name to-ts) ")"
                                " — fast dynamics must not write to slow constraints")}))))
    {:valid (empty? @violations)
     :check :timescale-ordering
     :violations @violations}))

;;; ============================================================
;;; Invariant I4: Preference Exogeneity
;;; ============================================================

(defn validate-exogeneity
  "Invariant I4: no directed path from any output port to any input
   port marked :role :constraint. If such a path exists, the system's
   fast dynamics can influence its own constraints — wireheading.

   Input ports with :constraint true are 'preferences' that must only
   be writable from outside the diagram (exogenous).

   Returns {:valid bool :vulnerable-paths [...]}"
  [diagram]
  (let [node-map    (get-in diagram [:index :node-map])
        output-ids  (get-in diagram [:index :output-ids])
        constraint-ids (set (keep (fn [[id node]]
                                    (when (:constraint node) id))
                                  node-map))
        ;; Build reverse adjacency to find what can reach constraint nodes
        rev-adj (build-reverse-adjacency (:edges diagram))
        ;; Find everything that can reach any constraint node
        reaches-constraint (reachable-backwards rev-adj constraint-ids)
        ;; Any output port that can reach a constraint is a vulnerability
        vulnerable (filter reaches-constraint output-ids)]
    {:valid (empty? vulnerable)
     :check :exogeneity
     :vulnerable-paths (vec (map (fn [out-id]
                                   {:output out-id
                                    :message (str out-id " has a path to constraint input(s) "
                                                  (vec (filter constraint-ids
                                                               (reachable-from
                                                                 (build-adjacency (:edges diagram))
                                                                 #{out-id}))))})
                                 vulnerable))}))

;;; ============================================================
;;; Invariant I6: Compositional Closure (Single Point of Failure)
;;; ============================================================

(defn validate-closure
  "Invariant I6: no single component failure should make ALL outputs
   unreachable. If removing one component disconnects every output
   from every input, that component is a single point of failure and
   the diagram lacks compositional closure.

   Returns {:valid bool :single-points-of-failure [...]}"
  [diagram]
  (let [{:keys [input-ids output-ids comp-ids]} (:index diagram)
        edges (:edges diagram)
        spofs (atom [])]
    (doseq [comp-id comp-ids]
      ;; Remove this component and all its edges
      (let [edges-without (filterv (fn [e]
                                     (and (not= (:from e) comp-id)
                                          (not= (:to e) comp-id)))
                                   edges)
            adj (build-adjacency edges-without)
            reachable (reachable-from adj input-ids)
            reachable-outputs (filter reachable output-ids)]
        (when (empty? reachable-outputs)
          (swap! spofs conj
                 {:component comp-id
                  :message (str "Removing " comp-id
                                " disconnects ALL outputs from inputs")}))))
    {:valid (empty? @spofs)
     :check :compositional-closure
     :single-points-of-failure @spofs}))

;;; ============================================================
;;; Validate All
;;; ============================================================

(defn validate
  "Run all validations on a mission diagram.
   Structural checks (completeness, coverage, orphans, types, spec)
   plus invariant checks (I3 timescale, I4 exogeneity, I6 closure).
   Returns {:all-valid bool :checks [...]}"
  [diagram]
  (let [checks [(validate-completeness diagram)
                (validate-coverage diagram)
                (validate-no-orphan-inputs diagram)
                (validate-type-safety diagram)
                (validate-spec-coverage diagram)
                (validate-timescale-ordering diagram)
                (validate-exogeneity diagram)
                (validate-closure diagram)]]
    {:all-valid (every? :valid checks)
     :checks checks
     :mission/id (:mission/id diagram)}))

;;; ============================================================
;;; Mission Composition
;;; ============================================================

(defn- prefixed-id
  [mission-id id]
  (keyword (str (name mission-id) "." (name id))))

(defn- shared-constraint-ports
  "Return a map of port-id -> merged port definition for shared constraint inputs.

  A shared constraint input is defined as:
  - present in both missions' :ports :input
  - :constraint true in both
  - same :id and same :type

  If core attributes differ, throw: shared constraints are intended as a stable
  contract between diagrams."
  [diagram-a diagram-b]
  (let [a-ports (->> (get-in diagram-a [:ports :input])
                     (filter :constraint)
                     (map (juxt :id identity))
                     (into {}))
        b-ports (->> (get-in diagram-b [:ports :input])
                     (filter :constraint)
                     (map (juxt :id identity))
                     (into {}))
        shared-ids (clojure.set/intersection (set (keys a-ports))
                                             (set (keys b-ports)))]
    (reduce
      (fn [m id]
        (let [a (get a-ports id)
              b (get b-ports id)]
          (when-not (= (:type a) (:type b))
            (throw (ex-info "Shared constraint input has mismatched :type."
                            {:port/id id
                             :diagram-a/type (:type a)
                             :diagram-b/type (:type b)})))
          (let [a-core (select-keys a [:id :type :constraint :timescale])
                b-core (select-keys b [:id :type :constraint :timescale])]
            (when-not (= a-core b-core)
              (throw (ex-info "Shared constraint input has mismatched core attributes."
                              {:port/id id
                               :diagram-a a-core
                               :diagram-b b-core}))))
          (assoc m id a)))
      {}
      shared-ids)))

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
            prefix-port (fn [mission-id p]
                          (update p :id (fn [id] (prefixed-id mission-id id))))
            ;; All components from both missions
            all-components (concat
                            (map #(update % :id (fn [id] (prefixed-id (:mission/id diagram-a) id)))
                                 (:components diagram-a))
                            (map #(update % :id (fn [id] (prefixed-id (:mission/id diagram-b) id)))
                                 (:components diagram-b)))
            ;; Prefix edges
            prefix-edge (fn [mission-id edge]
                          (-> edge
                              (update :from (fn [id] (prefixed-id mission-id id)))
                              (update :to   (fn [id] (prefixed-id mission-id id)))))
            a-edges (map (partial prefix-edge (:mission/id diagram-a)) (:edges diagram-a))
            b-edges (map (partial prefix-edge (:mission/id diagram-b)) (:edges diagram-b))
            ;; Composition edges (a-output → b-input)
            comp-edges (map (fn [{:keys [from-port to-port from-type]}]
                              {:from (prefixed-id (:mission/id diagram-a) from-port)
                               :to   (prefixed-id (:mission/id diagram-b) to-port)
                               :type from-type})
                            port-matches)]
        (mission-diagram
          {:mission/id (keyword (str (name (:mission/id diagram-a))
                                     "→"
                                     (name (:mission/id diagram-b))))
           :mission/state :composed
           :ports {:input  (concat (map (partial prefix-port (:mission/id diagram-a))
                                        (get-in diagram-a [:ports :input]))
                                   (map (partial prefix-port (:mission/id diagram-b))
                                        (vec remaining-b-inputs)))
                   :output (concat (map (partial prefix-port (:mission/id diagram-a))
                                        (vec remaining-a-outputs))
                                   (map (partial prefix-port (:mission/id diagram-b))
                                        (get-in diagram-b [:ports :output])))}
           :components (vec all-components)
           :edges (vec (concat a-edges b-edges comp-edges))})))))

(defn compose-parallel
  "Compose diagram-a and diagram-b \"in parallel\" by:
  - merging shared constraint inputs (same :id, :type, :constraint true)
  - prefixing all other ids with the mission id
  - adding cross-diagram edges where output types in A match input types in B

  Returns a composed mission diagram, or nil if there are no shared constraints
  and no type-compatible cross-diagram connections."
  [diagram-a diagram-b]
  (let [a-id (:mission/id diagram-a)
        b-id (:mission/id diagram-b)
        shared (shared-constraint-ports diagram-a diagram-b)
        shared-ids (set (keys shared))
        matches (composable? diagram-a diagram-b)]
    (when (or (seq shared-ids) (seq matches))
      (let [shared-ports (->> (keys shared) sort (map shared) vec)
            matched-a-outputs (set (map :from-port matches))
            matched-b-inputs  (set (map :to-port matches))
            prefix-endpoint (fn [mission-id id]
                              (if (shared-ids id)
                                id
                                (prefixed-id mission-id id)))
            prefix-port (fn [mission-id p]
                          (if (shared-ids (:id p))
                            (get shared (:id p))
                            (update p :id (fn [id] (prefixed-id mission-id id)))))
            prefix-component (fn [mission-id c]
                               (update c :id (fn [id] (prefixed-id mission-id id))))
            prefix-edge (fn [mission-id e]
                          (-> e
                              (update :from (fn [id] (prefix-endpoint mission-id id)))
                              (update :to   (fn [id] (prefix-endpoint mission-id id)))))
            inputs (vec (concat shared-ports
                                (->> (get-in diagram-a [:ports :input])
                                     (remove (fn [p] (shared-ids (:id p))))
                                     (map (partial prefix-port a-id)))
                                (->> (get-in diagram-b [:ports :input])
                                     (remove (fn [p]
                                               (or (shared-ids (:id p))
                                                   ;; Inputs satisfied by cross-diagram wiring become internal.
                                                   (and (matched-b-inputs (:id p))
                                                        (not (shared-ids (:id p)))))))
                                     (map (partial prefix-port b-id)))))
            ;; Outputs that feed cross-diagram connections become internal wires,
            ;; not boundary outputs. Keeping them as outputs would create
            ;; output->constraint paths (violating I4) in composed diagrams.
            outputs (vec (concat (->> (get-in diagram-a [:ports :output])
                                      (remove (fn [p] (matched-a-outputs (:id p))))
                                      (map (partial prefix-port a-id)))
                                 (map (partial prefix-port b-id) (get-in diagram-b [:ports :output]))))
            components (vec (concat (map (partial prefix-component a-id) (:components diagram-a))
                                    (map (partial prefix-component b-id) (:components diagram-b))))
            comp-edges (map (fn [{:keys [from-port to-port from-type]}]
                              {:from (prefix-endpoint a-id from-port)
                               :to   (prefix-endpoint b-id to-port)
                               :type from-type})
                            matches)
            edges (vec (concat (map (partial prefix-edge a-id) (:edges diagram-a))
                               (map (partial prefix-edge b-id) (:edges diagram-b))
                               comp-edges))]
        (mission-diagram
          {:mission/id (keyword (str (name a-id) "||" (name b-id)))
           :mission/state :composed
           :ports {:input inputs
                   :output outputs}
           :components components
           :edges edges})))))

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
