# Notebook: Prototype/Hybrid Follow‑ups (Ideas)

Date: 2026-01-25

## Context
The prototype + hybrid wiring shows near‑max entropy/change in every seed, while baseline is mixed. Hybrid fallback never appears to trigger.

Claude’s good‑band analysis adds key constraints:
- Good bands are **bursty**, not regular oscillations.
- **Phenotype leads** genotype (partial coupling recommended).
- **Boundary effects** matter: good bands sit between frozen and chaotic regions.
- **Clustering**: moderate columns neighbor other moderate columns.

## Ideas to test (next runs)
1) **Boundary‑aware hybrid fallback (replace simple diversity trigger)**
   - Detect neighbor type (frozen / chaotic / moderate) and trigger legacy‑kernel fallback when neighbors are **chaotic** or **frozen**.
   - This aligns with boundary effects: stabilize near chaos, energize near freeze.
   - Consider a blended transition (weighted mix) instead of hard switch.

2) **Add a “hotness + non‑bursty” detector**
   - Classify hot if avg‑entropy‑n > 0.90 AND avg‑change > 0.90 AND run‑lengths show **no long stable runs**.
   - This separates “balanced but too‑active” from bursty‑moderate patterns.

3) **Partial phenotype independence in wiring**
   - Introduce a coupling strength ~0.3–0.5 so phenotype does **not** deterministically follow genotype.
   - Target phe‑leads behavior seen in good bands.

4) **Spatial window evaluation as Stage‑2 ranking**
   - Keep current collapse/freeze filter.
   - Rank survivors by banding metrics from `scripts/good_band_analysis.clj`.
   - Prefer clustered moderate bands and boundary contexts.

5) **Known‑good generator rewires**
   - Re‑implement historical “known good” runs as generator‑first wiring.
   - Compare against boundary‑aware hybrid under the same seed list for fairness.

6) **Seed‑locked diagnostics**
   - For 1–2 canonical seeds, generate extra diagnostics:
     - run‑length histograms
     - banding map
     - wiring graph summary

## Proposed immediate action
- Update classifier to add hotness + burstiness rule.
- Adjust hybrid fallback to be boundary‑aware (replace diversity threshold).
- Re‑run the same 10 seeds to verify hybrid divergence.

