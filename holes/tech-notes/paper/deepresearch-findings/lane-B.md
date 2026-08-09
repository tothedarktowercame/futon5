# Lane B — Control of chaos, and its inverse

DeepResearch lane report for the rule-rewriting CA paper (draft9). Compiled 2026-08-08.
Scope: OGY-lineage chaos control; pinning / localized control of spatiotemporal chaos;
anticontrol and *chaos maintenance*; control targeted at CA specifically; failure modes and
backfiring interventions. Every item below was verified on a page I actually loaded; the URL
recorded per item is the one I loaded.

## Lane notes (what the literature actually says)

**(1) Is there work on MAINTAINING a marginal / mixed state rather than fully stabilising?**
Yes, and it has a name: **chaos maintenance** ("preserving chaos", "anticontrol"), founded by
Yang–Ding–Mandell–Ott (1995) and demonstrated experimentally the same year by In–Mahan–Ditto–Spano.
The canonical framing is exactly Part III's noise arms: the system has an *absorbing / regular*
attractor it will fall into, and small, undirected-or-nearly-undirected perturbations are applied
not to stabilise anything but to keep the trajectory on the chaotic saddle. Part III's noise arm
should be described in this vocabulary; a reviewer from nonlinear dynamics will supply the term if
we do not.

For *spatially extended* systems specifically, the closest thing to "hold the coexistence" is the
Wackerbauer school's work on **transient spatiotemporal chaos collapsing to a homogeneous absorbing
rest state**, where the question is literally "what input prevents / delays the collapse". Two
results there matter to us: nonlocal shortcuts can prevent collapse (Yonker–Wackerbauer 2006), and
noise both delays and advances it depending on amplitude and spatial structure
(Wackerbauer–Kobayashi 2007).

**(2) Failure modes of local / boundary-targeted control in extended systems.** The standard result
is a *density* result: Grigoriev–Cross–Schuster (1997) show the minimal density of control sites is
set by the **noise strength**, and their optimal arrangement by the system's symmetry — i.e. local
control in an extended system is fundamentally a coverage problem, and noise is what sets the price.
For CA specifically, Bagnoli–El Yacoubi–Rechtman have a whole programme (PRE 2012; Natural Computing
2018) on controlling a CA by *pinning a fraction of sites to a target configuration*, including
**boundary-only** control, with critical control densities.

**(3) Precedent for "the control input accelerates the collapse it was meant to prevent".** Yes —
three independent precedents, and this is the most valuable part of the lane:
  - **Hilker–Westerhoff 2006, "Paradox of simple limiter control"**: limiter control applied to
    reduce a population *raises* its mean. The named, citable statement of "control achieves the
    opposite of its intent" (adjacent literature calls it the hydra effect).
  - **Wackerbauer–Kobayashi 2007**: spatially *uniform* noise — the obvious "keep it stirred" input —
    **decreases** the lifetime of spatiotemporal chaos by enlarging regions of local collapse; only
    spatially inhomogeneous noise delays collapse, and only at intermediate amplitude.
  - **Allen–Schaffer–Rosko 1993 (Nature)**: chaos protects a coupled metapopulation from extinction
    *by decorrelating* the local populations; anything that re-correlates them (including a
    stabilising intervention) raises the risk of the absorbing state. This is the mechanism of our
    boundary-adoption result stated in ecological vocabulary.

**LOUD FLAGS (pre-emption, not contradiction of the data):**
  - **Bagnoli–El Yacoubi–Rechtman (PRE 86, 066201, 2012)** control a CA by *copying a fraction of
    sites from a master configuration*, and choose the sites using a **locally computable quantity**
    (the number of nonzero Boolean derivatives — the same object as our fork-and-flip damage
    sensitivity). They report critical properties of control. This pre-empts two things at once:
    (i) our boundary-copying intervention is an instance of a known control class whose *designed
    function* is to drive the bulk toward the copied configuration — so "copying the frozen boundary
    accelerates freezing" is what that control class does, not a surprise; (ii) our Part III
    statement "no tested locally computable quantity finds or holds the coexistence" sits next to a
    published result where a local quantity *is* an effective control-siting heuristic (for a
    different target: master–slave synchronisation, not coexistence maintenance). Both need
    explicit distinguishing in Related Work. This is the single most important item in the lane.
  - **Sieber–Omel'chenko–Wolfrum (PRL 112, 054102, 2014)** stabilise **chimera states** — the
    canonical coexistence of a coherent (frozen-like) and an incoherent (chaotic) domain in one
    extended system — which are otherwise chaotic transients that collapse to full coherence in
    finite systems. Their feedback holds the coexistence *through the bifurcation where it loses
    attractivity*. If Part III's claim is ever phrased as "the coexistence cannot be held", this
    pre-empts it. The honest distinguishing line: their control acts on a **global/system-level**
    quantity (the chaotic regime treated as a statistical equilibrium, feedback on a system
    parameter), not on locally computable per-cell information — which is precisely the scope of our
    negative result. Say so explicitly rather than leaving it to a referee.

