# M-metaca-search — a two-frame search engine over CA dynamics

- **Status:** PLAN / DERIVE (drafted 2026-07-14, claude-1; direction approved by Joe
  the same day). Grows out of the "how do we *search* the space?" question Joe
  raised while the sci-repro reproductions were landing.
- **Context:** M-sci-reproduction reproduced the paper's CA dynamics and the
  Tier-2 replays added more; the verified dynamics (mutating-template, Baldwin,
  contextual-template) are now a **vocabulary of change-operators**. This mission
  is the turn from *reproducing landmarks* to *searching the space of dynamics*.
  It couples the lab (`notebooks/sci-repro/`) to the dark-tower theory
  (`futon5a/holes/excursions/E-the-dark-tower{,-2}.md`) and its Lean
  implementation (`~/code/mathlib4/DarkTower/`).

## 1. The question

Reproducing dynamics one by one is a *survey*, not a *search*. A search needs a
space with coordinates, a move operator, and a way to score where a move lands.
The claim of this mission: we already have all three, in two complementary
reference frames, and the job is to **coordinate the frames** (Joe, 2026-07-14:
"models-in-space + behaviour-in-space, not just models + behaviour").

## 2. Architecture — two coordinatized frames, not two pincers

The two searches are the two axes of the dark tower (E-the-dark-tower v1/v2):

- **Behaviour frame (what a dynamic *does*).** The CA lattice read as a
  **polynomial interface**: positions = cell configurations on the grid,
  directions = local updates. Running it is a *coalgebra* — the spacetime
  diagram. Coordinates = the **discriminator registry** (bitplane MI, diagonal
  autocorrelation, verifier blind-spot, …), each vs. explicit nulls
  (frozen / Rule-30). This is "behaviour-in-space."
- **Model frame (where a dynamic *is*).** The space of dynamics given its own
  polynomial interface: positions = models/fills, directions = blend-edits that
  move to a neighbouring dynamic. A **grid on model space**, mirroring the CA's
  grid on behaviour space. This is "models-in-space." Canonical representation =
  **DarkTower combs**, NOT the legacy `src/futon5/wiring/*` (that is the
  operational shadow; see also `M-fulab-wiring-survey.md` for existing MMCA
  wiring primitives).

**The connection between the frames** (the new content, 2026-07-14): the
differential-calculus analogue E-the-dark-tower-2 §9 vetted as the one structure
that *composes with Poly today* — Smithe's categorical active inference
(`arXiv:2208.12173`): `DiffSys_C(p)` = differential systems over a polynomial
interface, with the functor **`Flow: DiffSys → Coalg`**. That functor *is* the
change-of-reference-frame: it carries the differential/model frame into the
behaviour/coalgebra frame.

## 3. Why this closes into a self-steering search

Once both sides are poly-grids with a `Flow` between them, the two searches
become **one gradient loop**:

- IF behaviour-space "interestingness" (the discriminators vs. nulls) is read as
  a **free energy**,
- THEN its gradient, pulled back through the lens / parametric optic, is a
  **flow on the model grid** — it points at *which blend-edit most increases
  structured-chaos*,
- SO the blend operator steps along that flow. Behaviour disposes; the pulled-back
  gradient tells the model frame where to step next.
- BECAUSE Smithe's Laplace doctrine makes free-energy gradient descent functorial
  over polynomial interfaces, this **first-order rung is buildable with what
  composes today** (E-dt-2 §9, "YES — first-order").

This is the feedback the earlier "blend proposes, grid filters" framing lacked:
the grid-in-the-theory upgrades a *filter* into a *gradient*.

## 3.5 The flight = the loop (the War Machine, de-Seinfelded)

The self-steering loop is the War Machine's own flight, re-targeted.
`WMPipelineExample.lean`'s flight —
`wake ◁ judge ◁ psi ◁ cascade ◁ (gateF ⅋ gateG) ◁ gate ◁ select ◁ enact ◁ trace ◁ learn`
— is an active-inference loop: `cascade` rolls out the generative model,
**`gateF` = ΔF (epistemic/accuracy)**, **`gateG` = ΔG (pragmatic/value)**,
`select`/`enact` act, `learn` updates. E-dt-2 §9's Smithe result makes it precise:
the ΔF/ΔG structure *is* free-energy-over-Poly, functorially.

