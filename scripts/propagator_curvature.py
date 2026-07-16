#!/usr/bin/env python3
"""Ollivier-Ricci curvature of the propagator space (M-propagators, 2026-07-16).

WHY CURVATURE AND NOT CLUSTERS. Three geometries agree the space has NO natural joints
(M-propagators 2b): Euclidean-on-features declines monotonically, Fisher-Rao saturates
flat at ~2x nothing, Wasserstein/mean-field sits at 2.0x a blob and 0.26x real structure.
It is a CONTINUUM. So "move around in that space" (Joe) cannot mean hopping between
kinds -- there are none. It has to mean local geometry, and Ollivier-Ricci is the
standard local-geometry read on a graph:

    kappa(x,y) = 1 - W1(m_x, m_y) / d(x,y)

  kappa < 0  the neighbourhoods spread APART faster than their centres -- the space
             BRANCHES here (a bridge / frontier). In M-aif2 this is the "propose here"
             polarity: negative curvature marks where structure is not yet decided.
  kappa > 0  neighbourhoods overlap -- locally clustered, redundant, "more of the same".
  kappa ~ 0  locally flat.

TWO LEVELS OF W1 -- CONFLATING THEM WOULD BE A REAL ERROR.
  LEVEL 1  W1 between two propagators' RULE distributions, ground metric = Hamming on
           the 8-bit rule byte (the propagator's own single-bit step). This defines
           d(sigma, tau), the distance between propagators. We use the certified
           mean-field lower bound (validated within ~4% of exact LP; see
           propagator_wasserstein.py).
  LEVEL 2  W1 between NEIGHBOURHOOD MEASURES m_x, m_y on the propagator k-NN graph,
           whose ground metric is d from level 1. This is what enters kappa. Exact, by
           scipy linprog over the small local supports (k x k), which is the same
           architecture M-substrate-metric/E1 used for substrate-2.

m_x is the standard lazy random walk: mass `alpha` stays at x, (1-alpha) spreads
uniformly over x's k nearest neighbours.

COST. Pairwise d over 20,256 propagators is 205M pairs -- impossible by LP, instant
under the mean-field bound. kappa is then needed only on the graph's EDGES (O(n*k)),
each a k x k LP. That is the whole reason the architecture is k-NN + local LP.

Reads  data/propagator-metric/bit-marginals.f64  (written by propagator_wasserstein.py)
       data/propagator-metric/sigmas.txt
Writes holes/labs/M-aif-tokamak/propagator-clusters/{curvature.json,curvature.png}
"""
import json
import sys
import numpy as np
from scipy.optimize import linprog
from scipy.sparse import csr_matrix
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

LAB = "holes/labs/M-aif-tokamak/propagator-clusters"
K = 10            # neighbours per node
ALPHA = 0.5       # lazy-walk mass staying put
SAMPLE = int(sys.argv[1]) if len(sys.argv) > 1 else 2000   # nodes to compute kappa on

M = np.fromfile("data/propagator-metric/bit-marginals.f64", dtype="<f8").reshape(-1, 8)
sig = open("data/propagator-metric/sigmas.txt").read().split()
n = len(M)
print(f"{n} propagators, mean-field embedding {M.shape}")

# ---------------- level 1: d(sigma,tau) = mean-field L1 (certified W1 lower bound) ----
def d_rows(idx, allM=M):
    """L1 distance from each row in idx to every propagator. Chunked: never materialise
    the full 20,256^2 matrix (3 GB) -- that trap has been hit twice in this mission."""
    out = np.empty((len(idx), len(allM)))
    for a in range(0, len(idx), 64):
        blk = idx[a:a + 64]
        out[a:a + 64] = np.abs(allM[blk][:, None, :] - allM[None, :, :]).sum(-1)
    return out

# ---------------- k-NN graph ----------------
print(f"building k-NN graph (k={K}) in mean-field space ...")
rng = np.random.default_rng(0)
nodes = np.sort(rng.choice(n, size=min(SAMPLE, n), replace=False))
Dn = d_rows(nodes)                       # |nodes| x n
knn = np.argsort(Dn, axis=1)[:, 1:K + 1]  # drop self
knn_d = np.take_along_axis(Dn, knn, axis=1)
print(f"  {len(nodes)} sampled nodes; mean k-NN radius {knn_d.mean():.4f}, "
      f"mean d overall {Dn.mean():.4f}")

