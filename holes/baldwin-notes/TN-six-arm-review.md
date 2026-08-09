# TN-six-arm-review — review of the shared-tape stop and the six-arm hold

**Status: review, 2026-08-02, claude (Opus 5). No new measurement.** Reviews the
preregistered shared-tape context diagnostic (`:no-shared-tape-context-gain`) and the
decision to hold the six-arm masking study. The headline conclusion — don't spend money
yet — is endorsed. The reasoning offered for it is not the reasoning I would record, and
the hold as currently specified blocks the wrong experiment.

Companions:

- [`TN-baldwin-experiments-status.md`](TN-baldwin-experiments-status.md) — the evidence
  ledger and the `## Review` section (R1--R10);
- [`TN-baldwin-experiment-guidance.md`](TN-baldwin-experiment-guidance.md) — the four
  obligations and the experiment order this note revises;
- `mathlib4/DarkTower/BaldwinSharedTapeContextPreregistration.lean` (`748eecc7`),
  `BaldwinMaskingSixArmPreregistration.lean` (`47d00afe`),
  `BaldwinMaskingInterventionPreregistration.lean` (the panel);
- `mmca-clj/data/baldwin-runs/shared-tape-context-20260802/STATUS.md` (`0c27fab`).

## 1. What the diagnostic established

| Cell | Stable contexts | Captured | Total | Capture |
|---|---:|---:|---:|---:|
| Variable rewrite tape | 4 / 16 | 7,500 | 37,440 | 2,003 bp |
| Shared rewrite tape | 3 / 16 | 5,637 | 37,440 | 1,505 bp |

The run is clean. Two independent extractions were byte-identical
(`661b380d…cac39b`), the fixed-`p0`/shared-tape apparatus control reproduced exactly, and
the estimand change was declared rather than slipped in (the older 14% was an unweighted
mean modal share; this is a captured-observation rate with numerator and denominator
retained). Not provisioning the Linode was the right call.

**The denominators are equal** — 37,440 in both cells. That matters, because the first
way this result could have been a false negative is the shared arm having fewer effective
observations and so less power to clear a stability threshold. It doesn't. The comparison
is on honest footing.

**Presentational caveat.** Reporting "the gate required at least 4 stable contexts and
2,500 bp" as the headline invites the reading that the shared tape underperformed an
achievable bar. It did not: **the variable-tape baseline scores 4/16 and 2,003 bp, so it
fails that same absolute bar.** The absolute limb could only ever fail, whichever arm ran.
What actually carries the classification is the *relative* limb — "both stable-context
count and capture must increase" — which is correctly designed and did its job. The
finding should be stated on that limb, or a reader will later mistake 2,003-vs-2,500 for
the result.

## 2. Two reservations before "the coordinate is dead"

### 2.1 The diagnostic is n = 1 in the dimension it ablates

One shared rewrite seed (`20260802`). Whatever idiosyncratic structure that single draw
carries was imposed on all eight environment seeds; the variable arm samples eight tapes,
the shared arm samples one.

"Two independent extractions were byte-identical" establishes **determinism of the
pipeline**, not **reproducibility across tape draws**. Those are different claims, and the
distinction is the one the ledger already insists on elsewhere (reconstruct ≠ reproduce).
It is also obligation 4 turned on the diagnostic itself: reproducibility has to be
demonstrated across the very thing being held fixed.

**Cheap closure, no new apparatus: re-run the shared arm at three further tape seeds.**
If it lands near 3/16 each time, the finding hardens and this objection is withdrawn. If
it swings, a single draw cannot carry a preregistered gate.

### 2.2 The direction is anomalous, and the two readings differ in kind

Removing a source of variation *reduced* cross-seed agreement (4 → 3 contexts,
2,003 → 1,505 bp). Naively that should not happen. Two mechanisms fit:

- **Marginalization artifact.** Under independent tapes, uncorrelated noise averages out
  across seeds and lets a weak central tendency win the mode. A shared tape correlates
  that noise across seeds and removes the averaging benefit, exposing that context alone
  does not determine the rule. On this reading the negative is *stronger* than recorded —
  the 4/16 under variable tape was itself partly spurious.
- **Idiosyncratic tape.** That one draw happens to scramble the context→rule association
  harder than a typical tape. On this reading the comparison is void.

§2.1 distinguishes them, and nothing else does. Until it is run, the defensible statement
is "no gain, direction unexplained" — not "the coordinate is wrong."

## 3. The six-arm study is gated on the wrong prerequisite

This is the operative finding of this review.

`BaldwinMaskingSixArmPreregistration.provenance` binds
`sharedTapeContextPrerequisitePassed = true` (`:85`, `:90`). But the study's six contrasts
are `goodHeldVsCurrentHeld`, `goodHeldVsBadHeld`, `goodHeldVsPlasticGood`,
`plasticGoodVsPlasticCurrent`, `discoveryHeldGoodVsNovelHeldGood` and
`heldCurrentVsPlasticCurrent` — **none of which uses the four-bit context coordinate.**

