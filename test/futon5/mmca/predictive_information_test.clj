(ns futon5.mmca.predictive-information-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.mmca.predictive-information :as pi]))

(deftest entropy-and-mi-estimates
  (testing "constant variables carry no predictive information"
    (is (zero? (:miller-madow
                (pi/mutual-information-estimate (repeat 100 0) (repeat 100 0))))))
  (testing "a balanced variable perfectly predicts itself"
    (let [xs (take 200 (cycle [0 1]))
          estimate (pi/mutual-information-estimate xs xs)]
      (is (< 0.99 (:miller-madow estimate) 1.02)))))

(deftest ais-rejects-frozen-grid
  (let [grid (vec (repeat 40 (vec (repeat 12 0))))
        score (pi/active-information-storage grid {:k 4 :burn-in 8})]
    (is (zero? (:ais-corrected score)))
    (is (= 32 (:samples-per-cell score)))))

(deftest metaca-bitplane-adapter
  (let [history (vec (repeat 20 (apply str (repeat 8 "一"))))
        score (pi/score-metaca-history history {:k 3 :burn-in 5})]
    (is (= 8 (:plane-count score)))
    (is (zero? (:mean-ais-corrected score)))))
