(ns futon5.ct.mission
  "Mission validator for exotype/concrete diagrams.

  This file is intentionally small and data-driven: the contract is that
  a concrete instantiation must not drift from the exotype without a
  stop-the-line failure."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]))

(def default-exotype-path "data/missions/coordination-exotype.edn")
(def default-concrete-path "data/missions/futon3-coordination.edn")

(defn- resolve-path
  "Prefer PATH if it exists; otherwise try stripping an optional leading \"futon5/\"."
  [path]
  (let [f (io/file path)]
    (cond
      (.exists f) path
      (str/starts-with? (str path) "futon5/") (let [alt (subs (str path) (count "futon5/"))]
                                                (if (.exists (io/file alt)) alt path))
      :else path)))

(defn- read-edn-file [path]
  (with-open [r (java.io.PushbackReader. (io/reader (io/file path)))]
    (edn/read {:eof nil} r)))

(defn- fail [name details]
  {:check name :ok false :details details})

(defn- pass [name details]
  {:check name :ok true :details details})

(defn- exotype-edge-by-id [exotype]
  (into {} (map (juxt :id identity)) (get exotype :edges)))

(defn- required-levels [exotype profile]
  (get-in exotype [:profiles profile :required-levels] #{:L0}))

(defn- required-edge-ids [exotype levels]
  (->> (:edges exotype)
       (filter #(contains? levels (:level %)))
       (map :id)
       set))

(defn- node [exotype nid]
  (get-in exotype [:nodes nid]))

(defn- edge [exotype eid]
  (get (exotype-edge-by-id exotype) eid))

(defn- referenced-nodes-ok? [exotype]
  (let [nodes (set (keys (:nodes exotype)))
        bad (->> (:edges exotype)
                 (mapcat (fn [{:keys [from to id]}]
                           (cond-> []
                             (not (contains? nodes from)) (conj {:edge id :missing from :pos :from})
                             (not (contains? nodes to)) (conj {:edge id :missing to :pos :to}))))
                 vec)]
    (if (seq bad)
      (fail :completeness {:missing-nodes bad})
      (pass :completeness {:nodes (count nodes) :edges (count (:edges exotype))}))))

(defn- check-coverage [exotype concrete levels]
  (let [required (required-edge-ids exotype levels)
        present (set (:edges concrete))
        missing (sort (seq (set/difference required present)))]
    (if (seq missing)
      (fail :coverage {:missing-edges missing})
      (pass :coverage {:required (count required) :present (count present)}))))

(defn- check-orphans [exotype concrete levels]
  (let [required (required-edge-ids exotype levels)
        all-exotype (set (map :id (:edges exotype)))
        present (set (:edges concrete))
        extra (sort (seq (set/difference present all-exotype)))
        wrong-level (->> present
                         (remove required)
                         (filter (fn [eid]
                                   (when-let [e (edge exotype eid)]
                                     (not (contains? levels (:level e))))))
                         sort)]
    (cond
      (seq extra) (fail :orphans {:extra-edges extra})
      (seq wrong-level) (fail :orphans {:edges-not-in-profile wrong-level
                                        :levels levels})
      :else (pass :orphans {:edges (count present)}))))

(defn- check-type-safety [exotype concrete]
  ;; Lightweight structural type checks:
  ;; - Only G2 may write to I-environment (I2)
  ;; - Every output's implementing gate exists in :gates
  (let [gates (set (keys (:gates concrete)))
        present (set (:edges concrete))
        writes-to-env (->> present
                           (keep (fn [eid]
                                   (let [{:keys [from to]} (edge exotype eid)]
                                     (when (= to :I-environment) {:edge eid :from from :to to}))))
                           vec)
        bad-writes (->> writes-to-env
                        (remove #(= (:from %) :G2))
                        vec)
        outputs (:outputs concrete)
        bad-impl (->> outputs
                      (keep (fn [[port {:keys [implemented-by]}]]
                              (when (and implemented-by (not (contains? gates implemented-by)))
                                {:port port :implemented-by implemented-by})))
                      vec)]
    (cond
      (seq bad-writes) (fail :type-safety {:i2-violations bad-writes})
      (seq bad-impl) (fail :type-safety {:unknown-gates bad-impl})
      :else (pass :type-safety {:env-writes (count writes-to-env)}))))

(defn- required-outputs [exotype levels]
  (let [required-edges (->> (:edges exotype) (filter #(contains? levels (:level %))) vec)
        outputs (->> required-edges
                     (map :to)
                     (filter #(= :output (:kind (node exotype %))))
                     distinct
                     vec)]
    outputs))

(defn- check-spec-coverage [exotype concrete levels]
  (let [ports (required-outputs exotype levels)
        outputs (:outputs concrete)
        missing (->> ports (remove #(contains? outputs %)) vec)
        bad (->> ports
                 (keep (fn [p]
                         (let [{:keys [spec-ref implemented-by tested-by]} (get outputs p)]
                           (when (or (str/blank? (str spec-ref))
                                     (nil? implemented-by)
                                     (empty? (or tested-by [])))
                             {:port p
                              :spec-ref spec-ref
                              :implemented-by implemented-by
                              :tested-by tested-by}))))
                 vec)]
    (cond
      (seq missing) (fail :spec-coverage {:missing-outputs missing})
      (seq bad) (fail :spec-coverage {:invalid-outputs bad})
      :else (pass :spec-coverage {:outputs (count ports)}))))

(defn- check-i3 [exotype concrete]
  ;; I3: slow/glacial constrain fast. Forbid fast -> constraint ports (slow or glacial).
  (let [present (set (:edges concrete))
        violations
        (->> present
             (keep (fn [eid]
                     (let [{:keys [from to]} (edge exotype eid)
                           from-node (node exotype from)
                           to-node (node exotype to)]
                       (when (and (= :fast (:timescale from-node))
                                  (contains? #{:slow :glacial} (:timescale to-node))
                                  (true? (:constraint? to-node)))
                         {:edge eid :from from :to to}))))
             vec)]
    (if (seq violations)
      (fail :I3-timescale-separation {:violations violations})
      (pass :I3-timescale-separation {:ok true}))))

(defn- check-i4 [exotype concrete]
  ;; I4: no fast output -> constraints path.
  (let [present (set (:edges concrete))
        violations
        (->> present
             (keep (fn [eid]
                     (let [{:keys [from to]} (edge exotype eid)
                           from-node (node exotype from)
                           to-node (node exotype to)]
                       (when (and (= :output (:kind from-node))
                                  (= :fast (:timescale from-node))
                                  (= :input (:kind to-node))
                                  (true? (:constraint? to-node)))
                         {:edge eid :from from :to to}))))
             vec)]
    (if (seq violations)
      (fail :I4-preference-exogeneity {:violations violations})
      (pass :I4-preference-exogeneity {:ok true}))))

(defn- check-i6 [exotype concrete]
  ;; I6: compositional closure (for Prototype 0 we enforce the presence of C-default wiring).
  (let [present (set (:edges concrete))
        required #{:e-ie-cd :e-irg-cd :e-cd-ir :e-cd-ov}
        missing (sort (seq (set/difference required present)))
        has-default? (contains? (set (keys (:gates concrete))) :c-default)]
    (cond
      (not has-default?) (fail :I6-compositional-closure {:missing-gate :c-default})
      (seq missing) (fail :I6-compositional-closure {:missing-edges missing})
      :else (pass :I6-compositional-closure {:ok true}))))

(defn validate!
  "Validate CONCRETE against EXOTYPE. Returns {:ok boolean :checks [...] ...}."
  [exotype concrete]
  (let [profile (:profile concrete)
        levels (required-levels exotype profile)
        checks [(referenced-nodes-ok? exotype)
                (check-coverage exotype concrete levels)
                (check-orphans exotype concrete levels)
                (check-type-safety exotype concrete)
                (check-spec-coverage exotype concrete levels)
                (check-i3 exotype concrete)
                (check-i4 exotype concrete)
                (check-i6 exotype concrete)]
        ok? (every? :ok checks)]
    {:ok ok?
     :profile profile
     :required-levels levels
     :checks checks}))

(defn -main [& args]
  (let [[exotype-path concrete-path] args
        exotype-path (resolve-path (or exotype-path default-exotype-path))
        concrete-path (resolve-path (or concrete-path default-concrete-path))
        exotype (read-edn-file exotype-path)
        concrete (read-edn-file concrete-path)
        result (validate! exotype concrete)]
    (doseq [{:keys [check ok details]} (:checks result)]
      (println (format "[%s] %s" (if ok "PASS" "FAIL") (name check)))
      (when-not ok
        (println (pr-str details))))
    (println (format "Overall: %s (profile=%s required-levels=%s)"
                     (if (:ok result) "PASS" "FAIL")
                     (:profile result)
                     (:required-levels result)))
    (System/exit (if (:ok result) 0 1))))
