(ns futon5.exotype.efe-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.chain-risk :as chain-risk]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as policy-epistemic]
            [futon5.xenotype.generator :as gen]))

(def observation {:activity 0.5 :diversity (/ 2.0 3.0)})

(deftest chain-risk-table-has-the-required-domain-and-identity-column
  (let [identity-column (mapv #(chain-risk/risk :identity %) (range 256))]
    (is (= 3072 (count chain-risk/table)))
    (is (= 1 (count (distinct identity-column))))
    (is (= 1.8971199614280152 (first identity-column)))
    (is (every? #(and (Double/isFinite (double %)) (not (neg? %)))
                (vals chain-risk/table)))))

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

(deftest policy-epistemic-table-is-complete-and-policy-specific
  (is (= 144 (count policy-epistemic/pair-table)))
  (doseq [kind grid/exotype-kinds]
    (is (zero? (policy-epistemic/pair-value kind kind))))
  (is (pos? (policy-epistemic/pair-value :identity :odd53)))
  (is (not= (policy-epistemic/pair-value :identity :odd53)
            (policy-epistemic/pair-value :identity :builder))))

(deftest policy-epistemic-table-matches-the-exhaustive-common-draw-condition
  (doseq [own grid/exotype-kinds
          candidate grid/exotype-kinds]
    (let [a (gen/sigma-positional (get grid/propagators own))
          b (gen/sigma-positional (get grid/propagators candidate))
          hits (for [byte (range 256)
                     k (range 8)
                     :let [bit #(bit-test byte (- 7 %))
                           ak (nth a k)
                           bk (nth b k)]
                     :when (and (not= ak bk)
                                (or (= (bit ak) (bit k))
                                    (= (bit bk) (bit k))))]
                 1)
          exhaustive (/ (count hits) (* 256.0 8.0))]
      (is (= exhaustive (policy-epistemic/pair-value own candidate))
          (str own " -> " candidate)))))

(deftest blend-epistemic-value-matches-eig-v2-reference
  (testing "the port agrees with scripts/exotype_eig_v2.clj on frozen states"
    (doseq [[seed index expected]
            [[17 0 0.18181818181818182]
             [17 7 0.5454545454545454]
             [991 13 0.18181818181818182]
             [20260803 23 0.45454545454545453]]]
      (let [width 24
            state (ca/with-seed seed
                    {:genotype (vec (ca/random-sigil-string width))
                     :phenotype (ca/random-phenotype-string width)})
            actual (policy-epistemic/blend-value state index)]
        (is (= expected actual)
            (str "reference drift at seed " seed " index " index))
        (is (<= 0.0 actual 1.0))))))

(deftest epistemic-default-is-inert-and-matched-churn-is-nonspecific
  (let [base (efe/score-policy :efe-full :builder observation)
        explicit-zero (efe/score-policy :efe-full :builder observation
                                        {:epistemic-coefficient 0.0
                                         :epistemic-value 0.75})
        epistemic (efe/score-policy :efe-full :builder observation
                                    {:epistemic-coefficient 2.0
                                     :epistemic-value 0.75})
        hold (efe/score-policy :efe-full :builder observation
                               {:adoption-bonus 0.4 :adoption? false})
        adopt (efe/score-policy :efe-full :builder observation
                                {:adoption-bonus 0.4 :adoption? true})]
    (is (= (:total base) (:total explicit-zero)))
    (is (= base (dissoc explicit-zero :epistemic-value :epistemic)))
    (is (= (- (:total base) 1.5) (:total epistemic)))
    (is (= (:total base) (:total hold)))
    (is (= (- (:total base) 0.4) (:total adopt)))))

(deftest score-controls-survive-synchronous-steps
  (let [state {:arm :efe-full :seed 1 :time 0 :lambda 0.2
               :rule-change-preference 0.4
               :apply-probability 0.15
               :epistemic-coefficient 2.0
               :adoption-bonus 0.3
               :genotype ["一" "一" "一"]
               :previous-genotype ["一" "一" "一"]
               :phenotype "010"
               :exotypes [:builder :collapser :identity]}
        advanced (efe/step state)]
    (is (= 0.2 (:lambda advanced)))
    (is (= 0.4 (:rule-change-preference advanced)))
    (is (= 0.15 (:apply-probability advanced)))
    (is (= 2.0 (:epistemic-coefficient advanced)))
    (is (= 0.3 (:adoption-bonus advanced)))))
