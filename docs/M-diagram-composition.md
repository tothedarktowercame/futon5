# Diagram Composition (ct/mission)

This repo models missions as typed wiring diagrams (EDN) and validates them with
`src/futon5/ct/mission.clj`.

Two composition modes exist:

## Serial Composition (`compose-missions`)

`compose-missions` composes `mission-a -> mission-b` by finding type-compatible
port matches from `a` outputs to `b` inputs and adding edges to connect them.

This is the standard "category composition" mode.

## Parallel Composition (`compose-parallel`)

Some diagrams are intended to share the same slow constraint boundary while
interacting at faster timescales (e.g., a social layer feeding a coordination
gate pipeline, both reading the same pattern library and agent registry).

`compose-parallel` composes two diagrams by:

- Merging shared *constraint input* ports that have the same `:id` and `:type`
  and are `:constraint true` in both diagrams.
- Prefixing all other ids with the mission id (to avoid collisions).
- Adding cross-diagram edges for type-compatible output->input pairs (A to B).
- Treating connected ports as internal wires:
  - outputs in A that feed cross-diagram edges are removed from the composed
    boundary outputs
  - inputs in B that are satisfied by cross-diagram edges are removed from the
    composed boundary inputs

This lets the composed diagram validate as a single system while preserving the
"shared constraints are shared" invariant.

### Shared Constraint Contract

If a port is shared as a constraint input, the core attributes must match
between diagrams:

- `:id`
- `:type`
- `:constraint` (must be true)
- `:timescale`

If they differ, `compose-parallel` throws: this mismatch indicates drift in the
shared boundary contract.
