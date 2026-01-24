(ns futon5.mmca.exotype-legacy
  "Legacy exotype helpers kept for backwards compatibility.

   Deprecated: prefer rule-based 256 physics (rule->physics-params)."
  (:require [futon5.hexagram.lift :as hex-lift]
            [futon5.mmca.exotype :as exotype]))

(defn hexagram->physics-params
  "Map a hexagram to physics parameters.
   Deprecated: use exotype/rule->physics-params for full 256-rule physics."
  [hexagram-id]
  ;; Default to Péng energy for backwards compatibility.
  (exotype/rule->physics-params
   (hex-lift/hexagram+energy->rule (dec (or hexagram-id 1)) 0)))
