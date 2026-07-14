(ns futon5.aif.controller
  "The :aif controller for the MetaCA tokamak.

   Plugs into the cyber-MMCA controller ABI (run-controller in
   cyber_mmca_compare.clj) alongside :null/:hex/:sigil.

   Each tick (Slice 4):
   1. R13 rollout: score each candidate action by H-step rollout S(π) = Σ ρ^t·g(s_t)
      over the run-mmca forward model (via futon5.aif.rollout)
   2. Select the first action of the best rollout policy by softmax(-rollout-score/τ)
   3. R8: surface F = ½·mean(Π·ε²) in the trace

   Controller split (Slice 3, ablated):
   - g-efe: the unit-pure 2-term core from futon2.aif.core-efe (shared, not forked)
   - augmentations: [] (regime-penalty DELETED as inert in S3b ablation)
   - precision: per-channel adaptive precision from futon2.aif.precision (shared)
   - τ: coupled to regime volatility (R14)
   - rollout: H>1 discounted policy search (R13)

   Faithfulness tag: FEP-derived (R5+R6+R7+R8+R13+R14+R19).
   The hex heuristic (choose-actions-hex) is NOT called here — it remains a
   named baseline arm in the comparison harness, never called EFE."
  (:require [futon5.aif.forward :as forward]
            [futon5.aif.rollout :as rollout]
            [futon2.aif.precision :as precision]))

(def ^:private default-tau
  "Base commitment temperature (R14). Coupled to regime volatility at runtime."
  0.5)

(def ^:private tau-min 0.1)
(def ^:private tau-max 2.0)

(def ^:private default-horizon
  "Default rollout horizon H > 1 (R13)."
  3)

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
  "Extract per-channel precision as a weight vector for g-efe's risk term."
  [precision-state]
  (mapv #(precision/precision-for precision-state %) macro-feature-channels))

(defn- regime-volatility
  "Compute regime volatility from the recent regime history.
   High volatility → higher τ → more exploration."
  [regime-history]
  (if (or (nil? regime-history) (< (count regime-history) 2))
    1.0
    (let [transitions (count (remove #(apply = %) (partition 2 1 regime-history)))
          n (dec (count regime-history))
          volatility (double (/ transitions n))]
      (+ 0.5 (* volatility 1.5)))))

(defn- compute-tau
  "Couple τ to regime volatility (R14). Returns τ in [tau-min, tau-max]."
  [regime-history]
  (let [mult (regime-volatility regime-history)
        tau (* default-tau mult)]
    (max tau-min (min tau-max tau))))

(defn- compute-F
  "R8: compute per-tick variational free energy F = ½·mean(Π·ε²).
   F is the mean weighted prediction error across macro-feature channels."
  [precision-state predicted-means observed]
  (let [errors (for [ch macro-feature-channels]
                 (let [pred (double (or (get predicted-means ch) 0.5))
                       obs (double (or (get observed ch) 0.5))
                       pi (precision/precision-for precision-state ch)
                       eps (- obs pred)]
                   (* pi eps eps)))
        n (count errors)]
    (if (pos? n)
      (* 0.5 (/ (reduce + 0.0 errors) n))
      0.0)))

(defn- softmax-select
  "Softmax over -score/τ. Returns the selected score map."
  [scores tau score-key]
  (let [tau (double (or tau default-tau))
        gs (map score-key scores)
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
     opts      — {:seed :W :S :tau :generations :precision-state :regime-history
                  :horizon :discount}

   Returns:
     {:actions   vector of selected actions
      :g-efe     the selected action's pure g-efe (telemetry)
      :controller-score  the selected action's rollout score
      :regime    the predicted regime (telemetry)
      :tau       the commitment temperature used (telemetry)
      :F         per-tick variational free energy (R8)
      :precision-state  the updated precision state (for next tick)
      :scores    all action rollout scores (telemetry)
      :horizon   the rollout horizon used}"
  ([state _window]
   (choose-actions-aif state nil {}))
  ([state _window {:keys [seed W S tau generations precision-state regime-history
                          horizon discount]}]
   (let [predict-opts {:generations (or generations 10)
                       :seed (or seed 42)
                       :W (or W 10)
                       :S (or S W)}
         p-state (or precision-state (initial-precision-state))
         weights (precision-weights p-state)
         effective-tau (or tau (compute-tau regime-history))
         h (or horizon default-horizon)
         ;; R13 rollout: score each action by H-step rollout
         rollout-scores (rollout/rollout-score state weights
                                                (assoc predict-opts
                                                       :horizon h
                                                       :discount discount))
         ;; Select by softmax(-rollout-score/τ)
         selected (softmax-select rollout-scores effective-tau :rollout-score)
         selected-action (:action selected)
         ;; Forward-predict the selected action to get the predicted state + F
         fp (forward/forward-predict state selected-action predict-opts)
         predicted-mean (:mean fp)
         ;; R8: compute F from the selected action's prediction vs current observation
         F (compute-F p-state predicted-mean (or (:window state) predicted-mean))
         ;; Update precision state
         updated-precision (update-precision p-state
                                             (into {} (for [ch macro-feature-channels]
                                                        [ch (get predicted-mean ch 0.5)]))
                                             predicted-mean)]
     {:actions [selected-action]
      :g-efe (:greedy-score selected)
      :controller-score (:rollout-score selected)
      :regime (:regime predicted-mean)
      :tau effective-tau
      :F F
      :precision-state updated-precision
      :scores rollout-scores
      :horizon h})))
