(ns futon5.exotype.menu-test
  "A menu may be any list of permutations of 0..7 (exotype DESIGN-general 2b).
   The named kinds must behave exactly as before."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ca.core :as ca]
            [futon5.exotype.efe :as efe]
            [futon5.exotype.grid :as grid]
            [futon5.exotype.policy-epistemic :as policy-epistemic]
            [futon5.exotype.self-tuning :as tuning]
            [futon5.xenotype.generator :as gen]))

(def ^:private width 40)

(defn- state [seed menu]
  (let [kinds (or menu grid/exotype-kinds)
        {:keys [genotype phenotype exotypes]}
        (ca/with-seed seed
          {:genotype (vec (ca/random-sigil-string width))
           :phenotype (ca/random-phenotype-string width)
           :exotypes (grid/initial-grid :heterogeneous-fixed width kinds)})]
    (cond-> {:arm :efe-full :seed seed :time 0
             :self-tuning-arm :fixed-0.55 :lambda-step-size 0.0
             :lambdas (vec (repeat width 0.55))
             :genotype genotype :previous-genotype genotype
             :phenotype phenotype :exotypes exotypes
             :blend-action? true :epistemic-coefficient 0.1
             :blend-strength 0.0 :apply-probability 1.0 :policy-precision 8.0}
      menu (assoc :menu menu))))

(defn- trajectory [state steps]
  (mapv (juxt :genotype :phenotype :exotypes)
        (take (inc steps) (iterate tuning/step state))))

(defn- as-vector [kind]
  (gen/sigma-positional (get grid/propagators kind)))

(deftest a-vector-denotes-the-same-operator-as-its-named-kind
  (doseq [[kind sigma] grid/propagators]
    (is (grid/sigma-vector? (as-vector kind)))
    (is (= sigma (grid/sigma-of (as-vector kind))) (str kind))))

(deftest what-is-not-a-permutation-is-refused
  (doseq [bad [[0 1 2 3 4 5 6] [0 0 2 3 4 5 6 7] [1 2 3 4 5 6 7 8] '(0 1 2 3 4 5 6 7) "01234567" nil]]
    (is (thrown? clojure.lang.ExceptionInfo (grid/sigma-of bad)) (pr-str bad)))
  (is (thrown? clojure.lang.ExceptionInfo (grid/sigma-of :no-such-kind)))
  (is (thrown? clojure.lang.ExceptionInfo (grid/check-menu [])))
  (is (thrown? clojure.lang.ExceptionInfo (grid/check-menu [:chaos :chaos]))))

(deftest the-named-menu-changes-nothing
  (testing "no menu, and the 12 named kinds given as a menu, give the same trajectory"
    (is (= (trajectory (state 2026102014 nil) 25)
           (trajectory (state 2026102014 grid/exotype-kinds) 25)))))

(deftest the-menu-survives-a-step
  (let [menu [(as-vector :chaos) (as-vector :odd53) (as-vector :collapser)]]
    (is (= menu (:menu (tuning/step (state 7 menu)))))
    (is (not (contains? (tuning/step (state 7 nil)) :menu)))))

(deftest a-vector-writes-the-rule-its-named-kind-writes
  (testing "the rule update depends on the operator only, so with scores out of
            the way (cells hold; no blend) a vector grid and its named grid agree"
    (let [named (-> (state 11 nil)
                    (assoc :exotypes (vec (take width (cycle [:chaos :odd53 :collapser]))))
                    (dissoc :policy-precision :epistemic-coefficient))
          vectors (update named :exotypes #(mapv as-vector %))
          step-rules (fn [s] (#'tuning/genotype-step s))]
      (is (= (step-rules named) (step-rules vectors))))))

(deftest pair-values-agree-between-a-vector-and-its-named-kind
  (doseq [own grid/exotype-kinds candidate grid/exotype-kinds]
    (is (= (policy-epistemic/pair-value own candidate)
           (policy-epistemic/pair-value (as-vector own) (as-vector candidate))))))

(deftest a-vector-outside-the-menu-or-without-one-is-refused
  (let [menu [(as-vector :chaos) (as-vector :odd53)]
        observation {:activity (/ 1.0 3.0) :diversity 1.0}]
    (is (thrown? clojure.lang.ExceptionInfo
                 (efe/predict (as-vector :chaos) observation :derived nil)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (efe/predict (as-vector :collapser) observation :derived menu)))
    (is (map? (efe/predict (as-vector :chaos) observation :derived menu)))))

(deftest the-menu-measurement-reproduces-the-shipped-table
  (testing "measuring the 14 named kinds as a menu gives the shipped resource,
            so a menu's rows are made the way the named rows were"
    (let [m (#'efe/measure-menu-model (vec (keys grid/propagators)))
          shipped @efe/conditional-model]
      (is (= (into {} (:bins shipped)) (:bins m)))
      (is (= (:global shipped) (:global m))))))

(deftest a-random-menu-runs
  (let [menu [[2 6 7 0 3 1 4 5] [1 0 4 3 5 6 2 7] [3 7 5 6 4 1 2 0]]
        end (last (take 31 (iterate tuning/step (state 5 menu))))]
    (is (every? (set menu) (:exotypes end)))
    (is (= width (count (:genotype end))))))
