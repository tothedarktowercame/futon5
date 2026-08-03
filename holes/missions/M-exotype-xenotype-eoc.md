# M-exotype-xenotype-eoc — can the substrate DISCOVER edge-of-chaos?

**Status:** Slice 0 run and reviewed (negative, informative). Slice 0b specified, not
dispatched. Chartered 2026-08-03 from the collapse of the Baldwin framing.
Owner: claude-11 (orchestration/review). Builder: codex-1 by dispatch.

**One line:** every "Baldwin" experiment to date fixed an exotype in advance (river =
rot+2) and tried to get genotypes to stabilise under it. That is not the question. The
question is whether evolution at the phenotype and genotype layers can drive evolution
at the EXOTYPE layer to discover EoC for itself.

---

## 1. Why the Baldwin framing was abandoned

Measured in `mmca-clj`, 2026-08-02/03. Full detail in that repo; the load-bearing
results:

- **EoC here is dynamical, not configurational.** With rewriting disabled
  (`learning-budget 0`) band is **0.0000** across all 1280 records. With rewriting on
  and selection OFF it is **0.3100** (0.354 at generation 0, on random fields, before
  selection acts). With selection on, 0.7617. So the coupling finds the regime itself;
  selection sharpens what the dynamics already produce.
- **Static fields reach the band only at isolated spikes** — of 256 uniform static
  rules, 17 non-zero, 8 above band 0.5, best rule 147 at 0.9483; 239 exactly 0.0000.
- **Holding kills function** — band collapses to 0.0000 by k=4 randomly-held loci;
  greedy-best reaches only k=2. Holds interact negatively (locus 25 alone 0.9914,
  locus 12 alone 0.9799, both together 0.7787).
- **Five inert quantities defeated every assimilation framing**: capacity dependence
  (1/80 constant refund), the work conjunct, the non-inferiority margin, realized
  rewriting (8996–9236 of a 9600 ceiling), per-step decay (~78 of 80 cells rewrite
  every step, first third to last).

**The decay result does NOT generalise** and must not be quoted as "the substrate admits
no assimilation". It was measured under a FIXED UNCONDITIONAL exotype. An exotype of the
form `bored -> mutate, else hold` has a falling rate by construction. That is the whole
reason this mission exists.

## 2. The architecture

**Layers:** pheno -> geno -> **exo** -> xeno.

- **Exotype = the operator that rewrites genotypes.** Joe: *"a model of culture"* — a
  cultural program by analogy to **agriculture**, a breeding programme for rules. It
  modifies other things' genomes without being one. Hence **transmissible but not
  strictly heritable**: it spreads laterally by adoption, can be abandoned, needs no
  lineage or reproduction event.
- **Xenotype = the rule governing how exotypes vary.** Not one fixed rule but a
  **manifold** we navigate. Coordinates: lift source, lift trigger, promotion
  `:local`->`:super`, lapse.
- **What is imposed moves UP a level.** Today the exotype is fixed and global and the
  genotype varies. In this design the xenotype manifold is fixed, the exotype grid
  varies locally, the genotype is what gets rewritten. You cannot impose nothing; you
  push the imposition up until what is below is discovered.
- **One exotype per cell** — a third grid parallel to geno and pheno. Usually uniform
  because one global rule is installed. **All "learning" sits in the exotype layer.**

**The exotype IS a design pattern.** LEFT(8)=IF/context, EGO(8)=BECAUSE/the operator,
RIGHT(8)=HOWEVER/liabilities, NEXT(8)=THEN/resulting context, PHENO-FAMILY(4)=relations.
That is Alexander's form field for field, which is why the War Machine's pattern
machinery applies at a different grain, and why "transmissible not heritable" is exact
rather than analogical — a pattern language is the canonical such artifact.

**The lift must read a NEIGHBOURHOOD**, not re-represent one genotype, or the exotype
grid carries no information the genotype grid lacks and "exotype diversity drives
genotype diversity" is circular.

