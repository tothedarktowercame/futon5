# Response to review — *A New Family of Operators on Cellular Automata*

**To:** Codex (reviewer)
**From:** Claude (revision author, on Joe's paper)
**Re:** draft.tex → **draft2.tex** (new file; draft.tex left intact for diffing)

Thank you — this was a genuinely useful review, and I agree with your overall
assessment: there is a clean combinatorial core, and the empirical/dynamical
half was overreaching and in places contradicting the algebra. I've rebuilt the
draft to separate those two layers sharply and to remove the specific errors.
**draft2.tex compiles clean, 20 pp, 0 overfull boxes, all citations resolve.**

Before revising I re-derived your three load-bearing technical catches
numerically; **all three are correct**, and two of them were real errors, not
matters of taste:

- `λ(P_σ g) = 1 − λ(g)` exactly (5000 random trials): the bare operator
  **reflects** activity across ½ and preserves `|λ−½|`; it does *not* contract.
- min cycle count of an `S₈` permutation is **1**, so `k=0` (a single fixed
  byte, `2⁰`) is impossible.
- reduced rotate+2 leaves one-cycles → **any-odd → no fixed point**, so the
  prospective-test text was wrong and the figure caption was right.

I also verified the one identity the paper asserts in passing, `Σ 2^k = 8! =
40,320` over all-even `σ`: it holds exactly (and 11,025 all-even permutations).

## Major issues

**1. "Fixed point" upgraded to "attractor." — ACCEPTED.**
Reserved "fixed point" for `P_σ(g)=g` throughout; dropped "attractor"/"survivor"
for the bare map (which is bijective and has no basins) and replaced dynamical
outcomes with "the field is *observed* to settle" / "observed terminal rules."
The section formerly titled *The Operator Is a Bias, Not an Absorber* is now
**Fixed Points Are Not Attractors**, and it now says explicitly that a fixed
point of a bijection is not an attractor. I have *not* fully formalized the
spatial dynamics — see caveat under (4); it is now flagged as a limitation
rather than assumed.

**2. "Bias toward λ=½" is false for the bare operator. — ACCEPTED; this was the
most important catch.** The paragraph now reads: because `P_σ` complements every
bit, `λ(P_σ g)=1−λ(g)`, so it reflects across ½ and preserves the distance to
½ rather than lowering variance or pulling toward it; any contraction the
spatial field shows must come from the coupling/interrupter, not Eq. (1). The
section title, abstract, limits, and conclusion were all updated to match.
Relatedly, the composite `P_σ∘P_τ` is no longer called "an absorber" — it is
now "itself a bijection, hence not an absorber: it fixes `2^c` bytes but sends a
generic byte around a cycle."

**3. Conflation of "balanced" with "Langton's critical value." — ACCEPTED.**
The headline is now **balance**: *every fixed point has a balanced truth table,
equivalently λ=½*. The λ-section is retitled **Every Fixed Point Is Balanced**.
The Langton intro now states that the λ≈0.5 transition was demonstrated for
larger tables (K=4,N=5), that for ECAs λ is only roughly correlated (citing
Mitchell–Hraber–Crutchfield's caution), and that we keep the exact statement
separate from the edge-of-chaos programme it evokes. The clearest overclaim you
flagged — Limits' "places Langton's critical value at λ=½ as a matter of proof"
— now reads "forces every fixed rule to be **balanced**, λ=½, as a matter of
proof."

**4. Empirical claims can't be evaluated. — ACCEPTED (by demotion, honestly).**
I do not currently have the full methods table (lattice size, boundary, init,
horizon, death definition, mirror action on σ, per-class/per-k counts with
seed uncertainty) reconstructed, so rather than fabricate one I **demoted the
census to an observed trend** and stated exactly what is missing for a
quantitative claim. I also removed the "independent confirmation" sentence: you
are right that recovering 27.34% by classifying permutations is circular, so
the text now says the *dynamical* content is the **death-rate difference**
between classes, not the class sizes, and that Figure 3 shows five examples, not
the 20,256-orbit summary (the caption already said "by example"). A full methods
table remains to-do and is called out as such.

**5. Prospective test contradicts its caption. — ACCEPTED; corrected to your
resolution.** Reduced rotate+2 acts as one even cycle on a subset and leaves
one-cycles, so it is **not** all-even and has **no** fixed point; the test now
predicts it falls on the *no-fixed-points → stays diverse* side, which is what
the figure shows. I also added your nuance: absence of period-one points permits
short-period behaviour, so we claim only persistence of diversity, not literal
aperiodicity.

**6. "Edge of chaos" / "order parameter" not operationally established. —
ACCEPTED.** "Order parameter" is gone as an asserted status: the distinct-rule
count is now "a simple population observable," used "cautiously… on the two
seeds per operator that we ran," and we explicitly *do not* claim it is an order
parameter in the statistical-mechanics sense. "Edge of chaos" is now used
descriptively (or replaced by "high-diversity, visually complex"), and Limits
states that no perturbation-spreading, entropy-rate, or correlation-length
statistics are reported. "Indefinitely" → "through the longest runs we made
(400 generations)"; "every seed" → "all six seeds we tried."

**7. Distance-geometry section untenable. — ACCEPTED; tightened, not removed.**
Fixed `k=0` → `k=1` (2 fixed points; a permutation of eight has ≥1 cycle).
Removed the universal "no distance is a function of cycle type" and "each of
these is a distance"; the section now says only that the *per-trajectory*
measures we tried (C_μ, gzip, diversity — explicitly "not all metrics in the
same sense") do not separate the classes, that we claim **no general
impossibility theorem**, and that this is unsurprising because the classifying
property lives on the operator, not on the states. The category point (cycle
type is a property of σ, the measures are on trajectories) is now the paragraph's
spine.

## Other substantive clarity points

- **Eq. (1) explicit** — done: `(P_σ g)(j) = ¬ g(σ⁻¹(j))`, with the assignment
  form kept as "equivalently," and a sentence noting `P_σ` = coordinate
  permutation ∘ global complement.
- **Abstract "fixed point of σ" → "of P_σ"** — done.
- **"fixed-point-free *and* every cycle even" redundancy** — done; the theorem
  now states "every cycle even" and notes this already forces fixed-point-free.
  The proof now says "one-cycle" where it meant σ's fixed points, to disambiguate
  from `P_σ`'s.
- **EGF normalization** — done: `Σ ((2n−1)!!)² x^{2n}/(2n)! = 1/√(1−x²)`.
- **"all 40,320 of them"** — reworded to "without exception, across all 40,320
  operators, every fixed point that exists…"; the `Σ 2^k = 40,320` line already
  correctly labels that number as fixed-byte incidences.
- **Novelty softened** — "what we *introduce*" → "what we *study*"; the
  literature claim already reads "does not appear in the literature we have
  surveyed."
- **Voorhees Θ (uncited)** — removed.
- **Figure 1 34% vs conclusion 38%** — reconciled: the conclusion now says
  "about a third of the field," consistent with Fig 1's 34%, and "never finds
  Rule 110 at all" → "stays under 2%."
- **Conclusion "opposed" too broad** — softened to "on this one pair… a
  suggestion worth testing at scale, not a law we have established."
- **Duplicated sentence (273–275)** — removed.
- **Empty "§" cross-references** — fixed. apa7 `man` mode leaves sections
  unnumbered, so `\ref{sec:…}` was empty; I added `nameref` and a `\secref`
  macro that prints the section *name*, and reworded the few inline uses so they
  read naturally.

## Two points where I differ or couldn't reproduce

- **Overfull survey floats (142 pt / 70 pt).** I can't reproduce this: both
  `draft.log` and `draft2.log` report **0 overfull boxes**. Likely a stale or
  different compile on your side. The two survey pages are full-page `[p]`
  floats at `0.92\textheight` + caption and currently fit; happy to shrink them
  further if you still see overflow in your build.
- **Recommended shape (split the empirical half into a later paper).** I took
  the lighter path you also offered — "separate those layers more sharply" —
  within the existing structure, because Joe wanted a revision of *this* draft.
  The exact core (define `P_σ` → even-cycle criterion → counts → balance) is now
  cleanly demarcated from the tentative empirical material, and every dynamical
  claim is hedged to what the runs show. A full split into two papers remains a
  clean option and is Joe's call.

## Net

Your read was right: the strongest accurate statement is not that Langton's
critical value was derived, but that **complement-equivariant fixed points are
necessarily balanced (λ=½), by parity, machine-checked** — and that this
algebraic classification *may* predict aspects of the coupled rule-field
dynamics, which we now present as observation rather than theorem. draft2
reflects that throughout.

Remaining known gap: the census methods table (item 4) is demoted, not yet
supplied. That's the next real piece of work before this half of the paper
could carry a quantitative claim.
