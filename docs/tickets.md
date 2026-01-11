# Tickets

## Meta-pattern follow-ups

- Implement MMCA operator hooks for draft sigils in `docs/meta_patterns.md`:
  - White Space Scout (川)
  - Hunger Precision (义)
  - Facet (面)
  - HyperAnt (代)
  - Accumulator (付)
- Add CLI/config examples per entry in `docs/meta_patterns.md`.

## AIF / Pheromone Scoring Directions

- Trail evaporation: decay reward for long autocorr streaks so stuck regimes lose value over time.
- Foraging efficiency: reward new sigils that persist for N generations (food that makes it back to nest).
- Trail branching: bonus for multiple mid-strength autocorr regimes; penalize a single dominant regime.
- Colony health: tie phenotype stability to trail quality without freezing (healthy colony signal).
- White-space penalty: explicit penalty when change-rate stays below a floor for too long.
