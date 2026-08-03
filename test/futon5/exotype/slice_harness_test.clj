(ns futon5.exotype.slice-harness-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.exotype.slice-harness :as harness]))

(def base-config
  {:seed-base 7 :seeds 1 :width 8 :steps 2 :workers 1
   :lambda 0.55 :mu 0.1 :tau 0.3 :prevalence-radius 1
   :eig-model :beta-posterior :damage-steps 1
   :checkpoints [0 1 2]})

(deftest configuration-is-an-argument-not-shared-state
  (let [low (harness/initial-state (assoc base-config :eig-coefficient 0.5)
                                   :next-C-plus-eig 11)
        high (harness/initial-state (assoc base-config :eig-coefficient 5.0)
                                    :next-C-plus-eig 11)]
    (testing "changing one run config changes no process-global harness state"
      (is (= 0.5 (:eig-coefficient low)))
      (is (= 5.0 (:eig-coefficient high)))
      (is (= (dissoc low :eig-coefficient)
             (dissoc high :eig-coefficient))))))

(deftest explicit-config-run-is-deterministic
  (is (= (harness/seed-run base-config :next-C-plus-eig 13)
         (harness/seed-run base-config :next-C-plus-eig 13))))
