# Review of TN-epistemic-term-evaluation — Fable, 2026-08-04

**Living document.** §1 (the §8 adjudication) was written before any code was run.
E1 results are appended in §2 as they land. If this file ends mid-section, the run
was interrupted; everything above the break stands.

Reviewer basis: the technote itself, plus a close read of
`/home/joe/code/futon5/analysis/microtest5_kappa_optimum.py` and
`/home/joe/code/futon5/analysis/microtest6_eig_vs_constant.py`. No prior context
with this codebase.

---

## 1. Adjudication of §8

### 1.1 Is §2's framing correct? Yes — and it understates its own case.

§2 claims the implemented objective is not an expected free energy because (i) its
`ambiguity` is the summed marginal predictive entropy Σ H[Q(o_i)] rather than the
expected likelihood entropy E_Q(s)[H[P(o|s)]], and (ii) with no latent layer there is
no information-gain term anywhere in the score. Both claims are correct. But the
relationship between the two quantities is tighter than "one is not the other", and
making it explicit turns §2's judgement into a small theorem.

For any generative model with states s and likelihood P(o|s), with predictive
marginal Q(o) = Σ_s Q(s) P(o|s):

```
H[Q(o)]  =  E_Q(s)[ H[P(o|s)] ]  +  I(s; o)
(marginal    (ambiguity)            (epistemic value /
 predictive                          expected information gain)
 entropy)
```

This is an identity, not an approximation. So substituting marginal predictive
entropy for ambiguity in G does not merely *omit* the epistemic term — it *includes
it with inverted sign*:

```
G_implemented  =  risk + H[Q(o)] + λ·pref
               =  risk + ambiguity + I(s;o) + λ·pref
               =  G_EFE + I(s;o)            (holding the risk term fixed)
```

A minimizer of the implemented objective is charged a penalty exactly equal to the
expected information gain. It is not epistemically neutral; it is epistemically
*averse*. In the degenerate no-latent reading (identify s with the next observation,
identity likelihood), ambiguity is exactly 0 and the entire entropy term is the
sign-flipped epistemic value — a pure anti-exploration term. §2's sentence
"minimising Σ H[Q(o)] rewards determinism — the opposite behavioural sign to an
epistemic drive" is therefore not a reading of the behaviour; it is provable from
the decomposition.

Three secondary sharpenings:

1. **The risk term is also missing its entropy.** In the standard risk–ambiguity
   form, risk = KL(Q(o|π) ‖ C) = −H[Q(o)] − E_Q[ln C(o)]: predictive entropy enters
   true EFE with a *negative* sign inside risk (this is where keep-options-open
   pressure lives), and the two entropy appearances cancel against ambiguity to
   leave G_EFE = −E_Q[ln C(o)] − I(s;o). The implemented risk is a KL between a
   *point statistic* (predicted rule-change rate) and a target scalar, which carries
   no −H[Q(o)]. So predictive entropy appears in the implemented score only with
   the wrong sign, in the one place EFE forbids it.

2. **Σ over channels of marginal entropies ≥ joint entropy.** The channel-summed
   form additionally penalizes inter-channel correlation. Minor relative to the
   sign flip, but it means even "predictability" is being scored in a
   correlation-blind way.

