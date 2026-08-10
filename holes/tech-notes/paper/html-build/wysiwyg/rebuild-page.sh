#!/usr/bin/env bash
# Rebuild the WYSIWYG page (and its scope table) from a main .tex.
#
# One place that knows how to build the page, so the file watcher in
# latex-wysiwyg.el only has to run a command asynchronously rather than carry
# the oxide invocation itself.
#
#   ./rebuild-page.sh [main.tex]        default: whatever page/.main records
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAPER="$(cd "$HERE/../.." && pwd)"
MAIN="${1:-$(cat "$HERE/page/.main" 2>/dev/null || echo draft8.tex)}"
echo "$MAIN" > "$HERE/page/.main"

OX="${OX:-}"
if [ -z "$OX" ]; then
  if command -v latexml_oxide >/dev/null 2>&1; then OX="$(command -v latexml_oxide)"
  else OX="$(ls -d /tmp/claude-*/*/*/scratchpad/oxide/*/latexml_oxide 2>/dev/null | head -1)"; fi
fi
[ -x "$OX" ] || { echo "rebuild-page: latexml_oxide not found" >&2; exit 1; }

cd "$PAPER"
# dest follows the main file: draft9.tex -> page/draft9.html, so switching
# drafts does not silently overwrite the previous page
DEST="$HERE/page/$(basename "${MAIN%.tex}").html"
# oxide 0.7.5 has a rare nondeterministic null-deref (kernel: "segfault at 460",
# seen 2026-08-10; same input then built cleanly). Convert to a temp file and
# install atomically, retrying once, so a crash never truncates the live page.
TMP="${DEST%.html}.tmp.html"   # oxide keys output format off the extension
ok=""
for attempt in 1 2; do
  if "$OX" --source-map --path="$PAPER/html-build" --preload=apa7-html-shim.sty \
        --dest="$TMP" "$MAIN" > "$HERE/page/build.log" 2>&1 \
     && grep -q '</html>' "$TMP"; then ok=1; break; fi
  echo "rebuild-page: oxide attempt $attempt failed (see page/build.log)" >&2
done
if [ -n "$ok" ]; then mv -f "$TMP" "$DEST"; else rm -f "$TMP"; fi
[ -f "$DEST" ] || { echo "rebuild-page: no page produced" >&2; exit 1; }
errs=$(grep -cE '^Error' "$HERE/page/build.log" || true)
cp -f "$HERE/wysiwyg-client.js" "$HERE/rocket-client.js" "$HERE/page/"
python3 "$HERE/make-scope-table.py" "$DEST" \
        --src "$PAPER" --main "$MAIN" >/dev/null
# the reading view is derived from the same conversion, so it never drifts
python3 "$HERE/tuftify.py" "$DEST" -o "${DEST%.html}-tufte.html" >/dev/null
echo "rebuilt $MAIN -> $(basename "$DEST") + $(basename "${DEST%.html}-tufte.html") ($errs errors)"
