(ns futon5.mmca.trigram
  "Early vs late (trigram-like) scoring for phenotype histories."
  (:require [futon5.mmca.metrics :as metrics]))

(defn- clamp-01 [x]
  (max 0.0 (min 1.0 (double (or x 0.0)))))

(defn- activity-score [activity]
  ;; Prefer mid activity; 0.5 -> 1.0, 0 or 1 -> 0.0.
  (clamp-01 (- 1.0 (* 2.0 (Math/abs (- (double activity) 0.5))))))

(defn- norm-entropy [s]
  (let [len (count (or s ""))]
    (if (pos? len)
      (let [raw (metrics/shannon-entropy s)
            maxe (/ (Math/log len) (Math/log 2.0))]
        (if (pos? maxe) (/ raw maxe) 0.0))
      0.0)))

(defn- frame-activity [s]
  (let [chars (seq (or s ""))]
    (if (seq chars)
      (/ (double (count (filter #(= % \1) chars))) (double (count chars)))
      0.0)))

(defn- summarize-half [frames]
  (let [activities (map frame-activity frames)
        entropies (map norm-entropy frames)
        avg-activity (metrics/avg activities)
        avg-entropy (metrics/avg entropies)
        a-score (activity-score avg-activity)
        e-score (clamp-01 avg-entropy)
        score (/ (+ a-score e-score) 2.0)]
    {:avg-activity avg-activity
     :avg-entropy-n avg-entropy
     :score score}))

(defn score-early-late
  "Split phenotype history into early/late halves and score balance.
  Returns a map with per-half scores and a balance-penalized score (0-100)."
  [phe-history]
  (let [frames (vec (or phe-history []))
        n (count frames)]
    (if (pos? n)
      (let [mid (quot n 2)
            early (subvec frames 0 mid)
            late (subvec frames mid n)
            early-summary (summarize-half early)
            late-summary (summarize-half late)
            early-score (double (or (:score early-summary) 0.0))
            late-score (double (or (:score late-summary) 0.0))
            balance (- 1.0 (Math/abs (- early-score late-score)))
            base (/ (+ early-score late-score) 2.0)
            score (* 100.0 base (clamp-01 balance))]
        {:early early-summary
         :late late-summary
         :balance (clamp-01 balance)
         :score score})
      {:early {:avg-activity 0.0 :avg-entropy-n 0.0 :score 0.0}
       :late {:avg-activity 0.0 :avg-entropy-n 0.0 :score 0.0}
       :balance 0.0
       :score 0.0})))
