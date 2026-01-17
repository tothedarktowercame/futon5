(ns futon5.exotic.vision
  "Helpers for declaring VISION structures and mission morphisms."
  (:require [futon5.exotic.category :as cat]))

(defrecord Vision [name objects morphisms identities compose-table missions])

(defn declare-vision
  "Create a vision declaration. Missions can be added later."
  [{:keys [name objects morphisms identities compose-table missions]}]
  (->Vision name (set objects) (or morphisms {}) (or identities {})
            (or compose-table {}) (or missions [])))

(defn mission->morphism [mission]
  (let [mid (:id mission)]
    [mid {:dom (:from mission)
          :cod (:to mission)}]))

(defn add-missions
  "Attach mission morphisms to the vision and record mission metadata."
  [^Vision vision missions]
  (let [mission-morphs (into {} (map mission->morphism missions))]
    (-> vision
        (update :morphisms merge mission-morphs)
        (update :missions into missions))))

(defn vision->category
  "Convert a vision declaration into a Category record."
  [^Vision vision]
  (cat/->Category (:objects vision)
                  (:morphisms vision)
                  (:identities vision)
                  (:compose-table vision)))
