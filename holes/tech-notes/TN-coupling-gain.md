# TN-coupling-gain — the order parameter is the gain of the phenotype→genotype loop

**Status: working notes, 2026-07-28, claude-8.** Not yet folded into the paper or
supplement — Joe and codex are refactoring the Empirical Finding boxes into a literate
notebook, and this is staged here until that lands. Every number below is re-derived from
a committed artefact; the scripts are named at each claim.

Companion to [`TN-baldwin-reconstructed.md`](TN-baldwin-reconstructed.md), whose Q2 this
extends — see §6.

---

## 1. The claim

Across eight coordinates the family offers — λ, propagator fraction, sustained rule
diversity, blend strength, mutation rate, refuge probability, niche width, rule mobility —
causal reach is silent or nearly so. The coordinate that governs is **the gain of the loop
from the phenotype back to the genotype**. Reach is graded and monotone in it, in both
constructions where it can be dialled, and a control matched in mobility but blind to the
phenotype never leaves the ordered band.

This is not "structural, not parametric" (an earlier and weaker framing of mine, now
retired). An order parameter exists and the classical picture is recovered in it. What is
unusual is only *which* coordinate it is: one that conventional scans never sweep, because
architecture is normally a property a model has or lacks rather than a quantity.

## 2. The scale

All numbers share one protocol so they can be compared: `L=80`, `t*=60`, one phenotype bit
flipped, mean differing phenotype cells at `dt=59`, over 4 seeds × 10 sites (or 3 × 80 for
the river). This is the protocol of the paper's `find:ladder`, and the calibration
reproduces its elementary-rule values exactly for rules 0 (`0.00`), 204 (`1.00`) and 90
(`8.00`); rules 54, 110 and 30 differ only by seed set. Bands below are drawn from the
elementary rules alone: **ordered** < rule 90 (8.00) < **complex** < 22 < **chaotic**
(rule 30 = 36.45).

`scripts/regime_placement.clj`, `scripts/plot_coupling_dial.py`.

## 3. Everything with a blind genotype stays ordered

Where the genotype update never reads the phenotype, nothing clears the bottom of the
complex band:

| construction | reach | | construction | reach |
|---|---|---|---|---|
| $P_a$ (bare) | 0.03 | | mutation 0.40 | 0.85 |
| blend 1.00 | 0.03 | | preserving limit | 1.10 |
| braid $P_a$/two-4 | 0.12 | | rot+1 | 2.05 |
| mutation 0.10 | 0.23 | | blend 0.35 | 3.38 |
| async 0.25 | 0.47 | | ungated transport 1.00 | 4.05 |
| niches (8) | 0.50 | | braid rot+2/rot+4 | 5.20 |
| blend 0.70 | 0.55 | | **blend 0.00** | **8.15** |

Most sit at or below rule 204 (1.00), the frozen identity, whose flip persists as exactly
one cell and never spreads. The bare operator's perturbation usually vanishes outright.
The maximum, blend 0.00, is level with rule 90 — so the honest statement is that the
blind constructions reach the boundary and do not pass it. **A feedforward construction
landing clearly inside the complex band would complicate this, and is worth attempting
deliberately.**

## 4. It is the coupling, not the mobility

The decisive control. Conservative transport (`diversity_dial4.clj`) moves rules by
Margolus-style disjoint bijective swaps whose probability is gated on the local phenotype
interface — a genuine X→G edge. Replace that gate with a *constant* probability equal to
the rate and the mean number of swaps is unchanged (for a balanced field the gated mean
*is* the rate); the dynamics stay bijective and the rule histogram stays invariant. The
two differ only in whether the genotype can see the phenotype.

| rate | gated (reads X) | ungated (blind) | ratio |
|---|---|---|---|
| 0.20 | 9.43 | 1.65 | 5.7× |
| 0.35 | 14.45 | 1.68 | 8.6× |
| 0.50 | 19.27 | 1.77 | 10.9× |
| 0.75 | 26.05 | 2.77 | 9.4× |
| 1.00 | 25.38 | 4.05 | 6.3× |

**The blind control never leaves the ordered band.** Mobility is not what buys reach.

## 5. Two dials, opposite curvature

`figures/gain_curves.pdf`. Both vary loop gain and nothing else.

**Conservative transport** — gain is the phenotype-gated swap rate:

