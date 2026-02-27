(ns futon5.mmca.metaevolve-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [futon5.mmca.metaevolve :as metaevolve]))

(defn- unique-path [prefix]
  (str "/tmp/" prefix "-" (System/currentTimeMillis) "-" (rand-int 1000000) ".edn"))

(deftest tensor-engine-path-writes-compatible-payloads
  (testing "metaevolve can dispatch to tensor closed-loop engine"
    (let [report (unique-path "metaevolve-tensor-report")
          feedback (unique-path "metaevolve-tensor-feedback")]
      (try
        (metaevolve/-main "--engine" "tensor-closed-loop"
                          "--seed" "2026"
                          "--runs" "3"
                          "--length" "8"
                          "--generations" "5"
                          "--feedback-top" "8"
                          "--tensor-explore-rate" "0.25"
                          "--report" report
                          "--feedback-edn" feedback)
        (let [report-edn (edn/read-string (slurp report))
              feedback-edn (edn/read-string (slurp feedback))]
          (is (= :tensor (:engine report-edn)))
          (is (= :tensor (:engine feedback-edn)))
          (is (true? (:closed-loop report-edn)))
          (is (true? (:closed-loop feedback-edn)))
          (is (= 3 (:runs report-edn)))
          (is (= 3 (:runs-completed feedback-edn)))
          (is (seq (:leaderboard report-edn)))
          (is (seq (:feedback-sigils feedback-edn))))
        (finally
          (when (.exists (io/file report))
            (.delete (io/file report)))
          (when (.exists (io/file feedback))
            (.delete (io/file feedback))))))))
