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


## Modelling choices

Damage uses t*=60, dt=59, midpoint perturbations, and phase anchors lambda=0.4/0.7. Long-horizon checkpoints are every 120 steps through 1200. Circular domains merge matching first/last runs. Invariance uses the fixed 5×3×3 matrix with no adaptive refinement. Natural-log entropy omits zero-count kinds. All grids update synchronously.
