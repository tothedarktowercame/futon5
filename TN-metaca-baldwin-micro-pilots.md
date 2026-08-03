# TN-metaca-baldwin-micro-pilots

**Date:** 2026-08-03
**Scope:** exotype selection objective (`futon5.exotype.pattern-eig`, `futon5.exotype.efe`)
**Method:** closed-form and mean-field computation over the model's own definitions.
No CA runs were used to obtain any result below.
**Artifacts:** `analysis/microtest1_domain_stability.py`, `analysis/microtest2_repairs.py`,
`analysis/microtest3_meanfield.py` (commit `be95f80`, plus this note)

---

## Why this note exists

Slices 6b–6d swept the EIG coefficient `c` over 1.475 … 7 across four arms, seven
temperature levels, 60 seeds, and horizons to 24,000 steps, looking for a value at which
the exotype layer sustains a mixed field. The runs cost hours of wall clock on a 16-core
box and produced no usable finding.

The micro-pilots below took under a second each and explain why. **Every coefficient in
that sweep lies at or beyond the point where the model's preference structure collapses.**
The result was available from the source before any of it ran.

Joe's framing, which this note adopts: the behaviour of these models is fixed by a handful
of basic states visible in the first few generations; long runs mostly re-photograph them.
Where a prediction can be computed, computing it is proper experimental discipline, not a
shortcut.

---

## The objective under study

For arm `:next-C-plus-eig`, per candidate kind, selection minimises

```
total = risk + ambiguity + λ·conatus − c·EIG
```

with a softmax at temperature τ over `total` (lower is preferred). Concretely:

- `risk` — Σ over channels of `KL(prediction ‖ the kind's own NEXT claim)`; the accuracy term.
- `ambiguity` — `Σ H(prediction)` over the four channels.
- `conatus` — `KL(prediction.hunger ‖ 0.05)`.
- `EIG` — `ln 2 · (Beta posterior variance / prior variance)` over *holders*, the
  neighbours already holding that kind.

The observation space is small enough to enumerate: `activity ∈ {0, ⅓, ⅔, 1}`,
`diversity ∈ {⅓, ⅔, 1}` — **twelve states**. This is the "handful of generators", and it
is the structural reason spacetime diagrams look alike at step 100 and step 24,000.

---

## Finding 1 — EIG maximally rewards adopting what your neighbours lack

`corrected-local-eig` conditions on holders, so a kind with no local holders is scored
from the Beta(1,1) prior:

| neighbourhood | EIG | vs absent |
|---|---:|---:|
| kind ABSENT (no holders) | **0.693** | 1.00× |
| 1 holder, confirmed | 0.462 | 0.67× |
| 4 holders, all confirmed | 0.165 | 0.24× |
| 8 holders, all confirmed | 0.068 | 0.10× |

Maximum is `ln 2`, attained on **zero evidence**. The term is loudest where it is least
informed, and decays monotonically with local support.

**Consequence.** In a domain interior every neighbour holds `k`, so `k` scores ≈ 0.068
while each of the other three scores 0.693. At `c = 5` that is a **3.1 nat bonus for
defecting**, applied at every interior cell at once. The construction is
interior-destabilising and boundary-stabilising: its equilibrium is maximal interface.
Domains cannot persist.

---

## Finding 2 — the chaos preference exists at c = 0, and it is the ambiguity term

Decomposition at `c = 0`, `λ = 0.55`, observation `(⅔, ⅔)`:

| kind | risk | ambiguity | λ·conatus | total |
|---|---:|---:|---:|---:|
| builder | **0.021** | 2.137 | 0.009 | 2.167 |
| collapser | 0.512 | 2.516 | 0.453 | 3.481 |
| **chaos** | 0.054 | **1.577** | **0.003** | **1.634** |
| identity | 0.155 | 2.177 | 0.428 | 2.759 |

