#!/usr/bin/env bb
;; TPG Coupling-Aware Evolution: Visual Report
;;
;; Exercises all new features from the TPG Integration + Coupling-Aware Evolution:
;;   1. bit-test component — bitplane-aware conditional hybrids
;;   2. Coupling-aware fitness — extended diagnostics in verifier evaluation
;;   3. Wiring-diagram operators in TPG — bridge wiring runtime into evolution
;;   4. Temporal schedule — alternating operator patterns
;;
;; Produces spacetime diagrams, bitplane grids, coupling heatmaps, and
;; comparison tables for all hybrid variants.
;;
;; Usage:
;;   bb -cp src:resources scripts/tpg_coupling_report.clj

(require '[futon5.ca.core :as ca]
         '[futon5.wiring.runtime :as wrt]
         '[futon5.mmca.render :as render]
         '[futon5.mmca.metrics :as metrics]
         '[futon5.mmca.domain-analysis :as domain]
         '[futon5.mmca.particle-analysis :as particle]
         '[futon5.mmca.bitplane-analysis :as bitplane]
         '[futon5.mmca.wolfram-class :as wclass]
         '[futon5.tpg.core :as tpg]
         '[futon5.tpg.runner :as runner]
         '[futon5.tpg.diagnostics :as diag]
         '[futon5.tpg.verifiers :as verifiers]
         '[futon5.tpg.evolve :as evolve]
         '[clojure.java.shell :as shell]
         '[clojure.string :as str])

;; =============================================================================
;; SETUP
;; =============================================================================

(def out-dir "out/tpg-coupling-report")
(defn ensure-dir! [dir] (.mkdirs (java.io.File. dir)))
(ensure-dir! out-dir)

(defn ppm->png! [ppm-path png-path scale]
  (let [result (shell/sh "convert" ppm-path
                         "-scale" (str scale "%")
                         "-interpolate" "Nearest" "-filter" "point"
                         png-path)]
    (when (zero? (:exit result))
      png-path)))

(defn fmt [x d] (if (number? x) (format (str "%." d "f") (double x)) (str x)))

(def seed 352362012)
(def width 100)
(def generations 100)

(println "\n======================================================================")
(println "  TPG COUPLING-AWARE EVOLUTION — VISUAL REPORT")
(println "======================================================================")
(printf  "\n  Width: %d  Generations: %d  Seed: %d\n" width generations seed)
(printf  "  Output: %s/\n\n" out-dir)

;; =============================================================================
;; SECTION 1: BITPLANE-AWARE HYBRIDS
;; =============================================================================

(println "======================================================================")
(println "  SECTION 1: Bitplane-Aware Conditional Hybrids")
(println "======================================================================\n")

(def hybrids
  [{:id "rule-110"          :path "data/wiring-rules/rule-110.edn"
    :label "Rule 110"        :group :reference}
   {:id "hybrid-110-addself" :path "data/wiring-rules/hybrid-110-addself.edn"
    :label "R110+AddSelf"    :group :unconditional}
   {:id "hybrid-110-boundary" :path "data/wiring-rules/hybrid-110-boundary.edn"
    :label "R110+Boundary"   :group :hamming-cond}
   {:id "hybrid-110-msb"    :path "data/wiring-rules/hybrid-110-msb.edn"
    :label "R110+MSB"        :group :bitplane-cond}
   {:id "hybrid-110-bit5"   :path "data/wiring-rules/hybrid-110-bit5.edn"
    :label "R110+Bit5"       :group :bitplane-cond}])

(defn render-spacetime! [id history]
  (let [pixels (render/render-history history)
        ppm (str out-dir "/" id ".ppm")
        png (str out-dir "/" id ".png")]
    (render/write-ppm! ppm pixels :comment id)
    (ppm->png! ppm png 400)))

(defn render-bitplane-grid! [id history]
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
    (ppm->png! ppm png 300)))

(defn render-coupling-heatmap! [id history]
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
    (ppm->png! ppm png 400)))

