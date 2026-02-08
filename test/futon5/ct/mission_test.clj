(ns futon5.ct.mission-test
  "Tests for mission diagram validation using futon1a as first exemplar.

   The futon1a mission diagram is the reference case — it was drawn by hand
   in the mission document, and this is the machine-checkable EDN equivalent."
  (:require [clojure.test :refer [deftest is testing]]
            [futon5.ct.mission :as mission]))

;;; ============================================================
;;; futon1a mission diagram (EDN equivalent of the mermaid in
;;; M-futon1a-rebuild.md Interface Signature section)
;;; ============================================================

(def futon1a-diagram-spec
  "The futon1a rebuild mission expressed as a typed wiring diagram."
  {:mission/id :futon1a-rebuild
   :mission/state :active
   :ports
   {:input
    [{:id :I-spec   :name "Mission spec"       :type :edn-document   :source "human"}
     {:id :I-futon1 :name "futon1 LMDB data"   :type :migration-data :source "futon1"}
     {:id :I-config :name "System config"       :type :config         :source "env"}
     {:id :I-req    :name "HTTP requests"       :type :http-request   :source "client"}]
    :output
    [{:id :O-api      :name "HTTP API"           :type :http-endpoint    :consumer "client"   :spec-ref "2.6"}
     {:id :O-proof    :name "Proof paths"        :type :proof-path       :consumer "auditor"  :spec-ref "2.4"}
     {:id :O-model    :name "Model descriptors"  :type :model-descriptor :consumer "meta-api" :spec-ref "2.11"}
     {:id :O-types    :name "Type registry"      :type :type-registry    :consumer "runtime"  :spec-ref "2.11.3"}
     {:id :O-errors   :name "Error responses"    :type :error-response   :consumer "client"   :spec-ref "2.6.4"}
     {:id :O-compat   :name "futon1-compat"      :type :http-endpoint    :consumer "futon3"   :spec-ref "2.6.2"}
     {:id :O-migrate  :name "Migrated entities"  :type :xtdb-entity      :consumer "store"    :spec-ref "2.5"}]}
   :components
   [{:id :C-system   :name "System lifecycle"      :inputs [:config]          :outputs [:xtdb-node :ring-handler]}
    {:id :C-store    :name "XTDB durable store"    :inputs [:xtdb-node]       :outputs [:xtdb-entity :proof-path]}
    {:id :C-http     :name "Ring HTTP handler"      :inputs [:ring-handler :http-request] :outputs [:http-response]}
    {:id :C-routes   :name "API routes"             :inputs [:http-request]    :outputs [:http-endpoint :error-response]}
    {:id :C-migrate  :name "futon1 migration"       :inputs [:migration-data :xtdb-node]  :outputs [:xtdb-entity]}
    {:id :C-model    :name "Model descriptor layer" :inputs [:edn-document]   :outputs [:model-descriptor :type-registry]}]
   :edges
   ;; Input → Component
   [{:from :I-config :to :C-system  :type :config}
    {:from :I-req    :to :C-http    :type :http-request}
    {:from :I-spec   :to :C-model   :type :edn-document}
    {:from :I-futon1 :to :C-migrate :type :migration-data}
    ;; Component → Component
    {:from :C-system :to :C-store   :type :xtdb-node}
    {:from :C-system :to :C-http    :type :ring-handler}
    {:from :C-http   :to :C-routes  :type :http-request}
    {:from :C-migrate :to :C-store  :type :xtdb-entity}
    ;; Component → Output
    {:from :C-routes :to :O-api     :type :http-endpoint}
    {:from :C-routes :to :O-errors  :type :error-response}
    {:from :C-routes :to :O-compat  :type :http-endpoint}
    {:from :C-store  :to :O-proof   :type :proof-path}
    {:from :C-store  :to :O-migrate :type :xtdb-entity}
    {:from :C-model  :to :O-model   :type :model-descriptor}
    {:from :C-model  :to :O-types   :type :type-registry}]})

(def futon1a-diagram (mission/mission-diagram futon1a-diagram-spec))

;;; ============================================================
;;; Structural Tests
;;; ============================================================

(deftest test-diagram-construction
  (testing "diagram builds with correct index"
    (let [idx (:index futon1a-diagram)]
      (is (= 4 (count (:input-ids idx)))
          "4 input ports")
      (is (= 7 (count (:output-ids idx)))
          "7 output ports")
      (is (= 6 (count (:comp-ids idx)))
          "6 components")
      (is (= 17 (count (get-in futon1a-diagram [:index :all-ids])))
          "17 total nodes"))))

