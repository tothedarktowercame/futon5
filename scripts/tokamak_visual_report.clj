(ns scripts.tokamak-visual-report
  "Render paired tokamak result images and generate a visual markdown report."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.mmca.render :as render]))

(defn- usage []
  (str/join
   "\n"
   ["usage: bb -m scripts.tokamak-visual-report [options]"
    ""
    "Options:"
    "  --baseline-dir PATH   Directory of baseline seed-*.edn runs (required)."
    "  --variant-dir PATH    Directory of variant seed-*.edn runs (required)."
    "  --out-dir PATH        Output directory (default out/tokamak/visual-report)."
    "  --baseline-label TXT  Baseline label (default baseline)."
    "  --variant-label TXT   Variant label (default variant)."
    "  --title TXT           Report title."
    "  --help                Show this message."]))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (case flag
          "--baseline-dir" (recur (rest more) (assoc opts :baseline-dir (first more)))
          "--variant-dir" (recur (rest more) (assoc opts :variant-dir (first more)))
          "--out-dir" (recur (rest more) (assoc opts :out-dir (first more)))
          "--baseline-label" (recur (rest more) (assoc opts :baseline-label (first more)))
          "--variant-label" (recur (rest more) (assoc opts :variant-label (first more)))
          "--title" (recur (rest more) (assoc opts :title (first more)))
          "--help" (recur more (assoc opts :help true))
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- ensure-dir! [path]
  (doto (io/file path) .mkdirs)
  path)

(defn- parse-long-safe [s]
  (try (Long/parseLong (str s))
       (catch Exception _ nil)))

(defn- seed-from-name [name]
  (some->> (re-find #"seed-(\d+)\.edn$" (str name))
           second
           parse-long-safe))

(defn- seed-edn-map [dir]
  (->> (file-seq (io/file dir))
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".edn"))
       (keep (fn [f]
               (when-let [seed (seed-from-name (.getName f))]
                 [seed (.getPath f)])))
       (into {})))

(defn- has-convert? []
  (zero? (:exit (shell/sh "bash" "-lc" "command -v convert >/dev/null 2>&1"))))

(defn- ppm->png! [ppm png]
  (let [res (shell/sh "convert" ppm png)]
    (when-not (zero? (:exit res))
      (throw (ex-info "convert failed"
                      {:ppm ppm
                       :png png
                       :err (:err res)
                       :out (:out res)})))
    png))

(defn- load-edn [path]
  (edn/read-string (slurp path)))

(defn- short-f [x]
  (format "%.3f" (double (or x 0.0))))

