# AGENTS.md

Use clj-kondo to identify and fix parenthesis errors.

## Important: This Is Guidance, Not a Formal Spec

This document provides principles and heuristics, not a complete specification. Use judgment. If something isn't defined precisely, infer it from context or ask. Do not use ambiguity as an excuse for inaction.

The pseudocode examples are illustrative. By “final phenotype row” we mean the last entry in `:phe-history` (if present). If phenotype is absent, use the last genotype row mapped to bits (via sigil→bits), or mark collapse as `:unknown` and note it in the report. Implement the spirit, not the letter.

If a wiring doesn't have `update-prob`, use whatever parameter controls intervention pressure (e.g., match-threshold, gate threshold, mutation rate, exotype weight, kernel mix strength). If runs are short, adapt the frozen check. These are not gotchas — they're obvious adaptations.

## Mission 0 Experimental Methodology

### Principles

1. **No experiment without a baseline.** Every run must compare against a known reference. "Interesting" is not a result; "better/worse/equivalent to baseline X on metric Y" is a result.

2. **Known-good seeds are sacred.** Configurations that produce validated EoC are the starting point, not a target to rediscover. Never overwrite or lose them.

3. **One variable at a time.** When testing a hypothesis (e.g., "lower match-threshold improves filament score"), vary only that parameter. Hold seed, sigil, and other parameters constant.

4. **Negative results are results.** If a perturbation degrades performance, record it. This maps the fitness landscape and prevents re-exploring dead ends.

5. **Replication before generalization.** A configuration must succeed on multiple seeds before it's promoted. Single-seed wins are noise.

### Experimental Structure

**Phase 1: Baseline Establishment**
- Collect all known-good EoC configurations with full parameters
- Verify each reproduces EoC on its original seed
- Run each on 5 new seeds to confirm robustness
- Output: `known-good-baseline.edn` with verified configurations

**Phase 2: Controlled Perturbation**
- Select one baseline configuration
- Define perturbation (e.g., update-prob += 0.05)
- Run perturbed config on same seeds as baseline
- Compare metrics (filament primary, envelope secondary)
- Decision: keep if ≥ baseline on majority of seeds, discard otherwise

**Phase 3: Composition**
- Once multiple improvements validated individually, test combinations
- Watch for interaction effects (A good + B good ≠ A+B good)

**Phase 4: Generalization**
- Winning configurations tested on fresh seed sets
- Cross-domain validation (ant transfer) for final candidates

### Evaluation Criteria

| Outcome | Criteria | Action |
|---------|----------|--------|
| **Promote** | Beats baseline on ≥4/5 seeds, Cohen's d > 0.3 on primary metric | Add to known-good library |
| **Hold** | Mixed results, 2-3/5 seeds better | Flag for larger sample |
| **Discard** | Worse than baseline on ≥3/5 seeds | Record as failed, don't revisit |

