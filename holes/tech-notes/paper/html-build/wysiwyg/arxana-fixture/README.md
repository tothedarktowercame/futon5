# arxana-fixture/ — M-jvm-arxana INSTANTIATE increment 1 (fixture gate)

D7 increment 1 / D4 repair-and-rejection procedure, executed.

## Contents

- `scopes-negative-v1.json` — the original contradiction-bearing scope table
  (copied from `../sandbox/scopes.json`; the original is preserved untouched).
  Its `source-sha` values describe `.pristine` bytes, not the sources the page
  was built from. The gate MUST reject it with `fixture/revision-mismatch`.
- `draft8.html` + `build.log` — clean rebuild from the sandbox sources with
  `latexml_oxide --source-map`.
- `scopes.json` — repaired positive scope table: producer-resolved paths
  constrained to the allowed build root, hashing the exact bytes named by
  each source-map index.
- `manifest.json` — D4 fixture manifest v1 (full revision digests, build ID,
  tool version, source commit).
- `check-fixture.py` — the gate: revision match, path containment, anchor
  bounds, and (with `--cases`) the three normative D4 cases.
- `run-tests.sh` — negative rejection, positive coherence, wrong-root
  rejection, path-escape rejection.

## Reproduce

```sh
cd /home/joe/code/futon5/holes/tech-notes/paper
latexml_oxide --source-map --path=html-build --preload=apa7-html-shim.sty \
  --dest=html-build/wysiwyg/arxana-fixture/draft8.html \
  html-build/wysiwyg/sandbox/draft8.tex \
  > html-build/wysiwyg/arxana-fixture/build.log 2>&1
python3 html-build/wysiwyg/make-scope-table.py \
  html-build/wysiwyg/arxana-fixture/draft8.html \
  --src html-build/wysiwyg/sandbox --main draft8.tex \
  --build-root html-build/wysiwyg/sandbox --producer-cwd "$PWD"
html-build/wysiwyg/arxana-fixture/run-tests.sh
```

The builder defect this repairs (V3 S2): `resolve_from_log` reduced each
producer-logged source path to a basename, which was then joined to whatever
`--src` directory was passed — hashing same-named files under the wrong root
(the paper root instead of the sandbox). The repair keeps the full
producer-resolved path, constrains it to an explicit `--build-root`, rejects
anything outside it, and hashes the exact resolved bytes.
