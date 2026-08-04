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

- [`TN-baldwin-experiment-guidance.md`](TN-baldwin-experiment-guidance.md), what the
  revised argument library now requires of a preregistration, and the suggested
  experiment order — start there if you are building the next round;
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

## Review

**Status: review of §§1--9, 2026-08-01, claude (Fable 5). No new runs.** This section
adds no measurement of its own. It reports one experiment the note records as pending
which has in fact been run, one construct-validity defect in the guidance pilot, and one
mechanism reading which — if correct — subsumes several of the open questions in §8.
Everything below is either a citation of committed code and data, or an explicitly
labelled prediction.

Checked: §§1--9 of this note and the three companion notes; `mmca-clj`
`src/mmca/baldwin_selection.clj`, `src/mmca/baldwin_guidance.clj`,
`src/mmca/hinton_nowlan.clj`, `src/mmca/core.clj`; the recovered guidance result EDN;
the 2×2 search `result.edn`; `data/baldwin/indifference_map_fixedgeno.tsv` and the
commits that produced it.

### R1. The §8 Q2 indifference measurement has been run, and it answers the question

§8 open question 2 and `TN-baldwin-selection-rewriters.md` §5.5 both record the per-cell
live-versus-frozen agreement measurement as outstanding. It was run on 2026-07-30
(`mmca-clj/scripts/indifference_map.clj`), and corrected in commit `1081503` after the
first version drew a fresh genotype per seed and so compared different rules in the same
cell. The corrected artefact is `mmca-clj/data/baldwin/indifference_map_fixedgeno.tsv`:
one fixed field, varying only `p0`, which is what a genome in the selection loop actually
faces.

| quantity | value |
|---|---|
| per-cell agreement, distribution | unimodal, mean `0.290`, sd `0.089` |
| cells above `0.6` agreement | **0** |
| within-run lag-1 spatial autocorrelation | `+0.422` |
| cross-seed correlation of the per-cell pattern | `-0.010`, `-0.060`, `+0.111` |

The answer is neither of the two options §8 Q2 poses. The distribution is not flat-and-
scattered, and it is not bimodal-with-hold-candidates. Indifference **is** spatially
structured — but the structure is a function of the initial phenotype, not of the genome,
and it does not survive a change of `p0`. Since a genome is scored across seeds, a
per-cell hold mask cannot encode a pattern that is redrawn in every universe the genome
meets.

This should be read as a structural negative for **per-cell** assimilation on this
substrate, measured rather than inferred. It also gives a single common cause for §7's
items 3 and 4 and for the withdrawn HGT result: recombination cannot assemble stable
assimilable loci when no locus is stably assimilable.

**Ledger consequence.** §2's row for the read mask is marked "superseded measurement" and
§8 Q2 is marked open; both should now point at this artefact instead.

### R2. Fixing `p0` did not make the target stationary, and there is a candidate reason

Commit `1081503` put a prediction on record: "under fixed p0, mean-plastic should fall
well below 0.96." The 2×2 pilot's fixed-`p0` arms (§5) did not show this — dependence
`0.9844` and `0.9609`, held fractions `0.0156` and `0.0391`, both still far below the
`0.1858` mutation-only expectation. Holding remained selected against.

A candidate explanation, from the code rather than from the result: **fixing `p0` removes
only one of two sources of cross-lifetime variation.** In `gain-genotype-step`
(`baldwin_selection.clj:102`) every cell draws `source (.nextInt random c/bit-count)` at
every step, and `propagate-at` (`core.clj:92-103`) uses it to write one *randomly chosen*
bit of the combined rule. The rewrite is therefore a deterministic combine
(`original-river-combine-rule`) plus a random single-bit perturbation, and the `random`
stream is seeded per evaluation seed. Two evaluations of the same genome on the same
fixed `p0` still follow different rewrite trajectories.

So the fixed-`p0` arm tested a necessary but not sufficient condition. This is a
hypothesis about why that arm was null, not a finding; R7.1 is the measurement that
would settle it.

### R3. The guidance pilot's learning-budget axis is confounded with the instrument

This is a construct-validity defect, and it is more serious than the zero-inflation
described in §6.

`learning-budget` gates rewriting by **absolute timestep**:
`learning? (< (+ time-offset t) learning-budget)` (`baldwin_selection.clj:130-132`). The
reach protocol perturbs at `t* = 60` and measures divergence at `t* + dt = 119`
(`baldwin_selection.clj:179-204`, constants at `:27`). Therefore:

| budget | rewriting during `[0, t*)` | rewriting during the damage window `[t*, 119]` |
|---:|---|---|
| 0 | none | none |
| 4, 16 | first 4 / 16 steps | none |
| 64 | all 60 steps | **4 of 59 steps** |
| 120 | all 60 steps | all 59 steps |

The first four budgets do not measure a learning curve. Budget 0 is the static-field
condition, and its observed reach (`1.18`--`1.41`) matches the all-held static reach of
`1.1778` in §4.3 and `hold` at `1.10` in §3. The rise from `2.4` at budget 64 to
`11.5`--`12.8` at budget 120 is the measurement window opening from 4 live steps to 59 —
which is the same live-versus-frozen axis §3 already established, not a learning rate.

