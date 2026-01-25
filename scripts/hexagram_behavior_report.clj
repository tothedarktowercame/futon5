#!/usr/bin/env clj -M
;; Generate hexagram behavior report from MMCA wiring runs
;;
;; Samples exotype contexts during runs, lifts to hexagrams via eigenvalue
;; diagonalization, and produces a markdown report.
;;
;; Usage: clj -M -e '(load-file "scripts/hexagram_behavior_report.clj")'

(ns hexagram-behavior-report
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [futon5.mmca.exotype :as exotype]
            [futon5.xenotype.interpret :as interpret]
            [futon5.xenotype.generator :as generator]
            [futon5.ca.core :as ca]))

(defn random-genotype [len seed]
  (let [rng (java.util.Random. seed)
        sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly len #(nth sigils (.nextInt rng (count sigils)))))))

(defn sample-hexagrams
  "Sample hexagrams from a run using the full exotype pipeline."
  [gen-history phe-history num-samples sample-seed]
  (let [rng (java.util.Random. sample-seed)]
    (for [_ (range num-samples)
          :let [ctx (exotype/sample-context gen-history phe-history rng)]
          :when ctx
          :let [physics (exotype/context->physics-family ctx)]
          :when physics]
      {:hexagram-id (:hexagram-id physics)
       :hexagram-name (:hexagram-name physics)
       :energy-key (:energy-key physics)
       :rule (:rule physics)
       :coord (:coord ctx)})))

(defn hexagram-distribution [samples]
  (let [ids (keep :hexagram-id samples)
        freqs (frequencies ids)
        total (count ids)
        sorted-freqs (sort-by (comp - val) freqs)]
    {:total total
     :unique (count freqs)
     :distribution freqs
     :sorted sorted-freqs
     :mode (when (seq freqs) (key (first sorted-freqs)))
     :top-10 (take 10 sorted-freqs)
     :entropy (when (pos? total)
                (- (reduce + (for [[_ cnt] freqs
                                   :let [p (/ (double cnt) total)]
                                   :when (pos? p)]
                               (* p (Math/log p))))))}))

(defn evolve-cell-with-wiring
  "Apply wiring diagram to a single cell, given its neighborhood context."
  [diagram pred-sigil self-sigil succ-sigil]
  (let [ctx {:pred (str pred-sigil)
             :self (str self-sigil)
             :succ (str succ-sigil)}
        result (interpret/evaluate-diagram (:diagram diagram) {:ctx ctx} generator/generator-registry)
        output-node (:output (:diagram diagram))
        output-value (get-in result [:node-values output-node :out])]
    (or output-value self-sigil)))

(defn evolve-genotype-with-wiring
  "Evolve entire genotype string using wiring diagram."
  [diagram genotype]
  (let [len (count genotype)
        chars (vec (seq genotype))]
    (apply str
           (for [i (range len)]
             (let [pred (get chars (mod (dec i) len))
                   self (get chars i)
                   succ (get chars (mod (inc i) len))]
               (evolve-cell-with-wiring diagram pred self succ))))))

(defn run-wiring-ca
  "Run a wiring diagram as a CA for multiple generations."
  [wiring genotype phenotype generations]
  (loop [gen-history [genotype]
         phe-history [phenotype]
         current genotype
         gen 0]
    (if (>= gen generations)
      {:gen-history gen-history
       :phe-history phe-history}
      (let [next-gen (evolve-genotype-with-wiring wiring current)]
        (recur (conj gen-history next-gen)
               (conj phe-history phenotype)  ; phenotype unchanged for now
               next-gen
               (inc gen))))))

(defn run-wiring [wiring-path seed generations width]
  (let [wiring (edn/read-string (slurp wiring-path))
        wiring-id (get-in wiring [:meta :id] :unknown)
        genotype (random-genotype width seed)
        phenotype (apply str (repeat width "0"))
        run (run-wiring-ca wiring genotype phenotype generations)]
    {:wiring-id wiring-id
     :wiring-path wiring-path
     :seed seed
     :generations (count (:gen-history run))
     :width width
     :gen-history (:gen-history run)
     :phe-history (:phe-history run)}))

(defn analyze-run [run-result num-samples]
  (let [{:keys [gen-history phe-history wiring-id seed]} run-result
        samples (sample-hexagrams gen-history phe-history num-samples 42)
        dist (hexagram-distribution samples)]
    (assoc run-result
           :samples samples
           :distribution dist)))

(defn format-hexagram-table [dist]
  (let [lines (for [[hex-id cnt] (:top-10 dist)]
                (let [pct (* 100.0 (/ cnt (:total dist)))]
                  (format "| %d | %d | %.1f%% |" hex-id cnt pct)))]
    (str "| Hexagram | Count | Percent |\n"
         "|----------|-------|--------|\n"
         (str/join "\n" lines))))

(defn generate-report [analyses output-path]
  (let [sb (StringBuilder.)
        timestamp (java.time.LocalDateTime/now)]

    (.append sb "# Hexagram Behavior Report\n\n")
    (.append sb (str "Generated: " timestamp "\n\n"))
    (.append sb "This report samples exotype contexts from MMCA wiring runs and lifts them to hexagrams\n")
    (.append sb "via eigenvalue diagonalization of the 36-bit context matrix.\n\n")
    (.append sb "---\n\n")

    ;; Summary table
    (.append sb "## Summary\n\n")
    (.append sb "| Wiring | Seed | Samples | Unique Hex | Mode | H-63 | H-64 | H-11 | Entropy |\n")
    (.append sb "|--------|------|---------|------------|------|------|------|------|--------|\n")
    (doseq [{:keys [wiring-id seed distribution]} analyses]
      (let [d distribution
            h63 (get (:distribution d) 63 0)
            h64 (get (:distribution d) 64 0)
            h11 (get (:distribution d) 11 0)]
        (.append sb (format "| %s | %d | %d | %d | #%s | %d | %d | %d | %.2f |\n"
                            (name wiring-id) seed (:total d) (:unique d)
                            (:mode d) h63 h64 h11 (or (:entropy d) 0.0)))))
    (.append sb "\n")

    ;; Notable hexagrams
    (.append sb "## Notable Hexagrams\n\n")
    (.append sb "| Hexagram | Name | Significance |\n")
    (.append sb "|----------|------|-------------|\n")
    (.append sb "| 11 | 泰 (Tai) | Peace/Prosperity - target for exotype EoC |\n")
    (.append sb "| 63 | 既濟 (Ji Ji) | Already Across - completion, Rule 90 connection |\n")
    (.append sb "| 64 | 未濟 (Wei Ji) | Not Yet Across - incompletion, Rule 90 connection |\n")
    (.append sb "| 2 | 坤 (Kun) | Receptive/Earth - pure yin |\n")
    (.append sb "| 23 | 剝 (Bo) | Splitting Apart - transition |\n\n")

    ;; Detailed results per wiring
    (.append sb "## Detailed Results\n\n")
    (doseq [{:keys [wiring-id wiring-path seed generations width distribution]} analyses]
      (.append sb (str "### " (name wiring-id) "\n\n"))
      (.append sb (str "- **Path**: `" wiring-path "`\n"))
      (.append sb (str "- **Seed**: " seed "\n"))
      (.append sb (str "- **Generations**: " generations "\n"))
      (.append sb (str "- **Width**: " width "\n"))
      (.append sb (str "- **Samples**: " (:total distribution) "\n"))
      (.append sb (str "- **Unique hexagrams**: " (:unique distribution) "\n"))
      (.append sb (str "- **Mode**: #" (:mode distribution) "\n"))
      (.append sb (str "- **Entropy**: " (format "%.3f" (or (:entropy distribution) 0.0)) "\n\n"))
      (.append sb "**Top 10 Hexagrams:**\n\n")
      (.append sb (format-hexagram-table distribution))
      (.append sb "\n\n"))

    ;; Methodology
    (.append sb "## Methodology\n\n")
    (.append sb "1. Run wiring through MMCA for N generations\n")
    (.append sb "2. Sample random (t, x) positions from run history\n")
    (.append sb "3. Extract 36-bit exotype context at each sample:\n")
    (.append sb "   - LEFT (8 bits): predecessor sigil\n")
    (.append sb "   - EGO (8 bits): self sigil\n")
    (.append sb "   - RIGHT (8 bits): successor sigil\n")
    (.append sb "   - NEXT (8 bits): next-generation sigil\n")
    (.append sb "   - PHENOTYPE (4 bits): phenotype family\n")
    (.append sb "4. Arrange into 6x6 matrix and compute eigenvalues\n")
    (.append sb "5. Signs of 6 eigenvalues → hexagram lines\n")
    (.append sb "6. Map to King Wen hexagram number\n\n")
    (.append sb "See `futon5.hexagram.lift` and `futon5.mmca.exotype` for implementation.\n")

    (spit output-path (str sb))
    (println "Report written to:" output-path)
    output-path))

(defn -main []
  (let [wirings [{:path "data/wiring-rules/rule-090.edn" :name "Rule 90"}
                 {:path "data/wiring-rules/rule-110.edn" :name "Rule 110"}
                 {:path "data/wiring-rules/rule-030.edn" :name "Rule 30"}
                 {:path "data/wiring-rules/rule-184.edn" :name "Rule 184"}
                 {:path "data/wiring-rules/rule-054.edn" :name "Rule 54"}
                 {:path "data/wiring-ladder/level-5-creative.edn" :name "L5-Creative"}]
        seed 352362012
        generations 100
        width 100
        num-samples 200

        _ (println "Generating hexagram behavior report...")
        _ (println "Wirings:" (count wirings))
        _ (println "Seed:" seed)
        _ (println "Generations:" generations)
        _ (println "Samples per run:" num-samples)
        _ (println)

        analyses (for [{:keys [path name]} wirings]
                   (do
                     (println "Running" name "...")
                     (-> (run-wiring path seed generations width)
                         (analyze-run num-samples))))

        output-path "reports/hexagram-behavior-report.md"]

    (generate-report analyses output-path)
    (println "\nDone!")))

(-main)
