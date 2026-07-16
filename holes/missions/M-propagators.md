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

## 2b. The geometry of the propagator space — BANKED 2026-07-16

**The question.** Joe: *cluster the ~20k into reasonable clusters, find a way to move
around in that space.* Answer: **the space has no natural joints under either geometry
tried — but the second failure is a METRIC failure, and it points at the fix.**

### 2b.1 Euclidean on 18 hand-made features — no joints, and it is CRUDE not wrong

Full 20,256 orbits. Silhouette **declines monotonically**: k=2 0.4409, k=3 0.4134,
k=4 0.3671, k=5 0.3275, k=6 0.3148, k=7 0.2890, k=8 0.2978, k=9 0.2830, k=10 0.2672.
No elbow, no local max: the best k is simply the smallest, and it scores "weak". The
split it finds (15,479 / 4,777, one body + one tail) is highly *stable* (0.998 across
restarts) — so the tail is real, it is just **not a joint**. The space is a continuum
with a skew, not a set of kinds.

**Validated at 2.9% coverage.** The overnight 580-orbit shuffled prefix predicted this:
k=2, silhouette **0.4441** vs the full **0.4409** — 0.7% apart. The seeded shuffle made
a partial build a trustworthy population estimate, exactly as intended.

### 2b.2 Fisher–Rao on the actual distributions — the metric SATURATES

The census *is* distributions: each propagator's terminal state is a point in the
256-simplex (rule counts / 180), so §2b.1 clustered lossy scalar summaries of those
distributions under a geometry I chose. Fisher–Rao is the principled alternative — by
Čencov's theorem the unique metric (up to scale) invariant under sufficient statistics,
`d(p,q) = 2·arccos(Σ√(pᵢqᵢ))`, no features, no free parameters. √p has unit norm, so the
simplex under FR *is* the unit sphere and k-means under the information metric is
literally spherical k-means on √p.

**Result: flat 0.0716–0.0837 across every k=2..10.** No peak. "Chosen k=10" is the
argmax of noise (spread 0.012). Read against controls run under *identical* conditions
(same restarts, same silhouette sample):

| | FR silhouette |
|---|---|
| structureless blob (negative control) | 0.0041 |
| **real propagator space** | **0.084, flat** |
| 3 real clusters, overlapping (positive control) | 0.2465 (peaks at true k=3) |
| 3 obvious joints, disjoint supports (ceiling) | 0.6956 (peaks at true k=3) |

**But this is a metric failure, not a fact about the space.** Width 60 puts **60 samples
into 256 bins**, so any two terminal distributions overlap barely by chance: mean
pairwise FR distance is 2.1–2.5 out of a maximum of π. Nearly every pair is
near-orthogonal and the metric flatlines. FR treats the 256 rules as **unordered
labels** — it cannot know rule 106 and 108 are two bit-flips apart.

**Proof that FR is blind to real structure — the 12 collapse targets.** The 1,479
collapsers (≤2 terminal rules) land on just **12 rules**, and those 12 are
**Hamming-tight**: mean pairwise Hamming 2.82, **max 4 of 8**, 39 of 66 pairs within 2
flips. FR calls every one of those pairs *maximally distant* (orthogonal point masses).
Six rules absorb 1,468 of the 1,479:

| rule | binary | n | |
|---|---|---:|---|
| 232 | 11101000 | 271 | **the majority rule** |
| 204 | 11001100 | 262 | **the identity rule** |
| 201 | 11001001 | 246 | |
| 108 | 01101100 | 244 | |
| 77 | 01001101 | 223 | |
| 105 | 01101001 | 222 | additive / XOR-like |

**Death lands on majority and identity** — the two canonical self-stabilising ECA rules
— measured over the whole space, not intuited. And **class-4 targets (106, 120) are hit
4 times in 1,479 = 0.27%**: EoC is rare, quantified.

### 2b.3 Wasserstein-1 under a Hamming ground metric — the negative TRIANGULATES

