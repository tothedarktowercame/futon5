# Core Terminal Vocabulary (AIF/GFE)

This document defines the **abstract structure** that all domain-specific terminal
vocabularies must instantiate. It is based on Active Inference (AIF) and the
Generalized Free Energy (GFE) formulation.

## Meta-Policy: AIF as "Xenotype to the Xenotype"

Xenotypes are **policies** — they select actions given observations and beliefs.
But what governs how xenotypes themselves operate? What selects among xenotypes?

The AIF/GFE loop is the **meta-policy**:

```
┌─────────────────────────────────────────────────────────────────────┐
│                     AIF/GFE (Meta-Policy)                           │
│                                                                     │
│   Observables → Beliefs → Policies → Actions → Free Energy         │
│                              │                      │               │
│                              │                      │               │
│                              ▼                      ▼               │
│                         XENOTYPES              SELECTION            │
│                    (domain policies)       (minimize G over π)      │
│                                                                     │
│   The loop itself is domain-agnostic.                               │
│   Each domain instantiates the vocabulary.                          │
│   Free energy minimization selects among xenotypes.                 │
└─────────────────────────────────────────────────────────────────────┘
```

**Key insight**: AIF doesn't just describe what xenotypes do — it governs
*which* xenotypes get selected and *how* they adapt. The free energy signal
is what "bends" the xenotype population toward effectiveness.

| Layer    | What it is            | What selects it |
|----------|-----------------------|-----------------|
| Genotype | CA rule (sigil)       | Exotype         |
| Exotype  | Local per-cell policy | Xenotype        |
| Xenotype | Global system policy  | AIF/GFE loop    |
| AIF/GFE  | Meta-policy           | (fixed)         |

The AIF/GFE loop is the only level that doesn't evolve — it's the fixed
reference frame within which evolution happens.

## The Core Loop

Every domain implements this loop. But notice: **this loop operates at two levels**:

1. **Within a run**: Xenotypes observe, believe, select actions, minimize G
2. **Across runs**: The evolution process observes run outcomes, updates beliefs
   about xenotype quality, selects better xenotypes, minimizes G over xenotype space

The same abstract loop governs both local action selection AND global xenotype evolution.

Every domain implements this loop:

```
     ┌─────────────────────────────────────────────────────┐
     │                                                     │
     ▼                                                     │
OBSERVABLES (o) ──► BELIEFS (μ) ──► POLICIES (π) ──► ACTIONS (a)
     │                   │               │                 │
     │                   │               │                 │
     │                   ▼               ▼                 │
     │            EXPECTATIONS    POLICY SELECTION         │
     │               (priors)      (xenotype)              │
     │                   │               │                 │
     │                   └───────┬───────┘                 │
     │                           │                         │
     │                           ▼                         │
     │                    FREE ENERGY (G)                  │
     │                           │                         │
     │                           │                         │
     └───────────────────────────┴─────────────────────────┘
                            (world dynamics)
```

## Core Vocabulary

### Observables (o)

**What the agent can sense from the environment.**

| Field          | Type            | Description                   |
|----------------|-----------------|-------------------------------|
| `o/raw`        | domain-specific | Raw sensory input             |
| `o/normalized` | [0,1] vector    | Normalized observation vector |
| `o/gradient`   | vector          | Change in observations        |
| `o/surprise`   | scalar [0,1]    | Prediction error magnitude    |

Domain instantiation required: What are the raw observables? How are they normalized?

### Beliefs / Hidden States (μ)

**The agent's inferred state of the world and itself.**

| Field          | Type            | Description                   |
|----------------|-----------------|-------------------------------|
| `μ/state`      | domain-specific | Current inferred world state  |
| `μ/self`       | domain-specific | Current inferred self state   |
| `μ/mode`       | keyword         | Inferred operating mode/phase |
| `μ/confidence` | [0,1]           | Confidence in current beliefs |

Domain instantiation required: What hidden states are tracked? What modes exist?

### Expectations / Priors (C)

**What the agent expects or desires.**

| Field          | Type         | Description                           |
|----------------|--------------|---------------------------------------|
| `C/preferred`  | distribution | Preferred observations (attractors)   |
| `C/avoided`    | distribution | Dispreferred observations (repellors) |
| `C/mode-prior` | distribution | Prior over operating modes            |

