# Mission 0 CHECKPOINT — Required Invariants

Date: 2026-01-24

## The Problem

The Mission-0-report-20260122.md and all prior work was **optimizing the wrong thing**.

The old exotype system:
- Used 8-bit sigil → params
- Applied ONE global kernel to ALL cells
- Was "kernel steering", not "physics"

The scoring, ratcheting, phase plans, etc. were all tuned against this broken system.

**No amount of banging our head against those steps was going to work.**

## What We Fixed Today

1. **36-bit exotype lift**: eigenvalue diagonalization → hexagram → energy → rule
2. **Local per-cell physics**: each cell computes its own 36-bit context → rule
3. **Global + local bending**: global rule modulates all local rules
4. **Composition**: sequential, blend, matrix operators

## Required Invariants Before Proceeding

### Invariant 1: Exotypes Are Real

**Test**: When we run MMCA, each cell MUST compute its local 36-bit context and apply the corresponding physics rule.

```clojure
;; MUST use:
(exotype/evolve-string-local genotype phenotype prev-genotype kernels)
;; or:
(exotype/evolve-with-global-exotype genotype phenotype prev-genotype global-rule bend-mode kernels)

;; MUST NOT use:
;; Old exotype/apply-exotype with random context sampling
;; Old global kernel steering
```

**Verification**:
- Add `:physics-rule` to each cell's output
- Log which rules were used per generation
- Assert rules vary by cell (not all same)

### Invariant 2: Xenotypes Evolve Real Physics

**Test**: Xenotype evolution MUST search over the 256-rule space (or compositions thereof), not just scoring specs.

```clojure
;; Xenotype = global rule that bends local physics
;; Evolution = mutation/selection over global rules
;; Fitness = run outcomes with that global rule
```

**Verification**:
- Log which global rule each xenotype represents
- Verify runs use that global rule
- Verify evolution produces different global rules

### Invariant 3: No Mixing Old and New

**Test**: The runtime MUST NOT accidentally use old code paths.

**Verification**:
- Deprecation warnings on old functions
- Runtime assertion that local physics is being used
- Test suite that fails if old paths are taken

### Invariant 4: Scoring Is Against Real Runs

**Test**: Any scoring/evaluation MUST operate on runs produced by the new system.

**Verification**:
- Run metadata includes `:exotype-system :local-physics`
- Reject runs that don't have this flag
- Re-baseline all scoring against new runs

## What To Do Before ANY More Compute

1. **Add runtime assertions** that verify local physics is active
2. **Create a minimal test** that runs MMCA with local exotypes and logs per-cell rules
3. **Verify the test** shows different rules per cell, not uniform
4. **Update xenoevolve** to use new exotype system
5. **Mark old code paths as deprecated** with warnings

## What NOT To Do

- Do NOT run the old scoring/ratchet pipeline
- Do NOT trust any prior "good runs" — they used the wrong physics
- Do NOT optimize scoring weights until we have real runs to score
- Do NOT assume any prior work transfers — it was against the wrong system

## Clean Slate

The prior Mission-0-report-20260122.md should be considered **obsolete**.

New plan:
1. Verify invariants (this document)
2. Create minimal working demo of local exotype physics
3. Run MMCA with real physics, capture outputs
4. THEN think about scoring/evaluation
5. THEN think about xenotype evolution

**No more early optimization against the wrong system.**
