(ns futon5.mmca.causal-state-particles-test
  (:require [clojure.test :refer [deftest is]]
            [futon5.mmca.causal-state-particles :as particles]))

(deftest homogeneous-state-field-has-domain-but-no-particles
  (let [field (vec (repeat 20 (vec (repeat 20 0))))
        result (particles/decompose
                field {:training-range [2 10]
                       :evaluation-range [10 18]
                       :margin 1})]
    (is (= 1.0 (:domain-coverage result)))
    (is (zero? (get-in result [:particles :count])))
    (is (zero? (:score result)))))

(deftest causal-state-transition-violations-form-persistent-objects
  (let [base (vec (repeat 20 0))
        field (mapv (fn [t]
                      (if (< t 10)
                        base
                        (assoc base (+ 5 (quot (- t 10) 2)) 1)))
                    (range 20))
        result (particles/decompose
                field {:training-range [2 10]
                       :evaluation-range [10 18]
                       :margin 1})]
    (is (pos? (get-in result [:particles :count])))
    (is (pos? (:score result)))))
