# Answer: interruption vs diversity — is there an epistemic quantity that interrupts?

Fable 5, 2026-08-05. Follow-up to `TN-policy-specific-fable-answer.md`, responding to the
question posed after the v2 run (`scripts/exotype_policy_epistemic_v2.clj`,
`reports-remote/epi/`): the shipped X_pair term delivered diversity but not interruption —
is there an epistemic quantity for this decision that supplies *interruption*, or is that
structurally impossible on a channel that carries transforms rather than rule content?

Written in order: §1 reasoning first, before any measurement; results appended in §2 as
they land. Scripts (all new; nothing under `src/` or `test/` touched):
`scripts/interrupter_epi_summary.clj` (babashka), `scripts/interrupter_landscape.clj`,
`scripts/interrupter_twins.clj`, `scripts/interrupter_persist.clj`,
`scripts/interrupter_coexist.clj`, `scripts/interrupter_identity_hub.clj`.
Row caches: `analysis/interrupter-{twins,coexist,persist}-rows.edn`.

---

## 1. Reasoning before measurement

### 1.1 What "interruption" has to mean here — read off the update map

The dynamics that actually ran (`self_tuning/step`, with `:apply-probability` p gating the
transform coin and `blend-strength` = 0 on this path) are, per step, for each cell i:

1. **Decision**: argmin over {hold, adopt-left, adopt-right} of
   G(candidate kind, obs_i) − κ·X_pair(σ_own, σ_cand) [epistemic arm]
   or G − bonus·1{candidate ≠ own} [churn arm], where
   obs_i = (activity_i, diversity_i): activity = fraction of the 3-neighbourhood's
   *phenotype* bits differing from own; diversity = number of distinct *genotype sigils*
   in the 3-neighbourhood (1..3).
2. **σ write**: exotype_i ← winner's kind. (Lateral: copies a neighbour's kind label.)
3. **Rule write**: with prob p, b_i ← T_{σ_i,k}(b_i) with k a *cell-private* shared-seed
   draw; else b_i unchanged. **Reads no neighbour byte. Ever.**
4. **Phenotype write**: s_i ← b_i(s_{i−1}, s_i, s_{i+1}). (Lateral, ECA-style.)

So the lattice has exactly three lateral channels: (a) the phenotype ECA channel,
(b) the σ-copy channel (payload: one of 12 kind labels), and (c) the decision's
observation reads — phenotype disagreement (3 values) and genotype *distinctness*
(3 values; an equality predicate on neighbour rule content, not the content itself).
Rule **content** has no lateral channel on this path. The blending operator
(`grid/blend-rule`) is the only place in the codebase where a cell's next byte is a
function of neighbour bytes, and on this path β = 0; it sits on a policy-free path.

"Interruption" therefore splits into three distinct claims, and the diagnosis
"diversity without ergodicity" is only assessable once they are separated:

- **I-content**: bits of cell j's rule coming to depend on bits of cell i's rule
  (content transport / lateral correlation of realized rule content).
- **I-divergence**: a perturbation at i causing the *rule layer* at j ≠ i to take a
  different trajectory (twin-experiment damage in the rule layer, the sense my
  X_pair quantifies at the injection site).
- **I-mixing**: ergodicity of the per-cell rule process — in particular mixing across
  the cycles of σ, since under a *fixed* σ the byte chain factorizes into independent
  subprocesses, one per cycle of σ (T only ever writes ¬b[k] to σ(k), so position j is
  influenced only by its σ-preimage chain, i.e. by its own cycle). This is the sense in
  which the prior review called blending "the layer's only source of position-mixing
  across the permutation's cycles."

### 1.2 Proposition 1 (content immobility): I-content is impossible for ANY score term

On this action space the claim is structural, not statistical. The genotype update
(step 3) is a function of (b_i, σ_i, private draws) only. The actions write σ_i only.
Therefore, for **any** scoring rule whatsoever over {hold, adopt-left, adopt-right} —
epistemic, prophetic, or adversarial — cell i's byte at time t is a deterministic
function of (b_i(0), the σ-kind history at i, the private draw stream at i). Neighbour
rule content enters nowhere. Changing the score changes *which Markov kernel runs at
cell i*, never what content arrives. A cleverer X changes the schedule of kernels; it
cannot make b_j read b_i.

Corollary (falsifiable, and I will measure it exactly): in a shared-draw twin
experiment, the rule layer at cell j ≠ (perturbed site) can first diverge at time t
only if the σ field at j already diverged at some t′ < t. Rule-layer damage is
*carried entirely by σ-decision damage*; there is no other door. If the measurement
finds even one (j, t) violating this ordering, Proposition 1 is wrong about the code.

By contrast, one step of blending writes a function of (b_{i−1}, b_i, b_{i+1}) into
cell i — I-content in one step, and cross-cycle position mixing too (bit n of the blend
is looked up through the centre rule at a *pattern*, not copied position-to-position).
The two operators are categorically different objects, not two strengths of one thing.

### 1.3 Proposition 2 (the ceiling on I-divergence, and where X_pair sits under it)

