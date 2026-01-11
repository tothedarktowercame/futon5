(ns futon5.mmca.adapters
  "Sigil adapters that map meta-patterns onto MMCA run configs."
  (:require [futon5.mmca.meta-rules :as meta-rules]))

(def operator-adapters
  {"手" {:role "BlendHand"
         :pattern "blending-curation"
         :mmca {:pattern-sigils ["手"] :use-operators true}}
   "升" {:role "UpliftOperator"
         :pattern "uplift"
         :mmca {:pattern-sigils ["升"] :use-operators true}}
   "川" {:role "WhiteSpaceScout"
         :pattern "white-space-scout"
         :mmca {:pattern-sigils ["川"] :use-operators true}}
   "义" {:role "HungerPrecision"
         :pattern "hunger-precision"
         :mmca {:pattern-sigils ["义"] :use-operators true}}
   "面" {:role "Facet"
         :pattern "facet"
         :mmca {:pattern-sigils ["面"] :use-operators true}}
   "代" {:role "HyperAnt"
         :pattern "hyperant"
         :mmca {:pattern-sigils ["代"] :use-operators true}}
   "付" {:role "Accumulator"
         :pattern "accumulator"
         :mmca {:pattern-sigils ["付"] :use-operators true}}})

(defn sigil->adapter [sigil]
  (get-in operator-adapters [sigil :mmca]))

(defn- merge-config [base delta]
  (let [sigils (into [] (distinct (concat (:pattern-sigils base)
                                          (:pattern-sigils delta))))]
    (cond-> (merge base delta)
      (seq sigils) (assoc :pattern-sigils sigils))))

(defn apply-adapters
  "Apply operator adapters to a base config map."
  [base sigils]
  (reduce (fn [acc sigil]
            (if-let [delta (sigil->adapter sigil)]
              (merge-config acc delta)
              acc))
          base
          sigils))

(defn apply-sigils
  "Apply meta-rule and operator sigils to a base MMCA config."
  [base sigils]
  (-> base
      (meta-rules/apply-meta-rules sigils)
      (apply-adapters sigils)))
