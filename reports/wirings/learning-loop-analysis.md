# Wiring Learning Loop Analysis

Date: 2026-01-26

## Data Summary

- **Wirings analyzed**: 72
- **Outcomes collected**: 89
- **Joined records**: 89

## Classification Distribution

| Classification | Count | % |
|----------------|-------|---|
| eoc | 9 | 10.1% |
| cooling | 4 | 4.5% |
| barcode | 25 | 28.1% |
| hot | 5 | 5.6% |
| collapsed-black | 14 | 15.7% |
| unknown | 23 | 25.8% |
| collapsed-white | 8 | 9.0% |
| periodic | 1 | 1.1% |

## Feature Correlations with Outcome Quality

| Feature | Correlation | N |
|---------|-------------|---|
| complexity | 0.003 | 70 |
| gate-nodes | 0.003 | 70 |
| diversity-nodes | 0.003 | 70 |
| node-count | 0.003 | 70 |
| edge-count | 0.003 | 70 |
| gate-ratio | 0.003 | 70 |
| allele-stratified-ratio | -0.003 | 70 |
| creative-nodes | -0.120 | 70 |
| creative-ratio | -0.134 | 70 |

## Trait Coverage

| Trait | Avg Nodes | Avg Ratio |
|-------|-----------|-----------|
| allele-stratified | 1.96 | 0.323 |
| sigil-level | 0.07 | 0.010 |

## Insights

- **-** creative-nodes: creative-nodes correlates negatively with better outcomes (r=-0.12)
- **-** creative-ratio: creative-ratio correlates negatively with better outcomes (r=-0.13)

## Recommendations

- [low] Try wirings with less creative-nodes
- [low] Try wirings with less creative-ratio

## Next Steps

1. Run more experiments with varied wirings
2. Populate wiring-outcomes.edn with results
3. Re-run this analysis to refine insights
