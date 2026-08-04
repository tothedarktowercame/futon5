# M-formal-patterns

**Status:** CHARTERED 2026-07-17 (oxf-claude-2 + Joe). Logic model established over a
long working session; slices below are the handoffs.

**One-line:** Formalize design patterns as *signed graphs* whose coherence is governed by
the (proved) signed-cycle balance theorem, at two self-similar scales — inside a single
pattern, and across a pattern language — with the MetaCA propagator as the shared formal
core.

---

## The core insight (Joe, 2026-07-17)

**A pattern and a pattern language are not that different.** The same balance theory
applies at both scales:

- **Within a pattern:** the slots IF / HOWEVER / THEN / BECAUSE / NEXT-STEPS form a graph
  (star, or K5, or something between — see S0). Its signed cycles decide whether the
  pattern is *internally coherent* — whether it can stabilize at all.
- **Across a language:** patterns link through their NEXT-STEPS (validation experiments
  that chain into other patterns). Its signed cycles decide whether the language admits a
  consistent joint verdict — whether the community can settle which patterns are salient.

Same theorem, two scales. This is the self-similar tower (cf. pheno/geno/exo) with the
balance theory as the scale-free invariant.

**Structural identity, not analogy.** The five slots
`IF (left) / HOWEVER (right) / THEN (center) / BECAUSE (ego) / NEXT-STEPS (phenotype)`
are exactly the five reads of a MetaCA cell update
(`readLeft / readRight / readCenter / <ego> / readPhenotypeContext`). A pattern *is* a
MetaCA cell, structurally. So the propagator theorems apply to a pattern's internal graph
directly, not by loose correspondence.

## Corrected semantics (Joe, 2026-07-17 — supersedes an earlier reading)

- **THEN** is the proposed *resolution* of the held tension (IF vs HOWEVER).
- **NEXT-STEPS are NOT the resolution.** They are the empirical experiments you run to
  gather evidence of the pattern's *salience* (香) — that it does in fact resolve or
  ameliorate the tension. They are the **ehipassiko** ("come and see") protocol for the
  pattern. Enact them; where salience is confirmed, hold; where disconfirmed, revise.
- NEXT-STEPS **chain through other patterns** (validating A may require enacting B). This
  is what makes the language a graph: the edges are **validation dependencies**, not
  tension-propagations.
- **Definition of a pattern (Joe):** "a learned generalization arising from a review
  process ... a candidate becomes a pattern only when it has survived repeated
  enactment-and-review cycles in which its failure modes are recorded and used to revise
  it." A pattern is a **fixed point of the enactment-and-review (revision) dynamic** — a
  *mogul*: sustained by ongoing re-enactment, not a static object.

## What balance means here

