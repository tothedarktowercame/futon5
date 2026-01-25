# Strategy: Searching for Exotype-Layer EoC

Date: 2026-01-25

## Current State

**L5-creative (seed 352362012)** exhibits:
- **Genotype**: EoC-like dynamics (Rule 90 patterns, 50% change rate, 0% frozen)
- **Exotype**: Chaotic (turbulent, unstructured)

The creative path (XOR of neighbors) produces Rule 90 behavior in the genotype, but this doesn't translate to structured exotype patterns.

## The Rule 90 → Hexagram Connection

Rule 90 (XOR) = 01011010 in binary. Its characteristic **alternating patterns** map directly to:

| Pattern | Hexagram | Name | Meaning |
|---------|----------|------|---------|
| 101010 | #63 既濟 | Ji Ji | "Already Across" - completion, order achieved |
| 010101 | #64 未濟 | Wei Ji | "Not Yet Across" - incompleteness, potential |

**These are the final two hexagrams** in the King Wen sequence. They represent:
- The eternal oscillation between order and chaos
- The dynamic balance point where things are "almost complete" vs "not yet"
- **This IS the I Ching's structural encoding of Edge of Chaos**

Wolfram's "simplest non-trivial CA" maps to the I Ching's representation of dynamic balance. This is not coincidence - it's structural correspondence.

## Why Exotype is Chaotic

The L5-creative wiring:
```
Genotype Context → Diversity Sensor → Gate → Output
                        ↓              ↓
                  Legacy Path    Creative Path (XOR)
```

The **genotype** receives coherent input (neighboring sigils) and XOR produces structured patterns.

