#!/usr/bin/env bb
;; Run L5-creative (boundary-guardian) through the full SCI detection pipeline.
;;
;; Tests the hypothesis that L5-creative's diversity gate produces
;; STRUCTURED coupling — spatially localized cross-bitplane interaction.
;;
;; Usage:
;;   bb -cp src:resources scripts/sci_l5_creative.clj

(require '[futon5.ca.core :as ca]
         '[futon5.wiring.runtime :as wrt]
         '[futon5.mmca.render :as render]
         '[futon5.mmca.metrics :as metrics]
         '[futon5.mmca.domain-analysis :as domain]
         '[futon5.mmca.particle-analysis :as particle]
         '[futon5.mmca.info-dynamics :as info]
         '[futon5.mmca.wolfram-class :as wclass]
         '[futon5.mmca.bitplane-analysis :as bitplane]
         '[futon5.tpg.diagnostics :as diag]
         '[clojure.string :as str]
         '[clojure.java.shell :as shell])

(def out-dir "out/sci-survey")

(defn ensure-dir! [dir] (.mkdirs (java.io.File. dir)))
(ensure-dir! out-dir)

(defn ppm->png! [ppm-path png-path scale]
  (let [result (shell/sh "convert" ppm-path
                         "-scale" (str scale "%")
                         "-interpolate" "Nearest" "-filter" "point"
                         png-path)]
    (when (zero? (:exit result))
      (println (str "    -> " png-path)))))

(defn fmt [x d] (if (number? x) (format (str "%." d "f") (double x)) (str x)))

;; =============================================================================
;; RUN L5-CREATIVE
;; =============================================================================

(println "\n======================================================================")
(println "  L5-CREATIVE BOUNDARY-GUARDIAN: SCI ANALYSIS")
(println "======================================================================\n")

;; Same seed and init as the original characterization
(def seed 352362012)
(def width 100)
(def generations 100)

(def rng (java.util.Random. (long seed)))
(def sigils (mapv :sigil (ca/sigil-entries)))
(def genotype (apply str (repeatedly width #(nth sigils (.nextInt rng (count sigils))))))
(def phenotype (apply str (repeatedly width #(if (< (.nextDouble rng) 0.5) \1 \0))))

(println (str "  Seed: " seed "  Width: " width "  Generations: " generations))
(println "  Running L5-creative wiring...")

(def t0 (System/nanoTime))
(def l5-result
  (wrt/run-wiring-from-file
   {:wiring-path "data/wiring-ladder/level-5-creative.edn"
    :genotype genotype
    :phenotype phenotype
    :generations generations}))
(printf "  Done in %.0fms\n\n" (/ (- (System/nanoTime) t0) 1e6))

(def history (:gen-history l5-result))

;; =============================================================================
;; RENDER IMAGES
;; =============================================================================

(println "  Rendering images...")

;; Sigil spacetime (genotype only)
(let [pixels (render/render-history history)
      ppm (str out-dir "/l5-creative.ppm")
      png (str out-dir "/l5-creative.png")]
  (render/write-ppm! ppm pixels :comment "l5-creative")
  (ppm->png! ppm png 400))

;; Sigil spacetime with phenotype
(let [pixels (render/render-run l5-result)
      ppm (str out-dir "/l5-creative-full.ppm")
      png (str out-dir "/l5-creative-full.png")]
  (render/write-ppm! ppm pixels :comment "l5-creative-full")
  (ppm->png! ppm png 400))

;; Bitplane grid
(defn bitplane->pixels [bp-history]
  (mapv (fn [row] (mapv (fn [bit] (if (= bit 1) [0 0 0] [255 255 255])) row)) bp-history))

(let [bitplanes (bitplane/decompose-history history)
      panels (mapv bitplane->pixels bitplanes)
      rows (count (first panels))
      cols (count (first (first panels)))
      sep 2
      grey [128 128 128]
      pixels (vec
              (for [gy (range 4)
                    y (concat (range rows) (when (< gy 3) (range sep)))]
                (vec
                 (for [gx (range 2)
                       x (concat (range cols) (when (< gx 1) (range sep)))]
                   (let [in-sep-x? (>= x cols) in-sep-y? (>= y rows)
                         idx (+ (* gy 2) gx)]
                     (cond (or in-sep-x? in-sep-y?) grey
                           (< idx 8) (nth (nth (nth panels idx) y) x)
                           :else grey))))))
      ppm (str out-dir "/l5-creative-bitplanes.ppm")
      png (str out-dir "/l5-creative-bitplanes.png")]
  (render/write-ppm! ppm pixels :comment "l5-creative bitplanes")
  (ppm->png! ppm png 300))

;; Coupling heatmap
(let [bitplanes (bitplane/decompose-history history)
      matrix (bitplane/coupling-matrix bitplanes)
      off-diag (for [i (range 8) j (range 8) :when (not= i j)] (nth (nth matrix i) j))
      max-val (max 0.001 (apply max off-diag))
      cell-size 12
      pixels (vec
              (for [py (range (* 8 cell-size))]
                (vec
                 (for [px (range (* 8 cell-size))]
                   (let [i (quot py cell-size) j (quot px cell-size)
                         val (nth (nth matrix i) j)
                         norm (if (= i j) 1.0 (min 1.0 (/ val max-val)))
                         r (int (min 255 (* 255 (min 1.0 (* 2.0 norm)))))
                         g (int (min 255 (* 255 (max 0.0 (- (* 2.0 norm) 1.0)))))
                         b (int (min 255 (* 255 (max 0.0 (- (* 3.0 norm) 2.0)))))]
                     [r g b])))))
      ppm (str out-dir "/l5-creative-coupling.ppm")
      png (str out-dir "/l5-creative-coupling.png")]
  (render/write-ppm! ppm pixels :comment "l5-creative coupling")
  (ppm->png! ppm png 400))

;; =============================================================================
;; SCI ANALYSIS
;; =============================================================================

(println "\n  Running SCI analysis pipeline...")

;; Basic metrics
(def metrics-h (metrics/series-metrics-history history))
(def interesting (metrics/interestingness metrics-h nil))
(def compress (metrics/compressibility-metrics history))
(def autocorr (metrics/autocorr-metrics history))
(def cv-result (metrics/compression-variance history))

;; Domain analysis
(def domain-result (domain/analyze-domain history))

;; Particle analysis
(def particle-result (particle/analyze-particles history))

;; Info dynamics
(def te-result (info/te-summary history))
(def ais-result (info/active-information-storage history))

;; Bitplane analysis
(println "  Bitplane analysis...")
(def bp-analyses (bitplane/analyze-all-bitplanes history))
(def bp-summary (bitplane/aggregate-bitplane-metrics bp-analyses))

;; Coupling spectrum (full, with spatial and temporal)
(println "  Coupling spectrum (with spatial profile)...")
(def coupling (bitplane/coupling-spectrum history {:temporal? true :spatial? true}))

;; Wolfram class
(def class-result (wclass/estimate-class l5-result))

;; Diagnostic trace
(def trace (diag/diagnostics-trace l5-result))
(def named-values (mapv :named (remove nil? trace)))
(def dim-means
  (into {}
        (map (fn [k]
               (let [vals (keep k named-values) n (count vals)]
                 [k (if (pos? n) (/ (reduce + 0.0 vals) n) 0.0)])))
        diag/diagnostic-keys))

;; =============================================================================
;; REPORT
;; =============================================================================

(println "\n======================================================================")
(println "  RESULTS")
(println "======================================================================")

(println "\n  Wolfram Class Estimation:")
(printf  "    Class: %s  Confidence: %s\n"
         (name (:class class-result)) (fmt (:confidence class-result) 3))
(println (str "    " (:class-reasoning class-result)))

(println "\n  6D Diagnostic Profile:")
(doseq [dim diag/diagnostic-keys]
  (printf "    %-22s %.3f\n" (name dim) (double (dim dim-means))))

(println "\n  Basic Dynamics:")
(printf  "    change-rate:    %s    entropy-n:    %s    lz78-ratio: %s\n"
         (fmt (:avg-change interesting) 3) (fmt (:avg-entropy-n interesting) 3) (fmt (:lz78-ratio compress) 3))
(printf  "    temporal-ac:    %s    spatial-ac:   %s    diag-ac:    %s\n"
         (fmt (:temporal-autocorr autocorr) 3) (fmt (:spatial-autocorr autocorr) 3) (fmt (:diag-autocorr autocorr) 3))
(printf  "    compression-cv: %s\n" (fmt (:cv cv-result) 3))

(println "\n  Sigil-Level Domain:")
(printf  "    domain-fraction: %s  (%dx%d)  class-iv? %s\n"
         (fmt (:domain-fraction domain-result) 3) (:px domain-result) (:pt domain-result)
         (:class-iv-candidate? domain-result))

(println "\n  Sigil-Level Particles:")
(printf  "    count: %d  species: %d  max-lifetime: %d  interactions: %d\n"
         (:particle-count particle-result) (:species-count particle-result)
         (:max-lifetime particle-result) (:interaction-count particle-result))

(println "\n  Bitplane Analysis:")
(doseq [a bp-analyses]
  (printf "    Plane %d: df=%s  px=%d  pt=%d  cv=%s  change=%s  iv?=%s\n"
          (:plane a) (fmt (:domain-fraction a) 3) (:domain-px a) (:domain-pt a)
          (fmt (:compression-cv a) 3) (fmt (:mean-change a) 3) (:class-iv-candidate? a)))
(printf "\n    Best: plane %d  df=%s  (%dx%d)\n"
        (:best-plane bp-summary) (fmt (:best-domain-fraction bp-summary) 3)
        (:best-domain-px bp-summary) (:best-domain-pt bp-summary))
(printf  "    Class IV planes: %d/8  Activity spread: %s\n"
         (:class-iv-plane-count bp-summary) (fmt (:activity-spread bp-summary) 3))

(println "\n  Coupling Spectrum:")
(printf  "    Independence:   %s\n" (fmt (:independence-score coupling) 3))
(printf  "    Mean coupling:  %s    Max: %s\n" (fmt (:mean-coupling coupling) 4) (fmt (:max-coupling coupling) 4))
(printf  "    Coupling CV:    %s\n" (fmt (:coupling-cv coupling) 3))
(printf  "    Structured:     %s    Hotspots: %s\n"
         (fmt (:structured-coupling coupling) 3)
         (fmt (get-in coupling [:spatial-profile :hotspot-fraction] 0.0) 3))
(printf  "    Temporal trend:  %s\n" (name (or (:temporal-trend coupling) :unknown)))
(printf  "    Summary:         %s\n" (name (or (:summary coupling) :unknown)))

;; Coupling matrix
(println "\n  Coupling Matrix (MI between bitplane pairs):")
(let [matrix (:coupling-matrix coupling)]
  (printf "         ")
  (doseq [j (range 8)] (printf "  bp%-2d" j))
  (println)
  (doseq [i (range 8)]
    (printf "    bp%d " i)
    (doseq [j (range 8)]
      (let [v (nth (nth matrix i) j)]
        (if (= i j)
          (printf "   -- ")
          (printf " %5s" (fmt v 3)))))
    (println)))

;; Spatial coupling profile
(when-let [sp (:spatial-profile coupling)]
  (println "\n  Spatial Coupling Profile (per-cell mean MI):")
  (printf  "    Mean: %s  Max: %s  Hotspot fraction: %s\n"
           (fmt (:mean sp) 4) (fmt (:max sp) 4) (fmt (:hotspot-fraction sp) 3))
  (let [per-cell (:per-cell sp)
        ;; Show as a sparkline-like bar
        n (count per-cell)
        max-v (apply max per-cell)
        bar-height 8
        bars (mapv (fn [v] (int (* bar-height (/ v (max 0.001 max-v))))) per-cell)]
    (println "    Spatial coupling across lattice (higher = more coupling):")
    (doseq [h (range bar-height 0 -1)]
      (printf "    %s\n"
              (apply str (map #(if (>= % h) "█" " ") bars))))))

(println "\n  Information Dynamics:")
(printf  "    mean-TE:        %s    max-TE:       %s    TE-var:     %s\n"
         (fmt (:mean-te te-result) 4) (fmt (:max-te te-result) 4) (fmt (:te-variance te-result) 4))
(printf  "    high-TE-frac:   %s    info-transport: %s\n"
         (fmt (:high-te-fraction te-result) 3) (fmt (:information-transport-score te-result) 3))
(printf  "    AIS-mean:       %s    AIS-max:      %s\n"
         (fmt (:mean ais-result) 4) (fmt (:max ais-result) 4))

;; Compare with known rules
(println "\n======================================================================")
(println "  COMPARISON WITH KNOWN RULES")
(println "======================================================================")
(println)
(printf  "%-15s %6s %6s %6s %6s %6s %6s %6s  %s\n"
         "System" "BpDF" "SigDF" "MI" "MaxMI" "Hotsp" "Part" "Spe" "Coupling")
(println (apply str (repeat 90 "-")))
(let [rows [["Rule 30"   "0.527" "0.03" "0.0001" "0.0002" "0.000"   "7"   "7" "independent"]
            ["Rule 90"   "0.537" "0.03" "0.0001" "0.0006" "0.017"   "6"   "5" "independent"]
            ["Rule 110"  "0.773" "0.05" "0.0013" "0.0127" "0.008"  "28"  "18" "independent"]
            ["Rule 184"  "0.992" "0.16" "0.1597" "0.5785" "0.000" "174"  "27" "uniformly-coupled"]
            ["Champion"  "0.728" "0.05" "0.0165" "0.0499" "0.017"  "32"  "30" "weakly-coupled"]]]
  (doseq [[name bdf sdf mi maxmi hot part spe coupling] rows]
    (printf "%-15s %6s %6s %6s %6s %6s %6s %6s  %s\n"
            name bdf sdf mi maxmi hot part spe coupling)))
(printf "%-15s %6s %6s %6s %6s %6s %6d %6d  %s\n"
        "L5-creative"
        (fmt (:best-domain-fraction bp-summary) 3)
        (fmt (:domain-fraction domain-result) 2)
        (fmt (:mean-coupling coupling) 4)
        (fmt (:max-coupling coupling) 4)
        (fmt (get-in coupling [:spatial-profile :hotspot-fraction] 0.0) 3)
        (:particle-count particle-result)
        (:species-count particle-result)
        (name (or (:summary coupling) :unknown)))
(println (apply str (repeat 90 "-")))

(println "\n  Images written to" out-dir)
(println "  Done.\n")
