(ns futon5.exotic.scoring
  "Exotic xenotype scoring stub for CT structures.")

(defn exotic-score
  "Compute a placeholder exotic score from CT scaffolding.
  Expected fields in xeno: :exotic {:vision ... :plan ... :adapt ... :pattern-id ...}."
  [xeno _result]
  (let [exotic (:exotic xeno)
        vision? (boolean (:vision exotic))
        plan? (boolean (:plan exotic))
        adapt? (boolean (:adapt exotic))
        vision-clarity (if vision? 1.0 0.0)
        plan-fidelity (if plan? 1.0 0.0)
        adapt-coherence (if adapt? 1.0 0.0)
        score (/ (+ vision-clarity plan-fidelity adapt-coherence) 3.0)]
    {:score score
     :components {:vision-clarity vision-clarity
                  :plan-fidelity plan-fidelity
                  :adapt-coherence adapt-coherence}
     :exotic (select-keys exotic [:pattern-id :vision :plan :adapt])}))