(defn- exo-stats [run]
  (let [ledger (vec (or (:ledger run) []))]
    {:attempts (count (filter #(get-in % [:mutation :exotype :attempted?]) ledger))
     :selected (count (filter #(get-in % [:mutation :exotype :selected?]) ledger))
     :cells (reduce + 0 (map #(long (or (get-in % [:mutation :exotype :tilt :changed-cells]) 0))
                             ledger))}))

(defn- run-metrics [run]
  (let [summary (or (:summary run) {})
        exo (exo-stats run)]
    {:best-total (double (or (:best-total summary) 0.0))
     :final-total (double (or (:final-total summary) 0.0))
     :rewinds (long (or (:rewinds summary) 0))
     :attempts (:attempts exo)
     :selected (:selected exo)
     :cells (:cells exo)}))

(defn- render-run-image! [run path]
  (render/render-run->file! {:gen-history (get-in run [:final :gen-history])
                             :phe-history (get-in run [:final :phe-history])}
                            path
                            {:exotype? true})
  path)

(defn- rel-path [base-file target]
  (let [base (-> (io/file base-file) .getParentFile .toPath .toAbsolutePath .normalize)
        target (-> (io/file target) .toPath .toAbsolutePath .normalize)]
    (-> (str (.relativize base target))
        (str/replace "\\" "/"))))

(defn- build-report
  [{:keys [title out-md baseline-label variant-label rows]}]
  (let [header (or title "Tokamak Visual Compare Report")
        lines (concat
               [(str "# " header)
                ""
                "- Images are triptychs: genotype | phenotype | exotype."
                "- Each row compares the same seed across two configurations."
                ""
                "| Seed | Baseline final | Variant final | Delta final | Baseline exo sel/att | Variant exo sel/att | Rewinds b/v |"
                "| --- | --- | --- | --- | --- | --- | --- |"]
               (map (fn [{:keys [seed b v]}]
                      (format "| %d | %s | %s | %s | %d/%d | %d/%d | %d/%d |"
                              seed
                              (short-f (:final-total b))
                              (short-f (:final-total v))
                              (short-f (- (double (:final-total v))
                                          (double (:final-total b))))
                              (:selected b) (:attempts b)
                              (:selected v) (:attempts v)
                              (:rewinds b) (:rewinds v)))
                    rows)
               [""]
               (mapcat (fn [{:keys [seed b v b-img v-img]}]
                         [(str "## Seed " seed)
                          ""
                          (str "- " baseline-label ": best `" (short-f (:best-total b))
                               "`, final `" (short-f (:final-total b))
                               "`, rewinds `" (:rewinds b)
                               "`, exo selected/attempts `" (:selected b) "/" (:attempts b)
                               "`, changed-cells `" (:cells b) "`")
                          (str "- " variant-label ": best `" (short-f (:best-total v))
                               "`, final `" (short-f (:final-total v))
                               "`, rewinds `" (:rewinds v)
                               "`, exo selected/attempts `" (:selected v) "/" (:attempts v)
                               "`, changed-cells `" (:cells v) "`")
                          ""
                          (str "| " baseline-label " | " variant-label " |")
                          "| --- | --- |"
                          (str "| ![](" b-img ") | ![](" v-img ") |")
                          ""])
                       rows))]
    (spit out-md (str/join "\n" lines))
    out-md))

(defn -main [& args]
  (let [{:keys [help unknown baseline-dir variant-dir out-dir baseline-label variant-label title]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do
                (println "Unknown option:" unknown)
                (println)
                (println (usage))
                (System/exit 2))
      (or (str/blank? baseline-dir) (str/blank? variant-dir))
      (do
        (println "--baseline-dir and --variant-dir are required")
        (println)
        (println (usage))
        (System/exit 2))
      :else
      (let [out-dir (or out-dir "out/tokamak/visual-report")
            baseline-label (or baseline-label "baseline")
            variant-label (or variant-label "variant")
            out-images (str (io/file out-dir "images"))
            out-md (str (io/file out-dir "README.md"))
            _ (ensure-dir! out-images)
            baseline-map (seed-edn-map baseline-dir)
            variant-map (seed-edn-map variant-dir)
            seeds (->> (keys baseline-map)
                       (filter #(contains? variant-map %))
                       sort
                       vec)
            _ (when-not (seq seeds)
                (throw (ex-info "No overlapping seed-*.edn runs found"
                                {:baseline-dir baseline-dir
                                 :variant-dir variant-dir})))
            use-png? (has-convert?)
            rows (mapv (fn [seed]
                         (let [b-run (load-edn (get baseline-map seed))
                               v-run (load-edn (get variant-map seed))
                               b-m (run-metrics b-run)
                               v-m (run-metrics v-run)
                               b-ppm (str (io/file out-images (str "seed-" seed "-" baseline-label ".ppm")))
                               v-ppm (str (io/file out-images (str "seed-" seed "-" variant-label ".ppm")))
                               _ (render-run-image! b-run b-ppm)
                               _ (render-run-image! v-run v-ppm)
                               b-img-path (if use-png?
                                            (ppm->png! b-ppm (str (io/file out-images (str "seed-" seed "-" baseline-label ".png"))))
                                            b-ppm)
                               v-img-path (if use-png?
                                            (ppm->png! v-ppm (str (io/file out-images (str "seed-" seed "-" variant-label ".png"))))
                                            v-ppm)]
                           {:seed seed
                            :b b-m
                            :v v-m
                            :b-img (rel-path out-md b-img-path)
                            :v-img (rel-path out-md v-img-path)}))
                       seeds)]
        (build-report {:title title
                       :out-md out-md
                       :baseline-label baseline-label
                       :variant-label variant-label
                       :rows rows})
        (println "Wrote" out-md)
        (println "Wrote images to" out-images)
        (println "Seeds:" (str/join ", " seeds))
        (println "PNG conversion:" (if use-png? "enabled" "disabled"))))))
