# Mission 0 Technote 2a: Experimental Branches on Top of the Plan

This note integrates external feedback into the Mission 0 plan by identifying
where new approaches can branch off from the current consolidation-focused
workflow. It is intended as an addendum to Mission-0-technote2.md.

## Summary of the current plan

The existing plan is a consolidation loop:
- calibrate scorers against human judgments
- pick sweep-guided priors
- run batches with PSR/PUR logging
- cross-check with Cyber-MMCA

This is a sound validation pathway, but it primarily refines the existing stack.
The additions below mark explicit pivot points for exploring new paradigms.

## Pivot A: Xenotype synthesis from scorer disagreement

**Motivation:** the current xenotype is hand-crafted (weighted ensemble).
Instead of seeking consensus, treat disagreement structure as signal.

**Idea:** build "disagreement signatures" per seed/run and look for clusters
that concentrate EoC judgments.

**Concrete steps:**
- For each seed in the Mission 17a compare set, compute:
  - variance across scorers
  - rank inversions / disagreement count
  - scorer spread (min/max/quantiles)
- Cluster seeds by disagreement signature.
- Evaluate whether EoC labels concentrate in specific clusters.

**Outcome:** if a cluster strongly correlates with EoC labels, that signature
can become a new xenotype candidate (a functor from disagreement-space to
selection-space).

## Pivot B: Adaptive parameter navigation (sweep as landscape)

**Motivation:** sweep priors are passive; they choose a point, not a trajectory.

**Idea:** treat the sweep output as a navigable surface and move along ridges.

**Concrete steps:**
- Identify the top N parameter pairs by mean-rank.
- Define a local neighborhood around each pair.
- Run short walks that move along high-score gradients rather than staying fixed.
- Compare diversity and EoC hit rate between fixed vs walking.

**Outcome:** if walking yields better results, parameter variation should be
treated as an exotype-level action rather than a static config.

## Pivot C: Online xenotype updating (PSR/PUR as feedback)

**Motivation:** PSR/PUR is currently post-hoc; xenotype remains static.

**Idea:** update the xenotype within a batch run based on recent outcomes.

**Concrete steps:**
- Define a windowed update interval (e.g., every 100 generations).
- Recompute ensemble weights or selection thresholds from recent results.
- Log each xenotype update as part of PSR/PUR.

**Outcome:** a moving xenotype, modeled as a CT presheaf refined by local
observations. Compare "adaptive" vs "static" to see if EoC is reached faster.

## Pivot D: Bidirectional Cyber-MMCA steering

**Motivation:** Cyber-MMCA currently validates; it does not steer.

**Idea:** use macro anomalies from Cyber-MMCA to bias MMCA search.

**Concrete steps:**
- Run Cyber-MMCA sweeps to identify regions with unusual macro persistence.
- Feed those regions back into MMCA as parameter bias.
- Compare hit rate against the baseline sweep prior.

**Outcome:** a bidirectional loop where macro-level CT signals guide micro-level
search, testing the adjoint relationship between scales.

## Experimental branch proposal

Add a lightweight "Phase X" alongside the main plan:

1) Run Pivot A on the Mission 17a compare set.
2) If disagreement clusters correlate with EoC labels, treat that as a new
   xenotype candidate and test it in a 50-run batch.
3) If not, proceed with Pivot B (parameter walking) using top sweep points.

This keeps the main plan intact while giving a defined path to a new approach
if the current scoring ensemble reaches a plateau.
