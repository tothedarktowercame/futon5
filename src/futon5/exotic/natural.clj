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

(defn commutativity-residual
  "Compute a placeholder residual for commutativity.
  Returns 0.0 when component is present, 1.0 otherwise."
  [^NatTrans nt morphism-id]
  (if (apply-component nt morphism-id) 0.0 1.0))

(defn residuals-for
  "Compute commutativity residuals for a set of morphisms."
  [^NatTrans nt morphisms]
  (let [by-morphism (into {}
                          (map (fn [m] [m (commutativity-residual nt m)])
                               morphisms))
        mean (when (seq by-morphism)
               (/ (reduce + 0.0 (vals by-morphism))
                  (double (count by-morphism))))]
    {:by-morphism by-morphism
     :mean mean}))

(defn apply-component
  "Apply a component mapping for a morphism id."
  [^NatTrans nt morphism-id]
  (get (:components nt) morphism-id))
