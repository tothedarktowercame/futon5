# Weighted predictive-information sweep

## Result

The stochastic blend family does **not** produce a common optimum for active
information storage (AIS) and nearest-neighbor transfer entropy (TE). AIS is
maximized by the pure mutating-template parent (`w = 1.0`), while TE is
maximized by the `w = 0.7` hybrid. The proposed 9:1 hybrid does not beat the
pure template on AIS: their 95% confidence intervals overlap. It does have
substantially higher TE, as do the other interior mixtures.

| Template weight `w` | AIS mean | AIS 95% CI half-width | TE mean | TE 95% CI half-width | n |
|---:|---:|---:|---:|---:|---:|
| 1.0 | 0.521016 | 0.009602 | 0.106786 | 0.003174 | 20 |
| 0.9 | 0.518139 | 0.006328 | 0.136450 | 0.001488 | 20 |
| 0.7 | 0.516302 | 0.004402 | **0.143480** | 0.001340 | 20 |
| 0.5 | 0.507408 | 0.003023 | 0.137240 | 0.001073 | 20 |
| 0.3 | 0.493517 | 0.002822 | 0.128209 | 0.000766 | 20 |
| 0.1 | 0.466293 | 0.007170 | 0.114608 | 0.001409 | 20 |
| 0.0 | 0.450110 | 0.007467 | 0.107755 | 0.001476 | 20 |

The highest AIS mean is **0.521016 at `w = 1.0`**. The best hybrid AIS mean
is 0.518139 at `w = 0.9`; its interval, [0.511810, 0.524467], overlaps the
template interval, [0.511414, 0.530618]. The highest TE mean is **0.143480 at
`w = 0.7`**. Its interval, [0.142140, 0.144821], is entirely above the pure
template interval, [0.103612, 0.109960]. At `w = 0.9`, TE is likewise above
the pure template without interval overlap.

## Interpretation guard

TE is retained as a spatial-transfer diagnostic, **not** a validated
edge-of-chaos discriminator. In the separately committed ECA trust anchor,
this estimator ranked chaotic rules (0.207600 ± 0.013588) above complex rules
(0.071637 ± 0.004203), contrary to the preregistered criterion. The weighted
TE maximum therefore identifies the largest measured nearest-neighbor
directed dependence in this family; it does not establish an edge-of-chaos
optimum. AIS and TE disagree about the best weight.

The images agree with the narrow descriptive reading: the pure template has
prominent long diagonal structures, while the interior mixtures become
visually denser. That qualitative observation is not used to alter either
estimator or select a post-hoc weight.

## Protocol and replay scope

This is a **statistical replay**, not a per-seed determinism claim. Each point
uses the same 20 integer seeds, a 96-cell grid, 160 updates, burn-in 32, past
window `k = 8`, and all eight rule bitplanes. On every interior-family cell
update, the shadow `java.util.Random` selects the complete mutating-template
cell dynamic with probability `w`, otherwise the complete Baldwin cell
dynamic. `w = 1.0` and `w = 0.0` delegate exactly to the parent implementations
and reproduce their grids byte-for-byte in the endpoint test. Confidence
intervals are normal 95% intervals over the 20 seed-level scores.

The complete per-run observations and recomputed summaries are in
[`weighted-predictive-information-sweep.edn`](weighted-predictive-information-sweep.edn).

## Matched seed-42 diagrams

All images use the same initial-condition and dynamic seed protocol as the
scored runs.

- [`w = 1.0`](weighted-predictive-information-diagrams/weighted-w-1p0-seed-42.png)
- [`w = 0.9`](weighted-predictive-information-diagrams/weighted-w-0p9-seed-42.png)
- [`w = 0.5`](weighted-predictive-information-diagrams/weighted-w-0p5-seed-42.png)
- [`w = 0.0`](weighted-predictive-information-diagrams/weighted-w-0p0-seed-42.png)
