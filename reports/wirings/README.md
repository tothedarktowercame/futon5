# Wiring Outcome Registry

This directory tracks the relationship between **wiring structures** and **health outcomes**.

## The Missing Loop

We have tools but no learning:
```
synthesize_xenotype.clj → [GENERATES] → wirings
cyber_mmca_wiring_ab_validate.clj → [RUNS] → runs
run_health_report.clj → [CLASSIFIES] → health

                    ↓ MISSING ↓

        Which wirings → which outcomes?
        What patterns → EoC vs barcode?
        How to evolve → toward EoC?
```

## Registry Structure

### wiring-outcomes.edn

Master registry mapping wirings to outcomes:

```clojure
{:wirings
 [{:id :boundary-guardian-001
   :path "data/xenotype-hybrid-wirings/boundary-guardian-001.edn"
   :structure {:nodes 10 :edges 15 :has-gate? true :has-legacy? true}
   :outcomes
   [{:experiment "boundary-guardian"
     :seeds [4242 238310129 352362012]
     :health {:hot 0 :eoc 0 :cooling 0 :barcode 9}
     :best-seed nil
     :notes "100% barcode by gen 100"}]}

  {:id :prototype-001-creative-peng
   :path "resources/xenotype-wirings/prototype-001-creative-peng.edn"
   :structure {...}
   :outcomes [...]}]}
```

### Structural Features to Track

For each wiring, extract:
- **Node count**: Total components
- **Edge count**: Total connections
- **Has legacy base?**: Uses legacy-kernel-step
- **Has gates?**: Uses threshold-sigil or similar
- **Has feedback?**: Cycles in the graph
- **Primary output path**: Chain from input to output

### Questions We Want to Answer

1. **Do wirings with legacy bases resist barcode better?**
   - Compare outcomes of wirings with/without :legacy-kernel-step

2. **Do gates help maintain EoC?**
   - Compare outcomes of wirings with/without threshold components

3. **Does complexity correlate with outcome?**
   - Plot node count vs health classification

4. **What edge patterns appear in EoC-producing wirings?**
   - Extract common subgraphs from successful wirings

## Evolution Strategy

Once we have enough data:

1. **Selection**: Identify wirings that produce EoC or COOLING (not BARCODE)
2. **Analysis**: What structural features do they share?
3. **Mutation**: Generate variants with those features
4. **Test**: Run and classify
5. **Repeat**: Build up a population of EoC-producing wirings

## Current Status

| Wiring | Experiments | Best Outcome | Notes |
|--------|-------------|--------------|-------|
| boundary-guardian-001 | 1 | BARCODE | 9/9 barcode |
| legacy-baseline | 1 | BARCODE | 6/6 barcode |
| prototype-001-creative-peng | 2 | COOLING | 2/19 cooling on seed 352362012 |

**No wirings have produced EoC yet.** The best outcome is COOLING (seed 352362012, width 120).
