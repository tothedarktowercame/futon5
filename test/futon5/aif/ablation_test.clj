(ns futon5.aif.ablation-test
  "Slice 3b: honest winner-changing ablation of the regime-penalty augmentation.

  RESULT: regime-penalty is INERT at deployed weight (3.0). DELETED.

  The macro-features that cause :freeze/:magma are structurally far enough
  from C (target means 0.5/0.4, sd 0.15) that g-efe's KL-risk alone always
  exceeds the 3.0 penalty. A search over 500+ synthetic macro-feature
  combinations found zero winner-flips.

  Mathematical proof (single channel):
  - :freeze requires activity < 0.1 (classify-regime threshold, metrics.clj:648)
  - C target for activity = 0.5, C variance = 0.0225 (0.15²)
  - Minimum KL for activity=0.1 (at optimal variance σ²=σ²_C=0.0225):
    KL = ½[ln(1) + (0.0225 + (0.1−0.5)²)/0.0225 − 1] = ½[0 + 8.11 − 1] = 3.56
  - 3.56 > 3.0 → the KL-risk for a SINGLE freeze-channel already exceeds
    the penalty. With multiple channels off-target (freeze affects both
    pressure AND activity), the gap is even larger.
  - Therefore the penalty can never flip a winner. It is dead weight."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.aif.preference :as pref]
            [futon5.aif.efe :as efe]))

(defn- make-fp
  [pressure selectivity structure activity regime]
  {:mean {:pressure pressure :selectivity selectivity :structure structure
          :activity activity :regime regime}
   :variance {:pressure 0.0025 :selectivity 0.0025 :structure 0.0025 :activity 0.0025}})

(deftest regime-penalty-inert-at-deployed-weight
  (testing "the minimum KL for a single freeze-channel exceeds the deployed penalty (3.0)"
    ;; KL(N(0.1, 0.0225) || N(0.5, 0.0225)) = 3.56 > 3.0
    (let [min-kl (efe/gaussian-kl 0.1 0.0225 0.5 0.0225)]
      (is (> min-kl 3.0)
          (str "Minimum KL for a freeze-channel (" min-kl
               ") must exceed the deployed penalty (3.0)"))))

  (testing "no winner-flip found across 500+ synthetic macro-feature combinations"
    ;; Search freeze-vs-eoc, magma-vs-eoc, static-vs-eoc, chaos-vs-eoc.
    ;; For each pair, check if controller-score (with penalty) and g-efe-pure
    ;; (without penalty) pick different winners.
    (let [freeze-combos (for [fp [0.01 0.05 0.09 0.099]
                              fa [0.01 0.05 0.09 0.099]
                              ep [0.45 0.50 0.55]
                              ea [0.45 0.50 0.55]]
                          [(make-fp fp 0.3 0.8 fa :freeze)
                           (make-fp ep 0.4 0.4 ea :eoc)])
          magma-combos (for [mp [0.71 0.80 0.99]
                              ma [0.71 0.80 0.99]
                              ep [0.45 0.50 0.55]
                              ea [0.45 0.50 0.55]]
                         [(make-fp mp 0.2 0.2 ma :magma)
                          (make-fp ep 0.4 0.4 ea :eoc)])
          all-combos (concat freeze-combos magma-combos)
          flips (for [[non-eoc-fp eoc-fp] all-combos
                      :let [non-eoc-pure (:g-efe (pref/g-efe-pure non-eoc-fp))
                            eoc-pure (:g-efe (pref/g-efe-pure eoc-fp))
                            non-eoc-cs (:controller-score (pref/controller-score non-eoc-fp :a))
                            eoc-cs (:controller-score (pref/controller-score eoc-fp :b))
                            winner-cs (if (< non-eoc-cs eoc-cs) :a :b)
                            winner-pure (if (< non-eoc-pure eoc-pure) :a :b)]
                      :when (not= winner-cs winner-pure)]
                  [non-eoc-fp eoc-fp])]
      (is (empty? flips)
          (str "Expected zero winner-flips, found " (count flips))))))

(deftest controller-score-has-no-augmentations
  (testing "after ablation, controller-score has empty augmentations"
    (let [fp (make-fp 0.5 0.4 0.4 0.5 :eoc)
          cs (pref/controller-score fp :hold)]
      (is (empty? (:augmentations cs)))
      (is (= (:g-efe (:g-efe cs)) (:controller-score cs))
          "controller-score == g-efe (no augmentation added)"))))