Today the WM runs this flight with ITSELF as the generative-model target
(developing futon by reasoning about futon developing — self-reference with no
external referent that can falsify it; the γ-starvation "coasted for hours"
pathology in `WMPipelineExample` is exactly this). Pointing the flight at the CA
gives it a **falsifiable external world**: the grid is `256ca.el`-exact, every
discriminator measured against a null. The reproduction rigor is what makes the
external target trustworthy enough to steer by. Mapping: generative model = a CA
blend; `gateF` = how well the model frame predicted the behaviour it got (Flow
faithfulness); `gateG` = advance toward novel structured-chaos; `enact` =
instantiate + run (pushforward); `learn` = pull the measured gradient back onto
the model grid. **The CA is the WM's first external world.**

## 3.6 One blend operation over both frames — generators, evaluators, Tokamak

(the unification, developed with Joe 2026-07-14)

Blend is a single operation — mix the fills of two combs — and it applies to
**both** frames, because both frames are combs:

- a **generator** is a comb `state → state` (`read ◁ combine ◁ mutate ◁ write`).
  Blending generators = mixing dynamics ("9 parts template : 1 part Baldwin").
- an **evaluator** is a comb `behaviour → score`
  (`read-spacetime ◁ estimate ◁ score`). Blending evaluators = mixing
  discriminators ("3 AIS : 2 TE").

The generator/evaluator distinction is therefore just **wiring** — which ports
are input, output, or feedback — and blend is uniform across it. Consequences:

- To blend evaluators *in the formalism* (not merely as a weighted sum in
  Clojure), represent AIS/TE/… as **evaluator-combs**. They are feed-forward
  (`behaviour → score`), so they port onto the current `Fill`/`BV` machinery
  exactly as the 9 generator occupants did — a near-term slice.
- Blend weights apply to **normalized** discriminators (each mapped frozen→0,
  complex-class→1 against its ECA reference) so the ratio is meaningful across
  their different scales.
- **The eye calibrates the evaluator-blend.** Which AIS:TE ratio is right is not
  a free parameter — it is the one that reproduces the human visual ranking. You
  rank N sweep-diagrams; we fit the weight that matches. That is the human as
  **xenotype**, supervising the evaluator rather than scoring every candidate.
  The search is then over a *product* space — (generator blend-weight) ×
  (evaluator blend-weight) — metrics driving the generator axis, the eye
  anchoring the evaluator axis.

**The Tokamak is where the two frames meet.** An AIF apparatus (see
`~/code/mathlib4/DarkTower/AIF-COMPLIANCE.md`) is the *general* comb —
`perceive ◁ evaluate ◁ act ◁ learn` — with **both** an evaluate-leg
(`gateF`/`gateG`) and an act-leg (`enact`). A pure generator drops the
evaluate-leg; a pure evaluator drops the act-leg. So the Tokamak is the
**self-evaluating generator**: its free energy over what it observes *is* the
EoC score of what it generates. AIS/TE are hand-crafted evaluator-combs (fixed
wiring, eye-calibrated); the Tokamak is the *learned* evaluator-comb (its
generative model adapts — it discovers the evaluator the eye was calibrating).
Feed-forward evaluator-combs fit current machinery; the recurrent Tokamak is the
frontier construction that forces the deferred open-diagram layer.

## 4. Step sequence

- **0 — read (DONE, 2026-07-14).** DarkTower `Comb`/`TypedHole`/`Fill`/`ScopeQuery`/
  `BV`/`WMPipelineExample` read. Findings: the semantic frame is built AND live
  (DarkTower is already the War Machine wiring-contract layer — a severed feed is
  a provable `IsHungry`). `ScopeQuery.answers_eq_fills` = the fill-enumeration
  search operator, theorem-backed.
- **1 — functor probe (DONE, codex-4 `b09fe548e5`, reviewed+accepted 2026-07-14).**
  `DarkTower/MetaCAExample.lean` realizes the mutating-template update as a BV expr
  (`(reads via copar) ◁ combine ◁ mutate ◁ write`), with `combine` and `mutate` as
  `TypedHole`s whose dependent fills match `256ca.el:971-986,990-1065` (incl. the
  RNG-faithful full-shuffle bit selection verified in R-repro-5). Reviewed: `lake
  build DarkTower` exit 0, no `sorry`/`axiom`, commit touches only the new file,
  fidelity cross-read clean. **VERDICT: current machinery suffices** — the update
  is a feed-forward two-slot diagram (`BV.seq`/`BV.copar` spine + `Fill.fill` for
  the two slots); the deferred Roman-coend open-diagram layer is NOT needed until a
  construction exposes independently-addressable middle holes or genuine feedback.
  Bonus: codex-4 seeded the *alternative* fills (`neighborAgreementElseFallback`,
  `unconditional`/`always`) — the blend surface for step 2 is already half-present.
  (Minor: `copar` vs `par` (⊗) for the independent reads is a BV-semantics point to
  pin when formalizing the flight; consistent with `WMPipelineExample`, not a defect.)
