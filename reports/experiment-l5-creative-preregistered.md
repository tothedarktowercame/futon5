# Preregistered Hypotheses: L5‑Creative Gate Tuning (Exotype‑Trace Aware)

Date: 2026-01-25

## Goal
Sustain genotype Rule‑90‑like structure (bitplane triangles) **without** phenotype barcode collapse and **without** runaway exotype chaos.

## Exotype Data Definition (Global‑Kernel Mode)
We are **not** running local‑physics exotypes here. Instead, we define an
**exotype trace** from the local 36‑bit context:

```
pred(8) + self(8) + succ(8) + prev(8) + phenotype‑family(4) = 36 bits
```

We use the full 36‑bit context for metrics (unique count + change rate).
The exotype panel in renders is a **folded 36→24 bit** visualization to RGB
(context‑folded colors).

## Hypotheses (with predictions)

**H1: Creative‑path overuse drives exotype chaos.**  
*Prediction*: Increasing threshold (0.6–0.7) reduces exotype‑trace change/diversity
while preserving genotype triangles.  
*Falsifier*: Exotype‑trace change stays maximal or genotype triangles collapse.

**H2: Boundary‑aware gating stabilizes without killing structure.**  
*Prediction*: Boundary‑aware gate yields higher genotype triangle scores **and**
lower barcode/frozen ratios than diversity‑only gate.  
*Falsifier*: No improvement vs baseline.

**H3: Genotype structure lives in bitplanes, not projections.**  
*Prediction*: Working candidates retain high genotype bitplane triangle scores even
when phenotype is only moderate.  
*Falsifier*: Visual structure without triangle signal, or triangle signal without
visual structure.

**H4: Exotype turbulence can be tolerated if phenotype remains non‑barcode.**  
*Prediction*: Exotype‑trace change may remain high while phenotype stays within
EoC‑ish band (change 0.15–0.45, frozen < 0.5).  
*Falsifier*: Exotype‑trace chaos tightly correlates with phenotype barcode.

## Planned Experiments

### Phase 1 — Threshold Sweep (single variable)
- Wiring: `data/wiring-ladder/level-5-creative.edn`
- Thresholds: **0.4, 0.5, 0.6, 0.7**
- Seeds: **352362012**, **4242**
- Metrics:
  - Genotype bitplane triangle score (max across planes)
  - Phenotype change rate + frozen ratio + band score
  - **Exotype‑trace** unique count + change rate (36‑bit context)

### Phase 2 — Boundary‑Aware Gate (single wiring change)
- Replace diversity‑only gate with chaos/freeze‑aware gate.
- Same seeds, same metrics.

### Phase 3 — Cross‑Domain Check (selective)
- Only for the top 1–2 candidates from Phase 2.
- Patchy + sparse ants transfer benchmark.

## Success Criteria (Target)
- Genotype: triangle score ≥ **0.10**
- Phenotype: change **0.15–0.45**, frozen **< 0.50**, band score **> 0.20**
- Exotype‑trace: avoid “max‑chaos” if possible (unique + change significantly below 1.0)

## Results

### Phase 1 — Threshold Sweep
Run dir: `/tmp/exp-l5-threshold-sweep-1769348128/runs`

Summary:

| Threshold | Seed | Exo‑trace unique (avg) | Exo‑trace change (avg) | Gen triangle score | Phe band score | Phe change |
|-----------|------|-------------------------|------------------------|--------------------|----------------|-----------|
| 0.4 | 352362012 | 100.0 | 1.000 | 0.133 | 0.13 | 0.500 |
| 0.4 | 4242 | 100.0 | 1.000 | 0.131 | 0.13 | 0.500 |
| 0.5 | 352362012 | 100.0 | 1.000 | 0.133 | 0.13 | 0.500 |
| 0.5 | 4242 | 100.0 | 1.000 | 0.131 | 0.13 | 0.500 |
| 0.6 | 352362012 | 100.0 | 1.000 | 0.133 | 0.13 | 0.500 |
| 0.6 | 4242 | 100.0 | 1.000 | 0.131 | 0.13 | 0.500 |
| 0.7 | 352362012 | 100.0 | 0.9998 | 0.113 | 0.10 | 0.499 |
| 0.7 | 4242 | 100.0 | 1.000 | 0.088 | 0.19 | 0.492 |

Observations:
- Thresholds **0.4–0.6 produce identical outcomes** for both seeds.
- Threshold 0.7 reduces genotype triangle scores and shifts planes.
- Phenotype band scores remain low (mostly‑chaotic), below target (>0.20).
- Exotype‑trace metrics are **maximal** across thresholds (unique≈100, change≈1.0).

Hypothesis check (Phase 1):
- **H1** not supported: exotype‑trace chaos did **not** decrease with higher thresholds.
- **H3** still supported: bitplane triangles persist where vote projection fails.

### Phase 2 — Boundary‑Aware Gate
Run dir: `/tmp/exp-l5-boundary-1769350374/runs`

Summary:

| Model | Seed | Exo‑trace unique (avg) | Exo‑trace change (avg) | Gen triangle score | Phe band score | Phe change |
|-------|------|-------------------------|------------------------|--------------------|----------------|-----------|
| L5-boundary | 352362012 | 48.80 | 0.451 | 0.302 | 0.10 | 0.087 |
| L5-boundary | 4242 | 46.82 | 0.435 | 0.587 | 0.12 | 0.084 |

Observations:
- Exotype‑trace **drops sharply** vs Phase 1 (unique ~47–49, change ~0.44–0.45).
- Genotype triangle scores **increase**, especially for seed 4242.
- Phenotype change is **very low** (≈0.084–0.087) with sparse‑activity band interpretation.
- Phenotype band score remains **below target** (>0.20); behavior looks too frozen/cool.

Hypothesis check (Phase 2):
- **H2 partially supported**: boundary gate improves genotype triangles and reduces exotype chaos,
  but pushes phenotype out of the EoC band.

### Phase 3 — Cross‑Domain Check
Run dir: `/tmp/exp-l5-boundary-1769350374`

Setup:
- Wiring: `/tmp/exp-l5-threshold-sweep-1769348128/level-5-creative-boundary.edn`
- Conversion: `/tmp/cyberant-compare-boundary-1769353234/*`
- Benchmark: `futon2` `ants.compare`
- Runs: 20, Ticks: 300

Summary (snowdrift/default):
- Mean score: cyber (wiring) **127.29**, cyber‑sigil **133.15**
- Win rate (cyber > sigil): **0.30** (6/20)
- Termination: all runs ended by queen starvation

Summary (patchy):
- Mean score: cyber (wiring) **12.21**, cyber‑sigil **0.00**
- Win rate (cyber > sigil): **1.00** (20/20)
- Termination: all runs ended by queen starvation

Summary (sparse):
- Mean score: cyber (wiring) **10.74**, cyber‑sigil **0.00**
- Win rate (cyber > sigil): **1.00** (20/20)
- Termination: all runs ended by queen starvation

Observation:
- Boundary‑aware wiring does **not** outperform baseline sigils in the default environment.
- Boundary‑aware wiring **dominates** in patchy/sparse environments (sigils collapse to zero).
