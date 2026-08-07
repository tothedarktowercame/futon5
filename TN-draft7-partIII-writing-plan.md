# TN — draft7 Part III writing plan

**Status: PLAN, 2026-08-06.** A guide to what Part III must cover, derived from reading
`holes/tech-notes/paper/draft7.tex` (1652 lines) against `TN-part3-draft.md` (663 lines)
after the 2026-08-06 long-run results. Not prose; a checklist of what to reconcile,
what to fix, and what can now be claimed.

Every number below was measured on 2026-08-06 and is reproducible from `figs/`.

---

## The situation

`draft7.tex` Part III ("Endogenous Coupling") is finished and typeset: conditional
composition, reading gates, the frozen-gate control, width as correlation length. Its
**Future work** paragraph names the next step exactly —

> "the step from a gate to a regulator. The conditions tested here are fixed families
> chosen by us; nothing yet adjusts its own condition in response to how it is doing …
> the question is whether such a system settles anywhere in particular on the
> calibrated scale."

`TN-part3-draft.md` (§III.1–§III.9) is the prose note for that step, written 2026-08-04
and intended to "slot after `A Causal Measure`". The 2026-08-06 long runs answer the
Future-work question and, in doing so, invalidate part of §III.9.

### CORRECTION 2026-08-06 (later) — the premise above is wrong

Checked term-by-term against `draft7.tex`: **none of the TN's apparatus is in the paper.**
`self-tun` 0, `policy precision` 0, `selection precision` 0, `exotype` 0, `compressib` 0,
`conatus` 0, `hunger` 0, `free energy` 0. draft7's "Part III: Endogenous Coupling" is a
*different* construction (conditional composition / reading gates) that shares the
damage-reach calibration and nothing else. TN-part3's "slots after `A Causal Measure`" was
true of **draft6**. `part3-diagnostic-result.tex` is an orphan — `\input` nowhere,
written against draft6, self-marked as pilot data whose registered confirmation never ran.
`packet.tex` still builds draft6.

**So this is not a reconciliation, it is an introduction.** Assembling requires presenting
an apparatus the reader has never met: EFE policy selection, exotypes, the epistemic
coefficient, the self-tuning arm. Items 2–5 below are still correct as *content*, but they
describe how the new material must position itself against draft7 — not edits to existing
sections.

This also escalates item 1: the new material introduces a **second** parameter named γ into
a paper whose central coordinate is already γ, with both appearing within a few pages.

### RESOLVED 2026-08-06 (Joe) — the miner is a supplement; Part III is the exotype layer

> "The EFE miner could go in a supplement. We worked hard on it, and it proved useful, but
> what we found was that it never actually did *quite* what we wanted it to do. There's a
> real result — we used it to search the space and identify a ridge — but we didn't create
> a self-driving CA search. We *discovered* our main items by a mix of the miner and human
> intelligence. But the miner itself is a tool, not a theory of MetaCAs, or really a finding
> for the paper. Part III should focus on explaining the exotype layer and explaining what
> we actually found at the *end* of running the search process."

This dissolves the "introduce a whole apparatus" problem: EFE policy selection, the sensor
screen, the controller attempts, the compressibility objective and the second γ all move to
a supplement, where they are a **tool paper with an honest negative** (the loop is closable
on an intrinsic target, R²=+0.726, but not on damage reach, R²=+0.016; no self-driving
search was built; discovery was miner + human).

**New structure:**

| | content | source |
|---|---|---|
| **Part III (main text)** | the exotype layer — what it *is*, mechanically — then what the search found at the end | TN §III.8; the 2026-08-06 long runs |
| **Supplement (miner)** | EFE engine, sensor screen, λ↔hunger failure, γ_sel sweep, local compressibility, the ridge | TN §III.1–III.7, §III.9 |

### And Part I is the THEORY of the exotype layer — verified 2026-08-06

The exotype σ is a permutation of the eight neighbourhood patterns: **the object Part I
classifies exhaustively.** The vocabulary is 12 of 8!=40,320, and the code organises it
along Part I's own axes — "the absorbing axis (even4/even8/even1)" and "the rate axis
(fix2/fix3/fix4/fix6)" are cycle-parity and fixed-point count.

Part I's theorem (fixed rules exist iff every cycle is even; an all-even σ with k cycles
has 2^k) **predicts the immune-byte count of all 14 propagators in `exotype/grid.clj`,
verified computationally:**

