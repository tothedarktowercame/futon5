# TN-apoptosis-proposal — a proposal for review, NOT a result

**Status, 2026-08-04, in two parts** — codex-14 flagged that the original single status line
conflated them, which muddied the evidential chronology:
- **COMPLETED AND REPORTED:** the clock mechanism (§1) and the falsifier result (§2). Run.
- **PROPOSED, NOT RUN:** everything from §4 onward.

**REVIEWED by codex-14, job `invoke-1785861471239-999-79e843c2`. §3 was REFUTED. §4's ranking
was substantially revised. See §7.**

**Reviewer: please read §3 before §4.** §3 is why the first attempt failed and it constrains
what any replacement can be.

---

## 1. The mechanism as built

`futon5.exotype.clock` (new, this session). Each cell carries (rule, τ). τ integrates stress
rather than time:

    tau += 1                  ageing floor: doing nothing still costs
         + activity           local mismatch -- a discordant neighbourhood is work
         - 2 * changed        successful self-maintenance repays more than the floor

A cell that keeps changing in a quiet neighbourhood runs its clock backwards and does not age.
A frozen cell ages at +1. A cell in a discordant neighbourhood that cannot change ages fastest.
At τ > θ the cell undergoes apoptosis: its rule is replaced by one generated from its **own
exotype**, and τ resets.

Invariants fixed before building, and honoured:

- **(a) τ modulates the RATE of variation, never its DIRECTION.** The clock decides *when*;
  the exotype decides *what*. Direction-modulation would be transcription in disguise.
- **(b) Replacement introduces NOVELTY, from the exotype.** Not a neighbour copy — a
  copy-only rule over a non-growing set is exactly the slice-12 void.
- **(c) Requires the N2 seeding fix**, or every cell past threshold fires together.

Measured, `:odd53`, 200 steps, 80 cells: τ mean settles ≈10 against θ=20, apoptosis 0.038 per
cell-step, τ field heterogeneous. The mechanism works as specified.

## 2. The hypothesis it was built to test, and the result

**Hypothesis.** The edge-of-chaos peak in blend is invisible to every local observable
(divergence, hunger, activity are all monotone while damage peaks at 0.5). Criticality is a
counterfactual, and a single run has no counterfactual. But apoptosis is an *endogenous
perturbation source* — an expiring cell is a live single-cell flip — so a field that generates
its own perturbations might sense the response.

**Falsifier, stated in advance:** if local statistics stay monotone in blend under apoptosis,
the perturbations are not being sensed.

**It fired.**

| blend | divergence (baseline) | divergence (N2b) | activity (N2b) | apoptosis rate |
|---|---:|---:|---:|---:|
| 0.00 | 0.5190 | 0.5107 | 0.3405 | 0.0362 |
| 0.25 | 0.4726 | 0.4788 | 0.3192 | 0.0188 |
| 0.50 | 0.4234 | 0.4328 | 0.2885 | 0.0077 |
| 0.75 | 0.3748 | 0.3844 | 0.2563 | 0.0031 |

Everything is monotone, **including the apoptosis rate itself**.

## 3. Why it failed — and this is the part that constrains any successor

The stress integrand is `1 + activity − 2·changed`. Every term is a local, instantaneous,
single-run scalar, and τ is a time-integral of them.

> ~~**A monotone integrand integrates to a monotone accumulator.**~~ **REFUTED.**

**This is false as stated, and codex-14 supplied the counterexample.** Component means that are
each monotone can combine into a non-monotone accumulator when their *slopes* differ. With
`a = 1-b` and `c = 1-b²`, the integrand `1 + a - 2c` becomes `2b² - b`, which has an interior
minimum at b = 0.25. Verified by hand.

The defensible narrower claim, which is what should have been written:

> A monotone functional of a **pointwise- or stochastically-ordered** local process cannot
> create an interior optimum. Monotone *sample means at four parameter settings* do not
> establish that ordering.

So the outcome was **retrospectively unsurprising, but not determined in advance**. My
self-criticism was itself overstated — a sharper-sounding claim than the evidence carried,
which is the same failure mode in the opposite direction.

Three further reasons the strong claim fails, from the review: threshold-crossing rates depend
on the full path distribution rather than mean stress; apoptosis feeds back into subsequent
trajectories, so an ordering measured before the intervention need not survive it; and
rectification or thresholding preserve monotonicity only under pointwise or first-order
stochastic ordering of the input.

