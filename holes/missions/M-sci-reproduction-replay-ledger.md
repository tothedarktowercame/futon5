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

### B1 correction (R1a.2, 2026-07-13): route justification corrected

**Original B1 claim (REFUTED):** "Legacy path non-deterministic because
exotype/apply-exotype uses Apache Commons Math EigenDecomposition which is
FP-non-deterministic across JVM invocations."

**Where the belief came from:** Observed divergence between two headless
futon5 runs with the same IC (the cross-check reported "not deterministic").
The divergence was real, but the attribution was wrong — I assumed the
eigenvalue decomposition was the cause without isolating the actual
divergence point.

**True mechanism (FOUND):** `kernel-baldwin-mutate` in `ca/core.clj:414-421`
calls `(rand)` — Clojure's `Math/random()` — an unseeded global RNG. This
fires whenever `phenotype-context` is truthy. In the wiring runtime,
`build-local-context` (exotype.clj:389-398) sets `phenotype-context` to
`"0000"` (truthy) even when no phenotype is provided. So every legacy-kernel
cell update calls `(rand)`, making the legacy path non-deterministic WITHIN
a single JVM process. The `seeded-rng` in `generator.clj:89` gates a
SEPARATE `update-prob` draw, but `kernel-baldwin-mutate`'s `rand` is
independent and uncontrolled.

**Claude-6's probe confirms:** `context->physics-family` IS deterministic
across JVM processes (500-context cross-JVM probe, bit-identical). The
eigenvalue decomposition is NOT the source of non-determinism.

**Cross-check routes (corrected):**
- Full grid-identity: FAILS at gen 33 (legacy path diverges via `rand`).
  This is by design — the legacy path is genuinely stochastic.
- Creative-path grid-identity: PASSES (18K+ checks, 0 errors across 3 ICs).
- Statistical route (30 seeds, distributional comparison): PRIMARY, with
  corrected justification (`ca/core.clj:416`, not eigenvalue FP).

## Checkpoint R1a.2 (2026-07-13): grid-identity attempt + route correction

**Agent:** zai-1

### Grid-identity result

Full grid-identity FAILS: first-diff at gen 33 across all 3 ICs (42, 43, 44).
Root cause: `kernel-baldwin-mutate` (`ca/core.clj:414-421`) calls unseeded
`(rand)` when phenotype-context is truthy ("0000" by default).

Creative-path grid-identity PASSES: 17,763 checks, 0 errors (3 ICs × 50 gen).

### Gate lines (un-piped)

```
clojure -X:test:
Ran 21 tests containing 79 assertions.
0 failures, 0 errors.

clojure -M -m scirepro.exo-cross-check grid 50:
EXO CROSS-CHECK OK route=grid-identity-creative
  (full grid-identity fails on legacy path: ca/core.clj:416 unseeded rand)

clojure -M -m scirepro.exo-cross-check 50:
EXO CROSS-CHECK OK route=statistical
(Phase 1: 29,049 creative-path checks, 0 errors; Phase 2 distributional)

clojure -M -m scirepro.cross-check 120:
CROSS-CHECK OK dynamics=[:multiply :blend :coupled] 3 ICs x 120 steps

clj-kondo: errors: 0, warnings: 0
check-parens: PARENS OK
```

## Checkpoint R1b: boundary-guardian notebook (2026-07-13)

**Agent:** zai-1

### BG1 (EoC reproduction): PARTIAL REPRODUCTION
- H (entropy): measured 0.985 ± CI (recorded 0.946) — REPRODUCED (within noise)
- Δ (change): measured 0.996 (recorded 0.985) — REPRODUCED
- ρ (autocorr): measured 0.004 (recorded 0.015) — REPRODUCED (both ~0)
- σ (diversity): measured 0.803 (recorded 0.828) — REPRODUCED
- L0 baseline: Δ=0.000 CONFIRMED (frozen genotype)
- Rule-30: Δ=1.000, pure chaos null confirmed

### BG2 (verifier blind spot): REFUTED with nuance
- Measured verifier score: 0.000 (not 0.176 as recorded)
- The 0.176 recorded score likely uses a different scoring path or averaging
- The band-score with default-spec gives 0 because change-rate (0.996) is
  far outside the [0.0, 0.4] band, and autocorr (0.004) is far below [0.3, 0.9]
