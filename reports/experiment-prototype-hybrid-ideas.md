# Notebook: Prototype/Hybrid Follow‑ups (Ideas)

Date: 2026-01-25

## Context
The prototype + hybrid wiring shows near‑max entropy/change in every seed, while baseline is mixed. Hybrid fallback never appears to trigger.

## Ideas to test (next runs)
1) **Adjust hybrid fallback trigger**
   - Lower diversity threshold (e.g., < 0.30) or add a second trigger on temporal stability.
   - Consider a gradual blend rather than hard switch to legacy.

2) **Add a “hotness” detector**
   - Classify as hot if avg‑entropy‑n > 0.90 AND avg‑change > 0.90, even when white/black ratio is balanced.
   - Log a “hotness score” (e.g., mean of entropy + change + inverse temporal).

3) **Compare against known‑good generator wiring**
   - Re‑implement a subset of “known good” historical runs as generator‑first wiring.
   - Run them with the same seed list and output format for a fair comparison.

4) **Introduce spatial window evaluation**
   - Add a windowed spatial evaluator (e.g., banding detection) to explicitly seek EoC bands.
   - Compare its effect on generator evolution vs current global metrics.

5) **Two‑stage selection**
   - Stage 1: filter for non‑collapsed, non‑hot.
   - Stage 2: rank by spatial heterogeneity (banding, structured regions).

6) **Seed‑locked diagnostics**
   - For one or two canonical seeds, generate detailed diagnostics: time‑slices, banding histograms, and wiring graph summary.

## Proposed immediate action
- Tighten classifier to include “hotness” rule and re‑label existing runs.
- Lower hybrid fallback threshold and re‑run the same 10 seeds to see if hybrid diverges.

