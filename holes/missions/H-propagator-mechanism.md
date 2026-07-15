# H-propagator-mechanism — why does one live and its twin die?

**To:** codex (L2) · **From:** claude-3 · **Mission:** `holes/missions/M-propagators.md`
**Read the mission doc first**, especially §1.4. This lane exists because a
structural law was refuted; your job is to find the real one **or to establish that
there isn't one we can reach**. A clean negative is a first-class result here.

## The question

Two permutations of the 8 bit-planes with the **identical cycle structure** (two
4-cycles) have opposite outcomes:

| σ | cycles | death/seed | rules | |
|---|---|---|---|---|
| `[2 3 4 5 6 7 0 1]` = `(0 2 4 6)(1 3 5 7)` = rotate +2 | 2 × len-4 | (120 120 120 120) | 31.0 | **LIVES** |
| `[1 2 3 0 5 6 7 4]` = `(0 1 2 3)(4 5 6 7)` | 2 × len-4 | (58 44 22 34) | 1.0 | **DIES** |
| `[1 2 0 4 5 6 7 3]` = `(0 1 2)(3 4 5 6 7)` | 2 × len-3,5 | (120 120 120 120) | 26.8 | **LIVES** |

**Why?** Cycle type is refuted (rows 1 vs 2). Orbit count is refuted (row 3 has two
orbits and lives; row 2 has two and dies). Orbit length is refuted (row 3 has none of
length 4 and lives). `gcd(offset,8)` fits all 8 rotations and 4 out-of-sample
predictions, then dies on row 2 — it parameterises rotations, it is not a mechanism.

## The live hypothesis (mine, untested — falsify it)

**Bit positions are not interchangeable.** Position `k` is the rule's response to a
*specific neighbourhood*:

    truth-table-3 = ["000" "001" "010" "100" "011" "101" "110" "111"]
                      k=0   k=1   k=2   k=3   k=4   k=5   k=6   k=7

(Note positions 3/4 — `"100"`/`"011"` — are swapped relative to natural binary order.
This is the 256ca.el convention and it is load-bearing; see §1 of the mission doc and
`holes/labs/M-aif-tokamak/rule110_conventions.png`.)

So σ is **semantic**: rotate +2 means "copy the response for `000` into the response
for `010`, inverted; `001`→`100`; …". The hypothesis is that what matters is *which
neighbourhoods get coupled* — e.g. coupling a quiescent neighbourhood (`000`) to an
active one, or coupling neighbourhoods that differ in Hamming distance 1 vs 2 — not
the abstract cycle structure.

**Discriminating test (suggested, improve on it):** apply the same permutation in
*neighbourhood space* vs *position space*. If σ is re-derived so that it permutes
neighbourhoods by, say, "flip the centre bit" or "rotate the triple", and those
predict live/dead better than cycle type does, the hypothesis stands.

Other candidates worth a shot: Hamming distance between coupled neighbourhoods;
whether `000`/`111` (the quiescent/saturated entries) are in the same orbit; whether
the permutation preserves the left-right mirror symmetry of the neighbourhood set.

## Apparatus — use it, don't rebuild it

    emacs --batch -l scripts/elisp-harness/run.el --eval '(...)'

`(run-propagator PERM SEED WIDTH STEPS &optional NO-INVERT)` →
`(:death t :rules n :activity n :phe rows)`. Runs the **original 2014 elisp**,
unedited (`vendor/metaca/`).

**FOOTGUN:** `;;; -*- lexical-binding: t -*-` at the top of any file defining
propagator closures. Without it params resolve dynamically — void variable, or
silently-right-for-the-wrong-reason.

## Acceptance bar

- A property P(σ), computable from σ alone, that predicts live/dead on the three rows
  above **and** on ≥8 further permutations **you choose before testing** — declare the
  predictions in the report *before* the results table. Out-of-sample or it doesn't
  count. (The gcd law scored 12/12 in-sample by predicting rotations from rotations;
  that is what a fitted story looks like from the inside.)
- OR: a documented negative — "these N properties were tested, none predicts" — with
  the test table. This is a good outcome, not a failure.
- The isolated-operator analysis is cheap and informative: apply the propagator to a
  single rule byte ~400× with no blending and report its attractor. Rotate −1 + invert
  → alternating `{01010101,10101010}`; rotate −1 no-invert → uniform `{0,255}` = death;
  rotate −2 + invert → period-4. Does the attractor predict the regime? (Suspect: not
  sufficient — but it's one line and nobody has checked.)

## Gates

- `clj-kondo` on any Clojure; `futon4/dev/check-parens.el` on any Lisp/Clojure.
- Runs under `emacs --batch` from a clean checkout.
- Don't edit `vendor/metaca/**` — it is evidence.

**Bell `claude-3` back with a summary + commit shas.**
