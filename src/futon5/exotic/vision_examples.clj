(ns futon5.exotic.vision-examples
  "Small examples for validating VISION declarations."
  (:require [futon5.exotic.vision :as vision]
            [futon5.exotic.category :as cat]
            [futon5.exotic.functor :as functor]
            [futon5.exotic.natural :as natural]
            [futon5.exotic.adapt :as adapt]))

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

(def execution-category
  (cat/->Category
   #{:exec-start :exec-ct :exec-plan}
   {:id-exec-start {:dom :exec-start :cod :exec-start}
    :id-exec-ct {:dom :exec-ct :cod :exec-ct}
    :id-exec-plan {:dom :exec-plan :cod :exec-plan}
    :exec-m9 {:dom :exec-start :cod :exec-ct}
    :exec-m10 {:dom :exec-ct :cod :exec-plan}
    :exec-m9+m10 {:dom :exec-start :cod :exec-plan}}
   {:exec-start :id-exec-start
    :exec-ct :id-exec-ct
    :exec-plan :id-exec-plan}
   {[:exec-m10 :exec-m9] :exec-m9+m10
    [:id-exec-ct :exec-m9] :exec-m9
    [:exec-m9 :id-exec-start] :exec-m9
    [:id-exec-plan :exec-m10] :exec-m10
    [:exec-m10 :id-exec-ct] :exec-m10}))

(def plan-functor
  (functor/->Functor
   :plan-functor
   :exotic/vision-skeleton
   :exotic/exec-skeleton
   {:start :exec-start
    :ct-foundation :exec-ct
    :plan-functor :exec-plan}
   {:id-start :id-exec-start
    :id-ct :id-exec-ct
    :id-plan :id-exec-plan
    :m9 :exec-m9
    :m10 :exec-m10
    :m9+m10 :exec-m9+m10}))

(defn valid-plan-functor?
  []
  (functor/preserves-composition?
   plan-functor
   (vision/vision->category vision-skeleton)
   execution-category))

(def adapt-transform
  (natural/->NatTrans
   :adapt-plan
   :plan-functor
   :plan-functor
   {:m9 :m9
    :m10 :m10
    :m9+m10 :m9+m10}))

(defn log-adaptation-example
  []
  (adapt/log-adaptation
   (adapt/adaptation
    {:id "adapt-001"
     :from :plan-functor
     :to :plan-functor
     :evidence (adapt/evidence {:artifact-id "demo-evidence"})
     :nattrans adapt-transform
     :morphisms [:m9 :m10 :m9+m10]})))
