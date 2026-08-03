# Exotype EFE Slice 2b — conatus-weight sweep

Fixed, prerequested lambda grid `[0.0 0.525 0.55 0.575 1.0]`; fixed seeds `20260803`–`20260902`; N=`100` per lambda. No adaptive refinement was performed.

| lambda | kinds present | Shannon entropy | changed steps | changed cells | phenotype activity | genotype rules |
|---:|---:|---:|---:|---:|---:|---:|
| 0.000 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3700 (sd 2.0531; sem 0.2053) | 81.9200 (sd 10.1491; sem 1.0149) | 0.3906 (sd 0.0312; sem 0.0031) | 68.9600 (sd 2.5462; sem 0.2546) |
| 0.525 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3800 (sd 2.0439; sem 0.2044) | 81.5100 (sd 9.2513; sem 0.9251) | 0.3896 (sd 0.0327; sem 0.0033) | 68.7800 (sd 2.8376; sem 0.2838) |
| 0.550 | 2.0000 (sd 0.0000; sem 0.0000) | 0.6030 (sd 0.0987; sem 0.0099) | 119.3700 (sd 2.3854; sem 0.2385) | 1334.0700 (sd 273.3317; sem 27.3332) | 0.3640 (sd 0.0343; sem 0.0034) | 63.0000 (sd 4.8137; sem 0.4814) |
| 0.575 | 1.1900 (sd 0.3943; sem 0.0394) | 0.0287 (sd 0.0623; sem 0.0062) | 33.4100 (sd 23.7772; sem 2.3777) | 169.3200 (sd 50.2850; sem 5.0285) | 0.3640 (sd 0.0384; sem 0.0038) | 29.8700 (sd 1.8730; sem 0.1873) |
| 1.000 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3400 (sd 2.1236; sem 0.2124) | 79.5200 (sd 9.8979; sem 0.9898) | 0.3592 (sd 0.0392; sem 0.0039) | 29.3500 (sd 1.3587; sem 0.1359) |

## Final exotype distributions

Every cell count is mean, sd, and sem across 100 seeds.

```clojure
{0.0 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}}, 0.525 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}}, 0.55 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 30.87, :sd 13.685246055367788, :sem 1.3685246055367788, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 49.13, :sd 13.685246055367788, :sem 1.3685246055367788, :n 100}}, 0.575 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 79.46, :sd 1.2095578956917699, :sem 0.12095578956917699, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 0.54, :sd 1.20955789569177, :sem 0.120955789569177, :n 100}}, 1.0 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}}}
```

## Diversity maximum

```clojure
{:criterion :mean-shannon-entropy, :lambda 0.55, :maximizers [0.55], :maximum-mean 0.6029576048295047, :endpoint-contrasts {0.0 {:entropy {:mean 0.6029576048295047, :sd 0.0987348922114738, :sem 0.00987348922114738, :n 100, :direction :right-minus-left, :sem-units 61.06834081897504, :more-than-2-sem? true}, :kind-count {:mean 1.0, :sd 0.0, :sem 0.0, :n 100, :direction :right-minus-left, :sem-units nil, :more-than-2-sem? false}}, 1.0 {:entropy {:mean 0.6029576048295047, :sd 0.0987348922114738, :sem 0.00987348922114738, :n 100, :direction :right-minus-left, :sem-units 61.06834081897504, :more-than-2-sem? true}, :kind-count {:mean 1.0, :sd 0.0, :sem 0.0, :n 100, :direction :right-minus-left, :sem-units nil, :more-than-2-sem? false}}}}
```

The maximisation criterion was mean Shannon entropy. `:maximizers` reports every tie; `:lambda` is the smallest tied value used only as the representative render. `:more-than-2-sem?` is the prerequested paired comparison with each endpoint.

## Risk-preference diagnostic

The default remains `0.15`. Alternative values are diagnostics only. `:pooled-selected-policies` gives cell-step spread; `:seed-means` gives the directly comparable between-seed spread for five fixed 120-step seeds.

