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

---

## Boundary-aware rerun (diversity low/high -> legacy)
Date: 2026-01-25

Low threshold 0.35, high 0.7. Classifier includes hotness/burstiness.

### Seed 4242

- Baseline legacy 工 status: candidate (white=0.480, black=0.520, source=phenotype)
- Baseline legacy 工 summary: composite=23.09, avg-entropy-n=0.476, avg-change=0.333, temporal=0.887
- Prototype 001 creative peng status: candidate (white=0.520, black=0.480, source=phenotype)
- Prototype 001 creative peng summary: composite=46.13, avg-entropy-n=0.948, avg-change=0.995, temporal=0.500
- Hybrid 001 + 工 fallback status: candidate (white=0.230, black=0.770, source=phenotype)
- Hybrid 001 + 工 fallback summary: composite=28.25, avg-entropy-n=0.526, avg-change=0.689, temporal=0.738

Notes: boundary-aware hybrid; no parameter changes mid-run.

### Seed 238310129

- Baseline legacy 工 status: candidate (white=0.450, black=0.550, source=phenotype)
- Baseline legacy 工 summary: composite=23.01, avg-entropy-n=0.456, avg-change=0.355, temporal=0.900
- Prototype 001 creative peng status: candidate (white=0.560, black=0.440, source=phenotype)
- Prototype 001 creative peng summary: composite=45.78, avg-entropy-n=0.944, avg-change=0.994, temporal=0.501
- Hybrid 001 + 工 fallback status: candidate (white=0.380, black=0.620, source=phenotype)
- Hybrid 001 + 工 fallback summary: composite=28.61, avg-entropy-n=0.562, avg-change=0.706, temporal=0.728

Notes: boundary-aware hybrid; no parameter changes mid-run.

### Seed 1281562706

- Baseline legacy 工 status: candidate (white=0.640, black=0.360, source=phenotype)
- Baseline legacy 工 summary: composite=21.24, avg-entropy-n=0.459, avg-change=0.268, temporal=0.911
- Prototype 001 creative peng status: candidate (white=0.470, black=0.530, source=phenotype)
- Prototype 001 creative peng summary: composite=45.80, avg-entropy-n=0.946, avg-change=0.996, temporal=0.493
- Hybrid 001 + 工 fallback status: candidate (white=0.380, black=0.620, source=phenotype)
- Hybrid 001 + 工 fallback summary: composite=28.88, avg-entropy-n=0.517, avg-change=0.678, temporal=0.722

Notes: boundary-aware hybrid; no parameter changes mid-run.

### Seed 71312373

- Baseline legacy 工 status: candidate (white=0.440, black=0.560, source=phenotype)
- Baseline legacy 工 summary: composite=23.73, avg-entropy-n=0.501, avg-change=0.285, temporal=0.862
- Prototype 001 creative peng status: candidate (white=0.550, black=0.450, source=phenotype)
- Prototype 001 creative peng summary: composite=45.80, avg-entropy-n=0.947, avg-change=0.997, temporal=0.509
- Hybrid 001 + 工 fallback status: candidate (white=0.400, black=0.600, source=phenotype)
- Hybrid 001 + 工 fallback summary: composite=28.80, avg-entropy-n=0.506, avg-change=0.615, temporal=0.748

Notes: boundary-aware hybrid; no parameter changes mid-run.

### Seed 352362012

- Baseline legacy 工 status: candidate (white=0.380, black=0.620, source=phenotype)
- Baseline legacy 工 summary: composite=24.37, avg-entropy-n=0.584, avg-change=0.356, temporal=0.876
- Prototype 001 creative peng status: candidate (white=0.520, black=0.480, source=phenotype)
- Prototype 001 creative peng summary: composite=45.88, avg-entropy-n=0.945, avg-change=0.997, temporal=0.503
- Hybrid 001 + 工 fallback status: candidate (white=0.470, black=0.530, source=phenotype)
- Hybrid 001 + 工 fallback summary: composite=28.23, avg-entropy-n=0.541, avg-change=0.699, temporal=0.736

