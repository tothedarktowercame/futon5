# vendored: holtzermann17/metaca (the 2014 MetaCA elisp)

Source: https://github.com/holtzermann17/metaca — 47 commits, 2014-12-15 → 2015-06-19.
Vendored 2026-07-15 because the Figure-8 reconstruction depends on running the
ORIGINAL code, and a clone in /tmp is not a reproducible dependency.

- `256ca-2014-12-29-BUGGY.el` — commit `2f62f59`. **This is the version that made
  Figure 8 of arXiv:1502.00130.** It contains `mutate-genotype-n` with the
  off-by-one: reads bit at 0-based `pos`, writes at `(goto-char pos)` where Emacs
  buffers are 1-BASED and `(goto-char 0)` clamps to point-min. So pos=0 and pos=1
  both write bit 0, and bit 7 is never written. That is the paper's
  "(erroneously-programmed) mutation rule that only flips the first bit".
- `256ca-2015-04-12.el` — commit `d5ad8b0`, HEAD. The bug is fixed here
  (`goto-char (1+ pos)`, renamed `mutate-rule-n`). Fixed in `4a1e37e` (2015-01-08),
  ten days after it shipped.
- `hexrgb.el` — the real dependency (colour only; no dynamics).
- `eoc.png`, `reef.png` — Figure 8 as published. `eoc.png`'s two dominant colours
  are #2a2a2a and #aaaaaa = rules `00101010`/`10101010`, which is how we know the
  paper's text (which says rules 0/128) misidentifies them.

## Running it

    emacs --batch -l scripts/elisp-harness/run.el

Requires `scripts/elisp-harness/clcompat.el` (Emacs 30 removed the old `cl`
package; the shim aliases map/mapcar*/first/second/member-if/string-to-int).
**The legacy source is unedited** — what executes is the real 2014 code.

Any file defining propagator closures MUST start with `;;; -*- lexical-binding: t -*-`.
Without it the lambda resolves its parameters dynamically at call time, which yields
either "void variable" or — worse — silently correct results for the wrong reason.
