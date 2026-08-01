# TN-baldwin-experiments-status — what the MetaCA Baldwin experiments establish so far

**Status: evidence synthesis, 2026-08-01.** This note updates the Baldwin discussion
in Part III with experiments run after the paper text was drafted. It changes no
preregistered criterion and promotes no pilot to a confirmation study. The overall
answer remains **inconclusive**: several mechanisms have been excluded under their
tested protocols, but neither learning-guided evolution nor genetic assimilation has
been demonstrated in MetaCA.

The evidence is not uniformly inconclusive. Current-state phenotype targeting is a
real causal mechanism; several proposed search repairs returned clean negatives; and
the planted Hinton--Nowlan control passes. What remains unresolved is whether MetaCA
has an evolutionary path by which lifetime rewriting first helps and then leaves an
inherited improvement behind.

Companions:

- [`TN-baldwin-reconstructed.md`](TN-baldwin-reconstructed.md), the historical 2014
  mechanism and replay;
- [`TN-exotype-placement.md`](TN-exotype-placement.md), the Part III causal gate and
  frozen-phenotype control;
- [`TN-baldwin-selection-rewriters.md`](TN-baldwin-selection-rewriters.md), the
  selection, cost, mask, and hold experiments, including their corrections;
- [`TN-part-III-b-baldwin-recovery.md`](TN-part-III-b-baldwin-recovery.md), the
  preregistered plan whose early versions the later work revised;
- the [Baldwin argument library](../../../futon3/library/baldwin/INDEX.md), which
  separates guidance from assimilation and relates the MetaCA mechanism to the
  literature.

## 1. Three claims that must not be collapsed

The word *Baldwin* has referred to three different objects in this project.

1. **The 2014 function named Baldwin.** This is an endogenous mutation policy:
   context matches lead to mutation and mismatches lead to holding. It self-anneals,
   but its name does not establish a Baldwin effect.
2. **Learning-guided evolution.** Lifetime rewriting changes which inherited
   populations evolution discovers. The later `GuidanceWitness` asks whether the
   learning-evolved population is better prepared on both training and held-out
   tasks than a paired no-learning evolutionary control.
3. **Strong genetic assimilation.** Function becomes heritable without continued
   rewriting. `BaldwinWitness` requires a high-function path, declining dependence,
   and a functional static endpoint.

Claim 3 is stronger than Claim 2. Plasticity can guide evolution without disappearing,
and plasticity can disappear for reasons unrelated to learning. The formal split in
[`DarkTower/BaldwinDesign.lean`](../../../mathlib4/DarkTower/BaldwinDesign.lean)
was introduced because the earlier experiments could otherwise be read both ways.

## 2. Evidence ledger

| stage | question | result | standing |
|---|---|---|---|
| Historical replay | Does the 2014 Baldwin default reproduce the published 42/170 attractor? | 0/15 seeds, against 15/15 for blending-mutation | Negative about the historical default, not about Baldwin effects generally |
| Part III gate | Does a phenotype-conditioned switch add causal reach beyond its constituents or a rate-matched blind gate? | Yes for live current-state gates; no for the initial low-resolution exotype comparison | Establishes causal targeting, not evolutionary guidance |
| Frozen control | Is spatially correlated firing sufficient? | No: live exceeded frozen in 7/7 pairs, mean +14.53 cells; every frozen departure was negative | Strong evidence that current phenotype information, not correlation alone, carries the effect |
| Scalar gain selection | Does charging for plasticity cause gain to rise then fall while score is maintained? | No tested cost did so | The scalar landscape is thresholded: below full gain the band score is zero, so partial assimilation has no gradient |
| Read mask | Can dependence be localized per cell? | Apparent decline was invalid: masked cells still rewrote | Superseded measurement |
| Hold mask | Do fixed loci accumulate under selection? | Holds remained far below a mutation-only baseline while function stayed plastic | Holding an unprepared inherited rule is selected against |
| Hold-only battery | Does a static endpoint or selectable local path exist in the implemented search? | Known static rules exist, but a 30-generation static search stayed near reach 1.18; a greedy path accepted one extra hold and then stopped | Endpoint existence is not path existence |
| 2x2 search pilot | Do coupled hold/rule mutation or a fixed initial phenotype unblock assimilation? | No witness in any arm; classifier `:no-tested-repair` | Negative for these two repairs in one pilot seed |
| Guidance pilot | Does evolution with rewriting produce a population that learns sooner and generalises better? | No `GuidanceWitness`; classifier `:neither-certified` | Inconclusive because the preregistered early-budget score censored every arm to zero, and the full-budget held-out contrast did not favour learning |

