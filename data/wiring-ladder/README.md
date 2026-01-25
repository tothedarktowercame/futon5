# Wiring Ladder: Compositional Build-Up Experiment

## Principle

Don't test complex wirings without understanding their components. Build up incrementally:

1. Start with simplest possible wiring
2. Add one component at a time
3. Test at each level
4. Record what each addition contributes

## The Ladder

```
Level 0: baseline
   No exotype. Raw CA dynamics.

Level 1: +legacy-kernel
   Just the legacy kernel step (工 params).
   This is the known-good baseline.

Level 2: +context
   Legacy kernel + explicit context extraction.
   Should behave same as L1 (refactoring, not new behavior).

Level 3: +diversity-sense
   Add diversity measurement of neighbors.
   Still uses legacy output, but now MEASURES diversity.

Level 4: +gate
   Add threshold gate that READS diversity.
   Gate output = legacy (diversity doesn't change output yet).

Level 5: +creative-path
   Add creative component (e.g., XOR).
   Gate now CHOOSES between legacy and creative based on diversity.
   This is the full boundary-guardian pattern.
```

## What We Learn At Each Level

| Level | Addition | Question Answered |
|-------|----------|-------------------|
| 0→1 | legacy kernel | Does exotype help at all? |
| 1→2 | context nodes | Does explicit context matter? (should be neutral) |
| 2→3 | diversity sense | Does measuring diversity change anything? (should be neutral) |
| 3→4 | gate | Does having a gate structure matter? (should be neutral) |
| 4→5 | creative path | Does the creative alternative help or hurt? |

If behavior changes at L2, L3, or L4 when it shouldn't, we have a bug.
If behavior doesn't change at L5, the creative path isn't activating.

## Standard Test Protocol

For each level:
- Seeds: [4242, 238310129, 352362012]
- Widths: [100, 120]
- Generations: 100
- Classify with run_health_report.clj
- Record in wiring-outcomes.edn

## File Structure

```
data/wiring-ladder/
  README.md           # This file
  level-0-baseline.edn
  level-1-legacy.edn
  level-2-context.edn
  level-3-diversity.edn
  level-4-gate.edn
  level-5-creative.edn
```

## Expected Outcome

By the end we'll know:
1. Whether exotypes help vs raw CA
2. Whether the compositional structure matters
3. Exactly which component (if any) prevents/causes barcode collapse
4. Whether creative paths help or hurt

This replaces "wiring soup" with systematic understanding.
