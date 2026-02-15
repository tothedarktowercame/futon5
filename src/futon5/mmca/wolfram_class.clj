(ns futon5.mmca.wolfram-class
  "Wolfram class estimation from combined metric signals.

   Classifies MMCA run dynamics into Wolfram's four classes:
     I   — Fixed point / homogeneous (frozen)
     II  — Periodic / oscillating (regular structure)
     III — Chaotic (spatially random, no persistent structure)
     IV  — Complex (periodic background + localized structures + interactions)

   Uses a decision-tree approach over existing and new SCI metrics.
   Class IV is the target for the Search for Computational Intelligence."
  (:require [futon5.mmca.metrics :as metrics]
            [futon5.mmca.domain-analysis :as domain]
            [futon5.mmca.particle-analysis :as particle]
            [futon5.mmca.band-analysis :as band]))

(defn collect-features
  "Collect all features needed for class estimation from a run result.
   Returns a flat map of normalized [0,1] features.
   Can also accept a pre-computed feature map (passes through)."
  [run-or-features]
  (if (contains? run-or-features :change-rate)
    ;; Already a feature map
    run-or-features
    ;; Compute from run result
    (let [history (or (:gen-history run-or-features)
                      (:phe-history run-or-features))
          metrics-history (metrics/series-metrics-history history)
          interesting (metrics/interestingness metrics-history nil)
          compress (metrics/compressibility-metrics history)
          autocorr (metrics/autocorr-metrics history)
          cv-result (metrics/compression-variance history)
          band-result (band/analyze-history history {:row->values vec})
          domain-result (domain/analyze-domain history)
          particle-result (particle/analyze-particles history)]
      {:change-rate (or (:avg-change interesting) 0.0)
       :entropy-n (or (:avg-entropy-n interesting) 0.0)
       :temporal-autocorr (or (:temporal-autocorr autocorr) 0.0)
       :spatial-autocorr (or (:spatial-autocorr autocorr) 0.0)
       :diag-autocorr (or (:diag-autocorr autocorr) 0.0)
       :frozen-ratio (or (:frozen-ratio band-result) 0.0)
       :moderate-ratio (or (:moderate-ratio band-result) 0.0)
       :lz78-ratio (or (:lz78-ratio compress) 0.0)
       :compression-cv (or (:cv cv-result) 0.0)
       :domain-fraction (or (:domain-fraction domain-result) 0.0)
       :domain-px (or (:px domain-result) 0)
       :domain-pt (or (:pt domain-result) 0)
       :particle-count (or (:particle-count particle-result) 0)
       :species-count (or (:species-count particle-result) 0)
       :max-lifetime (or (:max-lifetime particle-result) 0)
       :row-periodic? (boolean (:row-periodic? band-result))})))

(defn- class-i-score
  "Score for Class I (fixed point / frozen).
   High when: very low change, high frozen ratio."
  [{:keys [change-rate frozen-ratio]}]
  (if (and (< change-rate 0.05) (> frozen-ratio 0.9))
    {:score (min 1.0 frozen-ratio)
     :reasoning "Near-zero change rate with >90% frozen columns — fixed point dynamics"}
    {:score 0.0 :reasoning nil}))

(defn- class-ii-score
  "Score for Class II (periodic / oscillating).
   High when: periodic background fills space, no particles, moderate change."
  [{:keys [domain-fraction particle-count change-rate row-periodic? frozen-ratio]}]
  (let [periodic? (or row-periodic? (> domain-fraction 0.9))]
    (if (and periodic? (zero? particle-count) (< change-rate 0.4))
      {:score (max domain-fraction (if row-periodic? 0.9 0.0))
       :reasoning (str "Domain fraction " (format "%.2f" domain-fraction)
                       (when row-periodic? " + row periodicity detected")
                       " — periodic dynamics")}
      ;; Partial match for high frozen ratio
      (if (and (> frozen-ratio 0.7) (< change-rate 0.3))
        {:score (* 0.7 frozen-ratio)
         :reasoning (str "High frozen ratio " (format "%.2f" frozen-ratio)
                         " — trending toward periodic/barcode")}
        {:score 0.0 :reasoning nil}))))

