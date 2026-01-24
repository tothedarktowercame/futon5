# MetaCA Terminal Vocabulary v2

This vocabulary defines the **operational primitives** that xenotypes compose.
It replaces the previous vocabulary which listed derived metrics but not the
atomic operations needed for xenotype wiring.

## Design Principle

Xenotypes are **wirings** of primitives, not just labels. The I Ching patterns
(64 hexagrams × 4 energies = 256 xenotypes) define how to compose these
primitives into executable policies.

```
sigils define operations on sigils (genotype level)
exotypes define operations on exotypes (local policy)
xenotypes define how to wire exotype operations (global policy)
```

---

## Level 1: Context (What a Cell Can See)

### Immediate Neighborhood

| Primitive | Type | Description |
|-----------|------|-------------|
| `pred` | sigil | Left neighbor (position x-1) |
| `self` | sigil | Current cell (position x) |
| `succ` | sigil | Right neighbor (position x+1) |
| `prev` | sigil | Previous generation at this position (t-1, x) |
| `phe` | 4-bit | Phenotype bits at this position |

This is the standard 36-bit exotype context: 4 sigils × 8 bits + 4 phenotype bits.

### Extended Context (Light Cone)

| Primitive | Type | Description |
|-----------|------|-------------|
| `gp-left-2` | sigil | Grandparent at (t-2, x-2) |
| `gp-left-1` | sigil | Grandparent at (t-2, x-1) |
| `gp-center` | sigil | Grandparent at (t-2, x) |
| `gp-right-1` | sigil | Grandparent at (t-2, x+1) |
| `gp-right-2` | sigil | Grandparent at (t-2, x+2) |
| `light-cone` | [sigil] | All 5 grandparents as vector |

Light cone expansion: each cell can see its causal past (5 cells from t-2
that could have influenced the 3 parents at t-1).

### Hidden State (Per-Cell Memory)

| Primitive | Type | Description |
|-----------|------|-------------|
| `accum` | float | Accumulated signal (exponential decay) |
| `count` | int | Tick counter (resets on trigger) |
| `bias` | 8-bit | Preferred output pattern |
| `mode` | keyword | Current operating mode |

Hidden states persist across generations and allow cells to have memory
and preferences.

### Global Context

| Primitive | Type | Description |
|-----------|------|-------------|
| `tick` | int | Current generation number |
| `population` | [sigil] | Full genotype string |
| `phe-population` | [bit] | Full phenotype string |
| `global-entropy` | [0,1] | Population entropy |
| `global-diversity` | [0,1] | Population diversity |
| `global-mode` | keyword | Inferred global regime |

---

## Level 2: Sigil Operations (Atomic)

### Representation

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `bits-for` | sigil → 8-bit | Extract bit representation |
| `entry-for-bits` | 8-bit → sigil | Find nearest sigil for bits |
| `sigil-index` | sigil → [0,255] | Numeric index of sigil |
| `index-sigil` | [0,255] → sigil | Sigil at index |

### Bitwise Operations

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `bit-xor` | sigil × sigil → sigil | XOR (addition mod 2) |
| `bit-and` | sigil × sigil → sigil | AND (multiplication) |
| `bit-or` | sigil × sigil → sigil | OR |
| `bit-not` | sigil → sigil | NOT (flip all bits) |
| `bit-shift-left` | sigil × n → sigil | Rotate left n positions |
| `bit-shift-right` | sigil × n → sigil | Rotate right n positions |

### Aggregation Operations

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `majority` | [sigil] → sigil | Per-bit majority vote |
| `minority` | [sigil] → sigil | Per-bit minority vote |
| `blend` | [sigil] × [weight] → sigil | Weighted blend → nearest sigil |
| `random-pick` | [sigil] → sigil | Random selection from set |
| `modal` | [sigil] → sigil | Most frequent sigil |

### Comparison Operations

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `similarity` | sigil × sigil → [0,1] | 1 - (hamming distance / 8) |
| `distance` | sigil × sigil → [0,8] | Hamming distance |
| `same?` | sigil × sigil → bool | Exact equality |
| `balance` | sigil → [-1,1] | (1-bits - 0-bits) / 8 |

### Mutation Operations

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `mutate` | sigil × rate → sigil | Flip each bit with probability |
| `mutate-toward` | sigil × target × rate → sigil | Bias mutation toward target |
| `crossover` | sigil × sigil × point → sigil | Single-point crossover |
| `uniform-crossover` | sigil × sigil × rate → sigil | Per-bit crossover |

---

## Level 3: String/Population Operations

### Entropy and Diversity

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `entropy` | [sigil] → [0,1] | Shannon entropy (normalized) |
| `diversity` | [sigil] → [0,1] | Unique count / length |
| `evenness` | [sigil] → [0,1] | How evenly distributed |
| `dominance` | [sigil] → [0,1] | Frequency of most common |

