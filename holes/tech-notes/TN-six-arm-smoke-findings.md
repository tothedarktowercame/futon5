# TN-six-arm-smoke-findings — the launch-authorizing smoke re-read from its artifacts

**Status: review, 2026-08-02, claude (Opus 5). No new experiment; all numbers
re-derived from the banked smoke.** Answers one question: *is it worth commissioning
the high-CPU Linode on the strength of this smoke?*

**Recommendation: not yet.** The smoke's only strong result is an artifact of the
fitness definition, and every artifact-free contrast fails the registered bar. Two
free steps in §5 change what the paid run would be testing.

Source: `mmca-clj/data/baldwin-runs/masking-six-arm-smoke-fc8bfca/` —
`STATUS.md`, `a/result.edn`, `a/raw.edn` (6,912 units), `smoke-receipt.edn`.
Implementation `fc8bfca`, Lean amendment `1445d9bd`, admission commit `ccd1c4d`.

Companions: [`TN-six-arm-review.md`](TN-six-arm-review.md),
[`TN-baldwin-experiments-status.md`](TN-baldwin-experiments-status.md),
[`TN-baldwin-experiment-guidance.md`](TN-baldwin-experiment-guidance.md).

## 1. The pipeline is faithful

The registered contrasts reproduce **exactly** from `a/raw.edn` under unit-level sign
majority within locus, on `fitness`. Two repetitions were byte-identical and the
positive control passed. Nothing below is a complaint about the implementation — the
problem is which quantity was registered as the readout.

## 2. The 16–0–0 results are an accounting identity, not a finding

`fitness = band − 0.05 × dependence`, verified against a raw unit
(`band 0.0`, `dependence 1.0`, `fitness -0.05`).

Mean `dependence` by arm:

| arm | dependence |
|---|---:|
| `:held-bad`, `:held-current`, `:held-good`, `:held-good-novel-tape` | **0.9875** |
| `:plastic-current`, `:plastic-good` | **1.0000** |

The difference is exactly **1/80**. The intervention holds *one locus out of eighty*,
and `plastic-dependence` is a fraction over unheld loci, so holding **any** locus —
good, bad or current — mechanically refunds 1/80 of the capacity charge and grants a
**constant +0.000625 fitness bonus to every unit, independent of behaviour**.

A constant offset applied uniformly wins every near-tie. That is what produces
16–0–0, and it produces it twice.

