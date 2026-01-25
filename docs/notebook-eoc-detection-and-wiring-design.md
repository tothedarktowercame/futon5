# Notebook: EoC Detection and Wiring Design from First Principles

Date: 2026-01-25
Authors: Claude Code + Codex (collaborative)

## The Problem We're Solving

We have sophisticated wiring composition machinery, but runs keep going "hot" — visually galaxy-like rather than coral-reef EoC. We need to:
1. Detect "hot" automatically (not just collapsed)
2. Understand what makes "good" bands work
3. Design new wirings from first principles

## Key Discovery: "Hot" = Chaotic, Not Collapsed

Codex's hybrid experiment revealed the real issue:

| Wiring | avg-change | Status |
|--------|------------|--------|
| Legacy 工 | 0.25-0.37 | **Moderate** — EoC candidate |
| Prototype-001 | **0.995** | **Chaotic** — hot! |
| Hybrid | 0.995 | Fallback not triggering |

**The white-ratio detector misses this.** A run with 50% white and 50% black looks "healthy" — but if every cell flips every tick, it's pure noise. The prototype isn't collapsed, it's **maximally chaotic**.

### New Detection Rule

```
IF avg-change > 0.6 THEN status = :chaotic (hot)
IF avg-change < 0.05 THEN status = :frozen
IF 0.15 < avg-change < 0.45 THEN status = :moderate (EoC candidate)
```

This captures what white-ratio misses.

## Vertical Band Analysis: What Makes Good Bands Good?

Analyzing columns with moderate change rate (0.15-0.45) revealed:

| Finding | % Observed | Implication |
|---------|------------|-------------|
| Bursty pattern | 90%+ | Design for intermittent activity, not oscillation |
| Phenotype leads | 100% | phe should have partial independence from gen |
| Moderate neighbors | ~50% | Good bands cluster together |
| Chaotic neighbors | ~30% | Good bands also buffer at boundaries |

### The "Good Band" Recipe

1. **Bursts, not metronomes**: Long stable runs punctuated by change
2. **Phenotype semi-independence**: ~30-50% coupling to genotype
3. **Boundary awareness**: Sense neighbor type, adapt behavior
4. **Cluster formation**: Moderate activity is "contagious"

## Wiring Design Principles

### Principle 1: Stability Bias

**Problem**: Prototype-001 changes 99.5% of cells per tick.
**Solution**: Default to stability; require threshold crossing to change.

```
change_prob = base_prob * pressure_factor
IF random > change_prob THEN maintain_current_value
```

### Principle 2: Neighborhood-Adaptive Behavior

**Observation**: Good bands form at boundaries between chaotic and frozen regions.

```
neighbor_type = classify_neighbors()
behavior = CASE neighbor_type OF
  :frozen  -> increase activity (prevent spread)
  :chaotic -> increase stability (resist chaos)
  :moderate -> maintain (join the good zone)
```

### Principle 3: Phenotype Decoupling

**Observation**: Phenotype leads genotype in all good bands.

```
;; Don't tightly couple phenotype to genotype
new_phe = IF random < 0.4
          THEN derive_from_rule(gen)
          ELSE local_evolution(current_phe)
```

## Why Codex's Hybrid Didn't Work

The hybrid falls back to legacy 工 when diversity < 0.45. But:
- Prototype-001 produces chaotic diversity (everything changes)
- The diversity score is probably HIGH, not low
- So the fallback never triggers

**Fix**: The threshold should be on **change rate** or **stability**, not diversity. Fall back when things are too chaotic, not when they're too uniform.

## Proposed: "Boundary Guardian" Wiring

```clojure
{:diagram
 {:nodes [{:id :neighbors :component :context-neighbors}
          {:id :stability :component :measure-change-rate}
          {:id :legacy :component :legacy-kernel-step
           :params {...工 params...}}
          {:id :creative :component :mutate}
          {:id :chooser :component :threshold-sigil}]

  :edges [;; Measure neighbor stability
          {:from :neighbors :to :stability}

          ;; Choose based on CHANGE RATE, not diversity
          {:from :stability :from-port :change-rate :to :chooser :to-port :score}
          {:value 0.4 :to :chooser :to-port :threshold}  ;; If change > 0.4, use legacy
          {:from :legacy :to :chooser :to-port :above}   ;; Stable path
          {:from :creative :to :chooser :to-port :below} ;; Creative path
          ]}}
```

**Logic**:
- When neighbors are changing too fast (chaotic), fall back to 工's stable behavior
- When neighbors are moderate/stable, allow creative exploration

## Hexagram Mapping

The I Ching system could implement this naturally:

| Neighbor State | Hexagram | Behavior |
|----------------|----------|----------|
| Chaotic (change > 0.5) | 坤 (Receptive) | Absorb, stabilize |
| Moderate (0.15-0.45) | 泰 (Peace) | Maintain balance |
| Frozen (change < 0.1) | 乾 (Creative) | Inject novelty |

