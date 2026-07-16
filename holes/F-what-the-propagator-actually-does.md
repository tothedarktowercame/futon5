# F-what-the-propagator-actually-does — the phenotype is a rendering, and the bug is not a permutation

**Status:** MEASURED, 2026-07-16. Prompted by Joe: *"we need to understand a bit more what
the propagators actually do... they produce pretty pictures when applied to CAs. But if
they were applied to something else, they might produce very different results."*

**Bottom line:** the "pretty pictures" are not the propagator's doing and not the CA's
doing. The Figure-8 genotype dynamics is **autonomous** — it never reads the phenotype —
and it reproduces the 42/170 attractor **15/15 with no CA at all**. Separately: **the
Emacs bug is not a permutation**, so the 40,320-σ census does not contain the object it
was built to generalise.

---

## 1. The Figure-8 dynamics never reads the phenotype (source fact)

`vendor/metaca/256ca-2014-12-29-BUGGY.el`:

```elisp
551: (defun evolve-sigil-with-blending-mutation (sig &optional pred next ignore)   ; <- ignore
590: (defun evolve-sigil-with-blending-baldwin  (sig &optional pred next context)  ; <- context
```

The function that reproduces Figure 8 takes a 4th argument literally named **`ignore`**,
and never references it in its body. Only the Baldwin variant takes `context` (the
phenotype). `pred`/`sig`/`next` are all **genotype** sigils.

So the Figure-8 genotype layer is a closed loop: genotype → genotype. The phenotype is
downstream of it and feeds nothing back.

## 2. What the genotype layer IS: a self-applying CA (source fact)

From el:551-583, per cell, with `p`/`s`/`n` the left/self/right **genotypes**:

```elisp
(local-rule (mapcar* #'list truth-table-3 s-ints))        ; the cell's OWN byte, read as a RULE
(local-data (map 'list (lambda (a b c) ...) p s n))        ; the i-th bits of (left, self, right)
;; then, per bit-plane i:
;;   p[i]=0 and n[i]=0  -> 0          } blend: neighbours agree
;;   p[i]=1 and n[i]=1  -> 1          }
;;   else               -> local-rule[ p[i] s[i] n[i] ]    ; APPLY OWN BYTE AS A RULE
583: (get-genotype-from-rule (mutate-genotype-n output mutation))   ; then the propagator, 2x
```

Read that carefully. **Each of the 8 bit-planes is a line of cells across space, and the
cell's own rule byte is read as an ECA rule and applied to the triple of i-th bits of its
genotype neighbourhood.** The byte is simultaneously the data being evolved and the rule
doing the evolving. That is the "Meta" in MetaCA.

Plus: a **blend override** (where the two spatial neighbours agree, copy that), and then
the **propagator** as mutation.

So the full mechanism is three parts, none of which is the phenotype:

| part | what it does |
|---|---|
| **self-application** | the byte acts on its own bit-planes |
| **blend** | spatial consensus where neighbours agree |
| **propagator** | the mutation on top |

## 3. Measured: the genotype layer alone reproduces Figure 8, 15/15

`scratchpad/genolayer.py` — a **structural** reimplementation of el:551-583 with **no
phenotype, no CA, no rendering**. (Not bit-exact: the real engine has an unusual
head/tail/interior order. The Elisp harness stays ground truth.)

```
seed  0..14 : ['00101010', '10101010']   -> as plain binary [42, 170]
reached exactly {42,170}: 15/15
```

Compare `baldwin-repro/README.md`: the Elisp blending-mutation control reaches exactly
`{42,170}` in **15/15**. Same result, no CA.

**The sigil colour IS the genotype byte read as plain binary**: `#2a2a2a` = 0x2a = 42,
`#aaaaaa` = 0xaa = 170. This also resolves the encoding: the paper's "42/170" are the
genotype string as a plain 8-bit number, *not* legacy→standard rule numbers. (Under the
legacy truth-table-3 conversion the same two strings are rules 76/77. The A1/A4 bit-order
finding again — mind which convention any future claim is in.)

