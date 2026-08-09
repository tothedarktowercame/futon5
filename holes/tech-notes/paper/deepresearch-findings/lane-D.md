# Lane D — Absorbing states and directed percolation

DeepResearch findings, 2026-08-08. Lane owner: subagent D. Input brief:
`../deepresearch-related-work-brief.md`.

## Lane notes (read this first)

The literature here is **not empty — it is dense, mature, and it contains a
direct threat to our Part I negative.** That threat is the headline finding of
this lane and should be dealt with explicitly in the paper, not softened.

**The threat, stated plainly.** We report "no critical point in a finite-size
scan, only a broad crossover" and treat that as evidence *against* a critical
point. The disorder / absorbing-state literature says that a broad crossover
with drifting effective exponents is precisely the **expected finite-size
signature of a genuine transition** when the update rule carries quenched
randomness. Quenched spatial disorder is a *relevant* perturbation at the DP
fixed point in d=1 (Harris criterion: DP has nu_perp ~ 1.097 < 2/d = 2, so
disorder is relevant), and the flow is to an **infinite-randomness fixed point
with activated dynamical scaling**, ln(t_corr) ~ L^psi rather than
t_corr ~ L^z. Under activated scaling the correlation time is a *stretched
exponential* in system size, so at any size a naive scan sees (a) an extended
Griffiths region of continuously varying, non-universal exponents on the
inactive side, and (b) an apparently smeared, size-drifting transition point.
Vojta–Farquhar–Mast needed 8000 x 8000 lattices and times to 10^10 to resolve
the 2d disordered contact process — that is the scale at which "broad crossover"
resolves into a critical point. Items **D2, D3, D7** are the load-bearing ones.
A stat-phys referee will raise this; the paper is stronger if it raises it first
and says what would distinguish the two readings (test for *activated* scaling —
plot vs ln t — and for power-law rather than exponential size dependence of
lifetimes; look for continuously varying decay exponents off-transition).

**The counterweight (supports our reading).** Disorder can also genuinely
*destroy* the sharp transition. Vojta 2004 (**D4**) shows that when the quenched
defects are spatially **extended / correlated** rather than point-like, rare
regions order independently of the bulk and the DP transition is **smeared** —
there is no sharp critical point at all, only a broad inhomogeneous regime. Our
frozen refuges/islands ("foam") are exactly extended, correlated, self-generated
defects. So "no critical point, broad crossover" is a physically realised
outcome in this literature — but it is attributed to a specific mechanism
(extended defects / rare-region smearing), not to the absence of a transition
per se. Citing D4 alongside D2/D3/D7 lets the paper say: *both readings are live
in the literature; here is which one our data is compatible with, and here is
what we did not have the sizes to decide.*

**On the lane's specific question — "rule co-evolves with the state".** There is
prior work, but it is in three separate dialects and none of it is CA-rule
rewriting:
- *Quenched* rule disorder in CA: Noest 1986 (**D5**) is the original — spatially
  disordered stochastic CA are *incompatible* with DP exponents; new universal
  exponents. This is the closest classical statement of "randomness in the CA
  rule field changes the universality class."
- *Annealed / state-coupled* substrate: adaptive-network epidemics (**D8**),
  where the substrate rewires in response to the state. Result: the continuous
  absorbing transition becomes **first order with bistability and hysteresis**.
  This is a *competing reading of our own data* — a discontinuous transition with
  hysteresis also presents as "no critical point", and would be diagnosed by
  hysteresis/bistability tests we can actually run. Flagged as a contradiction.
- *Temporally* varying rates: Temporal Griffiths phases (**D11**) — a fluctuating
  environment (which a co-evolving rule field is) produces generic power laws and
  anomalous size scaling over an *extended region*, again a broad zone rather
  than a point.
- Local vs nonlocal feedback: Ha–Huse–Samajdar (**D13**, 2025/26 preprint) —
  under purely *local* update rules branching is relevant and no robust absorbing
  phase survives; **nonlocal information in the feedback is what stabilises it**.
  Same locality obstruction shape as our Part III negative, from the other
  direction.

