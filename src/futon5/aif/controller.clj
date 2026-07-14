(ns futon5.aif.controller
  "The :aif controller for the MetaCA tokamak.

   Plugs into the cyber-MMCA controller ABI (run-controller in
   cyber_mmca_compare.clj) alongside :null/:hex/:sigil.

   Each tick:
   1. forward-predict each of the 5 candidate actions (via futon5.aif.forward)
   2. score each by g-efe against the EoC preference C (via futon5.aif.preference)
   3. select by softmax(-g-efe/τ)

   Faithfulness tag: FEP-derived (R5+R6+R14+R19).
   The hex heuristic (choose-actions-hex) is NOT called here — it remains a
   named baseline arm in the comparison harness, never called EFE."
  (:require [futon5.aif.forward :as forward]
            [futon5.aif.preference :as pref]))

(def ^:private default-tau
  "Commitment temperature (R14). v1: fixed; Slice 3 couples τ to regime
   volatility."
  0.5)

(def ^:private candidate-actions
  "The R6 action vocabulary."
  [:pressure-up :pressure-down :selectivity-up :selectivity-down :hold])

(defn- softmax-select
  "Softmax over -g-efe/τ. Returns the selected score map (the action with
   highest softmax probability). For a finite action set with τ>0, this is
   equivalent to argmin(g-efe) when τ→0 and approaches uniform as τ→∞."
  [scores tau]
  (let [tau (double (or tau default-tau))
        gs (map :g-efe scores)
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
     opts      — {:seed :W :S :tau :generations}

   Returns:
     {:actions   vector of selected actions (the action keyword in a vector,
                  matching the controller ABI which expects a seq)
      :g-efe     the selected action's g-efe score (telemetry)
      :regime    the predicted regime (telemetry)
      :scores    all action scores (telemetry)}"
  ([state _window]
   (choose-actions-aif state nil {}))
  ([state _window {:keys [seed W S tau generations]}]
   (let [predict-opts {:generations (or generations 10)
                       :seed (or seed 42)
                       :W (or W 10)
                       :S (or S W)}
         ;; Forward-predict + score each candidate action.
         scores (for [action candidate-actions]
                  (let [fp (forward/forward-predict state action predict-opts)
                        scored (pref/score-action fp action)]
                    (assoc scored :fp-result fp)))
         ;; Select by softmax(-g-efe/τ).
         selected (softmax-select scores tau)]
     {:actions [(:action selected)]
      :g-efe (:g-efe selected)
      :regime (:regime selected)
      :scores scores})))
