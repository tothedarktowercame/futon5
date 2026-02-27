(ns futon5.mmca.runtime-engine-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ct.tensor-mmca :as tensor-mmca]
            [futon5.mmca.runtime :as runtime]))

(deftest tensor-engine-run-mmca
  (testing "runtime dispatches to tensor engine when requested"
    (let [opts {:engine :tensor
                :genotype "一二三四五六七八"
                :rule-sigil "手"
                :generations 6
                :seed 42
                :step-opts {:wrap? false :boundary-bit 0}}
          via-runtime (runtime/run-mmca opts)
          direct (tensor-mmca/run-tensor-mmca opts)]
      (is (= :tensor (:engine via-runtime)))
      (is (= (:gen-history direct) (:gen-history via-runtime)))
      (is (= (:metrics-history direct) (:metrics-history via-runtime)))
      (is (number? (get-in via-runtime [:summary :composite-score]))))))

(deftest tensor-engine-run-mmca-stream
  (testing "stream path emits init/tick events for tensor engine"
    (let [events (atom [])
          result (runtime/run-mmca-stream {:engine :tensor
                                           :genotype "一二三四五六七八"
                                           :rule-sigil "手"
                                           :generations 4
                                           :seed 42
                                           :step-opts {:wrap? false :boundary-bit 0}}
                                          #(swap! events conj (select-keys % [:phase :tick])))]
      (is (= :tensor (:engine result)))
      (is (= (count (:gen-history result))
             (count @events)))
      (is (= :init (:phase (first @events))))
      (is (= 0 (:tick (first @events))))
      (is (= :tick (:phase (second @events)))))))

(deftest tensor-engine-supports-lesion-and-rule-plan
  (testing "tensor engine accepts lesion/rule-plan options and returns backend metadata"
    (let [result (runtime/run-mmca {:engine :tensor
                                    :genotype "一二三四五六七八"
                                    :rule-sigil "一"
                                    :rule-plan {0 "乐"}
                                    :lesion {:tick 1 :target :genotype :half :left :mode :zero}
                                    :generations 3
                                    :step-opts {:backend :clj :wrap? false :boundary-bit 0}})]
      (is (= :tensor (:engine result)))
      (is (= :clj (get-in result [:tensor :backend])))
      (is (= {:tick 1 :target :genotype :half :left :mode :zero}
             (:lesion result)))
      (is (= 4 (count (:gen-history result)))))))
