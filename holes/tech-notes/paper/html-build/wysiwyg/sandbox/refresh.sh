#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; P="$HERE/../../.."
for f in draft8.tex intro-generated.tex part3-exotype.tex refs.bib html-boxes.tex latexml.sty; do
  cp -f "$P/$f" "$HERE/"
done
cp -f "$P/html-build/apa7-html-shim.sty" "$HERE/"
ln -sfn "$P/figures" "$HERE/figures"
echo "sandbox refreshed from $P"
