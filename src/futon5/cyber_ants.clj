(ns futon5.cyber-ants
  "Bridge Futon3 ant patterns to executable Futon5 operator data.
   Provides helpers for Futon2/Futon5 to fetch cyber-ant configs."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ct.dsl :as ct]))

(def default-pattern-id :cyber/baseline)

(defn- load-cyber-ants []
  (-> "futon5/cyber_ants.edn" io/resource slurp edn/read-string))

(defn- load-sigil-patterns []
  (-> "futon5/sigil_patterns.edn" io/resource slurp edn/read-string))

(defn patterns-by-id []
  (->> (load-cyber-ants)
       :patterns
       (map (fn [{:keys [id] :as entry}]
              [id entry]))
       (into {})))

(defonce ^:private cache* (delay (patterns-by-id)))
(defonce ^:private sigil-patterns*
  (delay (->> (load-sigil-patterns)
              (map (fn [{:keys [sigil] :as entry}]
                     [sigil entry]))
              (into {}))))

(def ^:private role->cyber-id
  {"WhiteSpaceScout" :cyber/white-space-scout
   "HungerPrecision" :cyber/hunger-precision
   "HyperAnt" :cyber/trail-tuner
   "Accumulator" :cyber/cargo-discipline})

(def ^:private role->ct-primitive
  {"BlendHand" :blend-hand
   "UpliftOperator" :uplift-operator
   "WhiteSpaceScout" :white-space-scout
   "HungerPrecision" :hunger-precision
   "Facet" :facet
   "HyperAnt" :hyperant
   "Accumulator" :accumulator
   "FreezeGenotype" :freeze-genotype
   "LockKernel" :lock-kernel
   "RandomGenotype" :random-genotype
   "GlobalRuleGenotype" :global-rule-genotype
   "UseOperators" :use-operators
   "PhenotypeOn" :phenotype-on
   "GenotypeGate" :genotype-gate})

(defn pattern-entry [pattern-id]
  (let [patterns @cache*]
    (or (get patterns pattern-id)
        (get patterns default-pattern-id))))

(defn cyber-config [pattern-id]
  (some-> pattern-id pattern-entry (select-keys [:id :title :summary :narrative :operator :aif :telemetry])))

(defn merge-configs
  "Merge multiple pattern configs into one composite config, deep-merging :aif maps."
  [ids]
  (let [entries (map cyber-config ids)]
    {:ids (map :id entries)
     :title (str/join " + " (map :title entries))
     :summary (str/join "; " (map :summary entries))
     :narrative (str/join "\n\n" (map :narrative entries))
     :operators (vec (distinct (keep :operator entries)))
     :aif (apply merge-with merge (keep :aif entries))
     :telemetry (vec (distinct (keep :telemetry entries)))}))

(defn sigil->pattern-id
  "Translate a sigil into a known cyber-ant pattern id when possible."
  [sigil]
  (let [role (get-in @sigil-patterns* [sigil :role])]
    (get role->cyber-id role)))

(defn pattern-ids-for-sigils
  "Return distinct cyber-ant pattern ids derived from sigils."
  [sigils]
  (->> sigils
       (map sigil->pattern-id)
       (remove nil?)
       distinct
       vec))

(defn- ordered-distinct [xs]
  (reduce (fn [acc x]
            (if (some #{x} acc) acc (conj acc x)))
          []
          xs))

(defn- sigil->role [sigil]
  (get-in @sigil-patterns* [sigil :role]))

(defn- sigil->ct-primitive [sigil]
  (some-> (sigil->role sigil) role->ct-primitive))

(defn- build-ct-blueprint
  "Create a lightweight CT blueprint using a single :world object."
  [sigils]
  (let [roles (map sigil->role sigils)
        primitives (->> sigils
                        (map sigil->ct-primitive)
                        (remove nil?)
                        ordered-distinct)
        diagrams (map (fn [name]
                        (ct/primitive-diagram {:name name
                                               :domain [:world]
                                               :codomain [:world]}))
                      primitives)
        diagram (cond
                  (empty? diagrams) (ct/identity-diagram :world)
                  (= 1 (count diagrams)) (first diagrams)
                  :else (apply ct/compose-diagrams diagrams))]
    {:object :world
     :operators primitives
     :roles (vec (ordered-distinct (remove nil? roles)))
     :diagram (into {} diagram)
     :note "Blueprint composes operator morphisms as world transforms."}))

(defn flexiarg-filename
  "Derive a flexiarg filename from a pattern id."
  [pattern-id]
  (str (name pattern-id) ".flexiarg"))

(defn flexiarg-content
  "Render a flexiarg file for a generated cyber-ant pattern."
  [{:keys [id title summary narrative aif ct] :as pattern}]
  (let [sigils (get-in pattern [:source :sigils])
        sigil-line (when (seq sigils)
                     (str "@sigils [" (str/join " " sigils) "]\n"))
        aif-delta (or aif {})
        ct-edn (when ct (pr-str ct))
        conclusion (or narrative summary "Auto-generated cyber-ant pattern.")]
    (str "@flexiarg ants/" (name id) "\n"
         "@title " (or title (name id)) "\n"
         (or sigil-line "")
         "@audience futon5 improvised cyber-ants, futon2 cyber-aif\n"
         "@tone exploratory\n"
         "@style pattern\n"
         "@allow-new-claims true\n"
         "@aif-delta " (pr-str aif-delta) "\n"
         (when ct-edn (str "@ct-blueprint " ct-edn "\n"))
         "\n"
         "! conclusion:\n  "
         (str/replace (str/trim conclusion) "\n" "\n  ")
         "\n")))

(defn write-flexiarg!
  "Write a generated flexiarg file to the given directory."
  [pattern dir]
  (let [filename (flexiarg-filename (:id pattern))
        target (io/file dir filename)]
    (spit target (flexiarg-content pattern))
    (.getPath target)))

(defn- build-auto-id [seed run-index]
  (keyword (format "cyber/auto-%s-%02d"
                   (or seed (System/currentTimeMillis))
                   (long (or run-index 0)))))

(defn propose-cyber-ant
  "Generate a composite cyber-ant config from a sigil list."
  ([sigils] (propose-cyber-ant sigils {}))
  ([sigils {:keys [base-id id title summary seed run-index policy rule]}]
   (let [base-id (or base-id default-pattern-id)
         sigils (vec (distinct (remove nil? sigils)))
         ids (vec (distinct (concat [base-id] (pattern-ids-for-sigils sigils))))
         merged (merge-configs ids)
         sigil-str (str/join " " sigils)
         operator (let [ops (:operators merged)]
                    (if (= 1 (count ops)) (first ops) :UpliftOperator))]
     {:id (or id (build-auto-id seed run-index))
      :title (or title (str "Auto Cyber-Ant (" sigil-str ")"))
      :summary (or summary (str "Generated from meta-lift sigils: " sigil-str))
      :narrative (:narrative merged)
      :ct (build-ct-blueprint sigils)
      :operator operator
      :operators (:operators merged)
      :aif (:aif merged)
      :telemetry (:telemetry merged)
      :flexiarg (:flexiarg (pattern-entry base-id))
      :source {:sigils sigils
               :pattern-ids ids
               :policy policy
               :rule rule
               :seed seed}})))
