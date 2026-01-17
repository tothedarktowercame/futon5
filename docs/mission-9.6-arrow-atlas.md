# Mission 9.6: Arrow Atlas Refinement

**Status:** Proposed (derived from Mission 9.5 results)

## Goal
Increase the reliability of arrow detection and promote arrows that consistently meet robustness thresholds.

## Mission Set

### 1) Null Hardening
- Increase contexts from 5 → 15 or 20.
- Re-run arrow mining with the same grammar and k=2.
- Track null pass rate; target < 0.10.

### 2) Normalization Tightening
- Add one more independent feature to the regime signature, or require dual-normalizer agreement.
- Re-run with the same contexts to isolate normalization effects.

### 3) Robustness Re-test
- Re-evaluate detected arrows under stricter contexts/τ/Δ.
- Keep only those that survive stronger nulls.

### 4) Lever Atlas Draft
For each surviving arrow, log:
- Q phrasing (“what control question does this answer?”)
- where it works (which A-classes)
- what it tends to break (collapse/melt, triviality, instability)

## Notes
- “Worse but different” is acceptable at this stage; we are mapping levers, not value.
- Value ranking comes later once robustness stabilizes.

## Nomination as a Related Feature
Nomination (Mission 7) and arrows are both mechanisms for identifying reliable transitions, but they operate at different granularity:
- **Nomination** picks a promising kernel/exotype state and reruns it under a locked context to check reproducibility.
- **Arrows** discover repeatable regime shifts induced by interventions (operators) across contexts.

### How nomination would change the learning loop
If reintroduced, nomination would add a post-run validation stage:
1) Run MMCA with the current exotype.
2) Nominate a kernel or evaluator state (from the run trace).
3) Re-run under a locked context (Lock-0/1/2) and log the comparison.
4) Use the nomination gap as a secondary signal (diagnostic, not selection).

This keeps nomination parallel to arrow discovery: both act as validation layers without altering core selection mechanics.
