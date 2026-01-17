# Exotic Programming: A Specification

**Status:** First formal specification
**Date:** 2026-01-17
**Update:** 2026-01-17 (M16–M17 findings)
**Context:** futon5 MMCA, Missions 7-8, xenotype interpretability

---

## What is Exotic Programming?

Exotic programming is a paradigm where **evaluation shapes evolution** through an interpretable, mission-structured xenotype layer.

Unlike direct optimization (maximize score), exotic programming asks:
- What is your declared intention?
- How did you plan to achieve it?
- Did you adapt coherently when evidence arrived?
- Are you worthy of your score given how you got here?

The "exotic" comes from the exotype/xenotype layers - environmental pressures that condition which genotypes are viable, evaluated through a lens that rewards **trajectory and justification**, not just outcome.

---

## Core Insight

From Mission 7/8 experiments:

> Xenotype as static evaluator produces stationary distributions, not learning.
> The system converges to an attractor, but doesn't climb through it.

The fix: xenotype must evaluate **progress through missions** with **interpretable structure**.

---

## CT Foundation

### Categories

```
Category VISION
  Objects: declared goals/intentions
  Morphisms: missions (claimed paths between goals)
  Composition: mission chaining

Category EXECUTION
  Objects: actual states (phenotypes, completed work)
  Morphisms: actions (what actually happened)
```

### Functors

```
Functor PLAN : VISION → EXECUTION
  Maps intended missions to actual execution
  Preserves composition: PLAN(m₁ ; m₂) = PLAN(m₁) ; PLAN(m₂)
  "Doing mission-1 then mission-2 should equal doing the combined mission"
```

### Natural Transformations

```
Natural Transformation ADAPT : PLAN₁ ⇒ PLAN₂
  Strategy change in light of evidence
  Naturality condition (the square must commute):

        PLAN₁(A) ───────→ PLAN₁(B)
            │                 │
    ADAPT_A │                 │ ADAPT_B
            ↓                 ↓
        PLAN₂(A) ───────→ PLAN₂(B)

  In English: adaptation must be coherent across the vision.
  You can't just change your plan for one milestone arbitrarily.
```

---

## Interpretability Payoff

The CT structure makes xenotype "reasoning" auditable:

| CT Element | Interpretable Question |
|------------|------------------------|
| Objects in VISION | What goals were declared? |
| Morphisms in VISION | What missions were proposed? |
| PLAN functor | How does intention map to execution? |
| PLAN preservation | Did execution follow the plan structure? |
| ADAPT naturality | Was the strategy change justified? |
| Composition check | Do completed missions chain to the vision? |

---

## Mission-Structured Xenotype

### Schema

```clojure
{:type :exotic-xenotype

 ;; VISION category
 :vision
 {:objects #{:start :m1 :m2 :m3 :goal}
  :morphisms {:mission-1 {:source :start :target :m1 :ask 1000}
              :mission-2 {:source :m1 :target :m2 :ask 1000}
              :mission-3 {:source :m2 :target :m3 :ask 2000}
              :mission-4 {:source :m3 :target :goal :ask 1000}}}

 ;; PLAN functor (current strategy)
 :plan {:mission-1 :in-progress
        :mission-2 :planned
        :mission-3 :planned
        :mission-4 :planned}

 ;; EXECUTION category (actual state)
 :execution {:completed []
             :current :start
             :evidence []}

 ;; ADAPT history
 :adaptations []}
```

### Evaluation Function

```clojure
(defn exotic-score [genotype xenotype-state]
  (let [vision-clarity   (score-vision-structure (:vision xenotype-state))
        plan-fidelity    (score-plan-preservation genotype xenotype-state)
        mission-progress (score-mission-completion xenotype-state)
        adapt-coherence  (score-adaptation-naturality (:adaptations xenotype-state))
        provenance       (score-path-novelty genotype (:history xenotype-state))]

    (+ (* 0.25 vision-clarity)      ; Is the vision well-formed?
       (* 0.25 plan-fidelity)       ; Does execution match plan?
       (* 0.20 mission-progress)    ; How many missions completed?
       (* 0.15 adapt-coherence)     ; Were adaptations justified?
       (* 0.15 provenance))))       ; Did you earn this score?
```

---

## The Contemplative Layer

Inspired by Buddhist contemplations, the xenotype asks:

### Five Contemplations (Provenance)

1. **"I reflect on the effort that brought this score"**
   - Track lineage: how did this genotype arrive here?
   - Reward novel paths, discount well-trodden ones

2. **"I reflect on whether I am worthy of this score"**
   - Did the genotype earn it or hack it?
   - Context-only shortcuts are penalized

3. **"I guard against greed, anger, delusion"**
   - Greed: optimizing one metric at expense of others
   - Delusion: believing delivery is guaranteed

4. **"I take this as medicine to sustain"**
   - Sustainability: does this produce viable descendants?

