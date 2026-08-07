# Answer: the search experiment's two faults are one fault, and G(t) was never measurable

Fable 5, 2026-08-05. Follow-up to `TN-interrupter-fable-answer.md`, responding to the two
questions posed after the `exotype_search` sweep (`scripts/exotype_search.clj`,
`reports-remote/srch/`): (1) is there a λ step size at which the undirected control both
perturbs decisions and avoids saturation, and (2) is convergence-from-both-directions
measurable at all in this system — and if not, what would constitute evidence of "search"?

Written in order: §1 reasoning first, before any new measurement ran; results appended in
§2 as they land; conclusions in §3. New scripts under `analysis/` only; nothing under
`src/` or `test/` touched; nothing committed.

**Summary of where the reasoning goes, stated up front so it can be checked against the
measurements:** the two faults are not independent tuning failures pulling on one
parameter. They are two symptoms of one structural fact — the coupled arm's error signal
`hunger − target` has, in the realized regime, no zero: the hunger target 0.05 is below
essentially every hunger value the winner's prediction can take. A sign controller on a
signed error that never changes sign is an open-loop ramp. A ramp accumulates coherently
(displacement s·T); a zero-mean walk accumulates incoherently (spread s·√T); no shared
step size can make the second a motion-matched control for the first, and the saturation
that was treated as a step-size fault is actually the coupled arm's only fixed point.
Separately, G(t) was unmeasurable for a reason that no tuning of the same design touches:
the plant erases initial conditions on a timescale ~10² steps while the controller moves
on a timescale ~10³ steps, so *every* arm's G(t) converges, including the frozen one, and
convergence measures the plant's mixing, not the controller's search.

---

## 1. Reasoning before measurement

### 1.1 What the coupled arm actually is — read off the update

`self_tuning/next-lambda`, `:hunger-coupled` arm:

    λ ← clip[0,1]( λ + s · sign(hunger_winner − target) ),   target = 0.05

with `hunger_winner` the *predicted* hunger of the selected candidate — a value read out
of the derived conditional model (`resources/futon5/exotype/conditional-model.edn`), a
finite table of 102 rows keyed by (kind, activity-count, diversity-count) with global-row
fallback. This is a bang-bang (sign) controller, not a gradient step. Its behaviour is
completely classified by the sign process of the error:

- If the realized error sign alternates with balanced frequency, λ chatters about an
  interior value — that, and only that, is "search" behaviour for this controller.
- If the error sign is constant, λ moves at *exactly* ±s per step per cell — a
  deterministic maximum-rate ramp — until it hits a clip boundary. The boundary is then
  the unique fixed point. Saturation is not a failure mode of this regime; it is the
  equilibrium.

So the first question to settle is not a tuning question but a sign census: **does the
winner's predicted hunger ever fall below 0.05 in the realized regime?**

### 1.2 The evidence already in hand says the sign is constant (hypothesis H1)

Three numbers already delivered, re-read:

1. The saturation table in `exotype_search.clj` (step 0.01/0.003/0.001/0.0003 →
   mean-λ at t=100/300/800) matches `clip(0.55 + s·t)` **exactly at every entry**:
   0.001 gives 0.649 at t=100 (max possible 0.650), 0.849 at t=300 (max 0.850);
   0.0003 gives 0.580/0.640/0.790 against maxima 0.580/0.640/0.790. The "fraction
   pinned" column is just the ramp crossing the ceiling.
2. In the artifact `reports-remote/srch/ordered-hunger-coupled.edn`, seed 2026085100:
   mean-λ 0.6357 → 0.6657 over t=300→400 and → 0.7257 at t=600 — rises of exactly
   0.0300 and 0.0600 = s·Δt with s = 0.0003. A single negative or zero sign among the
   8000 cell-steps in one interval would show as a deficit of ≥ 2s/80 ≈ 7.5e-6 in the
   mean; the printed values are at full rate to ~1e-13.
3. λ-SD is frozen at 0.00442 from t=200 on: all 80 cells stepping +s in lockstep.

So H1: **in the regime the dynamics actually occupies, sign(hunger_winner − 0.05) = +1
at essentially every cell-step after a short transient.** The reported fact "directed
motion accumulates 0.550 → 0.788" is then not evidence of directedness: 0.55 +
0.0003·800 = 0.79. The arm moved the maximum distance any process with step s can move.
Under H1 the "signal-coupled" arm is an open-loop ramp whose direction was decided at
design time by the choice target = 0.05, and the experiment compared: a frozen λ, an
inert walk, and a ramp.

