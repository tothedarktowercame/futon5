# Chapter 0: Active Inference as a Wiring Diagram

*Design memo — conceptual exploration for a diagram calculus*

---

## 0. Framing

Active Inference (AIF) proved something: that you can build a coherent
wiring diagram that couples inference, action, and constraint under a
single variational principle. This is valuable. It is not, however, the
discovery of "the theory of agency" any more than the Wright Flyer was
the discovery of "the theory of flight." It is an existence proof that
a certain class of diagram works.

This memo treats AIF as the first member of a family. The goal is to
understand what kind of diagram it is, what invariants it relies on,
where those invariants come from, and what the rest of the family might
look like.

Throughout: "diagram" means a typed directed graph with boxes (roles),
wires (interfaces), and constraints (invariants). Not a picture — a
data structure with checkable properties.


---

## 1. AIF Characterized Diagrammatically

### 1.1 Core Roles (Boxes)

The standard AIF diagram has five roles, though they are rarely drawn
this cleanly:

| Role | What it does | Timescale |
|------|-------------|-----------|
| **Environment** | Evolves according to its own dynamics; partially observable | Exogenous |
| **Sensory surface** | Transduces environment states into observations | Fast |
| **Internal states** | Maintain beliefs (generative model); update via inference | Fast–medium |
| **Active surface** | Transduces selected actions into environmental effects | Fast |
| **Preferences** | Constrain what counts as viable / desirable | Slow–glacial |

The Markov blanket formalism groups Sensory + Active into "blanket
states," treating them as the interface membrane. Internal states are
"inside," environment is "outside."

### 1.2 Core Interfaces (Wires)

| Wire | From → To | Type carried | Direction |
|------|-----------|-------------|-----------|
| Observation | Environment → Sensory → Internal | Sensory data | Inward |
| Action | Internal → Active → Environment | Motor commands | Outward |
| Model update | Internal ↔ Internal | Belief revisions | Internal |
| Preference constraint | Preferences → Internal | Prior expectations | Downward |
| Prediction | Internal → Sensory | Expected observations | Outward (comparison) |

The prediction wire is often drawn as "prediction error" flowing inward,
but this is an implementation detail. The structural fact is that
internal states generate expectations that are compared against
observations. Whether the comparison happens at the sensory surface or
internally is a design choice, not an invariant.

### 1.3 Timescale Separations

This is where AIF's real structure lives, and where most expositions
are weakest. The diagram implicitly relies on at least four timescales:

1. **Perception** (fastest): belief updating within a single
   observation cycle. Must converge before the next observation arrives.

2. **Action selection** (fast–medium): policy evaluation and selection.
   Operates over short planning horizons.

3. **Learning** (medium–slow): parameter updating in the generative
   model. Integrates evidence across many perception-action cycles.

4. **Preference / structure** (slow–glacial): changes to what the
   system considers viable. In biological systems this is
   phylogenetic. In institutional systems it is constitutional.

The critical constraint: **each timescale must be slow relative to the
one below it.** If preferences change as fast as actions, the system
has no stable notion of viability. If learning is as fast as
perception, the model overfits to noise. The diagram doesn't work
without these separations, but they are rarely stated as formal
requirements.

### 1.4 Implicit Assumptions (Usually Unstated)

1. **The boundary exists and is given.** The Markov blanket is assumed,
   not constructed. In biology, the membrane precedes the organism's
   inference. In AIF the blanket is a precondition, not an output.

2. **The generative model is adequate.** There exists an internal
   structure that is rich enough to track the relevant external
   structure. What "relevant" means is never formally specified — it
   is carried by the preference prior.

3. **The environment is stationary enough.** The world must not change
   faster than the fastest internal timescale, or the observation-
   action loop cannot close.

4. **Actions are reversible enough.** The system must be able to
   correct course. A single irreversible action that destroys the
   boundary (death) terminates the diagram. AIF has no formal
   treatment of irreversibility.

5. **Composition is not addressed.** Standard AIF describes a single
   agent with a single blanket. Multi-agent composition, nested
   blankets, and inter-agent coordination are extensions, not core.

6. **The variational objective is well-formed.** Free energy
   minimization presupposes that the KL divergence between the
   approximate posterior and the true posterior is meaningful. This
   requires that both distributions exist and are comparable.

### 1.5 Essential vs Contingent

**Essential** (remove it and the diagram class collapses):

