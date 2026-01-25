# Preregistered Hypotheses: L5‑Creative Gate Tuning

Date: 2026-01-25

## Goal
Sustain genotype Rule‑90‑like structure (bitplane triangles) **without** phenotype barcode collapse and **without** runaway exotype chaos.

## Hypotheses (with predictions)

**H1: Creative‑path overuse drives exotype chaos.**  
*Rationale*: XOR is allele‑stratified and can dominate dynamics if the gate fires too often.  
*Prediction*: Increasing the diversity threshold (0.6–0.7) reduces creative activation, lowering exotype chaos while preserving genotype triangles.  
*Falsifier*: Triangle score drops sharply or phenotype collapses as threshold increases.

**H2: Boundary‑aware gating stabilizes without killing structure.**  
*Rationale*: Good bands sit between chaos and freeze; reacting to “too chaotic” or “too frozen” should resist barcode collapse.  
*Prediction*: Boundary‑aware gate yields higher genotype triangle scores **and** lower barcode/frozen ratios than pure diversity gating.  
*Falsifier*: No improvement vs baseline.

**H3: Genotype structure lives in bitplanes, not projections.**  
*Rationale*: Bitplane triangle detector is strong; vote projection nearly eliminates signal.  
*Prediction*: Candidates that “work” show high genotype bitplane triangle scores even when phenotype is only moderate.  
*Falsifier*: Visual structure without triangle signal, or triangle signal without visual structure.

**H4: Exotype turbulence can be tolerated if phenotype remains non‑barcode.**  
*Rationale*: L5‑creative shows acceptable phenotype with chaotic exotype; phenotype seems closer to observable EoC.  
*Prediction*: Exotype chaos does not force phenotype barcode collapse in the best thresholds.  
*Falsifier*: Exotype chaos strongly correlates with phenotype barcode in the sweep.

## Planned Experiments

### Phase 1 — Threshold Sweep (single variable)
- Wiring: `data/wiring-ladder/level-5-creative.edn`
- Thresholds: **0.4, 0.5, 0.6, 0.7**
- Seeds: **352362012**, **4242**
- Metrics:
  - Genotype bitplane triangle score (max across planes)
  - Phenotype change rate + frozen ratio + band score
  - Exotype diversity + change rate (if available)

### Phase 2 — Boundary‑Aware Gate (single wiring change)
- Replace diversity‑only gate with chaos/freeze‑aware gate.
- Same seeds, same metrics.

### Phase 3 — Cross‑Domain Check (selective)
- Only for the top 1–2 candidates from Phase 2.
- Patchy + sparse ants transfer benchmark.

## Success Criteria (Target)
- Genotype: triangle score ≥ **0.10**
- Phenotype: change **0.15–0.45**, frozen **< 0.50**, band score **> 0.20**
- Exotype: can be turbulent, but avoid collapse to 3–4 active exotypes if measurable

## Results (to fill)

### Phase 1 — Threshold Sweep
Run dir: `/tmp/exp-l5-threshold-sweep-1769348128/runs`  
Health: `/tmp/exp-l5-threshold-sweep-1769348128/health.md` (all **UNKNOWN**, change ≈ 0.49–0.51, frozen ≈ 0–1%)

Summary (from `:summary` metrics):

| Threshold | Seed | Gen triangle score (max) | Gen triangle plane | Phe band score | Phe change |
|-----------|------|---------------------------|--------------------|----------------|-----------|
| 0.4 | 352362012 | 0.133 | 5 | 0.13 | 0.500 |
| 0.5 | 352362012 | 0.133 | 5 | 0.13 | 0.500 |
| 0.6 | 352362012 | 0.133 | 5 | 0.13 | 0.500 |
| 0.7 | 352362012 | 0.113 | 0 | 0.10 | 0.499 |
| 0.4 | 4242 | 0.131 | 7 | 0.13 | 0.500 |
| 0.5 | 4242 | 0.131 | 7 | 0.13 | 0.500 |
| 0.6 | 4242 | 0.131 | 7 | 0.13 | 0.500 |
| 0.7 | 4242 | 0.088 | 2 | 0.19 | 0.492 |

Notes:
- Thresholds **0.4–0.6 produced identical outcomes** for both seeds.
- Threshold 0.7 reduced genotype triangle score and shifted best plane.
- Phenotype band scores remain low (mostly‑chaotic), below the target (>0.20).
- Exotype metrics were not available in this harness run (diagram‑only model).

#### Exotype Proxy vs True Exotype (illustrative)
Using **L5‑thr‑0.5 / seed 352362012**:
- **Proxy exotype panel** (derived from gen/phe via XOR‑mixed RGB):  
  avg unique per gen ≈ **100.0**, avg change ≈ **1.0**
- **True exotype rules** (local‑physics run, per‑cell rules):  
  avg unique per gen ≈ **60.7**, avg change ≈ **0.758**

Takeaway: the proxy panel is *maximally chaotic* (all cells changing), while
true exotype rules are **less chaotic and more structured**. They are not the
same object.

Hypothesis check (Phase 1):
- **H1** (threshold reduces exotype chaos while preserving triangles): **inconclusive** (no exotype metrics; triangles unchanged until 0.7, where they weaken).
- **H3** (bitplanes > vote projection): still supported (vote projection remains near zero signal in L5 runs).

### Phase 2 — Boundary‑Aware Gate
TBD

### Phase 3 — Cross‑Domain Check
TBD
