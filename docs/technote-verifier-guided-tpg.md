# Technote: Verifier-Guided Tangled Program Graphs for Meta-Cellular Automata

**Date:** 2026-02-15
**Status:** Preparation round. Architecture formalization.
**Lineage:** SFI proposal (TPG), futon5 MMCA (exotypes/xenotypes), ChatGPT handoff sketch
**Excursion target:** P2 (Abstract Control Layer) — extends E1 xenotype motifs

---

## 1. Problem Statement

The current MMCA controller has two layers:

1. **Exotypes** (fast, per-cell): 36-bit context → physics rule (0-255) → kernel + params.
   Deterministic, local, already working.

2. **Xenotypes** (slow, population-level): weight vectors specifying target bands for
   entropy, change, autocorrelation, diversity. Evolved via tournament selection on
   scalar fitness aggregated over full runs.

The xenotype layer has three structural weaknesses:

- **Flat representation**: a weight vector cannot express conditional control logic
  ("if high entropy, apply conservation; if low entropy, apply expansion").
- **Global fitness**: the scalar score over a full run cannot distinguish between
  a controller that navigates well and one that happens to start in a good region.
- **No modularity**: every xenotype parameter affects every cell equally. There is
  no mechanism for specialized sub-controllers.

**Proposal**: Replace the flat xenotype layer with a Tangled Program Graph (TPG)
that routes among specialized operator teams based on locally computed diagnostic
features, scored by locally computable verifiers rather than global reward.

---

## 2. Formalization

### 2.1 State

The MMCA state at discrete time t:

```
S_t = (G_t, P_t, R_t)

where:
  G_t : Σ^N        — genotype string (N sigils, each from alphabet Σ of 256 symbols)
  P_t : {0,1}^N    — phenotype row (binary)
  R_t : [0,255]^N  — per-cell physics rule assignment
```

The **diagnostic feature vector** at cell i, time t:

```
D_t(i) = (
  H_local(i,t),      — local entropy over neighborhood window [i-w, i+w]
  Δ_local(i,t),      — local change rate (Hamming distance to t-1)
  ρ_local(i,t),      — local autocorrelation (correlation with t-τ)
  σ_local(i,t),      — local diversity (unique symbols in window)
  φ(i,t),            — phenotype-genotype coupling (phenotype-family bits)
  λ(i,t)             — damage spreading (sensitivity: perturb cell, measure radius)
)
```

Each component is computable from the local neighborhood — no global aggregation needed.

### 2.2 Operators

An **operator** O_k modifies the rule assignment or kernel parameters at a cell:

| Operator class | What it modifies | futon5 mapping |
|----------------|-----------------|----------------|
| Rule parameter | mutation-rate, bit-bias, strictness | `energy->kernel-params` |
| Coupling mode | how global/local compose | bend-mode {:sequential :blend :matrix} |
| Update schedule | probability of cell update | :update-prob in sigil->params |
| Kernel switch | which kernel family is active | `family->kernel` mapping |
| Structural rewrite | composition mode of multi-rule cells | `make-composite-exotype` |

There are K operators: O_1, ..., O_K. In the minimal prototype, K = 8
(one per hexagram family), with each operator parameterized by its energy mode.

### 2.3 Programs

A **program** is a small deterministic function:

```
π : D → (action, bid)

where:
  D    = diagnostic feature vector (ℝ^d)
  action ∈ {team_ref | operator_ref}  — route to another team or execute an operator
  bid  ∈ ℝ                           — priority (higher bid wins within a team)
```

Implementation: each program is a **linear classifier with threshold**:

```
π(D) = {
  action = a_π                            (fixed per program)
  bid    = w_π · D + b_π                  (linear combination of diagnostics)
}
```

This keeps programs deterministic, inspectable, and cheap to evaluate. The weight
vector w_π and bias b_π are evolved; the action a_π is structural.

A team can also route to another team (forming the "tangled" graph structure).

### 2.4 Teams

A **team** T = {π_1, ..., π_m} is a set of programs that compete:

```
T(D) = argmax_{π ∈ T} bid(π, D)
```

The winning program's action is followed: either execute an operator or pass
control to another team (which then runs its own competition).

### 2.5 TPG Structure

A **TPG** is a directed acyclic graph (with potential back-edges for routing):

