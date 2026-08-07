# Answer: a policy-specific epistemic quantity — reasoning, candidates, measurements

Fable 5, 2026-08-04. Response to `TN-policy-specific-epistemic-quantity.md`.
Written in order: §1 reasoning first, before any code was run; results appended below as
they landed. Scripts (all new; nothing under `src/` or `test/` touched):
`scripts/policy_x_v1.clj` (main harness), `scripts/policy_x_v1b.clj` (horizon + within-cell
ranking), `scripts/policy_x_v1c.clj` (incremental value vs |Δrate|). Row cache:
`analysis/policy-x-v1b-rows.edn` (v1 prints only; re-running it will now also write
`analysis/policy-x-v1-rows.edn`; everything is deterministic from the listed seeds).

## 1. Reasoning before building

### 1.1 Is the problem well-posed? Yes — and here is the exact mechanism

From the code (`gen/rule-permute`, `grid/apply-exotype`, `grid/step`), the per-step transform
at cell *i* is: draw k uniform in 0..7, write ¬b[k] into position σ(k) of the rule byte b.
The byte changes iff b[σ(k)] == b[k]. Three facts matter for predictability:

1. **The draw is shared between the twins.** The draw-seed is `seed + t*width + i`, identical
   in baseline and perturbed run. So Y(i, π) is *not* an average over policy-vs-noise — given
   the state and the seed, the same k is drawn in both trajectories, and the *only* source of
   divergence is σ vs σ′ acting on the same (k, b). The stochastic transform is common noise,
   not policy noise. This defuses the strongest "no local quantity can exist" argument: the
   policy's differential effect per draw is a deterministic function of (k, b, σ, σ′), and k
   is uniform — so the decision-time expectation over k is exactly computable from local state.

2. **The σ difference persists.** In `:heterogeneous-fixed` the exotype grid is static
   (`transmit` is identity), so replacing σ at cell i is a permanent change: divergence is
   *injected repeatedly*, not once. Even if horizon-60 damage is chaotic in its amplification,
   its *injection rate* is a stable local quantity.

3. **Divergence condition, exactly.** With own σ (positional map A) and candidate σ′ (map B),
   sharing draw k on byte b: baseline writes ¬b[k] at A(k), twin writes ¬b[k] at B(k).
   The resulting bytes differ iff

       A(k) ≠ B(k)  AND  ( b[A(k)] == b[k]  OR  b[B(k)] == b[k] ).

   And the *expressed* consequence (next phenotype output changes) requires the diverged
   truth-table position(s) to be exercised by the pattern the cell actually sees.

### 1.2 Is §5's trap argument right? Yes, and the way out is the pair structure

§5 is correct: rate(σ) = 0.5 + fix(σ)/16 is a function of σ alone, so any X that reduces to
f(rate(σ_π)) is a rescaled copy of an existing score term and cannot change any argmin that
term could not. The derivation above shows what survives: the divergence condition depends on
**where A and B disagree** (the set {k : A(k) ≠ B(k)}) and on **the actual byte b at those
positions**. This is a property of the *pair* (σ_own, σ_candidate) jointly with the state —
e.g. hold (σ′ = σ) gives zero regardless of rate, and two candidates with identical rates but
different overlap with the own σ give different values. That cannot be written as f(rate(σ_π)).

### 1.3 The candidates (registered before measuring)

**X_now(i, π)** — expected *expressed* first-divergence mass, jointly in σ-pair, byte, and
local patterns (the §5 "candidate shape", completed to the twin-difference form):

    X_now = (1/8) · Σ_{k : A(k) ≠ B(k)}  [ w(n_{A(k)}) · 1{b[A(k)] == b[k]}
                                          + w(n_{B(k)}) · 1{b[B(k)] == b[k]} ]

where n_j = `truth-table-3[j]` and w(·) is the 11-cell-window pattern frequency (same w as the
validated X_blend). Cost: O(8) per candidate. X_now(hold) = 0 identically.

