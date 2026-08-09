# FINDINGS — Related Work (DeepResearch, run 2026-08-08)

Brief: `../deepresearch-related-work-brief.md`. Targets: (1) Related Work / Discussion of the
rule-rewriting CA paper (draft9); (2) Joe's rebalancing graphical memory search on a locally
updating signed Laplacian. Lane files: `lane-A.md` … `lane-G.md` (keep them — they carry the
per-item URL provenance and the searched-and-empty query lists this file only summarises).

## Method, honestly

- **7 lanes** (A–G), one subagent each, run in parallel 2026-08-08. Each lane was told to
  stress-test the paper's gap claims rather than confirm them, to search across physics /
  ALife / CS / control / probability vocabularies, and to record the queries that came back
  empty so the negatives are auditable.
- **74 proposed items** survived the lanes' own filtering (lanes discarded further candidates
  they could not load a landing page for — those are listed under "Rejected / not recommended"
  below, and were never entered into the verification pipeline).
- **All 74 were then existence-checked by a separate adversarial verifier**, which resolved each
  DOI / arXiv id independently (Crossref, arXiv Atom API, OpenAlex, PubMed, PMC, Semantic
  Scholar, publisher pages where fetchable) and checked each item's one-line description against
  the abstract of the thing the identifier actually resolves to.
- **Result: 73 CONFIRMED, 1 MISMATCH (corrected, not dropped — `rollier2024taxonomy`, wrong
  journal year/volume), 0 rejected as non-existent.** No invented references survived, and none
  were found. That is a clean result but it is not the same as "every descriptive sentence is
  verified": ~30 items carry a *wording* caveat where a clause in the description is body-level
  rather than abstract-level. Those are itemised in §6 and must be checked against PDFs before
  their content claims enter the paper.
- **74 items = 72 distinct works.** Two works were found independently by two lanes and carry two
  keys: Khajehabdollahi et al. 2023 (lane A `khajehabdollahi2023locally` = lane G
  `khajehabdollahi2023locallyadaptiveca`) and Bonachela & Muñoz 2009 (lane D
  `bonachela2009selforganization` = lane E `bonachela2009soqc`). **The BibTeX block at the end
  emits one entry per work. RESOLVED 2026-08-08 (claude): the section headings and the BibTeX
  block now both use the canonical keys `khajehabdollahi2023locally` and
  `bonachela2009selforganization`; the lane-file aliases survive only as provenance notes.**
- Method limit worth stating: APS (`journals.aps.org`), MIT Press (`direct.mit.edu`),
  ScienceDirect, IEEE and nature.com return 403 to automated fetch. For items behind those,
  bibliographic data is index-verified and abstract text came from indexed renderings. Flagged
  per item.

---

## 1. CONTRADICTIONS AND NEAR-MISSES — read this section before anything else

**Verdict: no verified source falsifies a measured result of the paper. Ten distinct works
threaten *novelty*, *framing*, or the *inference* drawn from a measurement.** Ranked by how
badly. Nothing here is softened; where the threat is only to phrasing, that is said explicitly,
because that is the actual finding.

### T1. `hedlund1969endomorphisms` — λ-degeneracy under a structural constraint is 56 years old (Part I)
**Threatens: the implicit novelty of Part I's "every fixed rule has λ = 1/2".**
Hedlund's balance theorem: a surjective CA has a balanced local rule. Instantiated at k=2, m=3,
**every surjective ECA has Langton activity exactly 1/2**. So "a structural constraint pins a
derived subfamily of ECA rule space at λ = 1/2, where λ is informationless" is a known
phenomenon, for the most canonical constraint there is. Modern restatements:
`capobianco2017postsurjectivity` (reversible ⇒ balanced over arbitrary groups),
`paturi2025reversibility` (balance in the *non-uniform* CA setting — the setting closest to ours).
**What survives:** our λ = 1/2 set is the **fixed-point set of a rule-rewriting operator**,
produced by the rule dynamics itself (even-cycle condition on σ), not the surjective set. Lane C
searched specifically and found no prior work showing λ degenerate on the fixed points of a
rule-rewriting map. **Not citing Hedlund is the single most likely reviewer objection in lane C.**
Secondary, from lane A: at Boolean-function level the balance consequence is the
permutation-generalised form of "self-dual ⇒ balanced" — do not present the *balance* as
surprising; the *degeneracy of the edge-of-chaos coordinate on the attracting set* is the claim.

### T2. `khajehabdollahi2023locally` (= `…locallyadaptiveca`) — the Part II construction exists, 2023, and its local variant self-tunes to criticality (Parts II and III)
**Threatens: (a) any novelty claim for the state-coupled rule-update construction; (b) the
unscoped reading of Part III's negative.** Found independently by lanes A and G.
(a) They literally concatenate θ_i(t) (that cell's local rule parameters) onto σ_i(t) — the
genotype/phenotype split, updated online and locally by the state field. Two years earlier, in
the ML/ALife community. **Any "this construction does not appear in the surveyed literature"
sentence must be softened or deleted.** It does *not* do the measurement: no fork-and-flip causal
reach, no operator family, no classification — so the reach doubling and the ordering
(blind < current-reading; frozen-reading < blind) stand untouched. Cite alongside
`pavlic2014selfref` and `mori1998rulechanging` and state construction-vs-measurement explicitly.
(b) **The louder half.** Their Ising model has a *local* variant: each cell measures its nearest
neighbours' magnetisation and diffuses a local temperature parameter — and it self-organises to
(the vicinity of) the critical temperature. That is a locally computable quantity tuning an
adaptive-rule lattice to a critical point, i.e. a positive of exactly the kind Part III reports as
absent. It does not literally refute Part III (thermal criticality with a continuous,
near-conserved diffusive knob vs holding changing/frozen coexistence in ECA rule space under a
discrete high-precision adoption rule; our negative is scoped to *tested* quantities in *our*
system). The distinguishing sentence must be written down now, not discovered in review.

### T3. `vojta2006rare` + `hooyberghs2004absorbing` + `vojta2009infinite` — a broad crossover is the *predicted* finite-size signature of a genuine transition under quenched disorder (Part I)
**Threatens: the inference from "no critical point in a finite-size scan" to "no critical point".**
This is lane D's headline. Quenched disorder is *relevant* at the 1d DP fixed point (Harris:
ν⊥ ≈ 1.097 < 2/d = 2); the flow is to an **infinite-randomness fixed point with activated
dynamical scaling**, ln(t_corr) ~ L^ψ. Under activated scaling correlation times are
stretched-exponential in L, so *any* accessible scan shows (i) an extended Griffiths region of
continuously varying exponents and (ii) a size-drifting, apparently smeared transition.
Vojta–Farquhar–Mast needed **8000×8000 lattices and t up to 10^10** to resolve the 2d disordered
contact process. **Our scan cannot on its own distinguish "no transition" from "transition with
activated dynamical scaling."** A stat-phys referee will raise this. The paper is stronger raising
it first and naming the discriminating test: attempt a collapse under *activated* scaling (vs ln t,
L^ψ) rather than the power-law collapse tried, and check whether frozen-state lifetimes scale as a
power of L or exponentially.
**Counterweight (cite alongside, it supports our reading):** `vojta2004broadening` — spatially
**extended/correlated** quenched defects genuinely *smear* the DP transition away; rare regions
order independently and the order parameter turns on over a finite window rather than at a point.
Our self-generated frozen refuges / "foam" are structurally that. Scope caveat: theirs are
externally imposed and quenched, ours are dynamically generated — cite as a precedent for the
phenomenon, not as an established explanation.

### T4. `droste2013analytical` — an analytically derived local rule that *does* self-tune to criticality, and it names our obstruction (Part III)
**Threatens: any unscoped phrasing of Part III's central negative.**
Droste–Do–Gross prove that activity-dependent local rewiring creates an **attractive steady state
at criticality**, and derive *why* it works: only firing nodes carry information about the global
phase ("neurons in the resting state do not possess any information about the global phase, as they
occur in both phases"), so the rule must "[create links] tentatively as long as no definitive
information is known, but [destroy them] decisively once information about the global phase is
available", under the rate condition l ≪ g. Both quotes verified verbatim in the PMC full text.
**"No local rule can self-tune" / "local information is insufficient" is refuted by this paper and
must be scoped or retracted.** It also names a design apparently not tested: an **asymmetric,
one-sided** rule driven by an observable that is a *one-sided certificate* of the global phase
rather than a two-sided estimator. If every tested quantity was a symmetric estimator-shaped
statistic, say so explicitly — that is the difference. Bonus: "limited local accessibility of the
global phase" is the closest existing *name* for our decorrelation-time obstruction.

### T5. `huberman1993evolutionary` — the Part III artefact claim has the same logical form as a 1993 result in the same class of system (Part III)
**Threatens: the "surprising / unprecedented" register of the artefact claim.**
Huberman & Glance re-ran Nowak & May's lattice adopt-from-best-neighbour game asynchronously; the
persistent coexistence and the "dynamic fractals" vanished, and the conclusion was that the reported
phase outcome was an **artefact of the update discipline**, not of the modelled interaction. Our
"the freezing is an artefact of high-precision adoption" is the identical move. It falsifies
nothing we measured. Uncited, Part III reads as a rediscovery. Cite with `nowak1992spatial` (citing
5 without 6 is unreadable) and state the differences: their knob = update **synchrony**, ours =
adoption **fidelity/precision**; their adopted object = a game strategy, ours = a rule-rewriting
operator; their artefact **destroys** coexistence, ours **creates** freezing. That narrower claim
is defensible.

### T6. `bagnoli2012control` — boundary-copying is a known control class whose designed function is exactly what we call a backfire; and a local quantity *does* work there (Part III, Part II)
**Threatens: two Part III framings at once.**
(i) They control totalistic CA by **setting a fraction of the slave's sites equal to the master's**
— control by copying a target configuration into a subset of cells — with a *critical control
density*. That class is designed to drag the bulk toward the copied configuration, so "copying the
frozen boundary accelerates freezing" is what the mechanism does, not an anomaly.
(ii) They site controllers using a **locally computable quantity — the number of nonzero Boolean
derivatives**, the same object as our fork-and-flip sensitivity — and it works. Different objective
(convergence to a prescribed target, not maintenance of a mixed regime), so "no tested locally
computable quantity finds or holds the coexistence" survives *as stated about our objective*, but
the near-miss must be distinguished in Related Work rather than left for a referee.
Companion: `bagnoli2018boundary` establishes boundary-only actuation of Boolean CA as a studied
control problem with reachability limits — our backfiring boundary result then lands inside an
existing conversation rather than in a vacuum.

### T7. `sieber2014controlling` — an ordered–chaotic coexistence *can* be held by control (Part III)
**Threatens: any broad phrasing that the coexistence cannot be maintained.**
Chimera states — coexisting coherent and incoherent domains that otherwise collapse to full
coherence in finite systems — are stabilised through the bifurcation where the regime loses
attractivity. So the answer to "can a marginal ordered–chaotic coexistence be held?" is *yes*.
**The distinguishing line, which Part III must state explicitly, is locality:** their handle is
global (feedback on a system parameter driven by an aggregate statistic of the chaotic regime), not
a locally computable per-cell quantity. Their finite-size framing (coexistence collapses in finite
systems, lifetime grows with size) also bears on our finite-size scan.

### T8. `gross2006epidemic` — a rival, testable explanation of our Part I/III data that we have not excluded (Parts I and III)
**Threatens: the "broad crossover" claim as under-determined.**
When the substrate co-evolves with the state, the classical outcome is that the continuous
absorbing transition becomes **first order, with bistability and hysteresis**. A first-order
transition *also* presents as "no critical point" — but it **is** a transition, and it is diagnosable
at our sizes by sweep-up/sweep-down hysteresis loops and order-parameter bimodality. Part III's
three outcomes including full-horizon coexistence look suggestively bistable. **If we do not test
for hysteresis, the crossover claim is under-determined and a referee can say so.**

### T9. `cattaneo1997transformations` — an enumerated, exactly classified family of rule-space transformations already exists (Part I)
**Threatens: the "enumerated + exactly classified" half of the lane-A gap claim.**
Cattaneo–Formenti–Margara–Mauri introduce "double permutation" to study classes of transformations
of the 1D CA rule space, characterise each class by which metrical / language-theoretic / dynamical
properties it preserves, and give **exact cardinalities of the induced quotient sets**. Their maps
are *static conjugacies* partitioning rule space, applied once — not operators iterated as the
update law of a live lattice, hence no fixed-rule / cycle-parity structure. **This is the closest
formal neighbour in existence and the item Related Work most needs to cite and distinguish.**
Cf. `lipackard1990structure`, whose operator family is the eight single-bit flips (the rule-space
hypercube) — the precedent for "operators on truth-table positions applied across all 256 rules",
and the contrast that shows single-bit flips have no interesting fixed-point structure where an
S_8 action does.

### T10. `klemm2003global` — the qualitative shape of the noise result is established (Part III)
**Threatens: novelty of the phenomenon, not the measurement.**
High-fidelity local copying under a similarity gate freezes an Axelrod lattice into absorbing
configurations; undirected noise destabilises them, with a sharp criterion — noise rate r against
perturbation relaxation time T (r ≲ 1/T → monoculture; r ≳ 1/T → sustained disorder; T diverges in
the thermodynamic limit, so the frozen state survives there). Our "undirected noise holds a foam of
small frozen islands" is a finite-size, finite-rate regime of exactly this competition. Present ours
as the CA-substrate instance with the extra ingredient (the transmitted object is a rule-rewriting
operator), not as a new phenomenon.

### Pre-emptions of the memory-search write-up (not the paper)
- **`altafini2013antagonistic`** — "a joint G-minimum exists iff the network is balanced" **is**
  Altafini's bipartite-consensus theorem in Laplacian-flow form (bipartite consensus iff structurally
  balanced; otherwise collapse to zero). **Cite, do not re-derive.** `atay2020cheegersigned` is the
  spectral form (h_k^σ = 0 iff k balanced components).
