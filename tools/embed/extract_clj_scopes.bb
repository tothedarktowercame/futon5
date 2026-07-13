#!/usr/bin/env bb
;; extract_clj_scopes.bb — O4(c) code-text embedding cache: Clojure :symbol/:namespace
;; scope-window extractor (native Clojure reader). claude-6 (E2). Canonical trees only.
;;
;; Emits [{id, ns, name, kind, text}] where text = the contextualized :symbol window
;; (ns-context + def-form + docstring) — the `ctx` form that beat `bare` in Pilot #1.
;; Usage: bb extract_clj_scopes.bb <out.json> <limit-or-0> <root> [root ...]
(require '[clojure.java.io :as io] '[cheshire.core :as json] '[clojure.string :as str])
(import 'java.io.PushbackReader)

(def DEFKINDS '#{defn defn- def defmacro defmethod defprotocol defmulti definline
                 defrecord deftype definterface defonce deftest})
(def EXCLUDE #{".venv" "node_modules" ".state" "target" ".git" ".cpcache" "resources"})

(defn excluded? [path]
  (let [s (str path)]
    (or (some #(str/includes? s (str "/" % "/")) EXCLUDE)
        (str/includes? s "~")                 ;; arxana worktree backups, e.g. file.clj~...~
        (re-find #"worktree|/origin/|\.orig" s))))

(defn read-forms [f]
  (try
    (with-open [r (PushbackReader. (io/reader f))]
      (loop [v []]
        (let [x (read {:eof ::eof :read-cond :allow} r)]
          (if (= x ::eof) v (recur (conj v x))))))
    (catch Throwable _ nil)))

(defn ns-form [forms]
  (some (fn [form] (when (and (seq? form) (= 'ns (first form))) form)) forms))

(defn ns-node [forms]
  ;; the :namespace grain node (E2 file-dependency band needs namespace vectors)
  (when-let [nf (ns-form forms)]
    (let [nsname (str (second nf))
          doc (some #(when (string? %) %) (take 3 (drop 2 nf)))]
      {:id nsname :ns nsname :name nsname :kind "ns"
       :text (str "namespace " nsname
                  (when doc (str "\n" (subs doc 0 (min 240 (count doc))))))})))

(defn first-string [coll] (some #(when (string? %) %) coll))
(defn first-vector [coll] (some #(when (vector? %) %) coll))

(defn scope-of [nsname form]
  (when (and (seq? form) (symbol? (first form)) (DEFKINDS (first form)) (symbol? (second form)))
    (let [kind (first form) nm (str (second form))
          rest3 (take 4 (drop 2 form))
          doc (or (first-string rest3) "")
          args (first-vector rest3)
          text (str "namespace " nsname "\n(" kind " " nm
                    (when args (str " " (pr-str args))) ")\n"
                    (when (seq doc) (subs doc 0 (min 240 (count doc)))))]
      {:id (str nsname "/" nm) :ns nsname :name nm :kind (str kind) :text text})))

(let [[out limit & roots] *command-line-args*
      limit (Integer/parseInt (or limit "0"))
      files (->> roots
                 (mapcat #(file-seq (io/file %)))
                 (filter #(.isFile %))
                 (filter #(re-find #"\.cljc?$" (.getName %)))
                 (remove excluded?))
      scopes (loop [fs files acc []]
               (if (or (empty? fs) (and (pos? limit) (>= (count acc) limit)))
                 acc
                 (let [forms (read-forms (first fs))
                       nf (and forms (ns-form forms))
                       nsname (if nf (str (second nf)) "?")
                       nn (when nf (ns-node forms))
                       ss (when forms (keep #(scope-of nsname %) forms))]
                   (recur (rest fs) (into acc (if nn (cons nn ss) ss))))))
      ;; dedupe by :id so the cache has unique :node/id keys (collapses same-name
      ;; defmethods + repeated ns nodes)
      scopes (second (reduce (fn [[seen out] x]
                               (if (seen (:id x)) [seen out]
                                   [(conj seen (:id x)) (conj out x)]))
                             [#{} []] scopes))
      scopes (if (pos? limit) (vec (take limit scopes)) (vec scopes))]
  (spit out (json/generate-string scopes))
  (println "extracted" (count scopes) "scopes from" (count files)
           "canonical .clj(c) files ->" out))
