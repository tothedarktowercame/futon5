#!/usr/bin/env bb
(ns import-futon3a-patterns
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.time Instant)))

(defn- usage [msg]
  (binding [*out* *err*]
    (when msg
      (println msg))
    (println "Usage: bb scripts/import_futon3a_patterns.clj [--futon3a PATH] [--out PATH] [--futon1-version ID]")
    (println "Defaults: --futon3a ../futon3a --out resources/exotype-xenotype-lift.edn"))
  (System/exit 1))

(defn- parse-args [args]
  (loop [args args
         opts {:futon3a "../futon3a"
               :out "resources/exotype-xenotype-lift.edn"
               :futon1-version nil}]
    (if (empty? args)
      opts
      (let [[flag val & rest] args]
        (cond
          (or (= flag "--help") (= flag "-h"))
          (usage nil)

          (= flag "--futon3a")
          (recur rest (assoc opts :futon3a val))

          (= flag "--out")
          (recur rest (assoc opts :out val))

          (= flag "--futon1-version")
          (recur rest (assoc opts :futon1-version val))

          :else
          (usage (str "Unknown or malformed args: " (str/join " " args))))))))

(defn- read-tsv [path]
  (let [raw (slurp path)
        lines (remove str/blank? (str/split-lines raw))
        header (-> (first lines)
                   (str/replace-first #"^#\s*" "")
                   (str/split #"\t"))
        rows (map #(str/split % #"\t") (rest lines))]
    (map (fn [cols]
           (zipmap header cols))
         rows)))

(defn- normalize-entry [row]
  (let [sigil (let [truth (str/trim (get row "truth" ""))]
                (when (seq truth) truth))
        hotwords (-> (get row "hotwords" "")
                     (str/split #",\s*")
                     (->> (remove str/blank?)
                          vec))]
    (when sigil
      {:sigil sigil
       :pattern-id (str/trim (get row "pattern" ""))
       :okipona (str/trim (get row "okipona" ""))
       :rationale (str/trim (get row "rationale" ""))
       :hotwords hotwords
       :source :futon3a})))

(defn- load-registry [path]
  (if (.exists (io/file path))
    (edn/read-string (slurp path))
    {:meta {:registry "exotype-xenotype-lift"
            :version 1}
     :patterns []}))

(defn- merge-patterns [existing imported]
  (let [by-key (fn [entry]
                 [(get entry :sigil) (get entry :pattern-id)])
        existing-idx (into {} (map (juxt by-key identity) existing))]
    (->> imported
         (reduce (fn [acc entry]
                   (let [k (by-key entry)]
                     (assoc acc k (merge (get acc k {}) entry))))
                 existing-idx)
         (vals)
         (sort-by (juxt :sigil :pattern-id))
         vec)))

(defn -main [& args]
  (let [{:keys [futon3a out futon1-version]} (parse-args args)
        tsv-path (str (io/file futon3a "resources" "notions" "patterns-index.tsv"))
        rows (read-tsv tsv-path)
        imported (->> rows
                      (map normalize-entry)
                      (remove nil?)
                      vec)
        existing (load-registry out)
        merged (merge-patterns (:patterns existing) imported)
        registry (assoc existing
                        :meta (merge (:meta existing)
                                     {:cache true
                                      :source-of-truth :futon1
                                      :imported-from tsv-path
                                      :imported-count (count imported)
                                      :synced-at (.toString (Instant/now))}
                                     (when futon1-version
                                       {:futon1-version futon1-version}))
                        :patterns merged)]
    (spit out (pr-str registry))
    (println "Wrote" out "with" (count merged) "patterns")))

(apply -main *command-line-args*)