- **`marvel2009energylandscape`** — jammed, non-balanced local minima of the frustration energy
  exist for arbitrarily large networks up to the midpoint energy (Paley-type construction, verified
  verbatim in the PDF). Landscape-theoretic reason a purely local rebalancing law gets trapped;
  supports rather than threatens the Part III negative, by analogy.

### Weaker flags worth one sentence each
- **`park2023farfrom`** raises the bar for Part II: they separate phase shifts in oscillatory
  attractors from genuine phenotypic divergence. A referee may ask whether our damage reach conflates
  the two. (Also: their abstract's forward-looking claim — criticality may emerge at larger scales
  through module coupling — should not be dropped when citing them as a negative.)
- **`grassberger1995damage`** places damage-spreading in the DP framework *provided* the healing
  probability is nonzero. Our frozen regions do not heal — the excluded case. Cite carefully.
- **`carro2016noisy`** is a scope warning: a finite-size scan of copy-plus-noise dynamics is
  *predicted* to show a size-dependent apparent transition that is not a phase transition in the
  thermodynamic limit. Do not present our crossover as evidence about the thermodynamic limit.
- **`santos2015illusion`** contradicts a claim we do *not* make (Baldwin speedup). Citing it is
  protective; using any speedup language anywhere without it is the exposure.
- **`wackerbauer2007noise`** qualifies the noise arm: the effect of noise on maintenance is
  non-monotonic in amplitude and depends on uniform vs spatially structured noise. Report which
  regime our noise arm sits in rather than claiming noise maintains the changing phase generally.

### What is NOT pre-empted (the audited negatives, per lane)
- **A:** no prior work that (a) takes a group-theoretic family of rule-table → rule-table maps,
  (b) enumerates it exhaustively, (c) applies it as the update law of a running lattice, and
  (d) derives the operator's fixed-point structure as a theorem. Every near neighbour breaks ≥2 of
  those four conjuncts. Empty searches: "metamorphic cellular automata"; CA whose rule evolves by a
  deterministic map on the rule table; "8!"/"40320" operator families on rule space; rule fields
  copied between neighbours as a two-layer fast-state/slow-rule system; group actions combining
  permutation **and** negation of truth-table positions (the literature group is reflection ×
  state-complement, order 4 = `svozil2025symmetries`, already cited).
