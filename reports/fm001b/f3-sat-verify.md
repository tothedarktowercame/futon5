# f3-sat-verify run log

- 12:52 GMT 2026: re-cloned + rebuilt Kissat (tools/kissat) and launched `tools/kissat/build/kissat results/fm001b/n8.cnf > results/fm001b/n8-kissat.log 2>&1` (PID 2512410). Waiting for SAT/UNSAT; streaming log lives in `results/fm001b/n8-kissat.log`.
- Snapshot `results/fm001b/n8-kissat-run1.log` captures the initial solver banner/parsing phase for traceability. Next update will include the solver verdict and, if SAT, the witness adjacency export.
