# TN: Freezing requires a cold start — initialization-dependent bistability at high beta

Date: 2026-08-09. Provenance: T8 hysteresis discriminator (contribution.edn) run
after the DeepResearch audit flagged the Gross & Blasius 2006 first-order reading
as untested (FINDINGS-related-work.md T8).

## Result

At beta = 16, kappa = 0.1, width 250 (the freezing arm of the Part III bisection):

- **Cold random start**: freezes solid. Control reproduced the paper's pinned-seed
  absorption exactly: last phenotype change t = 1723 (paper: 1722; one-step
  counting convention). Paper reports the outcome seed-robust.
- **Developed live churn** (2500 steps at beta = 4 first): the frozen phase NEVER
  nucleates. 10,000 steps at beta = 16 — ~6x the cold-start absorption time —
  zero absorption in 6/6 seeds; maximum settled fraction ever reached during the
  entire dwell 0.020–0.028 (transient islands, immediately dissolved); every
  lattice still changing at the final step.
- Same for beta = 32 at 2500-step dwell (hysteresis sweep, 6/6 zeros), and no
  re-freezing anywhere on the down-branch.

Two long-lived macrostates at identical parameters, selected by history alone.
The high-beta freeze is an amplification of proto-settled structure present in a
cold random initial condition; churn destroys that structure and continuously
destroys new attempts (the 2% ceiling on transient islands).

## Scope

Width 250, kappa 0.1, one vocabulary, horizons <= 10,000 steps, 6 seeds per arm.
"Never nucleates" means "not within these horizons": metastability with a
nucleation time far beyond 10^4 steps is indistinguishable from stability here —
which is itself the first-order phenomenology (consistent with Gross & Blasius
2006 co-evolving-substrate transitions: bistability; also connects to Klemm et
al. 2003 noise-vs-relaxation competition and Vojta's rare-region language).
This SUPPORTS rather than excludes the first-order/bistable reading named in
sec:related; it does not prove a discontinuous transition (no order-parameter
jump measured, no interface-velocity measurement).

## Artifacts

- scripts/exotype_hysteresis_sweep.clj — up/down ladder, 2500-step plateaus;
  reports/exotype-hysteresis-sweep{,-traj}.edn (+ the inconclusive 400-step
  run preserved as -dwell400).
- scripts/exotype_nucleation_dwell.clj — 2500 @ beta 4 then 10,000 @ beta 16;
  reports/exotype-nucleation-dwell{,-traj}.edn, .log.
- Cold-start control: inline, reproduced in the transcript (t = 1723).

## Candidate paper text (NOT applied — Joe decides placement)

1. Part III closure or examples section, after the absorption results:
   "The frozen endpoints also depend on the start: a lattice that develops live
   churn first (2,500 steps at beta = 4) never freezes when beta is then raised
   to 16 — no absorption in six seeds over 10,000 steps, with the settled
   fraction never exceeding 0.03 — while the same seeds frozen solid from a
   cold start by t ~ 1,700. At matched parameters the frozen and live
   macrostates are both stable on every horizon we tested; which one obtains
   is decided by the initial condition."
2. sec:related, upgrading the Gross sentence: "...a reading our data now bear
   on: an up-down sweep in beta shows exactly this history dependence
   (\secref{...})."
3. Limits: horizons/seeds scope line.

## Interface velocity (run same day — completes the triad)

Splice construction: per seed, frozen donor (cold start beta = 16, 2500 steps)
and live donor (beta = 4, 2500 steps) joined half/half (genotype, phenotype,
exotypes), run 5000 steps at beta = 16 with nothing done at the seam
(scripts/exotype_interface_velocity.clj; reports/exotype-interface-velocity*).

Result: **the frozen phase invades.** 5/6 seeds fully absorbed (largest settled
run 125 -> 250) within 5000 steps, three by t = 2500; the pinned seed's front
receded to ~58 cells then re-advanced to ~100 and was still growing at the
horizon. Positive interface velocity with strong front fluctuations.

Synthesis — nucleation-limited first-order phenomenology:
- frozen phase INVADES any established front (this measurement),
- but CANNOT NUCLEATE from churn (10,000-step dwell: transient islands <= ~7
  cells, ~2% of the lattice, always dissolve),
- so the critical nucleus lies between ~7 and 125 cells, and the cold-start
  freeze happens because random initial conditions contain super-critical
  proto-domains that developed churn never regenerates.

Triangulation with the boundary-blast seam work (Figure 16 bottom row): there a
live seam in a freezing lattice survives only under sustained operator writes —
dose maintaining what phase competition alone would extinguish. Consistent with
the positive frozen-front velocity measured here.

Caveats: donor absorption at 2500 steps was NOT verified per seed (the paper
says outcome is seed-robust but timing is not; seed 2026102004's t=0 largest
run of 55 suggests its frozen donor was still partially live at splice time —
it nevertheless went to full absorption). Front position is tracked via
largest-settled-run on the ring, which conflates the two fronts; a per-front
track would give velocities, not just domain width.

## The Droste arm (same day): one-sided trigger beats its yoke — placement carries something

Design per Droste-Do-Gross 2013 (tentative in ignorance, decisive on one-sided
evidence), with the certificate the triad identified: a long-settled cell is
unilateral evidence of the frozen phase.  Rule: cell unchanged for W* steps ->
one random vocabulary write there; refractory 15.  Arms at cold-start beta=16
kappa=0.1 (absorbing regime), 3000 steps, 6 seeds; yokes are count-matched to
their own arm's closed-loop trigger census, random placement (the paper's yoke
discipline).  scripts/exotype_droste_arm.clj; reports/exotype-droste-arm*.

    arm          coexist absorbed settled  writes/step  width-med  width<=2
    policy          0/6     6/6    1.000      0.00         250       0.06
    droste-30       6/6     0/6    0.173      1.56           1       0.76
    droste-100      6/6     0/6    0.175      0.51           1       0.78
    yoked-30        6/6     0/6    0.356      5.02           2       0.58
    yoked-100       6/6     0/6    0.475      3.37           2       0.51

