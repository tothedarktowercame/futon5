# Evaluating an epistemic term in a three-layer cellular automaton

**Self-contained.** No prior context with this codebase is assumed. Standard cellular-automata
and active-inference terminology throughout. Written 2026-08-04.

**What is wanted:** an adjudication of three small experiments (E1–E3, §7), or a better
proposal. Each is designed to be a few hours of work and to add no new mechanism.

---

## 1. The system

A ring of *N* cells. Each cell carries three things:

- **rule** — an 8-bit elementary cellular automaton rule (256 possibilities);
- **state** — one bit;
- **policy** — a permutation σ of the eight truth-table positions, drawn from a small
  vocabulary.

Per step:

1. **State update.** Each cell applies its *own* 8-bit rule to its three-cell state
   neighbourhood, exactly as an ECA does. Cells with different rules coexist on one ring.
2. **Rule update.** Each cell's rule is transformed by its policy. Concretely, for each
   truth-table position *k*, if bit σ(*k*) equals bit *k*, then bit *k* is flipped. So σ
   determines both *how fast* a rule changes and *which* rules are stationary under it.
3. **Optional lateral coupling.** With strength β ∈ [0,1], a cell's rule is blended with its
   neighbours' by bitwise agreement: where left, centre and right agree on a bit, the bit is
   kept; where they disagree, the cell may take the neighbours' value. At β = 0 there is no
   lateral coupling at all and the rule layer is *N* independent chains.

Two exact structural facts about the policy layer, both derived and verified:

- **rate = 0.5 + fix(σ)/16**, where fix(σ) is the number of fixed points of σ. So the
  per-step probability that a rule changes is a function of the fixed-point count alone and
  takes only nine values.
- **A rule is stationary under σ iff every cycle of σ is even.** All-even cycle types have no
  fixed points, hence fix = 0, hence **stationary rules exist only at rate exactly 0.5000**.
  The count of stationary rules is 2^(number of cycles): 16, 8, 4, 4, 2 for cycle types
  (2,2,2,2), (4,2,2), (6,2), (4,4), (8) respectively, and 0 for any σ with an odd cycle.

Consequence, measured over 400 seeds: a policy with stationary rules drives the whole ring to
a halt, with median half-time falling 40 → 20 → 10 → 5 as the stationary-rule count doubles
2 → 4 → 8 → 16. A policy with none (e.g. cycle type (5,3)) **never halted in 400/400 runs**.

## 2. The objective, and what is wrong with it

Policies are chosen per cell by minimising

```
G = risk + ambiguity + λ · preference
  risk       = KL( predicted rule-change rate ‖ target 0.15 )
  ambiguity  = Σ_channels  H( predicted channel value )        <-- see below
  preference = KL( predicted "stalled" probability ‖ target 0.05 )
```

where the prediction comes from a table P(next observation | policy, current observation),
estimated by measuring the substrate (28,620 transitions).

**The `ambiguity` term is the summed marginal predictive entropy, Σ H[Q(o_i)].** In expected
free energy, ambiguity is `E_{Q(s|π)}[H[P(o|s)]]` — the expected entropy of the *likelihood*,
which requires latent states to be ambiguous about — and the dark-room outcome is barred by
the *epistemic* component, the expected information gain `I(s; o | π)`.

This implementation has **no latent layer**: the model maps observation to observation. There
is no likelihood distinct from the transition, no posterior, and therefore no information gain
anywhere in the scored objective. Minimising Σ H[Q(o)] rewards **determinism** — the opposite
behavioural sign to an epistemic drive.

### 2.1 The measured consequence

The ring converges to a policy monoculture that halts. Which monoculture depends on the
vocabulary offered, but not whether it halts:

| vocabulary | winner | stationary rules | halted fraction |
|---|---|---:|---:|
| 4 policies | (6,2) | 4 | 1.000 |
| 12 policies | (8) | 2 | 0.995 |

The only policy that cannot halt, (5,3), is a co-winner in the pure-function argmin over
observations and **loses in the dynamics**.

The discriminator is `ambiguity`, not the weighted term. At a representative observation, with
all four policies at rate 0.5000:

| policy | stalled-prob | risk | **ambiguity** | preference | total |
|---|---:|---:|---:|---:|---:|
| (5,3) — cannot halt | 0.1744 | 0.337 | **2.308** | 0.102 | 2.746 |
| **(8) — halts** | 0.0281 | 0.337 | **2.101** | 0.006 | **2.444** |
| (6,2) — halts | 0.0703 | 0.337 | 2.240 | 0.004 | 2.580 |
| (2,2,2,2) — halts | 0.1502 | 0.337 | 2.406 | 0.070 | 2.813 |

**Risk is identical** across all of them, because risk is a function of rate alone and these
all have rate 0.5. **The weighted preference term is ~4% of the total.** Sweeping λ confirms
this directly: at **λ = 0**, with that term removed entirely, 39 of 40 runs still converge to
the halting policy (8). A halting policy wins because a halted future is a *predictable*
future, and predictability is what this objective rewards.

## 3. The criterion used to judge outcomes

**Damage reach:** flip one cell's state at t = 0, run an otherwise identical copy, and count
differing state cells at t = 100. Calibrated against elementary rules measured in the same
harness, 24 seeds:

| anchor | reach |
|---|---:|
| rule 204 (stationary) | 1.0 |
| rule 90 | 8.0 |
| rule 54 | 32.8 |
| rule 110 | 37.1 |
| rule 30 (chaotic) | 65.1 |

