(ns futon5.mmca.score
  "Compile CT diagrams into operator schedules runnable by the MMCA runtime."
  (:require [futon5.ct.adapters :as adapters]
            [futon5.ct.dsl :as ct]))

(defn- mapcat-indexed [f coll]
  (apply concat (map-indexed f coll)))

(defn- adapter-name? [name adapter-set]
  (contains? adapter-set name))

(defn- ensure-operator [operators-map name]
  (if-let [op (get operators-map name)]
    op
    (throw (ex-info "Unknown operator in diagram" {:name name
                                                   :available (keys operators-map)}))))

(defn- annotate-operator [op path diagram]
  (assoc op :score/meta {:path path
                         :domain (:domain diagram)
                         :codomain (:codomain diagram)
                         :diagram-type (:type diagram)
                         :diagram-name (get-in diagram [:data :name])}))

(defn- diagram->ops
  "Flatten diagram into an operator vector, skipping adaptor primitives."
  [diagram operators-map adapter-set path]
  (case (:type diagram)
    :primitive
    (let [name (get-in diagram [:data :name])]
      (cond
        (adapter-name? name adapter-set) []
        (= name :identity) []
        :else [(annotate-operator (ensure-operator operators-map name)
                                  path diagram)]))

    :identity []

    :compose
    (mapcat-indexed
     (fn [idx part]
       (diagram->ops part operators-map adapter-set (conj path [:compose idx])))
     (get-in diagram [:data :parts]))

    :tensor
    (let [parts (get-in diagram [:data :parts])
          total (count parts)]
      (mapcat-indexed
       (fn [idx part]
         (let [subpath (conj path [:tensor idx total])]
           (diagram->ops part operators-map adapter-set subpath)))
       parts))

    ;; default fall-through
    [(annotate-operator (ensure-operator operators-map
                                         (get-in diagram [:data :name]))
                        path diagram)]))

(defn compile-score
  "Compile a CT diagram into a run-mmca opts map.
  Required keys in spec: :diagram, :operators (name->operator map), :genotype.
  Optional: :mode, :generations, :phenotype, :adapter-names, :base-opts."
  [{:keys [diagram operators genotype adapter-names]
    :as spec}]
  (when-not diagram
    (throw (ex-info "Score compilation requires a :diagram" {})))
  (when-not operators
    (throw (ex-info "Score compilation requires an operator map" {})))
  (when-not genotype
    (throw (ex-info "Score compilation requires a starting genotype" {})))
  (let [adapter-set (or adapter-names (adapters/adapter-names))
        operator-list (vec (diagram->ops diagram operators adapter-set []))
        {:keys [mode generations phenotype] :as rest} (dissoc spec :diagram :operators :adapter-names)]
    (merge {:mode (or mode :god)
            :generations (or generations 32)
            :genotype genotype
            :operators operator-list
            :score {:diagram diagram}}
           (select-keys rest [:phenotype :pattern-sigils]))))

(defn operator-map
  "Utility: build a name->operator map from a sequence of {:name ... :operator ...}."
  [entries]
  (into {}
        (map (fn [{:keys [name operator]}]
               [name operator])
             entries)))
