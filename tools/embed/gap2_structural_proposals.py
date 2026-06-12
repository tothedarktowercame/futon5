#!/usr/bin/env python3
"""gap2_structural_proposals.py — DERIVE step for gap #2 (what does the loss MEAN?).

The v1 consumer build (build_code_edit_proposals.py) showed raw cosine-band is a WEAK
edit-semantics: ADD-candidates at cos~0.6 included spurious pairs (tensor-transfer <->
social.bells) because semantic proximity alone doesn't justify a dependency. This tests a
stronger loss: an ADD-candidate must have BOTH (a) high band (coherent-not-duplicate) AND
(b) STRUCTURAL support = shared dependency-neighbors in the feeds-A graph (an open triad to
close — classic link-prediction / structural-hole signal). claude-6 (E2). Read-only; numpy.

Compares v1 (pure-band) vs v2 (band x shared-neighbors). The COMPARISON is the gap#2 finding.
"""
import json, pathlib
import numpy as np

D = pathlib.Path("/home/joe/code/futon5/data/code-embeddings")
C, W, TOPK = 0.60, 0.12, 12


def band(cos):
    return np.exp(-((cos - C) / W) ** 2)


def main():
    emb = np.load(D / "code-emb.npy")
    rel = json.loads((D / "relations.json").read_text())
    ids = json.loads((D / "code-emb-ids.json").read_text())
    ns_idx = np.asarray(rel["ns_idx"])
    pos = {int(g): i for i, g in enumerate(ns_idx)}
    name = [ids[int(g)]["id"] for g in ns_idx]
    ns_emb = emb[ns_idx]; M = len(ns_idx)

    edges = [(pos[s], pos[d]) for s, d in rel["fdep_edges"] if s in pos and d in pos]
    edge_set = set(edges) | {(d, s) for s, d in edges}
    # undirected ns dependency neighbours
    nbr = [set() for _ in range(M)]
    for s, d in edges:
        nbr[s].add(d); nbr[d].add(s)

    cosM = ns_emb @ ns_emb.T
    bandM = band(cosM)

    # v1: pure-band non-edge ADD ranking
    v1 = []
    for i in range(M):
        for j in range(i + 1, M):
            if (i, j) not in edge_set:
                v1.append((bandM[i, j], i, j))
    v1.sort(reverse=True)
    v1_top = v1[:TOPK]

    # v2: band x structural support (shared dependency-neighbours); require shared>=1
    v2 = []
    for b, i, j in v1:
        if b < 0.5:
            break
        shared = len(nbr[i] & nbr[j])
        if shared >= 1:
            v2.append((b * shared, shared, b, i, j))
    v2.sort(reverse=True)
    v2_top = v2[:TOPK]

    # how many of v1's top survive the structural filter (shared>=1)?
    v1_survive = sum(1 for b, i, j in v1_top if len(nbr[i] & nbr[j]) >= 1)

    print(f"[gap2] {M} ns, {len(edges)} deps, avg-degree {2*len(edges)/M:.1f}")
    print(f"[v1 pure-band ADD] {v1_survive}/{TOPK} of top proposals have ANY structural support "
          f"(shared dep-neighbour) — the rest are semantic-only (gap#2 spurious risk)")
    print("\n--- v2 STRUCTURE-AWARE ADD (band x shared-neighbours; coherent AND structurally adjacent) ---")
    if not v2_top:
        print("  NONE — no high-band non-edge pair shares a dependency-neighbour.")
    for score, shared, b, i, j in v2_top:
        print(f"  score={score:.2f} shared={shared} band={b:.3f} cos={cosM[i,j]:.3f}  "
              f"{name[i]}  <->  {name[j]}")

    out = {"method": "gap2 band x shared-dependency-neighbours (open-triad closure)",
           "v1_pure_band_structural_survival": f"{v1_survive}/{TOPK}",
           "v2_structure_aware": [{"a": name[i], "b": name[j], "shared_neighbours": shared,
                                   "band": float(b), "cos": float(cosM[i, j]), "score": float(s)}
                                  for s, shared, b, i, j in v2_top]}
    (D / "gap2-structural-proposals.json").write_text(json.dumps(out, indent=2))
    print(f"\n[finding] structural support {'sharpens' if v1_survive < TOPK else 'matches'} the "
          f"loss: v2 keeps only coherent+structurally-adjacent pairs (filters semantic-only "
          f"spurious). This is the gap#2 direction — loss = continuity-band AND structure, not cos alone.")
    print(f"wrote {D/'gap2-structural-proposals.json'}")


if __name__ == "__main__":
    main()
