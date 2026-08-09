#!/usr/bin/env python3
"""Build step for M-latex-wysiwyg slice S1.

Takes a latexml-oxide page built with --source-map and:

  1. resolves the opaque file indices in `data-sourcepos` to real filenames,
  2. writes scopes.json (the mapping Emacs loads),
  3. injects the WYSIWYG client script into the page.

On (1): oxide DOES emit the tag->file table -- to the .log, deliberately, so
the HTML stays anonymisable (see converter.rs: "keeping it out of the HTML
keeps that output anonymisable"). It is Source-Map-v3 `sources`-style, the
array index being the tag. Read it from there.

This replaces an earlier text-fingerprinting resolver written because I had
looked in the HTML and XML but not the log. It was not merely redundant: with
byte-identical copies (draft8.tex vs draft8a.tex) probes match both files
equally and the resolver goes ambiguous, which the log never does.

Usage:
  make-scope-table.py PAGE.html --src DIR [--out DIR]
"""
import argparse
import collections
import json
import re
import sys
from pathlib import Path

SOURCEPOS = re.compile(r'data-sourcepos="(\d+):(\d+):(\d+)-(\d+):(\d+):(\d+)"')
# Text immediately following a paragraph-ish anchored tag, for fingerprinting.
SAMPLE = re.compile(
    r'<(?:p|div|section|h\d)[^>]*data-sourcepos="(\d+):(\d+):(\d+)-[^"]*"[^>]*>(.{0,90})',
    re.S)


