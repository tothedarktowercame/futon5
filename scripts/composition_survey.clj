#!/usr/bin/env bb
;; Composition Survey — Do I Ching compositions produce different dynamics?
;;
;; Runs hexagram wirings individually and in temporal composition,
;; comparing SCI metrics to see if composition creates emergent behavior.
;;
;; I Ching composition theory: hexagrams are pairs of trigrams.
;; Complementary pairs (same trigrams reversed) should produce
;; structurally related but distinct dynamics.
;;
;; Temporal composition: run wiring A for N gens, then switch to B.
;; This maps to the I Ching's "changing lines" — a hexagram transitioning
;; to its complement.
;;
;; Usage:
;;   bb -cp src:resources scripts/composition_survey.clj

(require '[futon5.wiring.runtime :as wrt]
         '[futon5.mmca.metrics :as metrics]
         '[futon5.mmca.domain-analysis :as domain]
         '[futon5.mmca.wolfram-class :as wclass]
         '[futon5.mmca.bitplane-analysis :as bitplane]
         '[futon5.mmca.run-health :as health]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

;; =============================================================================
;; CONFIGURATION
;; =============================================================================

(def wirings-dir "resources/xenotype-wirings")

(def hexagrams
  "Selected hexagrams spanning the parameter space."
  [{:id "iching/hexagram-01-qian"   :name "乾 Creative"      :trigrams "☰☰"}
   {:id "iching/hexagram-02-kun"    :name "坤 Receptive"      :trigrams "☷☷"}
   {:id "iching/hexagram-11-tai"    :name "泰 Peace"          :trigrams "☷☰"}
   {:id "iching/hexagram-12-pi"     :name "否 Standstill"     :trigrams "☰☷"}
   {:id "iching/hexagram-29-kan"    :name "坎 Water"          :trigrams "☵☵"}
   {:id "iching/hexagram-30-li"     :name "離 Fire"           :trigrams "☲☲"}
   {:id "iching/hexagram-63-jiji"   :name "既濟 After"        :trigrams "☵☲"}
   {:id "iching/hexagram-64-weiji"  :name "未濟 Before"       :trigrams "☲☵"}])

(def compositions
  "Temporal compositions to test. Each is [A B label theory]."
  [;; Complement pairs (same trigrams reversed)
   ["iching/hexagram-11-tai"   "iching/hexagram-12-pi"    "泰→否"   "Peace→Standstill (trigram reversal)"]
   ["iching/hexagram-12-pi"    "iching/hexagram-11-tai"   "否→泰"   "Standstill→Peace (recovery)"]
   ["iching/hexagram-63-jiji"  "iching/hexagram-64-weiji" "既濟→未濟" "After→Before (completion dissolves)"]
   ["iching/hexagram-64-weiji" "iching/hexagram-63-jiji"  "未濟→既濟" "Before→After (natural progression)"]
   ;; Creative/Receptive transitions
   ["iching/hexagram-01-qian"  "iching/hexagram-11-tai"   "乾→泰"   "Creative→Peace (yang produces harmony)"]
   ["iching/hexagram-01-qian"  "iching/hexagram-02-kun"   "乾→坤"   "Creative→Receptive (full reversal)"]
   ;; Fire/Water interactions
   ["iching/hexagram-29-kan"   "iching/hexagram-30-li"    "坎→離"   "Water→Fire (opposite forces)"]
   ["iching/hexagram-30-li"    "iching/hexagram-29-kan"   "離→坎"   "Fire→Water (opposite forces)"]])

(def run-params
  {:width 120
   :generations 200
   :seed 352362012})

;; =============================================================================
;; EXECUTION
;; =============================================================================

(defn load-compiled-wiring [pattern-id]
  (let [safe-name (str/replace pattern-id "/" "-")
        path (str wirings-dir "/compiled-" safe-name ".edn")]
    (edn/read-string (slurp path))))

(defn run-single
  "Run a single wiring for all generations."
  [wiring {:keys [width generations seed]}]
  (let [genotype (wrt/random-genotype width seed)]
    (wrt/run-wiring {:wiring wiring
                     :genotype genotype
                     :generations generations
                     :collect-metrics? false})))

(defn run-temporal-composition
  "Run wiring A for half the generations, then wiring B for the other half."
  [wiring-a wiring-b {:keys [width generations seed]}]
  (let [genotype (wrt/random-genotype width seed)
        half (quot generations 2)
        ;; Phase 1: run A
        result-a (wrt/run-wiring {:wiring wiring-a
                                  :genotype genotype
                                  :generations half
                                  :collect-metrics? false})
        ;; Phase 2: run B starting from A's final state
        final-a (last (:gen-history result-a))
        result-b (wrt/run-wiring {:wiring wiring-b
                                  :genotype final-a
                                  :generations (- generations half)
                                  :collect-metrics? false})]
    ;; Concatenate histories
    {:gen-history (into (:gen-history result-a) (rest (:gen-history result-b)))
     :wiring-id :temporal-composition
     :generations generations
     :switch-point half}))

;; =============================================================================
;; ANALYSIS (lighter than full SCI survey — focus on discrimination)
;; =============================================================================

(defn analyze-run [run-result label]
  (let [history (:gen-history run-result)]
    (try
      (let [;; Basic metrics
            metrics-h (metrics/series-metrics-history history)
            interesting (metrics/interestingness metrics-h nil)
            compress (metrics/compressibility-metrics history)
            autocorr (metrics/autocorr-metrics history)

            ;; Domain analysis
            domain-result (domain/analyze-domain history)

            ;; Bitplane analysis (cheaper than particle/TE)
            bp-analyses (bitplane/analyze-all-bitplanes history)
            bp-summary (bitplane/aggregate-bitplane-metrics bp-analyses)
            coupling (bitplane/coupling-spectrum history {:temporal? false :spatial? true})

            ;; Wolfram class
            class-result (wclass/estimate-class run-result)

            ;; Health
            health-result (health/assess-health run-result)]

        {:label label
         ;; Basic
         :change-rate (or (:avg-change interesting) 0.0)
         :entropy-n (or (:avg-entropy-n interesting) 0.0)
         :temporal-ac (or (:temporal-autocorr autocorr) 0.0)
         :spatial-ac (or (:spatial-autocorr autocorr) 0.0)
         :lz78-ratio (or (:lz78-ratio compress) 0.0)
         ;; SCI
         :compression-cv (:cv (metrics/compression-variance history))
         :domain-fraction (:domain-fraction domain-result)
         :best-bp-df (:best-domain-fraction bp-summary)
         :class-iv-planes (or (:class-iv-plane-count bp-summary) 0)
         ;; Coupling
         :independence (:independence-score coupling)
         :mean-coupling (:mean-coupling coupling)
         :coupling-summary (:summary coupling)
         ;; Classification
         :wolfram-class (:class class-result)
         :class-confidence (:confidence class-result)
         :class-reasoning (:reasoning class-result)
         :health (:classification health-result)})

      (catch Exception e
        {:label label
         :error (str e)
         :wolfram-class :error
         :health :error}))))

;; =============================================================================
;; REPORT
;; =============================================================================

(defn fmt [x digits]
  (if (number? x) (format (str "%." digits "f") (double x)) (str x)))

(defn print-summary-row [{:keys [label change-rate entropy-n domain-fraction
                                  best-bp-df independence mean-coupling
                                  wolfram-class class-confidence health
                                  compression-cv class-iv-planes error]}]
  (if error
    (printf "  %-20s │ ERROR: %s\n" label error)
    (printf "  %-20s │ Δ=%s H=%s │ df=%s bp=%s │ ind=%s MI=%s │ cv=%s │ %s(%s) %s\n"
            label
            (fmt change-rate 3) (fmt entropy-n 3)
            (fmt domain-fraction 2) (fmt best-bp-df 2)
            (fmt independence 2) (fmt mean-coupling 4)
            (fmt compression-cv 2)
            (name (or wolfram-class :?)) (fmt class-confidence 2)
            (name (or health :?)))))

(defn print-comparison [singles compositions-results]
  (let [single-map (into {} (map (fn [r] [(:label r) r]) singles))]
    (println "\n╔═══════════════════════════════════════════════════════════════════════════════════════════╗")
    (println "║  COMPOSITION COMPARISON                                                                  ║")
    (println "╚═══════════════════════════════════════════════════════════════════════════════════════════╝")
    (println)
    (doseq [{:keys [label a-id b-id theory] :as comp-result} compositions-results]
      (let [a-result (get single-map a-id)
            b-result (get single-map b-id)
            c-result (:result comp-result)]
        (printf "\n  ─── %s (%s) ───\n" label theory)
        (printf "  %-20s │ class=%s  health=%-8s  Δ=%s  df=%s  cv=%s  ind=%s\n"
                (str "A: " a-id)
                (name (or (:wolfram-class a-result) :?))
                (name (or (:health a-result) :?))
                (fmt (:change-rate a-result) 3)
                (fmt (:domain-fraction a-result) 2)
                (fmt (:compression-cv a-result) 2)
                (fmt (:independence a-result) 2))
        (printf "  %-20s │ class=%s  health=%-8s  Δ=%s  df=%s  cv=%s  ind=%s\n"
                (str "B: " b-id)
                (name (or (:wolfram-class b-result) :?))
                (name (or (:health b-result) :?))
                (fmt (:change-rate b-result) 3)
                (fmt (:domain-fraction b-result) 2)
                (fmt (:compression-cv b-result) 2)
                (fmt (:independence b-result) 2))
        (printf "  %-20s │ class=%s  health=%-8s  Δ=%s  df=%s  cv=%s  ind=%s\n"
                (str "A→B: " label)
                (name (or (:wolfram-class c-result) :?))
                (name (or (:health c-result) :?))
                (fmt (:change-rate c-result) 3)
                (fmt (:domain-fraction c-result) 2)
                (fmt (:compression-cv c-result) 2)
                (fmt (:independence c-result) 2))
        ;; Verdict
        (let [a-class (:wolfram-class a-result)
              b-class (:wolfram-class b-result)
              c-class (:wolfram-class c-result)]
          (printf "  VERDICT: %s\n"
                  (cond
                    (= c-class :IV) "★ Composition achieves Class IV!"
                    (and (not= a-class :IV) (not= b-class :IV) (= c-class :IV))
                    "★★ EMERGENT Class IV from non-IV components!"
                    (= c-class a-class b-class) "Same class as both components"
                    (= c-class a-class) "Matches A (first phase dominates)"
                    (= c-class b-class) "Matches B (second phase dominates)"
                    :else (str "Novel class: " (name (or c-class :?))
                               " (neither A=" (name (or a-class :?))
                               " nor B=" (name (or b-class :?)) ")"))))))))

;; =============================================================================
;; MAIN
;; =============================================================================

(println "╔═══════════════════════════════════════════════════════════════════════════════════════════╗")
(println "║  COMPOSITION SURVEY — I Ching Temporal Composition                                      ║")
(println "║  Do hexagram compositions produce different dynamics than their components?              ║")
(println "╚═══════════════════════════════════════════════════════════════════════════════════════════╝")
(println)
(println (str "  Parameters: width=" (:width run-params)
              " generations=" (:generations run-params)
              " seed=" (:seed run-params)))
(println)

;; Phase 1: Run individual hexagrams
(println "Phase 1: Running individual hexagram wirings...")
(def single-results
  (mapv (fn [{:keys [id name trigrams]}]
          (printf "  [%s %s %s] " id name trigrams)
          (flush)
          (let [t0 (System/nanoTime)
                wiring (load-compiled-wiring id)
                result (run-single wiring run-params)
                analysis (analyze-run result id)
                elapsed (/ (- (System/nanoTime) t0) 1e6)]
            (printf "%.0fms → %s (%s)\n"
                    elapsed
                    (clojure.core/name (or (:wolfram-class analysis) :?))
                    (clojure.core/name (or (:health analysis) :?)))
            (flush)
            analysis))
        hexagrams))

;; Phase 2: Run temporal compositions
(println "\nPhase 2: Running temporal compositions (A for 100 gens → B for 100 gens)...")
(def composition-results
  (mapv (fn [[a-id b-id label theory]]
          (printf "  [%s] " label)
          (flush)
          (let [t0 (System/nanoTime)
                wiring-a (load-compiled-wiring a-id)
                wiring-b (load-compiled-wiring b-id)
                result (run-temporal-composition wiring-a wiring-b run-params)
                analysis (analyze-run result label)
                elapsed (/ (- (System/nanoTime) t0) 1e6)]
            (printf "%.0fms → %s (%s)\n"
                    elapsed
                    (clojure.core/name (or (:wolfram-class analysis) :?))
                    (clojure.core/name (or (:health analysis) :?)))
            (flush)
            {:label label
             :a-id a-id
             :b-id b-id
             :theory theory
             :result analysis}))
        compositions))

;; Phase 3: Report
(println "\n\n══════════════════════════════════════════════════════════════════════════════")
(println "INDIVIDUAL HEXAGRAM RESULTS")
(println "══════════════════════════════════════════════════════════════════════════════")
(println "  Legend: Δ=change-rate H=entropy df=domain-fraction bp=best-bitplane-df")
(println "         ind=independence MI=mean-coupling cv=compression-variance")
(println)
(doseq [r single-results]
  (print-summary-row r))

(println "\n══════════════════════════════════════════════════════════════════════════════")
(println "TEMPORAL COMPOSITION RESULTS")
(println "══════════════════════════════════════════════════════════════════════════════")
(println)
(doseq [{:keys [result]} composition-results]
  (print-summary-row result))

;; Comparison
(print-comparison single-results composition-results)

;; Summary
(println "\n══════════════════════════════════════════════════════════════════════════════")
(println "SUMMARY")
(println "══════════════════════════════════════════════════════════════════════════════")
(let [all-results (concat single-results (map :result composition-results))
      by-class (group-by :wolfram-class all-results)
      by-health (group-by :health all-results)]
  (println "\n  Wolfram class distribution:")
  (doseq [[cls results] (sort-by key by-class)]
    (printf "    Class %s: %d runs (%s)\n"
            (clojure.core/name (or cls :?))
            (count results)
            (str/join ", " (map :label results))))
  (println "\n  Health distribution:")
  (doseq [[h results] (sort-by key by-health)]
    (printf "    %-10s: %d runs\n" (clojure.core/name (or h :?)) (count results))))

(println "\n  Done.")
