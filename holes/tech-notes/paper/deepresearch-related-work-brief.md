# DeepResearch brief: Related Work expansion + contextualisation

STATUS: PREPARED, NOT DISPATCHED. Operator: Joe. Drafted 2026-08-08 (claude-5).
Dual-use target: (1) the Related Work section of the rule-rewriting CA paper
(draft9, Discussion part); (2) Joe's separate line of work — a rebalancing
graphical memory search based on a locally updating Laplacian, which takes
inspiration from the MetaCAs. Sources that pin down the shared mechanism are
the highest-value finds, reusable in both places.

## The paper, in brief (context for the researcher)

A cellular automaton where each cell carries, alongside its binary state
(phenotype), the eight-bit ECA rule it executes (genotype). Operators rewrite
rules while the system runs. The studied family: permutations sigma of the
eight truth-table positions, writing the negation of position p to position
sigma(p) — 8! = 40,320 operators, exactly classified. Findings: (I) an
operator has fixed rules iff every cycle of sigma is even; every fixed rule
has Langton activity 1/2, so the classical edge-of-chaos coordinate is
degenerate on the fixed points; no critical point found in a finite-size
scan, only a broad crossover. (II) A causal perturbation measurement
(fork-and-flip damage reach): reach roughly doubles when rule updates read
the current state field; sustained rule diversity, mutation, refuges,
niches, mobility all leave it unchanged; a locally deciding gate re-derives
the ordering (blind = prescribed-rate baseline; current-reading exceeds it;
frozen-reading falls below). (III) With per-cell operators (exotypes) under
an optimizing selection rule, three outcomes: changing phase wins, frozen
phase wins, or coexistence for the full horizon; no tested locally
computable quantity finds or holds the coexistence; boundary-copying
interventions accelerate the freezing they were built to prevent; undirected
noise holds a "foam" of small frozen islands amid changing surroundings; the
freezing itself is an artefact of high-precision adoption (with adoption off,
coexistence is the default state).

## Already cited (do not re-surface these; build outward from them)

- svozil2025symmetries — ECA symmetry group (order 4, 88 classes); our S8
  family is not in it.
- pavlic2014selfref — CA with state-dependent feedback (nearest neighbour).
- dennunzio2011nonuniform — non-uniform CA; finite rule sets reduce to
  uniform CA on product alphabets.
- mori1998rulechanging — rule-changing CA.
- langton1990computation, wuensche1999classifying, sakai2002edge — edge of
  chaos / classification background.
- aracena2008maximum, corneli2015search.

## Directions and questions

### A. Rule-rewriting operators as first-class objects
Does ANY prior work treat rule-space rewriting operators as an enumerated,
exactly classified family with derived fixed-point structure? Adjacent
genres to check and differentiate: structurally dynamic CA (Ilachinski),
automata networks with evolving local functions, "self-modifying" CA and
Von Neumann's self-modification lineage, genetic-programming-on-CA-rules.
The paper claims this does not appear in the surveyed literature — stress-test
that claim; a near-miss would need citing and distinguishing.

### B. Control of chaos, and its inverse
Our Part III interventions are local control laws trying to hold a marginal
mixed state; the noise arms are effectively *chaos maintenance* (keeping a
system out of an absorbing frozen state). Relevant literatures: OGY-style
control of chaos; pinning control of coupled map lattices / spatiotemporal
chaos; "anticontrol"/chaotification; control targeting of unstable states in
extended systems. Question: is there work on *maintaining* ordered–chaotic
coexistence (not full stabilization) in spatially extended systems, and on
the failure modes of local/boundary-targeted control there? The
boundary-adoption result (control input accelerating the collapse it was
meant to prevent) — any precedent?

### C. Edge of chaos: critiques and modern standing
Packard's original claim; Mitchell–Hraber–Crutchfield's re-examination; any
recent (2015+) assessments, including reservoir-computing-era "criticality
is/isn't computationally optimal" results. Where does a lambda-degeneracy
result (all fixed points at activity 1/2) land against that history?

### D. Absorbing states and directed percolation
The freezing transition is an absorbing-state transition; we report a broad
crossover and evidence *against* a critical point at tested sizes. What does
the DP / absorbing-phase-transition literature say about CA with quenched or
co-evolving disorder in the update rule? Any known cases where coupling the
rule to the state destroys or smears the DP transition?

### E. Self-tuning to criticality — the positive counterpart
Our central negative result: no locally computable quantity self-tunes the
system to coexistence. The literature has claimed positives: Bornholdt–Rohlf
style adaptive-network rules that drive toward criticality, SOC proper, and
the critiques (e.g. self-organization requiring conservation laws or hidden
global signals). Question: in the known "local rule tunes to criticality"
results, what global information is smuggled in, and does our
decorrelation-time obstruction (the quantity a cell would need decorrelates
faster than the window discrimination requires) have a named counterpart?

### F. Baldwin effect and heritable acquired modifiers
The exotype layer is inheritance of an acquired modifier (per-cell operator,
transmitted between neighbours). Hinton–Nowlan and the ALife Baldwin
literature; also cultural-transmission models with a fast heritable channel
coupled to a slow one. What is the cleanest citation for "acquired,
transmissible modifier layer changes evolutionary dynamics" in a lattice
setting? (Background: holes/baldwin-notes/ TN series.)

### G. The Laplacian bridge (dual-use; speculative but load-bearing for Joe's
other project)
Joe's separate work: a rebalancing graphical memory search based on a
locally updating Laplacian, MetaCA-inspired. The formal thread (see
holes/M-formal-patterns.md): pattern networks as signed graphs; held
tensions as antiferromagnetic bonds; joint minimum exists iff the network is
balanced (frustrated odd cycles have no ground state). Literatures to sweep:
structural balance dynamics (Cartwright–Harary onward, including continuous
Laplacian-flow treatments); signed-graph Laplacians and their spectra;
adaptive/coevolving networks where edge updates are local; diffusion or
random-walk search on time-evolving graphs; associative memory on graphs
(modern Hopfield included) where the *graph* is the plastic layer.
Question: what existing frame best describes "local edge updates that
rebalance a signed Laplacian while a search/diffusion process runs on it" —
and does any of it connect formally to rule-rewriting lattices (both are
two-layer systems: a fast state process on a slowly self-modifying
substrate)? Sources that make the two-layer analogy precise are reusable in
BOTH the paper's Related Work and the memory-search write-up.

## Output specification

Annotated bibliography, grouped by direction A–G. Per item:
1. Full citation with DOI or arXiv id, VERIFIED TO EXIST (no invented
   references; if uncertain, flag rather than guess).
2. 2–3 sentences: what it does.
3. One sentence: which specific claim of ours it contextualises, supports,
   or threatens (name the paper section, or "memory-search", or "both").
4. A BibTeX block at the end for everything recommended for citation.
Prefer 15–30 high-relevance sources over exhaustive coverage. Distinguish
"must cite" (a reviewer would expect it) from "enriches". Explicitly note
any find that CONTRADICTS a claim in the paper — that is the most valuable
category, not something to soften.

## Constraints

- Contextualisation only: do not propose reframing the paper's claims.
- The paper's own scope discipline applies: it measures constructions, not
  the 8! family; do not suggest citations that would require claims we
  cannot back.
- Deliverable feeds a hand-polish pass by Joe; write for reuse, not prose.
