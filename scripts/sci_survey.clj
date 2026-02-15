#!/usr/bin/env bb
;; SCI Survey — The Search for Computational Intelligence
;;
;; Runs all 5 known wiring rules through the complete SCI detection pipeline:
;;   1. Execute via wiring/runtime
;;   2. Compression variance (bursty complexity)
;;   3. Domain analysis (periodic background detection)
;;   4. Particle analysis (glider/defect detection)
;;   5. Info dynamics (transfer entropy, active information storage)
;;   6. Wolfram class estimation
;;   7. Extended diagnostics (6D + SCI dimensions)
;;
;; Also runs against champion MMCA run for comparison.
;;
;; Expected results:
;;   Rule 30  → Class III (chaotic)
;;   Rule 54  → Class II or IV (complex periodic)
;;   Rule 90  → Class II/III (linear, Sierpinski)
;;   Rule 110 → Class IV (Turing-complete, gliders)
;;   Rule 184 → Class II (traffic flow, periodic)
;;
;; Usage:
;;   bb -cp src:resources scripts/sci_survey.clj

(require '[futon5.wiring.runtime :as wrt]
         '[futon5.mmca.runtime :as runtime]
         '[futon5.mmca.metrics :as metrics]
         '[futon5.mmca.domain-analysis :as domain]
         '[futon5.mmca.particle-analysis :as particle]
         '[futon5.mmca.info-dynamics :as info]
         '[futon5.mmca.wolfram-class :as wclass]
         '[futon5.tpg.diagnostics :as diag]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

;; =============================================================================
;; CONFIGURATION
;; =============================================================================

(def wiring-rules
  "Known wiring rules with expected Wolfram classes."
  [{:id :rule-030 :path "data/wiring-rules/rule-030.edn" :expected-class :III}
   {:id :rule-054 :path "data/wiring-rules/rule-054.edn" :expected-class :II}
   {:id :rule-090 :path "data/wiring-rules/rule-090.edn" :expected-class :III}
   {:id :rule-110 :path "data/wiring-rules/rule-110.edn" :expected-class :IV}
   {:id :rule-184 :path "data/wiring-rules/rule-184.edn" :expected-class :II}])

(def run-params
  {:width 120
   :generations 200
   :seed 352362012})

(def champion-run
  "The champion MMCA run for comparison."
  {:id :champion-352362012
   :genotype "下为八尤午火大勿个土叫心五丸石扔巨刊认毛占术世日巴节土帅史忆击二亏风六力双毛厂无劝友风乎令兄心艺北刊白尤无劝只且飞日凡风布尸午田风及仓弓厂火书父车已飞一且付丸仅打从艺仪凡用电元井升犬刊刊支火不丸手白心二仗上乌由见元于刀允仔平一扔叮叫从大北印"
   :phenotype "000100000000101101110100111011111111111010101110000010000100011001110011110100000010110111010111000001011000110001000011"
   :generations 120
   :seed 352362012})

;; =============================================================================
;; EXECUTION
;; =============================================================================

(defn run-wiring-rule
  "Execute a wiring rule and return the history."
  [{:keys [path]} {:keys [width generations seed]}]
  (let [wiring (edn/read-string (slurp path))
        genotype (wrt/random-genotype width seed)]
    (println (str "    Executing " (get-in wiring [:meta :id])
                  " (" width "×" generations ")..."))
    (let [t0 (System/nanoTime)
          result (wrt/run-wiring {:wiring wiring
                                  :genotype genotype
                                  :generations generations
                                  :collect-metrics? false})
          elapsed (/ (- (System/nanoTime) t0) 1e6)]
      (println (format "    Done in %.0fms (%d generations)"
                       elapsed (count (:gen-history result))))
      result)))

(defn run-champion
  "Replay the champion MMCA run."
  [{:keys [genotype phenotype generations seed]}]
  (println (str "    Replaying champion (120×" generations ")..."))
  (let [t0 (System/nanoTime)
        result (runtime/run-mmca {:genotype genotype
                                  :phenotype phenotype
                                  :generations generations
                                  :seed seed
                                  :kernel :mutating-template
                                  :exotype-mode :local-physics})
        elapsed (/ (- (System/nanoTime) t0) 1e6)]
    (println (format "    Done in %.0fms" elapsed))
    result))

;; =============================================================================
;; ANALYSIS
;; =============================================================================

(defn analyze-run
  "Run the full SCI analysis pipeline on a history."
  [run-result label]
  (let [history (or (:gen-history run-result) (:phe-history run-result))
        t0 (System/nanoTime)]
    (println (str "    Analyzing " label "..."))

    ;; 1. Basic metrics
    (let [metrics-h (metrics/series-metrics-history history)
          interesting (metrics/interestingness metrics-h nil)
          compress (metrics/compressibility-metrics history)
          autocorr (metrics/autocorr-metrics history)

          ;; 2. Compression variance
          cv-result (metrics/compression-variance history)

          ;; 3. Domain analysis
          domain-result (domain/analyze-domain history)

          ;; 4. Particle analysis
          particle-result (particle/analyze-particles history)

          ;; 5. Info dynamics (TE summary — skip local TE grid for speed)
          te-result (info/te-summary history)
          ais-result (info/active-information-storage history)

          ;; 6. Wolfram class estimation
          class-result (wclass/estimate-class run-result)

          ;; 7. Diagnostic trace (summary stats)
          trace (diag/diagnostics-trace run-result)
          named-values (mapv :named (remove nil? trace))
          dim-means (into {}
                         (map (fn [k]
                                (let [vals (keep k named-values)
                                      n (count vals)]
                                  [k (if (pos? n)
                                       (/ (reduce + 0.0 vals) n)
                                       0.0)])))
                         diag/diagnostic-keys)

          elapsed (/ (- (System/nanoTime) t0) 1e6)]

      (println (format "    Analysis complete in %.0fms" elapsed))

      {:label label
       ;; Basic metrics
       :change-rate (or (:avg-change interesting) 0.0)
       :entropy-n (or (:avg-entropy-n interesting) 0.0)
       :temporal-autocorr (or (:temporal-autocorr autocorr) 0.0)
       :spatial-autocorr (or (:spatial-autocorr autocorr) 0.0)
       :diag-autocorr (or (:diag-autocorr autocorr) 0.0)
       :lz78-ratio (or (:lz78-ratio compress) 0.0)
       ;; SCI metrics
       :compression-cv (:cv cv-result)
       :domain-fraction (:domain-fraction domain-result)
       :domain-px (:px domain-result)
       :domain-pt (:pt domain-result)
       :class-iv-candidate? (:class-iv-candidate? domain-result)
       :particle-count (:particle-count particle-result)
       :species-count (:species-count particle-result)
       :max-lifetime (:max-lifetime particle-result)
       :mean-velocity (:mean-velocity particle-result)
       :interaction-count (:interaction-count particle-result)
       :mean-te (:mean-te te-result)
       :max-te (:max-te te-result)
       :te-variance (:te-variance te-result)
       :high-te-fraction (:high-te-fraction te-result)
       :info-transport-score (:information-transport-score te-result)
       :ais-mean (:mean ais-result)
       :ais-max (:max ais-result)
       ;; Wolfram class
       :wolfram-class (:class class-result)
       :class-confidence (:confidence class-result)
       :class-reasoning (:reasoning class-result)
       :class-scores (:scores class-result)
       ;; Diagnostic means
       :diag-means dim-means
       :elapsed-ms elapsed})))

;; =============================================================================
;; REPORT
;; =============================================================================

(defn fmt [x digits]
  (if (number? x)
    (format (str "%." digits "f") (double x))
    (str x)))

(defn print-rule-detail [{:keys [label] :as r} expected-class]
  (println (str "\n┌─── " label " ───"))
  (println (str "│  Expected: Class " (name expected-class)
                "  │  Detected: Class " (name (:wolfram-class r))
                "  │  Confidence: " (fmt (:class-confidence r) 3)
                (if (= expected-class (:wolfram-class r)) "  ✓" "  ✗")))
  (println (str "│  " (:class-reasoning r)))
  (println "│")
  (println "│  Basic Dynamics:")
  (printf  "│    change-rate:    %s    entropy-n:    %s    lz78-ratio: %s\n"
           (fmt (:change-rate r) 3) (fmt (:entropy-n r) 3) (fmt (:lz78-ratio r) 3))
  (printf  "│    temporal-ac:    %s    spatial-ac:   %s    diag-ac:    %s\n"
           (fmt (:temporal-autocorr r) 3) (fmt (:spatial-autocorr r) 3) (fmt (:diag-autocorr r) 3))
  (println "│")
  (println "│  SCI Metrics:")
  (printf  "│    compression-cv: %s    domain-frac:  %s    domain: %dx%d  class-iv? %s\n"
           (fmt (:compression-cv r) 3) (fmt (:domain-fraction r) 3)
           (:domain-px r) (:domain-pt r) (:class-iv-candidate? r))
  (printf  "│    particles:      %-5d  species:      %-5d  max-life: %-5d  interactions: %d\n"
           (:particle-count r) (:species-count r) (:max-lifetime r) (:interaction-count r))
  (printf  "│    mean-velocity:  %s\n" (fmt (:mean-velocity r) 3))
  (println "│")
  (println "│  Information Dynamics:")
  (printf  "│    mean-TE:        %s    max-TE:       %s    TE-var:     %s\n"
           (fmt (:mean-te r) 4) (fmt (:max-te r) 4) (fmt (:te-variance r) 4))
  (printf  "│    high-TE-frac:   %s    info-transport: %s\n"
           (fmt (:high-te-fraction r) 3) (fmt (:info-transport-score r) 3))
  (printf  "│    AIS-mean:       %s    AIS-max:      %s\n"
           (fmt (:ais-mean r) 4) (fmt (:ais-max r) 4))
  (println "│")
  (println "│  Class Scores:  "
           (str/join "  "
                     (map (fn [[k v]] (str (name k) "=" (fmt v 3)))
                          (sort-by key (:class-scores r)))))
  (println "└──────────────────────────────────────────────────────────────"))

(defn print-summary-table [results]
  (println "\n╔══════════════════════════════════════════════════════════════════════════════════════╗")
  (println "║  SCI SURVEY: SUMMARY TABLE                                                         ║")
  (println "╚══════════════════════════════════════════════════════════════════════════════════════╝")
  (println)
  (printf "%-12s │ %8s │ %5s %5s %5s │ %5s %5s │ %5s %5s %5s │ %4s %3s │ %s\n"
          "Rule" "Expected" "Δ" "H" "CV" "DomF" "P×T" "Part" "Spe" "Life" "Cls" "Cnf" "Match?")
  (println (apply str (repeat 105 "─")))
  (doseq [{:keys [label expected-class result]} results]
    (let [r result]
      (printf "%-12s │ %8s │ %5s %5s %5s │ %5s %2d×%-2d │ %5d %5d %5d │ %4s %3s │ %s\n"
              label
              (name expected-class)
              (fmt (:change-rate r) 2)
              (fmt (:entropy-n r) 2)
              (fmt (:compression-cv r) 2)
              (fmt (:domain-fraction r) 2)
              (:domain-px r) (:domain-pt r)
              (:particle-count r) (:species-count r) (:max-lifetime r)
              (name (:wolfram-class r))
              (fmt (:class-confidence r) 2)
              (if (= expected-class (:wolfram-class r)) "✓" "✗"))))
  (println (apply str (repeat 105 "─"))))

(defn print-class-iv-analysis [results]
  (println "\n╔══════════════════════════════════════════════════════════════════════════════════════╗")
  (println "║  CLASS IV DETECTION ANALYSIS                                                       ║")
  (println "╚══════════════════════════════════════════════════════════════════════════════════════╝")
  (println)

  ;; Which rules were classified as Class IV?
  (let [class-iv-results (filter #(= :IV (:wolfram-class (:result %))) results)
        class-iv-candidates (filter #(:class-iv-candidate? (:result %)) results)]
    (println "  Rules classified as Class IV:")
    (if (seq class-iv-results)
      (doseq [{:keys [label result]} class-iv-results]
        (printf "    %-12s  confidence=%.3f  particles=%d  species=%d  info-transport=%.3f\n"
                label (:class-confidence result) (:particle-count result)
                (:species-count result) (:info-transport-score result)))
      (println "    (none)"))

    (println "\n  Domain-fraction Class IV candidates (0.5 ≤ df ≤ 0.95):")
    (if (seq class-iv-candidates)
      (doseq [{:keys [label result]} class-iv-candidates]
        (printf "    %-12s  domain-frac=%.3f  particles=%d\n"
                label (:domain-fraction result) (:particle-count result)))
      (println "    (none)"))

    ;; Key question: can we distinguish Rule 110 from Rule 30?
    (println "\n  Rule 110 vs Rule 30 (the key SCI discrimination):")
    (let [r110 (first (filter #(= :rule-110 (:id %)) results))
          r030 (first (filter #(= :rule-030 (:id %)) results))]
      (when (and r110 r030)
        (let [a (:result r110) b (:result r030)]
          (printf "                   %12s  %12s  %s\n" "Rule 110" "Rule 30" "Discriminates?")
          (printf "    domain-frac:   %12s  %12s  %s\n"
                  (fmt (:domain-fraction a) 3) (fmt (:domain-fraction b) 3)
                  (if (> (:domain-fraction a) (:domain-fraction b)) "✓ 110>30" "—"))
          (printf "    compr-cv:      %12s  %12s  %s\n"
                  (fmt (:compression-cv a) 3) (fmt (:compression-cv b) 3)
                  (if (> (:compression-cv a) (:compression-cv b)) "✓ 110>30" "—"))
          (printf "    particles:     %12d  %12d  %s\n"
                  (:particle-count a) (:particle-count b)
                  (if (> (:particle-count a) (:particle-count b)) "✓ 110>30" "—"))
          (printf "    species:       %12d  %12d  %s\n"
                  (:species-count a) (:species-count b)
                  (if (> (:species-count a) (:species-count b)) "✓ 110>30" "—"))
          (printf "    max-lifetime:  %12d  %12d  %s\n"
                  (:max-lifetime a) (:max-lifetime b)
                  (if (> (:max-lifetime a) (:max-lifetime b)) "✓ 110>30" "—"))
          (printf "    info-transport:%12s  %12s  %s\n"
                  (fmt (:info-transport-score a) 3) (fmt (:info-transport-score b) 3)
                  (if (> (:info-transport-score a) (:info-transport-score b)) "✓ 110>30" "—"))
          (printf "    TE-variance:   %12s  %12s  %s\n"
                  (fmt (:te-variance a) 4) (fmt (:te-variance b) 4)
                  (if (> (:te-variance a) (:te-variance b)) "✓ 110>30" "—")))))))

;; =============================================================================
;; MAIN
;; =============================================================================

(println "╔══════════════════════════════════════════════════════════════════════════════════════╗")
(println "║  THE SEARCH FOR COMPUTATIONAL INTELLIGENCE — SCI SURVEY                            ║")
(println "║  Running 5 wiring rules + champion through the full SCI pipeline                   ║")
(println "╚══════════════════════════════════════════════════════════════════════════════════════╝")
(println)
(println (str "  Parameters: width=" (:width run-params)
              " generations=" (:generations run-params)
              " seed=" (:seed run-params)))
(println)

;; Run wiring rules
(println "Phase 1: Executing wiring rules...")
(def wiring-results
  (mapv (fn [{:keys [id path expected-class] :as rule}]
          (println (str "\n  [" (name id) "]"))
          (let [run-result (run-wiring-rule rule run-params)
                analysis (analyze-run run-result (name id))]
            {:id id
             :label (name id)
             :expected-class expected-class
             :result analysis}))
        wiring-rules))

;; Run champion
(println "\n\nPhase 2: Running champion MMCA...")
(println "\n  [champion]")
(def champion-result
  (let [run-result (run-champion champion-run)
        analysis (analyze-run run-result "champion")]
    {:id :champion
     :label "champion"
     :expected-class :II  ;; MMCA champion is typically periodic/structured
     :result analysis}))

(def all-results (conj wiring-results champion-result))

;; Print detailed reports
(println "\n\nPhase 3: Results")
(doseq [{:keys [expected-class result]} all-results]
  (print-rule-detail result expected-class))

;; Summary table
(print-summary-table all-results)

;; Class IV analysis
(print-class-iv-analysis all-results)

;; Score card
(println "\n╔══════════════════════════════════════════════════════════════════════════════════════╗")
(println "║  SCORECARD                                                                         ║")
(println "╚══════════════════════════════════════════════════════════════════════════════════════╝")
(let [correct (count (filter #(= (:expected-class %) (:wolfram-class (:result %))) all-results))
      total (count all-results)]
  (printf "\n  Classification accuracy: %d/%d (%.0f%%)\n" correct total (* 100.0 (/ correct total)))
  (println)
  (doseq [{:keys [label expected-class result]} all-results]
    (let [match? (= expected-class (:wolfram-class result))]
      (printf "    %-15s  expected=%s  detected=%s  %s\n"
              label
              (name expected-class)
              (name (:wolfram-class result))
              (if match? "✓" (str "✗ (scores: " (str/join " " (map (fn [[k v]] (str (name k) "=" (fmt v 2))) (sort-by key (:class-scores result)))) ")"))))))

(println "\n  Done.")
