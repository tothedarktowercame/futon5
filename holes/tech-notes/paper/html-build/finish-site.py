#!/usr/bin/env python3
"""Post-build step for the LaTeXML site (Perl or oxide).

Does four things the converter cannot:

  1. NAV -- injects a shared navigation bar into each page.

  2. CROSS-DOCUMENT REFERENCES -- in print these are handled by the xr package
     reading another document's .aux; neither converter has an equivalent.
     Perl LaTeXML renders them as the literal text "LABEL:sec:object"; oxide
     renders them as an EMPTY span, keeping the label only in its XML as
     labelref="...". We build a label -> (page, anchor) index across every
     document and turn both forms into real links. For oxide the empty spans
     are matched to the XML's labelrefs by document order (counts are asserted
     equal before substituting).

  3. BIBLIOGRAPHY DEDUP -- oxide emits TWO <section class="ltx_bibliography">
     (ids "bib" and "biba"), one from \\addbibresource and one from
     \\printbibliography, each with the full cited list. All citations link to
     the second. We drop any bibliography section nothing links into.

  4. INDEX -- writes the landing page.
"""
import argparse
import html
import re
from pathlib import Path

PAGES = [
    ("index-paper", "Main paper",     "The paper itself."),
    ("supplement",  "S4 · Domains",   "Exploratory domain analysis and a withdrawn statistic."),
    ("supplement1", "S1 · Findings",  "Empirical findings, boxed and numbered."),
    ("supplement2", "S2 · Theory",    "Definitions, theorems and worked examples."),
    ("supplement3", "S3 · Figures",   "Supplementary figure galleries."),
    ("supplement5", "S5 · Apparatus", "Apparatus and further analysis."),
]


