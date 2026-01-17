(ns futon5.exotic.scoring
  "Exotic xenotype scoring stub for CT structures.")

(defn- clamp-01 [x]
  (cond
    (not (number? x)) 0.0
    (< x 0.0) 0.0
    (> x 1.0) 1.0
    :else (double x)))

(defn exotic-score
  "Compute a placeholder exotic score from CT scaffolding.
  Expected fields in xeno: :exotic {:vision ... :plan ... :adapt ... :pattern-id ...}
  Optional: :ratchet {:prev-score x :curr-score y}."
  [xeno _result]
  (let [exotic (:exotic xeno)
        ratchet (:ratchet xeno)
        vision? (boolean (:vision exotic))
        plan? (boolean (:plan exotic))
        adapt? (boolean (:adapt exotic))
        vision-clarity (if vision? 1.0 0.0)
        plan-fidelity (if plan? 1.0 0.0)
        adapt-coherence (if adapt? 1.0 0.0)
        base (/ (+ vision-clarity plan-fidelity adapt-coherence) 3.0)
        delta (when (and (number? (:prev-score ratchet))
                         (number? (:curr-score ratchet)))
                (- (double (:curr-score ratchet))
                   (double (:prev-score ratchet))))
        delta-score (when (number? delta)
                      (clamp-01 (+ 0.5 (* 0.5 delta))))
        score (if (number? delta-score)
                (clamp-01 (/ (+ base delta-score) 2.0))
                base)]
    {:score score
     :components (cond-> {:vision-clarity vision-clarity
                          :plan-fidelity plan-fidelity
                          :adapt-coherence adapt-coherence}
                   (number? delta-score) (assoc :delta-score delta-score))
     :exotic (select-keys exotic [:pattern-id :vision :plan :adapt])
     :ratchet (when (number? delta-score)
                {:delta delta
                 :delta-score delta-score})}))
