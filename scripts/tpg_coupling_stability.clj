#!/usr/bin/env bb
;; Cross-Run Coupling Stability Analysis
;;
;; Tests whether an evolved TPG produces consistent coupling structure
;; across different random seeds. Measures:
;; - MI variance (coupling level consistency)
;; - Matrix correlation (same bitplane pairs couple?)
;; - Hotspot consistency (same spatial positions show coupling?)
;; - Summary classification stability
;;
;; Usage:
;;   bb -cp src:resources scripts/tpg_coupling_stability.clj <tpg-edn> [n-seeds] [generations]
;;
;; Example:
;;   bb -cp src:resources scripts/tpg_coupling_stability.clj out/tpg-evo-production/best-tpg.edn 20 50

(require '[futon5.ca.core :as ca]
         '[futon5.tpg.core :as tpg]
         '[futon5.tpg.runner :as runner]
         '[futon5.tpg.verifiers :as verifiers]
         '[futon5.wiring.runtime :as wrt]
         '[futon5.mmca.bitplane-analysis :as bitplane]
         '[clojure.string :as str])

(defn fmt [x d] (if (number? x) (format (str "%.\" d \"f") (double x)) (str x)))

;; =============================================================================
;; CONFIGURATION
;; =============================================================================

(def tpg-path (or (first *command-line-args*)
                  (do (println "Usage: tpg_coupling_stability.clj <tpg-edn> [n-seeds] [generations]")
                      (System/exit 1))))
(def n-seeds (or (some-> (second *command-line-args*) parse-long) 20))
(def generations (or (some-> (nth *command-line-args* 2 nil) parse-long) 50))
(def width 64)
(def base-seed 42)

;; Load TPG
(println "\n======================================================================")
(println "  CROSS-RUN COUPLING STABILITY ANALYSIS")
(println "======================================================================\n")

(println "Loading TPG from" tpg-path "...")
(def tpg-graph (read-string (slurp tpg-path)))
(println (str "  TPG ID: " (:tpg/id tpg-graph)))
(println (str "  Teams: " (count (:teams tpg-graph))
              "  Programs: " (reduce + (map #(count (:programs %)) (:teams tpg-graph)))))
(when (:temporal-schedule tpg-graph)
  (println (str "  Schedule: " (count (:temporal-schedule tpg-graph)) " entries")))

;; Load wiring operators
(println "\nLoading wiring operators...")
(def wiring-operators
  (runner/load-wiring-operators
   {:wiring-addself "data/wiring-rules/hybrid-110-addself.edn"
    :wiring-msb     "data/wiring-rules/hybrid-110-msb.edn"
    :wiring-bit5    "data/wiring-rules/hybrid-110-bit5.edn"}))

(def coupling-spec
  {:entropy      [0.6 0.35]
   :change       [0.2 0.2]
   :autocorr     [0.6 0.3]
   :diversity    [0.4 0.3]
   :mean-coupling [0.08 0.06]
   :coupling-cv  [0.5 0.3]})

;; =============================================================================
;; RUN ON MULTIPLE SEEDS
;; =============================================================================

(println (str "\nRunning " n-seeds " seeds x " generations " generations (width=" width ")..."))
(println)

(def run-results
  (mapv (fn [i]
          (let [seed (+ base-seed (* i 7919))
                genotype (wrt/random-genotype width seed)
                t0 (System/currentTimeMillis)
                result (runner/run-tpg
                        {:genotype genotype
                         :generations generations
                         :tpg tpg-graph
                         :verifier-spec coupling-spec
                         :seed seed
                         :wiring-operators wiring-operators
                         :spatial-coupling? true})
                t1 (System/currentTimeMillis)]
            (printf "  Seed %d: overall=%.3f  elapsed=%dms\n"
                    seed
                    (double (get-in result [:verifier-result :overall-satisfaction]))
                    (- t1 t0))
            (flush)
            result))
        (range n-seeds)))

;; =============================================================================
;; COUPLING ANALYSIS PER RUN
;; =============================================================================

(println "\nComputing coupling spectra...")

(def run-couplings
  (mapv (fn [run]
          (bitplane/coupling-spectrum (:gen-history run)
                                      {:spatial? true :temporal? false}))
        run-results))

;; =============================================================================
;; CROSS-RUN STABILITY METRICS
;; =============================================================================

(println "\n--- Per-Run Coupling ---")
(printf "  %-6s %-10s %-10s %-10s %-10s %-15s\n"
        "Seed" "MI" "MaxMI" "CV" "Hotspot" "Summary")
(println "  " (apply str (repeat 65 "-")))
(doseq [[i coupling] (map-indexed vector run-couplings)]
  (let [sp (:spatial-profile coupling)]
    (printf "  %-6d %-10.4f %-10.4f %-10.3f %-10.3f %-15s\n"
            i
            (double (:mean-coupling coupling))
            (double (:max-coupling coupling))
            (double (or (:coupling-cv coupling) 0.0))
            (double (or (:hotspot-fraction sp) 0.0))
            (name (or (:summary coupling) :unknown)))))

;; MI statistics
(let [mis (mapv :mean-coupling run-couplings)
      mi-mean (/ (reduce + 0.0 mis) (count mis))
      mi-var (/ (reduce + 0.0 (map #(let [d (- (double %) mi-mean)] (* d d)) mis))
                (double (max 1 (dec (count mis)))))
      mi-std (Math/sqrt mi-var)
      mi-cv (if (pos? mi-mean) (/ mi-std mi-mean) 0.0)]

  (println "\n--- MI Statistics ---")
  (printf "  Mean MI:  %.4f\n" mi-mean)
  (printf "  Std MI:   %.4f\n" mi-std)
  (printf "  CV MI:    %.3f  %s\n" mi-cv
          (cond (< mi-cv 0.3) "(stable)"
                (< mi-cv 0.7) "(moderate)"
                :else "(unstable)"))

  ;; Matrix correlation: pairwise Pearson between coupling matrices
  (let [flatten-upper (fn [matrix]
                        (vec (for [i (range 8) j (range (inc i) 8)]
                               (double (get-in matrix [i j] 0.0)))))
        flat-matrices (mapv #(flatten-upper (:coupling-matrix %)) run-couplings)
        pearson (fn [xs ys]
                  (let [n (count xs)
                        mx (/ (reduce + 0.0 xs) n)
                        my (/ (reduce + 0.0 ys) n)
                        cov (/ (reduce + 0.0 (map #(* (- (double %1) mx) (- (double %2) my)) xs ys)) n)
                        sx (Math/sqrt (/ (reduce + 0.0 (map #(let [d (- (double %) mx)] (* d d)) xs)) n))
                        sy (Math/sqrt (/ (reduce + 0.0 (map #(let [d (- (double %) my)] (* d d)) ys)) n))]
                    (if (and (pos? sx) (pos? sy)) (/ cov (* sx sy)) 0.0)))
        n-mat (count flat-matrices)
        matrix-corrs (for [i (range n-mat)
                           j (range (inc i) n-mat)]
                       (pearson (nth flat-matrices i) (nth flat-matrices j)))
        matrix-correlation (if (seq matrix-corrs)
                             (/ (reduce + 0.0 matrix-corrs) (count matrix-corrs))
                             0.0)]

    (println "\n--- Matrix Correlation ---")
    (printf "  Mean pairwise Pearson: %.3f  %s\n"
            matrix-correlation
            (cond (> matrix-correlation 0.8) "(highly consistent)"
                  (> matrix-correlation 0.5) "(moderately consistent)"
                  :else "(inconsistent)"))

    ;; Summary consistency
    (let [summaries (mapv :summary run-couplings)
          freq (frequencies summaries)
          mode-summary (key (apply max-key val freq))
          consistency (/ (double (get freq mode-summary)) (count summaries))]

      (println "\n--- Summary Classification ---")
      (printf "  Distribution: %s\n" (pr-str freq))
      (printf "  Mode: %s (%.0f%% of runs)\n" (name mode-summary) (* 100.0 consistency))

      ;; Hotspot consistency (Jaccard index)
      (let [spatial-profiles (keep :spatial-profile run-couplings)
            hotspot-sets (when (>= (count spatial-profiles) 2)
                           (mapv (fn [sp]
                                   (let [per-cell (:per-cell sp)
                                         threshold (* 2.0 (or (:mean sp) 0.0))]
                                     (set (keep-indexed
                                           (fn [i v] (when (> (double v) threshold) i))
                                           per-cell))))
                                 spatial-profiles))
            jaccard-pairs (when (and hotspot-sets (> (count hotspot-sets) 1))
                            (for [i (range (count hotspot-sets))
                                  j (range (inc i) (count hotspot-sets))]
                              (let [a (nth hotspot-sets i)
                                    b (nth hotspot-sets j)
                                    intersection (count (clojure.set/intersection a b))
                                    union-size (count (clojure.set/union a b))]
                                (if (pos? union-size)
                                  (/ (double intersection) union-size)
                                  0.0))))
            mean-jaccard (when (seq jaccard-pairs)
                           (/ (reduce + 0.0 jaccard-pairs) (count jaccard-pairs)))]

        (println "\n--- Hotspot Consistency ---")
        (if mean-jaccard
          (printf "  Mean Jaccard index: %.3f  %s\n"
                  mean-jaccard
                  (cond (> mean-jaccard 0.5) "(same hotspots)"
                        (> mean-jaccard 0.2) "(overlapping hotspots)"
                        :else "(different hotspots)"))
          (println "  No spatial profiles available"))

        ;; Composite stability score
        (let [stability-score (* (- 1.0 (min 1.0 mi-cv))
                                 (max 0.0 matrix-correlation)
                                 consistency)]
          (println "\n======================================================================")
          (println "  STABILITY SCORE")
          (println "======================================================================")
          (printf "\n  Stability: %.3f\n" stability-score)
          (printf "    = (1 - MI_CV=%.2f) x matrix_corr=%.2f x summary_consistency=%.2f\n"
                  mi-cv matrix-correlation consistency)
          (println)
          (println (cond
                     (> stability-score 0.7) "  VERDICT: High stability — coupling structure persists across seeds"
                     (> stability-score 0.4) "  VERDICT: Moderate stability — coupling structure partially persists"
                     :else                   "  VERDICT: Low stability — coupling structure is seed-dependent"))
          (println))))))

(println "Done.")
