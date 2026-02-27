# futon5 technical note

Date: 2026-02-21
Scope: current repository state in `/home/joe/code/futon5`

## 1) What futon5 is

`futon5` is the meta-evolution layer in the Futon stack.

At a technical level it is a control-and-evaluation framework around cellular automata over a 256-sigil alphabet. It combines:

- CA/MMCA execution engines (`src/futon5/mmca/runtime.clj`, `src/futon5/ca/core.clj`)
- Exotype dynamics (local physics from context to rule/kernel) (`src/futon5/mmca/exotype.clj`)
- Xenotype evaluators and evolution loops (`src/futon5/mmca/xenotype.clj`, `src/futon5/mmca/xenoevolve.clj`)
- Meta-meta evolution over rule/kernel/controller policies (`src/futon5/mmca/metaevolve.clj`)
- Wiring-diagram execution and validation (`src/futon5/wiring/runtime.clj`, `src/futon5/xenotype/*.clj`)
- TPG controller stack with verifier-guided evolution (`src/futon5/tpg/*.clj`)
- New tensor execution path and CT executable diagrams (`src/futon5/ct/tensor*.clj`)
- Transfer bridges toward cyber-ants and AIF-style summaries (`src/futon5/ct/tensor_transfer.clj`, `src/futon5/cyber_ants.clj`)

Conceptually: futon5 studies selection pressure on pattern dynamics, not just one-off CA behavior.

## 2) How it works

### 2.1 Core data model

The main runtime state uses:

- `genotype`: sigil row string
- `phenotype`: optional bit row string
- `history`: genotype/phenotype histories over generations
- per-generation `metrics-history`
- operator meta-state and proposal traces

This is visible in `run-mmca` and `run-mmca-stream` in `src/futon5/mmca/runtime.clj`.

### 2.2 Runtime engines

`run-mmca` supports two engines:

- `:mmca` (default): full MMCA pipeline with operators/exotype logic
- `:tensor`: tensor-diagram execution path

Engine normalization and dispatch are in `src/futon5/mmca/runtime.clj`.

### 2.3 MMCA engine pipeline

For `:mmca`, each tick is roughly:

- run operator hook stages (`:observe`, `:decide`, `:act`)
- apply optional lesion/gating effects
- apply exotype mode updates
- advance world via CA evolution
- refresh metrics

Operator hooks come from lifted pattern catalog/functor paths (`src/futon5/patterns/catalog.clj`, `src/futon5/mmca/functor.clj`, `src/futon5/mmca/operators.clj`).

### 2.4 Exotype and local physics

`src/futon5/mmca/exotype.clj` contains the core mapping:

- local context (36-bit structure) -> rule in 0..255
- rule -> kernel spec
- per-cell local evolution (`evolve-string-local`)
- optional global-rule bending of local physics (`evolve-with-global-exotype`)

The recommended production path is `:exotype-mode :local-physics`.

### 2.5 Metrics and analysis

Run summarization and diagnostics are in `src/futon5/mmca/metrics.clj`, with extra analysis modules including:

- domain/particle/info-dynamics (`src/futon5/mmca/domain_analysis.clj`, `src/futon5/mmca/particle_analysis.clj`, `src/futon5/mmca/info_dynamics.clj`)
- bitplane coupling and Wolfram class estimation (`src/futon5/mmca/bitplane_analysis.clj`, `src/futon5/mmca/wolfram_class.clj`)

### 2.6 Wiring-diagram path

There is a separate typed wiring runtime (`src/futon5/wiring/runtime.clj`) and xenotype wiring stack:

- component library loading/validation (`src/futon5/xenotype/wiring.clj`)
- diagram interpretation (`src/futon5/xenotype/interpret.clj`)
- scorer component registry (`src/futon5/xenotype/scorer.clj`)
- category-law checks (`src/futon5/xenotype/category.clj`)

This path is used directly and also by TPG wiring operators.

### 2.7 TPG stack (controller layer)

TPG implementation is in `src/futon5/tpg/*`:

- graph/program routing and validation (`core.clj`)
- verifier evaluation + Pareto ranking (`verifiers.clj`)
- evolutionary search over TPG structures (`evolve.clj`)
- MMCA runner integration with per-generation routing (`runner.clj`)

TPG can route either to exotype-aligned operators or to wiring operators loaded from files.

### 2.8 SMT and JAX integration (initial)

`src/futon5/tpg/compare.clj` shells out to Python tools:

- SMT static analysis (`tools/tpg/smt_analyzer.py`)
- JAX weight refinement (`tools/tpg/jax_refine.py`)

Interpreter resolution and setup are handled via `FUTON5_TPG_PYTHON`, `.venv-tpg`, or `python3` (`scripts/setup_tpg_python.sh`, `requirements-tpg.txt`).

