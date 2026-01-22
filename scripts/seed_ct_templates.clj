#!/usr/bin/env bb
(ns seed-ct-templates
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [futon5.exotic.ct-compressors :as compressors]))

(defn- usage []
  (str/join
   "\n"
   ["Seed missing CT templates in the exotype-xenotype lift registry."
    ""
    "Usage:"
    "  bb -cp futon5/src futon5/scripts/seed_ct_templates.clj [options]"
    ""
    "Options:"
    "  --lift-in PATH        Input lift registry (default futon5/resources/exotype-xenotype-lift.edn)."
    "  --lift-out PATH       Output lift registry (default overwrite input)."
    "  --pattern-root PATH   Root for pattern flexiargs (default futon3/library)."
    "  --compressor ID       Compressor id (default simple-if-then)."
    "  --dry-run             Report counts only, no write."
    "  --help                Show this message."]))

(defn- parse-args [args]
  (loop [args args
         opts {}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag) (recur more (assoc opts :help true))
          (= "--lift-in" flag) (recur (rest more) (assoc opts :lift-in (first more)))
          (= "--lift-out" flag) (recur (rest more) (assoc opts :lift-out (first more)))
          (= "--pattern-root" flag) (recur (rest more) (assoc opts :pattern-root (first more)))
          (= "--compressor" flag) (recur (rest more) (assoc opts :compressor (keyword (first more))))
          (= "--dry-run" flag) (recur more (assoc opts :dry-run true))
          :else (recur more (assoc opts :unknown flag))))
      opts)))

(defn- compressor-by-id [id]
  (case id
    :simple-if-then compressors/simple-if-then
    nil))

(defn- seed-pattern [pattern compressor pattern-root]
  (if (:ct-template pattern)
    {:pattern pattern :changed? false}
    (let [pattern-id (:pattern-id pattern)
          result (compressor {:pattern-id pattern-id :pattern-root pattern-root})]
      (if-let [ct (:ct-template result)]
        {:pattern (assoc pattern
                         :ct-template ct
                         :ct-template-source :simple-if-then)
         :changed? true}
        {:pattern pattern :changed? false}))))

(defn -main [& args]
  (let [{:keys [help unknown lift-in lift-out pattern-root compressor dry-run]} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do (println "Unknown option:" unknown) (println (usage)))
      :else
      (let [lift-in (or lift-in "futon5/resources/exotype-xenotype-lift.edn")
            lift-out (or lift-out lift-in)
            pattern-root (or pattern-root "futon3/library")
            compressor-id (or compressor :simple-if-then)
            compressor-fn (compressor-by-id compressor-id)
            registry (edn/read-string (slurp lift-in))
            patterns (:patterns registry)]
        (when-not compressor-fn
          (throw (ex-info "Unknown compressor" {:compressor compressor-id})))
        (let [seeded (mapv (fn [p] (seed-pattern p compressor-fn pattern-root)) patterns)
              updated (mapv :pattern seeded)
              changed (count (filter :changed? seeded))]
          (println "Patterns:" (count patterns))
          (println "CT templates added:" changed)
          (when-not dry-run
            (spit lift-out (pr-str (assoc registry :patterns updated)))
            (println "Wrote" lift-out)))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
