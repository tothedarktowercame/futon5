# Mission 0 Technote 6 — 36-bit Exotype Fix

Date: 2026-01-23

## The Problem

The exotype structure is **36 bits**:
- LEFT: 8 bits (IF / preconditions)
- EGO: 8 bits (BECAUSE / rationale)
- RIGHT: 8 bits (HOWEVER / risks)
- NEXT: 8 bits (THEN / outcomes)
- PHENOTYPE: 4 bits (evidence)

But the current implementation only uses **8 bits** (the EGO sigil):

```clojure
;; exotype.clj - only uses one sigil
(defn- sigil->params [sigil]
  (let [bits (sigil-bits sigil)  ;; 8 bits only!
        ...]
    {...}))
```

This is a **68 billion to 256** compression that throws away the design pattern structure.

## What Exists But Isn't Connected

**hexagram/lift.clj** already defines:
- `context->segments` — extracts LEFT/EGO/RIGHT/NEXT/PHENOTYPE
- `exotype->6x6` — arranges 36 bits into matrix
- `diagonal` — extracts the 6-bit trace
- `context->hexagram` — the full lift

But **this namespace is never required** by the runtime. The functions exist but aren't called.

## The Fix

### Step 1: Connect hexagram/lift to exotype

```clojure
;; In exotype.clj, add:
(:require [futon5.hexagram.lift :as hex-lift])

(defn context->physics-family
  "Derive physics family from full 36-bit context.
   Returns hexagram ID (0-63) as the stable physics identifier."
  [context]
  (let [hexagram (hex-lift/context->hexagram context)]
    {:hexagram-id (:number hexagram)
     :hexagram-name (:name hexagram)
     :lines (:lines hexagram)}))
```

### Step 2: Define physics by hexagram, not by sigil params

Current (wrong):
```clojure
(defn apply-exotype [kernel exotype context rng]
  (let [params (sigil->params (:sigil exotype))  ;; 8 bits only
        ...])
```

Fixed:
```clojure
(defn apply-exotype [kernel exotype context rng]
  (let [;; Full 36-bit physics family
        physics (context->physics-family context)
        hexagram-id (:hexagram-id physics)

        ;; Physics family determines stable behavior
        physics-params (hexagram->physics-params hexagram-id)

        ;; EGO sigil only provides local modulation
        modulation-params (sigil->modulation (:sigil exotype))
        ...]
```

### Step 3: Map hexagrams to physics families

The 64 hexagrams can group into physics families. For example:

| Hexagram Group | Physics Family | Behavior |
|----------------|---------------|----------|
| 1, 2 (乾坤 Creative/Receptive) | Foundation | Stable, structural |
| 11, 12 (泰否 Peace/Standstill) | Transition | Phase change dynamics |
| 29, 30 (坎離 Water/Fire) | Tension | Edge-of-chaos |
| 63, 64 (既濟未濟 Complete/Incomplete) | Boundary | Completion/renewal |

Or use the eight energies as a coarser grouping (64 → 8):

| Hexagram Range | Energy | Physics |
|----------------|--------|---------|
| 1-8 | Péng | Expansion |
| 9-16 | Lǚ | Conservation |
| 17-24 | Jǐ | Focus |
| 25-32 | Àn | Momentum |
| 33-40 | Cǎi | Phenotype-conditional |
| 41-48 | Liè | Differentiation |
| 49-56 | Zhǒu | Adjustment |
| 57-64 | Kào | Consolidation |

### Step 4: Integrate with flexiarg semantics

The 36-bit structure maps to design patterns:

```
IF (LEFT) ────────────────────────┐
                                  │
BECAUSE (EGO) ──────────┐         │
                        ▼         ▼
                   ┌─────────────────┐
                   │   6x6 MATRIX    │
                   │   (exotype)     │
                   └─────────────────┘
                        ▲         ▲
HOWEVER (RIGHT) ────────┘         │
                                  │
THEN (NEXT) ──────────────────────┘

PHENOTYPE: evidence bits scattered in lower triangle
DIAGONAL: hexagram trace (physics family identifier)
```

This means:
- A pattern's IF/BECAUSE/HOWEVER/THEN fields define its 36-bit exotype
- The diagonal trace identifies which of 64 physics families it belongs to
- The hexagram is the **design pattern family**, not just a label

## Implementation Priority

1. **Add require** for hexagram/lift in exotype.clj
2. **Add `context->physics-family`** function
3. **Modify `apply-exotype`** to use full context, not just sigil
4. **Define `hexagram->physics-params`** mapping (even if initially coarse)
5. **Test** that different contexts produce different physics families

## What This Fixes

| Before | After |
|--------|-------|
| 8-bit sigil → params | 36-bit context → hexagram → physics |
| Same params for all contexts | Context-dependent physics family |
| hexagram/lift unused | hexagram/lift is core |
| Design pattern structure ignored | IF/BECAUSE/HOWEVER/THEN drive physics |

## The Key Insight

> A sigil is just the EGO — the self. But an exotype is a **situation**:
> who comes before (LEFT/IF), the self (EGO/BECAUSE), who comes after (RIGHT/HOWEVER),
> and what results (NEXT/THEN), all conditioned by evidence (PHENOTYPE).

The physics family should be determined by the **situation** (hexagram), not just the **self** (sigil).
