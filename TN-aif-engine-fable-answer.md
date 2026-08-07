# Answer: does the engine need upgrading, and do the R-gaps say which?

Fable 5, 2026-08-05. Follow-up to `TN-search-control-fable-answer.md`, responding to the
question posed after the T1/target-sweep runs (`reports-remote/ctl/`, the target sweep) and
the audit of `futon5/src/futon5/exotype/{efe,self_tuning}.clj` against
`futon2/docs/futon-aif-completeness.md` (R13 single-step, R14 absent, R15 absent, R18 absent).

Written in order: §1 reasoning first, before any new measurement ran; results appended in §2
as they land; conclusions in §3. New scripts under `analysis/` only; nothing under `src/` or
`test/` touched; nothing committed.

**Summary of where the reasoning goes, stated up front so the measurements can check it:**
the operator's reduction is half right, and the halves separate cleanly. The λ-immobility and
the authority problem are both consequences of ONE absent mechanism — the hard argmin, i.e.
the graded-selection half of R14 — and neither is a consequence of R13's single-step horizon:
under an argmin, a candidate score affine in λ makes the selected action piecewise-constant
in λ, so the controller's loop gain is zero almost everywhere *at any policy horizon* —
closing R13 on top of a hard argmin provably cannot restore λ's leverage. Conversely the
third dead-end (the field that looks like the edge and measures as chaos) reduces to
*neither* absent R-number: it is the C-vector (the futon2 contract's R19, which the audit did
not list), because the score field's structural argmin is :chaos and no selection-rule or
horizon change alters the argmin of a fixed score field. The engine change that makes search
*possible* is one line at the decision site; whether it makes search *actual* is a
reachable-zero condition I can state exactly and measure in advance.

---

## 1. Reasoning before measurement

### 1.1 What the controller actually drives through — read off the code

`self_tuning/cell-decision` builds, per cell i, 3–4 candidates and computes

    total_c(λ_i) = base_c + λ_i · conatus_c

