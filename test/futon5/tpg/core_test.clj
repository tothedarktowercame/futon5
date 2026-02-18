(ns futon5.tpg.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.tpg.core :as tpg]
            [futon5.tpg.diagnostics :as diag]))

;; =============================================================================
;; DIAGNOSTICS
;; =============================================================================

(deftest diagnostic-vector-test
  (testing "diagnostic vector has correct dimension"
    (let [d (diag/compute-diagnostic "一二三四五六七八" "一二三四五六七八" nil
                                      ["一二三四五六七八"])]
      (is (= 6 (alength ^doubles (:vector d))))
      (is (= 6 (count (:named d))))))

  (testing "all values in [0, 1]"
    (let [d (diag/compute-diagnostic "一二三四五六七八" "八七六五四三二一" "01010101"
                                      ["一二三四五六七八" "八七六五四三二一" "一二三四五六七八"])]
      (doseq [[k v] (:named d)]
        (is (<= 0.0 v 1.0) (str k " = " v " is out of range")))))

  (testing "identical generations produce low change, high autocorr"
    (let [g "一二三四五六七八"
          d (diag/compute-diagnostic g g nil [g g g])]
      (is (< (:change (:named d)) 0.01))
      (is (> (:autocorr (:named d)) 0.99))))

  (testing "maximally different generations produce high change"
    (let [d (diag/compute-diagnostic "一一一一一一一一" "二二二二二二二二" nil
                                      ["一一一一一一一一" "二二二二二二二二"])]
      (is (> (:change (:named d)) 0.5)))))

;; =============================================================================
;; PROGRAM EXECUTION
;; =============================================================================

(deftest program-bid-test
  (testing "bid is linear combination of weights and diagnostics"
    (let [program (tpg/make-program :p1 [1.0 0.0 0.0 0.0 0.0 0.0] 0.0
                                     :operator :expansion)
          diag-hi (double-array [0.9 0.5 0.5 0.5 0.5 0.5])
          diag-lo (double-array [0.1 0.5 0.5 0.5 0.5 0.5])]
      (is (> (tpg/program-bid program diag-hi)
             (tpg/program-bid program diag-lo)))))

  (testing "bias shifts bid"
    (let [p1 (tpg/make-program :p1 [0.0 0.0 0.0 0.0 0.0 0.0] 1.0
                                :operator :expansion)
          p2 (tpg/make-program :p2 [0.0 0.0 0.0 0.0 0.0 0.0] -1.0
                                :operator :conservation)
          d (double-array [0.5 0.5 0.5 0.5 0.5 0.5])]
      (is (> (tpg/program-bid p1 d) (tpg/program-bid p2 d))))))

;; =============================================================================
;; TEAM EXECUTION
;; =============================================================================

(deftest team-execution-test
  (testing "team selects highest-bidding program"
    (let [team (tpg/make-team
                :test
                ;; p1: high entropy → expansion (weight +1, bias -0.5 → wins when H > 0.5)
                ;; p2: low entropy → conservation (weight -1, bias +0.5 → wins when H < 0.5)
                [(tpg/make-program :p1 [1.0 0.0 0.0 0.0 0.0 0.0] -0.5
                                    :operator :expansion)
                 (tpg/make-program :p2 [-1.0 0.0 0.0 0.0 0.0 0.0] 0.5
                                    :operator :conservation)])
          ;; High entropy → p1 wins (positive weight on dim 0)
          d-hi (double-array [0.9 0.5 0.5 0.5 0.5 0.5])
          ;; Low entropy → p2 wins (negative weight on dim 0)
          d-lo (double-array [0.1 0.5 0.5 0.5 0.5 0.5])]
      (is (= :expansion (:target (tpg/execute-team team d-hi))))
      (is (= :conservation (:target (tpg/execute-team team d-lo)))))))

;; =============================================================================
;; TPG ROUTING
;; =============================================================================

