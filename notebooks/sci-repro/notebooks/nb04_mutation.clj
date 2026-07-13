(ns notebooks.nb04-mutation
  (:require [scicloj.kindly.v4.kind :as kind]
            [scirepro.engine :as engine]
            [scirepro.report :as report]))

;; # MetaCA mutation reproduction — C4–C6
;;
;; This notebook pins the mutation semantics from `256ca.el` (A6, resolved
;; slice 4a) and measures three claims:
;;
;; - **C4**: High mutation → confetti; throttled → stability + intermingling
;;   swaths. Measured as rule-diversity (entropy) and change-rate over a
;;   mutation-rate sweep, against a no-mutation control AND a uniform-random-
;;   replacement null.
;; - **C5**: Rule-0/255-like genotypes predominate; Rule-110-variant patches
;;   transient. Measured as popcount-class frequencies over time and patch-
;;   lifetime statistics for flagged rules.
;; - **C6**: First-bit-only mutation → flutter between Rule 0 and Rule 128.
;;   Measured as occupancy of {0,128} over time on coupled runs.
;;
;; **Scope note (A7):** the elisp's default `evolve-sigil-fn`
;; (`evolve-sigil-with-mutating-template`, 256ca.el:1069) pairs balance-mutation
;; with the context-driven template dynamic. This notebook pairs balance-mutation
;; with the **blend** dynamic (the deterministic S3.2 blend already cross-checked
;; in slices 1–2) for the C4/C5 panels, labeled "balance-mutation on blend dynamic".
;; The template dynamic itself is out of scope for this slice (see A7). The
;; uniform-stream mutation panels (rate sweep) use the 4a cross-checked
;; `evolve-with-mutation` (blend + injected uniform stream).

(def c4 (report/c4-report))

;; ## C4: mutation-rate sweep
;;
;; Rate 1.0 = the elisp unconditional variant (every cell flips one bit every
;; generation — the "confetti" candidate). Rate 0 = no mutation. The
;; uniform-random-replacement null replaces a cell's rule with a fresh uniform
;; byte at the same 5% event rate.

(kind/html
 (report/chart-svg [{:label "rate 1.0"
                     :color "#d62728"
                     :points (:uniform-rate-1.0-entropy (:curves c4))}
                    {:label "rate 0.1"
                     :color "#ff7f0e"
                     :points (:uniform-rate-0.1-entropy (:curves c4))}
                    {:label "rate 0.01"
                     :color "#2ca02c"
                     :points (:uniform-rate-0.01-entropy (:curves c4))}
                    {:label "balance-mut"
                     :color "#1f77b4"
                     :points (:balance-entropy (:curves c4))}
                    {:label "no-mutation"
                     :color "#888"
                     :points (:no-mutation-entropy (:curves c4))}
                    {:label "random-replace"
                     :color "#9467bd"
                     :points (:random-replace-entropy (:curves c4))}]
                   {:title "Rule-distribution entropy over time (mutation sweep)"
                    :y-max 8.0}))

(kind/html
 (report/chart-svg [{:label "rate 1.0"
                     :color "#d62728"
                     :points (:uniform-rate-1.0-change-rate (:curves c4))}
                    {:label "balance-mut"
                     :color "#1f77b4"
                     :points (:balance-change-rate (:curves c4))}
                    {:label "no-mutation"
                     :color "#888"
                     :points (:no-mutation-change-rate (:curves c4))}
                    {:label "random-replace"
                     :color "#9467bd"
                     :points (:random-replace-change-rate (:curves c4))}]
                   {:title "Change-rate over time (mutation modes)"}))

;; ### C4 headline

(:summary c4)

;; **Interpretation:** Rate 1.0 (the elisp unconditional variant) sustains
;; high entropy — this is the "confetti" regime. The balance-mutation variant
;; (5% gate, homeostatic) allows diversity to decay partially but does not
;; reach the rapid stasis of no-mutation. The random-replacement null shows
;; that generic noise at the same event rate produces different dynamics than
;; structured (bit-flip) mutation — distinguishing the two is the point of
;; the null.

;; ### C4 visual panels: rate 1.0 vs balance-mutation vs no-mutation

(kind/html
 (str "<div style=\"display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px\">"
      (apply str
             (for [{:keys [seed rows]} (take 4 (get (:uniform-sweep c4) 1.0))]
               (str "<figure style=\"margin:0\"><figcaption>rate 1.0 seed " seed "</figcaption>"
                    (engine/grid->svg rows {:cell 3})
                    "</figure>")))
      "</div>"))

(kind/html
 (str "<div style=\"display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px\">"
      (apply str
             (for [{:keys [seed rows]} (take 4 (:balance-blend-runs c4))]
               (str "<figure style=\"margin:0\"><figcaption>balance-mut seed " seed "</figcaption>"
                    (engine/grid->svg rows {:cell 3})
                    "</figure>")))
      "</div>"))

;; ## C5: popcount-class dynamics

(def c5 (report/c5-report))

;; The paper's greyscale coding maps rules by their number of 1-bits. Rule 0
;; (all zeros) and Rule 255 (all ones) are the "extreme" classes. We track
;; the mean popcount-class histogram over time for both uniform-stream and
;; balance-mutation runs.

(:patch-lifetimes c5)

;; ### C5 visual: popcount evolution (uniform rate 0.1)

(kind/html
 (str "<div style=\"display:grid;grid-template-columns:repeat(1,minmax(0,1fr));gap:16px\">"
      (apply str
             (for [{:keys [seed rows]} (take 2 (report/mutation-runs :uniform 0.1 report/c4-steps (take 2 report/c4-seeds)))]
               (str "<figure style=\"margin:0\"><figcaption>uniform rate 0.1 seed " seed "</figcaption>"
                    (engine/grid->svg rows {:cell 3})
                    "</figure>")))
      "</div>"))

