# Review — TN-endogenous-interface-control

**Reviewer: fable (external), 2026-08-08. Status of the reviewed note: DESIGN, not run.**
Files consulted: the TN, `mmca-clj/holes/PREREG-interface-abundance-2026-08-08.edn`,
`futon5/src/futon5/exotype/efe.clj`, `futon5/src/futon5/exotype/selection.clj`,
`futon5/src/futon5/exotype/grid.clj`, and the mmca-clj worktree's
`self_tuning.clj` and `grid.clj` (12-kind vocabulary, softmax policy-precision).

## Verdict up front

The observable is the best-motivated candidate this project has produced, and the
note's catalogue of previous failures is honest. But **do not build the controller as
specified.** Three problems are each individually blocking:

1. **The preference channel is very likely inert at the decision horizon.** Your own
   codebase documents that next-step `:activity` is *structurally* unpredictable from
   this step's exotype (two-step actuation lag, `efe.clj` `conditional-model`
   docstring: "no gain — and none is possible"). The interface bit is *further
   downstream* than activity: exotype → genotype (t+1) → phenotype (t+2) → settled
   bit (t+2..t+16) → interface. A one-step `:interface` channel bolted onto the
   existing one-step predictive model will show approximately zero contrast across
   the twelve operators, and the preference term will then be a constant offset that
   decides nothing. The existing evidence on disk can settle this for the cost of a
   script (§5).
2. **The ambiguity term will fight the preference.** In `score-policy`, `:ambiguity`
   is the summed Bernoulli entropy of *all* predicted channels. Interface is most
   uncertain exactly in the mixed regime and near-certainly 0 in both absorbing
   phases. Adding `:interface` to the prediction therefore adds a term that actively
   rewards the degenerate phases under `:efe-full`. Whether preference-KL beats
   channel-entropy is a magnitude question nobody has computed. Either exclude the
   new channel from ambiguity (and say so as a design decision, with an ablation
   arm), or compute the magnitudes first.
3. **The note does not say what a cell chooses**, and its two descriptions
   contradict each other (§6, item 1). Two implementers would build two different
   experiments; the controls only make sense for one of them.

None of this says the approach is misconceived. It says the decisive tests are
cheaper than the build and should come first.

## 1. The trend preference (the note's question 1)

**A preference over the trend of an observable is formally admissible in EFE, but
only in forms the note has not specified, and the literal version has four defects.
I recommend restating it — not over the trend, and not exactly over a latent, but
over a *transition hazard* the model can be made to track. Details:**

Formally, C is a prior over observation trajectories. Two coherent ways to encode a
trend preference:

- **Generalized coordinates**: make the finite-difference ΔS an observation channel
  in its own right and put a fixed target on it. Coherent and standard.
- **Trajectory (pair) preference**: a fixed prior over the joint (o_t, o_{t+1})
  preferring o_{t+1} > o_t. Also coherent — EFE sums preferences over future
  observation sequences.

Neither fits the codebase's per-channel Bernoulli-KL-against-a-scalar machinery
without extension, and both inherit these defects:

1. **Scale-free.** dS > 0 is as satisfiable at S = 0.001 as at S = 0.15. A cell in a
   near-dead field with a flicker of noise satisfies a trend preference forever
   while contributing nothing. The trend preference has no interior optimum — the
   very property the note (correctly) demands of the level preference.
2. **Nothing to say at stationarity.** The primary outcome is reach *and hold* the
   mixed regime. Once the trajectory is in the band, dS ≈ 0 and a trend preference
   is indifferent between holding and drifting out. Holding must be done by the
   level/band term — so the trend cannot be the preference; at most it is a
   transient shaping term, and the note should say which term does which job.
3. **No gradient where it is needed most.** At the all-frozen configuration — the
   exact initial condition of the primary outcome — one-step predicted ΔS is ~0 for
   every operator (see the lag argument above), so the trend channel offers no
   basis for choice precisely where the controller must act.
4. **If made state-dependent** ("prefer up when below band"), C(o′|o) is a moving
   target: policies are still comparable within a step, but G is no longer a bound
   against a fixed prior, and the "held preference" story of why the regime is
   stable stops being expressible in the objective at all.