**Clean negative to report:** I found **no** work in which the *local update
rule itself* (a rule table, as opposed to a rate, a bond, or an edge) co-evolves
with the state and the resulting absorbing-state transition is characterised.
Searched vocabularies: "co-evolving rule cellular automaton absorbing
transition", "evolving update rules lattice directed percolation", "adaptive
local rates smears absorbing transition", "self-organized directed percolation",
"rule-changing CA phase transition", plus the disorder/Griffiths/DP corpus
below. The nearest neighbours are D5 (quenched rule disorder), D8 (state-coupled
substrate), D6 (a *parameter* — the synchrony rate — swept through ECA rule
space). Our construction appears to be new *in this literature too*, which is a
citable gap, but the disorder machinery above applies to it whether or not
anyone has applied it.

**Also useful for Part II:** damage spreading transitions are generically in the
DP class (**D12**) — our fork-and-flip damage reach is a damage-spreading
measurement, and a referee may ask for the DP-class framing.

**Also useful for Part III / lane E overlap:** the self-organisation literature
(**D9**) is explicit that non-conserving local feedback does **not** produce
bona fide criticality — only "self-organized quasi-criticality", hovering around
the transition with a width that requires fine-tuning of the recharge rate. This
is a named, published counterpart to our "no locally computable quantity finds
or holds the coexistence."

Tier counts: 6 must-cite, 6 enriches. 3 flagged as contradicting/threatening.

---

## D1. Hinrichsen 2000 — the reference review for absorbing-state transitions

- **Citation:** Haye Hinrichsen, "Nonequilibrium critical phenomena and phase
  transitions into absorbing states", *Advances in Physics* 49 (2000) 815–958.
- **Identifier:** arXiv:cond-mat/0001070
- **URL seen:** https://arxiv.org/abs/cond-mat/0001070
- **What it does:** The standard review of phase transitions from a fluctuating
  active phase into absorbing states. Covers the DP universality class in
  detail, the Janssen–Grassberger conditions under which DP is expected (short
  range interactions, a unique absorbing state, a non-negative one-component
  order parameter, no extra symmetry or conservation law, no quenched disorder),
  lattice models including probabilistic CA, numerical methodology for locating
  absorbing transitions, damage spreading, and the classes outside DP.
- **Relevance:** Part I — the canonical citation for calling our freezing
  transition an absorbing-state transition at all, and the source for the
  checklist of DP conditions our system violates (quenched-ish disorder in the
  rule field; many absorbing configurations, not one).
- **Tier:** must-cite. **Contradicts:** no. **Verification:** seen-on-page.

## D2. Vojta 2006 — rare regions, Griffiths phases, smearing (THREAT)

- **Citation:** Thomas Vojta, "Rare region effects at classical, quantum and
  nonequilibrium phase transitions", *J. Phys. A: Math. Gen.* 39 (2006)
  R143–R205.
- **Identifier:** doi:10.1088/0305-4470/39/22/R01 ; arXiv:cond-mat/0602312
- **URL seen:** https://arxiv.org/abs/cond-mat/0602312
- **What it does:** Topical review unifying rare-region effects at disordered
  classical, quantum and *nonequilibrium* transitions by the effective
  dimensionality of the rare regions. Three regimes: conventional critical
  behaviour with Griffiths singularities; infinite-randomness criticality with
  activated scaling and power-law Griffiths phases; and complete **smearing**
  of the transition. The disordered contact process is one of the worked
  examples.
- **Relevance:** Part I, directly — it is the single citation that tells a
  referee *both* that a broad crossover can be the finite-size face of an
  infinite-randomness critical point (threat to our negative) and that disorder
  can genuinely destroy the sharp transition (support). It also supplies the
  vocabulary (Griffiths phase, activated scaling, smearing) our Part I crossover
  discussion currently lacks.
- **Tier:** must-cite. **Contradicts:** **YES.**
  **Contradiction note:** undercuts "no critical point in a finite-size scan,
  only a broad crossover" as evidence *against* a critical point. In this review's
  framework, an extended parameter region of continuously varying exponents plus
  a size-drifting apparent transition is the *predicted* observation for a
  disordered system that nonetheless has a sharp infinite-randomness critical
  point. Our finite-size scan cannot, on its own, distinguish "no transition"
  from "transition with activated dynamical scaling".