I-divergence through the policy channel is *not* impossible — it is bounded. σ damage
propagates by (b)-channel copying at ≤ 1 cell/step, and by the (c)-channel: an
observation difference (activity or diversity) flipping an argmin. The per-cell,
per-step lateral payload is one kind label out of ≤ 3 candidates; conditioned on the
candidates, the observation can steer at most log₂3 bits into the σ stream, and the
effect of that on the rule layer is exactly the injection my previous answer priced
(X_pair per differing step). So *some* interruption capacity exists in the channel.

But now look at what kind of term X_pair is: it is **state-independent** — a 12×12
table in (σ_own, σ_cand), reading neither activity, nor diversity, nor the byte. Adding
−κ·X_pair deforms the *fixed* decision landscape; it contributes exactly zero
observation-sensitivity of its own. What it does contribute is an **anti-conformity
potential**: X_pair(A,A) = 0 < X_pair(A,B≠A), so it is boundary-active — it rewards
adopting across a σ-domain wall and is silent inside a domain. That is a diversity
force, and only a diversity force. The flat churn bonus 1{candidate ≠ own} is the
*same species of term* — boundary-active, state-independent — differing only in that it
is ungraded across differing candidates. This is why the churn control matched most of
the diversity effect: both terms are wall-maintainers. Neither one couples decisions to
the laterally-propagating state, which is what I-divergence needs.

Note also: monoculture is absorbing under *both* arms (inside a monoculture all three
candidates are the own kind, every term including X_pair and the bonus evaluates
identically, and ties prefer hold). So neither term can re-seed a lost kind; they can
only keep existing walls alive. "Prevents monoculture collapse" = "keeps walls from
annihilating for 600 steps," not "creates diversity."

**Prediction, stated before measuring:** in twin experiments the epistemic arm's
rule-layer damage cone will be no wider than off's or churn's — the term buys wall
density (diversity), and any propagation difference will be attributable to wall
density, not to added decision sensitivity. Separately measurable: the argmin's
sensitivity to a one-bit observation perturbation (fraction of cells whose decision
flips), per arm — if epistemic ≈ churn ≈ off there, the term adds no interruption
capacity, and the diagnosis "diversity without interruption" is measured, not assumed.

### 1.4 The nuance the diagnosis misses: temporal kernel diversity IS a mixing effect

One thing the σ channel *can* do that is genuinely ergodic in flavour: cross-cycle
mixing **in time**. Under a fixed σ the byte chain factorizes over σ's cycles forever
(§1.1). A cell whose kind *churns* — holds A a while, then B, then C — has its
positions coupled across the changing cycle partitions; the factorization is broken in
time even though nothing crosses in space. So an adoption-promoting term does buy a
real ergodicity improvement *within each cell*, measurable as the number of distinct
kinds a cell runs under during the run. Preregistered expectation: epistemic ≫ off on
this measure, epistemic ≈ churn (it is an adoption-rate effect, and churn is
adoption-matched). "Diversity without ergodicity" is thus half-right: no *lateral*
mixing, but real *temporal* kernel mixing — which never yields I-content, by Prop. 1.

### 1.5 A multi-step quantity that is not the one-step quantity iterated: X_persist

X_pair is the one-step injection probability. The question asks for a cheap multi-step
object. There is one, and it is not X_pair iterated: couple the two byte chains under
shared draws (baseline runs σ=A forever, twin runs σ=B forever, same k stream, started
from the same byte) and read off **P(b_t ≠ b′_t)** as a function of t. Divergence can
*heal* (the write ¬b[k] → position A(k)=B(k) can overwrite a differing bit with the
same value), and it can *freeze*: kinds with all-even cycles have absorbing (immune)
bytes, so a (b, b′) pair can lock into permanent divergence — or permanent agreement —
depending on which absorbing states are hit. So the t→∞ divergence mass is a genuinely
different functional of the pair than the injection rate: injection says how fast
divergence starts; persistence says whether it survives its own dynamics. It is still a
12×12 table (compute once, Monte Carlo or exactly), still zero per-step cost, still
policy-specific. Preregistered expectation: X_persist(t=60) correlates strongly with
X_pair over the 144 ordered pairs *except* on pairs involving halting-capable kinds
(:collapser, :even1, :even4, :even8), where absorption decouples the two. Whether that
decoupling is large enough to matter is exactly what the table will show. Scope note,
also stated up front: X_persist is a *vertical* quantity like X_pair — by Prop. 1 it
cannot supply interruption either; it refines the diversity/divergence prediction at
long horizon, nothing more.

### 1.6 The p = 0.6 anomaly: a tie-structure hypothesis, stated before measuring

The risk term is KL(p·rate(σ) ‖ 0.15) and rate(σ) = 0.5 + fix(σ)/16 ∈ [0.5, 1.0].
The p sweep moves the *tie structure* of the risk landscape through three regimes:

- **p ≤ 0.15**: p·rate ≤ 0.15 for every kind — the target sits at or above the whole
  range, risk is monotone *increasing in distance below target*, and :identity
  (rate 1.0) is the unique risk argmin. A strict scalar winner → sweep → monoculture.
  Any boundary-active bonus (graded or flat) resists the sweep: diversity, cheaply.
