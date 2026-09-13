#!/usr/bin/env python3
"""Re-typeset an oxide-converted paper in Tufte style, with a live margin.

Three transforms, in order of how much they help:

1. CROSS-REFERENCES.  LaTeXML expands \\secref into the *whole section title*,
   inline. Because this paper's titles are full sentences ("Feedback Roughly
   Doubles How Far a Flip Travels"), a sentence that references three sections
   becomes unreadable. We put a short marker in the text (S10, Fig. 3, Eq. (1))
   and the title in the margin, once per target per section so the margin does
   not flood.

2. FOOTNOTES -> SIDENOTES.  Tufte's actual argument: a note you must jump to
   is a note you will not read. These are already adjacent in the source; they
   just need to be beside the line instead of under the page.

3. MEASURE.  A main column at reading measure with the margin permanently
   occupied, rather than full-width text with notes collected at the bottom.

`data-sourcepos` is preserved on every element it was on, so the result stays
compatible with the WYSIWYG source map.

Usage:  ./tuftify.py page/draft9.html -o page/draft9-tufte.html
"""
import argparse
import re
import shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent
TUFTE_SRC = Path.home() / "code" / "futon7a" / "tufte.css"


# ------------------------------------------------------------------ helpers
def short_label(href: str, text: str) -> str:
    """A compact marker for a cross-reference target."""
    frag = href.lstrip("#")
    m = re.match(r"^S(\d+)(?:\.SS(\d+))?(?:\.SSS(\d+))?$", frag)
    if m:
        parts = [g for g in m.groups() if g]
        return "§" + ".".join(parts)
    m = re.match(r"^S[\d.]*\.F(\d+)", frag)
    if m:
        return f"Fig. {m.group(1)}"
    m = re.match(r"^S[\d.]*\.E(\d+)", frag)
    if m:
        return f"Eq. ({m.group(1)})"
    m = re.match(r"^S[\d.]*\.T(\d+)", frag)
    if m:
        return f"Table {m.group(1)}"
    m = re.match(r"^S[\d.]*\.Thmthm(\d+)", frag)
    if m:
        return f"Thm. {m.group(1)}"
    # Part openers and anything unrecognised keep a trimmed form of their text
    flat = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", text)).strip()
    return flat if len(flat) <= 24 else flat[:22].rstrip() + "…"


def is_citation(href: str) -> bool:
    return "bibx" in href or href.startswith("#bib")


# The class is "ltx_ref ltx_refmacro_nameref" for \\secref, and the visible
# title is nested in <span class="ltx_ref_title"> with the number in its own
# <span class="ltx_tag_ref"> -- so neither an exact class match nor a flat-text
# match finds them.
REF = re.compile(
    r'<a\s([^>]*?)href="(#[^"]+)"([^>]*?)class="(ltx_ref[^"]*)"([^>]*?)>(.*?)</a>', re.S)
TAGREF = re.compile(r'<span class="ltx_tag ltx_tag_ref">([^<]*)</span>')
NOTE = re.compile(
    r'<span id="(footnote\d+)" class="ltx_note ltx_role_footnote"([^>]*)>(.*?)</span></span></span>',
    re.S)
SECTION_ANCHOR = re.compile(r'<section[^>]*id="(S\d+[^"]*)"|<div[^>]*id="(S\d+)"[^>]*class="ltx_section')


def fix_refs(html: str) -> tuple:
    """Shorten cross-reference text; surface the title in the margin once each."""
    seen = set()
    count = [0, 0]

    def repl(m):
        pre, href, mid, cls, post, text = m.groups()
        if is_citation(href):
            return m.group(0)
        flat = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", text)).strip()
        # LaTeXML already prints the number; prefer it over parsing the href.
        tag = TAGREF.search(text)
        short = ("\u00a7" + tag.group(1).strip()) if tag else short_label(href, text)
        if tag and re.match(r"^\u00a7(Figure|Table|Eq)", short):
            short = tag.group(1).strip()
        # Only rewrite when the visible text really is a long title; a link
        # that already reads "Figure 3" is fine as it stands.
        if len(flat) <= 26 or flat == short:
            return m.group(0)
        count[0] += 1
        link = (f'<a {pre}href="{href}"{mid}class="{cls} ltx_ref_short"{post}>'
                f'{short}</a>')
        if href in seen:
            return link
        seen.add(href)
        count[1] += 1
        # The marker already prints the number; repeating it in the margin
        # gives "S3  3 Fixed Rules Exist...".
        title = re.sub(r"^[\d.]+\s*", "", flat)
        note = (f'<span class="marginnote"><span class="mn-kind">{short}</span>'
                f'{title}</span>')
        return link + note

    return REF.sub(repl, html), count


FIGURE = re.compile(r'(<figure[^>]*?class=")([^"]*)("[^>]*>)(.*?)(</figure>)', re.S)
ASSET = re.compile(r'(?:src|data)="([^"]+\.(?:png|jpg|jpeg|svg))"')


def asset_width(page_dir, ref):
    """True pixel width of a figure asset, or None."""
    f = page_dir / ref.split("/")[-1]
    if not f.exists():
        return None
    if f.suffix.lower() == ".svg":
        head = f.read_text(encoding="utf-8", errors="replace")[:2000]
        m = re.search(r'\bwidth="([\d.]+)', head) or re.search(
            r'viewBox="[\d.\-]+ [\d.\-]+ ([\d.]+)', head)
        return float(m.group(1)) if m else None
    import struct
    b = f.read_bytes()[:32]
    if b[:8] == b"\x89PNG\r\n\x1a\n":
        return struct.unpack(">I", b[16:20])[0]
    return None