Built 2026-07-16 (`scripts/propagator_wasserstein.py`). The ground metric is forced, not
chosen: `rule-permute` writes exactly ONE bit, so a propagator step *is* a Hamming step
and rule space *is* the 8-bit cube; W₁ under Hamming measures transport along the
operator's own moves. And `legacy-to-standard` is a bit *permutation*, which preserves
Hamming — so unlike σ, this metric is convention-independent by construction.

**The useful surprise: the expensive machinery was mostly unnecessary.** Hamming is a
SUM OVER BITS, so W₁ nearly decomposes into per-bit marginal differences:

    W1(p,q)  >=  Σ_b | E_p[bit b] − E_q[bit b] |          (certified LOWER BOUND)

Measured against exact LP on random pairs: **valid on every pair, mean lb/exact 0.965,
worst case 0.830** (`wasserstein.json`: `tightness_mean` .9647, `tightness_min` .8297, 24
pairs, `valid` true).
> *Corrected 2026-07-16 (caught by Fable re-grounding the paper draft).* This previously
> read "0.957–0.965", which was **not a range**: .957 was the mean of an early 8-pair
> ad-hoc run and .965 the mean of the committed 24-pair run. Stitching two means into an
> interval implied a floor of .957 and hid the true worst case of **.830**. So the 8 numbers
`M = P @ BITS` are a near-W₁ embedding that is **dense** (no sparsity saturation, unlike
FR), **interpretable** — it is the **mean field**: for each of the 8 neighbourhoods, the
fraction of the rule population responding 1 — and it is *exactly the object the
propagator acts on*. **205M pairs become instant.**
- Exact W₁: support-restricted LP is identical to the full 256×256 LP (verified) because
  terminal distributions are sparse; 11–31 ms/pair. Kept only to validate the bound.
- Sinkhorn was tried and **dropped**: 43 ms/pair, *slower* than exact.

**Result — no joints here either.** Controls on the same instrument, same restarts, same
8-dim space:

| | silhouette | shape |
|---|---|---|
| blob (no structure exists) | 0.088 | flat |
| **real propagator space** | **0.176 (k=3)** | **flat: .141 .176 .157 .162 .168 .167 .173 .156 .158** |
| 3 real clusters (sd .10) | 0.690 | strong |
| 3 obvious joints (sd .03) | 0.915 | peaks at true k=3 |

Real is **2.0× the blob and 0.26× genuine structure**, and its "peak at k=3" is a 0.02
wiggle — noise. So the negative now holds under the crude feature geometry, the
principled information metric, AND the operator's own transport metric. **The propagator
space is a continuum with a slight non-uniformity, not a set of kinds.** As
triangulated as a negative gets.

**Remaining control gap (honest):** the Euclidean-18-feature sweep (§2b.1) never got a
blob control, so part of its 0.44 may be what 18 correlated features score on
unstructured data. The other two geometries are controlled and agree, so the conclusion
stands — but that control is owed.

### 2b.4 Ollivier-Ricci curvature — how to navigate a continuum

Built 2026-07-16 (`scripts/propagator_curvature.py`). Since there are no joints, "moving
around in that space" cannot mean hopping between kinds — there are none. It has to mean
**local geometry**: `κ(x,y) = 1 − W₁(mₓ,m_y)/d(x,y)`, with mₓ the lazy random walk
(α=0.5) on a k-NN graph (k=10) in the mean-field space.

**Two levels of W₁, and conflating them would be a real error.** *Level 1*: W₁ between
two propagators' **rule distributions**, ground metric Hamming → defines `d(σ,τ)`; we use
the certified mean-field bound (within ~4% of exact). *Level 2*: W₁ between
**neighbourhood measures on the propagator graph**, ground metric `d` from level 1 → this
is what enters κ, exact by LP over the small local supports. That is the architecture
`M-substrate-metric`/E1 used for substrate-2, and it is what makes this affordable: κ is
needed only on **edges** (O(n·k)), never on the 205M pairs.

**Result (2,000 sampled nodes, 9,157 edges):**

