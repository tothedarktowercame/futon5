# Domain Transfer Report: L5-creative Wiring → Cyberant (Ants)

Date: 2026-01-25

## Summary

Successfully transferred the L5-creative wiring pattern from MMCA to the cyberant (ants) domain. The key finding: **behavioral patterns that seem suboptimal in uniform environments become essential in patchy/sparse environments**.

## Background

### The L5-creative Wiring

From `data/wiring-ladder/level-5-creative.edn`:

```
Context → Diversity Sensor → Gate → Output
              ↓               ↓
         Legacy Path    Creative Path
         (gradient)     (XOR/novelty)
```

- **Diversity threshold**: 0.5
- **Below threshold**: Use legacy kernel (gradient-following, exploitation)
- **Above threshold**: Switch to creative path (XOR of neighbors, novelty-seeking)

### The Translation

The new `wiring->cyberant` adapter maps this structure to ant behavior:

| MMCA Component | Cyberant Equivalent |
|----------------|---------------------|
| Diversity sensing | Novelty observation |
| Legacy path (gradient) | Trail-following, gradient-use |
| Creative path (XOR) | Novelty-seeking, differentiation |
| Gate threshold | Adapt-trigger with switch-to |

Resulting cyberant config:
```clojure
{:pattern-sense {:trail-follow 0.7, :gradient-use 0.8}  ; baseline
 :adapt-config {:trigger :diversity-high
                :threshold 0.5
                :switch-to {:pattern-sense {:novelty-seek 0.8
                                            :trail-follow 0.0}}}}
```

## Experiment Design

### Contestants

1. **L5-creative (wiring-based)**: Adaptive strategy with diversity-triggered exploration
2. **Sigil-based (工/土)**: Pure gradient-following, no adaptive switching

### Food Distributions

| Distribution | Description | Exploration Value |
|--------------|-------------|-------------------|
| Snowdrift | Radial falloff, food everywhere | Low (exploit is optimal) |
| Patchy | 5 isolated clusters, radius 3 | High (must find patches) |
| Sparse | 3 small clusters, radius 2 | Very high (patches rare) |

### Protocol

- 20 runs per distribution (10 for sparse)
- 300 ticks per run
- 6 ants per side
- Queen starvation termination enabled

## Results

### Quantitative

| Environment | L5-creative | Sigil (工/土) | Winner | Win Rate |
|-------------|-------------|---------------|--------|----------|
| Snowdrift | 119.25 | **127.28** | Sigil | 16/20 |
| Patchy | **12.35** | 0.00 | Wiring | **20/20** |
| Sparse | **10.85** | 0.00 | Wiring | **10/10** |

### Interpretation

**Snowdrift (uniform food)**: The sigil-based strategy wins by ~8 points. When food is everywhere, gradient-following is optimal. The novelty-seeking behavior in L5-creative adds overhead without benefit.

**Patchy (clustered food)**: Complete reversal. The sigil-based strategy scores **zero in all 20 runs** - it never finds the isolated food patches. L5-creative's exploration capability becomes essential for survival.

**Sparse (rare patches)**: Same pattern. Pure exploitation fails completely; adaptive exploration survives.

## Key Insights

### 1. Behavioral Robustness vs Peak Performance

The L5-creative wiring trades peak performance in easy environments for survivability in hard ones:

```
Easy environment (snowdrift):  Sigil 127 > Wiring 119  (−6%)
Hard environment (patchy):     Wiring 12 > Sigil 0     (∞)
```

This is the **boundary-guardian pattern**: stable by default, creative when conditions demand it.

### 2. Environmental Structure Determines Strategy Value

The "best" strategy depends entirely on environmental structure:

| Environment Type | Optimal Strategy |
|------------------|------------------|
| Uniform/abundant | Pure exploitation |
| Patchy/sparse | Exploration + exploitation |
| Unknown/variable | Adaptive switching |

### 3. Successful Domain Transfer

The L5-creative wiring's behavioral pattern **meaningfully transfers** from MMCA to ants:

