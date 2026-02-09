# Technote: Settled State (futon5)

This doc is a small set of "do not drift" invariants intended to keep futon5
settled and runnable through **Feb 18, 2026**.

The goal is to reduce cognitive load: you should be able to re-enter the repo,
run the key CLIs, and know where outputs will go without rediscovering
structure.

## File Location Ratchets

These paths are treated as stable. If you must move them, update the
healthcheck and any docs that reference them in the same patch.

- `resources/futon5/sigils.edn`
  - Sigil table used by `futon5.ca.core` (`io/resource "futon5/sigils.edn"`).
- `resources/futon5/sigil_patterns.edn`
  - Reserved sigil bindings and metadata (used by `futon5.sigils`).
- `resources/futon5/pattern-lifts.edn`
  - Lifted pattern registry consumed by `futon5.patterns.catalog`.
- `deps.edn`
  - Must continue to declare local deps:
    - `../futon3a`
    - `../futon1/apps/common`

## Key CLI Entrypoints (Expected Behavior)

These are the "front doors". Prefer improving messages and checks over
changing behavior.

- `clj -M -m futon5.healthcheck`
  - Verifies required resource files exist on disk.
  - Verifies expected local dep directories exist, and that `deps.edn` declares them.
  - Runs a tiny deterministic MMCA smoke run (no operators) and exits non-zero on exception.
  - Optionally checks for ImageMagick `convert` as a warning only.
- `clj -M -m futon5.sigils`
  - Prints reserved sigils, roles/pattern ids, and remaining free slots.
- `clj -M -m futon5.mmca.cli`
  - Terminal runner for MMCA (small runs by default).
- `bb -m futon5.mmca.render-cli`
  - Deterministic render helper (PPM output).
- `bb -m futon5.mmca.metaevolve`
  - Outer-loop meta-evolution runner (heavier; see `--help` for flags).
- `bb -m futon5.mmca.kernel-sampler`
  - Kernel sweep renderer.
- `clj -M -m futon5.llm.relay`
  - Experimental relay (supports `--dry-run`); reads key from `~/.openai-key`.

## Output Defaults (and Expectations)

Default outputs should be predictable and discoverable:

- Clojure CLIs (`clj -M -m ...` / `bb -m ...`) should print what they wrote.
- Scripts that write to `/tmp` by default should:
  - Print the actual output path(s) written.
  - Warn if overwriting an existing file, or if writing into a non-empty output directory.

Notes:
- Many scripts intentionally default to `/tmp` for disposable artifacts.
- Some scripts default to `futon5/resources/figures` for images; if rerun, this
  can overwrite existing figure names. Scripts should warn when writing into a
  non-empty figures directory.

## Stubbed-by-Design Surfaces

Some components are explicitly allowed to be incomplete without it being a bug:

- Pattern lift roles in `resources/futon5/pattern-lifts.edn` that are not mapped
  to an implementation in `src/futon5/patterns/catalog.clj` (`role->builder`)
  are treated as **stubs by design**.
- The "exotic xenotype" scorer path in `README-exotic-mode.md` is explicitly a
  placeholder scorer until later missions replace it with real CT-derived metrics.

## What Not To Change (During Settled Window)

- Core MMCA runtime/evolution behavior (unless a correctness bug is proven).
- Resource file locations listed above (unless moved with healthcheck + docs updates).
- Default output conventions (print + warn rather than changing paths).

