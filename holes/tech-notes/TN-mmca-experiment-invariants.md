# TN-mmca-experiment-invariants — what every experiment on this substrate must satisfy

**Status: 2026-07-30, claude-8.** Written after a session in which **nine** designs
or implementations were found to be structurally incapable of answering their
question, most of them only after the run. Joe's assessment is the reason for this
note: the hotfix series did not produce rigour, and the defects that were caught
were caught by luck rather than by method.

Each entry below is stated as an invariant, with the failure that motivated it and
the executable check that would have caught it. These are candidates for the Lean
formalisation Codex is building; nothing here depends on that yet.

**Rule: no long run until the applicable invariants pass.**

---

## I1 — Tape alignment

*Both branches of a damage fork consume identical RNG draws, whatever their gates
decide.*

**Failed three times.** A phenotype-dependent gate that skipped a draw
desynchronised the branches and turned an INTERPOLATES result into a spurious
CREATES an order of magnitude too large. Injecting a heritable genotype left the
RNG un-advanced by 80 draws, so gamma=1 read `11.0083` against the published
`12.3875`.

**Check:** instrument the field-step; give the branches deliberately opposite gate
values; assert the consumed draw vectors are identical at every paired step. Must
be non-vacuous — assert the gates actually differed. `causal_score_test.clj`'s
`fork-tapes-stay-aligned-across-a-phenotype-gate` is the model.

## I2 — Layering fidelity

*Any extension reduces to the published measurement when its new machinery is in
the neutral position.*

**Motivated by:** every extension so far. `update-prob = 1`, nothing held,
all-plastic mask must give `1.2833` at gamma=0 and `12.3875` at gamma=1.

**Check:** a test asserting both, run after every edit. Cheap and it has caught
real breakage.

## I3 — Heritable completeness under transfer

*Every heritable field travels together through recombination.*

**Failed once, fatally.** `hgt` spliced `:field` and `:mask` but not `:hold`,
which was added later. Transferred rules landed under the recipient's unrelated
hold pattern, so the arm could not test the mechanism it existed for. A comment in
the same file claimed the opposite.

**Check:** assert `(keys (hgt a b))` covers every heritable key, and that each
position-indexed key is spliced at the same cut points. Any new heritable field
fails this test until wired.

## I4 — Mutation reachability

*Every gene's full domain is reachable by the mutation operator.*

**Motivated by:** a gene the operator cannot reach looks present and is not.

**Check:** seeded mutation walk from an extreme; assert the reached set EQUALS the
domain, including both endpoints. `known-genome-mutation-roundtrip-covers-every-gene-domain`
is the model.

## I5 — Empirical null, not an analytical one

*Any "below expectation" claim uses a no-selection control running the identical
lifecycle.*

**Failed once.** The mutation-only baseline assumed every member mutates each
generation; survivors are in fact retained unmutated, so the expectation was
`half(1-0.98^t) = 0.1858`, not `half(1-0.96^t) = 0.3045`. The reported effect was
overstated about 1.6x. "Neutral drift" was also the wrong name — symmetric mutation
drives the expectation, not drift.

**Check:** a `--neutral` mode that runs the identical lifecycle with fitness
replaced by a constant. That is the null. Do not derive it on paper.

## I6 — Landscape non-degeneracy

*The quantity under selection must produce a non-constant score across its domain,
at the population's operating point.*

**Failed twice.** Band-score was exactly zero for the whole population from
generation 0, so ranking was driven entirely by the cost term and arms at different
costs produced byte-identical trajectories. Separately, band-score is exactly zero
for every gamma below 1.0, making gradual retreat geometrically impossible at any
cost below `5.01`.

**Check:** before running, evaluate score across the gene's domain holding the rest
fixed. A flat or single-spike profile means selection cannot act gradually, and the
run cannot answer a gradual-change question.

## I7 — Endpoint existence and reachability

*The target configuration must be constructible in the representation and must
score well.*

**Failed once, in the claim rather than the code.** "A random all-held field scores
1.2875" was read as "no high-function static endpoint exists". It shows only that a
*random* endpoint is bad. A uniform rule-90 field under the constructions' own
dynamics scores `10.00`, inside the complex band.

**Check:** construct the assimilated configuration explicitly and measure it, under
the *production* fitness protocol rather than a neighbouring one.

## I8 — Dynamical consistency between calibration and constructions

*The scale and the things placed on it must use the same dynamics.*

**Failed once, and it reaches the paper.** The ECA calibration uses `eca-row` with
**periodic** boundaries; every construction row uses `c/phenotype-step` with
**zero** boundaries. At matched initial conditions the difference is large:

| rule | periodic | zero-boundary |
|---|--:|--:|
| 90 | 8.00 | 10.00 |
| 54 | 18.30 | 12.70 |
| 110 | 16.68 | 13.08 |
| 30 | 36.45 | **18.40** |

Rule 30 halves, which matters because the chaotic threshold of `22` was derived
from it.

**Check:** assert `eca-row rule p` equals `phenotype-step (uniform rule) p` for
several rules and a random `p`. It currently differs in 1--2 cells, the two edges.

## I9 — Treatments must separate

*Different parameter values must produce different trajectories.*

**Failed once.** Four cost arms produced byte-identical gamma, reach and update
columns. That is diagnostic of I6, not of a robust result, and it was nearly
reported as one.

**Check:** assert arms differ in at least one non-score column; if they do not,
the treatment is not reaching selection.

## I10 — Comments are checked against code

**Failed once.** The `hgt` comment claimed field and hold transfer together while
the code spliced field and mask. The comment was written in the same commit as the
code it misdescribed.

**Check:** where a comment states an invariant, that invariant gets a test. A
comment asserting behaviour with no test is a claim, not documentation.

---

## Status of the current implementation

| invariant | state |
|---|---|
| I1 tape alignment | holds; tested |
| I2 layering fidelity | holds; tested (1.2833 / 12.3875) |
| I3 heritable completeness | **fixed this turn**; needs a test |
| I4 mutation reachability | untested for the mmca-clj genome |
| I5 empirical null | **not implemented** |
| I6 landscape non-degeneracy | **not implemented** |
| I7 endpoint existence | passes for rule 90 at 10.00; needs production protocol |
| I8 calibration consistency | **fails** — periodic vs zero boundaries |
| I9 treatments separate | ad hoc |
| I10 comment/code agreement | ad hoc |

I5, I6 and I8 are the gaps that would most change what we can claim.
