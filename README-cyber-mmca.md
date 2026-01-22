# Cyber-MMCA

Cyber-MMCA is a minimal controller framework for MMCA experiments. It mirrors
cyberants: a fixed observation ABI feeds a small action vocabulary that can be
swapped between controllers (null/hex/sigil/wiring). The goal is to show end-to-end
control loops without requiring an EoC oracle.

## Observation ABI (per window)

Derived from `futon5.mmca.metrics/windowed-macro-features`:

- `:pressure` - normalized change-rate proxy
- `:selectivity` - normalized uniqueness proxy (1 - unique ratio)
- `:structure` - normalized temporal autocorrelation
- `:activity` - currently same as pressure
- `:regime` - gated regime label (freeze/magma/eoc/etc.)

## Controller Actions

Macro-actions are coarse control moves that modify exotype parameters:

- `:pressure-up` / `:pressure-down` - adjust `:update-prob`
- `:selectivity-up` / `:selectivity-down` - adjust `:match-threshold`
- `:hold` - no-op

## Scripts

All scripts require `futon5/resources` on the classpath because they load
`futon5/sigils.edn`.

- Demo loop:
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_demo.clj`
- Controller comparison (null/hex/sigil):
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_compare.clj`
- A/B toggle comparison:
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_ab_compare.clj`
- Terminal stress test (extreme params + recovery):
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_stress_test.clj`
- Window/stride sweep (W/S grid):
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_stride_sweep.clj`
- Genotype-only vs phenotype-only isolation:
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_state_isolation.clj`
- Macro-trace sanity report:
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_macro_trace_report.clj`
- Prepare HIT inputs (full EDN runs + inputs list):
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_prepare_hit.clj`

## Wiring controller

The wiring controller evaluates a xenotype wiring diagram over the window
summary and maps the diagram output to macro-actions. Provide a diagram
directly or via a synthesized candidate file:

```
bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_stress_test.clj \
  --controllers wiring \
  --wiring-path /tmp/xenotype-synth-123.edn \
  --wiring-index 0
```

## Outputs

Each script writes a CSV to `/tmp/` by default and prints summary stats. These
CSVs are intended to be diffed or loaded into downstream analysis notebooks.
