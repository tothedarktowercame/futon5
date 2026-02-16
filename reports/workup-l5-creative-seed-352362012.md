# Workup: L5‑creative‑seed‑352362012

Date: 2026-01-25

## Context
This run comes from the wiring ladder experiment (Level 5 creative path). It appears visually promising.

## Run + Assets
- Run EDN: `/tmp/exp-wiring-ladder2/L5-creative-seed-352362012.edn`
- Health report (MD): `/tmp/L5-creative-seed-352362012-health.md`
- Health report (EDN): `/tmp/L5-creative-seed-352362012-health.edn`
- Visual report PDF (all ladder pages): `/tmp/exp-wiring-ladder2-report/wiring-ladder-report.pdf`
- Single page (2×): `/tmp/L5-creative-seed-352362012@2x.png`

## Wiring (Level 5 creative)
Source: `data/wiring-ladder/level-5-creative.edn`

Key structure:
- Context nodes: pred/self/succ/prev/phe + neighbors
- Diversity score from neighbors
- Legacy path: `legacy-kernel-step` (工, update‑prob 0.5)
- Creative path: `bit-xor` of pred+succ
- Gate: diversity > 0.5 → creative, else legacy

Interpretation: boundary‑guardian pattern — stable by default, creative when local diversity is high.

## Health Classification (phenotype-based)
From `/tmp/L5-creative-seed-352362012-health.edn`:
- Classification: `unknown`
- Final change rate: **0.508**
- Early change rate: **0.509**
- Frozen ratio: **0.0**
- White/black ratio: **0.44 / 0.56**

## Band Analysis (phenotype)
From `scripts/vertical_band_analysis.clj` output:
- Moderate columns: **13%** (13/100)
- Chaotic columns: **83%** (83/100)
- Frozen columns: **0%**
- Band score: **0.130**
- Interpretation: **mostly‑chaotic**
- Row periodicity: **none detected**

## Band Analysis (genotype, symbol-level)
Computed via `band-analysis/analyze-history` with raw symbols (not bit‑coerced):
- Chaotic columns: **100%** (100/100)
- Frozen columns: **0%**
- Band score: **0.0**
- Mean change rate: **0.9967**
- Interpretation: **mostly‑chaotic**
- Row periodicity: **none detected**

## Summary Metrics (run :summary)
From run EDN `:summary`:
- `avg-change` **0.9967** (genotype)
- `avg-entropy-n` **0.9462** (genotype)
- `temporal-autocorr` **0.4998**
- `phe-change` **0.5002**
- `phe/avg-entropy-n` **0.1492**
- `band-score` **0.13**
- `band-interpretation` **:mostly-chaotic**
- `band-row-periodic?` **false**

## Structured Chaos (diagonal coherence heuristic)
Computed with new `structured-chaos-score` (diag autocorr × spatial band × activity):
- **Genotype**: diag‑autocorr **0.0145**, structured‑chaos **0.0**
- **Phenotype**: diag‑autocorr **0.5022**, structured‑chaos **0.1676**

## Stunted Trees (Rule‑90‑style clearings)
Triangle‑clearing detector on **genotype bitplanes**:
- Best plane: **5**
- Triangles found (max across planes): **40**
- Triangle score (best plane): **0.133**
- Density (best plane): **0.396**
- Avg height (max): **3.54**

Vote‑projection (majority of sigil bits; seeded tie‑break):
- Triangles found: **1**
- Triangle score: **0.003**

## Layered Observations
- **Genotype**: visually EoC (structured, not uniform chaos); the **triangle detector on bitplanes** now aligns with the visual “stunted tree” pattern, even though symbol‑level band metrics still read mostly‑chaotic.
- **Phenotype**: acceptable dynamics (change ~0.5, no barcode).
- **Exotype**: appears turbulent/chaotic — may be the “price” of the above.

## Notes
- Health classification is phenotype‑based; summary `avg-change` is genotype‑weighted, so these can diverge.
- This run is a candidate to preserve and test for robustness under nearby seeds or gate thresholds.