> **The pretty pictures are a rendering of an autonomous genotype dynamics. The propagator
> does not need a CA. It needs self-application and a consensus field.**

## 4. The propagator is a *negating shift register* — Joe's loom, literally

`mutate-genotype-n` (el:527): read bit at 0-based `pos`; write the negation at
`(goto-char pos)`, which is **1-based** and clamps 0 → point-min. So:

```
bit[max(k-1, 0)] := NOT bit[k]
```

Information cascades **7 → 6 → 5 → 4 → 3 → 2 → 1 → 0**, negating at every step. A
Jacquard chain. Two consequences fall straight out and both are measured:

- **bit 7 is never written** — nothing shifts into the top. (Measured: 0.000% of 87,632
  writes.) It is pinned by the blend/self-apply dynamics alone.
- **bit 0 is written twice** — by `k=0` (a self-flip, `bit[0] := ¬bit[0]`) and by `k=1`.
  (Measured: 24.946% ≈ 2/8.)

### This explains Figure 8 exactly

The shift-consistent configuration is the **alternating** pattern `g[k-1] = ¬g[k]`. Bit 7
is pinned by blending; the cascade then fills bits 6..1 with alternation, giving
`g[1..7] = 0101010`. Bit 0 **cannot settle**, because `k=0` demands `g[0] = ¬g[0]`, which
is unsatisfiable. So bit 0 flickers and everything else is frozen — giving **exactly two
states differing in exactly one bit**:

```
00101010  = 42        g[1..7] = 0101010 frozen, g[0] = 0
10101010  = 170       g[1..7] = 0101010 frozen, g[0] = 1
          ^ index 0 — the one unsettleable bit
```

**Figure 8 is a shift register filling up and then hunting on its single unsettleable
bit.** Joe's structural description in the 2015 paper ("two rules differing in one bit")
was right; this is *why*.

## 5. THE BUG IS NOT A PERMUTATION — the census does not contain it

The bug's map is `k ↦ max(k-1, 0)`:

```
0→0  1→0  2→1  3→2  4→3  5→4  6→5  7→6
```

**0 has two preimages; 7 has none.** It is not injective, not surjective, **not a
permutation**.

The paper's family (`futon5/holes/tech-notes/paper/main.tex` §"The bug generalises to a
family") is `σ ∈ S_8`, and the census enumerates 8! = **40,320 permutations**. The Emacs
bug is **not among them**.

And the paper's own measurement proves it: **any permutation gives uniform 12.5% per bit.**
The measured histogram is bit 0 at 24.946% and bit 7 at 0.000% — the signature of
non-injectivity. The census of 40,320 σ never contained the object it was built to
generalise.

**Worse — and this is the load-bearing part — the Figure-8 mechanism *depends* on the
non-injectivity.** Bit 7 dead is what pins the top of the cascade; bit 0 doubled is what
makes it unsettleable. A permutation has neither property. **No σ in the studied family
can produce Figure 8's mechanism.**

This does not make the permutation family uninteresting. It makes it *a different object*,
and the paper currently claims it is a generalisation of the bug. That claim is false as
written.

## 6. Likely error in the paper's "Exact fixed points" section

The paper says (and `README-xeno-loop.md` §2 leans on it):

> **No operator has a single-byte attractor** ... The reason is immediate once stated: a
> fixed byte would require `bit[σ(k)] = ¬bit[k]` for all k simultaneously, which an
> always-invert operator forbids.

**The reason is false.** That condition is satisfiable exactly when **every cycle of σ has
even length** — follow a cycle of length L and you need `(¬)^L = id`. Brute force over all
40,320 σ:

```
sigma admitting a fixed byte:  11,025 / 40,320   (27.3%)
e.g. sigma = (01)(23)(45)(67)  ->  byte 85 = 01010101 is ABSORBING (verified)
```

