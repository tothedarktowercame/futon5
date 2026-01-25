(ns nonstarter.estimate
  "Heuristic cost estimation for Nonstarter proposals.")

(def ^:private size-costs
  {:tiny 50
   :small 150
   :medium 300
   :large 500
   :xl 800})

(def ^:private complexity-mults
  {:low 0.8
   :med 1.0
   :high 1.3
   :extreme 1.6})

(def ^:private risk-mults
  {:low 0.9
   :med 1.0
   :high 1.2})

(defn estimate-cost
  "Estimate mana cost for a proposal.

   Inputs:
   - :size (keyword) one of #{:tiny :small :medium :large :xl}
   - :complexity (keyword) one of #{:low :med :high :extreme}
   - :risk (keyword) one of #{:low :med :high}
   - :base (number) optional base override

   Returns {:mana N :breakdown {...}}"
  [{:keys [size complexity risk base]
    :or {size :medium
         complexity :med
         risk :med}}]
  (let [size* (get size-costs size (:medium size-costs))
        base* (double (or base size*))
        cmult (get complexity-mults complexity 1.0)
        rmult (get risk-mults risk 1.0)
        mana (Math/round (* base* cmult rmult))]
    {:mana mana
     :breakdown {:base base*
                 :size size
                 :complexity complexity
                 :risk risk
                 :complexity-mult cmult
                 :risk-mult rmult}}))

