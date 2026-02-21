(ns scripts.tensor-visual-artifacts
  "Generate visual artifacts from tensor metaevolve/closed-loop reports.

   Outputs:
   - Top run timeline images (PPM, optional PNG)
   - Mermaid diagrams for tensor pipelines
   - Mermaid diagrams for top run cyber-ant CT diagrams
   - Markdown index"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [futon5.ct.tensor-diagrams :as tensor-diagrams]
            [futon5.mmca.render :as render]))

(def ^:private default-top 8)

(defn- usage []
  (str/join
   "\n"
   ["usage: clj -M -m scripts.tensor-visual-artifacts [options]"
    ""
    "Options:"
    "  --report PATH      Tensor report EDN (required)."
    "  --out-dir PATH     Output directory (default out/tensor-visuals)."
    "  --top N            Number of top runs to render (default 8)."
    "  --scale PCT        PNG upscale percent if convert exists (default 200)."
    "  --render-exotype   Render genotype/phenotype/exotype triptych."
    "  --no-png           Skip PNG conversion; keep PPM only."
    "  --help             Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (case flag
          "--report" (recur (rest more) (assoc opts :report (first more)))
          "--out-dir" (recur (rest more) (assoc opts :out-dir (first more)))
          "--top" (recur (rest more) (assoc opts :top (parse-int (first more))))
          "--scale" (recur (rest more) (assoc opts :scale (parse-int (first more))))
          "--render-exotype" (recur more (assoc opts :render-exotype true))
          "--no-png" (recur more (assoc opts :no-png true))
          "--help" (recur more (assoc opts :help true))
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- sanitize
  [s]
  (-> (str s)
      (str/replace #"[^a-zA-Z0-9._-]" "_")))

(defn- sigil-token
  [s]
  (let [s (str s)]
    (if (= 1 (count s))
      (format "u%04x" (int (.charAt s 0)))
      (sanitize s))))

(defn- ensure-dir!
  [path]
  (doto (io/file path) .mkdirs)
  path)

(defn- write-text!
  [path text]
  (when-let [p (.getParentFile (io/file path))]
    (.mkdirs p))
  (spit path text)
  path)

(defn- has-convert?
  []
  (zero? (:exit (shell/sh "bash" "-lc" "command -v convert >/dev/null 2>&1"))))

(defn- has-montage?
  []
  (zero? (:exit (shell/sh "bash" "-lc" "command -v montage >/dev/null 2>&1"))))

(defn- maybe-ppm->png!
  [ppm png scale]
  (let [res1 (shell/sh "convert" ppm png)]
    (if-not (zero? (:exit res1))
      {:ok? false :error (or (:err res1) (:out res1))}
      (let [res2 (if (and scale (pos? (long scale)))
                   (shell/sh "mogrify" "-resize" (str scale "%") png)
                   {:exit 0})]
        {:ok? (zero? (:exit res2))
         :error (when-not (zero? (:exit res2))
                  (or (:err res2) (:out res2)))}))))

(defn- fmt-score [x]
  (format "%.2f" (double (or x 0.0))))

(defn- label-for
  [diagram]
  (case (:type diagram)
    :primitive (name (get-in diagram [:data :name]))
    :identity (str "id:" (name (first (:domain diagram))))
    :compose "compose"
    :tensor "tensor"
    (name (:type diagram))))

(defn- emit-diagram
  [diagram counter]
  (let [next-id (fn [prefix]
                  (let [n (swap! counter inc)]
                    (str prefix n)))]
    (case (:type diagram)
      :primitive
      (let [nid (next-id "n")
            dom (:domain diagram)
            cod (:codomain diagram)
            label (label-for diagram)]
        {:nodes [{:id nid :label label :shape :rect}]
         :edges (vec (concat
                      (map (fn [d] {:from (str "io_" (name d))
                                    :to nid
                                    :label "in"}) dom)
                      (map (fn [c] {:from nid
                                    :to (str "io_" (name c))
                                    :label "out"}) cod)))
         :entry nid
         :exit nid})

      :identity
      (let [nid (next-id "n")
            dom (:domain diagram)
            cod (:codomain diagram)
            label (label-for diagram)]
        {:nodes [{:id nid :label label :shape :stadium}]
         :edges (vec (concat
                      (map (fn [d] {:from (str "io_" (name d))
                                    :to nid
                                    :label "id"}) dom)
                      (map (fn [c] {:from nid
                                    :to (str "io_" (name c))
                                    :label "id"}) cod)))
         :entry nid
         :exit nid})

      :compose
      (let [parts (mapv #(emit-diagram % counter) (get-in diagram [:data :parts]))
            part-edges (->> (partition 2 1 parts)
                            (map (fn [[a b]] {:from (:exit a) :to (:entry b) :label ""}))
                            vec)]
        {:nodes (vec (mapcat :nodes parts))
         :edges (vec (concat (mapcat :edges parts) part-edges))
         :entry (:entry (first parts))
         :exit (:exit (last parts))})

      :tensor
      (let [parts (mapv #(emit-diagram % counter) (get-in diagram [:data :parts]))
            fork (next-id "fork")
            join (next-id "join")
            links (vec (concat
                        (map (fn [p] {:from fork :to (:entry p) :label ""}) parts)
                        (map (fn [p] {:from (:exit p) :to join :label ""}) parts)))]
        {:nodes (vec (concat [{:id fork :label "tensor fork" :shape :diamond}
                              {:id join :label "tensor join" :shape :diamond}]
                             (mapcat :nodes parts)))
         :edges (vec (concat (mapcat :edges parts) links))
         :entry fork
         :exit join})

      (throw (ex-info "Unsupported diagram type" {:type (:type diagram)})))))

(defn- node-syntax
  [{:keys [id label shape]}]
  (let [label (str/replace (str label) "\"" "'")]
    (case shape
      :diamond (format "%s{\"%s\"}" id label)
      :stadium (format "%s([\"%s\"])" id label)
      (format "%s[\"%s\"]" id label))))

(defn diagram->mermaid
  "Render CT diagram data as Mermaid flowchart."
  [diagram title]
  (let [counter (atom 0)
        {:keys [nodes edges]} (emit-diagram diagram counter)
        io-ids (->> (concat (:domain diagram) (:codomain diagram))
                    distinct
                    (map (fn [k] {:id (str "io_" (name k))
                                  :label (name k)
                                  :shape :stadium}))
                    vec)
        all-nodes (concat io-ids nodes)
        lines (concat
               [(str "%% " title)
                "graph LR"]
               (map #(str "    " (node-syntax %)) all-nodes)
               (map (fn [{:keys [from to label]}]
                      (if (seq label)
                        (format "    %s -->|%s| %s" from label to)
                        (format "    %s --> %s" from to)))
                    edges))]
    (str/join "\n" lines)))

(defn- render-top-runs!
  [report out-dir {:keys [top render-exotype png? scale]}]
  (let [ranked (vec (or (:ranked report) []))
        runs-detail (vec (or (:runs-detail report) []))
        by-seed (into {} (map (juxt :seed identity) runs-detail))
        top (max 1 (long (or top default-top)))
        selected (take top ranked)]
    (mapv (fn [idx entry]
            (let [seed (:seed entry)
                  score (fmt-score (get-in entry [:summary :composite-score]))
                  rule-sigil (or (get-in entry [:rule :rule-sigil]) "-")
                  base (format "run-%02d-score-%s-seed-%s-rule-%s"
                               (inc idx)
                               (sanitize score)
                               (sanitize (or seed "na"))
                               (sigil-token rule-sigil))
                  run-result (:run-result entry)
                  run-detail (get by-seed seed)
                  parity (get-in run-detail [:parity :history])
                  ppm (str out-dir "/images/" base ".ppm")
                  png (str out-dir "/images/" base ".png")
                  _ (render/render-run->file! run-result ppm {:exotype? (boolean render-exotype)})
                  png-result (when png?
                               (maybe-ppm->png! ppm png scale))
                  ct-diagram (get-in entry [:tensor-transfer :cyber-ant :ct :diagram])
                  ct-mmd (when ct-diagram
                           (str out-dir "/diagrams/" base "-cyber-ant.mmd"))
                  _ (when ct-diagram
                      (write-text! ct-mmd (diagram->mermaid ct-diagram
                                                            (str "Cyber-ant CT diagram seed " seed))))]
              {:index (inc idx)
               :seed seed
               :score score
               :rule-sigil rule-sigil
               :parity-history parity
               :ppm ppm
               :png (when (and png? (or (nil? png-result) (:ok? png-result))) png)
               :png-error (when (and png? png-result (not (:ok? png-result))) (:error png-result))
               :cyber-ant-mermaid ct-mmd}))
          (range)
          selected)))

(defn- maybe-gallery!
  [out-dir run-artifacts]
  (let [pngs (vec (keep :png run-artifacts))]
    (when (and (seq pngs) (has-montage?))
      (let [gallery (str out-dir "/images/top-runs-gallery.png")
            res (apply shell/sh
                       (concat ["montage"]
                               pngs
                               ["-tile" "2x"
                                "-geometry" "+6+6"
                                "-background" "white"
                                gallery]))]
        (when (zero? (:exit res))
          gallery)))))

(defn- render-tensor-diagrams!
  [out-dir]
  (let [ids (tensor-diagrams/available-diagrams)]
    (mapv (fn [id]
            (let [diagram (tensor-diagrams/diagram id)
                  path (str out-dir "/diagrams/" (name id) ".mmd")]
              (write-text! path (diagram->mermaid diagram (str "Tensor diagram: " (name id))))
              {:id id :path path}))
          ids)))

(defn- markdown-index
  [report-path out-dir run-artifacts diagram-artifacts gallery]
  (let [now (str (java.time.Instant/now))
        run-lines
        (map (fn [{:keys [index seed score rule-sigil parity-history ppm png cyber-ant-mermaid]}]
               (format "| %d | %s | %s | %s | %s | `%s` | `%s` |"
                       index
                       (or seed "-")
                       score
                       rule-sigil
                       (if (nil? parity-history) "-" parity-history)
                       (or png ppm)
                       (or cyber-ant-mermaid "-")))
             run-artifacts)
        diagram-lines
        (map (fn [{:keys [id path]}]
               (format "- `%s`: `%s`" (name id) path))
             diagram-artifacts)
        note-lines
        (when-let [errs (seq (keep :png-error run-artifacts))]
          (concat ["" "## Notes" ""]
                  (map #(str "- PNG conversion issue: " %) errs)))
        lines
        (concat
         ["# Tensor Visual Artifacts"
          ""
          (str "- Generated: `" now "`")
          (str "- Source report: `" report-path "`")
          (str "- Output dir: `" out-dir "`")
          (str "- Top rendered: `" (count run-artifacts) "`")
          ""
          "## Top Runs"
          ""]
         (when gallery
           [(str "- Gallery: `" gallery "`") ""])
         ["| # | seed | score | rule | history parity | image | cyber-ant diagram |"
          "| --- | --- | --- | --- | --- | --- | --- |"]
         run-lines
         ["" "## Tensor Pipeline Diagrams" ""]
         diagram-lines
         note-lines)]
    (str/join "\n" lines)))

(defn -main [& args]
  (let [{:keys [help unknown report out-dir top scale render-exotype no-png]} (parse-args args)]
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
            out-dir (or out-dir "out/tensor-visuals")
            png? (and (not no-png) (has-convert?))
            scale (or scale 200)
            _ (ensure-dir! (str out-dir "/images"))
            _ (ensure-dir! (str out-dir "/diagrams"))
            run-artifacts (render-top-runs! report-data out-dir {:top top
                                                                 :render-exotype render-exotype
                                                                 :png? png?
                                                                 :scale scale})
            gallery (maybe-gallery! out-dir run-artifacts)
            diagram-artifacts (render-tensor-diagrams! out-dir)
            index-path (str out-dir "/README.md")
            index-text (markdown-index report out-dir run-artifacts diagram-artifacts gallery)]
        (write-text! index-path index-text)
        (println "Wrote" index-path)
        (println "Rendered run visuals:" (count run-artifacts))
        (println "Rendered diagram files:" (count diagram-artifacts))
        (when gallery
          (println "Wrote" gallery))
        (when (not png?)
          (println "PNG conversion unavailable; PPM files were kept."))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