- **2 — flow a blend, as a re-targeted WM flight (HELD — do not dispatch yet).**
  Structure "flow a blend" as a WM flight over the CA world (§3.5): generative
  model = the blend (Baldwin × mutating-template), `gateF`/`gateG` = the two
  frames, `enact` = run it, `learn` = pull back the gradient — the first-order
  Smithe-buildable rung. **Gated (Joe, 2026-07-14) on: (1) more ported examples in
  the store; (2) the War Machine cooling down — Codex is finalizing it for
  end-to-end runs.** Do not bell this out until both clear.
- **3 — the curvature rung (RESEARCH TARGET).** See §5.

## 4.5 Meanwhile track (unblocked, while step 2 is held)

Two threads that build step 2's prerequisites without touching the held flight:

- **Port existing demos onto the unified basis.** Each reproduced dynamic becomes
  an occupant of the shared skeleton's typed holes (as codex-4 did for
  mutating-template in `MetaCAExample.lean`). Next: **Baldwin** — generalize the
  mutate `TypedHole` so both `balance-mutation` AND Baldwin's context-gated
  `mutate-rule-n(count+2)` are occupants; the combine hole already covers Baldwin
  via the seeded `neighborAgreementElseFallback` fill. Each port grows the fill
  store the blend will draw on — Joe's gating condition (1).
- **Add more back-catalogue demos.** Continue the Tier-2 replays / reproductions
  (see `M-sci-reproduction-replay-ledger.md`) to widen the vocabulary of dynamics.

## 5. Honest gaps, guardrails, and the prize

- **Open-diagram layer.** `Comb.lean` defers the n-hole open-diagram (Roman coend)
  comb `<A;–;B;–;C>`. Two-hole skeletons may compose as nested `Fill` (assoc
  proven); genuine mid-diagram feedback needs the deferred layer. Per Joe:
  **Codex upgrades `Comb.lean` when the verdict says we need it** — gate that as
  its own slice, do not let the probe edit `Comb.lean`.
- **The curvature ↔ Poly gap = the prize (E-dt-2 §9).** First-order (gradient
  flow) composes with Poly (Smithe). The **curvature rung** — iiching / 2-comb /
  derivative-of-derivative, i.e. how the model-space gradient itself bends — is
  *rigorous but isolated*: tangent categories (Cockett–Cruttwell) on one side,
  Poly on the other, **no published bridge**. Leverage: futon5's MMCA **already
  runs the exotype** (the 2nd-order operator), in a system small enough to
  instrument. So the CA lab is the place to take a first *empirical* Poly-side
  curvature — a discrete finite-difference curvature over the two grids. And
  "discrete finite-difference" is exactly v1's methodological turn (model the
  discrete operator tower directly, not continuous calculus on embeddings — "a
  teaspoon to plow a field"). Internal consistency is a good sign.
- **Vocabulary discipline.** The reproduced dynamics are the search vocabulary;
  port per-dynamic into DarkTower/scirepro, never wholesale. Ground-truth remains
  `256ca.el` + the verified scirepro ports.
- **Grid = discrete.** The differential here is discrete finite-difference over
  the lattices, not continuous calculus.

## 6. The artifact (goal)

For Aleks Kissinger ("you built me a theory — Caus[−]/BV combs, `arXiv:2205.11219`
— and futon eated it"): a blended CA dynamic produced by comb-fill of two
reproduced dynamics-as-combs, its well-formedness **discharged by DarkTower Lean
lemmas**, its novelty **measured by the behaviour discriminators**, and — the
research-grade version — **flowed** by a free-energy gradient over the two grids,
with a first shot at the curvature rung the literature leaves open.

## Cross-references

- `M-sci-reproduction.md`, `M-sci-reproduction-replay-ledger.md`,
  `M-lab-standard.md` — the reproduction lab that produced the vocabulary.
- `futon5a/holes/excursions/E-the-dark-tower.md` / `E-the-dark-tower-2.md` — the
  tower theory and the differential-strand reframe (§9).
- `~/code/mathlib4/DarkTower/` — `Comb`, `TypedHole`, `Fill`, `ScopeQuery`, `BV`,
  `WMPipelineExample` (the Lean implementation and the live WM contract example).
- `M-fulab-wiring-survey.md`, `src/futon5/wiring/*` — existing wiring primitives
  (operational shadow of the comb representation).
- Memory: `project_darktower_ca_blend_bridge.md`; related mission
  `project_typed_holes_mission.md` (the DarkTower typed-hole/fill theory).
