(ns futon5.exotic.curriculum
  "Curriculum tightening schedule for ratchet thresholds.")

(def default-schedule
  {:base-threshold 0.0
   :step 0.02
   :tighten-every 5
   :max-threshold 0.3})

(defn curriculum-threshold
  "Compute a tightening threshold based on window index."
  [window-idx schedule]
  (let [{:keys [base-threshold step tighten-every max-threshold]} (merge default-schedule schedule)
        steps (int (Math/floor (/ (double (max 0 window-idx)) (double tighten-every))))
        raw (+ (double base-threshold) (* (double step) (double steps)))]
    (min (double max-threshold) raw)))
