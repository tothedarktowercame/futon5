# M-categorical-code

**Status:** IDENTIFY opened 2026-06-02
**Xenotype:** derivation (IDENTIFY → MAP → DERIVE → ARGUE → VERIFY → INSTANTIATE)
**Created:** 2026-06-02 (paired with Joe, emacs-repl)
**Owner:** claude-6. Sibling / generalization of `M-differentiable-code` (E2); same owner.
**Repo:** futon5
**Campaign:** **late joiner to `C-substrate-completion`** (`futon3c/holes/campaigns/C-substrate-completion.md`)
as a consumer. Per Joe (2026-06-02): write this IDENTIFY first; the ESCROW entry (E3) is authored
*after* the mission doc exists.

## Thesis

**Port futon5's evolutionary machinery to *code evolution*, expressed as a functor.** futon5 evolved
Cellular Automata, topped out at *wiring diagrams* for the same, and — the crucial precedent —
already *transferred* those diagrams to another domain (AIF ants). That transfer is not informal: it
is implemented as **`metaca->cyber-ant-functor`** (`futon5/src/futon5/ct/dsl.clj:130`, source
`:futon5/metaca` → target `:futon5/cyber-ant`, carrying an `object-map` + `morphism-map`). The same
construction gives **F : Evolution → Code**. Category theory specifies the **morphisms**
(transformations) and the **functor** (the cross-domain port); Malli specifies the **objects** (data
shapes). `M-differentiable-code`'s `jax_refine` loop is a *microcosm* — one morphism (the Lamarckian
`refine` arrow) of the full loop.

## Why this exists (the convergence)

- **The transfer is a functor.** futon5 already proved its evolution machinery is domain-portable
  (CA → ants) by writing the port as a functor. Porting to code is the *same move* — `metaca → code`
  — and CT is exactly the language of structure-preserving transfer.
- **JAX is a microcosm.** `jax_refine` gradient-refines a controller (TPG weights) against
  band-satisfaction and injects the result back into the population (the Lamarckian step). That is one
  arrow of the evolution loop; M-differentiable-code is "futon5's pattern, one morphism, on code."
- **futon5 has a rich, *already-developed* concept vocabulary** worth refactoring into code-evolution
  abstractions (see "Concepts to port").

## 1. IDENTIFY

### Invariance claim

The object of specification is neither "some data types" (Malli) nor "some diagrams" — it is the
**domain-invariant evolution structure** futon5 proved portable. Stated categorically: an **Evolution
category E** whose objects are the stages of an evolutionary loop and whose morphisms are its
operators, plus a **functor F : E → Code** transporting the loop onto codebase structure, preserving
composition. *If the port is a genuine functor, the code-evolution loop **is** the CA loop
transported — structure-preserving by construction* (the guarantee that made the ants transfer work).

### The Evolution category E (= `metaca-category` generalized)

| | |
|---|---|
| **Objects** | Substrate · Genotype · Phenotype · Operator · Population · Diagnostics · Bands |
| **Morphisms** | `express` (Genotype→Phenotype) · `measure` (Phenotype→Diagnostics) · `score` (Diagnostics→Bands) · `vary` (Population→Population) · `select` (Population→Population, Pareto) · `refine`/Lamarck (Diagnostics→Genotype) |

The evolution **loop** is a composite endomorphism on Population; `refine` is the Lamarckian feedback
edge (futon5 `jax_refine` + Baldwin Lift, `mmca/operators.clj`).

### The functor F : E → Code (a `metaca → code` functor, by analogy to the ants one)

| E object / morphism | F maps to (code evolution) |
|---|---|
| Genotype | codebase structure (the substrate-2 scope/dependency graph) |
| Phenotype | runtime / test behaviour |
| Operator (kernel) | edit-operator (refactor / rewire) |
| Diagnostics (entropy, coupling, damage-spread) | code-health metrics (structural entropy, module coupling, churn-sensitivity) |
| Bands | wiring-contract / spec bands |
| `refine` / Lamarck | gradient edit-proposal → **apply** (the Rung-3 step) |
| ratchet-gate | "is the codebase still improving?" plateau detector |

Functoriality (composition-preservation) is the load-bearing claim: it must be *checked*, not
asserted (see gap 1).

### CT spec vs Malli spec (the layering — settled)

- **Malli types the *objects*** — the *shape of a value* (`[:map [:genotype [:vector :sigil]]]`), data
  at rest. Composes by nesting. Cannot express a transformation or a cross-domain map.
- **CT types the *morphisms*** (transformations) + composition laws + **functors** (structure-preserving
  maps to other domains) — data *in motion*, and its *portability*. The CA→ants transfer can *only* be
  a functor; Malli cannot state it.
- **Complementary, not competing:** Malli = the nouns (genotype, diagnostic-vector, codebase-graph);
  CT = the verbs (`express`, `score`, `select`, `refine`) **and** the functor F that ports the loop.
  A spec of an *evolving, transferable* system wants both layers.

