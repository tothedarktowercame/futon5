(ns futon5.exotype.prevalence-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.prevalence :as prevalence]))

(defn- initial-state [seed tau]
  (ca/with-seed seed
    (let [genotype (vec (ca/random-sigil-string 12))]
      {:arm :efe-full :seed seed :time 0 :lambda 0.55 :tau tau
       :prevalence-radius 1
       :genotype genotype :previous-genotype genotype
       :phenotype (apply str (repeatedly 12 #(if (< (ca/rnd) 0.5) \0 \1)))
       :exotypes (grid/initial-grid :heterogeneous-fixed 12)})))

(defn- legacy-view [state]
  (select-keys state [:arm :seed :time :phenotype :genotype
                      :previous-genotype :exotypes :lambda]))

(deftest prevalence-is-read-from-current-neighbourhood
  (let [xs [:identity :chaos :builder :identity]]
    (is (= [3 0 1] (prevalence/neighbourhood-indices 4 0 1)))
    (is (= 2 (prevalence/candidate-prevalence xs 0 1 :identity)))
    (is (= 1 (prevalence/candidate-prevalence xs 0 1 :chaos)))))

(deftest zero-temperature-is-the-legacy-trajectory
  (let [initial (initial-state 20260803 0.0)
        legacy-initial (dissoc initial :tau :prevalence-radius)
        legacy (mapv legacy-view (take 21 (iterate efe/step legacy-initial)))
        slice3 (mapv legacy-view (take 21 (iterate prevalence/step initial)))]
    (is (= legacy slice3))))

(deftest softmax-run-is-deterministic-by-seed
  (let [run #(take 21 (iterate prevalence/step (initial-state % 0.3)))]
    (is (= (run 20260803) (run 20260803)))
    (testing "the stochastic branch is live"
      (is (some #(= :prevalence-softmax
                    (get-in % [:prevalence-decisions 0 :selection]))
                (rest (run 20260803)))))))

(deftest negative-temperature-refuses
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"tau must be nonnegative"
                        (prevalence/cell-decision
                         :efe-full (initial-state 1 -0.1) 0))))