This is **boundary guardian behavior** expressed through hexagram selection.

## Next Steps

1. **Add change-rate to classifier**: The existing classifier should flag chaotic runs
2. **Build Boundary Guardian wiring**: Implement the change-rate-based fallback
3. **Test on known seeds**: Compare against legacy 工 and prototype-001
4. **Visual validation**: Confirm coral-reef vs galaxy appearance

## Summary

| What We Learned | How It Helps |
|-----------------|--------------|
| "Hot" = chaotic, not collapsed | Better detection (avg-change > 0.6) |
| Good bands are bursty | Design for intermittent activity |
| Phenotype leads genotype | Allow partial decoupling |
| Good bands form at boundaries | Neighborhood-adaptive behavior |
| Hybrid threshold was wrong | Use change-rate, not diversity |

The wiring infrastructure isn't broken — we were just measuring the wrong thing and designing fallbacks for the wrong condition.

---

## Visual Inspection Results (2026-01-25)

Human review of `docs/inspection-gallery.org` revealed a different failure mode:

### Key Observations

1. **Prototype-001 (seed 352362012)**: "Coral at first and in some locales, but then produces barcode stripes (Newtonian physics)"

2. **Legacy 工 (seed 352362012)**: "Similar but somewhat worse"

3. **Vertical bands**: "Visible bands of interest but they repeat" — periodic/oscillatory, not sustained EoC

4. **Exotype layer**: "Structure, but of the barcode variety — only 3-4 active exotypes"

5. **Boundary Guardian vs Legacy**: "Quite similar"

### New Failure Mode: Barcode Collapse

The system isn't staying "hot" (chaotic) — it's collapsing into **low-dimensional attractors**:
- Starts with EoC ("coral at first")
- Degenerates into repeating stripes ("barcode")
- Exotype diversity collapses to only 3-4 active rules

This is a different problem than we thought:
- Not chaotic (high change-rate)
- Not frozen (zero change)
- **Periodic/deterministic** — found a simple attractor

### Detection Ideas

1. **Exotype diversity**: Count unique active exotypes. If < 5, flag as "barcode collapse"

2. **Periodicity detection**: Look for repeating row patterns. If rows repeat with period P < 10, flag.

3. **Spatial autocorrelation**: Barcode patterns have high horizontal autocorrelation. Coral reef has moderate.

### Wiring Implications

To prevent barcode collapse:
- **Inject noise when periodicity detected**: Break out of attractors
- **Maintain exotype diversity**: Don't let the system settle on 3-4 rules
- **Reward spatial complexity**: Penalize horizontally uniform patterns

This is closer to the original 泰 zone insight: the system needs to stay in a regime where it doesn't collapse to simple attractors.

---

## Barcode Detector Results (2026-01-25)

Implemented `scripts/barcode_detector.clj` to automatically detect vertical barcode collapse.

### Key Insight: Vertical Freezing

The "barcode" pattern is **vertical column freezing** — columns that maintain the same value throughout late generations. This creates vertical stripes in the visualization.

Detection method:
```clojure
(defn column-frozen? [history col-idx n]
  (let [last-n (take-last n history)
        col-vals (map #(nth % col-idx) last-n)]
    (apply = col-vals)))
```

### Results: All Runs Show Barcode Collapse

| Wiring | Seed | Frozen % | Stripes | Max Width |
|--------|------|----------|---------|-----------|
| Legacy 工 | 4242 | 74% | 12 | 17 |
| Legacy 工 | 238310129 | 92% | 9 | 28 |
| Legacy 工 | 352362012 | 93% | 6 | 31 |
| Boundary Guardian | 4242 | 79% | 10 | 22 |
| Boundary Guardian | 238310129 | 87% | 13 | 25 |
| Boundary Guardian | 352362012 | 87% | 8 | 35 |
| Prototype-001 | 4242 | 88% | 9 | 46 |
| Prototype-001 | 238310129 | 93% | 8 | 33 |
| Prototype-001 | 352362012 | 93% | 6 | 33 |

**Every single run collapses to barcode by generation 100.**

### Full Classification Scheme

The detector now identifies **both** failure modes and the sweet spot:

```
IF final-change-rate > 0.6 THEN :hot        -- Too chaotic (galaxy)
IF frozen-ratio > 0.7       THEN :barcode   -- Too collapsed (stripes)
IF frozen-ratio > 0.5       THEN :cooling   -- Trending toward barcode
IF final-change-rate < 0.1  THEN :frozen    -- Nearly static
IF 0.15 < change-rate < 0.45 AND frozen < 0.5 THEN :eoc-candidate
ELSE :unknown
```

