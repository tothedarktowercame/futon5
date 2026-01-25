(ns futon5.wiring.features
  "Extract structural features from wiring diagrams for learning."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]))

;; === Feature Extraction ===

(defn node-ids
  "Extract all node IDs from a wiring diagram."
  [diagram]
  (set (map :id (:nodes diagram))))

(defn component-types
  "Extract set of component types used."
  [diagram]
  (set (map :component (:nodes diagram))))

(defn has-component?
  "Check if wiring uses a specific component type."
  [diagram component-kw]
  (contains? (component-types diagram) component-kw))

(defn edge-count
  "Count edges in the diagram."
  [diagram]
  (count (:edges diagram)))

(defn node-count
  "Count nodes in the diagram."
  [diagram]
  (count (:nodes diagram)))

(defn nodes-with-params
  "Count nodes that have custom params."
  [diagram]
  (count (filter :params (:nodes diagram))))

(defn in-degree
  "Count incoming edges to a node."
  [diagram node-id]
  (count (filter #(= node-id (:to %)) (:edges diagram))))

(defn out-degree
  "Count outgoing edges from a node."
  [diagram node-id]
  (count (filter #(= node-id (:from %)) (:edges diagram))))

(defn max-in-degree
  "Maximum incoming edges to any node."
  [diagram]
  (if-let [nodes (seq (node-ids diagram))]
    (apply max (map #(in-degree diagram %) nodes))
    0))

(defn max-out-degree
  "Maximum outgoing edges from any node."
  [diagram]
  (if-let [nodes (seq (node-ids diagram))]
    (apply max (map #(out-degree diagram %) nodes))
    0))

(defn value-edges
  "Count edges that inject constant values."
  [diagram]
  (count (filter :value (:edges diagram))))

(defn context-nodes
  "Count context-* nodes (input extraction)."
  [diagram]
  (count (filter #(and (:component %)
                       (let [c (name (:component %))]
                         (or (.startsWith c "context-")
                             (.contains c "neighbor"))))
                 (:nodes diagram))))

(defn gate-nodes
  "Count threshold/gate nodes (conditional logic)."
  [diagram]
  (count (filter #(and (:component %)
                       (let [c (name (:component %))]
                         (or (.contains c "threshold")
                             (.contains c "gate"))))
                 (:nodes diagram))))

(defn legacy-nodes
  "Count legacy-kernel-step nodes."
  [diagram]
  (count (filter #(= :legacy-kernel-step (:component %))
                 (:nodes diagram))))

(defn creative-nodes
  "Count creative/mutation nodes (xor, mutate, etc)."
  [diagram]
  (count (filter #(and (:component %)
                       (let [c (name (:component %))]
                         (or (.contains c "xor")
                             (.contains c "mutate")
                             (.contains c "creative"))))
                 (:nodes diagram))))

(defn diversity-nodes
  "Count diversity measurement nodes."
  [diagram]
  (count (filter #(and (:component %)
                       (.contains (name (:component %)) "diversity"))
                 (:nodes diagram))))

;; === High-Level Features ===

(defn extract-features
  "Extract all structural features from a wiring diagram.
   Returns a map of feature-name → value."
  [wiring]
  (let [diagram (:diagram wiring)]
    (if diagram
      {:node-count (node-count diagram)
       :edge-count (edge-count diagram)
       :nodes-with-params (nodes-with-params diagram)
       :value-edges (value-edges diagram)
       :context-nodes (context-nodes diagram)
       :gate-nodes (gate-nodes diagram)
       :legacy-nodes (legacy-nodes diagram)
       :creative-nodes (creative-nodes diagram)
       :diversity-nodes (diversity-nodes diagram)
       :max-in-degree (max-in-degree diagram)
       :max-out-degree (max-out-degree diagram)
       ;; Boolean features
       :has-legacy? (pos? (legacy-nodes diagram))
       :has-gates? (pos? (gate-nodes diagram))
       :has-creative? (pos? (creative-nodes diagram))
       :has-diversity? (pos? (diversity-nodes diagram))
       ;; Derived features
       :complexity (+ (node-count diagram) (edge-count diagram))
       :gate-ratio (if (pos? (node-count diagram))
                     (double (/ (gate-nodes diagram) (node-count diagram)))
                     0.0)
       :creative-ratio (if (pos? (node-count diagram))
                         (double (/ (creative-nodes diagram) (node-count diagram)))
                         0.0)}
      ;; No diagram - probably direct exotype specification
      {:node-count 0
       :edge-count 0
       :has-legacy? (boolean (:exotype wiring))
       :has-gates? false
       :has-creative? false
       :has-diversity? false
       :complexity 0
       :is-direct-exotype? true})))

(defn load-wiring
  "Load a wiring from an EDN file."
  [path]
  (try
    (edn/read-string {:readers {'object (fn [_] nil)}}
                     (slurp path))
    (catch Exception e
      (println "Warning: Could not load" path ":" (.getMessage e))
      nil)))

(defn extract-features-from-file
  "Load wiring and extract features."
  [path]
  (when-let [wiring (load-wiring path)]
    (assoc (extract-features wiring)
           :path path
           :id (or (get-in wiring [:meta :id])
                   (keyword (.getName (io/file path)))))))