**Risk — the term that rewards being right — prefers builder, and is outvoted.**
Ambiguity has nearly twice the spread (0.939 vs 0.491) and prefers chaos.

The mechanism: `ambiguity = Σ H(prediction)` is minimised by predictions near 0 or 1, and
`fixed-model` gives chaos the most extreme parameters (0.90, 0.78, 0.88). The term
therefore selects **the most confidently-claiming pattern regardless of accuracy**. In AIF
terms, ambiguity should be the expected uncertainty of observations given the state
occupied; computed on the candidate's own declared distribution it degenerates into
"prefer the most opinionated model".

**It is self-reinforcing.** `efe/predict` blends the candidate's base activity/diversity
50/50 with the *observed* activity/diversity. Chaos produces high activity and diversity;
high activity and diversity make chaos's blended prediction the most extreme, hence
lowest-ambiguity. **Chaos manufactures the observations that make chaos preferred.**

---

## Finding 3 — EIG does not cause the bias; it abolishes the only refuge from it

`P(select chaos)` in a domain interior, over the full 12-state observation space:

| | c = 0 | c = 1.475 | c = 5 |
|---|---:|---:|---:|
| lowest anywhere | **0.372** | 0.582 | 0.620 |
| highest anywhere | 0.951 | 0.993 | 0.997 |

At `c = 0`, chaos is already favoured in **11 of 12** states. One refuge exists — quiet and
low-diversity, `act = 0, div = ⅓` — where the blend pulls away from chaos's values. By
`c = 1.475` that refuge is gone and `P(chaos) ≥ 0.58` everywhere.

This is precisely the slice5 → slice6 transition in the figure library:

| figure | dominant kind | structured rows |
|---|---|---:|
| slice5 μ=0.10, λ=0.55 (no EIG) | identity 49% | 57% |
| slice5 μ=0.30, λ=0.55 (no EIG) | identity 45% | 62% |
| slice6b next-C+EIG | **chaos 98%** | 15% |
| slice6d w80 c=3 | chaos 76% | 58% |
| slice6d w160 c=3 | chaos 79% | 70% |

The pre-EIG runs at the same λ are balanced, with no kind above half. Every EIG run is
chaos-dominated.

---

## Finding 4 — repair sweep: only reweighting risk produces non-degenerate structure

Success criterion: a domain interior retains its resident, across more than one resident
kind, over the 12 observation states.

Retention out of 12 per resident, at `c = 0`:

| repair | builder | collapser | chaos | identity | distinct winners |
|---|---:|---:|---:|---:|---:|
| (none) baseline | 1 | 0 | **11** | 0 | 2 |
| A rebalanced extremeness | **11** | 0 | 1 | 0 | 2 |
| B risk ×3 | 3 | 1 | 8 | 0 | 3 |
| **B risk ×6** | 4 | 1 | 6 | 1 | **4** |
| C ambiguity ×0.5 | 3 | 0 | 9 | 0 | 2 |
| D chaos truly unpredictable | 12 | 0 | 0 | 0 | **1** |
| A+B rebalanced + risk ×3 | 9 | 0 | 3 | 0 | 2 |

Two results are worth more than the winner:

- **Rebalancing extremeness does not fix degeneracy — it relabels it.** Builder retains
  11/12 instead of chaos retaining 11/12. The disease is "one kind sweeps", not "chaos
  wins", so equalising parameters only changes which kind sweeps.
- **Making chaos genuinely unpredictable is the worst repair of the seven** — a single
  winner taking all twelve states. The intuition that chaos "ought" to sit at p ≈ 0.5
  makes the model *more* degenerate.

Only reweighting the accuracy term gives all four kinds a win, and it needs ≈ 6×.

---

## Finding 5 — the viable window, and the indictment of the swept range

With risk ×6, total retention across all four residents (max 48):

