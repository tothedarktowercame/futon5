# Lane F — Baldwin effect and heritable acquired modifiers

Researcher: claude subagent, lane F. Date: 2026-08-08.
Target: Related Work / Discussion of the rule-rewriting CA paper, Part III (exotypes).

## Lane notes (read this first)

**Headline: the lane splits cleanly in two, and only one half is where you'd expect.**

1. **The Baldwin canon is non-spatial.** Hinton–Nowlan (1987) and its ALife
   descendants (Mayley 1996; Ackley–Littman 1991; Suzuki–Arita) are panmictic
   GA populations. I searched specifically for a lattice/spatially-structured
   Baldwin model — queries included "Baldwin effect spatially structured
   population lattice", "genetic assimilation cellular automata lattice",
   "gene-culture coevolution lattice model fast/slow channel", "epigenetic
   inheritance lattice acquired modifier transmitted to neighbours",
   "cellular automaton cells inherit rule from neighbours ... Baldwin".
   **I did not find a canonical lattice Baldwin paper.** That is a real
   negative: there is no "the" citation for "an acquired, transmissible
   modifier layer changes evolutionary dynamics *on a lattice*" inside the
   Baldwin literature proper. Say so, cite the canon for the mechanism, and
   cite the physics line (below) for the lattice.

2. **The lattice half of the question is answered by the cultural-dissemination
   physics line, not by ALife.** Axelrod's model *is* a lattice model of an
   acquired, neighbour-transmitted modifier, and it has 25 years of
   statistical-mechanics analysis attached. This is the cleanest citable
   answer to the lane's headline question, and it is the one a reviewer from
   the complex-systems side will expect.

3. **The adoption-precision sub-question has a strong and slightly
   uncomfortable answer.** Two independent precedents:
   - **Axelrod/Klemm:** high-fidelity local copying with a similarity
     (homophily) gate drives a lattice into **frozen absorbing
     configurations**; adding undirected noise destabilises them. Klemm et al.
     (2003) give the sharp criterion (noise rate `r` vs perturbation relaxation
     time `T`: `r ≲ T⁻¹` → monoculture, `r ≳ T⁻¹` → sustained disorder). Our
     "undirected noise holds a foam of small frozen islands amid changing
     surroundings" is a *finite-size, finite-noise-rate regime* of exactly this
     competition. Cite it; do not claim novelty for the qualitative shape.
   - **Huberman–Glance (1993):** in the nearest structural analogue (a lattice
     where each cell adopts the modifier of its best-performing neighbour), the
     reported phase outcome turned out to be an **artefact of the update
     discipline** — synchronous update gave persistent coexistence, asynchronous
     update collapsed it. **This is the loudest find in the lane.** Our Part III
     conclusion has the same *form*: "the freezing is an artefact of
     high-precision adoption". A reviewer who knows the spatial-games literature
     will read Part III as a rediscovery of the Huberman–Glance lesson in a new
     substrate unless the paper cites it and states the difference (our knob is
     adoption *precision/fidelity*, theirs is update *synchrony*; our system's
     adopted object is a rule-rewriting operator, not a game strategy).
     **Flagged as pre-emption, not contradiction:** it does not falsify anything
     we measured, but it removes the "surprising and unprecedented" register
     from the artefact claim.

