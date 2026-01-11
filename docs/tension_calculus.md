# Tension Calculus (Pre-CT Requirement)

This note defines a pre-categorical substrate for FUTON5. It is a
requirement for any Category Theory (CT) build. The goal is to make
tensions explicit, measurable, and comparable before we allow
composition laws or functorial lifts.

Core premise:
- Objects are tensions, not patterns.
- Patterns are morphisms that shift tensions while partially preserving
  structure.
- Every transformation must expose its cost and relief profile.

## 1) Minimal Tension Vocabulary

Each tension has a name, observable signals, and a relief/cost profile.
Start with a minimal list and expand only when signals are stable.

Suggested seed tensions for FUTON5:
- stability vs change
  - signals: temporal autocorr, change rate
  - relief: maintain mid autocorr with non-zero change
  - cost: frozen or chaotic regimes
- diversity vs convergence
  - signals: unique sigils ratio
  - relief: sustained multi-sigil variety
  - cost: collapse to monoculture
- trail-following vs wandering
  - signals: edge-of-chaos proximity (autocorr near 0.5), change floor
  - relief: mid strength trail with forward motion
  - cost: white space (no trail), or sticky trail (stuck)
- resource discovery vs exploitation
  - signals: new sigil mass, reuse of learned sigils
  - relief: net increase of useful operators
  - cost: novelty without retention or dead reuse
- coupling vs independence (genotype/phenotype)
  - signals: genotype gate utilization, phenotype change vs genotype change
  - relief: meaningful coupling without lock-in
  - cost: locked kernel or ignored phenotype

## 2) Tension Signals and Normalization

Each tension is expressed as normalized signals in [0,1].
Example:
{:tension/id :stability-change
 :signals {:autocorr 0.53
           :change 0.31}
 :derived {:trail-score 0.42}}

Rules:
- All signals must be derived from observable data.
- Each signal has a stable normalization rule.
- Derived scores are functions of signals, not separate readings.

## 3) Cost and Relief Tradeoffs

Every operator must declare:
- relief: which tensions it reduces (improves)
- cost: which tensions it increases (worsens)
- invariants: what it must preserve

Example:
{:op/id :learned-sigil
 :relief [:resource-discovery]
 :cost [:trail-following :diversity]
 :invariants [:genotype-length]}

## 4) Comparing Incommensurate Tensions

We do not collapse all tensions into a single scalar by default.
Use one of these comparison modes:

1) Satisficing thresholds
   - Each tension has a minimum acceptable floor.
   - A candidate must meet all floors to be viable.

2) Pareto dominance
   - Prefer candidates that are no worse in all tensions and better in at least one.

3) Budgeted tradeoff
   - Allow explicit budget: how much cost can be paid per unit relief.
   - Example: "1 unit of diversity loss buys 0.5 unit of trail gain."

4) Guardrail order
   - A priority ordering of tensions for rescue when failures appear.

## 5) Integration with MMCA and AIF Scoring

Current MMCA signals map cleanly:
- temporal autocorr + change rate -> trail-following tension
- unique sigils ratio -> diversity tension
- new sigil mass -> resource discovery tension

AIF should guide search using these tensions, not just score.
If a run is weak in a tension, it should steer kernel or operator
selection to address that deficit.

## 6) Requirement for CT Build

We should not formalize CT operators until:
- Tension vocabulary is stable and documented.
- Signals are normalized and reproducible.
- Operators declare cost and relief.
- Comparison mode is explicit (not implied).

Once these are in place, CT becomes a formal layer over a shared
tension calculus, not a speculative abstraction.

## 7) Next Step (Concrete)

Implement a minimal tension registry and expose it to:
- metaevolve scoring
- operator definitions
- CT diagram annotations

This is the substrate that future CT morphisms will preserve or shift.
