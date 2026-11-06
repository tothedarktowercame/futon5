#!/usr/bin/env bash
# M-jvm-arxana INSTANTIATE increment 1 -- fixture gate tests (D4 tripwires).
#
#   1. negative: the original contradiction-bearing scopes.json is rejected
#      with fixture/revision-mismatch (never overwritten, never warning-only);
#   2. positive: the rebuilt coherent scope table passes revision match,
#      anchor bounds and all three normative cases;
#   3. wrong-root: the repaired builder refuses a producer-resolved source
#      path outside the allowed build root, even when a same-basename file
#      exists inside it;
#   4. path-escape: the checker rejects a scope entry that escapes src-dir.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WYSIWYG="$(cd "$HERE/.." && pwd)"
SANDBOX="$WYSIWYG/sandbox"
PAPER="$(cd "$WYSIWYG/../.." && pwd)"
fail=0

say() { printf '\n== %s ==\n' "$1"; }

# 1 -- negative fixture rejection
say "1: original scopes.json -> fixture/revision-mismatch"
out="$(python3 "$HERE/check-fixture.py" "$HERE/scopes-negative-v1.json" \
        --page "$WYSIWYG/page/draft8.html" 2>&1)"
echo "$out"
if echo "$out" | grep -q 'fixture/revision-mismatch' \
   && [ "$(echo "$out" | grep -c 'revision-mismatch')" = 3 ]; then
  echo PASS
else
  echo "FAIL: expected 3 revision-mismatch rejections"; fail=1
fi

# 2 -- positive fixture coherence + normative cases
say "2: rebuilt scopes.json -> coherent, 3 normative cases"
out="$(python3 "$HERE/check-fixture.py" "$HERE/scopes.json" --cases 2>&1)"
echo "$out"
echo "$out" | grep -q '^COHERENT' && echo PASS || { echo FAIL; fail=1; }

# 3 -- same-basename file under the wrong root is rejected by the builder
say "3: builder rejects producer path outside the build root"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
# a page with one anchor on file index 0
printf '<html><head></head><body><p data-sourcepos="0:1:1-0:1:5">x</p></body></html>\n' > "$TMP/probe.html"
# producer log names the PAPER-ROOT draft8.tex (same basename, wrong root)
echo "Info:source-map:source [0] $PAPER/draft8.tex" > "$TMP/build.log"
out="$(cd "$PAPER" && python3 "$WYSIWYG/make-scope-table.py" "$TMP/probe.html" \
        --src "$SANDBOX" --build-root "$SANDBOX" --producer-cwd "$PAPER" 2>&1)"
echo "$out"
rej="$(python3 -c "import json;t=json.load(open('$TMP/scopes.json'));print(t['files'][0], t['notes'])")"
echo "$rej"
if echo "$rej" | grep -q 'None' && echo "$rej" | grep -q 'REJECTED.*outside allowed build root'; then
  echo PASS
else
  echo "FAIL: outside-root producer path was not rejected"; fail=1
fi
# control: the same run with the paper root as build root accepts it
out="$(cd "$PAPER" && python3 "$WYSIWYG/make-scope-table.py" "$TMP/probe.html" \
        --src "$PAPER" --build-root "$PAPER" --producer-cwd "$PAPER" 2>&1 >/dev/null)"
ok="$(python3 -c "import json;t=json.load(open('$TMP/scopes.json'));print(t['files'][0], t['source-sha'])")"
echo "control (paper root allowed): $ok"
echo "$ok" | grep -q 'draft8.tex' && echo PASS || { echo FAIL; fail=1; }

# 4 -- checker rejects a path that escapes src-dir
say "4: checker rejects ../ path escape"
python3 - "$HERE/scopes.json" "$TMP/escape.json" <<'EOF'
import json, sys
t = json.load(open(sys.argv[1]))
t["files"][0] = "../../../../draft8.tex"   # same basename, outside src-dir
json.dump(t, open(sys.argv[2], "w"))
EOF
out="$(python3 "$HERE/check-fixture.py" "$TMP/escape.json" --page "$HERE/draft8.html" 2>&1)"
echo "$out"
echo "$out" | grep -q 'fixture/path-escape' && echo PASS || { echo FAIL; fail=1; }

echo
[ "$fail" = 0 ] && echo "ALL FIXTURE GATE TESTS PASS" || echo "FIXTURE GATE TESTS FAILED"
exit "$fail"
