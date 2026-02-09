(ns futon5.healthcheck-test
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.healthcheck :as hc]))

(deftest required-files-present
  (testing "Required resources exist on disk (settled-state ratchet)"
    (let [{:keys [ok? missing root]} (hc/check-required-files)]
      (is ok? (str "Missing required files from " root ": " missing))
      (is (empty? missing)))))

(deftest deps-edn-declares-local-roots
  (testing "deps.edn continues to declare expected :local/root deps (ratchet)"
    (let [{:keys [ok? missing]} (hc/check-deps-edn-declares-local-deps)]
      (is ok? (str "deps.edn missing expected :local/root entries: " missing))
      (is (empty? missing)))))

(deftest mmca-smoke
  (testing "Tiny deterministic MMCA run succeeds"
    (let [{:keys [ok? error]} (hc/run-mmca-smoke)]
      (is ok? (str "MMCA smoke failed: " error)))))