## 3. What Part III established before selection

The earliest three-policy placement was deliberately negative-capable. `hold` reached
`1.10`, unconditional `explore` `3.225`, and the conditional exotype `4.70`; all
remained below the complex-band boundary at rule 90 (`8.00`). With four independent
initial conditions the exotype was not resolved from `explore`.

The larger causal battery gave a sharper result. Rate-matched random gates behaved
like a gain dial. Conditioned gates exceeded them by 3--22 cells, non-monotonically,
and the best gate reached `38.23` while firing on only 63% of opportunities. Wider
predicates explained departure better than predicate strictness.

That result still admitted a geometric explanation: neighbourhood predicates produce
spatially correlated firing. The frozen-phenotype control retained the same predicate,
width, rate, and correlated field while removing access to consequences after the
perturbation. Live exceeded frozen in all seven comparisons; mean live departure was
`+12.10`, whereas mean frozen departure was `-2.76`. The gate therefore exploits
current causal information.

This matters, but it is not yet Baldwinian evolution. The Part III river writes the
genotype from phenotype information directly, making it Lamarckian at that interface.
It demonstrates that plastic access is useful enough to create a selection problem;
it does not demonstrate that selection turns that access into inherited preparedness.

## 4. Why the first assimilation searches failed

### 4.1 Scalar gain had no partial route

In the gain dial, `gamma = 0.875` reached `7.375`, below the complex-band threshold,
while `gamma = 1` reached `12.387`. The two-sided band score was exactly zero at every
level below full gain. Saving one eighth of the plasticity cost therefore sacrificed
the entire functional score. Low costs retained full plasticity; high costs prevented
plasticity from establishing. No gradual retreat was selectable.

This is evidence about a representation: a scalar gain cannot express partial
assimilation on this thresholded landscape. It is not evidence that Baldwin effects
cannot occur in MetaCA.

### 4.2 The first per-cell mask did not hold anything fixed

The read mask selected live versus frozen phenotype input, but rewriting remained
globally enabled. A cell labelled non-plastic still changed its rule from stale
context. Its apparent dependence decline therefore measured the wrong intervention
and is withdrawn.

### 4.3 A real hold exposes an unprepared allele

The corrected hold mask permanently retains an inherited rule. In two early runs,
selected populations held only `0.025` and `0.068` of loci, below the corrected
mutation-only expectation of `0.1858`. An audit also found that the HGT arm had failed
to transfer the hold mask with the field, so its HGT interpretation was withdrawn.

The cleaner paid hold-only battery made the mechanism visible. At generation 29:

| arm | mean reach | dependence | held |
|---|---:|---:|---:|
| mutation-only null | — | — | 0.2219 |
| selected, cost 0.05 | 12.8819 | 0.9844 | 0.0156 |
| selected, cost 2.0 | 12.8167 | 0.9641 | 0.0359 |
| all-held static search | 1.1778 | 0 | 1.0000 |

Selection preserves function while suppressing holds by roughly six- to fourteen-fold
relative to the empirical null. The static search did not find the known good fixed
fields in 30 generations. A greedy joint rule/hold probe did find one selectable step
(locus 60, rule 154), improving fitness from `0.946794` to `0.947419` while retaining
the band score; it found no second step.

The most economical reading is a coordination gap. A hold mutation simultaneously
removes rewriting and reveals whatever rule happens to be present. Hinton--Nowlan's
fixed allele is already a candidate answer; MetaCA's held rule is generally an
unprepared random replacement. A high-function static endpoint exists, but ordinary
mutation has no resolved path to it.

