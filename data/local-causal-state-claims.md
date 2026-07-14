# Local causal states and particle conjunction: ECA validation

## Verdict

The significance-reconstructed local-causal-state particle detector satisfies
the pooled-class `SeparatesEoC` criterion and the stronger Rule 110 bar.

- Complex class: `0.636732 +/- 0.016473`.
- Chaotic class: `0.206042 +/- 0.055132`.
- Ordered class: `0.000000 +/- 0.000000`.
- Complex lower bound `0.620259` exceeds the largest control upper bound
  `0.261174`.
- Rule 110 lower bound `0.628028` exceeds the largest chaotic-rule upper bound
  `0.503308`.

This is the first evaluator in this sequence to clear both the formal class
criterion and the explicit Rule 110 structural requirement.

## Per-rule table

Values are means plus or minus 95% CI half-width over 20 seeded initial
conditions. Domain coverage and particle density expose the two structural
halves; state count is inferred independently per run rather than fixed.

| Class | Rule | Conjunction | Domain coverage | Particle density | Causal states |
|---|---:|---:|---:|---:|---:|
| Ordered | 0 | 0.000000 +/- 0.000000 | 1.000000 +/- 0.000000 | 0.000000 +/- 0.000000 | 1.00 +/- 0.00 |
| Ordered | 8 | 0.000000 +/- 0.000000 | 1.000000 +/- 0.000000 | 0.000000 +/- 0.000000 | 1.00 +/- 0.00 |
| Ordered | 128 | 0.000000 +/- 0.000000 | 1.000000 +/- 0.000000 | 0.000000 +/- 0.000000 | 1.00 +/- 0.00 |
| Chaotic | 30 | 0.152127 +/- 0.007712 | 0.389408 +/- 0.009882 | 0.610579 +/- 0.009886 | 29.30 +/- 1.00 |
| Chaotic | 45 | 0.099010 +/- 0.039134 | 0.284092 +/- 0.060821 | 0.715889 +/- 0.060823 | 50.15 +/- 2.95 |
| Chaotic | 90 | 0.366989 +/- 0.136320 | 0.755058 +/- 0.096706 | 0.244110 +/- 0.096694 | 2.25 +/- 0.47 |
| **Complex** | **110** | **0.655801 +/- 0.027773** | **0.806400 +/- 0.017362** | **0.188592 +/- 0.017261** | **108.05 +/- 1.31** |
| Complex | 54 | 0.598781 +/- 0.020352 | 0.770906 +/- 0.013196 | 0.224367 +/- 0.013036 | 84.05 +/- 1.55 |
| Complex | 137 | 0.655615 +/- 0.030093 | 0.805307 +/- 0.018963 | 0.188000 +/- 0.018250 | 108.05 +/- 1.39 |

Rule 90 is the hardest chaotic control and has substantial seed variance: its
inferred state count ranges from one to five. Even its upper confidence bound
remains below Rule 110's lower bound.

## Reusable causal-state substrate

`futon5.mmca.local-causal-states` is independent of the particle evaluator and
provides:

1. finite-depth categorical past and future light-cone extraction;
2. empirical conditional future morphs for exact past cones;
3. CSSR-style state construction using chi-square homogeneity tests at
   `alpha = 0.01`, splitting only when all current states reject equivalence;
4. explicit unresolved rare pasts below minimum support rather than forced
   assignment;
5. inferred state predictive distributions and a past-to-state map; and
6. whole-grid causal-state labeling from a training-window model.

This model is suitable for reuse as a predictive belief-state substrate; the
domain/particle evaluator is only one consumer.

## Domain and particle decomposition

The fixed protocol uses past depth 3, future depth 2, minimum past support 20,
training times `[64,192)`, and evaluation times `[192,256)`. State count is not
a parameter.

On the training causal-state field, each state learns its modal spatial and
temporal successor, forming a finite phase automaton. An evaluation point is a
domain point only when both transitions are licensed. Unresolved states and
transition violations are defects. Eight-neighbor connected defect components
persisting for at least two time rows are particle objects, with area,
spatiotemporal extent, and bounds retained in the implementation.

The conjunction is exactly:

`domain-coverage * particle-sparsity`

where particle sparsity is `1 - persistent-particle-density`, forced to zero
when no persistent particle exists. There is no fitted exponent, label-specific
template, fixed cluster count, or post-result density threshold. This makes
ordered rules score zero despite perfect domain coverage.

## Statistical replay scope

The machine artifact contains 20 runs per rule, using width 256 and 512 ECA
updates with the existing seeded initial-condition protocol. Confidence
intervals are normal 95% intervals across seeds for rules and across the pooled
60 seed/rule observations for classes. This is a **statistical replay**, not a
per-seed determinism claim. Complete run-level structural observations are in
`local-causal-state-eca-validation.edn`.
