#!/usr/bin/env bb
(ns pattern-to-wiring
  "Generate deterministic wiring diagrams from sigil pattern registry entries."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [futon5.ca.core :as ca]
            [futon5.wiring.hexagram :as hex]
            [futon5.xenotype.wiring :as wiring])
  (:import (java.time Instant)))

(def ^:private default-patterns-path "resources/futon5/sigil_patterns.edn")
(def ^:private default-out-dir "resources/xenotype-wirings")
(def ^:private default-manifest-path "resources/xenotype-wirings/pattern-generated-manifest.edn")

(defn- usage []
  (str/join
   "\n"
   ["Generate wiring diagrams from entries in resources/futon5/sigil_patterns.edn."
    ""
    "Usage:"
    "  bb -cp src:resources scripts/pattern_to_wiring.clj [options]"
    "  python3 scripts/pattern_to_wiring.py [options]"
    ""
    "Options:"
    "  --all                Generate wirings for all registry entries."
    "  --sigil STR          Generate for one sigil."
    "  --pattern STR        Generate for one pattern id."
    "  --style NAME         Wiring style: simple (default) or full."
    "  --patterns PATH      Input registry path (default resources/futon5/sigil_patterns.edn)."
    "  --out-dir PATH       Output directory (default resources/xenotype-wirings)."
    "  --manifest PATH      Manifest path (default resources/xenotype-wirings/pattern-generated-manifest.edn)."
    "  --dry-run            Print what would be generated without writing files."
    "  --help               Show this message."]))

