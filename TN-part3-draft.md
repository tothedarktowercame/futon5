# Part III — draft

**Status: DRAFT, 2026-08-04.** Prose-level, not typeset. Every claim carries its measurement
and its limits; nothing here is asserted beyond what was run. Slots after
`A Causal Measure` in `holes/tech-notes/paper/draft7.tex`.

The arc: **what the extra layers buy → what a search finds → whether the system can find it
itself → the term it would need.** The last is where the paper currently ends, honestly.

---

## §III.1 What the extra layers buy

*Extends the existing section "A dead genotype can carry a live phenotype", which shows the
possibility. This shows the magnitude, and that the direction of travel is toward the edge.*

**Claim.** Letting the rule layer evolve moves a field from dynamically dead to the class-IV
band, and the effect is large.

**Measure.** Damage reach: differing state cells 100 steps after a single-cell flip, calibrated
in the same harness against elementary rules whose regime is not in dispute — 204 (frozen) 1.0,
90 → 8.0, 54 → 32.8, 110 → 37.1, 30 (chaotic) → 65.1. Higher is not better; the target is the
class-IV band, and rule 30 scores highest.

**Result** (24 seeds, identical initial conditions, the only difference being whether the rule
layer evolves):

| | damage reach |
|---|---:|
| three-layer system | **26.2** |
| the same rules, held fixed | **2.8** |

**And from provably dead starts** (16 seeds): fields initialised with every cell at rule 0 or
rule 255 — which annihilate a perturbation immediately, reach exactly **0.0** — are carried to
**17.5** and **19.8**. Rule 204, the frozen anchor at 1.0, is carried to **16.1**.

**The mechanism is escape, not resuscitation.** By t = 100 a uniform start holds 68–85 distinct
rules and fewer than 2 cells of 201 still carry the initial rule. The system does not sustain a
dead rule; it destroys it and diversifies past it. The claim belongs to the *basin*, not the
rule.

**Limits.** One configuration, width and horizon. Means over 16–24 seeds; dispersion reported
for the main cell (±9.1) and not for the revival arms. Revived fields reach 16–20 against a
random start's 25.5 — consistently below, and unexplained.

**A methodological note the paper should carry.** Phenotype activity — the fraction of cells
changing per step — ranks these configurations *backwards*. The fixed-rule field is five times
as active at t = 2000 (0.323 vs 0.065) and nine times less sensitive. High activity without
propagation is noise; low activity with propagation is localised structure. Any liveness claim
resting on activity in this substrate should be re-checked against damage.

## §III.2 What a search over the construction finds

**Claim.** Three ingredients matter, in this order: the phenotype-reading feedback loop, the
exclusion of propagators that can halt, and the coupling strength — and the third interacts
with the first two.

**Result** (3 arms × 6 vocabularies × 4 coupling levels × 24 seeds, scored by damage reach):
the best cell is **26.2 ± 9.1**. Every cell in the top twelve closes the phenotype → policy
loop; the best cell that does not, anywhere in the search, is **5.7**. Excluding propagators
with absorbing rules is worth a further 3–9. The coupling optimum is **not constant** — it moves
with vocabulary and arm, so an optimum read off one configuration does not transfer.

**What makes the vocabulary search possible** is that the policy layer has closed-form
coordinates. For a permutation propagator, `rate = 0.5 + fix(σ)/16` exactly, so the change rate
is a function of the fixed-point count alone and takes eight achievable values. Absorbing rules
exist iff every cycle of σ is even, which forces `fix(σ) = 0` — so **absorbing rules occur only
at rate exactly 0.5000**. The two coordinates are nested, not crossed: within $S_8$, "churns
fast" and "can halt" are mutually exclusive.

**A consequence the generative model cannot represent.** Cycle types (2,2,2,2) and (5,3) have
identical rate 0.5000 and 16 versus 0 absorbing rules. Over 400 seeds (5,3) never halted while
the absorbing kinds halted completely. Two propagators, identical on the only coordinate the
model carries, opposite in behaviour.

**A law we withdrew.** Half-life appeared proportional to the reciprocal of absorbing count
(t½ × absorbing = 80 across four kinds). Adding cycle type (4,4) — same fix, same rate, same
absorbing count as (6,2), different cycle structure — gives 15 against 20, and the product 60
against 80. The apparent law held only because absorbing count and maximum cycle length were
perfectly rank-correlated in the original four. **Absorbing count is not the sole driver.**

