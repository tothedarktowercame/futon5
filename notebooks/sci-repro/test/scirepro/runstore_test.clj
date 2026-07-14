(ns scirepro.runstore-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [scirepro.runstore :as rs]
            [clojure.java.io :as io]))

(def ^:dynamic *test-root* nil)

(defn temp-root-fixture
  "Create a unique temp directory for each test and clean it up after."
  [f]
  (let [tmp (java.io.File/createTempFile "runstore-test" "")
        root (io/file (str (.toPath tmp) "-dir"))]
    (.delete tmp)
    (.mkdirs root)
    (binding [*test-root* (.getPath root)]
      (try
        (f)
        (finally
          (doseq [f (reverse (file-seq root))]
            (.delete f))))))
  (use-fixtures :each temp-root-fixture))

(use-fixtures :each temp-root-fixture)

(defn- sample-config
  []
  {:width 32 :steps 100 :rule 110})

(deftest key-stability-test
  (testing "run-key is stable for the same inputs"
    (is (= (rs/run-key :blend 42 (sample-config))
           (rs/run-key :blend 42 (sample-config)))))
  (testing "run-key differs for different dynamics"
    (is (not= (rs/run-key :blend 42 (sample-config))
              (rs/run-key :mutation 42 (sample-config)))))
  (testing "run-key differs for different seeds"
    (is (not= (rs/run-key :blend 42 (sample-config))
              (rs/run-key :blend 43 (sample-config)))))
  (testing "run-key differs for different configs"
    (is (not= (rs/run-key :blend 42 (sample-config))
              (rs/run-key :blend 42 {:width 64 :steps 100 :rule 110})))))

(deftest absent-key-test
  (testing "lookup returns :absent for a key with no run"
    (is (= :absent (:status (rs/lookup *test-root* "nonexistent-key"))))))

(deftest partial-then-complete-test
  (testing "write partial → read :partial; mark complete → read :complete"
    (let [key-str (rs/init-run! {:root *test-root*
                                  :dynamic :blend
                                  :seed 150200140
                                  :config (sample-config)
                                  :steps 100})]
      ;; Write some rows (partial)
      (rs/flush-rows! *test-root* key-str [[1 0 1 0] [0 1 0 1]] 2)
      (let [partial (rs/lookup *test-root* key-str)]
        (is (= :partial (:status partial)))
        (is (= 2 (count (:rows partial))))
        (is (= 2 (:rows-written (:meta partial)))))

      ;; Mark complete with final rows
      (rs/mark-complete! *test-root* key-str [[1 1 1 1]] 3)
      (let [complete (rs/lookup *test-root* key-str)]
        (is (= :complete (:status complete)))
        (is (= 3 (count (:rows complete))))
        (is (= 3 (:rows-written (:meta complete))))))))

(deftest incremental-append-test
  (testing "multiple flushes append rows incrementally"
    (let [key-str (rs/init-run! {:root *test-root*
                                  :dynamic :standard
                                  :seed 99
                                  :config (sample-config)
                                  :steps 200})]
      ;; Flush 1: rows 0-49
      (rs/flush-rows! *test-root* key-str (repeat 50 [1 0]) 50)
      ;; Flush 2: rows 50-99
      (rs/flush-rows! *test-root* key-str (repeat 50 [0 1]) 100)
      (let [result (rs/lookup *test-root* key-str)]
        (is (= :partial (:status result)))
        (is (= 100 (count (:rows result))))
        (is (= 100 (:rows-written (:meta result))))
        ;; First 50 rows are [1 0], next 50 are [0 1]
        (is (= [1 0] (first (:rows result))))
        (is (= [0 1] (nth (:rows result) 50)))))))

(deftest resume-appends-past-partial-test
  (testing "resume: a driver can continue appending past the partial point"
    (let [key-str (rs/init-run! {:root *test-root*
                                  :dynamic :blend
                                  :seed 77
                                  :config (sample-config)
                                  :steps 300})]
      ;; Simulate partial run: 100 rows flushed, then killed
      (rs/flush-rows! *test-root* key-str (for [i (range 100)] [i]) 100)
      (let [partial (rs/lookup *test-root* key-str)
            resume-point (:rows-written (:meta partial))]
        (is (= 100 resume-point))
        (is (= :partial (:status partial)))

        ;; Driver resumes: computes rows 100-199 and flushes
        (rs/flush-rows! *test-root* key-str
                        (for [i (range 100 200)] [i])
                        200)
        (let [resumed (rs/lookup *test-root* key-str)]
          (is (= 200 (:rows-written (:meta resumed))))
          (is (= 200 (count (:rows resumed))))
          ;; Row 100 is [100], row 199 is [199]
          (is (= [100] (nth (:rows resumed) 100)))
          (is (= [199] (last (:rows resumed)))))))))

(deftest list-runs-test
  (testing "list-runs returns all runs in the store"
    (rs/init-run! {:root *test-root* :dynamic :blend :seed 1 :config {} :steps 10})
    (rs/init-run! {:root *test-root* :dynamic :mutation :seed 2 :config {} :steps 10})
    (let [runs (rs/list-runs *test-root*)]
      (is (= 2 (count runs)))
      (is (every? #(= :partial (:status %)) runs)))))

(deftest cleanup-test
  (testing "cleanup! removes a run's artifacts"
    (let [key-str (rs/init-run! {:root *test-root* :dynamic :blend :seed 5 :config {} :steps 10})]
      (rs/flush-rows! *test-root* key-str [[1 2 3]] 1)
      (is (= :partial (:status (rs/lookup *test-root* key-str))))
      (rs/cleanup! *test-root* key-str)
      (is (= :absent (:status (rs/lookup *test-root* key-str)))))))