3. **One overreach in §2's remedy space (not its conclusion).** "No latent layer,
   therefore no information gain anywhere in the scored objective" is true as a
   statement about the score, but latent states are not *required* for a principled
   epistemic term. The transition table P(o'|o,π) is estimated from finite data
   (28,620 transitions), so a standard parameter-novelty term (expected reduction
   in Dirichlet/Beta posterior uncertainty over table rows) is available with no
   new latent machinery. Notably, §6/E2 says the *implemented-but-unscored*
   epistemic quantity is exactly a Beta-posterior variance — i.e. the codebase
   already contains a novelty-shaped term; it just isn't in G. This matters for
   what "repair" would mean, and it interacts with §1.2 below.

**Is there any defensible reading under which the objective IS an EFE?** The
closest defense: declare the process fully observed (o *is* the state), whereupon
EFE degenerates to risk alone and the epistemic term is legitimately zero — and
then read +Σ H[Q(o)] as a deliberate determinism prior. That is a coherent
objective; it is KL-control / risk-sensitive control with an entropy penalty, and
such objectives are used on purpose in settings where predictability is the goal.
But under that honest name, the halting monoculture is not an anomaly to be
explained — it is the *advertised behaviour*: the most predictable future available
to this substrate is a halted one, and the objective finds it. Either way §2's
operational conclusion stands: **nothing in the scored objective opposes the dark
room, and the halting outcome is the dark-room attractor, correctly diagnosed.**
The dispute would only be over whether to call the objective "broken EFE" or
"working KL-control that was pointed at the wrong goal". I'd recommend the second
framing in any write-up, because it makes the result unremarkable-by-theory rather
than mysterious, and it names what would actually have to change.

### 1.2 A partial disagreement: §4's second finding is stated too strongly

§4: "an epistemic term scored on the policy layer cannot work, because a policy
that produces the observations confirming its own predictions is a self-consistent
fixed point of any objective scored on predictions." And §6 uses this to close the
door on ever adding an epistemic term to the policy score.

The fixed-point argument is airtight for **accuracy-type** objectives (prediction
error, predictive entropy, calibration): a halting policy makes its own predictions
true and cheap, so it is an attractor. It does **not** carry over to
**gain-type** objectives. Expected information gain is not a reward for being
right; it is a reward for *becoming less uncertain*. A halted region generates no
new evidence, so under a novelty term (e.g. the Beta-variance quantity E2 names,
maximal on zero evidence) a halting policy *starves* its own score rather than
confirming it. The genuine failure mode for gain-type terms is different and
weaker: an agent whose posterior has already collapsed assigns ≈0 EIG everywhere
and becomes *indifferent* to halting — not attracted to it. Indifference is a
tie-break problem, not a fixed point.

I would not reopen §6's exclusion now — the ladder is right to defer it — but it
should be downgraded from "closed" to "deprioritized, pending E2", because **E2 is
in fact a direct test of this disagreement**: if the implemented Beta-variance
quantity correlates positively with measured damage, then a gain-type term with
the correct sign exists in this system and §4's second finding, as stated, is
falsified in its strong form.

### 1.3 The ladder, and E1 as first rung

**E1 → E2 → E3 in that order: agreed.** E1 is the cheapest, it closes a standing
ledger item on the observable the programme's own definition names, and E2's
interpretation is cleaner once the damage instrument has been exercised in the
reduced model. E3's conditionality and its finite-size-scaling acceptance
condition are both right (the interior-vs-crossover distinction is exactly what
killed the earlier argmax-of-4-seeds "INTERIOR OPTIMUM: YES").

Four caveats, recorded before running, so the result cannot be quietly
reinterpreted afterwards:

**(a) Scope.** The reduced model has no policy layer and no rule transformation
(§5.2 already concedes this). E1 can close or reopen §4's *gate refutation* only.
Whatever E1 says, it is not evidence about §2's halting pathology, in either
direction. A clean E1 must not be spent as if it were.

**(b) Structural asymmetry between the arms (found by reading the scripts, and it
matters for interpreting any difference).** In `microtest6_eig_vs_constant.py`,
blend *offers* depend only on the rule layer (`blend_rule` reads `geno` alone), and
in `const` mode the acceptance probability is a fixed scalar. Therefore in the
const arm the rule layer's entire trajectory is independent of the state layer: a
state-cell flip **provably cannot ever produce rule-layer damage in the const
arm** (identical seed ⇒ identical offers ⇒ identical draws ⇒ identical rule
trajectory in both twins). In the eig arm it can, via the phenotype histogram
w(n) → EIG → acceptance probability. So the two arms do not merely differ in
*where* they blend; the eig arm possesses a phen→geno damage channel the const arm
lacks by construction. Any eig>const damage difference could reflect this extra
channel rather than "informative placement". Mitigation: measure state-layer and
rule-layer damage separately; the const arm's rule-layer damage must be exactly 0,
which doubles as a free correctness check on the harness.

**(c) RNG protocol confound, eig arm only.** Acceptance noise is drawn from one
sequential stream. Twins stay draw-aligned until their rule layers first diverge;
after that, the eig twins' noise desynchronizes and subsequent "damage" mixes
genuine causal propagation with chaotic amplification of decorrelated noise.
Standard damage-spreading practice uses common noise indexed per (cell, t). The
const arm never desynchronizes (see (b)), so this confound inflates the eig arm's
measured damage only. Since the instruction is to keep everything as-is, the
primary run keeps the sequential stream and this caveat; a common-noise variant is
worth running as a robustness check if time permits, clearly labelled secondary.

**(d) E1's falsifier needs a direction convention it does not currently have.**
"The gate still loses on damage" presupposes that one direction on the damage axis
is "losing". §3 itself insists damage is not monotone-good: the target is the
class-IV band, not the maximum (rule 30 wins the max and is chaotic). In the
reduced model there is no calibrated band. What E1 can honestly decide is: *does
gating change damage reach at matched blend rate, and in which direction, at what
absolute level?* The absolute level then locates the regime: if damage is a few
cells against W=80, the system is order-dominated and more reach plausibly reads
as livelier/better; if damage sits near the decorrelation ceiling (~W/2 for random
states), less reach reads as better. I will report signed differences *and*
absolute levels, and argue direction from the regime — not from a convention
adopted after seeing the numbers.

**Alternative first rungs considered and rejected.** Running E2 first is arguable
(it is in the full system, and it bears on the live §2 pathology and on §1.2), but
E1 is cheaper, its artifacts already exist, and an open refutation should be closed
on the right observable before anything is built near it. No better first rung
proposed.

### 1.4 Verdict

- §2's framing is **correct**, and can be strengthened from a judgement to an
  identity: the implemented objective equals an EFE *plus* the information gain —
  the epistemic term with inverted sign. Recommended reframe: the objective is
  well-formed KL-control-with-determinism-prior, under which name the halting
  monoculture is the predicted behaviour, not an anomaly.
- §4's second finding is too strong as stated: it closes accuracy-type policy
  scores, not gain-type ones. Downgrade §6's first exclusion to "deprioritized,
  pending E2"; E2 doubles as its direct test.
- The ladder E1 → E2 → E3 is right and E1 is the right first rung, subject to
  caveats (a)–(d) above, which are recorded here before any code ran.

Proceeding to E1.

---

## 2. E1 — gated vs matched constant, scored on damage reach

*(Appended as results land; design registered here before the full run.)*

### 2.1 Design (registered before running)

New script `/home/joe/code/futon5/analysis/microtest6b_damage_vs_constant.py`;
originals untouched. Everything inherited from `microtest6_eig_vs_constant.py`
unchanged: W=80, STEPS=4000, seeds 1–12, κ ∈ {1, 8, 64}, matched-constant rate =
mean realized blend rate of the eig runs at that κ, same shared-source `bit` /
`rule_out` / `blend_rule` (loaded from `microtest5` exactly as microtest6 does),
same sequential RNG usage so the unperturbed trajectories are bit-identical to the
original comparison's.

Only the scored observable changes. Twin protocol per (mode, param, seed): one run
as before; one copy from the same seed with `phen[0]` flipped at t=0; damage =
count of differing state cells, recorded at horizons t = 100 (primary — the §3
instrument's horizon), 1000, and 4000 (secondary). Rule-layer damage (count of
differing rules) recorded at the same horizons as a check: const arm must be
exactly 0 (§1.3b).

Reported per κ: matched rate; per horizon: eig mean ± sd, const mean ± sd,
difference, difference in pooled-sd units (same pooling as the original), and the
paired per-seed sign tally (seeds with eig>const / eig<const / tie). No argmax
verdict line. Ambiguity will be reported as ambiguity.

### 2.2 Fidelity check

`microtest6_eig_vs_constant.py` rerun unmodified reproduces the technote's §4
numbers exactly: domain-length differences −0.152 (−0.29 sd), −0.132 (−0.27 sd),
−0.378 (−0.60 sd) at κ = 1, 8, 64 — negative three of three, as reported. Its
matched blend rates (0.119, 0.753, 0.991) are **identical to the ones my 6b
baselines produce**, confirming the new script's unperturbed trajectories are
bit-identical to the original comparison's (same RNG consumption; `blend_rule`
memoization is a pure-function cache with no RNG impact).

