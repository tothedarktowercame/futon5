(ns futon5.exotic.natural
  "Natural transformation scaffolding for plan adaptation.")

(defrecord NatTrans [name source target components])

(defn naturality-square
  "Return a map describing the naturality square for a morphism."
  [^NatTrans nt morphism-id]
  (let [component (get (:components nt) morphism-id)]
    {:morphism morphism-id
     :component component}))

(defn justified?
  "Placeholder justification check. Returns true if evidence is present."
  [evidence]
  (boolean evidence))

(defn apply-component
  "Apply a component mapping for a morphism id."
  [^NatTrans nt morphism-id]
  (get (:components nt) morphism-id))