**X_pair(i, π)** — the byte-averaged injection rate, structural in the pair alone:

    X_pair = (1/8) · Σ_{k : A(k) ≠ B(k)}  ( 1        if A(k) = k or B(k) = k
                                             3/4      otherwise )

This is E_b[P(divergence | draw)] over uniform bytes. Registered because the rule byte churns
at ≥ 0.5/step, so b at t decorrelates from b at 0 within a couple of steps; the *sustained*
injection rate should converge to X_pair. If X_now beats X_pair, state-conditioning carries
signal; if not, the useful object is a 12×12 pair table — still policy-specific, still cheap,
still non-redundant (hold ⇒ 0; equal-rate candidates differ).

### 1.4 Predictions, stated before running

- Y(i, hold) = 0 exactly (the replacement is a no-op); X_now = X_pair = 0 there. Matched.
- Both X's will have **nonzero within-cell spread** by construction (hold pins one candidate
  at 0; adopt-left/right differ whenever neighbours' σ differ). This must be confirmed
  numerically, not asserted — the previous candidate died here.
- r(X, Y) on adopt policies: expect **weak positive**, order of the X_blend result
  (r ≈ +0.1..0.25), because horizon-60 damage adds chaotic amplification variance on top of
  the injection term. r ≈ 0.4+ would be surprising; r ≈ 0 would say amplification variance
  dominates injection entirely — a real "no useful local quantity" result, which the harness
  below is equipped to declare honestly.
- Redundancy: r(X, rate(σ_π)) should be visibly below 1 (expect moderate positive — larger
  disagreement sets loosely co-occur with higher-rate candidates — but the pair/overlap and
  byte terms are the point). Also reporting r(X, |rate(σ′) − rate(σ)|) since Δrate is the
  sharpest rate-only impostor.
- Control: r(X, Y_flip) ≈ 0, where Y_flip is damage from an unrelated first-genotype-bit flip
  (the existing control). Watch: X_now carries w(n) activity weighting, which could correlate
  with generic damage-propagation propensity; the control is precisely what catches that.

### 1.5 Design of the measurement

- Width 60, horizon 60, arm `:heterogeneous-fixed`, state built inside `ca/with-seed` exactly
  as in `scripts/exotype_eig_v2.clj`.
- Two disjoint seed sets: A = {11, 22, 33, 44, 55, 66}, B = {707, 808, 909, 1111, 1212, 1313}.
- Y(i, π): replace `exotypes[i]` with the neighbour's kind, run twin from the same state map,
  phenotype Hamming distance at t=60. Baseline trajectory computed once per seed.
- Report, per seed set: r(X, Y) per policy (adopt-left, adopt-right) and pooled over the two
  adopt policies; pooled including hold reported separately and flagged (hold contributes
  exact (0,0) pairs, which inflate r trivially); the same restricted to cells where
  σ_candidate ≠ σ_own (≈ 11/12 of cells; removes matched trivial zeros).
- Controls: r(X, Y_flip). Redundancy: r(X, rate(σ_π)), r(X, |Δrate|), and a partial
  correlation r(X, Y | rate(σ_π), |Δrate|) via linear-regression residuals — the sharpest
  form of the §5 check: does X predict Y *beyond* anything a rate-only term could.
- **Spread check**: mean over cells of the population SD of X across the three candidates
  {hold, adopt-left, adopt-right}; reported for X_now, X_pair, and (for context) rate(σ_π)
  itself.

---

## 2. Results

### 2.0 Sanity gate (before the main harness)

- All 12 vocabulary σ decode (via `gen/sigma-positional`) to proper permutations of 0..7;
  fix counts reproduce `rate(σ) = 0.5 + fix/16` exactly for every kind (:identity 8/1.0,
  :builder 5/0.8125, :fix6 6/0.875, :fix4 4/0.75, :fix3 3/0.6875, :fix2 2/0.625,
  :chaos 1/0.5625, all even-cycle kinds and :odd53 0/0.5000).
