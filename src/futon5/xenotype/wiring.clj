(ns futon5.xenotype.wiring
  (:require [clojure.edn :as edn]))

(def ^:private default-components-path
  "futon5/resources/xenotype-generator-components.edn")

(defn load-components
  ([] (load-components default-components-path))
  ([path]
   (edn/read-string (slurp path))))

(defn component-def
  [lib component-id]
  (get-in lib [:components component-id]))

(defn- inputs-map
  [component]
  (into {} (map (fn [[name type]] [name type]) (:inputs component))))

(defn- outputs-map
  [component]
  (into {} (map (fn [[name type]] [name type]) (:outputs component))))

(defn- default-port
  [ports]
  (first (keys ports)))

(defn- node-id-set
  [diagram]
  (set (map :id (:nodes diagram))))

(defn- edge->input-type
  [lib diagram edge]
  (let [to-id (:to edge)
        node (first (filter #(= (:id %) to-id) (:nodes diagram)))
        component (component-def lib (:component node))
        inputs (inputs-map component)
        to-port (or (:to-port edge) (default-port inputs))]
    (get inputs to-port)))

(defn- edge->output-type
  [lib diagram edge]
  (let [from-id (:from edge)
        node (first (filter #(= (:id %) from-id) (:nodes diagram)))
        component (component-def lib (:component node))
        outputs (outputs-map component)
        from-port (or (:from-port edge) (default-port outputs))]
    (get outputs from-port)))

(defn validate-diagram
  "Validate a wiring diagram against the component library.
  Returns {:ok? bool :errors [..] :warnings [..]}."
  [lib diagram]
  (let [nodes (:nodes diagram)
        edges (:edges diagram)
        node-ids (node-id-set diagram)
        errors (atom [])
        warnings (atom [])
        implicit-ports #{:summary :frames :run :hex-score :series}
        add-error (fn [msg] (swap! errors conj msg))
        add-warning (fn [msg] (swap! warnings conj msg))]
    (doseq [node nodes]
      (when-not (:id node)
        (add-error "node missing :id"))
      (when-not (:component node)
        (add-error (str "node missing :component: " (:id node))))
      (when-not (component-def lib (:component node))
        (add-error (str "unknown component: " (:component node)))))
    (when (not= (count node-ids) (count nodes))
      (add-error "duplicate node ids"))
    (doseq [edge edges]
      (let [from-id (:from edge)
            to-id (:to edge)]
        (when (and from-id (not (contains? node-ids from-id)))
          (add-error (str "edge references unknown :from node " from-id)))
        (when (and to-id (not (contains? node-ids to-id)))
          (add-error (str "edge references unknown :to node " to-id)))
        (if (:value edge)
          (let [expected (edge->input-type lib diagram edge)
                provided (:value-type edge)]
            (when (and expected provided (not= expected provided))
              (add-error (str "edge value type mismatch to " to-id ":" expected " vs " provided))))
          (let [out-type (edge->output-type lib diagram edge)
                in-type (edge->input-type lib diagram edge)]
            (when (nil? out-type)
              (add-error (str "edge missing output port type from " from-id)))
            (when (nil? in-type)
              (add-error (str "edge missing input port type to " to-id)))
            (when (and out-type in-type)
              (when (not (or (= out-type in-type)
                             (and (= in-type :scalar-list) (= out-type :scalar))))
                (add-error (str "edge type mismatch " from-id " -> " to-id ": " out-type " vs " in-type)))))))
      (when (and (nil? (:from edge)) (nil? (:value edge)))
        (add-warning "edge has neither :from nor :value")))
    (let [input-edges (group-by :to edges)]
      (doseq [node nodes]
        (let [component (component-def lib (:component node))
              inputs (inputs-map component)
              used-ports (set (map :to-port (get input-edges (:id node))))]
          (doseq [port (keys inputs)]
            (when-not (or (contains? used-ports port)
                          (contains? implicit-ports port))
              (add-warning (str "unwired input " (:id node) ":" port)))))))
    {:ok? (empty? @errors)
     :errors @errors
     :warnings @warnings}))

(defn example-diagram
  []
  {:nodes [{:id :n0 :component :short}
           {:id :n1 :component :envelope}
           {:id :n2 :component :disagreement}
           {:id :n3 :component :threshold-gate}]
   :edges [{:from :n0 :from-port :score :to :n2 :to-port :scores}
           {:from :n1 :from-port :score :to :n2 :to-port :scores}
           {:from :n2 :from-port :sigma :to :n3 :to-port :score}
           {:value 2.0 :value-type :scalar :to :n3 :to-port :threshold}]
   :output :n3})