Findings:
1. The one-sided rule holds all seeds unabsorbed at 0.51 writes/step; its yoke
   needs 6.6x the dose and still carries ~3x the frozen mass.  The yoke's dose
   inflation is the mechanism visible: random placement contains frozen mass
   badly, aged cells accumulate, trigger fires more.  PLACEMENT CARRIES
   SOMETHING once the trigger is one-sided — the paper's "placement carries
   nothing" is scoped to the symmetric/blind trigger classes it tested.
2. Honest limit: the held state is a FINER foam than the noise arms (median
   width 1, 76-78% of runs <= 2 cells, frozen fraction 0.17).  Absorption
   prevention, not regime selection: the valley configuration is not recovered.
3. Patient beats eager: W*=100 matches W*=30 at a third of the dose —
   critical-nucleus logic (only aged structure matters).

Closure-wording implications (Joe decides):
- "No tested local quantity finds or holds coexistence" survives for FINDING
  and for holding THE FOUND CONFIGURATION; the holding-in-general clause needs
  a scope: symmetric estimator- and cadence-triggered interventions.  The
  one-sided age-triggered rule holds A coexisting state (a fine foam)
  efficiently and placement-sensitively.
- The dose-not-placement sentence (interventions figure) needs the same scope.
- Cite droste2013analytical as the predicting theory; this is a confirmation
  of its design principle in a discrete rule-rewriting substrate.

## Follow-ups it motivates

- T3 activated-scaling / frozen-lifetime test inherits a sharper question: the
  quantity that scales is now the NUCLEATION time from structured starts, not a
  bulk relaxation time.
- Interface-velocity measurement (seed a half-frozen initial condition at
  beta = 16: does the frozen half invade the live half, or retreat?) — the
  direct order-of-transition probe, and cheap.
- The Droste one-sided arm gains context: "just froze after long activity" is
  now known to be a RARE event under churn (2% ceiling) — a certificate that
  fires rarely may be exactly right.
