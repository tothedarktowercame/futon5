# Strategy: Building Computational Intelligence via Pattern Lifts

This document describes the forward strategy for the futon5 project and
its relationship to the broader futon ecosystem. It picks up where
HISTORY.md leaves off (Feb 16, 2026).

---

## The Goal

Build computational intelligence that is **provably able to find good
solutions across domains** — not by optimising within any single domain,
but by developing a methodology of representational lifts that transfers
structural knowledge between domains.

Good CA runs, effective ant colonies, and whatever comes next are
*validation evidence* that the methodology works. They are not the goal.

## The Contrast

Anthropic's approach to computational intelligence: read the internet,
do massive training, select character attributes → produce a general
intelligence. Requires a datacenter.

This approach: define representational lifts between structured layers,
use a small set of ground-truth mappings (hexagrams) as a skeleton for
embedding domain-general patterns, run the resulting configurations
across multiple domains, ratchet the results. Requires a laptop.

Both are searching for computational intelligence. The question is
whether structural transfer through carefully designed lifts can achieve
what brute-force scaling achieves through statistical regularities.

---

## The Architecture

```
PATTERN LAYER (futon3 library, 729 patterns, textual)
    │
    │  pattern → exotype mapping (learned, via hexagram skeleton)
    ↓
EXOTYPE LAYER (8-bit local evaluation regime)
    │
    │  exotype → xenotype lift (the hard part)
    ↓
XENOTYPE LAYER (36-bit global physics)
    │
    │  xenotype → domain instantiation
    ↓
DOMAIN EXECUTION (MMCA, ants, future domains)
    │
    │  evaluation + ratchet
    ↓
EVIDENCE → feeds back to pattern layer
```

### What each layer means

**Pattern**: A textual description of a situation and response, in
IF/HOWEVER/THEN/BECAUSE structure. Domain-general. "When the creative
force descends and the receptive rises, cultivate the meeting point."
Or: "Create a dedicated adapter that translates between interfaces."

**Exotype** (8-bit): A *local evaluation regime*. In the MMCA domain,
this is the rule a specific cell uses — rotation, match-threshold,
update-prob, mix-mode. In the ants domain, this is the AIF config
delta for a specific agent. The exotype says: **in this situation,
apply this pattern.**

**Xenotype** (36-bit): The *global physics*. The complete declaration
of which exotypes apply where and when:
- 8 bits for the IF condition (preconditions / context)
- 8 bits for the HOWEVER condition (exceptions / constraints)
- 8 bits for the THEN action (what to do)
- 8 bits for the BECAUSE justification (why this works)
- 4 bits for the PHENOTYPE (observable consequences / next steps)

The xenotype is a policy declared in advance: given conditions A use
pattern P1, given conditions B use pattern P2. The simulation then
runs under this policy.

**Domain execution**: The xenotype instantiated in a specific substrate.
In MMCA: a spacetime field evolving under the declared physics. In ants:
a colony of cyberants running under AIF deltas derived from the policy.

### The critical property

**The xenotype must transfer.** The same 36-bit declaration, run through
different domain instantiations, should produce structurally analogous
results. 泰 (Peace/dynamic equilibrium) as a xenotype should produce
edge-of-chaos dynamics in MMCA and balanced gather-return cycles in
ants. This has been demonstrated for individual hexagrams. The strategy
is to make it systematic.

---

## The Two Problems

### Probably not that hard: pattern → exotype mapping

The 64 I Ching hexagram patterns are ground truth: each has rich
structured text AND a known exotype encoding. The 256 II Ching exotype
patterns expand this skeleton in a disciplined way. Together, ~320
paired (text, encoding) examples.

**Approach**: Learned embedding from structured text to 8-bit space,
using the hexagram skeleton as anchors. For each section
(IF/HOWEVER/THEN/BECAUSE), compute textual similarity to the hexagram
corpus, interpolate the known encodings. Lightweight, runs on a laptop,
produces an exotype for any pattern in the library.

This gives 729 patterns × exotype encodings = 729 candidate
configurations testable in any domain with a xenotype interpreter.

### Where the search lives: exotype → xenotype lift

An exotype says what to do locally. A xenotype says what to do
globally — it's the policy that maps conditions to local actions.

The default xenotype is trivial: 36 bits of zeros — do nothing,
let local physics run. Any 36-bit configuration is a valid xenotype.
Producing one is easy. Producing one that exhibits computational
intelligence is the search.

This is not a bottleneck — it's the subject matter. The search for
computational intelligence IS the search for xenotypes that produce
interesting, transferable dynamics. The questions the search must
navigate:

1. **Composition**: How do multiple exotypes combine into a coherent
   global physics? The I Ching has a theory of this (hexagram
   composition, trigram interactions, lines changing). Does it
   generalise?

2. **Situational binding**: The xenotype's IF/HOWEVER/THEN/BECAUSE
   bits encode *when* each exotype applies. How are these conditions
   defined in a domain-independent way? In MMCA, "IF" might mean
   "entropy exceeds threshold." In ants, it might mean "cargo level
   above 0.5." The binding must be abstract enough to transfer.

3. **Completeness**: A xenotype must handle all situations the domain
   can present. In MMCA, the wiring system runs for N generations and
   needs a rule at every step. Missing conditions → undefined behavior.
   How do we ensure coverage?

