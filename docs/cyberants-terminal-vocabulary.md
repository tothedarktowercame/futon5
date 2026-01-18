# Cyberants Terminal Vocabulary (Draft)

This draft inventories the concrete "terminal" levers and signals in the
Futon2 ant brain. The intent is to treat these as the atomic wiring points
for CT-backed cyberant programs (not just parameter deltas).

## Sensory Observations (ants.aif.observe/g-observe)

Normalized observation keys produced per tick:

- :food — local food density.
- :pher — local pheromone strength.
- :food-trace — neighbor mean food.
- :pher-trace — neighbor mean pheromone.
- :home-prox — closeness to friendly home.
- :enemy-prox — closeness to enemy home.
- :h / :hunger — latent hunger proxy.
- :ingest — recent ingest proxy.
- :friendly-home — 1 if standing on friendly home.
- :trail-grad — pheromone gradient magnitude.
- :novelty — inverse visit frequency (1/(1+visits)).
- :dist-home — normalized distance from home.
- :reserve-home — normalized colony reserves.
- :recent-gather — recent gather signal.
- :cargo — carried food.
- :white? — binary white-space indicator.

Vector ABI (see `ants.aif.observe/sense->vector`):
`[:food :pher :food-trace :pher-trace :home-prox :enemy-prox :h :ingest
  :friendly-home :trail-grad :novelty :dist-home :reserve-home :cargo]`

## Internal State (latent/derived)

- :mu — latent beliefs (position, goal, hunger).
- :prec — precision + temperature (`:Pi-o`, `:tau`).
- :mode — inferred phase (`:outbound`, `:homebound`, `:maintain`).
- :visit-counts — visit histogram per cell.
- :cyber-pattern — active pattern id + ticks-active.

## Action Terminals (policy outputs)

Macro actions chosen by `ants.aif.policy/choose-action`:

- :forage
- :return
- :hold
- :pheromone

Primitive world actions (war loop):

- move / step toward / random wander
- gather-food
- deposit-food
- pheromone-drop
- turn (directional change)

## Pattern Constraints (ants.aif.pattern-sense)

Pattern-derived control signals:

- :pattern/mode-aligned?
- :pattern/constraint-ok?
- :pattern/switch-cost

Pattern-specific constraint logic (examples):
- hunger coupling (h not near neutral)
- cargo return (cargo implies homebound)
- white-space (food/pher/trace low)
- pheromone tuner (novelty or trail-grad high)

## Outcome / Utility Signals

Used by `ants.aif.policy` / `ants.aif.pattern-efe`:

- expected free energy (risk, ambiguity, info gain)
- colony cost / survival cost
- action costs (pheromone, return, forage penalties)
- hunger pressure, reserve pressure
- mode-conditioned priors (C-prior)

## World Metrics (war loop)

Colony-wide signals that can be used as coarse reward:

- scores per species
- reserves per colony
- termination reasons (all-ants-dead, queen-starved, etc.)
- population counts and survival rates

## CT Hook Points (Draft)

Suggested wiring points for CT-backed cyberant programs:

- **VISION objects**: policy modes (outbound/homebound/maintain) or mission
  milestones (gather, return, maintain trail).
- **EXECUTION objects**: observation vectors + action outcomes per tick.
- **Morphisms**: action-selection transforms, mode transitions, or pattern
  constraint gates.
- **ADAPT**: pattern switches justified by constraint violations or shifts
  in observation regime (e.g., white-space detection).

This vocabulary is the minimum “terminal set” for building a structured
cyberant program that changes behavior, not just parameters.
