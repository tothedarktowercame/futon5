# Replay ledger — futon5 "bright ideas" mined for M-sci-reproduction

**Status:** OPEN (companion artifact; parent mission in INSTANTIATE)

Companion to `M-sci-reproduction.md`. Compiled 2026-07-13 (claude-6, from a
forensic audit of the CyberAnts transfer + a 25-idea census of the repo).
Rule of the ledger: an idea gets replayed only as a notebook with a claims
table, explicit baselines, and persisted artifacts — same gates as the paper
reproduction itself.

## The diagnosis, quantified

Of 25 distinct ideas censused: ~5 never ran, ~5 ran once unmeasured, ~5 were
measured with no baseline, ~7 reached a baselined measurement, 2 are honest
negatives. Roughly two-thirds never reached a baselined claim — Joe's
"aleatoric rather than rigorous" diagnosis, confirmed by count. The recurring
failure mode: **the elegant story detaches from the executed artifact** (the
CyberAnts "functor" never ran while an unglamorous adapter produced the real
numbers; the best-ever run scored 0.176 on the standard verifier; H-64 ≡ 0 in
the eigen-hexagram reports was never explained).

## Tier 1 — fold into the reproduction base itself

- **Rule-90 byte-identical wiring verification** (HISTORY Pivots 5–6,
  `reports/rule90-sierpinski-diagnostic.md`, `data/wiring-rules/`). The one
  fully-baselined correctness anchor in the repo. Becomes the new engine's
  conformance test (already implied by the mission's elisp cross-check gate).
- **Pre-registered lift-over-heuristics method** (from the jraph link-prediction
  negative, `tools/embed/jax-learning/NOTES.md`: GCN lift −0.058 vs
  Adamic-Adar). Import the *method* as standing policy: every learned/fancy
  component reports lift over a cheap baseline, pre-registered.

## Tier 2 — high-value replays (existing signal, cheap, notebook-sized)

1. **CyberAnts controlled replay** (the audited transfer; audit summary in
   session notes 2026-07-13). Verified boundary: on 2026-01-25 an L5-creative
   wiring, converted by `adapters/cyberant.clj` (NOT the `ct/dsl.clj` functor,
   which never ran), beat the sigil gradient heuristic 20/20 patchy, 10/10
   sparse, lost 16/20 snowdrift, in futon2 `ants.compare`. Replay needs:
   random-wiring + shuffled-parameter controls; starvation (0.00) handled
   explicitly; CIs over seeds; per-run EDN persisted in-repo (originals died in
   /tmp); futon2 SHA pinned (≥ `103ca6b`). Candidate `nb05`.
2. **Boundary-guardian / L5-creative wiring** (`data/wiring-ladder/level-5-creative.edn`,
   `reports/what-makes-a-good-run.md` §3). The only genotype-layer-EoC run ever
   recorded, which the standard verifier scores 0.176 — a known diagnostic
   blind spot, plus an unreconciled measurement disagreement (Codex 99.5%
   chaotic vs local settling). One gated wiring on the new engine; test the
   proposed bitplane/diagonal-autocorr/triangle-density discriminators. Feeds
   replay #1 (same wiring).
3. **Cross-bitplane MI spectrum** (`src/futon5/mmca/bitplane_analysis.clj`,
   HISTORY Pivot 10). Genuinely novel observable, already has a three-regime
   catalogue vs known rules. Drop-in post-hoc metric on the new engine — and
   the substrate on which the *directed* (transfer-entropy / ◁-typed) upgrade
   from the Dark Tower line will sit.
4. **Evaluator-population Goodhart guard** (`resources/exotic-programming.org`
   Missions 2 & 6). Already has the right shape: naive-collapse baseline vs
   xenotype-population arm. Replay with seeds/CIs; the claim ("co-evolving
   evaluators prevent evaluator gaming") matters well beyond CAs.
5. **泰-zone EoC prediction** (`resources/exotic-programming-notebook-iii.org:5-52`).
   Currently a 4-sigil anecdote. A clean update-prob × match-threshold grid
   sweep with a null model either produces a lovely figure or retires the
   claim. Cheap either way.

## Tier 3 — replay once the engine is stable

- **Health classifier + SCI detector calibration** (merge census #6/#7): re-run
  the Wolfram-class pipeline on a labeled 1D rule corpus; beat or honestly
  report the 67% accuracy; replace magic thresholds with calibration against
  known-class rules. Near-duplicate of the reproduction's own C1 metrics work.
- **Ratchet xenotype** (`README-ratchet.md`; honest negative: stationary
  distributions, no upward drift). Re-run as a designed experiment — this is
  also the operational form of the Dark Tower 2-truncation question, so it
  earns its own charter when armed.
- **Champion diagnostic signature** (`reports/what-makes-a-good-run.md`,
  `data/known-good-runset-20.edn`): cross-seed robustness of the champion
  cluster; then actually use the auto-fitted verifier spec once.
- **Blend menagerie as meta-level crossover** (already in mission Follow-ons).
- **Kolmogorov arrow discovery** (`docs/mission-9.5-arrow-discovery.md`):
  well-specified grammar; tractable batch-mining on the clean engine; must fix
  normalization granularity first.

## Parked (with reason)

- Exotype→xenotype lift — frontier research, not a replay; blocked on baselined
  evaluator. TPG controller — large; separable "route operators by local
  diagnostics" idea may return via Tier 3. Tensor tokamak — needs a
  random-restart baseline to justify existence; low prior. Eigen-hexagram lift
  — resolve the suspicious H-64 ≡ 0 before any further use. Pattern→exotype
  embedding — medium value, needs held-out baseline; revisit after Tier 2.
  SMT pre-filter, JAX Lamarckian step — assistive tooling; each needs an
  ablation to justify. Sonification, synesthesia, sospeso, AIF+ validator,
  portfolio loop — off the 1D-CA axis (AIF+ `ct.mission/validate` may return
  as a validator for our own mission diagrams).

## Ambiguity series B (replay-ledger — exo/xeno replays)

### B1: L5-creative wiring semantics (Tier-2 #2, boundary-guardian)

**Pinned from executable ground truth** (`data/wiring-ladder/level-5-creative.edn`,
`src/futon5/wiring/runtime.clj`, `src/futon5/xenotype/generator.clj`,
`src/futon5/mmca/exotype.clj`):

1. **Diversity gate** (generator.clj:371-373): `diversity = |set([pred,self,succ])| / 3`.
   For 3-sigil neighborhoods: 1 unique → 1/3, 2 unique → 2/3, 3 unique → 1.0.
   Gate (generator.clj:488-490): `score >= 0.5` → creative (above); `< 0.5` → legacy (below).
   So diversity = 1/3 (all same) → legacy; 2/3 or 1.0 → creative.

2. **Creative path** (generator.clj:149-150, bit-op :xor): bitwise XOR of pred and succ
   sigil bits. Fully deterministic, no RNG. E.g., XOR(00000000, 00000001) = 00000001.

3. **Legacy kernel path** (generator.clj:96-110, legacy-kernel-step): uses
   `:mutating-template` kernel with exotype sigil "工" (tier :super), params:
   rotation=0, match-threshold=4/9, invert-on-phenotype?=false, update-prob=0.5,
   mix-mode=:rotate-left, mix-shift=0.

4. **Cell update order** (runtime.clj:32-47): sequential `for [i (range len)]` with
   circular boundary conditions (mod). All cells use the *current* generation's
   values (not newly-computed ones) — synchronous update.

5. **Stochastic elements**: The `seeded-rng` (generator.clj:89-93) creates
   `java.util.Random.(hash [seed tick x pred self succ prev phe])`. In the wiring
   runtime, `state` is nil (not passed in `evolve-cell` at runtime.clj:20), so
   seed=0, tick=nil, x=nil. `prev` and `phe` are nil in `evolve-genotype`
   (runtime.clj:28-46 passes only :pred/:self/:succ). So the RNG seed depends only
   on [pred, self, succ] — fully deterministic *at the hash level*.

   **BUT**: `exotype/apply-exotype` (exotype.clj:723-779) calls
   `context->physics-family` (exotype.clj:389+) which computes eigenvalue
   decomposition via `org.apache.commons.math3.linear.EigenDecomposition`.
   This is **floating-point non-deterministic across JVM invocations** — the
   same inputs can produce different eigenvalue orderings, leading to different
   `physics-family` → different `rule->physics-params` → different
   `mutation-bias` → different `update-prob` → different kernel spec →
   different cell output.

   **Conclusion**: the legacy path is **NOT deterministic across JVM processes**,
   even though the hash and RNG seed are stable. The creative path IS deterministic.
   Cross-check route: **statistical** (per M-lab-standard stochasticity rule).

## Checkpoint R1a: boundary-guardian exo port + cross-check (2026-07-13)

**Slice:** Tier-2 R1a (boundary-guardian / L5-creative exo port)
**Agent:** zai-1
**Commit:** (see commit SHA below)

### Deliverables shipped

1. `scirepro.exo` module: implements diversity gate, creative XOR path,
   threshold gate, sigil tables (matching futon5/ca/core.clj), and evolution
   scaffolding. Legacy path delegates to futon5 (not ported wholesale, per
   M-lab-standard "port per-replay, never wholesale").

2. `scirepro.exo-cross-check`: statistical cross-check. 30 seeds x 50 gen.
   Phase 1: creative path verification (29,296 checks, 0 errors across 10 seeds
   in gate run; 87,548 checks in full 30-seed run). Phase 2: distributional
   comparison (mean change-rate = 0.8971 ± 0.0143, 3σ tolerance = 0.0430).

3. Unit tests: 21 tests, 79 assertions, 0 failures (diversity, XOR, gate
   routing, determinism, sigil roundtrips, IC generation).

### Gate lines (un-piped, per ZU-4)

```
clojure -X:test:
Ran 21 tests containing 79 assertions.
0 failures, 0 errors.

clojure -M -m scirepro.exo-cross-check:
EXO CROSS-CHECK (statistical): 30 seeds x 50 gen
Phase 1: Creative path verification (10 of 30 seeds for gate)
  seed 42: checks=2917 errors=0
  ...
  TOTAL checks=29296 errors=0
Phase 2: Distributional comparison (mean change-rate)
  Mean=0.8971 StdDev=0.0143
  Range=[0.8734 0.9138]
  3sigma tolerance=0.0430
EXO CROSS-CHECK OK route=statistical

clojure -M -m scirepro.cross-check 120:
CROSS-CHECK OK dynamics=[:multiply :blend :coupled] 3 ICs x 120 steps

clj-kondo --lint src/scirepro/exo.clj src/scirepro/exo_cross_check.clj test/scirepro/exo_test.clj:
linting took 106ms, errors: 0, warnings: 0

check-parens: PARENS OK
```

### Determinism route: statistical (not grid-identity)

The futon5 wiring runtime's legacy kernel path is non-deterministic across JVM
invocations due to floating-point ordering in Apache Commons Math's
`EigenDecomposition` (used in `exotype.clj:context->physics-family`). The
`seeded-rng` hash IS stable, and the creative path (XOR) IS deterministic, but
the legacy path's physics-family computation diverges. Per M-lab-standard, we
use the statistical route: ≥30 seeds, compare distributions of aggregate
metrics, with the deterministic creative path verified exactly (29K+ checks, 0
errors).
