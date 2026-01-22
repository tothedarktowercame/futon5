(ns futon5.xenotype.interpret
  (:require [futon5.mmca.filament :as filament]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.register-shift :as register-shift]
            [futon5.xenotype.sigil-features :as sigil-features]
            [futon5.xenotype.wiring :as wiring]))

(defn- clamp-01 [x]
  (cond
    (not (number? x)) 0.0
    (< x 0.0) 0.0
    (> x 1.0) 1.0
    :else (double x)))

(defn- band-score [x center width]
  (when (and (number? x) (number? center) (pos? (double width)))
    (clamp-01 (- 1.0 (/ (Math/abs (- (double x) (double center))) (double width))))))

(defn- envelope-score
  [summary {:keys [entropy-center entropy-width change-center change-width include-change?]
            :or {entropy-center 0.6 entropy-width 0.25 change-center 0.2 change-width 0.15}}]
  (let [ent (band-score (:avg-entropy-n summary) entropy-center entropy-width)
        chg (band-score (:avg-change summary) change-center change-width)
        scores (remove nil? (cond-> [ent] include-change? (conj chg)))]
    (if (seq scores)
      (* 100.0 (/ (reduce + 0.0 scores) (double (count scores))))
      0.0)))

(defn- triad-score
  [summary hex-score]
  (let [gen-score (double (or (:gen/composite-score summary) (:composite-score summary) 0.0))
        phe-score (double (or (:phe/composite-score summary) (:composite-score summary) 0.0))
        exo-score (double (or hex-score 0.0))]
    (/ (+ gen-score phe-score exo-score) 3.0)))

(defn- frames->score
  [frames]
  (double (or (:score (filament/analyze-run frames {})) 0.0)))