Domain instantiation required: What does the agent "want"? What does it avoid?

### Policies (π)

**Rules for selecting actions given beliefs and expectations.**

| Field          | Type                 | Description                   |
|----------------|----------------------|-------------------------------|
| `π/active`     | policy-id            | Currently active policy       |
| `π/repertoire` | [policy-id]          | Available policies            |
| `π/weights`    | {policy-id → weight} | Policy selection weights      |
| `π/trace`      | [policy-id]          | History of policy activations |

**THIS IS WHERE XENOTYPES LIVE.**

A xenotype IS a policy (or a meta-policy that selects among policies).

### Actions (a)

**What the agent can do to the environment.**

| Field          | Type            | Description                  |
|----------------|-----------------|------------------------------|
| `a/primitive`  | action-id       | Primitive action taken       |
| `a/macro`      | macro-action-id | Higher-level action category |
| `a/parameters` | map             | Action parameters            |
| `a/outcome`    | domain-specific | Observed outcome of action   |

Domain instantiation required: What actions are available? What parameters do they have?

### Free Energy (G)

**The quantity being minimized.**

| Field          | Type   | Description                             |
|----------------|--------|-----------------------------------------|
| `G/pragmatic`  | scalar | Pragmatic value (goal achievement)      |
| `G/epistemic`  | scalar | Epistemic value (uncertainty reduction) |
| `G/total`      | scalar | Combined free energy                    |
| `G/components` | map    | Breakdown of G into components          |

Domain instantiation required: How is pragmatic value measured? How is uncertainty measured?

## The Two Loops

### Inner Loop: Action Selection (within a run)

```
o(t) ──► μ(t) ──► π ──► a(t) ──► G(t)
                  │
                  └── xenotype determines this
```

The xenotype IS the policy. Given observations and beliefs, it selects actions.
Free energy G measures how well actions align with expectations.

### Outer Loop: Xenotype Selection (across runs)

```
run-outcome(n) ──► belief-about-xenotypes ──► π* ──► select-xenotype(n+1) ──► G*
                           │                   │
                           │                   └── meta-policy
                           │
                           └── "which xenotypes produce good runs?"
```

| Inner Loop                | Outer Loop                           |
|---------------------------|--------------------------------------|
| Observables: cell context | Observables: run metrics             |
| Beliefs: hidden state     | Beliefs: xenotype quality estimates  |
| Policy: xenotype wiring   | Policy: xenotype selection/evolution |
| Actions: output sigil     | Actions: choose next xenotype to try |
| Free Energy: local G      | Free Energy: fitness score           |

**The outer loop is also AIF.** It's the same abstract structure, but the
"agent" is the evolution process and the "environment" is the space of possible runs.

### Dual Evolution

For full AIF at both levels, we need to evolve both:
- **Generator xenotypes**: How to produce dynamics (inner loop policy)
- **Scorer xenotypes**: How to evaluate dynamics (outer loop observables)

If scorers are hardcoded, the outer loop is only half-AIF — it observes through
a fixed lens. Evolvable scorers complete the picture.

## Xenotypes as Policies

A **xenotype** is a policy that:

1. **Observes**: Receives normalized observations and current beliefs
2. **Evaluates**: Computes expected free energy for candidate actions
3. **Selects**: Chooses action(s) that minimize G
4. **Updates**: Modifies beliefs based on action outcomes

The 256 xenotypes are **domain-agnostic policy stances**:

```
xenotype = hexagram (situation, 64) × energy (engagement mode, 4)
```

The **hexagram** encodes the situation (what configuration of o, μ, C):
- Which observables are salient?
- What mode is the agent in?
- What expectations are active?

The **energy** encodes engagement mode (how to act in that situation):
- Péng: Expand, create space, establish boundaries
- Lǚ: Yield, redirect, absorb without adopting
- Jǐ: Focus, concentrate force on specific point
- Àn: Push, sustained forward pressure

## Domain Instantiation Template

Each domain vocabulary should provide:

