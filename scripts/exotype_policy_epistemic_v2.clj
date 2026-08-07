(ns exotype-policy-epistemic-v2
  "PREREGISTERED BUILD -- DO NOT LAUNCH BEFORE DESIGN REVIEW.

   QUESTION. Does policy-specific expected injection of new dynamics steer the
   exotype policy, rather than merely rewarding adoption?

   SCALE (fixed before dynamics). A single coefficient is used everywhere:

     kappa = RMS_cell,p SD(ambiguity over hold/left/right)
             / RMS_cell,p SD(X_pair over hold/left/right).

   It is calibrated on time-zero states from `scale-seeds`, pooling the entire
   preregistered apply-probability grid. Those seeds are disjoint from both
   churn-control calibration and experiment seeds. The EDN artifact is written
   before any trajectory is run and is thereafter read-only. A zero X
   denominator aborts.

   SCOPE. X_pair is the expected rate of injecting one-step divergence under
   common transform noise. Its validated effective horizon is about 5--20
   steps. Long-horizon damage magnitude is explicitly not evidence for or
   against this term because chaotic mixing erases that signal.

   DESIGN. At every apply probability there are three arms: coefficient zero,
   coefficient kappa, and a policy-insensitive adoption bonus. The bonus is
   calibrated on separate `churn-seeds` to match the epistemic arm's realised
   adoption rate, using the fixed grid below; a gap above `churn-tolerance`
   aborts. Main experiment seeds are untouched by either calibration.

   PREREGISTERED PREDICTION. Relative to EIG-off at the same apply probability,
   EIG-on will select larger X_pair and increase 5--20-step damage reach. At
   low p, where the risk target is reachable, it will reduce identity dominance
   and increase non-halting odd53 wins without restoring the halting-capable
   funnel. At high p it should reduce the halting-capable share. The matched
   churn arm will match adoption rate but have lower selected X and lower early
   damage reach if X is steering rather than only causing change.

   FALSIFIER. The term fails if it does not raise selected X/early reach over
   EIG-off, or if EIG-on and matched churn agree within seed spread on selected
   X, dominant-kind distribution, halting share, and early reach. A mere rise
   in adoption or maximum churn is not a success. Long-horizon damage is not a
   rescue criterion.

   MODES (only calibrate-scale is authorised in this build handoff):
     clojure -M scripts/exotype_policy_epistemic_v2.clj calibrate-scale OUT.edn
     clojure -M scripts/exotype_policy_epistemic_v2.clj calibrate-churn SCALE.edn P OUT.edn
     clojure -M scripts/exotype_policy_epistemic_v2.clj run SCALE.edn CHURN.edn P ARM OUT.edn"
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as policy-epistemic]
            [futon5.exotype.self-tuning :as tuning]))

