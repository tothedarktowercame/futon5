# Tensor Tokamak Session

- Seed: `1188240613`
- Length: `32`
- Generations requested: `40`
- Phenotype enabled: `true`
- Explore rate: `0.350`
- Schedule: geno/phen/exo = `1/2/2`
- Mutation size: geno/phen = `2/4`
- Exo trigger: stagnation `3`, novelty-floor `0.030`, total-floor `0.200`
- Exo credit: window `4`, blend `0.250`
- Exo assimilation: threshold `0.020`, streak `2`, rate `0.120`

## Outcome

- Best total score: `0.456`
- Best generation: `1`
- Final total score: `0.008`
- Final regime: `eoc`
- Rewinds used: `1`
- Definition versions: `41`
- Exo-genotype versions: `41`
- Exo attempts/selected: `6/3`
- Exo changed cells total: `12`
- Layer credit cumulative: `{:genotype 0.05894277996786738, :xenotype 0.05894277996786738, :phenotype -0.10987411588761001, :exotype -0.008942383016017864}`

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
| 1 | 手 | sigil-step-gated | 0.456 | 0.219 | 1.000 | 0.500 | eoc | yes | no | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | 0.456 | 0.456 | no | 1 | 1 |
| 2 | 手 | sigil-step-gated | 0.184 | 0.243 | 0.110 | 0.073 | magma | yes | yes | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | -0.272 | -0.272 | no | 2 | 1 |
| 3 | 手 | sigil-step-gated | 0.167 | 0.252 | 0.045 | 0.030 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.018 | -0.153 | no | 3 | 1 |
| 4 | 手 | sigil-step-gated | 0.189 | 0.282 | 0.055 | 0.037 | magma | yes | yes | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.022 | -0.081 | no | 4 | 1 |
| 5 | 手 | sigil-step-gated | 0.185 | 0.308 | 0.048 | 0.032 | eoc | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.004 | -0.064 | no | 5 | 1 |
| 6 | 手 | sigil-step-gated | 0.206 | 0.351 | 0.044 | 0.029 | eoc | yes | yes | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | 0.021 | 0.024 | no | 6 | 1 |
| 7 | 手 | sigil-step-gated | 0.223 | 0.383 | 0.045 | 0.030 | eoc | yes | no | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | 0.018 | 0.037 | no | 7 | 1 |
| 8 | 手 | sigil-step-gated | 0.215 | 0.419 | 0.038 | 0.025 | eoc | yes | yes | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | -0.008 | 0.014 | no | 8 | 1 |
| 9 | 手 | sigil-step-gated | 0.221 | 0.439 | 0.030 | 0.020 | eoc | yes | no | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | 0.006 | 0.013 | no | 9 | 1 |
| 10 | 手 | sigil-step-gated | 0.225 | 0.454 | 0.024 | 0.016 | eoc | yes | yes | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | 0.004 | 0.009 | no | 10 | 1 |
| 11 | 手 | sigil-step-gated | 0.197 | 0.462 | 0.015 | 0.010 | eoc | yes | no | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | -0.028 | -0.025 | no | 11 | 1 |
| 12 | 手 | sigil-step-gated | 0.202 | 0.476 | 0.014 | 0.009 | eoc | yes | yes | tilt/novelty+total-floor [low/novelty+total-floor] a=y s=n m=no-change c=0 | none | yes | 0.005 | -0.013 | no | 12 | 1 |
| 13 | 手 | sigil-step-gated | 0.207 | 0.484 | 0.017 | 0.012 | eoc | yes | no | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | 0.005 | -0.004 | no | 13 | 1 |
| 14 | 手 | sigil-step-gated | 0.177 | 0.492 | 0.011 | 0.007 | eoc | yes | yes | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | -0.029 | -0.030 | no | 14 | 1 |
| 15 | 手 | sigil-step-gated | 0.187 | 0.506 | 0.019 | 0.013 | eoc | yes | no | tilt/inactive [low/novelty+total-floor] a=n s=n m=no-change c=0 | none | yes | 0.010 | -0.008 | no | 15 | 1 |
| 16 | 手 | sigil-step-gated | 0.193 | 0.523 | 0.018 | 0.012 | eoc | yes | yes | tilt/novelty+total-floor [low/novelty+total-floor] a=y s=n m=reject c=3 | none | yes | 0.006 | -0.001 | yes | 16 | 1 |
| 17 | 手 | sigil-step-gated | 0.164 | 0.534 | 0.015 | 0.010 | eoc | yes | no | tilt/inactive [low/novelty+total-floor] a=n s=n m=no-change c=0 | none | yes | -0.028 | -0.027 | no | 17 | 1 |
| 18 | 手 | sigil-step-gated | 0.173 | 0.553 | 0.020 | 0.013 | eoc | yes | yes | tilt/inactive [low/novelty+total-floor] a=n s=n m=no-change c=0 | none | yes | 0.008 | -0.008 | no | 18 | 1 |
| 19 | 手 | sigil-step-gated | 0.174 | 0.565 | 0.017 | 0.011 | eoc | yes | no | tilt/inactive [low/novelty+total-floor] a=n s=n m=no-change c=0 | none | yes | 0.002 | -0.005 | no | 19 | 1 |
| 20 | 手 | sigil-step-gated | 0.147 | 0.581 | 0.017 | 0.011 | eoc | yes | yes | tilt/inactive [low/novelty+total-floor] a=n s=n m=no-change c=0 | none | yes | -0.028 | -0.029 | no | 20 | 1 |
| 21 | 手 | sigil-step-gated | 0.143 | 0.587 | 0.010 | 0.006 | eoc | yes | no | tilt/inactive [low/novelty+total-floor] a=n s=n m=no-change c=0 | none | yes | -0.004 | -0.022 | no | 21 | 1 |
| 22 | 手 | sigil-step-gated | 0.144 | 0.595 | 0.009 | 0.006 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=n m=no-change c=0 | none | yes | 0.001 | -0.015 | no | 22 | 1 |
| 23 | 手 | sigil-step-gated | 0.112 | 0.600 | 0.008 | 0.006 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.032 | -0.040 | no | 23 | 1 |
| 24 | 手 | sigil-step-gated | 0.115 | 0.609 | 0.010 | 0.006 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.003 | -0.022 | no | 24 | 1 |
| 25 | 手 | sigil-step-gated | 0.114 | 0.607 | 0.009 | 0.006 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.001 | -0.014 | no | 25 | 1 |
| 26 | 手 | sigil-step-gated | 0.085 | 0.609 | 0.012 | 0.008 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.029 | -0.036 | no | 26 | 1 |
| 27 | 手 | sigil-step-gated | 0.082 | 0.607 | 0.009 | 0.006 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.003 | -0.025 | no | 27 | 1 |
| 28 | 手 | sigil-step-gated | 0.080 | 0.607 | 0.007 | 0.004 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=1 | none | yes | -0.002 | -0.019 | no | 28 | 1 |
| 29 | 手 | sigil-step-gated | 0.047 | 0.605 | 0.005 | 0.003 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.033 | -0.043 | no | 29 | 1 |
| 30 | 手 | sigil-step-gated | 0.047 | 0.602 | 0.005 | 0.004 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=strict-improve c=4 | none | yes | 0.000 | -0.026 | no | 30 | 1 |
| 31 | 手 | sigil-step-gated | 0.045 | 0.601 | 0.003 | 0.002 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.003 | -0.019 | no | 31 | 1 |
| 32 | 手 | sigil-step-gated | 0.017 | 0.599 | 0.005 | 0.003 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.028 | -0.038 | no | 32 | 1 |
| 33 | 手 | sigil-step-gated | 0.016 | 0.596 | 0.005 | 0.003 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.023 | no | 33 | 1 |
| 34 | 手 | sigil-step-gated | 0.014 | 0.596 | 0.003 | 0.002 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.002 | -0.017 | no | 34 | 1 |
| 35 | 手 | sigil-step-gated | 0.008 | 0.594 | 0.002 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.006 | -0.015 | no | 35 | 1 |
| 36 | 手 | sigil-step-gated | 0.011 | 0.592 | 0.005 | 0.004 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=strict-improve c=4 | none | yes | 0.003 | -0.003 | no | 36 | 1 |
| 37 | 手 | sigil-step-gated | 0.010 | 0.590 | 0.004 | 0.003 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.001 | -0.002 | no | 37 | 1 |
| 38 | 手 | sigil-step-gated | 0.009 | 0.591 | 0.003 | 0.002 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.001 | -0.002 | no | 38 | 1 |
| 39 | 手 | sigil-step-gated | 0.011 | 0.589 | 0.005 | 0.003 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.002 | 0.001 | no | 39 | 1 |
| 40 | 手 | sigil-step-gated | 0.008 | 0.589 | 0.003 | 0.002 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.003 | -0.002 | no | 40 | 1 |

## Notes

- g2: regime chaos/magma: pull target change down
- g3: regime chaos/magma: pull target change down
- g4: regime chaos/magma: pull target change down
- g4: stale novelty: boost novelty weight
- g7: stale novelty: boost novelty weight
- g10: stale novelty: boost novelty weight
- g13: stale novelty: boost novelty weight
- g16: stale novelty: boost novelty weight
- g19: stale novelty: boost novelty weight
- g22: stale novelty: boost novelty weight
- g25: stale novelty: boost novelty weight
- g28: stale novelty: boost novelty weight
- g31: stale novelty: boost novelty weight
- g34: stale novelty: boost novelty weight
- g37: stale novelty: boost novelty weight
- g40: stale novelty: boost novelty weight