;; **Interpretation:** Flagged-rule patches (110/30/90/184 family) are transient
;; — their lifetimes are short relative to the run horizon. The popcount
;; distribution concentrates toward the extremes (0 and 8 ones) over time,
;; matching the paper's observation that Rule-0/255-like genotypes predominate.

;; ## C6: first-bit-only mutation on coupled runs

(def c6 (report/c6-report))

;; The Figure-8 "erroneously-programmed mutation" that "only ever flips the
;; first bit" has no elisp implementation (A6 finding). Here we measure its
;; effect: does first-bit-only mutation drive the system toward Rule 0
;; (00000000) and Rule 128 (10000000)?

(kind/html
 (report/chart-svg [{:label "Rule 0 + 128 occupancy"
                     :color "#d62728"
                     :points (mapv (fn [{:keys [t rule-0-or-128-frac]}]
                                     {:t t :value rule-0-or-128-frac})
                                   (:occupancy c6))}
                    {:label "Rule 0 only"
                     :color "#1f77b4"
                     :points (mapv (fn [{:keys [t rule-0-frac]}]
                                     {:t t :value rule-0-frac})
                                   (:occupancy c6))}
                    {:label "Rule 128 only"
                     :color "#2ca02c"
                     :points (mapv (fn [{:keys [t rule-128-frac]}]
                                     {:t t :value rule-128-frac})
                                   (:occupancy c6))}]
                   {:title "{0,128} occupancy over time (first-bit-only mutation)"
                    :y-max 1.0}))

(kind/html
 (report/chart-svg [{:label "phenotype entropy"
                     :color "#1f77b4"
                     :points (:pheno-entropy c6)}
                    {:label "phenotype change-rate"
                     :color "#d62728"
                     :points (:pheno-change-rate c6)}]
                   {:title "Phenotype transient under first-bit-only genotype mutation"
                    :y-max 1.0}))

;; ### C6 headline

(:summary c6)

;; ### C6 visual: genotype + phenotype panels

(kind/html
 (str "<div style=\"display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px\">"
      (apply str
             (for [{:keys [seed genotype phenotype]} (take 3 (:runs c6))]
               (str "<figure style=\"margin:0\"><figcaption>seed " seed " genotype (first-bit mut)</figcaption>"
                    (engine/grid->svg genotype {:cell 3})
                    "</figure>"
                    "<figure style=\"margin:0\"><figcaption>seed " seed " phenotype</figcaption>"
                    (engine/grid->svg phenotype {:cell 3 :palette report/binary-palette})
                    "</figure>")))
      "</div>"))

;; ## A6 mutation semantics (resolved slice 4a)
;;
;; The elisp has three mutation-bearing evolve-sigil variants:
;; 1. `evolve-sigil-with-blending-mutation` (256ca.el:595-627): blend step then
;;    unconditional one-bit flip per cell (rate 1.0).
;; 2. `evolve-sigil-with-mutating-template` (256ca.el:990-1065): context template
;;    + balance-mutation (5% gate, homeostatic).
;; 3. `evolve-sigil-with-blending-baldwin` (256ca.el:636-686): Baldwin-effect variant.
;;
;; Figure-8 first-bit-only is prose-only (no code).

{:a6 "Three variants; cross-checked #1 (injected stream, rate 1.0) and #2 (balance, injected stream) against elisp."
 :a7 "See below."}

;; ## A7: which elisp variant produced Figures 5–8?
;;
;; **Unresolvable from code.** The elisp has a single commit (`92fa793`, initial
;; commit) with no figure-generation trace. No comments or docstrings link
;; specific functions to specific figures. The `multiverse-files*` functions
;; (256ca.el:1444-1497) are the batch generators, but they call
;; `print-space-time` through `print-space-time-4`, all of which use
;; `run-for-generations` or `run-for-generations-3`, all of which use
;; `evolve-sigil-fn` (the global alias at 256ca.el:1069 = the default
;; `evolve-sigil-with-mutating-template`). So the DEFAULT dynamic for ALL batch
;; generation is template + balance-mutation. However, the operator could have
;; re-bound `evolve-sigil-fn` interactively before running the batch (the code
;; is designed for this), and there is no record of what binding was active
;; when the paper's figures were generated.

{:a7 "Unresolvable from code: single commit, no figure traces. All batch generators use the default evolve-sigil-fn = template + balance-mutation, but the operator could re-bind it interactively. This notebook pairs balance-mutation with blend dynamic (labeled accordingly) because the template dynamic is out of scope."}

;; ## How to reproduce this notebook
;;
;; From `futon5/notebooks/sci-repro/` (deps pinned in `deps.edn`, Clay 2-beta23):
;;
;; ```
;; clojure -X:test                              ; engine unit tests
;; clojure -M -m scirepro.cross-check 120       ; grid-identity vs 256ca.el, deterministic dynamics
;; clojure -M -m scirepro.mutation-cross-check 120  ; mutation (rate 1.0) cross-check
;; clojure -M -m scirepro.balance-cross-check 60    ; balance-mutation cross-check
;; clojure -M -m scirepro.render                ; re-render all notebooks into out/
;; ```
;;
;; ICs are explicit EDN artifacts under `resources/ics/` and
;; `resources/phenotype-ics/`. Mutation streams are seeded (java.util.Random,
;; seed in filename or in the notebook). Ground truth is `futon5/256ca.el`.
;; Claims table, ambiguity ledger (A1-A7), and per-slice checkpoints:
;; `futon5/holes/missions/M-sci-reproduction.md`.
