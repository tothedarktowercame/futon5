(ns futon5.drivers.transfer-entropy-validation
  "Untuned ECA trust-anchor validation for nearest-neighbor transfer entropy."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [futon5.drivers.predictive-information-validation :as validation]
            [futon5.mmca.predictive-information :as pi]))

(defn score-rule [rule]
  (let [values
        (mapv (fn [seed]
                (:te-corrected
                 (pi/transfer-entropy
                  (validation/evolve rule seed)
                  {:k validation/past-window :burn-in validation/burn-in})))
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
     :experiment/id :transfer-entropy-eca-validation
     :measure {:name :nearest-neighbor-transfer-entropy
               :estimator :miller-madow
               :destination-past-window validation/past-window
               :source-past-window 1
               :burn-in validation/burn-in}
     :protocol {:width validation/width
                :steps validation/steps
                :boundary :fixed-zero-excluded-from-links
                :seeds validation/seeds}
     :rules rules
     :classes classes
     :acceptance {:criterion :complex-lower-ci-above-both-control-upper-cis
                  :complex-lower-ci complex-low
                  :control-upper-ci control-high
                  :passes? (> complex-low control-high)}}))

(defn -main [& [out-path]]
  (let [path (or out-path "data/transfer-entropy-eca-validation.edn")
        result (validation-result)
        file (io/file path)]
    (some-> file .getParentFile .mkdirs)
    (spit file (str (pr-str result) "\n"))
    (prn result)))
