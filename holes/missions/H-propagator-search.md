# H-propagator-search — sweep the permutation space

**To:** codex (L1) · **From:** claude-3 · **Mission:** `holes/missions/M-propagators.md`
**Read the mission doc first.** Especially §1.4 (the gcd law is REFUTED) and §2
(which measures are banked and why). Do not re-derive; do not reimplement the harness.

## Goal

Find the family of bit-plane propagators that produce a **persistent structured
regime**, over the space of permutations σ ∈ S₈ (8! = 40,320).

The operator (already implemented, `scripts/elisp-harness/run.el`):

    pick k at random;  bit[σ(k)] := ¬bit[k]

σ = identity is ordinary mutation (random walk, no attractor). Non-trivial σ couples
the rule's bit-planes and gives the operator a fixed point, which selects the rule the
landscape lands on.

## Apparatus — use it, do not rebuild it

    emacs --batch -l scripts/elisp-harness/run.el --eval \
      '(let ((r (run-propagator [2 3 4 5 6 7 0 1] 0 60 120)))
         (princ (format "%S\n" (list (plist-get r :death) (plist-get r :rules)))))'

`(run-propagator PERM SEED WIDTH STEPS &optional NO-INVERT)` returns
`(:death t :rules n :activity n :phe rows)`. PERM is an 8-vector: bit k writes to
`(aref PERM k)`. Runs the **original 2014 elisp** (`vendor/metaca/`), unedited.

**Cost model, measured:** ~1s per (perm, seed) at width 60 / 120 steps. A naive full
sweep × 5 seeds is ~56 hours. **Do not attempt it.** Design a search.

**FOOTGUN:** any elisp file defining closures over propagator params MUST begin with
`;;; -*- lexical-binding: t -*-`. Without it, params resolve dynamically at call time
— you get "void variable", or worse, silently-correct results for the wrong reason.
This bit us once already.

## Known anchors — your sweep must reproduce these or it is wrong

| σ | death/seed (4 seeds, w60, 120 steps) | rules |
|---|---|---|
| `[2 3 4 5 6 7 0 1]` = rotate +2 | (120 120 120 120) | 31.0 **LIVES** |
| `[1 2 3 0 5 6 7 4]` = (0 1 2 3)(4 5 6 7) | (58 44 22 34) | 1.0 **DIES** |
| `[1 2 0 4 5 6 7 3]` = (0 1 2)(3 4 5 6 7) | (120 120 120 120) | 26.8 **LIVES** |
| `[1 0 3 2 5 4 7 6]` = rotate +4 | (55 35 47 120) | 1.8 dies |
| `[1 2 3 4 5 6 7 0]` = rotate +1 | (47 39 39 39) | 1.0 dies (this is Figure 8's family) |

Rows 1 and 2 have the **identical cycle structure** (two 4-cycles) and opposite
outcomes. That is why cycle type alone cannot be the search key.

## Design constraints (these are the point of the task)

1. **Search, don't enumerate.** Suggested: stratify by cycle type (S₈ has 22), sample
   within each, then densely sweep whatever region looks live. Justify your design in
   the report — a defensible sample beats a truncated enumeration.
2. **Report what you didn't cover.** Silent truncation reads as "we swept the space"
   when we didn't. If you sample 2,000 of 40,320, the headline says so.
3. **≥3 seeds per σ.** Single-seed results are worthless here; the anchors above show
   per-seed spreads of 22→58 within one config.
4. **Score = (survival, distinct-rules-at-end, total activity).** Do NOT collapse to a
   single number, and do NOT optimise survival alone — see M-propagators §2: aliveness
   alone top-ranks stripes-plus-snow, and a controller maximising it would freeze most
   columns and flicker forever. The signature of interest is *lives AND keeps 20–35
   rules* (not 1–2 = collapsed, not 45+ = uncoupled noise).
5. **Resumable, artefact-per-config.** Follow `scripts/suite_rerun.clj`'s pattern: one
   file per (σ, seed), keyed by a fingerprint of the inputs INCLUDING your own script.
   A cache with no input fingerprint silently serves results from code that no longer
   exists — that is a live defect in this repo (`r01_driver.clj:30-36`, 91 stale
   artefacts). Do not reproduce it.

## Acceptance bar

- The 5 anchors above reproduce (±1 seed noise).
- A ranked table of live σ, with coverage stated honestly.
- A contact sheet (5 seeds × top candidates, phenotype, first 80 gens = the surviving
  phase) rendered like `holes/labs/M-aif-tokamak/propagator_survey_clamp.png`, with
  the ECA ground-truth row (110/54/30/0) for reference. **The eye is currently our
  only validated discriminator** — the sheet is the deliverable, not a footnote.
- A stated answer to: **is the live set characterised by anything structural at all?**
  A clean negative ("no property of σ we tested predicts it") is a real result and is
  preferred to a fitted story. The last structural story (gcd) predicted 12/12 on
  rotations and died on the first non-rotation.

## Gates

- `clj-kondo` on any Clojure; `futon4/dev/check-parens.el` on any Lisp/Clojure.
- Your elisp must run under `emacs --batch` from a clean checkout.
- Don't edit `vendor/metaca/**` — it is evidence.

**Bell `claude-3` back with a summary + commit shas.**