def strip_tags(s: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", s)).strip()


def input_order_guess(main: Path) -> list:
    """Index 0 = the main file, then each \\input in order of appearance."""
    order = [main.name]
    try:
        text = main.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return order
    body = text.split(r"\begin{document}", 1)
    scan = body[1] if len(body) > 1 else text
    for m in re.finditer(r"\\input\{([^}]+)\}", scan):
        name = m.group(1)
        if not name.endswith(".tex"):
            name += ".tex"
        if name not in order:
            order.append(name)
    return order


LOGLINE = re.compile(r"^Info:source-map:source\s+\[(\d+)\]\s+(.+?)\s*$", re.M)


def resolve_from_log(logfile: Path) -> dict:
    """index -> filename, from oxide's own source-map log lines."""
    if not logfile.exists():
        return {}
    out = {}
    for m in LOGLINE.finditer(logfile.read_text(encoding="utf-8", errors="replace")):
        out[int(m.group(1))] = Path(m.group(2)).name
    return out


def resolve_by_matching(html: str, srcdir: Path) -> dict:
    """index -> (filename, confidence) by fingerprinting anchored text."""
    samples = collections.defaultdict(list)
    for m in SAMPLE.finditer(html):
        idx, line = int(m.group(1)), int(m.group(2))
        txt = strip_tags(m.group(4))
        if len(txt) >= 24:
            samples[idx].append((line, txt))

    candidates = {}
    for p in sorted(srcdir.glob("*.tex")):
        try:
            candidates[p.name] = p.read_text(encoding="utf-8",
                                             errors="replace").splitlines()
        except OSError:
            pass

    # Fingerprint on CONTENT, not on line proximity. Rendered text differs from
    # source (macros expanded, quotes curled, math lifted out) and paragraphs
    # are hard-wrapped, so matching a probe against the source line at the
    # stated offset is unreliable -- it scored draft8 at 8% while its siblings
    # scored 90%. Searching the whole flattened file for a run of plain words
    # discriminates cleanly, because the three sources share no prose.
    flat = {name: re.sub(r"\s+", " ", "\n".join(lines))
            for name, lines in candidates.items()}

    def probe_of(txt):
        runs = re.findall(r"[A-Za-z][A-Za-z ,'-]{15,}", txt)
        return re.sub(r"\s+", " ", max(runs, key=len)).strip()[:28] if runs else None

    resolved = {}
    for idx, rows in samples.items():
        score, tried = collections.Counter(), 0
        for _line, txt in rows[:40]:
            probe = probe_of(txt)
            if not probe:
                continue
            tried += 1
            for name, body in flat.items():
                if probe in body:
                    score[name] += 1
        if score and tried:
            top = score.most_common(2)
            name, hits = top[0]
            runner = top[1][1] if len(top) > 1 else 0
            # This is a CLASSIFICATION, not a recall measurement. Dense prose
            # (inline math, \parencite, ~ and -- ligatures) breaks most long
            # probes, so absolute hit rate runs low -- draft8 scores 13% where
            # its siblings score ~100%. What matters is the margin: the three
            # sources share no prose, so a decisive winner is decisive even at
            # a low rate. Accept on separation, and record both numbers.
            resolved[idx] = (name, hits / tried, hits, runner)
    return resolved


CLIENT_TAG = '<script src="wysiwyg-client.js" defer></script>'


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("page")
    ap.add_argument("--src", required=True, help="directory holding the .tex sources")
    ap.add_argument("--main", default="draft8.tex")
    ap.add_argument("--out", default=None, help="defaults to the page's directory")
    args = ap.parse_args()

    page = Path(args.page)
    srcdir = Path(args.src)
    outdir = Path(args.out) if args.out else page.parent
    outdir.mkdir(parents=True, exist_ok=True)

    html = page.read_text(encoding="utf-8")
    anchors = SOURCEPOS.findall(html)
    if not anchors:
        print("error: no data-sourcepos found -- was the page built with "
              "--source-map?", file=sys.stderr)
        return 1

    indices = sorted({int(a[0]) for a in anchors})
    from_log = resolve_from_log(page.with_suffix("").with_suffix("") .parent / "build.log")
    resolved = resolve_by_matching(html, srcdir) if not from_log else {}
    guess = input_order_guess(srcdir / args.main)

    files, notes = [], []
    for idx in range(max(indices) + 1):
        if from_log:
            name = from_log.get(idx)
            files.append(name if name and (srcdir / name).exists() else None)
            if name and not (srcdir / name).exists():
                notes.append(f"index {idx}: {name} (generated/derived; not editable)")
            continue
        got = resolved.get(idx)
        gss = guess[idx] if idx < len(guess) else None
        decisive = got and got[2] >= 3 and got[2] >= 3 * max(got[3], 1) 
        if decisive:
            files.append(got[0])
            if gss and gss != got[0]:
                notes.append(f"index {idx}: matched {got[0]} but \\input order "
                             f"suggested {gss} -- trusting the match")
        elif gss:
            files.append(gss)
            notes.append(f"index {idx}: no text to fingerprint "
                         f"(preamble-only?), falling back to \\input order: {gss}")
        else:
            # null, not a placeholder path: Emacs must refuse rather than
            # visit a nonexistent file and leave a junk buffer behind. On this
            # paper index 3 is the GENERATED bibliography -- its line numbers
            # (708..1396) exceed every real source, so it is synthesised and
            # correctly has nowhere to jump to.
            files.append(None)
            notes.append(f"index {idx}: unmapped (synthesised source, e.g. the "
                         f"generated bibliography) -- clicks there are no-ops")

    table = {
        "generated-by": "make-scope-table.py (M-latex-wysiwyg S1)",
        "page": page.name,
        # Absolute, because the page and the sources live in different
        # directories: resolving relative to scopes.json pointed Emacs at
        # <page-dir>/draft8.tex and every jump was refused as no-such-file.
        "src-dir": str(srcdir.resolve()),
        "files": files,
        "anchor-count": len(anchors),
        # Hash every mapped source at build time. A page whose sources have
        # moved on will refuse every edit with "literal-not-found", which is
        # correct but inscrutable -- this makes staleness detectable instead.
        "source-sha": {f: __import__("hashlib").sha256((srcdir / f).read_bytes())
                              .hexdigest()[:16]
                       for f in files if f and (srcdir / f).exists()},
        "confidence": {str(i): {"rate": round(resolved[i][1], 3),
                                "hits": resolved[i][2],
                                "runner_up": resolved[i][3]}
                       for i in resolved},
        "notes": notes,
    }
    (outdir / "scopes.json").write_text(json.dumps(table, indent=2) + "\n",
                                        encoding="utf-8")

    if CLIENT_TAG not in html:
        html2, n = re.subn(r"(</head>)", CLIENT_TAG + r"\1", html, count=1)
        if not n:
            print("error: no </head> to inject the client into", file=sys.stderr)
            return 1
        page.write_text(html2, encoding="utf-8")

    print(f"  scopes.json: {len(anchors)} anchors, {len(files)} files"
          + ("  (file table from oxide's log)" if from_log else ""))
    for i, f in enumerate(files):
        c = resolved.get(i)
        print(f"    [{i}] {f}" + (f"   ({c[2]} hits vs {c[3]} runner-up, {c[1]:.0%})"
                                  if c else "   (no fingerprint)"))
    for note in notes:
        print(f"    ! {note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
