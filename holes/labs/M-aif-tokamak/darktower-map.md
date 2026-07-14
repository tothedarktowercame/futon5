# DarkTower Compliance Mapping: MetaCA Tokamak (M-aif-tokamak, Slice 0)

**Status:** DERIVE, read-only design. No Lean code written (Slice 1+).
**Date:** 2026-07-14
**Compliance standard:** `mathlib4/DarkTower/AIF-COMPLIANCE.md`
**Exemplar mirrored:** `mathlib4/DarkTower/WMPipelineExample.lean` (the War
Machine flight as a feed-forward BV process expression over `Stage`).
**CA-dynamics reference:** `mathlib4/DarkTower/MetaCAExample.lean` (the feed-forward
cell-update `read ◁ combine ◁ mutate ◁ write`).

---

## 1. The Stage enum for one tokamak tick

Per compliance invariant 1 and the "Lean stages ≙ Clojure stages" gate, the
`Stage` enum must name the **AIF target controller's** actual per-tick stages.
The *current* cyber-MMCA controller (`run-controller`,
`cyber_mmca_compare.clj:195–246`) has only 4 bare stages (step → select → apply
→ commit — see `r-map.md` §"per-tick stages"). The AIF port will expand this to
the following 8 stages, which the Lean `Stage` enum must mirror:

```lean
inductive Stage where
  | step       -- run the CA forward (runtime/run-mmca): produces the raw observation
  | perceive   -- predictive-coding update of belief μ from the observation (R1/R3)
  | predict    -- forward-predict next macro-features under each candidate action (R4)
  | evaluateF  -- the epistemic leg: ΔF (ambiguity) over predicted outcomes
  | evaluateG  -- the pragmatic leg: ΔG (KL-risk to C) over predicted outcomes
  | gate       -- the ∧: combine epistemic + pragmatic into G_efe per action
  | select     -- softmax over -G/τ; abstain if below threshold (R6/R14/R9)
  | enact      -- apply the selected action via adjust-params; write exotype params
  | trace      -- emit per-tick F, G, τ, action, regime (R8)
  deriving DecidableEq, Repr
```

**Why these 9 constructors and not the current 4:** the compliance gate requires
the Lean stages to mirror the controller's *actual* stages. Since the AIF port
(Slices 1–4) will introduce perceive, predict, evaluate, and trace stages that
do not exist yet, the enum names the **target** architecture. The current 4 bare
stages are a strict subset: `step` (exists), `select` (exists as
`choose-actions-hex`, will become softmax), `enact` (exists as `adjust-params` +
`assoc`), `trace` (partially exists in `windows-out` but has no F/G/τ). The
missing stages (`perceive`, `predict`, `evaluateF`, `evaluateG`, `gate`) are
precisely the AIF content the port adds.

**Naming rationale (mirrors `WMPipelineExample`):**
- `step` ↔ `wake` (the entry point — here, the CA advance, not a wake event).
- `perceive` ↔ `judge` (form the belief / operational hypothesis).
- `predict` ↔ `psi` (roll out the forward model).
- `evaluateF`/`evaluateG` ↔ `gateF`/`gateG` (the copar legs — see §3).
- `gate` ↔ `gate` (the ∧ combining the legs).
- `select` ↔ `select` (softmax + abstain).
- `enact` ↔ `enact` (execute the chosen action).
- `trace` ↔ `trace` + `learn` (emit diagnostics; learning is external recurrence
  for v1, matching the feed-forward default).

---

## 2. The BV seq spine for one tick

Following `WMPipelineExample.flight` (L50–58), one tokamak tick is a left-nested
`BV.seq` (◁) spine, with the two EFE evaluation legs held simultaneous by
`BV.copar` (⅋) at the gate position:

```lean
def tick : BV Stage :=
  BV.seq (BV.atom Stage.step)
    (BV.seq (BV.atom Stage.perceive)
      (BV.seq (BV.atom Stage.predict)
        (BV.seq (BV.copar (BV.atom Stage.evaluateF) (BV.atom Stage.evaluateG))
          (BV.seq (BV.atom Stage.gate)
            (BV.seq (BV.atom Stage.select)
              (BV.seq (BV.atom Stage.enact)
                (BV.atom Stage.trace)))))))
```

**Spine shape:** `step ◁ perceive ◁ predict ◁ (evaluateF ⅋ evaluateG) ◁ gate ◁ select ◁ enact ◁ trace`

