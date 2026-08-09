# Lane G — The Laplacian bridge (dual-use)

Researcher: claude subagent, 2026-08-08. Brief:
`futon5/holes/tech-notes/paper/deepresearch-related-work-brief.md` §G.
Project vocabulary checked first: `futon5/holes/M-formal-patterns.md`.

## Lane notes (read this before the items)

**The lane is NOT empty — it is well populated, but it is populated in three
disconnected literatures that do not cite each other.** That disconnection is
itself the finding, and it is what makes the lane worth a paragraph in both
write-ups.

The three bodies:

1. **Structural-balance dynamics** (physics + control theory). This is a mature
   literature with exactly the theorems M-formal-patterns wants. It splits into
   (a) *discrete local edge-sign flips* — Antal–Krapivsky–Redner local/constrained
   triad dynamics, with an **absorbing balanced state**, a phase transition at
   propensity p = 1/2, and **jammed states** (local minima of the frustration
   energy that are not balanced); (b) *continuous flows* — Marvel–Kleinberg–
   Kleinberg–Strogatz's `dX/dt = X²` (only two asymptotic outcomes: harmony, or
   two hostile factions), and Cisneros-Velarde–Friedkin–Proskurnikov–Bullo's
   **gradient flow of a dissonance function**, which is the cleanest existing
   formalisation of "local edge updates that descend a frustration energy";
   (c) *signed-Laplacian spectral theory* — Altafini's bipartite-consensus
   theorem (Laplacian consensus on a signed graph converges to ±x* **iff** the
   graph is structurally balanced) and Atay–Liu's Cheeger inequalities
   (multi-way constants vanish iff there are k balanced components).
   **Together (a)+(b)+(c) are the existing frame for the memory-search formal
   thread. Joe's "joint minimum exists iff balanced" is Altafini's theorem in
   Laplacian-consensus form, plus Atay–Liu in spectral form. Cite, don't
   re-derive.**

2. **Adaptive / coevolutionary networks** (nonlinear dynamics). This is the
   frame that names the two-layer structure precisely: a fast state process on
   a slowly self-modifying substrate, with an explicit timescale-separation
   parameter ε and slow-manifold / geometric-singular-perturbation machinery.
   The canonical modern reference is **Berner–Gross–Kuehn–Kurths–Yanchuk,
   *Adaptive dynamical networks*, Phys. Rep. 1031 (2023)**; the older,
   ALife-facing one is Gross–Blasius (2008), which coined the "dynamics **on**
   the network / dynamics **of** the network" split that the MetaCA paper needs
   verbatim. **This is the single best answer to the lane's headline question**:
   the existing frame for "local edge updates while a process runs on the graph"
   is *adaptive dynamical network with slow structural adaptation*.

3. **The formal CA↔graph-rewriting bridge**: Sayama & Laramee's **Generative
   Network Automata**, which explicitly generalises CA to systems where the
   *substrate* rewrites itself based on the states running on it. This is the
   closest formal genus for the MetaCA construction that exists, and it is the
   one source that sits in *both* the paper's Related Work and the memory-search
   write-up without strain.

**What is genuinely missing (audited gap).** I searched hard for a source that
does the *specific* composite — a diffusion/search process running on a graph
whose **signed** edges are locally updated toward balance *while the search
runs*, with convergence results — and I could not find one. The nearest misses,
each verified:
- Jarman et al. 2017 has diffusion **driving** local rewiring (heat kernel →
  which edge to move), but the graph is **unsigned**.
- Tian & Lambiotte 2024 has diffusion **on a signed** graph, but the signs are
  **static** (balance influences the dynamics, not vice versa).
- Cisneros-Velarde et al. 2021 has local edge updates rebalancing a signed
  graph, but **no process running on it**.
- Yoon & Kuehn 2026 poses exactly the "structure vs adaptation" question for
  signed + adaptive coupling, but for Kuramoto phase-locking, not search.
So the memory-search construction sits in a real hole between four literatures.
That is a defensible novelty statement — and the four near-misses above are
exactly the citations that establish it.

