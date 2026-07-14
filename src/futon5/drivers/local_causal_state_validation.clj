(ns futon5.drivers.local-causal-state-validation
  "ECA validation for local-causal-state domain/particle decomposition."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [futon5.drivers.predictive-information-validation :as validation]
            [futon5.mmca.causal-state-particles :as particles]
            [futon5.mmca.local-causal-states :as lcs]))

(def protocol
  {:past-depth 3
   :future-depth 2
   :alpha 0.01
   :min-support 20
   :training-range [64 192]
   :evaluation-range [192 256]
   :margin 3})

(defn score-run [rule seed]
  (let [grid (validation/evolve rule seed)
        reconstruction
        (lcs/reconstruct
         grid {:past-depth (:past-depth protocol)
               :future-depth (:future-depth protocol)
               :training-time-range (:training-range protocol)
               :alpha (:alpha protocol)
               :min-support (:min-support protocol)})
        decomposition
        (particles/decompose
         (:field reconstruction)
         {:training-range (:training-range protocol)
          :evaluation-range (:evaluation-range protocol)
          :margin (:margin protocol)})]
    {:rule rule
     :seed seed
     :score (:score decomposition)
     :domain-coverage (:domain-coverage decomposition)
     :particle-sparsity (:particle-sparsity decomposition)
     :particle-count (get-in decomposition [:particles :count])
     :particle-density (get-in decomposition [:particles :density])
     :causal-state-count (count (get-in reconstruction [:model :states]))
     :distinct-pasts (get-in reconstruction [:model :distinct-pasts])
     :unresolved-pasts (count (get-in reconstruction
                                      [:model :unresolved-pasts]))}))

(defn summarize-rule [rule]
  (let [runs (mapv #(score-run rule %) validation/seeds)
        values (mapv :score runs)]
    {:rule rule
     :n (count values)
     :mean (validation/mean values)
     :ci95 (validation/ci95 values)
     :runs runs}))

(defn validation-result []
  (let [rules (into {}
                    (for [[class rule-ids] validation/classes]
                      [class (mapv summarize-rule rule-ids)]))
        class-values (into {}
                           (for [[class results] rules]
                             [class (vec (mapcat (fn [result]
                                                   (map :score (:runs result)))
                                                 results))]))
        classes (into {}
                      (for [[class values] class-values]
                        [class {:n (count values)
                                :mean (validation/mean values)
                                :ci95 (validation/ci95 values)}]))
        complex-low (- (get-in classes [:complex :mean])
                       (get-in classes [:complex :ci95]))
        control-high (apply max
                            (for [class [:ordered :chaotic]]
                              (+ (get-in classes [class :mean])
                                 (get-in classes [class :ci95]))))
        rule-110 (first (filter #(= 110 (:rule %)) (:complex rules)))
        chaotic-high (apply max (map #(+ (:mean %) (:ci95 %))
                                     (:chaotic rules)))]
    {:schema/version 1
     :experiment/id :local-causal-state-particle-validation
     :replay :statistical
     :protocol (merge protocol
                      {:width validation/width
                       :steps validation/steps
                       :seeds validation/seeds
                       :clustering :chi-square-homogeneity-no-fixed-k})
     :rules rules
     :classes classes
     :acceptance {:property :SeparatesEoC
                  :complex-lower-ci complex-low
                  :control-upper-ci control-high
                  :passes? (> complex-low control-high)
                  :rule-110-bar :rule-110-lower-ci-above-every-chaotic-upper-ci
                  :rule-110-lower-ci (- (:mean rule-110) (:ci95 rule-110))
                  :chaotic-rule-upper-ci chaotic-high
                  :rule-110-passes? (> (- (:mean rule-110) (:ci95 rule-110))
                                       chaotic-high)}}))

(defn -main [& [out-path]]
  (let [path (or out-path "data/local-causal-state-eca-validation.edn")
        result (validation-result)
        file (io/file path)]
    (some-> file .getParentFile .mkdirs)
    (spit file (str (pr-str result) "\n"))
    (prn (select-keys result [:classes :acceptance]))))
