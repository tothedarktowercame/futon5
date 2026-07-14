(ns futon5.aif.preference
  "The EoC confinement preference C (R19) for the MetaCA tokamak.

   C is a GRADED preference over the macro-feature observation ABI — NOT the
   binary ok-regime? gate (cyber_mmca_compare.clj:113). It defines what the
   tokamak 'wants': the edge-of-chaos confinement manifold.

   Structure:
   - Per-channel Gaussian targets over {:pressure :selectivity :structure
     :activity}, centered on the EoC band.
   - A regime-preference term: :eoc is preferred, :freeze/:magma are penalized.

   The preference is consumed by g-efe's risk term as the C-vector
   (target means + target variances). This namespace maps the macro-feature
   observation shape into the plain vectors g-efe expects.

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

(def ^:private regime-penalty
  "Penalty for predicted regimes that are NOT :eoc. This is a NAMED
   AUGMENTATION term — it is NOT part of the unit-pure g-efe (which stays
   in futon2.aif.core-efe). It carries a typed residual:
   {:term :regime-penalty :tag :principled-approx :residual ...}."
  {:freeze 3.0   ; dead order — strongly penalized
   :magma  3.0   ; chaos/boil — strongly penalized
   :static 1.0   ; sub-EoC — mildly penalized
   :chaos  1.0   ; super-EoC — mildly penalized
   :eoc    0.0}) ; target — no penalty

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

(defn regime-cost
  "The regime-preference cost for a predicted regime keyword.
   Returns the penalty from regime-penalty (0.0 for :eoc, up to 3.0 for
   :freeze/:magma). This is a NAMED AUGMENTATION — NOT inside g-efe."
  [regime]
  (double (get regime-penalty regime 0.0)))

(defn regime-augmentation
  "Compute the regime-penalty augmentation term with a typed residual.
   Returns:
     {:term :regime-penalty
      :tag :principled-approx
      :value  the penalty (0.0 for :eoc, up to 3.0 for :freeze/:magma)
      :residual {:regime regime :note \"discrete->continuous conversion\"}}
   This is added to g-efe OUTSIDE the unit-pure kernel to form
   controller-score."
  [regime]
  {:term :regime-penalty
   :tag :principled-approx
   :value (regime-cost regime)
   :residual {:regime regime :note "discrete->continuous conversion"}})

(defn g-efe-pure
  "Compute the PURE g-efe (risk + ambiguity only, NO augmentation) for a
   forward-predict result against C.

   This calls futon2.aif.core-efe/g-efe via the re-export. The result is
   the unit-pure 2-term core — nothing else may be called EFE."
  ([fp-result]
   (g-efe-pure fp-result {}))
  ([fp-result {:keys [weights]}]
   (let [{:keys [mean variance]} fp-result
         {:keys [means variances]} (macro-features->vectors mean variance)
         {:keys [c-means c-variances]} (c-vectors)]
     (efe/g-efe means variances c-means c-variances {:weights weights}))))

(defn controller-score
  "Compute the controller-score = g-efe (pure) + regime-penalty augmentation.

   This is the EXPLICIT split:
   - g-efe: the unit-pure 2-term core (risk + ambiguity) from futon2.aif.core-efe
   - regime-penalty: a named augmentation with typed residual, NOT inside g-efe

   Returns:
     {:action     the action keyword
      :g-efe      the pure g-efe {:risk :ambiguity :g-efe}
      :augmentations [regime-augmentation-map]
      :controller-score  g-efe + augmentation values
      :regime     the predicted regime}"
  ([fp-result action]
   (controller-score fp-result action {}))
  ([fp-result action opts]
   (let [pure (g-efe-pure fp-result opts)
         reg-aug (regime-augmentation (:regime (:mean fp-result)))
         aug-value (:value reg-aug)
         total-score (+ (:g-efe pure) aug-value)]
     {:action action
      :g-efe pure
      :augmentations [reg-aug]
      :controller-score total-score
      :regime (:regime (:mean fp-result))})))

(defn score-action
  "Backward-compatible score function. Returns the same shape as S2 but now
   delegates to controller-score (which has the explicit split)."
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
