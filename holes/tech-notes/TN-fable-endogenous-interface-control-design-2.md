# Design 2 — endogenous interface control under the h≈20 constraint

**Reviewer: fable, 2026-08-08.** Follow-up to
`TN-fable-endogenous-interface-control-review.md`, whose §8 (claude-14's
action-contrast measurement) is the input here: across-operator contrast in local
interface is weak at every horizon and clearly above a 200-permutation null only at
h≈20 (0.1636 vs p95 0.1245); h=1 crosses marginally; run-level separation of S is
untouched. This note is a buildable proposal, not a critique.

## 0. What h≈20 means — and the constraint it puts on any controller

**The lag is mostly instrumental, not physical.** Decompose the path from action to
observable: exotype → genotype (t+1) → phenotype (t+2) → settled bit → interface.
The physical response of the *state layer* to an operator choice arrives at t+2 —
that lag is real and documented. Everything after that is the observable's own
definition: the settled bit is a w=15-step low-pass filter over flip events, so a
freezing event *cannot register* in fewer than w steps, by construction, and the
first horizon at which an operator's full effect (both the fast melting direction
and the slow freezing direction) is visible in the interface is ≈ 2 + w ≈ 17–20.
The measured peak sits exactly there. On this reading h≈20 is not a mysterious
property of the dynamics; it is lag + w.

This is testable, and should be tested before anything is built (**Gate 0**, §6):
rerun the §8 harness at w = 10 and w = 25. If the contrast peak tracks w (≈ lag+w),
the instrumental-lag reading is right and the designs below are on solid ground. If
the peak stays at 20 regardless of w, the leverage is something else and this note's
premise is wrong — re-diagnose before building anything.

**Second consequence, independent of the first: the §8 contrast was measured under
operators held fixed** (`:heterogeneous-fixed` assigns at init and never changes
them). A controller that re-decides every step violates the premise under which the
only leverage we have evidence for was measured. Whatever else the design does,
**cells must hold their operator for a dwell comparable to the leverage horizon**
(~20 steps). This is not an optimisation of question 2's option (c); it is the
condition of validity of the evidence.

## 1. The settled-transition hazard band under the lag (question 1)

The h≈20 result splits the hazard band into a fast half and a slow half, and the
split is exact, not approximate:

- **Melting (settled → unsettled) is fast.** One flip un-settles a cell, and the
  flip responds to the operator at t+2. Melting hazard is measurable and actionable
  at short horizon. This half survives unchanged.
- **Freezing (unsettled → settled) is definitionally slow.** The *event* requires w
  quiet steps; no short-horizon observation of the event exists, and no model can
  conjure one — the lag is inherited from the definition, full stop. But its
  **precursor is fast**: flip hazard low while the quiescence counter climbs. A
  cell that knows its counter c and its flip hazard ρ knows its freezing
  trajectory; it does not need to wait w steps to know it is freezing.

So: **bidirectional flux is measurable at short horizon only in precursor form**
(melting hazard + freezing precursor), and in event form only at the slow timescale
(realized transition counts over a ≥w window). Both forms are usable; they belong
to the two different arms in §2. The stationary, interior, fixed-C character of the
flux/band preference survives in both — the lag changes *where the preference term
lives* (which clock, which channel), not its shape.

One correction to my earlier framing: a naive band on a cell's *own* flip hazard is
now actively dangerous — uniform ρ ≈ 1/w across the lattice is precisely the
threshold-riding breather (§4). The preference must stay on the neighbourhood
*pattern* (settled and unsettled coexisting locally), with hazard as the mediating
variable, never as the target itself.

## 2. The options, ranked, and what I would build (question 2)

First, the list of four is missing the strongest candidate. Adding it:

- **(e) Realized-outcome adoption on the slow clock.** Do not predict the slow
  variable at all. Every T ≈ 20 steps, each cell compares its own *realized* local
  interface over the last T steps with its two neighbours' realized values, scores
  each against the band, and adopts the operator of the best-in-band neighbour (or
  holds; small ε-probability of adopting a random vocabulary member so frozen
  blocks are not informationally dead). Realized windows integrate the h≈20
  leverage automatically; no 20-step predictive model exists to be wrong.

Ranking, with reasons:

1. **(e)** — first, because it consumes the leverage at its native timescale with
   zero model risk, and because the codebase has already built most of it:
   `selection.clj` is exactly a windowed realized-outcome fitness with
   neighbour-copy dynamics (`selection-window-size` 40, `fitness-values
   :preferences` = negative absolute deviation from band targets over a window).
   The build is "add a local-interface term to an existing fitness," not a new
   architecture. It is also still a *local preference-driven controller* — the
   fitness IS a band-KL against a fixed C, evaluated on observations rather than
   predictions — so the project's endogenous-control claim survives intact.
