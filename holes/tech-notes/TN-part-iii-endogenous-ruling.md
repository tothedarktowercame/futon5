# Ruling — the endogenous-search question, settled by measurement

**Fable, 2026-08-08.** Sixth note in the series, and intended as the last: the
three measurements the series called for have now been run, and this note
states what they decide and drafts what Part III should say. Scripts:
`scripts/none_label_check.clj`, `scripts/lstar_tau_measure.clj`,
`scripts/hetfixed_sheet_reference.clj`, `scripts/boundary_invasion_rescue.clj`
(all deterministic, all cheap to re-run). Inputs: the five prior notes
(`TN-fable-endogenous-interface-control-review.md`, `…-design-2.md`,
`TN-fable-endogenous-search-verdict.md`, `TN-fable-strict-locality.md`,
`TN-fable-construction-reframe.md`) and Joe's boundary-invasion proposal
(notice phase boundaries locally; provoke rule-mutations that invade from
them), which §4 tests directly.

## 0. The ruling in one paragraph

The question "can a MetaCA carrying an exotype layer find its own operating
point using only quantities available to it at runtime" splits under
measurement into three parts with three different answers. **Search — dead,
twice over**: the operations an external searcher performed are either
representationally unavailable to cells (axis discovery, restart) or, where a
local surrogate exists, the evidence it needs does not survive at any decision
rate (§2: the L\*/τ\_c kill condition fires in its strongest form). **But
search was also never the system's problem**: at every width and initial
condition tested without a policy layer, the mixed regime is the substrate's
*default basin* — the uncontrolled heterogeneous lattice coexists 24/24 even
from a deliberately frozen-leaning start (§1), and 6/6 at sheet scale from
the absorbing runs' own initial conditions (§3). What destroys coexistence in
this system is the optimizing policy layer itself, at high precision. So the
live endogenous question was never *find* but *protect* — can a strictly
local, calibration-free rule hold the regime against a policy that would kill
it — and §4 answers it: **protection by information fails** (the boundary-
adoption form absorbs *faster* than no intervention; the predicate-targeted
mutation form is matched exactly by its spatially-permuted yoke), **protection
by noise succeeds at preventing absorption** (6/6 against 0/6), **and what
noise preserves is a fragmented surrogate, not the found regime** (median
settled-domain width 2 against the exemplar's 4). The series closes: nothing
strictly local in this substrate knows where the edge of chaos is — not to
find it, and not even to hold it.

## 1. Measurement 1 — the `:none` label check

`none_label_check.clj`, an exact deterministic re-run of the four-arm gate's
uncontrolled arm (seeds 101–124, width 120, 1200 steps, frozen-leaning IC,
mean S reproduces the reported 0.4938 = 0.2938 + 0.20).

**Result: `:none` PASSES the coexistence label 24/24** — no seed absorbs, mean
settled fraction 0.403, per-seed S in [0.35, 0.67].

Consequences, in order of importance:

1. **The four-arm gate had no headroom.** It was designed around "a start the
   uncontrolled system does not escape"; the biased-frozen start does not
   deliver one. All four arms were scored on distance to an external 0.20 that
   coexistence does not require, in a basin where coexistence is free. The
   band-vs-yoke negative stands (spatial assignment of interface fitness adds
   nothing), but the run says nothing about *finding* a regime, because there
   was nothing to find.
2. **Detector external-validity caveat** (the branch the strict-locality note
   pre-registered): the preregistered no-overlap range [0.0984, 0.1617] does
   not transfer across ICs and widths — coexisting states at width 120 sit at
   S ≈ 0.35–0.67, 3× the top of the grid range. The 14/14 detector result
   stands *within its grid*; the S-range is not a universal signature. The
   detector paper must carry this.
3. No counterexample to the self-limiting argument arises (the high-S states
   are coexisting, not degenerate), but by the same token the relative-
   maximisation rule remains unsupported for the reasons already given
   (striping/breather maxima; ratchet).

## 2. Measurement 2 — L\* and τ\_c

`lstar_tau_measure.clj`: 8 seeds × 3000 steps of the het-fixed substrate
(width 120, full 14-operator vocabulary, random assignment — the §8 harness
context), measuring (a) P(correct pairwise ranking of two operators by a
windowed local-interface sample) against window length L, for distant pairs
(population-quality) and adjacent pairs (what a cell can actually compare);
(b) τ\_c, the site-level decorrelation time of the settled field.

**Result: the kill condition fires in its strongest form.**

| L | P(rank), distant | P(rank), adjacent |
|---|---|---|
| 5 | 0.571 | 0.540 |
| 10 | 0.571 | 0.521 |
| 20 | 0.557 | 0.526 |
| 40 | 0.549 | 0.538 |
| 80 | 0.533 | 0.527 |
| 160 | 0.554 | 0.521 |
| 320 | 0.520 | 0.513 |
| 640 | 0.519 | 0.509 |

τ\_c: median **6 steps** (q25 4, q75 8, q90 10, max 33) across 813 live sites.

Three facts, all load-bearing:

1. The operator differences are real — population mean local interface spans
   0.066 (fix4) to 0.149 (collapser) — but **no window length at any decision
   rate extracts the ranking locally**: P peaks at 0.571 and *decays* with L.
   For any useful confidence (δ ≳ 0.08), L\*(δ) does not exist. The condition
   "L\*(δ) > τ\_c for every achievable δ" is satisfied vacuously and maximally.
2. The *decay* with L is the nonstationarity mechanism made visible: the local
   context decorrelates in ~6 steps — less than half a settling window w = 15 —
   so longer integration pools evidence across context epochs and gets *worse*,
   not better. Fast controllers fail by noise, slow controllers fail by
   nonstationarity, and there is no third speed. This was stated as a
   prediction in the strict-locality note before it was measured, which is the
   strongest form a negative can take.
3. The adjacent-pair curve — the only comparison a strictly local cell can
   perform — never exceeds 0.540. A cell adopting its neighbour's operator
   because the neighbour's patch looks better is acting on a coin flip.

**Scope**: this kills *estimation-based* strictly-local steering — any rule
that must rank operators by their conditional effect on the observable, i.e.
the entire order-type family including the relative rule, and with it the
planned three-arm gate-arena run (correctly skipped per the pre-registered
order of work). It does not bind predicate-gated rules that use local state to
decide *where* to act rather than to rank operators — which is exactly the
surviving indicator family and Joe's proposal. Hence §4.

## 3. Where absorption actually lives

The two arenas must not be conflated, and the record now separates them
cleanly:

- **Gate arena** (width 120–250, operators fixed or slowly adopted): the mixed
  regime is the default basin. Nothing tested absorbs — not the generic random
  IC (first gate run, 8/8 coexist), not the frozen-leaning IC (§1, 24/24), not
  any single held operator (the most settling, `:collapser`, reaches settled
  0.497 and stays mixed).
- **Sheet arena** (width 250, per-cell EFE policy selecting operators at
  precision β with epistemic weight κ): absorption is real and β-driven —
  β = 12, 14, 16 freeze solid by t ≈ 1700–2600; β = 8, 10 at κ = 0.1 sustain
  the lava-lamp regime (Fig. 19's right-to-left progression).

The absorbing tendency the paper's bisection navigated is therefore **created
by the optimizing policy layer**, not by the substrate. The external search
over β was a search over the *exogenous* parameters of a controller — no
quantity a cell computes was ever on that axis. This dissolves the original
question's premise: the system did not need to find the edge of chaos; it
needed its controller not to push it off.

Pinned at sheet scale (`scripts/hetfixed_sheet_reference.clj`): the width-250
substrate with the *identical* initial components as the absorbing sheet runs
(same seeds 2026102000–2026102005, same genotype/phenotype/exotype draws),
operators held fixed and the policy removed, **coexists 6/6** — settled
fractions 0.083–0.145, no seed absorbs in 3000 steps. Same lattice, same
starting point; remove the optimizer and the regime is simply there.

## 4. Measurement 3 — the boundary-invasion rescue (Joe's rule)

The one live endogenous question, posed where there is headroom: at
β = 16, κ = 0.1 — the deep-freeze arm — can a strictly local rule hold
coexistence? Joe's mechanism, as the indicator-type family's admissible form:
a cell whose radius-1 locale is *pure* (all-settled or all-unsettled — the
calibration-free predicate "at least one settled and one unsettled neighbour"
failing) may, on its jittered dwell clock, receive one provoked rule-mutation;
cells at boundaries (mixed locales) are left alone. Placement is the
predicate's only content: it is satiating, has structural edges (no swept
constants), and cannot ratchet.

Arms, sharing substrate seeds (2026102000–2026102005, width 250, 3000 steps):

- `:policy` — unmodified EFE policy (baseline; the paper's absorbing arm);
- `:invade-adopt` — pure-locale cells copy a mixed-locale neighbour's
  operator when one exists (WHERE from the predicate, WHAT from the boundary —
  the literal "invade from the boundary" form);
- `:invade-mutate` — pure-locale cells take a uniform random vocabulary draw
  (WHERE only; WHAT is noise);
- `:yoked` — the same number of overrides at uniformly random cells with
  random kinds (neither WHERE nor WHAT; if this matches the invade arms, the
  mechanism is churn, not information).

Goodhart gates carried from the earlier reviews: settled-domain width
distribution (fragmentation), settled-interval lifetime mass in [w, 2w]
(breathers). An override is a single post-step exotype write — one operator
application — so persistence runs through the rewritten genotype byte: a
provoked mutation, not an occupation. The invading operator also enters the
policy's own adoption pool at neighbouring cells, so genuine invasion is
dynamically possible.

**Result: outcome 2 of the three pre-stated — with two sharpenings the
pre-statement did not anticipate.**

| arm | coexist | absorbed | mean S | mean settled | ovr/step | width med | width ≤ 2 |
|---|---|---|---|---|---|---|---|
| `:policy` | 0/6 | 6/6 | 0.000 | 1.000 | — | 250 | — |
| `:invade-adopt` | 0/6 | 6/6 | 0.000 | 1.000 | 0.40 | 250 | — |
| `:invade-mutate` | 6/6 | 0/6 | 0.271 | 0.315 | 9.39 | 2 | 0.56 |
| `:yoked` | 6/6 | 0/6 | 0.264 | 0.292 | 9.42 | 2 | 0.60 |

Determinism anchor: `:policy` at seed 2026102000 stops changing at t = 1723 —
the paper's published absorption at t = 1722, same event, one-off counting
convention. The experiment is continuous with the published bisection.

Reading, in order of severity:

1. **The literal invasion form is worse than nothing.** `:invade-adopt` —
   pure-locale cells copying a boundary neighbour's operator, the exact
   "invade from the boundary" mechanism — absorbed on all six seeds, and on
   five of six *earlier* than the unmodified policy (mean −297 steps; it also
   starves as it fails: overrides fall to nothing once no mixed locale
   remains). The mechanism is not merely uninformative, it is **conscripted**:
   at β = 16 the operators found at phase boundaries are the freeze front's
   own, so copying them inward accelerates the winning phase. This is the
   L\*/τ\_c result made vivid — the WHAT channel carries no ranking signal,
   and under an advancing phase its content is supplied by the enemy.
2. **Noise rescues; placement adds nothing.** `:invade-mutate` holds the
   coexistence label 6/6 where the policy absorbs 6/6 — and so does its yoke,
   with statistically indistinguishable settled fraction, S, and override
   rate (per-seed paired differences are small and mixed-sign, 6 seeds). The
   predicate's spatial information — the only content the indicator rule
   has — is doing no measurable work. What prevents absorption is undirected
   rule-layer noise at ~3.8% of cells per step, full stop.
3. **What noise holds is not the found regime.** The width gate fires: the
   rescued state's settled domains have median width 2 with the majority
   (56–60%) at ≤ 2 cells, against the exemplar regime's median width 4 —
   a fragmented, noise-churned mixture rather than the lava-lamp's coherent
   turnover. (The breather gate does not fire: [w, 2w] lifetime mass 0.16–0.17
   against the 0.30 criterion.) So even the weak "hold" claim must be stated
   as **anti-absorption, not regime-holding**: a mutation floor keeps the
   lattice alive, and what it keeps alive is a different, finer-grained state
   than the one the external search located.

Scope note: the adopt form was tested as a single-write override (one operator
application per firing). A sustained-occupation variant is untested, but its
information channel is the one measurement 2 killed, and its content the one
mechanism 1 shows to be conscripted; there is no remaining reason to build it.

## 5. What Part III should now say

Replace the agnostic core of "The Question This Part Does Not Answer" with the
settled decomposition (keeping the section's honest register):

1. The apparatus result stands unchanged: runtime observables track an
   intrinsic objective (R² = 0.73) and damage reach not at all (0.02).
2. **Find is external, and quantifiably so**: the searcher injected O(log)
   bits once per regime (an axis, ~7 bisection probes, a setpoint) plus
   restarts. Axis discovery is representationally unavailable at cell scale;
   restart is unavailable in principle after global absorption; and the local
   evidence channel that a within-run searcher would need fails at every
   decision rate (the L\*/τ\_c table). Externality is measured in
   bits-per-regime, not bits-per-decision — that is the precise sense in which
   the examples were located by "the apparatus together with our own reading."
3. **The searched-for regime is the substrate's default in the absence of the
   optimizing layer** (§3). The bisection was a search over the controller's
   exogenous parameters, and the absorbing states it navigated between are
   artifacts of that controller at high precision.
4. **The hold question closes negative in the strong sense and positive only
   in the weakest** (§4): boundary-directed adoption accelerates the freeze it
   was built to fight; predicate-targeted mutation is matched by its yoke; an
   undirected mutation floor prevents absorption but preserves a fragmented
   state that fails the pre-registered width gate. Local mechanisms can keep
   this lattice alive; none of them knows where the regime is.
5. The corrected rate statement, for the record (it is the reusable methods
   contribution): *when a detector's discriminative power is purchased by
   temporal integration over a window w, feedback closed through it inherits
   ≥ w of group delay; and when the local context's stationarity time is
   shorter than the integration a reliable local discrimination needs, no
   decision rate exists at which the feedback works — fast fails by noise,
   slow fails by nonstationarity.* Both quantities are measured here (w = 15
   filter, τ\_c ≈ 6, P(rank) ≤ 0.571 ∀L). This is not Goodhart — the yoke
   showed the measure was never steering — and the empirical winner
   (controlling the fast proxy directly) is the degenerate Smith predictor.

Retirements confirmed by today's measurements, final: the order-type
(relative) rule and every estimation-based local controller; any w-filtered
statistic as per-decision feedback; parameter iteration on interface feedback;
the gate-arena three-arm run. Surviving programme: the construction reframe
(materials catalogue; rung M1), with τ\_c ≈ 6 now available as the ambient
erosion clock M1's persistence numbers need as baseline.

## 6. Addendum (later the same day) — the boundary-blast test

Joe's counter-proposal after §4: the pure-locale trigger left boundaries
alone; instead put the random writes *at* the boundary — mixed-locale cells —
to blast crossings through frozen zones, motivated by the natural crossings
visible in the γ=1–2, κ=0.1 sheets (live filaments tunnelling through frozen
bands under low-precision policy churn; full-horizon check: those crossings
are the live phase's weapon — at low γ they end in total melt, frozen
fraction 0.062/0.003 past t=1000). `scripts/boundary_blast.clj`, four arms,
same seeds; results `reports/boundary-blast.edn`, figure
`figs/boundary-blast.png`.

| arm | coexist | absorbed | mean settled | ovr/step | width med | width max |
|---|---|---|---|---|---|---|
| `:blast` (dwell 15–25) | 5/6 | 1/6 | 0.821 | 1.66 | 2 | 250 |
| `:yoked-blast` | 5/6 | 1/6 | 0.689 | 2.34 | 2 | 250 |
| `:blast-fast` (dwell 5–10) | 6/6 | 0/6 | 0.253 | 8.35 | 2 | 70 |
| `:yoked-fast` | 6/6 | 0/6 | 0.270 | 9.00 | 2 | 62 |

Three findings:

1. **Dose dominates, and the dial does not pass through the regime.** ~1.7
   writes/step gives a near-frozen marginal state; ~8.5 gives the same
   fragmented mixture as §4's pure-locale arms (settled ≈ 0.25 ≈ the rescue
   run's ≈ 0.3). At matched dose (the fast pair) placement is again
   indistinguishable. Nothing on the noise axis, at any tested dose or
   placement, reproduces the coherent valley regime (median width 4 with
   turnover); the axis interpolates from absorption to fragmentation without
   passing through it.
2. **At low dose, placement finally shows a signature — morphological, not
   label-level.** The blast rule's dose is proportional to boundary density,
   which falls as freezing advances: a negative feedback that concentrates
   every write onto the last remaining seam. The pinned-seed spacetime shows
   the result — a frozen continent covering ~0.92 of the ring with a **live
   filament held open through it for the final ~1800 steps**: Joe's crossing,
   sustained indefinitely, at the price of the rest of the lattice. The yoke
   at the same rule scatters its writes, keeps more of the lattice mixed
   (realized dose 40% higher — the count-matching is dynamic and diverges),
   and holds a patchwork instead. So WHERE does matter at low dose — it
   selects the *form* of marginal survival (concentrated seam vs scattered
   patchwork) — but neither form is the regime, and the label statistics of
   the two arms are identical.
3. **The natural crossings are not reproducible by boundary-local writes
   alone.** At low γ the crossings were powered by lattice-wide policy churn
   — the whole live phase pressing behind each breach. A dosed boundary
   write opens the breach but nothing follows through; at β = 16 the policy
   re-freezes the far side immediately. The breach mechanism needs the live
   phase's collective pressure, which is a global property of the policy
   parameter, not a local resource.

Consequence for the ruling: unchanged in substance, sharpened in statement.
The §0 sentence "protection by noise succeeds at preventing absorption"
gains the qualifier *and at no dose does noise hold the found regime*; the
one candidate positive for placement information is the seam-concentration
morphology, which is worth one sentence in the paper's closure section and
no more.

## 7. Addendum 2 (2026-08-08, evening) — the "median 4" number does not
## survive an estimator audit, and "the regime is the default" is retracted

Joe challenged the ruling's central rhetorical claim ("the regime is the
substrate's default") on three grounds: the optimizer is five days old
(futon5 history: policy layer first committed 2026-08-03, the sheet
apparatus 2026-08-07), so "the search was hard because the optimizer made
it hard" is ahistorical; seven months of pre-policy exploration mostly
found nothing interesting, so default-interestingness contradicts the
operator's observed base rate over a much broader configuration sample;
and the no-policy control samples one engineered corner (twelve-operator
vocabulary, two geometries), not "MetaCAs."

The measurement check (`scripts/morphology_default_check.clj`,
`reports/morphology-default-check.edn`) is worse than the scope problem.
Under the rescue script's own estimator (circular settled-run widths over
the last 500 steps), six seeds each:

| state | frozen (late) | width median | p90 |
|---|---|---|---|
| het-fixed, no policy | 0.083–0.145 | 2 | 3–5 |
| valley, β=8 κ=0.1 policy on | 0.000–0.093 | 1 | 2–4 |

The "exemplar regime's median width 4" quoted in §5 and §6 comes from the
**lavalamp caption's connected-spacetime-component statistic** (median
component width 4, lifetime 24, max area 650) — a different estimator.
Under the like-for-like run-width statistic the valley measures median 1,
*finer* than both the no-policy state and the noise-rescued state (2), and
in 3/6 valley seeds the frozen phase is nearly extinct by t=2500–3000
(frozen ≤ 0.002) — the pinned lavalamp seed is one of the three that hold.
So: (a) the rescue-vs-exemplar "2 vs 4" contrast was cross-estimator and is
withdrawn; (b) no statistic we computed separates the noise-held, no-policy,
and valley states; the visible structural difference (coherent components,
genotype diversity) is real in the figures but currently unquantified
across states; (c) the β=8 valley is itself seed-fragile at width 250.

Consequence: the ruling's §0 sentence stands only in its narrow form —
absorption at this corner is produced by the policy at high β, and the
label-level coexistence default (6/6 width 250 + 24/24 width 120) is
policy-free. "The regime is the substrate's default," "needs no finding,"
and the fragmented-surrogate-vs-regime contrast are retracted from the
paper (draft9 closure section rewritten accordingly, 2026-08-08). Open:
a uniform connected-component analysis across the three states, which
would settle whether the found configuration differs from the held ones
by a computable number.

## 8. Reproducibility

- `scripts/none_label_check.clj` — ~3 min, exact seeds, no arguments.
- `scripts/lstar_tau_measure.clj` — ~4 min, 8 seeds × 3000 steps, prints the
  table and τ\_c quantiles.
- `scripts/hetfixed_sheet_reference.clj` — ~2 min, the sheet-scale no-policy
  reference (6/6 coexist).
- `scripts/boundary_invasion_rescue.clj` — ~40 min on 8 cores, writes
  `reports/boundary-invasion-rescue{,-traj}.edn`.
- `scripts/boundary_blast.clj` — ~40 min on 8 cores, writes
  `reports/boundary-blast{,-traj}.edn` and dumps the pinned-seed sheets to
  `figs/blast-*-phe.txt`; figure via `scripts/boundary_blast_figure.py`
  (pinned `.venv-figures` python).

All three derive every random draw from named seeds; the numbers above are
bit-reproducible.
