# README — AIF+: Wiring Diagrams With Checkable Properties

*Index document for the AIF+ apparatus. Authored 2026-05-27 to consolidate
what was buried across `docs/chapter0-aif-as-wiring-diagram.md`,
`futon2/holes/M-aif-head.md`, `futon3/holes/war-bulletin-8.md`, the
`stories/*.aif.edn` corpus in `futon5a/holes/stories/`, and the
`futon5.ct.mission` validator — so future authoring sessions don't have to
re-spelunk for the validation method.*

---

## 1. What AIF+ is

AIF+ is **a typed directed graph representation of an Active Inference loop,
treated as a data structure with mechanically checkable invariants** — not
as an illustrative picture.

Plain AIF specifies the inference-action loop in terms of five roles
(Environment, Sensory, Internal, Active, Preferences) and the standard
ingress / egress paths. AIF+ adds:

- a **typed wiring representation** (ports, components, edges, with types)
- a **six-invariant ladder** I1-I6 that any well-formed AIF+ diagram must
  satisfy (or fail explicitly)
- a **projection-functor calculus** that maps an AIF+ diagram onto a
  candidate substrate (codebase, mission, argument) and reports failure
  modes F1-F5
- a **gate ladder G5-G0** for tracking evidence durability through the
  diagram's life-cycle

The canonical reference is `~/code/futon5/docs/chapter0-aif-as-wiring-diagram.md`
(~964 lines). This README is the **entry-point**; chapter 0 is the depth.

## 2. What an AIF+ file looks like

`.aif.edn` files in `~/code/futon5a/holes/stories/` are the canonical
authoring substrate. Minimal shape (see `leaf-argument.aif.edn` for the
worked exemplar):

```edn
{:leaf "..."
 :name "..."
 :format :aif+
 :version 2
 :date "..."
 :source "path/to/source.md"
 :note "..."

 :spine [:nT1 :nF1 :nS1 ...]     ; the load-bearing node ids

 :nodes
 [{:id :nT1 :type :claim       ; thesis
   :content "..." :status :in-progress :role :thesis}
  {:id :nS1 :type :claim       ; supporting claim
   :content "..." :status :operational :role :support}
  {:id :nF1 :type :falsifiability   ; named observation that would refute
   :content "..." :status :spec-only
   :would-refute [:nS1]}
  {:id :nA1 :type :conflict     ; attack / objection
   :content "..." :status :active
   :counter "..."}
  ;; optional: :type :frame :type :meta :type :data :type :contra-target
  ]

 :edges
 {:bites          [{:from :nA1 :to :nS1 :note "..."} ...]
  :coalesces-from [{:from [:nS1 :nS2 :nS3] :to :nT1} ...]}}
```

**Node types** observed across the corpus:

| Type | Purpose |
|---|---|
| `:claim` | a positive assertion (thesis, support, pillar) |
| `:frame` | background assumption / preference, undergirding claims |
| `:falsifiability` | named observation that would refute a claim |
| `:conflict` | attack, objection, or blind-spot |
| `:meta` | classification of the lifeform itself |
| `:data` | embedded numerical / empirical anchor |
| `:contra-target` | sentence-shaped claim that would damage an opposing thesis |
| `:inference` | a generative-cycle step |

**Status values**: `:operational`, `:active`, `:in-progress`, `:spec-only`,
`:prototype`, `:closed`, `:background`, `:description`, `:pending`.

**Edge categories**: `:bites` (attacks / refutes), `:coalesces-from`
(aggregation), `:would-refute` (falsifiability targets), `:supports`,
`:rooted-in`, `:cross-cuts`, `:closed-by`.

## 3. The six invariants (I1-I6) — chapter 0 §2

| | Name | Predicate |
|---|---|---|
| **I1** | Boundary integrity | Distinguishable inside / outside; finite-bandwidth interface (Markov blanket exists as a span) |
| **I2** | Observation-action asymmetry | Both sensing and acting are present; their types differ; flow is non-symmetric |
| **I3** | Timescale separation | ≥2 timescales, ordered: slow constrains fast, never fast → slow |
| **I4** | Preference exogeneity | No directed path Action → Preferences bypassing Environment (anti-wireheading) |
| **I5** | Model adequacy | Internal structure tracks relevant external structure ("relevant" defined by preferences) |
| **I6** | Compositional closure | Diagram stable under its own operations; ≥2 independent obs→action paths (no SPOF) |

