(ns futon5.exotype.e8-guard-test
  "P-008b §7 item 3 (amended): fail-closed guard on the five memoised S8
  wrappers -- chain-risk/s8-column, policy-epistemic/s8-pair-value (both
  arguments), self-tuning/s8-score and s8-transition, policy-expansion/
  cached-score. Each calls grid/assert-exotype-allowed! BEFORE the memo
  lookup, so a flag-off call with a non-injective f throws whether or not a
  flag-on call already cached the value.

  Predictor note: s8-score and policy-expansion's cached-score route through
  efe/score-policy -> predict :derived, which requires an installed
  efe/s8-predictor for vector exotypes. The tests install a fake one ONLY
  via with-redefs on the efe/s8-predictor var (a fresh atom local to the
  test); the real defonce atom and shared state are untouched."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.chain-risk :as chain-risk]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as policy-epistemic]
            [futon5.exotype.policy-expansion :as policy-expansion]
            [futon5.exotype.self-tuning :as self-tuning]))

(def f-bug [0 0 1 2 3 4 5 6])
;; A second non-injective vector, used only for the cold-cache flag-off
;; checks so the memo cannot already hold it.
(def f-cold [1 1 2 3 4 5 6 6])

(def throw-msg #"S8 exotype is not a permutation of 0\.\.7")

(def ^:private fake-predictor
  (constantly {:activity 0.5 :diversity 0.5 :hunger 0.5}))

(defn- with-fake-s8-predictor
  "Run THUNK with efe/s8-predictor bound to a fresh local atom (with-redefs
  on the var root; the real defonce atom is never mutated)."
  [thunk]
  (with-redefs [efe/s8-predictor (atom fake-predictor)]
    (thunk)))

;; --- the guard itself -------------------------------------------------------