4. **Verified but routed elsewhere / deliberately not written up as items**
   (all landing pages loaded, all real):
   - Castellano, Marsili & Vespignani, *Nonequilibrium phase transition in a
     model for social influence*, PRL 85, 3536 (2000), arXiv:cond-mat/0003111
     — the Axelrod lattice order/disorder transition is a genuine nonequilibrium
     phase transition (continuous or discontinuous by parameter). Relevant to
     Part I's "broad crossover, no critical point" as *context* (different
     model, so no contradiction), but it belongs in lane C/D, not F.
   - Carro, Toral & San Miguel, *The noisy voter model on complex networks*,
     Sci. Rep. 6, 24775 (2016), doi:10.1038/srep24775 — pure imitation →
     consensus, noise → sustained coexistence, with a finite-size noise-induced
     transition. Correct mechanism, but the paper is network-focused (lattices
     appear only for autocorrelations), so Klemm et al. is the better lattice
     citation and I did not duplicate it.
   - Shrestha, Reimers, Jain, Baldini, Braccini, Roli & Nichele, *Emergent
     Dynamics in Heterogeneous Life-Like Cellular Automata*, arXiv:2406.13383
     — 2D CA where each cell carries its own life-like rule and rules are
     inherited-with-variation into empty neighbouring sites during the run.
     Closest thing found to our substrate with heritable per-cell rules; the
     abstract does not establish neighbour-to-neighbour *adoption* of an
     operator, and it is really a lane-A item (rule-as-genotype), so I flag it
     here and hand it to lane A rather than claiming it for F.
   - Henrich & Boyd (1998), conformist transmission → within-group homogeneity:
     right intuition, wrong formalism (no lattice, no absorbing-state analysis).
     Weaker than Axelrod/Klemm for our purposes; not recommended.

5. **No item in this lane contradicts a measured result.** The one item that
   pre-empts a *framing* is Huberman–Glance (item 5).

---

## Items

### 1. Hinton & Nowlan (1987) — the Baldwin canon

- **Citation:** Geoffrey E. Hinton and Steven J. Nowlan. "How Learning Can Guide
  Evolution." *Complex Systems* 1(3):495–502, 1987.
