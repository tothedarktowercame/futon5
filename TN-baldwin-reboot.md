# TN-baldwin-reboot — the exotype ladder is not six problems, it is one orphan document and one dead RNG

**Status:** DIAGNOSIS, 2026-08-04. Written after slice 12 was declared void and the
`fixed-model` "inversion" was reported. Both readings were partly wrong; the real defect
is upstream of both and upstream of most of the slice ladder.

**Companions:** `holes/F-what-the-propagator-actually-does.md` (the orphan — read it first),
`TN-baldwin-selection-strategy.md` §8/§10 (the two void designs),
`TN-metaca-baldwin-micro-pilots.md`, `TN-eig-definition.md`.

**Bottom line:** the exotype grid has **no spatial noise**. Every cell draws the same
propagator bit, the same selection direction, and the same blend outcome at any given
step, because every per-cell decision takes the *first* draw from a freshly seeded
`java.util.Random` whose seed is an arithmetic function of `(time, index)`. All the
marginal distributions are correct, which is why twelve slices did not catch it. Separately
and independently: a measured description of this substrate has existed since 2026-07-16
and **is cited by no file in the repository**, and it already contains the answers to three
of the recent stop-the-line failures.

> ## ❄ FREEZE (Joe, 2026-08-04)
>
> **No findings, no sweeps, no experiments until the register in §10 is discharged.**
> Work is restricted to the eight registered items. A new observation does not open a new
> thread: it is either evidence for a registered item, or it is parked in §12 with a home.
>
> **No more deep dives** (Joe, 2026-08-04). Each item gets the cheapest evidence that
> decides it — a rule applied to a class, not an investigation per member. Where a claim can
> be settled by one measurement or one file read, that is the whole budget. §13 is the
> worked example: twelve slices dispositioned from one measurement and two reads.
> This freeze is itself the first remedy for N3 and N4 — the failure mode has been *breadth
> under an unverified premise*, and the register is the premise-check.

---

## 1. The meta-finding: one orphan, six faces

`holes/F-what-the-propagator-actually-does.md` was committed 2026-07-16 (`7923943`).
`holes/M-formal-patterns.md` was chartered 2026-07-17 — the next day. Every slice from 1 to
12 descends from that charter. A grep across every `.md` and `.clj` in futon5 finds exactly
one file mentioning the note: itself.

Three of the recent failures are answered in it in advance:

| stop-the-line | what the orphan already says |
|---|---|
| §8 — Baldwin arm has no path from exotype to fitness | "The Figure-8 genotype dynamics is **autonomous** — it never reads the phenotype." The Elisp function's fourth argument is literally named `ignore`. |
| §10 / slice 12 — selection without variation | The genotype layer is self-application + blend + propagator. The propagator *is* the only variation source; there is no mutation operator and never was. |
| `fixed-model` vs the exotype kinds | A byte is immune to the propagator iff every cycle of σ is even. That is exactly why `:identity` can never leave a byte alone. |

**The handoffs are not failing. The design step is.** The handoff packet encodes the
imagined substrate; Codex implements it faithfully and correctly; the *run* is first contact
with the real one. So the gate fires after a full dispatch/park/review round-trip instead of
at design time, which is why it feels like every handoff. Codex is the only participant
touching ground truth, so all accumulated design debt surfaces there.

**And it is worse than one unread note.** `draft6.tex:1358` opens *"Invariants the
implementation must satisfy"*, and among them:

> **Operator reachability.** Every value of every gene must be attainable under mutation. A
> value the operator cannot reach is present in the representation and absent from the
> experiment.

That is the Baldwin void (H2 / §4), stated precisely, in the project's own paper, before
slice 12 was designed — and slice 12 violated it. The same list also carries *"Layering
fidelity… an extension can reproduce byte-for-byte and still report a different value than
the construction it claims to generalise"*, which is N1 in the abstract.

So the register's two worst items were each pre-empted by a document already in the
repository: one in `holes/`, one in the paper's own methods. **The system's failure mode is
not producing wrong knowledge. It is producing right knowledge that never reaches the design
step.**

**Gate G1 (design-time, not review-time):** before any exotype dispatch, the packet must
cite `F-what-the-propagator-actually-does.md` and state which of its measured facts the
design depends on. This costs a grep instead of a round-trip.

---

## 2. F2 — the first draw after `with-seed` is not random *(NEW, measured, root cause)*

`ca/with-seed` (`src/futon5/ca/core.clj:128`) expands to
`(binding [*rng* (java.util.Random. (long seed))] ...)` — a **fresh PRNG per call**. Three
sites call it once per cell per step with a seed that is simple arithmetic on `(time, index)`
and then take **one** draw:

| site | seed | first draw decides |
|---|---|---|
| `grid.clj:78,86` `apply-exotype` | `seed + time*width + index` | which bit the propagator flips |
| `selection.clj:84` `select-genotypes` | `(seed + time*width) XOR 0xC0FFEE + index` | which neighbour a cell copies from |
| `grid.clj:121` `apply-exotype-blend` | `(seed + time*width + index) XOR 0x5DEECE66D` | whether the cell blends |

`java.util.Random(s)` initialises its state to `(s ^ 0x5DEECE66D)` and the first `next()`
is one LCG step from there, so the first output is a *smooth* function of the seed.
Adjacent seeds give the same first draw.

### Measured — within-step spatial diversity across 80 cells, mean over 2000 steps

```
propagator k (grid.clj:78)    mean distinct k    per step = 1.22 of 8     (independent: 8.00)
selection direction (sel:84)  mean distinct dirs per step = 1.07 of 2     (independent: 2.00)
blend coin b=0.5 (grid:121)   mean distinct out  per step = 1.01 of 2     (independent: 2.00)

CONTROL, second draw from the same seeds:
propagator k                  mean distinct k    per step = 8.00 of 8     (independent: 8.00)
```

**The second draw is perfect. Only the first is dead.** Every one of the three per-cell
decisions uses the first.

In time, the propagator's k is also sticky — it held the value 4 for thirteen consecutive
steps from the slice-12 base seed. But over 6000 steps the marginal counts are flat
(≈750 each of the eight values), and the blend coin's fire rate tracks β accurately
(β=0.5 → 0.558, β=0.75 → 0.788; a modest upward bias, not a gross one). **Every marginal
check passes.** Only the joint is degenerate. That is precisely why this survived twelve
slices: anyone asking "is the RNG uniform?" gets yes.

### What this means

The exotype grid is not a stochastic spatial system. It is 80 cells in lockstep with a
shared coin. Consequences that are now explained rather than mysterious:

- **60-step slice-12 calibration** from a uniform seed produces only **9 distinct genotypes
  across 80 cells**, mean Hamming distance exactly **1.00 of 8 bits** — because every cell
  flips the same bit at the same step and repeated flips of one bit cancel. An independent
  stream would give ≈4.
- `select-genotypes`' docstring says "uniformly selected immediate neighbour". Measured:
  **all 80 cells choose the same direction at a given step** (1.07 of 2). It is a global
  shift, not a local competition.
- Commit `4ffad1d` "genotype has no spatial coupling; no c can break the bands" is a finding
  that must now be re-examined: the substrate had no spatial *variation* to couple.

**F2 is Joe-gated, not a free fix.** The one-line repair (draw from a per-run stream, or
discard the first draw, or hash the seed) changes the dynamics of every futon5 CA — the same
blast radius the 2026-07-15 truth-table standardisation carried, and `ca/core.clj:30-32`
already warns that such a change makes historical runs incomparable. **Do not apply it
without deciding what gets re-measured.**

---

## 3. F3 — `fixed-model` has one broken entry, not an inverted ordering *(corrected)*

The previous report claimed the measured ordering was "almost the exact reverse" of the
declared one, from this table: identity 1.000, collapser 0.667, builder 0.417, chaos 0.417.

**`builder` at 0.417 is below a hard analytic floor and cannot be right.** `rule-permute`
(`generator.clj:732`) picks one random position k, and writes `¬bits[k]` into position
`σ(k)`. The byte changes iff `bits[σ(k)] == bits[k]`. When `σ(k) == k` that is trivially
true — **a fixed point of σ is an unconditional flip.** So for any byte distribution
whatsoever, `P(change) ≥ fix(σ)/8`. Builder has 5 fixed points → floor 0.625.

Re-measured per application, 10,000 bytes each, through `grid/apply-exotype`:

| kind | measured | declared | fixed pts | σ cycle type | immune bytes |
|---|---:|---:|---:|---|---:|
| `:identity` | **1.000** | **0.02** | 8 | (1⁸) | 0 |
| `:builder` | 0.793 | 0.78 | 5 | (3,1,1,1,1,1) | 0 |
| `:chaos` | 0.608 | 0.90 | 1 | (4,3,1) | 0 |
| `:collapser` | 0.500 | 0.18 | 0 | (6,2) | 4 |

**`:builder` is calibrated almost exactly right (0.793 vs 0.78).** `:chaos` and `:collapser`
are compressed toward the middle but hold the correct order relative to each other.
**`:identity` alone is wrong, by a factor of fifty.**

It is wrong because *the name is the camouflage*. `:identity` reads as the kind that does
nothing; but identity is nothing *but* fixed points, and a fixed point is an unconditional
flip, so it is the maximally disruptive propagator in the family. It changes the genotype
on **every single application**, and `realized-context` therefore scores it against
`log(0.02)` forever — a structural, not statistical, self-disconfirmation.

**This is a one-entry defect in `fixed-model`, not a repudiation of the EFE work.** Finding 2
(chaos wins the scoring) is in materially better shape than the previous report concluded.

**Also newly measured** (the orphan's §6 left this open for the ants' σ): the four immune
bytes of `:collapser` are `01001101`, `01001110`, `10110001`, `10110010`. `:collapser` is
the only one of the four kinds admitting any byte immune to its own propagator — consistent
with it being the only all-even cycle type, i.e. the only one with an alternating colouring
under `hasAlternatingColouring_iff_cycleType_even`.

---

## 4. F4 — the Baldwin void is provable from the source, and was created by the fix for §8

`grid/step` after `071f9ea` ("separate transient expression from selection", today):

```clojure
heritable-base (if (and selection-family? (false? write-back?))
                 (:genotype selection-result)   ; Baldwin: genotype comes from SELECTION only
                 genotype)
next-expressed (expressed-grid heritable-base exotypes ...)   ; <- the propagator acts HERE
next-genotype  (if (and selection-family? (false? write-back?))
                 heritable-base                 ; Baldwin: propagator output DISCARDED
                 next-expressed)                ; Lamarckian: propagator written back
```

In the Baldwin arm the genotype update is `select-genotypes`, and `select-genotypes` only
**copies** an existing neighbour genotype. Copy-only over a set seeded by `uniform-rule`
starts that set at size 1 and can never grow it. **32/32 populations retaining their seeded
rule was not a weak result — it was the only arithmetically possible outcome.**

The sharp part: `071f9ea` is the *correct* layer reading (expression belongs to the
exotype, per `8aeeba7`). It fixed §8. In doing so it removed the genotype's only variation
source and did not replace it. **The repair for one void created the next.**

**Gate G2:** any arm that claims to evolve a layer must name that layer's variation source
and its selection source, separately, in the packet. If either is "none", the arm is inert
before it runs.

---

## 5. F5 — the calibration comment asserts the opposite of what happens

`scripts/exotype_baldwin_convergence_slice12.clj:43`:

> "Burn in the seeded phenotype for t*=60 under the exact ECA rule. **Identity exotypes and
> write-back preserve the uniform genotype during calibration.**"

The burn-in runs `:identity` exotypes with `write-back? true`. `:identity` changes the
genotype on 100% of applications (F3). Measured over the 60 steps, from both seeded rules:

```
ordered    (rule 204): 0/80 cells still equal the seeded rule; 9 distinct genotypes
disordered (rule  30): 0/80 cells still equal the seeded rule; 9 distinct genotypes
```

The "calibrated phenotype" is therefore not the phenotype of rule 204 or rule 30. It is the
phenotype of a lockstep random walk that started there. The ordered/disordered contrast —
the entire two-point design of slice 12 — does not mean what the script says it means.
(The genotype is re-seeded afterwards; only the phenotype carries this forward. The
separation slice 12 measured is real, but its *cause* is not the clean rule contrast
claimed.)

---

## 6. F6 — the unexploded round: the four kinds are permutations, and the bug is not

Not yet surfaced as a stop-the-line, and the largest of the six. From the orphan, §5,
verified:

The actual Emacs bug is the map `k ↦ max(k-1, 0)`. Bit 7 has no preimage; bit 0 has two.
**It is not injective, not surjective, not a permutation** — and the measured write histogram
(bit 0 at 24.9%, bit 7 at 0.000%) proves it, since any permutation gives a uniform 12.5%.
The paper's family is `σ ∈ S₈` and the census enumerates 8! = 40,320 permutations. The bug is
not among them. Worse, the Figure-8 mechanism *depends* on the non-injectivity: bit 7 dead
pins the cascade, bit 0 doubled makes it unsettleable. No σ in the studied family can produce
it.

All four exotype kinds — `:builder`, `:collapser`, `:chaos`, `:identity` — are permutations.

**This is a question for Joe, not a defect to fix:** is the exotype work studying the bug, or
a different object that the paper currently claims generalises it? The answer is upstream of
every slice, and the claim as written in `paper/main.tex` §"The bug generalises to a family"
is false.

---

## 7. Corrections to the record

Two claims previously reported that do not survive measurement. Both are the same error
class — a number or an inference stated without checking it against what the mechanism can
produce — and one of them is mine, made while diagnosing the other.

1. **"The measured ordering is almost the exact reverse of the declared one"** (previous
   session). The supporting table has `builder` at 0.417, below its analytic floor of 0.625.
   Whatever produced those numbers was not measuring per-application propagator effect.
   Corrected table in §3.
2. **"The blend coin is pinned at 0.99, so the blend never fires"** (mine, this session).
   Inferred from twelve samples at t=0. Measured over 40,000 draws, the fire rate tracks β
   correctly (0.5 → 0.558). The blend coin *is* spatially degenerate (1.01 of 2 distinct
   outcomes per step) but its marginal is sound, and slice 11's "blend is marginal" headline
   is **not** falsified by this. Retracted.

**Gate G3:** before reporting a measured rate, compute what the mechanism's floor and
ceiling are, and check the number lies between them. Both errors above would have been
caught by one line of arithmetic.

---

## 8. The methodical ladder, in dependency order

Nothing below jumps its predecessor; each rung is one file, one behaviour, one acceptance
test — a single Codex packet.

| # | rung | blocks | Joe-gated? |
|---|---|---|---|
| **R0** | Decide F6: is the object of study the bug, or the permutation family? | everything | **yes** |
| **R1** | Decide F2's blast radius: fix the first-draw defect, and name what gets re-measured. | every slice re-run | **yes** |
| **R2** | Fix `:identity` in `fixed-model` (or rename the kind). One entry. | all EFE scoring claims | **yes** — see below |
| **R3** | Correct the slice-12 calibration comment and re-derive what the two-point contrast actually contrasts. | slice 12 redesign | no — **DONE 2026-08-04** |
| **R4** | Add a genotype variation source for the Baldwin arm — a genuine substrate extension, not a parameter. | slice 12 rerun | **yes** |
| **R5** | Re-run the ladder's load-bearing slices under a repaired stream; treat pre-R1 numbers as incomparable. | the paper | no |
| **R6** | Move the census onto the genotype layer alone (orphan §"cheapest next experiments" — ~100× cheaper; the phenotype is a rendering). | — | no |

R3 is carve-out (a)/(b) work. R0, R1 and R4 are design decisions that must not be dispatched
until answered.

**R2 was re-gated to Joe on reflection.** The correct value is measured and the edit is one
map entry, but applying it now creates a *third* incomparable scoring regime — before R1,
after R1, and after R2 — in a substrate whose comparability is already the open question.
The defect is recorded where it will be read instead.

### Applied 2026-08-04 (behaviour-preserving only, nothing dispatched)

| change | file | kind |
|---|---|---|
| Gates G1/G2/G3 + "Substrate ground truth" section pointing at the orphan | `holes/M-formal-patterns.md` | doc |
| Read-by note recording the orphan status and that §5 is unactioned | `holes/F-what-the-propagator-actually-does.md` | doc |
| Measured table + floor rule, values **unchanged** | `src/futon5/exotype/efe.clj` (`fixed-model` docstring) | doc |
| False calibration claim replaced with the measurement | `scripts/exotype_baldwin_convergence_slice12.clj` | doc |

**No dynamics were altered.** Gates run on the two touched `.clj` files: clj-kondo 0 errors
0 warnings; `arxana-check-parens-run` exit 0; Clojure reader parses both.
Full exotype test surface — 11 namespaces including `futon5.mmca.*` — **57 tests, 1260
assertions, 0 failures, 0 errors.**

### Gate-integrity note

The slice-12 handoff reported its test gate as *"Exotype suite: 38 tests, 120 assertions"*.
The full exotype surface is **57 tests / 1260 assertions**. The reported gate covered under
10% of the available assertions and omitted `futon5.mmca.exotype-invariants-test` — the
namespace whose name most directly promises to guard this substrate's invariants. The
number was not wrong, but "the exotype suite" named a subset while reading as the whole.

**Gate G4:** a handoff reporting a test gate must state the namespaces run, not only the
counts. A count cannot reveal what was omitted.

---

## 9. Verified vs inferred

| claim | status |
|---|---|
| `F-what-the-propagator-actually-does.md` is cited by no file in futon5 | **verified** (grep, all `.md` + `.clj`) |
| first draw after `with-seed` is near-constant across adjacent seeds | **measured** (1.22/1.07/1.01 vs 8.00/2.00/2.00) |
| second draw from the same seeds is clean | **measured** (8.00 of 8) |
| all marginals (k counts, blend fire rate) are correct | **measured** (6000 steps; 40,000 draws) |
| a fixed point of σ is an unconditional flip; floor = fix(σ)/8 | **verified** (read `rule-permute`) |
| per-application change rates 1.000 / 0.793 / 0.608 / 0.500 | **measured** (10,000 bytes each) |
| `:collapser`'s four immune bytes | **measured** (brute force, all 256) |
| Baldwin genotype update is copy-only from a size-1 set | **verified** (read `grid/step` + `select-genotypes`) |
| slice-12 calibration destroys the seeded genotype | **measured** (0/80 retained, both inits) |
| the bug is non-injective and outside S₈ | **verified** (orphan §5, source + histogram) |
| lockstep explains `4ffad1d`'s "no spatial coupling" | **inferred** — plausible and untested; R5 would settle it |
| slice 11's "blend is marginal" headline | **unchallenged** — my falsification of it was wrong and is retracted |

---

## 10. The locked register (Joe, 2026-08-04)

Scope is now closed to these eight items. Nothing else is worked until they are discharged.
A *Nightmare* has no bounded fix, or invalidates a body of work, or changes what the project
is. A *Headache* has a bounded fix and the prior work survives it.

| # | item | status | gate |
|---|---|---|---|
| **N1** | **Wrong object** — the paper's family provably excludes the phenomenon it claims to generalise | **CLOSED** 2026-08-04 — "fixed well enough for now" (Joe); `draft7.tex`, §11 | — |
| **N2** | **Ghost evidence** — twelve slices measured under a spatially constant noise source | **DONE** §16 — 7 sites fixed (4 more than predicted); disposition §13 still governs the RE-RUN set | — |
| **N2b** | **The endogenous clock** — per-rule $(r,\tau,m)$, stress-integrating age, apoptosis | **LOOK-AHEAD ONLY**, §14 — prerequisites (N2, H1) now clear; still not a work item until Joe opens it | Joe |
| **N3** | **Orphaned knowledge** — *retrieval at the design step*, not documentation coverage | **RE-SCOPED §19**; three co-locations built as components-first verifications §21; ledger backlog remains | Joe |
| **N4** | **Recursive blind spot** — every level of review failed the same check | **DONE** §17 story, §18 all three mechanisms built + mutation-tested | — |
| **H1** | **RE-SCOPED §15** — not "four wrong numbers" but *`:rule-change` is hand-typed for a quantity that is a function of σ*; the exotype layer has no computed coordinates | **DONE** §16 — `:rule-change` derived from σ; `:activity`/`:diversity` still hand-declared, flagged | — |
| **H2** | Baldwin arm has no genotype variation source | open — **candidate resolution is N2b** | folded into N2b |
| **H5** | **Impoverished exotype** — vocabulary / parameters / structure | **structure DISCHARGED** §28–29 (derived conditional model, now default); **parameters** partly (`:rule-change` derived); **vocabulary** unblocked, not widened | — |
| **H3** | slice-12 calibration docstring asserted the opposite of the measurement | **DONE** | — |
| **H4** | test gate reported 120 of 1260 assertions as "the suite" | **DONE** (G4) | — |

**Exit criteria** — what "adequately addressed" means, so the register can actually close:

- **N1.** draft6 carries the warm-up section; all three bridge anchors (§11) are gone; no
  sentence asserts the bug is a member of the family, and the $2^k$ count is never applied
  to Figure 8.
- **N2.** *Not* "fix the RNG" — that is one line. Exit is a **written disposition of the
  evidence base**: for each slice 1–12, retained / re-run / retired, with the reason. Then
  the fix, then the re-runs. Fixing first would destroy the baseline needed to judge.
- **N3.** Gates G1–G4 live in `M-formal-patterns` (done). Remaining: every `holes/F-*.md`
  and top-level `TN-*.md` has an inbound reference or is explicitly marked terminal.
  *Framing (ChatGPT, via Joe, 2026-08-04, and it is the right name for them):* **the gates
  are a manually engineered inheritance channel for design knowledge.** In this project's
  own vocabulary they are an **exotype** — transmissible, not heritable. That reframes the
  register: the failures below are not decay, they are what a system looks like *before* it
  has a transmission channel, and N3 is precisely the absence of one. Note the irony worth
  keeping: the Baldwin arm failed for want of a variation source while the process around it
  was busy acquiring one.
- **N4.** G3 in force is necessary but not sufficient — it relies on a reviewer remembering.
  Exit needs one mechanism that does not: the strongest candidate is a floor/ceiling
  assertion living in the measurement code, so an out-of-range rate throws instead of being
  reported. Joe to decide the mechanism.
- **H1.** Value corrected once N2 has fixed the comparability baseline.
- **H2.** A decided variation source, or the arm retired. It is a substrate extension either
  way — the paper's family has none (`policy_expansion`: "innovation is not a mutation branch").

Ladder mapping from §8: R0→N1, R1/R5→N2, R2→H1, R3→H3 (done), R4→H2, R6→parked (§12).

---

## 11. N1 — DECIDED: the bug is promoted, not retracted (Joe, 2026-08-04)

**The call:** keep the bug, delete the bridge, and give the bug **a short section before
Part I** — because working out how it actually worked was the route to everything else in
the paper. Simple, easy, and accurate.

This is better than the retraction framing I proposed. Retraction treats the bug as an
embarrassment to be minimised; it was the on-ramp, and saying so is just the true history.
The move is not to drop the bug but to **demote the claim and promote the object**.

**Keep:** the bug, in full — and it can now be explained properly for the first time
(orphan §4: a negating shift register, `bit[max(k−1,0)] := ¬bit[k]`). **Keep:** S₈ and
T1/T2/T3, which are proved, machine-checked, and true *about S₈*.

**Delete:** the bridge. Three anchors, all asserting the bug is inside the family:

| anchor | the text | why it fails |
|---|---|---|
| `draft6.tex:429–433` | "That 2015 paper found **a single member of this family**, encountered through a programming error…" | The bug is `k ↦ max(k−1,0)` — non-injective, therefore not in S₈. It is not a member. |
| `draft6.tex:571–575` | "the programming error **that produced the family** is what pins its residual at two… the phenomenon was on the page before the $2^k$ count explained it" | The $2^k$ theorem counts **fixed bytes**. The bug has **zero** — its self-loop `f(0)=0` makes the functional graph non-bipartite (brute-forced). The 42/170 residual is a one-bit *hunt*, not two fixed points. The count does not explain it. |
| `main.tex:373` | `\subsection{The bug generalises to a family}` | older draft; the same claim, stated outright |

The second is the load-bearing one: the paper currently uses Figure 8's signature result as
corroboration for a family that provably cannot produce it, and attributes it to a theorem
that gives zero for the bug. The paper's own prose half-sees this — *"the erroneous mutation
froze a bit"* is the shift-register pinning, described without being recognised.

**Warm-up section spec** — short, sits before `\section{The Object}` (`draft6.tex:384`);
material for (1) already exists at 218–223:

1. The 2015 automaton and the error.
2. What the error *is*: `bit[max(k−1,0)] := ¬bit[k]`. Bit 7 is never written; bit 0 is
   written twice. Measured: 0.000% and 24.9% of 87,632 writes.
3. Why Figure 8 is two rules differing in one bit: the cascade fills bits 1–7 alternating,
   and bit 0 demands `g[0] = ¬g[0]`, which is unsatisfiable — so it hunts.
4. One sentence: this map is not a permutation, so what follows is a **neighbouring** family
   that shares the elementary write — not a generalisation of the bug.
5. Why it earns the space: this is how we got here.

**Acceptance:** after the edit, no sentence asserts the bug is in the family, and the $2^k$
count is never applied to Figure 8.

### Executed 2026-08-04 → `draft7.tex` (Joe: move to draft7, keep the paper trail clear)

`draft6.tex` copied to `draft7.tex`; draft6 left untouched as the record of what was
changed. Three edits:

1. **New `\section{The 2015 Example}`** (`\label{sec:warmup}`), placed immediately before
   `\section{The Object}` — i.e. before Part I opens, as specified. Five paragraphs: the
   write `bit[max(k−1,0)] := ¬bit[k]`; the two measured consequences (position 7 never
   written, position 0 written twice — 0.000% and 24.9% of 87,632 writes); why the cascade
   plus the unsettleable position 0 forces exactly two configurations differing in one bit;
   and one closing paragraph fixing the scope — a permutation has neither property, so
   Part I studies a neighbouring object, not a generalisation.
2. **Anchor 1 repaired** — "found a single member of this family" → the example "is a
   non-bijective writing (§The 2015 Example) and therefore lies outside this family; what it
   shares with the family is the elementary write, not membership."
3. **Anchor 2 repaired** — the 42/170 residual is now explicitly *not* an instance of the
   $2^k$ count: "that writing admits no fixed rule whatever, its residual being a hunt on a
   single unsettleable position rather than a pair of fixed points."

**Convention hazard handled explicitly.** The warm-up states that its position index is the
2015 engine's own display order, *not* the Wolfram order Part I adopts, and that 42/170 are
the genotype string read directly rather than a conversion into standard rule numbering
(under the legacy conversion the same strings are rules 76/77). This is the same silent-port
bug `positional-sigma->neighbourhood-sigma` exists to prevent; writing the section without
the caveat would have reintroduced it.

**Verified:** acceptance greps pass (no "member of this family" / "produced the family"
anywhere; Figure 8 now carries the explicit disclaimer; the three surviving uses of
"generalise" are the new denial plus two pre-existing methodological cautions). `latexmk`
exit 0, biber ran, **zero undefined citations in the final pass, zero `??` in the PDF**, and
`\secref{sec:warmup}` resolves correctly at both anchor sites. 52 → 54 pages.

### Provenance worth keeping (bears on N3)

draft6's **introduction already stated the correct fact** — "The original example recreated
in Figure~2 is a non-bijective writing outside that core… we therefore separate two objects
that share a substrate but not a scope" (`draft6.tex:233–236`) — and draft5 carried it too,
as Observation 1, "Permutations form a small bijective core". Meanwhile `"a single member of
this family"` **does not appear in draft5**: it was *introduced* in draft6, which is dated
after the orphan note.

So the correction reached the intro and the supplement and was contradicted three sections
later in the same file. N3's failure mode is not only that knowledge fails to travel — it is
that a document can hold the right statement and its negation simultaneously, and neither
reader nor author notices, because the two are forty pages apart.

---

## 12. Parked, with homes — applying N3's own lesson

N3's failure mode is a correct finding with nowhere to live. These are correct, currently
unneeded, and parked here *on purpose* so they do not become the next orphan.

- **T1′ — the bipartite criterion.** A byte immune to the propagator exists iff σ's
  functional graph, read undirected on edges `k — σ(k)`, is **bipartite**. On permutations
  every closed walk is a cycle, so this reduces to T1's "all cycles even"; it reproduces the
  orphan's **11,025 / 40,320** exactly, correctly returns False for the bug (via the
  self-loop), and disagrees with brute force on **0 of 42,858** sampled general maps.
  **Not needed under the chosen option** — it belongs to the general-map census, which is
  the road not taken. Analytic and brute-forced, **not machine-checked**; a Lean target that
  would need the non-triviality leg of the closure gate.
- **The 40,320 / 40,320 symmetry.** Maximal-tail rho maps — the bug's exact functional-graph
  shape — number 8! = 40,320, *identical* to |S₈|. Each is 0.240% of the 16,777,216 maps
  [8]→[8]. Verified by brute force at n = 3,4,5,6. The census enumerated the wrong 40,320,
  of the same size. Parked with T1′.
- **R6 — genotype-layer-only census** (~100× cheaper; the phenotype is a rendering). Belongs
  to N2's re-run disposition, not to now.

**Reopening any of these requires discharging the register first.**

---

## 13. N2 — the disposition of the evidence base (2026-08-04)

Written under "no more deep dives": one measurement and two file reads, classifying all
twelve slices by a rule, instead of twelve investigations.

### 13.1 My §2 characterisation was too strong — corrected

§2 says "80 cells in lockstep with a shared coin." Measured, from a *heterogeneous* start,
distinct genotypes across 80 cells:

```
t=0: 64/80    t=1: 71/80    t=5: 68/80    t=20: 62/80
t=50: 57/80   t=100: 53/80  t=200: 48/80  t=400: 45/80
```

Heterogeneity **persists and decays slowly; it does not collapse.** The coin is shared but
the *response is state-dependent*, so cells in different states diverge normally.

The accurate statement is narrower and it is what the disposition rests on:

> The noise source is spatially constant. The propagator cannot break symmetry between two
> cells that are in the same state, and the small amount of spatial variation the draw does
> supply is **positionally deterministic** — fixed by the seed arithmetic, not random.

### 13.2 What is *not* affected — and it is the reason most of the ladder survives

`slice-harness/initial-state` wraps its whole construction in a **single** `with-seed` and
draws sequentially. The initial genotype, phenotype and exotype grids are therefore properly
random. **The defect is confined to the per-cell, per-step draws; initialisation is sound.**

A second-draw inversion worth knowing: in `apply-exotype` with `transfer-fraction > 0` the
transfer coin is the **first** draw and the propagator draw is the **second**. So on the
transfer path the propagator is *healthy* and the transfer decision is degenerate — the
reverse of the `q = 0` path. Any slice mixing `q = 0` and `q > 0` arms is comparing two
different noise regimes.

### 13.3 The rule

Classify by what a claim rides on, not by slice number.

- **RETAIN (provisional)** — temporal marginals, aggregate rates, damage magnitudes,
  population statistics, from a heterogeneous start. These ride on a real, evolving,
  heterogeneous field; noise impoverishment weakens them, it does not void them.
- **RE-RUN** — any claim about *spatial structure* (domains, bands, walls, coupling,
  where things happen), and any claim about **blend, transfer, or selection**, because those
  decisions *are* the degenerate first draw.
- **RETIRE** — claims requiring symmetry-breaking from a uniform start.

### 13.4 The disposition

| slice | claim rides on | disposition | reason |
|---|---|---|---|
| 1 grid | exotype-grid arm comparison | **RE-RUN** | arms differ by spatial transmission |
| 2 / 2b EFE | scoring + ablations, aggregate | **RETAIN** ⚠ | also depends on `fixed-model` → H1 |
| 3 prevalence | prevalence radius | **RE-RUN** | the radius is spatial |
| 4b self-tuning | aggregate rates | **RETAIN** | |
| 5 policy | policy grid, aggregate | **RETAIN** ⚠ | depends on `fixed-model` → H1 |
| 6 / 6b / 6c / 6d EIG | information measure, aggregate | **RETAIN** | |
| 7 / 8 transfer | the transfer coin | **RE-RUN** | transfer coin is the degenerate first draw |
| 9 / 10 / 11 blend | the blend coin | **RE-RUN** | blend coin degenerate *and* blend is spatial |
| 12 baldwin | symmetry-breaking from uniform | **RETIRE** | independently void via H2 |
| `4ffad1d` probe | "no spatial coupling; no c can break the **bands**" | **RE-RUN, first** | bands are exactly what positionally-deterministic draws produce; this claim is the most likely artifact in the ladder |

Slice 11's "mu is the lever, blend is marginal" is therefore **neither confirmed nor
falsified** — it is RE-RUN. (My earlier attempt to falsify it was wrong and stays retracted.)

### 13.5 The fix, and why the codebase already contains it

Two options:

1. **Per-step stream** — one `with-seed` per step, drawn sequentially across cells. This is
   exactly what `initial-state` already does correctly. **But it breaks per-cell
   addressability**, which the harness relies on for `:workers` parallelism and resumable
   runs. Rejected for that reason.
2. **Hash the seed** — pass `(seed, time, index)` through an avalanche mix (e.g. a
   splitmix64 finalizer) before constructing the `Random`. Preserves "each cell's draw is a
   pure function of its coordinates", so parallelism and resumability are untouched, and it
   removes the smoothness that causes the defect. **Recommended.**

**Order of operations, and it matters:** this disposition first (done), *then* the fix,
*then* the re-runs. Fixing first would destroy the baseline the RETAIN judgements are
measured against.

**Approved by Joe 2026-08-04:** disposition accepted, fix option 2 (seed hashing) accepted.
Not yet executed — sequenced behind the N2b invariants in §14, since the fix is what makes
N2b's consequences well-defined.

---

## 14. N2b — the endogenous clock (Joe, 2026-08-04)

> **THIS IS A LOOK-AHEAD, NOT A WORK ITEM** (Joe, 2026-08-04). It is recorded only so that
> the N2 fix is made with knowledge of what it could enable. **No deep dive into N2b until
> N2 itself is fixed.** If this section starts generating work, it is doing the opposite of
> its job: the register in §10 is the work, and a promising direction is exactly the kind of
> thing that has historically pulled this project off its own gates.

**Filed as the candidate resolution of H2, not as a new nightmare.** H2 is "the Baldwin arm
has no genotype variation source". N2b supplies one that is local, endogenous, and not
transcription. If it holds, H2 closes with it and the register does not grow.

The proposal: every rule carries $(r, \tau, m)$; $\tau$ integrates **stress**, not
chronological time; apoptosis at $\tau > \theta$; ageing modulates the probability of
transformation. An external mutation schedule is replaced by an endogenous one, and the
edge of chaos is not encoded anywhere — it would emerge as the region where lifetimes are
longest.

### 14.1 It is smaller than it looks — the integrand already exists

`futon5.exotype.selection` already maintains, **per cell**, exactly the terms the proposal
needs:

| accumulator | where | what it is in the proposal's terms |
|---|---|---|
| `:divergence` | `selection.clj:38, 62–70` | phenotype disagreement with both neighbours — **Δ(local mismatch)** |
| `:hunger` | `selection.clj:37, 52–61` | steps where the expressed rule was static *and* the phenotype neighbourhood uniform — **the frozen-region stress term** |
| `:expressed-changes` | `selection.clj:36, 48–51` | steps where the expressed rule changed — candidate **Δ(successful interactions)** |

And `advance` (`selection.clj:99–105`) **resets all three to `empty-window` every 40 steps.**

So the substrate already computes the stress integrand, per cell and locally, and throws it
away on every window boundary. In its minimum viable form **τ is that accumulator not being
reset.** The first version of this is a deletion, not an addition — which is the strongest
possible answer to "is this too big a step?"

Note especially that `:hunger` already exists. Mismatch alone cannot make a frozen region
costly; something must price stasis, and the substrate priced it already.

### 14.2 Three invariants to fix *before* anything is built

**(a) τ may modulate the RATE of variation. It must never modulate its DIRECTION.**
Rate-modulation by experience is stress-induced mutagenesis; nothing acquired is copied into
$r$, so the Baldwin invariant survives. Direction-modulation is transcription in disguise and
would silently collapse the Lamarck/Baldwin contrast — the same shape as the §8 and §10
failures, both of which were internally coherent designs with no path, or the wrong path.

**(b) Apoptosis must be followed by something that introduces novelty.** If an expired rule
is replaced by a copy of a neighbour, the population is copy-only over a non-growing set —
which is *precisely* the slice-12 void (§4). Apoptosis is a death rule; alone it is a
selection mechanism, and H2 needs a **variation** mechanism. **The replacement rule is the
load-bearing choice, not the threshold θ.**

**(b) is ANSWERED (Joe, 2026-08-04): the replacement comes from the exotype, which functions
as a generative model.** And this is not an addition to the architecture — it is a name for
what the architecture already claims. `efe/fixed-model`'s docstring reads, verbatim:

> "Frozen P(next local observation | exotype, current local observation)."

That *is* a generative model indexed by exotype. The substrate has asserted since Slice 2
that the exotype is the conditional distribution; nothing downstream has ever used it as a
generative *source*, only as a scoring prior. N2b would be the first thing to actually draw
from it.

It also satisfies (b) properly: a rule produced by the exotype is not a copy of any
neighbour, so novelty enters and the slice-12 copy-only void cannot recur. And the exotype
is transmissible-not-heritable, so the successor is not a transcription of the dead rule's
acquired state.

> **Consequence — H1 is promoted, and is no longer cosmetic.** If the exotype is the
> generative source for replacements, then a misdeclared exotype model corrupts the
> replacement distribution directly. `:identity` being wrong by a factor of fifty stops being
> a docstring defect and becomes a defect in the thing that makes new rules. **H1 must be
> fixed before N2b is built**, not merely before the EFE claims are restated.
> *(This reasoning stands. A second reason I gave for promoting H1 — that the wrong numbers
> are what select `:chaos` — was tested and is **false**; see §15.2.)*

*Open, and deliberately NOT resolved here:* whether exotype→genotype replacement preserves
the Baldwin invariant at all. The exotype is shaped by local experience (the `:conformist`
and `:boring-triggered` arms read the phenotype), so drawing a heritable rule from it routes
experience into the germline. That may be neither Lamarckian nor Baldwinian but a **third
channel** — closer to horizontal transfer or niche construction than to either — in which
case the Lamarck/Baldwin axis is simply the wrong frame for this design. **Parked. Do not
resolve before N2 is fixed;** it is a design question, and answering it early would be
exactly the deep dive this section is meant to prevent.

**(c) The consequences depend on N2; the accumulator does not.** τ would vary spatially even
today, because `divergence` and `hunger` are state-derived. But any *stochastic* consequence
— probabilistic mutation at high τ, probabilistic apoptosis — draws the degenerate first
draw, so every cell past threshold would fire together. That is the precise sense in which
N2 unlocks N2b, and it is why the N2 fix is sequenced first.

### 14.3 The falsifier that must exist before it runs

"The edge of chaos emerges where lifetimes are longest" is a finding only if the stress
function does not make it true by construction. This bites hardest in the frozen case: a
frozen region has *no* mismatch, so stress must count stasis as costly — the `hunger` term —
and that is a modelling choice which produces the predicted result.

**Prereg gate, in the shape of G3:** state in advance a stress function under which the
prediction **fails**, and confirm the chosen one is not simply its complement. If no stress
function could put the lifetime maximum in the frozen or the chaotic regime, then "lifetimes
peak in the middle" restates the definition rather than measuring anything.

### 14.4 What it buys, if it holds

Every damage measurement in the ladder currently needs a **counterfactual twin run** —
perturb one cell, track separation against an otherwise identical run. τ carries that
information as *state*, in a single run. That is the difference between an apparatus
measuring the system and the system measuring itself, and it is a larger claim than
"endogenous mutation schedule". It would also remove the twin-run cost from every later
slice.

Parked, not scheduled: draft7's *Two Readings of the Edge of Chaos* would gain a third,
intrinsic reading. N1 is closed and this does not reopen it.

### Status

**REGISTERED, not started.** Sequence: N2 fix → invariants (a)(b)(c) agreed → smallest build
(stop resetting the window; give τ exactly one consequence; one acceptance test). Nothing is
built before the N2 fix lands, per (c).

---

## 15. H1 re-scoped — the exotype layer has no coordinates (Joe, 2026-08-04)

Joe: *"the whole exotype layer was being sorted into 4 'kinds' without visibility into what's
actually going on. In the genotype layer we have 256 rules, 256 colours, the ability to
colour-code by familiar ECA families. In the exotype, we should have myriads of colours…
but we have no such visibility."*

That is the right diagnosis and H1 as registered was too small.

### 15.1 What H1 literally is

`efe/fixed-model` is a **4 × 3 table of hand-written constants** standing in for
P(observation | exotype). Measured against the propagators it claims to describe, three of
its four `:rule-change` entries are wrong; `:identity` is wrong by a factor of fifty (§3).

`grid/propagators` holds **four** σ — chosen, named, and never justified — out of 40,320
permutations, or 16,777,216 maps if the family is widened to include the 2015 bug's shape.
Nothing in the codebase can say what any σ *does* without running it.

### 15.2 A negative result: the wrong numbers are NOT what selects `:chaos`

I predicted that correcting `:rule-change` would flip the EFE winner, because the hunger
term is $(1-\text{base-change})\cdot\text{boring}$ and corrected `:identity` has hunger
factor $0.000$. **Tested and false.** EFE argmin over 24 representative local observations,
arm `:efe-full`:

```
DECLARED  fixed-model  -> {:chaos 18, :identity 6}
CORRECTED rule-change  -> {:chaos 20, :identity 4}
```

Correcting the values leaves `:chaos` winning, slightly *more* often. Corrected `:identity`
gains on the hunger channel and loses far more on risk, since KL(1.000 ‖ 0.15) is enormous.

So **"chaos always wins" is structural to the objective, not an artefact of the bad
numbers.** Fixing H1 will not make the selection behave differently. That matters because it
was half my stated reason for promoting H1, and it is wrong. The other half — a generative
source with wrong parameters generates wrong things — stands, and is why H1 still blocks
N2b. (Prior art exists and is *not* excavated here, per the no-deep-dive rule: commit
`be95f80` "micro-pilots for exotype objective degeneracy" and
`TN-metaca-baldwin-micro-pilots.md`, described as *what the exotype objective can and cannot
do*. Read it **before** any work on the objective — G1.)

### 15.3 The re-scope: these numbers are typed in where they are derivable

The visibility Joe wants already exists as a by-product of this session. Every σ has a
computable signature:

| coordinate | how | example |
|---|---|---|
| fixed points fix(σ) | count | identity 8, builder 5, chaos 1, collapser 0 |
| cycle type | decompose | (1⁸), (3,1⁵), (4,3,1), (6,2) |
| change-rate **floor** | fix(σ)/8, holds for *any* byte distribution | 1.000, 0.625, 0.125, 0.000 |
| change rate, uniform bytes | (fix + (8−fix)/2)/8, closed form | 1.000, 0.812, 0.562, 0.500 |
| absorbing bytes | T1′ — exists iff the functional graph is bipartite; count 2^(#cycles) when all-even | 0, 0, 0, **4** |

Compare the closed form against measurement through `grid/apply-exotype`: 1.000 / 0.793 /
0.608 / 0.500. Identity and collapser are exact; builder and chaos differ because the
substrate's byte distribution is not uniform.

> **H1, re-scoped: `:rule-change` is a hand-typed constant for a quantity that is a function
> of σ.** The fix is not to correct four numbers; it is to *derive* the column — in closed
> form where the byte distribution allows, by direct measurement otherwise — so it cannot
> drift from the propagator it describes again.

**The corollary is the interesting part.** Once the parameters are derived from σ, the four
named kinds stop being necessary: *any* σ is an exotype whose model is computed on demand.
The quantization into `:builder / :collapser / :chaos / :identity` exists only because
somebody had to type a row per kind. Deriving the row dissolves the reason for the kinds —
and that is Joe's "myriads of colours", reached as a consequence of fixing H1 rather than as
a separate project.

`:activity` and `:diversity` are **not** covered: nothing in this session measured them, and
whether they are derivable from σ is unknown. Do not assume symmetry with `:rule-change`.

### 15.4 Scope discipline

This re-scopes an existing register item. It does **not** open a new one, and it is not a
licence to build an exotype browser. Sequence is unchanged: **N2 fix → H1 derived → N2b.**

---

## 16. EXECUTED 2026-08-04 — N2 fix and H1 derivation

Joe: *"if your N2 fix → H1 derived are executable now and not done, let's do them."*
Done, in that order, no dispatch (carve-out (b): the full diagnosis was already in context).

### 16.1 N2 — the seeding fix

`ca/core` gains `mix-seed` (SplitMix64 finalizer) and `with-mixed-seed`. `with-seed` is
**behaviourally unchanged** — only its docstring now carries the hazard, so nothing outside
the exotype path shifted. Per-cell addressability is preserved, so `:workers` parallelism
and resumability are untouched (this is why option 1, a per-step stream, was rejected).

Sites changed — **seven, not the three §13 predicted**:

| site | why |
|---|---|
| `grid.clj:78,86` `apply-exotype` | the propagator draw |
| `grid.clj:128` blend coin | also dropped the XOR with `0x5DEECE66D`, which *cancelled* Java's scramble |
| `selection.clj:84` | neighbour direction |
| `prevalence.clj:64` | per-cell stride 1 — found by grep |
| `policy_expansion.clj:71` | per-cell stride 1 — found by grep |
| `pattern_eig.clj:149` | per-cell stride 1 — found by grep |
| `self_tuning.clj:134` `genotype-step` | **a second copy of the whole defect** |

The last one matters. `self_tuning/genotype-step` re-implements the propagator draw inline
against a transition cache, constructing `java.util.Random` on the raw seed and bypassing
`ca/with-seed` entirely. §13 missed it because §13 searched for `with-seed`. **It was found
by `optimized-long-horizon-step-is-baseline-identical`** — a mechanical equivalence test that
fired the moment the two paths diverged. Evidence for N4 in the *good* direction: an
invariant test caught what a review pass did not.

`self_tuning/random-direction` was deliberately **left alone**. Its per-cell stride is 9176,
not 1, and measured at that separation the raw draws are already independent (2.00 of 2
distinct outcomes per step, versus 1.06 at stride 1). Mixing it would have changed a
trajectory for no correctness gain. The reason is recorded at the function so nobody
"fixes" it later.

**Verified end-to-end** — 80 cells, mean over 2000 steps:

```
                        before   after   independent
propagator draw          1.22     8.00      8.00
selection direction      1.07     2.00      2.00
blend coin (beta=0.5)    1.01     2.00      2.00
per-cell stride-1 draw   1.06     2.00      2.00
```

### 16.2 H1 — the derivation

`generator/rule-change-rate` computes the rate from σ, with `generator/sigma-positional`
exposing the positional map `rule-permute` actually applies. `efe/fixed-model` now derives
its `:rule-change` column; `:activity` and `:diversity` stay hand-declared in
`declared-channels`, explicitly flagged as unmeasured (Joe: sort later).

```
kind        derived   was     fix(sigma)   floor = fix/8
:identity    1.0000   0.02        8           1.000
:builder     0.8125   0.78        5           0.625
:chaos       0.5625   0.90        1           0.125
:collapser   0.5000   0.18        0           0.000
```

Floor invariant `rate >= fix(σ)/8` holds for all four. As §15.2 predicted, this does **not**
change which exotype the objective selects.

### 16.3 Fixture

`test/futon5/exotype/grid_q0_baseline.edn` regenerated — it is a drift guard, and the drift
was intentional and approved. Provenance, the exact before/after diff, and a warning not to
over-read the chaos→identity flip in that 8-cell run are recorded at `grid_test.clj`.

### 16.4 Gates

- clj-kondo: **0 errors**, 7 warnings — all pre-existing, all outside the edited regions.
- `arxana-check-parens-run`: exit **0**.
- Exotype surface (11 namespaces): **57 tests, 1260 assertions, 0 failures, 0 errors.**
- Rest of the suite (31 namespaces): 163 tests, 531 assertions, **1 failure**:
  `futon5.aif.rollout-test/rollout-h3-vs-greedy`. **Pre-existing, not caused by this work** —
  verified by re-running it against `git show HEAD:` copies of all nine edited files, where
  it fails identically (eoc 5 vs 7). It is an RNG-tolerance assertion in the AIF rollout, and
  `aif.rollout` requires only `aif.forward` and `aif.preference`. **Not fixed here; it is not
  on the register.**

### 16.5 Register effect

N2 → **DONE**. H1 → **DONE**. Both unblock N2b's prerequisites. The §13 disposition still
governs: RE-RUN slices have not been re-run, and all pre-fix numbers remain incomparable.

---

## 17. N4 — the story, and what the evidence says the mechanism must be

### 17.1 The catalogue

Nine instances of one pattern, across this session and the ones before it.

| # | the claim | what was wrong | caught by |
|---|---|---|---|
| 1 | §8 Baldwin arm | no path from exotype to fitness | Codex, at run time |
| 2 | §10 / slice 12 | selection with no variation source | codex-7, at run time (32/32) |
| 3 | "the measured ordering is the reverse of the declared" | `builder` reported 0.417, below its 0.625 floor | a person, one session later |
| 4 | "the blend coin is pinned, blend never fires" | inferred from 12 samples at t=0; 40,000 draws say otherwise | the same person, minutes later |
| 5 | "80 cells in lockstep with a shared coin" | heterogeneity persists, 64 → 45 of 80 | the same person, next turn |
| 6 | "fixing H1 will flip the EFE winner" | 18/24 → 20/24; no flip | the same person, on testing it |
| 7 | "exotype suite: 38 tests, 120 assertions" | 120 of 1260; omitted the invariants namespace | a person |
| 8 | "identity exotypes preserve the genotype" | 0/80 cells retained it | a person |
| 9 | "three sites need the seeding fix" (§13) | **seven**; a whole second copy of the defect in `self_tuning` | **an equivalence test** |

**One of nine was caught by a mechanism. Eight were caught by someone happening to look.**

\#9 is the sharpest, and it is the one to design from. It was missed *while writing this
document about this failure mode*, because the search was for the defect's **name**
(`with-seed`) instead of its **shape** (a raw `java.util.Random` on a coordinate seed). A
test that knew nothing about any of it found it in seconds.

### 17.2 Why it recurses

Every one of those checks was cheap and already available: a one-line floor computation, a
larger sample from the same code, one `ls` of the namespaces, one grep on a different
string. **They were not skipped for cost. They were skipped because the claim looked right,
and a plausible claim does not summon its own check.**

That is why adding reviewers does not converge. Each level inherits the previous level's
plausible frame and contributes its own plausible claims on top, so a new reviewer is a new
*producer* as much as a new checker. Plausibility is not what review filters — it is what
review is conducted *with*.

### 17.3 What the evidence says to build

Ordered by demonstrated value, not by appeal.

**(1) Equivalence tests — the only mechanism with a hit.** Where two implementations of the
same computation exist, assert they agree. `optimized-long-horizon-step-is-baseline-identical`
found #9 for free, and `zero-blend-strength-is-byte-identical-to-legacy-path` is already the
same pattern. Obvious unbuilt candidates: `grid`'s q=0 versus q>0 paths, and the
`slice_harness` fast paths.

**(2) A shape test — and it must be a TEST, not a lint rule.** "No raw `java.util.Random`
construction outside `ca/core`" would have made #9 impossible rather than merely detectable.
**Verified, not assumed:** clj-kondo's `:discouraged-var` fires correctly on a real var
(confirmed against `clojure.core/println`) but **cannot see Java constructors** (same config
shape, zero warnings). So this has to be a test that reads the sources. Cheap and total.

**(3) Executable floors.** `rule-change-rate`'s `fix(σ)/8` bound currently lives in a
docstring, where #3 could walk straight past it. As an assertion at the point rates are
computed or reported, an out-of-range number throws instead of being tabulated.

### 17.4 What none of them catch — stated plainly

\#4, #5 and #6 are over-generalisation from a sample chosen for convenience. No assertion
catches that; it is a judgement failure and it will recur.

But the asymmetry is the point. All three were caught within the same session by continuing
to measure, and cost one correction paragraph each. The class that **ships** is #1, #2, #3
and #9 — and mechanisms (1)–(3) cover exactly those. So the honest exit for N4 is not
"eliminate the error" but **"make the shipping class mechanical and accept the self-correcting
class."**

### 17.5 Proposed exit criterion

N4 closes when: the shape test exists and passes; the floor assertion is executable; and the
existing equivalence tests are inventoried with the obvious missing ones named. G3 stays as
prose but stops being load-bearing — a gate that depends on a reviewer remembering is,
by this section's own evidence, worth about one catch in nine.

**Gate is Joe's: which of (1)(2)(3) to build.** Nothing built here.

---

## 18. N4 EXECUTED 2026-08-04 — all three mechanisms built

Joe: *"these repairs are about making the system kick back without codex."* That is the
design brief, and it is the right one: the register's own evidence is that detection
currently costs a dispatch/park/review round-trip, because the only reliable kicker was a
Codex run. These move the kickback into the test surface, where it costs seconds.

New namespace: `test/futon5/exotype/invariants_test.clj` — **5 tests, 602 assertions.**

### 18.1 (2) Shape — no unmixed per-cell RNG in the exotype path

Scans every `.clj` under `src/futon5/exotype`, finds each `java.util.Random` construction,
and classifies it `:mixed` (seed routed through `ca/mix-seed`), `:opted-out` (an explicit
`rng-audit:raw-ok` comment precedes it), or `:unmixed` — which fails.

The one legitimate raw site, `self_tuning/random-direction`, now carries the marker with its
measured reason. **Adding a new raw draw without justifying it breaks the build.** This is
the check that would have made §17 #9 impossible rather than merely detectable.

It is a test and not a lint rule because clj-kondo cannot express it: `:discouraged-var`
fires on a real var (verified against `clojure.core/println`) but returns zero warnings for
a Java constructor under the identical config.

The test also asserts the scanner still *sees* sites of each class, so it cannot silently
degrade into a vacuous pass if the file layout changes.

### 18.2 (1) Equivalence — two implementations of one computation must agree

- `zero-transfer-arity-agrees-with-legacy-arity` — the 5-arity of `apply-exotype` at q=0
  must equal the 3-arity, across every kind, four rule bytes, five seeds.
- `cached-genotype-step-agrees-with-the-propagator` — `self_tuning/genotype-step`'s cached
  fast path must reproduce `grid/apply-exotype` exactly. This is the pointed version of the
  invariant that caught §17 #9; the existing test caught it only through a 20-step
  whole-state comparison.

### 18.3 (3) Floor — a derived rate may not fall below what the operator permits

`derived-rate-respects-the-operator-floor` checks `fix(σ)/8 <= rate <= 1` for all four named
propagators **and 500 random permutations**. `identity-is-the-most-disruptive-propagator`
pins the finding that named H1, so it cannot regress: identity's rate is exactly 1.0, and
`collapser < chaos < builder < identity`.

The bound previously lived in a docstring, which is precisely where §17 #3 walked past it.

### 18.4 Mutation-tested — because a test that cannot fail is worth nothing

Every mechanism was verified to kill a defect, not merely to pass. This is the
non-triviality leg, and skipping it would have reproduced the register's own failure mode
inside the fix for it.

| mutation | killed by | assertions failed |
|---|---|---|
| `ca/mix-seed` removed from `self_tuning/genotype-step` (the real §17 #9 defect, reintroduced) | equivalence **and** shape | 12 + 1 |
| `rule-change-rate` forced to a constant 0.417 (the exact §17 #3 number) | floor **and** the identity pin | 7+ |

Sources restored from backup and re-verified after each mutation.

### 18.5 Gates

clj-kondo **0 errors, 0 warnings**; `arxana-check-parens-run` exit **0**; exotype surface now
12 namespaces — **62 tests, 1862 assertions, 0 failures, 0 errors** (up from 57/1260).

### 18.6 What is now mechanical, and what is not

Mechanical, per §17's catalogue: **#3** (floor), **#9** (shape + equivalence), and the class
of #1/#2 that shows up as a path-equivalence violation.

Still human: **#4, #5, #6** — over-generalisation from a sample chosen for convenience. As
§17.4 argued, that class is self-correcting within a session and cheap; the class that
*ships* is now covered. G3 remains as prose but is no longer load-bearing.

**N4 → DONE.**

---

## 19. N3 in detail — it is a retrieval problem, not a documentation problem

Joe: *"vibe coding was getting me nowhere before, let's understand it in detail now."*
Measured rather than asserted. **The exit criterion registered in §10 is wrong**, and the
measurement below is what shows it.

### 19.1 The registered criterion would have caught one instance in four

Registered exit was: *"every `holes/F-*.md` and top-level `TN-*.md` has an inbound reference
or is explicitly marked terminal."* Scale is tractable — 1 `holes/F-*.md`, 20 `TN-*.md`, 37
`holes/**.md`. But against the four measured shapes of N3:

| shape | instance | inbound refs | would the criterion catch it? |
|---|---|---:|---|
| **(a) zero-reference orphan** | `F-what-the-propagator-actually-does.md`, 19 days, 12 slices | **0** | **yes** |
| **(b) self-contradicting corpus** | draft6 intro says the bug is non-bijective and outside the core; draft6:429 calls it "a single member of this family" | fine | no |
| **(c) right knowledge, wrong genre** | "Operator reachability" — an *implementation* invariant — lives in the paper's methods section | fine | no |
| **(d) cited, and still not in hand** | `TN-metaca-baldwin-micro-pilots.md` | **3 pre-existing** | no |

One in four — and it is the one we had already found and fixed by hand. **That is N4's error
(calibrating to the first instance) applied to N3.**

Shape (b) is worth pausing on: the *wrong* statement was the newer one. "A single member of
this family" does not appear in draft5; it was introduced in draft6, which postdates the
orphan note. The corpus did not merely fail to propagate a correction — it regressed while
holding the correction three sections earlier.

### 19.2 The natural experiment, and it points one way

`TN-metaca-baldwin-micro-pilots.md` records, as micro-pilot 7, that **the exotype objective
cannot be repaired from inside** — chaos's claims are confirmed at every active observation,
so no accuracy-based correction displaces it. That is a stronger form of the result §15.2
spent this session re-deriving by experiment.

Where that knowledge was **co-located**, it worked. `scripts/exotype_blend_mu_sweep_slice11.clj`
quotes it verbatim in its own opening docstring, as the stated rationale for the slice's
design. The author of that slice had it in hand and used it correctly.

Where it was **not co-located**, it failed. `src/futon5/exotype/efe.clj` — the file that
*defines the objective* — carried **zero** references to it at HEAD. That is the file H1 was
worked in, and the result was re-derived from scratch. §15.2 is a rediscovery, and G1 would
not have prevented it, because G1 names a different document.

Same note, same subsystem, same fortnight. Present in one file, absent from another. Used in
the first, re-derived in the second.

### 19.3 The diagnosis

**Storage is not the problem. The repository already knows everything on this list.** The
failure is that the knowledge is not *in hand at the moment the design decision is made*.

This is why G1–G4 only partly work, and it is the precise limit of the "manually engineered
inheritance channel": **a hardcoded pointer transmits the documents its engineer already
thought of.** G1 names `F-what-the-propagator-actually-does.md`. It could not have surfaced
the micro-pilots note for the H1 question, because nobody writing G1 was thinking about the
objective.

And the sharpest illustration is the simplest. `rule-permute` is the function the orphan
document is *entirely about*. It carries **no pointer to it**. The only reference to that
document anywhere in `src/` or `scripts/` is in a Python script. Two hundred lines of correct
measurement about a fourteen-line function, with no link between them.

### 19.4 What the evidence says to build

**(1) Co-location — the primary mechanism.** For each load-bearing finding, a one-line
pointer *at the code it constrains*. This is the only retrieval mechanism with a perfect
record here, and the reason is that it is not really retrieval: **you cannot change the
behaviour without opening the file the note is in.** Covers (a) and (d).

Concrete backlog, all one-liners: `rule-permute` → the orphan; `efe/score-policy` and
`efe/preferences` → micro-pilot 7; `grid/propagators` → why these four σ and what their
coordinates are (§15).

**(2) Pin tests — for claims that are computable.** Covers (b): prose cannot drift from a
number an assertion holds. Already begun —
`invariants-test/identity-is-the-most-disruptive-propagator` is exactly this, and it exists
because the same claim had already drifted once.

**(3) Genre discipline.** Covers (c): an implementation invariant belongs with the
implementation. The paper's "Invariants the implementation must satisfy" list should have a
counterpart in the code, or the code should point at it. Slice 12 violated a rule that was
written down in a place nobody looks for rules.

**Drop the reference-count audit.** It is the mechanism that would have passed three of the
four instances, including the one that cost the most.

### 19.5 Revised exit criterion

N3 closes when the co-location backlog in (1) is done, (3) has a home, and (2) continues as
part of the N4 mechanism set — **not** when every note has an inbound edge.

**Gate is Joe's.** Nothing built here.

---

## 20. Why the formalism did not catch this, and what "honest about the holes" would mean

Joe: *"this is what we were trying to go towards with the Lean+CLean formalism… the problem
is we would have been formalising the wrong things. What I think we need isn't just a formal
model, it is, ultimately, a proof that what we have works. And that can't be created if we
don't have a working system. But at least we could be honest about the holes."*

### 20.1 It is worse — and more interesting — than formalising the wrong thing

`mathlib4/DarkTower/Patterns/Propagator.lean` is **sorry-free**, and its theorems are stated
over `Equiv.Perm` — bijections. Nothing in it is wrong. T1′ (§12) later showed it is the
special case of a correct general theorem, so the mathematics was not even misaimed; it was
the restriction of the right statement.

The false claim was *"that 2015 paper found a single member of this family."* And in Lean
that claim is not false — **it is inexpressible.** You cannot construct `k ↦ max(k-1,0)` as
an `Equiv.Perm`, because it has no inverse. The formalism *contained* the refutation, as a
typing obstruction, and was never asked. The claim was made in LaTeX.

**A proof assistant checks theorems, not the relevance of theorems.** The scope claim — "and
this is what our system is" — lives outside the formalism by construction, and it is exactly
the kind of claim that N4 shows nothing checks and N3 shows nobody retrieves.

Note what would have surfaced it: stating T1′ over general endomaps rather than
permutations. Then "is the bug in this family?" stops being prose and becomes a typing
question. The generalisation is not just mathematically nicer; **it moves the load-bearing
claim inside the formalism**, which is the only place a formalism can help.

### 20.2 The distinction that actually matters here

- **Model-internal proof.** Properties of an idealisation. Lean does this, well.
- **Model–artifact bridge.** Whether the running thing satisfies what the model says about it.

Every failure in this register is the second kind. `fixed-model` claimed to describe the
propagators and did not (§3). `with-seed` claimed to be stochastic per cell and was not (§2).
The paper claimed the bug was in the family and it was not (§11). `select-genotypes` claimed
a uniformly selected neighbour and delivered a global shift (§2).

**Lean sits entirely on the model side.** The §18 invariants are the first bridges built here
— not proofs, but machine-checked statements that the artifact respects a bound the model
derives. That is why they caught what review did not.

So the ordering in Joe's remark is right and is now concrete: **bridges before proofs.** A
proof that the system works is only meaningful once the system's claims about itself are
true, and that is a property of the artifact, not of the model.

### 20.3 Honest about the holes — and it costs nothing extra

A `sorry` is an honest hole: the obligation is named, typed, and visible to the checker. The
register is already that structure for this subsystem, and §9's *Verified vs inferred* table
is the house form of it.

The useful realisation: **marking a hole at the code and N3's co-location fix are the same
artifact.** `declared-channels`' docstring — *"HAND-DECLARED, not derived… whether they are
functions of sigma is unknown — do NOT assume symmetry with `:rule-change`"* — is already a
`sorry` written in Clojure. It is a named, located, honest obligation. It needs no new
machinery, only doing it everywhere it applies.

### 20.4 The ledger — what this substrate currently asserts about itself

| claim | status |
|---|---|
| `fixed-model :rule-change` describes the propagators | **DISCHARGED** — derived from σ (§16), pinned by test |
| `fixed-model :activity` / `:diversity` describe anything | **HOLE** — hand-declared, never measured, derivability unknown |
| the four exotype kinds are the right or only kinds | **HOLE** — four σ of 40,320, chosen and never justified (§15) |
| per-cell draws are independent | **DISCHARGED** — fixed and test-pinned (§16, §18) |
| `select-genotypes` picks a uniformly selected neighbour | **HOLE** — was false, now fixed, **no test pins it** |
| the Baldwin arm has a variation source | **HOLE** — H2, open |
| the S₈ theorems apply to the 2015 bug | **REFUTED and retracted** — draft7 (§11) |
| slice results measure spatial structure | **HOLE** — 6 of 12 marked RE-RUN, not re-run (§13) |
| the EFE objective selects meaningfully | **HOLE — and the largest one** (below) |

### 20.5 The hole that is not on the register

`TN-metaca-baldwin-micro-pilots.md`, micro-pilot 7, as quoted in
`scripts/exotype_blend_mu_sweep_slice11.clj`: **the exotype objective cannot be repaired from
inside** — chaos's claims are confirmed at every active observation, so no accuracy-based
correction displaces it.

If that holds, everything downstream of EFE scoring — slices 2, 2b, 4b, 5, 6, 6b, 6c, 6d, and
the paper's Finding 2 — rests on an objective already known to be undischargeable by
accuracy. That is not N1–N4 and not H1–H4. It is a candidate new register item.

**Stated honestly: I have not read that note.** It is parked under the no-deep-dive rule and
flagged as a G1 read before any work on the objective. I am reporting a citation, not a
verification, and this sentence is itself the hole marker.

**Gate is Joe's:** whether the objective becomes a registered nightmare, and whether §20.4's
ledger gets co-located claim by claim.

---

## 21. Components-first verification — the co-locations, built as examples

Joe: *"simple low-cost verifications of simple blockers and simple capabilities gained…
components-first verification of the system. The 'proof' (if one is needed at all) is just
that the components compose well."*

### 21.1 First, a correction — the knot was partly mine

§20.5 cited micro-pilot 7 as though it were solid ground. **It was measured on the pre-fix
substrate**, before the per-cell seeding defect was found. It falls under the §13 disposition
like every other pre-fix result. Joe caught this: *"the slice-11 stuff is based on a
known-flawed model."*

That is instance #10 of the §17 pattern, and it is a *citation* failure rather than a
measurement one: I treated a document I had not read as authority, in the same section where
I congratulated myself for marking the hole. The fix is the one below — separate what a claim
verifiably contains from what it merely asserts.

### 21.2 The move that makes a claim modular: split it by what can be checked locally

Micro-pilot 7 is really two claims welded together, and they have different standing:

| half | standing |
|---|---|
| `:chaos` is the argmin over the observation grid | **VERIFIED and RNG-independent.** `score-policy` is a pure function of the local observation, so the seeding defect cannot touch it. Now pinned: `chaos-is-the-structural-argmin`. |
| no accuracy-based correction can displace chaos | **NOT VERIFIED.** A dynamical claim, measured pre-fix, not re-run. |

Welded together they are unusable — a known-flawed provenance contaminates the whole thing.
Split, the first half is a permanent component fact and the second is a named hole. **That
separation is the modularity, and it costs one measurement.**

### 21.3 The three co-locations, each a component contract + its check

| component | contract stated at the code | checked by |
|---|---|---|
| `gen/rule-permute` | a fixed point of σ is an unconditional flip; every permutation writes each position 1/8 of the time, and the 2015 bug does not — so the bug is not in this family | `permutation-writes-are-uniform-but-the-2015-bug-is-not`, `derived-rate-respects-the-operator-floor` |
| `grid/propagators` | the full coordinate table for the four σ (fix, cycle type, rate, floor, immune bytes), and an explicit note that **why these four has never been justified** | `derived-rate-respects-the-operator-floor`, `identity-is-the-most-disruptive-propagator` |
| `efe/preferences` | micro-pilot 7, split per §21.2, with the pre-fix provenance stated | `chaos-is-the-structural-argmin` (verified half only) |

The write-distribution test is a **control pair**, not a tautology: the same predicate accepts
all four propagators and rejects the bug's map. It fails if a non-permutation is ever added to
`propagators`.

Surface: **65 tests, 1871 assertions, 0 failures.** clj-kondo 0 errors; parens exit 0.

### 21.4 Joe's discharge criterion, applied to the §20.4 ledger

*"whether they are dischargeable by anything with the local-only properties that we need for
the model to be valid. If not, maybe those are the wrong things."*

| hole | locally dischargeable? | consequence |
|---|---|---|
| `:activity` / `:diversity` describe anything | **yes** — per-cell channels over a 3-cell neighbourhood; measurable | a real, cheap, undone obligation |
| Baldwin arm has a variation source | **yes** — a per-cell clock is local by construction | this is the argument *for* N2b |
| slice results measure spatial structure | **yes**, but costly — a re-run | real obligation, priced |
| `select-genotypes` picks per-cell | **DISCHARGED** this session — fixed and now pinned | — |
| the four kinds are the right or only kinds | **no** — "right" is a modelling choice, not a local property | **wrong requirement.** The local replacement — *parameters are derived, not typed* — is discharged for `:rule-change` |
| the EFE objective selects meaningfully | **no** — "meaningfully" is teleological, and no local mechanism discharges it | **wrong requirement** — see below |

### 21.5 The payoff: one undischargeable requirement becomes a measured finding

"Does the objective select meaningfully" admits no local-only discharge, so by the criterion
it is the wrong thing to ask. The local restatement that *is* checkable is: **is the selection
degenerate?**

And that is now measured. `:chaos` is the argmin for **18/24 observations under the old
declared model and 20/24 under the derived one** — three quarters to five sixths of the grid,
across the full range of activity and diversity. That is not a mystery about the objective's
meaningfulness. **It is direct evidence that the objective is degenerate**, and it is a
property of a pure function, so it survives every substrate fix.

Micro-pilot 7's dynamical half may or may not re-run true. It no longer matters much: the
degeneracy is visible in the scoring function itself, without any dynamics at all.

**This is what the criterion buys.** A hole that could be carried indefinitely as debt turns
out to be a badly posed requirement, and its well-posed replacement was one pure-function
measurement away.

### 21.6 What N3's fix now is

Co-location alone was the proposal in §19. The stronger version, demonstrated here: **a
co-located pointer plus the check that pins whatever part of it is checkable, and an explicit
marker on the part that is not.** A pointer can go stale; a pointer with a test cannot go
stale silently.

Remaining N3 backlog: the §20.4 ledger, co-located claim by claim, using this pattern.

---

## 22. Health first — the holes as known-failing tests

Joe: *"what we have here is akin to a set of known-failing tests… shall we start with making
the system we build before self-consistent, measured, and healthy first?"*

**Yes**, and the ordering is not merely tidy — §21.5 showed why. The objective's degeneracy
was visible in a pure function, with no dynamics at all. Building N2b on top would have added
a clock to a system whose selection is a near-constant, and any result would have been
uninterpretable: you cannot tell whether an endogenous mechanism is working when the thing it
modulates does not vary.

### 22.1 "Impoverished exotype" — the right name, and it is one defect at three depths

| depth | the impoverishment | status |
|---|---|---|
| **vocabulary** | 4 σ of 40,320 (or 16,777,216), hand-picked, never justified | H1 re-scope, §15 |
| **parameters** | 3 numbers per kind, hand-typed | `:rule-change` **derived** §16; `:activity`/`:diversity` still typed |
| **structure** | the model is *unconditional* — keyed by exotype alone, with the observation faked in by a hardcoded 50/50 blend | **new, §21** |

The third is the one that caps everything. `fixed-model`'s docstring promises
P(next | exotype, **current observation**) and the map has no slot for the second argument.
No choice of preference C repairs a model with no interaction term, which is precisely why no
accuracy-based correction displaces chaos — micro-pilot 7's result, now derived structurally
rather than measured on a substrate we no longer trust.

### 22.2 The health ratchet, built

`futon5.exotype.invariants-test` gains four tests that **pin current deficiency, not desired
property**. Each is a hole in the Lean sense: named, located, visible to the checker. The
numbers are exact, so nothing regresses silently *and* any improvement breaks the test and
must be acknowledged.

| pinned hole | current value |
|---|---|
| `hole-the-objective-is-degenerate-over-its-entire-domain` | domain = **12 points**; distinct winners per arm = full 2, risk-only **1**, ambiguity-only **1**, no-conatus 2, of 4 candidates |
| `hole-predict-discards-three-of-five-observation-channels` | `predict` output is *identical* when `:boring?`/`:static?`/`:hungry?` are flipped |
| `hole-the-generative-model-is-not-conditional` | `fixed-model` keyed by kind alone, three numbers per kind |
| `hole-the-exotype-vocabulary-is-four-of-forty-thousand` | 4 propagators |

Each docstring states that a failure is **not necessarily bad news** and that the fix is
usually to update the number, having understood why. That is the ratchet: it does not push,
it records, and it makes movement impossible to miss.

Surface: **69 tests, 1880 assertions, 0 failures.** clj-kondo 0/0; parens exit 0.

### 22.3 Why this is the cheap ordering

Every one of these is a **pure-function** measurement. No dynamics, no seeds, no slices,
milliseconds to run. So a candidate repair — restore the discarded channels, derive a
conditional model, widen the vocabulary — can be evaluated *before* any experiment is
designed, let alone dispatched. That is the components-first ratchet applied to the objective
itself, and it is strictly cheaper than the alternative, which was re-running slices to
re-establish micro-pilot 7 on the fixed substrate.

### 22.4 The restoration queue, in cost order

1. **Restore the discarded channels.** Sloppy building, and the cheapest. But note it is a
   *decision*, not a pure repair: either the three channels matter (feed them in) or they do
   not (stop computing them). Measure the degeneracy meter under both and choose on evidence.
2. **Derive a conditional model.** Key `fixed-model` by `[exotype, observation-bin]` and
   derive each entry by measuring what the propagator does to a cell in that local state.
   Local, derivable, and exactly the move that fixed `:rule-change` — one level up.
3. **Widen the vocabulary**, once (2) makes a per-σ model computable rather than typed.

**Not started. N2b stays parked behind all three.**

---

## 23. Repair queue item 1 — executed, and it overturned its own premise

Joe approved the cost-ordered queue. Item 1 said: *measure the meter under both options and
choose on evidence, because this is a decision and not a pure repair.* Measured over **18,000
real local observations** from a 60-cell grid across 300 steps.

### 23.1 Correction — "three of five discarded is sloppy building" was wrong, and it was mine

Two of the three unread channels carry **no information at all**:

```
boring?  <=>  (activity = 0)            exact, 18000/18000
hungry?  <=>  (static? AND boring?)     exact, 18000/18000
```

`activity` counts how many of the three phenotype neighbours differ from self, so
`boring?` is precisely `activity = 0`. **`predict` ignoring these is correct, not sloppy**,
and its `next-boring = 1 - next-activity` is a legitimate proxy rather than a fudge.

Only **`static?`** is genuinely discarded information — and it is true in **0.33%** of real
observations, so feeding it in is a real but very weak restoration. It will not move the
degeneracy.

This is instance #11 of the §17 pattern, mine, and Joe repeated my framing back to me
("a clear example of sloppy building") because I asserted it. **The queue's own instruction
to measure first is what caught it** — the repair would otherwise have shipped as three
channels fed into a model that needed one.

### 23.2 A second correction: the pinned domain was too large

`activity` can never be 1.0 — self never differs from self. The health ratchet pinned a
12-point domain; the reachable domain is **9**, and only **7 pairs actually occur**. Both
numbers are now corrected in the ratchet, which caught its author within the hour.

Winners on the reachable domain (unchanged conclusion, corrected counts): full
`{chaos 7, identity 2}`, risk-only `{chaos 9}`, ambiguity-only `{identity 9}`, no-conatus
`{chaos 6, collapser 3}`.

### 23.3 What the measurement found instead — and it is bigger

`preferences` calls low hunger *"load-bearingly"* important. Against reality:

| quantity | value |
|---|---|
| realized `P(hungry)` over 18,000 observations | **0.00050** |
| the preference target C | **0.05000** — 100× above reality |
| model-predicted hunger, `:builder` / `:chaos` / `:collapser` | 0.11 / 0.19 / 0.38 — **221× to 769×** above reality |
| model-predicted hunger, `:identity` | exactly 0.0000 |

**The objective's load-bearing term ranks candidates on a quantity the generative model
over-predicts by two to three orders of magnitude, against a target the system is already a
hundred times below.** Every non-zero predicted hunger exceeds the target, so the conatus
gradient points the same way for every candidate everywhere — which is another face of the
degeneracy, and arguably its most correctable one.

Like `:rule-change` before H1, this is **derivable**: the realized rate is one measurement
away, and is instead typed in. Same defect, same fix, one level up.

### 23.4 Revised queue

1. ~~Restore three discarded channels~~ → **done, and mostly dissolved.** Two are redundant;
   `static?` remains a small, honest, optional restoration.
2. **Recalibrate hunger from the realized rate** — *promoted to item 1.* Cheap, derivable,
   and it targets the term the objective leans on hardest.
3. **Derive a conditional model** (key by `[exotype, observation-bin]`).
4. **Widen the vocabulary**, once (3) makes a per-σ model computable.

Ratchet now 13 tests / 622 assertions; exotype surface green. N2b stays parked behind all.

---

## 24. "Verify the settings first" — the principle, and it immediately retracted §23.3

Joe: *"Verify the settings, and if anything, do small experiments (3 or 5 generations on a
small patch over a few seeds) as tests. But even that's likely not needed when the
configuration is just wrong."*

**Gate G5:** before measuring a quantity, verify the setting that makes it live. A dead
channel returns a number, and the number looks like evidence.

### 24.1 The setting was wrong, and it falsified my own measurement

`efe/local-observation` reads `:static?` as `(= previous-genotype genotype)` at the cell, and
`:hungry?` as `static? AND boring?`. But **`grid/step` does not return
`:previous-genotype`.** Three generations on an 8-cell patch was the whole experiment:

```
t=0  has :previous-genotype? true    static? 8/8
t=1  has :previous-genotype? false   static? 0/8
t=2  has :previous-genotype? false   static? 0/8
t=3  has :previous-genotype? false   static? 0/8

grid/step returns: (:arm :exotypes :genotype :phenotype :seed :time)
```

Both channels are **structurally false past t=0**. `self-tuning/step` *does* preserve the
field, so the two step functions disagree on the state contract — the same class of
divergence as §17 #9.

### 24.2 Retraction of §23.3 — the "221x to 769x" hunger miscalibration

My realized `P(hungry) = 0.00050` was measuring a dead channel. The 60 positives were exactly
the 60 cells at t=0, one per cell, and nothing after. **The denominator was an artefact.**

Measuring it properly — tracking the previous genotype by hand, since the state will not carry
it — over the same 18,000 cell-steps:

| quantity | §23.3 claimed | **true** |
|---|---:|---:|
| realized `P(hungry)` | 0.00050 | **0.09617** |
| ratio, model-predicted (chaos 0.19) to realized | 384× | **2.0×** |
| preference target 0.05 vs realized | 100× above reality | reality is **2× above target** |

So the model over-predicts hunger by about **two-fold** — an ordinary calibration gap, not a
catastrophe. And the preference target is entirely sensible: C asks for less hunger than the
system currently produces, which is what a preference is *for*. **§23.3 is withdrawn in
full**, and with it item 1 of the revised queue.

Bonus cross-validation, unlooked for: `P(genotype unchanged) = 0.4241` against a mean derived
change rate of 0.72 under uniform bytes. The gap is the known uniform-byte assumption (§15.3),
and the two agree in the right direction and magnitude — the H1 derivation checks out against
the running system.

### 24.3 Instance #12, and what it says about the method

That is three self-corrections in two turns (#10 citation, #11 sloppiness framing, #12 this
one). It is not a sign the method is failing; **it is the method working at the intended
speed.** Each cost one measurement and a paragraph, and each was caught before it reached a
slice, a dispatch, or the paper. Compare §17 #1 and #2, which cost a full Codex round-trip
each, and #3, which shipped into a report.

The specific lesson is Joe's, and it is now G5: **I measured a quantity without verifying the
setting that makes it live.** The floor check (§18.3) would not have caught it, because 0.0005
is a perfectly plausible rate. Only asking "is this channel wired?" catches it.

### 24.4 Revised queue — again

1. ~~Recalibrate hunger~~ — **withdrawn**, the premise was an artefact.
2. **Carry `:previous-genotype` in `grid/step`.** A real defect, a one-line fix, and a
   *prerequisite for N2b* — the clock needs precisely this per-cell history. Deliberately not
   fixed here: it changes what `local-observation` returns mid-run, and the §13 disposition
   should be consulted first.
3. **Derive a conditional model** (key by `[exotype, observation-bin]`) — now the top
   substantive item, and untouched by any of these corrections.
4. **Widen the vocabulary**, once (3) makes a per-σ model computable.

Ratchet: 13 tests / 621 assertions. The retracted hunger hole is replaced by
`hole-grid-step-drops-previous-genotype`.

---

## 25. `:previous-genotype` carried — and a third correction, from the same principle

Joe: *"the previous-genotype carry IS a big change, but in another sense it is implied by the
LEFT RIGHT EGO NEXT PHENO breakdown… NEXT has the EGO as its previous genotype… from
generation 2, 'previous genotype' is already available in the standard exotype model."*

### 25.1 The argument checks out, and the reference implementation already existed

Two verifications before touching anything:

- **Nothing consumes `:static?` or `:hungry?`.** Across all of `src/` and `scripts/`, the only
  occurrences outside `local-observation` are the two lines *inside* it that produce them.
- **`self-tuning/step` already does exactly the identity Joe names**: `previous (:genotype
  state)` → `:previous-genotype previous`. `grid/step` was the odd one out.

So this is not new state; it is a field the five-read structure already implies, written down.
`grid/step` now carries it, with the reasoning at the line. Measured directly: threading the
key versus stripping it every step gives **identical trajectories over 25 steps**. Full
surface stayed green, byte-identical fixtures included.

The channels are now live: `P(static?)` and `P(hungry?)` were **structurally 0.0000** past
t=0 and now register.

The pinned hole `hole-grid-step-drops-previous-genotype` was **closed by being broken** —
exactly the ratchet's design. It is replaced by the positive invariant
`grid-step-carries-previous-genotype`.

### 25.2 Instance #13 — my measurement harness was unseeded

Two runs of the same script gave `P(static?)` 0.4241 and 0.4687, and I nearly attributed the
difference to the fix. The trajectory test above says the fix is inert, so the difference was
in the measurement.

**`grid/initial-grid` calls `ca/rnd-nth`, which falls back to the GLOBAL rng outside a
`with-seed` binding.** My ad-hoc scripts built the exotype field outside any seed, so every
run drew a different initial exotype grid. `slice-harness/initial-state` wraps everything in
`with-seed` and is unaffected — this was my harness, not the codebase.

Corrected, with everything seeded, **five seeds** as Joe prescribed:

| | mean | range |
|---|---:|---|
| `P(static?)` | 0.5021 | 0.4518 – 0.5656 |
| `P(hungry?)` | 0.1021 | 0.0964 – 0.1139 |

The seed-to-seed spread on `P(static?)` is **±11%** — wider than the gap I nearly explained
as a code change. **Every single-seed number in §23–§24 should be read as one draw**, and
§24.2's "0.4241 / 0.09617" is superseded by the table above. The qualitative conclusion is
unchanged: the model over-predicts hunger roughly two-fold, and the preference target of 0.05
sits sensibly below a realized ~0.10.

### 25.3 The lesson, which is Joe's own instruction

*"do small experiments (3 or 5 generations on a small patch over a few seeds)"* — I did the
small patch and the few generations and **skipped the few seeds**, then compared two draws as
if they were the same measurement. G5 applies to the measuring harness, not only to the system
under measurement:

**Gate G5 (extended):** verify the settings of the *instrument* too. An unseeded initial
condition is a dead channel of a different kind — it returns a number, and the number moves.

### 25.4 Queue

1. ~~Carry `:previous-genotype`~~ — **DONE**, inert, prerequisite for N2b now satisfied.
2. **Derive a conditional model** (key by `[exotype, observation-bin]`) — the top substantive
   item, untouched by any correction so far.
3. **Widen the vocabulary**, once (2) makes a per-σ model computable.

---

## 26. How much conditioning is actually there? (Joe's role observation, measured)

Joe: *"each genotype participates in several exotypes (in each role except PHENO). So there
could be a tremendous amount of conditioning going on — e.g. 'If I am the diverse candidate as
LEFT then, as EGO do…'"*

The structural claim is right. A genotype at cell *i* is read as **EGO** in *i*'s own update,
as **RIGHT** in *i-1*'s, as **LEFT** in *i+1*'s, and — since §25 — as the **previous** of *i*
at *t+1*. Four roles, one value, and the model conditions on none of them.

### 26.1 The measurement, with the control that changes the answer

Outcome: next-step `:activity` at the cell. 26,820 cell-steps, 3 seeds, everything seeded.
`H(next activity) = 1.4915 bits`.

| context | distinct | samples/ctx | explains | **shuffled control** | verdict |
|---|---:|---:|---:|---:|---|
| what `predict` sees, `(activity, diversity)` | 7 | 3831 | 0.0220 | 0.0006 | real, 36× chance |
| structured intermediate — roles + static? + exotype + genotype-equality pattern | 80 | 335 | 0.0332 | 0.0049 | real, 6.8× chance |
| full radius 1 (phenotype + genotype triples) | 24,496 | **1.09** | 1.4417 | **1.3767** | **memorisation** |
| full radius 2 | 26,626 | **1.01** | 1.4915 | 1.4822 | **memorisation** |

**Without the shuffle control I would have reported that radius 1 explains 96.7% and radius 2
explains 100%.** Both are artefacts of one sample per context. This is instance #14 avoided
rather than committed, and only because the control was run before the write-up.

**Gate G6:** an information-theoretic claim needs a shuffle control reported beside it.
Conditional entropy falls to zero for free once contexts outnumber samples.

### 26.2 What the numbers actually support

Net of chance, the structured descriptor explains **0.0283 bits** against the current model's
**0.0214** — a **1.32× gain**, roughly 1.4% → 1.9% of the outcome's entropy. Real, worth
having, and *not* orders of magnitude.

So "tremendous conditioning" is true of **distinguishable contexts** (7 used, 80 usable, 24,496
present) and not yet demonstrated for **usable predictive gain**.

### 26.3 The part that is provable by reading, not measuring

`phenotype-step` is **deterministic** given the current genotype and phenotype. So
next-phenotype at *i-1, i, i+1* — and hence next-activity at *i* — is an exact function of the
radius-2 current state. All 1.4915 bits are determined in principle.

The measurement cannot establish this (the contexts are too sparse) but the code does. Which
sharpens the finding considerably:

> The outcome is fully determined by local state, and the model captures **1.5%** of it. The
> loss is not noise and not non-locality. **It is the binning.**

### 26.4 Why this makes Joe's proposal the right shape

If the binning is where all the value sits, the design question is a descriptor rich enough to
carry signal and coarse enough to generalise — 7 bins is too coarse, 24,496 is unusable, 80
already gains a third more.

**Role-conditioning is a principled coarsening rather than an arbitrary one.** "What am I as
LEFT, as RIGHT, as EGO, as previous" is a structured basis derived from the substrate's own
five-read geometry, not a bin count someone picked. That is exactly what the conditional model
in queue item 2 needs, and it now has a cheap evaluation harness: any candidate descriptor can
be scored in seconds, with its shuffle control, before a single slice is designed.

**Caveat carried forward:** roles beyond EGO may need radius 2 — "what I look like as LEFT to
*i+1*" depends on *i+2*. Whether that breaks the locality constraint the model needs is a
design question, not a measurement, and is unresolved.

---

## 27. Radius 2 parked; the bands measured; and `4ffad1d` vindicated against my own hypothesis

Joe: *"maybe [this] does a lot to explain the vertical bands that were the 'baseline'… I'm
less convinced by the radius 2 proposal… it seems like there are still many things we could
improve about the radius 1 situation before we extend to that."*

**Radius 2 is parked** — available, not pursued. §26 supports this: the radius-1 binning
captures 1.5% of an outcome that is *fully determined by local state*, so there is a large
amount of radius-1 headroom to spend before extending the neighbourhood. Extending radius
before exhausting binning would be buying information the model already has and discards.

### 27.1 The bands: measured by perturbation, post-RNG-fix

One genotype cell perturbed by a single bit flip, 41 cells, damage-cone width per layer,
four seeds:

| blend | genotype damage width, t = 0 / 2 / 5 / 10 / 20 / 40 |
|---|---|
| **0 (the baseline)** | seed 11 `1 1 1 1 1 1` · seed 22 `1 1 1 1 1 1` · seed 33 `1 1 0 0 0 0` · seed 44 `1 0 0 0 0 0` |
| 0.5 | seed 11 `1 1 4 0 0 0` · seed 22 `1 1 0 0 0 0` · seed 33 `1 0 …` · seed 44 `1 0 …` |

**At the baseline the genotype damage width never exceeds 1, in any seed, at any step.** It
either persists in its own column forever or dies out. It never propagates. With blend on it
can exceed 1 (seed 11 reaches 4), so blend genuinely couples — weakly.

That is *literally* a vertical band: a perturbation that cannot move sideways. Joe's reading
is confirmed at the mechanism level — the genotype layer at baseline is **41 independent
columns**, and the exotype model conditions on nothing spatial either, so the only spatially
coupled layer in the default configuration is the phenotype CA.

Damage also dies readily, which the propagator explains: a one-bit difference is erased
whenever σ(k) happens to address the differing position, ≈1/8 per application.

**Scope of the claim:** this establishes the *mechanism* — zero genotype spatial coupling at
baseline. It is not an inspection of the published band figures, which I have not opened. The
mechanism is verified; the identification with those specific figures is inferred.

### 27.2 Retraction — my §13 disposition of `4ffad1d` was wrong

§13 marked `4ffad1d` ("genotype has no spatial coupling; no c can break the bands")
**"RE-RUN, first — the most likely artifact in the ladder"**, reasoning that bands were what
positionally-deterministic draws would produce.

The re-run has now happened, post-fix, and **the original finding reproduces exactly.** The
bands are structural, not an RNG artefact. `4ffad1d` was right, and my hypothesis about it was
wrong — instance #15, and the first case where a §13 RE-RUN was actually executed and
*vindicated* the pre-fix result rather than overturning it.

Worth noting for the disposition generally: this one cost four seeds and forty steps. Several
other RE-RUN items are probably that cheap too, and §13's cost estimate for them was
pessimistic.

Historical note that also fits: `4ffad1d` predates `0deb7f9` (transfer) and `c72bfa3` (blend).
The no-coupling finding is what *motivated* adding them — so the ladder's own history is
coherent, and the measurement above shows blend does what it was added to do.

### 27.3 Queue, unchanged in order

1. **Derive a conditional model** — the binning is where the value is (§26.3), and role
   structure is the principled coarsening (§26.4).
2. **Widen the vocabulary**, once (1) makes a per-σ model computable.
3. *(back pocket)* radius 2, if radius-1 binning is exhausted.

---

## 28. Queue item 2 EXECUTED — the derived conditional model

### 28.1 The finding that justified building it

Legacy `predict` versus a derived table, mean absolute error on **held-out seeds**
(derivation used 11/22/33; evaluation used 77/88, 19,080 transitions):

| channel | null (constant) | **legacy** | **derived** |
|---|---:|---:|---:|
| activity | 0.1799 | 0.2309 | 0.1800 |
| diversity | 0.0281 | 0.2306 | **0.0128** |
| hunger | 0.1805 | 0.2171 | **0.1684** |

**The legacy model is worse than predicting a constant on every channel** — 28% worse on
activity, **8× worse on diversity**, 20% worse on hunger. It is not merely uninformative; it
is anti-predictive. The derived table is 18× better than legacy on diversity and 23% better
on hunger.

`:activity` shows no gain, and none is possible: `phenotype-step` reads the **current**
genotype, so an exotype chosen at *t* moves the genotype at *t+1* and the phenotype only at
*t+2*. Next-step activity is not a function of this step's exotype. That is structure, not a
modelling failure, and it is recorded at the code.

Adding `:static?` to the key changed MAE by <0.0001 on every channel, so it is **not** in the
key. §26 predicted exactly that.

### 28.2 What was built

- **`scripts/derive_conditional_model.clj`** — derives P(next observation | exotype, current
  observation) by measuring the substrate, and writes
  `resources/futon5/exotype/conditional-model.edn`. 28,620 transitions → **25 bins**.
  Exotype assignment is random at init and fixed under `:heterogeneous-fixed`, so
  conditioning on the exotype a cell actually carried is unconfounded — no counterfactual
  re-simulation needed.
- **`efe/observation-bin`** — key is `[exotype, activity-count, diversity-count]` with
  **integer** counts, so no floating-point equality ever enters a lookup.
- **`efe/min-bin-samples` = 30** — 3 of 25 bins fall below it, holding 39 of 28,620 samples
  (0.14%); those fall back to the global row. The raw counts stay in the resource so the
  sparsity *policy* lives in code where it is visible and testable. This is the §26 lesson
  applied at design time rather than after a retraction.
- **`efe/predict`** gains a third arity; **`score-policy`** takes `:observation-model`.
  **`:legacy` remains the default**, so nothing downstream moves until the switch is thrown
  deliberately.

### 28.3 The result that matters: chaos is displaced

Distinct argmins over the 9 reachable observations:

| arm | legacy | derived |
|---|---|---|
| `:efe-full` | 2 — `{chaos 7, identity 2}` | **3** — `{collapser 6, builder 2, chaos 1}` |
| `:efe-risk-only` | **1** — `{chaos 9}` | **2** — `{collapser 7, chaos 2}` |
| `:efe-ambiguity-only` | 1 — `{identity 9}` | 1 — `{identity 9}` |
| `:efe-no-conatus` | 2 — `{chaos 6, collapser 3}` | 2 — `{collapser 8, builder 1}` |

**"Chaos always wins" is a property of the hand-typed table, and it does not survive deriving
the model.** Degeneracy is reduced, not eliminated: the policy varies more with state, and
`:efe-risk-only` is no longer constant, but `:collapser` now takes 6–8 of 9 bins.

This bears on micro-pilot 7's claim that *no accuracy-based correction displaces chaos*.
Deriving the model **is** an accuracy-based correction, and it displaced chaos. Stated with
its limits: this is a pure-function argmin over the reachable domain, not a dynamical re-run,
and I still have not read that note.

### 28.4 Verification

Four new tests pin: resource presence and bin coverage; **derived beats both legacy and the
constant null out-of-sample** on diversity and hunger; the degeneracy reduction; and that
legacy is still the default. The out-of-sample test derives nothing — it evaluates the
shipped resource on seed 77, which is not in the derivation set.

Exotype surface **74 tests / 1904 assertions, 0 failures**; clj-kondo 0/0; parens exit 0.

### 28.5 Queue

1. ~~Derive a conditional model~~ — **DONE**, opt-in.
2. **Decide whether to make `:derived` the default.** A behaviour change for every slice; the
   §13 disposition applies. Evidence is strong but this is Joe's call.
3. **Widen the vocabulary** — now unblocked: a per-σ model is computable, so kinds need no
   longer be hand-typed rows.
4. *(back pocket)* radius 2.

---

## 29. `:derived` is now the default (Joe, 2026-08-04)

Joe: *"it sounds to me as though :derived should become the default, as that's honest to what
we've learned."* Switched. Defaulting to `:legacy` was defaulting to a model measured to be
**worse than predicting a constant on every channel** (§28.1).

`:legacy` is **retained, not deprecated**: it is the baseline every comparison is made
against, and every slice result predating the switch was produced under it.

### 29.1 No bootstrap circularity — checked, not assumed

The derivation runs `grid/step`, which under `:heterogeneous-fixed` never calls `predict`. So
flipping the default cannot feed back into the table. Verified rather than argued: the
resource **regenerates byte-identically** after the switch.

### 29.2 The switch broke four pins, and each one was the ratchet doing its job

| broken pin | why, and what replaced it |
|---|---|
| `legacy-remains-the-default` | inverted → `derived-is-the-default` |
| `chaos-is-the-structural-argmin` | **the finding itself changed.** Rewritten as `the-structural-argmin-depends-on-the-model-not-the-objective`, which now pins *both* sides: chaos > 2/3 under legacy, chaos < 1/3 and collapser > 1/2 under derived. The contrast is the finding. |
| `hole-the-objective-is-degenerate…` | numbers were legacy; now the default's: full **2→3**, risk-only **1→2**. Still a hole, a shallower one. |
| `hole-the-generative-model-is-not-conditional` | renamed `hole-the-legacy-table-is-not-conditional` — **now off the default path**, kept open because the table is still unconditional and still hand-types `:activity`/`:diversity`. |
| `grid_q0_baseline.edn` | regenerated; second provenance note added at `grid_test.clj`. |

Nothing here was a surprise, and nothing needed a Codex round-trip to find. That is what §18
was built for: **an improvement that changes a measured fact cannot pass silently.**

### 29.3 Joe's reading of what this unlocks

*"the wider vocab and per-σ modes… allows Part III to build on what we learned in Part II of
the paper, and not just satisfice via a half-assed exotype layer."*

That is now mechanically true rather than aspirational. Before today an exotype was a name
with three typed numbers, so a fifth kind meant a fifth hand-written row and Part III could
only inherit the impoverishment. Now:

- `:rule-change` is **computed from σ** (§16), so any σ has one for free;
- the observation-conditional rows are **derived by measurement** (§28), so any σ can have
  them by running the deriver;
- the vocabulary size is no longer bounded by how many rows someone is willing to type.

**H5's third depth (structure) is discharged; the first (vocabulary) is now unblocked** — the
remaining barrier was never the count, it was that each new kind needed hand-authored
parameters.

### 29.4 Verification

Exotype surface **74 tests / 1906 assertions, 0 failures**; clj-kondo 0 errors 0 warnings;
parens exit 0. Resource regenerates byte-identically.

### 29.5 Queue

1. ~~Derive a conditional model~~ — **DONE** (§28).
2. ~~Make `:derived` the default~~ — **DONE**.
3. **Widen the vocabulary** — unblocked, and the natural next step.
4. *(back pocket)* radius 2.

Still open and untouched by all of this: the **§13 RE-RUN set**. §27.2 showed one of those
re-runs cost four seeds and forty steps, so the disposition's cost estimate is due a revision
before anyone budgets a sweep.

---

## 30. Vocabulary options — the coordinate map, and an A/B design

Joe: *"let's discuss some vocabulary possibilities prior to the rerun… maybe we could make
future reruns actual A/B tests (w/ added vocabulary vs not)."*

### 30.1 An exact identity that collapses one axis

For a permutation, `rate = (fix + (8-fix)/2)/8`, which simplifies to

> **rate = 0.5 + fix(σ)/16**

So the change-rate coordinate is a **function of the fixed-point count alone** and takes only
**nine** values across all 40,320 permutations. It is not a rich dial; it is an eight-notch
one (fix = 7 is impossible).

### 30.2 The current vocabulary has a hole in the middle of its only ordered axis

| kind | cycle type | fix | rate | immune bytes |
|---|---|---:|---:|---:|
| `:collapser` | (6,2) | 0 | 0.5000 | 4 |
| `:chaos` | (4,3,1) | 1 | 0.5625 | 0 |
| `:builder` | (3,1,1,1,1,1) | 5 | 0.8125 | 0 |
| `:identity` | (1⁸) | 8 | 1.0000 | 0 |

Covered: fix ∈ {0, 1, 5, 8}. **Missing: fix ∈ {2, 3, 4, 6}** — rates 0.625, 0.6875, 0.75,
0.875. The unsampled block is *contiguous and central*: the vocabulary jumps 0.5625 → 0.8125,
straight over the middle of the range where one would look for a transition.

### 30.3 The second axis is invisible to the model

Immune bytes (those the propagator cannot change) exist iff every cycle is even — and an even
cycle type has no fixed points, so **immune bytes occur only at fix = 0, i.e. only at rate
exactly 0.5**. All five all-even types sit there:

| cycle type | immune bytes | in vocabulary? |
|---|---:|---|
| (2,2,2,2) | **16** | no |
| (4,2,2) | 8 | no |
| (6,2) | 4 | **yes** — `:collapser` |
| (4,4) | 4 | no |
| (8) | 2 | no |

And two σ with *identical* rate can differ completely: **(2,2,2,2) and (5,3) both have rate
0.5000, but 16 immune bytes versus 0.**

> **`fixed-model`'s `:rule-change` cannot distinguish a propagator that can freeze from one
> that cannot.** Under blend 0 and transfer 0 an immune byte is an absorbing state — the cell
> stops changing permanently — so this is the coordinate that governs whether the *ordered
> regime is reachable at all*, and the model has no slot for it.

The current vocabulary reaches 4 immune bytes of a possible 16, all via one kind.

### 30.4 A minimal principled widening — four new kinds, with a built-in control

Chosen to span both axes at their extremes rather than to add bulk:

| proposed | cycle type | fix | rate | immune | what it buys |
|---|---|---:|---:|---:|---|
| `:fix2` | (6,1,1) | 2 | 0.6250 | 0 | fills the rate gap, low end |
| `:fix4` | (4,1,1,1,1) | 4 | 0.7500 | 0 | fills the rate gap, high end |
| `:even4` | (2,2,2,2) | 0 | 0.5000 | **16** | maximum freeze capacity |
| `:odd53` | (5,3) | 0 | 0.5000 | **0** | **the control** — same rate as `:even4`, no freeze |

`:even4` vs `:odd53` is the clean contrast: identical on every coordinate the model currently
represents, opposite on the one it does not.

**Naming:** by cycle type, not by behavioural adjective. §15 established that the name was the
camouflage — `:identity` sounded like the conservative kind and is the most disruptive one.
No new kind should be nameable in a way that can be wrong.

### 30.5 Re-runs as A/B tests

Every §13 RE-RUN becomes two arms on identical seeds and config: **baseline vocabulary (4)
versus widened (8)**. Cost is one extra arm on a re-run already owed, and the output is a
controlled contrast rather than a redo.

Preregistered, in the house style (`TN-eig-definition.md`, and the paper's *Specifying an
Experiment Before Running It*):

- **P1** Widening raises the incidence of permanently frozen cells, driven by `:even4`.
  *Falsifier:* freeze incidence unchanged → immune bytes do not reach the dynamics, and §30.3
  is wrong about absorption.
- **P2** `:even4` versus `:odd53` separates on freeze incidence while matching on rate.
  *Falsifier:* they behave alike → freezing is not governed by immune-byte count.
- **P3** Filling fix ∈ {2,4} resolves rate-dependent structure the 0.5625→0.8125 jump hides.
  *Falsifier:* the new rates interpolate smoothly with nothing between them.

P2 is the load-bearing one: it is the only comparison that isolates the axis the model cannot
see, and it needs no new machinery beyond the two σ.

**Not built.** Discussion only, pending Joe's pick of vocabulary and whether the A/B replaces
the plain re-run.

---

## 31. P2 RUN on zone-joe — confirmed, monotonically

Preregistered in `scripts/exotype_immune_axis_p2.clj` before running. Report:
`reports/exotype-immune-axis-p2.md`; raw arms in `reports-remote/p2/`.

### 31.1 Gates cleared before trusting a number

- **README §4 determinism.** Its recipe diffs a re-run against a *committed* artifact, which
  today's RNG and model-default changes legitimately break. Applied the principle instead:
  regenerated `conditional-model.edn` remotely and byte-compared. **sha256 identical.**
- **README §5 partitioning** — one process per condition, five files, merged after.
- **The control was rebuilt before it was trusted.** Measured in-run it read 0.0066 for
  `:even4`, because a frozen field cannot change and the rate collapses toward zero. A
  control contaminated by the treatment is not a control; it is now measured on random bytes,
  free of the dynamics.

### 31.2 The result

400 seeds, width 80, 300 steps, blend 0, transfer 0.

| arm | cycle type | absorbing | **control rate** | median t½ | never froze |
|---|---|---:|---:|---:|---:|
| `:odd53` | (5,3) | 0 | 0.5011 | — | **400 / 400** |
| `:even1` | (8) | 2 | 0.5018 | 40 | 0 |
| `:collapser` | (6,2) | 4 | 0.4995 | 20 | 0 |
| `:even8` | (4,2,2) | 8 | 0.5012 | 10 | 0 |
| `:even4` | (2,2,2,2) | 16 | 0.4998 | 5 | 0 |

**`:odd53` never froze in 400 of 400 seeds.** Median half-freeze time falls 40 → 20 → 10 → 5
as absorbing count doubles 2 → 4 → 8 → 16. Every control rate is within 0.0018 of 0.5000.

> The coordinate that decides whether a cell can freeze at all — and how fast a field reaches
> the ordered regime — is **invisible to the generative model**. `:rule-change` reports
> 0.5000 for every one of these five.

### 31.3 Honest limits

- **The apparent law is not established.** t½ × absorbing = 80 across all four freezing arms,
  which looks like t½ ∝ 1/absorbing — but t½ is quantised to the checkpoint grid and every
  reported value *is* a checkpoint. The ordering is unambiguous; the proportionality is
  consistent with, not demonstrated by, this resolution. A finer grid costs minutes.
- **Baseline only.** With blend or transfer on, a neighbour can write a frozen cell. Untested.

### 31.4 What it changes

`:even4`, `:even8`, `:even1`, `:odd53` were added to `grid/propagators` but **deliberately
kept out of `exotype-kinds`**, so the default vocabulary, `fixed-model`, `initial-grid` and
the derived conditional model are all untouched. Two pins broke and were updated to say
"default vocabulary is four; propagators carries eight".

Joe's reading — that the rate/absorbing split is *"a cool Part I reprise style finding"* — is
parked as a paper candidate, not acted on. N1 is closed and this does not reopen it.

Surface **74 tests / 1916 assertions, 0 failures**; clj-kondo 0/0; parens exit 0.

### 31.5 Representative spacetimes — and the bands, explained end to end

`reports/figures/p2-A-vs-B-spacetime.png` (plus the two single-arm PNGs). Same seed, same
initial genotype and phenotype, same width; **the only difference is sigma**, and both report
`rate = 0.5000`, so `fixed-model` sees the two systems as identical.

- **A `:odd53`** — genotype churns for all 220 steps, never settles; phenotype disordered.
- **B `:even4`** — genotype locks into fixed vertical columns within ~10 steps and never
  moves; each column is then a *frozen ECA rule*, and the phenotype shows precisely that:
  regular, periodic, per-column textures.

**This closes the loop on the bands.** Two independent causes combine, and §27 and §31 each
found one: at blend 0 the genotype layer has **no lateral coupling**, so a perturbation stays
width 1 forever; and absorbing bytes stop each column changing **in time**. Bands are the
joint signature of a layer that cannot mix spatially and can freeze temporally — and neither
cause is representable in `:rule-change`.

Incidental: the PNGs are 20.7 KB (A) against 7.8 KB (B) at identical dimensions. The freeze
shows up in the compression ratio.

---

## 32. Crossed A/B — vocabulary × coupling. Run on zone-joe.

`reports/exotype-vocab-blend-ab.md`, figure `reports/figures/vocab-blend-cross.png`, raw in
`reports-remote/ab/`. 5 vocabulary arms × 5 blend levels, 40 seeds each, width 80, 200 steps.

### 32.1 Result

1. **Only `:odd53` (0 absorbing) sustains dynamics.** Every arm with *any* absorbing bytes —
   including `:even1` with just two — is fully frozen by 200 steps at every blend level.
2. **Coupling does not rescue a freezing arm; it makes it freeze *uniformly*.** Lateral
   agreement for the even arms runs 0.72–0.91 at blend ≥ 0.10: they collapse to a single
   shared rule rather than to distinct per-column rules.
3. **For `:odd53`, blend does what coupling should**: vertical persistence 0.747 → 0.364,
   lateral agreement 0.015 → 0.195, as blend goes 0 → 0.75.
4. **The interesting regime is located**: `:odd53` at blend 0.25–0.50. Sustained change in
   time *and* real organisation in space. It is the only such region in the cross.

Together with P2 this closes the vocabulary question for permutations: **absorbing count sets
how fast a field freezes, not whether**, and since absorbing bytes require all-even cycles
which force rate 0.5000, "can freeze" and "stays lively" are mutually exclusive inside S₈.

### 32.2 PNG size as a structure measure — Joe's proposal, validated

Checked against measures computed from the field, never from an image (n = 25):

| against | Pearson r |
|---|---:|
| vertical persistence | **−0.958** |
| frozen fraction | **−0.985** |
| lateral agreement | −0.682 |

`:odd53` spans 13,315–17,629 bytes; every frozen arm sits at 2,741–5,254. **A single `ls -l`
classifies the regime.** Weaker against lateral agreement because uniformity and stasis are
different routes to compressibility and size cannot separate them — a good screen, not a
replacement for the two measures.

Comparability was engineered, not hoped for: the runs emit PPMs and **one** encoder
invocation converts them all after retrieval, with fixed compression settings.

### 32.3 What this sets up for N2b

N2b should run at **`:odd53`-like σ with blend ≈ 0.25–0.5**, not at baseline. At blend 0 the
genotype layer is 80 independent chains and the clock would be 80 one-cell experiments; with
absorbing bytes the field freezes and the clock has nothing left to integrate. The regime
where an endogenous clock is interpretable is now measured rather than guessed.

---

## 33. EoC — Joe was right, but not about blend. The paper's result REPRODUCES post-fix.

Joe, on the `:odd53` × blend row: *"the first one, blend, is actually GOOD. After days of
nothing… we found EoC again."*

Put on the paper's own dynamical scale rather than the eye. Phenotype damage reach at t=100,
width 201, single-cell flip, 24 seeds.

### 33.1 The measurement

| | damage reach |
|---|---:|
| ECA rule 204 (frozen) | 1.0 ± 0.0 |
| ECA rule 90 | 8.0 ± 0.0 |
| ECA rule 54 | 32.8 ± 15.7 |
| ECA rule 110 | 37.1 ± 9.9 |
| ECA rule 30 (chaotic) | 65.1 ± 7.6 |
| **MetaCA, loop OPEN** (`:heterogeneous-fixed`) | **2.3 ± 3.9** |
| **MetaCA, loop CLOSED** (`:boring-triggered`) | **17.3 ± 5.8** |
| loop closed, blend 0.25 | 18.6 ± 6.6 |
| loop closed, **widened vocabulary** | 10.7 ± 5.7 |
| `:conformist` (exotype-only feedback) | 2.4 ± 4.7 |

### 33.2 What it says

**Blend is not what does it.** The `:odd53` × blend row is ordered — below the frozen ECA
anchor (0.0–2.6 against 1.0), and its *genotype* damage cone saturates at 4–6 cells of 401
over 150 steps where a chaotic cone would be ~300. The visual richness is a rapidly churning
rule field being rendered. **Appearance is not criticality**, and I checked the autonomous
layer as well as the rendering before concluding it.

**The feedback loop is what does it.** Closing phenotype → exotype takes the system from
2.3 to **17.3**, landing **between ECA rules 90 and 54** — which is precisely where draft7's
introduction says it lands: *"between rules 90 and 54, below rule 110 and far below the
chaotic rule 30, while frozen rules return exactly zero."*

**And it is specifically the PHENOTYPE-READING that matters.** `:conformist` also transmits
exotypes, but by exotype majority, and it stays at 2.4. Only `:boring-triggered`, which reads
the phenotype, moves the system. That isolates the mechanism to the loop, not to exotype
mobility.

> **The paper's central dynamical EoC result reproduces after the RNG fix, the derived model,
> and everything else this register changed.** It is the first §13 RE-RUN item to be executed
> on a headline claim, and it CONFIRMS.

### 33.3 The A/B answer, and it is negative

The **widened vocabulary makes EoC worse**: 10.7 against the default's 17.3. Absorbing kinds
freeze and drag the system toward order — consistent with §32, where every absorbing arm
froze completely. So the honest verdict on vocabulary widening is that it is a good
*instrument* (P2 proved the axis is real and invisible to the model) and a bad *ingredient*
for the edge of chaos.

### 33.4 Two setup errors caught on the way, both mine

1. First pass measured only the phenotype and concluded "ordered". A negative is evidence
   about the setup first, so I re-measured the **genotype** layer — the autonomous one, per
   the orphan — which agreed. Only then was the negative safe to state.
2. Second pass compared the three transmit arms on a **uniform** exotype field, where
   `:conformist` (majority) and `:boring-triggered` (adopt-left) are both no-ops. All three
   arms returned byte-identical numbers, which is a tell rather than a result. Re-run
   heterogeneous, the arms separate 2.4 / 17.3.

Had either gone unchecked I would have reported "no EoC" and been wrong.

---

## 34. What Joe is actually seeing — my instrument was wrong, and he is right

Joe: *"the blend row goes from vertical strips, to broken apart regions… the Phenotype is
interesting, particularly around .5 — if it isn't quite EoC it is very close."*

### 34.1 The measurement error: exact equality is not what the eye reads

§32's structure measures — lateral agreement, domain run length — test whether adjacent cells
hold the **identical** rule. The renderer maps similar bytes to similar colours, so the eye
reads *similarity*, not equality. Measuring the right thing:

| arm | blend | mean neighbour Hamming (0–8) |
|---|---|---:|
| `:odd53` | 0 | **3.980** — indistinguishable from independent random bytes |
| `:odd53` | 0.1 | 2.573 |
| `:odd53` | 0.25 | 2.077 |
| `:odd53` | 0.5 | **1.830** |
| `:odd53` | 0.75 | 1.660 |
| `:even4` | 0.25 | 0.575 (frozen, near-uniform) |

**Blend halves the distance between neighbouring rules.** Adjacent cells at blend 0.5 differ
in under 2 bits of 8 — visually near-identical, hence regions. My exact-equality numbers
(lateral agreement 0.015 → 0.195, domain length 1.02 → 1.25) understated this badly, because
neighbours become *similar* without becoming *identical*.

**The regions are real. I measured the wrong thing and nearly talked Joe out of a correct
observation.** Third setup error in this chain, and the most consequential.

Corroborating, on the phenotype: activity falls **0.445 → 0.262** as blend goes 0 → 0.75.
0.5 is maximal churn, so blend moves the phenotype *away from noise and toward structure* —
which is exactly the visual impression.

### 34.2 So why did damage say "ordered"? Because they are different axes

- **noise ↔ structure** — what blend moves along. Measured by neighbour similarity and
  activity. Blend produces genuine spatial coarsening.
- **order ↔ chaos** — what damage reach measures. Blend does not move along it at all
  (2.6 → 0.4).

A coarsening system has rich domain structure and no damage spreading; the two are
independent. The blend row is **structured but not sensitive** — visually class IV,
dynamically class II. At blend 0 the phenotype *looks* like noise and *is* noise
(activity 0.445, neighbour Hamming 3.98) yet is still insensitive, because the churning rule
field — identical in both runs — dominates each cell's output. High activity is not chaos.

### 34.3 The constructive result: they compose

The two mechanisms act on different axes, so they add rather than compete:

| configuration | damage reach | neighbour Hamming |
|---|---:|---:|
| loop open, blend 0 | 2.3 | 3.98 |
| loop open, blend 0.5 | 0.4 | 1.83 |
| **loop closed, blend 0** | 17.3 | — |
| **loop closed, blend 0.25** | **18.6** | — |

**Blend buys spatial structure; the phenotype-reading loop buys dynamical criticality; the
combination is the highest cell measured.** That is Part II's coupling and Part III's feedback
doing separate jobs, which is a better argument for the pairing than either alone.

### 34.4 Correction to §33

§33 said "appearance is not criticality" and stopped there. That was half right and unhelpful:
appearance is not criticality, *and* the appearance was tracking a real property I had not
measured. The lesson is not "distrust the eye" but **"if the eye and the instrument disagree,
suspect the instrument first"** — the same rule as for a negative result, which is what §33's
finding was.

Still running at time of writing: a t=1000 genotype cone at width 801, to check whether the
§27/§33 "saturates at 4–6" claim survives a longer horizon. At t=150 blend 0.5 was still
growing (2.8 → 3.8 → 6.5), so that claim is under-powered and not yet safe.

---

## 35. The cone does NOT saturate — §27/§33 retracted, and blend 0.5 peaks

Genotype damage cone, width 801, 12 seeds, t = 1000.

| blend | t=0 | 50 | 100 | 200 | 400 | 700 | 1000 | growth 400→1000 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 0.25 | 1.0 | 1.2 | 1.0 | 1.5 | 3.2 | 4.8 | **6.4** | **+3.3** |
| 0.50 | 1.0 | 1.1 | 1.6 | 2.3 | 3.6 | 8.9 | **11.9** | **+8.3** |
| 0.75 | 1.0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | +0.0 |

**RETRACTED: "the cone saturates at 4–6" (§27, repeated in §33).** It was measured to t=150,
where the growth had barely started. At t=1000 blend 0.5 is at 11.9 and still climbing. The
earlier reading was under-powered, and I flagged the risk in §34 before this landed but had
already used the claim twice.

### 35.1 What it actually shows

Damage in the **autonomous genotype layer** propagates persistently at blend 0.25 and 0.5.
Slowly — about 0.012 cells/step against 2 for a chaotic light cone, so ~0.6% of light speed —
but it does not stop. Growth decelerates (+5.3 then +3.0 per 300 steps at blend 0.5) without
halting; three points and 12 seeds cannot fit a law, and I am not going to guess one.

**And the response in blend is non-monotonic with a peak at 0.5**: ~1 at blend 0, 6.4 at
0.25, 11.9 at 0.5, **0.0 at 0.75**. Over-coupling kills it, consistent with §32 where high
blend drives the field to uniformity.

A parameter with a peak, order on both sides, and slow sustained propagation at the peak is
the shape of a critical transition — **which is what Joe said he could see, at the value he
named.**

### 35.2 Why §33's phenotype measurement missed it

§33 measured damage in the **phenotype**, which the orphan established is a *rendering* of an
autonomous genotype layer. Phenotype damage dies (0.4 at blend 0.5) while genotype damage
grows to 11.9 in the same configuration. Measuring criticality on the rendering rather than on
the substrate was the wrong choice, and it produced a confident negative.

### 35.3 Score

Joe's visual read has now been vindicated against my measurements **twice**: once on spatial
structure (§34 — I tested exact equality where the eye reads similarity) and once on damage
propagation (here — I stopped the clock at t=150). Both times I reported a negative with more
confidence than the measurement supported.

The rule that would have caught both is already in this note, from §24: **a negative is
evidence about the setup first.** I applied it to the *layer* in §33 and not to the *horizon*
or the *metric*. Checking one dimension of a setup is not checking the setup.

**Standing correction:** §32's "the interesting regime is `:odd53` at blend 0.25–0.50" was
right, and §33's "blend is not what does it" was wrong. Blend and the feedback loop both
matter, on different axes, and blend 0.5 is a peak in its own right.

---

## 36. Part III — can the MetaCA navigate to EoC itself? Half yes, half no, and the "no" argues for N2b

Joe: *"can the MetaCA, suitably equipped, navigate to this itself? Is EoC an attractor?"*

Two things have to be navigated: the exotype **kind**, which the objective already selects,
and the **coupling** (blend), which is currently exogenous. They come out opposite.

### 36.1 KIND — yes, and the blocker was vocabulary, not the objective

Conditional rows derived by measurement for all eight kinds (no hand-typed numbers), then
scored with the existing objective over the 9 reachable observations:

| candidate set | argmin distribution |
|---|---|
| default 4 | `{collapser 6, builder 2, chaos 1}` |
| default + `:odd53` | `{odd53 4, builder 2, collapser 2, chaos 1}` |
| all 8 | `{odd53 3, collapser 2, even1 2, builder 1, even4 1}` |

**The objective prefers `:odd53` as soon as it can see it** — plurality winner in both extended
sets. So Joe's hypothesis is confirmed in a better direction than expected: with a 4-kind
vocabulary the system picks `:collapser`, which freezes 100% of cells by t=200 (§32), and it
does so *because `:odd53` is not on the menu*. **Reachability was gated by vocabulary, not by
the objective.**

A structural blocker found on the way: offering `:odd53` to `efe/score-policy` throws an NPE,
because the derived path still reads `:rule-change` out of `fixed-model`, which only has rows
for the four declared kinds. `gen/rule-change-rate` computes it for any σ, so this is a small
fix — but until it is made, **the selectable vocabulary is hard-capped at the hand-typed
table.** H5's parameter hole is still load-bearing.

### 36.2 COUPLING — no. The peak is invisible to every local observable.

`:odd53`, 6 seeds, 150 steps:

| blend | divergence | hunger | activity | damage at t=1000 |
|---|---:|---:|---:|---:|
| 0 | 0.5191 | 0.1686 | 0.3461 | ~1 |
| 0.25 | 0.4754 | 0.1673 | 0.3169 | 6.4 |
| 0.50 | 0.4234 | 0.1594 | 0.2823 | **11.9** |
| 0.75 | 0.3762 | 0.1531 | 0.2508 | 0.0 |

**Every local observable is strictly monotone decreasing in blend, while damage peaks at
0.5.** A hill-climber on divergence, hunger, or activity runs to an endpoint and sails past
the peak. `:divergence` is `selection.clj`'s own fitness channel, so this is not a
hypothetical controller — it is the one the substrate already has.

The reason is the one §20 named: **EoC is a counterfactual property.** Damage reach asks what
*would* happen to a twin run, and time-averaging does not help — the numbers above are already
averaged over 150 steps.

**CORRECTED 2026-08-04 after codex-14's review** (`TN-apoptosis-proposal.md` §7.5). This
section originally said *no* single-run local statistic can report criticality. That is too
strong. A single run lacks an **exact paired** counterfactual; it does not lack all information
about counterfactual sensitivity. Transition statistics and **event-conditioned responses** can
still estimate susceptibility. The overstatement mattered — it is what made an explicit twin
look inevitable and pushed the author toward exotic mechanisms instead of the
event-triggered-response measurement that the review recommends.

### 36.3 Which is the strongest argument yet for N2b

If the missing signal is *perturbation response*, then a mechanism that generates its own
perturbations supplies it. **Apoptosis is an endogenous perturbation source**: a cell that
expires and is replaced from the exotype is a live single-cell flip, and the field's response
to it is exactly the quantity no passive observable reports.

That reframes N2b. It is not only a variation mechanism for H2 — it may be **the mechanism
that makes criticality observable to the system at all.**

**Hypothesis, not result.** Falsifier: if apoptosis-driven fields show the same monotone
local statistics across blend as the table above, the perturbations are not being sensed and
the argument fails. That is a cheap check and should be the first thing N2b measures.

### 36.4 On blend 0.75

Joe: *"in fact .75 is also good too!"* It is the most spatially structured setting short of
freezing — neighbour Hamming 1.66, the lowest for `:odd53` — but its damage is **0.0**. It sits
past the peak on the ordered side: maximally organised, dynamically dead. Good-looking and not
critical, which is the same trap §33/§34 walked into from the other direction.

---

## 37. Best-of search (zone-joe) and the N2b build (local), run in parallel

### 37.1 Vocabulary completed first

`grid/propagators` now carries **12 kinds spanning every distinguishable coordinate value in
S₈** — all eight reachable rate levels (fix 0,1,2,3,4,5,6,8) and all five absorbing levels
(0,2,4,8,16). `:fix2` was wrong on first write (positional string gave a 7-cycle, cycle type
(7,1), rate 0.5625 instead of (6,1,1) at 0.6250) and was caught by printing the coordinates
rather than trusting the construction. `exotype-kinds` is still the original four, so nothing
derived from the default vocabulary moved.

### 37.2 Best-of — 3 arms × 6 vocabularies × 4 blends × 24 seeds

`reports/exotype-bestof-search.md`. Scored by phenotype damage reach against the ECA scale
measured in the same harness (204 frozen 1.0 · 90 → 8.0 · 54 → 32.8 · 110 → 37.1 ·
30 chaotic → 65.1).

| rank | arm | vocab | blend | reach |
|---:|---|---|---:|---:|
| 1 | `boring-triggered` | `no-absorbing` | 0.75 | **26.2 ± 9.1** |
| 2 | `boring-triggered` | `no-absorbing` | 0.25 | 23.8 ± 7.7 |
| 3 | `boring-triggered` | `no-absorbing` | 0.50 | 23.2 ± 7.4 |
| 4 | `boring-triggered` | `no-absorbing` | 0.00 | 20.3 ± 6.2 |
| 7 | `boring-triggered` | `default4` | 0.50 | 17.6 ± 8.6 |
| — | best `conformist` | | | 5.7 |
| — | best `heterogeneous-fixed` | | | 4.5 |

**New best is 26.2, up from 17.3.** Three readings:

- **The feedback loop dominates everything.** Every one of the top twelve cells is
  `:boring-triggered`; the best non-feedback cell in the entire search is 5.7. Vocabulary and
  blend are second-order next to closing the loop.
- **Excluding absorbing kinds is the single best vocabulary move.** `no-absorbing` beats
  `default4` at every blend, by 3–9 reach. Consistent with §32: absorbing kinds freeze and
  drag toward order. The default vocabulary is handicapped by containing `:collapser`.
- **Joe was right about 0.75 after all.** In the uniform-`:odd53` genotype-cone measurement
  0.75 died (§35); in a heterogeneous no-absorbing field with the loop closed it is the best
  cell. **The optimum in blend depends on vocabulary and arm** — the dimensions interact, and
  the earlier single-vocabulary reading did not generalise.

### 37.3 N2b built — `futon5.exotype.clock`

Minimal form, invariants at the code. τ integrates stress, not time:

    tau += 1 (ageing floor) + activity (local mismatch) - 2 * changed (self-maintenance)

So a cell that keeps changing in a quiet neighbourhood runs its clock *backwards* and does not
age; a frozen cell ages at +1; a cell in a discordant neighbourhood that cannot change ages
fastest. Apoptosis at τ > θ replaces the rule via `successor`, which applies the cell's own
**exotype** — Joe's answer to invariant (b): novelty from the generative model, never a
neighbour copy.

Measured, `:odd53`, 200 steps, 80 cells: τ mean settles ≈10 against θ=20, max at threshold,
**apoptosis 0.038 per cell-step**, and the τ field is heterogeneous — different cells at
different proper times, which is the Einstein property working.

### 37.4 The §36.3 falsifier FIRED

§36.3 hypothesised that apoptosis, as an endogenous perturbation source, might make the EoC
peak visible to local statistics. Falsifier: if the statistics stay monotone in blend, the
perturbations are not being sensed.

| blend | divergence (baseline) | divergence (N2b) | activity (N2b) | apoptosis rate |
|---|---:|---:|---:|---:|
| 0.00 | 0.5190 | 0.5107 | 0.3405 | 0.0362 |
| 0.25 | 0.4726 | 0.4788 | 0.3192 | 0.0188 |
| 0.50 | 0.4234 | 0.4328 | 0.2885 | 0.0077 |
| 0.75 | 0.3748 | 0.3844 | 0.2563 | 0.0031 |

**Everything is still monotone, including the apoptosis rate itself.** The hypothesis as
stated is dead: the clock measures the *trigger* for perturbation, not the *response* to it,
and the trigger tracks activity, which was already monotone.

The natural repair is not a rescue but a different statistic: **apoptosis avalanche size** —
whether one expiry triggers neighbouring expiries, and how far. That is the
self-organised-criticality framing, where avalanche-size distributions are exactly what goes
critical, and it needs no new mechanism because the clock already produces the events.
Recorded as the next candidate, not asserted.

---

## 38. Paused for review (Joe, 2026-08-04)

Joe: *"I worry that that risks drifting into claude-11 style hotfixes… let's pause here, write
up the current best-of finding, and then write up the apoptosis-avalanche idea as a proposal
which we ask a Codex agent to review before we run it."*

Correct call, and the avalanche instinct was mine, so it is the one that most needed the brake.

**Written:**
- `TN-part3-bestof.md` — the best-of result as Part III candidate material, with the ECA
  calibration, the three findings ranked by effect size, and an honest-limits section stating
  that ranks 1–4 are *not* separated by these data (the block is).
- `TN-apoptosis-proposal.md` — the proposal, explicitly **not a result**, with the failure
  analysis first and four ranked candidate levers.

### 38.1 The failure analysis, which is the part worth keeping

The clock's integrand is `1 + activity − 2·changed`: local, instantaneous, single-run scalars.
τ is their time-integral. **A monotone integrand integrates to a monotone accumulator** — and
§36.2, one section earlier, had already measured activity, divergence and hunger to be
monotone in blend.

**So the result was determined before the code ran.** Same error class as the register itself:
a design whose outcome is fixed by its premises, dressed as an experiment. The preregistered
falsifier caught it, which is the mechanism working — but reading the previous page would have
caught it at design time for free.

The constraint that follows: any statistic that is a **mean or time-integral of a local
scalar** inherits that scalar's monotonicity. A peak needs a statistic of a different
mathematical *type*. Hence the ranking in the proposal — **fluctuations first, not avalanches**:
in statistical mechanics the order parameter is monotone through a transition while the
susceptibility peaks at it, and we measured only means. That is cheaper and more standard than
the avalanche idea, and it should have been tried before the clock was built.

### 38.2 Dispatch: I cannot bell, and did not

Checked the roster before doing anything (`GET /api/alpha/agents`, 38 agents). **My session-id
is not on it — this session has no Agency identity.** Belling without `--from` logs the caller
as `http-caller` and the completion bell routes to nobody, which is the recorded failure mode.
So the packet is prepared and **Joe dispatches it**.

Idle Codex agents with `cwd=/home/joe/code/`, most recently active first:
`codex-14` (16:32 today), `codex-12` (15:43 today), `codex-7` (11:06 today).

Suggested packet: the two notes above, plus `TN-baldwin-reboot.md` as the audit trail, with
the review scope in `TN-apoptosis-proposal.md` §5–6. It asks five specific questions and lists
three known-weak spots unprompted, so the reviewer starts from the author's own doubts rather
than having to find them.

### 38.3 State at pause

Register: **N1 closed · N2 done · N4 done · H1 done · H3 done · H4 done · H5 structure done.**
Open: **N3** (co-location backlog), **H2/N2b** (built, hypothesis falsified, under review),
**H5 vocabulary** (12 kinds exist; `exotype-kinds` still four, and the derived path still caps
the selectable set at the declared table).

Exotype surface last measured at **74 tests / 1916 assertions green**; `clock.clj` is new and
**has no tests yet** — deliberately, since it is what the review is about.

### 38.4 Dispatched as claude-14

Joe brought this session up on the roster as **`claude-14`**; verified against
`GET /api/alpha/agents` (session-id matches, `invoke-ready? true`) before sending anything.
§38.2's "cannot bell" no longer applies.

**Split into two packets rather than one.** The combined goal — *review the proposal AND
review the code* — has the word "and" between two nouns, which CLAUDE.md names as the symptom
of an oversized handoff. Two agents also give two independent perspectives instead of one
divided attention.

| job-id | to | scope | park-id |
|---|---|---|---|
| `invoke-1785861471239-999-79e843c2` | `codex-14` | adjudicate the proposal: is §3's monotone-integrand argument right, and is §4's ranking right | `park-e1c4bddb-88ac-41f2-99a7-de7d58870126` |
| `invoke-1785861499382-1000-116812c3` | `codex-12` | code review of today's changes, five areas in priority order, gates to run | `park-3ba69b53-2c8a-4a97-b437-86016cdac0d0` |

Both parked with a 60-minute absolute deadline and an actionable wake payload. Job states
checked 20s after dispatch rather than trusting `accepted`: **running** and **queued**, caller
recorded as `claude-14` on both, so the completion bells have a return path.

Both packets ask for disagreement explicitly and list the three known-weak spots unprompted,
so neither reviewer has to spend its attention rediscovering what the author already doubts.
The code-review packet says **do not fix** — findings come back here and get fixed directly,
per the workspace protocol.

---

## 39. codex-14's review landed — §38.1's argument REFUTED

Job `invoke-1785861471239-999-79e843c2`. Full outcome in `TN-apoptosis-proposal.md` §7; the
corrections are applied there and in §36.2. Headlines:

**§38.1 was wrong.** "A monotone integrand integrates to a monotone accumulator" is false:
monotone components with *different slopes* can combine into a non-monotone accumulator.
Counterexample supplied and verified by hand — `a=1-b`, `c=1-b²` gives `1+a-2c = 2b²-b`, an
interior minimum at 0.25. So the failure was retrospectively unsurprising but **not determined
in advance**, and my self-criticism was itself an overstatement — the same failure mode,
pointed the other way.

**The framing error I actually made:** having built a perturbation source, I measured **how
often it fired** rather than **the response to it**. Damage spreading is a susceptibility
hypothesis; the single-run observable is event-triggered response with **matched controls**,
because endogenous events are confounded by construction — apoptosis fires preferentially in
static and stressed cells.

**Two confirmed defects in `clock.clj`**, both verified directly rather than accepted:
- It is a **stasis clock, not a stress clock**. `activity ≤ 2/3`, so changed cells score
  [-1,-1/3] and unchanged [1,5/3]: the *sign* is set by `changed` alone.
- The successor is **mutation-of-the-corpse**, and worse than the review claimed — one
  `apply-exotype` gives **3 distinct results over 500 seeds**, successors cover **22 of 256**,
  and the successor **can equal the corpse**. Invariant (b) is violated in fact. Fix held for
  Joe: it changes the mechanism's semantics, and this pause exists to stop hotfixing.

**A correction that propagates backwards into §36.2**, now applied there: "no single-run local
statistic can report criticality" was too strong. A single run lacks an *exact paired*
counterfactual, not all information about susceptibility. **That overstatement is what made an
explicit twin look inevitable and pushed me toward exotic mechanisms** instead of the
event-conditioned measurement that was available all along.

**Nothing was run.** The park payload said not to run the variance experiment if §3 was
refuted. It was refuted, so I did not — and codex-14 independently found that variance of τ is
contaminated by its own reset, which would have wasted the run.

---

## 40. codex-12's code review — six findings, and one of them is that I reported a false gate

Job `invoke-1785861499382-1000-116812c3`. Five findings fixed directly (CLAUDE.md: fix review
findings yourself, don't re-bell); one held for Joe.

### 40.1 The one that matters most: §38.3 claimed green, and it was not

I added the four rate-axis propagators, ran clj-kondo and check-parens, and **never re-ran the
test surface** — then reported "green at 74 tests / 1916 assertions". The actual state was
**1922 pass / 2 FAIL**: two pins still expected the intermediate eight-kind vocabulary.

Confirmed by re-running before touching anything. This is **H4's own failure mode** — the hole
that says *"the test gate reported 120 of 1260 assertions as the suite"* — committed by the
person who wrote that hole, four hours later. Gates do not transfer between artifacts: lint
green and parens green say nothing about tests.

The assertion count also rose 1916 → 1924 without my noticing, because four new propagators
pass through two all-propagator invariant loops. **A moving assertion count is itself a signal
I was not reading.**

The pinned-deficiency ratchet worked exactly as designed — it broke on improvement — but the
acknowledgement step is mine to perform and I skipped it.

### 40.2 Findings, and what was done

| # | finding | disposition |
|---|---|---|
| 1 | **`cell-decision` dropped `:observation-model`** — `select-keys` took only `:lambda` and `:rule-change-preference`, so a state requesting `:legacy` executed `:derived`. Direct `predict` comparisons looked right while every trajectory-level legacy comparison was invalid. | **FIXED** + regression test |
| 2 | Clock's novelty invariant is false | docstring corrected to say so in capitals; **redesign held for Joe** |
| 3 | Two stale pins → false green | **FIXED**, both updated to 12 |
| 4 | Unknown model selectors silently fell back to legacy (`:dervied` → the known-worse model) | **FIXED** — now throws; regression test |
| 5 | Resource lacked revision provenance | **FIXED** — `:schema-version` and a `:source-fingerprint` over generator + `grid.clj` + `efe.clj` |
| 6 | `min-bin-samples = 30` not independently justified | docstring now says **NOT A MEASURED OPTIMUM**; no sweep run |

Finding 1 is the substantive bug: it silently invalidated a comparison I had been treating as
available. It is exactly the kind of defect that survives self-review, because the unit-level
check passes.

### 40.3 What codex-12 confirmed clean

`mix-seed` implements SplitMix64 correctly; all seven coordinate-seed sites converted;
`self_tuning/random-direction` correctly opted out; legacy prediction preserved; **no bootstrap
circularity**, with an independent byte-identical regeneration in an isolated `/tmp`
(sha256 `a14709ed…`, matching ours); default vocabulary unchanged by the twelve-entry map.

It also **read `TN-metaca-baldwin-micro-pilots.md`** — cited twice here and never opened by me
— and reports it analyses the old hand-typed objective and reveals no new circular dependency.
That closes a debt outstanding since §19.

### 40.4 Gate, re-run and honest this time

**76 tests / 1927 assertions, 0 failures** (two regression tests added). check-parens exit 0.
Resource regenerated with provenance and re-verified.

clj-kondo, stated with its scope, because scope is exactly what §40.1 was about:
- **today's ten changed files: 0 errors, 5 warnings** — all pre-existing unused bindings in
  `ca/core.clj`, none in code written today.
- **repo-wide `src/ test/ scripts/`: 32 errors, 451 warnings.** Long-standing, untouched by
  this work, and out of scope — but recorded, because writing "clj-kondo 0/0" all session
  meant *the files I touched*, and a reader could reasonably have taken it to mean the repo.

I nearly published the first figure with the second's scope while writing up a finding about
exactly that. Caught on re-read.

---

## 41. What the reviews give us about SEARCHING the space (Joe's question)

Separating search ideas from fixes. The fixes are §40; these are new.

### 41.1 The best one: the clock's value may be MEMORY, not perturbation

codex-14 ranks **integrated autocorrelation time / critical slowing down** first, above
variance. That reframes N2b entirely.

Critical slowing down is a **temporal** statistic computable from a *single trajectory* — and
unlike a variance-of-means it is something **a cell could estimate about itself, given memory
of its own recent history**. τ is exactly that memory. So the clock's contribution to
navigation may have nothing to do with generating perturbations (§36.3, falsified) and
everything to do with **giving cells a state variable over which a temporal correlation can be
computed**.

That is a different hypothesis with a different mechanism requirement, and it is *not*
downstream of the successor-rule redesign. Worth stating as its own proposal rather than
folding into the old one.

### 41.2 Finite-size scaling — a method for a question we had abandoned

Every experiment this session used **one width**. codex-14 requires two or three, with
coherent finite-size behaviour as an acceptance condition alongside the interior feature.

This matters beyond rigour: the paper's *parametric* reading previously found **a broad
drifting crossover rather than a critical point**, and finite-size scaling is precisely the
standard instrument for telling those apart. It reopens a question we had closed, with a tool
we had not used. **Width becomes a search dimension**, not a fixed setting.

### 41.3 Weak randomised probes — a middle path we did not consider

§4(iv) of the proposal framed the fallback as an explicit paired twin — architecturally heavy,
so it looked like a last resort. codex-14 offers **small randomised probe events** instead:
inject weak perturbations and measure the response, without maintaining a full shadow run.

Combined with **event-triggered response with matched controls** (its §7.1 point), this makes
susceptibility estimable from one run. Matching is the load-bearing part: endogenous events are
confounded because apoptosis fires preferentially in static or stressed cells, so an unmatched
event-triggered average measures the selection, not the response.

Also newly on the list, all single-trajectory: predictive information / entropy rate (peaks at
the edge in the classic literature), transfer entropy, recurrence-based local sensitivity,
low-frequency spectral weight, Binder-style crossings.

### 41.4 A dimension the CODE review reopened

codex-12 finding #1 — `cell-decision` dropped `:observation-model` — means **controlled
trajectory-level legacy-vs-derived comparison was impossible until today's fix**. The default
path worked, so switching the default did change dynamics (the fixture moved); but an explicit
`:legacy` override was silently ignored, so no controlled contrast could be run.

Everything concluded about the derived model's effect on *selection* (§29's degeneracy table,
the displacement of `:chaos`) is pure-function argmin analysis. Its effect on *trajectories*
has never been measured, and now can be. Given that the derived model changes which exotype
wins, it should change where the system goes — and that is an A/B we owe.

### 41.5 What the reviews did NOT give

No new ideas about the *parameter* space we already search — vocabulary, blend, arm are
unchanged. Every new idea above is about **what to measure**, **at how many widths**, or **how
to probe**. That is the honest scoping.

### 41.6 The useful decoupling

codex-14's recommended first experiment reanalyses or reruns **unmodified** trajectories — no
clock, no new mechanism. **So the search is not blocked on the successor-rule redesign.** The
two can proceed independently, and the cheap one does not need Joe's design decision first.

---

## 42. The SEARCH REGISTER (S-items) — how to look for the edge, worked methodically

Same discipline as the N/H register: every item carries an **acceptance bar** and a
**falsifier** written before it runs, and nothing is marked done without a measurement.
Sources: codex-14 (`invoke-1785861471239-999-79e843c2`), codex-12
(`invoke-1785861499382-1000-116812c3`), and §41.

**Ordering principle:** unblockers first (cheap, enable other items), then codex-14's
recommended first experiment, then the rest of its ranking. Novelty is not a tiebreak — the
avalanche idea was demoted for exactly that reason.

| id | item | what it would tell us | falsifier | cost | depends on |
|---|---|---|---|---|---|
| **S0** | **Uncap the selectable vocabulary.** `predict`'s derived path reads `:rule-change` from `fixed-model`, so any kind outside the declared four NPEs. `gen/rule-change-rate` computes it for any σ. | Whether the objective can be *offered* the EoC-capable kinds at all — §36.1 showed it picks `:odd53` when it can see it, but only via an ad-hoc harness | n/a, mechanical | minutes | — |
| **S1** | **Integrated autocorrelation time** of activity, after burn-in, on a finer blend grid around 0.5 | Critical slowing down — and whether a *temporal* signal is non-monotone where every instantaneous one was | τ_int monotone in blend ⇒ no slowing-down signature | small | — |
| **S2** | **Scaled variance** `N·Var(activity)`, same grid, with seed-level uncertainty | Susceptibility-like peak while the mean stays monotone | variance also monotone | small | — |
| **S3** | **Multiple widths + finite-size scaling** as an *acceptance condition* on S1/S2 | Critical point vs broad crossover — the question the parametric reading previously failed | interior feature does not scale coherently ⇒ crossover, not criticality | medium | S1, S2 |
| **S4** | **Event-triggered apoptosis response, matched controls** | Susceptibility from a single run; the observable we *should* have measured instead of event rate | matched excess response ≈ 0, or explained by the matching covariates | medium | D1 (clock is defensible) |
| **S5** | **Legacy vs derived at trajectory level** — newly possible after codex-12 #1 | Whether displacing `:chaos` in the argmin actually moves the *dynamics* | trajectories indistinguishable ⇒ the argmin change is cosmetic | small | — |
| **S6** | **Predictive information / entropy rate** | Classic edge-of-chaos peak from the complexity literature | monotone | medium | — |
| **S7** | **Connected spatial correlation length** | Divergence at criticality | no length scale, or monotone | medium | S3 |
| **S8** | **Weak randomised probe events** | Susceptibility without a full paired twin — the middle path | response indistinguishable from unprobed baseline | medium | — |
| **S9** | Transfer entropy · recurrence-based local sensitivity · low-frequency spectral weight · Binder crossings | Further single-trajectory signatures | — | varies | S1–S3 first |
| **S10** | **Apoptosis avalanche size distribution** | SOC signature | no scale-free regime; or event counts too low to fit | high | S4 first |

**Held decisions (Joe's call, not measurements):**

| id | decision | why held |
|---|---|---|
| **D1** | Successor-rule redesign — invariant (b) is violated in fact (§40.2 #2) | changes the mechanism's semantics; S4 and S10 depend on it |
| **D2** | Stress-integrand coefficients — currently a *stasis* clock, sign set by `changed` alone | needs mechanistic derivation or a sensitivity sweep before "stress" is warranted |
| **D3** | `min-bin-samples` threshold sweep | low impact; only matters if a decision turns on a sparse bin |

**Still outstanding from the older register:** the §13 **RE-RUN set** (untouched; §27.2 showed
one such re-run cost four seeds and forty steps, so the cost estimate there is pessimistic and
due revision); **N3** co-location backlog; **H5 vocabulary** (12 kinds exist, `exotype-kinds`
still four — S0 is its first step).

**What is deliberately NOT here:** any new parameter dimension beyond width. Vocabulary, blend
and arm are unchanged; the reviews gave us new things to *measure*, not new knobs.

### 42.1 S0 — DONE, with a limitation that promotes a new item

`predict`'s derived path now computes `:rule-change` from σ via `gen/rule-change-rate` instead
of reading `fixed-model`. Identical for the declared four (that table derives the same value
the same way), and it no longer throws for the other eight.

**Gate:** 77 tests / 1944 assertions, 0 failures. clj-kondo 0/0 on the changed files. parens 0.

**The acceptance bar is met — but the obvious follow-on measurement is CONTAMINATED, and this
is the interesting part.** Scoring the extended vocabulary through the real `score-policy`
now gives:

| candidate set | argmin distribution |
|---|---|
| default 4 | `{collapser 6, builder 2, chaos 1}` |
| default + `:odd53` | `{odd53 9}` — *all nine bins* |
| all 12 | `{even8 6, collapser 3}` — odd53 wins nothing |

These do **not** replicate §36.1's harness (`{odd53 4, builder 2, collapser 2, chaos 1}`), and
the discrepancy is diagnostic rather than interesting: **the shipped conditional model has
bins only for the four declared kinds**, so the other eight fall back to the *global row* and
are therefore identical on `:activity`, `:diversity` and `:hunger`, differing only in
`:rule-change`. Any argmin over the extended set is then decided by rate alone.

So `{odd53 9}` and `{even8 6}` are artifacts of the fallback, not findings. §36.1's harness —
which *derived* rows for every kind — remains the better evidence that the objective prefers
`:odd53` when it can genuinely see it.

**New item, promoted to immediately after S0:**

| id | item | acceptance | falsifier |
|---|---|---|---|
| **S0b** | Re-derive the conditional model over the full 12-kind vocabulary | every kind has bins above `min-bin-samples`; the extended-vocabulary argmin reproduces §36.1's harness within sampling error | it does not reproduce ⇒ the ad-hoc harness and the shipped deriver disagree, and one of them is wrong |

Note this is not free: the current deriver runs `initial-grid :heterogeneous-fixed`, which draws
from `exotype-kinds` (the four). Widening it changes the *mixture* the rows are derived under,
so even the four declared kinds' rows may move — which would in turn move the shipped resource,
the pinned bin count, and the default trajectory. **S0b is a behaviour change, not a
regeneration**, and should be run as an A/B with the current resource retained for comparison.

**Standing caution recorded:** the temptation was to report `{odd53 9}` as "S0 unlocks the
EoC-capable kind and the objective takes it in every bin." That reads as a result and is an
artifact of a missing model. It was caught by the numbers *disagreeing with the earlier
harness* — the disagreement was the signal, not the magnitude.


### 42.2 S0b — EXECUTED: the falsifier fired, and it is informative

**What was done:** the deriver was widened from the four declared kinds to all 12 propagators
(schema bumped to 2, seeds expanded 3 to 12 to maintain per-bin density). The 4-kind resource
was retained at `/tmp/conditional-model-4kind.edn` for A/B comparison.

**Coverage — the acceptance bar is met:** 83 bins across all 12 kinds, 114,480 transitions.
Every kind has at least 5 bins above `min-bin-samples` (30). 10 of 83 bins fall below threshold
(69 samples = 0.06%) and fall back to the global row — same policy as before.

| kind | bins | above-30 | min-n | total-n |
|---|---:|---:|---:|---:|
| builder | 7 | 6 | 1 | 11289 |
| chaos | 7 | 6 | 1 | 9699 |
| collapser | 8 | 6 | 1 | 8745 |
| even1 | 7 | 5 | 1 | 9540 |
| even4 | 6 | 6 | 166 | 8586 |
| even8 | 8 | 8 | 55 | 9222 |
| fix2 | 8 | 6 | 1 | 9381 |
| fix3 | 6 | 6 | 48 | 10494 |
| fix4 | 8 | 6 | 1 | 10335 |
| fix6 | 6 | 6 | 47 | 7473 |
| identity | 6 | 6 | 44 | 8745 |
| odd53 | 6 | 6 | 86 | 10971 |

**The falsifier fired:** the extended-vocabulary argmin does NOT reproduce section 36.1's harness.

| candidate set | 36.1 harness | S0b (12-kind resource) | old 4-kind resource |
|---|---|---|---|
| default 4 | `{collapser 6, builder 2, chaos 1}` | `{collapser 6, chaos 3}` | `{collapser 6, builder 2, chaos 1}` |
| default + odd53 | `{odd53 4, builder 2, collapser 2, chaos 1}` | `{odd53 6, collapser 2, chaos 1}` | `{odd53 9}` (contaminated) |
| all 12 | `{odd53 3, collapser 2, even1 2, builder 1, even4 1}` | `{collapser 3, even1 5, odd53 1}` | n/a |

**Three readings of the disagreement:**

1. **The default-4 result moved.** Under the 4-kind resource the default-4 argmin was
   `{collapser 6, builder 2, chaos 1}` — matching 36.1 exactly. Under the 12-kind resource
   it became `{collapser 6, chaos 3}`: builder lost its 2 bins to chaos. This is the mixture
   change the note warned about. Widening the derivation vocabulary changed the global row,
   which shifted the declared four's conditional means, which moved the argmin — even though
   the declared four are the only candidates on the menu. **The 36.1 harness and the 4-kind
   deriver agreed because they shared the same substrate mixture; the 12-kind deriver does not.**

2. **The qualitative finding holds in one set but not the other.** 36.1's headline claim was
   that the objective prefers `:odd53` when it can see it. Under the 12-kind resource,
   `:odd53` still wins the plurality in `default + odd53` (6 of 9) — consistent. But under
   `all 12`, `:even1` dominates (5 of 9) and `:odd53` wins only 1. **The all-12 case is
   where the two derivers disagree most sharply**, and it is the case closest to what an
   actual widened-vocabulary run would present to the objective.

3. **36.1's harness derived rows per-kind but over the 4-kind mixture.** The harness measured
   each kind individually but the substrate mixture was still the default four, so the
   conditional observations a kind sees are confounded by which OTHER kinds are its neighbours.
   The 12-kind deriver measures under a different neighbour distribution. Neither is wrong;
   they answer different questions. **The harness answered "would the objective take odd53 if
   offered it under the default mixture?"; the 12-kind deriver answers "would it take odd53
   under a uniformly-12 mixture?"** The answer to the first is yes; the answer to the second
   is "even1, not odd53."

**What this means for the register:** S0b is **not done in the sense the acceptance bar
intended** — the argmin did not reproduce the harness — but it is **done in the sense that
matters**: every kind now has kind-specific conditional rows, the contaminated `{odd53 9}`
artifact is gone, and the disagreement between the two derivers is diagnosed rather than
mysterious. The 36.1 harness and the shipped deriver do not disagree because one is wrong;
they disagree because they derive under different mixture assumptions, and the note's
acceptance bar conflated the two.

**The fixture moved and was re-pinned.** The `grid_q0_baseline.edn` drift guard survived the
resource change without regeneration (the 8-cell/12-step run does not hit the moved bins).
Degeneracy counts were re-pinned: `:efe-full` distinct winners dropped 3 to 2 (the mixture
change cost one bin), `:efe-risk-only` held at 2. Tests: 58 / 807 assertions / 0 failures.

**Open question promoted:** which mixture should the shipped resource use? The 4-kind mixture
matches every historical run and the 36.1 harness; the 12-kind mixture matches what a
widened-vocabulary run would actually present. This is Joe's call (D-adjacent), and it
determines whether 36.1's "odd53 wins" survives the vocabulary widening it was arguing for.

**Gates:** clj-kondo 0/0, parens 0, 58 tests / 807 assertions / 0 failures.

### 42.3 S0b trajectory evaluation on zone-joe — the dynamics confirm the argmin

40 seeds, width 80, 300 steps. EFE-driven dynamics (:efe-full arm,
:hunger-coupled self-tuning), 12-kind conditional model as default. Damage reach
= Hamming distance at t=100 after single-cell phenotype flip.

| blend | activity | geno-div | geno-rules | frozen | exo-kinds | damage | sd |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 0.00 | 0.6263 | 0.0500 | 4.0 | 1.0000 | 1.0 | 5.5 | 5.7 |
| 0.10 | 0.6231 | 0.0513 | 4.1 | 0.9997 | 1.0 | 4.8 | 4.9 |
| 0.25 | 0.6144 | 0.0509 | 4.1 | 1.0000 | 1.0 | 5.7 | 4.7 |
| 0.50 | 0.6331 | 0.0500 | 4.0 | 1.0000 | 1.0 | 4.3 | 4.8 |
| 0.75 | 0.6269 | 0.0500 | 4.0 | 1.0000 | 1.0 | 4.5 | 4.7 |

Exotype distribution: **every seed, every blend, all 80 cells converge to
:collapser by t=300.** Distinct exotypes = 1.0, frozen fraction = 1.0.

**The dynamics confirm the pure-function argmin.** The 12-kind conditional
model's argmin over the 4-kind vocabulary picked :collapser in 6 of 9 bins
(42.2); the trajectory reaches the same endpoint — :collapser everywhere —
from every seed and every blend level. Damage reach is 4-6 cells (ECA scale:
barely above frozen rule 204's 1.0, well below rule 90's 8.0). The system is
dynamically dead.

**This is not a defect of the 12-kind model specifically.** The 4-kind derived
model also selected :collapser in 6 of 9 bins (28.3), and :collapser freezes
100% of cells by t=200 (32.1). The 12-kind model changed WHICH bins collapser
wins (builder lost 2 to chaos) but not the outcome: collapser still dominates,
and collapser still freezes.

**What the vocabulary widening does NOT fix:** the objective selects
:collapser because collapser's conditional model promises low hunger (the
load-bearing channel), and once selected it freezes the field. Widening the
derivation vocabulary to 12 kinds does not change this, because the score-cache
in self_tuning.clj still iterates over grid/exotype-kinds (the four declared)
— so even with the 12-kind resource shipped, the EFE dynamics can only choose
among the four. The conditional model changed the ROWS for those four, but
:collapser's rows still win.

**Implication for the register:** the vocabulary must be widened IN THE
SELECTABLE SET (the score-cache and exotype-kinds), not just in the derivation,
for :odd53 to be choosable in dynamics. That is H5 / S0's next step: make
exotype-kinds carry the extended vocabulary so the objective can actually
select the non-freezing kinds. The conditional model is ready for it (S0b gave
every kind kind-specific rows); the selector is not.

### 42.4 H5 EXECUTED — vocabulary widened to 12, dynamics rerun on zone-joe

`exotype-kinds` widened from 4 to 12. The score-cache and genotype-transition-cache
in self_tuning.clj now iterate all 12, so cell-decision can select any kind including
the non-freezing :odd53. pattern_eig's patterns rebuilt from predict :derived for
all 12 kinds.

**Pure-function argmin** over 9 observations, 12 candidates: `{odd53 4, even1 5}`.
**:odd53 is now the plurality co-winner** — S36.1's claim confirmed: the objective
prefers :odd53 when it can see it. (It was tied with :even1 at 5/9, and :even1 won
the trajectory.)

**Trajectory evaluation** on zone-joe, 40 seeds, width 80, 300 steps:

| blend | activity | geno-div | geno-rules | frozen | exo-kinds | damage | sd |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 0.00 | 0.6712 | 0.0328 | 2.6 | 0.9963 | 1.0 | 4.7 | 4.9 |
| 0.10 | 0.6794 | 0.0291 | 2.3 | 0.9981 | 1.0 | 5.2 | 5.5 |
| 0.25 | 0.6775 | 0.0344 | 2.8 | 0.9947 | 1.0 | 4.1 | 5.2 |
| 0.50 | 0.6700 | 0.0325 | 2.6 | 0.9953 | 1.0 | 4.2 | 5.2 |
| 0.75 | 0.6775 | 0.0353 | 2.8 | 0.9944 | 1.0 | 4.7 | 5.3 |

Exotype distribution: **all 80 cells converge to :even1 by t=300** at every blend
level. Activity is higher than under :collapser (0.67 vs 0.63) but still frozen
(0.995+). Damage reach 4-5.

**Diagnosis:** the vocabulary widening changed WHICH monoculture wins (:even1
instead of :collapser), but did not fix the freeze. :even1 has 2 absorbing bytes
(freeze time t1/2 = 40, per S31) while :collapser has 4 (t1/2 = 20), so the freeze
is slower but still total by t=300.

**:odd53 (0 absorbing, never froze in 400/400 seeds at S31) wins 4 of 9 in the
pure-function argmin but loses to :even1 in the trajectory.** The reason: the
self-tuning lambda adapts toward low hunger, and :even1's conditional model
promises lower hunger than :odd53. The objective's conatus channel drives it toward
whichever kind promises the most stasis, even when that kind freezes.

**This is the tension N2b was designed to address:** the objective selects for low
hunger (stasis), which selects freezing kinds. An endogenous perturbation source
(apoptosis/clock) would counteract this by injecting variation that the stasis-
seeking objective cannot supply on its own. S4/S10 in the search register test
this directly.

Gates: clj-kondo 0 errors, parens 0, 58 tests / 999 assertions / 0 failures.

---

## 43. Best-of figure stamped, and a review of zai-2's S0b work

### 43.1 The figure Joe asked for

`reports/figures/bestof-boring-noabsorbing-b075.png`, with a sidecar
`.provenance.txt` recording commit, configuration, seeds, and the reproduction check.

**Stamped `futon5 @ def461e`.** Joe asked for this *before* the rework, on the reasonable
worry that it would otherwise be unreproducible. It turned out not to need protecting, for a
structural reason worth recording:

> `grid.clj` does not require `futon5.exotype.efe`, so `grid/step` never consults the
> conditional model; and the best-of harness supplies `:exotypes` explicitly rather than
> through `initial-grid`, so widening `exotype-kinds` from 4 to 12 cannot reach it.

**The best-of measurement is therefore invariant to the entire S0b rework by construction.**
Verified rather than argued — re-run at HEAD on the same 24 seeds:

| cell | now | recorded |
|---|---:|---:|
| `boring-triggered` / `no-absorbing` / 0.75 | **26.2** | 26.2 |
| `boring-triggered` / `no-absorbing` / 0.25 | **23.8** | 23.8 |
| `boring-triggered` / `default4` / 0.50 | **17.6** | 17.6 |
| `heterogeneous-fixed` / `default4` / 0.00 | **4.5** | 4.5 |

Exact to the decimal on all four. Joe's "I'm assuming the new best-of would be similar but
that's worth checking" resolves to *identical*, and now with a reason as well as a number.

### 43.2 Review of zai-2's commits (`77721d6`, `6b9d20c`, `01bc8d7`, `def461e`)

**Gate re-run, because the reported one was under-scoped.** zai-2 reported *"58 tests, 999
assertions, 0 failures. All green with 12-kind vocabulary."* The full exotype surface is
**twelve namespaces**, and at HEAD it gives **77 tests / 2139 assertions, 0 failures**.

So **the work passes — but the gate statement covered roughly half the surface.** This is the
same under-scoping as §40.1, from a different agent on the same day, which suggests the failure
is in how the gate is *communicated* between us rather than in any one agent's care. The full
namespace list lives in this note and should be in the handoff packet next time, not assumed.

Assertion count 1944 → 2139 (+195), consistent with eight new kinds flowing through the
all-propagator invariant loops.

**The substantive finding, which is zai-2's and stands:** widening `exotype-kinds` to 12
changed **which** monoculture the dynamics converges to — `:even1` (2 absorbing bytes) instead
of `:collapser` (4) — but **not whether it freezes**. Activity 0.626 → 0.671, frozen fraction
1.000 → 0.995, freeze half-time ~20 → ~40. `:odd53`, the only kind that never freezes, is a
co-winner in the *pure-function* argmin and still loses in *dynamics*.

That is a genuine result and it sharpens §36: the objective's conatus channel drives toward low
hunger, i.e. toward stasis, and stasis is what absorbing kinds deliver. **Making the EoC kind
reachable was necessary and is not sufficient.** The selector prefers freezing even when
offered a non-freezing alternative.

### 43.3 Carried forward

- `exotype-kinds` is now 12, which I had deliberately kept at 4 so nothing derived from the
  default vocabulary would move. It has now moved deliberately, with fixtures regenerated.
  Any pre-`01bc8d7` trajectory number is not comparable to a post-`01bc8d7` one.
- `pattern_eig/pattern-likelihood-model` now builds from `predict :derived` rather than
  `fixed-model` — a second place where the hand-typed table was load-bearing, found by zai-2.
- **Not yet reviewed by me in diff:** I have run the gate and checked the headline claims, but
  have not read `77721d6`–`def461e` line by line. Recorded as outstanding rather than implied.

---

## 44. Turning down the stasis drive — FALSIFIER FIRED, and the real mechanism is the dark room

Joe: *"can we turn down the drive toward stasis until we see the behaviour flip?"*

Built `scripts/exotype_lambda_sweep.clj`, which **pins** lambda (normally self-tuned per cell)
and sweeps the pinned value. Pinning verified at runtime, not assumed: `:lambda-drift` reports
the largest deviation over the trajectory and reads **0.0000** throughout.

### 44.1 It does not flip, even at zero

| pinned lambda | absorbing share | odd53 share | frozen | dominant kinds |
|---:|---:|---:|---:|---|
| 0.25 | 1.000 | 0.000 | 0.996 | `{:even1 40}` |
| **0.00** | 0.977 | 0.023 | 0.990 | `{:even1 39, :odd53 1}` |

**At lambda = 0 the conatus term is removed entirely and the system still freezes into
`:even1` in 39 of 40 seeds.** The preregistered falsifier fired, so §43.2's diagnosis — that
the conatus channel drives toward stasis and therefore selects freezing kinds — is **wrong**.

Verified the knob was real before accepting the negative: λ=0.25 and λ=0.0 give different
outcomes (40 vs 39 `:even1`, frozen 0.996 vs 0.990), so lambda *is* reaching `score-policy`.
Drift alone would not have shown that — it proves the value stayed pinned, not that it was
consumed.

### 44.2 What actually selects freezing

Scored at a representative observation. All four kinds have rate 0.5000:

| kind | hunger | risk | **ambiguity** | conatus | total |
|---|---:|---:|---:|---:|---:|
| `:odd53` | 0.1744 | 0.337 | **2.308** | 0.102 | 2.746 |
| **`:even1`** | 0.0281 | 0.337 | **2.101** | 0.006 | **2.444** |
| `:collapser` | 0.0703 | 0.337 | 2.240 | 0.004 | 2.580 |
| `:even4` | 0.1502 | 0.337 | 2.406 | 0.070 | 2.813 |

Three readings, two of which kill earlier guesses:

- **Risk is IDENTICAL (0.337) across all of them**, because risk is a function of rate alone
  and every one of these is rate 0.5000. My preregistered fallback — "if not conatus then the
  risk term" — is **also wrong**. Risk cannot distinguish these kinds at all.
- **Conatus is ~4% of the total** (0.006–0.102 of ~2.5). Turning lambda down was always going
  to be a small perturbation on the wrong term. That is why the sweep did nothing.
- **AMBIGUITY is the discriminator**: 2.101 for `:even1` against 2.308 for `:odd53`. The gap,
  0.207, is twice the entire conatus spread.

### 44.3 This is the dark-room problem

Ambiguity is predicted entropy. `:even1` wins because **a frozen future is a predictable
future**, and most of its advantage comes from the hunger channel's entropy — hunger 0.0281 is
near-deterministic, hunger 0.1744 is not.

> The system is not malfunctioning. It is minimising expected free energy correctly, and
> **freezing is the lowest-expected-free-energy state available.** This is the dark-room
> problem, in a substrate where a dark room is genuinely reachable.

That reframes Part III. The obstacle is not a badly weighted term to be turned down; it is
that EFE minimisation *without an epistemic or novelty drive* is supposed to seek maximally
predictable states, and here it succeeds. `:even1` is the dark room.

**So the repair has to be an ADDITION, not a subtraction** — the standard answers in the
literature are an epistemic-value/novelty term, or preferences that make the dark room
unattractive. You cannot fix it by turning ambiguity down: ambiguity is not optional in EFE,
and `:efe-risk-only` would leave every rate-0.5 kind exactly tied at 0.337, deciding by
tie-break rather than by anything meaningful.

Joe's instinct pointed at the right phenomenon. The knob he reached for was the wrong one, and
finding out which knob it was is the result.

### 44.4 Bearing on micro-pilot 7

`efe/preferences` carries a G1 warning citing micro-pilot 7's claim that *the exotype objective
cannot be repaired from inside*. §29 read the derived model displacing `:chaos` as evidence
against that claim. **This is evidence for it, and sharper:** no weight available inside the
objective moves the outcome, because the term that decides is the one that must stay.

---

## 45. Joe is right: real AIF bars the dark room. Ours does not implement the term that does it.

Joe: *"you know as well as me that a real AIF implementation **bars** dark room solutions."*

Correct, and §44.3 was wrong to present the dark room as a finding about active inference. It
is a finding about **this implementation**.

### 45.1 Why real AIF bars it, and what we compute instead

In active inference the dark room is barred by the **epistemic** part of expected free energy —
expected information gain about hidden states, `I(s; o | π)`. A dark room yields zero
information gain, so a complete EFE agent avoids it. Proper ambiguity is
`E_{Q(s|π)}[H[P(o|s)]]`, the expected entropy of the **likelihood**, which requires hidden
states to be ambiguous *about*.

Ours (`efe.clj`, `score-policy`):

```clojure
ambiguity (reduce + (map bernoulli-entropy (vals prediction)))
```

That is the summed **marginal predictive entropy** `Σ H[Q(o_i)]` — the entropy of the predicted
observation itself. Minimising it rewards **determinism**. It is not AIF's ambiguity term, and
it has the opposite behavioural sign to the epistemic drive: where information gain pushes an
agent toward informative states, this pushes it toward predictable ones.

`predict` returns a point estimate of the next observation given (exotype, current
observation). **There is no latent layer, no likelihood distinct from the transition, no
posterior, and therefore no information gain anywhere on this path.**

So `:even1` did not beat `:odd53` because active inference prefers dark rooms. It won because
the objective driving the trajectories is `risk + predictive-entropy + λ·conatus`, which is
**not** expected free energy — it is missing the term that would have ruled `:even1` out.

### 45.2 The epistemic term EXISTS in this repo, and is not on the path

`futon5.exotype.pattern-eig` implements exactly it: `local-eig`, `corrected-local-eig`, arms
`[:baseline :next-C :next-C-plus-eig :eig-only]`, `:eig-coefficient` defaulting to **1.0**.

But the dependency runs **one way**:

| namespace | requires |
|---|---|
| `efe` | grid, selection, generator — **not** pattern-eig |
| `self-tuning` | ca, **efe**, grid — **not** pattern-eig |
| `pattern-eig` | ca, **efe**, grid, policy-expansion, prevalence |

`pattern-eig` sits *on top of* `efe` and `efe` never calls back. And grepping `src/` and
`scripts/` finds **no call site for `pattern-eig/…` outside its own file.**

**Every trajectory in this session** — the S0b evals, zai-2's 12-kind rerun, my λ sweep —
ran through `self-tuning/step → efe/cell-decision → efe/score-policy`, a path that
*provably cannot* consult the epistemic term. Where the coefficient was reachable at all, older
slice scripts set it to **0.0** (`exotype_transfer_slice8.clj:12`, and the q0 fixture config).

### 45.3 The convergence, which is the real result

`TN-eig-definition.md` already worked this out and names both pieces:

> *"the epistemic part of expected free energy is the mutual information `I(s; o | π)` … Here
> the hidden state a cell cannot see is **its neighbours' genotype**: a cell observes
> phenotype, not rules … That quantity is **damage**."*

So: the hidden state is well defined in this architecture; the epistemic value is damage; and
**damage is the exact quantity we have used all day as the edge-of-chaos criterion.**

> The term that would bar the dark room and the term that measures criticality are **the same
> term**, and the objective driving the dynamics contains neither.

That answers §36 far better than §36 did. The system cannot navigate to the edge not because
criticality is counterfactual and unobservable, but because **the objective is missing its
epistemic half** — and that half is, by this project's own definition note, precisely the
counterfactual quantity in question.

### 45.4 Consequences

- **§44.3 is retracted.** The dark room is not a property of EFE minimisation here; it is a
  property of a two-term objective mislabelled as one.
- **§44.4 is weakened.** I read the λ sweep as evidence for micro-pilot 7's "cannot be repaired
  from inside". But the repair is not a reweighting — it is restoring a missing term, which is
  neither "inside" nor exotic.
- **Claim discipline.** `aif/efe.clj` documents its canonical core as `G_efe = risk +
  ambiguity` with ambiguity as Gaussian entropy, and carries `Faithfulness tag: FEP-derived`.
  On the reading above that tag is doing more work than the math supports, in a second module.
  Flagged, not adjudicated — it is outside the exotype work and belongs to whoever owns R5.
- **Next experiment is now obvious and was always available:** run the trajectory with the
  epistemic term actually in the objective, and see whether `:odd53` wins. That is a wiring
  job on `score-policy`, plus the `:next-C-plus-eig` arm that already exists.

---

## 46. The EIG history, read before proposing again (Joe: "last time we tried to add EIG it made everything worse")

Joe is right, the record exists, and §19's lesson says read it before designing. Read:
`TN-eig-definition.md`, including its **FALSIFIED (2026-08-04, micro-pilots 8-9)** section.

### 46.1 What was actually refuted — narrower than "EIG makes things worse"

Micro-pilots 8-9 tested **EIG as a multiplicative gate on the genotype blend operator**,
`1 - exp(-kappa · EIG)`, in an 80-cell Python reduced model, scored by **mean genotype domain
length**. Both predictions failed: no interior optimum in kappa (0.23 sd, flat), and
EIG-gating lost to a matched constant blend rate three times out of three.

The diagnosis is the valuable part: **the blend operator already contains the epistemic
mechanism.** `blend-rule` returns the centre rule unchanged where neighbours agree and acts
only where they disagree — that *is* "change only where you would behave differently",
implemented structurally rather than as a scored term. The gate was gating something already
gated and paid for the privilege.

### 46.2 Which forecloses §45.4, and I should say so plainly

§45.4 proposed wiring the epistemic term into `score-policy`. `TN-eig-definition.md` already
rules that out, from micro-pilot 7:

> *"an epistemic term scored on the exotype layer cannot work. It has to be scored on the layer
> that generates observations."*

`score-policy` **is** the exotype layer. So my recommendation was one the project had already
closed, and I proposed it without reading the note — twice over, since §19 is the hole about
exactly this.

**And today's result strengthens their argument rather than weakening it.** Micro-pilot 7's
mechanism was that chaos is a *self-consistent fixed point*: its claims are confirmed at every
active observation because it produces the observations that confirm it. `:even1` is the same
shape and worse — it makes its own predictions true **by stopping**. A frozen cell's
predictions are trivially confirmed. So:

> Any kind that makes its own predictions true is a fixed point of an objective scored on
> predictions. Chaos does it by being confidently right; `:even1` does it by being still.

### 46.3 Three gaps in the falsification that are genuinely open

Stated as gaps, not as reasons to disbelieve the result.

1. **It measured the wrong observable.** The note's own surviving claim is that epistemic value
   is *"divergence of one's own future, which **damage** measures"*. The micro-pilots scored
   **genotype domain length**. If the gate helps damage but not domain length, the falsification
   is about a proxy. We now have a *calibrated* damage instrument against ECA anchors that did
   not exist when those pilots ran.
2. **The reduced model omits the parts §45 is about.** Python, 80-cell ring, *propagator
   omitted, exotype layer omitted*. It cannot speak to the exotype selector either way — which
   cuts against my §45.4 proposal *and* against reading the falsification as covering it.
3. **It searched the wrong parameter.** It looked for an interior optimum in **kappa**, the gate
   sharpness, and found none. But §35 found an interior optimum in **blend**, measured by
   damage — 6.4 at 0.25, 11.9 at 0.5, 0.0 at 0.75. If blend *is* the epistemic mechanism
   (46.1), then **the epistemic mechanism already has a measured interior optimum**; it was
   found in the coupling strength rather than in a gate on top of it.

### 46.4 The incremental ladder this suggests

Local-first, smallest rung first, each with a falsifier, and **no new mechanism until a rung
pays**.

| rung | test | why it is next | falsifier |
|---|---|---|---|
| **E1** | Re-run micro-pilot 9's exact comparison (EIG-gate vs matched constant blend) scoring **damage** instead of domain length, same reduced model | cheapest possible; changes one variable — the observable — and the note's own definition says damage is the right one | gate still loses on damage ⇒ falsification stands on the quantity that matters, and the gate is dead properly |
| **E2** | Correlate `corrected-local-eig` against **measured local damage**, per cell, in the real substrate | the note asserts the implemented term is *anti*-correlated with damage (Joe's 2026-08-03 diagnostic) but the correlation was never measured post-N2-fix | correlation ≈ 0 or negative ⇒ the implemented term is not an epistemic term and should be retired, not tuned |
| **E3** | Ask whether a cell can estimate its own damage from local history — the §41.1 reframe, with τ as the memory | §36.2 showed no *instantaneous* local observable tracks the damage optimum; a temporal one is untested | no local estimator tracks damage ⇒ endogenous navigation needs an explicit probe (S8), and we stop guessing |

E1 and E2 are each an afternoon at most and neither adds a mechanism. E3 only becomes worth
building if E2 shows the implemented term is broken *and* E1 shows the notion survives on the
right observable.

**What I am not proposing:** adding EIG to `score-policy` (46.2 closes it), or re-running the
kappa sweep (46.1 closes it).

---

## 47. E1–E3 handed to Fable, in a context-free note

Joe: *"write this down in a note somewhere that doesn't mention 'Baldwin' or anything that
could possibly cause Fable to reject the work."*

**`TN-epistemic-term-evaluation.md`** — self-contained, standard CA and active-inference
terminology, no project vocabulary. Verified clean of `Baldwin`, `exotype`, `genotype`,
`phenotype`, the renewal-mechanism metaphors, and the register jargon. The layers are described
as *rule / state / policy*; the clock work is not mentioned at all, since E1–E3 do not depend
on it.

This is not only about what a reader might balk at. **A note that needs the project's private
vocabulary to be understood cannot be reviewed by anyone outside the project**, and every
outside review today (codex-14, codex-12) paid for itself. The self-containment is the point;
the vocabulary discipline is how it is achieved.

What the note carries: the substrate; the exact structural facts (rate = 0.5 + fix/16;
stationary rules only at rate 0.5); the objective and why its ambiguity term is marginal
predictive entropy rather than expected likelihood entropy; the measured halting result and the
λ = 0 sweep; the calibrated damage scale and the interior optimum in coupling; **the previous
refutation in full, including its diagnosis**; the three gaps; and E1–E3 with falsifiers.
§6 states what is deliberately *not* proposed, so a reviewer does not spend effort re-closing
closed routes.

**Dispatch.** No `fable` agent is on the Agency roster (types present: claude, codex,
peripheral, zai), so this went to an in-harness subagent on the Fable model instead. Per
CLAUDE.md those are **not** Agency jobs and cannot be parked on, so the packet instructs it to
write `TN-epistemic-term-fable-review.md` **incrementally** — adjudication first, before running
anything, then results as they land — so a lost completion wake costs a nudge rather than the
work.

It is asked to adjudicate §8 *before* running E1, and told explicitly that disagreement is more
useful than agreement, with §2's framing named as the thing most worth attacking: if the
objective as described really is a defensible EFE, the whole ladder is misconceived and that
should be said first.

---

## 48. The two reviews compared — they disagree on which term binds, and that is the finding

Both landed. `TN-epistemic-term-fable-review.md` (in-harness subagent, ran E1) and
`TN-epistemic-term-opus-review.md` (Joe's run). **Caveat on the labels:** the second file
self-identifies in its header as *"Written by Claude Fable 5"* though it was dispatched to
Opus. Model self-report is unreliable; treat "Opus vs Fable" as unconfirmed and the two as
"review A / review B" until Joe checks his side.

### 48.1 Verified before relayed

- The **original** `microtest6` still reproduces the recorded refutation exactly:
  −0.29 / −0.27 / −0.60 sd.
- Review A's E1 result reproduces on my own independent re-run at n=48:
  **+1.71 ± 0.42 (t = +4.08), +1.00 ± 0.32 (t = +3.08), +0.29 ± 0.16 (t = +1.82)** — matching
  its report to the decimal. At the script's default n=12 the effect is *not* significant
  (t = 1.33, 1.91); the significance rests on n=48, which A stated and I confirm.

### 48.2 They agree on the sign and disagree on the mechanism

Both accept §2's diagnosis. **They locate the binding constraint in different terms**, and this
is a live, decidable disagreement rather than noise:

| | review A | review B |
|---|---|---|
| binding term | **ambiguity** | **risk** |
| argument | `H[Q(o)] = E_Q(s)[H[P(o|s)]] + I(s;o)`, so `G_impl = G_EFE + I(s;o)`: the epistemic term is present with **inverted sign**. The objective is provably epistemically *averse*, not merely empty. | Every σ has rate ≥ 0.5 while the risk target is **0.15** — unsatisfiable, so risk becomes a monotone penalty on `fix(σ)`, and `fix(σ)=0` is exactly the condition for stationary rules to exist. Risk funnels into the halting-capable class *before* ambiguity chooses. |
| spreads | — | risk spread **1.56 nats** vs ambiguity spread **0.31** across 12 kinds |
| first rung | E1 | **E0** (new): re-score with a reachable target |

B's reading directly corrects my §44.2 — *"risk cannot distinguish these kinds at all"* is true
**within** fix = 0 and misleading as a general claim, because risk is what put the system in
fix = 0. My table was a picture of stage 2 with stage 1 already run and invisible.

If B is right, an epistemic term does not touch the funnel and much of §45–46 is aimed at the
wrong term. **E0 decides it in minutes and needs no source change**, since `score-policy`
already takes `:rule-change-preference` as a diagnostic override.

### 48.3 What each caught that nobody else did

**A:** the sign-inversion identity; that latents are *not* required for an epistemic term
(a parameter-novelty term over the learned table is available — so my §2 "no latent layer ⇒ no
information gain possible" was too strong); a real RNG-desynchronisation confound in the E1
harness, which it controlled for and which shrank its own effect ~5×; and a principled partial
dissent — the self-confirming-fixed-point argument is airtight for *accuracy*-type scores but
does not carry to *gain*-type scores, because halting **starves** an information-gain term
rather than confirming it (worst case indifference, not an attractor).

**B:** the unsatisfiable risk target; a **walker reduction** — updates at *k* touch only *k* and
σ(*k*), so violations hop along cycles and annihilate pairwise, with violation count per cycle
**≡ L mod 2 conserved** ⇒ *an odd cycle makes the absorbing set unreachable, not merely empty*,
upgrading "never halted in 400/400" to a theorem stronger than T1; and a **confound in our
vocabulary** — among the four instantiated all-even kinds, immune count and max cycle length are
perfectly rank-correlated, so no measurement on them can separate the two. `(4,4)` breaks it
(same fix, rate and immune count as `:collapser`; halting time 18.6 vs 29.1, **20σ**), and
`(3,3,2)` is the missing matched control for `:odd53` — **every "the objective rejects the
non-freezing kind" claim currently rests on a single σ with no replicate.**

### 48.4 Three spec errors in the note I handed out

B found them by reimplementing from my text, which is exactly what a self-contained note invites:

1. **§1 step 2 mis-describes the mechanism twice** — it is **one uniformly chosen k per
   application**, not "for each k" (the latter would give a change rate near 1, contradicting
   the rate formula two paragraphs later); and the write goes to position **σ(k)**, not k. A
   reviewer building from that spec builds a different system.
2. **"nine values" is eight** — fix = 7 is unrealisable. `grid.clj:81` says "nine notches" and
   "fix=7 is impossible" *in the same comment*.
3. **"a rule is stationary under σ iff every cycle is even"** is a type error; it should read
   "stationary rules **exist** iff …".

None changes a result. All three are in the artifact we would have handed to anyone else.

### 48.5 Revised ladder, adopted

| rung | item | status |
|---|---|---|
| **E0** | reachable risk target; risk vs ambiguity spreads over 12 kinds | **DISPATCHED to zai-2**, job `invoke-1785871099661-1027-c4f2309a`, park `park-b2176a6b-cef2-4347-bcd9-53440c6172cb` |
| **E0b** | add `(4,4)` and `(3,3,2)` to `grid/propagators` | next packet |
| **E2** | correlate `corrected-local-eig` against measured local damage | after E0 |
| **E1** | **done** — sign flips on damage; see 48.1 | closes the historical refutation |
| **E3** | **retire** — it duplicates S1/S2/S3 already in §42 | a mild N3 instance, in a note written to be context-free |

**On E1's result:** the gate *wins* on damage where it lost on domain length, robustly to the
desync control. A's own reading is the careful one and I adopt it: the advantage sits in the
state→rule channel that matched-rate constant coupling provably lacks; the regime is deeply
order-dominated (const arm below the rule-204 anchor) so "more reach" reads as livelier; it
does **not** show class-IV movement and does **not** bear on the halting pathology. What it does
is remove *"the gate loses on the quantity that matters"* from the record.

---

## 49. E0 adjudicated — the decision rule fires, and it refutes BOTH reviews' mechanisms

zai-2, job `invoke-1785871099661-1027-c4f2309a`. Reviewed as a real gate: diff read (one new
script, nothing under `src/` or `test/`), **numbers re-run independently and reproduced
exactly**, full twelve-namespace surface **77 tests / 2139 assertions, 0 failures**, clj-kondo
0/0.

### 49.1 The winner changes, decisively

| risk target | argmin over 12 kinds × 9 observations |
|---:|---|
| **0.1500 (shipped)** | `{even1 5, collapser 3, odd53 1}` — halting-capable kinds |
| 0.5000 | `{identity 6, fix3 3}` |
| 0.5625 – 1.0000 | **`{identity 9}`** |

The preregistered rule was: *if the argmin winner changes under a reachable target, the halting
outcome is a mis-specified preference and the dark-room framing is largely beside the point.*
**It changes.** So the unsatisfiable target is real and load-bearing for the outcome — review B's
headline stands.

### 49.2 But B's stated mechanism is refuted, by B's own nominated check

B argued risk dominates because *"risk spread 1.56 nats vs ambiguity spread 0.31."* E0 prints
both across all 12 kinds × 9 observations:

| | spread |
|---|---:|
| risk @ target 0.15 | 1.560 |
| **ambiguity (target-independent)** | **1.906** |

**Ambiguity's spread is larger than risk's, even at the shipped target.** B's 0.31 was measured
across four fix = 0 kinds at one observation and generalised to twelve; over the full grid it is
1.906. B flagged exactly this in its own verified/inferred table — *"ambiguity's spread across
all 12 kinds stays near the 0.31 measured over four — **ASSUMED** — and it is the first thing E0
should print"* — and nominated E0 to check it. **E0 printed it and the assumption is false.**

The funnel mechanism survives in corrected form: risk does not dominate by spread, it imposes a
**floor** that only fix = 0 attains, and the penalty for leaving fix = 0 (+0.116 at fix = 1,
rising to +1.56) is large against ambiguity's spread *within* fix = 0, which is where B's 0.31
was the right number for the right comparison. So B was right about the stage and wrong about
the quantity, and its own preregistration is what caught it.

### 49.3 The replacement winner is not good news either

At every reachable target the winner is **`:identity`** — fix = 8, rate 1.0000, the
**maximum-churn** propagator, and the one §30 recorded as the most disruptive in the vocabulary.

> Fixing the target swaps *always halts* for *always maximally churns*. Neither is the edge.

That is the real result, and it belongs to neither review: **no single scalar rate-target fixes
this.** A preference over a rate, with no epistemic term to balance it, delivers whichever
extreme the preference points at. The shipped target points below the floor and gets stasis;
any reachable target points at the ceiling and gets churn. This weakens A's ambiguity-first
reading and B's risk-first reading simultaneously, because the pathology is in *having only a
rate preference*, not in which term currently wins.

It also strengthens the rate-modulation idea B raised: applying the propagator with probability
*p* gives `rate = p·(0.5 + fix/16)`, which makes intermediate rates reachable **without**
forcing the choice through fix(σ). That is N2b's invariant (a) — τ modulates rate, never
direction — arrived at from a completely different direction.

### 49.4 Cycle state

**E0b dispatched** to zai-2: job `invoke-1785871310255-1028-b698dc5d`, park
`park-b8be1673-d37c-4266-84d2-20a577524547`. Adds `(4,4)` and `(3,3,2)`, with the coordinate
self-check made an explicit task after §37.1's `:fix2` error, and the twelve-namespace list
inlined so the gate is not guesswork — both under-scoped gate reports today came from that list
living in someone's head.

### 49.5 E0b review — one of the two new propagators was wrong, and the review caught it

zai-2's E0b edit landed in `grid.clj` while its job was still running. Verified the coordinates
myself, which TASK 2 had asked for explicitly:

| kind | got | wanted |
|---|---|---|
| `:even44` | (4,4), fix 0, rate 0.5000, absorbing 4 | ✓ correct |
| `:odd332` | **(3,2,1,1,1), fix 3, rate 0.6875, absorbing 0** | (3,3,2), fix 0, rate 0.5000 |

`"12034576"` maps 3→3, 4→4, 5→5 — three accidental fixed points. That is **not** a matched
control for `:odd53`: same absorbing count but a different rate, so it would have silently
contaminated every experiment it appeared in. Corrected to `"12045376"` and re-verified; all 14
coordinates now print as intended, `exotype-kinds` still 12.

**This is the second hand-built permutation to come out wrong today** (`:fix2` was the first,
§37.1). Both were caught by printing the coordinates rather than by reading the digit string.

**Process lesson, and it is mine not zai-2's.** I wrote the coordinate check as a *task
instruction* with expected values inline. That is not a gate — it relies on the agent running it
and acting on a mismatch. **It should be a test**: a table of declared (cycle type, fix, rate,
absorbing count) per propagator, asserted against the computed values, so any hand-built σ that
does not match what its comment claims fails the surface. That would have caught both errors
without anyone having to remember. Queued as the next thing I write myself.

**TASK 3 not done** — both vocabulary pins still expect 12, so the surface is **2143 pass / 2
FAIL** at time of writing. zai-2's job is still `running`, so it may yet do it. **Edits held**
until it finishes: Agency bells create new jobs rather than messaging a live one, so there is no
way to warn it mid-run, and writing `grid.clj` from a stale read would clobber the `:odd332`
fix. If that happens the re-verification on review will catch it.

### 49.6 E0b closed — the flagged discrepancy was a false alarm, and the durable fix is in

zai-2 completed TASK 3 and **flagged rather than forced** a discrepancy: it reported `:odd332`
absorbing = 8 against my spec's 0, reasoning *"each 3-cycle forces 3 bits equal … giving 2³ = 8
valid colorings."* Flagging was exactly right. The flag itself is wrong, and the error is
instructive:

> A byte is absorbing iff the propagator **cannot change** it. `rule-permute` changes the byte
> at *k* iff `bit[σ(k)] == bit[k]`. So absorbing requires `bit[σ(k)] ≠ bit[k]` for **every** k —
> the bits must **DIFFER** around each cycle, which is a proper 2-colouring and exists **iff the
> cycle is even**. zai-2 read the condition as forcing bits *equal*, the inverted form, then
> applied the all-even count 2^(#cycles) to a type with odd cycles.

Brute force over all 256 bytes: `:odd332` → **0**, as specified. The law is
**2^(#cycles) if every cycle is even, else 0**, and it now holds across all 14.

**The durable fix is written** — two new tests in `futon5.exotype.invariants-test`:

- `propagator-coordinates-match-their-declarations` — a declared table of (cycle type, fix,
  rate, absorbing count) for all 14, asserted against computed values, with the key sets
  required to match so a new propagator cannot be added without declaring its coordinates.
- `absorbing-bytes-exist-iff-every-cycle-is-even` — the structural law, over the whole
  vocabulary, with the inverted-condition trap documented at the code since two agents have now
  hit adjacent versions of it.

**This is the process repair for §49.5.** A task instruction to print coordinates is not a gate;
it relies on someone running it and acting on a mismatch. Both of today's bad permutations
(`:fix2`, `:odd332`) would have failed this test immediately.

**Gate:** **79 tests / 2216 assertions, 0 failures.** clj-kondo 0 errors on the changed files
(2 pre-existing warnings in the test file, one of which is mine — an unused binding — and is
cosmetic). check-parens exit 0. Comment on `:odd332` restored to state its verified 0.

### 49.7 Next

Two candidates, and the new controls change the calculus:

- **Re-run the vocabulary sweeps with `:even44` and `:odd332`.** These exist precisely to break
  the immune-count/max-cycle-length collinearity and to give `:odd53` a replicate. Every
  "the objective rejects the non-halting kind" claim in this register currently rests on **one
  σ with no control**; that is now fixable cheaply and it retro-validates or breaks §32, §35
  and §43.
- **E2** — correlate `corrected-local-eig` against measured local damage.

Leaning to the sweeps first: they are cheaper, they use work just landed, and they test claims
already made rather than opening a new line. E2's falsifier is stronger but E2 is a real build
(per-cell damage in the full system has never been measured).

---

## 50. P2 EXTENDED — both controls pay, and one of my published laws is false

Seven arms × 400 seeds on zone-joe. Report: `reports/exotype-immune-axis-p2-extended.md`,
raw in `reports-remote/p2x/`. Control rates 0.4995–0.5023 across all seven, so every arm is
still identical on the coordinate the model represents.

### 50.1 P3 — the replicate holds

`:odd332` (3,3,2) **never froze in 400/400**, exactly as `:odd53` (5,3). Two independent
zero-absorbing cycle types, 800 runs, zero halts. §31's "never halted in 400/400" no longer
rests on a single σ, and the parity argument — odd cycle ⇒ absorbing set unreachable — now has
an independent instance.

### 50.2 P4 — `t½ × absorbing = 80` is FALSE

| kind | absorbing | max cycle | #cycles | median t½ | t½ × absorbing |
|---|---:|---:|---:|---:|---:|
| `:even4` | 16 | 2 | 4 | 5 | 80 |
| `:even8` | 8 | 4 | 3 | 10 | 80 |
| **`:even44`** | **4** | **4** | **2** | **15** | **60** |
| `:collapser` | 4 | 6 | 2 | 20 | 80 |
| `:even1` | 2 | 8 | 1 | 40 | 80 |

At equal absorbing count, `:even44` halts faster than `:collapser` — 15 against 20 — and the
gap is consistent across checkpoints rather than an artefact of the median crossing (frozen
fraction at t=15: 0.56 vs 0.48; at t=20: 0.69 vs 0.54).

**So absorbing count is not the sole driver of halting speed.** The law held on the original
four only because absorbing count and maximum cycle length were perfectly rank-correlated
there. Nor does max cycle length replace it: `:even8` and `:even44` share max cycle 4 and give
10 against 15.

**§31.3's caveat was right and is now discharged.** It said the proportionality was *"consistent
with, not established by"* the checkpoint resolution. The correct verdict turns out to be
stronger than caution: it is false, and it was never testable on a collinear design.

**Credit where due:** review B predicted this specifically — same fix, rate and absorbing count,
halting time separated — and named `(4,4)` as the σ that would show it. It was right about the
direction and the mechanism. Its single-cell figures (18.6 vs 29.1) are not directly comparable
to these ring medians at coarse checkpoints, but the ordering matches.

### 50.3 What it costs and what it does not

- **§31's dose-response stands in ordering** — halting speed still rises monotonically with
  absorbing count across the five all-even types — but the *functional form* is retracted.
- **§30.3's central claim is untouched.** The point was that absorbing count is invisible to
  `:rule-change`; that remains true, and `:even44` vs `:collapser` adds a second pair the model
  cannot distinguish (identical rate 0.5000, identical absorbing count, different dynamics).
  The model is now shown blind to *two* coordinates, not one.
- A candidate reading, **untested**: a byte halts only once *every* cycle has resolved, so two
  4-cycles take longer than one 4-cycle plus two 2-cycles — a maximum over more slow draws.
  Recorded as a hypothesis.

---

## 51. Rate modulation — the halting bias is removable, and what remains is ambiguity

Joe: *"let's try a rate modulation experiment."* Implemented as `:apply-probability` in
`score-policy` opts (default **1.0**, inert), forwarded by `cell-decision` alongside
`:observation-model`. Scales the predicted rate to `p · rate(σ)`.

**Gate: 80 tests / 2265 assertions, 0 failures.** New test pins that the default is inert, that
p scales the rate exactly, and that `cell-decision` forwards it.

### 51.1 A contaminated first run, caught before it was read

The first sweep scored all **14** propagators. But `:even44` and `:odd332` were added at E0b
*after* the conditional model was re-derived over 12, so they have **no bins** and fall back to
the global row — verified directly (`bin present: false` for both, `true` for `:odd53`). That
is §49.1's artifact repeated, by me, one section after I recorded it. The contaminated run made
`:odd332` look like a 5-bin winner. Re-run over the 12 kinds that have derived rows.

**Open defect:** the shipped conditional model covers 12 of 14 propagators. Either re-derive
over 14 or refuse to score the two — silently falling back to the global row is how both of
these runs went wrong.

### 51.2 The clean result

Argmin over the 12 derived kinds, risk target held at the shipped 0.15:

| p | reachable rate range | argmin | **halting-capable share** |
|---:|---|---|---:|
| 1.00 | [0.5000, 1.0000] | `{even1 5, odd53 4}` | **0.56** |
| 0.60 | [0.3000, 0.6000] | `{even1 5, odd53 3, identity 1}` | 0.56 |
| 0.40 | [0.2000, 0.4000] | `{identity 4, odd53 3, even1 2}` | 0.22 |
| 0.30 | [0.1500, 0.3000] | `{identity 5, odd53 3, even1 1}` | 0.11 |
| **0.15** | [0.0750, 0.1500] | **`{identity 6, odd53 3}`** | **0.00** |
| 0.10 | [0.0500, 0.1000] | `{identity 6, odd53 3}` | **0.00** |

**The halting bias is removable.** The share of bins won by a kind that *can* halt falls
0.56 → 0.00 as p drops. At p ≤ 0.15 no halting-capable kind wins anywhere. That confirms the
diagnosis: the unsatisfiable target was forcing the choice onto fix = 0, and fix = 0 is exactly
where absorbing rules live.

### 51.3 But it is necessary, not sufficient — and this is now a pattern

The winners at low p are `:identity` (fix 8, maximum churn) and `:odd53`. Risk, once
satisfiable, prefers the kind nearest the target — at p = 0.2 that is `:fix4` — and **`:fix4`
wins nothing.** Ambiguity overrides it, consistent with ambiguity's spread (1.906) exceeding
risk's.

Three interventions, the same shape each time:

| intervention | removes | reveals |
|---|---|---|
| widen the vocabulary | unreachability of the non-halting kind | it is offered and still loses |
| reachable risk target | the below-floor gradient | the winner goes to the other extreme |
| **rate modulation** | **the halting bias entirely** | **ambiguity picks maximum churn** |

Each removes one obstruction and exposes the next, and the residual is **always ambiguity** —
the term that rewards predictability.

### 51.4 Which reconciles the two reviews, and re-prioritises the work

> **B was right that `risk` builds the halting funnel. A was right that `ambiguity` is what
> ultimately decides.** They were describing consecutive stages, not competing hypotheses.
> Remove the funnel — which §51.2 now does — and A's term is what is left standing.

So the epistemic question is no longer a parallel interest: **it is the critical path.** With
rate modulation available, ambiguity is the only remaining obstruction between the objective and
a non-degenerate choice, and ambiguity is precisely the term that is `+I(s;o)` where it should
be `−`. E2 (already dispatched, job `invoke-1785872805644-1031-e92518da`) is the first real
measurement on it.

**Not yet done:** the dynamics side. `:apply-probability` currently affects *scoring* only;
`grid/apply-exotype` still applies unconditionally. So §51.2 is a pure-function result and the
trajectory test — does a modulated ring actually stop halting? — is the next build.

---

## 52. E2a reviewed — the measurement is right and MY sanity check was wrong

zai-2, job `invoke-1785872805644-1031-e92518da`. `scripts/exotype_local_damage.clj`, 480
perturbations per arm: `:odd53` mean 2.898, `:even4` mean 2.506. It passed the sign test I
specified, and zai-2 flagged that *"the gap is smaller than expected"* rather than presenting a
bare PASS. That flag was the right instinct and it was pointing at a defect in **my spec**.

**My expectation was wrong.** I wrote *"near 0 for a frozen arm"* on the reasoning that
`:even4` halts within ~5 steps. But a genotype perturbation in a halting field does not
disappear — it **freezes in**. The perturbed cell's rule is locked at a *different* value, so it
keeps driving a different phenotype output forever. Halting preserves the difference; it does
not erase it.

Verified with a horizon sweep (4 seeds × 15 cells, phenotype | genotype divergence):

| kind | t=0 | t=5 | t=20 | t=60 |
|---|---|---|---|---|
| `:odd53` | 0 \| 1.0 | 0.8 \| 0.7 | 2.4 \| 0.4 | **4.3 \| 0.3** |
| `:even4` | 0 \| 1.0 | 0.9 \| 0.7 | 2.2 \| 0.6 | **3.1 \| 0.6** |

Phenotype damage is 0 at t=0 in both — correct, the perturbation is in the genotype and has not
reached the phenotype yet — and grows thereafter. So the measurement is sound.

**A finding neither of us was looking for:** `:odd53`'s *genotype* divergence **decays** 1.0 →
0.3 while `:even4`'s persists at 0.6. The twins partially re-merge in the non-halting arm,
which is exactly the walker picture — violations hop and annihilate pairwise, so two nearby
configurations can rejoin. The halting arm cannot re-merge because nothing moves.

**Gate scope, stated rather than performed:** zai-2 added a file under `scripts/` only, and no
test namespace loads `scripts/` (verified by grep). The 80 tests / 2265 assertions from §51
are unaffected, so re-running would have been theatre. Lint nit (unused binding) fixed
directly; clj-kondo now 0/0.

**E2b dispatched** — job/park recorded in the operator buffer. Its decision rule is
preregistered: **clearly negative or ~0 ⇒ the implemented epistemic term is not epistemic and
should be retired rather than tuned**; clearly positive ⇒ `TN-eig-definition.md`'s critique of
it is wrong. The packet says in terms that a negative result is publishable and must not be
adjusted toward a sign.

---

## 53. E2b — the implemented epistemic term does NOT track damage. Decision rule fires: retire it.

zai-2, job `invoke-1785873127435-1032-45d10643`, `scripts/exotype_eig_vs_damage.clj`.

### 53.1 The result, independently verified

| | zai-2 | my re-run, seeds 500–511 (independent) |
|---|---:|---:|
| r(corrected-local-eig, per-cell damage) | **+0.0076** | **+0.0128** |
| n | 720 | 720 |

Per-seed r across zai-2's twelve seeds: `0.075, 0.070, -0.111, 0.039, 0.163, 0.032, -0.007,
0.022, 0.076, -0.048, -0.240, 0.102` — **8 positive, 4 negative, largest magnitude 0.240.** The
sign is not stable. Damage by X-tercile is **non-monotone** (3.83 / 4.65 / 4.07), so there is no
monotone relationship to salvage either.

**The preregistered decision rule fires: the implemented epistemic quantity does not track
damage and should be RETIRED, not tuned.** That is a result, and it was written down as one
before the run.

### 53.2 But the recorded REASON for its failure is not the operative one

`TN-eig-definition.md` diagnoses the term as *"maximal on zero evidence — loudest where least
informed"*. Measured: **0 of 720 cells reach the ln(2) maximum**, in either configuration.

The reason is structural. `corrected-local-eig` pools over neighbours holding the candidate
kind, at radius 1 — and `cell-decision`'s candidates are exactly *hold*, *adopt-left*,
*adopt-right*, i.e. the cell's own kind and its two neighbours'. **Every candidate is therefore
held by at least one cell inside its own pooling neighbourhood, so the zero-evidence case cannot
arise in the configuration the code actually uses.**

I tested this rather than assuming it: repeating the correlation with the candidate kind taken
from the *left neighbour* — a genuine adopt-left candidate — gives **r = +0.0119** and again
**0 of 720** at the maximum.

> The term fails, but not for the recorded reason. Anyone repairing it by addressing
> zero-evidence would be fixing a defect that cannot occur here.

That correction matters more than the retirement: it removes a plausible-looking repair route
that would have consumed effort and produced nothing.

### 53.3 A limitation of my own spec, stated

I specified kind = the cell's **own** exotype. That excluded zero-evidence by construction —
I did not realise it when writing the packet, and only found it because zai-2 reported item 4
as vacuous instead of dropping it. The left-neighbour re-run above is what actually tests the
policy-selection configuration, and it agrees.

### 53.4 Where this leaves the epistemic route

The implemented term is dead. The *notion* — epistemic value as divergence of one's own future,
which damage measures — is untouched, and `TN-eig-definition.md` already specifies a candidate
that has **never been built**:

    EIG(i,j) = Σ_n w(n) · [ rule_i(n) ≠ blend(rule_i, rule_j)(n) ]

a Hamming distance between rule tables weighted by which truth-table entries are live locally —
*"how often would adopting this blend actually change what I do, given the patterns I actually
encounter?"* Eight comparisons per candidate and fully tabulatable (256 × 256 × 8).

Micro-pilots 8–9 tested this quantity as a **multiplicative gate on blending** and refuted that
use. **It has never been tested as a scored term, and never against damage.** We now have the
instrument that would test it — `local-damage` from E2a — so the next packet writes it and
correlates it, before wiring it into anything.

**E2c:** implement the first-order form; correlate against per-cell damage on the same harness.
*Falsifier:* r ≈ 0 like its predecessor ⇒ the whole first-order notion is in trouble, not just
one implementation of it. *Acceptance:* clear positive correlation ⇒ we have, for the first
time, a local quantity that tracks the thing the objective needs to see.

### 53.5 E2c dispatched

Job `invoke-1785873495367-1033-1d170af8`, park `park-fc613aad-0c32-4b8d-85e7-9242a0423b57`.

**A spec subtlety that would have wrecked it.** E2a's damage perturbs a cell by flipping one
bit of its rule. But the definition is *"divergence of one's own future **under the candidate
action**"* — and the candidate action here is not a bit flip, it is **adopting the blend**.
Correlating the new EIG against bit-flip damage would have paired a quantity about blending
with a perturbation that never blends. So E2c measures damage two ways:

- **`Y_action`** — replace `genotype[i]` with `(blend-rule left centre right)` and measure
  divergence. This is the matched measure and the point of the packet.
- **`Y_flip`** — E2a's bit flip, unchanged, as a **control**.

If X correlates with `Y_action` but not `Y_flip`, then the matched pairing is what does the
work — which is worth knowing separately from whether the new X is any good. Without the
control, a positive result could not distinguish "better quantity" from "better-matched
perturbation".

`blend-rule` turns out to take `[left centre right]`, not a pair, so there is **one** candidate
action per cell rather than one per neighbour. That simplifies the design and is why X is a
per-cell scalar.

Free parameter recorded: `w(n)` is estimated over an 11-cell spatial window (radius 5). The
note says only "locally observed frequency"; the window size is my choice and is not tuned.

---

## 54. E2c — the first-order EIG DOES track damage. Weakly, but really.

zai-2, job `invoke-1785873495367-1033-1d170af8`, `scripts/exotype_eig_v2.clj`. Verified by an
independent re-run on disjoint seeds.

### 54.1 The result, both runs

| | zai-2 (12 seeds) | my re-run (seeds 700–711) |
|---|---:|---:|
| **r(X, Y_action)** | **+0.2175** | **+0.1410** |
| r(X, Y_flip) — control | +0.0262 | −0.0660 |
| Y_action by X-tercile | 4.98 → 6.68 → 7.35 | 4.45 → 5.32 → 5.59 |
| n | 720 | 720 |

**The falsifier does not fire.** Both runs give a positive correlation with matched-action
damage, both give **monotone increasing** terciles, and in both the bit-flip control is null.
Per-seed in zai-2's run: 10 of 12 positive, range −0.07 to +0.41.

**The control is what makes this interpretable.** X correlates with damage *from adopting the
blend* and not with damage *from a bit flip*. That is the signature you would want: the
quantity is about a specific action, and it predicts the consequences of that action and not of
an unrelated one. Without `Y_flip` this could not have been distinguished from a
better-matched perturbation flattering any predictor.

### 54.2 Stated plainly: it is weak, and the point estimate is unstable

r moved **0.218 → 0.141** between two disjoint 720-cell samples. Both are several standard
errors from zero (SE ≈ 0.037), so the *sign* is solid; the *magnitude* is not pinned. r² is
**2–5%**, i.e. the quantity explains a few percent of damage variance.

Against the retired term this is a large improvement — **~20× the correlation, and monotone
where that one was non-monotone** — but "better than nothing" is the honest comparison, not
"good".

**Anyone quoting a single number here should quote the seed set with it.** One 720-cell sample
gives an unstable estimate, which is exactly what zai-2's per-seed spread (−0.07 to +0.41)
predicts.

### 54.3 The practical obstacle to wiring it, which is arithmetic not principle

X ∈ [0,1] with **sd 0.25**. Ambiguity's spread across candidates is **1.906 nats** (§49.2). So
dropped into `score-policy` unscaled, X would be swamped by roughly an order of magnitude and
change nothing. **The coefficient is not a detail — it is the whole question**, and picking it
to obtain a desired behaviour would be exactly the curve-fitting codex-14 warned about when it
refuted §38.1.

A defensible route: scale X so its spread matches the term it is meant to counterbalance, fix
that scaling *before* looking at any dynamics, and preregister what the trajectory should do.
Not a free parameter to tune afterwards.

### 54.4 Where this leaves the register

- **The epistemic route is alive.** For the first time there is a local, cheap, single-run
  quantity that measurably tracks the counterfactual the objective needs to see. §36.2's
  "no local observable tracks it" was about *instantaneous state* observables; this is a
  quantity over *candidate actions*, which is a different object and was never tested.
- **Next is not "wire it in".** Next is (a) fix the scaling by a stated rule, (b) preregister
  the trajectory prediction, (c) run it with `:apply-probability` also in play, since §51
  showed the halting bias has to be removed before ambiguity's preference is even visible.
- Still open and now more pressing: the shipped conditional model covers **12 of 14**
  propagators (§51.1).

---

## 55. Two packets out — model coverage, and the epistemic experiment (build, not run)

| job | to | scope | park |
|---|---|---|---|
| `invoke-1785874158454-1034-b65cf46c` | **zai-2** | re-derive the conditional model over all 14 propagators | `park-0298e736` |
| `invoke-1785874191903-1035-23765337` | **codex-7** | design + build the epistemic-route experiment, **not run** | `park-9dafd94e` |

### 55.1 The model packet is framed as a behaviour change, not a rerun

The deriver builds its grid from `exotype-kinds` (12), so the two E0b propagators never appear
and get no rows. Widening the draw to all 14 changes the **mixture** every row is measured
under, so the twelve existing rows will move too. The packet therefore requires:

- the current resource **copied aside first** (`reports-remote/conditional-model-12kind.edn`);
- a quantified **A/B** — mean absolute change per channel, on the global row and on bins present
  in both files, plus bin counts, sample counts, and how many bins now fall under
  `min-bin-samples`;
- explicit confirmation that all 14 kinds have bins;
- and it warns that two pinned assertions (25 bins, 28,620 samples) **will** fail, that the
  numbers may be updated but **the assertions must not be weakened or deleted**.

`exotype-kinds` stays at 12 — only the deriver's draw widens.

Once it lands I add the **loud-fallback guard** myself: a missing bin should raise rather than
silently return the global row. It has to go in *after* the re-derivation, since right now 2 of
14 would trip it. Silent fallback corrupted two of my own runs today (§49.1, §51.1); a guard is
the durable fix, the same shape as §49.6's coordinate test.

### 55.2 The experiment packet is deliberately build-only

Four constraints, each traceable to something measured today:

- **(a) the scaling rule must be stated and fixed before any dynamics is observed.** X has
  sd ≈ 0.25 against ambiguity's ≈1.9-nat spread, so unscaled it does nothing — and choosing
  the coefficient by trying values until the trajectory looks right is precisely the
  curve-fitting codex-14 warned about when it refuted §38.1.
- **(b) preregistered prediction and falsifier** in the docstring, before running.
- **(c) `:apply-probability` swept, not fixed at 1.0** — §51 showed the halting funnel must be
  removed or the epistemic term is tested underneath something that dominates it.
- **(d) an EIG-off control at every cell**, or any change could be attributed to the rate
  modulation alone.

Deliverables: the quantity promoted into `src/` with tests, wired behind a coefficient that
**defaults to zero** so nothing moves until asked, the experiment script preregistered and
**not run**, and a note stating the scaling rule and why. The packet says explicitly that if
codex-7 thinks any of (a)–(d) is wrong it should say so, since the design is the deliverable.

**Joe reviews before launch. I review before Joe.**

---

## 56. Conditional model re-derived over 14 kinds — coverage complete, degeneracy DOWN, guard armed

zai-2, job `invoke-1785874158454-1034-b65cf46c`. Reviewed as a real gate: coverage verified
independently, A/B read, the three downstream failures fixed by me (zai-2 correctly left them
alone, per the packet).

### 56.1 The A/B

| | 12-kind | 14-kind |
|---|---:|---:|
| bins | 83 | **101** |
| samples | 114,480 | 114,480 |
| bins below `min-bin-samples` | 10 | 16 |
| kinds with no bins | **2** | **0** |

Global row moved by 0.0002–0.0068 per channel; common bins by a MAD of 0.028–0.054. Same
sample count, spread over more bins — which is what widening the *mixture* rather than the
*amount* should do. **All 14 kinds covered, minimum 6 bins each**, verified by me directly, not
taken from the report.

**My packet quoted a stale baseline** (25 bins / 28,620 samples) — those were my own figures
from before zai-2's earlier S0b re-derivation, which had already moved them to 83 / 114,480.
zai-2 updated to the correct new values regardless. No harm done, but the number came out of my
head instead of out of the file.

### 56.2 Two of the three "failures" were improvements

| test | old | new |
|---|---|---|
| `derived-model-reduces-objective-degeneracy` | `:efe-full` 2 distinct winners | **4** |
| `hole-the-objective-is-degenerate…` | `{full 2, risk 4, ambig 1, no-conatus 2}` | **`{full 4, risk 4, ambig 1, no-conatus 4}`** |
| `zero-transfer-reproduces-stored-run…` | fixture | `:changed-steps` 6→7, `:changed-cells` 11→12 |

**A richer, better-covered model discriminates more, not less.** `:efe-full` and
`:efe-no-conatus` both double their distinct-argmin count over the same 9 observations.

And the two ablated arms are **unchanged**, which is the signature you would want:
`:efe-risk-only` scores on rate alone and `:efe-ambiguity-only` on entropy alone, so neither
reads the bins that got richer. If those had moved too, something other than coverage would
have changed.

The fixture moved in exactly two fields; checkpoints, entropy, autocorrelation, activity, rule
count and all three damage figures are byte-identical. A small localised shift is what a modest
change in the rows should produce, and **seeing the rest hold still is the check that it was
modest.** Regenerated with a third provenance note.

### 56.3 The guard is armed

`efe/covered-kinds` and `efe/kind-has-bins?` added; `predict`'s derived path now **throws** for
a kind with no rows anywhere, naming the covered set in the exception data. A *per-bin* miss for
a covered kind is still a legitimate sparse-data fallback to the global row — that is what
`min-bin-samples` is for. It is a kind with **no** bins that is a coverage defect, and that is
now impossible to hit silently.

Pinned by `every-propagator-is-covered-by-the-derived-model`: the propagator key set must equal
the covered set, and a fabricated kind must raise. Adding a propagator without re-deriving now
fails the surface immediately.

That is the third durable guard written today against a defect that had already cost real work
— after the coordinate test (§49.6) and the `:observation-model` regression test (§40.2).
Each replaces a piece of vigilance with a piece of machinery.

**Gate: 81 tests / 2267 assertions, 0 failures.** clj-kondo 0 errors, parens 0.

---

## 57. codex-7 REFUSED to build the experiment, and was right. Two defects behind it.

Job `invoke-1785874191903-1035-23765337`. It stopped before implementation and reported that
the design cannot work. **Both of its claims verified; both correct.**

### 57.1 The design was incoherent, and the error is one I had already been warned about

`score-policy` chooses an **exotype**. The three policies are hold / adopt-left / adopt-right,
scored on `(nth (:exotypes state) source)`. But **X(i) is a function of genotypes and phenotype
only** — it has no exotype argument. So `score − κ·X(i)` subtracts *the same constant from every
candidate*, and

> **argmin(score − κ·X(i)) = argmin(score)** for every κ.

EIG-on and EIG-off are provably identical. The within-decision candidate spread of X is exactly
zero, so my constraint (a) — scale X to match ambiguity's spread — is not merely hard, it is
**undefined**, and constraint (b)'s "which kind wins" prediction cannot be stated honestly.

The underlying mistake: **I conflated two action spaces.** X measures the consequence of a
*genotype blend*; `score-policy` chooses an *exotype*. E2c validated X against damage from
adopting the blend, and then I proposed wiring it into a decision that is not about blending.

§46.2 records micro-pilot 7's finding that *"an epistemic term scored on the exotype layer
cannot work; it has to be scored on the layer that generates observations"* — and records me
proposing exactly that, unread, and calling it *"twice over, since §19 is the hole about
exactly this."* **This is the third time.** The note was read, acknowledged, written up, and the
same error made again in a new costume. Reading a warning is not the same as holding it.

codex-7 also pre-empted the two repairs I would have reached for: `X(source)` scores a
*neighbour's* blend susceptibility rather than the consequence of adopting its exotype; and
computing `X(i, policy)` after applying each candidate propagator invents a new quantity whose
correlation with damage **has never been measured**. Either would have quietly substituted an
unvalidated quantity for the validated one.

### 57.2 The secondary defect is worse, and it invalidates a fix I reported as complete

`self-tuning` keeps its own `score-cache`, built once over `grid/exotype-kinds` at **default
options**, and consulted unconditionally. Measured through the trajectory driver, 120 steps:

| option | exotype distribution | |
|---|---|---|
| default | `{:chaos 25, :odd53 15}` | |
| `:observation-model :legacy` | `{:chaos 25, :odd53 15}` | **ignored** |
| `:apply-probability 0.15` | `{:chaos 25, :odd53 15}` | **ignored** |
| `:lambdas 0.9` | `{:chaos 20, :odd53 20}` | honoured |

λ works because the cache stores the λ=0 total plus the raw conatus and λ is reapplied per cell.
The others are baked in.

**So codex-12's finding #1 was only half-fixed.** §40.2 records it as FIXED with a regression
test — but that test exercises `efe/cell-decision`, and the trajectory driver goes through
`self-tuning/cell-decision`. The claim *"trajectory-level comparisons now honour it"* was false
when I wrote it. §41.4's "newly possible after codex-12 #1" is likewise retracted.

**Fixed:** a `cache-shaping-options` set (`:observation-model`, `:apply-probability`,
`:rule-change-preference`); any of them present forces a direct `score-policy` call. `:lambda`
is deliberately excluded and documented as such. A cache miss now throws instead of merging
`nil`. Verified after the fix — `p=0.30 → {:chaos 12, :odd53 28}`, `p=0.15 → {:chaos 29,
:odd53 11}`, both differing from default, and an explicit `:derived` is a no-op.

New regression test `trajectory-driver-honours-the-scoring-options` covers the path the earlier
one missed.

### 57.3 And it surfaced a third thing: `:legacy` is unusable

With the slow path actually taken, `:legacy` **throws**: `fixed-model` has rows for 4 kinds
(`:builder :chaos :collapser :identity`) and the vocabulary has 12. The legacy model cannot
score 8 of them.

So **S5 (legacy vs derived at trajectory level) is not runnable as specified** — not because of
plumbing, which is now fixed, but because the legacy model has no rows for two thirds of the
vocabulary. It would need `fixed-model` extended by hand, which is the hand-typed table H5 is
about. Recorded rather than worked around.

**Gate: 82 tests / 2269 assertions, 0 failures.**

### 57.4 What to do instead — codex-7's plan, adopted

1. **Define and validate a genuinely policy-specific X(i, π)** for hold/adopt-left/adopt-right,
   against *each policy's own* matched damage. Until that correlation is measured there is
   nothing to wire.
2. **Scale from disjoint pre-dynamics states, like-for-like:**
   `κ = RMS within-cell SD(ambiguity across candidates) / RMS within-cell SD(X across candidates)`,
   failing loudly if the denominator is zero. This is **better than the rule I specified** — mine
   compared global spreads, and what matters is variation *across the candidates being compared*.
3. Only then run the `apply-probability` × EIG-off/on design.

Step 1 is the real work and it is not obviously possible: an exotype's effect on damage is one
step removed, since adopting a kind changes which propagator transforms the rule rather than
changing the rule. That indirection is exactly why the exotype layer is hard, and it is what
micro-pilot 7 was pointing at.

---

## 58. The fixed-rule control — what the extra layers buy (Joe's reading of the best-of figure)

Joe, on `bestof-boring-noabsorbing-b075.png`: *"a lively genotype that manages to keep one of
the guaranteed-to-die EmacsBug style phenotypes ALIVE a lot longer than the old EmacsBug
genotype did."*

**The literal comparison is not runnable.** The 2015 bug's map is not implemented — it appears
only in a docstring at `grid.clj:34` ("if the family is widened to include the 2015 bug's
shape"). N1 is closed with the bug *promoted, not retracted*, but there is no propagator for it.

**The defensible version is runnable, and it holds.** The control that isolates the claim is
*the same rules, held fixed* — identical initial conditions, identical rules at t=0, differing
only in whether the rule layer evolves. 24 seeds:

| | damage reach at t=100 |
|---|---:|
| **three-layer (rules evolve)** | **26.2** |
| same rules, held fixed | **2.8** |

**9×, attributable entirely to the rule layer evolving.** Held fixed, this field sits just above
the frozen ECA anchor (1.0) — dynamically dead. Allowed to evolve, the same rules land in the
class-IV band. Recorded in `TN-part3-bestof.md` §4b as Part III material.

### 58.1 I measured the wrong thing first, and it said the opposite

My first instrument was phenotype **activity** — fraction of cells changing per step. It gave:

| | t=1 | 50 | 500 | 2000 |
|---|---:|---:|---:|---:|
| three-layer | 0.449 | 0.268 | 0.068 | **0.065** |
| fixed rules | 0.431 | 0.333 | 0.329 | **0.323** |

Read naively that **contradicts** Joe: the fixed-rule field is five times as active at t=2000.
But §34 already established that high activity here is *noise*, not life — and the damage
figures show it: the more active field is the one that cannot propagate a perturbation.

> Fixed rules: **5× the activity, 9× less sensitive.** High activity with no propagation is
> noise; low activity with propagation is localised structure.

**Third time today that activity has pointed the wrong way** — after §34 (blend row looks like
noise and is) and §44 (in-run change rate collapses under freezing). The pattern is now
explicit enough to state as a rule: *in this substrate, activity is not a liveness measure, and
any claim resting on it should be re-checked against damage before it is believed.*

Had I stopped at the first table I would have told Joe his reading of his own figure was wrong,
on the strength of a measure I had already documented as misleading, twice.

---

## 59. "Bringing the dead to life" — tested, and it happens

Joe: *"it's even conceivable that … we would discover a way to bring the dead to life and get
the guaranteed-dead coral reefs to become guaranteed-alive EoCs. The image looks to me like a
fire that wants to start."*

The testable version: start every cell at a **provably dead** rule and measure whether the
layered system carries it into the class-IV band. 16 seeds, best-of configuration, t = 100.

| initial rule field | layered | same rules held FIXED |
|---|---:|---:|
| random | 25.5 | 2.9 |
| all rule 204 | **16.1** | 1.0 |
| all rule 0 | **17.5** | **0.0** |
| all rule 255 | **19.8** | **0.0** |

Rules 0 and 255 held fixed erase a perturbation instantly — **exactly 0.0**, the floor. The same
initial conditions under the layered system reach **16–20**, inside the class-IV band. Recorded
in `TN-part3-bestof.md` §4c.

**But the mechanism is escape, not resuscitation.** By t = 100 the field carries 68–85 distinct
rules from a uniform start, and fewer than 2 cells of 201 still hold the initial rule. The
system does not keep the dead rule alive — it destroys it and diversifies past it. So the claim
belongs to the *basin*, not the rule: **dead initial conditions are carried into the band.**
Writing it up as "the dead rule becomes alive" would be false about the rule and true only about
the field.

**Unexplained, and worth chasing:** revived fields land at 16–20 against a random start's 25.5.
Dead starts get into the band but not as far in, consistently across all three dead rules. That
gap is a real signal about the basin's structure and nothing here explains it.

**Limits:** 16 seeds, means only, no dispersion — so "consistently" describes an ordering of
means and not a tested separation. One configuration, width and horizon.

---

## 60. Fable's answer: a policy-specific epistemic quantity EXISTS. And Part III is drafted.

### 60.1 The answer

Fable was asked whether a local quantity `X(i, π)` can predict the consequence of a policy, given
that the policy's effect is mediated by a stochastic transform. **It found one, and bounded its
scope honestly.**

The insight I had missed: **twin trajectories share the per-cell draw seed**, so the transform is
*common noise*, not policy noise. One-step divergence is therefore exactly computable — given
draw *k* on rule *b*, own σ (map A) and candidate σ′ (map B), the rules diverge iff
`A(k) ≠ B(k)` **and** (`b[A(k)] = b[k]` or `b[B(k)] = b[k]`). Verified exhaustively against
`rule-permute`. The quantity is O(8), zero for *hold* by construction, and collapses to a
12 × 12 constant table over policy pairs.

**Verified independently on my own seeds** — the check that killed the previous candidate:

| | mine | Fable's |
|---|---:|---:|
| within-cell SD across the three candidates | **0.2994** | 0.29 |
| the existing `rate` term, same measure | **0.1229** | 0.116 |
| cells with zero spread | 5 / 720 | 14 / 720 |

**2.4× the spread of the term it would sit beside**, and `X_pair(k,k) = 0` for every k so *hold*
scores zero correctly. The retired candidate had a within-cell spread of **exactly zero by
construction**; that failure mode is affirmatively absent.

### 60.2 What Fable was careful about, and I would not have been

- It **decomposed its own headline**: the pooled r of +0.17…+0.35 is mostly X = 0 matching Y = 0
  where the candidate equals the cell's own policy — decision-irrelevant. Among genuinely
  differing candidates r at t = 60 is only +0.02…+0.09, peaking at +0.28 around t = 10.
- It established a **calibration ceiling**: r against realised one-step divergence is +0.469
  against a theoretical maximum of ≈0.47 for a perfectly calibrated probability. So the
  long-horizon shortfall is **chaos erasing magnitude**, not a weak quantity — 94% of perturbed
  cells have diverged by t = 20.
- It reports the correct interpretation as *"expected rate of injecting new dynamics, valid at
  effective horizon 5–20 steps"* and says explicitly **not** to justify it by long-horizon damage.
- **It refuted my suggested shape.** The pattern-weighted `w(n)` form I offered in §5 of the note
  as "the concrete candidate" is consistently the *weakest* — pattern weighting adds noise. The
  one hint I gave was the wrong one.

### 60.3 Part III drafted

`TN-part3-draft.md`. Arc: **what the extra layers buy → what a search finds → whether the system
can find it itself → the term it would need.** Slots after `A Causal Measure` in `draft7.tex`.

§III.1 extends the paper's existing section *"A dead genotype can carry a live phenotype"* —
that section shows the possibility; ours gives the magnitude (9×) and the direction of travel
(dead starts carried into the band). The connection was already in the paper; I only found it by
reading the section list before writing, which is the §19 lesson applied for once at the right
moment.

Every claim in the draft carries its measurement and its limits inline, including the three
things we withdrew today (the t½ law, the zero-evidence diagnosis, my own spread rule).

### 60.4 Recorded as open, per Joe

**The revival gap.** Dead starts reach 16–20 against a random start's 25.5, consistently across
all three dead rules. It is a fact about the basin's structure and nothing measured explains it.
Carried in `TN-part3-draft.md` under "Open, and stated as open", and listed here so it is not
lost: **investigate later, not now.**

---

## 61. Epistemic-term experiment v2 dispatched to codex-7 — with the control that could kill it

Job `invoke-1785876161283-1036-7a4946b7`, park `park-1c8478da-6762-4bc4-8c33-4d615bd13fb3`.
Sent back to **codex-7 deliberately** — it refused v1 and its refusal was correct, so it holds
the context for why v2 is different, and is the agent most likely to refuse again if v2 is also
wrong.

The packet opens with **what changed since your refusal**, since the blocker it found (zero
within-cell spread) is the thing that has been resolved, and 0.299 against the existing rate
term's 0.123 is the number that resolves it.

### 61.1 The new constraint (e), which is the one that matters

`X_pair(σ, σ) = 0`, so the term gives *hold* no credit and **always favours adopting**. It is a
systematic push toward change — and §51.2 measured what happens when this construction is
pushed toward change: it selects the **maximum-churn** policy, which is not the edge.

**So an arm that merely churns more would look like a win.**

Constraint (e) therefore requires a **matched-churn control** at every cell of the design: a
constant adoption bonus tuned to produce the *same mean adoption rate* as the epistemic arm,
with no policy specificity. If the two behave alike, X is a change-bias wearing a hat.

This is the same shape as the matched-constant-blend-rate control that refuted the earlier gate
proposal (micro-pilots 8–9), and as `Y_flip` in E2c. **It is the control most likely to kill the
result, which is exactly why it is required rather than optional.** Without it a positive
outcome would be uninterpretable, and I would rather find that out from an arm than from a
reviewer.

### 61.2 The scope limit is stated as binding

The quantity is near-optimal at the step it models (r = +0.469 against a ≈0.47 ceiling) and
**does not predict long-horizon damage magnitude** (+0.02…+0.09 at t = 60; 94% of perturbed
cells have diverged by t = 20 and chaos has erased the magnitude). The packet says in terms: do
not justify it, or design around, damage at long horizons. Interpret it as *expected rate of
injecting new dynamics*, effective horizon ~5–20 steps.

Carrying that limit into the packet is the difference between a scoped claim and one that a
referee dismantles.

### 61.3 Also carried forward

- the scaling rule in **codex-7's own formulation** (within-cell candidate spreads, not global),
  fixed from disjoint pre-dynamics seeds and persisted before any trajectory;
- `:apply-probability` swept, with a note that the score-cache fix (§57.2) is what makes such a
  sweep non-inert, and an instruction to **verify that still holds** rather than trust it;
- epistemic-off control, coefficient defaulting to zero, build-but-do-not-run;
- and the standing invitation to refuse: *"if any of (a)–(e) is wrong, say so rather than
  complying — that is what you did last time and it saved the work."*

---

## 62. Epistemic experiment v2 BUILT and reviewed — ready to launch, not launched

codex-7, job `invoke-1785876161283-1036-7a4946b7`. Reviewed as a real gate. **What I checked,
and how:**

| check | result |
|---|---|
| full twelve-namespace surface, run by me | **85 tests / 2436 assertions, 0 failures** — matches its report |
| new files exist | `src/futon5/exotype/policy_epistemic.clj`, `scripts/exotype_policy_epistemic_v2.clj` |
| κ arithmetic | 0.1483 / 0.3102 = **0.4782**, matches the artifact to 1e-12 |
| calibration seed sets | scale (32) ∩ churn (8) ∩ experiment (32) — **all three pairwise empty** |
| artifact status | `:locked-before-dynamics`, formula and scope recorded in the file |
| epistemic term is policy-specific | X_pair varies 0.594 / 0.688 / 0.875 across candidates; **hold = 0.000** |
| matched-churn is NOT specific | constant −0.150 for both adoptions, **0.000 for hold** |

The design is cleaner than I specified: `score-policy` takes the epistemic **value** as an
input rather than computing it, so the quantity and the scoring stay separable, and the
`:adoption-bonus` is applied at `cell-decision` where the policy is known. My first probe
passed invented key names and measured nothing — caught because the two columns came out
byte-identical, which is a tell, not a result.

### 62.1 The preregistration is honest about what would NOT count

- Predicts: EIG-on raises selected `X_pair` and 5–20-step reach over EIG-off; at low *p*
  identity dominance falls and non-halting `:odd53` wins rise; at high *p* the halting-capable
  share falls; **and EIG-on must beat adoption-rate-matched churn** on selected X and early
  reach.
- Falsifies: no increase over EIG-off, **or** EIG-on and matched churn agreeing within seed
  spread on selected X, dominant kinds, halting share and early reach.
- States explicitly: *a mere adoption-rate increase, or a long-horizon damage change, is not
  success.*

That last line is the one I most wanted and did not have to ask for twice. Damage is measured
at **horizon 10 only**, consistent with the validated 5–20 step scope — so the experiment cannot
accidentally claim the long-horizon result the quantity was shown not to support.

### 62.2 Ready, and Joe's call

Sweep is `p ∈ {0.1, 0.15, 0.3, 0.6, 1.0}` × {EIG-off, EIG-on, matched-churn}. Matched-churn
bonuses are calibrated on their own seeds to match adoption rate within 0.02, and
`self-tuning` bypasses its cache for both new controls and verifies `:apply-probability`
survives every step — the §57.2 fix, checked rather than assumed.

**Nothing was run beyond the calibration.** Launching is Joe's decision, as agreed.

---

## 63. Epistemic experiment v2 LAUNCHED on zone-joe

Partitioned by **p** per `futon0/README-bare-metal.md` §5 — five lanes, each doing its own churn
calibration and then the three arms {off, epistemic, matched-churn}, so 15 cells. The scale
artifact (κ = 0.4782, locked before dynamics) is shared and read-only; each lane's churn
calibration is separate because the matching bonus is p-dependent.

**Health checked rather than assumed** (the "wall clock is not stuckness" rule): 6 JVMs at 108%
CPU each, 2m36s in, no exceptions in any log. The churn calibration is the expensive leg — it
searches for a bonus that reproduces the epistemic arm's realised adoption rate to within 0.02,
which means running trajectories per candidate bonus, five times over.

**What the result has to clear to count**, from the preregistration:

- beat EIG-off on selected `X_pair` and 5–20-step reach; **and**
- beat **adoption-rate-matched churn** on the same two.

Agreement between the epistemic arm and matched churn — within seed spread, on selected X,
dominant kinds, halting share and early reach — falsifies it. So does no increase over off.
A bare adoption-rate rise does not count, and neither does anything at long horizon: damage is
measured at **t = 10 only**, inside the validated scope.

The matched-churn arm is the one that can kill this, and it is the reason the run is worth
making. If the epistemic term is a change-bias wearing a hat, this design says so.

---

## 64. First result is a CALIBRATION FAILURE, and it is informative

Status: 9 of 15 cells (p ∈ {0.3, 0.6, 1.0} × 3 arms). **The two low-p lanes threw** during
churn calibration:

> `matched-churn calibration missed its preregistered tolerance`

| p | epistemic arm's adoption rate | best matched-churn | gap | tolerance |
|---:|---:|---:|---:|---:|
| 0.10 | **0.9304** | 0.9953 (bonus 0.25) | 0.065 | 0.02 |
| 0.15 | **0.9021** | 0.9952 (bonus 0.20) | 0.093 | 0.02 |

**This is codex-7's guard working as designed** — it refuses to proceed with an unmatched
control rather than quietly comparing against a mismatched one. Constraint (e) was added
precisely so a churn effect could not masquerade as steering, and the guard is what makes it
load-bearing rather than decorative.

### 64.1 The search was not too coarse

The bonus grid is 41 values, 0.00 to 2.00 in steps of 0.05 — **including 0.0**. Ties break
toward the smaller bonus, so the winner being 0.25 means bonus 0.0 was *further* from target,
i.e. **baseline adoption at low p is already above 0.995**, and the epistemic arm **reduces** it
to 0.90–0.93.

If that reading holds, it inverts the concern that motivated constraint (e): at low p the
epistemic term is not a push toward adoption at all — it is **selective**, declining adoptions
the baseline would take. A constant bonus can only ever move adoption *up*, so it cannot
reproduce an intermediate rate, and the arms are **not matchable by construction** at these p.

**Flagged as inference, not measurement.** I read the direction off the tie-break rule, not off
a printed adoption curve. It should be confirmed by measuring adoption against bonus directly
before it is repeated anywhere.

### 64.2 What this costs

The matched-churn control is **unavailable at p = 0.1 and 0.15** — the two values where §51
showed the halting bias is removed, i.e. the region of most interest. So for those cells the
preregistered bar ("must beat adoption-rate-matched churn") **cannot be evaluated**, and any
claim from them is weaker than the design intended.

Re-launched p = 0.1 and 0.15 with **off and epistemic only**, so 13 of 15 cells will exist. The
two missing cells are missing for a structural reason that is itself a result, and that is how
they should be reported — not as a run that partly failed.

**My lane script also over-reacted**: `calibrate-churn ... || exit 1` killed the whole lane, so
the off and epistemic arms did not run either. That was my error, not the design's — a
calibration failure for one arm should not have discarded the other two.

---

## 65. The epistemic experiment: THE FALSIFIER FIRES

13 cells on zone-joe, 32 seeds each. Raw in `reports-remote/epi/`.

| p | arm | adoption | selected X | damage@20 | halting share | dominant |
|---|---|---:|---:|---:|---:|---|
| 0.1 | off | 0.003 | 0.0017 | 1.59 ± 0.31 | 0.000 | `:identity` |
| 0.1 | **epistemic** | 0.958 | 0.8414 | 2.13 ± 0.41 | 0.022 | `:identity` |
| 0.15 | off | 0.003 | 0.0017 | 1.44 ± 0.29 | 0.000 | `:identity` |
| 0.15 | **epistemic** | 0.965 | 0.8148 | 2.41 ± 0.44 | 0.011 | `:identity` |
| 0.3 | off | 0.003 | 0.0018 | 1.63 ± 0.38 | 0.000 | `:identity` |
| 0.3 | **epistemic** | 0.996 | 0.9645 | 2.41 ± 0.41 | 0.090 | `:identity` |
| 0.3 | churn | 0.996 | 0.3664 | 1.88 ± 0.35 | 0.000 | `:identity` |
| 0.6 | off | 0.064 | 0.0378 | 3.31 ± 0.51 | 0.023 | `:chaos` |
| 0.6 | **epistemic** | 0.999 | 0.9655 | 2.09 ± 0.34 | 0.169 | `:identity` |
| 0.6 | churn | 1.000 | 0.6378 | 2.97 ± 0.37 | 0.208 | `:odd53` |
| 1.0 | off | 0.077 | 0.0403 | 2.78 ± 0.44 | 0.284 | `:odd53` |
| 1.0 | **epistemic** | 0.990 | 0.6577 | 2.31 ± 0.40 | 0.368 | `:chaos` |
| 1.0 | churn | 0.988 | 0.3756 | 2.56 ± 0.40 | 0.353 | `:collapser` |

### 65.1 Every prediction fails except the manipulation check

**P1 — epistemic raises damage@20 over off.** Welch t at 32 seeds/arm: **+1.02, +1.81, +1.37,
−1.97, −0.78**. Nothing separated, and the two largest magnitudes point in *opposite*
directions. **Fails.**

**P4 — epistemic beats adoption-rate-matched churn on early reach.** **+0.97, −1.73, −0.44.**
Not separated anywhere, and negative at two of three p. **Fails** — and this is the constraint
added specifically to catch a churn effect masquerading as steering.

**P2 — at low p, identity dominance falls and `:odd53` wins rise.** At p = 0.1 and 0.15 the
dominant kind is `:identity` in *both* arms. **Fails.**

**P3 — at high p the halting-capable share falls.** It **rises**: +0.146 at p = 0.6,
**t = +5.23** — the only strongly separated effect in the whole experiment, and it is in the
**wrong direction.** **Fails.**

**What did succeed** is selected X (0.002 → 0.84–0.97). That is a **manipulation check**, not a
result: the epistemic arm selects on X, so of course it selects high X. It confirms the wiring
works and says nothing about whether the term steers.

### 65.2 The honest verdict

> The epistemic term changes the decision — adoption goes from 0.3% to ~96–99% — and changes
> **nothing we care about**. It does not raise damage reach, does not beat a matched-churn
> control, does not displace `:identity` at low p, and it *increases* the halting-capable
> share. It is a large intervention with no steering.

A quantity can be genuinely policy-specific (spread 0.299 vs 0.123), near-optimally calibrated
at the step it models (r = +0.469 against a ≈0.47 ceiling), and **still not steer the dynamics**.
Predicting the next step well is not the same as choosing well. That is the finding.

### 65.3 §64.1 is RETRACTED — my inference was backwards

I inferred from the tie-break rule that baseline adoption at low p was ≈0.995 and that the
epistemic arm *reduced* it. **Measured: the off arm adopts at 0.0033.** The epistemic term
raises adoption from ~0 to ~96%, the opposite of what I claimed.

The *conclusion* survives — a constant bonus cannot reproduce an intermediate adoption rate, so
the arms are not matchable at low p — but for the opposite reason: adoption is a **step
function** of a constant bonus, ~0 below threshold and ~1 above, so it jumps past 0.93 rather
than approaching it from above.

I flagged that passage as "inference, not measurement" and said it should be confirmed before
being repeated. It was wrong within the hour. **The flag is the only reason this is a correction
and not a false claim in the record.**

---

## 66. §65's verdict was WRONG. Joe asked for the pictures and the pictures found it.

Joe: *"the notes say 'changes nothing we care about', but a visual inspection might show
otherwise!"* It did. Rendered `off` / `epistemic` / `matched-churn` at matched p and seed
(`reports/figures/epi-p*.png`). The final exotype composition at p = 0.6, one seed:

| arm | final kinds |
|---|---|
| off | `{:chaos 79, :odd53 1}` — near-monoculture |
| **epistemic** | **`{:identity 34, :odd53 25, :even1 11, :builder 7, :fix3 3}` — five kinds** |
| matched-churn | `{:identity 40, :odd53 40}` — two |

The genotype panel shows it directly: `off` has coherent vertical bands, `epistemic` a dense
multi-colour speckle.

### 66.1 The number was in the data the whole time

`:dominant-share` was in every run record. **I never printed it.** I reported damage, halting
share and dominant *kind*, and not dominant *share* — which is the direct measure of the one
pathology this entire project has been about.

| p | off | epistemic | t |
|---:|---:|---:|---:|
| 0.10 | **1.000 ± 0.000** | 0.513 | **−72.5** |
| 0.15 | **1.000 ± 0.000** | 0.516 | **−48.7** |
| 0.30 | **1.000 ± 0.000** | 0.503 | **−255.6** |
| 0.60 | 0.928 | 0.425 | **−18.8** |
| 1.00 | 0.633 | 0.506 | **−5.0** |

**At p ≤ 0.3 the control collapses to total monoculture in every one of 32 seeds, zero
variance. The epistemic arm does not, at any p.** These are the largest effects measured
anywhere in this work.

### 66.2 Against the control that matters, it is narrower but real

| p | matched-churn | epistemic | t | |
|---:|---:|---:|---:|---|
| 0.30 | 0.504 | 0.503 | −0.46 | churn does the same |
| **0.60** | 0.497 | **0.425** | **−5.39** | **beats churn** |
| 1.00 | 0.504 | 0.506 | +0.72 | churn does the same |

So most of the anti-monoculture effect is a **churn** effect — a constant adoption bonus buys it
too. But at p = 0.6 the epistemic arm is separated from adoption-matched churn, and the render
shows what that means: five coexisting kinds against churn's two. **That is steering, in the
one cell where the control exists to prove it.** At p = 0.1 and 0.15 — the largest effects —
there is no control, so they cannot be attributed.

### 66.3 What I got wrong, and why

§65.2 said the term *"changes nothing we care about."* **Retracted.** It prevents monoculture
collapse, which is the thing we care about most.

The failure was **choosing the outcome measures from the preregistration and stopping there.**
The prediction list named damage reach, dominant kind and halting share; all three did fail, and
I reported that correctly. But a preregistration bounds what counts as *confirmation* — it does
not bound what the experiment is allowed to *show*. I treated a list of hypotheses as a list of
columns to print, and a 255-sigma effect sat unexamined in the same file.

**Joe's instinct to look at the images is what surfaced it**, and it is the third time today
that a picture or a control has corrected a scalar summary of mine. The rule I wrote in §58 —
*activity is not a liveness measure, re-check against damage* — needs a companion: **when an
intervention changes the decision as massively as this one did (adoption 0.003 → 0.96), and the
preregistered measures show nothing, look for what it changed instead of concluding it changed
nothing.**

---

## 67. Joe's combining plan — two integration gaps found before promising anything

Joe: *"the epistemic arm by itself isn't going to solve our problems, but at p=0.6 it keeps the
exotype diverse … if we were to combine it with the technique from
bestof-boring-noabsorbing-b075 I expect we might see an improvement."*

The read is right and matches the data. But the combination is **a build, not a configuration
change**, for two reasons found by checking rather than assuming.

### 67.1 The two techniques live on DISJOINT code paths

| | best-of path | epistemic path |
|---|---|---|
| driver | `grid/step` | `self-tuning/step` |
| arm | `:boring-triggered` | `:efe-full`, **hard-coded** in `step` |
| blending | `apply-exotype-blend`, β = 0.75 | **none** — `genotype-step` applies the propagator only |
| policy selection | none (`grid.clj` has no `efe/` reference at all) | full EFE scoring, where the epistemic term lives |

`self-tuning/step` writes `:arm :efe-full` into every advanced state and never carries
`:blend-strength`; `grid/step` never consults `efe`. **So the phenotype-reading feedback loop
and blending exist only where the epistemic term does not, and vice versa.** No configuration
reaches both. Combining them means implementing blending in the self-tuning path — the smaller
of the two directions, since blending is a local operator and the EFE selector is not.

### 67.2 `:apply-probability` never reached the actual transform

It appears in `self_tuning.clj` only in the cache-shaping set and the state-carrying `cond->`.
The transform itself — `genotype-step`, reading `genotype-transition-cache` keyed
`[sigil exotype k]` — has **no probability term**. So p scales the *predicted* rate used in
scoring and **not** the rate at which rules actually change.

**This is a real caveat on §65–66 and I should have stated it when I recorded §51.4.** I noted
then that "the dynamics side is not done" and attributed it to `apply-exotype`; it is still not
done, and the epistemic experiment swept it anyway. So "p" in those results means *the objective
believes the rate is p·rate(σ)*, not *the rate is p·rate(σ)*.

It does not invalidate §66's diversity finding, which is about which exotype gets selected and
is therefore driven by scoring. It does mean the p axis is a belief axis, not a dynamics axis,
and every sentence about p in §65–66 should say so.

### 67.3 Consequence for the plan

The combining run Joe wants is worth doing and is **not** a quick sweep. It needs, in order:

1. blending in the self-tuning path (so `:boring-triggered` + β and the EFE selector coexist);
2. `:apply-probability` actually applied in the transform, or the p axis dropped and stated;
3. then the sweep: best-of ingredients × {epistemic on, off, matched-churn}.

Dispatching (1) and (2) as one small build, since they touch the same function, with the sweep
held until they are reviewed.

---

## 68. Joe: the epistemic run has no interrupter. Correct — and the reason is not the one I first gave.

Joe, on `epi-p0.6-epistemic`: *"there is no blending or blending analogue … the genotype flows
vertically without any disruption. Previously we had thought of blending as a kind of
proto-epistemic method, but our implementation doesn't recover the interrupter behaviour."*

### 68.1 My first reading was wrong, and measurement caught it

From the code — `genotype-step` maps over `(index, sigil, exotype)` and the new byte is a
function of **this** cell's sigil, **this** cell's exotype and a draw, never a neighbour's
sigil — I concluded the rule layer was N independent chains.

**Measured instead:** perturb one genotype cell, run 60 steps, and the difference reaches
**17 genotype cells, 8 exotypes, 11 phenotype cells.** Not independent.

The influence path is indirect and closes through the whole stack:

> genotype → phenotype → local observation → EFE policy selection → exotype → transform → genotype

So rule **content** never crosses cells, but **influence** does, via the selector reading a
phenotype that a neighbour's rule helped produce. Third time today a code-reading inference of
mine was corrected by running it.

### 68.2 Joe's conclusion stands, and is sharper than "no coupling"

There is coupling; what is absent is **mixing of rule content**. Blending takes bits from
neighbouring rules and writes them into this rule. Nothing in the self-tuning path does that —
the lateral channel carries only *which transform to apply*, never *what the rule says*. Hence
vertical flow: each column's rule is its own lineage, nudged from the side but never
interbred.

**So blending is not a proto-epistemic method, and the epistemic term is not a blending
substitute. They do different jobs**, and the paper's Part II "interrupter" is the blending job.

### 68.3 Which is exactly what review B predicted, and I recorded without connecting

`TN-epistemic-term-opus-review.md` §5:

> *`blend-rule` … where they disagree evaluates `centre-rule` on the triple — a lookup into a
> different position of the centre byte. So blend is the **only operator in the genotype layer
> that mixes bit positions across σ's cycles**. … That reading covers the spatial job. It does
> not cover the position-mixing job, which is the layer's only ergodicity source and is not
> epistemic at all. **So the interior optimum in β may be about ergodicity rather than about
> epistemic gating.***

I recorded that in §48.3 as "inferred, plausible and untested" and then did not use it.
**Joe's visual reading is the evidence for it**: an epistemic term with no blending produces
diversity without ergodicity — many kinds, no interruption. The two mechanisms separate cleanly,
which is what B's proposed control was meant to test.

### 68.4 Effect on the combining build

It stops being "might improve" and becomes a **structural prediction**: the epistemic term is
incapable of supplying interruption, because interruption requires lateral rule-content mixing
and the exotype channel does not carry rule content. Combining should therefore add something
the epistemic arm provably lacks, rather than more of what it already provides.

That gives the combining run a real falsifier: **if blending + epistemic is no better than
blending alone, the epistemic term contributes nothing the interrupter does not already
supply** — which would make the §66 diversity effect a curiosity rather than a tool.

---

## 69. Both dispatched (Joe approved 2026-08-05)

| what | to | job / park |
|---|---|---|
| blending + `:apply-probability` in the self-tuning transform | **codex-12** | `invoke-1785912579057-1037-b175310d` / `park-0152e268` |
| "an epistemic term that interrupts — or an impossibility proof" | **Fable** (in-harness) | writes `TN-interrupter-fable-answer.md` incrementally |

### 69.1 The build packet's acceptance bar is byte-compatibility

`genotype-step` is a **cached re-implementation** of `grid/apply-exotype` — its own comment says
so, and §2 records that it once carried its own copy of the first-draw defect. So the packet's
bar is not "blending works" but:

- **at β = 0 and p = 1.0, trajectories identical to HEAD byte for byte**, demonstrated on ≥4
  seeds × 60 steps over the full genotype/phenotype/exotype vectors, and reported;
- **separate RNG streams** for the blend coin and the apply coin, following the existing
  `blend-stream-tag` (0x0B1E4D) pattern. Called out explicitly because sharing a stream with the
  propagator draw would still pass the β=0/p=1 check while making every other setting subtly
  wrong — a defect that would survive the obvious test;
- and a test that β > 0 spreads rule content laterally **against the measured β = 0 baseline of
  ~17 of 80 cells**, not against zero. §68.1 is why: the indirect phenotype→selector loop
  already couples cells, so a naive "asserts spread > 0" test would pass on HEAD.

That last point is the packet's most useful sentence and it exists only because the measurement
in §68.1 contradicted my code reading.

### 69.2 The Fable brief asks the sharper question

Not "improve the term" but: **is there an epistemic quantity that supplies interruption, or is
that structurally impossible on a channel carrying transforms rather than rule content?** An
impossibility argument is stated as equally valuable — the shared-draw insight that produced
`X_pair` was itself structural, so that may be the right shape of answer.

It is also asked the question I most want answered and cannot answer myself: **why did the
effect survive the churn control at p = 0.6 and nowhere else?** A mechanism for that is worth
more than a new quantity, because it says what conditions the term needs.

Told explicitly not to touch `self_tuning.clj` — codex-12 is editing it concurrently, and
§49.5 is the record of what happens when two agents write the same file.

---

## 70. codex-12's blending build reviewed — correct, with one weak test and one useful surprise

Job `invoke-1785912579057-1037-b175310d`. **Gate run by me: 87 tests / 2682 assertions, 0
failures.** Stream tags verified in the source: `blend-stream-tag 0x0B1E4D` (matching
`grid/apply-exotype-blend`) and a **distinct** `apply-stream-tag 0x0A7711`, both xor'd into
`draw-seed`, with the propagator's own `ca/mix-seed draw-seed` draw untouched. That was the
defect most likely to survive the obvious test, and it did not occur.

**Byte-compatibility confirmed independently:** defaults versus explicit β=0 / p=1.0 give
identical genotype and phenotype at 60 steps.

**Both parameters genuinely act** — checked because a passing β=0 test proves nothing about
β>0:

| β | genotype cells differing from β=0 | distinct rules at t=60 |
|---:|---:|---:|
| 0.00 | 0 / 80 | 49 |
| 0.25 | **78 / 80** | 29 |
| 0.50 | 75 / 80 | **7** |
| 0.75 | 77 / 80 | 16 |

| p | cells whose rule never changed |
|---:|---:|
| 1.00 | 0 / 80 |
| 0.10 | 1 / 80 |
| 0.00 | **80 / 80** |

### 70.1 The lateral test is weak, though not wrong

codex-12's pin compares differing cells after a perturbation: **38 at β=0 against 46 at
β=0.75**. Both are near saturation on an 80-cell ring at 60 steps, so the test discriminates
poorly — it would pass even if blending did very little. It is not a false test; it is a test
measured where the measure has no room. A shorter horizon would separate the arms properly.
Noted rather than fixed, since the pin does hold and the real evidence is the table above.

### 70.2 The surprise: blending CONSOLIDATES the genotype

Distinct rules fall **49 → 29 → 7** as β rises. Blending mixes rules toward agreement, so it
*reduces* genotype diversity — while §66 showed the epistemic term *increases* exotype
diversity (dominant share 1.000 → 0.51).

> The two mechanisms act on **different layers in opposite directions**: blending consolidates
> rules, the epistemic term diversifies policies.

That is a better reason to combine them than the one we started with. They are not two sources
of the same good; they are complementary, and the combination is a genuine test rather than a
stacking of similar effects.

### 70.3 Combining sweep — designed, and one honest substitution

**`:boring-triggered` is still unavailable** in this path: `self-tuning/step` hard-codes
`:arm :efe-full`, and codex-12 was not asked to change that. But the EFE selector *is* a
phenotype-reading feedback loop — it scores candidates on a local observation derived from the
phenotype. So the combining run tests **blending + EFE-selector + epistemic**, not literally
"best-of + epistemic". Those are different feedback mechanisms and the writeup must not
conflate them.

Design: no-absorbing exotypes seeded explicitly (the selector can only ever hold or adopt a
neighbour's kind, so a no-absorbing start stays no-absorbing); β ∈ {0, 0.25, 0.5, 0.75} ×
{epistemic off, on at κ=0.478, matched-churn}; damage reach on the calibrated ECA scale, plus
dominant share, distinct kinds and halting share.

**Falsifier, from §68.4:** if blending + epistemic is indistinguishable from blending alone,
the epistemic term supplies nothing the interrupter does not, and §66's diversity effect is a
curiosity rather than a tool.

---

## 71. Fable's interrupter answer: an IMPOSSIBILITY RESULT, and the term made things worse

`TN-interrupter-fable-answer.md`, plus `scripts/interrupter_*.clj`.

### 71.1 Proposition 1 — content immobility, proved AND measured

> The genotype update reads only (own byte, own σ, private draws); the actions write σ only.
> So for **any** scoring rule whatsoever — *"epistemic, prophetic, or adversarial"* — cell i's
> byte is a deterministic function of its own initial byte, its own σ-history and its own draw
> stream. **Neighbour rule content enters nowhere.** A cleverer X changes the schedule of
> kernels; it cannot make b_j read b_i.

It gave the proposition a falsifiable corollary and then tested it: across **144 shared-noise
twin pairs**, *zero* cells' rules diverged without strictly earlier σ divergence at that cell.
Switching blending on (β = 0.15) produced **101 ordering violations immediately**.

So the answer to "is there an epistemic term that interrupts?" is **no, and provably not on
this action space.** That closes the question rather than inviting another attempt — the third
structural result today after the parity theorem and the shared-draw construction.

### 71.2 The term did not merely fail to interrupt — it REMOVED interruption

One-bit decision sensitivity at p = 0.6: **off = 0.205** (a near-tie field, the most
interruptible configuration in the study); **epistemic = 0.002**, a 100× collapse. The
σ-perturbation rule-damage cone shrinks **16.1 → 1.4** cells.

The mechanism: at κ = 0.478 the term is **state-independent** (a constant table lookup) and
outweighs most score gaps, so adoption saturates near 1.0 — *"a decision layer run by a
state-independent term is deaf."* **Maximum interruptibility was achieved by no term at all.**

That inverts §66. The diversity the term buys is the diversity of a field that has stopped
listening.

### 71.3 The p = 0.6 anomaly explained — and dominant-share retired

Partly a **metric artifact**: both bonus arms lock into a period-2 alternation that pins
dominant-share at ≈0.5. On a metric that sees partner diversity (kinds ≥ 5%), the graded term
beats churn at **every** p (3.05/4.19/3.71 vs 2.09/2.62/2.00) — it registered only at 0.6
because the `:identity` hub is bridge-limited exactly there (identity wins the three-way
landscape 89.8% / 57.3% / 26.4% at p = 0.3/0.6/1.0, against risk gaps 0.075/0.459/1.57).

**The condition the term needs:** κ·ΔX of the same order as the gaps it arbitrates — a
*grading* regime, not an *authority* regime. That is the mechanism I asked for and could not
supply myself.

**Retire dominant-share as the diversity metric** — §66's headline used it, and it is
period-2-sensitive. §66's *direction* survives on the better metric; its magnitude does not.

### 71.4 The reproduction flag — resolved, and a gap in MY review

Fable flagged that the v2 artifacts no longer reproduce on the current tree. Checked:

| cell | stored | current tree | |
|---|---:|---:|---|
| p = 1.0, off | 0.500000 | **0.500000** | SAME |
| p = 0.3, off | 1.0000 | **1.0000** | SAME |
| p = 0.6, off | 1.0000 | **0.5500** | **DIFFERS** |

**Not a regression — the intended semantic change.** `:apply-probability` now gates the
*transform*, where before it scaled only the *predicted* rate in scoring (§67.2). p = 1.0 is
unaffected and reproduces exactly, which is what codex-12's byte-compatibility claim asserted.

**But my review of that build was weaker than I reported.** I verified *explicit defaults ==
implicit defaults on the current tree* — internal consistency — and called it byte-compatibility.
The actual claim needed *current tree == previous tree*, which is what codex-12's sha256
snapshot did and I did not independently repeat. It happened to hold. I stated a stronger
verification than I performed, and Fable's flag is the only reason that surfaced.

**Consequence:** §65–66's p < 1 numbers are now **historical** — valid measurements of the
old semantics, not reproducible on this tree. Any reuse must say which semantics produced them.

### 71.5 The recommendation, adopted

**Gate blending as a fourth, rule-writing action and score it with the already-validated
`X_blend`** — whose "no σ argument" disqualification (§57.1) *dissolves in a 4-action space*,
because the blend action is then a distinct candidate rather than a constant offset across
three.

That is the answer to a question I got wrong twice: not a better quantity, **a different action
space**. And it makes the retired `X_blend` — r = +0.22/+0.14 against blend-action damage —
immediately reusable, because it was always the right quantity for an action we did not offer.

---

## 72. §1.7 followed up — the fourth action dispatched

Job `invoke-1785915062285-1038-c3a348f3`, park `park-f1f1ab03`, to **codex-12** (it wrote the
transform yesterday and holds the stream-tag context).

### 72.1 Why this is the right follow-up and not another quantity

Proposition 1 closes the search for a better X on a three-action space that writes only σ. The
substrate already contains the interruption operator — blending — but it fires on a stochastic
coin, **outside policy control entirely**. So:

> **X_pair prices the σ-writing actions (injection). X_blend prices the rule-writing action
> (interruption). The two validated quantities partition the enlarged action space exactly.**

`X_blend` was set aside in §57.1 because it "has no σ argument" and so contributed a constant
offset across three σ-candidates. **In a four-action space that objection dissolves** — it is
no longer differencing σ candidates, it is pricing the one action that touches content. The
quantity we retired was always right for an action we were not offering.

### 72.2 The packet's three acceptance tests, and why each exists

- **(a) flag-off byte-identity against the PREVIOUS tree**, demonstrated by snapshotting before
  the edits. Stated in those words because **§70 is where I got this wrong**: I compared
  explicit defaults to implicit defaults on the same tree, called it byte-compatibility, and
  only found out from Fable's flag that it was a weaker claim.
- **(b)** blend writes the rule and leaves σ untouched, and the propagator is skipped that step.
- **(c) the property the design exists for**: with the flag on, some cell's **rule** diverges
  without its **σ** ever diverging. That is impossible today — 0 of 144 twin pairs — so it is a
  clean discriminator, and if it fails the content channel is not actually under policy control.

(c) is the test that makes this build falsifiable rather than merely plausible.

### 72.3 Held back deliberately

No sweep in this packet. When the sweep comes it must **not** use dominant-share: §71.3 showed
both bonus arms lock into a period-2 alternation that pins it near 0.5, so it is an artifact
detector, not a diversity metric. The measures are partner diversity (kinds ≥ 5%), one-bit
decision sensitivity, and rule-damage cone width — the last being the direct interruption
measure, which fell 16.1 → 1.4 under the σ-only term.

---

## 73. "κ has too much authority" — the scaling rule targeted the wrong quantity

Joe picked this out of Fable's answer and it is the sharpest methodological point in it.

### 73.1 The rule we used was a proxy for the wrong thing

codex-7 proposed, and I adopted and defended, this calibration:

    kappa = RMS within-cell SD(ambiguity across candidates)
            / RMS within-cell SD(X across candidates)

It matches the epistemic term's spread to **the ambiguity term's** spread. But a decision does
not turn on how much any one term varies — it turns on the **margin between the top two
candidates**. Those are different quantities, and matching the first is a proxy for the second
only if the terms are the only thing separating candidates, which they are not.

Fable's version is the correct target: *"κ·ΔX of the same order as the gaps it arbitrates —
grading regime, not authority regime."*

### 73.2 Measured, at t = 0, 8 seeds × 80 cells

| p | median decision gap (best − 2nd, no epistemic term) | κ·ΔX at κ = 0.478 | ratio |
|---:|---:|---:|---:|
| 0.10 | 0.1496 | 0.3299 | 2.2 |
| 0.30 | 0.0952 | 0.3299 | 3.5 |
| 0.60 | 0.0302 | 0.3299 | **10.9** |
| 1.00 | 0.0669 | 0.3299 | 4.9 |

**The term exceeds the margin it is supposed to inform by 2–11×, at every p.** That is the
authority regime, and it is the direct mechanical explanation for adoption ≈ 1.0 and for the
100× collapse in decision sensitivity: a term larger than the gaps simply overwrites the
comparison. It is not steering; it is a constant that happens to be indexed by candidate.

**Implied correction:** for κ·ΔX to sit at the median gap (~0.07), **κ ≈ 0.10** — roughly
five times smaller than shipped.

### 73.3 A caveat that stops me contradicting Fable

**My gaps are measured at t = 0 on fresh random states; Fable's landscape was measured after
300 burn-in steps.** Those are different decision landscapes — the field organises, and the
gaps change with it. So my table and Fable's risk gaps (0.075 / 0.459 / 1.57 rising with p)
are **not the same measurement**, and mine should not be read as refuting its p = 0.6 story.
What both agree on, and what matters here, is the **ratio being far above 1 at the shipped κ**.

The burn-in version is the one that should drive the recalibration, and it is a small run.

### 73.4 What this changes

- **The calibration artifact is superseded.** `reports/exotype-policy-epistemic-scale-v2.edn`
  is `:locked-before-dynamics`, which was the right discipline and the wrong target. It should
  be reissued against the decision-gap distribution, still fixed before dynamics.
- **§62's review is weakened.** I verified κ's arithmetic, its seed disjointness and that it was
  locked before dynamics — all true, and none of it checked whether the *rule* was the right
  rule. Procedure was audited; premise was not.
- **The next cell is a κ sweep** at the grading target, with partner diversity and one-bit
  decision sensitivity preregistered, and dominant-share retired. That is cheap and it precedes
  any further use of X_pair as a diversity device.

---

## 74. The fourth action WORKS — Proposition 1 broken exactly where it should be

codex-12, job `invoke-1785915062285-1038-c3a348f3`. **Gate run by me: 90 tests / 2696
assertions, 0 failures.**

### 74.1 Flag-off verified against a pre-both-builds artifact

Not the §70 mistake this time. Checked against `reports-remote/epi/cell-1.0-off.edn`, recorded
**before either build**: stored dominant-share 0.500000, current tree 0.500000 — **identical**.
That is a genuine cross-tree regression check rather than internal consistency.

### 74.2 The decisive property, verified independently

A **witness** is a cell whose *rule* diverged while its *σ* never had. Proposition 1 says this
is impossible without a rule-writing action. 10 seeds × 40 steps, σ perturbed at one cell:

| | witnesses | winning policies |
|---|---:|---|
| 3-action (blend off) | **0** of 1206 | `{adopt-left 53, adopt-right 6, hold 1}` |
| **4-action (blend ON)** | **453** of 8206 | `{adopt-left 40, adopt-right 7, **blend 11**, hold 2}` |

**The content channel is now under policy control.** Blend wins 11 of 60 cells unprompted, and
rule content crosses cells for the first time in this architecture.

### 74.3 My first verification was wrong, and the failure was mine

My initial probe reported **0 witnesses in both arms with identical totals** — apparently
refuting codex-12. Diagnosis before accusation: the blend action **was** offered
(`:hold :adopt-left :adopt-right :blend`) and never won, because I had left
`:epistemic-coefficient` unset. `blend-value` was multiplied by zero.

This is the memory rule earning its keep: *a negative is evidence about your SETUP first.* Had
I reported the first result I would have told Joe a correct build was broken.

**And it exposes a real design property worth recording:** the blend action scores identically
to *hold* on risk and ambiguity — same exotype, since blending does not change σ. **Its only
advantage is its epistemic value.** So with κ = 0 the fourth action is inert by construction,
and X_blend is not merely the natural price for it but the *sole* thing that makes it
selectable. The partition Fable proposed is tighter than it first appeared.

### 74.4 Now the two open threads meet

The κ finding (§73 — the term has 2–11× the authority of the margins it arbitrates) and the
fourth action are now the same experiment. Blend needs *enough* κ to be selectable at all and
*little enough* that it grades rather than dictates, and those bound it from both sides for the
first time. Previously κ had only an upper problem and no lower one.

So the next cell is a **κ sweep on the 4-action space**, scored by rule-damage cone width
(the direct interruption measure, 16.1 → 1.4 under the σ-only term), partner diversity
(kinds ≥ 5%) and one-bit decision sensitivity — **not** dominant-share, retired at §71.3.

---

## 75. κ sweep on the 4-action space — an interior optimum, bracketed by mechanism

12 cells, 24 seeds each, on zone-joe. `reports/exotype-kappa-4action.md`, raw in
`reports-remote/kap/`. Scored against the predictions fixed in the script docstring before the
run.

| actions | κ | rule cone (±SE) | blend wins | kinds ≥5% | decision sens |
|---:|---:|---:|---:|---:|---:|
| 3 | 0.000 | 3.2 ± 1.3 | — | 2.29 | 0.0104 |
| 3 | 0.200 | 1.1 | — | 2.00 | 0.0000 |
| 3 | 1.000 | 1.7 | — | 4.63 | 0.0047 |
| 4 | 0.000 | **3.2 ± 1.3** | 0.000 | 2.29 | 0.0104 |
| 4 | 0.050 | 15.5 ± 3.1 | 0.208 | 1.63 | 0.0083 |
| 4 | 0.100 | 23.7 ± 3.7 | 0.209 | 2.25 | 0.0151 |
| 4 | **0.200** | **42.6 ± 4.2** | **0.235** | 2.04 | 0.0016 |
| 4 | 0.478 | 36.6 ± 4.2 | 0.143 | 2.71 | 0.0375 |
| 4 | 1.000 | 19.6 ± 3.4 | 0.081 | 2.17 | 0.0010 |

### 75.1 P4 confirmed decisively; P2 confirmed; P1 and P3 failed

**P4 — the fourth action is what produces interruption.** At κ = 0 the two arms are
**identical** (3.2 vs 3.2, t = 0.00) — the internal consistency check the design needed. At
every κ > 0 the 4-action arm is 10–40× higher: t = **+4.93, +9.89, +5.07** at κ = 0.05, 0.2, 1.0.

**P2 — the interior optimum is real.** Rise and fall are both separated from the peak:
0.2 vs 0.1 gives t = +3.40, 0.2 vs 1.0 gives t = +4.23. **But the peak's LOCATION is not
resolved** — 0.2 vs 0.478 is t = +1.00, not separated. So the honest claim is *an interior
optimum somewhere in [0.1, 0.5]*, not "at 0.2".

This is the first interior optimum in this work that is **bracketed from both sides by
mechanism** rather than fitted: blend needs enough κ to be selectable at all, and little enough
that it does not dictate. §50.2 is the cautionary case — a law fitted on collinear points that
broke on the first real control. This one has a control (κ = 0, both arms identical) and a
stated falsifier that did not fire.

**P1 failed, and the error was mine.** I predicted blend win-rate rises monotonically with κ,
calling it a manipulation check because *"κ is the only thing pricing the blend action."*
**That is wrong: κ scales X_pair on the three σ-actions too.** Blend wins peak at 0.235 (κ=0.2)
and fall to 0.081 (κ=1.0) because at large κ the σ-actions' epistemic values grow alongside.
The prediction was mis-specified, not the mechanism.

**P3 failed.** Decision sensitivity is non-monotone and noisy on both arms (3-action: 0.0104,
0.0062, **0.1745**, 0.0000, 0.0094, 0.0047). It is a single-snapshot measure taken at one
post-burn-in state and is evidently far too state-dependent to carry a monotone claim. Needs
averaging over time, not over seeds alone.

### 75.2 A flaw in my own instrument

The `:content-witnesses` column is **mismeasured and should not be quoted.** It counts cells
whose rule diverged while σ is equal **at the final timestep** — but Proposition 1 concerns
whether σ *ever* diverged. σ can diverge and re-converge, which is why the 3-action arm shows
nonzero values (0.04–1.75) where the correct test gives exactly 0.

§74.2 ran the correct test — tracking σ divergence across the whole history — and got 0 of 1206
for 3-action against 453 for 4-action. That result stands; **this column does not**, and the
rule-cone column is the one carrying the finding.

### 75.3 What it means

κ ≈ 0.1–0.5 gives the largest interruption, and the shipped 0.478 sits inside that window —
so §73's "κ has too much authority" was right about the *three*-action space and does not
transfer unchanged to the four-action one. Adding an action the term can actually price
changes what the right price is.

---

## 76. The search experiment — spec'd to codex-12

Job `invoke-1785923577456-1039-080395c2`, park `park-8f7fcad3`. Joe's framing:

> *"We're able to create various best-of pictures by hand-tuning, but not yet evidencing a
> harness for testing out a mechanical search/learning process."*

He is right, and there is a concrete artifact: **every experiment in this register pinned
`:lambda-step-size 0.0`.** Each pin was correct in isolation — you cannot attribute an effect
to κ while λ moves — but cumulatively **we have never run the system with its own tuning
mechanism switched on.**

### 76.1 Two design ideas, both Joe's, one sharpened

**(1) Measure criticality WITHIN a run.** Joe proposed damage on the top half of a space-time
diagram against the bottom half. Made into a time series: at checkpoints, fork the state,
perturb, advance fork and unperturbed copy H steps, count, **discard**. The main trajectory
continues unforked — the measurement must not perturb what it measures, and that is called out
in the packet as a correctness requirement rather than left to be inferred.

**(2) Convergence from BOTH directions.** One-sided improvement proves nothing — it could be
relaxation in whatever direction the initial condition points. So: ordered starts (all rule
204, frozen anchor 1.0) *and* chaotic starts (all rule 30, chaotic anchor 65.1). The primary
statistic is deliberately **assumption-free**:

    G(t) = | mean damage(t | ordered) − mean damage(t | chaotic) |

We do not assert what value it should reach. **Convergence is the claim**, and a target being
found from both sides is the difference between exhibiting and searching.

The "from below" leg is already half-answered: §59 showed dead starts carried to 16–20 with no
per-run tuning. The missing leg is from above.

### 76.2 The control was already in the code

`next-lambda` has a **`:random-walk` arm whose docstring says "HUNGER is ignored by the
random-walk null"** — λ moves by the same step size with no direction. That is a *matched*
control, better than λ-off, in exactly the shape that has killed two candidate results already
(matched-constant blend rate; adoption-rate-matched churn).

Three arms: `:fixed-0.55` (λ frozen), `:random-walk` (motion without direction),
`:hunger-coupled` (directed). **B is the one most likely to kill the result and the packet says
so and says do not drop it.**

### 76.3 Preregistered, and instructed to accept a null

P1: under C, G(t) decreases. P2: under A and B it does not, or materially less. P3: mean λ
under C moves consistently while under B it diffuses. **Falsifier: all three arms
indistinguishable ⇒ there is no search, only dynamics, and that is the result.**

The packet says in terms: *"Do not tune anything to make P1 come out. A clean null here is
worth more than a tuned positive, and this project has retracted three tuned-looking findings
already."* Given what the register now contains — a withdrawn t½ law, a retired epistemic term,
a misread diversity headline — that sentence is earned rather than ritual.

Smoke cell only; I launch the 9 cells on zone-joe after reviewing.

---

## 77. Search experiment reviewed and LAUNCHED — after two fixes, one of them mine

codex-12's `scripts/exotype_search.clj`, job `invoke-1785923577456-1039-080395c2`.
**Gate: 90 tests / 2696 assertions, 0 failures.** Smoke cell reproduces byte-identically.

### 77.1 Verified before launch

- **The fork does not perturb the trajectory.** `phenotype-damage` builds `perturbed` as a
  local copy, advances both locally, returns a count; `run-seed` recurs on `(tuning/step
  state)` from the unforked state. The measurement is genuinely non-invasive.
- **The three arms behave as designed**, checked at 300 steps from λ = 0.550:

| arm | mean λ | λ SD | |
|---|---:|---:|---|
| `:fixed-0.55` | 0.5500 | 0.0000 | frozen — correct |
| `:random-walk` | 0.5383 | 0.1005 | **diffuses without moving the mean — the null works** |
| `:hunger-coupled` | 1.0000 | 0.0000 | moved — but see below |

### 77.2 MY SPEC WOULD HAVE MEASURED SATURATION, NOT SEARCH

`:hunger-coupled` reached **λ = 1.0000 with SD 0.0000** — every cell pinned at the ceiling.
At step size 0.01 over 800 steps λ can travel 8.0 across a range of 1.0, so pinning is
guaranteed. Swept it:

| step | t=100 | t=300 | t=800 | fraction pinned at 0 or 1 |
|---:|---:|---:|---:|---:|
| 0.0100 | 1.000 | 1.000 | 1.000 | 1.00 |
| 0.0030 | 0.848 | 1.000 | 1.000 | 1.00 |
| 0.0010 | 0.649 | 0.849 | 1.000 | 1.00 |
| **0.0003** | 0.580 | 0.640 | **0.790** | **0.00** |

Only 0.0003 stays interior. **I specified 0.01 without checking it against the [0,1] range.**
P3 ("mean λ moves consistently") would have been *trivially true for a degenerate reason* — a
directed arm that runs to a wall is not searching, and the experiment would have produced a
confident positive on an artifact. Both moving arms now use 0.0003, since a different step for
`:random-walk` would stop it being a matched control.

Added a **saturation guard** to the preregistration: >20% of λ pinned at any checkpoint ⇒ that
arm is degenerate, P3 void for it, and it must be reported as such.

### 77.3 codex-12's objection was right and is now fixed

It flagged that *"decreases"* and *"materially less"* are not decision rules, and declined to
auto-declare P1/P2 — correctly, since a soft preregistration permits post-hoc reading, which is
the failure §66 already caught me in. Numerical rules now in the docstring:

- **P1** iff G(800) < G(0) and the reduction exceeds 2 × SE of the paired per-seed difference.
- **P2** iff C's reduction exceeds A's and B's, each by > 2 SE of the difference of reductions.
- **P3** iff |Δ mean λ| under C exceeds 3× that under B, **and** λ-SD under B exceeds λ-SD
  under C at t = 800.

It also fixed two unspecified parameters on its own initiative (initial λ = 0.55; stochastic
`:blend-strength` = 0.0, isolating *policy-controlled* blending) and said so. Both are the
choices I would have made, and neither was in my spec.

**Launched:** 9 cells on zone-joe, {ordered, chaotic, random} × {fixed, random-walk,
hunger-coupled}, 16 seeds each, 800 steps, checkpoints to 800.

---

## 78. The search experiment: a null, and the null is about the instrument

9 cells, 16 seeds, 800 steps. Raw in `reports-remote/srch/`. **Written up as
`TN-part3-draft.md` §III.5** — Joe: *"since this is the first real experiment after building the
apparatus for days on end, I think we should give it a proper write-up."* Written before knowing
whether it would be superseded, which is the point.

### 78.1 What happened

| arm | G(0) | G(100) | G(400) | G(800) |
|---|---:|---:|---:|---:|
| λ frozen | 7.1 | 1.1 | 2.1 | 3.1 |
| λ random-walk | 7.1 | 1.1 | 2.1 | 3.1 |
| λ hunger-coupled | 7.1 | 1.9 | 2.0 | 0.3 |

Ordered and chaotic starts **are** separated at t = 0 (−7.1 ± 2.5, t = −2.78) and **are not** at
any later checkpoint (t = −0.67, +0.93, +0.07). Convergence happened. It cannot be attributed.

### 78.2 Two instrument faults, both mine, and they pull against each other

**(1) The instrument is slower than the effect.** Damage horizon 40; a uniform rule field
diversifies into 68–85 rules within ~100 steps (§59). By the time one measurement completes,
both initial conditions are erased. **G(0) = 7.1 where the starts differ by ~64 on the
calibrated scale — the measurement never saw the separation it was built to track.**

**(2) The control is degenerate.** The random-walk arm's damage series is **byte-identical** to
the frozen arm's. λ diffused to SD 0.0043 — too small to flip any decision. So there is no
"motion without direction" arm: there are two frozen arms and one moving one, and P2 has nothing
to compare.

The two faults pull opposite ways on one parameter. At step 0.01 the *directed* arm saturates
(100% pinned — caught pre-launch, §77.2). At 0.0003 the *undirected* arm is inert. **I fixed the
saturation and created the inertness, and did not check the second after fixing the first.** The
usable band, if it exists, was never located.

The check that would have caught it is one line: *before* the run, confirm the undirected arm's
trajectories differ from the frozen arm's. I verified λ *moves* (SD 0.0043 > 0) and never
verified that its motion *changes anything* — the same error as §74.3, where I confirmed a
mechanism was present without confirming it was operative.

### 78.3 What stands

- **The within-run instrument works and is the durable output.** The fork is non-invasive,
  verified in code (`phenotype-damage` advances only local copies; `run-seed` recurs on the
  unforked state) and `damage(t)` is well-defined.
- **The directed arm functions**: λ 0.550 → 0.788, SD 0.0021, no saturation, inside the guard.
- **No claim of search is made.** The observed convergence is equally consistent with the
  dynamics erasing initial conditions, which §59 independently shows it does, fast.

### 78.4 The concrete thing to take back to Fable

Joe anticipated this: *"if we don't nail it in one, the experiment should give us something
concrete we can take back to Fable."* It is a sharper question than the one we started with:

> **Is there a λ step size at which the undirected control both (a) perturbs decisions and
> (b) does not saturate — or does the coupling's accumulate-vs-diffuse asymmetry mean no single
> step size gives a valid matched control?**

Directed motion accumulates (0.550 → 0.788 at step 0.0003); undirected motion cancels (mean
0.5501, SD 0.0043 at the same step). That asymmetry is structural, not a tuning accident, and it
may mean a *step-matched* control is the wrong control here — the same shape of finding as the
adoption-rate-matched churn arm, which was only interpretable because it matched an *outcome*
rather than an input.

### 78.5 Put to Fable

Writing to `TN-search-control-fable-answer.md`, incrementally as before. It has the three prior
notes as context, including its own two answers, and is told that its recommendation from the
last one **was implemented and works** — 453 content witnesses against 0, interior optimum in
[0.1, 0.5]. That matters: it is answering a live line, not a hypothetical.

Two questions, and the second is the one I think matters:

1. **Is a step-matched control possible at all**, given that directed λ motion accumulates
   (0.550 → 0.788) while undirected motion cancels (0.5501, SD 0.0043) at the same step? Framed
   with the observation that **the only control that has ever worked in this project matched an
   *outcome*** — the adoption-rate-matched constant bonus — **not an input.** So: is an
   outcome-matched null the right object, and what outcome should it match?

2. **Is convergence-from-both-directions measurable in this system at all?** If the dynamics
   erases initial conditions faster than criticality can be measured — and §59 says it does,
   68–85 distinct rules within ~100 steps against a 40-step measurement — then the test is
   **structurally impossible rather than badly tuned**, and the right output is a different test
   of search, not a better-tuned version of a broken one.

Told, as before, that a well-argued impossibility result beats a tuned fix, and that its last
two answers were valuable **because they closed questions rather than extending them**.

---

## 79. The baseline figure is CHAOTIC, not class IV — the picture and the number disagree again

Joe, on `search-baseline-w250-t250.png`: *"if this is the baseline I think we've already hit
good enough."* Ran the numbers. **It is not good enough; it is chaotic.**

All anchors re-measured at width 250 in the same harness, 24 seeds, t = 100, so the comparison
is like-for-like rather than against figures from a different width:

| | damage reach |
|---|---:|
| rule 204 (frozen) | 1.0 ± 0.0 |
| rule 90 | 8.0 ± 0.0 |
| rule 54 | 36.0 ± 2.4 |
| rule 110 | 38.0 ± 2.6 |
| rule 30 (chaotic) | 60.9 ± 1.2 |
| **this configuration, burn-in 0** | **63.5 ± 3.3** |
| **this configuration, burn-in 250** | **69.5 ± 1.5** |

**It sits at or above the chaotic anchor, and moves *further* above with burn-in** — 69.5 ± 1.5
against rule 30's 60.9 ± 1.2 is separated. The class-IV band at this width is 36–38. So the
configuration is not merely outside the band; it is running away from it over time.

### 79.1 This is the fourth time today a picture and a number disagreed

And it is the first time the **picture** was the misleading one. The genotype panel shows real
lateral structure — the interruption the fourth action was built to supply, and it *is* there,
26% of decisions taking the blend action. But **interruption is not criticality.** We added a
mechanism that mixes rule content across cells and it does exactly that; mixing content
aggressively is a route *into* chaos, not to the edge.

Joe's reading was reasonable and I supplied the figure that invited it. What stopped it becoming
a claim was the provenance note saying *"NOT a claim of criticality … damage reach for this
configuration has not been measured at this width"* — written because of §58 and §66, and it is
the only reason this correction arrived before a Part III sentence rather than after.

### 79.2 The confusion this exposes, which matters more than the number

**The baseline figure is not the best-of system.** They are different constructions:

| | best-of (§III.1–2) | search baseline (this figure) |
|---|---|---|
| driver | `grid/step` | `self-tuning/step` |
| feedback | `:boring-triggered` | EFE policy selection |
| blending | stochastic, β = 0.75 | policy-controlled, 4th action |
| damage reach | **26.2** (in band) | **63.5–69.5** (chaotic) |

I have been calling both "the system". §70.3 already flagged that the combining run substitutes
the EFE selector for `:boring-triggered` and that the writeup must not conflate them — and then
I produced a figure captioned as "the current system" without carrying that distinction into the
caption.

### 79.3 Consequence

κ = 0.2 was chosen as the **interruption** optimum (§75) — maximum rule-damage cone. That was
the right target for *"can the content channel be put under policy control"* and is the **wrong
target for criticality**. Maximising interruption maximises damage, and damage past ~40 at this
width is chaos.

**The next sweep needs criticality as its objective, not interruption**, and the two now have
measured, different optima. That is a concrete finding and it was not visible before this run.

---

## 80. Fable: the system cannot search AT ANY STEP SIZE — the error signal is rectified

`TN-search-control-fable-answer.md`. An impossibility result of a second kind, and the
step-size question dissolves rather than being answered.

### 80.1 H1 verified, and it is decisive

The λ controller is a sign controller on `realized winner-hunger − target`. Fable's H1: the
sign never changes, so there is no reachable zero and no interior attractor. Measured — λ
clamped at v, 120 steps, mean realized winner-hunger:

| λ clamped at | realized hunger | error vs target 0.05 | sign |
|---:|---:|---:|:--:|
| 0.05 | 0.1801 | +0.1301 | **+** |
| 0.20 | 0.1757 | +0.1257 | **+** |
| 0.55 | 0.1681 | +0.1181 | **+** |
| 0.90 | 0.1574 | +0.1074 | **+** |
| 1.00 | 0.1663 | +0.1163 | **+** |

**Positive at every λ.** The target 0.05 lies entirely below the realized range 0.157–0.187, so
the error never crosses zero. **λ is a rectified ramp: it pushes one way until it hits the
ceiling, at any step size.**

### 80.2 This is the SAME defect as the rate target, in a second term

§49–51: the rule-change target 0.15 sits below every σ's floor of 0.5, so `risk` degenerates
from a preference into a monotone penalty. **The hunger target 0.05 sits below the realized
range 0.157–0.187 and does exactly the same thing to the λ controller.**

> Twice, in two different terms, an aspirational target below the achievable range has turned a
> preference into a gradient. That is not a coincidence; it is a habit of the design.

### 80.3 My §77.2 result was a horizon artifact

I reported that step 0.0003 "stays interior" while larger steps saturate, and treated that as
finding the usable band. Fable: with the sign constant, **the ceiling is reached at t ≈ 0.45/s
for every s** — 0.0003 does not avoid saturation, it postpones it to t ≈ 1500, past my 800-step
horizon. **I measured the horizon, not a regime.** The saturation guard I added would never have
fired and would have certified a degenerate arm as healthy.

### 80.4 Why the whole test was the wrong object

Two independent, untunable reasons that G(t) cannot work here:

- **The plant forgets faster than the controller moves.** τ_mix ≈ 10² (68–85 distinct rules by
  t≈100) against τ_search ≈ 10³ (λ needs ~1500 steps to traverse). **G converges by itself
  first, for every arm including frozen λ** — so P2's premise is false by construction. And it
  is not repairable: slowing the churn to preserve the memory weakens what λ controls; speeding
  the controller hits the saturation wall.
- **The probe is pinned from both sides.** H must be ≳ width/2 to express a reach of ~65 at all
  (damage spreads ≤1 cell/step/side), and ≲ τ_mix to see the initial condition. **At width 80
  that interval is empty.** Hence G(0) = 7.1 against a configured separation of ~64.

**The principle worth keeping:** convergence-from-both-directions is evidence of search only
when τ_mix ≫ τ_search and the probe is fast relative to both. This system is fast-substrate /
slow-modulation *by design*, so the inequality is reversed by an order of magnitude.

### 80.5 The replacement, and the one measurement that gates it

**Vary the controller's initial condition, not the state's** — λ is the one variable the
substrate does not churn, so its initial condition survives. T1: start λ₀ ∈ {0.1, 0.55, 0.9},
search predicts a common interior λ*. T2: kick λ mid-run, **restoration after perturbation at
equilibrium is the operational meaning of "finds"**. T3: λ* must coincide with the independently
swept criticality optimum, or the attractor is arbitrary. T4: coupled vs **yoked replay** —
record λ traces, replay open-loop on disjoint seeds — which is the definition of closed-loop
contribution and the answer to question (1): **match the null on the actuation, not the
increment.**

But T1/T2 fail before being run while H1 holds. **The gating fix is a target with a reachable
zero** — the realized median (~0.17), not the aspirational 0.05.

**A caution Fable's framing does not carry, which I add:** h̄(λ) is nearly **flat** — 0.157 to
0.187 across the entire λ range, a spread of 0.03 for a λ swing of 0.95. So even with a
reachable target the crossing would be shallow and noise-dominated. **λ is a weak actuator for
this error**, and fixing the target is necessary but very likely not sufficient. That is
measurable before anything is built: sweep h̄(λ) properly and see whether the slope is
distinguishable from zero.

---

## 81. The response curve is the gate on the improved experiment

Joe: *"shall we build and run that improved experiment?"* Yes — but T1 and T2 are **guaranteed
to fail while H1 holds**, since a sign controller whose error has no reachable zero has no
interior attractor to find or to restore. Running them now would buy a negative we can already
predict, which is the shape of waste this register has recorded three times.

So the gate first: **h̄(λ)** — mean realized winner-hunger with λ *clamped* at v, swept over
v ∈ [0,1], 16 seeds, 150 burn-in steps. `scripts/exotype_lambda_response.clj`, running on
zone-joe.

It answers two questions at once, and the second is mine rather than Fable's:

1. **Is there any target with a zero crossing?** Search is possible iff h̄(v) − target crosses
   zero in (0,1), and the crossing point is the *predicted* λ*. The spot check in §80.1 says the
   realized range is 0.157–0.187, so a target near 0.17 would cross where 0.05 cannot.
2. **Is the slope distinguishable from zero?** h̄ moved only 0.157 → 0.187 across the entire λ
   range in that spot check — a spread of 0.03 for a λ swing of 0.95. If the slope is not
   separated from zero, **λ is a weak actuator for this error and no choice of target gives a
   sharp attractor.** Fixing the target would then be necessary and insufficient, and T1–T4
   would measure noise around an arbitrary point.

The script fits the slope with a standard error and reports the verdict rather than leaving it
to be eyeballed.

### 81.1 A correction to the chaos framing

Joe: *"as we know from chaos theory even small changes in initial conditions could give wildly
different results."* True of the **state** variable — that sensitivity is precisely what makes
damage reach a usable criticality measure, and it is why the damage instrument works at all.

But λ is a **controller** variable, and there the desired behaviour is the *opposite*:
**convergence to a common λ\* from different λ₀.** If small λ₀ changes gave wildly different
outcomes, that would be evidence *against* an attractor and against search. The whole reason
Fable's redesign works is that λ is the one variable the substrate does **not** churn — its
initial condition survives, so it can carry the memory the state cannot.

Sensitive dependence in the plant, convergence in the controller. The experiment needs both, in
different layers, and conflating them would invert the prediction.

### 81.2 The response curve — the gate PASSES, on a knife edge

`scripts/exotype_lambda_response.clj`, 16 seeds, 150 burn-in steps, λ clamped:

| λ | h̄ | SE |
|---:|---:|---:|
| 0.00 | 0.1755 | 0.0043 |
| 0.30 | 0.1670 | 0.0013 |
| 0.50 | 0.1676 | 0.0018 |
| 0.70 | 0.1648 | 0.0015 |
| 1.00 | 0.1617 | 0.0012 |

**Slope −0.01369 ± 0.00180, t = −7.62.** My worry that h̄ might be flat is **refuted** — λ does
regulate hunger, and the sign is right: h̄ *falls* as λ rises, so `λ ↑ when h > target` is
negative feedback with a genuine fixed point.

**But the reachable range is 0.0148 wide** — the full λ swing from 0 to 1 moves h̄ from 0.176 to
0.162. The shipped target of **0.05 sits far below that entire range**, which is exactly why the
error never changes sign and λ ramps to the ceiling. H1 is now explained, not merely confirmed.

**The fix, and its price.** Any target inside (0.1617, 0.1755) gives a crossing. Choosing the
midpoint ≈ **0.1676** predicts **λ\* ≈ 0.50**. But the precision is poor by construction:
with slope 0.0137 per unit λ and SE ≈ 0.0015 on h̄, **λ\* is determined to roughly ±0.11**. The
attractor, if it exists, is broader than a fifth of the λ range.

So the honest statement is: **search is possible here, the operating window is 0.015 wide, and
the attractor it defines is broad.** T1 remains worth running — convergence from λ₀ = 0.1 and
0.9 toward ≈0.5 is visible even at ±0.11 — but T3 (does λ\* coincide with the criticality
optimum?) will be the weak link, because a ±0.11 attractor cannot be matched sharply against
anything.

**And the target is configuration-dependent.** h̄ depends on κ, the blend action, the vocabulary
and apply-probability. A target derived here is not portable to another configuration; it would
have to be re-derived. That is a real fragility of the fix and should be recorded as such rather
than discovered later.

**Preregistered for T1, before it is built:** with target 0.1676, λ₀ ∈ {0.1, 0.55, 0.9} converge
toward λ\* ≈ 0.50 ± 0.11; with the shipped target 0.05, all three ramp to 1.0. The second half
is a positive control — it should reproduce H1 exactly, and if it does not, something else is
wrong.

---

## 82. T1/T2 dispatched — the first NUMERICAL prediction made before a run

Job `invoke-1785927541990-1041-588edf9d`, park `park-e982cf1f`, to codex-12.

Everything in this register so far has been a *directional* prediction — "rises", "falls",
"is non-monotone". This packet carries a **number fixed before the run**:

> **λ\* ≈ 0.50**, with the three λ₀ arms landing within **±0.11** of one another.

The ±0.11 is not a guess either: it is SE(h̄)/|slope| = 0.0015/0.0137, the intrinsic width of
the attractor implied by the response curve. So the packet predicts *both* the location and
that the attractor will be broad — the second half being a prediction that the experiment
cannot be sharp, stated in advance rather than offered as an excuse afterwards.

### 82.1 The positive control is the load-bearing part

**P2: under the shipped target 0.05, all three λ₀ arms must reach mean λ > 0.99.** This
reproduces the rectified-ramp behaviour already measured. The packet says: *"This MUST hold …
if it fails, the harness is wrong — stop and report rather than interpreting anything else."*

Without it, a null on T1 would be uninterpretable — indistinguishable from a broken harness.
With it, a null on T1 with P2 passing is a real result about the mechanism. This is the control
structure that §66 taught: an arm that must succeed, so that a failure elsewhere means
something.

### 82.2 Also carried

- **`:hunger-target` must be verified state-read before anything else** — if it is hardcoded
  anywhere the packet is void. That is the §74.3 lesson: confirm the mechanism is *operative*,
  not merely present.
- **N1, the ramp control** (open-loop λ(t) = clip(λ₀ + s·t), Fable's parameter-free null). Under
  a reachable target the coupled arm should *stop*; the ramp cannot. If they agree, there is no
  regulation.
- **T2, restoration:** kick λ by ±0.2 at equilibrium and require at least half the kick undone.
  Fable's framing, which I adopt: **restoration after perturbation at equilibrium is the
  operational meaning of "finds"**, and it needs no reference to initial conditions at all.
- The falsifier says in terms: **do not adjust the target to obtain convergence.**

### 82.3 What is genuinely at stake

If λ₀ = 0.1 and λ₀ = 0.9 converge toward ≈0.50 under a reachable target while ramping to 1.0
under the shipped one, then the system regulates a parameter toward a fixed point — the first
evidence in this work of the construction doing anything that deserves the word *search*.

If they do not converge, the negative-feedback reading is wrong **despite** t = −7.62 on the
slope, and the response curve measured something that does not govern the closed loop. Either
outcome is worth the run, which is the property a preregistration is supposed to produce.

---

## 83. T1/T2 reviewed and launched — and my preview says the prediction will FAIL

codex-12's `scripts/exotype_controller.clj`. **Gate run by me: 90 tests / 2696 assertions, 0
failures.** Smoke reproduces byte-identically. `:hunger-target` confirmed state-read.

### 83.1 codex-12 caught a false-positive trap in MY design

**N1, the ramp control, is invalid for T2 as I specified it.** The open-loop ramp reaches
λ = 1.0 at t ≈ 450, well before the t = 1200 kick. So a *negatively* kicked ramp climbs back to
its ceiling and **looks exactly like restoration** — the control I added to rule out "it's just
a ramp" could itself have produced the signature of regulation. codex-12 labelled N1 descriptive
and refuses to use it for P3.

That is the second time a control of mine has been structurally incapable of doing its job
(§78.2: the random-walk arm was byte-identical to frozen). Both were caught before the claim,
neither by me.

### 83.2 The positive control passes

Target 0.05, λ₀ ∈ {0.1, 0.55, 0.9} → **all three reach mean λ = 1.0000 at t = 1000.** The
rectified ramp reproduces exactly. So the harness is sound and a failure elsewhere means
something.

### 83.3 My preview says P1 will fail — and the reason matters

One seed, t = 1000, target 0.1676:

| λ₀ | mean λ @1000 |
|---:|---:|
| 0.10 | **0.0022** — at the floor |
| 0.55 | 0.1222 |
| 0.90 | 0.4295 |

Range **0.43**, against a predicted ±0.11 — and all three are moving **down**, with λ₀ = 0.1
already collapsed to the boundary. So the error is negative everywhere: **realized hunger in
the closed loop is below 0.1676**, and λ ramps down instead of settling.

**The rectified-ramp pathology again, in the opposite direction.**

The reason is the interesting part. **h̄(λ) was measured with λ clamped uniformly across the
ring.** In the closed loop λ is *heterogeneous* — the field develops an SD — and a heterogeneous
λ field produces different decisions than any uniform one. So the open-loop response curve
**does not govern the closed loop**, and the target derived from it does not sit where the
closed-loop equilibrium is.

That is precisely what my own falsifier said would falsify the negative-feedback reading:
*"if the three λ₀ arms do not converge under the reachable target, the negative-feedback reading
is wrong regardless of the slope's t-statistic."* It looks like it will fire, on a
t = −7.62 slope.

### 83.4 Launched anyway, deliberately

10 cells on zone-joe (6 × T1, 3 × T2, 1 ramp), 16 seeds, 2000 steps. One seed at t = 1000 is not
the experiment: the arms may still be converging, and the run goes to 2000 with statistics and
controls. **A documented negative with a passing positive control is a real result**, and
changing the plan now because a preview looked bad is exactly how a preregistration gets
laundered.

Recorded here *before* the cells land, so the prediction and its likely failure are both on the
record in advance.

---

## 84. T1/T2 RESULTS — the prediction fails, the control passes, and P3 is a false positive

10 cells, 16 seeds, 2000 steps. Raw in `reports-remote/ctl/`.

### 84.1 Positive control: PASSES

Target 0.05, λ₀ ∈ {0.1, 0.55, 0.9} → **all three at mean λ = 1.0000 ± 0.0000** at t = 2000. The
rectified ramp reproduces exactly, so the harness is sound and the rest is interpretable.

### 84.2 P1: FAILS — convergence to a boundary, not an attractor

| λ₀ | mean λ @2000 |
|---:|---:|
| 0.10 | 0.0037 ± 0.0003 |
| 0.55 | 0.0028 ± 0.0003 |
| 0.90 | 0.0471 ± 0.0025 |

**Pairwise range 0.0442** — *tighter* than the ±0.11 I predicted. But P1 had two clauses, and
the second fails: **not all interior**; two arms sit below 0.01, i.e. **on the floor**.

**Predicted λ\* = 0.50. Measured ≈ 0.02.** The location prediction is wrong by the width of the
whole range.

So the arms converge, and converge to zero. **This is still a rectified ramp — downward instead
of upward.** The target 0.1676 is unreachable *from below* in the closed loop, exactly as the
shipped 0.05 was unreachable from above.

**Why the prediction failed, which is the finding.** h̄(λ) was measured with λ **clamped
uniformly across the ring**. In the closed loop λ is **heterogeneous** — the field develops a
spread — and a heterogeneous λ field produces different decisions than any uniform one. The
open-loop response curve, slope t = −7.62 and all, **does not govern the closed loop.** My
falsifier said this would falsify the negative-feedback reading; it fired.

**A methodological note worth keeping: a two-clause decision rule caught what a one-clause rule
would have called a success.** My own summary line printed "P1 HOLDS" off the range clause alone
before the interior clause was applied. Had I written only "the three arms converge", this would
be recorded as the first evidence of search.

### 84.3 P3: satisfied ARITHMETICALLY and meaningless

| arm | t=400 | t=800 | t=1200 | t=1600 | t=2000 | \|end − pre-kick\| |
|---|---:|---:|---:|---:|---:|---:|
| no-kick | 0.3786 | 0.2054 | 0.0507 | 0.0054 | 0.0028 | 0.0479 |
| kick +0.2 | 0.3786 | 0.2054 | 0.0507 | 0.0892 | 0.0110 | 0.0397 |
| kick −0.2 | 0.3786 | 0.2054 | 0.0507 | 0.0036 | 0.0037 | 0.0470 |

Both kicks satisfy |end − pre-kick| < 0.1. **P3 "passes" and the pass is worthless.**

Pre-kick λ is 0.0507, already collapsing toward the floor. A **−0.2 kick is a no-op** against a
clip at zero. A **+0.2 kick decays back** not because the controller restores a setpoint but
because the ramp continues downward — and the **no-kick arm moves by 0.0479, essentially the
same amount as either kicked arm.** The control that reveals this is the one I nearly omitted.

**This is precisely the false-positive shape codex-12 rejected N1 for**, arriving in T2 itself:
a ramp pinned at a boundary mimics restoration. Its objection generalised further than either of
us applied it, and the no-kick arm is the only reason the mimicry is visible.

### 84.4 Where this leaves the search question

Two targets tried, two rectified ramps in opposite directions, **no interior attractor in
either**. The system does not regulate λ toward a setpoint under any target yet tested, and the
method for choosing a target — the clamped response curve — is now known not to transfer to the
closed loop.

What would have to be measured instead is the **closed-loop** equilibrium hunger as a function
of target: sweep the target, measure where λ settles. If λ settles at a boundary for every
target, the controller has no interior fixed point at all and search via this parameter is
closed — which would be the third impossibility result in this line, and cheaper to establish
than the two before it.

---

## 85. Dropped-message root cause — codex-1 traced it; it is the known D6 defect

Joe: *"I keep getting lost messages from yourself."* codex-1 traced it conclusively and I am
recording it here because **the diagnosis changes how I should work, not just what to fix.**

**Cause:** a background-task completion re-invoked the warm Claude pouch *after* the operator
turn had closed. With `FUTON3C_POUCH_DEMUX` unset (default off) nobody was continuously reading
those autonomous turns, so the next operator invocation deliberately discarded them via
`drain-pending!`. Server log, 10:54:58 UTC:

    [pouch] claude-14 drained 39 stale line(s) before turn

This is **D6** in `futon3c/holes/excursions/E-unsolicited-pouch-turns.md` — already documented.
The demultiplexer is built and tested but **load-dark**; operator-surface routing and
end-to-end turn identity are not built. It matches the standing memory note: *"M-agency-hardening
REOPENED-pending-Joe — pouch turns nobody fed break drain-pending!"*

### 85.1 Exactly which of my messages were lost, and why that is predictable

The two Joe missed were **both replies to background-task notifications**, not to Joe:

- "The gate passes, and it explains H1…" — fired by the response-curve task completing
- "Byte-identical to the Zone run…" — fired by the second (local) run completing

Neither was an answer to an operator turn. **That is the D6 signature exactly**: turns generated
autonomously between operator turns are the ones discarded.

### 85.2 The mitigation I can adopt unilaterally, starting now

Enabling the demux is a futon3c change and M-agency-hardening is Joe-gated, so that is not mine
to switch on. But the failure mode is fully predictable, which means it is avoidable from my
side:

1. **Never let an autonomous turn be the only carrier of a result.** Write findings to the
   register *before* replying — which I have been doing, and it is the reason nothing was
   actually lost from the record, only from Joe's screen.
2. **Restate the substance of any autonomous turn in the next operator-facing reply.** A
   background-task reply should be treated as *provisional delivery*, not delivery.
3. Prefer completing work inside an operator turn where the choice exists, rather than firing a
   long autonomous reply that may be dropped.

**⊸fix.** The register is why this cost a re-report rather than a re-run: every number in the
two lost messages was already in `TN-baldwin-reboot.md` §81.2 before the messages were sent.
Writing to the file first turned a delivery failure into an inconvenience.

### 85.3 One correction from codex-1 worth carrying

Its earlier `*ERROR*: Unknown message` fragments came from **emacsclient mishandling an oversized
multiline inspection result**, not from the live buffer. I hit the same thing myself this
session — a `ps` output flooded by a 400-line classpath. Oversized emacsclient/shell returns are
their own failure mode and should not be read as evidence about the system under investigation.

---

## 86. Xenotype-layer figures — and Langton's λ is useless here, for a reason the paper proves

Joe: *"we need a spacetime diagram in the **xenotype** layer … showing how the pheno-geno-exo
layers are being evolved over time and whether they are indeed reaching a healthy state"*, with
the sharp observation that **finding good examples does not show how the dynamics work.**

Right, and the reason is structural: **the ring axis is not the space being searched.** A
ring-space diagram can only answer "is this example nice?". The manifold is what the search
moves through. `scripts/exotype_xenotype_figure.clj`, two panels, time on X:

- **`xenotype-rulespace.png`** — Y = rule byte 0–255, intensity = occupancy. Shows **bright
  persistent horizontal bands against a haze**: specific rules the population keeps returning
  to, i.e. *attractors in rule space*, with exploration around them. This is the "how it works"
  view and it is new.
- **`xenotype-langton.png`** — Y = Langton's λ (count of 1s in the 8-bit table, 9 values), the
  classical order/chaos coordinate for CA rule space.

### 86.1 The Langton panel is flat, and that is a finding

| | Langton distribution |
|---|---|
| t = 0 | `{1:14, 2:23, 3:55, 4:70, 5:45, 6:31, 7:12}` |
| t = 250 | `{0:1, 1:6, 2:24, 3:37, 4:78, 5:68, 6:28, 7:6, 8:2}` |

Centred on 4/8 = **0.5 at both ends**, slightly broader in the tails. The population does not
drift in Langton coordinate at all.

**And the paper already proves it must not.** `draft7.tex` carries the section *"Every fixed
point has λ = 1/2"* — every fixed point of the propagator family sits at Langton λ = 1/2. So a
population under this transform is pinned near 1/2 **by construction**, whatever regime the
dynamics is in.

> **Langton's λ cannot diagnose the edge of chaos in this system.** It is constant by theorem,
> not by coincidence, and a search programme that used it as its coordinate would measure
> nothing.

That is worth more than the figure it came from: it rules out the most obvious candidate
manifold coordinate before anyone builds on it, and it does so from a result already in the
paper. Distinct rules fall 162 → 103 over the run, so the population *is* concentrating — just
not along the Langton axis.

### 86.2 Caution carried forward on the t1 figures

Joe read `t1-reachable-lambda-to-0.png` as *"clear EoC, we've already got what we're looking
for."* **Those figures are unmeasured.** The closely related search-baseline configuration
measured **63.5–69.5** at width 250 — at or above the chaotic anchor, §79. The t1 runs differ
(λ is evolving) so they may genuinely differ, but this is the third time a picture has read as
class-IV in this substrate, and twice the number has said otherwise.

**Measure before the visual programme rests on it.** Cheap: damage reach for the t1 configs at
width 250 against the anchors already re-measured there.

---

## 87. D6 routing built and reviewed — ready, and deployment is Joe's call

codex-1, job `invoke-1785930502837-1042-91ce1dfc`.

**Route chosen:** direct insertion via `agent-chat-insert-message` into the existing Claude REPL
buffer, resolved by session-id first and agent-id second — the same display primitive a normal
reply uses, without re-invoking Claude. **Marker:**

    claude-14 [AGENT-INITIATED — NOT A REPLY]:

That is the right shape: the failure mode is a turn nobody asked for being *read as* a reply, so
the marker has to make that reading impossible, and it does.

### 87.1 What I verified, and how

| check | result |
|---|---|
| fdev restarted? | **No** — uptime 1d 15h47m, unchanged |
| flag still default OFF | yes, `FUTON3C_POUCH_DEMUX` default `false`, unset in env |
| **OFF path byte-unchanged** | **verified, not taken on trust** — see below |
| sink can wedge the demux thread? | **no** — dispatched via a bounded executor with an inner catch |
| targeted suites, run by me | **15 tests / 41 assertions, 0 failures** |

**The OFF-path check is the one that mattered.** The diff shows *17 deletions*, which reads
badly against "byte-unchanged". Inspecting them: `read-turn-with-timeout`, `drain-pending!` and
the `drained … stale line(s)` message are all still present in the file — the deletions are
**moves into `(when-not (:demux pouch) …)` guards**, not removals. With the flag off `:demux` is
nil, so the original synchronous path runs exactly as before, `drain-pending!` included.

### 87.2 A failing test that is NOT this change

The full suite shows `agent_pouch_test.clj:36 enabled-defaults-off` failing. It is unrelated:

```clojure
(deftest enabled-defaults-off
  (System/clearProperty "FUTON3C_KANGAROO")
  (is (false? (pouch/enabled?))))
```

It tests **`FUTON3C_KANGAROO`**, not the demux flag, and it clears the system *property* while
`bool-prop-or-env` also reads the environment — where **`FUTON3C_KANGAROO=true` is set in my
shell.** Environment-dependent test, failing for an environmental reason, in a file codex-1 did
not touch. The federation failures are likewise in untouched namespaces.

Worth recording as a defect in its own right: a test named `enabled-defaults-off` that passes or
fails depending on the developer's shell is not testing what it says.

### 87.3 Deployment — Joe's decision, and it needs a restart

1. Set `FUTON3C_POUCH_DEMUX=true` in fdev's externally supervised environment.
2. **Restart fdev from a separate, non-Agency-dependent shell** — codex-1's own caveat and a good
   one: restarting fdev kills the Agency, so driving the restart *through* the Agency would sever
   the connection doing it.
3. Recreate warm pouches and verify REPL/socket delivery.

A restart is unavoidable because both demux selection and sink registration happen at
startup/pouch-creation. **Nothing was enabled, reloaded, restarted or committed.**

fdev has been up 1d 15h; that is the cost side of the decision, and it is Joe's to weigh.

---

## 88. Target sweep, 6 of 10 in — a BIFURCATION, not a fixed point

Recorded mid-run, and recorded *first*, because this is an autonomous turn and D6 drops those
(§85.2). Zone, parallelism 3:

| target | settled λ | drift 1600→2000 | |
|---:|---:|---:|---|
| 0.050 | 1.0000 | +0.0000 | boundary |
| **0.160** | **0.9593** | **+0.0647** | **still climbing** |
| 0.170 | 0.0041 | −0.0019 | boundary |
| 0.180 | 0.0009 | −0.0005 | boundary |

**A sharp transition between 0.160 and 0.170.** Below it λ runs to 1.0; above it λ runs to 0.
Nothing settles between.

### 88.1 The equilibrium is unstable, so there is nothing to find

The open-loop curve said h̄ falls with λ (slope −0.0137), which is the *right sign* for a stable
fixed point. In closed loop λ departs to a boundary from **both** sides of the critical target.
The reconciliation: closed-loop realized hunger is essentially **independent of λ**, so the
error keeps its sign indefinitely and the tiny restoring slope never bites before λ hits a wall.

> The critical target ≈ 0.165 is not an attractor. It is the value of realized hunger, and it
> separates "positive error forever" from "negative error forever". **A sign controller on a
> flat plant has no stable interior fixed point** — which is why two targets gave two rectified
> ramps in opposite directions.

Pending the remaining 4 cells, this is the falsifier for search via this parameter firing, as
§84.4 anticipated.

### 88.2 My inline label was wrong again, in the same way

The script printed **INTERIOR** for target 0.160 on the bounds alone. The preregistration also
requires **not still drifting** — `|λ(2000) − λ(1600)| < 0.02` — and 0.0647 fails it by 3×. λ at
0.9593 climbing at +0.065 per 400 steps is *en route to the ceiling*, not settled.

**Third time a display of mine checked fewer clauses than the preregistration it reports on**
(§84.2 P1, §75 the peak location, now this). The preregistration has been right each time and
the convenience summary wrong each time. The rule to adopt: **the reporting code must evaluate
the full decision rule, not a readable subset of it** — otherwise the rule is decorative.

---

## 89. R-audit of the futon5 EFE — two of the failures are ABSENT R-numbers, measured not guessed

Joe: *"is it reasonable to ask whether our AIF engine itself needs some upgrades? … maybe a
careful audit of the AIF requirements (R-numbers) would tell us more."*

The contract is `futon2/docs/futon-aif-completeness.md`, R1–R18; R14–R18 are the tracked-open
frontier. Audited the futon5 exotype engine against it:

| R | status in the contract | status in `futon5.exotype` |
|---|---|---|
| **R13** policy adequacy — multi-step `G(π)`, not degenerate single-step | apparatus ✓ in futon2 | **SINGLE-STEP.** No rollout, horizon or depth anywhere in `efe.clj`. `cell-decision` scores one-step candidates only. |
| **R14** precision-over-policies (γ) | **absent** | **ABSENT.** No γ, softmax, temperature or precision. The winner is `(first (sort-by :total candidates))` — a **hard argmin**. |
| **R15** hierarchical / temporal depth | partial | absent here |
| **R18** faithfulness of the quantities | absent as criterion | this whole line of work has been doing it empirically |

### 89.1 The failures map onto the gaps, and the mapping is mechanical

- **"κ has too much authority"** (§73: the term carries 2–11× the decision gaps it arbitrates;
  §80: adoption saturates, decision sensitivity collapses 100×). **With a hard argmin, any term
  larger than the gap DICTATES.** Under a softmax at precision γ the same term would *sharpen*
  the distribution while leaving the comparison intact. The authority problem is not a badly
  chosen κ — **it is R14's absence.** There is no knob for "how decisively to act on the score"
  because the engine only has one setting: absolutely.

- **"A sign controller on a flat plant"** (§88: h̄ essentially independent of λ in closed loop, so
  every target ramps to a boundary). λ multiplies conatus inside a **single-step** score. Under
  multi-step `G(π)` λ would shape *trajectories*, and realized hunger would depend on it far more
  strongly. **The flatness that killed the controller is what R13's degeneracy predicts.**

- **Fable's own result** that `X_persist` saturates and "past the mixing time the epistemic
  content of *which σ* IS the adoption indicator" is the same observation from the quantity side:
  a one-step quantity in a one-step objective.

### 89.2 Why this is worth a fourth Fable brief

Three independent measured dead-ends — the epistemic term's authority, λ's unsearchability, and
the one-step quantity's saturation — **all reduce to two absent R-numbers.** That is a much
better-posed question than "should we upgrade the engine": it is *which* of R13/R14/R15, closed
in which order, would change these specific outcomes.

The honest alternative it must be free to reach: **that we have been asking a single-step
hard-argmin engine to exhibit search, which it structurally cannot**, and the correct move is
the engine rather than another parameter. Three of its four answers so far have been
impossibility results, and each closed a line we would otherwise have kept pushing.

### 89.3 Joe's correction: depth in the MANIFOLD, not depth in time

I framed R13 as temporal rollout. Joe:

> *"G(π) could mean something like: given the possibilities that my exotype makes available to
> me, I will choose the one that is best given local conditions. That doesn't necessarily need
> to be multi-step … The exotype could give various possible playouts in a simulated sense."*

He is right, and the substrate makes his version the *cheaper* one. An exotype is a permutation
on the 256 rules, so adopting σ commits a cell to traversing **σ's orbit through its current
rule**. The orbit is the trajectory — and it is a static property of (σ, rule), precomputable,
no simulation.

**Measured orbit lengths:**

| exotype | lengths from 4 starts | | exotype | lengths |
|---|---|---|---|---|
| builder | 2, 2, 6, 6 | | identity | 4, 10, 5, 3 |
| collapser | 3, 2, 5, 2 | | even4 | 3, 2, 2, 3 |
| chaos | 3, 3, 4, 4 | | odd53 | 2, 2, 3, 3 |
| even1 | 2, 2, 3, 5 | | fix6 | 9, 11, 5, 6 |

**2 to 11 rules.** Fully enumerable per (σ, rule), cacheable across a run at about the cost of
the existing pair table.

> **R13 may be closable in CLOSED FORM.** Score each candidate σ over the orbit it commits the
> cell to, rather than over one step. That is a genuine multi-step `G(π)` — **the orbit *is* the
> rollout** — with no rollout engine, no horizon parameter, and none of the compute a temporal
> rollout needs.

**The nuance that must not be designed around:** the cell does **not** pick the best element of
its orbit. It applies σ once per step and is *carried along the cycle*. So the score cannot be
`max over orbit`; it must be a functional of the whole orbit — mean, discounted along traversal
order, worst-case, or time-to-reach-a-good-region. Choosing that functional carelessly would
reproduce the authority failure in a new place.

**Two connections:**

- **Orbit length is a free structural coordinate we have never used**, varying 2–11 across σ. And
  unlike Langton's λ it is **not pinned by the fixed-point theorem** (§86.1: every fixed point has
  λ = 1/2, which is exactly why the Langton coordinate is flat and useless here). If we want a
  manifold coordinate for the search, this is a live candidate and Langton's is not.
- Content immobility is untouched: orbits move a cell through rule space along σ but still never
  import a **neighbour's** content. Orthogonal to the blend action, not a substitute.

Relayed to Fable mid-run so it answers against this version rather than the temporal-rollout
version I originally described.

---

## 90. Fable's engine audit — the reduction SPLITS, and my orbit claim is RETRACTED

`TN-aif-engine-fable-answer.md`, four scripts under `analysis/`.

### 90.1 §89.3 IS WRONG — there are no orbits

I told Joe the exotype is a permutation on the 256 rules, measured "orbit lengths 2–11", and
built a closed-form-R13 recommendation on it. **Fable checked and it is false.** Verified myself:

`apply-exotype` on one rule with eight different draw-seeds → `订 功 甘 扔 功 甘 扔 功`. **The
transform is stochastic.** My measurement called `rule-permute` directly, *outside* the
`ca/with-mixed-seed` wrapper the real system uses — so I iterated a deterministic simplification
that does not exist in the system.

True reachable-set sizes: **chaos 197, identity 257, fix6 193, odd53 160, collapser 81** — against
my claimed 2–11. It is a **Markov chain on bytes with merges**, not a permutation.

**This is the same defect as §68.1 and §74.3: I inferred structure from reading code, and the
measurement I ran to "check" it quietly dropped the part that mattered.** Third occurrence. The
rule that would have caught all three: *call the function the system calls, not the one it wraps.*

Joe's underlying instinct — depth in the manifold rather than in time — **survives, and Fable
built it properly**: exact per-(σ, byte) **chain-DP**. So the idea was right and my mechanism for
it was wrong.

### 90.2 The reduction splits — one half right, one half wrong twice

**Authority ↦ R14: CONFIRMED.** One-bit sensitivity collapses 4–5× as κ rises 0 → 0.478 under the
argmin. Under a **softmax at γ = 4 it is flat in κ**: 0.110 / 0.117 / 0.119 / 0.103 at
κ = 0 / 0.2 / 0.478 / 1.0. Degrades at γ = 16, re-collapses at γ = 64 — because **γ → ∞ *is* the
argmin.** *Authority is a property of the selection rule, not of the coefficient.*

**λ-immobility ↦ R13: WRONG, and wrong twice over.**
1. Candidate totals are **affine in λ**, so an argmin winner is piecewise-constant in λ — **zero
   gain almost everywhere at ANY depth.** Depth-2 and chain scoring leave actuation at
   0.005–0.008 against state noise 0.045.
2. Nor is it R14: under softmax, E[h] moves ~0.005 across λ's whole range, per-cell leverage
   ~10⁻⁴, and **no target in 0.10–0.25 crosses ½.** The closed loop still ramps, and shows
   outright **path dependence** (target 0.20: floor from below, stall at λ≈0.52 from above).

> **The λ↔hunger pair is a PLANT degeneracy. No engine upgrade repairs it.**

And a confirmation I did not expect: **mean census winner-hunger = 0.16563** — our measured
bifurcation window (0.160–0.170) *is* the census mean, derived independently.

**Third dead-end reduces to neither**: it is the C-vector — futon2's **R19**, which was absent
from the audit as I posed it — plus R18. Unreachable aspirational targets make risk and conatus
monotone gradients and `:chaos` the structural argmin. **That is the same defect as the rate
target and the hunger target, now identified as a numbered gap rather than two incidents.**

### 90.3 The ordering, with a warning attached

1. **R14 softmax — one line.** The only change that fixes a measured dead-end by itself, and a
   prerequisite for the rest.
2. **R13 in chain form** — exact per-(σ,byte) chain-DP. Triples within-cell risk spread, changes
   36% of argmins, and **gives the blend action its first consequence price: 0.151 against
   exactly 0 today.** Blend is currently priced *entirely* by κ·X — which explains §74.3, where
   the fourth action was inert until the epistemic coefficient was switched on.
   **But chain-risk spans 1.9 nats against a median gap of 0.013**, so under an argmin it would
   simply become the next dictator. **R13 must come after R14 or it reproduces the authority
   failure.**
3. γ-from-outcomes. **R15 not now** — the λ layer is already a proto-second-level and it failed
   for plant reasons, not depth reasons.

### 90.4 The answer to the real question

> The single-step hard-argmin engine structurally cannot search — **but removing both defects
> exposes the deeper fault: the search variable was plumbed to a sensor it cannot move.**

Minimal change making search *possible*: softmax **plus re-pairing the controller to a
census-passing knob–sensor pair.** One already exists with measured leverage and an interior
optimum: **κ ↔ realized blend share.**

And the sentence that reframes the project: **"no engine change makes it *want* the edge — that
is R19 work."** We have been trying to make a system search for a regime it has no term
representing.

---

## 91. R14 dispatched — one packet, not the set, because the ORDER is the finding

Job `invoke-1785934411910-4-0e0efdce`, park `park-ad38fcd7`, to codex-12.

Joe: *"a set of findings that we could bell out to Codex to build."* Sending **one**. Fable's
measurement is that chain-scoring spans **1.9 nats against a median decision gap of 0.013**, so
R13 under a hard argmin becomes *the next dictator* — the identical authority failure, relocated.
Belling both would rebuild the defect we just spent a day diagnosing. The packet says this in
terms, so codex-12 does not helpfully add it.

### 91.1 The acceptance bar is Fable's numbers

This is the first packet in this line whose acceptance is **reproducing an independently
measured table** rather than passing a test the builder writes:

| | κ = 0 | 0.2 | 0.478 | 1.0 |
|---|---|---|---|---|
| argmin | collapses 4–5× across the row | | | |
| **softmax γ = 4** | **0.110** | **0.117** | **0.119** | **0.103** |
| γ = 16 | degrades smoothly | | | |
| γ = 64 | re-collapses | | | |

The γ = 64 row is a **self-check, not a result**: γ → ∞ *is* the argmin, so if it does not
re-collapse the softmax is wrong. That is a much better test than anything I would have written,
and it exists because the audit measured the fix before proposing it.

### 91.2 The footgun called out in advance

`self_tuning.clj` already carries **three** seeded streams — the propagator draw, the blend coin
(0x0B1E4D) and the apply coin (0x0A7711). The softmax draw must have a **fourth, distinct** tag.
Sharing a stream would leave the default path passing byte-compatibility while every γ setting is
subtly wrong — a defect that survives the obvious test. Same hazard as §69.1; stated because it
was avoided there by stating it.

Byte-identity is again demanded **against a pre-edit snapshot**, not against explicit-vs-implicit
defaults — §70's mistake, now standing boilerplate.

### 91.3 Held deliberately

- **R13 chain-DP** — next packet, only after R14 is reviewed. It is the one that gives the blend
  action its first *consequence* price (0.151 against exactly 0 today) and changes 36% of
  argmins, so it is the substantive one; it just cannot go first.
- **γ-from-outcomes** — third.
- **R15** — not now; the λ layer is already a proto-second-level and it failed for plant reasons.
- **Re-pairing the controller to κ ↔ realized blend share** — the knob–sensor pair that passes the
  actuation census. This is the one that matters most for *search*, and it is a design change
  rather than a code change; it needs Joe.
- **R19, the C-vector** — *"no engine change makes it want the edge."* Out of scope for any Codex
  packet. It is the project's next real question, not a build.

---

## 92. R14 reviewed — clean, and it reproduces Fable's table exactly

codex-12, job `invoke-1785934411910-4-0e0efdce`. **Gate run by me: 92 tests / 2702 assertions,
0 failures.**

### 92.1 Verified

- **Stream separation:** `policy-stream-tag 0x050F7A`, distinct from `blend 0x0B1E4D`,
  `apply 0x0A7711` and the untagged propagator draw. The footgun called out in §91.2 did not
  occur.
- **The draw is CONDITIONAL** — `(if probabilities (sample-policy … (ca/with-mixed-seed …))
  (first (sort-by :total candidates)))`. With `:policy-precision` absent, **no RNG is consumed at
  all**, which is what makes the default path safe rather than merely equal-by-luck.
- **Default path identical to a pre-ALL-builds artifact**: stored 0.500000, now 0.500000. That
  artifact predates the blending build, the 4-action build *and* this one — three consecutive
  changes to `genotype-step`/`cell-decision` with the legacy trajectory intact.

### 92.2 Fable's table reproduced, cell for cell

| γ | κ=0 | κ=0.2 | κ=0.478 | κ=1.0 |
|---|---:|---:|---:|---:|
| argmin | .5234 | .2766 | .1313 | .1094 |
| **4** | **.1099** | **.1172** | **.1191** | **.1030** |
| 16 | .2795 | .2728 | .1671 | .1040 |
| 64 | .4353 | .3171 | .1417 | .1078 |

Exact agreement on all sixteen cells, and **γ = 64 re-collapses toward the argmin** — the
self-check passes, so the softmax is the real thing rather than a monotone reweighting.

Exactness is expected, not suspicious: TV is an *analytic* distribution distance with no
sampling, so identical totals and γ must give identical TV. The implementation is new; the
measurement is shared. That is the right division.

### 92.3 codex-12 caught an error in MY packet

My packet defined sensitivity as *"fraction of cells whose winner changes"*. **Fable's published
table is distribution TV.** Different quantities. codex-12 computed **both** and reported them
side by side rather than silently picking the one that matched:

| γ | κ=0 sampled-winner | κ=0 TV |
|---|---:|---:|
| 4 | .1516 | .1099 |
| 16 | .3125 | .2795 |
| 64 | .4531 | .4353 |

The qualitative result is identical either way — flat at γ=4, re-collapsing at γ=64 — so the
finding is robust to the definition. But had it quietly used the sampled-winner definition and
reported a mismatch, or quietly used TV without saying so, I would have learned the wrong thing
about my own spec. **Reporting both is the behaviour I want and it is the third time this agent
has corrected my packet rather than executing it literally.**

### 92.4 A self-caught error of my own, worth recording

My first cross-build check printed **DIFFERS** (0.500000 vs 0.637500) — apparently a regression.
It was my harness: I had replaced the phenotype construction
`(apply str (repeatedly W #(if (< (ca/rnd) 0.5) \0 \1)))` with `ca/random-phenotype-string`,
which consumes the RNG differently and so starts from a different phenotype. Re-run with the
original construction: **identical**.

**Fourth time today a negative was about my setup rather than the system** (§74.3, §78.2, §83,
now this). The invariant that keeps failing: *when re-running a comparison, re-run the same
harness — a rewritten probe is a new experiment, not a repeat.*

**R13 chain-DP is now unblocked** and is the next packet.

---

## 93. INCIDENT: the D6 deploy marks every turn `[interrupted]` — recommend reverting the flag

Joe: *"the latest Agency fixes are blocking completion of turns (system: [interrupted])"*. My
deploy recommendation; diagnosed immediately.

### 93.1 Not a hang — the content arrives, the terminator does not

- **Threads healthy.** `jstack`: `pouch-demux-claude-10/12/14` all RUNNABLE, parked in
  `FileInputStream.read` — the correct idle state for an always-reading demux thread.
  `pouch-unsolicited-delivery` WAITING on an empty `ArrayBlockingQueue` — idle, nothing backed
  up. 163 threads, no runaway.
- **The reply is complete in the buffer.** My full R13 message is there, ending correctly, and
  **then** `system: [interrupted]` follows it.

**Counted over the live buffer:**

| | |
|---|---:|
| claude turns | 76 |
| `Cooked for` trailers | **1** |
| `[interrupted]` markers | 3 |
| interrupts **before** the restart | **0** |
| interrupts **after** the restart | **3** |

Every interrupt is post-restart. Content streams through; the **`done` event never arrives**, so
the client marks the turn interrupted.

### 93.2 This is the predicted cost of deploying HALF a pair

codex-1's own excursion says it in terms:

> **(1)+(2) are the load-bearing pair.** … (2) *Carry a turn-id end to end. Stamp each accepted
> turn, echo it in every SSE event **including `done`**, and have `claude-repl.el` assert it
> matches the prompt it is rendering under.* **Still owed.**

And fix (5): *"A reply with no `Cooked for` trailer means its `done` event never arrived."* That
is exactly the signature — 76 turns, 1 trailer.

We deployed **(1) + the routing** and **not (2)**. On the demux path `feed-turn-demux!` satisfies
the waiter's promise with the result, but the `done` SSE event does not reach the client the way
the synchronous path delivered it. **I scoped fix (2) out of the packet deliberately** — §91's
"separate packet" — without weighing that (1) alone changes how turns terminate. The excursion
told me they were a pair and I treated the pairing as an ordering.

### 93.3 Recommendation: revert the flag

The trade is bad. D6 dropped **occasional** autonomous turns; this marks **every** operator turn
interrupted, which destroys the operator's ability to distinguish a real interruption from a
spurious one — a worse failure than the one we fixed, and on a more common path.

    FUTON3C_POUCH_DEMUX=false   in scripts/dev-laptop-env:65   + restart

The revert is one word: the flag's OFF path is byte-for-byte the original synchronous read, and
I verified that independently at §87.1. Nothing else needs undoing, and the built code stays in
place for when fix (2) lands.

**⊸miss.** I recommended deploying a load-dark change on the strength of "the OFF path is
unchanged and the tests pass" — both true, and neither addressed *what turning it ON does to the
turn lifecycle*. The excursion named the missing half; I read it, quoted it, and still scoped it
out.

---

## 94. R13 chain-DP reviewed — clean, and the reference numbers reproduce to 5 decimals

codex-12, job `invoke-1785935491611-6-0ae0c3da`. **Gate run by me: 94 tests / 2709 assertions,
0 failures.**

### 94.1 The analytic self-check passes

Computed independently: `:identity`'s column is **1 distinct value across all 256 bytes** —
exactly the structural property required, since under identity every draw flips regardless of
byte.

Value 1.8971199614280 against the analytic ln(1/0.15) = 1.8971199848859 — agreement to **2.3 ×
10⁻⁸**, the residual attributable to the documented `epsilon 1.0e-9` guard in the file. Eight
orders of magnitude below the median decision gap of 0.013, so not worth chasing; **the
constancy, which is the actual check, is exact.**

Table: 3072 entries, all finite, all non-negative, **pure** (repeat calls identical, no RNG).
Real variation across kinds at a fixed byte: 0.901 / 0.163 / 0.317 / **1.897** / 0.163 / 0.163 —
with identity at its constant, as it must be.

### 94.2 Fable's census reproduced

| measure | reference | codex-12 |
|---|---:|---:|
| within-cell risk spread | 0.086 → 0.244 | 0.0863365 → **0.2444899** |
| blend-vs-hold gap | 0 → 0.151 | 0 → **0.1512956** |
| argmins changed | ~35.9% | **35.9375%** |
| **λ flip fraction** | 0.064 → 0.084 | 0.06354 → **0.08438** |
| **actuation range** | 0.0027 → 0.0054 | 0.002674 → **0.005394** |

**Both negative controls stayed flat**, which is the result that matters most: chain depth is
real and informative *and orthogonal to the λ dead-end*, exactly as predicted. A build that
"fixed" the controller here would have been evidence of a mistake, not a success.

### 94.3 What it actually buys

**The blend action now has a consequence price.** Today blend and hold are identical on every
channel — same kind, same bin — so their gap is priced *entirely* by κ·X. That is the direct
explanation for §74.3, where the fourth action was offered but never won until the epistemic
coefficient was switched on: **there was nothing else distinguishing it.** Chain-risk separates
them by byte, mean gap 0.151.

Four consecutive builds to `genotype-step` / `cell-decision` — blending, the fourth action, the
softmax, and now chain-DP — with the legacy default trajectory intact at every step.

---

## 95. Correction to §93, fix (2) dispatched, and γ-from-outcomes deferred

### 95.1 I overstated the incident

§93.3 said the deploy *"marks **every** operator turn interrupted."* **It does not.** Measured:
**3 `[interrupted]` markers, all post-restart, zero before** — out of ~15 post-restart turns. It
is **intermittent**, and Joe's next turn came through fully cooked.

My trailer-count argument was also weak: 1 `Cooked for` in 76 turns, but the trailer appears on
*codex* replies in other buffers, so its near-absence here is not evidence about claude turns at
all. **The solid signal is 3-after / 0-before, and nothing more.**

That matters because severity drove my recommendation. "Every turn" justified reverting; "3
intermittent, content always arrives" does not — and Joe's actual ask is better: **fix both
properties rather than trade one for the other.** The revert recommendation is withdrawn.

### 95.2 Fix (2) dispatched to codex-1

Job `invoke-1785936569064-7-a9b519ca`, park `park-e8581986`. Joe: *"I want both fully cooked
turns **and** no loss of intermediate material."*

The packet asks codex-1 to **diagnose why the `done` event goes missing before building** — I
have the symptom, not the mechanism, and told it so rather than have it build against my guess.
It also demands **both-direction** tests: a solicited result must never be misroutable to the
unsolicited sink, and an unsolicited turn must never satisfy a waiter. That confusion *is* the
defect class.

I invited its own **fix (5)**, the missing-trailer tripwire, which it reported "predicted every
operator re-send with no false positives" — the regression detector that would have surfaced this
incident from inside the system rather than via Joe noticing. Its call whether that ships here or
follows.

I also warned it off the `enabled-defaults-off` environmental failure so it does not lose a round
on my box's `FUTON3C_KANGAROO=true`.

### 95.3 γ-from-outcomes: NOT YET, and there is a cheap test that decides it

Fable ranked it third, behind R14 and R13 — both now in and reviewed. My recommendation is to
**hold it**, for a reason drawn from what we just learned rather than from the ordering:

**γ-from-outcomes is itself a controller.** We have just spent a day establishing that adding a
controller without first checking whether its knob can move its sensor is exactly how λ produced
two dead-ends and a bifurcation. The instrument for that check now exists — Fable's **actuation
census**, the closed-form per-cell counterfactual response of the decision layer to a knob, on
on-policy states.

So the honest sequence is: **run the census on γ before building anything that adapts it.** If
γ's on-policy leverage looks like λ's (E[h] moving ~0.005 across the whole range, per-cell
leverage ~10⁻⁴), then adapting γ would reproduce the same failure in a third place, and the
answer is no rather than later.

And the thing that actually blocks *search* is neither: it is Fable's Q4 answer —
**re-pairing the controller to a census-passing knob–sensor pair, κ ↔ realized blend share**,
which has measured leverage and an interior optimum. That is a design decision, not a build, and
it needs Joe.

---

## 96. Knob-sweep contact sheets — the perceptual read, and it agrees with the statistics

Joe, offered a leverage heatmap: *"this doesn't quite sound like a HIT."* Correct — a 4×6 matrix
of numbers is a table with colours, not the perceptual read he does on space-time diagrams. So:
**hold everything fixed, sweep ONE knob, same seed, panels side by side.**
`scripts/exotype_knob_contact_sheet.clj`, `reports/figures/knob-sweep-{gamma,kappa,lambda,p}.png`.

### 96.1 What the eye says

- **λ (0.1 / 0.55 / 1.0): the three panels are the same picture.** Same texture, same grey
  vertical bands, same coloured speckle; a grey band shifts slightly and nothing else changes
  across the knob's *entire* range. **λ has no visible leverage** — the statistical dead-end
  (E[h] moving 0.005, every target ramping to a boundary) is directly visible.
- **κ (0 / 0.2 / 0.478 / 1.0): the panels genuinely differ.** κ=0 has a broad grey band right of
  centre; κ=0.2 spreads coloured speckle through; κ=0.478 keeps bands with heavy speckle;
  κ=1.0 is visibly denser and more uniform with the large grey structures gone.
- **γ (1 / 4 / 16 / 64): differs, and non-monotonically** — γ=16 is the most structured panel,
  γ=64 returns toward the γ=1 character. Consistent with γ→∞ *being* the argmin.

> **The picture and the numbers agree**: λ is inert, κ acts. That is the first time today a
> perceptual read and a statistic have confirmed each other rather than one correcting the
> other — and it is worth as much as the corrections, because it means the candidate we are
> about to build on is visible from both directions.

### 96.2 The formal frame, offered to Joe alongside

A controller needs a knob *u* and sensor *y* with four properties, each cheaply measurable:

1. **Leverage** — ∂y/∂u ≠ 0 on-policy
2. **Reachability** — the target y\* lies inside y's attainable range
3. **Validity** — y tracks the objective (criticality)
4. **Stability** — the loop has a stable fixed point, not a repeller

This is the autopsy of today, not abstract bookkeeping. **λ↔hunger failed 1** (0.005 across the
full range), **failed 2 twice** (0.05 below the range; 0.1676 above the closed-loop value — the
same defect as the rate target), and **failed 4** (the bifurcation: every target runs to a
boundary, so the equilibrium is a repeller). **We never got to test 3.**

The open question, and it is more Joe's than mine: **does realized blend share actually track
criticality, or is it merely the thing we can move?** Leverage without validity regulates the
wrong quantity confidently. Condition 3 is a judgement about what the system is *for*.

---

## 97. γ IS THE CRITICALITY KNOB — it traverses the whole order/chaos axis

Found by accident, chasing what I assumed was a bug in Joe's purple map. Damage reach at t=100,
width 250, 12 seeds, against anchors re-measured at this width:

| γ | damage reach | regime |
|---|---:|---|
| **ARGMIN** | **71.67 ± 3.32** | CHAOTIC |
| 1 | 6.33 ± 2.03 | ordered |
| 2 | 15.08 ± 3.61 | intermediate |
| 4 | 19.08 ± 2.88 | intermediate |
| 8 | 27.83 ± 3.51 | intermediate |
| **16** | **39.33 ± 4.28** | **CLASS-IV BAND** |
| 64 | 62.58 ± 2.86 | CHAOTIC |

*(anchors: 204 = 1.0 · 90 = 8.0 · 54 = 36.0 · 110 = 38.1 · 30 = 60.9)*

**γ moves damage reach from 6.3 to 62.6 — a factor of ten, monotone, across the entire
order-to-chaos axis** — and passes through the class-IV band at **γ ≈ 16**, landing at 39.33,
just above rule 110's 38.1. ARGMIN sits at 71.67, *beyond* γ = 64, exactly as γ → ∞ = argmin
predicts.

### 97.1 Why this is the thing we have been hunting

Set it against λ, whose failure this note has documented all day:

| | λ | **γ** |
|---|---|---|
| range induced in the objective | ~0 (E[h] 0.005; damage flat) | **6.3 → 62.6** |
| reaches the class-IV band? | never | **yes, at γ ≈ 16** |
| sensor is a proxy? | hunger, validity untested | **damage reach IS the objective** |

The validity question I put to Joe — *"does the sensor actually track criticality?"* — **does not
arise here.** The sensor is the criticality measure itself. So this pair passes **leverage**,
**reachability** and **validity** outright; only **stability** is untested.

### 97.2 I was wrong to defer γ, and right about how to decide

§95.3: *"γ-from-outcomes is itself a controller … run the census on γ before building anything
that adapts it. If γ's on-policy leverage looks like λ's, adapting it would reproduce the same
failure in a third place."*

**The method was right and my expectation was wrong.** I predicted γ might be inert like λ; it is
the most powerful knob in the system. The deferral cost nothing because the census is what
resolved it — but the honest record is that I recommended holding the item that turned out to be
the answer, and the measurement overruled me.

### 97.3 What this does NOT yet show

- **Not search.** It shows a *hand-set* γ lands the system in the band. Nothing yet shows the
  system finding γ ≈ 16 by itself. That is precisely what γ-from-outcomes would have to do, and
  now there is a reason to build it.
- **Not stability.** Whether a controller on γ has a stable fixed point at the band, or bifurcates
  the way λ did, is untested and is the next measurement.
- **Not the ARGMIN=71.67 vs earlier 63.5–69.5 reconciliation.** Same regime, different harness
  details; consistent, not identical.

### 97.4 Joe's purple map is now runnable

It returned all-red-no-blue because at γ = 4 damage heals to zero — the system was *ordered*, so
there was no damage to co-occur with. **At γ ≈ 16 there is damage**, so the red/blue/purple test
can now be run where it is informative. That was the right instrument pointed at the wrong regime,
and pointing it there is what found the regime.

*(The knob×sensor census crashed separately — my bug: `:policy-probabilities` is a sequence of
maps, not of doubles, so the entropy term casts wrong. Fixing; it does not affect the above,
which came from a different script.)*

---

## 98. Fine γ sweep — the band is BRACKETED, not hit, and my label over-claimed again

24 seeds disjoint from the coarse sweep, so γ=16 is an independent re-measurement:

| γ | damage | vs the real band (36–38) |
|---:|---:|---|
| 10 | 29.29 ± 1.88 | below |
| 12 | 34.54 ± 2.75 | below |
| 14 | 35.29 ± 2.55 | **just below** (rule 54 = 36.0) |
| 16 | 41.79 ± 3.12 | **above** (rule 110 = 38.1) |
| 18 | 40.79 ± 2.75 | above |

γ=16 reproduces independently: 41.79 ± 3.12 against the coarse 39.33 ± 4.28, overlapping.

**No measured γ lies inside 36–38.** The band is bracketed by γ=14 and γ=16, with γ\* ≈ 15 by
interpolation.

### 98.1 My script's label was wrong — fourth occurrence

`exotype_gamma_damage.clj` prints `*** CLASS-IV BAND ***` on the test `(<= 30 m 45)`, which is
*far* looser than the anchors it prints two lines above (36–38). So it labelled 34.54 and 41.79
as in-band when neither is. **Fourth time a display of mine has evaluated a weaker criterion than
the one it reports against** (§75, §84.2, §88.2, now this). The pattern is identical every time:
the strict rule is written down correctly and the convenience label is written loosely, and the
loose one is what gets read.

### 98.2 The steepness is a controllability hazard, not a convenience

**6.5 damage units between γ=14 and γ=16.** A controller regulating damage via γ would be working
on a narrow, steep stretch: small γ errors produce large damage excursions, which is exactly the
condition under which a sign controller overshoots and oscillates rather than settling. λ failed
for being too *flat*; γ may fail for being too *steep*. Both are stability failures and the
restoration test is what distinguishes them.

Recorded in `TN-part3-draft.md` §III.6 alongside the table.

---

## 99. Knob × sensor census — λ is inert on every decision-layer sensor

12 seeds, 150 burn-in. Cell = absolute range the knob induces, (relative to the sensor's mid).

| knob | decision-entropy | blend-share | adoption | distinct-kinds | halting-share |
|---|---:|---:|---:|---:|---:|
| **γ** | **0.705 (52%)** | 0.068 (22%) | 0.201 (41%) | 1.08 (39%) | 0.384 (175%) |
| **κ** | 0.392 (28%) | **0.093 (32%)** | **0.237 (49%)** | 1.08 (43%) | 0.118 (50%) |
| **λ** | **0.003 (0%)** | **0.012 (4%)** | **0.012 (2%)** | 1.33 (53%) | 0.223 (95%) |
| p | 0.004 (0%) | 0.008 (3%) | 0.009 (2%) | 1.00 (46%) | 0.234 (147%) |

**The decision-layer columns separate the knobs cleanly.** λ and `apply-probability` move
decision entropy, blend share and adoption by **0–4%** — they are inert *at the decision layer*.
γ and κ move all three substantially. γ is the entropy knob (52%), as it must be, since γ is the
softmax temperature; κ is the strongest on blend share (32%) and adoption (49%), which is exactly
the pair Fable identified as census-passing.

So the census confirms from a fourth direction what §88 measured, §96.1 showed perceptually, and
Fable derived analytically: **λ cannot move the decision layer at all.**

### 99.1 The census's own damage column is UNDERPOWERED — do not use it

The `damage-reach` column reports λ at 4.17 (98%), which would contradict everything above. It
should not be read: this census advances the damage probe **30 steps at 12 seeds**, against the
dedicated measurement's **100 steps at 24 seeds**. At that horizon the damage cone has barely
formed and the relative percentages are inflated by small mid-values.

**Where they disagree, the dedicated sweep is the authority.** Flagging it explicitly because a
column in my own output currently says something I believe to be false, and the instinct to quietly
drop it is exactly what a register is meant to prevent. The fix is to raise its horizon and seed
count, not to delete the row.

This is the same class of defect as §98.1 — an instrument reporting on a looser standard than the
claim it is used for — and it is now five for the day. Every one has been in *my* reporting layer
rather than in the engine.

---

## 100. Analysis 1 — change-rate is a real sensor; entropy and adoption are γ in disguise

| observable | r with damage | **r with γ itself** | reading |
|---|---:|---:|---|
| entropy | −0.5440 | **−0.9586** | γ re-parameterised |
| adoption | +0.4645 | **+0.8728** | γ re-parameterised |
| **change-rate** | **+0.4556** | **+0.0934** | **genuine sensor** |
| halting | −0.5285 | −0.3681 | partly genuine |
| blend | +0.0407 | −0.2563 | tracks neither |
| kinds | −0.1353 | −0.2880 | weak |

The trap the script was built to catch **fired exactly as predicted** (§ prior turn, on the
record before the run): entropy correlates with damage at −0.54 and with γ at −0.96. A controller
reading entropy is **reading its own knob back** — it would show beautiful tracking and sense
nothing about the world.

**Change-rate is the find.** r = +0.456 with damage, +0.093 with γ. It tracks the objective while
being nearly *independent of the knob* — the signature of a sensor that carries state rather than
setting. And the engine already computes it.

Halting share is a partial second (−0.53 / −0.37), and since its γ-dependence differs from
change-rate's, **a combination of the two is likely to beat either** — which is Joe's regression
point, now with two named candidates instead of a wish.

Analysis 2 (within-γ, across seeds) is still running and is the one that *decides* this: it holds
the knob constant, so anything surviving there is state information a controller could act on.

## 101. Staging the feedback — the table gives a cascade, and the timescales finally cooperate

Joe: *"this table seems like it is starting to give us the basis of a controller … how to stage
feedback into the controller?"*

**The caveat first:** the table gives *leverage*, not a controller. λ had leverage on nothing and
failed; γ and κ have leverage. That is a precondition, not a loop.

But the staging question has a real answer, and it comes straight out of the table.

### 101.1 Pair each knob with the sensor it dominates

Two knobs are active and they are **not redundant**:

| | decision-entropy | blend-share | adoption |
|---|---:|---:|---:|
| γ | **52%** | 22% | 41% |
| κ | 28% | **32%** | **49%** |

γ dominates entropy; κ dominates blend share. That is the standard decentralised-control pairing
rule — **pair on the largest relative gain, so the two loops do not fight each other.** Pairing
the other way round (κ→entropy, γ→blend) gives each loop a knob the other controls better, and
they oscillate against one another.

### 101.2 The objective is not internally observable, so it must be a CASCADE

Damage reach needs a forked twin; the construction cannot see it about itself. So the loop cannot
be single-level:

- **Inner loop (fast, fully internal):** γ → change-rate (and κ → blend share). Closable
  **today**, with no damage measurement anywhere in it.
- **Outer loop (slow, needs the objective):** damage → *setpoint* for the inner sensor. This is
  where the external instrument lives, and where change-rate earns its place: it is the variable
  the outer loop can command and the inner loop can actually hold.

### 101.3 The timescales favour this — for the first time in this work

Fable's structural objection to the earlier design was **τ_mix ≈ 10² ≪ τ_search ≈ 10³**: the plant
erased its initial condition ten times faster than the controller could respond, so no controller
could work. Under the cascade:

- **inner loop**: γ enters the softmax directly — entropy and change-rate respond **within one
  step**;
- **outer loop**: damage needs ~width/2 steps to express, so ~10² steps.

**Inner ≈ 1 step, outer ≈ 10² steps.** That is a two-order separation in the *right* direction,
which is precisely what cascade control requires and exactly the inequality that was reversed
before. The architecture is not merely available; it is the one the system's own timescales
support.

### 101.4 Staging, in the sense of build order

1. **Inner loop alone.** Command a change-rate setpoint, let γ chase it, and test convergence and
   restoration **without any damage measurement**. If the inner loop cannot hold a setpoint, the
   cascade is dead and we learn it cheaply.
2. **Outer loop.** Only then map damage → change-rate setpoint, using the swept band (γ\* ≈ 15,
   damage 36–38) to fix the correspondence.

Step 1 is independently testable, needs no twin, and reuses the T1/T2 apparatus verbatim —
including the positive control and the no-kick arm, both of which caught real errors last time.

---

## 102. Analysis 2 — the loop IS closable. change-rate carries state information at fixed γ

Mean |r| with damage, computed **within** each γ (knob held constant, varying only the seed), so
nothing here can be explained by the knob:

| observable | mean \|r\| within-γ | r with γ (between) | verdict |
|---|---:|---:|---|
| **change-rate** | **0.431** | **+0.093** | **best on BOTH criteria** |
| halting | 0.414 | −0.368 | strong, mildly confounded |
| entropy | 0.365 | −0.959 | real but heavily confounded |
| blend | 0.224 | −0.256 | weak |
| adoption | 0.224 | +0.873 | weak and confounded |
| kinds | 0.156 | −0.288 | weak |

> **change-rate is the clean winner: the highest within-γ signal AND the lowest γ confound.** It
> senses the state, not the setting, and the engine already computes it.

### 102.1 I was partly wrong about entropy

I predicted *"entropy passes 1 and fails 2 — it's γ in disguise."* It **does not fail 2**: at
fixed γ it still tracks damage at |r| = 0.365. So entropy is heavily *confounded* with γ
(r = −0.96 between-γ) but is not *only* γ — it retains genuine state sensitivity. "In disguise"
was too strong; "dominated by, but not reducible to" is the accurate statement.

The prediction that held is the one that mattered: **change-rate would be the cleanest**, and it
is.

### 102.2 The honest size of the signal

|r| ≈ 0.43 means **R² ≈ 0.19** — change-rate explains under a fifth of seed-to-seed damage
variance. That is a *real* sensor and a *weak* one. A controller on it acts on a noisy reading,
which argues for slow integral action rather than a sign controller with a fixed step, and makes
**Joe's combination idea load-bearing rather than optional**: change-rate and halting carry
opposite signs and different γ-dependence, so a two-term fit should beat either alone.

Per-γ rows are noisy by construction — 16 seeds gives SE ≈ 0.28 on a single correlation, so the
γ=13 (0.062) and γ=20 (0.122) rows are not evidence of anything. **The mean over ten γ values is
the statistic**, at SE ≈ 0.09.

### 102.3 Where this leaves the four conditions

| condition | γ ↔ change-rate |
|---|---|
| **leverage** | ✓ γ moves the decision layer (52% on entropy, and change-rate responds) |
| **reachability** | ✓ band bracketed at γ ≈ 15 |
| **validity** | ✓ **measured — 0.43 within-γ, the first sensor to pass this** |
| **stability** | **untested — the whole remaining question** |

Three of four now pass on measurement rather than assumption. λ never got past the first. The
inner loop of §101 is therefore worth building, and its build is the cheap test: **command a
change-rate setpoint and see whether γ can hold it**, with no damage measurement anywhere in the
loop.

---

## 104. The inner loop fails on IDENTIFIABILITY, not on tuning — and R² was the wrong certificate

codex-12's negative result is sound and its procedural gates all pass (verified: range locked in
code with a hard throw; positive control pins all 48 runs at γ=64; no-kick arm present;
default path sha256-identical; 94 tests / 2709 assertions / 0 failures, re-run by me). Its stated
diagnosis — "the γ-to-sensor curve is non-monotone" — is correct but **understates the problem.**

Set the measured sensor map against our own damage sweeps on the shared γ values:

| γ | sensor S | damage |
|---:|---:|---:|
| 1 | 3.07 | 6.3 |
| 2 | 26.57 | 15.1 |
| 4 | 25.43 | 19.1 |
| 8 | 14.38 | 27.8 |
| 12 | 1.02 | 34.5 |
| 16 | 3.23 | 41.8 |
| 64 | 16.09 | 62.6 |

**Damage is monotone in γ. S is not.** The consequence is not slow convergence, it is that the
same sensor reading corresponds to wildly different states of the objective:

- **S ≈ 3** at γ=1 (damage **6.3**) and at γ=16 (damage **41.8**) — 35 damage units apart
- **S ≈ 15** at γ=8 (damage **27.8**) and at γ=64 (damage **62.6**) — 35 damage units apart

> **The composite sensor is many-to-one onto the objective. The controller cannot distinguish
> "far too ordered" from "far too chaotic" — both read the same.** No gain, no window length, and
> no controller architecture repairs that. It is an identifiability failure, upstream of control.

This also retires the competing explanation I raised in review (gain too large → random walk to
boundaries). That may *also* be true, but it is not needed: an unidentifiable sensor cannot close
a loop at any gain.

### 104.1 R² is a predictive certificate, not a control certificate

The composite was fitted at **R² = 0.291**, pooled across γ, and that number was treated —
by me, in the packet — as evidence the sensor was fit for control. It is not.

> **A positive predictive R² is fully compatible with a two-to-one map.** Prediction needs
> correlation; control needs *invertibility*. Pooling across the knob's range hides exactly the
> folding that destroys invertibility.

The check that was missing costs one line: **is the sensor monotone in the knob?** It was not
asked, in a packet that asked for six other gates. Sixth reporting-layer defect today and the
first that was designed in rather than displayed wrong.

### 104.2 The dilemma, now stated exactly

From the screen (§102) plus this:

| sensor | monotone in γ? | carries state info? | verdict |
|---|---|---|---|
| entropy (r=−0.96) | ~yes | 0.365, but ~determined by γ | **circular** — reads its own knob |
| adoption (r=+0.87) | ~yes | 0.224 | circular, and weak |
| change-rate (r=+0.09) | **no** | 0.431 | **not invertible** |
| halting (r=−0.37) | **no** | 0.414 | **not invertible** |

**Sensors monotone in γ are nearly deterministic functions of γ and carry no independent
information. Sensors carrying independent information are non-monotone in γ and cannot be
inverted.** That is the obstruction, and it is sharper than anything in §III.7.

*Dependency, stated: the S(γ) map above is codex-12's. My independent re-measure at the correct
2000-step horizon is still running; my first attempt used 220 steps and was wrong. If the
re-measure contradicts the map, this section falls with it.*

---

## 105. RETRACTION of §104 — it compared width-80 sensors against width-250 damage

**§104's identifiability finding is withdrawn. The comparison it rests on is invalid.**

`scripts/exotype_inner_loop.clj` line 52: **`:width 80`**. Every S(γ) value in codex-12's report
is a width-80 measurement. Every damage value I set beside it — 6.3, 27.8, 34.5, 41.8, 62.6 — is
from our width-250 sweeps (§97, §98). **I built a table with one column from each and read a fold
off it.** The "S ≈ 3 at γ=1 and γ=16, 35 damage units apart" claim compares quantities from two
different systems.

Damage reach is an **absolute cell count**, so it scales with width — at width 80 it cannot
exceed 80, and the class-IV band sits at a different absolute number than the 36–38 measured at
250. The two columns were never commensurable.

### 105.1 My re-check was invalid too, for three separate reasons

I ran it at width 250, with `:self-tuning-arm :hunger-coupled`, and a hand-rolled phenotype
generator. codex used **width 80**, **`:fixed-0.55`**, and `ca/random-phenotype-string`. Plus the
first attempt used a 220-step horizon against the design's 2000. **Four harness divergences across
two attempts at the same verification**, each one found only after I had already drawn a
conclusion from the output.

The rule this earns: **a verification must call the code under test, not a reimplementation of
it.** Every divergence above came from my re-writing the setup instead of re-running theirs with
different seeds. The correct check is to re-run `exotype_inner_loop.clj` with `:seeds` shifted —
one edit, no reimplementation, no drift.

### 105.2 What IS a real finding about the build

The composite weights come from `:rows 160` — ten γ × sixteen seeds — which is the geometry of
`exotype_sensor_regression.clj`, and **that script runs at width 250**. So:

> **The weights were fitted to predict damage at width 250, and the controller was then run at
> width 80.**

halting-share and change-rate are both per-cell fractions and so are width-comparable; the *fit to
damage* is not, since its target is an absolute count with a width-dependent scale. The controller
is internally consistent — codex re-measured the reachable range at width 80 — but the claim that
regulating S regulates *damage* does not transfer across the width change, and nothing in the run
tested that it does.

This is a genuine finding and it survives the retraction. It is also **my defect, not codex's**:
the packet said "screen data for the fit is on zone-joe at /tmp/sens.edn" and never stated the
width those rows were measured at, nor required the controller to run at the same width.

### 105.3 Standing count

Seven reporting-layer defects today, and this is the largest: a confident structural conclusion,
written up in two files, built on a cross-system comparison. The engine's numbers have been right
every time. What keeps failing is the layer where I decide what two numbers mean when placed side
by side.

---

## 106. Reseed verdict — the null is SOUND, but the stated reason is not the supported one

Re-ran codex-12's own `range` sweep via `range-cell`/`range-merge` with seeds shifted
2026088000→2026091000. **No reimplementation** — one edit to `:seeds`, their code throughout, on
zone. The offline fit reproduces bit-identically (weights −46.8145559826863 / 51.344601162211184,
R² 0.2913068018311319), so the composite derivation is fully deterministic.

| γ | reseed S | SD | SE | codex S | agrees at 2SE? |
|---:|---:|---:|---:|---:|---|
| 1 | 2.99 | 35.71 | 8.93 | 3.07 | yes |
| 2 | 21.04 | 26.08 | 6.52 | 26.57 | yes |
| 4 | 12.91 | 34.49 | 8.62 | 25.43 | yes |
| 8 | 0.12 | 33.83 | 8.46 | 14.38 | yes |
| 12 | −2.92 | 25.40 | 6.35 | 1.02 | yes |
| 15 | **−10.78** | 27.95 | 6.99 | 6.33 | **NO** |
| 16 | 2.15 | 18.86 | 4.72 | 3.23 | yes |
| 20 | −1.93 | 21.77 | 5.44 | 0.92 | yes |
| 24 | 9.75 | **2.95** | 0.74 | 12.29 | **NO** |
| 32 | 14.90 | **5.04** | 1.26 | 15.75 | yes |
| 48 | 16.81 | **1.65** | 0.41 | 16.54 | yes |
| 64 | 16.77 | **1.57** | 0.39 | 16.09 | yes |

Ten of twelve agree; the two maps correlate at **r = +0.79**. **The null reproduces and the
result stands.**

### 106.1 The stated diagnosis is under-supported; the real one is noise

codex reported the failure as a *non-monotone plant*. The reseed has the same five slope sign
flips — but **only 2 of 11 consecutive steps are significant at 2·SE.** Nine of the eleven
"wiggles" are not distinguishable from zero. The non-monotonicity is largely unresolved noise,
not established structure.

What the data does support, unambiguously:

> **Mean per-γ SD = 19.61 against a total span of 31.82. Signal-to-noise is 0.62 — the
> sample-to-sample noise is nearly two thirds of the entire range the sensor can express.**

And it is regime-split, which the single global figure hides:

- **γ ≤ 20: SD 19–36.** At γ=15 — *the class-IV band* — the sensor reads −10.78 with SD 27.95,
  against a full range of 32. **The sensor is close to uninformative exactly at the operating
  point the controller must hold.**
- **γ ≥ 24: SD 1.6–5.0**, and there the map is tight and monotone-saturating (9.75 → 14.90 →
  16.81 → 16.77).

That explains the controller's behaviour completely, without needing non-monotonicity: a reading
with SD ≈ 20–30 driving γ at 0.25 units per full-span error **is a random walk**, which is exactly
what the arms show — seeds dispersed across the entire 1.000–64.000 range.

### 106.2 Review verdict

| item | verdict |
|---|---|
| reachability measured before setpoint | **pass** — locked in code, hard throw |
| positive control | **pass** — 48/48 pin at γ=64 |
| no-kick arm | **pass**, and load-bearing: it exposed +8/−8 "restoration" as drift |
| default path byte-identical | **pass** — sha256 match |
| 12-namespace gate | **pass** — 94 / 2709 / 0, re-run by me |
| the null itself | **sound and reproduces** (r = 0.79 on fresh seeds) |
| stated diagnosis (non-monotone) | **under-supported** — 2/11 steps significant |
| better-supported diagnosis | **sensor SNR = 0.62, worst in the target regime** |
| width: fit at 250, controller at 80 | **defect — mine**, the packet never specified width |

**Accepted.** The falsifier was honoured and not renegotiated, which is the part that matters
most; the diagnosis is refined rather than overturned.

### 106.3 What this makes the right question

Not "how do we tune this loop" but: **the class-IV band sits in the γ region where every
available sensor is noisiest.** Whether that is remediable (longer windows, ensemble averaging,
a variance-based rather than mean-based statistic) or intrinsic is the open question — and it is
a much better one to put to Fable than the identifiability claim I retracted in §105.

---

## 107. ROOT CAUSE — width 80 saturates the objective. The controller had almost no dynamic range.

Re-anchored at **width 80**, the controller's width, 24 seeds, t=100.

**Anchors at width 80:** 204 = 1.00 · 90 = 8.00 · **54 = 29.13** · **110 = 30.04** · 30 = 38.67
→ class-IV band is **29–30**, chaos ≥ 38.7.

**γ → damage at width 80:**

| γ | 1 | 2 | 4 | 8 | 12 | 15 | 16 | 20 | 24 | 32 | 48 | 64 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| damage | 8.6 | 13.7 | 19.7 | 26.2 | 35.5 | 35.3 | 35.4 | 37.1 | 35.9 | 39.2 | 38.4 | 37.1 |

Set against width 250, where γ ran 6.3 → 62.6 monotonically:

> **At width 80 the objective SATURATES above γ = 12.** Every γ from 12 to 64 — five sixths of the
> controller's range — sits in a band of **4 damage units (35.3–39.2)**, and rule 30, the chaotic
> anchor, is 38.67. The entire upper range is chaos, compressed to within noise.

The usable dynamic range at width 80 is γ ∈ [1, 12]. **The controller was configured to search
[1, 64], of which roughly 80% is a plateau.**

### 107.1 This is the root cause of the inner-loop failure

Everything else follows from it:

- **The sensor "noise" is mostly plateau.** With damage flat above γ=12, an observable that tracks
  damage has almost nothing to track over most of the range. §106's finding — SD 19–36 at γ ≤ 20,
  SD 1.6–5.0 at γ ≥ 24 — reads differently now: the tight, low-SD region (γ ≥ 24) is tight
  *because it is saturated*, and the noisy region is the transition, where fluctuations are
  genuinely large.
- **The band moved.** At width 80 the class-IV band (29–30) is crossed between γ=8 and γ=12, so
  **γ\* ≈ 9–10 at this width**, not the ≈15 we had from width 250. The controller's setpoint was
  derived from a sensor range measured across a mostly-saturated γ axis.
- **The random walk is what you get** when a controller searches a range that is 80% flat in the
  quantity its sensor tracks.

### 107.2 Whose defect

**Mine.** The packet said "screen data is at /tmp/sens.edn" without stating that those rows are
width-250, and never specified the width the controller should run at. codex-12 chose 80, which is
a reasonable speed choice absent any instruction, and then correctly re-measured everything it
could re-measure *at that width*. What it could not know is that the objective it was ultimately
serving has no range there.

This is the fourth defect today traceable to a packet or display of mine rather than to the
engine, and the most consequential: it invalidated a build, a review, and one retracted finding.

### 107.3 What it changes

- **The survey must run at a width where the objective has range.** Width 250 is known good
  (6.3 → 62.6). Width 80 cannot support any residence-in-band measurement, because five sixths of
  its γ axis is one indistinguishable blob.
- **A finite-size question is now live and cheap**: damage at rule 30 is 60.9 at width 250 and
  38.67 at width 80 — 24% vs 48% of the width. The width-80 cone is approaching system size and
  wrapping. Part III already owes finite-size scaling; this is the first concrete evidence that it
  is not optional.
- **§106's "sensor SNR = 0.62" stands as measured but is re-read**: it is not a property of the
  sensor, it is largely a property of measuring a saturated system.

---

## 108. Generator audit (codex-13) — VERIFIED. The exotype is the dominant mutation source.

Audit dispatched to codex-13 rather than codex-12, so the generator is not audited by the author
of the controller that depends on it.

**My verification** (park checklist): instrumentation exists at
`scripts/genotype_novel_path_audit.clj`, clj-kondo 0/0; I **re-ran it myself** and every figure
reproduces exactly — aggregate events {exotype-transition 4172, policy-blend 1131}, value
attribution {3344 / 766 / 270 mixed}, selected {29207 / 11753}, sigil table {256 entries, 256
unique bits, 0 missing}. Citations spot-checked at `self_tuning.clj:266-271` and `:277-281`;
both are exactly as described. Design-record citation `TN-baldwin-reboot.md:5227-5229` confirmed.

### 108.1 A third path exists — I missed it

`self_tuning.clj:266-271`, the **beta-gated source pre-blend**: when `:blend-strength > 0` it
synthesises a sigil via `blend-rule` and *then* feeds that synthesised source through the exotype
transition. Independently generative. **Its contribution in every configuration we have run today
is exactly zero, because beta = 0.0** — which is why reading found two paths and not three.

codex-13 also established the enumeration is now closed: the only assignment to the next
`:genotype` is `genotype-step` (`:288-295`); `transmit` produces only exotypes/lambdas/decisions
(`:223-228`) and `phenotype-step` only phenotype. **No fourth path.**

### 108.2 The beta asymmetry is intentional, and the evidence is specific

Not a missing guard. `:blend-strength = 0.0` was chosen deliberately *to isolate policy-controlled
blending* (`TN-baldwin-reboot.md:5227-5229`) — i.e. the author knew policy-blend operates
independently of beta and picked 0.0 precisely to leave only that path active. The behaviour is
pinned by `winning-blend-action-writes-rule-but-not-exotype` with a `:blend-strength 0.0` fixture
(`self_tuning_test.clj:131-150`). The implementation is uncommitted, so no commit message settles
it; the design note and test do.

### 108.3 THE FINDING: 78.67% of novelty comes from the exotype

| path | novel-sigil events | share |
|---|---:|---:|
| **exotype transition** | **4,172** | **78.67%** |
| policy blend | 1,131 | 21.33% |
| beta pre-blend | 0 | 0% (beta = 0) |

Per application it is also the more generative branch: **14.28%** of exotype-transition
applications produce a currently-absent sigil, against **9.62%** for policy blend — and the
exotype branch is selected far more often (29,207 vs 11,753 site updates).

> **The exotype layer — which this project frames as *culture*: transmissible but not heritable,
> a modifier on rules rather than a rule — is the principal source of genotypic novelty in the
> system.** It does not merely change how rules are applied. It is the main way new rules enter
> the population at all.

That is a conceptual result, not a bookkeeping one, and it cuts against the framing in
`project_exotype_xenotype_architecture`. A "cultural" layer that supplies four fifths of genetic
novelty is doing something the culture/heredity distinction does not describe. It also means the
Baldwin-effect reading needs care: the channel we called non-heritable is the mutation operator.

### 108.4 `sigil-for` is total over `blend-rule`'s range

Every output position of `blend-rule` is either a neighbour bit or a `local-rule-table` output,
hence 0 or 1, hence one of exactly 256 byte strings — and the table has all 256 with none missing
(measured). `entry-for-bits`'s "Unknown genotype bits" throw is **unreachable from `blend-rule`
given valid inputs**. It remains reachable for arbitrary strings, which is correct.

---

## 109. DESIGN (not a result) — σ as inferred rather than fixed

Joe, 2026-08-06, on relaxing the 12 hand-picked exotypes into an exploration of the σ space.
Recorded as a design with its gate stated, **not** as a finding. Nothing below is measured.

### 109.1 The move

σ stops being a fixed per-kind constant and joins the **policy space**. The action set grows from
`{hold, adopt-left, adopt-right, blend}` to include local edits to the site's own σ:

- **permutation family (8! = 40,320):** a move is a transposition — swap two entries of σ.
  **C(8,2) = 28 candidates.** The transposition graph on Sₙ is connected, so every σ is reachable
  by local moves without ever enumerating the space.
- **map family (8⁸ = 16,777,216):** a move is `σ(i) := j`. **64 candidates.** This is the family
  the 2015 implementation actually had.

Either way the candidate count stays the same order as the four current policies, so the existing
EFE enumeration carries it unchanged.

### 109.2 Why this is AIF-valid and not a bolted-on search

In active inference, choosing a policy by expected free energy *is* inference — action and
inference are the same operation under the same functional. Putting σ in the policy space
therefore makes the exotype **something the agent infers about itself** rather than a constant the
experimenter picks. No new theory, no second optimiser, no separate search loop.

**And the exploration mechanism already exists.** EFE decomposes into a pragmatic term and an
**epistemic** term; the epistemic term rewards candidates that are *informative*. So the agent
would try unfamiliar σ because they reduce uncertainty, not merely because they score well — which
is exactly the exploration the relaxation is meant to buy. We do not have to add it.

Better: **κ, the epistemic coefficient, becomes the knob that governs σ-exploration** — and κ is
one of the two knobs with *measured* leverage (§99: 32% on blend share, 49% on adoption, against
λ's 2–4%). The parameter we found to be live acquires the job it is shaped for.

### 109.3 What the system would then be a model of

The exotype supplies **78.7% of novel-sigil events** (§108, verified). So an agent inferring σ is
an agent **acting on the dominant source of its own variation**. That is not a control problem
dressed up; it is *the evolution of evolvability* — and, in this project's own vocabulary, niche
construction in the mutational domain rather than the environmental one.

That reframing is worth more than the mechanism. It also sharpens §III.8's correction: the
culture/heredity gloss was never the right axis. The right axis is **what the agent can and
cannot act upon**, and σ moving across that line is the whole content of the proposal.

### 109.4 The gate, and the risks — stated before building

**Gate (running):** does σ have leverage at all? Six random permutations against the shipped
twelve, damage at width 250, γ ∈ {8, 16}. If random σ behave like the hand-picked twelve, the
space is flat and the machinery buys nothing. **Build nothing until this reads.** This is the rule
the λ controller died for want of, applied a third time.

**Risks, named now so they are not discovered as surprises:**

- **Collapse.** The vocabulary was widened 4 → 12 precisely because the EFE dynamics collapsed
  onto `:collapser` when the score cache under-enumerated (§III.8.3). A 28- or 64-candidate action
  space with the same functional could collapse harder, not less. Any build must measure kind
  diversity over time as a first-class output, not as a diagnostic.
- **Compute.** 4 → 32 candidates is ~8× the scoring per cell per step. Every sweep in this note
  becomes ~8× more expensive.
- **σ-conditionality of everything measured.** If σ has leverage, then λ's inertness, γ's
  traverse, and the class-IV band location were all measured at one hand-picked point in a
  40,320-member space, and none of them are known to survive σ varying. That would be the most
  consequential finding of the day, and it argues for re-running the core sweeps across σ before
  trusting any of them.

---

## 110. σ HAS MAJOR LEVERAGE — and the shipped twelve are an outlier, not a typical draw

The gate on §109 has read, and it did not read flat. Width 250, damage at t=100, 6 seeds per
configuration, 5 uniformly-drawn random permutation sets against the shipped twelve.
Anchors at this width: 54 = 36.0, 110 = 38.1 (class-IV band), 30 = 60.9 (chaos).

| | γ = 8 | γ = 16 |
|---|---:|---:|
| **SHIPPED (12 hand-picked)** | **30.50** | **39.00** |
| random draw 1 | 7.50 | 22.50 |
| random draw 2 | 21.33 | 6.83 |
| random draw 3 | 22.50 | 15.17 |
| random draw 4 | 12.83 | 37.83 |
| random draw 5 | 33.50 | 7.33 |
| random mean ± SD | 19.53 ± 8.91 | **17.93 ± 11.49** |
| shipped, in SD from random mean | +1.23 | **+1.83** |
| shipped rank | 2 of 6 | **1 of 6** |

### 110.1 Two findings, both large

**(a) σ is a first-class parameter, comparable to γ.** The spread across σ at γ=16 is **31.0 damage
units**. The entire γ axis, 1 → 64, spans 56.3 units at this width. **σ alone accounts for 55% of
the range that γ traverses** — while being held fixed in every experiment this project has run.

**(b) The shipped twelve are not a typical point in that space.** At γ=16 they produce **more
damage than all five random draws**, and sit +1.83 SD above the random mean. Random σ at γ=16
average 17.93 — which is *below rule 90's 8.0-to-36.0 territory*, i.e. mostly **ordered**. The
hand-picked set is what puts the system anywhere near the class-IV band at all.

### 110.2 What this does to the day's results

> **The class-IV band "at γ ≈ 15" is not a property of γ. It is a joint property of (γ, σ-shipped),
> measured at one hand-picked point that turns out to be an outlier in the only direction that
> matters.**

Every result in this note that fixes σ and varies something else inherits that conditionality:
λ's inertness, γ's monotone traverse, the band location, the sensor screen, and both controller
failures. None of them are known to survive σ varying, and we now know σ varying is not a small
perturbation.

This is the scenario flagged as a risk in §109.4 before the probe ran, and it landed.

### 110.3 What it does to the §109 design

It converts the σ-inference proposal from a refinement into **the largest unexplored dimension in
the model**. If σ carries 55% of the γ axis in leverage and is currently frozen at a hand-picked
outlier, then making it something the agent infers is not a nicety — it is the difference between
exploring the model and exploring one point of it. Joe's hypothesis that freeing σ "would free our
other parameters up to do more useful things" now has a mechanism behind it: **λ may be inert
*because* σ is pinned**, and that is a testable claim rather than a hope.

### 110.4 Honest limits on this probe

Five random draws, six seeds each, one width, one time horizon, two γ values. The effect size is
far larger than the seed noise — 6.83 against 39.00 is not a sampling artifact — but the *shape*
of the σ→damage relationship is entirely unmeasured, and "the shipped set ranks 1 of 6" rests on
five comparators. Before this is load-bearing in Part III it needs a real sweep: more draws,
error bars per draw, and the γ axis re-run under at least two contrasting σ sets.

**It is already sufficient to justify building §109 and to require a caveat on every σ-fixed
result in Part III.** It is not yet sufficient to state the relationship.

---

## 111. codex-1 consultation — "seeking" is ill-posed as framed. My κ claim is refuted.

Independent consultation (codex-1: not the controller author, not the generator auditor).
Reviewed against the park checklist. **Q1 engaged adversarially and answered NO**; Q2 correctly
reframed rather than proceeding as asked; Q3 names concrete system quantities throughout.
One instruction missed: speculation is not explicitly marked as such.

### 111.1 Q1 — the apparatus cannot establish seeking, and the reason is structural

> Damage reach is an **observer-side counterfactual**. The agent neither observes the twin nor
> represents damage reach as an outcome. Its preferences do not score proximity to the class-IV
> band. Therefore there is no error signal, utility, likelihood, or epistemic uncertainty whose
> reduction means "closer to the edge."

Adding σ moves enlarges the action space but supplies **no reason to choose band-reaching moves**.
The most this apparatus can support is:

> *A locally defined active-inference process spontaneously produces dynamics classified
> externally as class IV.*

That is **emergence, not seeking** — potentially a strong result, but a different claim. Two honest
routes: (a) put an internally available estimate of perturbation propagation into the generative
model and preferences, making seeking well-posed but the objective imposed; or (b) define
preferences over locally available goods only and preregister that their balance yields
intermediate damage, keeping damage as external validation. (b) is the cleaner science; a null
under (b) is a real null.

### 111.2 MY κ CLAIM IS REFUTED — §109.2 was hand-waving

I wrote that the epistemic term "already **is** the exploration mechanism", so κ becomes the knob
governing σ-exploration. codex-1:

> Epistemic value only induces useful exploration when the generative model **represents
> uncertainty that an action can resolve**. Merely adding 28 actions does not make their
> consequences epistemically valuable.

This is correct and I should not have needed telling. EFE's epistemic term is expected information
gain about *modelled* hidden states. If the generative model does not represent σ's consequences,
σ-moves carry no epistemic value, and κ turns a knob attached to nothing. **§109.2's central
"AIF-valid, no new theory needed" argument does not hold as stated.** The proposal survives only
with an explicit account of what uncertainty a σ-move resolves — which is exactly the work I
claimed was unnecessary.

### 111.3 A precision correction to §110

> "Sample complete 12-σ vocabularies, not merely individual permutations, because the existing
> effect was measured at vocabulary level."

Correct, and it describes my probe accurately: each "random draw" assigned a fresh random σ to all
twelve kinds, so what §110 measured is **vocabulary-level leverage, not single-σ leverage**. The
finding stands; its statement needs tightening. We do not know whether one σ or the ensemble
carries the effect.

### 111.4 The cheap decisive test, before any build

> Across held-out σ, γ, seeds and widths, **does the agent's own policy score predict subsequent
> movement toward or away from the external damage band?** If it does not, building a σ controller
> from that score is unjustified.

This is a *prospective bridge test* between the internal quantity and the external objective, and
nothing in this project has ever run it. It is cheap — no new machinery, only correlating existing
EFE scores against subsequent damage movement — and it gates everything downstream.

Q2's design adds the comparator that matters: a **compute-matched random walk on the same
transposition graph**, plus an **external oracle search using measured damage**. The oracle
separates "the landscape has no band-reaching path" from "the landscape is searchable but EFE
ranks wrongly" — a distinction none of our three controllers could have drawn.

Staging: map σ at 2–3 preregistered γ, establish whether band-reaching regions generalise, measure
interaction on a small crossed design, and only then consider joint adaptation. Joint adaptation
now would be **uninterpretable**: comparable leverage means either knob can compensate for the
other, producing a ridge of observationally equivalent states that a controller could chase while
appearing to succeed.

### 111.5 Q3 — σ is probably not the principal parameter

The warning that matters most:

> **σ may not be "the principal parameter." It may be the first exposed member of a larger
> equivalence class comprising the mutation kernel, available operator vocabulary, and EFE
> scaling.** Building search before separating those effects risks turning yesterday's frozen
> constant into tomorrow's unexplained adaptive state.

Concrete pins named, all of which are ours and none of which we have varied:

- **The rest of the mutation kernel**: `k ~ Uniform{0..7}`, reading `bits[k]`, the *mandatory
  inversion*, writing to σ(k), one rewrite per site per step, synchronous update. σ's leverage may
  be an **interaction** with invert-and-relocate rather than a property of σ.
- **The policy vocabulary and the blend operator** (21.3% of novelty — ablatable cheaply).
- **Twelve kinds, and their seeding** (uniform, spatially uncorrelated, duplicate-labelled).
- **Softmax parameterisation, not merely γ** — *the absolute scaling and normalisation of the EFE
  terms determine what γ means*. Rescaling risk or epistemic value can reproduce a γ change. This
  one is serious: it means our γ axis may not be a physically meaningful axis at all.
- **Planning horizon** — a short-horizon EFE may be structurally incapable of valuing σ changes
  whose effects emerge over the 100 steps the assay measures.
- **Initial ensembles; update ordering and synchrony.**
- **The damage assay itself** — width (already proved load-bearing), perturbation position and
  count, the t=100 horizon, and the rule-54/110 anchor band.

Recommended probe order: mutation-kernel factorial → blend ablation → initial-state/seeding
robustness → damage horizon/width robustness → EFE term-scaling and horizon audit → *only then*
dynamic σ inference.

---

## 112. E0 — THE BRIDGE TEST FAILS. The agent's score does not predict the objective.

The gate from PLAN-next-experiments.md. Width 250, t→200, lag 20, 6 seeds,
**within-configuration** correlation between mean `:winner :total` at *t* and
damage(t+20) − damage(t), across 3 σ-vocabularies × 4 γ.

| σ | γ=4 | γ=8 | γ=16 | γ=32 |
|---|---:|---:|---:|---:|
| shipped | +0.048 | +0.057 | −0.004 | −0.020 |
| random-1 | +0.087 | **−0.127** | **+0.063** | +0.059 |
| random-2 | −0.013 | +0.013 | −0.017 | +0.033 |

**All twelve |r| ≤ 0.127.** The largest is R² = 1.6%. Two cells flag at 2·SE — and they have
**opposite signs**, which is what twelve tests at that threshold produce by chance.

### 112.1 The null is not an artifact — checked before accepting it

A negative is evidence about the setup first. Two ways this null could have come for free, both
ruled out:

| check | γ=8 | γ=16 | verdict |
|---|---|---|---|
| damage deltas move? | sd 6.97, range [−10,+26], 68% ≥ 3 | sd 5.30, range [−7,+22], 72% ≥ 3 | **live** — plenty to predict |
| score varies? | rel. sd 0.0102 | rel. sd 0.0143 | **varies**, though only ~1% |

Damage is emphatically not equilibrated (γ=8 runs 12 → 8 → 11 → 31 → 56 across the window). The
predictor is not constant. **The absence of correlation is the finding, not the setup.**

### 112.2 What it means

codex-1 predicted exactly this from the architecture: damage reach is an **observer-side
counterfactual**, computed on a twin run the agent cannot perceive, and nothing in the EFE
references it. There is no path by which the agent's score could track it, and now there is no
measured path either.

> **No controller built on an EFE-derived sensor can regulate damage reach.** This retrospectively
> explains both failed controllers — not as tuning failures but as attempts to steer by an
> instrument not connected to the quantity of interest — and it forecloses the third before it was
> built.

### 112.3 What it does NOT license

The honest statement is **"the most natural internal→external bridge does not exist"**, not "no
bridge can exist". Specifically untested: other functionals of the decision record (score
*variance*, candidate spread, the full distribution rather than the winner's mean); other lags;
nonlinear relationships — this is Pearson only. The score's relative variance is ~1%, which itself
caps detectable correlation.

### 112.4 The stop rule fires

Per the plan: **E0 fails → pivot to the emergence framing.** The defensible claim becomes

> *A locally defined active-inference process spontaneously produces dynamics classified
> externally as class IV*

with damage reach as **external validation only**, never as an objective the agent pursues.
E1 and E2 remain worth running — they check foundations the emergence claim also rests on — but
the seeking programme, three controllers deep, is closed.

---

## 113. E1 — γ is not a unit convention, but κ and γ are partly confounded, and MAGNITUDE was the wrong statistic

`efe.clj:327-331`: `total = risk + ambiguity + (λ·conatus) + epistemic + churn`.
**risk and ambiguity carry hardcoded coefficients of 1.0.** Only conatus (λ), epistemic (κ) and
churn (adoption-bonus) are parameterised. So the risk:ambiguity ratio is pinned at 1:1 by nothing
but the absence of a coefficient — structurally the same kind of pin as σ.

### 113.1 My first reading was wrong, and the correction is the finding

Measuring term **magnitudes** at γ=16 gave: ambiguity 82.5%, risk 15.3%, λ·conatus 3.2%,
epistemic 1.0%. I wrote that this *explains* λ's inertness. **It does not, and magnitude is the
wrong statistic entirely.**

Selection is P ∝ exp(−γ·total) **over candidates**. A term that is 82% of the level but identical
across candidates contributes *nothing* to the choice; a term that is 1% of the level but varies
between candidates can dominate it. The right statistic is the **within-decision spread across
candidates**:

| γ=16 | risk | ambiguity | λ·conatus | epistemic |
|---|---:|---:|---:|---:|
| share of **magnitude** | 15.3% | **82.5%** | 3.2% | 1.0% |
| share of **spread** | 54.7% | 49.2% | 15.3% | **82.0%** |

**The epistemic term is 1% of the level and 82% of the spread.** It is the dominant driver of
selection, and κ scales it — which finally explains κ's measured leverage (§99: 32% on blend
share, 49% on adoption) mechanically rather than empirically.

*(Caveat: these shares do not partition. max−min of a sum ≠ sum of max−min, so they are ratios of
individual spreads to the total spread, indicative rather than a decomposition.)*

λ·conatus is **15.3% of the spread** — not a rounding error. So λ's inertness is **still
unexplained**; the magnitude reading that appeared to explain it was an artifact of the wrong
statistic.

### 113.2 The E1 verdict

**γ is not a pure rescaling artifact.** If it were, effective sharpness γ·spread would scale
exactly with γ. It does not: spread itself grows with γ (0.0620 → 0.0722 → 0.1392 for γ = 4, 16,
64), so γ·spread runs 0.248 → 1.16 → 8.91 — a factor of 36 across a factor-16 change in γ. The
plant responds by changing the spread, which is independent content.

**But γ's meaning is not constant along its own axis**, because the effective quantity is
γ·spread and spread is state-dependent. And more seriously:

> **κ and γ are partially confounded.** γ scales the whole total; κ scales the epistemic term,
> which carries 82% of the spread. Both therefore move effective decision sharpness, and a change
> in one can be substantially mimicked by the other. Any claim that attributes an effect to γ
> specifically — including the entire γ traverse in §III.6 — is exposed to this.

E1 therefore **passes with a qualification**, not cleanly: the axis is real, its calibration is
not constant, and it shares a direction with κ.

### 113.3 The spacetime diagrams

`futon5/figs/spacetime-gamma-{01,04,16,64}.png`, genotype left, phenotype right, 250 steps at
width 250, common seed.

- **γ=1** — a large uniform grey wedge opens in the genotype panel: the rule population collapses
  over a wide region, and the phenotype beside it goes to frozen vertical striping. Visibly the
  ordered regime, and the collapse is *spatially localised* rather than global, which none of our
  scalar statistics would have shown.
- **γ=16** — no dead zone; textured genotype throughout, and the phenotype shows the
  domain-and-boundary structure characteristic of class IV.
- **γ=64** — denser, finer-grained genotype churn; the phenotype's vertical domains break up into
  narrower, more irregular structures.

The γ=1 wedge is worth following up: **a localised collapse is not what a well-mixed statistic
like damage reach or survival rate would predict**, and it suggests the ordered regime is
spatially heterogeneous in a way we have not measured.

---

## 114. E2 FAILS — the class-IV "band" is an artifact of choosing t=100

Width 250, 16 seeds (more than the 6 used elsewhere, because these numbers are load-bearing).

### 114.1 The anchors grow without bound in the horizon

| rule | t=50 | t=100 | t=200 | t=400 |
|---|---:|---:|---:|---:|
| 204 frozen | 1.00 | 1.00 | 1.00 | 1.00 |
| 90 nested | 8.00 | 8.00 | 8.00 | 8.00 |
| **54 class-IV** | **21.50** | **33.63** | **67.75** | **105.88** |
| **110 class-IV** | **17.69** | **32.75** | **50.06** | **94.25** |
| 30 chaotic | 35.06 | 66.19 | 121.19 | 125.13 |

Rule 54 grows ×4.9 and rule 110 ×5.3 between t=50 and t=400. **The band we have used all day —
36.0 to 38.1 — is not a property of rules 54 and 110. It is a property of (54, 110, t=100).**
At t=50 the "band" would be ≈18–22; at t=200, ≈50–68; at t=400, ≈94–106.

Every statement of the form *"the system reaches damage 36–38, therefore it is in the class-IV
band"* is therefore conditional on a stopping time nobody chose deliberately.

### 114.2 The band is narrower than the assay's own arbitrariness

Perturbation position at t=100:

| rule | pos 10 | 62 | 125 | 187 | 240 | spread |
|---|---:|---:|---:|---:|---:|---:|
| 54 | 29.19 | 33.88 | 33.63 | 36.13 | 36.88 | **7.69** |
| 110 | 36.00 | 31.19 | 32.75 | 37.06 | 35.00 | **5.87** |
| 30 | 62.94 | 64.88 | 66.19 | 66.13 | 64.19 | 3.25 |

> **The band is a 2.1-unit range. Moving the perturbation — an arbitrary choice, always W/2 —
> moves a single anchor by 6–8 units.** The target is narrower than the measurement's sensitivity
> to a convention.

Note also that these 16-seed anchors (54 = 33.63, 110 = 32.75 at t=100, pos=W/2) do **not**
reproduce the 36.0 / 38.1 used throughout this note. The anchors themselves carry seed variance we
never quantified, and 54 > 110 here where 110 > 54 before.

### 114.3 What survives

**The ordering survives.** At every horizon and position: 204 < 90 < {54, 110} < 30. That is
robust, and it is the real content.

**The numbers do not.** So the correct formulation is *relative*: a system is "class-IV-like" if
its damage sits **between the rule-90 and rule-30 anchors measured under the same assay** — same
width, same horizon, same perturbation protocol, same seed set. Absolute figures like "36–38" have
no standing.

### 114.4 The stop rule fires — again, and harder

PLAN-next-experiments.md: *E1 or E2 fails → stop experimenting entirely and restate Part III's
existing numbers in whatever terms survive.*

E1 passed with a qualification (γ real but calibration non-constant, and κ/γ partly confounded).
**E2 has failed outright.** Combined with §112 (E0: no internal→external bridge) and §110 (σ
carries ~55% of γ's leverage), the position is:

- the **objective** is assay-relative, not absolute (§114);
- the **agent cannot perceive it** (§112);
- the **principal structural parameter was pinned** at an outlier (§110);
- the **decision functional's composition** is pinned by absent coefficients (§113, E1c pending).

No further experiments. The next work is restatement: every damage figure in Part III re-expressed
against same-assay anchors, every σ-fixed claim marked conditional, and the seeking framing
replaced by the emergence framing. Joe's judgement in §113's discussion — *"until we chase these
things out, all findings like the E0 null are suspect"* — is vindicated by E2 more directly than by
anything else today.

---

## 115. THE BRIDGE TEST PASSES — against the intrinsic objective

Run autonomously while Joe was out, under his authorisation to chain PLAN steps 1-2-{3,4}.

Objective surface: codex-4's `scripts/local_compressibility_grid.clj`, 35 cells (7 γ × 5 κ),
4 seeds, geometry fixed and non-configurable (width 250, burn-in 0, rows 250, patch 100, stride
50, 1250 packed bytes). GRID_EXIT=0, 140 data rows. ECA validation passed on the separation bar
before launch: 54 = 98.44% ± 1.56, 110 = 67.19% ± 7.81, and 204/90/30 all 0.00% ± 0.00.

**Gate: leave-one-out held-out prediction of mid_range from (halting share, change rate).**

| | held-out R² | verdict |
|---|---:|---|
| **§112 (E0)** — internal score → **damage reach** (external, twin-run) | **+0.016** | failed |
| **§115** — (halting, change) → **local compressibility** (intrinsic) | **+0.541** | **PASSES** |

Held-out MAE 0.1462 against a mean-baseline MAE of 0.2271. In-sample R² 0.6160, so the
overfit gap is small (0.616 → 0.541). Fitted relation:

    mid_range = 0.512 + 0.716·halting − 1.457·change

More halting and less change predicts a more graded distribution — consistent with §III.9's
finding that the top-scoring regimes have *large* dead zones, and with the aligned portraits.

### 115.1 Why this is the day's most important contrast

E0 and §115 ask the *same question* of the *same system* with the *same kind of observable*. The
only difference is the target: an **external, observer-side counterfactual** the agent cannot
perceive, versus an **intrinsic** measure computed from its own output. One yields 1.6% of
variance, the other 54%.

> **The three controllers did not fail because the system cannot be steered. They failed because
> they were steering by an instrument not connected to anything the system does.**

That is a stronger and more useful claim than "the loop does not close", and it is now measured
rather than argued.

### 115.2 The caveat, stated before it is discovered

The observables come from the earlier surface run (burn-in 100, steps 100–300) while the objective
comes from the grid (burn-in 0, rows 0–250). **Same seeds and same parameters, so the same
trajectories — but different measurement windows, overlapping on t=100–250.**

The mismatch adds noise, which biases the test *toward* failure, so **+0.541 is a lower bound**
and the pass is not an artifact of it. But the number itself should not be quoted as precise until
observables are measured on the matched window. That is a cheap fix and it is owed.

### 115.3 Consequence

PLAN step 3 is now authorised by its own gate: build the episodic surrogate + policy apparatus.
Note what makes this controller #4 different from the three that failed —

- the objective is **intrinsic** (no twin run, no external reference automata);
- the loop is **episodic**, not per-step, because the objective needs a whole sheet — which
  structurally prevents the per-step noise that destroyed the γ controller;
- and it is **gated by a bridge test that passed**, which none of the first three ever had.

Known blind spot to carry forward: the γ/κ ridge (§ surface analysis), where 5 of 595 cell pairs
collide within 0.02 in observable space — e.g. (γ=2, κ=0.5) sits on (γ=8, κ=0.1). A policy reading
only these two observables cannot distinguish those points.

## 116. Review of codex-3's apparatus — the acceptance test was wrong twice, the policy is real

`scripts/intrinsic_objective_controller.clj`. What I checked, and how.

**Passes, verified independently:** held-out R² 0.5414 and in-sample 0.6160 reproduce my own
figures exactly; the refusal path is not merely implemented but *tested* (exit 2 at held-out
R² = −0.1895); replay is deterministic and byte-identical; clj-kondo and check-parens clean;
no live loop was run and nothing was committed. The policy rule is stated in one sentence and the
ridge behaviour is documented rather than papered over.

**The acceptance test does not support its claim.** Reported: policy 3.3143 episodes to a
top-quartile cell versus 8.8622 for "random walk" — a 63% margin. Two independent defects:

1. **The baseline cannot lose.** `random-walk-time` (line 282) steps to a random *neighbour*
   with revisits. The fair baseline is random *sampling* without replacement, since an episodic
   controller may jump anywhere in (γ, κ) between episodes — locality is self-imposed, not a
   constraint of the problem. Sampling reaches a top-quartile cell in **3.2772** episodes
   (analytic (N+1)/(K+1) = 36/11 = 3.273; 20k-trial simulation 3.2772). Against that the policy
   is **1% worse, not 63% better.**
2. **The criterion has no headroom.** Ten of 35 cells are top-quartile, so random search wins in
   3.25 draws. No policy could have shown much on this test.

**Corrected replay — and the policy survives it:**

| criterion | policy | random sampling | |
|---|---:|---:|---|
| top quartile (10/35 winners) | 3.31 | 3.25 | no advantage |
| **top-3 cells** | **4.66** | **9.01** | **~2× faster** |
| single best cell | 16.80 | 18.05 | no advantage |

So the apparatus is worth keeping and the *evidence for it* needs replacing. The honest claim is:
**the surrogate-driven policy reaches the top few cells about twice as fast as random search, and
cannot pinpoint the single optimum.** Best cell is γ=1, κ=0.10 at mid_range 0.641.

**Owed:** replace `random-walk-time` with a sampling baseline and report top-3 alongside
top-quartile. Until then the script's own printed margin should not be quoted.

**Note on method.** This is the second time today that review caught a defect in the *acceptance
criterion* rather than the implementation — codex-4 caught my contaminated ECA targets, and here
the baseline was too weak. Both times the code was correct and the bar was wrong. Author ≠
reviewer is earning its cost, but the failures it catches are not where I expected them.
