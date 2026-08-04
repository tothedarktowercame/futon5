# TN-baldwin-selection-strategy — closing Part III's second extension

**Date:** 2026-08-04
**Status:** strategy, nothing implemented
**Companions:** `TN-metaca-baldwin-micro-pilots.md` (what the exotype objective can and
cannot do), `TN-eig-definition.md` (a falsified design and what survived it)
**Source of the argument:** `holes/tech-notes/paper/draft6.tex`, Part III closing and
Future Work

---

## 1. The argument, recovered

Part III establishes a paired negative and positive. A composition whose condition is
**blind** to the phenotype reproduces the Part II dial exactly, linear in its duty cycle —
so conditional composition is not by itself a new mechanism. A condition that **reads** the
phenotype exceeds that dial, by margins growing with the width of the neighbourhood read;
but an otherwise identical gate reading a phenotype **frozen at t\*** does not exceed it at
all. Correlated firing alone buys nothing: what the reading gate exploits is that what it
reads is **current**.

Two extensions are left open.

**Extension 1 — from a gate to a regulator.** *"The conditions tested here are fixed
families chosen by us; nothing yet adjusts its own condition in response to how it is
doing. The 2014 ancestor of this substrate already contains the relevant construction — a
switch that fires only where the local context carries no new information — so a system
that tunes its own gate is buildable on this lattice rather than hypothetical, and the
question is whether such a system settles anywhere in particular on the calibrated scale."*

**Extension 2 — the Baldwin effect, named as the more interesting.** *"As it stands the
substrate instantiates the Lamarckian arm alone: acquired state writes into the heritable
layer… The Baldwin effect requires something this substrate lacks — a selective filter, so
that plasticity alters what selection sees and the heritable layer follows without direct
transcription. Adding selection over a population of these architectures would supply that
ingredient, and because both arms would then be expressible in one family they could be
placed on the single measure used throughout this paper: the reach of damage-spreading,
calibrated against rules of undisputed regime. Whether direct write-back and
selection-mediated assimilation arrive at the same place on that scale, and by what route,
is a question the family is now equipped to ask."*

**The question is not whether a picture looks structured. It is whether a system settles
anywhere in particular on the calibrated damage scale, and whether the two arms arrive at
the same place.**

---

## 2. Where the 2026-08-03/04 work actually sat

Honest placement, because it is not where we thought.

- **Everything built was the Lamarckian arm.** Transfer fraction, neighbour-agreement
  blend, exotype-as-propensity, EIG-gating — all direct write-back. No selective filter, no
  population, no fitness. Extension 2 was never approached.
- **Every knob was one we set.** `c`, `β`, `μ`, `q`, `κ`. Extension 1 asks for a system that
  computes its own; we never built one, so we were never positioned to ask whether it
  settles.
- **The measure drifted.** The paper's scale is damage-spreading reach. The afternoon used
  checkpoint entropy over exotype kinds; the evening used dominance shares from a ratchet
  script. Neither is the calibrated scale, and the ratchet is a house metric invented for
  regression-catching, not a scientific instrument.

### What nonetheless carries forward

1. **Damage re-established as the instrument**, per layer. The known-good regime is
   characterised: phenotype damage 9.0–9.5 with genotype and exotype damage exactly 0 —
   one critical layer riding on two stable ones.
2. **A closed door, established cheaply.** The exotype scoring objective cannot host
   epistemic dynamics: chaos's claims are *confirmed* at every active observation, so it is
   a self-consistent fixed point and no reweighting displaces it (micro-pilot 7). This kills
   a whole family of designs, including three of my own.
3. **An unexpected convergence.** Falsifying the EIG design showed that `blend_rule` acts
   only where neighbours disagree — which is the construction Part III names as the
   ingredient for a self-tuning gate. It is now ported and working in futon5. We arrived at
   the 2014 engine's answer from the other direction.

---

## 3. Correction: "locally measured damage reach" does not exist

An earlier suggestion of mine — that a cell adjust its gate in response to its own damage
reach — is not implementable as stated. **Damage is counterfactual**: it requires a control
run to difference against, and a cell cannot fork reality.

What *is* locally computable is the **two-replica proxy**, the standard way damage spreading
is estimated without a control. Neighbouring cells are already near-copies, so the rate at
which a cell and its neighbours diverge estimates the same quantity, locally and causally.

This is a proxy for the calibrated scale, **not the scale itself**, and the two must not be
conflated in any claim. Extension 1 remains open; this note pursues Extension 2, which does
not require a cell to measure anything counterfactual.

---

