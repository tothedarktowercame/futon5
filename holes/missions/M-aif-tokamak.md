# M-aif-tokamak — Port the modern AIF (R1–R19) into a MetaCA tokamak controller

**Status:** SPEC / DERIVE (2026-07-14). Owner: Claude. Build: staged Zai handoffs (zai-10), Claude reviews each.
**Parallel to:** `futon2/holes/M-aif-ants-port.md` (Port 1, ants, driven by zai-9). Same R1–R19 AIF
contract, **developed slice-by-slice in parity** so the shared AIF core stays honest to both domains.
**Compliance standard:** `mathlib4/DarkTower/AIF-COMPLIANCE.md` — the tokamak must be **DarkTower-native**,
not prose with a formalism bolted on later.

## Why (the reframe, shared with Port 1)

Cyberants tried to transport a CA control regime into ants and failed because (a) the wiring was inert at
the substrate boundary and (b) neither endpoint was a well-specified controller. The fix is to instantiate
the *modern* AIF (the War Machine R1–R19 loop, `p4ng/main-2026.tex`) as a real controller in **both**
domains. This mission is the CA-side twin of the ant forager: an AIF agent that **steers a MetaCA and holds
it at the edge of chaos** — a "tokamak" keeping the CA "plasma" confined at EoC, countering drift toward
freeze/barcode (dead order) or magma/chaos (boil). Once both ports exist, "what a cyberant is" becomes
definable as the (CT-checkable) transport of the learned regulator between them — or is shown redundant.

**The deep point (same as ants):** explore/exploit — here, *inject-perturbation vs preserve-structure* —
is the epistemic/ambiguity term of G, not a hand-coded switch. A tokamak minimizing `G_efe` against an
EoC-preference C should hold confinement natively.

## Dual deliverable (this is what differs from Port 1)

Per `AIF-COMPLIANCE.md`, every slice ships **two agreeing artifacts**:

1. **Running controller (Clojure, futon5):** an `:aif` controller plugged into the cyber-MMCA controller
   ABI, alongside the existing `:null/:hex/:sigil/:wiring`.
2. **DarkTower-native object (Lean, `mathlib4/DarkTower/MetaCATokamakExample.lean`):** mirrors
   `WMPipelineExample.lean` — one tick = a `BV` process expression over a `Stage` enum; observation feeds
   are satiety-graded `TypedHole`s (**starvation is a theorem**); EFE terms are `BV.copar` (⅋) legs
   (non-signalling, never sequenced); R9 properties (starvation, conservation, coverage, abstain-fires) are
   **Lean theorems a repair must flip**; completeness is a `Coverage` proof.

**Lean ≙ Clojure fidelity is a gate:** the Lean `Stage`s must mirror the controller's *actual* stages — a
faithful mirror, never a plausible-looking stand-in (same bar we hold Codex ports to).

## Domain terminal mapping (MetaCA)

| AIF role | MetaCA instantiation | Seam |
|---|---|---|
| R2 observation `o` | macro-features `{:pressure :selectivity :structure :activity :regime}` | `futon5.mmca.metrics/windowed-macro-features` (`metrics.clj:596`); regime via `classify-regime` (`metrics.clj:643`, `freeze/magma/eoc`) |
| R19 preference `C` | **the EoC confinement manifold**: prefer `:regime :eoc`, penalize `:freeze`/`:magma`; target mid pressure/selectivity band | new — the tokamak set-point |
| R6 action set `a` | `:pressure-up/-down`, `:selectivity-up/-down`, `:hold` (exotype-param knobs `:update-prob`, `:match-threshold`) | `adjust-params` (`cyber_mmca_compare.clj:~100`) |
| R4 forward model | one MMCA step the controller predicts with | `futon5.mmca.runtime/run-mmca` (per-window step, `cyber_mmca_compare.clj:161`) |
| controller ABI | `window → {:actions [...]}`; add `:aif` beside `:null/:hex/:sigil/:wiring` | `run-controller` (`cyber_mmca_compare.clj:195`); baseline heuristic `choose-actions-hex` (`:116`) becomes a named augmentation/baseline arm |

## Shared AIF core with Port 1 (coordination)

The unit-pure math is domain-agnostic and must be **one implementation both ports call** — do not fork it:
`g-efe` (`risk = Σ KL(N(μ,σ²)‖C)`, `ambiguity = Σ ½ln(2πe·σ²)`), per-channel precision update, and the
`S(π)` rollout. Reference `futon2.aif.{efe,precision}` and the ant port's Slice 2/3 output.

**RECONCILIATION DECISION (2026-07-14, Joe): shared repo = futon2.** The domain-agnostic AIF core lives
in **futon2** as one canonical namespace (lift the ant port's `ants.aif.efe` → a shared `futon2.aif.*`
core); **both** ports consume it — futon5 (tokamak) gets a `:local/root` dep on futon2 and *requires* the
shared core, replacing its own S2 `futon5.aif.efe` copy. futon2 is thus the **domain-general AIF engine**;
**futon5's long-term role narrows to wiring diagrams** (the CA/xenotype artifacts), not AIF machinery.
The reconciliation (executed when both S2s land) is: (1) extract the pure kernels to `futon2.aif` core,
(2) add the futon5→futon2 dep, (3) point the tokamak controller at the shared core, (4) prove both ports
still pass their g-efe fixtures against the single implementation. Migration of MMCA runtime out of futon5
is a *later* question, not part of this reconciliation.

