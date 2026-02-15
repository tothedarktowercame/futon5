#!/usr/bin/env bb
;; Run hybrid wiring rules through the SCI detection pipeline.
;;
;; Tests whether sigil-level arithmetic components create STRUCTURED coupling
;; — spatially localized cross-bitplane interaction.
;;
;; Usage:
;;   bb -cp src:resources scripts/sci_hybrids.clj

(require '[futon5.ca.core :as ca]
         '[futon5.wiring.runtime :as wrt]
         '[futon5.mmca.render :as render]
         '[futon5.mmca.metrics :as metrics]
         '[futon5.mmca.domain-analysis :as domain]
         '[futon5.mmca.particle-analysis :as particle]
         '[futon5.mmca.info-dynamics :as info]
         '[futon5.mmca.wolfram-class :as wclass]
         '[futon5.mmca.bitplane-analysis :as bitplane]
         '[clojure.edn :as edn]
         '[clojure.string :as str]
         '[clojure.java.shell :as shell])

(def out-dir "out/sci-hybrids")

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
;; CONFIGURATION
;; =============================================================================

(def seed 352362012)
(def width 100)
(def generations 100)

(def hybrids
  [{:id "hybrid-110-boundary" :path "data/wiring-rules/hybrid-110-boundary.edn"
    :label "R110+Boundary" :predict "structured"}
   {:id "hybrid-arith-mean" :path "data/wiring-rules/hybrid-arith-mean.edn"
    :label "ArithMean" :predict "uniform"}
   {:id "hybrid-110-carry" :path "data/wiring-rules/hybrid-110-carry.edn"
    :label "R110+Carry" :predict "structured"}
   {:id "hybrid-110-rotate" :path "data/wiring-rules/hybrid-110-rotate.edn"
    :label "R110+Rotate" :predict "structured"}
   ;; Refined variants (round 2)
   {:id "hybrid-110-boundary-v2" :path "data/wiring-rules/hybrid-110-boundary-v2.edn"
    :label "R110+BndV2" :predict "structured"}
   {:id "hybrid-110-avgself" :path "data/wiring-rules/hybrid-110-avgself.edn"
    :label "R110+AvgSelf" :predict "carry-chain"}
   {:id "hybrid-110-addself" :path "data/wiring-rules/hybrid-110-addself.edn"
    :label "R110+AddSelf" :predict "carry-chain"}])

;; Reference rules for comparison (run fresh)
(def references
  [{:id "rule-110" :path "data/wiring-rules/rule-110.edn"
    :label "Rule 110" :predict "independent"}
   {:id "rule-030" :path "data/wiring-rules/rule-030.edn"
    :label "Rule 30" :predict "independent"}
   {:id "rule-184" :path "data/wiring-rules/rule-184.edn"
    :label "Rule 184" :predict "uniform"}])

;; =============================================================================
;; RUN AND ANALYZE
;; =============================================================================

(println "\n======================================================================")
(println "  HYBRID WIRING RULES: COUPLING SPECTRUM EXPERIMENT")
(println "======================================================================\n")
(printf  "  Width: %d  Generations: %d  Seed: %d\n\n" width generations seed)

(defn run-and-analyze [{:keys [id path label]}]
  (println (str "  " label " (" id ")..."))
  (let [t0 (System/nanoTime)
        genotype (wrt/random-genotype width seed)
        result (wrt/run-wiring-from-file
                {:wiring-path path
                 :genotype genotype
                 :generations generations})
        history (:gen-history result)
        ms (/ (- (System/nanoTime) t0) 1e6)]
    (printf "    Run: %.0fms  " ms)

    ;; Render spacetime
    (let [pixels (render/render-history history)
          ppm (str out-dir "/" id ".ppm")
          png (str out-dir "/" id ".png")]
      (render/write-ppm! ppm pixels :comment id)
      (ppm->png! ppm png 400))

    ;; Bitplane grid
    (let [bitplanes (bitplane/decompose-history history)
          panels (mapv (fn [bp-history]
                         (mapv (fn [row]
                                 (mapv (fn [bit] (if (= bit 1) [0 0 0] [255 255 255])) row))
                               bp-history))
                       bitplanes)
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
          ppm (str out-dir "/" id "-bitplanes.ppm")
          png (str out-dir "/" id "-bitplanes.png")]
      (render/write-ppm! ppm pixels :comment (str id " bitplanes"))
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
          ppm (str out-dir "/" id "-coupling.ppm")
          png (str out-dir "/" id "-coupling.png")]
      (render/write-ppm! ppm pixels :comment (str id " coupling"))
      (ppm->png! ppm png 400))

    ;; SCI analysis
    (let [t1 (System/nanoTime)
          ;; Basic metrics
          metrics-h (metrics/series-metrics-history history)
          interesting (metrics/interestingness metrics-h nil)
          compress (metrics/compressibility-metrics history)
          autocorr (metrics/autocorr-metrics history)
          cv-result (metrics/compression-variance history)
          ;; Domain analysis
          domain-result (domain/analyze-domain history)
          ;; Particle analysis
          particle-result (particle/analyze-particles history)
          ;; Info dynamics
          te-result (info/te-summary history)
          ;; Bitplane analysis
          bp-analyses (bitplane/analyze-all-bitplanes history)
          bp-summary (bitplane/aggregate-bitplane-metrics bp-analyses)
          ;; Coupling spectrum (with spatial profile)
          coupling (bitplane/coupling-spectrum history {:temporal? true :spatial? true})
          ;; Wolfram class
          class-result (wclass/estimate-class result)
          analysis-ms (/ (- (System/nanoTime) t1) 1e6)]
      (printf "    Analyze: %.0fms\n" analysis-ms)
      {:id id :label label :history history
       :class class-result
       :domain domain-result
       :particles particle-result
       :te te-result
       :bp-summary bp-summary
       :bp-analyses bp-analyses
       :coupling coupling
       :interesting interesting
       :compress compress
       :autocorr autocorr
       :cv-result cv-result})))

