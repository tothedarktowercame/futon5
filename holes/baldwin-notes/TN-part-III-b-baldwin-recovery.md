# TN-part-III-b-baldwin-recovery — scoping the selection run

**Status: PLAN, nothing measured here. 2026-07-30, claude-8.** Written before the
work so the criteria are fixed in advance. Every number quoted is a prior
measurement with its source named; this note adds none of its own.

Companions: [`TN-baldwin-reconsidered.md`](TN-baldwin-reconsidered.md) (where
Baldwin lives in the tower, and the original prediction),
[`TN-baldwin-reconstructed.md`](TN-baldwin-reconstructed.md) (what the 2014
Baldwin function actually is), [`TN-coupling-gain.md`](TN-coupling-gain.md) (the
gain result), [`TN-exotype-placement.md`](TN-exotype-placement.md) (Part III's
measurements, including the frozen control).

---

## 0. CORRECTION (2026-07-30): this experiment belongs in mmca-clj, not futon5

**The plan below sited the run in futon5 because the evolution machinery is there.
That was the wrong criterion and cost three dispatches.** The phenomenon lives in
`mmca-clj`. Recorded here so the detour is not repeated.

What was built in futon5, all verified and committed, all for the wrong substrate:
heritable `gain`/`width`/`update-prob`-to-zero/initial field (codex-9); a port of
the causal measure that `mmca-clj` already had (codex-5); an adapter joining them
(codex-6). Each passed review on its own terms.

Then a 648-config ceiling sweep over that family — 2 fields x 12 sigils x
gain {0,0.5,1} x width {3,5,9} x update-prob {0,0.5,1} — returned:

    best mean reach 2.667, at gain 0.0, width 3, update-prob 0.0

Three things follow. The ceiling is **below the complex band** (8.00), so the
family never enters the regime Parts II and III are about. The optimum sits at
**zero gain and zero rewriting** — a fixed field. And therefore, in that family,
plasticity is not merely useless but *harmful*.

**That would have produced a false positive.** Run the loop there and gamma falls
while score holds, which reads as Baldwin completing — when the mechanism is
"plasticity never helped." Only the rise-then-fall requirement in §5 catches it:
gamma never rises past 0.5, so the run lands in the bottom row, *experiment
failed, not the hypothesis*. Preregistering that is the sole reason this did not
become a claimed result.

Caveats, since 648 configs is not a proof of impossibility: 12 sigils of the
available set, two field types, one seed and three sites per config. The width
gene there also compresses via half-majorities rather than Part III's
`agree k/m`, so it may not carry the mechanism at all.

### What mmca-clj already provides

Three of the four ingredients, each reproduced byte-identically on independent
hardware:

| ingredient | status in mmca-clj |
|---|---|
| plasticity that **helps** | the gain dial, `1.2833` at gamma=0 to `12.3875` at gamma=1, monotone |
| a blind **destination** in the complex band | fixed rules 90, 110, 54 at `8.00`, `16.68`, `18.30`, reading no phenotype |
| tape discipline | already stated and enforced in `river_gain.clj`; `regime_placement.clj` corrected |
| a **selection loop** | **the only missing piece** |

So the build is far smaller than what was dispatched: a population over
(gain, rule field) with fitness a two-sided band score on reach minus `c`·gain.
Everything the loop needs to call already exists and is calibrated.

Sections 1-8 below stand as written **except** for their siting: read every
reference to `exoevolve`, `exotype.clj` and the tower as referring to the
equivalent to be built in `mmca-clj`. The question, the prediction, the cost
argument, the preregistered criteria and the failure modes are all
substrate-independent and unaffected.

## 1. The question

Part III establishes that an endogenous gain does not escape causal currency: a
gate reading the live phenotype far exceeds a rate-matched blind gate, and an
otherwise identical gate reading a phenotype frozen at `t*` does not exceed it at
all. That is assimilation failing *by construction* — we froze the read by hand.

The selection question is whether assimilation fails *under evolution*, when
nothing is frozen by hand and a population is free to find whatever works:

> Make the coupling gain heritable and costly, select on causal reach, and watch
> the **joint** trajectory of gain and score. Does the gain fall while score is
> maintained?

Rise-then-fall *with score maintained* is the Baldwin signature: what was first
achieved by reading the phenotype comes to be achieved without reading it.

## 2. Two corrections to an earlier draft of this plan

