# Exotype critical-point characterization

Fixed seeds `20260803`–`20260902`; N=`100` throughout.

## Three-layer damage

| lambda | phenotype | genotype | exotype |
|---:|---:|---:|---:|
| 0.40 | 0.4800 (sd 1.7261; sem 0.1726) | 1.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) |
| 0.55 | 7.7200 (sd 5.7368; sem 0.5737) | 9.9200 (sd 9.5385; sem 0.9539) | 7.0800 (sd 6.3543; sem 0.6354) |
| 0.70 | 3.6000 (sd 3.6790; sem 0.3679) | 0.0000 (sd 0.0000; sem 0.0000) | 0.0000 (sd 0.0000; sem 0.0000) |

Bands and critical-point fractions:

```clojure
{:bands {:exotype {:status :unbanded, :reason :anchors-equal, :ordered 0.0, :turbulent 0.0}, :genotype {:status :unbanded, :reason :anchors-inverted, :ordered 1.0, :turbulent 0.0}, :phenotype {:status :banded, :low 0.48, :high 3.6}}, :critical-band-fractions {:exotype nil, :genotype nil, :phenotype 0.06}, :critical-joint-fraction nil, :contrasts {0.4 {:exotype {:mean 7.08, :sd 6.354319297719762, :sem 0.6354319297719762, :n 100, :direction :right-minus-left, :sem-units 11.142027443507045, :resolved? true}, :genotype {:mean 8.92, :sd 9.538523698321155, :sem 0.9538523698321155, :n 100, :direction :right-minus-left, :sem-units 9.351551961410946, :resolved? true}, :phenotype {:mean 7.24, :sd 5.726281780553857, :sem 0.5726281780553857, :n 100, :direction :right-minus-left, :sem-units 12.643457443164339, :resolved? true}}, 0.7 {:exotype {:mean 7.08, :sd 6.354319297719762, :sem 0.6354319297719762, :n 100, :direction :right-minus-left, :sem-units 11.142027443507045, :resolved? true}, :genotype {:mean 9.92, :sd 9.538523698321155, :sem 0.9538523698321155, :n 100, :direction :right-minus-left, :sem-units 10.399932226143116, :resolved? true}, :phenotype {:mean 4.12, :sd 6.728921446887595, :sem 0.6728921446887595, :n 100, :direction :right-minus-left, :sem-units 6.12282374303191, :resolved? true}}}}
```

## Long-horizon mixture trajectory

Classification: `transient-toward-identity`. The step-120 mixture is not stationary; it drifts toward the identity phase.

| step | identity | chaos | kinds | entropy |
|---:|---:|---:|---:|---:|
| 120 | 49.1300 (sd 13.6852; sem 1.3685) | 30.8700 (sd 13.6852; sem 1.3685) | 2.0000 (sd 0.0000; sem 0.0000) | 0.6030 (sd 0.0987; sem 0.0099) |
| 240 | 60.9200 (sd 13.2396; sem 1.3240) | 19.0800 (sd 13.2396; sem 1.3240) | 1.9500 (sd 0.2190; sem 0.0219) | 0.4697 (sd 0.1945; sem 0.0194) |
| 360 | 65.4800 (sd 12.5420; sem 1.2542) | 14.5200 (sd 12.5420; sem 1.2542) | 1.7700 (sd 0.4230; sem 0.0423) | 0.3793 (sd 0.2506; sem 0.0251) |
| 480 | 69.9500 (sd 11.7906; sem 1.1791) | 10.0500 (sd 11.7906; sem 1.1791) | 1.6100 (sd 0.4902; sem 0.0490) | 0.2754 (sd 0.2582; sem 0.0258) |
| 600 | 72.9900 (sd 9.0381; sem 0.9038) | 7.0100 (sd 9.0381; sem 0.9038) | 1.4800 (sd 0.5021; sem 0.0502) | 0.2127 (sd 0.2456; sem 0.0246) |
| 720 | 74.4100 (sd 8.9444; sem 0.8944) | 5.5900 (sd 8.9444; sem 0.8944) | 1.3900 (sd 0.4902; sem 0.0490) | 0.1668 (sd 0.2336; sem 0.0234) |
| 840 | 75.6100 (sd 7.8905; sem 0.7891) | 4.3900 (sd 7.8905; sem 0.7891) | 1.3300 (sd 0.4726; sem 0.0473) | 0.1355 (sd 0.2158; sem 0.0216) |
| 960 | 76.5700 (sd 7.9191; sem 0.7919) | 3.4300 (sd 7.9191; sem 0.7919) | 1.2300 (sd 0.4230; sem 0.0423) | 0.0998 (sd 0.1969; sem 0.0197) |
| 1080 | 77.3900 (sd 6.4618; sem 0.6462) | 2.6100 (sd 6.4618; sem 0.6462) | 1.1800 (sd 0.3861; sem 0.0386) | 0.0797 (sd 0.1810; sem 0.0181) |
| 1200 | 77.4500 (sd 8.2270; sem 0.8227) | 2.5500 (sd 8.2270; sem 0.8227) | 1.1500 (sd 0.3589; sem 0.0359) | 0.0643 (sd 0.1683; sem 0.0168) |

Contrasts from step 120 and spatial structure:

```clojure
{:contrasts-from-120 {480 {:entropy {:mean -0.32757430412593214, :sd 0.260769194653261, :sem 0.026076919465326098, :n 100, :sem-units -12.561848210694535, :resolved? true}, :kind-count {:mean -0.39, :sd 0.4902071300001975, :sem 0.04902071300001975, :n 100, :sem-units -7.955820634429427, :resolved? true}}, 1200 {:entropy {:mean -0.538634474611447, :sd 0.20046562058299808, :sem 0.020046562058299806, :n 100, :sem-units -26.86916953864606, :resolved? true}, :kind-count {:mean -0.85, :sd 0.358870281282637, :sem 0.0358870281282637, :n 100, :sem-units -23.685438564654003, :resolved? true}}}, :spatial-at-120 {:pooled-domain-size-histogram {1 464, 2 177, 3 113, 4 71, 5 53, 6 45, 7 37, 8 35, 9 23, 10 20, 11 16, 12 19, 13 16, 14 16, 15 12, 16 12, 17 9, 18 6, 19 16, 20 6, 21 4, 22 6, 23 9, 24 3, 25 2, 26 8, 27 8, 28 1, 29 6, 30 2, 31 3, 32 2, 34 2, 35 2, 36 4, 38 1, 39 3, 40 2, 41 2, 42 1, 43 1, 44 1, 46 2, 47 3, 48 1, 54 1, 55 2, 56 1, 57 1, 58 1, 59 1, 61 2, 62 1, 66 1, 72 2}, :pooled-domain-size {:mean 6.359300476947536, :sd 9.687134238612373, :sem 0.2731209393318411, :n 1258}, :domains-per-run {:mean 12.58, :sd 5.528950436501754, :sem 0.5528950436501754, :n 100}, :maximum-domain-per-run {:mean 32.28, :sd 14.25601414820915, :sem 1.425601414820915, :n 100}}}
```


## Modelling choices

Damage uses t*=60, dt=59, midpoint perturbations, and phase anchors lambda=0.4/0.7. Long-horizon checkpoints are every 120 steps through 1200. Circular domains merge matching first/last runs. Invariance uses the fixed 5×3×3 matrix with no adaptive refinement. Natural-log entropy omits zero-count kinds. All grids update synchronously.
