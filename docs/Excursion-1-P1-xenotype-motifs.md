# Excursion 1: Xenotype Motifs Improve MMCA Trajectory Health

**Thesis**: Certain parameter-space constraints (hexagram-derived "zones") produce
better trajectory health in MMCA than unconstrained or baseline runs.

**Outcome**: Publishable lemma + validated motif library (64 hexagram families).

**Dependency**: Mission 0 (P0) instrumentation must be solid — reproducible runs,
full provenance, working scorers.

---

## P0 Readiness Gate (Must Pass Before Stage 1)

We will not run Stage 1 until the P0 gate is satisfied. Current status (as of
2026-01-23) is **PASSED**.

**Criteria**

- [x] **Deterministic runner**: RNG-seeded execution + deterministic selection of exotypes.
  - Status: **Done** (exoevolve uses `java.util.Random` with explicit seed; deterministic `rng-nth` selection).
  - Commits: 0d57599
- [x] **Provenance logging**: full run metadata + periodic checkpoints + error logs.
  - Status: **Done** (`:meta` event at start, `:checkpoint` at heartbeat, `:error` with class/message, `:done` at end).
  - Commits: 0d57599, 84baa1e
- [x] **Reproducible artifact bundle**: exact command + seeds + configs + outputs in a stable location.
  - Status: **Done** (`:bundle` event records argv, cwd, log path — sufficient to reproduce).
  - Commits: 84baa1e
- [x] **Scorer/HIT alignment**: documented agreement threshold on a labeled set.
  - Status: **Done** (exoevolve_summary.clj computes Spearman, exact/ordinal accuracy; thresholds baked in: exact ≥0.55, ordinal ≥0.70, spearman ≥0.40, strong-disagree ≤0.15).
  - Commits: 19302b4, e94a4a4
- [x] **Health/diagnostic triage**: freeze/magma detection and reporting tied to logs.
  - Status: **Done** (dead/confetti flags in judge_cli and exoevolve; geno×phe diversity metrics; thresholds: dead = change ≤0.05 + entropy ≤0.2, confetti = change ≥0.45 + entropy ≥0.8).
  - Commits: 32e1de1, e94a4a4

**Gate rule**: Stage 1 starts only when all criteria are at least **Partial** and
the last two are **Done**.

**Gate passed**: 2026-01-23. All criteria Done. Ready for Stage 1.

---

## Stage 1: Confirm the Tai Anchor

**Claim**: The tai-zone constraint (update-prob in [0.3, 0.7], match-threshold in
[0.3, 0.7]) produces better trajectory health than unconstrained parameter
selection on the sigil family that showed historical EoC.

### Steps

1. **Select anchor seeds**
   - Include Mission 17a seed 4242
   - Generate 19 additional seeds with `(rand-int Integer/MAX_VALUE)`
   - Store in `futon5/resources/excursion-1-anchor-seeds.edn`

2. **Define three arms**
   ```clojure
   {:tai-constrained {:update-prob [0.3 0.7]
                      :match-threshold [0.3 0.7]
                      :sigil-family :gong} ;; 工-family
    :unconstrained   {:update-prob [0.0 1.0]
                      :match-threshold [0.0 1.0]
                      :sigil-family :random}
    :baseline        {:exotype-enabled false}}
   ```
   - Store in `futon5/resources/excursion-1-arms.edn`

3. **Run batch**
   ```bash
   bb -cp futon5/src:futon5/resources futon5/scripts/excursion_1_stage_1.clj \
     --seeds futon5/resources/excursion-1-anchor-seeds.edn \
     --arms futon5/resources/excursion-1-arms.edn \
     --length 80 \
     --generations 100 \
     --out-dir /tmp/excursion-1-stage-1
   ```

4. **Score all runs**
   - Primary: filament (best HIT alignment)
   - Secondary: envelope, triad
   - Store scores in `/tmp/excursion-1-stage-1/scores.csv`