2. **(b)+(a) hybrid, semi-analytic** — second. The fast proxy is the flip hazard,
   and the h=20 prediction does not need to be *learned*, because the slow
   observable is a deterministic filter over the fast one. Concretely: estimate
   per-(operator, locale-bin) flip hazard ρ at h=2 from the same derivation
   harness that built the current conditional model; then, for horizon T ≥ some
   cell's remaining need, P(settled at t+T) has a closed form from ρ and the
   cell's known quiescence counter c — under an iid approximation, (1−ρ)^T when
   c ≥ w−T, else 0 for T < w−c, and (1−ρ)^w for T ≥ w. Predicted local interface
   follows from the neighbours' settled probabilities. The 20-step map factorizes
   as (validated fast channel) × (known counter dynamics). Approximations (iid
   flips, independent neighbours) are declared and its held-out skill against a
   base-rate predictor is a build gate, not an assumption.
3. **(c) alone** — necessary but not sufficient. A slow clock over the *existing*
   one-step model just makes bad decisions less often: the model still cannot tell
   operators apart on the quantity being preferred. Dwell is infrastructure for
   (e) and (b), not a design by itself.
4. **(a) as stated (learned 20-step model)** — last among the live options.
   Conditioning must be on operator-held-for-20 (sparse rows), credit assignment
   across 20 steps is unsolved, and a per-step re-deciding controller violates its
   own model's premise. Everything it could deliver, the semi-analytic version
   delivers cheaper. Do not build this.
5. **(d) full abandonment** — premature. (e) is already half of (d) (it abandons
   per-step model-based control); going further is only warranted if the §5
   structural test fails.

**What I would build: (e) as the primary arm, (b)-hybrid as the secondary arm, on
the same seeds and controls.** The comparison is itself informative: if (e) works
and (b) does not, the predictive model is the bottleneck and the endogenous claim
still stands on (e); if neither works, the leverage is insufficient (§5); if both
work, (b) supplies the mechanistic interpretation.

Concrete spec for arm (e), the items two implementers would otherwise diverge on:

- **Clock**: per-cell dwell drawn uniform from [15, 25] at each re-decision,
  phases random at init. Jitter is load-bearing (§4), not a nicety.
- **Observable**: cell i's realized local interface = mean over its dwell window
  of its two interface bits (self-vs-left, self-vs-right settled-bit XOR). With
  T ≈ 20 this has ~40-value support — the slow clock incidentally fixes the
  radius-1 support-coarseness problem from the review (§5.3 there).
- **Band**: translate the prospective grid's mixed-regime global range
  [0.0984, 0.1617] into expected per-cell window means, measured on the existing
  mixed-regime sheets (a calibration script, prespecified numbers committed
  before the run).
- **Decision**: score own and both neighbours' realized values by −|value −
  band-centre| (or 0 inside the band); adopt argmax's operator; ties prefer
  hold. ε = 0.02 random-vocabulary mutation.
- **Policy set**: hold/adopt-left/adopt-right(+mutation). Keeps the spatial
  correlation structure of the exotype field; resolves review §5 item 1 as
  option (c) there.
- **Codebase**: the mmca-clj worktree (12–14-kind vocabulary, the harness §8 ran
  in). Named to close review §5 item 2.

## 3. The ambiguity hazard under the new designs (question 3)

Does it persist? **For any design that adds a predicted channel, yes** — the
mechanism at `efe.clj:320` (ambiguity = Σ Bernoulli entropy over all predicted
channels) is indifferent to which channel is added, and any channel whose
uncertainty peaks in the preferred regime pays the same penalty.

- **Arm (e) sidesteps it entirely.** Nothing is added to the predictive model;
  the interface term lives in the selection fitness, which has no ambiguity term.
  This is a genuine additional argument for ranking (e) first.
- **Arm (b) needs the fix**, and the fix is principled, not a hack: in EFE,
  ambiguity is the expected entropy of *observations given states* — it penalises
  states whose observations are uninformative about them. Local interface is a
  **deterministic function** of the settled bits (an XOR of adjacencies), and the
  settled bits are deterministic functions of state history. Its observation-
  given-state entropy is exactly zero. The uncertainty in the *predictive*
  distribution over next interface is uncertainty about which state will obtain —
  that is risk-relevant, and the preference-KL already prices it. Summing its
  predictive entropy into ambiguity double-counts state uncertainty as if it were
  sensor noise. So excluding the interface channel from the ambiguity sum is not
  an exemption carved out to protect the result; it is the correct value of the
  term. Write that reasoning into the docstring at the exclusion site, and keep
  an ablation arm with the channel included so the claim is checked, not assumed.