- The gate mechanism (diversity → switch) maps to adapt-trigger
- The creative path (XOR/novelty) maps to novelty-seeking
- The legacy path (gradient) maps to trail-following

The transfer isn't just syntactic - the behavioral semantics carry over: "explore when local conditions are diverse/novel, exploit when stable."

## Implications for Learning Loop

### Current State

The futon5 learning loop (`scripts/wiring_learning_loop.clj`) correlates **structural features** with **MMCA outcomes**:

```
Wiring Features → MMCA Health Classification → Correlations
(node counts,     (barcode, cooling, EoC)      (creative-ratio: +0.16)
 creative nodes,
 gate presence)
```

### Missing: Cross-Domain Validation

We now have evidence that:
1. L5-creative produces good MMCA outcomes (cooling, not barcode)
2. L5-creative transfers well to ants in certain environments

The learning loop should track **xenotype-level transfer results**, not just MMCA metrics.

### Proposed Extension

```
Wiring Features → MMCA Outcomes → Xenotype Transfer → Cross-Domain Score
                                        ↓
                              Environment-specific results
                              (snowdrift, patchy, sparse)
```

New registry structure:
```clojure
{:wiring-id :level-5-creative
 :mmca-outcomes {:classification :cooling
                 :change-rate 0.508
                 :frozen-ratio 0.0}
 :xenotype-transfer {:ants {:snowdrift {:mean-score 119.25 :win-rate 0.20}
                            :patchy {:mean-score 12.35 :win-rate 1.00}
                            :sparse {:mean-score 10.85 :win-rate 1.00}}}}
```

### Actionable Recommendations

1. **Run transfer benchmarks** for all wiring ladder levels (L0-L5)
2. **Add environment diversity** to MMCA experiments (not just seeds)
3. **Weight cross-domain success** in wiring selection (not just MMCA metrics)
4. **Document environmental assumptions** for each wiring pattern

## Files Created/Modified

### futon5
- `src/futon5/adapters/cyberant.clj` - Added wiring→cyberant conversion
- `scripts/cyberant_wiring_compare.clj` - Comparison config generator

### futon2
- `src/ants/war.clj` - Added patchy/sparse food distributions
- `src/ants/compare.clj` - Added `--food` distribution option

## Reproduction

```bash
# Generate comparison configs
cd futon5
clj -M -e '(load-file "scripts/cyberant_wiring_compare.clj")
           ((resolve (quote cyberant-wiring-compare/-main)))'

# Run comparisons in futon2
cd ../futon2

# Snowdrift (default)
clj -M -m ants.compare \
  --hex /tmp/cyberant-compare/wiring-cyberant.edn \
  --sigil /tmp/cyberant-compare/sigil-cyberants.edn \
  --runs 20 --ticks 300

# Patchy
clj -M -m ants.compare \
  --hex /tmp/cyberant-compare/wiring-cyberant.edn \
  --sigil /tmp/cyberant-compare/sigil-cyberants.edn \
  --runs 20 --ticks 300 --food patchy

# Sparse
clj -M -m ants.compare \
  --hex /tmp/cyberant-compare/wiring-cyberant.edn \
  --sigil /tmp/cyberant-compare/sigil-cyberants.edn \
  --runs 10 --ticks 300 --food sparse
```

## Next Steps

1. **Systematic transfer testing**: Run all wiring ladder levels through ants benchmark
2. **Environment sweep**: Test more food distributions (clustered, gradient, random)
3. **Learning loop integration**: Add xenotype transfer metrics to wiring outcome registry
4. **Threshold tuning**: Test different diversity thresholds (0.3, 0.5, 0.7) in ants
5. **Bidirectional transfer**: Can ants-optimized patterns improve MMCA?

## Conclusion

The L5-creative wiring demonstrates successful domain transfer from MMCA to ants. The key insight is that **adaptive strategies trade peak performance for robustness** - a pattern that only reveals its value when environmental conditions vary.

This validates the compositional wiring approach: by building up from simple components (legacy → diversity → gate → creative), we create behavioral patterns that generalize across domains rather than overfitting to a single environment.