Verified directly: the only occurrences of "context" anywhere in
`BaldwinMaskingSixArmPreregistration.lean` are the import (`:6`), the `Trace` field
(`:54`), and the provenance flag that binds it (`:78`, `:85`, `:90`). Zero arms consume
it. The panel the arms actually run on is
`PanelEntry ⟨stratum, locus, currentRule, goodRule, badRule, hammingDistance⟩` — purely
locus- and rule-indexed, checksummed against the discovery map, and already bound in the
same flag via `discoveryRevision` and `discoveryMapSha256`, which hold.

The context re-indexing was an **alternative coordinate to try**, not a precondition for
testing the existing one. Gating the whole study on it discards arms that are informative
under either outcome — in particular `heldCurrentVsPlasticCurrent` and
`goodHeldVsPlasticGood`, the masking arms. "Does plasticity mask inherited content?" is a
mechanism claim that does not require a stationary target in any coordinate; it is the
claim the whole `content-flat-useful-endpoint` diagnosis rests on, and it is still
untested.

**Recommendation: amend the preregistration explicitly** — record the reasoning, date it,
and bind each arm to what that arm actually needs — then run the masking arms. Do not
silently proceed past the flag and do not quietly delete it. An amendment with a stated
rationale is legitimate practice; ignoring a live preregistration is the thing this whole
apparatus exists to prevent.

## 4. Where the invariant probably lives — and the held study already tests it

The strongest invariance signal in the banked data is not contextual, it is **positional**.
From the mechanism diagnostic: 1,062 useful held endpoints (5.19% of 20,480 locus×rule
combinations), present at 67 of 80 loci, with locus 1 carrying 86 useful rules while
several late loci carry none.

That heterogeneity is large, systematic, and indexed by **locus position**, not by the
instantaneous four-bit context. The candidate heritable unit is therefore a positional
band — a small evolvable table over locus-index classes — rather than 80 independent
per-cell alleles. This is the same prescription
`choose-the-heritable-unit-where-invariance-lives` already gives ("re-index the invariance
measurement you already have"); the context quadruple was one guess, and the endpoint map
is a second, already-banked one that costs nothing to interrogate.

**And the six-arm panel is already that experiment.** Its stratum type is
`earlyDense | middleDense | middleSparse | lateSparse`, and the registered panel is a
balanced 4 × 4 = 16 loci across those four strata — a crossing of *position* with
*endpoint density*. So the study currently on hold is the one that would test where the
invariant heritable unit lives, and it is being held because a **different** coordinate's
diagnostic failed. That is the clearest statement of why the dependency in §3 is
mis-specified.

Two banked numbers corroborate the underlying diagnosis rather than the coordinate:
only 5 of the genome's 80 current alleles are useful when held (6.25%, against a 5.19%
base rate — barely better than chance), yet the median nearest useful rule is 2 bits away
and 27 of 67 endpoint-bearing loci have one within Hamming distance ≤ 1. **The obstacle is
not distance, it is gradient** — which is exactly R5/R6, now independently corroborated
from a different artifact.

## 5. Recommended order

| # | step | cost | what it decides |
|---|---|---|---|
| 1 | Re-run the shared-tape arm at 3 further tape seeds | trivial, banked apparatus | whether §2 voids the diagnostic or hardens it |
| 2 | Regress useful-endpoint density on locus index | **zero compute**, banked map | whether the positional coordinate is real before spending on it |
| 3 | Amend the six-arm prereg per §3; bind each arm to what it needs | none | unblocks the masking arms |
| 4 | Run the masking arms (`heldCurrentVsPlasticCurrent`, `goodHeldVsPlasticGood`) | cheap subset | does plasticity mask inherited content — the untested load-bearing claim |
| 5 | Remaining arms, incl. the novel-tape overfitting arm | paid | endpoint generalization and jointness |

Steps 1 and 2 are both effectively free and both change what step 3 should say. Neither
has been run.

## 6. Operational flag

The Agency invocation default was raised 30 → 60 minutes (`3b7a568e`, `8e3fa04c`), but
**the running JVM still enforces 30 until it is restarted out-of-band.** Any Baldwin run
dispatched through Agency before that restart is still on the 30-minute cap — which is
precisely the silent-kill mode the park deadline exists to catch. Pin any long run to that
constraint, or restart out-of-band first. Do not assume the committed default is the live
default.

## 7. Provenance of this review

**Verified directly by me:** the `Trace`/`provenance` structure and the absence of any
context reference in the six-arm arms; the `Stratum` type and the balanced 4 × 4 panel
composition; the `PanelEntry` shape; the STATUS.md cell counts, denominators and gate
wording; the arithmetic in §1 and §4.

**Taken on report, not independently re-derived:** the mechanism-diagnostic figures quoted
in §4 (1,062 endpoints, 67/80 loci, locus 1 = 86, median 2 bits, 27/67 at Hamming ≤ 1,
5/80 currently useful) and the test/lint/axiom-audit results for the two Lean
preregistrations. If any of §4's conclusions become load-bearing for a paid run, re-derive
those from `assimilation-map.tsv` and `allele-sensitivity.edn` first.
