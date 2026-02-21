(ns scripts.tensor-trace-report
  "Generate one end-to-end traceability report for tensor closed-loop output."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ct.tensor :as tensor]
            [futon5.ct.tensor-transfer :as transfer]
            [futon5.mmca.meta-lift :as meta-lift]))

(def ^:private default-rank 1)

(defn- usage []
  (str/join
   "\n"
   ["usage: bb -m scripts.tensor-trace-report [options]"
    ""
    "Options:"
    "  --report PATH         Tensor report EDN (required)."
    "  --out PATH            Markdown output path."
    "  --seed N              Select run by seed."
    "  --rank N              Select run by 1-based rank (default 1)."
    "  --visual-dir PATH     Optional visual artifact dir for links."
    "  --preview N           Preview width for long rows (default 48)."
    "  --help                Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (case flag
          "--report" (recur (rest more) (assoc opts :report (first more)))
          "--out" (recur (rest more) (assoc opts :out (first more)))
          "--seed" (recur (rest more) (assoc opts :seed (parse-int (first more))))
          "--rank" (recur (rest more) (assoc opts :rank (parse-int (first more))))
          "--visual-dir" (recur (rest more) (assoc opts :visual-dir (first more)))
          "--preview" (recur (rest more) (assoc opts :preview (parse-int (first more))))
          "--help" (recur more (assoc opts :help true))
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- ensure-parent! [path]
  (when-let [p (.getParentFile (io/file path))]
    (.mkdirs p))
  path)

(defn- now-iso []
  (str (java.time.Instant/now)))

(defn- preview-text [n s]
  (let [s (str (or s ""))]
    (if (<= (count s) n)
      s
      (str (subs s 0 n) "..."))))

(defn- md-escape [s]
  (-> (str s)
      (str/replace "\\" "\\\\")
      (str/replace "|" "\\|")
      (str/replace "\n" " ")))

(defn- map-preview [m keys-of-interest]
  (let [m (or m {})
        picked (if (seq keys-of-interest)
                 (select-keys m keys-of-interest)
                 m)]
    (pr-str picked)))

(defn- row-shape [row]
  (str (count (str row)) " chars"))

(defn- tensor-shape [t]
  (let [w (count t)
        d (if (seq t) (count (first t)) 0)]
    (str w "x" d)))

(defn- bitplane-shape [b]
  (let [p (count b)
        w (if (seq b) (count (first b)) 0)]
    (str p "x" w)))

(defn- find-entry
  [ranked {:keys [seed rank]}]
  (let [ranked (vec ranked)]
    (if (seq ranked)
      (if seed
        (if-let [idx (first (keep-indexed (fn [i e] (when (= seed (:seed e)) i)) ranked))]
          {:entry (nth ranked idx)
           :rank (inc idx)}
          (throw (ex-info "Seed not found in :ranked" {:seed seed})))
        (let [idx (dec (max 1 (long (or rank default-rank))))]
          (when (>= idx (count ranked))
            (throw (ex-info "Rank out of bounds" {:rank (inc idx)
                                                  :ranked-count (count ranked)})))
          {:entry (nth ranked idx)
           :rank (inc idx)}))
      (throw (ex-info "Report :ranked is empty" {})))))

(defn- run-index-for-seed
  [report-seed detail seed]
  (or (some-> detail :run/id long dec)
      (when (and (number? report-seed) (number? seed))
        (let [x (- (long seed) (long report-seed) 1000)]
          (when (>= x 0) x)))
      0))

(defn- rel-path
  [base-file target]
  (when (and base-file target)
    (let [base (-> (io/file base-file) .getParentFile .toPath .toAbsolutePath .normalize)
          target (-> (io/file target) .toPath .toAbsolutePath .normalize)]
      (-> (str (.relativize base target))
          (str/replace "\\" "/")))))

(defn- find-artifact-link
  [visual-dir seed suffix]
  (when (and visual-dir seed)
    (let [dir (io/file visual-dir)
          files (when (.exists dir)
                  (file-seq dir))
          marker (str "seed-" seed "-")
          matches (->> files
                       (filter #(.isFile %))
                       (filter #(str/includes? (.getName %) marker))
                       (filter #(str/ends-with? (.getName %) suffix))
                       (map #(.getPath %))
                       sort
                       vec)]
      (first matches))))

(defn- top-k-counts
  [counts k]
  (->> (or counts {})
       (sort-by val >)
       (take k)
       (map (fn [[sigil n]] (str sigil ":" n)))
       (str/join ", ")))

(defn- bool-mark [x]
  (if x "yes" "no"))

(defn- trace-md
  [{:keys [report-path out-path visual-dir rank entry detail preview]}]
  (let [seed (:seed entry)
        report-seed (:seed (edn/read-string (slurp report-path)))
        run-index (run-index-for-seed report-seed detail seed)
        rule (or (:rule entry) {})
        rule-sigil (or (:rule-sigil rule) (get-in detail [:rule-sigil]) "?")
        backend (keyword (name (or (:backend rule) :clj)))
        run-result (or (:run-result entry) {})
        gen-history (vec (or (:gen-history run-result) []))
        phe-history (vec (or (:phe-history run-result) []))
        row0 (or (first gen-history) "")
        observed-row1 (or (second gen-history) "")
        phenotype0 (first phe-history)
        step-opts (merge (or (:step-opts (edn/read-string (slurp report-path))) {})
                         {:backend backend})
        tensor0 (tensor/sigil-row->tensor row0)
        bitplanes0 (tensor/tensor->bitplanes tensor0)
        stepped-bitplanes (tensor/step-bitplanes bitplanes0 rule-sigil step-opts)
        tensor1 (tensor/bitplanes->tensor stepped-bitplanes)
        ungated-row1 (tensor/tensor->sigil-row tensor1)
        gated-row1 (if phenotype0
                     (tensor/gate-sigil-row-by-phenotype row0 ungated-row1 phenotype0)
                     ungated-row1)
        one-step-match? (= gated-row1 observed-row1)
        summary (or (:summary entry) (:summary detail) {})
        stored-transfer (or (:tensor-transfer entry) (:tensor-transfer detail) {})
        recomputed-transfer
        (transfer/run-transfer-pack {:gen-history gen-history
                                     :summary summary
                                     :seed seed
                                     :run-index run-index
                                     :policy (or (:policy entry) :tensor-closed-loop)
                                     :rule rule})
        lift (meta-lift/lift-history gen-history)
        top-from-lift (->> (or (:sigil-counts lift) {})
                           (sort-by val >)
                           (map (comp str key))
                           (take 8)
                           vec)
        stored-top-sigils (vec (or (get-in entry [:meta-lift :top-sigils])
                                   (get-in detail [:meta-lift :top-sigils])
                                   []))
        top-sigils (vec (or (:top-sigils recomputed-transfer) []))
        sigil-counts (or (:sigil-counts recomputed-transfer) {})
        stored-aif (or (get stored-transfer :aif) {})
        recomputed-aif (or (:aif recomputed-transfer) {})
        stored-ca (or (get stored-transfer :cyber-ant) {})
        recomputed-ca (or (:cyber-ant recomputed-transfer) {})
        top-sigils-match? (= stored-top-sigils top-sigils)
        lift-match? (= top-from-lift top-sigils)
        aif-match? (= stored-aif recomputed-aif)
        cyber-ant-match? (= stored-ca recomputed-ca)
        image-path (find-artifact-link (some-> visual-dir (str "/images")) seed ".png")
        diagram-path (find-artifact-link (some-> visual-dir (str "/diagrams")) seed "-cyber-ant.mmd")
        image-link (rel-path out-path image-path)
        diagram-link (rel-path out-path diagram-path)
        summary-keys [:composite-score :score :avg-entropy :avg-change :avg-unique :temporal-autocorr]
        aif-keys [:aif/score :aif/food-quality :aif/trail-score :aif/biodiversity :aif/food-count]
        lines
        (concat
         ["# Tensor End-to-End Trace Report"
          ""
          (str "- Generated: `" (now-iso) "`")
          (str "- Source report: `" report-path "`")
          (str "- Selected run: rank `" rank "` seed `" seed "`")
          (str "- Rule: `" rule-sigil "` backend `" backend "`")
          (str "- Run index: `" run-index "`")
          ""
          "## 1) Plain-English Read"
          ""
          "- This traces one run from CA state evolution through tensor stages into transfer-pack outputs."
          "- The key bridge is: `gen-history + summary + seed/run-index/rule -> top-sigils + cyber-ant + aif`."
          "- This report verifies the mapping by recomputing transfer outputs and checking equality."
          ""
          "## 2) Run Context"
          ""
          (str "- Gen-history length: `" (count gen-history) "`")
          (str "- Phenotype-history length: `" (count phe-history) "`")
          (str "- Summary (selected): `" (md-escape (map-preview summary summary-keys)) "`")
          (str "- Top sigils: `" (str/join " " top-sigils) "`")
          (str "- Top counts: `" (md-escape (top-k-counts sigil-counts 8)) "`")]
         (when image-link
           [(str "- Run image: [" image-link "](" image-link ")")])
         (when diagram-link
           [(str "- Cyber-ant wiring: [" diagram-link "](" diagram-link ")")])
         ["" "## 3) Tensor Stage Trace (generation 0 -> 1)"
          ""
          "| Stage | Function | Input | Output | Check |"
          "| --- | --- | --- | --- | --- |"
          (format "| 0 | input | `row0` %s | `%s` | - |"
                  (row-shape row0)
                  (md-escape (preview-text preview row0)))
          (format "| 1 | `sigil-row->tensor` | row0 | tensor `%s` | width preserved |"
                  (tensor-shape tensor0))
          (format "| 2 | `tensor->bitplanes` | tensor0 | bitplanes `%s` | transpose |"
                  (bitplane-shape bitplanes0))
          (format "| 3 | `step-bitplanes` | rule `%s`, opts `%s` | bitplanes-next `%s` | CA local rule applied |"
                  rule-sigil
                  (md-escape (map-preview step-opts [:backend :wrap? :boundary-bit]))
                  (bitplane-shape stepped-bitplanes))
          (format "| 4 | `bitplanes->tensor` + `tensor->sigil-row` | bitplanes-next | ungated row1 `%s` | decode ok |"
                  (md-escape (preview-text preview ungated-row1)))
          (format "| 5 | `gate-sigil-row-by-phenotype` | old row0 + ungated row1 + phenotype0 | gated row1 `%s` | phenotype present: `%s` |"
                  (md-escape (preview-text preview gated-row1))
                  (bool-mark (some? phenotype0)))
          (format "| 6 | observed run history | gen-history[1] | `%s` | one-step match: `%s` |"
                  (md-escape (preview-text preview observed-row1))
                  (bool-mark one-step-match?))
          ""
          "## 4) Transfer-Pack Trace"
          ""
          "| Stage | Inputs | Outputs | Evidence |"
          "| --- | --- | --- | --- |"
          (format "| `gen-history->top-sigils` | `gen-history` | `top-sigils`, `sigil-counts` | `%s` |"
                  (md-escape (str/join " " top-sigils)))
          (format "| `pass-seed` + `pass-run-index` | `%s`, `%s` | `seed*`, `run-index*` | used in cyber-ant id/source |"
                  seed run-index)
          (format "| `pass-summary` + `summary->aif-score` | selected summary fields | `aif/*` | `%s` |"
                  (md-escape (map-preview recomputed-aif aif-keys)))
          (format "| `top-sigils->cyber-ant` | `top-sigils`, `seed*`, `run-index*`, `rule` | `cyber-ant` | id `%s`, operator `%s` |"
                  (md-escape (str (:id recomputed-ca)))
                  (md-escape (str (:operator recomputed-ca))))
          ""
          "## 5) Contract Checks"
          ""
          "| Check | Result |"
          "| --- | --- |"
          (format "| `top-sigils (stored vs recomputed)` | `%s` |" (bool-mark top-sigils-match?))
          (format "| `top-sigils (meta-lift vs transfer)` | `%s` |" (bool-mark lift-match?))
          (format "| `aif map (stored vs recomputed)` | `%s` |" (bool-mark aif-match?))
          (format "| `cyber-ant map (stored vs recomputed)` | `%s` |" (bool-mark cyber-ant-match?))
          ""
          "## 6) Wiring Link Map"
          ""
          "| Tensor-side source | Transfer node | Wiring-side target |"
          "| --- | --- | --- |"
          "| `gen-history` from tensor run | `gen-history->top-sigils` | `cyber-ant.source.sigils`, `meta-lift.top-sigils` |"
          "| `summary` from tensor MMCA metrics | `summary->aif-score` | `tensor-transfer.aif` scorecard fields |"
          "| `seed`, `run-index`, `rule` metadata | `pass-seed`, `pass-run-index`, primitive state | `cyber-ant.id`, `cyber-ant.source.rule`, `cyber-ant.source.seed` |"
          ""
          "## 7) Remaining Gap (for this run)"
          ""
          "- This confirms dataflow and deterministic transfer, not learning closure."
          "- Ant/AIF outcomes are not yet fed back as direct parameter updates into the next tensor/MMCA dynamics."
          "- So this is traceable plumbing with parity, still pre-optimization."
          ""])]
    (str/join "\n" lines)))

(defn -main [& args]
  (let [{:keys [help unknown report out seed rank visual-dir preview]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do
                (println "Unknown option:" unknown)
                (println)
                (println (usage))
                (System/exit 2))
      (str/blank? (str report))
      (do
        (println "Missing required --report")
        (println)
        (println (usage))
        (System/exit 2))
      :else
      (let [report-data (edn/read-string (slurp report))
            ranked (or (:ranked report-data) [])
            detail-by-seed (into {} (map (juxt :seed identity) (or (:runs-detail report-data) [])))
            {:keys [entry rank]} (find-entry ranked {:seed seed :rank rank})
            seed (:seed entry)
            detail (get detail-by-seed seed)
            out (or out (str "out/tensor-trace-e2e-seed-" seed ".md"))
            preview (max 16 (long (or preview 48)))
            text (trace-md {:report-path report
                            :out-path out
                            :visual-dir visual-dir
                            :rank rank
                            :entry entry
                            :detail detail
                            :preview preview})]
        (ensure-parent! out)
        (spit out text)
        (println "Wrote" out)))))