**(a) Plasticity must cost something.** The first version of this note made γ
heritable but free, and then predicted γ would not fall. That is close to a
tautology: nothing selects against a costless trait. Joe's diagnostic case is skin
pigmentation — melanin recedes at high latitude because it *costs* something,
not merely because sun is scarce. Assimilation's second phase is driven by the
cost of plasticity, so a design without such a cost cannot exhibit it and cannot
test for it.

Note what does *not* substitute. Scoring two-sided against the elementary-rule
calibration penalises γ high enough to tip into chaos, but that is a cost on the
**outcome**, not on the **mechanism**. It produces an optimal γ\* the population
climbs to and sits at. For a retreat you need blind to beat reading *at matched
outcome*, which requires charging for the reading itself.

**(b) The convexity argument was filed on the wrong side.** The gain curve's
convexity — 45% of the river's span in the last eighth of γ
(`TN-coupling-gain.md`) — was listed as supporting a stable high γ. It does the
opposite. With a cost on reading, convexity predicts **bistability**: pay in full
for the reach or don't pay and don't get it, with little worth having in between.
Under sufficient cost that predicts an *abrupt* collapse to γ=0, which is a fall.
Convexity therefore bears on the *shape* of any transition, not on whether one
occurs.

## 3. Does Baldwin have a destination? Yes — and the first plan could not reach it

Baldwin needs somewhere for the phenotypic solution to be encoded. The strongest
form of the "cannot complete" claim was that no blind alternative exists. **That
is false as stated, and the paper's own calibration says so.**

| blind construction | reach | band |
|---|--:|---|
| rule 90 | 8.00 | complex |
| rule 110 | 16.68 | complex |
| rule 54 | 18.30 | complex |
| rule 30 | 36.45 | chaotic |

These are fixed elementary rules. They read no phenotype, carry no coupling, and
three of them sit squarely in the complex band that two-sided scoring targets. A
genotype field that simply *is* rule-110-like, updating not at all, scores well
with γ = 0 and pays no plasticity cost. That is a complete Baldwin destination.

So why did sixteen hand-tried blind exotypes top out at 8.15? Because they were
blind *rewriters* — operators that still rewrite the rule field — not fixed
fields. The search never included the destination.

Two consequences for the design, and they are the difference between an
experiment that can recover Baldwin and one that cannot:

### G5 — plasticity must be switchable off

`exotype.clj:59` gives `update-prob ∈ {0.25, 0.5, 0.75, 1.0}`. The floor is
`0.25`, so the genotype can never stop rewriting. A population cannot reach a
fixed field, which is exactly the destination §3 identifies. **`update-prob` must
be able to reach 0.**

### G6 — the initial genotype field must be heritable

If every generation starts from a fresh random rule field, no "good fixed field"
can ever be inherited, and assimilation has nothing to accumulate in. The field
must be part of the heritable material alongside the exotype genes.

Without G5 and G6 a null result would mean only that we forbade the answer. With
them, Baldwin has a route we can name in advance: **evolve a rule field
intrinsically in the complex band, then let γ and `update-prob` fall away.**

## 4. The experiment

- **Heritable:** γ (8 levels), neighbourhood width `m ∈ {3,5,7,9}` (G4), the
  initial genotype field (G6), `update-prob` extended to include 0 (G5), plus the
  existing `rotation`, `match-threshold`, `mix-mode`.
- **Cost:** an explicit per-step charge proportional to γ, swept over `c` rather
  than fixed. A single hand-chosen `c` can produce any answer; the object is the
  *range* of `c` over which the joint trajectory decouples, if any.
- **Selected on:** causal reach at the Part III protocol (L=80, t\*=60, dt=59),
  scored two-sided against the elementary-rule calibration, minus `c·γ`.
- **Watched:** the joint trajectory (γ, score) per generation, plus the route —
  `update-prob`, and whether the field has drifted toward a fixed high-reach
  configuration.
- **Controls, wired before the main run:** γ pinned at 1 (can score be held
  without varying γ?); γ pinned at 0 with the field free (the blind ceiling under
  selection, the direct successor to the sixteen hand-tried exotypes).

## 5. Prediction and preregistered criteria