**Searches run** (vocabulary varied deliberately across physics / control / ALife /
probability / ML): continuous-time structural balance + Laplacian flow; signed
graph Laplacian spectrum + frustration index + least eigenvalue; adaptive
coevolutionary networks (Gross–Blasius); random walk / diffusion on time-varying
graphs; local triad dynamics (Antal–Krapivsky–Redner); associative memory with
plastic connectivity / modern Hopfield on graphs; generative network automata /
graph-rewriting automata; semantic-network memory search as random walk;
adaptive rewiring driven by graph diffusion; edge-reinforced random walk
(Sabot–Tarrès / Angel–Crawford–Kozma); Altafini bipartite consensus; adaptive
networks + timescale separation; CA with evolving rules as adaptive networks;
coevolving signed appraisal networks.

**One near-pre-emption, flagged loudly** (item 13): Khajehabdollahi, Giannakakis,
Buendía, Martius & Levina, *Locally adaptive cellular automata for goal-oriented
self-organization* (arXiv 2306.07067, 2023) builds CA whose **update rule is
locally coupled to the system state**, i.e. exactly the Part II construction
("rule updates read the current state field"). It does not touch the paper's
*measurements* (no causal-perturbation / damage-reach analysis), and it is an
ML/goal-directed paper rather than a classification one, so nothing in Parts
I–III is contradicted. But **any sentence claiming the state-coupled rule-update
construction is novel must be softened or deleted**, and this must be cited
alongside `pavlic2014selfref` as the modern instance.

**One structural warning worth reading, not a contradiction** (item 12): Hara &
Kabashima's DMFT of a Hopfield network with plastic couplings finds that
*moderate* plasticity enlarges basins but *excessively strong* plasticity makes
the network **imprint the imperfect cue itself**, producing spurious attractors.
That is the same failure shape as Part III's "the freezing is an artefact of
high-precision adoption" — an independent, analytically tractable instance of
*too-faithful adoption of the current signal collapses the system onto it*. This
is the best available external corroboration for the Part III artefact claim,
and it is directly a design warning for the memory search (a rebalancing law
that trusts the current query too much will freeze the graph onto the query).

**Explicit non-finding.** I found nothing that connects structural-balance
dynamics to rule-rewriting CA. The two-layer analogy has to be carried by the
adaptive-dynamical-networks frame (items 7, 8) and the GNA formalism (item 9);
nobody has written the bridge, so the paper can claim the connection as an
observation but must present it as an analogy under a named existing frame, not
as a cited theorem.

---

## Items

### 1. Antal, Krapivsky & Redner (2005) — Dynamics of social balance on networks
- **Citation:** T. Antal, P. L. Krapivsky, S. Redner, "Dynamics of social balance on networks", *Physical Review E* **72**, 036121 (2005).
- **Identifier:** arXiv:cond-mat/0506476; DOI 10.1103/PhysRevE.72.036121
- **URL seen:** https://arxiv.org/abs/cond-mat/0506476
- **What it does:** Introduces *local triad dynamics* on a signed complete graph: pick an imbalanced triad (1 or 3 negative links) and flip one link's sign to balance it. Proves/shows an infinite network undergoes a dynamic phase transition to "paradise" (all-friendly) as the propensity p for friendly links passes 1/2, while a **finite network always falls into a balanced absorbing state**. Constrained triad dynamics (the 2006 companion) converges quickly but admits jammed configurations.
- **Relevance:** memory-search — this is *the* canonical "purely local edge updates that rebalance a signed graph", and its absorbing-state / p = 1/2 structure is the closest existing analogue of the paper's Part III freezing dynamics; cite in both places.
- **Tier:** must-cite. **Contradicts:** no.

### 2. Marvel, Strogatz & Kleinberg (2009) — The energy landscape of social balance
- **Citation:** S. A. Marvel, S. H. Strogatz, J. M. Kleinberg, "Energy landscape of social balance", *Physical Review Letters* **103**, 198701 (2009).
- **Identifier:** arXiv:0906.2893; DOI 10.1103/PhysRevLett.103.198701
- **URL seen:** https://arxiv.org/abs/0906.2893
- **What it does:** Defines the frustration energy landscape of a signed complete graph and shows numerically that it is "dimpled with local minima of widely varying energy levels". Derives rigorous bounds on the energies of these minima and proves they have a **modular structure** that classifies them; proves that for arbitrarily large networks jammed states exist all the way up to the midpoint energy (construction via Paley-type symmetric structures).
- **Relevance:** memory-search — this is the obstruction result for any *purely local* rebalancing law: descending the frustration energy locally gets trapped in jammed states that are not balanced, which is the precise analogue of Part III's "no tested locally computable quantity finds or holds the coexistence".
- **Tier:** must-cite. **Contradicts:** no — it *supports* the paper's negative result by giving a landscape-theoretic reason why local descent fails.