(deftest assert-exotype-allowed!
  (testing "keywords and permutations pass under both flag settings"
    (is (nil? (grid/assert-exotype-allowed! :hold)))
    (is (nil? (grid/assert-exotype-allowed! [0 1 2 3 4 5 6 7])))
    (is (nil? (binding [grid/*allow-end8?* true]
                (grid/assert-exotype-allowed! [0 1 2 3 4 5 6 7])))))
  (testing "non-injective vector: throws flag-off, passes flag-on"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo throw-msg
                          (grid/assert-exotype-allowed! f-bug)))
    (is (nil? (binding [grid/*allow-end8?* true]
                (grid/assert-exotype-allowed! f-bug))))))

;; --- wrapper 1: chain-risk/s8-column (via risk) -----------------------------

(deftest chain-risk-guard
  (testing "flag on computes; the SAME call flag-off throws despite the cache"
    (is (double? (binding [grid/*allow-end8?* true]
                   (chain-risk/risk f-bug 0))))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo throw-msg
                          (chain-risk/risk f-bug 0))))
  (testing "flag off from a cold cache throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo throw-msg
                          (chain-risk/risk f-cold 0))))
  (testing "10 seeded permutations and named kinds: identical flag on/off"
    (let [perms (ca/with-seed 20261117
                  (vec (repeatedly 10 #(#'grid/random-s8))))
          off (mapv #(chain-risk/risk % 3) perms)
          on (binding [grid/*allow-end8?* true]
               (mapv #(chain-risk/risk % 3) perms))]
      (is (= 10 (count perms)))
      (is (= off on))
      (is (= (chain-risk/risk :identity 3)
             (binding [grid/*allow-end8?* true] (chain-risk/risk :identity 3)))))))

;; --- wrapper 2: policy-epistemic/s8-pair-value (BOTH arguments) --------------

(deftest policy-epistemic-guard
  (testing "flag on computes (both args vector); SAME call flag-off throws"
    (is (double? (binding [grid/*allow-end8?* true]
                   (policy-epistemic/pair-value f-bug [0 1 2 3 4 5 6 7]))))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo throw-msg
         (policy-epistemic/pair-value f-bug [0 1 2 3 4 5 6 7])))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo throw-msg
         (policy-epistemic/pair-value f-bug :identity))
        "own vector, candidate keyword: own argument is guarded")
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo throw-msg
         (policy-epistemic/pair-value :identity f-bug))
        "own keyword, candidate vector: the SECOND argument is guarded"))
  (testing "flag off from a cold cache throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo throw-msg
                          (policy-epistemic/pair-value f-cold :identity))))
  (testing "10 seeded permutations and named kinds: identical flag on/off"
    (let [perms (ca/with-seed 20261118
                  (vec (repeatedly 10 #(#'grid/random-s8))))
          off (mapv #(policy-epistemic/pair-value % :identity) perms)
          on (binding [grid/*allow-end8?* true]
               (mapv #(policy-epistemic/pair-value % :identity) perms))]
      (is (= 10 (count perms)))
      (is (= off on))
      (is (= (policy-epistemic/pair-value :identity :builder)
             (binding [grid/*allow-end8?* true]
               (policy-epistemic/pair-value :identity :builder)))))))

;; --- wrappers 3 and 4: self-tuning/s8-score and s8-transition ----------------

(deftest self-tuning-s8-score-guard
  (testing "flag on computes; SAME call flag-off throws despite the cache"
    (with-fake-s8-predictor
      (fn []
        (is (map? (binding [grid/*allow-end8?* true]
                    (#'self-tuning/s8-score f-bug 0.0 1.0))))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo throw-msg
                              (#'self-tuning/s8-score f-bug 0.0 1.0))))))
  (testing "flag off from a cold cache throws"
    (with-fake-s8-predictor
      (fn []
        (is (thrown-with-msg? clojure.lang.ExceptionInfo throw-msg
                              (#'self-tuning/s8-score f-cold 0.0 1.0))))))
  (testing "10 seeded permutations and a named kind: identical flag on/off"
    (with-fake-s8-predictor
      (fn []
        (let [perms (ca/with-seed 20261119
                      (vec (repeatedly 10 #(#'grid/random-s8))))
              off (mapv #(#'self-tuning/s8-score % 0.0 1.0) perms)
              on (binding [grid/*allow-end8?* true]
                   (mapv #(#'self-tuning/s8-score % 0.0 1.0) perms))]
          (is (= 10 (count perms)))
          (is (= off on)))))))

(deftest self-tuning-s8-transition-guard
  (testing "flag on computes; SAME call flag-off throws despite the cache"
    (is (string? (binding [grid/*allow-end8?* true]
                   (#'self-tuning/s8-transition "一" f-bug 0))))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo throw-msg
                          (#'self-tuning/s8-transition "一" f-bug 0))))
  (testing "flag off from a cold cache throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo throw-msg
                          (#'self-tuning/s8-transition "一" f-cold 0))))
  (testing "10 seeded permutations and a named kind: identical flag on/off"
    (let [perms (ca/with-seed 20261120
                  (vec (repeatedly 10 #(#'grid/random-s8))))
          off (mapv #(#'self-tuning/s8-transition "一" % 0) perms)
          on (binding [grid/*allow-end8?* true]
               (mapv #(#'self-tuning/s8-transition "一" % 0) perms))]
      (is (= 10 (count perms)))
      (is (= off on))
      (is (= (#'self-tuning/s8-transition "一" :identity 0)
             (binding [grid/*allow-end8?* true]
               (#'self-tuning/s8-transition "一" :identity 0)))))))

;; --- wrapper 5: policy-expansion/cached-score --------------------------------

(deftest policy-expansion-guard
  (testing "flag on computes; SAME call flag-off throws despite the cache"
    (with-fake-s8-predictor
      (fn []
        (is (map? (binding [grid/*allow-end8?* true]
                    (#'policy-expansion/cached-score
                     :efe-full f-bug {:activity 0.0 :diversity 1.0} {}))))
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo throw-msg
             (#'policy-expansion/cached-score
              :efe-full f-bug {:activity 0.0 :diversity 1.0} {}))))))
  (testing "flag off from a cold cache throws"
    (with-fake-s8-predictor
      (fn []
        (is (thrown-with-msg?
             clojure.lang.ExceptionInfo throw-msg
             (#'policy-expansion/cached-score
              :efe-full f-cold {:activity 0.0 :diversity 1.0} {}))))))
  (testing "10 seeded permutations and named kinds: identical flag on/off"
    (with-fake-s8-predictor
      (fn []
        (let [perms (ca/with-seed 20261121
                      (vec (repeatedly 10 #(#'grid/random-s8))))
              off (mapv #(#'policy-expansion/cached-score
                          :efe-full % {:activity 0.0 :diversity 1.0} {})
                        perms)
              on (binding [grid/*allow-end8?* true]
                   (mapv #(#'policy-expansion/cached-score
                           :efe-full % {:activity 0.0 :diversity 1.0} {})
                         perms))]
          (is (= 10 (count perms)))
          (is (= off on))
          (is (= (#'policy-expansion/cached-score
                  :efe-full :identity {:activity 0.0 :diversity 1.0} {})
                 (binding [grid/*allow-end8?* true]
                   (#'policy-expansion/cached-score
                    :efe-full :identity {:activity 0.0 :diversity 1.0} {})))))))))
