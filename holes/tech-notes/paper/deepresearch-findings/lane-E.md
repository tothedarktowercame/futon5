# Lane E — Self-tuning to criticality: the positive counterpart

Researcher: claude subagent, 2026-08-08. Scope: the literature that claims what
our Part III denies — that a *local* rule can drive an extended system to (or
hold it at) a critical / mixed state — plus the critiques of those claims, plus
the search for a named counterpart to our decorrelation-time obstruction.

## Lane notes (read this first)

**The lane is NOT empty. It is dense, and one item is a genuine near-miss that a
reviewer will raise.**

Three things came out of the sweep.

**(1) What global information is smuggled in — the literature answers this
explicitly, sometimes in the authors' own words.** The mechanisms fall into three
families, and each family has a different smuggling route:

- *Order-parameter feedback on the control parameter, driven at a vanishing
  rate.* This is Sornette–Johansen–Dornic's reading of SOC generally, and
  Dickman–Muñoz–Vespignani–Zapperi's "paths to SOC" (already-adjacent to lane
  D). The smuggled quantity is the **global order parameter**, plus a **drive
  rate that must go to zero** — i.e. an externally imposed timescale
  separation. On this reading SOC is not self-tuning at all; it is criticality
  reached by tuning in a rotated coordinate system.
- *Rates tuned with system size.* Pruessner–Peters is the sharpest form of the
  "SOC is really tuned criticality" line: the drive/dissipation mechanism can
  be applied to **any** continuous transition (they do it to the *Ising* model,
  which has no absorbing state), and the finite-size scaling exponents depend
  on how you tune drive and dissipation **against N**. Knowledge of N is global
  information no cell has.
- *Non-conserving systems only reach "apparent" criticality.* Bonachela–Muñoz
  show that without a conservation law a recharging rate must be fine-tuned;
  what you get instead is **self-organized quasi-criticality (SOqC)** —
  hovering, with excursions, not a critical point. This is the single closest
  published statement of our own negative result, arrived at analytically in a
  different substrate.

**(2) The near-miss that must be cited and distinguished: Droste, Do & Gross
(2013).** They derive *analytically* when an activity-dependent local rewiring
rule reaches criticality, and they say in plain language what the obstruction
is and how to get round it:

> "neurons in the resting state do not possess any information about the global
> phase, as they occur in both phases. By contrast, neurons in the firing state
> can infer the global phase from their local state, as their occurrence is
> restricted to the active phase."

> "To achieve aSOC despite the limited local accessibility of the global phase,
> links have to be created *tentatively* as long as no definitive information is
> known, but destroyed *decisively* once information about the global phase is
> available."

with the condition l ≪ g (link-loss rate much smaller than link-gain rate).
So: a local rule works when the local observable is a **one-sided certificate**
of the global phase (firing ⇒ active phase; resting ⇒ nothing), and when the
two rewiring rates are **asymmetrically separated**. This is the best-developed
theory of exactly the thing our Part III fails to find, and it is the reason a
reviewer will ask "did you try an asymmetric / one-sided rule?".
**Flagged as contradicting an unscoped reading of our negative** — see the item.

**(3) Named counterpart to the decorrelation-time obstruction: partial.** There
is no single canonical theorem called e.g. "the local-controller timescale
bound". The three closest named things, in order of fit:
- **"Limited local accessibility of the global phase"** (Droste–Do–Gross, above)
  — the closest *named* framing, and it is in the same problem class (local
  rewiring rules in an extended system).
- **Detection-power limits on finite time series** (Boettiger–Hastings): the
  early-warning-signal literature's quantitative statement that an indicator
  computed over a finite window has an error rate, and that for common
  indicators the error rate is severe even under favourable assumptions. This
  is the closest *quantitative* counterpart to "the quantity decorrelates
  faster than the window discrimination requires".