- **p = 0.3**: 0.3·rate ∈ [0.15, 0.30]; the five fix=0 kinds (:collapser, :even4,
  :even8, :even1, :odd53) sit *exactly on target* (risk 0), and — the point — the whole
  rest of the vocabulary is within a few millinats (chaos ≈ 0.001, fix2 ≈ 0.006,
  identity ≈ 0.075). Risk has essentially left the objective; ordering is set by
  ambiguity + conatus, which are scalar per kind given the observation → transitive →
  a strong winner again; either bonus destabilizes it the same way. Epistemic ≈ churn.
- **p ≥ 0.6**: target unreachable (min p·rate = 0.3 or 0.5 > 0.15); risk is monotone in
  rate; the five fix=0 kinds are *exactly tied at the minimum* and the gap to the sixth
  kind (:chaos) is ≈ 0.037 nats at p=0.6 but ≈ 0.116 at p=1.0 (and the rest recede
  further). Within the tied five, risk differences are zero and — since the model rows
  don't depend on p and the rule-change entropy term is equal across equal rates — the
  *score differences among the five are identical at p=0.6 and p=1.0*. So any
  epistemic-vs-churn difference between 0.6 and 1.0 must come from outside the tied
  set (can κ·X_pair or the bonus pull :chaos/:fix2 across the gap? at 0.6 the gap is
  ~κ·ΔX-sized; at 1.0 it is not) and/or from the realized observation distribution
  (byte churn is 2× faster at p=1.0, so activity/diversity/hunger statistics differ).