### 3. Marvel, Kleinberg, Kleinberg & Strogatz (2011) — Continuous-time structural balance
- **Citation:** S. A. Marvel, J. M. Kleinberg, R. D. Kleinberg, S. H. Strogatz, "Continuous-time model of structural balance", *PNAS* **108**(5), 1771–1776 (2011).
- **Identifier:** arXiv:1010.1814; DOI 10.1073/pnas.1013213108
- **URL seen:** https://arxiv.org/abs/1010.1814
- **What it does:** Studies the matrix flow dX/dt = X², where X is the symmetric matrix of signed relationship strengths. Proves that for generic initial conditions only two outcomes are possible — all relationships become friendly, or the network splits into exactly two mutually hostile factions — and gives a formula predicting faction membership from the initial condition.
- **Relevance:** memory-search — the continuous-flow counterpart of item 1 and the cleanest statement of "a signed network under a smooth self-consistent edge law reaches balance"; it is the dichotomy Joe's rebalancing search inherits when the update is smooth rather than discrete.
- **Tier:** must-cite. **Contradicts:** no.

### 4. Cisneros-Velarde, Friedkin, Proskurnikov & Bullo (2021) — Structural balance via gradient flows
- **Citation:** P. Cisneros-Velarde, N. E. Friedkin, A. V. Proskurnikov, F. Bullo, "Structural Balance via Gradient Flows over Signed Graphs", *IEEE Transactions on Automatic Control* **66**(7), 3169–3183 (2021).
- **Identifier:** arXiv:1909.11281; DOI 10.1109/TAC.2020.3018435
- **URL seen:** https://arxiv.org/abs/1909.11281 (journal metadata confirmed at https://dblp.org/search?q=Structural+Balance+via+Gradient+Flows+over+Signed+Graphs)
- **What it does:** Constructs edge-weight dynamics as the **gradient flow of a "dissonance function"** measuring violations of Heider's axioms, and analyses the critical points of that function, the transient behaviour, and convergence to (one- or two-faction) balanced configurations as strict local minima.
- **Relevance:** memory-search — this is the most direct existing formalisation of "a local edge-update law that descends a frustration energy on a signed graph"; it is the frame Joe's rebalancing law should be presented as an instance of (or a deliberate departure from).
- **Tier:** must-cite. **Contradicts:** no. **Verification note:** the arXiv abs page did not itself display the journal reference; volume/issue/pages/DOI were taken from the dblp record, which I loaded.

### 5. Altafini (2013) — Consensus on networks with antagonistic interactions
- **Citation:** C. Altafini, "Consensus Problems on Networks With Antagonistic Interactions", *IEEE Transactions on Automatic Control* **58**(4), 935–946 (2013).
- **Identifier:** DOI 10.1109/TAC.2012.2224251
- **URL seen:** https://dblp.org/search?q=Consensus+Problems+on+Networks+With+Antagonistic+Interactions (DOI resolved to https://ieeexplore.ieee.org/document/6329411/, which is paywalled to fetch)
- **What it does:** Runs Laplacian consensus dynamics on a **signed** graph and proves the dichotomy: agents reach *bipartite consensus* (values equal in modulus, opposite in sign, i.e. two coherent camps) **if and only if** the signed graph is structurally balanced; otherwise all states converge to zero (neutralisation). This is the signed-Laplacian result the memory-search thread is implicitly assuming.
- **Relevance:** memory-search — Joe's "a joint minimum exists iff the network is balanced" is exactly this theorem in Laplacian-flow form; it must be cited rather than re-derived, and it also fixes the correct failure mode for the frustrated case (collapse to neutrality, not oscillation).
- **Tier:** must-cite. **Contradicts:** no — but it **pre-empts any claim of originality** for the "joint minimum iff balanced" statement in its dynamical form. State it as known.
- **Verification:** uncertain-adjacent — bibliographic fields verified on dblp and via DOI resolution to the IEEE record; the IEEE landing page itself returned no body to WebFetch, so the abstract was not read on-page.

