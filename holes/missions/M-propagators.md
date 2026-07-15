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
  Cloned to `/tmp/metaca`; **it should be vendored, /tmp is not durable.**
- **The bug**: `mutate-genotype-n` @ `2f62f59` (2014-12-29) reads `elt` at 0-based
  `pos` but writes at `(goto-char pos)` — Emacs buffers are **1-based**, and
  `(goto-char 0)` does not error, it **clamps to point-min = 1**. So `pos=0` and
  `pos=1` both write bit 0, and **bit 7 is never written**. That is the paper's
  "(erroneously-programmed) mutation rule that only flips the first bit".
- Fixed 10 days later in `4a1e37e` (2015-01-08), which also renamed it
  `mutate-genotype-n` → `mutate-rule-n`. The rename is the tell: it had been
  treating a *rule* as a *genotype*.
- **Reproduction is exact in character**: colourful chaotic band → two-rule grey
  snow; phenotype briefly structured → vertical stripes forever ("succumbing to a
  version of Newton's First Law" is literal — activity reaches exactly 0).
- 15/15 seeds converge to the same attractor. **Joe did not cherry-pick** the
  phenomenon (he did pick the prettiest of a batch: his blind visual pick, seed 9,
  ranked 2/15 by survival and 3/15 by activity).

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

- **L1 — THE SEARCH** (`H-propagator-search`): sweep the 8! space. Dispatched.
- **L2 — THE MECHANISM** (`H-propagator-mechanism`): why does `(0 1 2 3)(4 5 6 7)`
  die while `(0 2 4 6)(1 3 5 7)` lives? Dispatched.
- **L3 — THE INSTRUMENT** (`H-diagonal-transport`): is the surviving regime EoC?
  Build the discriminator the eye keeps outperforming. Dispatched.
- **L4 — INFRASTRUCTURE** (claude-3, carve-out d): wiring-diagram component, the
  suite, vendoring the metaca repo, committing the day's work.
- **L5 — TRANSFER** (held): propagators on wiring diagrams / the ant domain. Blocked
  on L2 (no mechanism = nothing to transfer) and on an ant-domain authority gate.

## 5. Artefacts

`holes/labs/M-aif-tokamak/`: `figure8_REPRODUCED.png` (repro vs the paper's own
`eoc.png`), `figure8_x15_repro.png` (no cherry-picking), `figure8_family.png`,
`propagator_survey_{clamp,wrap}.png` (16 propagators × 5 seeds vs ECA ground truth),
`gcd_law.png` (the refuted law's best case), `eoc_confirm_phenotype.png`,
`rule110_conventions.png`.
