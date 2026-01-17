(ns futon5.exotic.adapt
  "Adaptation helpers for updating plans with evidence."
  (:require [futon5.exotic.natural :as natural]))

(defn adaptation
  "Create an adaptation record."
  [{:keys [id from to evidence residuals]}]
  {:adaptation/id id
   :from from
   :to to
   :evidence evidence
   :residuals residuals})

(defn log-adaptation
  "Return an adaptation map, ensuring evidence is present."
  [adaptation]
  (when-not (natural/justified? (:evidence adaptation))
    (throw (ex-info "Adaptation missing evidence" {:adaptation adaptation})))
  adaptation)
