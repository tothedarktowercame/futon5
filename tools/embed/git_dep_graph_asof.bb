#!/usr/bin/env bb
;; git_dep_graph_asof.bb — #3 temporal ground truth: reconstruct the ns-dependency graph
;; AS-OF a commit, NON-DISRUPTIVELY (git show, NO checkout — futon3c is a live shared repo).
;; Emits the snapshot graph at <old-commit> + the current (HEAD) graph + the FUTURE edges
;; (added since old). claude-6 (E2). Usage: bb git_dep_graph_asof.bb <old-commit> <repo> <out.json>
(require '[clojure.java.shell :refer [sh]] '[clojure.string :as str]
         '[cheshire.core :as json])

(defn git [repo & args] (:out (apply sh "git" (concat args [:dir repo]))))

(defn clj-files [repo commit]
  (->> (str/split-lines (git repo "ls-tree" "-r" "--name-only" commit))
       (filter #(re-find #"^(src|dev)/.*\.cljc?$" %))
       (remove #(re-find #"/test/|_test\.|/\.venv/" %))))

(defn ns-block [content]
  ;; substring of the (ns ...) form via paren-balance (robust to reader macros)
  (when-let [i (str/index-of content "(ns ")]
    (loop [j i depth 0]
      (if (>= j (count content)) (subs content i)
          (let [c (nth content j)
                d (cond (= c \() (inc depth) (= c \)) (dec depth) :else depth)]
            (if (and (= c \)) (zero? d)) (subs content i (inc j)) (recur (inc j) d)))))))

(defn graph-asof [repo commit]
  (let [files (clj-files repo commit)
        parsed (for [f files
                     :let [content (git repo "show" (str commit ":" f))
                           own (second (re-find #"\(ns\s+([a-z0-9.*+!?<>=_-]+)" content))
                           blk (or (ns-block content) "")
                           reqs (map second (re-seq #"\[\s*([a-z][a-z0-9.*+!?<>=_-]+)" blk))]
                     :when own]
                 [own (set reqs)])
        nodes (set (map first parsed))
        edges (set (for [[own reqs] parsed r reqs :when (and (nodes r) (not= own r))]
                     (vec (sort [own r]))))]
    {:commit commit :nodes nodes :edges edges
     :date (str/trim (git repo "log" "-1" "--format=%ad" "--date=short" commit))}))

(let [[old repo out] *command-line-args*
      g-old (graph-asof repo old)
      g-new (graph-asof repo "HEAD")
      e-old (set (map vec (:edges g-old)))
      e-new (set (map vec (:edges g-new)))
      future (vec (remove e-old e-new))]
  (println (format "OLD %s (%s): %d nodes, %d edges" old (:date g-old)
                   (count (:nodes g-old)) (count e-old)))
  (println (format "HEAD (%s): %d nodes, %d edges" (:date g-new)
                   (count (:nodes g-new)) (count e-new)))
  (println (format "FUTURE edges added since OLD: %d" (count future)))
  (spit out (json/generate-string
              {:snapshot {:commit old :date (:date g-old)
                          :nodes (vec (:nodes g-old)) :edges (vec e-old)}
               :future_edges future
               :head {:date (:date g-new) :n_edges (count e-new)}}))
  (println "wrote" out))