def fix_bibliography(html: str) -> tuple:
    """Drop the converter's duplicate bibliography and repair citation years.

    apa7.cls loads biblatex, whose .bbl holds ONE refsection with TWO
    \\datalist blocks: apa/apasortcite (citation ordering) and apa/global (the
    list that actually gets printed). Real LaTeX prints only the second -- the
    PDF has one References section and bare years. The converter renders both,
    so the section appears twice AND every entry looks like a duplicate to
    biblatex's extradate logic, which suffixes years that should be bare
    ("Stanley 1999a") and shifts the genuinely ambiguous ones out of step
    (Anthropic 2026b/2026c, where the PDF has 2026a/2026b).

    Keep the section the citations actually link to, then recompute every
    suffix from the entries themselves: a name+year used once loses its
    suffix, one used N times gets a, b, c in document order.
    """
    sections = []
    for m in re.finditer(r'<section[^>]*\bid="([^"]+)"[^>]*class="[^"]*ltx_bibliography[^"]*"[^>]*>'
                         r'|<section[^>]*class="[^"]*ltx_bibliography[^"]*"[^>]*\bid="([^"]+)"[^>]*>',
                         html):
        sid = m.group(1) or m.group(2)
        depth, i = 1, m.end()
        while depth and i < len(html):
            nxt = re.search(r'</?section\b', html[i:])
            if not nxt:
                break
            closing = nxt.group().startswith('</')   # test the MATCH, not the
            i += nxt.end()                           # text that preceded it
            depth += -1 if closing else 1
        sections.append((sid, m.start(), i))
    if len(sections) < 2:
        return html, (0, 0)

    # Keep whichever section the in-text citations resolve to.
    def incoming(sid, a, b):
        outside = html[:a] + html[b:]
        return len(re.findall(r'href="#%s\.' % re.escape(sid), outside))
    scored = [(incoming(sid, a, b), sid, a, b) for sid, a, b in sections]
    keep = max(scored, key=lambda t: t[0])
    dropped = 0
    for n, sid, a, b in sorted(scored, key=lambda t: -t[2]):
        if sid != keep[1]:
            html = html[:a] + html[b:] + ''
            dropped += 1

    # Recompute the year suffixes from the surviving entries.
    ks, ke = keep[2], keep[3]
    if ke > ks:
        shift = 0
        for _, sid, a, b in scored:
            if sid != keep[1] and a < ks:
                shift += (b - a)
        ks, ke = ks - shift, ke - shift
    seg = html[ks:ke]
    TAG = re.compile(r'(<span[^>]*ltx_tag_bibitem[^>]*>)(.*?)(</span>)', re.S)
    entries, groups = [], {}
    for m in TAG.finditer(seg):
        label = re.sub(r'<[^>]+>', '', m.group(2)).strip()
        ym = re.search(r'(\d{4})([a-z]?)', label)
        if not ym:
            continue
        name = label[:ym.start()].strip(' (')
        entries.append((m.group(2), name, ym.group(1), ym.group(0)))
        groups.setdefault((name, ym.group(1)), []).append(len(entries) - 1)
    fixes = {}
    for (name, year), idxs in groups.items():
        for n, idx in enumerate(idxs):
            new = year if len(idxs) == 1 else year + chr(ord('a') + n)
            if new != entries[idx][3]:
                fixes[entries[idx][3] + '\x00' + name] = new

    def relabel(text):
        ym = re.search(r'(\d{4}[a-z]?)', text)
        if not ym:
            return text, None
        name = text[:ym.start()].strip(' (')
        new = fixes.get(ym.group(1) + '\x00' + name)
        if not new:
            return text, None
        return text[:ym.start()] + new + text[ym.end():], new

    changed = [0]

    def tag_sub(m):
        inner, new = relabel(m.group(2))
        if new:
            changed[0] += 1
        return m.group(1) + inner + m.group(3)
    seg_new = TAG.sub(tag_sub, seg)
    html = html[:ks] + seg_new + html[ke:]

    # The same correction on every in-text citation.
    CITE = re.compile(r'(<a[^>]*href="#[^"]*\.bibx\d+"[^>]*>)(.*?)(</a>)', re.S)

    def cite_sub(m):
        inner, new = relabel(re.sub(r'\s+', ' ', m.group(2)))
        if new:
            changed[0] += 1
        return m.group(1) + inner + m.group(3)
    html = CITE.sub(cite_sub, html[:ks]) + html[ks:]
    return html, (dropped, changed[0])


def inline_svg(page_dir, body):
    """Replace <object data="x.svg"> with the SVG itself, inline.

    An <object> loads the file into a NESTED browsing context, and the browser
    paints that document's canvas -- white -- behind it. No outer CSS reaches
    inside, so a transparent SVG still lands on a white tile (Joe, 2026-08-25).
    Inlining also puts the SVG's <text> into the page DOM, where it can be
    selected, searched and read aloud with the surrounding prose instead of
    being a picture of words. The root's fixed pt width/height are dropped so
    the stylesheet sizes it; viewBox keeps the aspect ratio.
    """
    m = re.search(r'<object[^>]*data="([^"]+\.svg)"[^>]*>.*?</object>', body, re.S)
    if not m or not page_dir:
        return body
    f = page_dir / m.group(1).split("/")[-1]
    try:
        raw = f.read_text()
    except OSError:
        return body
    i = raw.find("<svg")
    if i < 0:
        return body
    svg = raw[i:]
    root = re.match(r"<svg[^>]*>", svg)
    if not root:
        return body
    stripped = re.sub(r'\s(?:width|height)="[^"]*"', "", root.group())
    return body[:m.start()] + stripped + svg[root.end():] + body[m.end():]