(defn run-and-analyze [{:keys [id path label]}]
  (print (str "  Running " label "... "))
  (flush)
  (let [t0 (System/nanoTime)
        genotype (wrt/random-genotype width seed)
        result (wrt/run-wiring-from-file
                {:wiring-path path
                 :genotype genotype
                 :generations generations})
        history (:gen-history result)
        ms (/ (- (System/nanoTime) t0) 1e6)]

    ;; Render visuals
    (render-spacetime! id history)
    (render-bitplane-grid! id history)
    (render-coupling-heatmap! id history)

    ;; Analyze
    (let [bp-analyses (bitplane/analyze-all-bitplanes history)
          bp-summary (bitplane/aggregate-bitplane-metrics bp-analyses)
          coupling (bitplane/coupling-spectrum history {:temporal? true :spatial? true})
          domain-result (domain/analyze-domain history)
          particle-result (particle/analyze-particles history)
          class-result (wclass/estimate-class result)]
      (printf "%.0fms\n" ms)
      {:id id :label label :history history
       :class class-result
       :domain domain-result
       :particles particle-result
       :bp-summary bp-summary
       :bp-analyses bp-analyses
       :coupling coupling})))

(def hybrid-results (mapv run-and-analyze hybrids))

;; Summary table
(println)
(printf "  %-20s %5s %6s %6s %5s %5s %4s  %-22s  %s\n"
        "System" "BpDF" "MI" "MaxMI" "CV" "Part" "Spe" "Coupling" "Class")
(println (str "  " (apply str (repeat 100 "-"))))

(doseq [r hybrid-results]
  (let [c (:coupling r)
        bp (:bp-summary r)
        p (:particles r)
        cl (:class r)]
    (printf "  %-20s %5s %6s %6s %5s %5d %4d  %-22s  %s (%.2f)\n"
            (:label r)
            (fmt (:best-domain-fraction bp) 3)
            (fmt (:mean-coupling c) 4)
            (fmt (:max-coupling c) 4)
            (fmt (:coupling-cv c) 2)
            (:particle-count p)
            (:species-count p)
            (name (or (:summary c) :unknown))
            (name (:class cl))
            (double (:confidence cl)))))
(println (str "  " (apply str (repeat 100 "-"))))

