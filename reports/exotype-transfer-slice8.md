# Directed exotype genotype-transfer scan — Slice 8

Fixed source offset +1; EIG off (`:next-C`); lambda 0.55, mu 0.1, tau 0.3; width 80; 6000 steps; N=4 paired seeds per transfer fraction.

## Damage

| q | seed | P damage | G damage | X damage |
|---:|---:|---:|---:|---:|
| 0.00 | 20260803 | 12 | 0 | 0 |
| 0.00 | 20260804 | 6 | 0 | 0 |
| 0.00 | 20260805 | 9 | 0 | 0 |
| 0.00 | 20260806 | 7 | 0 | 0 |
| 0.25 | 20260803 | 0 | 0 | 0 |
| 0.25 | 20260804 | 17 | 8 | 0 |
| 0.25 | 20260805 | 26 | 4 | 0 |
| 0.25 | 20260806 | 21 | 0 | 0 |
| 0.50 | 20260803 | 18 | 16 | 0 |
| 0.50 | 20260804 | 35 | 24 | 2 |
| 0.50 | 20260805 | 22 | 0 | 0 |
| 0.50 | 20260806 | 21 | 15 | 0 |
| 0.75 | 20260803 | 0 | 14 | 0 |
| 0.75 | 20260804 | 13 | 0 | 0 |
| 0.75 | 20260805 | 29 | 15 | 0 |
| 0.75 | 20260806 | 35 | 10 | 0 |
| 1.00 | 20260803 | 27 | 19 | 0 |
| 1.00 | 20260804 | 25 | 0 | 0 |
| 1.00 | 20260805 | 29 | 21 | 0 |
| 1.00 | 20260806 | 24 | 13 | 0 |

| q | P damage mean | G damage mean | X damage mean |
|---:|---:|---:|---:|
| 0.00 | 8.500 | 0.000 | 0.000 |
| 0.25 | 16.000 | 3.000 | 0.000 |
| 0.50 | 24.000 | 13.750 | 0.500 |
| 0.75 | 19.250 | 9.750 | 0.000 |
| 1.00 | 26.250 | 13.250 | 0.000 |

## Readout

The unchanged stored-run test confirms byte-identical q=0 behaviour. The directed transfer remains wired: mean G damage rises from 0.000 at q=0 to 3.000 at q=0.25 and 13.750 at q=0.5. No scanned q keeps P damage near the q=0 value of 8.500; the closest is q=0.25 at 16.000. Side by side, slice8 q=0.5 remains pixel-scale genotype confetti like slice7 q=0.5. The fixed direction adds at most faint directional microtexture, not coherent macroscopic domains. The exotype ratchet remains broken because `:next-C` is chaos-dominated; that is independent of this offset test.

## Figures and ratchet checks

### q=0.0

`reports/figures/slice8-q0-triptych.png`

```text

reports/figures/slice8-q0-triptych.png
  dominant kind share   0.991   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.138   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.863   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```

### q=0.25

`reports/figures/slice8-q0.25-triptych.png`

```text

reports/figures/slice8-q0.25-triptych.png
  dominant kind share   0.993   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.138   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.863   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```

### q=0.5

`reports/figures/slice8-q0.5-triptych.png`

```text

reports/figures/slice8-q0.5-triptych.png
  dominant kind share   0.994   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.113   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.887   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```

### q=0.75

`reports/figures/slice8-q0.75-triptych.png`

```text

reports/figures/slice8-q0.75-triptych.png
  dominant kind share   0.994   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.100   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.900   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```

### q=1.0

`reports/figures/slice8-q1.0-triptych.png`

```text

reports/figures/slice8-q1.0-triptych.png
  dominant kind share   0.992   (ratchet: <= 0.49)
  kinds above 15%       1       (ratchet: >= 2)
  domain rows           0.113   (ratchet: >= 0.57)
  confetti rows         0.000   (ratchet: <= 0.00)
  consensus rows        0.887   (informational)

  RATCHET BROKEN: one kind dominates, coexistence lost, structure lost

```