## D3. Hooyberghs–Iglói–Vanderzande 2004 — infinite-randomness fixed point in absorbing transitions (THREAT)

- **Citation:** Jef Hooyberghs, Ferenc Iglói, Carlo Vanderzande, "Absorbing state
  phase transitions with quenched disorder", *Phys. Rev. E* 69 (2004) 066140.
  (Letter version: same authors, "Strong disorder fixed point in absorbing-state
  phase transitions", *Phys. Rev. Lett.* 90 (2003) 100601.)
- **Identifier:** doi:10.1103/PhysRevE.69.066140 ; arXiv:cond-mat/0402086
- **URL seen:** https://arxiv.org/abs/cond-mat/0402086
- **What it does:** Strong-disorder RG plus DMRG/numerics for DP-class models
  with random transition rates. Sufficiently strong disorder drives the critical
  behaviour to a **strong-disorder (infinite-randomness) fixed point**,
  isomorphic to the random transverse-field Ising fixed point, with
  logarithmically slow dynamical correlations and (in 1d) conjecturally exact
  exponents beta = (3 - sqrt 5)/2, nu_perp = 2. Weaker disorder gives
  disorder-dependent, continuously varying exponents.
- **Relevance:** Part I — this is the mechanism behind the threat in D2, stated
  concretely for absorbing transitions: *any* quenched randomness in the local
  update makes the dynamics logarithmically slow near criticality, so a scan at
  accessible sizes and times will not see clean critical scaling. It is also the
  right citation for "disorder in the rule need not destroy the transition, it
  can change its class."
- **Tier:** must-cite. **Contradicts:** **YES.**
  **Contradiction note:** provides a concrete, published alternative to our
  reading: a genuine critical point exists, but with logarithmically slow
  (activated) dynamics that a finite-size, finite-time scan renders as a broad
  featureless crossover. Distinguishing test: check whether the data collapse
  under activated scaling (ln t vs L^psi), which is not the collapse we tried.

## D4. Vojta 2004 — extended defects smear the DP transition away entirely (SUPPORT)

- **Citation:** Thomas Vojta, "Broadening of a nonequilibrium phase transition by
  extended structural defects", *Phys. Rev. E* 70 (2004) 026108.
- **Identifier:** doi:10.1103/PhysRevE.70.026108 ; arXiv:cond-mat/0402606
- **URL seen:** https://arxiv.org/abs/cond-mat/0402606
- **What it does:** Studies DP-class nonequilibrium transitions with quenched
  **extended** (spatially correlated, e.g. linear/planar) impurities. Result:
  the impurities "completely destroy the sharp phase transition by smearing" —
  rare strongly-coupled regions order independently of the bulk, so the order
  parameter turns on inhomogeneously over a finite parameter window rather than
  at a point. Characterised by extremal statistics plus simulation.
- **Relevance:** Part I — the positive precedent for our reading. It says a broad
  crossover instead of a critical point is a *real* outcome in absorbing-state
  systems, and it names the mechanism (extended/correlated quenched defects with
  independently active rare regions), which is structurally what our frozen
  islands / "foam" of refuges are.
- **Tier:** must-cite. **Contradicts:** no (supports).
  Caveat for scope discipline: their defects are *externally imposed and
  quenched*; ours are self-generated by the dynamics. Cite as a precedent for the
  phenomenon, not as an explanation we have established.

## D5. Noest 1986 — spatially disordered stochastic CA are not in the DP class

- **Citation:** A. J. Noest, "New universality for spatially disordered cellular
  automata and directed percolation", *Phys. Rev. Lett.* 57 (1986) 90.
  (Companion: "Power-law relaxation of spatially disordered stochastic cellular
  automata and directed percolation", *Phys. Rev. B* 38 (1988) 2715.)
- **Identifier:** doi:10.1103/PhysRevLett.57.90
- **URL seen:** https://api.semanticscholar.org/graph/v1/paper/DOI:10.1103/PhysRevLett.57.90
  (publisher page https://journals.aps.org/prl/abstract/10.1103/PhysRevLett.57.90
  returns HTTP 403 to the fetcher; ADS bibcode 1986PhRvL..57...90N appeared in
  search results with the same title/venue/year)
- **What it does:** Stochastic CA with fixed but randomly chosen local
  probabilities (quenched spatial disorder) in D dimensions. At zero disorder
  these reduce to (D+1)-dimensional directed percolation; **finite spatial
  disorder is incompatible with the DP exponents**, and Monte Carlo in D=1,2
  gives new universal exponents. The 1988 companion reports power-law
  (Griffiths-like) relaxation.
- **Relevance:** Part I — the earliest and cleanest statement that randomness in
  a *cellular automaton's local rule* moves the absorbing transition out of the
  DP class. Our lattice carries a spatially varying rule field, so any expectation
  of clean DP behaviour was never warranted; this is the citation for that.
- **Tier:** must-cite. **Contradicts:** no (but undercuts any implicit assumption
  that DP exponents are the right null).
- **Verification:** *uncertain* — title, venue, volume, page, year and DOI
  confirmed on the Semantic Scholar index record and in search-result titles from
  APS and ADS, but the publisher page 403s and the index record lists the author
  surname only ("Noest"); the initials "A. J." come from search snippets, not
  from a page I loaded. Check initials before typesetting.

## D6. Fatès 2009 — DP transitions inside elementary-CA rule space

- **Citation:** Nazim Fatès, "Asynchronism induces second-order phase transitions
  in elementary cellular automata", *Journal of Cellular Automata* 4 (2009)
  21–38.
- **Identifier:** arXiv:nlin/0703044
- **URL seen:** https://arxiv.org/abs/nlin/0703044 (volume/pages from the
  Semantic Scholar record for the same arXiv id)
- **What it does:** Applies each ECA rule with probability alpha (the synchrony
  rate) per cell per step and sweeps alpha. Nine ECA rules show a genuine
  second-order phase transition; the measured exponents agree with **directed
  percolation**, with ECA 178 falling instead into the parity-conservation class
  because 0s and 1s play symmetric roles in its transition rule.
- **Relevance:** Parts I and III — the existing absorbing-state result *inside
  the same rule space we work in*, and the natural comparison point: there the
  tuned coordinate is an external update parameter (alpha) and the transition is
  sharp and DP; here the tuned object is the rule itself and the transition is
  broad. The ECA-178 exception is also the precedent for "a symmetry of the rule
  changes the universality class", which is the shape of our lambda = 1/2
  degeneracy argument.
- **Tier:** must-cite. **Contradicts:** no.
- **Verification:** seen-on-page. Minor discrepancy: the arXiv abstract page
  lists the journal ref as "Journal of Cellular Automata (2008)"; the indexed
  record is vol. 4, pp. 21–38 with a 2009 DBLP key. Use 2009, 4:21–38.

## D7. Vojta–Farquhar–Mast 2009 — the scale needed to resolve a disordered absorbing transition (THREAT)

- **Citation:** Thomas Vojta, Adam Farquhar, Jason Mast, "Infinite-randomness
  critical point in the two-dimensional disordered contact process", *Phys. Rev.
  E* 79 (2009) 011111.
- **Identifier:** doi:10.1103/PhysRevE.79.011111 ; arXiv:0810.1569
- **URL seen:** https://arxiv.org/abs/0810.1569
- **What it does:** Large-scale Monte Carlo for the 2d contact process with
  quenched disorder — times up to 10^10, lattices up to 8000 x 8000 — giving
  strong evidence for an **infinite-randomness critical point with activated
  (exponential) dynamical scaling** and universal exponents independent of
  disorder strength, matching a strong-disorder RG prediction.
- **Relevance:** Part I — the quantitative version of the threat. It fixes the
  order of magnitude of the sizes and times at which a disordered absorbing
  transition stops looking like a crossover. If our scan is far below that, the
  honest claim is "not resolvable at tested sizes", which is what the paper
  already says; this citation is what makes that phrasing defensible rather than
  weak.
- **Tier:** enriches (borderline must-cite if the crossover discussion is
  expanded). **Contradicts:** **YES** — same contradiction as D2/D3, but with
  numbers: a transition can be genuine and still invisible to a scan several
  orders of magnitude smaller in time and area.

## D8. Gross–D'Lima–Blasius 2006 — coupling the substrate to the state makes the absorbing transition first order (COMPETING READING)

- **Citation:** Thilo Gross, Carlos J. Dommar D'Lima, Bernd Blasius, "Epidemic
  dynamics on an adaptive network", *Phys. Rev. Lett.* 96 (2006) 208701.
- **Identifier:** doi:10.1103/PhysRevLett.96.208701 ; arXiv:q-bio/0512037
- **URL seen:** https://arxiv.org/abs/q-bio/0512037
- **What it does:** SIS epidemics where susceptible nodes rewire away from
  infected neighbours, so the interaction substrate co-evolves with the state.
  The continuous absorbing (DP-like) transition of the static model is replaced
  by **first-order transitions, hysteresis, bistability** (separate invasion and
  persistence thresholds) and oscillations; analysed by pair approximation and
  bifurcation analysis.
- **Relevance:** Parts I and III — the best-known worked case of exactly this
  lane's question, "what happens to an absorbing transition when the rule/substrate
  reads the state". Also the closest published analogue of our Part II finding
  that reading the current state field changes the causal structure of the
  dynamics.
- **Tier:** enriches. **Contradicts:** **YES (interpretive).**
  **Contradiction note:** offers a rival explanation of our Part I/III data that
  we have not excluded — state-coupled dynamics classically converts the
  continuous transition into a **discontinuous** one with bistability and
  hysteresis. A first-order transition also presents as "no critical point", but
  it *is* a transition, and it is diagnosable at our sizes by hysteresis loops
  (sweep up vs sweep down) and by bimodality of the order parameter. Our Part III
  "three outcomes including full-horizon coexistence" is suggestively bistable.
  If we do not test for hysteresis, a referee can say the crossover claim is
  under-determined.

## D9. Bonachela–Muñoz 2009 — local non-conserving feedback gives only quasi-criticality (SUPPORT)

- **Citation:** Juan A. Bonachela, Miguel A. Muñoz, "Self-organization without
  conservation: true or just apparent scale-invariance?", *J. Stat. Mech.* (2009)
  P09009.
- **Identifier:** arXiv:0905.1799 ; doi:10.1088/1742-5468/2009/09/P09009
- **URL seen:** https://arxiv.org/abs/0905.1799
- **What it does:** Asks whether slowly driven models *without* a conservation
  law (forest-fire, earthquake automata) are genuinely critical. Mean field,
  self-organized branching process, and Langevin analyses all conclude that
  non-conserving dynamics does **not** produce bona fide criticality: the
  recharge rate has to be fine-tuned. They name the weaker phenomenon
  **self-organized quasi-criticality (SOqC)** — hovering around the transition
  rather than sitting on it.
- **Relevance:** Part III (and lane E) — the published counterpart to our central
  negative. It is the standard citation for "a local feedback that adjusts the
  control parameter from local activity does not self-tune to a critical point
  unless something is conserved or fine-tuned", which is precisely the failure
  mode our tested local quantities exhibit.
- **Tier:** must-cite. **Contradicts:** no (supports).

## D10. Dickman–Muñoz–Vespignani–Zapperi 2000 — SOC *is* an absorbing transition plus a drive

- **Citation:** Ronald Dickman, Miguel A. Muñoz, Alessandro Vespignani, Stefano
  Zapperi, "Paths to self-organized criticality", *Brazilian Journal of Physics*
  30 (2000) 27–41.
- **Identifier:** arXiv:cond-mat/9910454
- **URL seen:** https://arxiv.org/abs/cond-mat/9910454
- **What it does:** Pedagogical account of SOC as an absorbing-state phase
  transition (DP being the familiar example) plus an imposed supervision — slow
  driving, extremal dynamics, or a conserved density. Covers sandpiles, driven
  interfaces, Bak–Sneppen, and self-organized directed percolation.
- **Relevance:** Part III / memory-search — the frame that says any "the system
  finds its own critical point" story requires naming the supervisory channel
  (slow drive, dissipation, conservation, extremal selection). Useful for
  articulating what our per-cell selection rule *is* as a supervision mechanism
  and why it is not one of the known ones.
- **Tier:** enriches. **Contradicts:** no.

## D11. Vázquez–Bonachela–López–Muñoz 2011 — temporal disorder makes a broad region, not a point

- **Citation:** Federico Vázquez, Juan A. Bonachela, Cristóbal López, Miguel A.
  Muñoz, "Temporal Griffiths phases", *Phys. Rev. Lett.* 106 (2011) 235702.
- **Identifier:** arXiv:1105.3562
- **URL seen:** https://arxiv.org/abs/1105.3562
- **What it does:** Absorbing-state models whose rates fluctuate in **time**
  (temporal disorder). For d >= 2 there are Temporal Griffiths Phases with
  generic power-law spatial scaling and generic divergences of the
  susceptibility over an extended region of the active phase — space and time
  playing reversed roles relative to ordinary Griffiths phases; finite-sample
  lifetimes acquire anomalous power-law dependence on system size instead of the
  usual exponential.
- **Relevance:** Part I — a rule field that is rewritten every step *is* a
  temporally fluctuating environment. This supplies a second named mechanism by
  which our system would show an extended anomalous region rather than a point,
  and a concrete diagnostic we could run: whether frozen-state lifetimes scale as
  a power of L (Griffiths-like) or exponentially (ordinary active phase).
- **Tier:** enriches. **Contradicts:** no, but it offers a rival *label*: what we
  call a featureless broad crossover may be a Griffiths-like phase with generic
  power laws, which is a stronger and testable claim than "crossover".

## D12. Grassberger 1995 — damage spreading transitions are generically DP

- **Citation:** Peter Grassberger, "Are damage spreading transitions generically
  in the universality class of directed percolation?", *Journal of Statistical
  Physics* 79 (1995) 13–23.
- **Identifier:** doi:10.1007/BF02179381 ; arXiv:cond-mat/9409068
- **URL seen:** https://api.semanticscholar.org/graph/v1/paper/DOI:10.1007/BF02179381
  (the Springer landing page redirects to an auth endpoint and could not be
  fetched)
- **What it does:** Argues, with simulations, that the damage-spreading
  transition found by Martins et al. in the Domany–Kinzel probabilistic CA is in
  the DP universality class, and conjectures that damage-spreading transitions
  generally are, subject to two provisos: the transition must not coincide with
  another phase transition (as it does for Glauber–Ising), and the probability
  for a locally damaged site to heal must be nonzero.
- **Relevance:** Part II — our fork-and-flip damage reach is a damage-spreading
  measurement on a CA, and this is the citation that places such measurements in
  the absorbing-transition framework and states the conditions under which the
  DP reading applies (the healing proviso is relevant to us: frozen regions do
  not heal, which is exactly the excluded case).
- **Tier:** enriches. **Contradicts:** no.
- **Verification:** seen-on-page via the Semantic Scholar index record (title,
  authors, journal, volume, pages, DOI, arXiv id, abstract). Note the index
  record gives year 1994 (arXiv posting); the journal issue is 1995.

## D13. Ha–Huse–Samajdar 2025/26 — no robust absorbing phase under purely local rules

- **Citation:** Hyunsoo Ha, David A. Huse, Rhine Samajdar, "Absorbing state
  transitions with discrete symmetries", arXiv preprint (submitted 12 Feb 2025;
  v2 27 Jan 2026).
