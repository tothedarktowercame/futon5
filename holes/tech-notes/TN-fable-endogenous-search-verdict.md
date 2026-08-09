# Verdict — endogenous search after the arm (e) failure

**Reviewer: fable, 2026-08-08.** Third note in the series
(`TN-fable-endogenous-interface-control-review.md`, `…-design-2.md`). Input: the
four-arm closed-loop run — none 0.2938 | band 0.1271 | yoked 0.1055 | activity
0.0782 on |S − 0.20|; band loses to its own yoke (t = +1.66 against, 9/24) and
loses significantly to the activity control (t = +3.42 against, 5/24); the
run-level detector result (14/14, no overlap, preregistered) stands.

## 0. Summary

Interface-as-feedback is dead — it fired my drop criteria 3 and 4 and stays dead
under every objection I can raise against the run. But the run does not cleanly
establish *why* it died (§1), the "essential external operation" is not
cross-run comparison — it is **restart** (§2), one experiment remains live
(two-timescale, §3), and the negative is worth reporting with a sharper
statement than the one proposed (§4). Retirement list in §5.

## 1. Challenging your reading first, as asked

Your reading — activity is a fast coupled proxy, interface feedback is stale —
is consistent with everything prior (the structural test, the h-scan, the
lag + w decomposition) and I believe it is mostly right. But this run is not
clean evidence for it, because the band arm as described carries three
confounds:

1. **The band target was out of validated range.** Fitness = −|windowed local
   interface − 0.20|, but the prospective grid's mixed-regime range was
   [0.0984, 0.1617]. A target above the achievable maximum is not a band; it is
   a monotone maximize-interface gradient — precisely the fragmentation-shaped
   objective the first review warned against. Design 2 §2 specified calibrating
   the band from the mixed-regime sheets and committing the numbers; that step
   appears to have been skipped. (The outcome metric |S − 0.20| shares the
   problem but is common to all arms, so cross-arm ordering survives it.)
2. **Cold-start flatness.** The IC was biased frozen, so at early times local
   interface is ~0 *everywhere* and the band arm's fitness landscape is flat —
   no gradient at all — while the activity arm has usable signal from step ~2.
   Part of the band-vs-activity gap may be "no signal at start," which is a
   different defect from "signal arrives late."
3. **T = 10 < w = 15.** A 10-step window cannot contain a settling event; the
   interface variable can only lose bits (melting) or hold them within one
   window. The band arm was scored on a variable that is close to constant at
   its own decision rate.

**None of this rescues the approach.** The decisive number is band-vs-yoke: the
yoke carries the *same* fitness values — same miscalibration, same cold-start
flatness, same window — attached to the wrong cells, and does no worse
(slightly better). Whatever information the interface fitness contains, its
*spatial assignment to cells* added nothing, and that conclusion is immune to
all three confounds. Interface feedback at this decision rate is dead on the
evidence we have; what the confounds cloud is only the mechanistic attribution
(delay vs no-signal vs wrong-target). A free clean-up from existing logs, if
the attribution matters for the write-up: recompute the paired band-vs-activity
differences on late windows only (t > 600, past cold start), and note the
calibration issue explicitly. Do not re-run anything for this.

## 2. Question 1 — is cross-run comparison essential?

Decompose what the external searcher actually did into four operations, and
answer per operation, because the answer differs:

**(A) Counterfactual evaluation — holding two configurations side by side.**
NOT essential, and this is already demonstrated, twice, by your own apparatus:
spatial heterogeneity substitutes for parallel runs. The §8 harness got
unconfounded per-operator conditioning from random spatial assignment within
single runs, and adoption-beats-none (24/24) shows the lattice can function as
its own ensemble. Space-for-runs is a real, working substitution. A cell
cannot fork the lattice, but a heterogeneous lattice is already forked.

**(B) Criterion evaluation — scoring a configuration on the coexistence
criterion.** Available locally only in slow form. The criterion's discriminative
power is bought by temporal integration (w-persistence, 500-step windows), so
local evaluation is possible but at ≥ hundreds of steps per candidate — usable
as a slow outer criterion, unusable as per-decision feedback. This is the §4
obstruction and it is quantitative (a rate ratio), not absolute.

