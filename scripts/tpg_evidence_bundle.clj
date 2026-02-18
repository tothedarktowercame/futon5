#!/usr/bin/env bb
(ns tpg-evidence-bundle
  "Build a reproducible evidence bundle from local TPG evolution artifacts."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private default-in-dir "out/tpg-evo-production")
(def ^:private default-report "reports/evidence/2026-02-18-tpg-evo-repro-report.md")
(def ^:private default-summary-edn "reports/evidence/2026-02-18-tpg-evo-repro-summary.edn")
(def ^:private default-checksums "reports/evidence/2026-02-18-tpg-evo-repro-checksums.txt")

(defn- usage []
  (str/join
   "\n"
   ["Build a deterministic TPG evidence bundle from checkpoint artifacts."
    ""
    "Usage:"
    "  bb -cp src:resources scripts/tpg_evidence_bundle.clj [options]"
    ""
    "Options:"
    "  --in-dir PATH        Input directory (default out/tpg-evo-production)."
    "  --report PATH        Markdown report output path."
    "  --summary-edn PATH   EDN summary output path."
    "  --checksums PATH     SHA-256 checksum output path."
    "  --dry-run            Print summary to stdout without writing files."
    "  --help               Show this message."]))

(defn- parse-args [args]
  (loop [args args
         opts {:in-dir default-in-dir
               :report default-report
               :summary-edn default-summary-edn
               :checksums default-checksums
               :dry-run false}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (assoc opts :help true)

          (= "--in-dir" flag)
          (recur (rest more) (assoc opts :in-dir (first more)))

          (= "--report" flag)
          (recur (rest more) (assoc opts :report (first more)))

          (= "--summary-edn" flag)
          (recur (rest more) (assoc opts :summary-edn (first more)))

          (= "--checksums" flag)
          (recur (rest more) (assoc opts :checksums (first more)))

          (= "--dry-run" flag)
          (recur more (assoc opts :dry-run true))

          :else
          (recur more (assoc opts :unknown (str "Unknown option: " flag)))))
      opts)))

