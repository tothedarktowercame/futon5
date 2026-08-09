#!/usr/bin/env python3
"""Exercise the whole edit space against the sandbox copy of the paper.

Driving edits by hand through a browser found bugs one at a time, slowly. This
enumerates edit KINDS x real paragraphs, applies each through the real Emacs
code path, checks the source afterwards, and restores. It never touches the
live paper.

For every case the outcome is one of:
  applied   - the source changed as intended and the atoms survived
  refused   - declined with a named reason, source untouched (acceptable)
  WRONG     - applied but the source is not what it should be (a real bug)
  CORRUPT   - atoms lost, or the paragraph mangled (a serious bug)

Usage:  ./edit-matrix.py [--kinds a,b,c] [--limit N] [--verbose]
"""
import argparse
import collections
import html as H
import json
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
PAPER = HERE.parent.parent
SANDBOX = HERE / "sandbox"
PAGE = HERE / "page" / "draft8.html"
PRISTINE = HERE / "sandbox" / ".pristine"

FILES = ["draft8.tex", "intro-generated.tex", "part3-exotype.tex"]


def emacs(form):
    r = subprocess.run(["emacsclient", "--eval", form],
                       capture_output=True, text=True, timeout=60)
    return (r.stdout or r.stderr).strip()


def elisp_str(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


# --------------------------------------------------------------- templates
def templates():
    """Paragraph templates from the built page: literals and atoms, in order."""
    doc = re.sub(r"<svg\b.*?</svg>", " ", PAGE.read_text(encoding="utf-8"), flags=re.S)
    out = []
    for m in re.finditer(
            r'<p[^>]*data-sourcepos="([012]):(\d+):(\d+)-[^"]*"[^>]*>(.*?)</p>',
            doc, re.S):
        f, line, col, inner = int(m.group(1)), int(m.group(2)), int(m.group(3)), m.group(4)
        tpl, buf, depth = [], [], 0
        for tok in re.split(r"(<[^>]+>)", inner):
            if tok.startswith("<"):
                if tok.startswith("</"):
                    depth -= 1
                    if depth == 0:
                        tpl.append({"k": "atom", "t": H.unescape(
                            re.sub(r"<[^>]+>", "", "".join(buf)))})
                        buf = []
                elif not tok.endswith("/>"):
                    if depth == 0 and buf:
                        tpl.append({"k": "lit", "t": H.unescape("".join(buf))}); buf = []
                    depth += 1
                continue
            buf.append(tok)
        if buf:
            tpl.append({"k": "lit", "t": H.unescape("".join(buf))})
        tpl = [e for e in tpl if e["t"]]
        if any(e["k"] == "lit" and e["t"].strip() for e in tpl):
            out.append({"file": f, "line": line, "col": col, "tpl": tpl})
    return out


# ------------------------------------------------------------- edit kinds
SHAPE = [r"\$", r"\\ref\{", r"\\eqref\{", r"\\parencite", r"\\textcite",
         r"\\cite\{", r"\\emph\{", r"\\secref\{", r"\\suppfindingref",
         r"\\supptheorytext", r"\\footnote", r"\\begin\{", r"\\end\{"]


def source_shape(text):
    """Counts of the source constructs an edit must never disturb."""
    return tuple(len(re.findall(p, text)) for p in SHAPE)


def longest_lit(tpl):
    best = -1
    for i, e in enumerate(tpl):
        if e["k"] == "lit" and (best < 0 or len(e["t"]) > len(tpl[best]["t"])):
            best = i
    return best


def make_case(kind, tpl):
    """Return (new_template, expect_substring) or None if not applicable."""
    new = [dict(e) for e in tpl]
    i = longest_lit(new)
    if i < 0 or len(new[i]["t"].strip()) < 12:
        return None
    words = new[i]["t"].split()
    long_word = next((w for w in words if re.fullmatch(r"[A-Za-z]{5,}", w)), None)

    if kind == "replace-word":
        if not long_word: return None
        new[i]["t"] = new[i]["t"].replace(long_word, "MXREPL", 1)
        return new, "MXREPL"
    if kind == "insert-word":
        new[i]["t"] = re.sub(r"(\s)", r"\1MXINS ", new[i]["t"], count=1)
        return new, "MXINS"
    if kind == "delete-word":
        if not long_word: return None
        new[i]["t"] = new[i]["t"].replace(long_word + " ", "", 1)
        return new, None
    if kind == "append-sentence":
        new[i]["t"] = new[i]["t"].rstrip() + " MXAPPEND ends here."
        return new, "MXAPPEND"
    if kind == "edit-first-lit":
        j = next((k for k, e in enumerate(new) if e["k"] == "lit" and e["t"].strip()), None)
        if j is None: return None
        new[j]["t"] = "MXFIRST " + new[j]["t"].lstrip()
        return new, "MXFIRST"
    if kind == "edit-last-lit":
        j = next((k for k in range(len(new) - 1, -1, -1)
                  if new[k]["k"] == "lit" and new[k]["t"].strip()), None)
        if j is None: return None
        new[j]["t"] = new[j]["t"].rstrip() + " MXLAST"
        return new, "MXLAST"
    if kind == "lit-after-atom":
        j = next((k for k in range(1, len(new))
                  if new[k]["k"] == "lit" and new[k - 1]["k"] == "atom"
                  and new[k]["t"].strip()), None)
        if j is None: return None
        new[j]["t"] = new[j]["t"].rstrip() + " MXAFTER"
        return new, "MXAFTER"
    if kind == "lit-before-atom":
        j = next((k for k in range(len(new) - 1)
                  if new[k]["k"] == "lit" and new[k + 1]["k"] == "atom"
                  and new[k]["t"].strip()), None)
        if j is None: return None
        new[j]["t"] = new[j]["t"].rstrip() + " MXBEFORE "
        return new, "MXBEFORE"
    if kind == "two-literals":
        lits = [k for k, e in enumerate(new) if e["k"] == "lit" and len(e["t"].strip()) > 8]
        if len(lits) < 2: return None
        new[lits[0]]["t"] = new[lits[0]]["t"].rstrip() + " MXTWO1 "
        new[lits[1]]["t"] = new[lits[1]]["t"].rstrip() + " MXTWO2 "
        return new, "MXTWO2"
    if kind in ("delete-atom", "retex-atom"):
        # atom index among atoms only (that is what the server counts)
        n = -1
        for e in tpl:
            if e["k"] == "atom":
                n += 1
                if e["t"].strip():
                    return ("ATOMOP", n), None
        return None
    return None


KINDS = ["replace-word", "insert-word", "delete-word", "append-sentence",
         "edit-first-lit", "edit-last-lit", "lit-after-atom", "lit-before-atom",
         "two-literals", "delete-atom", "retex-atom"]


# ------------------------------------------------------------------ driver
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--kinds", default=",".join(KINDS))
    ap.add_argument("--limit", type=int, default=6, help="paragraphs per kind")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()

    PRISTINE.mkdir(exist_ok=True)
    for f in FILES:
        (PRISTINE / f).write_bytes((SANDBOX / f).read_bytes())

    # Remember the operator's map: leaving Emacs pointed at the sandbox when
    # this exits means the next browser edit silently lands in a throwaway
    # copy. That nearly happened once; restore it in a finally.
    scopes = HERE / "page" / "scopes.json"
    tbl = json.loads(scopes.read_text())
    sandbox_scopes = SANDBOX / "scopes.json"
    tbl["src-dir"] = str(SANDBOX)
    sandbox_scopes.write_text(json.dumps(tbl))
    emacs(f'(progn (latex-wysiwyg-start {elisp_str(str(sandbox_scopes))})'
          f' (setq latex-wysiwyg-allow-edits t latex-wysiwyg-save-after-edit t)'
          f' (setq latex-wysiwyg-journal-file "/tmp/matrix-journal.jsonl") t)')

    paras = templates()
    print(f"  {len(paras)} paragraphs available\n")
    results = collections.defaultdict(collections.Counter)
    reasons = collections.defaultdict(collections.Counter)
    bugs = []

    for kind in args.kinds.split(","):
        done = 0
        for p in paras:
            if done >= args.limit:
                break
            case = make_case(kind, p["tpl"])
            if not case:
                continue
            new, expect = case
            done += 1
            tgt = SANDBOX / FILES[p["file"]]
            before = tgt.read_text(encoding="utf-8")

            if isinstance(new, tuple) and new[0] == "ATOMOP":
                op = "(quote delete)" if kind == "delete-atom" else "(quote retex)"
                tex = "nil" if kind == "delete-atom" else elisp_str("X_{\\\\mathrm{mx}}")
                form = ('(latex-wysiwyg--atom-op {f} {l} {c} (json-parse-string {o} '
                        ':array-type (quote array) :object-type (quote alist)) '
                        '{a} {op} {tex})').format(
                    f=p["file"], l=p["line"], c=p["col"],
                    o=elisp_str(json.dumps(p["tpl"])), a=new[1], op=op, tex=tex)
                expect = None if kind == "delete-atom" else "mx"
            else:
                form = ('(latex-wysiwyg--edit-template {f} {l} {c} (json-parse-string {o} '
                        ':array-type (quote array) :object-type (quote alist)) '
                        '(json-parse-string {n} :array-type (quote array) '
                        ':object-type (quote alist)))').format(
                    f=p["file"], l=p["line"], c=p["col"],
                    o=elisp_str(json.dumps(p["tpl"])), n=elisp_str(json.dumps(new)))
            out = emacs(form)
            after = tgt.read_text(encoding="utf-8")

            if ":applied t" in out:
                # Compare SOURCE-level structure, not rendered text: an atom's
                # rendered form ("P_sigma") never appears in the source, which
                # holds "$P_\\sig$". Counting the source constructs is the
                # honest test that nothing was swallowed.
                atoms_ok = (kind in ("delete-atom", "retex-atom")
                            or source_shape(before) == source_shape(after))
                if expect and expect not in after:
                    results[kind]["WRONG"] += 1
                    bugs.append((kind, p["line"], "applied but marker absent"))
                elif not atoms_ok:
                    results[kind]["CORRUPT"] += 1
                    bugs.append((kind, p["line"], "atom text lost from source"))
                else:
                    results[kind]["applied"] += 1
            elif after != before:
                results[kind]["CORRUPT"] += 1
                bugs.append((kind, p["line"], "refused but file changed"))
            else:
                results[kind]["refused"] += 1
                r = re.search(r':reason "([^"]+)"', out)
                reasons[kind][r.group(1) if r else "?"] += 1

            # restore through Emacs so it stays the single writer
            emacs(f'(latex-wysiwyg-restore-file {elisp_str(str(tgt))} '
                  f'{elisp_str(str(PRISTINE / FILES[p["file"]]))})')

    print(f"  {'kind':<18} {'applied':>8} {'refused':>8} {'WRONG':>6} {'CORRUPT':>8}   top refusal")
    for kind in args.kinds.split(","):
        c = results[kind]
        top = reasons[kind].most_common(1)
        print(f"  {kind:<18} {c['applied']:>8} {c['refused']:>8} {c['WRONG']:>6} "
              f"{c['CORRUPT']:>8}   {top[0][0] + ' x' + str(top[0][1]) if top else ''}")
    tot = sum(sum(c.values()) for c in results.values())
    ok = sum(c["applied"] for c in results.values())
    # hand the operator's own page back before reporting
    emacs(f'(progn (latex-wysiwyg-start {elisp_str(str(scopes))})'
          f' (setq latex-wysiwyg-journal-file nil) t)')
    print(f"\n  {ok}/{tot} applied cleanly; "
          f"{sum(c['WRONG'] + c['CORRUPT'] for c in results.values())} incorrect")
    if bugs and args.verbose:
        print("\n  incorrect cases:")
        for b in bugs[:15]:
            print(f"    {b[0]} line {b[1]}: {b[2]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
