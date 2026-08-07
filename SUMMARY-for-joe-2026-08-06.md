# Summary for Joe — 2026-08-06, autonomous stretch

Written while you were out, under your authorisation to chain PLAN steps 1-2-{3,4}.

## The headline

**The bridge test passed.** It is the first one in this project to do so, and the contrast is the
most important number of the day:

| | held-out R² |
|---|---:|
| **E0** — internal score → **damage reach** (external, twin-run) | **+0.016** |
| **§115** — (halting, change) → **local compressibility** (intrinsic) | **+0.541** |

Same system, same kind of observable, same form of test. The only difference is whether the target
is something the system can perceive.

> **The three controllers did not fail because the system cannot be steered. They failed because
> they were steering by an instrument not connected to anything the system does.**

That is a better result than a working controller would have been, and it is now measured rather
than argued.

## What ran

1. **Objective surface** — codex-4's `scripts/local_compressibility_grid.clj`, 35 cells
   (7 γ × 5 κ), 4 seeds. `GRID_EXIT=0`, 140 data rows, on zone at
   `/tmp/objective-surface.csv` (copy in `figs/`). ECA validation passed *before* launch on the
   separation bar: 54 = 98.44% ± 1.56, 110 = 67.19% ± 7.81, 204/90/30 all 0.00% ± 0.00.
2. **Bridge test** — `scripts/bridge_test_objective.py`, leave-one-out.
   Held-out R² **+0.541**, MAE 0.146 vs mean-baseline 0.227; in-sample 0.616, so overfit is mild.
   Fit: `mid_range = 0.512 + 0.716·halting − 1.457·change`.
   I smoke-tested the estimator on synthetic signal (+0.976, passes) and synthetic noise
   (−0.165, fails) **before** running it on real data, so the result is attributable to the
   system rather than to my arithmetic.
3. **Step 3 dispatched** — codex-3 is building the episodic surrogate + policy apparatus.
   Job `invoke-1786043616695-228-1df099ee`, park `park-f60e95b1`. Build + offline validation only;
   **it is instructed not to run a live closed loop**, so nothing runs unreviewed.

## Two things you should know before trusting the 0.541

- **Window mismatch.** Observables came from the earlier surface run (t=100–300); the objective
  sheet is t=0–250. Same seeds and parameters, so the same trajectories, but different windows.
  The mismatch adds noise and therefore biases *toward* failure — so **+0.541 is a lower bound**
  and the pass is not an artifact. But the number should not be quoted as precise until both are
  measured on the matched window. codex-3's packet requires that fix.
- **The γ/κ ridge.** 5 of 595 cell pairs collide within 0.02 in (halting, change) space —
  (γ=2, κ=0.5) sits on top of (γ=8, κ=0.1). Any policy reading only these two observables is
  blind there. Documented, not fixed.

## Also corrected while you were out

§III.9 of the Part III draft carried numbers from a **buggy packing** in my reference
implementation — each 100-bit row padded to 13 bytes, appending four zero bits per row, a regular
period-13 artifact zlib exploits. codex-4 caught it. Corrected figures are in the draft; the
material change is that **γ=1 and γ=4 tie at 81%** rather than γ=4 winning outright, so no
particular γ is singled out any more.

I also replaced the script's acceptance bar: it had my contaminated point targets (110 → 0.88)
hardcoded. It now tests **separation** — class-IV rules above 0.50, everything else below 0.05 —
which cannot be invalidated by a future packing correction.

## Step 3 landed and I reviewed it — the apparatus is real, its evidence was not

codex-3 built `scripts/intrinsic_objective_controller.clj`. Every gate I set passes, and it
*tested* the refusal path rather than just implementing it (exit 2 at held-out R² = −0.1895).
Held-out 0.5414 / in-sample 0.6160 reproduce my figures exactly. No live loop, nothing committed.

**But the acceptance test was wrong twice, in ways that cancelled into a plausible number.**
Reported: 3.3143 episodes to a top-quartile cell vs 8.8622 for "random walk", a 63% margin.

1. The baseline was a local random *walk* with revisits, which cannot lose. The fair baseline is
   random *sampling* — an episodic controller may jump anywhere between episodes. Sampling gets
   there in **3.27**, so the policy is 1% worse, not 63% better.
2. The criterion had no headroom: 10 of 35 cells qualify, so random search wins in 3.3 draws.

**Corrected — and the policy survives:**

| criterion | policy | random sampling | |
|---|---:|---:|---|
| top quartile (10/35) | 3.31 | 3.27 | no advantage |
| **top-3 cells** | **4.69** | **9.01** | **~2× faster** |
| single best cell | 16.80 | 18.05 | no advantage |

Honest claim: **the surrogate-driven policy reaches the top few cells about twice as fast as
random search, and cannot pinpoint the single optimum.** Best cell is γ=1, κ=0.10 (mid_range
0.641). I fixed the script myself — added `random-sampling-time`, kept the walk labelled as unfair,
and it now reports both criteria. clj-kondo and check-parens clean; verified against an
independent Python replay (4.66/9.01 vs the Clojure's 4.6857/9.0051).

## Open, for you

- Review codex-3's apparatus when it bells back (I will review first and report).
- Decide whether the matched-window remeasurement is worth its ~40 minutes before the number goes
  in the paper. I think yes.
- §III.9 is, in my view, the most defensible thing produced today and the strongest candidate for
  Part III: an intrinsic complexity measure that passes ECA validation where damage reach fails,
  and that puts rule 90 with the chaotic rules where damage reach puts it next to the frozen one.