def svg_aspect(page_dir, ref):
    """'W / H' for an SVG asset, or None. An <object> is a replaced element
    with no intrinsic size here, so without an explicit aspect it falls back to
    the min-height meant for full-width diagrams -- which stretched a 250x256
    key to 390px tall."""
    f = page_dir / ref.split("/")[-1]
    if not f.exists() or f.suffix.lower() != ".svg":
        return None
    head = f.read_text(encoding="utf-8", errors="replace")[:2000]
    w = re.search(r'\bwidth="([\d.]+)', head)
    hh = re.search(r'\bheight="([\d.]+)', head)
    if not (w and hh):
        m = re.search(r'viewBox="[\d.\-]+ [\d.\-]+ ([\d.]+) ([\d.]+)', head)
        return f"{m.group(1)} / {m.group(2)}" if m else None
    return f"{w.group(1)} / {hh.group(1)}"


def size_figures(html: str, page_dir=None) -> tuple:
    """Decide which figures span the block and which take the text column.

    Measured on this paper, every raster figure asks for 390-476px and has an
    aspect between 0.89 and 2.05 -- none is a wide panorama. Giving them all
    the full block made Figure 2 taller than the screen. So rasters take the
    text column (still larger than LaTeXML intended, with the caption in the
    margin for free) and only the schematic diagram, which is genuinely wide
    and detailed, spans everything.
    """
    counts = [0, 0, 0]
    COLUMN_PX = 646    # the text column at a typical desktop width
    # A vector this small is a key or a legend, not a diagram, and belongs in
    # the margin beside the prose that reads it. Surveyed across every paper
    # using this tool (2026-08-24): the smallest real diagram is 465px and the
    # only asset below 400 is a 250px register key. The threshold is therefore
    # well clear of anything it could catch by accident. It is also the knob:
    # a generator that wants the margin emits a small SVG.
    MARGIN_PX = 400
    # Two kinds of figure live in the margin and they want different sizes.
    # A SIDEBAR is a small object -- a key, a glyph, a legend -- that is drawn
    # at the size it should be read at; enlarging it to fill the column makes
    # it look like a diagram that has been blown up, which it has (the 250px
    # register key). A MARGINFIGURE is a diagram that merely happens to fit the
    # margin, and wants the whole column. The natural width separates them,
    # which keeps every layout decision in this file a function of the asset,
    # as the rest of the module already is. Authors choose by rendering at the
    # size they want (Joe, 2026-08-25).
    SIDEBAR_PX = 300
    # Width left for artwork when the caption sits beside it, at ~1600px.
    SIDE_PX = 1150
    TOLERANCE = 1.12   # a <=12% reduction is not visible; a third of one is

    def repl(m):
        open_, cls, close, body, end = m.groups()
        # Size from the asset's REAL pixels. Sizing by LaTeXML's requested
        # width (390-476) shrank 1968px plots to 646 -- a third of natural
        # size, which is why their axis labels became unreadable. A figure
        # whose text cannot be read is either unimportant or being treated as
        # unimportant; these are neither.
        nat, nat_ref = None, None
        if page_dir:
            for ref in ASSET.findall(body):
                w = asset_width(page_dir, ref)
                if w and w > (nat or 0):
                    nat, nat_ref = w, ref
        vector = ("<svg" in body) or ("<object" in body)
        # Margin figures are decided first and exclusively: they take neither a
        # width class nor a caption-placement class, because both assume the
        # figure occupies the block grid and a margin figure floats out of it.
        if vector and nat is not None and nat <= MARGIN_PX:
            counts[2] += 1
            ar = svg_aspect(page_dir, nat_ref) if page_dir and nat_ref else None
            st = f"--nat: {int(nat)}px" + (f"; --nar: {ar}" if ar else "")
            wide_margin = nat > SIDEBAR_PX
            kind = " margin-fig marginfigure" if wide_margin else " margin-fig"
            # Only the wide kind is inlined: it is the one drawn transparent to
            # sit on the page ground. A sidebar carries its own ground and is
            # left exactly as it was.
            inner = inline_svg(page_dir, body) if wide_margin else body
            return f'{open_}{cls}{kind}{close[:-1]} style="{st}">{inner}{end}'
        wide = vector or ("<table" in body) or (nat is not None and nat > COLUMN_PX)
        # Caption beside is the default (Joe, 2026-08-08: "better wherever
        # possible"). It costs the artwork ~350px, so the biggest rasters are
        # reduced; only a figure that would have to shrink below HARD_FLOOR of
        # its own pixels keeps the full width and takes the caption underneath.
        HARD_FLOOR = 0.55
        side = vector or nat is None or (SIDE_PX / nat) >= HARD_FLOOR
        counts[0 if wide else 1] += 1
        cls = cls + (" wide-fig" if wide else " col-fig")
        cls = cls + (" cap-side" if side else " cap-below")
        # Never enlarge a raster past its own pixels; vectors may fill.
        style = "" if (vector or not nat) else f' style="--nat: {int(nat)}px"'
        return f"{open_}{cls}{close[:-1]}{style}>{body}{end}"

    return FIGURE.sub(repl, html), counts


def fix_notes(html: str) -> tuple:
    """Turn LaTeXML footnotes into Tufte sidenotes."""
    n = [0]

    def repl(m):
        fid, attrs, body = m.groups()
        inner = re.search(r'<span class="ltx_note_content">(.*)$', body, re.S)
        content = inner.group(1) if inner else body
        content = re.sub(r'<sup class="ltx_note_mark">\d+</sup>', "", content)
        content = re.sub(r'<span class="ltx_tag ltx_tag_note">\d+</span>', "", content)
        content = re.sub(r"\s+", " ", content).strip()
        n[0] += 1
        sid = f"sn-{n[0]}"
        return (f'<label for="{sid}" class="margin-toggle sidenote-number"></label>'
                f'<input type="checkbox" id="{sid}" class="margin-toggle"/>'
                f'<span class="sidenote" id="{fid}"{attrs}>{content}</span>')

    return NOTE.sub(repl, html), n[0]


