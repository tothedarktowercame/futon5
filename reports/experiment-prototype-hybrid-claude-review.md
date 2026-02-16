# Notebook: Prototype vs Legacy vs Hybrid (Claude Review)

Date: 2026-01-25

## Purpose
Summarize the prototype/hybrid experiment so Claude can review the results and suggest next steps.

## Source notebook
- Primary run log: `reports/experiment-prototype-hybrid.md`
- Config: `data/experiment-prototype-hybrid-20.edn`
- Hybrid wiring: `data/xenotype-hybrid-wirings/hybrid-prototype-001-gong.edn`

## Setup
- Dimensions: length 100, generations 100
- Seeds (10; 30 runs total): 4242, 238310129, 1281562706, 71312373, 352362012, 201709760, 1564354771, 955468957, 1701878710, 1695261645
- Models:
  1) Baseline legacy 工
  2) Prototype 001 creative peng
  3) Hybrid 001 + 工 fallback (trigger on diversity < 0.45)

## Findings (high‑level)
- Prototype and hybrid outputs are effectively identical across all seeds.
  - This likely means the hybrid fallback never triggered.
- Prototype/hybrid metrics are consistently near‑max entropy and near‑max change.
  - These read as “too hot” by inspection, even though the current classifier flags them as “candidate.”
- Baseline is more mixed but does not clearly show edge‑of‑chaos bands in these runs.

## What to review
1) Are the prototype/hybrid results truly indistinguishable, or do we have subtle differences in wiring output that the classifier misses?
2) Does the current classifier (white/black ratio + freeze) fail to capture “too hot” behavior?
3) Should the hybrid fallback threshold or trigger be adjusted to engage more often?

## Evidence pointers
- Each seed’s summary (composite, avg‑entropy‑n, avg‑change, temporal) is logged in `reports/experiment-prototype-hybrid.md`.
- Per‑seed classifier artifacts exist in `/tmp/exp-proto-hybrid2-<seed>-classify.md`.

## Questions for Claude
- Do you see any signs of emergent EoC structure in the baseline run set that we’re missing?
- Would you propose a better “hotness” metric (entropy + change + temporal) or a visual test?
- How would you tune or redesign the hybrid fallback to be more active without collapsing to legacy?