(deftest simple-routing-test
  (testing "simple TPG routes to correct operator"
    (let [tpg (tpg/seed-tpg-simple)
          ;; High entropy diagnostic
          d-hi-entropy (double-array [0.9 0.1 0.5 0.5 0.5 0.5])
          result (tpg/route tpg d-hi-entropy)]
      (is (keyword? (:operator-id result)))
      (is (vector? (:route result)))
      (is (integer? (:depth result)))
      (is (not (:fallback? result))))))

(deftest hierarchical-routing-test
  (testing "hierarchical TPG routes through intermediate teams"
    (let [tpg (tpg/seed-tpg-hierarchical)
          ;; Frozen regime: low entropy, low change
          d-frozen (double-array [0.1 0.1 0.9 0.2 0.3 0.3])
          result-frozen (tpg/route tpg d-frozen)
          ;; Chaotic regime: high entropy, high change
          d-chaotic (double-array [0.9 0.9 0.1 0.8 0.3 0.7])
          result-chaotic (tpg/route tpg d-chaotic)]

      ;; Both should reach an operator without fallback
      (is (not (:fallback? result-frozen)) (str "frozen fallback: " result-frozen))
      (is (not (:fallback? result-chaotic)) (str "chaotic fallback: " result-chaotic))

      ;; Should have depth > 0 (routed through intermediate team)
      (is (> (count (:route result-frozen)) 1))
      (is (> (count (:route result-chaotic)) 1)))))

;; =============================================================================
;; TPG VALIDATION
;; =============================================================================

(deftest validation-test
  (testing "seed TPGs are valid"
    (let [v1 (tpg/validate-tpg (tpg/seed-tpg-simple))
          v2 (tpg/validate-tpg (tpg/seed-tpg-hierarchical))]
      (is (:valid? v1) (str "simple errors: " (:errors v1)))
      (is (:valid? v2) (str "hierarchical errors: " (:errors v2)))))

  (testing "empty TPG is invalid"
    (let [bad-tpg {:teams [] :config {}}
          v (tpg/validate-tpg bad-tpg)]
      (is (not (:valid? v)))))

  (testing "unreachable team is invalid"
    (let [bad-tpg (tpg/make-tpg
                   "bad"
                   [(tpg/make-team
                     :root
                     ;; All programs route to team :nowhere
                     [(tpg/make-program :p1 [0.0 0.0 0.0 0.0 0.0 0.0] 0.0
                                         :team :nowhere)])]
                   {:root-team :root})
          v (tpg/validate-tpg bad-tpg)]
      (is (not (:valid? v))))))

;; =============================================================================
;; OPERATOR BRIDGE
;; =============================================================================

(deftest operator-bridge-test
  (testing "all operators produce valid rule numbers"
    (doseq [{:keys [operator-id]} tpg/operator-table]
      (let [rule (tpg/operator->global-rule operator-id)]
        (is (some? rule) (str "No rule for " operator-id))
        (is (<= 0 rule 255) (str operator-id " → rule " rule " out of range")))))

  (testing "all operators have bend modes"
    (doseq [{:keys [operator-id]} tpg/operator-table]
      (is (#{:sequential :blend :matrix} (tpg/operator->bend-mode operator-id))
          (str "No bend mode for " operator-id)))))

;; =============================================================================
;; END-TO-END: diagnostic → route → operator
;; =============================================================================

(deftest end-to-end-test
  (testing "full pipeline: genotype → diagnostic → route → operator"
    (let [genotype "一二三四五六七八"
          prev "八七六五四三二一"
          history [prev genotype prev]
          diagnostic (diag/compute-diagnostic genotype prev nil history)
          tpg (tpg/seed-tpg-hierarchical)
          result (tpg/route tpg (:vector diagnostic))]
      (is (keyword? (:operator-id result)))
      (is (some? (tpg/operator->global-rule (:operator-id result))))
      (is (some? (tpg/operator->bend-mode (:operator-id result)))))))
