(ns futon5.exotic.pattern-repl
  "Minimal pattern REPL handshake for Mission 9/10."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [futon5.exotic.functor :as functor]
            [futon5.exotic.vision :as vision]))

(def default-registry-path "resources/exotype-xenotype-lift.edn")

(defn load-registry
  ([] (load-registry default-registry-path))
  ([path]
   (edn/read-string (slurp (io/file path)))))

(defn pattern->ct-template
  "Lookup a pattern-id in the lift registry.
  Returns the registry entry or nil."
  [registry pattern-id]
  (some #(when (= pattern-id (:pattern-id %)) %) (:patterns registry)))

(defn- degenerate-vision [pattern-id]
  (vision/declare-vision
   {:name (keyword "exotic" (str "vision-" (or pattern-id "degenerate")))
    :objects #{:start}
    :identities {:start :id-start}
    :morphisms {:id-start {:dom :start :cod :start}}
    :compose-table {}
    :missions []}))

(defn ct-template->vision
  "Create a vision skeleton from a CT template entry.
  Falls back to a degenerate vision when templates are missing."
  [pattern-id ct-entry]
  (let [template (:ct-template ct-entry)]
    (if (and (map? template) (seq (:objects template)))
      (vision/declare-vision
       {:name (keyword "exotic" (str "vision-" pattern-id))
        :objects (set (:objects template))
        :identities (or (:identities template) {})
        :morphisms (or (:morphisms template) {})
        :compose-table (or (:compose-table template) {})
        :missions []})
      (degenerate-vision pattern-id))))

(defn vision->plan-functor-stub
  "Create a placeholder plan functor that maps objects/morphisms to themselves."
  [vis]
  (let [objects (:objects vis)
        morphisms (keys (:morphisms vis))
        object-map (zipmap objects objects)
        morphism-map (zipmap morphisms morphisms)]
    (functor/->Functor
     :plan-functor-stub
     (:name vis)
     (:name vis)
     object-map
     morphism-map)))

(defn run->evidence
  "Create a lightweight evidence bundle stub."
  [run-id context]
  {:run/id run-id
   :evidence/type :placeholder
   :context context})

(defn evidence->lift-proposal
  "Create a placeholder lift proposal."
  [pattern-id run-id evidence]
  {:proposal/id (str "lift-proposal-" (or run-id "unknown"))
   :pattern-id pattern-id
   :evidence evidence
   :status :draft})

(defn pattern-repl-handshake
  "End-to-end stub: pattern-id -> ct-template -> vision skeleton -> plan functor stub
  -> run -> evidence bundle -> lift proposal."
  [{:keys [pattern-id run-id context registry-path]
    :or {run-id "demo-run"}}]
  (let [registry (load-registry (or registry-path default-registry-path))
        ct-entry (pattern->ct-template registry pattern-id)
        vision (ct-template->vision pattern-id ct-entry)
        plan-functor (vision->plan-functor-stub vision)
        evidence (run->evidence run-id context)
        lift-proposal (evidence->lift-proposal pattern-id run-id evidence)]
    {:pattern-id pattern-id
     :ct-template ct-entry
     :vision vision
     :plan-functor plan-functor
     :evidence evidence
     :lift-proposal lift-proposal}))

(comment
  ;; Example handshake:
  (pattern-repl-handshake
   {:pattern-id "f5/p3"
    :run-id "demo-001"
    :context {:note "stub run"}}))
