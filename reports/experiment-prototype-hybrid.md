# Experiment: Prototype Wiring vs Legacy Anchor (Sequential)

Date: 2026-01-25

## Intent
Compare a prototype wiring to the known-good legacy anchor, then compare a hybrid wiring that injects the legacy 工 kernel as a fallback. Run sequentially, document learning per seed, and flag any hot results immediately.

## Models
1) **Baseline legacy 工** (anchor)
   - wiring: `data/xenotype-legacy-wirings/legacy-exotype-gong-super.edn`
2) **Prototype 001 creative peng**
   - wiring: `resources/xenotype-wirings/prototype-001-creative-peng.edn`
3) **Hybrid 001 + 工 fallback**
   - wiring: `data/xenotype-hybrid-wirings/hybrid-prototype-001-gong.edn`
   - fallback: legacy 工 kernel when diversity < 0.45

## Seeds (10 → 30 runs)
4242, 238310129, 1281562706, 71312373, 352362012, 201709760, 1564354771, 955468957, 1701878710, 1695261645

## Dimensions
Length 100, generations 100.

## Classifier rules
Collapsed if white or black ratio > 0.85 on final row. Frozen if last N rows identical (N=min(10, max(2, generations/2))). Otherwise candidate.

---

## Run Log
### Seed 4242

- Baseline status: candidate (white=0.380, black=0.620, source=phenotype)
- Prototype status: candidate (white=0.520, black=0.480, source=phenotype)
- Hybrid status: candidate (white=0.520, black=0.480, source=phenotype)

- Baseline summary: composite=22.14, avg-entropy-n=0.429, avg-change=0.288, temporal=0.883
- Prototype summary: composite=46.13, avg-entropy-n=0.948, avg-change=0.995, temporal=0.500
- Hybrid summary: composite=46.13, avg-entropy-n=0.948, avg-change=0.995, temporal=0.500
- Δ prototype - baseline: 24.00
- Δ hybrid - baseline: 24.00

Notes: sequential seed; no parameter changes mid-run.

### Seed 238310129

- Baseline status: candidate (white=0.390, black=0.610, source=phenotype)
- Prototype status: candidate (white=0.570, black=0.430, source=phenotype)
- Hybrid status: candidate (white=0.540, black=0.460, source=phenotype)

- Baseline summary: composite=19.94, avg-entropy-n=0.376, avg-change=0.298, temporal=0.932
- Prototype summary: composite=45.81, avg-entropy-n=0.944, avg-change=0.995, temporal=0.501
- Hybrid summary: composite=45.70, avg-entropy-n=0.944, avg-change=0.995, temporal=0.496
- Δ prototype - baseline: 25.87
- Δ hybrid - baseline: 25.76

Notes: sequential seed; no parameter changes mid-run.

### Seed 1281562706

- Baseline status: candidate (white=0.450, black=0.550, source=phenotype)
- Prototype status: candidate (white=0.470, black=0.530, source=phenotype)
- Hybrid status: candidate (white=0.470, black=0.530, source=phenotype)

- Baseline summary: composite=22.61, avg-entropy-n=0.477, avg-change=0.302, temporal=0.901
- Prototype summary: composite=45.80, avg-entropy-n=0.946, avg-change=0.996, temporal=0.493
- Hybrid summary: composite=45.80, avg-entropy-n=0.946, avg-change=0.996, temporal=0.493
- Δ prototype - baseline: 23.18
- Δ hybrid - baseline: 23.18

Notes: sequential seed; no parameter changes mid-run.

### Seed 71312373

- Baseline status: candidate (white=0.470, black=0.530, source=phenotype)
- Prototype status: candidate (white=0.550, black=0.450, source=phenotype)
- Hybrid status: candidate (white=0.550, black=0.450, source=phenotype)

- Baseline summary: composite=22.77, avg-entropy-n=0.488, avg-change=0.279, temporal=0.881
- Prototype summary: composite=45.80, avg-entropy-n=0.947, avg-change=0.997, temporal=0.509
- Hybrid summary: composite=45.80, avg-entropy-n=0.947, avg-change=0.997, temporal=0.509
- Δ prototype - baseline: 23.03
- Δ hybrid - baseline: 23.03

Notes: sequential seed; no parameter changes mid-run.

### Seed 352362012

