# TN-baldwin-selection-rewriters — selection over a heritable coupling gain

**Status: measured 2026-07-30, claude-8.** Results from `mmca-clj`
`scripts/baldwin_selection.clj`. Criteria were fixed in advance in
[`TN-part-III-b-baldwin-recovery.md`](TN-part-III-b-baldwin-recovery.md) §5 and
are not revised here.

**Scope, stated first because it is the main caveat.** Everything in §2 predates
the `update-prob` gene. In those runs rewriting is always on, so the γ=0 organism
is a blind **rewriter**, not a fixed field. The honest claim is therefore *a blind
rewriter cannot assimilate the read* — narrower than *Baldwin cannot complete
here*.

---

## 1. The loop layers on the published protocol

Same constants as every published row (L=80, T=120, t\*=60, dt=59), the same
river update, the same gain gate as `scripts/river_gain.clj`, the same two-sided
calibration, and the same frozen-field discipline where the stale reference is
supplied from outside and shared by both fork branches. New is only the
population: γ and the initial genotype field are heritable and fitness charges
`c·γ`.

**Faithfulness check, which caught a real bug.** `river_gain.clj` draws the
genotype from `r` *before* the phenotype. Injecting a heritable genotype left `r`
un-advanced by those 80 draws, so `p0` diverged and γ=1 measured `11.0083`
against the published `12.3875`. Advancing the tape by the same draws fixes it:

| | published | loop |
|---|--:|--:|
| γ=0 | 1.2833 | **1.2833** |
| γ=1 | 12.3875 | **12.3875** |

Without that the numbers would have looked plausible and been incomparable to any
published row.

## 2. Cost sweep, rewriters only

Traction first: at `c=0` selection drove mean γ from `0.369` to `0.988` and mean
reach from `3.32` to `12.51`, converging on the published dial's own γ=1 value of
`12.3875`. Selection has grip; the "no traction" row does not apply.

30 generations were not used here — 20, population 12, 2 seeds × 8 sites.

| run | final γ | max γ | final score | % of peak | final reach | criterion |
|---|--:|--:|--:|--:|--:|---|
| c=0.05 | 0.976 | 0.988 | 0.641 | 95% | 12.68 | (b) γ stays high, score held |
| c=0.10 | 0.976 | 0.988 | 0.592 | 94% | 12.68 | (b) γ stays high, score held |
| c=0.20 | 0.024 | 0.369 | 0.000 | — | 1.52 | (d) never established |
| c=0.40 | 0.024 | 0.369 | −0.004 | — | 1.52 | (d) never established |
| pin γ=1 | 1.000 | — | 0.671 | 90% | 14.10 | score holds at high γ |
| pin γ=0 | 0.000 | — | 0.000 | — | **0.71** | the blind-rewriter ceiling |

**At no cost does γ rise and then fall while score is maintained.** The prediction
on record holds. Low cost retains plasticity because it pays; high cost prevents
plasticity ever establishing, and the population collapses to the blind ceiling.
Neither is assimilation.

**A vacuous pass, worth recording.** An automated pass over these columns labelled
`c=0.20` as "score at 100% of peak" and credited it. The peak is `0.000`.
Percent-of-peak is meaningless when the peak is zero, and γ never rose past 0.5
in that run, so the correct reading is (d). Any maintenance claim must first
require the peak to exceed the blind ceiling.

## 3. Why this is not yet the Baldwin test

Baldwin needs a destination. The paper's calibration says one exists — fixed
rules 90, 110 and 54 read no phenotype and sit at `8.00`, `16.68`, `18.30` — but
with rewriting always on, no lineage can reach a fixed field. So §2's blind
ceiling of `0.71` is a fact about rewriters, and the *degeneration* route named in
§5 of the scoping note (assimilation by the system ceasing to be a two-layer
MetaCA) was inexpressible.

`update-prob` closes that. It is heritable over `[0, 0.25, 0.5, 0.75, 1.0]`, and
its coin comes from a **third** RNG stream so the gate and source tapes are
untouched; at `update-prob = 1` the loop reduces exactly to `river_gain.clj`.

Verified after adding it:

- **Invariant A** — γ=0 → `1.2833`, γ=1 → `12.3875`. Layering intact.
- **Invariant B** — with `update-prob = 0` the field is fixed and reaches the
  complex band: rule 110 → `16.4208`, rule 90 → `19.6000`, rule 0 → `0.0000`.