But the **exotype** (xenotype layer) sees:
- The OUTPUT of this process (which sigil was selected)
- Aggregated over many cells
- Through a different lens (the exotype's context window)

The exotype chaos may stem from:
1. **Phase mismatch**: Genotype dynamics and exotype sampling are out of sync
2. **Aggregation noise**: Many local decisions blur into noise at the exotype level
3. **Missing feedback**: Exotype state doesn't influence the gate decision

## Search Strategies for Exotype EoC

### Strategy 1: Threshold Sweep

**Hypothesis**: The diversity threshold (currently 0.5) may be too aggressive, causing too many switches to the creative path.

**Experiment**:
```clojure
{:thresholds [0.2 0.3 0.4 0.5 0.6 0.7 0.8]
 :metrics [:genotype-change-rate :exotype-change-rate :exotype-entropy]
 :goal "Find threshold where BOTH layers show EoC dynamics"}
```

**Prediction**: Higher thresholds (0.6-0.7) may produce more coherent exotype by using creative path more sparingly.

### Strategy 2: Exotype-Aware Gating

**Hypothesis**: The gate should consider exotype state, not just genotype diversity.

**Modification**:
```clojure
;; Current: gate on genotype diversity
{:from :diversity :from-port :score :to :gate :to-port :score}

;; Proposed: gate on exotype diversity (or combined)
{:id :exo-diversity :component :exotype-diversity}
{:from :exo-diversity :from-port :score :to :gate :to-port :score}
```

**Rationale**: If exotype is chaotic, exotype diversity is high → switch to legacy (stabilize). If exotype is ordered, diversity is low → allow creative exploration.

### Strategy 3: Modulated Creative Path

**Hypothesis**: Pure XOR is too aggressive. Mixing XOR output with legacy output might produce coherent exotype.

**Modification**:
```clojure
;; Current: pure XOR
{:id :creative :component :bit-xor}

;; Proposed: weighted mix of XOR and legacy
{:id :creative-raw :component :bit-xor}
{:id :creative-mix :component :weighted-blend
 :params {:weights [0.7 0.3]}}  ; 70% XOR, 30% legacy
```

### Strategy 4: Hexagram-Guided Search

**Hypothesis**: If Rule 90 maps to hexagrams 63/64, we should look for wirings that produce 63/64-like exotype patterns.

**Approach**:
1. Sample exotype context during runs
2. Lift to hexagram via eigenvalue diagonalization
3. Track hexagram distribution over time
4. Search for wirings where exotype hexagram stabilizes near 63/64 (or 泰/11)

**Metrics**:
```clojure
{:exotype-hexagram-entropy "Lower = more coherent"
 :exotype-hexagram-mode "Most common hexagram"
 :exotype-hexagram-63-64-ratio "Fraction in completion/incompletion"}
```

### Strategy 5: Layered Architecture

**Hypothesis**: We need separate control for genotype and exotype dynamics.

**New wiring structure**:
```
Genotype Layer:
  Genotype Context → Gate-G → Genotype Output
                       ↓
               Genotype Diversity

Exotype Layer:
  Exotype Context → Gate-E → Exotype Influence
                      ↓
               Exotype Diversity

Cross-Layer:
  Genotype Output + Exotype Influence → Final Output
```

This allows independent tuning of each layer's dynamics.

## Hexagram Space Navigation

### 泰 (Tai, #11) as Landmark

泰 = Heaven below Earth (111000) = exchange, prosperity

If L5-creative's genotype produces Rule 90 (→ 63/64), perhaps:
- **Target state**: Genotype at 63/64, Exotype at 11 (泰)
- This would represent: dynamic genotype (completion/incompletion cycle) with harmonious exotype (exchange, balance)

### The 8×8 Grid

64 hexagrams = 8 trigrams × 8 trigrams

| | ☰ | ☱ | ☲ | ☳ | ☴ | ☵ | ☶ | ☷ |
|-|---|---|---|---|---|---|---|---|
|☰| 1 | 43| 14| 34| 9 | 5 | 26| 11|
|☱| 10| 58| 38| 54| 61| 60| 41| 19|
|...| | | | | | | | |

泰 (#11) is at position (☰, ☷) - Heaven meeting Earth.

### Projection Strategy

1. Run L5-creative variants
2. Sample genotype and exotype hexagrams over time
3. Plot trajectory in 8×8 hexagram space
4. Look for variants that stabilize near target region (63/64 for geno, 11 for exo)

## Proposed Experiments

### Experiment 1: Threshold Sweep
```bash
# Modify level-5-creative.edn with different thresholds
for threshold in 0.3 0.4 0.5 0.6 0.7; do
  # Run with modified wiring
  # Collect genotype AND exotype metrics
done
```

### Experiment 2: Hexagram Tracking
Add hexagram sampling to run health analysis:
```clojure
(defn sample-hexagram [run generation]
  (let [context (sample-exotype-context run generation)]
    (context->hexagram context)))
```

### Experiment 3: Exotype Diversity Gate
Create `level-6-exotype-aware.edn`:
```clojure
{:meta {:id :level-6-exotype-aware
        :description "Gate uses exotype diversity, not genotype"}
 :diagram
 {:nodes [...
          {:id :exo-diversity :component :exotype-diversity}
          ...]
  :edges [...
          {:from :exo-diversity :from-port :score :to :gate :to-port :score}
          ...]}}
```

## Success Criteria

A successful exotype-EoC configuration should show:

| Metric | Genotype | Exotype |
|--------|----------|---------|
| Change rate | 0.15-0.45 | 0.15-0.45 |
| Frozen ratio | <0.50 | <0.50 |
| Entropy | High | Moderate |
| Hexagram mode | 63/64 | Near 11 or 63/64 |
| Band score | >0.3 | >0.2 |

## Connection to Domain Transfer

If we find exotype-EoC, the transfer to ants might be even more interesting:
- Genotype EoC → robust foraging behavior (current L5-creative)
- Exotype EoC → emergent colony-level coordination?

The exotype layer maps more closely to "policy" in the cyberant adapter. Structured exotype dynamics might translate to more coherent ant behavior.

## Next Steps

1. **Implement hexagram sampling** in run health analysis
2. **Threshold sweep** on L5-creative (0.3-0.7)
3. **Create level-6-exotype-aware** wiring
4. **Track hexagram trajectories** over run lifetime
5. **Identify candidate configurations** with dual-layer EoC