| rate | 0.00 | 0.05 | 0.10 | 0.20 | 0.35 | 0.50 | 0.75 | 1.00 |
|---|---|---|---|---|---|---|---|---|
| reach | 1.10 | 2.17 | 4.25 | 9.43 | 14.45 | 19.27 | 26.05 | 25.38 |

Crosses rule 90 between rates 0.10 and 0.20; passes rules 110 and 54; saturates past 0.75.
**Concave — it buys most of its reach early.**

**The river** — gain is the *currency* of the four context bits the genotype step reads.
Each cell, each step, reads the live phenotype with probability γ and a frozen reference
otherwise, so γ is the fraction of the genotype's view that is causally current
(`scripts/river_gain.clj`). Three seeds × 80 sites:

| γ | 0.000 | 0.125 | 0.250 | 0.375 | 0.500 | 0.625 | 0.750 | 0.875 | 1.000 |
|---|---|---|---|---|---|---|---|---|---|
| reach | 1.28 | 3.00 | 3.85 | 3.43 | 4.63 | 5.00 | 5.77 | 7.38 | 12.39 |
| sem | 0.19 | 0.30 | 0.33 | 0.30 | 0.33 | 0.39 | 0.34 | 0.37 | 0.57 |

Monotone within 1 SEM; maximum at the endpoint. **Convex — 45% of the span arrives in the
last eighth of the dial**, and only γ=1 clears rule 90. Even one stale read in eight costs
40% of the reach. Plausibly because the river's `firstMatch` fires on a 4-bit conjunction,
so a stale context does not degrade the match, it breaks it — but that is a hypothesis, not
something measured here.

Three design properties make the dial an instrument rather than a knob, and are worth
preserving in any reimplementation:

1. **Anchored at both ends.** γ=1 must reproduce the live river. It does, at **12.97
   exactly** — which is what validates the whole construction.
2. **The tape is preserved.** `random` is consumed once per cell at every γ, exactly as
   the river consumes it. Gate coins come from a *separate* stream, re-seeded identically
   in both branches of the fork, so the gate never injects divergence of its own.
3. **Statistics matched for free.** The frozen field is a real phenotype, so marginals and
   spatial structure are identical at every γ; only causal currency varies. (A noisy-channel
   read — corrupting bits with probability p — was considered and rejected: it destroys
   spatial structure too, confounding coupling with disorder.)

**A one-seed pilot showed a non-monotonic dip around γ=0.5–0.75. It did not survive three
seeds.** Recorded because it was nearly believed.

## 6. Relation to TN-baldwin-reconstructed

That note establishes the 2014 MetaCA "Baldwin function" is
`switch(local-condition, propagator, no-op)`, and that what the composition buys is a
*state-dependent mutation rate, not a new attractor* — on diversity the switch strictly
**interpolates** between its constituents (explore 14.2, hold 68.0, exotype 53.6).

This is the same object. The `context` that note reads out of the 2014 elisp — three old
context values plus the new state — is exactly the four-bit context
`original-paper-river-genotype-step` reads. **The river is that Baldwin function.**

The gain dial extends Q2 from diversity to causal reach, and the answer agrees:
reach is monotone in γ with its maximum at the full-coupling endpoint, so the partially
coupled switch reaches nowhere its constituents do not. Baldwin coupling buys a *rate*, and
now also a *reach*, but still not a new attractor. The interpolation is however strongly
non-linear, which the diversity measurement could not have seen.

One caution on the framing, worth keeping when this is written up: the implemented
mechanism is **Lamarckian** — the phenotype writes the genotype directly. The Baldwin
effect proper is the weaker, selection-mediated version, where plasticity reshapes the
landscape and genes follow without direct transfer. The shared structure (phenotype
influences genotype; *how strongly* is what matters) is real and the dial is the right
shape for it, but "we measured the Baldwin effect" would be overclaiming.

## 7. A correction to the published ablation

`run-river-ablated-from` captures its frozen reference at the start of *each call*, and
`two-stage` calls the runner once per stage. So in the perturbed branch the frozen field is
captured **after** the flip, and the genotype reads a static copy of the perturbation for
all 59 remaining steps. The docstring is scrupulous — "only the *dynamic* X→G edge is cut"
— so this is not a bug, but a consequence follows that the paper's phrasing does not carry:

**5.51 is not the zero-coupling value.** It is dynamic-cut-with-a-static-leak. With the
frozen reference supplied externally and identical in both branches, zero coupling is
**1.28**, near rule 204. The true span of the river's edge is 1.28 → 12.97, not 5.51 →
12.97, so `find:causal`'s "roughly halves" understates it by a factor of about two. That
finding needs either a qualifier or the corrected number when it moves into the notebook.

## 7b. Could selection recover Baldwin proper? (and would it complete?)

Raised by Joe, 2026-07-28. Two separable questions.

**Is there a selection process in the system?** No. There is no fitness, no population and
no generations; the coupling is a direct write, which is why §6 calls it Lamarckian. The
nearest thing the family already has to a criterion is the census's *aliveness* (change
rate over the final 40 generations), which could serve as a fitness in principle.

**Could the dial model selection?** Yes, and there is a canonical design — Hinton &
Nowlan's. Make γ heritable across a population of configurations, select on a criterion,
mutate γ, and watch its trajectory. The Baldwin signature is specific and falsifiable:
**γ rises and then falls while performance is maintained.** That fall is genetic
assimilation — what was achieved by reading the phenotype comes to be achieved without it.

**But our existing measurements predict that it would not complete here,** for a reason
worth stating because it is not the usual one. Assimilation requires the plastic
contribution to be *compressible into the genotype*. Three numbers say it is not:

1. **The gain curve is convex** (§5). 45% of the river's span arrives in the last eighth of
   γ. A partially assimilated genotype — one that has internalised most but not all of the
   read — collects almost none of the benefit, so there is no gradient for selection to
   climb.
2. **The read is determinative, not a tiebreak** (`scripts/coupling_load.clj`, 4 seeds ×
   80 × 120). The live context selects a *different* rule than a frozen context in
   **73.1%** of cell-steps, and than no context in **76.8%**. The combine-rule is a
   `firstMatch` over four candidates, so an uninformative re-selection would differ ~75%:
   at 73% the frozen context tells you almost nothing about the live answer. There is no
   common case for a blind rule to encode.
3. **Sixteen blind constructions were tried and none matched** (§3, max 8.15 against the
   river's 12.97). That is a weak search — hand-chosen, not exhaustive — but it points the
   same way.

So the prediction is that selection would **hold γ at 1** rather than drive it down. If
that is right, this is a system where Baldwin cannot complete *in principle*, because the
information the phenotype supplies is irreducibly dynamic rather than a fixed target
waiting to be encoded. Hinton & Nowlan's needle-in-a-haystack has a genome that *can*
express the answer; here the answer changes every step.

**Does it matter for the present claim?** No. The paper's claim is that reach is graded and
monotone in loop gain, and that holds whichever way the coupling is implemented. The
Lamarck/Baldwin distinction affects only how the mechanism is *described*, and §6's caution
covers that. Building a population, a fitness and a generational loop would be a
substantial new apparatus in service of a distinction that does not move the measured
result — worth doing as a follow-on, not as a prerequisite.

It would however be a genuinely interesting follow-on, because the prediction is sharp and
could be wrong. A γ that *did* decline under selection would mean the phenotype's
contribution was compressible after all, and would falsify the reading of §5's convexity
given here.

## 8. Open

- A feedforward construction deliberately aimed *past* rule 90, to test §3's boundary.
- Seeds. The river dial has 3; seed 3 runs consistently high (e.g. 9.95 vs 0.95 at
  γ=0.625). The trend is robust to this but the per-γ values are not tight.
- Whether the convex/concave contrast in §5 is about the conjunctive `firstMatch` or about
  the constructions differing some other way. Gating the river's four context bits
  *independently* rather than together would separate these.
- The selection experiment of §7b, whose predicted outcome (γ held at 1, no assimilation)
  would confirm the reading of §5's convexity, and whose opposite would falsify it.

## 9. Files

Code and data in `mmca-clj` (`64e6635` and ancestors):
`scripts/regime_placement.clj`, `scripts/river_gain.clj`, `scripts/plot_coupling_dial.py`,
`scripts/plot_gain_curves.py`, `scripts/phenotype_lambda.py`, `scripts/coupling_load.clj`,
`data/regime_placement_summary.tsv`, `data/river_gain{,_summary}.tsv`,
`figures/regime_placement.pdf`, `figures/gain_curves.pdf`.
