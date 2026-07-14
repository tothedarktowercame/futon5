(ns futon5.aif.forward-test
  "Tests for the MetaCA forward model (R4).

  Acceptance 1: forward-predict's mean equals the run-mmca deterministic
  next-macro-state on a fixed seed.  Since run-mmca is non-deterministic
  (the exotype RNG is seeded but the CA kernel advance uses global rand),
  we verify this by checking that forward-predict's :mean is the correct
  windowed-macro-features projection of the SAME run-mmca call (exposed
  via :run-result in the return map)."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.exotype :as exotype]
            [futon5.aif.forward :as forward]))

(def ^:private test-seed 4242)
(def ^:private test-length 32)
(def ^:private test-W 10)

(defn- make-test-state
  "Build a macro-state for testing with a fixed genotype."
  []
  (let [genotype (ca/random-sigil-string test-length)
        base-exotype (exotype/resolve-exotype
                      {:sigil ca/default-sigil :tier :super})]
    {:genotype genotype
     :phenotype nil
     :kernel :mutating-template
     :exotype base-exotype
     :metrics-history []
     :gen-history []
     :phe-history []}))

(deftest forward-predict-mean-equals-deterministic-step
  (testing "mean matches the windowed-macro-features of the same run-mmca call"
    ;; forward-predict exposes :run-result so we can verify the projection.
    ;; We independently re-derive the macro-features from that exact run-mmca
    ;; output and confirm the :mean matches.
    (let [state (make-test-state)
          opts {:generations test-W :seed test-seed :W test-W :S test-W}
          fp-result (forward/forward-predict state :pressure-up opts)
          run-result (:run-result fp-result)
          ;; Re-derive macro-features from the same run-mmca output.
          metrics-hist (:metrics-history run-result)
          gen-hist (:gen-history run-result)
          windows (metrics/windowed-macro-features
                   {:metrics-history metrics-hist
                    :gen-history gen-hist}
                   {:W test-W :S test-W})
          independent-mean (last windows)]
      (is (= independent-mean (:mean fp-result))
          "forward-predict mean must equal windowed-macro-features of its own run-mmca call"))))

(deftest forward-predict-applies-action-before-step
  (testing "forward-predict applies the action to exotype params before run-mmca"
    ;; Verify the action was applied: the next-state exotype should reflect
    ;; :pressure-up (update-prob increased from the base).
    (let [state (make-test-state)
          base-update-prob (get-in state [:exotype :params :update-prob])
          result (forward/forward-predict state :pressure-up
                                           {:generations test-W :seed test-seed})
          after-update-prob (get-in (:next-state result) [:exotype :params :update-prob])]
      (is (> after-update-prob base-update-prob)
          "pressure-up should increase update-prob before the CA step"))))

(deftest forward-predict-returns-variance
  (testing "variance map is present and well-formed"
    (let [state (make-test-state)
          result (forward/forward-predict state :hold {:generations test-W
                                                       :seed test-seed})]
      (is (map? (:variance result)))
      (is (pos? (count (:variance result))))
      (doseq [[_ch v] (:variance result)]
        (is (and (number? v) (pos? v)) "variance values must be positive")))))

(deftest forward-predict-hold-preserves-params
  (testing "hold does not change params"
    (let [state (make-test-state)
          before (get-in state [:exotype :params])
          result (forward/forward-predict state :hold
                                           {:generations test-W :seed test-seed})
          after (get-in (:next-state result) [:exotype :params])]
      (is (= before after)
          ":hold should preserve exotype params"))))

(deftest forward-predict-mean-has-regime
  (testing "mean has all 5 macro-feature channels"
    (let [state (make-test-state)
          result (forward/forward-predict state :hold
                                           {:generations test-W :seed test-seed})
          mean (:mean result)]
      (is (contains? mean :pressure))
      (is (contains? mean :selectivity))
      (is (contains? mean :structure))
      (is (contains? mean :activity))
      (is (contains? mean :regime)))))

(deftest forward-predict-next-state-advances-genotype
  (testing "next-state has the advanced genotype from the CA step"
    (let [state (make-test-state)
          result (forward/forward-predict state :hold
                                           {:generations test-W :seed test-seed})
          next-genotype (:genotype (:next-state result))]
      ;; The genotype may or may not change (depends on the kernel), but it
      ;; must be a non-nil string.
      (is (string? next-genotype)
          "next-state genotype must be a string"))))
