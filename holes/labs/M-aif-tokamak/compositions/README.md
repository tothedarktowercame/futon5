# Compositions of propagators — the run

**2026-07-17, claude-2.** Script: `scripts/propagator_compositions.clj`.
6 arms x 3 seeds x 120 steps x width 80. Reuses `gen/rule-permute` by var, not a copy.

Joe: *"we haven't yet tried compositions of the propagators... let's see if we can
make some examples to see whether we get any interesting pictures."*

## What was new here

`exotype_by_example.clj` ran `switch(cond, propagator, NO-OP)` — its `choose-n`
returns 1 or 0, i.e. how many times to fire ONE sigma. This script switches **which**
sigma: `switch(cond, sigmaA, sigmaB)`, both live. That composition had never been run.

## The prediction, registered BEFORE the run

`image(switch) = image(A) ∪ image(B)`, so `FREE(switch) = FREE(A) ∩ FREE(B)`.
The clamped-shift family `s_j(k) = max(k-j, 0)` has `|FREE(s_j)| = j` and **nested**
free sets, so it composes without losing its scaffold. Permutations have `FREE = {}`
and annihilate any scaffold they meet. `s_1` **is** the 2014 Emacs bug.

## Results

| arm | predicted FREE | diversity (3 seeds) | mean mut-rate | phenotype |
|---|---|---|---|---|
| `s1-alone` | {7} | 4 / 4 / 4 | 0.223 | collapsed |
| `rot2-alone` | {} | 4 / 4 / 4 | 0.053 | collapsed |
| `baldwin-rot2` (as built) | {} | 43 / 42 / 54 | 0.028 | structured |
| **`baldwin-s1`** | **{7}** | **63 / 57 / 63** | 0.038 | **structured, nested** |
| `s1-x-s2` (two live) | {7} | 39 / 37 / 45 | 0.392 | **noise** |
| `s1-x-rot2` (two live) | {} | 36 / 49 / 47 | 0.316 | **noise** |

## Three findings

**1. The switch is load-bearing, not the shape map.** Either propagator ALONE
collapses to diversity 4 — dead. The same propagator inside `switch(bored, ·, no-op)`
sustains 43–63. The Baldwin reconstruction reproduces here independently, in a script
written from scratch, on a different propagator than the original used.

**2. `switch(bored, s_1, no-op)` is the best arm** — diversity 63, structured nested
phenotype, low rate. This is the substitution `paper/main.tex` §Baldwin flags as
untested: Baldwin-as-built uses `rot2`, a PERMUTATION with `FREE = {}`, so the
measured self-annealing happened with **no scaffold at all**. Swap in the 2014 bug and
you get an endogenous rate AND a scaffold in one composition. Nothing had both before.

**3. THE PREDICTION FAILED, and the failure is the finding.** `s1-x-s2` has the SAME
predicted scaffold `{7}` as `baldwin-s1`, and its phenotype is noise at ten times the
mutation rate. **The scaffold survived and bought nothing.** So: the no-op is not a
degenerate branch to be upgraded — it IS the mechanism. Holding is what lets structure
persist. With two live branches something always fires, and constant motion is noise
regardless of which position is nominally free. Set arithmetic predicts which positions
CAN be written; it does not predict what the composition DOES.

## Limits — not banked

- **n = 3 seeds.** No control for the `boring?` threshold.
- **"structured" is my eye on a PNG, not a measure.** `s1-x-s2`'s speckle vs
  `baldwin-s1`'s nesting is stark, but the causal-state count or a terminal-rule census
  is what would license the word. Diversity and mut-rate are measured; "structured" is not.
- Every arm here is `switch(boring?, ·, ·)` with the same discriminator. A different
  condition may reorder everything.
