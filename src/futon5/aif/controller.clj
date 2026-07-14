(ns futon5.aif.controller
  "The :aif controller for the MetaCA tokamak.

   Plugs into the cyber-MMCA controller ABI (run-controller in
   cyber_mmca_compare.clj) alongside :null/:hex/:sigil.

   Each tick:
   1. forward-predict each of the 5 candidate actions (via futon5.aif.forward)
   2. score each by controller-score = g-efe (pure) + regime-penalty augmentation
   3. select by softmax(-controller-score/τ) where τ is coupled to regime volatility (R14)

   Controller split (Slice 3):
   - g-efe: the unit-pure 2-term core from futon2.aif.core-efe (shared, not forked)
   - regime-penalty: named augmentation with typed residual, NOT inside g-efe
   - precision: per-channel adaptive precision from futon2.aif.precision (shared, not forked)
   - τ: coupled to regime volatility (high volatility → higher τ → more exploration)

   Faithfulness tag: FEP-derived (R5+R6+R7+R14+R19).
   The hex heuristic (choose-actions-hex) is NOT called here — it remains a
   named baseline arm in the comparison harness, never called EFE."
  (:require [futon5.aif.forward :as forward]
            [futon5.aif.preference :as pref]
            [futon2.aif.precision :as precision]))

(def ^:private default-tau
  "Base commitment temperature (R14). Coupled to regime volatility at runtime."
  0.5)

(def ^:private tau-min 0.1)
(def ^:private tau-max 2.0)

(def ^:private candidate-actions
  "The R6 action vocabulary."
  [:pressure-up :pressure-down :selectivity-up :selectivity-down :hold])

(def ^:private macro-feature-channels
  "The macro-feature channels tracked for precision (R7)."
  [:pressure :selectivity :structure :activity])

(defn initial-precision-state
  "Initialize the per-channel precision state for the tokamak's macro-features.
   Consumes the SHARED futon2.aif.precision kernel (not forked)."
  []
  (precision/initial-precision-state macro-feature-channels))

(defn- update-precision
  "Update the precision state from the current observation vs predicted means.
   Consumes the SHARED futon2.aif.precision/update-precision-state."
  [precision-state predicted-means observed]
  (let [errors (into {}
                     (for [ch macro-feature-channels]
                       (let [pred (double (or (get predicted-means ch) 0.5))
                             obs (double (or (get observed ch) 0.5))]
                         [ch {:error (- obs pred)
                              :observed obs}])))]
    (precision/update-precision-state precision-state errors)))

(defn- precision-weights
  "Extract per-channel precision as a weight vector for g-efe's risk term.
   Higher precision → higher weight (attend more to channels the controller
   is more certain about)."
  [precision-state]
  (mapv #(precision/precision-for precision-state %) macro-feature-channels))

(defn- regime-volatility
  "Compute regime volatility from the recent regime history.
   High volatility (frequent regime transitions) → higher τ (more exploration).
   Returns a multiplier in [0.5, 2.0]."
  [regime-history]
  (if (or (nil? regime-history) (< (count regime-history) 2))
    1.0
    (let [transitions (count (remove #(apply = %) (partition 2 1 regime-history)))
          n (dec (count regime-history))
          volatility (double (/ transitions n))]
      ;; Map volatility [0,1] to multiplier [0.5, 2.0]
      (+ 0.5 (* volatility 1.5)))))

(defn- compute-tau
  "Couple τ to regime volatility (R14). High volatility → higher τ → more
   exploration (the controller is less committed when the regime is unstable).
   Returns τ in [tau-min, tau-max]."
  [regime-history]
  (let [mult (regime-volatility regime-history)
        tau (* default-tau mult)]
    (max tau-min (min tau-max tau))))

(defn- softmax-select
  "Softmax over -controller-score/τ. Returns the selected score map (the
   action with highest softmax probability)."
  [scores tau]
  (let [tau (double (or tau default-tau))
        gs (map :controller-score scores)
        min-g (apply min gs)
        logits (map #(/ (- (double %) min-g) tau) gs)
        exps (map #(Math/exp (- (double %))) logits)
        sum-exp (reduce + 0.0 exps)
        probs (map #(/ (double %) sum-exp) exps)
        best-idx (first (apply max-key second (map-indexed vector probs)))]
    (nth scores best-idx)))

(defn choose-actions-aif
  "The :aif controller's action-selection function.

   Args:
     state     — the controller's macro-state (genotype/exotype/history)
     window    — the current macro-feature observation (for telemetry)
     opts      — {:seed :W :S :tau :generations :precision-state :regime-history}

   Returns:
     {:actions   vector of selected actions
      :g-efe     the selected action's pure g-efe (telemetry)
      :controller-score  the selected action's full controller-score
      :regime    the predicted regime (telemetry)
      :tau       the commitment temperature used (telemetry)
      :precision-state  the updated precision state (for next tick)
      :scores    all action scores (telemetry)}"
  ([state _window]
   (choose-actions-aif state nil {}))
  ([state _window {:keys [seed W S tau generations precision-state regime-history]}]
   (let [predict-opts {:generations (or generations 10)
                       :seed (or seed 42)
                       :W (or W 10)
                       :S (or S W)}
         p-state (or precision-state (initial-precision-state))
         ;; Get precision weights for g-efe scoring.
         weights (precision-weights p-state)
         ;; Compute τ from regime volatility (R14).
         effective-tau (or tau (compute-tau regime-history))
         ;; Forward-predict + score each candidate action.
         scores (for [action candidate-actions]
                  (let [fp (forward/forward-predict state action predict-opts)
                        scored (pref/controller-score fp action {:weights weights})]
                    (assoc scored :fp-result fp)))
         ;; Select by softmax(-controller-score/τ).
         selected (softmax-select scores effective-tau)
         ;; Update precision state from the selected action's prediction.
         fp-selected (:fp-result selected)
         predicted-mean (:mean fp-selected)
         updated-precision (update-precision p-state
                                             (into {} (for [ch macro-feature-channels]
                                                        [ch (get predicted-mean ch 0.5)]))
                                             predicted-mean)]
     {:actions [(:action selected)]
      :g-efe (:g-efe selected)
      :controller-score (:controller-score selected)
      :regime (:regime selected)
      :tau effective-tau
      :precision-state updated-precision
      :scores scores})))
