(ns futon5.exotype.self-tuning-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.self-tuning :as tuning]))

(defn- baseline-step [state]
  (let [decisions
        (mapv (fn [index]
                (let [lambda (double (nth (:lambdas state) index))
                      decision (efe/cell-decision
                                :efe-full (assoc state :lambda lambda) index)
                      hunger (double (get-in decision [:winner :prediction :hunger]))]
                  (assoc decision :lambda lambda :selected-hunger hunger
                         :next-lambda
                         (tuning/next-lambda (:self-tuning-arm state) state index
                                             lambda hunger))))
              (range (count (:exotypes state))))
        exotypes (mapv #(get-in % [:winner :candidate-exotype]) decisions)
        lambdas (mapv :next-lambda decisions)
        previous (:genotype state)
        advanced (grid/step (assoc state :arm :heterogeneous-fixed))]
    (-> advanced
        (assoc :arm :efe-full :self-tuning-arm (:self-tuning-arm state)
               :previous-genotype previous :exotypes exotypes :lambdas lambdas
               :lambda-step-size (:lambda-step-size state)
               :hunger-target (:hunger-target state)
               :efe-decisions decisions :self-tuning-decisions decisions))))

(deftest hunger-feedback-has-the-required-sign
  (let [state {:lambda-step-size 0.01 :hunger-target 0.05
               :seed 1 :time 0}]
    (is (= 0.51 (tuning/next-lambda :hunger-coupled state 0 0.5 0.7)))
    (is (= 0.49 (tuning/next-lambda :hunger-coupled state 0 0.5 0.01)))
    (is (= 0.5 (tuning/next-lambda :hunger-coupled state 0 0.5 0.05)))))

(deftest clipping-and-fixed-arms
  (let [state {:lambda-step-size 0.1 :hunger-target 0.05
               :seed 1 :time 0}]
    (is (= 1.0 (tuning/next-lambda :hunger-coupled state 0 0.99 0.8)))
    (is (= 0.0 (tuning/next-lambda :hunger-coupled state 0 0.01 0.0)))
    (is (= 0.55 (tuning/next-lambda :fixed-0.55 state 0 0.1 0.9)))
    (is (= 0.4 (tuning/next-lambda :fixed-0.40 state 0 0.9 0.0)))
    (is (= 0.7 (tuning/next-lambda :fixed-0.70 state 0 0.1 0.9)))))

(deftest random-walk-is-stateless-and-does-not-read-hunger
  (let [state {:lambda-step-size 0.01 :hunger-target 0.05
               :seed 20260803 :time 17}
        low (tuning/next-lambda :random-walk state 9 0.5 0.0)
        high (tuning/next-lambda :random-walk state 9 0.5 1.0)]
    (is (= low high))
    (is (= low (tuning/next-lambda :random-walk state 9 0.5 0.4)))
    (is (< (Math/abs (- 0.01 (Math/abs (- low 0.5)))) 1.0e-12))))

(deftest global-observables-are-not-accepted-by-the-update-interface
  (testing "only arm/state/index/lambda/hunger enter next-lambda"
    (is (= 5 (count (first (:arglists (meta #'tuning/next-lambda))))))))

(deftest optimized-long-horizon-step-is-baseline-identical
  (let [initial
        (ca/with-seed 991
          (let [genotype (vec (ca/random-sigil-string 12))]
            {:arm :efe-full :self-tuning-arm :hunger-coupled
             :seed 991 :time 0 :lambda-step-size 0.001 :hunger-target 0.05
             :lambdas (mapv #(/ % 11.0) (range 12))
             :genotype genotype :previous-genotype genotype
             :phenotype (apply str (repeatedly 12 #(if (< (ca/rnd) 0.5) \0 \1)))
             :exotypes (grid/initial-grid :heterogeneous-fixed 12)}))]
    (loop [fast initial, baseline initial, remaining 20]
      (when (pos? remaining)
        (let [fast-next (tuning/step fast)
              baseline-next (baseline-step baseline)]
          (is (= baseline-next fast-next))
          (recur fast-next baseline-next (dec remaining)))))))
