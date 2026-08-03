(ns futon5.exotype.self-tuning-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.exotype.self-tuning :as tuning]))

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
