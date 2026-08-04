# Neighbour-agreement genotype-blend scan — Slice 9

Deterministic agreement blend over both circular immediate neighbours (the existing futon5 grid topology), selected against the centre rule with probability beta before the exotype propagator; EIG off (`:next-C`); lambda 0.55, mu 0.1, tau 0.3; width 80; 6000 steps; N=4 paired seeds per blend strength.

## Damage

| beta | seed | P damage | G damage | X damage |
|---:|---:|---:|---:|---:|
| 0.00 | 20260803 | 12 | 0 | 0 |
| 0.00 | 20260804 | 6 | 0 | 0 |
| 0.00 | 20260805 | 9 | 0 | 0 |
| 0.00 | 20260806 | 7 | 0 | 0 |
| 0.10 | 20260803 | 27 | 0 | 3 |
| 0.10 | 20260804 | 16 | 0 | 14 |
| 0.10 | 20260805 | 22 | 0 | 5 |
| 0.10 | 20260806 | 12 | 0 | 0 |
| 0.35 | 20260803 | 31 | 0 | 1 |
| 0.35 | 20260804 | 29 | 0 | 22 |
| 0.35 | 20260805 | 0 | 34 | 41 |
| 0.35 | 20260806 | 37 | 60 | 34 |
| 0.70 | 20260803 | 3 | 0 | 8 |
| 0.70 | 20260804 | 42 | 0 | 9 |
| 0.70 | 20260805 | 19 | 0 | 8 |
| 0.70 | 20260806 | 10 | 0 | 3 |
| 1.00 | 20260803 | 8 | 4 | 13 |
| 1.00 | 20260804 | 0 | 0 | 19 |
| 1.00 | 20260805 | 0 | 0 | 8 |
| 1.00 | 20260806 | 30 | 15 | 16 |

| beta | P damage mean | G damage mean | X damage mean |
|---:|---:|---:|---:|
| 0.00 | 8.500 | 0.000 | 0.000 |
| 0.10 | 19.250 | 0.000 | 5.500 |
| 0.35 | 24.250 | 23.500 | 24.500 |
| 0.70 | 18.500 | 0.000 | 7.000 |
| 1.00 | 9.500 | 4.750 | 14.000 |

## Readout

The beta=0 stored-run test remains byte-identical, and its PNG has the same SHA-256 as slice8 q=0 (`0753df1ae652f9b7f376241c21c11385fae4fb7f825d8260d48ac96a6b68ba3e`). Slice9 beta=0.35 plainly replaces slice8 q=0.5's uniform pixel-scale genotype confetti with contiguous horizontal and block-like same-colour regions: coherent spatial structure appeared. Genotype damage is nonzero at beta=0.35 and beta=1.0, but is seed-sensitive and non-monotone. Beta=1.0 is the scanned damage band: mean G is nonzero (4.750) while mean P is 9.500, near beta=0's 8.500. Beta=0.35 has much stronger mean G damage (23.500), but mean P rises to 24.250. The exotype ratchet remains broken, as expected for the independently chaos-dominated `:next-C` arm; it is not a failure of the blend mechanism.

## Figures and ratchet checks

### beta=0.0

`reports/figures/slice9-beta0.0-triptych.png`

```text

reports/figures/slice9-beta0.0-triptych.png
  dominant kind share   0.991   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.138   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.863   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```

### beta=0.1

`reports/figures/slice9-beta0.1-triptych.png`

```text

reports/figures/slice9-beta0.1-triptych.png
  dominant kind share   0.950   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.350   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.650   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```

### beta=0.35

`reports/figures/slice9-beta0.35-triptych.png`

```text

reports/figures/slice9-beta0.35-triptych.png
  dominant kind share   0.917   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.388   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.613   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```

### beta=0.7

`reports/figures/slice9-beta0.7-triptych.png`

```text

reports/figures/slice9-beta0.7-triptych.png
  dominant kind share   0.898   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.463   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.537   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```

### beta=1.0

`reports/figures/slice9-beta1.0-triptych.png`

```text

reports/figures/slice9-beta1.0-triptych.png
  dominant kind share   0.921   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.338   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.662   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```