- The divergence predicate underlying both candidates —
  `A(k) ≠ B(k) AND (b[A(k)] == b[k] OR b[B(k)] == b[k])` — was checked against exhaustive
  simulation of the actual `rule-permute` write for the (:builder, :chaos) pair over all
  256 bytes × 8 draws: **2048 checks, 0 mismatches**. The candidates are computing the
  true one-step twin-divergence condition, not an approximation of it.

### 2.1 Main harness (`scripts/policy_x_v1.clj`, 2160 rows, seeds A/B disjoint)

Y descriptives (adopt rows): mean 5.27, sd 4.36, zero-fraction 0.175; same-σ candidate
fraction 0.092.

**Correlations r(X, Y), adopt policies:**

| set | X | adopt-left | adopt-right | pooled | pooled, cand≠own only |
|---|---|---:|---:|---:|---:|
| A | X_now  | +0.265 | +0.238 | +0.251 | **+0.047** (n=650) |
| A | X_pair | +0.304 | +0.347 | +0.325 | **+0.073** (n=650) |
| B | X_now  | +0.174 | +0.165 | +0.170 | **+0.016** (n=658) |
| B | X_pair | +0.242 | +0.205 | +0.224 | **+0.030** (n=658) |
| A+B | X_now  | +0.210 | +0.196 | +0.203 | **+0.027** (n=1308) |
| A+B | X_pair | +0.266 | +0.267 | +0.266 | **+0.047** (n=1308) |

(Hold: X = 0 and Y = 0 exactly for every cell, as predicted — per-policy r undefined, and
pooling hold in would only add exact (0,0) pairs.)

Rate-only impostors on the same rows (pooled): r(rate(σ_π), Y) = −0.073;
r(|Δrate|, Y) = +0.166. Partial r(X, Y | rate(σ_π), |Δrate|): X_now +0.105, X_pair +0.216
(pooled; per-set +0.12/+0.25 and +0.10/+0.20) — but see the caveat below.

**Control (clean):** r(X_now, Y_flip) = −0.009; r(X_pair, Y_flip) = −0.044. X is not
measuring generic activity.

**Redundancy (passes §5):** r(X_now, rate(σ_π)) = +0.125; r(X_pair, rate(σ_π)) = +0.132 —
nowhere near 1. (r(X, |Δrate|) is high — +0.73/+0.85 — as expected, since larger
disagreement sets co-occur with larger rate gaps; but |Δrate| itself predicts Y worse than
X_pair, and Δrate is *pair*-structural too, not a function of σ_π alone.)

