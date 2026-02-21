# Tensor Tokamak Session

- Seed: `1695261645`
- Length: `50`
- Generations requested: `40`
- Phenotype enabled: `true`
- Explore rate: `0.350`
- Schedule: geno/phen/exo = `1/2/2`
- Mutation size: geno/phen = `2/4`
- Exo trigger: stagnation `3`, novelty-floor `0.030`, total-floor `0.200`
- Exo credit: window `4`, blend `0.250`
- Exo assimilation: threshold `0.020`, streak `2`, rate `0.120`

## Outcome

- Best total score: `0.439`
- Best generation: `1`
- Final total score: `0.010`
- Final regime: `magma`
- Rewinds used: `0`
- Definition versions: `41`
- Exo-genotype versions: `41`
- Exo attempts/selected: `7/6`
- Exo changed cells total: `11`
- Layer credit cumulative: `{:genotype 0.07327373688370552, :xenotype 0.07327373688370552, :phenotype -0.12745612994321578, :exotype -0.01587602238301236}`

## Final Physics

- Rule sigil: `手`
- Diagram: `sigil-step-gated`
- Step opts: `{:backend :clj, :wrap? false, :boundary-bit 0}`
- Exo-gene top pattern weights: `([:anti-freeze 0.24000000000000002] [:anti-magma 0.22000000000000003] [:edge-preserve 0.20000000000000004] [:symmetry-break 0.18000000000000002] [:boundary-soften 0.16000000000000003])`
- Exo-gene global-strength: `0.350`
- Exo-gene max-cells-ratio: `0.200`
- Exo-gene reason biases: `{"stagnation" 0.34, "regime" 0.3, "novelty+total-floor" 0.2, "rewind" 0.1, "none" 0.06}`

## Ledger

| g | rule | diagram | total | quality | novelty | surprise | regime | geno | pheno | exo-plastic | exo-assim | xeno | delta | exo-delta | rewind | interest-v | exo-gene-v |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 手 | sigil-step-gated | 0.439 | 0.190 | 1.000 | 0.500 | magma | yes | no | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | 0.439 | 0.439 | no | 1 | 1 |
| 2 | 手 | sigil-step-gated | 0.124 | 0.180 | 0.044 | 0.029 | magma | yes | yes | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.315 | -0.315 | no | 2 | 1 |
| 3 | 手 | sigil-step-gated | 0.129 | 0.190 | 0.042 | 0.028 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.005 | -0.152 | no | 3 | 1 |
| 4 | 手 | sigil-step-gated | 0.119 | 0.189 | 0.016 | 0.011 | magma | yes | yes | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.010 | -0.111 | no | 4 | 1 |
| 5 | 手 | sigil-step-gated | 0.106 | 0.184 | 0.018 | 0.012 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.013 | -0.097 | no | 5 | 1 |
| 6 | 手 | sigil-step-gated | 0.100 | 0.179 | 0.010 | 0.007 | magma | yes | yes | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.006 | -0.019 | no | 6 | 1 |
| 7 | 手 | sigil-step-gated | 0.107 | 0.185 | 0.019 | 0.013 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.007 | -0.006 | no | 7 | 1 |
| 8 | 手 | sigil-step-gated | 0.104 | 0.208 | 0.014 | 0.009 | magma | yes | yes | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.003 | -0.004 | no | 8 | 1 |
| 9 | 手 | sigil-step-gated | 0.124 | 0.239 | 0.023 | 0.016 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.019 | 0.019 | no | 9 | 1 |
| 10 | 手 | sigil-step-gated | 0.133 | 0.267 | 0.016 | 0.010 | magma | yes | yes | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.009 | 0.024 | no | 10 | 1 |
| 11 | 手 | sigil-step-gated | 0.125 | 0.290 | 0.013 | 0.009 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.008 | 0.008 | no | 11 | 1 |
| 12 | 手 | sigil-step-gated | 0.134 | 0.316 | 0.010 | 0.007 | magma | yes | yes | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.009 | 0.013 | no | 12 | 1 |
| 13 | 手 | sigil-step-gated | 0.142 | 0.338 | 0.009 | 0.006 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.008 | 0.013 | no | 13 | 1 |
| 14 | 手 | sigil-step-gated | 0.127 | 0.354 | 0.007 | 0.005 | magma | yes | yes | tilt/regime [medium/regime] a=y s=y m=strict-improve c=2 | none | yes | -0.015 | -0.006 | no | 14 | 1 |
| 15 | 手 | sigil-step-gated | 0.127 | 0.360 | 0.004 | 0.003 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.000 | -0.005 | no | 15 | 1 |
| 16 | 手 | sigil-step-gated | 0.124 | 0.341 | 0.009 | 0.006 | magma | yes | yes | tilt/regime [medium/regime] a=y s=y m=strict-improve c=1 | none | yes | -0.004 | -0.009 | no | 16 | 1 |
| 17 | 手 | sigil-step-gated | 0.102 | 0.343 | 0.004 | 0.003 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.022 | -0.029 | no | 17 | 1 |
| 18 | 手 | sigil-step-gated | 0.103 | 0.344 | 0.006 | 0.004 | magma | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=1 | none | yes | 0.002 | -0.017 | no | 18 | 1 |
| 19 | 手 | sigil-step-gated | 0.108 | 0.352 | 0.010 | 0.006 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.005 | -0.006 | no | 19 | 1 |
| 20 | 手 | sigil-step-gated | 0.083 | 0.351 | 0.003 | 0.002 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.025 | -0.026 | no | 20 | 1 |
| 21 | 手 | sigil-step-gated | 0.085 | 0.354 | 0.004 | 0.003 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.002 | -0.014 | no | 21 | 1 |
| 22 | 手 | sigil-step-gated | 0.086 | 0.356 | 0.005 | 0.003 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | -0.009 | no | 22 | 1 |
| 23 | 手 | sigil-step-gated | 0.066 | 0.359 | 0.003 | 0.002 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.020 | -0.025 | no | 23 | 1 |
| 24 | 手 | sigil-step-gated | 0.066 | 0.360 | 0.003 | 0.002 | magma | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=4 | none | yes | 0.000 | -0.014 | no | 24 | 1 |
| 25 | 手 | sigil-step-gated | 0.065 | 0.361 | 0.002 | 0.001 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.001 | -0.011 | no | 25 | 1 |
| 26 | 手 | sigil-step-gated | 0.046 | 0.361 | 0.002 | 0.002 | magma | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=1 | none | yes | -0.019 | -0.025 | no | 26 | 1 |
| 27 | 手 | sigil-step-gated | 0.046 | 0.362 | 0.002 | 0.001 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | -0.014 | no | 27 | 1 |
| 28 | 手 | sigil-step-gated | 0.047 | 0.363 | 0.003 | 0.002 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | -0.009 | no | 28 | 1 |
| 29 | 手 | sigil-step-gated | 0.030 | 0.366 | 0.005 | 0.003 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.017 | -0.021 | no | 29 | 1 |
| 30 | 手 | sigil-step-gated | 0.030 | 0.367 | 0.004 | 0.003 | magma | yes | yes | tilt/stagnation [high/stagnation] a=y s=n m=no-change c=0 | none | yes | -0.000 | -0.013 | no | 30 | 1 |
| 31 | 手 | sigil-step-gated | 0.030 | 0.370 | 0.005 | 0.003 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | -0.008 | no | 31 | 1 |
| 32 | 手 | sigil-step-gated | 0.014 | 0.372 | 0.007 | 0.005 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.016 | -0.020 | no | 32 | 1 |
| 33 | 手 | sigil-step-gated | 0.013 | 0.376 | 0.006 | 0.004 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.001 | -0.013 | no | 33 | 1 |
| 34 | 手 | sigil-step-gated | 0.014 | 0.379 | 0.007 | 0.004 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | -0.008 | no | 34 | 1 |
| 35 | 手 | sigil-step-gated | 0.011 | 0.384 | 0.007 | 0.005 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.003 | -0.007 | no | 35 | 1 |
| 36 | 手 | sigil-step-gated | 0.010 | 0.388 | 0.007 | 0.005 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.003 | no | 36 | 1 |
| 37 | 手 | sigil-step-gated | 0.010 | 0.392 | 0.007 | 0.005 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | -0.002 | no | 37 | 1 |
| 38 | 手 | sigil-step-gated | 0.010 | 0.396 | 0.007 | 0.004 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.001 | no | 38 | 1 |
| 39 | 手 | sigil-step-gated | 0.011 | 0.401 | 0.008 | 0.005 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | 0.001 | no | 39 | 1 |
| 40 | 手 | sigil-step-gated | 0.010 | 0.404 | 0.006 | 0.004 | magma | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=2 | none | yes | -0.002 | -0.001 | no | 40 | 1 |

