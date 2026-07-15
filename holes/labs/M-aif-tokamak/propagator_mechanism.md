# Propagator mechanism: semantic permutation test

## Result

**Negative.** None of the seven tested properties predicts live/dead across the
three anchors and ten predeclared held-out permutations.  The one candidate fitted
to the anchors scored **4/10 held out**.  Neighborhood semantics therefore matters
enough that abstract cycle structure is inadequate, but the tested semantic
summaries do not explain the regime.

This test ran the original 2014 Elisp through `scripts/elisp-harness/run.el`; it did
not reimplement the dynamics.  Raw results are in
`data/propagator-mechanism-results.el` and the reproducible driver is
`scripts/propagator_mechanism.el`.

## Predeclared test

The neighborhood order was exactly:

```text
index:          0    1    2    3    4    5    6    7
neighborhood: 000  001  010  100  011  101  110  111
```

The candidate property, chosen from the three anchors before held-out dynamics
were run, was:

> P(sigma): at most three of the eight edges `k -> sigma(k)` preserve
> neighborhood Hamming weight, and `000` and `111` are in different orbits.

P predicted **live** for the three coordinate flips and **dead** for mirror,
complement, both neighborhood rotations, and the three XOR maps.  These ten calls
are constants beside the permutations in the driver, before any call to
`run-propagator`.

`LIVES` below means all four seeds still change at generation 120.  `DIES` means
at least one seed stops earlier.  Distinct rules remain a separate measurement:
survival with 1–4 rules is not the target 20–35-rule structured regime.

## Results

All runs used seeds 0–3, width 60, and 120 generations.

| case | held out | predicted | death by seed | mean final rules | mean activity | result |
|---|---:|---|---|---:|---:|---|
| rotate +2 anchor | no | live | 120,120,120,120 | 30.25 | 2099.50 | LIVES |
| two 4-cycles anchor | no | dead | 74,40,29,33 | 1.00 | 429.25 | DIES |
| 3+5-cycles anchor | no | live | 120,120,120,120 | 26.25 | 1604.00 | LIVES |
| flip left bit | yes | live | 120,120,120,120 | 1.25 | 3467.00 | LIVES |
| flip centre bit | yes | live | 120,120,120,120 | 4.00 | 787.00 | LIVES |
| flip right bit | yes | live | 120,120,120,120 | 1.25 | 3359.25 | LIVES |
| left-right mirror | yes | dead | 120,120,120,120 | 39.25 | 2557.25 | **LIVES** |
| complement neighborhood | yes | dead | 20,120,15,120 | 3.75 | 423.25 | DIES |
| rotate neighborhood left | yes | dead | 120,120,120,120 | 33.25 | 1754.25 | **LIVES** |
| rotate neighborhood right | yes | dead | 120,120,120,120 | 41.75 | 1743.75 | **LIVES** |
| centre XOR left | yes | dead | 120,120,120,120 | 21.50 | 1391.25 | **LIVES** |
| centre XOR right | yes | dead | 120,120,120,120 | 24.75 | 1903.50 | **LIVES** |
| right XOR left | yes | dead | 120,120,120,120 | 17.00 | 2571.75 | **LIVES** |

The proposed P gets only flip-left, flip-centre, flip-right, and complement right:
**4/10**.  It is falsified.

## Properties tested

| property | verdict |
|---|---|
| abstract cycle type | Refuted by the two 4-cycle anchors. |
| orbit count and orbit lengths | Refuted by the anchors, as previously reported. |
| edge Hamming-distance histogram | Refuted directly: both 4-cycle anchors have histogram `(d0,d1,d2,d3) = (0,4,4,0)` and opposite outcomes. |
| number of Hamming-weight-preserving edges | The predeclared P based on this scored 4/10. |
| `000` and `111` in the same orbit | They are separate for both outcomes; complement joins them but dies only in two of four seeds. |
| commutation with left-right mirror | Both commuting and non-commuting permutations survive; it does not separate the outcomes. |
| semantically natural neighborhood maps | Coordinate flips, mirror, rotations, complement, and XOR maps span collapse, broad survival, and mixed death. Membership in this family is not sufficient. |

This does **not** refute the full semantic hypothesis.  It refutes the proposed
coarse semantic summaries.  Exact identities of the coupled neighborhoods remain
a plausible mechanism, but the present sample does not support a property that can
be stated without post-hoc fitting.

## Isolated operator

For each permutation, 32 deterministic random initial bytes received 400 isolated
propagator updates with inversion and no blending.  The artifact records every
distinct final byte observed.

The decisive negative is already in the anchor pair:

| permutation | isolated final states | count | coupled outcome |
|---|---|---:|---|
| rotate +2 | `00110011`, `01100110`, `10011001`, `11001100` | 4 | LIVES, 30.25 rules |
| two 4-cycles | `01010101`, `01011010`, `10100101`, `10101010` | 4 | DIES, 1.00 rule |

Both even-cycle operators reach four absorbing constraint solutions, yet their
coupled dynamics are opposite.  Isolated attractor cardinality therefore does not
predict the regime.  The exact attractor bytes differ, so their semantic content
could still matter.  The live 3+5-cycle anchor has odd cycles, no globally
consistent inverted constraint assignment, and 28 sampled final bytes after 400
updates; a small absorbing set is not necessary for survival either.

Across held-out cases, sampled final-set counts range from 13 to 30 while nearly
all survive.  The isolated operator is informative about constraint consistency,
but is not a sufficient regime classifier.

### Addendum: rule-space classification

The proposed rule-space view is useful as an **instrument on the coupled run**, but
it cannot by itself supply the requested propagator-alone property.  The two
opposite-outcome 4-cycle anchors make this an exact result, not a sampling result.

Let `sigma_L = (0 2 4 6)(1 3 5 7)` and
`sigma_D = (0 1 2 3)(4 5 6 7)`.  The relabeling

```text
tau = [0 4 1 5 2 6 3 7]
```

satisfies `sigma_D(tau(k)) = tau(sigma_L(k))`.  Relabeling a byte by `tau` and
relabeling each random update choice `k` by `tau(k)` therefore maps every isolated
trajectory of the live twin to a trajectory of the dead twin.  Their 256-state,
eight-action transition graphs are isomorphic.  Consequently every intrinsic
quantity of the isolated stochastic system is identical up to labels: absorption
probability, absorption-time distribution, transient graph, recurrent classes,
and attractor count.

The driver verifies this exhaustively for all **256 bytes x 8 update choices** and
records `twin-transition-conjugacy-check: t` in the raw artifact.  This explains
why the sampled isolated attractors have the same cardinality despite opposite CA
outcomes.

There is no contradiction with the rule-space image: a cell's rule byte inside the
full MetaCA is also changed by neighbor blending.  Its non-freezing trace measures
the interaction of propagator, semantic bit placement, and lattice coupling.  That
trace may be an excellent diagnostic or a target for a new instrument, but it is
not computed from the isolated propagator alone.  The exact conjugacy result rules
out freeze time or stochastic rule-space class as a standalone `P(sigma)` for this
anchor pair.  A successful mechanism must include how the labelled rule vertices
couple back to neighborhood semantics and blending.

## Reproduction

```sh
cd /home/joe/code/futon5
emacs --batch -l scripts/elisp-harness/run.el \
  -l scripts/propagator_mechanism.el
```

The driver has lexical binding enabled.  It writes the raw artifact to
`data/propagator-mechanism-results.el` (override with `MECHANISM_OUT`).
