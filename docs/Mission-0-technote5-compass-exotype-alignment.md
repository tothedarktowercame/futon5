# Mission 0 Technote 5 — Compass-Informed Exotype Alignment

Date: 2026-01-23

## Context

Technote 4 identified a mismatch between the intended exotype semantics (stable physics families) and the current implementation (kernel steering via contextual mutation). This note proposes that the futon3a compass work provides a conceptual model that could resolve this gap.

## The Compass Two-Layer Model

The compass implements a clean separation that maps to the 256ca intent:

| Compass Layer | Semantics | Timescale | 256ca Analog |
|---------------|-----------|-----------|--------------|
| **Policy** (exploit/explore/balanced) | Stable stance, run-long | Full session | Physics family |
| **Energy** (八勁 selection per step) | Local modulation | Per-step | Contextual parameter |

This is exactly the split technote 4 called for:
- `exotype-physics` ↔ compass policy
- `exotype-steering` ↔ compass energy selection

## Why the Compass Model Works

### 1. Policy is Stable

A compass policy doesn't change every tick. When the compass recommends "explore", that's a *stance* that conditions the entire session:

```clojure
;; Policy defines the regime, not per-tick behavior
{:id :explore
 :description "Investigate risks and expand scope understanding"
 :mutation-rate 0.5    ; higher than exploit
 :strategy :explore}   ; stable identity
```

This matches 256ca's "run-long physics" intent.

### 2. Energies Are Physics Families

The eight energies (Péng, Lǚ, Jǐ, Àn, Cǎi, Liè, Zhǒu, Kào) each have characteristic dynamics:

| Energy | Character | CA Analog |
|--------|-----------|-----------|
| Péng (ward off) | Expand from rooted base | High mutation, boundary growth |
| Lǚ (roll back) | Yield and acknowledge | Low mutation, information preservation |
| Jǐ (press) | Focus on single point | Selective rule activation |
| Àn (push) | Aggressive forward | High change-rate |
| Cǎi (pluck) | Ground in feedback | Phenotype-conditional |
| Liè (split) | Separate, trade-offs | Rule differentiation |
| Zhǒu (elbow) | Small adjustment | Conservative tweak |
| Kào (lean) | Structural consolidation | Majority/voting rules |

These map naturally to the 256ca physics examples:
- **Baldwin effect** ↔ Cǎi (phenotype-conditional)
- **Blending** ↔ Kào (majority)
- **Generalized template** ↔ Policy + energy composition

### 3. Documented Semantics

The compass model has explicit documentation (library/eight-gates/, docs/README-style-guide.md) that explains *why* each energy behaves as it does. The current exotype lift lacks this — it's arbitrary bit-slicing without semantic justification.

## Proposed Exotype Redesign

### Level 1: Physics Family (Stable)

Map the 8-bit sigil to one of 8 physics families (3 bits) plus family-specific parameters (5 bits):

```
Sigil: [FFF PPPPP]
        │   └───── Family-specific parameters (5 bits)
        └───────── Physics family (3 bits, 0-7)
```

Physics families derived from eight energies:

| Family ID | Energy | Core Behavior |
|-----------|--------|---------------|
| 0 | Péng | Expansion, boundary growth |
| 1 | Lǚ | Conservation, yield |
| 2 | Jǐ | Focus, selective activation |
| 3 | Àn | Momentum, aggressive change |
| 4 | Cǎi | Phenotype-conditional |
| 5 | Liè | Differentiation, splitting |
| 6 | Zhǒu | Conservative adjustment |
| 7 | Kào | Consolidation, voting |

### Level 2: Family Parameters (5 bits)

Each family interprets the 5 parameter bits differently:

**Family 0 (Péng/Expansion):**
- Bits 0-1: mutation-rate (0.25, 0.5, 0.75, 1.0)
- Bits 2-4: expansion-radius (1-8 cells)

**Family 1 (Lǚ/Conservation):**
- Bits 0-1: preservation-threshold (0.6, 0.7, 0.8, 0.9)
- Bits 2-4: memory-depth (1-8 generations)

**Family 4 (Cǎi/Phenotype-conditional):**
- Bits 0-1: feedback-weight (0.25, 0.5, 0.75, 1.0)
- Bit 2: invert-on-phenotype?
- Bits 3-4: lag (0-3 generations)

**Family 7 (Kào/Consolidation):**
- Bits 0-2: voting-threshold (majority, supermajority, unanimous)
- Bits 3-4: consolidation-mode (local, regional, global)

