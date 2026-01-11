(ns futon5.mmca.functor
  "Functor that translates lifted pattern specs into executable MetaMetaCA operators."
  (:require [futon5.patterns.catalog :as catalog]))

(def source-category :pattern-dsl)
(def target-category :meta-meta-ca)

(defn lift->metammca
  "Translate a lifted pattern entry into a MetaMetaCA operator description."
  [{:keys [sigil bits lift] :as entry}]
  (let [{:keys [pattern impl hooks parameters]} (catalog/implement-pattern entry)]
    {:sigil sigil
     :bits bits
     :pattern pattern
     :context (:context lift)
     :if (:if lift)
     :however (:however lift)
     :then (:then lift)
     :because (:because lift)
     :parameters parameters
     :metrics (:metrics lift)
     :hooks hooks
     :functor {:name "Pattern→MetaMetaCA"
               :source source-category
               :target target-category
               :implements impl}}))

(defn compile-patterns
  ([] (map lift->metammca (catalog/available-patterns)))
  ([sigils]
   (let [entries (catalog/available-patterns)
         idx (into {} (map (juxt :sigil identity) entries))]
     (map lift->metammca (map idx sigils)))))

(comment
  (compile-patterns ["父" "仍" "手" "山"]))
