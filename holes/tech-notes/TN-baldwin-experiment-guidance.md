# TN-baldwin-experiment-guidance — what the revised Baldwin patterns require

**Status: pointer note, 2026-08-01, claude (Fable 5). No measurement.** This note
exists to hand the revised argument library to whoever builds the next round of
experiments. It summarises what changed in
[`futon3/library/baldwin/`](../../../futon3/library/baldwin/INDEX.md) and states, for
each pattern, what a preregistration must now contain in order to satisfy it.

Companions:

- [`TN-baldwin-experiments-status.md`](TN-baldwin-experiments-status.md) — the evidence
  ledger, and the `## Review` section (R1--R10) this revision encodes;
- [`TN-baldwin-selection-rewriters.md`](TN-baldwin-selection-rewriters.md) — the
  selection, cost, mask and hold experiments with their corrections;
- [`TN-exotype-placement.md`](TN-exotype-placement.md) — the Part III causal gate.

## 1. Why the library was revised

The previous round of experiments was motivated by this library and came back
inconclusive. The review asked why, and the answer was uncomfortable enough to be
worth stating plainly:

> **The library was not mainly wrong. It was mainly unenforced.**

Three prescriptions that would have caught the central defect were written down before
the pilots ran, and none was implemented:

| prescribed, and where | what happened instead |
|---|---|
| "a preregistered inherited-perturbation response curve" in preflight (`plasticity-builds-a-selective-neighbourhood`) | preflight checks that `:field` *mutates* (I4 reachability) and that the **gamma** axis is navigable (I6). The field axis is never tested for response. |
| "replace the band-only scalar" (same pattern) | `fitness` stayed `band-score(reach) - c * plasticDependence`; guidance preparedness stayed `:population-mean-band-score`. |
| score "time-to-threshold, cumulative realized rewrites, and held-out-task performance" (`test-evolution-of-learnability-before-static-assimilation`) | only held-out performance was registered; the run set `plasticityCost := 0`. |

A prescription that lives only in prose can be satisfied in name by a registration that
drops its load-bearing clause, and no apparatus check notices — every check that ran
confirmed the arms were separated, the tapes aligned and the configuration valid, which
is true and beside the point.

**So the operative change is not the new prose. It is the four obligations in §2.** When
adding to this library in future, prefer adding an obligation over adding a paragraph.

## 2. The four obligations

These are now stated in the root [`ARGUMENT.flexiarg`](../../../futon3/library/baldwin/ARGUMENT.flexiarg)
`CHECK` section. Each should appear as a registration field or preflight gate that fails
closed, not as narrative.

1. **Content response.** Show that varying inherited material *alone* moves
   post-learning fitness, above evaluation noise, before a paid run. Gene reachability
   is not this check. Navigability of a plasticity axis is not this check.
2. **Effort measured.** Name the fitness or readout component that is a function of
   realized adaptation — rewrites used, time to threshold — and register it.
3. **Axis disjointness.** Name the interval each ablation applies to, and predict the
   value of its zero endpoint in advance.
4. **Target reproducibility.** Show that the adapted state is reproducible across
   lifetimes of the same genome, against chance and against the inherited starting
   point, before claiming that anything can be assimilated.

All four would have failed on the 2026 pilots before compute was spent.

## 3. What is new

Three patterns were added.

**[`charge-for-realized-work-not-for-capacity`](../../../futon3/library/baldwin/charge-for-realized-work-not-for-capacity.flexiarg)** `[💰/工]`
A cost on plasticity drives assimilation only if it is a function of inherited material.
Charging for *capacity* — a gain, an update probability, a fraction of unheld loci — is
flat in genome content and selects for ablation rather than preparation. The evidence is
the repository's own positive control, which assimilates with **no plasticity cost term
at all** because `expected-learning-score = 2^(-plastic-count)` is already a smooth
function of genome content. Discharges obligation 2.

**[`ablation-axes-must-not-disable-the-instrument`](../../../futon3/library/baldwin/ablation-axes-must-not-disable-the-instrument.flexiarg)** `[🔦/双]`
When the mechanism under study is also the mechanism the instrument depends on, an
ablation produces a curve of how much instrument remains, not a dose-response. This is
the guidance pilot's learning-budget defect generalised: the zero-budget arm reproduced
the all-held static reach (`1.18`--`1.41` against `1.1778`), and mean reach was
non-monotone, with budgets 4 and 16 below budget 0 in all four population-by-partition
cells. Discharges obligation 3.

**[`choose-the-heritable-unit-where-invariance-lives`](../../../futon3/library/baldwin/choose-the-heritable-unit-where-invariance-lives.flexiarg)** `[🌰/本]`
"No assimilable structure exists" and "none exists in the coordinates I chose" are
different findings, and only the second has been earned. Constructive move: re-index the
invariance measurement you already have — for MetaCA, onto the four-bit context
quadruple the rewrite already conditions on, which has sixteen classes and recurs across
initial conditions even when cells do not.

## 4. What was sharpened

- **`plasticity-builds-a-selective-neighbourhood`** — gained an `EVIDENCE` block
  recording that its own two prescriptions were never implemented, and its `NEXT-STEPS`
  are now marked `OUTSTANDING` with the field-axis requirement made explicit.
