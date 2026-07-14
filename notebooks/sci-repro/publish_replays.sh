#!/usr/bin/env bash
# Publish the Tier-2 replay notebooks to futon7a/lab/replays/ (M-lab-standard
# step 8; sibling series to publish.sh's lab/sci-repro).
set -euo pipefail
cd "$(dirname "$0")"
DEST=../../../futon7a/lab/replays
SHA=$(git rev-parse --short HEAD)
DATE=$(date -u +%Y-%m-%d)
mkdir -p "$DEST"
cp out/notebooks.r*.html "$DEST"/
for d in out/notebooks.r*_files; do [ -d "$d" ] && rsync -a --delete "$d" "$DEST"/; done
python3 - "$DEST" "$SHA" "$DATE" <<'PY'
import sys, os, re, html
dest, sha, date = sys.argv[1:4]
nbs = sorted(f for f in os.listdir(dest) if re.match(r'notebooks\.r\d+.*\.html$', f))
DESC = {
    'r01_boundary_guardian': 'The boundary-guardian replay: the only '
        'genotype-layer edge-of-chaos run in the archive, re-measured with '
        'CIs against frozen and Rule-30 nulls (BG1); the standard verifier\'s '
        'blind spot confirmed at floor (BG2); a recorded measurement '
        'disagreement reconciled (BG3); and two discriminators — bitplane MI '
        'and diagonal autocorrelation — shown to separate structured chaos '
        'from generic chaos where the old diagnostics could not (BG4).',
    'r02_evaluator_population': 'Two statistical evaluator-population arms: '
        'naive genotype evolution collapses diversity from about 45 to 17 '
        'unique sigils, while a xenotype guard reduces — but does not '
        'eliminate — evaluator degeneracy with disjoint confidence intervals.',
    'r03_cyberants': 'A controlled statistical CyberAnts replay: L5-creative '
        'beats the starving sigil-gradient arm on patchy and sparse, but '
        'matches random-wiring and shuffled-parameter controls; snowdrift '
        'shows no supported difference, with the adapter limitation explicit.',
}
items = ''.join(
    f'<li><a href="{html.escape(f)}">{html.escape(re.sub(r"^notebooks\.|\.html$","",f))}</a>'
    f' &mdash; {DESC.get(re.sub(r"^notebooks\.|\.html$","",f), "Replay notebook.")}</li>'
    for f in nbs)
page = f"""<!doctype html><html><head><meta charset="utf-8">
<title>Open lab notebooks &mdash; Tier-2 replays</title>
<link rel="stylesheet" href="../../latex.css">
<style>body{{max-width:46rem;margin:2rem auto;padding:0 1rem}}</style></head><body>
<h1>Open lab notebooks: Tier-2 replays &mdash; bright ideas, measured</h1>
<p>Replays of the most promising unbaselined results from the futon5 archive,
each re-run as a claims-table notebook with explicit baselines and null
models, cross-checked against the original engine as executable ground truth.
Companion series to the <a href="../sci-repro/index.html">paper
reproduction</a>; method in the lab standard (futon5 repo,
<code>holes/missions/M-lab-standard.md</code>).</p>
<ul>{items}</ul>
<p><small>Published {date} from futon5 <code>{sha}</code>.</small></p>
</body></html>"""
open(os.path.join(dest, 'index.html'), 'w').write(page)
print('index written,', len(nbs), 'notebooks')
PY
cd "$DEST"
git add -A .
git commit -m "lab/replays: publish @ futon5 $SHA ($DATE)" || echo "nothing to commit"
[ "${1:-}" != "--no-push" ] && git push && echo "PUBLISHED lab/replays @ futon5 $SHA"
