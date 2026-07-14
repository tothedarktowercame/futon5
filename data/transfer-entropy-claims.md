# Transfer entropy: trust-anchor and MetaCA eye-check

## Verdict

The untuned nearest-neighbor transfer-entropy (TE) discriminator **does not
separate edge-of-chaos dynamics from chaos**. It correctly rejects frozen rules,
but ranks the chaotic ECA class above the complex class. The MetaCA eye-check
also fails: TE ranks the visually noise-like blend above mutating-template's
diagonal/glider dynamics.

This is a committed negative result, not a tuned-away failure. TE remains a
valid spatial information-transfer measure alongside AIS, but under this
protocol it is not a reliable standalone EoC discriminator.

## ECA rule table

Miller–Madow-corrected TE is in bits, mean ± 95% CI over 20 matched seeds.

| Class | Rule | TE (bits) |
|---|---:|---:|
| Ordered | 0 | 0.0000 ± 0.0000 |
| Ordered | 8 | 0.0000 ± 0.0000 |
| Ordered | 128 | 0.0000 ± 0.0000 |
| Chaotic | 30 | 0.1510 ± 0.0038 |
| Chaotic | 45 | 0.2368 ± 0.0271 |
| Chaotic | 90 | 0.2350 ± 0.0008 |
| Complex / EoC | 110 | 0.0661 ± 0.0070 |
| Complex / EoC | 54 | 0.0786 ± 0.0043 |
| Complex / EoC | 137 | 0.0702 ± 0.0090 |

## ECA class comparison

| Class | Corrected TE (bits; mean ± 95% CI) | Samples |
|---|---:|---:|
| Ordered | 0.0000 ± 0.0000 | 60 |
| Chaotic | 0.2076 ± 0.0136 | 60 |
| Complex / EoC | 0.0716 ± 0.0042 | 60 |

The predeclared acceptance criterion fails: the complex lower bound is `0.0674`,
while the largest control upper bound is `0.2212`.

## AIS versus TE versus the eye

Both measures are corrected bits, mean ± 95% CI over 20 matched seeds.

| Dynamic | AIS | TE | Visual note from matched diagram |
|---|---:|---:|---|
| Mutating-template | 0.5210 ± 0.0096 | 0.1068 ± 0.0032 | Large domains and diagonal/glider structure |
| Baldwin | 0.4501 ± 0.0075 | 0.1078 ± 0.0015 | Banded mixed texture |
| Template-combine + Baldwin-mutate | 0.5708 ± 0.0011 | 0.1695 ± 0.0005 | Densest and most noise-like |

TE does **not** agree with the eye where AIS disagreed: both scalar measures rank
the blend highest. The ECA control explains why TE does so—nearest-neighbor
causal transfer is also abundant in chaotic deterministic dynamics. A future
EoC evaluator would need a joint criterion (for example transfer constrained by
coherent domains), not post-hoc retuning of TE.

## Protocol and estimator

- TE = `I(source[t-1]; destination[t] | destination[t-8..t-1])`.
- Directed nearest-neighbor links from both sides; fixed boundary cells excluded.
- Per-link conditional MI is averaged across links, then across all eight MetaCA
  genotype bitplanes. No best-plane selection.
- Same ECA grids, seeds, width, steps, and burn-in as the committed AIS anchor.
- Same MetaCA grids and matched seeds as the committed AIS comparison.
- This is a **statistical replay**; no per-seed determinism claim is made.
- Machine-readable artifacts: `transfer-entropy-eca-validation.edn` and
  `transfer-entropy-dynamics.edn`.
