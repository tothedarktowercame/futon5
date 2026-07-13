# M-sci-reproduction — reproduce the 1D experiments of "The Search for Computational Intelligence" (arXiv:1502.00130)

- **Status:** IDENTIFY → slice 1 dispatched (2026-07-13)
- **Owner:** claude-6 (review + architecture); slices belled to Codex
- **Operator:** Joe
- **Motive (Joe, 2026-07-13):** "a lot of this comes down to methods — computational
  reproducibility and all that... go back to my original paper, reproduce the
  experiments, and build up from there, with clear baselines." futon5 is "home to a
  lot of bright ideas that don't go anywhere, possibly because the search has been
  very aleatoric rather than rigorous. This is basically a *computational* version
  of my Mount Analogue problem."

## Sources of truth

1. **The paper:** Corneli & Maclean, *The Search for Computational Intelligence*,
   arXiv:1502.00130v1 (2015). Local copy: `~/Downloads/1502.00130v1.pdf`.
2. **The original implementation:** `futon5/256ca.el` (Emacs Lisp, 2014; the
   "working code on Github" = holtzermann17/metaca referenced in §3.2 fn 3).
   Where paper prose and code disagree, **the code is ground truth** and the
   discrepancy is logged as a finding.

## Scope

**In:** the 1D experiments (§3.1–3.2, §4.1, §5.3) as browser-viewable Clay
notebooks with quantified claims and explicit baselines.
**Out:** 2D experiments (§3.3, §4.2) — except that the 2D *blend menagerie*
(union/intersection/average, §4.2.1) is recorded as a follow-on idea: reuse
blends as **crossover operators at the meta/controller level** (Joe, 2026-07-13).

## Known ambiguities to resolve against 256ca.el (methods findings ledger)

- **A1. Bit-order convention.** §3.1 lists rule "01010100" with neighborhoods in
  ascending order (000→111), but calls it "Rule 84 in Wolfram's standard
  enumeration" — ascending-order reading gives Wolfram 42; descending gives 84.
  **Resolved slice 1:** `256ca.el` is ground truth and uses the custom
  `truth-table-3` order `000,001,010,100,011,101,110,111`
  (`256ca.el:108`). `evolve-sigil` zips that order with the central rule's
  left-to-right byte string (`256ca.el:435-448`), and
  `evolve-digits-by-rule` uses the same table position lookup
  (`256ca.el:1164-1170`). The independent Clojure engine follows this order,
  not Wolfram descending order.
- **A2. Boundary conditions.** The paper never states wrap-around vs fixed
  edges. **Resolved slice 1:** one-dimensional string evolution uses fixed
  zero boundary neighbors, represented by sigil `一`: the head is evolved with
  predecessor `一` (`256ca.el:1087-1089`) and the tail with next `一`
  (`256ca.el:1090-1093`). The lower-level `evolve-sigil` default also uses
  `一` for missing predecessor/next (`256ca.el:430-434`).
- **A3. Blending tie-breaking / update order.** §3.2's generic-space procedure:
  **Resolved slice 1:** blending is deterministic and per-allele. If left and
  right neighbor bits are both `0`, output `0`; if both are `1`, output `1`;
  otherwise fall back to the same central-rule lookup as no-blend
  (`256ca.el:453-490`). There is no random tie-break and no second update pass.
- **A4. §5.3 "Rule 23" is convention-dependent.** (Found in slice-1 review.)
  The censored-rule identity — blending = Rule 23 on neighbor-match rows,
  local logic elsewhere — is TRUE with Rule 23's byte read in the `256ca.el`
  truth-table-3 order (8192/8192 cases) and FALSE under Wolfram's descending
  order (4096/8192). Same finding family as A1: the paper's rule labels are
  correct in the code's convention, not Wolfram's. Also: the slice-1 C7 check
  as first committed was circular (it compared the blending expression to an
  inlined copy of itself and never referenced Rule 23); replaced in review
  with the non-circular enumeration in `scirepro.engine`.
- (Add further findings here as they surface.)

## Claims table — every §4.1/§5 assertion becomes a measured proposition

