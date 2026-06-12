# M-differentiable-code

**Status:** MAP opened 2026-05-31
**Xenotype:** derivation (IDENTIFY → MAP → DERIVE → ARGUE → VERIFY → INSTANTIATE)
**Created:** 2026-05-30
**Owner:** claude-6 (ratified by Joe, 2026-06-01) — owns this mission (E2); contributes to Campaign `C-substrate-completion` (futon3c). Keystone `M-substrate-metric` owned by codex-3; co-consumer `M-aif2` (E1) by claude-3.
**Repo:** futon5
**Escrow (E2, Campaign C-substrate-completion):** this mission's continuity requirement — a continuous code-graph embedding (the band a gradient ranges over) **+ node-granularity resolution** (gap #1) — is registered `:held` on the keystone `M-substrate-metric`. Per the Campaign's CONSTITUTION, node-granularity is a *shared* prerequisite resolved once in the keystone (it's also the metric's own precondition). Release: `:contract-released` on Campaign STANDARD-VERIFY, `:satisfied` on metric delivery. See `futon3c/holes/campaigns/C-substrate-completion.md`. Don't solve node-granularity unilaterally here — it's keystone work now.

## Context

The futon stack is now past half a million lines of Clojure + Emacs Lisp
(501,745 measured 2026-05-30, including futon3 as origin material; ~433k for
the living stack with origin excluded). At that scale, nobody holds the whole
thing in their head. We already have two separate things that, put together,
could let us *reason* about the stack's structure instead of just reading it:

1. A **model of the code as a graph** — namespace/mission/pattern nodes and
   their dependencies — which can live in the futon1a hypergraph (this is the
   priors-vs-posteriors framing of M-webarxana-as-monitor: the hand-drawn
   futon5 wiring diagrams are the *prior*, the extracted running graph is the
   enriched *posterior*).

2. **Real JAX experience in futon5** — specifically `tools/tpg/jax_refine.py`,
   which gradient-optimises TPG program weights so they satisfy a verifier
   spec, then injects the improved weights back into the evolutionary
   population (the "Lamarckian step"). Its own docstring states the key move:
   *"band-score satisfaction is a differentiable function of program weights
   (via softmax-approximated routing)."* That is a loss function with real
   semantics, not decoration.

This mission asks: **can we point the `jax_refine` pattern at the code graph
instead of at TPG programs?** I.e. treat the stack's wiring as a weighted
graph, treat the wiring-contract claims as the spec, and use autodiff to (a)
measure how well the real structure satisfies the intended structure and
(b) propose structural edits that improve satisfaction.

The seed is a late-Saturday conversation (2026-05-30) reflecting that we
haven't used futon5's JAX work in a while, and that our code-graph models
*could* feed it. A collaborator (Rob) is doing the structurally-similar thing
in his stack (mfuton): refactoring everything into ≤800-line Python modules
reasoned about via a graph database. The convergence is "make the code
addressable as graph nodes"; the **differentiator** we'd add is putting
autodiff-driven active inference on top of that graph, not only queries.

## The shift

From: "read 500k lines of code and hold the wiring in your head"
To:   "represent the wiring as a differentiable object, score it against the
       intended contract, and gradient-descend toward edit-proposals"

The analogy to jax_refine.py is meant to be exact, slot-for-slot:

| jax_refine.py (TPG)            | here (code graph)                          |
|--------------------------------|--------------------------------------------|
| TPG programs                   | stack nodes (namespace / mission / pattern)|
| softmax routing weights        | edge weights in the wiring graph           |
| `verifier_spec` band-scores    | wiring-contract claims                      |
| `parse_tpg` → JAX arrays       | code-graph → JAX arrays  *(the hard step)* |
| refined weights → population   | gradient → edit-proposals → diagram        |

## Why this matters

- It unifies, rather than competes with, the directions already committed:
  M-bayesian-structure-learning (the Friston / active-inference reframe —
  this is its *differentiable* realisation), M-substrate-2 / M-live-geometric-stack
  (whose (T, ∇, Δ, drift) operators are currently **heuristic** — a hand-rolled
  ∇ that this mission could make a literal autodiff gradient), and
  M-webarxana-as-monitor (priors vs posteriors over the wiring).
- Every ingredient exists in isolation and has never been wired into one loop:
  the loss-semantics (`jax_refine.py`), the spec (`futon3c/docs/wiring-claims.edn`,
  13KB, machine-readable; `wiring-contract.md`, 30KB), the graph store (futon1a),
  and the priors (futon5 hand-drawn diagrams). The novel artefact is the loop.

## What exists (verified 2026-05-30, do not re-assume)

- `futon5/tools/tpg/jax_refine.py` — differentiable weight refinement against a
  verifier spec; `grad`, `jit`, `vmap`, optimisation trace, satisfaction
  before/after. THIS is the pattern to port.
- `futon5/tools/tensor/jax_step.py` — JAX-as-fast-array (bitplane CA step). Useful
  for speed, but it is NOT the differentiable-reasoning piece; do not confuse the two.
- `futon3c/docs/wiring-claims.edn` (13KB) + `wiring-contract.md` (30KB) +
  `mission-claims.edn` — machine-readable architectural claims. Candidate spec.
- futon1a hypergraph — candidate graph store (note: POST /entity REPLACES props;
  fetch-merge-upsert — see M-futon1a notes).
- futon5 hand-drawn wiring diagrams — the priors.

## The real gaps and doubts (named on purpose — these are the work, not asides)

1. **Node granularity is the upstream blocker.** `jax_refine` works because TPG
   nodes are uniform, small, and array-friendly (`parse_tpg` flattens cleanly).
   Our nodes are NOT: sizes span four orders of magnitude (a 115k-line futon3c
   namespace down to a one-line `defn`). A gradient over wildly non-uniform
   nodes is ill-conditioned. **The `parse_tpg`-equivalent for the stack is the
   unsolved step**, and node-uniformity is what makes the gradient well-behaved.
   - Rob gets uniformity *by construction* (the ≤800-line refactor IS the
     chunking). We would get it *by extraction* — harder. Joe's standing note
     (2026-05-30): we can, like Rob, **refactor and enforce a line/size limit
     in future** to converge our case toward his. That is a legitimate path,
     but it is a precondition to make explicit, not a free assumption.
2. **What does a gradient over a wiring diagram MEAN?** This is the question that
   usually hides the real work. The mission is only worth doing if the loss has
   real semantics. The candidate answer: free-energy / expected-information-gain
   over the structure (M-bayesian-structure-learning), with the wiring-claims as
   the band-score spec — the direct analogue of jax_refine's verifier_spec. If
   no such loss survives scrutiny in DERIVE, JAX is decoration on a graph query
   and the mission should stop.
3. **The R-GCN warning.** A prior experiment found R-GCN underperformed for
   retrieval (hard negatives needed; BGE preferred). So "just run a GNN over the
   wiring graph" is a *tried-and-found-wanting* path — be deliberate about not
   re-walking it. jax_refine's explicit differentiable-satisfaction objective is
   attractive precisely because it is NOT a black-box GNN embedding.
4. **Which trees are canonical.** The 563k → 501k correction came from deleting
   stale worktrees; futon3 is load-bearing origin material. Any code→graph
   extractor MUST exclude worktrees / origin / `.state` sandbox checkouts or the
   posterior is contaminated (same hazard as the substrate-2 ingest rule).

## Derivation plan (sketch — to be filled per phase)

- **IDENTIFY** *(here)* — name the convergence (Rob's graph-DB refactor + our
  JAX + our code-graph), the slot-for-slot analogy, and the four gaps above.
- **MAP** — **item zero (cheapest falsification, runs before pilot selection):**
  take ONE real `wiring-claims.edn` claim and check whether it becomes a
  band (center/width or pass band) *without manufacturing*. If it cannot, that
  is a finding, and DERIVE must manufacture the loss before any extractor work.
  Then inventory precisely: read `jax_refine.py` end to end and extract its
  contract (inputs, loss, the softmax-routing trick, output shape); pick the node
  granularity for ONE subsystem; choose the extractor (static namespace deps?
  mission graph? library patterns?).
