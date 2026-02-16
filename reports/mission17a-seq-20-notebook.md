# Mission 17a Sequential Batch (20 runs)

Date: 2026-01-25

## Intent
Run baseline + single-parameter perturbation sequentially across seeds, documenting learning and adjusting only if needed. Known-good anchor is always included per seed.

## Baseline (known-good anchor)
- Sigil: 工 (super)
- update-prob: 0.50
- match-threshold: 0.4444444444444444
- mix-mode: :rotate-left, rotation 0

## Candidate (initial)
- Sigil: 工 (super)
- update-prob: 0.45 (single-variable change)
- match-threshold: 0.4444444444444444
- mix-mode: :rotate-left, rotation 0

## Seeds (10 → 20 runs)
4242, 238310129, 1281562706, 71312373, 352362012, 201709760, 1564354771, 955468957, 1701878710, 1695261645

## Classifier rules
Collapsed if white or black ratio > 0.85 on final row. Frozen if last N rows identical (N=min(10, max(2, generations/2))). Otherwise candidate.

---

## Run Log
### Seed 4242

- Baseline status: candidate (white=0.667, black=0.333, source=phenotype)
- Candidate status: candidate (white=0.642, black=0.358, source=phenotype)

- Baseline summary: composite=20.37, avg-entropy-n=0.417, avg-change=0.298, temporal=0.922
- Candidate summary: composite=21.84, avg-entropy-n=0.515, avg-change=0.318, temporal=0.915
- Δ composite (candidate - baseline): 1.46

Notes: single-seed comparison; no parameter changes after this seed.

### Seed 238310129

- Baseline status: candidate (white=0.517, black=0.483, source=phenotype)
- Candidate status: candidate (white=0.617, black=0.383, source=phenotype)

- Baseline summary: composite=21.26, avg-entropy-n=0.442, avg-change=0.313, temporal=0.913
- Candidate summary: composite=23.41, avg-entropy-n=0.483, avg-change=0.360, temporal=0.879
- Δ composite (candidate - baseline): 2.15

Notes: single-seed comparison; no parameter changes after this seed.

### Seed 1281562706

- Baseline status: candidate (white=0.517, black=0.483, source=phenotype)
- Candidate status: candidate (white=0.617, black=0.383, source=phenotype)

- Baseline summary: composite=22.04, avg-entropy-n=0.473, avg-change=0.297, temporal=0.884
- Candidate summary: composite=22.92, avg-entropy-n=0.525, avg-change=0.337, temporal=0.895
- Δ composite (candidate - baseline): 0.88

Notes: single-seed comparison; no parameter changes after this seed.

### Seed 71312373

- Baseline status: candidate (white=0.492, black=0.508, source=phenotype)
- Candidate status: candidate (white=0.625, black=0.375, source=phenotype)

- Baseline summary: composite=22.86, avg-entropy-n=0.462, avg-change=0.318, temporal=0.859
- Candidate summary: composite=23.04, avg-entropy-n=0.447, avg-change=0.377, temporal=0.863
- Δ composite (candidate - baseline): 0.18

Notes: single-seed comparison; no parameter changes after this seed.

### Seed 352362012

- Baseline status: candidate (white=0.608, black=0.392, source=phenotype)
- Candidate status: candidate (white=0.608, black=0.392, source=phenotype)

- Baseline summary: composite=25.10, avg-entropy-n=0.562, avg-change=0.361, temporal=0.839
- Candidate summary: composite=26.36, avg-entropy-n=0.596, avg-change=0.410, temporal=0.804
- Δ composite (candidate - baseline): 1.25

Notes: single-seed comparison; no parameter changes after this seed.

### Seed 201709760

- Baseline status: candidate (white=0.517, black=0.483, source=phenotype)
- Candidate status: candidate (white=0.525, black=0.475, source=phenotype)

- Baseline summary: composite=26.06, avg-entropy-n=0.514, avg-change=0.424, temporal=0.833
- Candidate summary: composite=27.17, avg-entropy-n=0.603, avg-change=0.603, temporal=0.810
- Δ composite (candidate - baseline): 1.11

Notes: single-seed comparison; no parameter changes after this seed.

### Seed 1564354771

- Baseline status: candidate (white=0.442, black=0.558, source=phenotype)
- Candidate status: candidate (white=0.433, black=0.567, source=phenotype)

- Baseline summary: composite=23.93, avg-entropy-n=0.477, avg-change=0.384, temporal=0.858
- Candidate summary: composite=24.98, avg-entropy-n=0.516, avg-change=0.394, temporal=0.849
- Δ composite (candidate - baseline): 1.05

Notes: single-seed comparison; no parameter changes after this seed.

### Seed 955468957

- Baseline status: candidate (white=0.508, black=0.492, source=phenotype)
- Candidate status: candidate (white=0.467, black=0.533, source=phenotype)

- Baseline summary: composite=30.49, avg-entropy-n=0.558, avg-change=0.495, temporal=0.708
- Candidate summary: composite=30.08, avg-entropy-n=0.605, avg-change=0.512, temporal=0.723
- Δ composite (candidate - baseline): -0.40

Notes: single-seed comparison; no parameter changes after this seed.

### Seed 1701878710

- Baseline status: candidate (white=0.475, black=0.525, source=phenotype)
- Candidate status: candidate (white=0.392, black=0.608, source=phenotype)

- Baseline summary: composite=20.70, avg-entropy-n=0.395, avg-change=0.282, temporal=0.889
- Candidate summary: composite=20.96, avg-entropy-n=0.403, avg-change=0.268, temporal=0.879
- Δ composite (candidate - baseline): 0.26

Notes: single-seed comparison; no parameter changes after this seed.

### Seed 1695261645

- Baseline status: candidate (white=0.525, black=0.475, source=phenotype)
- Candidate status: candidate (white=0.525, black=0.475, source=phenotype)

- Baseline summary: composite=26.39, avg-entropy-n=0.712, avg-change=0.486, temporal=0.860
- Candidate summary: composite=26.66, avg-entropy-n=0.698, avg-change=0.554, temporal=0.858
- Δ composite (candidate - baseline): 0.27

Notes: single-seed comparison; no parameter changes after this seed.

---

## Interim Summary (after 10 seeds / 20 runs)
- Collapse/frozen: none (all runs classified as candidate).
- Mean composite-score: baseline 23.92, candidate 24.74 (Δ +0.82).
- Interpretation: u=0.45 is not hotter than baseline and is slightly higher on composite; no change made.
