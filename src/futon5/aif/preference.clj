(ns futon5.aif.preference
  "The EoC confinement preference C (R19) for the MetaCA tokamak.

   C is a GRADED preference over the macro-feature observation ABI — NOT the
   binary ok-regime? gate (cyber_mmca_compare.clj:113). It defines what the
   tokamak 'wants': the edge-of-chaos confinement manifold.

   Structure:
   - Per-channel Gaussian targets over {:pressure :selectivity :structure
     :activity}, centered on the EoC band.

   The preference is consumed by g-efe's risk term as the C-vector
   (target means + target variances). This namespace maps the macro-feature
   observation shape into the plain vectors g-efe expects.

   RETRACT TABLE (Slice 3b ablation):
   - regime-penalty (discrete→continuous regime cost): DELETED. Ablation
     proved it was inert at deployed weight (3.0): the macro-features that
     cause :freeze/:magma are structurally far enough from C that g-efe's
     KL-risk alone always exceeds the penalty. A search over 500+ synthetic
     macro-feature combinations found zero winner-flips. The :aif controller
     now runs on pure g-efe + shared precision — a valid, cleaner result.

   Faithfulness tag: FEP-derived (R19 — explicit graded preference C)."
  (:require [futon5.aif.efe :as efe]))

;; --------------------------------------------------------------------------- ;;
;; The EoC confinement set-point.
;; --------------------------------------------------------------------------- ;;

(def eoc-targets
  "Target means and sds for each numeric macro-feature channel in the EoC band.
   These encode 'the tokamak wants mid-band pressure/selectivity/structure/activity'
   — the edge-of-chaos is neither frozen (low pressure, high structure) nor
   molten (high pressure, low structure).

   *** MEASURED: THIS BAND IS AT BEST MARGINALLY REACHABLE (2026-07-15). ***

   These values are left as-is ON PURPOSE.  They are the principled scientific
   target — where the edge of chaos IS — and are not to be tuned to whatever the
   plant happens to do; that would be moving the goalposts to manufacture a pass.

   The reachable set is NOT a constant.  It is a function of (genotype, window
   length, time), measured over the whole live actuator (r ∈ Z/8, all 8
   rotations):

     config A (genotype-99, W=12): pressure 0.076..0.279, selectivity 0.604..0.896
     config B (genotype-99, W=8) : pressure 0.208..0.500, selectivity 0.583..0.875

   So, honestly:
     - :selectivity 0.4 is BELOW the floor in every config measured (floors
       0.55–0.60).  Unreachable, robustly.
     - :pressure 0.5 is unreachable in config A (max 0.279) but EXACTLY reachable
       in config B (r=6 → 0.5000).  An earlier note here claimed 0.5 was
       unreachable outright; that generalised one config and was wrong.
     - After the plant drifts, pressure 0.5 is gone anyway (max 0.336 by t=6).

   So the EoC band is reachable at best on ONE channel, in SOME configs, at t=0
   only — and see the niche-construction note below, because the act of steering
   toward it is what destroys it.

   It also has a control-theoretic consequence that silently nulls experiments:
   an unreachable target in a FIXED direction makes argmin over the reachable
   set a CONSTANT, so the controller emits one action forever and is
   indistinguishable from :null.  Any experiment scored against these targets is
   vacuous by construction.  Use REACHABLE set-points to test confinement (claim
   A); use this band only to ask whether the reachable region IS the EoC (claim
   B), whose honest current answer is 'the band cannot be reached'.

   NICHE CONSTRUCTION (measured 2026-07-15).  The reachable set does not merely
   drift — THE CONTROLLER'S OWN ACTIONS COLLAPSE IT.  From an identical start,
   after 6 windows:

     6x :hold        → reachable pressure up to 0.336
     mixed rotations → up to 0.208
     6x :rotate-down → up to 0.188

   Acting COSTS future control authority.  So the tokamak's real problem is not
   regulation of an exogenously drifting plant: the agent is modifying its own
   affordance landscape, and steering toward the high-pressure corner is what
   destroys the high-pressure corner.  A myopic controller spends authority it
   will need; this is precisely what R13's horizon (S(π) = Σ ρ^t s(s_t)) is for,
   and it makes this testbed a niche-construction problem (cf. M-aif2) rather
   than a set-point-regulation one.

   Reachability is a precondition to check BEFORE running, not a result to
   discover after — see `futon5.aif.design-gates`."
  {:pressure    {:mean 0.5  :sd 0.15}   ; mid-band change rate
   :selectivity {:mean 0.4  :sd 0.15}   ; moderate template-matching
   :structure   {:mean 0.4  :sd 0.15}   ; moderate temporal autocorrelation
   :activity    {:mean 0.5  :sd 0.15}}) ; mid-band activity