- Boundary with controlled coupling (some inside/outside distinction)
- Bidirectional observation-action loop (sense AND act)
- Timescale separation (fast dynamics constrained by slow)
- Preference exogeneity (what-to-maintain is not freely writable)
- Internal structure that tracks external structure (some model)

**Contingent** (design choices within the diagram class):

- Probabilistic formalism (could be energetic, symbolic, topological)
- Free energy as the specific objective (could be other functionals)
- Variational inference as the update mechanism
- The specific G decomposition (epistemic + pragmatic value)
- Discrete time (continuous time versions exist)
- The Markov blanket as the specific boundary formalism
- Gaussian or categorical generative models


---

## 2. Invariants

What properties must any "viability-maintaining" diagram share with
AIF? These are the load-bearing walls. Everything else is furniture.

### I1: Boundary Integrity

There must be a distinguishable inside and outside with controlled
coupling. "Controlled" means: the interface has finite bandwidth. Not
everything outside is observable; not everything inside is expressible
as action.

**If removed:** No agent. No distinction between system and
environment. The "diagram" dissolves into undifferentiated dynamics.

**Formal shadow:** The boundary is a span in the category of
interfaces — an object with morphisms to both inside and outside that
factor all coupling through themselves.

### I2: Observation-Action Asymmetry

The system must both sense and act. These are different interfaces with
different types. Observation carries information inward; action carries
effect outward. They are not symmetric — observation is (mostly)
non-destructive; action changes the world.

**If removed:** A pure observer cannot maintain viability (it cannot
eat, move, or repair). A pure actor without observation is ballistic —
it cannot adapt to environmental change.

**What this rules out:** "Perception-only" theories of consciousness
that treat action as epiphenomenal. Also: "pure planning" systems that
model but never act.

### I3: Timescale Separation

Fast dynamics (perception, action) must be constrained by slow dynamics
(preferences, structure). There must be at least two timescales, and
they must be ordered: slow constrains fast, not the reverse.

**If removed:** If everything changes at the same rate, there is no
persistent structure to maintain. The system is a random walk with no
memory. More precisely: there is no distinction between "adapting to
the environment" and "losing your identity."

**What this rules out:** Systems where learning rate equals action
rate. Systems where "all parameters are free." Fully flat architectures
with no inductive bias.

### I4: Preference Exogeneity

The "what-to-maintain" signal must not be freely writable by the fast
dynamics. Preferences must enter the diagram from outside the
perception-action loop, or from a timescale so much slower that the
fast dynamics cannot effectively influence them within their operating
horizon.

**If removed:** Wireheading. The system discovers that rewriting its
own preferences is cheaper than satisfying them. In diagram terms:
there is a wire from Action → Preferences that should not exist. The
system shortcuts the loop by modifying the target rather than reaching
it.

**This is the most important invariant for engineering.** Every known
catastrophic alignment failure can be recast as a violation of I4.
Reward hacking, specification gaming, goal misgeneralization — all
involve the fast dynamics gaining write access to the slow constraints.

**The diagram test:** If you can trace a directed path from any Action
node to any Preference node that does not pass through the Environment,
the diagram has a wireheading vulnerability. The environment is the
only legitimate channel through which actions should (eventually,
slowly) influence preferences — through evolutionary selection,
institutional reform, or other glacial-timescale processes.

### I5: Model Adequacy

The internal structure must track relevant external structure well
enough for the system to persist. "Relevant" is defined by the
preferences: the model must represent the aspects of the environment
that matter for viability.

**If removed:** The system's internal structure becomes uncorrelated
with external threats and opportunities. Actions are random with
respect to viability. The system is "alive but not adaptive" — it
persists only by luck, not by competence.

**Note:** This is the weakest invariant. It's a sliding scale, not a
binary. Organisms with better models persist longer (on average).
There is no threshold of adequacy below which the diagram fails and
above which it works. This makes it hard to certify.

### I6: Compositional Closure

The diagram must be stable under its own operations. Acting must not
destroy the ability to sense. Learning must not overwrite the ability
to act. The diagram's dynamics must preserve the diagram's structure.

**If removed:** Self-destructive behavior. An action that blinds the
sensory surface. A learning update that zeros out the action policy. A
model update that destroys the model's own representational capacity.

**This is autopoiesis expressed as a diagram constraint.** The system's
operations must reproduce the conditions for those operations to
continue. In engineering terms: no component may have a failure mode
that destroys the monitoring system for that component.

### Invariant Interactions

