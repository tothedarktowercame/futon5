(ns futon5.mmca.causal-score-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.mmca.causal-score :as causal]))

(defn- fixed-field [rule]
  (vec (repeat 80 rule)))

(deftest exact-ordered-calibration
  (testing "rules 0, 204, and 90 reproduce the exact published invariants"
    (doseq [[rule expected] [[0 0.0] [204 1.0] [90 8.0]]]
      (let [result (causal/reach (fixed-field rule) (causal/eca-config))]
        (is (= expected (:mean result)) (str "rule " rule))
        (is (= 0.0 (:sd result)) (str "rule " rule " spread"))
        (is (= 40 (:n result)) (str "rule " rule " sample count"))))))
