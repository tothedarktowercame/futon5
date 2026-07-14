(ns futon5.drivers.distance-transfer-entropy-validation
  "Preregistered ECA trust anchor for the distance/lagged TE occupant."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [futon5.drivers.predictive-information-validation :as validation]
            [futon5.mmca.predictive-information :as pi]))

;; Fixed before inspecting ECA scores. A full-cell diagonal-MI scan of the
;; seed-42 mutating-template grid found its dominant ridge at d=-1,tau=1.
;; d=-2,tau=2 is the first non-nearest point on that same v=-1 ridge, keeping
;; this occupant distinct from nearest-neighbor TE.
(def chosen-offset {:d -2 :tau 2 :velocity -1.0})

(def velocity-selection
  {:source :mutating-template-seed-42
   :method :full-cell-diagonal-mutual-information
   :scan {:d [-8 -7 -6 -5 -4 -3 -2 -1 1 2 3 4 5 6 7 8]
          :tau [1 2 3 4 5 6 7 8]}
   :dominant-peak {:d -1 :tau 1 :mutual-information-bits 1.2424426411686431}
   :chosen chosen-offset
   :rationale :first-non-nearest-offset-on-dominant-velocity-ridge
   :chosen-before-eca-scoring true})

(defn score-rule [rule]
  (let [{:keys [d tau]} chosen-offset
        values
        (mapv (fn [seed]
                (:te-corrected
                 (pi/distance-transfer-entropy
                  (validation/evolve rule seed) d tau
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
     :experiment/id :distance-transfer-entropy-eca-validation
     :replay :statistical
     :velocity-selection velocity-selection
     :measure {:name :distance-lagged-transfer-entropy
               :source-hole (select-keys chosen-offset [:d :tau])
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
     :acceptance {:property :SeparatesEoC
                  :criterion :complex-lower-ci-above-both-control-upper-cis
                  :complex-lower-ci complex-low
                  :control-upper-ci control-high
                  :passes? (> complex-low control-high)}}))

(defn -main [& [out-path]]
  (let [path (or out-path "data/distance-transfer-entropy-eca-validation.edn")
        result (validation-result)
        file (io/file path)]
    (some-> file .getParentFile .mkdirs)
    (spit file (str (pr-str result) "\n"))
    (prn result)))
