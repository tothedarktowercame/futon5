#!/usr/bin/env python3
"""o2_curvature_unified.py — O2 convergence for M-differentiable-code's loss: bring E1
Ollivier-Ricci CURVATURE into the edit-proposal loss (one-metric-both-cuts).

The ratified design DECOUPLED E1 curvature's ground metric (d_E1 = hop-distance) from E2's
embedding. This tests whether RE-COUPLING (d = embedding distance) gives a better curvature
signal for M-differentiable-code's edit-proposals — concrete evidence for codex-3's
metric-contract call (it owns d_E1). Computes kappa per existing ns->ns dependency edge under
BOTH ground metrics and compares. Reuses claude-2's W1-via-LP (futon6 ricci_bottleneck_pilot).
claude-6 (E2). scipy, read-only.  futon5/.venv-tpg/bin/python o2_curvature_unified.py
"""
import json, pathlib, time
import numpy as np
from scipy.optimize import linprog

D = pathlib.Path("/home/joe/code/futon5/data/code-embeddings")
ALPHA = 0.5  # lazy self-mass


def w1(kx, wx, ky, wy, dist):
    m, n = len(kx), len(ky)
    c = np.array([dist(a, b) for a in kx for b in ky], dtype=float)
    A = np.zeros((m + n, m * n))
    for i in range(m):
        for j in range(n):
            A[i, i * n + j] = 1.0
            A[m + j, i * n + j] = 1.0
    b = np.concatenate([wx, wy])
    r = linprog(c, A_eq=A, b_eq=b, bounds=(0, None), method="highs")
    return float(r.fun) if r.success else None


def main():
    emb = np.load(D / "code-emb.npy")
    rel = json.loads((D / "relations.json").read_text())
    ids = json.loads((D / "code-emb-ids.json").read_text())
    ns_idx = np.asarray(rel["ns_idx"]); pos = {int(g): i for i, g in enumerate(ns_idx)}
    name = [ids[int(g)]["id"] for g in ns_idx]
    e = emb[ns_idx]; M = len(ns_idx)

    edges = [(pos[s], pos[d]) for s, d in rel["fdep_edges"] if s in pos and d in pos]
    nbr = [set() for _ in range(M)]
    for s, d in edges:
        nbr[s].add(d); nbr[d].add(s)

    cos = e @ e.T
    d_emb = lambda a, b: 0.0 if a == b else max(1e-6, 1.0 - float(cos[a, b]))   # embedding metric
    d_hop = lambda a, b: 0.0 if a == b else (1.0 if b in nbr[a] else 2.0)        # E1 ratified proxy

    def mu(x):
        ns = list(nbr[x])
        if not ns:
            return [x], np.array([1.0])
        return [x] + ns, np.concatenate([[ALPHA], np.full(len(ns), (1 - ALPHA) / len(ns))])

    def kappa(distfn):
        out = {}
        for (x, y) in edges:
            kx, wx = mu(x); ky, wy = mu(y)
            emd = w1(kx, wx, ky, wy, distfn)
            if emd is not None:
                dd = distfn(x, y)
                out[(x, y)] = 1.0 - emd / dd if dd > 0 else 0.0
        return out

    t0 = time.time()
    k_emb = kappa(d_emb)
    k_hop = kappa(d_hop)
    print(f"[curvature] {len(edges)} edges, both ground metrics, {time.time()-t0:.1f}s")

    ke = np.array([k_emb[e_] for e_ in edges]); kh = np.array([k_hop[e_] for e_ in edges])
    cs = np.array([cos[x, y] for x, y in edges])
    corr = float(np.corrcoef(ke, kh)[0, 1])
    print(f"[compare] corr(kappa_emb, kappa_hop) = {corr:+.3f}")
    # does curvature DISAMBIGUATE the band's low-band edges? negative=bridge, positive=cluster
    for tag, k in [("d=EMBEDDING (recoupled)", ke), ("d=HOP (E1 ratified)", kh)]:
        neg = [(k[i], edges[i]) for i in np.argsort(k)[:5]]
        pos = [(k[i], edges[i]) for i in np.argsort(k)[-5:][::-1]]
        print(f"\n[{tag}] most-NEGATIVE kappa (bridges/bottlenecks — 'ameliorate here'):")
        for kv, (x, y) in neg:
            print(f"   kappa={kv:+.3f} cos={cos[x,y]:.3f}  {name[x]} -> {name[y]}")
        print(f"[{tag}] most-POSITIVE kappa (dense-cluster edges):")
        for kv, (x, y) in pos:
            print(f"   kappa={kv:+.3f} cos={cos[x,y]:.3f}  {name[x]} -> {name[y]}")

    # KEY unified finding: curvature should disambiguate the v1 'low-band' edges into
    # bridges (negative kappa, low cos, KEEP) vs near-duplicates (positive kappa, high cos)
    hi_cos = cs > 0.85
    print(f"\n[disambiguation] near-duplicate edges (cos>0.85, n={int(hi_cos.sum())}): "
          f"mean kappa_emb={ke[hi_cos].mean():+.3f} kappa_hop={kh[hi_cos].mean():+.3f} "
          f"(expect POSITIVE = dense cluster)")
    lo_cos = cs < 0.4
    if lo_cos.sum():
        print(f"[disambiguation] distant edges (cos<0.4, n={int(lo_cos.sum())}): "
              f"mean kappa_emb={ke[lo_cos].mean():+.3f} kappa_hop={kh[lo_cos].mean():+.3f} "
              f"(expect NEGATIVE = bridge)")

    (D / "o2-curvature.json").write_text(json.dumps({
        "corr_emb_hop": corr, "n_edges": len(edges),
        "mean_kappa_emb": float(ke.mean()), "mean_kappa_hop": float(kh.mean()),
        "near_dup_cos_gt85_mean_kappa_emb": float(ke[hi_cos].mean()) if hi_cos.sum() else None,
        "note": "Curvature for M-differentiable-code's loss under d=embedding (recoupled) vs "
                "d=hop (E1 ratified). codex-3 owns the d_E1 contract call.",
    }, indent=2))
    print(f"\nwrote {D/'o2-curvature.json'}")


if __name__ == "__main__":
    main()