## Notes

- g1: regime chaos/magma: pull target change down
- g2: regime chaos/magma: pull target change down
- g3: regime chaos/magma: pull target change down
- g4: regime chaos/magma: pull target change down
- g4: stale novelty: boost novelty weight
- g5: regime chaos/magma: pull target change down
- g6: regime chaos/magma: pull target change down
- g7: regime chaos/magma: pull target change down
- g7: stale novelty: boost novelty weight
- g8: regime chaos/magma: pull target change down
- g9: regime chaos/magma: pull target change down
- g10: regime chaos/magma: pull target change down
- g10: stale novelty: boost novelty weight
- g11: regime chaos/magma: pull target change down
- g12: regime chaos/magma: pull target change down
- g13: regime chaos/magma: pull target change down
- g13: stale novelty: boost novelty weight
- g14: regime chaos/magma: pull target change down
- g15: regime chaos/magma: pull target change down
- g16: regime chaos/magma: pull target change down
- g16: stale novelty: boost novelty weight
- g17: regime chaos/magma: pull target change down
- g18: regime chaos/magma: pull target change down
- g19: regime chaos/magma: pull target change down
- g19: stale novelty: boost novelty weight
- g20: regime chaos/magma: pull target change down
- g21: regime chaos/magma: pull target change down
- g22: regime chaos/magma: pull target change down
- g22: stale novelty: boost novelty weight
- g23: regime chaos/magma: pull target change down
- g24: regime chaos/magma: pull target change down
- g25: regime chaos/magma: pull target change down
- g25: stale novelty: boost novelty weight
- g26: regime chaos/magma: pull target change down
- g27: regime chaos/magma: pull target change down
- g28: regime chaos/magma: pull target change down
- g28: stale novelty: boost novelty weight
- g29: regime chaos/magma: pull target change down
- g30: regime chaos/magma: pull target change down
- g31: regime chaos/magma: pull target change down
- g31: stale novelty: boost novelty weight
- g32: regime chaos/magma: pull target change down
- g33: regime chaos/magma: pull target change down
- g34: regime chaos/magma: pull target change down
- g34: stale novelty: boost novelty weight
- g35: regime chaos/magma: pull target change down
- g36: regime chaos/magma: pull target change down
- g37: regime chaos/magma: pull target change down
- g37: stale novelty: boost novelty weight
- g38: regime chaos/magma: pull target change down
- g39: regime chaos/magma: pull target change down
- g40: regime chaos/magma: pull target change down
- g40: stale novelty: boost novelty weight