(ns futon5.scoring
  "Gate + rank scoring helpers."
  (:require [futon5.mmca.metrics :as metrics]))

(defn- clamp-01 [x]
  (max 0.0 (min 1.0 (double (or x 0.0)))))

(defn gate-rank-score
  "Gate a run, then compute a rank score.

  Inputs:
  - summary: MMCA summary with :avg-change and :avg-entropy-n (optional)
  - raw: feature map used by the ranker (e.g., :delta-short, :delta-envelope)
  - weights: map of feature -> weight
  - gate: thresholds {:freeze-change :freeze-entropy :magma-change :magma-entropy :envelope-floor}
  "
  [{:keys [summary raw weights gate]}]
  (let [gate (merge {:freeze-change 0.05
                     :freeze-entropy 0.2
                     :magma-change 0.45
                     :magma-entropy 0.8
                     :envelope-floor 20.0}
                    (or gate {}))
        avg-change (double (or (:avg-change summary) 0.0))
        avg-entropy (double (or (:avg-entropy-n summary) 0.0))
        envelope (double (or (:envelope raw) 0.0))
        reasons (cond-> []
                  (and (pos? avg-change)
                       (<= avg-change (:freeze-change gate))) (conj :freeze)
                  (and (pos? avg-entropy)
                       (<= avg-entropy (:freeze-entropy gate))) (conj :freeze)
                  (>= avg-change (:magma-change gate)) (conj :magma)
                  (>= avg-entropy (:magma-entropy gate)) (conj :magma)
                  (< envelope (:envelope-floor gate)) (conj :low-envelope))
        passed? (empty? reasons)
        score (reduce (fn [acc [k w]]
                        (+ acc (* (double (or w 0.0))
                                  (double (or (get raw k) 0.0)))))
                      0.0
                      weights)]
    {:gate {:passed? passed?
            :reasons (vec reasons)}
     :rank {:score score}
     :raw raw}))
