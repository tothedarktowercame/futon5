#!/usr/bin/env bb
;; extract_clj_relations.bb — O4(b): SPARSE feeds-A? graph over the O4(c) nodes.
;; Emits file-dependency (ns->ns require) + function-ownership (symbol->ns) edges and
;; total-A-degree per node. SPARSE only — never a dense 7534x7534. claude-6 (E2).
;; Usage: bb extract_clj_relations.bb <ids.json> <out.json> <root> [root ...]
(require '[clojure.edn :as edn] '[clojure.java.io :as io] '[cheshire.core :as json]
         '[clojure.string :as str])
(import 'java.io.PushbackReader)

(def EXCLUDE #{".venv" "node_modules" ".state" "target" ".git" ".cpcache" "resources"})
(defn excluded? [p]
  (let [s (str p)]
    (or (some #(str/includes? s (str "/" % "/")) EXCLUDE)
        (str/includes? s "~") (re-find #"worktree|/origin/|\.orig" s))))

(defn read-forms [f]
  (try (with-open [r (PushbackReader. (io/reader f))]
         (loop [v []] (let [x (read {:eof ::eof :read-cond :allow} r)]
                        (if (= x ::eof) v (recur (conj v x))))))
       (catch Throwable _ nil)))

(defn ns-form [forms] (some #(when (and (seq? %) (= 'ns (first %))) %) forms))

(defn requires-of [nf]
  ;; collect ns symbols from (:require ...) and (:use ...) clauses of an ns form
  (when nf
    (->> (rest nf)
         (filter seq?)
         (filter #(#{:require :use} (first %)))
         (mapcat rest)
         (keep (fn [e] (cond (vector? e) (str (first e))
                             (symbol? e) (str e)
                             (seq? e) (str (first e))
                             :else nil))))))

(let [[ids-path out & roots] *command-line-args*
      nodes (cheshire.core/parse-string (slurp ids-path) true)
      idx   (into {} (map-indexed (fn [i n] [(:id n) i]) nodes))
      n     (count nodes)
      deg   (long-array n)
      ;; function-ownership: every non-ns node -> its :ns node
      own   (vec (keep (fn [nd]
                         (when (and (not= "ns" (:kind nd)) (idx (:ns nd)))
                           [(idx (:id nd)) (idx (:ns nd))]))
                       nodes))
      files (->> roots (mapcat #(file-seq (io/file %))) (filter #(.isFile %))
                 (filter #(re-find #"\.cljc?$" (.getName %))) (remove excluded?))
      ;; file-dependency: ns -> required internal ns (only edges between existing nodes)
      fdep  (vec (mapcat (fn [f]
                           (let [nf (ns-form (read-forms f))
                                 src (and nf (idx (str (second nf))))]
                             (when src
                               (keep (fn [r] (when-let [d (idx r)]
                                               (when (not= d src) [src d])))
                                     (requires-of nf)))))
                         files))]
  (doseq [[s d] (concat own fdep)] (aset deg s (inc (aget deg s))) (aset deg d (inc (aget deg d))))
  (spit out (json/generate-string
              {:n n
               :own_edges own
               :fdep_edges fdep
               :degree (vec deg)
               :ns_idx (vec (keep-indexed (fn [i nd] (when (= "ns" (:kind nd)) i)) nodes))}))
  (println "nodes" n "ownership-edges" (count own) "file-dep-edges" (count fdep)
           "max-degree" (apply max (seq deg)) "->" out))
