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
