# Mission 0 Technote 2: Plan for the Next Experiment Round

This plan builds on the recent Mission 0 and Cyber-MMCA commits to structure
the next round of experiments, with clear inputs, outputs, and decision points.

## What is Cyber-MMCA?

Cyber-MMCA is a CT-framed variant of MMCA that lifts micro-scale CA dynamics
into macro-scale observables that can be treated as morphisms and compared
across parameter regimes. Practically, it provides sweep/report tooling that
maps parameter surfaces to macro feature trajectories and stability signals.

We use Cyber-MMCA here as a parallel analysis framework:
- MMCA expresses the core xeno → exo → geno → pheno loop.
- Cyber-MMCA provides a higher-level CT lens on the same loop by tracking
  macro features and their transformations as first-class evidence.
- Agreement between MMCA and Cyber-MMCA sweeps is a signal that the CT-level
  abstractions are aligned with the micro-level dynamics.

## Goals for this round

1. Validate that the ensemble scorers align with human EoC judgments.
2. Reduce variance in Mission 0 batches using parameter priors from sweeps.
3. Establish reproducible logging that supports PSR/PUR style learning.
4. Confirm that Cyber-MMCA tooling can be used to generalize sweep workflows.

## Inputs from recent work

- Mission 17a compare set and judgments (20 seeds) as a calibration set.
- Ensemble scores: short, envelope, triad, shift, filament.
- p × t sweep output for sigil 工: `/tmp/mission-17a-sweep-1768964125056.csv`.
- Mission 0 batch scripts and rendering pipeline.
- Cyber-MMCA sweeps and reports for parameter-surface analysis.

## Plan overview

### Phase 1: Calibration and scoring alignment

Objective: choose a reliable ensemble or gate+rank that matches human labels.

Steps:
- Re-score the Mission 17a compare set with current scorers.
- Use the learned preference weights to generate a composite.
- For each seed, record: scorer pick, gate+rank pick, human label.
- Update a small table in notebook III with agreement counts.

Outputs:
- Updated per-seed agreement table.
- Revised weights if needed.

### Phase 2: Parameter prior selection (sweep-guided)

Objective: pick a default parameter region for Mission 0 batches.

Steps:
- Use the sweep output to choose a top-3 parameter set.
- Run short A/B batches for each setting (e.g., 50 runs).
- Compare ensemble distributions and hex class distributions.

Outputs:
- A chosen default (update-prob, match-threshold).
- Evidence logged in notebook III.

### Phase 3: Mission 0 batch runs with PSR/PUR logging

Objective: produce reproducible batches with logs that capture selection logic.

Steps:
- Run a modest batch with the chosen default parameters.
- Use `mission_0_persist.clj` to emit PSR/PUR artifacts.
- Render top/mid snapshots for visual audit.

Outputs:
- `/tmp/mission-0/mission-0-*.edn` logs
- `/tmp/mission-0/mission-0-*-hex.csv` summaries
- New snapshot images in `futon5/resources/figures`

### Phase 4: Cyber-MMCA integration

Objective: apply Cyber-MMCA sweep/report workflow to Mission 0.

Steps:
- Run a Cyber-MMCA stride sweep using the current best exotype settings.
- Compare the sweep report to the MMCA sweep for consistency.
- Log any divergences as candidates for further analysis.

Outputs:
- Cyber-MMCA sweep report artifacts.
- Notes on cross-domain alignment (or mismatch).

## Decision checkpoints

- If the composite scorer is misaligned, revisit weights or remove a scorer.
- If the sweep prior fails visually on high-confidence seeds, reduce its weight.
- If PSR/PUR logs are incomplete, pause before large overnight runs.

## Proposed commands

```bash
# Re-score Mission 17a compare set
bb -cp futon5/src:futon5/resources futon5/scripts/mission_17a_rescore.clj \
  --log futon5/resources/mission-17a-refine-r50.log \
  --replay

# Generate compare set (if needed)
bb -cp futon5/src:futon5/resources futon5/scripts/mission_17a_compare.clj \
  --n 20 \
  --seed 4242

# p × t sweep (if rerunning)
bb -cp futon5/src:futon5/resources futon5/scripts/sweep_pt.clj

# Mission 0 batch (modest)
bash futon5/scripts/mission_0_batch.sh modest
```
