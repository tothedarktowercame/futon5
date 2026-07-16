# S8 propagator composition index

**Status: COMPLETE. Coverage: 40320 / 40,320 σ through 20256 / 20,256 proven mirror-orbit representatives.**

The index records three seeded trajectories per representative at width 60 for 120 updates. Each trajectory is a dense `121 × 256` census in standard Wolfram rule numbering, accompanied by survival, terminal rule count, total phenotype activity, and the supplied class-4 population over time. No embedding or post-hoc regime labels are applied.

## Orbit reduction

Left–right reflection was proven pathwise on the original 2014 engine; 0↔1 complementation was rejected because its fixed-zero boundaries break the symmetry. Burnside reduction therefore yields exactly 20,256 orbits. The machine index enumerates all 40,320 σ and gives each representative, artifact path, and standard-rule mirror-axis transform.

## Anchors

| anchor | sigma | representative | available | death | terminal rules | pass |
|---|---|---|---|---|---|---|
| `rotate+2` | `[2 3 4 5 6 7 0 1]` | `[2 3 4 5 6 7 0 1]` | true | [120 120 120] | [30 25 34] | true |
| `rotate+1` | `[1 2 3 4 5 6 7 0]` | `[1 2 3 4 5 6 7 0]` | true | [47 41 44] | [1 1 1] | true |
| `two-4-cycles` | `[1 2 3 0 5 6 7 4]` | `[1 2 3 0 5 6 7 4]` | true | [74 40 29] | [1 1 1] | true |
| `three-five` | `[1 2 0 4 5 6 7 3]` | `[1 2 0 4 5 6 7 3]` | true | [120 120 120] | [29 18 34] | true |

## Storage and provenance

Fingerprint `ac2ff1681eae5b85` covers this driver, its lexical-binding Elisp worker, the orbit witness, harness, protocol, and every vendored MetaCA input. Each compressed census has an atomic manifest containing its SHA-256 and compact measures; resume rejects absent, stale, or checksum-mismatched files.

- Full machine table: `data/propagator-index/index-ac2ff1681eae5b85.edn.gz`
- Compact build state: `data/propagator-index/coverage.edn`
- Census artifacts: `data/propagator-index/artifacts/ac2ff1681eae5b85/`
- Per-representative manifests: `data/propagator-index/manifests/ac2ff1681eae5b85/`

Reproduce or resume with:

```sh
PROPAGATOR_INDEX_WORKERS=8 clojure -M -e '(load-file "scripts/propagator_index.clj")'
```
