# Gate-Pattern Mapping: Deriving Coordination Patterns from Futon-Theory

This document maps futon-theory patterns to the six gates (G5-G0) of the
futon3 coordination specification, organizes existing ad-hoc patterns as
ancestral evidence, and identifies what domain-specific coordination patterns
must still be DERIVED.

This is **step 2 of the derivation xenotype** (IDENTIFY → MAP → DERIVE →
ARGUE → VERIFY → INSTANTIATE). Step 1 (IDENTIFY domain = coordination) was
completed when we built the `futon3-coordination.edn` specification.

## Method

The same derivation xenotype used for futon1a (storage domain), extended with
ARGUE (compose pattern conclusions into a flexiformal proof) and a structural
VERIFY step (translate patterns into a wiring diagram, validate against
invariants):

| Step | Storage (futon1a) | Coordination (futon3) |
|------|-------------------|-----------------------|
| IDENTIFY | Storage domain | Agent coordination domain |
| MAP | Theory → L0-L4 | Theory → G5-G0 (this document) |
| DERIVE | durability-first, single-source-of-truth, ... | 12 coordination patterns + 3 theory patterns |
| ARGUE | (implicit — the futon1a mission doc served this role) | `coordination/ARGUMENT.flexiarg` |
| VERIFY | futon1a unit + stress tests | `coordination-exotype.edn` + ct/mission validator |
| INSTANTIATE | futon1a code | `M-coordination-rewrite.md` (Futonic mission) |

### VERIFY method: structural coherence + empirical adequacy

Verification is not deference to authority. It has two parts: structural
(is the specification self-consistent?) and empirical (do the claimed
tensions match the actual tensions from practice?).

**Structural verification (wiring diagram):**
1. Translate pattern conclusions into a typed directed graph (components =
   abstract roles, edges = typed wires, annotations = timescales + constraints)
2. Run all 8 checks: completeness, coverage, orphan inputs, type safety,
   spec coverage, timescale ordering (I3), exogeneity (I4), closure (I6)
3. Compare against the concrete diagram to surface structural gaps
4. Check composability between abstract and concrete to confirm feedback paths
5. Document findings — the diagram produces structural information, not just
   pass/fail

**Empirical verification (git archaeology / pattern graph):**
1. Trace the development history (git log, commit messages, branch structure)
   for evidence that the theoretical tensions match real development tensions
2. Check each claimed tension (E1-E7) against the historical record
3. Look for emergent patterns not predicted by any single tension but visible
   in their combination (e.g. the structural cycle)
4. Note where the linear git log is insufficient and what a pattern phylogeny
   graph would add (typed derivation edges, construction proofs, confidence)

In the future, the empirical step should evolve from git archaeology (manual,
linear, without theoretical salience) to pattern graph traversal (automated,
typed, with derivation structure). This is the difference between manually
running L1-observe and having L1-observe built into the system.

The exotype diagram (`data/missions/coordination-exotype.edn`) IS the abstract
specification. A concrete coordination system is valid iff it projects onto
this diagram preserving structure, types, timescale separations, and invariants.

## Structural Correspondence

The six gates ARE the eight-phase proof path applied to agent coordination:

| Proof Path Phase | Gate | HTTP Analogy |
|------------------|------|--------------|
| CLOCK_IN | G5 Task Specification | - |
| OBSERVE | G5 Task Specification | 400 Bad Request |
| PROPOSE_CLAIM | G4 Agent Authorization | 403 Forbidden |
| (pattern selection) | G3 Pattern Reference | (unique to futon3) |
| APPLY_CHANGE | G2 Execution | - |
| VERIFY | G1 Validation | 409 Conflict |
| INVARIANT_CHECK | G1 Validation | 500 Internal Server Error |
| PROOF_COMMIT | G0 Evidence Durability | 503 Service Unavailable |
| CLOCK_OUT | G0 Evidence Durability | - |

Note: G3 (Pattern Reference) has no proof-path analogue because it is unique
to the coordination domain — it makes pattern use mandatory rather than
optional. This is futon3's distinctive contribution.

---

## G5: Task Specification

**Question**: "Is the task well-defined?"

**Rejects**: Missing mission context, untyped I/O, no success criteria.

### Theory Grounding

| Theory Pattern | Constraint Imposed |
|----------------|--------------------|
| proof-path (CLOCK_IN + OBSERVE) | Session must begin with declared intent and observed state |
| agent-contract (OBSERVE-BEFORE-ACT) | No action without observation phase |
| curry-howard-operational | Task must have a specification — code without spec = proof without theorem |
| mission-scoping | Bounded ownership and success criteria required |
| mission-interface-signature | Typed I/O ports, wiring diagram for mission |

