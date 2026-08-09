# Infographic — *Rule-Rewriting Cellular Automata and the Edge of Chaos*, as one visual page

**Status: 2026-08-09 — REALIZED, in a different form than described below.**
Figure 1 of draft9 is now a Lamport-style structured-proof outline (three
assertions, steps tagged [thm]/[def]/[meas]/[ctrl]/[neg]/[probe], the A/B seam
of §5 rendered as an explicit "Scope seam" paragraph stating that no step of
one chain cites evidence from the other). The node-and-edge map it replaced is
recoverable from git history. The caption audit (§5) and its repairs remain
this document's lasting contribution; the zone/panel visual described below
was not built and is superseded unless a poster/talk needs it.

**Status: 2026-08-08.** Reworks `precis.md` (2026-08-07) into the description of a
visual summary. **Input discipline: figure captions only** — every panel takeaway
below must be licensed by the caption of the figure it uses, not by body text.
Where the precis's argument chain needs a claim no caption carries, that is
recorded as a **caption gap** rather than smuggled in. The visual is described,
not built; figure numbers are those of the current draft8 build (20 figures).

The exercise has a second purpose, and it found something: the captions alone
*almost* carry Argument A and *fully* carry Argument B — and the two places
where A leaks are its two strongest controls (§5).

---

## 1. The page at a glance

One landscape page, three zones and a seam:

```
+--------------------------------------------------------------------------+
|  TITLE BAND: one substrate, drawn as a two-layer ribbon (rules | states) |
+-----------------------------------+--------------------------------------+
|  ZONE A (left, 3 tiers)           |  ZONE B (right, 2 tiers)             |
|  "What governs propagation?"      |  "Can it find its own regime?"       |
|  descending evidential chain      |  descending evidential chain         |
|                                   |                                      |
|  A1 the classical coordinate dies |  B1 the phenomenon exists            |
|  A2 the causal replacement        |  B2 the contest and its ridge        |
|  A3 currency, reached twice       |  B3 the closure: no local knowledge  |
+-----------------------------------+--------------------------------------+
|  SEAM (full width): what joins A and B — and what does not               |
+--------------------------------------------------------------------------+
```

The seam is drawn as a visible break, not a flow arrow. The precis's central
finding — two arguments joined by shared substrate rather than by evidence —
becomes the page's most prominent design feature instead of its buried caveat.
Figure 1 (the argument map) is deliberately **not** reproduced: this page is its
successor, built from evidence panels rather than claim boxes.

---

## 2. Title band — the object

One wide ribbon: a genotype/phenotype pair from **Figure 2** (two operators,
256-colour rule field beside black-and-white state field). Caption licenses the
band's only sentence: *the operator does not merely stir rule space; it drives
it toward particular, computationally distinguished rules* — under one σ the
field comes to be 39% Rule 110 and the phenotype shows Rule 110's gliders.

Inset chip: the historical pair from **Figure 3** — one hand-written operator
whose conjugate collapses the genotype to two adjacent terminal rules while its
direct form keeps ~30 — *two ends of genotypic aliveness from one historical
write*. This chip is the page's origin story: the object predates the theory.

---

## 3. Zone A — what governs propagation

### Tier A1 — the classical coordinate dies (three small panels)

1. **Figure 5** (parity dichotomy): five operators; the all-even ones possess
   fixed bytes but only one settles onto them; odd-cycle operators possess none
   and stay diverse. Panel takeaway, licensed verbatim: *fixed-point existence
   makes settling possible, not inevitable*.
2. **Figure 7** (two 4-cycles, opposite fates): *the specific permutation, not
   its cycle type, decides*. This kills reading class structure off the algebra.
3. **Figure 10** (finite-size scan, four diagnostics): logistic crossovers that
   do not sharpen with size, a wandering susceptibility peak, no common Binder
   crossing. Takeaway: *a broad crossover, and no critical point on the tested
   axis*.