Each validation-link carries a sign: chaining to the next pattern **confirms** salience
(+) or **supersedes** it (−: "these two are alternatives — evidence for one is evidence
against the other"). The theorem's colouring = each pattern's **validation status**
(salient / superseded).

- **Balanced (even # of − links on every cycle):** a globally consistent verdict exists;
  patterns partition into two coherent camps; the community can settle.
- **Frustrated (odd):** a self-undermining validation loop — evidence for A, routed around
  the cycle, disconfirms A. Rock-paper-scissors of salience. No stable verdict; the
  patterns stay perpetual candidates.

**Practical claim (the payoff):** a community that can never settle *which* of several
competing practices is "the real pattern" is sitting on an **odd validation-cycle**. The
meta-pattern (propagator) intervention is to flip one validation-link — find where two
practices are treated as alternatives but are actually compatible (or vice versa) — which
changes the parity and makes a stable verdict reachable.

## The War-Machine bridge

A Peeragogy pattern is a **mini-War-Machine**: its NEXT-STEPS are its **mission menu**, and
it selects among them by **G over policy** (expected free energy). The formal link Joe
intuited: **a joint G-minimum across a pattern network exists iff the network is balanced.**
A held tension is an antiferromagnetic bond; a frustrated (odd) cycle has no ground state
that satisfies every bond, so the mini-WMs never jointly settle — no consent reachable.
Balance is the exact condition for coherent joint commitment.

## Formal grounding (what already exists)

- **PROVED, machine-checked** (mathlib4 `DarkTower/Patterns/Propagator.lean`, codex-4,
  reviewed + `lake build` green, no sorry/axiom):
  - T1 `hasAlternatingColouring_iff_cycleType_even` — fixed point iff fixed-point-free and
    all cycles even.
  - T2 `alternatingColouring_true_card_eq_four` — every fixed point has λ = 1/2.
  - T3 the count is 2^(#cycles).
- These are the **all-negative** special case (ν = ¬ on every edge). The pattern setting
  needs **arbitrary edge signs** (Harary/Aracena signed-graph balance). That generalization
  is S1 and it also retroactively proves the phenotype-dependent-sign coupling result.
- **Literature (from deep-research pass):** Aracena (2008) *Bull. Math. Biol.* 70(5) for
  signed-cycle fixed points; Harary balance; OEIS A001818 for the count. Cite, don't claim.

---

## ❄ FROZEN 2026-08-04 — no new slices until the reboot register closes

**No findings, no sweeps, no experiments.** Scope is restricted to the eight registered
items in `TN-baldwin-reboot.md` §10 (N1–N4, H1–H4) until they are discharged. A new
observation does not open a new thread: it is evidence for a registered item, or it is
parked in §12 with a home. **Do not dispatch a slice while this notice stands.**

N1 is decided (§11): the 2015 bug is *promoted*, not retracted — it becomes a short warm-up
section before Part I, because working out how it actually worked was the route to
everything else. What gets deleted is the claim that the permutation family generalises it.

## Substrate ground truth — READ BEFORE DISPATCHING (added 2026-08-04)

The propagator's *measured* behaviour is not what the mission's prose, the paper, or
`efe/fixed-model` assume. Two documents hold the ground truth:

- **`holes/F-what-the-propagator-actually-does.md`** (2026-07-16) — the genotype layer is
  autonomous and never reads the phenotype; its only variation source is the propagator;
  the actual Emacs bug is `k ↦ max(k-1,0)`, **non-injective, and therefore not in the S₈
  family the census enumerates**.
- **`TN-baldwin-reboot.md`** (2026-08-04) — the per-cell RNG draws are spatially
  degenerate (all cells act in lockstep); `:identity` is the *most* disruptive propagator,
  not the least; the Baldwin arm's genotype update is copy-only from a size-1 set.

Between 2026-07-17 and 2026-08-04 the first of these was cited by no file in the
repository, and slices 8, 10 and 12 each independently rediscovered a fact it already
records — each at the cost of a full dispatch/park/review round-trip.

**Gate G1:** every exotype handoff packet must cite these two documents and state which of
their measured facts the design depends on. The design step is where this check belongs;
by review time the run has already happened.

**Gate G2:** any arm claiming to evolve a layer must name that layer's **variation** source
and its **selection** source separately. If either is "none", the arm is inert before it runs.

**Gate G3:** before reporting a measured rate, compute the mechanism's floor and ceiling and
check the number lies between them. Two reported results have failed this.

**Gate G4:** a handoff reporting a test gate must name the namespaces it ran, not only the
counts. Slice 12 reported "exotype suite: 38 tests, 120 assertions"; the full surface is
57 tests / 1260 assertions. A count cannot reveal what was omitted.

---

## Slices (handoffs)

**S0 — DERIVE the pattern graph (with Joe; logic model, no code).**
Pin the internal graph of a single pattern. Which slot-pairs of
{IF, HOWEVER, THEN, BECAUSE, NEXT-STEPS} actually relate, and with what sign? Candidates:
star K1,4 (identity + 4 spokes), K5 (all 10 edges), or a determined subgraph
(IF—HOWEVER tension −; THEN—IF, THEN—HOWEVER resolution; BECAUSE—THEN justification;
NEXT-STEPS—THEN validation). K5 is non-planar and has rich cycle structure; the honest
question is which edges are *real*. Output: the canonical signed pattern-graph, with sign
semantics per edge. **Gate:** the edge set is justified from pattern semantics, not assumed.

**S1 — Generalize the theorem to arbitrary edge signs (Lean; bell codex-4).**
Extend T1/T2/T3 from "ν = ¬ everywhere" to "a sign function on edges, product around each
cycle." This is Harary/Aracena balance and it is the formal core for BOTH the pattern side
AND the phenotype-dependent-sign coupling. codex-4 already holds the cycle-following
machinery. **Gate:** `lake build`, no sorry/axiom, proof references `cycleType`; must NOT
reduce to `decide` over a finite case.

**S2 — Pattern-as-signed-graph type + internal-coherence check.**
Formalize a single pattern as the S0 signed graph; "pattern is internally coherent"
= its signed graph is balanced (by S1). Depends on S0 + S1. Include the K5 finite check
(2^10 sign assignments — enumerable) as a concrete sanity artifact.

**S3 — Language-as-cascade + cross-pattern validation graph.**
The inter-pattern validation-dependency graph; balance = consistent joint verdict. The
self-similar level up from S2 (same theorem, patterns as nodes). Connect to the existing
cascade work (`futon2` cascade-tokamak; lon-claude-6's `check_cascade_sat.clj`, which
already found the odd self-loop-at-`:dead` frustration case).

**S4 — Empirical: the salience-validation graph of a REAL pattern language.**
Build the validation-dependency graph of an actual Peeragogy pattern language (from the
patterns' NEXT-STEPS and their cross-references), assign signs, and CHECK for frustrated
(odd) cycles. This is "come and see" applied to the theory itself — the modeling claim
(pattern → signed graph) gets tested against real data. **Gate:** the sign assignment is
derived from the patterns' actual text, not fitted to produce a desired answer.

**S5 — War-Machine bridge (most speculative).**
Formalize "joint G-minimum exists iff balanced" for a network of mini-WMs whose missions
are NEXT-STEPS. Connects the balance theorem to the AIF/WM G-over-policy machinery.
Depends on S1–S3. Flag as the frontier, not the foundation.

---

## Honest bounds

- Everything above is the **Boolean/binary** case (each link ±, each verdict two-valued) —
  where T1/T2/T3 live. Richer pattern responses (P beyond Bool) are follow-on, not v1.
- The map **real pattern → signed graph** is a MODELING claim (the morphism). It is
  well-motivated by structure but unverified until S4. Do not present it as measured.
- S0 (the actual edge set) is genuinely open — K5 is a hypothesis, not a result. The whole
  mission's concreteness rests on getting S0 right, so it is DERIVE-with-Joe, not a bell.

## Provenance

Emerges from the M-propagators / MetaCA paper thread (futon5, branch
M-propagators-2026-07-15) and the morphism discussion of 2026-07-17. Related:
[[project_aif_ants_port]] (the ant learning brain, codex-5, is the transfer-via-morphism
sibling), [[project_war_machine]], [[project_peeragogy_mission_reframe]],
[[feedback_dhamma_as_structural_prior_art]].