Why would the error never change sign? The model table's hunger rows have median 0.114;
the target 0.05 was inherited from the EFE preference vector C
(`selection/preference-targets`), where it functions as an *aspiration* — conatus =
KL(hunger ‖ 0.05) is a penalty that can only say "lower". The efe.clj docstring already
diagnoses exactly this pattern for the risk channel: *"Every sigma has rate ≥ 0.5, while
the risk target is 0.15. An unsatisfiable target is not a preference, it is a gradient."*
Re-using an aspirational preference as a *regulation setpoint* imports that gradient into
the λ layer: the controller inherits an error signal with no reachable zero. (There are
model rows with hunger < 0.05, including exact zeros — whether any of them is *visited by
winners* in the realized regime is measurable, §1.6 M2. H1 predicts: effectively never
after the transient.)

### 1.3 Question (1), structural half: coherent vs incoherent integration has no shared step size

Grant, for a moment, the design's own framing (a directed arm that is genuinely directed).
The asymmetry the experiment measured is still structural, and quantifiable:

- Directed displacement after T steps: Δλ_dir ≈ s·T·(2q−1), q = fraction of steps with
  positive sign. Coherent integration: linear in T.
- Undirected spread after T steps: SD(Δλ_walk) ≤ s·√T. Incoherent integration: √T.

For the walk to be a *control on motion* it must actually move decisions: per-cell
displacement must reach the decision-flip threshold δ — the smallest |Δλ| that changes
some cell's argmin (λ enters every candidate's total as +λ·conatus(candidate), so a λ
shift of Δ re-orders candidates c, w exactly when Δ crosses
(total_c − total_w)/(conatus_w − conatus_c)). So the walk is non-inert over horizon T
only if s·√T ≳ δ. For the directed arm to stay interior over the same T (the saturation
guard) needs s·T·(2q−1) < 0.45. With q ≈ 1 the two conditions bracket s:

    δ/√T  ≲  s  <  0.45/T      — nonempty  iff  δ < 0.45/√T.

At T = 800: **the window exists only if δ < 0.0159.** δ is measurable (M3 below): it is
set by the score-gap-to-conatus-spread ratio, and everything known about the score
landscape (gaps 10⁻²–10⁻¹ nats, conatus spreads ≲ 1) predicts δ well above 0.0159 —
the reported facts already imply an empirical bound in this direction, since the walk's
byte-identical damage at SD 0.0043 means no decision flipped anywhere in 16 seeds × 800
steps × 80 cells of walk displacement. If measured δ ≫ 0.0159, the answer to question
(1) is **no: there is no single step size**, not as tuning bad luck but because a linear
integrator and a √T integrator cannot be matched on their shared increment over any
horizon long enough for the directed arm to need a saturation guard.

And under H1 the window is doubly illusory: q = 1 means the directed arm reaches the
ceiling at t = 0.45/s for *every* s. Step 0.0003 did not avoid saturation; it postponed
it past the 800-step horizon (ceiling at t = 1500). The observed "only 0.0003 stays
interior" is a horizon artifact, not a regime.

### 1.4 Question (1), constructive half: the null must be matched on the actuation, not the increment

The one control in this project that ever worked — the adoption-rate-matched constant
bonus — was matched on an *outcome* (realized adoption rate), not an input (bonus size).
The same move is available here, and the outcome to match is sharper than "some rate":
**the actuation trajectory itself, the λ path.** What the coupled arm does to the
dynamics is entirely mediated by the λ field it writes; a null that reproduces the λ
field while severing the state-coupling isolates exactly the closed-loop content. Three
nulls in increasing strength:

- **N1, ramp control (parameter-free under H1):** λ(t) = clip(λ₀ + s·t), applied
  open-loop, uniformly. If H1 holds this matches the coupled arm's λ path to within the
  frozen SD 0.004, with zero tuning. If coupled ≈ N1 on every downstream statistic, the
  directed arm's entire effect is "λ was ramped", and the three-arm design collapses.
- **N2, yoked replay:** record the coupled arm's per-cell λ traces on seed set S, replay
  them open-loop on disjoint seed set S′ (schedule, not feedback). Matches drift,
  dispersion, autocorrelation, per-cell heterogeneity — everything about the motion —
  while breaking the within-run loop. Coupled-vs-yoked is the *definition* of the
  closed-loop contribution. This is the general-purpose instrument; N1 is its special
  case when the signal is a constant.
- **N3, permuted replay:** same-run λ traces, cell-permuted (or time-blocked): retains
  even the marginal per-time distribution, kills spatial alignment of λ with state.

