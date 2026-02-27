(ns futon5.ct.tensor-stepper
  "Single-run tensor stepper with checkpoint/rewind support."
  (:require [futon5.ct.tensor-diagrams :as diagrams]
            [futon5.ct.tensor-exec :as exec]
            [futon5.ct.tensor-mmca :as tensor-mmca]
            [futon5.mmca.metrics :as metrics]))

(def ^:private default-rule-sigil "手")
(def ^:private default-step-opts {:backend :clj :wrap? false :boundary-bit 0})

(defn- output-key-for
  [diagram-id phenotype]
  (if (= diagram-id :sigil-step-gated)
    :gated-row
    (if phenotype :new-row :new-row)))

(defn- normalize-physics
  [{:keys [rule-sigil diagram-id step-opts]} phenotype]
  (let [diagram-id (or diagram-id
                       (if phenotype :sigil-step-gated :sigil-step))]
    {:rule-sigil (or rule-sigil default-rule-sigil)
     :diagram-id diagram-id
     :step-opts (merge default-step-opts (or step-opts {}))}))

(defn- init-metrics-history
  [history]
  (tensor-mmca/history->metrics-history history))

(defn init-stepper
  [{:keys [seed genotype phenotype physics] :or {seed 0}}]
  (when-not (seq genotype)
    (throw (ex-info "init-stepper requires :genotype" {})))
  (when (and phenotype (not= (count genotype) (count phenotype)))
    (throw (ex-info "init-stepper phenotype width must match genotype width"
                    {:genotype-width (count genotype)
                     :phenotype-width (count phenotype)})))
  (let [physics (normalize-physics physics phenotype)
        history [genotype]
        phe-history (when phenotype [phenotype])]
    {:seed (long seed)
     :generation 0
     :physics physics
     :gen-history history
     :phe-history phe-history
     :metrics-history (init-metrics-history history)
     :checkpoints {0 {:generation 0
                      :physics physics
                      :gen-history history
                      :phe-history phe-history
                      :metrics-history (init-metrics-history history)}}
     :events []}))

(defn checkpoint
  "Store a snapshot keyed by checkpoint-id."
  [state checkpoint-id]
  (assoc-in state
            [:checkpoints checkpoint-id]
            {:generation (:generation state)
             :physics (:physics state)
             :gen-history (:gen-history state)
             :phe-history (:phe-history state)
             :metrics-history (:metrics-history state)}))

(defn rewind
  "Rewind to checkpoint-id."
  [state checkpoint-id]
  (if-let [snap (get-in state [:checkpoints checkpoint-id])]
    (merge state snap)
    (throw (ex-info "Unknown checkpoint id" {:checkpoint-id checkpoint-id
                                             :known (keys (:checkpoints state))}))))

(defn set-physics
  [state physics]
  (assoc state :physics (normalize-physics physics (some-> state :phe-history seq boolean))))

(defn set-current-genotype
  "Replace the current (latest) genotype row."
  [state row]
  (let [hist (vec (or (:gen-history state) []))
        row (str (or row ""))]
    (when-not (seq hist)
      (throw (ex-info "No genotype history to update" {})))
    (when-not (= (count row) (count (last hist)))
      (throw (ex-info "Genotype width must remain constant"
                      {:expected (count (last hist))
                       :actual (count row)})))
    (let [hist' (assoc hist (dec (count hist)) row)]
      (assoc state
             :gen-history hist'
             :metrics-history (tensor-mmca/history->metrics-history hist')))))

(defn set-current-phenotype
  "Replace the current (latest) phenotype row."
  [state phenotype]
  (let [phe (vec (or (:phe-history state) []))
        phenotype (str (or phenotype ""))]
    (when-not (seq phe)
      (throw (ex-info "No phenotype history to update" {})))
    (when-not (= (count phenotype) (count (last phe)))
      (throw (ex-info "Phenotype width must remain constant"
                      {:expected (count (last phe))
                       :actual (count phenotype)})))
    (assoc state :phe-history (assoc phe (dec (count phe)) phenotype))))

(defn available-diagram-ids
  []
  (diagrams/available-diagrams))

(defn step-once
  "Run one generation with current physics.

   Returns {:state updated-state :event event-map}."
  [state]
  (let [{:keys [generation physics gen-history phe-history]} state
        current (or (last gen-history) "")
        phenotype (when (seq phe-history) (last phe-history))
        {:keys [rule-sigil diagram-id step-opts]} physics
        diagram (diagrams/diagram diagram-id)
        output-key (output-key-for diagram-id phenotype)
        input-state (merge {:sigil-row current
                            :rule-sigil rule-sigil
                            :step-opts step-opts}
                           (when phenotype {:phenotype phenotype}))
        out (exec/run-diagram diagram input-state)
        next-row (or (get out output-key)
                     (throw (ex-info "Step output missing expected key"
                                     {:output-key output-key
                                      :output-keys (keys out)
                                      :diagram-id diagram-id})))
        _ (when-not (= (count next-row) (count current))
            (throw (ex-info "Step width changed unexpectedly"
                            {:before (count current)
                             :after (count next-row)
                             :diagram-id diagram-id})))
        gen-history' (conj (vec gen-history) next-row)
        phe-history' (if phenotype
                       (conj (vec phe-history) phenotype)
                       nil)
        metrics-history' (tensor-mmca/history->metrics-history gen-history')
        run-result {:gen-history gen-history'
                    :phe-history phe-history'
                    :metrics-history metrics-history'}
        summary (metrics/summarize-run run-result)
        episode (metrics/episode-summary run-result)
        event {:generation (inc generation)
               :rule-sigil rule-sigil
               :diagram-id diagram-id
               :step-opts step-opts
               :row-before current
               :row-after next-row
               :summary summary
               :episode episode}
        state' (-> state
                   (assoc :generation (inc generation)
                          :gen-history gen-history'
                          :phe-history phe-history'
                          :metrics-history metrics-history')
                   (update :events conj event))]
    {:state state' :event event}))

(defn history-summary
  [state]
  (let [result {:gen-history (:gen-history state)
                :phe-history (:phe-history state)
                :metrics-history (:metrics-history state)}
        summary (metrics/summarize-run result)
        episode (metrics/episode-summary result)]
    {:summary summary
     :episode episode
     :generation (:generation state)
     :rule-sigil (get-in state [:physics :rule-sigil])
     :diagram-id (get-in state [:physics :diagram-id])}))
