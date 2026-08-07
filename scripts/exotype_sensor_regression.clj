(ns exotype-sensor-regression
  "Is the loop closable from INSIDE? One collection, two analyses.

   Joe, 2026-08-05: `maybe not just ONE of the terms ... but some combination of
   them is a better knob`. Right, and it makes the screen a regression rather
   than five correlations.

   Damage reach is measured by forking the run and counting divergences against
   a perturbed twin. The construction cannot compute that about itself at
   runtime, so a controller on gamma cannot use it. The question is whether some
   combination of quantities the engine ALREADY computes tracks it.

   *** THE TRAP, and the reason for the second analysis. ***
   Fitted across a gamma sweep, a regressor may track damage only because it
   tracks GAMMA. Decision entropy in particular is close to a deterministic
   function of gamma. A controller reading such a sensor is reading its own knob
   back -- circular, and it senses nothing about the world. So:

     ANALYSIS 1 (between-gamma): does the combination track damage across the
       sweep? R^2 here is necessary but NOT sufficient -- it is inflated by
       whatever the regressors share with gamma.
     ANALYSIS 2 (within-gamma): at FIXED gamma, across seeds, does any
       observable explain seed-to-seed variation in damage? This is the part
       that cannot be explained by the knob, because the knob is held constant.
       ONLY analysis 2 shows the sensor carries state information.

   A sensor passing 1 but failing 2 is a re-parameterisation of gamma and is
   useless for control. That distinction is the whole point of this script.

   usage: clojure -M scripts/exotype_sensor_regression.clj [out.edn]"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def W 250)
(def T 100)
(def GAMMAS [8.0 10.0 12.0 13.0 14.0 15.0 16.0 18.0 20.0 24.0])
(def SEEDS (range 2026087000 2026087016))
(def absorbing #{:collapser :even1 :even4 :even8})

(defn- state [gamma seed]
  (ca/with-seed seed
    (let [g (vec (ca/random-sigil-string W))]
      {:arm :efe-full :seed seed :time 0 :hunger-target (:hunger efe/preferences)
       :lambdas (vec (repeat W 0.55)) :genotype g :previous-genotype g
       :phenotype (apply str (repeatedly W #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed W)
       :blend-action? true :blend-strength 0.0 :apply-probability 1.0
       :epistemic-coefficient 0.2 :self-tuning-arm :hunger-coupled
       :lambda-step-size 0.0 :policy-precision gamma})))

(defn- internals
  "Everything the engine can see about itself at runtime. No twin, no fork."
  [s prev]
  (let [decs (or (:efe-decisions s) [])
        wins (frequencies (map #(get-in % [:winner :policy]) decs))
        ent (let [ps (keep :policy-probabilities decs)]
              (if (seq ps)
                (/ (reduce + (for [p ps]
                               (- (reduce + 0.0 (for [q (keep :probability p)
                                                      :when (> q 1e-12)]
                                                  (* q (Math/log q)))))))
                   (double (count ps)))
                0.0))]
    {:entropy ent
     :blend (/ (double (get wins :blend 0)) W)
     :adoption (/ (double (+ (get wins :adopt-left 0) (get wins :adopt-right 0))) W)
     :change-rate (if prev
                    (/ (double (count (filter true? (map not= (:phenotype s) prev)))) W)
                    0.0)
     :kinds (double (count (frequencies (:exotypes s))))
     :halting (/ (double (count (filter absorbing (:exotypes s)))) W)}))

(defn- one-run [gamma seed]
  (let [a0 (state gamma seed)
        b0 (assoc a0 :phenotype (apply str (update (vec (:phenotype a0)) (quot W 2)
                                                   #(if (= % \0) \1 \0))))]
    (loop [a a0 b b0 prev nil t 0 acc []]
      (if (= t T)
        {:gamma gamma :seed seed
         :damage (double (count (filter true? (map not= (:phenotype a) (:phenotype b)))))
         ;; internals averaged over the second half, after the transient
         :internals (let [half (drop (quot (count acc) 2) acc)]
                      (into {} (for [k (keys (first half))]
                                 [k (/ (reduce + (map k half)) (double (count half)))])))}
        (let [a' (tuning/step a) b' (tuning/step b)]
          (recur a' b' (:phenotype a) (inc t) (conj acc (internals a' (:phenotype a)))))))))

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))
(defn- corr [xs ys]
  (let [mx (mean xs) my (mean ys)
        num (reduce + (map #(* (- %1 mx) (- %2 my)) xs ys))
        dx (Math/sqrt (reduce + (map #(let [d (- % mx)] (* d d)) xs)))
        dy (Math/sqrt (reduce + (map #(let [d (- % my)] (* d d)) ys)))]
    (if (and (pos? dx) (pos? dy)) (/ num (* dx dy)) 0.0)))

(defn -main [& [out]]
  (let [runs (doall (for [g GAMMAS s SEEDS] (one-run g s)))
        keys' [:entropy :blend :adoption :change-rate :kinds :halting]]
    (println (format "  SENSOR SCREEN -- %d gammas x %d seeds, width %d, t=%d\n"
                     (count GAMMAS) (count SEEDS) W T))
    (println "  ANALYSIS 1 (between-gamma): correlation with damage ACROSS the sweep")
    (println "    -- inflated by whatever each regressor shares with gamma; necessary, not sufficient\n")
    (let [ds (map :damage runs)]
      (println (format "    %-14s %8s   %s" "observable" "r" "r with gamma itself"))
      (doseq [k keys']
        (println (format "    %-14s %+8.4f   %+.4f" (name k)
                         (corr (map #(get-in % [:internals k]) runs) ds)
                         (corr (map #(get-in % [:internals k]) runs) (map :gamma runs))))))
    (println "\n  ANALYSIS 2 (within-gamma): at FIXED gamma, across seeds -- the knob is constant,")
    (println "    so any correlation here is genuine STATE information the controller could use\n")
    (println (format "    %-8s %s" "gamma" (apply str (map #(format "%12s" (name %)) keys'))))
    (doseq [g GAMMAS]
      (let [rs (filter #(= g (:gamma %)) runs)
            ds (map :damage rs)]
        (println (format "    %-8.1f %s" g
                         (apply str (for [k keys']
                                      (format "%12s"
                                              (format "%+.3f" (corr (map #(get-in % [:internals k]) rs) ds)))))))))
    (println "\n    mean |r| within-gamma, per observable:")
    (doseq [k keys']
      (let [rs (for [g GAMMAS]
                 (let [sub (filter #(= g (:gamma %)) runs)]
                   (Math/abs (corr (map #(get-in % [:internals k]) sub) (map :damage sub)))))]
        (println (format "      %-14s %.3f  %s" (name k) (mean rs)
                         (if (> (mean rs) 0.4) "<-- carries state information" "")))))
    (when out (spit out (pr-str runs)))))

(apply -main *command-line-args*)
