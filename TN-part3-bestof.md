# TN-part3-bestof — navigating to the edge: what we can now locate, and by what

**Status:** MEASURED, 2026-08-04. Candidate material for Part III.
**Substrate:** post-reboot. Every number here postdates the N2 seeding fix, the derived
conditional model, and the `:previous-genotype` repair (`TN-baldwin-reboot.md` §16, §28–29,
§25). Pre-reboot numbers are not comparable and are not used.

---

## 1. The criterion

Damage reach: differing phenotype cells 100 steps after a single-cell flip, against an ECA
scale measured **in the same harness** so the comparison is like-for-like.

| anchor | reach |
|---|---:|
| rule 204 (frozen) | 1.0 |
| rule 90 | 8.0 |
| rule 54 | 32.8 |
| rule 110 | 37.1 |
| rule 30 (chaotic) | 65.1 |

Higher is not better. The target is the class-IV band; rule 30 scores highest and is chaos.

## 2. Best-of: 3 arms × 6 vocabularies × 4 blend levels × 24 seeds

| rank | arm | vocabulary | blend | reach |
|---:|---|---|---:|---:|
| 1 | `boring-triggered` | `no-absorbing` | 0.75 | **26.2 ± 9.1** |
| 2 | `boring-triggered` | `no-absorbing` | 0.25 | 23.8 ± 7.7 |
| 3 | `boring-triggered` | `no-absorbing` | 0.50 | 23.2 ± 7.4 |
| 4 | `boring-triggered` | `no-absorbing` | 0.00 | 20.3 ± 6.2 |
| 7 | `boring-triggered` | `default4` | 0.50 | 17.6 ± 8.6 |
| best `conformist` | | | | 5.7 |
| best `heterogeneous-fixed` | | | | 4.5 |

**26.2 sits between rules 90 and 54, nearer 54** — inside the class-IV band, comfortably
above the frozen anchor and far below chaos.

## 3. Three findings, in order of effect size

### 3.1 The phenotype-reading feedback loop is the dominant term

Every cell in the top twelve is `:boring-triggered`. The best cell without it, anywhere in
the search, is **5.7**. Closing phenotype → exotype is worth roughly **+15 reach**; blend and
vocabulary together are worth about +9 on top of that.

And it is specifically the *phenotype-reading*. `:conformist` also moves exotypes about, by
exotype majority, and reaches 5.7 — statistically indistinguishable from the open loop's 4.5.
Exotype mobility is not the mechanism; reading the phenotype is.

### 3.2 The default vocabulary is handicapped, and the handicap is `:collapser`

`no-absorbing` beats `default4` at every blend level by 3–9 reach. The reason is mechanical:
a propagator with absorbing bytes freezes the cells that carry it — `:collapser` freezes 100%
of a uniform field by t=200, with a half-time of 20 steps — and frozen cells cannot transmit
damage.

Worse, the objective *prefers* the freezing kind. Scored over the nine reachable observations
with rows derived by measurement, the default four give `{collapser 6, builder 2, chaos 1}`.
**The system selects the kind that ends its own dynamics, because the alternative is not on
the menu**: offered `:odd53`, the same objective picks it in 4 of 9 bins.

### 3.3 The optimum in blend is not a constant

In a uniform `:odd53` field, genotype damage peaks at blend 0.5 and dies by 0.75. In a
heterogeneous no-absorbing field with the loop closed, 0.75 is the best cell. **Blend, arm and
vocabulary interact**, and a coupling optimum read off one vocabulary does not transfer.

## 4. The coordinate map that made the vocabulary search possible

For any permutation propagator,

> **rate = 0.5 + fix(σ)/16**, exactly.

So `:rule-change` is a function of the fixed-point count alone and takes nine values. And
absorbing bytes exist iff every cycle is even, which forces fix = 0 — so **absorbing bytes
occur only at rate exactly 0.5000**. The two coordinates are nested, not crossed: within S₈,
"churns fast" and "can freeze" are mutually exclusive.

That map turns vocabulary from a naming exercise into a search over 12 kinds spanning every
distinguishable coordinate value, and it is what let §2 be a designed search rather than a
sweep.

Its sharpest consequence is a fact the generative model cannot represent: `(2,2,2,2)` and
`(5,3)` both have rate 0.5000, and 16 absorbing bytes versus 0. Measured over 400 seeds,
`(5,3)` never froze while the absorbing kinds froze completely, with half-time falling
40 → 20 → 10 → 5 as absorbing count doubles 2 → 4 → 8 → 16.

## 5. Honest limits

- **Reach is a single scalar at one horizon.** t=100, width 201. It orders the cells; it does
  not establish that the top cell is *at* criticality rather than near it.
- **± 9.1 on the best cell** is large against a 2.4-point gap to rank 2. Ranks 1–4 are not
  separated by these data; the *block* (`boring-triggered` × `no-absorbing`) is separated from
  everything else, and that is the claim.
- **No claim of a critical point.** This is the dynamical reading (perturbation and a clock),
  not the parametric one. The paper's earlier finding that a parameter sweep produces a broad
  crossover rather than a critical point is untouched here.
- **Navigation is only half demonstrated.** The system chooses its exotype and, given the
  vocabulary, chooses well. It does not choose its coupling: blend is exogenous, and §6 below
  is why that is hard rather than merely undone.

## 6. What the system cannot yet do, and why

Every local observable the substrate computes — divergence, hunger, activity — is **strictly
monotone in blend**, while damage peaks. A hill-climber on any of them runs to an endpoint and
sails past the optimum.

The reason is structural: **damage reach is a counterfactual.** It asks what *would* have
happened to an otherwise identical run. A single trajectory contains no counterfactual, so no
single-run local statistic can report it — and time-averaging does not help, because the mean
of a monotone signal is monotone.

Making criticality *sensible* to the model, rather than only measurable by us, is the open
problem of Part III. `TN-apoptosis-proposal.md` sets out one attempt, why the first version of
it failed, and what the failure implies about which statistics could work.
