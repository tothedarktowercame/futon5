# R-Contract Audit: MetaCA Cyber-MMCA Controller (M-aif-tokamak, Slice 0)

**Status:** DERIVE, read-only audit. No product code changed.
**Date:** 2026-07-14
**Scope:** `futon5/scripts/cyber_mmca_compare.clj` (the controller ABI), plus
`futon5/src/futon5/mmca/{metrics,runtime,exotype}.clj` (the dynamics surface).
**Reference target:** `futon2/src/futon2/aif/{efe,precision,belief}.clj` (the shared
AIF core both ports must consume).
**Feature checklist source:** `futon2/holes/labs/M-aif-ants-port/r-map.md`
(commit 46bb77a) — the ant R-map this mirrors, row-for-row.

All line references are 1-based. Every row was resolved by reading the cited
source. A grep for `prefer|set-point|temperature|tau|precision|free-energy|variance|
entropy|KL|belief|prior|softmax` (case-insensitive) over the entire controller
script `cyber_mmca_compare.clj` returns **zero matches** — confirming that the
controller is currently a bare reactive loop with no AIF content whatsoever.

---

## R-map table

| R# | Contract quantity | Current MetaCA seam (fn + file:line) | Exact gap vs contract | Faithfulness note |
|---|---|---|---|---|
| **R1** | belief map μ (operational hypothesis) | **ABSENT.** The controller holds no belief state. The `loop` in `run-controller` (`cyber_mmca_compare.clj:202–243`) threads only CA state: `{:genotype :phenotype :kernel :exotype :metrics-history :gen-history :phe-history}`. The `window` produced by `next-window` (L161) is a per-tick observation consumed once then discarded. There is no accumulator predicting the next observation, no sensory-running-mean, nothing like the ants' `perceive/ensure-mu` (`perceive.clj:55`). | **Build required.** A belief μ over macro-features (running predicted means of `{:pressure :selectivity :structure :activity :regime}`) must be introduced. Reuse the ant `perceive/update-sensory` predictive-coding shape (`perceive.clj:107`), ported to the 5-channel macro-feature ABI. | **Absent.** |
| **R2** | typed observation o | `windowed-macro-features` (`metrics.clj:596–641`): returns per-window `{:w-start :w-end :summary :pressure :selectivity :structure :activity :regime}`. Channels `:pressure :selectivity :structure :activity` are normalized [0,1] via `normalize-window` (`metrics.clj:552–558`, which enforces the [0,1] contract with `ensure-normalized!` L547). `:regime` is a keyword in `#{:freeze :magma :static :chaos :eoc}` from `classify-regime` (`metrics.clj:643–671`). Called once per tick at `cyber_mmca_compare.clj:165`. | **No gap — structurally complete as an observation ABI.** 5 channels, all typed and bounded. Gap for the AIF port: there is **no variance/covariance** tracked alongside these point readings (the ant gap at R3 applies identically here). | **Complete** (FEP-derived observation ABI). |
| **R3** | predictive-coding update μ←μ+αΠε + variance floor | **ABSENT.** There is no μ to update (R1 is absent) and no precision Π (R7 is absent). The closest thing is `windowed-macro-features` recomputing features from scratch each window from the raw `metrics-history` — there is no exponential/predictive running mean. | **Build required.** Port the ants' `perceive/compute-errors` (`perceive.clj:81`) + `perceive/update-sensory` (`perceive.clj:107`) shape: `μ_k ← μ_k + α·Π_k·(o_k − μ_k)` over the 5 macro-feature channels. Add per-channel variance σ²_k with a floor. | **Absent.** |
| **R4** | one pure forward kernel `(state,action,seed,opts)→next` | **Partially present but NOT exposed as a pure predictive kernel.** The CA step itself — `runtime/run-mmca` — is called at `cyber_mmca_compare.clj:162` inside `next-window` (L158). It IS a pure-ish step: `({:genotype :phenotype :generations :kernel :exotype :seed :lesion} → {:gen-history :phe-history :metrics-history :summary})`. It advances the CA by `W` generations deterministically given a seed. **Gap:** it is used only to *produce* the next observation, never to *predict* one for EFE. The controller has no `forward-predict` that asks "if I apply action a, what macro-features would result?" — it runs the CA, observes, reacts. There is no decoupled predict seam the way the ants' `policy/predict-outcome` (broken as it is) at least attempts. | **Build required.** Wrap `runtime/run-mmca` as a `forward-predict(state, action, seed) → predicted-macro-features`. The action (a `params` delta via `adjust-params`) must be applied to the exotype *before* the step. Reuse the ant port's `aif/forward` abstraction shape (ants-S1). | **Principled approximation** available — the CA step is a real generative kernel, unlike the ants' heuristic `predict-outcome`. This is the MetaCA port's structural advantage. |
| **R5** | unit-pure G_efe = KL-risk + entropy-ambiguity | **ABSENT.** No free-energy, KL, or entropy-of-prediction term anywhere in the controller. `choose-actions-hex` (`cyber_mmca_compare.clj:116–127`) is a hard `cond` over `regime`/`pressure`/`selectivity`/`structure` — a hand-coded thermostat, not a variational objective. | **Build required.** Compute `g-efe = KL(N(μ,σ²) ‖ C) + Σ ½ln(2πe·σ²)` over predicted macro-features, via the **shared** `futon2.aif.efe` core (not a fork). The ant R5 deep-dive found the ants' `expected-free-energy` is a hand-shaped penalty — do NOT port that; port the shared unit-pure kernel. | **Absent.** The hex heuristic is **non-FEP engineering** — it becomes a named augmentation arm with a typed residual (Slice 3), never called EFE. |
| **R6** | constructed candidate set a | `choose-actions-hex` (`cyber_mmca_compare.clj:116–127`) emits from the action vocabulary. The full action set is defined implicitly in `adjust-params` (`cyber_mmca_compare.clj:96–111`): `#{:pressure-up :pressure-down :selectivity-up :selectivity-down :hold}`. Each maps to a `±0.1` step on `:update-prob` or `:match-threshold` (clamped). `:hold` is the no-op. | **No gap — complete.** Small legible 5-action set. The mission spec's domain terminal mapping (R6 row) lists exactly these. | **Complete** (FEP-derived candidate structure). The action vocabulary is clean; the *selection policy* (hex `cond`) is what must be replaced by EFE-ranking. |
| **R7** | per-channel precision Π (adaptive) | **ABSENT.** No precision, no per-channel weighting, no variance. `windowed-macro-features` treats all 4 numeric channels uniformly. `choose-actions-hex` reads `regime`/`pressure`/`selectivity`/`structure` with equal weight. | **Build required.** Port `futon2.aif.precision`: per-channel `Π_k = 1/max(σ²_k, σ²_min)` with a rolling error-variance window over the 5 macro-feature channels. The ant port's `affect/modulate-precisions` heuristic is the analogical form to avoid. | **Absent.** |
| **R8** | per-tick variational F scalar | **ABSENT.** No F is computed or surfaced. The controller's `window` map (the per-tick record emitted at `cyber_mmca_compare.clj:243`) contains `:controller :seed :actions :sigil :update-prob :match-threshold :delta-update :delta-match :applied? :kernel` — no energy term. | **Build required.** Compute `F = ½·Σ_k Π_k·ε_k²` from the R3 predictive-coding residuals and surface it as `:F` in the per-tick trace. | **Absent.** |
| **R9** | validation properties (conservation, coverage, abstain-fires) | **ABSENT** (expected — these are Lean theorems per the DarkTower mapping, not Clojure). The Clojure side has no abstain condition: `choose-actions-hex` always returns at least `:hold`; `:null` always returns `:hold`. There is no situation where the controller refuses to act on epistemic grounds. | **Build required (Clojure):** an abstain condition (the controller returns `:abstain` when expected information gain is below threshold). **Build required (Lean):** the R9 theorems — see `darktower-map.md`. | **Absent.** |
| **R13** | policy horizon S(π), H>1 rollout | **ABSENT.** `choose-actions-hex` is purely greedy 1-step: it reads the current window and emits actions. There is no lookahead, no discount ρ, no horizon parameter. `run-controller` (L195) loops over windows but each window's action is independent of the next. | **Build required.** H>1 rollout over the R4 forward kernel with discount ρ. The CA step (`runtime/run-mmca`) is expensive (full W-generation run), so the rollout will need a cheaper surrogate or a small horizon — flag this to the owner. | **Absent.** |
| **R14** | commitment temperature τ | **ABSENT.** No τ. Action selection in `choose-actions-hex` is deterministic (a hard `cond`), not a softmax. There is no `-G/τ` anywhere. | **Build required.** τ coupled to regime volatility (high volatility → higher τ → more exploration). Softmax over `-G/τ` for the 5 actions. | **Absent.** |
| **R16** | external witness | `run-controller` (`cyber_mmca_compare.clj:195–246`) IS the harness: it runs windows, records `windows-out`, and the downstream `stats-for-controller` / CSV writer produces the comparison. It supports `:kick-window`/`:kick-target`/`:kick-half` lesions (L214). | **Partially present.** The harness exists but currently compares only `:null/:hex/:sigil`. Slice 5 adds `:aif-full` vs `:aif-no-epistemic` vs baselines, scored on time-at-EoC. The witness must be independently-seeded (the current `(+ seed idx)` per-window seeding at L216 is deterministic but the spec wants randomized genotype + seed per run). | **Out of scope for Slice 0.** Slice 5 builds the honest witness. |
| **R17** | BMR structure learning | **n/a** — not present. | Out of scope (tokamak v1). | **Out of scope.** |
| **R19** | preference C-vector | **ABSENT.** The controller has no preference, set-point, or target. `choose-actions-hex` implicitly prefers non-`:freeze`/non-`:magma` via `ok-regime?` (`cyber_mmca_compare.clj:113–114`: `(and regime (not (#{:freeze :magma} regime)))`), but this is a hard binary gate in a `cond`, not a graded preference distribution C over the observation ABI. | **Build required.** Define C = the EoC confinement preference: Gaussian targets over `{:pressure :selectivity :structure :activity}` centered on the EoC band, and a categorical preference for `:regime :eoc`. This is the tokamak "set-point" — the single most important new construct. | **Absent.** The `ok-regime?` gate is **non-FEP engineering** (a thermostat switch), not a preference. |

