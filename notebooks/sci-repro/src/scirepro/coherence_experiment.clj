(ns scirepro.coherence-experiment
  "Statistical MetaCA eye-check for fluctuation-field shifted coherence."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [futon5.mmca.predictive-information :as pi]
            [scirepro.predictive-information-experiment :as experiment]
            [scirepro.weighted-blend-experiment :as weighted]))

(def source-hole {:type :offset :d -2 :tau 2})

(def alphabets
  (array-map
   :bitplane :bitplane
   :coarse-8 [:coarse 8]
   :full-cell :full-cell))

(defn score-history [history]
  (into (array-map)
        (for [[label alphabet] alphabets
              :let [score (pi/score-history
                           history source-hole alphabet
                           {:k experiment/past-window
                            :burn-in experiment/burn-in
                            :estimate :coherence
                            :aggregate :mean})]]
          [label (:mean-score-corrected score)])))

(defn score-run [weight seed]
  (let [result (weighted/run-weight weight seed)
        history (experiment/rule-history->sigil-history (:genotype result))]
    {:weight weight
     :seed seed
     :scores (score-history history)}))

(defn metric-summary [values]
  {:n (count values)
   :mean (experiment/mean values)
   :ci95 (experiment/ci95 values)})

(defn summarize [runs]
  (into (array-map)
        (for [label (keys alphabets)]
          [label
           (into (sorted-map)
                 (for [weight weighted/weights
                       :let [values
                             (mapv #(get-in % [:scores label])
                                   (filter (fn [run] (= weight (:weight run)))
                                           runs))]]
                   [weight (metric-summary values)]))])))

(defn eye-check [summary]
  (into (array-map)
        (for [[label weights] summary
              :let [preserved (get weights 0.9)
                    dissolved (get weights 0.5)
                    preserved-low (- (:mean preserved) (:ci95 preserved))
                    dissolved-high (+ (:mean dissolved) (:ci95 dissolved))]]
          [label {:criterion :w0.9-lower-ci-above-w0.5-upper-ci
                  :w0.9-lower-ci preserved-low
                  :w0.5-upper-ci dissolved-high
                  :passes? (> preserved-low dissolved-high)}])))

(defn rotation-verdict [checks]
  (let [bitplane (get-in checks [:bitplane :passes?])
        coarse (get-in checks [:coarse-8 :passes?])
        full-cell (get-in checks [:full-cell :passes?])]
    (cond
      (and (not bitplane) (or coarse full-cell))
      :rotation-evidence

      (and bitplane coarse full-cell)
      :all-alphabets-preserve-eye-order

      (not-any? true? [bitplane coarse full-cell])
      :no-alphabet-preserves-eye-order

      :else
      :mixed-alphabet-evidence)))

(defn experiment-result []
  (let [runs (mapv (fn [[weight seed]] (score-run weight seed))
                   (for [weight weighted/weights
                         seed experiment/seeds]
                     [weight seed]))
        summary (summarize runs)
        checks (eye-check summary)]
    {:schema/version 1
     :experiment/id :coherence-metaca-alphabet-sweep
     :replay :statistical
     :occupant {:source-hole source-hole
                :estimate :coherence
                :aggregate :mean}
     :protocol {:weights weighted/weights
                :alphabets alphabets
                :coarse-bins 8
                :width experiment/width
                :steps experiment/steps
                :seeds experiment/seeds
                :past-window experiment/past-window
                :burn-in experiment/burn-in}
     :runs runs
     :summary summary
     :eye-check checks
     :rotation-verdict (rotation-verdict checks)}))

(defn -main [& _]
  (let [root (experiment/repo-root)
        path (io/file root "data/coherence-metaca-sweep.edn")
        result (experiment-result)]
    (.mkdirs (.getParentFile path))
    (spit path (str (pr-str result) "\n"))
    (prn (select-keys result [:summary :eye-check :rotation-verdict]))))
