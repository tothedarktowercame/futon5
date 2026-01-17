# Exotic Programming: Development Curriculum

**Vision:** Implement and validate exotic programming as specified in `exotic-programming-spec.md`

**Total scope:** ~10 missions
**Adaptation policy:** Review after every 2-3 missions; adjust based on evidence

---

## VISION Category (declared structure)

```
:start → :ct-foundation → :xenotype-integration → :ratchet → :contemplative → :validation → :goal

Missions:
  M9:  :start → :ct-foundation        (CT scaffolding)
  M10: :ct-foundation → :plan-functor (PLAN implementation)
  M11: :plan-functor → :adapt-nat     (ADAPT + naturality)
  M12: :adapt-nat → :xenotype-integration (wire into MMCA)
  M13: :xenotype-integration → :ratchet-memory (memory layer)
  M14: :ratchet-memory → :ratchet-curriculum (tightening thresholds)
  M15: :ratchet-curriculum → :contemplative (provenance, worthiness)
  M16: :contemplative → :validation   (test vs M7/M8 baselines)
  M17: :validation → :refinement      (iterate based on results)
  M18: :refinement → :goal            (documented, working system)
```

---

## Mission 9: CT Scaffolding

**Goal:** Implement VISION and EXECUTION categories as data structures.

**Deliverables:**
- `src/futon5/exotic/category.clj`
  - `(defrecord Category [objects morphisms])`
  - `(defn compose [cat m1 m2])` - morphism composition
  - `(defn valid-category? [cat])` - check laws
- `src/futon5/exotic/vision.clj`
  - Vision declaration helpers
  - Mission decomposition

**Success criteria:**
- Can declare a vision with objects and morphisms
- Composition is associative
- Identity morphisms work

**Ask:** 1 session

---

## Mission 10: PLAN Functor

**Goal:** Implement the PLAN functor mapping VISION → EXECUTION.

**Deliverables:**
- `src/futon5/exotic/functor.clj`
  - `(defrecord Functor [source target obj-map mor-map])`
  - `(defn apply-functor [f obj-or-mor])`
  - `(defn preserves-composition? [f cat])` - functor law check
- `src/futon5/exotic/plan.clj`
  - PLAN-specific functor
  - Alignment scoring (how well does execution match plan?)

**Success criteria:**
- PLAN functor can be declared
- Preservation of composition is checkable
- Alignment score is computable

**Ask:** 1 session

**Depends on:** M9

---

## Mission 11: ADAPT Natural Transformation

**Goal:** Implement strategy adaptation with naturality checking.

**Deliverables:**
- `src/futon5/exotic/natural.clj`
  - `(defrecord NatTrans [source-functor target-functor components])`
  - `(defn naturality-square [nt morphism])` - check commutativity
  - `(defn justified? [nt evidence])` - coherence check
- `src/futon5/exotic/adapt.clj`
  - ADAPT-specific logic
  - Justification logging

**Success criteria:**
- Can declare an adaptation between plans
- Naturality violation is detectable
- Adaptations are logged with justification

**Ask:** 1-2 sessions

**Depends on:** M10

---

## Mission 12: Xenotype Integration

**Goal:** Wire CT structures into existing MMCA xenotype evaluation.

**Deliverables:**
- Update `src/futon5/mmca/exotype.clj`
  - Add `:exotic` xenotype tier
  - Integrate vision/plan/adapt into evaluation
- `src/futon5/exotic/scoring.clj`
  - `(defn exotic-score [genotype xenotype-state])`
  - Weighted combination: vision-clarity, plan-fidelity, mission-progress, adapt-coherence

**Success criteria:**
- Exotic xenotype runs in existing MMCA pipeline
- Scores are computed and logged
- CT structure is inspectable in output

**Ask:** 2 sessions

**Depends on:** M11

---

## Mission 13: Ratchet Memory

**Goal:** Add windowed memory for delta-scoring.

**Deliverables:**
- `src/futon5/exotic/ratchet.clj`
  - Window state tracking
  - `(defn update-window! [state entries])`
  - `(defn delta-score [prev-window curr-window])`
