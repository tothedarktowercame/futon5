#!/usr/bin/env python3
"""M-jvm-arxana INSTANTIATE increment 1: correspondence fixture gate checker.

Validates a scope table (scopes.json) against the exact source bytes it names,
per D4 "Repair and rejection procedure":

  1. every mapped file path stays inside the declared source root
     (same-basename files elsewhere are never substituted);
  2. every recorded source-sha equals the live bytes of the mapped file
     (mismatch => fixture/revision-mismatch -- the D4 negative tripwire);
  3. every page anchor names a known file index and in-bounds line/column;
  4. with --cases, the three normative correspondence cases hold:
     paragraph point anchor, section range, macro-expanded math.

Exit 0 = coherent positive fixture. Exit 1 = rejected; errors are printed as
JSON lines tagged fixture/<kind>.

Usage:
  check-fixture.py SCOPES.json [--page PAGE.html] [--cases]
"""
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

SOURCEPOS = re.compile(r'data-sourcepos="(\d+):(\d+):(\d+)-(\d+):(\d+):(\d+)"')
TAG = re.compile(
    r'<(?P<tag>p|section|h\d|math)\b(?P<attrs>[^>]*data-sourcepos="[^"]*"[^>]*)>')


def err(kind, **kw):
    print(json.dumps({"error": f"fixture/{kind}", **kw}))
    return 1


def sha16(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()[:16]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("scopes")
    ap.add_argument("--page", default=None,
                    help="defaults to <scopes-dir>/<page field>")
    ap.add_argument("--cases", action="store_true",
                    help="also verify the three normative D4 cases")
    args = ap.parse_args()

    scopes_path = Path(args.scopes)
    table = json.loads(scopes_path.read_text(encoding="utf-8"))
    srcdir = Path(table["src-dir"]).resolve()
    files = table["files"]
    hashes = table.get("source-sha", {})
    page = Path(args.page) if args.page else scopes_path.parent / table["page"]
    html = page.read_text(encoding="utf-8")

    failures = 0

    # 1. Path containment: every mapped entry resolves inside src-dir.
    resolved = {}
    for f in files:
        if f is None:
            continue
        rp = (srcdir / f).resolve()
        try:
            rp.relative_to(srcdir)
        except ValueError:
            failures += err("path-escape", file=f, src_dir=str(srcdir))
            continue
        resolved[f] = rp

    # 2. Revision match: recorded hash == live bytes of the mapped file.
    for f, recorded in hashes.items():
        rp = resolved.get(f)
        if rp is None:
            failures += err("path-escape", file=f, src_dir=str(srcdir))
            continue
        if not rp.exists():
            failures += err("source-missing", file=f, path=str(rp))
            continue
        actual = sha16(rp)
        if actual != recorded:
            failures += err("revision-mismatch", file=f,
                            recorded=recorded, actual=actual)

    # 3. Anchor bounds: known file index, in-bounds line/column.
    anchors = SOURCEPOS.findall(html)
    line_maps = {}
    for f, rp in resolved.items():
        if rp.exists():
            line_maps[f] = rp.read_text(
                encoding="utf-8", errors="replace").splitlines()
    for a in anchors:
        idx, sl, sc, _eidx, el, ec = (int(x) for x in a)
        if idx >= len(files):
            failures += err("anchor-unknown-index", index=idx)
            continue
        f = files[idx]
        if f is None or f not in line_maps:
            continue  # synthesized source (e.g. bibliography): nothing to bound
        lines = line_maps[f]
        for (ln, col) in ((sl, sc), (el, ec)):
            if ln < 1 or ln > len(lines):
                failures += err("anchor-out-of-bounds", file=f, line=ln,
                                reason="line beyond end of file")
            elif col - 1 > len(lines[ln - 1]):
                failures += err("anchor-out-of-bounds", file=f, line=ln,
                                column=col, reason="column beyond end of line")

    # 4. Normative cases.
    if args.cases and not failures:
        by_name = {f: i for i, f in enumerate(files) if f}
        intro_idx = by_name.get("intro-generated.tex")
        main_idx = by_name.get("draft8.tex")
        intro_lines = line_maps.get("intro-generated.tex", [])
        main_lines = line_maps.get("draft8.tex", [])

        # Case 1 -- paragraph point anchor: intro-generated.tex line 10, a
        # point locator (start == end) on a <p> with NO DOM id, leading
        # literal quote. Partial correspondence: no invented end.
        p1 = None
        for m in TAG.finditer(html):
            sp = SOURCEPOS.search(m.group("attrs"))
            if (sp and int(sp.group(1)) == intro_idx
                    and sp.group(2) == "10" and m.group("tag") == "p"
                    and sp.group(2) == sp.group(5) and sp.group(3) == sp.group(6)):
                p1 = m
                break
        if not p1:
            failures += err("case-paragraph-point", reason="no point-anchored <p> on intro-generated.tex line 10")
        else:
            if re.search(r'\bid=', p1.group("attrs")):
                failures += err("case-paragraph-point", reason="paragraph anchor carries a DOM id; must stay partial")
            quote = "The object of study is a finite family"
            if len(intro_lines) < 10 or not intro_lines[9].startswith(quote):
                failures += err("case-paragraph-point", reason="leading literal quote not found at line 10")

        # Case 2 -- section range: draft8.tex \section{The Object} at line
        # 316 maps to build-local DOM id S2. DOM id is locator evidence only.
        s2 = re.search(r'<section\b[^>]*\bid="S2"[^>]*>', html)
        if not s2:
            failures += err("case-section-range", reason="no element with id S2")
        else:
            sp = SOURCEPOS.search(s2.group(0))
            if not (sp and int(sp.group(1)) == main_idx and sp.group(2) == "316"):
                failures += err("case-section-range", reason="S2 does not map to draft8.tex line 316",
                                sourcepos=sp.group(0) if sp else None)
            if len(main_lines) < 316 or "The Object" not in main_lines[315] \
                    or not main_lines[315].startswith(r"\section{"):
                failures += err("case-section-range", reason="quote 'The Object' not at draft8.tex:316")

        # Case 3 -- math transformation: $P_\sig$ (macro) renders as math
        # S1.p2.m2 with alttext P_{\sigma}. Directional, not literal.
        mm = re.search(r'<math\b[^>]*\bid="S1\.p2\.m2"[^>]*>', html)
        if not mm:
            failures += err("case-math-transformation", reason="no math id S1.p2.m2")
        else:
            sp = SOURCEPOS.search(mm.group(0))
            alt = re.search(r'alttext="([^"]*)"', mm.group(0))
            alttext = alt.group(1) if alt else ""
            if not (sp and int(sp.group(1)) == intro_idx and sp.group(2) == "10"):
                failures += err("case-math-transformation", reason="S1.p2.m2 not anchored to intro-generated.tex line 10")
            elif len(intro_lines) >= 10:
                seg = intro_lines[9][int(sp.group(3)) - 1:int(sp.group(6))]
                if r"\sig" not in seg:
                    failures += err("case-math-transformation",
                                    reason=f"source range does not contain \\sig macro: {seg!r}")
            if alttext != r"P_{\sigma}":
                failures += err("case-math-transformation",
                                reason=f"alttext is {alttext!r}, expected macro-expanded P_{{\\sigma}}")

    if failures:
        print(f"REJECTED {scopes_path}: {failures} fixture error(s)", file=sys.stderr)
        return 1
    print(f"COHERENT {scopes_path}: {len(anchors)} anchors, "
          f"{len(hashes)} hashed sources" + (", 3 normative cases" if args.cases else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