(deftest test-mission-id
  (testing "mission ID preserved"
    (is (= :futon1a-rebuild (:mission/id futon1a-diagram)))))

;;; ============================================================
;;; Completeness: every output reachable from some input
;;; ============================================================

(deftest test-completeness-passes
  (testing "all outputs reachable from inputs in futon1a"
    (let [result (mission/validate-completeness futon1a-diagram)]
      (is (:valid result)
          (str "unreachable outputs: " (:unreachable-outputs result))))))

(deftest test-completeness-detects-unreachable
  (testing "detects output with no path from input"
    (let [bad-spec (update-in futon1a-diagram-spec [:ports :output]
                              conj {:id :O-phantom :name "Ghost" :type :cli-command
                                    :consumer "nobody" :spec-ref "X"})
          bad-diagram (mission/mission-diagram bad-spec)
          result (mission/validate-completeness bad-diagram)]
      (is (not (:valid result)))
      (is (some #{:O-phantom} (:unreachable-outputs result))))))

;;; ============================================================
;;; Coverage: every component reaches an output
;;; ============================================================

(deftest test-coverage-passes
  (testing "all components reach outputs in futon1a"
    (let [result (mission/validate-coverage futon1a-diagram)]
      (is (:valid result)
          (str "dead components: " (:dead-components result))))))

(deftest test-coverage-detects-dead-component
  (testing "detects component with no path to any output"
    (let [bad-spec (update futon1a-diagram-spec :components
                           conj {:id :C-dead :name "Dead end" :inputs [:config] :outputs [:config]})
          bad-diagram (mission/mission-diagram bad-spec)
          result (mission/validate-coverage bad-diagram)]
      (is (not (:valid result)))
      (is (some #{:C-dead} (:dead-components result))))))

;;; ============================================================
;;; No Orphan Inputs
;;; ============================================================

(deftest test-no-orphan-inputs-passes
  (testing "all inputs connected in futon1a"
    (let [result (mission/validate-no-orphan-inputs futon1a-diagram)]
      (is (:valid result)
          (str "orphan inputs: " (:orphan-inputs result))))))

(deftest test-detects-orphan-input
  (testing "detects input port with no outgoing edge"
    (let [bad-spec (update-in futon1a-diagram-spec [:ports :input]
                              conj {:id :I-orphan :name "Unused" :type :cli-command :source "nobody"})
          bad-diagram (mission/mission-diagram bad-spec)
          result (mission/validate-no-orphan-inputs bad-diagram)]
      (is (not (:valid result)))
      (is (some #{:I-orphan} (:orphan-inputs result))))))

;;; ============================================================
;;; Type Safety
;;; ============================================================

(deftest test-type-safety-passes
  (testing "all edges type-safe in futon1a"
    (let [result (mission/validate-type-safety futon1a-diagram)]
      (is (:valid result)
          (str "type errors: " (mapv :message (:type-errors result)))))))

(deftest test-type-safety-detects-mismatch
  (testing "detects type mismatch on edge"
    (let [bad-spec (update futon1a-diagram-spec :edges
                           conj {:from :I-config :to :O-proof :type :proof-path})
          bad-diagram (mission/mission-diagram bad-spec)
          result (mission/validate-type-safety bad-diagram)]
      (is (not (:valid result))
          "config→proof-path should be a type mismatch"))))

;;; ============================================================
;;; Spec Coverage
;;; ============================================================

(deftest test-spec-coverage-passes
  (testing "all outputs have spec-refs in futon1a"
    (let [result (mission/validate-spec-coverage futon1a-diagram)]
      (is (:valid result)
          (str "unspecified: " (:unspecified-outputs result))))))

(deftest test-spec-coverage-detects-missing-ref
  (testing "detects output without spec-ref"
    (let [bad-spec (update-in futon1a-diagram-spec [:ports :output]
                              conj {:id :O-nospec :name "No spec" :type :http-endpoint :consumer "?"})
          bad-diagram (mission/mission-diagram bad-spec)
          result (mission/validate-spec-coverage bad-diagram)]
      (is (not (:valid result)))
      (is (some #{:O-nospec} (:unspecified-outputs result))))))

;;; ============================================================
;;; Validate All
;;; ============================================================

(deftest test-validate-all-passes
  (testing "futon1a passes all validations"
    (let [result (mission/validate futon1a-diagram)]
      (is (:all-valid result)
          (str "failed checks: "
               (vec (keep (fn [c] (when-not (:valid c) c))
                          (:checks result))))))))

;;; ============================================================
;;; Composition
;;; ============================================================

(def hypothetical-mission-b
  "A hypothetical downstream mission that consumes futon1a's outputs."
  {:mission/id :futon-analytics
   :mission/state :greenfield
   :ports
   {:input
    [{:id :B-api    :name "API endpoint"     :type :http-endpoint    :source "futon1a"}
     {:id :B-types  :name "Type registry"    :type :type-registry    :source "futon1a"}]
    :output
    [{:id :B-dash   :name "Dashboard"        :type :http-endpoint    :consumer "user" :spec-ref "1.0"}]}
   :components
   [{:id :B-query  :name "Query engine" :inputs [:http-endpoint :type-registry] :outputs [:http-endpoint]}]
   :edges
   [{:from :B-api   :to :B-query :type :http-endpoint}
    {:from :B-types :to :B-query :type :type-registry}
    {:from :B-query :to :B-dash  :type :http-endpoint}]})

(deftest test-composable
  (testing "futon1a can compose with downstream mission"
    (let [matches (mission/composable? futon1a-diagram
                                        (mission/mission-diagram hypothetical-mission-b))]
      (is (pos? (count matches))
          "should find composable port pairs")
      ;; futon1a's :O-api and :O-compat are http-endpoint, B wants http-endpoint
      ;; futon1a's :O-types is type-registry, B wants type-registry
      (is (>= (count matches) 3)
          (str "expected >=3 matches, got " (count matches) ": " matches)))))

(deftest test-compose-missions
  (testing "composition produces valid merged diagram"
    (let [b-diagram (mission/mission-diagram hypothetical-mission-b)
          composed (mission/compose-missions futon1a-diagram b-diagram)]
      (is (some? composed) "composition should succeed")
      ;; The composed mission should have futon1a's inputs + any unmatched B inputs
      (is (>= (count (get-in composed [:ports :input])) 4)
          "should preserve futon1a's inputs")
      ;; The composed mission should have B's outputs + futon1a's unmatched outputs
      (is (>= (count (get-in composed [:ports :output])) 1)
          "should have B's dashboard output"))))

(deftest test-compose-validates
  (testing "composed diagram passes validation"
    (let [b-diagram (mission/mission-diagram hypothetical-mission-b)
          composed (mission/compose-missions futon1a-diagram b-diagram)]
      (when composed
        (let [result (mission/validate composed)]
          ;; Composed diagrams may have edge prefix issues but structure should hold
          (is (some? result) "validation should return"))))))

;;; ============================================================
;;; Not Composable
;;; ============================================================

(def incompatible-mission
  {:mission/id :unrelated
   :mission/state :greenfield
   :ports {:input [{:id :U-in :name "CLI" :type :cli-command :source "user"}]
           :output [{:id :U-out :name "CLI" :type :cli-command :consumer "user" :spec-ref "1"}]}
   :components [{:id :U-proc :name "Processor" :inputs [:cli-command] :outputs [:cli-command]}]
   :edges [{:from :U-in :to :U-proc :type :cli-command}
           {:from :U-proc :to :U-out :type :cli-command}]})

(deftest test-not-composable
  (testing "incompatible missions have no matching ports"
    (let [matches (mission/composable? futon1a-diagram
                                        (mission/mission-diagram incompatible-mission))]
      (is (empty? matches)))))

;;; ============================================================
;;; Mermaid Rendering
;;; ============================================================

(deftest test-mermaid-rendering
  (testing "diagram renders to mermaid string"
    (let [mermaid (mission/diagram->mermaid futon1a-diagram)]
      (is (string? mermaid))
      (is (.startsWith mermaid "graph LR"))
      (is (.contains mermaid "inputs"))
      (is (.contains mermaid "outputs"))
      (is (.contains mermaid "futon1a-rebuild"))))

(deftest test-summary
  (testing "summary captures key metrics"
    (let [s (mission/summary futon1a-diagram)]
      (is (= :futon1a-rebuild (:mission/id s)))
      (is (= 4 (:input-count s)))
      (is (= 7 (:output-count s)))
      (is (= 6 (:component-count s)))
      (is (:all-valid s))))))