Tier annotation (from Figure 1's caption, the one place a caption states it):
*λ = 1/2 closes off the classical coordinate*. **Caption gap A-i**: the balance
theorem itself — every fixed rule of every operator at λ = 1/2 — has no figure;
its only caption presence is that clause. The tier must either borrow that
clause or add a spec for a tiny new panel (70-rule balanced shell as a 8×?
bit-grid, with the 32 phenotypically live members highlighted — **Figure 4**'s
caption, a dead genotype carrying a live phenotype on Rule 105, licenses the
neighbouring fact and could stand in).

### Tier A2 — the causal replacement (two panels)

1. **Figure 12** (damage from a single flip, live vs matched control): the
   measurement itself — fork, flip one cell, watch the cone. This is the
   page's methodological pivot: *no region-drawing, only a perturbation and a
   clock* (clause carried by the Figure 13 caption's protocol reference).
2. **Figure 13** (every construction on one scale): the scale, bounded by named
   elementary rules, rows split by *whether the genotype update reads the
   phenotype*. Takeaway licensed directly: everything blind sits low; everything
   reading lands high.

### Tier A3 — currency, reached twice (two panels + the seam's strongest chip)

1. **Figure 15** (two couplings, dialled): reach against gain for transport and
   river — the dial exists and is monotone in both.
2. **Figure 16** (reach against sustained diversity): the 76 original
   configurations with conservative-transport bands overlaid. **Caption gap
   A-ii, the big one**: the takeaway — *the bands are flat, so diversity does
   not govern; transport moves reach an order of magnitude* — is **not in the
   caption**, which only says what is plotted. Likewise the frozen-gate
   control, the single load-bearing control of the endogenous-gain result, has
   **no figure at all**; the entire "currency, not correlation" step lives in
   body text and one line of Figure 1's caption. If the infographic is ever
   built, these two are the panels the paper does not currently supply:
   (a) Figure 16 needs one sentence added to its caption stating the flat-band
   reading; (b) the frozen-gate comparison deserves a small paired-bar panel
   (seven matched pairs, live minus frozen, all positive). Both are caption
   repairs in the paper, not new experiments.

Tier annotation: *the same coordinate, recovered from a construction sharing
none of the first one's dials* — this clause is carried by Figure 1's caption
(teal box) and by no evidence figure. Recorded as **caption gap A-iii**.

---

## 4. Zone B — can it find its own regime?

Zone B is fully caption-carried; nothing in this zone needs the body text.

### Tier B1 — the phenomenon and the contest

1. **Figure 19** (the lava lamp, β = 8, κ = 0.1): *coherent propagating
   structure runs the full sheet; frozen regions of median 4 cells × 24 steps
   form and dissolve; all 256 sigils present*. The thing itself.
2. **Figure 18** (the two arms of the ridge): frozen fraction for seven
   configurations, the two absorbing arms in red; the extremes as spacetimes —
   one fragments the frozen field and is squeezed out, one erodes it to
   nothing. Takeaway licensed verbatim: *the same contest, opposite winners*.
3. **Figure 20** (the bisection): β = 12, 14, 16 taper to absorption; 8 and 10
   do not. The external search that located the phenomenon, shown as five
   strips — with the threshold discipline in the caption (*at a four-step
   threshold the turnover count inflates thirteen-fold*) as a small footnote
   chip on measurement honesty.

### Tier B2 — the closure

**Figure 17** (local intervention at the freezing arm), the page's newest
panel and Zone B's verdict, all licensed by its caption: from identical initial
conditions, the unmodified policy absorbs; boundary-directed adoption absorbs
*earlier*; one random write per dwell at pure-neighbourhood cells prevents
absorption — *and so does the same number of writes at uniformly random cells*;
what survives is a width-2 fragmented mixture, not the Figure 19 regime.

Zone B's closing annotation, assembled from Figures 17–20's captions jointly:
*the regime exists (19), an external bisection finds it (20), the freeze is a
policy artifact (18), and no local placement of writes knows where it is (17)*.

### Zone B margin — held open

One reserved slot at the tier's foot, dashed border: the boundary-blast arm
(writes at the boundary rather than at pure cells), currently running. If it
beats its yoke it takes the slot as the series' first placement-information
positive; if not, the slot carries one line: *boundary targeting joins the
retired list*. The infographic description should not pre-state the outcome.

---

## 5. The seam — what the caption discipline proved

The full-width seam band states, in two sentences, what the precis needed a
section for: **Zone A is an evidential chain about a coordinate; Zone B is an
evidential chain about a regime; the object σ is shared, the evidence is not.**
No arrow crosses the seam. The one legitimate crossing is typographic: the
two-layer ribbon of the title band reappears as the background of both zones.

And the audit result, worth keeping even if no visual is ever made:

| precis claim | carried by captions? |
|---|---|
| A1 classical coordinate closed (λ, census, scan) | yes, minus the balance theorem itself (gap A-i) |
| A2 feedback doubles reach | yes (Figs. 12–13) |
| A3 currency governs; diversity does not | **no** — flat-band reading absent (gap A-ii) |
| A3′ frozen-gate control (currency, not correlation) | **no figure exists** (gap A-ii) |
| A ends by re-deriving the coordinate endogenously | only via Figure 1's claim-box caption (gap A-iii) |
| B phenomenon, ridge, bisection | yes, verbatim (Figs. 18–20) |
| B closure: no local knowledge of the regime | yes (Fig. 17) |

The asymmetry is the finding: **the paper's Part III argument is now visible
from its figures alone; its Part II argument is not.** The three caption gaps
are cheap paper repairs (two caption sentences and one small control panel),
and they would pay for themselves in any medium — reviewers read captions
first.

**Repairs landed (2026-08-08, same day):** gap A-i by the balance clause added
to the shell figure's caption; gap A-ii by the flat-band sentence added to the
diversity-axis caption and by the new frozen-gate dumbbell panel
(`figures/frozen-gate.pdf`, seven matched pairs, every live departure positive
and every frozen departure negative); gap A-iii by that panel's caption, which
carries the convergence clause. With these, both zones are caption-complete.

---

## 6. Relation to precis.md

Kept: the two-argument diagnosis (now a design feature), the A1–A5/B1–B5
spines (compressed into tiers), the map audit (superseded by §5's caption
audit). Dropped: the compression candidates and open-items lists — they were
about editing the text, and this document is about what the figures carry.
Changed since the precis was written: B is no longer "stated as unanswered";
the closure section and Figure 17 exist, and Zone B ends in a verdict rather
than a disclaimer.
