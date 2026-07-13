# M-sci-reproduction — reproduce the 1D experiments of "The Search for Computational Intelligence" (arXiv:1502.00130)

- **Status:** INSTANTIATE — slices 1–3 done, reviewed, published to
  `futon7a/lab/sci-repro/` (2026-07-13); nb04 (mutation, C4–C6) remaining
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
- **A5. Phenotype update semantics for Figure 4.** **Resolved slice 3:**
  phenotype ICs are random bit strings from `random-phenotype-string`
  (`256ca.el:399-402`). `evolve-phenotype-against-genotype` updates each
  phenotype bit by applying the cell's own current genotype rule to the old
  phenotype neighborhood, with fixed-zero phenotype boundaries
  (`256ca.el:1175-1190`). `co-evolve-phenotype-and-genotype` computes the new
  phenotype first from old genotype+phenotype, then computes the new genotype
  from the old genotype plus old/new phenotype context (`256ca.el:1192-1199`).
  Figure-4 rendering goes through `print-space-time-3`/`run-for-generations-3`
  (`256ca.el:1227-1241`, `256ca.el:1323-1365`) and the batch reproduction
  binds `evolve-sigil-fn` to `evolve-sigil-with-blending` so the genotype path
  is the paper's deterministic S3.2 blend dynamic; the context is present but
  ignored by that blend function.
- **A6. Mutation mechanism for Figures 5–8.** **Resolved slice 4a:** The elisp
  has **three** mutation-bearing evolve-sigil variants, each with distinct
  semantics:
  1. `evolve-sigil-with-blending-mutation` (`256ca.el:595-627`): performs the
     S3.2 blend step, then unconditionally calls `mutate-rule-n` with
     `mutation=1` (`256ca.el:596`) — so **every cell flips exactly one bit per
     generation**, AFTER the evolve step. `mutate-rule-n` (`256ca.el:571-591`)
     picks a uniform `(random 8)` allele position (`256ca.el:573`) and toggles
     it (0→1 or 1→0). There is **no rate gate** in this variant; the per-cell
     probability of flipping is 1.0 (one flip always happens), and the bit
     position is uniform over 0–7.
  2. `evolve-sigil-with-mutating-template` (`256ca.el:990-1065`): the DEFAULT
     `evolve-sigil-fn` (aliased at `256ca.el:1069`). Performs a context-driven
     template lookup, then applies `balance-mutation` (`256ca.el:971-986`):
     with probability **1/20** (5%), if the byte has `>6` ones it flips one
     randomly chosen 1-bit; if `<2` ones it flips one randomly chosen 0-bit;
     otherwise the byte is unchanged (`256ca.el:979-985`). This is the
     "reduced mutation rate" referenced in S4.1.
  3. `evolve-sigil-with-blending-baldwin` (`256ca.el:636-686`): a Baldwin-effect
     variant with stochastic 1/3 gating and context-count-dependent mutation
     count (`256ca.el:677-685`); not used for the headline figures.

  **Figure-8 "first-bit-only" skewed variant:** The paper prose (Fig 8 caption:
  "erroneously-programmed mutation... only ever flips the first bit") has **NO
  code implementation** in `256ca.el`. No function restricts the flipped allele
  to position 0. This is a prose-only construct. The Clojure engine implements
  it as a named variant (`evolve-with-first-bit-mutation`, `:first-bit` stream
  mode) so the notebook can measure its effect, but it is not cross-checked
  against elisp ground truth because no such ground truth exists.

  **Paper-vs-code discrepancies (S4.1):**
  - Paper S4.1 says "random mutations" without specifying the rate or mechanism.
    The code's `evolve-sigil-with-blending-mutation` uses rate 1.0 (every cell,
    every generation); `balance-mutation` uses 1/20 = 5%. These are very
    different effective rates.
  - Paper S4.1 says "reduced mutation rate" — this matches `balance-mutation`
    (the default `evolve-sigil-fn`), not `evolve-sigil-with-blending-mutation`.
  - Paper Fig 8 "first-bit-only" has no code counterpart.

  **Cross-check route taken:** Deterministic injected-stream (preferred route).
  `evolve-sigil-with-blending-mutation` was cross-checked because its mutation
  structure (one `(random 8)` call per cell per generation, unconditional) can
  be fully driven by an injected stream: we shadow `random` via `fset` in the
  elisp batch program so both engines consume the same explicit flip events.
  `balance-mutation`'s state-dependent allele selection (choose among current
  majority bits) cannot be driven by a pre-generated stream — but was not needed
  because the simpler variant gave grid-identity on the first try.