(def numeric-channels
  "The ordered numeric macro-feature channels C is scored over.

   DEGENERACY FIX (2026-07-14, verified by control-authority + structure probes):
   the macro-feature ABI presents FOUR channels but carries only TWO independent
   signals, so scoring all four made C a malformed objective:

     :pressure    = normalize(avg-change)                    <- real signal #1
     :activity    = normalize(avg-change)   IDENTICAL to :pressure
                    (metrics.clj:626,629 feed both from (:avg-change summary);
                     README-cyber-mmca admits ':activity - currently same as
                     pressure'.)  Scoring it DOUBLE-COUNTED change-rate.
     :structure   = normalize(temporal-autocorr), and
                    temporal-autocorr = avg(1 - hamming-rate) = 1 - avg-change
                    EXACTLY (metrics.clj temporal-autocorr-window) -> it is the
                    COMPLEMENT of :pressure, carrying no independent information.
                    In practice it is also nil on the metrics path, because
                    run-mmca's metrics entries carry no :temporal-autocorr at all
                    -> C was scoring a MISSING value.
     :selectivity = normalize(1 - avg-unique)                <- real signal #2

   So C is scored over the two genuinely independent dimensions only.  This is a
   fix to the OBJECTIVE, not to the observation: metrics.clj still reports all
   four channels; we simply refuse to score a duplicate and a complement.
   (That the plant's observable state is only 2-D is itself the argument for the
   causal-state / particle evaluator: macro-features are too impoverished to
   characterise edge-of-chaos.)"
  [:pressure :selectivity])

(defn c-vectors
  "Return the preference C as parallel vectors for g-efe:
   {:c-means [μ₁ μ₂ …] :c-variances [σ²₁ σ²₂ …]}
   over the numeric macro-feature channels.

   Arity-0 uses the default EoC set-point. Arity-1 takes an EXPLICIT target
   map {channel {:mean m :sd s}} — this is what makes the controller
   RE-TARGETABLE: it can be asked to confine to any C, not only the EoC band.
   The re-targeting test (claim A: competent confinement) depends on this."
  ([] (c-vectors eoc-targets))
  ([targets]
   (let [c-means (mapv #(:mean (targets %)) numeric-channels)
         c-vars  (mapv #(let [sd (:sd (targets %))] (* sd sd)) numeric-channels)]
     {:c-means c-means
      :c-variances c-vars})))

(defn macro-features->vectors
  "Project a macro-feature observation map (from windowed-macro-features /
   forward-predict :mean) into parallel vectors for g-efe.
   Returns {:means [μ₁ …] :variances [σ²₁ …]}.

   The means come from the observation; the variances come from the
   forward-predict :variance map (or a default if not present)."
  ([macro-features]
   (macro-features->vectors macro-features nil))
  ([macro-features variance-map]
   (let [means (mapv #(double (or (get macro-features %) 0.5)) numeric-channels)
         default-var 0.0025  ; 0.05² — the forward-predict default noise
         vars (mapv #(if variance-map
                       (double (get variance-map % default-var))
                       default-var)
                    numeric-channels)]
     {:means means
      :variances vars})))

(defn g-efe-pure
  "Compute the PURE g-efe (risk + ambiguity only) for a forward-predict
   result against C. This calls futon2.aif.core-efe/g-efe via the re-export.
   The result is the unit-pure 2-term core — nothing else may be called EFE."
  ([fp-result]
   (g-efe-pure fp-result {}))
  ([fp-result {:keys [weights target-c]}]
   (let [{:keys [mean variance]} fp-result
         {:keys [means variances]} (macro-features->vectors mean variance)
         {:keys [c-means c-variances]} (c-vectors (or target-c eoc-targets))]
     (efe/g-efe means variances c-means c-variances {:weights weights}))))

(defn controller-score
  "Compute the controller-score for a forward-predict result against C.

   After the Slice 3b ablation, controller-score = g-efe (pure) with NO
   augmentations. The regime-penalty was deleted because it was inert at
   deployed weight (the macro-features that cause non-:eoc regimes are
   already far enough from C that g-efe's KL-risk dominates).

   Returns:
     {:action          the action keyword
      :g-efe           the pure g-efe {:risk :ambiguity :g-efe}
      :augmentations   [] (empty — no augmentations survive)
      :controller-score  g-efe (:g-efe of the pure result)
      :regime          the predicted regime}"
  ([fp-result action]
   (controller-score fp-result action {}))
  ([fp-result action opts]
   (let [pure (g-efe-pure fp-result opts)]
     {:action action
      :g-efe pure
      :augmentations []
      :controller-score (:g-efe pure)
      :regime (:regime (:mean fp-result))})))

(defn score-action
  "Backward-compatible score function. Delegates to controller-score."
  ([fp-result action]
   (score-action fp-result action {}))
  ([fp-result action opts]
   (let [cs (controller-score fp-result action opts)
         pure (:g-efe cs)]
     {:action (:action cs)
      :risk (:risk pure)
      :ambiguity (:ambiguity pure)
      :g-efe (:controller-score cs)
      :regime (:regime cs)
      :augmentations (:augmentations cs)})))
