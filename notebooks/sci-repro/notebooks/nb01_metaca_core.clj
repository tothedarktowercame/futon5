(ns notebooks.nb01-metaca-core
  (:require [scicloj.kindly.v4.kind :as kind]
            [scirepro.engine :as engine]
            [scirepro.report :as report]))

;; # MetaCA core reproduction: no-blending 1D dynamics
;;
;; This notebook reproduces the no-blending one-dimensional MetaCA
;; dynamics from arXiv:1502.00130v1 §3.1 with an independent Clojure
;; engine. It also records fixed-rule elementary CA baselines on the
;; same seeded initial conditions and checks the §5.3 statement that
;; blending is a bitwise censored Rule 23.

(def findings (report/report))

;; ## Figure 2-style no-blend random IC runs
;;
;; Each cell is a byte-valued local rule rendered as a 256-level
;; genotype palette. Initial conditions are generated from named seeds
;; and can be persisted under `resources/ics/`.

(kind/html
 (str "<div style=\"display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px\">"
      (apply str
             (for [{:keys [seed rows]} (:figure-runs findings)]
               (str "<figure style=\"margin:0\"><figcaption>seed " seed "</figcaption>"
                    (engine/grid->svg rows {:cell 3})
                    "</figure>")))
      "</div>"))

;; ## C1: measured time-to-stasis
;;
;; Stasis is defined as the first row identical to its predecessor.
;; Runs that do not reach this condition by the fixed horizon are
;; reported as censored.

(:stasis-summary findings)

;; ## Fixed-rule ECA baselines
;;
;; Baselines use the same genotype ICs, projected to bit position 0,
;; then evolved under fixed rules 0, 23, 30, 84, 90, 110, 128, and 184.

(mapv #(select-keys % [:rule :stasis-count :median-time]) (:baselines findings))

;; ## C7: blending as censored Rule 23
;;
;; Exhaustive enumeration over all 256 local rules and all 8 bit triples.

(:c7 findings)

;; ## A1-A3 resolved from `256ca.el`
;;
;; These are copied into the mission ledger with line references.

(:ambiguities findings)

