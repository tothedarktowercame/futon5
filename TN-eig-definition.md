# TN-eig-definition — a properly defined epistemic term

**Date:** 2026-08-04
**Status:** design, not implemented
**Companion:** `TN-metaca-baldwin-micro-pilots.md` (findings this is built on)

---

## What the implemented term does, and why it cannot be patched

`corrected-local-eig` computes the Beta posterior variance of a *local confirmation rate*,
pooled over `holders` — neighbours already holding the candidate kind. It is:

- **maximal on zero evidence** (`ln 2` at Beta(1,1)) — loudest where least informed;
- **exotype-pooled** — the evidence set is defined by exotype identity alone;
- **anti-correlated with damage** — maximal where a kind is locally *absent*, and absence
  is commonest in frozen regions where nothing propagates, i.e. where an action teaches
  least (Joe's diagnostic, 2026-08-03).

Micro-pilot 7 closed off the repair route. Conditioning ambiguity on claim confirmation
does not displace chaos, because **chaos's claims are confirmed at every active
observation** — it is not confidently wrong, it is confidently right, since it produces
the observations that confirm it. Chaos is a *self-consistent fixed point* of the
objective. No reweighting or redefinition of terms scored on the exotype layer can
dislodge it.

**Consequence for design:** an epistemic term scored on the exotype layer cannot work.
It has to be scored on the layer that generates observations.

---

## The definition

**Epistemic value = expected divergence of one's own future under the candidate action.**

In active-inference terms the epistemic part of expected free energy is the mutual
information `I(s; o | π)` — how much the observations a policy generates tell you about
hidden state. Here the hidden state a cell cannot see is **its neighbours' genotype**: a
cell observes phenotype, not rules. So the question "how much would I learn by blending
with this neighbour?" has a precise answer — *how differently would I behave*, because
behaviour is the only channel through which a rule becomes observable.

That quantity is **damage**: divergence between the trajectory under one's own rule and the
trajectory under the candidate rule. Damage is already the harness's instrument, is
holistic across all three layers by construction, and is the classical edge-of-chaos
measure. **The epistemic drive and the EoC instrument become the same quantity** — which is
the structural claim this design rests on.

### First-order form (the implementable one)

Full divergence needs forward simulation per candidate — too costly per cell per step. The
first-order term is cheap and is the derivative of damage:

```
EIG(i, j) = Σ_{n ∈ neighbourhood patterns} w(n) · [ rule_i(n) ≠ blend(rule_i, rule_j)(n) ]
```

- `blend(rule_i, rule_j)` is the neighbour-agreement blend already implemented
  (`grid/blend-rule`, ported from the 2014 engine);
- `w(n)` is the locally observed frequency of the three-cell phenotype pattern `n`;
- the bracket is 1 where the blended rule would act differently from the cell's own.

In words: **how often would adopting this blend actually change what I do, given the
patterns I actually encounter?** It is a Hamming distance between rule tables, weighted by
which truth-table entries are live locally.

Cost is eight comparisons per candidate, and the whole thing is tabulatable —
`256 × 256` rule pairs × 8 patterns — so it can be precomputed.

---

## Pathology checks

The point of writing it this way is that the current term's failure modes become
structurally impossible rather than merely unlikely.

| situation | implemented EIG | this definition |
|---|---|---|
| candidate kind locally absent | **maximal** (no evidence) | irrelevant — kind counts never appear |
| frozen / uniform region | **maximal** | **zero** — few patterns live, and neighbours' rules agree |
| domain interior | maximal for every non-resident → defection | **zero** → interiors stable |
| boundary / heterogeneous region | low (many holders) | **high** → exploration where it informs |
| neighbour's rule identical to mine | maximal if kind differs | **zero** — nothing to learn |
| relation to damage | anti-correlated | first-order damage *by construction* |

The two decisive reversals: it is **zero in frozen regions** (nothing propagates, so nothing
is learnable) and **zero when the neighbour's rule would not change my behaviour** (no
divergence, no information). The implemented term is maximal in both.

---

## Where it enters, and the exotype's role

The exotype stops being the thing that transmits and becomes the **propensity to act on
epistemic value** — Joe's architecture, 2026-08-03:

```
blend?  :=  σ( κ(exotype_i) · EIG(i, j) )      ; epistemic appetite, per kind
source  :=  blend(rule_i, rule_j) if blend? else rule_i
result  :=  apply the exotype's propagator to source
```

`κ(exotype)` is the kind's epistemic gain. This is what makes the exotype layer
*load-bearing*: it modulates a quantity that is defined on the genotype/phenotype layers,
rather than being scored against itself. The current `:blend-strength β` is the degenerate
case `κ ≡ β`, `EIG ≡ 1` — a constant propensity, no epistemic content. **Slice 9/10 are
therefore the correct null model for this design**, which is convenient: the comparison is
already run and the mechanism already in place.