| | |
|---|---|
| mean κ | **+0.0845** (median +0.0799, sd 0.1785) |
| range | [−2.05, +0.55] |
| **negative — branching / frontier** | **30.3%** |
| positive — locally redundant | 69.7% |

So the continuum is **mostly mildly positively curved** (locally redundant: your
neighbours' neighbourhoods overlap — more of the same) **with a substantial 30% negative
minority** where the space genuinely branches. That negative 30% is the frontier, and in
M-aif2's terms it is the **"propose here"** polarity.

**The finding: minimal σ-edits sit at the branch points.** Of the 20 most-negatively-
curved edges, **80% connect σ that differ by a single TRANSPOSITION** (exactly two
positions swapped; mean positions differing 2.45).

**And it survives its artifact test.** κ divides by d, and transpositions produce
near-identical mean fields (small d), so the pattern *could* be pure 1/d amplification.
Re-checked with the near-duplicate regime excluded (d ≥ 0.2): **83% single transpositions
— SURVIVES**. Only 3/20 extremes are near-duplicates. The test is now in the script, and
it prints SURVIVES/ARTIFACT rather than leaving it to a reader's charity.

**Why this matters for the xeno step.** It says where to mutate: *the evolutionary
operator on σ should be transpositions*, because that is where curvature is negative =
where the space branches = where structure is not yet decided. That is an actionable
"propose here" for §2c's open problem (the exotype triples were hand-picked; the annealer
proved the space has emergent points; this says where to look). It also rhymes with the
conjugacy theorem (§1.4/L2): no σ-only property separates live from dead, and now —
minimal σ-edits are exactly where the geometry is most undecided.

**Caveat, measured not glossed.** Two different statements are both true and were twice
mis-captioned before the data settled it: the *average* trend is that κ<0 edges are
slightly **longer** (corr(d,κ) = −0.26; mean d 0.428 for κ<0 vs 0.388 for κ≥0), while the
*extreme* negatives sit at tiny d and are **1/d amplification, not structure**.

Artefacts: `propagator-clusters/curvature.{json,png}`.

### 2b.5 What this licenses

- **Neither geometry is right, in *opposite* ways.** The 18 features encode
  CONCENTRATION (entropy, top1) but weight it arbitrarily → crude. FR sees LOCATION
  exactly but is blind to rule adjacency → saturates. Do not read §2b.1 as "the features
  were an artifact" (an earlier claude-3 overstatement, corrected here): they capture
  something FR cannot.
- **Wasserstein-1 with a HAMMING ground metric** (now BUILT — see §2b.3, and it agrees:
  no joints). It
  is not a preference: `rule-permute` writes exactly one bit, so a propagator step *is*
  a Hamming step, and rule space *is* the 8-bit cube. W₁ measures transport along the
  operator's own moves, and would see the 12 targets as one basin rather than 12
  orthogonal spikes. Bonus: `legacy-to-standard` is a bit *permutation*, and bit
  permutations **preserve Hamming distance** — so this ground metric is
  convention-independent, immune to the silent-port bug that forced σ to be a
  neighbourhood map.
- **Cost governs the design.** 205M pairs × LP is impossible. OR curvature needs W₁ only
  between *neighbours* on a k-NN graph (O(edges)) — the architecture `M-substrate-metric`
  already used; reuse `futon3c/scripts/substrate_metric_e1_or_sample.py`'s exact-W₁-by-
  `scipy.optimize.linprog`.

Reproduce: `scripts/propagator_terminal_dists.clj` then `scripts/propagator_fisher_rao.py`.
Artefacts: `propagator-clusters/{fisher-rao.json,fisher_rao_vs_euclidean.png}`.

**Two process notes, logged because both nearly shipped.** (1) The first version of the
figure plotted the FR curve against *Euclidean* strong/weak thresholds, implying "FR
found even less structure" — false; FR's scale is compressed and needs its own controls,
which now live in the script rather than in an agent's head. (2) The controls initially
ran with ONE restart against the real curve's FIVE, and `spherical_kmeans` closed over
the global `n` so controls only ran *by luck* (a constant seed whose first draw happened
to land inside the smaller array). A control that passes by luck is not a control.

