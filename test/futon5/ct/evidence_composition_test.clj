(ns futon5.ct.evidence-composition-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]]
            [futon5.ct.mission :as mission]))

(defn- kw*
  [mission-id id]
  (keyword (str (name mission-id) "." (name id))))

(defn- read-mission
  [path]
  (mission/mission-diagram (edn/read-string (slurp path))))

(def social   (read-mission "data/missions/social-exotype.edn"))
(def evidence (read-mission "data/missions/evidence-landscape-exotype.edn"))
(def coord    (read-mission "data/missions/coordination-exotype.edn"))

;; ================================================================
;; 1. Evidence landscape standalone validates 8/8
;; ================================================================

(deftest evidence-landscape-standalone-validates
  (testing "evidence landscape passes all 8 checks"
    (let [result (mission/validate evidence)]
      (is (:all-valid result)
          (pr-str (mission/summary evidence)))
      (is (= 8 (count (:checks result))))
      (is (every? :valid (:checks result))
          (pr-str (keep (fn [c] (when-not (:valid c) c)) (:checks result)))))))

;; ================================================================
;; 2. Social + evidence composes and validates 8/8
;; ================================================================

(deftest social+evidence-composes
  (testing "compose-parallel returns a composed diagram that validates"
    (let [composed (mission/compose-parallel social evidence)]
      (is (some? composed))
      (is (:all-valid (mission/validate composed))
          (pr-str (mission/summary composed))))))

;; ================================================================
;; 3. Three-way composition validates 8/8
;;
;; Composition order: (compose-parallel social coord) first, then
;; compose with evidence. This ensures:
;;   social.O-task-submissions → coord.I-request          (first pass)
;;   social.O-coordination-evidence → evidence.I-coordination-events (second pass)
;;   coord.O-evidence → evidence.I-gate-traversals        (second pass)
;; Evidence is the SINK for both pipelines' evidence outputs.
;; ================================================================

(deftest three-way-composition-validates
  (testing "social || coordination || evidence composes and validates"
    (let [social+coord (mission/compose-parallel social coord)
          three-way (mission/compose-parallel social+coord evidence)]
      (is (some? social+coord))
      (is (some? three-way))
      (is (:all-valid (mission/validate three-way))
          (pr-str (mission/summary three-way))))))

;; ================================================================
;; 4. Shared constraints deduplicated
;; ================================================================

(deftest shared-constraints-deduplicated
  (testing "I-patterns and I-registry appear once in social+evidence composed"
    (let [composed (mission/compose-parallel social evidence)
          inputs (get-in composed [:ports :input])]
      (is (= 1 (count (filter #(= :I-patterns (:id %)) inputs))))
      (is (= 1 (count (filter #(= :I-registry (:id %)) inputs))))))
  (testing "I-patterns and I-registry appear once in three-way composed"
    (let [social+coord (mission/compose-parallel social coord)
          three-way (mission/compose-parallel social+coord evidence)
          inputs (get-in three-way [:ports :input])]
      (is (= 1 (count (filter #(= :I-patterns (:id %)) inputs))))
      (is (= 1 (count (filter #(= :I-registry (:id %)) inputs)))))))

;; ================================================================
;; 5. Cross-diagram edge: social.O-coordination-evidence → evidence.I-coordination-events
;; ================================================================

(deftest cross-diagram-social-to-evidence
  (testing "O-coordination-evidence connects to I-coordination-events"
    (let [composed (mission/compose-parallel social evidence)]
      (is (some #(and (= (kw* :social-exotype :O-coordination-evidence) (:from %))
                      (= (kw* :evidence-landscape-exotype :I-coordination-events) (:to %))
                      (= :xtdb-entity (:type %)))
                (:edges composed))))))

;; ================================================================
;; 6. Cross-diagram edge: coordination.O-evidence → evidence.I-gate-traversals
;; ================================================================

(deftest cross-diagram-coordination-to-evidence
  (testing "O-evidence connects to I-gate-traversals in three-way"
    (let [social+coord (mission/compose-parallel social coord)
          three-way (mission/compose-parallel social+coord evidence)]
      ;; In the three-way (social||coord)||evidence, coordination's O-evidence
      ;; is a boundary output of social+coord (prefixed). It should match
      ;; evidence's I-gate-traversals input.
      (let [edges (:edges three-way)
            coord-evidence-edges (filter #(and (= :xtdb-entity (:type %))
                                               (.contains (name (:from %)) "O-evidence")
                                               (.contains (name (:to %)) "I-gate-traversals"))
                                         edges)]
        (is (seq coord-evidence-edges)
            (str "Expected cross-diagram edge from coordination O-evidence to evidence I-gate-traversals. "
                 "Edges: "
                 (pr-str (take 5 (filter #(or (.contains (name (:from %)) "evidence")
                                               (.contains (name (:to %)) "evidence"))
                                          edges)))))))))

;; ================================================================
;; 7. I3: no social component writes to glacial constraint in composed
;; ================================================================

(deftest i3-no-social-writes-to-glacial-constraint
  (testing "I3 holds in three-way composition"
    (let [social+coord (mission/compose-parallel social coord)
          three-way (mission/compose-parallel social+coord evidence)
          result (mission/validate-timescale-ordering three-way)]
      (is (:valid result) (pr-str result)))))

;; ================================================================
;; 8. I4: no output reaches constraint input in composed
;; ================================================================

(deftest i4-no-output-reaches-constraint
  (testing "I4 holds in three-way composition"
    (let [social+coord (mission/compose-parallel social coord)
          three-way (mission/compose-parallel social+coord evidence)
          result (mission/validate-exogeneity three-way)]
      (is (:valid result) (pr-str result)))))