5. **"I accept this to continue the practice"**
   - The practice is the point, not the score

### Kammassaka (Ownership)

> "All beings are heirs to their actions"

- The genotype inherits consequences of its choices
- Xenotype tracks: do high-scorers beget high-scorers?
- Lineage matters, not just snapshot

---

## The Ratchet Mechanism

To escape stationary distributions:

### Memory

Track performance over windows, not just current score.

```clojure
{:window-size 100
 :history [{:window 1 :mean 34.2 :q50 33.8}
           {:window 2 :mean 35.1 :q50 34.9}
           ...]}
```

### Delta-Reward

Score **improvement**, not absolute value.

```clojure
(defn ratchet-score [prev-window curr-window]
  (let [delta-mean (- (:mean curr-window) (:mean prev-window))
        delta-q50  (- (:q50 curr-window) (:q50 prev-window))]
    (+ (* 0.6 delta-mean)
       (* 0.4 delta-q50))))
```

### Curriculum

Tighten constraints as windows advance.

```clojure
(defn curriculum-threshold [window-idx base-threshold]
  ;; Every 5 windows, tighten by 5%
  (let [tightening (* 0.05 (quot window-idx 5))]
    (* base-threshold (- 1.0 tightening))))
```

---

## Implementation Status (M13–M17)

- **Ratchet memory:** windowed scoring with delta-based updates is active in exotype evolution logs.
- **Curriculum tightening:** thresholds are attached per window (currently a light schedule).
- **Contemplative layer:** provenance and worthiness hooks are present but still stubs.
- **Xenotype blending:** `--xeno-weight` blends xenotype score into the short score.

### Observations

- High xenotype weights (e.g. 1.0) dominate short scores and can mask regime dynamics.
- Moderate weights (0.2–0.25) keep scores in a reasonable range and reveal regime shifts.
- Several runs show early edge-of-chaos structure that later destabilizes.
- A small subset (Mission 17a) shows a partially stable EoC band within the run window.

### Practical Note

Nomination (Mission 7) is currently treated as a **validation layer** rather than a core selection step.
Arrow detection is run **post-hoc** on logged runs to avoid slowing simulation.

---

## Nonstarter Parallel

The exotic xenotype mirrors nonstarter mechanics:

| Nonstarter | Exotic Programming |
|------------|-------------------|
| Proposal | Vision declaration |
| Milestones | Missions |
| Funding | Mission completion |
| Track record | Lineage/provenance |
| Market clearing | Score assignment |
| No delivery guarantee | Execution ≠ intention |

### Mission Decomposition

Instead of one big proposal:
```
"Build system X for £10,000"
```

Mission-structured:
```
Vision: "Build system X"
  Mission 1: Core mechanics (£1,000)
  Mission 2: Integration layer (£2,000)
  Mission 3: Testing harness (£1,000)
  ...
```

Each mission:
- Independently fundable
- Completion builds credibility
- Adaptation between missions is tracked

---

## Implementation Notes

### Word-Class Conditioning (CT Manifest)

Thresholds should vary by program word-class:

```clojure
(defn word-class-threshold [word-class base]
  (case (:frequency word-class)
    :common (* base 1.2)   ; stricter for common patterns
    :rare   (* base 0.8)   ; relaxed for exploration
    base))
```

### Context Guardrails

Prevent evaluator shortcuts:

```clojure
(defn context-robustness-check [genotype]
  (let [scores-varied-context (map #(score genotype %) (random-contexts 10))
        variance (statistical-variance scores-varied-context)]
    ;; High variance = context-dependent = suspicious
    (if (> variance threshold)
      {:flag :context-hack :penalty 0.2}
      {:flag :robust})))
```

### Adaptation Logging

Every ADAPT must be justified:

```clojure
{:adaptation-id "adapt-001"
 :from-plan :plan-v1
 :to-plan :plan-v2
 :evidence [:mission-2-harder-than-expected
            :new-dependency-discovered]
 :naturality-check :passed
 :justification "Reordered missions to derisk critical path"}
```

---

## Success Criteria

Exotic programming succeeds when:

1. **Upward drift** - windowed means show improvement, not stationarity
2. **Interpretable** - every score can be traced through CT structure
3. **Adaptive** - plan changes are logged and justified
4. **Robust** - context hacks are detected and penalized
5. **Generative** - the system produces novel high-scoring paths, not just finds known optima

---

## Open Questions

1. How to initialize VISION for a genotype that hasn't declared intentions?
2. What's the right window size for ratchet memory?
3. How strict should naturality checking be for ADAPT?
4. Can the CT structure itself evolve (meta-exotic programming)?

---

## References

- futon5/README-mission8.md - Codex findings from Mission 7/8
- futon5/resources/exotic-programming-notebook.org - experimental data
- futon5a/nonstarter-personal.md - Buddhist contemplations parallel
- futon3a/docs/tech-note-sigil-exotype-lift.md - emoji/hanzi as exotype/genotype