4. **Evaluation**: How do we know a xenotype is good? The SCI detection
   pipeline works for MMCA. The AIF metrics work for ants. But
   xenotype quality must be assessed *across* domains. A xenotype that
   produces Class IV in MMCA but starving ants has not transferred.

### What we've already demonstrated

Almost all of the pieces exist:

- **Pattern → exotype**: Hand-authored for 64+256 patterns, with
  explicit experimental predictions for cross-domain transfer (泰
  has both ant-interpretation and mmca-interpretation).

- **Exotype execution in MMCA**: The wiring runtime, xenotype
  interpreter, and TPG runner all work. 50-generation evolution
  runs with coupling fitness complete in hours.

- **Exotype execution in ants**: Domain transfer from MMCA to ants
  demonstrated (L5-creative → exploration advantage in patchy
  environments).

- **SCI detection**: 8-component Wolfram class detection, coupling
  spectrum, health classification — comprehensive evaluation for
  MMCA domain.

- **Evolved temporal schedules**: TPG evolution discovers operator
  sequences (28-step cycles) that outperform static assignments.

- **Cross-bitplane coupling**: Carry-chain arithmetic creates
  structured coupling between representation layers.

What's *not* demonstrated: the lift working systematically (beyond
individual hexagrams), the evaluation working across domains
simultaneously, the ratchet closing the loop automatically.

---

## The Learning Loop (Revised)

The original learning loop (Jan 2026) stalled because evaluation was
too weak and the wiring diagram space was too narrow. Both problems
are now substantially improved. The revised loop:

```
1. EMBED:  Map patterns to exotypes via hexagram skeleton
2. LIFT:   Compose exotypes into xenotype policies
3. RUN:    Execute xenotypes in multiple domains
4. EVALUATE: SCI detection (MMCA), AIF metrics (ants), ...
5. RATCHET: Retain xenotypes that transfer, discard those that don't
6. LEARN:  Update the embedding and lift based on what transferred
           └→ return to step 1
```

### What makes this different from the first attempt

- **Evaluation is richer**: SCI detection pipeline, coupling spectrum,
  health classification — all built since the first attempt.

- **The pattern space is larger**: 729 patterns vs a handful of
  hand-designed wirings.

- **The skeleton exists**: The hexagram/exotype ground truth provides
  a coordinate system for the embedding, not just isolated points.

- **Multi-domain validation**: Transfer to ants is demonstrated.
  The ratchet can require cross-domain consistency, not just
  single-domain performance.

### What still needs building

1. **The embedding function**: pattern text → exotype encoding via
   hexagram skeleton. Small ML problem, ~320 training examples.

2. **Lift strategies to test**: exotype set → xenotype policy. The
   search for computational intelligence lives here. Multiple
   approaches to explore:
   - Combinatorial (I Ching composition rules)
   - Evolutionary (TPG-style, let evolution find good compositions)
   - Learned (train on the hexagram xenotypes that work)
   - Hybrid (use composition rules to constrain evolution)

3. **Cross-domain evaluation**: A single score or Pareto vector that
   captures "this xenotype transferred." Requires running in MMCA
   and ants (at minimum) and comparing structural properties.

4. **The ratchet**: An automated gate that admits xenotypes to the
   "known good" set only when they demonstrate transfer. The
   healthcheck ratchet infrastructure (Feb 9) is a starting point.

---

## Priorities

### Near-term: the embedding prototype

Build `pattern->exotype` using the hexagram skeleton. Test it on
patterns with known domain interpretations (the ants patterns, which
have hand-authored AIF deltas — compare the learned encoding to
the hand-authored one).

This unblocks the entire pipeline: once every pattern has an exotype,
they're all executable.

### Medium-term: the lift

Explore multiple exotype → xenotype lift strategies. The I Ching
itself provides one (hexagram composition from trigrams). TPG
evolution provides another (evolve the policy). The question is not
"can we produce xenotypes" (any 36 bits will do) but "which
strategies find xenotypes that exhibit computational intelligence
and transfer across domains." This is the search.

### Long-term: the closed loop

Wire embedding → lift → execution → evaluation → ratchet into an
automated loop. The loop IS the computational intelligence. Good CA
runs and effective ant colonies are its output, not its input.

---

## Relationship to HISTORY.md

HISTORY.md documents what happened: 13 pivots across 6 phases, from
AIF scoring through the diversity-coupling tradeoff. The history
tells us that:

- Top-down theory (CT) generates vocabulary but not solutions
- Empirical grounding (wiring breakthrough) is necessary
- Measurement (coupling spectrum) enables evolution
- Evolution finds the Pareto frontier, including its tradeoffs

The strategy builds on all of this but reframes the objective. The
MMCA work is a case study in the methodology. The coupling-diversity
tradeoff (Pivot 13) is a test case for whether the methodology can
navigate domain-specific tensions — not a problem to solve by
hand-tuning the MMCA pipeline.

If the methodology works, it will find solutions to the coupling-
diversity tradeoff that we wouldn't design by hand, because it will
bring structural knowledge from other domains (via the xenotype lift)
that applies to MMCA in ways we can't predict in advance.

---

*Strategy drafted 2026-02-16. To be revised as the embedding and
lift prototypes develop.*
