(ns exotype-inner-loop
  "PREREGISTERED INNER LOOP -- 2026-08-06, before controller runs.

   Offline sensor fit:
     damage = intercept + w_h * halting-share + w_c * phenotype-change-rate.
   Damage is used only to fit fixed sensor weights. It is never computed or
   read by the runtime controller. The runtime sensor is
     S = w_h * halting-share + w_c * phenotype-change-rate.

   RANGE GATE. Before any controller cell, clamp gamma at
   {1,2,4,8,12,15,16,20,24,32,48,64}, run 16 seeds for 2000 steps, and measure
   each seed's final trailing-20 mean S. The setpoint is exactly the midpoint
   between the minimum and maximum gamma-cell means. Controller artifacts
   refuse any range input missing a gamma cell.

   CONTROLLER. After the first 20 observations,
     gamma <- clip(gamma + k * (mean_trailing_20(S) - setpoint), 1, 64).
   Gamma itself is the integral state, so clipping it is anti-windup: rejected
   motion beyond a boundary is not accumulated elsewhere.

   SIGN. The range artifact determines dS/dgamma. k has sign opposite that
   slope, so positive feedback error moves gamma in the direction that lowers
   S. The magnitude is chosen without outcome tuning: a full observed sensor
   span commands 0.25 gamma units per step.

   TESTS. Convergence starts gamma in {2,15,64}. The positive control commands
   S_max + 0.25*(S_max-S_min), which is unreachable, from all three starts.
   Restoration runs to t=1200, then kicks gamma by +8, -8, or 0 and continues
   to t=2000. The zero-kick arm is mandatory.

   FALSIFIER. If the three convergence arms do not reach a common interior
   gamma under the measured reachable midpoint, the inner loop does not close.
   The setpoint is never adjusted after seeing controller outcomes.

   usage:
     snapshot OUT.edn
     fit SENS.edn FIT.edn
     range-cell FIT.edn GAMMA OUT.edn
     range-merge FIT.edn OUT.edn CELL.edn...
     range FIT.edn OUT.edn
     run RANGE.edn ARM OUT.edn
     report OUT.md RANGE.edn RUN.edn..."
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def design
  {:schema :exotype-inner-loop-v1
   :width 80
   :steps 2000
   :seeds (vec (range 2026088000 2026088016))
   :gammas [1.0 2.0 4.0 8.0 12.0 15.0 16.0 20.0 24.0 32.0 48.0 64.0]
   :window 20
   :gain-span-fraction 0.25
   :gamma-bounds [1.0 64.0]
   :kick-time 1200
   :kick-size 8.0
   :checkpoints [0 50 100 200 400 800 1200 1600 2000]
   :blend-action? true
   :epistemic-coefficient 0.2
   :blend-strength 0.0
   :apply-probability 1.0})

