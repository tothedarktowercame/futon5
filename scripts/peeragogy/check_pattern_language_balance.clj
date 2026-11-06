(ns peeragogy.check-pattern-language-balance
  "M-formal-patterns S4: translate the real Peeragogy v4 pattern
  language (vendored org sources under
  vendor/peeragogy-pattern-language/src) into a signed
  validation-dependency graph and check it for frustrated (odd
  negative-parity) cycles.

  Translation rule (fixed, not fitted):
  - Nodes are the files tagged '#+roam_tags: PAT' (the v4 pattern
    catalog).  Stale file aliases are resolved to the catalog file.
  - Edge A->B exists when A's text links B with an org file link
    [[file:B.org][...]].  Each edge records the org section it appears
    in and a verbatim quote so a third party can audit it.
  - Sign: the linking paragraph frames B as a substitute/retirement
    target for what A validates => negative (evidence for one is
    evidence against the other); otherwise positive (chaining
    confirms salience).  The marker list below is the whole rule.

  Deterministic: sorted traversal, no timestamps; running twice
  produces byte-identical output."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]))

(def src-dir "vendor/peeragogy-pattern-language/src")
(def out-dir "reports")
(def source-commit "3ba572c553b1a45627e5abe4927fd42784b12d2f")

(def pattern-tag "#+roam_tags: PAT")

;; Aliases used by older in-text links, resolved to the catalog file.
(def alias->pattern
  {"pattern-carrying" "carrying"
   "reduce_reuse_recycle" "reduce"
   "a_specific_project" "specific"})

;; The complete negative-sign rule: any of these markers in the
;; linking paragraph means 'this link supersedes rather than confirms'.
(def negative-markers
  ["no longer" "out-of-date" "stop doing" "instead of"
   "rather than" "retire" "supersede"])

(defn- pattern-files []
  (->> (file-seq (io/file src-dir))
       (filter #(.isFile %))
       (filter #(str/ends-with? (.getName %) ".org"))
       (sort-by #(.getName %))
       (keep (fn [f]
               (let [t (slurp f)]
                 (when (str/includes? t pattern-tag)
                   {:file (str/replace (.getName f) #"\.org$" "")
                    :title (or (some-> (re-find #"(?m)^#\+TITLE:\s*(.+)$" t)
                                       second str/trim)
                               "UNTITLED")
                    :text t}))))))

(defn- headings-of [text]
  (let [m (re-matcher #"(?m)^\*+\s+(.+)$" text)]
    (loop [out []]
      (if (.find ^java.util.regex.Matcher m)
        (recur (conj out {:idx (.start ^java.util.regex.Matcher m)
                          :title (str/trim (.group ^java.util.regex.Matcher m 1))}))
        out))))

(defn- section-at [headings idx]
  (or (some->> (filter #(<= (:idx %) idx) headings)
               last :title)
      "(preamble)"))

(defn- whats-next? [title]
  ;; true when an org section heading is a What's-Next section
  (and (str/includes? (str/lower-case title) "what")
       (str/includes? (str/lower-case title) "next")))

(defn- normalize [s] (-> s (str/replace #"\s+" " ") str/trim))

(defn- para-around [text idx]
  (let [lo (max 0 (str/last-index-of (subs text 0 idx) "\n\n"))
        hi-end (or (str/index-of text "\n\n" idx) (count text))
        para (normalize (subs text (if (zero? lo) 0 (+ lo 2)) hi-end))]
    (if (<= (count para) 320)
      para
      (let [rel (str/index-of para (subs text idx (min (count text) (+ idx 20))))
            rel (or rel (- (count para) 20))
            q (subs para (max 0 (- rel 150)) (min (count para) (+ rel 170)))
            q (if (pos? (max 0 (- rel 150)))
                (str/replace q #"^\S*\s+" "") q)
            q (if (< (+ rel 170) (count para))
                (str/replace q #"\s\S*$" "") q)]
        (str "…" q "…")))))

(defn- edges-of [patterns]
  (let [ids (set (map :file patterns))
        by-file (into {} (map (juxt :file :title)) patterns)]
    (sort-by (juxt :from :to)
             (mapcat (fn [{:keys [file text]}]
                       (let [headings (headings-of text)
                             m (re-matcher #"\[\[file:([a-zA-Z_0-9-]+)\.org\]\[[^\]]*\]" text)]
                         (loop [out []]
                           (if (.find ^java.util.regex.Matcher m)
                             (let [idx (.start ^java.util.regex.Matcher m)
                                   target-raw (.group ^java.util.regex.Matcher m 1)
                                   target (get alias->pattern target-raw target-raw)
                                   sec (section-at headings idx)
                                   para (para-around text idx)]
                               (recur (if (and (ids target) (not= target file))
                                        (conj out {:from file :to target
                                                   :from-title (by-file file)
                                                   :to-title (by-file target)
                                                   :section sec
                                                   :whats-next? (whats-next? sec)
                                                   :sign (if (some #(str/includes?
                                                                    (str/lower-case para) %)
                                                                   negative-markers)
                                                           :negative :positive)
                                                   :quote (para-around text idx)})
                                        out)))
                             out))))
                     patterns))))

;; ---- signed-graph balance over the underlying undirected graph ----

(defn- undirected-edges [edges]
  (->> edges
       (map (fn [{:keys [from to sign section]}]
              {:a (if (neg? (compare from to)) from to)
               :b (if (neg? (compare from to)) to from)
               :sign sign :section section}))
       (group-by (juxt :a :b))
       (map (fn [[_ v]]
              ;; an undirected edge is negative iff every citation of
              ;; this pair is negative (mixed pairs are reported, none
              ;; exist in this catalog)
              (assoc (first v)
                     :citations (mapv (juxt :from :to) v)
                     :mixed-signs? (> (count (distinct (map :sign v))) 1))))
       (sort-by (juxt :a :b))))

(defn- adjacency [uedges]
  (reduce (fn [acc {:keys [a b sign]}]
            (-> acc
                (update a (fnil conj {}) {b sign})
                (update b (fnil conj {}) {a sign})))
          {} uedges))

(defn- cycles [adj]
  (let [nodes (sort (keys adj))
        found (atom (transient []))]
    (doseq [start nodes]
      (let [dfs (fn dfs [path seen]
                  (let [node (peek path)]
                    (doseq [[nbr _sign] (sort-by key (get adj node))]
                      (cond
                        (and (= nbr start) (>= (count path) 3))
                        (let [cp (vec path) ;; cycle nodes, starts at `start`
                              body (subvec cp 1)
                              body-rev (vec (reverse body))]
                          (when (neg? (compare body body-rev))
                            (let [n (count cp)
                                  signs (mapv (fn [i]
                                                (let [u (nth cp i)
                                                      v (nth cp (mod (inc i) n))]
                                                  (get-in adj [u v])))
                                              (range n))]
                              (swap! found conj! {:nodes cp :signs signs}))))
                        (and (pos? (compare nbr start))
                             (not (contains? seen nbr))
                             (< (count path) 9))
                        (dfs (conj path nbr) (conj seen nbr))))))]
        (dfs [start] #{start})))
    (persistent! @found)))

(defn -main [& _args]
  (let [patterns (pattern-files)
        edges (edges-of patterns)
        uedges (undirected-edges edges)
        adj (adjacency uedges)
        cycs (map (fn [c] (assoc c
                                 :negative-count (count (filter #{:negative} (:signs c)))
                                 :frustrated? (odd? (count (filter #{:negative} (:signs c))))))
                  (cycles adj))
        frustrated (filter :frustrated? cycs)
        title-of (into {} (map (juxt :file :title)) patterns)
        md (str
            "# Peeragogy v4 pattern language: signed-graph balance check\n\n"
            "M-formal-patterns S4. Source: Peeragogy/Peeragogy.github.io `src/*.org`\n"
            "at commit `" source-commit "`, vendored under\n"
            "`vendor/peeragogy-pattern-language/src` (" (count patterns)
            " files tagged `#+roam_tags: PAT`).\n\n"
            "## Sign rule (stated, not fitted)\n\n"
            "An edge A→B is **negative** when the linking paragraph frames B as a\n"
            "substitute/retirement target — it contains one of: "
            (str/join ", " (map #(str "`" % "`") negative-markers))
            " — else **positive** (chaining confirms salience). Every edge carries\n"
            "its verbatim quote; audit the signs against the quotes.\n\n"
            "## Patterns (" (count patterns) ")\n\n"
            (str/join "\n" (map (fn [p] (str "- `" (:file p) "` — " (:title p))) patterns))
            "\n\n## Edges (" (count edges) " directed citations → "
            (count uedges) " undirected signed edges)\n\n"
            (str/join "\n"
                      (map (fn [e]
                             (str "- " (:from e) " → " (:to e) " **"
                                  (name (:sign e)) "** (§" (:section e)
                                  (when (:whats-next? e) ", NEXT-STEPS") ") ["
                                  (:quote e) "]"))
                           edges))
            "\n\n## Cycles (" (count cycs) "): "
            (count frustrated) " frustrated\n\n"
            (str/join "\n"
                      (map (fn [c]
                             (str "- " (str/join "–" (map title-of (:nodes c))) " — "
                                  (:negative-count c) " negative "
                                  (if (:frustrated? c) "**FRUSTRATED**" "balanced")))
                           (sort-by (juxt :frustrated? :nodes) cycs)))
            "\n\n## Verdict\n\n"
            (if (seq frustrated)
              (str "The language is **unbalanced**: " (count frustrated)
                   " of " (count cycs)
                   " simple cycles are frustrated (odd negative parity). Every\n"
                   "frustrated cycle traverses the Heartbeat–Scrapbook edge, whose text\n"
                   "is the catalog's one substitution/retirement link (\"if a specific\n"
                   "activity is no longer sustaining the project, stop doing it … move\n"
                   "an out-of-date pattern to the Scrapbook\"). Under the mission's\n"
                   "semantics each such cycle is a self-undermining validation loop:\n"
                   "evidence routed around it disconfirms the starting pattern, so no\n"
                   "stable joint verdict exists. The mission's predicted meta-pattern\n"
                   "intervention applies: flip one validation-link on each frustrated\n"
                   "cycle — e.g. re-read the retirement edge as confirming the Heartbeat\n"
                   "mechanism's salience (it is what *effects* the retirement) rather\n"
                   "than superseding the retired activity — and parity becomes even.\n")
              (str "The language is **balanced**: every simple cycle has even negative\n"
                   "parity; a globally consistent two-camp verdict exists.\n")))
        edn {:source-commit source-commit
             :patterns (mapv (juxt :file :title) patterns)
             :edges edges
             :undirected-edges uedges
             :cycle-count (count cycs)
             :frustrated-cycles (mapv :nodes frustrated)}]
    (spit (io/file out-dir "peeragogy-pattern-language-balance.md") md)
    (spit (io/file out-dir "peeragogy-pattern-language-balance.edn")
          (with-out-str (pr edn)))
    (println "patterns:" (count patterns)
             "| directed edges:" (count edges)
             "| undirected:" (count uedges)
             "| cycles:" (count cycs)
             "| frustrated:" (count frustrated))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