;; Detailed per-bitplane report for new hybrids
(println "\n  --- Per-bitplane detail for new conditional hybrids ---")
(doseq [r (filter #(= :bitplane-cond (:group (some (fn [h] (when (= (:id h) (:id %)) h)) hybrids))) hybrid-results)]
  (println (str "\n  " (:label r) ":"))
  (doseq [a (:bp-analyses r)]
    (printf "    Plane %d: df=%s  change=%s  iv?=%s\n"
            (:plane a) (fmt (:domain-fraction a) 3) (fmt (:mean-change a) 3) (:class-iv-candidate? a)))
  (let [c (:coupling r)
        matrix (:coupling-matrix c)]
    (println "    Coupling matrix:")
    (printf  "             ")
    (doseq [j (range 8)] (printf " bp%-2d" j))
    (println)
    (doseq [i (range 8)]
      (printf "      bp%d " i)
      (doseq [j (range 8)]
        (let [v (nth (nth matrix i) j)]
          (if (= i j) (printf "  -- ") (printf " %4s" (fmt v 3)))))
      (println))))

;; =============================================================================
;; SECTION 2: TPG WIRING OPERATOR DISPATCH
;; =============================================================================

(println "\n======================================================================")
(println "  SECTION 2: TPG Wiring Operator Dispatch")
(println "======================================================================\n")

(def wiring-operators
  (runner/load-wiring-operators
   {:wiring-addself "data/wiring-rules/hybrid-110-addself.edn"
    :wiring-msb     "data/wiring-rules/hybrid-110-msb.edn"
    :wiring-bit5    "data/wiring-rules/hybrid-110-bit5.edn"}))

(def coupling-spec
  {:entropy  [0.6 0.35]
   :change   [0.2 0.2]
   :autocorr [0.6 0.3]
   :diversity [0.4 0.3]
   :mean-coupling [0.08 0.06]
   :coupling-cv [0.5 0.3]})

;; Run each wiring operator as a TPG
(def wiring-run-results
  (vec (for [op-id [:wiring-addself :wiring-msb :wiring-bit5]]
         (do
           (print (str "  Running TPG with " (name op-id) "... "))
           (flush)
           (let [t0 (System/nanoTime)
                 genotype (wrt/random-genotype width seed)
                 test-tpg (tpg/make-tpg
                           (str "wiring-" (name op-id))
                           [(tpg/make-team
                             :root
                             [(tpg/make-program :p1 [0.0 0.0 0.0 0.0 0.0 0.0] 0.5
                                                :operator op-id)])]
                           {:root-team :root})
                 result (runner/run-tpg
                         {:genotype genotype
                          :generations generations
                          :tpg test-tpg
                          :verifier-spec coupling-spec
                          :seed seed
                          :wiring-operators wiring-operators})
                 history (:gen-history result)
                 ms (/ (- (System/nanoTime) t0) 1e6)
                 id (str "tpg-" (name op-id))]

             ;; Render visuals
             (render-spacetime! id history)
             (render-bitplane-grid! id history)
             (render-coupling-heatmap! id history)

             ;; Coupling analysis
             (let [coupling (bitplane/coupling-spectrum history {:spatial? false :temporal? false})]
               (printf "%.0fms\n" ms)
               {:op-id op-id :result result :coupling coupling :id id}))))))

;; Satisfaction comparison table
(println)
(printf "  %-20s" "Verifier Dim")
(doseq [r wiring-run-results] (printf " %14s" (name (:op-id r))))
(println)
(println (str "  " (apply str (repeat 65 "-"))))

(let [all-keys (sort (keys coupling-spec))]
  (doseq [k all-keys]
    (printf "  %-20s" (name k))
    (doseq [r wiring-run-results]
      (let [sat (get-in r [:result :verifier-result :satisfaction-vector k] 0.0)]
        (printf " %14s" (fmt sat 3))))
    (println))
  (println (str "  " (apply str (repeat 65 "-"))))
  (printf "  %-20s" "OVERALL")
  (doseq [r wiring-run-results]
    (printf " %14s" (fmt (get-in r [:result :verifier-result :overall-satisfaction]) 3)))
  (println))

;; Coupling comparison
(println "\n  --- Coupling Spectrum ---")
(printf "  %-20s %8s %8s %8s  %s\n" "Operator" "MI" "MaxMI" "CV" "Summary")
(println (str "  " (apply str (repeat 65 "-"))))
(doseq [r wiring-run-results]
  (let [c (:coupling r)]
    (printf "  %-20s %8s %8s %8s  %s\n"
            (name (:op-id r))
            (fmt (:mean-coupling c) 4)
            (fmt (:max-coupling c) 4)
            (fmt (:coupling-cv c) 2)
            (name (or (:summary c) :unknown)))))

;; Extended diagnostics detail
(println "\n  --- Extended Diagnostics (run-level) ---")
(doseq [r wiring-run-results]
  (let [ext (:extended (first (get-in r [:result :diagnostics-trace])))]
    (printf "  %s: mean-coupling=%.4f  coupling-cv=%.4f  compression-cv=%.4f  domain-frac=%.4f  particles=%.4f\n"
            (name (:op-id r))
            (double (or (:mean-coupling ext) 0.0))
            (double (or (:coupling-cv ext) 0.0))
            (double (or (:compression-cv ext) 0.0))
            (double (or (:domain-fraction ext) 0.0))
            (double (or (:particle-count ext) 0.0)))))

;; =============================================================================
;; SECTION 3: TEMPORAL SCHEDULE
;; =============================================================================

(println "\n======================================================================")
(println "  SECTION 3: Temporal Schedule Comparison")
(println "======================================================================\n")

(def schedule-configs
  [{:id "sched-4-1" :label "4:1 AddSelf:Conserve"
    :schedule [{:operator :wiring-addself :steps 4} {:operator :conservation :steps 1}]}
   {:id "sched-2-2" :label "2:2 AddSelf:MSB"
    :schedule [{:operator :wiring-addself :steps 2} {:operator :wiring-msb :steps 2}]}
   {:id "sched-1-1-1" :label "1:1:1 Round-robin"
    :schedule [{:operator :wiring-addself :steps 1}
               {:operator :wiring-msb :steps 1}
               {:operator :wiring-bit5 :steps 1}]}])

(def schedule-results
  (vec (for [{:keys [id label schedule]} schedule-configs]
         (do
           (print (str "  Running " label "... "))
           (flush)
           (let [t0 (System/nanoTime)
                 genotype (wrt/random-genotype width seed)
                 ;; TPG routes to conservation by default, schedule overrides
                 base-tpg (tpg/make-tpg
                           "schedule-base"
                           [(tpg/make-team
                             :root
                             [(tpg/make-program :p1 [0.0 0.0 0.0 0.0 0.0 0.0] 0.5
                                                :operator :conservation)])]
                           {:root-team :root})
                 result (runner/run-tpg
                         {:genotype genotype
                          :generations generations
                          :tpg base-tpg
                          :verifier-spec coupling-spec
                          :seed seed
                          :wiring-operators wiring-operators
                          :temporal-schedule schedule})
                 history (:gen-history result)
                 ms (/ (- (System/nanoTime) t0) 1e6)]

             ;; Render
             (render-spacetime! id history)
             (render-coupling-heatmap! id history)

             (let [coupling (bitplane/coupling-spectrum history {:spatial? false :temporal? false})
                   ops (frequencies (map :operator-id (:routing-trace result)))]
               (printf "%.0fms\n" ms)
               {:id id :label label :result result :coupling coupling :ops ops}))))))

;; Schedule comparison table
(println)
(printf "  %-25s %8s %8s %8s  %s\n" "Schedule" "MI" "MaxMI" "CV" "Operator Mix")
(println (str "  " (apply str (repeat 90 "-"))))
(doseq [r schedule-results]
  (let [c (:coupling r)
        ops (:ops r)
        mix-str (str/join " " (map (fn [[k v]] (str (name k) "=" v)) (sort-by val > ops)))]
    (printf "  %-25s %8s %8s %8s  %s\n"
            (:label r)
            (fmt (:mean-coupling c) 4)
            (fmt (:max-coupling c) 4)
            (fmt (:coupling-cv c) 2)
            mix-str)))

;; Satisfaction vectors
(println)
(printf "  %-25s" "Verifier")
(doseq [r schedule-results] (printf " %14s" (:id r)))
(println)
(println (str "  " (apply str (repeat 70 "-"))))
(doseq [k (sort (keys coupling-spec))]
  (printf "  %-25s" (name k))
  (doseq [r schedule-results]
    (printf " %14s" (fmt (get-in r [:result :verifier-result :satisfaction-vector k] 0.0) 3)))
  (println))
(println (str "  " (apply str (repeat 70 "-"))))
(printf "  %-25s" "OVERALL")
(doseq [r schedule-results]
  (printf " %14s" (fmt (get-in r [:result :verifier-result :overall-satisfaction]) 3)))
(println)

;; =============================================================================
;; SECTION 4: SHORT TPG EVOLUTION (coupling-aware)
;; =============================================================================

(println "\n======================================================================")
(println "  SECTION 4: TPG Evolution with Coupling Fitness")
(println "======================================================================\n")

(println "  Running short evolution (4 parents, 4 offspring, 3 evo-gens)...")
(let [t0 (System/nanoTime)
      config {:mu 4
              :lambda 4
              :eval-runs 2
              :eval-generations 40
              :genotype-length width
              :evo-generations 3
              :verifier-spec coupling-spec
              :seed seed
              :wiring-operators wiring-operators
              :verbose? true}
      result (evolve/evolve config)
      best (:best result)
      ms (/ (- (System/nanoTime) t0) 1e6)]

  (printf "\n  Evolution completed in %.1fs\n" (/ ms 1000.0))
  (printf "  Best overall satisfaction: %.3f\n" (double (:overall-satisfaction best)))
  (println "  Satisfaction vector:")
  (doseq [[k v] (sort-by key (:satisfaction-vector best))]
    (printf "    %-20s %.3f\n" (name k) (double v)))
  (printf "  Teams: %d  Programs: %d\n"
          (count (:teams best))
          (reduce + (map #(count (:programs %)) (:teams best))))

  ;; Operator targets in best TPG
  (println "  Operator targets in best TPG:")
  (let [all-actions (for [team (:teams best)
                          prog (:programs team)]
                      (get-in prog [:action :target]))
        action-freq (frequencies all-actions)]
    (doseq [[op cnt] (sort-by (comp - val) action-freq)]
      (printf "    %-20s %d\n" (name op) cnt)))

  ;; Evolution history
  (println "\n  Evolution history:")
  (printf "  %4s %10s %10s %6s\n" "Gen" "Best" "Mean" "Front")
  (println (str "  " (apply str (repeat 35 "-"))))
  (doseq [h (:history result)]
    (printf "  %4d %10s %10s %6d\n"
            (:generation h)
            (fmt (:best-overall h) 3)
            (fmt (:mean-overall h) 3)
            (:front-size h)))

  ;; Run the best TPG and render its spacetime diagram
  (println "\n  Rendering best TPG's spacetime diagram...")
  (let [genotype (wrt/random-genotype width seed)
        best-run (runner/run-tpg
                  {:genotype genotype
                   :generations generations
                   :tpg best
                   :verifier-spec coupling-spec
                   :seed seed
                   :wiring-operators wiring-operators})
        history (:gen-history best-run)]
    (render-spacetime! "evo-best" history)
    (render-bitplane-grid! "evo-best" history)
    (render-coupling-heatmap! "evo-best" history)

    ;; Coupling analysis of best run
    (let [coupling (bitplane/coupling-spectrum history {:spatial? true :temporal? false})]
      (printf "  Best TPG coupling: MI=%s  MaxMI=%s  CV=%s  Summary=%s\n"
              (fmt (:mean-coupling coupling) 4)
              (fmt (:max-coupling coupling) 4)
              (fmt (:coupling-cv coupling) 2)
              (name (or (:summary coupling) :unknown)))

      ;; Spatial coupling sparkline
      (when-let [sp (:spatial-profile coupling)]
        (let [per-cell (:per-cell sp)
              n (count per-cell)
              max-v (max 0.001 (apply max per-cell))
              bar-height 6
              bars (mapv (fn [v] (int (* bar-height (/ v (max 0.001 max-v))))) per-cell)]
          (println "  Spatial coupling profile:")
          (doseq [h (range bar-height 0 -1)]
            (printf "  %s\n"
                    (apply str (map #(if (>= % h) "\u2588" " ") bars)))))))))

;; =============================================================================
;; SECTION 5: COUPLING LANDSCAPE
;; =============================================================================

(println "\n======================================================================")
(println "  SECTION 5: Coupling Landscape — All Variants Compared")
(println "======================================================================\n")

;; Gather all results
(def all-results
  (concat
   ;; Section 1: wiring-only runs
   (map (fn [r] {:label (:label r) :coupling (:coupling r) :group "Wiring Rule"}) hybrid-results)
   ;; Section 2: TPG-dispatched runs
   (map (fn [r] {:label (str "TPG:" (name (:op-id r))) :coupling (:coupling r) :group "TPG Dispatch"}) wiring-run-results)
   ;; Section 3: Scheduled runs
   (map (fn [r] {:label (:label r) :coupling (:coupling r) :group "Schedule"}) schedule-results)))

(printf "  %-30s %7s %7s %7s %6s  %-22s  %s\n"
        "Variant" "MI" "MaxMI" "CV" "Indep" "Summary" "Group")
(println (str "  " (apply str (repeat 105 "-"))))
(doseq [r (sort-by #(get-in % [:coupling :mean-coupling]) > all-results)]
  (let [c (:coupling r)]
    (printf "  %-30s %7s %7s %7s %6s  %-22s  %s\n"
            (:label r)
            (fmt (:mean-coupling c) 4)
            (fmt (:max-coupling c) 4)
            (fmt (:coupling-cv c) 2)
            (fmt (:independence-score c) 3)
            (name (or (:summary c) :unknown))
            (:group r))))
(println (str "  " (apply str (repeat 105 "-"))))

;; Render a combined coupling comparison panel
;; Side-by-side coupling heatmaps: R110 | AddSelf | MSB | Bit5 | Boundary
(println "\n  Rendering combined coupling comparison panel...")
(let [rule-ids ["rule-110" "hybrid-110-addself" "hybrid-110-msb" "hybrid-110-bit5" "hybrid-110-boundary"]
      histories (mapv (fn [id] (:history (first (filter #(= id (:id %)) hybrid-results)))) rule-ids)
      cell-size 10
      panel-size (* 8 cell-size)
      sep 4
      n-panels (count rule-ids)
      total-width (+ (* n-panels panel-size) (* (dec n-panels) sep))
      grey [128 128 128]
      ;; Compute all coupling matrices
      matrices (mapv (fn [h]
                       (let [bp (bitplane/decompose-history h)]
                         (bitplane/coupling-matrix bp)))
                     histories)
      ;; Find global max for consistent scale
      global-max (max 0.001
                      (apply max
                             (for [matrix matrices
                                   i (range 8) j (range 8)
                                   :when (not= i j)]
                               (nth (nth matrix i) j))))
      pixels (vec
              (for [py (range panel-size)]
                (vec
                 (for [panel-idx (range n-panels)
                       px (concat (range panel-size)
                                  (when (< panel-idx (dec n-panels)) (range sep)))]
                   (if (>= px panel-size)
                     grey
                     (let [i (quot py cell-size) j (quot px cell-size)
                           matrix (nth matrices panel-idx)
                           val (nth (nth matrix i) j)
                           norm (if (= i j) 1.0 (min 1.0 (/ val global-max)))
                           r (int (min 255 (* 255 (min 1.0 (* 2.0 norm)))))
                           g (int (min 255 (* 255 (max 0.0 (- (* 2.0 norm) 1.0)))))
                           b (int (min 255 (* 255 (max 0.0 (- (* 3.0 norm) 2.0)))))]
                       [r g b]))))))
      ppm (str out-dir "/coupling-comparison.ppm")
      png (str out-dir "/coupling-comparison.png")]
  (render/write-ppm! ppm pixels :comment "Coupling comparison: R110 | AddSelf | MSB | Bit5 | Boundary")
  (ppm->png! ppm png 400)
  (println (str "    -> " png)))

;; =============================================================================
;; SUMMARY
;; =============================================================================

(println "\n======================================================================")
(println "  REPORT SUMMARY")
(println "======================================================================\n")

(println "  KEY FINDINGS:\n")

;; Compare MSB vs Boundary
(let [msb-c (:coupling (first (filter #(= "hybrid-110-msb" (:id %)) hybrid-results)))
      bnd-c (:coupling (first (filter #(= "hybrid-110-boundary" (:id %)) hybrid-results)))
      r110-c (:coupling (first (filter #(= "rule-110" (:id %)) hybrid-results)))
      add-c (:coupling (first (filter #(= "hybrid-110-addself" (:id %)) hybrid-results)))]

  (printf "  1. bit-test MSB condition vs Hamming-based boundary:\n")
  (printf "     R110+MSB:      MI=%s  CV=%s  Summary=%s\n"
          (fmt (:mean-coupling msb-c) 4) (fmt (:coupling-cv msb-c) 2) (name (or (:summary msb-c) :unknown)))
  (printf "     R110+Boundary: MI=%s  CV=%s  Summary=%s\n"
          (fmt (:mean-coupling bnd-c) 4) (fmt (:coupling-cv bnd-c) 2) (name (or (:summary bnd-c) :unknown)))

  (printf "\n  2. Coupling hierarchy (MI):\n")
  (printf "     Rule 110 (pure):    MI=%s (baseline)\n" (fmt (:mean-coupling r110-c) 4))
  (printf "     R110+MSB:           MI=%s (%.1fx baseline)\n"
          (fmt (:mean-coupling msb-c) 4)
          (double (if (pos? (:mean-coupling r110-c))
                    (/ (:mean-coupling msb-c) (:mean-coupling r110-c))
                    0.0)))
  (printf "     R110+AddSelf:       MI=%s (%.1fx baseline)\n"
          (fmt (:mean-coupling add-c) 4)
          (double (if (pos? (:mean-coupling r110-c))
                    (/ (:mean-coupling add-c) (:mean-coupling r110-c))
                    0.0))))

(printf "\n  3. Output directory: %s/\n" out-dir)
(println "     Files generated:")
(let [files (sort (.list (java.io.File. out-dir)))]
  (doseq [f (filter #(str/ends-with? % ".png") files)]
    (println (str "       " f))))

(println "\n  Done.\n")
