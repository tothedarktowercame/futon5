# Mission 0 Report — Lessons, Gaps, and Next Plan (2026-01-22)

This report synthesizes **exotic-programming-notebook.org**,
**exotic-programming-notebook-ii.org**,
**exotic-programming-notebook-iii.org**, plus the associated futon5
code (exoevolve, metrics/scoring, lift registry, CT scaffolding).  It
focuses on: (1) where we *did* see edge-of-chaos (EoC), (2) how CT
interpretations are formed, and (3) why current runs are weaker than
historical bests. It ends with a concrete plan to improve quality,
interpretability, reproducibility, and to make run-on-run improvement
the default outcome.

---

## Executive Summary

- **We have observed EoC** in prior runs, most clearly in **Mission
  17a refine baseline/exotic** (seed 4242, sigil 工 in the 泰 zone)
  and in **Mission 5** (long-transient, Rule‑110‑like behavior that is
  fragile under evaluator kernels). These are *not* persistent across
  all current runs.

- **Current Mission 0 runs (modest/modest50, envelope sweep, and
  partial ratchet overnight)** produce interesting
  exotype/exotype-level structure but *often lack convincing
  phenotype-level EoC*, and are scored inconsistently with human
  judgments.

- **CT interpretation exists but is thin**: we can parse CT templates,
  build vision skeletons, and validate categories, but **most sigils
  have no CT template**, so they fall back to degenerate visions. This
  limits interpretability and undermines the “CT-native” claim.

- **Scoring alignment is weak**: ensemble scores
  (envelope/triad/shift/filament) have weak or even negative
  correlation with HIT judgments on mixed-regime batches; current
  scoring emphasizes exotype-level patterns and misses phenotype-level
  EoC.

- **Reproducibility is brittle**: exoevolve logs do not store full
  histories; replays are required for rendering; EDN contains
  `#object` tags that break tooling; long runs have stopped early
  without a clear error log.

---

## Design Pattern Definition (working)

A **design pattern** is a learned generalization arising from a review
process that describes how intentions, actions, and outcomes tend to
relate across situations. A candidate pattern becomes a pattern only
when it has survived repeated enactment‑and‑review cycles in which its
failure modes are recorded and used to revise it.

This matters because (a) futon3/fulab can turn coding‑agent
interactions into **proto‑patterns**, and (b) our CT compressor should
treat patterns as living hypotheses, not static texts.

---

## What We’ve Learned (by notebook)

### Notebook I (exotic-programming-notebook.org)

**Instrumentation works**
- Mission 1 establishes that exotypes produce *measurably different score distributions* and that the system is instrumentable (per‑exotype differences, stable histogram patterns).

**Baseline evolution collapses**
- Mission 2 shows naive evolution collapses into a narrow score band; this is the baseline failure case.

**Exotypes change trajectories, but are fragile**
- Mission 3 confirms local vs super exotypes materially change outcomes.
- Mission 4 shows exotype evolution can improve distributions but also *collapses* in many cases. High score ≠ richer dynamics.

**Where EoC is visible**
- Mission 5 (Rule‑110‑like behavior) shows long transient dynamics. **Evaluator kernels can erase EoC**: “high” kernels preserve richer dynamics in some seeds but collapse others.
- Mission 5a shows that short-cycle detection is needed to distinguish true long transients from short-period attractors.

**Xenotype blending helps**
- Mission 6 shows xenotype blending shifts score distributions upward and reduces evaluator collapse. This is a concrete signal that a *meta‑evaluation layer* can help.

**Key lesson**: EoC is real but fragile under evaluator choices. Simply optimizing exotype score is not enough to preserve EoC.

---

### Notebook II (exotic-programming-notebook-ii.org)

**Ratchet and curriculum integration**
- Ratchet state and curriculum gating are wired into exoevolve. This creates a mechanism for “improvement over time,” but early versions saturated (window means jumped into 90–100 range). Recent normalization fixed this (see notebook III).

**CT interpretation is real (but minimal)**
- Mission 17: CT template parsing works; CT templates in `resources/exotype-xenotype-lift.edn` can produce **valid categories**. The `pattern-repl` handshake converts CT templates → vision → functor stub → lift proposal.
- This is *structurally correct* but **sparse** (only a few templates).

**Historical EoC highlight**
- Mission 17 refine: “early EoC structure appears, but destabilizes later.”
- Mission 17a variant (xeno-weight 0.2): **sigil 工 (update-prob 0.5)** shows a partially stable EoC band. This is the most consistent historical EoC exemplar.

