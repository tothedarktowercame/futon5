#!/usr/bin/env python3
"""Re-ask "does the propagator space have natural joints?" under the INFORMATION METRIC.

THE POINT. features.csv holds 18 hand-made scalar summaries (entropy-early/late, top1,
class4, ...) of each propagator's terminal rule distribution, and we clustered them
under EUCLIDEAN distance. Result: silhouette monotone-decreasing 0.441 (k=2) -> 0.267
(k=10), no elbow, k=2 "weak" -- read as "the space has no natural joints". But that
result is CONDITIONAL on my choice of summary statistic and on Euclidean geometry. If
the geometry was wrong, the negative was about me, not about the space.

THE FIX. The census already IS distributions: each propagator's terminal state is a
point p in the 256-simplex (rule counts / 180). Fisher-Rao is THE metric there --
by Cencov's theorem the unique Riemannian metric (up to scale) invariant under
sufficient statistics. No feature engineering, no ground metric, no free parameters:

    d_FR(p, q) = 2 * arccos( sum_i sqrt(p_i * q_i) )

GEOMETRY, NOT A HACK. sqrt(p) has unit L2 norm, so the simplex under Fisher-Rao IS the
positive orthant of the unit sphere, and d_FR is exactly twice the great-circle angle.
So k-means under the information metric is literally SPHERICAL k-means on sqrt(p):
assign by max dot product, centroid = normalised mean (the Karcher mean's standard
approximation for tight clusters). That is what this does.

COMPARABILITY IS THE WHOLE POINT. Same k range (2..10), same 5 restarts, same
even-stride silhouette sample (1000), same seeds as scripts/propagator_features.clj,
so the two silhouette curves are directly comparable. The ONLY thing changed is the
representation + metric.

Reads  data/propagator-metric/{terminal-dists.f64,sigmas.txt}
Writes holes/labs/M-aif-tokamak/propagator-clusters/fisher-rao.{json,png}
"""
import json
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

LAB = "holes/labs/M-aif-tokamak/propagator-clusters"
D = 256

counts = np.fromfile("data/propagator-metric/terminal-dists.f64", dtype="<f8").reshape(-1, D)
sigmas = open("data/propagator-metric/sigmas.txt").read().split()
n = counts.shape[0]
assert counts.shape[0] == len(sigmas), (counts.shape, len(sigmas))
assert np.allclose(counts.sum(1), 180.0), "rows must be pooled counts over 3 seeds x width 60"
print(f"{n} propagators x {D} rules; every row sums to {counts.sum(1)[0]:.0f}")

# simplex -> sphere.  S = sqrt(p), ||S|| = 1, and d_FR = 2*arccos(S_i . S_j)
P = counts / counts.sum(1, keepdims=True)
S = np.sqrt(P)
print(f"sqrt(p) norms: min {np.linalg.norm(S,axis=1).min():.6f} max {np.linalg.norm(S,axis=1).max():.6f}")

def fr_dist(A, B):
    """Fisher-Rao distance between rows of A and rows of B (both sqrt-space)."""
    return 2.0 * np.arccos(np.clip(A @ B.T, -1.0, 1.0))

def spherical_kmeans(S, k, seed, iters=100):
    """k-means under Fisher-Rao = spherical k-means on sqrt(p).
    kmeans++ init using the FR distance, so init matches the metric too.

    NB `m = len(S)`, NOT the module-level `n`. This function originally closed over the
    global n (=20,256), so it silently only worked on the real data; the controls passed
    solely because _peak used one constant seed whose first draw happened to land inside
    their smaller array. A control that only runs by luck is not a control."""
    m = len(S)
    rng = np.random.default_rng(seed)
    centers = [S[rng.integers(m)]]
    for _ in range(k - 1):
        d = np.min(np.stack([fr_dist(S, c[None, :])[:, 0] for c in centers]), axis=0)
        w = d ** 2
        tot = w.sum()
        centers.append(S[rng.integers(m)] if tot <= 0 else S[np.searchsorted(np.cumsum(w), rng.random() * tot)])
    C = np.stack(centers)
    assign = None
    for _ in range(iters):
        # max dot product == min great-circle angle == min Fisher-Rao
        new_assign = np.argmax(S @ C.T, axis=1)
        if assign is not None and np.array_equal(new_assign, assign):
            break
        assign = new_assign
        for j in range(k):
            m = S[assign == j]
            if len(m):
                v = m.mean(0)                       # Karcher mean, standard approx...
                nv = np.linalg.norm(v)
                C[j] = v / nv if nv > 0 else C[j]   # ...projected back onto the sphere
    inertia = float((2.0 * np.arccos(np.clip(np.sum(S * C[assign], axis=1), -1, 1)) ** 2).sum())
    return assign, inertia

