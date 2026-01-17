(ns futon5.exotic.vision-examples
  "Small examples for validating VISION declarations."
  (:require [futon5.exotic.vision :as vision]
            [futon5.exotic.category :as cat]))

(def vision-missions
  [{:id :m9
    :from :start
    :to :ct-foundation
    :label "CT scaffolding"}
   {:id :m10
    :from :ct-foundation
    :to :plan-functor
    :label "PLAN functor"}])

(def vision-skeleton
  (-> (vision/declare-vision
       {:name :exotic/vision-skeleton
        :objects #{:start :ct-foundation :plan-functor}
        :identities {:start :id-start
                     :ct-foundation :id-ct
                     :plan-functor :id-plan}
        :morphisms {:id-start {:dom :start :cod :start}
                    :id-ct {:dom :ct-foundation :cod :ct-foundation}
                    :id-plan {:dom :plan-functor :cod :plan-functor}
                    :m9 {:dom :start :cod :ct-foundation}
                    :m10 {:dom :ct-foundation :cod :plan-functor}}
        :compose-table {[:m10 :m9] :m9+m10
                        [:id-ct :m9] :m9
                        [:m9 :id-start] :m9
                        [:id-plan :m10] :m10
                        [:m10 :id-ct] :m10}
        :missions []})
      (vision/add-missions vision-missions)))

(defn valid-vision?
  []
  (cat/valid-category? (vision/vision->category vision-skeleton)))
