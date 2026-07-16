# ALife paper draft — propagator / edge-of-chaos

Refactor of `../TN-propagator-paper.md` into an APA-7 (`apa7`) LaTeX draft.
**Status: skeleton with real prose in the settled sections.** Not submittable —
see *Open before submission* below.

## Build

```sh
cd futon5/holes/tech-notes/paper
latexmk -pdf main.tex          # biber wired via latexmkrc
```

Verified: builds clean at TeX Live 2025, **0 errors, 0 undefined
citations/references, 23pp**.

- `main.tex` — the draft
- `refs.bib` — starting bibliography (the TN's named ancestors + method cites)
- `figures/` — copies of the seven TN-listed figures + the original `eoc.png`
- `latexmkrc` — biber wiring

### Knobs

| what | how |
|---|---|
| Manuscript ↔ journal look | `\documentclass[man,...]` → `jou` (or `doc` for a plain read) |
| Hide draft apparatus | `\draftnotestrue` → `\draftnotesfalse` in the preamble |

`\owed{...}` (red) = a reviewer will ask for this and we don't have it.
`\dnote{...}` (blue) = a note to ourselves. Both vanish with `\draftnotesfalse`,
so a clean read is one word away.

## Structure

Follows the TN's core decision — **one paper, Paper B's thesis, Paper A as the
first half**:

1. Introduction — thesis + contributions
2. Background — **STUB** (TN weakness 4)
3. The Propagator — reconstruction, corrections, family, conjugacy, census, fixed points
4. The Fail Bank — Cμ, aliveness, run-level means, **the pincer**
5. The Fail Bank Is a Theorem, Not a Gap — the thesis
6. Geometry: No Natural Joints — Euclidean / Fisher–Rao / Wasserstein + controls
7. Methods That Survived — mean-field bound, Ollivier–Ricci
8. Reproducibility
9. Discussion — **NOT WRITTEN** (sketch in an `\owed`)

## What was checked against artefacts (2026-07-16)

The TN is an outline, so every number it carries was re-grounded before it went
into the draft rather than copied on faith. Checked:

| claim | artefact | verdict |
|---|---|---|
| 20,256 orbits, Burnside, complement rejected | `propagator_orbit_proof.md` | ✓ |
| Fingerprint `ac2ff1681eae5b85`, 4 anchors pass, **3 seeds/σ** | `propagator_index_REPORT.md` | ✓ |
| Conjugacy: τ=[0 4 1 5 2 6 3 7], 2048 transitions | `propagator_mechanism.md` | ✓ |
| Predeclared semantic property scores 4/10 | `propagator_mechanism.md` | ✓ |
| Write histogram: 87,632 writes, bit 0 = 24.946%, bit 7 = 0.000% | `baldwin-repro/README.md` | ✓ |
| Baldwin 0/15 vs blending 15/15 → {42,170} | `baldwin-repro/README.md` | ✓ |
| 112/202 σ live (55%) | `M-propagators.md:496` | ✓ |
| Euclidean silhouette .441 → .267 | `fisher-rao.json` | ✓ |
| FR flat .07–.08; controls .004 / .247 / .696 | `fisher-rao.json` | ✓ |
| W₁ peak .176 (k=3); blob .088; real .690 | `wasserstein.json` | ✓ |
| Curvature mean +.085, 30.3% neg, 80% single-transposition, 83% artifact-controlled | `curvature.json` | ✓ |
| Fixed points: 0.0% ≤1, 11.5% ≤4, 54.1% ≤32, 88.0% ≤64 | `fixedpoints.npz` (recomputed) | ✓ (min support = 4) |
| Pooling: occupied bins 43.8 → 118.9 | `*.f64` (recomputed) | ✓ (43.84 → 118.93) |

### Two discrepancies found — TN vs artefact

**These are the reason to read this section.** Neither changes a conclusion;
both must be reconciled before submission.

1. **Mean pairwise Fisher–Rao under pooling.** TN says `2.600 → 2.301`.
   **No artefact reproduces this.** Recomputed from
   `data/propagator-metric/{terminal-dists,pooled-dists-K20}.f64`:

   | method | terminal → pooled |
   |---|---|
   | 600-row random sample | 2.775 → 2.538 |
   | evenly-spaced 1000-row (per `propagator-clusters/README.md`) | 2.787 → 2.560 |
   | **exact, all 205M pairs** | **2.781 → 2.553** |

   Note the stored rows are **counts**, not probabilities (terminal sums to 180
   = 60 cells × 3 seeds; pooled to 3600 = ×20 generations) — they must be
   renormalised before `2·arccos(Σ√(pq))`. Failing to renormalise is a plausible
   origin for the TN pair, but I could not reproduce `2.600/2.301` under any
   normalisation I tried. The draft uses the **exact full-population** figures,
   which are stronger than the TN's sampled ones anyway. Direction and
   conclusion are unaffected: saturation drops, silhouette stays flat.

2. **Mean-field bound tightness.** TN says "0.957–0.965 of exact".
   `wasserstein.json` records `tightness_mean = 0.9647`, `tightness_min =
   0.8297` over 24 pairs. So 0.957 is **not** the range floor — the true minimum
   is 0.830. The draft reports mean 0.965 / min 0.830. `bound.valid = true`, so
   "never violated" holds.

## Venue and length

**Target: *Artificial Life* (MIT Press), category ARTICLE** — "a manuscript
reporting original research, typically of **6,000 to 12,000 words**". The other
categories are Letters (≤2,000), Fast Track (~2,000), Reports (≤2,000), and
Reviews (≥12,000), so Article is the only one this work fits.

- **APA-7 is the journal's own requirement** ([submission
  guidelines](https://direct.mit.edu/artl/pages/submission-guidelines)) — `apa7`
  is on-style, not a stand-in.
- Submission is a **PDF** via [Manuscript
  Central](https://mc.manuscriptcentral.com/artificiallife).
- The journal has a **Research Data Policy** (make data public where possible).
  Section 8 (Reproducibility) already satisfies this in substance; it may need
  restating as a formal data-availability statement.

### Word count (measured, `\draftnotesfalse`, references excluded)

| part | words | target |
|---|---:|---|
| abstract | 252 | APA-7 convention ≤250; journal states no limit — trimmed from 310, close enough |
| **body (Introduction → References)** | **3,541** | **6,000–12,000 → ~2,460 short** |
| references | 394 | — |

Recount after any edit with:

```sh
sed -i 's/^\\draftnotestrue/\\draftnotesfalse/' main.tex && latexmk -pdf main.tex
pdftotext main.pdf - | sed -n '/Introduction/,/^References/p' | wc -w
sed -i 's/^\\draftnotesfalse/\\draftnotestrue/' main.tex
```

**The body is ~2,460 words under the 6,000 floor** — the opposite of the problem
you'd expect from a 23pp double-spaced PDF. The two unwritten sections are
almost exactly the gap:

- Background (currently a stub) — a real related-work pass: ~1,200–1,800 words
- Discussion (not written) — the four-part sketch in its `\owed`: ~800–1,200 words

So the length constraint and the content gaps are the *same task*. Nothing needs
padding, and nothing needs cutting — with one exception: dropping the tokamak
(recommended, item 6) costs ~150 words and is still the right call.

### On the class file

The journal also ships an [Overleaf
template](https://www.overleaf.com/latex/templates/artificial-life-journal-submission-template/zhhhjdgvhryt)
using a custom `artificial-life.cls` (XeLaTeX/LuaLaTeX only). That class is
**not in TeX Live** — it's bundled with the template. We are not blocked by this:
the template sets `\usepackage[style=apa,natbib=true]{biblatex}` and we set
`\usepackage[style=apa,backend=biber]{biblatex}`, i.e. **the same citation
style**, so `refs.bib` and every `\parencite` port across unchanged. If we move
to that class, only the preamble and the title block change.

## Open before submission

Tracked inline as `\owed{}` / `\dnote{}` — build the PDF to see them in context.

1. **Background section is a stub.** The literature search the TN flags as owed
   has still not been run. `refs.bib` has the entries; nobody has read them *for
   this paper*. This is the single biggest gap.
2. **Discussion is not written.** Sketch is in an `\owed` at the section head.
3. **3 seeds per σ** — the reviewer's first target. Fix is costed in the TN (JAX
   MetaCA port *with blending*).
4. **No blob control on the Euclidean 18-feature sweep** — the other two metrics
   have theirs.
5. **"The eye outperforms every instrument"** — the line the paper will be
   remembered by, currently resting on a single blind pick (2/15). Either soften
   to what that supports or run a real human-baseline protocol.
6. **Tokamak: in or out?** Draft recommends **cut** (TN itself says "if it
   appears at all"; the numbers are explicitly not banked). If cut,
   `figures/tokamak_run_figure.png` goes unused.
7. **`refs.bib` details unverified** — entries assembled from the TN's named
   ancestors; volume/page/DOI not checked against primary sources.
8. **Authorship** — Maclean is co-author of the 2015 paper being corrected.
   Joe's call.
9. **~2,460 words short of the Article floor.** See *Venue and length* below.
