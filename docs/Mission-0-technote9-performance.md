# Mission 0 Technote 9 — Exotype Performance + Evaluation Plan

Date: 2026-01-24

## Purpose

We keep the current eigenvalue-based hexagram extraction as the default behavior.
This technote proposes a performance investigation plan and a set of alternative
mappings to test as hypotheses, not replacements.

## Current Behavior (Keep)

Per cell, per tick:
1. Build 36-bit context from LEFT/EGO/RIGHT/NEXT + phenotype family (4 bits)
2. Convert context to 6x6 matrix
3. QR-iterate to estimate eigenvalues
4. Sign of eigenvalues -> hexagram lines
5. Hexagram + energy -> rule (0-255)
6. Rule -> kernel + params -> evolve cell

## Why Performance Might Matter

- Eigenvalue extraction is called for every cell and tick in local evolution.
- QR iteration uses float math and repeated matrix multiplications.
- Near-zero eigenvalues can introduce instability, causing rule jitter.

We want data about actual runtime cost and stability impact, not assumptions.

## Hypotheses to Test (Not Adopted Yet)

These are candidate mappings that remove eigenvalues. They should be evaluated
against the current method for both performance and behavioral fidelity.

1) Fixed linear projections
   - Predefine 6 signed dot-product vectors over 36 bits
   - Line bit = sign(sum)

2) Row/column/quadrant sums
   - Derive 6 line bits from simple region sums

3) Bit-group hashing
   - Partition 36 bits into 6 groups of 6
   - Line bit = parity or majority of each group

4) Approximate spectral
   - Power iteration on symmetricized matrix (A + A^T)
   - Use top-k signs + aggregate bits to form 6 lines

5) Keep eigenvalues + cache
   - Memoize context->physics-rule by 36-bit key
   - LRU cache sized for typical run history

## Evaluation Plan

Performance:
- Compare wall-clock runtime for identical seeds and runs
- Measure time in context->physics-rule and overall run-mmca

Behavioral fidelity:
- Compare hexagram distribution (counts per 1-64)
- Compare energy distribution (Peng/Lu/Ji/An)
- Compare run-level metrics (fitness, stability, diversity)

Stability:
- Track rule jitter rate per cell across ticks
- Evaluate if near-zero eigenvalue cases correlate with instability

## Non-Goals

- No change to default behavior unless data shows a clear win
- No claims about "better" physics without measurement

## Next Steps (If Approved)

- Add a simple benchmarking harness to time current mapping
- Add a feature-flagged alternate mapping for A/B comparison
- Capture metrics + hexagram distributions for a small seed set