- **Identifier:** none (pre-DOI Complex Systems volume; no arXiv id).
- **URL loaded:** https://www.cs.toronto.edu/~hinton/absps/evolution.htm
  (author's own publication page, carrying the abstract and full citation).
- **What it does:** A needle-in-a-haystack fitness landscape in which a
  lifetime "learning" search over undetermined alleles smooths the landscape,
  so that a population that would never find the target by genetic search alone
  finds it quickly. Establishes that an acquired, non-inherited capacity can
  nevertheless reshape the evolutionary search space (the Baldwin effect), and
  that the plastic loci then get progressively fixed.
- **Relevance:** Part III — this is the reference frame in which "exotype" is
  legible as an acquired modifier layer coupled to a slower genotype layer;
  a reviewer will expect it the moment the word Baldwin appears.
- **Tier:** must-cite. **Contradicts:** no.
- **Verification:** seen-on-page.

### 2. Mayley (1996) — genetic assimilation and the cost of the plastic layer

- **Citation:** Giles Mayley. "Landscapes, Learning Costs, and Genetic
  Assimilation." *Evolutionary Computation* 4(3):213–234, 1996.
- **Identifier:** doi:10.1162/evco.1996.4.3.213
- **URL loaded:**
  https://api.semanticscholar.org/graph/v1/paper/DOI:10.1162/evco.1996.4.3.213?fields=title,authors,year,venue,externalIds,abstract
  (authoritative DOI-keyed index entry; the MIT Press abstract page
  https://direct.mit.edu/evco/article-abstract/4/3/213/779/Landscapes-Learning-Costs-and-Genetic-Assimilation
  returned HTTP 403 to the fetcher, so I did not rely on it).
- **What it does:** Gives the conditions under which the Baldwin effect proceeds
  to genetic assimilation: a correlation between the learned and genetic
  landscapes, plus a *cost* to the plastic layer that supplies the selection
  pressure to hard-code the acquired trait. Without a cost, the plastic layer
  is retained and assimilation stalls.
- **Relevance:** Part III — our exotype layer carries no explicit cost, which is
  precisely the regime Mayley identifies as *non*-assimilating; this is the
  cleanest existing account of why an acquired modifier layer can persist rather
  than be absorbed, and it contextualises the coexistence outcome.
- **Tier:** must-cite. **Contradicts:** no.
- **Verification:** seen-on-page (metadata only; abstract not served — title,
  authors, year, venue and DOI all confirmed).

### 3. Axelrod (1997) — the lattice answer to the lane's headline question

- **Citation:** Robert Axelrod. "The Dissemination of Culture: A Model with
  Local Convergence and Global Polarization." *Journal of Conflict Resolution*
  41(2):203–226, 1997.
- **Identifier:** doi:10.1177/0022002797041002001
- **URL loaded:** https://journals.sagepub.com/doi/10.1177/0022002797041002001
- **What it does:** Agents on a square lattice each carry F cultural features
  with q traits each; an agent adopts a trait from a neighbour with probability
  proportional to their existing similarity. The dynamics runs to **frozen
  absorbing configurations**: locally converged domains that globally never
  homogenise. The number and size of surviving domains depend on F, q, the
  interaction range, and system size.
- **Relevance:** Part III — this is the cleanest citable instance of "an
  acquired, transmissible modifier layer, carried per-cell on a lattice and
  copied from neighbours, produces frozen-vs-changing phase structure". It is
  the reference model our exotype-adoption arm is structurally closest to.
- **Tier:** must-cite. **Contradicts:** no.
- **Verification:** seen-on-page.

### 4. Klemm, Eguíluz, Toral & San Miguel (2003) — noise destabilises the frozen state

- **Citation:** Konstantin Klemm, Víctor M. Eguíluz, Raúl Toral and Maxi San
  Miguel. "Global culture: A noise-induced transition in finite systems."
  *Physical Review E* 67(4):045101(R), 2003.
- **Identifier:** doi:10.1103/PhysRevE.67.045101; arXiv:cond-mat/0205188
- **URL loaded:** https://arxiv.org/abs/cond-mat/0205188
- **What it does:** Adds cultural drift (undirected single-trait noise) to the
  Axelrod lattice model and shows the frozen multicultural configurations are
  only **metastable**. In d=1 the result is proved via a Lyapunov potential;
  in d=2 the control is the noise rate r against the average relaxation time T
  of a perturbation — `r ≲ T⁻¹` drives the system to a uniform (monocultural)
  state, `r ≳ T⁻¹` sustains disorder. In the thermodynamic limit T diverges and
  the frozen polarised state survives.
- **Relevance:** Part III — direct precedent for both of our noise results
  (undirected noise prevents/erodes the frozen phase; the effect is a
  finite-size, rate-vs-relaxation-time competition rather than a true phase
  transition), and the natural place to point when explaining why the "foam" of
  small frozen islands is a rate-controlled regime, not a new phase.
- **Tier:** must-cite. **Contradicts:** no — but it **pre-empts the general
  shape** of the noise finding, so present ours as the CA-substrate instance
  with the extra ingredient (the transmitted object is a rule-rewriting
  operator), not as a new phenomenon.
- **Verification:** seen-on-page.

### 5. Huberman & Glance (1993) — ⚠ PRE-EMPTS THE FORM OF THE PART III ARTEFACT CLAIM

- **Citation:** Bernardo A. Huberman and Natalie S. Glance. "Evolutionary games
  and computer simulations." *Proceedings of the National Academy of Sciences
  USA* 90(16):7716–7718, 1993.
- **Identifier:** doi:10.1073/pnas.90.16.7716; PMID 8356075
- **URL loaded:** https://pubmed.ncbi.nlm.nih.gov/8356075/ (the PNAS landing
  page https://www.pnas.org/doi/10.1073/pnas.90.16.7716 returned HTTP 403 to
  the fetcher; PubMed served the full record).
- **What it does:** Re-runs Nowak & May's spatial evolutionary game — a lattice
  where each cell adopts the strategy of its best-performing neighbour — with
  **asynchronous** rather than synchronous updating. The persistent
  coexistence of cooperators and defectors, and the "dynamic fractal"
  structures, **disappear**. The conclusion is that the reported phase outcome
  was a property of the update discipline (the space–time granularity of the
  simulation), not of the modelled interaction.