MARGINPAR = re.compile(r'<span class="ltx_note ltx_marginpar[^"]*"[^>]*>')
SPAN_TAG = re.compile(r'<span\b|</span>')


def fix_marginpars(html: str) -> tuple:
    """Turn LaTeXML \\marginpar notes into Tufte margin notes.

    LaTeXML renders a marginpar like a footnote -- a dagger whose text only
    appears in a hover popup, so on a phone, which cannot hover, the note is
    unreachable (p4ng plop-2026's 37 tracker notes, 2026-09-13). Unlike a
    footnote its body nests paragraph spans, so the end is found by counting
    spans rather than by a fixed run of closing tags.
    """
    out, pos, n = [], 0, 0
    while (m := MARGINPAR.search(html, pos)):
        depth, i = 1, m.end()
        while depth:
            t = SPAN_TAG.search(html, i)
            if t is None:
                break
            depth += 1 if t.group() == "<span" else -1
            i = t.end()
        if depth:
            break  # unbalanced: leave the rest of the page as LaTeXML wrote it
        body = html[m.end():i - len("</span>")]
        inner = re.search(r'<span class="ltx_note_content">(.*)</span></span>$', body, re.S)
        content = inner.group(1) if inner else body
        content = re.sub(r'<sup class="ltx_note_mark">[^<]*</sup>', "", content)
        content = re.sub(r'<span class="ltx_note_type">[^<]*</span>', "", content)
        # \scriptsize in print; in the margin the note takes the sidenote size.
        content = re.sub(r"font-size:\s*[\d.]+%;?", "", content)
        content = re.sub(r"\s+", " ", content).strip()
        note = f'<span class="marginnote">{content}</span>'
        before = html[pos:m.start()]
        # Between blocks (e.g. after the abstract) the note's parent is the
        # full-width article, so the margin offset put it past the right edge
        # of the window. Hang it on the block it follows, as print does.
        tail = re.search(r"</div>\s*$", before)
        if tail:
            before = before[:tail.start()] + note + before[tail.start():]
            note = ""
        out += [before, note]
        pos, n = i, n + 1
    out.append(html[pos:])
    return "".join(out), n


