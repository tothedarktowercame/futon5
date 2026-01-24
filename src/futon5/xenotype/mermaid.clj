(ns futon5.xenotype.mermaid
  "Render xenotype wiring diagrams as Mermaid flowcharts."
  (:require [clojure.string :as str]))

(defn- node-label
  "Generate a label for a node."
  [node]
  (let [component (name (:component node))
        params (:params node)]
    (if (seq params)
      (str component "<br/>" (pr-str params))
      component)))

(defn- node-shape
  "Determine node shape based on component type."
  [component]
  (cond
    (str/starts-with? (name component) "context-") :stadium    ;; Rounded for inputs
    (str/starts-with? (name component) "output") :stadium      ;; Rounded for outputs
    (#{:if-then-else-sigil :threshold-sigil :threshold?} component) :diamond  ;; Diamond for conditionals
    :else :rectangle))

(defn- shape-syntax
  "Return mermaid syntax for a shape."
  [shape id label]
  (case shape
    :stadium (str id "([" label "])")
    :diamond (str id "{" label "}")
    :rectangle (str id "[" label "]")))

(defn- edge-label
  "Generate label for an edge."
  [edge]
  (if-let [v (:value edge)]
    (str v)
    (let [from-port (when (:from-port edge) (name (:from-port edge)))
          to-port (when (:to-port edge) (name (:to-port edge)))]
      (cond
        (and from-port to-port) (str from-port " → " to-port)
        from-port from-port
        to-port to-port
        :else ""))))

(defn wiring->mermaid
  "Convert a wiring diagram to Mermaid flowchart syntax.

   Options:
   - :direction - :LR (left-right) or :TD (top-down), default :LR
   - :title - optional title to add as comment"
  ([diagram] (wiring->mermaid diagram {}))
  ([{:keys [nodes edges output]} {:keys [direction title] :or {direction :LR}}]
   (let [lines (atom [])
         add-line (fn [& parts] (swap! lines conj (str/join parts)))
         node-ids (set (map :id nodes))
         output-id output]

     ;; Header
     (when title
       (add-line "%% " title))
     (add-line "graph " (name direction))
     (add-line "")

     ;; Nodes
     (add-line "    %% Nodes")
     (doseq [node nodes]
       (let [id (name (:id node))
             shape (node-shape (:component node))
             label (node-label node)
             is-output (= (:id node) output-id)]
         (add-line "    " (shape-syntax shape id label))
         (when is-output
           (add-line "    style " id " stroke:#333,stroke-width:3px"))))
     (add-line "")

     ;; Edges
     (add-line "    %% Edges")
     (doseq [edge edges]
       (let [from-id (when (:from edge) (name (:from edge)))
             to-id (name (:to edge))
             label (edge-label edge)]
         (if from-id
           ;; Normal edge
           (if (seq label)
             (add-line "    " from-id " -->|" label "| " to-id)
             (add-line "    " from-id " --> " to-id))
           ;; Value edge (constant input)
           (let [value-id (str "val_" (gensym))]
             (add-line "    " value-id "((" (:value edge) "))")
             (add-line "    " value-id " -->|" (or (name (:to-port edge)) "") "| " to-id)))))

     (str/join "\n" @lines))))

(defn save-mermaid
  "Save a wiring diagram as a .mmd file."
  [diagram path & [opts]]
  (spit path (wiring->mermaid diagram opts)))

(defn print-mermaid
  "Print a wiring diagram as Mermaid syntax."
  [diagram & [opts]]
  (println (wiring->mermaid diagram opts)))

(comment
  ;; Example usage:
  (def example-diagram
    {:nodes [{:id :ctx-self :component :context-self}
             {:id :ctx-pred :component :context-pred}
             {:id :xor :component :bit-xor}
             {:id :out :component :output-sigil}]
     :edges [{:from :ctx-self :from-port :sigil :to :xor :to-port :a}
             {:from :ctx-pred :from-port :sigil :to :xor :to-port :b}
             {:from :xor :from-port :result :to :out :to-port :sigil}]
     :output :out})

  (print-mermaid example-diagram {:title "XOR Example"}))