**The Regime Map**:
```
                    ← more stable    more chaotic →

      FROZEN        BARCODE         EoC           HOT
      (static)      (stripes)       (coral)       (galaxy)
      <5% change    <10% change     15-45%        >60%
      --% frozen    >70% frozen     <50% frozen   few frozen
```

### The Real Problem: Cooling, Not Hot

Updated analysis with change rates reveals the trajectory:

| Run | Early Change | Final Change | Frozen % | Status |
|-----|--------------|--------------|----------|--------|
| legacy-352362012 (w=120) | 31% | **22%** | 55% | **COOLING** |
| proto-352362012 (w=120) | 30% | **21%** | 58% | **COOLING** |
| legacy-4242 (w=100) | 26% | 11% | 74% | BARCODE |
| proto-4242 (w=100) | 27% | 0.7% | 88% | BARCODE |
| boundary-4242 (w=100) | 25% | 7% | 79% | BARCODE |

**Key insight**: All runs START in the EoC zone (17-31% early change rate) but COOL into barcode (0.4-11% final, 74-93% frozen).

The "hot" failure mode (>60% change rate) was not observed in these experiments. Codex's 0.995 report may have been:
- Different wiring configuration
- Measured at different time (early generations?)
- Different measurement (per-cell vs per-generation?)

**Two Distinct Failure Modes**:

1. **Hot** (galaxy-like): Too chaotic, >60% cells change per generation, noisy/random appearance. We haven't reproduced this in current experiments but it's a real failure mode that Codex observed.

2. **Barcode** (stripe-like): Collapsed to attractors, columns freeze, deterministic patterns. This is what we're seeing in all current runs.

**EoC is the narrow band between them** — sustained moderate activity (15-45% change) without freezing into patterns or exploding into noise. The current experiments show runs that START in EoC territory but COOL into barcode.

### Next Direction: Attractor Escape

To sustain EoC, we need mechanisms that:
1. **Detect freezing in progress**: Monitor per-column change rate during run
2. **Inject perturbation**: When a column starts freezing, inject noise
3. **Maintain diversity pressure**: Penalize uniform neighborhoods

This is a **homeostatic control problem**: the wiring needs a feedback loop that detects and resists collapse.

### Cross-Experiment Analysis

Running the detector on all available experiment data (24 runs):

| Experiment | Runs | Collapsed | Healthy | Notes |
|------------|------|-----------|---------|-------|
| Boundary Guardian | 9 | 9 (100%) | 0 | Width=100, all collapse |
| Wiring Comparison | 10 | 8 (80%) | 2 | Width=120, seed 352362012 resists |
| Update-prob | 6 | 6 (100%) | 0 | Width=120, all collapse |

**Seed 352362012 is special**: On width=120, both legacy (55%) and prototype-001 (58%) stay below the 70% threshold. This seed may have structural properties that resist collapse.

**Width matters**: Narrower grids (100) collapse more completely than wider grids (120). This suggests boundary effects help sustain activity.

**Update-prob has minor effect**: 0.40 vs 0.50 vs 0.60 all collapse to similar levels (74-84% frozen).

---

## Proposed Next Experiments

Based on barcode detector findings, here are concrete experiments ordered by expected insight:

### Experiment 1: Width Scaling Study
**Hypothesis**: Wider grids resist collapse because boundary effects break up attractor basins.
```
Widths: 80, 100, 120, 160, 200
Seeds: 4242, 352362012
Measure: Frozen column % at gen 100, 200, 300
```
**Why**: Seed 352362012 resists on width=120 but collapses on width=100. This is a cheap experiment (same wiring, different grid size).

### Experiment 2: Generation Depth Study
**Hypothesis**: All runs collapse eventually; seed 352362012 just takes longer.
```
Width: 120
Seeds: 352362012 (healthy), 4242 (collapsed)
Generations: 100, 200, 500, 1000
Measure: Frozen column % trajectory over time
```
**Why**: We need to know if collapse is inevitable or if some configurations sustain indefinitely.

### Experiment 3: Perturbation Injection
**Hypothesis**: Injecting noise when freezing detected can break out of attractors.
```
Implementation:
1. Monitor per-column change rate during run (not just at end)
2. When column change rate drops below 0.05 for 10 consecutive gens, inject noise
3. Noise = flip cell with probability 0.1

Measure: Does this sustain EoC or just delay collapse?
```
**Why**: This is the homeostatic control approach. If it works, it can be lifted into wiring design.

### Experiment 4: Seed 352362012 Autopsy
**Hypothesis**: Seed 352362012 has structural properties (initial genotype pattern) that resist collapse.
```
Analysis:
1. Extract initial genotype pattern from seed 352362012
2. Compare to collapsed seeds (4242, 238310129)
3. Look for: symmetry, information content, spatial frequency

Measure: What is structurally different?
```
**Why**: If we understand WHY this seed resists, we can design wirings that produce similar initial conditions.
