# Experiment Proposal: Wiring Structure vs Parameter Isolation

Date: 2026-01-25
Author: Claude Code
Status: Proposed

## Motivation

We have two contrasting results:

| Batch | Wiring Type | Params | Outcome |
|-------|-------------|--------|---------|
| known-good-20-jvm3 | Legacy (工, etc.) | Known-good | EoC / healthy |
| xenotype-portrait-20-jvm2 | Prototype wirings | Default | Hot / collapsed |

The prototype wirings represent significant infrastructure investment (compositional diagrams, generators, scorers). If they all produce collapse, that infrastructure is wasted.

## The Question

**Why do prototype wirings collapse when legacy wirings don't?**

Two hypotheses:

**H1 (Structure):** The prototype wiring *structures* are fundamentally broken — they produce dynamics that inevitably collapse regardless of parameters.

**H2 (Parameters):** The prototype wiring structures are fine, but their *default parameters* are in collapse zones. Injecting known-good params would fix them.

## Experimental Design

### Phase 1: Replicate the collapse

Run `prototype-1.edn` with its default parameters on seeds from the known-good set.

- Expected: Collapse (replicates xenotype-portrait-20-jvm2 finding)
- If no collapse: The problem may have been elsewhere; re-examine

### Phase 2: Inject known-good params

Take `prototype-1.edn` structure but override key parameters:
- update-prob: 0.5 (from 工)
- match-threshold: 0.44 (from 工)

Run on same seeds as Phase 1.

### Phase 3: Classify and compare

| Phase | Wiring | Params | Expected (H1) | Expected (H2) |
|-------|--------|--------|---------------|---------------|
| 1 | prototype-1 | default | collapse | collapse |
| 2 | prototype-1 | 工 params | collapse | candidate |

**If Phase 2 collapses (H1 confirmed):** The wiring structure is broken. Need to diagnose what about the structure causes collapse (maybe specific components, topological patterns, etc.)

**If Phase 2 produces candidates (H2 confirmed):** The wiring infrastructure works! We just need to seed prototypes with known-good params, then the ratchet can take over.

## Implementation Notes

Need to understand how to inject params into a prototype wiring. Options:
1. Edit the wiring EDN directly to override param values
2. Use a command-line override mechanism if the batch runner supports it
3. Create a hybrid wiring that uses prototype structure with 工 params

## Success Criteria

Per AGENTS.md:
- **Promote** (to "known-working"): If Phase 2 produces candidates on ≥4/5 seeds
- **Discard** (H1): If Phase 2 still collapses on ≥3/5 seeds

## Why This Matters

If H2 is true, we unlock the entire wiring composition system. The sophisticated machinery (generators, scorers, Mermaid diagrams, wiring interpreter) becomes useful rather than decorative.

If H1 is true, we learn that wiring structure matters and can start investigating what structural properties cause collapse vs EoC.

Either way, we learn something actionable.

## Next Steps

1. Examine `prototype-1.edn` to understand its structure and default params
2. Create experiment config for Phase 1 and 2
3. Run and classify
4. Report findings