;; Run all hybrids and references
(def results
  (vec (concat
        (mapv run-and-analyze references)
        (mapv run-and-analyze hybrids))))

;; =============================================================================
;; REPORT
;; =============================================================================

(println "\n======================================================================")
(println "  COUPLING SPECTRUM RESULTS")
(println "======================================================================\n")

;; Summary table
(printf "%-20s %5s %5s %6s %6s %5s %5s %4s  %-20s  %s\n"
        "System" "BpDF" "SigDF" "MI" "MaxMI" "Hotsp" "Part" "Spe" "Coupling" "Class")
(println (apply str (repeat 110 "-")))

(doseq [r results]
  (let [c (:coupling r)
        bp (:bp-summary r)
        d (:domain r)
        p (:particles r)
        cl (:class r)]
    (printf "%-20s %5s %5s %6s %6s %5s %5d %4d  %-20s  %s (%.2f)\n"
            (:label r)
            (fmt (:best-domain-fraction bp) 3)
            (fmt (:domain-fraction d) 2)
            (fmt (:mean-coupling c) 4)
            (fmt (:max-coupling c) 4)
            (fmt (get-in c [:spatial-profile :hotspot-fraction] 0.0) 3)
            (:particle-count p)
            (:species-count p)
            (name (or (:summary c) :unknown))
            (name (:class cl))
            (double (:confidence cl)))))
(println (apply str (repeat 110 "-")))

