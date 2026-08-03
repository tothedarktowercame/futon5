# Exotype genotype-transfer scan — Slice 7

EIG off (`:next-C`); lambda 0.55, mu 0.1, tau 0.3; width 80; 6000 steps; N=4 seeds per transfer fraction.

## Damage

| q | seed | P damage | G damage | X damage |
|---:|---:|---:|---:|---:|
| 0.00 | 20260803 | 12 | 0 | 0 |
| 0.00 | 20260804 | 6 | 0 | 0 |
| 0.00 | 20260805 | 9 | 0 | 0 |
| 0.00 | 20260806 | 7 | 0 | 0 |
| 0.25 | 20260803 | 8 | 8 | 0 |
| 0.25 | 20260804 | 18 | 11 | 0 |
| 0.25 | 20260805 | 13 | 19 | 0 |
| 0.25 | 20260806 | 14 | 7 | 0 |
| 0.50 | 20260803 | 14 | 1 | 0 |
| 0.50 | 20260804 | 18 | 8 | 0 |
| 0.50 | 20260805 | 8 | 4 | 0 |
| 0.50 | 20260806 | 0 | 0 | 0 |
| 0.75 | 20260803 | 22 | 12 | 0 |
| 0.75 | 20260804 | 21 | 9 | 0 |
| 0.75 | 20260805 | 28 | 0 | 0 |
| 0.75 | 20260806 | 27 | 0 | 0 |
| 1.00 | 20260803 | 37 | 19 | 0 |
| 1.00 | 20260804 | 10 | 22 | 0 |
| 1.00 | 20260805 | 21 | 0 | 0 |
| 1.00 | 20260806 | 29 | 0 | 2 |

| q | P damage mean | G damage mean | X damage mean |
|---:|---:|---:|---:|
| 0.00 | 8.500 | 0.000 | 0.000 |
| 0.25 | 13.250 | 11.250 | 0.000 |
| 0.50 | 10.000 | 3.250 | 0.000 |
| 0.75 | 24.500 | 5.250 | 0.000 |
| 1.00 | 24.250 | 10.250 | 0.500 |

## Readout

The stored-run test confirms that q=0 reproduces the pre-change result byte-identically. Genotype damage rises from 0.000 at q=0 to 11.250 at q=0.25, so the neighbour-transfer mechanism is active. The closest candidate band is q=0.5: mean G damage is 3.250 while mean P damage is 10.000, versus 8.500 at q=0. This is a weak, seed-variable band rather than a robust one. The genotype panel's vertical bands are visibly broken for every nonzero q, becoming fine-grained horizontal activity. The exotype ratchet fails at every q because the `:chaos` kind dominates.

## Figures and ratchet checks

### q=0.0

`reports/figures/slice7-q0-triptych.png`

```text

reports/figures/slice7-q0-triptych.png
  dominant kind share   0.991   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.138   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.863   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```

### q=0.25

`reports/figures/slice7-q0.25-triptych.png`

```text

reports/figures/slice7-q0.25-triptych.png
  dominant kind share   0.994   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.113   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.887   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```

### q=0.5

`reports/figures/slice7-q0.5-triptych.png`

```text

reports/figures/slice7-q0.5-triptych.png
  dominant kind share   0.993   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.100   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.900   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```

### q=0.75

`reports/figures/slice7-q0.75-triptych.png`

```text

reports/figures/slice7-q0.75-triptych.png
  dominant kind share   0.994   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.100   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.900   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```

### q=1.0

`reports/figures/slice7-q1.0-triptych.png`

```text

reports/figures/slice7-q1.0-triptych.png
  dominant kind share   0.993   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.125   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.875   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```
