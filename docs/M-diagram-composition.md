# Mission: Diagram Composition (Parallel / Nested)

Extend ct/mission.clj to validate multi-diagram composition with shared
constraint inputs. Currently only serial composition (A→B) is supported;
the futon3 three-timescale stack requires parallel composition with shared
constraints.

## Status

Complete — compose-parallel implemented (feat/compose-parallel), all 8 checks
pass standalone and composed.

## Owner

Claude (+ Codex for review).

## Motivation

The futon3 stack has three AIF loops at three timescales:
- futon3c: social coordination (~seconds)
- futon3b L0: task execution (~minutes)
- futon3b L1: library evolution (~weeks)

Each loop has a validated standalone diagram:
- `social-exotype.edn`: 8/8 checks pass
- `coordination-exotype.edn`: 8/8 checks pass
- `futon3-coordination.edn`: 8/8 checks pass (concrete)

These diagrams share constraint inputs (I-patterns, I-registry) and connect
via typed outputs (social's O-task-submissions → coordination's I-request).
Validating them AS A COMPOSED SYSTEM is not yet possible.

## Evidence: Serial Composition Failure (2026-02-09)

Attempted `(compose-missions social-exotype coordination-exotype)`:

**Port matches found (5):**

| Social output | Coordination input | Type |
|---|---|---|
| O-task-submissions | I-request | http-request |
| O-coordination-evidence | I-environment | xtdb-entity |
| O-coordination-evidence | I-tensions | xtdb-entity |
| O-social-events | I-environment | xtdb-entity |
| O-social-events | I-tensions | xtdb-entity |

Type safety and timescale ordering pass. But:

**Failures (3):**

| Check | Failure | Root Cause |
|---|---|---|
| completeness | 7 outputs unreachable | Edge prefixing (`social-exotype.S-presence`) disconnects internal wiring from unprefixed input ports |
| coverage | 16 dead components | Same prefixing issue — components can't reach outputs |
| compositional-closure | 16 single points of failure | Disconnected graph has no redundancy |

**Also: orphan inputs (5)**

I-patterns, I-registry, I-session-state, I-connections, I-missions all orphaned
because shared constraint ports exist in both diagrams but aren't deduplicated.

## Diagnosis

`compose-missions` was designed for serial pipelines (A produces → B consumes).
The three-futon stack is a **shared-constraint parallel composition with
asymmetric feedback**:

```
futon3c (social)  ──→  futon3b L0 (task)  ──→  futon3b L1 (glacial)
    ↑                       ↑                        │
    │    shared: I-patterns, I-registry               │
    └─────────── library constrains ──────────────────┘
```

Serial composition can't express this because:

1. **Shared constraint ports** need deduplication, not independent wiring.
   I-patterns in diagram A IS I-patterns in diagram B — same port, same
   constraint, same timescale.

2. **ID prefixing** breaks when inputs are shared. The prefixer turns
   `:I-patterns` into `:social-exotype.I-patterns` in one diagram and
   `:coordination-exotype.I-patterns` in the other, creating two separate
   nodes for what should be one.

3. **I6 (compositional closure)** needs to reason about the COMPOSED default
   mode, not individual defaults. Each diagram has its own S-default /
   C-default, but the composed system's resilience depends on whether they
   can cover for each other.

## What Was Needed

### compose-parallel (or compose-shared)

A new composition function that:

1. **Merges shared constraint ports**: ports with the same `:id`, `:type`, and
   `:constraint true` across diagrams become one port in the composition.

2. **Preserves internal wiring**: edge prefixing must also update edges that
   reference shared ports, pointing them to the merged port ID.

3. **Creates cross-diagram edges**: where output ports in diagram A type-match
   input ports in diagram B (the serial composition already finds these).

4. **Validates cross-diagram invariants**:
   - **I3 (timescale ordering)**: social components (`:social`) must not bypass
     task timescale to reach glacial constraints. Extended timescale ordering:
     `:social < :fast < :medium < :slow < :glacial`.
   - **I4 (exogeneity)**: no social output port has a path to shared constraint
     inputs through the composed graph.
   - **I6 (compositional closure)**: removing any single component from the
     composed graph does not disconnect ALL outputs from ALL inputs.

### Timescale extension

Added `:social` to `timescale-order`:
```clojure
(def timescale-order [:social :fast :medium :slow :glacial])
```

This is backward-compatible: existing diagrams using `:fast` through `:glacial`
are unaffected. The social exotype uses `:social` for its components, making
cross-diagram I3 checks precise.

## Implementation Notes

### compose-parallel

Composes two diagrams by:

- Merging shared *constraint input* ports that have the same `:id` and `:type`
  and are `:constraint true` in both diagrams.
- Prefixing all other IDs with the mission ID (to avoid collisions).
- Adding cross-diagram edges for type-compatible output→input pairs (A to B).
- Treating connected ports as internal wires:
  - Outputs in A that feed cross-diagram edges are removed from the composed
    boundary outputs.
  - Inputs in B that are satisfied by cross-diagram edges are removed from the
    composed boundary inputs.

### Shared Constraint Contract

If a port is shared as a constraint input, the core attributes must match
between diagrams:

- `:id`
- `:type`
- `:constraint` (must be true)
- `:timescale`

If they differ, `compose-parallel` throws: this mismatch indicates drift in the
shared boundary contract.

### Composition Result (social || coordination)

Composed `social-exotype || coordination-exotype`:
- 5 inputs, 7 outputs, 16 components, 69 edges
- All 8 checks pass
- Cross-diagram edges include:
  - O-task-submissions → I-request (social→task boundary)
  - O-coordination-evidence → I-environment, I-tensions (evidence feedback)
  - O-social-events → I-environment, I-tensions (telemetry feedback)

## Isomorphism: fast-social-glacial ≅ pheno-geno-exo

The three-timescale composition validated here is structurally identical to
pheno-geno-exo stacking used throughout futon5:

| Social stack | futon5 stack | Timescale |
|---|---|---|
| social (futon3c) | phenotype (observable) | fastest |
| task (futon3b L0) | genotype (generative) | medium |
| glacial (futon3b L1) | exotype (constraining) | slowest |

The composition rules (shared constraints, asymmetric feedback, timescale
ordering) are the same in both. Building `compose-parallel` for the futon3
use case gives futon5 a validated multi-timescale composition framework for
free.

## Scope Out

- pheno-geno-exo-xeno (four-level stacking) — save for a subsequent mission
- Rewriting existing compose-missions — extend, don't replace
- Automated timescale inference across diagrams (require explicit annotation)

## Success Criteria

- [x] `compose-parallel` function implemented
- [x] Shared-port deduplication works (I-patterns appears once in composed diagram)
- [x] Cross-diagram I3 passes (social→fast→glacial ordering respected)
- [x] Cross-diagram I4 passes (no social output→constraint path)
- [x] Cross-diagram I6 passes (composed default modes provide resilience)
- [x] social-exotype + coordination-exotype compose with all 8 checks passing
- [x] Existing serial composition unaffected (regression test)

## Connects To

- **Triggered by**: M-social-exotype Part II (futon3c)
- **Feeds**: futon5 multi-timescale validation capability
- **Enables**: pheno-geno-exo diagram validation
- **Mana**: completing this earns `:proposal-accepted` mana for gated futon5
  missions that require multi-timescale reasoning
