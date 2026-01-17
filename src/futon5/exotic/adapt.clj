(ns futon5.exotic.adapt
  "Adaptation helpers for updating plans with evidence."
  (:require [futon5.exotic.natural :as natural]))

(defn adaptation
  "Create an adaptation record."
  [{:keys [id from to evidence residuals nattrans morphisms]}]
  {:adaptation/id id
   :from from
   :to to
   :evidence evidence
   :residuals residuals
   :nattrans nattrans
   :morphisms morphisms})

(defn commutativity-residual
  "Compute a placeholder commutativity residual.
  Assumes residuals already computed."
  [adaptation]
  (or (get-in adaptation [:residuals :commutativity])
      (get-in adaptation [:residuals :mean])))

(defn evidence
  "Build a minimal evidence record."
  [{:keys [artifact-id notes]}]
  {:artifact-id artifact-id
   :notes notes})

(defn valid-evidence?
  "Return true when evidence has an artifact-id."
  [evidence]
  (and (map? evidence) (some? (:artifact-id evidence))))

(defn log-adaptation
  "Return an adaptation map, ensuring evidence is present."
  [adaptation]
  (when-not (valid-evidence? (:evidence adaptation))
    (throw (ex-info "Adaptation missing evidence" {:adaptation adaptation})))
  (let [residuals (or (:residuals adaptation)
                      (when (and (:nattrans adaptation) (seq (:morphisms adaptation)))
                        (natural/residuals-for (:nattrans adaptation) (:morphisms adaptation))))]
    (assoc adaptation
           :residuals residuals
           :residuals/by-morphism (:by-morphism residuals)
           :logged-at (System/currentTimeMillis)
           :commutativity-residual (commutativity-residual (assoc adaptation :residuals residuals)))))
