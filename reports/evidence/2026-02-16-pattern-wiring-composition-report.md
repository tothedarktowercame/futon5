# Evidence Report: Pattern->Wiring + Composition Survey

Generated at: 2026-02-16T16:54:23Z

## Commands

`python3 scripts/pattern_to_wiring.py --all`

`bb -cp src:resources scripts/composition_survey.clj`

## Outcomes

- Pattern-to-wiring generation: success (14 wiring files + manifest written)
- Mission validation: 6/6 valid
- Serial composition: 22/22 valid
- Parallel composition: 22/22 valid

## Visual Debug Log (Generated Wiring Complexity)

Legend: each `#` is ~4 (nodes+edges).

| Pattern | Sigil | Strategy | Hex | Nodes | Edges | Visual |
|---|---|---|---:|---:|---:|---|
| use-operators | 术 | sigil-bits-mod-64 | 2 | 6 | 5 | ### |
| global-rule-genotype | 专 | sigil-bits-mod-64 | 15 | 6 | 5 | ### |
| genotype-gate | 屯 | sigil-bits-mod-64 | 33 | 6 | 5 | ### |
| facet | 面 | unicode-sum-mod-64 | 35 | 6 | 5 | ### |
| freeze-genotype | 止 | sigil-bits-mod-64 | 38 | 6 | 5 | ### |
| white-space-scout | 川 | sigil-bits-mod-64 | 41 | 6 | 5 | ### |
| phenotype-on | 生 | sigil-bits-mod-64 | 43 | 6 | 5 | ### |
| accumulator | 付 | sigil-bits-mod-64 | 47 | 6 | 5 | ### |
| blending-curation | 手 | sigil-bits-mod-64 | 47 | 6 | 5 | ### |
| hyperant | 代 | sigil-bits-mod-64 | 49 | 6 | 5 | ### |
| random-genotype | 凡 | sigil-bits-mod-64 | 49 | 6 | 5 | ### |
| lock-kernel | 门 | sigil-bits-mod-64 | 52 | 6 | 5 | ### |
| uplift | 升 | sigil-bits-mod-64 | 53 | 6 | 5 | ### |
| hunger-precision | 义 | sigil-bits-mod-64 | 54 | 6 | 5 | ### |

## Artifact Paths

- `reports/evidence/2026-02-16-pattern_to_wiring.log`
- `reports/evidence/2026-02-16-composition_survey.log`
- `reports/evidence/2026-02-16-pattern_to_wiring_debug.edn`
- `reports/evidence/2026-02-16-pattern-wiring-composition-report.md`
- `scripts/pattern_to_wiring.py`
- `scripts/pattern_to_wiring.clj`
- `scripts/composition_survey.clj`
