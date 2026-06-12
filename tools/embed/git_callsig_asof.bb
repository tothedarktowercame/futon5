#!/usr/bin/env bb
;; git_callsig_asof.bb — #3 structural signal, AS-OF a commit (leak-free; git show, no checkout).
;; A namespace's CALL-SIGNATURE = the bag of head-position symbols (functions/macros it INVOKES)
;; in its function bodies — what the code DOES, distinct from its name. claude-6 (E2).
;; Usage: bb git_callsig_asof.bb <commit> <repo> <out.json>
(require '[clojure.java.shell :refer [sh]] '[clojure.string :as str] '[cheshire.core :as json])

(defn git [repo & args] (:out (apply sh "git" (concat args [:dir repo]))))

(defn clj-files [repo commit]
  (->> (str/split-lines (git repo "ls-tree" "-r" "--name-only" commit))
       (filter #(re-find #"^(src|dev)/.*\.cljc?$" %))
       (remove #(re-find #"/test/|_test\.|/\.venv/" %))))

(defn ns-block-end [content]
  (when-let [i (str/index-of content "(ns ")]
    (loop [j i depth 0]
      (if (>= j (count content)) (count content)
          (let [c (nth content j)
                d (cond (= c \() (inc depth) (= c \)) (dec depth) :else depth)]
            (if (and (= c \)) (zero? d)) (inc j) (recur (inc j) d)))))))

(let [[commit repo out] *command-line-args*
      sigs (into {}
                 (for [f (clj-files repo commit)
                       :let [content (git repo "show" (str commit ":" f))
                             own (second (re-find #"\(ns\s+([a-z0-9.*+!?<>=_-]+)" content))
                             body (subs content (or (ns-block-end content) 0))
                             ;; head-of-list symbols = invoked fns/macros (the operations)
                             heads (map second (re-seq #"\(\s*([a-z][a-zA-Z0-9.*+!?<>=/_-]+)" body))
                             ;; drop bare def-forms (structure, not operations) + 1-char noise
                             heads (remove #(or (#{"defn" "defn-" "def" "defmacro" "let" "fn" "if"
                                                   "when" "do" "ns" "comment"} %)
                                                (< (count %) 2)) heads)]
                       :when own]
                   [own (frequencies heads)]))]
  (spit out (json/generate-string sigs))
  (println "call-signatures for" (count sigs) "namespaces @" commit "->" out))
