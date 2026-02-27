(ns futon5.ct.tensor-benchmark-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ct.tensor-benchmark :as bench]))

(deftest generated-cases-are-seed-deterministic
  (testing "same seed yields same generated inputs"
    (let [a (bench/generate-cases {:seed 123 :cases 4 :length 8 :generations 5})
          b (bench/generate-cases {:seed 123 :cases 4 :length 8 :generations 5})]
      (is (= a b)))))

(deftest fixed-seed-benchmark-parity
  (testing "tensor and meta-lift paths agree on generated cases"
    (let [result (bench/run-fixed-seed-benchmark {:seed 123
                                                  :cases 3
                                                  :length 10
                                                  :generations 6})
          summary (:summary result)]
      (is (= 3 (:cases summary)))
      (is (= 1.0 (:parity-rate summary)))
      (is (= 1.0 (:history-parity-rate summary)))
      (is (number? (:tensor-ms-total summary)))
      (is (number? (:meta-lift-ms-total summary))))))

(deftest benchmark-report-shape
  (testing "report/feedback payloads follow metaevolve-compatible shape"
    (let [result (bench/run-fixed-seed-benchmark {:seed 321
                                                  :cases 3
                                                  :length 8
                                                  :generations 4})
          report (bench/report-payload result {:leaderboard-size 2})
          feedback (bench/feedback-payload result {:leaderboard-size 2})]
      (is (= 2 (count (:leaderboard report))))
      (is (= 2 (count (:leaderboard feedback))))
      (is (seq (:ranked report)))
      (is (seq (:feedback-sigils feedback)))
      (is (= :tensor (:engine report)))
      (is (= :tensor (:engine feedback))))))
