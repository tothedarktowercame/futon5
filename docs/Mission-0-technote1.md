# Mission 0 Technote 1

This technote summarizes the latest Mission 0 work and related notebook updates,
covering the four most recent commits in `futon5` and the relevant sections of
`futon5/resources/exotic-programming-notebook-iii.org`.

## Scope and sources

- Commits: `ef2764f`, `57cdb72`, `b89cb08`, `f2ce811`
- Notebook: `futon5/resources/exotic-programming-notebook-iii.org`
- Core batch scripts: `futon5/scripts/mission_0_batch.sh`, `futon5/scripts/mission_0_render.clj`
- Scoring and analysis: `futon5/src/futon5/mmca/*`, `futon5/src/futon5/hexagram/*`

## High-level goal (Mission 0)

Build MMCA-native xenotypes that can reliably find and recognize edge-of-chaos
states, then encode that selection logic as CT-native processes rather than
ad-hoc human inspection. The notebook frames this as self-transfer: MMCA
evaluates itself, and the evaluation is the xenotype.

## Notebook III highlights (Mission 0)

- "Tai zone" hypothesis: edge-of-chaos occurs in a mid-band for exotype params.
  The notebook defines this as update-prob in ~[0.3, 0.7] and match-threshold in
  ~[0.3, 0.7], with a working interpretation of Qian/Kun/Tai as extremes/balance.
- Mission 0 batch runner: `bash futon5/scripts/mission_0_batch.sh modest` and
  `bash futon5/scripts/mission_0_batch.sh overnight`.
- Logged outputs:
  - EDN logs: `/tmp/mission-0/mission-0-*.edn`
  - Hex summaries: `/tmp/mission-0/mission-0-*-hex.csv`
- Visual checks:
  - Mission 0 modest snapshots (top/mid)
  - Mission 0 modest50 snapshots (lower hex weight)
  - Envelope sweep examples (legacy and shift)
- Ensemble calibration against Mission 17a:
  - Rescoring and compare sets used to check agreement between envelope/filament/
    shift/triad/short vs human judgment.
  - Example seeds are embedded with baseline/exotic side-by-side images.
- Parameter sweep result (p x t for sigil Gong, gate+rank):
  - Best mean-rank config: update-prob 0.60, match-threshold 0.80
  - Sweep output: `/tmp/mission-17a-sweep-1768964125056.csv`
  - Visual checks logged for the top config.

## Recent commits summary

### ef2764f - Work on Mission 0 and Cyber-MMCA

- Introduces Cyber-MMCA core and comparison tooling:
  - `futon5/src/futon5/cyber_mmca/core.clj`
  - `futon5/scripts/cyber_mmca_*.clj`
- Adds windowed macro features and an initial test suite:
  - `futon5/scripts/windowed_macro_features.clj`
  - `futon5/test/futon5/mmca/metrics_test.clj`
- Adds vocabulary notes: `futon5/docs/metaca-terminal-vocabulary.md`

### 57cdb72 - Further Mission 0 work

- Adds Mission 0 batch tooling and persistence helpers:
  - `futon5/scripts/mission_0_batch.sh`
  - `futon5/scripts/mission_0_render.clj`
  - `futon5/scripts/mission_0_persist.clj`
- Adds scorer suite and supporting modules:
  - Filament scoring: `futon5/src/futon5/mmca/filament.clj`
  - Register shift scoring: `futon5/src/futon5/mmca/register_shift.clj`
  - Trigram scoring: `futon5/src/futon5/mmca/trigram.clj`
  - Hexagram lift + metrics: `futon5/src/futon5/hexagram/*`
  - Ensemble scoring glue: `futon5/src/futon5/scoring.clj`
- Adds Mission 17a compare/rescore scripts:
  - `futon5/scripts/mission_17a_compare.clj`
  - `futon5/scripts/mission_17a_rescore.clj`
  - `futon5/scripts/mission_17a_gate_rank.clj`
  - `futon5/scripts/replay_fidelity.clj`
- Adds sonification tooling:
  - `futon5/sonify.py`
  - `futon5/sonify.md`

### b89cb08 - Update Notebook III

- Adds the current notebook III narrative and evidence:
  - "Tai zone" hypothesis and evidence summary
  - Mission 0 batch snapshots and envelope sweep examples
  - Mission 17a rescoring and compare set results
  - p x t sweep results with a visual check

### f2ce811 - Add Cyber-MMCA sweeps and reports

- Adds Cyber-MMCA sweep and stress-test tooling:
  - `futon5/scripts/cyber_mmca_stride_sweep.clj`
  - `futon5/scripts/cyber_mmca_stress_test.clj`
  - `futon5/scripts/cyber_mmca_state_isolation.clj`
  - `futon5/scripts/cyber_mmca_macro_trace_report.clj`
- Expands Cyber-MMCA core and documentation:
  - `futon5/src/futon5/cyber_mmca/core.clj`
  - `futon5/README-cyber-mmca.md`

## How the sweep helps Mission 0

The p x t sweep provides a population-level prior for exotype parameters. This
reduces variance when running Mission 0 batches by anchoring update-prob and
match-threshold to a region that scores well across seeds. It does not guarantee
improvement for any specific seed, but increases the expected frequency of
good runs in batch mode.

## Practical integration plan (Mission 0)

1. Use the sweep-best parameter region as the default for Mission 0 runs.
2. Keep the ensemble (envelope, filament, shift, triad, short) active for all
   batch runs and store scores in the run log.
3. Continue to log PSR/PUR-style selections so the selection process becomes
   reproducible and improvable.
4. Use Cyber-MMCA sweep/report tooling to generalize the parameter-surface
   mapping approach into Mission 0 workflows.

## Reproducibility commands

```bash
# Mission 0 batch
bash futon5/scripts/mission_0_batch.sh modest
bash futon5/scripts/mission_0_batch.sh overnight

# Mission 17a compare set
bb -cp futon5/src:futon5/resources futon5/scripts/mission_17a_compare.clj --n 20 --seed 4242

# Mission 17a p x t sweep
bb -cp futon5/src:futon5/resources futon5/scripts/sweep_pt.clj
```