This mirrors `WMPipelineExample.flight` exactly in structure:
`wake ◁ judge ◁ psi ◁ cascade ◁ (gateF ⅋ gateG) ◁ gate ◁ select ◁ enact ◁ trace ◁ learn`.
The tokamak tick folds `cascade` into `predict` (the forward model rollout is the
cascade) and drops `learn` (externalized for v1 — recurrence is the outer
`run-controller` loop, just as CA generations iterate the one-step object).

---

## 3. Which EFE legs are `copar` (⅋, non-signalling)

Per compliance invariant 2, the EFE decomposition into epistemic (ΔF) and
pragmatic (ΔG) legs is held by `BV.copar`, **not** `BV.seq`:

```lean
BV.copar (BV.atom Stage.evaluateF) (BV.atom Stage.evaluateG)
```

- **`evaluateF`** — the epistemic leg: the ambiguity term
  `Σ_ch ½·ln(2πe·σ²_ch)` over predicted macro-feature variances. This is the
  expected information gain — "what would I learn if I took this action?"
- **`evaluateG`** — the pragmatic leg: the KL-risk term
  `Σ_ch KL(N(μ_ch, σ²_ch) ‖ C_ch)` against the EoC preference C. This is the
  pragmatic value — "how far from my preferred EoC confinement would this action
  put me?"

These are **independent simultaneous readings of the same `predict` cascade** —
neither may signal the other. The `gate` stage (the ∧) combines them into the
scalar `G_efe` per candidate action. This is exactly the
`WMPipelineExample` idiom (`gateF ⅋ gateG`), and the ant port's Slice 2 must do
the same (parity checkpoint).

**Extension note:** if a third EFE term is added later (e.g. an exploration
bonus), it joins as a third `copar` leg: `copar evaluateF (copar evaluateG
explore)`. The copar is associative (`BV.Cong.copar_assoc`, `BV.lean:79`).

---

## 4. Which observation feeds become satiety-graded TypedHoles (and their grading)

Per compliance invariant 1 (the single most important property), every external
dependency (observation/feed) is a port on a satiety-graded `TypedHole`. The
tokamak has one feed interface — the macro-feature observation — with 5 ports:

```lean
/-- The 5 macro-feature observation channels (R2 ABI). -/
inductive ObsPort where
  | pressure     -- normalized avg-change (metrics.clj:626)
  | selectivity  -- normalized 1-avg-unique (metrics.clj:628)
  | structure    -- normalized temporal-autocorr (metrics.clj:629)
  | activity     -- normalized avg-change (metrics.clj:630)
  | regime       -- classify-regime keyword: freeze|magma|static|chaos|eoc
  deriving DecidableEq, Repr

/-- What fills each observation port: a normalized reading or a regime label. -/
inductive ObsFeed where
  | reading (v : Float)     -- a normalized [0,1] macro-feature value
  | regimeLabel (r : RegimeLabel)
  deriving DecidableEq, Repr
```

**Satiety grading:** in the fed (live) apparatus, all 5 ports grade `canon`
(fully fed — the CA step produces them every tick). The satiety-graded hole:

```lean
def obsHole : TypedHole where
  poly := { A := ObsPort, B := fun _ => ObsFeed }
  satiety := fun _ => SatietyGrade.canon   -- all ports fed when the CA is running
```

**The starvation theorem** (Slice 3): if the CA step is severed (e.g. a lesion
zeroes the metrics-history, or the controller runs with no genotype), the
observation ports go hungry. The Lean model proves `IsHungry obsHole ObsPort.X`
for the severed port and `¬ IsHungry` when fed — exactly the
`WMPipelineExample.gammaFeedHole` idiom (L108–120). The `:kick-window`/`:kick-target`
lesion mechanism in `run-controller` (`cyber_mmca_compare.clj:214`) is the
operational correspondent of severing a feed.

**Additional feed (the preference C):** the EoC set-point C is a second feed —
not an observation but a target. It enters as a `Fill` with declared `holeType`
(compliance row "R19 preference / belief / policy / precision enter as Fills").
If C is severed (no target), `evaluateG` cannot compute KL-risk — this is a
different starvation: the *preference* port goes hungry, not the observation
port. Proposed:

```lean
inductive PrefPort where
  | eoCRegime       -- the categorical preference: prefer :eoc
  | pressureBand    -- Gaussian target over :pressure
  | selectivityBand -- Gaussian target over :selectivity
  | structureBand   -- Gaussian target over :structure
  deriving DecidableEq, Repr
```

Graded `canon` when C is defined (the tokamak always has a target), `payoff`
(hungry) if C is absent.

---

## 5. Which R9 properties become Lean theorems

Per compliance invariant 4 (repair-flips-the-theorem) and the R9 row, the
following are Lean theorems whose truth tracks apparatus health:

| R9 property | Theorem (proposed) | What it tracks | When it flips |
|---|---|---|---|
| **Starvation (observation)** | `IsHungry obsHole port` (severed) / `¬ IsHungry obsHole port` (fed) | Whether the CA step feeds the macro-feature ports. | Repairing a severed CA feed flips `IsHungry` → `¬ IsHungry`. The single most important property (invariant 1). |
| **Starvation (preference)** | `IsHungry prefHole PrefPort.eoCRegime` (no C) / `¬ IsHungry prefHole ...` (C defined) | Whether the EoC set-point is wired. | Defining C flips the satiety to `canon`. |
| **Conservation** | The discard equation ↔ VFE normalization (stated in Slice 4 per `E-the-dark-tower-2` §4). The control law conserves normalization: the softmax over actions sums to 1 (a probability distribution), and the discard of the unselected actions is the VFE normalization. | That the controller's action selection is a proper distribution, not an arbitrary ranking. | A bug that breaks softmax normalization (e.g. negative G values dominating) violates this. |
| **Coverage** | `Coverage` proof (Slice 5): the controller's discharge projections (the `windows-out` trace records) are all views of existing facets — no orphan outputs. Every field in the per-tick record (`:actions :update-prob :match-threshold :regime :F :G :τ`) is traceable to a stage output. | That the trace has no ghost fields. | Adding an untraceable output field breaks Coverage. |
| **Abstain-fires** | A provable condition: `∃ threshold, ∀ action, G_efe(action) > threshold → select returns :hold`. The controller abstains (holds) when no action offers sufficient expected improvement. | That the controller can refuse to act on epistemic grounds (unlike the current `choose-actions-hex` which always acts). | Lowering the threshold below all G values makes abstain fire; the theorem tracks this. |

---

## 6. The feed-forward vs within-diagram-feedback verdict

**VERDICT: FEED-FORWARD. One tokamak tick can be modelled feed-forward
(recurrence external), exactly like `WMPipelineExample`. No stage feeds an
earlier open port of the same diagram. The open-diagram (Comb.lean coend) layer
is NOT triggered by the tokamak tick.**

### Why (detailed reasoning)

The compliance §"complexity boundary" asks: does a stage genuinely feed an
earlier open port of the same diagram? Examining each stage in the tick spine
(`step ◁ perceive ◁ predict ◁ (evaluateF ⅋ evaluateG) ◁ gate ◁ select ◁ enact ◁ trace`):

- **`step`** reads CA state (genotype, phenotype, exotype params) and produces
  the observation. It feeds `perceive`. Forward only.
- **`perceive`** reads the observation and updates belief μ. It feeds `predict`.
  The μ-update is `μ ← μ + αΠε` — a **fixed-form** update, not a fixed-*count*
  iterative loop. It is one application of the update rule, which unrolls as a
  single `seq` node. Forward only.
- **`predict`** reads μ and rolls out the forward model per candidate action. For
  H=1 (Slice 1–3), this is one `runtime/run-mmca` call per action — a fixed
  5-branch map (one per action in the candidate set). For H>1 (Slice 4, the
  rollout), each horizon step is a fixed-count unroll: `predict ◁ predict ◁ ...`
  (H deep). **This is a fixed-count loop that unrolls as `seq`** — exactly the
  case the compliance doc says fits current machinery ("Fixed-count inner loops
  unroll as a seq chain"). Forward only.
- **`evaluateF ⅋ evaluateG`** read the `predict` output simultaneously (copar,
  non-signalling). Neither feeds back to `predict`. Forward only.
- **`gate`** combines the copar legs. Forward only.
- **`select`** reads `gate` output (G per action) and picks one. Forward only.
- **`enact`** applies the selected action to exotype params. Forward only.
- **`trace`** emits diagnostics. Terminal — feeds nothing within the tick.

**No stage's output re-enters an earlier port of the same tick.** The recurrence
is the *outer* `run-controller` loop (`cyber_mmca_compare.clj:202`): tick N's
`enact` writes params that tick N+1's `step` reads. This is external iteration,
precisely like CA generations iterate the one-step `MetaCAExample.cellUpdate`
object, and precisely like `WMPipelineExample`'s `learn → next wake` loop is
external.

### The one place to watch (but it does NOT trigger)

The **`predict` rollout at H>1** (Slice 4) is the closest thing to
within-diagram feedback: each rollout step's predicted state feeds the next
rollout step's forward model. But this is a **fixed-count** loop (horizon H is a
compile-time parameter), so it unrolls as `seq` — it does not need dynamic-count
feedback. If, in a future slice, the horizon becomes adaptive (the controller
decides mid-rollout to extend H based on what it sees), THAT would be genuine
within-diagram feedback and would trigger the open-diagram layer. For v1 (fixed
H), it does not.

