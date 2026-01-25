(ns futon5.wiring.embedding
  "Structural embedding of wiring diagrams via path signatures.

   Each wiring is characterized by the set of paths from context inputs
   to output. Two wirings with isomorphic path sets compute equivalent
   functions.

   Future: Weisfeiler-Lehman graph kernel for richer embeddings."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]))

;;; ============================================================
;;; Graph Construction
;;; ============================================================

(defn diagram->graph
  "Convert wiring diagram to adjacency structure.
   Returns {:nodes {id {:component ...}} :adj {from-id [{:to to-id :from-port :to-port}]}}"
  [diagram]
  (let [nodes (into {} (map (fn [n] [(:id n) n]) (:nodes diagram)))
        adj (reduce (fn [m edge]
                      (update m (:from edge) (fnil conj [])
                              {:to (:to edge)
                               :from-port (:from-port edge)
                               :to-port (:to-port edge)}))
                    {}
                    (:edges diagram))]
    {:nodes nodes
     :adj adj
     :output (:output diagram)}))

(defn input-nodes
  "Find all input nodes (context-* components)."
  [graph]
  (filter (fn [[id node]]
            (let [comp (:component node)]
              (and (keyword? comp)
                   (.startsWith (name comp) "context-"))))
          (:nodes graph)))

(defn output-node
  "Find the output node."
  [graph]
  (or (:output graph)
      (first (filter (fn [[id node]]
                       (let [comp (:component node)]
                         (and (keyword? comp)
                              (.startsWith (name comp) "output"))))
                     (:nodes graph)))))

;;; ============================================================
;;; Path Extraction
;;; ============================================================

(defn- all-paths-from
  "Find all paths from start-id to target-id using DFS.
   Returns list of paths, each path is [{:node-id :component :port} ...]"
  [graph start-id target-id]
  (let [adj (:adj graph)
        nodes (:nodes graph)]
    (loop [stack [[start-id [{:node-id start-id
                              :component (:component (get nodes start-id))
                              :port :out}]]]
           paths []]
      (if (empty? stack)
        paths
        (let [[current-id path] (peek stack)
              stack' (pop stack)]
          (if (= current-id target-id)
            (recur stack' (conj paths path))
            (let [edges (get adj current-id [])
                  next-items (for [{:keys [to from-port to-port]} edges]
                               [to (conj path {:node-id to
                                               :component (:component (get nodes to))
                                               :from-port from-port
                                               :to-port to-port})])]
              (recur (into stack' next-items) paths))))))))

(defn extract-paths
  "Extract all paths from context inputs to output.
   Returns set of paths, each path is a vector of {:component :from-port :to-port}."
  [diagram]
  (let [graph (diagram->graph diagram)
        inputs (input-nodes graph)
        output-id (if (keyword? (output-node graph))
                    (output-node graph)
                    (first (output-node graph)))]
    (when output-id
      (set
       (for [[input-id _] inputs
             path (all-paths-from graph input-id output-id)]
         ;; Simplify path to just component and ports
         (mapv (fn [step]
                 (select-keys step [:component :from-port :to-port]))
               path))))))

;;; ============================================================
;;; Path Signatures
;;; ============================================================

(defn path->signature
  "Convert a path to a canonical string signature."
  [path]
  (->> path
       (map (fn [{:keys [component from-port to-port]}]
              (str (name component)
                   (when to-port (str "." (name to-port))))))
       (interpose "→")
       (apply str)))

(defn paths->signature-set
  "Convert set of paths to set of string signatures."
  [paths]
  (set (map path->signature paths)))

(defn wiring-signature
  "Compute the path signature for a wiring."
  [wiring]
  (-> (:diagram wiring)
      extract-paths
      paths->signature-set))

;;; ============================================================
;;; Comparison & Distance
;;; ============================================================

(defn jaccard-similarity
  "Jaccard similarity between two sets."
  [a b]
  (if (and (empty? a) (empty? b))
    1.0
    (let [intersection (count (set/intersection a b))
          union (count (set/union a b))]
      (/ (double intersection) union))))

(defn jaccard-distance
  "Jaccard distance (1 - similarity)."
  [a b]
  (- 1.0 (jaccard-similarity a b)))

(defn signature-distance
  "Distance between two wirings based on path signatures."
  [wiring-a wiring-b]
  (jaccard-distance (wiring-signature wiring-a)
                    (wiring-signature wiring-b)))

(defn signature-similarity
  "Similarity between two wirings based on path signatures."
  [wiring-a wiring-b]
  (jaccard-similarity (wiring-signature wiring-a)
                      (wiring-signature wiring-b)))

;;; ============================================================
;;; Landmark Distances
;;; ============================================================

(def landmark-paths
  "Standard paths for elementary CA rule landmarks."
  [:rule-090 :rule-110 :rule-030 :rule-184 :rule-054])

(defn load-landmarks
  "Load landmark wirings from standard paths."
  []
  (into {}
        (for [id landmark-paths]
          (let [path (str "data/wiring-rules/" (name id) ".edn")]
            [id (edn/read-string (slurp path))]))))

(defn landmark-coordinates
  "Compute similarity to each landmark wiring.
   Returns map of {:rule-090 sim :rule-110 sim ...}"
  [wiring landmarks]
  (let [wiring-sig (wiring-signature wiring)]
    (into {}
          (for [[id landmark] landmarks]
            [id (jaccard-similarity wiring-sig (wiring-signature landmark))]))))

(defn landmark-vector
  "Return landmark similarities as a vector in standard order."
  [wiring landmarks]
  (let [coords (landmark-coordinates wiring landmarks)]
    (mapv #(get coords % 0.0) landmark-paths)))

;;; ============================================================
;;; Analysis Helpers
;;; ============================================================

(defn signature-report
  "Generate a report of path signatures for a wiring."
  [wiring]
  (let [paths (extract-paths (:diagram wiring))
        sigs (paths->signature-set paths)]
    {:wiring-id (get-in wiring [:meta :id])
     :path-count (count paths)
     :signatures (sort sigs)
     :input-components (set (map (comp :component first) paths))
     :max-depth (apply max (map count paths))}))

(defn compare-wirings
  "Compare two wirings and show their path differences."
  [wiring-a wiring-b]
  (let [sig-a (wiring-signature wiring-a)
        sig-b (wiring-signature wiring-b)]
    {:similarity (jaccard-similarity sig-a sig-b)
     :only-in-a (set/difference sig-a sig-b)
     :only-in-b (set/difference sig-b sig-a)
     :shared (set/intersection sig-a sig-b)}))