**Open, and do not paper over it.** Rule 90 gives `19.60` here against the
calibration's `8.00`. The likely cause is that `p0` derives from the river's tape
rather than the calibration's initial-condition construction, and rule 90 is
strongly initial-condition sensitive. That is a hypothesis, not a finding. **No
fixed-field number here should be compared to a published ECA row until it is
checked.**

## 4. What is running

The G5-live sweep, on rented hardware because eight local cores could not carry
it: costs 0.05/0.1/0.2/0.4 plus both pinned controls, 30 generations, population
24, 3 seeds × 10 sites, arms run sequentially so each gets the whole box. The
population evaluation is `pmap`-parallel; determinism was re-verified
(byte-identical output twice) before renting anything.

That run is the first in which the degeneration row is testable.


---

## 5. Selection under cost, with a reachable blind destination (2026-07-30)

Eight arms, 30 generations, population 24, 3 seeds x 10 sites, warm-up 8, run on a
rented 32-vCPU box. Data in `mmca-clj/data/baldwin/`.

### 5.1 The scalar-gain landscape is a spike, so no cost could have worked

This is the most reusable result here and it stands independent of any selection
run. Take the published gain dial and score it two-sided against the calibration
(complex band 8--22, centre 15, half-width 7):

| gamma | reach | band-score |
|--:|--:|--:|
| 0.875 | 7.375 | **0.000** |
| 1.000 | 12.387 | **0.627** |

Band-score is **exactly zero for every gamma below 1.0**, because reach at
`gamma = 0.875` is `7.375` and the complex band begins at `8.00`. The gain curve
is convex enough that only full plasticity clears the threshold.

So dropping one gamma level costs the *entire* score while saving only `c x 0.125`.
Retreat pays only when `c > 5.01`. The costs tested were `0.05`--`0.40`, about
**13x too small** -- and raising `c` past `5.01` would not produce gradual retreat
either, it would abandon plasticity outright. There is no intermediate regime.

**Gradual assimilation is geometrically impossible under a scalar gain.** The cost
sweep must therefore be read as *this parameterisation cannot express partial
assimilation*, not as *Baldwin did not complete*. The four cost arms coming out
identical in gamma, reach and update is the signature of exactly this, not a defect.

### 5.2 Results

| arm | final gamma | score | reach | plastic | criterion |
|---|--:|--:|--:|--:|---|
| c=0.05 | 0.970 | 0.569 | 11.35 | 1.000 | (b) gamma held |
| c=0.10 | 0.970 | 0.521 | 11.35 | 1.000 | (b) |
| c=0.20 | 0.970 | 0.424 | 11.35 | 1.000 | (b) |
| c=0.40 | 0.970 | 0.230 | 11.35 | 1.000 | (b) |
| per-cell | 0.964 | 0.392 | 9.90 | 0.963 | (b) |
| per-cell + HGT | 0.976 | 0.329 | 9.94 | 0.954 | (b) |
| pin gamma=1 | 1.000 | 0.427 | 11.38 | — | (b) |
| pin gamma=0 | 0.000 | 0.000 | 0.70 | — | blind ceiling |

Every arm is criterion (b). Gamma rose and did not fall while score was
maintained, in every condition. All arms beat the blind ceiling comfortably, so
the maintenance claims are not vacuous.

### 5.3 A refuted prediction, and why it is the useful part

**Predicted:** per-cell `mean-plastic` would fall toward `0.73` and stall, since
the live read selects a different rule in `73.1%` of cell-steps, leaving ~27%
assimilable free. **Observed: `0.963`** -- about 4% assimilated, not 27%.

The prediction was wrong because **73.1% is a figure about cell-STEPS, not cells.**
Treating "27% of cell-steps are indifferent" as "27% of cells are indifferent"
assumes indifference is concentrated in particular cells. If it is instead
scattered across time, then over 120 steps every cell is determinative at some
point and none is assimilable for free.

That is the structural difference from Hinton & Nowlan. Their loci are
**persistently** relevant or irrelevant -- a `?` at position 7 either matters for
the whole lifetime or never does -- so partial assimilation accumulates. Ours is
**transient**: a cell whose read is irrelevant at one step may be decisive at the
next. Assimilation needs indifference **spatially localised**; here it is
**temporally scattered**.