### Allele Operations

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `allele-freq` | [sigil] → {sigil → count} | Frequency table |
| `allele-rank` | [sigil] → [sigil] | Sigils sorted by frequency |
| `rare-alleles` | [sigil] × threshold → [sigil] | Alleles below threshold |
| `common-alleles` | [sigil] × threshold → [sigil] | Alleles above threshold |

### Pattern Matching

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `match-template` | [sigil] × template → [position] | Find template occurrences |
| `match-motif` | [sigil] × motif → [position] | Fuzzy motif matching |
| `find-repeats` | [sigil] → [(start, length)] | Find repeated subsequences |
| `autocorr` | [sigil] → [0,1] | Spatial autocorrelation |

### Aggregate Comparisons

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `hamming-dist` | [sigil] × [sigil] → int | Total bit differences |
| `change-rate` | [sigil] × [sigil] → [0,1] | Normalized change |
| `drift` | [[sigil]] → [0,1] | Directional change over time |

---

## Level 4: Composition Modes

### Sequential Composition

```
A ; B
```

Apply operation A, then apply operation B to the result.

Example: `(mutate 0.1) ; (majority [pred self succ])`
→ First mutate, then take majority with neighbors.

### Parallel Composition

```
A ⊗ B → combine
```

Apply A and B independently, combine outputs.

Example: `(xor pred succ) ⊗ (blend [pred self succ]) → majority`
→ Compute XOR and blend in parallel, take majority of results.

### Conditional Composition

```
if X then A else B
```

Choose operation based on condition.

Example: `if (balance self) > 0 then (mutate-toward 0xFF) else (mutate-toward 0x00)`
→ If more 1s than 0s, push toward all-1s; otherwise push toward all-0s.

### Blend Composition

```
α·A + (1-α)·B
```

Weighted combination of two operations' outputs.

Example: `0.7·(majority neighbors) + 0.3·(self)`
→ 70% neighbor majority, 30% preserve self.

### Iterated Composition

```
repeat N times: A
```

Apply operation N times.

Example: `repeat 3: (bit-shift-left 1)`
→ Rotate left 3 positions.

### Conditional Iteration

```
while X: A
until X: A
```

Apply operation while/until condition holds.

---

## Level 5: Hidden State Operations

### Accumulation

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `accum-add` | state × value × decay → state | Add value with decay |
| `accum-reset` | state → state | Reset accumulator to 0 |
| `accum-read` | state → float | Read current accumulator |

### Thresholds and Triggers

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `threshold?` | state × level → bool | Is accumulator above level? |
| `trigger-on` | state × condition → state | Set trigger when condition |
| `triggered?` | state → bool | Check if triggered |
| `cooldown` | state × ticks → state | Prevent re-trigger for N ticks |

### Preference/Bias

| Operation | Signature | Description |
|-----------|-----------|-------------|
| `set-bias` | state × pattern → state | Set preferred output |
| `apply-bias` | sigil × state → sigil | Bias output toward preference |
| `learn-bias` | state × outcome → state | Update bias from outcome |

---

## Level 6: Xenotype Wirings

A xenotype is a **composition of primitives** defined by hexagram + energy.

### Hexagram Families (Situation Classification)

| Family | Hexagrams | Characteristic | Typical Primitives |
|--------|-----------|----------------|-------------------|
| Creative | 1-8 | Expansion, novelty | mutate, xor, diversity-seeking |
| Receptive | 9-16 | Conservation, structure | majority, blend, similarity-seeking |
| Difficulty | 17-24 | Challenge, adaptation | conditional, threshold, mode-switch |
| Youthful | 25-32 | Learning, exploration | random, crossover, light-cone |
| Waiting | 33-40 | Patience, timing | accumulate, trigger, delay |
| Conflict | 41-48 | Tension, resolution | balance, xor, difference-seeking |
| Army | 49-56 | Coordination, pattern | match-template, autocorr, allele-freq |
| Joy | 57-64 | Harmony, completion | blend, modal, evenness-seeking |

### Energy Modulation (Engagement Mode)

| Energy | Modulation | Effect on Wiring |
|--------|------------|------------------|
| Péng (掤) | Expand | Increase mutation rate, broaden light cone, prefer diversity |
| Lǚ (捋) | Yield | Decrease mutation, favor majority, preserve structure |
| Jǐ (擠) | Focus | Narrow context, threshold-based action, selective |
| Àn (按) | Push | Sustained pressure, accumulator-driven, directional bias |

### Example Xenotype Wirings

