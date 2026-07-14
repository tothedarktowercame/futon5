(ns futon5.drivers.predictive-information-validation
  "Untuned ECA trust-anchor validation for active information storage."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [futon5.mmca.predictive-information :as pi]))

(def classes
  {:ordered [0 8 128]
   :chaotic [30 45 90]
   :complex [110 54 137]})

(def width 256)
(def steps 512)
(def burn-in 128)
(def past-window 8)
(def seeds (vec (range 42 62)))

(defn wolfram-next-bit [rule left center right]
  (let [neighborhood (+ (* 4 left) (* 2 center) right)]
    (bit-and 1 (bit-shift-right rule neighborhood))))

(defn eca-step [rule row]
  (mapv (fn [x]
          (let [left (if (zero? x) 0 (nth row (dec x)))
                center (nth row x)
                right (if (= x (dec (count row))) 0 (nth row (inc x)))]
            (wolfram-next-bit rule left center right)))
        (range (count row))))

(defn seeded-binary-row [seed]
  (let [rng (java.util.Random. (long seed))]
    (mapv (fn [_] (.nextInt rng 2)) (range width))))

(defn evolve [rule seed]
  (vec (take (inc steps)
             (iterate #(eca-step rule %) (seeded-binary-row seed)))))

(defn mean [xs]
  (/ (reduce + 0.0 xs) (double (count xs))))

(defn ci95 [xs]
  (let [xs (vec xs)
        n (count xs)
        m (mean xs)
        variance (/ (reduce + 0.0
                            (map (fn [x]
                                   (let [d (- (double x) m)] (* d d)))
                                 xs))
                    (double (dec n)))]
    (* 1.96 (Math/sqrt (/ variance n)))))

(defn score-rule [rule]
  (let [values (mapv (fn [seed]
                       (:ais-corrected
                        (pi/active-information-storage
                         (evolve rule seed)
                         {:k past-window :burn-in burn-in})))
                     seeds)]
    {:rule rule :n (count values) :mean (mean values) :ci95 (ci95 values)
     :seed-values values}))

(defn validation-result []
  (let [rules (into {}
                    (for [[class rule-ids] classes]
                      [class (mapv score-rule rule-ids)]))
        class-values (into {}
                           (for [[class results] rules]
                             [class (vec (mapcat :seed-values results))]))
        class-summary (into {}
                            (for [[class values] class-values]
                              [class {:n (count values)
                                      :mean (mean values)
                                      :ci95 (ci95 values)}]))
        complex-low (- (get-in class-summary [:complex :mean])
                       (get-in class-summary [:complex :ci95]))
        control-high (apply max
                            (for [class [:ordered :chaotic]]
                              (+ (get-in class-summary [class :mean])
                                 (get-in class-summary [class :ci95]))))]
    {:schema/version 1
     :experiment/id :predictive-information-eca-validation
     :measure {:name :active-information-storage
               :estimator :miller-madow
               :past-window past-window
               :burn-in burn-in}
     :protocol {:width width :steps steps :boundary :fixed-zero :seeds seeds}
     :rules rules
     :classes class-summary
     :acceptance {:criterion :complex-lower-ci-above-both-control-upper-cis
                  :complex-lower-ci complex-low
                  :control-upper-ci control-high
                  :passes? (> complex-low control-high)}}))

(defn -main [& [out-path]]
  (let [path (or out-path "data/predictive-information-eca-validation.edn")
        result (validation-result)
        file (io/file path)]
    (some-> file .getParentFile .mkdirs)
    (spit file (str (pr-str result) "\n"))
    (prn result)))
