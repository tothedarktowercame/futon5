#!/usr/bin/env bash
# Build a static HTML site for the propagators paper + supplements via LaTeXML.
#
# Usage:  ./build-site.sh [outdir]
#
# Requires: latexml/latexmlpost (Debian package `latexml`, tested at 0.8.8).
# The bindings/ directory next to this script shadows several stock LaTeXML
# bindings; see the header comment in each .ltxml for why.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="${SRC:-$(cd "$HERE/.." && pwd)}"   # the paper directory
OUT="${1:-$HERE/site}"
XML="$HERE/xml"
BINDINGS="$HERE/bindings"

mkdir -p "$OUT" "$XML"

# Documents to build: <texfile>|<slug>|<nav label>
DOCS=(
  "draft8.tex|index-paper|Main paper"
  "supplement.tex|supplement|Supplement: Notebook"
  "supplement1.tex|supplement1|Supplement 1: Findings"
  "supplement2-theory.tex|supplement2|Supplement 2: Theory"
  "supplement3-figures.tex|supplement3|Supplement 3: Figures"
  "supplement5.tex|supplement5|Supplement 5"
)

echo "==> bibliography"
latexml --preload=url.sty --preload=hyperref.sty \
        --dest="$XML/refs.bib.xml" "$SRC/refs.bib" 2>&1 | grep -E "^Conversion" || true

for entry in "${DOCS[@]}"; do
  IFS='|' read -r tex slug _label <<<"$entry"
  echo "==> $tex -> $slug.html"

  latexml --preload=paperboxes.sty \
          --path="$BINDINGS" --path="$SRC" --path="$SRC/figures" \
          --dest="$XML/$slug.xml" "$SRC/$tex" 2>&1 | grep -E "^Conversion" || true

  latexmlpost --format=html5 \
          --bibliography="$XML/refs.bib.xml" \
          --path="$SRC" --path="$SRC/figures" --sourcedirectory="$SRC" \
          --css=LaTeXML.css --css=ltx-article.css --css=paper-site.css \
          --dest="$OUT/$slug.html" "$XML/$slug.xml" 2>&1 \
        | grep -E "^(Post-processing|Error|Warning)" || true
done

cp -f "$HERE/paper-site.css" "$OUT/paper-site.css"

echo "==> nav + index"
python3 "$HERE/finish-site.py" "$OUT"

echo
echo "==> built $(ls -1 "$OUT"/*.html | wc -l) pages in $OUT"
