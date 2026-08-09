# Strict locality — audit, the relative-rule argument, and the shape of the impossibility

**Reviewer: fable, 2026-08-08.** Fourth note in the series. Inputs: the two
corrections (scope = within a non-absorbing basin; the reported negative was
locality-contaminated, TARGET = 0.20 being externally calibrated) and the
strict-locality constraint: bounded-window own history + bounded-radius
neighbour states only; no global aggregates, forks, or swept-and-inspected
constants; every constant structural or locally derivable.

Scope correction accepted without reservation: my (C) answered a stronger claim
than was being made, and the in-scope question is find-and-hold within a basin
that does not absorb. One residue of (C) survives rescoping and is noted in §2.

## 0. A number in your own report that decides most of question 2

`:none` finished at S = 0.49 (distance 0.2938 from 0.20). The validated
coexistence range is [0.0984, 0.1617]. **The uncontrolled lattice, in this
basin, sits at three times the top of the coexistence range.** So states with
far more interface than the mixed regime are not merely reachable — here they
are the default. One cheap check decides what this means, and you should run it
before anything else: **compute the coexistence label (never absorbs AND settled
fraction in [0.02, 0.98]) for the `:none` arm.**

- If `:none` fails the label: you possess a direct counterexample to "the
  maximum of S is coexistence" — a high-S state that is not the regime. The
  relative-maximisation argument is dead on your own data.
- If `:none` passes the label: the detector's "no overlap" range does not
  transfer across ICs and widths (the 35-cell grid never produced S ≈ 0.49),
  which is an external-validity caveat the detector paper must carry — and the
  self-limiting argument survives this particular bullet but not §1's others.

Either branch matters; neither costs more than a script.

## 1. The relative-rule argument, checked (question 2)

**The argument is unsound.** It repurposes my degenerate-proof argument beyond
its stated scope, and the original review flagged the exact gap it falls into.

1. **Self-limiting at the poles does not put the maximum at coexistence.** The
   two-sided vanishing argument rules out the two *uniform* phases only. The
   configuration-space maximum of S is spatial period-2 in the settled field —
   width-1 fragmentation, S → 1 — and the temporal analogue (threshold-riding
   breathers, staggered flips at period ~w+1) also scores far above the
   coexistence range. Both were named in the first review as exactly the cases
   the "by construction" argument does not cover. Coexistence lives at
   S ≈ 0.10–0.16; the observable's maximum lives near 1. "Interior optimum
   along the phase axis" and "maximum over configuration space" are different
   claims, and the relative rule targets the second.
2. **The detector's empirical range does not bound a controlled system.** The
   grid's S values described *passive* dynamics; a maximising controller
   changes the reachable set — that is what control is. Assuming the controlled
   lattice stays inside the passively observed range is assuming the
   conclusion. (And §0 shows even passive dynamics leave the range at this
   width/IC.) Note this failure mode is **Goodhart proper**, unlike the
   previous negative, which was latency: a relative maximiser is the first
   design in this series with a genuine incentive to corrupt the measure.
3. **The relative rule ratchets.** "Adopt the neighbour with more interface"
   contains no term that ever prefers *less*. Interior cells of healthy domains
   tie at zero and hold (fine), but every cell adjacent to a gradient copies
   wall-generating operators inward; wall density is monotonically favoured for
   as long as any neighbour exceeds self. Its closed-loop fixed points are the
   maximal-wall-density states the substrate can sustain — fragmentation or
   breathers — unless substrate dynamics push back. Holding a *moderate* S is
   not expressible in the rule: satiation needs a band, which is the thing the
   relative form was built to avoid.

**Direct answer to your specific question** — yes, there is a local-maximisation
failure mode where local interface rises while the lattice leaves the region:
two, spatial (width-1 striping) and temporal (breathers), plus the ratchet
argument for why the rule steers toward them rather than merely permitting them.

**Against your expectation, one point in the rule's favour.** Your inference
"we already ran a maximise gradient and it lost to its yoke, so the relative
rule will too" is not airtight. The out-of-range band and the relative rule
have different gradient structure where it mattered most: on the cold start,
the band's fitness landscape was flat (|0 − 0.20| everywhere), while a relative
comparison has signal wherever neighbours differ at all, including at low S. So
the relative rule is not predetermined to reproduce the band's failure — which
is precisely why it is worth running. My expectation still matches yours
(failure), but with two distinguishable signatures, and the experiment should
be instrumented to tell them apart:

- **Rate failure**: does not beat its yoke; S stays wherever churn puts it.
- **Goodhart failure**: beats its yoke, S climbs *through* [0.10, 0.16] and
  keeps going; settled-domain widths → 1 or settled-interval lifetimes cluster
  in [w, 2w]. The two gates from the earlier reviews (width and lifetime
  distributions) are outcome checks, not mechanism, so they are
  locality-admissible and must both be in.

A cleaner negative than the one you have, either way — agreed. One addition
(§2 explains why): also run the **indicator variant** alongside, same harness.

## 2. The survival audit under strict locality (question 1)

- **(A) space-for-runs: survives, in one narrow form only.** Your audit is
  right that one cell-pair comparison conflates operator with context. The fix
  randomisation provides is population-level: with assignment uncorrelated
  with context, the conflation averages out *across many pairs* — and no
  single cell performs that average, but **adoption dynamics perform it
  implicitly**: operators that do better across many local contexts spread.
  Selection is spatial pooling by differential replication — the one lawful
  loophole in the no-pooling constraint. Its validity decays as
  operator–context correlation builds (adoption creates it), which is what
  ε-mutation exists to counteract: it keeps a randomised stream flowing.
  Discipline point: ε and dwell must be expressed as functions of structural
  parameters to stay admissible — ε = 1/|V|² and dwell ~ uniform[w, 2w] are
  declarable forms; "0.02 because it worked" is not.
- **(B) slow local criterion: survives.** Agreed.
- **(C) restart: out of scope, accepted — with one in-scope residue.** Within
  the basin, the question that survives rescoping is *reversibility of
  Goodhart states*: frozen regions are recoverable (melting is fast, a live
  neighbour re-agitates them), but whether fragmented or breathing regions are
  locally escapable is unknown. If they are one-way at this width, they are
  in-scope absorbing states in all but name, and the §1 experiment should
  report whether any run that entered them left. One column in the results
  table, no extra runs.
- **(D) axis discovery: dead.** Unchanged by rescoping.
- **Two-timescale: numeric setpoints are gone at BOTH levels — note that this
  disqualifies your empirical winner too.** The audit lists `:band`'s 0.20 as
  inadmissible; the `:activity` arm's 0.20 is calibrated in exactly the same
  way. Under strict locality there is no incumbent controller. What survives
  of the two-timescale idea is the one calibration-free interior preference
  that exists: on a k-cell locale, the only band whose edges are structural
  rather than swept is **[1, k−1] — "at least one settled and at least one
  unsettled cell in radius r."** A predicate, not a number; derivable from the
  observable's own definition; satiating (indifferent among all mixed
  locales), therefore ratchet-free. Its cost is the mirror of its safety: the
  gradient is nonzero only at pure locales, so it can rescue cells from the
  poles but cannot rank mixtures. General characterisation, worth stating
  because it maps the admissible space exactly: **strict locality admits
  order-type preferences (comparisons, like your relative rule) and
  indicator-type preferences (predicates, like local mixedness), and forbids
  metric setpoints. Every admissible objective is built from those two
  atoms.** The relative rule is the strongest-gradient member of the space and
  Goodharts; the indicator is the weakest and cannot; a lexicographic
  composite — "if my locale is pure, adopt from any neighbour whose locale is
  mixed; else hold; ε-mutate" — is the admissible middle, and is the arm I
  would add to the §1 run. Predicted signatures: the relative rule fails by
  ratchet or rate; the indicator composite, if it fails, fails by rate alone
  and cannot Goodhart, which makes it the cleanest instrument for question 3.

## 3. The rate obstruction under strict locality (question 3)

**As stated, it is not an impossibility — one of its two inputs is free.**
Nothing about locality forces fast decisions: a dwell counter is strictly
local, w is structural, and a cell may decide every 2w steps with a 2w window.
Rate-matching the filter is trivially available. So "strict locality + temporal
integration" cannot be impossible *on rate grounds alone*.

