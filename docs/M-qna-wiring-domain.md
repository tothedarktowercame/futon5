# Mission: Exercise futon5 Wiring Diagrams on Q&A Thread Domain

Use locally available Math.StackExchange / MathOverflow thread wirings from
futon6 as a concrete domain for futon5 wiring analysis, control, and
evaluation.

## Status

Proposed.

## Owner

futon5 + futon6 bridge effort.

## Why this mission

We already have:

- Thread datasets: `futon6/data/stackexchange-samples/*.jsonl`
- Thread-level wiring outputs: `futon6/data/thread-wiring/*.json`
- Discourse/performative structure (`assert`, `challenge`, `clarify`, ...)
  and port/categorical annotations in thread wiring nodes/edges.

futon5 already has:

- Executable wiring runtime (`futon5.wiring.runtime`)
- Wiring composition/embedding/feature extraction
- TPG routing with support for wiring operators

The missing piece is a clean exercise loop that treats Q&A structures as
first-class wiring artifacts in futon5.

## Problem statement

Current futon5 wiring execution expects cellular-context components
(`context-pred`, `context-self`, `context-succ`) and generator-registry
components. futon6 thread wirings are discourse graphs, not executable CA
wirings. They cannot be run directly in futon5 runtime.

We need a two-stage bridge:

1. **Structural bridge**: treat thread wirings as analyzable wiring objects
   (without forcing CA execution).
2. **Executable projection**: compile selected thread-wiring motifs into
   futon5-executable wiring operators for controlled experiments.

## Hypothesis

Q&A-derived wiring motifs can improve controller interpretability and regime
selection behavior when integrated as explicit wiring operators in TPG,
provided we keep a strict separation between:

- semantic discourse graph data (source of motifs),
- executable wiring diagrams (runtime artifacts),
- evaluation metrics (task quality + structural validity).

## Work packages

### WP1: Data contract for thread wiring ingest

Define and freeze a local contract for consumed fields from:

- `futon6/data/thread-wiring/*.json`

Required fields for v1:

- thread id/site/topic metadata
- node ids/types
- edge source/target/type (`assert/challenge/...`)
- optional ports and categorical tags

Deliverable:

- `docs/qna-wiring-contract-v1.md` (or equivalent section in this mission)

Acceptance:

- Contract validates all current sample files.
- Any missing/optional field handling is explicit.

### WP2: Structural exerciser in futon5 (no execution yet)

Add a script to load thread wirings and compute:

- graph size/depth/branching
- performative distribution
- motif signatures (reusable subgraphs)
- compatibility with futon5 wiring embedding features where possible

Deliverable:

- `scripts/qna_wiring_exercise.clj` summary report under `reports/`.

Acceptance:

- Runs deterministically on local sample bundle.
- Produces per-site/per-topic summary tables.

### WP3: Executable projection (semantic -> runtime wiring)

Define a minimal projection from performative motifs to executable
futon5 components (v1 mapping table).

Example direction (placeholder, to be validated):

- `challenge` -> gate-like suppress/flip behavior
- `clarify` -> conservative/identity-like behavior
- `assert` -> commit/progress behavior
- `reference` -> retrieval/conditional branch behavior

Deliverable:

- projection spec + one compiler script generating EDN wirings in
  futon5 runtime format.

Acceptance:

- Generated EDN passes futon5 wiring loading and runtime smoke tests.
- Projection is reversible at trace level (can attribute runtime operator
  choice back to source thread motif).

### WP4: TPG integration with Q&A-derived wiring operators

Inject projected wirings as `:wiring-operators` in `futon5.tpg.runner`.

Deliverable:

- reproducible run comparing:
  1) baseline exotype operators only
  2) exotype + existing handcrafted wirings
  3) exotype + Q&A-derived wirings

Acceptance:

- same evaluation budget + seed control.
- routing trace includes Q&A operator usage frequencies and paths.

### WP5: Domain-grounded evaluation

Evaluate with three complementary checks:

1. **Synthetic control tasks** (known expected routing behavior)
2. **Computational checks** (ground-truth executable tests)
3. **Human-in-the-loop audits** (interpretability and relevance)

Deliverables:

- one report connecting extraction choices -> wiring motifs -> controller
  behavior -> task outcomes.

Acceptance:

- Failure attribution is explicit:
  - extraction issue,
  - projection issue,
  - controller issue,
  - metric issue.

## Success criteria

- [ ] Thread wiring ingest contract v1 established and validated.
- [ ] Structural exerciser produces stable reports on local SE/MO samples.
- [ ] At least one projected Q&A wiring runs in futon5 runtime.
- [ ] TPG run with Q&A-derived operators completes with deterministic traces.
- [ ] Comparative report exists with clear wins/losses and attribution.

## Scope out (for this mission)

- End-to-end autonomous Q&A agent productization.
- Replacing futon5 physics/controller stack with discourse-only stack.
- Large distributed training and infra work.

## Immediate first step

Pick one small slice for v1:

- `math.stackexchange.com__category-theory`
- top 10 threads by score

Run WP1+WP2 only, publish summary, then decide projection mapping for WP3.

## Connects to

- `futon6/data/thread-wiring/*.json`
- `futon6/data/stackexchange-samples/*.jsonl`
- `futon6/scripts/assemble-wiring.py`
- `futon5/src/futon5/wiring/runtime.clj`
- `futon5/src/futon5/tpg/runner.clj`
- `futon5/docs/M-tpg-improvements.md`
