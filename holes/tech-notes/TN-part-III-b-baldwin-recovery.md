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

## 1. The question

Part III establishes that an endogenous gain does not escape causal currency: a
gate reading the live phenotype far exceeds a rate-matched blind gate, and an
otherwise identical gate reading a phenotype frozen at `t*` does not exceed it at
all. That is assimilation failing *by construction* — we froze the read by hand.

The selection question is whether assimilation fails *under evolution*, when
nothing is frozen by hand and a population is free to find whatever works:

> Make the coupling gain heritable, select on causal reach, and watch the gain's
> trajectory. Does it rise and then fall while reach is maintained?

Rise-then-fall with score maintained is the Baldwin signature: what was first
achieved by reading the phenotype comes to be achieved without reading it.

## 2. The prediction, and why

**We predict the gain rises and does not fall.** Four measurements support this,
all prior:

1. **The gain curve is convex.** 45% of the river's span arrives in the last
   eighth of γ (`TN-coupling-gain.md`). A partially assimilated exotype collects
   almost nothing, so selection has no gradient to climb toward blindness.
2. **The read is determinative, not a tiebreak.** The live context selects a
   different rule than a frozen one in 73.1% of cell-steps, against ~75% for an
   uninformative re-selection (`TN-coupling-gain.md`). There is no common case a
   blind exotype could encode.
3. **Sixteen blind exotypes were tried by hand and none matched the river** —
   best 8.15 against 12.97 (`TN-coupling-gain.md`).
4. **The frozen gate collects nothing.** Same predicate, width and spatial
   statistics, reading a field held at `t*`: every departure negative, and the
   live gate wins 7 of 7 pairs by 14.5 cells
   (`TN-exotype-placement.md`). This is the assimilated form scoring at baseline,
   measured rather than argued.

If the prediction holds, the claim is sharper than "Baldwin did not occur here":
it is that **Baldwin cannot complete in this substrate in principle**, because
the information plasticity supplies is irreducibly dynamic rather than a fixed
target waiting to be encoded. Hinton & Nowlan's genome can express the answer;
here the answer changes every step. A negative result of that shape is worth more
than a positive one would be, and it is falsifiable.

## 3. What must be built

The machinery exists. `src/futon5/mmca/` has `exotype.clj`, `genoevolve.clj`,
`exoevolve.clj` (short-horizon exotype evolution) and `xenoevolve.clj` (slow
outer loop). The gaps are narrow and worth stating precisely so they are not
overstated.

### G1 — the two lines are not wired together

The tower and its evolution loops live in futon5's cyber-mmca line; Part III's
measurements live in `mmca-clj` and know nothing about them. The exotypes have
never been handed to `exoevolve`.

*Closure:* the smaller move is to bring the causal measure to the tower, not the
tower to `mmca-clj` — port `regime_placement.clj`'s damage protocol as a scoring
function callable from `exoevolve`. It is one paired run per evaluation, which is
the cost driver of the whole experiment (see §6).

### G2 — no heritable gain gene

Decoding an exotype (`exotype.clj:55-67`) yields `rotation` (4 values),
`match-threshold` (9 values), `update-prob` (4 values) and `mix-mode` (8 values).
There is no gene governing whether, or how much, the local regime reads the
phenotype. γ is a parameter added by hand to study one construction.

*Closure:* add γ as a heritable field, mutable like any other gene. Its
resolution matters given the convexity in §2.1 — too coarse and the loop cannot
express a partial retreat, which is precisely the trajectory we are watching for.
Start at 8 levels.

### G3 — the fitness is a proxy

`exoevolve` blends score modes (`legacy`, `triad`, `shift`) with xenotype and
hexagram weights. The xenotype layer scores edge-of-chaos *appearance*,
penalising stasis and confetti — the same two-sided intent the damage-spreading
scale measures, but not the same instrument.

*Closure:* substitute the causal measure, with the proxy retained as a cheap
pre-filter if the cost in §6 bites.

### G4 — width is not a gene (new; found while scoping)

This one is not in the companion note and it changes the plan. Part III's result
is that **width** governs the departure — R² from 0.84 to 0.96 when width is
added, against 0.88 for strictness, which is itself nearly collinear with firing
rate. But the evolvable exotype carries `match-threshold` (strictness) and
`update-prob` (duty cycle) and **no width gene at all**.

So the current population can vary the two parameters Part III shows do not
govern, and cannot vary the one that does. Selection would be searching a space
that excludes the answer. Width must become heritable or the run is
uninterpretable — a null could mean "Baldwin cannot complete" or merely "the
population could not express reach in the first place", and those must not be
confusable.

## 4. The experiment

- **Heritable:** γ (8 levels), neighbourhood width `m` ∈ {3,5,7,9}, plus the
  existing `rotation`, `match-threshold`, `update-prob`, `mix-mode`.
- **Selected on:** causal reach at the Part III protocol (L=80, t\*=60, dt=59),
  scored two-sided against the elementary-rule calibration so that both stasis
  and saturation are penalised, not raw reach.
- **Watched:** the population trajectory of γ against score, generation by
  generation.
- **Controls:** (a) a γ-frozen-at-1 lineage, to confirm selection can hold score
  without varying γ; (b) a γ-frozen-at-0 lineage, to establish the blind ceiling
  under selection rather than by hand — this is the direct successor to the
  sixteen hand-tried blind exotypes in §2.3.

## 5. Preregistered criteria

Fixed now, before any run. Three of the last four positive-looking results in
this line dissolved on inspection, so the criteria are written where tuning
cannot reach them.

| outcome | criterion |
|---|---|
| **Baldwin completes** | mean γ rises above 0.5, then falls below 0.25 for ≥10 consecutive generations, while mean score stays ≥90% of its peak |
| **Baldwin does not complete** (predicted) | mean γ rises and stays above 0.5 while score is maintained; no sustained retreat |
| **Assimilation via another route** | score maintained with γ below 0.25, but the γ-frozen-at-0 control reaches comparable score — assimilation happened, *not* through γ. Report as such; do not call it Baldwin |
| **Experiment failed, not the hypothesis** | score never rises above the γ-frozen-at-0 control. Selection had no traction; the run says nothing about Baldwin and must not be reported as a null |

That last row is the one to guard hardest. A flat run is the most likely outcome
of a first attempt and the easiest to misreport as a result.

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
