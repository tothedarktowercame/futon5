(ns exotype-gamma-damage
  "Does the R14 softmax move the system OFF chaos?

   Observed by accident while building the purple map: at gamma=4 a one-bit
   perturbation HEALS to zero by t=15. The same configuration before R14 --
   hard argmin -- measured damage reach 63.5-69.5 at this width, i.e. AT OR
   ABOVE the chaotic anchor (rule 30 = 60.9). If that difference is the
   selection rule, then R14 did not merely fix the authority problem; it moved
   the regime.

   Anchors re-measured in this harness at THIS width (see TN 79):
     rule 204 = 1.0 | rule 90 = 8.0 | rule 54 = 36.0 | rule 110 = 38.1 | rule 30 = 60.9
   Class-IV band is 36-38. Chaos is >= 61.

   usage: clojure -M scripts/exotype_gamma_damage.clj"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def W 250)
(def T 100)
(def SEEDS (if (System/getenv "FINE") (range 2026086000 2026086024) (range 2026085500 2026085512)))

(defn- state [gamma seed]
  (ca/with-seed seed
    (let [g (vec (ca/random-sigil-string W))]
      (cond-> {:arm :efe-full :seed seed :time 0 :hunger-target (:hunger efe/preferences)
               :lambdas (vec (repeat W 0.55)) :genotype g :previous-genotype g
               :phenotype (apply str (repeatedly W #(if (< (ca/rnd) 0.5) \0 \1)))
               :exotypes (grid/initial-grid :heterogeneous-fixed W)
               :blend-action? true :blend-strength 0.0 :apply-probability 1.0
               :epistemic-coefficient 0.2 :self-tuning-arm :hunger-coupled
               :lambda-step-size 0.0}
        gamma (assoc :policy-precision gamma)))))

(defn- damage [gamma seed]
  (let [a0 (state gamma seed)
        b0 (assoc a0 :phenotype (apply str (update (vec (:phenotype a0)) (quot W 2)
                                                   #(if (= % \0) \1 \0))))]
    (loop [a a0 b b0 t 0]
      (if (= t T) (count (filter true? (map not= (:phenotype a) (:phenotype b))))
          (recur (tuning/step a) (tuning/step b) (inc t))))))

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))
(defn- se [xs] (let [m (mean xs)]
                 (/ (Math/sqrt (mean (map #(let [d (- % m)] (* d d)) xs))) (Math/sqrt (count xs)))))

(defn -main [& _]
  (println (format "  DAMAGE REACH at t=%d, width %d, %d seeds\n" T W (count SEEDS)))
  (println "  anchors (re-measured this width): 204=1.0  90=8.0  54=36.0  110=38.1  30=60.9")
  (println "  class-IV band = 36-38 | chaos >= 61\n")
  (doseq [g (if (System/getenv "FINE") [10.0 12.0 14.0 16.0 18.0 20.0 24.0 32.0] [nil 1.0 2.0 4.0 8.0 16.0 64.0])]
    (let [d (map #(damage g %) SEEDS)
          m (mean d)]
      (println (format "  gamma %-8s damage %6.2f +- %5.2f   %s"
                       (if g (format "%.0f" g) "ARGMIN") m (se d)
                       (cond (>= m 61) "CHAOTIC"
                             (<= 30 m 45) "*** CLASS-IV BAND ***"
                             (< m 10) "ordered/frozen"
                             :else "intermediate"))))))

(apply -main *command-line-args*)
