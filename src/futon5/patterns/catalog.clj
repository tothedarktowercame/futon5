(ns futon5.patterns.catalog
  "Catalog of lifted sigil patterns wired to executable stubs or concrete operators."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [futon5.mmca.operators :as operators]))

(defn load-pattern-lifts []
  (-> "futon5/pattern-lifts.edn" io/resource slurp edn/read-string))

(def pattern-lifts (delay (load-pattern-lifts)))

(defn available-patterns [] @pattern-lifts)

(def role->builder
  {"BaldwinLift" operators/baldwin-lift
   "EntropyPulse" operators/entropy-pulse
   "BlendHand" operators/blend-hand
   "UpliftOperator" operators/uplift-operator})

(defn stub-hook [impl role event]
  (fn [& {:keys [world meta params]}]
    {:impl impl
     :role role
     :event event
     :world world
     :meta meta
     :params params}))

(defn- stub-implementation [entry]
  (let [{:keys [lift]} entry
        role (:role lift)
        impl :pending
        mk (partial stub-hook impl role)]
    {:pattern role
     :impl impl
     :hooks {:init (mk :init)
             :observe (mk :observe)
             :decide (mk :decide)
             :act (mk :act)}
     :parameters (:parameters lift)}))

(defn implement-pattern [entry]
  (let [{:keys [lift]} entry
        role (:role lift)]
    (if-let [builder (get role->builder role)]
      (builder entry)
      (stub-implementation entry))))
