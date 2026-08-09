# TN-baldwin-reconstructed — the Baldwin effect, rebuilt from propagators

**Self-contained.** 2026-07-16, claude-3. Every number re-grounded against its artefact
before it went in (this note's own working discipline; two earlier numbers in
`M-propagators.md` were wrong precisely because they weren't).

**Claim in one line:** the 2014 MetaCA's Baldwin function is not a primitive. It is
`switch(local-condition, propagator, no-op)` — a **composition of propagators** — and what
that composition buys is a **state-dependent mutation rate**, not a new attractor.

---

## 1. The object: what the 2014 code actually does

`vendor/metaca/256ca-2014-12-29-BUGGY.el`, `evolve-sigil-with-blending-baldwin`. After the
blend step:

```elisp
(if context
    (let ((mutations 0)
          (to-match (car (last context))))     ; the NEW state
      (map 'list (lambda (elt)
                   (when (eq elt to-match)
                     (setq mutations (1+ mutations))))
           (nbutlast context))                 ; the 3 OLD context values
      (get-genotype-from-rule (mutate-genotype-n output (1- mutations))))
  (get-genotype-from-rule output))
```

Read it plainly: **count how many of the 3 old context values equal the new state, then
mutate `(1- mutations)` times.**

- `(dotimes (j -1) ...)` runs **zero** times, so 0 matches → no mutation.
- So: **bored (the context already matches what just happened) → mutate. Interesting (the
  context differs) → hold.**

That is an **endogenous objective** — an intrinsic drive written in 2014, and the ancestor
of every objective this mission later chased. It needs no edge-of-chaos oracle because it
never asks the question. It reads its own local boredom.

**It was the file's DEFAULT** at the Figure-8 commit (`:637` aliases `evolve-sigil-fn` to
it); the 2015 file re-aliases to `evolve-sigil-with-mutating-template` (`:1069`).

---

## 2. What it does, measured (H-baldwin-repro, `36db2df`)

15 seeds, unedited vendored elisp, instrumented from a lexical-binding side-file.
Artefact: `holes/labs/M-aif-tokamak/baldwin-repro/README.md`.

**The requested-mutation histogram — the drive, in numbers:**

| requested `(1- mutations)` | count | share |
|---:|---:|---:|
| −1 (no-op) | 1,869 | 1.79% |
| 0 (no-op) | 22,539 | 21.59% |
| 1 | 72,352 | 69.30% |
| 2 | 7,640 | 7.32% |

**76.62%** of interior calls request a positive mutation (74.07% of all cells);
**0.8114** mutation steps per cell. Contrast `blending-mutation`, which requests mutation
on **100%** of cells at a fixed 2 steps — no state-dependence at all.

**And it does NOT reproduce Figure 8.** Baldwin freezes the phenotype 15/15 but reaches
the diagnostic `{42,170}` attractor **0/15**; `blending-mutation` reaches it **15/15**.
Baldwin lands on `{0,42,170}` plus 0–5 stragglers, still mutating at the horizon. So the
Figure-8 reconstruction used the *right* function despite the file's misleading default
alias — the negative is what confirms the positive. (Full review of that claim, including
recomputation from raw genotype rows and a byte-identical determinism check, is in
`M-propagators.md §1.1`.)

---

## 3. The reconstruction: Baldwin is a composition

Joe's conjecture (2026-07-16): *if propagators are a basis for a MetaCA's global physics,
then named physics like "baldwin" or "blend" should themselves be compositions of
propagators.*

Baldwin's rule is literally that shape:

```
                  bored → mutate,  interesting → hold
   ==  switch(local-condition, a-propagator, no-op)
```

**Built as a first-class wiring component** — `:rule-permute-switch`
(`src/futon5/xenotype/generator.clj`, declared in
`resources/xenotype-generator-components.edn`):

```
apply σ_A where a per-cell condition holds, σ_B otherwise
```

Conditions read the per-cell context a single propagator ignores: `:boredom` (the
phenotype neighbourhood `[prev self next]` is uniform — nothing happening locally),
`:active` (its negation), `:dense`. Branches and condition are **data**, i.e. evolvable —
which is what a xeno layer would need.

**Honest scope.** This reconstructs Baldwin's *structure*, not the 2014 function
bit-for-bit: Baldwin mutates `(1- matches)` times — a **graded count** — where the switch
is **binary**; and Baldwin's condition is *temporal* (old context vs new state) where the
component's `:boredom` is the *spatial* proxy available in one cell update. Same shape,
different resolution. It is not offered as a port.

---

## 4. What the composition buys: a RATE, not an attractor

`scripts/exotype_by_example.clj` (width 80, 120 steps, propagator = rotate+2). Three
policies, identical apparatus:

- `explore` — fire the propagator always (the constituent, unconditional)
- `hold` — never fire (the other constituent)
- `exotype` — fire only where bored (**the Baldwin-flavoured switch**)

