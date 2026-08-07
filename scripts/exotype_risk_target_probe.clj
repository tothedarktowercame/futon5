(ns exotype-risk-target-probe
  "BUILD PACKET E0 — risk-target probe.

  The shipped TARGET for the risk term (bernoulli-kl(rate, TARGET)) is 0.15, but
  every propagator has rule-change rate >= 0.5 (rate = 0.5 + fix(sigma)/16). So
  the target is below the mechanism's floor. This script measures, for each of
  several targets, how the argmin distribution over all 12 kinds changes, and
  how the spreads of risk vs ambiguity compare — to test whether the risk term
  is a monotone penalty on fix(sigma) that pre-selects halting-capable policies.

  Uses score-policy's existing :rule-change-preference diagnostic override.
  No source changes."
  (:require [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]))

(def targets [0.15 0.50 0.5625 0.625 0.75 1.0])

(def observation-domain
  "The 9 reachable observations: activity in {0, 1/3, 2/3}, diversity in {1/3, 2/3, 1}."
  (for [a [0.0 (/ 1.0 3) (/ 2.0 3)]
        d [(/ 1.0 3) (/ 2.0 3) 1.0]]
    {:activity a :diversity d}))

(defn- argmin-kind [target obs]
  (:candidate-exotype
   (apply min-key :total
          (map #(efe/score-policy :efe-full % obs
                                  {:rule-change-preference target})
               (keys grid/propagators)))))

(defn- argmin-distribution [target]
  (into (sorted-map)
        (frequencies
         (for [o observation-domain]
           (argmin-kind target o)))))

(defn- term-spread
  "Across the 12 kinds at one observation, collect the term values.
  Returns the min, max, and max-min across ALL 12 kinds x ALL 9 observations,
  so the overall spread of each term is directly comparable."
  [target term-key]
  (let [vals (for [k (keys grid/propagators)
                   o observation-domain]
               (term-key (efe/score-policy :efe-full k o
                                           {:rule-change-preference target})))
        mn (apply min vals)
        mx (apply max vals)]
    {:min mn :max mx :spread (- mx mn)}))

(defn -main [& _]
  ;; (a) argmin distribution per target
  (println "=== (a) ARGMIN DISTRIBUTION over 12 kinds, 9 observations ===")
  (println)
  (doseq [t targets]
    (println (format "target %.4f -> %s" t (argmin-distribution t))))
  (println)

  ;; (b) risk and ambiguity spread per target
  (println "=== (b) RISK vs AMBIGUITY spread across 12 kinds x 9 observations ===")
  (println)
  (println "| target | risk-min | risk-max | risk-spread | ambig-min | ambig-max | ambig-spread |")
  (println "|---:|---:|---:|---:|---:|---:|---:|")
  (doseq [t targets]
    (let [r (term-spread t :risk)
          a (term-spread t :ambiguity)]
      (println (format "| %.4f | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f |"
                       t (:min r) (:max r) (:spread r)
                       (:min a) (:max a) (:spread a))))))

(apply -main *command-line-args*)
