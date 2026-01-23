# Mission 0 Technote 4 — Exotype Lift Audit (P1 Gate)

Date: 2026-01-23

## Summary
We audited the current exotype implementation against the intended “sigil → stable physics” model. The current MMCA exotype path is a **contextual kernel transformer** that *steers* kernel specs over time; it does not currently implement a documented 8‑bit → 36‑bit physics expansion. This is a mismatch with the original 256ca intent (Baldwin/Blending/phenotype feedback as run‑long physics). As a result, Approach 3 Phase 1 (Excursion 1) should be considered **not ready** until the lift spec is made explicit and aligned.

This audit therefore introduces a new **P1 Readiness Gate**: exotype lift semantics must be explicit and stable before Stage 1 runs can be treated as definitive.

## Evidence (files reviewed)
- `futon5/src/futon5/mmca/exotype.clj`
- `futon5/resources/exotype-program-manifest.edn`
- `futon5/resources/exotype-xenotype-lift.edn`
- `futon5/256ca.el`

## What the current exotype lift does
**Implementation**: `futon5/src/futon5/mmca/exotype.clj`
- Sigil bits (8) are mapped into parameters:
  - `:rotation` (2 bits)
  - `:match-threshold` (3 bits)
  - `:invert-on-phenotype?` (1 bit)
  - `:update-prob` (2 bits)
  - `:mix-mode` (derived from full byte)
  - `:mix-shift` (derived from full byte)
- `apply-exotype` samples a **context** from run history and **mutates kernel specs** with probability `update-prob`.
- `:super` tier adds `:mix-mode` and `:mix-shift`.

**Interpretation**: This is *kernel steering* via contextual mutation, not a run‑long “physics” as originally intended.

## What the exotype program manifest encodes
**Data**: `futon5/resources/exotype-program-manifest.edn`
- Every entry is a `:kernel-transformer` with the same template `:contextual-mutate+mix`.
- Domain/codomain:
  - inputs: `[:kernel-spec :context :params :rng]`
  - output: `[:kernel-spec]`
- No separate “physics families” (Baldwin/Blending/etc.) are encoded as distinct templates.

**Interpretation**: The manifest is coherent with the kernel‑steering model, but it does not provide a 36‑bit physics expansion or a typed exotype space.

## What the xenotype lift registry encodes
**Data**: `futon5/resources/exotype-xenotype-lift.edn`
- Maps sigils to **pattern IDs** and optional **CT templates**.
- This registry is not used in the MMCA exotype runtime (no functional linkage from CT templates to kernel updates).

**Interpretation**: CT metadata exists, but it does not drive the CA physics.

## What 256ca originally implies
**Source**: `futon5/256ca.el`
- Exotype‑like “physics” examples:
  - Baldwin effect (entropy‑conditional mutation levels)
  - Blending, phenotype feedback
  - A “generalised template” derived from phenotype heredity data
- Emphasis on **local rule evolution** and explicit physics families.

**Interpretation**: Original intent is closer to **stable physics with local modulation**, rather than high‑frequency kernel steering.

## Mismatch summary
| Aspect | Intended (256ca) | Current (MMCA exotype) |
|---|---|---|
| Exotype semantics | Run‑long physics family | Kernel steering via contextual mutation |
| Lift expansion | 8‑bit → richer physics (36‑bit) | 8‑bit → small parameter vector |
| CT templates | Should inform physics | Stored but unused in runtime |
| Mutation | Optional composition of physics | Frequent kernel ID changes by design |

## P1 Readiness Gate (new)
**Gate condition:** A documented and enforced lift spec must exist that maps sigils to a *stable physics profile* (with optional local modulation), and the runtime must honor that spec.

Until then, Stage 1/Excursion results are exploratory rather than definitive.

## Proposed next steps
1. **Define a canonical lift spec** (explicit 8‑bit → 36‑bit or typed fields).
   - Must specify which parts are physics (stable) vs modulation (contextual).
2. **Split or rename exotype types**:
   - `exotype-physics` (run‑long regime)
   - `exotype-steering` (contextual kernel changes)
3. **Map current sigil lift into the spec** and document any lossy approximations.
4. **Connect CT templates to physics** (even if only via a simple mapping stub).
5. **Re‑run Stage 1 only after the above holds.**

## Impact
This audit explains why Stage 1 results may not reflect the intended sigil physics: we are currently evaluating steering policies rather than stable exotype regimes. This does not invalidate the CA work, but it does mean **interpretation and reproducibility claims must wait** until the lift spec is aligned.
