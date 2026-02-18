(ns futon5.tpg.diagnostics
  "Generation-level diagnostic feature vectors for TPG routing.

   Computes a compact 6-dimensional diagnostic vector from a single
   generation's state. Used by TPG programs to determine which operator
   to apply. All values are normalized to [0, 1].

   Diagnostic dimensions:
     0  H  — normalized Shannon entropy of the genotype row
     1  Δ  — Hamming change rate from previous generation
     2  ρ  — temporal autocorrelation (1 - change rate, smoothed)
     3  σ  — symbol diversity (unique / length)
     4  φ  — phenotype-genotype coupling (phe entropy, or 0 if no phenotype)
     5  λ  — damage spreading estimate (perturbation sensitivity)"
  (:require [futon5.ca.core :as ca]
            [futon5.mmca.metrics :as metrics]
            [futon5.mmca.domain-analysis :as domain]
            [futon5.mmca.particle-analysis :as particle]
            [futon5.mmca.bitplane-analysis :as bitplane]))

(def diagnostic-dim
  "Number of diagnostic dimensions."
  6)

(def diagnostic-keys
  "Ordered keys for the diagnostic vector."
  [:entropy :change :autocorr :diversity :phenotype-coupling :damage-spread])

(defn- clamp01 [x]
  (cond
    (nil? x) 0.0
    (Double/isNaN (double x)) 0.0
    (< (double x) 0.0) 0.0
    (> (double x) 1.0) 1.0
    :else (double x)))

(defn- normalized-entropy
  "Shannon entropy of string s, normalized by log2(length)."
  [s]
  (let [entropy (metrics/shannon-entropy s)
        len (count s)
        max-e (when (pos? len)
                (/ (Math/log (double len)) (Math/log 2.0)))]
    (if (and max-e (pos? max-e))
      (clamp01 (/ entropy max-e))
      0.0)))

(defn- change-rate
  "Hamming distance rate between two strings."
  [prev curr]
  (if (and prev curr (= (count prev) (count curr)) (pos? (count curr)))
    (clamp01 (metrics/hamming-rate prev curr))
    0.0))

(defn- symbol-diversity
  "Ratio of unique symbols to total length."
  [s]
  (let [len (count s)]
    (if (pos? len)
      (clamp01 (/ (double (count (frequencies (seq s)))) (double len)))
      0.0)))

(defn- phenotype-coupling
  "Normalized entropy of phenotype row, or 0.0 if no phenotype."
  [phenotype]
  (if (and phenotype (pos? (count phenotype)))
    (normalized-entropy phenotype)
    0.0))

(defn- damage-spread-estimate
  "Estimate perturbation sensitivity from recent history.

   Computes: for each position, how often does a change at position i
   correlate with changes at positions i-1 and i+1 in the next generation?
   This approximates the Lyapunov exponent without running a separate
   perturbed simulation.

   Returns a value in [0, 1] where:
   - 0 = changes don't spread (frozen/Class I)
   - 0.5 = moderate spreading (edge of chaos)
   - 1 = changes spread everywhere (chaotic/Class III)"
  [gen-history]
  (if (< (count gen-history) 3)
    0.5 ;; insufficient data, assume neutral
    (let [;; Look at last 3 generations
          recent (take-last 3 gen-history)
          [g0 g1 g2] recent
          len (min (count g0) (count g1) (count g2))
          ;; Find positions that changed between g0→g1
          changed-01 (set (for [i (range len)
                                :when (not= (nth g0 i) (nth g1 i))]
                            i))
          ;; Find positions that changed between g1→g2
          changed-12 (set (for [i (range len)
                                :when (not= (nth g1 i) (nth g2 i))]
                            i))
          ;; For each position that changed in 01, check if neighbors changed in 12
          spread-count
          (reduce (fn [acc i]
                    (let [neighbors (filter #(and (>= % 0) (< % len))
                                           [(dec i) i (inc i)])
                          spread? (some changed-12 neighbors)]
                      (if spread? (inc acc) acc)))
                  0
                  changed-01)]
      (if (pos? (count changed-01))
        (clamp01 (/ (double spread-count) (double (count changed-01))))
        0.0))))

(defn compute-diagnostic
  "Compute a diagnostic feature vector for the current generation.

   Arguments:
   - genotype: current generation string
   - prev-genotype: previous generation string (or nil)
   - phenotype: current phenotype string (or nil)
   - gen-history: vector of recent genotype strings (at least 3 for damage spread)

   Returns a map with:
   - :vector — double array of length 6 (the diagnostic vector)
   - :named — map from diagnostic-keys to values (for inspection)"
  [genotype prev-genotype phenotype gen-history]
  (let [h (normalized-entropy genotype)
        d (change-rate prev-genotype genotype)
        rho (clamp01 (- 1.0 d))
        sigma (symbol-diversity genotype)
        phi (phenotype-coupling phenotype)
        lambda (damage-spread-estimate gen-history)
        vec-vals [h d rho sigma phi lambda]
        named (zipmap diagnostic-keys vec-vals)]
    {:vector (double-array vec-vals)
     :named named}))

