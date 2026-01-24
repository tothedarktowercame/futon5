# Mission 0 Technote 7 — Local Exotype Architecture

Date: 2026-01-24

## Current State

The current implementation:
1. Samples ONE random position from history to get a context
2. Uses that to modify the GLOBAL kernel
3. The same kernel applies uniformly to all cells

This is **kernel steering**, not **local physics**.

## Correct Architecture

Each cell should compute its own physics rule from its local 36-bit context:

```
For each cell position x:
  left   = sigil[x-1]      (8 bits)
  ego    = sigil[x]        (8 bits)
  right  = sigil[x+1]      (8 bits)
  next   = sigil_prev[x]   (8 bits, from previous generation)
  phe    = phenotype[x]    (4 bits)

  context = {left, ego, right, next, phe}  (36 bits)
       ↓
  eigenvalue diagonalization → hexagram (6 bits)
       +
  phenotype bits 0-1 → energy (2 bits)
       ↓
  physics rule (8 bits, 0-255)
       ↓
  kernel function + params
       ↓
  evolve this cell
```

## 256 Runnable Exotypes

Each physics rule maps to a specific behavior. The behaviors compose from:

### Base Kernels (existing)

| Kernel | Behavior |
|--------|----------|
| `:multiplication` | Standard CA rule application |
| `:blending` | Interpolate neighbor values |
| `:blending-baldwin` | Entropy-conditional mutation |
| `:ad-hoc-template` | Context-derived templates |
| `:mutating-template` | Self-modifying templates |

### Energy Modulation

| Energy | Effect on Kernel |
|--------|------------------|
| Péng (expand) | Higher mutation rate, prefer 1-bits |
| Lǚ (yield) | Lower mutation, preserve structure |
| Jǐ (focus) | Selective template matching |
| Àn (push) | Sustained pressure, momentum |

### Hexagram Family → Base Kernel

| Hexagram Family | Base Kernel |
|-----------------|-------------|
| 0 (1-8, Creative) | `:blending` — creative mixing |
| 1 (9-16, Taming) | `:multiplication` — conservative |
| 2 (17-24, Following) | `:ad-hoc-template` — adaptive |
| 3 (25-32, Innocence) | `:blending-mutation` — forward motion |
| 4 (33-40, Retreat) | `:blending-baldwin` — phenotype-conditional |
| 5 (41-48, Decrease) | `:collection-template` — differentiating |
| 6 (49-56, Revolution) | `:mutating-template` — transformation |
| 7 (57-64, Gentle) | `:blending` — gentle consolidation |

### Rule → Kernel + Params

```clojure
(defn rule->kernel-spec [rule]
  (let [{:keys [hexagram energy]} (rule->hexagram+energy rule)
        family (quot (dec hexagram) 8)
        base-kernel (case family
                      0 :blending
                      1 :multiplication
                      2 :ad-hoc-template
                      3 :blending-mutation
                      4 :blending-baldwin
                      5 :collection-template
                      6 :mutating-template
                      7 :blending
                      :mutating-template)
        energy-params (case (:key energy)
                        :peng {:mutation-rate 0.3 :bit-bias :yang}
                        :lu   {:mutation-rate 0.1 :bit-bias :preserve}
                        :ji   {:mutation-rate 0.2 :bit-bias :selective}
                        :an   {:mutation-rate 0.25 :bit-bias :momentum})]
    {:kernel base-kernel
     :params energy-params
     :rule rule}))
```

## Local Evolution Function

Replace global kernel with local dispatch:

```clojure
(defn evolve-sigil-local
  "Evolve a single sigil using locally-computed physics rule."
  [sigil pred next phenotype-bits prev-sigil]
  (let [;; Build 36-bit context
        context {:context-sigils [pred sigil next prev-sigil]
                 :phenotype-context phenotype-bits}

        ;; Lift to physics rule
        physics (context->physics-rule context)
        rule (:rule physics)

        ;; Get kernel + params for this rule
        {:keys [kernel params]} (rule->kernel-spec rule)
        kernel-fn (get kernels kernel)

        ;; Evolve with local physics
        result (kernel-fn sigil pred next (merge context params))]

    {:sigil (:sigil result)
     :rule rule
     :kernel kernel}))

(defn evolve-sigil-string-local
  "Evolve entire string with per-cell local physics."
  [genotype phenotype prev-genotype]
  (let [len (count genotype)
        letters (mapv str (seq genotype))
        phe-bits (seq (or phenotype (repeat len "0000")))
        prev-letters (mapv str (seq (or prev-genotype genotype)))]
    (->> (range len)
         (map (fn [idx]
                (let [pred (get letters (dec idx) default-sigil)
                      self (get letters idx)
                      next (get letters (inc idx) default-sigil)
                      phe (str (get phe-bits idx "0000"))
                      prev (get prev-letters idx self)]
                  (evolve-sigil-local self pred next phe prev))))
         (map :sigil)
         (apply str))))
```

## Composition

Two exotypes A and B can compose in several ways:

### Sequential Composition (A ; B)

```clojure
(defn compose-sequential [rule-a rule-b]
  "Apply rule-a then rule-b."
  (fn [sigil pred next context]
    (let [intermediate (evolve-with-rule rule-a sigil pred next context)]
      (evolve-with-rule rule-b (:sigil intermediate) pred next context))))
```

### Parallel Composition (A ⊗ B)

```clojure
(defn compose-parallel [rule-a rule-b]
  "Average outputs of rule-a and rule-b."
  (fn [sigil pred next context]
    (let [result-a (evolve-with-rule rule-a sigil pred next context)
          result-b (evolve-with-rule rule-b sigil pred next context)
          bits-a (bits-for (:sigil result-a))
          bits-b (bits-for (:sigil result-b))
          ;; Blend by majority vote per bit
          blended (map (fn [a b] (if (= a b) a (rand-nth [a b])))
                       bits-a bits-b)]
      (entry-for-bits (apply str blended)))))
```

### Matrix Composition

If we represent each rule as a parameter vector, composition can be matrix multiplication:

```clojure
(defn rule->param-vector [rule]
  (let [{:keys [mutation-bias structure-weight]} (rule->physics-params rule)]
    [mutation-bias structure-weight]))

(defn compose-matrix [rule-a rule-b]
  "Compose rules via parameter multiplication."
  (let [va (rule->param-vector rule-a)
        vb (rule->param-vector rule-b)]
    ;; Element-wise or matrix multiply
    ...))
```

## Implementation Path

1. **Add `rule->kernel-spec`** function that maps 256 rules to kernels + params
2. **Add `evolve-sigil-local`** that computes physics rule from local context
3. **Add `evolve-sigil-string-local`** that applies local physics per cell
4. **Integrate with MMCA runtime** as an option (`:exotype-mode :local`)
5. **Add composition operators** for combining rules

## Performance Consideration

Computing eigenvalues per cell per generation is expensive. Options:
- Cache physics rules by context hash
- Use approximate/fast eigenvalue estimation
- Compute physics rules only every N generations
- Use pre-computed lookup tables for common contexts

## Connection to P1 Gate

This architecture satisfies the P1 gate requirements:
- Physics is determined by full 36-bit structure
- Stable physics families (hexagram + energy → rule)
- Local modulation (each cell computes its own rule)
- Compositional (rules can be combined)