### Ancestral Patterns (ad-hoc, evolved through use)

| Pattern | Namespace | Gate Relevance |
|---------|-----------|----------------|
| intent-handshake-is-binding | agent/ | Run must not start until intent restated and bound |
| scope-before-action | agent/ | Declare territory before action |
| declare-scope | musn/ | State in-bounds, out-of-bounds, exit condition |
| clock-in | fulab/ | Non-optional anchor for proof path |
| sense-deliberate-act (phase 1) | agent/ | Gather input phase |
| identifier-separation | agency/ | Transport IDs vs session IDs — typed identity |

### Patterns Needed (TO DERIVE)

- **task-shape-validation**: A coordination pattern specifying what a
  well-formed task looks like (mission reference, typed I/O, success
  criteria, scope declaration). Derived from: mission-interface-signature +
  curry-howard-operational.

- **intent-to-mission-binding**: How an incoming task request is validated
  against the mission spec that authorizes it. Derived from:
  mission-scoping + agent-contract (OBSERVE-BEFORE-ACT).

---

## G4: Agent Authorization

**Question**: "Is this agent registered, capable, and assigned?"

**Rejects**: Unregistered agent, capability mismatch, no assignment.

### Theory Grounding

| Theory Pattern | Constraint Imposed |
|----------------|--------------------|
| agent-contract (ATTRIBUTE-ALL-CHANGES) | Every change linked to agent identity |
| proof-path (PROPOSE_CLAIM) | Agent proposes what will change — requires identity |
| error-hierarchy (Layer 3) | Authorization errors surface as 403 Forbidden |
| stop-the-line | Unauthorized agents blocked, not silently degraded |

### Ancestral Patterns (ad-hoc)

| Pattern | Namespace | Gate Relevance |
|---------|-----------|----------------|
| single-routing-authority | agency/ | One routing entry per agent-id |
| self-attribution | agency/ | Agents post under own identity; server never proxies |
| identifier-separation | agency/ | Typed IDs prevent identity confusion |
| invariants (A1-identity) | agency/ | One entity per ID, no ambiguity |

### Patterns Needed (TO DERIVE)

- **capability-gate**: Agent capabilities declared at registration,
  checked against task requirements at G4. Derived from:
  agent-contract + single-routing-authority.

- **assignment-binding**: Agent must be explicitly assigned to a task
  (not self-assigned) to proceed. Analogous to futon1a's penholder
  authorization. Derived from: coordination-protocol + self-attribution.

---

## G3: Pattern Reference

**Question**: "Has a pattern been selected (or gap declared)?"

**Rejects**: No PSR created, pattern search not attempted.

**Unique to futon3** — no direct analogue in futon1a or the proof path.
This gate makes patterns mandatory, not optional. It is the structural
enforcement of what was previously a social contract.

### Theory Grounding

| Theory Pattern | Constraint Imposed |
|----------------|--------------------|
| baldwin-cycle (EXPLORE phase) | Pattern selection is constrained exploration |
| theory-as-exotype | Patterns must satisfy theory constraints |
| symbolic-geodesic | Shortest defensible path — pattern provides the defensibility |
| four-types (genotype) | PSR captures the pattern-as-genotype for the task |

### Ancestral Patterns (ad-hoc)

| Pattern | Namespace | Gate Relevance |
|---------|-----------|----------------|
| sense-deliberate-act (phase 2) | agent/ | Deliberation phase = pattern selection |
| pattern-dep | fulab/ | Make reasoning dependencies explicit |
| pattern-propose | fulab/ | If no pattern fits, propose one (gap declaration) |
| commitment-varies-with-confidence | agent/ | PSR confidence level drives execution approach |
| devmap-xref | fulab/ | Cross-reference to devmap for traceability |

### Patterns Needed (TO DERIVE)

- **mandatory-psr**: Every task entering G2 must carry a PSR. If no
  pattern matches, a gap-PSR is created (with confidence=none). The
  absence of a PSR is itself a gate rejection. Derived from:
  baldwin-cycle + symbolic-geodesic.

- **pattern-search-protocol**: How the pattern library is queried —
  hotword matching, sigil lookup, namespace filtering, confidence
  scoring. Derived from: theory-as-exotype (type-checking at meta-level).

---

## G2: Execution

**Question**: "Do the work."

The agent subprocess (Claude/Codex/human). The only component that
touches the environment. Must emit events and register artifacts.

### Theory Grounding

| Theory Pattern | Constraint Imposed |
|----------------|--------------------|
| proof-path (APPLY_CHANGE) | Execute the change |
| agent-contract (all 5 requirements) | Full contract applies during execution |
| error-hierarchy | Execution errors surface at correct layer |
| minimum-viable-events | Emit enough events for reconstruction, no more |

