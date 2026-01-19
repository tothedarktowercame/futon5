# Mission 0 Tickets — Self-Transfer: CT-Native EoC Discovery

Junior dev tickets for implementing Mission 0. Work through in order.
See `resources/exotic-programming-notebook-iii.org` for full context.

---

## Phase 1: The 6×6 Matrix Structure

### M0-001: Implement exotype->6x6 matrix conversion

**Context**: The 36-bit exotype has structure: LEFT(8) + EGO(8) + RIGHT(8) + NEXT(8) + PHENOTYPE(4).
This maps to a 6×6 matrix where diagonal = self/time, off-diagonal = space.

**Task**:
- Create `src/futon5/hexagram/lift.clj`
- Implement `(defn exotype->6x6 [exotype-bits] ...)` that arranges 36 bits into 6×6 matrix
- Diagonal: [EGO, EGO, NEXT, NEXT, NEXT, OFFSPRING]
- Below diagonal: LEFT bits
- Above diagonal: RIGHT bits
- Lower-right 4×4: Phenotype parents
- Corner (6,6): Offspring bit

**Test**: Round-trip test — `(6x6->exotype (exotype->6x6 bits))` should equal `bits`

**Acceptance**: Function exists and passes round-trip test.

---

### M0-002: Implement diagonal extraction and yang? threshold

**Context**: The hexagram is literally the diagonal of the 6×6 matrix.

**Task**:
- Implement `(defn diagonal [matrix-6x6] ...)` → returns 6 values
- Implement `(defn yang? [value] ...)` → returns true if value is "yang" (threshold TBD, start with >0.5)
- Implement `(defn diagonal->hexagram-lines [diag] ...)` → returns 6-element vector of :yin/:yang

**Test**:
- All-ones diagonal → all :yang
- All-zeros diagonal → all :yin
- Mixed → correct mix

**Acceptance**: Functions exist and pass tests.

---

### M0-003: Implement hexagram line representation

**Context**: Need standard representation for hexagrams as 6 lines (bottom to top).

**Task**:
- Define hexagram as `{:lines [l1 l2 l3 l4 l5 l6] :number N :name "..."}`
- Implement `(defn lines->hexagram-number [lines])` — convert 6 yin/yang to 1-64
- Implement `(defn hexagram-number->lines [n])` — reverse
- Use King Wen sequence (traditional I Ching ordering)

**Test**:
- `:qian` (all yang) = hexagram 1
- `:kun` (all yin) = hexagram 2
- `:tai` = hexagram 11 (earth over heaven = [yang yang yang yin yin yin])

**Acceptance**: Can convert between lines and hexagram numbers correctly.

---

### M0-004: Implement exotype->hexagram lift

**Context**: Combine M0-001, M0-002, M0-003 into complete lift.

**Task**:
- Implement `(defn exotype->hexagram [exotype-36-bits] ...)`
- Pipeline: bits → 6×6 matrix → diagonal → yang? threshold → hexagram

**Test**:
- Known exotypes from M17 (工, 土, etc.) should lift to sensible hexagrams
- Extreme exotypes (all intervention, all preservation) should lift to 乾/坤

**Acceptance**: Function exists. Manual inspection of results looks reasonable.

---

## Phase 2: Dynamics Classification

### M0-005: Extract existing EoC metrics from MMCA run

**Context**: We have computational proxies for EoC — entropy, diversity, etc. Need to expose them cleanly.

**Task**:
- Create `src/futon5/hexagram/metrics.clj`
- Implement `(defn run->metrics [mmca-run] ...)` that extracts:
  - `:entropy-spatial` — spatial entropy of final state
  - `:entropy-temporal` — entropy over time
  - `:motif-diversity` — count of distinct local patterns
  - `:regime` — classification (collapse/chaos/eoc/static)
  - `:update-prob` — from exotype params
  - `:match-threshold` — from exotype params

**Test**: Run on a few known MMCA runs, verify metrics are extracted.

**Acceptance**: Function returns map with all metrics populated.

---

### M0-006: Implement tensor signature extraction

**Context**: The tensor interpretation says 乾 = projection, 坤 = identity, 泰 = convex combo.

**Task**:
- Implement `(defn run->transition-matrix [mmca-run] ...)`
  - Build P(state_t+1 | state_t) from run history (coarse-grain if needed)
- Implement `(defn transition-matrix->signature [M] ...)`
  - `:spectral-gap` — largest eigenvalue gap
  - `:projection-rank` — effective rank / attractor count
  - `:alpha-estimate` — where on identity-projection spectrum (0=坤, 1=乾)

**Test**:
- Static run → alpha near 0
- Collapse run → alpha near 1
- EoC run → alpha in (0.3, 0.7)

**Acceptance**: Signature extraction works on test runs.

---

### M0-007: Implement dynamics->hexagram classifier

**Context**: Use tensor signature to classify dynamics as hexagram.

