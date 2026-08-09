# The de-AI-ifying loop: Joe's edits → patterns → whole-paper sweeps

Instrument: `writing-patterns.edn` (seeded 2026-08-09 with the eight house
patterns established during the audit passes, each carrying a real
before/after from this paper's own revision history).

## The loop

1. **Joe edits freely** — in the tex/edn sources, proofing against the
   tufte-HTML surface or the PDF. No ceremony required: git is the capture
   layer. Whatever granularity of commits (or none — a dirty working tree
   diffs fine).

2. **Marking an exemplar (optional, one of):**
   - a one-line note in the commit message ("swapped nominalization for the
     verb", "cut hedging doublet");
   - an inline `%PAT: <one line>` comment next to the edit (harvested and
     stripped by the extraction pass);
   - nothing — the extractor infers intent from the diff and ASKS when the
     intent is ambiguous rather than guessing.

3. **Extraction pass (Claude):** read `git diff` hunk by hunk. For each
   deliberate stylistic change, write a `:candidate` entry in
   `writing-patterns.edn`: trigger, before/after (verbatim from the diff),
   the rule stated generally, and the inferred why. Joe promotes
   `:candidate` → `:house` (or kills it) — the ruling IS the calibration,
   exactly as in the audit passes.

4. **Sweep pass (Claude):** for each promoted pattern, sweep the whole paper
   (and supplements) for instances; emit standoff proposals in the
   `partN-edits.md` format (verbatim OLD that greps exactly once, NEW,
   one-line rationale, pattern id). NOTHING is applied without a ruling;
   Joe can rule per-item or per-pattern ("apply all :state-the-positive").

5. **Durability:** patterns that survive become session memory (the
   paper-no-lab-notebook-voice memory is pattern 1 in embryo), so future
   drafting starts from them instead of rediscovering them.

## Why this direction works

The audit passes ran top-down: stated standards, hunted violations. This
loop runs bottom-up: Joe's actual hand-edits are the standard, observed
rather than declared. One good exemplar edit generalises to N sites at the
cost of one diff read — the leverage the 69 pages need. And the asymmetry
is the right one: taste is expensive and stays human; application is cheap
and doesn't.

## The interactive channel (sketched 2026-08-09, build later)

Git diffs capture the *edit*; a highlight-and-speak channel captures the
*intent at the moment of noticing* — richer, and faster to iterate than
commit-diff-extract. Prior art exists and worked:
`futon3c/holes/missions/M-smart-emacs-cursor.md` (the spoken mission loop,
2026-06-11: voice → Whisper → text → agent → cursor choreography), voice
input live at `futon0/contrib/voice-typing.el`, routing in
`futon3c/scripts/voice-route/`. That ran against the legacy CLI; the
pipeline is surface-agnostic enough to port.

Port to the tufte-HTML surface (127.0.0.1:8129/draft9-tufte.html):
- a small JS layer: on text selection + hotkey, capture
  `window.getSelection()` plus the nearest anchored element id, and POST
  `{selection, anchor-id, note}` to a local receiver that appends to
  `paper/edit-notes.jsonl`;
- the note is typed or spoken (reuse the Whisper pipeline; the transcription
  lands in the same POST);
- selection → tex-source mapping needs no new machinery: the
  verbatim-greps-once discipline applies to prose selections exactly as to
  OLD blocks, and the anchor id narrows the search when a phrase repeats.
The extraction pass then consumes `edit-notes.jsonl` alongside (or instead
of) git diffs; everything downstream — :candidate entries, promotion by
ruling, per-pattern sweeps — is unchanged. Emacs-as-surface is the same
loop with region-highlight in place of browser selection, per
M-smart-emacs-cursor's revival notes (it stalled when dispatched for
autonomous building; it is inherently interactive — build it WITH the
operator at the keyboard).

## Practical notes

- Sweeps should run per-pattern, not per-file, so each proposal batch is
  homogeneous and fast to rule on.
- The verbatim-OLD-greps-once discipline from the codex edit rounds applies
  unchanged; it is what makes proposals safely appliable after Joe has
  edited around them.
- If an edit contradicts an existing :house pattern, that is a finding, not
  an error — surface it; the pattern may need :retired or a narrower
  trigger.
- Renders: after any applied batch, rebuild and re-render the touched pages
  (the pinned .venv-figures matplotlib for figures).
