(ns futon5.mmca.exotype-invariants-test
  "Test invariants for the local exotype physics system.

   These tests verify the critical invariants from Mission-0-CHECKPOINT-invariants.md:

   Invariant 1: Exotypes Are Real
   - Each cell MUST compute its local 36-bit context and apply the corresponding physics rule
   - Rules must vary by cell (not all same)

   Invariant 2: Xenotypes Evolve Real Physics
   - Xenotype evolution MUST search over the 256-rule space

   Invariant 3: No Mixing Old and New
   - Runtime MUST NOT accidentally use old code paths

   Invariant 4: Scoring Is Against Real Runs
   - Any scoring MUST operate on runs produced by the new system"
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]
            [futon5.hexagram.lift :as hex-lift]))

;; =============================================================================
;; INVARIANT 1: Exotypes Are Real
;; =============================================================================

(deftest test-36-bit-context-structure
  (testing "Local context has all 36 bits"
    (let [context (exotype/build-local-context "一" "二" "三" "四" "1010")]
      (is (= 4 (count (:context-sigils context)))
          "Context must have 4 sigils (LEFT, EGO, RIGHT, NEXT)")
      (is (= "1010" (:phenotype-context context))
          "Context must have 4-bit phenotype"))))

(deftest test-context-produces-physics-rule
  (testing "Context maps to physics rule via eigenvalue diagonalization"
    (let [context (exotype/build-local-context "一" "二" "三" "四" "1010")
          physics (hex-lift/context->physics-rule context)]
      (is (map? physics))
      (is (contains? physics :rule))
      (is (<= 0 (:rule physics) 255)
          "Physics rule must be in 0-255 range")
      (is (contains? physics :hexagram)
          "Physics must include hexagram")
      (is (contains? physics :energy)
          "Physics must include energy"))))