- **Identifier:** arXiv:2502.08702
- **URL seen:** https://arxiv.org/abs/2502.08702
- **What it does:** Asks whether stable absorbing phases exist in 1d classical
  stochastic systems with discrete symmetries, where domain walls coarsen under
  local update rules and imperfect feedback causes branching. Finds that
  **branching is a relevant perturbation, ruling out a robust absorbing phase
  under purely local rules**; incorporating **nonlocal** information into the
  feedback stabilises the absorbing phase and yields a new active–absorbing
  universality class.
- **Relevance:** Part III — a very recent, independent statement of the same
  locality obstruction our negative result reports, from the opposite direction
  (there: local feedback fails to *hold* the absorbing phase; here: local
  quantities fail to *avoid* it). It also supplies the honest counterpoint to our
  boundary-copying failure — the literature's known fix is nonlocal information,
  which our construction deliberately does not have.
- **Tier:** enriches. **Contradicts:** no.
- **Verification:** seen-on-page. Preprint — check for a journal version before
  final submission.

---

## Searched and not found (auditable gap)

Queries run (WebSearch), all returning nothing closer than the items above:

- "cellular automaton rule co-evolves with state absorbing phase transition
  freezing directed percolation evolving update rules lattice"
- "contact process / SIS with adaptive local rates rule feedback smears or
  destroys the absorbing phase transition continuously varying exponents"