| # | Paper claim (qualitative) | Measured form | Fig |
|---|---|---|---|
| C1 | Without blending, random ICs stabilise early into barcode-like stripes | time-to-stasis distribution over ≥30 seeds; terminal per-column constancy | 2 |
| C2 | Blending yields an interesting initial period before stable bands | transient length (blend) ≫ transient length (no-blend), same seeds; entropy/change-rate curves | 1, 4 |
| C3 | Phenotype behaviour follows genotype in coupled runs | per-region mutual information genotype↔phenotype; local stabilisation to recognisable rules | 4 |
| C4 | High mutation → confetti; throttled → stability + intermingling swaths | diversity + change-rate vs mutation rate sweep | 5–6 |
| C5 | Rule-0/255-like genotypes predominate; Rule-110-variant patches transient | rule-frequency by popcount over time; patch lifetime stats for flagged rules (110/30/90/184 + reversals/inverses) | 7 |
| C6 | First-bit-only mutation → flutter between Rule 0 and Rule 128, lifelike short-term phenotype | occupancy of {0,128} over time; phenotype transient metrics | 8 |
| C7 | The blending rule is bitwise a "censored Rule 23" (§5.3) | **provable by exhaustive enumeration** — notebook proof over all 256 rules × 8 triples | — |

Baselines for every panel: fixed-rule elementary CAs (0, 23, 30, 84, 90, 110,
128, 184) on the *same* initial conditions, same metrics. The paper's own
stated limitation — "our results are purely observational" (§5.1) — is the
thing this mission fixes.

## Reproducibility gates

- Pinned deps (Clay `2-beta23` pattern from `futon5a/analysis/notebooks/deps.edn`).
- All ICs explicit, stored as EDN artifacts; no hidden RNG (splittable seeded
  RNG; seeds recorded in the notebook and in artifact filenames).
- **Cross-implementation check:** deterministic runs (no mutation) must be
  grid-identical between `256ca.el` and the new Clojure engine on shared
  explicit ICs, for both plain-multiplication and blending dynamics.
  (Precedent: the Rule 90/110 byte-identical wiring verification, HISTORY.md.)
- Rendered HTML checked into `futon5/notebooks/sci-repro/out/` (or published);
  viewable in a browser with no toolchain.
- Gates per AGENTS.md: clj-kondo clean; `futon4/dev/check-parens.el` on all
  Lisp/Clojure; unit tests for the engine (incl. the §3.1 worked example once
  A1 is resolved).

## Notebook plan

1. `nb01_metaca_core.clj` — the multiplication operator, no-blend dynamics;
   C1 + C7; ECA baselines; A1–A3 resolution + elisp cross-check. **(slice 1)**
2. `nb02_blending.clj` — generic-space blend; C2; blend-vs-no-blend measured.
3. `nb03_phenotype.clj` — coupled genotype/phenotype runs; C3.
4. `nb04_mutation.clj` — mutation sweep + skewed mutation; C4–C6.

## Checkpoints

### Slice 1 — nb01 core MetaCA + baselines + elisp cross-check (2026-07-13)

- Added standalone project `notebooks/sci-repro/` with pinned Clay 2-beta23
  deps, an independent `scirepro.engine`, deterministic IC artifacts under
  `resources/ics/`, an elisp cross-check harness, and
  `notebooks/nb01_metaca_core.clj`.
- Implemented no-blending multiplication and the S3.2 blending variant without
  reusing `futon5/src/futon5/**`.
- A1-A3 were resolved above from `256ca.el`; the paper/code discrepancy for
  the S3.1 worked example is handled by treating code as ground truth:
  `01101110 x 01010100 x 01010101 -> 00101011` under the elisp table order.
- C1 headline from nb01: 27/30 seeded no-blend runs reached row-repeat stasis
  by 300 steps; observed median 8, range 7-11; 3 runs censored at 300.
- C7 headline (as amended in review): non-circular exhaustive enumeration,
  8192/8192 cases — blending = Rule 23 (elisp bit order) censored by local
  logic; Wolfram-descending reading fails 4096/8192 (finding A4).