## Shared MMCA substrate (2026-07-14) — local causal states = the tokamak's generative model

Landed from the DarkTower evaluator work (another session): updated `mathlib4/DarkTower/AIF-COMPLIANCE.md`
(new §"Generators, evaluators, and the Tokamak are one comb" + §"Shared substrate — local causal states"),
`mathlib4/DarkTower/EVALUATOR-SPEC.md`, and `futon5/src/futon5/mmca/local_causal_states.clj` (Rupe–Crutchfield
local causal states: light-cones → CSSR clustering w/ χ² significance → causal-state field → domains/particles).

**Load-bearing insight (compliance §47): a local-causal-state model IS an AIF generative model** — the causal
states ARE the belief-states (μ). The evaluator uses them to detect the domains/particles that are the *actual*
EoC structure; the tokamak reuses the SAME causal states as its generative model. Much as the tokamak reuses
zai-9's AIF core (g-efe/precision), it reuses this MMCA substrate — do not hand-roll EoC detection or a belief
representation. `local_causal_states.clj` API: `past/future-light-cone`, `light-cone-samples`,
`conditional-morphs`, `reconstruct-model`, `causal-state-field`, `reconstruct`.

**Two consequences:**
1. **S5 metric-validity (MUST):** the confinement metric "time at EoC" must use a VALID EoC discriminator (the
   evaluator / causal-state particle detection), NOT the crude `classify-regime` thresholds. EVALUATOR-SPEC
   banks honest fails — AIS, nn-TE, distance-TE, mean-aggregated measures all rank **chaos > complex** (they
   measure information, not coherence) — so naive EoC signals are presumed-invalid until they pass
   `SeparatesEoC`. Measuring confinement with an invalid EoC signal = a worthless result.
2. **Generative-model upgrade (S4.5, proposed):** the current belief/obs is ad-hoc macro-features and C targets
   a macro-feature mid-band — a *proxy* for EoC. Reusing `local_causal_states.clj` as the generative model
   (R1/R2/R4) lets C target actual EoC structure (domains/particles). Foundational (touches belief/C) → a
   distinct slice BEFORE S5, not crammed in. (Note: the controller already dropped the classify-regime-based
   regime-penalty as redundant in S3-parity, so its *scoring* is the Gaussian macro-feature C; the upgrade
   replaces that proxy with causal-state structure.)

**Sequencing (owner decision):** S4 continues on macro-feature v1 (rollout/F/R9 are orthogonal to the belief
representation). Then either (A) run S5 on v1 with a valid EoC *metric* only, or (B) do S4.5 (causal-state
generative model) first, then S5 on the principled v2. Recommend B given the reuse intent; flagged for Joe.

## Faithfulness discipline (identical to Port 1)

`G_efe` is unit-pure (KL-risk + entropy-ambiguity). The existing hand-heuristic controllers
(`choose-actions-hex`, sigil) are **engineering baselines/augmentation**, never called EFE; each augmentation
term carries a typed residual. Tags: FEP-derived / principled-approx / analogical / non-FEP-engineering.

## The build — staged as Zai handoffs (parity with M-aif-ants-port S0–S5)

Gates every slice: **Clojure** — clj-kondo, `futon4/dev/check-parens.el`, named tests. **Lean** — `lake build
DarkTower` exit 0, **no `sorry`/`admit`/`axiom`**, Lean `Stage`s mirror the Clojure controller. No
self-certification (owner re-runs; [[feedback_zai_bell_handoff_economics]]). Bell `claude-3` back with
summary + shas; park on the job-id (deadline ≥ 45 min).

### Slice 0 — DERIVE: MetaCA R-map + DarkTower mapping (no product code) ‖ ants-S0
- **Goal:** two ledgers. (a) `futon5/holes/labs/M-aif-tokamak/r-map.md`: for R1–R14, the MetaCA terminal +
  cyber-MMCA seam (file:line) or "absent", mirroring the ant `r-map.md`
  (`futon2/holes/labs/M-aif-ants-port/r-map.md`, commit 46bb77a) as the AIF-feature reference. (b)
  `.../darktower-map.md`: the compliance mapping — the `Stage` enum for one tokamak tick, which feeds are
  `TypedHole`s (and their satiety grading), which EFE legs are `copar`, which R9 properties become theorems.
- **Read:** `futon5/scripts/cyber_mmca_compare.clj`, `futon5/src/futon5/mmca/{metrics,runtime,exotype}.clj`;
  `mathlib4/DarkTower/{WMPipelineExample,MetaCAExample,BV,TypedHole,Fill,Coverage}.lean`; `AIF-COMPLIANCE.md`.