---

## 2c. Exotypes — compositional physics, by example (2026-07-16)

Joe's frame: in futon5's **pheno → geno → EXO → xeno** hierarchy, an **exotype** is
the *global physics* of a MetaCA, and if propagators are a basis then named physics
(baldwin, blend) should themselves be **compositions of propagators**. A **xenotype**
is where exotypes get evolved under outside selection (e.g. ants).

**Built:** `:rule-permute-switch` (generator.clj) — the general compositional form,
`switch(local-condition, σ_A, σ_B)`: apply propagator A where a per-cell condition
holds, B otherwise. Conditions read the same per-cell context a single propagator
ignores (`:boredom` = phenotype neighbourhood uniform; `:active`; `:dense`). It is a
first-class wiring component, so its branches and condition are **data** — i.e.
evolvable, which is what the xeno layer needs.

**Finding 1 — baldwin IS a composition, and what it buys is a state-dependent RATE.**
Baldwin's live rule (H-baldwin-repro) is *bored → mutate, interesting → hold* =
`switch(:boredom, propagator, no-op)`. Reconstructed and measured
(`scripts/exotype_by_example.clj`): the exotype's per-step mutation rate **falls
0.055 → 0.002 as structure emerges**, tracking local state where neither constant
policy can (explore pinned near 1, hold at 0). Its genotype diversity *interpolates*
between its branches (53.6, between explore 14.2 and hold 68) — so baldwin's value is
not a new attractor but a **self-regulating churn rate**. NB this reconstructs
baldwin's *structure*, not the 2014 fn bit-for-bit (baldwin mutates `(1- matches)`
times, a graded count, not a binary switch).

**Finding 2 — other compositions reach attractors NEITHER branch can (emergence).**
`scripts/exotype_demo.clj` runs three exotypes, each against BOTH its branches solo
(the branches are the nulls). Terminal distinct-rule counts, width 60, seeds 0–2:

| exotype | branch-A | branch-B | switch | verdict |
|---|---|---|---|---|
| baldwin-reconstructed (builder / identity, :boredom) | 45.3 | 46.7 | 48.0 | within noise — interpolates (cf. Finding 1: the signal is the rate, not diversity) |
| thermostat (collapser / builder, :active) | 1.0 | 45.3 | 1.0 | **honest negative** — tracks the collapser; collapse is the stronger attractor, no set-point |
| **annealer (chaos / collapser, :active)** | 14.3 | 1.0 | **31.7** | **EMERGENT** — settles at 2× either branch, at near-chaos activity 0.51 |

So conditional composition buys *either* a state-tracking rate (baldwin) *or* a new
attractor (annealer) — the exo layer is real physics, not a relabelled geno knob.

**Vocabulary was mined from the 20,256-orbit census** (each σ chosen for a measured
solo role: builder ~48 rules; quiet collapser → 1 rule, dies; chaos, activity .57).
The census also surfaced two facts worth keeping: a *loud collapser* exists (1 rule,
survives, activity .48 — monoculture chaos), and **peak class-4 population is a
MONOCULTURE** (every c4peak=1.0 propagator has 1–2 terminal rules — one class-4 rule
taking over, which is Rule 110's own situation).

**Open (the xeno step):** the (σ_A, σ_B, condition) triples here were hand-picked. The
annealer proves the composition space has emergent points; finding them is the job of
the evolutionary method (niche construction / the ant selection pressure), not of
guessing. Artefacts: `exotype-{annealer,thermostat,baldwin-reconstructed}.png`.

**Provenance note (claude-3):** `exotype_by_example.clj` and `exotype_demo.clj` were
written either side of a context compaction; the second re-derived the idea without
recall of the first. They are kept because they measure *different* things (rate vs
attractor); the duplication of framing is the cost of the memory gap, logged honestly.

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
