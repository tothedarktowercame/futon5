#!/usr/bin/env bb
;; Characterize the known-good historical runs using TPG diagnostic & verifier tools.
;;
;; Replays best runs through the MMCA runtime, then analyzes the resulting
;; diagnostic traces with the new verifier system. Answers: what does the
;; verifier signature of a "good" run look like?
;;
;; Usage:
;;   bb -cp src:resources scripts/characterize_best_runs.clj

(require '[futon5.ca.core :as ca]
         '[futon5.mmca.runtime :as runtime]
         '[futon5.mmca.render :as render]
         '[futon5.tpg.diagnostics :as diag]
         '[futon5.tpg.verifiers :as verifiers]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

;; =============================================================================
;; KNOWN-GOOD RUNS TO CHARACTERIZE
;; =============================================================================

(def runs
  "A curated set of historically significant runs to characterize."
  [;; The champion seed — L5-creative boundary-guardian pattern
   {:id :champion-352362012
    :label "Champion seed 352362012 (legacy baseline, 120×120)"
    :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二亏风六力双毛厂无劝友风乎令兄心艺北刊白尤无劝只且飞日凡风布尸午田风及仓弓厂火书父车已飞一且付丸仅打从艺仪凡用电元井升犬刊刊支火不丸手白心二仗上乌由见元于刀允仔平一扔叮叫从大北印"
    :phenotype "000100000000101101110100111011111111111010101110000010000100011001110011110100000010110111010111000001011000110001000011"
    :generations 120
    :seed 352362012}

   ;; Same genotype, different seed — the "original best"
   {:id :baseline-4242
    :label "Baseline seed 4242 (legacy baseline, 120×120)"
    :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二亏风六力双毛厂无劝友风乎令兄心艺北刊白尤无劝只且飞日凡风布尸午田风及仓弓厂火书父车已飞一且付丸仅打从艺仪凡用电元井升犬刊刊支火不丸手白心二仗上乌由见元于刀允仔平一扔叮叫从大北印"
    :phenotype "000100000000101101110100111011111111111010101110000010000100011001110011110100000010110111010111000001011000110001000011"
    :generations 120
    :seed 4242}

   ;; Mission 17a exotic (工 super exotype)
   {:id :exotic-gong-4242
    :label "Exotic 工 super (seed 4242, 120×120)"
    :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二亏风六力双毛厂无劝友风乎令兄心艺北刊白尤无劝只且飞日凡风布尸午田风及仓弓厂火书父车已飞一且付丸仅打从艺仪凡用电元井升犬刊刊支火不丸手白心二仗上乌由见元于刀允仔平一扔叮叫从大北印"
    :phenotype "000100000000101101110100111011111111111010101110000010000100011001110011110100000010110111010111000001011000110001000011"
    :generations 120
    :seed 4242
    :exotype {:sigil "工" :tier :super}}

   ;; Mission 7A top genotype — shorter, different config
   {:id :m07a-top2
    :label "Mission 7A top #2 (seed 1701878710, 50×30)"
    :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二亏风六力双毛厂无劝友风乎令兄心"
    :phenotype "00010000000010110111010011101111111111101010111000"
    :generations 30
    :seed 1701878710}

   ;; Mission 7B top exotype — 兄 super with invert-on-phenotype
   {:id :m07b-top1
    :label "Mission 7B top #1 兄 super (seed 1695261645, 50×30)"
    :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二亏风六力双毛厂无劝友风乎令兄心"
    :phenotype "00010000000010110111010011101111111111101010111000"
    :generations 30
    :seed 1695261645
    :exotype {:sigil "兄" :tier :super}}

   ;; Arrow pilot: the best arrow (丁 super, highest composite-score 39.29)
   {:id :arrow-ding-super
    :label "Arrow pilot 丁 super (seed 1188240613, 32×32)"
    :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二"
    :phenotype "01101000101111101110011100011001"
    :generations 32
    :seed 1188240613
    :exotype {:sigil "丁" :tier :super}}

   ;; Arrow pilot: noop baseline (good reference)
   {:id :arrow-noop-baseline
    :label "Arrow pilot noop baseline (seed 1188240613, 32×32)"
    :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二"
    :phenotype "01101000101111101110011100011001"
    :generations 32
    :seed 1188240613}])

;; =============================================================================
;; REPLAY AND CHARACTERIZE
;; =============================================================================

(defn replay-run
  "Replay a run through the MMCA runtime and return gen/phe histories."
  [{:keys [genotype phenotype generations seed exotype] :as config}]
  (let [opts (cond-> {:genotype genotype
                      :generations generations
                      :seed seed
                      :kernel :mutating-template
                      :exotype-mode :local-physics}
               phenotype (assoc :phenotype phenotype)
               exotype (assoc :exotype exotype))
        result (runtime/run-mmca opts)]
    {:gen-history (:gen-history result)
     :phe-history (:phe-history result)
     :metrics-history (:metrics-history result)}))

(defn compute-diagnostic-trace
  "Compute the full diagnostic trace for a run."
  [{:keys [gen-history phe-history]}]
  (diag/diagnostics-trace {:gen-history gen-history
                           :phe-history phe-history}))

(defn characterize-run
  "Full characterization of a single run."
  [{:keys [id label] :as config}]
  (println (str "\n  Replaying " label "..."))
  (let [t0 (System/nanoTime)
        run-result (replay-run config)
        trace (compute-diagnostic-trace run-result)
        ;; Evaluate against default verifier spec
        verifier-result (verifiers/evaluate-run trace verifiers/default-spec)
        ;; Also evaluate against several alternative specs to find best-fit
        alt-specs {:tai-zone verifiers/default-spec
                   :high-entropy {:entropy [0.8 0.15]
                                  :change [0.5 0.3]
                                  :autocorr [0.5 0.3]
                                  :diversity [0.5 0.3]}
                   :edge-of-chaos {:entropy [0.6 0.2]
                                   :change [0.3 0.2]
                                   :autocorr [0.6 0.2]
                                   :diversity [0.4 0.2]}
                   :frozen-regime {:entropy [0.3 0.2]
                                   :change [0.1 0.1]
                                   :autocorr [0.8 0.15]
                                   :diversity [0.2 0.15]}
                   :chaotic {:entropy [0.9 0.1]
                             :change [0.8 0.15]
                             :autocorr [0.2 0.2]
                             :diversity [0.7 0.2]}}
        alt-results (into {}
                          (map (fn [[k spec]]
                                 [k (verifiers/evaluate-run trace spec)]))
                          alt-specs)
        ;; Compute per-dimension statistics over the trace
        named-values (mapv :named (remove nil? trace))
        dim-stats (into {}
                        (map (fn [dim-key]
                               (let [vals (keep dim-key named-values)
                                     n (count vals)]
                                 [dim-key
                                  (when (pos? n)
                                    {:mean (/ (reduce + 0.0 vals) n)
                                     :min (apply min vals)
                                     :max (apply max vals)
                                     :std (let [mean (/ (reduce + 0.0 vals) n)]
                                            (Math/sqrt (/ (reduce + 0.0 (map #(Math/pow (- % mean) 2) vals)) n)))
                                     :n n})])))
                        diag/diagnostic-keys)
        elapsed (/ (- (System/nanoTime) t0) 1e6)]
    {:id id
     :label label
     :generations (:generations config)
     :gen-length (count (:genotype config))
     :has-exotype (boolean (:exotype config))
     :dim-stats dim-stats
     :tai-zone-satisfaction (:satisfaction-vector verifier-result)
     :tai-zone-overall (:overall-satisfaction verifier-result)
     :alt-results (into {} (map (fn [[k v]] [k {:overall (:overall-satisfaction v)
                                                 :sat-vec (:satisfaction-vector v)}]))
                         alt-results)
     :best-fit-spec (first (sort-by (comp - :overall-satisfaction val) alt-results))
     :elapsed-ms elapsed
     :run-result run-result}))

;; =============================================================================
;; RENDERING
;; =============================================================================

(def out-dir "out/best-run-characterizations")

(defn ensure-dir! [dir]
  (.mkdirs (java.io.File. dir)))

(defn render-run! [{:keys [id run-result]}]
  (let [base (str out-dir "/" (name id))
        ppm-path (str base ".ppm")
        png-path (str base ".png")
        pixels (render/render-run run-result)]
    (render/write-ppm! ppm-path pixels :comment (name id))
    (let [result (clojure.java.shell/sh
                  "convert" ppm-path "-scale" "400%" "-interpolate" "Nearest"
                  "-filter" "point" png-path)]
      (when (zero? (:exit result))
        png-path))))

;; =============================================================================
;; REPORT
;; =============================================================================

(defn print-dim-bar [value width]
  (let [filled (int (* value width))
        empty (- width filled)]
    (str (apply str (repeat filled "█"))
         (apply str (repeat empty "░")))))

(defn print-characterization-report [results]
  (println "\n╔══════════════════════════════════════════════════════════════════╗")
  (println "║  CHARACTERIZATION OF KNOWN-GOOD HISTORICAL RUNS                ║")
  (println "║  Using TPG Diagnostic & Verifier Tools                         ║")
  (println "╚══════════════════════════════════════════════════════════════════╝")

  ;; Per-run detail
  (doseq [{:keys [id label dim-stats tai-zone-satisfaction tai-zone-overall
                  alt-results best-fit-spec has-exotype generations gen-length
                  elapsed-ms]} results]
    (println (str "\n─── " label " ───"))
    (printf "  Config: %d cells × %d gen%s | %.0fms\n"
            gen-length generations
            (if has-exotype " + exotype" "")
            elapsed-ms)

    (println "\n  Diagnostic Profile (mean ± std over trace):")
    (doseq [dim diag/diagnostic-keys]
      (when-let [{:keys [mean std min max]} (dim dim-stats)]
        (printf "    %-22s %s %.3f ± %.3f  [%.2f – %.2f]\n"
                (name dim)
                (print-dim-bar mean 20)
                mean std min max)))

    (println "\n  Verifier Satisfaction (tai-zone spec):")
    (printf "    Overall: %.3f\n" (double tai-zone-overall))
    (doseq [[k v] (sort-by key tai-zone-satisfaction)]
      (printf "    %-22s %s %.3f\n" (name k) (print-dim-bar v 20) (double v)))

    (println "\n  Best-fit verifier spec:")
    (let [[spec-name result] best-fit-spec]
      (printf "    %s → %.3f overall\n" (name spec-name) (double (:overall-satisfaction result))))

    (println "  All specs:")
    (doseq [[spec-name {:keys [overall]}] (sort-by (comp - :overall val) alt-results)]
      (printf "    %-22s %.3f %s\n" (name spec-name) (double overall) (print-dim-bar overall 15))))

  ;; Summary comparison table
  (println "\n══════════════════════════════════════════════════════════════════")
  (println "  SUMMARY TABLE")
  (println "══════════════════════════════════════════════════════════════════")
  (println)
  (printf "%-30s %6s %6s %6s %6s %6s %6s  %6s\n"
          "Run" "H" "Δ" "ρ" "σ" "φ" "λ" "Sat")
  (println (apply str (repeat 85 "-")))
  (doseq [{:keys [id dim-stats tai-zone-overall]} results]
    (printf "%-30s %6.3f %6.3f %6.3f %6.3f %6.3f %6.3f  %6.3f\n"
            (name id)
            (double (get-in dim-stats [:entropy :mean] 0))
            (double (get-in dim-stats [:change :mean] 0))
            (double (get-in dim-stats [:autocorr :mean] 0))
            (double (get-in dim-stats [:diversity :mean] 0))
            (double (get-in dim-stats [:phenotype-coupling :mean] 0))
            (double (get-in dim-stats [:damage-spread :mean] 0))
            (double tai-zone-overall)))
  (println (apply str (repeat 85 "-")))

  ;; Key observations
  (println "\n══════════════════════════════════════════════════════════════════")
  (println "  OBSERVATIONS")
  (println "══════════════════════════════════════════════════════════════════")

  ;; Find the best and worst on each dimension
  (doseq [dim diag/diagnostic-keys]
    (let [sorted (sort-by #(get-in % [:dim-stats dim :mean] 0) results)
          lowest (first sorted)
          highest (last sorted)]
      (printf "\n  %s:\n" (name dim))
      (printf "    Highest: %-25s (%.3f)\n"
              (name (:id highest))
              (double (get-in highest [:dim-stats dim :mean] 0)))
      (printf "    Lowest:  %-25s (%.3f)\n"
              (name (:id lowest))
              (double (get-in lowest [:dim-stats dim :mean] 0)))))

  ;; Satisfaction ranking
  (println "\n  Tai-zone satisfaction ranking:")
  (doseq [[rank r] (map-indexed vector (sort-by (comp - :tai-zone-overall) results))]
    (printf "    %d. %-30s %.3f\n" (inc rank) (name (:id r)) (double (:tai-zone-overall r))))

  ;; What the "good" signature looks like
  (let [top-3 (take 3 (sort-by (comp - :tai-zone-overall) results))
        avg-dims (into {}
                       (map (fn [dim]
                              [dim (/ (reduce + 0.0 (map #(get-in % [:dim-stats dim :mean] 0) top-3))
                                      (count top-3))]))
                       diag/diagnostic-keys)]
    (println "\n  'Good run' diagnostic signature (top-3 average):")
    (doseq [dim diag/diagnostic-keys]
      (printf "    %-22s %.3f %s\n"
              (name dim)
              (double (avg-dims dim))
              (print-dim-bar (avg-dims dim) 25)))
    (println)
    (println "  Proposed verifier spec (auto-fitted to top-3 runs):")
    (doseq [dim [:entropy :change :autocorr :diversity]]
      (let [vals (map #(get-in % [:dim-stats dim :mean] 0) top-3)
            center (/ (reduce + 0.0 vals) (count vals))
            spread (- (apply max vals) (apply min vals))
            width (max 0.1 (* 1.5 spread))]
        (printf "    %-22s [%.3f %.3f]   (center ± width)\n"
                (name dim) center width)))))

;; =============================================================================
;; MAIN
;; =============================================================================

(ensure-dir! out-dir)

(println "\nReplaying and characterizing known-good historical runs...")
(println "This uses the full MMCA runtime — may take a minute.\n")

(def results
  (mapv (fn [config]
          (let [result (characterize-run config)]
            ;; Render spacetime diagram
            (when-let [png (render-run! result)]
              (println (str "    → " png)))
            result))
        runs))

(print-characterization-report results)

(println "\n  Spacetime diagrams rendered to:" out-dir)
(println)
