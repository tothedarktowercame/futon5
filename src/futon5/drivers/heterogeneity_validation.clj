(ns futon5.drivers.heterogeneity-validation
  "ECA trust anchor for the preregistered heterogeneity aggregate fill."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [futon5.drivers.distance-transfer-entropy-validation :as distance]
            [futon5.drivers.predictive-information-validation :as validation]
            [futon5.mmca.predictive-information :as pi]))

(def occupants
  {:offset-heterogeneity
   {:source-hole {:type :offset
                  :d (:d distance/chosen-offset)
                  :tau (:tau distance/chosen-offset)}
    :aggregate :heterogeneity}
   :ais-heterogeneity
   {:source-hole :self-past
    :aggregate :heterogeneity}})

(defn score-rule [occupant rule]
  (let [{:keys [source-hole aggregate]} occupant
        values
        (mapv (fn [seed]
                (:score-corrected
                 (pi/predictive-information
                  (validation/evolve rule seed)
                  source-hole
                  {:k validation/past-window
                   :burn-in validation/burn-in
                   :aggregate aggregate})))
              validation/seeds)]
    {:rule rule
     :n (count values)
     :mean (validation/mean values)
     :ci95 (validation/ci95 values)
     :seed-values values}))

(defn summarize-occupant [occupant]
  (let [rules (into {}
                    (for [[class rule-ids] validation/classes]
                      [class (mapv #(score-rule occupant %) rule-ids)]))
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
    {:occupant occupant
     :rules rules
     :classes classes
     :acceptance {:property :SeparatesEoC
                  :criterion :complex-lower-ci-above-both-control-upper-cis
                  :complex-lower-ci complex-low
                  :control-upper-ci control-high
                  :passes? (> complex-low control-high)}}))

(defn validation-result []
  {:schema/version 1
   :experiment/id :heterogeneity-aggregate-eca-validation
   :replay :statistical
   :aggregate {:fill :heterogeneity
               :definition :population-variance-of-per-source-corrected-information}
   :offset-selection distance/velocity-selection
   :protocol {:width validation/width
              :steps validation/steps
              :boundary :fixed-zero-excluded-from-offset-links
              :destination-past-window validation/past-window
              :burn-in validation/burn-in
              :seeds validation/seeds}
   :occupants (into {}
                    (for [[name occupant] occupants]
                      [name (summarize-occupant occupant)]))})

(defn -main [& [out-path]]
  (let [path (or out-path "data/heterogeneity-eca-validation.edn")
        result (validation-result)
        file (io/file path)]
    (some-> file .getParentFile .mkdirs)
    (spit file (str (pr-str result) "\n"))
    (prn result)))