**Key lesson**: We have at least one *known‑good* EoC regime (M17a + 泰 zone), but it is fragile, and CT interpretation is not yet robustly attached to exotypes.

---

### Notebook III (exotic-programming-notebook-iii.org)

**EoC → hexagram link**
- The 泰 zone hypothesis (update‑prob/match‑threshold ~0.3–0.7) predicts M17a’s most stable EoC exotype (工). This is the strongest cross‑notebook “theory → evidence” alignment.

**Scoring ensemble + HIT are misaligned**
- Mixed‑regime HIT results show weak or negative Spearman correlations for some scorers; the ensemble is not a reliable proxy for human EoC judgment.
- In Mission 17a compare, envelope and filament are better but still miss key cases (including the “best run seen”).

**Current Mission 0 runs are weaker**
- Modest and modest50 runs show *interesting exotype-level structure but weak phenotype EoC*; some runs show phenotype collapse while exotype/genotype remain busy.
- Envelope sweeps produce higher envelope scores but not clearly better visual EoC.

**Approach 2 (wiring diagrams) shows promise**
- Small Cyber‑MMCA HIT suggests wiring controller outperforms hex in a tiny sample (n=4 per controller). This hints that wiring‑diagram synthesis is a stronger path than static scoring.

**Ratchet improvements + evidence persistence**
- Ratchet normalization is in place (delta mean + q50, stddev normalization). Evidence is now persisted into iiching (`@exotype-ratchet`).
- However, evidence is sparse (few entries), and CT templates are missing for many sigils (e.g. exotype‑197 / sigil “厉”).

**Key lesson**: We are **measuring more but not converging**. Scorers are not aligned to human EoC judgment, and generation methods may be too far from the known‑good regime.

---

## How CT Interpretations Are Formed (Current Pipeline)

Code references:
- `futon5/resources/exotype-xenotype-lift.edn`: lift registry
- `futon5/src/futon5/exotic/pattern_repl.clj`: CT handshake
- `futon5/src/futon5/exotic/vision.clj` + `category.clj`: category construction/validation

Pipeline (current):
1. **Pattern ID → Lift Registry** (`exotype-xenotype-lift.edn`)
2. **CT Template** extracted (if present)
3. **Vision skeleton** built (`vision/build-vision-from-template`)
4. **Category** validated (`category/valid-category?`)
5. **Plan functor stub** created (placeholder)

Important gaps:
- Most sigils have **no CT template**, so the system falls back to a **degenerate vision**.
- Evidence from runs is stored in iiching flexiargs, but **there’s no automatic CT template generation** from evidence yet.

**Implication**: Interpretability is structurally supported but semantically sparse. Without richer CT templates, EoC explanations remain mostly rhetorical rather than mechanistic.

---

## Why Current Runs Are Not as Good as Historical Runs

1. **Generation regime drift**
   - Mission 17a’s stable EoC examples were produced under a specific regime (xeno‑weight 0.2, mid‑range params, stable seeds). Mission 0 runs use different regimes and shorter horizons.

2. **Scoring misalignment with phenotype**
   - Many high‑scoring runs show exotype/genotype structure while phenotype collapses. This indicates the scoring function is **over‑weighting exotype dynamics** and under‑weighting phenotype EoC.

3. **Short horizons**
   - Many Mission 0 runs use length 50 / generations 30. Historical EoC runs used longer horizons (e.g., Mission 17 refine visuals at 120). Short windows may not let EoC stabilize.

4. **Evaluator fragility**
   - Mission 5 demonstrates that EoC‑like dynamics are fragile under evaluator kernels. This fragility likely persists in Mission 0, where evaluators strongly shape dynamics.

5. **Scorer disagreement / weak HIT alignment**
   - Mixed‑regime HIT results show weak or negative correlation between scorers and human judgments. We are *optimizing the wrong proxies*.

6. **CT interpretation sparsity**
   - Without CT templates for most sigils, the CT layer cannot guide selection meaningfully. This undermines CT‑native evaluation and transfer.

7. **Reproducibility gaps**
   - Exoevolve logs don’t store histories; replays are required. EDN contains `#object` tags, breaking downstream tools. Runs stopping early without error logs prevents root‑cause analysis.

---

## Plan: Improve Quality, Interpretability, Reproducibility, and Run‑to‑Run Improvement

### Phase 0 — Reproducibility & Diagnostics (Immediate)

**Goals**: reliable runs, full provenance, debuggable failures.

