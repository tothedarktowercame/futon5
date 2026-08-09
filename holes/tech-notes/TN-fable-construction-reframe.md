# The construction reframe — attacked, computed, and largely upheld

**Reviewer: fable, 2026-08-08.** Fifth note in the series. This one contains
computation, not only argument: the fixed-byte alphabet, its absorption
structure, and the quiescence intersection were enumerated exactly from the
vocabulary in `mmca-clj/src/futon5/exotype/grid.clj` (semantics per
`generator.clj`'s `rule-permute`, Wolfram convention per `ca/core.clj`;
scripts in this session's scratchpad, `alphabet.py`). Sanity check: the
computation reproduces every documented immune-byte count in `grid.clj`
(collapser 4, even4 16, even8 8, even1 2, even44 4) exactly.

## 0. Summary

The reframe is sound as a change of question, and the computation comes out
strongly in its favour — better than the claim as posed:

- Problem (b) — immunity = ROM — dissolves under a two-register reading:
  the rule layer is the **medium**, the state layer is the **content**, and
  the medium's unwritability is a feature. Verified concretely (§2).
- Problem (a) — the intersection — is non-empty, graded, and contains one
  outstanding object: **byte 77**, fixed under every all-even operator in the
  vocabulary, whose quiescent language has entropy ≈ 0.694 bits/cell, which
  self-settles from any state in ≤ 6 steps, and which confines single
  perturbations to depth ≤ 1 (§3).
- The theorem is more than a storage-alphabet spec: the fixed sets form a
  **materials catalogue** — storage media, wire media, and blank — and one
  operator (`even8`) fixes all three at once, which collapses the whole
  construction problem onto a frozen rule layer (§4).
- The empirical obstacle (settled regions thinning to width ~2) is
  diagnosed, not fatal: it is exotype churn, not state-layer fragility (§4).
- The minimal demonstration is not the wire; the wire is step 3 of a
  4-rung ladder whose rung 0 this note completes (§5).
- What the reframe does NOT do is dissolve the impossibility — it concedes
  it (§1). And the highest-risk primitive is not memory, as feared; on my
  analysis it is composition — read *gating* in particular (§5).

## 1. Question 1 — sound, or relocated?

**Sound, with one honesty condition and one relocation.**

The honesty condition: the reframe does not dissolve the L\*/τ_c
impossibility — it **abandons the question the impossibility is about**.
That claim binds a *cell that must estimate in order to steer*. Construction
with external layout and external verification has no estimating cell; it
also has no endogeneity. The series' original question — "can the system
find its operating point from inside, no external objective" — is being
retired, not answered. Say that in the paper. The minimum-externality ledger
from the earlier verdict note covers it: construction moves every search,
layout, and verification bit to the external side, which is maximal
externality and perfectly good science under a different heading. The
damage-reach concession is correct and clean: the objection to twin runs was
always availability-to-a-cell, never validity — as an external construction
test it is the right tool, and I withdraw nothing by agreeing.

The relocation: what the construction programme must now do that control
never did —

1. **Persistence of specific configurations**, not statistics. A regime is
   translation-invariant; a structure is *somewhere*, and erosion of that
   particular somewhere is failure even if the bulk statistics are unchanged.
2. **Termination of writes.** Control tolerated perpetual activity; a write
   must provably re-settle. (Measured below: for byte 77, in ≤ 6 steps.)
3. **Composition.** Read must not destroy memory, writes must not leak past
   their target, wires must not widen. None of this had a control-programme
   analogue, and it is where constructive CA work historically bleeds.

One genuine retro-diction credit: if the live-tail-into-frozen-region event
is a write operation, then the control programme was using an *event count*
as a *state variable* — rare, discrete, localised — which is exactly the
profile of the noisy, weakly-steerable feedback signal every closed-loop
arm measured. And the conjugacy result in your own Part I (live twin and
dead twin exactly conjugate as isolated systems, so position-intrinsic
properties carry no outcome information) is independent support for
"structure, not bulk parameter." The reframe explains the failures better
than the failures explained themselves.

## 2. Question 2 — is immunity fatal? No. Here is the object.

Your fear conflates two registers. Separate them and (b) dissolves:

- **The rule byte is the MEDIUM** — the local physics of a region. For a
  memory region you want the medium *unwritable*: a write that altered the
  medium would change what the stored content means. ROM-ness of the rule
  is not the defect; it is the specification of a stable material.
- **The CONTENT is the state configuration** within the region — and it is
  writable through ordinary state dynamics at the boundary, exactly your
  contextual-stability escape. The escape is real, and it is not exotic: it
  is how every still-life in Life and every ether-particle system in rule
  110 stores information.

**The characterised object** (what you asked for instead of a gesture):
a memory is a triple (σ, r, w) where

1. σ is all-even and r ∈ Fix(σ) — the medium survives its own operator;
2. r's *quiescent set* Q(r) = {(a,b,c) : r(a,b,c) = b} defines a subshift
   of finite type (allowed triples on the de Bruijn graph), and that SFT
   has positive entropy — the medium admits many frozen words;
3. w is a word of that SFT — the content. Capacity of an n-cell region =
   entropy × n bits, minus O(1) guard cells.

The count theorem (2^k fixed rules for k even cycles) sizes the **menu of
media per operator**, not the storage. Storage is the SFT entropy of the
chosen medium, and for byte 77 it is ≈ 0.694 bits/cell — a 100-cell patch
holds ~69 bits, not log2(2^k) bits. This is the correction to "the theorem
is the specification of a storage alphabet": it is the specification of a
**materials list**; the storage alphabet is the quiescent language of the
material you pick.

Two further computed facts sharpen the write story:

- **The fixed sets are absorbing, and globally attracting.** For every
  all-even operator in the vocabulary, every one of the 256 bytes reaches
  the fixed set under the stochastic propagator, and fixed bytes are exact
  fixed points of every application. A held all-even operator does not
  merely tolerate its media — it *repairs toward them*. (Caveat, measured:
  repair is not target-deterministic. Every Hamming-1 deviation from byte
  77 can be absorbed back to 77 **or** to a different alphabet byte —
  {77,78}, {77,177}, {77,141}… depending on operator and bit. So
  rule-layer noise degrades the medium into a *different* medium rather
  than restoring it. Under a frozen all-even exotype there is no rule-layer
  noise at all and the point is moot; under churn, media are metastable.)
- **Write-by-operator-substitution exists but is stochastic.** The union
  graph of single applications over the 12 selectable operators is strongly
  connected on all 256 bytes, so any medium is reachable from any other —
  but the propagator's position draw is random, so rule-layer writes are
  rejection-sampling (drive, check, retry), not deterministic. Usable for
  *laying out* media; the wrong channel for content. Content writes belong
  to the state layer, where dynamics are deterministic.

## 3. Question 3 — the intersection, computed

Fixed bytes across the selectable vocabulary: {77, 78, 89, 90, 101, 102,
113, 114, 141, 142, 153, 154, 165, 166, 177, 178} (16 media; per-operator:
collapser 4, even4 16, even8 8, even1 2). Quiescent-language analysis of
each (entropy in bits/cell; "words" = distinct ring configurations up to
n = 8):

- **77** — |Q| = 6, entropy **0.694**, 23 words. Q is everything except
  000 and 111: the quiescent language is the run-length-≤2 shift. Fixed
  under **all four** all-even operators. Measured dynamics: 2000/2000
  random 40-cell rings converge to a quiescent word, max transient 6 steps
  (mean 2.1); a single flip of a quiescent word never alters cells beyond
  ring-distance 1 (500 trials: depth 0 in 336, depth 1 in 164, ≥2 never).
  Self-settling, positive-capacity, perturbation-confining. The medium.
- **141, 78** — entropy 0.406, 17 words each. Second-tier storage media.
- **90** — entropy 0 in the quiescent sense, but this is the interesting
  zero: rule 90 is the additive XOR rule — fixed physics, perpetually
  active states, perturbations propagate at speed 1 in both directions and
  superpose linearly. Not a memory. **A wire.** See §4.
- **178, 177, 154, 166, 114, 102…** — entropy 0, only uniform-ish words:
  blank media.
- **113** — empty quiescent SFT: no frozen word exists at all. Proof that
  the intersection is not automatic; a fixed rule with no settled
  phenotype, exactly your (a).

**Answer: the intersection is non-empty, it is graded, and it is large
enough — because "alphabet size" was the wrong measure.** As media, 16
bytes; as storage, byte 77 gives ~0.69n bits per n-cell region, which
scales with region size. Problem (a) is settled affirmatively, with 113 as
the exhibited near-miss that shows the two conditions are genuinely
independent.

(Convention note for the checker: all of this is in the post-2026-07-15
Wolfram convention; the computation reproduces `grid.clj`'s documented
immune-byte counts exactly, which is the validation that the semantics were
read correctly.)

## 4. The materials catalogue, and the empirical obstacle diagnosed

The single most consequential computed fact: **`even8`'s fixed set contains
77, 90, and 178 simultaneously.** Storage medium, wire medium, and blank —
all exactly invariant under one operator. So a construction needs no
exotype heterogeneity at all:

> Lay `even8` everywhere (uniform exotype, exactly fixed rule layer).
> Write the *rule field* as the circuit diagram: 77-segments where memory
> should sit, 90-segments where signals should travel, 178 where nothing
> should happen. The state layer is then the only dynamical layer: 77
> regions freeze onto content words, 90 regions run hot forever.

This dissolves your empirical obstacle rather than fighting it. The epoch
table (settled widths collapsing to ~2 by t = 800 at β = 8, κ = 0.1) is a
measurement of **exotype churn**: under adoption, cells keep receiving
non-even operators — including `:identity`, the most disruptive in the
family — which kick rule bytes out of every alphabet, and the phenotype
transience follows. It is not a fact about the media themselves: at the
state layer, perturbations of a 77-domain confine to depth ≤ 1 (measured),
and at the rule layer a held all-even operator is exactly inert on its
fixed bytes. **Settled regions are transient at those parameters because
nothing there holds a medium in place; construction holds the medium in
place by layout.** Coexistence — settled and active regions, indefinitely —
is then not a regime to steer toward but a property of the rule-field
drawing, which is where five notes of failed steering say it belonged.
Your `:heterogeneous-fixed` harness arm is already the right chassis: it is
the arm where operating points are laid out and held.

One honest limit of the measured depth-≤1 result: it is for isolated flips
allowed to heal. A 90-channel hammers its 77-neighbour continuously; under
sustained drive the effective penetration could exceed 1 before healing.
That is precisely what rung M1 below measures, and 1–2 sacrificial guard
cells per boundary are the expected mitigation.

## 5. Question 4 — the minimal demonstration is not the wire

The wire is rung 3 of a ladder, and testing it first would confound three
untested primitives. Also, as specified, your wire test is too weak: in a
live lattice damage spreads generically, so "perturbation reaches the
target" is what chaos gives you for free; and on a ring there are two
paths. The ladder, each rung with its falsifier:

- **M0 — media exist.** Done in this note, by enumeration: (even8, 77,
  run-limited word) with self-settling and confinement measured. Cost: two
  scripts. (Falsifier would have been an empty §3 table; 113 shows it was
  live.)
- **M1 — memory persists under fire.** Uniform even8; rule field
  77…77|90…90|77…77; left patch initialised to content word w; run ~2000
  steps with the 90-channel hot against its boundary. Pass: interior
  content (behind ≤ 2 guard cells) intact at horizons ≫ the epoch-table
  lifetimes; fail: erosion at bulk rates, or sustained-drive penetration
  ≫ 1. **This is the critical rung** — it is where your empirical obstacle
  either dissolves as diagnosed or kills the programme.
- **M2 — write terminates and discriminates.** Twin-run (legitimate here):
  a designed boundary sequence drives patch content w_A → w_B and
  re-settles (expected ≤ ~6 steps per written cell); the matched twin
  without the sequence stays w_A. Fail: non-termination, or
  content-independent scrambling.
- **M3 — the wire**, strengthened: perturbation at the far side of the
  90-channel produces a *content-dependent*, distance-timed (speed-1
  arrival) alteration at the target patch edge, which does **not** occur
  when the channel segment is replaced by 77 (whose depth-≤1 confinement
  blocks transmission — the channel-cut control is a medium swap, which is
  the clean version) and does not arrive via the other ring path (blank
  178 as the back-path). Rule 90's linearity is an asset: signals
  superpose, so content-dependence is checkable exactly by XOR.

Cheapest full version: ~200 cells, ~2000 steps, one seed family, all four
rungs on the same layout. Days, not weeks, and every rung has a named
falsifier.

**Where I expect trouble — inverting your fear ordering.** Memory (your
(b)) is, on the computed evidence, the *solved* primitive. The soft spot is
**read gating**: a 77-patch adjacent to a 90-channel broadcasts its edge
state into the channel continuously (linearity again — an asset for the
wire is a liability for the memory, which cannot stop transmitting), and
nothing in a linear channel can conditionally *not* read. Selective read,
and any logic, needs nonlinearity — a third material or an engineered
boundary motif — and the catalogue has candidates (141, 165 have mixed
quiescent/active structure) but nothing verified. The wire will likely
pass; composition is where this substrate has to prove it is more than a
parts drawer.

## 6. Question 5 — prior art placement

Where each primitive already lives:

- **Settled regions / interfaces**: these are *domains* and *particles* in
  the Crutchfield–Hanson computational-mechanics sense (invariant spatial
  languages and their boundaries, formalised early 1990s). Your quiescent
  words are domain configurations; adopt the vocabulary — it comes with
  theorems and a filtering method (domain transducers) you can reuse for
  detection.
- **Memory as settled structure + write by collision**: Conway Life still
  lifes and glider salvos; von Neumann's 29-state constructor and Codd's
  8-state simplification (write = construction arm ingress into quiescent
  background; your write primitive is a 1D miniature of exactly this);
  Langton loops for self-maintenance.
- **1D computation**: Cook's rule 110 universality — memory lives in a
  periodic ether and moving particle trains, i.e., contextual stability,
  never rule immunity; particle/collision computing generally (Adamatzky's
  collision-based computing collection; Fredkin–Toffoli billiard-ball
  model, which even drops wires).
- **Noise**: the sobering shelf. Reliable computation in noisy 1D CA is
  possible but notoriously hard — Gács's positive-rates construction —
  and Toom's rule is the standard 2D robustness result. This literature is
  what governs your substrate **when exotype churn is on**: churn is
  rule-layer noise, media become metastable (§2), and naive structures
  erode, which is what your epoch table measured. With churn off
  (constructed layouts), none of it applies, which is the cheap corridor
  the ladder walks through.
- **Two-layer, rewritable rule field**: the genuinely distinctive part.
  Nearest relatives: von Neumann's genotype/phenotype separation (your
  rule layer is a genotype field), EvCA / cellular programming
  (per-cell rules, but evolved by an external GA, not rewritten by an
  in-lattice operator), structurally dynamic CA (Ilachinski–Halpern —
  topology rewrites rather than rule rewrites). **A stochastic permutation
  operator on the rule byte whose all-even members have absorbing immune
  sets — a self-repairing materials system — I do not know a precedent
  for.** Status: not already known, not known impossible; the components
  are classical, the two-layer materials theorem is yours. That is the
  paper-able core, and §3–§4 are its worked example.

## 7. Question 6 — should the control programme finish first?

Not either/or, and mostly no. Do both of these, in this order:

1. **M1 now** — it is the critical path of the live programme, it is
   days-cheap, and M0 (its precondition) is already done above.
2. **L\*/τ_c from the logs in parallel** — still worth one day, for two
   reasons that survive the reframe: it closes the strict-locality series
   with a stated, checkable result instead of a fourth abandoned pivot
   (the series has reframed three times; closing loops is what makes the
   eventual paper honest), and τ_c is *reused* — it is the ambient erosion
   clock that M1's persistence numbers need as their baseline comparison.

What I would no longer prioritise: everything on the strict-locality
retirement list stays retired, and the L\*/τ_c result should be *reported*,
not *acted on* — no fifth controller gets built on it. The construction
programme is now the main line; the control series ends as the measured
explanation of why construction was necessary.

---

*Reproducibility: `alphabet.py` and the verification snippets live in the
session scratchpad; both take seconds. Every derived operator property was
cross-checked against the documented table in `grid.clj` and matches. The
depth/convergence numbers are 2000- and 500-trial Monte Carlo on 40-cell
rings, seeds 1 and 2.*