### 6. Atay & Liu (2020) — Cheeger constants, structural balance, spectral clustering for signed graphs
- **Citation:** F. M. Atay, S. Liu, "Cheeger constants, structural balance, and spectral clustering analysis for signed graphs", *Discrete Mathematics* **343**(1), 111616 (2020).
- **Identifier:** arXiv:1411.3530; DOI 10.1016/j.disc.2019.111616
- **URL seen:** https://arxiv.org/abs/1411.3530
- **What it does:** Introduces multi-way Cheeger-type constants h_k^σ on signed graphs with the property that h_k^σ = 0 **iff** the graph has k balanced connected components, proves higher-order and dual Cheeger inequalities linking these to signed-Laplacian eigenvalues, estimates the extremal signed-Laplacian eigenvalues in terms of the number of signed triangles, and derives spectral clustering methods from them.
- **Relevance:** memory-search — this is the *spectral* form of "balanced iff a joint minimum exists", and it also supplies the algorithmic layer (spectral clustering on the signed Laplacian) that a graphical memory search would actually run.
- **Tier:** must-cite. **Contradicts:** no.

### 7. Berner, Gross, Kuehn, Kurths & Yanchuk (2023) — Adaptive dynamical networks
- **Citation:** R. Berner, T. Gross, C. Kuehn, J. Kurths, S. Yanchuk, "Adaptive dynamical networks", *Physics Reports* **1031**, 1–59 (2023).
- **Identifier:** arXiv:2304.05652; DOI 10.1016/j.physrep.2023.08.001
- **URL seen:** https://arxiv.org/abs/2304.05652 (journal fields confirmed at https://csh.ac.at/publication/adaptive-dynamical-networks/)
- **What it does:** The current comprehensive review of systems whose connectivity changes over time as a function of the dynamical state running on it — "their function depends on their structure and vice versa". Covers the explicit timescale-separation parameter ε in the adaptation law, the slow/fast (geometric singular perturbation) reduction, slow-manifold methods, and applications from synaptic plasticity to epidemic and social systems.
- **Relevance:** **both** — this is the best available named frame for the whole lane question ("local edge updates while a process runs on the graph" = adaptive dynamical network with slow structural adaptation) *and* the frame under which the MetaCA two-layer structure (fast phenotype on slow genotype) can be described honestly in the paper's Related Work without inventing a bridge.
- **Tier:** must-cite. **Contradicts:** no. **Verification note:** the arXiv abs page did not display the Phys. Rep. reference; volume/pages/DOI were read on the Complexity Science Hub publication page and are consistent with the ScienceDirect record S0370157323002685 (whose landing page 403s to WebFetch).

### 8. Gross & Blasius (2008) — Adaptive coevolutionary networks: a review
- **Citation:** T. Gross, B. Blasius, "Adaptive coevolutionary networks: a review", *Journal of the Royal Society Interface* **5**(20), 259–271 (2008).
- **Identifier:** arXiv:0709.1858 (DOI 10.1098/rsif.2007.1229)
- **URL seen:** https://arxiv.org/abs/0709.1858
- **What it does:** The founding survey of adaptive networks: systems that "combine topological evolution of the network with dynamics in the network nodes". Draws together parallel results from genomics, epidemiology and game theory under two common themes — complex dynamics, and robust topological self-organisation **based on simple local rules**.
- **Relevance:** **both** — supplies the "dynamics *on* the network vs dynamics *of* the network" vocabulary that the MetaCA paper needs for its phenotype/genotype split, and it is the standard citation reviewers will expect before any adaptive-network claim.
- **Tier:** must-cite (as background; item 7 is the modern one). **Contradicts:** no. **Verification note:** the arXiv page lists the J. R. Soc. Interface 5, 259–271 (2008) reference but only the arXiv DOI; the publisher DOI 10.1098/rsif.2007.1229 was not resolved on-page — treat that field as uncertain and check before submission.