The invariants are not independent. I4 (preference exogeneity) depends
on I3 (timescale separation) — preferences are "exogenous" precisely
because they operate on a slower timescale. I6 (compositional closure)
depends on I1 (boundary integrity) — you can't maintain the diagram if
the boundary dissolves. I5 (model adequacy) depends on I2
(observation-action asymmetry) — a model can only be adequate if there
is observation to calibrate it.

The dependency graph:

```
I1 (boundary) ← I6 (closure) depends on
I2 (obs-action) ← I5 (adequacy) depends on
I3 (timescales) ← I4 (exogeneity) depends on
```

I1, I2, I3 are foundational. I4, I5, I6 are derived.


---

## 3. The Design Space: Alternative Diagram Families

AIF occupies a specific point in a larger space. Here are three
neighboring points — diagram families that share some invariants with
AIF but differ structurally.

### 3.1 Autopoietic Diagrams (Varela/Maturana)

**Key difference from AIF:** The boundary is self-produced, not given.

In AIF, the Markov blanket is a precondition. In autopoietic theory,
the boundary (membrane, organizational closure) is the *output* of the
system's own dynamics. The system produces the components that produce
the boundary that defines the system.

| Feature | AIF | Autopoietic |
|---------|-----|-------------|
| Boundary | Given (Markov blanket) | Self-produced (organizational closure) |
| Internal model | Explicit (generative model) | Implicit (the organization IS the model) |
| Inference | Variational belief updating | None — structure is the "inference" |
| Action principle | Minimize free energy | Maintain organizational closure |
| Preferences | Explicit (prior preferences) | Implicit (viability = continued closure) |

**Invariants shared:** I1, I2, I3, I6. The autopoietic diagram
absolutely requires boundary integrity, observation-action coupling,
timescale separation, and compositional closure.

**Invariants differing:** I4 (preference exogeneity) doesn't apply in
the same way — there are no explicit preferences, so there is nothing
to "wirehead." The viability criterion is organizational closure
itself, which is structural, not representational. I5 (model adequacy)
is replaced by a structural adequacy condition — the organization must
be complex enough to maintain itself.

**What this family buys you:** A theory of how boundaries arise, not
just how they are maintained. Relevant for understanding biological
morphogenesis, institutional formation, and self-organizing systems
where the "agent" crystallizes from initially undifferentiated dynamics.

**What it costs you:** No easy hook for learning or planning. The
autopoietic diagram doesn't naturally accommodate the kind of
model-based anticipation that AIF handles well.

### 3.2 Viable System Diagrams (Beer/Ashby)

**Key difference from AIF:** Explicitly nested and recursive.

Stafford Beer's Viable System Model (VSM) is a five-level recursive
diagram where each level contains a complete viable system. This is
composition built into the architecture, not bolted on.

