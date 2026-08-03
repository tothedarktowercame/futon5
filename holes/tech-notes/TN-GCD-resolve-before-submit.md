# TN-GCD-resolve-before-submit — the gcd(offset,8) classification is stated two ways

**Status: parked defect, 2026-08-03. Not being fixed now.** Raised so it cannot be
submitted unnoticed. No action required until the supplement is prepared for
submission; at that point one of the two statements below has to go.

## The conflict

**Supplement 3, `supplement3-figures.tex`, caption of `\label{fig:eoc}`:**

> Of these all-even rotations only the single 8-cycles, $\gcd(\text{offset},8) = 1$
> --- offset $\pm 1$ and $\pm 3$, highlighted --- sustain a high-diversity phase;
> offset $\pm 2$ ($\gcd 2$) and $\pm 4$ ($\gcd 4$) collapse.

**`futon5/holes/missions/M-propagators.md` §1.4**, headed *"The gcd 'law' — REFUTED.
Read this before believing any offset story."*:

| gcd | orbits | outcome |
|---|---|---|
| 1 (`±1, ±3, ±5`) | one 8-cycle | collapse to 1–2 rules, **dies** |
| **2 (`±2, ±6`)** | two 4-cycles | **~25–35 rules, lives, structured** |
| 4 (`±4`) | four 2-cycles | saturates black, **dies** |

Same parameterisation, same cycle structure, opposite verdicts for gcd 1 and gcd 2.

## Independent measurement does NOT adjudicate, and weakly favours the supplement

Measured 2026-08-03 in `mmca-clj` (`scripts/writing_axis_probe.clj`), band under
causal reach, writing operator rebound to rotation by offset `d`, **20 random genomes
per offset** plus the evolved genome:

| offset | gcd | evolved | random mean | random median | min–max | frac>0.5 |
|---:|---:|---:|---:|---:|---|---:|
| 0 | 8 | 0.0000 | 0.0000 | 0.0000 | 0.0000–0.0000 | 0.00 |
| 1 | 1 | 0.2615 | 0.4362 | 0.4770 | 0.0000–0.9511 | 0.45 |
| 2 | 2 | **0.9655** | 0.4615 | 0.5316 | 0.0000–0.8477 | 0.65 |
| 3 | 1 | 0.3621 | 0.5693 | 0.6638 | 0.0000–0.8937 | 0.65 |
| 4 | 4 | 0.0000 | 0.0000 | 0.0000 | 0.0000–0.0000 | 0.00 |
| 5 | 1 | 0.7356 | 0.6034 | 0.6466 | 0.0144–0.9224 | 0.80 |
| 6 | 2 | 0.5747 | 0.4332 | 0.5029 | 0.0000–0.9626 | 0.50 |
| 7 | 1 | 0.5460 | 0.6191 | 0.7615 | 0.0000–0.9799 | 0.70 |

Aggregated by class: **gcd 1 → 0.5570**, gcd 2 → 0.4474, gcd 4 → 0.0000,
gcd 8 → 0.0000.

So on this observable gcd 1 scores *above* gcd 2 — the supplement's direction, not the
mission's. Both agree that gcd 4 (and gcd 8, i.e. the identity) is dead.

**This does not settle the question, and should not be cited as if it did:**

- Band under causal reach is a different observable from the survey's "sustained
  high-diversity phase" and from the mission's rules-surviving census. Three
  measurements of three things.
- Between-genome variance swamps the between-offset differences: single offsets span
  0.0000–0.9799 across 20 draws, against a gcd-1-vs-gcd-2 gap of 0.11.
- gcd 2 has only two offsets (2 and 6) in an 8-point space, so its class mean is two
  numbers.
- The evolved genome's peak at offset 2 (0.9655) is confounded — it evolved under
  rot+2 and is specialised to it. The random column is the unconfounded one.

**Correction, logged:** an earlier version of this note claimed the measurement sided
with the mission. That was written from the first three offsets before the run
finished, and the completed table reverses it. The partial data genuinely pointed the
other way; the error was drawing a conclusion from it.

## Also from §1.4, and more damaging than the direction

The law **does not generalise**. It was broken by testing non-rotation permutations
on the same substrate: the gcd regularity is a rotation-subfamily confound, not a
property of the operator space. So even the version that survives adjudication should
not be stated as a law about permutations — only about rotations, and then only as a
description of that slice.

## What has to happen before submission

1. Decide which observable the caption is claiming — genotype-diversity survival (the
   mission's) or something else — and say so explicitly.
2. Re-measure that observable across the offsets, since the two existing measurements
   disagree and neither was made to settle this.
3. Either correct the caption's direction, or restrict its claim to the observable and
   subfamily it actually holds for, and cite §1.4's refutation so the reader is not
   left with a law that does not generalise.

Related: `futon5/holes/missions/M-propagators.md` §1.3–1.4;
`mmca-clj/scripts/writing_axis_probe.clj`.
