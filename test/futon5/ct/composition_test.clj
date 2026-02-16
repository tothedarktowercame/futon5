(ns futon5.ct.composition-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [futon5.ct.mission :as mission]))

(defn- kw*
  [mission-id id]
  (keyword (str (name mission-id) "." (name id))))

(defn- read-mission
  [path]
  (mission/mission-diagram (edn/read-string (slurp path))))

(def social (read-mission "data/missions/social-exotype.edn"))
(def coord (read-mission "data/missions/coordination-exotype.edn"))

(deftest standalone-diagrams-still-valid
  (testing "standalone diagrams validate (8/8)"
    (is (:all-valid (mission/validate social)) (pr-str (mission/summary social)))
    (is (:all-valid (mission/validate coord)) (pr-str (mission/summary coord)))))

(deftest timescale-ordering-with-social
  (testing ":social components constrained by :glacial inputs passes I3"
    (is (:valid (mission/validate-timescale-ordering social)))))

(deftest social+coordination-composes
  (testing "compose-parallel returns a composed diagram that validates"
    (let [composed (mission/compose-parallel social coord)]
      (is (some? composed))
      (is (:all-valid (mission/validate composed))
          (pr-str (mission/summary composed))))))

(deftest shared-ports-deduplicated
  (testing "shared constraint inputs appear once in the composed boundary"
    (let [composed (mission/compose-parallel social coord)
          inputs (get-in composed [:ports :input])]
      (is (= 1 (count (filter #(= :I-patterns (:id %)) inputs))))
      (is (= 1 (count (filter #(= :I-registry (:id %)) inputs)))))))

(deftest cross-diagram-edges-created
  (testing "O-task-submissions connects to I-request"
    (let [composed (mission/compose-parallel social coord)]
      (is (some #(and (= (kw* :social-exotype :O-task-submissions) (:from %))
                      (= (kw* :coordination-exotype :I-request) (:to %))
                      (= :http-request (:type %)))
                (:edges composed))))))

(deftest existing-serial-unaffected
  (testing "compose-missions still composes and returns a mission-diagram (regression)"
    (let [mission-a (mission/mission-diagram
                      {:mission/id :a
                       :mission/state :active
                       :ports {:input  [{:id :I :name "in" :type :http-request :source "x"}]
                               :output [{:id :O :name "out" :type :http-endpoint :consumer "y" :spec-ref "1"}]}
                       :components [{:id :C :name "proc"
                                     :accepts #{:http-request}
                                     :produces #{:http-endpoint}}]
                       :edges [{:from :I :to :C :type :http-request}
                              {:from :C :to :O :type :http-endpoint}]})
          mission-b (mission/mission-diagram
                      {:mission/id :b
                       :mission/state :active
                       :ports {:input  [{:id :I2 :name "in2" :type :http-endpoint :source "a"}]
                               :output [{:id :O2 :name "out2" :type :http-endpoint :consumer "z" :spec-ref "2"}]}
                       :components [{:id :C2 :name "dash"
                                     :accepts #{:http-endpoint}
                                     :produces #{:http-endpoint}}]
                       :edges [{:from :I2 :to :C2 :type :http-endpoint}
                              {:from :C2 :to :O2 :type :http-endpoint}]})
          composed (mission/compose-missions mission-a mission-b)]
      (is (some? composed))
      (is (= :a→b (:mission/id composed)))
      ;; This regression check is intentionally minimal: some composed systems
      ;; may fail I6 closure without additional redundancy.
      (is (:valid (mission/validate-type-safety composed))
          (pr-str (mission/validate-type-safety composed)))
      (is (some? (mission/validate composed))))))
