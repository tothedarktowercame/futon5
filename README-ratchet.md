# Ratchet Defaults (Mission 0)

This repository uses a **windowed ratchet** to reward improvement over time.
The ratchet computes *delta* features between successive windows and blends the
result into the exotic score.

This file records the default choices we made so they can be revisited later.

## Current defaults

1) **Normalization**
   - Delta values are normalized by the **current window stddev**.
   - A minimum floor of `1.0` is used so normalization never explodes.
   - Rationale: keeps deltas on a stable [-1, 1] scale across regimes.

2) **Delta composition**
   - Weighted delta uses both mean and median (q50):
     - `w_mean = 0.6`
     - `w_q50 = 0.4`
   - Rationale: matches the spec’s intent and reduces sensitivity to outliers.

3) **Curriculum gate**
   - Gate thresholds are applied to **normalized weighted delta**.
   - Rationale: curriculum values are defined in a 0–1 range.

4) **Evidence persistence**
   - Ratchet evidence should live in its own flexiarg section (e.g. `@exotype-ratchet`)
     so that automated syncs do not overwrite it.
   - Evidence retention is intended to keep **best M + last N** (numbers TBD).

## Revisit points

If ratchet behavior looks too flat or too noisy, consider:
 - Using MAD instead of stddev for scale.
 - Adjusting weights (e.g., 0.7/0.3 or 0.5/0.5).
 - Using a smaller floor (e.g., 0.5) for more sensitivity.
 - Tightening curriculum thresholds once normalized deltas are stable.
