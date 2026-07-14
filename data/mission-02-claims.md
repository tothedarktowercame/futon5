# Mission 2: Naive evolution collapses — baseline + null

## Claims table

| Claim | Metric | Baseline (mean ± 95% CI, n=30) | Null (mean ± 95% CI, n=30) | Verdict |
|-------|--------|-------------------------------|---------------------------|---------|
| C2.1 | Composite score | 22.45 ± 0.47 | 3.15 ± 0.05 | Evolution produces higher structure than random |
| C2.2 | Avg entropy (normalized) | 0.572 ± 0.016 | N/A (no metrics) | Moderate entropy — not maximal chaos |
| C2.3 | Avg change rate | 0.112 ± 0.004 | N/A (no metrics) | Low change — evolution settles, not chaotic |
| C2.4 | Final unique sigils | 17.4 ± 0.9 | 45.0 ± 0.6 | Evolution collapses diversity (17 vs 45) |

## Measured proposition

**"Naive evolution collapses":** Under local evolution with no exotype
(kernel=:mutating-template, no exotype steering), the genotype diversity
collapses from the initial ~45 unique sigils to ~17 (a 62% reduction),
with a low change rate (0.112 ± 0.004) and moderate entropy (0.572 ± 0.016).
The composite score (22.45) is well above the null (3.15), confirming that
evolution produces *some* structure — but the collapse in diversity shows
it converges to a narrow attractor rather than maintaining exploratory
dynamics.

The null model (random genotype per generation) maintains full diversity
(45 unique sigils) but has near-zero composite score (3.15), confirming
that diversity without evolution is just noise.

## Protocol

- length=50, generations=80, kernel=:mutating-template, no exotype
- 30 seeds (42–71), deterministic (java.util.Random seeded)
- Null: random genotype each generation (no evolution)
- Per-run EDN artifacts committed in-repo at `data/mission-02-runs/`

## Ground truth

- Protocol: `resources/exotic-programming.org:81-122` (Mission 2)
- Engine: `futon5.mmca.runtime/run-mmca` (src/futon5/mmca/runtime.clj:664)
- Metrics: `futon5.mmca.metrics/summarize-run` (src/futon5/mmca/metrics.clj:326)
