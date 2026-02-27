(ns futon5.ct.tensor-closed-loop-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ct.tensor-closed-loop :as loop]))

(deftest closed-loop-is-deterministic
  (testing "same seed/options produce identical closed-loop outputs"
    (let [opts {:seed 2026
                :runs 3
                :length 10
                :generations 6
                :feedback-top 8}
          a (loop/run-tensor-closed-loop opts)
          b (loop/run-tensor-closed-loop opts)]
      (is (= a b)))))

(deftest closed-loop-parity-and-policy-feedback
  (testing "default closed loop is parity-clean and accumulates transfer feedback"
    (let [result (loop/run-tensor-closed-loop {:seed 123
                                               :runs 3
                                               :length 8
                                               :generations 5})]
      (is (= 1.0 (get-in result [:summary :history-parity-rate])))
      (is (every? :history-parity? (:results result)))
      (is (seq (:feedback-sigils result)))
      (is (= 3 (count (:results result)))))))

(deftest closed-loop-report-feedback-shapes
  (testing "report/feedback payloads follow shared metaevolve contract"
    (let [result (loop/run-tensor-closed-loop {:seed 77
                                               :runs 3
                                               :length 8
                                               :generations 4})
          report (loop/report-payload result {:leaderboard-size 2})
          feedback (loop/feedback-payload result {:leaderboard-size 2})]
      (is (= :tensor (:engine report)))
      (is (= :tensor (:engine feedback)))
      (is (true? (:closed-loop report)))
      (is (true? (:closed-loop feedback)))
      (is (= 2 (count (:leaderboard report))))
      (is (= 2 (count (:leaderboard feedback))))
      (is (= 3 (:runs-completed feedback)))
      (is (seq (:runs-detail report))))))

(deftest closed-loop-exploration-stays-deterministic
  (testing "exploration remains reproducible for a fixed seed"
    (let [opts {:seed 99
                :runs 4
                :length 8
                :generations 4
                :init-feedback-sigils ["手" "乐" "甘"]
                :explore-rate 0.5}
          a (loop/run-tensor-closed-loop opts)
          b (loop/run-tensor-closed-loop opts)]
      (is (= a b))
      (is (= 0.5 (get-in a [:config :explore-rate]))))))