def text_of(fragment: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", fragment)).strip()


def nav_html(current: str) -> str:
    items = ['<a class="site-nav-home" href="index.html">Rule-Rewriting CA</a>']
    for slug, label, _ in PAGES:
        cur = ' aria-current="page"' if slug == current else ""
        items.append(f'<a href="{slug}.html"{cur}>{html.escape(label)}</a>')
    return '<nav class="site-nav">' + "".join(items) + "</nav>"


# ----------------------------------------------------------------- labels ---
def build_label_index(xmldir: Path) -> dict:
    """label -> (slug, anchor, refnum, title) across all converted documents."""
    index = {}
    for slug, _, _ in PAGES:
        src = xmldir / f"{slug}.xml"
        if not src.exists():
            continue
        s = src.read_text(encoding="utf-8")
        for m in re.finditer(r"<(\w+)\s([^>]*\blabels=\"([^\"]+)\"[^>]*)>", s):
            attrs, labels = m.group(2), m.group(3)
            xid = re.search(r'xml:id="([^"]+)"', attrs)
            if not xid:
                continue
            refnum = re.search(r'refnum="([^"]*)"', attrs)
            near = s[m.end():m.end() + 1500]
            tm = re.search(r"<(?:toc)?title[^>]*>(.*?)</(?:toc)?title>", near, re.S)
            for label in labels.split():
                index[label] = (slug, xid.group(1),
                                refnum.group(1) if refnum else None,
                                text_of(tm.group(1)) if tm else None)
    return index


def link_for(label, index, slug, wants_name):
    entry = index.get(label)
    if not entry:
        return None
    tslug, anchor, refnum, title = entry
    text = (title if wants_name else refnum) or title or refnum or label[6:]
    href = f"#{anchor}" if tslug == slug else f"{tslug}.html#{anchor}"
    return f'<a href="{href}" class="ltx_ref xdoc-ref">{html.escape(text)}</a>'


PERL_MISSING = re.compile(
    r'<span class="([^"]*ltx_missing_label[^"]*)"[^>]*>(LABEL:[^<]+)</span>')
OXIDE_MISSING = re.compile(
    r'<span class="([^"]*ltx_missing_label[^"]*)"[^>]*>\s*</span>')


def resolve_refs(page: str, index: dict, slug: str, xml: Path):
    """Rewrite unresolved references into cross-document links."""
    stats = {"resolved": 0, "unresolved": 0}

    def finish(cls, label):
        out = link_for(label, index, slug, "nameref" in cls)
        if out:
            stats["resolved"] += 1
            return out
        stats["unresolved"] += 1
        return (f'<span class="ltx_ref xdoc-unresolved" '
                f'title="unresolved reference">{html.escape(label[6:])}</span>')

    # Perl form: the label is in the span's text.
    page = PERL_MISSING.sub(lambda m: finish(m.group(1), m.group(2)), page)

    # oxide form: spans are empty; recover the labels from the XML, in order.
    # Only references whose target is NOT defined in this same document end up
    # as empty spans -- internal ones resolve normally but still carry a
    # labelref attribute, so they must be filtered out before pairing up.
    empties = OXIDE_MISSING.findall(page)
    if empties and xml.exists():
        xs = xml.read_text(encoding="utf-8")
        local = set()
        for attr in re.findall(r'labels="([^"]+)"', xs):
            local.update(attr.split())
        labels = [l for l in re.findall(r'labelref="(LABEL:[^"]+)"', xs)
                  if l not in local]
        if len(labels) != len(empties):
            # A \ref{#1} inside a \newcommand body is recorded once for the
            # definition and once for each use, so the same label can appear
            # twice in a row for a single rendered reference. Collapsing runs
            # of identical labels recovers the pairing.
            collapsed = [l for i, l in enumerate(labels) if i == 0 or l != labels[i - 1]]
            if len(collapsed) == len(empties):
                labels = collapsed
        if len(labels) == len(empties):
            it = iter(labels)
            page = OXIDE_MISSING.sub(lambda m: finish(m.group(1), next(it)), page)
        else:
            print(f"  ! {slug}: {len(empties)} empty ref spans but "
                  f"{len(labels)} labelrefs in XML — skipping (order unsafe)")
    return page, stats


# ----------------------------------------------------------- bibliographies --
def drop_orphan_bibliographies(page: str, slug: str) -> str:
    """Remove any <section class="ltx_bibliography"> that nothing links into."""
    while True:
        m = re.search(r'<section id="([^"]+)" class="ltx_bibliography">', page)
        removed = False
        for m in re.finditer(r'<section id="([^"]+)" class="ltx_bibliography">', page):
            sec_id = m.group(1)
            if re.search(rf'href="#{re.escape(sec_id)}\.', page):
                continue                      # something cites into it: keep
            # Scan to the matching </section>, honouring nesting.
            depth, i = 0, m.start()
            for tag in re.finditer(r'<section\b|</section>', page[m.start():]):
                depth += 1 if tag.group(0).startswith("<section") else -1
                if depth == 0:
                    end = m.start() + tag.end()
                    break
            else:
                break
            print(f"  {slug}: dropped orphan bibliography #{sec_id}")
            page = page[:m.start()] + page[end:]
            removed = True
            break
        if not removed:
            return page


# ---------------------------------------------------------------- assembly --
def extract(page: Path):
    s = page.read_text(encoding="utf-8")
    m = re.search(r"<title>(.*?)</title>", s, re.S)
    title = text_of(m.group(1)) if m else page.stem
    m = re.search(r'<div class="ltx_abstract">(.*?)</div>', s, re.S)
    return title, re.sub(r"^Abstract\s*", "", text_of(m.group(1)) if m else "")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("outdir")
    ap.add_argument("--xml", default=None, help="directory of converter XML")
    args = ap.parse_args()

    out = Path(args.outdir)
    xmldir = Path(args.xml) if args.xml else out.parent / "xml"
    index = build_label_index(xmldir)
    print(f"  label index: {len(index)} labels")

    built, total = [], {"resolved": 0, "unresolved": 0}
    for slug, label, blurb in PAGES:
        page = out / f"{slug}.html"
        if not page.exists():
            print(f"  ! missing {page.name}")
            continue
        s = page.read_text(encoding="utf-8")
        s = drop_orphan_bibliographies(s, slug)
        s, stats = resolve_refs(s, index, slug, xmldir / f"{slug}.xml")
        # oxide writes its own <link> tags and knows nothing of our stylesheet;
        # add it last so it overrides LaTeXML.css/ltx-article.css.
        if "paper-site.css" not in s:
            s, k = re.subn(r"(</head>)",
                           '<link rel="stylesheet" href="paper-site.css" '
                           'type="text/css">\\1', s, count=1)
            if not k:
                print(f"  ! no </head> in {page.name}")
        s, n = re.subn(r"(<body[^>]*>)", r"\1" + nav_html(slug), s, count=1)
        if not n:
            print(f"  ! no <body> in {page.name}")
        page.write_text(s, encoding="utf-8")
        for k in total:
            total[k] += stats[k]
        if stats["resolved"] or stats["unresolved"]:
            print(f"  {slug}: {stats['resolved']} xrefs resolved, "
                  f"{stats['unresolved']} unresolved")
        t, a = extract(page)
        built.append((slug, label, blurb, t, a))
    print(f"  TOTAL: {total['resolved']} resolved, {total['unresolved']} unresolved")

    cards = "".join(
        f'<li class="card"><a href="{slug}.html"><h2>{html.escape(t)}</h2></a>'
        f'<p class="card-label">{html.escape(label)}</p>'
        f'<p class="card-blurb">{html.escape((a[:340] + "…") if len(a) > 340 else (a or blurb))}</p></li>'
        for slug, label, blurb, t, a in built)

    (out / "index.html").write_text(f"""<!doctype html>
<html lang="en-US"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Rule-Rewriting Cellular Automata and the Edge of Chaos</title>
<link rel="stylesheet" href="paper-site.css">
<style>
  .home {{ max-width: 46rem; margin: 0 auto; padding: 0 1.25rem 6rem; }}
  .home h1 {{ font-size: 2rem; line-height: 1.2; margin: 2.5rem 0 .4rem; }}
  .home .byline {{ color: var(--ink-muted); margin: 0 0 2.5rem; }}
  .cards {{ list-style: none; padding: 0; margin: 0; }}
  .card {{ border-top: 1px solid var(--rule); padding: 1.4rem 0; }}
  .card h2 {{ font-size: 1.12rem; margin: 0 0 .2rem; }}
  .card a {{ text-decoration: none; }}
  .card-label {{ margin: 0 0 .5rem; font-size: .74rem; letter-spacing: .09em;
                 text-transform: uppercase; color: var(--ink-muted);
                 font-family: system-ui, sans-serif; }}
  .card-blurb {{ margin: 0; color: var(--ink-muted); font-size: .93rem; }}
  .colophon {{ margin-top: 3.5rem; padding-top: 1.2rem;
               border-top: 1px solid var(--rule);
               font-size: .85rem; color: var(--ink-muted); }}
</style></head>
<body>{nav_html("index")}
<main class="home">
  <h1>Rule-Rewriting Cellular Automata and the Edge of Chaos</h1>
  <p class="byline">Joseph Corneli · Oxford Brookes University · Hyperreal Enterprises Ltd</p>
  <ul class="cards">{cards}</ul>
  <p class="colophon">Built from the LaTeX sources with latexml-oxide.</p>
</main></body></html>
""", encoding="utf-8")
    print(f"  index.html written with {len(built)} entries")


if __name__ == "__main__":
    main()