Notes: boundary-aware hybrid; no parameter changes mid-run.

### Seed 201709760

- Baseline legacy 工 status: candidate (white=0.390, black=0.610, source=phenotype)
- Baseline legacy 工 summary: composite=21.40, avg-entropy-n=0.462, avg-change=0.285, temporal=0.914
- Prototype 001 creative peng status: candidate (white=0.540, black=0.460, source=phenotype)
- Prototype 001 creative peng summary: composite=45.98, avg-entropy-n=0.947, avg-change=0.996, temporal=0.499
- Hybrid 001 + 工 fallback status: candidate (white=0.530, black=0.470, source=phenotype)
- Hybrid 001 + 工 fallback summary: composite=28.42, avg-entropy-n=0.515, avg-change=0.657, temporal=0.743

Notes: boundary-aware hybrid; no parameter changes mid-run.

### Seed 1564354771

- Baseline legacy 工 status: candidate (white=0.550, black=0.450, source=phenotype)
- Baseline legacy 工 summary: composite=25.61, avg-entropy-n=0.548, avg-change=0.406, temporal=0.836
- Prototype 001 creative peng status: candidate (white=0.530, black=0.470, source=phenotype)
- Prototype 001 creative peng summary: composite=45.76, avg-entropy-n=0.945, avg-change=0.996, temporal=0.496
- Hybrid 001 + 工 fallback status: candidate (white=0.430, black=0.570, source=phenotype)
- Hybrid 001 + 工 fallback summary: composite=28.63, avg-entropy-n=0.519, avg-change=0.683, temporal=0.731

Notes: boundary-aware hybrid; no parameter changes mid-run.

### Seed 955468957

- Baseline legacy 工 status: candidate (white=0.500, black=0.500, source=phenotype)
- Baseline legacy 工 summary: composite=27.24, avg-entropy-n=0.649, avg-change=0.484, temporal=0.841
- Prototype 001 creative peng status: candidate (white=0.510, black=0.490, source=phenotype)
- Prototype 001 creative peng summary: composite=45.86, avg-entropy-n=0.948, avg-change=0.996, temporal=0.508
- Hybrid 001 + 工 fallback status: candidate (white=0.430, black=0.570, source=phenotype)
- Hybrid 001 + 工 fallback summary: composite=28.63, avg-entropy-n=0.533, avg-change=0.689, temporal=0.729

Notes: boundary-aware hybrid; no parameter changes mid-run.

### Seed 1701878710

- Baseline legacy 工 status: candidate (white=0.630, black=0.370, source=phenotype)
- Baseline legacy 工 summary: composite=26.80, avg-entropy-n=0.610, avg-change=0.575, temporal=0.839
- Prototype 001 creative peng status: candidate (white=0.490, black=0.510, source=phenotype)
- Prototype 001 creative peng summary: composite=45.90, avg-entropy-n=0.946, avg-change=0.997, temporal=0.505
- Hybrid 001 + 工 fallback status: candidate (white=0.430, black=0.570, source=phenotype)
- Hybrid 001 + 工 fallback summary: composite=28.60, avg-entropy-n=0.549, avg-change=0.694, temporal=0.734

Notes: boundary-aware hybrid; no parameter changes mid-run.

### Seed 1695261645

- Baseline legacy 工 status: candidate (white=0.560, black=0.440, source=phenotype)
- Baseline legacy 工 summary: composite=24.97, avg-entropy-n=0.583, avg-change=0.393, temporal=0.859
- Prototype 001 creative peng status: candidate (white=0.450, black=0.550, source=phenotype)
- Prototype 001 creative peng summary: composite=45.80, avg-entropy-n=0.946, avg-change=0.996, temporal=0.505
- Hybrid 001 + 工 fallback status: candidate (white=0.500, black=0.500, source=phenotype)
- Hybrid 001 + 工 fallback summary: composite=29.33, avg-entropy-n=0.522, avg-change=0.669, temporal=0.709

Notes: boundary-aware hybrid; no parameter changes mid-run.
