# Exotype EFE Slice 2b — conatus-weight sweep

Fixed, prerequested lambda grid `[0.0 0.001 0.002 0.005 0.01 0.02 0.05 0.1 0.2 0.4 0.7 1.0]`; fixed seeds `20260803`–`20260902`; N=`100` per lambda. No adaptive refinement was performed.

| lambda | kinds present | Shannon entropy | changed steps | changed cells | phenotype activity | genotype rules |
|---:|---:|---:|---:|---:|---:|---:|
| 0.000 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3700 (sd 2.0531; sem 0.2053) | 81.9200 (sd 10.1491; sem 1.0149) | 0.3906 (sd 0.0312; sem 0.0031) | 68.9600 (sd 2.5462; sem 0.2546) |
| 0.001 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3700 (sd 2.0531; sem 0.2053) | 81.9200 (sd 10.1491; sem 1.0149) | 0.3906 (sd 0.0312; sem 0.0031) | 68.9600 (sd 2.5462; sem 0.2546) |
| 0.002 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3700 (sd 2.0531; sem 0.2053) | 81.9200 (sd 10.1491; sem 1.0149) | 0.3906 (sd 0.0312; sem 0.0031) | 68.9600 (sd 2.5462; sem 0.2546) |
| 0.005 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3700 (sd 2.0531; sem 0.2053) | 81.9200 (sd 10.1491; sem 1.0149) | 0.3906 (sd 0.0312; sem 0.0031) | 68.9600 (sd 2.5462; sem 0.2546) |
| 0.010 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3700 (sd 2.0531; sem 0.2053) | 81.9200 (sd 10.1491; sem 1.0149) | 0.3906 (sd 0.0312; sem 0.0031) | 68.9600 (sd 2.5462; sem 0.2546) |
| 0.020 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3700 (sd 2.0531; sem 0.2053) | 81.9200 (sd 10.1491; sem 1.0149) | 0.3906 (sd 0.0312; sem 0.0031) | 68.9600 (sd 2.5462; sem 0.2546) |
| 0.050 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3700 (sd 2.0531; sem 0.2053) | 81.9200 (sd 10.1491; sem 1.0149) | 0.3906 (sd 0.0312; sem 0.0031) | 68.9600 (sd 2.5462; sem 0.2546) |
| 0.100 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3700 (sd 2.0531; sem 0.2053) | 81.9200 (sd 10.1491; sem 1.0149) | 0.3906 (sd 0.0312; sem 0.0031) | 68.9600 (sd 2.5462; sem 0.2546) |
| 0.200 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3700 (sd 2.0531; sem 0.2053) | 84.9300 (sd 11.8384; sem 1.1838) | 0.3904 (sd 0.0317; sem 0.0032) | 69.0300 (sd 2.5916; sem 0.2592) |
| 0.400 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3700 (sd 2.0531; sem 0.2053) | 84.3000 (sd 11.9650; sem 1.1965) | 0.3888 (sd 0.0321; sem 0.0032) | 68.9200 (sd 2.7180; sem 0.2718) |
| 0.700 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.4000 (sd 2.1367; sem 0.2137) | 82.4700 (sd 11.5404; sem 1.1540) | 0.3585 (sd 0.0410; sem 0.0041) | 29.3300 (sd 1.3711; sem 0.1371) |
| 1.000 | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) | 6.3400 (sd 2.1236; sem 0.2124) | 79.5200 (sd 9.8979; sem 0.9898) | 0.3592 (sd 0.0392; sem 0.0039) | 29.3500 (sd 1.3587; sem 0.1359) |

## Final exotype distributions

Every cell count is mean, sd, and sem across 100 seeds.

```clojure
{0.0 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}}, 0.001 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}}, 0.002 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}}, 0.005 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}}, 0.01 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}}, 0.02 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}}, 0.05 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}}, 0.1 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}}, 0.2 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}}, 0.4 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}}, 0.7 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}}, 1.0 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 80.0, :sd 0.0, :sem 0.0, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}}}
```

## Diversity maximum

```clojure
{:criterion :mean-shannon-entropy, :lambda 0.0, :maximizers [0.0 0.001 0.002 0.005 0.01 0.02 0.05 0.1 0.2 0.4 0.7 1.0], :maximum-mean 0.0, :endpoint-contrasts {0.0 {:entropy {:mean 0.0, :sd 0.0, :sem 0.0, :n 100, :direction :right-minus-left, :sem-units nil, :more-than-2-sem? false}, :kind-count {:mean 0.0, :sd 0.0, :sem 0.0, :n 100, :direction :right-minus-left, :sem-units nil, :more-than-2-sem? false}}, 1.0 {:entropy {:mean 0.0, :sd 0.0, :sem 0.0, :n 100, :direction :right-minus-left, :sem-units nil, :more-than-2-sem? false}, :kind-count {:mean 0.0, :sd 0.0, :sem 0.0, :n 100, :direction :right-minus-left, :sem-units nil, :more-than-2-sem? false}}}}
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
- `lambda-max`: `reports/figures/slice2b-diversity-max-lambda-0p000.png`
- `lambda-1`: `reports/figures/slice2b-endpoint-one-lambda-1p000.png`
