# Precis — *Rule-Rewriting Cellular Automata and the Edge of Chaos*

**Status: 2026-08-07.** Written against `draft8.tex` (64 pp) and `supplement5.tex` (7 pp),
using the argument map (Figure 1) as a starting point and the `futon3/library/mmca`
flexiargs as the discipline. Where the map and the text disagree, the text wins and the
disagreement is recorded here.

Purpose: to state the argument as a chain of claims, each with its ground, its warrant and
its scope, so that the next steps — standoff annotation, then compression — can be decided
by what the argument needs rather than by taste.

---

## 0. The finding this precis reaches

**The paper contains two arguments, not one, and they are joined by shared substrate rather
than by evidence.**

- **Argument A (Parts I → II → III§1)** is a single evidential chain about *what coordinate
  governs propagation* in rule-rewriting automata. It is continuous, and each link is
  measured.
- **Argument B (Part III§2–6)** is about *whether the system can search for its own
  operating regime*, answers no, and exhibits three configurations separated by
  persistence. No measurement connects B to A's coordinate.

This is not necessarily a defect — B is a real contribution and the paper says so — but it
is currently presented as a continuation of A, and it is not one. Everything in §5 below
follows from this.

---

## 1. The question

The classical "edge of chaos" admits two readings, and the paper separates them:

