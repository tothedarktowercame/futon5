# Excursions Overview: Climbing Mount Analogue

This document maps the excursion structure for the futon5 research program.

---

## The Mountain

**Summit thesis**: Certain abstract control motifs (xenotype → exotype) steer
collective exploration into non-degenerate productive regimes across domains
under fixed budgets.

**Base camp (Mission 0)**: Instrumentation — reproducible runs, full provenance,
working scorers, budget normalization.

**Excursions**: Focused expeditions that each establish a lemma on the way to
the summit.

---

## Excursion Map

```
                                    ┌─────────────────────────────────┐
                                    │  P8: Service deployment         │
                                    │      (Andela-for-bots)          │
                                    └───────────────┬─────────────────┘
                                                    │
                                    ┌───────────────┴─────────────────┐
                                    │  P7: Formal proof search        │
                                    │      (Lean/Coq micro-domains)   │
                                    └───────────────┬─────────────────┘
                                                    │
                                    ┌───────────────┴─────────────────┐
                                    │  P6: Cross-domain invariants    │
                                    │      (motif ablation matrix)    │
                                    └───────────────┬─────────────────┘
                                                    │
                        ┌───────────────────────────┼───────────────────────────┐
                        │                           │                           │
            ┌───────────┴───────────┐   ┌───────────┴───────────┐   ┌───────────┴───────────┐
            │  P5: Platform ecology │   │  P4: Coding agents    │   │  P3: AIF ants         │
            │      (SE-for-bots)    │   │      (verifiable      │   │      (foraging,       │
            │                       │   │       katas)          │   │       ecology)        │
            └───────────┬───────────┘   └───────────┬───────────┘   └───────────┬───────────┘
                        │                           │                           │
                        └───────────────────────────┼───────────────────────────┘
                                                    │
                                    ┌───────────────┴─────────────────┐
                                    │  P2: Abstract control layer     │
                                    │      (CT/design pattern spec)   │
                                    └───────────────┬─────────────────┘
                                                    │
                        ┌───────────────────────────┴───────────────────────────┐
                        │                                                       │
            ┌───────────┴───────────┐                           ┌───────────────┴───────────┐
            │  EXCURSION 2          │                           │  EXCURSION 1              │
            │  Hexagram → Sigil     │                           │  P1: Xenotype motifs      │
            │  Promotion (256)      │                           │  improve MMCA             │
            │                       │                           │                           │
            │  Stages 6b-6e         │                           │  Stages 1-7               │
            └───────────┬───────────┘                           └───────────────┬───────────┘
                        │                                                       │
                        └───────────────────────────┬───────────────────────────┘
                                                    │
                                    ┌───────────────┴─────────────────┐
                                    │  MISSION 0 (Base Camp)          │
                                    │  P0: Instrumentation            │
                                    │                                 │
                                    │  - Reproducible runner          │
                                    │  - Full provenance logging      │
                                    │  - Budget normalization         │
                                    │  - Scorer alignment             │
                                    └─────────────────────────────────┘
```

---

## Excursion Details

| Excursion | Proof Node | Core Claim | Deliverable | Status |
|-----------|------------|------------|-------------|--------|
| Mission 0 | P0 | Instrumentation sufficient for controlled experiments | Evaluation harness | In progress |
| **Excursion 1** | **P1** | **Xenotype motifs improve MMCA trajectory health** | **Paper + 64 hexagram families** | **Next** |
| Excursion 2 | P1 ext | 64 hexagrams promote to 256 sigils systematically | Full sigil library | After E1 |
| Excursion 3 | P2 | Abstract CT spec instantiates to MMCA | Adaptation interface | After E1 |
| Excursion 4 | P3 | Same spec instantiates to AIF ants | Transfer validation | After E2/E3 |
| Excursion 5 | P4 | Motifs improve coding-agent collectives | Kata benchmarks | After E3 |
| Excursion 6 | P6 | Cross-domain invariants exist | Ablation matrix | After E4/E5 |
| Excursion 7+ | P5/P7/P8 | Platform, proof search, deployment | Various | Future |

---

## Excursion 1: P1 Xenotype Motifs (Current Focus)

**File**: `Excursion-1-P1-xenotype-motifs.md`

**Stages**:

| Stage | Claim | Key Output |
|-------|-------|------------|
| 1 | Tai-zone beats baseline | Anchor set, effect size |
| 2 | Effect is specific (ablation) | Boundary map |
| 3 | Multiple health components | Health decomposition |
| 4 | Other hexagrams work too | Hexagram taxonomy (64) |
| 5 | Synthesis can discover motifs | Top wiring diagrams |
| 6 | CT interpretation works | 64 sigil CT templates |
| 7 | Full tournament | Publishable P1 result |

