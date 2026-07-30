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
                     110 {:gain 1.0 :width 3 :update-prob 1.0})
          opts {:seeds [1] :sites [0 8]}]
      (is (= (pr-str (score/reach-for candidate opts))
             (pr-str (score/reach-for candidate opts)))))))

(deftest exotype-fork-tapes-stay-aligned-across-live-phenotype-gates
  (testing "opposite damaged phenotypes cannot desynchronise source draws"
    (let [calls (atom [])
          candidate (scored-exotype
                     110 {:gain 1.0 :width 3 :update-prob 1.0})
          _ (score/reach-for
             candidate
             {:seeds [0]
              :sites [0]
              :on-field-step #(swap! calls conj %)})
          fork-calls (drop 60 @calls)
          branch-pairs (partition 2 fork-calls)]
      (is (some (fn [[a b]]
                  (not= (nth (:phenotype a) 0)
                        (nth (:phenotype b) 0)))
                branch-pairs))
      (is (every? (fn [[a b]]
                    (= (:source-draws a) (:source-draws b)))
                  branch-pairs))
      (is (every? (fn [[a b]]
                    (= (:gate-coins a) (:gate-coins b)))
                  branch-pairs)))))

(deftest live-gain-scores-above-frozen-gain
  (testing "the live and frozen reads are causally distinct"
    (let [base (scored-exotype
                110 {:width 3 :update-prob 1.0})
          opts {:seeds [1] :sites (range 0 80 8)}
          live (score/reach-for (assoc-in base [:params :gain] 1.0) opts)
          frozen (score/reach-for (assoc-in base [:params :gain] 0.0) opts)]
      (is (= 0.5 (:mean live)))
      (is (= 0.0 (:mean frozen)))
      (is (> (:mean live) (:mean frozen))))))
