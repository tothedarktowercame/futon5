# f3-sat-encode mentor review

## Scope
Reviewed `scripts/fm001b/ramsey_book_sat.py` (commit 03c957c on `codex/f3-sat-encode`) to confirm the book-graph constraints match the Ramsey definition for `B_{n-1}` (n=8 ⇒ B₇) and the complement `B_n` condition.

## Findings
- **Constraint construction**: The `y` auxiliaries correctly reify `x_uv ∧ x_uw ∧ x_vw` (lines 79-113) and the `CardEnc.atmost` guard enforces `≤ n-2` shared neighbors when the base edge is present. The `z` auxiliaries symmetrically encode `¬x_uv ∧ ¬x_uw ∧ ¬x_vw` so the complement constraint is only active when the base edge is absent.
- **Edge cases**: `book_n` is constrained to ≥3, so the `n-2` and `n-1` bounds are non-negative, and vertices are iterated with `w ≠ u,v`, preventing degenerate pages.
- **Encoding vs semantics check**: For `vertex_count ∈ {5,6}` and `book_n=4`, enumerated every graph assignment (2¹⁰ and 2¹⁵ cases respectively). For each graph we:
  1. Evaluated `verify_book_constraints`.
  2. Solved the CNF with PySAT under assumptions fixing all edge variables.
  The SAT result matched the semantic check for all 32 768 assignments — no mismatches.
- **No issues found**: encoding matches the Ramsey definitions, and auxiliary clauses prevent spurious satisfying assignments.

## Test command
```
.venv/bin/python - <<'PY'
from pysat.solvers import Solver
from scripts.fm001b.ramsey_book_sat import BookRamseyEncoder, verify_book_constraints
# ... loop over all graphs for V in {5,6}, n=4 (see conductor log 2026-03-10, ~4.4s)
PY
```

## Status
No correctness bugs identified. Recommend merging `codex/f3-sat-encode` as-is.
