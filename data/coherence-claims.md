# Shifted fluctuation coherence: ECA trust anchor

## Verdict

The preregistered coherence occupant at `(d = -2, tau = 2)` **satisfies the
stated class-level `SeparatesEoC` criterion**. The complex-class 95% lower bound
is `0.151263`, strictly above the largest control upper bound, `0.140471` for
the chaotic class. Frozen rules score exactly zero as required.

The separation is narrow and is driven by Rule 54. Rules 110 and 137 score
below chaotic Rules 30 and 45, so this is a class-level pass under the
predeclared pooled criterion, not rule-by-rule separation.

## Estimator fixed before scoring

`coherenceOccupant = {sourceFill = offset(-2,2), estimateFill = coherence,
aggregateFill = mean}`. The offset was selected before this experiment.

Coherence reuses the existing diagonal shifted-pair geometry but operates on a
binary fluctuation field: a cell is active when its value differs from its
previous-time value. For each valid spatial source/destination link, coherence
is the positive Pearson/phi correlation between the aligned source and
destination activity series. Mean subtraction removes the domain baseline;
if either activity series is constant, its score is defined as zero. Thus a
uniform frozen field cannot score merely because every raw value matches.

## ECA rule table

Scores are dimensionless correlations, mean plus or minus the 95% CI
half-width over 20 matched seeds.

| Class | Rule | Coherence |
|---|---:|---:|
| Ordered | 0 | 0.000000 +/- 0.000000 |
| Ordered | 8 | 0.000000 +/- 0.000000 |
| Ordered | 128 | 0.000000 +/- 0.000000 |
| Chaotic | 30 | 0.206066 +/- 0.005227 |
| Chaotic | 45 | 0.131406 +/- 0.018309 |
| Chaotic | 90 | 0.023626 +/- 0.001156 |
| Complex / EoC | 110 | 0.022233 +/- 0.010630 |
| Complex / EoC | 54 | 0.628986 +/- 0.021216 |
| Complex / EoC | 137 | 0.022808 +/- 0.008154 |

## Class comparison

| Class | Coherence | Samples |
|---|---:|---:|
| Ordered | 0.000000 +/- 0.000000 | 60 |
| Chaotic | 0.120366 +/- 0.020105 | 60 |
| Complex / EoC | 0.224676 +/- 0.073413 | 60 |

`0.151263 > 0.140471`, so `SeparatesEoC` is true under the specified pooled
class criterion.

## Protocol and replay scope

The protocol uses width 256, 512 updates, burn-in 128, fixed-zero boundaries,
and seeds 42 through 61. Confidence intervals pool the 60 seed/rule scores in
each class, matching the prior anchors. This is a **statistical replay**, not a
per-seed determinism claim. Complete seed-level observations are in
`coherence-eca-validation.edn`.
