(ns futon5.wiring.compose
  "Composition and breeding operations on wiring diagrams.

   Wirings are typed directed graphs, so we can:
   - Compose serially (output of A → input of B)
   - Compose in parallel with selection/blend
   - Graft subgraphs into existing wirings
   - Breed/crossover between wirings

   Types must match at connection points (sigil→sigil, etc.)."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]))

;;; ============================================================
;;; Helpers
;;; ============================================================

(defn- prefix-ids
  "Add prefix to all node IDs in a diagram to avoid collisions."
  [diagram prefix]
  (let [prefix-kw (fn [kw] (keyword (str (name prefix) "-" (name kw))))
        nodes (mapv (fn [n] (update n :id prefix-kw)) (:nodes diagram))
        edges (mapv (fn [e]
                      (cond-> e
                        (:from e) (update :from prefix-kw)
                        (:to e) (update :to prefix-kw)))
                    (:edges diagram))
        output (when (:output diagram) (prefix-kw (:output diagram)))]
    (cond-> {:nodes nodes :edges edges}
      output (assoc :output output))))

(defn- find-output-node
  "Find the output node in a diagram."
  [diagram]
  (or (:output diagram)
      (:id (first (filter #(#{:output-sigil :output-with-state} (:component %))
                          (:nodes diagram))))))

(defn- find-input-nodes
  "Find context input nodes."
  [diagram]
  (filter #(and (keyword? (:component %))
                (.startsWith (name (:component %)) "context-"))
          (:nodes diagram)))

