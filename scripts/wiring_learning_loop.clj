(ns wiring-learning-loop
  "The learning loop: connect wiring features to health outcomes.

   This script:
   1. Loads all wirings and extracts structural features
   2. Loads all health reports (outcomes)
   3. Joins features to outcomes
   4. Analyzes correlations
   5. Suggests what features might help

   Usage:
     bb -cp src:resources scripts/wiring_learning_loop.clj [options]"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.wiring.features :as features]))

(defn- usage []
  (str/join
   "\n"
   ["Wiring Learning Loop: correlate features with outcomes"
    ""
    "Usage:"
    "  bb -cp src:resources scripts/wiring_learning_loop.clj [options]"
    ""
    "Options:"
    "  --wirings-dir PATH   Directory with wiring EDN files (default: data/)"
    "  --health-dir PATH    Directory with health CSVs (default: reports/health/)"
    "  --outcomes PATH      Wiring outcomes EDN (default: reports/wirings/wiring-outcomes.edn)"
    "  --out PATH           Output analysis EDN"
    "  --markdown PATH      Output Markdown report"
    "  --help               Show this message"]))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag) (recur nil (assoc opts :help true))
          (= "--wirings-dir" flag) (recur (rest more) (assoc opts :wirings-dir (first more)))
          (= "--health-dir" flag) (recur (rest more) (assoc opts :health-dir (first more)))
          (= "--outcomes" flag) (recur (rest more) (assoc opts :outcomes (first more)))
          (= "--out" flag) (recur (rest more) (assoc opts :out (first more)))
          (= "--markdown" flag) (recur (rest more) (assoc opts :markdown (first more)))
          :else (recur more (assoc opts :unknown flag))))
      opts)))

;; === Collect Wirings ===

