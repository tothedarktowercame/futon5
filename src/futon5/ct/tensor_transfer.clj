(ns futon5.ct.tensor-transfer
  "Transfer primitive pack for tensor runs (meta-lift -> cyber-ant + AIF)."
  (:require [futon5.ct.tensor-diagrams :as diagrams]
            [futon5.ct.tensor-exec :as exec]
            [futon5.cyber-ants :as cyber]
            [futon5.mmca.meta-lift :as meta-lift]))

(def ^:private default-top-k 8)

(defn- clamp-01 [x]
  (max 0.0 (min 1.0 (double (or x 0.0)))))

(defn- normalize-sigils [xs]
  (->> xs
       (map str)
       (remove empty?)
       distinct
       vec))

(defn top-sigils-from-history
  "Extract top-K lifted sigils from genotype history."
  ([gen-history] (top-sigils-from-history gen-history default-top-k))
  ([gen-history top-k]
   (let [lift (meta-lift/lift-history (or gen-history []))
         counts (or (:sigil-counts lift) {})
         top-k (long (or top-k default-top-k))
         top-sigils (->> counts
                         (sort-by val >)
                         (map key)
                         (map str)
                         (take top-k)
                         vec)]
     {:top-sigils (normalize-sigils top-sigils)
      :sigil-counts (into {} (map (fn [[k v]] [(str k) v]) counts))})))

(defn summary->aif-scorecard
  "Derive lightweight AIF bridge metrics from MMCA summary fields."
  [summary]
  (let [quality (clamp-01 (/ (double (or (:composite-score summary) 0.0)) 100.0))
        diversity (clamp-01 (:avg-unique summary))
        temporal (clamp-01 (:temporal-autocorr summary))
        change (clamp-01 (:avg-change summary))
        trail (clamp-01 (* temporal (- 1.0 (Math/abs (- change 0.2)))))
        food-mass (* quality diversity)
        food-count (long (Math/round (* 16.0 food-mass)))
        biodiversity-score diversity
        score (* 100.0 (+ (* 0.5 quality)
                          (* 0.3 biodiversity-score)
                          (* 0.2 trail)))]
    {:aif/food-mass food-mass
     :aif/food-count food-count
     :aif/food-quality quality
     :aif/trail-score trail
     :aif/biodiversity diversity
     :aif/biodiversity-score biodiversity-score
     :aif/score score}))

(defn register-transfer-primitives!
  "Register primitives required by `tensor-transfer-pack` diagram."
  []
  (exec/register-primitive! :pass-seed
                            (fn [[seed] _] [seed]))
  (exec/register-primitive! :pass-run-index
                            (fn [[run-index] _] [run-index]))
  (exec/register-primitive! :pass-summary
                            (fn [[summary] _] [summary]))
  (exec/register-primitive! :gen-history->top-sigils
                            (fn [[gen-history] state]
                              (let [k (or (:top-k state) default-top-k)]
                                (top-sigils-from-history gen-history k))))
  (exec/register-primitive! :top-sigils->cyber-ant
                            (fn [[top-sigils seed run-index] state]
                              (let [base-id (or (:base-id state) cyber/default-pattern-id)]
                                [(cyber/propose-cyber-ant top-sigils
                                                          {:base-id base-id
                                                           :seed seed
                                                           :run-index run-index
                                                           :policy (:policy state)
                                                           :rule (:rule state)})])))
  (exec/register-primitive! :summary->aif-score
                            (fn [[summary] _]
                              [(summary->aif-scorecard summary)])))

(defonce ^:private transfer-installed?
  (delay (register-transfer-primitives!)))

(defn- ensure-transfer! []
  @transfer-installed?)

(def transfer-pack-diagram diagrams/tensor-transfer-pack-diagram)

(defn run-transfer-pack
  "Run transfer pack diagram to produce top sigils + cyber-ant + AIF map.

   input keys:
   - :gen-history (required)
   - :summary (required)
   - :seed, :run-index, :top-k, :base-id, :policy, :rule (optional)"
  [{:keys [gen-history summary] :as state}]
  (ensure-transfer!)
  (when-not (seq gen-history)
    (throw (ex-info "run-transfer-pack requires :gen-history" {})))
  (when-not (map? summary)
    (throw (ex-info "run-transfer-pack requires :summary map" {})))
  (exec/run-diagram transfer-pack-diagram
                    (merge {:seed 0
                            :run-index 0
                            :top-k default-top-k}
                           state)))
