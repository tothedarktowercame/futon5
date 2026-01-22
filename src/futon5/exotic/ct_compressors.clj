(ns futon5.exotic.ct-compressors
  "Lightweight CT compressors from flexiarg patterns."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(def ^:private section-regex
  (re-pattern "(?i)^\\s*\\+\\s*(IF|HOWEVER|THEN|BECAUSE|NEXT-STEPS)\\s*:") )

(defn- pattern-id->path
  [root pattern-id]
  (str (io/file root (str pattern-id ".flexiarg"))))

(defn- read-lines
  [path]
  (when (and path (.exists (io/file path)))
    (str/split-lines (slurp path))))

(defn- extract-sections
  "Return a set of section labels present in the flexiarg body."
  [lines]
  (->> lines
       (map (fn [line]
              (when-let [m (re-find section-regex line)]
                (keyword (str/lower-case (second m))))))
       (remove nil?)
       set))

(defn- ensure-objects
  [sections]
  (cond-> #{:action}
    (contains? sections :if) (conj :context)
    (contains? sections :however) (conj :constraint)
    (contains? sections :because) (conj :rationale)
    (contains? sections :next-steps) (conj :future)
    true (conj :start)))

(defn- ensure-morphisms
  [sections]
  (let [morphs (cond-> {:m-start [:start :action]}
                 (contains? sections :if) (assoc :m-if [:context :action])
                 (contains? sections :however) (assoc :m-however [:constraint :action])
                 (contains? sections :because) (assoc :m-because [:rationale :action])
                 (contains? sections :next-steps) (assoc :m-next [:action :future]))]
    morphs))

(defn- add-composition
  "Add composed morphisms for action->future chains."
  [morphs]
  (let [next? (contains? morphs :m-next)
        sources (->> (keys morphs)
                     (filter #(not= % :m-next))
                     (filter #(= :action (second (get morphs %)))))]
    (if (and next? (seq sources))
      (reduce (fn [acc k]
                (let [src (first (get morphs k))
                      composed (keyword (str (name k) "-next"))]
                  (-> acc
                      (update :morphisms assoc composed [src :future])
                      (update :compose assoc [:m-next k] composed))))
              {:morphisms morphs :compose {}}
              sources)
      {:morphisms morphs :compose {}})))

(defn simple-if-then
  "Compress a flexiarg into a tiny CT template using IF/HOWEVER/THEN/BECAUSE/NEXT-STEPS.
  Returns {:ct-template (category {...}) :sections #{...}} or nil if file missing."
  [{:keys [pattern-id pattern-root] :or {pattern-root "futon3/library"}}]
  (let [path (pattern-id->path pattern-root pattern-id)
        lines (read-lines path)]
    (when (seq lines)
      (let [sections (extract-sections lines)
            objects (ensure-objects sections)
            morphs (ensure-morphisms sections)
            {:keys [morphisms compose]} (add-composition morphs)
            template {:name (keyword "exotic" (str "vision-" (str/replace pattern-id #"/" "-")))
                      :objects (vec objects)
                      :morphisms morphisms
                      :compose compose}]
        {:ct-template (list 'category template)
         :sections sections
         :path path}))))

(def registry
  {:id :simple-if-then
   :version 1
   :fn simple-if-then
   :description "Rule-based CT compressor using IF/HOWEVER/THEN/BECAUSE/NEXT-STEPS sections."})