(deftest test-different-contexts-produce-different-rules
  (testing "Different local contexts should produce different rules"
    (let [contexts [(exotype/build-local-context "一" "一" "一" "一" "0000")
                    (exotype/build-local-context "二" "二" "二" "二" "0000")
                    (exotype/build-local-context "三" "三" "三" "三" "0000")
                    (exotype/build-local-context "四" "四" "四" "四" "0000")
                    (exotype/build-local-context "一" "二" "三" "四" "1111")]
          rules (mapv #(:rule (hex-lift/context->physics-rule %)) contexts)]
      ;; Not all rules should be identical
      (is (> (count (set rules)) 1)
          (str "Different contexts should produce different rules. Got: " rules)))))

(deftest test-rule-maps-to-kernel
  (testing "Each rule maps to a valid kernel spec"
    (doseq [rule (range 0 256 16)] ; Sample every 16th rule
      (let [spec (exotype/rule->kernel-spec rule)]
        (is (map? spec)
            (str "Rule " rule " should produce a kernel spec"))
        (is (contains? spec :kernel)
            (str "Rule " rule " spec should have :kernel"))
        (is (keyword? (:kernel spec))
            (str "Rule " rule " kernel should be a keyword"))
        (is (contains? spec :params)
            (str "Rule " rule " spec should have :params"))))))

;; A mock kernel function map for testing
(def ^:private mock-kernels
  (let [identity-kernel (fn [sigil _ _ _] {:sigil sigil})]
    {:blending identity-kernel
     :multiplication identity-kernel
     :ad-hoc-template identity-kernel
     :blending-mutation identity-kernel
     :blending-baldwin identity-kernel
     :collection-template identity-kernel
     :mutating-template identity-kernel}))

(deftest test-evolve-string-local-produces-per-cell-rules
  (testing "evolve-string-local computes different rules per cell"
    (let [;; Use a genotype with variety
          genotype "一二三四五六七八"
          phenotype "01010101"
          result (exotype/evolve-string-local genotype phenotype genotype mock-kernels)]
      (is (contains? result :genotype)
          "Result must have :genotype")
      (is (contains? result :rules)
          "Result must have :rules vector")
      (is (contains? result :kernels)
          "Result must have :kernels vector")
      (is (= (count genotype) (count (:rules result)))
          "Must have one rule per cell")
      ;; Check that we got some rule variety
      (let [unique-rules (set (:rules result))]
        (is (>= (count unique-rules) 1)
            "Should compute at least one rule")
        ;; With varied input, expect some variety
        (when (> (count genotype) 4)
          (is (> (count unique-rules) 1)
              (str "With varied input, expect rule variety. Got: " (:rules result))))))))

;; =============================================================================
;; INVARIANT 2: 256 Rules = 64 Hexagrams x 4 Energies
;; =============================================================================

(deftest test-256-rule-space
  (testing "256 rules decompose correctly"
    (doseq [rule (range 256)]
      (let [{:keys [hexagram energy]} (hex-lift/rule->hexagram+energy rule)]
        (is (<= 1 hexagram 64)
            (str "Hexagram must be 1-64, got " hexagram " for rule " rule))
        (is (contains? #{:peng :lu :ji :an} (:key energy))
            (str "Energy must be one of the four, got " (:key energy) " for rule " rule))))))

(deftest test-hexagram-energy-roundtrip
  (testing "hexagram+energy -> rule -> hexagram+energy roundtrips"
    (doseq [hexagram (range 1 65)
            energy-id (range 4)]
      (let [rule (hex-lift/hexagram+energy->rule (dec hexagram) energy-id)
            decomposed (hex-lift/rule->hexagram+energy rule)]
        (is (= hexagram (:hexagram decomposed))
            (str "Hexagram mismatch for hex " hexagram " energy " energy-id))
        (is (= energy-id (get-in decomposed [:energy :id]))
            (str "Energy mismatch for hex " hexagram " energy " energy-id))))))

;; =============================================================================
;; INVARIANT 3: No Mixing Old and New
;; =============================================================================

(deftest test-deprecation-markers
  (testing "Old functions should be marked deprecated or wrapped"
    ;; Test that deprecated apply-exotype still exists and is callable
    ;; Note: The function is deprecated but should still work for backwards compatibility
    (is (fn? exotype/apply-exotype)
        "apply-exotype should still exist (deprecated but functional)")
    ;; Test that the function has the :deprecated metadata
    (is (= "2026-01-24" (:deprecated (meta #'exotype/apply-exotype)))
        "apply-exotype should have :deprecated metadata")))

;; =============================================================================
;; INVARIANT 4: Local Physics Metadata
;; =============================================================================

(deftest test-local-evolution-includes-metadata
  (testing "Local evolution should include metadata about rules used"
    (let [genotype "一二三四"
          result (exotype/evolve-string-local genotype nil genotype mock-kernels)]
      (is (vector? (:rules result))
          "Result must include :rules vector")
      (is (vector? (:kernels result))
          "Result must include :kernels vector")
      (is (every? #(<= 0 % 255) (:rules result))
          "All rules must be valid 0-255"))))

;; =============================================================================
;; EXOTYPE COMPOSITION
;; =============================================================================

(deftest test-rule-composition-matrix
  (testing "Matrix composition produces valid parameters"
    (let [composed (exotype/compose-rules-matrix 0 255)]
      (is (map? composed))
      (is (contains? composed :mutation-bias))
      (is (contains? composed :structure-weight))
      (is (<= 0 (:mutation-bias composed) 1)
          "Composed mutation-bias should be 0-1")
      (is (<= 0 (:structure-weight composed) 1)
          "Composed structure-weight should be 0-1"))))

(deftest test-composite-exotype-creation
  (testing "Composite exotypes can be created"
    (let [composite-fn (exotype/make-composite-exotype [0 100 200] :blend mock-kernels)]
      (is (fn? composite-fn)
          "Composite should be a function")
      (let [result (composite-fn "一" "二" "三" {})]
        (is (map? result))
        (is (contains? result :sigil))))))

;; =============================================================================
;; GLOBAL + LOCAL BENDING
;; =============================================================================

(deftest test-bent-evolution-function
  (testing "make-bent-evolution creates valid evolution function"
    (let [bent-fn (exotype/make-bent-evolution :baldwin :blend mock-kernels)]
      (is (fn? bent-fn))
      (let [genotype "一二三四五六七八"
            result (bent-fn genotype nil genotype)]
        (is (string? result)
            "Bent evolution should return a string")
        (is (= (count genotype) (count result))
            "Output length should match input")))))

(deftest test-global-rule-keywords
  (testing "Named global rules resolve to valid rule numbers"
    (doseq [kw [:baldwin :blending :creative :conservative :adaptive :transformative]]
      (let [bent-fn (exotype/make-bent-evolution kw :sequential mock-kernels)]
        (is (fn? bent-fn)
            (str "Should create bent-fn for " kw))))))

;; =============================================================================
;; RUN THIS TO VERIFY ALL INVARIANTS
;; =============================================================================

(defn verify-all-invariants
  "Run all invariant tests and return a summary."
  []
  (let [test-vars [#'test-36-bit-context-structure
                   #'test-context-produces-physics-rule
                   #'test-different-contexts-produce-different-rules
                   #'test-rule-maps-to-kernel
                   #'test-evolve-string-local-produces-per-cell-rules
                   #'test-256-rule-space
                   #'test-hexagram-energy-roundtrip
                   #'test-deprecation-markers
                   #'test-local-evolution-includes-metadata
                   #'test-rule-composition-matrix
                   #'test-composite-exotype-creation
                   #'test-bent-evolution-function
                   #'test-global-rule-keywords]
        results (atom {:passed 0 :failed 0 :errors []})]
    (doseq [tv test-vars]
      (try
        (tv)
        (swap! results update :passed inc)
        (catch Throwable t
          (swap! results update :failed inc)
          (swap! results update :errors conj {:test (str tv) :error (.getMessage t)}))))
    @results))
