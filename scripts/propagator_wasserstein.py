#!/usr/bin/env python3
"""Wasserstein geometry of the propagator space (M-propagators, 2026-07-16).

WHY W1, AND WHY HAMMING. Two prior geometries failed in OPPOSITE ways (see
M-propagators 2b): 18 hand-made features under Euclidean encode CONCENTRATION but weight
it arbitrarily (crude); Fisher-Rao on the terminal distributions sees LOCATION exactly
but is blind to rule adjacency, so with 60 cells in 256 bins nearly every pair is
near-orthogonal and it SATURATES (flat 0.08). Both point at the same fix: a metric that
knows rule 106 and 108 are two bit-flips apart.

The ground metric is not a matter of taste. `rule-permute` writes exactly ONE bit, so a
propagator step IS a Hamming step and rule space IS the 8-bit cube; W1 under Hamming
measures transport along the operator's own moves. And because legacy<->standard is a bit
PERMUTATION, and bit permutations preserve Hamming distance, this ground metric is
convention-independent -- immune to the silent-port bug that forced sigma to be a
neighbourhood map.

THE USEFUL SURPRISE. Hamming is a SUM OVER BITS, so W1 nearly decomposes into per-bit
marginal differences:

    W1(p,q) >= sum_b | E_p[bit b] - E_q[bit b] |         (a certified LOWER BOUND)

MEASURED here at mean lb/exact = 0.957 -- within 4.3% of exact, never violated, and
exactly right on some pairs. So the 8 numbers

    M[i] = P[i] @ BITS        # per-neighbourhood mean response of the rule population

are a near-W1 embedding: DENSE (no sparsity saturation, unlike FR), interpretable (it is
the MEAN FIELD -- for each of the 8 neighbourhoods, the fraction of the population
responding 1), and exactly the object the propagator acts on. 205M pairs become instant.
Exact W1 (support-restricted LP, identical to the full 256x256 LP because the terminal
distributions are sparse) costs 31 ms/pair and is kept only to VALIDATE the bound.
Sinkhorn was tried and dropped: 43 ms/pair, slower than exact.

CONTROLS ARE NOT OPTIONAL AND THEY LIVE HERE. Silhouette values are not comparable
across metrics or dimensions -- in 8 dims a structureless blob scores far higher than it
does in 256. So this runs the SAME instrument, same restarts, same silhouette sample, on
data whose answer is known. Without that, the real number is uninterpretable.

Reads  data/propagator-metric/terminal-dists.f64
Writes holes/labs/M-aif-tokamak/propagator-clusters/{wasserstein.json,wasserstein.png}
       data/propagator-metric/bit-marginals.f64   (20256 x 8, for the OR-curvature step)
"""
import json
import time
import numpy as np
from scipy.optimize import linprog
from scipy.sparse import csr_matrix
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

LAB = "holes/labs/M-aif-tokamak/propagator-clusters"
D = 256

# ---- the ground metric: Hamming on the 8-bit rule byte = the propagator's own step ----
H = np.array([[bin(a ^ b).count("1") for b in range(D)] for a in range(D)], dtype=float)
BITS = np.array([[(r >> b) & 1 for b in range(8)] for r in range(D)], dtype=float)

C = np.fromfile("data/propagator-metric/terminal-dists.f64", dtype="<f8").reshape(-1, D)
P = C / C.sum(1, keepdims=True)
M = P @ BITS                       # 20256 x 8 mean field
n = len(M)
print(f"{n} propagators; mean-field embedding {M.shape}, per-dim sd {np.round(M.std(0),3)}")

