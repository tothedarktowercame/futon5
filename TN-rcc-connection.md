# TN — Rate Control of Chaos as related work, and what it lends us

**Source:** olde Scheper, T.V., *Beyond Michaelis-Menten: Allosteric Rate Control of Chaos*,
arXiv:2401.04786v1 (9 Jan 2024). School of Engineering, Computing and Mathematics, Oxford Brookes.
Read pp. 1–8 (abstract, introduction, RCC method, Nielsen model, ARCC generalisation).

## 1. What RCC actually is, in its own terms

Not a metaphor — a specific construction. For a state variable *X* with domain parameter μ:

    q(X)  = Xⁿ / (Xⁿ + μⁿ)              (a saturating quotient; n = Hill coefficient, usually 1)
    σ(X)  = f · exp(ξ · q(X) + θ)       (the control function)

and σ **multiplies the nonlinear growth terms** of the dynamics. In the Rabinovich–Fabrikant
demonstration, four such σ's scale the nonlinear terms of a 3-variable chaotic system, with
ξ = −0.5, μ = 4, f = 1.

The mechanism in one line: **ξ < 0 means that as a variable runs out toward the limit of its own
dynamic range, the exponential damps its growth term.** Chaos is controlled not by targeting an
orbit but by *rate limitation proportional to where the variable sits within its attainable
domain*.

ARCC adds allostery: μ, f and θ become themselves dynamic, modulated by ligand concentration, so
the control parameters adapt rather than being fixed.

The claim that matters most for us, from the introduction: *"the emergent scale-free dynamics can
address the issue of scalability by maintaining the system in a **critical dynamic state**."*
That is our objective, reached by a different route.

## 2. The structural parallel to γ, stated precisely

Our selection rule after R14 is

    P(c) ∝ exp(−γ · total_c)

Set beside σ(X) = f · exp(ξ · q(X)), the shared form is real: **both modulate a nonlinearity by
an exponential of a scaled scalar, and in both the sign of the coefficient decides whether the
system is pushed toward or away from decisiveness.** γ → ∞ is our hard argmin (maximal
commitment, measured chaotic at damage 71.7); γ → 0 is indifference (measured frozen at 6.3).
ξ → 0 in RCC is no control at all.

But the roles of the parts differ, and the difference is the interesting part:

| | RCC | ours |
|---|---|---|
| what is fixed | ξ (the coefficient) | the scoring function |
| what carries state | q(X), per variable, continuously | `total_c`, per cell, per candidate |
| what the control multiplies | the nonlinear growth term | the selection distribution |
| locality | per-variable, local | one global scalar (γ) |
| time | continuous ODE | discrete stochastic update |

**RCC's control is state-feedback with a fixed gain; ours is a global gain with no state
feedback.** That is exactly the gap the inner loop under construction is meant to close — and it
means RCC is not merely analogous, it is *the architecture we are converging on, arrived at
earlier and from biochemistry*.

## 3. What it lends us — the reachability lesson, which is our most repeated failure

The single most repeated defect in this project is **an unreachable target**: the rule-change
target 0.15 against a floor of 0.5; the hunger target 0.05 against a realized range of
0.157–0.187; and a hunger target of 0.1676 taken from an open-loop curve that sat above the
closed-loop value. Three controllers, one failure mode.

RCC makes that failure **structurally impossible**:

> q(X) = Xⁿ/(Xⁿ + μⁿ) is a *normalised position within the attainable domain*, with μ ≥ max(X)
> by construction. q ∈ [0,1) always. A setpoint expressed in q is reachable by definition.

**The lesson is portable and cheap: express the controlled quantity as a fraction of its own
attainable range, not in raw units.** Had our hunger controller regulated
q(h) = h/(h + μ_h) rather than raw h, the 0.05 target could not have been specified outside the
range, because there is no outside. This is worth adopting in the inner loop directly — the
packet already demands the range be *measured* first, and q-normalisation would make that
measurement part of the controller rather than a precondition of it.

## 4. Biological interpretation — and it is specific, not decorative

The allosteric reading maps onto our layers with unusual exactness:

- **An allosteric regulator does not participate in the reaction.** It binds elsewhere, changes
  the enzyme's conformation, and thereby changes the *rate*.
- **An exotype does not participate in the update.** It is a transform *on* rules — a permutation-
  like map on the rule byte — that changes which propagator applies and thereby changes the rate
  of change. It is not itself a rule.

> **exotype : genotype :: allosteric ligand : enzyme.**

That is a sharper statement than "the system is biologically inspired", and it makes a
prediction: allosteric regulation is characteristically *cooperative* (the Hill coefficient
n > 1 in RCC's q). Our exotype application is currently first-order — one draw, one flip. Whether
a cooperative exotype (several coordinated flips, n > 1) behaves differently is a well-posed
question this analogy generates and we have not asked.

Two further correspondences worth recording:

- **The flow parameter is a bifurcation parameter** in the Nielsen glycolysis model — "allowing
  stable, oscillatory and chaotic dynamics to emerge". We found γ to be exactly that: monotone
  traverse from frozen (6.3) through the class-IV band (γ ≈ 15) to chaotic (62.6). Same role,
  different substrate.
- **The conatus/hunger vocabulary is metabolic in form** — the terms are rate preferences over a
  resource-like quantity — which is why the Michaelis–Menten framing reads as familiar rather
  than foreign.

## 5. Honest differences, so the connection is not oversold

- RCC controls **continuous ODEs**; our substrate is a **discrete stochastic CA**. The exponential
  damping of a growth term has no exact discrete analogue, and our γ acts on a *distribution over
  discrete actions*, not on a rate of change.
- RCC demonstrates control **into specific orbits**; we have a **band in a statistical measure**
  (damage reach), which is a weaker object.
- RCC's control is **per-variable**; γ is **one global scalar**. Our per-cell analogue would be a
  γ field, which we have not tried and which the λ-field experience suggests would be worth
  approaching carefully.
- We have **no scale-free claim**. RCC's scalability argument rests on emergent scale-free
  dynamics; our finite-size behaviour across widths is untested (Part III already records that
  finite-size scaling is owed).

## 6. What to do with this

1. **Adopt q-normalisation in the inner loop.** Cheapest and most direct: it retires our most
   repeated failure mode by construction rather than by discipline.
2. **Ask the cooperativity question.** n > 1 in the exotype application is a well-posed experiment
   the analogy generates.
3. **Cite it as related work in Part III.** The "critical dynamic state maintained by rate
   control" framing is the closest prior art we have found to what §III.6 is attempting, and the
   honest framing is *convergent architecture from a different discipline*, not derivation.

*(Noted separately: the author is at Oxford Brookes, which is Joe's lead institution. The
intellectual overlap here is genuine and specific — a better basis for contact than a cold
approach — but whether and how to use that is entirely his call, and nothing above depends on it.)*