Three quantities collapse into one:

- **exotype** = propensity to transfer;
- **EIG** = expected divergence from transferring;
- **damage** = the measure of that divergence.

---

## Predictions, to be checked before implementing

1. **Against the β-constant null.** At matched mean blend rate, EIG-gated blending should
   give *more* genotype domain structure than constant β, because blending is suppressed
   inside domains (zero EIG) and concentrated at boundaries.
2. **Damage should rise in the phenotype layer without the genotype-noise trade** seen in
   the EIG-coefficient probe (μ=0.30, c=3: G 11.0 while P collapsed to 3.0). That trade was
   the signature of a term firing where it should have been silent.
3. **No refuge abolition.** The implemented term destroyed the single quiet
   low-diversity observation state; this one should leave it intact, since that state has
   near-zero EIG.
4. **Non-monotone in κ with an interior optimum** — at κ=0 no blending (frozen genotype),
   at κ→∞ blend whenever anything differs (confetti, the slice7/8 failure). If there is no
   interior optimum, the design is wrong.

Prediction 4 is the falsifier. It should be computed in the mean field *before* any
implementation, on the pattern that has twice now killed a proposal before it cost a run.

---

## What is NOT claimed

- That this makes the exotype objective correct. It does not touch it; chaos remains a
  self-consistent fixed point of `pattern-score`. This design routes *around* that by
  acting on the observation-generating layer.
- That first-order divergence equals damage. It is the derivative; the relationship to
  multi-step damage is an assumption to be tested, not a result.
- That `w(n)` is well estimated from a three-cell neighbourhood. It is a crude local
  histogram, and how crude is an open question.
- Any performance claim. Tabulation is asserted, not measured.

---

## FALSIFIED (2026-08-04, micro-pilots 8-9) — before implementation

Both testable predictions fail. Reduced model: 80-cell ring, per-cell 8-bit rule,
ECA phenotype step, neighbour-agreement blend gated by `1 - exp(-kappa * EIG)`.
Propagator omitted (per-cell bijection, no spatial coupling, cannot create or destroy the
structure measured). Artifacts: `analysis/microtest5_kappa_optimum.py`,
`analysis/microtest6_eig_vs_constant.py`.

### Prediction 4 (interior optimum in kappa) — FAILS

Mean genotype domain length, 12 seeds:

| kappa | mean | sd |
|---:|---:|---:|
| 0.5 | 3.001 | 0.555 |
| 8 | 3.143 | 0.429 |
| 64 | 3.144 | 0.664 |
| 1000 | 2.755 | 0.648 |

kappa=64 minus kappa=0.5 is **+0.143 against a pooled sd of 0.612 — 0.23 sd**. Flat.
The only real transition is 0 -> nonzero (1.000 -> ~3.0), which says "blending at all
creates structure", not "there is an epistemic sweet spot". A first 4-seed pass reported
"INTERIOR OPTIMUM: YES" by taking the argmax of noisy values — the same error this note
elsewhere criticises in others' results, committed by its own script.

### Prediction 1 (beats constant-beta at matched blend rate) — FAILS

| kappa | blend rate | EIG-gated | matched constant | difference |
|---:|---:|---:|---:|---:|
| 1 | 0.119 | 3.009 | 3.161 | −0.152 (−0.29 sd) |
| 8 | 0.753 | 3.153 | 3.285 | −0.132 (−0.27 sd) |
| 64 | 0.991 | 3.128 | 3.506 | −0.378 (−0.60 sd) |

EIG-gating is *worse* than a constant blend probability at every kappa — within noise
individually, but negative three times out of three.

### Why, and what it teaches

**The blend operator already contains the epistemic mechanism.** Where left, centre and
right agree, every bit agrees, so `blend_rule` returns the centre rule unchanged: the
operation is the identity in a uniform neighbourhood and acts only where neighbours
disagree. That *is* "change only where you would behave differently" — the epistemic
principle, implemented structurally rather than as a scored term.

So the EIG gate was gating something already gated, and paid for the privilege. This
retrospectively explains why constant-beta blend produced domains in slice9/slice10 with no
epistemic machinery: the 2014 agreement rule was doing the epistemic work implicitly.

**What survives.** The *notion* — epistemic value as divergence of one's own future, which
damage measures, holistic across layers — is unaffected and remains the right reading of
Joe's diagnostic that a real EIG should correlate with damage. What is refuted is the
specific proposal to add it as a multiplicative gate on top of agreement-blending.

**What is NOT tested.** The reduced model omits the propagator and the exotype layer
entirely, measures only genotype run length at one width and horizon, and uses a
lattice-global `w(n)` rather than a per-cell histogram. A gate that modulated something
other than blend probability — or an exotype-conditioned `kappa` varying across space,
which this test holds uniform — is untouched by this result.