| kind | cycle type | k | all-even | 2^k | immune bytes claimed |
|---|---|---:|---|---:|---:|
| `:collapser` | (6,2) | 2 | yes | 4 | 4 |
| `:even4` | (2,2,2,2) | 4 | yes | 16 | 16 |
| `:even8` | (4,2,2) | 3 | yes | 8 | 8 |
| `:even1` | (8) | 1 | yes | 2 | 2 |
| `:even44` | (4,4) | 2 | yes | 4 | 4 |
| `:odd53`, `:odd332`, `:identity`, `:builder`, `:chaos`, `:fix2/3/4/6` | odd | — | no | 0 | 0 |

**Consequence for draft7's Conclusion.** It currently says "The connection between the parts
is therefore *motivational rather than deductive*." For Part I → Part III that is too weak:
Part I's classification enumerates Part III's design space. Strengthen the sentence for this
pair while leaving the Part I → Part II reading as it stands.

**This also settles the structural question: same paper, not standalone.** The deductive
link is the reason Part III belongs here.

**Retained from §III.8 as a boundary on claims:** the 12 are 0.030% of the permutation
space, the original four were hand-picked for unrecorded reasons, and every dynamical result
runs at one fixed point in a 40,320-member design space. Nothing is known to generalise
across σ.

---

## 1. HARD DEFECT — `γ` denotes two different quantities

| | meaning | range |
|---|---|---|
| `draft7.tex` §sec:gain, Part II & III, Conclusion | probability a cell reads the **live** phenotype rather than a frozen reference — the *causal currency dial* | γ ∈ [0, 1] |
| `TN-part3-draft.md` §III.6 onward, and all 2026-08-06 work | **selection / policy precision** — how sharply a policy is chosen from expected free energy | γ ∈ 1 … 64 |

