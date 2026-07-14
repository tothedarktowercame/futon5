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
  "Penalty for predicted regimes that are NOT :eoc. Added to g-efe's risk
   as an extra 'virtual channel' — a principled approximation that converts
   the discrete regime label into a continuous cost."
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
   :freeze/:magma). This is added to g-efe's risk as a discrete-regime term."
  [regime]
  (double (get regime-penalty regime 0.0)))

(defn score-action
  "Score a single action's forward-predict result against C.

   Args:
     fp-result  — the {:mean :variance} map from forward-predict
     action     — the action keyword (for telemetry)
     opts       — {:weights per-channel risk weights (optional)}

   Returns:
     {:action  the action
      :risk    Σ KL(N(μ,σ²)‖N(C_μ,C_σ²))  + regime-cost
      :ambiguity Σ ½·ln(2πe·σ²)
      :g-efe   risk + ambiguity + regime-cost
      :regime  the predicted regime}

   The regime-cost is added to :risk so it enters g-efe as a pragmatic term.
   This is tagged :principled-approx (the discrete→continuous conversion is a
   modeling choice, not a derived identity)."
  ([fp-result action]
   (score-action fp-result action {}))
  ([fp-result action {:keys [weights]}]
   (let [{:keys [mean variance]} fp-result
         {:keys [means variances]} (macro-features->vectors mean variance)
         {:keys [c-means c-variances]} (c-vectors)
         efe-result (efe/g-efe means variances c-means c-variances
                               {:weights weights})
         r-cost (regime-cost (:regime mean))
         total-risk (+ (:risk efe-result) r-cost)]
     {:action action
      :risk total-risk
      :ambiguity (:ambiguity efe-result)
      :g-efe (+ total-risk (:ambiguity efe-result))
      :regime (:regime mean)})))
