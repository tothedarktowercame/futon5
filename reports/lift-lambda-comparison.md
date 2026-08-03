# Dynamically grounded lift comparison

Fixed seed `20260803`; N=`200` neighbourhoods; T=`8` fixed primary tapes plus `8` independent ceiling tapes; width `80`; t*=`60`; dt=`59`. The active three-grid apparatus uses EFE conatus weight `0.55`.

| variant | occupancy | flip locality | within | between | ratio | null@k | null sd | excess | excess sd |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| lambda-grounded | 30 | 0.298194 | 1.292957 | 1.297016 | 1.003140 | 1.001374 | 0.018578 | +0.001766 | +0.10 |
| eigen-sign | 32 | 1.327361 | 1.270778 | 1.298162 | 1.021549 | 1.003511 | 0.015222 | +0.018038 | +1.18 |
| random | 60 | 2.971250 | 1.267523 | 1.297257 | 1.023458 | 1.000659 | 0.020903 | +0.022798 | +1.09 |

## Damage ceiling and controls

Damage ceiling: within-neighbourhood tape distance `1.181294` (200 pairs), between-neighbourhood distance `1.310175` (39800 pairs), ratio `1.109101`.

For comparison, unaveraged primary-tape repeatability is within `1.169729`, between `1.212464`, ratio `1.036534`; this is not used as the T=8 ceiling.

The oracle ratio exceeds this empirical ceiling. Therefore average different-neighbourhood / same-neighbourhood distance is not a strict mathematical upper bound on a deliberately contrastive partition: the oracle selects groups far apart in signature space, whereas the ceiling averages every different-neighbourhood pair. It is reported as the requested repeatability ceiling, not asserted as an absolute bound on partition ratios.

Oracle: occupancy `2`, ratio `1.380055`, matched null `0.999643 ± 0.006203`, excess `+0.380412` (`+61.33` null SD).

Matched-granularity nulls use `32` deterministic random partitions per occupancy.

## Method and packing

Each tape initializes the same 36-bit neighbourhood as four repeating current rule sigils plus its repeating phenotype family. Only the heterogeneous exotype field and subsequent rewrite tape vary. After 60 burn-in steps, independent midpoint perturbations are propagated for 59 steps and phenotype, genotype, and exotype Hamming reach are divided by width. The three reaches are averaged over the eight fixed primary tapes, then each layer is z-scored across neighbourhoods before RMS Euclidean distance. The ceiling compares that T=8 estimate against an independent T=8 replicate estimate for the same neighbourhood, versus different neighbourhoods; it therefore uses the same averaging grain as the arm signatures.

The lambda-grounded key packs six bottom-to-top lines: four lookup-table Langton-lambda bits for the CURRENT LEFT/EGO/RIGHT/NEXT rules (`lambda >= 0.5`); one bit saying LEFT/EGO/RIGHT are not all identical; and one bit saying at least two of the four phenotype-family bits are one. Thus evaluation is local in both space and time and never retains initial sigils. The half-inclusive tie rules are fixed, not fitted. `eigen-sign` calls the incumbent lift unchanged; `random` is the seeded-hash control.

The old `2.4344` ceiling used a different signature and is not reused here.