**Consequence for what comes next:** a band-pass or rectified transform *could* now be chosen
to manufacture the desired peak. Any such choice needs independent mechanistic motivation and
preregistration, or it is curve-fitting.

What survives of the original self-criticism: the falsifier was caught by preregistration
rather than by design review, and a glance at the previous section's table would have made the
outcome *unsurprising* — though, per the refutation above, not *predictable*.

**The general constraint, corrected:** a statistic that is a mean or time-integral of a local
scalar inherits that scalar's monotonicity **only when the underlying process is pointwise or
stochastically ordered**, which four sample means do not establish. Locating a peak is
therefore not strictly barred to such statistics — but it is not guaranteed by them either,
and §7 gives a better-founded route than picking a transform until one bends.

## 4. Candidate levers, ranked — for the reviewer to challenge

Ordered by (standardness × cheapness), not by novelty.

### (i) FLUCTUATIONS, not means — SUPERSEDED, see §7

In statistical mechanics the order parameter is monotone through a transition while the
**susceptibility — the variance — peaks at it.** We measured only means. Variance of local
observables across cells and across time is computable from data already in hand, needs no new
mechanism, and is the textbook signature.

*Prediction:* variance of divergence (or of τ) peaks near the damage optimum while its mean
stays monotone.
*Falsifier:* variance is also monotone, in which case the local observation genuinely contains
no critical signature and the counterfactual is unavoidable.

**This should have been tried before apoptosis was built.** It is cheaper and more standard.

### (ii) Correlation length

Diverges at criticality; also not a mean of a local scalar. Spatial autocorrelation of the
genotype or phenotype field as a function of lag, fitted for a length scale. Slightly more
machinery than (i), same argument.

### (iii) Apoptosis avalanche size — DEMOTED FURTHER, see §7

Whether one expiry triggers neighbouring expiries, and how far. This is the
self-organised-criticality framing, where avalanche-size *distributions* go critical. Needs no
new mechanism — the clock already emits the events — but it is a distribution over events
rather than a scalar, so it is more work to estimate and easier to fool oneself with.

**This was the author's first instinct and is ranked third deliberately.** Joe's caution is
recorded: reaching for it directly risks a hotfix where a cheaper standard measure exists.

### (iv) Accept the counterfactual

If (i)–(iii) all come back monotone, the honest conclusion is that criticality cannot be
sensed from a single local trajectory, and any navigating system needs an explicit twin — a
cell that maintains a perturbed shadow of its own neighbourhood. That is a real architectural
commitment and should not be reached for before (i)–(iii) are excluded.

## 5. Questions for the reviewer

1. **Is §3's argument right?** Is there a way to build a clock on local scalars whose integral
   is non-monotone that we have missed — a rectified or thresholded integrand, say?
2. **Is (i) the right first move**, or is there a cheaper standard signature still?
3. **Is the stress integrand itself defensible?** `1 + activity − 2·changed` was chosen for
   the Einstein property (history-dependent proper time). The coefficients are unjustified.
4. **Does the successor rule honour invariant (b) in substance**, or is applying the cell's
   own propagator 8 times to its own dying byte closer to mutation-of-the-corpse than to
   generation from a model?
5. **Is there a lever outside this framing entirely?** The author has been inside this problem
   all day and the reviewer has not.

## 6. Code review requested alongside

A large number of changes have landed since the claude-11 era, all in `TN-baldwin-reboot.md`
with measurements attached. Reviewer attention is most wanted on:

- `ca/mix-seed` and `with-mixed-seed`, and the seven call sites (§16). Is the SplitMix64
  finalizer the right fix, and is `self_tuning/random-direction` correctly *excluded*?
- `efe/predict`'s two paths and the switch of the default to `:derived` (§28–29). Is
  `min-bin-samples = 30` defensible, and is the derived resource's provenance adequate?
- `grid/step` carrying `:previous-genotype` (§25) — verified inert, but worth a second eye.
- `futon5.exotype.clock` (this note) — new, unreviewed, and the newest code here.
- The 12-kind vocabulary in `grid/propagators`, deliberately outside `exotype-kinds`.
- `futon5.exotype.invariants-test` — 17 tests including four **pinned deficiencies** that are
  supposed to fail when things improve. Is that pattern sound or is it a trap?

**Known-weak spots the author would flag unprompted:** the `:activity`/`:diversity` channels of
`fixed-model` are still hand-typed and unmeasured; `efe/predict`'s derived path still reads
`:rule-change` from `fixed-model`, which caps the selectable vocabulary at the declared four
and throws an NPE for any other kind; and `TN-metaca-baldwin-micro-pilots.md` has still not
been read, though it is cited twice.