(defn- remove-output-node
  "Remove the output node and its incoming edges."
  [diagram]
  (let [output-id (find-output-node diagram)
        nodes (filterv #(not= (:id %) output-id) (:nodes diagram))
        ;; Find what feeds into output
        output-edges (filter #(= (:to %) output-id) (:edges diagram))
        feed-node (first (map :from output-edges))
        edges (filterv #(not= (:to %) output-id) (:edges diagram))]
    {:nodes nodes
     :edges edges
     :feed-node feed-node}))

;;; ============================================================
;;; Serial Composition: A → B
;;; ============================================================

(defn compose-serial
  "Compose two wirings serially: output of A feeds into B.

   B should have a single 'main' input that receives A's output.
   By default, replaces B's :context-self with A's output.

   Options:
   - :input-component - which context in B to replace (default :context-self)"
  ([wiring-a wiring-b] (compose-serial wiring-a wiring-b {}))
  ([wiring-a wiring-b {:keys [input-component] :or {input-component :context-self}}]
   (let [;; Prefix both diagrams
         diag-a (prefix-ids (:diagram wiring-a) :a)
         diag-b (prefix-ids (:diagram wiring-b) :b)

         ;; Remove A's output node, get its feed
         {:keys [nodes edges feed-node]} (remove-output-node diag-a)
         a-nodes nodes
         a-edges edges
         a-feed feed-node

         ;; Find B's input node to replace
         b-input-id (keyword (str "b-" (name
                                        (:id (first (filter #(= (:component %) input-component)
                                                            (:nodes (:diagram wiring-b))))))))

         ;; Remove B's input node
         b-nodes (filterv #(not= (:id %) b-input-id) (:nodes diag-b))

         ;; Redirect edges that came FROM b-input to come from a-feed
         b-edges (mapv (fn [e]
                         (if (= (:from e) b-input-id)
                           (assoc e :from a-feed)
                           e))
                       (:edges diag-b))

         ;; Merge
         all-nodes (vec (concat a-nodes b-nodes))
         all-edges (vec (concat a-edges b-edges))

         new-meta {:id (keyword (str (name (get-in wiring-a [:meta :id] :a))
                                     "→"
                                     (name (get-in wiring-b [:meta :id] :b))))
                   :composition :serial
                   :components [(get-in wiring-a [:meta :id])
                                (get-in wiring-b [:meta :id])]}]

     {:meta new-meta
      :diagram {:nodes all-nodes
                :edges all-edges
                :output (:output diag-b)}})))

;;; ============================================================
;;; Parallel Composition with Selection
;;; ============================================================

(defn compose-parallel
  "Compose two wirings in parallel with a selector.

   Both wirings run on the same context. A threshold/selector
   chooses which output to use.

   Options:
   - :selector - :blend (weighted average) or :threshold (binary choice)
   - :threshold-input - what controls selection (default :diversity from neighbors)"
  ([wiring-a wiring-b] (compose-parallel wiring-a wiring-b {}))
  ([wiring-a wiring-b {:keys [selector] :or {selector :threshold}}]
   (let [;; Prefix both diagrams
         diag-a (prefix-ids (:diagram wiring-a) :a)
         diag-b (prefix-ids (:diagram wiring-b) :b)

         ;; Get feed nodes (what feeds into output)
         a-stripped (remove-output-node diag-a)
         b-stripped (remove-output-node diag-b)

         ;; Keep context nodes from A only (they're the same inputs)
         a-context-ids (set (map :id (find-input-nodes diag-a)))
         b-context-ids (set (map :id (find-input-nodes diag-b)))

         ;; Remove B's context nodes, redirect edges to A's
         context-mapping (into {}
                               (for [b-ctx (find-input-nodes diag-b)
                                     :let [comp (:component b-ctx)
                                           a-equiv (keyword (str "a-" (name
                                                                       (:id (first (filter #(= (:component %) comp)
                                                                                           (:nodes (:diagram wiring-a))))))))]]
                                 [(:id b-ctx) a-equiv]))

         b-nodes-no-ctx (filterv #(not (b-context-ids (:id %))) (:nodes b-stripped))
         b-edges-remapped (mapv (fn [e]
                                  (if-let [new-from (get context-mapping (:from e))]
                                    (assoc e :from new-from)
                                    e))
                                (:edges b-stripped))

         ;; Add selector node
         selector-node {:id :selector
                        :component (if (= selector :blend) :blend :threshold-sigil)
                        :params (when (= selector :threshold) {:threshold 0.5})}

         ;; Add output node
         output-node {:id :final-output :component :output-sigil}

         ;; Connect both feeds to selector
         selector-edges (if (= selector :blend)
                          [{:from (:feed-node a-stripped) :to :selector :to-port :sigils}
                           {:from (:feed-node b-stripped) :to :selector :to-port :sigils}]
                          [{:from (:feed-node a-stripped) :to :selector :to-port :above}
                           {:from (:feed-node b-stripped) :to :selector :to-port :below}])

         output-edge {:from :selector :to :final-output}

         all-nodes (vec (concat (:nodes a-stripped)
                                b-nodes-no-ctx
                                [selector-node output-node]))
         all-edges (vec (concat (:edges a-stripped)
                                b-edges-remapped
                                selector-edges
                                [output-edge]))

         new-meta {:id (keyword (str (name (get-in wiring-a [:meta :id] :a))
                                     "||"
                                     (name (get-in wiring-b [:meta :id] :b))))
                   :composition :parallel
                   :selector selector
                   :components [(get-in wiring-a [:meta :id])
                                (get-in wiring-b [:meta :id])]}]

     {:meta new-meta
      :diagram {:nodes all-nodes
                :edges all-edges
                :output :final-output}})))

;;; ============================================================
;;; Boost: Apply CA as transformation
;;; ============================================================

(defn boost
  "Apply a CA rule as a 'boost' to a wiring.

   The CA rule is applied to the wiring's output:
   - :post - CA(output, pred, succ) - transform output with neighbor context
   - :xor  - output XOR CA(pred, succ) - XOR blend
   - :pre  - CA feeds into wiring's self input

   This 'boosts' the wiring in the direction of the CA's dynamics."
  ([wiring ca-rule] (boost wiring ca-rule :post))
  ([wiring ca-rule mode]
   (case mode
     :post (compose-serial wiring ca-rule {:input-component :context-self})
     :xor  (let [;; Special case: XOR the outputs
                 diag-w (prefix-ids (:diagram wiring) :w)
                 diag-ca (prefix-ids (:diagram ca-rule) :ca)
                 w-stripped (remove-output-node diag-w)
                 ca-stripped (remove-output-node diag-ca)

                 ;; Share context nodes
                 ca-context-ids (set (map :id (find-input-nodes diag-ca)))
                 context-mapping (into {}
                                       (for [ca-ctx (find-input-nodes diag-ca)
                                             :let [comp (:component ca-ctx)
                                                   w-equiv (keyword (str "w-" (name
                                                                                (:id (first (filter #(= (:component %) comp)
                                                                                                    (:nodes (:diagram wiring))))))))]]
                                         [(:id ca-ctx) w-equiv]))

                 ca-nodes-no-ctx (filterv #(not (ca-context-ids (:id %))) (:nodes ca-stripped))
                 ca-edges-remapped (mapv (fn [e]
                                           (if-let [new-from (get context-mapping (:from e))]
                                             (assoc e :from new-from)
                                             e))
                                         (:edges ca-stripped))

                 ;; XOR node
                 xor-node {:id :boost-xor :component :bit-xor}
                 output-node {:id :boosted-output :component :output-sigil}

                 xor-edges [{:from (:feed-node w-stripped) :to :boost-xor :to-port :a}
                            {:from (:feed-node ca-stripped) :to :boost-xor :to-port :b}
                            {:from :boost-xor :to :boosted-output}]

                 all-nodes (vec (concat (:nodes w-stripped)
                                        ca-nodes-no-ctx
                                        [xor-node output-node]))
                 all-edges (vec (concat (:edges w-stripped)
                                        ca-edges-remapped
                                        xor-edges))

                 new-meta {:id (keyword (str (name (get-in wiring [:meta :id] :w))
                                             "⊕"
                                             (name (get-in ca-rule [:meta :id] :ca))))
                           :composition :xor-boost
                           :components [(get-in wiring [:meta :id])
                                        (get-in ca-rule [:meta :id])]}]

             {:meta new-meta
              :diagram {:nodes all-nodes
                        :edges all-edges
                        :output :boosted-output}})

     :pre (compose-serial ca-rule wiring {:input-component :context-self}))))

;;; ============================================================
;;; Breeding / Crossover
;;; ============================================================

(defn- subgraph-from
  "Extract subgraph reachable from a node (going forward)."
  [diagram start-id]
  (let [node-map (into {} (map (fn [n] [(:id n) n]) (:nodes diagram)))
        adj (reduce (fn [m e] (update m (:from e) (fnil conj []) e))
                    {}
                    (:edges diagram))]
    (loop [queue [start-id]
           visited #{}
           nodes []
           edges []]
      (if (empty? queue)
        {:nodes nodes :edges edges}
        (let [id (first queue)
              queue' (rest queue)]
          (if (visited id)
            (recur queue' visited nodes edges)
            (let [node (get node-map id)
                  out-edges (get adj id [])
                  next-ids (map :to out-edges)]
              (recur (concat queue' next-ids)
                     (conj visited id)
                     (conj nodes node)
                     (concat edges out-edges)))))))))

(defn crossover
  "Breed two wirings by swapping subgraphs.

   Finds a compatible cut point (node with same component type)
   and swaps the downstream subgraphs.

   Returns [child-a child-b] or nil if no compatible cut found."
  [wiring-a wiring-b]
  (let [diag-a (:diagram wiring-a)
        diag-b (:diagram wiring-b)

        ;; Find nodes with matching component types (excluding context/output)
        nodes-a (filter #(not (or (.startsWith (name (:component %)) "context-")
                                  (.startsWith (name (:component %)) "output")))
                        (:nodes diag-a))
        nodes-b (filter #(not (or (.startsWith (name (:component %)) "context-")
                                  (.startsWith (name (:component %)) "output")))
                        (:nodes diag-b))

        comps-a (set (map :component nodes-a))
        comps-b (set (map :component nodes-b))
        shared-comps (set/intersection comps-a comps-b)]

    (when (seq shared-comps)
      (let [;; Pick first shared component type
            cut-comp (first shared-comps)
            cut-a (first (filter #(= (:component %) cut-comp) nodes-a))
            cut-b (first (filter #(= (:component %) cut-comp) nodes-b))

            ;; Extract subgraphs from cut points
            sub-a (subgraph-from diag-a (:id cut-a))
            sub-b (subgraph-from diag-b (:id cut-b))

            ;; Nodes/edges NOT in subgraph
            sub-a-ids (set (map :id (:nodes sub-a)))
            sub-b-ids (set (map :id (:nodes sub-b)))

            pre-a-nodes (filterv #(not (sub-a-ids (:id %))) (:nodes diag-a))
            pre-a-edges (filterv #(not (sub-a-ids (:to %))) (:edges diag-a))

            pre-b-nodes (filterv #(not (sub-b-ids (:id %))) (:nodes diag-b))
            pre-b-edges (filterv #(not (sub-b-ids (:to %))) (:edges diag-b))

            ;; Prefix subgraphs to avoid collisions
            sub-a-prefixed (prefix-ids {:nodes (:nodes sub-a) :edges (:edges sub-a)} :xa)
            sub-b-prefixed (prefix-ids {:nodes (:nodes sub-b) :edges (:edges sub-b)} :xb)

            ;; Reconnect: pre-a + sub-b, pre-b + sub-a
            ;; Need to redirect edges that went TO cut point
            reconnect (fn [pre-edges old-cut new-cut-id]
                        (mapv (fn [e]
                                (if (= (:to e) old-cut)
                                  (assoc e :to new-cut-id)
                                  e))
                              pre-edges))

            child-a {:meta {:id (keyword (str (name (get-in wiring-a [:meta :id])) "×"
                                              (name (get-in wiring-b [:meta :id])) "-a"))
                            :crossover true
                            :parents [(get-in wiring-a [:meta :id])
                                      (get-in wiring-b [:meta :id])]}
                     :diagram {:nodes (vec (concat pre-a-nodes (:nodes sub-b-prefixed)))
                               :edges (vec (concat (reconnect pre-a-edges (:id cut-a)
                                                              (keyword (str "xb-" (name (:id cut-b)))))
                                                   (:edges sub-b-prefixed)))
                               :output (keyword (str "xb-" (name (find-output-node diag-b))))}}

            child-b {:meta {:id (keyword (str (name (get-in wiring-a [:meta :id])) "×"
                                              (name (get-in wiring-b [:meta :id])) "-b"))
                            :crossover true
                            :parents [(get-in wiring-a [:meta :id])
                                      (get-in wiring-b [:meta :id])]}
                     :diagram {:nodes (vec (concat pre-b-nodes (:nodes sub-a-prefixed)))
                               :edges (vec (concat (reconnect pre-b-edges (:id cut-b)
                                                              (keyword (str "xa-" (name (:id cut-a)))))
                                                   (:edges sub-a-prefixed)))
                               :output (keyword (str "xa-" (name (find-output-node diag-a))))}}]

        [child-a child-b]))))

;;; ============================================================
;;; Loading helpers
;;; ============================================================

(defn load-wiring [path]
  (edn/read-string (slurp path)))

(defn load-ca-rules []
  {:rule-090 (load-wiring "data/wiring-rules/rule-090.edn")
   :rule-110 (load-wiring "data/wiring-rules/rule-110.edn")
   :rule-030 (load-wiring "data/wiring-rules/rule-030.edn")
   :rule-184 (load-wiring "data/wiring-rules/rule-184.edn")
   :rule-054 (load-wiring "data/wiring-rules/rule-054.edn")})
