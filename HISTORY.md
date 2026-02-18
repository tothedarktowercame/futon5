# The Search for Computational Intelligence in MMCA

A narrative history of futon5, tracing the pivotal moments in the search
for sustained, structured computation in Multi-scale Multi-Channel
Cellular Automata.

242 commits across 37 days (2026-01-11 to 2026-02-16).

---

## Timeline

| Date | Pivot | Commit | Significance |
|------|-------|--------|--------------|
| Jan 11 | [Genesis](#pivot-1-genesis) | `92fa793` | AIF scoring — the starting question |
| Jan 17 | [CT Sprint](#pivot-2-the-category-theory-sprint) | 48 commits | Top-down derivation attempt |
| Jan 23 | [P0 Gate](#pivot-3-p0-gate-passed) | `358001e` | Shift to gated experimentation |
| Jan 24 | [Xenotype Architecture](#pivot-4-xenotype-architecture) | `f66bf2f` | 256 physics rules crystallised |
| Jan 25 | [CA Rules as Wirings](#pivot-5-elementary-ca-rules-as-mmca-wirings) | `0720950` | MMCA meets Wolfram |
| Jan 25 | [Sierpinski Verification](#pivot-6-sierpinski-verification) | `d2acdea` | Framework correctness proved |
| Jan 25 | [Barcode Collapse](#pivot-7-barcode-collapse) | `bf1d68d` | The real failure mode identified |
| Jan 25 | [Health Classification](#pivot-8-health-classification) | `1c4d8a9` | Automatic run diagnostics |
| Feb 15 | [SCI Detection Pipeline](#pivot-9-sci-detection-pipeline) | `a8728bc` | 8-component Wolfram class detector |
| Feb 15 | [Coupling Spectrum](#pivot-10-cross-bitplane-coupling-spectrum) | `fa44be4` | New observable: cross-bitplane MI |
| Feb 15 | [Carry-Chain Coupling](#pivot-11-carry-chain-coupling) | `237861a` | First designed structured coupling |
| Feb 15 | [Coupling as Fitness](#pivot-12-coupling-as-fitness) | `94fcdb5` | Closing the evolutionary loop |
| Feb 16 | [Diversity-Coupling Tradeoff](#pivot-13-the-diversity-coupling-tradeoff) | `6a70834` | The fundamental tension revealed |

---

## Phase 1: Top-Down Theoretical (Jan 11–22)

**Strategy**: Can we *derive* the conditions for computational intelligence?

### Pivot 1: Genesis
`92fa793` — 2026-01-11

The starting question: can Active Inference Framework (AIF) scoring detect
interesting MMCA dynamics? The first commit implements spacetime rendering
and a scoring function that looks for a "god-mode regime" — sustained
activity that isn't chaotic.

This frames the entire project: MMCA produces 8-bit sigil spacetime
fields, and we need to know which ones are *computing*.

### Pivot 2: The Category Theory Sprint
48 commits — 2026-01-17

The most intensive single day of the project. An attempt to formally derive
SCI from first principles: exotic programming specification, curriculum with
basecamps and lifts, natural transform residuals, Kolmogorov arrow discovery,
ratchet scoring.

The approach was to build a category-theoretic framework where computational
intelligence would emerge as a structural property — functors, natural
transforms, arrows between categories of CA dynamics.

**What survived**: The CT mission diagram infrastructure (used Feb 8–10 for
static analysis of futon1a–futon3). The hexagram connection, which became
the basis for the 256-rule xenotype system. The idea that SCI needs
*structural* characterisation, not just scalar scores.

**What didn't**: Direct derivation of SCI from CT residuals. The arrow
discovery pilot produced candidate morphisms but no actionable classifier.
The ratchet scoring system was eventually superseded by verifier
constraints.

**Lesson**: Top-down theory generates useful vocabulary and architecture,
but the search for SCI needs empirical grounding.

---

## Phase 2: Empirical Grounding (Jan 23–24)

**Strategy**: Can we *measure* computational intelligence systematically?

### Pivot 3: P0 Gate Passed
`358001e` — 2026-01-23

The shift from "build theory" to "run experiments with quality gates."
P0 is the first formal readiness check: does the infrastructure actually
produce reproducible results?

Excursion 1 runs 200 seeds. Trigram collapse scorer quantifies a specific
failure mode. HIT (Human Inspection Task) alignment metrics connect
automatic scores to visual quality.

**Why it matters**: Before this, experiments were ad hoc — run something,
look at the picture, adjust. After P0, there's a repeatable methodology
with staged gates.

### Pivot 4: Xenotype Architecture
`f66bf2f` — 2026-01-24

The computational substrate crystallises:
- 64 hexagrams × 4 energy levels = 256 physics rules
- Per-cell local exotype evolution (each cell carries its own rule)
- Eigenvalue-based hexagram extraction from genotype
- Wiring system with CT validation

This is when MMCA's degrees of freedom become concrete. Before: "a cell
has a sigil and some rules." After: "a cell executes one of 256 named
physics rules, and we can track which rule is where."

---

## Phase 3: The Wiring Breakthrough (Jan 25)

**Strategy**: What *is* computation in MMCA?

63 commits — the most productive day. This is where the search pivots
from "trying things" to "understanding what we're looking at."

### Pivot 5: Elementary CA Rules as MMCA Wirings
`0720950` — 2026-01-25

Rules 90, 110, 30, 184, and 54 expressed as MMCA wiring diagrams.
Boolean formulas over neighbour bits, packaged as EDN data.

**Why this changes everything**: Before this commit, MMCA dynamics were
opaque — you could look at a spacetime diagram and guess whether it was
interesting, but you had no reference frame. After this commit, you can
say "this run behaves like Rule 110" or "this is closer to Rule 30."

Wolfram's classification (Class I–IV) becomes the vocabulary for MMCA.
The search for computational intelligence becomes the search for
Class IV dynamics.

Key finding from the same day: Rule 184 shows highest affinity for
hexagram 11 (泰/Peace) in exotype sampling.

### Pivot 6: Sierpinski Verification
`d2acdea` — 2026-01-25

Wiring Rule 90 produces byte-identical output to pure Rule 90 (both
5,790 bytes). The framework is faithful.

This sounds trivial but is critical: it proves that MMCA wiring diagrams
*are* cellular automata, not approximations. Every theorem about CA
dynamics applies directly.

**The bug found**: `run-mmca` was ignoring the `:wiring` parameter
entirely — running the default kernel regardless. The fix (`run_wiring_ca.clj`)
bypasses the legacy runner and uses the xenotype interpreter directly.
This architectural clarification separated "MMCA the legacy system" from
"MMCA the wiring framework."

### Pivot 7: Barcode Collapse
`bf1d68d` — 2026-01-25

> "barcode collapse is the real problem"

Human visual inspection reveals a failure mode that automatic metrics
hadn't captured:
1. System starts with edge-of-chaos dynamics ("coral at first")
2. Degenerates into repeating vertical stripes ("barcode")
3. Exotype diversity collapses to 3–4 active rules
4. The pattern repeats periodically — not sustained complexity

This is *not* chaos (high change rate) or frozen (no change). It's
**low-dimensional attractor collapse**: the system finds simple periodic
solutions and gets stuck.

**Why it matters**: Understanding how systems fail is as important as
finding systems that succeed. This reframes the search: the problem isn't
*initiating* complexity (many configurations show transient complexity),
it's *sustaining* it against attractor collapse.

### Pivot 8: Health Classification
`1c4d8a9` — 2026-01-25

Universal run health classification: every run gets an automatic
HEALTHY / BARCODE / FROZEN / CHAOTIC / PERIODIC label based on
change-rate, autocorrelation, and band periodicity.

Combined with the wiring outcome registry (`1d95119`), this creates
a systematic record: wiring X at parameters Y produced outcome Z.
The learning loop (`09981e0`) correlates wiring features with health
outcomes.

**The infrastructure shift**: From "look at pictures and decide" to
"classify automatically and correlate." This is what makes the search
*searchable*.

---

## Phase 4: Consolidation (Jan 26 – Feb 10)

**Strategy**: Build infrastructure for disciplined search.

No single dramatic pivot, but important structural work:

- **Nonstarter system** (Jan 26, 17 commits): Hypothesis tracking with
  mana/priority. Missions, excursions, and reports linearised into a
  searchable database. This is the first attempt at the "evidence tracking"
  that futon3c is now building more formally.

- **CT Mission Diagrams** (Feb 8–10): Static analysis of futon1a, futon2,
  futon3 as wiring diagrams. Validates architectural invariants. Composition
  mission with parallel constraints.

- **Healthcheck Ratchets** (Feb 9): Settled-state guards that prevent
  regression — once a metric reaches a threshold, it can't drop back.

---

## Phase 5: The Measurement Breakthrough (Feb 15)

**Strategy**: What does SCI *look like* at the bitplane level?

16 commits. The second phase transition.

### Pivot 9: SCI Detection Pipeline
`a8728bc` — 2026-02-15

2,602 lines of analysis code across 4 new modules:
- `domain_analysis.clj`: spatiotemporal periodic tile detection
- `particle_analysis.clj`: defect tracking, species cataloguing, collisions
- `info_dynamics.clj`: transfer entropy and active information storage
- `wolfram_class.clj`: decision-tree Wolfram class estimator

For the first time, a run can be automatically classified into Wolfram
Classes I–IV with calibrated thresholds. Classification accuracy on
known rules: 67% (improved from 33% after bitplane analysis).

Also includes Z3-based structural analysis (`wiring_analyzer.py`):
GF(2) linearity, surjectivity, and sensitivity profiling of wiring rules.

### Pivot 10: Cross-Bitplane Coupling Spectrum
`fa44be4` — 2026-02-15

**A genuinely new observable.** Pairwise mutual information between the
8 bitplanes of the sigil field, computed across spacetime history.

Three regimes discovered:

| Regime | Mean MI | Examples | Meaning |
|--------|---------|----------|---------|
| Independent | ~0 | Rules 90, 110, 30 | Each bitplane runs its own CA |
| Weakly coupled | ~0.017 | Champion MMCA | Legacy kernel creates mild coupling |
| Uniformly coupled | ~0.08–0.16 | Rule 184 | AND/OR/NOT gates mix all bits equally |

**The gap**: no system showed *structured* coupling — spatially localised
or topologically organised cross-bitplane interaction. This became the
target.

### Pivot 11: Carry-Chain Coupling
`237861a` — 2026-02-15

New wiring components: `sigil-add-mod`, `sigil-sub-mod`, `sigil-mul-low`,
`sigil-avg` — arithmetic on sigil integer values mod 256.

R110+AddSelf (Rule 110 per-bit, then add self mod 256) creates
**band-diagonal coupling**: only adjacent bitplanes interact
(bp6↔bp7 MI=0.031, bp2↔bp3 MI=0.024). Preserves Class IV dynamics
(BpDF=0.69, 15 particles) while creating 5× more coupling than
pure Rule 110.

**Why this is a breakthrough**: This is *designed* coupling. Not found by
accident or exhaustive search, but constructed from understanding that
carry propagation in modular addition couples bit *n* to bit *n+1*.
The coupling matrix's band-diagonal structure is a direct signature of
the carry chain.

### Pivot 12: Coupling as Fitness
`94fcdb5` — 2026-02-15

Mean-coupling and coupling-CV as verifier fitness dimensions. TPG
evolution selects *for* structured coupling.

Result: MI=0.1881 in the first evolved TPG — first "structured" coupling
found by search rather than manual design. Evolved temporal schedule
(4:1 AddSelf:Conservation) scored 0.406 satisfaction.

This closes the loop: measure → classify → target → evolve → measure.

---

## Phase 6: First Production Run (Feb 16)

**Strategy**: Scale up and let evolution find what we can't design by hand.

### Pivot 13: The Diversity-Coupling Tradeoff
`6a70834` — 2026-02-16

The production run completed: 16+16 population, 50 generations, 5 eval
runs × 50 inner generations per candidate. Best overall satisfaction
rose from 0.589 to **0.753**.

But the headline number hides the real finding. The objective trends
reveal two distinct regimes separated by a phase transition at **gen 12**:

| Generations | Best | Diversity | Mean-Coupling | Regime |
|-------------|------|-----------|---------------|--------|
| 0–11 | 0.59–0.68 | 0.85–0.89 | 0.0 | Good CA dynamics, zero coupling |
| 12–49 | 0.71–0.75 | 0.35–0.40 | 1.0 | Full coupling, diversity collapses |

At gen 12 the Pareto front reorganises: the best individual switches from
a high-diversity/zero-coupling phenotype to one that fully satisfies the
coupling objectives but sacrifices diversity. Diversity drops from 0.89
to 0.36 and never recovers.

**The 27-generation plateau**: Best score hits 0.753 at gen 23 and
flatlines. The Pareto front saturates at 16 (= mu) — every parent is
non-dominated. The population has found the Pareto frontier and can't
push past it.

**The evolved schedule** is a 28-step cycle:
```
conditional(8) → wiring-msb(2) → wiring-bit5(1) → expansion(4)
→ adaptation(1) → conditional(6) → wiring-addself(6)
```
Conditional dominates (50%), wiring-addself gets a solid end-of-cycle
block (21%), with brief bitplane-selective probes (msb, bit5) in the
middle. More structured than the proof-of-concept's 4:1 ratio.

**The spacetime**: Genotype shows barcode-like vertical striping in the
lower half — the old failure mode (Pivot 7) is still the main obstacle.
The system achieves coupling but at the cost of the diversity that would
indicate sustained complex dynamics.

**Why this is pivotal**: This is the first time the search has run long
enough to expose a *structural* limitation rather than just a lack of
compute. The diversity-coupling tension is not a bug in the verifier
spec — it reflects a genuine property of the dynamics. Coupling (via
carry-chain arithmetic) tends to synchronise bitplanes, which reduces
the distinct phenotype states available, which is exactly what the
diversity metric measures.

This reframes the next phase of the search: the problem is no longer
"can we evolve coupling?" (yes) but **"can we couple bitplanes without
synchronising them?"**

---

## The Arc

```
Jan 11    Can we score it?          → AIF regime detection
  ↓
Jan 17    Can we derive it?         → Category theory, arrows, ratchets
  ↓
Jan 23    Can we gate experiments?  → P0 readiness, 200-seed runs
  ↓
Jan 24    What are the rules?       → 256 xenotype physics rules
  ↓
Jan 25    What IS computation?      → CA rules as wirings, barcode collapse
  ↓
Jan 26    How do we track search?   → Nonstarter, outcome registry
  ↓
Feb 15    What does SCI look like?  → Coupling spectrum, carry chains
  ↓
Feb 15    Can we evolve toward it?  → Coupling as fitness, MI=0.188
  ↓
Feb 16    What's the bottleneck?    → Diversity-coupling tradeoff exposed
```

The search strategy evolved from **top-down derivation** (Jan 17) through
**empirical grounding** (Jan 23–25) to **measurement-driven evolution**
(Feb 15–16). Each phase was necessary: the CT vocabulary informed the
wiring design; the wiring framework enabled the Wolfram classification;
the classification enabled the coupling spectrum; the coupling spectrum
enabled coupling-as-fitness; the production run revealed the structural
tension that now defines the next phase.

---

## Where We Are (Feb 16)

The production run answered several of the open questions from Feb 15
and sharpened the remaining ones.

### Answered

1. **Does coupling scale?** — Yes. The evolution reliably achieves
   mean-coupling=1.0 and coupling-cv=1.0 (full satisfaction) by gen 12.
   Coupling is *easy* to evolve.

2. **Temporal schedules?** — Yes. The evolved 28-step schedule is more
   structured than any hand-designed pattern: conditional(8) → msb(2)
   → bit5(1) → expansion(4) → adaptation(1) → conditional(6) →
   addself(6). Evolution finds temporal structure we wouldn't design.

3. **Coupling ↔ Class IV?** — This is the bad news. Higher coupling
   comes at the cost of diversity (0.89 → 0.36). The spacetime shows
   barcode-like striping. Coupling tends to synchronise bitplanes,
   which suppresses the phenotype variety that Class IV requires.

### The Central Problem

**Coupling and diversity are in tension.** Carry-chain coupling
(sigil-add-mod) works by propagating information from bit *n* to bit
*n+1*. But this propagation also *correlates* the bitplanes — when
bit 3 flips, the carry chain tends to flip bits 4, 5, 6 too. Correlated
bitplanes produce fewer distinct sigil values, which is exactly what
the diversity metric measures.

This isn't a bug in the verifier spec. It's a property of the physics:
**modular addition couples bits but also synchronises them.**

### Strategic Options

The question is now: **can we couple bitplanes without synchronising
them?** Three broad approaches:

#### A. Different coupling mechanisms

Carry-chain coupling (mod-256 addition) is one way to couple bitplanes.
It creates band-diagonal MI (adjacent bits only) but also synchronises.
Alternatives:

- **XOR-based coupling**: `sigil-xor` mixes bits without carry
  propagation. Couples all bitplanes equally (not band-diagonal) but
  shouldn't synchronise — XOR preserves entropy.
- **Rotation-based coupling**: Bit rotation (`rotate-left`,
  `rotate-right`) couples all bitplanes via position-shifting. The
  R110+Rotate hybrid was tested (Feb 15) but not with evolution.
- **Conditional coupling**: Only couple when a diagnostic threshold is
  crossed. The evolved schedule already does this temporally (addself
  only in 6/28 steps). Could be made spatial: couple only at domain
  boundaries or active computation sites.
- **Asymmetric coupling**: Couple upward (low → high bits) but not
  downward, or vice versa. Prevents feedback loops that drive
  synchronisation.

**Experiment**: Run evolution with each coupling mechanism separately,
compare diversity-coupling Pareto frontiers.

#### B. Reformulate the fitness landscape

The current verifier spec treats diversity and coupling as independent
band-score objectives. But the production run shows they're anti-
correlated. Options:

- **Composite metric**: Replace separate diversity + coupling with a
  single "coupled-diversity" score that rewards *diverse coupling
  patterns* rather than just high MI. E.g., the number of distinct
  coupling matrix shapes across runs, or the entropy of the coupling
  matrix itself.
- **Novelty search**: Instead of (or alongside) satisfaction, reward
  TPGs that produce coupling patterns unlike anything in the population
  archive. This would push exploration away from the "full coupling,
  low diversity" attractor.
- **Constrained optimisation**: Set coupling as a constraint (≥ some
  threshold) rather than a maximised objective, then optimise diversity
  subject to that constraint. This prevents the Pareto front from being
  dominated by maximum-coupling solutions.

**Experiment**: Try coupling-as-constraint (minimum MI ≥ 0.05) with
diversity as the primary objective.

#### C. Change the computational substrate

The diversity-coupling tension might be specific to 8-bit sigils with
mod-256 arithmetic. The carry chain in 8-bit addition is short enough
that one operation can propagate through all bits. Alternatives:

- **Larger sigils**: 16-bit or 32-bit sigils would give carry chains
  that can't reach all bits in one step. Local coupling without global
  synchronisation.
- **Multi-layer**: Separate the "computation layer" (Rule 110 dynamics)
  from the "coupling layer" (inter-bitplane communication). Run them
  on different timescales.
- **Asymmetric bit roles**: Designate some bits as "coupling channels"
  and others as "computation channels." Only arithmetic operations
  affect coupling channels; only bitwise operations affect computation
  channels.

These are larger changes. Probably not the next thing to try, but worth
keeping in mind if approaches A and B don't break the tradeoff.

#### D. Investigate whether the tradeoff is real

Before investing in new mechanisms, verify that the diversity-coupling
tension isn't an artifact:

- **Run coupling stability analysis** on the best TPG. Does the low
  diversity persist across seeds, or is it seed-dependent? Use
  `scripts/tpg_coupling_stability.clj`.
- **Inspect the coupling matrix** of the best TPG. Is it band-diagonal
  (carry-chain signature) or uniform? If uniform, the coupling may be
  trivial (all bits identical) rather than structured.
- **Compare to hand-designed R110+AddSelf**. The hybrid-110-addself
  wiring achieved coupling while preserving Class IV (BpDF=0.69, 15
  particles). Does the evolved TPG do better or worse than that
  baseline?
- **Check whether the barcode is in the genotype or the phenotype.**
  The spacetime image shows striping in both, but if the genotype is
  already barcoded then the problem is in the TPG's operator sequence,
  not in the coupling mechanism.

**This is probably the right first step.** Understand the failure
before trying to fix it.

### Recommended Next Actions

1. **Diagnostic deep-dive** (approach D): Run stability analysis,
   inspect coupling matrix, compare to R110+AddSelf baseline.
   Cost: ~1 hour compute + analysis. High information value.

2. **XOR coupling experiment** (approach A): Add `sigil-xor` as a
   wiring operator, run 10-gen evolution with just XOR coupling (no
   addself), check diversity-coupling tradeoff.
   Cost: ~30 min compute. Tests whether the tradeoff is carry-specific.

3. **Coupling-as-constraint** (approach B): Change verifier spec so
   coupling is a floor (≥0.05) not a band, make diversity the
   maximised objective. Re-run 50-gen evolution.
   Cost: ~2-3 hours compute. Tests whether the Pareto structure changes.

These three are independent and could run in parallel.

---

## Gaps in the Record

Things that would strengthen the narrative going forward:

- **Dead ends aren't marked.** The CT sprint (Jan 17, 48 commits) produced
  scaffolding — which parts are load-bearing and which were abandoned?
  The git log doesn't distinguish.

- **No hypothesis → result annotations.** Commits describe *what* was
  built, not *what was learned*. The barcode collapse commit (`bf1d68d`)
  is a rare exception.

- **Quantitative milestones aren't searchable.** When did Class IV
  detection accuracy go from 33% to 67%? When did coupling MI first
  exceed 0.01? These numbers live in reports but not in structured data.

- **Branch strategy is minimal.** Only 3 feature branches created; most
  work goes to main. The "I tried X and it didn't work" signal is lost.

The evidence tracking infrastructure coming online in futon3c could
address these gaps with structured experiment records that complement
rather than replace the git log.

---

## Commit Density

Burst days correspond to phase transitions:

| Date | Commits | Event |
|------|---------|-------|
| Jan 25 | 63 | Wiring breakthrough + health classification |
| Jan 17 | 48 | Category theory sprint |
| Jan 23 | 22 | P0 gate + excursions |
| Jan 26 | 17 | Nonstarter system |
| Feb 15 | 16 | SCI detection + coupling evolution |
| Jan 24 | 11 | Xenotype architecture |
| Feb 8  | 10 | CT mission diagrams |
| Feb 16 | 1 | Production run results + diversity-coupling tradeoff |

---

*History reconstructed from the git log on 2026-02-15.
Updated 2026-02-16 with production run results.
Future pivots should be recorded as they happen.*