Hypothesis, preregistered: **the epistemic term separates from churn exactly where the
top of the score landscape is an exact multi-way tie whose resolution the term's
*grading* can dominate** — at 0.6 the five-way risk tie is resolved by κ·X_pair's
pair-dependent (own-kind-relative, hence potentially non-transitive) preferences,
sustaining several kinds; the flat bonus preserves the transitive ambiguity/conatus
residual order among the five, collapsing to its top one or two. At 1.0 the same tie
exists, so if the mechanism is *only* the tie, epistemic should separate there too — it
did not (t = +0.72). So the hypothesis must be refined by measurement: candidates are
(i) boundary traffic with just-outside kinds (:chaos at 0.037 vs 0.116 nats), and
(ii) the faster byte churn at p=1.0 changing the observation mix that feeds the
residual ordering. The measurement plan below distinguishes (i) from (ii) by looking at
the composition of the coexisting set (does :chaos actually appear at 0.6?) and by
computing the invasion tournament from the pure score function under both observation
mixes. Also to check against the artifacts: the halting-share rise at 0.6 (t = +5.23)
has a clean candidate explanation under this hypothesis — four of the tied five are
halting-capable, so *any* term that spreads mass across the tie instead of letting the
residual order pick a single (possibly non-halting, :odd53) winner raises the
halting-capable share. The prediction failed because its premise ("the term will
select against halting kinds via rate") was a rate-term intuition, and inside the tied
set rate does not vary.

### 1.7 What would actually supply interruption: change the action space, not the X

By Prop. 1 the search for a better X on {hold, adopt-left, adopt-right} cannot yield
I-content, and by Prop. 2 the X_pair family (state-independent terms) yields no
I-divergence either. The system already contains the interruption operator — blending —
on a policy-free path. The structurally right move is to put the *content channel under
policy control*: add a fourth action (e.g. **blend**: b_i ← blend(b_{i−1}, b_i,
b_{i+1}), an action that writes the *rule*, not the σ), and score it. And the epistemic
quantity for that action already exists and is already validated: X_blend, r = +0.22 /
+0.14 against realized blend damage with a null control — the very quantity §3 of the
original problem statement set aside because it "has no σ argument." In a 4-action
space that objection dissolves: X_blend is not differencing σ candidates any more, it
is pricing the one action that touches content. The two validated quantities partition
the enlarged action space exactly: X_pair prices the σ-writing actions (injection),
X_blend prices the rule-writing action (interruption). I will not implement the
4-action policy inside `src/` (out of scope, and the file is under concurrent edit) —
but I can measure the premise it rests on with the existing state fields: run the same
twin experiment with `blend-strength` β > 0 and verify that the blend channel produces
exactly the signature the policy channel cannot: rule-layer divergence at cells whose σ
never diverged (I-content), including across σ-cycle boundaries (I-mixing).

### 1.8 Measurement plan (all deterministic from listed seeds)

1. **Artifact summary** (`interrupter_epi_summary.clj`): per-cell arm summaries from
   `reports-remote/epi/` — dominant-kind composition, shares, halting shares, adoption
   rates, matched bonuses, selected-x — the numbers the mechanism story must match.
2. **Landscape** (`interrupter_landscape.clj`): exact risk/ambiguity/conatus tables per
   p from the pure score function; tie structure; invasion tournament per arm
   (κ = 0.478 from the scale artifact, bonuses from the churn artifacts); transitivity
   check of the beats-relation among kinds, per arm and p.
3. **Twins** (`interrupter_twins.clj`): burn 300 steps under each arm × p ∈ {0.3, 0.6,
   1.0}; perturb (σ at centre cell / phenotype bit at centre); run 60 twin steps;
   record per-layer divergence masks (phenotype, σ, rule). Tests: (a) Prop. 1′ ordering
   — rule divergence at j only after σ divergence at j (exact, zero tolerated
   violations, perturbed site excluded); (b) rule-layer cone width per arm — the
   diversity-without-interruption measurement; (c) same with β = 0.15, expecting
   ordering violations everywhere — the content channel demonstrated. Also: one-bit
   observation-perturbation argmin-flip rate per arm (decision sensitivity).
4. **Persistence table** (`interrupter_persist.clj`): coupled-chain P(b_t ≠ b′_t) for
   all 144 ordered pairs, t ≤ 60, Monte Carlo over shared draw streams and uniform
   start bytes; correlation with X_pair; where absorption decouples them.
5. **Coexistence composition** (`interrupter_coexist.clj`): re-run 8 seeds × {off,
   epistemic, churn} × p ∈ {0.3, 0.6, 1.0} recording kind histograms over time,
   per-cell temporal kind counts (the §1.4 measure), and a reproduction check of one
   artifact run (guarding against the concurrently-edited `self_tuning.clj` having
   drifted from what produced `reports-remote/epi/`).

---

## 2. Results

### 2.1 What the 13 cells actually contain (`scripts/interrupter_epi_summary.clj`)

Per-arm composition of the *dominant* kind across the 32 seeds (κ = 0.478 confirmed from
the embedded scale artifact; matched bonuses: 0.15 @ p=0.3, 0.8 @ p=0.6, 0.1 @ p=1.0):

| p | off dominant (share) | epistemic dominant (share) | churn dominant (share) |
|---|---|---|---|
| 0.10 | :identity 32/32 (1.000 ± .000) | :identity 16, :chaos 16 (0.513) | — |
| 0.15 | :identity 32/32 (1.000) | :identity 19, :chaos 13 (0.516) | — |
| 0.30 | :identity 32/32 (1.000) | :identity 29, :odd53 2, :chaos 1 (0.503) | :identity 21, :builder 11 (0.504) |
| 0.60 | :chaos 27, :odd53 5 (0.928) | **:identity 28, :odd53 4 (0.425 ± .075)** | **:odd53 24**, others 8 (0.497 ± .014) |
| 1.00 | :odd53 23, :collapser 5, :chaos 4 (0.633) | **:chaos 31**, :even4 1 (0.506) | :collapser 14, :odd53 11, :chaos 7 (0.504) |

Adoption rates: off 0.003–0.077; epistemic 0.958–0.999; churn 0.988–1.000. Mean
selected-x in the epistemic arm: 0.841 / 0.815 / 0.965 / 0.966 / 0.658 across the five p.

Three facts that reshape §1.6 immediately:

1. **The off-arm argmin is :identity at every p ≤ 0.3** — not a fix-0 kind at 0.3, where
   identity carries the *worst* risk (≈ 0.075 nats). So at p ≤ 0.3 the ambiguity+conatus
   residual (which favours identity) outweighs the entire risk landscape's spread; risk
   only starts steering the off arm at p ≥ 0.6 (chaos) and 1.0 (odd53/collapser).
2. **The epistemic arm is in a strong-coupling regime**: adoption ≈ 0.96–1.0 means nearly
   every cell changes kind nearly every step. κ·X_pair (up to 0.478) is larger than
   almost every score gap. Selected-x ≈ 0.97 at p = 0.3/0.6 says the exchanges are almost
   all X = 1.0 pairs — and X_pair(A, :identity) = (8 − fix(A))/8, maximal per disagreeing
   position, so the X = 1.0 exchanges are precisely **fix-0 ↔ :identity swaps**. The term
   does not "avoid identity"; identity is the *best epistemic partner in the vocabulary*
   (every position moves, and every moved position carries weight 1).
3. **At p = 1.0 the identity bridge snaps**: the risk gap from the tied five to :identity
   is ≈ 1.57 nats ≫ κ, while at p = 0.6 it is ≈ 0.46 < κ = 0.478 (for X = 1.0 partners).
   Selected-x drops to 0.658 and the arm's dominant flips to :chaos (gap 0.116, easily
   bridged). This is a quantitative candidate mechanism for "separation at 0.6 only,"
   sharpened and tested in §2.2.

### 2.2 The score landscape, exactly (`scripts/interrupter_landscape.clj`)

Pure-function computations over `efe/score-policy` (λ = 0.55 as ran; κ = 0.478;
matched bonuses 0.15/0.8/0.1). Three results. (§1.8's promised "invasion tournament +
transitivity check" is delivered in the sharper form of a wall-dynamics classification —
the swap/sweep relation *is* the pairwise beats-relation, evaluated at the actual
decision rule including tie-breaks, rather than on score values alone.)

**Wall dynamics explain the diversity effect completely.** At a sharp A|B kind wall,
classify the two wall cells' joint decision per observation bin: both adopt across
(*swap* — the wall oscillates in place, a stable interleaving), one side adopts
(*sweep* — the wall translates, the better kind grows), or neither (*frozen*). Counts
over 132 ordered pairs × 9 observation bins:

| p | arm | swap | sweeps | frozen |
|---|---|---:|---:|---:|
| 0.3/0.6/1.0 | off | **0** | 1158 | 30 |
| 0.3 | epistemic | 970 | 218 | 0 |
| 0.3 | churn | 728 | 460 | 0 |
| 0.6 | epistemic | 932 | 256 | 0 |
| 0.6 | churn | 1168 | 20 | 0 |
| 1.0 | epistemic | 682 | 506 | 0 |
| 1.0 | churn | 300 | 888 | 0 |