- "self-organized directed percolation feedback control parameter coupled to
  order parameter Grassberger Zhang"
- "absorbing state phase transitions quenched disorder cellular automata directed
  percolation Noest new universality"
- "infinitely many absorbing states critical behavior nonuniversal exponents"
- "damage spreading transition directed percolation universality class Domany–Kinzel"
- "Vojta rare region Griffiths smeared transitions"
- "Hooyberghs Iglói Vanderzande strong disorder fixed point"
- "Fatès asynchronous elementary cellular automata directed percolation"
- "adaptive network epidemic coevolving topology absorbing transition
  discontinuous bistability"
- "temporal Griffiths phases temporal disorder"
- "Dickman Muñoz Vespignani Zapperi paths to self-organized criticality"

**Not found:** any model in which the *local rule table* is rewritten by the
dynamics and the resulting absorbing-state transition is characterised
(exponents, universality class, or the existence of a critical point). The
adjacent genres each vary something else: a rate (Noest, Hooyberghs), a synchrony
parameter (Fatès), an edge (Gross), or an environment in time (Vázquez).

Also checked and deliberately **not** recommended, to keep the lane strong rather
than broad: Jensen's dimer–trimer / infinitely-many-absorbing-states work
(arXiv:cond-mat/9405012 — concludes DP is generic for many-absorbing-state
models, so it weakens rather than sharpens any "many frozen configurations →
non-DP" argument we might have been tempted by; noting it here so nobody
re-derives that temptation), and the quantum/monitored-circuit absorbing-state
literature (different physics, would read as padding).

