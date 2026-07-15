# H-diagonal-transport — build the discriminator the eye keeps beating

**To:** codex (L3) · **From:** claude-3 · **Mission:** `holes/missions/M-propagators.md`
**Read the mission doc first**, especially §2 (the fail-bank). Every measure tried so
far has lost to a human eyeball. Your job is to build one that doesn't, and to prove
it on ground truth **before** pointing it at anything we care about.

## The question

Is the surviving propagator regime edge-of-chaos, or just durable noise? Right now
**nobody knows**, because the only instrument that has tracked Joe's judgement is Joe.

## Why the obvious answers are already dead — read this or you will rebuild them

- **Cμ (local-causal-state count).** Cleanly separates class-4 from class-3/1 on the
  ECA anchor, clearing the Rule-110 bar that every informational measure in
  EVALUATOR-SPEC's fail-bank misses. Then, on MetaCA: a **frozen barcode**
  (`l0-baseline`, whose genotype never changes) scores **39–44**, above **Rule 110's
  26.4**. It measures the temporal stability of the local rule, not complexity. It
  also grows with sample size (same field: 38 over 160 gens, ~17 over 40-gen windows),
  so cross-length comparison is invalid, and coarse-graining drives the verdict (k=2
  vs k=4 flips "not EoC" to "EoC"). **Banked.**
- **Aliveness (survival/activity).** Ranked Joe's blind visual pick 2/15 *within one
  family* — then, across families, top-ranks stripes-plus-snow. A controller
  maximising it would freeze most columns and flicker forever. Good tiebreaker, not a
  class detector. **Banked as a headline.**
- **Run-level means.** The Figure-8 transient is ~8–20 generations. A 100-gen mean
  reports all 15 living seeds as dead. **Any measure you build must be windowed.**

## The candidate

**Diagonal transport.** What visibly separates the reference row from everything else
is that Rule 110 *propagates* — gliders travel across the lattice, carrying
information sideways through time. Rule 0 doesn't. A frozen barcode doesn't (vertical
stripes = zero transport), which is exactly the case that broke Cμ. The gcd-2 rows
appear to; Figure 8's dying phase appears to; that's the thing to test.

r01 already banked "diagonal autocorrelation" as an axis — but it was developed on
L5-creative, which we now know is **bitwise Rule 90** (its gate takes the creative
branch 1600/1600 times, making it a bare `bit-xor`). So the axis may be sound and its
provenance contaminated. Check it, don't assume it.

## Acceptance bar — the gates it must clear

1. **The Rule-110 bar.** Ranks class-4 (110, 54) above class-3 (30, 90) above class-1
   (0, 250), on every seed. Same protocol, same width/length for all — the count-style
   measures inflate with sample size, so equal dimensions or a normalised statistic.
2. **The barcode gate — this is the one that matters.** Must rank a **frozen
   heterogeneous rule field BELOW Rule 110**. That is precisely where Cμ failed
   (39–44 vs 26.4). Build the barcode null: `l0-baseline` genotype (89/89 rows frozen,
   0.0 cells changing per step) driving a phenotype. If your measure likes the
   barcode, bank it and say so.
3. **Windowed.** Report a profile over time, not one number for a run. It must be able
   to say "EoC for the first 20 generations, then dead" — that is Figure 8, and it is
   the phenomenon.
4. **Then, and only then**, apply it to: rotate ±2 (lives, ~31 rules), rotate −1
   (Figure 8, dies t≈50, and Joe says its *surviving phase* IS EoC), and
   `(0 1 2)(3 4 5 6 7)` (lives, 26.8 rules). If it agrees with the sheets in
   `holes/labs/M-aif-tokamak/propagator_survey_*.png`, we have an instrument.

## Apparatus

    emacs --batch -l scripts/elisp-harness/run.el --eval '(...)'

`(run-propagator PERM SEED WIDTH STEPS)` → `(:death t :rules n :activity n :phe rows)`.
`:phe` is the binary phenotype field — **that is what to measure**: it's the same
alphabet as an ECA, so it's directly comparable to the anchor. The 256-valued genotype
is NOT comparable (that confound sank the Cμ attempt).

Existing (validated on ECA, fails on MetaCA — read before reusing):
`src/futon5/mmca/particle_detection.clj`, `src/futon5/mmca/local_causal_states.clj`,
`scripts/cmu_anchor.clj`, `scripts/eca_anchor.clj`.

## Honest note

A negative — "diagonal transport also fails the barcode gate" — is a real result and
goes in the fail-bank with the others. Do not tune a measure until it agrees with the
eye; that is fitting, and this mission has already been burned by a story that fit 12
cases and died on the 13th.

## Gates

- `clj-kondo` on Clojure; `futon4/dev/check-parens.el` on Lisp/Clojure; relevant tests.
- Don't edit `vendor/metaca/**` — it is evidence.

**Bell `claude-3` back with a summary + commit shas.**