The off arm has *no* swap-stable wall anywhere: every wall translates until a
monoculture absorbs (dominant share 1.000 at p ≤ 0.3 in §2.1). Both bonus terms
convert walls from translating to oscillating — that, and only that, is the diversity
mechanism. It is a fixed-landscape (state-independent) effect, exactly as §1.3 argued.

**X_pair's grading is a fixed-point-count gradient in disguise at the top end.**
X_pair(A, :identity) = (8 − fix(A))/8 = 1.000 for every fix-0 kind: :identity is the
globally best "epistemic partner." The epistemic arm's selected-x ≈ 0.97 at p = 0.3/0.6
(§2.1) is the signature of perpetual fix-0 ↔ :identity swapping.

**The identity bridge quantifies the p regimes.** Adopting :identity against holding
:odd53 (at the modal observation): bridged at every p, but the margin collapses from
0.79 nats (p=0.1) to **0.007 nats at p=1.0** (2.6127 vs 2.6195). Identity-involving
swap-stable pairs: 11 at p ≤ 0.6, 3 at p = 1.0. The κ·X_pair = 0.478 discount for an
X = 1.0 partner just clears the :odd53→:identity risk gap of 0.459 at p = 0.6 and is
razor-thin against 1.57 − Δambiguity ≈ 0.007 at p = 1.0 — which is why the epistemic
arm's composition flips from identity-centred (p ≤ 0.6) to :chaos-centred (p = 1.0,
gap 0.116 ≪ κ·0.875 = 0.418).

**A caution the wall table forces:** raw swap counts do *not* by themselves predict the
observed epistemic-vs-churn difference at p = 0.6 (churn has *more* swap configurations
there, 1168 vs 932, because bonus 0.8 out-discounts κ·X ≤ 0.478 — yet churn collapsed
toward :odd53 dominance while epistemic held five kinds). Wall stability is a pair-local
property; which interleavings *survive competition when three or more kinds meet* is
what the coexistence re-runs (§2.4) must show. I flag this now so the mechanism claim
in §2.4 is tested against it rather than papered over.

### 2.3 The multi-step quantity exists, and it dissolves its own usefulness (`scripts/interrupter_persist.clj`)

X_persist(A,B; t) = P(b_t ≠ b′_t) for the two byte chains coupled under shared draws
from a common uniform start (256 bytes × 40 shared k-streams per ordered pair; the t=1
column reproduces X_pair, r = 0.982, residual consistent with the 40-stream draw noise;
the exact one-step identity was verified exhaustively in the previous answer).

| | mean | sd across 132 differing pairs |
|---|---:|---:|
| X_pair (= t=1) | 0.62 | 0.237 |
| X_persist(5) | 0.91 | 0.120 |
| X_persist(10) | 0.93 | 0.091 |
| X_persist(20) | 0.94 | 0.084 |
| X_persist(60) | 0.94 | 0.087 |

- It is *not* the one-step quantity iterated: r(X_persist(60), X_pair) = 0.68 over all
  pairs (0.65 outside the all-even block). Low-injection pairs (X_pair ≈ 0.19–0.28,
  e.g. :even1↔:odd53, :fix2↔:fix3) still reach ≈ 0.95 persistent divergence. Healing
  is real but small (4% of mixed pairs healed at t=60); the all-even × all-even block
  is the exception — immune bytes lock in agreement or divergence (persist 0.75,
  healed 14%, r with X_pair 0.95).
- **But the table saturates.** By t ≈ 5–10 nearly every differing pair sits at 0.9+,
  and the across-pair spread collapses from 0.237 to 0.087, most of which is the
  all-even block. As a decision quantity, X_persist(≥10) ≈ 0.94·1{σ′ ≠ σ}: the *graded*
  information in "which σ do I adopt" is a transient of the first ~5 steps; what
  persists is only *whether you switched at all*.

This is the measured mechanism for the headline v2 result. **The churn control matched
the epistemic term because, at any horizon past the mixing time, the epistemic content
of the policy choice IS the adoption indicator.** A constant adoption bonus is not an
unfair straw control that happens to match — it is what X_pair itself converges to
under the dynamics' own mixing. The one p where they separated is the one where the
*transient* grading interacts with an exact tie structure (§2.2, §2.4).

### 2.4 Coexistence composition and the p = 0.6 mechanism (`scripts/interrupter_coexist.clj`)

Re-runs of 8 of the 32 v2 seeds × {off, epistemic, churn} × p ∈ {0.3, 0.6, 1.0}, 600
steps, recording what the artifacts don't (rows: `analysis/interrupter-coexist-rows.edn`).

**Reproduction check: FAILED exactly, reproduced structurally — flagged.** Same seed
(2026084300), p=0.6, epistemic: artifact adoption 0.99969 / selected-x 0.96490 /
dominant-share 0.425; this tree today gives 0.99979 / 0.98308 / 0.5125. The working
tree's `self_tuning.clj` was edited this morning (concurrent agent; the apply/blend
coin restructuring changes the draw streams), so the run-time code is not on disk to
diff against. Every *aggregate* behaviour I compared matches the artifacts (off
monocultures to :identity at 0.3 in 8/8; epistemic is identity-centred at 0.3/0.6 and
:chaos-centred at 1.0; adoption and selected-x within 2%), so I treat the current tree
as the same system at the mechanism level — but every number in this section is a
measurement of the *current* dynamics, not a re-derivation of the artifact numbers.

