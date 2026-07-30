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