- **`stationarity-decides-what-can-be-fixed`** — gained a `CHECK` (measure
  reproducibility, do not declare it) and an `EVIDENCE` block with the corrected
  indifference numbers. Also gained the second stationarity axis: fixing `p0` does not
  make the target stationary while the rewrite draws a random source bit per cell per
  step from a per-evaluation stream.
- **`assimilable-traits-need-heritable-shadows`** — gained
  `EXPRESSION-IS-NOT-SELECTABILITY`. The inherited field *is* expressed and still is not
  selectable, so a shadow variable and a fitness component that reads it are two separate
  gates. Adding a latent allele to a fitness function that is flat in genome content
  would leave it as unprepared as the byte it replaces.
- **`metaca-negative-evidence-localizes-the-bottleneck`** — gained `THREE-GATES`, which
  resolves the diagnosis into (1) no gradient on inherited content, (2) a non-stationary
  target on two axes, (3) a heritable unit that does not match the invariance. It now
  also says the guidance pilot must **not** be counted as an independent negative about
  assimilation.
- **`test-evolution-of-learnability-before-static-assimilation`** — gained `DIVERGENCE`,
  recording the gap between what it prescribed and what was registered.
- **`ARGUMENT`** — conclusion extended with the two missing gates; four failure modes
  added; the four obligations added to `CHECK`; `@hasa` extended to ten children.
- **`INDEX.md`** — argument map renumbered to ten, a `## Revision` section recording the
  unenforcement finding, and an evidence boundary extended with the indifference map, the
  guidance pilot reanalysis, the positive control and the guidance preregistration.

## 5. Suggested experiment order, mapped to obligations

Cheapest first. The first two are the ones that decide whether the rest is worth
building, and neither has been run.

| # | experiment | discharges | review ref |
|---|---|---|---|
| 0 | Reanalysis only: fold the indifference result into the ledger; re-read the guidance pilot on raw `:mean-reach`, already recorded per budget alongside the band score | — | R1, R4 |
| 1 | **Target reproducibility.** For one evolved genome, compute the field at `t*` under K evaluation seeds; measure pairwise agreement against chance (`1/256`) and against the inherited field | obligation 4 | R7.1 |
| 2 | **Content response.** Perturb one locus of `:field` at an unheld locus of an evolved genome; measure Δreach against evaluation noise; repeat across loci | obligation 1 | R7.2 |
| 3 | Run `scripts/assimilation_map.clj` (80×256 locus×rule) — the empirical map of `LocallyAssimilable`. The script exists; no committed artifact does | — | R7.3 |
| 4 | **Usage cost.** Replace or supplement the capacity charge with a term proportional to realized rewriting (`c/changed-count` already exists; `scripts/mutation_axis.clj` already computes churn with it) | obligation 2 | R7 |
| 5 | **Context re-indexing**, if 1 says the target is unstable in cell space | — | R7 |
| 6 | **Stationarity boundary sweep** — share the rewrite tape or not × fix `p0` or not, four cells, locate where the witness switches off | — | R8 |

Experiment 4 should be preregistered as a two-phase signature so that a partial result is
still a result:

- **Phase 1 (preparation / guidance):** agreement between the inherited field and the
  converged field rises under selection *while dependence stays high*.
- **Phase 2 (assimilation):** held fraction rises above the `0.1858` mutation-only null.

Phase 1 alone is a positive result and is the claim `two-claims-not-one` exists to
protect.

## 6. Two cautions carried forward

**Do not spend the registered confirmation seeds yet.** Both preregistrations still have
them unspent (`[20260801, 20260802]` for search; `[20260803, 20260804, 20260805]` for
guidance). Running them against the current budget axis would consume the
preregistration without testing the claim.

**Do not count the guidance pilot as a second negative.** Its low-budget arms are not
interpretable as learning, so it constrains the gradient gate only weakly. One
directional signal survives — at budget 64, learning-evolution exceeded no-learning
evolution on both training (`2.4193` vs `2.2656`) and held-out (`2.7083` vs `2.4010`),
with no consistent advantage at budget 0 — which is guidance-shaped, is one seed, and is
a hypothesis for the confirmation run rather than a result.

## 7. Provenance

Pattern sigils were validated against the vocabularies rather than assumed: the emoji
`💰`, `🔦`, `🌰` against the tokizh set in `futon3/resources/sigils/emoji-adjacency.csv`,
and the hanzi `工`, `双`, `本` against `futon3/resources/truth-table-8/truth-table-8.el`,
per the rule in `futon3/src/futon3/chops.clj:173-205`. All ten `baldwin/*`
cross-references and all INDEX links resolve in both directions.

**Indexing gap, since closed.** When this note was written all eleven `baldwin/` patterns
were absent from `futon3/resources/sigils/patterns-index.tsv`. They are present as of
2026-08-02: the TSV is a symlink into `storage/` rebuilt nightly at 04:30 by the
`index_patterns.sh` cron, and it picked them up on its first run after they were written.
Indexing lags pattern creation by up to 24h and needs no manual step. Diagnosis and the
wider scheduled-job sweep it triggered are in
`futon3c/holes/tech-notes/TN-futon1a-sweep-2026-08-02.md`.