- **DERIVE** — **the spine: name the hard structural choice being relaxed, and
  the typed index it ranges over** (per the 2026-05-31 multi-agent view). The
  candidate: typed structural assignment — for node `n` under relation-type `r`
  / responsibility `c`, *which target/boundary should `n` connect to?* — relaxed
  to a soft adjacency tensor `A[n, r, target]`. From that, define the loss with
  real semantics (gap #2) and state precisely what a gradient step proposes and
  why it is a structural improvement. **Granularity is part of this step, not a
  separate one** (gap #1): the relaxed choice (file-dep vs function-ownership vs
  claim-to-code vs boundary-membership) *determines* the node schema and the
  `parse_*`-equivalent that flattens the subsystem to JAX arrays. Bands are
  authored/spec-derived, never fit (gap #3).
- **ARGUE** — short and strategic: is the differentiable loop genuinely better
  than querying the graph? Hold open until the VERIFY hooks below are ratified.
- **VERIFY** — operational hooks (what we will actually DO about each tension):
  - *Gap #1 (granularity):* produce the array encoding for ONE subsystem and
    show the gradient is numerically sane (not dominated by the 115k-line node).
    Decide whether a size-limit refactor is a prerequisite or a parallel track.
  - *Gap #2 (loss semantics):* write the loss down explicitly; show one
    edit-proposal it produces and argue, in plain language, why that edit
    improves the wiring vs the contract.
  - *Gap #3 (R-GCN):* confirm the chosen approach is the explicit-satisfaction
    objective, not a black-box embedding; if it drifts toward GNN, stop and
    reconsider.
  - *Gap #4 (canonical trees):* the extractor excludes worktrees/origin/.state;
    verify the node count matches the ~433k living-stack figure, not 563k.
- **INSTANTIATE** — the end-to-end vertical slice: extract ONE futon3c
  subsystem's dependency graph → lay it beside its futon5 hand-drawn diagram →
  score it against that subsystem's wiring-claims → run one `jax_refine`-style
  gradient step → emit one structural edit-proposal. Success = the loop runs on
  real data and the disagreement between extracted-posterior and drawn-prior is
  legible (the disagreement IS the signal — same epistemics as combining-methods-
  as-diagnostic).

## First concrete probe (the actual next action, when picked up)

Not the whole mission. The initial thought was a single futon3c subsystem
(candidate: the agency/registry + transport cluster, or the AIF/substrate
cluster), but the MAP discussion on 2026-05-31 found a better three-pilot shape:

1. **Pilot A — futon5 / TPG:** the friendly case. Prove the mechanics on the
   subsystem already closest to `jax_refine.py`.
2. **Pilot B — futon4 / Arxana-VSATARCS:** the comparator case. Test whether the
   method generalises to Elisp and to code-doc-test/workflow alignment
   ("differentiable documentation"), rather than merely succeeding where TPG is
   already shaped like the method.
3. **Pilot C — futon2 / AIF implementation:** the high-impact case. Apply the
   method to `futon2.aif.*` and benchmark the pressure report against
   `futon2/holes/M-aif2.md`, especially its findings about fixed action-class
   support, R12 one level too low, and missing boundary-redraw admissibility.

Each pilot should produce four things side by side:
1. the extracted graph for the slice (excluding worktrees/origin/.state),
2. the prior/spec surface for that slice (diagram, docs, `.aif.edn`, or mission
   claims),
3. whether those claims give a band-score spec we could feed a
   `jax_refine`-style loss,
4. **the discrete hard choice + its relaxation for THIS slice** (per the
   2026-05-31 multi-agent view). Without this, Pilot A can pass merely by
   inheriting TPG's already-solved relaxation and tell us too little; the other
   pilots must name their own — e.g. for docs/AIF the choice is usually *which
   surface owns this claim / action-class / preference boundary?*, not *does a
   dependency edge exist?*

If extracted ≈ drawn and a spec exists → we have a real vertical-slice
candidate and the jax_refine port is worth attempting. If extracted ≠ drawn in
interesting ways → that disagreement is itself the first finding. If no spec
falls out of wiring-claims → DERIVE has to manufacture the loss before any JAX
is justified.

## 2. MAP (opened 2026-05-31)

MAP discipline: record facts, candidate pilot surfaces, and survey questions.
Do not commit to the differentiable loss or refactor design until DERIVE.

### Survey questions

**Q1. What is the minimal reusable contract of `jax_refine.py`?**

Preliminary answer: `tools/tpg/jax_refine.py` accepts JSON with `tpg`, `traces`,
`verifier_spec`, and optional `config`; flattens TPG programs into JAX arrays
`W` and `b`; parses trace diagnostics and verifier bands; replaces discrete TPG
argmax routing with softmax routing; optimises negative band satisfaction; and
emits original/refined satisfaction, improvement, refined weights, and an
optimisation trace. The important reusable shape is not "JAX" by itself, but:

`structured thing -> array encoding -> differentiable relaxation of a discrete
choice -> explicit band-score satisfaction -> ranked pressure on weights`.

**Q2. What graph schema must all pilots share?**

Candidate common schema:

| Field | Meaning |
|---|---|
| `:node/id` | stable identity (`repo:path`, namespace, symbol, mission section, claim id) |
| `:node/type` | `:file`, `:namespace`, `:symbol`, `:test`, `:claim`, `:doc-section`, etc. |
| `:node/source` | canonical source path + optional line/span |
| `:edge/type` | `:requires`, `:provides`, `:defines`, `:tests`, `:claims`, `:writes`, `:reads`, `:supports`, `:violates` |
| `:edge/from`, `:edge/to` | stable node ids |
| `:edge/weight` | observed strength, prior confidence, or optimisable relaxed weight |
| `:claim/band` | target center/width or pass band for differentiable scoring |

Language-specific extractors are allowed. The downstream graph and scoring
surface should not be language-specific.

**Q3. Are the pilots actually different enough to prevent self-confirmation?**

Preliminary answer: yes.

- Pilot A (`futon5/tools/tpg`) is friendly because it already has the JAX loss
  precedent and uniform program-like nodes.
- Pilot B (`futon4/dev/arxana-browser*.el` or `arxana-vsatarcs*.el`) is a
  comparator because Elisp exposes discrete, parseable structure through
  `require`, `provide`, `defun`, `defcustom`, `define-minor-mode`, and
  `ert-deftest`, while the target property is code-doc-test/workflow alignment
  rather than program-weight refinement.
- Pilot C (`futon2/src/futon2/aif/*`) is higher impact and higher risk because
  it targets the implementation that should drive stack priorities. It should
  run only after A and B produce meaningful pressure reports.

**Q4. What would count as a good Pilot C result against `M-aif2.md`?**

Not "optimise the AIF" and not "auto-refactor the AIF." The first success is a
pressure report whose top findings rediscover or sharpen `M-aif2` MAP findings:
the action-class inventory is code rather than data, support is enumerated in
multiple places, R12 infers value-of-class rather than existence-of-class,
unknown-unknown action classes are invisible, preferences are static, and
boundary-redraw admissibility is missing as a first-class mechanism.

### Inventory existing infrastructure

| Surface | Ready facts |
|---|---|
| `futon5/tools/tpg/jax_refine.py` | Existing differentiable band-score optimiser; supplies the pattern to port. |
| `futon5/tools/tensor/jax_step.py` | JAX array precedent, but not the reasoning/loss precedent. |
| `futon3c/docs/wiring-claims.edn` + `wiring-contract.md` | Candidate machine-readable spec surface for code wiring. Needs band-score audit. |
| futon1a / substrate-2 hypergraph | Candidate persistent graph store. It is storage/query substrate, not the differentiable representation itself. |
| futon5 diagrams | Prior wiring diagrams for prior/posterior comparison. |
| futon4 Arxana / VSATARCS | Concrete Elisp comparator with code, docs, tests, writer actions, trace/belief machinery. |
| `futon2/holes/M-aif2.md` + `futon2/src/futon2/aif/*` | High-impact benchmark target with known architectural pressure points. |

### Ready vs missing

| Ready (no new code needed) | Missing (the actual work) |
|---|---|
| A friendly differentiable optimisation precedent (`jax_refine.py`) | A code/doc graph extractor that emits the common schema |
| Multiple candidate spec surfaces: wiring claims, diagrams, `.aif.edn`, mission docs, tests | A rule for converting each spec surface into band-score claims |
| Substrate-2/futon1a as graph persistence | A stable projection from repository graph to JAX arrays |
| futon4 as an Elisp comparator with concrete discrete forms | Elisp extractor over `require`/`provide`/definitions/tests and doc/workflow surfaces |
| `M-aif2` as a benchmark with known pressure points | A safe Pilot C protocol that reports pressure before proposing AIF refactors |
| Canonical-tree cautions already known | Enforcement of exclusions for worktrees, origin material where inappropriate, `.state`, compiled artifacts, caches, and vendor trees |

### Pilot ordering finding

MAP opened with a revised sequencing constraint:

1. Run **Pilot A** first because it is the easiest falsification of the
   mechanics.
2. Run **Pilot B** second because it tests generalisation into
   "differentiable documentation" without aiming at the live AIF core.
3. Run **Pilot C** third because it is the highest-impact but most
   self-referential target; success should be benchmarked against `M-aif2`, not
   judged by whether the system confidently emits an edit.

This ordering is a guard against over-convincing ourselves on TPG while also
avoiding the opposite error: starting with AIF and being unable to distinguish
method failure from target complexity.

### Item zero result (2026-05-31)

Ran the cheapest-falsification check via micro-whistle handoff to codex-2.
Chosen claim: `:daemon/portfolio-inference-scheduler` (`wiring-claims.edn:58-67`,
verified verbatim — not fabricated). codex-2 verdict: BAND-NATURAL. **claude-2
review sharpens this to BAND-NATURAL-but-mostly-boolean**, which is the actual
finding:

- The claim's `:verification/notes` DO supply pass/fail predicates on their own
  terms — but most are **hard booleans**, not the continuous band a
  `jax_refine`-style loss needs: `running? == true`, `error-count == 0`,
  `last-result-keys non-empty` have nothing for a gradient to range over.
- `tick-count > 0` is a threshold on a monotone counter — degenerate.
- **`grows by 4 per tick`** is the ONLY genuinely band-shaped quantity (target
  4, tolerance width) — continuous, softmax-relaxable, differentiable.

So: wiring-claims yields verification predicates "naturally," but they are
mostly **binary state-checks, not continuous bands**. Out of one rich claim,
exactly one number is band-shaped without manufacturing. **Implication for
DERIVE:** the real work is the relaxation from pass/fail predicates to a
continuous satisfaction surface — which is the spine question (what discrete
choice is being relaxed) arriving from the data side. Item zero did NOT kill the
mission, but it disproved the clean "claims → bands" assumption: a
predicate→band relaxation layer is now a named precondition, not a freebie.

**Survey across all claims (iterated via whistle, then claude-2 self-check):**
codex-2 classified all 7 `wiring-claims.edn` claims by what their spec surface
offers — CONTINUOUS (a quantity a gradient can descend), BOOLEAN (pass/fail
state), or STRUCTURAL (presence/absence of files/edges/components):

- **1 of 7** has any continuous quantity (`:daemon/portfolio-inference-scheduler`:
  "grows by 4 per tick").
- **6 of 7** are boolean + structural only (`verification/required #{:commit
  :test :script}` + `implementation/files` lists).
- All 7 carry a structural surface; none of the other six expose a scalar.

claude-2 read `mission-claims.edn` directly (`futon3c/docs/mission-claims.edn`,
38 lines, 5 claims: `:mission-peripheral/cycle-machine`, `…/snapshot-evidence`,
`:mission-control/portfolio-observer`, `:war-room/bulletin-evidence`,
`:war-room/tri-agent-loop`). **All 5 are boolean + structural** — descriptions
like "runs via", "snapshots are emitted", "is executable and evidenced", plus
`:verification/required` sets and `:implementation/files` lists. No scalar in any
of them. So mission-claims adds NO continuous bands.

(Correction, logged for discipline: an earlier draft of this finding asserted a
`:mission/ranking` claim at `mission-claims.edn:30-35` with a "monotone scoring
function." **That claim does not exist** — it was fabricated before reading the
file and has been removed. The real continuous/scoring surface lives in the
`portfolio-inference` *code* (mission-feature scoring, precision-proxy
aggregation), referenced by `:peripheral/portfolio-inference`, NOT in any claims
file. Treat "scoring lives in portfolio-inference code" as a hypothesis to verify
in MAP, not an established band-source.)

**Item zero — settled finding:** across BOTH claims files (7 wiring + 5 mission
= 12 claims), only the 4-per-tick growth quantity is unambiguously continuous;
`:ops/cyder-process-registry`'s "every process registered" is a coverage target
(1.0) with no denominator supplied. **~11 of 12 claims are boolean + structural.**
wiring/mission-claims are therefore **not a viable band-source as-is**. DERIVE
has a named, non-optional precondition: **manufacture a predicate→band relaxation
layer** (and/or derive continuous proxies — coverage ratios over the structural
edge/file sets). The mission is NOT killed, but the "claims → bands for free"
assumption is disproved. Candidate place to find real continuous bands, to be
verified not assumed: the portfolio-inference scoring code.

### Asset: continuous embedding substrate (added 2026-05-31)

Item zero showed the *claims* are ~11/12 boolean+structural — no continuous
quantity to descend. The resolution (Joe, 2026-05-31): **we already own a
continuous substrate — embeddings of missions and patterns — and have used it
only for retrieval, never as a loss surface.** This turns the item-zero finding
from a near-dead-end into the mission's clearest path. Verified on disk (not
assumed):

- `futon3/resources/embeddings/minilm_pattern_embeddings.json` (+ fasttext/glove
  variants) — pattern nodes already in a vector space.
- `futon3a/src/futon/missions.clj:483` ("Search missions corpus via parallel v1
  (MiniLM cosine) + v1.1 (structural)") + `futon3a/src/futon/notions.clj:201`
  (`:score (double (dot query-vec vec))`) — the actual MiniLM-cosine retriever.
  `futon4/dev/web/webarxana/src/webarxana/server/mission_search.clj` is an HTTP
  wrapper over it, NOT the retriever itself (corrected after codex-2 audit — I
  first mislocated the retriever in the wrapper).

**Why this is the asset, not the R-GCN trap (gap #3 guardrail):** the legitimate
move is NOT "learn an embedding that satisfies the graph" (that IS the R-GCN
black-box objective already found wanting). It is: **embedding coordinates are
fixed observations; the differentiable choice stays the typed structural
assignment `A[n, r, target]`; the embedding supplies the continuous BAND the
claims could not** — e.g. edge `n→m` is satisfied to the degree
`cos(emb(n), emb(m))` sits in a target band, band specified from the contract.
The embedding is the *measurement instrument*, not the thing optimised. This
keeps codex-2's spine intact and answers the "what makes the satisfaction
continuous?" question the claims left open. (The embedding is also a candidate
`parse_*`-equivalent for gap #1: a 115k-line namespace and a one-line defn both
become a fixed-dim vector, which *normalises the coordinates* — but does NOT make
gap #1 disappear, per codex-2's caveat: extraction and attribution granularity
(what counts as a node, where its boundary is) is still an unsolved design
problem. Fixed dimensionality ≠ solved granularity.)

### Asset: the missing piece is a DIRECT code embedding — and futon6 already has the pattern

The missions/patterns embedding does NOT yet embed the **code itself**. But the
translation precedent already exists in futon6's Arxiv/math work. Real docstrings
(read, verbatim — earlier draft of this section quoted INVENTED docstrings; see
correction note):

- `futon6/src/futon6/paper_hypergraph.py` — *"Stage 5d: Paper hypergraph. Lifts a
  paper into a structure-and-terminology-first semantic object: the argumentative
  skeleton (theorem/lemma/proof blocks + their dependencies) plus terminological
  anchors…"* Classical arm produces nodes {section, definition,
  theorem/lemma/proposition/corollary, proof, equation, citation, concept,
  technique} and edges {derivation, definition-use, structural-cooccurrence,
  citation-grounding}.
- `futon6/src/futon6/graph_embed.py` — *"Stage 9b: Typed hypergraph embedding via
  Relational GCN… Uses R-GCN (one weight matrix per edge type) with
  self-supervised contrastive training [InfoNCE]."* Edge types: iatc, mention,
  discourse, scope, surface, categorical.
- Artifacts on disk: `storage/arxiv-paper-hg-gpu/` (`embeddings.npy` 39M textual,
  `hypergraph-embeddings.npy` 3.7M structural, `graph-gnn-model.pt`,
  `hypergraphs.json`) and `storage/math-processed-gpu/` (`embeddings.npy` 3.1G,
  `hypergraph-embeddings.npy` 522M, `structural-similarity-index.faiss` 522M).

This is a **dual embedding over a typed hypergraph**: a textual arm
(`embeddings.npy`) and a **structural arm that is R-GCN-based**
(`hypergraph-embeddings.npy` + `graph-gnn-model.pt`). Two consequences, both
sharper than the earlier (fabricated) version of this note claimed:

1. The hypergraph schema (typed nodes + typed edges) is **exactly the
   `:node/type` / `:edge/type` schema this mission proposed in Q2** — already
   built and populated at scale for mathematics. That is the real asset: the
   common graph schema is not hypothetical.
2. **The structural embedding is R-GCN — which is precisely what gap #3 and
   [[feedback_superpod_embeddings]] flag as tried-and-found-wanting.** And futon6
   has DIRECT evidence it failed, surfaced by codex-2's audit:
   `futon6/technote-arxiv-mining.md:14-30` records that the R-GCN graph
   embeddings collapsed to cosine ~1.0 (FAISS could not distinguish papers), and
   that switching to BGE text embeddings (`superpod-job.py:32` Stage 2:
   `bge-large-en-v1.5`) resolved retrieval; R-GCN is logged "to be fixed."
   So futon6 is NOT a ready-made band-source to adopt uncritically — it is the
   cautionary precedent, with a recorded failure. The differentiable-satisfaction
   objective this mission wants is attractive *precisely because* it is not the
   R-GCN black-box embedding futon6 already tried and saw collapse. The asset is
   the **typed-hypergraph schema + textual-BGE** layer; the R-GCN layer is the
   thing to learn from, not reuse.

*(Correction note, logged for discipline: the first draft of this entire asset
section was written with FABRICATED docstrings for `paper_hypergraph.py` and
`graph_embed.py` — and the invented text claimed the structural embedding
"honours BGE not R-GCN" when the real code IS R-GCN, i.e. the fabrication
reversed the actual finding. Corrected against verbatim reads. This is the
fourth fabrication in this session; see the session-discipline note. The
corrected section was subsequently audited adversarially by codex-2: 5/6 claims
verified against ground truth, item 2 corrected (retriever location), and the
R-GCN-collapse evidence above was added from that audit.)*

**Springboard (named, not yet scoped):** because the futon6 substrate IS the
Arxiv/math hypergraph, the same differentiable-structure machinery points two
ways — "differentiable code" (this mission) and **"differentiable mathematics"**
(the same loss over the math hypergraph). Both are realisations of
M-bayesian-structure-learning, which motivated this mission in the first place.
Differentiable mathematics is a deferred but natural sibling, not in scope here —
recorded so the connection is not lost.

### The missing code embedding — candidate grain is `:scope` (overlay); prototyping out-of-campaign (2026-06-01)

Joe's direction (via claude-3, 2026-06-01): de-risk the missing code embedding
**outside the Campaign**, in the futon6 `M-differentiable-math` timeline (claude-2 owns),
which already HAS the math-embedding equivalent we lack — then feed design lessons back to
the keystone. This does NOT touch E2's `:held` slice or the frozen-pending contract.

**Reads that ground the grain question (verified 2026-06-01, file:line):**
- futon6's typed hypergraph already carries a **`scope` node type** (with `post`/`term`/
  `expression`), detected by `detect_scopes()` (`futon6/.../nlab-wiring.py:1174-1281`) at
  sentence→paragraph grain — *between* document and symbol. **But there is NO scope-level
  embedding**: embeddings are textual-BGE (over posts/entities — discriminative, coherent)
  + structural-R-GCN (collapsed to cosine≈1.0). claude-2's first-probe confirmed the BGE
  half is semantically coherent on real math data with zero building.
