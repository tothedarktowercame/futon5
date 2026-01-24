# Changelog

## 2026-01-24

### Fixed
- Make hexagram catalog load from a cached EDN resource to avoid runtime dependence on `reference/pg25501.txt` and repeated parsing.
- Restore reproducible exotype blending by routing RNG where available and reducing non-deterministic tiebreakers.
- Improve template handling: allow context maps to supply `:phenotype-context` and `:template-strictness`, and respect mutation-rate overrides in contextual mutation.

### Added
- Legacy namespaces for deprecated hexagram/exotype helpers (`futon5.hexagram.legacy`, `futon5.mmca.exotype-legacy`).
- `scripts/exotype_behavior_demo.clj` bb runner to generate inline, bending, strictness, and combined demos.
- Combined-mode demo (inline + bending + strictness) with low mutation rate for visual inspection.
- Notebook updates with representative images and reproducibility details.
- Cached hexagram catalog: `resources/iching-hexagrams.edn`.

### Notes
- `hexagram/lift.clj` now uses a QR-based eigenvalue approximation to keep bb runs self-contained.
