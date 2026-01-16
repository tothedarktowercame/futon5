(ns futon5.ct.dsl
  "Minimal category-theory DSL for documenting FUTON5 operator architectures.
  The goal is to keep diagrams executable as plain data so we can evolve a
  richer formalisation (or port to Agda) later without changing semantics."
  (:require [clojure.pprint :as pprint]))

(defrecord Category [name objects morphisms])
(defrecord Functor [name source target object-map morphism-map])
(defrecord NaturalTransformation [name source target components])
(defrecord Diagram [type domain codomain data])

(defn build-category [spec]
  (->Category (:name spec)
              (set (:objects spec))
              (:morphisms spec)))

(defn build-functor [spec]
  (->Functor (:name spec)
             (:source spec)
             (:target spec)
             (:object-map spec)
             (:morphism-map spec)))

(defn build-natural-transformation [spec]
  (->NaturalTransformation (:name spec)
                           (:source spec)
                           (:target spec)
                           (:components spec)))

(defn primitive-diagram
  "Create a primitive diagram describing a named morphism.
  Domain/codomain are vectors of object keywords."
  [{:keys [name domain codomain]}]
  (->Diagram :primitive (vec domain) (vec codomain) {:name name}))

(defn identity-diagram [object]
  (->Diagram :identity [object] [object] {:name :identity}))

(defn compose-diagrams
  "Vertical composition of diagrams (g ∘ f)."
  [& diagrams]
  (let [parts (vec diagrams)
        domain (:domain (first parts))
        codomain (:codomain (last parts))]
    (->Diagram :compose domain codomain {:parts parts})))

(defn tensor-diagrams
  "Horizontal/parallel composition (f ⊗ g)."
  [& diagrams]
  (let [parts (vec diagrams)
        domain (vec (mapcat :domain parts))
        codomain (vec (mapcat :codomain parts))]
    (->Diagram :tensor domain codomain {:parts parts})))

(defn diagram-description [diagram]
  (case (:type diagram)
    :primitive (format "%s : %s → %s"
                       (get-in diagram [:data :name])
                       (:domain diagram)
                       (:codomain diagram))
    :identity (format "id_%s" (first (:domain diagram)))
    :compose (str "(compose " (count (get-in diagram [:data :parts])) " parts)")
    :tensor (str "(tensor " (count (get-in diagram [:data :parts])) " parts)")
    (str diagram)))

(defonce registry (atom {:categories {} :functors {} :transformations {}}))

(defn register-category! [spec]
  (let [cat (build-category spec)]
    (swap! registry update :categories assoc (:name cat) cat)
    cat))

(defn register-functor! [spec]
  (let [fun (build-functor spec)]
    (swap! registry update :functors assoc (:name fun) fun)
    fun))

(defn register-natural-transformation! [spec]
  (let [nt (build-natural-transformation spec)]
    (swap! registry update :transformations assoc (:name nt) nt)
    nt))

(defn describe [x] (pprint/pprint x))

(def aif-stage-category
  {:name :futon5/aif-stage
   :objects #{:world :observation :belief :policy :execution}
   :morphisms {:sense {:source :world :target :observation}
               :perceive {:source :observation :target :belief}
               :plan {:source :belief :target :policy}
               :enact {:source :policy :target :execution}
               :feedback {:source :execution :target :world}}})

(def meta-kernel-category
  {:name :futon5/meta-kernel
   :objects #{:sigil :context :template :mutation}
   :morphisms {:lift {:source :sigil :target :context}
               :template {:source :context :target :template}
               :mutate {:source :template :target :mutation}
               :apply {:source :mutation :target :sigil}}})

(def metaca-category
  {:name :futon5/metaca
   :objects #{:world :genotype :phenotype :sigil :kernel :operator-set :metrics}
   :morphisms {:observe {:source :world :target :metrics}
               :lift {:source :sigil :target :operator-set}
               :score {:source :metrics :target :operator-set}
               :evolve {:source :world :target :world}
               :gate {:source :world :target :world}}})

(def cyber-ant-category
  {:name :futon5/cyber-ant
   :objects #{:world :pheromone-field :food-field :nest-state :agent-state
              :policy :telemetry}
   :morphisms {:sense {:source :world :target :telemetry}
               :plan {:source :telemetry :target :policy}
               :act {:source :policy :target :world}
               :deposit {:source :world :target :pheromone-field}
               :consume {:source :world :target :agent-state}}})

(def design-pattern-category
  {:name :futon5/design-pattern
   :objects #{:if :then :however :because :next-steps :pattern :rationale}
   :morphisms {:kernel-step {:source [:if :then :however :because]
                             :target :next-steps}
               :frame {:source :pattern :target [:if :then :however]}
               :justify {:source [:pattern :rationale] :target :because}
               :synthesize {:source [:if :then :however :because] :target :next-steps}}})

(def metaca->cyber-ant-functor
  {:name :metaca->cyber-ant
   :source :futon5/metaca
   :target :futon5/cyber-ant
   :object-map {:world :world
                :genotype :policy
                :phenotype :agent-state
                :kernel :policy
                :operator-set :policy
                :metrics :telemetry
                :sigil :policy}
   :morphism-map {:observe :sense
                  :score :plan
                  :evolve :act
                  :gate :act
                  :lift :plan}})

(def observation->kernel-functor
  {:name :interestingness
   :source :futon5/aif-stage
   :target :futon5/meta-kernel
   :object-map {:observation :context
                :belief :template
                :policy :mutation
                :execution :sigil}
   :morphism-map {:perceive :template
                  :plan :mutate
                  :enact :apply}})

(def local-rule-balancer
  {:name :mutation-budget
   :source :interestingness
   :target :interestingness
   :components {}})

(comment
  (register-category! aif-stage-category)
  (register-category! meta-kernel-category)
  (register-category! metaca-category)
  (register-category! design-pattern-category)
  (register-category! cyber-ant-category)
  (register-functor! observation->kernel-functor)
  (register-functor! metaca->cyber-ant-functor)
  (register-natural-transformation! local-rule-balancer)
  (describe @registry))
