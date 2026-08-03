(ns damage-probe-eig-off
  "Micro-run: does removing EIG raise or lower damage spreading?
   Two seeds. :next-C is the identical objective with the EIG term off."
  (:require [futon5.exotype.slice-harness :as harness]
            [clojure.pprint :as pp]))

(def base
  {:width 80 :steps 6000 :tau 0.3 :prevalence-radius 1
   :damage-steps 59 :include-genotype-spatial? true
   :eig-model :beta-posterior
   :checkpoints [0 600 3000 6000]})

(def seeds [20260803 20260804])

(defn probe [mu lambda arm coefficient]
  (let [cfg (assoc base :mu mu :lambda lambda)
        runs (for [s seeds]
               (harness/seed-run cfg arm s
                                 (cond-> {:eig-model :beta-posterior}
                                   coefficient (assoc :eig-coefficient (double coefficient)))))
        dmg (fn [k] (/ (reduce + (map #(get-in % [:damage k]) runs)) (double (count runs))))]
    {:mu mu :lambda lambda :arm arm :c coefficient
     :P (dmg :phenotype) :G (dmg :genotype) :X (dmg :exotype)}))

(defn -main [& _]
  (println "\nDAMAGE PROBE — EIG off vs on, 2 seeds, 6000 steps, width 80\n")
  (let [rows (doall
              (for [mu [0.1 0.3]
                    [arm c] [[:next-C nil] [:next-C-plus-eig 3.0] [:next-C-plus-eig 5.0]]]
                (probe mu 0.55 arm c)))]
    (printf "%6s %22s %8s %9s %9s %9s%n" "mu" "arm" "c" "P dmg" "G dmg" "X dmg")
    (println (apply str (repeat 68 "-")))
    (doseq [r rows]
      (printf "%6.2f %22s %8s %9.3f %9.3f %9.3f%n"
              (:mu r) (name (:arm r)) (str (:c r)) (:P r) (:G r) (:X r)))
    (spit "reports/damage-probe-eig-off.edn" (with-out-str (pp/pprint rows)))
    (println "\nwrote reports/damage-probe-eig-off.edn")))