**(C) Restart — reinitializing after absorption.** **This is the essential
external operation, and it is unavailable in principle from inside.** The
system has absorbing states; after global absorption no local rule ever acts
again — activity is zero, adoption events never fire, every fitness is flat
forever. An external searcher's bisection consumed restarts liberally: each
probe that died was thrown away and the search continued. A within-run searcher
gets no second trajectory. Worse, there is a bootstrap circularity: evaluating
a candidate on the coexistence criterion takes ~500+ steps, so an endogenous
search needs the lattice to *survive its own experimentation* for the duration
of the search — it needs to already be near the region it is searching for.
Partial mitigation exists inside a run (a live region can re-agitate a frozen
neighbour, so local absorption is recoverable while *any* region lives), which
is why the answer to (A) is yes; but global absorption is the point of no
return, and nothing local crosses back over it. Plainly: **the minimum
externality is whatever prevents or undoes global absorption during search —
restart, an external agitation source, or a guarantee the search dynamics never
globally absorb.** That is a statement worth writing up on its own.

**(D) Axis discovery — recognising two dead runs as opposite extremes of one
ordered parameter and hypothesising the interval.** Not available at cell scale
in this architecture, for a representational reason that is worth stating
plainly rather than dressing up: the objects compared are whole spacetime runs,
and a cell's state (bits, counters, small windows) cannot encode a summary of
one run, let alone a coordinate over pairs of them. This is architecture-
relative, not metaphysical — but within this substrate it is a flat no. Note
that no experiment in this series ever tested endogenous axis discovery; every
controller, including arm (e), regulated along an externally chosen axis
against an externally chosen target. What failed empirically was the *easiest*
of the four operations.

**So the direct answer**: cross-run comparison per se is substitutable (space
for runs). What is not substitutable is restart (C), and what was never on the
table is axis discovery (D). Full endogeneity as originally posed — "find and
hold, no external objective" — is dead at the *find* stage for systems with
reachable global absorption, and that is the useful in-principle result. "Hold,
given the axis and a viable start" remains partly open (§3).

## 3. Question 2 — where the remaining hope is, ranked

**(b) two-timescale — the one experiment I would still build.** Inner loop: the
activity-band controller, which won, with a **per-cell setpoint** instead of a
global 0.20. Outer loop: each cell adjusts its own activity setpoint every
~150–200 steps (≥ 5–10× the lag + w delay, so the slow observable is fresh at
that clock) by whether its windowed local interface sits below/in/above a
*calibrated* band ([0.0984, 0.1617]-derived this time). The endogeneity content
is exactly this: **can the outer loop find the setpoint, rather than being
handed it?** Test: initialise all setpoints wrong (e.g., 0.05, deep in the
freeze direction), and compare against (i) oracle arm — fixed externally-chosen
best setpoint, (ii) yoked outer loop — setpoint adjustments spatially permuted,
(iii) frozen outer loop — setpoints stay wrong. Success = recovers to within
noise of oracle; the yoke guards against "any setpoint churn helps." Drop
criterion attached: if wrong-start recovery fails or the outer yoke matches,
the setpoint cannot be internalised either, and the minimum externality of §2
grows from {restart, axis} to {restart, axis, setpoint} — at which point write
the negative up and stop. One run of the existing harness with one new arm
family; cheap.

**(a) activity-as-controller with interface offline** — this is not a separate
line; it is the *oracle baseline* of (b). As a standalone result it contains no
endogeneity at all: the setpoint is external, so it is distributed
thermostatting to an external target — a fine engineering result, already in
hand, nothing more to test.

