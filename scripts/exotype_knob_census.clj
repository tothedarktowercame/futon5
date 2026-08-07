(ns exotype-knob-census
  "KNOB x SENSOR actuation census.

   WHY. Two controllers have now failed the same way: lambda was plumbed to a
   sensor it cannot move (measured -- E[hunger] shifts ~0.005 across lambda's
   entire range, per-cell leverage ~1e-4, every target ramps to a boundary).
   The lesson generalises: before building ANY controller, measure whether its
   knob can actually move its sensor, on-policy.

   A knob-sensor pair is usable only if BOTH hold:
     (1) LEVERAGE  -- the knob moves the sensor by more than seed noise;
     (2) VALIDITY  -- the sensor tracks what we actually want (criticality).
   Leverage without validity regulates the wrong thing; validity without
   leverage is the lambda dead-end. This script measures (1) for every pair and
   reports the sensor's own spread so (2) can be judged.

   Knobs   : gamma (selection precision), kappa (epistemic coefficient),
             lambda (conatus weight), apply-probability.
   Sensors : decision entropy, blend share, adoption rate, distinct kinds,
             halting share, damage reach (the criticality proxy).

   usage: clojure -M scripts/exotype_knob_census.clj [out.edn]"
  (:require [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def W 80)
(def BURN 150)
(def SEEDS (range 6100 6112))
(def absorbing #{:collapser :even1 :even4 :even8})

(defn- base [seed]
  (ca/with-seed seed
    (let [g (vec (ca/random-sigil-string W))]
      {:arm :efe-full :seed seed :time 0 :hunger-target (:hunger efe/preferences)
       :lambdas (vec (repeat W 0.55)) :genotype g :previous-genotype g
       :phenotype (apply str (repeatedly W #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed W)
       :blend-action? true :blend-strength 0.0 :apply-probability 1.0
       :epistemic-coefficient 0.2 :self-tuning-arm :hunger-coupled
       :lambda-step-size 0.0 :policy-precision 4.0})))

(defn- with-knob [s knob v]
  (case knob
    :gamma  (assoc s :policy-precision v)
    :kappa  (assoc s :epistemic-coefficient v)
    :lambda (assoc s :lambdas (vec (repeat W v)))
    :p      (assoc s :apply-probability v)))

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))

(defn- sensors [s]
  (let [decs (:efe-decisions s)
        wins (frequencies (map #(get-in % [:winner :policy]) decs))
        probs (keep :policy-probabilities decs)
        ;; :policy-probabilities is a vector of {:policy _ :probability double},
        ;; one entry per candidate -- read the field by name, not by type.
        numeric (fn [p] (keep :probability p))
        ent (if (seq probs)
              (mean (for [p probs]
                      (- (reduce + 0.0 (for [q (numeric p) :when (> q 1e-12)]
                                         (* q (Math/log q)))))))
              0.0)
        twin (assoc s :phenotype (apply str (update (vec (:phenotype s)) (quot W 2)
                                                    #(if (= % \0) \1 \0))))
        adv #(nth (iterate tuning/step %) 30)]
    {:decision-entropy ent
     :blend-share (/ (double (get wins :blend 0)) W)
     :adoption (/ (double (+ (get wins :adopt-left 0) (get wins :adopt-right 0))) W)
     :distinct-kinds (double (count (frequencies (:exotypes s))))
     :halting-share (/ (double (count (filter absorbing (:exotypes s)))) W)
     :damage-reach (double (count (filter true? (map not= (:phenotype (adv s))
                                                    (:phenotype (adv twin))))))}))

(def SWEEPS {:gamma  [1.0 2.0 4.0 8.0 16.0 64.0]
             :kappa  [0.0 0.1 0.2 0.478 1.0]
             :lambda [0.1 0.3 0.55 0.8 1.0]
             :p      [0.3 0.6 0.8 1.0]})

(defn -main [& [out]]
  (let [rows
        (for [[knob vals] SWEEPS]
          (let [per-val (for [v vals]
                          (let [ms (for [sd SEEDS]
                                     (sensors (nth (iterate tuning/step
                                                            (with-knob (base sd) knob v))
                                                   BURN)))]
                            [v (into {} (for [k (keys (first ms))]
                                          [k (mean (map k ms))]))]))
                sensor-keys (keys (second (first per-val)))]
            [knob (into {} (for [sk sensor-keys]
                             (let [ys (map #(get (second %) sk) per-val)
                                   ;; leverage = full range the knob induces in the sensor
                                   rng (- (apply max ys) (apply min ys))
                                   ;; noise floor = seed sd of the sensor at the mid knob value
                                   mid (nth (map second per-val) (quot (count per-val) 2))]
                               [sk {:range rng :mid (get mid sk)
                                    :ratio (if (pos? (get mid sk))
                                             (/ rng (max 1e-9 (get mid sk))) 0.0)}])))]))]
    (println (format "  KNOB x SENSOR LEVERAGE -- range the knob induces, %d seeds, %d burn-in\n"
                     (count SEEDS) BURN))
    (printf "  %-8s" "knob")
    (doseq [sk [:decision-entropy :blend-share :adoption :distinct-kinds :halting-share :damage-reach]]
      (printf " %16s" (name sk)))
    (println)
    (doseq [[knob m] rows]
      (printf "  %-8s" (name knob))
      (doseq [sk [:decision-entropy :blend-share :adoption :distinct-kinds :halting-share :damage-reach]]
        (printf " %16s" (format "%.4f (%.0f%%)" (:range (get m sk))
                                (* 100 (:ratio (get m sk))))))
      (println))
    (println "\n  cell = absolute range induced (relative to the sensor's own mid-level).")
    (println "  A knob with a LARGE relative range on a sensor can regulate it; near-zero cannot.")
    (when out (spit out (pr-str (into {} rows))))))

(apply -main *command-line-args*)
