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

(def ^:private eoc-targets
  "Target means and sds for each numeric macro-feature channel in the EoC band.
   These encode 'the tokamak wants mid-band pressure/selectivity/structure/activity'
   — the edge-of-chaos is neither frozen (low pressure, high structure) nor
   molten (high pressure, low structure)."
  {:pressure    {:mean 0.5  :sd 0.15}   ; mid-band change rate
   :selectivity {:mean 0.4  :sd 0.15}   ; moderate template-matching
   :structure   {:mean 0.4  :sd 0.15}   ; moderate temporal autocorrelation
   :activity    {:mean 0.5  :sd 0.15}}) ; mid-band activity

(def ^:private numeric-channels
  "The ordered numeric macro-feature channels (for vector projection)."
  [:pressure :selectivity :structure :activity])

(defn c-vectors
  "Return the preference C as parallel vectors for g-efe:
   {:c-means [μ₁ μ₂ …] :c-variances [σ²₁ σ²₂ …]}
   over the numeric macro-feature channels."
  []
  (let [c-means (mapv #(:mean (eoc-targets %)) numeric-channels)
        c-vars  (mapv #(* (:sd (eoc-targets %)) (:sd (eoc-targets %))) numeric-channels)]
    {:c-means c-means
     :c-variances c-vars}))

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
  ([fp-result {:keys [weights]}]
   (let [{:keys [mean variance]} fp-result
         {:keys [means variances]} (macro-features->vectors mean variance)
         {:keys [c-means c-variances]} (c-vectors)]
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
