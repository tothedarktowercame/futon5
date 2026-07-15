(ns futon5.mmca.particle-detection
  "Domain / particle decomposition over a local-causal-state field — steps 4-5
   of EVALUATOR-SPEC §3.9, completing the CSSR build.

   `futon5.mmca.local-causal-states` supplies steps 1-3 (light-cones -> CSSR
   clustering with a significance test -> a causal-state field). This namespace
   reads that field and extracts the STRUCTURE:

     domain    — a spatiotemporally homogeneous causal-state region (the regular
                 background the bulk of the spacetime obeys)
     particle  — a coherent inhomogeneity: a cell whose causal state deviates
                 from its neighbourhood, i.e. the structured boundary between
                 domains (gliders, domain walls)

   Why the causal-state route: for ECAs the domains are catalogued, but for
   MetaCA they are UNKNOWN and must be discovered (EVALUATOR-SPEC §3.8's
   'genuinely hard part'). The causal states do that discovery intrinsically —
   a homogeneous causal-state region IS a domain, with no rule catalogue needed.

   THE EoC SIGNATURE IS A CONJUNCTION, NOT A DENSITY (§3.8). Raw defect-density
   fails because chaos maximises it. A spacetime is edge-of-chaos iff it
   decomposes into regular domains WITH sparse propagating particles:

     complex  — high domain-coverage AND nonzero particles  -> fires
     chaotic  — LOW domain-coverage (no regular background)  -> fails domain half
     frozen   — high domain-coverage but ZERO particles      -> fails particle half

   so `eoc-score = domain-coverage * particle-density`.

   NOTE ON NAMING: the spec writes the aggregate as `domainCoverage x
   particleSparsity`. Read literally as (1 - density), 'sparsity' would make
   FROZEN score highest (coverage 1 x sparsity 1), which inverts the intended
   conjunction. The term must vanish when there are no particles, so we use
   particle-density — nonzero-and-modest for complex, ~0 for frozen, high for
   chaotic (where the domain half already kills the product). This implements
   the conjunction the spec DESCRIBES."
  (:require [futon5.mmca.local-causal-states :as lcs]))

(defn- neighbours
  "Spatiotemporal neighbours of [t x] in a field: left/right/up/down."
  [field t x]
  (let [times (count field)
        width (count (first field))]
    (for [[dt dx] [[0 -1] [0 1] [-1 0] [1 0]]
          :let [t' (+ t dt) x' (+ x dx)]
          :when (and (<= 0 t') (< t' times) (<= 0 x') (< x' width))]
      (get-in field [t' x']))))

(defn classify-field
  "Label each causal-state-field cell as :domain, :particle, or nil.

   A labelled cell is a DOMAIN cell when every labelled neighbour shares its
   causal state (a homogeneous neighbourhood = the regular background); it is a
   PARTICLE cell when some labelled neighbour differs (a structured boundary).
   Unlabelled cells (margins / unresolved pasts) stay nil."
  [field]
  (let [times (count field)
        width (count (first field))]
    (mapv (fn [t]
            (mapv (fn [x]
                    (when-let [s (get-in field [t x])]
                      (let [ns (remove nil? (neighbours field t x))]
                        (cond
                          (empty? ns) nil
                          (every? #(= % s) ns) :domain
                          :else :particle))))
                  (range width)))
          (range times))))

(defn decompose
  "Decompose a causal-state field into domains and particles.

   Returns:
     {:domain-coverage  fraction of labelled cells in homogeneous domains
      :particle-density fraction of labelled cells that are boundaries
      :eoc-score        domain-coverage * particle-density  (the §3.8 conjunction)
      :n-labelled       cells the model could label
      :n-states         distinct causal states present in the field
      :classified       the :domain/:particle field}"
  [field]
  (let [classified (classify-field field)
        cells (remove nil? (flatten classified))
        n (count cells)
        n-dom (count (filter #(= :domain %) cells))
        n-par (count (filter #(= :particle %) cells))
        coverage (if (pos? n) (/ (double n-dom) n) 0.0)
        density (if (pos? n) (/ (double n-par) n) 0.0)
        states (count (disj (set (flatten field)) nil))]
    {:domain-coverage coverage
     :particle-density density
     :eoc-score (* coverage density)
     :n-labelled n
     :n-states states
     :classified classified}))

(defn observe
  "PURE OBSERVE MODE: reconstruct local causal states from a spacetime and
   report its structural decomposition. No control, no training — this is the
   analysis instrument.

   `grid` is a spacetime (vector of rows; rows may be strings or vectors).
   Returns the decomposition plus the reconstructed model's summary."
  ([grid] (observe grid {}))
  ([grid {:keys [past-depth future-depth alpha min-support]
          :or {past-depth 2 future-depth 1 alpha 0.01 min-support 20}}]
   (let [{:keys [model field]} (lcs/reconstruct grid {:past-depth past-depth
                                                      :future-depth future-depth
                                                      :alpha alpha
                                                      :min-support min-support})
         d (decompose field)]
     (assoc d
            :model-states (count (:states model))
            :distinct-pasts (:distinct-pasts model)
            :sample-count (:sample-count model)
            :field field))))