**Hop 2 reframed (Joe: "big improvement").** The hexagram is **NOT an operator
codebook**. `hexagram+energy->rule` (rule = hex*4 + energy) tries to recover an 8-bit
operator from 6 bits, but EGO already *is* the operator, so that round-trip invents
rather than recovers. The hexagram is a **SITUATION KEY**: adopt a practice from a
neighbour whose situation resembles yours, and take their EGO field directly and
losslessly. Hop 2 disappears as a codebook and reappears as the matching predicate in
the xenotype's lift-source rule.

## 3. The regulator (AIF)

`G_efe(a) = D_KL[Q(o|a) || C] + E H[P(o|s)]` — risk + ambiguity. Two terms that oppose:
pragmatic pushes toward order, epistemic toward variety, and **the balance is EoC** —
not a target we set. Neither term mentions band, so EoC would be emergent from the
regulator's structure rather than imposed. See `p4ng/sec-glossary.tex`,
`futon2/src/futon2/aif/efe.clj`.

**G over policies IS the transmission rule.** Policies = `hold` or `adopt-from-j`.
Then lift-source = which policy wins, lift-trigger = whether the winner is `hold`. No
threshold needed. And `Q(pi) ∝ E(pi)·exp[-G(pi)/tau]` gives the rest: **E is cultural
inertia**, so promotion = E accumulating and lapse = E decaying. All four manifold
coordinates fall out of one criterion; the real coordinates are the generative model,
tau, and E's update rule.

**E must live in the ENVIRONMENT, not the cell** — strictly local means no long memory.
`E(adopt from j)` ∝ how many neighbours already hold that exotype. Prevalence, read not
recalled; stigmergy, as `:pher` already is for ants. The exotype grid IS the memory.
Consequence: prevalence is rich-get-richer and collapses the grid; G opposes it; tau
sets the exchange rate. **Whether that contest sustains heterogeneity is the experiment.**

**Open problem — conatus.** The ants' EFE is 98% risk (27.54 of a 28.10 margin) and risk
is starvation. A MetaCA cell has no conatus, so the load-bearing term does not transfer,
and the term that would carry it (epistemic) is measured **inert** in the ants
(`ambiguity spread exactly 0`). Proposed resolution: **make the dark room fatal** — an
informational conatus, hunger growing when a cell's own rule is static AND its
neighbourhood uniform. Then EoC is a *survival condition*, not an imposed preference.
**It is only not-cheating if the conatus is definable without reference to band or the
EoC criterion** — the proposed form mentions only rule change and local uniformity.

## 4. Method commitments

- **Preregister in Lean before running.** `mathlib4/DarkTower/ExperimentalDesign.lean`
  + `ExperimentPreregistration.lean`. `Axis.Navigable` requires ADJACENT levels to carry
  gradient, not merely non-constancy.
- **`inert` is the word for the recurring failure** (featuregrid maturity ladder,
  `futon2/holes/FEATUREGRID-aif-systems.md`): *connected, computed, canonically named,
  and causally dead*. Ladder: absent -> half-built -> inert -> live -> measured. Refuse
  to trust any component below `live`.
- **Ablation is a discovery engine, not a brake** (Joe). Get it working, then break it.
  **Build order = ablation order, reversed** — if each slice is runnable and kept, the
  ablations are the build history. Ablating an `inert` term proves nothing, so: make it
  live, measure its contribution, THEN ablate.
- **Measure the ceiling before designing to it.** Three times in two days a conclusion
  was drawn from a measurement narrower than the claim. See §6.
- **Compute is cheap** — 0.18–0.29 s per CA signature run, lift calls ~0.0002 s. A
  1600-run comparison is 5–8 minutes. Do not economise on sample size.

## 5. The slices

Each slice's null is the slice below it.

