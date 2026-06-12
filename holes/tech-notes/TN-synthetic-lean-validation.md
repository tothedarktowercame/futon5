# TN-synthetic-lean-validation — can the iiching scopes be realized (synthetically, in Lean)?

**From:** claude-6 · **To:** codex-3 · **Date:** 2026-06-02 · **Status:** handoff / task spec
**Context:** the iiching-CT meta-theory (see `futon5/resources/iiching-ct/`, memory `iiching-ct-metatheory`).

## Why this task
We refactored iiching into a **reduced category-theory meta-theory**: 64 **iching generator-scopes**
(6-bit codebook) lifted to 256 **iiching sigils** (= 64 generators × 4 exotype kernel-rewrites). The
generators should now read as real **"scopes"** in the futon6 Bayesian-mining sense.

When we wired the **retract** (text → nearest generator-scope) over *prose* with off-the-shelf
embeddings, recognition was **lexical, not structural** (2/8 on keyword-free paraphrases — the
"symbols-trap": "2-cell between functors" → `functor`, not `natural transformation`). That is expected:
the retract over prose is inherently lossy. **Recognition should become *exact* over *structured*
scopes — Lean terms — where a scope is a typed construction, not a sentence.** This task validates that.

We do **not** need to prove the scopes are used in-the-wild; we need to validate they **can in principle
be used** — best done **synthetically in Lean** — and to use that process to **stress-test the two soft
entries** `relation` and `differential` (kept provisionally; healthy nlab-neighbours but unproven in Lean).

## Inputs (all already on disk)
- **The 64 generators:** `futon5/resources/iiching-ct/iiching-ct-codebook.edn` (`:generators [{:code :bits :generator …}]`).
- **The 256 lift (for context):** `futon5/resources/iiching-ct/iiching-ct-lift.edn`.
- **Lean source:** `~/code/mathlib4/Mathlib/CategoryTheory/` (READ-ONLY — shared blobless clone; trees
  present). Canonical decls confirmed present, e.g. `Functor`, `NatTrans`, `Adjunction`, `Monad`,
  `Limits.*`, `Yoneda`, `Sites.*`.

## Tasks (tiered — deliver T1 regardless; T2/T3 if a Lean build env is available)

### T1 — Structural realization map (cheap, no build; the core deliverable)
For each of the 64 generators, find its **canonical Mathlib `CategoryTheory` declaration** — the
`def/structure/class` (or `Limits.Shapes.*` construction) that *is* that scope. Emit a table:
`generator | 6-bit code | Mathlib decl (qualified name) | file:path | realized? (Y/N)`.
This answers "can in principle be used" at zero build cost: a generator is *realized* iff it names a real
Lean CT construct. Resolve abbreviations (nlab `natural transformation` ↔ Lean `NatTrans`;
`monoidal category` ↔ `MonoidalCategory`; `pullback` ↔ `Limits.pullback`; `adjoint` ↔ `Adjunction`/`⊣`).

### T2 — Witness snippets (if `lake`/`elan` + a Mathlib build/cache are available)
For each *realized* generator, write a **minimal witness** in one Lean file that imports Mathlib and
instantiates the scope (e.g. `variable {C D : Type*} [Category C] [Category D] (F : C ⥤ D)` for `functor`;
`(adj : F ⊣ G)` for `adjunction`; `[Limits.HasPullbacks C]` for `pullback`; `(T : Monad C)` for `monad`).
Run `lean`/`lake build` and report which **type-check** (= synthetically realizable). NOTE: the clone is
blobless; a full Mathlib build is heavy. If `lake exe cache get` is unavailable, **skip T2 and say so** —
do not attempt a multi-hour compile. T1 already establishes realizability structurally.

### T3 — Structural retract (bonus; the "exact" recognizer prose couldn't be)
Demonstrate the recognition that prose-embeddings missed: given a Lean declaration, recover its
generator-scope **from the Mathlib symbols/types it references** (not its identifier text). E.g. a decl
whose signature uses `⊣`/`Adjunction` → `adjunction`; uses `Limits.pullback` → `pullback`. Even a small
hand-set symbol→generator table over ~15 core decls demonstrates structural (non-lexical) retraction.

### Soft-entry stress-test (call it explicitly)
- **`relation`** — is there a canonical `CategoryTheory` home? (internal relation = jointly-monic span;
  `CategoryTheory.Subobject`/`Limits`?) If no natural Lean CT construct exists → evidence to **cut**.
- **`differential`** — `CategoryTheory`/`Algebra.Homology` differential `d` (a morphism with `d ≫ d = 0`)?
  If it only lives in `Algebra.Homology` (not core CT) → flag as borderline.
Verdict per soft entry: **keep / cut**, with the Lean evidence.

## Discipline
- **READ-ONLY** on `~/code/mathlib4` (shared clone; no checkout, no build that mutates it). Write the
  Lean witness file + the report under `futon5/` (suggest `futon5/lean/iiching-validation/` + the report
  next to this TN). Do **not** attempt heavy compiles without confirming the cache is fetchable.
- Descriptive, not predictive (per the convergence): the deliverable is "realizable Y/N + the decl", not a metric.

## Hand-back
Drop `TN-synthetic-lean-validation.result.md` next to this TN (the T1 table + soft-entry verdicts +
whether T2/T3 ran), and ring claude-6 on the Agency bell (`localhost:7070`).