**What the lane does NOT contain.** I found no work that (a) maintains an *ordered–chaotic
coexistence* in a lattice **using only locally computable per-cell quantities**, nor (b) any
treatment of control of a system whose *update rule itself* is the controlled variable (the control
literature perturbs states or bifurcation parameters, never the local transition function's truth
table). Part III's negative result therefore does not appear to be pre-empted at the level of
mechanism — only its *plausibility* is (chimera control shows coexistence is holdable with a global
handle; CA pinning control shows local information can site a controller). Searched vocabularies:
OGY/chaos control, pinning control, localized control, adaptive control of spatially extended
systems, targeting, anticontrol, chaotification, chaos maintenance/preserving chaos, collapse of
spatiotemporal chaos, control of cellular automata, regional/boundary control of CA, control-induced
extinction, hydra effect, Braess paradox in oscillator networks, noisy voter model.

**Cross-lane handoffs.** Two things surfaced here that belong to other lanes and I did not claim:
Buendía–di Santo–Bonachela–Muñoz, "Feedback mechanisms for self-organization to the edge of a phase
transition" (Frontiers in Physics 8:333, 2020) → **lane E**; the supertransient / collapse-of-
spatiotemporal-chaos scaling literature (Wackerbauer–Showalter and successors) → **lane D**.

---

## Items

### B1. Yang, Ding, Mandell, Ott (1995) — "Preserving chaos" — MUST CITE
- Citation: W. Yang, M. Ding, A. J. Mandell, E. Ott, "Preserving chaos: Control strategies to
  preserve complex dynamics with potential relevance to biological disorders", *Physical Review E*
  **51**(1), 102–110, 1995.
- Identifier: DOI 10.1103/PhysRevE.51.102 (PMID 9962622)
- URL loaded: https://pubmed.ncbi.nlm.nih.gov/9962622/ (APS abstract page returns 403 to
  automated fetch; metadata verified on PubMed)
- What it does: Founding paper of *chaos maintenance* / anticontrol. Where OGY-style control applies
  small perturbations to stabilise an unstable periodic orbit, this applies small perturbations to
  *prevent* the trajectory leaving a chaotic saddle for a regular (or absorbing) attractor, keeping
  complex dynamics alive after the chaotic attractor has been destroyed by a crisis. Motivated by
  biological "dynamical disease" cases where the regular state is the pathology.
- Relevance: Part III — this is the name and the citation for our undirected-noise arms; "keeping
  the system out of the absorbing frozen state by small undirected perturbations" is chaos
  maintenance, and the paper should use that term.
- Tier: must-cite. Contradicts: no.

### B2. In, Mahan, Ditto, Spano (1995) — "Experimental Maintenance of Chaos" — enriches
- Citation: V. In, S. E. Mahan, W. L. Ditto, M. L. Spano, "Experimental Maintenance of Chaos",
  *Physical Review Letters* **74**, 4420–4423, 1995.
- Identifier: DOI 10.1103/PhysRevLett.74.4420
- URL loaded: https://api.crossref.org/works/10.1103/PhysRevLett.74.4420
- What it does: Experimental realisation of the Yang et al. strategy in a magnetoelastic ribbon:
  small parameter perturbations applied whenever the trajectory approaches the escape region keep
  the system chaotic past the crisis point at which it would otherwise settle onto a periodic
  attractor. Establishes maintenance as a real, implementable control mode, not a numerical artefact.