```
TPG = (V, E)

V = V_team ∪ V_action
  V_team   = {T_0, T_1, ..., T_p}    — team nodes (T_0 is root)
  V_action = {O_1, ..., O_K}         — operator/action nodes

E = { (T, π, target) | π ∈ T, target ∈ V_team ∪ V_action }
```

**Execution**: Given diagnostic D at cell i:
1. Start at root team T_0
2. T_0 selects winning program π*
3. If π* routes to team T_j, recurse to T_j(D)
4. If π* routes to operator O_k, apply O_k at cell i
5. Maximum depth bound prevents infinite routing

**Structural invariant**: every team must have at least one program routing to
an action node (reachability). This is enforced during evolution.

### 2.6 Verifiers

A **verifier** V_j : (S_t, i, window) → ℝ produces a locally computable score.
Each verifier has a **specification** (target band):

```
spec_j = (center_j, width_j)

V_j is satisfied when |V_j(S_t, i, w) - center_j| < width_j
```

**Core verifiers** (mapping to existing xenotype scoring):

| Verifier | Computes | Current futon5 source |
|----------|---------|----------------------|
| V_entropy | Neighborhood Shannon entropy | `metrics/summarize-run` → avg-entropy-n |
| V_change | Local Hamming distance | `metrics/summarize-run` → avg-change |
| V_autocorr | Temporal autocorrelation | `metrics/summarize-run` → temporal-autocorr |
| V_diversity | Local symbol diversity | `metrics/summarize-run` → avg-unique |
| V_stasis | Time to fixation (local) | `first-stasis-step` |
| V_phenotype | Phenotype-genotype coupling | phe-entropy, phe-change |

**New verifiers** (TPG-specific):

| Verifier | Computes | Why it matters |
|----------|---------|---------------|
| V_damage | Perturbation spreading radius | Lyapunov exponent proxy; edge-of-chaos indicator |
| V_coherence | Fine-coarse agreement (multiscale) | Validates that structure exists at multiple scales |
| V_invariant | Structural property preservation | Conservation laws; testable local constraints |
| V_locality | Operator effect stays local | Confirms no pathological long-range coupling |

### 2.7 Fitness (Multi-Objective, Constraint-Based)

**No scalar reward.** Instead, fitness is a constraint satisfaction vector:

```
fitness(TPG) = (s_1, s_2, ..., s_J)

where s_j = fraction of (cell, time) pairs where V_j is satisfied
```

**Selection**: Pareto dominance.

TPG_A dominates TPG_B iff:
- For all j: s_j(A) >= s_j(B)
- For some j: s_j(A) > s_j(B)

Evolution uses **(μ+λ) with Pareto ranking**:
1. Parent population of μ TPGs
2. Generate λ offspring (mutation + crossover)
3. Rank all μ+λ by Pareto front
4. Select μ survivors from non-dominated fronts

This avoids the "reward hacking" problem of scalar fitness: a TPG cannot
improve one verifier at the expense of others without losing Pareto rank.

**Specification-driven**: the verifier specs are the experiment's hypothesis.
For example, the tai-zone specification:

```clojure
{:entropy  [0.6 0.35]    ; "interesting" entropy range
 :change   [0.2 0.2]     ; moderate change, not stasis or confetti
 :autocorr [0.6 0.3]     ; temporal structure present
 :diversity [0.4 0.3]}   ; symbol diversity moderate
```

This IS the current xenotype spec format. The TPG proposal doesn't change
the specifications — it changes how the controller achieves them.

---

## 3. Evolution Operators (for TPGs, not MMCA)

### 3.1 Program Mutation

- **Weight perturbation**: w_π ← w_π + N(0, σ) for each weight
- **Action reassignment**: change which team/operator the program routes to
- **Bias shift**: b_π ← b_π + N(0, σ_b)

### 3.2 Team Mutation

- **Program addition**: insert a new random program into the team
- **Program deletion**: remove a program (maintaining reachability invariant)
- **Program replacement**: swap one program for a mutant copy

### 3.3 Graph Mutation

- **Team addition**: add a new team node with random programs
- **Team deletion**: remove a team, rewire its incoming edges (maintaining reachability)
- **Edge rewiring**: change a program's target (team → team, team → action)

### 3.4 Crossover