**Bottom line:** model one tick feed-forward. Recurrence is the outer loop. The
`Comb.lean` coend layer is not needed for the tokamak v1. If a future slice
introduces adaptive-horizon rollout or within-tick belief revision (μ updated
multiple times from the same observation), revisit this verdict.

---

## 7. Explicit yes/no answers to the controller-capability questions

Per the handoff: "does cyber-MMCA already have..."

| Question | Answer | Seam |
|---|---|---|
| **(a) a preference/set-point over regime?** | **No.** `ok-regime?` (`cyber_mmca_compare.clj:113–114`) is a hard binary gate `(not (#{:freeze :magma} regime))` used inside `choose-actions-hex`'s `cond` — a thermostat switch, not a graded preference distribution C. There is no Gaussian target, no KL-distance-to-preference, no `:eoc`-as-mode computation anywhere. | `cyber_mmca_compare.clj:113` |
| **(b) any per-channel precision on macro-features?** | **No.** `windowed-macro-features` (`metrics.clj:596`) produces 4 numeric channels (`:pressure :selectivity :structure :activity`) with no per-channel weight, variance, or precision. `choose-actions-hex` reads them with raw numeric thresholds in a `cond`. Grep for `precision` across the controller script: zero matches. | `metrics.clj:596`; grep of `cyber_mmca_compare.clj` |
| **(c) any commitment temperature?** | **No.** Action selection is a deterministic hard `cond` (`choose-actions-hex`, `cyber_mmca_compare.clj:116–127`). There is no softmax, no τ, no stochastic selection. Grep for `tau\|temperature\|softmax`: zero matches. | `cyber_mmca_compare.clj:116` |
| **(d) any multi-step control rollout?** | **No.** `choose-actions-hex` is purely greedy 1-step — it reads the current window and emits actions. `run-controller` loops over windows but each window's action is computed independently with no lookahead. There is no horizon parameter, no discount ρ. | `cyber_mmca_compare.clj:116`, `cyber_mmca_compare.clj:202` |
| **(e) any per-tick free-energy scalar?** | **No.** The per-tick record emitted at `cyber_mmca_compare.clj:243` (`assoc window :controller :seed :actions ...`) contains no energy term. Grep for `free-energy\|entropy\|KL`: zero matches in the controller. (Shannon entropy IS computed inside `runtime.clj`'s `derive-metrics` at `runtime.clj:292` and `metrics.clj`'s `shannon-entropy` — but this is a CA-state entropy for regime classification, not a variational free energy.) | `cyber_mmca_compare.clj:243`; `runtime.clj:292` |

---

## Compliance checklist (projected, for the built apparatus)

- [x] (designed) one tick = a `BV` process expression over a `Stage` enum (§2)
- [x] (designed) EFE legs via `copar` (§3)
- [ ] every observation/feed is a port on a satiety-graded `TypedHole` (§4 — to build Slice 3)
- [ ] a severed feed is a provable `IsHungry`; the fed apparatus proves `¬ IsHungry` (§5 — to build Slice 3)
- [ ] belief / policy / precision enter as `Fill`s with declared `holeType` (to build Slice 2–3)
- [ ] R9 properties are Lean theorems (§5 — to build Slice 3–4)
- [ ] `lake build DarkTower` exit 0; no cheats (Slice 1+)
- [x] (answered) recurrence is external (feed-forward per tick) — §6 verdict
- [x] (designed) the Lean `Stage`s mirror the controller's target stages (§1)