---

## 7. Review outcome — codex-14, 2026-08-04

Job `invoke-1785861471239-999-79e843c2`. The review refuted the central argument and rebuilt
the programme. Recorded in full because it is more useful than the proposal it replaces.

### 7.1 The framing error the author missed

> *"Apoptosis introduced perturbations, but the experiment measured their RATE rather than the
> RESPONSE to them."*

Damage spreading is a response/susceptibility hypothesis. The closest single-run observable is
therefore **event-triggered response**: after each apoptosis, measure the excess number,
spatial reach and duration of changes **relative to matched non-apoptotic cells and times**.

And the matching is not optional — endogenous events are **confounded by construction**, since
apoptosis occurs preferentially in static or stressed cells. Avalanche size is one *summary* of
that response, not the primary observable. This is the point the author had entirely missed:
having built a perturbation source, we then measured how often it fired.

### 7.2 Revised programme, replacing §4's ranking

1. **Joint second-order temporal diagnostics** on native activity/divergence — scaled variance
   **plus integrated autocorrelation time / critical slowing down**.
2. **Event-triggered apoptosis response with matched controls.**
3. Connected spatial correlation and finite-size scaling.
4. Explicit paired twin or weak randomised probe.

Variance-first is "reasonable as a cheap screen but too weak alone": in a nonequilibrium CA,
fluctuation–dissipation does not automatically make the variance of an arbitrary observable a
susceptibility. And **variance of τ specifically is contaminated by its own reset mechanism** —
τ is zeroed on apoptosis, so its spread is dominated by reset timing, not by criticality. That
is a defect in the author's proposed measurement, not merely a ranking preference.

Avalanche-distribution fitting comes *after* event-triggered response: low event counts,
endogenous selection and spurious power laws make it fragile.

### 7.3 Recommended first experiment (adopted)

Reanalyse or rerun **unmodified** trajectories on a finer blend grid around 0.5 and at two or
three widths. Preregister `N·Var(activity)` and the integrated autocorrelation time of activity
after burn-in, with seed-level uncertainty. **Require both** a reproducible interior feature
near the independently measured damage optimum *and* coherent finite-size behaviour.

Nearly as cheap as "variance first" and much harder to fool with heteroskedastic noise or an
engineered clock. The finite-size requirement is the part the author's design lacked.

### 7.4 Two confirmed defects in the clock

**(a) It is a stasis clock, not a stress clock.** `activity` is at most 2/3, so a changed cell
receives stress in [-1, -1/3] and an unchanged cell receives [1, 5/3]. With the zero clamp,
**the sign is set by `changed` alone and activity only modulates speed.** The docstring's
"stress" framing oversells what the arithmetic does. Coefficients need a mechanistic derivation
or a sensitivity sweep before the interpretation is warranted.

**(b) The successor rule is mutation-of-the-corpse.** Eight deterministic applications of the
exotype transform starting from the corpse's own sigil is an exotype-conditioned *orbit of the
corpse*, not eight samples from a generative distribution.

Verified directly, and it is worse than the review claimed:

| check | result |
|---|---|
| distinct results of ONE `apply-exotype`, 500 seeds | **3** — the draw-seed is very nearly inert |
| distinct successors, 500 seeds | **22 of 256** (8.6% coverage) |
| successor ever equals the corpse exactly | **yes** — apoptosis can be a no-op |

**Invariant (b) — "apoptosis must introduce novelty" — is violated in fact.** The fix is a
design decision, not a patch: drawing from the exotype's induced distribution needs a
source-independent start (a random byte the propagator then shapes) rather than a short walk
from the dead rule, and it needs a test establishing source-independent diversity and explicit
non-copying. **Held for Joe**, because it changes the mechanism's semantics and the point of
this pause was to stop hotfixing.

### 7.5 A correction that propagates backwards

The review's last point corrects `TN-baldwin-reboot.md` §36.2, not just this note:

> *"'Single run' does not mean 'contains no information about counterfactual sensitivity'; it
> means it lacks an exact paired counterfactual. Transition statistics and event-conditioned
> responses can still estimate susceptibility."*

§36.2 asserted that **no** single-run local statistic can report criticality. That was too
strong, and it mattered: it is the sentence that made an explicit twin look inevitable and
pushed the author toward exotic mechanisms. The corrected claim is narrower and leaves the
event-conditioned route open — which is exactly the route §7.1 recommends.
