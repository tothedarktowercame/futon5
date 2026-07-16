# TN-propagator-paper — outline for an ALife submission

**Status:** OUTLINE ONLY (2026-07-16). Not a draft. Slots the *actual* artefacts and
measured numbers into a shape, so the writing later is assembly rather than invention.
**Venue:** ALife — the ALIFE conference (~8pp) or *Artificial Life* (MIT Press, rolling).
NOT eLife (life sciences; we have no biological finding). **Deadline unknown to the agent
— check; ALIFE 2026 is past claude-3's knowledge cutoff.**

**Why ALife specifically:** this community has chased the edge of chaos since Langton's λ.
The EoC material below is an *asset* here and would be a *gap* anywhere else.

---

## The core decision: one paper or two

**Paper B is the stronger and more finished contribution, and Paper A is its setup.**
Recommendation: **one paper, B's thesis, A as the first half.** B is counterintuitive,
complete, and says something the field wants said; A alone is "we found a neat object".

---

## Paper A — the object (setup, ~40%)

**A 12-year-old Emacs off-by-one turns mutation from a random walk into a constraint
propagator over the rule's bit-planes.**

1. **The reconstruction.** Figure 8 of arXiv:1502.00130, from the original 2014 code.
   `mutate-genotype-n` @ `2f62f59` reads at 0-based `pos`, writes at `(goto-char pos)`;
   Emacs buffers are 1-based and `(goto-char 0)` silently clamps to point-min.
   - **We correct the paper's own text**: it says the genotype flutters between rules 0 and
     128. It does not — it flutters between **42/170**. Proof from the paper's *own
     artefact*: rule 0's colour `#000000` and 128's `#808080` do not appear in `eoc.png`;
     its dominant colours `#2a2a2a` (126k px) and `#aaaaaa` (36k px) are exactly what
     `256ca.el` assigns to 42 and 170. Joe's structural description (two rules differing
     in one bit) was right; the identities were wrong.
   - **We correct the prose's extent too**: "only ever flips the first bit" is a gloss.
     Measured over 87,632 instrumented writes: bit 0 takes **24.946%** (the predicted 2/8),
     bits 1–6 ~12.5% each, bit 7 **0.000%**. It *doubles* bit 0 and *never* writes bit 7.
   - **Confirmed against a control.** The Figure-8 commit's own default is baldwin, not the
     blending-mutation we used. Baldwin reaches the 42/170 attractor **0/15**; blending-
     mutation **15/15**. The reconstruction used the right function despite a misleading
     alias — and the figure is *not* default-function robust, which is why the control was
     needed. `36db2df`.
   - Artefacts: `figure8_REPRODUCED.png`, `figure8_x15_repro.png` (no cherry-picking),
     `baldwin-repro/README.md`.

2. **The bug generalises to a family.** Operator: pick k; `bit[σ(k)] := ¬bit[k]`.
   σ = identity is a random walk with no attractor; non-trivial σ *couples* the planes.
   **112/202 sampled σ live (55%)** — the live regime is generic; the dead set is the rare
   thing.

3. **The conjugacy theorem** (`dbb8453`) — the paper's sharpest formal result.
   The live twin `(0 2 4 6)(1 3 5 7)` and dead twin `(0 1 2 3)(4 5 6 7)` are **exactly
   conjugate** via τ = `[0 4 1 5 2 6 3 7]`; 2048/2048 transitions commute. Therefore **no
   σ-only property can separate live from dead.** This killed our own "gcd law" (which had
   fit 12/12 rotations incl. 4 out-of-sample) and it forecloses every count-the-orbits
   story. What separates them lives in *what τ scrambles*.

4. **Exhaustive census.** Burnside reduction (mirror + 192 fixed σ) → exactly **20,256
   orbits**; complementation rejected (boundaries not complement-invariant). Full census
   complete, fingerprinted (`ac2ff1681eae5b85`), all four anchors passing.

5. **Exact fixed points, all 40,320 σ** (JAX). On an isolated byte the propagator *is* a
   256×256 Markov chain — no sampling needed.
   - **No σ has a single-byte attractor** (0.0% at support ≤1; 11.5% ≤4, 54.1% ≤32, 88.0%
     ≤64). A fixed byte would need `bit[σ(k)] = ¬bit[k]` for all k at once, which an
     always-invert operator forbids. **The "fixed point" is never a fixed byte — it is an
     invariant SET.** This corrects our own one-liner.
   - Port test reproduces §1.3's hand-measured rows exactly, incl. identity → uniform
     support 256 (*the random walk, no attractor* — now proved, not sampled).
   - **Blending is not a detail**: isolated attractors are low-weight bytes (7, 11, 13, 19);
     the census's collapse targets are **232 (majority)** and **204 (identity)**. Same
     operator, different object — *blending is what selects majority/identity as death*.

---

## Paper B — the thesis (~60%)

**Why the edge of chaos cannot be measured here — and what that feels like from the inside.**