- Housekeeping either way: `invariants-test/chaos-is-the-structural-argmin` pins
  the current argmin. The whole point of the new term is to move the argmin, so
  that test must be *intentionally revised* alongside the change — a failing
  invariant left unexplained, or silently deleted, would each be their own defect.

## 4. Do the breathers still bite? (question 4)

**Yes — harder than before, and with a new resonance.** Two aggravations:

1. A hazard-mediated preference makes the breather the *proximal* optimum:
   uniform flip hazard ρ ≈ 1/(w+1) maximises threshold-riding directly. This is
   why the preference must target the neighbourhood pattern, never own-hazard
   (§1).
2. The dwell clock T ≈ 20 is close to the breather period ~w+1 = 16. A lattice of
   cells re-deciding on a clock commensurate with the cheapest S-generating cycle
   can entrain: "flip once per dwell" is a fixed point of the realized-outcome
   dynamics if windows and periods lock. The per-cell jittered dwell (uniform
   [15, 25], random phases) exists to break exactly this; do not "simplify" it to
   a global synchronous T.

Mitigations, all three required:

- **Jittered, phase-staggered dwell** (above).
- **Interior band** — over-band is dispreferred, which kills width-1 striping but
  not temporal breathers, hence:
- **The domain-lifetime gate, promoted to a prespecified outcome criterion** (not
  an in-loop objective term — putting it in the objective would hand the
  controller a second measure to Goodhart). Commit before running: if more than
  X% (propose 30%) of settled-interval mass falls in [w, 2w], the run is in the
  breather regime and **fails regardless of its S value**. Report the interval
  distribution and the width distribution in every arm, controls included.

## 5. What would make me say drop it (question 5)

Plainly, in order of when they can fire:

1. **Gate 0 fails** (pre-build, one script): the h≈20 contrast does not replicate
   at width 250 / more seeds, or the peak does not track w when w is varied. Then
   the §8 signal was a small-lattice artifact or my premise is wrong — stop, and
   any resumption needs a new diagnosis, not a new controller.
2. **The structural test fails** (pre-build, computable from the §8 harness data;
   this is the "structurally unable to work" criterion you asked for): compute
   the probability that, for two operators whose true per-operator means differ,
   a T≈20-window local sample ranks them correctly. If that probability is ≈0.5
   at the observed spread (0.16 across the full vocabulary, less between typical
   pairs) against within-operator variance, then **no local decision procedure of
   any kind** — model-based, realized-outcome, or otherwise — can extract the
   signal per decision, and averaging over cells is the only way it can matter,
   which is population selection, not per-cell control. Drop the per-cell claim.
   This single number is the cheapest remaining decisive computation and I would
   run it the same day as Gate 0.
3. **Fails yoked-blind in the built experiment**: the observable is inert in
   closed loop despite the open-loop contrast. Drop.
4. **Beats blind but not the wrong-observable (activity-band) control**: the
   interface framing is decoration on an activity thermostat. Drop the interface
   claim; the thermostat result may survive as a smaller, different finding.
5. **Breather criterion fires and survives one band re-specification**: the
   observable is Goodhart-fragile in exactly the way §4 predicts. Retire it
   rather than iterating bands — a target that needs per-failure re-tuning to
   avoid its cheapest generator has lost the property that made it attractive.

What would NOT make me drop it: arm (b) failing while arm (e) works (that retires
the predictive model, not the approach), or the primary outcome failing on some
seeds (that is a quantitative result, prespecify the success fraction).

## 6. Build order

0. **Gate 0 + structural test** (§0, §5.1–2). One day, no build. Both must pass.
1. Calibration script: band numbers from existing mixed-regime sheets; commit.
2. **Arm (e)** on `selection.clj`'s machinery, with jittered dwell, ε-mutation,
   and the five controls carried over from the review unchanged — yoked blind,
   lagged-observable (τ ∈ {50, 250, 1000}), best-global, **wrong-observable
   (activity-band, equal billing)**, fragmentation (widths *and* lifetimes as the
   §4 gate). Prereg the lot, including seed count and success criterion.
3. **Arm (b)** semi-analytic predictor: derive the h=2 hazard channel; verify
   held-out skill vs base rate (gate); ambiguity exclusion per §3 with ablation
   arm; same seeds and controls as (e).
4. Compare, and report which of the §5 criteria were armed and which fired.

The one-sentence version: the h≈20 result says the leverage lives at the
timescale of *realized* windows, not one-step predictions — so move the interface
preference from the EFE channel to the selection channel, hold operators for a
jittered ~20-step dwell, and let the model-based arm ride along as the
interpretation, not the vehicle.
