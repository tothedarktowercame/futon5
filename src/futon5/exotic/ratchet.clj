(ns futon5.exotic.ratchet
  "Windowed ratchet memory for delta scoring."
  (:require [clojure.string :as str]))

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

(defn ratchet-summary
  "Generate a minimal ratchet summary for logging."
  [state]
  {:window-idx (:window-idx state)
   :window-count (count (:windows state))})