(defn- parse-args [args]
  (loop [args args
         opts {:all false
               :style :simple
               :patterns default-patterns-path
               :out-dir default-out-dir
               :manifest default-manifest-path
               :dry-run false}]
    (if (seq args)
      (let [[flag & more] args]
        (cond
          (#{"--help" "-h"} flag)
          (assoc opts :help true)

          (= "--all" flag)
          (recur more (assoc opts :all true))

          (= "--sigil" flag)
          (recur (rest more) (assoc opts :sigil (first more)))

          (= "--pattern" flag)
          (recur (rest more) (assoc opts :pattern (first more)))

          (= "--style" flag)
          (let [style (keyword (first more))]
            (if (#{:simple :full} style)
              (recur (rest more) (assoc opts :style style))
              (recur (rest more) (assoc opts :unknown (str "Unsupported style: " (first more))))))

          (= "--patterns" flag)
          (recur (rest more) (assoc opts :patterns (first more)))

          (= "--out-dir" flag)
          (recur (rest more) (assoc opts :out-dir (first more)))

          (= "--manifest" flag)
          (recur (rest more) (assoc opts :manifest (first more)))

          (= "--dry-run" flag)
          (recur more (assoc opts :dry-run true))

          :else
          (recur more (assoc opts :unknown (str "Unknown option: " flag)))))
      opts)))

(defn- slug [s]
  (let [s (or s "unnamed")
        s (-> s
              str/lower-case
              (str/replace #"[^a-z0-9]+" "-")
              (str/replace #"^-+" "")
              (str/replace #"-+$" ""))]
    (if (seq s) s "unnamed")))

(defn- safe-sigil-bits [sigil]
  (try
    (ca/bits-for sigil)
    (catch Exception _ nil)))

(defn- unicode-sum [s]
  (reduce + 0 (map int (str s))))

(defn- ->hexagram-source [sigil]
  (if-let [bits (safe-sigil-bits sigil)]
    {:hexagram (inc (mod (Integer/parseInt bits 2) 64))
     :strategy :sigil-bits-mod-64
     :sigil-bits bits}
    ;; Not all registry sigils are guaranteed to exist in the CA sigil table.
    ;; Fall back to deterministic unicode hashing to keep generation total.
    {:hexagram (inc (mod (unicode-sum sigil) 64))
     :strategy :unicode-sum-mod-64
     :sigil-bits nil}))

(defn- wiring-from-entry [{:keys [sigil pattern role description] :as entry} style]
  (let [{:keys [hexagram strategy sigil-bits]} (->hexagram-source sigil)
        hexagram-number hexagram
        base-wiring (case style
                      :full (hex/hexagram->wiring hexagram-number)
                      (hex/hexagram->simple-wiring hexagram-number))
        wiring-id (keyword (str "pattern-" (slug pattern)))
        bits sigil-bits]
    {:entry entry
     :hexagram hexagram-number
     :filename (format "pattern-%03d-%s.edn" hexagram-number (slug pattern))
     :wiring (-> base-wiring
                 (assoc-in [:meta :id] wiring-id)
                 (assoc-in [:meta :source-pattern] pattern)
                 (assoc-in [:meta :source-sigil] sigil)
                 (assoc-in [:meta :source-role] role)
                 (assoc-in [:meta :source-description] description)
                 (assoc-in [:meta :generation]
                           {:tool "scripts/pattern_to_wiring.clj"
                            :generated-at (.toString (Instant/now))
                            :strategy strategy
                            :sigil-bits bits
                            :style style}))}))

(defn- pick-entries [entries {:keys [all sigil pattern]}]
  (cond
    all entries
    (and sigil (seq sigil)) (filterv #(= sigil (:sigil %)) entries)
    (and pattern (seq pattern)) (filterv #(= pattern (:pattern %)) entries)
    :else []))

(defn- validate-wiring! [lib {:keys [wiring filename]}]
  (let [validation (wiring/validate-diagram lib (:diagram wiring))]
    (when-not (:ok? validation)
      (throw (ex-info "Generated invalid wiring diagram."
                      {:file filename
                       :errors (:errors validation)
                       :warnings (:warnings validation)})))
    validation))

(defn- write-wiring! [out-dir {:keys [filename wiring]}]
  (let [path (str (io/file out-dir filename))]
    (spit path (str (pr-str wiring) "\n"))
    path))

(defn -main [& args]
  (let [{:keys [help unknown patterns out-dir manifest dry-run style] :as opts} (parse-args args)]
    (cond
      help (println (usage))
      unknown (do
                (binding [*out* *err*]
                  (println unknown)
                  (println)
                  (println (usage)))
                (System/exit 2))
      :else
      (let [entries (edn/read-string (slurp patterns))
            selected (pick-entries (vec entries) opts)]
        (when (empty? selected)
          (binding [*out* *err*]
            (println "No patterns selected. Use --all, --sigil, or --pattern.")
            (println)
            (println (usage)))
          (System/exit 2))
        (let [generated (mapv #(wiring-from-entry % style) selected)
              lib (wiring/load-components)
              checks (mapv #(validate-wiring! lib %) generated)
              manifest-data {:generated-at (.toString (Instant/now))
                             :tool "scripts/pattern_to_wiring.clj"
                             :patterns-path patterns
                             :out-dir out-dir
                             :style style
                             :count (count generated)
                             :entries (mapv (fn [{:keys [entry hexagram filename]} validation]
                                              {:file filename
                                               :pattern (:pattern entry)
                                               :sigil (:sigil entry)
                                               :hexagram hexagram
                                               :ok? (:ok? validation)
                                               :warnings (:warnings validation)})
                                            generated
                                            checks)}]
          (if dry-run
            (do
              (println "Dry run: no files written.")
              (doseq [{:keys [entry filename hexagram]} generated]
                (println "  " filename "<-" (:pattern entry) "[" (:sigil entry) "]" "hexagram" hexagram)))
            (do
              (.mkdirs (io/file out-dir))
              (doseq [g generated]
                (let [path (write-wiring! out-dir g)]
                  (println "Wrote" path)))
              (.mkdirs (.getParentFile (io/file manifest)))
              (spit manifest (str (pr-str manifest-data) "\n"))
              (println "Wrote" manifest))))))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