- **B:** no work that maintains an ordered–chaotic coexistence in a lattice using **only locally
  computable per-cell quantities**; and no work treating control of a system whose **update rule
  itself** is the controlled variable (the control literature perturbs states or bifurcation
  parameters, never the local transition function's truth table).
- **C:** no prior work showing λ degenerate on the **fixed-point set of a rule-rewriting operator**.
- **D:** no work in which the local **rule table** is rewritten by the dynamics and the resulting
  absorbing-state transition is characterised (exponents, class, existence of a critical point). The
  adjacent genres each vary something else: a rate (Noest, Hooyberghs), a synchrony parameter
  (Fatès), an edge (Gross), an environment in time (Vázquez).
- **E:** no work that attempts, and reports failing at, a local self-tuning rule for **coexistence of
  a frozen and a changing phase**. Every positive in that literature targets a *point* (K_c = 2,
  branching ratio 1, the order–disorder transition); nobody is trying to hold a two-phase mixture.
  Worth one sentence in the Discussion.
- **F:** no canonical "Baldwin effect on a lattice with an acquired transmissible modifier" paper
  exists. The Baldwin canon (Hinton–Nowlan, Mayley, Ackley–Littman, Suzuki–Arita) is panmictic.
  Cite the canon for the mechanism, the cultural-dissemination physics line for the lattice.
- **G:** nothing does the specific composite — a search/diffusion process on a graph whose **signed**
  edges are locally updated toward balance **while the search runs**, with convergence results. Four
  verified near-misses bound the hole: `jarman2017adaptiverewiring` (diffusion drives rewiring, graph
  unsigned), `tian2024spreadingsigned` (diffusion on a signed graph, signs static),
  `cisnerosvelarde2021gradientbalance` (rebalancing edge updates, no process on the graph),
  `yoon2026signedkuramoto` (structure-vs-adaptation, but Kuramoto phase-locking not search). Also:
  **nothing connects structural-balance dynamics to rule-rewriting CA** — the two-layer analogy must
  be carried by the adaptive-dynamical-networks frame and GNA, presented as an analogy under a named
  existing frame, not as a cited theorem.

---

## A. Rule-rewriting operators as first-class objects
*(8 items: 5 must-cite, 3 enriches. Verdict: gap claim SURVIVES but must be narrowed to the
four-conjunct form above; three concessions are mandatory.)*

The strongest positive move available is the **recency test**: the most recent comprehensive CA
taxonomy has five families and no category for rules rewritten during evolution. That is far
stronger evidence for the gap than asserting it.

### MUST CITE

**`cattaneo1997transformations`** — G. Cattaneo, E. Formenti, L. Margara, G. Mauri,
"Transformations of the one-dimensional cellular automata rule space", *Parallel Computing*
23(11):1593–1611, 1997. DOI 10.1016/S0167-8191(97)00076-8.
Introduces "double permutation" to study classes of transformations of the 1D CA rule space, each
class characterised by the metrical / language-theoretic / dynamical properties it preserves and
inducing an equivalence relation; gives exact cardinalities of the quotient sets.
→ **Part I. THREATENS (T9)** — closest existing "enumerated + exactly classified family of
rule-space transformations"; distinguish on static conjugacy vs iterated operator on a live lattice.

**`rollier2024taxonomy`** *(CORRECTED CITATION)* — M. Rollier, K. M. C. Zielinski, A. J. Daly,
O. M. Bruno, J. M. Baetens, "A comprehensive taxonomy of cellular automata", *CNSNS* **140**
(**2025**), art. 108362; arXiv:2401.08408 (2024). DOI 10.1016/j.cnsns.2024.108362.
Five families — asynchronous, stochastic, multi-state, extended-neighbourhood, non-uniform — each
with definition, variations, a genotype/phenotype analysis (genotype = model-definition information,
e.g. rule table and λ; phenotype = simulation-outcome information, e.g. Wolfram classes, Lyapunov
exponents) and applications.
→ **Part I / framing.** Supplies the *established* genotype/phenotype vocabulary our model reifies
as a runtime object, and is the citable instrument for the gap claim. **Verifier note:** the claim
that its non-uniform family treats rule heterogeneity as *predetermined assignments* rather than
rule modification is body-level, not in the abstract — check before it carries weight.

**`khajehabdollahi2023locally`** — see **T2**. ALIFE 2023, MIT Press, DOI 10.1162/isal_a_00663;
arXiv:2306.07067. → **Parts II and III. THREATENS.**

**`sipper1996coevolving`** — M. Sipper, "Co-evolving non-uniform cellular automata to perform
computations", *Physica D* 92(3–4):193–208, 1996. DOI 10.1016/0167-2789(95)00286-3.
Cellular programming: each cell holds its own rule; rules co-evolve by a *local* algorithm —
per-cell fitness, rules exchanged with neighbours — not a global GA over one rule.
→ **Part III.** The reference genre for "per-cell rule updated by a local selection rule with
neighbour exchange". The exotype layer cannot be presented as the first per-cell heritable rule
lattice; ours differs in that the inherited object is a rule-*rewriting operator* and the selection
rule is a fixed deterministic optimiser. **Verifier caveat: the r=1 density-classification numbers
(~0.93–0.94 vs ~0.83 uniform) could NOT be read out of this paper's abstract; one secondary source
attributes different figures to it. Do not print those numbers without checking the body.**

**`ilachinski1987structurally`** — A. Ilachinski, P. Halpern, "Structurally dynamic cellular
automata", *Complex Systems* 1(3):503–527, 1987. No DOI.
Binary value-configurations and the conventionally quiescent underlying topology are dynamically
coupled; topology alterations are defined by local transition rules analogous to the value functions
of fixed-lattice CA. Reports growth, decay, periodicity, relaxation to stable effective
dimensionality. (Verifier read the journal PDF; abstract matches essentially verbatim.)
→ **Part I / framing.** The canonical prior "the CA rewrites part of itself while running", and the
one a reviewer names first. Clean distinguishing axis: SDCA rewrite the **substrate** and leave the
local function fixed; we rewrite the **local function** and leave the substrate fixed.

### ENRICHES

**`tomita2007selfdescription`** — K. Tomita, S. Murata, H. Kurokawa, "Self-description for
construction and computation on graph-rewriting automata", *Artificial Life* 13(4):383–396, 2007.
DOI 10.1162/artl.2007.13.4.383. A "metanode" structure embeds the rule set inside the graph, giving
universal graph-rewriting automata "that can serve as a model of systems that maintain themselves
through replication and modification".
→ **Part I / framing.** Strongest representative of the von Neumann self-modification lineage where
the rule set is a manipulable object *inside* the system. Differentiate: construction/universality
mechanism with rules as copyable data, not a classified algebraic operator family with a fixed-point
theorem. **Verifier caveat: the "hand-coding / evolutionary / exhaustive search" triple is a
line-of-work claim, not in this abstract — do not attribute it to this paper.**

**`lipackard1990structure`** — W. Li, N. Packard, "The structure of the elementary cellular automata
rule space", *Complex Systems* 4(3):281–297, 1990. No DOI. Intra-class connection probability
(one truth-table bit away) 0.3–0.5, showing strong clustering of behaviour; mean-field clusters
classified nonlinear / linear / inversely linear by the "hot bits" of the rule table. (Verifier read
the article PDF; both claims verbatim.)
→ **Part I.** Prior art for "operators on truth-table positions applied exhaustively across all 256
rules"; their family is the eight single-bit flips, ours is 8! permutations-with-negation.

**`miszczak2023ruleswitching`** — J. A. Miszczak, "Rule switching mechanisms in the Game of Life with
synchronous and asynchronous updating policy", *Physica Scripta* 98(11):115210, 2023.
DOI 10.1088/1402-4896/acfc6c; arXiv:2310.05979. Game of Life "with the dynamical process governing
the rule selection at each step"; the synchronisation policy controls a stability/growth trade-off.
→ **Part I / framing.** Most recent instance of "the rule is a dynamical variable" — the *idea*
cannot be claimed as new; but the switching maps are neither enumerated nor classified and no
fixed-point analysis is attempted.

---

## B. Control of chaos, and its inverse
*(12 items: 7 must-cite, 5 enriches. Verdict: dense, well-named literature; two genuine pre-emptions
of Part III framing; the negative at the level of mechanism survives.)*

Two vocabulary imports the paper should adopt rather than have supplied by a referee:
**chaos maintenance / "preserving chaos" / anticontrol** for the undirected-noise arms, and
**pinning / boundary regional control** for the intervention arms.

### MUST CITE

**`yang1995preserving`** — W. Yang, M. Ding, A. J. Mandell, E. Ott, "Preserving chaos: Control
strategies to preserve complex dynamics with potential relevance to biological disorders", *PRE*
51(1):102–110, 1995. DOI 10.1103/PhysRevE.51.102 (PMID 9962622).
Where OGY control stabilises an unstable periodic orbit, this applies small perturbations to *prevent*
the trajectory leaving a chaotic saddle for a regular/absorbing attractor, keeping complex dynamics
alive after a crisis. Motivated by cases where the regular state is the pathology.
→ **Part III.** The name and the citation for our undirected-noise arms. **Verifier: PubMed serves no
abstract for this 1995 article; the mechanism description comes from secondary accounts. Also soften
"the founding paper" to "an originating paper" — In et al. below is a contemporaneous claimant.**

**`grigoriev1997pinning`** — R. O. Grigoriev, M. C. Cross, H. G. Schuster, "Pinning Control of
Spatiotemporal Chaos", *PRL* 79(15):2795–2798, 1997. DOI 10.1103/PhysRevLett.79.2795;
arXiv:chao-dyn/9705001. Linear control theory for localized control of a coupled map lattice: the
**optimal arrangement of control sites depends on system symmetry, and their minimal density depends
on noise strength** (both verbatim in the abstract).
→ **Part III.** The standard reference for how much local control an extended chaotic system needs
and what sets the price; the noise-sets-the-density result is directly germane to our noise arms.

**`bagnoli2012control`** — see **T6**. *PRE* 86(6):066201, 2012. DOI 10.1103/PhysRevE.86.066201;
arXiv:1206.2237. → **Parts III and II. THREATENS.** Single most important item in the lane.

**`bagnoli2018boundary`** — F. Bagnoli, S. El Yacoubi, R. Rechtman, "Toward a boundary regional
control problem for Boolean cellular automata", *Natural Computing* 17(3):479–486, 2018.
DOI 10.1007/s11047-017-9626-1; arXiv:1606.05122. Can a target configuration in a region of interest
be reached by acting only on boundary values, with the region of interest itself a portion of the
boundary? (Abstract-verified, including the boundary-as-region clause. Earlier version: ACRI 2016,
LNCS 9863, 101–112.)
→ **Part III.** Direct prior-art genre for the boundary-copying arm; boundary-only actuation of a CA
is a studied control problem with known reachability limits.

**`sieber2014controlling`** — see **T7**. *PRL* 112(5):054102, 2014.
DOI 10.1103/PhysRevLett.112.054102; arXiv:1310.7560. → **Part III. THREATENS.**

**`hilker2006paradox`** — F. M. Hilker, F. H. Westerhoff, "Paradox of simple limiter control", *PRE*
73(5):052901, 2006. DOI 10.1103/PhysRevE.73.052901 (PMID 16802979). Limiter control (clip the state
at a threshold) "can significantly shift its mean value, which is a countereffective and unexpected
result when the aim of control is to restrict the dynamics", with stated implications for pest and
epidemic management.
→ **Part III.** The named, citable precedent for a simple locally applied control law producing the
opposite of its design intent. **Verifier caveat: the *direction* of the shift (culling raises mean
population), the density-dependence mechanism, and the term "hydra effect" (coined 2009) are NOT in
this abstract — attribute them as body-level or to the adjacent ecology literature.**

**`wackerbauer2007noise`** — R. Wackerbauer, S. Kobayashi, "Noise can delay and advance the collapse
of spatiotemporal chaos", *PRE* 75(6):066209, 2007. DOI 10.1103/PhysRevE.75.066209.
On a ring of excitable Gray–Scott elements: spatially **uniform** noise significantly *decreases* the
average chaos lifetime by enlarging regions of local collapse; spatially **inhomogeneous** noise
maximally delays collapse at intermediate amplitude but drastically advances it at larger amplitude.
(Fully abstract-verified.)
→ **Part III.** Direct precedent for the undirected-noise arm, and an independent instance of a
maintenance input accelerating the collapse it was meant to prevent — with the caution that the
effect is non-monotonic and structure-dependent.

### ENRICHES

**`in1995experimental`** — V. In, S. E. Mahan, W. L. Ditto, M. L. Spano, "Experimental Maintenance of
Chaos", *PRL* 74(22):4420–4423, 1995. DOI 10.1103/PhysRevLett.74.4420. Model-free, return-map-based
maintenance using "small, very infrequently applied time-dependent perturbations of a single system
parameter", demonstrated in a **magnetoelastic ribbon**.
→ **Part III.** Cheap second anchor: chaos maintenance is a demonstrated experimental objective, not
a numerical curiosity. **Verifier: rewrite the gloss to track the abstract (the "approaches the escape
region / past the crisis" phrasing is the standard framing but is not abstract-supported) and name the
magnetoelastic ribbon.**

**`yonker2006nonlocal`** — S. Yonker, R. Wackerbauer, "Nonlocal coupling can prevent the collapse of
spatiotemporal chaos", *PRE* 73(2):026218, 2006. DOI 10.1103/PhysRevE.73.026218. A single nonlocal
shortcut has competing effects (interface formation delays collapse, reduced path length advances it);
**two** shortcuts can prevent collapse outright via asymptotic local collapse.
→ **Part III.** Structural counterpart to our negative: what rescues an extended system from the
absorbing state here is *nonlocality*, not a better local rule. Supports (does not prove) the reading
that our obstruction is a locality obstruction. Pairs with `ha2025absorbing` (lane D).

**`allen1993chaos`** — J. C. Allen, W. M. Schaffer, D. Rosko, "Chaos reduces species extinction by
amplifying local population noise", *Nature* 364(6434):229–232, 1993. DOI 10.1038/364229a0.
"Although low densities lead to more frequent extinction at the local level, the decorrelating effect
of chaotic oscillations reduces the degree of synchrony among populations and thus the likelihood that
all are simultaneously extinguished" (abstract, verbatim).
→ **Part III.** Our boundary-adoption mechanism in another field's vocabulary: any intervention that
*raises* spatial correlation (copying a neighbour is the strongest such) removes the decorrelation
holding the system away from the absorbing state. Also gives the "foam" result a mechanism.
*(Arguably must-cite if the Discussion keeps the boundary-adoption interpretation.)*

**`carro2016noisy`** — A. Carro, R. Toral, M. San Miguel, "The noisy voter model on complex networks",
*Scientific Reports* 6:24775, 2016. DOI 10.1038/srep24775; arXiv:1602.06935. Copying a neighbour drives
consensus (absorbing); undirected noise makes the dynamics ergodic and destroys the absorbing state.
→ **Parts III and I.** Copy-a-neighbour *is* voter dynamics whose absorbing state is freezing; noise is
the standard studied antidote. **Verifier caveat: the "finite-size transition, not a phase transition
in the thermodynamic limit" property is true of the noisy voter model but is NOT in this abstract —
do not cite this abstract for it.** Scope warning as recorded in §1.

**`witthaut2012braess`** — D. Witthaut, M. Timme, "Braess's paradox in oscillator networks,
desynchronization and power outage", *NJP* 14:083036, 2012. DOI 10.1088/1367-2630/14/8/083036.
Adding a link may "not only promote but also destroy synchrony", via geometric frustration in cycle
consistency conditions, generically across topologies.
→ **Part III.** Cleanest general-purpose citation for "a locally reasonable intervention produces the
global failure it was meant to prevent". **Drop first if Related Work is over budget.** (The
"doubling an existing line's capacity" clause is body-level.)

---

## C. Edge of chaos: critiques and modern standing
*(9 items: 6 must-cite, 3 enriches. Verdict: not empty; contains the T1 pre-emption; the
reservoir-computing era did NOT vindicate the hypothesis, which cuts in the paper's favour.)*

Lane C confirms `packard1988adaptation`, `langton1990computation` and `mitchell1993revisiting` are
already in `refs.bib` (the brief understated this) and did not re-surface them. It deliberately
skipped Mitchell/Crutchfield/Hraber arXiv:adap-org/9306003 as a shortened version of the cited paper —
optional second cite if the "re-examination" phrasing is wanted.

### MUST CITE

**`hedlund1969endomorphisms`** — see **T1**. *Mathematical Systems Theory* 3(4):320–375, 1969.
DOI 10.1007/BF01691062. → **Part I. THREATENS.**

**`sakai2002lambdaF`** — S. Sakai, M. Kanno, "A New Parameter F to Classify Cellular Automata Rule
Table Space and a Phase Diagram in λ–F Plane", arXiv:nlin/0211015, 2002 (preprint only).
Class II, III and IV patterns coexist at the *same* λ over the whole range 1/K ≤ λ ≤ 1 − 1/K for
N-neighbour K-state CA; a second parameter F breaks the tie.
→ **Part I.** Cleanest existing statement that λ is many-to-one and least informative exactly around
1/2 — the *generic* version of our degeneracy, against which the structural version is the sharper
claim. Note the paper already cites the sibling `sakai2002edge` (nlin/0204045); this is the one
carrying the explicit coexistence range. **Verifier caveat: the λ–F phase diagram is computed for the
specific 5-neighbour, 4-state case — say so.**

**`vispoel2022classification`** — M. Vispoel, A. J. Daly, J. M. Baetens, "Progress, gaps and obstacles
in the classification of cellular automata", *Physica D* 432:133074, 2022.
DOI 10.1016/j.physd.2021.133074. Splits classification into rule-table schemes (where λ lives) and
space-time-pattern schemes; most schemes confined to ECA; persistent analytic/experimental dichotomy.
→ **Part I / Discussion.** Current-standing citation for "rule-table coordinates underdetermine the
dynamics, and this is a recognised open problem" — and the right anchor for the "broad crossover, no
critical point at tested sizes" phrasing.

**`teuscher2022revisiting`** — C. Teuscher, "Revisiting the edge of chaos: Again?", *BioSystems*
218:104693, 2022. DOI 10.1016/j.biosystems.2022.104693. 30-year review of whether biological and
artificial computation happens at "some sort of edge of chaos", explicitly including the critical voices.
→ **Part I / Discussion.** Lets the paper say "the hypothesis remains contested" with one reference
instead of a paragraph of hedging.

**`carroll2020reservoir`** — T. L. Carroll, "Do reservoir computers work best at the edge of chaos?",
*Chaos* 30(12):121109, 2020. DOI 10.1063/5.0038163; arXiv:2012.01409. Two cases where capacity
*decreases* on approach to the edge — one from breakdown of generalised synchronisation, one from
reservoir/task mismatch; concludes the edge is not in general optimal.
→ **Part I / Discussion.** The reservoir-computing-era negative: the modern computational-optimality
form of the claim has been falsified in its general form.

**`park2023farfrom`** — K. H. Park, F. X. Costa, L. M. Rocha, R. Albert, J. C. Rozum, "Models of Cell
Processes are Far from the Edge of Chaos", *PRX Life* 1(2):023009, 2023. DOI 10.1103/PRXLife.1.023009.
72 experimentally supported Boolean models, new perturbation-spread measures (quasicoherence,
fragility) on GPU (cubewalkers); propagation of internal perturbations is transient in most cases and
phenotypically small when it persists — far more rigid than the criticality hypothesis predicts.
→ **Parts I and II.** Strongest recent empirical negative against the biological claim; its measures
are direct methodological neighbours of the fork-and-flip damage reach. **See §1 weak flag** —
raises the bar for Part II; and their "final Hamming distance" / "stochasticity aids recovery" claims
are body-level, as is their forward-looking "criticality may emerge at larger scales" caveat.

### ENRICHES

**`capobianco2017postsurjectivity`** — S. Capobianco, J. Kari, S. Taati, "Post-surjectivity and
balancedness of cellular automata over groups", *DMTCS* 19(3), Paper No. 4, 2017.
DOI 10.23638/DMTCS-19-3-4; arXiv:1507.02472. Pre-injective + post-surjective ⇒ reversible (post-
surjectivity alone on sofic groups); reversible CA over arbitrary groups are **balanced**.
→ **Part I.** The contemporary, general-setting statement of the same constraint-forces-balance
mechanism as Hedlund — the "and this is still the active formulation" cite for the T1 concession.

**`paturi2025reversibility`** — K. Paturi, "Reversibility, balance and expansivity of non-uniform
cellular automata", arXiv:2507.06896, 2025 (preprint). A bijective NUCA with uniformly recurrent rule
distribution is reversible; a NUCA is balanced if surjective with recurrent rule distribution, or if
bijective.
→ **Part I**, and the bridge to the already-cited `dennunzio2011nonuniform`: our lattice *is* a
non-uniform CA at every instant, so this is the closest published relative of "a structural condition
on a heterogeneous rule assignment forces activity 1/2".

**`roli2018criticality`** — A. Roli, M. Villani, A. Filisetti, R. Serra, "Dynamical Criticality:
Overview and Open Questions", *JSSC* 31(3):647–663, 2018. DOI 10.1007/s11424-017-6117-5;
arXiv:1512.05259. Reviews the criticality hypothesis across cell biology, evolution, neuroscience and
CS; closes with what must be resolved before a solid formulation is possible.
→ **Part I / Discussion.** Sympathetic-but-honest companion to Teuscher: together they establish the
hypothesis is live, unsettled and short of a solid formulation — the context in which a degenerate
coordinate is a contribution rather than a curiosity.

---

## D. Absorbing states and directed percolation
*(13 items: 7 must-cite, 6 enriches. Verdict: dense and mature; contains the single most serious
threat to the paper (T3), a rival testable reading (T8), and the strongest support item in the sweep.)*

### MUST CITE

**`hinrichsen2000nonequilibrium`** — H. Hinrichsen, "Nonequilibrium critical phenomena and phase
transitions into absorbing states", *Advances in Physics* 49(7):815–958, 2000.
DOI 10.1080/00018730050198152; arXiv:cond-mat/0001070.
The standard review: DP in detail, the Janssen–Grassberger conditions (short-range interactions,
unique absorbing state, non-negative one-component order parameter, no extra symmetry or conservation,
no quenched disorder), probabilistic CA, numerics for locating absorbing transitions, damage spreading,
non-DP classes.
→ **Part I.** The canonical citation for calling our freezing transition an absorbing-state transition
at all, and the source for the checklist of DP conditions our system violates (disorder in the rule
field; many absorbing configurations rather than one). *Broad review gloss — the itemised sub-topics
are in the review but not enumerated in its abstract.*

**`vojta2006rare`** and **`hooyberghs2004absorbing`** — see **T3**. *J. Phys. A* 39(22):R143–R205,
2006, DOI 10.1088/0305-4470/39/22/R01; and *PRE* 69(6):066140, 2004, DOI 10.1103/PhysRevE.69.066140
(Letter version *PRL* 90:100601, 2003, independently confirmed). → **Part I. THREATEN.**
*Verifier caveat on Hooyberghs: the exponent values β = (3−√5)/2, ν⊥ = 2 and the DMRG method are
body-level, not in the abstract; the abstract additionally states there is no strong-disorder fixed
point for parity-conserving systems.*

**`vojta2004broadening`** — T. Vojta, "Broadening of a nonequilibrium phase transition by extended
structural defects", *PRE* 70(2):026108, 2004. DOI 10.1103/PhysRevE.70.026108; arXiv:cond-mat/0402606.
Quenched **extended** (spatially correlated) impurities "completely destroy the sharp phase transition
by smearing" — rare strongly-coupled regions order independently of the bulk, so the order parameter
turns on inhomogeneously over a finite window.
→ **Part I. SUPPORTS.** The positive precedent for our reading, with a named mechanism structurally
matching our frozen refuges. Scope caveat: theirs imposed and quenched, ours dynamically generated.

**`noest1986new`** — A. J. Noest, "New universality for spatially disordered cellular automata and
directed percolation", *PRL* 57(1):90–93, 1986. DOI 10.1103/PhysRevLett.57.90. Stochastic CA with
fixed but randomly chosen local probabilities: zero disorder reduces to (D+1)-dimensional DP; finite
spatial disorder is **incompatible** with DP exponents; Monte Carlo in D=1,2 gives new universal
exponents. Companion *PRB* 38(4):2715–2720 (1988) reports power-law (Griffiths-like) relaxation.
→ **Part I.** Earliest clean statement that randomness in a CA's *local rule* moves the absorbing
transition out of the DP class — so for a lattice carrying a spatially varying rule field, clean DP
was never the right null. **Verifier: initials CONFIRMED (Andre J. Noest) — drop the "verify initials"
note the lane file carried.**

**`fates2009asynchronism`** — N. Fatès, "Asynchronism induces second-order phase transitions in
elementary cellular automata", *J. Cellular Automata* 4(1):21–38, 2009. arXiv:nlin/0703044.
Each ECA rule applied with probability α per cell per step; nine ECA rules show a genuine second-order
transition with DP exponents; ECA 178 instead falls in the PC-DP2 (Z₂-symmetric DP) class because 0s
and 1s play symmetric roles. (Verifier read the v2 PDF and confirmed both body-level claims.)
→ **Parts I and III.** The existing absorbing-state result *inside our rule space*: there the tuned
coordinate is an external update parameter and the transition is sharp and DP; here the tuned object is
the rule itself and the transition is broad. The ECA-178 exception is the precedent for "a symmetry of
the rule changes the universality class" — the shape of our λ = 1/2 argument. Use the label **PC-DP2**,
which is more exact than "parity-conservation class". arXiv's journal-ref year (2008) is the stale one.

**`bonachela2009selforganization`** *(= lane E `bonachela2009soqc` — same work, dedupe)* — J. A.
Bonachela, M. A. Muñoz, "Self-organization without conservation: true or just apparent
scale-invariance?", *JSTAT* 2009(09):P09009. DOI 10.1088/1742-5468/2009/09/P09009; arXiv:0905.1799.
Mean field + self-organized branching process + Langevin all conclude non-conserving dynamics does not
produce bona fide criticality — a recharging rate must be fine-tuned; they coin **self-organized
quasi-criticality (SOqC)**.
→ **Parts III and I. SUPPORTS — strongest support item in the whole sweep.** The published counterpart
to our central negative, and the general theoretical reason to *expect* a broad crossover plus a
coexistence no local rule holds.

### ENRICHES

**`vojta2009infinite`** — T. Vojta, A. Farquhar, J. Mast, "Infinite-randomness critical point in the
two-dimensional disordered contact process", *PRE* 79(1):011111, 2009.
DOI 10.1103/PhysRevE.79.011111; arXiv:0810.1569. Times to 10^10, lattices to 8000×8000; activated
(exponential) dynamical scaling, exponents universal in disorder strength.
→ **Part I. THREATENS (numbers attached to T3).** Fixes the order of magnitude at which a disordered
absorbing transition stops looking like a crossover — which is what makes "not resolvable at tested
sizes" defensible rather than weak. *Borderline must-cite if the crossover discussion is expanded.*

**`gross2006epidemic`** — see **T8**. *PRL* 96(20):208701, 2006. DOI 10.1103/PhysRevLett.96.208701;
arXiv:q-bio/0512037. → **Parts I and III. THREATENS (competing reading).** Also the closest published
analogue of Part II's finding that reading the current state field changes the causal structure.
*(Published author form "Carlos J. Dommar D'Lima"; arXiv omits the middle initial.)*

**`vazquez2011temporal`** — F. Vázquez, J. A. Bonachela, C. López, M. A. Muñoz, "Temporal Griffiths
phases", *PRL* 106(23):235702, 2011. DOI 10.1103/PhysRevLett.106.235702; arXiv:1105.3562.
Absorbing-state models with rates fluctuating in **time**: for d ≥ 2, generic power-law spatial scaling
and generic susceptibility divergences over an *extended region*, space and time reversed relative to
ordinary Griffiths phases.
→ **Part I.** A rule field rewritten every step *is* a temporally fluctuating environment — a second
named mechanism for an extended anomalous region rather than a point, plus a diagnostic (do frozen-state
lifetimes scale as a power of L or exponentially?). *That lifetime clause is body-level, not abstract.*
Offers a rival **label**: what we call a featureless crossover may be a Griffiths-like phase with
generic power laws — a stronger, testable claim.

**`grassberger1995damage`** — P. Grassberger, "Are damage spreading transitions generically in the
universality class of directed percolation?", *JSP* 79(1–2):13–23, 1995. DOI 10.1007/BF02179381;
arXiv:cond-mat/9409068. Yes, "unless they coincide with other transitions (as in the Ising model with
Glauber dynamics) and provided the probability for a locally damaged state to become healed is not zero".
→ **Part II.** Places fork-and-flip damage reach in the absorbing-transition framework. **The healing
proviso is the excluded case for us — frozen regions do not heal. Cite carefully.**

**`ha2025absorbing`** — H. Ha, D. A. Huse, R. Samajdar, "Absorbing state transitions with discrete
symmetries", arXiv:2502.08702 (v1 12 Feb 2025; v2 27 Jan 2026), cond-mat.stat-mech. Branching is a
relevant perturbation, ruling out a robust absorbing phase under purely **local** rules; incorporating
**nonlocal** information into the feedback stabilises it and yields a new active–absorbing universality
class.
→ **Part III.** Very recent independent statement of the same locality obstruction, from the opposite
direction, naming the literature's known fix (nonlocality) that our construction deliberately lacks.
**Verifier: the no-robust-absorbing-phase result is for THREE-state (Z₃/S₃) models — the abstract
explicitly says two-state models DO have the well-known absorbing/active transition. Add the qualifier.**
Check for a journal version before final submission.

**`dickman2000paths`** — R. Dickman, M. A. Muñoz, A. Vespignani, S. Zapperi, "Paths to self-organized
criticality", *Braz. J. Phys.* 30(1):27–41, 2000. DOI 10.1590/S0103-97332000000100004;
arXiv:cond-mat/9910454. SOC = an absorbing-state transition plus an imposed supervision (slow drive,
extremal dynamics, or a conserved density).
→ **Part III / memory-search.** The frame requiring any "the system finds its own critical point" story
to name its supervisory channel — which our per-cell optimizing selection rule is not.

---

## E. Self-tuning to criticality: the positive counterpart
*(9 items: 7 must-cite, 2 enriches. Verdict: dense; one genuine near-miss (T4); the literature answers
the brief's "what global information is smuggled in" question explicitly, sometimes in authors' own
words.)*

Three smuggling routes, each with its own citation:
(a) **order-parameter feedback + drive rate tuned to zero** (Sornette et al.);
(b) **rates tuned with system size N** (Pruessner–Peters — a rule that needs N is not local);
(c) **non-conserving systems reach only *apparent* criticality** (Bonachela–Muñoz).

### MUST CITE

**`droste2013analytical`** — see **T4**. *J. R. Soc. Interface* 10(78):20120558, 2013.
DOI 10.1098/rsif.2012.0558; arXiv:1203.4942. → **Part III. THREATENS.** Read this before writing the
Part III Discussion. (Verifier confirmed both quoted strings verbatim in PMC §6, and eq. (6.2) l ≪ g.
Full names: Felix Droste, Anne-Ly Do, Thilo Gross.)

**`bornholdt2000topological`** — S. Bornholdt, T. Rohlf, "Topological Evolution of Dynamical Networks:
Global Criticality from Local Dynamics", *PRL* 84(26):6114–6117, 2000. DOI 10.1103/PhysRevLett.84.6114;
arXiv:cond-mat/0003215. Purely local rewiring — quiet nodes grow links, active nodes lose links — drives
average connectivity to K_c = 2.
→ **Part III.** The canonical positive our negative is measured against; a reviewer names it first. Note
K_c = 2 only in the large-N limit — finite N gives systematic deviation, structurally the same situation
as our finite-size crossover in Part I.

**`bornholdt2003socneural`** — S. Bornholdt, T. Röhl, "Self-organized critical neural networks", *PRE*
67(6):066118, 2003. DOI 10.1103/PhysRevE.67.066118; arXiv:cond-mat/0109256. Correlated neighbours
connect, decorrelated disconnect. Abstract, verbatim: **"network connectivity is regulated locally on the
basis of an order parameter of the global dynamics"** — continuing "which is estimated from an observable
at the single synapse level" (**keep that qualifier if quoting for a locality argument**).
→ **Part III.** Direct answer to the brief's question (1): local in *implementation*, but the input is a
local estimate of a **global order parameter**. The correlation-over-a-window observable is exactly the
class of quantity Part III tests and rejects.

**`bonachela2009selforganization`** — *canonical key; lane E originally filed this as
`bonachela2009soqc`. Same work, one BibTeX entry, alias resolved 2026-08-08.*

**`pruessner2006lessons`** — G. Pruessner, O. Peters, "Self-organized criticality and absorbing states:
Lessons from the Ising model", *PRE* 73(2):025106(R), 2006. DOI 10.1103/PhysRevE.73.025106;
arXiv:cond-mat/0411709. The standard drive/dissipation SOC mechanism is not specific to absorbing states
— it works for *any* continuous transition (demonstrated on **Ising**, which has no absorbing state), and
the FSS exponents depend on how drive/dissipation are tuned **with system size**.
→ **Part III (and Part I's finite-size scan).** The cleanest citation for "published self-tuning
mechanisms smuggle in N", which no cell has access to.

**`sornette1995mapping`** — D. Sornette, A. Johansen, I. Dornic, "Mapping self-organized criticality onto
criticality", *J. Physique I* 5(3):325–335, 1995. **DOI 10.1051/jp1:1995129 (now verified — drop the
lane file's "DOI not verified" note)**; arXiv:adap-org/9411002. SOC as "nothing but the expression,
unfolded in a suitable parameter space, of an underlying unstable dynamical critical point"; tune the
order parameter to a vanishingly small but positive value and the control parameter sits at criticality;
explains the universally slow drive.
→ **Part III.** The canonical statement that the "self" in SOC is a coordinate change, not a free lunch.
Use as the framing sentence for why our negative is the expected outcome once both ingredients (global
order parameter, drive rate → 0) are refused.

**`melby2005noise`** — P. Melby, N. Weber, A. Hübler, "Dynamics of self-adjusting systems with noise",
*Chaos* 15(3):033902, 2005. DOI 10.1063/1.1953147. "Adaptation to the edge of chaos, a feature previously
ascribed to self-adjusting systems, is only a long-lived transient when noise is present"; the
self-adjusting parameter obeys a rescaled diffusion equation; noise produces "chaotic outbreaks" with
power-law distributed lengths. (Fully abstract-verified.)
→ **Part III. SUPPORTS strongly.** Two hooks: (a) the same horizon-dependence we report — which is why
"coexistence **for the full horizon**" is the right phrasing; (b) prior art for undirected noise changing
the *character* of the held state rather than stabilising it — the closest published analogue of the
noise-foam arm.

### ENRICHES

**`melby2000adaptation`** — P. Melby, J. Kaidel, N. Weber, A. Hübler, "Adaptation to the Edge of Chaos in
the Self-Adjusting Logistic Map", *PRL* 84(26):5991–5993, 2000. DOI 10.1103/PhysRevLett.84.5991;
arXiv:nlin/0007006. "The dynamics of these parameters is governed by a **low-pass filtered feedback** from
the dynamical variables of the system" (verbatim); the parameter leaves the chaotic regime and is found
with high probability at the periodicity/chaos boundary.
→ **Parts I and III.** The edge-of-chaos (rather than SOC) phrasing of the positive, and its mechanism is
an **averaging window** — exactly the design our decorrelation-time obstruction says fails when the
observable decorrelates inside the window. Cite as the positive, immediately followed by `melby2005noise`.

**`boettiger2012quantifying`** — C. Boettiger, A. Hastings, "Quantifying limits to detection of early
warning for critical transitions", *J. R. Soc. Interface* 9(75):2527–2539, 2012.
DOI 10.1098/rsif.2012.0125; arXiv:1204.6231. Proposed early-warning indicators "hardly ever characterize
their expected error rates"; a model-based framework shows error rates "can be quite severe for common
indicators even under favorable assumptions". (All verbatim.)
→ **Part III.** Closest *quantitative* counterpart to the decorrelation-time obstruction: a statistic over
a finite window has a detection power, and the standard indicators' power is low. **Honesty caveat: it is
about *forecasting* a transition, not a *controller* holding a state. A good analogy; not a theorem about
local control.**

**Verified, deliberately NOT shortlisted** (in `lane-E.md` with URLs, pull in if the section needs mass):
Rohlf & Bornholdt arXiv:0811.0980 (best single entry point, both rules in one place); Liu & Bassler
*PRE* 74:041910 / cond-mat/0605020 (coevolving Boolean networks → ⟨K⟩ = 2, large finite-size deviations —
closest **substrate** match, **but verify whether topology updates only after the dynamics reaches an
attractor before citing it on that point**); Das & Levina *PRX* 9:021062 / 1808.04196 (relaxed timescale
separation, but drive-during-avalanche not local estimation windows). Also considered and rejected as the
*name* for our obstruction: Touchette & Lloyd, "Information-Theoretic Limits of Control", *PRL* 84:1156
(2000), chao-dyn/9905039 — real, but it bounds control authority *given* information, not information
given a decorrelating observable; same for Ashby requisite variety / Conant–Ashby. **Do not lead with those.**

---

## F. Baldwin effect and heritable acquired modifiers
*(8 items: 5 must-cite, 3 enriches. Verdict: the lane splits in two — the Baldwin canon supplies the
mechanism, the cultural-dissemination physics line supplies the lattice. Contains T5.)*

**Audited negative worth stating in the paper:** there is no canonical "Baldwin effect on a lattice with
an acquired transmissible modifier" paper. Hinton–Nowlan, Mayley, Ackley–Littman, Suzuki–Arita are all
panmictic GA populations. Queries run are listed in `lane-F.md`.

### MUST CITE

**`hinton1987learning`** — G. E. Hinton, S. J. Nowlan, "How Learning Can Guide Evolution", *Complex
Systems* 1(3):495–502, 1987. No DOI (author copy at cs.toronto.edu/~hinton/absps/evolution.htm).
Needle-in-a-haystack landscape smoothed by a lifetime search over undetermined alleles, so a population
that would never find the target by genetic search alone finds it quickly.
→ **Part III.** The reference frame in which "exotype" is legible as an acquired modifier layer coupled to
a slower genotype layer; a reviewer expects it the moment "Baldwin" appears. (*"plastic loci progressively
fixed thereafter" — the paper notes the fixation is incomplete.*)

**`mayley1996landscapes`** — G. Mayley, "Landscapes, Learning Costs, and Genetic Assimilation",
*Evolutionary Computation* 4(3):213–234, 1996. DOI 10.1162/evco.1996.4.3.213. Genetic assimilation requires
a correlation between learned and genetic landscapes **plus an explicit cost to the plastic layer**, which
is what supplies the pressure to hard-code the acquired trait.
→ **Part III.** Our exotype layer carries no explicit cost — precisely Mayley's non-assimilating regime.
The cleanest existing account of why an acquired modifier layer *persists* rather than being absorbed;
contextualises the coexistence outcome. **Verifier: MIT Press restricts the abstract, so the description
rests on secondary sources; "without a cost the plastic layer is retained" is the contrapositive of a
necessary condition and is phrased more strongly than that strictly licenses. Do NOT conflate with
Mayley's separate 1996 "The Evolutionary Cost of Learning".**

**`axelrod1997dissemination`** — R. Axelrod, "The Dissemination of Culture: A Model with Local Convergence
and Global Polarization", *J. Conflict Resolution* 41(2):203–226, 1997. DOI 10.1177/0022002797041002001.
Agents carry F features × q traits; adoption probability proportional to the **fraction of features already
shared** (homophily gate); dynamics runs to frozen absorbing configurations — locally converged domains that
globally never homogenise, their number/size depending on F, q, interaction range and system size.
→ **Part III.** The cleanest citable instance of "an acquired, transmissible modifier layer, carried per-cell
on a lattice and copied from neighbours, produces frozen-vs-changing phase structure", and the lane's answer
to the headline question given that the Baldwin canon is non-spatial.

**`klemm2003global`** — see **T10**. *PRE* 67(4):045101(R), 2003. DOI 10.1103/PhysRevE.67.045101;
arXiv:cond-mat/0205188. → **Part III. PRE-EMPTS the shape of the noise result.** (Fully abstract-verified,
including the d=1 Lyapunov potential and the r vs 1/T criterion.)

**`huberman1993evolutionary`** — see **T5**. *PNAS* 90(16):7716–7718, 1993. DOI 10.1073/pnas.90.16.7716.
→ **Part III. PRE-EMPTS the form of the artefact claim.** Loudest find in the lane.

### ENRICHES

**`nowak1992spatial`** — M. A. Nowak, R. M. May, "Evolutionary games and spatial chaos", *Nature*
359(6398):826–829, 1992. DOI 10.1038/359826a0. Deterministic lattice game, every cell copies the
best-scoring neighbour; depending on payoff and initial condition the lattice freezes into one strategy or
sustains indefinite churn with both coexisting ("evolutionary kaleidoscopes", "dynamic fractals").
→ **Part III.** Precedent that a lattice of cells adopting a neighbour's modifier under an optimising rule
yields exactly our tripartite outcome set, governed by a scalar parameter. **Required context for citing
Huberman & Glance — citing T5 without this is unreadable.**

**`shreesha2023competency`** — L. Shreesha, M. Levin, "Cellular Competency during Development Alters
Evolutionary Dynamics in an Artificial Embryogeny Model", *Entropy* 25(1):131, 2023. DOI 10.3390/e25010131.
Slow genetic layer + fast "competency" layer; competency **masks** the raw genotype, with selection
"favoring improvements to its developmental problem-solving capacities over improvements to its structural
genome".
→ **Part III.** Cleanest recent statement of "a fast acquired modifier layer changes *what* the slow layer
optimises, not merely how fast" — the mechanism our exotype arm instantiates; and the counterweight to a
naive Baldwin reading, since here the plastic layer is *not* assimilated. Pairs with Mayley on
cost/assimilation. **Use the paper's own wording ("favoring… over…"), not "selection stops improving the
structural genome".** (An earlier single-author preprint exists as arXiv:2310.09318 — cite the Entropy paper.)

**`santos2015illusion`** — M. Santos, E. Szathmáry, J. F. Fontanari, "Phenotypic plasticity, the Baldwin
effect, and the speeding up of evolution: The computational roots of an illusion", *JTB* 371:127–136, 2015.
DOI 10.1016/j.jtbi.2015.02.012; arXiv:1411.6843. Standard population genetics cracks the supposedly
unsolvable Hinton–Nowlan landscape without learning; "the Baldwin effect is once again in need of convincing
theoretical foundations".
→ **Part III.** A guard rail: the standing reason not to claim the exotype layer *accelerates* anything.
Contradicts a claim we do not make; citing it is protective.

**Verified, routed elsewhere, not written up** (URLs in `lane-F.md`): Castellano–Marsili–Vespignani *PRL*
85:3536 (2000) / cond-mat/0003111 (the Axelrod order–disorder transition IS a genuine nonequilibrium phase
transition — context for Part I, route to lanes C/D); Carro–Toral–San Miguel (already carried as a lane-B
item); Shrestha et al. arXiv:2406.13383 (heterogeneous life-like CA with per-cell rule genomes inherited
locally during the run — route to lane A); Henrich & Boyd 1998 conformist transmission (right intuition, no
lattice/absorbing-state analysis — not recommended).

---

## G. The Laplacian bridge (dual-use)
*(15 items: 11 must-cite, 4 enriches. Verdict: not empty, but populated across three literatures that do
not cite each other — that disconnection is itself the finding.)*

**Items marked ⇄ DUAL-USE serve BOTH the paper's Related Work and the memory-search write-up.** The rest
are memory-search-only unless stated.

Three bodies: (1) structural-balance dynamics — discrete local edge-sign flips, continuous flows, and
signed-Laplacian spectral theory; (2) adaptive/coevolutionary networks — the modern formal frame for a fast
state process on a slowly self-modifying substrate; (3) the CA↔graph-rewriting bridge (GNA).

### MUST CITE

**`berner2023adaptivenetworks`** ⇄ **DUAL-USE** — R. Berner, T. Gross, C. Kuehn, J. Kurths, S. Yanchuk,
"Adaptive dynamical networks", *Physics Reports* 1031:1–59, 2023. DOI 10.1016/j.physrep.2023.08.001;
arXiv:2304.05652. The current comprehensive review of systems whose connectivity changes as a function of the
dynamical state on it — "their function depends on their structure and vice versa"; explicit
timescale-separation parameter, fast/slow (geometric singular perturbation) reduction, slow-manifold methods.
→ **BOTH.** The single best named frame for the lane's headline question, and the honest frame under which
the MetaCA two-layer structure (fast phenotype on slow genotype) can be described without inventing a bridge.
*(The itemised methods are in the review but not in its abstract.)*

**`gross2008adaptivereview`** ⇄ **DUAL-USE** — T. Gross, B. Blasius, "Adaptive coevolutionary networks: a
review", *J. R. Soc. Interface* 5(20):259–271, 2008. DOI 10.1098/rsif.2007.1229 (**resolves fine — drop the
lane file's "verify before submission" note**); arXiv:0709.1858. Systems combining topological evolution with
node dynamics, unified by complex dynamics + robust topological self-organisation from simple local rules.
→ **BOTH.** Supplies the "dynamics **on** the network vs dynamics **of** the network" vocabulary the paper
needs for its phenotype/genotype split; the citation reviewers expect before any adaptive-network claim.

**`sayama2009gna`** ⇄ **DUAL-USE** — H. Sayama, C. Laramee, "Generative Network Automata: A Generalized
Framework for Modeling Adaptive Network Dynamics Using Graph Rewritings", in Gross & Sayama (eds.),
*Adaptive Networks*, Springer, pp. 311–332, 2009. DOI 10.1007/978-3-642-01284-6_15; arXiv:0901.0216. Graph
rewriting in which "both state transitions and autonomous topology transformations [are] seamlessly
integrated", the system evolving "based on the system's own states and topologies".
→ **BOTH.** The closest existing **formal genus** for "a fast state process on a slowly self-modifying
substrate", hence the most reusable citation in the sweep. **Distinguish explicitly: GNA rewrites topology,
MetaCA rewrites local rules on a fixed lattice — sibling, not superset.**

**`khajehabdollahi2023locally`** ⇄ **DUAL-USE** — *canonical key; lane G originally filed this as
`khajehabdollahi2023locallyadaptiveca`. Same work, one BibTeX entry, alias resolved 2026-08-08.*
See **T2**. → **Part II. THREATENS.**

**`altafini2013antagonistic`** — C. Altafini, "Consensus Problems on Networks With Antagonistic
Interactions", *IEEE TAC* 58(4):935–946, 2013. DOI 10.1109/TAC.2012.2224251. Laplacian consensus on a signed
graph reaches **bipartite consensus** (equal modulus, opposite signs) **iff** the graph is structurally
balanced; otherwise all states converge to zero.
→ **memory-search. PRE-EMPTS originality** of "a joint G-minimum exists iff balanced" in its dynamical form.
Cite, do not re-derive; it also fixes the correct frustrated-case failure mode (collapse to neutrality).
*(The dblp search URL in the lane file is not evidence; the DOI is. Author preprint at sissa.it corroborates.)*

**`atay2020cheegersigned`** — F. M. Atay, S. Liu, "Cheeger constants, structural balance, and spectral
clustering analysis for signed graphs", *Discrete Mathematics* 343(1):111616, 2020.
DOI 10.1016/j.disc.2019.111616; arXiv:1411.3530. Multi-way Cheeger constants with h_k^σ = 0 **iff** k
balanced connected components; higher-order and dual Cheeger inequalities to signed-Laplacian eigenvalues;
extremal eigenvalue estimates via signed-triangle counts; spectral clustering. (Every clause abstract-level;
the abstract also notes these constants unify classical Cheeger constants, bipartiteness measures and
frustration indices — useful if the citation is doing framing work.)
→ **memory-search.** The spectral form of "balanced iff a joint minimum exists", plus the algorithmic layer a
graphical memory search would actually run.

**`antal2005socialbalance`** — T. Antal, P. L. Krapivsky, S. Redner, "Dynamics of social balance on
networks", *PRE* 72(3):036121, 2005. DOI 10.1103/PhysRevE.72.036121; arXiv:cond-mat/0506476. Local triad
dynamics: pick an imbalanced triad (1 or 3 negative links), flip one sign. Infinite network undergoes a
dynamic transition to "paradise" as the friendly-link propensity p passes 1/2; a **finite network always
falls into a balanced absorbing state**.
→ **memory-search (and Part III by analogy).** The canonical prior model of purely local edge updates
rebalancing a signed graph, with absorbing-state structure mirroring our freezing dynamics. **Verifier: the
"jammed configurations" clause for constrained triad dynamics is NOT in this abstract — body/follow-up work.**

**`marvel2009energylandscape`** — S. A. Marvel, S. H. Strogatz, J. M. Kleinberg, "Energy Landscape of Social
Balance", *PRL* 103(19):198701, 2009. DOI 10.1103/PhysRevLett.103.198701; arXiv:0906.2893. Frustration
landscape "dimpled with local minima of widely varying energy"; rigorous bounds and a modular classification;
**jammed states exist for arbitrarily large networks all the way up to the midpoint energy** (Paley-type
construction — verified verbatim in the PDF).
→ **memory-search; SUPPORTS Part III by analogy.** The landscape-theoretic reason a purely local rebalancing
law gets trapped in jammed non-balanced states.

**`marvel2011continuousbalance`** — S. A. Marvel, J. Kleinberg, R. D. Kleinberg, S. H. Strogatz,
"Continuous-time model of structural balance", *PNAS* 108(5):1771–1776, 2011. DOI 10.1073/pnas.1013213108;
arXiv:1010.1814. For the flow dX/dt = X², generic initial conditions admit only two outcomes — all friendly,
or exactly two mutually hostile factions — with a closed-form faction-membership formula.
→ **memory-search.** The smooth-flow counterpart of local triad dynamics; the dichotomy a signed-graph
rebalancing search inherits when its edge law is continuous. **Note the arXiv version is titled "Analysis of a
continuous-time model…" — if citing the preprint, change the title.**

**`cisnerosvelarde2021gradientbalance`** — P. Cisneros-Velarde, N. E. Friedkin, A. V. Proskurnikov, F. Bullo,
"Structural Balance via Gradient Flows Over Signed Graphs", *IEEE TAC* 66(7):3169–3183, 2021.
DOI 10.1109/TAC.2020.3018435; arXiv:1909.11281. Edge-weight dynamics as the **gradient flow of a "dissonance
function"** measuring violations of Heider's axioms; critical points, transients, convergence.
→ **memory-search.** The most direct existing formalisation of "a local edge-update law that descends a
frustration energy on a signed graph" — the frame Joe's rebalancing law should be presented as an instance of,
or a deliberate departure from. *(The one-/two-faction strict-local-minima result is body-level.)*

**`jarman2017adaptiverewiring`** — N. Jarman, E. Steur, C. Trengove, I. Y. Tyukin, C. van Leeuwen,
"Self-organisation of small-world networks by adaptive rewiring in response to graph diffusion", *Scientific
Reports* 7:13158, 2017. DOI 10.1038/s41598-017-12589-9. Heat-kernel diffusion on the graph drives local edge
rewiring — shortcuts where diffusion is intense, pruning where it is low; the diffusion rate selects
modular vs centralised topology, with a hierarchical regime at the transition.
→ **memory-search.** Closest published mechanism to Joe's construction — *the process running on the graph
decides which edges get updated* — and its transition result is a template for what a rebalancing search
should target. The graph is **unsigned**: that is exactly the open extension. **Verifier: soften "always
emerges for any nonzero diffusion rate" to "robustly leads to small-world structure from random initial
graphs" — the stronger claim is not in the abstract.**

### ENRICHES

**`hara2026dmfthopfieldplasticity`** ⇄ **DUAL-USE** — Y. Hara, Y. Kabashima, "DMFT analysis of Hopfield
network with plasticity", arXiv:2605.22254, 2026 (v2 5 June 2026; no journal ref). DMFT of a fully connected
Hopfield network where neural states and synaptic couplings coevolve during retrieval: moderate plasticity
enlarges basins via positive delayed feedback, while **excessively strong plasticity makes the network
imprint the imperfect initial cue itself, producing spurious attractors and degrading retrieval**; an optimal
plasticity strength emerges. (The verifier treated this 2026 id as likely-fabricated and checked it directly
against the arXiv Atom API — it is real, and every quoted clause is in the abstract.)
→ **BOTH. STRONGEST EXTERNAL CORROBORATION of the Part III artefact claim**, analytically tractable and
independent: too-faithful adoption of the current signal collapses the system onto that signal. For the
memory search it is a direct constraint on the rebalancing gain. **Promote to must-cite if Part III's artefact
claim is foregrounded.**

**`angel2014errwlocalization`** — O. Angel, N. Crawford, G. Kozma, "Localization for linearly edge-reinforced
random walks", *Duke Math. J.* 163(5):889–921, 2014. DOI 10.1215/00127094-2644357; arXiv:1203.4010. The
linearly edge-reinforced random walk is recurrent on any bounded-degree graph for sufficiently small initial
weights and transient on non-amenable graphs for large initial weights — a genuine
localization/delocalization transition, proved via a mixture-of-reversible-Markov-chains representation
rather than the magic formula.
→ **memory-search (must-cite for that write-up specifically).** The rigorous probability-theory instance of "a
search process that rewrites the weights it walks on", and a warning with a theorem attached: small initial
weights ⇒ the search localizes (freezes onto a small subgraph) — a proved version of our measured freezing
failure mode. *(Published title hyphenates "edge-reinforced"; the abstract also covers vertex-reinforced jump
processes.)*

**`tian2024spreadingsigned`** — Y. Tian, R. Lambiotte, "Spreading and Structural Balance on Signed Networks",
*SIAM J. Applied Dynamical Systems* 23(1):50–80, 2024. DOI 10.1137/22M1542325; arXiv:2212.10158. Classifies
signed networks as balanced / antibalanced / strictly unbalanced, characterises each spectrally (the signed
spectral radius is smaller than the unsigned one iff strictly unbalanced), and relates the classes to
spreading dynamics.
→ **memory-search.** What a diffusion/search process actually does *on* a signed graph as a function of its
balance type — the fast-layer half of the two-layer model. **Signs are static: one of the four near-misses
that bound the gap.** *(The classification is delivered spectrally; "from cycle properties" is the framing,
and the linear/nonlinear spreading claim is body-level.)*

**`yoon2026signedkuramoto`** — J. Yoon, C. Kuehn, "Stability of Phase-Locked States in Signed Kuramoto
Networks: Structure versus Adaptation", arXiv:2602.11981, 2026 (12 Feb 2026; no journal ref). Static signed
structure "imposes severe constraints on the stability of phase-locked configurations", while adaptive
coupling "organize[s] and delineate[s] their robustness when stability is permitted".
→ **memory-search.** The only source found posing the lane's headline question in exactly the
"structure vs adaptation" form; answers that the **plastic layer, not the signed structure, buys robustness**.
**Verifier: the result is for two canonical classes of static signed networks, not signed networks in general.**

---

## 4. Lanes that came up empty or thin

**No lane came up empty.** Every lane reported a populated literature. The *sub-questions* that came up
empty are the audited negatives listed at the end of §1 — those are the citable gaps, and each lane file
carries the full query list so the negative is auditable. The thin spots, honestly:

- **Lane E, "named counterpart to the decorrelation-time obstruction": PARTIAL, no canonical theorem.**
  There is no "local-controller timescale bound" in the literature. Closest *named* framing is
  Droste–Do–Gross's "limited local accessibility of the global phase"; closest *quantitative* counterpart is
  the early-warning detection-power literature (Boettiger–Hastings). Touchette–Lloyd and Ashby/Conant–Ashby
  were checked and rejected as the wrong shape (they bound authority given information, not information
  given a decorrelating observable). **Operator decision: name the obstruction ourselves, or borrow Droste's
  phrase.**
- **Lane F, the Baldwin half:** thin by construction — the canon is non-spatial and there is no lattice
  Baldwin paper. The lattice half had to be answered from the cultural-dissemination physics line.
- **Lane G, the bridge:** the specific composite (signed edges locally rebalanced *while* a search runs) has
  **no** source. Four near-misses bound the hole; that is a defensible novelty statement for the
  memory-search write-up, with those four as the citations that establish it. Separately, **nothing connects
  structural-balance dynamics to rule-rewriting CA** — the analogy must be carried by the
  adaptive-dynamical-networks frame + GNA, as an analogy under a named frame, not as a cited theorem.

## 5. Rejected in verification — WARNING LABEL, NOT A SUGGESTION LIST

**Zero (0) items failed the existence check.** All 74 proposed items resolved to real works; one
(`rollier2024taxonomy`) had wrong journal coordinates and was corrected rather than dropped (the Elsevier
DOI string contains "2024" — that is internal numbering, not the issue year; the article is
**vol. 140, 2025**). Nothing in the BibTeX block below is unverified.

**Separately: items the lanes searched, found plausible, and REFUSED to recommend because no landing page
could be loaded, or because citing them would be a liability. These must NOT be cited without manual
verification. They are recorded so the gaps are auditable, not so they get pasted in.**

- **Glover, Lind, Yazidi, Osipov & Nichele**, two ECA-reservoir papers (*Complex Systems* 32(3), 2023;
  ALIFE 2021, doi:10.1162/isal_a_00440). Ideal lane-C material. complex-systems.com returned a TLS
  certificate error, direct.mit.edu returned 403. **Worthwhile manual follow-up for Joe.**
- **Kanoh & Wu**, GA-evolved rule-changing CA (lane A). Real, but no landing page loadable — omitted rather
  than padded.
- **Coevolving signed appraisal networks** (Automatica paper, lane G) — surfaced, landing page 403'd,
  NOT recommended.
- **Fulbright, "Where is the Edge of Chaos?"** (arXiv:2304.07176, 2023) — loadable and real, but unrefereed,
  with a "critical value ≈ 1/e" claim that would invite an argument the paper does not need.
  **Deliberately not recommended.**
- **Jensen, infinitely-many-absorbing-states** (cond-mat/9405012) — real; deliberately excluded because it
  concludes DP is *generic* for many-absorbing-state models, which **weakens** any "many frozen
  configurations ⇒ non-DP" argument. Recorded so nobody re-derives that temptation.
- **Brain-criticality literature** (e.g. *Trends Neurosci.* 2022), **monitored-quantum-circuit absorbing
  states**, **Henrich & Boyd 1998** — all real, all judged dilutive/off-domain.
- **Abbott–Austerweil–Griffiths** semantic-network memory search as random walk (lane G) — surfaced, judged
  too weak to recommend.

## 6. Verification caveats — wording corrections to apply before these sentences enter the paper

Bibliographic data is verified for all 74. These are **description-level** caveats raised by the verifier
(body-level claims presented as abstract-level, or wording stronger than the source licenses):

| key | correction |
|---|---|
| `khajehabdollahi2023locally` | abstract says the coupling keeps the model "in the **vicinity** of the critical temperature" — not "reaches and holds" |
| `sipper1996coevolving` | the 0.93–0.94 / 0.83 density-classification figures are NOT in this paper's abstract; one secondary source gives different numbers. Verify against the body |
| `tomita2007selfdescription` | the hand-coding/evolutionary/exhaustive-search triple is a line-of-work claim, not this abstract |
| `rollier2024taxonomy` | vol. **140**, year **2025**; the "predetermined assignments not modification" claim is body-level |
| `yang1995preserving` | "an originating paper", not "the founding paper"; no abstract distributed — mechanism description is secondary |
| `in1995experimental` | rewrite gloss to track the abstract (model-free return-map method, infrequent single-parameter perturbations, **magnetoelastic ribbon**) |
| `hilker2006paradox` | direction of the shift, the density-dependence mechanism, and "hydra effect" are all outside this abstract |
| `carro2016noisy` | the finite-size / not-a-thermodynamic-limit-transition property is not in this abstract |
| `witthaut2012braess` | "doubling an existing line's capacity" is body-level |
| `sakai2002lambdaF` | the λ–F phase diagram is for the 5-neighbour, 4-state case specifically |
| `park2023farfrom` | "final Hamming distance" and "stochasticity/desync increase recovery" are body-level; do not drop their "criticality may emerge at larger scales via module coupling" caveat |
| `hooyberghs2004absorbing` | exponent values and DMRG are body-level; abstract adds: no strong-disorder fixed point for parity-conserving systems |
| `ha2025absorbing` | the no-robust-absorbing-phase result is for **three-state (Z₃/S₃)** models; two-state models *do* have the standard transition |
| `vazquez2011temporal` | the lifetime-scaling clause is body-level; DOI 10.1103/PhysRevLett.106.235702 added |
| `noest1986new` | initials **confirmed** (Andre J. Noest) — delete the "verify initials" note |
| `fates2009asynchronism` | the class is **PC-DP2** (Z₂-symmetric DP); arXiv's journal-ref year (2008) is the stale one, use 2009 |
| `gross2006epidemic` | "bistability / separate invasion and persistence thresholds" is the standard reading of the hysteresis result, slightly beyond the abstract |
| `gross2008adaptivereview` | DOI **does** resolve — delete the "verify before submission" note; Crossref stamps 2007 (online-first), cite 2008 |
| `sornette1995mapping` | journal DOI **now verified**: 10.1051/jp1:1995129 — delete the caveat note |
| `bornholdt2003socneural` | if quoting the "regulated locally on the basis of an order parameter of the global dynamics" line, keep the continuation "which is estimated from an observable at the single synapse level" — it is the load-bearing qualifier |
| `mayley1996landscapes` | abstract not readable; "without a cost the plastic layer is retained" is stronger than a necessary condition licenses; do not conflate with Mayley's "The Evolutionary Cost of Learning" |
| `shreesha2023competency` | use "favoring improvements to competency over the structural genome", not "selection stops improving the structural genome" |
| `axelrod1997dissemination` | adoption probability is proportional to the **fraction of features already shared** |
| `antal2005socialbalance` | "jammed configurations" for CTD is not in this abstract |
| `cisnerosvelarde2021gradientbalance` | one-/two-faction strict-local-minima convergence is body-level; publisher capitalises "Over" |
| `jarman2017adaptiverewiring` | soften "always … for any nonzero diffusion rate" |
| `tian2024spreadingsigned` | classification is delivered spectrally; the linear/nonlinear spreading claim is body-level |
| `yoon2026signedkuramoto` | result is for two canonical classes of static signed networks |
| `marvel2011continuousbalance` | arXiv preprint title differs ("Analysis of a continuous-time model…") |
| `angel2014errwlocalization` | published title hyphenates "edge-reinforced"; abstract also covers vertex-reinforced jump processes |
| `hinrichsen2000nonequilibrium` | broad review gloss; the itemised sub-topics are in the review, not its abstract |
| `berner2023adaptivenetworks` | the itemised methods (ε, GSP, slow manifolds) are in the review, not its abstract |
| `sayama2009gna` | "explicitly positioned as the generalisation of CA" rests on the framework's stated generality, not an explicit CA sentence in the abstract |
| `capobianco2017postsurjectivity` | journal DOI 10.23638/DMTCS-19-3-4 added (was missing) |
| `dickman2000paths` | journal DOI 10.1590/S0103-97332000000100004 added (was missing) |

---

## 7. BibTeX — all recommended entries (confirmed + corrected), verifier-corrected forms

Keys match those used above. **Two dedupe decisions before compiling:** the Khajehabdollahi entry is emitted
once under `khajehabdollahi2023locally` (lane G used `khajehabdollahi2023locallyadaptiveca` — alias or
rename); the Bonachela–Muñoz entry is emitted once under `bonachela2009selforganization` (lane E used
`bonachela2009soqc` — same). 72 entries.

```bibtex
%% ---------- A. Rule-rewriting operators as first-class objects ----------
@article{cattaneo1997transformations,
  author  = {Cattaneo, Gianpiero and Formenti, Enrico and Margara, Luciano and Mauri, Giancarlo},
  title   = {Transformations of the one-dimensional cellular automata rule space},
  journal = {Parallel Computing},
  volume  = {23},
  number  = {11},
  pages   = {1593--1611},
  year    = {1997},
  doi     = {10.1016/S0167-8191(97)00076-8}
}

@inproceedings{khajehabdollahi2023locally,
  author    = {Khajehabdollahi, Sina and Giannakakis, Emmanouil and Buend{\'\i}a, Victor and Martius, Georg and Levina, Anna},
  title     = {Locally adaptive cellular automata for goal-oriented self-organization},
  booktitle = {ALIFE 2023: Ghost in the Machine: Proceedings of the 2023 Artificial Life Conference},
  publisher = {MIT Press},
  year      = {2023},
  pages     = {59},
  doi       = {10.1162/isal_a_00663},
  note      = {arXiv:2306.07067; arXiv DOI 10.48550/arXiv.2306.07067. Lane G key: khajehabdollahi2023locallyadaptiveca}
}

@article{rollier2024taxonomy,
  author  = {Rollier, Michiel and Zielinski, Kallil M. C. and Daly, Aisling J. and Bruno, Odemir M. and Baetens, Jan M.},
  title   = {A comprehensive taxonomy of cellular automata},
  journal = {Communications in Nonlinear Science and Numerical Simulation},
  volume  = {140},
  pages   = {108362},
  year    = {2025},
  doi     = {10.1016/j.cnsns.2024.108362},
  note    = {arXiv:2401.08408 (2024)}
}

@article{sipper1996coevolving,
  author  = {Sipper, Moshe},
  title   = {Co-evolving non-uniform cellular automata to perform computations},
  journal = {Physica D: Nonlinear Phenomena},
  volume  = {92},
  number  = {3--4},
  pages   = {193--208},
  year    = {1996},
  doi     = {10.1016/0167-2789(95)00286-3}
}

@article{ilachinski1987structurally,
  author  = {Ilachinski, Andrew and Halpern, Paul},
  title   = {Structurally dynamic cellular automata},
  journal = {Complex Systems},
  volume  = {1},
  number  = {3},
  pages   = {503--527},
  year    = {1987}
}

@article{tomita2007selfdescription,
  author  = {Tomita, Kohji and Murata, Satoshi and Kurokawa, Haruhisa},
  title   = {Self-description for construction and computation on graph-rewriting automata},
  journal = {Artificial Life},
  volume  = {13},
  number  = {4},
  pages   = {383--396},
  year    = {2007},
  doi     = {10.1162/artl.2007.13.4.383}
}

@article{lipackard1990structure,
  author  = {Li, Wentian and Packard, Norman},
  title   = {The structure of the elementary cellular automata rule space},
  journal = {Complex Systems},
  volume  = {4},
  number  = {3},
  pages   = {281--297},
  year    = {1990}
}

@article{miszczak2023ruleswitching,
  author  = {Miszczak, Jaros{\l}aw Adam},
  title   = {Rule switching mechanisms in the {Game of Life} with synchronous and asynchronous updating policy},
  journal = {Physica Scripta},
  volume  = {98},
  number  = {11},
  pages   = {115210},
  year    = {2023},
  doi     = {10.1088/1402-4896/acfc6c},
  note    = {arXiv:2310.05979}
}

%% ---------- B. Control of chaos, and its inverse ----------
@article{yang1995preserving,
  author  = {Yang, Weiming and Ding, Mingzhou and Mandell, Arnold J. and Ott, Edward},
  title   = {Preserving chaos: Control strategies to preserve complex dynamics with potential relevance to biological disorders},
  journal = {Physical Review E},
  volume  = {51},
  number  = {1},
  pages   = {102--110},
  year    = {1995},
  doi     = {10.1103/PhysRevE.51.102}
}

@article{in1995experimental,
  author  = {In, Visarath and Mahan, Susan E. and Ditto, William L. and Spano, Mark L.},
  title   = {Experimental Maintenance of Chaos},
  journal = {Physical Review Letters},
  volume  = {74},
  number  = {22},
  pages   = {4420--4423},
  year    = {1995},
  doi     = {10.1103/PhysRevLett.74.4420}
}

@article{grigoriev1997pinning,
  author  = {Grigoriev, Roman O. and Cross, Michael C. and Schuster, Heinz G.},
  title   = {Pinning Control of Spatiotemporal Chaos},
  journal = {Physical Review Letters},
  volume  = {79},
  number  = {15},
  pages   = {2795--2798},
  year    = {1997},
  doi     = {10.1103/PhysRevLett.79.2795},
  eprint  = {chao-dyn/9705001},
  archivePrefix = {arXiv}
}

@article{bagnoli2012control,
  author  = {Bagnoli, Franco and El Yacoubi, Samira and Rechtman, Ra{\'u}l},
  title   = {Control of cellular automata},
  journal = {Physical Review E},
  volume  = {86},
  number  = {6},
  pages   = {066201},
  year    = {2012},
  doi     = {10.1103/PhysRevE.86.066201},
  eprint  = {1206.2237},
  archivePrefix = {arXiv}
}

@article{bagnoli2018boundary,
  author  = {Bagnoli, Franco and El Yacoubi, Samira and Rechtman, Ra{\'u}l},
  title   = {Toward a boundary regional control problem for Boolean cellular automata},
  journal = {Natural Computing},
  volume  = {17},
  number  = {3},
  pages   = {479--486},
  year    = {2018},
  doi     = {10.1007/s11047-017-9626-1},
  eprint  = {1606.05122},
  archivePrefix = {arXiv}
}

@article{sieber2014controlling,
  author  = {Sieber, Jan and Omel'chenko, Oleh E. and Wolfrum, Matthias},
  title   = {Controlling Unstable Chaos: Stabilizing Chimera States by Feedback},
  journal = {Physical Review Letters},
  volume  = {112},
  number  = {5},
  pages   = {054102},
  year    = {2014},
  doi     = {10.1103/PhysRevLett.112.054102},
  eprint  = {1310.7560},
  archivePrefix = {arXiv}
}

@article{hilker2006paradox,
  author  = {Hilker, Frank M. and Westerhoff, Frank H.},
  title   = {Paradox of simple limiter control},
  journal = {Physical Review E},
  volume  = {73},
  number  = {5},
  pages   = {052901},
  year    = {2006},
  doi     = {10.1103/PhysRevE.73.052901}
}

@article{wackerbauer2007noise,
  author  = {Wackerbauer, Renate and Kobayashi, Sumire},
  title   = {Noise can delay and advance the collapse of spatiotemporal chaos},
  journal = {Physical Review E},
  volume  = {75},
  number  = {6},
  pages   = {066209},
  year    = {2007},
  doi     = {10.1103/PhysRevE.75.066209}
}

@article{yonker2006nonlocal,
  author  = {Yonker, Safia and Wackerbauer, Renate},
  title   = {Nonlocal coupling can prevent the collapse of spatiotemporal chaos},
  journal = {Physical Review E},
  volume  = {73},
  number  = {2},
  pages   = {026218},
  year    = {2006},
  doi     = {10.1103/PhysRevE.73.026218}
}

@article{allen1993chaos,
  author  = {Allen, J. C. and Schaffer, W. M. and Rosko, D.},
  title   = {Chaos reduces species extinction by amplifying local population noise},
  journal = {Nature},
  volume  = {364},
  number  = {6434},
  pages   = {229--232},
  year    = {1993},
  doi     = {10.1038/364229a0}
}

@article{carro2016noisy,
  author  = {Carro, Adri{\'a}n and Toral, Ra{\'u}l and San Miguel, Maxi},
  title   = {The noisy voter model on complex networks},
  journal = {Scientific Reports},
  volume  = {6},
  pages   = {24775},
  year    = {2016},
  doi     = {10.1038/srep24775},
  eprint  = {1602.06935},
  archivePrefix = {arXiv}
}

@article{witthaut2012braess,
  author  = {Witthaut, Dirk and Timme, Marc},
  title   = {Braess's paradox in oscillator networks, desynchronization and power outage},
  journal = {New Journal of Physics},
  volume  = {14},
  pages   = {083036},
  year    = {2012},
  doi     = {10.1088/1367-2630/14/8/083036}
}

%% ---------- C. Edge of chaos: critiques and modern standing ----------
@article{hedlund1969endomorphisms,
  author  = {Hedlund, Gustav A.},
  title   = {Endomorphisms and Automorphisms of the Shift Dynamical System},
  journal = {Mathematical Systems Theory},
  volume  = {3},
  number  = {4},
  pages   = {320--375},
  year    = {1969},
  doi     = {10.1007/BF01691062}
}

@misc{sakai2002lambdaF,
  author = {Sakai, Sunao and Kanno, Megumi},
  title  = {A New Parameter {$F$} to Classify Cellular Automata Rule Table Space and a Phase Diagram in {$\lambda$--$F$} Plane},
  year   = {2002},
  eprint = {nlin/0211015},
  archivePrefix = {arXiv},
  primaryClass  = {nlin.CG},
  url    = {https://arxiv.org/abs/nlin/0211015}
}

@article{vispoel2022classification,
  author  = {Vispoel, Milan and Daly, Aisling J. and Baetens, Jan M.},
  title   = {Progress, gaps and obstacles in the classification of cellular automata},
  journal = {Physica D: Nonlinear Phenomena},
  volume  = {432},
  pages   = {133074},
  year    = {2022},
  doi     = {10.1016/j.physd.2021.133074}
}

@article{teuscher2022revisiting,
  author  = {Teuscher, Christof},
  title   = {Revisiting the Edge of Chaos: Again?},
  journal = {BioSystems},
  volume  = {218},
  pages   = {104693},
  year    = {2022},
  doi     = {10.1016/j.biosystems.2022.104693}
}

@article{carroll2020reservoir,
  author  = {Carroll, Thomas L.},
  title   = {Do reservoir computers work best at the edge of chaos?},
  journal = {Chaos: An Interdisciplinary Journal of Nonlinear Science},
  volume  = {30},
  number  = {12},
  pages   = {121109},
  year    = {2020},
  doi     = {10.1063/5.0038163},
  eprint  = {2012.01409},
  archivePrefix = {arXiv}
}

@article{park2023farfrom,
  author  = {Park, Kyu Hyong and Costa, Felipe Xavier and Rocha, Luis M. and Albert, R{\'e}ka and Rozum, Jordan C.},
  title   = {Models of Cell Processes Are Far from the Edge of Chaos},
  journal = {PRX Life},
  volume  = {1},
  number  = {2},
  pages   = {023009},
  year    = {2023},
  doi     = {10.1103/PRXLife.1.023009}
}

@article{capobianco2017postsurjectivity,
  author  = {Capobianco, Silvio and Kari, Jarkko and Taati, Siamak},
  title   = {Post-Surjectivity and Balancedness of Cellular Automata over Groups},
  journal = {Discrete Mathematics \& Theoretical Computer Science},
  volume  = {19},
  number  = {3},
  pages   = {Paper No. 4},
  year    = {2017},
  doi     = {10.23638/DMTCS-19-3-4},
  eprint  = {1507.02472},
  archivePrefix = {arXiv},
  primaryClass  = {math.DS}
}

@misc{paturi2025reversibility,
  author = {Paturi, Katariina},
  title  = {Reversibility, Balance and Expansivity of Non-Uniform Cellular Automata},
  year   = {2025},
  eprint = {2507.06896},
  archivePrefix = {arXiv},
  primaryClass  = {math.DS},
  url    = {https://arxiv.org/abs/2507.06896}
}

@article{roli2018criticality,
  author  = {Roli, Andrea and Villani, Marco and Filisetti, Alessandro and Serra, Roberto},
  title   = {Dynamical Criticality: Overview and Open Questions},
  journal = {Journal of Systems Science and Complexity},
  volume  = {31},
  number  = {3},
  pages   = {647--663},
  year    = {2018},
  doi     = {10.1007/s11424-017-6117-5},
  eprint  = {1512.05259},
  archivePrefix = {arXiv}
}

%% ---------- D. Absorbing states and directed percolation ----------
@article{hinrichsen2000nonequilibrium,
  author  = {Hinrichsen, Haye},
  title   = {Nonequilibrium critical phenomena and phase transitions into absorbing states},
  journal = {Advances in Physics},
  volume  = {49},
  number  = {7},
  pages   = {815--958},
  year    = {2000},
  doi     = {10.1080/00018730050198152},
  eprint  = {cond-mat/0001070},
  archivePrefix = {arXiv}
}

@article{vojta2006rare,
  author  = {Vojta, Thomas},
  title   = {Rare region effects at classical, quantum and nonequilibrium phase transitions},
  journal = {Journal of Physics A: Mathematical and General},
  volume  = {39},
  number  = {22},
  pages   = {R143--R205},
  year    = {2006},
  doi     = {10.1088/0305-4470/39/22/R01},
  eprint  = {cond-mat/0602312},
  archivePrefix = {arXiv}
}

@article{hooyberghs2004absorbing,
  author  = {Hooyberghs, Jef and Igl{\'o}i, Ferenc and Vanderzande, Carlo},
  title   = {Absorbing state phase transitions with quenched disorder},
  journal = {Physical Review E},
  volume  = {69},
  number  = {6},
  pages   = {066140},
  year    = {2004},
  doi     = {10.1103/PhysRevE.69.066140},
  eprint  = {cond-mat/0402086},
  archivePrefix = {arXiv}
}

@article{vojta2004broadening,
  author  = {Vojta, Thomas},
  title   = {Broadening of a nonequilibrium phase transition by extended structural defects},
  journal = {Physical Review E},
  volume  = {70},
  number  = {2},
  pages   = {026108},
  year    = {2004},
  doi     = {10.1103/PhysRevE.70.026108},
  eprint  = {cond-mat/0402606},
  archivePrefix = {arXiv}
}

@article{noest1986new,
  author  = {Noest, Andre J.},
  title   = {New universality for spatially disordered cellular automata and directed percolation},
  journal = {Physical Review Letters},
  volume  = {57},
  number  = {1},
  pages   = {90--93},
  year    = {1986},
  doi     = {10.1103/PhysRevLett.57.90}
}

@article{fates2009asynchronism,
  author  = {Fat{\`e}s, Nazim},
  title   = {Asynchronism induces second-order phase transitions in elementary cellular automata},
  journal = {Journal of Cellular Automata},
  volume  = {4},
  number  = {1},
  pages   = {21--38},
  year    = {2009},
  eprint  = {nlin/0703044},
  archivePrefix = {arXiv}
}

@article{vojta2009infinite,
  author  = {Vojta, Thomas and Farquhar, Adam and Mast, Jason},
  title   = {Infinite-randomness critical point in the two-dimensional disordered contact process},
  journal = {Physical Review E},
  volume  = {79},
  number  = {1},
  pages   = {011111},
  year    = {2009},
  doi     = {10.1103/PhysRevE.79.011111},
  eprint  = {0810.1569},
  archivePrefix = {arXiv}
}

@article{gross2006epidemic,
  author  = {Gross, Thilo and D'Lima, Carlos J. Dommar and Blasius, Bernd},
  title   = {Epidemic dynamics on an adaptive network},
  journal = {Physical Review Letters},
  volume  = {96},
  number  = {20},
  pages   = {208701},
  year    = {2006},
  doi     = {10.1103/PhysRevLett.96.208701},
  eprint  = {q-bio/0512037},
  archivePrefix = {arXiv}
}

%% NOTE: same work as lane E's `bonachela2009soqc`. Use ONE key.
@article{bonachela2009selforganization,
  author  = {Bonachela, Juan A. and Mu{\~n}oz, Miguel A.},
  title   = {Self-organization without conservation: true or just apparent scale-invariance?},
  journal = {Journal of Statistical Mechanics: Theory and Experiment},
  volume  = {2009},
  number  = {09},
  pages   = {P09009},
  year    = {2009},
  doi     = {10.1088/1742-5468/2009/09/P09009},
  eprint  = {0905.1799},
  archivePrefix = {arXiv}
}

@article{vazquez2011temporal,
  author  = {V{\'a}zquez, Federico and Bonachela, Juan A. and L{\'o}pez, Crist{\'o}bal and Mu{\~n}oz, Miguel A.},
  title   = {Temporal {G}riffiths phases},
  journal = {Physical Review Letters},
  volume  = {106},
  number  = {23},
  pages   = {235702},
  year    = {2011},
  doi     = {10.1103/PhysRevLett.106.235702},
  eprint  = {1105.3562},
  archivePrefix = {arXiv}
}

@article{grassberger1995damage,
  author  = {Grassberger, Peter},
  title   = {Are damage spreading transitions generically in the universality class of directed percolation?},
  journal = {Journal of Statistical Physics},
  volume  = {79},
  number  = {1--2},
  pages   = {13--23},
  year    = {1995},
  doi     = {10.1007/BF02179381},
  eprint  = {cond-mat/9409068},
  archivePrefix = {arXiv}
}

@misc{ha2025absorbing,
  author        = {Ha, Hyunsoo and Huse, David A. and Samajdar, Rhine},
  title         = {Absorbing state transitions with discrete symmetries},
  year          = {2025},
  eprint        = {2502.08702},
  archivePrefix = {arXiv},
  primaryClass  = {cond-mat.stat-mech},
  note          = {Preprint; v2 27 January 2026}
}

@article{dickman2000paths,
  author  = {Dickman, Ronald and Mu{\~n}oz, Miguel A. and Vespignani, Alessandro and Zapperi, Stefano},
  title   = {Paths to self-organized criticality},
  journal = {Brazilian Journal of Physics},
  volume  = {30},
  number  = {1},
  pages   = {27--41},
  year    = {2000},
  doi     = {10.1590/S0103-97332000000100004},
  eprint  = {cond-mat/9910454},
  archivePrefix = {arXiv}
}

%% ---------- E. Self-tuning to criticality ----------
@article{droste2013analytical,
  author  = {Droste, Felix and Do, Anne-Ly and Gross, Thilo},
  title   = {Analytical investigation of self-organized criticality in neural networks},
  journal = {Journal of the Royal Society Interface},
  volume  = {10},
  number  = {78},
  pages   = {20120558},
  year    = {2013},
  doi     = {10.1098/rsif.2012.0558},
  eprint  = {1203.4942},
  archivePrefix = {arXiv}
}

@article{bornholdt2000topological,
  author  = {Bornholdt, Stefan and Rohlf, Thimo},
  title   = {Topological Evolution of Dynamical Networks: Global Criticality from Local Dynamics},
  journal = {Physical Review Letters},
  volume  = {84},
  number  = {26},
  pages   = {6114--6117},
  year    = {2000},
  doi     = {10.1103/PhysRevLett.84.6114},
  eprint  = {cond-mat/0003215},
  archivePrefix = {arXiv}
}

@article{bornholdt2003socneural,
  author  = {Bornholdt, Stefan and R{\"o}hl, Torsten},
  title   = {Self-organized critical neural networks},
  journal = {Physical Review E},
  volume  = {67},
  number  = {6},
  pages   = {066118},
  year    = {2003},
  doi     = {10.1103/PhysRevE.67.066118},
  eprint  = {cond-mat/0109256},
  archivePrefix = {arXiv}
}

@article{pruessner2006lessons,
  author  = {Pruessner, Gunnar and Peters, Ole},
  title   = {Self-organized criticality and absorbing states: Lessons from the {I}sing model},
  journal = {Physical Review E},
  volume  = {73},
  number  = {2},
  pages   = {025106},
  year    = {2006},
  doi     = {10.1103/PhysRevE.73.025106},
  eprint  = {cond-mat/0411709},
  archivePrefix = {arXiv},
  note    = {Rapid Communication, 025106(R)}
}

@article{sornette1995mapping,
  author  = {Sornette, Didier and Johansen, Anders and Dornic, Ivan},
  title   = {Mapping self-organized criticality onto criticality},
  journal = {Journal de Physique I},
  volume  = {5},
  number  = {3},
  pages   = {325--335},
  year    = {1995},
  doi     = {10.1051/jp1:1995129},
  eprint  = {adap-org/9411002},
  archivePrefix = {arXiv}
}

@article{melby2005noise,
  author  = {Melby, Paul and Weber, Nicholas and H{\"u}bler, Alfred},
  title   = {Dynamics of self-adjusting systems with noise},
  journal = {Chaos: An Interdisciplinary Journal of Nonlinear Science},
  volume  = {15},
  number  = {3},
  pages   = {033902},
  year    = {2005},
  doi     = {10.1063/1.1953147}
}

@article{melby2000adaptation,
  author  = {Melby, Paul and Kaidel, J{\"o}rg and Weber, Nicholas and H{\"u}bler, Alfred},
  title   = {Adaptation to the Edge of Chaos in the Self-Adjusting Logistic Map},
  journal = {Physical Review Letters},
  volume  = {84},
  number  = {26},
  pages   = {5991--5993},
  year    = {2000},
  doi     = {10.1103/PhysRevLett.84.5991},
  eprint  = {nlin/0007006},
  archivePrefix = {arXiv}
}

@article{boettiger2012quantifying,
  author  = {Boettiger, Carl and Hastings, Alan},
  title   = {Quantifying limits to detection of early warning for critical transitions},
  journal = {Journal of the Royal Society Interface},
  volume  = {9},
  number  = {75},
  pages   = {2527--2539},
  year    = {2012},
  doi     = {10.1098/rsif.2012.0125},
  eprint  = {1204.6231},
  archivePrefix = {arXiv}
}

%% ---------- F. Baldwin effect and heritable acquired modifiers ----------
@article{hinton1987learning,
  author  = {Hinton, Geoffrey E. and Nowlan, Steven J.},
  title   = {How Learning Can Guide Evolution},
  journal = {Complex Systems},
  volume  = {1},
  number  = {3},
  pages   = {495--502},
  year    = {1987},
  note    = {No DOI; author copy at \url{https://www.cs.toronto.edu/~hinton/absps/evolution.htm}}
}

@article{mayley1996landscapes,
  author  = {Mayley, Giles},
  title   = {Landscapes, Learning Costs, and Genetic Assimilation},
  journal = {Evolutionary Computation},
  volume  = {4},
  number  = {3},
  pages   = {213--234},
  year    = {1996},
  doi     = {10.1162/evco.1996.4.3.213}
}

@article{axelrod1997dissemination,
  author  = {Axelrod, Robert},
  title   = {The Dissemination of Culture: A Model with Local Convergence and Global Polarization},
  journal = {Journal of Conflict Resolution},
  volume  = {41},
  number  = {2},
  pages   = {203--226},
  year    = {1997},
  doi     = {10.1177/0022002797041002001}
}

@article{klemm2003global,
  author  = {Klemm, Konstantin and Egu{\'\i}luz, V{\'\i}ctor M. and Toral, Ra{\'u}l and San Miguel, Maxi},
  title   = {Global Culture: A Noise-Induced Transition in Finite Systems},
  journal = {Physical Review E},
  volume  = {67},
  number  = {4},
  pages   = {045101(R)},
  year    = {2003},
  doi     = {10.1103/PhysRevE.67.045101},
  eprint  = {cond-mat/0205188},
  archivePrefix = {arXiv}
}

@article{huberman1993evolutionary,
  author  = {Huberman, Bernardo A. and Glance, Natalie S.},
  title   = {Evolutionary Games and Computer Simulations},
  journal = {Proceedings of the National Academy of Sciences USA},
  volume  = {90},
  number  = {16},
  pages   = {7716--7718},
  year    = {1993},
  doi     = {10.1073/pnas.90.16.7716}
}

@article{nowak1992spatial,
  author  = {Nowak, Martin A. and May, Robert M.},
  title   = {Evolutionary Games and Spatial Chaos},
  journal = {Nature},
  volume  = {359},
  number  = {6398},
  pages   = {826--829},
  year    = {1992},
  doi     = {10.1038/359826a0}
}

@article{shreesha2023competency,
  author  = {Shreesha, Lakshwin and Levin, Michael},
  title   = {Cellular Competency during Development Alters Evolutionary Dynamics in an Artificial Embryogeny Model},
  journal = {Entropy},
  volume  = {25},
  number  = {1},
  pages   = {131},
  year    = {2023},
  doi     = {10.3390/e25010131}
}

@article{santos2015illusion,
  author  = {Santos, Mauro and Szathm{\'a}ry, E{\"o}rs and Fontanari, Jos{\'e} F.},
  title   = {Phenotypic plasticity, the {Baldwin} effect, and the speeding up of evolution: The computational roots of an illusion},
  journal = {Journal of Theoretical Biology},
  volume  = {371},
  pages   = {127--136},
  year    = {2015},
  doi     = {10.1016/j.jtbi.2015.02.012},
  eprint  = {1411.6843},
  archivePrefix = {arXiv}
}

%% ---------- G. The Laplacian bridge (dual-use) ----------
@article{berner2023adaptivenetworks,
  author  = {Berner, Rico and Gross, Thilo and Kuehn, Christian and Kurths, J{\"u}rgen and Yanchuk, Serhiy},
  title   = {Adaptive dynamical networks},
  journal = {Physics Reports},
  volume  = {1031},
  pages   = {1--59},
  year    = {2023},
  doi     = {10.1016/j.physrep.2023.08.001},
  eprint  = {2304.05652},
  archivePrefix = {arXiv}
}

@article{gross2008adaptivereview,
  author  = {Gross, Thilo and Blasius, Bernd},
  title   = {Adaptive coevolutionary networks: a review},
  journal = {Journal of the Royal Society Interface},
  volume  = {5},
  number  = {20},
  pages   = {259--271},
  year    = {2008},
  doi     = {10.1098/rsif.2007.1229},
  eprint  = {0709.1858},
  archivePrefix = {arXiv}
}

@incollection{sayama2009gna,
  author    = {Sayama, Hiroki and Laramee, Craig},
  title     = {Generative Network Automata: A Generalized Framework for Modeling Adaptive Network Dynamics Using Graph Rewritings},
  booktitle = {Adaptive Networks: Theory, Models and Applications},
  editor    = {Gross, Thilo and Sayama, Hiroki},
  series    = {Understanding Complex Systems / NECSI Studies on Complexity},
  publisher = {Springer},
  pages     = {311--332},
  year      = {2009},
  doi       = {10.1007/978-3-642-01284-6_15},
  eprint    = {0901.0216},
  archivePrefix = {arXiv}
}

@article{altafini2013antagonistic,
  author  = {Altafini, Claudio},
  title   = {Consensus Problems on Networks With Antagonistic Interactions},
  journal = {IEEE Transactions on Automatic Control},
  volume  = {58},
  number  = {4},
  pages   = {935--946},
  year    = {2013},
  doi     = {10.1109/TAC.2012.2224251}
}

@article{atay2020cheegersigned,
  author  = {Atay, Fatihcan M. and Liu, Shiping},
  title   = {Cheeger constants, structural balance, and spectral clustering analysis for signed graphs},
  journal = {Discrete Mathematics},
  volume  = {343},
  number  = {1},
  pages   = {111616},
  year    = {2020},
  doi     = {10.1016/j.disc.2019.111616},
  eprint  = {1411.3530},
  archivePrefix = {arXiv}
}

@article{antal2005socialbalance,
  author  = {Antal, T. and Krapivsky, P. L. and Redner, S.},
  title   = {Dynamics of social balance on networks},
  journal = {Physical Review E},
  volume  = {72},
  number  = {3},
  pages   = {036121},
  year    = {2005},
  doi     = {10.1103/PhysRevE.72.036121},
  eprint  = {cond-mat/0506476},
  archivePrefix = {arXiv}
}

@article{marvel2009energylandscape,
  author  = {Marvel, Seth A. and Strogatz, Steven H. and Kleinberg, Jon M.},
  title   = {Energy Landscape of Social Balance},
  journal = {Physical Review Letters},
  volume  = {103},
  number  = {19},
  pages   = {198701},
  year    = {2009},
  doi     = {10.1103/PhysRevLett.103.198701},
  eprint  = {0906.2893},
  archivePrefix = {arXiv}
}

@article{marvel2011continuousbalance,
  author  = {Marvel, Seth A. and Kleinberg, Jon and Kleinberg, Robert D. and Strogatz, Steven H.},
  title   = {Continuous-time model of structural balance},
  journal = {Proceedings of the National Academy of Sciences},
  volume  = {108},
  number  = {5},
  pages   = {1771--1776},
  year    = {2011},
  doi     = {10.1073/pnas.1013213108},
  eprint  = {1010.1814},
  archivePrefix = {arXiv}
}

@article{cisnerosvelarde2021gradientbalance,
  author  = {Cisneros-Velarde, Pedro and Friedkin, Noah E. and Proskurnikov, Anton V. and Bullo, Francesco},
  title   = {Structural Balance via Gradient Flows Over Signed Graphs},
  journal = {IEEE Transactions on Automatic Control},
  volume  = {66},
  number  = {7},
  pages   = {3169--3183},
  year    = {2021},
  doi     = {10.1109/TAC.2020.3018435},
  eprint  = {1909.11281},
  archivePrefix = {arXiv}
}

@article{jarman2017adaptiverewiring,
  author  = {Jarman, Nicholas and Steur, Erik and Trengove, Chris and Tyukin, Ivan Y. and van Leeuwen, Cees},
  title   = {Self-organisation of small-world networks by adaptive rewiring in response to graph diffusion},
  journal = {Scientific Reports},
  volume  = {7},
  pages   = {13158},
  year    = {2017},
  doi     = {10.1038/s41598-017-12589-9}
}

@misc{hara2026dmfthopfieldplasticity,
  author        = {Hara, Yoshinori and Kabashima, Yoshiyuki},
  title         = {{DMFT} analysis of {H}opfield network with plasticity},
  year          = {2026},
  eprint        = {2605.22254},
  archivePrefix = {arXiv},
  note          = {Preprint; v2 5 June 2026}
}

@article{angel2014errwlocalization,
  author  = {Angel, Omer and Crawford, Nicholas and Kozma, Gady},
  title   = {Localization for linearly edge-reinforced random walks},
  journal = {Duke Mathematical Journal},
  volume  = {163},
  number  = {5},
  pages   = {889--921},
  year    = {2014},
  doi     = {10.1215/00127094-2644357},
  eprint  = {1203.4010},
  archivePrefix = {arXiv}
}

@article{tian2024spreadingsigned,
  author  = {Tian, Yu and Lambiotte, Renaud},
  title   = {Spreading and Structural Balance on Signed Networks},
  journal = {SIAM Journal on Applied Dynamical Systems},
  volume  = {23},
  number  = {1},
  pages   = {50--80},
  year    = {2024},
  doi     = {10.1137/22M1542325},
  eprint  = {2212.10158},
  archivePrefix = {arXiv}
}

@misc{yoon2026signedkuramoto,
  author        = {Yoon, Jaeyoung and Kuehn, Christian},
  title         = {Stability of Phase-Locked States in Signed {K}uramoto Networks: Structure versus Adaptation},
  year          = {2026},
  eprint        = {2602.11981},
  archivePrefix = {arXiv}
}
```