### Ancestral Patterns (ad-hoc)

| Pattern | Namespace | Gate Relevance |
|---------|-----------|----------------|
| sense-deliberate-act (phase 3) | agent/ | Commit action phase |
| loud-failure | agency/ | Errors surface with context, never swallowed |
| expensive-move-consent | musn/ | Wide scans require consent |
| budget-bounds-exploration | agent/ | Bounded resource use |
| escalation-cost-vs-risk | agent/ | Speed on low-stakes, protection on high |
| pause-is-not-failure | agent/ | Uncertainty surfaced, not hidden |
| state-is-hypothesis | agent/ | Self-correction enabled |
| handoff-preserves-context | agent/ | Session state to persistent state |
| coordination-has-cost | agent/ | Cost-aware coordination |

### Patterns Needed (TO DERIVE)

- **bounded-execution**: Execution must have budget (time, tokens,
  tool calls). Exceeding budget triggers pause, not silent continuation.
  Derived from: budget-bounds-exploration + minimum-viable-events.

- **artifact-registration**: Every artifact produced during execution
  must be registered (in HX) before G1. Unregistered artifacts are
  invisible to validation. Derived from: proof-path (APPLY_CHANGE must
  be traceable) + agent-contract (ATTRIBUTE-ALL-CHANGES).

---

## G1: Validation

**Question**: "Does the output satisfy the pattern's criteria?"

Creates PUR. Cross-validates where applicable. Rejects: no PUR,
criteria not met, validation not attempted.

### Theory Grounding

| Theory Pattern | Constraint Imposed |
|----------------|--------------------|
| proof-path (VERIFY + INVARIANT_CHECK) | Confirm change matches claim, invariants pass |
| symbolic-geodesic | Was the path defensible? |
| retrospective-stability | Proofs survive future refinement |
| counter-ratchet | Key counts must not drop |
| all-or-nothing (I2) | Validation succeeds completely or rejects |

### Ancestral Patterns (ad-hoc)

| Pattern | Namespace | Gate Relevance |
|---------|-----------|----------------|
| delivery-receipt | agency/ | Every operation needs explicit success/failure |
| evidence-over-assertion | agent/ | Claims require grounding — file path, trace, test |
| loud-failure | agency/ | Validation failures surface, never hidden |
| validation-enforcement-gate | sidecar/ | Centralized invariant checking at write-time |
| proof-commit | fulab/ | Commit constrained to verified proof paths |
| aif-live-scores | musn/ | Live feedback on action quality |
| provisional-claims-ledger | agent/ | Unverified claims tracked with expiry |

### Patterns Needed (TO DERIVE)

- **mandatory-pur**: Every PSR must produce a matching PUR at G1.
  Orphaned PSRs (PSR without PUR) are a detectable violation.
  Derived from: proof-path (VERIFY) + delivery-receipt.

- **cross-validation-protocol**: When applicable, a second agent
  validates the first agent's work. This is the coordination-domain
  equivalent of futon1a's two-agent signoff. Derived from:
  coordination-protocol + retrospective-stability.

---

## G0: Evidence Durability

**Question**: "Is everything persisted and resumable?"

Creates PAR. Persists session events. Emits proof path. Rejects:
session not durable, PAR not created.

### Theory Grounding

| Theory Pattern | Constraint Imposed |
|----------------|--------------------|
| proof-path (PROOF_COMMIT + CLOCK_OUT) | Durable commit + session close |
| durability-first (I0) | What you save is what you get back |
| local-gain-persistence | Gains persist or are explicitly deleted |
| event-protocol | Canonical event sequence recorded |
| retrospective-stability | Evidence survives future refinement |

### Ancestral Patterns (ad-hoc)

| Pattern | Namespace | Gate Relevance |
|---------|-----------|----------------|
| clock-out | fulab/ | Seal proof path with summary |
| notebook-cell | fulab/ | Persist prompt/response in real time |
| session-resume | fulab/ | Audit continuity across conversations |
| state-atomicity | agency/ | State transitions complete fully or fail |
| bounded-lifecycle | agency/ | Transient state cleaned up |
| changelog-trail | fulab/ | Manual records as temporary proof |
| tradeoff-record | fulab/ | Decision records for enforcement |
| blast-radius | fulab/ | Rollback risk documented |

### Patterns Needed (TO DERIVE)

- **session-durability-check**: Before G0 completes, verify that the
  session can be reconstructed from persisted events alone. If not,
  reject. Derived from: durability-first + event-protocol.

- **par-as-obligation**: Every completed proof path must produce a
  PAR (Post-Action Review). PARs are not optional reflections — they
  are the evidence that the system learns from its actions. Derived
  from: proof-path (CLOCK_OUT) + local-gain-persistence.