| p | arm | kinds ≥ 5% (last-100 mean) | wall density | period-2 osc | kinds seen /cell | pooled final composition (top) |
|---|---|---:|---:|---:|---:|---|
| 0.3 | off | 1.00 | 0.000 | 0.000 | 2.4 | :identity 640/640 |
| 0.3 | epistemic | 3.05 | 0.982 | 0.996 | 4.6 | :identity 321, :chaos 142, :odd53 103, :even8 46 |
| 0.3 | churn | 2.09 | 0.979 | 1.000 | 3.7 | :identity 322, :builder 315 |
| 0.6 | off | 1.51 | 0.068 | 0.024 | 3.7 | :chaos 589, :odd53 51 |
| 0.6 | epistemic | 4.19 | 0.973 | 0.996 | 4.3 | :identity 252, :odd53 194, :builder 68, + 7 more |
| 0.6 | churn | 2.62 | 0.994 | 0.995 | 5.1 | :odd53 312, :identity 105, :even1 78, :even4 51 |
| 1.0 | off | 2.17 | 0.073 | 0.024 | 3.5 | :odd53 357, :collapser 190, :chaos 87 |
| 1.0 | epistemic | 3.71 | 0.967 | 0.983 | 4.4 | :chaos 320, :even4 157, :collapser 53 |
| 1.0 | churn | 2.00 | 0.987 | 0.990 | 4.3 | :odd53 319, :collapser 201, :chaos 120 |

Findings:

1. **The "diversity" state is a period-2 alternation, in both bonus arms.** Wall
   density ≈ 0.97–0.99 and oscillation rate ≈ 1.0: nearly every adjacent pair differs
   and nearly every cell swaps kind every step (the §2.2 swap-walls, tiled wall-to-wall).
   The off arm is the opposite phase: frozen or coarsening (wall ≤ 0.07). "Prevents
   monoculture collapse" concretely means "locks the field into a maximally
   interleaved oscillator."
2. **The graded term sustains richer coexistence than the flat bonus at *every* p** —
   kinds ≥ 5%: 3.05 vs 2.09, 4.19 vs 2.62, 3.71 vs 2.00. The v2 conclusion "the effect
   survives churn only at p = 0.6" is partly a **metric artifact**: in an alternating
   state the single-most-common-kind share pins at ≈ 0.5 whether the hub kind alternates
   with one partner (churn: identity↔builder at 0.3, giving 322 vs 315) or with a
   *diverse pool* (epistemic: identity↔{chaos, odd53, even8, ...}). Dominant-share is
   blind to partner diversity. It separated at 0.6 only because there the epistemic
   hub's occupancy drops below the 0.5 ceiling (identity 252/640 = 0.394). Measured
   directly on the score landscape (three-way competitions: a fix-0 cell choosing
   among hold, adopt-:identity, adopt-B, over all B × 9 observation bins):
   **:identity wins 89.8% of competitions at p = 0.3, 57.3% at 0.6, 26.4% at 1.0.**
   At 0.3 identity is G-favoured outright (risk gap 0.075 ≪ κ·X = 0.478) → wins nearly
   everywhere → occupancy at the alternation ceiling (0.502). At 0.6 identity is
   G-*dis*favoured (risk gap 0.459 ≈ κ·X) and survives only where the bridge clears
   the local competition — 57% of cases → partial occupancy 0.394, *below* the
   ceiling, which is the one thing dominant-share can see. At 1.0 the bridge is dead
   (gap ≈ 1.57; identity wins 26%) and the hub re-forms on :chaos (gap 0.116,
   occupancy back at ceiling 0.500). **"Why there?": the share metric detects the
   grading only where the hub kind's occupancy is bridge-limited, and
   κ·X_max ≈ Δrisk(:identity) lands at p ≈ 0.6 for this κ.**
3. **Temporal kernel mixing (the §1.4 measure): the bonus arms do buy it** — kinds seen
   per cell 4.3–5.1 vs off's 2.4–3.7 — and churn buys it as much or more (5.1 at 0.6).
   So the within-cell, cross-cycle mixing effect is real but is an adoption-rate
   effect, not an epistemic one, exactly as preregistered.
4. The halting-capable-share rise at 0.6 has the predicted composition mechanism: off
   collapses to :chaos (halting 0.00); both bonus arms spread mass across the risk-tied
   five, four of which are halting-capable (epistemic 0.155, churn 0.245 — churn even
   higher, so this is not an epistemic effect either; the v2 prediction failed because
   inside the risk-tied set the rate intuition has nothing to grip).

### 2.5 The interruption measurement itself (`scripts/interrupter_twins.clj`)

8 seeds × 9 (arm × p) cells; burn 300 steps, then 60-step shared-noise twins for a
σ-replacement at cell 40 and a phenotype-bit flip at cell 40; β = 0 (as ran) and the
same phenotype twin with β = 0.15 (blend on, both twins). Rows:
`analysis/interrupter-twins-rows.edn`.