The step-matched random walk answers a different and less interesting question ("does
zero-mean λ jitter of increment s do anything?" — answer: at any s that leaves the
coupled arm interior over T, provably nothing, by §1.3). Matching on the λ path answers
the question actually asked: *is the coupled arm doing anything the schedule it happens
to emit would not do on its own?* Under H1 the preregistered prediction is: coupled ≈ N1
≈ N2 within seed noise, i.e. the answer is no.

### 1.5 Question (2): G(t) convergence is structurally unmeasurable here — two independent reasons, neither tunable

**(a) The plant forgets faster than the controller moves.** Damage(t) is a functional of
the state at time t only. The rule layer churns at p·rate(σ) ≥ 0.5 flips/cell·step
(apply-probability 1.0, every σ has rate ≥ 0.5); with the blend action winning ~20–30%
of cells per step, rule content is additionally being laterally rewritten. The uniform
initial rule field (the *only* thing distinguishing "ordered" from "chaotic" starts) is
therefore destroyed on a timescale of tens of steps — measured: 68–85 distinct rules by
t ≈ 100. Past that mixing time τ_mix, the state distribution is start-independent for
*every* arm, so G(t) → seed noise for every arm **including frozen λ**. P2's premise
("controls do not converge, or converge materially less") is false by construction:
convergence of G is a property of the dynamics' mixing, and the design assigned it to
the controller. The controller meanwhile moves λ at s = 0.0003/step: to traverse the
interesting range (~0.5) takes ~1500 steps. **τ_mix ≈ 10² ≪ τ_search ≈ 10³: the plant
erases the initial-condition memory ~10× faster than the controller can respond to it.**
No arm can "converge G" because G converges by itself first; no arm can fail to.

This order cannot be repaired inside this system, because the same machinery that makes
λ worth adapting (the per-step σ-transform churn and blend traffic in the rule layer) is
the machinery that erases the starts. Slowing the churn to preserve IC memory (lower
apply-probability) proportionally weakens what λ controls; speeding the controller
(bigger s) is the saturation wall of §1.3. The two timescales are not independently
tunable in the needed direction.

**(b) The instrument integrates across the transient.** The damage probe advances forks
H = 40 steps under the *full* dynamics — σ-transforms, blend actions, decisions all
running. Its reading is therefore an average of BOT sensitivity over a 40-step window of
a field that is itself diversifying on a ~100-step timescale. At t = 0 the probe reads
"damage of the uniform field's first 40 steps of decay", not "damage of rule 204" —
hence G(0) = 7.1 against a configured separation of ~64. And H cannot be shrunk to fix
this: a damage cone needs ~width/2 steps to express a reach of ~65 on width 80 (speed
≤ 1 cell/step/side), so any H that can *measure* the chaotic end of the scale spans the
ordered start's decay window. H is pinned from below by the quantity's range and from
above by the transient. At width 80 the interval is empty.

**General principle, worth keeping:** convergence-of-G-from-both-directions is evidence
of search only when the plant's initial-condition memory outlives the controller's
search time (τ_mix ≫ τ_search) and the probe is fast relative to both. Here the
inequality is reversed by an order of magnitude, and the probe is slower than the
memory. The test is not badly tuned; it is the wrong object for any system whose state
mixes faster than its parameters adapt — which is exactly the regime this system is in,
by design (fast substrate, slow modulation).

### 1.6 What WOULD constitute evidence of "search"

"A system that finds a regime" differs from "a system that lands in it" in the
*controller's* behaviour, not the state's. The state forgets its start here by (a); so
stop varying the state's initial condition and vary — and perturb — the controller's.
Four tests, in increasing strength, each immune to the mixing confound because each
conditions on the plant already being mixed:

- **T1, convergence in λ:** start the coupled arm at λ₀ ∈ {0.1, 0.55, 0.9} (state
  starts random, irrelevant by (a)). Search predicts a common interior attractor λ*:
  downward motion from 0.9, upward from 0.1, damage statistics converging across λ₀
  arms. A ramp (H1) predicts monotone rise from every start and pinning at 1.0 from
  0.9. This is the two-sided-convergence design *transplanted from the state variable
  (where memory is erased) to the controller variable (where memory is the whole
  point)* — λ is not churned by the substrate; it is the one variable in the system
  whose initial condition survives.
