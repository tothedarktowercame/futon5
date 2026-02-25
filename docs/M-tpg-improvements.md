# Mission: TPG Improvements for Meaningful Non-RL Control

Extend the futon5 TPG controller so it preserves the useful structural ideas from
classic TPG work while remaining faithful to futon5's non-RL verifier-guided
setting.

## Status

Proposed.

## Owner

futon5 team (drafted with Codex).

## Motivation

futon5 already uses TPG as a modular routing layer, but several high-value TPG
ideas are either simplified or absent in the current implementation:

- No explicit traversal policy variants (only depth-bound fallback routing).
- No delayed team-pointer introduction to encourage early specialization.
- No explicit internal memory channel separating context computation from
  action decision.
- No side-by-side evolution of traversal policies.

At the same time, futon5 is intentionally not RL-first: control quality is
judged by verifier satisfaction, structural invariants, and reproducibility.
This mission pushes TPG where it matches futon5's goals: interpretable modular
control with machine-checkable guarantees and measurable behavior changes.

## Problem Statement

Current futon5 TPG behavior:

- Programs bid on a 6D generation-level diagnostic and choose operator/team
  actions.
- Routing is depth-bounded with fallback.
- Team pointers are always available during mutation.
- Fitness is a verifier satisfaction vector with Pareto ranking.

This works, but it leaves capability on the table:

- Loop handling semantics are under-specified compared to node-mark / arc-mark
  alternatives.
- Early evolution can over-use pointers before teams become strong specialists.
- There is no explicit memory substrate to support partial observability or
  context accumulation.

## Hypothesis

If we add (1) configurable traversal semantics, (2) pointer-unlock scheduling,
and (3) explicit TPG memory, then evolved controllers will show:

- higher operator diversity without structural invalidity,
- better verifier satisfaction under matched compute budget,
- improved interpretability of routing behavior (clearer team roles),
- no weakening of existing invariants.

## Non-Negotiables

- No bypassing invariants or adding special-case escape hatches.
- All new behavior must pass existing TPG validity checks and associated tests.
- Any new semantics must be explicit in config and reproducible by seed.
- If invariants conflict with progress, stop and surface the conflict with
  targeted tests.

## Work Packages

### WP1: Routing Semantics as First-Class Policy

Add routing policy modes:

- `:depth-fallback` (current behavior, baseline)
- `:team-mark` (node-mark style loop handling)
- `:arc-mark` (learner/arc-mark style loop handling)

Deliverables:

- `futon5.tpg.core/route` supports `[:config :routing-policy]`.
- Routing trace records policy + loop-resolution events.
- Deterministic tests for each policy under the same synthetic graphs.

Acceptance:

- No invalid graph can crash routing.
- Same seed + same policy -> identical routing trace.
- Existing default behavior unchanged when policy not provided.

### WP2: Delayed Pointer Introduction (P0 Warmup)

Add evolution config:

- `:pointer-unlock-generation` (default `0` to preserve current behavior).

Behavior:

- Before unlock generation, mutation cannot create `:team` actions.
- After unlock generation, normal pointer mutation resumes.

Deliverables:

- Mutation path changes in `futon5.tpg.evolve`.
- Tests verifying no pointers pre-unlock, pointers allowed post-unlock.

Acceptance:

- Reachability and validity still hold across all generations.
- No regression in crossover/mutation validity tests.

### WP3: Indexed Memory for TPG Programs

Add a small explicit memory bank to TPG execution state.

Minimal design:

- Fixed-size vector memory (e.g. 8-32 slots).
- Programs may read memory as extra features.
- Optional write step after winner selection.

Deliverables:

- Extended diagnostic/program evaluation path with memory support.
- Config switch to enable/disable memory for ablation.
- Trace output includes memory read/write summaries.

Acceptance:

- Memory-off mode reproduces baseline behavior.
- Memory-on mode remains deterministic with seed.
- No violation of existing TPG validation invariants.

### WP4: Co-Evolve Traversal Policy Families

Support mixed populations where individuals may carry different routing policies
(`:team-mark`, `:arc-mark`, etc.) and compete under identical evaluation budget.

Deliverables:

- Individual-level routing policy encoded in TPG config.
- Evolution reports policy prevalence over generations.
- Comparative report of verifier vector + complexity + runtime.

Acceptance:

- Policy families can coexist in one run without tooling breakage.
- Final report identifies whether one policy dominates globally or by regime.

### WP5: Evaluation Protocol for Meaningfulness

Define evaluation beyond raw verifier score:

- Synthetic control tasks with known expected routing behavior.
- Human-in-the-loop review of route interpretability.
- Computational ground-truth tasks where tests provide objective correctness.

Deliverables:

- One reproducible benchmark script for each evaluation class.
- Attribution table: extraction quality vs control quality vs constraints.

Acceptance:

- Mission output includes explicit failure attribution, not only aggregate score.
- Results are reproducible and versioned in reports/evidence.

## Success Criteria

- [ ] WP1 shipped with tests and no baseline regressions.
- [ ] WP2 shipped with tests and configurable default compatibility.
- [ ] WP3 shipped with memory on/off ablation and deterministic traces.
- [ ] WP4 shipped with mixed-policy evolution experiment report.
- [ ] WP5 shipped with synthetic + HITL + computational evaluation bundle.
- [ ] At least one configuration outperforms current baseline on overall
      verifier satisfaction while preserving structural validity guarantees.
- [ ] Routing interpretability evidence demonstrates clearer specialization
      than current seeds.

## Scope Out

- Rewriting exotype physics or kernel semantics.
- Replacing verifier-guided fitness with scalar RL reward.
- New infrastructure that bypasses mission and invariant checks.
- Premature large-scale distributed training work; prioritize semantic quality
  first.

## Connects To

- `docs/technote-verifier-guided-tpg.md`
- `src/futon5/tpg/core.clj`
- `src/futon5/tpg/evolve.clj`
- `src/futon5/tpg/runner.clj`
- `reports/tpg-coupling-evolution.md`
- `reports/what-makes-a-good-run.md`

## Proposed Sequence

1. Implement WP1 + WP2 together (highest leverage, lowest disruption).
2. Add WP3 behind a strict feature flag and run ablations.
3. Run WP4 mixed-policy experiments.
4. Execute WP5 evaluation and publish evidence bundle.

