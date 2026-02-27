(ns futon5.tpg.compare-test
  (:require [cheshire.core :as json]
            [clojure.test :refer [deftest is testing]]
            [futon5.tpg.compare :as compare]
            [futon5.tpg.core :as tpg]
            [futon5.tpg.verifiers :as verifiers]))

(deftest smt-score-parses-json-output
  (testing "SMT details uses structured JSON fields"
    (let [analysis-json (json/generate-string
                         {"unreachable_operators" ["expansion" "momentum"]
                          "dead_programs" [{"program_id" "p-dead"}]
                          "verifier_satisfiable" true})]
      (with-redefs [futon5.tpg.compare/run-python-tool
                    (fn [_ _]
                      {:ok? true
                       :out analysis-json
                       :python-path "python3"
                       :python-source :path})]
        (let [details (compare/smt-score-details (tpg/seed-tpg-simple) verifiers/default-spec)]
          (is (nil? (:error details)))
          (is (= 2 (:unreachable-count details)))
          (is (= 1 (:dead-program-count details)))
          (is (true? (:verifier-satisfiable details)))
          (is (< (Math/abs (- 0.825 (:score details))) 1e-9)))))))

(deftest jax-refine-injects-weights
  (testing "JAX refinement updates program weights and bias in returned TPG"
    (let [base (tpg/seed-tpg-simple)
          refined-w [0.11 0.12 0.13 0.14 0.15 0.16]
          jax-json (json/generate-string
                    {"improvement" 0.25
                     "original_satisfaction" 0.45
                     "refined_satisfaction" 0.70
                     "refined_weights" {"root" {"0" {"weights" refined-w
                                                     "bias" 1.25}}}})]
      (with-redefs [futon5.tpg.compare/run-python-tool
                    (fn [_ _]
                      {:ok? true
                       :out jax-json
                       :python-path "python3"
                       :python-source :path})]
        (let [result (compare/jax-refine-weights base [] verifiers/default-spec)
              updated-program (-> result :tpg :teams first :programs first)]
          (is (= refined-w (:weights updated-program)))
          (is (= 1.25 (:bias updated-program)))
          (is (= 1 (get-in result [:refinement-stats :programs-updated])))
          (is (= 1 (get-in result [:refinement-stats :weights-updated])))
          (is (= 1 (get-in result [:refinement-stats :bias-updated])))
          (is (< (Math/abs (- 0.25 (:improvement result))) 1e-9)))))))