A secondary effect compounds it: at `c = 0.05`, assimilating one cell of eighty
saves about `0.0006` while reach swings `9.4`--`11.5` between generations, so the
per-cell signal sits below evaluation noise.

### 5.4 Horizontal transfer was not the bottleneck

Contiguous segment transfer, carrying field and mask together, moved
`mean-plastic` from `0.963` to `0.954` -- roughly 1%, within noise. Recombination
therefore does **not** rescue assimilation here, which supports §5.3: the problem
is the absence of stable assimilable loci, not an inability to assemble them.

### 5.5 The measurement that would settle it, not yet run

For each cell, the fraction of steps at which the live and frozen reads select the
same rule. If that distribution is flat near `0.27` for every cell, indifference is
scattered and no per-cell scheme can work. If it is bimodal with some cells near
`1.0`, those are genuine assimilable loci and the failure is the noise floor
instead. This is a direct measurement rather than the inference above, and it
distinguishes the two explanations that §5.3 cannot separate.


## 6. Hold mask: fixed loci, and a drift baseline that changes the reading

**Section 5.3's per-cell result measured the wrong thing and is superseded.** That
`:mask` chose only which phenotype a cell *read* (live or frozen) while the rewrite
stayed on a global `update-prob`, so a "non-plastic" cell still churned its rule
every step from stale context. Nothing was ever fixed, and `mean-plastic = 0.963`
therefore said nothing about assimilation. Joe identified this by asking why a
genotype like the elisp `刀` -- the same eight-bit string in every universe the
genome meets -- could not be treated as Hinton & Nowlan treat theirs.

`:hold` is the corrected mechanism: per-cell, controlling the **rewrite**. A held
cell keeps its inherited rule permanently, so its value is invariant across
evaluations, which is H&N's fixed locus. Mutation flips each locus independently
between plastic and fixed. Cost charges for the fraction NOT held, so assimilating
genuinely saves.

Verified: nothing held with `update-prob = 1` reproduces the published dial exactly
(`1.2833` / `12.3875`); all held gives `1.2875`, confirming the field truly stops
changing.

### 6.1 Result

24 generations, population 16, `c = 0.05`, warm-up 8.

| arm | gamma | score | reach | held | neutral drift | ratio |
|---|--:|--:|--:|--:|--:|--:|
| hold | 0.696 | 0.074 | 6.19 | 0.0250 | 0.3007 | **0.08x** |
| hold + HGT | 0.991 | 0.226 | 9.08 | 0.0680 | 0.3007 | **0.23x** |

### 6.2 The drift baseline is the guard, and it inverts the naive reading

The held fraction *rising* while score is maintained looks like assimilation. It is
not, without a null. Mutation flips each locus with probability `0.02` per
generation, so from all-plastic the neutral expectation is
`0.5 (1 - e^{-2 x 0.02 n})`, giving `0.3007` at `n = 23`.

Observed held fractions are `0.0250` and `0.0680` -- **0.08x and 0.23x of neutral
drift**. Holding is not being selected *for*; it is being selected **against**, by a
factor of four to twelve. That is what should happen when a held cell keeps a
*random* inherited rule and an all-held field scores only `1.2875`: every hold is a
liability, not a saving.

**Any future claim about the hold fraction must be stated against this baseline.**
Three times in this line of work a number moved in the expected direction and meant
something else -- "100% of peak" with a peak of zero, cell-steps read as cells, and
now a falling plastic fraction that is slower than drift.

### 6.3 Horizontal transfer reversed its verdict

In the scalar and read-mask designs, HGT was worth nothing (`0.963` against
`0.943`, within noise). Here it is the difference between climbing and not: without
it the population stalls at `gamma = 0.696`, reach `6.19`; with it, `gamma = 0.991`
and reach `9.08`, into the complex band.

Recombination's value scales with the number of independent loci, and the genome
went from about three scalars to eighty hold loci. This is a result about method
rather than about Baldwin, and it is worth carrying into any future design on this
substrate.

### 6.4 Standing

Gamma rose and did not fall while score was maintained, in both arms. The
prediction on record holds -- and this is the first version of the experiment in
which that is a measurement rather than an artefact of a design that could not have
shown otherwise.

`codex-1`'s review adds the gate that should have come first: the design is not
structurally validated until a **high-function static endpoint** and a
**score-preserving partial-hold path** are shown to exist. Section 6.1 is direct
evidence the first fails -- if holding were toward something good, selection would
not suppress it four- to twelve-fold below drift.
