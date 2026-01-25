(ns futon5.wiring.runtime
  "Runtime for executing wiring diagrams as cellular automata.

   This is the canonical way to run wirings. For legacy MMCA runs,
   see futon5.mmca.runtime (which does NOT execute wirings)."
  (:require [clojure.edn :as edn]
            [futon5.ca.core :as ca]
            [futon5.xenotype.interpret :as interpret]
            [futon5.xenotype.generator :as generator]))

;;; ============================================================
;;; Core Evolution
;;; ============================================================

(defn evolve-cell
  "Apply wiring diagram to a single cell.

   ctx should contain:
   - :pred - predecessor sigil (string)
   - :self - self sigil (string)
   - :succ - successor sigil (string)

   Returns the output sigil (string)."
  [diagram ctx]
  (let [result (interpret/evaluate-diagram diagram {:ctx ctx} generator/generator-registry)
        output-node (:output diagram)
        output-value (get-in result [:node-values output-node :out])]
    (or output-value (:self ctx))))

(defn evolve-genotype
  "Evolve entire genotype string using wiring diagram.

   Applies the wiring rule to each cell position, using circular
   boundary conditions (wraps around)."
  [diagram genotype]
  (let [len (count genotype)
        chars (vec (seq genotype))]
    (apply str
           (for [i (range len)]
             (let [ctx {:pred (str (get chars (mod (dec i) len)))
                        :self (str (get chars i))
                        :succ (str (get chars (mod (inc i) len)))}]
               (evolve-cell diagram ctx))))))

(defn evolve-phenotype
  "Evolve phenotype against genotype (standard CA rule)."
  [genotype phenotype]
  (ca/evolve-phenotype-against-genotype genotype phenotype))

;;; ============================================================
;;; Metrics
;;; ============================================================

(defn- log2 [x]
  (/ (Math/log x) (Math/log 2.0)))

(defn shannon-entropy
  "Calculate Shannon entropy of a string (in bits)."
  [s]
  (let [chars (seq (or s ""))
        total (double (count chars))]
    (if (pos? total)
      (- (reduce (fn [acc [_ cnt]]
                   (let [p (/ cnt total)]
                     (if (pos? p)
                       (+ acc (* p (log2 p)))
                       acc)))
                 0.0
                 (frequencies chars)))
      0.0)))

(defn normalized-entropy
  "Entropy normalized to [0,1] based on unique symbols."
  [s]
  (let [ent (shannon-entropy s)
        unique (count (set s))
        max-ent (if (> unique 1) (log2 unique) 1.0)]
    (/ ent max-ent)))

(defn change-rate
  "Fraction of positions that changed between two strings."
  [s1 s2]
  (when (and s1 s2 (= (count s1) (count s2)))
    (let [diffs (count (filter (fn [[a b]] (not= a b)) (map vector s1 s2)))]
      (/ (double diffs) (count s1)))))

(defn compute-metrics
  "Compute metrics for a generation."
  [genotype prev-genotype phenotype]
  {:entropy (shannon-entropy genotype)
   :entropy-n (normalized-entropy genotype)
   :unique-sigils (count (set genotype))
   :change-rate (when prev-genotype (change-rate prev-genotype genotype))
   :length (count genotype)})

;;; ============================================================
;;; Main Runner
;;; ============================================================

(defn run-wiring
  "Run a wiring diagram as a cellular automaton.

   opts:
   - :wiring      - wiring map with :diagram key (required)
   - :genotype    - initial genotype string (required)
   - :phenotype   - initial phenotype string (optional)
   - :generations - number of generations to run (default 32)
   - :collect-metrics? - whether to compute per-generation metrics (default true)

   Returns:
   - :gen-history    - vector of genotype strings
   - :phe-history    - vector of phenotype strings (if phenotype provided)
   - :metrics-history - vector of per-generation metrics (if collect-metrics?)
   - :wiring-id      - ID from wiring metadata
   - :generations    - actual generations run"
  [{:keys [wiring genotype phenotype generations collect-metrics?]
    :or {generations 32 collect-metrics? true}}]
  (when-not wiring
    (throw (ex-info "run-wiring requires :wiring" {})))
  (when-not genotype
    (throw (ex-info "run-wiring requires :genotype" {})))
  (let [diagram (:diagram wiring)
        wiring-id (get-in wiring [:meta :id] :unknown)]
    (loop [gen-history [genotype]
           phe-history (when phenotype [phenotype])
           metrics-history (when collect-metrics?
                            [(compute-metrics genotype nil phenotype)])
           current-gen genotype
           current-phe phenotype
           gen 0]
      (if (>= gen generations)
        (cond-> {:gen-history gen-history
                 :wiring-id wiring-id
                 :generations (count gen-history)}
          phe-history (assoc :phe-history phe-history)
          metrics-history (assoc :metrics-history metrics-history))
        (let [next-gen (evolve-genotype diagram current-gen)
              next-phe (when current-phe (evolve-phenotype current-gen current-phe))
              next-metrics (when collect-metrics?
                            (compute-metrics next-gen current-gen next-phe))]
          (recur (conj gen-history next-gen)
                 (when phe-history (conj phe-history next-phe))
                 (when metrics-history (conj metrics-history next-metrics))
                 next-gen
                 next-phe
                 (inc gen)))))))

(defn load-wiring
  "Load a wiring from an EDN file."
  [path]
  (edn/read-string (slurp path)))

(defn random-genotype
  "Generate a random genotype string."
  [length seed]
  (let [rng (java.util.Random. (long seed))
        sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (.nextInt rng (count sigils)))))))

;;; ============================================================
;;; Convenience
;;; ============================================================

(defn run-wiring-from-file
  "Load wiring from file and run it.

   opts same as run-wiring, but :wiring-path instead of :wiring."
  [{:keys [wiring-path] :as opts}]
  (let [wiring (load-wiring wiring-path)]
    (run-wiring (assoc opts :wiring wiring))))