## 4. The design: add selection in the exotype layer

Joe's proposal, 2026-08-04. The mapping onto the paper's requirement is exact.

| paper's requirement | substrate element |
|---|---|
| plasticity | **exotype** — acquired, horizontally transmitted, not heritable |
| plasticity alters what selection sees | exotype changes a cell's performance against its own preferences |
| heritable layer follows **without direct transcription** | genotypes change by **differential reproduction**, not by being written into |

**The missing ingredient is one step:** a cell copies a **neighbour's genotype** with
probability weighted by relative fitness. Genotypes then change because some cells do
better, not because anything wrote into them.

Today `apply-exotype` writes the exotype's propagator straight into the cell's own genotype.
That is direct transcription — the Lamarckian arm, precisely as the paper says. The Baldwin
arm keeps the exotype as plasticity and routes heritable change through selection instead.

**The population is the lattice itself** — cells competing with neighbours — rather than an
outer loop over whole architectures. This keeps the object a CA, keeps damage measurable by
the existing instrument, and keeps runs cheap.

### Fitness must not be edge-of-chaos

**This is the load-bearing constraint.** If fitness rewards damage reach, the system will
converge on damage reach and the result is a definition unfolding, not a finding.

The model already carries a non-circular fitness: `efe/preferences`
`{:rule-change 0.15 :hunger 0.05}` — the C term, conservative rule change and low hunger.
Selecting on free energy against those preferences says **nothing about dynamical regime**.
If the population nonetheless converges to a particular point on the damage scale, that is a
real result.

Any future change to the fitness definition must be checked against this constraint
explicitly, in writing, before it is run.

---

## 5. The experiment

**Two arms, one measure.**

- **Lamarckian arm** — current dynamics: exotype writes into genotype directly.
- **Baldwin arm** — exotype affects fitness; genotype changes only by neighbour-copying
  weighted by fitness; direct write-back disabled.

**Measure:** reach of damage-spreading, per layer, calibrated against rules of undisputed
regime — the paper's own scale, not entropy, not dominance share, not the ratchet.

**The primary test is convergence, not appearance.** Initialise from several different
starting regimes (ordered, critical, disordered) and ask whether each arm settles at a
particular damage reach. *"Whether such a system settles anywhere in particular."*

### Predictions, to be checked before building where possible

1. **Convergence.** Each arm settles to a reach largely independent of initialisation. If
   final reach simply tracks initial reach, nothing is being regulated and the design fails.
2. **Non-vacuity.** The converged reach is not trivially predictable from the fitness
   definition — checkable by varying the preference parameters and confirming the converged
   reach does not move proportionally.
3. **The paper's question.** Do the two arms arrive at the same place, and by what route?
   Both answers are publishable; same place is the stronger claim about the family, different
   places is the more informative about mechanism.

### Falsifiers

- Final damage reach tracks initial condition → no regulation, design dead.
- Converged reach moves with the preference parameters → fitness is doing the work, result
  vacuous.
- The Baldwin arm never assimilates — genotype distribution stays at drift — → the selective
  filter is too weak to be the missing ingredient, which is itself a finding about the
  substrate.

---

## 6. What this does NOT claim

- **It does not fix the exotype scoring objective.** Chaos remains a self-consistent fixed
  point of `pattern-score`. The design routes around this: selection does not score exotypes
  against a preference model, so the self-consistency trap does not arise. That is an
  avoidance, not a repair.
- **It does not close Extension 1.** A self-tuning gate still requires a cell to respond to
  something it can measure; the two-replica proxy is a candidate, untested.
- **It does not inherit tonight's blend result as support.** Slice 9–11 are Lamarckian-arm
  measurements. They establish that the mechanism works and that μ is the dominant lever;
  they say nothing about selection.
- **No claim about which arm is "right".** The paper asks where they arrive, not which wins.

---

## 7. Method note, recorded deliberately

Three designs were specified and falsified in one session — random-neighbour transfer,
coherent-offset transfer, and EIG-gating — two of them by computation before implementation
and one by looking at the picture after. The pattern that worked was **predict, then run**;
the pattern that failed was asserting a mechanism confidently enough that building it looked
cheaper than checking it.

Two specific errors worth not repeating: a 4-seed run reported an interior optimum that
vanished at 12 seeds (argmax of noise); and a citation of `compose-rules-blend` as evidence
for a spatial blend was simply wrong — it composes two rules' outputs on one sigil. The
correction came from a subagent instructed that pushing back was wanted, and it saved a
fourth wasted scan.

**Before anything in section 5 is built, its falsifier in that section should be run first.**