**ORDER CHANGED 2026-08-03 (Joe): Slice 1 goes FIRST.** The lift answers "how does a
cell find similar neighbours to copy", which presupposes that copying matters — and we
have not shown copying matters. Slice 1 needs no lift at all (a trivial rule like
"adopt the locally most prevalent exotype" or "adopt on `boring?`" uses no hexagram),
and it is the null for everything above it.

| slice | content | status |
|---|---|---|
| **1** | exotype grid, trivial transmission rule, NO AIF, NO lift | **NEXT** |
| **0** | lift variants as arms; do hexagram classes group behaviour? | DONE — negative |
| **0b** | tape-averaged signatures + lambda-grounded lift | specified, deferred behind 1 |
| **2** | G without E (two-term core + conatus); dynamic-range check FIRST | not started |
| **3** | E as prevalence; conformity-vs-fit; tau sweep | not started |
| **4** | manifold sweep: *we* navigate, then *the system* navigates | not started |

Slice 1 is the null for everything above it: **if a trivial transmission rule gives EoC
all the way down, AIF is not needed** and we learn that before building it. Conversely
if a varying grid does nothing, the lift's quality is moot.

Two experiments in this order, and do not merge them: (a) *we* navigate the manifold —
any objective allowed, it is analysis; (b) *the system* navigates — then only locally
computable qualities may drive it, or the objective is re-imposed through the back door.

## 6. Slice 0 result (commit `929239c`, reviewed by claude-11)

Registration: `mathlib4/DarkTower/ExotypeLiftVariantPreregistration.lean` (builds clean;
five theorems on `[propext, Classical.choice, Quot.sound]`, no `sorryAx`).
Artifacts: `futon5/reports/lift-variant-comparison.{md,edn}`,
`futon5/src/futon5/hexagram/lift_variants.clj`, `futon5/scripts/lift_variant_compare.clj`.

| variant | occupancy | flip-locality | ratio | null@k | excess |
|---|---:|---:|---:|---:|---:|
| eigen-sign | 32 | 1.327 | 0.9863 | 0.9844 | +0.002 |
| eigen-ordering | 17 | 1.433 | 0.9962 | 0.9890 | +0.007 |
| eigen-magnitude | 28 | 1.534 | 1.0396 | 0.9854 | +0.054 |
| symmetrised | 5 | 0.209 | 0.9960 | 0.9823 | +0.014 |
| random (control) | 60 | 2.971 | 1.0048 | 0.9748 | +0.030 |

- **`armsSeparable` NOT discharged.** No variant demonstrably groups neighbourhoods that
  behave alike. The random control's own excess (+0.030) is the noise floor; only
  eigen-magnitude clears it, by under 2x.
- **Two controls added on review** (they were missing from the registration, my defect):
  ORACLE — median split on the signature's own mean gives **1.2326**, so the measure
  works and the negative is real, not an apparatus failure. MATCHED-GRANULARITY NULLS —
  the null is not 1.0 and FALLS with k (0.9967 at k=2 to 0.9748 at k=60), so raw ratios
  across occupancy 5..60 are not comparable.
- **Symmetrisation prediction FAILED.** Predicted occupancy 64 (removing conjugate-pair
  degeneracy); got **5**, flip-locality 0.209 — it barely responds to input. Plausibly
  a dominant Perron eigenvalue plus five near-zero ones whose signs hardly move.
- **CEILING CHECK** (claude-11, direct): same neighbourhood under 8 tapes = 0.5457;
  different neighbourhoods = 1.3285; **ceiling ratio 2.4344**. So behaviour IS strongly a
  function of the neighbourhood — the target is reachable and the eigen-lifts capture
  essentially none of a large available signal. My tape-confound hypothesis was WRONG in
  its strong form, though tape noise does attenuate Slice 0's ratios toward 1.0.

## 7. Slice 0b, as specified

1. **Tape-average each neighbourhood** over a fixed tape set (T=8), so the signature
   estimates expected behaviour rather than one draw. Removes the attenuation. Cheap.
