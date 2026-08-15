# S0b trajectory evaluation -- 12-kind conditional model

40 seeds, width 80, 300 steps.

EFE-driven dynamics (:efe-full arm, hunger-tracking self-tuning).
Damage reach = Hamming distance at t=100 after single-cell flip.

| blend | activity | geno-div | geno-rules | frozen | exo-kinds | damage | sd |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 0.00 | 0.6712 | 0.0328 | 2.6 | 0.9963 | 1.0 | 4.7 | 4.9 |
| 0.10 | 0.6794 | 0.0291 | 2.3 | 0.9981 | 1.0 | 5.2 | 5.5 |
| 0.25 | 0.6775 | 0.0344 | 2.8 | 0.9947 | 1.0 | 4.1 | 5.2 |
| 0.50 | 0.6700 | 0.0325 | 2.6 | 0.9953 | 1.0 | 4.2 | 5.2 |
| 0.75 | 0.6775 | 0.0353 | 2.8 | 0.9944 | 1.0 | 4.7 | 5.3 |

## Exotype distributions (summed over seeds)

- blend 0.00: {:even1 80}
- blend 0.10: {:even1 80}
- blend 0.25: {:even1 80}
- blend 0.50: {:even1 80}
- blend 0.75: {:collapser 1, :even1 80}