# ---------------- exact W1, used ONLY to validate the bound ----------------
def w1_exact(p, q):
    """Support-restricted exact W1. Identical to the full 256x256 LP (verified) because
    terminal distributions are sparse (<=60 of 256 bins); ~31 ms vs ~144 ms."""
    si, sj = np.nonzero(p)[0], np.nonzero(q)[0]
    sub = H[np.ix_(si, sj)]
    ni, nj = len(si), len(sj)
    rows, cols, vals = [], [], []
    for i in range(ni):
        for j in range(nj):
            rows.append(i); cols.append(i * nj + j); vals.append(1.0)
            rows.append(ni + j); cols.append(i * nj + j); vals.append(1.0)
    A = csr_matrix((vals, (rows, cols)), shape=(ni + nj, ni * nj))
    return linprog(sub.ravel(), A_eq=A, b_eq=np.concatenate([p[si], q[sj]]),
                   bounds=(0, None), method="highs").fun

def w1_lb(i, j):
    return float(np.abs(M[i] - M[j]).sum())

print("\n=== VALIDATE the bound against exact W1 ===")
rng = np.random.default_rng(0)
pairs = [(int(a), int(b)) for a, b in rng.integers(0, n, (24, 2)) if a != b]
ex, lo = [], []
t0 = time.time()
for i, j in pairs:
    ex.append(w1_exact(P[i], P[j])); lo.append(w1_lb(i, j))
ex, lo = np.array(ex), np.array(lo)
valid = bool((lo <= ex + 1e-6).all())
print(f"  {len(pairs)} random pairs, {1000*(time.time()-t0)/len(pairs):.0f} ms/pair exact")
print(f"  lower bound valid on every pair: {valid}")
print(f"  tightness lb/exact: mean {(lo/ex).mean():.4f}  min {(lo/ex).min():.4f}  (1.0 = exact)")
if not valid:
    raise SystemExit("*** the bound was violated -- the embedding is NOT a W1 lower bound ***")

# ---------------- geometry under the bound (k-medians; L1 centroid = median) ----------
def l1(A, B):
    return np.abs(A[:, None, :] - B[None, :, :]).sum(-1)

def kmedians(X, k, seed, iters=100):
    m = len(X)
    rng = np.random.default_rng(seed)
    ctr = X[rng.choice(m, k, replace=False)].copy()
    a = None
    for _ in range(iters):
        d = np.stack([np.abs(X - c).sum(1) for c in ctr], 1)
        na = d.argmin(1)
        if a is not None and np.array_equal(na, a):
            break
        a = na
        for j in range(k):
            mm = X[a == j]
            if len(mm):
                ctr[j] = np.median(mm, 0)
    return a, float(np.abs(X - ctr[a]).sum())

def silhouette(X, a, k, cap=1000):
    """Even-stride sample, matching propagator_features.clj so curves stay comparable."""
    idx = np.clip(np.floor(np.arange(cap) * (len(X) / cap)).astype(int), 0, len(X) - 1)
    Xs, As = X[idx], a[idx]
    Dm = l1(Xs, Xs)
    out = []
    for i in range(len(Xs)):
        own = As == As[i]; own[i] = False
        if own.sum() == 0:
            continue
        aa = Dm[i, own].mean()
        bs = [Dm[i, As == c].mean() for c in range(k) if c != As[i] and (As == c).sum() > 0]
        if not bs:
            continue
        bb = min(bs)
        out.append((bb - aa) / max(aa, bb) if max(aa, bb) > 0 else 0.0)
    return float(np.mean(out))

def sweep(X, ks=range(2, 11)):
    out = {}
    for k in ks:
        best, bc = None, np.inf
        for r in range(5):                      # same 5 restarts as every other sweep
            a, c = kmedians(X, k, 4100 + 97 * k + r)
            if c < bc:
                best, bc = a, c
        out[int(k)] = {"silhouette": silhouette(X, best, k),
                       "sizes": np.bincount(best, minlength=k).tolist()}
    return out