**Xenotype 4: Hexagram 1 (Creative) + Àn (Push)**
```
context: [pred, self, succ, prev]
operation:
  accum-add(global-entropy, decay=0.9)
  if accum > 0.7:
    mutate(self, rate=0.3)
  else:
    xor(pred, succ)
bias: toward high-balance sigils
```
Interpretation: Sustained creative pressure. When entropy accumulates, mutate;
otherwise differentiate from neighbors.

**Xenotype 37: Hexagram 10 (Treading) + Lǚ (Yield)**
```
context: [pred, self, succ, light-cone]
operation:
  if similarity(self, modal(light-cone)) > 0.75:
    self  # preserve if aligned with ancestry
  else:
    blend([majority(pred, self, succ), modal(light-cone)], [0.6, 0.4])
bias: toward ancestry-consistent sigils
```
Interpretation: Careful treading. Preserve when aligned with lineage,
otherwise blend toward both local and ancestral consensus.

**Xenotype 148: Hexagram 37 (Family) + Jǐ (Press)**
```
context: [pred, self, succ, phe]
operation:
  if balance(phe) > 0:
    mutate-toward(self, majority(pred, succ), rate=0.2)
  else:
    self  # hold when phenotype suppresses
hidden-state:
  track allele-freq over last 5 generations
  trigger mode-switch if rare-alleles > 0.3
```
Interpretation: Family coherence with focused adjustment. Phenotype gates
mutation. Track diversity and switch modes if too many rare alleles.

---

## Level 7: Evaluation Primitives (Global Scoring)

Evaluation primitives operate at the **xenotype level** — they require global
access to population, history, or cross-cell structure. Scorers are compositions
of these primitives and should be evolvable, not hardcoded.

### Entropy and Information

| Primitive | Signature | Description |
|-----------|-----------|-------------|
| `shannon-entropy` | [sigil] → [0,1] | Normalized Shannon entropy |
| `joint-entropy` | [sigil] × [sigil] → [0,1] | Joint entropy of two strings |
| `mutual-info` | [sigil] × [sigil] → [0,1] | Mutual information |
| `conditional-entropy` | [sigil] | [sigil] → [0,1] | H(X|Y) |
| `compressibility` | [sigil] → [0,1] | 1 - (LZ78 tokens / length) |

### Temporal Dynamics

| Primitive | Signature | Description |
|-----------|-----------|-------------|
| `change-rate` | gen(t) × gen(t-1) → [0,1] | Normalized Hamming distance |
| `temporal-autocorr` | [gen] → [-1,1] | Autocorrelation over time |
| `drift-direction` | [gen] → vector | Direction of population drift |
| `stability-window` | [gen] × window → [0,1] | Stability over window |
| `first-stasis` | [gen] → int | Tick when change-rate first → 0 |
| `regime-duration` | [gen] × regime → int | Ticks spent in regime |

### Spatial Structure

| Primitive | Signature | Description |
|-----------|-----------|-------------|
| `spatial-autocorr` | [sigil] → [-1,1] | Neighbor similarity |
| `run-lengths` | [sigil] → [int] | Lengths of constant runs |
| `boundary-count` | [sigil] → int | Number of sigil transitions |
| `cluster-sizes` | [sigil] → [int] | Sizes of same-sigil clusters |
| `symmetry` | [sigil] → [0,1] | Palindromic symmetry |

### Filament Detection

| Primitive | Signature | Description |
|-----------|-----------|-------------|
| `filament-count` | [gen] → int | Number of persistent structures |
| `filament-lengths` | [gen] → [int] | Lengths of filaments |
| `filament-stability` | [gen] × position → [0,1] | Persistence of structure at position |
| `filament-endpoints` | [gen] → [(start, end)] | Where filaments start/end |
| `glider-detect` | [gen] → [(position, velocity)] | Moving structures |

### Envelope Analysis (Phenotype)

| Primitive | Signature | Description |
|-----------|-----------|-------------|
| `phe-entropy` | [phe] → [0,1] | Phenotype entropy |
| `phe-change` | phe(t) × phe(t-1) → [0,1] | Phenotype change rate |
| `envelope-width` | [gen] × [phe] → int | Width of phenotype=1 region |
| `envelope-drift` | [[phe]] → vector | How envelope moves over time |
| `gen-phe-coupling` | [gen] × [phe] → [-1,1] | Correlation between layers |

### Regime Classification

| Primitive | Signature | Description |
|-----------|-----------|-------------|
| `regime-classify` | metrics → keyword | :static, :eoc, :chaos, :magma |
| `regime-boundary?` | metrics → bool | Near regime transition? |
| `eoc-score` | metrics → [0,1] | Edge-of-chaos quality |
| `interestingness` | metrics → [0,1] | Composite interest measure |

### Allele and Population Genetics

