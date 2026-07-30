(ns futon5.mmca.causal-score-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.mmca.causal-score :as causal]))

(defn- fixed-field [rule]
  (vec (repeat 80 rule)))

(defn- close? [expected actual tolerance]
  (<= (Math/abs (- (double expected) (double actual))) tolerance))

(deftest exact-ordered-calibration
  (testing "rules 0, 204, and 90 reproduce the exact published invariants"
    (doseq [[rule expected] [[0 0.0] [204 1.0] [90 8.0]]]
      (let [result (causal/reach (fixed-field rule) (causal/eca-config))]
        (is (= expected (:mean result)) (str "rule " rule))
        (is (= 0.0 (:sd result)) (str "rule " rule " spread"))
        (is (= 40 (:n result)) (str "rule " rule " sample count"))))))

(deftest complex-and-chaotic-calibration
  (testing "seed-sensitive rules reproduce the published four-seed means"
    (doseq [[rule expected expected-sd expected-seed-sd]
            [[110 16.675 9.677856035088423 2.7475746880961514]
             [54 18.3 11.048146381250497 1.3735598518691017]
             [30 36.45 7.3238283389537155 2.8571547618799613]]]
      (let [result (causal/reach (fixed-field rule) (causal/eca-config))]
        (is (close? expected (:mean result) 1.0e-12) (str "rule " rule))
        (is (close? expected-sd (:sd result) 1.0e-12)
            (str "rule " rule " site-level SD"))
        (is (close? expected-seed-sd (:seed-sd result) 1.0e-12)
            (str "rule " rule " between-seed SD"))
        (is (= 40 (:n result)) (str "rule " rule " sample count"))))))

(deftest deterministic-output
  (testing "the same seed, field, configuration, and sites are byte-identical"
    (let [opts {:seeds [2] :sites [0 8]}
          first-run (causal/reach (fixed-field 30) (causal/eca-config) opts)
          second-run (causal/reach (fixed-field 30) (causal/eca-config) opts)]
      (is (= (pr-str first-run) (pr-str second-run))))))

(deftest fork-tapes-stay-aligned-across-a-phenotype-gate
  (testing "both branches consume source draws and separate gate coins"
    (let [calls (atom [])
          cfg {:phenotype-step (fn [_field phenotype] phenotype)
               :field-step
               (fn [{:keys [field phenotype source-draws gate-coins]}]
                 (swap! calls conj {:gate-bit (nth phenotype 0)
                                    :source-draws source-draws
                                    :gate-coins gate-coins})
                 ;; Deliberately branch on the perturbed phenotype. The RNG
                 ;; vectors have already been drawn before this branch.
                 (if (= \1 (nth phenotype 0)) field field))}
          _ (causal/reach (fixed-field 204) cfg {:seeds [0] :sites [0]})
          fork-calls (drop 60 @calls)
          branch-pairs (partition 2 fork-calls)]
      (is (some (fn [[a b]] (not= (:gate-bit a) (:gate-bit b)))
                branch-pairs))
      (is (every? (fn [[a b]]
                    (= (:source-draws a) (:source-draws b)))
                  branch-pairs))
      (is (every? (fn [[a b]]
                    (= (:gate-coins a) (:gate-coins b)))
                  branch-pairs)))))