```clojure
{0.15 {:pooled-selected-policies {:mean 1.395380274038187, :sd 0.04913778754179838, :sem 2.2428228885449413E-4, :n 48000, :min 0.0033536326575399934, :median 1.3985769059556226, :max 1.3985769059556226}, :seed-means {:mean 1.395380274037441, :sd 4.644224831347389E-4, :sem 2.0769604851370515E-4, :n 5}}, 0.4 {:pooled-selected-policies {:mean 0.5491452655108097, :sd 0.020548840599721195, :sem 9.37921960587107E-5, :n 48000, :min 0.11241585641536625, :median 0.5506612476718904, :max 0.5506612476718904}, :seed-means {:mean 0.5491452655114164, :sd 2.3992521998514305E-4, :sem 1.0729782028067418E-4, :n 5}}, 0.6 {:pooled-selected-policies {:mean 0.2258192790035165, :sd 0.01524849320514619, :sem 6.959953080352349E-5, :n 48000, :min 0.07311998611840653, :median 0.22628916118535888, :max 0.8101423164322596}, :seed-means {:mean 0.22581927900373575, :sd 4.209602579505406E-4, :sem 1.88259150520651E-4, :n 5}}}
```

## Modelling choices

```clojure
{:sweep :fixed-no-adaptive-refinement, :entropy {:log-base :natural, :zero-counts :omitted}, :peak-tie-break :smallest-lambda, :risk-probe {:seeds 5, :scope :selected-policies, :default-unchanged true}, :render-seed 20260803}
```

## Spacetime panels

- `lambda-0`: `reports/figures/slice2b-endpoint-zero-lambda-0p000.png`
- `lambda-max`: `reports/figures/slice2b-diversity-max-lambda-0p550.png`
- `lambda-1`: `reports/figures/slice2b-endpoint-one-lambda-1p000.png`

---

## CORRECTION (claude-11, 2026-08-03): this sweep missed the transition

**The "no interior" conclusion above is NOT established by this run.** Its grid was
`[0 0.001 0.002 0.005 0.01 0.02 0.05 0.1 0.2 0.4 0.7 1.0]` — eight of twelve points
below 0.1, and **no point at all inside (0.4, 0.7)**, which is precisely where the
system changes regime. claude-11 specified that grid on the guess that the transition
sat near zero, because a preferred-hunger of 0.05 already produced full chaos. The guess
was wrong. The run establishes only that lambda <= 0.4 gives `:identity` and
lambda >= 0.7 gives `:chaos`.

**A refined sweep across the unsampled interval finds an interior.** See
`exotype-efe-slice2b-refined.{md,edn}` (same driver, same seeds, 15 weights):

| lambda | kinds | entropy | changed steps | changed cells | genotype rules |
|---:|---:|---:|---:|---:|---:|
| 0.525 | 1.00 | 0.0000 | 6.38 | 81.5 | 68.78 |
| **0.550** | **2.00** | **0.6030** | **119.37** | **1334.07** | **63.00** |
| 0.575 | 1.19 | 0.0287 | 33.41 | 169.3 | 29.87 |
| 0.600 | 1.07 | 0.0072 | 7.06 | 81.7 | 29.39 |

At lambda = 0.55 **every one of 100 seeds ends with exactly two exotypes coexisting**
(sd 0.0000), entropy 0.603 against a two-kind maximum of 0.693. Grid activity rises from
~6 changed steps to **119 of 120**, and changed cells from ~84 to **1334, a 16-fold
increase**. Genotype rule count sits at 63.0, between the identity regime's 69 and the
chaos regime's 29.

The window is narrow — collapsed at 0.525, nearly collapsed by 0.575 — which is what a
critical point should look like. Phenotype activity declines monotonically across the
whole sweep (0.389 -> 0.359) with no peak, so the signature is at the exotype and
genotype layers only.

**So Joe's reading was right: the system needed tuning, not discarding.** The
bistability reported above is real at the sampled points and not a property of the
regulator.

**Method note.** This is the third correction today where the measurement was sound and
the SAMPLING was the defect (see also `TN-GCD-resolve-before-submit.md`, and the
single-draw null in `lift_variant_compare.clj`). The generalisable rule: bracket the
outcome extremes first, then sweep densely BETWEEN them, rather than around a guessed
transition point.