**Limits.** ±9.1 on the best cell against a 2.4 gap to second: ranks 1–4 are *not* separated by
these data. The separated object is the block — feedback loop × no-absorbing vocabulary — and
that is the claim.

## §III.3 Can the construction navigate there itself?

**Claim.** Not as specified, and the reason is structural rather than a matter of tuning.

**The objective** scores a candidate policy as `risk + ambiguity + λ·preference`, where risk is
the divergence of the predicted rule-change rate from a target and ambiguity is the summed
entropy of the predicted next observation. Three findings, each measured:

1. **The rate target is unsatisfiable.** Every σ has rate ≥ 0.5; the target is 0.15. An
   unsatisfiable target is not a preference but a gradient: risk becomes a monotone penalty on
   `fix(σ)`, and `fix(σ) = 0` is exactly the condition for absorbing rules to exist. **The
   objective's only targeted term is an instruction to choose a policy that can halt.**
2. **Removing that bias does not fix it.** Scaling the predicted rate by an application
   probability makes the target reachable; the share of decisions won by a halting-capable
   policy then falls from 0.56 to 0.00. But the winner moves to the *maximum-churn* policy.
   Both ends of a rate preference are wrong, and no scalar target sits between them.
3. **What decides, once the funnel is removed, is `ambiguity`** — and `ambiguity` as
   implemented is the summed *marginal* predictive entropy. In expected free energy the
   ambiguity term is the expected entropy of the likelihood, and the dark-room outcome is
   barred by the *epistemic* component, the expected information gain. This construction has no
   latent layer and therefore no information-gain term anywhere in the score. Minimising summed
   marginal entropy **rewards determinism**: it is the opposite behavioural sign.

So the construction converges on a halting monoculture not because it is malfunctioning, but
because it is minimising the objective it was given, and that objective is missing its
epistemic half.

**Limits.** The vocabulary-widening, target and weight results are measured on the argmin over
reachable observations (a pure-function analysis) and, for the halting outcome, on trajectories.
The identification of `ambiguity` as the residual obstruction is an inference from three
interventions each of which removed one obstruction and revealed it, not a direct manipulation.

## §III.4 The term it would need

**Claim.** A local, cheap, policy-specific epistemic quantity exists, and its scope is
measurable and bounded.

**What failed first.** The quantity already in the construction — a Beta-posterior uncertainty
over local confirmation rates — does **not** track damage: r = +0.008 and +0.013 on disjoint
720-cell samples, per-seed sign unstable, tercile response non-monotone. It is retired. And its
recorded pathology (maximal on zero evidence) is *not* what fails: zero evidence cannot arise,
because candidates are drawn from the pooling neighbourhood and therefore always have a holder.

**What works, with its scope stated.** Because twin trajectories share the per-cell draw, the
stochastic transform is *common noise*, and one-step divergence under a candidate policy is
exactly computable:

> given draw *k* on rule *b*, own σ (positional map A) and candidate σ′ (map B), the rules
> diverge iff `A(k) ≠ B(k)` **and** (`b[A(k)] = b[k]` or `b[B(k)] = b[k]`).

Verified exhaustively against the implementation. The quantity is the resulting divergence
probability — O(8) to evaluate, zero for *hold* by construction, and reducible to a 12 × 12
constant table over policy pairs.

**Every check:**

| check | result |
|---|---|
| within-cell spread across candidates | **0.299**, against the existing rate term's **0.123** — 2.4× |
| redundancy against `rate(σ)` | r = +0.125 / +0.132 — not a restatement |
| control (unrelated perturbation) | −0.01 / −0.04 — clean |
| correlation with divergence at the step it models | **+0.469**, against a calibration ceiling of ≈0.47 |
| correlation with damage at t = 60 | +0.02 … +0.09 among genuinely differing candidates |

**The honest reading.** The quantity is near-optimal at the step it models and **does not
predict long-horizon damage magnitude** — by t = 20, 94% of perturbed cells have diverged, and
chaos has erased the initial difference in magnitude. It should be interpreted as *the expected
rate of injecting new dynamics*, valid at an effective horizon of roughly 5–20 steps, and it
should **not** be justified by long-horizon damage prediction.

**The spread result is the one that matters** for whether it can be used at all: the previous
candidate correlated acceptably and had a within-cell spread of exactly zero, which made it
incapable of changing any decision regardless. That failure mode is affirmatively absent here.

