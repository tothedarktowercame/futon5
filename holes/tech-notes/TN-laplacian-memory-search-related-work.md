# TN: Related work for the rebalancing graphical memory search (Lane G route-out)

Source: `paper/deepresearch-findings/FINDINGS-related-work.md` §G (verified 2026-08-08;
per-item URL provenance and the searched-and-empty query lists live in
`paper/deepresearch-findings/lane-G.md`). This note carries the memory-search-only and
dual-use items OUT of the paper pipeline so the paper's Related Work stays scoped.
All 15 items exist (adversarially verified: DOI/arXiv resolved, descriptions checked
against abstracts). Wording caveats from the verifier are reproduced inline.

## The finding about the field itself

Three literatures cover pieces of the construction and do not cite each other:
(1) structural-balance dynamics (discrete triad flips, continuous flows, signed-Laplacian
spectral theory); (2) adaptive/coevolutionary networks; (3) the CA-to-graph-rewriting
bridge (GNA). **The specific composite — a search/diffusion process on a graph whose
SIGNED edges are locally updated toward balance WHILE the search runs, with convergence
results — has no source.** Four near-misses bound the hole (each is missing exactly one
ingredient):

- `jarman2017adaptiverewiring` — diffusion drives local rewiring (closest mechanism:
  the process on the graph decides which edges update), but the graph is UNSIGNED.
  (Verifier: say "robustly leads to small-world structure", not "always for any
  nonzero rate".)
- `tian2024spreadingsigned` — spreading on signed graphs, spectrally classified
  (balanced / antibalanced / strictly unbalanced), but signs are STATIC.
- `cisnerosvelarde2021gradientbalance` — edge updates as gradient flow of a Heider
  "dissonance function" (the frame the rebalancing law should be presented as an
  instance of, or departure from), but NO process runs on the graph.
  (One-/two-faction strict-local-minima result is body-level.)
- `yoon2026signedkuramoto` — the only source posing "structure vs adaptation" exactly;
  answer: the plastic layer, not the signed structure, buys robustness. But it is
  Kuramoto phase-locking, not search. (Result is for two canonical classes of static
  signed networks.)

That bounded hole is the novelty statement for the write-up, with those four as the
citations that establish it.

## Pre-emptions — cite, do not re-derive

- **`altafini2013antagonistic`** — "a joint G-minimum exists iff the network is
  balanced" IS Altafini's bipartite-consensus theorem in Laplacian-flow form:
  bipartite consensus iff structurally balanced, otherwise collapse to zero. The
  frustrated-case failure mode is collapse to neutrality (not oscillation). IEEE TAC
  58(4):935-946, 2013. DOI 10.1109/TAC.2012.2224251.
- **`atay2020cheegersigned`** — the spectral form: multi-way Cheeger constants with
  h_k^sigma = 0 iff k balanced components; spectral clustering layer a graphical
  memory search would actually run. Discrete Mathematics 343(1):111616, 2020.

## The trap results (why a purely local rebalancing law can fail)

- **`marvel2009energylandscape`** — frustration landscape is dimpled with local minima;
  jammed non-balanced states exist for arbitrarily large networks up to the midpoint
  energy (Paley-type construction, verified verbatim). Landscape-theoretic reason a
  local law gets trapped. Also supports the paper's Part III negative by analogy.
- **`antal2005socialbalance`** — canonical local triad dynamics; a finite network
  always falls into a balanced absorbing state; infinite network has a dynamic
  transition at friendly-propensity 1/2. ("Jammed configurations" for the constrained
  variant is body/follow-up, not this abstract.)
- **`marvel2011continuousbalance`** — the continuous flow dX/dt = X^2: generic outcomes
  are all-friendly or exactly two hostile factions, closed-form membership. The
  dichotomy a continuous edge law inherits. (arXiv version has a different title.)
- **`angel2014errwlocalization`** — MUST-CITE for the write-up: linearly edge-reinforced
  random walk localizes (recurrent) for small initial weights on bounded-degree graphs,
  transient on non-amenable graphs for large weights. A PROVED version of "a search
  that rewrites the weights it walks on can freeze onto a small subgraph." Duke Math J
  163(5):889-921, 2014. (Published title hyphenates "edge-reinforced".)

## The frame (dual-use with the paper — now cited there too)

- **`berner2023adaptivenetworks`** (Physics Reports 2023) — adaptive dynamical networks:
  the named frame for a fast process on a slowly self-modifying substrate; explicit
  timescale-separation methods (in the review body, not its abstract).
- **`gross2008adaptivereview`** — "dynamics ON the network vs dynamics OF the network"
  vocabulary. (DOI resolves; Crossref stamps 2007 online-first, cite 2008.)
- **`sayama2009gna`** — generative network automata: graph rewriting integrating state
  transitions and topology transformations; the sibling that rewrites topology where
  MetaCA rewrites local rules.
- **`khajehabdollahi2023locally`** — locally adaptive CA/Ising (see the paper's Related
  Work; same work, one key).

## Strongest external corroboration of the paper's artefact claim (and a gain constraint)

- **`hara2026dmfthopfieldplasticity`** — DMFT of a Hopfield network with coevolving
  couplings during retrieval: moderate plasticity enlarges basins; EXCESSIVE plasticity
  imprints the imperfect initial cue itself, producing spurious attractors (verified
  against the arXiv Atom API; every quoted clause abstract-level). arXiv:2605.22254.
  For the memory search: a direct constraint on the rebalancing gain — too-faithful
  adoption of the current signal collapses the system onto that signal. For the paper:
  promote to must-cite if the Part III artefact claim is foregrounded.

## Presentation rule (from the audit)

Nothing connects structural-balance dynamics to rule-rewriting CA. The MetaCA analogy
must be carried by the adaptive-dynamical-networks frame + GNA, presented as an analogy
under a named existing frame — NOT as a cited theorem. The balance-iff-minimum claim in
`holes/M-formal-patterns.md` should be restated as an instance of Altafini.

## BibTeX

All entries are already in `paper/refs.bib` (appended 2026-08-08 from the verified
block). Copy from there when the memory-search write-up gets its own .bib.
