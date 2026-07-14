# Particle detection ECA slice: scope stop

## Verdict

A tractable periodic-tile or single-translation filter in the current repository
does **not identify Rule 110's ether robustly enough to instantiate the requested
particle detector**. The authorized escape hatch fires. No ECA class score,
confidence interval, or `SeparatesEoC` claim is made.

Landing the tested approximation would mislabel a recurrence as a domain and
would fail the request's non-negotiable Rule 110 bar. The next implementation
must infer a finite-state domain language (local causal states / epsilon-machine
or an equivalent de Bruijn transducer), then extract defects relative to that
language.

## Evidence from the existing finder

`futon5.mmca.domain-analysis/find-best-tile` searches rectangular tiles with
spatial and temporal periods up to 16. On the existing width-256, 512-step,
seed-42 anchor, exact held-region coverage is:

| Rule | Best rectangular-tile coverage | Inferred tile |
|---:|---:|---:|
| 0 | 1.000 | 1 x 1 |
| 8 | 1.000 | 1 x 1 |
| 128 | 1.000 | 1 x 1 |
| 30 | 0.804 | 2 x 1 |
| 110 | 0.560 | 1 x 1 |
| 54 | 0.583 | 16 x 16 |

This is not the desired decomposition: it explains chaotic Rule 30 better than
Rule 110 and reduces Rule 110 to a majority-symbol tile rather than its ether.
The filter cannot therefore define meaningful Rule 110 defects or particles.

## Label-free translation prototype and stop condition

A bounded, rule-agnostic diagnostic searched `|dx| <= 16`, `1 <= dt <= 16` on
a post-burn-in training window, then filtered a disjoint evaluation window.
Shift violations were connected with 8-neighbor spacetime connectivity;
persistent defect components were treated as provisional particles. The fixed
conjunction was domain coverage times one minus propagating-particle density,
with zero score when no particle existed.

Seed-42 diagnostics were:

| Rule | Learned shift `(dx,dt)` | Domain coverage | Provisional score |
|---:|---:|---:|---:|
| 0 | (16,16) | 1.000 | 0.000 |
| 8 | (16,16) | 1.000 | 0.000 |
| 128 | (16,16) | 1.000 | 0.000 |
| 30 | (2,2) | 0.756 | 0.572 |
| 45 | (6,6) | 0.764 | 0.584 |
| 90 | (15,1) | 0.503 | 0.253 |
| **110** | **(-4,8)** | **0.733** | **0.538** |
| 54 | (2,2) | 0.852 | 0.726 |
| 137 | (2,3) | 0.770 | 0.593 |

Rule 110 remains below chaotic Rules 30 and 45. A translation symmetry alone is
not a domain language: it cannot represent multiple ether phases and permitted
phase transitions, and its connected violations are not trustworthy particle
objects. Adjusting windows, shift penalties, or sparsity exponents after seeing
this ordering would tune to the labels, so the prototype was not committed as
an implementation.

These numbers are a **single-seed scope diagnostic**, not a statistical replay.
No confidence intervals were run after the structural stop condition fired.

## Required domain-inference machinery

The missing implementation needs all of the following:

1. **Past/future lightcones.** Extract finite-depth local past and future
   lightcones at every interior spacetime point, respecting the ECA causal cone.
2. **Causal-state reconstruction.** Group past lightcones by equivalent
   conditional future distributions (CSSR/local causal states), with an
   explicit finite-sample merge test and held-out validation.
3. **Domain phase automaton.** Infer recurrent causal-state components and their
   allowed spatial/temporal transitions. For Rule 110 this must recover the
   multi-phase period-14-ish ether language rather than hardcoding a Rule 110
   template.
4. **Epsilon-machine filter.** Mark a cell as domain only when its reconstructed
   causal-state transition is licensed by a recurrent domain component. Domain
   coverage is then the licensed fraction, not raw symbol agreement.
5. **Particle extraction.** Connect non-domain causal states into spacetime
   components, track their displacement and lifetime, and distinguish finite
   particles/domain walls from a percolating chaotic defect field.
6. **Structural conjunction.** Score high domain coverage together with
   nonzero, sparse, persistent particles; keep the no-particle frozen result at
   zero without a label-chosen density exponent.
7. **Boundary audit.** The current fixed-zero width-256 grids are evolved for
   512 steps, allowing boundary cones to dominate late-time behavior. The
   particle anchor must either predeclare a causally uncontaminated interior
   window or explicitly revise the reference protocol to periodic boundaries
   before class labels are inspected.

Only after this machinery recognizes Rule 110 ether and produces inspectable
particle objects should the 20-seed per-rule table and `SeparatesEoC` confidence
intervals be run.
