# P2 EXTENDED — the absorbing-byte axis, with controls

**Run on zone-joe 2026-08-04**, seven arms, 400 seeds each. Extends the original five with
two controls added at E0b, both preregistered before the run:

- **`:odd332` (3,3,2)** — a second zero-absorbing kind, so "never halts" no longer rests on
  one sigma.
- **`:even44` (4,4)** — the SAME absorbing count as `:collapser` (6,2) with a different
  maximum cycle length, breaking a collinearity that made the original five uninterpretable.

## The two tests

**P3 — replicate. CONFIRMED.** `:odd332` never froze in **400/400**, exactly as `:odd53`.
Two independent zero-absorbing cycle types, 800 runs, zero halts.

**P4 — the law breaks. CONFIRMED.** At equal absorbing count, median half-freeze time is
**15 for `:even44` against 20 for `:collapser`**, and the separation is consistent across
checkpoints, not an artefact of where the median crosses (frozen fraction at t=15: 0.56 vs
0.48; at t=20: 0.69 vs 0.54).

| kind | absorbing | max cycle | #cycles | median t½ | t½ × absorbing |
|---|---:|---:|---:|---:|---:|
| `:even4` (2,2,2,2) | 16 | 2 | 4 | 5 | 80 |
| `:even8` (4,2,2) | 8 | 4 | 3 | 10 | 80 |
| **`:even44` (4,4)** | **4** | **4** | **2** | **15** | **60** |
| `:collapser` (6,2) | 4 | 6 | 2 | 20 | 80 |
| `:even1` (8) | 2 | 8 | 1 | 40 | 80 |

**`t½ × absorbing = 80` is false.** It held on the original four because absorbing count and
maximum cycle length were perfectly rank-correlated there; `:even44` sits off the line at 60.
So **absorbing count is not the sole driver of halting speed** — cycle structure matters
independently.

Nor does maximum cycle length replace it: `:even8` and `:even44` share max cycle 4 and differ
10 against 15. The candidate reading, untested here, is that a byte halts only once *every*
cycle has resolved, so two 4-cycles (`:even44`) take longer than one 4-cycle plus two 2-cycles
(`:even8`) — a maximum over more slow draws. That is a hypothesis, not a result.

---

# Original report



All arms have `rate = 0.5000` exactly: the coordinate the generative model
represents is IDENTICAL across every row below.

400 seeds, width 80, 300 steps, blend 0, transfer 0.

| arm | absorbing | derived rate | control rate | t=0 | t=1 | t=2 | t=3 | t=5 | t=10 | t=15 | t=20 | t=30 | t=40 | t=60 | t=100 | t=200 | t=300 | median t½ | never |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `odd53` | 0 | 0.5000 | 0.5011 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 |  | 400 |
| `odd332` | 0 | 0.5000 | 0.5021 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 |  | 400 |
| `even1` | 2 | 0.5000 | 0.5018 | 0.01 | 0.02 | 0.03 | 0.04 | 0.08 | 0.17 | 0.27 | 0.33 | 0.45 | 0.55 | 0.71 | 0.88 | 0.98 | 0.99 | 40 | 0 |
| `even44` | 4 | 0.5000 | 0.5023 | 0.02 | 0.03 | 0.05 | 0.08 | 0.15 | 0.34 | 0.56 | 0.69 | 0.82 | 0.91 | 0.98 | 1.00 | 1.00 | 1.00 | 15 | 0 |
| `collapser` | 4 | 0.5000 | 0.4995 | 0.02 | 0.03 | 0.05 | 0.09 | 0.16 | 0.33 | 0.48 | 0.54 | 0.66 | 0.80 | 0.91 | 0.98 | 1.00 | 1.00 | 20 | 0 |
| `even8` | 8 | 0.5000 | 0.5012 | 0.03 | 0.06 | 0.11 | 0.17 | 0.30 | 0.53 | 0.70 | 0.81 | 0.92 | 0.95 | 0.98 | 1.00 | 1.00 | 1.00 | 10 | 0 |
| `even4` | 16 | 0.5000 | 0.4998 | 0.06 | 0.13 | 0.22 | 0.34 | 0.59 | 0.88 | 0.96 | 0.99 | 1.00 | 1.00 | 1.00 | 1.00 | 1.00 | 1.00 | 5 | 0 |
