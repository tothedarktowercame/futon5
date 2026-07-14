# Mission 4/6: Xenotype guards reduce evaluator degeneracy (r02 guard arm)

Statistical replay — the exo/xeno engine (`evolve-exotypes`) is **not per-seed
deterministic** (cf. the Mission-2 review); the mean comparison over n=30 is
stable and the arm CIs are disjoint on the degeneracy metrics.

## Claims table (n=30 / arm, 95% CIs)

| Metric | Unguarded (Mission 4) | Guarded (Mission 6) | Verdict |
|--------|----------------------|---------------------|---------|
| survivor-confetti-rate | 0.927 ± 0.013 | 0.631 ± 0.038 | Guard **reduces** degenerate survivors (CIs disjoint) |
| survivor-mean-change | 0.853 ± 0.006 | 0.776 ± 0.012 | Guard lowers churn (CIs disjoint) |
| survivor-mean-entropy-n | 0.870 ± 0.003 | 0.810 ± 0.011 | Guard lowers noise (CIs disjoint) |
| survivor-identity-diversity | 0.979 ± 0.020 | 0.992 ± 0.011 | No significant difference (CIs overlap; both near-max) |
| survivor-dead-rate | 0.000 | 0.000 | No dead survivors in either arm |

## Measured proposition

**"Xenotype guards change the surviving evaluator set (Goodhart guard)":** a slow
xenotype guard (update every ~100 exotype evals) shifts the surviving exotype
population toward **less degenerate** evaluators — confetti-rate 0.927 → 0.631,
mean-change 0.853 → 0.776, entropy 0.870 → 0.810 (all with **disjoint** 95% CIs,
n=30). The guard **reduces but does not eliminate** degeneracy (63% of guarded
survivors are still confetti). Identity-diversity is unchanged (both arms
near-maximal). So the guard demonstrably reshapes the surviving evaluator set on
the degeneracy axis — the honest, statistical form of Mission 6's "xenotypes
demonstrably change the surviving evaluator set."

Together with the Mission-2 genotype-collapse baseline, this is the second arm of
the **r02 evaluator-population replay**.

## Protocol

- Two arms via `evolve-exotypes`: unguarded (Mission 4, no xeno) vs guarded
  (Mission 6, xeno-spec `data/mission-04-06-xeno.edn`, xeno-weight 0.5).
- 30 seeds (42–71); runs=100, length=30, generations=10, pop=16,
  update-every=100. STATISTICAL replay (no per-seed determinism); mean comparison
  stable, arm CIs disjoint on the degeneracy metrics.
- Per-run EDN artifacts in-repo at `data/mission-04-06-runs/`.

## Ground truth

- Protocol: `resources/exotic-programming.org` Mission 4 (:130–134) + Mission 6 (:142–146)
- Engine: `futon5.mmca.exoevolve/evolve-exotypes`, `futon5.mmca.xenotype`
- Driver: `src/futon5/drivers/mission04_06.clj`
