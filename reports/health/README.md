# Run Health Reports

This directory contains standardized health classification reports for MMCA runs.

## Classification Scheme

| Status | Criteria | Visual |
|--------|----------|--------|
| **HOT** | >60% change rate | Galaxy (chaotic noise) |
| **EoC** | 15-45% change, <50% frozen | Coral reef (sustained complexity) |
| **COOLING** | 50-70% frozen | Trending toward barcode |
| **BARCODE** | >70% columns frozen | Vertical stripes |
| **FROZEN** | <5% change rate | Static/dead |
| **COLLAPSED** | >90% white or black | Uniform collapse |
| **PERIODIC** | Row repetition | Deterministic attractor |

## File Naming Convention

```
{experiment-name}-health.md    # Human-readable report
{experiment-name}-health.csv   # Machine-readable data
{experiment-name}-health.edn   # Full metrics (optional)
```

## Generating Reports

```bash
# From futon5 directory:
bb -cp src:resources scripts/run_health_report.clj \
  --runs-dir /path/to/runs \
  --markdown reports/health/experiment-name-health.md \
  --csv reports/health/experiment-name-health.csv
```

## Key Metrics

- **Change Rate (final)**: % of cells changing per generation in last 20 gens
- **Change Rate (early)**: % of cells changing in generations 5-25
- **Frozen Ratio**: % of columns with identical values in last 20 rows
- **Stripe Count**: Number of contiguous frozen regions

## The EoC Target Zone

Edge of Chaos = the narrow band between HOT and BARCODE:
- Not so chaotic that structure dissolves (HOT)
- Not so stable that it freezes into patterns (BARCODE)
- Sustained moderate activity with spatial structure (EoC)
