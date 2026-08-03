# Exotype prevalence Slice 3

Fixed tau grid `[0.0 0.01 0.03 0.1 0.3 1.0 3.0 10.0]`; lambda fixed at `0.55`; N=`100` seeds per tau.

| tau | kinds present | Shannon entropy | changed steps | changed cells | phenotype activity | genotype rules |
|---:|---:|---:|---:|---:|---:|---:|
| 0.00 | 2.0000 (sd 0.0000; sem 0.0000) | 0.6030 (sd 0.0987; sem 0.0099) | 119.3700 (sd 2.3854; sem 0.2385) | 1334.0700 (sd 273.3317; sem 27.3332) | 0.3640 (sd 0.0343; sem 0.0034) | 63.0000 (sd 4.8137; sem 0.4814) |
| 0.01 | 1.8400 (sd 0.3949; sem 0.0395) | 0.4002 (sd 0.2331; sem 0.0233) | 96.2500 (sd 16.2725; sem 1.6273) | 487.0400 (sd 117.4118; sem 11.7412) | 0.3700 (sd 0.0348; sem 0.0035) | 43.6700 (sd 9.7339; sem 0.9734) |
| 0.03 | 1.9800 (sd 0.1407; sem 0.0141) | 0.5979 (sd 0.1292; sem 0.0129) | 81.9500 (sd 7.6163; sem 0.7616) | 415.0200 (sd 73.7973; sem 7.3797) | 0.3742 (sd 0.0348; sem 0.0035) | 56.7500 (sd 8.9909; sem 0.8991) |
| 0.10 | 1.9900 (sd 0.1000; sem 0.0100) | 0.6027 (sd 0.1304; sem 0.0130) | 66.2500 (sd 5.3567; sem 0.5357) | 527.3600 (sd 89.1028; sem 8.9103) | 0.3763 (sd 0.0342; sem 0.0034) | 61.3900 (sd 7.2473; sem 0.7247) |
| 0.30 | 2.0000 (sd 0.0000; sem 0.0000) | 0.6376 (sd 0.0665; sem 0.0067) | 59.8000 (sd 2.7780; sem 0.2778) | 591.4600 (sd 90.5849; sem 9.0585) | 0.3763 (sd 0.0299; sem 0.0030) | 61.0000 (sd 6.0952; sem 0.6095) |
| 1.00 | 2.2100 (sd 0.4984; sem 0.0498) | 0.6560 (sd 0.1225; sem 0.0123) | 61.5400 (sd 2.8477; sem 0.2848) | 679.0100 (sd 105.4266; sem 10.5427) | 0.3771 (sd 0.0351; sem 0.0035) | 58.7900 (sd 7.8910; sem 0.7891) |
| 3.00 | 3.8200 (sd 0.3861; sem 0.0386) | 1.1529 (sd 0.1533; sem 0.0153) | 58.8600 (sd 1.6269; sem 0.1627) | 861.3700 (sd 109.0975; sem 10.9097) | 0.3784 (sd 0.0338; sem 0.0034) | 61.0200 (sd 5.6120; sem 0.5612) |
| 10.00 | 3.9400 (sd 0.2387; sem 0.0239) | 1.2473 (sd 0.1105; sem 0.0111) | 57.1500 (sd 1.8000; sem 0.1800) | 892.6100 (sd 108.2625; sem 10.8262) | 0.3760 (sd 0.0356; sem 0.0036) | 60.2000 (sd 4.7990; sem 0.4799) |

## Final exotype distributions

```clojure
{0.0 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 30.87, :sd 13.685246055367788, :sem 1.3685246055367788, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 49.13, :sd 13.685246055367788, :sem 1.3685246055367788, :n 100}}, 0.01 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 64.08, :sd 13.730214414887628, :sem 1.373021441488763, :n 100}, :collapser {:mean 0.02, :sd 0.1999999999999999, :sem 0.01999999999999999, :n 100}, :identity {:mean 15.9, :sd 13.750665732276676, :sem 1.3750665732276677, :n 100}}, 0.03 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 40.4, :sd 16.64544102962451, :sem 1.664544102962451, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 39.6, :sd 16.64544102962451, :sem 1.664544102962451, :n 100}}, 0.1 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 32.11, :sd 14.142774862383998, :sem 1.4142774862383998, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 47.89, :sd 14.142774862383998, :sem 1.4142774862383998, :n 100}}, 0.3 {:builder {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :chaos {:mean 34.73, :sd 11.964471816949791, :sem 1.196447181694979, :n 100}, :collapser {:mean 0.0, :sd 0.0, :sem 0.0, :n 100}, :identity {:mean 45.27, :sd 11.964471816949791, :sem 1.196447181694979, :n 100}}, 1.0 {:builder {:mean 0.32, :sd 1.309483667232039, :sem 0.1309483667232039, :n 100}, :chaos {:mean 38.36, :sd 14.307248992639146, :sem 1.4307248992639146, :n 100}, :collapser {:mean 0.55, :sd 1.6414763002993507, :sem 0.16414763002993507, :n 100}, :identity {:mean 40.77, :sd 14.559425424338302, :sem 1.4559425424338301, :n 100}}, 3.0 {:builder {:mean 11.98, :sd 7.936592149625363, :sem 0.7936592149625363, :n 100}, :chaos {:mean 27.99, :sd 11.783823014984774, :sem 1.1783823014984773, :n 100}, :collapser {:mean 10.97, :sd 8.379213096777772, :sem 0.8379213096777771, :n 100}, :identity {:mean 29.06, :sd 11.258953788165238, :sem 1.1258953788165238, :n 100}}, 10.0 {:builder {:mean 17.35, :sd 8.76675884270011, :sem 0.876675884270011, :n 100}, :chaos {:mean 22.96, :sd 10.862706101077386, :sem 1.0862706101077386, :n 100}, :collapser {:mean 16.36, :sd 9.276362923630632, :sem 0.9276362923630632, :n 100}, :identity {:mean 23.33, :sd 9.85557833120027, :sem 0.985557833120027, :n 100}}}
```

## Apparatus checks

```clojure
{:legacy-endpoint {:comparison :legacy-state-abi-pr-str, :seeds-checked 100, :all-byte-identical? true, :mismatches []}, :determinism {:seed 20260803, :tau 0.3, :comparison :full-trajectory-pr-str, :byte-identical? true, :hash "8d8444a9"}}
```

## Entropy maximum

```clojure
{:criterion :mean-shannon-entropy, :tau 10.0, :mean 1.2473442831426795}
```

The entropy maximum coincides with the high-tau extreme, so that one panel serves both requested roles.

## Modelling choices

```clojure
{:policy-space [:hold :adopt-left :adopt-right], :prior-source :current-exotype-grid-only, :neighbourhood {:topology :circular, :radius 1, :includes-self true}, :duplicate-candidate-treatment "Each source policy receives the prevalence of its candidate exotype; policies remain distinct even when they yield the same exotype.", :positive-temperature :sample-q, :render {:seed 20260803, :content :exotype-grid}, :entropy {:log-base :natural, :zero-counts :omitted}, :cell-memory :none, :sampling :stateless-java-util-random-by-seed-time-index, :zero-temperature :legacy-argmin-g}
```

## Spacetime panels

- tau `0.0`: `reports/figures/slice3-fit-extreme-tau-0p00.png`
- tau `10.0`: `reports/figures/slice3-conformity-extreme-tau-10p00.png`

This report records measurements only; interpretation is reserved for review.