| Feature | AIF | VSM |
|---------|-----|-----|
| Boundary | Single blanket | Nested blankets (recursive) |
| Scale | One agent | Organization (cells within organs within organisms within institutions) |
| Control principle | Minimize free energy | Requisite variety (Ashby's law) |
| Composition | Not addressed | Core feature (System 1 contains viable subsystems) |
| Normativity | Prior preferences | Variety balance at interfaces |

**Invariants shared:** I1, I2, I3, I6.

**Invariants differing:** I4 becomes richer — each level has its own
"preferences" constrained by the level above. Wireheading in VSM
terms is a level N system gaming its level N+1 monitor. This is
well-understood in organizational theory (departments gaming their
KPIs). I5 becomes variety-relative — the model must have at least as
much variety as the relevant disturbances (Ashby's law).

**What this family buys you:** Native composition. You can ask "what
happens when you put two viable systems inside a third?" and get a
principled answer. Also: a mature theory of pathology (Beer's
organizational diseases map precisely to invariant violations).

**What it costs you:** The VSM is less formal than AIF. The
"variety" concept is qualitative where AIF's free energy is
quantitative. Integration with modern ML/probabilistic methods is
non-obvious.

### 3.3 Dissipative Structure Diagrams (Prigogine/Kauffman)

**Key difference from AIF:** No inference, no model. Just
thermodynamics.

A dissipative structure maintains its organization by continuously
processing energy/matter flows. The boundary is physical (not
statistical). The "inference" is replaced by thermodynamic selection —
structures that are better at dissipating energy persist.

| Feature | AIF | Dissipative |
|---------|-----|-------------|
| Boundary | Statistical (Markov blanket) | Physical (membrane, interface) |
| Internal model | Generative model | None — structure IS the attractor |
| What flows | Information (observations, beliefs) | Energy, matter, entropy |
| Persistence mechanism | Free energy minimization | Far-from-equilibrium stability |
| Failure mode | Model collapse | Thermodynamic death (equilibrium) |

**Invariants shared:** I1 (boundary — physical, not statistical), I3
(timescale separation — molecular dynamics vs. structural persistence),
I6 (compositional closure — the structure produces the flows that
maintain the structure).

**Invariants differing:** I2 is present but asymmetric in a different
way — energy in vs. entropy out, not observation vs. action. I4 is
replaced by a thermodynamic version — the attractor basin plays the
role of preferences, and it is "exogenous" in the sense that the
microscopic dynamics cannot easily escape it. I5 doesn't apply — there
is no model to be adequate.

**What this family buys you:** A diagram language for non-cognitive
viability-maintenance. Crystals, flames, convection cells, and
autocatalytic networks all have diagram descriptions in this family.
This is the widest applicable family — it covers everything that
persists far from equilibrium.

**What it costs you:** No account of learning, anticipation, or
planning. The dissipative diagram maintains structure but doesn't
model its environment or plan actions. To get cognition, you must
augment the diagram — which arguably is what AIF does.

### 3.4 Design Space Map

These three families, together with AIF, span a 2D subspace
organized by two axes:

```
                      explicit model
                           ↑
                           |
                    AIF ●  |
                           |
                           |
    self-produced ←————————+————————→ given boundary
    boundary               |
                           |
            Autopoietic ●  |  ● VSM
                           |
                           |
                           ↓
                      no explicit model

                    Dissipative ●
                    (below both axes:
                     given boundary,
                     no model)
```

The design space is larger than this. Unstudied regions include:

- **Self-produced boundary + explicit model** (above-left quadrant):
  Systems that construct their own boundaries AND maintain explicit
  models. Perhaps: immune systems, which build antibody repertoires
  (model) while maintaining self/non-self boundaries (autopoietic).

- **Nested + probabilistic**: VSM structure with AIF internals at each
  level. This is where the futon5 wiring system is headed.


---

## 4. Projection Discipline

The same abstract diagram should be projectable into different domains.
But "projectable" has conditions. Without discipline, projection
degrades into metaphor.

### 4.1 What Projection Means

A projection is a structure-preserving map from the abstract diagram
to a domain-specific diagram. "Structure-preserving" means:

- Boxes map to domain-specific roles
- Wires map to domain-specific interfaces
- Types map to domain-specific data shapes
- Invariants map to domain-specific constraints

A projection is NOT:

- An analogy ("the immune system is LIKE an agent")
- A metaphor ("institutions have beliefs")
- A renaming (changing labels without checking structure)

### 4.2 Projection Examples

**Biological projection (well-studied):**
- Boundary → cell membrane / organism skin
- Observation → sensory transduction
- Action → motor effectors
- Internal model → neural population activity
- Preferences → homeostatic setpoints (genetic)
- Timescale separation → neural (ms) vs developmental (days) vs
  evolutionary (generations)

**Institutional projection (less studied, more dangerous):**
- Boundary → organizational boundary (legal entity, membership)
- Observation → market signals, reports, complaints
- Action → policies, communications, resource allocation
- Internal model → organizational knowledge (documents, procedures,
  culture)
- Preferences → charter, constitution, regulatory constraints
- Timescale separation → operational (hours) vs strategic (quarters)
  vs constitutional (decades)

**Physical/thermodynamic projection:**
- Boundary → physical interface (membrane, surface)
- Observation → energy/matter influx
- Action → entropy export, waste ejection
- Internal model → (absent — structural attractor instead)
- Preferences → thermodynamic basin of attraction
- Timescale separation → molecular (ps) vs structural (s–hours)

### 4.3 When a Projection Fails

A projection fails — is not structure-preserving — when any of the
following occur:

**F1: Missing wire.** A wire in the abstract diagram has no plausible
implementation in the target domain. Example: projecting AIF onto a
crystal. Crystals have no plausible "action" wire — they don't
act on their environment to maintain their structure (they persist
passively). The observation-action loop (I2) doesn't close. You
can project the dissipative diagram onto a crystal, but not the
full AIF diagram.

**F2: Violated invariant.** An invariant of the abstract diagram is
violated by the domain's physics or structure. Example: projecting
AIF onto a social media feed. The preference exogeneity invariant
(I4) is violated by design — the algorithm rewrites what the user
"wants" based on engagement signals. The fast dynamics have direct
write access to the preference register. AIF-as-projected predicts
this will wirehead. It does.

**F3: Missing timescale.** A timescale separation required by the
diagram doesn't exist in the domain. Example: projecting AIF onto
a high-frequency trading system. If the "environment" (market) and
the "learning" process operate on the same millisecond timescale,
there is no separation between perception and learning. The diagram
collapses to a single timescale, violating I3.

**F4: Type mismatch.** The types carried by wires in the abstract
diagram have no coherent realization in the domain. Example:
projecting "beliefs" (probability distributions over states) onto
a thermostat. The thermostat has a setpoint and a measurement, not
a distribution. You can project a cybernetic control diagram, but
the full probabilistic AIF diagram doesn't fit without violent
type coercion.

**F5: Structural surplus.** The target domain has structure that the
abstract diagram cannot represent. Example: projecting a single-
agent AIF diagram onto a multi-agent negotiation. The diagram has
one boundary; the domain has many, with complex inter-boundary
protocols. The diagram is too simple, and forcing it requires either
collapsing the multi-agent structure (losing information) or
extending the diagram (which means you needed a different family).

### 4.4 The Projection Test

Before projecting a diagram onto a domain, check:

1. For each wire: what carries it? (If answer is "well, metaphorically..."
   → projection fails at F1 or F4)
2. For each invariant: does the domain's structure support it?
   (If answer requires squinting → projection fails at F2)
3. For each timescale separation: what physical/institutional process
   produces it? (If answer is "we assume..." → projection fails at F3)
4. Does the domain have relevant structure the diagram ignores?
   (If yes → projection fails at F5, or you need a richer diagram)

These are not merely theoretical criteria. They can be formalized as
type-checking in the same way `futon5.ct.mission/validate` checks
mission diagrams: map the abstract boxes and wires to domain-specific
types, then check whether all edges are type-safe.


---

## 5. Engineering Perspective: From First Flight to Airworthiness

### 5.1 AIF as First Flight

The Wright Flyer had no pressurized cabin, no instrument panel, no
redundant control surfaces. It proved that powered heavier-than-air
flight was possible. Everything else — the century of aerospace
engineering that followed — was about making it reliable, certifiable,
composable, and safe.

AIF is at the Wright Flyer stage. It proved that free-energy-based
inference-action coupling produces viable behavior in simulated
environments. Everything else — the engineering discipline for building
actual systems from such diagrams — does not yet exist.

What would it look like?

### 5.2 Certification Criteria

A wiring diagram should be "airworthy" only if:

**C1: All invariants hold.** I1–I6 are checkable properties of the
diagram. This is the equivalent of the five checks in
`futon5.ct.mission/validate`:

| Mission check | Diagram equivalent |
|---------------|-------------------|
| Completeness | Every output reachable from inputs |
| Coverage | Every component reaches an output |
| No orphan inputs | Every input connects to something |
| Type safety | Wire types match port accepts/produces |
| Spec coverage | Every output has a specification |

Additional diagram-specific checks:

- **Boundary check:** Sensory and active surfaces fully partition the
  interface. No "back-channel" wires that bypass the blanket.
- **Timescale check:** For every pair of connected timescales, the
  slower one has lower update frequency by at least an order of
  magnitude.
- **Exogeneity check:** No directed path from Action → Preferences
  that does not pass through Environment.

**C2: Failure modes are enumerated.** Every component must have a
documented failure mode and the diagram must specify what happens
when that component fails. (Cf. FMEA in aerospace.)

**C3: Graceful degradation.** The diagram should specify which
components can fail without total system failure. The minimum viable
subdiagram must be identified — the Wright Flyer of your system
that still flies when non-essential components are offline.

### 5.3 Stress Tests

Every certified diagram should be tested under boundary conditions:

- **Observation blackout:** What happens when sensory input goes to
  zero? Does the system freeze, hallucinate, or degrade gracefully?
  (In AIF: falls back on the generative model's predictions. In
  practice: hallucination if model is overconfident.)

- **Action blockage:** What happens when actions have no effect?
  (In AIF: free energy increases but the system should not diverge.
  In practice: many AIF implementations don't handle this.)

- **Preference shock:** What happens when preferences change
  discontinuously? (In AIF: free energy spikes, system must
  re-converge. In practice: catastrophic forgetting.)

- **Model mismatch:** What happens when the environment changes
  structurally, not just parametrically? (In AIF: the generative
  model becomes structurally inadequate. No standard recovery
  mechanism.)

- **Composition stress:** What happens when two individually-certified
  diagrams are composed? Do the invariants still hold for the
  composite? (Currently: no guarantee. This is the open problem.)

### 5.4 Modularity

Aerospace systems are modular: you can replace an engine without
redesigning the wing. Wiring diagrams should have the same property.

**Component interface contracts.** Each box should specify its
`:accepts` and `:produces` types (exactly as in the mission diagram
validator). Replacing a component with a different implementation that
satisfies the same contract should not affect the rest of the diagram.

**This is where the CT perspective pays off.** A component is a
morphism f: A → B. Replacing f with g: A → B preserves all
composition. The type system enforces this automatically.

**Hot-swappable components.** The observation module should be
replaceable (different sensors, different preprocessing) without
touching the inference module. The action selection module should be
replaceable without touching the model. This is achievable only if
the wire types are precise enough to capture the real interface
contracts.

### 5.5 Failure Analysis

Known failure modes, mapped to invariant violations:

| Failure mode | Invariant violated | Diagram symptom |
|-------------|-------------------|-----------------|
| Wireheading | I4 (exogeneity) | Path from Action to Preferences bypassing Environment |
| Hallucination | I5 (adequacy) | Model generates outputs uncorrelated with observations |
| Catatonia | I2 (obs-action) | Action surface decoupled from internal states |
| Brittleness | I3 (timescales) | Learning timescale too fast → overfitting to noise |
| Self-destruction | I6 (closure) | Action that destroys sensory surface |
| Identity loss | I3 (timescales) | Preference timescale same as action → what-to-be drifts |
| Boundary dissolution | I1 (boundary) | Interface bandwidth exceeds capacity → inside/outside merge |

Each failure mode is detectable as a structural property of the
diagram before the system is run. This is the key engineering insight:
**you can check for failure modes by inspecting the wiring, not by
running the system.** This is what "certification" means.

### 5.6 Compositional Guarantees

The hardest open problem. Given two certified diagrams A and B,
under what conditions is A ∘ B (or A ⊗ B) also certified?

**Serial composition** (A feeds B): Safe if A's output types match
B's input types AND B's timescale structure is compatible with A's.
The composed system's preferences are (at minimum) the union of A's
and B's preferences. If they conflict, the composition is ill-formed.

**Parallel composition** (A ⊗ B): Safe if A and B share no internal
state and their actions don't interfere. If A's actions affect B's
observations (and vice versa), the composition is a multi-agent
system and requires an inter-agent protocol — a higher-level diagram.

**Nesting** (A contains B as a subsystem): This is VSM territory.
Safe if B's boundary is a sub-blanket of A's boundary, and A's
preferences are consistent with (and slower than) B's preferences.
The timescale hierarchy must extend: A-slow > B-slow > B-fast.

**What `futon5.ct.mission/compose-missions` does for missions,
a future `futon5.ct.agent/compose-agents` could do for agent
diagrams:** check type compatibility at ports, merge internal
structure, validate the result against I1–I6.


---

## 6. The Default Mode: Architectural Tiers and the SPOF Problem

### 6.1 The Policy Bottleneck

Validating the futon2 AIF ant diagram against I6 (compositional closure)
reveals a structural finding: **C-policy is a single point of failure.**
Removing the policy component disconnects all five outputs from all four
inputs. No actions, no pheromone, no scores, no diagnostics, no
termination signal.

This is not a bug in the ant implementation. It is inherent to the
standard AIF diagram. The policy node is the sole coupling between the
perceptual chain (observe → perceive → affect) and the motor chain
(execute → world). There is no alternative path. If the deliberative
process fails, the entire system goes dark.

### 6.2 Three Tiers: Reflex, Default Mode, Deliberative

Neuroscience offers a structural insight here. The brain does not have
a single "policy" node. It has at least three tiers of behavioral
organization:

| Tier | Timescale | Example | What it provides |
|------|-----------|---------|-----------------|
| **Reflex** | Fastest (~ms) | Spinal reflexes, brainstem | Immediate survival responses, no deliberation |
| **Default mode** | Medium (~s) | Default Mode Network (DMN) | Baseline activity when task-specific processing is offline |
| **Deliberative** | Slow (~s–min) | Prefrontal, task-positive networks | Goal-directed planning, EFE-like evaluation |

The critical insight is the **default mode tier.** When the deliberative
system is not engaged — between tasks, after a goal is achieved, when
the current plan fails — the system does not go silent. It falls back
to a default pattern of activity. The DMN is not "doing nothing." It is
maintaining baseline cognition: self-referential processing, future
simulation, social modeling.

### 6.3 Why This Matters for Wiring Diagrams

In diagram terms, the default mode is a **parallel path** from
observation to action that does not pass through the deliberative
policy. It provides:

1. **Compositional closure (I6).** If the deliberative path fails, the
   default path keeps *some* outputs alive. The diagram survives
   component failure.

2. **Graceful degradation.** Instead of binary (deliberative works /
   system dead), the system has a fallback: simpler behavior, but still
   behavior. The ant doesn't freeze — it follows pheromone gradients,
   returns toward the nest, maintains basic survival responses.

3. **Inter-task continuity.** Between deliberative episodes, the system
   has somewhere to go. It doesn't stall waiting for the next goal to
   be assigned.

The standard AIF diagram lacks this tier. It has the deliberative
policy and nothing else. Adding a default mode component is not
"adding a feature" — it is fixing a structural deficiency (I6
violation) that makes the diagram fragile.

### 6.4 Architectural Pattern

The fix is a `C-default-mode` component wired in parallel with
`C-policy`:

```
                    ┌─→ C-perceive → C-affect → C-policy ──→ C-execute
I-world → C-observe │                                              │
                    └─→ C-default-mode ──────────────────→ C-execute
                              │
                              └──→ O-actions, O-diagnostics
```

Properties of C-default-mode:

- **Accepts:** observations (same as the perceptual chain's input)
- **Produces:** baseline actions + diagnostics
- **Timescale:** fast (same as perception and action)
- **Does NOT accept:** preferences or EFE weights (those are
  deliberative concerns — the default mode is pre-deliberative)
- **Simpler than policy:** no EFE evaluation, no planning horizon.
  Tropisms, gradients, cached habits.

When C-policy is present and functioning, both paths produce candidate
actions. The execution layer selects or blends them. When C-policy
fails, the default mode path alone sustains behavior.

### 6.5 The Fulab Agent Parallel

This finding transfers directly to fulab agents. Consider the current
fulab agent architecture:

| Tier | Fulab equivalent | Behavior |
|------|-----------------|----------|
| **Reflex** | Error handlers, watchdogs | Crash recovery, timeout handling |
| **Default mode** | *"Wait for user input"* | **The current baseline** |
| **Deliberative** | AIF controller, task execution | Mission-directed work |

The problem: the current default mode is **interactive chat.** When a
deliberative episode ends (task complete, context exhausted, run
finishes), the agent reverts to waiting for human input. This is the
correct default mode for a *tool* but not for an *autonomous agent.*

An autonomous agent's default mode should be:

1. Check mission queue for pending work
2. Read handoff notes from previous sessions
3. Generate a Post-Action Review (PAR) for the completed episode
4. Signal availability to peer agents
5. If nothing pending, enter low-power monitoring

This is structurally identical to the ant fix. The ant's default mode
is "follow pheromone gradients toward the nest" — a simple, robust
behavior that keeps the ant alive and useful between deliberative
episodes. The fulab agent's default mode should be "check for work,
process handoffs, maintain readiness" — a simple, robust behavior
that keeps the agent productive between task-directed episodes.

**The ant diagram and the fulab agent diagram are instances of the
same diagram family.** Fixing the SPOF in one informs the fix in
the other.

### 6.6 Implications for I6

Compositional closure (I6) should be understood as requiring not just
"no SPOF" but specifically: **the diagram must have at least two
independent paths from observation to action.** One deliberative, one
pre-deliberative. This is what makes the system robust to component
failure — not redundancy in the trivial sense (two copies of the same
thing) but structural redundancy (two different *kinds* of response
generation).

The invariant dependency graph extends:

```
I1 (boundary) ← I6 (closure) depends on
                 I6 ← requires multi-tier action generation
I2 (obs-action) ← I5 (adequacy) depends on
I3 (timescales) ← I4 (exogeneity) depends on
                   I3 ← default mode is a distinct (faster) timescale
```


---

## 7. Toward a Diagram Calculus

### 6.1 What Would It Be?

A diagram calculus is a formal system for constructing, validating,
composing, and comparing wiring diagrams. It would include:

- **Syntax:** How to write a diagram (EDN, graphical, categorical)
- **Type system:** What flows on each wire (the objects in the
  category)
- **Composition operations:** Serial (∘), parallel (⊗), nesting,
  feedback
- **Invariant checking:** Mechanized verification of I1–I6
- **Projection:** Functors from abstract diagrams to domain-specific
  realizations
- **Equivalence:** When are two diagrams "the same diagram in
  different notation"?

### 6.2 What Exists Already

In futon5:

- `ct/dsl.clj`: Category, Functor, NaturalTransformation, Diagram
  records. Primitive, compose, tensor operations. Pre-built categories
  for AIF stages, meta-kernel, metaca, cyber-ants, design patterns.
- `wiring/compose.clj`: Serial and parallel composition of typed
  directed graphs. Crossover/breeding. Node/edge structure with typed
  ports.
- `xenotype/category.clj`: Category-theoretic validation of wiring
  diagrams — objects are port types, morphisms are components,
  composition is checked for identity laws and associativity.
- `ct/mission.clj`: Mission diagram validation — completeness,
  coverage, type safety, spec coverage, composition.

### 6.3 What's Missing

1. **Agent diagrams as first-class objects.** Mission diagrams describe
   organizational architecture. Agent diagrams describe
   inference-action coupling. They need the same treatment: typed
   ports, validated composition, checkable invariants. But the
   invariants are different (I1–I6 above vs the five mission checks).

2. **Timescale annotations.** The current type system captures what
   flows on each wire but not *how fast.* Timescale separation (I3)
   is the most important invariant for avoiding failure modes, and it
   has no representation yet. Wires need a `:timescale` annotation.

3. **Projection as functor.** A projection from an abstract diagram
   to a domain-specific one is exactly a functor between categories.
   The failure criteria (F1–F5) are conditions under which the functor
   fails to be well-defined. This could be mechanized.

4. **Invariant checking beyond type safety.** The current validator
   checks types at ports. It does not check for directed paths (the
   exogeneity check), timescale ordering, or compositional closure.
   These require graph algorithms beyond reachability.

5. **A library of diagram families.** AIF, autopoietic, VSM,
   dissipative — each is a template (a diagram with some boxes and
   wires fixed and others free). A calculus needs a catalog of such
   templates, with formal relationships between them.

### 6.4 Design Brief for Futon5

If futon5 is to be the workbench for this calculus, it needs:

**Short term (extend what exists):**
- Add `:timescale` to port/edge annotations in `ct/mission.clj`
- Add `validate-exogeneity` check (no Action→Preference path)
- Add `validate-timescale-ordering` check
- Create `ct/agent.clj` parallel to `ct/mission.clj` with I1–I6

**Medium term (new capabilities):**
- Projection functor: map abstract diagrams to domain-specific ones
  with type checking at each step
- Template library: reusable diagram skeletons (AIF template, VSM
  template, etc.)
- Composition validator for multi-diagram systems

**Long term (the calculus):**
- Equivalence checker: when are two diagrams structurally identical?
- Diagram refinement: when is diagram B a more detailed version of
  diagram A? (Analogous to model refinement in formal methods.)
- Automatic failure mode enumeration from invariant violations
- Connection to futon6's graph-theoretic infrastructure for
  large-scale diagram analysis


---

## 8. Summary of Claims

1. AIF is an existence proof for inference-action-constraint coupling,
   not a universal theory of agency.

2. Its diagrammatic structure relies on six invariants: boundary
   integrity, observation-action asymmetry, timescale separation,
   preference exogeneity, model adequacy, and compositional closure.

3. Wireheading and related failure modes are invariant violations
   detectable by diagram inspection — structural properties of the
   wiring, not runtime pathologies.

4. AIF occupies one point in a design space that includes autopoietic,
   cybernetic (VSM), and dissipative diagram families. These share
   foundational invariants (I1–I3) but differ in derived invariants
   and representational commitments.

5. Projection from abstract diagrams to domain-specific realizations
   is a functor with explicit failure criteria. When the functor
   fails, the projection is metaphor, not structure.

6. An engineering discipline for wiring diagrams would require
   certification (invariant checking), stress testing (boundary
   conditions), modularity (typed component interfaces), failure
   analysis (invariant → failure mode mapping), and compositional
   guarantees (when does A ∘ B preserve certification?).

7. The standard AIF diagram has an inherent SPOF: the policy node is
   the sole coupling between perception and action. Fixing this
   requires a default mode tier — a pre-deliberative path from
   observation to action that sustains behavior when deliberation
   fails. This is the Default Mode Network pattern, and it applies
   equally to AIF ants and fulab agents.

8. The tools already built in futon5 (CT DSL, wiring composition,
   type-checked mission diagrams) are the prototype of such a
   discipline. Extending them to agent diagrams with timescale
   annotations, exogeneity checking, and projection functors is
   the natural next step.
