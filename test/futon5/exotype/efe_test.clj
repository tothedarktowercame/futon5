(ns futon5.exotype.efe-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.exotype.efe :as efe]))

(def observation {:activity 0.5 :diversity (/ 2.0 3.0)})

(deftest lambda-endpoints-preserve-slice-two-scores
  (let [full (efe/score-policy :efe-full :builder observation)
        full-one (efe/score-policy :efe-full :builder observation {:lambda 1.0})
        full-zero (efe/score-policy :efe-full :builder observation {:lambda 0.0})
        no-conatus (efe/score-policy :efe-no-conatus :builder observation)]
    (is (= full full-one))
    (is (= 1.0 (:lambda full)))
    (is (= 0.0 (:lambda no-conatus)))
    (is (= (:total full-zero) (:total no-conatus)))
    (is (= (:total full)
           (+ (:risk full) (:ambiguity full) (:conatus full))))))

(deftest risk-preference-override-is-diagnostic-only
  (let [default (efe/score-policy :efe-full :builder observation)
        alternative (efe/score-policy :efe-full :builder observation
                                      {:rule-change-preference 0.6})]
    (testing "the override changes risk but not the frozen prediction"
      (is (not= (:risk default) (:risk alternative)))
      (is (= (:prediction default) (:prediction alternative))))))
