# Wiring Learning Loop Analysis

Date: 2026-01-25

## Data Summary

- **Wirings analyzed**: 30
- **Outcomes collected**: 25
- **Joined records**: 25

## Classification Distribution

| Classification | Count | % |
|----------------|-------|---|
| cooling | 2 | 8.0% |
| barcode | 23 | 92.0% |

## Feature Correlations with Outcome Quality

| Feature | Correlation | N |
|---------|-------------|---|
| allele-stratified-ratio | 0.250 | 9 |
| allele-stratified-nodes | 0.250 | 9 |
| creative-ratio | 0.155 | 25 |
| creative-nodes | 0.139 | 25 |
| diversity-nodes | 0.086 | 25 |
| gate-ratio | 0.054 | 25 |
| gate-nodes | 0.008 | 25 |
| edge-count | -0.045 | 25 |
| complexity | -0.051 | 25 |
| node-count | -0.063 | 25 |
| legacy-nodes | -0.180 | 25 |

## Trait Coverage

| Trait | Avg Nodes | Avg Ratio |
|-------|-----------|-----------|
| allele-stratified | 0.61 | 0.075 |
| sigil-level | 0.18 | 0.025 |

## Insights

- **+** allele-stratified-ratio: allele-stratified-ratio correlates positively with better outcomes (r=0.25)
- **+** allele-stratified-nodes: allele-stratified-nodes correlates positively with better outcomes (r=0.25)
- **+** creative-ratio: creative-ratio correlates positively with better outcomes (r=0.16)
- **+** creative-nodes: creative-nodes correlates positively with better outcomes (r=0.14)
- **-** legacy-nodes: legacy-nodes correlates negatively with better outcomes (r=-0.18)

## Recommendations

- [high] No EoC runs found - try wider grids, more seeds, or perturbation injection
- [medium] Try wirings with more allele-stratified-ratio
- [medium] Try wirings with more allele-stratified-nodes
- [medium] Try wirings with more creative-ratio
- [low] Try wirings with less legacy-nodes

## Next Steps

1. Run more experiments with varied wirings
2. Populate wiring-outcomes.edn with results
3. Re-run this analysis to refine insights