- Validation:
  - `clj-kondo --lint src test notebooks` — `linting took 248ms, errors: 0, warnings: 0`
  - `emacs -Q --batch -l /home/joe/code/futon4/dev/check-parens.el --eval '(arxana-check-parens-cli)' -- --no-defaults ...` — `OK`
  - `clojure -X:test` — `Ran 5 tests containing 8 assertions. 0 failures, 0 errors.`
  - `clojure -M -m scirepro.cross-check 120` — `CROSS-CHECK OK 3 ICs x 120 steps; report=out/cross-check.edn`
  - `clojure -M -m scirepro.render` — `CLAY RENDER OK out/notebooks.nb01_metaca_core.html bytes=4074430; report=out/nb01_metaca_core.html bytes=3384053`
  - HTML inspection — `out/notebooks.nb01_metaca_core.html` contains 39 `<svg>` elements.

### Slice 2 — nb02 blending + C2 paired measurement (2026-07-13)

- Extended `scirepro.cross-check` to verify both `:multiply` and `:blend`
  dynamics against `256ca.el`, using `evolve-sigil` and
  `evolve-sigil-with-blending` respectively.
- Added `notebooks/nb02_blending.clj` and report support for Figure-1-style
  blend panels, paired C2 stasis/band timing, entropy curves, and change-rate
  curves.
- Persisted all 30 measurement ICs under `notebooks/sci-repro/resources/ics/`
  so nb01 and nb02 both read explicit IC artifacts rather than hidden RNG.
- C2 headline (as corrected in review — see defect note below): horizon 500,
  same 30 seeds, homogeneous width 80. No-blend stasis observed 27/30 with
  median 8; blend stasis observed 15/30 with median 20 among observed.
  Using horizon+1 for censored runs in the paired sign count, blend lasted
  longer on 27/30 seeds, no-blend longer on 3/30, ties 0; delta median 490.
- Stable-band proxy: first row remaining unchanged for 8 consecutive rows.
  No-blend band observed 27/30, median 7; blend band observed 15/30, median 19.
- **Slice-2 review defect (fixed): mixed-width cohort.** As committed, seeds
  150200130-132 reused width-64 IC artifacts left by the slice-1 cross-check
  (`ensure-ic!` reuses any existing file), so 3 of 30 paired runs were at a
  different width — caught because the reviewer's independent width-80
  recomputation gave blend-observed 15 vs the reported 14 (and delta median
  490 vs 491). Fix: cross-check ICs moved to their own `xcheck-*` namespace;
  the three cohort files regenerated at width 80; `ic-for-seed` now throws
  loudly on a persisted-width mismatch. Conclusion unchanged; numbers above
  are from the homogeneous cohort. All gates re-run green post-fix.
- Validation:
  - `clojure -X:test` — `Ran 6 tests containing 18 assertions. 0 failures, 0 errors.`
  - `clojure -M -m scirepro.cross-check 120` — `CROSS-CHECK OK dynamics=[:multiply :blend] 3 ICs x 120 steps; report=out/cross-check.edn`
  - `clj-kondo --lint src test notebooks` — `linting took 480ms, errors: 0, warnings: 0`
  - `emacs -Q --batch -l /home/joe/code/futon4/dev/check-parens.el --eval '(arxana-check-parens-cli)' -- --no-defaults ...` — `OK`
  - `clojure -M -m scirepro.render` — `CLAY RENDER OK [{:path "out/notebooks.nb01_metaca_core.html", :bytes 3733974} {:path "out/notebooks.nb02_blending.html", :bytes 3768052}]; reports=[{:id :nb01, :path "out/nb01_metaca_core.html", :bytes 3043205} {:id :nb02, :path "out/nb02_blending.html", :bytes 3076657}]`
  - HTML inspection — `out/notebooks.nb02_blending.html` contains 41 `<svg>` elements.

## Follow-ons (recorded, not armed)

- Blends as controller-level crossover (from 2D §4.2.1).
- The Dark Tower reprise ideas (directed ◁-coupling metric; BV-typed mutation
  vocabulary; tower-truncation/ratchet experiment) enter as *additional
  notebooks on this base*, each with the same claims-table discipline —
  see `futon5a/holes/excursions/E-the-dark-tower-2.md` and session notes.
