# Sigil ↔ Hexagram Mapping (Draft Spec)

This spec treats I Ching hexagrams as a low-dimensional prior over sigil behavior.
Mappings are hypotheses that can be revised by CA/ant evidence.

## Goals

- Maintain **two families** of CT diagrams:
  - **Pure hexagram CT**: text-derived, prior-only.
  - **Empirical sigil CT**: behavior-derived from MMCA/ants runs.
- Track **rewiring cost**: how much CT structure must change to align priors with evidence.
- Keep hexagrams as **labels/regimes**, not hard behavioral molds.

## Data Model (EDN)

```clojure
{:sigil "土"
 :bits "01010010"
 :hexagram {:id :hexagram-11-tai
            :confidence 0.62
            :basis :projection-8-to-6
            :evidence [:mmca/eoc-run-17a
                       :mmca/stable-band-17a
                       :ant/pilot-survival-01]}
 :ct
 {:pure-hexagram {:vision {...} :plan {...} :adapt {...}}
  :empirical-sigil {:vision {...} :plan {...} :adapt {...}}
  :rewiring
  {:morphism-edits 3
   :object-edits 1
   :weight-delta 0.42
   :notes "Adjusted mode bias and transition rate to match observed stability"}}}
```

## Projection Strategies (8-bit sigil → 6-bit hexagram)

- **projection-8-to-6**: drop or merge two bits (declared).
- **majority-pair**: map pairs of bits to 1 line by majority (8→4, then merge).
- **behavioral-fit**: ignore raw bits; assign by nearest observed regime.

Each mapping carries a `:basis` and an adjustable `:confidence`.

## Rewiring Metric

Define a minimal edit distance between CT graphs:

- **Object edits**: add/remove/rename VISION nodes.
- **Morphism edits**: add/remove/retarget morphisms.
- **Weight delta**: sum of absolute differences in plan/adapt weights.

```clojure
{:rewiring {:object-edits 1
            :morphism-edits 3
            :weight-delta 0.42}}
```

Interpretation: higher rewiring = larger divergence between hexagram prior and
empirical sigil behavior.

## Dual Families

- **Daoist cyberants** = driven by pure-hexagram CT (text-as-prior).
- **Exotic cyberants** = driven by empirical sigil CT (data-as-posterior).

This enables a “war” experiment:

- identical simulation conditions
- different CT sources
- compare survival, resource efficiency, stability, and adaptability

## Open Questions

- How to normalize rewiring across different CT graph sizes?
- Which evidence sources update confidence (MMCA vs ants vs other domains)?
- Should empirical CT ever feed back to revise hexagram templates themselves?