# ---------------- level 2: exact W1 between neighbourhood measures ----------------
def lazy_measure(node_row, nbrs):
    """m_x: ALPHA stays at x, (1-ALPHA) uniform over x's k neighbours."""
    supp = np.concatenate([[node_row], nbrs])
    mass = np.concatenate([[ALPHA], np.full(len(nbrs), (1 - ALPHA) / len(nbrs))])
    return supp, mass

def w1_local(suppA, massA, suppB, massB, ground):
    """Exact W1 by LP over the two small supports, ground metric = d from level 1."""
    ni, nj = len(suppA), len(suppB)
    rows, cols, vals = [], [], []
    for i in range(ni):
        for j in range(nj):
            rows.append(i); cols.append(i * nj + j); vals.append(1.0)
            rows.append(ni + j); cols.append(i * nj + j); vals.append(1.0)
    A = csr_matrix((vals, (rows, cols)), shape=(ni + nj, ni * nj))
    r = linprog(ground.ravel(), A_eq=A, b_eq=np.concatenate([massA, massB]),
                bounds=(0, None), method="highs")
    return float(r.fun)

print("computing Ollivier-Ricci curvature on graph edges ...")
_knn_cache = {}
def knn_of(y):
    """y's k nearest neighbours. Cached: y recurs across many edges, and each miss is a
    full scan of all 20,256 rows -- uncached this dominated the run."""
    r = _knn_cache.get(y)
    if r is None:
        r = np.argsort(np.abs(M - M[y]).sum(1))[1:K + 1]
        _knn_cache[y] = r
    return r

kappas, edges = [], []
for a, x in enumerate(nodes):
    sx, mx = lazy_measure(x, knn[a])
    for b_i, y in enumerate(knn[a]):
        if y < x:                       # each undirected edge once
            continue
        sy, my = lazy_measure(y, knn_of(y))
        dxy = float(np.abs(M[x] - M[y]).sum())
        if dxy <= 1e-12:
            continue
        ground = np.abs(M[sx][:, None, :] - M[sy][None, :, :]).sum(-1)
        k_ = 1.0 - w1_local(sx, mx, sy, my, ground) / dxy
        kappas.append(k_); edges.append((int(x), int(y), dxy))
    if (a + 1) % 200 == 0:
        print(f"  {a+1}/{len(nodes)} nodes, {len(kappas)} edges, "
              f"mean kappa {np.mean(kappas):.4f}"); sys.stdout.flush()

kap = np.array(kappas)
print(f"\n=== OLLIVIER-RICCI CURVATURE ({len(kap)} edges over {len(nodes)} sampled nodes) ===")
print(f"  mean   {kap.mean():+.4f}")
print(f"  median {np.median(kap):+.4f}")
print(f"  sd     {kap.std():.4f}")
print(f"  range  [{kap.min():+.4f}, {kap.max():+.4f}]")
print(f"  negative (branching / frontier): {(kap<0).mean():6.1%}")
print(f"  positive (locally clustered)   : {(kap>0).mean():6.1%}")

# which propagators sit on the most negative edges = the frontier
order = np.argsort(kap)
print("\n  most NEGATIVE edges (the space branches here — 'propose here'):")
for i in order[:6]:
    x, y, dxy = edges[i]
    print(f"    kappa {kap[i]:+.4f}  d={dxy:.3f}  sigma {sig[x]} <-> {sig[y]}")
print("\n  most POSITIVE edges (locally redundant — more of the same):")
for i in order[-4:]:
    x, y, dxy = edges[i]
    print(f"    kappa {kap[i]:+.4f}  d={dxy:.3f}  sigma {sig[x]} <-> {sig[y]}")

# How do the most-negatively-curved sigma PAIRS differ? On the 300-node smoke test, 50%
# of the 20 most-negative edges were a single TRANSPOSITION (exactly 2 positions swapped)
# -- i.e. a MINIMAL change to sigma sits at a branch point. Recorded, not assumed.
def _ndiff(a, b):
    return sum(1 for u, v in zip(a, b) if u != v)