- **Relevance:** Part III — **this is the closest prior statement of our own
  headline artefact claim.** Our claim ("the freezing is an artefact of
  high-precision adoption; with adoption off, coexistence is the default") has
  the same logical form as theirs ("the coexistence is an artefact of
  synchronous update"), in the same class of system (lattice, adopt-from-best-
  neighbour). It does not contradict any measurement of ours; it removes the
  novelty of the *move*. The paper should cite it, state the difference
  explicitly (their knob is update synchrony, ours is adoption fidelity; their
  adopted object is a game strategy, ours is a rule-rewriting operator; their
  artefact destroys coexistence, ours creates freezing), and thereby claim the
  narrower, defensible thing.
- **Tier:** must-cite. **Contradicts:** ⚠ **yes — pre-empts** (framing, not
  measurement).
- **Verification:** seen-on-page.

### 6. Nowak & May (1992) — the target of item 5, and the coexistence precedent

- **Citation:** Martin A. Nowak and Robert M. May. "Evolutionary games and
  spatial chaos." *Nature* 359:826–829, 1992.
- **Identifier:** doi:10.1038/359826a0
- **URL loaded:**
  https://api.semanticscholar.org/graph/v1/paper/DOI:10.1038/359826a0?fields=title,authors,year,venue,journal,externalIds,abstract
  (authoritative DOI-keyed index entry; nature.com redirects to an IdP
  authorization host and could not be fetched anonymously — abstract elided by
  the publisher, so title/authors/venue/volume/pages/DOI are what I confirmed).
- **What it does:** Deterministic lattice game in which every cell copies the
  strategy of the best-scoring cell in its neighbourhood. Depending on the
  payoff parameter and the initial condition the lattice either freezes, or
  sustains indefinite spatiotemporal churn with both strategies coexisting
  forever ("evolutionary kaleidoscopes", "dynamic fractals").
- **Relevance:** Part III — precedent that a lattice of cells adopting a
  neighbour's modifier under an optimising rule yields exactly the tripartite
  outcome set we report (one phase wins / the other wins / indefinite
  coexistence), governed by a scalar parameter. Cite together with item 5;
  citing 5 without 6 is unreadable.
- **Tier:** enriches. **Contradicts:** no.
- **Verification:** seen-on-page (metadata via DOI-keyed index; publisher
  landing page not anonymously fetchable — marked accordingly).

### 7. Shreesha & Levin (2023) — fast plastic layer redirects what the slow layer optimises

- **Citation:** Lakshwin Shreesha and Michael Levin. "Cellular Competency during
  Development Alters Evolutionary Dynamics in an Artificial Embryogeny Model."
  *Entropy* 25(1):131, 2023.
- **Identifier:** doi:10.3390/e25010131; PMCID PMC9858125; PMID 36673272
- **URL loaded:** https://pmc.ncbi.nlm.nih.gov/articles/PMC9858125/ (the MDPI
  landing page https://www.mdpi.com/1099-4300/25/1/131 returned HTTP 403 to the
  fetcher).
- **What it does:** Two-layer artificial embryogeny: a slow genetic layer plus a
  fast "competency" layer in which cells rearrange themselves before fitness is
  measured. Competent populations improve faster, but competency **masks** the
  raw genotype: selection stops improving the structural genome and instead
  ratchets up the plastic layer, a positive feedback that locks evolution into
  the fast layer.
