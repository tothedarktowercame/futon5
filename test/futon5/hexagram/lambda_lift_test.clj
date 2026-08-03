(ns futon5.hexagram.lambda-lift-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.hexagram.lambda-lift :as lambda-lift]))

(deftest complete-eca-lambda-lookup
  (is (= 256 (count lambda-lift/eca-lambda-table)))
  (is (= 0.0 (lambda-lift/rule-lambda [0 0 0 0 0 0 0 0])))
  (is (= 0.5 (lambda-lift/rule-lambda [0 1 0 1 1 0 1 0])))
  (is (= 1.0 (lambda-lift/rule-lambda [1 1 1 1 1 1 1 1]))))

(deftest six-lines-pack-current-local-quantities
  (let [left (repeat 8 0)
        ego (repeat 8 1)
        right [0 1 0 1 1 0 1 0]
        next [0 0 0 0 0 0 0 1]
        context (vec (concat left ego right next [1 1 0 0]))]
    (is (= [:yin :yang :yang :yin :yang :yang]
           (lambda-lift/context->lines context)))
    (is (= (lambda-lift/context->hexagram context)
           (lambda-lift/context->hexagram context)))))

(deftest lookup-follows-the-rule-at-the-current-moment
  (let [all-zero (vec (repeat 36 0))
        current-ego-high (reduce #(assoc %1 %2 1) all-zero (range 8 16))]
    (testing "changing the current EGO rule changes its lambda line"
      (is (= :yin (nth (lambda-lift/context->lines all-zero) 1)))
      (is (= :yang (nth (lambda-lift/context->lines current-ego-high) 1))))))
