# Shifted fluctuation coherence: MetaCA alphabet eye-check

## Verdict

The coherence occupant reproduces the preregistered visual ordering on all
three alphabet fills: the glider-preserving `w = 0.9` mixture scores above the
glider-dissolving `w = 0.5` mixture with non-overlapping 95% confidence
intervals for bitplane, coarse(8), and full-cell(256).

Because bitplane already preserves the ordering, richer alphabets do not rescue
a signal hidden by per-bit projection. This is **not evidence that the gliders
rotate in an alphabet-hidden way**. Coarse and full-cell strengthen the
separation, but the activity trajectory is visible in every bitplane. Since
coherence operates on change activity rather than symbol identity, the result
is evidence about trajectory visibility, not proof that individual symbol
values remain constant along a glider.

## Fixed protocol

The occupant remains `{sourceFill = offset(-2,2), estimateFill = coherence,
aggregateFill = mean}`. The eye criterion was fixed before scoring:

> `w=0.9 lower 95% bound > w=0.5 upper 95% bound`

Every point uses the same 20 seeds and stochastic weighted dynamics as the
earlier sweep. Coarse uses eight equal-width bins over cell values 0..255.

## Sweep

Scores are dimensionless correlations, mean plus or minus the 95% CI
half-width over 20 matched seeds.

| Template weight | Bitplane | Coarse(8) | Full-cell(256) |
|---:|---:|---:|---:|
| 1.0 | 0.098098 +/- 0.004993 | 0.180224 +/- 0.007299 | 0.208219 +/- 0.007377 |
| 0.9 | 0.075535 +/- 0.002211 | 0.157274 +/- 0.005199 | 0.196970 +/- 0.006741 |
| 0.7 | 0.066847 +/- 0.001736 | 0.132513 +/- 0.004469 | 0.170074 +/- 0.004479 |
| 0.5 | 0.065862 +/- 0.001284 | 0.118507 +/- 0.003352 | 0.151356 +/- 0.005447 |
| 0.3 | 0.061366 +/- 0.001093 | 0.094594 +/- 0.003243 | 0.116549 +/- 0.004375 |
| 0.1 | 0.059169 +/- 0.001062 | 0.077228 +/- 0.002704 | 0.089360 +/- 0.003367 |
| 0.0 | 0.058260 +/- 0.001316 | 0.066330 +/- 0.003001 | 0.072312 +/- 0.004191 |

## Eye-ordering checks

| Alphabet | `w=0.9` lower | `w=0.5` upper | Pass |
|---|---:|---:|:---:|
| Bitplane | 0.073323 | 0.067146 | yes |
| Coarse(8) | 0.152075 | 0.121859 | yes |
| Full-cell(256) | 0.190229 | 0.156804 | yes |

The pure mutating-template parent (`w = 1.0`) also has the highest mean under
every alphabet, matching the visual observation that it contains the strongest
diagonal structures.

## Scope and caveats

The ECA admission anchor passed at class level but was driven by Rule 54; it did
not separate every complex rule from every chaotic rule. The combined result is
therefore evidence that fluctuation coherence is more aligned with the selected
visual glider criterion than the four failed information fills, not a universal
CA classifier.

This is a **statistical replay**, not a per-seed determinism claim. The protocol
uses width 96, 160 updates, burn-in 32, and seeds 42 through 61. Complete
per-seed, per-weight, per-alphabet observations are in
`coherence-metaca-sweep.edn`.
