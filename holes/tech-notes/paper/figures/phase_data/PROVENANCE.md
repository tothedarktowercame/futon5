# Phase-diagram figure data (offset+2 feedforward finite-size scan)

- `E2b-scan-results.md` — copy of `mmca-clj/holes/E2b-offset2-finite-size-results.md`,
  produced bit-for-bit by `clojure -M -m mmca.experiments.offset2-finite-size`
  (writing=[2 3 4 5 6 7 0 1], seeds 0–31, widths [30 60 120 240], steps 300).
  q = propagator duty cycle (Pr apply propagator write; 1−q holds the neighbour blend).
- `phase_qNNN.txt` — genotype fields (rule bytes, rows=time, cols=space), single
  paired illustrative realization at width 240, seed 1, steps 300:
  `mmca.core/run-propagator [2 3 4 5 6 7 0 1] 1 240 300 {:interrupter-q q}` for q=NNN/1000.
- `rule_scores.txt` — per-rule isolated-rule activity score used for the tint.

Regenerate the figure: `python3 figures/gen_phase_diagram.py`.