(Cohen's d = (mean_A - mean_B) / pooled_std_dev. Use the primary metric across the shared seed set. If pooled std dev is 0, report d as 0 and note the degeneracy.)

### Anti-Patterns (What Not To Do)

- Running batches without a comparison baseline
- Changing multiple parameters between runs
- Treating "visually interesting" as success without metrics
- Re-running the same parameter space hoping for different results
- Skipping the baseline collection step

### Tooling Checklist

Before any experimental run:
- [ ] Baseline configuration specified
- [ ] Comparison seeds defined (shared with baseline)
- [ ] Primary metric declared
- [ ] Success/failure criteria stated
- [ ] Output location for results

### Known-Good Baseline Reference

The full baseline library is in `data/known-good-runset-20.edn`. Key anchor:

1. **Mission 17a 工** (primary anchor)
   - sigil: 工
   - update-prob: 0.50
   - match-threshold: 0.44
   - evidence: "most stable EoC in M17a"

See also: `reports/known-good-20-jvm3.md` for the full 20-run baseline set with reproduction commands.

Start from these. Do not attempt to rediscover them from random search.

## Universal Health Classification

Use the standard health classifier for all run classification:

```bash
bb -cp src:resources scripts/run_health_report.clj \
  --runs-dir /path/to/runs \
  --markdown reports/health/experiment-name-health.md \
  --csv reports/health/experiment-name-health.csv
```

### Classification Scheme

| Status | Criteria | Visual | Action |
|--------|----------|--------|--------|
| **HOT** | >60% change rate | Galaxy (chaotic) | Reduce update-prob |
| **BARCODE** | >70% columns frozen | Vertical stripes | System collapsed |
| **COOLING** | 50-70% frozen | Trending → barcode | May still collapse |
| **FROZEN** | <5% change rate | Static/dead | Increase update-prob |
| **EoC** | 15-45% change, <50% frozen | Coral reef | **This is the target** |
| **COLLAPSED** | >90% white or black | Uniform | Severely broken |
| **PERIODIC** | Row repetition | Attractor | Low-dimensional trap |

### Key Metrics

- **Change Rate (final)**: % cells changing per gen in last 20 generations
- **Change Rate (early)**: % cells changing in generations 5-25
- **Frozen Ratio**: % columns with identical values in last 20 rows
- **Stripe Count**: Number of contiguous frozen regions

### Persist Health Reports

**IMPORTANT**: Health reports must be committed to `reports/health/` so they're visible to all agents.

Workflow:
1. Run experiment → `/tmp/experiment-name/` (ephemeral)
2. Classify → `reports/health/experiment-name-health.{md,csv}` (persistent)
3. Commit the report
4. Analyze and plan next experiment

### The Two Failure Modes

Runs fail in two distinct ways — **don't confuse them**:

1. **HOT (galaxy)**: Too chaotic, everything changing, no structure
   - Fix: Reduce update-prob, add stability bias

2. **BARCODE (stripes)**: Collapsed to periodic attractor, columns frozen
   - Fix: Inject perturbation, maintain diversity pressure

**EoC is the narrow band between them.** Most experiments show BARCODE collapse by generation 100.

### Legacy Detection Rules (Still Valid)

The old collapsed/frozen/candidate checks are subsumed by the new classifier:

- `collapsed-white` = >90% white final row → now part of COLLAPSED
- `collapsed-black` = >90% black final row → now part of COLLAPSED
- `frozen` = rows identical → now captured by FROZEN (<5% change)
- `candidate` = none of above → now EoC or COOLING depending on metrics

Only **EoC** runs are worth examining further. All other classifications are failure modes.

## Decision Tree: What To Try Next

When runs fail, do not flail randomly. Follow this flowchart.

Note: "update-prob" below means "the primary parameter controlling intervention pressure." For some wirings this is literally `update-prob`. For others it might be `match-threshold`, a gate threshold, or kernel selection. Use judgment — the principle is "reduce pressure if collapsing, increase if frozen."

```
START
  │
  ▼
┌─────────────────────────────────┐
│ Run batch with current params   │
└─────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────┐
│ Classify each run               │
│ (collapsed/frozen/candidate)    │
└─────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────┐
│ Are most runs collapsed?        │
└─────────────────────────────────┘
  │YES                      │NO
  ▼                         ▼
┌──────────────────┐  ┌─────────────────────────────────┐
│ update-prob is   │  │ Are most runs frozen?           │
│ too high.        │  └─────────────────────────────────┘
│                  │    │YES                      │NO
│ ACTION:          │    ▼                         ▼
│ update-prob      │  ┌──────────────────┐  ┌──────────────────┐
│ -= 0.1           │  │ update-prob is   │  │ You have         │
│                  │  │ too low.         │  │ candidates!      │
│ Retry.           │  │                  │  │                  │
└──────────────────┘  │ ACTION:          │  │ Evaluate them    │
                      │ update-prob      │  │ against baseline │
                      │ += 0.1           │  │ and report.      │
                      │                  │  └──────────────────┘
                      │ Retry.           │
                      └──────────────────┘

IF update-prob adjustments don't help after 3 tries:
  → Reset to known-good params (工: update-prob=0.5, match-threshold=0.44)
  → The wiring structure itself may be the problem

IF known-good params also collapse with this wiring:
  → The wiring is broken. Discard it and try a different wiring.
```

## Reporting Template

After each batch, report what you learned. Do not just dump results.

```markdown
## Batch Report: [description]

### Configuration
- Wiring: [path]
- Params: update-prob=[X], match-threshold=[Y]
- Seeds: [list]
- Baseline comparison: [which known-good run]

### Results Summary
- Total runs: N
- Collapsed: X (Y%)
- Frozen: X (Y%)
- Candidates: X (Y%)

### Classification
| Seed | Status | Notes |
|------|--------|-------|
| 4242 | collapsed-white | 92% white final row |
| 1234 | candidate | looks structured |
| ... | ... | ... |

### What I Learned
- [If all collapsed]: update-prob [X] is too high for this wiring
- [If mixed]: seeds [A, B] worked, seeds [C, D] collapsed — investigating why
- [If candidates found]: runs [X, Y] beat baseline on filament by [Z]

### Next Action
- [Concrete next step based on decision tree]
```

This report goes to the user. Do not present unlabeled image dumps.
