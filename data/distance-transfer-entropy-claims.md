# Distance/lagged transfer entropy: ECA trust anchor

## Verdict

The preregistered distance/lagged transfer-entropy occupant at `(d = -2,
tau = 2)` **does not satisfy `SeparatesEoC`**. It rejects frozen rules, but it
ranks the chaotic ECA class far above the complex class. In accordance with the
experiment's stop gate, the MetaCA alphabet sweep was not run and this result
does not answer whether the observed gliders rotate.

This is a negative result, not an offset tuned against the ECA labels.

## Offset selection before ECA scoring

A full-cell diagonal mutual-information scan over `d in {-8..-1, 1..8}` and
`tau in {1..8}` on the existing mutating-template seed-42 spacetime found its
dominant peak at `(d = -1, tau = 1)`, with 1.242443 corrected bits. This implies
the dominant observed velocity `v = d/tau = -1`. The trust-anchor occupant was
fixed at `(d = -2, tau = 2)`, the first non-nearest point on the same velocity
ridge, so it remained distinct from nearest-neighbor TE. This choice was made
before ECA distance-TE scores were inspected.

## Rule table

Miller-Madow-corrected distance-TE in bits, mean plus or minus the 95% CI
half-width over 20 matched seeds:

| Class | Rule | Distance-TE |
|---|---:|---:|
| Ordered | 0 | 0.000000 +/- 0.000000 |
| Ordered | 8 | 0.000000 +/- 0.000000 |
| Ordered | 128 | 0.000000 +/- 0.000000 |
| Chaotic | 30 | 0.218430 +/- 0.005343 |
| Chaotic | 45 | 0.354173 +/- 0.040195 |
| Chaotic | 90 | 0.237009 +/- 0.001006 |
| Complex / EoC | 110 | 0.041308 +/- 0.004448 |
| Complex / EoC | 54 | 0.085186 +/- 0.003097 |
| Complex / EoC | 137 | 0.044238 +/- 0.005421 |

## Class comparison and `SeparatesEoC`

| Class | Distance-TE | Samples |
|---|---:|---:|
| Ordered | 0.000000 +/- 0.000000 | 60 |
| Chaotic | 0.269871 +/- 0.020291 | 60 |
| Complex / EoC | 0.056911 +/- 0.005695 | 60 |

The required complex lower bound is `0.051216`. The largest control upper
bound is the chaotic upper bound, `0.290162`. Therefore
`0.051216 > 0.290162` is false and `SeparatesEoC` fails.

The result points in the same problematic direction as nearest-neighbor TE:
deterministic chaos has abundant directed dependence even along the selected
velocity ridge. A reliable glider discriminator likely needs an explicit
coherent-particle or computational-mechanics representation rather than a
different fixed TE offset.

## Scope and replay

- One parameterized estimator now supplies AIS (`self-past`), nearest-neighbor
  TE, and distance-TE (`offset(d,tau)`) through a shared source-selection seam.
- Its MetaCA adapter exposes `bitplane`, `coarse(N bins)`, and `full-cell(256)`
  alphabet fills. They were implemented but not empirically swept after the
  ECA stop condition fired.
- The ECA protocol uses width 256, 512 updates, burn-in 128, destination-past
  `k = 8`, and the same 20 seeds as the AIS and nearest-neighbor TE anchors.
- This is a **statistical replay**, not a per-seed determinism claim.
- Complete seed-level observations are in
  `distance-transfer-entropy-eca-validation.edn`.