### Concepts to port (the refactoring payload — from the futon5 concept-learning, 2026-06-02)

1. **Lamarckian / Baldwin feedback** (`jax_refine`, `mmca/operators.clj`) — acquired→heritable via an
   observer window. This *is* the Rung-3 apply-and-feed-back loop M-differentiable-code lacked.
2. **Band-satisfaction** — "attract to a zone, not a peak" (already borrowed; futon5 is its origin).
3. **Ratchet curriculum gating** (`exotic/ratchet.clj`) — block when windowed Δ < threshold =
   **plateau detection**: the "are we still learning?" signal we were missing.
4. **Edge-of-chaos diagnostics** (damage-spread Lyapunov, Wolfram-class) — sensitivity ↔ computational
   capacity; candidate **structural-health** metric (not frozen, not chaotic).
5. **Coupling spectrum** — "coupling is where computation happens."
6. **Pareto multi-objective** — preserve the trade-off front; no scalar fitness.

### Gaps & doubts (named on purpose — these are the work)

1. **Functoriality must be checkable, not relabelled boxes.** A table mapping objects is not a
   functor unless it preserves composition. The test: does `F(select ∘ vary)` = `F(select) ∘ F(vary)`
   on code? Same discipline as the baseline ablations — assert nothing we cannot check.
2. **The code-side Diagnostics are the novel, uncertain piece.** structural entropy / coupling /
   churn-sensitivity (≈ damage-spread) must be *defined* and shown to carry signal (not reduce to
   classical graph metrics — the lesson from the symbol smoke test + ablation).
3. **`refine`/Lamarck = the Rung-3 apply step** — consent-gated, and needs a real "did it improve?"
   signal (the gap M-differentiable-code surfaced; *inherited, not solved* here).
4. **`jax_refine` caveat (verified by reading the code):** its loss as-implemented is constant w.r.t.
   the optimized weights (diagnostics are fixed trace data; only the entropy bonus has gradient). The
   *loop structure* is the blueprint, **not** that loss — the recurring lesson: the loss must depend
   on what you optimize.
5. **CT enrichment is open.** `ct/dsl` today is a documentation-DSL. Turning it into a checkable
   framework (monoidal composition for wirings — `wiring/compose.clj` already has serial/parallel —,
   functor laws) is unbuilt; futon6's ncatlab / arxiv-mining could supply the richer categorical
   vocabulary.

### How it consumes the Campaign metric (escrow E3 preview — to formalise after IDENTIFY)

F's **Diagnostics** object (code-health metrics) and **Genotype** object (the structure being evolved)
are defined *on* substrate-2's metric + the code graph `C-substrate-completion` delivers. So
M-categorical-code is a **consumer**: its code-side diagnostics (coupling, structural entropy) read the
delivered metric, and its Genotype is the metric's node-identity. **E3 (TBD):** the requirement that
the metric expose the structural quantities the code-diagnostics need — to be authored next, per Joe.

### Relations

- **M-differentiable-code (E2)** — this *generalizes* it; `jax_refine` is the microcosm; the
  differentiable `refine` is one morphism `F(refine)`. Carries E2's honest discipline: baseline-ablate;
  loss-must-depend-on-the-optimized-variable; code grows by *node-arrival* (so forecasting is
  cold-start, not densification).
- **futon5 `metaca->cyber-ant-functor`** (`ct/dsl.clj:130`) — the precedent: domain transfer as functor.
- **`C-substrate-completion`** — late-joiner consumer (escrow E3 TBD).
- **futon6 ncatlab / arxiv-mining** — the CT-enrichment source.

### IDENTIFY exit criterion

✅ Invariance claim named (evolution-as-category + port-as-functor); ✅ convergence grounded (the
existing ants functor + the jax_refine microcosm); ✅ objects / morphisms / functor sketched; ✅
CT-vs-Malli layering settled. → **MAP next:** flesh F's morphism-map (what `select`/`vary`/`refine`
concretely become for code), define the code-side **Diagnostics** (the most novel piece), and the
Malli object-schemas — and author the Campaign ESCROW (E3).

## 2. MAP (opened 2026-06-02)

*MAP discipline (`futon4/holes/mission-lifecycle.md`): survey what exists; facts, not decisions.*

### Survey questions

**Q1. Is futon5's functor machinery reusable to express `F : Evolution → Code`?**
**YES, directly.** `ct/dsl.clj` has `build-functor` (:17) + `register-functor!` (:73), and **four worked
categories + three functors** — incl. `metaca→cyber-ant-functor` (:130, `:object-map` + `:morphism-map`)
and a `design-pattern-category` (:121, the IF/HOWEVER/THEN/BECAUSE shape as objects+morphisms). A
category = `{:name :objects #{…} :morphisms {name {:source :target}}}`; a functor = `{:name :source
:target :object-map :morphism-map}`. The port = add a `code-evolution-category` + a `metaca→code`
functor mirroring the ants one (`:genotype→code-structure, :metrics→code-diagnostics, :evolve→apply-edit,
:gate→ratchet-gate, …`). **Known-shape construction, not new infra.**

