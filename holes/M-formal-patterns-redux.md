# M-formal-patterns-redux

**Status:** IDENTIFY — opened 2026-11-06 (Joe, via claude-6). Supersedes `M-formal-patterns` (retired as superseded/wontfix the same day).

## Why this mission exists (Joe, 2026-11-06)

`M-formal-patterns` set out to formalise design patterns by treating them as
cellular automata, and most of its remaining work was re-running old
experiments. Those experiments are done and are now pilots: accepted in good
faith and learned from, but nothing here depends on their framing or findings.
The MMCA work has moved on, and patterns are already half formalised by a
different route: a pattern as a 3/2 blend (Goguen's 3/2-pushout over the four
written parts IF, HOWEVER, BECAUSE, THEN) and a cascade as pasted pushouts.

Joe: "it would be better to be inspired by the analogy with CAs and formalise
patterns as we actually use them." And: "What we could do if we wanted to loop
back to the CAs is *formalise them using the same theory*. But we shouldn't
insist that the two formalisations are 'the same'."

What the old mission got right, and this one keeps: a pattern and a pattern
language are not very different, because a composition of blends is itself a
complex blend; and the old mission made next steps explicit, leaning into an
active-inference reading of self-evidencing.

## Gaps (Joe, 2026-11-06)

1. `M-formal-patterns` never completed.
2. Patterns are partly formalised (3/2 blends, pasted pushouts), not fully.
3. There is no active-inference interpretation of patterns that builds on, or
   improves, the one in Lynn (2026).
4. Open: how the formalisation of design patterns maps to a simplified
   formalisation of cellular automata, stated in the same theory, without
   claiming the two are the same.

## Constraints carried from Joe's answers (T-formal-patterns-unblock, 2026-11-06)

- A finding counts only when it rests on a test, a proof, or a concrete
  argument. Notes are not evidence; an unbacked claim is marked open, never
  cited as a finding.
- The Baldwin effect enters only through a formal account built on the War
  Machine's learning loop (e.g. its β and related variables), where a cascade
  is applied and updated in use, not run top to bottom like an automaton.
  Copying acquired traits gets its own name and never passes as Baldwin.
- In the general case the substrate is a semilattice, not a grid; "grid" is
  right only for one-dimensional automata.

## Sources for MAP

- Predecessor: `futon5/holes/M-formal-patterns.md` (retired; read, do not
  re-run), and its register `futon5/TN-baldwin-reboot.md` §10.
- Blog post: "Cascades as pasted pushouts: four records, drawn" (3 November
  2026), https://zone.hyperreal.enterprises/2026-11-03-cascades-as-pasted-pushouts.html
- Lynn (2026). "Conceptual Blending in Humans and Language Models".
  Frontiers in Psychology 17. doi:10.3389/fpsyg.2026.1849083. Reading notes and
  a run of its supplementary code:
  `~/notes/wm-next/anticipation/parr-friston-and-futon-theory.md` (section
  "Lynn (2026)") and `~/notes/wm-next/anticipation/lynn-supp/`.
- Lean carriers: `mathlib4/DarkTower/WarMachine/ThreeHalvesBlend.lean`,
  `mathlib4/DarkTower/WarMachine/MemoryCones.lean`.
- Runtime square construction: `futon2/src/futon2/aif/three_halves_square.clj`.

## Acceptance checklist

- [ ] MAP: a note in this mission lists what `M-formal-patterns` established
  that survives as a test, proof or concrete argument (with locators), and
  what is only a pilot result. Locator: a "MAP" section below with one row per
  item and its backing.
- [ ] MAP: the 3/2-blend and pasted-pushout formalisation as it stands today is
  summarised from the blog post and the Lean carriers, with what is proved and
  what is open. Locator: the same MAP section, citing Lean declaration names.
- [ ] MAP: Lynn (2026)'s reading of blending is set against the active-inference
  reading the War Machine uses (precision set by preferences, the C of expected
  free energy), stating where it agrees, where it differs, and what an improved
  account would need. Locator: the same MAP section.
- [ ] Gap 4 is stated as a precise question: which simplified CA formalisation,
  in which shared theory, and what map is claimed. Locator: the same MAP section.
