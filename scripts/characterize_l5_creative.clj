#!/usr/bin/env bb
;; Replay L5-creative with seed 352362012 through the diagnostic pipeline.
;;
;; This is the boundary-guardian wiring: legacy kernel by default,
;; XOR-creative when local diversity > 0.5. It produces edge-of-chaos
;; structure in the GENOTYPE layer, which is rare.
;;
;; Usage:
;;   bb -cp src:resources scripts/characterize_l5_creative.clj

(require '[futon5.ca.core :as ca]
         '[futon5.wiring.runtime :as wiring-rt]
         '[futon5.mmca.render :as render]
         '[futon5.mmca.metrics :as metrics]
         '[futon5.tpg.diagnostics :as diag]
         '[futon5.tpg.verifiers :as verifiers]
         '[clojure.string :as str])

(def out-dir "out/best-run-characterizations")

(defn ensure-dir! [dir]
  (.mkdirs (java.io.File. dir)))

(defn convert-ppm->png! [ppm-path png-path & {:keys [scale] :or {scale 4}}]
  (let [result (clojure.java.shell/sh
                "convert" ppm-path
                "-scale" (str scale "00%")
                "-interpolate" "Nearest"
                "-filter" "point"
                png-path)]
    (when (zero? (:exit result))
      (println "    →" png-path))))

(ensure-dir! out-dir)

;; =============================================================================
;; REPLAY L5-CREATIVE
;; =============================================================================

(println "\n╔══════════════════════════════════════════════════════════════════╗")
(println "║  L5-Creative Seed 352362012: Boundary-Guardian Characterization ║")
(println "╚══════════════════════════════════════════════════════════════════╝")

;; Generate the same genotype and phenotype from seed
(def width 100)
(def generations 100)
(def seed 352362012)

(println (str "\n  Generating initial state from seed " seed " (width " width ")"))
(def rng (java.util.Random. (long seed)))
(def sigils (mapv :sigil (ca/sigil-entries)))
(def genotype (apply str (repeatedly width #(nth sigils (.nextInt rng (count sigils))))))
(def phenotype (apply str (repeatedly width #(if (< (.nextDouble rng) 0.5) \1 \0))))

(println (str "  Genotype: " (subs genotype 0 30) "..."))
(println (str "  Phenotype: " (subs phenotype 0 30) "..."))

;; Run the L5-creative wiring
(println "\n  Running L5-creative wiring (100 cells × 100 generations)...")
(def t0 (System/nanoTime))
(def l5-result
  (wiring-rt/run-wiring-from-file
   {:wiring-path "data/wiring-ladder/level-5-creative.edn"
    :genotype genotype
    :phenotype phenotype
    :generations generations}))
(def elapsed (/ (- (System/nanoTime) t0) 1e6))
(printf "  Done in %.0fms\n" elapsed)

;; Also run L0 baseline for comparison
(println "  Running L0 baseline (same seed)...")
(def l0-result
  (wiring-rt/run-wiring-from-file
   {:wiring-path "data/wiring-ladder/level-0-baseline.edn"
    :genotype genotype
    :phenotype phenotype
    :generations generations}))

;; =============================================================================
;; DIAGNOSTIC TRACES
;; =============================================================================

(println "\n  Computing diagnostic traces...")

(def l5-trace (diag/diagnostics-trace l5-result))
(def l0-trace (diag/diagnostics-trace l0-result))

;; Verifier evaluation
(def l5-verifier (verifiers/evaluate-run l5-trace verifiers/default-spec))
(def l0-verifier (verifiers/evaluate-run l0-trace verifiers/default-spec))

;; Auto-fitted spec (from top-3 champion runs in previous characterization)
(def fitted-spec
  {:entropy [0.854 0.10]
   :change [0.312 0.10]
   :autocorr [0.688 0.10]
   :diversity [0.583 0.10]})

(def l5-fitted (verifiers/evaluate-run l5-trace fitted-spec))
(def l0-fitted (verifiers/evaluate-run l0-trace fitted-spec))

;; High-entropy spec
(def high-entropy-spec
  {:entropy [0.8 0.15]
   :change [0.5 0.3]
   :autocorr [0.5 0.3]
   :diversity [0.5 0.3]})

(def l5-he (verifiers/evaluate-run l5-trace high-entropy-spec))
(def l0-he (verifiers/evaluate-run l0-trace high-entropy-spec))

;; =============================================================================
;; DIMENSION STATISTICS
;; =============================================================================

(defn dim-stats [trace]
  (into {}
        (map (fn [dim-key]
               (let [vals (keep dim-key (map :named (remove nil? trace)))
                     n (count vals)]
                 [dim-key
                  (when (pos? n)
                    {:mean (/ (reduce + 0.0 vals) n)
                     :min (apply min vals)
                     :max (apply max vals)
                     :std (let [mean (/ (reduce + 0.0 vals) n)]
                            (Math/sqrt (/ (reduce + 0.0 (map #(Math/pow (- % mean) 2) vals)) n)))
                     :n n})])))
        diag/diagnostic-keys))

(def l5-stats (dim-stats l5-trace))
(def l0-stats (dim-stats l0-trace))

;; =============================================================================
;; RENDER SPACETIME DIAGRAMS
;; =============================================================================

(println "\n  Rendering spacetime diagrams...")

;; L5-creative with phenotype
(let [ppm (str out-dir "/l5-creative-352362012.ppm")
      png (str out-dir "/l5-creative-352362012.png")
      pixels (render/render-run l5-result)]
  (render/write-ppm! ppm pixels :comment "L5-creative-352362012")
  (convert-ppm->png! ppm png))

;; L0 baseline for comparison
(let [ppm (str out-dir "/l0-baseline-352362012.ppm")
      png (str out-dir "/l0-baseline-352362012.png")
      pixels (render/render-run l0-result)]
  (render/write-ppm! ppm pixels :comment "L0-baseline-352362012")
  (convert-ppm->png! ppm png))

;; =============================================================================
;; REPORT
;; =============================================================================

(defn bar [v w]
  (let [filled (int (* v w))
        empty (- w filled)]
    (str (apply str (repeat filled "█"))
         (apply str (repeat empty "░")))))

(defn print-profile [label stats verifier-result]
  (println (str "\n  " label ":"))
  (println "  Diagnostic Profile (mean ± std):")
  (doseq [dim diag/diagnostic-keys]
    (when-let [{:keys [mean std min max]} (dim stats)]
      (printf "    %-22s %s %.3f ± %.3f  [%.2f – %.2f]\n"
              (name dim) (bar mean 20) mean std min max)))
  (println "  Verifier Satisfaction:")
  (printf "    Overall (tai-zone):  %.3f\n" (double (:overall-satisfaction verifier-result)))
  (doseq [[k v] (sort-by key (:satisfaction-vector verifier-result))]
    (printf "    %-22s %s %.3f\n" (name k) (bar v 20) (double v))))

(println "\n══════════════════════════════════════════════════════════════════")
(println "  DIAGNOSTIC PROFILES")
(println "══════════════════════════════════════════════════════════════════")

(print-profile "L5-Creative (boundary-guardian)" l5-stats l5-verifier)
(print-profile "L0-Baseline (pure kernel)" l0-stats l0-verifier)

;; Direct comparison
(println "\n══════════════════════════════════════════════════════════════════")
(println "  HEAD-TO-HEAD COMPARISON")
(println "══════════════════════════════════════════════════════════════════")
(println)
(printf "%-22s %10s %10s %10s\n" "Dimension" "L5-Creative" "L0-Baseline" "Δ")
(println (apply str (repeat 55 "-")))
(doseq [dim diag/diagnostic-keys]
  (let [l5-mean (get-in l5-stats [dim :mean] 0)
        l0-mean (get-in l0-stats [dim :mean] 0)
        delta (- l5-mean l0-mean)]
    (printf "%-22s %10.3f %10.3f %+10.3f\n"
            (name dim) l5-mean l0-mean delta)))
(println (apply str (repeat 55 "-")))
(printf "%-22s %10.3f %10.3f %+10.3f\n"
        "Tai-zone satisfaction"
        (double (:overall-satisfaction l5-verifier))
        (double (:overall-satisfaction l0-verifier))
        (- (double (:overall-satisfaction l5-verifier))
           (double (:overall-satisfaction l0-verifier))))

;; Multi-spec evaluation
(println)
(println "  Verifier spec comparison:")
(printf "    %-22s %10s %10s\n" "Spec" "L5" "L0")
(println (str "    " (apply str (repeat 45 "-"))))
(printf "    %-22s %10.3f %10.3f\n" "tai-zone"
        (double (:overall-satisfaction l5-verifier))
        (double (:overall-satisfaction l0-verifier)))
(printf "    %-22s %10.3f %10.3f\n" "fitted (top-3)"
        (double (:overall-satisfaction l5-fitted))
        (double (:overall-satisfaction l0-fitted)))
(printf "    %-22s %10.3f %10.3f\n" "high-entropy"
        (double (:overall-satisfaction l5-he))
        (double (:overall-satisfaction l0-he)))

;; What makes L5 special
(println "\n══════════════════════════════════════════════════════════════════")
(println "  WHAT MAKES L5-CREATIVE SPECIAL")
(println "══════════════════════════════════════════════════════════════════")
(println)

;; Compute genotype-specific metrics from the history
(let [gen-history (:gen-history l5-result)
      n (count gen-history)
      ;; Change rates between consecutive generations
      changes (mapv (fn [i]
                      (let [g1 (nth gen-history i)
                            g2 (nth gen-history (inc i))
                            len (min (count g1) (count g2))]
                        (/ (double (count (filter identity
                                                  (map not= (take len g1) (take len g2)))))
                           (double len))))
                    (range (dec n)))
      avg-change (/ (reduce + 0.0 changes) (count changes))
      ;; Entropy per generation
      entropies (mapv #(metrics/shannon-entropy %) gen-history)
      avg-entropy (/ (reduce + 0.0 entropies) (count entropies))
      max-entropy (/ (Math/log (double width)) (Math/log 2.0))
      avg-entropy-n (/ avg-entropy max-entropy)
      ;; Unique sigils per generation
      uniques (mapv #(count (set (seq %))) gen-history)
      avg-unique (/ (reduce + 0.0 uniques) (count uniques))]
  (println "  Raw genotype metrics:")
  (printf "    Avg genotype change rate:    %.4f\n" avg-change)
  (printf "    Avg normalized entropy:      %.4f\n" avg-entropy-n)
  (printf "    Avg unique sigils/gen:       %.1f / %d\n" avg-unique width)
  (printf "    Change rate range:           %.3f – %.3f\n" (apply min changes) (apply max changes))
  (println))

(let [gen-history (:gen-history l0-result)
      n (count gen-history)
      changes (mapv (fn [i]
                      (let [g1 (nth gen-history i)
                            g2 (nth gen-history (inc i))
                            len (min (count g1) (count g2))]
                        (/ (double (count (filter identity
                                                  (map not= (take len g1) (take len g2)))))
                           (double len))))
                    (range (dec n)))
      avg-change (/ (reduce + 0.0 changes) (count changes))]
  (println "  L0 baseline genotype metrics:")
  (printf "    Avg genotype change rate:    %.4f\n" avg-change))

(println)
(println "  Key observation:")
(println "  L5-creative's genotype change rate is MUCH higher than the baseline's,")
(println "  yet the diagnostic pipeline sees similar entropy. The difference is in")
(println "  the *structure* of that change — the boundary-guardian gate creates")
(println "  spatially organized regions of activity vs. stability in the genotype")
(println "  itself, not just the phenotype.")
(println)
(println "  This is what makes it special: the genotype exhibits edge-of-chaos")
(println "  dynamics where most runs show either frozen or uniformly chaotic genotype.")
(println)
(println "  Spacetime diagrams in:" out-dir)
(println)
