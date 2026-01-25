# Wiring Design Principles from Good Band Analysis

Date: 2026-01-25
Author: Claude Code

## Empirical Findings

Analyzing the "good" (moderate activity) bands in our experiment runs revealed:

| Finding | Implication |
|---------|-------------|
| 90%+ show **bursty** pattern | Don't design for regular oscillation — design for intermittent activity |
| 100% show **phe-leads** correlation | Phenotype should have some independence from genotype |
| Good bands cluster OR buffer | Self-sustaining regions AND boundary dynamics both matter |
| Seed 352362012 has most moderate bands (19) | Its moderate columns neighbor chaotic columns — boundary effect |

## Design Principles

### Principle 1: Allow Bursts, Not Forced Oscillation

**Finding**: Good columns have long stable runs punctuated by bursts of change. They're not metronomes.

**Wiring implication**:
- Include a "stability bias" that resists change by default
- Change should require multiple triggers or threshold crossing
- Don't force regular state flipping

**Possible mechanism**:
```
IF change_pressure > threshold THEN change
ELSE maintain_stability

;; Where threshold is relatively high (requires multiple factors)
```

### Principle 2: Phenotype Semi-Independence

**Finding**: Phenotype changes lead genotype changes. The layers aren't tightly coupled.

**Wiring implication**:
- Phenotype shouldn't just be a deterministic function of genotype
- Consider: phenotype has its own dynamics that genotype influences but doesn't control
- This creates a "lag" that allows exploration

**Possible mechanism**:
```
new_phenotype = IF random < coupling_strength
                THEN derive_from_genotype(...)
                ELSE evolve_independently(...)

;; Where coupling_strength ~ 0.3-0.5 (partial coupling)
```

### Principle 3: Boundary-Aware Behavior

**Finding**: Good bands often occur at the boundary between chaotic and frozen regions.

**Wiring implication**:
- Cells should sense their neighborhood type (chaotic vs frozen vs moderate)
- Behavior should adapt based on context:
  - Next to frozen: increase activity (prevent freezing)
  - Next to chaotic: increase stability (prevent chaos spreading)
  - Next to moderate: maintain (join the good zone)

**Possible mechanism**:
```
neighbor_activity = measure_neighbor_change_rates()

IF neighbors_mostly_frozen
  THEN bias_toward_change
ELSE IF neighbors_mostly_chaotic
  THEN bias_toward_stability
ELSE
  maintain_current_behavior
```

### Principle 4: Cluster Formation

**Finding**: Moderate columns tend to neighbor other moderate columns.

**Wiring implication**:
- Moderate activity is "contagious" in a good way
- Design for positive feedback when neighbors are moderate
- This creates self-sustaining EoC islands

**Possible mechanism**:
```
IF neighbor_is_moderate
  THEN increase_own_stability_in_moderate_zone
  ;; Don't go chaotic, don't freeze — match neighbors
```

## Proposed Wiring: "Boundary Guardian"

Combining these principles into a single wiring:

```
;; Sense neighborhood
diversity = measure_neighbor_diversity()
neighbor_type = classify_neighbors(frozen/chaotic/moderate)

;; Adapt behavior
base_change_prob = CASE neighbor_type OF
  :frozen   -> 0.4  ;; Higher activity to prevent spread of freeze
  :chaotic  -> 0.1  ;; Lower activity to resist chaos
  :moderate -> 0.25 ;; Maintain moderate activity

;; Apply burst dynamics (don't change every tick)
change_threshold = 0.3
actual_change = IF (random < base_change_prob) AND (pressure > change_threshold)
                THEN compute_new_value()
                ELSE maintain()

;; Phenotype semi-independence
phenotype_update = IF random < 0.4
                   THEN follow_genotype_rule()
                   ELSE independent_local_evolution()
```

## Testing the Hypothesis

To validate these principles:

1. **Build the "Boundary Guardian" wiring** implementing the mechanisms above

2. **Compare band metrics**:
   - Does it produce more moderate columns than existing wirings?
   - Do moderate bands cluster appropriately?
   - Does it maintain bursty (not oscillating) dynamics?

3. **Visual inspection**: Does it look like "coral reef" rather than "galaxy"?

## Relation to Hexagram Lifting

The I Ching hexagram system could map these behaviors:

| Hexagram | Suggested Behavior |
|----------|-------------------|
| 泰 (Peace) | Target state — moderate activity, balanced |
| 乾 (Creative) | Use when neighbors are frozen — inject novelty |
| 坤 (Receptive) | Use when neighbors are chaotic — absorb and stabilize |
| 否 (Stagnation) | Avoid — represents frozen state |
| 革 (Revolution) | Transition mode — shift between regimes |

The lifting strategy could select hexagrams based on detected neighbor type, implementing the "Boundary Guardian" behavior through the existing hexagram semantics.
