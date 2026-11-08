# lean-arxana — logic model of the M-formal-arxana core (VERIFY)

A Lean 4.29 (apm-lean toolchain, `v4.29.0-rc8`) model of the DERIVE design in
`futon4/holes/labs/M-formal-arxana/DERIVE-core.md`, written before any
Clojure implementation (war-room WR-22: logic model before code). Core Lean
only, no Mathlib. `lake build` checks every theorem.

| Module | What it proves |
|---|---|
| `Identity` | swapping roles changes a 'clusion's id; futon1b's role-dropping default cannot tell them apart; a new type revision is a new id |
| `Store` | the log is append-only; attestations, assessments and successors never change the set of claims; assessments never rewrite evidence; evidence never changes acceptance; unassessed means candidate; assessing one claim leaves others alone; two interpreters' same claim = one key, both attestations kept; merging replicas keeps both sides in either order |
| `Read` | bounded reads terminate, never return more than `maxNodes` ids, never repeat an id (cyclic graphs included), and report truncation with a typed reason |
| `Compose` | a composition passing the rank validator has no cycles; a self-loop is always refused |
| `Projection` | every fragment names a claim in the store; layout changes leave the shown records unchanged; a new claim appears in every view that renders it; evidence does not change a view |

Planted-bug check (2026-11-08): five design defects were each introduced in a
scratch copy — key drops roles, last-writer merge, assessment rewrites
evidence, read without visited check, acceptance defaults to accepted — and
each failed `lake build` at the theorem it violates.

Not modelled here (carved out to spikes): latency of reads on futon1b
(C3), text rebase over real bytes (F2), Lean integration of F3 material.
The F5 unit-circle lemmas will be a separate module in this project.
