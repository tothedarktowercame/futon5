# TN-exotype-placement — a conditional propagator on the calibrated reach scale

**Status: measured 2026-07-29.** This is a technical note, not a paper result.
It joins the compositional switch from
`futon5/scripts/exotype_by_example.clj` to the calibrated damage-spreading
harness in `mmca-clj/scripts/regime_placement.clj`.

## Question and answer

Does `switch(bored?, propagator, no-op)` enter the complex band when neither
unconditional branch does?

**No. All three policies remain in the ordered band below rule 90 (`8.00`).**
At four-seed resolution the conditional exotype is also not distinguishable
from the unconditional `explore` constituent. Of the three preregistered
outcomes, the result is therefore **(c): the exotype is indistinguishable from
a constituent on this measure**. More narrowly: no additional causal reach
beyond `explore` is resolved here.

The exotype point estimate is above both constituents, so this is not outcome
(b), interpolation. But that elevation is smaller than its seed spread and is
not consistent across seeds. It is not evidence for a new causal regime.

## Construction

The port preserves the operator in `exotype_by_example.clj`:

- `explore`: apply legacy rotate+2 once at every cell;
- `hold`: apply no genotype propagator;
- `exotype`: apply rotate+2 only where the circular phenotype neighbourhood
  `[prev self next]` is uniform (“bored”), otherwise hold.

The legacy positional permutation `[2 3 4 5 6 7 0 1]` is converted through
neighbourhood names into standard Wolfram ordering, yielding the writing
`[6 7 0 2 1 4 3 5]`. This avoids silently changing the operator when crossing
truth-table conventions.

The port changes no other part of the regime harness. In particular, genotype
and phenotype update simultaneously from the old state, as in the source
construction. The deterministic local switch introduces no gate coin. A fired
propagator consumes its ordinary source-neighbourhood draw.

## Protocol

The three rows use exactly the other regime-placement rows' protocol:

- lattice width `L=80`;
- evolve to `t*=60`;
- flip one phenotype bit at each site `0,8,…,72`;
- continue both branches with cloned RNG state;
- measure differing phenotype cells at `dt=59` (`T=120`);
- seeds `0,1,2,3`, ten perturbation sites per seed.

Thus each summary has 40 site observations, but only four independent initial
conditions. Seed means, rather than the 40 correlated sites, are used below to
judge whether a separation clears seed spread.

## Placement

The committed summary artefact is
`mmca-clj/data/regime_placement_summary.tsv`; the complete site-level producer
artefact is `mmca-clj/data/regime_placement.tsv`.

| policy | mean reach | site-level SEM | seed means | seed range | band |
|---|---:|---:|---|---:|---|
| hold | 1.1000 | 0.2019 | 1.00, 0.80, 1.20, 1.40 | 0.80–1.40 | ordered |
| explore | 3.2250 | 0.5158 | 3.00, 3.40, 3.00, 3.50 | 3.00–3.50 | ordered |
| exotype | 4.7000 | 0.4588 | 3.30, 2.90, 5.70, 6.90 | 2.90–6.90 | ordered |

Calibration rows in the same artefact place rule 204 at `1.00`, rule 90 at
`8.00`, and rule 30 at `36.45`. The exotype is `3.30` cells below the ordered /
complex boundary.

The paired exotype-minus-explore differences by seed are
`0.30, -0.50, 2.70, 3.40`: mean `1.475`, SD `1.870`, SEM `0.935`. The mean
separation does **not** exceed either the paired seed spread or the exotype's
between-seed SD (`1.918`). A descriptive 95% paired-t interval is approximately
`[-1.50, 4.45]`, including zero. With only four seeds this is a failure to
resolve an effect, not proof that the two operators are identical.

By contrast, exotype exceeds `hold` in all four seeds. The null concerns whether
the switch adds reach beyond the active constituent, not whether mutation and
no mutation are equivalent.

## Test A: sixteen-seed power check

The identical protocol was rerun without stopping early, extending only the
three exotype-family rows to seeds `0,…,15` (160 site observations per policy).
Between-seed summaries use the 16 seed means and a two-sided Student-t 95%
interval (`df=15`):

| policy | seed-mean mean | between-seed SD | 95% CI | margin from CI upper bound to 8.00 |
|---|---:|---:|---:|---:|
| hold | 1.2063 | 0.4768 | [0.9522, 1.4603] | +6.5397 |
| explore | 3.3813 | 1.3227 | [2.6764, 4.0861] | +3.9139 |
| exotype | 6.7875 | 3.8262 | [4.7487, 8.8263] | -0.8263 |

The projected outcome did **not** occur. Rather than holding near `4.70`, the
exotype mean rose by `2.0875` cells, driven by heterogeneous seed means ranging
from `1.70` to `14.50`. Its point estimate remains in the ordered band, but the
confidence interval now crosses the `8.00` ordered/complex boundary. Thus the
four-seed null is still unresolved: increasing power exposed larger
between-seed heterogeneity instead of excluding a complex-band mean.

The same full producer invocation regenerated all calibration rows. The ten
protected published values—rules 204/90/110/54/30, transport
0.50/0.75/1.00, blend 0.00, and ungated 1.00—were unchanged.

## Reproduction and determinism

From `/home/joe/code/mmca-clj`:

```sh
clojure -M -i scripts/regime_placement.clj > data/regime_placement.tsv
python3 scripts/summarize_reproduction_data.py regime
```

The full command was run twice. Both the complete raw TSV and reduced summary
were byte-identical:

- `data/regime_placement.tsv` SHA-256:
  `beaae4a7c918573734869fc9524e3222c326864c58535f3fc2ab01429fdbae63`
- `data/regime_placement_summary.tsv` SHA-256:
  `38f6df5117195aa89e4136a816d4e7d7d1ad8f387b8913bb4a457130f428e505`

## Conclusion

The conditional composition does not create a complex-band operator on the
paper's causal-reach scale. Its point estimate is higher than both constituents,
but that difference is seed-sensitive and unresolved relative to `explore`.
The honest classification is **(c)** at the present seed count: the switch adds
no demonstrated causal reach on this measure.