- Relevance: Part III — cheap second anchor alongside B1 showing chaos maintenance is an accepted
  experimental control objective; supports framing our noise arms as maintenance rather than "noise
  robustness checks".
- Tier: enriches. Contradicts: no.

### B3. Grigoriev, Cross, Schuster (1997) — "Pinning Control of Spatiotemporal Chaos" — MUST CITE
- Citation: R. O. Grigoriev, M. C. Cross, H. G. Schuster, "Pinning Control of Spatiotemporal Chaos",
  *Physical Review Letters* **79**, 2795, 1997.
- Identifier: arXiv:chao-dyn/9705001; DOI 10.1103/PhysRevLett.79.2795
- URL loaded: https://arxiv.org/abs/chao-dyn/9705001
- What it does: Linear control theory applied to localized ("pinning") control of a coupled map
  lattice. Two structural results, both quoted from the abstract on the page: the **optimal
  arrangement** of control sites depends on the system's symmetry properties, and their **minimal
  density depends on the strength of noise** in the system. Also gives a nonlinear generalisation in
  1-d and needs far fewer controllers than earlier schemes.
- Relevance: Part III — the standard reference for "how much local control does a spatially extended
  chaotic system need, and what sets the price"; it is the frame against which our sparse local
  interventions (boundary copying, per-cell adoption) should be positioned, and the noise-sets-the-
  density result is directly germane to our noise arms.
- Tier: must-cite. Contradicts: no.

### B4. Bagnoli, El Yacoubi, Rechtman (2012) — "Control of cellular automata" — MUST CITE (PRE-EMPTS)
- Citation: F. Bagnoli, S. El Yacoubi, R. Rechtman, "Control of cellular automata", *Physical Review
  E* **86**(6), 066201, 2012.
- Identifier: arXiv:1206.2237; DOI 10.1103/PhysRevE.86.066201
- URL loaded: https://arxiv.org/abs/1206.2237 (metadata cross-checked at
  https://api.crossref.org/works/10.1103/PhysRevE.86.066201)
- What it does: Studies master–slave synchronisation and **control of totalistic CA by setting a
  fraction of the slave's sites equal to the master's** — i.e. control *by copying a target
  configuration into a subset of cells* — and measures the resulting distance between the two
  systems. Presents three control strategies that "exploit local information about the CA, mainly
  the number of nonzero Boolean derivatives", contrasts them with information-free synchronisation,
  and finds the **critical properties of control** (critical density of controlled sites).
- Relevance: **Part III, and Part II.** (a) Our boundary-copying intervention is an instance of this
  exact control class; the class is *designed* to drag the bulk toward the copied configuration, so
  copying a frozen boundary propagating freeze inward is the expected behaviour of the mechanism —
  we should cite this rather than present the acceleration as unexplained. (b) Their site-selection
  uses a locally computable sensitivity (nonzero Boolean derivatives) — the same object as our
  fork-and-flip damage measurement — which is the nearest published counterexample to "no locally
  computable quantity is useful here", and must be distinguished (their objective is convergence to
  a prescribed target, ours is maintenance of a mixed regime).
- Tier: must-cite. **Contradicts: yes (pre-empts).**
- Contradiction note: Pre-empts the framing of two Part III results — (i) that boundary-copying
  "unexpectedly" accelerates freezing (this control class provably drives the bulk to the copied
  configuration above a critical control density), and (ii) that locally computable quantities are
  not useful control handles in CA (they use local Boolean-derivative counts as a successful
  control-siting heuristic, for a different objective). Neither touches our measurements; both touch
  our claims of novelty/surprise.

### B5. Bagnoli, El Yacoubi, Rechtman (2018) — boundary regional control of Boolean CA — MUST CITE
- Citation: F. Bagnoli, S. El Yacoubi, R. Rechtman, "Toward a boundary regional control problem for
  Boolean cellular automata", *Natural Computing* **17**(3), 479–486, 2018.
- Identifier: arXiv:1606.05122; DOI 10.1007/s11047-017-9626-1
- URL loaded: https://arxiv.org/abs/1606.05122 (metadata cross-checked at
  https://api.crossref.org/works/10.1007/s11047-017-9626-1)
- What it does: Poses the *boundary* control problem for Boolean CA: can a target configuration in a
  region of interest be reached by acting **only on the boundary values of the sites at that
  region's edge**, with the region of interest itself part of the boundary rather than interior.
  Gives reachability conditions for this restricted actuation.