The genuine candidate impossibility lives one level down, and locality is what
arms it. Slowing the clock buys freshness but not evidence: a strictly local
estimator can reduce variance only by integrating over **time**, because
integrating over **space** is exactly what the constraint forbids (a global
aggregate is spatial pooling). Temporal integration is in turn capped by the
stationarity of the local context: domain walls wander and neighbours'
operators change, so evidence collected in different context-epochs does not
pool — the quantity being estimated ("which operator is better *here*") has
moved before the estimate converges. Two timescales govern everything:

- **L\*** — the integration a reliable local discrimination needs: the
  smallest window L such that a radius-r observation ranks two operators by
  their true conditional effect with probability ≥ ½ + δ.
- **τ_c** — the stationarity time of the local context: how long the
  conditional values of operators at a site stay put (measurable as the
  decorrelation time of the settled field at a site under held-fixed
  operators — substrate-intrinsic, since walls wander even without control).

If L\* > τ_c, evidence decays faster than it accumulates at every site, at
every decision rate: fast controllers fail by noise, slow controllers fail by
nonstationarity, and there is no third speed. The only escape is pooling
across sites, which strict locality forbids explicitly — except the implicit
replication loophole of §2(A), whose effective pooling is bounded by the same
ratio (selection can only amplify a per-site bias that per-site evidence
actually contains).

## 4. The impossibility, stated for checking (question 4)

> **Claim (conditional impossibility of strictly local steering by a
> temporally integrated observable).** Fix radius r and let the controller be
> any rule reading only radius-r neighbourhoods over bounded windows, with any
> decision schedule, all constants structural or locally derived. Let L\*(δ)
> be the smallest window such that a radius-r observation ranks two operators
> by their true conditional effect on the observable with probability
> ≥ ½ + δ. Let τ_c be the stationarity time of the site-level context
> relevant to that effect, measured under held-fixed operators. **If
> L\*(δ) > τ_c for every δ large enough to overcome the replication noise
> floor, then no such rule steers the lattice by this observable better than
> its yoked control.** Escaping the conclusion requires denying an
> assumption: (i) spatial pooling — forbidden by the constraint, with
> implicit pooling-by-replication bounded by the same L\*/τ_c ratio;
> (ii) context-dependence of operator values — if values are in fact
> context-free, confounding vanishes, L\* collapses to a variance bound, and
> the claim's premise fails (this is testable and would be good news);
> (iii) the observable — substituting a faster one exits the claim's scope.

Both quantities are measurable from data already on disk, which is what makes
this checkable by someone else rather than rhetorical: **L\*** by extending
the structural test across window lengths (P(correct pairwise ranking) vs L,
from the het-fixed harness); **τ_c** from site-level settled-field
autocorrelation in the same runs. My expectation, from the h-scan spread
(0.16 across the whole vocabulary against within-site noise): L\* in the
hundreds of steps; τ_c in the mixed regime unknown but plausibly shorter.
Measure both before running anything; if L\* > τ_c the §1 experiment becomes
the confirmation of a stated prediction rather than another exploratory
negative — which is the strongest form the write-up can take.

## 5. Does the constraint empty the space?

No — but it reduces it to a single family: **selection dynamics
(adopt/hold + structural-ε mutation, dwell ~ w) driven by order- or
indicator-type fitness on the observable**, i.e. essentially local evolution
under a comparison or a predicate. Everything else in four notes of designs is
inadmissible or dead: numeric bands at both timescales (calibrated), the
semi-analytic predictor (its channels were swept), axis discovery
(representational), and the empirical winner itself (calibrated setpoint).
The surviving family has exactly two known failure modes — Goodhart for the
order type (§1), the evidence budget for both types (§3) — and one measurable
kill condition, L\* vs τ_c, that decides in advance whether the family is
viable at all. Order of work: (1) the `:none` label check (§0, a script);
(2) L\* and τ_c from existing logs (§4); (3) only if L\* < τ_c, the
three-arm run — relative rule, indicator composite, yokes — with the width
and lifetime gates and the reversibility column. If L\* > τ_c, skip the run,
and the series ends with a checkable impossibility statement instead of a
fifth controller — which, as you say, is the outcome most worth having if it
is true.
