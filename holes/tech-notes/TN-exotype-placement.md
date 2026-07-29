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

## Correction: a tape-desynchronisation confound (2026-07-29)

Tests A-C below were first reported from a producer in which **the gate changed
how much randomness was consumed**. `c/propagate` draws internally, and the
exotype's non-firing branch returned the rule without drawing; the transport
switch's `and` short-circuited past its `rand-double`. Which cells fire depends
on the phenotype, and the perturbation changes the phenotype -- so the two damage
branches consumed different numbers of draws, their tapes desynchronised, and
the divergence that followed was RNG artefact rather than causal effect.

The confound aligned exactly with the treatment: `explore` fires everywhere and
`hold` nowhere, so neither can desynchronise, while both conditional
constructions could. `river_gain.clj` states the required discipline explicitly
-- "the gate coins come from a SEPARATE stream, re-seeded identically in both
branches of the fork, so the gate itself never injects divergence" -- and the
port did not carry it over.

Both constructions now draw unconditionally and use the draw only when firing.
The controls are unchanged to four decimal places, which is the signature of the
bug; the conditional rows collapse:

| policy | as first reported | tape-aligned | change |
|---|---:|---:|---:|
| hold | 1.2063 | 1.2063 | 0.0000 |
| explore | 3.3813 | 3.3813 | 0.0000 |
| exotype | 6.7875 | **1.4438** | -5.3438 |
| switch transport 1.00/hold | 10.4625 | **3.5063** | -6.9563 |

**Every headline below is withdrawn.** Corrected:

- **Test A.** Exotype is `1.4438`, 95% CI `[1.0591, 1.8284]` -- firmly ordered and
  now *below* `explore`. The four-seed null is not merely unresolved-but-elevated;
  it is resolved, downward. Between-seed SD falls from `3.8262` to `0.7220`: the
  "heterogeneity" was desynchronisation noise.
- **Test B.** Excess reach over `hold` per unit firing is `1.8926` for exotype
  against `2.1750` for explore -- a ratio of **0.87x**, not `20.449x`.
  Conditioning buys no efficiency. (Raw reach over `f` still favours exotype
  `3.40x`, but that statistic flatters any low-duty-cycle construction, since
  `hold` scores `1.2063` while never firing at all.)
- **Test C.** Observed `3.5063` against the preregistered prediction
  `f x transport + (1-f) x hold = 3.3981`: a difference of `+0.108`, and a 95%
  CI of `[2.7914, 4.2211]` wholly inside the ordered band. The verdict is
  **INTERPOLATES**, not CREATES.

So the original (c) null stands and now generalises: across these constituents,
conditional composition interpolates rather than creating a new regime. The
positive control was the right experiment; it simply answered the other way once
the instrument was fixed.

Regression: the nine protected published values are unchanged. The producer
rerun is byte-identical (`a2255fb8957662cf...`). clj-kondo 6 errors / 41
warnings (baseline); 35 tests, 0 failures.

The sections below are retained as first written, for the record. Their numbers
are superseded by this correction, and the SHA-256 values quoted in their
determinism section were already stale before it.

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

## Test B: measured duty cycle

The producer was instrumented without adding any RNG draws. It counts each
genotype-update opportunity and whether the selected exotype policy fires,
including the shared trajectory to `t*=60` and both cloned trajectories after
each perturbation. Across the 16 seeds, each policy had `1,587,200`
opportunities:

| policy | fired | firing fraction | mean reach | reach / firing fraction |
|---|---:|---:|---:|---:|
| hold | 0 | 0.000000 | 1.2063 | undefined |
| explore | 1,587,200 | 1.000000 | 3.3813 | 3.3813 |
| exotype | 199,177 | 0.125490 | 6.7875 | 54.0882 |

The structured phenotype field therefore makes the uniform-neighbourhood gate
fire only `12.5490%` of the time, about half the `25%` random-field estimate.
Nevertheless, raw reach per unit firing is `15.996×` higher for exotype than
for unconditional explore. Subtracting the hold baseline gives the same
qualitative result: `(6.7875-1.2063)/0.125490 = 44.4756` excess-reach units per
firing fraction, versus `3.3813-1.2063 = 2.1750` for explore, a `20.449×`
ratio. Conditioning is therefore several times more efficient per actual
firing; this is a positive mechanism result even though Test A does not resolve
the regime-boundary question.

## Test C: reachability with boundary-straddling constituents

The decisive positive control replaces the weak active constituent by the
existing chaotic `transport $1.00$` construction while retaining hold:
`switch(bored?, transport-1.00, hold)`. At each Margolus transport-pair
opportunity, the same uniform phenotype-neighbourhood predicate selects either
the unchanged transport branch or no update. The row uses the same 16 seeds and
the same damage protocol as Tests A and B.

The selector fired `71,086` times in `783,840` pair opportunities, a measured
fraction `f=0.090689`. Its seed-mean reach was `10.4625` (between-seed SD
`4.0317`, 95% t interval `[8.3142, 12.6108]`). It therefore lands in the
**complex band**, where neither constituent sits: hold is ordered at `1.2063`,
while transport 1.00 is chaotic at `25.3750`.

The preregistered interpolation check strongly fails:

```text
f × transport + (1-f) × hold
= 0.090689 × 25.3750 + 0.909311 × 1.2063
= 3.3981 predicted

10.4625 observed  (observed - predicted = +7.0644)
```

The result is therefore **CREATES**, not INTERPOLATES. Conditional composition
can create a regime occupied by neither constituent, so the exotype space is
live and worth searching. The original rotate+2/hold result concerns weak
constituents and cannot be generalized into a structural impossibility claim.

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

The original rotate+2/hold composition has a seed-sensitive ordered-band point
estimate and remains unresolved relative to the `8.00` boundary at 16 seeds.
It is nevertheless highly efficient per actual firing. More decisively, the
boundary-straddling transport/hold positive control creates a complex-band
operator far above its linear-interpolation prediction. The exotype space is
therefore demonstrably live; the original null reflects its chosen weak
constituents, not a general limitation of conditional composition.