### 2.3 Primary result — as-original protocol (`microtest6b_damage_vs_constant.py`)

State-cell damage at t=100 (primary horizon), 12 seeds, W=80:

| κ | matched rate | eig damage | const damage | paired diff ± SE | t | e>c / e<c / tie |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 0.119 | 8.67 ± 5.23 | 0.17 ± 0.58 | **+8.50 ± 1.45** | +5.85 | 11 / 0 / 1 |
| 8 | 0.753 | 3.75 ± 5.45 | 0.67 ± 0.89 | +3.08 ± 1.55 | +1.98 | 8 / 1 / 3 |
| 64 | 0.991 | 1.25 ± 2.26 | 0.50 ± 0.90 | +0.75 ± 0.54 | +1.39 | 2 / 0 / 10 |

In the original's pooled-sd units: +2.29, +0.79, +0.44 sd. **Sign: positive three
times of three** (the original domain-length comparison was negative three of
three). Notes:

- Damage is identical at t=100, 1000 and 4000 in essentially every condition —
  the perturbation's fate is settled within 100 steps and then frozen. Horizon
  choice is immaterial here.
- Rule-layer damage: eig arm 27.25 / 13.50 / 1.67 cells; const arm **exactly 0 at
  every horizon and seed** — the registered harness check (§1.3b) passes, and
  confirms the structural asymmetry is real, not hypothetical.
