# Baseline-arm neighbour-agreement blend scan — Slice 10

Deterministic agreement blend over both circular immediate neighbours, selected against the centre rule with probability beta before the exotype propagator; `:baseline` arm; lambda 0.55, mu 0.1, tau 0.3; width 80; 6000 steps; N=8 paired seeds per beta.

## Identity gate

Beta=0 `pr-str` equals the stored pre-change slice6 baseline run: **true**. Its PNG is also byte-identical to the pre-change slice6 baseline (`d760b5b51240cc8e673e775f62dec0f74dc9534a31f8db00249f3e9844b17681`).

## Damage and per-seed spread

| beta | seed | P damage | G damage | X damage |
|---:|---:|---:|---:|---:|
| 0.00 | 20260803 | 6 | 0 | 0 |
| 0.00 | 20260804 | 12 | 10 | 0 |
| 0.00 | 20260805 | 12 | 0 | 10 |
| 0.00 | 20260806 | 9 | 9 | 0 |
| 0.00 | 20260807 | 7 | 0 | 16 |
| 0.00 | 20260808 | 6 | 13 | 1 |
| 0.00 | 20260809 | 1 | 0 | 1 |
| 0.00 | 20260810 | 8 | 17 | 3 |
| 0.10 | 20260803 | 0 | 0 | 4 |
| 0.10 | 20260804 | 17 | 13 | 8 |
| 0.10 | 20260805 | 1 | 0 | 0 |
| 0.10 | 20260806 | 7 | 17 | 6 |
| 0.10 | 20260807 | 12 | 21 | 8 |
| 0.10 | 20260808 | 2 | 8 | 9 |
| 0.10 | 20260809 | 5 | 10 | 6 |
| 0.10 | 20260810 | 13 | 10 | 4 |
| 0.35 | 20260803 | 0 | 12 | 0 |
| 0.35 | 20260804 | 2 | 10 | 8 |
| 0.35 | 20260805 | 27 | 0 | 11 |
| 0.35 | 20260806 | 11 | 27 | 2 |
| 0.35 | 20260807 | 12 | 0 | 0 |
| 0.35 | 20260808 | 18 | 29 | 5 |
| 0.35 | 20260809 | 7 | 0 | 0 |
| 0.35 | 20260810 | 29 | 0 | 2 |
| 0.70 | 20260803 | 0 | 0 | 0 |
| 0.70 | 20260804 | 1 | 7 | 0 |
| 0.70 | 20260805 | 4 | 0 | 2 |
| 0.70 | 20260806 | 0 | 16 | 0 |
| 0.70 | 20260807 | 0 | 14 | 0 |
| 0.70 | 20260808 | 0 | 0 | 0 |
| 0.70 | 20260809 | 0 | 0 | 0 |
| 0.70 | 20260810 | 0 | 0 | 0 |
| 1.00 | 20260803 | 0 | 0 | 0 |
| 1.00 | 20260804 | 0 | 0 | 0 |
| 1.00 | 20260805 | 0 | 6 | 0 |
| 1.00 | 20260806 | 0 | 0 | 0 |
| 1.00 | 20260807 | 0 | 0 | 0 |
| 1.00 | 20260808 | 15 | 3 | 3 |
| 1.00 | 20260809 | 14 | 0 | 0 |
| 1.00 | 20260810 | 0 | 0 | 0 |

| beta | P mean (SD; range) | G mean (SD; range) | X mean (SD; range) |
|---:|---:|---:|---:|
| 0.00 | 7.625 (3.583; 1–12) | 6.125 (6.958; 0–17) | 3.875 (5.939; 0–16) |
| 0.10 | 7.125 (6.266; 0–17) | 9.875 (7.396; 0–21) | 5.625 (2.925; 0–9) |
| 0.35 | 13.250 (10.740; 0–29) | 9.750 (12.268; 0–29) | 3.500 (4.140; 0–11) |
| 0.70 | 0.625 (1.408; 0–4) | 4.625 (6.865; 0–16) | 0.250 (0.707; 0–2) |
| 1.00 | 3.625 (6.718; 0–15) | 1.125 (2.232; 0–6) | 0.375 (1.061; 0–3) |

## Readout

No nonzero beta passes the ratchet while adding the new genotype domains. Beta=0 passes at dominant share 0.425, but every nonzero beta narrowly fails only dominance (0.535–0.544): all retain 2–3 represented kinds, 0.800–0.825 domain rows, and zero confetti rows. Visually, the exotype panel retains broad coexisting green, orange, and red domains despite that formal failure. Genotype spatial structure survives on `:baseline`, clearest at beta=0.7 as extended horizontal same-colour regions, but it is weaker and finer-grained than the large blocks in slice9 beta=0.35. Slice10 beta=0.35 is therefore structured relative to the slice7/slice8 confetti, but plainly less coherent than slice9. The two desired layers are close but do not satisfy their gates simultaneously.

## Figures and ratchet checks

### beta=0.0

`reports/figures/slice10-baseline-beta0.0-triptych.png`

```text

reports/figures/slice10-baseline-beta0.0-triptych.png
  dominant kind share   0.425   (ratchet: <= 0.49)
  kinds above 15%       2       (ratchet: >= 2)
  domain rows           0.812   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.188   (informational)

  RATCHET HELD

```

### beta=0.1

`reports/figures/slice10-baseline-beta0.1-triptych.png`

```text

reports/figures/slice10-baseline-beta0.1-triptych.png
  dominant kind share   0.537   (ratchet: <= 0.49)
  kinds above 15%       3       (ratchet: >= 2)
  domain rows           0.825   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.175   (informational)

  RATCHET BROKEN: one kind dominates

```

### beta=0.35

`reports/figures/slice10-baseline-beta0.35-triptych.png`

```text

reports/figures/slice10-baseline-beta0.35-triptych.png
  dominant kind share   0.535   (ratchet: <= 0.49)
  kinds above 15%       2       (ratchet: >= 2)
  domain rows           0.825   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.175   (informational)

  RATCHET BROKEN: one kind dominates

```

### beta=0.7

`reports/figures/slice10-baseline-beta0.7-triptych.png`

```text

reports/figures/slice10-baseline-beta0.7-triptych.png
  dominant kind share   0.538   (ratchet: <= 0.49)
  kinds above 15%       2       (ratchet: >= 2)
  domain rows           0.800   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.200   (informational)

  RATCHET BROKEN: one kind dominates

```

### beta=1.0

`reports/figures/slice10-baseline-beta1.0-triptych.png`

```text

reports/figures/slice10-baseline-beta1.0-triptych.png
  dominant kind share   0.544   (ratchet: <= 0.49)
  kinds above 15%       2       (ratchet: >= 2)
  domain rows           0.800   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.200   (informational)

  RATCHET BROKEN: one kind dominates

```
