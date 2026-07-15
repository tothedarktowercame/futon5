(ns futon5.aif.forward-test
  "Tests for the MetaCA forward model (R4).

  Acceptance 1: forward-predict's mean equals the run-mmca deterministic
  next-macro-state on a fixed seed.

  NOTE (2026-07-15): this docstring used to read '...since run-mmca is
  non-deterministic (the exotype RNG is seeded but the CA kernel advance uses
  global rand), we verify this by checking the projection of the SAME run-mmca
  call'.  The impurity was known, documented, and DESIGNED AROUND rather than
  fixed — and it was exactly what confounded the aif-vs-null comparison the
  forward model exists to serve.  forward-predict now binds ca/with-seed and is
  genuinely pure, so the acceptance is stated directly and pinned by
  `forward-predict-is-pure` below."
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
    ;; :rotate-up (mix-shift stepped within Z/8).
    (let [state (make-test-state)
          base-shift (long (or (get-in state [:exotype :params :mix-shift]) 0))
          result (forward/forward-predict state :rotate-up
                                          {:generations test-W :seed test-seed})
          after-shift (get-in (:next-state result) [:exotype :params :mix-shift])]
      (is (= (mod (inc base-shift) 8) after-shift)
          ":rotate-up should step mix-shift within Z/8 before the CA step"))))

(deftest forward-predict-actions-have-authority
  ;; THE GATE THAT WAS MISSING (2026-07-15).
  ;;
  ;; The test replaced above asserted "pressure-up increases update-prob" — and
  ;; it PASSED, for the whole life of the tokamak, while :update-prob moved the
  ;; plant by EXACTLY 0.000.  It verified the WIRE and never asked whether the
  ;; field it wrote did anything.  A green test certifying a no-op is the
  ;; cyberant defect in test form: wiring written, never read.
  ;;
  ;; So: assert AUTHORITY, not assignment.  An action must change the plant's
  ;; OBSERVABLE, or it is a no-op wearing a name.  Paired by construction (same
  ;; seed, only the action differs), because unpaired this measures the plant's
  ;; own variance and calls it control.
  (testing "every actuating action changes the plant's observable"
    (let [state (make-test-state)
          opts {:generations test-W :seed test-seed :W test-W :S test-W}
          press (fn [action]
                  (double (or (:pressure (:mean (forward/forward-predict state action opts)))
                              0.5)))
          held (press :hold)]
      (doseq [action [:rotate-up :rotate-down]]
        (is (not= held (press action))
            (str (name action) " must MOVE the plant, not merely set a param. "
                 "If this fails the action is inert and any ablation over it "
                 "passes for free.")))))
  (testing ":hold is inert — that is its job"
    (let [state (make-test-state)
          opts {:generations test-W :seed test-seed :W test-W :S test-W}]
      (is (= (:mean (forward/forward-predict state :hold opts))
             (:mean (forward/forward-predict state :hold opts)))
          ":hold must be deterministic and unchanging"))))

(deftest forward-predict-is-pure
  ;; R4 says "one PURE forward kernel".  It was not pure: run-mmca's :seed drove
  ;; only the exotype rng while the CA dynamics drew from the GLOBAL rand, so the
  ;; result depended on how much RNG the process had already burned.  That
  ;; confounded aif-vs-null directly — the :aif arm burns RNG on rollouts, :null
  ;; does not, so identical action sequences produced different trajectories.
  (testing "forward-predict is a pure function of (state, action, seed)"
    (let [state (make-test-state)
          opts {:generations test-W :seed test-seed :W test-W :S test-W}
          a (:mean (forward/forward-predict state :hold opts))]
      (dotimes [_ 1000] (rand))
      (let [b (:mean (forward/forward-predict state :hold opts))]
        (is (= a b)
            "intervening global rand must not change the result — else every
             paired comparison built on this kernel is confounded")))))

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
