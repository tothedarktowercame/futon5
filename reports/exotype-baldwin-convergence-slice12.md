# Baldwin convergence experiment — Slice 12

## Design

Rule 204 (ordered) and Rule 30 (disordered/chaotic) are the two non-overlapping starts retained from the preregistered preflight. Each phenotype receives the paper's pure-ECA burn-in to t*=60 before the exotype dynamics starts. Three arms, two starts, and eight paired seeds give 48 runs at width 80 for 6000 steps on `:baseline`; lambda 0.55, mu 0.1, tau 0.3, damage horizon 59, selection window 40, and Baldwin selection strength 1.0. Damage is measured at 0, 120, 600, 1200, 3000, and 6000.

The Lamarckian arm writes the expressed rule back every step and has selection disabled. The two Baldwin arms disable write-back and differ only in `:fitness-kind`. Divergence fitness is an upper bracket because it selects on a damage proxy; it is not independent evidence for an edge-of-chaos claim.

## Fitness threading gate

Preference and divergence fitness produced different final genotypes in **0/16** paired runs, different unperturbed final states in **0/16**, and different damage trajectories in **4/16**. **32/32** Baldwin runs retained a single uniform genotype equal to their seeded rule. Gate: **VOID — the fitnesses do not separate base dynamics**.

The multimethod dispatch is exercised by the existing unit test and the four differing counterfactual trajectories. The null in the base runs is instead caused by absent heritable variation: copying a neighbour from a uniform population can only copy the same rule.

## Verdict

**No arm demonstrates regulation.** The Baldwin result is the decisive negative: ordered and disordered starts remain far apart. Preference fitness ends at P=1.250 (SD 0.707, range 0–2) from Rule 204 and P=26.000 (SD 6.392, range 18–38) from Rule 30; divergence fitness is the same on P. The temporary narrowing at step 600 reopens by 1200 and persists through 6000, so final reach tracks initial reach rather than converging.

This is structural, not a weak-selection estimate. Every Baldwin population starts with one uniform heritable rule. Neighbour-copy selection has no heritable variant to choose, and all 32 Baldwin runs finish with exactly that one starting rule. Preference and divergence dispatch do affect four counterfactual damage forks, showing that `fitness-kind` is threaded, but their unperturbed final states are identical in all 16 paired comparisons. The experiment is therefore void as a comparison of Baldwin fitnesses.

The Lamarckian endpoints overlap (P=8.625 versus 7.500, both range 0–13), but its trajectory does not settle: mean P falls near 1 by step 600, rises near 6 by 1200, falls again by 3000, and rises again by 6000. Direct every-step write-back also erases the starting genotype by construction. That is initial-condition washout, not evidence that the arm regulates to a stable reach. The divergence arm remains well below the width-80 ceiling, but because selection never had variation this is not an informative upper bracket. The two Baldwin triptychs make the failure visible as solid, unchanged genotype columns.

## Final damage

| time | arm | start | P mean (SD; range) | G mean (SD; range) | X mean (SD; range) |
|---:|---|---|---:|---:|---:|
| 6000 | lamarckian | ordered | 8.625 (4.749; 0–13) | 5.875 (6.175; 0–16) | 1.000 (2.138; 0–6) |
| 6000 | lamarckian | disordered | 7.500 (4.209; 0–13) | 3.125 (4.486; 0–11) | 6.000 (5.345; 0–14) |
| 6000 | baldwin-preferences | ordered | 1.250 (0.707; 0–2) | 0.750 (0.463; 0–1) | 0.000 (0.000; 0–0) |
| 6000 | baldwin-preferences | disordered | 26.000 (6.392; 18–38) | 1.000 (0.000; 1–1) | 0.000 (0.000; 0–0) |
| 6000 | baldwin-divergence | ordered | 1.250 (0.707; 0–2) | 0.875 (0.354; 0–1) | 0.000 (0.000; 0–0) |
| 6000 | baldwin-divergence | disordered | 26.000 (6.392; 18–38) | 1.000 (0.000; 1–1) | 0.000 (0.000; 0–0) |

## Damage trajectory

