(ns futon5.ct.tensor-transfer-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ct.tensor-transfer :as transfer]))

(deftest top-sigils-extraction
  (testing "top sigils are extracted from lifted history"
    (let [history ["一二三四"
                   "二三四五"
                   "三四五六"]
          out (transfer/top-sigils-from-history history 4)]
      (is (seq (:top-sigils out)))
      (is (map? (:sigil-counts out))))))

(deftest transfer-pack-produces-cyber-ant-and-aif
  (testing "transfer pack diagram executes end-to-end"
    (let [history ["一二三四五六七八"
                   "二三四五六七八九"
                   "三四五六七八九十"]
          summary {:composite-score 42.0
                   :avg-change 0.2
                   :avg-unique 0.5
                   :temporal-autocorr 0.7}
          out (transfer/run-transfer-pack {:gen-history history
                                           :summary summary
                                           :seed 77
                                           :run-index 2})]
      (is (seq (:top-sigils out)))
      (is (map? (:cyber-ant out)))
      (is (number? (get-in out [:aif :aif/score]))))))
