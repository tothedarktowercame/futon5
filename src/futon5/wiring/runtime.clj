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
;;; Pattern Failure Detectors
;;; ============================================================

(defn row-uniformity
  "Measure how uniform a row is (fraction of cells matching mode)."
  [row]
  (if (empty? row)
    1.0
    (let [freqs (frequencies row)
          mode-count (apply max (vals freqs))]
      (/ (double mode-count) (count row)))))

(defn detect-barcode
  "Detect BARCODE pattern: horizontal stripes (uniform rows).
   Returns score 0-1 where 1 = definite barcode."
  [history]
  (if (or (empty? history) (< (count history) 5))
    0.0
    (let [;; Skip first few rows (may be initialization artifacts)
          rows (drop 3 history)
          uniformities (map row-uniformity rows)
          ;; Barcode if most rows are highly uniform
          high-uniform (count (filter #(> % 0.8) uniformities))]
      (/ (double high-uniform) (count rows)))))

(defn diagonal-shift
  "Shift a row by n positions (circular)."
  [row n]
  (let [len (count row)
        n (mod n len)]
    (str (subs row n) (subs row 0 n))))

(defn row-correlation
  "Measure correlation between two rows (fraction matching)."
  [row1 row2]
  (if (or (empty? row1) (empty? row2) (not= (count row1) (count row2)))
    0.0
    (let [matches (count (filter true? (map = row1 row2)))]
      (/ (double matches) (count row1)))))

(defn detect-candycane
  "Detect CANDYCANE pattern: diagonal stripes.
   Checks if shifting row N by N correlates with row 0.
   Also checks for consistent diagonal structure across the history.
   Returns score 0-1 where 1 = definite candycane."
  [history]
  (if (or (empty? history) (< (count history) 10))
    0.0
    (let [rows (vec (map str history))
          ;; Method 1: Check diagonal shift correlation with base row
          base-row (nth rows 5) ;; Skip initial transient
          correlations
          (for [offset (range 1 (min 30 (- (count rows) 5)))]
            (let [shifted-base (diagonal-shift base-row offset)
                  target-row (get rows (+ 5 offset))]
              (when target-row
                (row-correlation shifted-base target-row))))
          valid-corrs (filter some? correlations)
          high-corrs (count (filter #(> % 0.5) valid-corrs))
          method1-score (if (empty? valid-corrs) 0.0 (/ (double high-corrs) (count valid-corrs)))

          ;; Method 2: Check if adjacent rows are just shifts of each other
          shift-correlations
          (for [i (range 10 (min 40 (dec (count rows))))]
            (let [row-a (get rows i)
                  row-b (get rows (inc i))
                  ;; Try shifts of 1 and 2
                  corr1 (row-correlation (diagonal-shift row-a 1) row-b)
                  corr2 (row-correlation (diagonal-shift row-a 2) row-b)]
              (max corr1 corr2)))
          valid-shifts (filter some? shift-correlations)
          high-shifts (count (filter #(> % 0.6) valid-shifts))
          method2-score (if (empty? valid-shifts) 0.0 (/ (double high-shifts) (count valid-shifts)))]
      (max method1-score method2-score))))

(defn detect-failures
  "Detect pattern failures in a run history.
   Returns map of failure types with scores."
  [history]
  (let [barcode (detect-barcode history)
        candycane (detect-candycane history)]
    {:barcode barcode
     :candycane candycane
     :fails? (or (> barcode 0.5) (> candycane 0.4))
     :failure-types (cond-> []
                      (> barcode 0.5) (conj :barcode)
                      (> candycane 0.4) (conj :candycane))}))

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
