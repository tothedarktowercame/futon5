(ns futon5.ct.tensor-diagrams-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ct.tensor :as tensor]
            [futon5.ct.tensor-diagrams :as diagrams]
            [futon5.ct.tensor-exec :as exec]))

(deftest diagram-library-exposes-canonical-pipelines
  (testing "expected ids are present"
    (let [ids (set (diagrams/available-diagrams))]
      (is (contains? ids :sigil-step))
      (is (contains? ids :sigil-step-gated))
      (is (contains? ids :sigil-step-with-branch))
      (is (contains? ids :tensor-transfer-pack))))

  (testing "diagram lookup returns executable diagrams"
    (let [row "一二三四五六七八"
          out (exec/run-diagram (diagrams/diagram :sigil-step)
                                {:sigil-row row
                                 :rule-sigil "手"
                                 :step-opts {:wrap? false :boundary-bit 0}})]
      (is (= (tensor/step-sigil-row row "手" {:wrap? false :boundary-bit 0})
             (:new-row out)))))

  (testing "unknown ids fail with available options"
    (try
      (diagrams/diagram :does-not-exist)
      (is false "Expected unknown diagram to throw")
      (catch Exception e
        (let [data (ex-data e)]
          (is (= :does-not-exist (:id data)))
          (is (seq (:available data))))))))