exactly (verified numerically to machine precision: the cached path stores the λ=0 total and
re-applies λ per cell; the slow path's `score-policy` adds `(* lambda conatus)`). `base_c`
folds in risk, ambiguity, the epistemic term −κ·X_c, and the churn bonus; none of them reads
λ. The winner is `(first (sort-by :total candidates))` — a hard argmin — and the controller's
entire sensor is `hunger_win = (:hunger (:prediction winner))`, a per-candidate constant.

So per cell-step, as a function of the one variable the controller writes:

- The candidate totals are 3–4 **lines in λ**.
- The selected action is the **lower envelope's argmin — piecewise-constant in λ** with at
  most (#candidates − 1) breakpoints in [0,1].
- The sensor reading hunger_win(λ) is therefore **piecewise-constant**: dh/dλ = 0 almost
  everywhere, with jumps only at envelope breakpoints.

And λ has **no other channel into the dynamics whatsoever**: if the argmin at cell i does not
change, cell i's action, exotype write, genotype write, and phenotype write are all
byte-identical — this is not a modelling claim, it is the measured mechanism behind the
random-walk null being *exactly* the frozen arm (0 differing decisions in 16 seeds × 800
steps; `TN-search-control-fable-answer.md` §2.1). **λ acts on the world if and only if it
crosses a breakpoint of the envelope.**

This is the R14 diagnosis made exact. It also settles the R13 half of the operator's reading
*against* it, structurally: a depth-k rollout scored under a hard argmin is a finite min of
sums of functions affine in λ — still piecewise-linear in λ, still a piecewise-constant
winner, still zero gain a.e. **The single-step horizon is not what makes λ inert; the argmin
is.** R13's degeneracy is responsible for a different, equally real blindness (§1.5).

### 1.2 Why "closed-loop hunger independent of λ" — two possible mechanisms, and they dictate opposite upgrades

Piecewise-constant is not constant. The measured λ-independence of realized hunger can come
from two different places, and the census must separate them because they point at different
faults:

- **(a) Breakpoint scarcity**: the envelope's breakpoints rarely fall inside [0,1] on the
  states the closed loop actually visits — λ can sweep its whole range without flipping any
  argmin. Then the block is the *selection rule* (R14's graded-selection half), and a softmax
  restores a genuine, sign-correct gain (§1.3). The operator's reduction is right for
  dead-ends 1 and 2.
- **(b) Sensor degeneracy**: breakpoints exist, but the candidates' predicted hungers are
  near-equal on visited bins, so crossing one changes the action without changing the
  reading. Then softmax buys nothing (its gain is a covariance that vanishes with the hunger
  spread, §1.3), no selection-rule or horizon change helps, and the fault is in the *model
  and preferences* — R18/R19 territory. The operator's reduction would be wrong.

The one-cell probe already run while drafting this note shows candidate hungers can spread
widely (0.060 vs 0.165 in one state), so (b) is not trivially true; but a single cell is not
a census. This is the first thing to measure.

### 1.3 What a softmax with precision γ changes, exactly

Replace the argmin with P(c) ∝ exp(−γ·total_c(λ)). Two consequences, both computable in
closed form on frozen states:

**Gain.** E[hunger](λ) = Σ_c p_c(λ) h_c is smooth, with

    dE[h]/dλ = −γ · Cov_p(h_c, conatus_c).

conatus_c = KL(Bern(h_c) ‖ Bern(0.05)) is strictly increasing in h_c on h_c > 0.05, and the
sign census (previous answer, M2) showed essentially every visited candidate sits above 0.05
after diversification — so Cov ≥ 0, vanishing only under mechanism (b). The controller steps
λ *up* when hunger reads above target; the gain is ≤ 0; **the loop becomes negative feedback
by construction**. Under the hard argmin this quantity is a sum of delta functions at
breakpoints; the softmax spreads it into a usable slope. That is the precise sense in which
the absent R14 mechanism *is* the missing loop gain.

**Fixed point.** With sampled selection the controller reads the sampled winner's hunger, so
the drift of λ_i is s·(2·P(h_win > target | λ_i, state) − 1): an interior attractor exists
where the **median sampled winner-hunger equals the target**. The reachable-zero corollary
of the previous answer transplants verbatim: *search through λ is possible under a softmax
engine iff P(h_win > target | λ) crosses ½ for some λ ∈ (0,1) on the visited state
distribution* — and that crossing, with its location, is computable in advance without
running any closed loop (§1.6).

**Authority.** Under argmin, a term with |κ·ΔX| above the decision gap dictates: influence is
thresholded, and the measured signature was the 100× sensitivity collapse and the 453-vs-0
regime being κ-fragile. Under softmax the term's influence is a *graded* log-odds shift
γ·κ·ΔX competing with γ·Δbase — it biases without silencing, at any κ, provided γ is
moderate. The falsifier the operator set ("if the authority problem survives adding a softmax
with precision γ, my reading is wrong") becomes measurable as: one-bit observation
sensitivity — total-variation distance between the two action distributions — as a function
of (κ, γ). If softmax sensitivity collapses with κ the way the argmin's did, the reading
fails. Note the γ→∞ limit *is* the argmin, so "some γ preserves sensitivity" is trivially
false at the sharp end; the honest claim to test is that there is a γ regime that keeps
selection sharp enough to differ from uniform while keeping sensitivity within a small factor
of its κ=0 value.

### 1.4 What R14 closure means here, honestly

The futon2 contract's R14 is γ *inferred from realized policy outcomes*, not merely a
temperature. The engine lacks both halves; the dead-ends only need the first (a graded
selection rule with *any* finite γ). So "close R14" decomposes: (i) the mechanism — softmax
at the decision site, one line; (ii) the inference — γ updated from outcome history. (i) is
what restores gain and graded authority; (ii) is what would set γ without hand-tuning. The
measurements below test (i); (ii) inherits its value only if (i) works.

### 1.5 What R13's absence actually costs (it is real, just not these two dead-ends)

The indirection is two steps by construction: an exotype chosen at t rewrites the genotype at
t+1 and the phenotype only at t+2 — and the derivation of the conditional model measured the
consequence: **next-step activity is not a function of this step's exotype** ("no gain — and
none is possible", `efe.clj` conditional-model docstring). A single-step G is therefore
structurally blind to the *phenotype* consequences of every policy; the only channels that
differentiate candidates at depth 1 are rule-change rate (σ-structural), the model's
hunger/diversity rows, and the epistemic table. Any notion of "searching over dynamical
regimes" needs the score to see regime consequences, and those live at horizon ≥ 2. So R13
closure is what would give the engine a *sensor* for what the third question cares about —
but on a hard argmin it cannot re-animate λ (§1.1), and adding depth under an unreachable
C-target only sharpens the pursuit of the wrong argmin. Hence the ordering in §3.

### 1.6 The cheaper diagnostic: an actuation census on on-policy states

The λ-response curve failed for a specific reason: it clamped λ *uniformly* and measured the
*field-level* response, while the closed loop runs a heterogeneous λ field in feedback — the
curve had the right sign and the wrong regime because it measured a different plant. The
per-cell controller's plant is narrower and is measurable without that mistake: λ_i couples
to the world **only** through cell i's decision at the current state. So:

**Census: on states sampled from the closed loop itself** (the distribution the controller
actually lives on), compute per cell, in closed form, the decision's exact response to the
one variable the controller writes:

1. flip fraction — does the argmin change anywhere in λ ∈ [0,1]? (breakpoint density on-distribution);
2. actuation range — max−min of hunger_win over λ (what a sign controller could possibly feel);
3. candidate hunger spread — max−min of h_c across candidates (mechanism (a) vs (b), §1.2);
4. under the *candidate engine change* (softmax at γ, or depth-2 rollout), the same census:
   E[h](λ) slope, and P(h_win > target | λ) — whose ½-crossing predicts in advance whether a
   given target admits an interior attractor, and where.

This is cheap (pure function evaluation, no forward simulation), exact (the envelope is 3–4
lines), and evaluated on-distribution, so it cannot repeat the clamped-uniform regime error.
Its honest scope: it is a **screening test, not an equilibrium predictor** — a zero census is
a proof that closing the gap cannot help (λ has no other door), a nonzero census is necessary
but not sufficient (feedback still shifts the state distribution, so the predicted λ* can
move; the closed-loop run in M-B is what checks how far). The general rule it instantiates:
*before closing an R-gap, compute the decision function's counterfactual response under the
upgraded engine on frozen on-policy states; if the response is unchanged, the closure cannot
change the closed loop.*

### 1.7 The third dead-end does not reduce to R13/R14/R15

"Looks like the edge, measures as chaos" is a fact about where the objective's optimum sits,
not about how sharply or how far ahead the engine optimizes it. The score field's structural
argmin is :chaos — pinned by `invariants-test/chaos-is-the-structural-argmin`, reconfirmed
when the model constants were corrected (18/24 → 20/24), and micro-pilot 7's stronger claim
is that no accuracy-based correction displaces it. Both C-targets are aspirational gradients,
not preferences: risk target 0.15 against min rate 0.5; hunger target 0.05 against realized
winner-hunger ≥ ~0.1. A softmax changes occupancy *around* the argmin; a rollout pursues the
same argmin farther ahead; neither moves it. The gap with a claim on this dead-end is the one
the audit did not list: **R19, the C-vector** (present-but-implicit in the futon2 contract,
absent as a criterion in the audit as posed) — plus R18, since "criticality" appears nowhere
in the quantities the engine actually scores; the edge-of-chaos reading of this objective is
exactly the relabeling R18 exists to catch.

### 1.8 Preregistered predictions

- **P-A (argmin census)**: flip fraction well below 1 (most cell-states have no breakpoint in
  [0,1]); mean actuation range of hunger_win ≪ the typical |h − target| error for the swept
  targets; candidate hunger spread clearly nonzero (mechanism (a), not (b)). Mean winner
  hunger on visited states ≈ 0.165 ± a few thousandths — reproducing the measured bifurcation
  point (0.160–0.170) as the census mean, which is what a zero-gain sign controller bifurcates
  around.
- **P-B (softmax census)**: dE[h]/dλ < 0 at every γ tested; magnitude growing with γ until
  selection saturates; a nonempty band of targets whose P(h_win > target | λ) crosses ½
  inside (0,1). The bifurcation targets 0.16/0.17 land inside or near that band.
- **P-C (softmax closed loop — the decisive test)**: at a (γ, target) chosen from the census
  band, λ converges from λ0 = 0.1 and λ0 = 0.9 to a common interior band (final mean-λ range
  across starts ≤ 0.15, both interior), with realized median winner-hunger ≈ target; at a
  target outside the band, boundary pinning as before (negative control). If no (γ, target)
  from the census produces two-sided interior convergence, the R14 reduction is wrong and I
  will say so.
- **P-D (authority under softmax)**: argmin one-bit flip-rate collapses with κ (reproducing
  the prior result on this config); softmax TV-sensitivity at moderate γ declines smoothly
  and retains at least ~half of its κ=0 value at κ = 0.478 where the argmin retains ~1%.
- **P-E (depth-2 under argmin)**: a mean-field depth-2 rollout changes a nonzero fraction of
  decisions (the horizon is not vacuous) but leaves the λ-actuation range ≈ the depth-1 value
  (no gain restoration) — the measured form of "R13 without R14 does not touch dead-ends 1–2."

### 1.9 Addendum received mid-draft: manifold-depth R13 ("the orbit is the rollout")

The operator's reframing arrived before any run started, and it is half right in a way worth
being exact about, because the word "orbit" is load-bearing and the substrate refuses it.

**Measured first (`/tmp` probe, then §2.5): there are no orbits.** The per-step rule map
under a fixed σ is: draw k uniform in 0..7, write ¬b[k] at position A(k). That is a Markov
chain on 256 bytes with 8 equally likely successors, self-loops, and merges — not a
permutation of the rules. Reachable sets from single bytes are 9–256, not 2–11: identity
reaches all 256 (it is an unbiased hypercube walk — every position is always flippable),
builder ~192, collapser ~96, odd53 60–180; only the all-even kinds are genuinely confined
(even4: 9–27 reachable, 16 absorbing bytes). Merges exist (absorbing bytes have in-degree
> 1), so the byte-map is not a bijection and no deterministic cycle through rule space
exists to traverse. The 2–11 numbers are consistent with a deterministic iteration
convention (e.g. a fixed k-schedule) or a sampled first-repeat — but not with the shipped
stochastic map, whose sampled distinct-rules-before-repeat from the same bytes is 1–2.

**But the idea survives, stronger.** What is a static, precomputable property of (σ, b) is
the *chain* — and every functional of it that respects the operator's own nuance ("the cell
does not get to pick; it is carried") is computable exactly by 256-vector dynamic
programming and cacheable: 12 kinds × 256 bytes, one pass at load time, the same cost class
as the X_pair table. Expectation along the traversal, not max over the orbit:

    e_t(σ, b) = E[ per-step quantity at step t | b_0 = b ],   e_0 = q,  e_t = M_σ e_{t-1}

with (M_σ f)(b) = mean_k f(succ(b, k)). The concrete payoff is specific: the engine's risk
channel currently uses the **byte-averaged** rate(σ) — the one channel that could read the
genotype's actual content reads none of it. The chain-DP version scores each candidate σ by
the expected per-step risk along the playout *from the cell's actual byte* (and prices the
blend action from the blended byte, so blend becomes byte-sensitive too). That is a genuine
multi-step G(π), no rollout engine, no horizon simulation — manifold depth, as claimed,
minus the word "orbit".

**What does not change: the ordering argument of §1.1.** Chain-scoring (or any temporal or
manifold refinement) alters `base_c` by a per-candidate *constant*; total_c stays affine in
λ; the argmin winner stays piecewise-constant in λ; the controller's gain stays zero a.e.
So the R13 gap being cheap to close does not move it ahead of R14 for dead-ends 1 and 2 —
that is now a measurement (M-E below), not just the theorem. What chain-scoring plausibly
buys is different and real: within-cell score spread from state the current model cannot
see, i.e. more state-dependence for the authority budget to compete with — measured in M-E.

**The free coordinate.** Orbit *length* does not exist, but its honest replacements do:
reachable-set size and absorbing mass per (σ, b) vary from 9 to 256 and are exactly the
halting structure the earlier answers kept meeting. Logged as a follow-on, not measured
against damage here.

- **P-F (chain census)**: chain-scored risk has strictly positive within-cell spread where
  the one-step risk is degenerate (blend vs hold are *identical* on every channel today —
  same kind, same bin — so their gap is priced by κ·ΔX alone; chain-risk separates them by
  byte). Chain-scoring changes a nonzero fraction of argmins, but leaves the λ flip
  fraction and actuation range essentially at their depth-1 values.

### 1.10 Measurement plan

- **M-A** (`analysis/aif_engine_actuation_census.clj`): run the closed loop exactly as the
  target sweep configured it (width 80, κ = 0.2, blend action on, p = 1.0, hunger-coupled,
  step 0.001), targets {0.05, 0.17, 0.25} × 4 seeds, capture full states at t ∈ {100, 300,
  600}; per cell compute the candidate lines (base, conatus, hunger) and deliver census items
  1–3 plus the softmax census (γ ∈ {1, 4, 16, 64}) and the target-crossing table.
- **M-B** (`analysis/aif_engine_softmax_loop.clj`): script-local copy of the decision layer
  with sampled softmax selection (deterministic per-(seed,t,cell) draws, separate stream
  tag); hunger-coupled λ; (γ, target) from M-A's band, λ0 ∈ {0.1, 0.9}, 4 seeds, 1500 steps;
  plus one out-of-band target as negative control. Everything else identical to the shipped
  engine (verified by the γ→∞ limit reproducing `tuning/cell-decision` winners).
- **M-C** (`analysis/aif_engine_sensitivity.clj`): on M-A's captured states, one-bit
  neighbour phenotype perturbation; per (κ ∈ {0, 0.2, 0.478, 1.0}, selection ∈ {argmin,
  softmax γ ∈ {4, 16, 64}}): mean TV distance between action distributions (argmin = flip
  indicator), rebuilt from the candidate decompositions without re-running dynamics.
- **M-D** (inside M-A's script): mean-field depth-2 rollout (candidate's predicted next
  observation from the derived model, neighbours frozen, argmin at both steps): fraction of
  decisions changed vs depth-1, and the depth-2 λ-actuation range.
- **M-E** (`analysis/aif_engine_chain_census.clj`): the manifold-depth version from §1.9 —
  exact chain-DP per (kind, byte) of discounted expected per-step risk along the playout;
  on captured on-policy states: within-cell spread of chain-risk vs one-step risk, argmin
  change fraction, and the λ flip fraction / actuation range under chain-scored `base_c`.

---

## 2. Results

### 2.1 M-A — the argmin envelope census (`analysis/aif_engine_actuation_census.clj`)

2880 cell-states from the closed loop as the target sweep ran it (targets {0.05, 0.17,
0.25} × 4 sweep seeds × t ∈ {100, 300, 600}).

- **Breakpoint scarcity confirmed (mechanism (a)): flip fraction 0.082.** In 92% of visited
  cell-states, sweeping λ across its entire range flips no argmin — λ is inert there by
  construction, not by tuning. Median decision gap at the actual λ: 0.0126.
- **Actuation range of the sensor: mean 0.0035** (p90 = 0.000; conditional on a flip
  existing, 0.042). Against |h − target| errors of order 0.01–0.12 for the swept targets,
  the sign controller's error can essentially never change sign by anything λ does.
- **The sensor is not degenerate at candidate level**: within-cell candidate hunger spread
  mean 0.037, > 0.01 in half the cells. The block is the selection rule and the tiny
  λ-conditional leverage, not a flat hunger table.
- **The bifurcation point is the census mean.** Winner-hunger over visited states: mean
  0.16563 (sd 0.045; per-target means 0.159/0.167/0.171). The closed-loop target sweep
  bifurcated between 0.160 and 0.170: exactly the behaviour of a rectified ramp around a
  λ-independent realized hunger of ≈ 0.166. The sweep's mystery number is this number.

### 2.2 M-A(4) — the softmax census falsifies half of my own reading

P-B predicted a nonempty band of targets with an interior ½-crossing. **Measured: the band
is empty at every γ ∈ {1, 4, 16, 64} and every target in 0.10–0.25.** E[h](λ) does slope
the right way at every γ (negative, as derived — the covariance argument is correct), but
the total range over λ ∈ [0,1] is **0.0017–0.0050** — and P(h_win > target | λ) moves by
≤ 0.06 across the whole λ range while moving by ~0.95 across the target range. The h
distribution has most of its mass in state-to-state variation (sd 0.045) that λ cannot
touch; between targets 0.16 and 0.17 the pooled P jumps 0.61 → 0.28 (an atom of the
winner-hunger distribution at ≈ 0.166 — the global-row/dense-bin hunger), which is where
any crossing would have to hide, if anywhere.

So: **a softmax restores a genuine, sign-correct loop gain, and the gain is an order of
magnitude too small to regulate with.** The λ-immobility dead-end does not reduce to R14's
absence after all — R14's absence makes the gain *zero*; closing it makes the gain
*~0.005*. The plant, not the selection rule, is the binding constraint: hunger is almost
entirely a function of which observation bin the state occupies, and λ only reweights
candidates within a bin. This is measured confirmation of the operator's option 4, sharpened:
the engine cannot search *through λ against a hunger target* under any selection rule,
because the actuator–sensor pair has a gain an order of magnitude below the noise floor.
(The closed-loop run in §2.4 tests this prediction end-to-end.)

### 2.3 M-D — depth (temporal or manifold) does not restore the controller

Depth-2 mean-field rollout under argmin: changes 28.9% of decisions at the actual λ (the
horizon is far from vacuous — R13 is a real gap), roughly doubles the flip fraction (0.145
vs 0.082) and the actuation range (0.0085 vs 0.0035) — **still 5–10× below the state noise
and the |h − target| scale.** Deeper scoring re-prices candidates by per-candidate
constants; it cannot manufacture λ-leverage the sensor does not carry. P-E confirmed.

### 2.4 The fine screen: the atom band contains no crossing either
(`analysis/aif_engine_sensitivity.clj`, part 1)

640 cells from the target-0.17 closed loop, γ = 16, targets 0.150–0.175 in steps of
0.0025. Pooled P(h_win > target | λ) moves in *plateaus across targets* (0.699 → 0.599 →
0.527 → 0.264 — the winner-hunger distribution is atomic, dominated by a few dense model
bins) and by ≤ 0.004 *across the whole λ range* at any target. Even at the most favourable
target (0.1625–0.1650, P ≈ 0.525) it never touches 0.5. **Per-cell λ-leverage
|P_i(λ=0) − P_i(λ=1)| at the run target: mean 0.0001, p90 0.0000, max 0.0032.**

Refined prediction for the closed-loop run (§2.5), committed before reading its output:
targets 0.14 and 0.20 ramp to the respective boundaries from both starts; target 0.165
(P ≈ 0.525 at every λ) gives drift ≈ s·(2P−1) ≈ +5×10⁻⁵/step from both starts — a slow
upward creep of ≈ +0.05 per 1000 steps regardless of λ0 — indifference, not attraction.

### 2.5 M-B — the closed-loop softmax run (`analysis/aif_engine_softmax_loop.clj`)

Sampled softmax selection (γ = 16) inside the otherwise-unchanged engine (private
genotype/phenotype steps called via vars), hunger-coupled λ, 3 seeds × 1000 steps:

| target | λ0 | λ(400) | λ(800) | λ(1000) | P(h>tgt) last-200 | outcome |
|---:|---:|---:|---:|---:|---:|---|
| 0.140 | 0.10 | 0.209 | 0.349 | 0.399 | 0.568 | slow upward ramp |
| 0.140 | 0.90 | 0.992 | 0.998 | 0.997 | 0.666 | pinned at 1 |
| 0.165 | 0.10 | 0.129 | 0.191 | 0.231 | 0.435 | slow upward creep |
| 0.165 | 0.90 | 0.849 | 0.845 | 0.843 | 0.496 | flat — indifference |
| 0.200 | 0.10 | 0.011 | 0.001 | 0.000 | 0.037 | pinned at 0 |
| 0.200 | 0.90 | 0.641 | 0.531 | 0.524 | 0.483 | decelerating stall |

**No (target, λ0) pair converges to a common interior attractor.** At the atom target
(0.165) the two starts end 0.61 apart, each with near-zero drift where it stands —
indifference, not attraction, exactly as the census predicted. The sharpest row is
0.200/0.90: the *same target* that pins λ = 0.10 starts to the floor stalls the 0.90 start
at λ ≈ 0.52 with P ≈ 0.483 — **path dependence in place of a fixed point**. With a gain
this weak, the field's own state (which the trajectory shaped) sets P, and λ follows the
field instead of steering it. My §2.4 prediction was right in the operative claim (no
two-sided convergence, indifference at the atom) and wrong in one detail (I predicted the
0.20 target would reach the floor from both sides; from above it stalled mid-range — the
in-loop P values differ from the frozen-census ones by up to 0.2, which is the
distribution shift the census's screen-not-predictor caveat named). The verdict the run
was preregistered to deliver: **closing R14 makes the loop's gain real and sign-correct,
and the λ↔hunger channel still cannot regulate — the census's screen held end to end.**

### 2.6 M-C — the authority problem under graded selection
(`analysis/aif_engine_sensitivity.clj`, part 2)

One-bit observation perturbation (left neighbour's phenotype bit), 640 on-policy cells;
action-distribution total variation, κ re-applied analytically:

| κ | argmin flip | TV γ=4 | TV γ=16 | TV γ=64 |
|---:|---:|---:|---:|---:|
| 0.000 | 0.5234 | 0.1099 | 0.2795 | 0.4353 |
| 0.200 | 0.2766 | 0.1172 | 0.2728 | 0.3171 |
| 0.478 | 0.1313 | 0.1191 | 0.1671 | 0.1417 |
| 1.000 | 0.1094 | 0.1030 | 0.1040 | 0.1078 |

- Under the argmin, raising κ from 0 to 0.478 cuts sensitivity 4× (0.52 → 0.13): the
  authority effect on this configuration, reproduced.
- **At γ = 4 the sensitivity is flat in κ** — 0.110 / 0.117 / 0.119 / 0.103 — the term
  biases the distribution without deafening it, at every κ tested including 1.0. At γ = 16
  the decline is smooth and modest to κ = 0.2 (the shipped value) and ~1.7× at 0.478; at
  γ = 64 the argmin pathology re-emerges, as it must (γ → ∞ *is* the argmin).
- So the authority problem is not a property of κ; it is a property of the product γ·κ·ΔX
  against γ·Δbase, and under graded selection it degrades **smoothly** instead of
  catastrophically. **On this half, the operator's reduction is right: "the epistemic
  coefficient has too much authority" is R14's absent mechanism, and a softmax at moderate
  γ dissolves it** — with the honest corollary that γ then has to come from somewhere,
  which is exactly R14-proper (γ inferred from realized outcomes), not a hand-set constant.

---

### 2.7 M-E — manifold-depth R13 measured (`analysis/aif_engine_chain_census.clj`)

The chain-DP table (12 kinds × 256 bytes, H = 12, discount 0.7, exact):

- Chain-risk is strongly byte-dependent within almost every kind (sd over bytes 0.18–0.32
  nats; ranges e.g. chaos 0.034–1.013, collapser 0.035–0.922) — the byte content the
  current score cannot see is large. Validation: :identity's column is exactly constant at
  KL(1.0 ‖ 0.15) = 1.8971 (every draw flips regardless of byte), as it must be.
- On 960 on-policy cells: within-cell risk spread **triples** (0.086 → 0.244); the
  blend-vs-hold risk gap goes from **exactly 0 by construction** (same kind, same bin —
  today the blend action is priced *only* by κ·X, i.e. entirely by the authority term) to
  a mean 0.151 consequence price; **35.9% of argmins change**.
- And the controller numbers do not move: λ flip fraction 0.064 → 0.084, actuation range
  0.0027 → 0.0054. **P-F confirmed: manifold depth is real, cheap, informative — and
  orthogonal to the λ dead-end.**

One warning the table makes concrete: chain-risk spans ~1.9 nats against a median decision
gap of 0.013. Dropped into the *argmin* engine it would out-shout every other term — the
authority failure reproduced in a new place, exactly as the operator's addendum worried.
Under graded selection it is priced instead of sovereign. This is the measured reason the
ordering below puts R14's mechanism before R13's closure, not after.

## 3. Conclusions

### 3.1 Question 1 — the reduction, tested: it splits

**"The epistemic coefficient has too much authority" IS R14's absence — measured.** Under
the argmin, one-bit observation sensitivity collapses 4–5× as κ rises 0 → 0.478; under
softmax at γ = 4 it is flat in κ (0.110/0.117/0.119/0.103 across κ = 0/0.2/0.478/1.0), and
at γ = 16 it degrades smoothly instead of catastrophically. Authority is a property of the
selection rule, not of the coefficient: a hard argmin turns any term larger than the gap
into a dictator; a graded rule turns it into a bias. The operator's falsifier does not
fire on this half.

**"λ's lack of closed-loop leverage is what R13's degeneracy predicts" is wrong — twice,
measured both times.** (i) Depth does not touch it: candidate totals are affine in λ at
any horizon, temporal or manifold, so the argmin winner is piecewise-constant in λ and the
loop gain is zero a.e. regardless of depth — and measured, depth-2 rollout and chain-DP
scoring leave the actuation range at 0.005–0.008 against state noise 0.045. (ii) Nor is it
R14 alone: closing R14 turns the gain from exactly-zero into sign-correct-but-~0.005 —
E[h] moves ≤ 0.005 over λ's entire range, per-cell λ-leverage on P(h > target) is ~10⁻⁴,
no target in 0.10–0.25 (fine grid through the atom band) crosses ½, and the end-to-end
closed loop shows ramps, indifference, and path dependence, never a common attractor. The
λ ↔ hunger pair is a **plant degeneracy**: hunger is almost entirely a function of which
observation bin the state occupies; λ only reweights candidates within one. No selection
rule, horizon, γ, step size, or target fixes that pair.

**Dead-end 3 (looks like the edge, measures as chaos) reduces to neither absent
R-number.** It belongs to the C-vector — the futon2 contract's R19, which the audit as
posed did not list — plus R18: both preference targets are unreachable aspirations (risk
0.15 vs min rate 0.5; hunger 0.05 vs realized ≈ 0.166), so risk and conatus are monotone
gradients, the score field's structural argmin is :chaos (pinned by
`invariants-test/chaos-is-the-structural-argmin`), and "criticality" appears nowhere in
any quantity the engine scores. Selection sharpness and horizon move occupancy around an
argmin; they never move the argmin.

So: three dead-ends do not reduce to two absent R-numbers. They reduce to **one absent
mechanism (R14's graded selection), one degenerate actuator–sensor pairing (λ ↔ hunger,
which no R-gap closure repairs), and one C-vector fault (R19)**.

### 3.2 Question 2 — which gaps, in which order (against the manifold version of R13)

1. **R14, mechanism half (softmax at the decision site) — first.** One line at
   `(first (sort-by :total candidates))`, script-verified here end to end. It is the only
   change that alters a measured dead-end by itself (authority, §2.6), and it is a
   *prerequisite* for every later term: any new score component dropped into an argmin
   engine with less than gap-scale care becomes the next dictator. Nothing else should be
   added to an argmin engine.
2. **R13, closed in the chain form — second.** The operator's manifold framing survives
   contact with the substrate once "orbit" is replaced by "chain": there are no orbits
   (reachable sets are 9–256 with merges; §1.9), but exact per-(σ, byte) playout
   expectations are one 256-vector DP per kind, cacheable like the X_pair table. Measured
   value: triples within-cell risk spread, gives the blend action its first
   consequence-based price (vs exactly 0 today — blend currently lives entirely on the
   authority term), changes 36% of argmins. Measured non-value: does not touch the λ
   dead-end (flip fraction 0.084, range 0.005). It must come after R14 because its own
   scale (up to 1.9 nats vs median gap 0.013) reproduces the authority failure under an
   argmin — the addendum's worry, quantified.
3. **R14, inference half (γ from realized outcomes) — third.** After R13 enlarges the
   score, γ is the arbiter of how much any of it binds; the sensitivity table shows γ = 4
   vs 16 vs 64 spans "deaf to κ" through "argmin pathology", so γ is load-bearing and
   should be earned, not typed in.
4. **R15 — not now.** The λ layer already *is* a proto-second-level (a slow state
   parameterising the fast layer's prior weight), and this note's central measurement is
   that it failed for plant reasons, not for lack of levels. Nesting more model on a
   zero-gain loop multiplies dead layers.
- **R19/C is the gap that owns dead-end 3** and is not in the engine's audit list at all;
  with R18 as the discipline that would have flagged "edge of chaos" as a quantity nothing
  scores. No ordering of R13/R14/R15 substitutes for it.

### 3.3 Question 3 — the cheaper diagnostic, and why this one does not mislead

**The actuation census**: on states sampled from the closed loop itself, compute the
decision layer's exact counterfactual response to the knob under evaluation — the argmin
envelope (candidate totals are 3–4 lines in λ), the softmax mixture E[h](λ) and
P(h_win > target | λ), all closed-form, no forward simulation. Three numbers per proposal:
flip fraction, actuation range of the sensed quantity, candidate spread.

It avoids the response curve's failure by construction: evaluated **on-distribution** (the
states the loop visits, not a clamped-uniform regime) and **per-cell counterfactual** (the
thing the controller actually writes, with the heterogeneous field intact). And its
semantics are honest: it is a **necessary-condition screen, not an equilibrium
predictor** — a null census proves the gap closure cannot help (the knob has no other door
into the dynamics); a non-null census licenses the closed-loop test, nothing more.
Validated both ways in this note: it predicted the bifurcation point as the census mean
(0.16563 vs the measured 0.160–0.170 window), predicted no interior attractor at any
(γ, target), and the end-to-end runs obeyed — while the in-loop P values shifted by up to
0.2 from the frozen-census values, which is precisely the distribution-shift caveat doing
its job. The screening rule generalises: *before closing any R-gap, recompute the census
under the upgraded decision function on frozen on-policy states; if the census does not
move, the closure cannot change the closed loop.* (That rule is how M-D and M-E turned
"would R13 help?" into an afternoon instead of an engine rebuild.)

### 3.4 Question 4 — the answer I was invited to reach, sharpened

Yes, with one level more structure than offered. **We have been asking a single-step
hard-argmin engine to exhibit search, and it structurally cannot — but removing the argmin
and the single step, both measured here, shows the deeper fact: the search variable was
plumbed to a sensor it cannot move.** The reachable-zero corollary generalises to a
two-condition form:

> A sign controller on (actuator a, sensor y, target c) can search iff
> (i) P(y > c | a) crosses ½ somewhere in a's range — a reachable zero — and
> (ii) the crossing exists *under the actual selection rule*, which for a hard argmin it
> almost never does, because y(a) is piecewise-constant with measured flip density ~0.08.
>
> Closing R14 repairs (ii). Nothing repairs (i) for the pair (λ, hunger): its measured
> leverage is 10⁻⁴ against state noise 0.045.

The minimal engine change that makes search **possible** (not guaranteed) is therefore
two-part, and both parts are cheap:

1. **Softmax at the decision site** — one line; restores graded influence and a nonzero
   gain; dissolves the authority failure (§2.6).
2. **Re-pair the controller with a knob–sensor pair that passes the census.** The engine
   already contains one with *measured* leverage: κ against the realized blend/adoption
   mix — the κ sweep found blend win-rate monotone in κ with an interior optimum in
   exactly the quantity the third dead-end cares about (content interruption: 453
   witnesses vs 0). A per-cell κ-controller regulating realized blend share toward a
   setpoint chosen at that interior optimum is the same controller architecture that
   failed for (λ, hunger), attached to a pair whose census is not degenerate. Screen it
   with §3.3 before building; that screen costs an afternoon.

What no engine change buys: an objective that prefers the edge. The C-vector prefers what
it prefers — :chaos is the structural argmin — and search machinery, however complete,
searches *for the objective it is given*. R14+R13 make the engine capable of being
informed; only R19 work makes it want the right thing.

### 3.5 Limitations

Census and screens: 2880 (M-A) / 640 (fine screen, M-C) / 960 (M-E) cell-states from 4
seeds of one configuration (width 80, κ = 0.2, blend action on, p = 1.0); the softmax loop
ran one γ (16), 3 seeds, 1000 steps; depth-2 is a frozen-neighbour mean-field
approximation and skips a depth-2 blend candidate; the chain-risk functional (discounted
expectation, H = 12, discount 0.7) was chosen, not swept — the "which functional" question
the addendum raised is answered here only to the point of "expectation along the
traversal, never max", not calibrated; sensitivity used one perturbation channel (left
neighbour phenotype bit), matching the earlier study's; the κ↔blend-share re-pairing in
§3.4 is a census-backed proposal, not a measured controller; R15's dismissal is
architectural argument, not measurement. The no-orbit finding (§1.9) is about the shipped
stochastic transform; if the operator's 2–11 numbers came from a deterministic iteration
convention worth having, that convention defines a *different* substrate whose chain is a
permutation, and the DP machinery here applies to it unchanged.