(def absorbing-kinds #{:collapser :even1 :even4 :even8})

(def arms
  {:converge-2 {:kind :convergence :gamma0 2.0}
   :converge-15 {:kind :convergence :gamma0 15.0}
   :converge-64 {:kind :convergence :gamma0 64.0}
   :unreachable-2 {:kind :positive-control :gamma0 2.0}
   :unreachable-15 {:kind :positive-control :gamma0 15.0}
   :unreachable-64 {:kind :positive-control :gamma0 64.0}
   :restore-plus {:kind :restoration :kick 8.0}
   :restore-minus {:kind :restoration :kick -8.0}
   :restore-zero {:kind :restoration :kick 0.0}})

(defn- mean [values]
  (/ (reduce + 0.0 values) (double (count values))))

(defn- population-sd [values]
  (let [average (mean values)]
    (Math/sqrt
     (mean (map #(let [delta (- (double %) average)] (* delta delta)) values)))))

(defn- clip [value low high]
  (-> (double value) (max low) (min high)))

(defn- write-edn! [path value]
  (spit path (with-out-str (pprint/pprint value))))

(defn- fit-sensor [runs]
  (let [x1 (mapv #(double (get-in % [:internals :halting])) runs)
        x2 (mapv #(double (get-in % [:internals :change-rate])) runs)
        y (mapv #(double (:damage %)) runs)
        m1 (mean x1) m2 (mean x2) my (mean y)
        cross (fn [a ma b mb]
                (reduce + 0.0 (map #(* (- %1 ma) (- %2 mb)) a b)))
        a (cross x1 m1 x1 m1)
        b (cross x1 m1 x2 m2)
        d (cross x2 m2 x2 m2)
        r1 (cross x1 m1 y my)
        r2 (cross x2 m2 y my)
        determinant (- (* a d) (* b b))]
    (when (zero? determinant)
      (throw (ex-info "sensor regression is singular" {:rows (count runs)})))
    (let [w1 (/ (- (* r1 d) (* r2 b)) determinant)
          w2 (/ (- (* r2 a) (* r1 b)) determinant)
          intercept (- my (* w1 m1) (* w2 m2))
          predictions (map #(+ intercept (* w1 %1) (* w2 %2)) x1 x2)
          residual-ss (reduce + 0.0
                              (map #(let [delta (- %1 %2)] (* delta delta))
                                   y predictions))
          total-ss (reduce + 0.0
                           (map #(let [delta (- % my)] (* delta delta)) y))]
      {:schema (:schema design)
       :status :frozen-offline-fit
       :rows (count runs)
       :intercept intercept
       :weights {:halting w1 :change-rate w2}
       :r2 (- 1.0 (/ residual-ss total-ss))})))

(defn- checked-fit [path]
  (let [fit (edn/read-string (slurp path))]
    (when-not (and (= (:schema design) (:schema fit))
                   (= :frozen-offline-fit (:status fit))
                   (number? (get-in fit [:weights :halting]))
                   (number? (get-in fit [:weights :change-rate])))
      (throw (ex-info "invalid inner-loop sensor fit" {:path path :fit fit})))
    fit))

(defn- initial-state [gamma seed]
  (ca/with-seed seed
    (let [width (:width design)
          genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full
       :seed seed :time 0
       :self-tuning-arm :fixed-0.55 :lambda-step-size 0.0
       :lambdas (vec (repeat width 0.55))
       :genotype genotype :previous-genotype genotype
       :phenotype (ca/random-phenotype-string width)
       :exotypes (grid/initial-grid :heterogeneous-fixed width)
       :blend-action? (:blend-action? design)
       :epistemic-coefficient (:epistemic-coefficient design)
       :blend-strength (:blend-strength design)
       :apply-probability (:apply-probability design)
       :policy-precision gamma})))

(defn- internals [state previous-phenotype weights]
  (let [width (:width design)
        halting (/ (double (count (filter absorbing-kinds (:exotypes state))))
                   width)
        change-rate (/ (double (count (filter true?
                                               (map not= (:phenotype state)
                                                    previous-phenotype))))
                       width)
        sensor (+ (* (:halting weights) halting)
                  (* (:change-rate weights) change-rate))]
    {:halting halting :change-rate change-rate :sensor sensor}))

(defn- trim-window [values]
  (let [window (:window design)]
    (if (> (count values) window)
      (subvec values (- (count values) window))
      values)))

(defn- open-loop-seed [fit gamma seed]
  (loop [state (initial-state gamma seed), time 0, sensors []]
    (if (= time (:steps design))
      {:seed seed :sensor (mean sensors)
       :halting (:halting (internals state (:phenotype state) (:weights fit)))
       :gamma gamma}
      (let [advanced (tuning/step state)
            measurement (internals advanced (:phenotype state) (:weights fit))
            sensors' (trim-window (conj sensors (:sensor measurement)))]
        (recur advanced (inc time) sensors')))))

(defn- default-snapshot []
  (into (sorted-map)
        (for [seed [17 991 424242 20260803]]
          [seed
           (ca/with-seed seed
             (let [project #(select-keys % [:genotype :phenotype :exotypes])
                   initial (dissoc (initial-state 15.0 seed)
                                   :policy-precision :blend-action?
                                   :epistemic-coefficient :blend-strength
                                   :apply-probability)]
               (mapv project (take 61 (iterate tuning/step initial)))))])))

(defn- range-cell [fit gamma]
  (when-not ((set (:gammas design)) gamma)
    (throw (ex-info "gamma is outside the preregistered range grid"
                    {:gamma gamma :grid (:gammas design)})))
  (let [runs (mapv #(open-loop-seed fit gamma %) (:seeds design))]
    {:schema (:schema design) :status :range-cell
     :gamma gamma :design design :fit fit :runs runs
     :sensor-mean (mean (map :sensor runs))
     :sensor-sd (population-sd (map :sensor runs))}))

(defn- checked-range-cell [path]
  (let [cell (edn/read-string (slurp path))]
    (when-not (and (= (:schema design) (:schema cell))
                   (= :range-cell (:status cell))
                   (= 16 (count (:runs cell))))
      (throw (ex-info "invalid reachable-range cell" {:path path :cell cell})))
    cell))

(defn- merge-range [fit cells]
  (let [by-gamma (into {} (map (juxt :gamma identity) cells))
        missing (remove #(contains? by-gamma %) (:gammas design))]
    (when (seq missing)
      (throw (ex-info "reachable-range gate is incomplete" {:missing missing})))
    (let [ordered (mapv by-gamma (:gammas design))
          means (mapv :sensor-mean ordered)
          low (apply min means) high (apply max means)
          span (- high low)
          midpoint (/ (+ low high) 2.0)
          low-cell (:gamma (apply min-key :sensor-mean ordered))
          high-cell (:gamma (apply max-key :sensor-mean ordered))
          gamma-mean (mean (:gammas design))
          sensor-mean (mean means)
          slope (/ (reduce + 0.0
                           (map #(* (- %1 gamma-mean) (- %2 sensor-mean))
                                (:gammas design) means))
                   (reduce + 0.0
                           (map #(let [delta (- % gamma-mean)] (* delta delta))
                                (:gammas design))))]
      (when (zero? span)
        (throw (ex-info "composite sensor has zero reachable span" {})))
      (when (zero? slope)
        (throw (ex-info "composite sensor has no measured gamma leverage" {})))
      (let [slope-sign (compare slope 0.0)
            ;; Stable feedback needs k with the opposite sign to dS/dgamma.
            gain (* (- slope-sign)
                    (/ (:gain-span-fraction design) span))
            equilibrium (:gamma
                         (apply min-key #(Math/abs (- (:sensor-mean %) midpoint))
                                ordered))]
        {:schema (:schema design) :status :reachable-range-locked
         :design design :fit fit :cells ordered
         :sensor-range [low high] :sensor-span span :setpoint midpoint
         :unreachable-setpoint (+ high (* 0.25 span))
         :sensor-min-gamma low-cell :sensor-max-gamma high-cell
         :sensor-on-gamma-slope slope
         :gain gain :gain-rule :quarter-gamma-unit-per-full-sensor-span
         :equilibrium-gamma equilibrium}))))

(defn- checked-range [path]
  (let [artifact (edn/read-string (slurp path))]
    (when-not (and (= (:schema design) (:schema artifact))
                   (= :reachable-range-locked (:status artifact))
                   (= (count (:gammas design)) (count (:cells artifact))))
      (throw (ex-info "controller requires a complete locked range artifact"
                      {:path path :artifact artifact})))
    artifact))

(defn- arm-config [range-artifact arm]
  (let [base (or (arms arm)
                 (throw (ex-info "unknown inner-loop arm"
                                 {:arm arm :known (keys arms)})))]
    (cond-> base
      (= :restoration (:kind base))
      (assoc :gamma0 (:equilibrium-gamma range-artifact))
      (= :positive-control (:kind base))
      (assoc :setpoint (:unreachable-setpoint range-artifact))
      (= :convergence (:kind base))
      (assoc :setpoint (:setpoint range-artifact))
      (= :restoration (:kind base))
      (assoc :setpoint (:setpoint range-artifact)))))

(defn- checkpoint [state measurement measured-sensor]
  {:gamma (:policy-precision state)
   :sensor (:sensor measurement)
   :measured-sensor measured-sensor
   :halting (:halting measurement)
   :change-rate (:change-rate measurement)})

(defn- controller-seed [range-artifact arm seed]
  (let [config (arm-config range-artifact arm)
        wanted (set (:checkpoints design))
        [lower upper] (:gamma-bounds design)]
    (loop [state (initial-state (:gamma0 config) seed)
           time 0 sensors [] last-measurement nil
           kicked? false trajectory (sorted-map)]
      (let [zero-measurement (when (zero? time)
                               (internals state (:phenotype state)
                                          (get-in range-artifact [:fit :weights])))
            measured (when (seq sensors) (mean sensors))
            trajectory' (if (wanted time)
                          (assoc trajectory time
                                 (checkpoint state
                                             (or zero-measurement
                                                 last-measurement)
                                             measured))
                          trajectory)]
        (if (= time (:steps design))
          {:seed seed :trajectory trajectory'
           :final-gamma (:policy-precision state)}
          (let [kick-now? (and (= :restoration (:kind config))
                               (not kicked?) (= time (:kick-time design)))
                gamma-kicked (if kick-now?
                               (clip (+ (:policy-precision state) (:kick config))
                                     lower upper)
                               (:policy-precision state))
                state-kicked (assoc state :policy-precision gamma-kicked)
                advanced (tuning/step state-kicked)
                measurement (internals advanced (:phenotype state-kicked)
                                       (get-in range-artifact [:fit :weights]))
                sensors' (trim-window (conj sensors (:sensor measurement)))
                measured' (mean sensors')
                delta (if (< (count sensors') (:window design))
                        0.0
                        (* (:gain range-artifact)
                           (- measured' (:setpoint config))))
                gamma' (clip (+ gamma-kicked delta) lower upper)]
            (recur (assoc advanced :policy-precision gamma')
                   (inc time) sensors' measurement
                   (or kicked? kick-now?) trajectory')))))))

(defn- run-arm [range-artifact arm]
  (let [config (arm-config range-artifact arm)
        runs (mapv #(controller-seed range-artifact arm %) (:seeds design))]
    {:schema (:schema design) :status :controller-cell
     :arm arm :config config :range range-artifact :runs runs
     :final-gamma-mean (mean (map :final-gamma runs))
     :final-gamma-sd (population-sd (map :final-gamma runs))}))

(defn- checked-run [path]
  (let [run (edn/read-string (slurp path))]
    (when-not (and (= (:schema design) (:schema run))
                   (= :controller-cell (:status run)))
      (throw (ex-info "invalid inner-loop run" {:path path :run run})))
    run))

(defn- report-text [range-artifact runs]
  (let [convergence (filter #(= :convergence (get-in % [:config :kind])) runs)
        finals (mapv :final-gamma-mean convergence)
        final-range (when (seq finals) (- (apply max finals) (apply min finals)))]
    (str
     "# Composite-sensor gamma inner loop\n\n"
     (format "Fit: `S = %.6f*halting + %.6f*change-rate`; intercept %.6f; R^2 %.6f.\n\n"
             (get-in range-artifact [:fit :weights :halting])
             (get-in range-artifact [:fit :weights :change-rate])
             (get-in range-artifact [:fit :intercept])
             (get-in range-artifact [:fit :r2]))
     (format "Reachable S range: `[%.6f, %.6f]`; locked midpoint setpoint `%.6f`; gain `%.6f`.\n\n"
             (first (:sensor-range range-artifact))
             (second (:sensor-range range-artifact))
             (:setpoint range-artifact) (:gain range-artifact))
     "| arm | kind | gamma t=1200 | final gamma mean | SD | seed range |\n|---|---|---:|---:|---:|---:|\n"
     (str/join "\n"
               (for [run runs]
                 (let [seed-finals (mapv :final-gamma (:runs run))
                       at-1200 (mean (map #(get-in % [:trajectory 1200 :gamma])
                                          (:runs run)))]
                   (format "| %s | %s | %.6f | %.6f | %.6f | %.6f--%.6f |"
                         (name (:arm run)) (name (get-in run [:config :kind]))
                         at-1200 (:final-gamma-mean run) (:final-gamma-sd run)
                         (apply min seed-finals) (apply max seed-finals)))))
     "\n\n"
     (if final-range
       (format "Convergence-arm final pairwise range: `%.6f`.\n" final-range)
       "Convergence-arm range unavailable.\n"))))

(defn -main [& [mode a b & more]]
  (case mode
    "snapshot"
    (let [artifact (default-snapshot)]
      ;; Keep the compact serializer used by the pre-edit snapshot so the
      ;; acceptance comparison is byte-for-byte, not merely EDN-equal.
      (spit a (pr-str artifact)) (println "wrote" a))

    "fit"
    (let [runs (edn/read-string (slurp a)) artifact (fit-sensor runs)]
      (write-edn! b artifact) (println (pr-str artifact)))

    "range-cell"
    (let [artifact (range-cell (checked-fit a) (Double/parseDouble b))
          out (first more)]
      (write-edn! out artifact)
      (println (select-keys artifact [:gamma :sensor-mean :sensor-sd])))

    "range-merge"
    (let [fit (checked-fit a) out b cells (mapv checked-range-cell more)
          artifact (merge-range fit cells)]
      (write-edn! out artifact) (println (pr-str (dissoc artifact :cells))))

    "range"
    (let [fit (checked-fit a)
          cells (mapv #(range-cell fit %) (:gammas design))
          artifact (merge-range fit cells)]
      (write-edn! b artifact) (println (pr-str (dissoc artifact :cells))))

    "run"
    (let [artifact (run-arm (checked-range a) (keyword b)) out (first more)]
      (write-edn! out artifact)
      (println (select-keys artifact [:arm :final-gamma-mean :final-gamma-sd])))

    "report"
    (let [out a range-artifact (checked-range b) runs (mapv checked-run more)]
      (spit out (report-text range-artifact runs)) (println "wrote" out))

    (println "usage: snapshot OUT | fit SENS OUT | range-cell FIT GAMMA OUT | range-merge FIT OUT CELLS... | range FIT OUT | run RANGE ARM OUT | report OUT RANGE RUNS...")))

(apply -main *command-line-args*)