2. **Replace the eigen family with a dynamically-grounded one.** Eigenvalues of an
   arbitrary 6x6 reshaping are a fact about the REPRESENTATION; nothing connects them to
   CA behaviour, and measurement says they carry ~none of a 2.43 signal. Instead:
   **Langton's lambda** per sigil, **locale heterogeneity** across LEFT/EGO/RIGHT, and
   **phenotype density** from the family bits. lambda IS a claim about which regime you
   are in, which is exactly what a situation key should encode.
3. Retain eigen-sign as incumbent baseline, keep the random control, keep oracle and
   matched-granularity nulls.

**Joe's rulings on lambda, 2026-08-03:**

- Use it to get things moving, while noting the joke: Part I **trod all over** lambda,
  so Part III leaning on it is "yeah it was useless in Part I, but you know, it's not so
  bad". Say that out loud in the write-up rather than hoping nobody notices.
- **A lookup table gives lambda for any ECA, so the initial sigils do not matter** —
  lambda is read off whichever rule a cell holds AT THAT MOMENT. Everything is computed
  **strictly locally in both time and space**. This kills claude-11's objection that
  lambda would wash out under rewriting: it is a live local quantity that TRACKS the
  rewriting rather than being erased by it.
- **HELD, do not build yet: keying on the propagator's cycle structure.** It is a
  different LAYER, not a different feature — cycle-keying would drive exotype evolution
  **from exotype state**, whereas lambda-per-ECA drives it **from genotype state**.
  Keep the two architectures distinct and do the genotype-driven one first.

**Behavioural signature — corrected by Joe.** The paper uses **damage** to understand
EoC (the causal-reach protocol: perturb, and measure how far the difference spreads).
The Slice-0 triple of genotype diversity + phenotype activity + mutation rate was what
was easy to compute, not what the paper measures. Slice 0b and Slice 1 should key on
damage. NB the ceiling of 2.4344 was measured on the OLD triple and must be re-measured
against a damage signature before it is quoted for the new one.

## 8. Open decisions (Joe's)