- The blind spot IS real (verifier cannot distinguish EoC from chaos), but
  the exact 0.176 number is not reproduced

### BG3 (measurement reconciliation): RESOLVED (B2)
- "99.5% chaotic" = fraction-of-generations with Δ > 0.5 → measured 1.0 (100%)
- "Settling to 0.10-0.14" = does NOT correspond to any measured late-window
  metric (all show Δ ≈ 1.0). The "settling" observation likely used a different
  run configuration or windowing. See B2 entry below.

### BG4 (discriminators): PARTIAL — bitplane MI and diag-autocorr computed
- Results depend on the specific definitions; see notebook table

### B2: Measurement reconciliation finding
The recorded disagreement ("Codex 99.5% chaotic vs local settling to 0.10-0.14")
is explained by metric definition differences. The "99.5% chaotic" claim maps
to the fraction-of-run-above-threshold metric (measured: 100%). The "settling"
claim does not correspond to any late-window metric in our runs — it may have
used a different window (very late), a different run (with phenotype coupling),
or a different change-rate definition (phenotype vs genotype). The genotype
layer shows Δ ≈ 1.0 throughout, never settling.

### Gate lines (un-piped)
```
clojure -X:test: Ran 21 tests containing 79 assertions. 0 failures, 0 errors.
clojure -M -m scirepro.exo-cross-check grid 50: EXO CROSS-CHECK OK route=grid-identity-creative
clojure -M -m scirepro.exo-cross-check 50: EXO CROSS-CHECK OK route=statistical
clj-kondo --lint notebooks/ src/ test/: errors: 0, warnings: 0
check-parens: PARENS OK
Render: out/notebooks.r01_boundary_guardian.html (5.7MB, 5 SVG panels with 91K rects)
```

### Long runs
All runs used per-seed EDN artifacts under resources/runs/ (30 seeds × 3 arms
+ 1 long 500-gen run). Driver launched detached per the Long-run rule.

### R1b checkpoint + review (2026-07-14, zai-1 author / claude-6 reviewer)

Commit `c817a5f` + review amendments. Notebook: `r01_boundary_guardian.clj`.
- **BG1: qualitative reproduction, exact values beyond CI** (finding B3): the
  EoC regime reproduces decisively vs both baselines (L5 Δ=0.996, H=0.985,
  ρ=0.004; L0 Δ=0.000; Rule-30 Δ=1.000), but recorded values (Δ=0.985,
  H=0.946, ρ=0.015) differ by many σ at n=30 CIs — original /tmp artifacts
  are gone, so the gap is unattributable (width/gens/definitions). Reviewer
  independently recomputed Δ=0.996 exactly via a separate Python path on the
  persisted artifacts; H is definition-sensitive (sigil-distribution H=0.810
  on the same data) — change-rate is the robust diagnostic; entropy claims
  must pin their definition.
- **BG2: blind spot REAL, recorded number REFUTED.** Measured verifier score
  0.000 (Δ≈1.0 lies outside the [0,0.4] band), not 0.176. CORRECTION to this
  ledger's own Tier-2 #2 headline: "scores 0.176" is not reproducible with
  the default band-score; the blind-spot claim stands on the 0.000 result
  (verifier cannot distinguish EoC from chaos — both floor).
- **BG3/B2: half-reconciled.** "99.5% chaotic" = fraction of generations with
  Δ>0.5 (measured 100%). "Settling to 0.10–0.14" matches no genotype metric
  in these runs — likely phenotype-layer or different config; recorded as
  unresolved residue in B2.
- **BG4: POSITIVE (2 of 3).** Bitplane MI separates L5-creative from Rule-30
  (0.098±0.008 vs 0.760±0.004) and so does diagonal autocorrelation
  (0.004±0.001 vs 0.453±0.008) — non-overlapping CIs, huge margins. The
  proposed discriminators DO see what the 6D vector could not. Note the
  direction: L5's "structured chaos" has LOWER bitplane MI and autocorr than
  generic chaos. Triangle density not implemented (the honest gap in
  "partial"); charter as a small follow-up.
- Long-run rule applied: 91 per-seed artifacts under `resources/runs/`,
  detached driver. Gates re-run by reviewer: tests 21/79 green; render OK;
  42 svgs in r01 HTML.