**Not yet done.** The term is not wired into the objective. Doing so requires a scaling rule
fixed before any dynamics is observed — the honest comparison is *within-cell candidate spread*
against the term it must counterbalance — a preregistered prediction, and an
epistemic-off control at every cell of the design. None of that has been run.

---

## §III.5 Can the construction search for the regime itself?

*The first experiment aimed at the paper's actual question rather than at a component. It
returns a null, and the null is about the instrument rather than the system — which is worth
recording in full, because the instrument is the contribution here.*

### The question, and why it needed apparatus

§III.1–III.2 show the construction can **exhibit** class-IV dynamics when configured, and §III.3
shows the objective as specified cannot navigate to that regime. Neither addresses whether the
construction can **find** the regime itself. Every parameter above — coupling strength, rate
target, epistemic coefficient — was set by hand, across runs. The system was never asked to
search.

### Design

Two commitments, both chosen to avoid the obvious ways of fooling oneself.

**Criticality is measured *within* a run.** At checkpoints t, the state is forked, one phenotype
bit at the ring centre is flipped, fork and unperturbed copy are advanced H steps, differing
cells are counted, and both forks are discarded. The main trajectory continues from the unforked
state, so the measurement does not perturb what it measures. This yields `damage(t)`, a time
series of criticality inside a single run, rather than one number at the end.

**Convergence is tested from both directions.** Runs start deep in the ordered regime (every
cell rule 204, which held fixed gives damage 1.0) and deep in the chaotic regime (rule 30, which
gives 65.1). One-sided improvement would prove nothing — it could be relaxation in whichever
direction the initial condition points. The primary statistic is deliberately assumption-free:

    G(t) = | mean damage(t | ordered start) − mean damage(t | chaotic start) |

No target damage value is asserted. **Convergence is the claim**, because a target found from
both sides is the difference between exhibiting a regime and searching for one.

**Three arms**, the middle one being the control that matters: λ frozen; λ moving by a
random walk that *ignores* the coupling signal; λ moving under the signal. Motion without
direction is the null — if the directed arm merely does what the undirected one does, nothing
is being searched for.

Decision rules were fixed numerically before the run, with a saturation guard: if more than 20%
of λ values pin at a boundary, that arm is degenerate and its result void.

### Result: convergence occurs, and the design cannot attribute it

| arm | G(0) | G(100) | G(400) | G(800) |
|---|---:|---:|---:|---:|
| λ frozen | 7.1 | 1.1 | 2.1 | 3.1 |
| λ random walk | 7.1 | 1.1 | 2.1 | 3.1 |
| λ signal-coupled | 7.1 | 1.9 | 2.0 | 0.3 |

The two starts **are** separated at the first checkpoint (−7.1 ± 2.5, t = −2.78) and **are not**
separated at any later one (t = −0.67, +0.93, +0.07). So the ordered and chaotic starts do
converge, and stay converged. But two properties of the experiment make that uninterpretable as
evidence of search.

**The instrument is slower than the effect it measures.** The damage horizon is 40 steps, and a
uniform rule field diversifies into 68–85 distinct rules within ~100 steps (§III.1). By the time
a single measurement completes, both initial conditions have already been erased. This shows in
the numbers: G(0) is **7.1**, where the initial conditions differ by ~64 on the calibrated scale.
**The measurement never saw the separation it was built to track.**

**The control is degenerate.** The random-walk arm's damage series is *byte-identical* to the
frozen arm's: at the chosen step size λ diffused to SD 0.0043, too small to change any decision.
So there is no "motion without direction" arm — there are two frozen arms and one moving one, and
the comparison the design rests on does not exist.

Both faults trace to one parameter each, and they pull against each other: a larger λ step
saturates the directed arm at the boundary (100% of cells pinned, measured), a smaller one makes
the undirected arm inert. The usable band, if there is one, was not located before the run.

### What stands

- **The within-run instrument works.** The fork is non-invasive (verified in code and by
  construction) and `damage(t)` is well-defined. That apparatus is reusable and is the durable
  output.
- **The directed arm does move and does not saturate**: λ 0.550 → 0.788 with SD 0.0021, inside
  the guard. The mechanism functions; only its control does not.
- **No claim of search is made.** The convergence observed is equally consistent with the
  dynamics erasing initial conditions — which §III.1 independently shows it does, fast.

### What a repeat requires