The curve is also **non-monotone**: mean reach runs `1.391 → 0.914 → 0.836 → 2.266 →
11.500` (no-learning, training) and `1.406 → 0.784 → 0.786 → 2.419 → 12.763` (learning,
training). Budgets 4 and 16 sit *below* budget 0 in every one of the four
population×partition cells. That is what one expects from rewriting the inherited field
for a few steps and then freezing the result: a partially scrambled field is worse than
the untouched inherited one.

**Consequence for §6 and §9.1.** §6 attributes the null to the preparedness statistic
being zero-inflated, and §9 proposes a continuous readout as the fix. That fix is
necessary but not sufficient: a continuous readout on these budgets would still measure
plasticity-during-the-measurement-window rather than learning speed. A revised
preregistration must decouple the two — vary the learning budget over `[0, t*)` while
holding plasticity during `[t*, 119]` identical across arms, or adopt a functional
readout at `t*` that does not require live rewriting to register.

### R4. One directional signal survives, and it is guidance-shaped

Budget 64 is the only budget at which anything is nonzero, and there the preregistered
direction holds in **both** partitions:

| partition | no-learning | learning | difference |
|---|---:|---:|---:|
| training | 2.2656 | 2.4193 | `+0.154` |
| held-out | 2.4010 | 2.7083 | `+0.307` |

At budget 0 there is no consistent advantage (training `1.3906` vs `1.4062`, held-out
`1.2943` vs `1.1823` — opposite signs). That combination — no innate head start, an
advantage appearing only once rewriting runs — is the shape of **guidance**, not
assimilation, and it is what §8 Q10 proposes targeting.

This is one evolution seed, on four budget points of which three are empty, under the
axis defect of R3. It is **not** a result and must not be reported as one. It is a
directional hypothesis with a sign, which is what the confirmation seeds exist to test —
once the axis is fixed.

**Do not spend the registered confirmation seeds yet.** Both preregistrations
(`[20260801, 20260802]` for search, `[20260803, 20260804, 20260805]` for guidance) are
still unspent per §8 Q8. Running them against a confounded budget axis would consume the
preregistration without testing the claim.

### R5. The inherited field carries no selection gradient while rewriting is on

This is the mechanism reading, and if it holds it subsumes §8 Q1, Q4 and Q7 and explains
§5's clean negative.

```clojure
(defn fitness [genome c seeds sites]                    ; baldwin_selection.clj:263-268
  (let [r (:mean (reach genome seeds sites))]
    {:reach r :score (- (band-score r) (* c (plastic-dependence genome)))}))
```

with `plastic-dependence = update-prob · gamma · frac(unheld ∧ unmasked)`
(`:211-234`). Neither term is a function of the inherited rule content:

- the cost term reads only `gamma`, `update-prob`, `mask` and `hold` — the plasticity
  **machinery**. It is exactly flat in `:field`.
- `band-score(reach)` is measured after rewriting has run for the whole lifetime, which
  is precisely the operation that washes out the inherited field's contribution.

So while plasticity is active, `:field` is close to a **neutral trait**. It random-walks
under uniform re-draws at rate `field-rate` (`:325-328`), and a hold mutation then fixes
whatever that walk happened to leave in place. §4.3's "coordination gap" and §8 Q1's
"heritable shadow" are two descriptions of this one fact.

If this reading is right, then §5's result is over-determined: **coupling adds proposal
supply to a flat landscape**, and no mutation operator can supply a gradient that the
fitness function does not contain. The 2×2 could not have come out any other way, which
is a stronger statement than "these two repairs failed."

In the Lean, this is `plasticDependence` being degenerate **in the `field` coordinate**.
`BaldwinDesign.no_witness_of_degenerate` covers total degeneracy; the per-coordinate
version is the lemma that would license a redesign rather than merely describing the
failure afterwards.

### R6. The positive control shows which kind of cost is load-bearing

`TN-part-III-b-baldwin-recovery.md` §2(a) records a correction — "plasticity must cost
something" — and the design implemented it as an explicit charge on the plasticity
machinery. The planted control in this repository, which passes, has **no explicit
plasticity cost at all**:

```clojure
(defn expected-learning-score                          ; hinton_nowlan.clj:23-28
  [genome]
  (if (compatible? genome)
    (Math/pow 2.0 (- (plastic-count genome)))
    0.0))
```

Fitness is a smooth monotone function of genome **content**: every locus correctly fixed
doubles fitness, and any incorrectly fixed locus is lethal via `compatible?`. The cost of
plasticity is implicit in the probability that learning succeeds, and that probability is
determined by what the genome already carries.

So §2(a) was right that costlessness is near-tautological, but the design answered it
with a **capacity** cost where Hinton--Nowlan uses a **usage/reliability** cost. A
capacity cost is flat in genome content by construction, which is R5. This is the
sharpest available statement of what separates the passing control from the failing
substrate, and it comes from the apparatus rather than from the literature.

