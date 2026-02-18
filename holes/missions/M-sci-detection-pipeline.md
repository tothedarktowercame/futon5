# Mission: SCI Detection Pipeline

**Date:** 2026-02-15
**Status:** Complete
**Blocked by:** None

## Summary

8-component automated Wolfram class detector for MMCA runs. Classifies
spacetime dynamics into Class I/II/III/IV with calibrated thresholds.

Components: domain analysis (spatiotemporal periodic tiles), particle
analysis (defect tracking, species, collisions), information dynamics
(transfer entropy, active information storage), and Wolfram class
decision tree.

67% accuracy on known rules (up from 33% before bitplane analysis).
Foundational to all subsequent measurement and evolution work.

## Key Files

- `scripts/sci_survey.clj`
- `scripts/sci_hybrids.clj`
- `scripts/sci_l5_creative.clj`
- `tools/tpg/wiring_analyzer.py`