HEAD_CSS = """
<link rel="stylesheet" href="tufte.css"/>
<style>
  /* Full width, with the margin as a working second column rather than a
     gutter. The text keeps a reading measure; notes, captions and figures use
     the space beside it. Figures span the whole block and put their caption in
     the margin underneath -- Tufte's arrangement, and the reason the margin is
     worth its width. */
  /* The block fills the window exactly: prose keeps a reading measure and the
     margin takes ALL the remaining width, so nothing is left as dead space at
     the right edge. Capping the margin instead (the previous attempt) left
     600px unused on a 1600px screen and still overflowed. */
  :root {
    --pad: 3vw;
    --gutter: 2.5rem;
    --measure: clamp(22rem, 38vw, 42rem);
    --margin-w: max(13rem, calc(100vw - (2 * var(--pad)) - var(--measure) - var(--gutter)));
    --block: calc(var(--measure) + var(--gutter) + var(--margin-w));
    /* The COLUMN may be wide so figures can use it; a NOTE may not -- an
       859px line at .76rem is unreadable. Notes take their own measure and
       sit at the column's left edge. */
    --note-w: min(var(--margin-w), 21rem);
  }
  *, *::before, *::after { box-sizing: border-box; }
  body { width: 100%; max-width: none; margin: 0; padding: 0;
         background: #fffff8; color: #111; }
  .ltx_page_main, .ltx_page_content { width: 100%; max-width: none; padding: 0; }
  .ltx_document { width: 100%; max-width: none;
                  padding: 3rem var(--pad) 6rem var(--pad); overflow-x: clip; }

  /* Prose keeps the measure; everything else may use the block. */
  .ltx_para, .ltx_p, .ltx_abstract, .ltx_title_section, .ltx_title_subsection,
  .ltx_title_subsubsection, .ltx_theorem, .ltx_proof, .ltx_listing,
  .ltx_itemize, .ltx_enumerate { width: var(--measure); max-width: 100%; }
  .ltx_title_document, .ltx_title_part { width: var(--block); max-width: 100%; }

  /* \\paragraph{} is a RUN-IN heading -- the PDF reads "Future work. Of the two
     extensions ...". LaTeXML models that by setting the .ltx_para and its <p>
     to display:inline, which makes their width:var(--measure) inert (inline
     boxes ignore width), so the text ran the full 1504px block. Constrain the
     section, which IS a block, and let the heading run in as it does in print. */
  section.ltx_paragraph { display: block; width: var(--measure); max-width: 100%;
    margin: 1.15rem 0 0; }
  /* Same treatment for starred sub/subsub-sections: LaTeXML nests body text in
     section.ltx_subsection, which nothing else here constrains, so a paper that
     uses \subsection*{} had its prose run the full block while a paper that did
     not looked fine (Joe, 2026-08-22 -- the fix had been made once, in one
     paper, and did not travel). */
  /* LIGHT. paper-site.css carries a prefers-color-scheme:dark block whose
     coverage is partial -- tables and figures keep light backgrounds while the
     page goes dark, so the result is unreadable rather than dark. Pin light for
     every tuftified paper until dark is either completed or dropped. This lived
     in one paper's local publish step and did not travel (Joe, 2026-08-22). */
  @media (prefers-color-scheme: dark) {
    :root { --ink: #111; --ink-muted: #555; --bg: #fffff8; --bg-sunk: #f4f4ec;
            --rule: #ccc; --link: #1a4b8c; }
    html, body, article, .ltx_page_main, .ltx_page_content, .ltx_document,
    figure, figure.ltx_table, figure.ltx_figure, table, table.ltx_tabular,
    table.ltx_tabular td, table.ltx_tabular th, .sidenote, .marginnote {
      background: #fffff8; color: #111; }
    .site-nav { background: #f4f4ec; border-bottom-color: #ddd; }
    a, .ltx_ref { color: #1a4b8c; }
  }
  /* Nav and cross-reference markers: explicit stacks so they cannot fall back
     to a decorative or math face. */
  .site-nav, .site-nav a, .site-nav .site-nav-home {
    font-family: "et-book", Palatino, "Palatino Linotype", Georgia, serif;
    font-variant: normal; font-weight: 400; }
  .site-nav a[aria-current="page"], .site-nav .site-nav-home { font-weight: 600; }
  a, .ltx_ref, .ltx_ref .ltx_text, .ltx_ref_tag {
    font-family: inherit; font-variant: normal; font-weight: inherit;
    font-style: inherit; }

  /* The measure belongs on the PROSE, not on the subsection box. Constraining
     the box traps wide figures: a .wide-fig inside a subsection still gets the
     full-bleed negative margin, which is calibrated against the section width,
     so it is yanked off the left edge and clipped by article{overflow-x:clip}.
     Measured (Joe, 2026-08-22, "Box 1 has now degenerated to one column"):
     figure margin-left -377.59px in a 486px subsection -> table at left -339,
     columns 1 and 2 entirely clipped, only column 3 visible. Constrain the
     children instead; figures then size against the full section.

     DO NOT add section.ltx_paragraph to this list without doing the rest of
     the work. Tried 2026-08-23 and reverted the same hour: a \\paragraph{}
     before a wide figure nests it in section.ltx_paragraph, which is still
     box-constrained, so the figure is dragged left until its centre sits on
     the page edge -- the same bug this rule fixes, one container deeper. But
     releasing that box with width:auto also releases everything inside it that
     has no replacement measure rule, and the paragraph TITLE (h5.ltx_title_
     paragraph) is one of them: WR-4's heading and its lede immediately ran the
     full page width. The child list below covers .ltx_para/.ltx_p/lists, not
     titles, and not lists nested inside .ltx_para. A correct fix must enumerate
     every child of a paragraph box, titles included, or move the measure onto
     the prose elements themselves rather than toggling ancestor boxes.
     Until then the author-side workaround is the cheap one: do not put a
     \\paragraph{} immediately before a wide figure (p4ng 80a2eb6 uses
     \\noindent\\textbf lead-ins instead). */
  section.ltx_subsection, section.ltx_subsubsection {
    display: block; width: auto; max-width: 100%; }
  section.ltx_subsection > .ltx_para, section.ltx_subsubsection > .ltx_para,
  section.ltx_subsection > .ltx_para > .ltx_p,
  section.ltx_subsubsection > .ltx_para > .ltx_p,
  section.ltx_subsection > .ltx_p, section.ltx_subsubsection > .ltx_p,
  section.ltx_subsection > .ltx_itemize, section.ltx_subsection > .ltx_enumerate,
  section.ltx_subsection > .ltx_description, section.ltx_subsection > .ltx_quote,
  section.ltx_subsubsection > .ltx_itemize, section.ltx_subsubsection > .ltx_enumerate,
  section.ltx_subsubsection > .ltx_description, section.ltx_subsubsection > .ltx_quote {
    width: var(--measure); max-width: 100%; }
  /* ltx-amsart.css:29-40 runs the FIRST paragraph of a subsection in beside its
     title, by setting the title, its sibling .ltx_para AND that para's .ltx_p
     all to display:inline. Tuftify already renders subsection titles as block
     headings, so the run-in is half-undone -- and an inline box ignores width,
     which meant the first paragraph of every subsection silently escaped the
     measure the moment the subsection box stopped constraining it (measured:
     width computed 486.4px, rect 1187px). Complete the un-run-in. */
  .ltx_subsection .ltx_title + .ltx_para,
  .ltx_subsection .ltx_title + .ltx_para > .ltx_p,
  .ltx_subsubsection .ltx_title + .ltx_para,
  .ltx_subsubsection .ltx_title + .ltx_para > .ltx_p {
    display: block; width: var(--measure); max-width: 100%; }
  /* LaTeXML emits h4 at paragraph level in some classes and h5 in others;
     keying the run-in heading to h5 alone lets it fall back to the SECTION's
     font-size, which is smaller than the paragraph text it introduces. */
  section.ltx_paragraph > h4.ltx_title_paragraph,
  section.ltx_paragraph > h5.ltx_title_paragraph { display: inline; float: none;
    font-size: 1.334em; font-weight: 700; font-style: normal; margin: 0;
    padding: 0; }

  /* A sidenote is a right float, so a figure or table starting before the
     float has ended renders UNDER it. Measured 2026-08-22: the footnote from
     the paragraph above Box 2 overlapped the table's third column, making both
     unreadable. Floats must be cleared by anything that lays out its own
     columns. */
  .ltx_figure, .ltx_table, figure.ltx_float { clear: right; }

  /* Notes hang in the margin, beside the line that cites them. */
  .sidenote, .marginnote {
    float: right; clear: right; position: relative;
    width: var(--note-w);
    margin-right: calc(-1 * (var(--note-w) + var(--gutter)));
    margin-top: .3rem; margin-bottom: 1.1rem;
    font-size: 1.05rem; line-height: 1.5; text-align: left; text-indent: 0;
    font-style: normal; color: #55524a; }
  /* A coloured marginpar keeps its author's colour. The page is pinned light
     above, but LaTeXML.css lightens --ltx-fg-color under a dark scheme, which
     would wash it out against the light margin. */
  .marginnote [style*="--ltx-fg-color:"] { color: var(--ltx-fg-color); }
  /* LaTeXML CENTRES figure contents and display equations. Against a strict
     left-aligned measure that gives every figure a different left edge --
     which reads, correctly, as things scattered at random. Everything hangs
     from the same line as the text. */
  .ltx_figure, .ltx_table, .ltx_figure > *, .ltx_table > *,
  .ltx_equation, .ltx_equationgroup, .ltx_eqn_table, .ltx_eqn_row, .ltx_eqn_cell {
    text-align: left; }
  .ltx_figure img, .ltx_figure svg, .ltx_table img, .ltx_graphics {
    display: block; margin-left: 0; margin-right: auto; }
  /* ltx_centering sets margin:auto, which re-centres the caption inside its
     grid cell -- so each one started at a different x depending on its own
     width. Captions hang from the margin column's edge like everything else. */
  .ltx_centering, figcaption.ltx_centering, .ltx_figure > figcaption,
  .ltx_table > figcaption { margin-left: 0 !important; margin-right: auto !important; }
  /* Equations sit on the measure, indented once, not floated to centre. */
  .ltx_equation, .ltx_equationgroup, .ltx_eqn_table {
    width: var(--measure); max-width: 100%; margin-left: 0; }
  .ltx_eqn_table { table-layout: auto; }
  .ltx_eqn_cell.ltx_eqn_center_padleft, .ltx_eqn_cell.ltx_eqn_center_padright,
  .ltx_eqn_eqno { width: 0 !important; padding: 0 !important; }
  .ltx_eqn_cell.ltx_align_center { text-align: left !important; padding-left: 1.5rem; }

  /* A note must not escape the window. */
  .sidenote, .marginnote { max-width: calc(100vw - var(--measure) - var(--gutter) - (2 * var(--pad))); }

  /* LaTeXML justifies captions; at margin width that opens rivers. */
  .ltx_figure figcaption, .ltx_table figcaption,
  .ltx_caption, .ltx_caption * { text-align: left !important; }
  .ltx_figure, .ltx_table { background: none; }
  .mn-kind { font-variant: small-caps; letter-spacing: .05em;
             color: #8a8578; margin-right: .35em; }

  /* Figures take the full block; the caption sits in the margin beneath. */
  figure.ltx_figure, .ltx_figure, figure.ltx_table, .ltx_table {
    display: grid; width: var(--block); max-width: 100%;
    grid-template-columns: calc(var(--measure) + var(--gutter)) var(--margin-w);
    column-gap: 0; row-gap: .4rem; margin: 2.2rem 0; }
  /* Default: the image takes the text column, the caption the margin. */
  .ltx_figure > *:not(figcaption), .ltx_table > *:not(figcaption) { grid-column: 1; }
  /* Only genuinely wide, detailed artwork spans text + margin. */
  .wide-fig > *:not(figcaption) { grid-column: 1 / -1; }
  /* Caption beside: the artwork takes the space left over, the caption sits
     at its top right. Used when the figure can spare the width. */
  /* .ltx_figure is matched as `figure.ltx_figure` above, which outranks a
     bare .cap-side and silently reinstated the narrow first column. */
  /* The artwork column takes the artwork's own width, not the whole span, so
     the caption sits against the figure's right edge. A fixed far-right
     caption column stranded Figure 3 (740px wide) 412px from its caption. */
  figure.cap-side, .ltx_figure.cap-side, .ltx_table.cap-side {
    grid-template-columns: minmax(0, max-content) var(--note-w);
    justify-content: start; column-gap: var(--gutter); }
  /* Tables carry their caption FIRST in the DOM (captions conventionally sit
     above a table), so auto-placement put the caption in row 1 and the table
     in row 2 -- the caption floated above rather than beside. Pin both to row
     1 so DOM order stops deciding the layout. Any further child flows below. */
  figure.cap-side > *:not(figcaption), .ltx_figure.cap-side > *:not(figcaption),
  .ltx_table.cap-side > *:not(figcaption) {
    grid-column: 1; grid-row: 1; }
  figure.cap-side > figcaption, .ltx_figure.cap-side > figcaption,
  .ltx_table.cap-side > figcaption {
    grid-column: 2; grid-row: 1; align-self: start; }
  /* Caption below: the artwork needs every pixel, so the caption goes under
     it, still in the margin column. */
  figure.cap-below > *:not(figcaption), .ltx_figure.cap-below > *:not(figcaption) {
    grid-column: 1 / -1; }
  figure.cap-below > figcaption, .ltx_figure.cap-below > figcaption {
    grid-column: 2; align-self: start; }
  .ltx_figure > figcaption, .ltx_table > figcaption {
    grid-column: 2; align-self: start; justify-self: start;
    width: var(--note-w); max-width: 100%;
    font-size: 1.1rem; line-height: 1.5; color: #4a4740;
    text-align: left; text-indent: 0; hyphens: none; }
  .ltx_figure img, .ltx_figure svg, .ltx_table img, .ltx_figure object {
    width: 100%; height: auto; max-height: 88vh; object-fit: contain; }
  /* --nat is the asset's true pixel width: fill the space available, but do
     not blow a raster up past the detail it actually contains. */
  .ltx_figure[style*="--nat"] > *:not(figcaption) { max-width: min(100%, var(--nat)); }
  .ltx_figure[style*="--nat"] img { max-width: var(--nat); }
  .ltx_figure object, .ltx_figure embed { display: block; width: 100%;
    min-height: 26rem; border: 0; }

  /* A margin figure. Floated like a marginnote rather than placed in the
     block grid, so body text runs alongside it instead of leaving column 1
     empty. Capped at the asset's own pixels: a 250px key stretched to the
     full margin looked like a diagram that had been enlarged, which is what
     it was. */
  figure.ltx_figure.margin-fig, .ltx_figure.margin-fig {
    display: block; float: right; clear: right;
    width: var(--note-w); max-width: var(--note-w);
    /* NOT the marginnote's negative margin. A note's containing block is the
       paragraph, at reading measure; a figure's is the section, already
       --block wide, so the same negative margin threw it 100px past the
       viewport (measured x=1655 in a 1600px window). Offsetting from the
       right by the margin column's unused width lands its left edge on the
       margin, in line with every caption and note. */
    margin: .3rem calc(var(--margin-w) - var(--note-w)) 1.2rem 0;
    grid-template-columns: none; }
  /* Fill the margin column rather than sitting at the asset's own pixels. A
     vector may be enlarged, and capping at --nat left a 250px key at 79% of
     its 315px caption, which read as a thumbnail beside a block of text
     (Joe, 2026-08-24). 95% keeps a hair of right-hand air. */
  .ltx_figure.margin-fig > *:not(figcaption) {
    grid-column: auto; width: 95%; max-width: 95%;
    margin-left: 0; margin-right: auto; }
  /* A marginfigure takes the whole margin column rather than the note width.
     --note-w is capped at 21rem because prose wants a reading measure; a
     diagram does not, and on a wide window that left a 364px figure beside an
     850px margin, reading as a thumbnail. A sidebar (.margin-fig without this
     class) is unaffected. */
  .ltx_figure.margin-fig.marginfigure {
    /* Stack explicitly rather than leaning on auto-margin arithmetic: the
       figure inherits grid rules from three earlier blocks, several of them
       !important, and `margin-right: auto` on the caption was not surviving
       the trip. A column flex box with a start alignment states the intent --
       artwork, then caption, both against the left edge (Joe, 2026-08-25). */
    display: flex !important; flex-direction: column; align-items: flex-start;
    width: var(--margin-w); max-width: var(--margin-w);
    margin-right: 0; }
  /* The caption keeps the sidebar's measure and sits at the left edge, below
     the artwork -- a caption run across a full-margin figure is far past a
     readable line length (Joe, 2026-08-25). */
  /* !important, reluctantly: the cap-side block below sets
     `width: 100% !important` on every figure caption, which outranks this on
     the cascade no matter how specific it is. Specificity then decides
     between the two importants, and this rule is the more specific. */
  .ltx_figure.margin-fig.marginfigure > figcaption {
    width: var(--note-w) !important; max-width: var(--note-w) !important;
    margin-left: 0 !important; margin-right: auto !important;
    text-align: left !important; }
  .ltx_figure.margin-fig > figcaption {
    grid-column: auto; width: 100%; max-width: 100%;
    margin: .5rem 0 0; }
  .ltx_figure.margin-fig object, .ltx_figure.margin-fig embed {
    min-height: 0; height: auto; aspect-ratio: var(--nar, auto); }
  .ltx_figure.margin-fig svg, .ltx_figure.margin-fig img {
    width: 100%; height: auto; max-height: none; }

  /* References. The entries were collapsing to ~94px blocks inside a 608px
     section: the bibblock spans were being sized as grid items. Take the list
     out of the grid, give it the full span, and set it in columns so the
     line length stays readable rather than running the width of the page. */
  section.ltx_bibliography { display: block; grid-column: 1 / -1;
    width: var(--block); max-width: 100%; }
  section.ltx_bibliography > h2, section.ltx_bibliography > h1 { margin-bottom: 1rem; }
  /* LaTeXML's own sheet pins ul.ltx_biblist to 47rem, so the column rule was
     subdividing an already-halved box; state the width rather than inherit it. */
  .ltx_biblist, .ltx_bibliography ul { display: block; list-style: none;
    width: 100%; max-width: none; margin: 0; padding: 0;
    columns: 24rem 2; column-gap: var(--gutter); }
  li.ltx_bibitem { display: block; width: auto; max-width: none;
    break-inside: avoid; margin: 0 0 0.75rem; padding-left: 1.6em;
    text-indent: -1.6em; font-size: 0.92em; line-height: 1.4; }
  li.ltx_bibitem > .ltx_bibblock { display: inline; width: auto; }
  li.ltx_bibitem > .ltx_tag_bibitem { font-weight: 600; }
  /* Spacetime diagrams are cell grids: nearest-neighbour keeps them crisp when
     they are shown larger than their natural size. */
  .ltx_figure img { image-rendering: pixelated; }

  /* A small figure belongs wholly in the margin. */
  .ltx_figure.mn-fig {
    display: block; float: right; clear: right;
    width: var(--note-w); margin-right: calc(-1 * (var(--note-w) + var(--gutter)));
    margin-top: .3rem; }
  .ltx_figure.mn-fig > figcaption { grid-column: auto; }

  .ltx_title_section, .ltx_title_subsection {
    font-style: italic; font-weight: 400; line-height: 1.2; }
  .ltx_title_part { font-style: normal; font-weight: 400; letter-spacing: .02em; }
  .ltx_ref_short, em.ltx_emph > .ltx_ref_short {
    font-style: normal; white-space: nowrap; text-decoration: none;
    font-variant-numeric: tabular-nums; border-bottom: 1px solid rgba(0,0,0,.22); }
  math { font-style: normal; }

  @media (max-width: 1080px) {
    :root { --measure: min(34rem, 92vw); }
    .ltx_document { padding-left: 4vw; padding-right: 4vw; }
    .sidenote, .marginnote, .ltx_figure.mn-fig {
      float: none; width: 100%; margin: .9rem 0; display: block;
      background: #f4f2e9; padding: .55rem .75rem;
      /* The base sheet caps notes at calc(100vw - measure - gutter - pads),
         the space BESIDE the text column. On a phone that is negative,
         clamps to 0, and every footnote rendered as a one-word-per-line
         ribbon (measured: 23px wide, 5379px tall at 390px). */
      max-width: 100%; }
    /* The previous rule here was a bare .ltx_figure (0,1,0), which LOSES to
       the base figure.ltx_figure display:grid (0,1,1) -- so on a phone the
       two-column grid survived, the caption column sat past the right edge,
       and .ltx_document{overflow-x:clip} deleted every caption (Joe,
       2026-08-31, reading on a phone). Match every figure variant at equal
       or better weight; margin-fig floats and marginfigure's !important flex
       need the !important to lose too. */
    figure.ltx_figure, .ltx_figure, figure.ltx_table, .ltx_table,
    figure.cap-side, .ltx_figure.cap-side, .ltx_table.cap-side,
    figure.ltx_figure.margin-fig, .ltx_figure.margin-fig,
    .ltx_figure.margin-fig.marginfigure {
      display: block !important; float: none; clear: both;
      width: 100% !important; max-width: 100% !important;
      margin: 1.6rem 0; grid-template-columns: none; }
    .ltx_figure > *:not(figcaption), .ltx_table > *:not(figcaption),
    .ltx_figure.margin-fig > *:not(figcaption) {
      width: 100%; max-width: 100%; }
    .ltx_figure > figcaption, .ltx_table > figcaption,
    .ltx_figure.margin-fig.marginfigure > figcaption {
      width: 100% !important; max-width: 100% !important;
      margin: .5rem 0 0; }
    /* 26rem of forced height letterboxes a shallow SVG on a narrow screen;
       let the object's own width/height attributes set the aspect. */
    .ltx_figure object, .ltx_figure embed { min-height: 0; }
    /* Long URLs and the DOI/pubnotes line are unbreakable strings; measured
       at 390px they ran to 465-531px and set the page's scroll width. */
    a.ltx_url, .ltx_bibblock, .ltx_pubnotes_content, .ltx_pubnotes {
      overflow-wrap: anywhere; max-width: 100%; }
    /* A paragraph holding a FIXED --measure inside an indented list overflows
       by exactly the indentation (measured: outline items at x=122 running to
       right=419 in a 390px viewport, every nesting level of app-argument-
       outline). width:auto follows the container through the indents; the
       measure survives as a line-length cap. !important because several base
       rules set the width at (0,2,1) via section-scoped child selectors. */
    .ltx_para, .ltx_p, .ltx_abstract, .ltx_theorem, .ltx_proof, .ltx_listing,
    .ltx_itemize, .ltx_enumerate, .ltx_quote, .ltx_description,
    section.ltx_paragraph {
      width: auto !important; max-width: var(--measure) !important; }
    /* LaTeXML.css puts white-space:nowrap on li.ltx_item (its :272, to hold
       the item tag and the inline-block para on one line), so outline items
       ran past the screen edge and were clipped, not wrapped -- 40px of
       every app-argument-outline line lost at 390px. Released, the
       inline-block para shrinks to the li's width (probed live: right edge
       419 -> 374). */
    .ltx_para, .ltx_p { white-space: normal !important; }
    li.ltx_item, .ltx_item { white-space: normal !important; }
    /* Any content table wider than the screen scrolls inside its own box
       rather than being clipped by the document. ALL tables, not just
       .ltx_tabular: the WR/snatch tables carry no class and their last
       columns were unreachable. When a table fits, no scrollbar appears,
       so this is safe on tablets too. display:block on a <table> leaves
       the row groups forming an anonymous table inside the scroll box. */
    .ltx_document table:not(.ltx_eqn_table), table.ltx_tabular, .ltx_tabular {
      display: block; width: 100%; max-width: 100%;
      overflow-x: auto; -webkit-overflow-scrolling: touch; }
    .ltx_equation, .ltx_equationgroup, .ltx_eqn_table {
      display: block; width: 100%; max-width: 100%; overflow-x: auto; }
  }

  /* Phone. Anything wider than the screen must scroll inside its own box,
     because the document clips: a wide tabular or equation was simply cut
     off at the right edge with no way to reach the rest (Joe, 2026-08-31).
     display:block on a <table> is the standard scrollable-table move; the
     row groups form an anonymous table inside the scrolling block. */
  @media (max-width: 600px) {
    :root { --gutter: 1rem; }
    .ltx_listing, .ltx_verbatim, pre { max-width: 100%; overflow-x: auto; }
    /* Two-up biblist columns at 24rem each cannot fit; one readable column. */
    .ltx_biblist, .ltx_bibliography ul { columns: auto 1; }
    /* A list at var(--measure) plus its own indent ran ~5px past the screen. */
    .ltx_itemize, .ltx_enumerate { width: auto; }
  }
</style>
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("page")
    ap.add_argument("-o", "--out", required=True)
    args = ap.parse_args()

    src = Path(args.page)
    html = src.read_text(encoding="utf-8")

    html, refs = fix_refs(html)
    html, notes = fix_notes(html)
    html, margins = fix_marginpars(html)
    html, figs = size_figures(html, Path(args.page).parent)
    html, bib = fix_bibliography(html)

    # Tufte's stylesheet last, so it wins over LaTeXML's defaults.
    if "</head>" in html:
        html = html.replace("</head>", HEAD_CSS + "</head>", 1)
    else:
        html = HEAD_CSS + html

    out = Path(args.out)
    out.write_text(html, encoding="utf-8")
    css = out.parent / "tufte.css"
    if TUFTE_SRC.exists() and not css.exists():
        shutil.copyfile(TUFTE_SRC, css)

    print(f"  {out.name}: {refs[0]} cross-references shortened "
          f"({refs[1]} titles moved to the margin), {notes} footnotes -> sidenotes, {margins} margin notes, "
          f"{figs[0]} full-width / {figs[1]} column / {figs[2]} margin figures, "
          f"{bib[0]} duplicate bibliography dropped, {bib[1]} citation years fixed")


if __name__ == "__main__":
    main()