**The prediction is now genuinely uncertain, and weaker than the earlier draft's.**
With G5 and G6 in place a blind destination exists and is reachable, so the
strong claim — that Baldwin cannot complete in this substrate in principle — is
not defensible in advance. What survives from the earlier reasoning is narrower:
the live read selects a different rule than a frozen one in 73.1% of cell-steps,
and the frozen gate collects nothing, so there is no blind *rewriter* that mimics
the read. Assimilation, if it happens, should therefore route through **abandoning
rewriting altogether** rather than through a cleverer blind rewriter.

That is a specific, falsifiable expectation about the *route*, and it is what to
instrument.

| outcome | criterion |
|---|---|
| **Baldwin completes** | mean γ rises above 0.5, then falls below 0.25 for ≥10 consecutive generations, while mean score stays ≥90% of its peak |
| **Baldwin completes by degeneration** (expected route if it completes) | as above, *and* `update-prob` falls toward 0 with the field converging on a fixed high-reach configuration. Assimilation is real but the system has stopped being a two-layer MetaCA — report this explicitly rather than as unqualified Baldwin |
| **Baldwin does not complete** | γ stays above 0.5 with score maintained, or γ falls and score falls *with* it — loss of function, not assimilation |
| **Assimilation via another route** | score maintained with γ below 0.25 while `update-prob` stays high — a blind rewriter after all, contradicting the 73.1% measurement. Would be the most surprising outcome and needs the hardest scrutiny |
| **Experiment failed, not the hypothesis** | score never rises above the γ-pinned-at-0 control. Selection had no traction; says nothing about Baldwin and must not be reported as a null |

Two disciplines on top. The decisive observable is the **joint** trajectory, never
γ alone — the earlier draft's criteria led on γ with score as a side condition,
and that ordering is what hid the missing cost. And the prediction in this section
stands as written; it is not to be revised after seeing a trajectory.

## 6. Cost, and where it bites

One causal evaluation is a paired run: two branches, `t*`+`dt` steps at L=80.
Part III's producer does 16 seeds × 10 sites per row and takes ~40 min for ~40
rows on this hardware, so a single well-sampled row is ~1 min. A generation of
population 32 is therefore ~30 min at Part III's sampling, and 50 generations is
~25 hours — too slow to iterate on.

Three levers, in the order to reach for them:

1. **Cut sampling per evaluation** — 4 seeds × 5 sites for selection, reserving
   16 × 10 for the final trajectory rows. Roughly 8× cheaper.
2. **Proxy pre-filter** — score cheaply with the existing edge-of-chaos scorer,
   pay for the causal measure only on survivors.
3. **Rent it.** The reproduction run cost $1.67 for 73 minutes on 32 vCPU. The
   evaluation is embarrassingly parallel across population members, so a
   dedicated-CPU box turns 25 hours into roughly an hour for a couple of dollars.

Lever 1 has a cost of its own: at 4 seeds the between-seed spread already seen in
Part III (SD up to 4.9 cells) will make single-generation means noisy. The
trajectory, not any single generation, is the object — but the criteria in §5 are
written on ≥10-generation windows for exactly this reason.

## 7. Order of work

1. **G4 first** (width gene). Cheapest of the four and the run is uninterpretable
   without it.
2. **G2** (γ gene, 8 levels).
3. **G1** (port the causal measure as a scoring fn) — the real work.
4. Wire the two frozen-γ controls before the main run, not after. They calibrate
   what "maintained score" and "blind ceiling" mean, and a main run without them
   cannot be read.
5. Short run at reduced sampling to confirm selection has traction at all —
   i.e. that the §5 bottom row is not where we are.
6. Full run, on rented hardware if the timing above holds.
7. Write-up. Prediction stands or falls as recorded in §2; do not revise it after
   seeing the trajectory.

## 8. Failure modes to watch

- **Tape alignment.** A heritable γ means gates that fire conditionally on the
  phenotype, which is exactly the construction that produced a spurious
  order-of-magnitude result earlier in this line via RNG desynchronisation. Every
  new construction must draw unconditionally; `explore` and `hold` remain the
  invariants.
- **Assimilation routing around γ.** Flagged in the companion note and covered by
  §5 row 3: selection may achieve reach through `mix-mode` or `rotation` while γ
  drifts down for reasons unrelated to assimilation.
- **Reading a flat run as a null.** §5 row 4.
- **Fitness rewarding saturation.** Raw reach is maximised by chaos. The score
  must be two-sided against the calibration or the population will simply evolve
  toward rule-30 behaviour and the gain question never arises.
