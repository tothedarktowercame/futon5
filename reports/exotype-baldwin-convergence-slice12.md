# Baldwin convergence experiment — Slice 12 preflight

**Result: stopped at the initial-separation gate; the 6000-step convergence sweep was not run.**

## Design and calibration

The paper's exact ECA calibration family was used: Rule 204 (ordered), Rule 110 (complex, used here only as a `critical-proxy`), and Rule 30 (disordered/chaotic). Draft6 explicitly reports no finite-size evidence for a critical point, so Rule 110 is not relabelled as proven critical. The published damage anchors are 1.00, 16.68, and 36.45 respectively.

The requested apparatus was evaluated on `:baseline` at width 80, lambda 0.55, mu 0.1, tau 0.3, damage horizon 59, and eight paired seeds. Two preflights were used: an immediate uniform-genotype seed, and the calibration-faithful version in which the phenotype first receives the paper's pure-ECA burn-in to t*=60 before the exotype arm starts.

## Gate result

Rule 204 has substantially lower mean reach than the other two starts. Rule 110 and Rule 30 are not separated once the actual exotype/Baldwin update is part of the 59-step reach measurement. After the t*=60 burn-in their Baldwin phenotype reaches are 24.750 (SD 5.548, range 16–32) and 29.750 (SD 5.036, range 24–38); the distributions overlap substantially. In the Lamarckian arm they overlap and reverse in their means: 10.875 (SD 5.842, range 0–18) versus 9.500 (SD 7.309, range 0–20).

Therefore the three starts do not actually instantiate three distinguishable initial reaches under this experiment's instrument. A later equality of endpoints could not be interpreted as convergence rather than loss of the initial contrast. Per the preregistered instruction, the test is void and stops here. No triptychs were rendered.

## Summary

| protocol | arm | start | P mean (SD; range) | G mean (SD; range) | X mean (SD; range) |
|---|---|---|---:|---:|---:|
| eca-burn-60 | baldwin-divergence | critical-proxy | 24.750 (5.548; 16–32) | 0.875 (0.354; 0–1) | 0.625 (1.188; 0–3) |
| eca-burn-60 | baldwin-divergence | disordered | 29.750 (5.036; 24–38) | 0.875 (0.354; 0–1) | 1.125 (2.232; 0–6) |
| eca-burn-60 | baldwin-divergence | ordered | 1.750 (2.188; 0–6) | 1.125 (0.641; 0–2) | 1.500 (2.976; 0–8) |
| eca-burn-60 | baldwin-preferences | critical-proxy | 24.750 (5.548; 16–32) | 0.875 (0.354; 0–1) | 0.625 (1.188; 0–3) |
| eca-burn-60 | baldwin-preferences | disordered | 29.750 (5.036; 24–38) | 0.875 (0.354; 0–1) | 1.125 (2.232; 0–6) |
| eca-burn-60 | baldwin-preferences | ordered | 1.750 (2.188; 0–6) | 1.375 (0.744; 0–2) | 1.500 (2.976; 0–8) |
| eca-burn-60 | lamarckian | critical-proxy | 10.875 (5.842; 0–18) | 8.000 (4.536; 0–13) | 1.250 (0.886; 0–3) |
| eca-burn-60 | lamarckian | disordered | 9.500 (7.309; 0–20) | 13.500 (12.750; 0–29) | 3.250 (2.915; 0–9) |
| eca-burn-60 | lamarckian | ordered | 3.875 (3.720; 0–10) | 8.875 (6.792; 1–19) | 2.000 (3.742; 0–9) |
| immediate | baldwin-divergence | critical-proxy | 23.000 (11.326; 0–35) | 1.000 (0.000; 1–1) | 0.375 (1.061; 0–3) |
| immediate | baldwin-divergence | disordered | 25.625 (10.474; 9–40) | 1.000 (0.000; 1–1) | 1.250 (3.536; 0–10) |
| immediate | baldwin-divergence | ordered | 1.125 (1.642; 0–4) | 1.000 (0.535; 0–2) | 0.000 (0.000; 0–0) |
| immediate | baldwin-preferences | critical-proxy | 23.000 (11.326; 0–35) | 1.000 (0.000; 1–1) | 0.375 (1.061; 0–3) |
| immediate | baldwin-preferences | disordered | 25.625 (10.474; 9–40) | 1.000 (0.000; 1–1) | 1.250 (3.536; 0–10) |
| immediate | baldwin-preferences | ordered | 1.125 (1.642; 0–4) | 1.000 (0.535; 0–2) | 0.000 (0.000; 0–0) |
| immediate | lamarckian | critical-proxy | 9.125 (6.643; 0–20) | 9.000 (2.204; 6–12) | 0.625 (0.744; 0–2) |
| immediate | lamarckian | disordered | 11.625 (3.962; 7–17) | 13.625 (5.290; 5–20) | 3.875 (4.422; 0–14) |
| immediate | lamarckian | ordered | 1.750 (2.550; 0–6) | 3.750 (3.151; 0–8) | 1.250 (2.375; 0–6) |