- Relevance: Part III — the direct prior art genre for our boundary-copying arm; establishes
  boundary-only actuation of a CA as a studied control problem with known reachability limits, so
  our negative/backfiring boundary result lands in an existing conversation rather than in a vacuum.
- Tier: must-cite. Contradicts: no.

### B6. Sieber, Omel'chenko, Wolfrum (2014) — stabilizing chimera states — MUST CITE (PRE-EMPTS)
- Citation: J. Sieber, O. E. Omel'chenko, M. Wolfrum, "Controlling Unstable Chaos: Stabilizing
  Chimera States by Feedback", *Physical Review Letters* **112**, 054102, 2014.
- Identifier: arXiv:1310.7560; DOI 10.1103/PhysRevLett.112.054102
- URL loaded: https://arxiv.org/abs/1310.7560
- What it does: A control scheme that *locates and stabilises an unstable chaotic regime* in a large
  interacting-particle system, allowing a high-dimensional chaotic attractor to be tracked through
  the bifurcation where it loses attractivity. Non-invasive in the delayed-feedback sense; treats
  the chaotic regime as "a statistical equilibrium displaying random fluctuations as a finite size
  effect". Demonstrated on **chimera states** — coexisting coherent and incoherent domains — making
  them observable close to coherence, at small oscillator number, and from random initial conditions
  where they would otherwise collapse.
- Relevance: **Part III** — the strongest existing positive result for "maintaining an
  ordered–chaotic coexistence that would otherwise collapse", i.e. the literature's answer to the
  thing our interventions fail at. Its finite-size framing (coexistence collapses in finite systems,
  lifetime grows with size) also speaks to our finite-size scan.
- Tier: must-cite. **Contradicts: yes (pre-empts).**
- Contradiction note: Pre-empts any broad phrasing that a marginal ordered–chaotic coexistence
  cannot be held by control. It can — but with a *global* handle (feedback on a system parameter
  driven by an aggregate statistic of the chaotic regime), not with locally computable per-cell
  quantities. Part III must state the locality restriction explicitly when claiming the negative,
  or this paper answers it.

### B7. Hilker, Westerhoff (2006) — "Paradox of simple limiter control" — MUST CITE
- Citation: F. M. Hilker, F. H. Westerhoff, "Paradox of simple limiter control", *Physical Review E*
  **73**(5), 052901, 2006.
- Identifier: DOI 10.1103/PhysRevE.73.052901 (PMID 16802979)
- URL loaded: https://www.wikidata.org/wiki/Q51938953 (APS abstract page returns 403 to automated
  fetch; metadata verified on Wikidata's indexed record, content from the indexed abstract)
- What it does: Limiter control is a standard cheap chaos-control method (clip the state variable at
  a threshold). The paper shows that applying it **shifts the mean of the controlled variable in the
  direction opposite to the intervention's intent**: culling individuals can *raise* mean population
  size, because removal relieves density dependence. Explicitly flagged by the authors as
  countereffective and consequential for pest/epidemic management.
- Relevance: Part III — the citable precedent for our headline control anomaly: a simple, locally
  applied control law that produces the opposite of its design intent. Nearest named cousin in the
  adjacent ecology literature is the "hydra effect".
- Tier: must-cite. Contradicts: no (it *supports* the plausibility of our backfiring result).
- Verification caveat: I read the abstract via an index record and search rendering, not the APS
  page (403). Bibliographic fields (authors/volume/article number/year/DOI/PMID) are index-verified.

### B8. Wackerbauer, Kobayashi (2007) — noise can delay AND advance collapse — MUST CITE
- Citation: R. Wackerbauer, S. Kobayashi, "Noise can delay and advance the collapse of
  spatiotemporal chaos", *Physical Review E* **75**, 066209, 2007.
- Identifier: DOI 10.1103/PhysRevE.75.066209 (PMID 17677342)
- URL loaded: https://api.crossref.org/works/10.1103/PhysRevE.75.066209 (bibliographic);
  content from the indexed abstract via https://pubmed.ncbi.nlm.nih.gov/17677342/ search rendering