### 9. Sayama & Laramee (2009) — Generative Network Automata
- **Citation:** H. Sayama, C. Laramee, "Generative Network Automata: A Generalized Framework for Modeling Adaptive Network Dynamics Using Graph Rewritings", in T. Gross & H. Sayama (eds.), *Adaptive Networks: Theory, Models and Applications*, Springer/NECSI Studies on Complexity, pp. 311–332 (2009).
- **Identifier:** arXiv:0901.0216; DOI 10.1007/978-3-642-01284-6_15
- **URL seen:** https://arxiv.org/abs/0901.0216 (chapter landing: https://link.springer.com/chapter/10.1007/978-3-642-01284-6_15)
- **What it does:** Defines Generative Network Automata: a graph-rewriting formalism in which **state transitions and autonomous topology transformations are represented uniformly**, so that the substrate is rewritten by rules that read the states currently running on it. Explicitly positioned as the generalisation of cellular automata beyond a fixed lattice.
- **Relevance:** **both** — the closest existing *formal* genus for "a fast state process on a slowly self-modifying substrate", and therefore the single most reusable citation across the paper's Related Work and the memory-search write-up. Caveat for scope discipline: GNA rewrites *topology*, the MetaCA rewrites *local update rules* on a fixed lattice — a sibling, not a superset; say so explicitly.
- **Tier:** must-cite. **Contradicts:** no. **Verification note:** page range 311–332 taken from the Springer chapter listing, not re-read line by line; the arXiv version is the safe citation if the page range cannot be confirmed.

### 10. Jarman, Steur, Trengove, Tyukin & van Leeuwen (2017) — Adaptive rewiring in response to graph diffusion
- **Citation:** N. Jarman, E. Steur, C. Trengove, I. Y. Tyukin, C. van Leeuwen, "Self-organisation of small-world networks by adaptive rewiring in response to graph diffusion", *Scientific Reports* **7**, 13158 (2017).
- **Identifier:** DOI 10.1038/s41598-017-12589-9; PMID 29030608
- **URL seen:** https://pubmed.ncbi.nlm.nih.gov/29030608/ (nature.com landing page redirects to an IdP and could not be fetched directly)
- **What it does:** Runs heat-kernel **diffusion on the graph** and uses the resulting traffic to drive **local edge rewiring** — add shortcuts where diffusion is intense, prune where it is low. Shows small-world structure always emerges from random initial graphs for any nonzero diffusion rate, and that the diffusion rate selects modular vs centralised topology, with a hierarchical (balanced) structure at the transition point.
- **Relevance:** memory-search — this is the closest published mechanism to Joe's construction: *the process running on the graph is what decides which edges get updated*. The critical-point result (modularity/centrality balance at a transition) is a direct template for what a rebalancing search should be looking for. The graph is unsigned, so the signed extension is the open part.
- **Tier:** must-cite. **Contradicts:** no.

### 11. Angel, Crawford & Kozma (2014) — Localization for linearly edge-reinforced random walks
- **Citation:** O. Angel, N. Crawford, G. Kozma, "Localization for linearly edge reinforced random walks", *Duke Mathematical Journal* **163**(5), 889–921 (2014).
- **Identifier:** arXiv:1203.4010; DOI 10.1215/00127094-2644357
- **URL seen:** https://arxiv.org/abs/1203.4010
- **What it does:** Proves that the linearly edge-reinforced random walk (a walker that increases the weight of each edge it crosses, i.e. **local edge updates driven by the search itself**) is recurrent on any bounded-degree graph for sufficiently small initial weights, and transient on non-amenable graphs for large initial weights — establishing a genuine localization/delocalization phase transition. Proof route: representing the walk as a mixture of reversible Markov chains rather than via the Coppersmith–Diaconis "magic formula".
- **Relevance:** memory-search — the rigorous probability-theory instance of "a search process that rewrites the weights it walks on", and a warning with a theorem attached: **small initial edge weights ⇒ the search localizes** (freezes onto a small subgraph). That is a quantitative version of the freezing failure mode Part III reports, in a setting where it is proved rather than measured.
- **Tier:** enriches (must-cite for the memory-search write-up specifically). **Contradicts:** no.

