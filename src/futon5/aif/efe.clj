(ns futon5.aif.efe
  "Unit-pure Expected Free Energy for the MetaCA tokamak (R5).

   RE-EXPORT namespace: the math bodies now live in futon2.aif.core-efe (the
   shared domain-agnostic implementation both ports consume). This file
   re-exports them so all existing tokamak call sites stay unchanged.

   The canonical 2-term EFE core:
     G_efe = risk + ambiguity
   where:
     risk      (mode :kl) = Σ_ch w_ch · KL(N(μ_ch, σ²_ch) ‖ N(C_μ_ch, σ²_C_ch))
     ambiguity (mode :gaussian-entropy) = Σ_ch ½·ln(2πe·σ²_ch)

   Faithfulness tag: FEP-derived (R5 — unit-pure G_efe)."
  (:require [futon2.aif.core-efe :as core-efe]))

(def gaussian-entropy core-efe/gaussian-entropy)
(def gaussian-kl core-efe/gaussian-kl)
(def ambiguity core-efe/ambiguity)
(def risk core-efe/risk)
(def g-efe core-efe/g-efe)
