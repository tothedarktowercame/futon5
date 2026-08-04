# Slice 11 — blend strength x mutation rate (baseline arm)

Config: {:seeds 8, :lambda 0.55, :steps 6000, :width 80, :prevalence-radius 1, :checkpoints [0 120 600 1200 3000 6000], :damage-steps 59, :tau 0.3, :workers 24}

| beta | mu | verdict | dominant | kinds>15% | domain rows | confetti | P dmg | G dmg | X dmg |
|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|
| 0.0 | 0.0 | ? | null | null | null | null | 1.250 | 1.000 | 0.000 |
| 0.05 | 0.0 | ? | null | null | null | null | 1.375 | 2.000 | 0.000 |
| 0.1 | 0.0 | ? | null | null | null | null | 0.000 | 2.000 | 0.000 |
| 0.2 | 0.0 | ? | null | null | null | null | 0.000 | 0.875 | 0.000 |
| 0.35 | 0.0 | ? | null | null | null | null | 0.000 | 0.000 | 0.000 |
| 0.5 | 0.0 | ? | null | null | null | null | 0.000 | 2.250 | 0.000 |
| 0.7 | 0.0 | ? | null | null | null | null | 2.250 | 0.000 | 0.000 |
| 1.0 | 0.0 | ? | null | null | null | null | 0.000 | 0.000 | 0.000 |
| 0.0 | 0.03 | ? | null | null | null | null | 7.875 | 2.625 | 6.625 |
| 0.05 | 0.03 | ? | null | null | null | null | 3.625 | 7.625 | 9.750 |
| 0.1 | 0.03 | ? | null | null | null | null | 2.375 | 5.875 | 5.000 |
| 0.2 | 0.03 | ? | null | null | null | null | 6.375 | 10.625 | 3.625 |
| 0.35 | 0.03 | ? | null | null | null | null | 6.625 | 14.500 | 5.000 |
| 0.5 | 0.03 | ? | null | null | null | null | 1.875 | 1.375 | 1.250 |
| 0.7 | 0.03 | ? | null | null | null | null | 2.625 | 3.250 | 2.125 |
| 1.0 | 0.03 | ? | null | null | null | null | 1.625 | 5.000 | 0.000 |
| 0.0 | 0.1 | ? | null | null | null | null | 7.625 | 6.125 | 3.875 |
| 0.05 | 0.1 | ? | null | null | null | null | 3.250 | 7.750 | 8.250 |
| 0.1 | 0.1 | ? | null | null | null | null | 7.125 | 9.875 | 5.625 |
| 0.2 | 0.1 | ? | null | null | null | null | 3.625 | 6.500 | 6.750 |
| 0.35 | 0.1 | ? | null | null | null | null | 13.250 | 9.750 | 3.500 |
| 0.5 | 0.1 | ? | null | null | null | null | 5.750 | 5.125 | 2.375 |
| 0.7 | 0.1 | ? | null | null | null | null | 0.625 | 4.625 | 0.250 |
| 1.0 | 0.1 | ? | null | null | null | null | 3.625 | 1.125 | 0.375 |
| 0.0 | 0.3 | ? | null | null | null | null | 13.250 | 2.875 | 4.875 |
| 0.05 | 0.3 | ? | null | null | null | null | 10.875 | 4.625 | 2.500 |
| 0.1 | 0.3 | ? | null | null | null | null | 14.000 | 2.625 | 4.625 |
| 0.2 | 0.3 | ? | null | null | null | null | 16.000 | 0.000 | 14.750 |
| 0.35 | 0.3 | ? | null | null | null | null | 18.625 | 3.625 | 11.250 |
| 0.5 | 0.3 | ? | null | null | null | null | 15.750 | 3.625 | 13.375 |
| 0.7 | 0.3 | ? | null | null | null | null | 14.375 | 0.500 | 10.625 |
| 1.0 | 0.3 | ? | null | null | null | null | 16.375 | 12.625 | 13.500 |
| 0.0 | 1.0 | ? | null | null | null | null | 9.250 | 0.000 | 0.500 |
| 0.05 | 1.0 | ? | null | null | null | null | 9.750 | 0.000 | 1.500 |
| 0.1 | 1.0 | ? | null | null | null | null | 7.625 | 2.375 | 4.500 |
| 0.2 | 1.0 | ? | null | null | null | null | 12.000 | 1.750 | 9.875 |
| 0.35 | 1.0 | ? | null | null | null | null | 5.625 | 3.000 | 5.000 |
| 0.5 | 1.0 | ? | null | null | null | null | 8.625 | 13.125 | 10.750 |
| 0.7 | 1.0 | ? | null | null | null | null | 10.125 | 6.125 | 12.375 |
| 1.0 | 1.0 | ? | null | null | null | null | 2.875 | 6.500 | 6.375 |
