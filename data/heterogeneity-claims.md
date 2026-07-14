# Heterogeneity aggregate: ECA trust anchor

## Verdict

The preregistered heterogeneity occupant **does not satisfy `SeparatesEoC`**.
Spatial variance of the per-source corrected-information field remains larger
for the chaotic ECA class than for the complex class. The secondary
AIS-source/heterogeneity occupant also fails in the same direction.

Because the primary offset/heterogeneity occupant failed its binary trust
anchor, the MetaCA alphabet and eye-ordering sweep was not run. This experiment
therefore does not answer whether MetaCA gliders rotate. Per the DERIVE, the
next escalation is explicit computational-mechanics particle detection rather
than another information-statistic fill.

## Fixed occupants

- Primary: `sourceFill = offset(-2,2)`, selected before this experiment;
  `aggregateFill = heterogeneity`.
- Secondary: `sourceFill = self-past`; `aggregateFill = heterogeneity`.
- `heterogeneity` is the population spatial variance of the corrected
  per-source estimates. It uses exactly the same local field as `mean`; only
  the final aggregate fill changes.

## Offset-source heterogeneity

Scores are variance in squared bits, mean plus or minus the 95% CI half-width
over 20 matched seeds.

| Class | Rule | Heterogeneity |
|---|---:|---:|
| Ordered | 0 | 0.000000 +/- 0.000000 |
| Ordered | 8 | 0.000000 +/- 0.000000 |
| Ordered | 128 | 0.000000 +/- 0.000000 |
| Chaotic | 30 | 0.037504 +/- 0.000825 |
| Chaotic | 45 | 0.038302 +/- 0.006660 |
| Chaotic | 90 | 0.004191 +/- 0.000189 |
| Complex / EoC | 110 | 0.000858 +/- 0.000135 |
| Complex / EoC | 54 | 0.001559 +/- 0.000274 |
| Complex / EoC | 137 | 0.001001 +/- 0.000117 |

| Class | Heterogeneity | Samples |
|---|---:|---:|
| Ordered | 0.000000 +/- 0.000000 | 60 |
| Chaotic | 0.026666 +/- 0.004614 | 60 |
| Complex / EoC | 0.001139 +/- 0.000132 | 60 |

The complex lower bound is `0.001007`; the largest control upper bound is
`0.031280`. `SeparatesEoC` is false.

## AIS-source heterogeneity

| Class | Rule | Heterogeneity |
|---|---:|---:|
| Ordered | 0 | 0.000000 +/- 0.000000 |
| Ordered | 8 | 0.000000 +/- 0.000000 |
| Ordered | 128 | 0.000000 +/- 0.000000 |
| Chaotic | 30 | 0.043757 +/- 0.001238 |
| Chaotic | 45 | 0.039375 +/- 0.006033 |
| Chaotic | 90 | 0.001159 +/- 0.000054 |
| Complex / EoC | 110 | 0.017941 +/- 0.002989 |
| Complex / EoC | 54 | 0.002683 +/- 0.000708 |
| Complex / EoC | 137 | 0.010430 +/- 0.001750 |

| Class | Heterogeneity | Samples |
|---|---:|---:|
| Ordered | 0.000000 +/- 0.000000 | 60 |
| Chaotic | 0.028097 +/- 0.005283 | 60 |
| Complex / EoC | 0.010351 +/- 0.001967 | 60 |

The complex lower bound is `0.008384`; the largest control upper bound is
`0.033380`. `SeparatesEoC` is false. AIS with mean aggregation still passes its
earlier trust anchor, so this secondary result shows that replacing its mean
with spatial variance makes that previously successful source fill worse under
the stated criterion.

## Protocol and replay scope

Both occupants use width 256, 512 updates, burn-in 128, destination-past
`k = 8`, and seeds 42 through 61. Confidence intervals are computed over the
60 pooled seed/rule observations per class, matching the prior anchors. This is
a **statistical replay**, not a per-seed determinism claim. Complete per-seed
observations are in `heterogeneity-eca-validation.edn`.