| c | retained/48 | verdict |
|---:|---:|---|
| 0.0 – 0.2 | 12 | non-degenerate, 4 winners |
| 0.3 | 10 | weak |
| 0.75 | 8 | weak |
| 1.0 | 6 | weak |
| **1.475** | **5** | **collapsed** |
| 3.0 | 1 | collapsed |

**The sweep ran c ∈ {1.475, 3, 4, 4.5, 5, 5.5, 6, 7}. It began at the collapse point and
went up.** No horizon, width, or seed count could have recovered a result from that range.

---

## Finding 6 — mean field predicts the mixture at w80 and cannot predict w160 at all

Mean-field map: `π_{t+1}[k] = E_{nbhd ~ Multinomial(3, π_t)} P(select k | nbhd, obs)`,
iterated to a fixed point.

| | builder | collapser | chaos | identity |
|---|---:|---:|---:|---:|
| **measured** w80 c=3 (60 seeds) | .643 | .009 | .348 | .000 |
| mean-field, obs (1, 1) | .728 | .001 | .227 | .044 |
| mean-field, obs (⅔, ⅔) | .794 | .012 | .079 | .116 |
| **measured** w160 c=3 (60 seeds) | **.000** | .000 | **.933** | .067 |

At high-activity observations the mean field reproduces w80's ordering and is within
~0.12 on both dominant kinds. **It predicts builder-dominance for w160, where the measured
run has builder at exactly zero with zero variance across all 60 seeds.**

Mean field has no width dependence by construction. The measured width effect is therefore
**a spatial-correlation phenomenon, not a mixture phenomenon** — the one place in this
study where a CA run carries information the algebra does not.

Note also the consistency between Findings 2 and 6: a domain *interior* favours defection
to chaos, while the *population* fixed point is builder-dominant. Both are true, and
together they describe a mixture with unstable domains — which is what w80 looks like.

---

## What this means

1. **EoC is not reachable in this configuration**, and not for want of a good coefficient.
   The dominant term of the objective selects the most disordered pattern, and that pattern
   manufactures its own supporting evidence. There is no ordered phase to sit at the edge of.
2. **The defect is in the model, not the EIG code.** `fixed-model`'s kinds are not on a
   common footing, ambiguity is arguably mis-specified relative to AIF, and risk is too
   weak to counterbalance it.
3. **The c-sweep was searching inside the collapsed region.** This was computable in advance.
4. **The prior "interior optimum at c = 5" (slice 6c) was a measurement artifact** —
   checkpoint entropy cannot distinguish stable coexistence from a field caught mid-flip.
   Run-length analysis of the 6d panels found the entire 80-cell row occupied by a single
   kind ten steps before the end, and three kinds at mean run 2.42 at the final step.

## Recommended next steps

- **Do not sweep c again.** The viable window is `c ≤ 0.2` and only with the objective
  rebalanced; below `c ≈ 0.2` the term barely matters.
- **Argue the risk/ambiguity weighting before adopting it.** Risk ×6 is a claim that
  accuracy should dominate confidence in this objective. That is a modelling position to
  defend, not a knob to tune.
- **One CA run, not a sweep**, at risk-weight ≈ 6 and `c ≤ 0.2`, to test the predicted
  non-degenerate mixture. If it still collapses to chaos, the mean-field analysis is
  missing something spatial and that is the finding.
- **Pair approximation** is the cheapest route to the width effect, since mean field
  provably cannot produce it.
- **Replace checkpoint entropy with a persistence measure** before any further
  measurement — how long a mixture survives, not whether one instant looks mixed.

## Limits of these results

- Findings 1–5 are single-cell local stability. "Retention" is a proxy for domains, not
  proof of them, and retention at the viable point is 12/48 — non-degenerate but not strong.
- The mean field assumes independent neighbours and a confirmation rate parameter; it
  cannot represent spatial correlation, which Finding 6 shows is where the width effect lives.
- The figure-library percentages in Finding 3 come from single-seed panels; the 6d
  mixtures are 60-seed aggregates.
- `risk ×6` changes the science rather than tuning it.
