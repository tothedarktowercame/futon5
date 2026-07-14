(ns scirepro.transfer-entropy-experiment
  "TE re-score of the three committed AIS dynamics, using matched runs."
  (:gen-class)
  (:require [clojure.java.io :as io]
            [futon5.mmca.predictive-information :as pi]
            [scirepro.predictive-information-experiment :as experiment]))

(defn score-run [dynamic seed]
  (let [result (experiment/run-dynamic dynamic seed)
        score (pi/score-metaca-transfer-entropy
               (experiment/rule-history->sigil-history (:genotype result))
               {:k experiment/past-window :burn-in experiment/burn-in})]
    {:dynamic dynamic
     :seed seed
     :mean-te-corrected (:mean-te-corrected score)
     :max-te-corrected (:max-te-corrected score)
     :per-plane (mapv #(select-keys % [:plane :te-corrected :te-plugin
                                      :samples-per-link :directed-links])
                      (:per-plane score))}))

(defn summarize [runs]
  (into {}
        (for [dynamic experiment/dynamics
              :let [values (mapv :mean-te-corrected
                                 (filter #(= dynamic (:dynamic %)) runs))]]
          [dynamic {:n (count values)
                    :mean (experiment/mean values)
                    :ci95 (experiment/ci95 values)}])))

(defn experiment-result []
  (let [runs (mapv (fn [[dynamic seed]] (score-run dynamic seed))
                   (for [dynamic experiment/dynamics
                         seed experiment/seeds]
                     [dynamic seed]))
        summary (summarize runs)]
    {:schema/version 1
     :experiment/id :transfer-entropy-parent-blend
     :replay :statistical
     :protocol {:width experiment/width
                :steps experiment/steps
                :seeds experiment/seeds
                :bitplanes 8
                :destination-past-window experiment/past-window
                :source-past-window 1
                :burn-in experiment/burn-in
                :initial-condition :matched-per-seed}
     :runs runs
     :summary summary
     :eye-check {:criterion :mutating-template-mean-above-blend-mean
                 :passes? (> (get-in summary [:mutating-template :mean])
                             (get-in summary [:blend-template+baldwin-mutate :mean]))}}))

(defn -main [& _]
  (let [root (experiment/repo-root)
        path (io/file root "data/transfer-entropy-dynamics.edn")
        result (experiment-result)]
    (.mkdirs (.getParentFile path))
    (spit path (str (pr-str result) "\n"))
    (prn (select-keys result [:summary :eye-check]))))