- **Information-theoretic limits of control** (Touchette & Lloyd, PRL 84, 1156
  (2000), arXiv chao-dyn/9905039 — verified to exist, not shortlisted): each
  bit a controller gathers buys at most one bit of entropy reduction. General
  and citable, but it bounds *authority given information*, not *information
  given a decorrelating observable*, so it does not name our obstruction. The
  cybernetics ancestor (Ashby's law of requisite variety; Conant & Ashby 1970)
  has the same character. Mentioned for completeness; I would not lead with it.

**Also found and verified but NOT shortlisted** (real, relevant, second tier —
pull in if the section needs more mass):
- Rohlf & Bornholdt, *Self-organized criticality and adaptation in discrete
  dynamical networks*, arXiv:0811.0980 (review chapter, Adaptive Networks,
  Springer 2009) — best single entry point to the whole family; both rules
  ("active nodes lose links / frozen nodes gain links" and "correlated nodes
  connect / decorrelated disconnect") in one place.
  https://arxiv.org/abs/0811.0980
- Liu & Bassler, *Emergent criticality from coevolution in random Boolean
  networks*, PRE 74, 041910 (2006), arXiv:cond-mat/0605020 — coevolving
  topology+dynamics in Boolean networks converges to ⟨K⟩=2, "independent of
  initial conditions", with large finite-size deviations (⟨K⟩ between 2 and 3
  at biological sizes). Closest *substrate* match (Boolean rules on a network),
  and the finite-size caveat rhymes with our finite-size crossover. I could not
  verify the exact measurement the rule makes (whether topology updates only
  after the dynamics reaches an attractor, which would be an infinite
  timescale separation) — the arXiv PDF did not extract. Verify before citing
  on that point. https://arxiv.org/abs/cond-mat/0605020
- Das & Levina, *Critical Neuronal Models with Relaxed Timescale Separation*,
  PRX 9, 021062 (2019), arXiv:1808.04196 — what happens when the timescale
  separation SOC models assume is relaxed (exponent moves 1.5 → ~1.3). Adjacent
  to question (2) but concerns drive-during-avalanche, not local estimation
  windows. https://arxiv.org/abs/1808.04196

**Searches run** (so the gap is auditable): Bornholdt–Rohlf topological
evolution / global criticality from local dynamics; Bornholdt–Röhl
self-organized critical neural networks; self-organized criticality adaptive
networks review; Bonachela–Muñoz self-organization without conservation;
self-organized quasicriticality homeostatic hovering neuronal avalanches;
paths to self-organized criticality Dickman/Muñoz/Vespignani/Zapperi;
Pruessner–Peters absorbing states lessons from the Ising model; mapping SOC
onto criticality Sornette; Melby/Hübler self-adjusting logistic map edge of
chaos; Melby/Weber/Hübler self-adjusting systems with noise; Liu–Bassler
coevolution random Boolean networks; Boettiger–Hastings limits to detection of
early warning; Touchette–Lloyd information-theoretic limits of control; Wilting
–Priesemann inferring collective dynamical states (subsampling); "local
controller cannot estimate order parameter finite observation window timescale
separation".

**Clean negative worth recording:** I found **no** work that attempts, and
reports failing at, a local self-tuning rule for *coexistence of a frozen and a
changing phase* (as opposed to tuning to a critical point). The positives all
target a *point* (K_c = 2, branching ratio 1, λ at the order–disorder
transition); nobody in this literature is trying to hold a two-phase mixture.
That is a real gap our Part III sits in, and it is worth one sentence in the
Discussion.

---

## Items

### E1. Bornholdt & Rohlf (2000) — the canonical "local rule → global criticality"

- **Citation:** S. Bornholdt and T. Rohlf, "Topological Evolution of Dynamical
  Networks: Global Criticality from Local Dynamics", *Physical Review Letters*
  **84**(26), 6114–6117 (2000).
- **Identifier:** doi:10.1103/PhysRevLett.84.6114 · arXiv:cond-mat/0003215
- **URL seen:** https://arxiv.org/abs/cond-mat/0003215
- **What it does:** Threshold networks with asymmetric connections are evolved
  by a purely local rewiring rule — "quiet nodes grow links, active nodes lose
  links". The average connectivity converges to the critical value K_c = 2, and
  finite-size scaling is used to argue convergence to a self-organized critical
  state *in the thermodynamic limit*. Discussed for neural and genetic
  regulatory networks.
- **Relevance:** Part III. This is the positive claim our negative is measured
  against; a reviewer will name it first. Note the two places global
  information enters: the target K_c = 2 is only reached as N → ∞ (finite N
  gives systematic deviation — same structural situation as our finite-size
  crossover in Part I), and the node's "quiet/active" verdict is an average
  over an observation window.
- **Tier:** must-cite. **Contradicts:** no.

### E2. Bornholdt & Röhl (2003) — the authors state the smuggled quantity themselves

- **Citation:** S. Bornholdt and T. Röhl, "Self-organized critical neural
  networks", *Physical Review E* **67**(6), 066118 (2003).
- **Identifier:** doi:10.1103/PhysRevE.67.066118 · arXiv:cond-mat/0109256
- **URL seen:** https://arxiv.org/abs/cond-mat/0109256
- **What it does:** A rewiring rule modelled on synaptic development —
  correlated neighbours connect, decorrelated neighbours disconnect — drives the
  network to the order–disorder transition, "robust against thermal noise" and
  without fine tuning of parameters. The abstract's own description of the
  mechanism: *"network connectivity is regulated locally on the basis of an
  order parameter of the global dynamics."*
- **Relevance:** Part III, and it is the direct answer to our question (1): the
  rule is local in *implementation* but its input is a local estimate of a
  **global order parameter** — the exact resource our cells do not have. The
  correlation-over-a-window observable is also precisely the class of locally
  computable quantity our Part III tests and rejects, so this is the sharpest
  contrast to draw.
- **Tier:** must-cite. **Contradicts:** no.

### E3. Droste, Do & Gross (2013) — NEAR-MISS. Read this one before writing the Discussion.

- **Citation:** F. Droste, A.-L. Do and T. Gross, "Analytical investigation of
  self-organized criticality in neural networks", *Journal of the Royal Society
  Interface* **10**(78), 20120558 (2013).
- **Identifier:** doi:10.1098/rsif.2012.0558 · arXiv:1203.4942
- **URL seen:** https://pmc.ncbi.nlm.nih.gov/articles/PMC3565782/ (also
  https://arxiv.org/abs/1203.4942)
- **What it does:** Shows *analytically* that adding activity-dependent rewiring
  rules inspired by homeostatic plasticity creates an **attractive steady state
  at criticality**, with numerical confirmation. Crucially it also derives
  *why*: only firing nodes carry information about the global phase (firing
  occurs only in the active phase; resting occurs in both), so the rule must
  create links tentatively and destroy them decisively, which requires the rate
  condition l ≪ g.
- **Relevance:** Part III. It is the strongest published counter-example to an
  unqualified reading of "no locally computable quantity self-tunes the
  system", AND it supplies the vocabulary for our obstruction ("limited local
  accessibility of the global phase").
- **Tier:** must-cite. **Contradicts:** **YES — flagged loudly.**
- **Contradiction note:** It does not touch the measured claim (our
  constructions, our operators, our selection rule): they tune to a *critical
  point* in an adaptive network, not to *coexistence of two phases on a
  lattice*, and their rewiring is a network operation we do not have. But any
  unscoped phrasing in Part III — "no local rule can self-tune", "local
  information is insufficient" — is refuted by this paper and must be
  retracted or explicitly scoped. It also names a design we apparently did not
  test: an **asymmetric, one-sided** rule (tentative in one direction, decisive
  in the other, with separated rates), driven by an observable that is a
  one-sided certificate rather than a two-sided estimate. If our tested
  quantities were all symmetric/estimator-shaped, say so, because that is the
  difference.

### E4. Bonachela & Muñoz (2009) — self-organization without conservation is only *quasi*-critical

- **Citation:** J. A. Bonachela and M. A. Muñoz, "Self-organization without
  conservation: true or just apparent scale-invariance?", *Journal of
  Statistical Mechanics: Theory and Experiment* **2009**(09), P09009 (2009).
- **Identifier:** doi:10.1088/1742-5468/2009/09/P09009 · arXiv:0905.1799
- **URL seen:** https://arxiv.org/abs/0905.1799 (publisher:
  https://iopscience.iop.org/article/10.1088/1742-5468/2009/09/P09009)
- **What it does:** Using mean field, self-organized branching processes and a
  full Langevin description, argues on general grounds that **non-conserving**
  slowly driven dynamics does not produce bona fide criticality: a "recharging"
  rate (the tree-growth rate in forest-fire models, etc.) must be fine-tuned.
  Coins **self-organized quasi-criticality (SOqC)** for what you get instead —
  apparent scale invariance, hovering around the critical region.
- **Relevance:** Part I and Part III. Our system has no conservation law, and
  our finding is a broad crossover rather than a critical point plus a
  coexistence that no local rule holds — this paper is the general theoretical
  reason to *expect* that, arrived at independently in a different substrate.
  Strongest support item in the lane.
- **Tier:** must-cite. **Contradicts:** no (it supports us).

### E5. Pruessner & Peters (2006) — the "SOC is really tuned criticality" argument, in its sharpest form

- **Citation:** G. Pruessner and O. Peters, "Self-organized criticality and
  absorbing states: Lessons from the Ising model", *Physical Review E* **73**(2),
  025106(R) (2006).
- **Identifier:** doi:10.1103/PhysRevE.73.025106 · arXiv:cond-mat/0411709
- **URL seen:** https://arxiv.org/abs/cond-mat/0411709
- **What it does:** Examines the standard path to SOC (drive slowly, dissipate
  slowly, at an absorbing-state transition) and shows the mechanism is not
  specific to absorbing states at all — it works for *any* continuous phase
  transition, demonstrated on the **Ising** model as well as the Manna model.
  The resulting finite-size scaling exponents depend on **how driving and
  dissipation rates are tuned with system size**, which limits the mechanism's
  explanatory power to non-universal critical behaviour.
- **Relevance:** Part III (and Part I's "no critical point in a finite-size
  scan"). This is the cleanest citation for "the published self-tuning
  mechanisms smuggle in N": a rule that needs the system size is not local.
- **Tier:** must-cite. **Contradicts:** no.

### E6. Sornette, Johansen & Dornic (1995) — SOC = order-parameter feedback + vanishing drive

- **Citation:** D. Sornette, A. Johansen and I. Dornic, "Mapping self-organized
  criticality onto criticality", *Journal de Physique I (France)* **5**(3),
  325–335 (1995).
- **Identifier:** arXiv:adap-org/9411002 (journal DOI not verified — see note)
- **URL seen:** https://arxiv.org/abs/adap-org/9411002 (journal landing page
  https://jp1.journaldephysique.org/fr/articles/jp1/abs/1995/03/jp1v5p325/jp1v5p325.html
  returned 403 to the fetcher but confirms volume 5, page 325)
- **What it does:** Proposes the unifying reading of SOC as "nothing but the
  expression of an underlying unstable dynamical critical point": tune the
  *order parameter* to a small positive value and the *control parameter* sits
  at criticality. Explains why every SOC system has an extremely slow drive.
  Covers sandpiles, earthquakes, depinning, fractal growth, forest fires.
- **Relevance:** Part III. The canonical statement that the "self" in
  self-organized criticality is a coordinate change, not a free lunch — the
  feedback loop still needs the order parameter and still needs a drive rate
  tuned to zero. Use it as the framing sentence for why our negative is the
  expected outcome once you refuse those two ingredients.
- **Tier:** must-cite. **Contradicts:** no.
- **Verification caveat:** existence, authors, title and volume/page verified;
  I did **not** verify the journal DOI and have deliberately omitted it from the
  BibTeX rather than guess.

### E7. Melby, Kaidel, Weber & Hübler (2000) — the edge-of-chaos-vocabulary positive

- **Citation:** P. Melby, J. Kaidel, N. Weber and A. Hübler, "Adaptation to the
  Edge of Chaos in the Self-Adjusting Logistic Map", *Physical Review Letters*
  **84**(26), 5991–5993 (2000).
- **Identifier:** doi:10.1103/PhysRevLett.84.5991 · arXiv:nlin/0007006
- **URL seen:** https://arxiv.org/abs/nlin/0007006
- **What it does:** Treats the control parameter of a system as a slow variable
  driven by **low-pass filtered feedback from the dynamical variables**. In the
  logistic map, the parameter leaves the chaotic regime and is found with high
  probability at the periodicity/chaos boundary — "adaptation to the edge of
  chaos".
- **Relevance:** Part I and Part III. This is the *edge-of-chaos* (rather than
  SOC) phrasing of the positive claim, and its mechanism is a low-pass filter,
  i.e. an **averaging window** — the same design our decorrelation-time
  obstruction says cannot work when the observable decorrelates inside the
  window. Cite it as the positive, then cite E8 as what happens to it.
- **Tier:** enriches. **Contradicts:** no.

### E8. Melby, Weber & Hübler (2005) — the adaptation is a transient once you add noise

- **Citation:** P. Melby, N. Weber and A. Hübler, "Dynamics of self-adjusting
  systems with noise", *Chaos: An Interdisciplinary Journal of Nonlinear
  Science* **15**(3), 033902 (2005).
- **Identifier:** doi:10.1063/1.1953147 · PMID 16252993
- **URL seen:** https://pubmed.ncbi.nlm.nih.gov/16252993/
- **What it does:** Analytical, numerical and experimental study of the same
  self-adjusting systems with noise added. Finding: **adaptation to the edge of
  chaos is only a long-lived transient** when noise is present; the parameter
  dynamics is a rescaled diffusion, and noise produces "chaotic outbreaks" —
  re-entries into the chaotic regime — whose lengths are power-law distributed.
- **Relevance:** Part III. Two direct hooks: (a) it is the same
  horizon-dependence our Part III reports — an apparently held mixed state that
  is actually a long transient, which is why "coexistence *for the full
  horizon*" is the right way to phrase our result; (b) it is prior art for
  "undirected noise changes the character of the held state rather than
  stabilising it", the closest published analogue of our noise-foam arm.
- **Tier:** must-cite. **Contradicts:** no (it strongly supports us, in a
  different substrate).

### E9. Boettiger & Hastings (2012) — quantitative detection limits for a finite-window indicator

- **Citation:** C. Boettiger and A. Hastings, "Quantifying limits to detection
  of early warning for critical transitions", *Journal of the Royal Society
  Interface* **9**(75), 2527–2539 (2012).
- **Identifier:** doi:10.1098/rsif.2012.0125 · arXiv:1204.6231
- **URL seen:** https://arxiv.org/abs/1204.6231 (publisher:
  https://royalsocietypublishing.org/rsif/article/9/75/2527/278/Quantifying-limits-to-detection-of-early-warning)
- **What it does:** Points out that proposed early-warning indicators (rising
  variance, increased return times / critical slowing down) "hardly ever
  characterize their expected error rates", and supplies a model-based
  framework that quantifies the sensitivity/reliability trade-off. Result:
  **error rates are severe for common indicators even under favourable
  assumptions.**
- **Relevance:** Part III — the closest quantitative counterpart to our
  decorrelation-time obstruction. It is the statement, made precise elsewhere,
  that a statistic computed from a finite observation window has a detection
  power, and that the power of the standard indicators is low. Cite it when
  stating that the obstruction is a general feature of window-based local
  indicators, not an artefact of our particular quantities.
- **Tier:** enriches. **Contradicts:** no.
- **Caveat for honesty:** it is an ecology/regime-shift paper about
  *forecasting* a transition, not about a *controller* holding a state. It is
  an analogy, a good one, but do not present it as a theorem about local
  control.

---

## BibTeX

```bibtex
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

@article{droste2013analytical,
  author  = {Droste, F. and Do, A.-L. and Gross, T.},
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

@article{bonachela2009soqc,
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
  eprint  = {adap-org/9411002},
  archivePrefix = {arXiv},
  note    = {journal DOI not verified by the researcher; arXiv id verified}
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

@article{melby2005noise,
  author  = {Melby, Paul and Weber, Nicholas and H{\"u}bler, Alfred},
  title   = {Dynamics of self-adjusting systems with noise},
  journal = {Chaos},
  volume  = {15},
  number  = {3},
  pages   = {033902},
  year    = {2005},
  doi     = {10.1063/1.1953147}
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
```

## Second-tier BibTeX (verified to exist; not shortlisted)

```bibtex
@incollection{rohlf2009adaptive,
  author    = {Rohlf, Thimo and Bornholdt, Stefan},
  title     = {Self-organized criticality and adaptation in discrete dynamical networks},
  booktitle = {Adaptive Networks: Theory, Models and Applications},
  publisher = {Springer},
  year      = {2009},
  eprint    = {0811.0980},
  archivePrefix = {arXiv}
}

@article{liu2006emergent,
  author  = {Liu, Min and Bassler, Kevin E.},
  title   = {Emergent criticality from coevolution in random Boolean networks},
  journal = {Physical Review E},
  volume  = {74},
  number  = {4},
  pages   = {041910},
  year    = {2006},
  doi     = {10.1103/PhysRevE.74.041910},
  eprint  = {cond-mat/0605020},
  archivePrefix = {arXiv}
}

@article{das2019relaxed,
  author  = {Das, Anirban and Levina, Anna},
  title   = {Critical Neuronal Models with Relaxed Timescale Separation},
  journal = {Physical Review X},
  volume  = {9},
  number  = {2},
  pages   = {021062},
  year    = {2019},
  doi     = {10.1103/PhysRevX.9.021062},
  eprint  = {1808.04196},
  archivePrefix = {arXiv}
}
```