(defn find-wiring-files
  "Find all wiring EDN files in a directory tree."
  [root]
  (->> (file-seq (io/file root))
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".edn"))
       (filter #(or (str/includes? (.getPath %) "wiring")
                    (str/includes? (.getPath %) "ladder")))
       (remove #(str/includes? (.getName %) "experiment"))
       (remove #(str/includes? (.getName %) "outcomes"))))

(defn collect-wiring-features
  "Collect features from all wirings.
   Scans both the given root and resources/xenotype-wirings/."
  [root]
  (let [data-files (find-wiring-files root)
        resource-files (when (.exists (io/file "resources/xenotype-wirings"))
                         (->> (file-seq (io/file "resources/xenotype-wirings"))
                              (filter #(.isFile %))
                              (filter #(str/ends-with? (.getName %) ".edn"))))
        all-files (concat data-files resource-files)]
    (->> all-files
         (map #(.getPath %))
         (map features/extract-features-from-file)
         (filter some?)
         vec)))

;; === Collect Outcomes ===

(defn parse-csv-line
  "Parse a CSV line into a map using headers."
  [headers line]
  (let [values (str/split line #",")]
    (zipmap headers values)))

(defn load-health-csv
  "Load a health CSV file."
  [path]
  (let [lines (str/split-lines (slurp path))
        headers (mapv keyword (str/split (first lines) #","))
        data-lines (rest lines)]
    (mapv #(parse-csv-line headers %) data-lines)))

(defn collect-health-outcomes
  "Collect all health outcomes from CSV files."
  [health-dir]
  (->> (file-seq (io/file health-dir))
       (filter #(str/ends-with? (.getName %) ".csv"))
       (mapcat #(load-health-csv (.getPath %)))
       vec))

;; === Join Features to Outcomes ===

(defn extract-wiring-id-from-filename
  "Try to extract wiring ID from a run filename.
   Maps run output filenames to actual wiring IDs."
  [filename]
  (cond
    ;; Hexagram wirings (hex-01, hex-1, etc.)
    (re-find #"hex-0*([0-9]+)" filename)
    (let [[_ num] (re-find #"hex-0*([0-9]+)" filename)
          n (Integer/parseInt num)]
      (keyword (str "hex-" n)))
    ;; Wiring ladder levels
    (str/includes? filename "level-0") :level-0-baseline
    (str/includes? filename "level-1") :level-1-legacy
    (str/includes? filename "level-2") :level-2-context
    (str/includes? filename "level-3") :level-3-diversity
    (str/includes? filename "level-4") :level-4-gate
    (str/includes? filename "level-5") :level-5-creative
    ;; Prototype wirings (file is prototype-001-creative-peng.edn but ID is :xenotype-001)
    (str/includes? filename "prototype-001") :xenotype-001
    (str/includes? filename "prototype-038") :xenotype-038
    (str/includes? filename "prototype-071") :xenotype-071
    ;; Named wirings
    (str/includes? filename "boundary-guardian") :boundary-guardian-001
    (str/includes? filename "hybrid-prototype") :hybrid-prototype-001-gong
    ;; Legacy/baseline runs (use legacy kernel, map to legacy-baseline wiring)
    (str/includes? filename "legacy") :legacy-baseline
    (str/includes? filename "baseline") :legacy-baseline
    (str/includes? filename "perturb") :legacy-baseline  ; parameter variations
    :else :unknown))

(defn join-features-to-outcomes
  "Join wiring features to health outcomes."
  [features outcomes]
  (let [feature-map (into {} (map (juxt :id identity) features))]
    (for [outcome outcomes
          :let [wiring-id (extract-wiring-id-from-filename (:file outcome))
                feats (get feature-map wiring-id)]]
      (merge outcome
             {:wiring-id wiring-id
              :wiring-features feats}))))

;; === Analysis ===

(defn group-by-classification
  "Group joined data by health classification."
  [joined]
  (group-by :classification joined))

(defn feature-means-by-classification
  "Calculate mean feature values for each classification."
  [joined feature-keys]
  (let [by-class (group-by-classification joined)]
    (into {}
          (for [[class runs] by-class]
            [class
             (into {}
                   (for [fk feature-keys
                         :let [vals (->> runs
                                         (map #(get-in % [:wiring-features fk]))
                                         (filter number?))]]
                     [fk (when (seq vals)
                           (double (/ (reduce + vals) (count vals))))]))]))))

(defn classification-score
  "Score a classification (higher = better)."
  [class]
  (case (keyword class)
    :eoc 100
    :cooling 50
    :barcode 10
    :hot 5
    :frozen 5
    :collapsed-white 0
    :collapsed-black 0
    0))

(defn feature-correlation
  "Calculate correlation between a feature and outcome score.
   Returns {:feature :correlation :n}."
  [joined feature-key]
  (let [pairs (->> joined
                   (map (fn [r]
                          [(get-in r [:wiring-features feature-key])
                           (classification-score (:classification r))]))
                   (filter #(and (number? (first %)) (number? (second %)))))]
    (when (>= (count pairs) 3)
      (let [xs (map first pairs)
            ys (map second pairs)
            n (count pairs)
            mean-x (/ (reduce + xs) n)
            mean-y (/ (reduce + ys) n)
            cov (/ (reduce + (map #(* (- %1 mean-x) (- %2 mean-y)) xs ys)) n)
            std-x (Math/sqrt (/ (reduce + (map #(Math/pow (- % mean-x) 2) xs)) n))
            std-y (Math/sqrt (/ (reduce + (map #(Math/pow (- % mean-y) 2) ys)) n))]
        (when (and (pos? std-x) (pos? std-y))
          {:feature feature-key
           :correlation (double (/ cov (* std-x std-y)))
           :n n})))))

(defn analyze-correlations
  "Analyze correlations between all features and outcomes."
  [joined]
  (let [base-keys [:node-count :edge-count :complexity
                   :gate-nodes :legacy-nodes :creative-nodes :diversity-nodes
                   :gate-ratio :creative-ratio
                   :has-legacy? :has-gates? :has-creative? :has-diversity?]
        trait-keys (->> joined
                        (mapcat #(keys (:wiring-features %)))
                        (filter #(= "trait" (namespace %)))
                        distinct
                        sort)
        feature-keys (concat base-keys trait-keys)]
    (->> feature-keys
         (map #(feature-correlation joined %))
         (filter some?)
         (sort-by :correlation >))))

(defn- trait-coverage
  [features]
  (let [trait-counts (->> features
                          (map :trait-counts)
                          (remove nil?))
        traits (->> trait-counts
                    (mapcat keys)
                    distinct
                    sort)]
    (for [trait traits
          :let [counts (map #(get % trait 0) trait-counts)
                ratios (map #(get % trait 0.0) (map :trait-ratios features))
                avg-count (when (seq counts)
                            (/ (reduce + counts) (double (count counts))))
                avg-ratio (when (seq ratios)
                            (/ (reduce + ratios) (double (count ratios))))]]
      {:trait trait
       :avg-count avg-count
       :avg-ratio avg-ratio})))

;; === Suggestions ===

(defn generate-suggestions
  "Generate suggestions based on analysis."
  [correlations by-class]
  (let [positive-corr (filter #(> (:correlation %) 0.1) correlations)
        negative-corr (filter #(< (:correlation %) -0.1) correlations)
        eoc-count (count (get by-class "eoc" []))
        cooling-count (count (get by-class "cooling" []))
        barcode-count (count (get by-class "barcode" []))]
    {:summary
     {:total-runs (reduce + (map count (vals by-class)))
      :eoc-runs eoc-count
      :cooling-runs cooling-count
      :barcode-runs barcode-count
      :best-classification (if (pos? eoc-count) :eoc
                               (if (pos? cooling-count) :cooling :barcode))}

     :feature-insights
     (vec
      (concat
       (for [{:keys [feature correlation]} positive-corr]
         {:type :positive
          :feature feature
          :insight (format "%s correlates positively with better outcomes (r=%.2f)"
                           (name feature) correlation)})
       (for [{:keys [feature correlation]} negative-corr]
         {:type :negative
          :feature feature
          :insight (format "%s correlates negatively with better outcomes (r=%.2f)"
                           (name feature) correlation)})))

     :recommendations
     (vec
      (concat
       (when (zero? eoc-count)
         [{:priority :high
           :action "No EoC runs found - try wider grids, more seeds, or perturbation injection"}])
       (for [{:keys [feature]} (take 3 positive-corr)]
         {:priority :medium
          :action (format "Try wirings with more %s" (name feature))})
       (for [{:keys [feature]} (take 2 negative-corr)]
         {:priority :low
          :action (format "Try wirings with less %s" (name feature))})))}))

;; === Output ===

(defn markdown-report
  [features _outcomes joined correlations suggestions]
  (str/join
   "\n"
   (concat
    ["# Wiring Learning Loop Analysis"
     ""
     (format "Date: %s" (java.time.LocalDate/now))
     ""
     "## Data Summary"
     ""
     (format "- **Wirings analyzed**: %d" (count features))
     (format "- **Outcomes collected**: %d" (count _outcomes))
     (format "- **Joined records**: %d" (count joined))
     ""
     "## Classification Distribution"
     ""
     "| Classification | Count | % |"
     "|----------------|-------|---|"]

    (let [by-class (group-by-classification joined)
          total (count joined)]
      (for [[class runs] (sort-by #(- (classification-score (first %))) by-class)]
        (format "| %s | %d | %.1f%% |"
                class (count runs)
                (* 100.0 (/ (count runs) total)))))

    [""
     "## Feature Correlations with Outcome Quality"
     ""
     "| Feature | Correlation | N |"
     "|---------|-------------|---|"]

    (for [{:keys [feature correlation n]} correlations]
      (format "| %s | %.3f | %d |" (name feature) correlation n))

    [""
     "## Trait Coverage"
     ""
     "| Trait | Avg Nodes | Avg Ratio |"
     "|-------|-----------|-----------|"]

    (let [summary (trait-coverage features)]
      (if (seq summary)
        (for [{:keys [trait avg-count avg-ratio]} summary]
          (format "| %s | %.2f | %.3f |" (name trait) (double avg-count) (double avg-ratio)))
        ["| (none) | 0 | 0.000 |"]))

    [""
     "## Insights"
     ""]

    (for [{:keys [type feature insight]} (:feature-insights suggestions)]
      (format "- **%s** %s: %s" (if (= type :positive) "+" "-") (name feature) insight))

    [""
     "## Recommendations"
     ""]

    (for [{:keys [priority action]} (:recommendations suggestions)]
      (format "- [%s] %s" (name priority) action))

    [""
     "## Next Steps"
     ""
     "1. Run more experiments with varied wirings"
     "2. Populate wiring-outcomes.edn with results"
     "3. Re-run this analysis to refine insights"
     ""])))

;; === Main ===

(defn -main [& args]
  (let [{:keys [help unknown wirings-dir health-dir out markdown]}
        (parse-args args)
        wirings-dir (or wirings-dir "data")
        health-dir (or health-dir "reports/health")]
    (cond
      help
      (println (usage))

      unknown
      (do (println "Unknown option:" unknown)
          (println)
          (println (usage)))

      :else
      (do
        (println "=== Wiring Learning Loop ===")
        (println)

        ;; Collect data
        (println "Collecting wiring features from" wirings-dir "...")
        (let [features (collect-wiring-features wirings-dir)]
          (println (format "  Found %d wirings" (count features)))

          (println "Collecting health outcomes from" health-dir "...")
          (let [outcomes (collect-health-outcomes health-dir)]
            (println (format "  Found %d outcomes" (count outcomes)))

            ;; Join and analyze
            (println "Joining features to outcomes...")
            (let [joined (join-features-to-outcomes features outcomes)]
              (println (format "  Joined %d records" (count joined)))

              (println "Analyzing correlations...")
              (let [correlations (analyze-correlations joined)
                    by-class (group-by-classification joined)
                    suggestions (generate-suggestions correlations by-class)]

                ;; Print summary
                (println)
                (println "=== Results ===")
                (println)
                (println "Classification distribution:")
                (doseq [[class runs] (sort-by #(- (classification-score (first %))) by-class)]
                  (println (format "  %s: %d runs" class (count runs))))

                (println)
                (println "Top feature correlations:")
                (doseq [{:keys [feature correlation]} (take 5 correlations)]
                  (println (format "  %s: %.3f" (name feature) correlation)))

                (println)
                (println "Recommendations:")
                (doseq [{:keys [action]} (:recommendations suggestions)]
                  (println (format "  - %s" action)))

                ;; Output files
                (when out
                  (spit out (pr-str {:features features
                                     :outcomes outcomes
                                     :correlations correlations
                                     :suggestions suggestions}))
                  (println)
                  (println "Wrote EDN:" out))

                (when markdown
                  (spit markdown (markdown-report features outcomes joined correlations suggestions))
                  (println "Wrote Markdown:" markdown))))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