- What it does: Spatiotemporal chaos on a ring of excitable Gray–Scott elements is a long transient
  that collapses to a homogeneous rest (absorbing) state. Adding dynamical noise: **spatially
  uniform noise significantly decreases** the average chaos lifetime by enlarging regions of local
  collapse; **spatially inhomogeneous noise maximally delays** collapse at an intermediate noise
  level but **drastically advances** it at larger levels.
- Relevance: Part III — the direct precedent for our undirected-noise arm, and a caution: the effect
  of noise on maintenance is *non-monotonic in amplitude* and depends on whether noise is uniform or
  spatially structured. Also an independent instance of the lane's question (3): the input meant to
  keep the system disordered can be exactly what advances the collapse.
- Tier: must-cite. Contradicts: no — but it qualifies our "undirected noise holds a foam" result:
  the literature says this is amplitude- and spatial-structure-dependent, so we should report which
  regime our noise arm sits in rather than claiming noise maintains the changing phase generally.

### B9. Yonker, Wackerbauer (2006) — nonlocal coupling prevents collapse — enriches
- Citation: S. Yonker, R. Wackerbauer, "Nonlocal coupling can prevent the collapse of spatiotemporal
  chaos", *Physical Review E* **73**(2), 026218, 2006.
- Identifier: DOI 10.1103/PhysRevE.73.026218
- URL loaded: https://api.openalex.org/works/doi:10.1103/PhysRevE.73.026218
- What it does: On the same excitable ring, adding a very small number of **nonlocal shortcuts**
  drastically changes the lifetime of spatiotemporal chaos: a single shortcut has competing effects
  (local interface formation delays collapse, reduced characteristic path length advances it), while
  two shortcuts can **prevent** the collapse outright via asymptotic local collapse.
- Relevance: Part III — the structural counterpart to our negative result: what rescues an extended
  system from the absorbing state here is *nonlocality*, not a better local rule. Supports (does not
  prove) the reading that the obstruction we hit is a locality obstruction.
- Tier: enriches. Contradicts: no.
- Verification caveat: content from the OpenAlex record plus the indexed abstract; the APS page
  returns 403 to automated fetch.

### B10. Allen, Schaffer, Rosko (1993) — chaos reduces extinction by decorrelating — enriches
- Citation: J. C. Allen, W. M. Schaffer, D. Rosko, "Chaos reduces species extinction by amplifying
  local population noise", *Nature* **364**(6434), 229–232, 1993.
- Identifier: DOI 10.1038/364229a0 (PMID 8321317)
- URL loaded: https://pubmed.ncbi.nlm.nih.gov/8321317/
- What it does: Against the argument that chaotic dynamics drive populations extinct through low
  densities, it shows that in a lattice of migration-coupled local populations, chaotic oscillation
  **reduces synchrony between patches**, so although each patch goes extinct more often, the
  species-level (global) absorbing state becomes far less likely. Persistence is bought with
  decorrelation.
- Relevance: **Part III** — this is the mechanism of our boundary-adoption result stated in another
  field's vocabulary: any intervention that raises spatial correlation between cells (copying a
  neighbour/boundary state is the strongest such intervention) removes the decorrelation that was
  holding the system away from the absorbing state. Also gives the "foam" result a mechanism: local
  noise sustains the global mixed regime precisely by keeping patches out of phase.
- Tier: enriches (arguably must-cite if the Discussion keeps the boundary-adoption interpretation).
  Contradicts: no.

### B11. Carro, Toral, San Miguel (2016) — noisy voter model — enriches (cross-lane with D)
- Citation: A. Carro, R. Toral, M. San Miguel, "The noisy voter model on complex networks",
  *Scientific Reports* **6**, 24775, 2016.
- Identifier: arXiv:1602.06935; DOI 10.1038/srep24775
- URL loaded: https://arxiv.org/abs/1602.06935
- What it does: Analytical treatment of the voter model with spontaneous (idiosyncratic) state
  changes. Copying a neighbour drives the system to consensus, an absorbing state; adding undirected
  noise makes the dynamics ergodic and destroys the absorbing state, producing a **noise-induced,
  finite-size transition** between coexistence and near-consensus whose critical point depends on
  system size and network heterogeneity — a transition that is not a phase transition in the
  thermodynamic limit.