What `scripts/propagator_fixedpoints_jax.py:77` actually computes:

```python
def stationary(sigma, iters=400):
    """Power-iterate from uniform to the chain's stationary/limiting distribution."""
```

It power-iterates **from uniform**. Its "support" is therefore the support of the limiting
distribution *from a uniform start* — the union of all recurrent classes — not the size of
the smallest attractor. For σ=(01)(23)(45)(67) it reports support 16: `{85}` is a
singleton absorbing class, but there are others, and from uniform the limit spreads over
all of them.

So the defensible claim is:

> the limiting distribution from a uniform start never concentrates on a single byte
> (min support 4)

which is **not** "no σ has a single-byte attractor". 27% of σ have one. The distinction
matters because `README-xeno-loop.md` §2 uses the stronger version to argue that freezing
cannot work.

**Not yet checked:** whether the ants' specific σ is one of the 11,025.

---

## What this means for porting (the actual question)

The ants port asked *"what is the analogue of the rule byte?"* and answered *"the
C-vector"*. But the rule byte is not merely a substrate — it is **a substrate that is also
its own operator, embedded in a spatial consensus field**. The C-vector is only a
substrate. The port kept the wrong half, and then the propagator — a device whose entire
job is to cascade and never settle — did the only thing it can do to a lone goal vector:
scramble it. That is `F-propagator-on-c-vector-NEGATIVE.md`'s measured amnesia, and the
dose–response (less propagation = less damage, never better than baseline) is exactly what
a pure noise source looks like.

The `locus` candidate in that doc (couple **across** ants, not within one ant) restores the
**consensus field**. It does not restore **self-application** — there is no sense in which
an ant's C-vector is read as a policy that acts on C-vectors. So locus is a necessary
repair, not obviously a sufficient one, and the prediction is that it recovers *toward*
baseline the way the gentle sweep did.

**The honest general answer to "what would propagators do elsewhere?":** on their own, a
negating shift register cascades and cannot settle — it is a structured noise source. Every
interesting thing in Figure 8 comes from its interaction with self-application and a
consensus field. A substrate with neither gets the noise and nothing else.

This also re-reads the conjugacy theorem rather than contradicting it: σ's abstract
structure carries no live/dead information because the content is in the *registration* —
which neighbourhood-semantics get coupled to which. Joe's loom is right. The card is not
the cloth; the cloth is the card's registration against the warp. Rethread the loom
(conjugate by τ) and the same card gives different cloth — which is precisely
σ_D = τ σ_L τ⁻¹ with opposite outcomes.

## Cheapest next experiments (not run)

1. **Move the census onto the genotype layer alone.** §3 shows that is the whole system.
   It is ~100× cheaper than the CA census (no phenotype, no rendering), which also
   dissolves the "3 seeds per σ" weakness in the paper.
2. **Study the family that actually contains the bug** — maps `[8]→[8]`, not just
   bijections. The clamp map is one; the question "which maps live?" has never been asked
   of the right set.
3. **Check whether the ants' σ is one of the 11,025** with an absorbing byte.

## Verified vs inferred

| claim | status |
|---|---|
| Figure-8 fn's 4th arg is `ignore`, Baldwin's is `context` | **verified** (read the source) |
| genotype layer is self-applying + blend + propagator | **verified** (read el:551-583) |
| genotype layer alone → {42,170} 15/15 | **measured** (structural reimpl., not bit-exact) |
| bug's map is `k ↦ max(k-1,0)`, non-injective | **verified** (source + matches the 24.9%/0.0% histogram) |
| permutation family excludes the bug | **verified** (a permutation cannot give 2/8 and 0/8) |
| 11,025/40,320 σ admit a fixed byte | **measured** (brute force, all 40,320) |
| JAX power-iterates from uniform | **verified** (read the source) |
| the shift-register explanation of Figure 8 | **inferred** — analytic, consistent with the sim; not independently tested |
