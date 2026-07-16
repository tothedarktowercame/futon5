#!/usr/bin/env python3
"""EXACT fixed-point structure of every propagator, by JAX. (M-propagators, 2026-07-16)

WHY THIS IS THE RIGHT JAX TARGET (and the census was not).

The mission's one-line claim is: "a 2014 Emacs off-by-one turned mutation from a random
walk into a constraint propagator over the rule's bit-planes, WHOSE FIXED POINT SELECTS
THE RULE the landscape lands on." Everything we know about that fixed point is EMPIRICAL
-- sampled from 3 seeds per sigma in a 10.4-hour elisp census.

But on an isolated byte the propagator is not a simulation at all. It is an exact Markov
chain:
    pick k uniformly from 8;  bit[sigma(k)] := NOT bit[k]
That is a 256x256 transition matrix, fully determined by sigma. So the fixed-point
structure is COMPUTABLE ANALYTICALLY for all 40,320 sigma -- no seeds, no sampling, no
census. 40,320 chains x 256 states is ~10.3M transitions: trivial batched linear algebra,
which is exactly what JAX is for.

WHAT I REJECTED, AND WHY (so this is not JAX-because-JAX):
  - A wide-grid JAX census, to get dense distributions and re-test "no joints". KILLED by
    a cheaper test: pooling the last 20 generations of the EXISTING census gave 20x denser
    distributions (43.8 -> 118.9 occupied bins, mean pairwise FR 2.600 -> 2.301) and the
    silhouette stayed flat (0.084 -> 0.106 vs a 0.247 real-cluster control). Density was
    not the obstacle, so building a wide census would have been an expensive way to
    confirm what we already know.
  - Speeding up the metric. It collapsed to an 8-dim mean field with abs(a-b).sum().
    Already instant.
  - Differentiating the xeno loop. Its objective is a discrete Clojure ant sim.
  - tools/tensor/jax_step.py: JSON-in/JSON-out, ONE step per subprocess. A subprocess per
    generation is slower than the elisp it would replace. Wrong shape; not reused.

SCOPE, STATED HONESTLY. This is the propagator IN ISOLATION (no blending, no
neighbours) -- the same object M-propagators 1.3 measured by hand. It is NOT the census's
MetaCA, which blends neighbouring rules before propagating. So its attractors need not
equal the census's 12 collapse targets, and any resemblance is a finding, not an
assumption.

PORT TEST (1.3 recorded these by hand; this must reproduce them or it is wrong):
  sigma = rotate-1 + invert -> alternating {01010101, 10101010}
  sigma = rotate-1, no invert -> uniform {00000000, 11111111} = death
  sigma = rotate-2 + invert -> period-4 bytes
"""
import itertools
import sys
import numpy as np
import jax
import jax.numpy as jnp

jax.config.update("jax_platform_name", "cpu")
print(f"jax {jax.__version__} on {jax.devices()}")

BYTES = jnp.arange(256, dtype=jnp.int32)

def transition(sigma):
    """T[b, k] = the byte reached from b when the propagator draws k.

    The operator, verbatim: read bit k of b, write its INVERSE into bit sigma(k).
    Bits are indexed 0..7 as positions in the rule byte (this is the isolated-byte
    reading of 1.3; the neighbourhood-map subtlety only matters when porting a sigma
    ACROSS engines, which we are not doing here -- we sweep all sigma anyway)."""
    b = BYTES[:, None]                                   # (256,1)
    k = jnp.arange(8)[None, :]                           # (1,8)
    s = jnp.asarray(sigma)[None, :]                      # (1,8) sigma(k)
    bit_k = (b >> k) & 1
    v = 1 - bit_k                                        # the INVERSE
    cleared = b & ~(1 << s)
    return cleared | (v << s)                            # (256,8)

@jax.jit
def step_matrix(sigma):
    """Row-stochastic P[b, b'] = P(b -> b'), uniform over the 8 draws of k."""
    T = transition(sigma)                                # (256,8)
    onehot = jax.nn.one_hot(T, 256, dtype=jnp.float32)   # (256,8,256)
    return onehot.mean(axis=1)                           # (256,256)

@jax.jit
def stationary(sigma, iters=400):
    """Power-iterate from uniform to the chain's stationary/limiting distribution.
    Absorbing structure shows up as mass concentrating on the absorbing set."""
    P = step_matrix(sigma)
    v = jnp.full((256,), 1.0 / 256.0)
    def body(_, v):
        return v @ P
    return jax.lax.fori_loop(0, iters, body, v)

def summarise(sigma):
    v = np.asarray(stationary(jnp.asarray(sigma, dtype=jnp.int32)))
    supp = np.nonzero(v > 1e-6)[0]
    return {"support": supp, "n_support": int(len(supp)),
            "top": supp[np.argsort(-v[supp])][:4].tolist(),
            "mass_top2": float(np.sort(v)[-2:].sum())}

def rot(n, invert=True):
    """sigma = rotate by n. NB 1.3's variants also invert; our operator ALWAYS writes the
    inverse, so 'no invert' is a different operator, handled separately below."""
    return [(k + n) % 8 for k in range(8)]

print("\n=== PORT TEST — reproduce M-propagators 1.3's hand-measured results ===")
for name, sigma in [("rotate-1 (+invert)", rot(-1)),
                    ("rotate-2 (+invert)", rot(-2)),
                    ("rotate+2 (+invert)", rot(2)),
                    ("identity", list(range(8)))]:
    s = summarise(sigma)
    print(f"  {name:22s} support {s['n_support']:3d}  top bytes "
          f"{[format(b,'08b') for b in s['top']]}  mass(top2) {s['mass_top2']:.3f}")

print("\n  1.3 recorded, by hand:")
print("    rotate-1 + invert -> alternating {01010101, 10101010}")
print("    rotate-2 + invert -> period-4 bytes")
print("  (rotate-1 WITHOUT invert -> {00000000,11111111} is a DIFFERENT operator: our")
print("   propagator always writes the inverse, so that row is not reproducible here.)")

# ---- the sweep: all 40,320 sigma, exactly, no sampling ----
if "--sweep" in sys.argv:
    perms = np.array(list(itertools.permutations(range(8))), dtype=np.int32)
    print(f"\n=== EXACT SWEEP over all {len(perms):,} sigma ===")
    batch = jax.jit(jax.vmap(stationary))
    supports, tops = [], []
    B = 256   # peak ~0.5GB: each sigma builds a (256,8,256) one-hot; 2016 would want ~4GB
              # and the xeno loop needs the box.
    for i in range(0, len(perms), B):
        V = np.asarray(batch(jnp.asarray(perms[i:i + B])))
        supports.append((V > 1e-6).sum(1))
        tops.append(V.argmax(1))
        print(f"  {i+len(V):,}/{len(perms):,}", flush=True)
    supports = np.concatenate(supports); tops = np.concatenate(tops)
    print(f"\n  support size (how many bytes the fixed point spreads over):")
    for q in [1, 2, 4, 8, 16, 32, 64, 256]:
        print(f"    <= {q:3d} bytes : {(supports<=q).mean():6.1%}")
    from collections import Counter
    c = Counter(tops.tolist())
    print(f"\n  most common attractor byte across all sigma:")
    for b, n in c.most_common(8):
        print(f"    rule {b:3d} = {format(b,'08b')}  {n:6,} sigma ({n/len(perms):.1%})")
    np.savez("data/propagator-metric/fixedpoints.npz", supports=supports, tops=tops)
    print("\n  wrote data/propagator-metric/fixedpoints.npz")