---

## BibTeX

```bibtex
@article{hinrichsen2000nonequilibrium,
  author  = {Hinrichsen, Haye},
  title   = {Nonequilibrium critical phenomena and phase transitions into absorbing states},
  journal = {Advances in Physics},
  volume  = {49},
  number  = {7},
  pages   = {815--958},
  year    = {2000},
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
  author  = {Hooyberghs, Jef and Igl\'{o}i, Ferenc and Vanderzande, Carlo},
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
  author  = {Noest, A. J.},
  title   = {New universality for spatially disordered cellular automata and directed percolation},
  journal = {Physical Review Letters},
  volume  = {57},
  number  = {1},
  pages   = {90--93},
  year    = {1986},
  doi     = {10.1103/PhysRevLett.57.90},
  note    = {Author initials not confirmed on a publisher page; verify before submission}
}

@article{fates2009asynchronism,
  author  = {Fat\`{e}s, Nazim},
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

@article{bonachela2009selforganization,
  author  = {Bonachela, Juan A. and Mu\~{n}oz, Miguel A.},
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

@article{dickman2000paths,
  author  = {Dickman, Ronald and Mu\~{n}oz, Miguel A. and Vespignani, Alessandro and Zapperi, Stefano},
  title   = {Paths to self-organized criticality},
  journal = {Brazilian Journal of Physics},
  volume  = {30},
  number  = {1},
  pages   = {27--41},
  year    = {2000},
  eprint  = {cond-mat/9910454},
  archivePrefix = {arXiv}
}

@article{vazquez2011temporal,
  author  = {V\'{a}zquez, Federico and Bonachela, Juan A. and L\'{o}pez, Crist\'{o}bal and Mu\~{n}oz, Miguel A.},
  title   = {Temporal {G}riffiths phases},
  journal = {Physical Review Letters},
  volume  = {106},
  number  = {23},
  pages   = {235702},
  year    = {2011},
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
  note          = {Preprint; v2 January 2026}
}
```
