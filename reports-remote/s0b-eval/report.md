# S0b trajectory evaluation -- 12-kind conditional model

40 seeds, width 80, 300 steps.

EFE-driven dynamics (:efe-full arm, hunger-tracking self-tuning).
Damage reach = Hamming distance at t=100 after single-cell flip.

| blend | activity | geno-div | geno-rules | frozen | exo-kinds | damage | sd |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 0.00 | 0.6263 | 0.0500 | 4.0 | 1.0000 | 1.0 | 5.5 | 5.7 |
| 0.10 | 0.6231 | 0.0513 | 4.1 | 0.9997 | 1.0 | 4.8 | 4.9 |
| 0.25 | 0.6144 | 0.0509 | 4.1 | 1.0000 | 1.0 | 5.7 | 4.7 |
| 0.50 | 0.6331 | 0.0500 | 4.0 | 1.0000 | 1.0 | 4.3 | 4.8 |
| 0.75 | 0.6269 | 0.0500 | 4.0 | 1.0000 | 1.0 | 4.5 | 4.7 |

## Exotype distributions (summed over seeds)

- blend 0.00: {:collapser 80}
- blend 0.10: {:chaos 2, :collapser 80}
- blend 0.25: {:collapser 80}
- blend 0.50: {:collapser 80}
- blend 0.75: {:collapser 80}