- Absolute levels are tiny against W=80: const arm 0.17–0.67 cells (below the
  rule-204 stationary anchor of §3), eig arm 1.25–8.67 (at κ=1, roughly the
  rule-90 anchor). The regime is deeply order-dominated, so per the registered
  direction convention (§1.3d): more reach reads as livelier, and the gate is
  livelier.

### 2.4 Robustness — common per-(t,i) acceptance noise (`microtest6c_damage_common_noise.py`)

The registered confound (c) — sequential-stream RNG desynchronization, which can
only inflate the eig arm — is removed by drawing acceptance noise as a per-(t,i)
field shared by both twins, so twin decisions differ only where the perturbation
causally changes the acceptance probability or the offer. Baselines differ from
the original (different noise consumption), so matched rates are recomputed
within-variant. Run at the registered 12 seeds and, as a labelled extension
(runs cost 0.25 s; the causal magnitude was the ambiguous number), at 48 seeds:

State damage at t=100, n=48:

| κ | matched rate | eig damage | const damage | paired diff ± SE | t | e>c / e<c / tie | sign-test p (approx) |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 0.158 | 2.21 ± 2.82 | 0.50 ± 0.99 | **+1.71 ± 0.42** | +4.08 | 25 / 4 / 19 | ~1e-4 |
| 8 | 0.753 | 1.79 ± 1.98 | 0.79 ± 1.40 | **+1.00 ± 0.32** | +3.08 | 21 / 4 / 23 | ~1e-3 |
| 64 | 0.937 | 0.83 ± 1.39 | 0.54 ± 0.92 | +0.29 ± 0.16 | +1.82 | 10 / 5 / 33 | ~0.3 |

(12-seed values are consistent: +1.33, +0.75, +0.50 cells, same signs.) Eig-arm
rule-layer damage 5.12 / 3.48 / 0.42 cells; const arm again exactly 0. The
paired t on zero-inflated counts is approximate; the sign test is the
conservative backup and agrees at κ=1 and κ=8.

