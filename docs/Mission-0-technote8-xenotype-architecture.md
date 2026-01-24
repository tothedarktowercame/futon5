# Mission 0 Technote 8 — Xenotype Architecture

Date: 2026-01-24

## What is a Xenotype?

**Without xenotypes there is no exotic programming.**

Xenotypes are the mechanism for evolving global rules to search for the best behavior.

## Current vs Proposed Architecture

### Current Implementation

Xenotypes are **fitness functions** (scoring specs):

```clojure
{:weights {:edge 0.7 :diversity 0.3}
 :targets {:entropy [0.6 0.35]
           :change [0.2 0.2]
           :autocorr [0.6 0.3]}
 :penalties {:stasis-after 6
             :confetti-change 0.45}}
```

The xenoevolve loop:
1. Run MMCA with random exotypes
2. Score runs against xenotype specs
3. Evolve the xenotype specs (mutate weights/targets)

### Proposed Architecture

Xenotypes are **global exotype rules** that get evolved:

```
Xenotype = global rule (0-255) that bends all local physics
         + composition mode (:sequential, :blend, :matrix)
         + optional fitness target spec

Evolution searches the 256-rule space for best behavior.
```

## The Xenotype Lift (Parallel to Exotype Lift)

Just as exotypes lift from 36-bit context → physics rule:

```
Exotype lift:
  36-bit context (LEFT/EGO/RIGHT/NEXT/PHE)
    → eigenvalue diagonalization
    → hexagram + energy
    → rule (0-255)
```

Xenotypes could lift from **run history** → evaluation criteria:

```
Xenotype lift:
  Run history (gen-history, phe-history, metrics)
    → summarize dynamics (entropy, change, autocorr)
    → map to hexagram (which regime?)
    → derive target rule for evolution
```

## Three Xenotype Modes

### Mode 1: Xenotype as Evolved Global Rule

```clojure
(defn xenotype-evolve-global
  "Evolve global exotype rules to find best physics."
  [fitness-fn initial-population]
  ;; Population of global rules (0-255)
  ;; Each rule tested by running MMCA with that rule bending local physics
  ;; Fitness evaluated on run outcomes
  ;; Evolve toward best rules
  ...)
```

### Mode 2: Xenotype as Fitness Function (current)

```clojure
(defn xenotype-fitness-spec
  "Xenotype defines what 'good' means."
  [xeno run-result]
  ;; Score run against xenotype's targets
  ...)
```

### Mode 3: Xenotype as Lifted Evaluation

```clojure
(defn xenotype-lift
  "Lift run dynamics to evaluation hexagram."
  [run-result]
  (let [summary (metrics/summarize-run run-result)
        ;; Map summary metrics to a "virtual" 36-bit context
        virtual-context {:left (entropy->bits (:avg-entropy-n summary))
                         :ego (change->bits (:avg-change summary))
                         :right (autocorr->bits (:temporal-autocorr summary))
                         :next (diversity->bits (:avg-unique summary))
                         :phenotype (regime->bits (classify-regime summary))}
        ;; Lift to hexagram
        hexagram (hex-lift/exotype->hexagram virtual-context)]
    hexagram))
```

This would give each run a "hexagram signature" based on its dynamics, enabling:
- Clustering runs by hexagram
- Evolving toward specific hexagram targets
- Using I Ching semantics for interpretation

## Exotic Programming Flow

```
1. Select global xenotype(s) — either manually or evolved
   e.g., :baldwin, :transformative, or rule 148

2. Run MMCA with global+local bending
   - Global xenotype bends all local exotypes
   - Each cell still computes local 36-bit physics

3. Evaluate run with xenotype fitness criteria
   - Did it achieve edge-of-chaos?
   - Did it avoid stasis/confetti?

4. Xenotype evolution (outer loop)
   - Mutate global rules
   - Select best performers
   - Converge on optimal xenotype for this fitness landscape
```

## Connection to CT Templates

The exotype-xenotype-lift.edn registry maps sigils to CT templates:

```clojure
{:sigil "一"
 :pattern-id "paramitas/discernment"
 :ct-template (category {:name :exotic/vision-...
                         :objects [:constraint :future :start ...]
                         :morphisms {:m-next [:action :future] ...}})}
```

The CT template could define the **evaluation structure**:
- Objects = dimensions being measured
- Morphisms = relationships between dimensions
- Composition = how to combine scores

## Implementation Path

1. **Add `xenotype-as-global-rule`** function
   - Takes a rule number or keyword
   - Returns a function that bends local physics

2. **Add `evolve-global-xenotype`** function
   - Population of global rules
   - Fitness evaluated on MMCA outcomes
   - Evolve toward best rules

3. **Add `xenotype-lift`** function
   - Map run dynamics to hexagram
   - Use for clustering/analysis

4. **Integrate with existing xenoevolve**
   - Option to evolve rules vs. evolve fitness specs
   - Or evolve both simultaneously

## Open Questions

1. Should xenotypes evolve in the 256-rule space, or in a higher-dimensional space of rule combinations?

2. How do CT templates drive xenotype evaluation? Are the morphisms scoring weights?

3. Can we compose multiple xenotypes the same way we compose exotypes?

4. What's the relationship between xenotype fitness and the exotype-xenotype-lift registry?