**Dependencies**:
- Mission 0 (P0) must be solid before Stage 1 runs are trustworthy
- Stages 1-2 sequential; Stage 3 can parallel Stage 2
- Stages 4-5 depend on Stage 2-3
- Stage 6 depends on Stage 4-5
- Stage 7 is capstone

---

## Excursion 2: Hexagram → Sigil Promotion

**File**: `Excursion-2-hexagram-to-sigil-promotion.md`

**Stages**:

| Stage | Claim | Key Output |
|-------|-------|------------|
| 6b | Promotion rule is principled | Variant implementation |
| 6c | Variants behave as predicted | Validation results |
| 6d | Full 256 generation | `sigil-library-256.edn` |
| 6e | Documentation complete | Library index + transfer hints |

**Promotion Rule Options**:
- A: Mode bits (Observe/Nudge/Steer/Gate) — recommended first
- B: Temporal phase (Genesis/Sustain/Transition/Terminal)
- C: Polarity (Yin-soft/Yin-firm/Yang-soft/Yang-firm)
- D: Spatial context (Local-narrow/Local-wide/Global-sample/Global-aggregate)

**Dependencies**:
- Requires Excursion 1 Stages 1-6 complete
- Estimated ~30% effort of Excursion 1

---

## Future Excursions (Sketched)

### Excursion 3: Abstract Control Layer (P2)

**Claim**: CT/design pattern spec instantiates to MMCA without adding operators.

**Approach**:
- Define adaptation interface from E1/E2 sigil library
- Re-derive MMCA controller from abstract spec
- Test: constrained instantiation retains E1 gain

### Excursion 4: AIF Ant Transfer (P3)

**Claim**: Same abstract spec instantiates to AIF-ant controller.

**Approach**:
- Use transfer hints from E2 Stage 6e
- Two ecological regimes (sparse/dense)
- Fixed spec, small adaptation budget
- Compare: motif-enabled vs baseline ants

### Excursion 5: Coding Agents (P4)

**Claim**: AIF control motifs improve coding-agent collectives.

**Approach**:
- Bots Q&A + verifiable katas
- Hidden tests, procedural variants
- Strict budgets
- Compare controllers on generalization + trajectory health

### Excursion 6: Cross-Domain Invariants (P6)

**Claim**: Same motif family is necessary across ≥3 domains.

**Approach**:
- Motif-level ablation matrix (MMCA × ants × code)
- Preregistered hypotheses
- Blinded outcome labeling

---

## Success Criteria by Level

### Base Camp (Mission 0)
- [ ] Given seed+config, reruns match within tolerance
- [ ] Budgets enforced
- [ ] Full provenance logged
- [ ] Scorers align with human judgment (≥0.65 agreement)

### Excursion 1 (P1)
- [ ] Tai-zone effect size d > 0.5
- [ ] Ablation confirms specificity
- [ ] ≥3 hexagram zones produce distinct regimes
- [ ] 64 sigils with CT templates
- [ ] Publishable result

### Excursion 2 (P1 ext)
- [ ] Promotion rule is predictive (≥3/4 variants behave as expected)
- [ ] Within-hexagram coherence > across-hexagram
- [ ] Full 256 library generated
- [ ] Transfer hints documented

### Summit (P6+)
- [ ] Same motif family necessary across ≥3 domains
- [ ] Effect survives ablation in all domains
- [ ] Taxonomy of motif families with conditional applicability

---

## Current Status

| Component | Status | Blocker |
|-----------|--------|---------|
| Mission 0 (P0) | In progress | Codex working on gaps |
| Excursion 1 | Planned | Waiting for P0 |
| Excursion 2 | Planned | Waiting for E1 |
| Excursions 3+ | Sketched | Waiting for E1/E2 |

---

## Files

```
futon5/docs/
├── Excursions-overview.md          # This file
├── Excursion-1-P1-xenotype-motifs.md
├── Excursion-2-hexagram-to-sigil-promotion.md
├── Mission-0-report-20260122.md    # Base camp status
└── Mission-0-technote2b.md         # Wiring diagram approach
```

---

## Codex Handoff

**Immediate priority**: Complete Mission 0 (P0) gaps per `Mission-0-report-20260122.md`

**Next**: Begin Excursion 1 Stage 1 prep
- Create anchor seeds file
- Create arms config
- Implement Stage 1 runner script

**Parallel track**: Wiring diagram synthesis (Approach 2) feeds into E1 Stage 5
