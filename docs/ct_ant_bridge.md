# CT Bridge: MetaCA → Cyber-Ants

This note proposes a CT mapping from MetaCA world objects (genotype,
phenotype, sigils) into the cyber-ant world (policy, pheromone, hunger,
telemetry). The goal is a blueprint that explains how an ant "works"
when derived from MetaCA runs.

## Source objects (MetaCA)

- `:genotype` — sigil string / rule field
- `:phenotype` — auxiliary bit register
- `:kernel` — local rule family
- `:operator-set` — active operator hooks
- `:metrics` — run metrics and summaries
- `:sigil` — atomic rule/table symbol
- `:world` — composite state (genotype + phenotype + kernel + metrics)

## Target objects (Cyber-Ants)

- `:policy` — ant action policy weights (forage/return/hold/pheromone)
- `:agent-state` — hunger/reserve/cargo-like internal state
- `:pheromone-field` — trail field representation
- `:food-field` — food distribution state
- `:nest-state` — nest or home context
- `:telemetry` — observation/summary stream
- `:world` — composite ant world state

## Object mapping (draft)

| MetaCA object   | Cyber-ant object | Rationale |
| --- | --- | --- |
| `:genotype` | `:policy` | rule tables → policy weights |
| `:phenotype` | `:agent-state` | internal register → agent state |
| `:kernel` | `:policy` | kernel family sets policy style |
| `:operator-set` | `:policy` | operators modulate policies |
| `:metrics` | `:telemetry` | run metrics → ant telemetry |
| `:sigil` | `:policy` | base symbol informs policy bias |
| `:world` | `:world` | composite state preserved |

## Morphism mapping (draft)

| MetaCA morphism | Cyber-ant morphism | Intent |
| --- | --- | --- |
| `:observe` | `:sense` | metrics become telemetry |
| `:score` | `:plan` | ranking drives policy updates |
| `:lift` | `:plan` | sigil lifts propose policy tweaks |
| `:evolve` | `:act` | evolution becomes action |
| `:gate` | `:act` | gating constrains actions |

## Functor spec (data)

This lives in `src/futon5/ct/dsl.clj` as a draft functor:

```clojure
{:name :metaca->cyber-ant
 :source :futon5/metaca
 :target :futon5/cyber-ant
 :object-map {:world :world
              :genotype :policy
              :phenotype :agent-state
              :kernel :policy
              :operator-set :policy
              :metrics :telemetry
              :sigil :policy}
 :morphism-map {:observe :sense
                :score :plan
                :lift :plan
                :evolve :act
                :gate :act}}
```

## Hand-crafted examples (anchors)

- **WhiteSpaceScout (川)** → `:policy` bias toward pheromone scouting.
- **HungerPrecision (义)** → `:agent-state` hunger/precision coupling.
- **Accumulator (付)** → `:policy` dampening when reserves are low.
- **HyperAnt (代)** → `:policy` that promotes trail tuning or search.

## Next steps

- Decide whether `:operator-set` should map to `:policy` or a separate
  `:operator-set` object in the ant world.
- Specify how sigils encode policy weights (linear map vs lookup table).
- Add a compile step that uses this functor to attach CT blueprints to
  generated cyber-ant configs.