**Q2. What futon5 evolution machinery is ready-to-reuse vs needs code-specific porting?**
Ready (concepts+code): band-satisfaction (`tpg/verifiers`), Pareto select (`tpg/evolve`), ratchet-gating
(`exotic/ratchet`), the 6-dim diagnostics framework (`tpg/diagnostics`), the `jax_refine` Lamarckian loop,
monoidal wiring-compose (`wiring/compose.clj`, serial/parallel). Code-specific (must build): the code-side
**Diagnostics**, the **edit-operators** `F(operator)`, the **Genotype = code-graph** encoding.

**Q3. Does the delivered metric (O1–O4) cover E3's requirements?**
**Partially.** O1 node-identity = the Genotype ✓; O2 curvature ≈ a coupling diagnostic ✓-derivable +
continuity ✓; O3/O4 relevant to `refine`. **But there is no dynamics / damage-spread object in O1–O4**
(all static) → requirement **(c)** is NOT covered.

**Q4. Where does the structural-dynamics / damage-spread diagnostic (c) come from — a new O5, or the
temporal substrate?** **Temporal substrate (option ii) — decisive MAP finding.** The dynamics data
*already exists*: git (808 commits/repo; edge+node evolution, datable — confirmed in the #3 work),
XTDB bitemporal, the evidence landscape. "damage-spread for code" = how a structural change propagates
across commits = computable from git history, **not** from the static metric. **So no new O5 is needed;
(c) self-sources from git/XTDB.** ⇒ **E3's metric-dependency reduces to (a)+(b), which are already
`:contract-released`** — the dynamics is a self-sourced input, not a metric obligation. *(Updates the
E3 escrow draft.)*

**Q5. Is the functor genuinely functorial (composition-preserving) or relabelled boxes?**
OPEN — a DERIVE/VERIFY question, not MAP. Note: futon5's *own* existing functors specify
object-map+morphism-map **without** explicit law-checking in `ct/dsl`; but `exotic/category.clj` +
`exotic/functor.clj` provide runtime category-law validation — so the gap-1 functoriality check is
**buildable by reusing existing infra** (and would improve on futon5's own practice).

**Q6. Malli object-schema status?** **MISSING.** No Malli schemas exist for the code-Genotype (graph)
or the Diagnostics; the graph shape exists only informally (`relations.json`, the O1 node schema). To
author in DERIVE.

### Inventory

| Surface | Ready facts |
|---|---|
| `futon5/ct/dsl.clj` | functor machinery + 4 categories + 3 functors (the F template) |
| `futon5` evolution code | `tpg/{verifiers,evolve,diagnostics,core}`, `mmca/{operators,local-physics,exotype}`, `exotic/{ratchet,category,functor}`, `xenotype/*`, `jax_refine`, `wiring/compose` |
| Campaign metric | O1–O4 delivered+verified; `futon5/data/code-embeddings/code-emb.npy` (7534 nodes); `relations.json` (feeds-A graph) |
| Temporal substrate | git (808 commits/repo), XTDB bitemporal, evidence landscape, wm-trace — **the (c) dynamics source** |
| futon6 | ncatlab / arxiv-mining — CT-enrichment source |

### Ready vs missing

| Ready (no new code) | Missing (the actual work) |
|---|---|
| ct/dsl functor machinery + `metaca→ants` template + `design-pattern-category` | the `code-evolution-category` + `metaca→code` functor (the F instance) |
| futon5 evolution concepts (band-sat, Pareto, ratchet, diagnostics-framework, Lamarckian loop) | **the code-side Diagnostics** (structural-entropy / coupling / damage-spread) — the novel piece |
| O1 node-identity (=Genotype), O2 curvature (≈coupling), code-emb + relations.json | the edit-operators `F(operator)` + the `refine`/apply morphism (Rung-3, consent-gated) |
| git/XTDB temporal substrate (the (c) dynamics data) | the damage-spread / change-propagation diagnostic computed over git history |
| `exotic/category` + `functor` law-validation infra | a functoriality **check** for F (composition-preservation) — gap 1 |
| — | Malli object-schemas (Genotype graph, Diagnostics) |

### Surprises (recorded before DERIVE locks in)

1. **(c) self-sources from git/XTDB — no O5 needed.** The static metric needn't be extended; E3's
   metric-dependency reduces to (a)+(b) (already released). → **updates ESCROW E3** (resolves (c) to
   option ii). This is the escrow revision the survey was meant to surface.
2. **futon5's categorical machinery is richer + more directly reusable than expected** — a
   `design-pattern-category` and `observation→kernel` + `metaca→ants` functors already exist, so the
   port is a known-shape construction.
3. **Functoriality is asserted-not-checked even in futon5's own functors** — but law-validation infra
   exists to reuse, so gap-1 is buildable (and an improvement on the precedent).

**MAP exit:** survey questions Q1–Q6 answered with concrete findings; ready-vs-missing complete;
the (c) surprise recorded and routed to the E3 escrow. → DERIVE (flesh F's morphism-map + the code-side
Diagnostics + Malli schemas + the functoriality check).

## 3. DERIVE (2026-06-02)

### The Code-evolution category `Code` (mirrors `metaca-category`'s form)

```
:objects   #{:codebase :code-structure :behavior :edit-operator
             :portfolio :code-diagnostics :contract-bands}
:morphisms {:build    {:source :code-structure :target :behavior}        ; run/compile/test
            :diagnose {:source :behavior        :target :code-diagnostics}
            :score    {:source :code-diagnostics:target :contract-bands}
            :propose  {:source :portfolio       :target :portfolio}       ; generate edit-candidates
            :select   {:source :portfolio       :target :portfolio}       ; Pareto on contract-satisfaction
            :apply    {:source :code-diagnostics:target :code-structure}} ; Lamarckian refine→edit (CONSENT-GATED)
```

### The functor `F : metaca → Code` (object-map + morphism-map, like `metaca→cyber-ant`)

| metaca object | F (code) | metaca morphism | F (code) |
|---|---|---|---|
| `:world` | `:codebase` | `:observe` (world→metrics) | `:diagnose` (∘`:build`) |
| `:genotype` | `:code-structure` (the substrate-2 graph) | `:score` (metrics→operator-set) | `:score`→`:select` |
| `:phenotype` | `:behavior` (runtime/tests) | `:lift` (sigil→operator-set) | `:propose` |
| `:sigil` | code-atom (a `:symbol`/`:scope` node) | `:evolve` (world→world) | `:apply` (the edit endomorphism) |
| `:kernel` / `:operator-set` | `:edit-operator` | `:gate` (world→world) | ratchet-gate (guard on `:apply`) |
| `:metrics` | `:code-diagnostics` | | |

The Code category *elaborates* metaca's loop more finely (functors map *into*, not onto); F is faithful
on metaca's objects/morphisms. Built + registered via `ct/dsl` `build-functor`/`register-functor!`.

### The code-side Diagnostics (the novel piece — futon5's 6-dim vector, ported)

| futon5 diagnostic | code analogue | source |
|---|---|---|
| entropy (genotype Shannon) | **structural entropy** (dependency-degree / module-size distribution) | graph (static) |
| change (Hamming rate) | **churn rate** (edges/nodes added-removed per commit window) | **git temporal (E3-c)** |
| autocorr | **structural stability** (graph autocorrelation across commits) | git temporal |
| diversity | **concept/namespace diversity** | graph + embedding |
| phenotype-coupling | **module coupling** = Ollivier–Ricci curvature (O2) / shared-symbol | **metric O2** (static) |
| damage-spread (Lyapunov) | **change-propagation sensitivity**: when ns X changes, do its dependents change next commit? | **git temporal (E3-c)** — the Lyapunov analogue |

**Band-targets = "healthy code at the edge of chaos"**: not *frozen* (zero churn/coupling = rigid/dead),
not *chaotic* (high churn/damage-spread = unstable). Authored from the wiring-contract (bands specified,
never fit — gap #3 discipline).

### Invariant rules (checkable)

1. **Functoriality** — `F(g∘f) = F(g)∘F(f)`. The load-bearing invariant: the code loop must be the
   evolution loop *transported*, not relabelled boxes. Checked by reusing `exotic/category.clj` +
   `exotic/functor.clj` law-validation.
2. **Diagnostic-band conformance** — code-diagnostics sit in the authored edge-of-chaos bands.
3. **Lamarckian-closure gate** — `:apply` is **consent-gated** AND accepts an edit only if it yields a
   *measured* diagnostic improvement (Δ band-satisfaction > 0). (Rung-3 discipline + the loss-fix.)

### Data flow

- `:code-structure` ← substrate-2 O1 graph + the O4(c) embedding cache.
- `:code-diagnostics` ← **static** {coupling = O2 curvature; structural-entropy = graph} ⊕ **dynamic**
  {churn, stability, damage-spread = git/XTDB temporal, self-sourced per E3-(c)}.
- `:contract-bands` ← wiring-claims / spec.
- `:apply` → edit-proposal → **consent-gate** → codebase mutation → next `:diagnose` (loop closure).

### IF / HOWEVER / THEN / BECAUSE

1. **IF** code-diagnostics were embedding/text-cosine, **HOWEVER** text≈names collapses to baselines
   (the symbol smoke-test + ablation), **THEN** diagnostics are **structural** (curvature/churn/
   damage-spread), not text, **BECAUSE** structure empirically beat names.
2. **IF** damage-spread needs dynamics, **HOWEVER** the static metric has none, **THEN** self-source from
   git/XTDB (E3-c, no O5), **BECAUSE** the dynamics already exist there.
3. **IF** F maps objects, **HOWEVER** object-relabelling isn't a functor, **THEN** *check* composition-
   preservation (invariant 1), **BECAUSE** functoriality is the entire claim.
4. **IF** the Lamarckian loop applies edits, **HOWEVER** unvalidated edits are the "plunger" we refused,
   **THEN** gate `:apply` on measured diagnostic-improvement + consent, **BECAUSE** the ablation/consent
   discipline.
5. **IF** `jax_refine` is the template, **HOWEVER** its loss is constant w.r.t. the optimised weights,
   **THEN** the code refine-loss must depend on `:code-structure` (the optimised variable), **BECAUSE**
   the loss-must-depend-on-what-you-optimise lesson recurs.

### Fidelity contract (GF) — Capability-Preservation Matrix (this is a *port* mission)

| futon5 capability | preserve / adapt / drop | tripwire |
|---|---|---|
| `ct/dsl` functor machinery | **PRESERVE** | F registers + validates via `register-functor!` |
| band-satisfaction (`tpg/verifiers`) | **PRESERVE** | band-score reused as-is |
| Pareto select (`tpg/evolve`) | **PRESERVE** | multi-objective on contract-bands |
| `exotic/category`+`functor` law-validation | **PRESERVE** | reused for invariant 1 |
| 6-dim diagnostics framework | **ADAPT** | the code-Diagnostics table above |
| ratchet curriculum gating | **ADAPT** | plateau detection on code-diagnostic Δ |
| Lamarckian / `jax_refine` loop | **ADAPT** | edit-proposal→apply + loss-fix + consent gate |
| MMCA cellular-automaton substrate | **DROP** | Genotype = dependency graph, not a sigil string |
| sigil / hexagram encoding | **DROP** | code-atom = `:symbol`/`:scope` node |

### Wiring diagram

The categorical diagram **Evolution `─F→` Code** (objects→objects, morphisms→morphisms above) *is*
this mission's wiring diagram — fitting, since the mission's content is the functor itself. It can be
rendered as a futon5 exotype `.edn` later; for DERIVE the table above settles the ports.

### Malli object-schemas (the Malli layer — to author at INSTANTIATE)

Malli types each `Code` object's data shape: `:code-structure` = the graph schema (`relations.json`
shape + O1 node schema); `:code-diagnostics` = `[:map [:structural-entropy :double] [:churn :double]
[:coupling :double] [:damage-spread :double] …]`; `:contract-bands` = `[:map-of :keyword [:tuple
:double :double]]`. CT types the morphisms + F; Malli types these objects (the layering from IDENTIFY).

### DERIVE exit criterion

Someone could implement from here: the `Code` category, F's object/morphism maps, the code-Diagnostics
(with sources), the three invariants, the data flow, the GF preservation matrix, and the Malli object
shapes are all specified. **Open risks routed to VERIFY (spike):** (1) is F genuinely functorial
(run the law-validation)? (2) do the *structural* code-diagnostics beat classical baselines at
forecasting real evolution on the #4 harness (the empirical bar, not just a design)? → VERIFY.

## 4. ARGUE (2026-06-02)

### Pattern cross-reference (`futon3/library/`, 989 flexiargs surveyed)

**The two linchpins — the design is established doctrine composed, not a novel leap:**

- **`pattern-discipline/patterns-as-categorical-objects`** — *patterns are objects in a category whose
  morphisms are the canonical components (context/if/however/then/because); admission = the new object's
  morphisms **compose** with the category's laws.* **Grounds the whole CT thesis + two design elements:**
  its admission-by-composition predicate **IS invariant 1** (functoriality = compose-cleanly); its
  *Level-1 text-shape vs Level-3 morphism-predicate* distinction **IS the Malli(objects)/CT(morphisms)
  layering.** Its context explicitly cites `ct/dsl.clj`'s Functor/Category records + `design-pattern-category`
  + `pattern_to_wiring` — the substrate M-categorical-code reuses. **Revision:** state invariant 1 as the
  pattern's admission predicate (F admitted iff its morphisms compose), and *generate a wiring diagram on
  admission* (the pattern's step).
- **`futon-theory/xenotype-portability`** — *the xenotype makes Baldwin cycles portable: one
  generate-select-compress skeleton, many interfaces.* **Grounds F itself:** its **transplantation-protocol
  IS F's construction** (instantiate Generator with the code interface's exotype, bind Selector to code
  contract-bands, connect Compressor to the code Genotype); its Compressor ("gradient/symbolic") **is the
  Lamarckian `refine`.** **Revision:** frame F explicitly as transplanting the `VariationalSearch` xenotype
  to the code interface.

**Supporting cluster (grouped by design element):**

| Pattern | Grounds | How / revision |
|---|---|---|
| `futon-theory/baldwin-cycle` | the Lamarckian loop | explore→assimilate→**canalize**; structure `:apply` in these 3 phases (assimilate = commit+pattern-record; canalize = guard the improved region) |
| `meta/baldwin-ratchet-defeats-darkroom` | the diagnostic gate | the cheap response to friction is to *suppress diagnostics*; the ratchet forbids it → **make the code-health gate non-negotiable** (don't negotiate the metric away to ship an edit) |
| `futon-theory/local-gain-persistence` | the apply outcome | every accepted edit **persists (commit+record) or is deleted (with reason)** — no ghost improvements |
| `math-informal/transport-across-isomorphism` | the functor's transfer | prove a property once, transport across the structure-preserving map — the math discipline behind F (name what's invariant vs substrate-specific) |
| `futon-theory/structural-tension-as-observation` | the code-diagnostics | the diagnostics ARE structural tension *observed* — coherent with O3's "tension = curvature" |
| `system-coherence/verified-rewrite-from-diagnostic-annotation` | the `:apply` morphism | type it: `(diagnostic, rewrite-rule) → (code, invariant-delta, verification-status)`; verified iff no invariant weakens |
| `system-coherence/single-seed-results-need-multi-seed-validation` + `devmap-coherence/baseline-freeze` | invariant 3 (the gate) | **strengthen:** the "measured improvement" gate needs a baseline-freeze snapshot + multi-run validation (one run is exploration, ≥3 is evidence) |
| `invariant-coherence/shape-first-identify` + `protocol-family-naming` | invariant 1 + the diagnostic family | functoriality is a *shape* with instances (enumerate the composition pairs you test); name diagnostics `structural-entropy/clojure`, `coupling/python`, … |
| `peripherals/inhabitation-feeds-evolution` | loop adoption | the apply-loop must be *inhabited* (easier than bypassing it) or it generates no data and can't evolve — adoption is a first-order design concern |
| `ants/baseline-cyber-ant` | the precedent | the CA→ants transfer the mission generalizes — the existing proof that the xenotype ports |

### Theoretical coherence

The design *serves* IDENTIFY's anchoring (evolution-as-category + port-as-functor) and is **validated by
the library's own doctrine**: `patterns-as-categorical-objects` already says structures-as-categorical-
objects with composition-admission, and `xenotype-portability` already says the Baldwin engine is
transplantable. M-categorical-code is their composition on code structure. The CT/Malli layering is the
library's Level-3/Level-1 distinction. **The design feels *inevitable*, not merely possible** — it assembles
two established patterns rather than inventing machinery.

### Trade-offs (what we give up)

- **Drop** the CA/sigil substrate (Genotype = dependency graph, not sigils) — loses futon5's rich
  local-physics, gains direct applicability to code.
- **Defer** `:apply` behind a consent + multi-run-improvement gate — slower, but the only honest path
  (the "plunger" we refused).
- **Library gap (honest finding):** the library is strong on verification/invariants/feedback/Baldwin but
  **light on the transformation/optimization *math*** (concrete gradient/Lamarckian update rules,
  capability-preservation-during-porting, multi-dim/Pareto diagnostics). That gap is exactly where the
  **futon6 ncatlab/arxiv-mining** CT-enrichment lands — named, not hand-waved.

### Generalization (`plos-npt/transferability-not-generalisability`)

Claim **transferability, not universality**: F transfers cleanly to *structural* code where the Genotype
is a graph and health is edge-of-chaos (Clojure stack confirmed); it is weakest where growth is pure
node-arrival (forecasting is cold-start, not densification — the #3 finding) and where the loss would
reduce to names (the smoke-test floor). Name the matching conditions; don't claim all code evolution.

### Plain-language argument

futon5 already learned to *evolve and improve* cellular-automata designs, and to carry that skill to a
different problem (ant colonies) without rebuilding it. This mission carries the same skill to **code
itself**: treat the codebase's structure as the thing being improved, measure its health the way futon5
measured its automata, propose changes — and apply one only if it *measurably* improves health and a human
approves. The library already holds the two ideas this rests on — that reusable designs *compose like a
language*, and that an *improvement engine can be transplanted between domains* — so we're assembling
established pieces, not inventing from scratch.

### PSR — Pattern Selection Records (ARGUE catch-up)

- **Pattern chosen:** `pattern-discipline/patterns-as-categorical-objects`
  - Candidates: patterns-as-categorical-objects, invariant-coherence/shape-first-identify
  - Rationale: it is the library's own categorical doctrine and directly supplies invariant 1
    (admission-by-composition) + the Malli/CT layering. Confidence: high.
- **Pattern chosen:** `futon-theory/xenotype-portability`
  - Candidates: xenotype-portability, baldwin-cycle, transport-across-isomorphism
  - Rationale: its transplantation-protocol is literally F's construction; the Compressor is the refine
    morphism. Confidence: high.

### ARGUE exit criterion

✅ Pattern cross-reference grounded in established library patterns (two linchpins + a supporting cluster),
with concrete DERIVE revisions; ✅ theoretical coherence shown (the design is the composition of two
existing patterns → *inevitable*); ✅ trade-offs + the honest library-gap named; ✅ generalization scoped as
transferability; ✅ plain-language argument written. → VERIFY (the spike: run the functoriality
law-validation + test whether structural code-diagnostics beat baselines on the #4 forecasting harness).

## 5. VERIFY (2026-06-02)

### Risk-2 spike (the riskiest DERIVE commitment) — `verify_diagnostics.py`

Empirical: do the **structural** code-health diagnostics beat / add to the classical baseline at
forecasting real evolution? File-level, leak-free (signals from ≤T, target from >T), pure git,
well-powered. T = 2026-03-10 (HEAD~250); 94 files w/ pre-T history; **46/94 reworked after T**
(base rate 0.49). Target = which files get reworked (≥1 commit) in (T, HEAD].

| signal (≤T) | AP | vs base |
|---|---|---|
| **prior-churn** (autoregressive BASELINE) | **0.811** | +0.32 |
| coupling — temporal co-change (structural) | 0.703 | +0.21 |
| damage-spread — co-change burst (structural-dyn, the *novel* one) | 0.436 | **−0.05** |
| churn + coupling (combined) | 0.743 | (< churn) |
| churn + damage (combined) | 0.519 | (< churn) |

**VERDICT — risk-2 NOT supported (honest negative).** The structural diagnostics neither beat
prior-churn *alone* (0.70 < 0.81) **nor add orthogonal signal** when combined (0.74 < 0.81 — they
*dilute* it); the novel damage-spread proxy is **noise that hurts** (combined 0.52). VERIFY caught
this *before* INSTANTIATE — the gap-#2 / ablation discipline, recurring: a plausible structural
signal that doesn't beat a strong classical baseline.

### What it means (per ARGUE's `transferability-not-generalisability`)

This is a **named non-transfer boundary**, not a dead mission. The functor F transfers the
*structure* — the loop, the categorical form, the Baldwin/xenotype skeleton — but the **ported
Diagnostics object does NOT transfer its predictive power to code**: CA-evolution's signal lived in
structural-dynamics (coupling, damage-spread); **code-evolution's signal is autoregressive (churn).**
The futon5-native structural diagnostics don't carry the code-predictive load that churn does.
(Coupling *correlates* with rework — coupled files churn more — but churn-history already subsumes it.)

### Risk-1 (functoriality) — DEPRIORITIZED

No point verifying F composes cleanly when the object it transports (the Diagnostics) carries no
code-predictive signal. Functoriality check deferred until/unless the diagnostics are re-grounded.

### DERIVE-revision hypotheses (to *test*, not rescues — burden is on them)

1. **Use churn as the code-native health signal** (it IS a legitimate edge-of-chaos diagnostic: high
   churn = unstable region); demote coupling/damage-spread to *audit-only*, not loop-drivers.
2. **Different target:** the diagnostics may predict *refactor*-churn or *bug*-density specifically, or
   the *node-arrival wiring* event — not all rework. Re-test against a sharper target.
3. **Ollivier–Ricci curvature** (the O2 *real* coupling) vs the co-change proxy — may carry more.
4. **The band, not the raw value:** health = *mid-band* (edge-of-chaos), so test band-distance, not
   magnitude.

### VERIFY exit / status

**Risk-2 is a genuine gate.** The mission's categorical/loop machinery (IDENTIFY→ARGUE) stands, but
its empirical premise — that *ported structural diagnostics* forecast code evolution better than
classical — is **NOT supported as defined.** Per the lifecycle, this is a **DERIVE-revision-or-DEFER**
point (operator's call): revise the Diagnostics (hypotheses above) before INSTANTIATE, or DEFER the
mission pending a diagnostic that beats baseline. Do **not** proceed to INSTANTIATE / the apply-loop on
diagnostics that don't beat baseline.

### Convergence: DESCRIPTIVE-not-predictive — the real lesson of the VERIFY negative (claude-3 + claude-6, 2026-06-02)

Compared notes with claude-3 (its E-half-mil-audit ran in parallel). We hit the **same wall
independently**: *every predictive signal tested reduces to a classical baseline* — symbol-
embeddings→keyword, code-edges→common-neighbours, this mission's categorical-diagnostics→churn
(VERIFY: churn AP 0.81 > coupling 0.70 > damage-spread ~noise). Joe: forecasting with the metric is
"a teaspoon to plow a field."

**The opening (two surveys fuse into one morphology):** the FUTON stack is a manifold that **grows at
its edges** (corr(node-age, centroid-distance)=**+0.48**; node-arrival — the dataviz) **and accretes
into a few interior basins** (claude-3: 16 true attractor hubs within the fat tail of 108 files = 5% of
files / 29% of LOC; the rest born-big/generated).

**Re-anchor (this mission's success criterion shifts):** M-categorical-code's value is **DESCRIPTIVE,
not predictive.** Not "the diagnostics forecast evolution better than classical" (FALSIFIED at VERIFY) —
but "**the categorical structure DESCRIBES the manifold's intentional provenance**": which
missions/patterns drove the edge-growth vs fed the basins, with `mission→pattern→turn→code` as a
**composition chain** (the admission-by-composition test from `patterns-as-categorical-objects`) — *does
it compose cleanly into a provenance morphism intention→code-basin?* That is the teaspoon's right job:
delicate structural description, not plowing churn. The Lamarckian *predictive* apply-loop is **deferred**
(prediction loses); the **descriptive/categorical provenance layer** is the live value. Institutionalized:
any correspondence is built **with a co-occurrence baseline from the start**. turn→code is already
reconstructable from git (commit=turn, diff=code). (Joint convergence note shipped to Joe; consistent with
re-anchoring C-substrate-completion's consumer deliverables toward survey/manifold/provenance.)

---

## 2026-06-02 — iiching-CT meta-theory + the trigram-factored retract (Joe + claude-6)

The descriptive/categorical layer got a concrete substrate. See memory `iiching-ct-metatheory` and
`futon5/resources/iiching-ct/`. **Corrected layering** (Joe, this session — supersedes earlier drift):

- **iching (64)** = the **classical hexagram patterns** (`futon3/library/iching/*.flexiarg`, Taoist text→flexiarg).
  This is the **retract** target. `@binary` = upper(3)++lower(3); `@trigrams [upper/lower]`.
- **iiching (256)** = the **CT meta-theory** (the reduced-CT-subset manifest) — the **lift** target.
- **64 CT-atoms** (`iiching-ct-codebook.edn`, 6-bit, Hamming≈CT-distance, +0.27) = the CT-side; they are
  **parallel** to the hexagrams, NOT code-identical (hexagram code is trigram-structural; CT-atom code is
  CT-similarity). NB earlier `iiching-ct-lift.edn` (= 64×4 exotype-rewrites) was fork (b); Joe chose
  fork **(a)**: iiching = the fuller CT concept manifold, lift = restoring the modifier/structural detail
  the retract drops (e.g. monoidal → symmetric/closed/cartesian). The exotype-rewrites become a *separate*
  operator axis, not the lift itself. **Lift rebuild pending.**

**Retract = FACTORED BY TRIGRAM (Joe's design, validated).** A turn's **operator → upper trigram**,
**agent → lower trigram** (classify each half against 8 trigrams, then compose → hexagram). Tool:
`futon5/tools/iiching/trigram_retract.py`; codebook `resources/iiching-ct/iching-trigrams.edn`.
- Flat 64-way text-retract was **degenerate**: hexagram-text spread 0.569 (clumped — shared I-Ching idiom),
  held-out self-recovery **28%**. (CT-*name* retract was also lexical/keyword: 2/8 demos, unfixed by BGE
  vs MiniLM or by def-anchoring — so prose retract is fundamentally lexical; structure is the answer.)
- **Factored trigram retract: spread 0.326, self-recovery 88% (7/8)**, and compositions recover the
  correct classical hexagrams (heaven+earth→Pǐ/Obstruction; thunder+water→Zhūn/Difficulty-at-Beginning;
  fire+lake→Gé/Revolution). The 8-way factoring is the fix.

**Lift — DEFERRED (Joe: "lots of ways, don't get lost").** Options on record: (1) re-key CT-atoms onto
hexagram addresses; (2) semantic hexagram→CT map; (3) keep parallel + design the bridge (claude-6 lean).
**Joe's framing to pursue:** since the lift adds only **+2 bits**, view the iching pattern as a
**centraliser for 4 iiching alternatives**; prefer one of the 4 by **composing the pattern with a textual
"boost" from the turn embedding**. ⇒ implies we should **start doing turn embedding** (not done yet);
it feeds both the lift-boost and the saturation analysis below.

**OPEN CHECK — is iching turn-tagging degenerate?** WM UI shows iching tagging "starting to saturate"
(~200 turns → 64 patterns). Preliminary (NOT the authoritative WM store — these are `futon3/lab/musn` +
`lab/sessions`, 149 tags): **heavily skewed — 3 patterns (hexagram-11, -61, -01) hold ~95%**, long tail
at 1–2. Suggests real degeneracy, but **verify against the actual WM/Agency turn store** (source not
located in-tree; Agency at :7070). If degenerate, the trigram-factored retract above is the likely remedy
(spreads load across the compositional 8×8 rather than collapsing to a few archetypes).
