(ns notebooks.mmca-supplement1
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [scicloj.kindly.v4.kind :as kind]))

;; # Supplement 1: Empirical Findings Notebook
;;
;; This is the literate-notebook view of the empirical findings accompanying
;; *Rule-Rewriting Cellular Automata and the Edge of Chaos*. The journal PDF and
;; this page share one canonical source:
;; `holes/tech-notes/paper/supplement1-findings.tex`.
;;
;; Mathematical definitions, protocols, figures, and interpretation remain in
;; the paper. Stable citations of the form “Supplement 1, Box n” refer to the
;; numbered findings rendered below.

(def source-path
  (io/file "../../holes/tech-notes/paper/supplement1-findings.tex"))

(def source
  (slurp source-path))

(def finding-pattern
  #"(?s)\\begin\{empiricalfinding\}\[label=\{([^}]+)\}\]\{([^}]+)\}\s*(.*?)\s*\\end\{empiricalfinding\}")

(def findings
  (mapv (fn [number [_ label title body]]
          {:number number :label label :title title :body body})
        (iterate inc 1)
        (re-seq finding-pattern source)))

(defn tex->markdown [s]
  (-> s
      (str/replace #"\\begin\{itemize\}[^\n]*" "")
      (str/replace #"\\end\{itemize\}" "")
      (str/replace #"\\item\s+" "- ")
      (str/replace #"\\emph\{([^{}]+)\}" "*$1*")
      (str/replace #"\\textbf\{([^{}]+)\}" "**$1**")
      (str/replace #"\\parencite\{([^}]+)\}" "[$1]")
      (str/replace #"\\(?:ref|eqref)\{([^}]+)\}" "`$1`")
      (str/replace #"\\sig\b" "\\sigma")
      (str/replace #"---" "—")
      (str/replace #"~" " ")))

(kind/md
 (str
  "> **Source contract.** There are " (count findings)
  " boxes in the canonical source. The PDF and this notebook preserve their "
  "order and identifiers; edits belong in `supplement1-findings.tex`.\n\n"
  (str/join
   "\n\n"
   (for [{:keys [number label title body]} findings]
     (str "## Box " number ": " title "\n\n"
          "Stable identifier: `" label "`\n\n"
          (tex->markdown body))))))

;; ## Reproduce
;;
;; From `futon5/notebooks/sci-repro/`:
;;
;; ```
;; clojure -M -m scirepro.render-supplement1
;; ```
;;
;; The pinned Clay dependency is recorded in `deps.edn`. For the journal PDF,
;; run `latexmk -pdf supplement1.tex` from `holes/tech-notes/paper/`.
