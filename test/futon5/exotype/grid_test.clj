(ns futon5.exotype.grid-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.slice-harness :as harness]
            [futon5.exotype.grid :as grid]))

(deftest fixed-and-local-transmission-policies
  (let [exotypes [:builder :builder :chaos :identity]
        phenotype "0001"]
    (is (= exotypes (grid/transmit :uniform-fixed exotypes phenotype)))
    (is (= exotypes (grid/transmit :heterogeneous-fixed exotypes phenotype)))
    (is (= exotypes
           (grid/transmit :conformist exotypes phenotype)))
    (is (= exotypes
           (grid/transmit :boring-triggered exotypes phenotype)))))

(deftest every-arm-is-selectable-and-deterministic
  (doseq [arm grid/arms]
    (testing (name arm)
      (let [state {:arm arm :genotype (vec (repeat 8 "一"))
                   :phenotype "01010101"
                   :exotypes (vec (repeat 8 :identity))}]
        (is (= (grid/step state) (grid/step state)))))))

(deftest vocabulary-is-neighbourhood-converted
  (is (= #{:builder :collapser :chaos :identity}
         (set (keys grid/propagators))))
  (let [neighbourhoods #{"000" "001" "010" "100" "011" "101" "110" "111"}]
    (is (every? #(and (= neighbourhoods (set (keys %)))
                      (= neighbourhoods (set (vals %))))
                (vals grid/propagators)))))

(deftest zero-transfer-reproduces-stored-run-byte-identically
  (let [config {:width 8 :steps 12 :lambda 0.55 :mu 0.1 :tau 0.3
                :prevalence-radius 1 :eig-model :legacy :eig-coefficient 0.0
                :damage-steps 5 :checkpoints [0 12] :transfer-fraction 0.0
                :blend-strength 0.0}
        expected (str/trim-newline
                  (slurp (io/resource "futon5/exotype/grid_q0_baseline.edn")))
        actual (pr-str (harness/seed-run config :next-C 17))]
    (is (= expected actual))))

(deftest neighbour-agreement-blend-is-faithful
  (let [zero-rule (ca/sigil-for "00000000")
        identity-rule (ca/sigil-for "11001100")
        one-rule (ca/sigil-for "11111111")]
    (testing "agreement survives unchanged"
      (is (= zero-rule (grid/blend-rule zero-rule identity-rule zero-rule))))
    (testing "the centre rule adjudicates neighbour disagreement"
      (is (= identity-rule
             (grid/blend-rule zero-rule identity-rule one-rule))))))

(deftest zero-blend-strength-is-byte-identical-to-legacy-path
  (let [config {:width 8 :steps 12 :lambda 0.55 :mu 0.1 :tau 0.3
                :prevalence-radius 1 :eig-model :legacy :eig-coefficient 0.0
                :damage-steps 5 :checkpoints [0 12] :transfer-fraction 0.0}]
    (is (= (pr-str (harness/seed-run config :next-C 17))
           (pr-str (harness/seed-run (assoc config :blend-strength 0.0)
                                     :next-C 17))))))

(deftest full-blend-feeds-complete-neighbour-agreement-rule-to-propagator
  (let [zero-rule (ca/sigil-for "00000000")
        identity-rule (ca/sigil-for "11001100")
        one-rule (ca/sigil-for "11111111")
        result (grid/apply-exotype-blend zero-rule identity-rule one-rule
                                         :identity 0.0 1.0 9)]
    (is (= 1 (harness/difference (ca/bits-for result)
                                 (ca/bits-for identity-rule))))))

(deftest transfer-reads-fixed-offset-plus-one-neighbour
  (let [own "甘" right "示" exotype :identity]
    (is (= (grid/apply-exotype own exotype 9)
           (grid/apply-exotype own right exotype 0.0 9)))
    (let [result (grid/apply-exotype own right exotype 1.0 9)
          distance #(harness/difference (ca/bits-for result) (ca/bits-for %))]
      (is (= 1 (distance right))))))