**Proposition 1′ holds exactly.** Across all 144 β = 0 twin pairs (9 cells × 8 seeds ×
2 perturbations; ~690k cell-step comparisons): **zero** cells whose rule diverged
without a strictly earlier σ divergence at that same cell. Rule-layer damage is carried
by σ-decision damage and nothing else. With β = 0.15 the violations appear on cue —
101 across the same design (e.g. 36 in off/p=1.0, 24 in epistemic/p=1.0): blending, and
only blending, moves rule content across cells without the σ layer's involvement. The
operator's space-time reading ("rule layer flows vertically") is a theorem of the β = 0
path, now measured as one.

**The epistemic term does not add interruption — it removes what little existed.**
One-bit decision sensitivity (fraction of cells whose argmin changes when a neighbour's
phenotype bit is flipped, at the burnt-in state) and rule-layer damage at t = 60
(mass = diverged cells of 80; hw = cone half-width) for the σ-perturbation twin:

| p | arm | sensitivity | gen mass @60 | gen half-width @60 |
|---|---|---:|---:|---:|
| 0.3 | off | 0.000 | 0.4 | 0.1 |
| 0.3 | epistemic | 0.016 | 2.6 | 2.3 |
| 0.3 | churn | 0.000 | 0.4 | 0.1 |
| 0.6 | off | **0.205** | **16.1** | **10.9** |
| 0.6 | epistemic | 0.002 | 1.4 | 0.9 |
| 0.6 | churn | 0.025 | 5.4 | 4.9 |
| 1.0 | off | 0.163 | 4.0 | 1.9 |
| 1.0 | epistemic | 0.017 | 1.5 | 2.6 |
| 1.0 | churn | 0.014 | 1.9 | 3.8 |

The *off* arm at p ≥ 0.6 is the most interruptible configuration in the study: its
mixed :chaos/:odd53 field sits near score ties, so ~20% of decisions flip on a one-bit
observation change, and a σ perturbation propagates to a fifth of the lattice's rule
layer within 60 steps. Adding κ·X_pair at κ = 0.478 — a state-independent bias larger
than most score gaps — drives decisions *away* from those ties: sensitivity collapses
by up to 100× (0.205 → 0.002) and the rule-layer cone shrinks by ~10× (16.1 → 1.4
cells). The phenotype-perturbation twins agree (at 0.6: gen mass 8.3 off → 0.1
epistemic — in the epistemic arm a phenotype perturbation essentially *never* reaches
the rule layer through the decision channel). My §1.3 preregistration said "no wider";
the measured result is stronger: **the term arrived with enough authority to run the
decisions (adoption ≈ 1.0, selected-x ≈ 0.97), and a decision layer run by a
state-independent term is deaf** — the exact opposite of interruption. Diversity was
bought *with* the channel that interruption would have needed.

Caveats, stated plainly. (i) Sensitivity and cone size are functions of the whole arm
(field composition and tie structure), not of the term alone — off@0.3 is a frozen
monoculture and also has zero sensitivity; against *that* baseline the epistemic arm's
cone is slightly wider (2.6 vs 0.4 cells at t=60, sensitivity 0.016 vs 0.000), which
is the §1.3 prediction's own let-out measured: the small extra propagation at 0.3 is
wall-density (the perturbed σ has an oscillating field to persist in), not decision
sensitivity. The clean comparative claims are: both bonus arms sit at sensitivity
≈ 0.00–0.03 at every p (differences between them noise-level), and wherever the
baseline has real interruption capacity (off at p ≥ 0.6, sensitivity 0.16–0.21) the
term destroys it rather than adding to it. (ii) The β = 0.15 runs produced ordering
violations in 6 of the 9 cells, not "everywhere" as §1.8 loosely put it — in the other
three the twins barely diverged at all within 60 steps; violations appear wherever
divergence does. No β = 0 run produced any, anywhere.

---

## 3. Conclusions

**The central question has a two-part answer, one part proved and one part measured.**

**(a) On a channel that carries transforms, content interruption is impossible for any
epistemic quantity — this is structural, and it is now a measured fact, not just an
argument.** The genotype update reads (own byte, own σ, private shared-seed draws); the
three actions write σ only. Any score term, however constructed, can only change which
kernel runs at a cell — it cannot make one cell's rule read another's. The falsifiable
corollary held exactly: in 144 shared-noise twin pairs across every arm and p, not one
cell's rule diverged without a strictly earlier σ divergence at that cell; switching on
the blend operator (β = 0.15) produced 101 violations of that same ordering on cue
(72 pairs, all violations in β > 0 pairs).
The operator's "rule layer flows vertically" is the correct reading of a theorem of
this code path.

**(b) Divergence-type interruption is not impossible in principle — the decision layer
does read the neighbourhood — but it cannot be bought by this family of terms, and the
measurements show the shipped term spent it.** Three measured facts close this:

1. A term must be large to matter (κ·X ≈ 0.48 against score gaps mostly < 0.1), and a
   large state-independent term takes over the argmin (adoption ≈ 1.0, selected-x
   ≈ 0.97): decisions become nearly deaf to the state. One-bit decision sensitivity
   fell up to 100× vs off (0.205 → 0.002 at p = 0.6), and rule-layer damage cones
   shrank ~10×. **Maximum interruptibility in this study was achieved by no term at
   all** — the off arm at p = 0.6, whose mixed field sits near score ties.
