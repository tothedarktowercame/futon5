(ns futon5.ct.tensor-mmca-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ct.tensor-mmca :as tensor-mmca]
            [futon5.mmca.meta-lift :as meta-lift]))

(deftest history-metrics-shape
  (testing "history->metrics-history mirrors runtime metric fields"
    (let [history ["一一一一"
                   "一二一二"
                   "二二二二"]
          metrics (tensor-mmca/history->metrics-history history)]
      (is (= 3 (count metrics)))
      (is (= #{:generation :length :unique-sigils :sigil-counts :entropy :change-rate}
             (set (keys (first metrics)))))
      (is (nil? (:change-rate (first metrics))))
      (is (number? (:change-rate (second metrics)))))))

(deftest tensor-run-mmca-summary
  (testing "run-tensor-mmca returns summary and episode summary"
    (let [result (tensor-mmca/run-tensor-mmca {:genotype "一二三四五六七八"
                                               :rule-sigil "手"
                                               :generations 5
                                               :seed 42
                                               :step-opts {:wrap? false :boundary-bit 0}})]
      (is (= 6 (count (:gen-history result))))
      (is (= 6 (count (:metrics-history result))))
      (is (= :tensor (:backend result)))
      (is (number? (get-in result [:summary :composite-score])))
      (is (contains? (:episode-summary result) :regime)))))

(deftest tensor-and-meta-lift-parity-through-mmca-summary-path
  (testing "tensor and meta-lift histories match under open-boundary semantics"
    (let [row "一二三四五六七八"
          rule "手"
          generations 8
          tensor-run (tensor-mmca/run-tensor-mmca {:genotype row
                                                   :rule-sigil rule
                                                   :generations generations
                                                   :step-opts {:wrap? false :boundary-bit 0}})
          meta-history (loop [i 0
                              curr row
                              hist [row]]
                         (if (>= i generations)
                           hist
                           (let [next (meta-lift/lift-sigil-string curr rule)]
                             (recur (inc i) next (conj hist next)))))
          meta-run (tensor-mmca/run->mmca-metrics {:gen-history meta-history})]
      (is (= (:gen-history tensor-run) (:gen-history meta-run)))
      (is (= (get-in tensor-run [:summary :first-stasis-step])
             (get-in meta-run [:summary :first-stasis-step]))))))
