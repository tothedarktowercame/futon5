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

Joe's spec (2026-08-09, verbatim sense). **Whisper is another REPL
frontend**, peer to IRC and Emacs — it delivers turns like any surface,
with the spoken keyword **"rocket"** as the RET-equivalent: whisper runs
continuously in the background (as piloted in the legacy CLI), and the
transcript feeds through only when a rocket completes it. The browser JS
supplies the other half: it tracks the highlighted region in
draft9-tufte.html, and the two streams join at rocket-time — the current
selection is the fragment, the rocket-completed transcript is the
commentary. A record ingests as:

    surface: whisper
    fragment: <text payload from the document — the highlighted region>
    commentary: <rocket-completed transcript>

Processing, live, per record:
1. **Patch** the fragment immediately per the commentary.
2. **Extract** the pattern: abstraction of
   IF fragment HOWEVER commentary→problem THEN commentary→solution
   (maps onto writing-patterns.edn: before=fragment, after=patch,
   trigger=problem generalised, why=the commentary itself).
3. **Count** matches across the whole paper and report the count as
   immediate spoken-back/on-screen feedback.
3½. **Trace visibly**: POST each processing step as a one-liner to
   `http://127.0.0.1:8130/log` (dispatch is logged by the receiver itself) —
   the page shows log lines as toasts, and the rocket pill click-expands
   the history. This is the operator's visual trace of the turn; the
   emacs-repl surface does not echo bell-triggered turns. The pill shows
   "rocket heard — processing…" (amber, pulsing) from dispatch until the
   processor POSTs `/done` — ALWAYS end a record's processing with
   `curl -s -X POST localhost:8130/done`, even when no edit was needed.
4. **Auto-apply** to the matched sites. The live commentary is the ruling —
   given per-pattern at capture time, so this does not violate the
   never-apply-without-ruling discipline; the count report is the
   checkpoint, and applications should be visually annotated in the HTML
   (highlighted changed spans) so review is instant and rollback is one
   word.

Selection → tex-source mapping needs no new machinery: the
verbatim-greps-once discipline applies to prose fragments exactly as to
OLD blocks. Emacs-as-surface is the same loop with region-highlight in
place of browser selection. Per M-smart-emacs-cursor's retrospective, this
is inherently interactive — build it WITH the operator at the keyboard,
not by autonomous dispatch.

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

## Coda: the loop is the paper's own construction (Joe, 2026-08-10)

The editing system is an exotype for the paper's contents. The text is the
fast state layer; writing-patterns.edn is the heritable rule field carried
alongside it; a pattern application is an operator whose reach comes from
reading the whole state field; pattern capture is the rule-rewriting layer.
The paper's negative results are the loop's design constraints: no locally
computable certificate of the good regime (no grep for well-written), and
undirected local interventions hold a foam, not extended coherent regions
(patterns applied without rulings). Hence the gate: "an external selection
policy helped us find this regime interactively" describes both the exotype
experiments and this workflow.
