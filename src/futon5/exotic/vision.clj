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

(defn- ensure-identity-ids
  "Ensure identity morphisms exist for each object."
  [objects identities morphisms]
  (let [identities (or identities {})
        id-for (fn [obj]
                 (or (get identities obj)
                     (keyword (str "id-" (name obj)))))
        identities' (into {}
                          (map (fn [obj] [obj (id-for obj)]) objects))
        morphisms' (reduce (fn [acc [obj mid]]
                             (if (contains? acc mid)
                               acc
                               (assoc acc mid {:dom obj :cod obj})))
                           (or morphisms {})
                           identities')]
    {:identities identities'
     :morphisms morphisms'}))

(defn build-vision-from-template
  "Build a Vision from a template map with :objects and :morphisms.
  Morphisms can be {:m1 [:a :b]} or {:m1 {:dom :a :cod :b}}."
  [{:keys [name objects morphisms identities compose] :as template}]
  (let [objects (set objects)
        morphisms (into {}
                        (map (fn [[mid spec]]
                               (if (and (vector? spec) (= 2 (count spec)))
                                 [mid {:dom (first spec) :cod (second spec)}]
                                 [mid spec])))
                        (or morphisms {}))
        {:keys [identities morphisms]} (ensure-identity-ids objects identities morphisms)]
    (declare-vision
     {:name (or name :exotic/vision-template)
      :objects objects
      :identities identities
      :morphisms morphisms
      :compose-table (or compose {})
      :missions []})))