5. **Statistical analysis**
   - Paired t-test or Wilcoxon: tai-constrained vs unconstrained
   - Paired t-test or Wilcoxon: tai-constrained vs baseline
   - Effect size (Cohen's d) with 95% CI
   - Store in `/tmp/excursion-1-stage-1/analysis.edn`

### Success Criteria

- [ ] tai-constrained > unconstrained with p < 0.05
- [ ] tai-constrained > baseline with p < 0.05
- [ ] Effect size d > 0.5 (medium effect)

### Artifacts

- `futon5/resources/excursion-1-anchor-seeds.edn`
- `futon5/resources/excursion-1-arms.edn`
- `/tmp/excursion-1-stage-1/` (full run histories)
- `/tmp/excursion-1-stage-1/scores.csv`
- `/tmp/excursion-1-stage-1/analysis.edn`

### Script Template

```clojure
;; futon5/scripts/excursion_1_stage_1.clj
(ns excursion-1-stage-1
  (:require [futon5.mmca.exoevolve :as exo]
            [futon5.mmca.score :as score]
            [clojure.edn :as edn]))

(defn run-arm [seed arm-config out-dir]
  ;; Run single arm for single seed
  ;; Store full history to out-dir
  ;; Return summary map
  )

(defn -main [& args]
  ;; Parse args
  ;; Load seeds and arms
  ;; Run all combinations
  ;; Score all runs
  ;; Write scores.csv
  )
```

---

## Stage 2: Ablation and Boundary Mapping

**Claim**: The tai-zone effect is specific — relaxing constraints degrades
performance; adjacent zones produce characteristically different regimes.

### Steps

1. **Define ablation arms**
   ```clojure
   {:tai-zone      {:update-prob [0.3 0.7] :match-threshold [0.3 0.7]}
    :relaxed       {:update-prob [0.2 0.8] :match-threshold [0.2 0.8]}
    :qian-ward     {:update-prob [0.8 1.0] :match-threshold [0.0 1.0]}
    :kun-ward      {:update-prob [0.0 0.2] :match-threshold [0.0 1.0]}
    :tight-tai     {:update-prob [0.4 0.6] :match-threshold [0.4 0.6]}}
   ```
   - Store in `futon5/resources/excursion-1-ablation-arms.edn`

2. **Run on same 20 seeds from Stage 1**
   ```bash
   bb -cp futon5/src:futon5/resources futon5/scripts/excursion_1_stage_2.clj \
     --seeds futon5/resources/excursion-1-anchor-seeds.edn \
     --arms futon5/resources/excursion-1-ablation-arms.edn \
     --out-dir /tmp/excursion-1-stage-2
   ```

3. **Score and compare**
   - All arms on same metrics as Stage 1
   - Pairwise comparisons: tai-zone vs each ablation
   - Generate boundary heatmap (update-prob x match-threshold x score)

4. **Characterize failure modes**
   - qian-ward: expect collapse/magma
   - kun-ward: expect freeze/static
   - Document regime signatures for each zone

### Success Criteria

- [ ] relaxed < tai-zone (p < 0.05)
- [ ] qian-ward < tai-zone (p < 0.05)
- [ ] kun-ward < tai-zone (p < 0.05)
- [ ] tight-tai ≈ tai-zone (no significant difference)

### Artifacts

- `futon5/resources/excursion-1-ablation-arms.edn`
- `/tmp/excursion-1-stage-2/`
- `/tmp/excursion-1-stage-2/boundary-heatmap.png`
- `/tmp/excursion-1-stage-2/failure-modes.edn`

---

## Stage 3: Trajectory Health Decomposition

**Claim**: Tai-zone motifs improve multiple independent trajectory health
signals, not just one scorer.

### Steps

1. **Define four health components**
   ```clojure
   {:non-collapse    ;; fraction of run avoiding freeze/magma
    :metastability   ;; sustained mid-entropy (not transient spikes)
    :substructure    ;; motif recurrence / filament persistence
    :phenotype-coupling} ;; phenotype active when genotype active
   ```
   - Implement in `futon5/src/futon5/mmca/health.clj`

2. **Compute health components for Stage 1 runs**
   ```bash
   bb -cp futon5/src:futon5/resources futon5/scripts/excursion_1_stage_3.clj \
     --runs-dir /tmp/excursion-1-stage-1 \
     --out /tmp/excursion-1-stage-3/health-decomposition.csv
   ```

3. **Analyze per-component**
   - Does tai-zone win on all 4?
   - Which components do current scorers (filament, envelope) capture?
   - Correlation matrix: components vs scorers

4. **Design composite health metric**
   - If tai wins on all 4: equal weight or Pareto
   - If tai wins on 3/4: note the exception, investigate

### Success Criteria

- [ ] tai-zone improves ≥3 of 4 components vs baseline
- [ ] No component is worse than baseline for tai-zone
- [ ] Identify which scorer best predicts each component

### Artifacts

- `futon5/src/futon5/mmca/health.clj`
- `/tmp/excursion-1-stage-3/health-decomposition.csv`
- `/tmp/excursion-1-stage-3/component-analysis.edn`

---

## Stage 4: Motif Family Expansion

**Claim**: Other hexagram-derived zones produce distinct, characterized regimes.

### Steps

1. **Select candidate hexagrams**
   Map 8-12 hexagrams to parameter zones:
   ```clojure
   {:tai      {:hex 11 :name "Peace" :zone [0.3 0.7] :predict :healthy}
    :pi       {:hex 12 :name "Stagnation" :zone :inverted-tai :predict :stuck}
    :qian     {:hex 1  :name "Creative" :zone [0.8 1.0] :predict :collapse}
    :kun      {:hex 2  :name "Receptive" :zone [0.0 0.2] :predict :frozen}
    :ji-ji    {:hex 63 :name "After Completion" :zone :tbd :predict :stable-static}
    :wei-ji   {:hex 64 :name "Before Completion" :zone :tbd :predict :dynamic-fragile}
    :xian     {:hex 31 :name "Influence" :zone :tbd :predict :responsive}
    :heng     {:hex 32 :name "Duration" :zone :tbd :predict :persistent}}
   ```
   - Store in `futon5/resources/excursion-1-hexagram-zones.edn`
   - Derive parameter zones from hexagram structure (line patterns)

2. **Run each zone on 20 seeds**
   ```bash
   bb -cp futon5/src:futon5/resources futon5/scripts/excursion_1_stage_4.clj \
     --seeds futon5/resources/excursion-1-anchor-seeds.edn \
     --hexagrams futon5/resources/excursion-1-hexagram-zones.edn \
     --out-dir /tmp/excursion-1-stage-4
   ```

3. **Characterize each regime**
   - 4-component health scores
   - Regime classification (healthy/stuck/collapse/frozen/etc)
   - Validate predictions from hexagram semantics

4. **Build taxonomy**
   - Which hexagrams produce "healthy" regimes?
   - Which produce useful "negative" regimes (for contrast)?
   - Cluster by health profile

### Success Criteria

- [ ] ≥3 hexagram zones produce statistically distinguishable regimes
- [ ] ≥1 zone rivals tai on health (different tradeoffs)
- [ ] Predicted-bad zones (qian, kun, pi) are actually bad
- [ ] Hexagram semantics correlate with regime character

### Artifacts

- `futon5/resources/excursion-1-hexagram-zones.edn`
- `/tmp/excursion-1-stage-4/`
- `/tmp/excursion-1-stage-4/hexagram-taxonomy.edn`

---

## Stage 5: Discovered Motifs via Synthesis

**Claim**: Wiring-diagram synthesis can discover high-health motifs.

### Steps

1. **Prepare training data**
   - Use Stage 1-4 runs as labeled examples
   - Health scores as fitness signal
   - Split: 60% train, 20% val, 20% test

2. **Run wiring synthesis**
   ```bash
   bb -cp futon5/src:futon5/resources futon5/scripts/synthesize_xenotype.clj \
     --train-data /tmp/excursion-1-synthesis-train.edn \
     --val-data /tmp/excursion-1-synthesis-val.edn \
     --generations 100 \
     --population 50 \
     --out /tmp/excursion-1-stage-5
   ```

3. **Evaluate top synthesized motifs**
   - Compare best synthesized vs tai-zone vs baseline
   - On held-out test seeds (not used in Stage 1-4)
   - Ablation: remove key nodes from synthesized diagram

4. **Interpret synthesized motifs**
   - Do they rediscover tai-like constraints?
   - What novel structure do they find?
   - Post-hoc CT interpretation

### Success Criteria

- [ ] Synthesis finds ≥1 motif matching tai on health
- [ ] OR synthesis exceeds tai (even better)
- [ ] Ablation of synthesized motif removes gain
- [ ] Synthesized motif is interpretable (not opaque)

### Artifacts

- `/tmp/excursion-1-stage-5/top-diagrams.edn`
- `/tmp/excursion-1-stage-5/synthesis-analysis.edn`

---

## Stage 6: CT Interpretation and Library Entry

**Claim**: Validated motifs can be expressed in CT terms and attached to sigils.

### Steps

1. **For each validated hexagram family (Stage 4)**
   - Identify which sigils map to that family
   - Query: `futon5/resources/exotype-program-manifest.edn`

2. **Generate CT templates**
   ```bash
   bb -cp futon5/src:futon5/resources futon5/scripts/generate_ct_templates.clj \
     --taxonomy /tmp/excursion-1-stage-4/hexagram-taxonomy.edn \
     --manifest futon5/resources/exotype-program-manifest.edn \
     --out-dir futon3/library/iiching
   ```

3. **Write PSR rationales**
   - For each sigil: "selected because maps to hexagram X which produces regime Y"
   - Store in flexiarg `@exotype-lift` section

4. **Validate CT predictions**
   - Hold out 10 seeds not used anywhere
   - Predict regime from CT template
   - Check prediction accuracy

### Success Criteria

- [ ] ≥50 sigils have CT templates derived from validated motifs
- [ ] CT templates predict regime correctly ≥70% on holdout
- [ ] PSR/PUR records generated automatically in new runs

### Artifacts

- Updated `futon3/library/iiching/*.flexiarg` files
- `/tmp/excursion-1-stage-6/ct-validation.edn`

---

## Stage 7: Full P1 Tournament

**Claim**: Xenotype motifs discovered in MMCA improve trajectory health vs
baselines (the P1 claim from the proof-plan).

### Steps

1. **Preregister hypotheses**
   - Primary: tai-family > random > baseline on 4-component health
   - Secondary: synthesized motifs ≥ tai-family
   - Ablation predictions
   - File: `futon5/docs/excursion-1-preregistration.md`

2. **Generate fresh seed set**
   - 50 seeds not used in Stages 1-6
   - Store in `futon5/resources/excursion-1-tournament-seeds.edn`

3. **Run tournament**
   ```bash
   bb -cp futon5/src:futon5/resources futon5/scripts/excursion_1_tournament.clj \
     --seeds futon5/resources/excursion-1-tournament-seeds.edn \
     --arms futon5/resources/excursion-1-tournament-arms.edn \
     --out-dir /tmp/excursion-1-tournament
   ```

4. **Blinded labeling** (if human judgment component)
   - Shuffle run IDs
   - Label without knowing arm
   - Unblind after all labels collected

5. **Full statistical analysis**
   - Effect sizes with CIs
   - Multiple comparison correction (Bonferroni or FDR)
   - Power analysis

6. **Write up**
   - Lemma statement
   - Methods
   - Results
   - Discussion of failure modes

### Success Criteria

- [ ] Primary hypothesis confirmed (p < 0.01 after correction)
- [ ] Effect size d > 0.5
- [ ] Ablation confirms motif is necessary
- [ ] Results hold on fresh seeds

### Artifacts

- `futon5/docs/excursion-1-preregistration.md`
- `/tmp/excursion-1-tournament/`
- `futon5/docs/excursion-1-results.md`

---

## Summary: Excursion 1 Deliverables

1. **Validated tai-zone anchor** (Stage 1-2)
2. **Trajectory health decomposition** (Stage 3)
3. **Hexagram taxonomy** (Stage 4)
4. **Synthesized motifs** (Stage 5)
5. **64 CT-interpreted sigil entries** (Stage 6)
6. **Publishable P1 result** (Stage 7)

---

## Codex Action Items

### Immediate (Stage 1 prep)
- [ ] Create `futon5/resources/excursion-1-anchor-seeds.edn` with 20 seeds
- [ ] Create `futon5/resources/excursion-1-arms.edn` with arm configs
- [ ] Implement `futon5/scripts/excursion_1_stage_1.clj`

### After Stage 1 passes
- [ ] Create ablation arms config
- [ ] Implement Stage 2 script
- [ ] Implement `futon5/src/futon5/mmca/health.clj` for Stage 3

### Infrastructure (can parallel)
- [ ] Ensure full history storage works (P0 dependency)
- [ ] Verify scorer alignment on known examples
- [ ] Set up statistical analysis utilities
