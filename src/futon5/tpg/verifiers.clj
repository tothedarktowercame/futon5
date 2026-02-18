(ns futon5.tpg.verifiers
  "Verifier evaluation for TPG-controlled MMCA runs.

   A verifier checks whether a diagnostic value falls within a target band.
   Verifier specs use the same format as xenotype target bands:
     [center width]
   where satisfaction means |value - center| < width.

   Fitness is a constraint satisfaction vector (one entry per verifier),
   NOT a scalar. Selection uses Pareto dominance."
  (:require [futon5.tpg.diagnostics :as diag]))

(def default-spec
  "Default verifier specification: tai-zone targets.

   These are the same target bands from xenotype.clj default-spec,
   mapped to diagnostic vector dimensions."
  {:entropy  [0.6 0.35]    ; moderate-high entropy
   :change   [0.2 0.2]     ; moderate change, not stasis or confetti
   :autocorr [0.6 0.3]     ; temporal structure present
   :diversity [0.4 0.3]})  ; symbol diversity moderate

(defn- band-score
  "Score how well value x satisfies band [center, width].
   Returns 1.0 at center, 0.0 at center +/- width, linearly interpolated."
  [x center width]
  (when (and (number? x) (number? center) (pos? (double width)))
    (max 0.0 (- 1.0 (/ (Math/abs (- (double x) (double center)))
                        (double width))))))

(defn- band-satisfied?
  "Is value x within the target band [center, width]?"
  [x center width]
  (when-let [score (band-score x center width)]
    (> score 0.0)))

(defn evaluate-diagnostic
  "Evaluate a single diagnostic against the verifier spec.

   Returns a map from verifier key to {:score double :satisfied? boolean}."
  [diagnostic-named spec]
  (reduce-kv
   (fn [acc verifier-key [center width]]
     (let [;; Map verifier keys to diagnostic keys
           diag-key (case verifier-key
                      :entropy :entropy
                      :change :change
                      :autocorr :autocorr
                      :diversity :diversity
                      :phenotype-coupling :phenotype-coupling
                      :damage-spread :damage-spread
                      verifier-key)
           value (get diagnostic-named diag-key)]
       (if value
         (assoc acc verifier-key
                {:score (or (band-score value center width) 0.0)
                 :satisfied? (boolean (band-satisfied? value center width))
                 :value value})
         acc)))
   {}
   spec))

(defn evaluate-run
  "Evaluate an entire run's diagnostic trace against verifier spec.

   diagnostics-trace: vector of diagnostic maps (from diagnostics/diagnostics-trace)
   spec: verifier specification (default: tai-zone targets)

   Returns:
   {:satisfaction-vector {verifier-key fraction-satisfied}
    :per-generation [{verifier-key {:score :satisfied? :value}} ...]
    :overall-satisfaction double  — fraction of (generation, verifier) pairs satisfied
    :generation-count int}"
  [diagnostics-trace spec]
  (let [spec (or spec default-spec)
        n (count diagnostics-trace)
        per-gen (mapv (fn [diag]
                        (when diag
                          (evaluate-diagnostic (merge (:named diag) (:extended diag)) spec)))
                      diagnostics-trace)
        ;; Per-verifier satisfaction rate
        verifier-keys (keys spec)
        satisfaction-vector
        (into {}
              (map (fn [vk]
                     (let [satisfied-count
                           (count (filter (fn [gen-eval]
                                           (get-in gen-eval [vk :satisfied?]))
                                         (remove nil? per-gen)))]
                       [vk (if (pos? n)
                             (/ (double satisfied-count) (double n))
                             0.0)])))
              verifier-keys)
        ;; Overall: fraction of all (gen, verifier) pairs satisfied
        total-checks (* n (count verifier-keys))
        total-satisfied (reduce + 0 (map (fn [vk]
                                           (count (filter #(get-in % [vk :satisfied?])
                                                          (remove nil? per-gen))))
                                         verifier-keys))
        overall (if (pos? total-checks)
                  (/ (double total-satisfied) (double total-checks))
                  0.0)]
    {:satisfaction-vector satisfaction-vector
     :per-generation per-gen
     :overall-satisfaction overall
     :generation-count n}))

;; =============================================================================
;; PARETO DOMINANCE
;; =============================================================================

(defn dominates?
  "Does solution A Pareto-dominate solution B?

   A dominates B iff:
   - For all verifiers: satisfaction(A) >= satisfaction(B)
   - For at least one verifier: satisfaction(A) > satisfaction(B)"
  [sat-vec-a sat-vec-b]
  (let [keys (set (concat (keys sat-vec-a) (keys sat-vec-b)))
        comparisons (map (fn [k]
                           (let [a (get sat-vec-a k 0.0)
                                 b (get sat-vec-b k 0.0)]
                             (compare a b)))
                         keys)]
    (and (every? #(>= % 0) comparisons)
         (some #(pos? %) comparisons))))

(defn pareto-rank
  "Assign Pareto ranks to a collection of satisfaction vectors.

   Returns a vector of {:index i :rank r :satisfaction-vector sv}
   where rank 0 = non-dominated front, rank 1 = dominated by rank 0 only, etc."
  [satisfaction-vectors]
  (let [n (count satisfaction-vectors)
        indexed (mapv (fn [i sv] {:index i :satisfaction-vector sv})
                      (range) satisfaction-vectors)
        ;; Count how many solutions dominate each one
        domination-counts
        (mapv (fn [i]
                (count (filter (fn [j]
                                 (and (not= i j)
                                      (dominates? (:satisfaction-vector (nth indexed j))
                                                  (:satisfaction-vector (nth indexed i)))))
                               (range n))))
              (range n))]
    ;; Rank = number of solutions that dominate this one
    ;; (This is a simple ranking; proper NSGA-II uses fronts, but this suffices)
    (mapv (fn [i]
            (assoc (nth indexed i) :rank (nth domination-counts i)))
          (range n))))
