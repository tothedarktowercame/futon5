#!/usr/bin/env bash
# Snapshot the paper as draft8a.tex (+ its inputs) for interactive WYSIWYG use.
#
# draft8.tex is live: Joe and other agents edit it, and every change makes the
# built page stale and refuses edits. The interactive surface gets its own
# stable copy, refreshed on demand rather than under you mid-session.
set -euo pipefail
P="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cp -f "$P/draft8.tex"           "$P/draft8a.tex"
cp -f "$P/intro-generated.tex"  "$P/intro-generated-a.tex"
cp -f "$P/part3-exotype.tex"    "$P/part3-exotype-a.tex"
# point the copy at the copied inputs, so nothing shared can drift
sed -i 's|\\input{intro-generated}|\\input{intro-generated-a}|; s|\\input{part3-exotype}|\\input{part3-exotype-a}|' "$P/draft8a.tex"
echo "snapshot: draft8a.tex + intro-generated-a.tex + part3-exotype-a.tex"