(def default-registry
  {:short (fn [{:keys [summary]} _ _]
            {:score (double (or (:composite-score summary) 0.0))})
   :envelope (fn [{:keys [summary]} params _]
               {:score (envelope-score summary (assoc params :include-change? true))})
   :triad (fn [{:keys [summary]} _ {:keys [hex-score]}]
            {:score (triad-score summary hex-score)})
   :shift (fn [_ _ {:keys [run]}]
            {:score (double (or (:shift/composite (register-shift/register-shift-summary run)) 0.0))})
   :filament (fn [{:keys [frames]} _ _]
               {:score (frames->score frames)})
   :entropy (fn [{:keys [summary]} _ _]
              {:score (double (or (:avg-entropy-n summary) 0.0))})
   :sigil-features (fn [_ _ {:keys [run]}]
                     {:features (sigil-features/feature-vector run)})
   :weighted-sum (fn [{:keys [scores weights]} _ _]
                   (let [scores (map double (or scores []))
                         weights (map double (or weights []))
                         total (reduce + 0.0 (map * scores weights))]
                     {:score total}))
   ;; Expects precomputed ranks (lower is better); averages them.
   :rank-fusion (fn [{:keys [scores]} _ _]
                  (let [scores (map double (or scores []))
                        rank (if (seq scores)
                               (/ (reduce + 0.0 scores) (double (count scores)))
                               0.0)]
                    {:rank rank}))
   :disagreement (fn [{:keys [scores]} _ _]
                   (let [scores (map double (or scores []))
                         mean (if (seq scores) (/ (reduce + 0.0 scores) (double (count scores))) 0.0)
                         variance (if (seq scores)
                                    (/ (reduce + 0.0 (map (fn [x] (let [d (- x mean)] (* d d))) scores))
                                       (double (count scores)))
                                    0.0)]
                     {:sigma (Math/sqrt variance)}))
   :normalize (fn [{:keys [score]} _ _]
                {:score (clamp-01 score)})
   :threshold-gate (fn [{:keys [score threshold]} _ _]
                     {:pass? (boolean (and (number? score) (number? threshold) (>= score threshold)))})
   :temporal-mean (fn [{:keys [series]} _ _]
                    (let [series (map double (or series []))
                          score (if (seq series)
                                  (/ (reduce + 0.0 series) (double (count series)))
                                  0.0)]
                      {:score score}))
   :if-then-else (fn [{:keys [cond then else]} _ _]
                   {:score (double (if cond then else))})
   :parallel-vote (fn [{:keys [votes]} _ _]
                    (let [votes (map boolean (or votes []))
                          pass? (if (seq votes)
                                  (>= (count (filter true? votes))
                                      (Math/ceil (/ (count votes) 2.0)))
                                  false)]
                      {:pass? pass?}))
   :sequential-filter (fn [{:keys [scores min-score]} _ _]
                        (let [scores (map double (or scores []))
                              min-score (double (or min-score 0.0))]
                          {:scores (vec (filter #(>= % min-score) scores))}))})

(defn- series-values
  [series k]
  (if (keyword? k)
    (->> (or series [])
         (map k)
         (filter number?)
         (map double)
         vec)
    []))

(defn- series-flags
  [series pred]
  (->> (or series [])
       (map pred)
       (map boolean)
       vec))

(def eval-registry
  {:series-field (fn [{:keys [series]} {:keys [key]} _]
                   {:values (series-values series key)})
   :series-regime-flag (fn [{:keys [series]} {:keys [regime]} _]
                         {:flags (series-flags series #(= (:regime %) regime))})
   :series-min (fn [{:keys [values]} _ _]
                 (let [vals (->> (or values []) (filter number?) (map double) vec)]
                   {:score (when (seq vals) (reduce min vals))}))
   :series-max (fn [{:keys [values]} _ _]
                 (let [vals (->> (or values []) (filter number?) (map double) vec)]
                   {:score (when (seq vals) (reduce max vals))}))
   :series-ratio (fn [{:keys [flags]} _ _]
                   (let [flags (map boolean (or flags []))
                         total (count flags)
                         hits (count (filter true? flags))]
                     {:score (if (pos? total) (/ hits (double total)) 0.0)}))})

(defn- component-inputs
  [lib component-id]
  (into {} (map (fn [[name type]] [name type])
                (:inputs (wiring/component-def lib component-id)))))

(defn- component-outputs
  [lib component-id]
  (into {} (map (fn [[name type]] [name type])
                (:outputs (wiring/component-def lib component-id)))))

(defn- default-port
  [ports]
  (first (keys ports)))

(defn- list-type?
  [t]
  (contains? #{:scalar-list :bool-list} t))

(defn- topo-order
  [nodes edges]
  (let [ids (map :id nodes)
        incoming (reduce (fn [m id] (assoc m id 0)) {} ids)
        adj (reduce (fn [m id] (assoc m id [])) {} ids)
        edge-pairs (for [edge edges :when (:from edge)]
                     [(:from edge) (:to edge)])
        incoming (reduce (fn [m [_ to]] (update m to (fnil inc 0))) incoming edge-pairs)
        adj (reduce (fn [m [from to]] (update m from (fnil conj []) to)) adj edge-pairs)]
    (loop [queue (into clojure.lang.PersistentQueue/EMPTY
                       (filter #(zero? (get incoming % 0)) ids))
           incoming incoming
           order []]
      (if (empty? queue)
        (if (= (count order) (count ids)) order ids)
        (let [id (peek queue)
              queue (pop queue)
              [incoming queue] (reduce (fn [[inc q] to]
                                         (let [n (dec (get inc to 0))
                                               inc (assoc inc to n)
                                               q (if (zero? n) (conj q to) q)]
                                           [inc q]))
                                       [incoming queue]
                                       (get adj id []))]
          (recur queue incoming (conj order id)))))))

(defn- collect-inputs
  [lib node edges node-map node-values]
  (let [inputs (component-inputs lib (:component node))
        edges-for (group-by :to edges)
        relevant (get edges-for (:id node))
        result (atom {})]
    (doseq [[port port-type] inputs]
      (let [port-edges (filter #(= (or (:to-port %) (default-port inputs)) port) relevant)
            values (map (fn [edge]
                          (if (contains? edge :value)
                            (:value edge)
                            (let [from-id (:from edge)
                                  from-node (get node-map from-id)
                                  from-port (or (:from-port edge)
                                                (default-port (component-outputs lib (:component from-node))))]
                              (get-in node-values [from-id from-port]))))
                        port-edges)]
        (if (list-type? port-type)
          (let [values (mapcat (fn [v] (if (sequential? v) v [v])) values)]
            (swap! result assoc port (vec values)))
          (swap! result assoc port (first values)))))
    @result))

(defn evaluate-diagram
  "Evaluate a wiring diagram and return {:output .. :node-values ..}."
  ([diagram context] (evaluate-diagram diagram context {}))
  ([diagram {:keys [summary run frames hex-score series components-path] :as context} registry]
   (let [lib (wiring/load-components (or components-path "futon5/resources/xenotype-generator-components.edn"))
         registry (merge default-registry eval-registry registry)
         nodes (:nodes diagram)
         edges (:edges diagram)
         order (topo-order nodes edges)
       node-map (into {} (map (fn [n] [(:id n) n]) nodes))]
    (loop [ids order
           node-values {}]
      (if (empty? ids)
        {:output (get node-values (:output diagram))
         :node-values node-values}
        (let [id (first ids)
              node (get node-map id)
              component-def (wiring/component-def lib (:component node))
              _ (when-not component-def
                  (throw (ex-info "Unknown component in wiring diagram"
                                  {:node-id id
                                   :component (:component node)})))
              inputs (collect-inputs lib node edges node-map node-values)
              component-fn (get registry (:component node))
              params (:params node)
               result (if component-fn
                        (component-fn (assoc inputs
                                             :summary summary
                                             :run run
                                             :frames frames
                                             :series series)
                                      params
                                      {:hex-score hex-score
                                       :summary summary
                                       :run run
                                       :frames frames
                                       :series series})
                        {})]
           (recur (rest ids) (assoc node-values id result))))))))

(defn evaluate-run-diagram
  "Evaluate a wiring diagram against a run, using windowed macro feature series."
  ([diagram run] (evaluate-run-diagram diagram run {}))
  ([diagram run {:keys [W S components-path registry] :or {W 10 S 10}}]
   (let [series (metrics/windowed-macro-features run {:W W :S S})]
     (evaluate-diagram diagram {:run run
                                :series series
                                :components-path components-path}
                       (or registry {})))))
