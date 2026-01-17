(ns futon5.exotic.functor
  "Functor helpers for mapping VISION to EXECUTION."
  (:require [futon5.exotic.category :as cat]))

(defrecord Functor [name source target object-map morphism-map])

(defn apply-functor
  "Apply functor mapping to an object or morphism id."
  [^Functor f value]
  (or (get (:object-map f) value)
      (get (:morphism-map f) value)))

(defn preserves-composition?
  "Check functor composition preservation over the target category."
  [^Functor f ^cat/Category source-cat ^cat/Category target-cat]
  (let [compose-table (:compose-table source-cat)]
    (every?
     true?
     (for [[[m1 m2] m12] compose-table
           :let [f-m1 (apply-functor f m1)
                 f-m2 (apply-functor f m2)
                 f-m12 (apply-functor f m12)
                 composed (when (and f-m1 f-m2)
                            (cat/compose target-cat f-m1 f-m2))]
           :when (and f-m1 f-m2 f-m12 composed)]
       (= f-m12 composed)))))