- Add robust run wrapper: stdout+stderr logs, exit code, retry on crash.
- Ensure *full history storage* for top/mid/low runs (save-run artifacts at selection time, not just replay later).
- Add EDN sanitation step in toolchain (strip `#object` tags during logging or after write).
- Capture run metadata consistently: seed, length, generations, kernel spec, exotype, regime ID.

**Success criterion**: overnight runs complete >95% of requested runs without silent truncation; rerenders require no ad‑hoc sanitation.

---

### Phase 1 — Return to Known‑Good Regimes (Quality Baseline)

**Goals**: reproduce historical EoC and lock it as a regression target.  This is particularly good for tuning evaluation regimes (can they at least distinguish between known-good examples and more recent failure modes?)

- Re‑run Mission 17a refine baseline/exotic with multiple seeds (including 4242) at longer horizons.
- Fix a “known‑good” EoC regression suite: 5–10 seeds with fixed init states, stored full histories, rendered triptychs.
- Establish “EoC anchor” score thresholds from these runs (envelope, filament, triad, shift).

**Success criterion**: current pipeline can reproduce EoC bands matching Mission 17a in ≥70% of the anchor suite.

---

### Phase 2 — Scoring Alignment & Human Calibration

**Goals**: make scores reflect human EoC judgment.

- Create a HIT‑aligned calibration set (larger than 20 seeds; active learning on disagreement cases).
- Train a simple preference learner (linear weights) to align scorers with HIT labels.
- Add phenotype/genotype/exotype balance (explicit 1/3 weighting or multi‑objective scoring).
- Use disagreement patterns as *signal* (not noise): EoC may live at boundaries where scorers disagree.

**Success criterion**: scorer agreement ≥0.65 exact / ≥0.75 ordinal on HIT set; no negative Spearman.

---

### Phase 3 — Generation Diversity & Regime Pooling

**Goals**: reduce mode collapse; explore broader dynamic space.

- Use the **regime pool** (baseline, exotype, hex, wiring, cyber‑mmca, fixed genotype) in mixed batches.
- Parameter sweeps (update‑prob × match‑threshold) with ridge navigation instead of fixed points.
- Ensure phenotype is present by default (don’t run “baldwin‑off” unless explicitly testing).

**Success criterion**: mixed regimes produce visually diverse phenotypes and increased EoC discovery rate vs single regime.

---

### Phase 4 — CT Layer Enrichment

**Goals**: make CT interpretation real and actionable.

- Fill missing CT templates for sigils that appear in evidence (e.g., “厉”).
- Auto‑attach CT templates via lift registry before selection decisions.
- Make PSR/PUR records explicit in run logs, linking selection rationale → evidence outcome.

**Success criterion**: ≥50% of evidence‑bearing sigils in iiching have CT templates; CT annotations appear in logs and are used in selection or reporting.

---

### Phase 5 — Run‑to‑Run Improvement Loop (Ratchet + Evidence + Transfer)

**Goals**: the system improves on average across runs.

- Ratchet evidence updates iiching continuously; run selection draws from that evidence.
- Add a gate+rank pipeline that uses learned scorer weights plus regime gates (freeze/magma) to prevent collapse.
- Periodic cross‑domain checks (ants/cyber‑mmca) as tiebreakers.

**Success criterion**: moving window of mean score increases across runs (or stable mean with higher EoC hit rate), plus rising agreement with HIT labels.

---

## Immediate Next Actions (Suggested)

1. **Stability pass**: make the overnight runner emit stderr logs and retry on crash; verify full 1000‑run completion.
2. **Regression suite**: recreate Mission 17a baseline/exotic at longer horizons (seeds 4242 + 5 new). Save full histories.
3. **Calibration upgrade**: expand HIT set and train a simple preference weight model.
4. **CT template gap fix**: fill missing CT templates for evidence‑bearing sigils (starting with “厉”).
5. **Mixed‑regime batches**: use regime pool to avoid single‑regime collapse.

---

## Note on CT Candidate Scoring (Option B)

CA dynamics are *not* influenced by a CT template alone. To score CT
choices with CA, we must bind each CT candidate to an **exotype
override** (e.g., update‑prob, match‑threshold). That is why the CT
candidate harness uses **Option B**: the CT choice is evaluated through
its associated exotype parameters, so the CA run actually differs.

## Closing

We have real evidence that EoC exists and is controllable (Mission 17a, Mission 5). We also have the beginnings of a CT‑native interpretation stack. What’s missing is **alignment between metrics and human judgment**, **generation regimes that preserve phenotype EoC**, and **a robust reproducibility pipeline**. If we fix those, the ratchet + evidence loop can finally become a mechanism that improves run‑to‑run on average.