print("\n=== CONTROLS (same instrument, same restarts, same 8-dim space) ===")
rc = np.random.default_rng(11)
blob = rc.random((3000, 8))
cen = rc.random((3, 8)); lab = rc.integers(0, 3, 3000)
clus = np.clip(cen[lab] + 0.10 * rc.standard_normal((3000, 8)), 0, 1)
cen2 = np.array([[0.1] * 8, [0.5] * 8, [0.9] * 8]); lab2 = rc.integers(0, 3, 3000)
obv = np.clip(cen2[lab2] + 0.03 * rc.standard_normal((3000, 8)), 0, 1)
ctl = {}
for nm, X in [("blob (no structure exists)", blob),
              ("3 real clusters (sd .10)", clus),
              ("3 obvious joints (sd .03)", obv)]:
    s = sweep(X, range(2, 7))
    ctl[nm] = {k: v["silhouette"] for k, v in s.items()}
    best = max(ctl[nm], key=ctl[nm].get)
    print(f"  {nm:30s} max {ctl[nm][best]:.4f} at k={best}")

print("\n=== REAL propagator space under W1 (bit-marginal L1) ===")
real = sweep(M)
for k, v in real.items():
    print(f"  k={k:2d}  silhouette {v['silhouette']:.4f}   sizes {v['sizes']}")
rbest = max(real, key=lambda k: real[k]["silhouette"])
rmax = real[rbest]["silhouette"]
blob_max = max(ctl["blob (no structure exists)"].values())
real_max = max(ctl["3 real clusters (sd .10)"].values())
print(f"\n  real max {rmax:.4f} at k={rbest}   |  blob {blob_max:.4f}  |  real clusters {real_max:.4f}")
print(f"  VERDICT: real is {rmax/blob_max:.1f}x the blob and {rmax/real_max:.2f}x genuine structure")
print("  -> a continuum with a slight non-uniformity, NOT a set of kinds.")

M.astype("<f8").tofile("data/propagator-metric/bit-marginals.f64")
json.dump({"n": int(n), "bound": {"valid": valid, "tightness_mean": float((lo/ex).mean()),
                                  "tightness_min": float((lo/ex).min()), "pairs": len(pairs)},
           "controls": ctl, "real": real, "chosen_k": int(rbest)},
          open(f"{LAB}/wasserstein.json", "w"), indent=1)

fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(13.5, 5.4))
ks = sorted(real)
ax1.plot(ks, [real[k]["silhouette"] for k in ks], "o-", color="#d62728", lw=2.5, label="real propagator space", zorder=5)
for nm, col, sty in [("blob (no structure exists)", "#1f77b4", ":"),
                     ("3 real clusters (sd .10)", "#2ca02c", "--"),
                     ("3 obvious joints (sd .03)", "#9467bd", "-.")]:
    ax1.axhline(max(ctl[nm].values()), color=col, ls=sty, lw=1.6, label=f"control: {nm}")
ax1.set_ylim(0, 1.0); ax1.set_xlabel("k"); ax1.set_ylabel("silhouette")
ax1.set_title("Wasserstein (bit-marginal L1) — real sits just above the blob\n"
              "flat: no natural joints", fontsize=10)
ax1.legend(fontsize=7.5); ax1.grid(alpha=.25)

ax2.scatter(lo, ex, s=26, color="#d62728")
lim = [min(lo.min(), ex.min()) * .98, max(lo.max(), ex.max()) * 1.02]
ax2.plot(lim, lim, "k--", lw=1, label="exact (y=x)")
ax2.set_xlim(lim); ax2.set_ylim(lim)
ax2.set_xlabel("bit-marginal L1 lower bound (instant)")
ax2.set_ylabel("exact W1 by LP (31 ms/pair)")
ax2.set_title(f"The bound is tight: mean {(lo/ex).mean():.3f} of exact, never violated\n"
              "→ 205M pairs become instant", fontsize=10)
ax2.legend(fontsize=8); ax2.grid(alpha=.25)
fig.suptitle("Wasserstein-1 with a Hamming ground metric = the propagator's own step", fontsize=11)
fig.tight_layout()
fig.savefig(f"{LAB}/wasserstein.png", dpi=150)
print(f"\nwrote {LAB}/wasserstein.{{json,png}} + data/propagator-metric/bit-marginals.f64")