**(c) spatial comparison** — fold into (b), do not build standalone. The honest
mechanism here is that boundary cells are the only cells with fast access to
both phases: melting of an adjacent settled cell is detectable at ~2 steps, and
the freezing *precursor* (neighbour's quiescence counter climbing) is likewise
fast. That makes boundary flux precursors a candidate *outer-loop signal*
(possibly better than windowed interface, since it avoids the w-delay). But as
a standalone feedback architecture it reduces to interface-feedback with extra
steps, which is the thing that just died.

**(d) characterise minimum externality** — do this regardless of how (b) goes;
§2 is the content. The characterisation is informational and clean: the
external searcher injects O(log) bits *once per regime* (the axis, ~7 bisection
probes, a setpoint) plus restarts, versus a feedback controller's bits *per
decision*. Externality measured in bits-per-regime rather than bits-per-decision
is the precise sense in which the system is "partly" externally steered, and
the (b) experiment decides whether the setpoint bits move to the endogenous
side of the ledger or stay external.

## 4. Question 3 — is the negative known, and is it stated correctly?

**Stated correctly? Almost — two tightenings.** As proposed ("the filtering that
makes it a clean detector is what makes it slow") it overclaims in one direction
and underclaims in another:

1. It is specifically **temporal** integration that carries the obstruction.
   Separation bought by spatial or structural aggregation adds no group delay;
   S's separation is bought by the w = 15 persistence filter plus 500-step
   windows, which is why it inherits ≥ w of latency.
2. The obstruction is a **rate ratio, not a property of the quantity**. A slow
   detector steers a slow actuator perfectly well (a thermostat on a sluggish
   furnace). The failure condition is decision rate ≳ 1/(filter window): here,
   ~20-step decisions against a 15-step filter, ratio ≈ 1, obstruction bites.

Corrected statement: *when a detector's discriminative power is purchased by
temporal integration over a window w, feedback closed through it inherits ≥ w
of group delay, and it becomes unusable for control at decision rates
approaching 1/w — while remaining fully usable as a slow outer criterion or an
offline validator.*

**Known?** Not, to my knowledge, under one canonical name, but it is assembled
from classical pieces, and the write-up should say so: measurement/filter delay
limiting achievable closed-loop bandwidth is textbook control (Bode phase lag,
delay margin; process-control folklore "you cannot control what you cannot
measure fast enough"); the classical remedy is the **Smith predictor** (1957) —
control a fast internal model of the delayed variable rather than the delayed
measurement. Note the pleasing structural fact: your empirical winner is
exactly what a Smith-predictor architecture collapses to when the slow variable
is a deterministic filter of a fast one — controlling the fast variable
(activity) directly *is* the degenerate Smith predictor, and design 2's
semi-analytic arm (b) was a non-degenerate one. Adjacent framings: integration
time vs latency in signal detection (speed–accuracy trade-offs), delayed credit
in RL. And one distinction the write-up should draw explicitly: **this is not
Goodhart**. The measure was not corrupted by being optimised — the yoke shows
it was never steering at all. It was late, not gamed.

**Worth reporting on its own? Yes**, and the exhibit is unusually clean, which
is most of the value: preregistered detector result intact (14/14, unseen
cells) alongside a shared-decision-stream closed loop in which (i) the yoke —
same fitness values, spatially permuted — matches the real controller, and
(ii) a fast coupled proxy beats the slow target variable *on the slow
variable's own criterion*. The yoke construction (churn control with matched
value distribution) is independently a nice method and should be named as such.
One honesty requirement: report the three §1 confounds and the h-scan in the
same paper, so the mechanistic claim is delay-shaped rather than proven-delay.

## 5. Question 4 — what not to bother testing

Retire, with reasons:

1. **Any w-filtered statistic as per-decision feedback** — interface level,
   interface trend, interface in EFE C, transfer-entropy boundary measures.
   Killed structurally (h-scan), then empirically (yoke). Twice is enough.
2. **Design 2's arm (b) semi-analytic predictor as a rescue.** Its ceiling is
   approximating realized interface values — which, attached to the *correct*
   cells, just failed to beat the same values attached to wrong cells. The h=2
   hazard channel survives only if the two-timescale outer loop wants a
   boundary-flux signal; do not build it for its own sake.
3. **Learned 20-step models.** Ranked "do not build" in design 2; now moot.
4. **Parameter iteration on interface feedback** — band numbers, dwell, T,
   neighbourhood radius. Spatial aggregation reduces variance but not group
   delay; the binding constraint is temporal, and no parameter touches it.
5. **Bigger lattices / longer runs / more seeds hoping band-vs-yoke flips.**
   The point estimate is on the wrong side; this is not an underpowered
   positive.
6. **Refinements of yoked-vs-none.** The churn effect is established; it is a
   control, not a phenomenon to chase.

Still standing, and the only things standing: the two-timescale experiment of
§3 with its attached drop criterion; the minimum-externality write-up of §2,
which is worth doing even if — especially if — the two-timescale arm fails;
and the detector paper with the corrected §4 statement. If the two-timescale
arm fails its yoke, the honest final sentence of the series is: *this system's
interesting region can be detected from inside but not found from inside;
finding it requires restart, an axis, and a setpoint, all external — and that
is a result, not a disappointment.*
