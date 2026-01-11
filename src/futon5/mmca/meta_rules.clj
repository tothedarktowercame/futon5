(ns futon5.mmca.meta-rules
  "Sigil-mapped meta-rule templates for MMCA runs."
  (:require [clojure.string :as str]))

(def meta-rule-registry
  {"止" {:role "FreezeGenotype"
         :pattern "freeze-genotype"
         :meta-rule {:freeze-genotype true}}
   "门" {:role "LockKernel"
         :pattern "lock-kernel"
         :meta-rule {:lock-kernel true}}
   "凡" {:role "RandomGenotype"
         :pattern "random-genotype"
         :meta-rule {:genotype-mode :random}}
   "专" {:role "GlobalRuleGenotype"
         :pattern "global-rule-genotype"
         :meta-rule {:genotype-mode :global-rule}}
   "术" {:role "UseOperators"
         :pattern "use-operators"
         :meta-rule {:use-operators true}}
  "生" {:role "PhenotypeOn"
         :pattern "phenotype-on"
         :meta-rule {:phenotype? true}}
   "屯" {:role "GenotypeGate"
         :pattern "genotype-gate"
         :meta-rule {:genotype-gate true}}})

(defn sigil->meta-rule [sigil]
  (get-in meta-rule-registry [sigil :meta-rule]))

(defn apply-meta-rules
  "Merge meta-rule settings from sigils into a base config map."
  [base sigils]
  (reduce (fn [acc sigil]
            (if-let [rule (sigil->meta-rule sigil)]
              (merge acc rule)
              acc))
          base
          sigils))
