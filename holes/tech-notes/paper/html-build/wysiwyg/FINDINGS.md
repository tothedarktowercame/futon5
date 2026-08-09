# WYSIWYG findings

Defects in the conversion pipeline itself (`latexml_oxide` → `draft*.html`),
as distinct from styling choices in `tuftify.py`. These affect the **editable
page** too, not just the reading view.

## F1 — biblatex: both `\datalist` blocks are rendered as bibliographies

**Found** 2026-08-08, reviewing `draft9-tufte.html`.
**Symptom (as reported):** the References section appears twice.
**Second, worse symptom:** *every* in-text citation carries a spurious
disambiguation suffix — `Stanley 1999a`, `Aracena 2008a`, `Riordan 1968a` —
and the genuinely ambiguous ones are shifted out of step: `Anthropic 2026b/2026c`
where the PDF has `2026a/2026b`.

**Root cause.** `apa7.cls` loads biblatex. Biber writes `draft9.bbl` as ONE
`\refsection` containing TWO `\datalist` blocks:

| datalist | purpose |
|---|---|
| `apa/apasortcite//global/global/global` | citation ordering |
| `apa/global//global/global/global`      | the list that is printed |

This is correct, normal biblatex output. Real LaTeX prints only the second —
`draft9.pdf` has one References section and bare years (`Aracena, J. (2008)`).
The converter walks *both* datalists and emits a `<section
class="ltx_bibliography">` for each (`id="bib"` and `id="biba"`, the `a`
suffix being LaTeXML's id-collision fallback). Because each entry is then
present twice, biblatex's `extradate` logic sees a collision on every
name+year and suffixes years that should be bare.

Confirming evidence:
- `grep -c 'datalist\[entry\]' draft9.bbl` → 2; `refsection{` → 1.
- `refs.bib` has exactly one genuine name+year collision (Anthropic 2026 ×2);
  every other suffix in the HTML is spurious.
- All 22 in-text citation links resolve into the **second** section (`biba.*`),
  so the first is an orphan nothing points at.

**Correct behaviour.** Render only the datalist whose sorting template matches
the printed bibliography (here `apa/global//global/global/global`); treat
`apasortcite` as citation-ordering metadata, not content. The fix belongs in
the biblatex binding (`latexml_contrib/src/biblatex_sty.rs`, which the build
log names at line 719).

**Interim mitigation** (in `tuftify.py:fix_bibliography`, reading view only):
keep whichever section the citations actually link to, drop the orphan, then
recompute every suffix from the surviving entries — a name+year used once
loses its suffix, one used N times gets `a, b, c` in document order. Verified
against `draft9.pdf`: 17 entries, `Anthropic (2026a)`/`(2026b)`, all others
bare, 0 dangling links. **The editable page still shows both sections** — only
the converter fix cures that.

## F2 — `ul.ltx_biblist` is pinned to 47rem by LaTeXML's stylesheet

Reference entries collapsed to ~94px-wide blocks inside a 608px section: the
list box was fixed at 47rem regardless of its container, so any column rule
subdivided an already-halved box. `tuftify.py` now states the width instead of
inheriting it. Styling-level, but worth knowing before debugging widths here —
a computed width that matches no rule of yours is likely theirs.