```clojure
{:domain :metaca  ; or :cyberants, :fulab, etc.

 ;; Observables
 :o/raw-fields [:genotype :phenotype :kernel ...]
 :o/normalize-fn (fn [raw] ...)
 :o/vector-abi [:entropy :change-rate :unique-sigils ...]

 ;; Beliefs
 :μ/state-fields [:regime :stability ...]
 :μ/modes [:static :eoc :chaos :magma]
 :μ/infer-fn (fn [o-history] ...)

 ;; Expectations
 :C/preferred {:entropy [0.5 0.7] :change-rate [0.1 0.3]}
 :C/avoided {:regime :chaos}

 ;; Actions
 :a/primitives [:kernel-change :grid-delta :noop]
 :a/macro-categories [:pressure-up :pressure-down :selectivity-up ...]
 :a/execute-fn (fn [action state] ...)

 ;; Free Energy
 :G/pragmatic-fn (fn [o C] ...)
 :G/epistemic-fn (fn [μ o] ...)
 :G/weights {:pragmatic 0.6 :epistemic 0.4}}
```

## Xenotype Activation

Given this structure, a xenotype activates as:

```
1. Receive: o (observables), μ (beliefs), C (expectations)
2. Classify: Map (o, μ, C) → hexagram (situation)
3. Engage: Apply energy (Péng/Lǚ/Jǐ/Àn) to situation
4. Select: Choose action a that minimizes G given xenotype stance
5. Update: Revise μ based on outcome of a
6. Log: Record (xenotype, o, a, outcome) for policy learning
```

## Relation to Exotypes

| Level | Scope | What it does |
|-------|-------|--------------|
| Genotype | Single sigil | Defines CA rule |
| Exotype | 3 sigils + phenotype | Local physics (per-cell policy) |
| Xenotype | Across vocabulary | Global policy (shapes all exotypes) |

Exotypes are **local policies** (what does this cell do?).
Xenotypes are **global policies** (what stance does the whole system take?).

## Verification Questions

For any domain vocabulary, we should be able to answer:

1. **Observables**: What can be sensed? Is it normalized?
2. **Beliefs**: What is inferred? What modes exist?
3. **Expectations**: What is preferred/avoided?
4. **Actions**: What can be done? How are they parameterized?
5. **Free Energy**: How is G computed? What are the weights?
6. **Xenotype Activation**: How does xenotype stance affect action selection?

If these questions can't be answered clearly, the domain isn't ready for xenotype-driven runs.

## Xenotype Applications

The 256 xenotypes are **domain-agnostic policy stances**. The same xenotype
can operate in different domains because the vocabulary instantiation handles
the translation:

| Domain | Same Xenotype (e.g., Hexagram 37 + Jǐ) |
|--------|----------------------------------------|
| MetaCA | Focus mutation on family-consistent sigils |
| Cyberants | Ant scrutinizes whether to follow pheromone trail |
| Fulab | Argument pattern demands mechanism test |
| Security | Tripwire activates press-mechanism check |

### Security Layer (One Application)

The security layer (tripwires, escalation, ahimsa) is one **interpretation**
of xenotype activation in a particular domain (compass/fulab). When a tripwire
fires, it's like the outer loop detecting anomaly → activating security xenotype.

| Security Concept | AIF Translation |
|------------------|-----------------|
| Tripwire | Observation that triggers mode switch |
| Escalation | Policy selection under threat |
| Quarantine (Péng) | Action: establish boundary |
| Hold (Lǚ) | Action: yield without adopting |
| Scrutinize (Jǐ) | Action: demand mechanism test |
| Pressure (Àn) | Action: force warrant test |
| Ahimsa constraint | Expectation: harm visible, bounded, corrigible |

The security patterns are xenotypes specialized for the "detecting fabrication
templates" problem. They're not the whole story — just one domain instantiation.

## Current Status

| Domain | Observables | Beliefs | Expectations | Actions | Free Energy | Xenotype |
|--------|-------------|---------|--------------|---------|-------------|----------|
| MetaCA | ✓ v2 vocab | partial | implicit | ✓ v2 vocab | partial | ✗ |
| Cyberants | ✓ listed | ✓ | ✓ | ✓ listed | ✓ | partial |
| Fulab | partial | ✗ | ✗ | ✗ | ✗ | ✗ |

**Next steps**:
1. Implement MetaCA vocabulary (Levels 1-7) in exotype.clj
2. Audit existing scorers as outer-loop observations
3. Define 8 prototype xenotypes (one per hexagram family)
4. Define 4 prototype scorers (one per primary energy)
5. Test dual evolution: generator + scorer xenotypes together