- **Acceptance:** every AIF row cites a real seam or "absent"; the Stage enum is concrete (named stages, seq
  spine, copar EFE legs); each proposed theorem names the property it tracks. **Explicitly answer the
  feed-forward vs within-diagram-feedback question** (compliance §"complexity boundary") for the tokamak
  tick — if genuine feedback is needed, that is the open-diagram trigger note, not something to force.
- **Gate:** docs only; owner spot-checks ≥4 AIF rows + the Stage enum against source/exemplar.

### Slice 1 — R4 forward model + Lean Stage spine ‖ ants-S1
- **Clojure:** wrap `runtime/run-mmca` as the tokamak's `forward-predict` (macro-feature next-state
  distribution), reusing the ant port's `aif/forward` abstraction shape. No new CA dynamics — the CA step
  exists; this is the predict seam.
- **Lean:** `MetaCATokamakExample.lean` skeleton — the `Stage` enum + one-tick feed-forward `BV` expr
  (`read ◁ perceive ◁ evaluate ◁ select ◁ act ◁ trace`), building `lake build DarkTower` green.
- **Acceptance:** `forward-predict` mean == the CA step outcome on a fixed seed; Lean tick expr compiles,
  stages named to mirror the (stubbed) controller stages.

### Slice 2 — R19 C (EoC set-point) + R5 unit-pure g-efe + EFE copar legs ‖ ants-S2
- **Clojure:** define `C` = EoC confinement preference over macro-features; compute `g-efe` (KL-risk to C +
  entropy-ambiguity of predicted regime) via the **shared** core; `:aif` controller ranks the 5 actions by
  `-G/τ`.
- **Lean:** the EFE decomposition as `BV.copar` (⅋) legs — epistemic (ΔF) ⅋ pragmatic (ΔG) held
  simultaneous, never sequenced (compliance invariant 2).
- **Acceptance:** `g-efe` matches the shared core on a fixture to 1e-9; the `:aif` controller runs a full
  MMCA episode and steers regime toward `:eoc` on a seed where `:null` drifts to `:freeze`/`:magma`; Lean
  copar legs typecheck.

### Slice 3 — controller split + R7 precision + R14 τ + feed TypedHoles (starvation theorem) ‖ ants-S3
- **Clojure:** `controller-score = g-efe + Σ augmentation` (the hex/sigil heuristics demoted to named
  augmentation with residuals); per-channel precision over macro-features; τ commitment coupled to regime
  volatility.
- **Lean:** each observation feed is a satiety-graded `TypedHole`; prove `IsHungry` for a severed feed and
  `¬ IsHungry` when fed (compliance invariant 1 — the single most important property).
- **Acceptance:** winner-changing ablation proves each augmentation's role; severing a feed breaks the Lean
  build via the starvation theorem, repairing it flips the theorem.

### Slice 4 — R13 rollout + R8 F + R9 theorems (conservation, coverage, abstain-fires) ‖ ants-S4
- **Clojure:** `S(π)` rollout of control moves to horizon H>1 over the `run-mmca` forward model; per-tick
  `F` surfaced in trace.
- **Lean:** conservation (discard ↔ VFE normalization, `E-the-dark-tower-2` §4) stated + proved; `Coverage`
  proof (no orphan discharge outputs); abstain-fires as a provable condition.
- **Acceptance:** planted scenario where H=3 beats greedy at holding EoC; `lake build DarkTower` exit 0 with
  the R9 theorems present.

### Slice 5 — honest confinement experiment + Coverage close ‖ ants-S5
- **Goal:** pre-registered comparison of `:aif-full` vs `:aif-no-epistemic` vs `:hex`/`:sigil`/`:null`,
  scored on **time-at-EoC / confinement** over independently-seeded CA runs (randomized genotype + seed per
  run). Contrast: `aif-full − aif-no-epistemic > 0` on confinement; report honestly either way.
- **Lean:** `Coverage` proof that the apparatus's discharge projections are all views of existing facets.
- **Acceptance:** numbers reproducible from logged seeds by independent re-run; `lake build DarkTower`
  exit 0, no cheats, Lean stages ≙ controller stages.

## Open-diagram watch (compliance §"complexity boundary")
The AIF tokamak is the likely first construction to need genuine within-diagram feedback (a stage output
feeding an earlier open port, or a dynamic-count loop). **Default: model one tick feed-forward, recurrence
external** (like `WMPipelineExample`). If real feedback surfaces: **STOP, commit a note naming the stage and
why, do not force it or edit `Comb.lean`** — that report is the scoped trigger to build the open-diagram
(Roman coend) layer, a real result, not a failure.

## Review protocol (Claude owner)
Per returned slice: `git show`, re-run named Clojure tests + kondo + parens, `lake build DarkTower` locally,
confirm the Lean stages faithfully mirror the controller (not a plausible stand-in), spot-check claimed
numbers. Fix small findings directly; re-dispatch only substantial new work. Keep parity with the ant
port's slice — flag drift.
