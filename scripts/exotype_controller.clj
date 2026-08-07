(ns exotype-controller
  "PREREGISTERED CONTROLLER EXPERIMENT -- 2026-08-05, before any sweep run.

   QUESTION. Does the endogenous hunger-coupled lambda controller converge to
   and restore a reachable setpoint, rather than merely ramping at its clamp?

   MECHANISM. `self-tuning/next-lambda` reads `:hunger-target` from STATE and
   applies a clipped sign update. The preregistered clamp sweep found mean
   winner-hunger h(lambda) falling from 0.1755 at lambda=0 to 0.1617 at
   lambda=1 (slope -0.01369 +/- 0.00180). Thus target 0.1676 is reachable and
   target 0.05 is not.

   T1 CONVERGENCE. Cross lambda0 in {0.10,0.55,0.90} with target in
   {0.1676,0.05}. P1: for target 0.1676, the three mean lambdas at t=2000 have
   pairwise range <= 0.11 and each lies in [0.01,0.99]. Predicted common value:
   lambda* approximately 0.50. P2, positive control: for target 0.05, all three
   mean lambdas at t=2000 exceed 0.99. If P2 fails the harness is invalid and
   no other result is interpreted.

   T2 RESTORATION. Start lambda0=0.55 at target 0.1676. Measure the t=1200
   equilibrium state, then kick every lambda by +0.2, -0.2, or 0 (clipped), and
   continue to t=2000. P3: for both nonzero kicks,
     abs(mean-lambda(2000) - mean-lambda(1200 pre-kick)) < 0.1.
   This is at least half of the 0.2 kick undone.

   N1 RAMP CONTROL. Open-loop lambda(t)=clip(0.55 + 0.001*t), ignoring hunger.
   It tests whether the reachable-target coupled trajectory is distinguishable
   from parameter-free upward motion. N1 is not treated as a restoration arm:
   it is already clamped at 1 by t=1200, so the packet's requested kick-based
   P3 contrast is not operationally defined for this schedule.

   FALSIFIER. If the three reachable-target T1 arms do not satisfy P1, the
   negative-feedback reading is wrong regardless of the slope t-statistic. Do
   not adjust the target.

   At every checkpoint, damage is measured on two discarded immutable forks:
   flip the centre phenotype bit in one, advance both H=20 under shared
   deterministic draws, count phenotype differences, and leave the main state
   untouched.

   `smoke` is a labelled one-seed/50-step, H=5 wiring check and is not evidence.

   usage:
     clojure -M scripts/exotype_controller.clj run <t1|t2|n1> <arm> <out.edn>
     clojure -M scripts/exotype_controller.clj report <out.md> <in.edn>...
     clojure -M scripts/exotype_controller.clj smoke <test> <arm> <out.edn>"
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(def design
  {:schema :exotype-controller-v1
   :width 80
   :steps 2000
   :checkpoints [0 50 100 200 400 800 1200 1600 2000]
   :damage-horizon 20
   :seeds (vec (range 2026085200 2026085216))
   :step-size 0.001
   :reachable-target 0.1676
   :unreachable-target 0.05
   :blend-action? true
   :epistemic-coefficient 0.2
   :blend-strength 0.0
   :apply-probability 1.0
   :kick-time 1200
   :kick-size 0.2})

(def smoke-design
  (assoc design
         :steps 50
         :checkpoints [0 50]
         :damage-horizon 5
         :seeds [(first (:seeds design))]))

(def t1-arms
  {:low-reachable {:lambda0 0.10 :target 0.1676}
   :mid-reachable {:lambda0 0.55 :target 0.1676}
   :high-reachable {:lambda0 0.90 :target 0.1676}
   :low-unreachable {:lambda0 0.10 :target 0.05}
   :mid-unreachable {:lambda0 0.55 :target 0.05}
   :high-unreachable {:lambda0 0.90 :target 0.05}})

(def t2-arms
  {:kick-plus {:lambda0 0.55 :target 0.1676 :kick 0.2}
   :kick-minus {:lambda0 0.55 :target 0.1676 :kick -0.2}
   :no-kick {:lambda0 0.55 :target 0.1676 :kick 0.0}})

(def n1-arms
  {:ramp {:lambda0 0.55 :target nil :open-loop? true}})

(defn- mean [xs]
  (/ (reduce + 0.0 xs) (double (count xs))))

(defn- population-sd [xs]
  (let [average (mean xs)]
    (Math/sqrt
     (mean (map #(let [delta (- (double %) average)] (* delta delta)) xs)))))

(defn- clip-unit [value]
  (-> (double value) (max 0.0) (min 1.0)))

(defn- cell-config [test arm]
  (or (get (case test :t1 t1-arms :t2 t2-arms :n1 n1-arms {}) arm)
      (throw (ex-info "unknown controller test/arm"
                      {:test test :arm arm
                       :known {:t1 (keys t1-arms)
                               :t2 (keys t2-arms)
                               :n1 (keys n1-arms)}}))))

(defn- initial-state [run-design cell seed]
  (ca/with-seed seed
    (let [width (:width run-design)
          genotype (vec (ca/random-sigil-string width))]
      (cond->
       {:arm :efe-full
        :seed seed
        :time 0
        :self-tuning-arm :hunger-coupled
        :lambda-step-size (if (:open-loop? cell) 0.0 (:step-size run-design))
        :lambdas (vec (repeat width (:lambda0 cell)))
        :genotype genotype
        :previous-genotype genotype
        :phenotype (ca/random-phenotype-string width)
        :exotypes (grid/initial-grid :heterogeneous-fixed width)
        :blend-action? (:blend-action? run-design)
        :epistemic-coefficient (:epistemic-coefficient run-design)
        :blend-strength (:blend-strength run-design)
        :apply-probability (:apply-probability run-design)}
        (some? (:target cell)) (assoc :hunger-target (:target cell))))))

(defn- advance-one [run-design cell state next-time]
  (let [advanced (tuning/step state)]
    (if (:open-loop? cell)
      (assoc advanced :lambdas
             (vec (repeat (:width run-design)
                          (clip-unit (+ (:lambda0 cell)
                                        (* (:step-size run-design) next-time))))))
      advanced)))

(defn- phenotype-damage [run-design cell state]
  (let [site (quot (:width run-design) 2)
        perturbed (update state :phenotype
                          #(apply str
                                  (update (vec %) site
                                          (fn [bit] (if (= bit \0) \1 \0)))))
        advance
        (fn [initial]
          (loop [fork initial, offset 0]
            (if (= offset (:damage-horizon run-design))
              fork
              (recur (advance-one run-design cell fork
                                  (+ (:time state) offset 1))
                     (inc offset)))))
        control-final (advance state)
        perturbed-final (advance perturbed)]
    (count (filter true?
                   (map not= (:phenotype control-final)
                        (:phenotype perturbed-final))))))

(defn- checkpoint [run-design cell state]
  {:damage-reach (phenotype-damage run-design cell state)
   :mean-lambda (mean (:lambdas state))
   :lambda-sd (population-sd (:lambdas state))})

(defn- apply-kick [state kick]
  (update state :lambdas
          #(mapv (fn [lambda] (clip-unit (+ lambda kick))) %)))

(defn- run-seed [run-design test cell seed]
  (let [wanted (set (:checkpoints run-design))]
    (loop [state (initial-state run-design cell seed)
           time 0
           kicked? false
           trajectory (sorted-map)]
      (let [trajectory' (if (wanted time)
                          (assoc trajectory time (checkpoint run-design cell state))
                          trajectory)]
        (if (= time (:steps run-design))
          {:seed seed :trajectory trajectory'}
          (let [kick-now? (and (= :t2 test)
                               (not kicked?)
                               (= time (:kick-time run-design)))
                state' (if kick-now? (apply-kick state (:kick cell)) state)]
            (recur (advance-one run-design cell state' (inc time))
                   (inc time)
                   (or kicked? kick-now?)
                   trajectory')))))))

(defn- run-cell [run-design test arm smoke?]
  (let [cell (cell-config test arm)]
    {:schema (:schema design)
     :status (if smoke? :smoke-non-evidential :preregistered-cell)
     :test test
     :arm arm
     :cell cell
     :design run-design
     :runs (mapv #(run-seed run-design test cell %) (:seeds run-design))}))

(defn- write-edn! [path value]
  (spit path (with-out-str (pprint/pprint value))))

(defn- checked-cell [path]
  (let [cell (edn/read-string (slurp path))]
    (when-not (and (= (:schema design) (:schema cell))
                   (#{:t1 :t2 :n1} (:test cell))
                   (seq (:runs cell)))
      (throw (ex-info "input is not an exotype-controller cell"
                      {:path path :artifact cell})))
    cell))

(defn- spread [values]
  {:mean (mean values) :sd (population-sd values)})

(defn- metric-row [cell time]
  (into {:test (:test cell) :arm (:arm cell) :time time}
        (for [metric [:damage-reach :mean-lambda :lambda-sd]]
          [metric (spread (map #(get-in % [:trajectory time metric])
                               (:runs cell)))])))

(defn- final-mean-lambda [cell time]
  (:mean (:mean-lambda (metric-row cell time))))

(defn- production-by-key [cells]
  (into {}
        (for [cell cells :when (= :preregistered-cell (:status cell))]
          [[(:test cell) (:arm cell)] cell])))

(defn- p1-result [by-key]
  (let [arms [:low-reachable :mid-reachable :high-reachable]
        cells (map #(get by-key [:t1 %]) arms)]
    (when (every? some? cells)
      (let [values (mapv #(final-mean-lambda % 2000) cells)
            pairwise-range (- (apply max values) (apply min values))
            interior? (every? #(<= 0.01 % 0.99) values)]
        {:pass? (and (<= pairwise-range 0.11) interior?)
         :values (zipmap arms values)
         :pairwise-range pairwise-range
         :interior? interior?
         :predicted-common-value 0.50}))))

(defn- p2-result [by-key]
  (let [arms [:low-unreachable :mid-unreachable :high-unreachable]
        cells (map #(get by-key [:t1 %]) arms)]
    (when (every? some? cells)
      (let [values (mapv #(final-mean-lambda % 2000) cells)]
        {:pass? (every? #(< 0.99 %) values)
         :values (zipmap arms values)}))))

(defn- p3-result [by-key]
  (let [arms [:kick-plus :kick-minus]
        cells (map #(get by-key [:t2 %]) arms)]
    (when (every? some? cells)
      (let [restoration
            (mapv (fn [cell]
                    (let [before (final-mean-lambda cell 1200)
                          final (final-mean-lambda cell 2000)]
                      {:pre-kick before :final final
                       :absolute-return-error (Math/abs (- final before))}))
                  cells)]
        {:pass? (every? #(< (:absolute-return-error %) 0.1) restoration)
         :arms (zipmap arms restoration)
         :threshold 0.1}))))

(defn- fmt-spread [{:keys [mean sd]}]
  (format "%.4f +/- %.4f" mean sd))

(defn- report-text [cells]
  (let [rows (for [cell cells
                   time (get-in cell [:design :checkpoints])]
               (metric-row cell time))
        by-key (production-by-key cells)
        p1 (p1-result by-key)
        p2 (p2-result by-key)
        p3 (p3-result by-key)]
    (str
     "# Endogenous lambda controller\n\n"
     "Preregistered in `scripts/exotype_controller.clj` before the sweep. "
     "Smoke cells are non-evidential. P2 is a harness-validity gate: if it "
     "fails, P1 and P3 are not interpreted.\n\n"
     "| test | arm | t | damage | mean lambda | lambda SD |\n"
     "|---|---|---:|---:|---:|---:|\n"
     (str/join
      "\n"
      (for [row rows]
        (format "| %s | %s | %d | %s | %s | %s |"
                (name (:test row)) (name (:arm row)) (:time row)
                (fmt-spread (:damage-reach row))
                (fmt-spread (:mean-lambda row))
                (fmt-spread (:lambda-sd row)))))
     "\n\n## Preregistered decision rules\n\n"
     "- P1: " (if p1 (pr-str p1) "not computable; incomplete T1 reachable cells") "\n"
     "- P2: " (if p2 (pr-str p2) "not computable; incomplete T1 positive controls") "\n"
     "- P3: " (if p3 (pr-str p3) "not computable; incomplete T2 kick cells") "\n"
     "- N1: descriptive open-loop ramp only; its t=1200 clamp makes the requested "
     "kick-restoration contrast non-identifying.\n")))

(defn -main [& [mode a b & more]]
  (case mode
    "run"
    (let [test (keyword a) arm (keyword b) out (first more)
          artifact (run-cell design test arm false)]
      (write-edn! out artifact)
      (println (format "%s/%s: %d seeds -> %s"
                       a b (count (:runs artifact)) out)))

    "smoke"
    (let [test (keyword a) arm (keyword b) out (first more)
          artifact (run-cell smoke-design test arm true)]
      (write-edn! out artifact)
      (println (pr-str artifact)))

    "report"
    (let [out a
          paths (cons b more)
          cells (mapv checked-cell paths)
          keys (map (juxt :test :arm) cells)]
      (when-not (= (count keys) (count (distinct keys)))
        (throw (ex-info "duplicate test/arm cell in report" {:cells keys})))
      (spit out (report-text cells))
      (println "wrote" out))

    (println "usage: run TEST ARM OUT.edn | report OUT.md IN.edn... | smoke TEST ARM OUT.edn")))

(apply -main *command-line-args*)
