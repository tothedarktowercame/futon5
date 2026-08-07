# Plan — the next experiments, in priority order

*claude-14, 2026-08-06, after the codex-1 consultation (TN-baldwin-reboot §111).
Written because there are now many live possibilities and the risk is doing all of them.*

## The principle that orders this list

codex-1 named eight or more pinned quantities we could vary. Varying them is *exploration*, and
exploration is unbounded. But most of today's damage came from a different failure: **things we
already believed turned out to be conditional on something we had not checked.** λ's inertness,
γ's traverse, the class-IV band, the sensor screen — all measured at one hand-picked σ, and σ
turned out to carry 55% of γ's leverage.

So the ordering principle is:

> **Do not explore new territory until the ground already stood on is known to be real.**
> A foundation check that could *invalidate an existing belief* outranks any experiment that
> merely *adds* a belief — because everything built on a false foundation has to be redone anyway.

Concretely, that promotes three experiments and demotes everything else. Each of the three is
cheap, and each can **kill a claim we are currently relying on**. If all three pass, we have
earned the right to explore. If any fails, exploring first would have been wasted work.

---

## TIER 0 — running now

### E0. The bridge test: does the agent's own score predict the external objective?

**Question.** At time *t*, does the mean EFE score of the winning policies predict the *subsequent*
movement of damage reach toward or away from the class-IV band?

**Why first.** codex-1's Q1: damage reach is an observer-side counterfactual the agent cannot
perceive, and nothing in EFE references it. If the internal score carries no information about
subsequent damage movement, then **no controller built on an EFE-derived sensor can work** — which
retrospectively explains both failed controllers and forecloses the third. Nothing else on this
list changes as many downstream decisions.

**Design.** Twin pairs (perturbed / unperturbed) across a crossed set of γ ∈ {4, 8, 16, 32},
σ-vocabulary ∈ {shipped, 2 random}, 8 seeds, width 250, t to 200. Record per step: mean
`:winner :total` over cells, and damage(t). Test the correlation between score at *t* and
damage(t+20) − damage(t), computed **within configuration** (so it cannot be explained by
configuration-level differences), then pooled.

**Null.** No within-configuration correlation between score and subsequent damage movement.

**What each outcome licenses.**
- *Correlation present and consistent in sign* → an internal→external bridge exists; a controller
  is justified in principle, and E1/E2 decide whether its axis is meaningful.
- *No correlation* → **the seeking programme is dead as currently framed.** Pivot to codex-1's
  option (b): preferences over locally available goods, damage as external validation only, and
  the claim becomes *emergence*, not seeking. This is a publishable result and a cheaper paper.

---

## TIER 1 — the two remaining foundation checks

### E1. Is γ a physically meaningful axis, or a rescaling artifact?

**Question.** The softmax is P ∝ exp(−γ·total). `total` is a *sum of EFE terms whose absolute
scales are set by arbitrary implementation choices*. If rescaling the risk or epistemic term
reproduces a γ change, then γ is not an axis of the model — it is a unit convention.

**Why second.** γ's monotone traverse (damage 6.3 → 62.6) is the **single most load-bearing result
in Part III**. Everything about the class-IV band, the sensor screen and both controllers rests on
it. codex-1 flagged it and it has never been checked. Cheap: mostly a code audit of term
construction, plus one experiment holding γ fixed and scaling terms by a constant c, predicting
that damage(γ, c) = damage(cγ, 1).

**Null.** damage(γ·c, 1) ≠ damage(γ, c) — i.e. γ is *not* a pure rescaling and does have
independent content.

**What a failure means.** If γ *is* a rescaling, Part III's central axis is a unit convention, and
every γ number in the note needs restating in scale-invariant terms. Better to know now than after
publication.

### E2. Is the class-IV band an assay artifact?

**Question.** The band is defined by rule 54 = 36.0 and rule 110 = 38.1, at width 250, t=100, one
perturbed cell at position W/2. Width has **already** proved load-bearing (at width 80 the whole
objective saturates). Do the anchors and the band survive changing horizon and perturbation
position?

**Why third.** It threatens the *definition of the target*, not just a measurement. Cheap: plot
damage trajectories over t rather than reading t=100, and repeat one-cell perturbations across
several positions. No new machinery.

**Null.** Anchor ordering and the 36–38 band are stable across t ∈ {50, 100, 200, 400} and across
perturbation position.

---

## TIER 2 — conditional, and only after Tier 0/1

Ordered as codex-1 recommended, but **each is gated**:

| # | experiment | gate |
|---|---|---|
| E3 | mutation-kernel factorial (σ × k-distribution × invert-vs-copy × rewrite rate) | only if E0 passes AND σ-search is still live |
| E4 | blend-policy ablation (21.3% of novelty) | only if E3 shows the kernel matters |
| E5 | σ landscape map + oracle search vs matched random walk | only if E0 passes |
| E6 | initial-ensemble and seeding robustness | only if E1/E2 pass and a result depends on it |

**Not scheduled**: update ordering/synchrony, neighbourhood topology, planning horizon, exotype
kind count. All are real free parameters. None currently threatens a claim we are making, and each
would generate more possibilities than it closes.

---

## The stop rule

**Three experiments (E0, E1, E2), then a decision point — not a fourth experiment.** At that point
the state is one of:

- **All three pass** → the foundations hold; pick *one* of E3/E5 and pursue σ properly.
- **E0 fails** → pivot to the emergence framing. Part III becomes a paper about a null and an
  emergent classification, which is honest and finishable.
- **E1 or E2 fails** → stop experimenting entirely and restate Part III's existing numbers in
  whatever terms survive. No new experiments until the restatement is done.

The failure mode this rule exists to prevent is the one that has cost the most today: **running
the next interesting experiment instead of the one that could invalidate the last three.**
