# M-propagators — patterns of improvisation in the MetaCA rule byte

**Status:** IDENTIFY complete, ARGUE open. Chartered 2026-07-15 from the Figure-8
reconstruction. Owner: claude-3 (orchestration/review). Three lanes dispatched.

**One line:** a 2014 Emacs off-by-one turned mutation from a random walk into a
*constraint propagator over the rule's bit-planes*, whose fixed point selects the
rule the landscape lands on. Some propagators produce a persistent structured
regime. That family — not the bug — is the object of study.

---

## 1. What was found (all measured, all reproducible)

### 1.1 Figure 8 of arXiv:1502.00130 is reproduced, from the original code

- Repo: `https://github.com/holtzermann17/metaca` (47 commits, 2014-12-15 → 2015-06-19).
  **Vendored** at `vendor/metaca/` (the Figure-8 commit as
  `256ca-2014-12-29-BUGGY.el`, plus `256ca-2015-04-12.el` and the real
  `hexrgb.el`). **`vendor/` is evidence — do not edit it**; override from a
  lexical-binding side-file.
- **The bug**: `mutate-genotype-n` @ `2f62f59` (2014-12-29) reads `elt` at 0-based
  `pos` but writes at `(goto-char pos)` — Emacs buffers are **1-based**, and
  `(goto-char 0)` does not error, it **clamps to point-min = 1**. So `pos=0` and
  `pos=1` both write bit 0, and **bit 7 is never written**.
- **The paper's "only flips the first bit" is an imprecise gloss** — now measured,
  not inferred (87,632 instrumented writes, H-baldwin-repro): bit 0 takes
  **24.946%** of writes (the predicted 2/8 — `pos=0` and `pos=1` both land there),
  bits 1–6 take ~12.5% each, bit 7 takes **0.000%**. So the bug *doubles* bit 0 and
  *never* writes bit 7; it does not confine writes to the first bit. The prose is
  right about the mechanism's flavour and wrong about its extent.
- Fixed 10 days later in `4a1e37e` (2015-01-08), which also renamed it
  `mutate-genotype-n` → `mutate-rule-n`. The rename is the tell: it had been
  treating a *rule* as a *genotype*.
