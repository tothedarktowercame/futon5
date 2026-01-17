# Mission 9.5: Kolmogorov Arrow Discovery Pilot

**Status:** Active (parallel to M9-M11)
**Operators:** Claude + Joe
**Context:** ChatGPT conversation on grounding CT structures empirically

---

## Vision

Discover the actual short programs (exotype patches) that reliably transform MMCA regimes. Build an arrow graph where nodes are normalized regimes and edges are witnessed by cheap interventions.

This is "the ticket that exploded" - find the minimal cuts that blow open regime space.

---

## Core Definitions

### Terms (interventions as programs)

A term is an exotype patch - a small edit the simulator can apply:

```clojure
;; Atomic patches (the grammar)
[:set-param :noise δ]           ; adjust noise level
[:set-param :temperature δ]     ; adjust temperature
[:set-param :coupling δ]        ; adjust coupling strength
[:switch-rule k]                ; switch rule family index
[:inject-stimulus pattern-id]   ; inject a pattern
[:set-obs-grain g]              ; change observation granularity
[:tighten-threshold Δ]          ; curriculum tightening

;; Compound terms (composition)
[:seq patch1 patch2 ...]        ; sequential application
```

Cost = number of atoms + word-class penalty for common moves.

### Types (interface constraints)

A type is what makes composition meaningful:

```clojure
{:lattice-size [w h]
 :boundary :periodic | :fixed | :absorbing
 :alphabet-arity n
 :obs-family :density | :entropy | :motif | :spectrum
 :budget-class :micro | :small | :medium}
```

Terms must preserve type or explicitly declare type transitions.

### Normalization (canonicalize regimes)

Map high-entropy traces to compact descriptors:

```clojure
{:attractor-sig hash      ; hash of last N steps after burn-in
 :macro-vec [ρ H m₁ m₂]   ; density, entropy, motif counts
 :word-class :stable | :oscillating | :chaotic | :dead
 :spectrum-sig hash}      ; FFT signature
```

Different microstates that are "the same regime" collapse to the same normalized representative.

### Cost (description-length proxy)

```clojure
(defn term-cost [term word-class-weights]
  (let [atoms (count-atoms term)
        wc-penalty (get word-class-weights (classify-term term) 0)]
    (+ atoms wc-penalty)))
```

Stricter for common patterns (per exotic-programming-spec.md).

---

## Arrow Definition

A **Kolmogorov arrow** A → B exists if there is a term p with:
- cost(p) ≤ k
- when applied to runs starting in regime A
- yields normalized regime B
- with probability ≥ τ across a context set

```clojure
{:arrow/id "stable→oscillating::p17"
 :from {:regime :stable :sig "abc123"}
 :to {:regime :oscillating :sig "def456"}
 :witness {:term [:set-param :noise 0.15]
           :cost 1
           :atoms [:set-param]}
 :robustness {:tau 0.74
              :contexts 20
              :seed-set "batch-02"}
 :composition {:factors [] :residual nil}
 :evidence {:run-ids [...] :norm-hash "..."}}
```

---

## Deliverables

### Phase 1: Infrastructure (1 session)

- `src/futon5/arrow/term.clj`
  - Term grammar definition
  - `(defn cost [term])` - compute term cost
  - `(defn apply-term [state term])` - apply patch to simulator state

- `src/futon5/arrow/normalize.clj`
  - `(defn normalize [trace])` - map trace to canonical descriptor
  - `(defn regime-sig [descriptor])` - hash for equality testing
  - Word-class tagging integration

### Phase 2: Mining (1-2 sessions)

- `src/futon5/arrow/mine.clj`
  - `(defn generate-candidates [grammar k])` - enumerate terms up to cost k
  - `(defn test-arrow [from-regime term contexts])` - run batch, compute τ
  - `(defn mine-arrows [regimes grammar k τ])` - full mining loop

- Arrow cache (EDN or SQLite):
  - Store discovered arrows with witnesses
  - Index by (from, to) pair
  - Track shortest witness per pair

### Phase 3: Graph Analysis (1 session)

- `src/futon5/arrow/graph.clj`
  - Build arrow graph from cache
  - `(defn shortest-path [graph A B])` - find cheapest witnessed path
  - `(defn geodesic-check [graph A C B])` - does A→C→B beat direct A→B?
  - `(defn compression-gain [graph new-node])` - does adding node reduce avg path cost?

---

## Pilot Scope

Start narrow:

| Component | Scope |
|-----------|-------|
| Regimes | 3-5: `:dead`, `:stable`, `:oscillating`, `:chaotic`, `:glider` |
| Term grammar | 10-15 atomic patches |
| Max cost k | 3 atoms |
| Robustness τ | 0.70 |
| Context set | 20 seeds per test |
| Lattice | 32x32, periodic boundary |

---

## Success Criteria

1. **Non-degeneracy**: Not everything maps to everything. Some A→B pairs have no cheap witness.

2. **Composition works**: Found A→B and B→C implies we can test A→C via composition, and it behaves predictably.

3. **Geodesics exist**: Some indirect paths A→X→B are cheaper than direct A→B. This means X is a useful "symbol."

4. **Compression signal**: Adding good intermediate nodes reduces average description length of paths.

---

## Connection to M9-M18

| Arrow Discovery | Exotic Programming |
|-----------------|-------------------|
| Discovered arrows | Inform VISION templates (M12) |
| Arrow costs | Ground provenance scoring (M15) |
| Composition residuals | Empirical functor preservation test |
| Geodesic structure | Suggest mission decomposition |
| Compression gain | Validate "good intermediate symbols" |

The arrow graph becomes an artifact that M12+ consumes. It's the empirical basis for the declared CT structure.

## Integration Hook (CT Metrics)

Arrow discovery provides empirical inputs for CT metrics:
- arrow cost → provenance proxy
- robustness τ → evidence grounding
- composition residuals → functor preservation tests

These outputs should be referenced in `docs/ct-metrics-report-plan.md` when calibrating real CT metrics.

---

## Logging Format

```clojure
;; Per-arrow discovery event
{:event :arrow-discovered
 :arrow/id "..."
 :timestamp ...
 :mining-batch "..."
 ...}

;; Per-session summary
{:event :mining-session
 :arrows-tested n
 :arrows-discovered m
 :best-new-arrow {...}
 :graph-stats {:nodes n :edges m :avg-path-cost c}}
```

---

## The Burroughs Angle

"The ticket that exploded" - we're looking for the minimal cuts that fragment the regime space into navigable pieces. Each arrow is a cut-up operation. The grammar is the word hoard. The geodesics are the fold-ins that make new meaning.

If this works, the arrow graph is a kind of generative grammar for MMCA behavior - not designed, but discovered.

---

## Open Questions

1. **What's the right normalization?** Too coarse = false equivalences. Too fine = no compression.

2. **How to handle stochastic regimes?** τ threshold helps, but some regimes are inherently high-variance.

3. **Does the grammar have enough leverage?** If no arrows exist at k≤3, need to expand grammar or raise k.

4. **Can we visualize the arrow graph usefully?** Node = regime, edge = arrow, edge weight = cost.

---

## Meta-Note

This mission is itself a Kolmogorov arrow: a short intervention (this spec) that transforms the project state from "CT structure is declared" to "CT structure is empirically grounded."

Cost: 1 document. Robustness: TBD.