---

## Summary: Derivation Status

### Theory Patterns -> Gates (complete mapping)

| Theory Pattern | Gates |
|----------------|-------|
| proof-path | G5, G2, G1, G0 (spans the full chain) |
| agent-contract | G5, G4, G2 (behavioral requirements) |
| curry-howard-operational | G5 (spec required before work) |
| error-hierarchy | G4, G2, G1 (errors surface correctly) |
| stop-the-line | G4, G1 (block on violation) |
| baldwin-cycle | G3 (pattern selection as exploration) |
| theory-as-exotype | G3 (patterns type-checked against theory) |
| symbolic-geodesic | G3, G1 (defensible path) |
| four-types | G3 (PSR captures genotype) |
| minimum-viable-events | G2 (emit enough, no more) |
| durability-first | G0 (persistence invariant) |
| local-gain-persistence | G0 (gains durable) |
| retrospective-stability | G1, G0 (proofs survive refinement) |
| counter-ratchet | G1 (monotonic, no regressions) |
| all-or-nothing | G1 (validation atomic) |
| event-protocol | G0 (canonical event sequence) |
| mission-scoping | G5 (bounded ownership) |
| mission-interface-signature | G5 (typed I/O) |
| coordination-protocol | G4, G1 (multi-agent handoff) |

### Ancestral Patterns -> Gates (ad-hoc, organized by gate)

| Gate | Ancestor Count | Key Ancestors |
|------|---------------|---------------|
| G5 | 6 | intent-handshake-is-binding, scope-before-action, clock-in |
| G4 | 4 | single-routing-authority, self-attribution |
| G3 | 5 | pattern-dep, pattern-propose, sense-deliberate-act |
| G2 | 9 | loud-failure, budget-bounds-exploration, pause-is-not-failure |
| G1 | 7 | delivery-receipt, evidence-over-assertion, validation-enforcement-gate |
| G0 | 8 | clock-out, session-resume, state-atomicity |

### Patterns To Derive (10 new coordination patterns)

| Gate | Pattern | Derives From |
|------|---------|-------------|
| G5 | task-shape-validation | mission-interface-signature + curry-howard-operational |
| G5 | intent-to-mission-binding | mission-scoping + agent-contract |
| G4 | capability-gate | agent-contract + single-routing-authority |
| G4 | assignment-binding | coordination-protocol + self-attribution |
| G3 | mandatory-psr | baldwin-cycle + symbolic-geodesic |
| G3 | pattern-search-protocol | theory-as-exotype |
| G2 | bounded-execution | budget-bounds-exploration + minimum-viable-events |
| G2 | artifact-registration | proof-path + agent-contract |
| G1 | mandatory-pur | proof-path + delivery-receipt |
| G1 | cross-validation-protocol | coordination-protocol + retrospective-stability |
| G0 | session-durability-check | durability-first + event-protocol |
| G0 | par-as-obligation | proof-path + local-gain-persistence |

### Baldwin Cycle Status

```
EXPLORE (done)      ~39 ancestral patterns organized by gate
                    ~95% of library = ad-hoc exploration
                    These ARE the genotype from which theory evolved
                         |
                         v
ASSIMILATE (done)   This document: mapping ancestors to gates
                    12 coordination patterns derived (library/coordination/)
                    3 theory patterns added (task-as-arrow, retroactive-
                    canonicalization, structural-tension-as-observation)
                    ARGUMENT.flexiarg composed and verified
                    coordination-exotype.edn validates (8/8 checks pass)
                         |
                         v
CANALIZE (next)     Futonic mission written: M-coordination-rewrite.md
                    Implement gates as pipeline (futon3.gate.*)
                    Compose futon3 components + futon3a inference rules
                    Add Level 1 loop (L1-observe, L1-canon)
                    Remove freedom: patterns mandatory, not optional
                    Make success cheap: gate rejects bad input early
```

## Respect for Ancestors

The ~39 ancestral patterns listed above were not designed with gates in
mind. They emerged from practice — real sessions, real failures, real
fixes. They are the messy genotype through which the futon-theory patterns
evolved. The six-gate structure doesn't replace them; it organizes them.

Each ancestor pattern provides *evidence* that a gate is needed:
- `intent-handshake-is-binding` exists because tasks without intent
  declarations kept going wrong -> evidence that G5 is necessary
- `delivery-receipt` exists because silent failures kept corrupting
  state -> evidence that G1 is necessary
- `clock-out` exists because sessions without evidence trails were
  unreconstructable -> evidence that G0 is necessary

The ancestors stay in the library. They are referenced by the gate
patterns that subsume them. They are not deleted, but they are no
longer individually responsible for enforcement — the gates are.
