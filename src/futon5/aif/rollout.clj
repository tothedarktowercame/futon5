(ns futon5.aif.rollout
  "R13 policy rollout for the MetaCA tokamak.

   Scores control-move policies S(π) = Σ ρ^t · g(s_t) to horizon H > 1 over
   the run-mmca forward model (futon5.aif.forward/forward-predict), with
   temporal discount ρ.

   The rollout is a tree search: for each candidate first action, we chain
   forward-predict calls to depth H, scoring each step by g-efe. The policy
   score is the discounted sum. The first action of the best-scoring policy
   is selected.

   Design note: the domain-agnostic discount-sum skeleton (S(π) = Σ ρ^t · g_t)
   could be lifted to futon2.aif.core if the ant port builds the same shape.
   For now it lives here, consuming the shared g-efe kernel and the local
   forward model. The discount-sum math is trivial (reduce + over discounted
   step-scores) and not worth forking.

   Faithfulness tag: FEP-derived (R13 — policy horizon S(π), H>1 rollout)."
  (:require [futon5.aif.forward :as forward]
            [futon5.aif.preference :as pref]))

(def ^:private default-horizon
  "Default rollout horizon H > 1 (R13)."
  3)

(def ^:private default-discount
  "Default temporal discount ρ."
  0.9)

(def ^:private candidate-actions
  "The R6 action vocabulary (mirrors controller).  Every action here is
   PROVEN to move the plant — see the measured-actuator note in
   `futon5.aif.forward`.  The previous vocabulary (:pressure-up/-down,
   :selectivity-up/-down) actuated :update-prob and :match-threshold, both of
   which are inert: paired effect exactly 0.000 on every seed.  An action that
   cannot move the plant is not a candidate, it is a no-op wearing a name."
  [:rotate-up :rotate-down :hold])

(defn- score-step
  "Score a single forward-predict step by g-efe against C.
   Returns the g-efe scalar (lower = better).
   `target-c` (optional) overrides the default EoC set-point — this is what
   lets the controller be re-targeted to an arbitrary C."
  [fp-result weights target-c]
  (:g-efe (pref/g-efe-pure fp-result {:weights weights :target-c target-c})))

(defn- rollout-branch
  "Roll out a single branch (fixed action sequence) to depth H.
   Returns {:actions [a1 a2 ...] :score S(π) :states [state1 state2 ...]}.
   After the first action, subsequent actions are greedy 1-step (pick the
   action that minimizes g-efe at each step). This is a common rollout
   heuristic that keeps the branching factor manageable."
  [state first-action weights opts depth discount target-c]
  (loop [d 0
         current-state state
         actions [first-action]
         total-score 0.0
         states []]
    (if (>= d depth)
      {:actions actions
       :score total-score
       :states states}
      (let [action (if (zero? d)
                     first-action
                     ;; Greedy continuation: pick the best action at this step.
                     (let [step-scores (for [a candidate-actions]
                                         (let [fp (forward/forward-predict current-state a opts)
                                               s (score-step fp weights target-c)]
                                           [a s fp]))
                           best (apply min-key second step-scores)]
                       (first best)))
            fp (forward/forward-predict current-state action opts)
            step-score (score-step fp weights target-c)
            discounted (* (Math/pow discount d) step-score)
            next-state (:next-state fp)]
        (recur (inc d)
               next-state
               (conj actions action)
               (+ total-score discounted)
               (conj states next-state))))))

(defn rollout-score
  "Score all candidate first-actions by H-step rollout.
   Returns a list of {:action :rollout-score :greedy-score} maps,
   sorted by rollout-score (ascending = best first).

   Args:
     state     — the controller's macro-state
     weights   — precision weights for g-efe
     opts      — {:horizon H :discount ρ :seed :W :S :generations :target-c}
                 :target-c overrides the default EoC set-point (re-targeting)."
  ([state weights]
   (rollout-score state weights {}))
  ([state weights {:keys [horizon discount seed W S generations target-c]}]
   (let [h (or horizon default-horizon)
         rho (or discount default-discount)
         predict-opts {:generations (or generations 10)
                       :seed (or seed 42)
                       :W (or W 10)
                       :S (or S W)}
         results (for [action candidate-actions]
                   (let [branch (rollout-branch state action weights predict-opts h rho target-c)
                         ;; Also compute the greedy 1-step score for comparison.
                         fp (forward/forward-predict state action predict-opts)
                         greedy (score-step fp weights target-c)]
                     {:action action
                      :rollout-score (:score branch)
                      :greedy-score greedy
                      :branch branch}))]
     (sort-by :rollout-score results))))

(defn best-rollout-action
  "Returns the first action of the best H-step rollout policy.
   This REPLACES greedy 1-step selection when H > 1."
  ([state weights]
   (best-rollout-action state weights {}))
  ([state weights opts]
   (let [scores (rollout-score state weights opts)
         best (first scores)]
     {:action (:action best)
      :rollout-score (:rollout-score best)
      :greedy-score (:greedy-score best)
      :all-scores scores})))
