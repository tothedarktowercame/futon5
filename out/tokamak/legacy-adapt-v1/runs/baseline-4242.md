# Tensor Tokamak Session

- Seed: `4242`
- Length: `120`
- Generations requested: `80`
- Phenotype enabled: `true`
- Explore rate: `0.350`
- Schedule: geno/phen/exo = `1/2/2`
- Mutation size: geno/phen = `2/4`
- Exo trigger: stagnation `3`, novelty-floor `0.030`, total-floor `0.200`
- Exo credit: window `4`, blend `0.250`
- Exo assimilation: threshold `0.020`, streak `2`, rate `0.120`

## Outcome

- Best total score: `0.541`
- Best generation: `1`
- Final total score: `0.006`
- Final regime: `eoc`
- Rewinds used: `1`
- Definition versions: `81`
- Exo-genotype versions: `81`
- Exo attempts/selected: `18/16`
- Exo changed cells total: `61`
- Layer credit cumulative: `{:genotype 0.08235719915120601, :xenotype 0.08235719915120601, :phenotype -0.14247845491380864, :exotype -0.026682638266078206}`

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
| 1 | 手 | sigil-step-gated | 0.541 | 0.359 | 1.000 | 0.500 | magma | yes | no | tilt/inactive [none/none] a=n s=n m=no-change c=0 | none | yes | 0.541 | 0.541 | no | 1 | 1 |
| 2 | 手 | sigil-step-gated | 0.201 | 0.319 | 0.028 | 0.019 | magma | yes | yes | tilt/regime [medium/regime] a=y s=n m=no-change c=0 | none | yes | -0.339 | -0.339 | no | 2 | 1 |
| 3 | 手 | sigil-step-gated | 0.178 | 0.292 | 0.008 | 0.005 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.023 | -0.193 | no | 3 | 1 |
| 4 | 手 | sigil-step-gated | 0.165 | 0.270 | 0.007 | 0.006 | magma | yes | yes | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.013 | -0.142 | no | 4 | 1 |
| 5 | 手 | sigil-step-gated | 0.144 | 0.266 | 0.006 | 0.004 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.020 | -0.127 | no | 5 | 1 |
| 6 | 手 | sigil-step-gated | 0.143 | 0.262 | 0.007 | 0.005 | magma | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=1 | none | yes | -0.001 | -0.029 | no | 6 | 1 |
| 7 | 手 | sigil-step-gated | 0.146 | 0.265 | 0.010 | 0.008 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.003 | -0.011 | no | 7 | 1 |
| 8 | 手 | sigil-step-gated | 0.130 | 0.267 | 0.008 | 0.006 | magma | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=3 | none | yes | -0.016 | -0.020 | no | 8 | 1 |
| 9 | 手 | sigil-step-gated | 0.134 | 0.276 | 0.009 | 0.006 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.005 | -0.007 | no | 9 | 1 |
| 10 | 手 | sigil-step-gated | 0.142 | 0.297 | 0.005 | 0.004 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.008 | 0.004 | no | 10 | 1 |
| 11 | 手 | sigil-step-gated | 0.133 | 0.318 | 0.006 | 0.004 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.009 | -0.005 | no | 11 | 1 |
| 12 | 手 | sigil-step-gated | 0.142 | 0.340 | 0.006 | 0.004 | magma | yes | yes | tilt/regime [medium/regime] a=y s=y m=strict-improve c=1 | none | yes | 0.009 | 0.007 | no | 12 | 1 |
| 13 | 手 | sigil-step-gated | 0.149 | 0.360 | 0.004 | 0.003 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.007 | 0.011 | no | 13 | 1 |
| 14 | 手 | sigil-step-gated | 0.137 | 0.383 | 0.007 | 0.004 | magma | yes | yes | tilt/regime [medium/regime] a=y s=n m=reject c=2 | none | yes | -0.012 | -0.005 | yes | 14 | 1 |
| 15 | 手 | sigil-step-gated | 0.145 | 0.405 | 0.007 | 0.004 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.008 | 0.004 | no | 15 | 1 |
| 16 | 手 | sigil-step-gated | 0.145 | 0.408 | 0.006 | 0.004 | magma | yes | yes | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.000 | 0.002 | no | 16 | 1 |
| 17 | 手 | sigil-step-gated | 0.124 | 0.414 | 0.007 | 0.004 | magma | yes | no | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | -0.021 | -0.020 | no | 17 | 1 |
| 18 | 手 | sigil-step-gated | 0.125 | 0.418 | 0.007 | 0.005 | magma | yes | yes | tilt/inactive [medium/regime] a=n s=n m=no-change c=0 | none | yes | 0.001 | -0.013 | no | 18 | 1 |
| 19 | 手 | sigil-step-gated | 0.127 | 0.423 | 0.007 | 0.005 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.002 | -0.008 | no | 19 | 1 |
| 20 | 手 | sigil-step-gated | 0.101 | 0.424 | 0.003 | 0.002 | magma | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=1 | none | yes | -0.026 | -0.030 | no | 20 | 1 |
| 21 | 手 | sigil-step-gated | 0.104 | 0.429 | 0.007 | 0.004 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.004 | -0.015 | no | 21 | 1 |
| 22 | 手 | sigil-step-gated | 0.103 | 0.431 | 0.004 | 0.003 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.001 | -0.011 | no | 22 | 1 |
| 23 | 手 | sigil-step-gated | 0.081 | 0.435 | 0.006 | 0.004 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.022 | -0.027 | no | 23 | 1 |
| 24 | 手 | sigil-step-gated | 0.081 | 0.437 | 0.005 | 0.003 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.016 | no | 24 | 1 |
| 25 | 手 | sigil-step-gated | 0.082 | 0.441 | 0.006 | 0.004 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | -0.010 | no | 25 | 1 |
| 26 | 手 | sigil-step-gated | 0.057 | 0.443 | 0.003 | 0.002 | magma | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=2 | none | yes | -0.025 | -0.030 | no | 26 | 1 |
| 27 | 手 | sigil-step-gated | 0.057 | 0.445 | 0.003 | 0.002 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | -0.018 | no | 27 | 1 |
| 28 | 手 | sigil-step-gated | 0.057 | 0.446 | 0.002 | 0.002 | magma | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=3 | none | yes | -0.001 | -0.013 | no | 28 | 1 |
| 29 | 手 | sigil-step-gated | 0.033 | 0.448 | 0.002 | 0.001 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.023 | -0.030 | no | 29 | 1 |
| 30 | 手 | sigil-step-gated | 0.033 | 0.448 | 0.002 | 0.001 | magma | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.018 | no | 30 | 1 |
| 31 | 手 | sigil-step-gated | 0.035 | 0.451 | 0.003 | 0.002 | magma | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.002 | -0.010 | no | 31 | 1 |
| 32 | 手 | sigil-step-gated | 0.012 | 0.452 | 0.003 | 0.002 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.023 | -0.028 | no | 32 | 1 |
| 33 | 手 | sigil-step-gated | 0.012 | 0.454 | 0.003 | 0.002 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.017 | no | 33 | 1 |
| 34 | 手 | sigil-step-gated | 0.012 | 0.456 | 0.003 | 0.002 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | -0.011 | no | 34 | 1 |
| 35 | 手 | sigil-step-gated | 0.008 | 0.458 | 0.003 | 0.002 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.004 | -0.010 | no | 35 | 1 |
| 36 | 手 | sigil-step-gated | 0.006 | 0.459 | 0.002 | 0.001 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=5 | none | yes | -0.001 | -0.004 | no | 36 | 1 |
| 37 | 手 | sigil-step-gated | 0.007 | 0.460 | 0.002 | 0.002 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | -0.003 | no | 37 | 1 |
| 38 | 手 | sigil-step-gated | 0.006 | 0.461 | 0.002 | 0.001 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=4 | none | yes | -0.001 | -0.002 | no | 38 | 1 |
| 39 | 手 | sigil-step-gated | 0.006 | 0.461 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.001 | no | 39 | 1 |
| 40 | 手 | sigil-step-gated | 0.008 | 0.463 | 0.003 | 0.002 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.002 | 0.001 | no | 40 | 1 |
| 41 | 手 | sigil-step-gated | 0.007 | 0.464 | 0.002 | 0.002 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.001 | 0.000 | no | 41 | 1 |
| 42 | 手 | sigil-step-gated | 0.006 | 0.464 | 0.002 | 0.001 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.000 | no | 42 | 1 |
| 43 | 手 | sigil-step-gated | 0.006 | 0.465 | 0.002 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | -0.000 | no | 43 | 1 |
| 44 | 手 | sigil-step-gated | 0.006 | 0.466 | 0.001 | 0.001 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=3 | none | yes | -0.000 | -0.001 | no | 44 | 1 |
| 45 | 手 | sigil-step-gated | 0.006 | 0.466 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.001 | no | 45 | 1 |
| 46 | 手 | sigil-step-gated | 0.006 | 0.467 | 0.001 | 0.001 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | -0.000 | no | 46 | 1 |
| 47 | 手 | sigil-step-gated | 0.006 | 0.468 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.000 | no | 47 | 1 |
| 48 | 手 | sigil-step-gated | 0.006 | 0.468 | 0.001 | 0.001 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=7 | none | yes | -0.000 | -0.000 | no | 48 | 1 |
| 49 | 手 | sigil-step-gated | 0.006 | 0.469 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | 0.000 | no | 49 | 1 |
| 50 | 手 | sigil-step-gated | 0.005 | 0.469 | 0.000 | 0.000 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=4 | none | yes | -0.001 | -0.001 | no | 50 | 1 |
| 51 | 手 | sigil-step-gated | 0.006 | 0.470 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | 0.000 | no | 51 | 1 |
| 52 | 手 | sigil-step-gated | 0.005 | 0.470 | 0.000 | 0.000 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.001 | -0.001 | no | 52 | 1 |
| 53 | 手 | sigil-step-gated | 0.006 | 0.470 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | 0.000 | no | 53 | 1 |
| 54 | 手 | sigil-step-gated | 0.006 | 0.471 | 0.001 | 0.001 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | 0.001 | no | 54 | 1 |
| 55 | 手 | sigil-step-gated | 0.007 | 0.473 | 0.002 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | 0.001 | no | 55 | 1 |
| 56 | 手 | sigil-step-gated | 0.006 | 0.474 | 0.001 | 0.001 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=5 | none | yes | -0.000 | 0.000 | no | 56 | 1 |
| 57 | 手 | sigil-step-gated | 0.006 | 0.475 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.000 | no | 57 | 1 |
| 58 | 手 | sigil-step-gated | 0.006 | 0.475 | 0.001 | 0.001 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.000 | no | 58 | 1 |
| 59 | 手 | sigil-step-gated | 0.006 | 0.476 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | 0.000 | no | 59 | 1 |
| 60 | 手 | sigil-step-gated | 0.006 | 0.477 | 0.001 | 0.001 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.000 | no | 60 | 1 |
| 61 | 手 | sigil-step-gated | 0.006 | 0.477 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.000 | no | 61 | 1 |
| 62 | 手 | sigil-step-gated | 0.006 | 0.478 | 0.002 | 0.001 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=6 | none | yes | 0.001 | 0.001 | no | 62 | 1 |
| 63 | 手 | sigil-step-gated | 0.006 | 0.479 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | 0.000 | no | 63 | 1 |
| 64 | 手 | sigil-step-gated | 0.006 | 0.480 | 0.001 | 0.001 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=4 | none | yes | 0.000 | 0.000 | no | 64 | 1 |
| 65 | 手 | sigil-step-gated | 0.006 | 0.482 | 0.002 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | 0.000 | no | 65 | 1 |
| 66 | 手 | sigil-step-gated | 0.006 | 0.483 | 0.001 | 0.001 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.000 | no | 66 | 1 |
| 67 | 手 | sigil-step-gated | 0.006 | 0.484 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.000 | no | 67 | 1 |
| 68 | 手 | sigil-step-gated | 0.006 | 0.484 | 0.001 | 0.001 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=4 | none | yes | -0.000 | -0.000 | no | 68 | 1 |
| 69 | 手 | sigil-step-gated | 0.007 | 0.486 | 0.002 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | 0.000 | no | 69 | 1 |
| 70 | 手 | sigil-step-gated | 0.006 | 0.486 | 0.001 | 0.001 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | 0.000 | no | 70 | 1 |
| 71 | 手 | sigil-step-gated | 0.007 | 0.488 | 0.002 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.001 | 0.001 | no | 71 | 1 |
| 72 | 手 | sigil-step-gated | 0.007 | 0.489 | 0.002 | 0.001 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | 0.000 | no | 72 | 1 |
| 73 | 手 | sigil-step-gated | 0.006 | 0.491 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.000 | no | 73 | 1 |
| 74 | 手 | sigil-step-gated | 0.006 | 0.491 | 0.002 | 0.001 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | -0.000 | no | 74 | 1 |
| 75 | 手 | sigil-step-gated | 0.007 | 0.493 | 0.002 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | 0.000 | no | 75 | 1 |
| 76 | 手 | sigil-step-gated | 0.007 | 0.494 | 0.002 | 0.001 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.000 | no | 76 | 1 |
| 77 | 手 | sigil-step-gated | 0.007 | 0.495 | 0.002 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | 0.000 | no | 77 | 1 |
| 78 | 手 | sigil-step-gated | 0.006 | 0.496 | 0.001 | 0.001 | eoc | yes | yes | tilt/stagnation [high/stagnation] a=y s=y m=bounded-explore c=6 | none | yes | -0.001 | -0.000 | no | 78 | 1 |
| 79 | 手 | sigil-step-gated | 0.006 | 0.497 | 0.001 | 0.001 | eoc | yes | no | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | 0.000 | -0.000 | no | 79 | 1 |
| 80 | 手 | sigil-step-gated | 0.006 | 0.498 | 0.001 | 0.001 | eoc | yes | yes | tilt/inactive [high/stagnation] a=n s=n m=no-change c=0 | none | yes | -0.000 | -0.000 | no | 80 | 1 |

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
- g34: stale novelty: boost novelty weight
- g37: stale novelty: boost novelty weight
- g40: stale novelty: boost novelty weight
- g43: stale novelty: boost novelty weight
- g46: stale novelty: boost novelty weight
- g49: stale novelty: boost novelty weight
- g52: stale novelty: boost novelty weight
- g55: stale novelty: boost novelty weight
- g58: stale novelty: boost novelty weight
- g61: stale novelty: boost novelty weight
- g64: stale novelty: boost novelty weight
- g67: stale novelty: boost novelty weight
- g70: stale novelty: boost novelty weight
- g73: stale novelty: boost novelty weight
- g76: stale novelty: boost novelty weight
- g79: stale novelty: boost novelty weight