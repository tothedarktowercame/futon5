# Exotype-grid Slice 1

Fixed seeds `20260803`–`20260826`; N=`24` per arm; width 80; t*=60; dt=59.

| arm | phenotype damage | genotype damage | exotype damage | pheno band | geno band | exo band | joint |
|---|---:|---:|---:|---:|---:|---:|---:|
| boring-triggered | 2.333 ± 3.158 | 1.708 ± 2.528 | 2.542 ± 4.452 | 0.16666666666666666 | nil | nil | nil |
| conformist | 1.458 ± 2.245 | 0.833 ± 0.381 | 1.000 ± 0.417 | 0.16666666666666666 | nil | nil | nil |
| heterogeneous-fixed | 1.292 ± 2.645 | 0.792 ± 0.415 | 1.000 ± 0.000 | 0.041666666666666664 | nil | nil | nil |
| uniform-fixed | 0.458 ± 1.062 | 1.000 ± 0.000 | 1.000 ± 0.000 | 0.16666666666666666 | nil | nil | nil |

## Measured anchors and bands

```clojure
{:anchors {:chaos {:exotype {:mean 1.0, :sd 0.0, :n 24}, :genotype {:mean 0.0, :sd 0.0, :n 24}, :phenotype {:mean 3.0, :sd 3.8221323436236365, :n 24}}, :collapser {:exotype {:mean 1.0, :sd 0.0, :n 24}, :genotype {:mean 1.0, :sd 0.0, :n 24}, :phenotype {:mean 1.7083333333333333, :sd 2.493106437788994, :n 24}}}, :bands {:exotype {:status :unbanded, :reason :anchors-equal, :ordered 1.0, :turbulent 1.0}, :genotype {:status :unbanded, :reason :anchors-inverted, :ordered 1.0, :turbulent 0.0}, :phenotype {:status :banded, :low 1.7083333333333333, :high 3.0}}}
```

The band is the inclusive interval from the measured collapser mean to the measured chaos mean. Equal or inverted anchors leave that layer explicitly unbanded. Because at least one layer is unbanded, `joint` is reported as `nil`, not imputed.

## Paired contrasts

```clojure
{:variation {:exotype {:mean 0.0, :sd 0.0, :n 24, :direction :right-minus-left}, :genotype {:mean -0.20833333333333334, :sd 0.41485111699905336, :n 24, :direction :right-minus-left}, :phenotype {:mean 0.8333333333333334, :sd 2.8386565509925217, :n 24, :direction :right-minus-left}}, :transmission {:conformist {:exotype {:mean 0.0, :sd 0.41702882811414954, :n 24, :direction :right-minus-left}, :genotype {:mean 0.041666666666666664, :sd 0.3586407511783863, :n 24, :direction :right-minus-left}, :phenotype {:mean 0.16666666666666666, :sd 1.6329931618554525, :n 24, :direction :right-minus-left}}, :boring-triggered {:exotype {:mean 1.5416666666666667, :sd 4.452445997686985, :n 24, :direction :right-minus-left}, :genotype {:mean 0.9166666666666666, :sd 2.500724532691635, :n 24, :direction :right-minus-left}, :phenotype {:mean 1.0416666666666667, :sd 4.27814635465923, :n 24, :direction :right-minus-left}}}}
```