## Full per-seed preflight

| protocol | arm | start | rule | seed | P | G | X |
|---|---|---|---:|---:|---:|---:|---:|
| eca-burn-60 | baldwin-divergence | critical-proxy | 110 | 20260803 | 29 | 0 | 0 |
| eca-burn-60 | baldwin-divergence | critical-proxy | 110 | 20260804 | 25 | 1 | 3 |
| eca-burn-60 | baldwin-divergence | critical-proxy | 110 | 20260805 | 26 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | critical-proxy | 110 | 20260806 | 18 | 1 | 2 |
| eca-burn-60 | baldwin-divergence | critical-proxy | 110 | 20260807 | 23 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | critical-proxy | 110 | 20260808 | 32 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | critical-proxy | 110 | 20260809 | 29 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | critical-proxy | 110 | 20260810 | 16 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | disordered | 30 | 20260803 | 24 | 0 | 6 |
| eca-burn-60 | baldwin-divergence | disordered | 30 | 20260804 | 30 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | disordered | 30 | 20260805 | 35 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | disordered | 30 | 20260806 | 31 | 1 | 3 |
| eca-burn-60 | baldwin-divergence | disordered | 30 | 20260807 | 38 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | disordered | 30 | 20260808 | 26 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | disordered | 30 | 20260809 | 24 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | disordered | 30 | 20260810 | 30 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | ordered | 204 | 20260803 | 0 | 0 | 0 |
| eca-burn-60 | baldwin-divergence | ordered | 204 | 20260804 | 0 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | ordered | 204 | 20260805 | 6 | 1 | 4 |
| eca-burn-60 | baldwin-divergence | ordered | 204 | 20260806 | 4 | 1 | 8 |
| eca-burn-60 | baldwin-divergence | ordered | 204 | 20260807 | 1 | 2 | 0 |
| eca-burn-60 | baldwin-divergence | ordered | 204 | 20260808 | 1 | 2 | 0 |
| eca-burn-60 | baldwin-divergence | ordered | 204 | 20260809 | 0 | 1 | 0 |
| eca-burn-60 | baldwin-divergence | ordered | 204 | 20260810 | 2 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | critical-proxy | 110 | 20260803 | 29 | 0 | 0 |
| eca-burn-60 | baldwin-preferences | critical-proxy | 110 | 20260804 | 25 | 1 | 3 |
| eca-burn-60 | baldwin-preferences | critical-proxy | 110 | 20260805 | 26 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | critical-proxy | 110 | 20260806 | 18 | 1 | 2 |
| eca-burn-60 | baldwin-preferences | critical-proxy | 110 | 20260807 | 23 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | critical-proxy | 110 | 20260808 | 32 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | critical-proxy | 110 | 20260809 | 29 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | critical-proxy | 110 | 20260810 | 16 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | disordered | 30 | 20260803 | 24 | 0 | 6 |
| eca-burn-60 | baldwin-preferences | disordered | 30 | 20260804 | 30 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | disordered | 30 | 20260805 | 35 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | disordered | 30 | 20260806 | 31 | 1 | 3 |
| eca-burn-60 | baldwin-preferences | disordered | 30 | 20260807 | 38 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | disordered | 30 | 20260808 | 26 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | disordered | 30 | 20260809 | 24 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | disordered | 30 | 20260810 | 30 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | ordered | 204 | 20260803 | 0 | 0 | 0 |
| eca-burn-60 | baldwin-preferences | ordered | 204 | 20260804 | 0 | 2 | 0 |
| eca-burn-60 | baldwin-preferences | ordered | 204 | 20260805 | 6 | 1 | 4 |
| eca-burn-60 | baldwin-preferences | ordered | 204 | 20260806 | 4 | 1 | 8 |
| eca-burn-60 | baldwin-preferences | ordered | 204 | 20260807 | 1 | 2 | 0 |
| eca-burn-60 | baldwin-preferences | ordered | 204 | 20260808 | 1 | 2 | 0 |
| eca-burn-60 | baldwin-preferences | ordered | 204 | 20260809 | 0 | 1 | 0 |
| eca-burn-60 | baldwin-preferences | ordered | 204 | 20260810 | 2 | 2 | 0 |
| eca-burn-60 | lamarckian | critical-proxy | 110 | 20260803 | 7 | 11 | 1 |
| eca-burn-60 | lamarckian | critical-proxy | 110 | 20260804 | 13 | 5 | 1 |
| eca-burn-60 | lamarckian | critical-proxy | 110 | 20260805 | 8 | 4 | 0 |
| eca-burn-60 | lamarckian | critical-proxy | 110 | 20260806 | 13 | 10 | 1 |
| eca-burn-60 | lamarckian | critical-proxy | 110 | 20260807 | 0 | 13 | 1 |
| eca-burn-60 | lamarckian | critical-proxy | 110 | 20260808 | 11 | 9 | 1 |
| eca-burn-60 | lamarckian | critical-proxy | 110 | 20260809 | 18 | 12 | 2 |
| eca-burn-60 | lamarckian | critical-proxy | 110 | 20260810 | 17 | 0 | 3 |
| eca-burn-60 | lamarckian | disordered | 30 | 20260803 | 0 | 22 | 0 |
| eca-burn-60 | lamarckian | disordered | 30 | 20260804 | 16 | 2 | 0 |
| eca-burn-60 | lamarckian | disordered | 30 | 20260805 | 12 | 29 | 4 |
| eca-burn-60 | lamarckian | disordered | 30 | 20260806 | 6 | 20 | 3 |
| eca-burn-60 | lamarckian | disordered | 30 | 20260807 | 20 | 1 | 9 |
| eca-burn-60 | lamarckian | disordered | 30 | 20260808 | 0 | 29 | 5 |
| eca-burn-60 | lamarckian | disordered | 30 | 20260809 | 8 | 0 | 2 |
| eca-burn-60 | lamarckian | disordered | 30 | 20260810 | 14 | 5 | 3 |
| eca-burn-60 | lamarckian | ordered | 204 | 20260803 | 4 | 10 | 0 |
| eca-burn-60 | lamarckian | ordered | 204 | 20260804 | 0 | 19 | 0 |
| eca-burn-60 | lamarckian | ordered | 204 | 20260805 | 7 | 1 | 7 |
| eca-burn-60 | lamarckian | ordered | 204 | 20260806 | 10 | 9 | 9 |
| eca-burn-60 | lamarckian | ordered | 204 | 20260807 | 4 | 12 | 0 |
| eca-burn-60 | lamarckian | ordered | 204 | 20260808 | 0 | 1 | 0 |
| eca-burn-60 | lamarckian | ordered | 204 | 20260809 | 0 | 16 | 0 |
| eca-burn-60 | lamarckian | ordered | 204 | 20260810 | 6 | 3 | 0 |
| immediate | baldwin-divergence | critical-proxy | 110 | 20260803 | 19 | 1 | 0 |
| immediate | baldwin-divergence | critical-proxy | 110 | 20260804 | 27 | 1 | 0 |
| immediate | baldwin-divergence | critical-proxy | 110 | 20260805 | 29 | 1 | 0 |
| immediate | baldwin-divergence | critical-proxy | 110 | 20260806 | 0 | 1 | 0 |
| immediate | baldwin-divergence | critical-proxy | 110 | 20260807 | 34 | 1 | 0 |
| immediate | baldwin-divergence | critical-proxy | 110 | 20260808 | 23 | 1 | 3 |
| immediate | baldwin-divergence | critical-proxy | 110 | 20260809 | 35 | 1 | 0 |
| immediate | baldwin-divergence | critical-proxy | 110 | 20260810 | 17 | 1 | 0 |
| immediate | baldwin-divergence | disordered | 30 | 20260803 | 33 | 1 | 10 |
| immediate | baldwin-divergence | disordered | 30 | 20260804 | 23 | 1 | 0 |
| immediate | baldwin-divergence | disordered | 30 | 20260805 | 40 | 1 | 0 |
| immediate | baldwin-divergence | disordered | 30 | 20260806 | 21 | 1 | 0 |
| immediate | baldwin-divergence | disordered | 30 | 20260807 | 36 | 1 | 0 |
| immediate | baldwin-divergence | disordered | 30 | 20260808 | 27 | 1 | 0 |
| immediate | baldwin-divergence | disordered | 30 | 20260809 | 9 | 1 | 0 |
| immediate | baldwin-divergence | disordered | 30 | 20260810 | 16 | 1 | 0 |
| immediate | baldwin-divergence | ordered | 204 | 20260803 | 4 | 0 | 0 |
| immediate | baldwin-divergence | ordered | 204 | 20260804 | 0 | 1 | 0 |
| immediate | baldwin-divergence | ordered | 204 | 20260805 | 0 | 1 | 0 |
| immediate | baldwin-divergence | ordered | 204 | 20260806 | 3 | 1 | 0 |
| immediate | baldwin-divergence | ordered | 204 | 20260807 | 0 | 1 | 0 |
| immediate | baldwin-divergence | ordered | 204 | 20260808 | 2 | 2 | 0 |
| immediate | baldwin-divergence | ordered | 204 | 20260809 | 0 | 1 | 0 |
| immediate | baldwin-divergence | ordered | 204 | 20260810 | 0 | 1 | 0 |
| immediate | baldwin-preferences | critical-proxy | 110 | 20260803 | 19 | 1 | 0 |
| immediate | baldwin-preferences | critical-proxy | 110 | 20260804 | 27 | 1 | 0 |
| immediate | baldwin-preferences | critical-proxy | 110 | 20260805 | 29 | 1 | 0 |
| immediate | baldwin-preferences | critical-proxy | 110 | 20260806 | 0 | 1 | 0 |
| immediate | baldwin-preferences | critical-proxy | 110 | 20260807 | 34 | 1 | 0 |
| immediate | baldwin-preferences | critical-proxy | 110 | 20260808 | 23 | 1 | 3 |
| immediate | baldwin-preferences | critical-proxy | 110 | 20260809 | 35 | 1 | 0 |
| immediate | baldwin-preferences | critical-proxy | 110 | 20260810 | 17 | 1 | 0 |
| immediate | baldwin-preferences | disordered | 30 | 20260803 | 33 | 1 | 10 |
| immediate | baldwin-preferences | disordered | 30 | 20260804 | 23 | 1 | 0 |
| immediate | baldwin-preferences | disordered | 30 | 20260805 | 40 | 1 | 0 |
| immediate | baldwin-preferences | disordered | 30 | 20260806 | 21 | 1 | 0 |
| immediate | baldwin-preferences | disordered | 30 | 20260807 | 36 | 1 | 0 |
| immediate | baldwin-preferences | disordered | 30 | 20260808 | 27 | 1 | 0 |
| immediate | baldwin-preferences | disordered | 30 | 20260809 | 9 | 1 | 0 |
| immediate | baldwin-preferences | disordered | 30 | 20260810 | 16 | 1 | 0 |
| immediate | baldwin-preferences | ordered | 204 | 20260803 | 4 | 0 | 0 |
| immediate | baldwin-preferences | ordered | 204 | 20260804 | 0 | 1 | 0 |
| immediate | baldwin-preferences | ordered | 204 | 20260805 | 0 | 1 | 0 |
| immediate | baldwin-preferences | ordered | 204 | 20260806 | 3 | 1 | 0 |
| immediate | baldwin-preferences | ordered | 204 | 20260807 | 0 | 1 | 0 |
| immediate | baldwin-preferences | ordered | 204 | 20260808 | 2 | 1 | 0 |
| immediate | baldwin-preferences | ordered | 204 | 20260809 | 0 | 1 | 0 |
| immediate | baldwin-preferences | ordered | 204 | 20260810 | 0 | 2 | 0 |
| immediate | lamarckian | critical-proxy | 110 | 20260803 | 13 | 6 | 0 |
| immediate | lamarckian | critical-proxy | 110 | 20260804 | 0 | 11 | 1 |
| immediate | lamarckian | critical-proxy | 110 | 20260805 | 20 | 7 | 0 |
| immediate | lamarckian | critical-proxy | 110 | 20260806 | 0 | 12 | 1 |
| immediate | lamarckian | critical-proxy | 110 | 20260807 | 10 | 11 | 0 |
| immediate | lamarckian | critical-proxy | 110 | 20260808 | 12 | 7 | 0 |
| immediate | lamarckian | critical-proxy | 110 | 20260809 | 9 | 9 | 1 |
| immediate | lamarckian | critical-proxy | 110 | 20260810 | 9 | 9 | 2 |
| immediate | lamarckian | disordered | 30 | 20260803 | 17 | 20 | 2 |
| immediate | lamarckian | disordered | 30 | 20260804 | 10 | 11 | 6 |
| immediate | lamarckian | disordered | 30 | 20260805 | 8 | 17 | 2 |
| immediate | lamarckian | disordered | 30 | 20260806 | 7 | 8 | 3 |
| immediate | lamarckian | disordered | 30 | 20260807 | 16 | 19 | 0 |
| immediate | lamarckian | disordered | 30 | 20260808 | 12 | 5 | 2 |
| immediate | lamarckian | disordered | 30 | 20260809 | 8 | 14 | 2 |
| immediate | lamarckian | disordered | 30 | 20260810 | 15 | 15 | 14 |
| immediate | lamarckian | ordered | 204 | 20260803 | 5 | 1 | 4 |
| immediate | lamarckian | ordered | 204 | 20260804 | 0 | 8 | 0 |
| immediate | lamarckian | ordered | 204 | 20260805 | 6 | 7 | 6 |
| immediate | lamarckian | ordered | 204 | 20260806 | 3 | 3 | 0 |
| immediate | lamarckian | ordered | 204 | 20260807 | 0 | 7 | 0 |
| immediate | lamarckian | ordered | 204 | 20260808 | 0 | 3 | 0 |
| immediate | lamarckian | ordered | 204 | 20260809 | 0 | 0 | 0 |
| immediate | lamarckian | ordered | 204 | 20260810 | 0 | 1 | 0 |

## Found, not fixed

The calibration ladder itself is intact; the collision appears only after embedding those rules in the exotype/selection family. Choosing a different middle rule after seeing this result, or declaring Rule 110 and Rule 30 different by label despite the measured overlap, would weaken the gate and was not done.
