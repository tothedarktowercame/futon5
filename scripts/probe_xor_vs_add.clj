#!/usr/bin/env bb
;; Probe: XOR coupling vs carry-chain (AddSelf) coupling
;;
;; Tests the hypothesis that the diversity-coupling tradeoff (Pivot 13)
;; is specific to carry-chain arithmetic. XOR couples all bitplanes
;; without carry propagation — a flip at bit n affects only bit n.
;;
;; Runs R110+AddSelf and R110+XorSelf on the same seeds, measures:
;; - Coupling: mean MI, max MI, coupling CV, coupling summary
;; - Diversity: symbol diversity (unique sigils / total sigils)
;; - Health: Wolfram class estimate
;;
;; Usage:
;;   bb -cp src:resources scripts/probe_xor_vs_add.clj [n-seeds] [generations] [width]

(require '[futon5.wiring.runtime :as wrt]
         '[futon5.mmca.bitplane-analysis :as bitplane]
         '[futon5.mmca.wolfram-class :as wolfram]
         '[futon5.mmca.domain-analysis :as domain]
         '[futon5.mmca.particle-analysis :as particle]
         '[clojure.string :as str])

;; =============================================================================
;; CONFIGURATION
;; =============================================================================

(def n-seeds (or (some-> (first *command-line-args*) parse-long) 10))
(def generations (or (some-> (second *command-line-args*) parse-long) 50))
(def width (or (some-> (nth *command-line-args* 2 nil) parse-long) 64))
(def base-seed 4242)

(def wirings
  [{:id :addself
    :label "R110+AddSelf"
    :path "data/wiring-rules/hybrid-110-addself.edn"}
   {:id :xorself
    :label "R110+XorSelf"
    :path "data/wiring-rules/hybrid-110-xorself.edn"}])

;; Also include pure R110 as a baseline (coupling should be ~0)
(def baseline
  {:id :r110
   :label "R110 (pure)"
   :path "data/wiring-rules/rule-110.edn"})

(def all-wirings (conj wirings baseline))

;; =============================================================================
;; RUN + ANALYZE
;; =============================================================================

(defn symbol-diversity
  "Ratio of unique symbols to total length."
  [s]
  (let [len (count s)]
    (if (pos? len)
      (/ (double (count (frequencies (seq s)))) (double len))
      0.0)))

(defn mean-diversity
  "Mean symbol diversity across all generations."
  [history]
  (let [divs (mapv symbol-diversity history)]
    (/ (reduce + 0.0 divs) (max 1 (count divs)))))

(defn run-probe [{:keys [id label path]} seed]
  (let [genotype (wrt/random-genotype width seed)
        result (wrt/run-wiring-from-file
                {:wiring-path path
                 :genotype genotype
                 :generations generations})
        history (:gen-history result)
        coupling (bitplane/coupling-spectrum history {:temporal? false :spatial? false})
        diversity (mean-diversity history)
        ;; Final-generation diversity
        final-div (symbol-diversity (last history))]
    {:id id
     :label label
     :seed seed
     :mean-coupling (:mean-coupling coupling)
     :max-coupling (:max-coupling coupling)
     :coupling-cv (:coupling-cv coupling)
     :coupling-summary (:summary coupling)
     :coupling-matrix (:coupling-matrix coupling)
     :mean-diversity diversity
     :final-diversity final-div}))

;; =============================================================================
;; MAIN
;; =============================================================================

(println)
(println "======================================================================")
(println "  COUPLING MECHANISM COMPARISON: AddSelf vs XorSelf")
(println "======================================================================")
(println)
(printf "  Seeds: %d  Generations: %d  Width: %d\n" n-seeds generations width)
(println)

;; Run all combinations
(def results
  (vec
   (for [wiring all-wirings
         i (range n-seeds)]
     (let [seed (+ base-seed (* i 7919))
           _ (printf "  %-15s seed %-10d ... " (:label wiring) seed)
           _ (flush)
           t0 (System/nanoTime)
           result (run-probe wiring seed)
           ms (/ (- (System/nanoTime) t0) 1e6)]
       (printf "MI=%.4f  div=%.3f  (%,.0fms)\n"
               (double (:mean-coupling result))
               (double (:mean-diversity result))
               ms)
       (flush)
       result))))

;; =============================================================================
;; AGGREGATE BY WIRING TYPE
;; =============================================================================

(defn aggregate [results]
  (let [n (count results)
        mean-of (fn [k] (/ (reduce + 0.0 (map #(double (get % k 0.0)) results)) n))
        std-of (fn [k]
                 (let [m (mean-of k)
                       var (/ (reduce + 0.0 (map #(let [d (- (double (get % k 0.0)) m)]
                                                     (* d d)) results))
                              (max 1 (dec n)))]
                   (Math/sqrt var)))]
    {:n n
     :mean-coupling (mean-of :mean-coupling)
     :std-coupling (std-of :mean-coupling)
     :mean-max-coupling (mean-of :max-coupling)
     :mean-diversity (mean-of :mean-diversity)
     :std-diversity (std-of :mean-diversity)
     :mean-final-diversity (mean-of :final-diversity)
     :std-final-diversity (std-of :final-diversity)
     :coupling-summaries (frequencies (map :coupling-summary results))}))

(def grouped (group-by :id results))

(println)
(println "======================================================================")
(println "  AGGREGATE RESULTS")
(println "======================================================================")
(println)
(printf "  %-15s %8s %8s %8s %8s %8s %8s  %s\n"
        "Wiring" "MI" "±MI" "MaxMI" "Div" "±Div" "FnlDiv" "Summary")
(println (str "  " (apply str (repeat 82 "-"))))

(doseq [wiring all-wirings]
  (let [agg (aggregate (get grouped (:id wiring)))]
    (printf "  %-15s %8.4f %8.4f %8.4f %8.3f %8.3f %8.3f  %s\n"
            (:label wiring)
            (double (:mean-coupling agg))
            (double (:std-coupling agg))
            (double (:mean-max-coupling agg))
            (double (:mean-diversity agg))
            (double (:std-diversity agg))
            (double (:mean-final-diversity agg))
            (pr-str (:coupling-summaries agg)))))

;; =============================================================================
;; COUPLING MATRIX COMPARISON
;; =============================================================================

(println)
(println "--- Mean Coupling Matrices (8x8 bitplane MI) ---")
(println)

(doseq [wiring all-wirings]
  (let [runs (get grouped (:id wiring))
        matrices (keep :coupling-matrix runs)]
    (when (seq matrices)
      (let [n (count matrices)
            mean-matrix (vec (for [i (range 8)]
                               (vec (for [j (range 8)]
                                      (/ (reduce + 0.0
                                                 (map #(double (get-in % [i j] 0.0)) matrices))
                                         n)))))]
        (println (str "  " (:label wiring) ":"))
        (doseq [row mean-matrix]
          (print "    ")
          (doseq [v row]
            (printf "%6.3f " (double v)))
          (println))
        (println)))))

;; =============================================================================
;; VERDICT
;; =============================================================================

(let [add-agg (aggregate (get grouped :addself))
      xor-agg (aggregate (get grouped :xorself))
      r110-agg (aggregate (get grouped :r110))
      add-mi (:mean-coupling add-agg)
      xor-mi (:mean-coupling xor-agg)
      add-div (:mean-diversity add-agg)
      xor-div (:mean-diversity xor-agg)
      r110-div (:mean-diversity r110-agg)]
  (println "======================================================================")
  (println "  VERDICT")
  (println "======================================================================")
  (println)
  (printf "  Coupling:  AddSelf MI=%.4f  XorSelf MI=%.4f  (R110 baseline=%.4f)\n"
          (double add-mi) (double xor-mi) (double (:mean-coupling r110-agg)))
  (printf "  Diversity: AddSelf=%.3f  XorSelf=%.3f  (R110 baseline=%.3f)\n"
          (double add-div) (double xor-div) (double r110-div))
  (println)
  (cond
    (and (> xor-mi 0.01) (> xor-div (* 0.8 r110-div)))
    (println "  → XOR couples WITHOUT destroying diversity. The tradeoff is CARRY-CHAIN SPECIFIC.")

    (and (> xor-mi 0.01) (<= xor-div (* 0.8 r110-div)))
    (println "  → XOR couples but ALSO destroys diversity. The tradeoff is FUNDAMENTAL to coupling.")

    (<= xor-mi 0.01)
    (println "  → XOR does NOT couple. Need a different non-arithmetic coupling mechanism.")

    :else
    (println "  → Inconclusive. Need more seeds or longer runs."))
  (println))

(println "Done.")
