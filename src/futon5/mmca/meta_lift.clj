(ns futon5.mmca.meta-lift
  "Lift sigil neighborhoods into a meta-sigil string via bitplane rules."
  (:require [futon5.ca.core :as ca]))

(def ^:private default-meta-rule "手")

(defn- sigil-bits [sigil]
  (ca/bits-for (ca/safe-sigil sigil)))

(defn- apply-meta-rule [meta-rule a b c]
  (let [rule (ca/local-rule-table meta-rule)
        triple (str a b c)]
    (str (rule triple))))

(defn lift-neighborhood
  "Lift three neighboring sigils into a new sigil using a meta-rule."
  ([left self right] (lift-neighborhood default-meta-rule left self right))
  ([meta-rule left self right]
   (let [lb (sigil-bits left)
         sb (sigil-bits self)
         rb (sigil-bits right)
         out (apply str (map (fn [a b c]
                               (apply-meta-rule meta-rule a b c))
                             lb sb rb))]
     (ca/sigil-for out))))

(defn lift-sigil-string
  "Lift a genotype string into a meta-sigil string.

  Uses the meta-rule (default \"手\") on each bitplane of the neighborhood."
  ([genotype] (lift-sigil-string genotype default-meta-rule))
  ([genotype meta-rule]
   (let [letters (vec (map str genotype))
         len (count letters)
         left-sigil ca/default-sigil]
     (cond
       (zero? len) ""
       (= 1 len) (lift-neighborhood meta-rule left-sigil (letters 0) left-sigil)
       :else
       (let [head (lift-neighborhood meta-rule left-sigil (letters 0) (letters 1))
             tail (lift-neighborhood meta-rule (letters (- len 2)) (letters (dec len)) left-sigil)
             mids (map (fn [idx]
                         (lift-neighborhood meta-rule
                                            (letters (dec idx))
                                            (letters idx)
                                            (letters (inc idx))))
                       (range 1 (dec len)))]
         (apply str (concat [head] mids [tail])))))))

(defn lift-history
  "Lift a genotype history and return lifted strings with sigil counts."
  ([gen-history] (lift-history gen-history default-meta-rule))
  ([gen-history meta-rule]
   (let [lifted (map #(lift-sigil-string % meta-rule) gen-history)
         counts (frequencies (apply concat lifted))]
     {:lifted-history lifted
      :sigil-counts counts})))
