#!/usr/bin/env python3
"""unified_edit_proposals.py — M-differentiable-code's CAPSTONE output: edit-proposals
from the full unified loss (continuity-band ⊗ structural-support ⊗ hop-curvature).

One-metric-both-cuts, consumed AS DELIVERED (O2): the embedding (E2 continuity band) + the
feeds-A graph (structure) + Ollivier-Ricci curvature with d=hop (E1 cut — d=embedding is
degenerate, vindicated). Curvature is the ingredient that makes the proposals ACTIONABLE:

  REVIEW (existing low-band deps), split by curvature:
    - kappa>0 (dense cluster, high cos) => COHESIVE-SPLIT / near-duplicate (merge-candidate)
    - kappa<0 (bridge)                  => LOAD-BEARING connector (KEEP, do not drop)
  ADD (gap#2 structural-hole non-edges: high band + shared dep-neighbours), annotated with
    local curvature: negative-curvature region => closing the triad RELIEVES a bottleneck
    ("ameliorate here") = priority.

Examine-not-apply (gap#2 / consent discipline). claude-6 (E2). scipy+numpy, read-only.
  futon5/.venv-tpg/bin/python unified_edit_proposals.py
"""
import json, pathlib, time
import numpy as np
from scipy.optimize import linprog

D = pathlib.Path("/home/joe/code/futon5/data/code-embeddings")
C, W, ALPHA, TOPK = 0.60, 0.12, 0.5, 10


def band(x): return float(np.exp(-((x - C) / W) ** 2))


def w1(kx, wx, ky, wy, dist):
    m, n = len(kx), len(ky)
    c = np.array([dist(a, b) for a in kx for b in ky], float)
    A = np.zeros((m + n, m * n))
    for i in range(m):
        for j in range(n):
            A[i, i * n + j] = 1.0; A[m + j, i * n + j] = 1.0
    r = linprog(c, A_eq=A, b_eq=np.concatenate([wx, wy]), bounds=(0, None), method="highs")
    return float(r.fun) if r.success else None


def main():
    emb = np.load(D / "code-emb.npy")
    rel = json.loads((D / "relations.json").read_text())
    ids = json.loads((D / "code-emb-ids.json").read_text())
    ns_idx = np.asarray(rel["ns_idx"]); pos = {int(g): i for i, g in enumerate(ns_idx)}
    name = [ids[int(g)]["id"] for g in ns_idx]
    e = emb[ns_idx]; M = len(ns_idx); cos = e @ e.T

    edges = [(pos[s], pos[d]) for s, d in rel["fdep_edges"] if s in pos and d in pos]
    edge_set = set(edges) | {(d, s) for s, d in edges}
    nbr = [set() for _ in range(M)]
    for s, d in edges:
        nbr[s].add(d); nbr[d].add(s)

    d_hop = lambda a, b: 0.0 if a == b else (1.0 if b in nbr[a] else 2.0)

    def mu(x):
        ns = list(nbr[x])
        if not ns:
            return [x], np.array([1.0])
        return [x] + ns, np.concatenate([[ALPHA], np.full(len(ns), (1 - ALPHA) / len(ns))])

    t0 = time.time()
    kap = {}
    for (x, y) in edges:
        kx, wx = mu(x); ky, wy = mu(y)
        emd = w1(kx, wx, ky, wy, d_hop)
        if emd is not None:
            kap[(x, y)] = 1.0 - emd / d_hop(x, y)
    # per-node mean incident curvature (local strain)
    inc = [[] for _ in range(M)]
    for (x, y), k in kap.items():
        inc[x].append(k); inc[y].append(k)
    loc_k = np.array([np.mean(v) if v else 0.0 for v in inc])
    print(f"[unified] {M} ns, {len(edges)} deps, kappa computed {time.time()-t0:.1f}s")

    # ---- REVIEW: lowest-band existing deps, split by curvature ----
    revs = sorted(edges, key=lambda ed: band(cos[ed]))[:20]
    cohesive, bridge = [], []
    for (x, y) in revs:
        k = kap.get((x, y), 0.0)
        (cohesive if k >= 0 else bridge).append((band(cos[x, y]), k, x, y))
    print("\n=== REVIEW · COHESIVE-SPLIT (kappa>0, near-duplicate dep — merge-candidate) ===")
    for b, k, x, y in sorted(cohesive)[:TOPK]:
        print(f"  band={b:.3f} kappa={k:+.2f} cos={cos[x,y]:.3f}  {name[x]} -> {name[y]}")
    print("\n=== REVIEW · BRIDGE (kappa<0, load-bearing connector — KEEP, do not drop) ===")
    for b, k, x, y in sorted(bridge)[:TOPK]:
        print(f"  band={b:.3f} kappa={k:+.2f} cos={cos[x,y]:.3f}  {name[x]} -> {name[y]}")

    # ---- ADD: gap#2 structural holes, prioritized by curvature relief ----
    cand = []
    for i in range(M):
        for j in range(i + 1, M):
            if (i, j) in edge_set:
                continue
            b = band(cos[i, j])
            if b < 0.5:
                continue
            shared = len(nbr[i] & nbr[j])
            if shared >= 1:
                local = min(loc_k[i], loc_k[j])           # most-strained local region
                relief = -local if local < 0 else 0.0     # negative curvature => relief priority
                score = b * shared * (1.0 + relief)
                cand.append((score, b, shared, local, i, j))
    cand.sort(reverse=True)
    print("\n=== ADD · structural holes prioritized by curvature relief (ameliorate-here) ===")
    for score, b, shared, local, i, j in cand[:TOPK]:
        flag = "RELIEVES bottleneck" if local < 0 else "in dense region"
        print(f"  score={score:.2f} band={b:.3f} shared={shared} local-kappa={local:+.2f} "
              f"[{flag}]  {name[i]} <-> {name[j]}")

    out = {
        "loss": "continuity-band (embedding) ⊗ structural-support (shared dep-neighbours) ⊗ "
                "hop-curvature (E1, d=hop — d=embedding degenerate). One-metric-both-cuts, "
                "consumed as delivered.",
        "review_cohesive_split": [{"from": name[x], "to": name[y], "band": b, "kappa": k,
                                   "cos": float(cos[x, y])} for b, k, x, y in sorted(cohesive)[:TOPK]],
        "review_bridge_keep": [{"from": name[x], "to": name[y], "band": b, "kappa": k,
                                "cos": float(cos[x, y])} for b, k, x, y in sorted(bridge)[:TOPK]],
        "add_candidates": [{"a": name[i], "b": name[j], "shared": shared, "band": b,
                            "local_kappa": float(local), "score": float(s),
                            "relieves_bottleneck": bool(local < 0)}
                           for s, b, shared, local, i, j in cand[:TOPK]],
        "discipline": "examine-not-apply; curvature makes proposals actionable (split bridges from "
                      "near-duplicates; flag bottleneck relief). Rung-3 (apply→measure) needs consent.",
    }
    (D / "unified-edit-proposals.json").write_text(json.dumps(out, indent=2))
    print(f"\nwrote {D/'unified-edit-proposals.json'}")


if __name__ == "__main__":
    main()
