(ns futon5.mmca.causal-score-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.mmca.causal-score :as causal]))

(defn- fixed-field [rule]
  (vec (repeat 80 rule)))

(deftest fixed-rule-zero-smoke-test
  (testing "the standalone protocol runs and rule 0 has zero causal reach"
    (is (= {:mean 0.0 :sd 0.0 :n 1 :seed-sd 0.0
            :by-seed [{:seed 0 :damages [0] :mean 0.0}]}
           (causal/reach (fixed-field 0) (causal/eca-config)
                         {:seeds [0] :sites [0]})))))