### 12. Hara & Kabashima (2026) — DMFT analysis of a Hopfield network with plasticity
- **Citation:** Y. Hara, Y. Kabashima, "DMFT analysis of Hopfield network with plasticity", arXiv:2605.22254 (submitted 21 May 2026, revised 5 June 2026).
- **Identifier:** arXiv:2605.22254
- **URL seen:** https://arxiv.org/abs/2605.22254
- **What it does:** Dynamical mean-field theory of a fully connected Hopfield network in which **neural states and synaptic couplings coevolve during retrieval** — i.e. the graph is the plastic layer and the retrieval dynamics is the fast layer. Finds an optimal plasticity level: moderate plasticity enlarges basins of attraction and raises the retrievable load via positive delayed feedback, while **excessively strong plasticity makes the network imprint the imperfect initial cue itself, producing spurious attractors and degrading retrieval**.
- **Relevance:** **both** — the cleanest analytically tractable statement of Part III's core artefact ("the freezing is an artefact of high-precision adoption"): too-faithful adoption of the current signal collapses the system onto that signal. For the memory search it is a direct design constraint on the rebalancing gain.
- **Tier:** enriches (strong; promote to must-cite if Part III's artefact claim is foregrounded). **Contradicts:** no — it corroborates. **Verification:** seen on page; note it is a 2026 preprint with no journal reference yet.

### 13. Khajehabdollahi, Giannakakis, Buendía, Martius & Levina (2023) — Locally adaptive cellular automata ⚠ PARTIAL PRE-EMPTION
- **Citation:** S. Khajehabdollahi, E. Giannakakis, V. Buendía, G. Martius, A. Levina, "Locally adaptive cellular automata for goal-oriented self-organization", arXiv:2306.07067 (12 June 2023).
- **Identifier:** arXiv:2306.07067
- **URL seen:** https://arxiv.org/abs/2306.07067
- **What it does:** Proposes a model class of **adaptive cellular automata** in which "the update rule of the cellular automaton [is coupled] with itself and the system state in a localized way" — a CA whose local rule is modified, online and locally, by the state field it is acting on. Demonstrated on temperature-coupling in an Ising model and on plasticity mechanisms in neural models, with local/global measurements feeding back into the rule dynamics in real time.
- **Relevance:** **Part II** — the paper's Part II construction is "rule updates read the current state field"; this builds exactly that class of CA, two years earlier, in a different community (ML / neural-evolutionary). It does **not** perform the paper's measurement (no fork-and-flip causal-perturbation / damage-reach analysis) and does not classify an operator family, so no measured result is threatened.
- **Tier:** must-cite. **Contradicts:** **YES (partially).**
- **Contradiction note:** it undercuts any *novelty* claim for the state-coupled rule-update construction itself (Part II, and any "this does not appear in the surveyed literature" sentence that reaches that far). The defensible residual claim is the *measurement* — the causal-reach doubling and the ordering blind < current-reading, frozen-reading < blind — not the construction. Cite alongside `pavlic2014selfref` and `mori1998rulechanging` and state the distinction explicitly. Also worth flagging to lane A, whose brief asks the same novelty question.

### 14. Tian & Lambiotte (2024) — Spreading and structural balance on signed networks
- **Citation:** Y. Tian, R. Lambiotte, "Spreading and Structural Balance on Signed Networks", *SIAM Journal on Applied Dynamical Systems* **23**(1), 50–80 (2024).
- **Identifier:** DOI 10.1137/22M1542325
- **URL seen:** https://epubs.siam.org/doi/10.1137/22M1542325
- **What it does:** Classifies signed networks as balanced, antibalanced, or strictly unbalanced from cycle properties, relates each class to spectral properties of the signed adjacency/Laplacian operators, and shows that these classes produce consistent patterns in **both linear and nonlinear spreading dynamics** running on the network.
- **Relevance:** memory-search — the reference for "what a diffusion/search process actually does *on* a signed graph as a function of its balance type"; supplies the fast-layer half of the two-layer memory-search model. Important scope note (verified on the landing page): **the signs here are static** — the paper studies how balance shapes dynamics, not how dynamics reshapes balance. That is precisely the gap Joe's construction fills.
- **Tier:** enriches. **Contradicts:** no.

### 15. Yoon & Kuehn (2026) — Stability of phase-locked states in signed Kuramoto networks
- **Citation:** J. Yoon, C. Kuehn, "Stability of Phase-Locked States in Signed Kuramoto Networks: Structure versus Adaptation", arXiv:2602.11981 (12 Feb 2026).
- **Identifier:** arXiv:2602.11981
- **URL seen:** https://arxiv.org/abs/2602.11981
- **What it does:** Asks, for adaptive Kuramoto models with antipodal and rotating-wave phase-locked states, whether their persistence comes from intrinsic properties of the **signed interaction network** or from the **adaptive coupling dynamics**. Concludes that static signed structure severely limits the stability of phase-locked states, while adaptive coupling enhances robustness where stability conditions hold.
- **Relevance:** memory-search — the only source found that poses the lane's headline question in the exact "structure vs adaptation" form: it is the adaptation layer, not the signed structure, that buys robustness. Useful as the argument that the *plastic* layer is doing the work in a rebalancing search.
- **Tier:** enriches. **Contradicts:** no. **Verification:** seen on page; 2026 preprint, no journal reference — treat as recent-work signposting, not a settled result.

---

## BibTeX

```bibtex
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
  title   = {Structural Balance via Gradient Flows over Signed Graphs},
  journal = {IEEE Transactions on Automatic Control},
  volume  = {66},
  number  = {7},
  pages   = {3169--3183},
  year    = {2021},
  doi     = {10.1109/TAC.2020.3018435},
  eprint  = {1909.11281},
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
  archivePrefix = {arXiv},
  note    = {publisher DOI not resolved on-page; verify before submission}
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

@article{jarman2017adaptiverewiring,
  author  = {Jarman, Nicholas and Steur, Erik and Trengove, Chris and Tyukin, Ivan Y. and van Leeuwen, Cees},
  title   = {Self-organisation of small-world networks by adaptive rewiring in response to graph diffusion},
  journal = {Scientific Reports},
  volume  = {7},
  pages   = {13158},
  year    = {2017},
  doi     = {10.1038/s41598-017-12589-9}
}

@article{angel2014errwlocalization,
  author  = {Angel, Omer and Crawford, Nicholas and Kozma, Gady},
  title   = {Localization for linearly edge reinforced random walks},
  journal = {Duke Mathematical Journal},
  volume  = {163},
  number  = {5},
  pages   = {889--921},
  year    = {2014},
  doi     = {10.1215/00127094-2644357},
  eprint  = {1203.4010},
  archivePrefix = {arXiv}
}

@misc{hara2026dmfthopfieldplasticity,
  author        = {Hara, Yoshinori and Kabashima, Yoshiyuki},
  title         = {{DMFT} analysis of {H}opfield network with plasticity},
  year          = {2026},
  eprint        = {2605.22254},
  archivePrefix = {arXiv}
}

@misc{khajehabdollahi2023locallyadaptiveca,
  author        = {Khajehabdollahi, Sina and Giannakakis, Emmanouil and Buend{\'i}a, Victor and Martius, Georg and Levina, Anna},
  title         = {Locally adaptive cellular automata for goal-oriented self-organization},
  year          = {2023},
  eprint        = {2306.07067},
  archivePrefix = {arXiv},
  doi           = {10.48550/arXiv.2306.07067}
}

@article{tian2024spreadingsigned,
  author  = {Tian, Yu and Lambiotte, Renaud},
  title   = {Spreading and Structural Balance on Signed Networks},
  journal = {SIAM Journal on Applied Dynamical Systems},
  volume  = {23},
  number  = {1},
  pages   = {50--80},
  year    = {2024},
  doi     = {10.1137/22M1542325}
}

@misc{yoon2026signedkuramoto,
  author        = {Yoon, Jaeyoung and Kuehn, Christian},
  title         = {Stability of Phase-Locked States in Signed {K}uramoto Networks: Structure versus Adaptation},
  year          = {2026},
  eprint        = {2602.11981},
  archivePrefix = {arXiv}
}
```