**Spread (passes the previous candidate's killer):** mean within-cell SD across
{hold, L, R}: X_now 0.054 (zero-SD in 14/720 cells), X_pair 0.290 (8/720),
rate(σ_π) 0.116 (59/720). The quantities genuinely vary across candidates.

**The honest headline, though, is the cand≠own column.** Nearly all the pooled correlation
comes from matched zeros: X = 0 predicting Y = 0 where the candidate σ equals the own σ.
Those cases are *decision-irrelevant*: if σ_left = σ_own, adopt-left IS hold, and every
score term already assigns them identical values — the argmin cannot be changed there.
Among genuinely differing candidates the correlation is +0.02..+0.07 and terciles are flat
(Y by X_now tercile: 5.67 / 5.89 / 5.85; by X_pair: 5.78 / 5.69 / 5.94). The partial-r
numbers above inherit the same inflation (they include the matched zeros).

### 2.2 What remains to be settled before concluding

Pooled r across cells conflates between-cell amplification variance (shared by all
candidates of a cell) with the within-cell candidate differences the decision actually
compares. Two follow-ups, run next:

1. **Within-cell pairwise ranking** — among cells where own, left, right kinds are all
   distinct: does sign(X(L) − X(R)) agree with sign(Y(L) − Y(R)) above chance? This holds
   the cell context fixed entirely and is the decision-level falsifier.
2. **Horizon decomposition** — measure Y at t ∈ {2, 5, 10, 20, 40, 60}, the first
   phenotype-divergence time, and the realized first-step rule divergence at t=1, and
   calibrate the exact one-step injection probability X_inj = |K_div|/8 against the
   realized t=1 divergence frequency. If X predicts injection (t=1) and early damage but
   not t=60 damage, the conclusion is: the local quantity predicts exactly what is locally
   determined — the injection — and horizon-60 consequences are dominated by chaotic
   amplification that no local quantity can see.

### 2.3 Horizon decomposition and decision-level ranking (`scripts/policy_x_v1b.clj`)

1308 rows (adopt policies, cand ≠ own, same two seed sets). A third quantity was added:
**X_inj = |K_div|/8**, the *exact* probability over the uniform draw that the twin rules
diverge at the first step, conditioned on the actual byte (X_now without the w-weighting).

**Calibration — X_inj is the true one-step divergence probability.** Binned realized t=1
rule-divergence frequency against X_inj: 0.000→0.000, 0.125→0.086, 0.250→0.341,
0.375→0.384, 0.500→0.521, 0.625→0.618, 0.750→0.754, 0.875→0.901, 1.000→1.000 (monotone,
deviations within small-n noise). Pooled r(X_inj, 1{t=1 divergence}) = **+0.469**, and the
theoretical maximum for a *perfectly calibrated* probability with these marginals is
sd(p)/sd(1{·}) ≈ 0.23/0.49 ≈ **0.47**. X_inj sits at the ceiling: no local quantity can
predict the first-step effect better, because X_inj *is* its exact conditional probability.

**Horizon decay of r(X, Y_t), pooled (per-set values consistent):**

| X | t=2 | t=5 | t=10 | t=20 | t=40 | t=60 |
|---|---:|---:|---:|---:|---:|---:|
| X_now  | +0.176 | +0.189 | +0.258 | +0.140 | +0.123 | +0.027 |
| X_pair | +0.147 | +0.196 | +0.272 | +0.174 | +0.115 | +0.046 |
| X_inj  | +0.161 | +0.211 | +0.284 | +0.178 | +0.127 | +0.059 |

(The t=60 column reproduces §2.1's cand≠own numbers exactly — cross-harness consistency.)
Signal peaks at t≈10 (divergence has had time to be *expressed* but not to be chaos-mixed)
and decays to near-null by t=60.

**Divergence onset.** P(diverged by t) = 0.10 / 0.49 / 0.78 / 0.94 / 0.98 / 0.98 at
t = 2/5/10/20/40/60. r(X_inj, −first_divergence_time) = **+0.429**. Nearly every differing
candidate eventually diverges; the ~9% with Y=0 at t=60 mostly diverged and *healed* —
pure amplification/persistence noise, invisible to any one-step local quantity.

**Within-cell pairwise ranking** (547 cells with own/left/right kinds all distinct; the
decision-level test — all cell context shared, only the candidate differs):

| predictor | vs Y_5 | vs Y_10 | vs Y_60 |
|---|---:|---:|---:|
| X_now  | 65.9% (z=+4.8) | 63.4% (z=+4.8) | 53.0% (z=+1.2) |
| X_pair | 65.9% (z=+4.8) | **68.0% (z=+6.5)** | 55.0% (z=+2.1) |
| X_inj  | 67.0% (z=+4.9) | 67.7% (z=+6.2) | 56.1% (z=+2.4) |
| \|Δrate\| | 66.8% (z=+4.9) | 66.5% (z=+5.6) | 55.3% (z=+2.0) |

X_inj vs *earlier first divergence*: 72.5% agreement. So within a cell the quantity ranks
which adoption diverges sooner and does more early damage, well above chance; by t=60 the
edge shrinks to ~55%.

### 2.4 Does X add anything beyond |Δrate|? (`scripts/policy_x_v1c.clj`)

|Δrate| ranked nearly as well as X in §2.3, and r(X_inj, |Δrate|) = +0.81 — so is X just
Δrate wearing a hat? No, and the asymmetry is sharp:

- partial r(X_inj, Y_10 | |Δrate|) = **+0.177** pooled (+0.214 set A, +0.142 set B);
  at t=20 +0.147; at t=60 +0.062.
- The reverse: partial r(|Δrate|, Y_10 | X_inj) = **−0.005** (t=60: −0.033).

**X_inj strictly subsumes |Δrate| as a predictor; |Δrate| adds nothing given X_inj.**
Confirmed non-parametrically: among the 99 cells where the two candidates have *equal*
|Δrate| (so a rate-built term is constitutionally indifferent), X_inj still ranks Y_10 at
70.5% (z=+2.7) and Y_60 at 69.4% (z=+2.7); X_pair 72.7% at Y_10.

Also: X_now (the w(n)-weighted version) is consistently the *weakest* of the three — the
local-pattern weighting adds noise, not signal (patterns decorrelate before the diverged
entries get exercised). r(X_inj, X_pair) = +0.95: byte-conditioning beyond the pair
structure is a small refinement.

## 3. Conclusions

**§5's trap argument is correct, but it does not close the door.** Any X reducing to
f(rate(σ_π)) is indeed redundant. What survives is the *pair* structure: the divergence
condition depends on where σ_own and σ_candidate disagree and on the byte at those
positions — hold gives 0, equal-rate candidates differ, and measured redundancy against
rate(σ_π) is r ≈ +0.13. The trap kills the obvious candidates, not the problem.

**A policy-specific quantity exists, and its scope is now exactly characterized:**

- **X_inj(i, π) = |{k : A(k)≠B(k) ∧ (b[A(k)]=b[k] ∨ b[B(k)]=b[k])}| / 8** (A = own σ,
  B = candidate σ, b = current rule byte) is the exact one-step twin-divergence
  probability — calibrated at the theoretical ceiling, O(8) per candidate, zero forward
  simulation. Its byte-average **X_pair(σ, σ′)** is a 12×12 constant table (compute once,
  free per step) and carries ~95% of the same signal.
- It passes every §6 gate: two disjoint seed sets (consistent), clean control
  (r vs unrelated-flip damage ≈ −0.01/−0.04), non-redundant with rate(σ_π) (≈ +0.13),
  **nonzero within-cell spread** (X_pair mean SD 0.29 across the three candidates — more
  spread than the existing rate term itself, 0.12), and it strictly subsumes the sharpest
  rate-only impostor |Δrate|.
- **But it predicts the injection, not the horizon-60 magnitude.** r(X, Y) peaks ≈ +0.28
  at t≈10 and decays to +0.03..+0.09 by t=60; within-cell ranking falls from ~68% to ~55%.

**The "does the state determine the consequence?" question has a measured two-part answer.**
The current state determines the *onset* of the policy's consequence essentially completely
(X_inj is the true conditional probability; the draw is shared between twins, so it is
common noise, not policy noise). The current state does *not* determine the horizon-60
*magnitude*: nearly all candidates diverge by t=20, and what Y_60 then measures is chaotic
amplification and healing of the damage cone — shared cell context plus realized-draw
detail, irreducibly unpredictable from any cheap local readout. This is not a defect of
the candidate: a predictor sitting at the calibration ceiling for the step it models still
loses to chaos at 6× the mixing time. A long-horizon Y is the wrong target for *any* local
X; the injected-divergence rate is the right one, and there X works.

**Recommendation.** If the score in §2 gets an epistemic term, use X_pair (table lookup;
upgrade to X_inj if the byte-conditioned refinement is wanted). Interpret it as "expected
rate of injecting genuinely new dynamics by switching to this σ", valid at effective
horizon ~5–20 steps. Do not justify it by long-horizon damage prediction — that claim
would not survive its own falsifier. And the previous failure mode is affirmatively
absent: within-cell spread 0.29 ≠ 0, so this term can actually move an argmin.

**Limitations.** One arm (:heterogeneous-fixed), one width/horizon (60/60), 12 seeds;
n=99 for the drate-tied subtest; Y is a single deterministic twin outcome per (cell,
candidate), not an average over draw-seeds — the reported r's therefore bound the
*decision-time* predictability (seed unknown), which is the operative quantity, but they
understate how well X predicts the *expected* damage E_seed[Y].