(defn- checkpoint-file?
  [^java.io.File f]
  (and (.isFile f)
       (re-matches #"checkpoint-gen-\d{4}\.edn" (.getName f))))

(defn- checkpoint-idx
  [^java.io.File f]
  (Long/parseLong (second (re-find #"checkpoint-gen-(\d{4})\.edn" (.getName f)))))

(defn- read-edn-file [^java.io.File f]
  (edn/read-string (slurp f)))

(defn- as-double
  [x]
  (double (or x 0.0)))

(defn- candidate-score
  [candidate]
  (as-double (:overall-satisfaction candidate)))

(defn- best-candidate
  [population]
  (when (seq population)
    (apply max-key candidate-score population)))

(defn- summarize-checkpoint
  [^java.io.File f]
  (let [data (read-edn-file f)
        history (vec (:history data))
        population (vec (:population data))
        last-h (last history)
        best (best-candidate population)]
    {:file (.getPath f)
     :generation (:generation data)
     :config-seed (:config-seed data)
     :history-count (count history)
     :history-first-generation (:generation (first history))
     :history-last-generation (:generation last-h)
     :last-best-overall (as-double (:best-overall last-h))
     :last-mean-overall (as-double (:mean-overall last-h))
     :population-size (count population)
     :checkpoint-best-id (:tpg/id best)
     :checkpoint-best-overall (candidate-score best)
     :checkpoint-best-team-count (count (:teams best))
     :checkpoint-best-operator-count (count (:operators best))
     :raw data}))

(defn- fmt-float
  [x]
  (format "%.12f" (as-double x)))

(defn- normalize-tpg
  [tpg]
  (select-keys tpg [:tpg/id :tpg/version :overall-satisfaction :satisfaction-vector
                    :config :temporal-schedule :teams :operators]))

(defn- digest-bytes
  [algo bytes]
  (let [md (java.security.MessageDigest/getInstance algo)]
    (.update md bytes)
    (.digest md)))

(defn- hex-lower [^bytes bs]
  (apply str (map #(format "%02x" (bit-and % 0xff)) bs)))

(defn- sha256-file
  [^java.io.File f]
  (hex-lower (digest-bytes "SHA-256" (java.nio.file.Files/readAllBytes (.toPath f)))))

(defn- strict-increasing?
  [xs]
  (every? true? (map < xs (rest xs))))

(defn- approx=
  [a b]
  (< (Math/abs (- (as-double a) (as-double b))) 1.0e-12))

(defn- build-checks
  [checkpoint-summaries best-tpg]
  (let [gens (mapv :generation checkpoint-summaries)
        seeds (set (map :config-seed checkpoint-summaries))
        history-aligned? (every? (fn [{:keys [generation history-last-generation]}]
                                   (= (dec generation) history-last-generation))
                                 checkpoint-summaries)
        pop-sizes (set (map :population-size checkpoint-summaries))
        all-candidates (mapcat (comp :population :raw) checkpoint-summaries)
        max-score (if (seq all-candidates)
                    (apply max (map candidate-score all-candidates))
                    0.0)
        max-ids (->> all-candidates
                     (filter #(approx= (candidate-score %) max-score))
                     (map :tpg/id)
                     set)
        best-id (:tpg/id best-tpg)
        best-score (candidate-score best-tpg)
        checks [{:name :has-checkpoints
                 :ok? (pos? (count checkpoint-summaries))
                 :detail (str "checkpoint-count=" (count checkpoint-summaries))}
                {:name :checkpoint-generations-strictly-increasing
                 :ok? (strict-increasing? gens)
                 :detail (pr-str gens)}
                {:name :checkpoint-seed-constant
                 :ok? (= 1 (count seeds))
                 :detail (pr-str seeds)}
                {:name :history-aligns-with-generation
                 :ok? history-aligned?
                 :detail "for each checkpoint: history-last-generation == generation-1"}
                {:name :population-size-constant
                 :ok? (= 1 (count pop-sizes))
                 :detail (pr-str pop-sizes)}
                {:name :best-tpg-overall-matches-global-max
                 :ok? (approx= best-score max-score)
                 :detail (str "best=" (fmt-float best-score) " max=" (fmt-float max-score))}
                {:name :best-tpg-id-appears-among-global-max-candidates
                 :ok? (contains? max-ids best-id)
                 :detail (str "best-id=" best-id " max-ids=" (pr-str (sort (map str max-ids))))}]]
    {:checks checks
     :all-ok? (every? :ok? checks)
     :max-score max-score
     :max-ids max-ids
     :all-candidates all-candidates}))

(defn- checkpoint-table
  [checkpoint-summaries]
  (str/join
   "\n"
   (concat
    ["| File | Gen | Hist Last | Hist Best | Hist Mean | Pop | Best ID | Best Overall |"
     "|---|---:|---:|---:|---:|---:|---|---:|"]
    (for [{:keys [file generation history-last-generation last-best-overall last-mean-overall
                  population-size checkpoint-best-id checkpoint-best-overall]} checkpoint-summaries]
      (format "| `%s` | %d | %d | %.6f | %.6f | %d | `%s` | %.6f |"
              (.getName (io/file file))
              generation
              history-last-generation
              (as-double last-best-overall)
              (as-double last-mean-overall)
              population-size
              (or checkpoint-best-id :none)
              (as-double checkpoint-best-overall))))))

(defn- checks-table
  [checks]
  (str/join
   "\n"
   (concat
    ["| Check | Status | Detail |"
     "|---|---|---|"]
    (for [{check-name :name :keys [ok? detail]} checks]
      (format "| `%s` | %s | %s |"
              (name check-name)
              (if ok? "PASS" "FAIL")
              (str/replace (str detail) "|" "\\|"))))))

(defn- report-markdown
  [{:keys [in-dir checkpoint-summaries best-tpg checks all-ok? max-score max-ids report summary-edn checksums]}]
  (str/join
   "\n"
   (concat
    ["# Evidence Report: TPG Evolution Repro Bundle"
     ""
     "Deterministic inspection of local TPG checkpoints and best-individual artifact."
     ""
     "## Command"
     ""
     (format "`bb -cp src:resources scripts/tpg_evidence_bundle.clj --in-dir %s --report %s --summary-edn %s --checksums %s`"
             in-dir report summary-edn checksums)
     ""
     "## Outcomes"
     ""
     (format "- Checkpoint files: %d" (count checkpoint-summaries))
     (format "- Global max overall-satisfaction: %.12f" (as-double max-score))
     (format "- Global max candidate IDs: `%s`"
             (if (seq max-ids)
               (str/join "`, `" (sort (map str max-ids)))
               "none"))
     (format "- Best artifact ID: `%s` (overall %.12f)"
             (:tpg/id best-tpg)
             (candidate-score best-tpg))
     (format "- All consistency checks pass: %s" all-ok?)
     ""
     "## Consistency Checks"
     ""
     (checks-table checks)
     ""
     "## Checkpoint Summary"
     ""
     (checkpoint-table checkpoint-summaries)
     ""
     "## Best Artifact"
     ""
     (format "- `:tpg/id`: `%s`" (:tpg/id best-tpg))
     (format "- `:overall-satisfaction`: %.12f" (candidate-score best-tpg))
     (format "- Teams: %d" (count (:teams best-tpg)))
     (format "- Operators: %d" (count (:operators best-tpg)))
     (format "- Schedule length: %d" (count (:temporal-schedule best-tpg)))
     ""
     "## Artifact Paths"
     ""
     (format "- `%s`" checksums)
     (format "- `%s`" summary-edn)
     (format "- `%s`" report)])))

(defn- ensure-parent! [path]
  (when-let [parent (.getParentFile (io/file path))]
    (.mkdirs parent)))

(defn- relative-to-cwd
  [^java.io.File f]
  (let [cwd (.toPath (io/file "."))
        path (.toPath f)]
    (str (.toString (.normalize (.relativize cwd path))))))

(defn- checksums-content
  [files]
  (str/join "\n"
            (for [^java.io.File f files]
              (format "%s  %s"
                      (sha256-file f)
                      (relative-to-cwd f)))))

(defn -main [& args]
  (let [{:keys [help unknown in-dir report summary-edn checksums dry-run]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do
                (binding [*out* *err*]
                  (println unknown)
                  (println)
                  (println (usage)))
                (System/exit 2))
      :else
      (let [in-dir-file (io/file in-dir)
            best-file (io/file in-dir-file "best-tpg.edn")
            checkpoints (->> (file-seq in-dir-file)
                             (filter checkpoint-file?)
                             (sort-by checkpoint-idx)
                             vec)]
        (when-not (.exists in-dir-file)
          (binding [*out* *err*]
            (println "Input directory does not exist:" in-dir))
          (System/exit 2))
        (when-not (.exists best-file)
          (binding [*out* *err*]
            (println "Missing required file:" (.getPath best-file)))
          (System/exit 2))
        (when (empty? checkpoints)
          (binding [*out* *err*]
            (println "No checkpoint files found in" in-dir))
          (System/exit 2))
        (let [checkpoint-summaries (mapv summarize-checkpoint checkpoints)
              best-tpg (normalize-tpg (read-edn-file best-file))
              {:keys [checks all-ok? max-score max-ids]} (build-checks checkpoint-summaries best-tpg)
              summary {:tool "scripts/tpg_evidence_bundle.clj"
                       :input-dir in-dir
                       :checkpoint-count (count checkpoint-summaries)
                       :checkpoint-files (mapv (comp #(.getName ^java.io.File %) io/file :file) checkpoint-summaries)
                       :global-max-overall-satisfaction (as-double max-score)
                       :global-max-candidate-ids (vec (sort (map str max-ids)))
                       :best-tpg/id (:tpg/id best-tpg)
                       :best-tpg/overall-satisfaction (candidate-score best-tpg)
                       :all-checks-passed? all-ok?
                       :checks checks
                       :checkpoints (mapv #(dissoc % :raw) checkpoint-summaries)}
              report-body (report-markdown {:in-dir in-dir
                                            :checkpoint-summaries checkpoint-summaries
                                            :best-tpg best-tpg
                                            :checks checks
                                            :all-ok? all-ok?
                                            :max-score max-score
                                            :max-ids max-ids
                                            :report report
                                            :summary-edn summary-edn
                                            :checksums checksums})
              checksum-files (vec (concat [best-file] checkpoints))
              checksum-body (str (checksums-content checksum-files) "\n")]
          (if dry-run
            (do
              (println "Dry run: no files written.")
              (println (pr-str summary)))
            (do
              (doseq [path [summary-edn report checksums]]
                (ensure-parent! path))
              (spit summary-edn (str (pr-str summary) "\n"))
              (spit report report-body)
              (spit checksums checksum-body)
              (println "Wrote" summary-edn)
              (println "Wrote" report)
              (println "Wrote" checksums)
              (when-not all-ok?
                (binding [*out* *err*]
                  (println "One or more consistency checks failed."))
                (System/exit 1)))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
