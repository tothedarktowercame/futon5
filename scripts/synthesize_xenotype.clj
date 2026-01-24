(ns synthesize-xenotype
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.xenotype.wiring :as wiring]))

(defn- usage []
  (str/join
   "\n"
   ["Generate candidate xenotype wiring diagrams from templates."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/synthesize_xenotype.clj [options]"
    ""
    "Options:"
    "  --n N            Number of candidates (default 20)."
    "  --seed N         RNG seed (default 4242)."
    "  --mutations N    Mutations per candidate (default 0)."
    "  --mutation-mix EDN  Map of mutation weights (default {:swap 0.5 :ablate 0.5 :rewire 0.5})."
    "  --templates PATH Template EDN (default futon5/resources/xenotype-templates.edn)."
    "  --components PATH Component library EDN (default resources/xenotype-generator-components.edn)."
    "  --out PATH       Output EDN (default /tmp/xenotype-synth-<ts>.edn)."
    "  --help           Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-edn [s]
  (try (edn/read-string s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--n" flag)
          (recur (rest more) (assoc opts :n (parse-int (first more))))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--mutations" flag)
          (recur (rest more) (assoc opts :mutations (parse-int (first more))))

          (= "--mutation-mix" flag)
          (recur (rest more) (assoc opts :mutation-mix (parse-edn (first more))))

          (= "--templates" flag)
          (recur (rest more) (assoc opts :templates (first more)))

          (= "--components" flag)
          (recur (rest more) (assoc opts :components (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- pick [^java.util.Random rng coll]
  (nth coll (rng-int rng (count coll))))

(defn- components-by-kind
  [lib]
  (reduce (fn [m [id comp]]
            (update m (:kind comp) (fnil conj []) id))
          {}
          (:components lib)))

(defn- component-inputs
  [lib component-id]
  (into {} (map (fn [[name type]] [name type])
                (:inputs (wiring/component-def lib component-id)))))

(defn- component-outputs
  [lib component-id]
  (into {} (map (fn [[name type]] [name type])
                (:outputs (wiring/component-def lib component-id)))))

(defn- default-port
  [ports]
  (first (keys ports)))

(defn- swap-component
  [^java.util.Random rng by-kind node]
  (let [kind (:kind node)
        options (vec (remove #{(:component node)} (get by-kind kind [])))]
    (if (seq options)
      (assoc node :component (pick rng options))
      node)))

(defn- ablate-edge
  [^java.util.Random rng edges]
  (let [candidates (vec (filter :from edges))]
    (if (seq candidates)
      (let [edge (pick rng candidates)]
        (vec (remove #(= % edge) edges)))
      edges)))

(defn- rewire-edge
  [^java.util.Random rng lib diagram]
  (let [nodes (:nodes diagram)
        edges (:edges diagram)
        edge (pick rng (vec edges))
        to-id (:to edge)
        from-id (:from edge)
        to-node (first (filter #(= (:id %) to-id) nodes))
        to-inputs (component-inputs lib (:component to-node))
        to-port (or (:to-port edge) (default-port to-inputs))
        expected-type (get to-inputs to-port)
        candidates (->> nodes
                        (filter #(not= (:id %) to-id))
                        (filter (fn [node]
                                  (let [outputs (component-outputs lib (:component node))
                                        default (default-port outputs)]
                                    (some (fn [[_ t]] (or (= t expected-type)
                                                          (and (= expected-type :scalar-list) (= t :scalar))))
                                          outputs))))
                        vec)]
    (if (seq candidates)
      (let [new-from (pick rng candidates)
            new-port (default-port (component-outputs lib (:component new-from)))
            new-edge (assoc edge :from (:id new-from) :from-port new-port)]
        (vec (conj (remove #(= % edge) edges) new-edge)))
      edges)))

(defn- normalized-mix
  [mix]
  (let [mix (merge {:swap 0.5 :ablate 0.5 :rewire 0.5} (or mix {}))
        total (reduce + 0.0 (vals mix))]
    (if (pos? total)
      (into {} (map (fn [[k v]] [k (/ (double v) total)]) mix))
      {:swap 0.34 :ablate 0.33 :rewire 0.33})))

(defn- pick-mutation
  [^java.util.Random rng mix]
  (let [r (.nextDouble rng)
        mix (normalized-mix mix)
        swap-w (:swap mix)
        ablate-w (:ablate mix)]
    (cond
      (< r swap-w) :swap
      (< r (+ swap-w ablate-w)) :ablate
      :else :rewire)))

(defn- mutate-diagram
  [^java.util.Random rng lib diagram mix]
  (let [by-kind (components-by-kind lib)
        choice (pick-mutation rng mix)]
    (case choice
      :swap (let [idx (rng-int rng (count (:nodes diagram)))
                  node (nth (:nodes diagram) idx)
                  mutated (swap-component rng by-kind node)
                  nodes (assoc (:nodes diagram) idx mutated)]
              (assoc diagram :nodes nodes))
      :ablate (assoc diagram :edges (ablate-edge rng (:edges diagram)))
      :rewire (assoc diagram :edges (rewire-edge rng lib diagram))
      diagram)))

(defn- instantiate-template
  [^java.util.Random rng lib template]
  (let [by-kind (components-by-kind lib)
        nodes (mapv (fn [node]
                      (let [kind (:kind node)
                            options (when kind (get by-kind kind []))
                            component (or (:component node)
                                          (when (seq options) (pick rng options)))]
                        (assoc node :component component)))
                    (:nodes template))]
    (assoc template :nodes nodes)))

(defn- apply-mutations
  [^java.util.Random rng lib diagram n mix]
  (loop [diagram diagram
         remaining n]
    (if (<= remaining 0)
      diagram
      (let [mutated (mutate-diagram rng lib diagram mix)
            validation (wiring/validate-diagram lib mutated)]
        (recur (if (:ok? validation) mutated diagram)
               (dec remaining))))))

(defn- generate-candidates
  [^java.util.Random rng lib templates n mutations mix]
  (loop [cands []
         attempts 0]
    (if (>= (count cands) n)
      cands
      (let [template (pick rng templates)
            diagram (instantiate-template rng lib template)
            diagram (apply-mutations rng lib diagram (int (or mutations 0)) mix)
            validation (wiring/validate-diagram lib diagram)]
        (if (:ok? validation)
          (recur (conj cands (assoc diagram :validation validation)) (inc attempts))
          (recur cands (inc attempts)))))))

(defn -main [& args]
  (let [{:keys [help unknown n seed mutations mutation-mix templates components out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println) (println (usage)))
      :else
      (let [n (int (or n 20))
            seed (long (or seed 4242))
            mutations (int (or mutations 0))
            mix (normalized-mix mutation-mix)
            templates-path (or templates "futon5/resources/xenotype-templates.edn")
            components-path (or components "resources/xenotype-generator-components.edn")
            out (or out (format "/tmp/xenotype-synth-%d.edn" (System/currentTimeMillis)))
            rng (java.util.Random. seed)
            templates (get (edn/read-string (slurp templates-path)) :templates)
            lib (edn/read-string (slurp components-path))
            candidates (generate-candidates rng lib templates n mutations mix)]
        (spit out (pr-str {:seed seed
                           :count (count candidates)
                           :mutations mutations
                           :mutation-mix mix
                           :templates (map :name templates)
                           :candidates candidates}))
        (println "Wrote" out)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
