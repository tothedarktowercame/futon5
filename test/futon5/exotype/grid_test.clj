(ns futon5.exotype.grid-test
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.slice-harness :as harness]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.selection :as selection]))

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
  (testing "the vocabulary is now all 12 kinds (S0b/H5 widening, TN 42.3)"
    (is (= [:builder :collapser :chaos :identity
            :even4 :even8 :even1 :odd53 :fix2 :fix3 :fix4 :fix6]
           grid/exotype-kinds)))
  (testing "propagators also carries the P2 probe kinds and E0b controls (NOT default)"
    (is (= #{:builder :collapser :chaos :identity
             :even4 :even8 :even1 :odd53           ; absorbing axis (16/8/2/0)
             :fix2 :fix3 :fix4 :fix6              ; rate axis (fix 2,3,4,6)
             :even44 :odd332}                     ; E0b cycle-type controls (4,4) and (3,3,2)
           (set (keys grid/propagators)))))
  (let [neighbourhoods #{"000" "001" "010" "100" "011" "101" "110" "111"}]
    (is (every? #(and (= neighbourhoods (set (keys %)))
                      (= neighbourhoods (set (vals %))))
                (vals grid/propagators)))))

;; PROVENANCE: grid_q0_baseline.edn was REGENERATED 2026-08-04 when the per-cell
;; seeding defect was fixed (N2, TN-baldwin-reboot.md 2 and 13). This is a DRIFT
;; guard, not a claim that the old values were right; the draws changed on purpose
;; and by approval, so the fixture had to move with them. What changed in this
;; 8-cell/12-step run:
;;   exotypes at t=12  {:chaos 8}       -> {:identity 8}
;;   changed-cells     13               -> 10
;;   phenotype-activity 0.3854          -> 0.4375
;;   genotype-rule-count 7              -> 8
;;   damage {:phenotype 2}              -> {:phenotype 0}
;; Do NOT read the chaos->identity flip as a general result: 8 cells, 12 steps,
;; one seed. It is a fixture, not a measurement.
;;
;; REGENERATED A THIRD TIME 2026-08-04 when the conditional model was re-derived over
;; all 14 propagators (TN-baldwin-reboot.md 56). Only :changed-steps (6 -> 7) and
;; :changed-cells (11 -> 12) moved; checkpoints, entropy, autocorrelation, activity,
;; rule count and all three damage figures are byte-identical. A small, localised shift
;; is what a modest change in the prediction rows should produce, and seeing the rest
;; hold still is the check that it was modest.
;;
;; REGENERATED AGAIN 2026-08-04 when the DERIVED conditional model became
;; `predict`'s default (TN-baldwin-reboot.md 29). The EFE path selects different
;; exotypes under a model that is no longer worse-than-constant, so the run
;; legitimately differs. Same caveat: this is a fixture, not a measurement.
(deftest zero-transfer-reproduces-stored-run-byte-identically
  (let [config {:width 8 :steps 12 :lambda 0.55 :mu 0.1 :tau 0.3
                :prevalence-radius 1 :eig-model :legacy :eig-coefficient 0.0
                :damage-steps 5 :checkpoints [0 12] :transfer-fraction 0.0
                :blend-strength 0.0 :selection-strength 0.0
                :fitness-kind :preferences :write-back? true}
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

(defn- baldwin-state []
  {:arm :heterogeneous-fixed
   :seed 29
   :time 0
   :genotype (vec (repeat 8 (ca/sigil-for "11001100")))
   :phenotype "01010101"
   :exotypes (vec (repeat 8 :chaos))
   :selection-strength 0.0
   :fitness-kind :preferences
   :write-back? false})

(deftest baldwin-expression-is-real-but-never-written-back
  (let [state (baldwin-state)
        advanced (grid/step state)]
    (testing "without selection, no genotype changes"
      (is (= (:genotype state) (:genotype advanced))))
    (testing "the exotype still changes transient expression"
      (is (not= (:expressed advanced) (:genotype advanced))))
    (testing "the next phenotype reads the current transient expression"
      (let [witness (assoc advanced :phenotype "00000011")
            next-step (grid/step witness)
            counterfactual (grid/step (assoc witness :expressed (:genotype witness)))]
        (is (not= (:phenotype next-step) (:phenotype counterfactual)))))))

(deftest baldwin-genotype-assignment-is-routed-only-through-selection
  (let [sentinel (vec (repeat 8 (ca/sigil-for "11111111")))]
    (with-redefs [selection/advance
                  (fn [_]
                    {:genotype sentinel
                     :window (selection/empty-window 8)
                     :selected? true})]
      (let [advanced (grid/step (assoc (baldwin-state) :selection-strength 1.0))]
        (is (= sentinel (:genotype advanced)))
        (is (not= sentinel (:expressed advanced)))))))
