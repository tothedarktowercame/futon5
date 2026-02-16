(ns xenotype-portrait-run
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.render :as render]
            [futon5.mmca.runtime :as mmca]
            [futon5.scripts.output :as out]
            [futon5.xenotype.mermaid :as mermaid]
            [futon5.xenotype.wiring :as wiring]))

(def ^:private default-length 64)
(def ^:private default-generations 64)

(defn- usage []
  (str/join
   "\n"
   ["Xenotype portrait: render triptych + wiring mermaid."
    ""
    "Usage:"
    "  bb -cp futon5/src:futon5/resources futon5/scripts/xenotype_portrait_run.clj [options]"
    ""
    "Options:"
    "  --out-dir PATH         Output directory (default /tmp/futon5-xenotype-portrait)"
    "  --genotype STR         Starting sigil string."
    "  --length N             Random sigil string length (default 64)."
    "  --phenotype STR        Starting phenotype bit string."
    "  --phenotype-length N   Phenotype length (default = length)."
    "  --generations N        Generations to run (default 64)."
    "  --kernel KW            Kernel keyword, e.g. :mutating-template."
    "  --mode KW              :god or :classic (default :god)."
    "  --pattern-sigils STR   Comma-separated sigils to activate operators."
    "  --no-operators         Disable MMCA operators."
    "  --lock-kernel          Ignore operator kernel changes."
    "  --freeze-genotype      Keep genotype fixed."
    "  --genotype-gate        Gate genotype changes by phenotype bits."
    "  --gate-signal BIT      Phenotype bit used as keep-signal (default 1)."
    "  --seed N               Seed for deterministic init."
    "  --input PATH           Render from an EDN run result instead of running."
    "  --save-run PATH        Write the run result to EDN (optional)."
    "  --gen-wiring-path PATH Generator wiring EDN path (optional)."
    "  --gen-wiring-index N   Generator wiring index if EDN has :candidates (default 0)."
    "  --gen-wiring-out PATH  Generator mermaid output path (default xenotype-generator.mmd)."
    "  --score-wiring-path PATH Scorer wiring EDN path (optional)."
    "  --score-wiring-index N  Scorer wiring index if EDN has :candidates (default 0)."
    "  --score-wiring-out PATH Scorer mermaid output path (default xenotype-scorer.mmd)."
    "  --wiring-path PATH     (deprecated) Wiring diagram EDN path."
    "  --wiring-index N       (deprecated) Wiring candidate index."
    "  --wiring-out PATH      (deprecated) Mermaid output path."
    "  --out PATH             Output image path (PPM only; default triptych.ppm)."
    "  --help                 Show this message."]))

(defn- parse-int [s]
  (try (Long/parseLong s) (catch Exception _ nil)))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (recur more (assoc opts :help true))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--genotype" flag)
          (recur (rest more) (assoc opts :genotype (first more)))

          (= "--length" flag)
          (recur (rest more) (assoc opts :length (parse-int (first more))))

          (= "--phenotype" flag)
          (recur (rest more) (assoc opts :phenotype (first more)))

          (= "--phenotype-length" flag)
          (recur (rest more) (assoc opts :phenotype-length (parse-int (first more))))

          (= "--generations" flag)
          (recur (rest more) (assoc opts :generations (parse-int (first more))))

          (= "--kernel" flag)
          (recur (rest more) (assoc opts :kernel (some-> (first more) keyword)))

          (= "--mode" flag)
          (recur (rest more) (assoc opts :mode (some-> (first more) keyword)))

          (= "--pattern-sigils" flag)
          (let [tokens (->> (str/split (or (first more) "") #",")
                            (map str/trim)
                            (remove str/blank?)
                            vec)]
            (recur (rest more) (assoc opts :pattern-sigils tokens)))

          (= "--no-operators" flag)
          (recur more (assoc opts :no-operators true))

          (= "--lock-kernel" flag)
          (recur more (assoc opts :lock-kernel true))

          (= "--freeze-genotype" flag)
          (recur more (assoc opts :freeze-genotype true))

          (= "--genotype-gate" flag)
          (recur more (assoc opts :genotype-gate true))

          (= "--gate-signal" flag)
          (recur (rest more) (assoc opts :gate-signal (first more)))

          (= "--seed" flag)
          (recur (rest more) (assoc opts :seed (parse-int (first more))))

          (= "--input" flag)
          (recur (rest more) (assoc opts :input (first more)))

          (= "--save-run" flag)
          (recur (rest more) (assoc opts :save-run (first more)))

          (= "--wiring-path" flag)
          (recur (rest more) (assoc opts :wiring-path (first more)))

          (= "--wiring-index" flag)
          (recur (rest more) (assoc opts :wiring-index (parse-int (first more))))

          (= "--wiring-out" flag)
          (recur (rest more) (assoc opts :wiring-out (first more)))

          (= "--gen-wiring-path" flag)
          (recur (rest more) (assoc opts :gen-wiring-path (first more)))

          (= "--gen-wiring-index" flag)
          (recur (rest more) (assoc opts :gen-wiring-index (parse-int (first more))))

          (= "--gen-wiring-out" flag)
          (recur (rest more) (assoc opts :gen-wiring-out (first more)))

          (= "--score-wiring-path" flag)
          (recur (rest more) (assoc opts :score-wiring-path (first more)))

          (= "--score-wiring-index" flag)
          (recur (rest more) (assoc opts :score-wiring-index (parse-int (first more))))

          (= "--score-wiring-out" flag)
          (recur (rest more) (assoc opts :score-wiring-out (first more)))

          (= "--out" flag)
          (recur (rest more) (assoc opts :out (first more)))

          :else
          (recur more (assoc opts :unknown flag))))
      opts)))

