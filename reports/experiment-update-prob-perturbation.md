# Batch Report: update-prob Perturbation Experiment

Date: 2026-01-25
Author: Claude Code (following AGENTS.md methodology)

## Configuration

- **Baseline**: Mission 17a 工 (update-prob=0.50, match-threshold=0.44)
- **Perturbations**:
  - A: update-prob=0.60 (+0.10)
  - B: update-prob=0.40 (-0.10)
- **Seeds**: 4242, 238310129
- **Length**: 120, **Generations**: 120
- **Primary metric**: Visual inspection + white ratio
- **Hypothesis**: update-prob=0.5 is near-optimal; deviations may degrade

## Results Summary

| Run | Seed | update-prob | White Ratio | Frozen? | Status |
|-----|------|-------------|-------------|---------|--------|
| baseline | 4242 | 0.50 | 0.633 | No | :candidate |
| perturb-A | 4242 | 0.60 | 0.542 | No | :candidate |
| perturb-B | 4242 | 0.40 | 0.575 | No | :candidate |
| baseline | 238310129 | 0.50 | 0.492 | No | :candidate |
| perturb-A | 238310129 | 0.60 | 0.533 | No | :candidate |
| perturb-B | 238310129 | 0.40 | 0.508 | No | :candidate |

**Total**: 6 runs
**Collapsed**: 0 (0%)
**Frozen**: 0 (0%)
**Candidates**: 6 (100%)

## Classification Details

All runs passed the automatic failure detection:
- No white ratios > 0.85 (collapsed-white)
- No white ratios < 0.15 (collapsed-black)
- No frozen (identical final rows)

This is expected since we started from known-good baseline params and made small perturbations.

## What I Learned

1. **The 工 baseline is robust**: Both seeds produced healthy candidates at update-prob=0.5
2. **±0.1 perturbations don't cause collapse**: All variations remained candidates
3. **White ratio varies by seed more than by update-prob**:
   - Seed 4242: range 0.542-0.633
   - Seed 238310129: range 0.492-0.533
4. **No obvious winner from metrics alone**: Need visual inspection to assess EoC quality

## Outputs

- Run EDNs: `/tmp/experiment-update-prob-perturbation/*.edn`
- Triptych images: `/tmp/experiment-update-prob-perturbation/*.ppm`

## What To Try Next (and Why)

The interesting finding isn't in this experiment — it's in what this experiment *doesn't* explain.

**The puzzle:**
- Codex's xenotype-portrait-20-jvm2 runs (varied wiring prototypes) all went "hot" and collapsed
- My runs here (工 baseline ± perturbations) all stayed healthy candidates
- Both used similar update-prob ranges

**This suggests:** The problem with the portrait runs isn't update-prob. It's something else in the wiring prototypes.

**Proposed next experiment:**
Take one of the "hot" wiring prototypes (e.g., `resources/xenotype-wirings/prototype-1.edn`) and:
1. Run it with its default parameters → expect collapse (replicates Codex's finding)
2. Run it with 工's parameters injected (update-prob=0.5, match-threshold=0.44) → does it still collapse?

**If it still collapses:** The wiring *structure* is the problem, not just the parameters.
**If it becomes a candidate:** The wiring structure is fine; Codex just needs to seed prototypes with known-good params.

This would bridge the gap between:
- known-good-20-jvm3 (legacy wirings, healthy)
- xenotype-portrait-20-jvm2 (prototype wirings, hot)

**Why this matters:** We have sophisticated wiring composition machinery that's going unused because everything it produces collapses. If we can make it work by injecting good params, the whole system becomes useful.

## Notes

This experiment followed AGENTS.md methodology:
- [x] Baseline configuration specified (工)
- [x] Comparison seeds defined (4242, 238310129)
- [x] Primary metric declared (visual + white ratio)
- [x] Success/failure criteria stated (collapse detection)
- [x] Output location documented
- [x] Automatic failure classification run
- [x] Report includes "What I Learned"
