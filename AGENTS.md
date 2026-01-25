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