---

## Headline finding: the cyber-MMCA controller is a bare reactive loop

The ant R-map found a *fragmentary* AIF controller — broken in places, hand-shaped
in others, but structurally present (μ, Π, τ, C, F all exist in some form). The
MetaCA controller is **structurally absent**: it is a pure `observe → cond → act`
thermostat with zero variational content. Every AIF row from R1 through R19 is
either "complete" (the observation ABI and action set, which are clean) or
"absent" (everything else). This is not a port of a broken AIF controller; it is
building the AIF controller from scratch on a clean CA substrate.

**Structural advantage over the ants port:** the MetaCA has a *real generative
kernel* (`runtime/run-mmca`) that the ants lack (their `predict-outcome` is a
heuristic that doesn't call the world step). R4 — the hardest row in the ant
port — is substantially easier here: `forward-predict` wraps an existing
deterministic step, it does not have to be factored out of a mutating world.

---

## Quick-reference: the controller's actual per-tick stages (for the DarkTower mapping)

From `run-controller` (`cyber_mmca_compare.clj:195–246`), one tick (one iteration
of the `loop`) executes exactly:

1. **step** — `next-window` (L161) calls `runtime/run-mmca` (L162) advancing the
   CA by `W` generations, then `windowed-macro-features` (L165) computes the
   macro-feature observation `window`.
2. **select** — `choose-actions-{hex,null,sigil}` (L217–221) reads `window` and
   emits `actions`.
3. **apply** — `adjust-params` (L225) translates `actions` into
   `{:update-prob :match-threshold}` deltas.
4. **commit** — `assoc state :exotype exotype'` (L242) writes the new params into
   state for the next tick.

There is **no predict stage, no evaluate stage, no belief-update stage**. The
AIF port will insert `predict → evaluate-g-efe → softmax-select` between the
current `step` and `apply`, and add a `perceive` (belief update) stage consuming
the observation. The DarkTower `Stage` enum (see `darktower-map.md`) must name
the AIF *target* stages, not these 4 bare stages — but the 4 bare stages are the
honest baseline.