- **T2, setpoint restoration:** run to whatever λ-equilibrium exists, then externally
  kick the λ field (±0.2) mid-run. Search/regulation predicts return toward pre-kick
  level; a ramp predicts indifference (continue rising at s from wherever put); a walk
  predicts diffusion from wherever put. Restoration after perturbation *at equilibrium*
  is the operational meaning of "finds", and it needs no reference to initial
  conditions at all.
- **T3, target validity:** whatever λ* T1/T2 finds must coincide (within its chatter
  width) with the independently swept optimum of the criticality proxy — the analogue
  of the interior optimum found in the κ sweep. T1/T2 without T3 shows only that the
  controller has an attractor; T3 is what makes the attractor "the critical regime"
  rather than an arbitrary set point.
- **T4, closed-loop content:** coupled vs yoked replay (N2) at matched λ path, on every
  downstream statistic. This is the only test in the family that can distinguish "the
  loop is doing work" from "a good open-loop schedule".

Under H1, T1 and T2 fail *before being run*: a sign controller whose error has no
reachable zero has no interior attractor to find or restore. So the honest sequencing
is: measure the sign census and the response curve first (M2), and if H1 holds, the
conclusion of this note is an impossibility result of the second kind — **this system,
as wired, does not search at any step size, because its error signal is rectified; the
step-size question dissolves.** The constructive corollary is equally sharp: the
minimal change that makes search *possible* (not guaranteed) is a signed error with a
reachable zero — either a hunger target inside the support of realized winner-hunger
(e.g. the realized median, not the aspirational 0.05), or an error defined on a
quantity λ actually regulates with a sign change. Then T1–T4 are the preregistrable
tests, and the λ-response curve h̄(λ) (mean realized winner-hunger under λ clamped at v,
swept over v) tells in advance whether a zero exists: search is possible iff
h̄(λ) − target crosses 0 in (0,1), and the crossing point is the predicted λ*.

### 1.7 Measurement plan

- **M1** (`analysis/srch_artifact_analysis.clj`): from `reports-remote/srch/` — G(t)
  per arm with per-seed paired SE; the frozen arm's own G(t) collapse timescale (the
  (a) confound measured); frozen-vs-walk per-seed damage identity check; coupled-arm
  mean-λ residual against the exact ramp clip(0.55 + s·t) per checkpoint (H1's
  trajectory-level test); walk λ-SD against the √t law (is the walk even diffusing at
  its nominal rate?).
- **M2** (`analysis/srch_sign_census.clj`): re-run 4 seeds × 3 starts, 300 steps,
  logging sign(hunger_winner − 0.05) per cell-step (H1's direct test: fraction
  negative, overall and after t=100); plus the static table census — which of the 102
  model rows have hunger < 0.05, and are those (kind, observation) bins ever occupied
  by winners.
