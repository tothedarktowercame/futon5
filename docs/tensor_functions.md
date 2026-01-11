# Tensor-Function Embedding Space (MMCA)

This note frames MMCA operators and sigils as **functions on tensors**.
Each sigil is an 8-bit rule table; a genotype string is a width×8 tensor
(bitplanes). The phenotype is an auxiliary tensor (bits) and can be
extended with provenance channels later.

## Core idea

We embed sigils and operators into a single space:

```
F : (G, P, K, O, Θ) → (G', P')
```

Where:
- `G` is the genotype tensor (width×8).
- `P` is the phenotype tensor (width×1, or richer later).
- `K` is the kernel (rule-of-rules).
- `O` is the operator set (meta-pattern hooks).
- `Θ` are static meta-parameters (gate strength, schedules, etc.).

## Operator stories in this space

**BlendHand**  
Blends two rule functions into a new effective rule. In tensor terms:
`K' = α*K_a + (1-α)*K_b` (soft) or `K' = choose(K_a, K_b)` (hard).
This is explicitly a *function on functions*.

**Baldwin / Genotype Gate**  
Uses phenotype as an auxiliary register to filter genotype updates:
`G' = gate(P) ⊙ G + (1-gate(P)) ⊙ G_new`.  
This is a tensor masking operator that couples `P → G`.

**EntropyPulse**  
A schedule operator that temporarily switches kernel families. In tensor
terms: `K(t) = pulse(K_base, K_alt, t)`. It is a time-indexed function on `K`.

**UpliftOperator**  
Promotes repeating tensor motifs into higher-level transforms. This can be
modeled as `O := lift(motif(G,P))`, i.e., new operator kernels induced
from tensor patterns.

## Lifting 8-bit vectors into this space

Two principled lift families:

1) **Bitplane-wise meta-rule**
   - Treat each sigil as an 8-bit vector.
   - For each bitplane `i`, apply a local meta-rule to `(left_i, self_i, right_i)`.
   - Collect 8 outputs → new rule table → new sigil.

2) **Function composition**
   - Interpret each 8-bit table as `f : {0,1}^3 → {0,1}`.
   - Define `f_meta` by composing or mixing `f_left, f_self, f_right` per input.
   - The resulting table is a new sigil in the same lexicon.

Both lifts are *functions on tensor fields*, not just scalar rules.

## Synthetic functions in this space

We can induce synthetic operators by:
- sampling parameterized tensor transforms (gate strength, pulse schedule),
- composing them (e.g., `Baldwin ∘ BlendHand ∘ Pulse`),
- and naming stable behaviors as new meta-sigils.

## Next steps

1) Choose a lift family (bitplane vs function composition) to generate
   candidate meta-sigils from existing rule neighborhoods.
   The bitplane lift is implemented in `futon5.mmca.meta-lift`.
2) Define a minimal parameter set `Θ` for synthetic operators (gate strength,
   pulse period, kernel family).
3) Add a registry entry that maps induced functions to sigils (observational
   first, generative later).