- **Team-level**: swap entire teams between two TPGs
- **Program-level**: swap individual programs between matching teams

### 3.5 Constraints on Evolution

- **Reachability**: every team must reach at least one action node
- **Depth bound**: maximum routing depth D_max (proposed: 4)
- **Size bound**: maximum teams T_max (proposed: 8), max programs per team M_max (proposed: 6)
- **Determinism**: all operations are seeded; given RNG state, evolution is reproducible

---

## 4. Connection to futon5 Architecture

### 4.1 What TPG Replaces

```
BEFORE (current):
  xenotype spec (flat weight vector)
    → score-run (global aggregation)
      → scalar fitness
        → tournament selection

AFTER (TPG):
  TPG graph (teams + programs + routing)
    → per-cell verifier evaluation (local)
      → constraint satisfaction vector
        → Pareto selection
```

### 4.2 What TPG Preserves

- **Exotype system**: unchanged. Per-cell 36-bit context → physics rule.
- **Hexagram families**: unchanged. TPG operators correspond to hexagram families.
- **Kernel specs**: unchanged. Operators select/modify kernel specs.
- **Wiring diagram formalism**: TPGs ARE wiring diagrams. Teams are morphisms,
  programs are internal arrows, operators are terminal objects.
- **Invariants 1-4**: fully preserved (exotype invariants).
- **Invariants 5-8**: extended (TPG wirings replace xenotype wirings).

### 4.3 CT Interpretation

A TPG has a natural reading as a wiring diagram in the existing CT framework:

- **Objects** (ports): diagnostic feature type D, operator type O_k
- **Morphisms**: teams as transformations D → D (routing) or D → O_k (action)
- **Composition**: sequential team routing = morphism composition
- **Identity**: a team that passes through unchanged = id_D

The composition laws from `futon5.xenotype.category` apply directly. The
validation infrastructure (mission.clj, 8/8 checks) can validate TPG wirings.

### 4.4 Where SMT Fits

SMT (Satisfiability Modulo Theories) enters at verification time:

- **Constraint encoding**: verifier specs encode as SMT assertions over bounded
  integer/real arithmetic
- **Reachability checking**: "does this TPG always reach an action node?" is
  decidable by SMT over the finite graph
- **Specification checking**: "given these diagnostic ranges, which operator
  does the TPG select?" can be answered exactly by SMT over linear programs
- **Invariant checking**: "does this operator preserve the structural invariant?"
  can be checked by SMT over the kernel spec arithmetic

Tool: Z3 via Clojure interop (z3-clj or shell-out). The SMT layer provides
**static guarantees** that the TPG satisfies structural constraints before
running expensive MMCA simulations.

### 4.5 Where JAX Fits

JAX enters at the diagnostic and embedding layers:

- **Batched verifier evaluation**: evaluate all cells in parallel via JAX vectorization
- **Differentiable diagnostics**: if diagnostics are differentiable w.r.t. kernel
  params, gradient information can guide TPG evolution (Lamarckian hint)
- **Eigendecomposition**: the hexagram lift (context → 6x6 matrix → eigenvalues →
  hexagram lines) becomes batched and differentiable via `jax.numpy.linalg.eigh`
- **Embedding space**: TPG structures can be embedded in a learned manifold for
  structural similarity search (extends proof-bridge pipeline to TPG space)

JAX is an acceleration layer, not a structural dependency. The system must work
without JAX first (pure Clojure), then JAX adds speed and differentiability.

---

## 5. Minimal Implementable Prototype

### Phase 0: Diagnostic Infrastructure (pre-TPG)

**Goal**: Make all verifiers computable per-cell, not just per-run.

1. Refactor `metrics/summarize-run` to produce per-cell metric tensors
2. Add local windowed versions of entropy, change, autocorr, diversity
3. Add damage-spreading probe (perturb one cell, measure affected radius)
4. Validate: local metrics correlate with global metrics on existing runs

**Deliverable**: `futon5.mmca.local-metrics` namespace
**Budget**: ~200 lines of Clojure
**Mana source**: extends Mission 0 instrumentation

### Phase 1: TPG Data Structure + Execution

**Goal**: Implement TPG representation and deterministic execution.