## 5. Preregistered search-operator pilot

The next pilot crossed two proposed repairs:

- independent versus coupled plastic-to-held mutation, where coupling also draws an
  uninformed uniform rule allele;
- variable versus preregistered fixed initial phenotype.

The no-selection null and four selected arms used the same production evaluation shape.
All selected arms maintained substantially more reach than neutral mutation, but none
produced the strict witness:

| arm | reach | dependence | held |
|---|---:|---:|---:|
| neutral mutation | 5.2931 | 0.8016 | 0.1984 |
| independent / variable | 12.4389 | 0.9755 | 0.0245 |
| coupled / variable | 12.4583 | 0.9719 | 0.0281 |
| independent / fixed | 12.8056 | 0.9844 | 0.0156 |
| coupled / fixed | 12.9972 | 0.9609 | 0.0391 |

The preregistered classifier returned `:no-tested-repair`. Coupling increased proposal
supply but did not provide a prepared allele; fixing one initial phenotype did not make
the evolutionary target sufficiently stationary. The result is a clean negative for
those repairs in pilot seed `20260730`, not a confirmation study. The registered
confirmation seeds have not been run.

Source: [pilot result](../../../mmca-clj/data/baldwin-runs/baldwin-search-pilot-20260731-200346-r2/result.edn)
and [preregistration](../../../mathlib4/DarkTower/BaldwinSearchPreregistration.lean).

## 6. Preregistered guidance pilot

The guidance experiment asked the logically prior question. Three populations began
from a common inherited population: mutation-only, selection with lifetime rewriting
disabled, and selection with rewriting enabled. The two selected endpoints were then
evaluated on fixed training and held-out tasks at learning budgets
`[0, 4, 16, 64, 120]`.

All apparatus checks passed: common initial population, valid population paths,
configuration, manifests, treatment separation, and paired evaluation tape. Both
selected populations were functional at budget 120. The preregistered witness still
failed:

| partition | population | reach at budget 0 | reach at 64 | reach at 120 | preparedness over 0/4/16/64 |
|---|---|---:|---:|---:|---:|
| training | no-learning evolution | 1.391 | 2.266 | 11.500 | 0.0 |
| training | learning evolution | 1.406 | 2.419 | **12.763** | 0.0 |
| held-out | no-learning evolution | **1.294** | 2.401 | **11.643** | 0.0 |
| held-out | learning evolution | 1.182 | **2.708** | 11.026 | 0.0 |

The outcome was `:neither-certified`, with failures
`:no-training-preparedness-advantage` and
`:no-held-out-preparedness-advantage`.

The result is inconclusive for a precise reason. Preparedness was defined as mean
**band score** at the first four budgets. Every mean reach at those budgets stayed
below the complex-band boundary, so all eight component scores were exactly zero.
The readout censored small differences rather than establishing equal learning curves.
At full budget, learning evolution was better on training tasks but worse on held-out
tasks, so the uncensored endpoint also does not support a general guidance claim.

This metric cannot be changed after seeing the pilot. A new preregistration could use
a continuous early-performance or time-to-threshold measure, while retaining the band
criterion separately for functional endpoints.

Source: [recovered result](../../../mmca-clj/data/baldwin-runs/baldwin-guidance-pilot-20260801-135800-r2.reanalysis/result.recovered.edn)
and [preregistration](../../../mathlib4/DarkTower/BaldwinGuidancePreregistration.lean).

## 7. Current synthesis

The experiments now constrain the mechanism more than Part III's original discussion:

1. **Plastic access is genuinely useful.** Live phenotype targeting creates causal
   reach that rate, width, and a frozen correlated field do not explain.
2. **Useful plasticity remains load-bearing in the tested evolutionary runs.** Selected
   populations retain 96--98% dependence and suppress random holds.
3. **A destination exists but the implemented mutation operator does not provide a
   path.** Good fixed rules are known; blind static evolution and random hold mutations
   do not find them at the tested scale.