| time | arm | start | P mean (SD; range) | G mean (SD; range) | X mean (SD; range) |
|---:|---|---|---:|---:|---:|
| 0 | lamarckian | ordered | 3.875 (3.720; 0–10) | 8.875 (6.792; 1–19) | 2.000 (3.742; 0–9) |
| 0 | lamarckian | disordered | 9.500 (7.309; 0–20) | 13.500 (12.750; 0–29) | 3.250 (2.915; 0–9) |
| 0 | baldwin-preferences | ordered | 1.750 (2.188; 0–6) | 1.375 (0.744; 0–2) | 1.500 (2.976; 0–8) |
| 0 | baldwin-preferences | disordered | 29.750 (5.036; 24–38) | 0.875 (0.354; 0–1) | 1.125 (2.232; 0–6) |
| 0 | baldwin-divergence | ordered | 1.750 (2.188; 0–6) | 1.125 (0.641; 0–2) | 1.500 (2.976; 0–8) |
| 0 | baldwin-divergence | disordered | 29.750 (5.036; 24–38) | 0.875 (0.354; 0–1) | 1.125 (2.232; 0–6) |
| 120 | lamarckian | ordered | 2.375 (2.615; 0–6) | 6.625 (4.138; 1–12) | 1.625 (3.114; 0–8) |
| 120 | lamarckian | disordered | 5.375 (3.068; 0–9) | 4.375 (1.923; 1–6) | 2.750 (3.059; 0–8) |
| 120 | baldwin-preferences | ordered | 1.500 (1.604; 0–5) | 1.000 (0.000; 1–1) | 0.750 (1.389; 0–3) |
| 120 | baldwin-preferences | disordered | 22.125 (9.109; 11–37) | 1.000 (0.000; 1–1) | 0.000 (0.000; 0–0) |
| 120 | baldwin-divergence | ordered | 1.500 (1.604; 0–5) | 1.250 (0.463; 1–2) | 0.750 (1.389; 0–3) |
| 120 | baldwin-divergence | disordered | 22.125 (9.109; 11–37) | 1.000 (0.000; 1–1) | 0.000 (0.000; 0–0) |
| 600 | lamarckian | ordered | 1.500 (2.268; 0–5) | 2.125 (1.458; 0–4) | 0.750 (1.165; 0–3) |
| 600 | lamarckian | disordered | 0.500 (0.926; 0–2) | 2.750 (2.053; 1–6) | 1.125 (2.232; 0–6) |
| 600 | baldwin-preferences | ordered | 1.500 (1.414; 0–4) | 0.875 (0.354; 0–1) | 2.750 (3.882; 0–9) |
| 600 | baldwin-preferences | disordered | 10.125 (11.103; 0–24) | 1.000 (0.000; 1–1) | 1.500 (4.243; 0–12) |
| 600 | baldwin-divergence | ordered | 1.500 (1.414; 0–4) | 0.875 (0.354; 0–1) | 2.750 (3.882; 0–9) |
| 600 | baldwin-divergence | disordered | 10.125 (11.103; 0–24) | 1.000 (0.000; 1–1) | 1.500 (4.243; 0–12) |
| 1200 | lamarckian | ordered | 5.375 (5.236; 0–12) | 11.125 (6.289; 1–21) | 11.000 (8.071; 0–21) |
| 1200 | lamarckian | disordered | 6.750 (6.205; 0–16) | 9.250 (5.922; 1–16) | 3.875 (4.853; 0–12) |
| 1200 | baldwin-preferences | ordered | 5.125 (3.871; 0–12) | 1.375 (0.518; 1–2) | 1.750 (3.240; 0–7) |
| 1200 | baldwin-preferences | disordered | 27.125 (8.887; 14–38) | 1.000 (0.000; 1–1) | 1.750 (3.615; 0–10) |
| 1200 | baldwin-divergence | ordered | 5.125 (3.871; 0–12) | 1.375 (0.518; 1–2) | 1.750 (3.240; 0–7) |
| 1200 | baldwin-divergence | disordered | 27.125 (8.887; 14–38) | 1.000 (0.000; 1–1) | 1.750 (3.615; 0–10) |
| 3000 | lamarckian | ordered | 2.750 (4.803; 0–11) | 0.375 (1.061; 0–3) | 0.000 (0.000; 0–0) |
| 3000 | lamarckian | disordered | 1.125 (2.800; 0–8) | 0.000 (0.000; 0–0) | 0.000 (0.000; 0–0) |
| 3000 | baldwin-preferences | ordered | 3.750 (1.389; 2–6) | 1.125 (0.354; 1–2) | 0.000 (0.000; 0–0) |
| 3000 | baldwin-preferences | disordered | 26.500 (6.803; 15–35) | 1.000 (0.000; 1–1) | 0.000 (0.000; 0–0) |
| 3000 | baldwin-divergence | ordered | 3.750 (1.389; 2–6) | 1.125 (0.354; 1–2) | 0.000 (0.000; 0–0) |
| 3000 | baldwin-divergence | disordered | 26.500 (6.803; 15–35) | 1.000 (0.000; 1–1) | 0.000 (0.000; 0–0) |
| 6000 | lamarckian | ordered | 8.625 (4.749; 0–13) | 5.875 (6.175; 0–16) | 1.000 (2.138; 0–6) |
| 6000 | lamarckian | disordered | 7.500 (4.209; 0–13) | 3.125 (4.486; 0–11) | 6.000 (5.345; 0–14) |
| 6000 | baldwin-preferences | ordered | 1.250 (0.707; 0–2) | 0.750 (0.463; 0–1) | 0.000 (0.000; 0–0) |
| 6000 | baldwin-preferences | disordered | 26.000 (6.392; 18–38) | 1.000 (0.000; 1–1) | 0.000 (0.000; 0–0) |
| 6000 | baldwin-divergence | ordered | 1.250 (0.707; 0–2) | 0.875 (0.354; 0–1) | 0.000 (0.000; 0–0) |
| 6000 | baldwin-divergence | disordered | 26.000 (6.392; 18–38) | 1.000 (0.000; 1–1) | 0.000 (0.000; 0–0) |

