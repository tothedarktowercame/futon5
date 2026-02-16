# Vertical Band Analysis: Initial Findings

Date: 2026-01-25
Author: Claude Code

## The Metric

For each column (cell position) across time, I computed:
- **Change rate**: How often the cell flips between generations
- **Classification**: frozen (<5%), low-activity (5-15%), moderate (15-45%), chaotic (>45%)
- **Band score**: Fraction of columns with moderate activity (the "interesting" zone)

## Results Summary

All 10 runs from the wiring comparison experiment showed **sparse-activity**:

| Run Type | Moderate % | Frozen % | Chaotic % | Band Score |
|----------|------------|----------|-----------|------------|
| legacy u=0.50 (seed 4242) | 5.8% | 52.5% | 0.0% | 0.058 |
| legacy u=0.50 (seed 238310129) | 5.0% | 50.0% | 6.7% | 0.050 |
| legacy u=0.50 (seed 352362012) | 15.8% | 26.7% | 29.2% | 0.158 |
| prototype-001 (seed 4242) | 8.3% | 40.8% | 18.3% | 0.083 |
| prototype-001 (seed 238310129) | 3.3% | 53.3% | 10.8% | 0.033 |
| prototype-001 (seed 352362012) | 5.0% | 28.3% | 32.5% | 0.050 |
| legacy u=0.45 (seed 4242) | 4.2% | 52.5% | 3.3% | 0.042 |
| legacy u=0.45 (seed 238310129) | 6.7% | 45.8% | 6.7% | 0.067 |
| legacy u=0.40 (seed 4242) | 5.0% | 50.8% | 1.7% | 0.050 |
| legacy u=0.40 (seed 238310129) | 10.0% | 40.0% | 10.0% | 0.100 |

## Key Observations

### 1. Most columns are frozen or chaotic, few are moderate

Across all runs:
- 26-53% of columns are frozen (barely change)
- 0-33% of columns are chaotic (change too much)
- Only 3-16% are in the "moderate" sweet spot

### 2. Seed 352362012 stands out

For both legacy and prototype wirings, seed 352362012 produces:
- Highest moderate ratio (16% legacy, 5% prototype)
- Lowest frozen ratio (~28% vs ~50% for other seeds)
- More chaotic columns too (29-33%)

This might be the "best" seed for visual EoC quality.

### 3. Legacy vs prototype comparison

For seed 4242:
- Legacy: 5.8% moderate, 52.5% frozen, 0% chaotic
- Prototype: 8.3% moderate, 40.8% frozen, 18.3% chaotic

Prototype has more moderate columns but also more chaos. The increased chaos might be the "hot" quality — activity that doesn't sustain structure.

### 4. Active bands are narrow

Most "active bands" (contiguous moderate columns) are only 1-2 columns wide. The widest was 6 columns. This suggests activity is scattered, not concentrated in wide stable regions.

## Interpretation

The "sparse-activity" interpretation means most columns either:
- **Freeze**: Settle into a stable value and stop changing
- **Go chaotic**: Flip too rapidly to maintain structure

Only a small fraction (3-16%) maintain the moderate change rate that might indicate EoC.

## What might "coral reef" EoC look like?

Hypothetically, a run with genuine EoC would show:
- Higher band score (>0.3 = more than 30% moderate columns)
- Wider active bands (contiguous regions of 5+ columns)
- Less frozen (columns stay engaged longer)
- Less chaotic (activity is structured, not random)

## Open Questions

1. **Is "sparse-activity" normal?** We don't have a known-good EoC run with proven visual quality to compare against. Maybe 15% moderate is as good as it gets, or maybe true EoC would show 40%+.

2. **Does exotype data tell a different story?** We only analyzed phenotype. The exotype layer might show different patterns.

3. **What about spatial correlation?** This analysis treats columns independently. EoC might show up as correlated activity across neighboring columns (emergent patterns).

## Next Steps

1. **Find a visually-confirmed EoC run** and analyze its band structure as a reference point

2. **Try different change-rate thresholds** — maybe the "moderate" zone (15-45%) is wrong for this system

3. **Add spatial correlation** — measure whether active columns tend to cluster

4. **Analyze exotype history** — the exotype layer might reveal structure not visible in phenotype