def silhouette_sampled(S, assign, k, cap=1000):
    """Same even-stride sample as propagator_features.clj, so curves are comparable."""
    idx = np.floor(np.arange(cap) * (len(S) / cap)).astype(int)
    idx = np.clip(idx, 0, len(S) - 1)
    Ss, As = S[idx], assign[idx]
    Dm = fr_dist(Ss, Ss)
    out = []
    for i in range(len(Ss)):
        own = As == As[i]
        own[i] = False
        if own.sum() == 0:
            continue
        a = Dm[i, own].mean()
        bs = [Dm[i, As == c].mean() for c in range(k) if c != As[i] and (As == c).sum() > 0]
        if not bs:
            continue
        b = min(bs)
        out.append((b - a) / max(a, b) if max(a, b) > 0 else 0.0)
    return float(np.mean(out)) if out else 0.0

print("\n=== k-selection under FISHER-RAO (spherical k-means on sqrt(p)) ===")
curve = []
for k in range(2, 11):
    best, best_in = None, np.inf
    for r in range(5):
        a, inertia = spherical_kmeans(S, k, 4100 + 97 * k + r)
        if inertia < best_in:
            best, best_in = a, inertia
    sil = silhouette_sampled(S, best, k)
    sizes = np.bincount(best, minlength=k).tolist()
    curve.append({"k": k, "silhouette": sil, "inertia": best_in, "sizes": sizes})
    print(f"  k={k:2d}  silhouette {sil:.4f}   sizes {sizes}")

chosen = max(curve, key=lambda c: c["silhouette"])
strength = "strong" if chosen["silhouette"] >= 0.5 else ("weak" if chosen["silhouette"] >= 0.25 else "unseparated")
print(f"\n  chosen k={chosen['k']}  silhouette {chosen['silhouette']:.4f}  -> {strength}")

EUCLID = {2: 0.4409, 3: 0.4134, 4: 0.3671, 5: 0.3275, 6: 0.3148,
          7: 0.2890, 8: 0.2978, 9: 0.2830, 10: 0.2672}

# ---------------------------------------------------------------- CONTROLS
# CALIBRATION IS NOT OPTIONAL, AND IT LIVES IN THE SCRIPT.
# Fisher-Rao silhouettes are NOT on the same scale as Euclidean ones -- plotting both
# against the Euclidean strong/weak thresholds implies "FR found even less structure",
# which is FALSE and was the first version of this figure. FR needs its OWN scale, and
# the only honest way to get one is to run the instrument on data whose answer we know.
def _synth_blob(nn, seed):
    rng = np.random.default_rng(seed)
    Q = rng.random((nn, D)); return np.sqrt(Q / Q.sum(1, keepdims=True))

