# Tensor End-to-End Trace Report

- Generated: `2026-02-21T20:43:33.613039170Z`
- Source report: `out/metaevolve-tensor-explore025-2026-02-21.edn`
- Selected run: rank `1` seed `1007`
- Rule: `手` backend `:clj`
- Run index: `0`

## 1) Plain-English Read

- This traces one run from CA state evolution through tensor stages into transfer-pack outputs.
- The key bridge is: `gen-history + summary + seed/run-index/rule -> top-sigils + cyber-ant + aif`.
- This report verifies the mapping by recomputing transfer outputs and checking equality.

## 2) Run Context

- Gen-history length: `257`
- Phenotype-history length: `257`
- Summary (selected): `{:composite-score 26.000134781169763, :score 14.927845574072956, :avg-entropy 6.4490196356782326, :avg-change 0.53125, :avg-unique 0.7548942120622568, :temporal-autocorr 1.0}`
- Top sigils: `乐 仗 斥 仔 匹 甩 尸 禾`
- Top counts: `乐:1539, 仗:774, 仔:727, 斥:727, 匹:644, 甩:641, 尸:599, 禾:594`
- Run image: [tensor-visuals-explore025-pheno-v1/images/run-01-score-26.00-seed-1007-rule-u624b.png](tensor-visuals-explore025-pheno-v1/images/run-01-score-26.00-seed-1007-rule-u624b.png)
- Cyber-ant wiring: [tensor-visuals-explore025-pheno-v1/diagrams/run-01-score-26.00-seed-1007-rule-u624b-cyber-ant.mmd](tensor-visuals-explore025-pheno-v1/diagrams/run-01-score-26.00-seed-1007-rule-u624b-cyber-ant.mmd)

## 3) Tensor Stage Trace (generation 0 -> 1)

| Stage | Function | Input | Output | Check |
| --- | --- | --- | --- | --- |
| 0 | input | `row0` 128 chars | `甘孔节二历仅叫古巧乌友白大刃叶示于刊丘寸巴丈布世方示化火习斗比心厉心已正艺且电刊才乎牛井凶为才付...` | - |
| 1 | `sigil-row->tensor` | row0 | tensor `128x8` | width preserved |
| 2 | `tensor->bitplanes` | tensor0 | bitplanes `8x128` | transpose |
| 3 | `step-bitplanes` | rule `手`, opts `{:backend :clj, :wrap? false, :boundary-bit 0}` | bitplanes-next `8x128` | CA local rule applied |
| 4 | `bitplanes->tensor` + `tensor->sigil-row` | bitplanes-next | ungated row1 `土下世乐千为匹无丸仗予丘乐心冈仗扔五他乐甘仔仁冈勺北付印田瓜印仇友另丹付以入气仔另勿节们牙乏仪什...` | decode ok |
| 5 | `gate-sigil-row-by-phenotype` | old row0 + ungated row1 + phenotype0 | gated row1 `甘下节二历为叫古巧仗予丘乐心叶示于五他乐巴仔仁世勺示化火田瓜印仇友另丹正艺入电刊另乎牛们凶乏才付...` | phenotype present: `yes` |
| 6 | observed run history | gen-history[1] | `甘下节二历为叫古巧仗予丘乐心叶示于五他乐巴仔仁世勺示化火田瓜印仇友另丹正艺入电刊另乎牛们凶乏才付...` | one-step match: `yes` |

## 4) Transfer-Pack Trace

| Stage | Inputs | Outputs | Evidence |
| --- | --- | --- | --- |
| `gen-history->top-sigils` | `gen-history` | `top-sigils`, `sigil-counts` | `乐 仗 斥 仔 匹 甩 尸 禾` |
| `pass-seed` + `pass-run-index` | `1007`, `0` | `seed*`, `run-index*` | used in cyber-ant id/source |
| `pass-summary` + `summary->aif-score` | selected summary fields | `aif/*` | `#:aif{:score 49.021893752452584, :food-quality 0.26000134781169765, :trail-score 0.66875, :biodiversity 0.7548942120622568, :food-count 3}` |
| `top-sigils->cyber-ant` | `top-sigils`, `seed*`, `run-index*`, `rule` | `cyber-ant` | id `:cyber/auto-1007-00`, operator `:UpliftOperator` |

## 5) Contract Checks

| Check | Result |
| --- | --- |
| `top-sigils (stored vs recomputed)` | `yes` |
| `top-sigils (meta-lift vs transfer)` | `yes` |
| `aif map (stored vs recomputed)` | `yes` |
| `cyber-ant map (stored vs recomputed)` | `yes` |

## 6) Wiring Link Map

| Tensor-side source | Transfer node | Wiring-side target |
| --- | --- | --- |
| `gen-history` from tensor run | `gen-history->top-sigils` | `cyber-ant.source.sigils`, `meta-lift.top-sigils` |
| `summary` from tensor MMCA metrics | `summary->aif-score` | `tensor-transfer.aif` scorecard fields |
| `seed`, `run-index`, `rule` metadata | `pass-seed`, `pass-run-index`, primitive state | `cyber-ant.id`, `cyber-ant.source.rule`, `cyber-ant.source.seed` |

## 7) Remaining Gap (for this run)

- This confirms dataflow and deterministic transfer, not learning closure.
- Ant/AIF outcomes are not yet fed back as direct parameter updates into the next tensor/MMCA dynamics.
- So this is traceable plumbing with parity, still pre-optimization.