(defn- class-iii-score
  "Score for Class III (chaotic).
   High when: no periodic background, uniform compression, high change."
  [{:keys [domain-fraction compression-cv change-rate entropy-n]}]
  (if (and (< domain-fraction 0.3) (< compression-cv 0.15) (> change-rate 0.4))
    {:score (* (- 1.0 domain-fraction) (- 1.0 compression-cv))
     :reasoning (str "Low domain fraction " (format "%.2f" domain-fraction)
                     ", low compression variance — spatially random chaos")}
    ;; Partial match for high entropy + high change
    (if (and (> entropy-n 0.8) (> change-rate 0.5))
      {:score (* 0.5 entropy-n change-rate)
       :reasoning (str "High entropy " (format "%.2f" entropy-n)
                       " + high change " (format "%.2f" change-rate))}
      {:score 0.0 :reasoning nil})))

(defn- class-iv-score
  "Score for Class IV (complex / computational).
   High when: partial periodic background, particles detected, bursty complexity."
  [{:keys [domain-fraction compression-cv particle-count species-count max-lifetime]}]
  (let [domain-ok? (and (> domain-fraction 0.3) (< domain-fraction 0.98))
        particles? (pos? particle-count)
        bursty? (> compression-cv 0.1)]
    (if (and domain-ok? particles?)
      (let [;; Domain proximity to ideal (0.75)
            domain-fit (- 1.0 (* 3.0 (Math/abs (- domain-fraction 0.75))))
            domain-fit (max 0.0 (min 1.0 domain-fit))
            ;; Particle richness
            particle-norm (Math/tanh (/ (double particle-count) 5.0))
            species-norm (Math/tanh (/ (double species-count) 3.0))
            lifetime-norm (Math/tanh (/ (double max-lifetime) 10.0))
            ;; Composite
            base (* 0.3 domain-fit
                    (+ (* 0.3 particle-norm)
                       (* 0.3 species-norm)
                       (* 0.2 lifetime-norm)
                       (* 0.2 (min 1.0 compression-cv))))]
        {:score (min 1.0 (+ 0.4 (* 2.0 base)))
         :reasoning (str "Domain fraction " (format "%.2f" domain-fraction)
                         ", " particle-count " particles"
                         " (" species-count " species)"
                         ", compression CV " (format "%.2f" compression-cv)
                         " — potential computational dynamics")})
      ;; Weak signal: particles without clear domain
      (if particles?
        {:score (* 0.3 (Math/tanh (/ (double particle-count) 5.0)))
         :reasoning (str particle-count " particles detected but weak domain signal")}
        {:score 0.0 :reasoning nil}))))

(defn estimate-class
  "Estimate Wolfram class from a run's metrics.

   Accepts either:
   - A pre-computed feature map (from collect-features)
   - A run result map (computes features on-the-fly)

   Returns:
   {:class :I | :II | :III | :IV
    :confidence double  -- [0, 1]
    :signals {:domain-fraction :compression-cv :change-rate ...}
    :reasoning string   -- human-readable explanation
    :scores {:I double :II double :III double :IV double}}"
  [run-or-features]
  (let [features (collect-features run-or-features)
        scores {:I (class-i-score features)
                :II (class-ii-score features)
                :III (class-iii-score features)
                :IV (class-iv-score features)}
        ;; Pick the class with the highest score
        [best-class best-result] (apply max-key (comp :score val) scores)]
    {:class best-class
     :confidence (:score best-result)
     :reasoning (or (:reasoning best-result) "No strong class signal detected")
     :signals (select-keys features [:domain-fraction :compression-cv :change-rate
                                     :particle-count :species-count :entropy-n
                                     :frozen-ratio :temporal-autocorr])
     :scores (into {} (map (fn [[k v]] [k (:score v)]) scores))}))
