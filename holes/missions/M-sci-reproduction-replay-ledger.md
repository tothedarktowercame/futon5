# Replay ledger — futon5 "bright ideas" mined for M-sci-reproduction

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