6. **The fail-bank IS the contribution.** Nobody publishes these; they are the paper.
   - **Cμ (causal-state count)** clears the Rule-110 bar on ECA, then a *frozen barcode*
     scores **39–44** vs Rule 110's **26.4**. It reads temporal stability, not complexity,
     and grows with sample size (38 over 160 gens, ~17 over 40-gen windows). Binning drives
     the verdict (k=2 vs k=4 flips it).
   - **Aliveness** earns its stripes *within* one family (Joe's blind pick ranked 2/15),
     then breaks *across* families: it top-ranks stripes-plus-snow.
   - **Run-level means**: the Figure-8 transient is 8–20 gens; a 100-gen mean reports every
     one of 15 living seeds as dead.
   - **THE PINCER — the finding.** *Phenotype* diagonal transport has a positive anchor
     (class-4 > class-3 > settled, every seed) but **fails its null** (frozen barcode
     .1814 > Rule 110). *Genotype* transport passes its nulls (identity .12 < live floor
     .196, 15/15; Figure 8 decays to exactly 0) but **has no anchor** (there is no ECA
     genotype field). **Each has exactly what the other lacks.**

7. **And it is a theorem, not a gap.** Wolfram class membership is **undecidable** (Culik &
   Yu 1988); nilpotency **undecidable** (Kari 1992); Cook proved Rule 110 universal **by
   construction**, never by measurement. So "build an EoC instrument" is not a well-posed
   goal. **The eye outperforming every instrument is not an embarrassment to fix — it is
   what undecidability feels like from the inside.** Also: EoC is a *band*, so `argmax` of
   any proxy is Goodhart bait regardless.

8. **The geometry: no natural joints.** Triangulated across three metrics, each with
   controls run on the same instrument:
   - *Euclidean on 18 hand-made features*: silhouette declines monotonically .441→.267. No
     elbow. (Honest gap: this one never got a blob control.)
   - *Fisher–Rao on the terminal distributions* (Čencov: the unique metric invariant under
     sufficient statistics): **flat .07–.08**. Controls: blob .004, real clusters .247,
     obvious joints .696.
   - *Wasserstein-1 under a Hamming ground metric* — **forced, not chosen**: `rule-permute`
     writes exactly one bit, so a propagator step *is* a Hamming step and rule space *is*
     the 8-bit cube. And `legacy→standard` is a bit *permutation*, which preserves Hamming
     — so the ground metric is convention-independent. Result: **0.176 vs a 0.088 blob and
     0.690 real structure.** Flat.
   - **It survives its best attack.** Pooling the last 20 generations gave 20× denser
     distributions (43.8 → 118.9 occupied bins; mean pairwise FR 2.600 → 2.301 — saturation
     measurably reduced) and the silhouette **stayed flat** (.084 → .106). So the metric was
     saturating *and* the space has no joints: two independent facts we had conflated.
   - **Conclusion: a continuum with a slight non-uniformity, not a set of kinds.**

9. **Methods contributions worth their own subsection.**
   - **The mean-field embedding.** Hamming is a sum over bits, so W₁ nearly decomposes into
     per-bit marginal differences: `W1(p,q) ≥ Σ_b |E_p[b] − E_q[b]|`, a **certified lower
     bound measured at 0.957–0.965 of exact** (never violated, sometimes exact). The 8
     numbers are interpretable — the per-neighbourhood mean response of the rule population
     — dense, and exactly the object the propagator acts on. **205M pairs become instant.**
     (Sinkhorn was tried and dropped: 43 ms/pair, *slower* than exact LP's 31.)
   - **Ollivier-Ricci curvature** as navigation for a space with no clusters to hop between:
     mean κ **+0.085**, **30.3% negative** (the frontier / "propose here"). And: **80% of the
     most negatively-curved edges connect σ differing by a single TRANSPOSITION** — a
     minimal σ-edit sits at a branch point — **surviving its artifact test** (83% with
     near-duplicates excluded, where 1/d amplification could have manufactured it).

---

## What is deliberately NOT in the paper

- **The transfer thesis.** "Patterns of improvisation", computational intelligence that
  transfers domain-to-domain. **Not established.** The tokamak is parked with an unanchored
  objective (§4b); the ant C-vector authority gate passes but the xeno loop is
  undemonstrated. Gesturing at it would be the exciting version we cannot support.
- The tokamak's own numbers (greedy .2458 v best fixed .2418 is 3/4 seeds with one seed
  losing by more than the mean effect — suggestive, not banked).

## Known weaknesses a reviewer will find first

1. **The census is 3 seeds per σ.** Every live/dead and collapse-target claim rests on it.
   *Fixable*: a JAX MetaCA port **with blending** would give 100+ seeds cheaply — the one
   place a JAX census genuinely pays (the wide-grid version was killed by the pooling test).
2. **Width 60, 120 generations.** Defensible (transient is 8–20 gens) but will be asked.
3. **The Euclidean-18-feature sweep has no blob control.** Owed.
4. **No related-work grounding yet.** Langton (λ); Packard; Mitchell/Crutchfield/Hraber
   ("Revisiting the edge of chaos" — the classic negative, our closest ancestor); Culik &
   Yu; Kari; Cook; Čencov; Ollivier. This is a literature search, not an experiment.

## Figures (all exist)

`figure8_REPRODUCED.png` · `figure8_x15_repro.png` · `propagator-clusters/contact_sheet.png`
(10 exemplars, genotype+phenotype, full coverage) · `fisher_rao_vs_euclidean.png` (with
controls) · `wasserstein.png` (bound tightness + flat silhouette) · `curvature.png` ·
`tokamak_run_figure.png` (if the tokamak appears at all)

## Reproducibility (a genuine strength — lead with it)

Fingerprinted census (`ac2ff1681eae5b85`) over sha256 of every source input; seeded-shuffle
build order so **any prefix is a uniform sample** (the 580-orbit overnight preview predicted
the full 20,256 result to 0.7%); byte-identical determinism verified by deleting and
regenerating a seed; `vendor/metaca/` carried as evidence, never edited.
