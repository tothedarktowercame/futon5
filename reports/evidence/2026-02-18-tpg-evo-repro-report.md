# Evidence Report: TPG Evolution Repro Bundle

Deterministic inspection of local TPG checkpoints and best-individual artifact.

## Command

`bb -cp src:resources scripts/tpg_evidence_bundle.clj --in-dir out/tpg-evo-production --report reports/evidence/2026-02-18-tpg-evo-repro-report.md --summary-edn reports/evidence/2026-02-18-tpg-evo-repro-summary.edn --checksums reports/evidence/2026-02-18-tpg-evo-repro-checksums.txt`

## Outcomes

- Checkpoint files: 10
- Global max overall-satisfaction: 0.752941176471
- Global max candidate IDs: `random-7846`
- Best artifact ID: `random-7846` (overall 0.752941176471)
- All consistency checks pass: true

## Consistency Checks

| Check | Status | Detail |
|---|---|---|
| `has-checkpoints` | PASS | checkpoint-count=10 |
| `checkpoint-generations-strictly-increasing` | PASS | [5 10 15 20 25 30 35 40 45 50] |
| `checkpoint-seed-constant` | PASS | #{352362012} |
| `history-aligns-with-generation` | PASS | for each checkpoint: history-last-generation == generation-1 |
| `population-size-constant` | PASS | #{16} |
| `best-tpg-overall-matches-global-max` | PASS | best=0.752941176471 max=0.752941176471 |
| `best-tpg-id-appears-among-global-max-candidates` | PASS | best-id=random-7846 max-ids=("random-7846") |

## Checkpoint Summary

| File | Gen | Hist Last | Hist Best | Hist Mean | Pop | Best ID | Best Overall |
|---|---:|---:|---:|---:|---:|---|---:|
| `checkpoint-gen-0005.edn` | 5 | 4 | 0.631373 | 0.590727 | 16 | `random-7846` | 0.631373 |
| `checkpoint-gen-0010.edn` | 10 | 9 | 0.676471 | 0.626634 | 16 | `random-7846` | 0.676471 |
| `checkpoint-gen-0015.edn` | 15 | 14 | 0.732026 | 0.681577 | 16 | `random-7846` | 0.732026 |
| `checkpoint-gen-0020.edn` | 20 | 19 | 0.742484 | 0.708946 | 16 | `random-7846` | 0.742484 |
| `checkpoint-gen-0025.edn` | 25 | 24 | 0.752941 | 0.725286 | 16 | `random-7846` | 0.752941 |
| `checkpoint-gen-0030.edn` | 30 | 29 | 0.752941 | 0.735212 | 16 | `random-7846` | 0.752941 |
| `checkpoint-gen-0035.edn` | 35 | 34 | 0.752941 | 0.738848 | 16 | `random-7846` | 0.752941 |
| `checkpoint-gen-0040.edn` | 40 | 39 | 0.752941 | 0.740278 | 16 | `random-7846` | 0.752941 |
| `checkpoint-gen-0045.edn` | 45 | 44 | 0.752941 | 0.740727 | 16 | `random-7846` | 0.752941 |
| `checkpoint-gen-0050.edn` | 50 | 49 | 0.752941 | 0.740727 | 16 | `random-7846` | 0.752941 |

## Best Artifact

- `:tpg/id`: `random-7846`
- `:overall-satisfaction`: 0.752941176471
- Teams: 4
- Operators: 8
- Schedule length: 7

## Artifact Paths

- `reports/evidence/2026-02-18-tpg-evo-repro-checksums.txt`
- `reports/evidence/2026-02-18-tpg-evo-repro-summary.edn`
- `reports/evidence/2026-02-18-tpg-evo-repro-report.md`