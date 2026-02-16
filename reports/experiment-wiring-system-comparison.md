# Batch Report: Wiring System Comparison

Date: 2026-01-25
Author: Claude Code

## Configuration

**Experiment**: Compare legacy param-based wirings vs prototype graph-based wirings

**Seeds**: 4242, 238310129, 352362012
**Length**: 120, **Generations**: 120

**Runs**:
- 3× Legacy 工 (u=0.50) — known baseline
- 3× Prototype-001 (creative-peng) — graph-based wiring
- 2× Legacy 工 (u=0.45) — testing Codex's finding
- 2× Legacy 工 (u=0.40) — extending the trend

## Results Summary

| Run Type | Collapsed | Frozen | Candidates |
|----------|-----------|--------|------------|
| Legacy u=0.50 | 0 | 0 | 3 |
| Prototype-001 | 0 | 0 | 3 |
| Legacy u=0.45 | 0 | 0 | 2 |
| Legacy u=0.40 | 0 | 0 | 2 |
| **Total** | **0** | **0** | **10** |

### Detailed Classification

| Label | Seed | Status | White Ratio |
|-------|------|--------|-------------|
| legacy 工 u=0.50 | 4242 | candidate | 0.600 |
| legacy 工 u=0.50 | 238310129 | candidate | 0.492 |
| legacy 工 u=0.50 | 352362012 | candidate | 0.467 |
| prototype-001 | 4242 | candidate | 0.625 |
| prototype-001 | 238310129 | candidate | 0.458 |
| prototype-001 | 352362012 | candidate | 0.608 |
| legacy 工 u=0.45 | 4242 | candidate | 0.533 |
| legacy 工 u=0.45 | 238310129 | candidate | 0.550 |
| legacy 工 u=0.40 | 4242 | candidate | 0.483 |
| legacy 工 u=0.40 | 238310129 | candidate | 0.592 |

## What I Learned

### Key Finding: Prototype wirings do NOT inherently collapse

All 3 prototype-001 runs were candidates with healthy white ratios (0.458-0.625). This contradicts the earlier assumption that prototype/graph-based wirings inherently produce collapse.

**Implication**: The "hot" runs in xenotype-portrait-20-jvm2 were NOT caused by the wiring structure itself. Something else was different:
- Different seeds?
- Different length/generations?
- Different batch configuration?
- Visual misclassification (galaxy ≠ collapsed)?

### Secondary Finding: u=0.40 and u=0.45 also work

Both lower update-prob values produced candidates. Combined with Codex's finding that u=0.45 slightly outperforms u=0.50, this suggests the optimal may be in the 0.40-0.50 range rather than exactly 0.50.

### Open Question: What is "hot" if not collapsed?

The user described xenotype-portrait runs as "hot" (galaxy-like, not coral reef). But if they're not collapsed by our metrics, "hot" must mean something else:
- High entropy but no structure?
- Rapid change without pattern persistence?
- Visual quality issues not captured by white ratio?

This needs visual inspection or better metrics to resolve.

## Next Steps

1. **Retrieve xenotype-portrait-20-jvm2 runs** and classify them with the same tool — are they actually collapsed, or just visually "hot"?

2. **Visual comparison**: If the user can view triptychs, compare:
   - legacy-工-seed-4242 (known good)
   - prototype-001-seed-4242 (tested here)
   - One of the "hot" portrait runs (if available)

3. **Refine "hot" definition**: If collapsed doesn't capture "hot", what metric does? Options:
   - Spatial entropy
   - Temporal change rate
   - Motif diversity
   - The "vertical band" idea (spatial regions with structure)

## Conclusion

The prototype wiring infrastructure is NOT broken. Graph-based wirings can produce candidates. The earlier "hot" finding needs re-examination — it may be a visual quality issue rather than collapse.

This is good news: the sophisticated wiring composition machinery may be usable after all.
