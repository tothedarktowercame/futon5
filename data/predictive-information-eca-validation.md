# Predictive-information ECA validation

This is the predeclared trust anchor for the active-information-storage (AIS)
discriminator. No measure parameters were changed after observing these results.

## Rule table

Corrected AIS is in bits, reported as mean ± 95% CI over 20 seeds.

| Class | Rule | AIS (bits) |
|---|---:|---:|
| Ordered | 0 | 0.0000 ± 0.0000 |
| Ordered | 8 | 0.0000 ± 0.0000 |
| Ordered | 128 | 0.0000 ± 0.0000 |
| Chaotic | 30 | 0.3555 ± 0.0054 |
| Chaotic | 45 | 0.3448 ± 0.0216 |
| Chaotic | 90 | 0.3722 ± 0.0011 |
| Complex / EoC | 110 | 0.8057 ± 0.0116 |
| Complex / EoC | 54 | 0.7522 ± 0.0131 |
| Complex / EoC | 137 | 0.8092 ± 0.0141 |

## Class comparison

| Class | Corrected AIS (bits; mean ± 95% CI) | Samples |
|---|---:|---:|
| Ordered | 0.0000 ± 0.0000 | 60 |
| Chaotic | 0.3575 ± 0.0078 | 60 |
| Complex / EoC | 0.7891 ± 0.0099 | 60 |

Acceptance was fixed in advance: the complex class's lower 95% bound must be
above the upper 95% bounds of both controls. It passes: `0.7791 > 0.3653`.

## Protocol and estimator

- Standard Wolfram ECA rule convention, fixed-zero boundaries.
- Width 256, 512 updates, seeds 42–61, identical initial conditions per rule.
- AIS = `I(cell past-8; cell next)`, averaged over spatial cells.
- First 128 updates discarded; 385 temporal samples remain per cell.
- Plug-in entropies receive the Miller–Madow small-sample correction; corrected
  MI is clamped at zero when the correction slightly overshoots.
- Full per-seed values and machine-readable protocol are in
  `predictive-information-eca-validation.edn`.