2. The graded content of "which σ" is a transient. X_persist — the honest multi-step
   generalisation, a coupled-chain table that is *not* the one-step quantity iterated
   (r = 0.68 with X_pair) — saturates at ≈ 0.94 for essentially every differing pair
   within ~5–10 steps, spread collapsing 0.237 → 0.087. Past the mixing time, the
   epistemic content of the policy choice IS the adoption indicator, which is exactly
   the churn control. The v2 headline ("matched churn buys most of the same
   diversity") is not an embarrassment for the term; it is its asymptotics.
3. What the diversity actually is: both bonus arms lock the lattice into a period-2,
   wall-density-≈1.0 alternation (§2.4). Off has zero swap-stable walls in the exact
   landscape; both bonuses create hundreds. Diversity here = oscillating interleaving,
   a fixed-landscape effect — precisely the state-independent mechanism §1.3 predicted,
   with no lateral information flow involved.

**So "diversity without ergodicity" is the right diagnosis only after splitting
"ergodicity" three ways, and each part is separately measurable (and now measured):**
lateral content transport — absent, exactly (0/144); lateral divergence propagation —
present in the substrate but *reduced* by the term (sensitivity/cone tables); temporal
kernel mixing — actually *increased* (kinds-seen-per-cell 2.4–3.7 → 4.3–5.1, breaking
the per-cell cycle factorization in time), but equally by churn, since it is an
adoption-rate effect. The layer's missing ergodicity is specifically *spatial*, and
that is the action space's property, not the quantity's.

**Why p = 0.6 and only there (the mechanism, worth more than a new quantity):** the
dominant-share metric is pinned at ≈ 0.5 by the alternating state and is blind to
partner diversity; on a metric that sees it (kinds ≥ 5%), the graded term beats flat
churn at *every* p tested (3.05/4.19/3.71 vs 2.09/2.62/2.00). The share metric
separated only at 0.6 because that is where the hub kind's occupancy is
*bridge-limited*: :identity wins the three-way landscape competitions 89.8% / 57.3% /
26.4% of the time at p = 0.3 / 0.6 / 1.0 (κ·X_pair(fix-0, :identity) = 0.478 against
identity risk gaps of 0.075 / 0.459 / 1.57), so only at 0.6 does the hub sit below the
alternation ceiling where the share metric can register it. **The condition the term
needs is not "p = 0.6"; it is κ·ΔX of the same order as the score gaps it must
arbitrate** — grading regime, not authority regime. At this κ that coincidence
happens, for the identity hub, at ≈ 0.6.
(Caveat: kinds ≥ 5% was not preregistered; it is consistent across all three p × 8
seeds but should be preregistered before being relied on.)

**Recommendations.**

1. **If interruption is wanted, change the action space, not the quantity.** Add a
   rule-writing action (blend with neighbours) to {hold, adopt-left, adopt-right} and
   gate it by the score. The epistemic quantity for that action already exists and is
   already validated on exactly the right target — X_blend (r = +0.22/+0.14 vs realized
   blend damage, null control clean). Its original disqualification ("no σ argument, no
   spread across candidates") dissolves in a 4-action space, where it prices the one
   action that touches content while X_pair prices the ones that touch transforms. The
   premise is measured here: β > 0 is the only thing in the system that produced
   content interruption. (Not implemented in `src/` — out of scope for this note and
   the file is under concurrent edit.)
2. **If the term is kept as a diversity device, κ has too much authority.** The
   calibration matched within-cell spreads at t = 0, but the realized regime is
   term-dominated (adoption ≈ 1.0). A κ small enough that adoption stays well below 1
   would keep decisions inside the grading regime — where §2.4's mechanism says the
   term's actual comparative advantage over flat churn (partner diversity) lives — and
   would cost less decision sensitivity. Not measured here beyond the endpoints; a κ
   sweep at p = 0.6 with the kinds ≥ 5% and sensitivity metrics preregistered is the
   natural next cell.
3. **Retire dominant-share as the primary diversity metric** in any follow-up; in an
   alternating regime it measures the ceiling, not the diversity.

**Limitations.** Exact reproduction of the artifacts fails (§2.4): the concurrently
edited `self_tuning.clj` changed draw streams between the v2 runs and this analysis;
all comparisons here are internally consistent on the current tree and structurally
consistent with the artifacts, but no number here re-derives an artifact number. 8
seeds per re-run cell (vs 32 in v2); one width (80), one burn-in (300), horizon 60;
sensitivity probed with phenotype-bit flips only (the diversity observation channel —
genotype distinctness — was not separately perturbed); the wall-dynamics table is
exact but pair-local (two-kind walls, sharp interface); X_persist uses 40 shared
k-streams per pair (t = 1 noise ± ~0.05, structure verified exhaustively in the
previous answer); kinds ≥ 5% is post hoc. The impossibility claim (a) is code-path
specific: it is a statement about the β = 0 self-tuning path that ran, and stops being
true the moment any rule-writing action is added — which is recommendation 1.
