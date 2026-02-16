(ns cyberant-wiring-compare
  "Compare wiring-based cyberant configs vs sigil-based configs.

   Generates cyberant config EDN files that can be used with futon2's
   ants.compare benchmark.

   Usage:
     bb -cp src:resources scripts/cyberant_wiring_compare.clj [options]

   Options:
     --wiring PATH       Wiring file to convert (default: L5-creative)
     --sigils CSV        Comma-separated sigils to compare against
     --out-dir PATH      Output directory (default: /tmp/cyberant-compare)
     --help              Show this message"
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.adapters.cyberant :as cyber]
            [futon5.scripts.output :as out]))

(defn- usage []
  (str/join
   "\n"
   ["Cyberant Wiring Compare: generate configs for ants benchmark"
    ""
    "Usage:"
    "  bb -cp src:resources scripts/cyberant_wiring_compare.clj [options]"
    ""
    "Options:"
    "  --wiring PATH       Wiring file to convert (default: data/wiring-ladder/level-5-creative.edn)"
    "  --sigils CSV        Comma-separated sigils to compare against (default: 工,土)"
    "  --out-dir PATH      Output directory (default: /tmp/cyberant-compare)"
    "  --help              Show this message"
    ""
    "Output:"
    "  Creates EDN files for use with futon2's ants.compare:"
    "    - wiring-cyberant.edn    (from wiring diagram)"
    "    - sigil-cyberants.edn    (from sigil list)"
    "    - comparison-manifest.edn (metadata for tracking)"
    ""
    "Example:"
    "  bb -cp src:resources scripts/cyberant_wiring_compare.clj \\"
    "    --wiring data/wiring-ladder/level-5-creative.edn \\"
    "    --sigils 工,土,上"
    ""
    "Then in futon2:"
    "  clj -M -m ants.compare \\"
    "    --hex /tmp/cyberant-compare/wiring-cyberant.edn \\"
    "    --sigil /tmp/cyberant-compare/sigil-cyberants.edn \\"
    "    --runs 20 --ticks 200"]))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"-h" "--help"} flag) (recur nil (assoc opts :help true))
          (= "--wiring" flag) (recur (rest more) (assoc opts :wiring (first more)))
          (= "--sigils" flag) (recur (rest more) (assoc opts :sigils (first more)))
          (= "--out-dir" flag) (recur (rest more) (assoc opts :out-dir (first more)))
          :else (recur more (assoc opts :unknown flag))))
      opts)))

(defn- parse-sigils [csv]
  (->> (str/split (or csv "") #",")
       (map str/trim)
       (remove str/blank?)
       vec))

(defn- write-edn [path data]
  (out/spit-text! path (pr-str data)))

(defn- config-summary [config]
  (select-keys config [:species :wiring-pattern :default-mode
                       :precision :pattern-sense :adapt-config]))

(defn -main [& args]
  (let [{:keys [help unknown wiring sigils out-dir]} (parse-args args)
        wiring (or wiring "data/wiring-ladder/level-5-creative.edn")
        sigils (parse-sigils (or sigils "工,土"))
        out-dir (or out-dir "/tmp/cyberant-compare")]
    (cond
      help
      (println (usage))

      unknown
      (do (println "Unknown option:" unknown)
          (println)
          (println (usage)))

      :else
      (do
        (println "=== Cyberant Wiring Compare ===")
        (println)

        ;; Ensure output directory exists
        (out/warn-overwrite-dir! out-dir)
        (.mkdirs (io/file out-dir))

        ;; Convert wiring to cyberant
        (println "Converting wiring:" wiring)
        (let [wiring-config (cyber/convert->cyberant wiring)
              analysis (when-let [w (cyber/load-wiring wiring)]
                         (cyber/analyze-wiring-structure w))]
          (println "  Pattern:" (:wiring-pattern wiring-config))
          (println "  Adapt enabled?" (get-in wiring-config [:adapt-config :enabled?]))
          (println)

          ;; Convert sigils to cyberants
          (println "Converting sigils:" (str/join ", " sigils))
          (let [sigil-configs (mapv cyber/sigil->cyberant sigils)]
            (doseq [cfg sigil-configs]
              (println "  " (:sigil cfg) "→" (:species cfg)))
            (println)

            ;; Write output files
            (let [wiring-path (str out-dir "/wiring-cyberant.edn")
                  sigil-path (str out-dir "/sigil-cyberants.edn")
                  manifest-path (str out-dir "/comparison-manifest.edn")]

              ;; Wiring config (single cyberant)
              (write-edn wiring-path {:cyberant wiring-config})

              ;; Sigil configs (multiple cyberants)
              (write-edn sigil-path {:cyberants sigil-configs})

              ;; Manifest for tracking
              (write-edn manifest-path
                         {:created (str (java.time.Instant/now))
                          :wiring-source wiring
                          :wiring-pattern (:wiring-pattern wiring-config)
                          :wiring-analysis (select-keys analysis
                                                        [:pattern :has-legacy? :has-creative?
                                                         :has-gate? :has-diversity?])
                          :sigils sigils
                          :comparison-type :wiring-vs-sigils
                          :wiring-summary (config-summary wiring-config)
                          :sigil-summaries (mapv config-summary sigil-configs)})

              (println)
              (println "=== Ready for comparison ===")
              (println)
              (println "Run in futon2:")
              (println (format "  clj -M -m ants.compare \\"))
              (println (format "    --hex %s \\" (out/abs-path wiring-path)))
              (println (format "    --sigil %s \\" (out/abs-path sigil-path)))
              (println "    --runs 20 --ticks 200"))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