- **Relevance:** Part III — the cleanest recent statement of "a fast acquired
  modifier layer changes *what* the slow layer optimises, not merely how fast",
  which is the mechanism our exotype arm instantiates; also the theoretical
  counterweight to a naive Baldwin reading, since here the plastic layer is
  *not* assimilated. Pairs with Mayley (item 2) on the cost/assimilation
  condition. (Note: an earlier single-author preprint of this work exists as
  arXiv:2310.09318, labelled a Master's thesis — cite the Entropy paper.)
- **Tier:** enriches. **Contradicts:** no.
- **Verification:** seen-on-page.

### 8. Santos, Szathmáry & Fontanari (2015) — the Baldwin-speedup illusion

- **Citation:** Mauro Santos, Eörs Szathmáry and José F. Fontanari. "Phenotypic
  plasticity, the Baldwin effect, and the speeding up of evolution: The
  computational roots of an illusion." *Journal of Theoretical Biology*
  371:127–136, 2015.
- **Identifier:** doi:10.1016/j.jtbi.2015.02.012; arXiv:1411.6843
- **URL loaded:** https://arxiv.org/abs/1411.6843
- **What it does:** Analytical and computational re-examination of the
  Hinton–Nowlan setup, arguing that a standard population-genetics treatment
  solves the supposedly unsolvable landscape without any learning, so the
  claimed acceleration is an artefact of the original model's construction
  rather than a general property of phenotypic plasticity.
- **Relevance:** Part III — a guard rail. If the paper invokes Baldwin at all it
  should cite this, because it is the standing reason not to claim that the
  exotype layer *accelerates* anything; it also supports our register of
  reporting negative and artefact-flavoured results about the plastic layer
  rather than a speedup story.
- **Tier:** enriches. **Contradicts:** no (it contradicts a claim we do *not*
  make; citing it protects us).
- **Verification:** seen-on-page.

---

## BibTeX

```bibtex
@article{hinton1987learning,
  author  = {Hinton, Geoffrey E. and Nowlan, Steven J.},
  title   = {How Learning Can Guide Evolution},
  journal = {Complex Systems},
  volume  = {1},
  number  = {3},
  pages   = {495--502},
  year    = {1987},
  note    = {No DOI; author copy at
             \url{https://www.cs.toronto.edu/~hinton/absps/evolution.htm}}
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
  title   = {The Dissemination of Culture: A Model with Local Convergence
             and Global Polarization},
  journal = {Journal of Conflict Resolution},
  volume  = {41},
  number  = {2},
  pages   = {203--226},
  year    = {1997},
  doi     = {10.1177/0022002797041002001}
}

@article{klemm2003global,
  author  = {Klemm, Konstantin and Egu{\'\i}luz, V{\'\i}ctor M. and
             Toral, Ra{\'u}l and San Miguel, Maxi},
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
  pages   = {826--829},
  year    = {1992},
  doi     = {10.1038/359826a0}
}

@article{shreesha2023competency,
  author  = {Shreesha, Lakshwin and Levin, Michael},
  title   = {Cellular Competency during Development Alters Evolutionary
             Dynamics in an Artificial Embryogeny Model},
  journal = {Entropy},
  volume  = {25},
  number  = {1},
  pages   = {131},
  year    = {2023},
  doi     = {10.3390/e25010131}
}

@article{santos2015illusion,
  author  = {Santos, Mauro and Szathm{\'a}ry, E{\"o}rs and
             Fontanari, Jos{\'e} F.},
  title   = {Phenotypic Plasticity, the {Baldwin} Effect, and the Speeding
             up of Evolution: The Computational Roots of an Illusion},
  journal = {Journal of Theoretical Biology},
  volume  = {371},
  pages   = {127--136},
  year    = {2015},
  doi     = {10.1016/j.jtbi.2015.02.012},
  eprint  = {1411.6843},
  archivePrefix = {arXiv}
}
```

## Also verified, not written up as items (see lane note 4)

- Castellano, Marsili & Vespignani, PRL 85, 3536 (2000),
  https://arxiv.org/abs/cond-mat/0003111 — route to lane C/D.
- Carro, Toral & San Miguel, Sci. Rep. 6, 24775 (2016), doi:10.1038/srep24775,
  https://pmc.ncbi.nlm.nih.gov/articles/PMC4837380/ — networks, not lattices.
- Shrestha et al., arXiv:2406.13383, https://arxiv.org/abs/2406.13383 —
  route to lane A (per-cell rule genomes inherited locally in a 2D CA).