- **A7. Which elisp variant produced Figures 5–8?** **Resolved slice 4b
  (unresolvable-from-code):** `256ca.el` has a single commit (`92fa793`,
  initial commit) with no figure-generation trace. No comments or docstrings
  link specific functions to specific figures. All batch generators
  (`multiverse-files*`, 256ca.el:1444-1497) call `print-space-time` through
  `print-space-time-4`, all of which use `run-for-generations` or
  `run-for-generations-3`, all of which call `evolve-sigil-fn` (the global
  alias at 256ca.el:1069 = the default `evolve-sigil-with-mutating-template`).
  So the DEFAULT dynamic for ALL batch generation is template + balance-
  mutation. However, the operator could have re-bound `evolve-sigil-fn`
  interactively before running the batch (the code is designed for this), and
  there is no record of what binding was active when the paper's figures were
  generated. **Scope note:** nb04 pairs balance-mutation with the blend
  dynamic (labeled "balance-mutation on blend dynamic") because the template
  dynamic is out of scope for this slice — implementing the context-driven
  template lookup faithfully would require reproducing the full
  `evolve-sigil-string-contextually` → `evolve-sigil-with-mutating-template`
  pipeline with phenotype context, which is future work.
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

### Slice 3 — nb03 phenotype coupling + C3 measurement (2026-07-13)

- Resolved A5 above and implemented deterministic Figure-4 pheno-geno
  coupling in `scirepro.engine`: genotype takes the S3.2 blend step; phenotype
  is driven by the old genotype's local rules over old phenotype bits.
- Added explicit phenotype IC artifacts under
  `notebooks/sci-repro/resources/phenotype-ics/` for the 30 width-80
  measurement seeds plus 3 width-64 xcheck seeds.
- Extended `scirepro.cross-check` with `:coupled`; both genotype and phenotype
  grids are compared against `256ca.el` for 3 ICs x 120 steps.
- Added `notebooks/nb03_phenotype.clj`, nb03 render artifacts, and the nb03
  entry in `notebooks/sci-repro/publish.sh` without running publication.
- C3 headline, width 80, 30 seeds, 160 steps: frozen-region conformance
  227227/227227 = 1.000. Mean genotype/phenotype MI over rows = 0.7549;
  shuffled-pairing null = 0.3616; lift = 0.3934. Frozen-random-genotype
  phenotype baseline MI = 0.2364.
- Validation:
  - `clojure -X:test` — `Ran 6 tests containing 22 assertions. 0 failures, 0 errors.`
  - `clojure -M -m scirepro.cross-check 120` — `CROSS-CHECK OK dynamics=[:multiply :blend :coupled] 3 ICs x 120 steps; report=out/cross-check.edn`
  - `clj-kondo --lint src test notebooks` — `linting took 264ms, errors: 0, warnings: 0`
  - `emacs -Q --batch -l /home/joe/code/futon4/dev/check-parens.el --eval '(arxana-check-parens-cli)' -- --no-defaults ...` — `OK`
  - `clojure -M -m scirepro.render` — `CLAY RENDER OK [{:path "out/notebooks.nb01_metaca_core.html", :bytes 4075736} {:path "out/notebooks.nb02_blending.html", :bytes 4109810} {:path "out/notebooks.nb03_phenotype.html", :bytes 7464997}]; reports=[{:id :nb01, :path "out/nb01_metaca_core.html", :bytes 3384053} {:id :nb02, :path "out/nb02_blending.html", :bytes 3417491} {:id :nb03, :path "out/nb03_phenotype.html", :bytes 6773443}]`
  - HTML inspection — `out/notebooks.nb03_phenotype.html` contains 46 `<svg>` elements.
- **Slice-3 review (claude-6):** all headline numbers independently recomputed
  and reproduced exactly (conformance 227227/227227; MI 0.7549 / null 0.3616 /
  lift 0.3934 / frozen-random baseline 0.2364); tests and three-dynamic
  cross-check re-run green. One framing amendment: the C3a conformance 1.000
  is a *machine-checked consistency proof* of A5 + the region-finder
  (necessarily 1.0 when the implementation is correct; it would fail under
  alternative A5 readings), NOT empirical evidence — the empirical C3 claim
  rests on C3b's MI clearing both nulls. Noted in the notebook prose. Also
  observed: coupled MI converges to the null by t≈160 as the field freezes —
  the "phenotype follows genotype" signal lives in the active transient.

### Slice 4a — mutation engine + streams + elisp cross-check (2026-07-14)

- Resolved A6 above: pinned the exact mutation mechanism from `256ca.el`. Three
  variants exist; the cross-check uses `evolve-sigil-with-blending-mutation`
  (rate-1.0, one uniform flip per cell per generation, applied AFTER the blend
  step). Figure-8 first-bit-only is prose-only (no code).
