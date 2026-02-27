# Preregistration

Use this folder to preregister MMCA/tensor experiments before running them.

Minimum prereg contents:

- `study_id`: stable identifier
- `question`: what we are trying to learn
- `hypotheses`: directional, falsifiable statements
- `design`: exact run commands and fixed parameters
- `metrics`: how outcomes are computed
- `decision_rules`: pass/fail thresholds decided in advance
- `disconfirmation`: what would count against the idea
- `artifacts`: expected output files

For Nonstarter logging, mirror the same fields in:

- `scripts.nonstarter-hypothesis register`
- `scripts.nonstarter-study register`

Keep prereg immutable after run start; append a result note in a separate file.