This is the defect `charge-for-realized-work-not-for-capacity` exists to prevent, and
obligation 2 of the revised library ("name the fitness or readout component that is a
function of realized adaptation, and register it") is what should have caught it. It
came back in because `fitness` was registered as the readout without checking that its
cost term is flat in behaviour.

## 3. The same contrasts, scored three ways

Re-derived from `a/raw.edn` with identical aggregation, varying only the field.
(The tape row pairs on `tape-slot`, since the novel arm uses tape ids 1001–1003.)

| contrast | `fitness` (registered) | `band` | `reach` |
|---|---|---|---|
| held-good vs plastic-good | **16-0-0** | 6-7-3 | **6-10-0** |
| held-current vs plastic-current | **16-0-0** | 6-7-3 | **6-9-1** |
| held-good vs held-current | 9-4-3 | 9-4-3 | 10-5-1 |
| held-good vs held-bad | 12-3-1 | 12-3-1 | 15-1-0 |
| plastic-good vs plastic-current | 11-4-1 | 11-4-1 | 11-5-0 |
| discovery vs novel tape (held-good) | 7-7-2 | 7-7-2 | 5-11-0 |

Two things to read off it.

**The holding effect is entirely in `fitness`.** On `band` it is a wash (6-7-3) and on
`reach` it **reverses** — plasticity beats holding 9–6 and 10–6. "Holding was strongly
beneficial" is the cost refund, not adaptive benefit.

**The both-held contrasts are identical under `fitness` and `band`.** Of course: both
arms are held, dependence cancels, so the cost term contributes nothing. Those rows are
the clean ones — and they are the ones that fail.

## 4. Against the registered bar

Production requires **14 wins with ≤2 losses** (`familywiseWin 14 2 = true`,
`13 3 = false`), over a primary family of five (`:primary-contrast-family-size 5`).

| contrast | registered result | verdict |
|---|---|---|
| held-good vs held-current | 9-4-3 | fails, and not narrowly |
| held-good vs held-bad | 12-3-1 | fails |
| plastic-good vs plastic-current | 11-4-1 | fails |
| held-good vs plastic-good | 16-0-0 | passes — but see §2 |

**The novel-tape arm is vacuous, not reassuring.** 7-7-2 tests whether a good-rule
advantage degrades on unseen tapes. But held-good beats held-current only 9-4-3, so
there is no advantage to degrade; a balanced result is what you would see either way.
Reporting "no indication of tape degradation" as support reads the vacuity as a pass.
(On `reach` it is 5-11-0 — the novel tape is *better*, which is not a degradation story
either.)

## 5. The nuance worth keeping

On `reach`, **held-good beats held-bad 15-1-0** while beating held-current only 10-5-1.

That is a real and useful signal: the endpoint map has genuine **discriminative**
validity — it can tell good rules from bad ones — but little **marginal** value over
the rules the genome already carries. Those are different claims, and only the first is
supported. It is also consistent with the whole `content-flat` line: the evolved genome
is already sitting near per-locus parity with the map's recommendations.

So the map is not worthless. It is just not obviously worth *evolving toward*, which is
what the paid search would spend money assuming.

## 6. What the paid confirmation would buy

- Confirming an accounting identity (§2) at higher power.
- Attempting to push 9–4 and 12–3 over a 14/16 bar. Confirmation adds environment seeds
  (`101–108` vs the smoke's `901–903`), which sharpens each locus but does not change
  the *number* of loci. The critical contrast needs 5 of its 7 non-wins converted, and
  the four losses are not rounding error — per-locus mean Δfitness runs −0.0437,
  −0.0281, −0.0259, −0.0019.

## 7. Two free steps first

1. **Re-score the banked smoke on `band` and `reach`** — done in §3, at zero cost. Both
   16–0–0 collapse. That table is the honest headline and should replace the current
   one in `STATUS.md`.
2. **Re-register the readout** so a held-vs-plastic comparison is not decided by a
   constant. Either score `band`/`reach` directly, or replace the capacity charge with
   one proportional to realized rewriting — `c/changed-count` already exists and
   `scripts/mutation_axis.clj` already computes churn with it. This is obligation 2,
   discharged properly this time.

Only then is the spend worth reconsidering, and it should be reconsidered against a
sharper question than the current one — most likely the positional/endpoint-density
hypothesis (Pearson `r = −0.647`, Spearman `ρ = −0.767`, endpoint totals by 20-locus
block `505, 461, 68, 28`), which the panel already stratifies for.

## 8. Provenance

**Derived by me from `a/raw.edn`:** the arm-level `dependence` table and the 1/80
identity; the `fitness = band − 0.05·dependence` decomposition; the full three-field
contrast table in §3; the per-locus loss magnitudes in §6. The registered
`fitness`-column values reproduce `a/result.edn` exactly, which is the check that the
re-derivation is sound.

**Taken on report:** the two-repetition byte-identity, the positive control, the gate
and validator results, and the positional-signal statistics in §7.

**Caveat, discharged.** §2 contradicts a launch authorization, so the `dependence`
semantics were checked at the source rather than inferred. `mmca/baldwin_selection.clj`
defines

> `dependence = update-prob * gamma * fraction of cells that are unheld and unmasked`

with the docstring noting this is "ONE operational definition, used for BOTH the
reported column and the cost term". With `update-prob` and `gamma` at 1 in these arms,
holding one locus of eighty moves the fraction by exactly 1/80 — which is precisely the
`1.0000 → 0.9875` observed in the artifacts. The identity in §2 is therefore confirmed
from both directions, definition and data.

Note the irony recorded in that same docstring: it exists because a *previous* round
had the reported quantity and the charged quantity diverge. The definition was
repaired; what remains unrepaired is that the charge is still a **capacity** cost, flat
in behaviour — so a held/plastic comparison on `fitness` is decided by it.
