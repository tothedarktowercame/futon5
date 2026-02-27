# pod-eigs

Babashka pod for 6x6 eigenvalues using Rust + nalgebra.

## Build

```bash
cd pod-eigs
cargo test
cargo build --release
```

The binary will be at `pod-eigs/target/release/pod-eigs`.

## Babashka usage

Example helper:

```clojure
(require '[babashka.pods :as pods])
(pods/load-pod "./pod-eigs/target/release/pod-eigs")
(require '[pod.eigs :as eigs])
(eigs/eigenvalues {:data (repeat 36 0) :symmetric true})
```

Or use the wrapper in `bb/pod_eigs.clj`:

```clojure
(require '[pod-eigs :as eigs])
(eigs/eigenvalues {:rows [[1 0 0 0 0 0]
                          [0 1 0 0 0 0]
                          [0 0 1 0 0 0]
                          [0 0 0 1 0 0]
                          [0 0 0 0 1 0]
                          [0 0 0 0 0 1]]
                 :symmetric true})
```

## Input format

Pass an EDN map with either:

- `{:data [36 ints 0/1]}` row-major, OR
- `{:rows [[6 ints] ... 6]}`

Optional: `{:symmetric true}` (default false).

## Output format

- If `:symmetric true`:
  - `{:eigenvalues [6 doubles]}`
- Else:
  - `{:eigenvalues [[re im] ... 6]}`

Eigenvalues are sorted by `(re, im)` for determinism.

## Notes

- If `:symmetric true` and the matrix is not symmetric within `1e-9`, the pod returns an error.
- The pod uses the JSON format for payloads; Babashka handles EDN<->JSON conversion automatically.