- Extended `scirepro.engine` with mutation as an EXPLICIT EVENT-STREAM consumer:
  `generate-mutation-stream` (seeded, persisted EDN artifact), `flip-bit`,
  `apply-flips`, `stream->event-map`, `evolve-with-mutation` (fully deterministic
  given IC + stream — no hidden RNG in the dynamics path),
  `evolve-with-first-bit-mutation` (Figure-8 named variant), and stream
  persistence (`save-mutation-stream!`, `read-mutation-stream`,
  `mutation-stream->path` under `resources/mutation-streams/`).
- Added `scirepro.mutation-cross-check`: deterministic injected-stream route.
  Shadows `random` via `fset` in the elisp batch so both engines consume the
  same explicit flip events. Grid-identity required for 3 ICs × 120 steps.
- Validation:
  - `clojure -X:test` — `Ran 10 tests containing 42 assertions. 0 failures, 0 errors.`
  - `clojure -M -m scirepro.cross-check 120` — `CROSS-CHECK OK dynamics=[:multiply :blend :coupled] 3 ICs x 120 steps; report=out/cross-check.edn`
  - `clojure -M -m scirepro.mutation-cross-check 120` — `MUTATION CROSS-CHECK OK route=injected-stream variant=evolve-sigil-with-blending-mutation 3 ICs x 120 steps; report=out/mutation-cross-check.edn`
  - `clj-kondo --lint src test` — `linting took 395ms, errors: 0, warnings: 0`
  - `emacs -Q --batch -l futon4/dev/check-parens.el ... (src test)` — `OK`

### Slice 4b — balance-mutation + nb04 (C4-C6) (2026-07-14)

- Resolved A7: **unresolvable from code** — single commit, no figure traces.
  All batch generators use the default `evolve-sigil-fn` (template + balance-
  mutation), but the operator could re-bind interactively. nb04 pairs balance-
  mutation with blend dynamic (labeled accordingly); template dynamic is out
  of scope (scope note in A7).
- Implemented seeded balance-mutation (`balance-mutate-rule`,
  `evolve-with-balance-mutation`) matching 256ca.el:971-986 exactly: 5% gate
  (`(random 20) < 1`), homeostatic (popcount >6 flips a 1-bit, <2 flips a
  0-bit, [2,6] unchanged). Deterministic given a persisted seed
  (java.util.Random). Applied after the evolve step, same point as elisp.
- Added uniform-random-replacement null model (`evolve-with-random-replacement`)
  for C4 baseline.
- Added coupled evolution with mutation (`coupled-evolve-with-mutation`) for C6.
- Balance-mutation cross-check: deterministic injected-stream route (shadowing
  `random` via `fset`). Replicates the elisp's Fisher-Yates shuffle draw
  sequence exactly. Grid-identity 3 ICs x 60 steps, all identical=true.
- Added nb04_mutation.clj: C4 (rate sweep {1.0, 0.1, 0.01, 0.001, 0} x 10
  seeds, entropy + change-rate, no-mutation control + random-replacement
  null), C5 (popcount-class frequencies + flagged-rule patch lifetimes),
  C6 (first-bit-only on coupled runs, {0,128} occupancy + phenotype
  transients). A6+A7 findings sections. How-to-reproduce section.
- Added nb04 to render.clj and publish.sh (DESC entry).
- Validation:
  - `clojure -X:test` — `Ran 13 tests containing 47 assertions. 0 failures, 0 errors.`
  - `clojure -M -m scirepro.cross-check 120` — `CROSS-CHECK OK dynamics=[:multiply :blend :coupled] 3 ICs x 120 steps; report=out/cross-check.edn`
  - `clojure -M -m scirepro.mutation-cross-check 120` — `MUTATION CROSS-CHECK OK route=injected-stream variant=evolve-sigil-with-blending-mutation 3 ICs x 120 steps; report=out/mutation-cross-check.edn`
  - `clojure -M -m scirepro.balance-cross-check 60` — `BALANCE CROSS-CHECK OK route=injected-stream variant=evolve-sigil-with-mutating-template 3 ICs x 60 steps; report=out/balance-cross-check.edn`
  - `clj-kondo --lint src test notebooks` — `linting took 1131ms, errors: 0, warnings: 0`
  - `emacs -Q --batch -l futon4/dev/check-parens.el ... (all 8 touched files)` — `OK`
  - `clojure -M -m scirepro.render` — `CLAY RENDER OK [... nb04 bytes=14638793 ...]`
  - HTML inspection — `out/notebooks.nb04_mutation.html` contains 9 `<svg>` elements.

## Follow-ons (recorded, not armed)

- Blends as controller-level crossover (from 2D §4.2.1).
- The Dark Tower reprise ideas (directed ◁-coupling metric; BV-typed mutation
  vocabulary; tower-truncation/ratchet experiment) enter as *additional
  notebooks on this base*, each with the same claims-table discipline —
  see `futon5a/holes/excursions/E-the-dark-tower-2.md` and session notes.