- **Reproduction is exact in character**: colourful chaotic band → two-rule grey
  snow; phenotype briefly structured → vertical stripes forever ("succumbing to a
  version of Newton's First Law" is literal — activity reaches exactly 0).
- 15/15 seeds converge to the same attractor. **Joe did not cherry-pick** the
  phenomenon (he did pick the prettiest of a batch: his blind visual pick, seed 9,
  ranked 2/15 by survival and 3/15 by activity).

**CONFIRMED against a control (2026-07-15). The reproduction stands.** A doubt was
raised and then closed by measurement, so both halves are logged here:

- *The doubt.* The Figure-8 commit aliases `evolve-sigil-fn` to
  **`evolve-sigil-with-blending-baldwin`** (`256ca-2014-12-29-BUGGY.el:637`), not
  to the `evolve-sigil-with-blending-mutation` this reconstruction used. The 2015
  file re-aliases to `evolve-sigil-with-mutating-template` (`:1069`). So the
  reconstruction ran a **non-default** evolve function and reproduced the figure
  anyway. Either the figure was robust to that choice, or we had the right picture
  for the wrong reason — a live risk to a published notebook, worth the check.
- *The answer (H-baldwin-repro, `36db2df`, 15 seeds, unedited vendored elisp).*
  **Baldwin does not reproduce Figure 8: 0/15 exact `{42,170}`, versus 15/15 for
  blending-mutation.** Baldwin freezes the phenotype (15/15) but lands on
  `{0,42,170}` plus 0–5 stragglers, retaining 3–8 rules and still mutating at the
  horizon. The 2015 control never freezes at all (active through gen 120, 15/15).
- *So:* the reproduction used the **right** function despite the misleading alias,
  and the alias is a red herring. Figure 8 is **not** default-function robust —
  which is *why* the control was needed, and why the negative is the thing that
  confirms the positive. Logged exactly as it stands: the figure is reproduced
  well enough. It is not a from-scratch re-derivation of every 2014 default, and
  does not claim to be.
- Full report + paired panels: `holes/labs/M-aif-tokamak/baldwin-repro/README.md`;
  reproducer `scripts/baldwin_repro.{clj,el}`; raw per-run evidence
  `data/baldwin-repro/runs/*.edn` (45 records).
- *Review (claude-3, not a rubber stamp — what was actually checked):* `vendor/`
  untouched; the mutation histogram sums to exactly 104,400 = 58 interior × 120
  gens × 15 seeds, and its positive requests imply exactly the 87,632 writes
  claimed (three independent paths, no slack); the write histogram matches the
  bug's **a priori** prediction (25% / 12.5%×6 / 0) to within 0.13%; Q1
  **recomputed from the raw genotype rows** rather than from `summary.edn` or the
  README (0/15, 15/15, 0/15 — all match); determinism verified by deleting seed-03
  and regenerating it **byte-identical**; `check-parens` OK; clj-kondo 0/0.

### 1.2 The paper's text has an error, and the figure proves it

The paper says the genotype "flutters randomly between **Rule 0 (00000000)** and
**Rule 128 (10000000)**". It does not. It flutters between **`00101010` and
`10101010`** (rules 42/170).

*Proof from the paper's own artefact*: rule 0's colour is `#000000`, rule 128's is
`#808080` — **neither appears in `eoc.png`**. Its two dominant colours are
`#2a2a2a` (126k px) and `#aaaaaa` (36k px), which are exactly the colours
`256ca.el` assigns to `00101010` and `10101010`, and exactly the pair the code
converges to on every seed. The structure Joe described (two rules differing only
in the first bit) is right; the identities are wrong.

### 1.3 The bug generalises to a family

The operator is: **pick k at random; set `bit[σ(k)] := ¬bit[k]`** for a permutation
σ of the 8 bit-planes. Ordinary mutation is σ = identity — a random walk, no
attractor. Non-trivial σ *couples* the planes and gives the operator a fixed point,
which selects the rule.

Measured, in isolation (400 applications, no blending):
- σ = rotate −1 + invert → alternating `{01010101, 10101010}` (absorbing: for
  `10101010`, positions 1–7 are no-ops; only p=0 changes it)
- σ = rotate −1, no invert → uniform `{00000000, 11111111}` → **death** (rules 0/255)
- σ = rotate −2 + invert → period-4 bytes

### 1.4 The gcd "law" — REFUTED. Read this before believing any offset story.

Over **rotations**, `gcd(offset, 8)` predicted the regime 12/12, including four
correct **out-of-sample** calls (`±5` → collapse+death; `±6` → lives, ~31 rules):

| gcd | orbits | outcome |
|---|---|---|
| 1 (`±1, ±3, ±5`) | one 8-cycle | collapse to 1–2 rules, **dies** |
| **2 (`±2, ±6`)** | two 4-cycles | **~25–35 rules, lives, structured** |
| 4 (`±4`) | four 2-cycles | saturates black, **dies** |

**It does not generalise.** Breaking the confound with non-rotation permutations
(same substrate, no new machinery):

| permutation | cycles | death/seed | rules | |
|---|---|---|---|---|
| `(0 2 4 6)(1 3 5 7)` = offset +2 | 2 × len-4 | (120 120 120 120) | 31.0 | LIVES |
| `(0 1 2 3)(4 5 6 7)` **not a rotation** | 2 × len-4 | (58 44 22 34) | 1.0 | **DIES** |
| `(0 1 2)(3 4 5 6 7)` | 2 × len-3,5 | (120 120 120 120) | 26.8 | **LIVES** |
| `(0 1)(2 3)(4 5)(6 7)` = offset +4 | 4 × len-2 | (55 35 47 120) | 1.8 | dies |
| `(0 1 2 3 4 5 6 7)` = offset +1 | 1 × len-8 | (47 39 39 39) | 1.0 | dies |

- "two orbits" is refuted — row 2 has two and dies.
- "length-4 orbits" is refuted — row 3 has neither and lives.
- **`gcd` is a parameterisation of rotations, not a mechanism.** The out-of-sample
  success predicted *rotations from rotations* — weaker evidence than it looked.

**Live hypothesis (untested):** bit positions are NOT interchangeable. Position `k`
is the rule's response to a *specific neighbourhood*, `truth-table-3[k]`, where
`truth-table-3 = ["000" "001" "010" "100" "011" "101" "110" "111"]`. So σ is
**semantic**: "copy the response for `000` into the response for `010`, inverted".
If true, the family is defined by which *neighbourhoods* are coupled, and the
search space is permutations (8! = 40,320), not offsets (8).

### 1.5 What the surviving regime is — OPEN

Joe: `-1 inv clamp` (Figure 8) **is** EoC in its surviving phase; the gcd-2 rows are
"more chaotic than Rule 110", possibly "meta-chaos" — *columns that are not frozen*.
Claude read the gcd-2 rows as "frozen columns + noise" and was **wrong** (they are
active). No instrument has confirmed any of this: **the eye is the only working
discriminator so far.**

---

## 2. Apparatus (works, use it — do not reimplement)

**The original elisp runs in batch.** `emacs --batch` with:
- `/tmp/elstub/hexrgb.el` — stub (colour only, no dynamics). The **real** hexrgb.el
  is in the metaca repo; prefer it.
- `/tmp/elstub/clcompat.el` — aliases `map`/`mapcar*`/`first`/`second`/`member-if`/
  `string-to-int`; Emacs 30 removed the old `cl` package. **The legacy source is
  unedited** — what executes is the real 2014 code.
- `;;; -*- lexical-binding: t -*-` is REQUIRED in any file defining closures over
  propagator parameters; without it the lambda resolves them dynamically and you get
  either "void variable" or, worse, silently correct results for the wrong reason.
- `evolve-sigil-fn` is a `defalias` — point it at `evolve-sigil-with-blending-mutation`
  for Figure 8. `fset 'mutate-genotype-n` to swap the propagator.

**Measures that work:** survival time (last t with any phenotype change), total
activity, distinct rules at t_end, {0,128}-fraction.

**Measures that DON'T (banked):**
- **Cμ (causal-state count)** — clears the Rule-110 bar on ECA, then fails on MetaCA:
  a *frozen barcode* (`l0-baseline`) scores **39–44** vs Rule 110's **26.4**. It reads
  the temporal stability of the local rule, not complexity, and it grows with sample
  size (same field: 38 over 160 gens, ~17 over 40-gen windows). Cross-alphabet
  comparison is invalid; the binning drives the verdict (k=2 vs k=4 flips it).
- **Aliveness alone** — earned its stripes ranking *within* one family (Joe's pick
  2/15), then breaks *across* families: it top-ranks stripes-plus-snow. A controller
  maximising it would freeze most columns and flicker forever. Good tiebreaker, not a
  class detector.
- **Run-level means** — the Figure-8 transient is ~8–20 generations. A 100-gen mean
  reports every one of 15 living seeds as dead.

**Candidate instrument, untried:** *diagonal transport*. Rule 110 has it, rule 0
doesn't, gcd-2 rows appear to. r01 already banked "diagonal autocorrelation" as an
axis — developed on L5, which we now know is bitwise Rule 90.

**Diagonal transport — BANKED (2026-07-15, H-diagonal-transport).** A normalized,
windowed bilateral innovation-transport statistic cleared the ECA anchor on every
seed (class-4 > class-3 > settled), then failed the preregistered barcode gate:
the frozen heterogeneous l0 rule field scored 0.1814, above Rule 110's five-seed
range. The static rule barcode sustains correlated moving phenotype activity, so
diagonal correlation is not sufficient evidence of glider-borne computation.
The valued propagator regimes were not evaluated after the failure. Reproducer:
`scripts/diagonal_transport_anchor.clj`; full profiles:
`data/diagonal-transport/anchor-and-barcode.edn`.

**Genotype transport — registered-null separation, not an EoC instrument
(2026-07-15).** A preregistered follow-on decoded each rule sigil into eight
truth-table bit-planes and applied the same windowed bilateral innovation probe
per plane. There is no ECA genotype field and therefore no positive Wolfram
anchor: this probe can reject impostors but cannot certify edge-of-chaos. The
trivial frozen l0 null scored exactly 0. More importantly, the busy identity
propagator scored 0.1230–0.1436 across five seeds, while all 15 live-regime runs
scored higher (global live floor 0.1962; rotate+2 mean 0.2623, observed sigma
mean 0.2217, 3+5-cycle mean 0.2488). Figure 8 began high and decayed to exactly
zero window-by-window as its genotype froze. This establishes directed
rule-space transport relative to the registered nulls, while leaving the EoC
question open. Preregistration and report:
`holes/labs/M-aif-tokamak/genotype_transport_{PREREG,REPORT}.md`; full profiles:
`data/genotype-transport/gates.edn`.

**2014 Baldwin-default cross-check — Figure 8 is not default-function robust
(2026-07-15).** The Figure-8 commit aliases `evolve-sigil-fn` to Baldwin, not to
the blending-mutation function used in the original reconstruction.  A direct
15-seed replay of unedited 2014 Elisp found that Baldwin makes phenotype
activity reach exactly zero in 15/15 runs, but reaches the exact 42/170 rule
pair in 0/15 (versus 15/15 for blending-mutation).  All terminal Baldwin fields
also contain rule 0 and retain 3--8 rules.  The buggy write path is active:
positive mutation requests occur on 74.067% of all cells, measured bit 0 gets
24.946% of writes, and bit 7 gets none.  The fixed 2015 Baldwin control remains
phenotypically active through generation 120 in 15/15 runs.  Report, paired
genotype/phenotype panels, and reproducer:
`holes/labs/M-aif-tokamak/baldwin-repro/README.md` and
`scripts/baldwin_repro.{clj,el}`.

---

## 3. What this is for

futon5's purpose is **patterns of improvisation**. Joe's claim: it is not the
pictures — those are produced by *simple rules*, and the rules should generalise.
E.g. propagators might run over **wiring diagrams rather than bits**, defining how to
continuously weave a logical process. If we have found computational intelligence, it
transfers across domains.

**Counterweight (claude-3):** this session opened by finding that the *last* transfer
claim was vacuous — the cyberant wiring was written to `:cyber-pattern :config` and
never read, so scrambling it changed nothing, and that null was read as a refutation
of the hypothesis rather than of the apparatus. Transfer is the **falsification
test**, not the victory lap, and the receiving domain's actuator must pass an
authority test *before* any transfer claim is made. See `futon5.aif.design-gates`.

---

## 4. Lanes

*Status as of 2026-07-16. Previously read "Dispatched" for lanes that had long since
landed — corrected here.*

- **L1 — THE SEARCH** (`H-propagator-search`): sweep the 8! space. **LANDED.** 112/202
  σ live = **55%**: the live regime is *generic*, the dead set is the rare thing. Orbit
  reduction proven (mirror + 192 fixed σ → Burnside **20,256** orbits; complementation
  rejected — boundaries are not complement-invariant).
- **L2 — THE MECHANISM** (`H-propagator-mechanism`): **LANDED, as a negative that is
  worth more than the positive would have been** (codex-4, `dbb8453`). The live twin
  `(0 2 4 6)(1 3 5 7)` and the dead twin `(0 1 2 3)(4 5 6 7)` are **exactly conjugate**
  via τ = `[0 4 1 5 2 6 3 7]` — 2048/2048 transitions commute. Therefore **no σ-only
  property can distinguish them**, and every "count the orbits / measure the offset"
  story is dead on arrival (the gcd law, §1.4, was the first casualty). What separates
  them lives in **what τ scrambles**, not in σ. τ preserves the left bit, fixes 000 (坤)
  and 111 (乾), and 3-cycles `(001 011 010)(100 101 110)`.
- **L3 — THE INSTRUMENT** (`H-diagonal-transport`): **LANDED as a FAILURE, twice, and
  the failure is now believed to be structural — see §4b.** Class membership is
  undecidable; there is no effective test to build. This lane should not be re-opened as
  specified.
- **L4 — INFRASTRUCTURE** (claude-3, carve-out d): **LANDED.** `:rule-permute` wiring
  component (port test passed on all five regimes), truth-table standardisation, the
  metaca repo vendored as evidence, the suite, the tokamak scripts.
- **L5 — TRANSFER** (held → **NOW UNBLOCKED, and it is the mission's live question**).
  Propagators on wiring diagrams / the ant domain. *Was* blocked on L2; L2 has landed,
  and its negative does not block transfer — you do not need the mechanism to test
  whether the family transfers, and Joe's claim is precisely that real computational
  intelligence transfers domain-to-domain "no problem". Remaining gates: an **ant-domain
  authority gate** (prove the knob moves something before running — the old tokamak knob
  measured exactly 0.000 and nobody checked) and a named **"ant rule byte"**.

## 4b. The tokamak — PARKED 2026-07-16 (Joe: "the Tokamak has done enough")

Four runs. The apparatus works; the objective is the problem. Parked deliberately, not
abandoned — with the reason stated so it is not re-opened by accident.

**What was established.**
1. **The actuator has authority.** Arms span transport **0.0002 → 0.2458**. This is new:
   the *old* tokamak knob measured **exactly 0.000** across every seed. Propagators are
   a control surface; the previous one was not.
2. **Switching beats the best fixed propagator — weakly.** Greedy .2458 v rotate+2 .2418
   on held-out seeds, 3/4, with one seed losing by **more than the mean effect**.
   Suggestive. **Not banked.**
3. **The trap is only a trap if held.** rotate+1 (Figure 8) has the *highest* single-window
   transport in the set (.319/.273) and decays to exactly 0 when held. Greedy picks it
   3/6 windows and wins; run 2's memory arm picked it 0/24 and lost. Its "myopia" is
   adaptive: re-probing every window means it never holds anything, so it surfs the
   transient and leaves. Visible in `tokamak_run_figure.png`.

**Why it is parked: the objective is not EoC, and cannot be made so by trying harder.**
- §2 already banked genotype transport as **"registered-null separation, not an EoC
  instrument... can reject impostors but cannot certify edge-of-chaos."** Four runs
  optimised it anyway. That is the mission's own bank being ignored by its own owner.
- **The two instruments are pincered.** *Phenotype* diagonal transport has a positive
  anchor (class-4 > class-3 > settled, every seed) but **fails its null** (frozen barcode
  .1814 > Rule 110). *Genotype* transport passes its nulls (identity .12 < live floor
  .196, 15/15) but **has no anchor** (there is no ECA genotype field). Each has exactly
  what the other lacks. Neither is an EoC detector.
- **argmax is the wrong shape.** EoC is a *band* between order and chaos, not a maximum.
  Even a valid proxy would be Goodhart bait under `argmax`.
- **There is no effective test, and this is a theorem, not a gap.** Wolfram class
  membership is undecidable (Culik & Yu 1988); nilpotency is undecidable (Kari 1992).
  Cook proved Rule 110 universal by **construction**, never by measurement. So "build an
  EoC instrument" is **not a well-posed goal** and should stop being treated as the open
  problem. The eye outperforming every instrument is not an embarrassment to fix; it is
  what undecidability feels like from the inside.

**The way out, if the tokamak is resumed: the ants do not detect EoC, they eat.**
Their objective is grounded in the domain, not in a class label needing an oracle.
MetaCA's equivalent already exists and is **measured**: `evolve-sigil-with-blending-baldwin`
— count how many of the 3 old context values match the new state, then mutate
`(1- mutations)` times, i.e. **bored → mutate, interesting → hold**. `(dotimes -1)` runs
zero times, so 0 matches → no mutation. Live rates (H-baldwin-repro): positive mutation
requested on **76.6%** of interior calls, **0.81** steps/cell. An endogenous drive,
written in 2014, needing no EoC oracle because it never asks the question. Under it, EoC
is an **outcome you observe**, not a target you optimise.

**Methodological findings worth more than the numbers** (all self-inflicted, all logged
so they are not repeated):
- **Monte Carlo return contaminates credit.** `G_w` credits an action with transport
  realised to the *end of the run*, but later windows run *different* propagators.
  rotate+1 scored .226 (not ~0) because rotate+2 carried the rest — the trap was **bailed
  out by its successors**, then never revisited (n=1), so the estimate never corrected.
  `G_w` estimates "how good was this **situation**", never "how good was this **action**".
- **Random exploration is unbiased over ACTIONS, not over STATES.** The `:dead` and
  `:collapsing` bins came back **empty** (`tok4_qsa.png`) — collapse is only reachable by
  *holding* a propagator, and a random policy never holds. So the very states that reveal
  the trap are off-distribution for the policy meant to discover them. V's spread
  collapsed to .017 against r's .13–.26, the advantage term went **inert**, `A ≈ r`, and
  the arm degenerated into the fixed rotate+2 — *pixel-identical* to it. Fix, if resumed:
  seed exploration with **hold-k** trajectories.
- **Score all arms on the SAME seeds.** Run 2 reported memory beating greedy; it was
  memory's *late* seeds against greedy's *early* ones. The real result was the opposite.

Artefacts: `tokamak_run_figure.png` (controller choices coupled to the actual evolution,
shared time axis), `tok4_propp.png`, `tok4_qsa.png`, `tok4-trace-*.png`. Reproducers:
`scripts/tokamak_advantage.clj`, `scripts/tokamak_{propp_lines,run_figure}.py`.

## 4c. What "useful for anything" would take (Joe, 2026-07-16)

The bug→family discovery is remarkable on its own; the open question is **use**. Three
candidates, and their real dependencies:

- **Transfer to ants (L5) — UNBLOCKED, and it is the actual usefulness test.** If this is
  computational intelligence it transfers domain-to-domain. `:rule-permute` is already a
  ported wiring component and its port test passed on all five regimes. Still needs an
  ant-domain authority gate (measure the knob moves something *before* running) and a
  named "ant rule byte". **Does not depend on the index.**
- **Cluster the space — BLOCKED, and the data does not exist.** The census index is
  **4 / 20,256 orbits = 0.02%**, 7 MB, **not running**. The "30-odd GB" is ~35 GB away.
  **But clustering does not need the census.** It needs a *feature vector per σ*
  (survival, terminal rule count, activity, transport, class-4 population over time) —
  ~100 floats × 20,256 ≈ **10 MB**, hours not weeks. The 37.6 GB spec was raw census
  where the requirement is a feature table: a ~4000× error in what to build.
- **Move around in that space — blocked on the above**, and needs a metric. See
  `project_substrate_metric` (OR/Wasserstein + Fisher–Rao) rather than inventing one.

## 5. Artefacts

`holes/labs/M-aif-tokamak/`: `figure8_REPRODUCED.png` (repro vs the paper's own
`eoc.png`), `figure8_x15_repro.png` (no cherry-picking), `figure8_family.png`,
`propagator_survey_{clamp,wrap}.png` (16 propagators × 5 seeds vs ECA ground truth),
`gcd_law.png` (the refuted law's best case), `eoc_confirm_phenotype.png`,
`rule110_conventions.png`.