| policy | mut-rate start → end (mean) | diversity start → end | phe activity |
|---|---|---|---|
| explore | 1.000 → 0.000 (0.053) | 65 → **4** | 0.464 |
| hold | 0.000 → 0.000 (0.000) | 68 → **68** | 0.257 |
| **exotype** | **0.225 → 0.000 (0.022)** | 68 → **50** | 0.215 |

**Q1 — the finding. The exotype's mutation rate TRACKS LOCAL STATE:**

> first-third **0.055** → last-third **0.002**. It falls as structure emerges.

Neither constant policy can do this: `explore` is pinned near 1 by construction, `hold` at
0. A state-dependent churn rate is the signature of a genuinely new operator — and it is
exactly Baldwin's intent, recovered from propagator + condition.

**Q2 — and what it does NOT buy.** Mean genotype diversity: explore **14.2**, hold
**68.0**, exotype **53.6** — *strictly between its constituents*. The switch **interpolates**;
it does not reach anywhere new.

So **Baldwin's value is a self-regulating churn rate, not a new attractor.** That is a
sharper claim than "the switch is better", and it is the honest one.

---

## 5. Contrast: composition CAN reach new attractors — just not here

`scripts/exotype_demo.clj` runs three exotypes, each measured against **both its branches
solo** (the branches are the nulls). Terminal distinct rules, width 60, seeds 0–2:

| exotype | branch A | branch B | switch | verdict |
|---|---:|---:|---:|---|
| baldwin-reconstructed (builder / identity, `:boredom`) | 45.3 | 46.7 | 48.0 | within noise — interpolates (cf. §4: the signal is the rate) |
| thermostat (collapser / builder, `:active`) | 1.0 | 45.3 | 1.0 | **honest negative** — tracks the collapser; collapse is the stronger attractor |
| **annealer (chaos / collapser, `:active`)** | 14.3 | 1.0 | **31.7** | **EMERGENT** — 2× either branch, at near-chaos activity 0.51 |

**The annealer is the existence proof**: conditional composition *can* reach a regime
neither branch reaches alone. Baldwin simply isn't that kind of composition — both its
branches are already high-diversity (`identity` here is ordinary mutation, a diverse random
walk), so there is nowhere new to go. Composition buys **either** a state-tracking rate
(Baldwin) **or** a new attractor (annealer). Two different goods.

---

## 6. Why this matters beyond the reconstruction

- **It grounds the exotype layer.** "Exotype = a MetaCA's global physics, composed from
  propagators" had been a proposal. Baldwin is the **existence proof that a named,
  historical physics really is such a composition** — not a primitive.
- **It is the endogenous objective the tokamak needed and never had.** The tokamak was
  parked because its objective (genotype transport) is banked as *not* an EoC instrument,
  and `argmax` of a band-shaped property is Goodhart bait regardless (`M-propagators §4b`).
  Baldwin sidesteps the whole problem: it never asks whether it is at the edge of chaos, it
  asks whether it is bored. EoC, if it happens, is an **outcome you observe**, not a target
  you optimise.
- **It is the ants' objective, in CA form.** The ants work as a selection signal because
  they *just like to eat* — grounded, not a proxy. Baldwin is the MetaCA's version of
  liking to eat.

---

## 7. Provenance, and a caveat about this note's own sources

`exotype_by_example.clj` and `exotype_demo.clj` were written **either side of a context
compaction**; the second re-derived the idea without recall of the first. Both are kept
because they measure *different* things (rate vs attractor). The duplicated framing is the
cost of the memory gap, logged rather than hidden.

**Numbers here were re-grounded** (2026-07-16) by re-reading
`baldwin-repro/README.md` and **re-running** `exotype_by_example.clj`, not recalled. Two
numbers elsewhere in this mission (`M-propagators.md`) were wrong precisely because they
were carried rather than re-derived — a Fisher–Rao mean computed on a lexicographic prefix,
and two run-means stitched into a fake "range". Both were caught by Fable re-grounding the
paper draft. Assume recall is wrong; check the artefact.

## 8. Files

| file | what |
|---|---|
| `vendor/metaca/256ca-2014-12-29-BUGGY.el` | the original Baldwin fn (**evidence — do not edit**) |
| `holes/labs/M-aif-tokamak/baldwin-repro/README.md` | the 15-seed replay + histograms |
| `scripts/baldwin_repro.{clj,el}` | reproducer for §2 |
| `src/futon5/xenotype/generator.clj` | `:rule-permute-switch`, the composition component |
| `scripts/exotype_by_example.clj` | §4 — the rate-tracking measurement |
| `scripts/exotype_demo.clj` | §5 — three exotypes vs their own branches |
| `holes/missions/M-propagators.md` §1.1, §2c | the Figure-8 control; the exotype section |
