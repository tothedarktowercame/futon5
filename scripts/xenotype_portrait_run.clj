(ns xenotype-portrait-run
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.mmca.render :as render]
            [futon5.mmca.runtime :as mmca]
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
    "  --wiring-path PATH     Wiring diagram EDN path (optional)."
    "  --wiring-index N       Wiring candidate index if EDN has :candidates (default 0)."
    "  --wiring-out PATH      Mermaid output path (default xenotype-portrait.mmd)."
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
  (edn/read-string (slurp path)))

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

(defn- resolve-wiring-diagram [{:keys [wiring-path wiring-index]}]
  (cond
    (and wiring-path (seq wiring-path))
    (let [data (edn/read-string (slurp wiring-path))]
      (cond
        (and (map? data) (:candidates data))
        (let [idx (long (or wiring-index 0))]
          (nth (:candidates data) (max 0 (min (dec (count (:candidates data))) idx))))
        (map? data) data
        :else (wiring/example-diagram)))
    :else
    (wiring/example-diagram)))

(defn -main [& args]
  (let [{:keys [help unknown out-dir genotype length phenotype phenotype-length generations
                kernel mode pattern-sigils no-operators lock-kernel freeze-genotype
                genotype-gate gate-signal seed input save-run wiring-path wiring-index
                wiring-out out]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown)
                  (println)
                  (println (usage)))
      :else
      (let [out-dir (or out-dir "/tmp/futon5-xenotype-portrait")
            out (or out (str out-dir "/triptych.ppm"))
            wiring-out (or wiring-out (str out-dir "/xenotype-portrait.mmd"))
            _ (.mkdirs (io/file out-dir))
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
        (render/render-run->file! result out {:exotype? true})
        (let [diagram (resolve-wiring-diagram {:wiring-path wiring-path
                                               :wiring-index wiring-index})
              mermaid (diagram->mermaid diagram)]
          (spit wiring-out mermaid))
        (when (and save-run (or fresh? input))
          (spit save-run (pr-str result))
          (println "Saved run" save-run))
        (println "Wrote" out)
        (println "Wrote" wiring-out)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
