# Wiring Learning Loop Analysis

Date: 2026-01-25

## Data Summary

- **Wirings analyzed**: 22
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
| complexity | -0.158 | 13 |
| creative-nodes | -0.158 | 13 |
| diversity-nodes | -0.158 | 13 |
| edge-count | -0.158 | 13 |
| gate-nodes | -0.158 | 13 |
| node-count | -0.158 | 13 |
| gate-ratio | -0.158 | 13 |
| creative-ratio | -0.158 | 13 |

## Insights

- **-** complexity: complexity correlates negatively with better outcomes (r=-0.16)
- **-** creative-nodes: creative-nodes correlates negatively with better outcomes (r=-0.16)
- **-** diversity-nodes: diversity-nodes correlates negatively with better outcomes (r=-0.16)
- **-** edge-count: edge-count correlates negatively with better outcomes (r=-0.16)
- **-** gate-nodes: gate-nodes correlates negatively with better outcomes (r=-0.16)
- **-** node-count: node-count correlates negatively with better outcomes (r=-0.16)
- **-** gate-ratio: gate-ratio correlates negatively with better outcomes (r=-0.16)
- **-** creative-ratio: creative-ratio correlates negatively with better outcomes (r=-0.16)

## Recommendations

- [high] No EoC runs found - try wider grids, more seeds, or perturbation injection
- [low] Try wirings with less complexity
- [low] Try wirings with less creative-nodes

## Next Steps

1. Run more experiments with varied wirings
2. Populate wiring-outcomes.edn with results
3. Re-run this analysis to refine insights
