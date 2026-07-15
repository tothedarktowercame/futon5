# Diagonal transport: ECA-positive, barcode-negative

**Verdict:** BANKED. The candidate clears the Rule-110 anchor and fails the
frozen-barcode gate. No propagator regime was evaluated after that failure.

## Measure fixed before the barcode test

For each 20-generation window, form the binary temporal-innovation field
`D[t,x] = X[t+1,x] XOR X[t,x]`. Measure the absolute lag-one phi correlation
between `D[t,x]` and `D[t+1,x+v]` for velocities `v = -3..-1, 1..3`. The window
score is the smaller of the strongest left-moving and strongest right-moving
correlations. The run summary is the median of its nine overlapping windows
(stride 10).

This definition followed one rejected preliminary: a one-direction maximum
rewarded Rule 30's single dominant characteristic. Bilateral traffic was then
fixed on the ECA ground-truth gate, before l0 was inspected. No threshold or
parameter was changed after the barcode result.

## Gate 1: ECA anchor passes

Protocol: width 120, 100 generations, seeds 11/23/37/41/59, identical windows.
For every seed, the lower class-4 score exceeded the upper class-3 score, and
the lower class-3 score exceeded the upper settled score. Rule 110's median
scores span the range recorded in
`data/diagonal-transport/anchor-and-barcode.edn`; Rule 54 is higher, while Rules
30 and 90 remain near zero once one-way traffic is excluded. Rule 250's brief
initial transient remains visible in the first window, but its median is zero.

## Gate 2: frozen barcode fails

The l0 baseline used a heterogeneous width-120 genotype and 100 phenotype rows,
seed 4242. The genotype is byte-identical in every row: zero changing genotype
cells. Nevertheless its phenotype has persistent correlated innovations.

| Field | Median bilateral transport |
|---|---:|
| Rule 110, five-seed range | 0.1500–0.1624 |
| Frozen heterogeneous l0 barcode | **0.1814** |

The barcode is not vertically inert at the phenotype level: a frozen spatial
rule field can drive moving, bidirectionally correlated activity. The statistic
therefore detects transport-like correlation but cannot distinguish durable
barcode-driven noise from EoC computation. It is not an EoC instrument.

## Consequence

The preregistered sequence stops here. Rotate ±2, Figure-8 rotate −1, and
`(0 1 2)(3 4 5 6 7)` were not scored. Any refinement now made to demote l0
would be fitted to the decisive null and requires a new hypothesis, not an
extension of this result.