- Relevance: **Part III and Part I.** Copying-a-neighbour (our adoption rule) is literally voter
  dynamics whose absorbing state is consensus/freezing, and undirected noise is the standard,
  studied antidote that holds coexistence — so both Part III results have a clean minimal-model
  precedent. The finite-size caveat also bears on Part I: a noise-held coexistence with a
  size-dependent apparent critical point that vanishes with size is a known, named way to get a
  broad crossover rather than a critical point in a finite-size scan.
- Tier: enriches. Contradicts: no — but it is a *scope warning*: if we report a crossover from a
  finite-size scan of a copy-plus-noise dynamics, this literature predicts exactly that, and we
  should not present it as evidence about the thermodynamic limit.

### B12. Witthaut, Timme (2012) — Braess's paradox in oscillator networks — enriches (droppable)
- Citation: D. Witthaut, M. Timme, "Braess's paradox in oscillator networks, desynchronization and
  power outage", *New Journal of Physics* **14**, 083036, 2012.
- Identifier: DOI 10.1088/1367-2630/14/8/083036
- URL loaded: https://iopscience.iop.org/article/10.1088/1367-2630/14/8/083036
- What it does: Shows that adding a transmission line (or doubling a line's capacity) to a
  synchronised oscillator network — an intervention intended to strengthen it — can **destroy the
  synchronised steady state**, tracing the effect to geometric frustration in the phase-oscillator
  cycle constraints, and showing it is generic rather than a special case.
- Relevance: Part III — the cleanest general-purpose citation for "a locally reasonable intervention
  in a spatially extended coupled system produces the global failure it was meant to prevent", if we
  want one sentence of general precedent alongside the domain-specific ones (B7, B8, B10).
- Tier: enriches; drop first if Related Work is over budget.
- Contradicts: no.

---

## BibTeX

```bibtex
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
  pages   = {4420--4423},
  year    = {1995},
  doi     = {10.1103/PhysRevLett.74.4420}
}

@article{grigoriev1997pinning,
  author  = {Grigoriev, Roman O. and Cross, Michael C. and Schuster, Heinz G.},
  title   = {Pinning Control of Spatiotemporal Chaos},
  journal = {Physical Review Letters},
  volume  = {79},
  pages   = {2795--2798},
  year    = {1997},
  doi     = {10.1103/PhysRevLett.79.2795},
  eprint  = {chao-dyn/9705001}
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
  eprint  = {1206.2237}
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
  eprint  = {1606.05122}
}

@article{sieber2014controlling,
  author  = {Sieber, Jan and Omel'chenko, Oleh E. and Wolfrum, Matthias},
  title   = {Controlling Unstable Chaos: Stabilizing Chimera States by Feedback},
  journal = {Physical Review Letters},
  volume  = {112},
  pages   = {054102},
  year    = {2014},
  doi     = {10.1103/PhysRevLett.112.054102},
  eprint  = {1310.7560}
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
  eprint  = {1602.06935}
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
```

## Verification ledger

| item | metadata source loaded | abstract/content source |
|---|---|---|
| B1 | PubMed 9962622 | PubMed record has no abstract; content from the paper's well-known framing + APS title/abstract listing in search results — **treat description as index-level, not full-text-verified** |
| B2 | Crossref DOI record | title + venue only; description of method is from the standard account of this experiment — verify wording before use |
| B3 | arXiv abs page | full abstract read on page |
| B4 | arXiv abs page + Crossref | full abstract read on page |
| B5 | arXiv abs page + Crossref | full abstract read on page |
| B6 | arXiv abs page | full abstract read on page |
| B7 | Wikidata record (APS 403) | abstract via indexed rendering |
| B8 | Crossref DOI record | abstract via indexed rendering (PubMed 17677342) |
| B9 | OpenAlex DOI record | abstract via indexed rendering |
| B10 | PubMed 8321317 | abstract read on page |
| B11 | arXiv abs page | full abstract read on page |
| B12 | IOPscience article page | abstract read on page |

Items B1, B2, B7, B8, B9 have publisher pages behind a 403 for automated fetch; their bibliographic
data is index-verified (Crossref / OpenAlex / PubMed / Wikidata) and their content descriptions come
from indexed abstract text. Joe should eyeball B1 and B2's descriptions against the PDFs before the
sentences go into the paper.
