(ns futon5.exotic.category
  "Minimal category structures for exotic programming experiments.")

(defrecord Category [objects morphisms identities compose-table])

(defn identity-for
  "Return the identity morphism id for an object."
  [^Category cat obj]
  (get (:identities cat) obj))

(defn compose
  "Compose morphisms (m1 ∘ m2). Returns a morphism id or nil."
  [^Category cat m1 m2]
  (let [morphisms (:morphisms cat)
        m1* (get morphisms m1)
        m2* (get morphisms m2)]
    (when (and m1* m2* (= (:dom m1*) (:cod m2*)))
      (get (:compose-table cat) [m1 m2]))))

(defn- valid-morphism? [objects morphism]
  (and (contains? objects (:dom morphism))
       (contains? objects (:cod morphism))))

(defn- compose-entry-valid? [cat [m1 m2] m3]
  (let [morphisms (:morphisms cat)
        m1* (get morphisms m1)
        m2* (get morphisms m2)
        m3* (get morphisms m3)]
    (and m1* m2* m3*
         (= (:dom m1*) (:cod m2*))
         (= (:dom m3*) (:dom m2*))
         (= (:cod m3*) (:cod m1*)))))

(defn- identity-valid? [cat obj mid]
  (let [m (get (:morphisms cat) mid)]
    (and m
         (= obj (:dom m))
         (= obj (:cod m)))))

(defn- associativity-valid? [cat]
  (let [compose-table (:compose-table cat)]
    (every?
     true?
     (for [[[m1 m2] m12] compose-table
           [[m2' m3] m23] compose-table
           :when (= m2 m2')
           :let [m1m23 (get compose-table [m1 m23])
                 m12m3 (get compose-table [m12 m3])]
           :when (and m1m23 m12m3)]
       (= m1m23 m12m3)))))

(defn valid-category?
  "Validate category laws for the provided category.
  Returns true/false, does not throw."
  [^Category cat]
  (let [objects (:objects cat)
        morphisms (:morphisms cat)
        identities (:identities cat)
        compose-table (:compose-table cat)]
    (and (set? objects)
         (map? morphisms)
         (every? (partial valid-morphism? objects) (vals morphisms))
         (every? (fn [[obj mid]] (identity-valid? cat obj mid)) identities)
         (every? (fn [[k v]] (compose-entry-valid? cat k v)) compose-table)
         (associativity-valid? cat))))