5. Define TPG EDN format (teams, programs, edges, actions)
6. Implement program execution (linear classifier → action + bid)
7. Implement team execution (max-bid selection)
8. Implement graph routing (root → team → ... → action, with depth bound)
9. Integrate: given MMCA state + TPG, produce per-cell operator assignment

**Deliverable**: `futon5.tpg.core` namespace + EDN format spec
**Budget**: ~300 lines
**Validates**: TPG can route to different operators based on local diagnostics

### Phase 2: TPG-Driven MMCA Runner

**Goal**: Run MMCA with TPG controller instead of flat xenotype.

10. Replace `xenotype/score-run` with per-cell verifier aggregation
11. Wire TPG execution into the MMCA generation loop
12. At each cell, each timestep: compute diagnostics → route through TPG → apply operator
13. Collect per-cell verifier satisfaction rates

**Deliverable**: `futon5.tpg.runner` namespace
**Budget**: ~200 lines
**Validates**: TPG controller produces valid MMCA trajectories

### Phase 3: TPG Evolution

**Goal**: Evolve TPGs via Pareto selection on constraint satisfaction.

14. Implement TPG mutation operators (program, team, graph level)
15. Implement Pareto ranking
16. Implement (μ+λ) evolution loop
17. Seed initial population with hand-crafted TPGs matching hexagram families

**Deliverable**: `futon5.tpg.evolve` namespace
**Budget**: ~400 lines
**Validates**: evolution discovers TPGs that satisfy verifier specs

### Phase 4: Comparison Experiment

**Goal**: Compare TPG controller against flat xenotype on matched budget.

18. Run E1 Stage 1 protocol (tai-zone effect size) with both controllers
19. Match compute budget (same number of MMCA generations evaluated)
20. Compare: constraint satisfaction rate, trajectory health, regime stability

**Deliverable**: experiment report
**Exit criterion**: TPG achieves higher constraint satisfaction OR reveals
structural insight about regime navigation

---

## 6. Success Criteria

### Quantitative

| Criterion | Measure | Threshold |
|-----------|---------|-----------|
| Constraint satisfaction | Fraction of (cell,time) satisfying all verifiers | TPG > flat by d > 0.3 |
| Regime stability | Timesteps before leaving tai-zone | TPG > flat by 20%+ |
| Specialization | Distinct operator distributions per team | Shannon entropy of team usage > 1.5 bits |
| Modularity | Ablation: remove one team | Performance degrades only in matching regime |
| Inspectability | Graph size | < 20 nodes total |
| Determinism | Repeated runs with same seed | Bit-identical output |

### Qualitative