The target is the class-IV band, not the maximum — rule 30 scores highest and is chaotic.

Two results from this instrument matter here:

- **Lateral coupling has an interior optimum.** Rule-layer damage cone width at t = 1000,
  width 801, 12 seeds: **6.4** at β = 0.25, **11.9** at β = 0.5, **0.0** at β = 0.75. It does
  not saturate at β = 0.25–0.5 and dies entirely at 0.75.
- **No instantaneous local observable tracks that optimum.** Local disagreement, stall rate and
  activity are all strictly *monotone* in β across the same range, while damage peaks. A
  hill-climber on any of them runs to an endpoint.

## 4. A previous attempt to add an epistemic term, and its refutation

The obvious repair — add information gain — was tried and falsified **before implementation**,
which is the local house method and worked.

**What was tested:** an epistemic quantity used as a multiplicative gate on the lateral-coupling
operator, `β_effective = 1 − exp(−κ · EIG)`, in an 80-cell reduced model, scored by **mean rule
domain length**.

**Result:** no interior optimum in κ (+0.143 against a pooled sd of 0.612 — 0.23 sd, flat), and
the gated version *lost* to a matched constant coupling rate at every κ tested (−0.29, −0.27,
−0.60 sd; negative three times of three).

**The diagnosis, which is the valuable part:** the coupling operator *already implements the
epistemic principle structurally*. Bitwise-agreement blending returns the centre rule unchanged
wherever the neighbourhood agrees, and acts only where neighbours disagree — that is precisely
"change only where you would behave differently". The gate was gating something already gated,
and paid for it.

**A second, separate finding** from the same programme: an epistemic term scored on the *policy
layer* cannot work, because a policy that produces the observations confirming its own
predictions is a self-consistent fixed point of any objective scored on predictions. That
observation was originally made about a high-activity policy; it applies with more force to a
halting one, which makes its predictions true by stopping.

## 5. Why the question is still open

Three gaps in the refutation. These are gaps, not grounds for disbelieving the result.

1. **It scored the wrong observable.** The programme's own surviving definition is that
   epistemic value is the divergence of a cell's own future under a candidate action — *which
   damage measures*. The pilots scored **rule domain length**. The calibrated damage instrument
   of §3 did not exist when they ran.
2. **The reduced model omits the layers in question.** It had no policy layer and no rule
   transformation — only state dynamics and coupling. It therefore cannot speak to the policy
   selector in either direction.
3. **It searched the wrong parameter.** It sought an interior optimum in κ, the gate sharpness,
   and found none. §3 finds one in **β, the coupling strength itself**, measured by damage. If
   the coupling operator *is* the epistemic mechanism (§4), then that mechanism already has a
   measured interior optimum — located in the coupling, not in a gate on top of it.

## 6. What is deliberately not proposed

- **Adding an epistemic term to the policy-selection score.** §4's second finding closes it,
  and the halting-policy result strengthens rather than weakens that argument.
- **Re-running the κ sweep.** §4's first finding closes it.

## 7. The three experiments

Smallest first. Each changes one thing. None adds a mechanism.

### E1 — re-score the existing refutation on damage

Repeat the gated-vs-matched-constant comparison exactly as run, in the same reduced model,
changing **only the scored observable**: damage reach instead of rule domain length.

- *Acceptance:* a decision on whether the gate's deficit survives on the observable the
  definition actually names, with seed-level uncertainty and the sign reported three times as
  before.
- *Falsifier:* the gate still loses on damage ⇒ the refutation stands on the quantity that
  matters, and the gate is closed properly rather than on a proxy.
- *Cost:* hours. The reduced model and both scripts exist.

### E2 — measure whether the implemented epistemic quantity correlates with damage

The implemented term is a Beta-posterior variance of a local confirmation rate, pooled over
neighbours already holding the candidate policy. It is *asserted* to be anti-correlated with
damage — maximal on zero evidence, and zero evidence is commonest in halted regions where
nothing propagates and an action teaches least. **That correlation has never been measured.**

- *Acceptance:* a per-cell scatter and correlation coefficient of the implemented quantity
  against measured local damage, in the full system, with the sign and magnitude reported.
- *Falsifier:* correlation ≈ 0 or negative ⇒ the implemented quantity is not an epistemic term
  and should be retired rather than tuned. That is a useful outcome, not a failure.
- *Cost:* hours. Both quantities are already computable.

### E3 — only if E1 and E2 both pay

Ask whether a cell can estimate its own damage from its **local history**. §3 established that
no *instantaneous* local observable tracks the damage optimum; a *temporal* one is untested,
and second-order temporal diagnostics — scaled variance, integrated autocorrelation time,
critical slowing down — are the standard candidates. Finite-size scaling across two or three
ring widths should be an acceptance condition, not an afterthought, since the distinction
between a genuine interior feature and a broad crossover is exactly what is at issue.

- *Falsifier:* no local temporal estimator tracks damage ⇒ endogenous navigation requires an
  explicit perturbation probe, and the guessing stops.

## 8. The question for the reviewer

Is E1 → E2 → E3 the right ladder, and is E1 the right first rung? Disagreement is more useful
than agreement. If the framing in §2 is wrong — if the objective as described *is* a defensible
expected free energy and the halting outcome should be read some other way — that is the most
valuable thing to say, and it should be said first.