### Level 3: Stable vs Modulation

Separate the lift into two phases:

```clojure
(defn lift-physics [sigil]
  "Derive stable physics family from sigil. Called once per run."
  (let [family-id (bit-and (sigil->int sigil) 0x07)
        family (nth physics-families family-id)]
    {:family family
     :params (family-specific-params family sigil)
     :stable? true}))

(defn apply-modulation [physics context rng]
  "Optional local modulation within physics constraints. Called per-tick."
  ;; Only modulates WITHIN the physics family's allowed space
  ;; Does NOT change the family itself
  ...)
```

This enforces the key invariant: **physics family is stable for the run**.

## Mapping to Current Implementation

The current `sigil->params` could be reinterpreted:

| Current Field | Proposed Interpretation |
|---------------|------------------------|
| `rotation` | Within-family orientation (kept) |
| `match-threshold` | Family-specific selectivity (reinterpreted per family) |
| `invert-on-phenotype?` | Only active for Cǎi family |
| `update-prob` | **Remove from lift** — this is steering, not physics |
| `mix-mode` | Family-specific (only for Kào/Liè) |
| `mix-shift` | Family-specific (only for Péng/Àn) |

The critical change: **remove `update-prob` from the physics lift**. This field is what makes the current implementation "kernel steering" rather than "stable physics".

## Implementation Path

1. **Define family registry** (data, not code)
   ```clojure
   ;; resources/exotype-physics-families.edn
   [{:id 0 :name :peng :description "Expansion" :params-schema {...}}
    {:id 1 :name :lu :description "Conservation" ...}
    ...]
   ```

2. **New lift function** that returns stable physics
   ```clojure
   (defn lift-physics [sigil]
     ;; Returns physics that doesn't change per-tick
     ...)
   ```

3. **Separate modulation** (optional, policy-dependent)
   ```clojure
   (defn apply-modulation [physics context]
     ;; Only affects parameters WITHIN the family
     ;; Never changes family identity
     ...)
   ```

4. **Runtime honors stability**
   - `run-mmca` takes a physics spec
   - Physics family is fixed for the run
   - Modulation is optional and bounded

## Connection to P1 Gate

This proposal addresses the P1 gate requirements:

| Requirement | How Addressed |
|-------------|---------------|
| Documented lift spec | Family registry + parameter schemas |
| Stable physics profile | Family fixed for run, not per-tick |
| Optional local modulation | Separate modulation function |
| CT template connection | Families can be linked to CT templates |

## Alignment with Original 256ca Baldwin Effect

The 256ca.el commentary defines a Baldwin effect as entropy-conditional mutation:

```
Condition 1: there is no entropy;       induce heavy mutation
Condition 2: there is moderate entropy; induce medium mutation
Condition 3: there is moderate entropy; induce low mutation
Condition 4: there is no(?) entropy;    induce no mutation
```

This is a **physics family** — it defines HOW the system responds to entropy conditions, not WHAT specific mutations happen per-tick. Compare to the current implementation:

| 256ca Intent | Current Implementation | Problem |
|--------------|----------------------|---------|
| Entropy → mutation level | `update-prob` per sigil | Not conditioned on entropy |
| Physics family selection | Same template for all | No family differentiation |
| Run-long behavior | Per-tick resampling | Unstable identity |

The eight energies provide the physics families that 256ca implied but never enumerated:

| Energy | Baldwin Analog | Entropy Response |
|--------|---------------|------------------|
| Péng | Condition 1 | Low entropy → expand aggressively |
| Lǚ | Condition 4 | Stable → preserve structure |
| Cǎi | Condition 2-3 | Moderate → phenotype-conditional |
| Kào | Condition 3 | Moderate → consolidate (reduce entropy) |

The **generalised template** concept from 256ca (deriving local rules from phenotype heredity) maps to Cǎi energy — the family that grounds mutation in phenotype feedback.

## Open Questions

1. Should the 8 families map 1:1 to eight energies, or is 8 just a convenient number?
2. How do xenotypes interact with physics families?
3. Should modulation be disabled by default for reproducibility?
4. How does the Baldwin entropy-conditional logic integrate with family selection?

## Conclusion

The compass's two-layer model (stable policy + energy modulation) provides the conceptual structure that 256ca intended but the current exotype lacks. By using the eight energies as physics families and clearly separating stable physics from optional modulation, we can resolve the trust gap identified in technote 4.

The key insight: **a sigil should identify a physics family, not a steering policy**.
