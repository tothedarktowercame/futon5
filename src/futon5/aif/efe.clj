(ns futon5.aif.efe
  "Unit-pure Expected Free Energy for the MetaCA tokamak (R5).

   The canonical 2-term EFE core:
     G_efe = risk + ambiguity
   where:
     risk      (mode :kl) = Σ_ch w_ch · KL(N(μ_ch, σ²_ch) ‖ N(C_μ_ch, σ²_C_ch))
     ambiguity (mode :gaussian-entropy) = Σ_ch ½·ln(2πe·σ²_ch)

   The Gaussian-Gaussian KL is the FULL closed form:
     KL(N₁‖N₂) = ½[ln(σ²₂/σ²₁) + (σ²₁ + (μ₁−μ₂)²)/σ²₂ − 1]

   This namespace is DOMAIN-AGNOSTIC: it operates on plain vectors, not
   macro-feature-shaped maps. The tokamak's policy layer maps its observation
   channels into vectors before calling g-efe.

   This mirrors ants.aif.efe byte-for-byte (the canonical source both ports
   mirror), so the S2 reconciliation merges them into one shared core.

   Faithfulness tag: FEP-derived (R5 — unit-pure G_efe)."
  (:require [clojure.math :as math]))

;; --------------------------------------------------------------------------- ;;
;; Core math kernels — domain-agnostic, plain doubles.
;; --------------------------------------------------------------------------- ;;

(def ^:private two-pi-e
  "The constant 2πe used in Gaussian entropy."
  (* 2.0 Math/PI Math/E))

(defn gaussian-entropy
  "Differential entropy of a univariate Gaussian: ½·ln(2πe·σ²).

   σ² is floored at 1e-9 so zero-variance yields a large-negative finite
   entropy, never -∞. Higher σ² → higher entropy → more uncertain → higher
   EFE ambiguity contribution.

   Matches futon2.aif.efe/ambiguity with :gaussian-entropy mode exactly."
  [sigma2]
  (* 0.5 (math/log (* two-pi-e (max (double sigma2) 1e-9)))))

(defn gaussian-kl
  "KL divergence between two univariate Gaussians:
     KL(N(μ₁,σ²₁) ‖ N(μ₂,σ²₂)) = ½[ln(σ²₂/σ²₁) + (σ²₁ + (μ₁−μ₂)²)/σ²₂ − 1]

   Both variances are floored at 1e-9. Always ≥ 0 for valid inputs.

   This is the FULL Gaussian KL including the log-variance term — the term
   that distinguishes it from the ant's old ½·z² (which omits it)."
  [mu1 sigma2-1 mu2 sigma2-2]
  (let [s1 (max (double sigma2-1) 1e-9)
        s2 (max (double sigma2-2) 1e-9)
        d  (- (double mu1) (double mu2))]
    (* 0.5 (+ (math/log (/ s2 s1))
              (/ (+ s1 (* d d)) s2)
              -1.0))))

(defn ambiguity
  "Sum of per-channel Gaussian entropies over a variance map.

   variance-map: {channel σ²}  or  [σ²₁ σ²₂ …]  (seq of variances)
   mode: :gaussian-entropy (default) — canonical E_Q(s|π)[H[P(o|s)]]

   Matches futon2.aif.efe/ambiguity :gaussian-entropy byte-for-byte."
  ([variance-map]
   (ambiguity variance-map :gaussian-entropy))
  ([variance-map mode]
   (let [vars (if (map? variance-map)
                (vals variance-map)
                variance-map)]
     (case mode
       :gaussian-entropy
       (reduce + 0.0 (map gaussian-entropy vars))
       ;; fallback: raw variance sum (the historical WM mode)
       (reduce + 0.0 (map #(max (double %) 1e-9) vars))))))

(defn risk
  "Sum of per-channel weighted Gaussian KL divergences.

   Each channel contributes w_ch · KL(N(μ_ch, σ²_ch) ‖ N(C_μ_ch, σ²_C_ch)).

   Args (parallel seqs):
     means        predicted μ per channel
     variances    predicted σ² per channel
     c-means      preference mean per channel
     c-variances  preference σ² per channel (σ²_C = sd²)
     weights      per-channel weight (default 1.0 for missing)"
  ([means variances c-means c-variances]
   (risk means variances c-means c-variances nil))
  ([means variances c-means c-variances weights]
   (let [weights (or weights (repeat 1.0))]
     (reduce + 0.0
             (for [[mu s2 cmu cs2 w] (map vector means variances c-means c-variances weights)]
               (* (double w) (gaussian-kl mu s2 cmu cs2)))))))

(defn g-efe
  "Unit-pure Expected Free Energy: risk + ambiguity (the 2-term canonical core).

   Args (parallel seqs of per-channel values):
     means        predicted μ per channel
     variances    predicted σ² per channel
     c-means      preference mean per channel
     c-variances  preference σ² per channel
     opts         {:weights       per-channel risk weights (default all 1.0)
                   :risk-mode     :kl (default) — Gaussian KL risk
                   :ambiguity-mode :gaussian-entropy (default)}

   Returns:
     {:risk        Σ w_ch · KL(N(μ_ch,σ²_ch)‖N(C_ch,σ²_C))
      :ambiguity   Σ ½·ln(2πe·σ²_ch)
      :g-efe       risk + ambiguity  (the canonical G)}

   Nothing else may be called EFE. Controller augmentations (the hex heuristic,
   sigil mapping, etc.) are added OUTSIDE this function."
  ([means variances c-means c-variances]
   (g-efe means variances c-means c-variances {}))
  ([means variances c-means c-variances {:keys [weights risk-mode ambiguity-mode]
                                         :or {risk-mode :kl
                                              ambiguity-mode :gaussian-entropy}}]
   (let [r (case risk-mode
             :kl (risk means variances c-means c-variances weights)
             0.0)
         a (ambiguity variances ambiguity-mode)]
     {:risk r
      :ambiguity a
      :g-efe (+ r a)})))
