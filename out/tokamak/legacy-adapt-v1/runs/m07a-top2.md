# Tensor Tokamak Session

- Seed: `1701878710`
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

- Best total score: `0.458`
- Best generation: `1`
- Final total score: `0.010`
- Final regime: `eoc`
- Rewinds used: `2`
- Definition versions: `41`
- Exo-genotype versions: `41`
- Exo attempts/selected: `9/4`
- Exo changed cells total: `14`
- Layer credit cumulative: `{:genotype 0.07250538137063553, :xenotype 0.07250538137063553, :phenotype -0.12735824382040814, :exotype -0.011101850039436477}`

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
| 1 | 手 | sigil-step-gated | 0.458 | 0.222 | 1.000 | 0.500 | magma | yes | no | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | 0.458 | 0.458 | no | 1 | 1 |
| 2 | 手 | sigil-step-gated | 0.128 | 0.199 | 0.024 | 0.016 | magma | yes | yes | tilt/regime [medium/regime] a=y s=n m=no-change c=0 | none | yes | -0.330 | -0.330 | no | 2 | 1 |
| 3 | 手 | sigil-step-gated | 0.109 | 0.170 | 0.020 | 0.013 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.019 | -0.184 | no | 3 | 1 |
| 4 | 手 | sigil-step-gated | 0.107 | 0.164 | 0.025 | 0.017 | magma | yes | yes | tilt/regime [medium/regime] a=y s=n m=no-change c=0 | none | yes | -0.001 | -0.124 | no | 4 | 1 |
| 5 | 手 | sigil-step-gated | 0.093 | 0.163 | 0.014 | 0.009 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.015 | -0.108 | no | 5 | 1 |
| 6 | 手 | sigil-step-gated | 0.090 | 0.161 | 0.009 | 0.006 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.003 | -0.020 | no | 6 | 1 |
| 7 | 手 | sigil-step-gated | 0.090 | 0.160 | 0.012 | 0.008 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | -0.009 | no | 7 | 1 |
| 8 | 手 | sigil-step-gated | 0.091 | 0.172 | 0.021 | 0.014 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | -0.004 | no | 8 | 1 |
| 9 | 手 | sigil-step-gated | 0.099 | 0.194 | 0.016 | 0.011 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.008 | 0.008 | no | 9 | 1 |
| 10 | 手 | sigil-step-gated | 0.114 | 0.225 | 0.016 | 0.011 | magma | yes | yes | tilt/regime [medium/regime] a=y s=n m=reject c=1 | none | yes | 0.015 | 0.021 | yes | 10 | 1 |
| 11 | 手 | sigil-step-gated | 0.109 | 0.249 | 0.015 | 0.010 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.004 | 0.011 | no | 11 | 1 |
| 12 | 手 | sigil-step-gated | 0.122 | 0.277 | 0.017 | 0.011 | magma | yes | yes | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.013 | 0.019 | no | 12 | 1 |
| 13 | 手 | sigil-step-gated | 0.130 | 0.300 | 0.013 | 0.009 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.007 | 0.019 | no | 13 | 1 |
| 14 | 手 | sigil-step-gated | 0.117 | 0.323 | 0.007 | 0.005 | magma | yes | yes | tilt/regime [medium/regime] a=y s=n m=reject c=1 | none | yes | -0.013 | -0.002 | yes | 14 | 1 |
| 15 | 手 | sigil-step-gated | 0.127 | 0.341 | 0.014 | 0.009 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.011 | 0.008 | no | 15 | 1 |
| 16 | 手 | sigil-step-gated | 0.124 | 0.333 | 0.013 | 0.009 | magma | yes | yes | tilt/regime [medium/regime] a=y s=y m=strict-improve c=1 | none | yes | -0.003 | -0.000 | no | 16 | 1 |
| 17 | 手 | sigil-step-gated | 0.109 | 0.346 | 0.014 | 0.009 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.015 | -0.015 | no | 17 | 1 |
| 18 | 手 | sigil-step-gated | 0.110 | 0.352 | 0.012 | 0.008 | magma | yes | yes | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.001 | -0.009 | no | 18 | 1 |
| 19 | 手 | sigil-step-gated | 0.114 | 0.363 | 0.014 | 0.009 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.004 | -0.003 | no | 19 | 1 |
| 20 | 手 | sigil-step-gated | 0.095 | 0.370 | 0.013 | 0.008 | magma | yes | yes | tilt/stagnation [high/stagnation] a=y s=n m=no-change c=0 | none | yes | -0.019 | -0.019 | no | 20 | 1 |
| 21 | 手 | sigil-step-gated | 0.097 | 0.379 | 0.013 | 0.009 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.003 | -0.009 | no | 21 | 1 |
| 22 | 手 | sigil-step-gated | 0.100 | 0.388 | 0.014 | 0.009 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.003 | -0.004 | no | 22 | 1 |
| 23 | 手 | sigil-step-gated | 0.080 | 0.397 | 0.013 | 0.008 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.020 | -0.022 | no | 23 | 1 |
| 24 | 手 | sigil-step-gated | 0.079 | 0.403 | 0.010 | 0.007 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.001 | -0.014 | no | 24 | 1 |
| 25 | 手 | sigil-step-gated | 0.081 | 0.410 | 0.011 | 0.008 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.002 | -0.008 | no | 25 | 1 |
| 26 | 手 | sigil-step-gated | 0.061 | 0.418 | 0.011 | 0.008 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.020 | -0.024 | no | 26 | 1 |
| 27 | 手 | sigil-step-gated | 0.061 | 0.427 | 0.011 | 0.007 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | -0.014 | no | 27 | 1 |
| 28 | 手 | sigil-step-gated | 0.061 | 0.433 | 0.009 | 0.006 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.001 | -0.010 | no | 28 | 1 |
| 29 | 手 | sigil-step-gated | 0.041 | 0.441 | 0.011 | 0.007 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.020 | -0.025 | no | 29 | 1 |
| 30 | 手 | sigil-step-gated | 0.039 | 0.446 | 0.009 | 0.006 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=4 | none | yes | -0.001 | -0.016 | no | 30 | 1 |
| 31 | 手 | sigil-step-gated | 0.040 | 0.453 | 0.009 | 0.006 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | -0.011 | no | 31 | 1 |
| 32 | 手 | sigil-step-gated | 0.015 | 0.457 | 0.007 | 0.004 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=5 | none | yes | -0.025 | -0.030 | no | 32 | 1 |
| 33 | 手 | sigil-step-gated | 0.017 | 0.464 | 0.008 | 0.006 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.002 | -0.017 | no | 33 | 1 |
| 34 | 手 | sigil-step-gated | 0.018 | 0.470 | 0.009 | 0.006 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | -0.010 | no | 34 | 1 |
| 35 | 手 | sigil-step-gated | 0.014 | 0.476 | 0.009 | 0.006 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.005 | -0.009 | no | 35 | 1 |
| 36 | 手 | sigil-step-gated | 0.011 | 0.480 | 0.007 | 0.005 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.002 | -0.005 | no | 36 | 1 |
| 37 | 手 | sigil-step-gated | 0.011 | 0.485 | 0.007 | 0.005 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.004 | no | 37 | 1 |
| 38 | 手 | sigil-step-gated | 0.012 | 0.489 | 0.007 | 0.005 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | -0.002 | no | 38 | 1 |
| 39 | 手 | sigil-step-gated | 0.012 | 0.495 | 0.008 | 0.005 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | 0.000 | no | 39 | 1 |
| 40 | 手 | sigil-step-gated | 0.010 | 0.497 | 0.005 | 0.003 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=2 | none | yes | -0.003 | -0.002 | no | 40 | 1 |

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
- g25: stale novelty: boost novelty weight
- g28: stale novelty: boost novelty weight
- g31: stale novelty: boost novelty weight
- g34: stale novelty: boost novelty weight
- g37: stale novelty: boost novelty weight
- g40: stale novelty: boost novelty weight