This integration is operational but currently experimental/orchestration-level.

### 2.9 Tensor stack (new)

Tensor modules in `src/futon5/ct/` now provide:

- tensor transforms and bitplane stepping (`tensor.clj`)
- executable diagram interpreter + primitive registry (`tensor_exec.clj`)
- reusable tensor diagram library (`tensor_diagrams.clj`)
- MMCA-compatible metric wrapping (`tensor_mmca.clj`)
- transfer pack primitives for top-sigils/cyber-ant/AIF (`tensor_transfer.clj`)
- deterministic parity benchmark + report payloads (`tensor_benchmark.clj`)

Runtime/CLI integration is present:

- `--engine :tensor` in MMCA CLI (`src/futon5/mmca/cli.clj`)
- tensor engine support in render CLI (`src/futon5/mmca/render_cli.clj`)
- tensor stream support in `run-mmca-stream` (`src/futon5/mmca/runtime.clj`)

### 2.10 Transfer to ants and AIF

Current transfer surfaces include:

- tensor transfer pack output containing `:top-sigils`, `:cyber-ant`, `:aif`
- cyber-ant proposal construction (`src/futon5/cyber_ants.clj`)
- adapter pipeline from exotype/CT interpretation to cyberant config (`src/futon5/adapters/cyberant.clj`)
- post-run ants benchmark bridge (`src/futon5/mmca/ants_benchmark.clj`)

## 3) Current validated state

Current checks run in this repo state:

- `clj -M -m futon5.healthcheck`: PASS
- test suite: 86 tests, 300 assertions, 0 failures/errors
- strict invariants (`bb -cp src:resources:test -m futon5.mmca.verify-invariants --strict`): PASS for invariants 1-8

Observed note:

- one warning about `pod-eigs` timeout fallback appeared during invariant checks, but checks still passed.

## 4) Remaining gaps

### P0 gaps (high impact)

- Tensor engine is intentionally narrower than MMCA engine.
- `src/futon5/mmca/runtime.clj` rejects many MMCA options for `:engine :tensor` (kernel/exotype/operator controls, lesion, etc.).
- Result: tensor execution is integrated, but not yet feature-parity with full MMCA control surfaces.

- Pattern lift catalog is only partially concretized.
- `src/futon5/patterns/catalog.clj` maps a small role set to concrete builders; other roles still produce `:impl :pending` stub hooks.
- Result: significant portions of pattern space remain declarative/stub-level.

- MMCA runtime and wiring runtime are separate execution paths.
- `src/futon5/wiring/runtime.clj` explicitly notes legacy MMCA runtime does not execute wirings directly.
- Result: architecture is coherent but split; users must choose path explicitly.

- SMT/JAX are not in the main evolutionary hot path.
- `src/futon5/tpg/compare.clj` is a comparison/orchestration path; core TPG evolution (`src/futon5/tpg/evolve.clj`) does not yet depend on SMT/JAX by default.
- Result: integration is real, but still "assistive" rather than foundational.

### P1 gaps (medium impact)

- Tensor backend is functional but not yet a high-performance numeric runtime.
- Current tensor operations are vector/list transformations in Clojure (`src/futon5/ct/tensor.clj`, `src/futon5/ct/tensor_exec.clj`).
- No JAX/XLA-backed tensor execution path exists yet.

- Transfer to ants/AIF is mostly bridge/output level.
- Tensor transfer pack and cyber-ant generation are in place, but there is not yet a fully closed training loop where ants/AIF outcomes directly drive MMCA/tensor parameter updates in one integrated loop.

- Some docs still describe older expected states.
- Example: `verify-invariants` output string still includes "expected - not yet implemented" wording even when xenotype invariants pass.

### P2 gaps (cleanup and maintainability)

- Local-physics kernel logic is duplicated between MMCA runtime and TPG runner.
- This increases drift risk when kernel behavior changes.

- Experimental/placeholder layers still exist in exotic/CT pathways.
- Several modules are intentionally placeholder/stub by design (as also documented in `docs/technote-settled-state.md`).

## 5) Suggested next steps to get "tensors flowing"

1. Expand tensor engine support from "safe subset" to targeted parity.
2. Add tensor equivalents for one or two high-value MMCA controls first (for example lesion and selected operator effects).
3. Unify execution contracts so metaevolve/report/feedback can consume MMCA and tensor runs interchangeably with no branch-specific glue.
4. Introduce a JAX-backed tensor backend behind the existing diagram primitive interface, preserving deterministic parity tests.
5. Move one closed-loop experiment to tensor-first (tensor run -> transfer pack -> policy update -> next tensor run).

That progression keeps invariants intact while moving from diagram-level tensor semantics to actual high-throughput tensor evolution.
