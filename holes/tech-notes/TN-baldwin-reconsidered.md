# TN-baldwin-reconsidered — where Baldwin proper would live in the tower of types

**Status: jotted, not built. 2026-07-28, claude-8.** Written to park a line of thought so
the current paper can be finished with focus. Nothing here is a result; §4 is the only part
carrying measurements, and those are already recorded in
[`TN-coupling-gain.md`](TN-coupling-gain.md). Read this as a design note with a prediction
attached.

Prior art it depends on: [`TN-baldwin-reconstructed.md`](TN-baldwin-reconstructed.md)
(what the 2014 Baldwin function actually is), `STRATEGY.md` §Architecture (the tower),
`README.md` §Xenotypes, and `reports/strategy-exotype-eoc-search.md`.

---

## 1. The tower, as already defined

From `STRATEGY.md`, unchanged and not reinvented here:

| layer | width | what it is |
|---|---|---|
| **Pattern** | textual | IF / HOWEVER / THEN / BECAUSE |
| **Exotype** | 8-bit | a *local evaluation regime* — "the rule a specific cell uses: rotation, match-threshold, update-prob, mix-mode" |
| **Xenotype** | 36-bit | the *global physics* — which exotypes apply where and when (8 IF + 8 HOWEVER + 8 THEN + 8 BECAUSE + 4 phenotype) |
| **Domain** | — | the xenotype instantiated; for us, an MMCA spacetime field |

`README.md` adds two things that matter here. First, the exotype is "the local heredity
signal (**neighbor sigils + phenotype context**)". Second, the loop is explicitly triadic:
"exotypes condition how genotypes update, phenotypes expose mismatches, and selection
rewards exotypes that induce more informative genotype structure."

Joe's formulation — *the evolution of genotypes requires an exotype, and the evolution of
exotypes requires a xenotype* — is the tower's own logic. Each layer supplies the
selection environment for the one below.

## 2. The recent experiments were an exotype sweep

This is the connection worth recording, because it was not obvious while doing the work.

Every dial in the recent experiments is an exotype in the `STRATEGY.md` sense — a local
evaluation regime, varying exactly the things the definition lists:

| experiment | exotype parameter |
|---|---|
| blend strength 0.00–1.00 | mix-mode |
| async refuges 0.25–0.75 | update-prob |
| braids, niches | which writing applies where/when |
| conservative transport rate | update-prob, phenotype-gated |
| the river's γ | how much *phenotype context* the local regime reads |

So the finding of `TN-coupling-gain.md` restates cleanly in the tower's vocabulary:

> **Sweeping exotype parameters that do not include the phenotype-context coupling leaves
> the genotype layer in the ordered band. The coupling is the exotype parameter that
> matters; the rest are silent.**

And the river is the exotype that reads the phenotype context maximally — which is why
`TN-baldwin-reconstructed` was right to identify it with the 2014 Baldwin function. The
four-bit context that note reads out of the elisp *is* "neighbor sigils + phenotype
context". Same object, three descriptions.

**We therefore already have a stock of exotypes**, characterised and placed on a calibrated
scale: the structured high-diversity fields, both suppression mechanisms, the braids and
niches, the conservative transport family, and the river at every γ.

## 3. What is actually missing

Not the machinery. `src/futon5/mmca/` has `exotype.clj`, `genoevolve.clj`, `exoevolve.clj`
("short-horizon exotype evolution loop") and `xenoevolve.clj` ("slow outer loop that
evolves xenotypes against exotype runs"), with a runnable entry point. The xenotype layer
already scores "edge-of-chaos behavior, penalizing degenerate regimes (stasis/confetti)" —
which is, in different words, the same two-sided criterion the damage-spreading scale
measures, with stasis below rule 90 and confetti above rule 30.

What is missing is narrower, and worth stating precisely so it does not get overstated:

1. **The two lines are not wired together.** The tower and its evolution loops live in
   `futon5`'s cyber-mmca line; the recent measurements live in `mmca-clj` and know nothing
   about them. The exotypes of §2 have never been handed to `exoevolve`.
2. **No exotype in the evolvable population currently carries a coupling-gain gene.** γ is
   a parameter I added by hand to study one construction. For the Baldwin question it would
   need to be a heritable field of the exotype, mutable like any other.
3. **The fitness is a proxy.** The xenotype scorer rewards edge-of-chaos *appearance*. The
   causal measure is a sharper instrument for the same intent, and would be the natural
   substitution — with the caveat that it costs a paired run per evaluation.

## 4. Where Baldwin proper lives, and what we predict

Baldwin proper needs plasticity, selection, and assimilation *without* direct transfer.
The tower supplies each at a different level:

- **Plasticity** is the exotype reading the phenotype context — γ > 0.
- **Selection** is the xenotype layer scoring exotypes over full runs.
- **Assimilation** would be exotypes achieving the same score with γ driven down: what was
  achieved by reading the phenotype comes to be achieved without it.

So the experiment is well-posed within machinery that already exists: make γ heritable in
the exotype, select at the xenotype layer on the causal measure, and watch γ's trajectory.
**The Baldwin signature is γ rising and then falling while score is maintained.**

**Our prediction is that it will not fall.** Three measurements, all in
`TN-coupling-gain.md`:

1. The gain curve is **convex** — 45% of the river's span arrives in the last eighth of γ,
   so a partially assimilated exotype collects almost nothing and selection has no gradient
   to climb.
2. The read is **determinative, not a tiebreak** — the live context selects a different
   rule than a frozen one in **73.1%** of cell-steps, against ~75% for an uninformative
   re-selection. There is no common case for a blind exotype to encode.
3. Sixteen blind exotypes were tried by hand and none matched the river (max 8.15 against
   12.97).

If that holds, this is a system where **Baldwin cannot complete in principle**: the
information plasticity supplies is irreducibly dynamic, not a fixed target waiting to be
encoded. Hinton & Nowlan's genome *can* express the answer; here the answer changes every
step.

**One way the prediction could fail, which §7b of the companion note missed.** Assimilation
need not route through γ. The exotype has other genes — match-threshold, mix-mode, rotation
— and selection might find a regime that achieves the reach by some other means, leaving γ
free to fall. Point 3 above is evidence against that, but it is a *hand* search over
sixteen constructions, and xenotype-level selection is precisely the automated version of
the same search. **That is the strongest reason to run it: it would replace the weakest
evidence in the current argument with something systematic.**

## 5. Scope, deliberately

This is parked. The current paper's claim — causal reach is graded and monotone in coupling
gain — is complete without any of it, and holds whether the coupling is Lamarckian or
Baldwinian. Nothing above changes a number in the paper or the supplement.

The order, when it resumes: wire one exotype family from §2 into `exoevolve` as a
smoke test; then add γ as a heritable gene; then substitute the causal measure for the
proxy fitness; then run and watch γ. The first step is small and would tell us quickly
whether the two codebases can be made to talk at all.

## 6. Files

- Measurements and the gain dials: `TN-coupling-gain.md`; code in `mmca-clj`
  (`scripts/river_gain.clj`, `scripts/regime_placement.clj`, `scripts/coupling_load.clj`).
- The 2014 Baldwin function reconstructed: `TN-baldwin-reconstructed.md`.
- The tower: `STRATEGY.md`, `README.md` §§Exotypes/Xenotypes.
- Machinery: `src/futon5/mmca/{exotype,genoevolve,exoevolve,xenoevolve}.clj`.
- Nearest prior search: `reports/strategy-exotype-eoc-search.md`.