### R7. Suggested order of work

**Free — reanalysis only.** Fold R1 into the §2 ledger and close §8 Q2. Re-read the
guidance pilot on raw `:mean-reach`, which is already recorded per budget in
`baldwin_guidance.clj:70-86` alongside the band score, and record R4 as the hypothesis
for the confirmation seeds.

**Cheap diagnostics, hours not days.**

1. **Does lifetime rewriting have a target?** For one evolved genome, compute the field
   at `t*` under K evaluation seeds and measure pairwise agreement, against chance
   (`1/256`) and against the inherited field. Every Baldwin experiment presupposes that
   learning converges on something reproducible enough to encode, and this note records
   no check of it. R2 gives a specific reason to doubt it. If the fields agree at chance,
   §8 Q12's "decisive negative" conditions are met by a single cheap measurement.
2. **Is the inherited field selectable at all under active rewriting?** Perturb one locus
   of `:field` at an unheld locus of an evolved genome; measure Δreach against evaluation
   noise; repeat across loci. This converts R5 from a code reading into a number, and it
   is the correct form of §8 Q7 — the relevant comparison is the selection coefficient of
   an *allele*, not of a hold.
3. Run `scripts/assimilation_map.clj` (the 80×256 locus×rule sweep) if it has not been.
   It is the empirical map of `BaldwinDesign.LocallyAssimilable`, and it says what a
   latent allele would have to contain.

**The mechanistic change, if 1 and 2 come out as predicted.** Replace or supplement the
capacity cost with a **usage** cost: a term proportional to realised rewriting, e.g.
`c_use · (rewrite events)/(W·T)`, or the Hamming distance between the inherited field and
the field at `t*`. `c/changed-count` (`core.clj:167`) already exists and
`scripts/mutation_axis.clj:23` already computes churn with it. This makes `:field`
non-neutral without touching the causal protocol, and — relevant to §4.1 — it is
**smooth**, so it supplies a gradient even though `band-score` is a cliff with a hard
zero outside `[10.0, 18.7]`.

Preregister it as a two-phase signature, so that a partial result is still a result:

- **Phase 1 (preparation / guidance):** agreement between the inherited field and the
  converged field rises under selection *while dependence stays high*. This is §8 Q1's
  heritable shadow made observable, and it is a positive result on its own.
- **Phase 2 (assimilation):** held fraction rises above the `0.1858` mutation-only null.

**A representation change, if diagnostic 1 says the target is unstable in cell space.**
R1 says the assimilable structure is real but indexed by `p0` rather than by cell — so
the cell may simply be the wrong heritable unit. The rewrite already conditions on a
4-bit context quadruple `[ph(i-1), ph(i), ph(i+1), nph(i)]`
(`baldwin_selection.clj:96-100`), which is only 16 classes, and contexts recur across
initial conditions even when cells do not. **Recompute the R1 agreement statistic indexed
by context instead of by cell index.** If context-indexed agreement is bimodal and stable
across seeds where cell-indexed agreement was unimodal and unstable, the missing heritable
unit has been found, and the genome becomes a small evolvable table over contexts — §8
Q11's "evolvable prior over rewriting", made concrete and cheap to test. This is a
re-indexing of instrumentation that already exists.

### R8. A strategic alternative to demonstrating the effect

§8 Q12 asks when a negative would become decisive. There is a third option between a
decisive negative and a demonstrated positive, and the apparatus is already sized for it.

The planted Hinton--Nowlan control passes and MetaCA fails, and R1/R2/R6 identify what
separates them: **cross-lifetime stationarity of the learning target**. That is now a
knob, not a property — share the rewrite tape across evaluations or not, fix `p0` or not
— giving four cells from fully stationary to fully non-stationary. Sweeping it and
locating where the witness switches off would yield a positive, explanatory result that
retro-explains every negative in §2, and unlike "find a Baldwin effect in MetaCA" it is
an outcome the design can be confident of obtaining.

### R9. Statistical note

Both pilots are single evolution seeds compared on arm means. The one experiment in this
line that produced a decisive answer — the frozen-phenotype control in §3 — did so with
seven **paired** comparisons and an all-seven sign, not with a mean contrast. Pair on
(seed, site, task), test the sign of paired differences, and derive the required effect
size from the mutation-only null before renting hardware again. §8 Q7's `0.0006` figure
is the saving from one hold; the quantity that matters for R7.2 is the selection
coefficient of one allele, and it has not been estimated.

### R10. What this review does not establish

- R2, R5 and R6 are readings of committed code, not measurements. R7.1 and R7.2 are the
  measurements that would confirm or refute them.
- The prediction that a usage cost supplies a workable gradient is untested. It is
  motivated by the contrast in R6, not by any MetaCA run.
- The context-indexing proposal is a conjecture about where stable structure might live.
  R1 establishes that it does not live in cell space; it does not establish that it lives
  anywhere.
- R4 is one seed on a confounded axis and is not evidence of guidance.
- No criterion, classifier or preregistration is changed by this section, and no pilot is
  promoted.
