(ns futon5.exotic.ratchet
  "Windowed ratchet memory for delta scoring.")

(defn init-state
  "Initialize ratchet state."
  []
  {:window-idx 0
   :windows []})

(defn update-window
  "Return updated state after adding a window summary."
  [state summary]
  (-> state
      (update :windows conj summary)
      (update :window-idx inc)))

(defn- clamp
  [x lo hi]
  (max lo (min hi x)))

(defn- clamp-01
  [x]
  (clamp x 0.0 1.0))

(def ^:private default-weights
  {:mean 0.6
   :q50 0.4})

(defn- normalize-delta
  [delta scale]
  (when (number? delta)
    (let [scale (double (or scale 1.0))
          denom (max scale 1.0)]
      (clamp (/ (double delta) denom) -1.0 1.0))))

(defn delta-score
  "Compute delta between last two windows. Returns nil if unavailable."
  [state key]
  (let [windows (:windows state)
        n (count windows)]
    (when (>= n 2)
      (let [prev (get (nth windows (- n 2)) key)
            curr (get (nth windows (dec n)) key)]
        (when (and (number? prev) (number? curr))
          (- (double curr) (double prev)))))))

(defn ratchet-context
  "Compute normalized ratchet deltas and scoring context from window stats."
  [prev curr {:keys [weights scale floor threshold window gate?]
              :or {weights default-weights
                   floor 1.0}}]
  (when (and (map? prev) (map? curr))
    (let [w-mean (double (or (:mean weights) 0.0))
          w-q50 (double (or (:q50 weights) 0.0))
          w-sum (if (pos? (+ w-mean w-q50)) (+ w-mean w-q50) 1.0)
          delta-mean (when (and (number? (:mean prev)) (number? (:mean curr)))
                       (- (double (:mean curr)) (double (:mean prev))))
          delta-q50 (when (and (number? (:q50 prev)) (number? (:q50 curr)))
                      (- (double (:q50 curr)) (double (:q50 prev))))
          scale-val (cond
                      (number? scale) scale
                      (number? (:stddev curr)) (max floor (double (:stddev curr)))
                      :else floor)
          mean-n (normalize-delta delta-mean scale-val)
          q50-n (normalize-delta delta-q50 scale-val)
          weighted (when (or (number? mean-n) (number? q50-n))
                     (/ (+ (* w-mean (double (or mean-n 0.0)))
                           (* w-q50 (double (or q50-n 0.0))))
                        w-sum))
          delta-score (when (number? weighted)
                        (clamp-01 (+ 0.5 (* 0.5 weighted))))
          gated? (and gate? (number? threshold) (number? weighted) (< weighted threshold))]
      {:prev-score (:mean prev)
       :curr-score (:mean curr)
       :delta-mean delta-mean
       :delta-q50 delta-q50
       :delta-mean-n mean-n
       :delta-q50-n q50-n
       :delta-weighted weighted
       :delta-score delta-score
       :scale scale-val
       :weights {:mean w-mean :q50 w-q50}
       :gate (when gated? :blocked)
       :curriculum {:threshold threshold
                    :window window}})))

(defn ratchet-summary
  "Generate a minimal ratchet summary for logging."
  [state]
  {:window-idx (:window-idx state)
   :window-count (count (:windows state))})
