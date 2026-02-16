# 01 Routemap — Prototype 1 + 1b

Goal: complete Prototype 1 (Patterns of Improvisation) and Prototype 1b
(Sigil–Operator Lexicon) as working, documented, and callable layers.

Status note: FUTON5 already has running MMCA code + early sigil/operator
plumbing, but the operator set is still sparse and the narrative/spec
is not yet canonical.

Settled-state invariants (what should not drift during housekeeping windows):
see `docs/technote-settled-state.md`.

## Route (short)

1) Lock the operator spec
   - Define the minimal operator record once (fields, semantics, invariants).
   - Decide how "cost/relief" is expressed (see `docs/tension_calculus.md`).
   - Result: a stable schema used by all operators.

2) Select the first 8–12 operators
   - Use the devmap list (Phase-Switch, Return-to-I, Noise Budget, etc.).
   - For each operator, provide:
     - short intent
     - domain/codomain
     - invariants
     - cost/relief profile
     - CT sketch (optional but preferred)
     - MMCA hook mapping (init/observe/decide/act)

3) Implement real MMCA hooks
   - Replace stubs with concrete behavior.
   - Ensure each operator emits metrics and respects invariants.
   - Add at least one example per operator in `docs/meta_patterns.md`.

4) Bind sigils to operators (Prototype 1b)
   - Declare the mapping in `resources/futon5/sigil_patterns.edn`.
   - Ensure dispatch is callable by sigil in runtime (MMCA + CT diagram paths).
   - Provide a CLI listing of available operators by sigil.

5) Minimal operator tests/examples
   - Create 1-2 example runs per operator.
   - Save as EDN + image renders (use MMCA renderer).
   - Ensure at least one cross-domain example (CT sketch -> MMCA score).

6) Documentation checkpoint
   - Update `docs/meta_patterns.md` with "status: implemented" entries.
   - Add a short "how to add a new operator" section.
   - Record the current operator list + sigils in the README.

## Exit criteria

- 8–12 operators are executable and documented.
- Sigil -> operator mapping is stable and callable.
- Each operator has a real MMCA hook + example run.
- The tension calculus is referenced as a requirement for operators.
