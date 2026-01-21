# Nonstarter Exotic Adapter: Specification

**Status:** Draft
**Date:** 2026-01-17
**Context:** futon5 exotic programming basecamp, CT reuse beyond MetaCA

---

## Purpose

Provide a bidirectional adapter between:
- **Exotic programming (CT / xenotype / exotype)**, and
- **Nonstarter runtime (proposal + milestone lifecycle)**

So we can:
1) **Outbound:** run nonstarter using exotic programming techniques (xenotype shapes proposal evolution).
2) **Inbound:** interpret nonstarter traces via CT structures (vision/plan/adapt coherence).

---

## Adapter Overview

### Directions

**Outbound (Exotic → Nonstarter)**
- CT VISION + PLAN + ADAPT drive proposal generation and milestone updates.
- Xenotype ratchet shapes which proposal policies survive.

**Inbound (Nonstarter → Exotic)**
- Convert proposal/milestone traces into EXECUTION morphisms.
- Compare with VISION/PLAN functor and ADAPT naturality for interpretability.

### Runtime Model (VM)

A background loop simulates proposal lifecycle:
```
submit → review → fund → execute → deliver → verify
```
Each step emits an event and updates a state ledger.

---

## Data Model

### CT Layer (Exotic)

```clojure
{:vision {:objects #{:start :m1 :m2 :goal}
          :morphisms {:m1 {:source :start :target :m1 :ask 1000}
                      :m2 {:source :m1 :target :m2 :ask 2000}
                      :m3 {:source :m2 :target :goal :ask 1000}}}
 :plan {:m1 :in-progress :m2 :planned :m3 :planned}
 :execution {:completed [] :current :start :evidence []}
 :adaptations []}
```

### Nonstarter Layer

```clojure
{:proposal/id "p-123"
 :title "Build system X"
 :milestones [{:id :m1 :ask 1000 :status :planned}
              {:id :m2 :ask 2000 :status :planned}
              {:id :m3 :ask 1000 :status :planned}]
 :ledger [{:event :submit :t 0}
          {:event :fund :milestone :m1 :t 4}
          {:event :deliver :milestone :m1 :t 9}]
 :policy {:strategy :balanced :weights {:edge 0.7 :diversity 0.3}}}
```

---

## Mapping Rules

### Outbound: CT → Nonstarter

- `VISION.objects` → milestone graph
- `VISION.morphisms` → milestone specs (`:ask`, `:source`, `:target`)
- `PLAN` → milestone statuses (`:planned`, `:in-progress`, `:done`)
- `ADAPT` → milestone reorder/merge/split with justification

### Inbound: Nonstarter → CT

- proposals/milestones → EXECUTION objects/morphisms
- ledger events → execution morphisms (`:submit`, `:fund`, `:deliver`, `:verify`)
- policy changes → ADAPT natural transformations

---

## Event Schema

```clojure
{:event :fund
 :proposal/id "p-123"
 :milestone :m1
 :amount 1000
 :t 4}
```

Supported events:
- `:submit`, `:review`, `:fund`, `:execute`, `:deliver`, `:verify`, `:adapt`

---

## Xenotype Integration

### Scoring Hooks

- **Plan fidelity:** did execution follow PLAN ordering?
- **Adapt coherence:** do ADAPT changes commute across missions?
- **Ratchet:** reward improvement in delivery quality over windows.
- **Provenance:** reward novel proposal lineages.

### Guardrails

- Context-only shortcut check: detect score spikes without milestone progress.
- Portfolio: retain top-k per mission word-class (CT word class).

---

## Minimal API (Adapter)

```clojure
(defn vision->proposal [vision plan] ...)
(defn proposal->execution [proposal-ledger] ...)
(defn adapt->proposal [adaptation proposal] ...)
(defn ledger->ct-trace [ledger] ...)
```

---

## MVP Pilot (Basecamp)

- Implement outbound adapter for a fixed vision graph (3 missions).
- Run VM loop for N steps with synthetic funding events.
- Inbound: collect ledger and score against CT PLAN/ADAPT.
- Compare against baseline (no CT constraints).

---

## Open Questions

1) How to encode “mission completion quality” into xenotype scores?
2) What is the correct granularity of ADAPT events in nonstarter traces?
3) Should funding events be treated as stochastic or policy-driven?
4) How to align word-class conditioning with nonstarter mission types?

---

## References

- docs/exotic-programming-spec.md
- docs/exotic-programming-curriculum.md
- README-mission8.md
