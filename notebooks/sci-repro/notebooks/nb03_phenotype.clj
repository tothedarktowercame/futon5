(ns notebooks.nb03-phenotype
  (:require [scicloj.kindly.v4.kind :as kind]
            [scirepro.engine :as engine]
            [scirepro.report :as report]))

;; # MetaCA phenotype coupling reproduction
;;
;; This notebook pins the Figure 4 pheno-geno layer semantics from
;; `256ca.el` and measures C3: phenotype behaviour follows locally
;; stabilised genotype rules.

(def findings (report/c3-report))

;; ## Figure 4-style coupled panels
;;
;; Genotype uses the 256-level palette; phenotype is black/white.

(kind/html
 (str "<div style=\"display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px\">"
      (apply str
             (for [{:keys [seed genotype phenotype]} (:figure-runs findings)]
               (str "<figure style=\"margin:0\"><figcaption>seed " seed " genotype</figcaption>"
                    (engine/grid->svg genotype {:cell 3})
                    "</figure>"
                    "<figure style=\"margin:0\"><figcaption>seed " seed " phenotype</figcaption>"
                    (engine/grid->svg phenotype {:cell 3 :palette report/binary-palette})
                    "</figure>")))
      "</div>"))

;; ## C3a: region conformance
;;
;; Frozen genotype regions are maximal same-rule horizontal segments
;; that remain unchanged for the measurement window. The phenotype in
;; the interior is compared with pure ECA-R evolution from the same
;; phenotype state.

(:conformance findings)

;; ## C3b: genotype/phenotype mutual information
;;
;; The null deterministically rotates phenotype rows before pairing,
;; preserving row composition while breaking local genotype alignment.

(:summary findings)

(kind/html
 (report/chart-svg [{:label "coupled MI"
                     :color "#b23b3b"
                     :points (mapv (fn [{:keys [t mi]}] {:t t :value mi}) (:mi findings))}
                    {:label "shuffled null"
                     :color "#555"
                     :points (mapv (fn [{:keys [t null]}] {:t t :value null}) (:mi findings))}
                    {:label "frozen genotype baseline"
                     :color "#1f6fb2"
                     :points (mapv (fn [{:keys [t mi]}] {:t t :value mi}) (:frozen-mi findings))}]
                   {:title "Mean genotype/phenotype mutual information"}))

;; ## A5 phenotype semantics
;;
;; `256ca.el` updates phenotype first from the old genotype and old
;; phenotype, then updates genotype. The phenotype rule for a cell is
;; the cell's own current genotype byte, applied to the phenotype
;; neighborhood with fixed-zero boundaries. Initial phenotype rows are
;; explicit seeded EDN artifacts under `resources/phenotype-ics/`.

{:a5 "old genotype + old phenotype -> new phenotype; old genotype -> new genotype via S3.2 blend"}

;; ## How to reproduce
;;
;; ```bash
;; cd /home/joe/code/futon5/notebooks/sci-repro
;; clojure -X:test
;; clojure -M -m scirepro.cross-check 120
;; clojure -M -m scirepro.render
;; ```

