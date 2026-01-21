# Mission 0 Technote 3: Cyber-MMCA

This technote summarizes the Cyber-MMCA work that complements Mission 0. The
focus is on the observation ABI, controller variants, and the new stress/sweep
reports that make MMCA steering measurable without an EoC oracle.

## Scope and sources

- Core: `futon5/src/futon5/cyber_mmca/core.clj`
- ABI and metrics: `futon5/src/futon5/mmca/metrics.clj`
- Scripts: `futon5/scripts/cyber_mmca_*.clj`
- Notebook context: `futon5/resources/exotic-programming-notebook-iii.org`
- Technote links: `futon5/docs/Mission-0-technote1.md`, `futon5/docs/Mission-0-technote2a.md`

## Observation ABI (per window)

Cyber-MMCA consumes the terminal ABI defined by windowed macro features:

- `:pressure` - normalized change-rate proxy
- `:selectivity` - normalized uniqueness proxy (1 - unique ratio)
- `:structure` - normalized temporal autocorrelation
- `:activity` - currently same as pressure
- `:regime` - gated regime label (freeze/magma/eoc/etc.)

This makes control decisions legible and comparable across controllers and
seed suites.

## Controller variants

Controllers share the same ABI and action vocabulary:

- `controller/null` - always `:hold`
- `controller/hex` - rule-based decisions from regime + bands
- `controller/sigil` - action vectors derived from control sigils

All controllers operate by adjusting exotype parameters:

- `:pressure-up` / `:pressure-down` -> `:update-prob`
- `:selectivity-up` / `:selectivity-down` -> `:match-threshold`
- `:hold` -> no-op

## Scripts and outputs

All scripts require `futon5/resources` on the classpath.

- Demo loop:
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_demo.clj`
- Controller comparison:
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_compare.clj`
- A/B toggle comparison (fixed-seed):
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_ab_compare.clj`
- Terminal stress test (extreme params + recovery):
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_stress_test.clj`
- Window/stride sweep (W/S grid):
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_stride_sweep.clj`
- Genotype-only vs phenotype-only isolation:
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_state_isolation.clj`
- Macro-trace sanity report:
  - `bb -cp futon5/src:futon5/resources futon5/scripts/cyber_mmca_macro_trace_report.clj`

Each script writes a CSV to `/tmp/` by default and prints per-controller
statistics (fraction of freeze/magma/ok, streak lengths, regime transitions).

## How this supports Mission 0 (Approach 2)

Approach 2 needs an executable wiring diagram and a way to validate it without
hand-tuned EoC labels. Cyber-MMCA provides:

- A stable terminal ABI that can act as typed inputs to wiring diagrams.
- A/B scaffolding (fixed-seed comparisons) for diagram evaluation.
- PSR/PUR-style evidence (actions + deltas + applied?) for auditability.
- Stress/sweep tools that map control response without an oracle.

This makes Approach 2 falsifiable before EoC alignment and prepares the ground
for later diagram synthesis.

## Next steps

1) Wire a wiring-diagram controller into the Cyber-MMCA loop (drop-in
   replacement for controller/hex).
2) Use stress + stride sweeps to identify parameter regions with stable
   recovery signatures, then bias Mission 0 batch priors.
3) Add a short recovery summary (time-to-ok post-stress) to the stress report
   CSV and summary stats.