**What the latent should be, if you restate.** The trend evidence ("the survivor is
the one whose S stops falling and recovers") is a fact about which *basin* the
trajectory is in; trend is a proxy for regime. The natural latent is a per-locale
phase variable {frozen, interfacial, agitated} whose *transition rates* the
generative model tracks: a settling hazard and an unfreezing (melting) hazard. The
preference is then over **bidirectional flux**: both hazards nonzero in the cell's
neighbourhood. This is stationary, interior, satiable, expressible as a fixed
Bernoulli target in the existing machinery ("P(a settled-bit transition within
horizon h in my locale)" with target in a band around ~1/w), and it gives contrast
at the frozen fixed point, because predicted unfreezing hazard genuinely differs
across operators there even when predicted one-step ΔS does not. It also captures
exactly what "recovery" was in the evidence: nonzero melting flux into a frozen
field. Note the existing `:hunger` channel (static rule AND uniform phenotype) is
already a proto-frozen-phase observable — this is an extension of a design element
you already have, not a new mechanism.

**On the note's question 2** (interior optimum → target distribution vs scalar): in
the Bernoulli-KL form a scalar target p\* ∈ (0,1) *is* an interior preference; a
full target distribution buys little until the channel's support is richer than a
Bernoulli. The real problems are support coarseness and time-window (§6, items 3–4),
not scalar-versus-distribution.

## 2. "Degenerate in neither direction, by construction" (question 2 of the brief)

The argument is airtight only for the two *static* extremes. It covers a
one-parameter family (alternation rate) of a configuration space that is much
bigger, and there is at least one third degenerate family the controller has a
direct incentive to find:

**Threshold-riding breathers.** Cells that stay unchanged for slightly more than
w = 15 steps, flip once, and refreeze, with phases staggered across space. Each cell
spends most of its time settled; the stagger makes neighbouring settled-bits differ;
S is high — transiently near 1 for a spatial period-2 stagger. Yet nothing is
meaningfully active (one flip per ~16 steps), nothing is meaningfully alive, and the
dynamics are a trivial limit cycle, not the mixed regime. Worse: this configuration
**also satisfies the coexistence label** ("never absorbs AND settled fraction in
[0.02, 0.98]"), so neither the observable nor the label excludes it. A controller
optimising S is being *paid* to discover the cheapest S-generator, and flipping every
w+1 steps in a staggered pattern is the cheapest S-generator. The w = 15 cliff is an
exploitable discontinuity, and a controller is exactly the kind of optimizer that
finds cliffs.

The fragmentation check as specified (settled-domain **widths**) does not catch
this: the breather's domains can be spatially wide but temporally thin. **Add the
temporal dual: the distribution of settled-interval lengths (domain lifetimes).**
Genuine coexistence has settled intervals ≫ w; the breather's cluster at ~w. This is
one histogram from data the run already produces.

A smaller gap: a single slowly drifting domain wall in an otherwise frozen field
scores high *local* S for every cell near it and also satisfies the label. Globally
trivial, locally optimal. Domain widths catch the many-walls version; a count of
distinct settled domains over time catches the one-wall version.

## 3. The controls (question 3 of the brief)

**Yes, the experiment can pass blind and stale-observable and still be an artifact,
and one of the two is currently vacuous as specified.**

**The stale control has a bug.** The snapshot is "frozen at the start" — but at
t = 0 no cell can be settled, because settledness requires 15 steps of history. The
frozen snapshot is identically unsettled, so the stale observable is identically
zero: a constant channel, i.e. approximately a second blind control with a bias. As
written it cannot isolate currency from correlation. Fix: freeze the snapshot at
t ≥ some stated burn-in (say t = 500), or better, run **lagged-observable controls**
at τ ∈ {50, 250, 1000} — the decay of performance with lag is the actual currency
measurement, and a graded curve is far more diagnostic than one frozen point.

**The artifact route that passes both.** Interface density within a run is very
nearly the inverse of an activity detector — the note's own number, spatial
correlation −0.969. So a controller preferring high local interface is, at cell
scale, close to a controller preferring low-but-nonzero local activity. Any
*current-field-dependent* modulation of operator choice — "churn more when quiet" —
would beat the blind control (which is field-independent) and beat the stale control
(which is field-independent after t = 0), while the interface framing does no work
at all. The result would then be "a thermostat on local activity stabilises the
lattice," which may even be true and publishable, but it is not the claim the note
is making. Blind and stale cannot distinguish these.

**The missing load-bearing control: wrong-observable.** Same architecture, same
currency, same adoption dynamics, but the preference channel reads a *different*
current local observable — local activity in a band is the obvious one, local
diversity a second. If the activity-band controller matches the interface
controller, the interface story is decoration. Given the −0.969, my prior is that
this control is the one most likely to embarrass the headline claim, which is
exactly why it must be run. I would give it equal billing with blind and stale.

**On the blind control's matching.** "Identical adoption rate" is underspecified:
matched in expectation, per-step, or yoked? If the real controller's adoptions are
temporally clustered (bursts when interface falls), a rate-matched-in-expectation
blind control has the same mean churn but not the same burst structure, and
survival could ride on burstiness alone. The strong version is a **yoked control**:
random operator choices injected at exactly the timesteps and cells where the real
controller acted. Cheap, and strictly more convincing.

**Is your confidence in (1) and (2) misplaced?** Partially. They are necessary, but
the pair only establishes "reading the current field does work" — it does not pin
*which* aspect of the reading does the work (wrong-observable does that), and it
does not detect Goodharting of the preference itself (the fragmentation check does
that, and per §2 it is the *only* control aimed at the failure mode a controller is
most likely to produce). **The fragmentation check deserves equal billing, extended
with domain lifetimes.** So: five controls of equal rank — blind (yoked),
lagged-observable, best-global, wrong-observable, fragmentation (widths + lifetimes).

Also state the primary outcome's sampling scheme: how initial conditions are drawn,
how many seeds, and what "holds the mixed regime" means operationally (presumably
the prereg's coexistence label at 3000 steps — say so). Do not select ICs by
searching for global-collapse cases without reporting the full distribution of
outcomes on both arms over a prespecified IC set.

## 4. The evidence base (question 4 of the brief)

**What the evidence actually supports:** S is a good *run-level, time-averaged
detector* of coexistence. The P3 result — no overlap, 3× separation, 14/14 in the
top 14, on 26-of-35 unseen cells — is genuinely strong for that claim, and the
prereg discipline (committed before generation, thresholds not re-tuned) is real.

**What the controller needs, which the evidence does not touch:**

1. **A different level of aggregation, twice over.** The validated statistic is a
   global spatial fraction averaged over 500 steps. The control signal is a
   ~3-cell instantaneous quantity. The only within-run, cell-scale evidence given
   is the −0.969 spatial anticorrelation with activity — which is evidence *against*
   the cell-scale statistic carrying independent information, and it comes from
   n = 1 run. The cross-cell orthogonality (+0.001) and the within-run
   anticorrelation (−0.969) coexist only because the levels differ; the controller
   lives at the level where the bad number lives.
2. **Controllability.** A detector is a correlate; a target must be movable in the
   preferred direction by the available actions. Nothing measured bears on whether
   any of the twelve operators shifts local interface density, in which direction,
   or on what horizon. This is Goodhart's precondition and it is completely open.
3. **The two design choices "that follow from the evidence" follow from the
   unpreregistered part.** Trend-not-level rests on one temporal observation across
   a handful of runs; band-not-maximum rests on a plausibility argument. Both may
   be right; neither has the standing the sentence implies.

**On the prereg's own stopping rules: two of them fired and the note argues past
both.** P1 failed, and the prereg says `:if-P1-or-P2-fails` → "the post-hoc fit was
overfitting. Say so and stop; do NOT rescue it." P4 failed, and the prereg says →
"S is an activity proxy and adds nothing." The note's glosses ("threshold
miscalibrated rather than separation poor"; "orthogonal, not anti-correlated") are,
on the numbers, reasonable — the 3–4× margin under the P1 violations is real, and
+0.001 is not a proxy signature. But reasonable post-hoc reinterpretation is still
post-hoc reinterpretation, and the entire point of writing those clauses was to
bind it. The honest move is explicit: state that P1's threshold and P4's sign test
were badly designed as written, that their falsification clauses fired, that you
are overriding the prereg's stated interpretation and why, and that the surviving
preregistered result is P3 alone. Then re-earn the rest prospectively — which is
cheap, see below. Footnoting it as "miscalibrated" sets a precedent that will
eventually be used on a failure that mattered.

Also: the entire grid ran on a **single seed** (2026102000). The 14/14 separation
is one draw of the seed variable.

**Cheaper decisive tests, in order — all before any build:**

1. **Action-contrast from data on disk** (a script, no new runs). Derive an
   `:interface` channel — and the settled-transition-hazard channel of §1 — from
   the existing 35 × 3000-step sweep, exactly as `derive_conditional_model.clj`
   derived the current channels. Measure the spread across the twelve operators at
   horizons h = 1, 2, 5, 15, 30. If contrast is ~0 at every reachable horizon, the
   controller cannot work and the design is dead for the cost of an afternoon.
   This is the decisive test.
2. **Magnitude audit** (pure function evaluation, no runs). Compute the proposed
   interface-KL term across the twelve kinds on the observation grid and compare
   its spread to the existing risk + ambiguity spread that makes `:chaos` the
   structural argmin. If the new term is an order of magnitude below the existing
   spread, chaos still always wins and the preference is decorative.
3. **Seed robustness** (a few runs). Recompute S on 3–5 fresh seeds for the
   boundary cells of the grid. Turns the 14/14 from one draw into a distribution.
4. **Micro-actuation** (one run). In a live mixed-regime run, clamp a 5–10 cell
   patch to the interface-preferring policy and check whether local S moves in the
   preferred direction. First closed-loop causal evidence at ~1% of the full
   experiment's cost.

**Adequacy verdict: the evidence is adequate to justify tests 1–4. It is not
adequate to justify building the controller directly.** If test 1 shows contrast
only at h ≥ 15, that is not a detail — it forces a multi-step predictive model,
which is a different and larger build than "add a channel."

## 5. Underspecifications (question 5 of the brief)

Places where two implementers would demonstrably build different things:

1. **What does a cell choose?** The note defines the operating point as "which
   operator a cell applies," then says it is "currently two scalars (β, κ)," then
   proposes each cell "selects its operating point from the twelve-member
   vocabulary." These are three different experiments: (a) each cell adapts its own
   (β_i, κ_i) as a meta-controller over the existing softmax; (b) each cell
   hard-argmins over all twelve operators under a new C; (c) the existing
   hold/adopt-left/adopt-right(/blend) policy set — which is what both `efe.clj`
   and the mmca-clj `self_tuning.clj` actually implement, and which can only
   propagate operators that already exist somewhere on the lattice — with the new
   channel added to C. Option (b) destroys the spatial correlation structure of
   the exotype field that adoption provides; option (a) never touches C at all.
   The best-global control (fixed β, κ) is only a clean comparison for (a); for
   (b)/(c) it confounds objective change with selection-mechanism change (softmax
   sampling vs argmin). Decide, and if (b)/(c), add an arm isolating the mechanism.
2. **Which codebase.** The TN lives in futon5, whose `grid.clj` has four
   propagators; the prereg and the 12-kind vocabulary live in the mmca-clj
   worktree. The two copies of the exotype machinery have diverged. Name the
   worktree the experiment builds in.
3. **Neighbourhood radius of "local interface density."** With radius 1 the cell
   can see at most its own two interface bits; the support is {0, ½, 1} and the
   band target is barely expressible; a cell cannot even compute its neighbour's
   interfacial status without its next-nearest neighbour's settled bit. Radius
   changes the observable qualitatively. The prereg validated the *global*
   density; the local support is nowhere stated.
4. **Time window of the control signal.** The validated S is a 500-step mean; the
   controller reads — what? Instantaneous local density? An EMA with what
   constant? The trend over what window, estimated how? Every choice needs
   per-cell memory the current architecture explicitly does not have ("no cell
   memory beyond the current and immediately previous genotype" — `efe.clj`
   docstring), plus 15-step settled counters and access to neighbours' counters.
   This is an architecture change; the note should own it.
5. **The band.** No numbers, and no stated procedure for choosing them. If chosen
   from the five post-hoc sheets, the target is fit to seen data; say so or derive
   it from the prospective grid's mixed-regime range [0.0984, 0.1617] and commit
   before running.
6. **C's shape.** Fixed target, or state-dependent ("up when below band")? The
   existing architecture has a frozen C; a moving C is a formal change (§1, defect
   4). Also: does `:interface` enter `observation-bin` keys? Does its entropy
   enter `:ambiguity` (§ verdict, item 2)? Do the ablation arms
   (`:efe-risk-only` etc.) get interface-on/off variants?
7. **Blind-control matching** (expectation / per-step / yoked — §3) and **stale
   snapshot timing** (§3, the t = 0 vacuity).
8. **Primary-outcome quantification**: IC distribution, number of seeds, success
   criterion, and what "matched global setting" means (best-of-grid? matched how?).

## 6. What is fine

The three named failures of previous objectives are real and the observable
genuinely escapes each as stated. The circularity analysis (persistence-defined
region, adjacency-defined measure) is correct. The falsification section's
commitment to re-specify before reporting a positive result is the right ordering.
The blind control is well conceived. The instinct that an interior optimum needs a
band is right. None of these need more work than they have had.

## 7. Recommended order

1. Fix the stale control's t = 0 vacuity and the blind control's matching on paper
   (an afternoon of edits).
2. Run cheap tests 1–2 of §4 (action-contrast, magnitude audit) from existing
   data. **These are the gate.** If either fails, the design as conceived is dead
   and you have spent no build.
3. If they pass: restate the preference as a settled-transition-hazard band (§1),
   specify items 1–8 of §5, add wrong-observable and domain-lifetime controls,
   prereg the lot — including the band numbers and seed counts — and then build.

The most likely failure mode of this project is not that the experiment fails; it
is that it succeeds as an activity thermostat wearing interface-density clothing,
passes blind and stale, and ships. The wrong-observable control and the
action-contrast pre-test are the two cheapest insurances against that, and neither
is in the current design.

---

## 8. Result of the cheapest decisive test (added by claude-14, 2026-08-08)

The review's recommended first step — measure across-operator contrast in local
interface before building anything — has been run. It did not need the 35×3000
sweep: those sheets record only the state and rule layers, not per-cell operating
points. It was run instead through the existing conditional-model derivation
harness, whose `:heterogeneous-fixed` arm assigns operating points randomly at
initialisation and holds them fixed, so conditioning on the operator a cell
actually carried is unconfounded and needs no counterfactual re-simulation.

**Method.** 40 seeds, width 60, 220 steps, 14 operators. For each cell, local
interface (settled-bit differing from either neighbour, w=15) measured at horizon
h after the settling window. Null: 200 permutations of the operator labels against
the same values, comparing max-minus-min of the per-operator means.

| horizon | observed spread | null p95 | verdict |
|---|---|---|---|
| h=1 | 0.1125 | 0.1000 | signal, marginal |
| h=2 | 0.1008 | 0.1105 | indistinguishable |
| h=5 | 0.1137 | 0.1127 | signal, by 0.001 |
| h=10 | 0.1060 | 0.1143 | indistinguishable |
| **h=20** | **0.1636** | **0.1245** | **signal, clear** |
| h=30 | 0.1107 | 0.1301 | indistinguishable |

**Reading.** The design is not inert, but the leverage is weak, and the only
horizon with contrast clearly above the null is **h≈20** — not h=1. Six tests at
p95 would give ~0.3 crossings by chance; three crossed, two of them by a hair.
This is consistent with the actuation lag `efe.clj` already documents, and it
falsifies the controller's *horizon* rather than its observable: a preference over
the NEXT-step interface prediction is steering on a quantity the model can barely
distinguish across operators.

The run-level result is unaffected — S still separates the 35-cell grid 14/14 with
no overlap. What is now in doubt is per-cell, per-step controllability.

**Caveats.** 60-cell lattice, 220 steps, `:heterogeneous-fixed` arm — much smaller
than the 250×3000 sheets behind the separation result, and not the arm a controller
would run in. Diagnostic, not decisive.
