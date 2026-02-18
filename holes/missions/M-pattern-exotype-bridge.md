# Mission: Pattern-Exotype Bridge

**Date:** 2026-02-16
**Status:** Complete
**Blocked by:** None

## Summary

Embed 791 patterns from the futon3 library into 8-bit exotype space using
the hexagram skeleton as anchors. Enables all patterns to be executable
in any domain.

Method: PCA(32) + ridge regression on 320 anchor points (64 hexagrams +
256 exotypes). 57.9% bit accuracy on holdout hexagrams; domain coherence
strong (corps 1.07, vsat 0.40).

## Key Files

- `scripts/pattern_exotype_bridge.py`
- `scripts/pattern_to_wiring.py`
- `scripts/pattern_to_wiring.clj`
- `resources/pattern-exotype-bridge.edn`