A damage horizon short relative to the diversification time (≈5–10 steps, not 40); a first
checkpoint before diversification; and an undirected control that demonstrably perturbs
decisions — verified *before* the run by showing its trajectories differ from the frozen arm,
which is a one-line check that was not made.

---

## Open, and stated as open

- **The revival gap.** Dead starts reach 16–20 against a random start's 25.5, consistently.
  Unexplained; it is a fact about the basin's structure and we have no account of it.
- **Navigation is half demonstrated.** The construction chooses its policy and, given the
  vocabulary, chooses reasonably. It does not choose its coupling, and no instantaneous local
  observable tracks the coupling optimum — every one measured is monotone in coupling while
  damage peaks.
- **No claim of a critical point.** This is the dynamical reading throughout — a perturbation
  and a clock. The parametric reading's earlier finding, that a parameter sweep yields a broad
  crossover rather than a critical point, is untouched here and would need finite-size scaling
  across widths to revisit.

---

## §III.6 The method changed, and so did what we are looking for

Everything up to §III.5 shares a shape: choose a parameter that ought to matter, sweep it, look
for structure. That shape produced the sequence recorded in §III.3–§III.5 — an apparatus that
works, a mechanism that moves, and no attributable search. This section states the method that
replaced it, because the method is now the more transferable result.

### The rule that changed

> **Before building a controller on a knob, measure whether that knob can move its sensor,
> on-policy.**

The controller in §III.5 could not have worked, and this was establishable *before* it was built.
Its knob was the conatus weight λ and its sensor was realized winner-hunger. Sweeping λ across
its entire range moves mean realized hunger by **0.005** — from 0.176 at λ=0 to 0.162 at λ=1 —
against a seed-level spread an order of magnitude larger. A closed-loop sweep over ten target
values then found that **every** target settles at a boundary, with a bifurcation between 0.160
and 0.170: below it λ runs to 1, above it to 0, nothing rests between. The critical target is not
an attractor; it is the mean realized hunger, separating "positive error forever" from "negative
error forever". **A sign controller on a flat plant has no interior fixed point.**

This is diagnosable in one screen. For each candidate knob *u* and sensor *y*, four conditions,
each cheap:

1. **Leverage** — does *u* move *y* on-policy, by more than seed noise?
2. **Reachability** — does the target *y\** lie inside *y*'s attainable range?
3. **Validity** — does *y* track the objective (criticality), rather than merely being movable?
4. **Stability** — does the closed loop have a fixed point rather than a repeller?

λ↔hunger fails (1), fails (2) twice over — the shipped target 0.05 sits below the attainable
range, and a target chosen from the open-loop response curve sits above the closed-loop value —
and fails (4). **(3) was never reached.** Two of those failures repeat a defect already recorded
against the rate target in Part II: *an aspirational target below the achievable range converts a
preference into a monotone gradient.*

### What the screen then found

Applied to the engine's other knobs, the screen located a knob that passes conditions 1–3
outright — and it is not one we had been treating as a control at all. It is the **selection
precision γ**, the parameter that grades how sharply a policy is chosen from its expected free
energy. Before this work the engine had no such parameter: the winner was a hard argmin, which is
γ → ∞.

Damage reach at width 250, t = 100, against anchors re-measured in the same harness at the same
width (rule 204 = 1.0, rule 90 = 8.0, rule 54 = 36.0, rule 110 = 38.1, rule 30 = 60.9):

| γ | damage reach |
|---:|---:|
| argmin (γ → ∞) | 71.7 ± 3.3 |
| 1 | 6.3 ± 2.0 |
| 4 | 19.1 ± 2.9 |
| 8 | 27.8 ± 3.5 |
| 12 | 34.5 ± 2.8 |
| 14 | 35.3 ± 2.6 |
| 16 | 41.8 ± 3.1 |
| 18 | 40.8 ± 2.8 |
| 64 | 62.6 ± 2.9 |