(defn- rng-int [^java.util.Random rng n]
  (.nextInt rng n))

(defn- rng-sigil-string [^java.util.Random rng length]
  (let [sigils (mapv :sigil (ca/sigil-entries))]
    (apply str (repeatedly length #(nth sigils (rng-int rng (count sigils)))))))

(defn- rng-phenotype-string [^java.util.Random rng length]
  (apply str (repeatedly length #(rng-int rng 2))))

(defn- load-run [path]
  (edn/read-string {:readers {'object (fn [_] nil)}} (slurp path)))

(def ^:private default-gen-wiring-path
  "resources/xenotype-wirings/prototype-001-creative-peng.edn")

(def ^:private default-score-wiring-path
  "resources/xenotype-scorer-wirings/scorer-peng-diversity.edn")

(defn- existing-path [path]
  (when (and path (.exists (io/file path))) path))

(defn- extract-diagram [data]
  (cond
    (and (map? data) (:diagram data)) (:diagram data)
    (map? data) data
    :else (wiring/example-diagram)))

(defn- sanitize-id [id]
  (-> (cond
        (keyword? id) (name id)
        (string? id) id
        (nil? id) "nil"
        :else (str id))
      (str/replace #"[^A-Za-z0-9_]" "_")))

(defn- label-for-node [node]
  (let [id (name (:id node))
        component (name (:component node))]
    (str id ": " component)))

(defn- edge-label [{:keys [from-port to-port]}]
  (cond
    (and from-port to-port) (str (name from-port) " → " (name to-port))
    to-port (name to-port)
    from-port (name from-port)
    :else ""))

(defn- diagram->mermaid [diagram]
  (let [nodes (:nodes diagram)
        edges (:edges diagram)
        node-ids (into {} (map (fn [node]
                                 [(:id node) (sanitize-id (:id node))])
                               nodes))
        value-edges (filter :value edges)
        value-ids (into {} (map-indexed (fn [idx edge]
                                          [edge (str "value_" idx)])
                                        value-edges))
        out-id (some-> (:output diagram) node-ids)
        header "graph LR"
        node-lines (map (fn [node]
                          (let [nid (get node-ids (:id node))
                                label (label-for-node node)]
                            (format "  %s[\"%s\"]" nid label)))
                        nodes)
        value-lines (map (fn [edge]
                           (let [vid (get value-ids edge)
                                 label (str "value " (:value edge)
                                            (when-let [vt (:value-type edge)]
                                              (str " (" (name vt) ")")))]
                             (format "  %s[\"%s\"]" vid label)))
                         value-edges)
        edge-lines (map (fn [edge]
                          (let [label (edge-label edge)
                                label (when (seq label) (str " \"" label "\""))
                                to-id (get node-ids (:to edge))]
                            (if (:value edge)
                              (let [from-id (get value-ids edge)]
                                (format "  %s --%s--> %s" from-id (or label "") to-id))
                              (let [from-id (get node-ids (:from edge))]
                                (format "  %s --%s--> %s" from-id (or label "") to-id)))))
                        edges)
        output-line (when out-id
                      (format "  class %s output" out-id))
        classes ["  classDef output fill:#ffe6a6,stroke:#6b4d00,stroke-width:2px;"]]
    (str/join
     "\n"
     (concat [header]
             node-lines
             value-lines
             edge-lines
             (when output-line [output-line])
             classes
             [""]))))

(defn- resolve-wiring
  [{:keys [wiring-path wiring-index default-path]}]
  (let [path (or (and (seq wiring-path) wiring-path)
                 (existing-path default-path))]
    (cond
      (and path (seq path))
      (let [data (edn/read-string (slurp path))]
        (cond
          (and (map? data) (:candidates data))
          (let [idx (long (or wiring-index 0))]
            (nth (:candidates data) (max 0 (min (dec (count (:candidates data))) idx))))
          (map? data) data
          :else (wiring/example-diagram)))
      :else
      (wiring/example-diagram))))

(defn- wiring->mermaid-text
  [wiring _title]
  (diagram->mermaid (extract-diagram wiring)))

(defn- wiring-title [wiring fallback]
  (or (get-in wiring [:meta :id])
      (get-in wiring [:meta :name])
      fallback))

(defn -main [& args]
  (let [{:keys [help unknown out-dir genotype length phenotype phenotype-length generations
                kernel mode pattern-sigils no-operators lock-kernel freeze-genotype
                genotype-gate gate-signal seed input save-run wiring-path wiring-index
                wiring-out out gen-wiring-path gen-wiring-index gen-wiring-out
                score-wiring-path score-wiring-index score-wiring-out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [out-dir (or out-dir "/tmp/futon5-xenotype-portrait")
            out (or out (str out-dir "/triptych.ppm"))
            gen-wiring-path (or gen-wiring-path wiring-path)
            gen-wiring-index (or gen-wiring-index wiring-index)
            gen-wiring-out (or gen-wiring-out wiring-out (str out-dir "/xenotype-generator.mmd"))
            score-wiring-out (or score-wiring-out (str out-dir "/xenotype-scorer.mmd"))
            _ (do
                (out/warn-overwrite-dir! out-dir)
                (.mkdirs (io/file out-dir)))
            {:keys [result fresh?]}
            (if input
              {:result (load-run input)
               :fresh? false}
              (let [length (or length default-length)
                    generations (or generations default-generations)
                    rng (when seed (java.util.Random. (long seed)))
                    genotype (or genotype
                                 (when rng (rng-sigil-string rng length))
                                 (ca/random-sigil-string length))
                    phenotype-length (or phenotype-length length)
                    phenotype (or phenotype
                                  (when rng (rng-phenotype-string rng phenotype-length))
                                  (ca/random-phenotype-string phenotype-length))
                    base {:genotype genotype
                          :generations generations
                          :mode (or mode :god)}
                    base (cond-> base
                           kernel (assoc :kernel kernel)
                           phenotype (assoc :phenotype phenotype)
                           lock-kernel (assoc :lock-kernel true)
                           freeze-genotype (assoc :freeze-genotype true)
                           genotype-gate (assoc :genotype-gate true)
                           gate-signal (assoc :genotype-gate-signal (first gate-signal))
                           (seq pattern-sigils) (assoc :pattern-sigils pattern-sigils))
                    opts (cond-> base
                           no-operators (assoc :operators []))]
                {:result (mmca/run-mmca opts)
                 :fresh? true}))]
        (out/warn-overwrite-file! out)
        (render/render-run->file! result out {:exotype? true})
        (let [gen-wiring (resolve-wiring {:wiring-path gen-wiring-path
                                          :wiring-index gen-wiring-index
                                          :default-path default-gen-wiring-path})
              score-wiring (resolve-wiring {:wiring-path score-wiring-path
                                            :wiring-index score-wiring-index
                                            :default-path default-score-wiring-path})]
          (out/spit-text! gen-wiring-out (wiring->mermaid-text gen-wiring (wiring-title gen-wiring "generator")))
          (out/spit-text! score-wiring-out (wiring->mermaid-text score-wiring (wiring-title score-wiring "scorer"))))
        (when (and save-run (or fresh? input))
          (out/spit-text! save-run (pr-str result)))
        (out/announce-wrote! out)
        (println "Outputs saved to" (out/abs-path out-dir)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
