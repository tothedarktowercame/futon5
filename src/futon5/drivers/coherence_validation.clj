(ns futon5.drivers.coherence-validation
  "ECA trust anchor for shifted fluctuation-field coherence."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [futon5.drivers.distance-transfer-entropy-validation :as distance]
            [futon5.drivers.predictive-information-validation :as validation]
            [futon5.mmca.predictive-information :as pi]))

(def occupant
  {:source-hole {:type :offset
                 :d (:d distance/chosen-offset)
                 :tau (:tau distance/chosen-offset)}
   :estimate :coherence
   :aggregate :mean})

(defn score-rule [rule]
  (let [{:keys [source-hole estimate aggregate]} occupant
        values
        (mapv (fn [seed]
                (:score-corrected
                 (pi/predictive-information
                  (validation/evolve rule seed)
                  source-hole
                  {:k validation/past-window
                   :burn-in validation/burn-in
                   :estimate estimate
                   :aggregate aggregate})))
              validation/seeds)]
    {:rule rule
     :n (count values)
     :mean (validation/mean values)
     :ci95 (validation/ci95 values)
     :seed-values values}))

(defn validation-result []
  (let [rules (into {}
                    (for [[class rule-ids] validation/classes]
                      [class (mapv score-rule rule-ids)]))
        class-values (into {}
                           (for [[class results] rules]
                             [class (vec (mapcat :seed-values results))]))
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
                                 (get-in classes [class :ci95]))))]
    {:schema/version 1
     :experiment/id :coherence-eca-validation
     :replay :statistical
     :occupant occupant
     :offset-selection distance/velocity-selection
     :estimate {:fill :coherence
                :field :binary-change
                :similarity :positive-pearson
                :domain-subtraction :mean-centered
                :constant-field-score 0.0}
     :protocol {:width validation/width
                :steps validation/steps
                :boundary :fixed-zero-excluded-from-links
                :destination-past-window validation/past-window
                :burn-in validation/burn-in
                :seeds validation/seeds}
     :rules rules
     :classes classes
     :acceptance {:property :SeparatesEoC
                  :criterion :complex-lower-ci-above-both-control-upper-cis
                  :complex-lower-ci complex-low
                  :control-upper-ci control-high
                  :passes? (> complex-low control-high)}}))

(defn -main [& [out-path]]
  (let [path (or out-path "data/coherence-eca-validation.edn")
        result (validation-result)
        file (io/file path)]
    (some-> file .getParentFile .mkdirs)
    (spit file (str (pr-str result) "\n"))
    (prn result)))