neg_pairs = [(sig[edges[i][0]], sig[edges[i][1]]) for i in order[:20]]
ndiffs = np.array([_ndiff(a, b) for a, b in neg_pairs])
nd_d = np.array([edges[i][2] for i in order[:20]])
# ARTIFACT TEST. kappa = 1 - W1/d, so a tiny d manufactures a huge negative kappa -- and
# transpositions produce near-identical mean fields, i.e. small d. So the pattern could be
# pure 1/d amplification. Re-check it with the near-duplicate regime EXCLUDED: if it only
# holds at tiny d it is an artifact, not a fact about branching.
far = nd_d >= 0.2
frac_all = float((ndiffs == 2).mean())
frac_far = float((ndiffs[far] == 2).mean()) if far.sum() else float("nan")
print(f"\n  sigma positions differing on the 20 most-negative edges: mean {ndiffs.mean():.2f}, "
      f"min {ndiffs.min()}; single transpositions: {frac_all:.0%}")
print(f"  ARTIFACT TEST (exclude near-duplicates, d>=0.2, {int(far.sum())} edges): "
      f"single transpositions {frac_far:.0%}  -> {'SURVIVES' if frac_far >= 0.5 else 'ARTIFACT'}")

json.dump({"k": K, "alpha": ALPHA, "nodes": len(nodes), "edges": len(kap),
           "corr_d_kappa": float(np.corrcoef([e[2] for e in edges], kap)[0, 1]),
           "mean_d_neg": float(np.mean([e[2] for i, e in enumerate(edges) if kap[i] < 0])),
           "mean_d_pos": float(np.mean([e[2] for i, e in enumerate(edges) if kap[i] >= 0])),
           "neg_edge_sigma_ndiff_mean": float(np.mean(ndiffs)),
           "neg_edge_frac_single_transposition": frac_all,
           "neg_edge_frac_single_transposition_d_ge_0.2": frac_far,
           "mean": float(kap.mean()), "median": float(np.median(kap)),
           "sd": float(kap.std()), "min": float(kap.min()), "max": float(kap.max()),
           "frac_negative": float((kap < 0).mean()),
           "most_negative": [{"sigma_x": sig[edges[i][0]], "sigma_y": sig[edges[i][1]],
                              "kappa": float(kap[i]), "d": edges[i][2]} for i in order[:20]]},
          open(f"{LAB}/curvature.json", "w"), indent=1)

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(13, 5))
ax1.hist(kap, bins=60, color="#d62728", alpha=.85)
ax1.axvline(0, color="k", lw=1)
ax1.set_xlabel("Ollivier-Ricci curvature κ"); ax1.set_ylabel("edges")
ax1.set_title(f"κ = 1 − W₁(mₓ,m_y)/d(x,y)   (k={K}, α={ALPHA})\n"
              f"mean {kap.mean():+.3f}, {(kap<0).mean():.0%} negative", fontsize=10)
ax1.grid(alpha=.25)
ds = np.array([e[2] for e in edges])
neg = kap < 0
ax2.scatter(ds[~neg], kap[~neg], s=5, alpha=.22, color="#1f77b4")
ax2.scatter(ds[neg], kap[neg], s=7, alpha=.45, color="#d62728")
ax2.axhline(0, color="k", lw=1)
ax2.set_xlabel("edge length d(x,y)  (mean-field / W₁ lower bound)")
ax2.set_ylabel("κ")
# MEASURED, and it took two wrong captions to get here. Draft 1 asserted "negative kappa
# at long edges" from nothing; draft 2 asserted the opposite from a 300-node smoke test.
# The full run says BOTH patterns are present and they are different statements:
#   - the AVERAGE trend: kappa<0 edges are slightly LONGER (corr is mildly negative);
#   - the EXTREME tail: kappa = 1 - W1/d divides by d, so near-duplicate propagators
#     amplify into huge negative kappa. Those outliers are 1/d noise, not geometry.
# State the correlation; do not gloss it.
r = float(np.corrcoef(ds, kap)[0, 1])
ax2.set_title(f"curvature vs edge length   (corr {r:+.2f})\n"
              f"mean d: κ<0 {ds[neg].mean():.3f} vs κ≥0 {ds[~neg].mean():.3f}   —   "
              f"extreme κ at tiny d is 1/d amplification, not structure", fontsize=9)
ax2.grid(alpha=.25)
fig.suptitle("Ollivier-Ricci curvature of the propagator continuum — "
             "there are no clusters to hop between, so navigate by curvature", fontsize=11)
fig.tight_layout()
fig.savefig(f"{LAB}/curvature.png", dpi=150)
print(f"\nwrote {LAB}/curvature.{{json,png}}")