(def design
  {:schema :exotype-policy-epistemic-v2
   :apply-probabilities [0.1 0.15 0.3 0.6 1.0]
   :width 80
   :steps 600
   :damage-horizon 10
   :checkpoints [20 60 120 300 600]
   :lambda 0.55
   :scale-seeds (vec (range 2026084100 2026084132))
   :churn-seeds (vec (range 2026084200 2026084208))
   :experiment-seeds (vec (range 2026084300 2026084332))
   :churn-bonuses (vec (map #(/ % 20.0) (range 41)))
   :churn-tolerance 0.02})

(def absorbing-kinds #{:collapser :even1 :even4 :even8})

(defn- write-edn! [path value]
  (spit path (with-out-str (pprint/pprint value))))

(defn- mean [xs] (/ (reduce + 0.0 xs) (double (count xs))))

(defn- population-sd [xs]
  (let [m (mean xs)]
    (Math/sqrt (mean (map #(let [d (- (double %) m)] (* d d)) xs)))))

(defn- rms [xs]
  (Math/sqrt (mean (map #(* (double %) (double %)) xs))))

(defn- initial-state [seed]
  (ca/with-seed seed
    (let [width (:width design)
          genotype (vec (ca/random-sigil-string width))]
      {:arm :efe-full :seed seed :time 0
       :self-tuning-arm :hunger-coupled
       :lambda-step-size 0.0
       :hunger-target (:hunger efe/preferences)
       :lambdas (vec (repeat width (:lambda design)))
       :genotype genotype :previous-genotype genotype
       :phenotype (apply str (repeatedly width #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed width)})))

(defn- policies [state index]
  (let [width (count (:exotypes state))]
    [{:policy :hold :source index}
     {:policy :adopt-left :source (mod (dec index) width)}
     {:policy :adopt-right :source (mod (inc index) width)}]))

(defn calibrate-scale []
  (let [rows
        (for [seed (:scale-seeds design)
              :let [state (initial-state seed)]
              p (:apply-probabilities design)
              index (range (:width design))
              :let [own (nth (:exotypes state) index)
                    observation (efe/local-observation state index)
                    candidates (mapv #(nth (:exotypes state) (:source %))
                                     (policies state index))
                    ambiguity (mapv #(-> (efe/score-policy
                                          :efe-full % observation
                                          {:lambda 0.0 :apply-probability p})
                                         :ambiguity)
                                    candidates)
                    x (mapv #(policy-epistemic/pair-value own %) candidates)]]
          {:ambiguity-sd (population-sd ambiguity)
           :x-sd (population-sd x)})
        numerator (rms (map :ambiguity-sd rows))
        denominator (rms (map :x-sd rows))]
    (when (zero? denominator)
      (throw (ex-info "zero RMS policy-epistemic spread; calibration is undefined"
                      {:rows (count rows)})))
    {:schema (:schema design)
     :status :locked-before-dynamics
     :formula :rms-within-cell-ambiguity-sd-over-rms-within-cell-x-sd
     :scope :expected-injection-effective-horizon-5-to-20
     :scale-seeds (:scale-seeds design)
     :reserved-churn-seeds (:churn-seeds design)
     :reserved-experiment-seeds (:experiment-seeds design)
     :apply-probabilities (:apply-probabilities design)
     :cells (count rows)
     :ambiguity-rms-sd numerator
     :x-rms-sd denominator
     :kappa (/ numerator denominator)}))

(defn- checked-scale [path]
  (let [artifact (edn/read-string (slurp path))]
    (when-not (and (= (:schema design) (:schema artifact))
                   (= :locked-before-dynamics (:status artifact))
                   (= (:scale-seeds design) (:scale-seeds artifact))
                   (= (:apply-probabilities design)
                      (:apply-probabilities artifact))
                   (pos? (double (:x-rms-sd artifact))))
      (throw (ex-info "scale artifact does not match the preregistration"
                      {:artifact artifact})))
    artifact))

(defn- arm-options [arm scale bonus]
  (case arm
    :off {:epistemic-coefficient 0.0 :adoption-bonus 0.0}
    :epistemic {:epistemic-coefficient (:kappa scale) :adoption-bonus 0.0}
    :matched-churn {:epistemic-coefficient 0.0 :adoption-bonus bonus}
    (throw (ex-info "unknown arm" {:arm arm}))))

(defn- phenotype-damage [state]
  (let [site (quot (:width design) 2)
        perturbed (update state :phenotype
                          #(apply str (update (vec %) site
                                              (fn [bit] (if (= bit \0) \1 \0)))))
        advance #(nth (iterate tuning/step %) (:damage-horizon design))]
    (count (filter true? (map not= (:phenotype (advance state))
                              (:phenotype (advance perturbed)))))))

(defn- run-trajectory [seed p arm scale bonus measure?]
  (loop [state (merge (initial-state seed)
                      {:apply-probability p}
                      (arm-options arm scale bonus))
         t 0
         adopted 0
         selected-x 0.0
         trajectory (sorted-map)]
    (if (= t (:steps design))
      (let [counts (frequencies (:exotypes state))
            dominant (key (apply max-key val counts))]
        {:seed seed :apply-probability p :arm arm
         :adoption-rate (/ adopted (double (* (:steps design) (:width design))))
         :selected-x (/ selected-x (double (* (:steps design) (:width design))))
         :dominant dominant
         :dominant-share (/ (double (get counts dominant)) (:width design))
         :halting-capable-share
         (/ (reduce + 0 (for [[kind n] counts :when (absorbing-kinds kind)] n))
            (double (:width design)))
         :trajectory trajectory})
      (let [next (tuning/step state)
            winners (map :winner (:self-tuning-decisions next))
            effective-adoptions
            (count (filter true?
                           (map-indexed
                            (fn [index winner]
                              (not= (nth (:exotypes state) index)
                                    (:candidate-exotype winner)))
                            winners)))
            next-time (inc t)
            trajectory'
            (if (and measure? ((set (:checkpoints design)) next-time))
              (assoc trajectory next-time
                     {:phenotype-damage (phenotype-damage next)
                      :halting-capable-share
                      (/ (count (filter absorbing-kinds (:exotypes next)))
                         (double (:width design)))})
              trajectory)]
        (when-not (= p (:apply-probability next))
          (throw (ex-info "apply-probability fell out of trajectory state"
                          {:expected p :actual (:apply-probability next) :time t})))
        (recur next next-time
               (+ adopted effective-adoptions)
               (+ selected-x (reduce + 0.0 (map :epistemic-value winners)))
               trajectory')))))

(defn- adoption-mean [seeds p arm scale bonus]
  (mean (map #(-> (run-trajectory % p arm scale bonus false) :adoption-rate)
             seeds)))

(defn calibrate-churn [scale p]
  (let [target (adoption-mean (:churn-seeds design) p :epistemic scale 0.0)
        trials (mapv (fn [bonus]
                       {:bonus bonus
                        :adoption-rate
                        (adoption-mean (:churn-seeds design) p
                                       :matched-churn scale bonus)})
                     (:churn-bonuses design))
        winner (first (sort-by (juxt #(Math/abs (- (:adoption-rate %) target))
                                     :bonus)
                               trials))
        gap (Math/abs (- (:adoption-rate winner) target))]
    (when (> gap (:churn-tolerance design))
      (throw (ex-info "matched-churn calibration missed its preregistered tolerance"
                      {:p p :target target :winner winner :gap gap})))
    {:schema (:schema design) :status :matched-on-disjoint-seeds
     :apply-probability p :churn-seeds (:churn-seeds design)
     :target-adoption-rate target :bonus (:bonus winner)
     :matched-adoption-rate (:adoption-rate winner) :gap gap
     :grid (:churn-bonuses design)}))

(defn run-cell [scale-path churn-path p arm]
  (let [scale (checked-scale scale-path)
        churn (edn/read-string (slurp churn-path))
        bonus (if (= arm :matched-churn)
                (do
                  (when-not (and (= :matched-on-disjoint-seeds (:status churn))
                                 (= p (:apply-probability churn)))
                    (throw (ex-info "wrong matched-churn artifact" {:churn churn :p p})))
                  (:bonus churn))
                0.0)]
    {:schema (:schema design) :design design :scale scale
     :churn-control (when (= arm :matched-churn) churn)
     :apply-probability p :arm arm
     :runs (mapv #(run-trajectory % p arm scale bonus true)
                 (:experiment-seeds design))}))

(defn -main [& [mode a b c d e]]
  (case mode
    "calibrate-scale"
    (let [artifact (calibrate-scale)]
      (write-edn! a artifact)
      (println (pr-str artifact)))

    "calibrate-churn"
    (let [artifact (calibrate-churn (checked-scale a) (Double/parseDouble b))]
      (write-edn! c artifact)
      (println (pr-str artifact)))

    "run"
    (let [p (Double/parseDouble c)
          arm (keyword d)
          out e]
      (write-edn! out (run-cell a b p arm))
      (println "wrote" out))

    (println "usage: calibrate-scale OUT | calibrate-churn SCALE P OUT | run SCALE CHURN P ARM OUT")))

(apply -main *command-line-args*)