Chapter 0 explicitly notes that **I1, I2, I5 are sliding scales, not
binary**, and resist full mechanisation (chapter0:228-230). They are
validated as **narrative PASS / PARTIAL / FAIL with evidence cited**.

## 4. The five structural checks — chapter 0 §4.4 + `futon5.ct.mission`

For mission-shaped AIF+ diagrams (where ports / components / edges are
explicit):

| Check | Asserts |
|---|---|
| Completeness | every output port reachable from some input |
| Coverage | every component reaches at least one output (no dead components) |
| No orphan inputs | every input connects to ≥1 component |
| Type safety | edge types match source `:produces` and target `:accepts` |
| Spec coverage | every output has a `:spec-ref` |

Diagram-specific extras (chapter0:580-587):
- Boundary check (Sensory + Active fully partition the interface, no back-channel)
- Timescale check (slower endpoint has ≥1 order-of-magnitude lower update freq)
- Exogeneity check (no Action → Preference path bypassing Environment)

## 5. The five projection-failure criteria F1-F5 — chapter 0 §4.5

When projecting an AIF+ diagram onto a candidate substrate (codebase /
mission / argument), the projection can fail in five canonical ways:

| | Failure |
|---|---|
| **F1** | Missing wire (edge in AIF+ has no projection in substrate) |
| **F2** | Violated invariant (I-N fails after projection) |
| **F3** | Missing timescale (substrate collapses a multi-tier ordering to one) |
| **F4** | Type mismatch (port types don't survive projection) |
| **F5** | Structural surplus (substrate has wires AIF+ doesn't account for) |

## 6. The gate ladder G5-G0 — `futon5/docs/gate-pattern-mapping.md`

Each gate scored PASS / PARTIAL / FAIL with evidence-line citations:

| | Gate | Purpose |
|---|---|---|
| **G5** | task specification | what is the work? |
| **G4** | capability / assignment | who can do it? |
| **G3** | pattern reference | what library pattern governs? |
| **G2** | execution | did it run? |
| **G1** | validation | was the result checked? |
| **G0** | evidence durability | is the trace persisted? |

## 7. The three-layer validation method

This is the load-bearing pattern. **AIF+ validation is not a single
function — it is a stack of three coexisting layers, none of which
substitutes for the others.**

### Layer 1 — Mechanised (Clojure)

**Location**: `~/code/futon5/src/futon5/ct/mission.clj` — the
`futon5.ct.mission/validate` function (line 419).

Runs the seven mechanisable predicates:

1. `validate-completeness`
2. `validate-coverage`
3. `validate-no-orphan-inputs`
4. `validate-type-safety`
5. `validate-spec-coverage`
6. `validate-timescale-ordering` (I3, line 311)
7. `validate-exogeneity` (I4, line 350 — graph-reachability check)
8. `validate-closure` (I6, line 386 — per-node-removal SPOF detection)

Returns `{:all-valid bool :checks [{:check :name :passed bool :details ...}]}`.

**REPL invocation** (from `~/code/futon5/`):

```clojure
(require '[futon5.ct.mission :as ctm]
         '[clojure.edn :as edn])

(def diagram (-> "data/missions/futon1a-rebuild.edn"
                 slurp edn/read-string
                 ctm/mission-diagram))

(ctm/validate diagram)
;; => {:all-valid true/false :checks [...]}
```

I1, I2, I5 are **not** mechanised here — they are structural priors
enforced by diagram shape (port types) rather than predicate functions.

### Layer 2 — Narrative audit table (Markdown)

For each `.aif.edn` file, author a **PASS / PARTIAL / FAIL × 12-row
audit table** in a sibling Markdown document. Rows:

- I1-I6 (one each)
- G5-G0 (one each)

Each row carries: verdict + prose justification + evidence cited as
`path:line`. Coverage score = (PASS-count + PARTIAL × 0.5) / 12 — see
the P4/P6 worked example for the exact denominator convention.

**Canonical output shape**:
`~/code/storage/futon6/data/first-proof/aif-plus-method-audit-p4-p6-full.md`
(101 lines, the exemplar).

### Layer 3 — Open-Gap Ledger

Table of `path:line` + excerpt for every still-open claim. This is the
**handoff document**: it tells the next authoring session where the
diagram is structurally honest-but-empirically-pending.

### Plus three AIF+-data-shape checks from WR-8 / WR-9

Not in `ct.mission` (which is for mission-architecture diagrams, not
argument-graphs). Apply manually:

1. Every `:claim` has ≥1 incoming `:supports` OR ≥1 incoming `:bites`
   (it is either argued for or argued against — no orphan claims)
2. Every `:bites` edge tagged `:logical` or `:empirical` (WR-9)
3. Every spine node carries an `:origin` provenance tag

## 8. The render-script pattern (WR-8 + WR-9)

`~/code/futon3/holes/war-bulletin-8.md` WR-8 (lines 133-143):

> *"The 16 leaf + 1 stack AIF+ files at `futon5a/holes/stories/` are the
> canonical self-model. Prose deliverables are generated from them via
> Babashka renderers."*

Named renderers in `~/code/futon5a/scripts/`:

- `render_aif2_prose.clj` — renders `THE-STACK.aif.edn` →
  `futon5a/holes/holistic-argument-aif2.md`
- `render_leaf_prose.clj` — renders per-leaf `.aif.edn` → per-leaf prose
- `render_external_prompt.clj` — renders external-facing prompts from
  the graph

Plus `~/code/futon5a/scripts/detect_drift.clj` — checks rendered prose
matches current EDN; **regenerate on change, never hand-edit prose**.

WR-9 (lines 144-154): **bites are logical or empirical**. Cached
weights in EDN are *priors*; running War Machine carries *posteriors*
(empirical bite-pressure observations).

**The validation report itself is the exception.** Audit Markdown
documents (P4/P6-shape) are authored ABOUT the EDN, not rendered from
it. The graph is truth for *content*; the audit is truth for *process*.

## 9. Three independent "F" namespaces (do not conflate)

The futon stack has three different things called "F" in different contexts:

| Context | What F means | Defined in |
|---|---|---|
| **Per-leaf falsifiability F-nodes** | named observations that would refute specific S-claims; `:status :spec-only` or `:operational` | `leaf-argument.aif.edn` convention |
| **Stack fitness criteria F1-F10** | homeostat properties the stack must satisfy; **maintained**, not acquired | `futon0/docs/stack-fitness-completeness.md` |
| **Projection-failure criteria F1-F5** | how an AIF+ projection onto a substrate can fail | `chapter0` §4.5 |

R-criteria (R1-R12) are AIF-implementation properties (acquired-permanently);
`futon-aif-completeness.md` is the canonical R-reference. R-numbering is
independent of all three F-numberings.

## 10. Worked examples in the corpus

Locations: `~/code/futon5a/holes/stories/*.aif.edn`.

| File | What it represents | Audit status |
|---|---|---|
| `leaf-argument.aif.edn` | the holistic argument as AIF+ | exemplar; "all 5 F-conditions spec-only" |
| `leaf-6-4-4.aif.edn` | portfolio/sorry cluster centroid | operational |
| `leaf-2.aif.edn` | another anthology cluster | operational |
| `leaf-0.aif.edn` | thesis-level leaf | operational |
| `THE-STACK.aif.edn` | stack-level meta-AIF+ (Phase 3 quotient of 16 leaves → 4 conflicts) | operational; rendered to `holistic-argument-aif2.md` |
| `devmap-futon{0,1,2,3,5,7}.aif.edn` | per-repo devmap as AIF+ | operational |
| `leaf-invariants.aif.edn` | invariant-tier nodes | operational |
| `shen-tamkin.aif.edn` | external paper as virtual lifeform (How AI Impacts Skill Formation, Shen & Tamkin 2025) | **authored 2026-05-27; audit pending** |
| `futon-pilot-contra-claim.aif.edn` | the working design for M-pilot-appearance §8.4 | **authored 2026-05-27; audit pending** |

Mission-architecture examples (use `futon5.ct.mission/validate`):
`~/code/futon5/data/missions/*.edn` — 14 worked diagrams including
`futon1a-rebuild.edn`, `coordination-exotype.edn`,
`f3c-grounding-functor.edn`, `social-exotype.edn`,
`evidence-landscape-exotype.edn`, `npt-paper-argument.edn`.

## 11. The contest pattern (new 2026-05-27)

`shen-tamkin.aif.edn` and `futon-pilot-contra-claim.aif.edn` are the
first **paired contest** of AIF+ lifeforms. The pair contains a
`:contest` map at file-level naming:

- the opponent file
- the contest question ("do they eradicate each other or form a symbiotic organism?")
- the coalesce hypothesis (under what conditions both can stand)
- the eradication hypothesis (under what conditions one must yield)
- the provisional result + next steps

This is a new pattern grown from the same root as the
`:coalesces-from` Phase 3 quotient — but at the **lifeform-vs-lifeform**
scale rather than the leaf-aggregation scale. The validation method for
contests is still emerging; see `M-pilot-appearance §8.4` (TBD) for the
first attempted formal contest closure.

## 12. AIF² — when the same graph is also read as an argument

**AIF²** is the convention of treating an AIF+ wiring diagram as *also*
carrying **Argument Interchange Framework** structure (Walton / Reed:
nodes are claims / supports / attacks, edges carry argument-form
semantics). The acronym pun is load-bearing: same letters, two meanings,
and the diagram earns the square when both readings hold simultaneously.

Canonical references in the corpus:

- `~/code/futon3c/holes/missions/M-pilot-appearance.md:1126` —
  *"… inspected by the futon5 AIF+ / AIF² machinery: thesis node …
  support nodes … conflict nodes … falsifiability node …"*
- `~/code/futon0/docs/stack-annotations-schema.md:276` —
  *"I1-I6 is the AIF² hypergraph integrity audit (the gluing conditions)"*
- `~/code/futon4/README-vsatarcs.md:32` — *"AIF² typing of the story's
  spans (roles, claims, supports/attacks)"*

### What AIF² adds to AIF+

Nothing in the data shape changes. The `.aif.edn` files already carry
node-types (`:claim`, `:conflict`, `:falsifiability`, `:frame`) and
edge-types (`:bites`, `:would-refute`, `:supports`, `:coalesces-from`)
that **map cleanly onto Walton/Reed AIF**:

| AIF+ node type | Walton/Reed AIF reading |
|---|---|
| `:claim` (role `:thesis`) | central claim being argued |
| `:claim` (role `:support`) | supporting premise / pro-argument |
| `:frame` | preference / background assumption (warrant) |
| `:conflict` | con-argument / rebuttal / undermining attack |
| `:falsifiability` | named refutation condition |
| `:meta` | argument-about-the-argument (meta-level) |
| `:data` | evidence-source citation |
| `:contra-target` | sentence that would refute an opposing thesis |

| AIF+ edge type | Walton/Reed AIF reading |
|---|---|
| `:bites` (conflict → claim) | rebuttal attack |
| `:would-refute` (falsifiability → claim) | undercutting attack with specified observation |
| `:supports` / `:rooted-in` | pro-argument |
| `:coalesces-from` (set → node) | argumentation-via-multiple-premises |
| cross-file edge (`shen-tamkin/nX` → `futon-pilot/nY`) | inter-argument attack/coalesce |

### Validation is the same; the reading is richer

AIF² validation **is** AIF+ validation — same six invariants I1-I6, same
five structural checks, same three-layer audit method (mechanised +
narrative + open-gap ledger). The square doesn't add a new validator;
it adds a **second register for reading the result**.

The same `validate-completeness` check that asserts "every output port
is reachable from some input" reads, under the AIF² gloss, as
*"every thesis must be argued from at least one premise."* The same
`validate-exogeneity` check that asserts "no path from outputs to
constraint inputs" reads, under AIF², as *"the argument does not bootstrap
its preferences from its own conclusions."* I3 timescale-separation reads
as *"slower argument constraints (frames, meta-claims) bound faster ones
(supports, contras), never the reverse."*

### The contest pattern IS an AIF² operation

The 2026-05-27 paired contest between `shen-tamkin.aif.edn` and
`futon-pilot-contra-claim.aif.edn` is **the first formalised AIF² debate**
in the corpus. The setup:

- Each `.aif.edn` is a complete AIF+ lifeform (passes I1-I6).
- Each is also a complete Walton/Reed argument (passes claim-support-attack
  shape and bite-tagging per WR-9).
- Cross-file edges (e.g. `futon-pilot/nF1 → shen-tamkin/nF4`) are the
  **debate interface** — the contest happens at exactly these edges.
- The `:contest` map at file-level names the **coalesce-vs-eradicate
  hypothesis**: under what structural conditions does one argument absorb
  the other (eradication), and under what scope-restrictions can both
  stand (symbiosis)?

This is **the controlled-conditions Joe named**. The contest is not just
"two essays disagreeing" — it is two graphs running against each other
under shared validation discipline, with mechanically inspectable cross-
edges and named resolution conditions.

### Connection to the Futon 2 Ant War

The Ant War (`~/code/futon2/src/ants/aif/`, see
`~/code/futon2/README-war.md`) is the **cellular-automata-grade**
implementation of the same competitive-AIF pattern:

| | Ant War | AIF² contest |
|---|---|---|
| **Lifeforms** | two ant colonies, each with its own AIF policy | two argument-graphs, each with its own AIF+ spine |
| **Substrate** | shared physical world (cells, pheromone, food, home) | shared semantic substrate (claims, evidence corpus, R-criteria) |
| **Conflict surface** | physical contact (food competition, border duels, brood raids) | argumentative contact (cross-edges, falsifiability hits, frame-attacks) |
| **AIF role per side** | each colony minimises its own free energy | each argument-graph projects EFE-like preferences over inference outcomes |
| **Outcome predicate** | starvation / queen-death / territory-control | eradication / symbiosis / scope-restriction |
| **Validation** | run-simulation; observe trajectory | structural-overlay + manual-audit + projection-test |
| **Granularity** | per-cell, per-tick | per-node, per-edge |

The AIF² contest is the **advanced** version because the lifeforms are
**arguments-as-typed-hypergraphs** rather than ant-policies-over-physics.
The Ant War is bottom-up emergence (simulation reveals outcome). The AIF²
contest is top-down structural (audit reveals coalescence-or-eradication
in advance of any empirical play-through). The two are **dual
implementations of the same competitive-AIF substrate** at different
levels of abstraction.

A future cycle that runs both in coupled form — argument-grade lifeforms
emitting policies that drive ant-grade lifeforms in a shared world —
would be the **AIF³** integration. Not in scope for this README; named
here as a follow-on shape.

### Concrete relevance to the current setting

For the 2026-05-27 contest:

1. Both `.aif.edn` files already pass the AIF² *shape* test (have
   thesis / supports / conflicts / falsifiability + bite-tagging).
2. Both lack the Layer-2 narrative audit (P4/P6-shaped Markdown
   sibling) — that is the next prerequisite for the debate to be
   formally judgeable.
3. The `:contest` maps already name resolution conditions, but those
   conditions are unaudited; the audit Markdown is where each side's
   PASS/PARTIAL/FAIL vector gets recorded.
4. Structural-overlay analysis — enumerating where cross-edges coalesce
   vs where they conflict — is the **AIF² debate-resolution step**, and
   feeds directly into M-pilot-appearance §8.4 ARGUE thesis.

The doc-method-already-exists answer is therefore complete: AIF² adds no
new validation infrastructure; it adds the **reading** that lets the
contest be staged under controlled, audit-grade conditions.

## 13. Recommended workflow for a new `.aif.edn` audit

1. Author the `.aif.edn` file in `futon5a/holes/stories/` following the
   shape in §2.
2. If the diagram is mission-shaped (has ports / components / edges with
   types), run `futon5.ct.mission/validate` from the REPL — Layer 1.
3. Author a P4/P6-shaped audit Markdown sibling — Layer 2:
   - I1-I6 PASS/PARTIAL/FAIL with evidence
   - G5-G0 PASS/PARTIAL/FAIL with evidence
   - Compute coverage score
4. Author the Open-Gap Ledger — Layer 3 — listing every claim still
   pending evidence with `path:line` citations.
5. If the diagram is a contest member, name the opponent + write the
   `:contest` map in the file.
6. Run `~/code/futon5a/scripts/detect_drift.clj` if a prose render
   exists; do not hand-edit rendered prose.

## 14. Future possibility — generating mission ARGUE prose from the graph

The render-script pattern (§8) is currently demonstrated for the
holistic-argument: `render_aif2_prose.clj` reads `THE-STACK.aif.edn` and
emits `holistic-argument-aif2.md`. Once the AIF² contest pattern
matured (2026-05-27), the same shape becomes available for **per-mission
ARGUE prose**:

A future `render_pilot_appearance_argue.clj` (or similar per-mission
renderer) would read `futon-pilot-contra-claim.aif.edn` and emit the
M-pilot-appearance §8.4 ARGUE thesis as prose generated *from* the
graph's spine + R-alignment + contest result. The mission's argumentative
substance would then be **structurally locked to the AIF² lifeform**:
edit the graph, regenerate the prose; the §8.4 thesis cannot drift from
its DERIVE artefact.

Not implemented in this round. Named here as a recognised possibility so
future authoring sessions don't reinvent it. Joe (emacs-repl 2026-05-27):
*"(b) is durable but heavier; (a) lands today. … please note that
possibility in README-aif+.md and we will explore it another time."*

Suggested seed when the time comes:
```clojure
;; ~/code/futon5a/scripts/render_pilot_appearance_argue.clj
;; Render M-pilot-appearance §8.4 from futon-pilot-contra-claim.aif.edn
;; Reads: spine + R-alignment + contest verdict + fitness readout
;; Emits: §8.4 ARGUE thesis as prose, suitable to slot into M-pilot-appearance.md
```

## 15. What this README replaces / supersedes

- The scattered prose across chapter0, M-aif-head, war-bulletin-8, the
  audit exemplar, and `ct.mission` is consolidated here as an
  **index + workflow**.
- Chapter 0 remains the canonical reference for depth.
- `ct.mission` remains the mechanised validator.
- WR-8 remains the canonical statement of graph-is-truth.

This README is the entry-point so future sessions don't have to
re-spelunk for the method.

---

**Cross-references** (absolute paths):

- `~/code/futon5/docs/chapter0-aif-as-wiring-diagram.md` — canonical depth
- `~/code/futon5/src/futon5/ct/mission.clj` — mechanised validator (`validate`, line 419)
- `~/code/futon5/docs/gate-pattern-mapping.md` — G5-G0 gate ladder
- `~/code/futon2/holes/M-aif-head.md` §5.2-5.3 — dual application of I1-I6 + projection test
- `~/code/futon2/docs/futon-aif-completeness.md` — R1-R12 (AIF-implementation criteria; not F)
- `~/code/futon0/docs/stack-fitness-completeness.md` — F1-F10 stack-fitness (not the same F as falsifiability)
- `~/code/storage/futon6/data/first-proof/aif-plus-method-audit-p4-p6-full.md` — canonical audit output shape
- `~/code/futon3/holes/war-bulletin-8.md` — WR-8 graph-is-truth, WR-9 logical-vs-empirical bites
- `~/code/futon5a/scripts/render_aif2_prose.clj` — prose render exemplar
- `~/code/futon5a/scripts/detect_drift.clj` — drift detector
- `~/code/futon5a/holes/stories/` — `.aif.edn` corpus
- `~/code/futon5/data/missions/` — mission-architecture worked examples
