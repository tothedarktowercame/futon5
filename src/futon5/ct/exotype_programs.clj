(ns futon5.ct.exotype-programs
  "Exotype program manifests built on the CT DSL."
  (:require [futon5.ca.core :as ca]
            [futon5.ct.dsl :as ct]
            [futon5.mmca.exotype :as exotype]))

(def exotype-program-category
  {:name :futon5/exotype-program
   :objects #{:history :context :kernel-spec :params :rng}
   :morphisms {:sample-context {:source [:history :rng] :target :context}
               :gate-update {:source [:kernel-spec :context :params :rng]
                             :target :kernel-spec}
               :contextual-mutate {:source [:kernel-spec :context :params :rng]
                                   :target :kernel-spec}
               :set-mix {:source [:kernel-spec :context :params :rng]
                         :target :kernel-spec}
               :normalize {:source [:kernel-spec :context :params :rng]
                           :target :kernel-spec}}})

(def sample-context
  (ct/primitive-diagram {:name :sample-context
                         :domain [:history :rng]
                         :codomain [:context]}))

(def gate-update
  (ct/primitive-diagram {:name :gate-update
                         :domain [:kernel-spec :context :params :rng]
                         :codomain [:kernel-spec]}))

(def contextual-mutate
  (ct/primitive-diagram {:name :contextual-mutate
                         :domain [:kernel-spec :context :params :rng]
                         :codomain [:kernel-spec]}))

(def set-mix
  (ct/primitive-diagram {:name :set-mix
                         :domain [:kernel-spec :context :params :rng]
                         :codomain [:kernel-spec]}))

(def normalize
  (ct/primitive-diagram {:name :normalize
                         :domain [:kernel-spec :context :params :rng]
                         :codomain [:kernel-spec]}))

(def local-program
  (ct/compose-diagrams gate-update contextual-mutate normalize))

(def super-program
  (ct/compose-diagrams gate-update contextual-mutate set-mix normalize))

(defn- diagram->edn* [diagram]
  (let [diagram (or diagram {})]
    (cond-> {:type (:type diagram)
             :domain (:domain diagram)
             :codomain (:codomain diagram)
             :data (:data diagram)}
      (seq (get-in diagram [:data :parts]))
      (update-in [:data :parts] (fn [parts]
                                  (mapv diagram->edn* parts))))))

(defn diagram->edn [diagram]
  (diagram->edn* diagram))

(defn program-for-tier [tier]
  (case tier
    :super super-program
    :local local-program
    local-program))

(defn manifest-entry
  [sigil tier]
  (let [exo (exotype/resolve-exotype {:sigil sigil :tier tier})
        diagram (program-for-tier (:tier exo))]
    {:sigil (:sigil exo)
     :bits (ca/bits-for (:sigil exo))
     :tier (:tier exo)
     :category (:name exotype-program-category)
     :inputs {:kernel-transformer [:kernel-spec :context :params :rng]
              :context-step [:history :rng]}
     :outputs [:kernel-spec]
     :program {:template (if (= :super (:tier exo))
                           :contextual-mutate+mix
                           :contextual-mutate)
               :composition-order :left-to-right
               :semantics :stochastic-kleisli
               :diagram (diagram->edn diagram)
               :context-step (diagram->edn sample-context)}
     :params (:params exo)
     :invariants [:normalize :probabilistic-update]
     :word-variation :by-tier
     :scope :kernel-transformer}))

(defn manifest
  ([]
   (manifest :super))
  ([tier]
   (mapv (fn [{:keys [sigil]}]
           (manifest-entry sigil tier))
         (ca/sigil-entries))))