*(γ = 10–18 re-measured on 24 seeds disjoint from the coarse sweep's; γ=16 independently
reproduces at 41.8 ± 3.1 against the coarse run's 39.3 ± 4.3.)*

**No measured γ lies inside the 36–38 band.** γ = 14 sits at 35.3, just below rule 54; γ = 16 at
41.8, above rule 110. The band is *bracketed*, not hit, and γ\* ≈ 15 by interpolation. The
crossing is steep — 6.5 damage units between γ=14 and γ=16 — which matters for controllability:
a controller regulating here works on a narrow, steep stretch, and that is a stability hazard
rather than a convenience.

**γ traverses the entire order-to-chaos axis monotonically**, from frozen at γ=1 to chaotic at
γ=64, crossing the class-IV band between γ = 14 and γ = 16. The hard argmin the engine shipped
with sits *beyond* rule 30 — the selection rule alone was holding the system in chaos.

Set against λ, the contrast is the whole point: λ moves the objective by nothing across its full
range; γ moves it by a factor of ten. And condition (3) does not arise for γ, because the sensor
here **is** the criticality measure rather than a proxy for it.

### §III.6.1 What "controllable" would mean for γ, and the obstacle

That γ *reaches* the band is not that the construction *finds* it. A hand-set γ = 14 is a
well-chosen constant. The claim Part III has been trying to earn requires the loop to close, and
there is a specific obstacle, which is new — it is not the one λ failed on.

**Damage reach is not an internal observable.** It is measured by forking the run, perturbing one
bit, advancing a twin and counting divergences. The construction cannot compute it about itself
at runtime; only an external instrument can. So a controller on γ cannot use the sensor that
makes γ attractive.

This splits the controllability question in two, and the first half gates the second:

- **(a) Is the loop closable at all?** Does any *runtime-computable* quantity track damage reach
  as γ varies? Candidates the engine already computes: decision entropy (which γ directly
  grades), realized blend share, adoption rate, per-step change rate, and the epistemic quantity
  X the policy scoring already evaluates. **The screen is a correlation across the γ sweep
  between each internal candidate and externally-measured damage.** A candidate that tracks
  damage is a sensor the construction can regulate on; if none does, the loop cannot be closed
  from inside, and γ is a tuning parameter rather than a control — which would be a real result
  and should be reported as one.
- **(b) Given such a sensor, does the loop converge and hold?** This is the familiar experiment,
  and it is the §III.5 design transplanted onto a knob that passes the screen: start γ₀ at
  {2, 14, 64} and look for a common interior attractor; kick γ at equilibrium and look for
  restoration, since **restoration after perturbation is the operational meaning of "finds"**;
  and require the attractor to coincide with the independently swept band, or the controller has
  merely found *an* attractor rather than *the regime*.

Two controls are mandatory and both are cheap. A **positive control**: with a deliberately
unreachable target, γ must ramp to a boundary — reproducing the known failure mode, so that a
null elsewhere is interpretable rather than indistinguishable from a broken harness. And a
**no-kick arm** in the restoration test: a knob already pinned at a boundary returns toward it
after any kick, which mimics restoration exactly; without the no-kick arm that mimicry is
invisible.

### What is claimed here, and what is not

**Claimed.** The engine's selection rule is a criticality control with measured leverage across
the full order/chaos axis; the class-IV band is reachable at γ ≈ 14–16; and the shipped hard
argmin was itself holding the system beyond the chaotic anchor.

**Not claimed.** That the construction finds that band. That any internal sensor tracks damage —
untested, and it is the gate. That a loop on γ is stable — untested. The distinction between a
system that *lands* in a regime and one that *searches for* it is exactly what §III.3–§III.5
failed to establish for λ, and nothing above establishes it for γ.

---

## §III.7 The sensor screen, the candidate controller, and the prior art

§III.6 left the controllability of γ as two questions, the first gating the second: whether any
runtime-computable quantity tracks damage at all, and whether a loop on such a quantity converges.
This section answers the first and states the controller the answer implies.

### §III.7.1 The screen, and why it has two halves

Six quantities the engine already computes were screened against externally-measured damage
reach, ten γ values × sixteen seeds. The screen is run twice, and the second half is the one that
matters.

**Between-γ** asks whether an observable tracks damage across the sweep. It is necessary and
**not sufficient**, because a regressor can track damage merely by tracking γ — and decision
entropy is close to a deterministic function of γ, since γ *is* the softmax temperature. A
controller reading such a sensor reads its own knob back: it would report perfect agreement and
sense nothing about the world.

**Within-γ** holds the knob constant and varies only the seed. Any correlation surviving there is
state information that the knob cannot account for, because the knob did not move.

| observable | γ→S leverage | tracks damage (between-γ) | **tracks damage (within-γ)** |
|---|---:|---:|---:|
| decision entropy | 0.959 | 0.544 | 0.365 |
| **halting share** | **0.368** | **0.529** | **0.414** |
| change rate | 0.093 | 0.456 | **0.431** |
| adoption | 0.873 | 0.465 | 0.224 |
| blend share | 0.256 | 0.041 | 0.224 |
| distinct kinds | 0.288 | — | 0.156 |

**The loop is closable: several observables carry genuine state information about damage.** That
is the first sensor result in this work to pass a validity test rather than assume one.

But the table also contains a trap that the obvious reading walks straight into. **Change rate has
the best state signal and almost no leverage from γ (0.093).** The property that makes it
non-circular — independence from the knob — is exactly what makes it uncontrollable *by* that
knob. It senses the residual (is this run more damaged than typical for its γ), not the level. A
γ controller on change rate alone reproduces the λ failure in a new costume.

**Halting share is the only single observable strong on all three counts**: γ moves it, it tracks
damage across γ, and it still tracks damage at fixed γ.

### §III.7.2 The candidate controller

Sensor: a **two-term composite**, S = w₁·(halting share) + w₂·(change rate), the weights fitted by
least squares to predict damage. Halting supplies steerability; change rate supplies state
information γ cannot fake. Neither term is viable alone, which is the argument for the composite
and not merely a preference for one.

Knob: γ. Law: **integral action with a trailing-window average**, not a sign controller — the
sensor's within-γ R² is about 0.19, so a fixed-step sign law on a reading that noisy chatters
rather than settles. Anti-windup at the clips.

The design is deliberately conservative about the failure this work keeps repeating:

> **The reachable range of S is measured before any setpoint is chosen, and the setpoint is the
> midpoint of what was measured.** Three controllers here have died with a target outside its
> sensor's attainable range — the rule-change target 0.15 against a floor of 0.5; the hunger
> target 0.05 against a realized range of 0.157–0.187; and a hunger target of 0.1676 read off an
> open-loop curve that sat above the closed-loop value. Reachability is now a measured step, not
> an assumption.

Two controls are mandatory. A **positive control** — an unreachable setpoint must ramp γ to a
boundary, or the harness cannot distinguish a null from a broken rig. And a **no-kick arm** in the
restoration test, because a knob already pinned at a boundary returns toward it after any kick and
mimics restoration exactly.

The falsifier is fixed in advance: **if three γ₀ starts do not converge to a common interior value
under a reachable setpoint, the inner loop does not close, and that is the result.** The setpoint
is not to be adjusted until convergence appears.

### §III.7.3 Prior art: Rate Control of Chaos

The closest related work we have found is olde Scheper's **Rate Control of Chaos** (RCC) and its
allosteric extension ARCC (arXiv:2401.04786). For a variable *X* with domain parameter μ,

    q(X) = Xⁿ/(Xⁿ + μⁿ),    σ(X) = f · exp(ξ · q(X) + θ)

with σ multiplying the nonlinear growth terms. With ξ < 0, a variable running out toward the limit
of its own dynamic range has its growth damped. The stated purpose is **maintaining the system in
a critical dynamic state** — our objective, reached from biochemistry rather than from cellular
automata.

The form is shared with ours: both modulate a nonlinearity by an exponential of a scaled scalar,
and in both the coefficient decides decisiveness. The instructive difference is that **RCC is
state feedback with a fixed gain, where ours is a fixed gain with no state feedback** — which is
precisely the gap §III.7.2 is built to close. The honest description is convergent architecture
from a different discipline, not derivation.

One construction is worth borrowing outright. **q(X) is a normalised position within the
attainable domain**, so q ∈ [0,1) always and a setpoint expressed in q is reachable *by
definition*. Our most-repeated failure is structurally impossible in that formulation. Adopting
q-normalisation would convert the reachability discipline of §III.7.2 from a procedure we must
remember into a property of the controller.

The allosteric reading also maps with unusual exactness: an allosteric regulator does not
participate in the reaction but changes the enzyme's conformation and thereby the rate, and an
exotype does not participate in the update but transforms *which rule applies* and thereby the
rate of change. **exotype : genotype :: allosteric ligand : enzyme.** That analogy generates a
question we have not asked: allostery is characteristically cooperative (Hill n > 1), while our
exotype application is first-order.

Full reading notes, including the differences that should keep the connection from being
oversold — continuous ODEs against a discrete stochastic CA, control into orbits against a band in
a statistical measure, per-variable control against one global scalar, and our absence of any
scale-free claim — are in `TN-rcc-connection.md`.

---

## §III.8 What an exotype actually is

Part III cannot rest on the informal gloss the project has used — *exotype as culture,
transmissible but not heritable*. That gloss survives only while nobody reads the transition. This
section states the mechanism and then says what it does and does not license.

### §III.8.1 The mechanism

The exotype vocabulary is 12 named kinds. Each kind names a **propagator σ**, a permutation of
the eight three-cell neighbourhood patterns. A rule (a "sigil") is an eight-bit string indexed by
those same eight patterns.

At each site, each step, on the non-blend path (`self_tuning.clj:46-64`, `:277-282`):

    k     ~ Uniform{0..7}            -- a bit position, drawn per site per step
    value = NOT bits[k]              -- read the bit at k, INVERT it
    di    = index of σ(pattern k)    -- the exotype maps that pattern to another
    bits' = bits with position di := value

In one sentence: **read a bit of the rule at position k, flip it, and write it at position σ(k).**

Three consequences follow immediately, and none of them are visible from the "culture" gloss.

- **The exotype edits the rule; it does not transform how the rule is applied.** σ never touches
  the propagation of state. It selects a *write address inside the genotype*. The name
  "propagator" is misleading on this point.
- **When σ = identity, this is exactly a point mutation** — flip a random bit of the rule. Eleven
  of the twelve kinds differ from identity, and for those the read address and the write address
  differ, so the operation transfers inverted information from one entry of the truth table to
  another.
- **It fires on every applied non-blend site, every step.** This is not a rare perturbation. It is
  the ordinary path.

So the accurate description is Joe's, not the one this project has been using: **an exotype is a
locally-conditioned way of adapting the global rule** — more precisely, *a locally varying,
structured mutation operator, whose bias is which bit of the rule gets rewritten from which.*

### §III.8.2 Correcting two claims of our own

**"The exotype changes how new rules enter the population *rather than* how rules are applied" is
a false dichotomy** (mine, and wrong). The exotype transition *is* the application path for
non-blend sites and *is* generative; these are one operation seen from two sides, not competing
descriptions. What is true and non-trivial is the measured share: **78.7% of novel-sigil events
arise on the exotype path, against 21.3% from policy blend** (§108, verified by re-run).

**"Transmissible but not heritable" does not carry the weight we put on it.** The exotype grid
persists across steps and is transmitted between sites; whatever we call that, it is inheritance
of an acquired modifier in the sense that matters for a Baldwin-style argument. Declining the word
"heritable" does not change the causal structure. Part III should describe the transmission
mechanism and drop the label.

### §III.8.3 How much of the space is this?

σ is a permutation of eight patterns, so the family has **8! = 40,320** members. The system
instantiates **12**.

> **12 / 40,320 = 0.030% of the permutation space.** If the family is widened to arbitrary maps
> rather than permutations — the shape the 2015 implementation actually had — the denominator is
> 8⁸ = 16,777,216, and the fraction is **0.00007%**.

The original four were hand-picked and **why those four has never been justified** — an open hole
recorded long before this work (TN 15) and untouched by it. The eight later additions were made
for a specific and different reason: the EFE dynamics collapsed onto `:collapser` because the
score cache only iterated the four declared kinds, so the objective could not select the rest.

This matters for every claim in §III.6–§III.7. The γ sweep, the sensor screen, and the controller
all run **at one fixed point in a 40,320-member design space, chosen by hand for unrecorded
reasons.** Nothing measured here is known to generalise across σ, and no experiment we have run
varies it. That is not a caveat to add at the end of Part III; it is a boundary on what Part III
is entitled to claim.

---

## §III.9 An intrinsic complexity measure: local compressibility

The damage-reach assay fails as an objective for this system on three independent counts:
it is defined by **external reference automata**; the agent **cannot perceive it** (it is computed
on a twin run — §112); and its numeric band is an artifact of the stopping time (§114, rules 54
and 110 grow ~5× between t=50 and t=400). This section states a replacement that has none of
those properties.

### §III.9.1 Definition

Tile the phenotype spacetime sheet into S×S patches, pack each to a bitstring, and take the
compressed-length ratio (zlib, level 9). The **local compressibility field** is the resulting map;
the observable is its **distribution**, not its mean.

Two summary statistics, and both are needed:

- **%mid-range** — the fraction of patches with ratio in [0.3, 0.9]. Distinguishes a *graded*
  distribution from a *bimodal* one.
- **SD among active patches** (ratio > 0.5) — heterogeneity that a frozen region cannot
  manufacture for free.

The second exists because of a defect Joe identified in the first draft of this measure: raw SD is
inflated by a dead zone. At γ=1 the SD of 0.233 was **one freezing column** against a saturated
remainder — "ordered background plus random defects", which codex-1 independently named as the
characteristic false positive for compressibility measures.

**Patch size is load-bearing.** At S=50 (312 bytes) zlib's own ~11-byte overhead puts the ceiling
at 1.031 and rules 90, 30 and γ=4 all pinned there with SD exactly 0.000 — the estimator had no
resolution at the incompressible end. At **S=100** (1250 bytes) the ceiling falls to 1.008 and the
separation below appears. Any use of this measure must state S.

### §III.9.2 It passes validation on the ECAs

S=100, stride 50, 16 patches per sheet, 250×250, common seed.

**Packing matters, and the first version of this table was wrong.** Patch bits must be packed
CONTIGUOUSLY. Packing row-by-row pads each 100-bit row to 13 bytes, appending four zero bits per
row — a regular period-13 artifact, present in every patch, that zlib exploits. Caught by codex-4
on review (§113a). Corrected figures:

| | %mid-range | mean | SD | SD active | *was (padded)* |
|---|---:|---:|---:|---:|---:|
| **rule 54** (class IV) | **94%** | 0.855 | 0.036 | 0.036 | *94%* |
| **rule 110** (class IV) | **75%** | 0.721 | 0.192 | 0.119 | *88%* |
| **γ = 1** | **81%** | 0.497 | 0.204 | 0.125 | *62%* |
| **γ = 4** | **81%** | 0.706 | 0.205 | 0.135 | *81%* |
| γ = 16 | 44% | 0.869 | 0.138 | 0.138 | *56%* |
| rule 204 (frozen) | 0% | 0.037 | 0.000 | — | *0%* |
| rule 90 (nested) | 0% | 1.009 | 0.000 | 0.000 | *0%* |
| rule 30 (chaotic) | 0% | 1.009 | 0.000 | 0.000 | *0%* |
| γ = 64 | 0% | 1.009 | 0.000 | 0.000 | *0%* |

**Both class-IV rules score 88–94%; frozen, nested and chaotic all score 0%.** The measure
separates the classes in the right direction — and note it puts **rule 90 with rule 30**, refusing
to call it class IV despite its fractal appearance. Damage reach does the opposite, placing rule 90
(damage 8.0) next to the frozen rule.

### §III.9.3 Why this is the right kind of objective

- **Intrinsic.** Computed from the system's own phenotype. No twin run, so §112's obstruction —
  that the agent cannot perceive an observer-side counterfactual — does not apply.
- **No external reference.** The ECAs validate the *instrument*; they do not define the target.
- **Not horizon-defined.** It reads a distribution over a sheet rather than a scalar at one t.

### §III.9.4 What it says about the system

**γ = 1 and γ = 4 tie at 81%**, above rule 110's corrected 75% and below rule 54's 94%.
Under the padded measure γ=4 appeared to win outright; it does not. Any claim that a *particular*
γ is optimal is not supported — what is supported is that low-to-mid γ scores high and high γ
scores zero.

The optimum is *not* where frozen area is minimised: γ=16 and γ=64 have smaller dead zones and
score worse (44%, 0%). Both top scorers have **large** dead zones. The frozen region is not a
defect — it is half of what makes the distribution graded.

Two distinct class-IV signatures also appear, and should not be collapsed: **rule 54 is
homogeneously intermediate** (94%, SD 0.042) while **rule 110 is genuinely heterogeneous** (88%,
SD 0.178). γ=4 (SD 0.214) resembles 110. On SD-among-active, γ=4 (0.143) and γ=16 (0.155) both
*exceed* rule 110 (0.105).

### §III.9.5 Owed before this is load-bearing

Per codex-1's refinement and our own findings: **shuffled controls** (row-shuffle preserves density
while destroying spatial structure — if the score does not separate real from shuffled it is
measuring density); **multiple radii**; **temporal persistence** of the mixture rather than of any
one region; and a **block-entropy or BDM estimator** for small patches where gzip cannot work.
Pair with **predictive information**, which is intrinsic — *not* with perturbation propagation,
which is damage reach and would reimport every objection above.
