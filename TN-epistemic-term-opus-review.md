# TN-epistemic-term-opus-review — the binding constraint is `risk`, and its target is below the mechanism's floor

**Status:** REVIEW, 2026-08-04. Written by Claude Fable 5 in answer to
`TN-epistemic-term-evaluation.md` §8 (*"Is E1 → E2 → E3 the right ladder... If the framing in
§2 is wrong, that is the most valuable thing to say, and it should be said first"*).

**Companions:** `TN-epistemic-term-evaluation.md` (the note under review),
`TN-baldwin-reboot.md` §42 (the Search register), §44–46 (the dark-room reading and the EIG
history), `TN-eig-definition.md`.

**Bottom line:** §2's diagnosis is right about the *sign* and names the *second* stage.
Ambiguity breaks the final tie, but it never chooses from the full vocabulary, because `risk`
has already funnelled the system into the halting-capable class. It does that because
**`rate ≥ 0.5` for every σ ∈ S₈ while the risk target is 0.15** — the preference is
unsatisfiable, which converts `risk` into a monotone penalty on `fix(σ)`, and `fix(σ) = 0` is
exactly the necessary condition for stationary rules to exist. Adding an epistemic term to
ambiguity does not touch that funnel. Two further findings: the rule layer is exactly a system
of annihilating random walkers, which upgrades "never halted in 400/400" to a theorem; and the
12-kind vocabulary is missing the two cycle types that would break its central confound.

---

## 1. The binding constraint is `risk`, and its target is unreachable

`gen/rule-permute` writes `¬bit[k]` into position σ(k), so the byte changes iff
`bit[σ(k)] == bit[k]`. Hence

```
rate(σ) = 0.5 + fix(σ)/16
```

verified by brute force over all 256 bytes × 8 positions for twelve cycle types; it agrees
with `gen/rule-change-rate` to the last digit in every case. **The minimum achievable rate
over all of S₈ is therefore 0.5000.**

`selection/preference-targets` is `{:rule-change 0.15 :hunger 0.05}`, and
`efe/score-policy` computes `risk = bernoulli-kl(predicted rule-change ‖ 0.15)`. The target
lies below the mechanism's floor by a factor of 3.33. Three consequences, all structural:

1. **`risk` is pinned at `KL(0.5 ‖ 0.15) = 0.3367`** for every fix = 0 policy. That is the
   `0.337` in every row of the note's §2.1 table and of `TN-baldwin-reboot.md` §44.2. It is
   not a coincidence of those four policies; it is the global minimum of the term.
2. **`risk` is a strictly increasing function of `fix(σ)`**, so minimising risk *is*
   minimising fix:

   | fix(σ) | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
   |---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
   | rate | 0.5000 | 0.5625 | 0.6250 | 0.6875 | 0.7500 | 0.8125 | 0.8750 | — | 1.0000 |
   | risk | **0.3367** | 0.4529 | 0.5851 | 0.7340 | 0.9011 | 1.0893 | 1.3035 | — | 1.8971 |

   (fix = 7 is unrealisable — see §4. `KL(1.0 ‖ 0.15) = ln(1/0.15)` under the 0·log 0 = 0
   convention.)
3. **`fix(σ) = 0` is the necessary condition for stationary rules to exist.** So the
   objective's only *targeted* term is, by construction, an instruction to choose a policy
   capable of halting.

### 1.1 Why this reorders the diagnosis

The risk spread across the 12-kind vocabulary is **1.56 nats**. The measured ambiguity spread
is **0.31** (2.101 for `:even1` to 2.406 for `:even4`). A kind at fix ≥ 3 pays ≥ +0.40 on risk
and cannot be rescued on ambiguity at any observation. So the objective is a **two-stage
funnel**:

> **Stage 1 — `risk` eliminates every policy with fixed points**, narrowing 12 kinds to the
> seven fix = 0 cycle types, of which five halt and two cannot.
> **Stage 2 — `ambiguity` chooses among the survivors**, and prefers the ones that halt.

`TN-baldwin-reboot.md` §44.2 concludes *"risk cannot distinguish these kinds at all"*. That is
true **within** fix = 0 and misleading as a general statement: risk is what put the system in
fix = 0. The §44.2 table shows all candidates at rate 0.5000 because the vocabulary sweep had
already been funnelled there — the table is a picture of stage 2 with stage 1 invisible
because it has already run.

**Bearing on the note's §6.** §6 rules out adding an epistemic term to the policy-selection
score, on §4's second finding. That ruling is untouched by this. But the *converse* also
holds and is new: even if the term were added, it would compete inside a candidate set that
risk has already restricted to five halting types plus two non-halting ones. **The
epistemic-term question and the halting question are less coupled than the note assumes.**

---

## 2. The rule layer is annihilating random walkers — the 400/400 is a theorem

An update at position *k* touches only positions *k* and σ(*k*), which lie in the same cycle
of σ. So at β = 0 a rule byte decomposes into independent words, one per cycle of σ.

On a cycle, write `e_k = bit[k] XOR bit[σ(k)]` and call *k* **violated** when `e_k = 0` (which
is exactly the condition under which the propagator changes the byte). Then:

- an update at an unviolated *k* is a **no-op on the entire byte**;
- an update at a violated *k* clears the violation at *k* and toggles σ(*k*).

That is: **violations are walkers that hop one step along the cycle and annihilate in pairs.**
A fixed point of σ is a self-loop, so its walker hops to itself and is permanent — which is
the same fact as "a fixed point is an unconditional flip", read in the walker coordinate.

Verified exhaustively over random (byte, k) pairs for cycle types (8), (6,2), (4,4), (4,2,2),
(2,2,2,2), (5,3), (4,3,1) — 20,000 trials each, zero violations of the rule.

### 2.1 The conservation law

Around a cycle of length *L* the XORs telescope, so the number of violations on that cycle is
**congruent to *L* mod 2, always**, and the walker dynamics (hop, pairwise annihilate)
preserves that parity. Checked over all 256 bytes for (8), (6,2), (5,3) and (4,3,1).

> **An odd cycle in σ makes the absorbing set unreachable, not merely empty.**

So `(5,3)` is not "never halted in 400/400 runs" — it is **cannot halt, from any initial rule,
ever**. This is strictly stronger than T1 (`hasAlternatingColouring_iff_cycleType_even`), which
establishes that the absorbing set is *empty* when a cycle is odd. The dynamical statement —
that the conserved parity blocks approach to it — is what makes the 400/400 a certainty rather
than a strong empirical regularity. It is a candidate Lean target and would need the
non-triviality leg of the closure gate.

### 2.2 The coordinate H1/§15 asked for

`TN-baldwin-reboot.md` §15 records Joe's complaint that the exotype layer has no coordinates —
*"in the genotype layer we have 256 rules, 256 colours... in the exotype we have no such
visibility."* The walker reduction supplies one: **the per-cycle violation counts**. The state
of a rule under σ is `⊕_c (cycle of length L_c carrying V_c walkers)`, with `V_c ≡ L_c mod 2`
conserved. That is finer than four named kinds, computable in closed form, and it is the
coordinate in which the dynamics is actually simple.

### 2.3 The ring contributes nothing at β = 0

Simulating a **single cell's** chain, the median steps to reach a stationary rule are 5, 9, 19,
36 for cycle types (2,2,2,2), (4,2,2), (6,2), (8) — reproducing the note's §1 ring-level
half-times of 5, 10, 20, 40 (400 seeds, median). At β = 0 the ring's half-time *is* the
single-cell chain's halting time; the ring plays no part.

---

## 3. The vocabulary is missing the two cycle types that would break its confound

`grid.clj:68` states *"The five ALL-EVEN cycle types are the entire immune-byte axis... a
five-point dose-response the model cannot see."* **The vocabulary instantiates four of the
five.** 4000 seeds per cycle type, single-cell chain:

| cycle type | kind | immune bytes | max cycle | mean halt time |
|---|---|---:|---:|---:|
| (2,2,2,2) | `:even4` | 16 | 2 | 5.6 ± 0.06 |
| (4,2,2) | `:even8` | 8 | 4 | 13.1 ± 0.21 |
| (6,2) | `:collapser` | 4 | 6 | 29.1 ± 0.47 |
| **(4,4)** | **absent** | **4** | **4** | **18.6 ± 0.24** |
| (8) | `:even1` | 2 | 8 | 52.1 ± 0.82 |

**The confound:** among the four instantiated kinds, immune-byte count and maximum cycle length
are perfectly rank-correlated (16,8,4,2 against 2,4,6,8). No measurement on the current
vocabulary can say which one drives an outcome. **(4,4) breaks it** — same fix, same rate, same
immune-byte count as `:collapser`, halting time separated by **20 σ**. It is one line to add.

This also explains why "half-time = 80 / 2^(#cycles)" looks so clean on the four points and
should not be trusted: with (4,4) absent, the absorbing-state density and the cycle length are
collinear, and (4,4) sits off that line.

**The non-halting side has the same gap.** The fix = 0 cycle types are:

| cycle type | immune | halts? | in vocabulary |
|---|---:|---|---|
| (2,2,2,2) | 16 | yes | `:even4` |
| (4,2,2) | 8 | yes | `:even8` |
| (6,2) | 4 | yes | `:collapser` |
| (4,4) | 4 | yes | **NO** |
| (8) | 2 | yes | `:even1` |
| (5,3) | 0 | **no** | `:odd53` |
| (3,3,2) | 0 | **no** | **NO** |

Seven cycle types sit at the risk floor; five halt, two cannot. The vocabulary has four of the
five and **one of the two**. Every claim of the form "the objective rejects the non-freezing
kind" therefore rests on a single σ with no control. `(3,3,2)` is the matched control: same
fix, same rate, same risk, also unable to halt, three cycles instead of two.

**Recommendation (mechanical, no design decision):** add `(4,4)` and `(3,3,2)` to
`grid/propagators`. It converts a collinear four-point series into a genuine dose-response and
gives `:odd53` a replicate.

---

## 4. Corrections to the note as written

The note is explicitly self-contained for a reader with no prior context, so these are the
places an outside reviewer would be misled.

1. **§1 step 2 mis-describes its own mechanism, twice.**
   - *"for each truth-table position k"* — it is **one uniformly chosen k per application**.
     Under the "for each" reading the change rate would be near 1 for almost every byte,
     contradicting the rate formula two paragraphs later.
   - *"then bit k is flipped"* — the write goes to position **σ(k)**, not *k*. The change
     *condition* is the same either way, but the dynamics is the mirror image: walkers hop
     backwards along σ⁻¹. A reviewer reimplementing from this spec builds a different system.
2. **"takes only nine values" — it takes eight.** A permutation of 8 cannot have exactly 7
   fixed points (that needs a derangement of one element), so rate 0.9375 is a value the
   formula permits and no σ realises. Achievable fix ∈ {0,1,2,3,4,5,6,8}. `grid.clj:81` says
   "nine notches" and then "fix=7 is impossible" in the same comment.
3. **"A rule is stationary under σ iff every cycle of σ is even"** is a type error as stated —
   the left-hand side depends on the rule, the right-hand side does not. It should read
   "stationary rules **exist** iff every cycle of σ is even."

None of these change a result. All three are the shape G3 exists to catch.

---

## 5. The β interior optimum rests on three points, one of them degenerate

From `TN-baldwin-reboot.md` §35: damage at β = 0.75 is **exactly 0.0 from t = 50 onward** —
the perturbation is erased, which is the rule field reaching consensus, not a graded decline.
An interior optimum read off {rise, rise, exact zero} is under-determined. A peak needs points
*between* 0.5 and 0.75, which is precisely S1's "finer blend grid around 0.5".

**And β is doing two jobs, not one.** `blend-rule` (`grid.clj:155`) retains the neighbours' bit
where they agree, and where they disagree evaluates `centre-rule` on the triple
`(left-bit, centre-bit, right-bit)` — a lookup into a *different* position of the centre byte.
So blend is the **only operator in the genotype layer that mixes bit positions across σ's
cycles**. At β = 0 the layer cannot spread damage at all, which is why blend 0 measures ~1.0
(the injected cell and nothing else) — now explained structurally rather than empirically.

`TN-epistemic-term-evaluation.md` §4 and `TN-baldwin-reboot.md` §46.1 read the coupling
operator as already implementing the epistemic principle ("change only where you would behave
differently"). That reading covers the *spatial* job. It does not cover the *position-mixing*
job, which is the layer's only ergodicity source and is not epistemic at all. **So the interior
optimum in β may be about ergodicity rather than about epistemic gating** — a live alternative
to §5.3's reading, testable by giving the position-mixing job to an operator with no spatial
component and seeing whether the optimum survives.

---

## 6. The ladder

**E1 is not the right first rung, and E3 is already registered.**

### E0 — score the vocabulary with a reachable risk target *(new, minutes, no mechanism)*

Re-run the pure-function argmin over the 12 kinds with the risk target moved inside the
achievable range, and report the risk and ambiguity spreads side by side.

- *Acceptance:* the argmin distribution under a reachable target, plus `range(risk)` and
  `range(ambiguity)` over all 12 kinds at each of the 9 observations.
- *Falsifier / decision:* if the winner changes, the halting outcome was a mis-specified
  preference and the dark-room framing is largely beside the point. If it does not, ambiguity
  is confirmed load-bearing and E1–E3 are attacking the binding constraint — which is currently
  **assumed, not shown**.
- *Why first:* it is the cheapest item on either register and it decides whether the rest of
  the ladder is aimed at the right term.

**The natural repair is rate modulation.** Applying the propagator with probability *p* gives
`rate = p·(0.5 + fix/16)`, which makes 0.15 reachable. That is precisely N2b's invariant (a) —
*"τ may modulate the RATE of variation; it must never modulate its DIRECTION"*
(`TN-baldwin-reboot.md` §14.2). The unsatisfiable-preference defect and the endogenous clock
converge on the same knob, which is an argument for the clock that does not depend on the
edge-of-chaos story at all.

### E2 before E1

E2 measures a real quantity in the real substrate and its falsifier retires a term. E1 re-runs
a comparison in a reduced model that §5.2 concedes *"cannot speak to the policy selector in
either direction"* — so a **win** for the gate licenses nothing about the full system, and only
the loss is informative. That asymmetry makes E1 **bookkeeping** — closing the historical
refutation on the observable the definition names, which is worth doing — rather than a rung.

### E3 ≡ S1 + S2 + S3

`TN-baldwin-reboot.md` §42 already carries integrated autocorrelation time (S1), scaled
variance (S2) and multiple-width finite-size scaling as an acceptance condition (S3), each with
a falsifier. E3 restates them without the id. The context-free framing was deliberate and
correct for getting an unbiased outside read, but it cost the register linkage — a mild
instance of the N3 failure mode the parent document is about.

### Revised order

| rung | item | cost | why here |
|---|---|---|---|
| **E0** | Reachable risk target; risk vs ambiguity spread over all 12 kinds | minutes | decides whether the rest is aimed at the binding term |
| **E0b** | Add `(4,4)` and `(3,3,2)` to `grid/propagators` | minutes | breaks the immune-count / cycle-length confound; gives `:odd53` a control |
| **E2** | Correlate `corrected-local-eig` against measured local damage | hours | real substrate; falsifier retires a term |
| **E1** | Re-score micro-pilot 9 on damage | hours | closes the historical refutation properly; low information either way |
| **E3** | → run as **S1/S2/S3**, under their existing acceptance bars | medium | do not open a duplicate item |

---

## 7. Verified vs inferred

| claim | status |
|---|---|
| `rate = 0.5 + fix(σ)/16`; minimum over S₈ is 0.5000 | **verified** — brute force, all 256 bytes × 8 positions, 12 cycle types; matches `gen/rule-change-rate` exactly |
| risk target 0.15 is below the mechanism's floor; `KL(0.5‖0.15) = 0.3367` | **verified** — read `selection/preference-targets` + `efe/score-policy`; arithmetic reproduces the 0.337 in both notes |
| risk is strictly increasing in `fix(σ)`; spread 1.56 nats | **verified** — closed form |
| the update at *k* touches only *k* and σ(*k*); violations are hopping, pairwise-annihilating walkers | **verified** — exhaustive over 20,000 random (byte, k) pairs × 7 cycle types |
| violation count per cycle ≡ *L* mod 2, conserved ⇒ odd cycle can never halt | **verified** — all 256 bytes, 4 cycle types; walker rule preserves parity by construction |
| (4,4) halts faster than (6,2) at equal fix, rate and immune count | **measured** — 4000 seeds, 18.6 ± 0.24 vs 29.1 ± 0.47, 20σ |
| (4,4) and (3,3,2) are absent from `grid/propagators` | **verified** — read `grid.clj:53–91` |
| fix = 7 is unrealisable ⇒ eight achievable rates, not nine | **verified** — enumerated all 8! permutations |
| `blend-rule` mixes bit positions across σ's cycles | **verified** — read `grid.clj:155–170` |
| single-cell halting time reproduces the ring half-time at β = 0 | **measured** — 5/9/19/36 against the note's 5/10/20/40 |
| **ambiguity's spread across all 12 kinds stays near the 0.31 measured over four** | **ASSUMED** — this is what §1.1's "risk dominates" rests on, and it is the first thing E0 should print |
| the β interior optimum may be about ergodicity rather than epistemic gating | **inferred** — plausible and untested; §5's proposed control would settle it |

---

## 8. Reproduction

Source read: `src/futon5/xenotype/generator.clj` (`rule-permute`, `sigma-positional`,
`rule-change-rate`), `src/futon5/exotype/efe.clj` (`score-policy`),
`src/futon5/exotype/selection.clj` (`preference-targets`), `src/futon5/exotype/grid.clj`
(`propagators`, `blend-rule`). Documents read: `TN-epistemic-term-evaluation.md` in full;
`TN-baldwin-reboot.md` §1–17, §35, §41–47.

Verification scripts (throwaway, Python, no repo dependency):
`rule_layer.py` — rate formula vs brute force, stationary-byte counts, the walker reduction,
the parity invariant, halting times, the risk table.
`confound.py` — the all-even dose-response at 4000 seeds, the (4,4)/(6,2) separation, the
achievable-fix enumeration, the fix = 0 cycle-type census.

Both under
`/tmp/claude-1000/-home-joe-code/aeb729a4-613c-4abf-a5fb-a41e0a8149cf/scratchpad/` — a
session-scoped directory that will not survive, so relocate them if they are wanted. Neither
touches the repo and neither is a gate. If any
of these findings is adopted, the measurements belong in `futon5.exotype.invariants-test`
alongside the existing floor and rate pins, where they cannot drift.

**No repo file was modified by this review, and nothing was added to either register.**
