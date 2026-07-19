# TN-draft3-review — codex-9 adversarial review of the Discussion

**Date:** 2026-07-19. **Reviewer:** codex-9 (session 019f758e), via two synchronous
whistles from claude-2. **Scope:** the Discussion section (newest, least-scrutinised)
after the entropy→boundary-mass reframe. **Status: HELD — Joe wants to discuss
before actioning. No edits made.**

Joe's charge: not to strip hostages to fortune nor to shore up the argument, just
to test whether the reframing stands. codex-9's verdict: **as written, it does not
survive the strongest scrutiny.** Two defensible results, not shown to be one
phenomenon; "edge" is doing two jobs.

## The decisive problem (verified in code)
The reframe unifies two things as one "edge of chaos":
- **Literal edge** — the domain-wall boundary (fig:interface filaments), measured by
  box-counting D and interface fraction.
- **Littoral region** — the parameter-space crossover (fig:phase), q ≈ 0.1–0.5.

But **the boundary is measured at the wrong q.** `fig:interface` runs
`run-propagator` at the **default `interrupter-q = 1`** (propagator-dominated end)
— `figures.clj:113` / `run-interface`, and `core.clj:198` default — while the
crossover band is q ≈ 0.1–0.5. So the geometric edge is measured **outside** the
littoral crossover. codex-9: the paper "finds a crossover in one experiment and a
fractal boundary in another, then joins them through a pun." Confirmed by reading
the generators.

The two would cohere only if **D(q,L)**, boundary density, or persistence were
measured **across the scan** and shown to arise within / track the crossover band.
That bridge is **absent**.

## The clean bar (no new computation)
> **De-unify.** Break the chain "littoral crossover → morphology of the crossover →
> the edge *is* the boundary" (draft3 ~line 732). Present them as **two separate
> findings**: *a broad parameter-space crossover, and, separately, fractal-like
> activity-domain boundaries.* If any unified EoC claim remains, **D(q,L) is
> required** (real new computation).

## Terminology + rigor fixes
1. **"boundary mass" is a misnomer.** Box-counting D is a scaling exponent, not
   mass/density. Use **interface fraction** for amount, **D** for dimension, reported
   separately, **with seed ranges**. codex-9's measurement (committed threshold/
   smoothing, `plot_eoc_interface.py:22`), L=256:
   - offset+2 fraction 0.129 ± 0.025
   - σ=16250374  fraction 0.119 ± 0.016
   - river       fraction 0.204 ± 0.017
   - At **every** tested L, both river seeds exceed all offset+2/σ seeds in D.
   - River mean D: 1.746 / 1.698 / 1.686 / 1.672 at L = 128 / 256 / 512 / 768.
2. **"distinctly more" for the river** is fine *descriptively* if seed ranges are
   shown — but it is an **unmatched** comparison, not a population-level or causal
   inference (the river changes the dynamics, not just one knob).
3. **"size-stable / holds rather than drifting" (draft3:766) is false and must be
   weakened.** Interface fraction *declines* with L (river 0.236→0.179; offset+2
   0.119→0.092; only σ ≈ stable), and river D drifts modestly **down** (1.75→1.67).

## Consequence for recent work
This course-corrects the item-5 rewrite of 2026-07-19 ("The boundary is the object;
its mass is the measure", commit d1e13d8) and the fig:interface caption (cef7f68) —
both assert exactly the unification codex-9 says must be broken.

## Decision for Joe (before actioning)
- **(A) De-unify** — codex-9's endorsed path, no new compute. Stop calling the
  boundary "the edge of chaos"; two separate findings; fraction (with seed ranges) +
  D reported separately; drop the size-stable claim. Honest, cheap, clears the bar.
- **(B) Bridge** — keep a unified claim by running **D(q,L)** across the scan to show
  the boundary tracks the crossover. Stronger if it works; real work, no guarantee,
  and the boundary currently sits at q=1 so it may not track.

claude-2 recommendation: **(A)**. Matches "make it stand, don't shore it up," and
codex-9 pre-computed the numbers.
