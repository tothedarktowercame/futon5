(ns futon5.mmca.exotype-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.mmca.exotype :as exotype]))

(defn- phenotype-family-at [phe-row phe-next idx]
  (#'futon5.mmca.exotype/phenotype-family-at phe-row phe-next idx))

(defn- evidence->phenotype-family [evidence]
  (#'futon5.mmca.exotype/evidence->phenotype-family evidence))

(deftest evidence-phenotype-family-normalization
  (testing "evidence coerces to a 4-bit phenotype family"
    (is (= "0101" (evidence->phenotype-family "0101")))
    (is (= "1010" (evidence->phenotype-family [1 0 1 0])))
    (is (= "1010" (evidence->phenotype-family [true false true false])))
    (is (= "1000" (evidence->phenotype-family [true])))
    (is (= "1010" (evidence->phenotype-family "101010")))
    (is (= "0000" (evidence->phenotype-family nil)))))

(deftest phenotype-family-consistency
  (testing "local evolution uses parent+child phenotype family"
    (let [genotype (apply str (take 5 (map :sigil (ca/sigil-entries))))
          phenotype "01010"
          prev-genotype genotype
          contexts (atom [])
          spy-fn (fn [sigil _pred _next context]
                   (swap! contexts conj context)
                   {:sigil sigil})
          kernel-fn-map (->> (keys ca/kernels)
                             (map (fn [k] [k spy-fn]))
                             (into {}))]
      (exotype/evolve-string-local genotype phenotype prev-genotype kernel-fn-map)
      (let [phe-next (ca/evolve-phenotype-against-genotype genotype phenotype)
            expected (phenotype-family-at phenotype phe-next 2)
            actual (:phenotype-context (nth @contexts 2))]
        (is (= (count genotype) (count @contexts)))
        (is (= expected actual))))))
