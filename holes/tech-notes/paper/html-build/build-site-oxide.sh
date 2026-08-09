#!/usr/bin/env bash
# Build the static site with latexml-oxide (Rust). Replaces build-site.sh,
# which used Perl LaTeXML plus nine .ltxml bindings; oxide needs only the
# 22-line apa7-html-shim.sty.
#
# Each document is converted twice: once to HTML (the deliverable) and once to
# XML. The XML is needed because oxide keeps the `labelref="LABEL:..."`
# attribute for unresolved cross-document references, while the HTML renders
# them as empty spans -- finish-site.py pairs the two up by document order to
# turn them into real links. Both runs together are still ~8s per document.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="${SRC:-$(cd "$HERE/.." && pwd)}"   # the paper directory
SHIM="$HERE"                            # holds apa7-html-shim.sty
OUT="${1:-$HERE/site-oxide}"
XML="$HERE/xml-oxide"

# Locate latexml_oxide: $OX, then PATH, then a local ./oxide/ checkout.
if [ -n "${OX:-}" ] && [ -x "${OX:-}" ]; then
  :
elif command -v latexml_oxide >/dev/null 2>&1; then
  OX="$(command -v latexml_oxide)"
elif [ -x "$HERE/oxide/latexml_oxide" ]; then
  OX="$HERE/oxide/latexml_oxide"
else
  echo "error: latexml_oxide not found." >&2
  echo "  Install it (CC0, ~20 MB) with:" >&2
  echo "    curl -sSL https://github.com/dginev/latexml-oxide/releases/download/0.7.5/latexml-oxide-0.7.5-x86_64-unknown-linux-gnu.tar.gz \\" >&2
  echo "      | tar xz -C /tmp && install -D /tmp/latexml-oxide-0.7.5-x86_64-unknown-linux-gnu/latexml_oxide ~/.local/bin/latexml_oxide" >&2
  echo "  ...or point OX=/path/to/latexml_oxide at an existing copy." >&2
  exit 1
fi

mkdir -p "$OUT" "$XML"

DOCS=(
  "draft8.tex|index-paper"
  "supplement.tex|supplement"
  "supplement1.tex|supplement1"
  "supplement2-theory.tex|supplement2"
  "supplement3-figures.tex|supplement3"
  "supplement5.tex|supplement5"
)

cd "$SRC"
for entry in "${DOCS[@]}"; do
  IFS='|' read -r tex slug <<<"$entry"
  echo "==> $tex"
  "$OX" --path="$SHIM" --preload=apa7-html-shim.sty \
        --dest="$OUT/$slug.html" "$tex" 2>&1 | grep -E "^Conversion|^Error" || true
  "$OX" --path="$SHIM" --preload=apa7-html-shim.sty --xml \
        --dest="$XML/$slug.xml" "$tex" >/dev/null 2>&1 || true
done

cp -f "$HERE/paper-site.css" "$OUT/paper-site.css"

echo "==> nav, xref resolution, bibliography dedup, index"
python3 "$HERE/finish-site.py" "$OUT" --xml "$XML"

echo
echo "==> built $(ls -1 "$OUT"/*.html | wc -l) pages in $OUT"