- **M3** (`analysis/srch_flip_threshold.clj`): δ distribution — per cell, the smallest
  |Δλ| that changes the argmin, over sampled states from realized runs (all four
  candidates' (total, conatus) re-scored under shifted λ). Quantiles against the §1.3
  window bound 0.45/√800 = 0.0159, and against the walk's realized displacement.
- **M4** (`analysis/srch_lambda_response.clj`): the response curve h̄(λ): λ clamped at
  v ∈ {0.0, 0.2, 0.4, 0.55, 0.7, 0.85, 1.0} open-loop (script-side clamp; no src
  change), 4 seeds, mean winner-hunger over the last 100 of 300 steps. Does
  h̄(λ) − 0.05 have a zero anywhere? This decides "search impossible as wired" at the
  landscape level, independent of step size.
- **M5** (`analysis/srch_lambda0_probe.clj`): mini-T1 — coupled arm from λ₀ ∈ {0.2,
  0.9}, 4 seeds, 400 steps: does λ descend from 0.9 (search) or rise to the ceiling
  (ramp)? Under H1 this is the one-plot refutation of "the coupled arm searches".

Predictions, committed before running: M1 ramp residual < 0.005 at every checkpoint
t ≥ 200, frozen-arm G(800) within noise of the other arms', frozen ≡ walk exactly;
M2 negative-sign fraction < 1% after t = 100; M3 δ median ≫ 0.0159; M4 h̄(λ) > 0.05
for all λ, no zero; M5 λ rises from 0.9.

---

## 2. Results

### 2.1 M1 — the delivered artifacts, re-read (`analysis/srch_artifact_analysis.clj`)

**G(t) was noise from the first checkpoint, under every arm — including frozen.**
Paired per-seed G(t) (16 shared seeds, ordered − chaotic):

| t | frozen G(t) ± SE | coupled G(t) ± SE |
|---:|---|---|
| 0 | 7.06 ± 2.96 | 7.06 ± 2.96 |
| 25 | 1.13 ± 3.65 | 0.94 ± 3.65 |
| 100 | 1.06 ± 2.09 | 1.88 ± 1.97 |
| 300 | 3.50 ± 3.80 | 4.75 ± 2.86 |
| 800 | 3.06 ± 2.59 | 0.31 ± 5.07 |

The primary statistic was only 2.4 SE from zero **at t = 0** (the instrument
compression of §1.5b: configured separation ~64, read as 20.9 vs 27.9), and is
indistinguishable from zero at every later checkpoint in every arm. The frozen arm
"converged" G completely by t = 25 without moving anything: τ_mix < 25 steps, against
τ_search ≈ 1500. There was never a separation for any arm to close, and nothing for the
falsifier ("all three arms indistinguishable") to bite on that distinguishes arms — the
prereg's P1/P2 comparisons are comparisons of noise with noise.

**The walk arm is exactly the frozen arm.** All 144 seed × checkpoint damage values
identical per start, and *every* non-λ checkpoint metric (blend rate, adoption, kinds,
halting share) identical too: zero decisions differed anywhere in 3 × 16 runs × 800
steps. Confirmed at the strongest possible level: one moving arm, two copies of the
same frozen arm.

**The coupled arm is the open-loop ramp, measured.** Mean-λ residual against
clip(0.55 + 0.0003·t): mean −0.0016/−0.0006/−0.0005 (ordered/chaotic/random) at t=800,
max per-seed |residual| 0.0043 — against 0.24 of total motion, the ramp explains
> 99.3%, and the entire residual accrues before t ≈ 100. The implied negative-sign
budget per checkpoint interval (rise deficit / 2s): 2–8% in t ∈ [0,25), ≤ 0.3% in
[50,100), ≤ 0.02% from t = 200 on — **zero to four decimal places in most intervals
after t = 300**. λ-SD frozen from t ≈ 200 (all cells stepping +s in lockstep).

### 2.2 M1b — the walk was not even a nominal walk (`analysis/srch_walk_diffusion.clj`)

The artifact λ-SD is *sub*-diffusive: ratio to s·√t falls from 0.87 (t=25) to 0.50
(t=800). Replicating `self_tuning/random-direction` exactly (same seed arithmetic,
`java.util.Random` first draw): per-cell 800-step displacement SD is **13.8·s against
the iid value 28.3·s** (variance 4× low), with lag-1 temporal autocorrelation
**−0.187** (4 seeds, all agreeing to 3 decimals). The direction draws at consecutive
times (seed stride 1000003) are anti-correlated — the rng-audit note on that function
("stride 9176 between cells, measured independent") covered the *cell* stride and not
the *time* stride. Not load-bearing here (the walk was inert by three orders of
magnitude, not a factor of 2 — §2.4), but it means even a corrected design would have
had half the nominal diffusion budget.

### 2.3 M2 — the sign census: the error signal has no reachable zero (`analysis/srch_sign_census.clj`)

Static (the model table, `conditional-model.edn`): the coupling target 0.05 sits below
the trusted-bin median hunger 0.129 and the global fallback row 0.134. 17 of 85 trusted
bins do predict hunger < 0.05 — **every one of them at genotype-diversity ≤ 2/3**
(locally uniform or two-kind rule patches; all nine hunger = 0.000 bins are diversity-2
or [:identity · ·] bins). The error's zero exists in the table, but only at
observations describing an *undiversified* field.

Dynamic (hunger-coupled, 4 seeds × 3 starts × 300 steps, 96 000 cell-steps each):
winner-hunger < target at **1.08% / 0.38% / 0.55%** of cell-steps (ordered / chaotic /
random) — and the time structure is the point:

| window | ordered | chaotic | random |
|---|---:|---:|---:|
| t < 25 | 703 | 319 | 299 |
| 25 ≤ t < 100 | 323 | 44 | 198 |
| **t ≥ 100** | **8** | **0** | **34** |

After the diversification transient the negative-sign rate is ≤ 0.05% (42 of 192 000).
The negative signs come from winners in diversity-2 bins (:even1, :even8, :collapser,
:fix2) — patches of local rule uniformity that exist only while the initial uniform
field is decaying. **The rectification is dynamical, not tabular: the regime the
dynamics occupies keeps every winner in diversity-3 bins, where all hunger rows exceed
the target.** Once diversified, sign(hunger − 0.05) = +1 always, and the controller is
an open-loop ramp from then on. H1 confirmed directly.