## Full per-seed trajectory

| time | arm | start | seed | P | G | X |
|---:|---|---|---:|---:|---:|---:|
| 0 | lamarckian | ordered | 20260803 | 4 | 10 | 0 |
| 0 | lamarckian | ordered | 20260804 | 0 | 19 | 0 |
| 0 | lamarckian | ordered | 20260805 | 7 | 1 | 7 |
| 0 | lamarckian | ordered | 20260806 | 10 | 9 | 9 |
| 0 | lamarckian | ordered | 20260807 | 4 | 12 | 0 |
| 0 | lamarckian | ordered | 20260808 | 0 | 1 | 0 |
| 0 | lamarckian | ordered | 20260809 | 0 | 16 | 0 |
| 0 | lamarckian | ordered | 20260810 | 6 | 3 | 0 |
| 0 | lamarckian | disordered | 20260803 | 0 | 22 | 0 |
| 0 | lamarckian | disordered | 20260804 | 16 | 2 | 0 |
| 0 | lamarckian | disordered | 20260805 | 12 | 29 | 4 |
| 0 | lamarckian | disordered | 20260806 | 6 | 20 | 3 |
| 0 | lamarckian | disordered | 20260807 | 20 | 1 | 9 |
| 0 | lamarckian | disordered | 20260808 | 0 | 29 | 5 |
| 0 | lamarckian | disordered | 20260809 | 8 | 0 | 2 |
| 0 | lamarckian | disordered | 20260810 | 14 | 5 | 3 |
| 0 | baldwin-preferences | ordered | 20260803 | 0 | 0 | 0 |
| 0 | baldwin-preferences | ordered | 20260804 | 0 | 2 | 0 |
| 0 | baldwin-preferences | ordered | 20260805 | 6 | 1 | 4 |
| 0 | baldwin-preferences | ordered | 20260806 | 4 | 1 | 8 |
| 0 | baldwin-preferences | ordered | 20260807 | 1 | 2 | 0 |
| 0 | baldwin-preferences | ordered | 20260808 | 1 | 2 | 0 |
| 0 | baldwin-preferences | ordered | 20260809 | 0 | 1 | 0 |
| 0 | baldwin-preferences | ordered | 20260810 | 2 | 2 | 0 |
| 0 | baldwin-preferences | disordered | 20260803 | 24 | 0 | 6 |
| 0 | baldwin-preferences | disordered | 20260804 | 30 | 1 | 0 |
| 0 | baldwin-preferences | disordered | 20260805 | 35 | 1 | 0 |
| 0 | baldwin-preferences | disordered | 20260806 | 31 | 1 | 3 |
| 0 | baldwin-preferences | disordered | 20260807 | 38 | 1 | 0 |
| 0 | baldwin-preferences | disordered | 20260808 | 26 | 1 | 0 |
| 0 | baldwin-preferences | disordered | 20260809 | 24 | 1 | 0 |
| 0 | baldwin-preferences | disordered | 20260810 | 30 | 1 | 0 |
| 0 | baldwin-divergence | ordered | 20260803 | 0 | 0 | 0 |
| 0 | baldwin-divergence | ordered | 20260804 | 0 | 1 | 0 |
| 0 | baldwin-divergence | ordered | 20260805 | 6 | 1 | 4 |
| 0 | baldwin-divergence | ordered | 20260806 | 4 | 1 | 8 |
| 0 | baldwin-divergence | ordered | 20260807 | 1 | 2 | 0 |
| 0 | baldwin-divergence | ordered | 20260808 | 1 | 2 | 0 |
| 0 | baldwin-divergence | ordered | 20260809 | 0 | 1 | 0 |
| 0 | baldwin-divergence | ordered | 20260810 | 2 | 1 | 0 |
| 0 | baldwin-divergence | disordered | 20260803 | 24 | 0 | 6 |
| 0 | baldwin-divergence | disordered | 20260804 | 30 | 1 | 0 |
| 0 | baldwin-divergence | disordered | 20260805 | 35 | 1 | 0 |
| 0 | baldwin-divergence | disordered | 20260806 | 31 | 1 | 3 |
| 0 | baldwin-divergence | disordered | 20260807 | 38 | 1 | 0 |
| 0 | baldwin-divergence | disordered | 20260808 | 26 | 1 | 0 |
| 0 | baldwin-divergence | disordered | 20260809 | 24 | 1 | 0 |
| 0 | baldwin-divergence | disordered | 20260810 | 30 | 1 | 0 |
| 120 | lamarckian | ordered | 20260803 | 0 | 11 | 5 |
| 120 | lamarckian | ordered | 20260804 | 5 | 12 | 0 |
| 120 | lamarckian | ordered | 20260805 | 4 | 1 | 0 |
| 120 | lamarckian | ordered | 20260806 | 0 | 7 | 0 |
| 120 | lamarckian | ordered | 20260807 | 4 | 7 | 0 |
| 120 | lamarckian | ordered | 20260808 | 0 | 9 | 8 |
| 120 | lamarckian | ordered | 20260809 | 6 | 5 | 0 |
| 120 | lamarckian | ordered | 20260810 | 0 | 1 | 0 |
| 120 | lamarckian | disordered | 20260803 | 0 | 6 | 0 |
| 120 | lamarckian | disordered | 20260804 | 3 | 6 | 3 |
| 120 | lamarckian | disordered | 20260805 | 7 | 6 | 1 |
| 120 | lamarckian | disordered | 20260806 | 9 | 4 | 4 |
| 120 | lamarckian | disordered | 20260807 | 9 | 2 | 6 |
| 120 | lamarckian | disordered | 20260808 | 5 | 5 | 0 |
| 120 | lamarckian | disordered | 20260809 | 4 | 5 | 8 |
| 120 | lamarckian | disordered | 20260810 | 6 | 1 | 0 |
| 120 | baldwin-preferences | ordered | 20260803 | 0 | 1 | 0 |
| 120 | baldwin-preferences | ordered | 20260804 | 2 | 1 | 0 |
| 120 | baldwin-preferences | ordered | 20260805 | 0 | 1 | 0 |
| 120 | baldwin-preferences | ordered | 20260806 | 2 | 1 | 0 |
| 120 | baldwin-preferences | ordered | 20260807 | 1 | 1 | 0 |
| 120 | baldwin-preferences | ordered | 20260808 | 1 | 1 | 0 |
| 120 | baldwin-preferences | ordered | 20260809 | 5 | 1 | 3 |
| 120 | baldwin-preferences | ordered | 20260810 | 1 | 1 | 3 |
| 120 | baldwin-preferences | disordered | 20260803 | 17 | 1 | 0 |
| 120 | baldwin-preferences | disordered | 20260804 | 18 | 1 | 0 |
| 120 | baldwin-preferences | disordered | 20260805 | 19 | 1 | 0 |
| 120 | baldwin-preferences | disordered | 20260806 | 18 | 1 | 0 |
| 120 | baldwin-preferences | disordered | 20260807 | 35 | 1 | 0 |
| 120 | baldwin-preferences | disordered | 20260808 | 37 | 1 | 0 |
| 120 | baldwin-preferences | disordered | 20260809 | 22 | 1 | 0 |
| 120 | baldwin-preferences | disordered | 20260810 | 11 | 1 | 0 |
| 120 | baldwin-divergence | ordered | 20260803 | 0 | 1 | 0 |
| 120 | baldwin-divergence | ordered | 20260804 | 2 | 2 | 0 |
| 120 | baldwin-divergence | ordered | 20260805 | 0 | 1 | 0 |
| 120 | baldwin-divergence | ordered | 20260806 | 2 | 1 | 0 |
| 120 | baldwin-divergence | ordered | 20260807 | 1 | 2 | 0 |
| 120 | baldwin-divergence | ordered | 20260808 | 1 | 1 | 0 |
| 120 | baldwin-divergence | ordered | 20260809 | 5 | 1 | 3 |
| 120 | baldwin-divergence | ordered | 20260810 | 1 | 1 | 3 |
| 120 | baldwin-divergence | disordered | 20260803 | 17 | 1 | 0 |
| 120 | baldwin-divergence | disordered | 20260804 | 18 | 1 | 0 |
| 120 | baldwin-divergence | disordered | 20260805 | 19 | 1 | 0 |
| 120 | baldwin-divergence | disordered | 20260806 | 18 | 1 | 0 |
| 120 | baldwin-divergence | disordered | 20260807 | 35 | 1 | 0 |
| 120 | baldwin-divergence | disordered | 20260808 | 37 | 1 | 0 |
| 120 | baldwin-divergence | disordered | 20260809 | 22 | 1 | 0 |
| 120 | baldwin-divergence | disordered | 20260810 | 11 | 1 | 0 |
| 600 | lamarckian | ordered | 20260803 | 0 | 3 | 3 |
| 600 | lamarckian | ordered | 20260804 | 0 | 2 | 0 |
| 600 | lamarckian | ordered | 20260805 | 0 | 1 | 1 |
| 600 | lamarckian | ordered | 20260806 | 0 | 1 | 0 |
| 600 | lamarckian | ordered | 20260807 | 0 | 4 | 0 |
| 600 | lamarckian | ordered | 20260808 | 5 | 2 | 0 |
| 600 | lamarckian | ordered | 20260809 | 5 | 0 | 0 |
| 600 | lamarckian | ordered | 20260810 | 2 | 4 | 2 |
| 600 | lamarckian | disordered | 20260803 | 0 | 3 | 0 |
| 600 | lamarckian | disordered | 20260804 | 0 | 6 | 6 |
| 600 | lamarckian | disordered | 20260805 | 2 | 1 | 0 |
| 600 | lamarckian | disordered | 20260806 | 0 | 4 | 0 |
| 600 | lamarckian | disordered | 20260807 | 0 | 5 | 0 |
| 600 | lamarckian | disordered | 20260808 | 0 | 1 | 3 |
| 600 | lamarckian | disordered | 20260809 | 0 | 1 | 0 |
| 600 | lamarckian | disordered | 20260810 | 2 | 1 | 0 |
| 600 | baldwin-preferences | ordered | 20260803 | 2 | 0 | 0 |
| 600 | baldwin-preferences | ordered | 20260804 | 4 | 1 | 9 |
| 600 | baldwin-preferences | ordered | 20260805 | 0 | 1 | 0 |
| 600 | baldwin-preferences | ordered | 20260806 | 2 | 1 | 0 |
| 600 | baldwin-preferences | ordered | 20260807 | 0 | 1 | 0 |
| 600 | baldwin-preferences | ordered | 20260808 | 2 | 1 | 7 |
| 600 | baldwin-preferences | ordered | 20260809 | 0 | 1 | 0 |
| 600 | baldwin-preferences | ordered | 20260810 | 2 | 1 | 6 |
| 600 | baldwin-preferences | disordered | 20260803 | 0 | 1 | 0 |
| 600 | baldwin-preferences | disordered | 20260804 | 23 | 1 | 0 |
| 600 | baldwin-preferences | disordered | 20260805 | 0 | 1 | 0 |
| 600 | baldwin-preferences | disordered | 20260806 | 0 | 1 | 0 |
| 600 | baldwin-preferences | disordered | 20260807 | 17 | 1 | 0 |
| 600 | baldwin-preferences | disordered | 20260808 | 17 | 1 | 12 |
| 600 | baldwin-preferences | disordered | 20260809 | 0 | 1 | 0 |
| 600 | baldwin-preferences | disordered | 20260810 | 24 | 1 | 0 |
| 600 | baldwin-divergence | ordered | 20260803 | 2 | 0 | 0 |
| 600 | baldwin-divergence | ordered | 20260804 | 4 | 1 | 9 |
| 600 | baldwin-divergence | ordered | 20260805 | 0 | 1 | 0 |
| 600 | baldwin-divergence | ordered | 20260806 | 2 | 1 | 0 |
| 600 | baldwin-divergence | ordered | 20260807 | 0 | 1 | 0 |
| 600 | baldwin-divergence | ordered | 20260808 | 2 | 1 | 7 |
| 600 | baldwin-divergence | ordered | 20260809 | 0 | 1 | 0 |
| 600 | baldwin-divergence | ordered | 20260810 | 2 | 1 | 6 |
| 600 | baldwin-divergence | disordered | 20260803 | 0 | 1 | 0 |
| 600 | baldwin-divergence | disordered | 20260804 | 23 | 1 | 0 |
| 600 | baldwin-divergence | disordered | 20260805 | 0 | 1 | 0 |
| 600 | baldwin-divergence | disordered | 20260806 | 0 | 1 | 0 |
| 600 | baldwin-divergence | disordered | 20260807 | 17 | 1 | 0 |
| 600 | baldwin-divergence | disordered | 20260808 | 17 | 1 | 12 |
| 600 | baldwin-divergence | disordered | 20260809 | 0 | 1 | 0 |
| 600 | baldwin-divergence | disordered | 20260810 | 24 | 1 | 0 |
| 1200 | lamarckian | ordered | 20260803 | 0 | 15 | 16 |
| 1200 | lamarckian | ordered | 20260804 | 12 | 11 | 14 |
| 1200 | lamarckian | ordered | 20260805 | 11 | 15 | 0 |
| 1200 | lamarckian | ordered | 20260806 | 0 | 13 | 5 |
| 1200 | lamarckian | ordered | 20260807 | 0 | 1 | 19 |
| 1200 | lamarckian | ordered | 20260808 | 3 | 6 | 1 |
| 1200 | lamarckian | ordered | 20260809 | 10 | 7 | 12 |
| 1200 | lamarckian | ordered | 20260810 | 7 | 21 | 21 |
| 1200 | lamarckian | disordered | 20260803 | 0 | 1 | 0 |
| 1200 | lamarckian | disordered | 20260804 | 8 | 15 | 0 |
| 1200 | lamarckian | disordered | 20260805 | 8 | 7 | 12 |
| 1200 | lamarckian | disordered | 20260806 | 3 | 5 | 2 |
| 1200 | lamarckian | disordered | 20260807 | 4 | 13 | 0 |
| 1200 | lamarckian | disordered | 20260808 | 15 | 14 | 1 |
| 1200 | lamarckian | disordered | 20260809 | 16 | 16 | 6 |
| 1200 | lamarckian | disordered | 20260810 | 0 | 3 | 10 |
| 1200 | baldwin-preferences | ordered | 20260803 | 12 | 2 | 0 |
| 1200 | baldwin-preferences | ordered | 20260804 | 0 | 1 | 0 |
| 1200 | baldwin-preferences | ordered | 20260805 | 3 | 2 | 0 |
| 1200 | baldwin-preferences | ordered | 20260806 | 4 | 2 | 0 |
| 1200 | baldwin-preferences | ordered | 20260807 | 5 | 1 | 0 |
| 1200 | baldwin-preferences | ordered | 20260808 | 2 | 1 | 0 |
| 1200 | baldwin-preferences | ordered | 20260809 | 9 | 1 | 7 |
| 1200 | baldwin-preferences | ordered | 20260810 | 6 | 1 | 7 |
| 1200 | baldwin-preferences | disordered | 20260803 | 28 | 1 | 0 |
| 1200 | baldwin-preferences | disordered | 20260804 | 14 | 1 | 10 |
| 1200 | baldwin-preferences | disordered | 20260805 | 27 | 1 | 0 |
| 1200 | baldwin-preferences | disordered | 20260806 | 34 | 1 | 0 |
| 1200 | baldwin-preferences | disordered | 20260807 | 20 | 1 | 4 |
| 1200 | baldwin-preferences | disordered | 20260808 | 37 | 1 | 0 |
| 1200 | baldwin-preferences | disordered | 20260809 | 19 | 1 | 0 |
| 1200 | baldwin-preferences | disordered | 20260810 | 38 | 1 | 0 |
| 1200 | baldwin-divergence | ordered | 20260803 | 12 | 2 | 0 |
| 1200 | baldwin-divergence | ordered | 20260804 | 0 | 1 | 0 |
| 1200 | baldwin-divergence | ordered | 20260805 | 3 | 2 | 0 |
| 1200 | baldwin-divergence | ordered | 20260806 | 4 | 2 | 0 |
| 1200 | baldwin-divergence | ordered | 20260807 | 5 | 1 | 0 |
| 1200 | baldwin-divergence | ordered | 20260808 | 2 | 1 | 0 |
| 1200 | baldwin-divergence | ordered | 20260809 | 9 | 1 | 7 |
| 1200 | baldwin-divergence | ordered | 20260810 | 6 | 1 | 7 |
| 1200 | baldwin-divergence | disordered | 20260803 | 28 | 1 | 0 |
| 1200 | baldwin-divergence | disordered | 20260804 | 14 | 1 | 10 |
| 1200 | baldwin-divergence | disordered | 20260805 | 27 | 1 | 0 |
| 1200 | baldwin-divergence | disordered | 20260806 | 34 | 1 | 0 |
| 1200 | baldwin-divergence | disordered | 20260807 | 20 | 1 | 4 |
| 1200 | baldwin-divergence | disordered | 20260808 | 37 | 1 | 0 |
| 1200 | baldwin-divergence | disordered | 20260809 | 19 | 1 | 0 |
| 1200 | baldwin-divergence | disordered | 20260810 | 38 | 1 | 0 |
| 3000 | lamarckian | ordered | 20260803 | 0 | 0 | 0 |
| 3000 | lamarckian | ordered | 20260804 | 10 | 0 | 0 |
| 3000 | lamarckian | ordered | 20260805 | 1 | 3 | 0 |
| 3000 | lamarckian | ordered | 20260806 | 0 | 0 | 0 |
| 3000 | lamarckian | ordered | 20260807 | 0 | 0 | 0 |
| 3000 | lamarckian | ordered | 20260808 | 0 | 0 | 0 |
| 3000 | lamarckian | ordered | 20260809 | 11 | 0 | 0 |
| 3000 | lamarckian | ordered | 20260810 | 0 | 0 | 0 |
| 3000 | lamarckian | disordered | 20260803 | 0 | 0 | 0 |
| 3000 | lamarckian | disordered | 20260804 | 0 | 0 | 0 |
| 3000 | lamarckian | disordered | 20260805 | 8 | 0 | 0 |
| 3000 | lamarckian | disordered | 20260806 | 0 | 0 | 0 |
| 3000 | lamarckian | disordered | 20260807 | 0 | 0 | 0 |
| 3000 | lamarckian | disordered | 20260808 | 1 | 0 | 0 |
| 3000 | lamarckian | disordered | 20260809 | 0 | 0 | 0 |
| 3000 | lamarckian | disordered | 20260810 | 0 | 0 | 0 |
| 3000 | baldwin-preferences | ordered | 20260803 | 6 | 1 | 0 |
| 3000 | baldwin-preferences | ordered | 20260804 | 5 | 1 | 0 |
| 3000 | baldwin-preferences | ordered | 20260805 | 4 | 1 | 0 |
| 3000 | baldwin-preferences | ordered | 20260806 | 4 | 1 | 0 |
| 3000 | baldwin-preferences | ordered | 20260807 | 3 | 1 | 0 |
| 3000 | baldwin-preferences | ordered | 20260808 | 4 | 1 | 0 |
| 3000 | baldwin-preferences | ordered | 20260809 | 2 | 2 | 0 |
| 3000 | baldwin-preferences | ordered | 20260810 | 2 | 1 | 0 |
| 3000 | baldwin-preferences | disordered | 20260803 | 24 | 1 | 0 |
| 3000 | baldwin-preferences | disordered | 20260804 | 35 | 1 | 0 |
| 3000 | baldwin-preferences | disordered | 20260805 | 15 | 1 | 0 |
| 3000 | baldwin-preferences | disordered | 20260806 | 28 | 1 | 0 |
| 3000 | baldwin-preferences | disordered | 20260807 | 33 | 1 | 0 |
| 3000 | baldwin-preferences | disordered | 20260808 | 29 | 1 | 0 |
| 3000 | baldwin-preferences | disordered | 20260809 | 19 | 1 | 0 |
| 3000 | baldwin-preferences | disordered | 20260810 | 29 | 1 | 0 |
| 3000 | baldwin-divergence | ordered | 20260803 | 6 | 1 | 0 |
| 3000 | baldwin-divergence | ordered | 20260804 | 5 | 1 | 0 |
| 3000 | baldwin-divergence | ordered | 20260805 | 4 | 1 | 0 |
| 3000 | baldwin-divergence | ordered | 20260806 | 4 | 1 | 0 |
| 3000 | baldwin-divergence | ordered | 20260807 | 3 | 1 | 0 |
| 3000 | baldwin-divergence | ordered | 20260808 | 4 | 1 | 0 |
| 3000 | baldwin-divergence | ordered | 20260809 | 2 | 2 | 0 |
| 3000 | baldwin-divergence | ordered | 20260810 | 2 | 1 | 0 |
| 3000 | baldwin-divergence | disordered | 20260803 | 24 | 1 | 0 |
| 3000 | baldwin-divergence | disordered | 20260804 | 35 | 1 | 0 |
| 3000 | baldwin-divergence | disordered | 20260805 | 15 | 1 | 0 |
| 3000 | baldwin-divergence | disordered | 20260806 | 28 | 1 | 0 |
| 3000 | baldwin-divergence | disordered | 20260807 | 33 | 1 | 0 |
| 3000 | baldwin-divergence | disordered | 20260808 | 29 | 1 | 0 |
| 3000 | baldwin-divergence | disordered | 20260809 | 19 | 1 | 0 |
| 3000 | baldwin-divergence | disordered | 20260810 | 29 | 1 | 0 |
| 6000 | lamarckian | ordered | 20260803 | 12 | 0 | 0 |
| 6000 | lamarckian | ordered | 20260804 | 12 | 0 | 0 |
| 6000 | lamarckian | ordered | 20260805 | 6 | 0 | 0 |
| 6000 | lamarckian | ordered | 20260806 | 12 | 16 | 0 |
| 6000 | lamarckian | ordered | 20260807 | 10 | 2 | 6 |
| 6000 | lamarckian | ordered | 20260808 | 0 | 9 | 0 |
| 6000 | lamarckian | ordered | 20260809 | 13 | 9 | 2 |
| 6000 | lamarckian | ordered | 20260810 | 4 | 11 | 0 |
| 6000 | lamarckian | disordered | 20260803 | 9 | 0 | 0 |
| 6000 | lamarckian | disordered | 20260804 | 5 | 1 | 7 |
| 6000 | lamarckian | disordered | 20260805 | 13 | 4 | 3 |
| 6000 | lamarckian | disordered | 20260806 | 9 | 0 | 2 |
| 6000 | lamarckian | disordered | 20260807 | 0 | 0 | 14 |
| 6000 | lamarckian | disordered | 20260808 | 4 | 0 | 14 |
| 6000 | lamarckian | disordered | 20260809 | 11 | 11 | 5 |
| 6000 | lamarckian | disordered | 20260810 | 9 | 9 | 3 |
| 6000 | baldwin-preferences | ordered | 20260803 | 2 | 1 | 0 |
| 6000 | baldwin-preferences | ordered | 20260804 | 2 | 1 | 0 |
| 6000 | baldwin-preferences | ordered | 20260805 | 2 | 1 | 0 |
| 6000 | baldwin-preferences | ordered | 20260806 | 0 | 0 | 0 |
| 6000 | baldwin-preferences | ordered | 20260807 | 1 | 1 | 0 |
| 6000 | baldwin-preferences | ordered | 20260808 | 1 | 1 | 0 |
| 6000 | baldwin-preferences | ordered | 20260809 | 1 | 0 | 0 |
| 6000 | baldwin-preferences | ordered | 20260810 | 1 | 1 | 0 |
| 6000 | baldwin-preferences | disordered | 20260803 | 28 | 1 | 0 |
| 6000 | baldwin-preferences | disordered | 20260804 | 25 | 1 | 0 |
| 6000 | baldwin-preferences | disordered | 20260805 | 28 | 1 | 0 |
| 6000 | baldwin-preferences | disordered | 20260806 | 18 | 1 | 0 |
| 6000 | baldwin-preferences | disordered | 20260807 | 28 | 1 | 0 |
| 6000 | baldwin-preferences | disordered | 20260808 | 38 | 1 | 0 |
| 6000 | baldwin-preferences | disordered | 20260809 | 18 | 1 | 0 |
| 6000 | baldwin-preferences | disordered | 20260810 | 25 | 1 | 0 |
| 6000 | baldwin-divergence | ordered | 20260803 | 2 | 1 | 0 |
| 6000 | baldwin-divergence | ordered | 20260804 | 2 | 1 | 0 |
| 6000 | baldwin-divergence | ordered | 20260805 | 2 | 1 | 0 |
| 6000 | baldwin-divergence | ordered | 20260806 | 0 | 0 | 0 |
| 6000 | baldwin-divergence | ordered | 20260807 | 1 | 1 | 0 |
| 6000 | baldwin-divergence | ordered | 20260808 | 1 | 1 | 0 |
| 6000 | baldwin-divergence | ordered | 20260809 | 1 | 1 | 0 |
| 6000 | baldwin-divergence | ordered | 20260810 | 1 | 1 | 0 |
| 6000 | baldwin-divergence | disordered | 20260803 | 28 | 1 | 0 |
| 6000 | baldwin-divergence | disordered | 20260804 | 25 | 1 | 0 |
| 6000 | baldwin-divergence | disordered | 20260805 | 28 | 1 | 0 |
| 6000 | baldwin-divergence | disordered | 20260806 | 18 | 1 | 0 |
| 6000 | baldwin-divergence | disordered | 20260807 | 28 | 1 | 0 |
| 6000 | baldwin-divergence | disordered | 20260808 | 38 | 1 | 0 |
| 6000 | baldwin-divergence | disordered | 20260809 | 18 | 1 | 0 |
| 6000 | baldwin-divergence | disordered | 20260810 | 25 | 1 | 0 |

## Representative triptychs

- `baldwin-preferences` / `ordered`: `reports/figures/slice12-baldwin-preferences-ordered-triptych.png`
- `baldwin-preferences` / `disordered`: `reports/figures/slice12-baldwin-preferences-disordered-triptych.png`

The previous failed three-start preflight remains in `reports/exotype-baldwin-convergence-slice12.preflight.edn`; Rule 110 was dropped rather than relabelled as an undisputed critical point.