| Primitive | Signature | Description |
|-----------|-----------|-------------|
| `allele-spectrum` | [gen] → distribution | Frequency distribution of sigils |
| `heterozygosity` | [gen] → [0,1] | Expected heterozygosity |
| `fixation-index` | [[gen]] → [0,1] | Population structure (Fst) |
| `effective-pop-size` | [[gen]] → float | Ne from allele dynamics |
| `selection-coefficient` | sigil × [[gen]] → float | Selection on specific sigil |

### Composite Scoring

| Primitive | Signature | Description |
|-----------|-----------|-------------|
| `weighted-sum` | [score] × [weight] → score | Combine scores with weights |
| `geometric-mean` | [score] → score | Multiplicative combination |
| `threshold-gate` | score × level → {0,1} | Binary threshold |
| `band-score` | value × (center, width) → [0,1] | Score closeness to target band |
| `penalty` | condition → score | Apply penalty if condition holds |

### Scorer Composition

Just as generators compose primitives, scorers compose evaluation primitives:

```
scorer = evaluation-primitives → composition → [0,1] score
```

**Example Scorer Wiring: Edge-of-Chaos**
```
inputs: [gen-history, phe-history]
compute:
  e = shannon-entropy(current-gen)
  c = change-rate(current-gen, prev-gen)
  a = temporal-autocorr(last-10-gens)
  f = filament-count(gen-history)
score:
  band-score(e, center=0.6, width=0.3) * 0.3 +
  band-score(c, center=0.2, width=0.2) * 0.3 +
  band-score(a, center=0.5, width=0.3) * 0.2 +
  threshold-gate(f > 0, bonus=0.2)
penalties:
  if first-stasis < 10: penalty(0.5)
  if c > 0.5: penalty(0.3)  # confetti
```

**Example Scorer Wiring: Filament Quality**
```
inputs: [gen-history]
compute:
  f = filament-count(gen-history)
  l = mean(filament-lengths(gen-history))
  s = mean(filament-stability(gen-history, all-positions))
  g = glider-detect(gen-history)
score:
  (f > 0) * (
    0.4 * normalize(l, max=20) +
    0.4 * s +
    0.2 * (length(g) > 0)
  )
```

### Evolvable Scorers

Scorers should be evolvable just like generators. A xenotype population includes:

| Component | What evolves |
|-----------|--------------|
| Generator wiring | Which primitives, how composed |
| Scorer wiring | Which evaluation primitives, how combined |
| Weights | Relative importance of score components |
| Targets | Center/width for band-scores |
| Thresholds | Cutoffs for penalties/bonuses |

This means xenoevolve should evolve **both** the generator xenotypes and the
scorer xenotypes, or at least allow scorer parameters to adapt.

---

## Relation to AIF/GFE (Core Vocabulary)

| Core Concept | MetaCA Instantiation |
|--------------|---------------------|
| Observables (o) | Context primitives (pred, self, succ, light-cone, etc.) |
| Beliefs (μ) | Hidden state (accum, mode, bias) |
| Expectations (C) | Xenotype bias + global regime preference |
| Policies (π) | Xenotype wirings (hexagram + energy → composition) |
| Actions (a) | Output sigil + phenotype bit |
| Free Energy (G) | Computed from entropy, diversity, allele-freq, regime |

---

## Implementation Checklist

### Generation Side
- [ ] Implement light-cone context (grandparents)
- [ ] Implement hidden state per cell
- [ ] Implement composition operators (; ⊗ if/else blend)
- [ ] Define all 256 xenotype wirings (or generative rules for them)
- [ ] Connect xenotype selection to run-mmca

### Evaluation Side
- [ ] Audit existing scorers against Level 7 primitives
- [ ] Refactor scorers as compositions of primitives
- [ ] Implement missing evaluation primitives (filament, envelope, etc.)
- [ ] Make scorer wirings explicit and inspectable
- [ ] Enable evolution of scorer parameters (weights, targets, thresholds)
- [ ] Enable evolution of scorer structure (which primitives, how combined)

### Integration
- [ ] Xenoevolve evolves both generator and scorer xenotypes
- [ ] Verify: different xenotypes produce measurably different dynamics
- [ ] Verify: evolved scorers find runs that hardcoded scorers miss
- [ ] Verify: same xenotype produces similar dynamics across seeds

---

## Next Steps

1. **Audit current scorers** (metrics.clj, score.clj, exotic/scoring.clj) against Level 7 primitives
2. **Audit current exotype.clj** against Level 2 operations — which exist, which are missing?
3. **Refactor scorers** as composable primitive wirings
4. **Implement light-cone** in evolve-string-local
5. **Implement hidden state** infrastructure
6. **Define 8 prototype xenotypes** (one per hexagram family) for generation
7. **Define 4 prototype scorers** (one per primary energy) for evaluation
8. **Test dual evolution**: Can we evolve generators and scorers together?