def _synth_clusters(nn, kk, seed, disjoint):
    rng = np.random.default_rng(seed)
    lab = rng.integers(0, kk, nn)
    if disjoint:                       # near-disjoint supports = unmistakable joints
        Q = np.full((nn, D), 1e-4)
        for j in range(kk):
            blk = slice(j * (D // kk), (j + 1) * (D // kk))
            Q[lab == j, blk] += rng.random((int((lab == j).sum()), D // kk))
    else:                              # overlapping but genuinely 3 groups
        base = rng.random((kk, D)) ** 6; base /= base.sum(1, keepdims=True)
        Q = base[lab] + 0.02 * rng.random((nn, D))
    return np.sqrt(Q / Q.sum(1, keepdims=True))

def _peak(Sx, ks=(2, 3, 4, 5)):
    """Controls MUST run under the same conditions as the thing they calibrate.
    Originally this used ONE restart while the real curve used 5-restarts-best-inertia;
    the ceiling control then hit a bad kmeans++ init at k=3 and reported 0.47 instead of
    0.70 -- handicapping the control and flattering the real data by comparison. Same 5
    restarts, same best-inertia selection, same silhouette sample."""
    out = {}
    for kk in ks:
        best, best_in = None, np.inf
        for r in range(5):
            a, inertia = spherical_kmeans(Sx, kk, 4100 + 97 * kk + r)
            if inertia < best_in:
                best, best_in = a, inertia
        out[kk] = silhouette_sampled(Sx, best, kk)
    return out

print("\n=== CONTROLS: what does this instrument score on KNOWN answers? ===")
ctl = {"blob (no structure exists)": _peak(_synth_blob(3000, 3)),
       "3 real clusters (overlapping)": _peak(_synth_clusters(3000, 3, 1, False)),
       "3 obvious joints (disjoint supports)": _peak(_synth_clusters(3000, 3, 7, True))}
for nm, d in ctl.items():
    print(f"  {nm:38s} max {max(d.values()):.4f} at k={max(d, key=d.get)}")
real_max = max(c["silhouette"] for c in curve)
print(f"  {'REAL propagator space':38s} max {real_max:.4f} at k={chosen['k']}")

print("\n=== EUCLIDEAN-on-18-features vs FISHER-RAO-on-distributions ===")
print("  (values are NOT comparable across metrics -- compare SHAPE: peak = joints,")
print("   flat/monotone = none. Absolute FR levels are read against the controls above.)")
print(f"  {'k':>3} {'euclid':>9} {'fisher-rao':>11}")
for c in curve:
    print(f"  {c['k']:>3} {EUCLID[c['k']]:>9.4f} {c['silhouette']:>11.4f}")

json.dump({"curve": curve, "chosen_k": chosen["k"], "strength": strength,
           "euclidean_reference": EUCLID, "controls": ctl, "n": int(n)},
          open(f"{LAB}/fisher-rao.json", "w"), indent=1)

ks = [c["k"] for c in curve]
fig, (axL, axR) = plt.subplots(1, 2, figsize=(13.5, 5.6))

axL.plot(ks, [EUCLID[k] for k in ks], "o-", color="#7f7f7f", lw=2)
for y, lab_ in [(0.5, "strong"), (0.25, "weak")]:
    axL.axhline(y, ls=":", c="k", alpha=.5); axL.text(10, y + .008, lab_, ha="right", fontsize=8)
axL.set_ylim(0, 0.78); axL.set_xlabel("k"); axL.set_ylabel("silhouette")
axL.set_title("Euclidean on 18 hand-made features\n"
              "monotone decline, no elbow → no joints\n(but these features encode CONCENTRATION)", fontsize=10)
axL.grid(alpha=.25)

axR.plot(ks, [c["silhouette"] for c in curve], "o-", color="#d62728", lw=2.5, label="real propagator space", zorder=5)
for nm, col, sty in [("blob (no structure exists)", "#1f77b4", ":"),
                     ("3 real clusters (overlapping)", "#2ca02c", "--"),
                     ("3 obvious joints (disjoint supports)", "#9467bd", "-.")]:
    axR.axhline(max(ctl[nm].values()), color=col, ls=sty, lw=1.6, label=f"control: {nm}")
axR.set_ylim(0, 0.78); axR.set_xlabel("k")
axR.set_title("Fisher–Rao on terminal rule distributions\n"
              "flat ~0.08, barely above the no-structure control\n"
              "→ the METRIC saturates (60 cells into 256 bins)", fontsize=10)
axR.legend(fontsize=7.5, loc="upper right"); axR.grid(alpha=.25)

fig.suptitle("Does the propagator space have natural joints?  Same k range, restarts, silhouette sample — "
             f"only the geometry changes  (all {n:,} orbits)", fontsize=11)
fig.tight_layout()
fig.savefig(f"{LAB}/fisher_rao_vs_euclidean.png", dpi=150)
print(f"\nwrote {LAB}/fisher-rao.json + fisher_rao_vs_euclidean.png")
