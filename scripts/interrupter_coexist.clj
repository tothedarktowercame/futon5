(ns interrupter-coexist
  "Coexistence composition + temporal kernel diversity re-runs, for
   TN-interrupter-fable-answer.md (2.x). Re-runs a subset of the v2 cells with
   richer recording (the artifacts store only the dominant kind).

   Per (p, arm, seed): 600 steps of self_tuning/step from the exact v2 initial
   state. Records:
     - final kind histogram; #kinds with share >= 0.05 (mean over last 100 steps)
     - wall density (fraction of adjacent differing-kind pairs), last-100 mean
     - period-2 oscillation rate: kind(t) = kind(t-2) != kind(t-1), last-100 mean
     - per-cell temporal distinct-kind count over the whole run (1.4 measure)
     - reproduction check vs the artifact for (p=0.6, epistemic, seed 2026084300)

   Run: clojure -Sdeps '{:paths [\"src\" \"resources\"]}' -M scripts/interrupter_coexist.clj"
  (:require [clojure.pprint :as pprint]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def width 80)
(def steps 600)
(def lambda 0.55)
(def kappa 0.47821902791182086)
(def bonuses {0.3 0.15, 0.6 0.8, 1.0 0.1})
(def seeds (vec (range 2026084300 2026084308)))
(def absorbing-kinds #{:collapser :even1 :even4 :even8})

(defn initial-state [seed]
  (ca/with-seed seed
    (let [genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full :seed seed :time 0
       :self-tuning-arm :hunger-coupled
       :lambda-step-size 0.0
       :hunger-target (:hunger efe/preferences)
       :lambdas (vec (repeat width lambda))
       :genotype genotype :previous-genotype genotype
       :phenotype (apply str (repeatedly width #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed width)})))

(defn arm-options [arm p]
  (case arm
    :off {:epistemic-coefficient 0.0 :adoption-bonus 0.0}
    :epistemic {:epistemic-coefficient kappa :adoption-bonus 0.0}
    :matched-churn {:epistemic-coefficient 0.0 :adoption-bonus (get bonuses p)}))

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))

(defn- cycle-shift [v] (conj (vec (rest v)) (first v)))

(defn- wall-density [exos]
  (/ (count (filter true? (map not= (vec exos) (cycle-shift (vec exos)))))
     (double (count exos))))

(defn run-one [seed p arm]
  (loop [state (merge (initial-state seed) {:apply-probability p} (arm-options arm p))
         t 0
         adopted 0
         selected-x 0.0
         seen (vec (repeat width #{}))       ; per-cell kinds seen
         prev2 nil prev1 nil
         acc {:kinds [] :wall [] :osc []}]
    (if (= t steps)
      (let [counts (frequencies (:exotypes state))
            dominant (key (apply max-key val counts))]
        {:seed seed :p p :arm arm
         :adoption-rate (/ adopted (double (* steps width)))
         :selected-x (/ selected-x (double (* steps width)))
         :dominant dominant
         :dominant-share (/ (double (get counts dominant)) width)
         :final-counts (into (sorted-map) counts)
         :halting-share (/ (reduce + 0 (for [[k n] counts :when (absorbing-kinds k)] n))
                           (double width))
         :kinds>=5% (mean (:kinds acc))
         :wall-density (mean (:wall acc))
         :osc-rate (mean (:osc acc))
         :mean-kinds-seen-per-cell (mean (map count seen))})
      (let [next (tuning/step state)
            winners (map :winner (:self-tuning-decisions next))
            exos (:exotypes next)
            adoptions (count (filter true?
                                     (map-indexed
                                      (fn [i w] (not= (nth (:exotypes state) i)
                                                      (:candidate-exotype w)))
                                      winners)))
            last100? (>= t (- steps 100))
            counts (frequencies exos)
            acc' (if last100?
                   {:kinds (conj (:kinds acc)
                                 (count (filter #(>= (val %) (* 0.05 width)) counts)))
                    :wall (conj (:wall acc) (wall-density exos))
                    :osc (conj (:osc acc)
                               (if prev2
                                 (/ (count (filter true?
                                                   (map (fn [a b c] (and (= a c) (not= a b)))
                                                        prev2 prev1 exos)))
                                    (double width))
                                 0.0))}
                   acc)]
        (recur next (inc t)
               (+ adopted adoptions)
               (+ selected-x (reduce + 0.0 (map #(double (get % :epistemic-value 0.0)) winners)))
               (mapv conj seen exos)
               prev1 exos
               acc')))))

(defn -main []
  (let [rows (vec (for [p [0.3 0.6 1.0]
                        arm [:off :epistemic :matched-churn]
                        seed seeds]
                    (do (binding [*out* *err*] (println "run" p arm seed))
                        (run-one seed p arm))))]
    (spit "analysis/interrupter-coexist-rows.edn"
          (with-out-str (pprint/pprint rows)))
    ;; summary
    (doseq [[[p arm] group] (sort-by key (group-by (juxt :p :arm) rows))]
      (println (format "p=%.2f %-14s kinds>=5%% %.2f  wall %.3f  osc %.3f  kinds-seen/cell %.1f  dom-share %.3f  halting %.3f"
                       p (name arm)
                       (mean (map :kinds>=5% group))
                       (mean (map :wall-density group))
                       (mean (map :osc-rate group))
                       (mean (map :mean-kinds-seen-per-cell group))
                       (mean (map :dominant-share group))
                       (mean (map :halting-share group))))
      (println "   pooled final counts:"
               (into (sorted-map)
                     (apply merge-with + (map :final-counts group)))))
    ;; reproduction check
    (let [r (first (filter #(and (= (:seed %) 2026084300) (= (:p %) 0.6)
                                 (= (:arm %) :epistemic)) rows))]
      (println "\nreproduction check (p=0.6 epistemic seed 2026084300):")
      (println "  " (select-keys r [:adoption-rate :selected-x :dominant :dominant-share])))))

(-main)