TN-part3 is meant to slot **into** draft7, which would put both in one document. In
draft7's notation §III.6's headline ("the selection rule is a criticality control with
measured leverage across the full order/chaos axis") reads as a claim about the currency
dial, which it is not.

**Action: rename the selection-precision parameter before anything merges.** Mechanical,
but load-bearing — draft7's Conclusion turns on the currency reading of γ.
The epistemic coefficient κ has no collision.

---

## 2. The measure story — one finding, not two embarrassments

Current reading order is: draft7 measures everything with damage reach → §III.9 argues
damage reach fails and proposes local compressibility → 2026-08-06 shows compressibility
fails too.

**Write it as the single lesson it is:**

> Damage reach and local compressibility are both sound *instruments* and both fail as
> *objectives*, for the same reason — neither tracks long-run persistence. An instrument
> validated on 250-step ECA references can be measuring a transient in a system whose
> transients run to t ≈ 1000.

This leaves draft7's own use of damage reach untouched (external instrument, legitimate)
and preserves §III.9's three objections to it, which were about **perceivability**, not
correctness. Precedent already exists in the paper: Supplement 4 records an information
measure withdrawn after its mask and measure proved circular.

---

## 3. §III.9.4 contains a sentence the long runs refute

> "The frozen region is not a defect — it is half of what makes the distribution graded."
> … "Both top scorers have **large** dead zones."

**This is now false, and it is the crux of the section.** The cells with the largest dead
zones are the ones that reach absorbing states:

| cell | objective (mid-range) | fate at 3000 steps, both seeds |
|---|---:|---|
| γ_sel=32, κ=0.1 | **0.625** | **DEAD** — absorbs t=1046 (seed 1) / t=460 (seed 2) |
| γ_sel=64, κ=0.1 | **0.547** | **DEAD** — absorbs t=881 / t=537 |
| γ_sel=2, κ=0 | 0.422 | alive |
| γ_sel=2, κ=0.1 | 0.422 | alive |
| γ_sel=4, κ=0 | 0.312 | alive |
| γ_sel=4, κ=0.1 | 0.219 | alive |
| γ_sel=4, κ=0.2 | 0.109 | alive |

Perfect separation, sign inverted: **every cell that dies scored higher than every cell
that lives.** "Absorbing" is verified literally — γ_sel=32 changes for the last time at
t=1046 and every subsequent row is byte-identical; final pattern density 0.500, so it is
a frozen pattern rather than a blank sheet.

Rewrite, don't soften. Related corrections in the same section: the genotype view shows
the dead zones are **genotype monoculture** (diversity 0.257 on the low side of the
boundary vs 0.435 on the high side), so the objective is anti-correlated with genotype
diversity.

---

## 4. §III.6.1(a) is answered, and the answer splits

The gate was: *does any runtime-computable quantity track the target as γ varies?*

- **internal score → damage reach: R² = +0.016.** Fails. The loop cannot be closed on
  damage reach from inside — which §III.6.1 says explicitly "would be a real result and
  should be reported as one."
- **(halting, change) → local compressibility: R² = +0.726** held-out, matched window
  (was +0.541 with a t=100–300 vs t=0–250 window mismatch; the matched remeasurement
  recovered the difference). In-sample +0.773, shown only to expose overfit.

**The loop is closable — just not on damage reach.** This survives the objective being
wrong, because it is a statement about sensor-to-target coupling, not about the target's
value. It is the cleanest thing §III.6.1 asked for.

Supporting: the objective column reproduced **140/140 identical** across two independently
written code paths (the grid script and `aligned-episode`), so the measurement pipeline is
not where any remaining error lives.

---

## 5. Name the Part I tension rather than avoiding it

draft7's Conclusion states plainly:

> "the tested propagator-fraction scan gives a **broad crossover rather than evidence of a
> critical point**."

The 2026-08-06 result is a sharp bifurcation with two absorbing states and a crossing
bracketed in γ_sel ∈ (8, 16) — the frozen-drift sign flips there at both κ=0 (−0.078 →
+0.091) and κ=0.1 (−0.050 → +0.191), and the geometric γ grid never sampled inside it.

These are compatible — different object (phenotype persistence vs genotype aliveness),
different axis — but unstated they read as self-contradiction.

**Stated, it is the stronger version of the reprise Joe wants:** Part I finds the
collapse/sustain division *without* a critical point on its axis; Part III finds the same
division *with* a candidate critical point on a different one. Part I's division was a
classification; Part III's is a dynamics — same theme, one level down.

---

## 6. The replacement objective: persistence

**Does the sheet reach an absorbing state?** It satisfies every criterion §III.9.3 sets
out — intrinsic (computed from the system's own phenotype), no external reference, not
horizon-defined in the bad way — and adds one that matters more after 2026-08-06:

**It cannot be faked.** Four measures failed that day by finding artifacts that correlated
with something (interface flicker; one-step lulls; domain mergers; frozenness). "The sheet
never stops changing" is not a statistic.

The mechanism is boundary competition between live and frozen domains, and both regimes
are the same process with opposite winners:

- **γ_sel=2, κ=0** — frozen domain *count* holds at 10 while mean width collapses
  9.0 → 3.8 → 0. Domains erode inward from every edge simultaneously. The count never
  rises, which rules out interior dissolution (that would fragment domains and drive the
  count up). Live invades frozen and wins.
- **γ_sel=32, κ=0.1** — frozen count *rises* 15 → 22 (live fragmenting the frozen field),
  then collapses to 2 domains of width 124. The coral cuts its opponent up for ~500 steps
  and is then overwhelmed.

**The wild case — and the bisection found candidates.** A regime where neither wins,
domains breaking apart, freezing and unfreezing indefinitely, is the edge of chaos defined
*dynamically*: created rather than located. Bisection at κ=0.1, 3000 steps, seed
2026102000:

| γ_sel | frozen 2500–3000 | absorbs | domains early → late | verdict |
|---:|---:|---:|---|---|
| **8** | **0.295** | **never** | **7 → 6** | **neither wins** |
| **10** | **0.530** | **never** | 9 → 3 | neither wins (but see caveat) |
| 12 | 1.000 | t=1917 | 13 → 1 | frozen wins |
| 14 | 0.991 | t=2604 | 17 → 1 | frozen wins |
| 16 | 1.000 | t=1722 | 14 → 1 | frozen wins |

**γ_sel=8 is the protagonist of Part III: a coral reef that survives indefinitely.** It is
the same morphology as the γ_sel=32 reef that dies at t=1046 — coherent diagonal
propagating structure, genotype diversity sustained (0.306 mean row, churn 0.405, 256
distinct sigils, no monoculture after the t≈500 transient) — but it never reaches an
absorbing state. Its frozen phase is a fine-grained lava lamp: 367 blobs in the steady
state with median lifetime 24 steps and median width 4 cells, continuously forming and
dissolving, largest area only 650 (nothing coarsens into a continent). Frozen fraction
0.071 at the 15-step threshold, far from both attractors.

**It was in the 35-cell survey all along, scoring 0.453 — eighth of 35.** The objective
ranked the reef that dies (0.625) above the reef that lives, and neither looked
interesting in a 200-step window. This is the single sharpest statement of the
window-and-objective failure the part is built around. γ_sel=10 is a lava lamp *plus a continent*: the same fine-grained turnover (164 blobs,
median 22 steps × 4 cells) with one persistent mass of area 198,697 — three hundred times
larger than anything in γ_sel=8. That mass thaws around t≈2400; whether it refreezes
(rhythm) or stays live (one-way transition caught mid-flight) is unresolved, and its
falling domain count (9 → 3) is also consistent with freezing more slowly than 3000 steps
can reveal. **Disambiguating it needs ~10,000 steps**,
which is where that cost is justified (Joe's call: 3000 first, escalate only where
ambiguous).

Note the 200-step drift **mis-predicted the γ_sel=8 endpoint** (−0.050 suggested live-wins;
the long run gives 0.295, 7× the live-wins value). Long-run endpoints must be measured, not
inferred from short-window drift.

Single seed. γ_sel=12's absorption at t=1917 is well inside the ~2× between-seed spread
measured at the bracket endpoints, so the exact crossing is not yet fixed.

**Caution on absorption time as a signature.** Critical slowing down predicts it diverges
at γ*, but it is seed-noisy: γ_sel=32 gives 1046 / 460 and γ_sel=64 gives 881 / 537 across
two seeds, so the between-γ difference at the bracket endpoints is within seed spread. The
*alive/dead verdict* is robust (14/14 across seeds); the *timing* is not. Single-seed runs
suffice to locate the crossing; several seeds per γ are needed before any claim about
divergence.

---

## 7. The figure

`figs/FIG-bifurcation.png`. Two outcomes of one contest, not "good cell / bad cell":

- the coral (γ_sel=32, κ=0.1) — a dense branching structure holding out in a field of
  static stripes for 1046 steps before extinguishing;
- γ_sel=2, κ=0 — braided structure that erodes the striped territory to nothing and runs
  indefinitely.

Give the coral its full 1046 steps rather than compressing it under 2000 steps of
tombstone. The current version does the latter and buries the interesting part.

Second figure: `figs/FIG-g8-spacetime.png` — γ_sel=8 phenotype and genotype, 3000 steps,
with a zoom to the blob scale. This is the reef that survives, and the genotype panel
showing sustained diversity against the boundary pair's monoculture continents is the
mechanism half of the claim.

Supporting figure: `figs/FIG-sampler-sheet.png`, the 35-cell survey in frozen/live
coordinates, which shows frozen territory exists only at κ ≤ 0.2 — so the contest can only
be staged in that corner.

---

## Claim discipline — what is and is not established

**Claimed.** The objective's ranking inverts against long-run persistence, with perfect
separation on 7 cells × 2 seeds. Absorbing states are literal and verified. A
runtime-computable sensor tracks the intrinsic objective at held-out R² = +0.726. The
frozen-drift sign flips in γ_sel ∈ (8, 16) at two κ values. The erosion mechanism is
boundary retreat, not interior dissolution (domain count vs width).

**Not claimed.** That the γ_sel=8 diagonals are persistent objects with a velocity rather
than correlated texture — visible in `figs/FIG-g8-spacetime.png` and not yet measured; the
day's record on quickly-written structure measures is 0 for 4. That the blob scale
(4 cells × 24 steps) is intrinsic rather than set by the width-250 lattice — the width-1000
zoom run tests exactly this, predicting ~1470 blobs of unchanged median size if intrinsic.
That a critical point exists in that interval — the bisection has not
reported. That absorption time diverges — under-powered, seed spread exceeds the effect.
That any of this characterises the substrate in general: it is 7 cells at one lattice
width, in the tradition of draft7's own limits sections.

**Method note worth carrying.** Three automated measures for the invasion criterion each
found a different artifact; Joe identified the phenomenon by eye and his four picks turned
out to be 3 of the 4 most negative frozen-drift cells in the survey. Build the labelled set
before the metric. See `~/.claude/.../feedback_eye_first_then_metric.md`.