- Baseline status: candidate (white=0.460, black=0.540, source=phenotype)
- Prototype status: candidate (white=0.500, black=0.500, source=phenotype)
- Hybrid status: candidate (white=0.440, black=0.560, source=phenotype)

- Baseline summary: composite=20.88, avg-entropy-n=0.452, avg-change=0.290, temporal=0.928
- Prototype summary: composite=45.92, avg-entropy-n=0.946, avg-change=0.996, temporal=0.500
- Hybrid summary: composite=45.97, avg-entropy-n=0.946, avg-change=0.997, temporal=0.500
- Δ prototype - baseline: 25.05
- Δ hybrid - baseline: 25.09

Notes: sequential seed; no parameter changes mid-run.

### Seed 201709760

- Baseline status: candidate (white=0.390, black=0.610, source=phenotype)
- Prototype status: candidate (white=0.540, black=0.460, source=phenotype)
- Hybrid status: candidate (white=0.540, black=0.460, source=phenotype)

- Baseline summary: composite=19.90, avg-entropy-n=0.424, avg-change=0.252, temporal=0.939
- Prototype summary: composite=45.98, avg-entropy-n=0.947, avg-change=0.996, temporal=0.499
- Hybrid summary: composite=45.98, avg-entropy-n=0.947, avg-change=0.996, temporal=0.499
- Δ prototype - baseline: 26.09
- Δ hybrid - baseline: 26.09

Notes: sequential seed; no parameter changes mid-run.

### Seed 1564354771

- Baseline status: candidate (white=0.700, black=0.300, source=phenotype)
- Prototype status: candidate (white=0.530, black=0.470, source=phenotype)
- Hybrid status: candidate (white=0.530, black=0.470, source=phenotype)

- Baseline summary: composite=22.91, avg-entropy-n=0.547, avg-change=0.374, temporal=0.902
- Prototype summary: composite=45.76, avg-entropy-n=0.945, avg-change=0.996, temporal=0.496
- Hybrid summary: composite=45.76, avg-entropy-n=0.945, avg-change=0.996, temporal=0.496
- Δ prototype - baseline: 22.85
- Δ hybrid - baseline: 22.85

Notes: sequential seed; no parameter changes mid-run.

### Seed 955468957

- Baseline status: candidate (white=0.430, black=0.570, source=phenotype)
- Prototype status: candidate (white=0.510, black=0.490, source=phenotype)
- Hybrid status: candidate (white=0.510, black=0.490, source=phenotype)

- Baseline summary: composite=24.49, avg-entropy-n=0.611, avg-change=0.301, temporal=0.870
- Prototype summary: composite=45.86, avg-entropy-n=0.948, avg-change=0.996, temporal=0.508
- Hybrid summary: composite=45.86, avg-entropy-n=0.948, avg-change=0.996, temporal=0.508
- Δ prototype - baseline: 21.37
- Δ hybrid - baseline: 21.37

Notes: sequential seed; no parameter changes mid-run.

### Seed 1701878710

- Baseline status: candidate (white=0.650, black=0.350, source=phenotype)
- Prototype status: candidate (white=0.490, black=0.510, source=phenotype)
- Hybrid status: candidate (white=0.490, black=0.510, source=phenotype)

- Baseline summary: composite=26.94, avg-entropy-n=0.657, avg-change=0.618, temporal=0.837
- Prototype summary: composite=45.90, avg-entropy-n=0.946, avg-change=0.997, temporal=0.505
- Hybrid summary: composite=45.90, avg-entropy-n=0.946, avg-change=0.997, temporal=0.505
- Δ prototype - baseline: 18.96
- Δ hybrid - baseline: 18.96

Notes: sequential seed; no parameter changes mid-run.

### Seed 1695261645

- Baseline status: candidate (white=0.550, black=0.450, source=phenotype)
- Prototype status: candidate (white=0.450, black=0.550, source=phenotype)
- Hybrid status: candidate (white=0.450, black=0.550, source=phenotype)

- Baseline summary: composite=22.12, avg-entropy-n=0.512, avg-change=0.322, temporal=0.926
- Prototype summary: composite=45.80, avg-entropy-n=0.946, avg-change=0.996, temporal=0.505
- Hybrid summary: composite=45.80, avg-entropy-n=0.946, avg-change=0.996, temporal=0.505
- Δ prototype - baseline: 23.68
- Δ hybrid - baseline: 23.68

Notes: sequential seed; no parameter changes mid-run.