4. **More proposals are not enough.** Coupled hold-plus-random-rule mutation did not
   differ qualitatively from independent mutation.
5. **One fixed initial phenotype is not enough.** It did not unlock assimilation, and
   the guidance pilot's held-out results show why stationarity and generalisation must
   be treated explicitly.
6. **Guidance has not been tested sensitively enough at low budgets.** The pilot's
   preparedness statistic was zero-inflated by construction at the observed scale.
7. **The apparatus can show a Baldwin effect.** The planted Hinton--Nowlan positive
   control passed. The open issue is the MetaCA representation and evolutionary path,
   not whether the experiment code can ever emit a positive result.

Thus “inconclusive” does not mean “nothing happened.” It means the evidence localizes
the missing bridge between adaptive rewriting and inherited preparedness without yet
showing whether that bridge can be built in MetaCA.

## 8. Open questions

### Mechanism

1. **Where is the heritable shadow of a plastic action?** Can a latent rule allele be
   prepared while rewriting remains active, then exposed by holding, rather than drawn
   randomly at the moment plasticity is removed?
2. **Is indifference spatially localized or temporally scattered?** For each locus,
   measure the fraction of steps at which live and frozen reads choose the same rule.
   A flat distribution would explain why no locus can be assimilated independently;
   a bimodal distribution would reveal genuine hold candidates.
3. **Can genetic search find static fields at all under the exact production
   protocol?** Known fixed endpoints and a failed 30-generation static search are not
   contradictory; they measure existence and reachability respectively.
4. **Is the needed mutation intrinsically multi-locus?** One selectable rule/hold step
   exists, but the greedy path stops at two held loci. Does performance require a
   coordinated block, developmental encoding, or recombination that transfers rules
   with their plastic/fixed state?
5. **What is the stationary target?** Is preparedness tied to one initial phenotype,
   a distribution of environments, or a family of tasks? What inherited structure can
   improve across held-out environments rather than memorize a training field?

### Measurement

6. **What is a sensitive preregistered learnability metric?** Candidates include area
   under the raw learning curve, time to a fixed reach threshold, or reach at a fixed
   small budget. Functional band membership should remain a separate endpoint gate.
7. **How large is the selection signal for one assimilated locus relative to evaluation
   noise?** At cost `0.05`, one of 80 holds saves only about `0.0006` before accounting
   for functional changes.
8. **Do the pilot effects replicate?** Neither the 2x2 search nor the guidance result
   has been run on its preregistered confirmation seeds.
9. **Why does the learning-evolved population improve full-budget training reach but
   lose full-budget held-out reach?** Is this ordinary pilot variation, evolutionary
   overfitting, or a structural trade-off caused by rewriting?

### Scope

10. **Should MetaCA aim first for guidance rather than disappearance of plasticity?**
    Hinton--Nowlan establishes a selective neighbourhood; complete assimilation is a
    stronger outcome. A stable dependence on rewriting could coexist with inherited
    improvements in learning speed.
11. **Would a different representation expose the bridge?** Recent Baldwinian systems
    often evolve morphology, initialization, or learning biases rather than replacing
    every learned action with a fixed allele. MetaCA may need an evolvable prior over
    rewriting, not only a binary hold mask.
12. **When would a negative become decisive?** A stronger negative would require a
    sensitive guidance metric, replicated paired arms, demonstrated reachability of
    static endpoints under the same encoding, and a mutation operator capable of
    preparing before fixing. The current pilots do not jointly meet those conditions.

## 9. Immediate next experiment

Do not simply rerun the current pilot at greater cost. First preregister two changes:

1. a continuous guidance readout which is nonzero on the smoke and positive-control
   learning curves; and
2. a **prepare-then-fix** operator in which each plastic locus carries a heritable latent
   rule that can mutate and be selected while rewriting is still active.

Cross that operator with the existing no-learning evolutionary control, retain paired
training/held-out tasks, and preserve the strict `BaldwinWitness` only as the stronger
secondary endpoint. That experiment would test the missing mechanism identified by the
current evidence rather than spending more computation on a path already shown to be
poorly supplied.