- Update scoring to include delta component

**Success criteria:**
- Windows are tracked across runs
- Delta-scores reward improvement
- Regression is penalized

**Ask:** 1 session

**Depends on:** M12

---

## Mission 14: Ratchet Curriculum

**Goal:** Add tightening thresholds over time.

**Deliverables:**
- `src/futon5/exotic/curriculum.clj`
  - `(defn curriculum-threshold [window-idx base])`
  - Schedule: tighten every N windows
  - Word-class conditioning (stricter for common patterns)

**Success criteria:**
- Thresholds tighten over windows
- Different word-classes have different schedules
- Tightening is logged

**Ask:** 1 session

**Depends on:** M13

---

## Mission 15: Contemplative Layer

**Goal:** Add provenance and worthiness evaluation.

**Deliverables:**
- `src/futon5/exotic/contemplative.clj`
  - `(defn provenance-score [genotype history])` - path novelty
  - `(defn worthiness-score [genotype score method])` - earned vs hacked
  - `(defn inheritance-check [genotype descendants])` - do children score well?
- Context robustness checking

**Success criteria:**
- Novel paths are rewarded
- Context-only shortcuts are penalized
- Lineage effects are tracked

**Ask:** 1-2 sessions

**Depends on:** M14

---

## Mission 16: Validation vs Baselines

**Goal:** Test exotic xenotype against Mission 7/8 baselines.

**Deliverables:**
- Run A/B comparison:
  - A: Current best (Regime B from M7)
  - B: Exotic xenotype (full stack from M9-M15)
- Overnight runs (5k evaluations each)
- Windowed analysis: does B show upward drift?

**Success criteria:**
- B shows statistically significant upward trend
- B has lower regression rate
- CT structure is interpretable in high-scoring runs

**Ask:** 2 sessions (1 setup, 1 analysis)

**Depends on:** M15

---

## Mission 17: Refinement

**Goal:** Iterate based on M16 results.

**Deliverables:**
- Document what worked / didn't work
- Adjust weights, thresholds, curriculum schedule
- Re-run validation if changes are significant

**Success criteria:**
- Clear understanding of exotic xenotype behavior
- Documented parameter sensitivity
- Stable configuration identified

**Ask:** 1-2 sessions

**Depends on:** M16

---

## Mission 18: Documentation and Handoff

**Goal:** Complete documentation, clean code, ready for wider use.

**Deliverables:**
- Update `exotic-programming-spec.md` with learnings
- Code review and cleanup
- Usage examples in notebook
- README for exotic programming module

**Success criteria:**
- Someone unfamiliar can understand and use the system
- Spec matches implementation
- Examples are reproducible

**Ask:** 1 session

**Depends on:** M17

---

## Adaptation Checkpoints

Review and potentially adapt after:

- **After M11:** Is CT structure too heavy? Simplify?
- **After M14:** Is ratchet showing any signal? Adjust windows?
- **After M16:** What did validation reveal? Major pivot needed?

Each checkpoint logs:
```clojure
{:checkpoint :after-M11
 :evidence [...]
 :adaptation {:from :plan-v1 :to :plan-v2 :justification "..."}
 :naturality-check :passed}
```

---

## Resource Estimate

| Mission | Sessions | Cumulative |
|---------|----------|------------|
| M9 | 1 | 1 |
| M10 | 1 | 2 |
| M11 | 1-2 | 3-4 |
| M12 | 2 | 5-6 |
| M13 | 1 | 6-7 |
| M14 | 1 | 7-8 |
| M15 | 1-2 | 8-10 |
| M16 | 2 | 10-12 |
| M17 | 1-2 | 11-14 |
| M18 | 1 | 12-15 |

**Total:** 12-15 sessions

---

## Meta-Note

This curriculum is itself structured as the spec describes:
- VISION declared (the goal)
- Missions decomposed (M9-M18)
- Dependencies explicit (DAG structure)
- Adaptation checkpoints scheduled
- Success criteria per mission

We are eating our own cooking.
