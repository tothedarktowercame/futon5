(ns futon5.exotype.self-tuning
  "Per-cell lambda adaptation driven only by the selected policy's local hunger.

   The update never reads damage, reach, entropy, kind counts, or any global
   statistic. Lambda and exotype updates are synchronous."
  (:require [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]))

(def arms [:hunger-coupled :random-walk :fixed-0.55 :fixed-0.40 :fixed-0.70])
(def default-step-size 0.001)
(def default-hunger-target (:hunger efe/preferences))

(defn clip-unit [x]
  (-> (double x) (max 0.0) (min 1.0)))

(defn- signed-step [value target step-size]
  (cond
    (> value target) step-size
    (< value target) (- step-size)
    :else 0.0))

(defn- random-direction [{:keys [seed time]} index]
  (let [draw-seed (+ (long (or seed 0))
                     (* 1000003 (long (or time 0)))
                     (* 9176 (long index))
                     44119)]
    (if (< (.nextDouble (java.util.Random. draw-seed)) 0.5) -1.0 1.0)))

(defn next-lambda
  "One local update. HUNGER is ignored by the random-walk null."
  [arm state index lambda hunger]
  (let [step-size (double (get state :lambda-step-size default-step-size))
        target (double (get state :hunger-target default-hunger-target))]
    (case arm
      :hunger-coupled
      (clip-unit (+ lambda (signed-step hunger target step-size)))
      :random-walk
      (clip-unit (+ lambda (* step-size (random-direction state index))))
      :fixed-0.55 0.55
      :fixed-0.40 0.40
      :fixed-0.70 0.70
      (throw (ex-info "unknown self-tuning arm" {:arm arm :available arms})))))

(defn cell-decision [state index]
  (let [lambda (double (nth (:lambdas state) index))
        decision (efe/cell-decision :efe-full (assoc state :lambda lambda) index)
        hunger (double (get-in decision [:winner :prediction :hunger]))]
    (assoc decision
           :lambda lambda
           :selected-hunger hunger
           :next-lambda (next-lambda (:self-tuning-arm state) state index
                                    lambda hunger))))

(defn transmit [state]
  (let [decisions (mapv #(cell-decision state %)
                        (range (count (:exotypes state))))]
    {:exotypes (mapv #(get-in % [:winner :candidate-exotype]) decisions)
     :lambdas (mapv :next-lambda decisions)
     :decisions decisions}))

(defn step
  "Advance phenotype, genotype, exotype, and lambda fields synchronously."
  [state]
  (let [{:keys [exotypes lambdas decisions]} (transmit state)
        previous (:genotype state)
        advanced (grid/step (assoc state :arm :heterogeneous-fixed))]
    (-> advanced
        (assoc :arm :efe-full
               :self-tuning-arm (:self-tuning-arm state)
               :previous-genotype previous
               :exotypes exotypes
               :lambdas lambdas
               :lambda-step-size
               (double (get state :lambda-step-size default-step-size))
               :hunger-target
               (double (get state :hunger-target default-hunger-target))
               :efe-decisions decisions
               :self-tuning-decisions decisions))))

(defn run-steps [state steps]
  (nth (iterate step state) steps))
