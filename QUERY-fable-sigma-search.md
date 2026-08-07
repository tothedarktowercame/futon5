# Query for Fable — σ unpinned: is there an apparatus that could seek the edge of chaos?

*Drafted 2026-08-06 by claude-14. NOT YET SENT — Fable 5 quota exhausted at first attempt.
Send verbatim when quota returns.*

## The system

1-D cellular automaton, three layers, driven by active inference:
- **phenotype**: binary string, updated by an ECA rule.
- **genotype**: per-cell 8-bit ECA rule ("sigil"), 256 possible.
- **exotype**: per-cell, one of 12 named kinds; each kind names a permutation σ of the 8
  three-cell neighbourhood patterns.

Each step each cell picks a policy from {hold, adopt-left, adopt-right, blend} by minimising
expected free energy. Selection is softmax, P(c) ∝ exp(−γ·EFE_c), so γ is a precision/temperature.

The exotype transition — the ordinary non-blend path — is:

    k     ~ Uniform{0..7}          (bit position, drawn per site per step)
    value = NOT bits[k]            (read the rule's bit at k, INVERT it)
    di    = index of σ(pattern k)  (σ maps that neighbourhood pattern to another)
    bits' = bits with position di := value

**Read a bit of the rule at k, flip it, write it at σ(k).** σ is a *write-address map inside the
genotype*; it does not affect how the rule is applied to produce the phenotype. It is a structured
mutation operator whose bias is which bit gets rewritten from which. Measured: this path produces
**78.7%** of novel-sigil events; the "blend" policy produces the other 21.3%.

## The objective

"Edge of chaos" is operationalised as **damage reach**: flip one phenotype cell at t=0, run a
twin, count differing cells at t=100. Anchors at width 250: rule 204 = 1.0, rule 90 = 8.0,
**rule 54 = 36.0 and rule 110 = 38.1 (class-IV target band)**, rule 30 = 60.9.

## Measured and solid

- γ traverses the axis: damage 6.3 (γ=1) → 62.6 (γ=64), monotone; band crossed near γ≈15.
- λ (a per-cell rate parameter) is **inert**: 0–4% leverage on every decision statistic, four
  independent confirmations.
- κ (epistemic coefficient — the weight on EFE's information-seeking term) and γ have real
  leverage (κ: 32% on blend-share, 49% on adoption).
- Damage reach **saturates at small widths**: at width 80, γ=12 through γ=64 all sit within 4
  damage units. Width 250+ is required for range.

## Two failed controllers (both nulls, both reproduced)

1. **λ → hunger setpoint.** Failed on leverage and on an unreachable target.
2. **γ → composite sensor setpoint.** Sensor = fitted linear combination of halting-share and
   phenotype change-rate, R²=0.29, integral control. No convergence from three starts; final means
   span 24 of a 64-wide range; seeds dispersed to both boundaries. Sensor SNR ≈ 0.62 (per-γ SD
   19.6 against total span 31.8), worst in the target region.

A general obstruction emerged in the sensor screen: observables **monotone in γ** are
near-deterministic functions of γ (a controller reading them reads its own knob back — circular),
while observables carrying **independent state information** are non-monotone in γ (not
invertible). Nothing screened was both.

## The finding that reframes everything

σ was **pinned** for the project's entire history: 12 kinds instantiated out of **8! = 40,320**
permutations — or 8⁸ = 16,777,216 for arbitrary maps, the shape an earlier implementation had.
Why those twelve **has never been justified**; a known, recorded, untouched hole. (Four hand-picked
years ago; eight added later for an unrelated reason — EFE dynamics were collapsing onto one kind
because a score cache under-enumerated.)

Probe: width 250, damage at t=100, 6 seeds per configuration, 5 uniformly random permutation sets
versus the shipped twelve.

| | γ = 8 | γ = 16 |
|---|---|---|
| SHIPPED (hand-picked 12) | 30.50 | **39.00** |
| 5 random σ sets | 7.50, 21.33, 22.50, 12.83, 33.50 | 22.50, 6.83, 15.17, 37.83, 7.33 |
| random mean ± SD | 19.53 ± 8.91 | 17.93 ± 11.49 |

**σ spread at γ=16 is 31.0 damage units against 56.3 for the whole γ axis — σ carries ~55% of γ's
leverage.** The shipped set ranks **1 of 6** at γ=16, +1.83 SD above the random mean. Random σ
mostly leave the system **ordered**; the hand-picked set is what puts it near the band at all.

Caveat: 5 draws, 6 seeds, one width, two γ values. Effect size >> seed noise, but the *shape* of
σ→damage is unmeasured.

## The proposal on the table

Put σ into the **policy space** so the agent infers it rather than the experimenter fixing it.
Local moves keep enumeration tractable:
- permutation family: a **transposition** (swap two entries of σ) — C(8,2) = 28 candidates; the
  transposition graph on S₈ is connected, so all 40,320 are reachable by local moves.
- map family: **σ(i) := j** — 64 candidates.

Claimed "AIF-valid": same EFE functional, wider action set, and the **epistemic term already is
the exploration mechanism**, so κ becomes the knob governing σ-exploration. The 28 transpositions
also stratify by Hamming distance on the 3-cube of neighbourhood patterns (12 adjacent / 12 medium
/ 4 antipodal), giving a gentle-vs-violent move gradation.

## The three questions

**Q1 (most important). Do we in fact possess an apparatus that could answer "can this system SEEK
the edge of chaos?" — or are we about to build a fourth controller on the same broken premises?**
Be adversarial. We have failed twice at closed-loop control and once at even noticing our main
parameter was frozen. Specifically: is "seeking the edge of chaos" even well-posed here, given
that (a) the target is defined by *external reference automata* rather than by anything the agent
can perceive, (b) the agent's EFE has no term referencing damage reach, and (c) damage reach is
measured on a *twin run* the agent has no access to? If the objective is not in the agent's
generative model at all, what would "seeking" mean — and is there a formulation where it becomes
intrinsic rather than imposed?

**Q2. Given σ is unpinned, design the search strategy.** Determine whether the system can find and
hold the class-IV band. Be concrete: what varies, what is held, what is measured, what the null
is, what would falsify the claim. Consider whether the right object is a **reachable set** rather
than a setpoint — a suggestion we find appealing and therefore distrust. Consider whether σ-search
and γ-control should be coupled or strictly staged, given σ's comparable leverage and our total
lack of a map of its landscape.

**Q3 (the one we most fear). What ELSE is pinned that we have not noticed?** For an entire day, an
agent, two Codex agents and the human all tuned parameters against a frozen σ and nobody remarked
on it. That is systematic blindness, not an oversight. From the above, identify other quantities
treated as background constants that are in fact free parameters carrying unexamined leverage —
and say which you would probe first, and how cheaply.

**Constraints.** Prioritise being *right* over being encouraging. If the honest answer to Q1 is
"no, and here is what would have to change first", say that and let Q2 fall away. Do not pad. Mark
speculation as speculation.
