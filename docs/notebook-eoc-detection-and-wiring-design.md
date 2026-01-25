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
