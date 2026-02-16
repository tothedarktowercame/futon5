(ns nonstarter.adapter
  "Adapter helpers for portal candidates → nonstarter policy inputs.

   Targets the new-style multiarg/flexiarg surface used in futon0/futon3a devmaps."
  (:require [clojure.string :as str]))

(def ^:private block-keys
  [:context :if :however :then :because])

(defn- candidate-id [candidate]
  (or (:external-id candidate)
      (:id candidate)
      (:name candidate)))

(defn- candidate-title [candidate]
  (or (:title candidate)
      (:name candidate)
      (candidate-id candidate)))

(defn- normalize-blocks [blocks]
  (reduce (fn [acc k]
            (assoc acc k (get blocks k)))
          {}
          block-keys))

(defn- extract-blocks [candidate]
  (let [blocks (cond
                 (map? (:blocks candidate)) (:blocks candidate)
                 (map? (:flexiarg candidate)) (:flexiarg candidate)
                 (map? (:multiarg candidate)) (:multiarg candidate)
                 :else {})]
    (normalize-blocks blocks)))

(defn normalize-candidate
  "Normalize a portal candidate into the new-style surface.

   Missing block fields are included with nil values to keep shape stable."
  [candidate]
  {:pattern/id (candidate-id candidate)
   :pattern/title (candidate-title candidate)
   :pattern/namespace (:namespace candidate)
   :pattern/surface :multiarg
   :pattern/score (:score candidate)
   :blocks (extract-blocks candidate)
   :evidence (or (:evidence candidate) (:evidence-for-settled candidate) [])
   :next (or (:next candidate) (:next-steps candidate) [])
   :psr-example (:psr-example candidate)
   :pur-template (:pur-template candidate)
   :raw candidate})

(defn normalize-candidates
  "Normalize a sequence of portal candidates."
  [candidates]
  (mapv normalize-candidate candidates))

(defn policy-input
  "Build a policy simulation input envelope from raw candidates."
  [{:keys [session-id turn intent aif candidates]}]
  {:session/id session-id
   :turn turn
   :intent (or (some-> intent str/trim not-empty) "")
   :aif aif
   :candidates (normalize-candidates candidates)})