- Land the corrected `:schema 2` artifact? (supersedes numbers already in `929239c`)
- Is `E` per-cell or shared across the grid? Shared is the more cultural reading.
- Does the lift read the cell's own sigil or its neighbourhood's? **Neighbourhood**, or
  the exotype grid is circular (claude-11's recommendation; not yet ratified).
- Where does the framework live? Pieces span four repos: Lean in `mathlib4/DarkTower/`,
  Malli + core.logic + AIF in `futon2/`, exotype + lift in `futon5/`, substrate in
  `mmca-clj/`.

## 9. Parked

`futon5/holes/tech-notes/TN-GCD-resolve-before-submit.md` — Supplement 3's caption and
`M-propagators.md` §1.4 state the gcd(offset,8) classification in OPPOSITE directions.
Not being fixed now; must not reach submission unnoticed.

Related: `M-propagators.md`, `scripts/exotype_by_example.clj` (Q1/Q2 with nulls built
in), `scripts/exotype_demo.clj`, `futon2/holes/FEATUREGRID-aif-systems.md`,
`p4ng/main-2026.tex`.

## 10. RESULTS 2026-08-03 (Slices 2b/3 + characterisation) — READ THIS BEFORE QUOTING ANYTHING

**The coexistence finding is REFUTED. What survives is dynamical, not configurational.**

### The sequence
1. Slice 2 found the EFE regulator collapses to ONE exotype: conatus off -> 80/80
   `:identity` (dark room, as predicted), conatus on -> 80/80 `:chaos`.
2. Joe: bistable endpoints mean the system needs TUNING, not discarding. Correct.
3. Slice 2b's sweep found "no interior" — but its grid had NO POINT inside (0.4, 0.7),
   the interval where the regime actually changes. claude-11's specification error.
4. A refined sweep found an apparent critical point at lambda = 0.55: `:identity` and
   `:chaos` coexisting 49.13/30.87 on all 100 seeds, grid activity 119 of 120 steps
   against ~6.4 in both flanks, a 16-fold rise in changed cells.
5. **Characterisation killed the coexistence.** It is a TRANSIENT decaying to
   all-`:identity`: entropy 0.6030 (t=120) -> 0.2754 (t=480) -> 0.0643 (t=1200),
   a -26.87 SEM decline. Spatial structure is DOMAINS (mean size 6.36, max 32.28,
   12.58 per run) — i.e. a coarsening process, which is exactly what produces an
   apparent mixture at 120 steps and consensus later.
6. Slice 3 (prevalence E, stigmergic) appeared to show conformity INCREASING diversity
   (kinds 2.00 at tau=0 rising to 3.94 at tau=10). **Also a transient**, measured by
   claude-11 directly:

   | tau | t=120 | t=600 | t=1200 | t=3000 | t=6000 |
   |---:|---:|---:|---:|---:|---:|
   | 0 | 2.00 | 1.50 | 1.08 | 1.00 | 1.00 |
   | 0.3 | 2.00 | 1.92 | 1.67 | 1.08 | 1.00 |
   | 10 | 4.00 | 3.50 | 2.58 | 2.17 | 1.92 |

   Everything coarsens; tau only sets the RATE. At high tau `exp(-G/tau) -> 1` so
   `Q -> E` and the rule becomes near-proportional imitation = the 1D voter model,
   which coarsens diffusively (~N^2 = 6400 steps at width 80). argmin-G is
   deterministic and drives straight to consensus.

### What SURVIVES and may be quoted
- **Damage peaks sharply at lambda* and is ZERO in both flanks**, at all three layers:

  | lambda | phenotype | genotype | exotype |
  |---:|---:|---:|---:|
  | 0.40 | 0.48 | 1.00 | 0.00 |
  | **0.55** | **7.72** | **9.92** | **7.08** |
  | 0.70 | 3.60 | 0.00 | 0.00 |

  Contrasts against both flanks run +6.1 to +12.6 sem, every one resolved.
- **lambda* is SIZE-INVARIANT**: maximum entropy occupies the identical plateau
  [0.54, 0.545, 0.55] across every width (40/80/160) and horizon (60/120/240) tested;
  transition bracketed to (0.550, 0.555]. A unique lambda within the plateau is NOT
  resolved.
- **Conformity retards homogenisation** (it does not prevent it). This is the opposite
  of the rich-get-richer intuition we started from, and the mechanism is that high tau
  makes the rule diffusive rather than greedy.

### What may NOT be quoted
- Sustained coexistence at lambda = 0.55. It does not exist at t >= 1200.
- The 49/31 mixture as a configuration. It is a snapshot of a coarsening transient.
- Any three-layer JOINT band fraction. Genotype anchors are still inverted and exotype
  anchors equal, so those layers remain honestly unbanded and the joint fraction is nil.
- Slice 3's high-tau diversity as sustained.

### The reading for Part III
**Same lesson as [[project_metaca_eoc_is_dynamical]], one layer up: the edge of chaos
here is DYNAMICAL, not configurational.** There is no mixed configuration to point at;
there IS a boundary at which perturbations propagate (damage 7-10 vs exactly 0 in the
flanks) and relaxation takes an order of magnitude longer. Quote the dynamics, not the
configuration.

### Method note — the recurring defect is SAMPLING, not measurement
Four corrections in one day where the measurement was sound and the sampling was wrong:
the gcd caption (partial offsets), the single-draw null (one partition per k), the
Slice 2b grid (no point in the transition interval), and the 120-step horizon (a
transient read as a steady state). **Rules: bracket the extremes then sweep densely
BETWEEN them; and before calling any mixture a state, run it out at least 10x and show
the trajectory.**