**Task**:
- Implement `(defn signature->hexagram-class [sig] ...)`
- Classification rules (starting point):
  - `alpha > 0.8` → :qian (乾)
  - `alpha < 0.2` → :kun (坤)
  - `0.3 <= alpha <= 0.7` AND stable → :tai (泰)
  - blocked/bimodal → :pi (否)
- Return hexagram number or keyword

**Test**: Manual validation on known runs.

**Acceptance**: Classifier produces sensible hexagram labels.

---

## Phase 3: Xenotype Integration

### M0-008: Implement hexagram-based fitness function

**Context**: Hexagrams judge octagrams. 泰-like dynamics get boosted.

**Task**:
- Implement `(defn hexagram-fitness [hex-class] ...)`
- Fitness values (starting point):
  - `:tai` (泰) → 1.0 (best)
  - `:qian` (乾) → 0.3 (collapse)
  - `:kun` (坤) → 0.3 (frozen)
  - `:pi` (否) → 0.1 (blocked)
  - others → 0.5 (neutral)

**Test**: Unit tests for each hexagram class.

**Acceptance**: Fitness function exists with configurable weights.

---

### M0-009: Hook hexagram classifier into MMCA selection

**Context**: Use hexagram fitness as xenotype signal during evolution.

**Task**:
- Find where MMCA does exotype selection/fitness
- Add hook: after evaluating run, compute hexagram class
- Add hexagram fitness to selection signal
- Make it toggleable (config flag to enable/disable)

**Test**: Run MMCA with hexagram xenotype enabled. Verify fitness signal is applied.

**Acceptance**: MMCA can run with hexagram-based selection pressure.

---

### M0-010: Implement PSR/PUR logging

**Context**: Track pattern selection records and pattern use records.

**Task**:
- Create logging for:
  - PSR: `{:sigil X :exotype-params {...} :predicted-hexagram Y :reason "..."}`
  - PUR: `{:sigil X :actual-dynamics {...} :actual-hexagram Z :match? bool}`
- Log to EDN file for analysis
- Include timestamp, generation, seed

**Test**: Run MMCA, verify PSR/PUR logs are written.

**Acceptance**: PSR/PUR log file is created with correct structure.

---

## Phase 4: Validation

### M0-011: Easy mode — long batch runs

**Context**: Test if ratchet naturally finds 泰 zone.

**Task**:
- Set up long MMCA run (1000+ generations)
- Enable hexagram xenotype
- Track hexagram distribution over generations
- Plot: % of population in each hexagram class over time

**Test**: Does the population converge toward 泰?

**Acceptance**: Can run long batch and produce hexagram distribution plot.

---

### M0-012: Build Joe judgment collection tool

**Context**: Need ground truth — Joe's EoC judgments.

**Task**:
- Create simple CLI/UI for labeling MMCA runs
- Show visualization of run
- Joe inputs: EoC / not-EoC / borderline
- Save to labeled dataset file

**Test**: Can label 10 runs and save results.

**Acceptance**: Tool exists and produces labeled dataset.

---

### M0-013: Compare hexagram predictions to Joe judgments

**Context**: Validate that hexagram classifier matches human judgment.

**Task**:
- Load labeled dataset from M0-012
- Run hexagram classifier on each
- Compute:
  - Accuracy (does 泰 = EoC?)
  - Confusion matrix
  - False positive/negative rates

**Test**: Accuracy > 70% on initial dataset.

**Acceptance**: Report comparing hexagram classifier to Joe's labels.

---

## Phase 5: Advanced (if time permits)

### M0-014: Implement 否 (Pi) hexagram detection

**Context**: 否 = obstruction, blocked dynamics. Need to detect this.

**Task**:
- Research what "blocked" looks like in MMCA (disconnected components? oscillation?)
- Implement detection in `signature->hexagram-class`
- Test on runs that "feel stuck"

**Acceptance**: Can identify 否-class runs.

---

### M0-015: Add more diagnostic hexagrams

**Context**: Beyond 乾/坤/泰/否, other hexagrams have meaning.

**Task**:
- Implement detection for:
  - ䷄ 需 (5, Waiting) — slow buildup
  - ䷌ 同人 (13, Fellowship) — convergent agreement
  - ䷍ 大有 (14, Great Possession) — flourishing diversity
- Add to classifier

**Acceptance**: Classifier handles extended hexagram set.

---

### M0-016: Medium mode — ant battles as xenotype

**Context**: Periodically evaluate top exotypes in ant arena.

**Task**:
- Wire MMCA to call ant arena every N generations
- Select top-K exotypes for evaluation
- Feed survival/stability back as fitness signal
- Compare to hexagram-only xenotype

**Test**: Does ant xenotype find different/better EoC than hexagram xenotype?

**Acceptance**: Can run MMCA with ant battle xenotype.

---

## Notes for Codex

- Start with Phase 1 — get the matrix/hexagram math working first
- Phase 2 depends on existing MMCA run infrastructure — check what's already there
- Ask Joe if stuck on hexagram interpretation
- The 泰 zone is approximately `alpha ∈ [0.3, 0.7]` — this is the target
- See `futon3/library/iching/hexagram-*.flexiarg` for hexagram interpretations
