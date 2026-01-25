# AGENTS.md

Use clj-kondo to identify and fix parenthesis errors.

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
| **Promote** | Beats baseline on ≥4/5 seeds, effect size d>0.3 | Add to known-good library |
| **Hold** | Mixed results, 2-3/5 seeds better | Flag for larger sample |
| **Discard** | Worse than baseline on ≥3/5 seeds | Record as failed, don't revisit |

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

The following configurations have been validated as producing EoC:

1. **Mission 17a 工** (anchor configuration)
   - sigil: 工
   - update-prob: 0.50
   - match-threshold: 0.44
   - evidence: "most stable EoC in M17a", lands in 泰 zone

Start from these. Do not attempt to rediscover them from random search.

## Automatic Failure Detection

Before reporting results, classify each run. These checks are mandatory.

### Collapsed (Hot → White)
```
white_ratio = count(phenotype_final == 1) / length
IF white_ratio > 0.85 THEN status = :collapsed-white
```

### Collapsed (Hot → Black)
```
black_ratio = count(phenotype_final == 0) / length
IF black_ratio > 0.85 THEN status = :collapsed-black
```

### Frozen (Stagnant)
```
IF last 10 phenotype rows are identical THEN status = :frozen
```

### Possibly Good
```
IF none of the above THEN status = :candidate
```

Only `:candidate` runs are worth examining further. Collapsed and frozen runs are failures.

## Decision Tree: What To Try Next

When runs fail, do not flail randomly. Follow this flowchart:

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