Comparing 2.3 and 2.4: the as-original κ=1 effect (+8.5 cells) is ~5× the
causal-only effect (+1.7 cells) — **most of the primary protocol's magnitude is
RNG-desync amplification, exactly as the registered caveat predicted.** But the
causal-only effect survives: positive at all three κ, individually significant at
κ=1 and κ=8, and indistinguishable from zero at κ=64 — where the gate saturates
(acceptance ≈ 0.94–0.99) and the two arms nearly coincide mechanically, so a
vanishing difference is what the mechanics predict.

### 2.5 Reading

**E1's registered question — does the gate's deficit survive on damage? No. The
sign flips.** On mean rule domain length the gate lost three of three
(reproduced, §2.2). On damage reach it loses zero of three, and wins
significantly at the two κ where the gate is meaningfully different from its
control. The §4 refutation is **observable-specific**: it stands on the
structure score it was run on, and does not transfer to the observable the
programme's definition of epistemic value actually names.

**Mechanism, not magic.** The gate's damage advantage is located: it is the
state→rule information channel that gating creates and that the matched-rate
control provably cannot have (const-arm rule damage ≡ 0 by construction; eig-arm
rule damage measured at 3–5 cells causal). A matched *rate* is not a matched
*coupling topology*. So under damage scoring the comparison is channel vs
no-channel — and that is arguably fair, because responsiveness of the coupling to
the state layer is precisely what "blend where it is informative" *means*
mechanically. The two scores answer different questions: on "does the gate build
more rule-layer structure?" the answer stays no; on "does the gate make the
system's future more sensitive to a present difference?" the answer is yes.
That is §5.1's gap, now measured rather than argued.

**What this does and does not license.**
- It does **not** resurrect the gate design. §4's diagnosis (the coupling
  operator is already structurally epistemic; gating the gate pays a structure
  cost) is untouched — indeed reproduced here.
- It does **not** show the gate moves the reduced model toward a class-IV band.
  Absolute damage levels are 1–2 cells (causal) in a near-dead regime; the
  honest statement is "less dead than matched constant", not "critical".
- It does **not** bear on §2's halting pathology (registered scope caveat (a):
  no policy layer in the reduced model).
- It **does** remove the sentence "the gate loses on the quantity that matters"
  from the record: on the quantity that matters, measured cleanly, it wins where
  it is distinguishable from its control.

**Ambiguity statement.** The κ=64 result is ambiguous (t = +1.82, sign test
p ≈ 0.3) and is reported as ambiguous; the mechanics make a null expected there.
The κ=1 and κ=8 causal results are not ambiguous at n=48 by either test. The
primary-protocol magnitudes should not be quoted without the desync caveat.

### 2.6 Consequence for the ladder

Proceed to E2 as planned, with slightly more motivation than before: E1 shows the
damage instrument discriminates in the reduced model where the structure score
pointed the other way, and E2 now tests whether the implemented Beta-variance
quantity points at damage in the full system — which (per §1.2) doubles as the
test of whether §4's "no epistemic term on the policy layer can work" holds in
its strong form. E3 remains conditional and its finite-size-scaling condition
remains essential.

Deviations from registration, for the record: (1) paired mean ± SE and t added
to the reporting alongside the registered sign tallies (the pooled-sd metric
understates paired effects on zero-inflated counts); (2) the common-noise
variant, registered as "if time permits", was run and extended to 48 seeds,
labelled as an extension; (3) `blend_rule` memoized for speed (pure function,
bit-identical results, verified against the original's matched rates in §2.2).

Artifacts: `/home/joe/code/futon5/analysis/microtest6b_damage_vs_constant.py`
(primary, as-original protocol),
`/home/joe/code/futon5/analysis/microtest6c_damage_common_noise.py` (robustness,
optional argv = seed count). Originals untouched; nothing under `src/` or
`test/` touched; nothing committed to git.
