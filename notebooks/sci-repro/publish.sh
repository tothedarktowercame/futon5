#!/usr/bin/env bash
# Publish rendered lab notebooks to the public site (futon7a/lab/sci-repro/).
#
# Protocol (M-lab-standard genre contract, step 8 — "open lab notebooks"):
# after a slice passes review, run this from futon5/notebooks/sci-repro/.
# It copies the Clay renders (+ asset dirs) into futon7a, regenerates the
# section index with provenance (futon5 SHA + date), commits, and pushes.
#
# Usage: ./publish.sh            # copy, index, commit, push
#        ./publish.sh --no-push  # everything except the push (inspect first)

set -euo pipefail
cd "$(dirname "$0")"

DEST=../../../futon7a/lab/sci-repro
SHA=$(git rev-parse --short HEAD)
DATE=$(date -u +%Y-%m-%d)

mkdir -p "$DEST"
cp out/notebooks.*.html "$DEST"/
for d in out/notebooks.*_files; do
  [ -d "$d" ] && rsync -a --delete "$d" "$DEST"/
done

python3 - "$DEST" "$SHA" "$DATE" <<'PY'
import sys, os, re, html
dest, sha, date = sys.argv[1:4]
nbs = sorted(f for f in os.listdir(dest)
             if f.startswith('notebooks.') and f.endswith('.html'))
DESC = {
    'nb01_metaca_core': 'The no-blending MetaCA (&sect;3.1): Figure-2-style runs, '
        'measured time-to-stasis (C1), fixed-rule ECA baselines, and the '
        'machine-checked censored-Rule-23 proof (C7) with its bit-order finding.',
    'nb02_blending': 'The blending dynamic (&sect;3.2): paired blend-vs-no-blend '
        'measurement on shared seeds (C2), entropy and change-rate curves, '
        'stable-band timing.',
    'nb03_phenotype': 'The coupled genotype+phenotype dynamic (Figure 4): '
        'phenotype update semantics pinned to 256ca.el (A5), region '
        'conformance (C3), and genotype/phenotype MI against a shuffled null.',
    'nb04_mutation': 'Mutation dynamics (Figures 5&ndash;8): mutation-rate sweep '
        '(C4) with no-mutation control and random-replacement null, popcount-class '
        'frequencies and flagged-rule patch lifetimes (C5), and first-bit-only '
        'mutation on coupled runs with Rule-0/128 occupancy (C6).',
    'mmca_supplement1': 'Supplement 1 to <em>Rule-Rewriting Cellular Automata '
        'and the Edge of Chaos</em>: the twelve empirical findings, rendered '
        'from the same canonical source as the journal-ready PDF.',
}
items = []
for f in nbs:
    stem = re.sub(r'^notebooks\.|\.html$', '', f)
    desc = DESC.get(stem, 'Lab notebook.')
    items.append(f'<li><a href="{html.escape(f)}">{html.escape(stem)}</a> &mdash; {desc}</li>')
page = f"""<!doctype html><html><head><meta charset="utf-8">
<title>Open lab notebooks &mdash; M-sci-reproduction</title>
<link rel="stylesheet" href="../../latex.css">
<style>body{{max-width:46rem;margin:2rem auto;padding:0 1rem}}</style></head><body>
<h1>Open lab notebooks: reproducing <em>The Search for Computational Intelligence</em></h1>
<p>Computational reproduction of the 1D experiments in
<a href="https://arxiv.org/abs/1502.00130">arXiv:1502.00130</a> (Corneli &amp;
Maclean, 2015), with every qualitative claim restated as a measured proposition
against explicit baselines, and the dynamics verified grid-identical to the
paper's original 2014 Emacs Lisp implementation. Each notebook ends with
instructions for reproducing it; sources live in the futon5 repository
(<code>notebooks/sci-repro/</code>).</p>
<ul>{''.join(items)}</ul>
<p><small>Published {date} from futon5 <code>{sha}</code>. These pages are
rendered <a href="https://scicloj.github.io/clay/">Clay</a> notebooks and are
republished as the experiment series progresses.</small></p>
</body></html>"""
open(os.path.join(dest, 'index.html'), 'w').write(page)
print('index.html written with', len(nbs), 'notebooks')
PY

cd "$DEST"
git add -A .
git commit -m "lab/sci-repro: publish notebooks @ futon5 $SHA ($DATE)" || echo "nothing to commit"
if [ "${1:-}" != "--no-push" ]; then
  git push
  echo "PUBLISHED lab/sci-repro @ futon5 $SHA"
else
  echo "STAGED (not pushed) lab/sci-repro @ futon5 $SHA"
fi