- TPG graph structure can be described in natural language ("team A handles
  high-entropy regions by applying conservation; team B handles low-entropy
  by applying expansion")
- Verifier scores align with human regime judgments (extends E1 scorer alignment)
- The system runs without JAX (pure Clojure); JAX is optional acceleration

---

## 7. Risk Assessment (Sospeso)

| Risk | Confidence | Mitigation |
|------|-----------|------------|
| Per-cell diagnostics are too noisy for routing | 0.6 | Spatial smoothing window; temporal EMA |
| TPG evolution is too slow to converge | 0.6 | Seed with hand-crafted TPGs from hexagram families |
| Pareto selection loses pressure in high dimensions | 0.8 | Start with 3-4 verifiers, not all |
| Graph routing adds too much overhead per cell | 0.8 | Depth bound 4; cache routing decisions |
| SMT integration is yak-shaving | 0.6 | Defer SMT to Phase 5; use runtime assertions first |

**Sospeso estimate**: Phase 0-1 can proceed at p=0.8 (rescope-to-safety).
Phase 2-3 should carry sospeso at p=0.6 (pay 0.4·C to mana pool).
Phase 4 depends on Phase 3 outcomes.

---

## 8. Relation to Excursion Roadmap

```
Excursion 1 (P1: xenotype motifs)
  ↓ establishes: effect size, hexagram taxonomy

Excursion 2 (P1 ext: 256 sigils)
  ↓ provides: full operator vocabulary for TPG actions

THIS WORK (P2 precursor: TPG architecture)
  ↓ replaces: flat xenotype with graph-structured controller
  ↓ requires: Phase 0 diagnostics (extends Mission 0)
  ↓ validates: P2 claim (CT spec instantiates to MMCA)

Excursion 3 (P2: Abstract Control Layer)
  ↓ TPG wirings ARE the CT spec instantiation
  ↓ same TPG structure transfers to AIF ants (E4)
```

The TPG work can begin in parallel with E1 Stages 1-3 (Phase 0: diagnostics),
then interleave with E1 Stages 4-7 (Phases 1-3: TPG implementation).
Phase 4 (comparison) runs after E1 Stage 2 provides the baseline.

---

## 9. Decisions and Open Questions

### Decided (2026-02-15)

**D1. Routing frequency: per-generation.**
The TPG routes once per generation, producing a single operator assignment
for the entire grid at each timestep. This keeps cost linear in T (not N×T),
and the diagnostics are computed as generation-level summaries (spatial
aggregates over the grid). Per-cell routing deferred to Phase 5+ if
per-generation proves too coarse.

**D2. Hexagram alignment: 1:1 pilot.**
The 8 operator classes map directly to the 8 hexagram families. This gives
a clean interpretive frame (team specialization has a name and a physics
meaning from day one). Evolution can later relax this constraint by allowing
operator reassignment mutations, but the pilot holds it fixed.

### Open

1. **Program representation**: Linear classifiers are simple but limited.
   Should programs be lookup tables over discretized diagnostics instead?
   (More inspectable, finite state, but combinatorial growth with d.)

2. **Multiscale**: When does the second scale enter? Phase 2 (as a verifier
   comparing fine/coarse) or Phase 5 (as a separate TPG layer)?

3. **SMT scope**: What exactly should be verified statically? Reachability is
   cheap. Full spec checking requires encoding the linear programs. Where is
   the cost/benefit line?

---

## Appendix A: Glossary

| Term | Definition | futon5 equivalent |
|------|-----------|------------------|
| TPG | Directed graph of teams and actions | Wiring diagram |
| Team | Set of competing programs | Morphism (CT) |
| Program | Deterministic classifier: diagnostics → action + bid | Internal arrow |
| Operator | Modifies CA rule/params at a cell | Kernel spec mutation |
| Verifier | Locally computable constraint checker | Band-score in xenotype.clj |
| Diagnostic | Feature vector computed from local neighborhood | Metrics from metrics.clj |
| Pareto front | Set of non-dominated solutions | Multi-objective selection |
| Specification | Set of verifier target bands | Xenotype spec |

## Appendix B: EDN Format Sketch

```clojure
{:tpg/id "tpg-001"
 :tpg/version 1

 :teams
 [{:team/id :root
   :programs
   [{:program/id :p1
     :weights [0.3 -0.2 0.5 0.1 0.0 -0.4]  ; one weight per diagnostic dimension
     :bias 0.1
     :action {:type :team :target :conservation-team}}
    {:program/id :p2
     :weights [-0.3 0.4 -0.1 0.2 0.3 0.0]
     :bias -0.1
     :action {:type :team :target :expansion-team}}
    {:program/id :p3
     :weights [0.0 0.0 0.0 0.0 0.0 0.0]
     :bias 0.0
     :action {:type :operator :target :adaptive}}]}

  {:team/id :conservation-team
   :programs
   [{:program/id :p4
     :weights [0.5 0.0 0.3 0.0 0.0 0.0]
     :bias 0.0
     :action {:type :operator :target :conservative}}
    {:program/id :p5
     :weights [-0.5 0.0 -0.3 0.0 0.0 0.0]
     :bias 0.0
     :action {:type :operator :target :consolidation}}]}

  {:team/id :expansion-team
   :programs
   [{:program/id :p6
     :weights [0.0 0.5 0.0 0.3 0.0 0.0]
     :bias 0.0
     :action {:type :operator :target :creative}}
    {:program/id :p7
     :weights [0.0 -0.5 0.0 -0.3 0.0 0.0]
     :bias 0.0
     :action {:type :operator :target :transformative}}]}]

 :operators
 {:creative       {:family 0 :energy :peng}
  :conservative   {:family 1 :energy :lu}
  :adaptive       {:family 2 :energy :ji}
  :transformative {:family 6 :energy :peng}
  :consolidation  {:family 7 :energy :lu}}

 :config
 {:max-depth 4
  :diagnostic-window 3    ; radius for local metric computation
  :routing-frequency :per-generation}}  ; D1: per-generation for pilot
```