;; Detailed per-hybrid analysis
(doseq [r (drop 3 results)]  ;; Skip reference rules
  (let [c (:coupling r)
        bp (:bp-summary r)
        sp (:spatial-profile c)]
    (println (str "\n  " (:label r) " (" (:id r) "):"))
    (println (str "    Class: " (name (get-in r [:class :class])) "  Confidence: " (fmt (get-in r [:class :confidence]) 3)))
    (println (str "    " (get-in r [:class :reasoning])))

    (println "    Bitplane analysis:")
    (doseq [a (:bp-analyses r)]
      (printf "      Plane %d: df=%s  change=%s  iv?=%s\n"
              (:plane a) (fmt (:domain-fraction a) 3) (fmt (:mean-change a) 3) (:class-iv-candidate? a)))

    (println (str "    Coupling: " (name (or (:summary c) :unknown))))
    (printf  "      Mean MI: %s  Max MI: %s  Independence: %s\n"
             (fmt (:mean-coupling c) 4) (fmt (:max-coupling c) 4) (fmt (:independence-score c) 3))

    (when sp
      (printf  "      Spatial: mean=%s  max=%s  hotspots=%s\n"
               (fmt (:mean sp) 4) (fmt (:max sp) 4) (fmt (:hotspot-fraction sp) 3))
      ;; Sparkline of spatial coupling
      (let [per-cell (:per-cell sp)
            n (count per-cell)
            max-v (apply max per-cell)
            bar-height 6
            bars (mapv (fn [v] (int (* bar-height (/ v (max 0.001 max-v))))) per-cell)]
        (println "      Spatial coupling profile:")
        (doseq [h (range bar-height 0 -1)]
          (printf "      %s\n"
                  (apply str (map #(if (>= % h) "█" " ") bars))))))

    ;; Coupling matrix
    (println "    Coupling matrix:")
    (let [matrix (:coupling-matrix c)]
      (printf "           ")
      (doseq [j (range 8)] (printf " bp%-2d" j))
      (println)
      (doseq [i (range 8)]
        (printf "      bp%d " i)
        (doseq [j (range 8)]
          (let [v (nth (nth matrix i) j)]
            (if (= i j)
              (printf "  -- ")
              (printf " %4s" (fmt v 3)))))
        (println)))

    (printf "    Info: TE-mean=%s  TE-var=%s  AIS-mean=%s\n"
            (fmt (get-in r [:te :mean-te]) 4)
            (fmt (get-in r [:te :te-variance]) 4)
            "n/a")))

;; =============================================================================
;; HYPOTHESIS CHECK
;; =============================================================================

(println "\n======================================================================")
(println "  HYPOTHESIS CHECK")
(println "======================================================================\n")

(let [hybrid-results (drop 3 results)
      structured-found? (some (fn [r]
                                (let [hot (get-in r [:coupling :spatial-profile :hotspot-fraction] 0.0)
                                      summary (get-in r [:coupling :summary])]
                                  (or (> hot 0.02)
                                      (= summary :structured-coupling)
                                      (= summary :weakly-coupled))))
                              hybrid-results)
      arith-mean (first (filter #(= "hybrid-arith-mean" (:id %)) results))
      arith-coupling (get-in arith-mean [:coupling :mean-coupling] 0.0)]
  (println "  1. Did any hybrid show structured coupling?")
  (if structured-found?
    (println "     YES — at least one hybrid shows spatially localized coupling!")
    (println "     NO — no hybrid achieved structured coupling yet."))

  (println (str "\n  2. Does arith-mean show uniform coupling (control check)?"))
  (printf  "     Mean MI: %s — %s\n"
           (fmt arith-coupling 4)
           (if (> arith-coupling 0.01)
             "YES, substantial coupling detected (control passes)"
             "Low coupling — control needs investigation"))

  (println "\n  3. Coupling spectrum coverage:")
  (doseq [r results]
    (let [summary (get-in r [:coupling :summary])
          mi (get-in r [:coupling :mean-coupling] 0.0)
          hot (get-in r [:coupling :spatial-profile :hotspot-fraction] 0.0)]
      (printf "     %-20s  MI=%s  hot=%s  → %s\n"
              (:label r) (fmt mi 4) (fmt hot 3) (name (or summary :unknown))))))

(println "\n  Images written to" out-dir)
(println "  Done.\n")