- **parameter-space reading** — a location in a tuning parameter, at which complex
  behaviour appears (Langton's λ and its successors);
- **dynamical reading** — a property of the trajectory, measured by how a perturbation
  propagates.

The paper's substrate is a two-layer automaton whose cells carry *rules* as well as states,
so the rule field evolves alongside the phenotype. The object of study is the operator that
rewrites rules: a permutation σ of the eight truth-table positions, composed with negation.
There are 8! = 40,320 of them, and they are exhaustively enumerable.

---

## 2. Argument A — the coordinate that governs

### A1. The parameter-space reading is closed off inside the permutation core

**Claim.** λ cannot discriminate among the fixed points of this operator family.

**Ground.** Exact, for the whole 8! core: a propagator has fixed rules exactly when every
cycle of σ is even; an all-even σ with *k* cycles has 2^k of them; **every such rule has
λ = 1/2**.

**Warrant.** If every fixed rule takes the same λ, λ carries no information distinguishing
them, so no tuning of λ can locate anything among them.

**Scope.** Exact for the core. Says nothing about non-fixed rules, and nothing about
attraction — which A2 then shows matters.

### A2. Nor does the empirical parameter scan find a critical point

**Claim.** The tested propagator-fraction scan shows a **broad crossover, not a critical
point**; and fixed-point existence does not determine attraction.

**Ground.** Exhaustive census (all odd-cycle operators remain genotype-alive; every
all-even cycle type contains both sustaining and collapsing members); rotation survey;
finite-size scan at 32 runs per cell.

**Warrant.** A critical point would show as a sharpening transition under finite-size
scaling. A broad crossover that does not sharpen is evidence against one.

**Scope.** Finite-run, one spatial protocol, stated seed counts. A *negative* on the tested
axis, not a proof that no critical point exists on any axis.

> ⚠️ **This negative is later contradicted in appearance by Argument B**, which reports a
> sharp bifurcation. The two are compatible (different object, different axis) and draft8
> says so — but a reader meets "no critical point" in Part I and a bifurcation in Part III
> without the reconciliation being prominent. See §5.

### A3. Feedback roughly doubles causal reach

**Claim.** Phenotype-to-genotype feedback approximately doubles the reach of a single-cell
perturbation, against a matched feedback-off control.

**Ground.** The river construction; damage reach calibrated against reference rules of
undisputed regime (204 → 1.0, 90 → 8.0, 54 → 32.8, 110 → 37.1, 30 → 65.1); six seeds, one
lattice width.

**Warrant.** A matched control differing only in the feedback isolates the feedback as the
cause.

**Scope.** One principal construction. Explicitly *not* a census of the 8^8 writings, and
does not retroactively characterise the 8! core.

### A4. The coordinate is *currency*, not diversity, not mobility

**Claim.** What governs reach is **how much of what the genotype reads is causally
current** — not rule diversity, not rule mobility, not λ.

**Ground.** (i) The gain γ interpolates between reading an ancestral phenotype and reading
the live one, with the frozen field a *real* phenotype so marginals and spatial structure
are matched at every setting; reach is monotone in γ. (ii) Conservative transport sustains
high genotype diversity while changing reach far more than diversity does — the **diversity
test**, which breaks the apparent interior optimum. (iii) Across λ, propagator fraction,
diversity, mutation rate, refuge probability, niche width and rule mobility, reach never
leaves the ordered band; blend strength alone reaches its edge.

**Warrant.** Matching everything except currency and still seeing reach move isolates
currency. Varying a rival coordinate and seeing reach *not* move rules the rival out.

**Scope.** The constructions tested. This is the paper's strongest positive claim.

### A5. An endogenous condition re-derives the same coordinate

**Claim.** Making the coupling endogenous does not escape the currency coordinate; it
**re-derives** it from a construction sharing none of Part II's dials.

**Ground.** (i) A gate blind to the phenotype reproduces the dial *exactly*: reach = 1.21 +
22.42·f, R² = 0.993. (ii) Thirteen phenotype-reading gates all exceed that line at their own
firing fraction; the widest reaches 38.23 while firing on 63% of opportunities — above
unit-rate transport's own 25.38. (iii) Width, not strictness, predicts the departure
(R² 0.84 → 0.96 adding width; only 0.88 adding strictness). (iv) **The frozen-gate
control**: same predicate, same width, same spatial statistics, a real phenotype field —
but stale. Across seven matched pairs the live gate exceeds the frozen one every time, by
14.5 cells on average, and every frozen departure is *negative*. The frozen gate fires
*more* often in six of seven pairs and still reaches less.

**Warrant.** (iv) is the load-bearing step. Width is the correlation length of the firing
pattern, so a width effect could be pure geometry; the frozen control matches the geometry
and removes only the currency, and the effect vanishes. Therefore currency, not correlation.

**Scope.** One pair of constituents, one predicate family, sixteen seeds per row. Two stated
limits: frozen firing fractions differ by up to 0.04 (in the conservative direction), and
the width gradient is **not decomposed** — a blind gate with matched correlation length
would separate currency from geometry and **has not been run**.

**→ A ends here.** A1–A5 form a chain: the classical coordinate is closed off, a causal
measurement is substituted, the governing coordinate is identified and survives being
reached by a second, independent route.

---

## 3. Argument B — can the system find its own regime?

### B1. The question, stated as unanswered

**Claim.** Whether a MetaCA carrying an exotype layer can search for the edge of chaos
*itself*, using only runtime-available quantities, is **not settled by this paper**.

**Ground.** Supplement 5 documents the apparatus in full. It scores a surface and moves over
it; it was never run in a closed loop against a live system.

**Warrant.** None needed — this is a disclaimer, and it is correctly placed before the
results rather than after them.

### B2. The layer: the operator becomes local state

**Claim.** An exotype is a per-cell assignment of a propagator; the transition is one
elementary write, g′(σ(k)) = ¬g(k) at a uniformly drawn coordinate. It is therefore a
**locally varying, structured mutation operator**, not "culture", and not a change to how
rules are applied.

**Ground.** The transition as implemented; 78.7% of novel-genotype events arrive by this
path against 21.3% from policy blend.

**Warrant.** Definitional plus a measured share.

**Scope.** *This is the connection to Part I* — and it is **conceptual, not evidential**.
The object σ is the object Part I classifies. Figure 1's violet edge draws exactly this and
nothing more.

> **Correction of record.** An earlier draft drew this edge from Part I's *count theorem*
> ("2^k predicts the immune bytes of every propagator"), verified for all fourteen
> implemented propagators. It is true and **no conclusion depends on it**. Pattern:
> `mmca/verified-but-idle-link`. The repair was substitution, not deletion.

### B3. Three examples separated by persistence

**Claim.** The substrate contains configurations of three kinds — one phase winning, the
other winning, and neither winning within the horizon — separated by a bisectable boundary.

**Ground.** Frozen fraction and absorbing-state checks over 3000–10,000 steps: the low-β arm
settles at 0.040 and never absorbs; the high-β arm absorbs at t = 1046 and t = 881; β = 12,
14, 16 absorb at 1917, 2604, 1722; β = 8 and 10 do not. One realisation at β = 8 sustains an
intermediate state across 10,000 steps with 391 frozen regions still turning over at
t = 9500.

**Warrant.** Absorption is a property of a single trajectory, needs no twin run and no
external reference, and **cannot be faked by an artifact** (`mmca/persistence-over-appearance`).

**Scope, stated in the text.** The parameters do **not** determine which phase wins: the
second seed at β = 8 dissolves, and at β = 10 two seeds reach opposite outcomes. The paper
therefore reports a *region*, and explicitly declines a critical-point claim
(`mmca/realization-variance-is-not-a-regime`).

### B4. The search objective inverts against survival

**Claim.** The objective that found these configurations **ranks them inverted**: every
configuration that reaches an absorbing state scored higher than every one that does not.

**Ground.** Seven configurations, two seeds, perfect separation with the sign reversed. The
objective's two maxima (0.625 and 0.641) are a dying regime and a living one; the most
interesting configuration scores 0.453, eighth of thirty-five.

**Warrant.** The behaviour that separates them occurs *entirely outside* the 250-step window
in which the objective was computed, and the reference automata used to validate it settle
within that window (`mmca/instrument-horizon-must-match-system`).

**Scope.** A statement about this objective on this system. Generalises as a *method*
warning, not as a result about compressibility measures at large.

### B5. The loop is closable — but not on damage reach

**Claim.** A runtime-computable pair of observables predicts an intrinsic objective well
(held-out R² = +0.726) and an external twin-run objective not at all (+0.016).

**Warrant.** Same system, same observable class, same test; only the perceivability of the
target differs (`mmca/perceivable-target`).

**Scope.** Supplement 5. Survives the objective being the wrong target, because it is a claim
about sensor-to-target coupling rather than about the target's value.

---

## 4. Where the map is right, and where it misleads

| Figure 1 element | verdict |
|---|---|
| blue exact branch → λ constant → "no order parameter to tune" | **correct**, A1 |
| amber branch → "no critical point" | **correct**, A2 |
| green branch → Diversity test as Part II's conclusion | **correct**, A4(ii) |
| teal Reading gate → "currency, not correlation" | **correct**, A5 |
| violet Operator → Exotype layer edge | **correct as drawn** — conceptual re-entry of the operator family, B2 |
| Exotype layer → Three examples | **correct**, B3 |
| *implied* continuity from Diversity test → Reading gate → Exotype layer | **misleading**: the first arrow is evidential (A4 → A5), the second is not |

The map's vertical chain reads as one argument descending. It is two: A ends at the Reading
gate, and B begins at the Exotype layer. **The map has no way to show that the arrow between
them changes kind**, and this is its one substantive error.

---

## 5. Consequences for compression

Ordered by confidence.

**Certainly keep** — load-bearing for A: the parity/count/balance theorems; the census and
finite-size scan (they establish the negative that motivates everything); the feedback-vs-control
comparison; the gain dial; **the diversity test**; **the frozen-gate control** (A5's whole
weight rests on it).

**Certainly keep** — load-bearing for B: the transition equation; the persistence criterion and
the three examples; the objective inversion; the disagreeing seeds.

**Candidates for compression.**

1. **The width-gradient analysis in A5** (thirteen gates, R² 0.84 → 0.96, the strictness
   collinearity). The claim it supports — width predicts departure — is *superseded* by the
   frozen control, which shows the width effect is not geometric. The paper itself states the
   gradient is **not decomposed**. Two paragraphs could become two sentences.
2. **The rotation survey** (A2). The census and the finite-size scan already carry the
   negative. Ask: which conclusion changes if the rotation survey is cut?
3. **Part III's vocabulary table** (fourteen propagators, cycle types, 2^k). Now that the
   deductive link is withdrawn, this table supports only the scope statement "12 of 40,320,
   hand-picked". That statement needs one sentence, not a fourteen-row table.
4. **Supplement 5's search-path figure**, if the observable-degeneracy point is made in text.

**Structural change worth considering.** Split Part III explicitly into its two arguments with
a visible seam, or move B into its own part with its own question. Presenting B as a
continuation of A is the paper's main structural weakness and it is *cheaper to fix by
labelling than by cutting*.

**Do not cut, despite looking cuttable.** The negative results in Supplement 5 (the
0.005-leverage knob, the four-condition screen) — they are the reusable contribution, and the
paper's own framing depends on the search having honestly failed.

---

## 6. Open items the argument depends on

- **A5's undecomposed width gradient.** A blind gate with matched correlation length has not
  been run. A5's claim survives without it (the frozen control is sufficient for *currency is
  necessary*), but the paper cannot say how much of the gradient is currency.
- **B3's seed count.** Two seeds per point, disagreeing. More seeds would either firm up "the
  parameters do not determine the winner" or reveal it as undersampling.
- **B3's width/horizon confound.** The width-1000 runs changed lattice width *and* duration
  together, so β = 8's dissolution there is unattributable. Controls at width 250 exist and
  are reported; the width-1000 result should not be leaned on.
- **σ is never varied.** Every dynamical result in B sits at one point of a 40,320-member
  design space, chosen by hand for unrecorded reasons.

---

## 7. Patterns governing this argument

`mmca/persistence-over-appearance` (B3, B4) · `mmca/instrument-horizon-must-match-system` (B4) ·
`mmca/perceivable-target` (B1, B5) · `mmca/screen-the-knob-before-the-controller` (Supp 5) ·
`mmca/realization-variance-is-not-a-regime` (B3) · `mmca/threshold-shaped-events` (B3's frozen
definition) · `mmca/eye-before-metric` (how B3's criterion was found) ·
`mmca/verified-but-idle-link` (Figure 1's withdrawn edge)
