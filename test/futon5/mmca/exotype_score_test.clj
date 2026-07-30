(ns futon5.mmca.exotype-score-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.mmca.exotype-score :as score]))

(defn- uniform-genotype [rule]
  (let [bits (format "%8s" (Integer/toBinaryString rule))
        bits (str/replace bits " " "0")
        sigil (ca/sigil-for bits)]
    (apply str (repeat 80 sigil))))

(defn- scored-exotype
  [rule params]
  (-> (exotype/lift "一")
      (assoc :initial-genotype (uniform-genotype rule))
      (update :params merge params)))

(deftest zero-update-prob-reproduces-fixed-rule-110
  (testing "a heritable rule-110 field with plasticity off matches calibration"
    (let [candidate (scored-exotype
                     110 {:gain 1.0 :width 3 :update-prob 0.0})
          result (score/reach-for candidate)]
      (is (= {:mean 16.675
              :sd 9.677856035088423
              :n 40}
             result)))))

(deftest reach-for-is-deterministic
  (testing "the same exotype and causal tapes produce byte-identical results"
    (let [candidate (scored-exotype
                     110 {:gain 1.0 :width 3 :update-prob 0.0})
          opts {:seeds [0 2] :sites [0 16]}]
      (is (= (pr-str (score/reach-for candidate opts))
             (pr-str (score/reach-for candidate opts)))))))
