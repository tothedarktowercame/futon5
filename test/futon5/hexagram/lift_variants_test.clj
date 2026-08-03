(ns futon5.hexagram.lift-variants-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.hexagram.lift :as lift]
            [futon5.hexagram.lift-variants :as variants]))

(def sample-bits
  (vec (take 36 (cycle [0 1 1 0 1]))))

(deftest all-variants-share-one-deterministic-interface
  (doseq [variant variants/variants]
    (testing (name variant)
      (let [a (variants/exotype->hexagram variant sample-bits 73)
            b (variants/exotype->hexagram variant sample-bits 73)]
        (is (= a b))
        (is (= 6 (count (:lines a))))
        (is (<= 1 (:number a) 64)))))
  (is (= (lift/exotype->hexagram sample-bits)
         (variants/exotype->hexagram :eigen-sign sample-bits 73))))

(deftest unknown-variant-is-rejected
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"unknown lift variant"
                        (variants/exotype->hexagram :not-a-lift sample-bits))))