- futon4's code-overlay analogue is the `about-var` claim→var reflection link
  (`M-self-representing-stack.md`) — per-var, not per-scope; VSATARCS itself overlays AIF+
  on *prose*, not a code hypergraph.

**Design hypothesis (the contribution):** the right code grain for O4 continuity is neither
`:file` (the 115k-line conditioning problem, gap #1) nor bare `:symbol` (context-free) but a
**`:scope`/overlay node** grouping a coherent code unit, **BGE-embedded on its text window**
(source span + docstring/claim context). This satisfies O4(c) (discriminative textual) and
explicitly avoids the R-GCN arm — now **double-confirmed collapsed** in *both* the arxiv and
math timelines, the strongest possible support for gap #3's guardrail.

**O1 boundary (do not cross unilaterally):** codex-3's ratified O1 identity set is
`:file/:namespace/:symbol/:boundary` — **no `:scope`**. If the out-of-campaign pilot concludes
the real metric wants a `:scope`/overlay grain, that is an **O1 schema escalation to codex-3**
(a conversation), NOT a unilateral re-authoring of identity (per Joe, 2026-06-01).

**Cheapest demonstration (mirror claude-2's math first-probe):** BGE-embed a slice of
code-overlay scopes, check nearest-neighbour coherence. If coherent → the continuity half for
*code* exists at `:scope` grain, exactly as it already does for math — and the grain question
has an answer to bring back to O1.

### Scope-grain first-probe RESULT — hypothesis SUPPORTED (measured 2026-06-01, claude-6)

Ran the demonstration locally, escrow-clean (no GPU, MiniLM `all-MiniLM-L6-v2` from local
HF cache via `futon3a`'s embedder). Script + output live in claude-2's timeline:
`futon6/scripts/code_scope_grain_pilot.py`,
`futon6/resources/differentiable-math/code-scope-probe/code-scope-grain-result.json`.
Slice: **342 scope nodes from 23 modules** of `futon6/src/futon6/*.py` (parsed with `ast`;
each function/method/class → a scope window). Coherence proxy = same-module-in-top5 NN rate
(module = known concern label).

| Text variant | content | same-module-in-top5 |
|---|---|---|
| `bare` | `name(signature)` only — the context-free "bare function" | **0.422** |
| `ctx` | qualname + signature + **docstring** (no module identity) | **0.577** |
| `scope` | + module name + module-doc (the full overlay) | **0.974** |

**Findings (stated with the confound controlled):**
1. **`ctx` > `bare` (0.577 vs 0.422)** is the clean, non-confounded result: adding the local
   docstring/signature *overlay content* genuinely raises concern-coherence over a bare
   function name. Joe's "don't embed bare functions" is borne out — context is real signal.
2. **`scope`'s jump to 0.974 is partly tautological** (the text contains the module name), but
   partly legitimate — a real `:scope`/`:namespace` node natively carries namespace context,
   which is genuine locating signal. Read 0.974 as "namespace-context is strong," NOT "pure
   semantics."
3. **Cross-module semantic bridges survive every variant** — e.g.
   `thread_performatives:diagram_to_hyperedges ↔ hypergraph:assemble` (0.79),
   `symbol_grounding:_collect_envelopes_lazily ↔ math_ast:find_math_envelopes` (0.75). These
   are the code analogue of claude-2's curvature bottlenecks (math: group-theory↔probability):
   the **same continuous geometry that gives E2 its band also surfaces the bridges E1's
   curvature marks** — one object, both cuts (C-substrate-completion §4 O2), now shown on code.

**What this settles / doesn't.** Settles: a discriminative *textual* code embedding at scope
grain is coherent and buildable with cached MiniLM/BGE — no R-GCN (gap #3 guardrail holds;
this is the non-R-GCN path). Does NOT yet settle: (a) whether `:scope` should be a distinct
O1 grain or is adequately served by `:symbol`+`:namespace` context (this probe used per-def
windows *with* namespace context — i.e. `:symbol` carrying `:namespace` context, which may
mean no new grain is needed); (b) conditioning across orders of magnitude (O4(b)) — untested
here, the slice is uniform-ish Python defs.

**O1 implication (the feedback to codex-3):** the probe leans toward "**no new `:scope` grain
needed** — a `:symbol` node embedded *with its `:namespace` context window* already gives the
coherent continuity band." That is a weaker, cheaper claim than escalating a new grain, and it
is the one to bring to codex-3: O1's existing `:symbol`/`:namespace` set may suffice for O4
continuity, provided the embedding text window is the *contextualized* symbol, not the bare
symbol. To be confirmed on a Clojure slice (the real stack) and against the conditioning test.

### O4(c) confirmed at BGE + the O4(b) size→degree reframe (2026-06-01, claude-6)

Keystone (codex-3) ratified the **IFR** grounding: the metric reads what the substrate already
has — **baseline pilot = `:symbol` + per-node `:conditioning-scale`, BGE-embedded, JAX for
`∂s/∂A`**; `:scope`/refactor are costed fallbacks only if baseline fails. Re-ran the probe at
the ratified embedding (`BAAI/bge-large-en-v1.5`, local cache):

- **O4(c) discrimination — measured PASS.** Off-diagonal cosine median ≈0.51–0.56, p99 ≈0.71
  (`ctx`), **fraction > 0.95 ≈ 0** (0.00–0.28%). The inverse of the R-GCN collapse (cosine ≈1.0
  everywhere). `ctx`(0.615) > `bare`(0.448) holds at BGE — the contextualized `:symbol` window
  is what discriminates; the bare symbol underperforms. **So: `:symbol` PASSES O4(c) provided
  the embedded text is the *contextualized* symbol window (qualname+signature+docstring+ns),
  not the bare symbol.**
- **O4(b) conditioning — early read, reframed (untested in JAX):** the line-size disparity
  (115k-line ns vs 1-line `defn`) **largely dissolves at the embedding input**, because the
  window text is *bounded* (signature+docstring+ns context) and the vector is *unit-normalized*
  — both extremes become unit vectors, and the band is a bounded cosine, so node line-count
  never enters `∂s/∂A`. Conditioning more likely **re-enters via node *degree*** (a high-edge
  node contributes many terms to the loss sum and can dominate the gradient). So
  `:conditioning-scale` that matters is plausibly **degree-normalization, not size
  normalization**. Must be confirmed with the actual JAX gradient step.

**Net early verdict:** `:symbol`+norm is on track to PASS both O4 checks ⇒ **IFR holds, no O1
escalation, `:scope`-overlay stays parked.** Open: the JAX `∂s/∂A` step (conditioning), and a
Clojure-slice replication (this slice was Python).

### JAX vertical slice RAN — loop closes; O4(b) nuanced + a self-correction (2026-06-01, claude-6)

The `jax_refine.py` port ran **end-to-end on real code** — the first time the whole loop runs,
not just the analogy. Script: `futon6/scripts/code_diff_jax_pilot.py`; output
`…/code-scope-probe/code-diff-jax-result.json`. Architecture matched O4(a) exactly: embedding
(BGE) done in `futon6/.venv` (PyTorch), the JAX optimization in `futon5/.venv-tpg` (jax 0.9, no
torch) — i.e. **the embedding is literally a constant array outside JAX**, never differentiated.
Slot-for-slot: fixed BGE scope emb (constant) → soft adjacency `A[n,t]` (softmax routing) →
authored cosine band (center 0.60 / width 0.12; **authored, not fit** — gap #3) → `grad(loss)(A)`
→ edit-proposals.

- **Loop closes:** mean band-satisfaction **0.699 → 0.780 (+0.081)** over 600 steps; softmax
  mass concentrates on band-center (cos≈0.60, "coherent-not-duplicate") targets, e.g.
  `symbol_grounding:SymbolBinding → canon_store:aggregate_canon_store` (mass 0.0029→0.0109). The
  band correctly steers toward band-center rather than max-cosine. **Caveat:** edits are
  *soft/directional*, not decisive — full-softmax over 341 targets is diffuse; **hard edits need
  candidate-restriction / sharpening** (next refinement, not done here).

- **O4(b) conditioning — measured, and it CORRECTS my earlier prediction.** I had predicted
  `corr(grad-norm, size) ≈ 0` ("size doesn't enter"). **Measured corr(grad-norm, module-size) =
  +0.647** — *not* ~0. Derivation of why: `∂loss/∂A[n,:]` is ∝ the **variance of node n's
  band-satisfaction across targets**, so gradient scale tracks a **degree-like** structural
  quantity. The honest, corrected statement: **code line-count does NOT enter `∂s/∂A`** (the
  bounded embedding window neutralizes it, as hoped) — but a **degree-like quantity DOES** drive
  gradient scale. So the size→degree *reframe* survives; my specific "corr≈0" claim did not.
  `:conditioning-scale` should therefore be **degree-aware**, not size-aware.
  - Numerically **sane on this slice** (grad-norm max/med = **1.30** — no node swamps).
  - **NOT settled:** the slice is uniform-ish Python (no 115k-line node), so the real
    conditioning extreme O4(b) names is *still untested*. Sane here ≠ sane at the extreme.

**Net (post-JAX):** the mission's central claim — *`jax_refine` ports to the code graph* — is
now **demonstrated on real data**, not analogical. O4(c) PASS (BGE). O4(b): numerically fine on
a uniform slice, degree is the live conditioning axis (size isn't), the worst-case extreme owed.
Ran entirely at the ratified `:symbol`+`:namespace`-context grain → **no O1 escalation**. Next:
(a) hard edits via candidate-restriction; (b) the conditioning extreme + a Clojure/Malli slice.

### STANDARD-VERIFY: DESIGN-PASS across O1–O4 (2026-06-01)

Campaign C-substrate-completion STANDARD-VERIFY assembled by claude-3 (coord owner), keystone
attested by codex-3 (§2.11), **RATIFIED by Joe 2026-06-01**. Verdict: **DESIGN-PASS, fit-for-all** —
the metric contract is verifiable as a design before anyone builds (the Campaign's reason to
exist). E2 column:
- **O1** (E2 clause): select-not-subdivide PASS for all 4 relation-types.
- **O4(a)** differentiable band: design-OK (JAX band over existing nodes / `feeds-A?`).
- **O4(b)** conditioning: codex-3 sets `:conditioning-scale` = **`1/√(max(1, total-A-degree))`**
  (degree-aware; line-count audit-only) — concretely vindicates this mission's size→degree
  reframe.
- **O4(c)** non-degeneracy: **reclassified to required BUILD-WORK.** My BGE probe proved the
  approach discriminates, but the **code-text embedding cache does not exist on disk** (futon6
  BGE = papers; futon3 = missions/patterns; R-GCN rejected). It is a fixed-observation layer to
  build over existing `:symbol`/`:namespace` identities — does **not** reopen O1. **This is the
  main E2 post-SV deliverable.**

**Three items carry to RUN/DELIVER (superpod / memory-gated, none a design failure):**
1. O4(b) full conditioning extreme — empirical confirm (de-risked ~370×, not 115000×).
2. **O4(c) BUILD the code-text embedding cache** — build-work, the primary E2 deliverable.
3. O2-third Fisher–Rao WM shadow — additive (not a contracted obligation).

**Escrow E2 `:held` → `:contract-released` (Joe, 2026-06-01)** — E2 may **build to the verified
spec**. The three carried gates stay open until their runs complete (→ `:satisfied`); **all
heavy runs are venue-held** pending Joe's compute-venue call (claude-3 advising laptop-with-
memory-discipline; the earlier OOM was an orchestration failure, not heavy compute).

### O4(c) build-to-spec design (the primary E2 RUN/DELIVER deliverable) — venue-held for the heavy embed

The deliverable: a **code-text embedding cache** = a fixed-observation layer over *existing*
`:symbol`/`:namespace` identities (no new grain; does not reopen O1). Design (decided parts,
ready to build; the heavy BGE embed is the only venue-gated step):

- **Input:** canonical code trees only — **exclude worktrees / origin / `.state`** (O4 canonical
  rule); reuse the `ast`-based scope-window extractor proven in `code_scope_grain_pilot.py`
  (contextualized `:symbol` window = ns-context + qualname + signature + docstring — the `ctx`
  form that beat `bare`).
- **Embed:** BGE (`bge-large-en-v1.5`, local cache), normalized — *the one heavy/gated step*.
  NOT R-GCN (rejected, cosine-collapse).
- **Cache schema:** `{:node/id → {:vector <BGE>, :total-A-degree <int>, :conditioning-scale
  (= 1/√(max(1, total-A-degree)))}}` — degree-aware per codex-3's §2.11 / the O4(b) reframe;
  line-count audit-only.
- **OPEN scope decision (for Joe / codex-3):** which repos + languages the v1 cache covers —
  Clojure stack only, or +Python(futon6)/+Elisp(futon4)? The extractor is per-language; the
  cache schema is language-agnostic. Flagged, not assumed.

### WM differentiable-shadow (O2-third) RAN (laptop, 2026-06-01): toolchain validated; finding relocates the per-entity sorry

First laptop run authorized by Joe (venue=laptop; `.venv-tpg` already had jax 0.9 — no install).
`futon2/scripts/wm_shadow_pilot.py` on trace step 23 (264 entities).

- **PASS-BAR descent-validity = PASS:** F 28.146 → 27.871 monotone, on-simplex, mass-conserved.
  The EG/Fisher–Rao natural-gradient operator + the whole differentiable-shadow toolchain are
  **validated on real WM data** — Rung-0's primary goal met.
- **Headline OVERTURNED our (claude-6 + claude-3) shared prediction — honestly:** all per-entity
  dispersions = 0.00000 (full / acc-only / kl-only / R3d). **Verified cause:** cross-entity
  dispersion of `μ_pre` = 0.000000 at **all 24 steps** — every one of ~264 entities holds the
  *identical* posterior, always. Two structural causes: (1) all 4 live channels are
  **mean-over-entities** (no per-entity observation signal — verified in `belief.clj`); (2)
  homogeneous prior + aggregate-only evidence ⇒ beliefs stay identical. There is **no per-entity
  structure to recover**, and the VFE flow faithfully reproduces that.
- **Deeper finding (stronger than the hypothesis):** the per-entity gap is **not in the update
  rule (R3d) — it is in the OBSERVATION MODEL.** With aggregate-only channels + homogeneous
  prior, *no* update rule (discrete or differentiable) can differentiate entities.
  `:sorry/r3d-per-entity-attribution` is mis-located: there is no per-entity *signal* to attribute
  from; the fix is **per-entity observation channels**, not a better update. (claude-3's
  semantics call to confirm + fold into M-aif2.)
- Note: `kl-only` decomposition is degenerate by construction (init at μ=μ_pre ⇒ `dKL/dμ`=const
  ⇒ softmax-invariant ⇒ 0 movement). The honest "disagreement is the signal": the differentiable
  shadow's value here was to *prove* (via descent-validity PASS) that the model contains no
  per-entity structure — relocating the gap.
- **Positive control PASSED (`wm_shadow_poscontrol.py`, laptop 2026-06-01):** with a synthetic
  per-entity observation channel, the SAME EG/Fisher–Rao flow gives **valid descent (monotone)
  + per-entity dispersion 0.04–0.24** (vs 0 on the aggregate-only model). So the step-23 null is
  a **FAITHFUL null, not a broken flow** — the differentiable shadow is a validated measurement
  instrument (descent-valid · differentiates under signal · correctly nulls without it). Bonus:
  `full < acc-only` ⇒ **KL-to-a-homogeneous-prior is itself a homogenizing force** (it resists
  per-entity differentiation), reinforcing the observation-model reframe. **Rung-0 closed.**

### O4(c) cache DELIVERED + O4(b) conditioning PASS (laptop, 2026-06-01) — all 3 carried gates green

**O4(c) — code-text embedding cache delivered.** `futon5/data/code-embeddings/code-emb.npy`
**(7534, 1024)** BGE-large over the **living-Clojure stack, futon3 origin excluded** (O1
canonical-trees rule): **7111 symbols + 423 namespaces**. Non-degeneracy on the FULL set: cos
median **0.627**, frac>0.95 **0.0001** (strongly discriminative — not R-GCN-collapsed). 48.8 min,
3.15 GB peak (safe). Coverage metadata written (v1 = Clojure-only; Elisp/Python pending separate
extractors — no silent all-languages read). Tooling: `futon5/tools/embed/{extract_clj_scopes.bb,
build_code_embedding_cache.py}` (chunked + resumable + self-belling).

**O4(b) — conditioning extreme PASS, with a refinement.** Sparse `feeds-A?` graph
(`extract_clj_relations.bb` → `relations.json`: 7084 ownership + 852 file-dependency edges, never
dense). JAX check (`o4b_conditioning_check.py`, `.venv-tpg`) over the file-dependency relation
(the multi-target differentiable choice; ownership is single-target → degree-only):
- **`∂s/∂A` finite across all nodes; NO single node swamps** — per-source grad-norm max/med ≈
  **3.95**. The highest-degree ns (`futon3c.dev`, out-deg 28, **total-A-deg 298**) has grad-norm
  `6.11e-2`, well below the max `3.49e-1`. **The feared gap-#1 10⁴–10⁵× ill-conditioning does NOT
  manifest — real spread is ~4×.** O4(b)'s substantive requirement PASSES.
- **Refinement:** degree-aware `:conditioning-scale = 1/√(total-A-degree)` is **not load-bearing**
  — applying it slightly *worsens* max/med (4.76 vs 3.95), because high-degree nodes weren't
  swamping. Well-conditioning comes from (a) the bounded BGE embedding window (size never enters
  `∂s/∂A` — same root as the WM-shadow line-count finding) + (b) per-source softmax normalization.
  grad-norm tracks band-satisfaction *variance*, not degree. Recommended to codex-3: keep
  `:conditioning-scale` as optional/audit metadata, not applied by default.

**Status: all three carried RUN/DELIVER gates GREEN** (O4(b) PASS · O4(c) cache delivered ·
O2-third Rung-0 closed). Remaining for full Campaign `:satisfied` = the consent-gated LIVE consume
(M-aif2 slice-1 reading live curvature) — claude-3's gate, separate from the metric's own RUN/DELIVER.

### CONSUMER BUILD — edit-proposals on the real delivered metric (2026-06-01) — the mission's actual output

First run of M-differentiable-code's reason-to-exist (gradient/band → edit-proposals) on the
**real delivered O4(c) cache + sparse feeds-A graph** (parallel to M-aif2 slice-1; additive,
read-only, applies nothing → no consent gate). `futon5/tools/embed/build_code_edit_proposals.py`
→ `futon5/data/code-embeddings/edit-proposals.json`. Namespace-grain file-dependency (the
multi-target differentiable choice); 423 ns, 852 existing deps; authored band center 0.60.

- **REVIEW (existing deps, lowest band-sat) — defensible, legible:** every top hit is a
  **near-duplicate cohesive-split pair** (cos > 0.91): `X → X-backend`, `X → X-shapes`,
  `render-cli → render`, `mission-control → mission-control-backend`, etc. The band cleanly
  detects the "module split into impl + backend/shapes" pattern. This IS the INSTANTIATE
  success criterion — *legible disagreement* (extracted band vs drawn wiring).
- **ADD-candidates (non-edges, cos ≈ 0.60) — speculative; confirms gap #2:** some plausible
  (`aif.repl-trace ↔ proof-backend`), some spurious (`tensor-transfer ↔ social.bells`). Raw
  cosine-band alone is a **weak edit-semantics** — "cos 0.6 ⇒ should depend" doesn't survive
  scrutiny. The loop runs + ranks, but **gap #2 (what does the loss MEAN?) is the next real
  work**: the satisfaction needs structure beyond embedding proximity before ADD proposals are
  trustworthy. (Honest: the consumer demonstrates the pipeline; it does not yet justify auto-edits.)

**Net:** consumer loop demonstrated end-to-end on real data; REVIEW (near-duplicate detection)
is the trustworthy first output; ADD relocates the open work to gap #2 (loss semantics). The
proposals are examine-not-apply (gap #2 discipline). This is Rung-1→2 of the ladder; Rung-3
(apply an edit → measured improvement) remains consent-gated + needs the loss-semantics work.

**ESCROW — E2 `:satisfied` candidate (released-AND-consumed):** the artifact carries an explicit
`consumption_evidence` block — E2 *consumes* the delivered metric (code-emb.npy [7534,1024] +
feeds-A 7084 ownership/852 file-dep edges) to produce its output. This is the Campaign
dissolution criterion-2 evidence for E2 (≥1 paired requirement released-AND-consumed). Whether
cache-consumption counts as escrow "consume-live" vs a stricter running-WM read = Joe's
dissolution-authority call (claude-3 surfacing in parallel).

### gap #2 DERIVE step — loss-semantics: continuity-band × structural-context (2026-06-01)

The consumer build showed raw cosine-band is a weak edit-semantics (spurious ADD). DERIVE test
(`gap2_structural_proposals.py` → `gap2-structural-proposals.json`): require ADD-candidates to
have BOTH high band (coherent-not-duplicate) AND **structural support** (shared dependency-
neighbours in feeds-A — an open triad to close). Result:
- **v1 pure-band: 0/12 top ADD proposals had ANY structural support** → cosine-alone is
  pervasively weak (confirmed, not a one-off).
- **v2 band × shared-neighbours: architecturally plausible** — `peripheral.registry ↔
  peripheral.evidence` (14 shared, cos 0.69), `agents.tickle-orchestrate ↔ dev` (10 shared),
  `peripheral.runner ↔ blackboard` (6 shared) — coherent AND structurally adjacent, real
  structural holes, not the `tensor-transfer ↔ social.bells` spurious class.

**Finding:** the loss with real semantics (gap #2) = **continuity-band AND structural context**
— it consumes *both* delivered artifacts (embedding + feeds-A graph) together. This is the
defensible direction the mission's DERIVE was for; it materially sharpens the edit-proposals
over embedding-proximity alone. Still examine-not-apply (an open triad is a *candidate* missing
edge, not a confirmed one); Rung-3 (apply → measured improvement) needs consent + validation that
closing a triad genuinely improves structure. gap #2 is now *grounded* (not closed): the loss
has a real shape, with the structural signal as the key ingredient cosine lacked.

### O2 convergence — curvature in the loss: the ratified decoupling VINDICATED (2026-06-01)

Explored unifying E2's loss with E1 curvature (one-metric-both-cuts). Computed Ollivier–Ricci κ
on the ns-dependency graph under BOTH ground metrics (`o2_curvature_unified.py` → `o2-curvature.json`):
- **`d`=embedding (the "elegant" recoupling I proposed) is DEGENERATE.** Most-negative κ are all
  near-duplicates (`evidence.store → evidence.backend` κ=−1.46, cos 0.93) — an artifact: `d=1−cos≈0.07`
  is a tiny denominator, so `κ=1−W₁/d` blows up. Not a bridge signal.
- **`d`=hop (E1 ratified) gives MEANINGFUL curvature:** negative κ = genuine cross-concern bridges
  (`dev.bootstrap → futon1a.system`, `transport.http → mfuton-mode`, `portfolio.core → evidence.boundary`),
  positive κ = dense clusters. Disambiguation: near-duplicate edges (cos>0.85) κ_hop **+0.146**
  (correctly dense) vs κ_emb **−0.073** (denominator artifact). corr(κ_emb,κ_hop)=+0.53.

**Finding (reverses the hypothesis):** **codex-3's `d_E1`=hop decoupling is empirically CORRECT** —
the embedding is right for the *continuity band* but degenerate as a *curvature ground metric*
(κ's `1−W₁/d` form is unstable at small `d`). "One metric, both cuts" = two appropriate ground
structures, each fit for its cut — not a literal single `d`. So the O2 unification for
M-differentiable-code's loss = **consume both cuts AS DELIVERED** (embedding-band ⊗ hop-curvature),
complementary signals; curvature (hop) disambiguates the band's low-band edges into bridges (KEEP,
load-bearing) vs near-duplicates (cohesive splits). No contract change; recoupling rejected.

### CAPSTONE — unified edit-proposals (band ⊗ structure ⊗ hop-curvature), (a) complete (2026-06-01)

`unified_edit_proposals.py` → `unified-edit-proposals.json`. The full O2 loss, both cuts consumed
as delivered (embedding continuity-band + feeds-A structure + E1 hop-curvature). Curvature makes
the proposals **actionable**:
- **REVIEW split by κ:** **COHESIVE-SPLIT** (κ>0: `tensor-exec→tensor-diagrams`,
  `render-cli→render`, `mentor→mentor-map` — companion-file splits, merge-candidates) vs
  **BRIDGE** (κ<0: `portfolio-inference→…-backend`, `mission-control→…-backend`,
  `evidence.store→evidence.backend` — load-bearing, KEEP). The band alone called all of these
  "low-band"; curvature tells you which to act on.
- **ADD:** gap#2 structural holes (`peripheral.registry↔peripheral.evidence` 14 shared;
  `agents.tickle-orchestrate↔dev` 10 shared) annotated with local curvature.

**Honest caveats (recorded):** (1) the cohesive/bridge κ magnitudes are modest (±~0.1, all within
the near-duplicate-cos class — curvature *refines* within it, doesn't cleanly separate). (2) the
ADD "relieves-bottleneck" flag barely discriminates because the futon3c core is near-uniformly
negative-curvature — shared-neighbours + band carry the ADD ranking there.

**(a) complete.** The unified consumer loss is demonstrated end-to-end on real data, consuming both
campaign cuts. Ladder: Rung 0 (WM shadow) · Rung 1 (cache+conditioning) · Rung 2 (consumer) ·
gap#2 grounded · **(a) O2 unification** — all ✓. Rung 3 (apply→measured-improvement, the noteworthy
endpoint) remains: consent-gated + needs validation that acting on a proposal genuinely improves
structure (the curvature-magnitude modesty above is the honest reason that validation is non-trivial).

### BASELINE ABLATION — the apparatus largely reduces to baselines; gap #2 stop-condition fires (2026-06-01)

Before any consent-to-apply, tested whether the differentiable/embedding/curvature apparatus
beats trivial baselines (`ablation_baselines.py` → `ablation-baselines.json`). **It mostly does not:**
- **REVIEW: 20/20 lowest-band edges are STRING-COMPANION pairs** (shared ns-path / prefix:
  `X→X-backend/shapes/cli`). The embedding adds NOTHING — REVIEW re-derives a naming convention.
- **ADD: 11/20 overlap with pure common-neighbours** (graph-only link prediction); **0/20 lexical.**
  The graph topology is the ADD workhorse; the embedding reorders ~45% but is **unvalidated**
  (different ≠ better — no ground truth that its picks are good edits).

**Verdict (answer to Joe's forward-or-back):** **GO BACK / RETHINK** — this is the mission's own
gap #2 stop-condition (*"if no loss with real semantics beats a graph query, JAX is decoration"*).
Assumptions to revisit before investing further: (1) the embedding band's *structural* value is
unproven over baselines (REVIEW=string-match, ADD=common-neighbours); (2) curvature's magnitudes
too modest to drive edits; (3) no validation signal distinguishes a good edit from a different one.
**Consenting to apply now would mutate the live codebase on string-matching + link-prediction in
JAX clothing.** NOT warranted.

**What stands (baseline-independent, genuinely valuable):** Rung-0 WM observation-model finding;
the validated differentiable instrument; the reusable embedding cache. And the ablation *itself* is
a high-value result — it prevented a consent-gated apply of an unearned signal.

**Path to actually justify the apparatus (the real next work, not an apply):** a ground-truth eval
— do embedding/curvature-driven proposals match architect-endorsed edits *better* than
common-neighbours + string-match? Until that's shown, the differentiable layer is unjustified for
edit-proposals. (Rung-3 apply is gated behind this, not just behind consent.)

### SMOKE-TEST REFRAME + next-session levers (2026-06-01 EOD, Joe)

Reframe of the ablation (corrects the "stop-condition" gloss): the negative finding is a
**successful smoke test** — *"can we build embeddings over real code and get a meaningful signal
back?"* → **resounding yes** (pipeline runs end-to-end; embeddings discriminative, cos median
0.63; outputs coherent). Reducing to keyword/graph baselines is the **EXPECTED null for the
SYMBOL arm** (symbol text is name-dominated), not a failure of differentiable structure learning.
The smoke test deliberately used the cheap representation + discarded the time dimension; both are
levers, not verdicts.

**The hypothesis was never fairly tested — two untried levers (come back to these):**
1. **What to embed** (we used symbols = name-dominated → keyword reduction was obvious). Untried
   arms that could carry real *structural* signal: **specs** (wiring-claims/contracts = intended
   structure) and **scopes** (learned binding/usage/dataflow context, updated as we go). Symbols
   ≠ scopes ≠ specs — the IFR "no `:scope` grain" ruling was the load-bearing wrong turn.
2. **The time dimension** (discarded for the static smoke test): the **git/XTDB evolutionary
   model** — 808 commits in futon3c give a dated, edge-level record of *what actually happened*
   (`git log --follow -p` recovers when each `:require` edge appeared). Ground truth = predict the
   structural evolution; reconstructable from Git if substrate-2's record is thin.

3. **ONE shared space over heterogeneous stack entities (the real ceiling, Joe EOD).** The
   machinery is **type-agnostic** — code symbols were the hardest/lowest-signal input (names ≈
   keywords). Embed **patterns, missions, turns, specs, code all in one space** and learn the
   *cross-type* structure: **pattern→mission**, **turn→pattern**, **spec→code**. Assets already on
   disk: `futon3/resources/embeddings/minilm_pattern_embeddings.json` (patterns embedded);
   futon3a missions corpus + MiniLM retriever (missions embedded); **hundreds of pattern-tagged
   turns with an increasingly EVEN tag distribution** (informative labels, not collapsed). These
   entities carry rich descriptive text → real signal where symbols had none. The futon self-
   improvement loop (recommend "this turn matches pattern X" / "this mission should fire pattern
   Y") is arguably MORE useful than code→code edit-proposals, and higher-signal.

**META (Joe EOD): today = "can we turn it on and off?" → YES.** The differentiable-embedding-→-
signal switch is built + validated end-to-end. The negative code→code result just located the
floor; the levers above are where the signal is. Mission de-risked from "does the apparatus
work" to "which inputs give USEFUL recommendations."

**Tomorrow's plan:** pull these levers — embed **specs / scopes / patterns / missions / turns**
(not bare symbols), in a shared space, with the time dimension; and
bring back the **time dimension** (forecast real edge-additions vs preferential-attachment /
triadic baselines). Staged: Stage-1 = does ANY current signal forecast real git edge-additions
above temporal-graph baselines (tests the target fix on the cheap representation); Stage-2 = build
the **learned-scope** representation trained to forecast the evolution (the exact, non-decorative
Friston structure-learning). Tooling all in `futon5/tools/embed/`; cache + artifacts on disk.

### EXPERIMENT #3 ran (02 Jun) — pipeline validated; underpowered; the graph grows by NODE-ARRIVAL

Built the shared graph-agnostic forecasting harness (`forecast_harness.py`, toy-validated — caught
+ fixed a tie-break artifact) + #3 leak-free, as-of-T (`git_dep_graph_asof.bb` + `git_callsig_asof.bb`
via `git show`, NO checkout — live repo untouched; `run_exp3.py`). futon3c split: snapshot @HEAD~250
(2026-03-10) = 94 nodes/278 edges; HEAD = 170/458; **184 future edges added.**
- **UNDERPOWERED: only 5 forecastable positives** (future edges between nodes that existed @T). The
  other 179 involve NEW namespaces (+76 nodes). So AP numbers (call-sig 0.05 / name-token 0.38 /
  adamic 0.29) are NOISE at n=5 — no structure-vs-names conclusion.
- **Real finding: futon3c grows by NODE-ARRIVAL, not edge-densification** (~97% of new edges involve a
  brand-new namespace wiring in). Classic link-prediction-among-existing is the wrong frame.
- **Methodological reframe (affects #5 too):** the forecast target should be **cold-start node-arrival
  wiring** — *when a new entity appears, where does it wire in?* — scored from its as-of-arrival signal
  (call-signature for E2, curvature for E1). Harness unchanged; only candidate/positive construction
  changes. Flagged to claude-3 to check #5's growth mode (same wall if missions grow by arrival).
- Pipeline (harness + temporal extraction + leak-free as-of-T signals) is validated and reusable.

### CHECKPOINT — TensorType review clarifies why typed shape works where our first code-graph probes struggled (2026-06-05, codex-4)

Joe noticed that Bruno Gavranovic's
[`TensorType`](https://github.com/bgavran/TensorType) is adjacent to this mission's
"differentiable structure" question. Codex pulled it locally at
`external/TensorType` (`b85946f0dd2ee4989b8900b6bbadf6c14340550b`) and reviewed
the code against this mission's failure modes.

**Finding:** TensorType "works" because it starts from a **typed shape/index
calculus**, not because it has solved arbitrary differentiable code graphs.
Its core stack is:
- `Cont`: a container with a type of shapes and positions-per-shape.
- `Ext c x`: a concrete container instance, `shapeExt : c.Shp` plus
  `index : c.Pos shapeExt -> x`.
- `Tensor shape a`: a wrapper around `Ext (Cont.Tensor (conts shape)) a`.
- `TensorShape`: a vector of named axes guarded by consistency proofs.

The important contrast: TensorType makes the legal positions first-class before
numeric operations run. An axis is `name + container`; repeated names are
allowed only when they refer to the same underlying container. A non-rectangular
operation, such as zipping two binary-tree tensors, is not inferred from an
untyped graph after the fact; it is provided as explicit container structure
(`pairBTreeShapes`, `pairBTreePos`, then `TensorMonoid BinTree`). That is the
precondition our early code-graph probes lacked.

**Caveat:** TensorType is early-stage, and the review did **not** establish it as
a mature AD system. The repo currently contains many Idris holes and
`believe_me` uses; `src/Data/Autodiff/README.md` says "Stuff here doesn't work
yet", and the forward-mode notes document failed typeclass search for
differentiability evidence. `pack` / `idris2` were not installed on this machine,
so no local build was run. Treat the stable lesson as the typed container/tensor
surface, not the unfinished AD/training layer.

**Mission import:** this supports the MAP/DERIVE conclusion already emerging
here: define the typed index of the hard structural choice **before**
differentiating. A future Futon slice should not embed arbitrary extracted graph
nodes and ask JAX to discover semantics. It should first name the admissible
choice space, e.g.

`Node × RelationType × CandidateBoundary -> soft assignment`

Joe follow-up (2026-06-05): read `CandidateBoundary` more generally as a
**substrate-2 scope**, not only as a substrate-1 / Clojure-code boundary. That
is the interesting reframing. Substrate-1 code remains evidence (namespaces,
vars, requires, call-sites, files, tests), but the thing being learned can live
one level up: typed candidate membership in substrate-2 scopes that cut across
code, missions, claims, turns, patterns, diagrams, docs, and rendered views.

Simple WebAraxana-facing examples:
- `diagram -> edges`: a **cone/fiber scope** —
  `Scope(Diagram d, Relation :has-edge) = { e | edge-of(e, d) }`.
- `all diagrams and all their edges`: a **family scope** —
  `Scope(EntityType :diagram, Relation :has-edge) = { (d, e) | diagram(d) and edge-of(e, d) }`.

These are scopes, not yet constructions. "Compute the pushout of all diagrams"
would consume such a scope, but goes beyond the scope concept itself. Useful
substrate-2 scope kinds to consider:
- **Diagram cone:** for a diagram, return nodes, edges, labels, source claims,
  and rendered artifact.
- **Claim-support cone:** for a claim, return code/doc/test/mission evidence
  supporting or contradicting it.
- **Mission scope:** for a mission, return a typed subgraph/hypergraph whose
  nodes may include claims, artifacts, commits, bells, owners, dependencies, and
  associated wiring diagrams; preserve the edges/relations among them rather
  than flattening them into a bag.
- **Boundary scope:** for a named boundary, return members plus crossing edges.
- **Pattern scope:** for a pattern, return missions/turns/code/docs tagged with
  or predicted to instantiate it.
- **Turn scope:** for a conversation turn, return claims made, files touched,
  missions referenced, and follow-up obligations.
- **Ownership scope:** for an entity, return candidate owners/boundaries and
  evidence for each.
- **Temporal scope:** for a time/window/commit range, return substrate-2 changes
  that appeared or disappeared.
- **Conflict scope:** for an invariant, return entities currently pressuring or
  violating it.
- **Construction scope:** for a diagram family, return the typed data required
  to attempt a pushout/pullback/etc., without performing the construction.

NB (Joe): early experiments may already have associated missions to wiring
diagrams directly. `futon3c` has first-class per-mission wiring diagrams
(`holes/missions/*-wiring.edn`) and API/peripheral surfaces such as
`GET /api/alpha/missions/:id/wiring` / `:mission-wiring`. The scope lesson is
not merely "retrieve the mission's contents"; it is "retrieve the mission's
local network/hypergraph", including any diagram and its internal edges as
typed substrate-2 structure.

Further Joe refinement: a mission is itself somewhat `let`-like, but at
substrate-2 rather than lexical-code level. A mission introduces terms,
desiderata, invariants, named problems, and local vocabulary; then its mapping
work relates those names to existing substrate concepts; then implementation
binds them to concrete code, tests, diagrams, hyperedges, and evidence. In that
sense:

```text
mission M introduces:
  terms / desiderata / invariants / named problems

mapping phase:
  term_i -> existing substrate concepts
  term_i -> candidate code symbols/files/patterns

implementation phase:
  term_i := concrete construction in code
  obligation_j := test / invariant / evidence edge
```

So the code-domain analogue of `Let X be a group` need not be a Clojure `let`.
It may be: "Let `M` be the mission `differentiable-code`; within `M`, let
`scope` mean a substrate-2 boundary candidate; bind `scope` to namespaces, vars,
patterns, missions, turns, diagrams, and claims; instantiate those bindings by
typed edges/hyperedges/tests in the actual substrate." This suggests a
`mission/bind` family of scope edges:

- `introduces`: mission -> local term / problem / invariant.
- `defines`: local term -> textual definition / intended role.
- `maps-to`: local term -> existing substrate concept or candidate entity.
- `instantiates`: local term -> concrete code artifact / test / diagram /
  hyperedge.
- `constrains`: mission or term -> invariant / admissibility rule.
- `evidenced-by`: binding or claim -> artifact.
- `invalidated-by`: binding or claim -> contradiction / failing invariant.

The important difference from a bag-of-mission-contents view is that these are
typed binding edges. A mission becomes a scope-producing hypergraph: a local
conceptual vocabulary plus obligations, progressively bound to substrate-2
entities.

NB (Joe): compare `futon6/holes/missions/M-differentiable-math.md`. That mission
is already mining real scopes from ArXiv/math text, so it should eventually
yield evidentially grounded substrate-2 scope examples once the NLP mining
results are strong enough. There is also a noncontentious seed class: local
definition/binder scopes such as "Let `X` be a group". In scope terms, the text
introduces a binder, a typed entity, and a local validity region; the mined
artifact can therefore provide concrete examples of
`Entity × ScopeKind × CandidateScope -> weight` without starting from code
topology.

Concrete pointer (codex-4 exploration, 2026-06-05): the current arXiv
paper-hypergraph run already has this seed corpus at
`storage/arxiv-paper-hg-gpu/scopes.json`. It was produced by Stage 5
`run_stage5_ner_scopes` in `futon6/scripts/superpod-job.py` using
`nlab-wiring.detect_scopes`. The artifact has 9,916 arXiv entities, 5,569 scope
records, 1,512 entities with scopes, and 664 `bind/let` records. The records are
already substrate-2-shaped hyperedge records (`hx/type`, `hx/ends`,
`hx/content`, `hx/labels`); a representative `bind/let` record is:

`Let $F$ be a nontrivial endofunctor on the category of sets that weakly preserves pullbacks...`

with ends:
- `entity = arxiv-math/0105256`
- `symbol = F`
- `type = a nontrivial endofunctor on the category of sets that weakly preserves pullbacks`

Transfer hypothesis (Joe): these innocuous math scopes may be portable into the
code/substrate-2 domain. The code-side target should **not** be limited to
literal code annotations such as `(let [F nontrivial-endofunctor] ...)`, nor only
to `let`-like scopes. Use the mined math top-ten as seed families and ask whether
substrate-2 has analogues for each:

1. `quant/universal` — "for all / every / any" applicability.
2. `bind/let` — local introduction of a typed entity.
3. `constrain/where` — post-hoc qualifier/binding.
4. `constrain/such-that` — property-restricted entity.
5. `assume/explicit` — local assumption/hypothesis.
6. `bind/integral` — operator-bound variable region.
7. `assume/consider` — chosen object / working context.
8. `bind/product` — indexed product binding.
9. `bind/big-union` — indexed union binding.
10. `bind/big-intersection` — indexed intersection binding.

For code, these may correspond to introductions of local entities, roles,
boundaries, assumptions, validity regions, quantified applicability, or indexed
families in substrate-2. Some may not exist; that would be a legitimate negative
result. The search target is the substrate-2 scope concept, not only the
substrate-1 syntactic form.

The resulting differentiable object becomes:

`Entity × ScopeKind × CandidateScope -> weight`

Examples:
- `edge e × :belongs-to-diagram × diagram d -> 0.97`
- `claim c × :supported-by-boundary × boundary b -> 0.72`

with explicit consistency/admissibility constraints, and only then relax hard
assignment to a differentiable tensor. TensorType's useful lesson is therefore:
**shape discipline first, gradient second**. Our first probes struggled because
their "positions" were extracted facts plus vector geometry, so size/granularity
leaked into the gradient. This matches the local `code-diff-jax-result.json`
warning: satisfaction improved (`0.699 -> 0.780`), but gradient norm correlated
with module size (`0.647`), i.e. a representation artifact remained in the
signal.

### MAP exit still open

MAP is **not complete**. Before DERIVE, the mission still needs:

- ~~**(item zero) the one-claim band-translation check**~~ **DONE 2026-05-31**
  (see "Item zero result" above): ~11/12 claims (7 wiring + 5 mission) are
  boolean+structural, not a viable band-source as-is; DERIVE must manufacture a
  predicate→band relaxation layer (or coverage-ratio proxies),
- a line-by-line contract extraction from `jax_refine.py`,
- a small graph inventory for the chosen Pilot A slice,
- a concrete futon4 slice choice (`arxana-browser`, docbook, or VSATARCS writer
  path),
- a Pilot C benchmark list derived from `M-aif2` with path/line evidence,
- a canonical exclusion policy for all extractors,
- a first draft of the common EDN graph schema.

## Multi-agent views (2026-05-31)

Joe asked for an early critique — before MAP closes — so two agents
(claude-2, codex-2) could steer the structure from the start. Exchanged via
Agency bell. Recorded here so the divergences sit on disk, not only in
bell-traffic (same epistemics as combining-methods-as-diagnostic: the
disagreement is the signal).

### The crux: name the discrete choice this mission relaxes

The reusable shape of `jax_refine` (Q1) is *differentiable relaxation of a
discrete choice*. In TPG that choice is argmax routing → softmax. The mission
had not yet named the code-graph analogue. Two answers:

- **claude-2:** "does edge `e` exist / which target does node `n` depend on"
  → soft adjacency (a node×node matrix).
- **codex-2 (sharper, adopted):** the hard choice is **typed structural
  assignment**, not raw edge existence. For node `n` under relation-type `r` /
  responsibility `c`: *which target node or boundary should `n` connect to?*
  Relaxation: hard typed adjacency → **soft adjacency tensor `A[n, r, target]`**
  → optimise pressure against specified claim bands → emit ranked hard edit
  proposals.

Why codex-2's wording wins: for Pilot B (docs) and Pilot C (AIF) the live
question is usually not "does dependency edge X exist?" but "**which
implementation surface owns this claim / action-class / preference
boundary?**" — i.e. ownership of a boundary, not presence of a dependency.
The flat-adjacency framing under-describes exactly the two pilots meant to
stress the method. **DERIVE's first question is therefore: what hard
structural choice is being relaxed, and over what typed index?**

### Five structural revisions (both agents accept all five)

1. **Promote loss semantics (gap #2) to the spine.** MAP currently lets pilot
   logistics carry too much weight; the first DERIVE question must be the
   discrete-choice question above, in codex-2's broader (typed) wording.
2. **Add a 4th required per-pilot artifact: "discrete choice + relaxation for
   this slice."** The A/B/C pilots vary language/subsystem but share the real
   risk axis (the loss). Without this artifact Pilot A can succeed merely by
   inheriting TPG's already-solved relaxation and tell us too little.
3. **Merge gap #1 (granularity) with the loss in DERIVE — do not sequence
   them.** The array encoding is *where the discrete choice lives*; the relaxed
   choice (file-level dependency vs function-level ownership vs claim-to-code
   alignment vs boundary membership) determines the node schema. (codex-2:
   accept strongly.)
4. **Bands specified, never fit.** Even the explicit-satisfaction objective
   degrades into "embedding by another name" if bands are learned. *Caveat
   (codex-2):* empirical calibration may inform diagnostics, but the contract
   bands used to **judge success** must remain authored / spec-derived.
5. **Cheapest falsification first.** Move "can one real `wiring-claims.edn`
   claim become a band without manufacturing?" to **item zero**, before pilot
   selection. If it fails, the mission has learned something important before
   any extractor is built.

### Disposition

Both agents agree the doc should change accordingly: promote "discrete hard
choice → differentiable relaxation" to the MAP/DERIVE spine, add it as a
required per-pilot artifact, and move the one-claim band-translation check
ahead of pilot selection. Folding these into the MAP-exit list and DERIVE plan
is the next editing pass (held for Joe's go-ahead).

## Live mission-as-wiring-diagram in WebArxana — design note (Joe + claude-6, 2026-06-06)

*Next WebArxana target after E-interest-mining closes. Joe: "the way Rob uses missions, they go right into
WebArxana … he does Hebbian / anti-Hebbian learning to see how missions relate to a broader landscape when
specified." Our framing (with codex-4): **a mission is like a Lisp `let`/`lambda`** — it BINDS to stack
concepts, may CONSTRUCT new concepts, and EMITS new code. So each mission is viewable as a futon5 wiring
diagram — and the cool part is to render it **live as the mission is worked.***

**This IS the priors→posteriors framing (M-webarxana-as-monitor):** the hand-drawn futon5 wiring diagram is
the *prior*; the extracted running graph is the enriched *posterior*. The feature makes the posterior live.

**Node-kinds (the let/lambda structure):** `:mission` (the lambda root) · `:bound-concept` (let-bindings —
existing stack concepts/patterns it binds) · `:constructed-concept` (new concepts) · `:emitted-code`
(files/vars produced). Edges: `binds` / `constructs` / `emits`.

**~70% is reuse** (the constellation renderer we just built): `webarxana/client/graph.cljs` (typed node-kinds,
magnitude, kind-filter, pan/zoom, A/B/C/D view-mode scaffold) renders this directly — add the 4 node-kinds + a
**dataflow/lambda view-mode** (bindings left → constructs middle → emits right). `interest_network.clj` is the
template for a `mission_projection.clj`. Live signals already exist: the `multi_watcher` (5s file ingestion),
substrate-2 `:pattern-application` edges, and the M-INC checkpoint event vocabulary.

**Genuinely new / decisions:** (1) **source of bind/construct/emit** — posterior-extraction (substrate-2
`:mission/mentions-file` + `:pattern-application`, currently thin) vs **live-from-work** (the pilot LOOP's
`loop_learning` already records patterns-applied + sorries-mined per frame → the diagram *accretes as the
mission is worked* = Joe's "live"). (2) **Hebbian/anti-Hebbian is absent from the codebase** (Rob's, not
ported) → a *later* co-activation layer over the extracted graph, not the first cut.

**Proposed first slice + reflexive trick:** prove it on **E-interest-mining itself** — this session bound
(interest-network, colimit, scribe), constructed (the `career-coherence/free-solo-vs-rope` pattern, the
constellation), and emitted code (graph.cljs, auth.clj, the seed `.el`s) — real, known bind/construct/emit, a
self-documenting test case. Phase A = node-kinds + `mission_projection` for one mission (authored-seed →
rendered, fastest path to a real diagram). Phase B = wire to live work-events. Phase C = Hebbian co-activation
across missions. Full grounding map (file:line) from the 2026-06-06 scoping pass in claude-6's session notes.

## Relations

- M-bayesian-structure-learning — this is its differentiable realisation.
- M-substrate-2 / M-live-geometric-stack — supplies the (T, ∇, Δ, drift) the
  heuristic ∇ here could become a real gradient for.
- M-webarxana-as-monitor — priors (drawn) vs posteriors (extracted) over wiring.
- M-coupling-as-constraint (futon5) — same jax_refine "constraint satisfaction
  is differentiable" move, applied to CA coupling rather than code structure;
  closest methodological sibling.
- M-aif2 (futon2) — Pilot C benchmark target; differentiable structural
  pressure should rediscover or sharpen its AIF support-extension findings
  before any AIF refactor is trusted.
- Arxana / VSATARCS (futon4) — Pilot B comparator; candidate realisation of
  differentiable documentation over code, docs, claims, tests, and writer
  workflows.
- External: Rob / mfuton — ≤800-line modules + graph DB; convergent, and the
  source of the "enforce a size limit by refactor" path for gap #1.