(defn diagnostic-from-run
  "Compute the generation-level diagnostic from a run result at generation t.

   Useful for post-hoc analysis of existing run data."
  [{:keys [gen-history phe-history]} t]
  (when (and gen-history (< t (count gen-history)))
    (let [genotype (nth gen-history t)
          prev-genotype (when (pos? t) (nth gen-history (dec t)))
          phenotype (when (and phe-history (< t (count phe-history)))
                      (nth phe-history t))
          ;; Provide up to 3 recent generations for damage spread
          recent-start (max 0 (- t 2))
          recent-history (subvec (vec gen-history) recent-start (inc t))]
      (compute-diagnostic genotype prev-genotype phenotype recent-history))))

(defn diagnostics-trace
  "Compute diagnostic vectors for every generation in a run.

   Returns a vector of diagnostic maps, one per generation."
  [{:keys [gen-history phe-history] :as run-result}]
  (mapv (fn [t] (diagnostic-from-run run-result t))
        (range (count gen-history))))

;; =============================================================================
;; EXTENDED DIAGNOSTICS (SCI Detection Pipeline)
;; =============================================================================

(def extended-diagnostic-keys
  "Extended diagnostic dimensions beyond the core 6D vector.
   These are run-level features (not per-generation) computed once per run."
  [:compression-cv :domain-fraction :particle-count :diag-autocorr-max
   :mean-coupling :coupling-cv
   ;; Spatial coupling keys (only populated when spatial?=true)
   :hotspot-fraction :spatial-coupling-mean])

(defn compute-extended-diagnostic
  "Compute extended diagnostic features from run history.
   These require full history and are computed once per run, not per generation.

   opts:
   - :spatial? (default false) — when true, includes hotspot-fraction
     and spatial-coupling-mean (3-5x slower coupling computation)

   Returns a map from extended-diagnostic-keys to [0,1] normalized values."
  ([run-result] (compute-extended-diagnostic run-result {}))
  ([{:keys [gen-history phe-history] :as run-result} opts]
   (let [history (or gen-history phe-history)
         spatial? (get opts :spatial? false)
         ;; Compression variance (bursty complexity)
         cv-result (metrics/compression-variance history)
         cv (or (:cv cv-result) 0.0)
         ;; Domain fraction (periodic background)
         domain-result (domain/analyze-domain history)
         domain-frac (or (:domain-fraction domain-result) 0.0)
         ;; Particle count
         particle-result (particle/analyze-particles history)
         raw-count (or (:particle-count particle-result) 0)
         particle-norm (Math/tanh (/ (double raw-count) 10.0))
         ;; Diagonal autocorrelation (already in metrics but not in 6D vector)
         autocorr (metrics/autocorr-metrics history)
         diag-ac (or (:diag-autocorr autocorr) 0.0)
         ;; Coupling spectrum
         coupling (bitplane/coupling-spectrum history
                    {:spatial? spatial? :temporal? false})
         mean-mi (or (:mean-coupling coupling) 0.0)
         c-cv (or (:coupling-cv coupling) 0.0)]
     (cond->
       {:compression-cv (clamp01 cv)
        :domain-fraction (clamp01 domain-frac)
        :particle-count (clamp01 particle-norm)
        :diag-autocorr-max (clamp01 diag-ac)
        :mean-coupling (clamp01 (* 4.0 mean-mi))      ;; normalize: MI maxes ~0.25
        :coupling-cv (clamp01 (/ c-cv 3.0))            ;; normalize CV to [0,1]
        ;; Pass through raw results for downstream use
        :domain-result domain-result
        :particle-result particle-result
        :coupling-result coupling}
       ;; Spatial coupling keys when spatial? is true
       spatial?
       (merge (let [sp (:spatial-profile coupling)]
                {:hotspot-fraction (clamp01 (or (:hotspot-fraction sp) 0.0))
                 :spatial-coupling-mean (clamp01 (* 4.0 (or (:mean sp) 0.0)))}))))))


(defn diagnostics-trace-extended
  "Compute diagnostic vectors with extended SCI dimensions.
   Extended dimensions are the same for all generations (run-level features).
   Returns a vector of diagnostic maps, each with an :extended key."
  [{:keys [gen-history] :as run-result}]
  (let [base-trace (diagnostics-trace run-result)
        extended (compute-extended-diagnostic run-result)]
    (mapv #(assoc % :extended extended) base-trace)))
