(ns futon5.mmca.payload
  "Shared report/feedback payload contract helpers.

   These payload shapes are used by metaevolve and tensor pipelines to keep
   downstream consumers branch-agnostic."
  (:require [clojure.set :as set]))

(defn feedback-payload
  "Build a canonical feedback payload.

   input keys:
   - :meta (map merged into output)
   - :feedback-sigils (vector)
   - :learned-specs (map)
   - :leaderboard (vector)
   - :runs-completed (optional)"
  [{:keys [meta feedback-sigils learned-specs leaderboard runs-completed]}]
  (cond-> (merge {:feedback-sigils feedback-sigils
                  :learned-specs learned-specs
                  :leaderboard leaderboard}
                 meta)
    runs-completed (assoc :runs-completed runs-completed)))

(defn report-payload
  "Build a canonical report payload.

   input keys:
   - :meta (map merged into output)
   - :ranked (vector)
   - :learned-specs (map)
   - :leaderboard (vector)
   - :runs-detail (vector)
   - :top-runs (vector)"
  [{:keys [meta ranked learned-specs leaderboard runs-detail top-runs]}]
  (merge meta
         {:ranked ranked
          :learned-specs learned-specs
          :leaderboard leaderboard
          :runs-detail runs-detail
          :top-runs top-runs}))

(defn learned-specs-delta
  "Compute newly added learned-spec keys between revisions."
  [prev learned]
  (let [prev (set (keys (or prev {})))
        learned (set (keys (or learned {})))]
    (set/difference learned prev)))
