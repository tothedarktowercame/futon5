#!/usr/bin/env python3
"""build_code_edit_proposals.py — M-differentiable-code's actual output: turn the
delivered metric (O4(c) embedding + sparse feeds-A) into ranked dependency EDIT-PROPOSALS.

This is the E2 consumer build (parallel to M-aif2 slice-1): a credited consumer of the
delivered signal. NAMESPACE-grain file-dependency (the multi-target differentiable choice).
ADDITIVE / read-only over the cache+graph; emits a proposals artifact; applies NOTHING
(no codebase mutation, no live-WM) -> no consent gate. claude-6 (E2).

Method = the band-satisfaction landscape that the O4(b) gradient descends (authored band,
center 0.60 = coherent-not-duplicate; gap-3: bands authored, never fit):
  - REVIEW: existing ns->ns deps with LOW band-sat (cos far from 0.60 — distant dep, or
    cos>>0.60 near-duplicate). The gradient wants to weaken these.
  - ADD: non-edge ns pairs with HIGH band-sat (cos near 0.60, coherent-not-duplicate, not
    currently connected). The gradient wants to add these.
gap#2 discipline: these are LEGIBLE DISAGREEMENT to examine (extracted band vs drawn wiring),
NOT auto-applied edits — the disagreement is the signal.

  futon6/.venv/bin/python build_code_edit_proposals.py   (numpy only; no model load)
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
    ns_idx = np.asarray(rel["ns_idx"])                       # node-indices of the 423 ns nodes
    pos = {int(g): i for i, g in enumerate(ns_idx)}          # node-index -> ns-row
    name = [ids[int(g)]["id"] for g in ns_idx]
    ns_emb = emb[ns_idx]
    M = len(ns_idx)

    # existing ns->ns file-dependency edges, mapped to ns-rows
    edges = [(pos[s], pos[d]) for s, d in rel["fdep_edges"] if s in pos and d in pos]
    edge_set = set(edges)

    cosM = ns_emb @ ns_emb.T                                 # 423x423 (tiny)
    bandM = band(cosM)

    # REVIEW existing deps: lowest band-sat
    rev = sorted(edges, key=lambda e: bandM[e[0], e[1]])[:TOPK]
    # ADD candidates: non-edges with highest band-sat (exclude self + existing + symmetric dup)
    cand = []
    for i in range(M):
        for j in range(M):
            if i != j and (i, j) not in edge_set:
                cand.append((bandM[i, j], cosM[i, j], i, j))
    cand.sort(reverse=True)
    seen, add = set(), []
    for b, cs, i, j in cand:
        key = frozenset((i, j))
        if key in seen:
            continue
        seen.add(key); add.append((b, cs, i, j))
        if len(add) >= TOPK:
            break

    print(f"[edit-proposals] {M} namespaces, {len(edges)} existing file-dep edges")
    print("\n--- REVIEW: existing dependencies the band scores LOW (examine, do not auto-drop) ---")
    for s, d in rev:
        cs = cosM[s, d]
        why = "distant (cos<<0.6)" if cs < C else "near-duplicate (cos>>0.6)"
        print(f"  band={bandM[s,d]:.3f} cos={cs:.3f} [{why:24s}] {name[s]} -> {name[d]}")
    print("\n--- ADD-candidate: coherent-not-duplicate ns pairs NOT currently connected ---")
    for b, cs, i, j in add:
        print(f"  band={b:.3f} cos={cs:.3f}  {name[i]}  <->  {name[j]}")

    out = {
        "grain": "namespace", "relation": "file-dependency",
        "band": {"center": C, "width": W, "authored": True},
        # E2 :satisfied evidence — the delivered metric is released-AND-CONSUMED here
        "consumption_evidence": {
            "consumed_embedding": {"artifact": "code-emb.npy", "shape": list(emb.shape),
                                   "model": "BAAI/bge-large-en-v1.5"},
            "consumed_feeds_A": {"artifact": "relations.json",
                                 "ownership_edges": len(rel["own_edges"]),
                                 "file_dep_edges": len(rel["fdep_edges"])},
            "how": "E2 (M-differentiable-code) reads the delivered O4(c) embedding (continuity "
                   "band = cos in authored band) over the feeds-A file-dependency graph and "
                   "produces ranked dependency edit-proposals — i.e. the metric's continuity band "
                   "is consumed to generate M-differentiable-code's actual output.",
            "n_review": TOPK, "n_add_candidates": TOPK,
            "escrow": "candidate E2 :satisfied (released-AND-consumed); dissolution-authority = Joe.",
        },
        "n_namespaces": M, "n_existing_edges": len(edges),
        "review": [{"from": name[s], "to": name[d], "cos": float(cosM[s, d]),
                    "band": float(bandM[s, d])} for s, d in rev],
        "add_candidates": [{"a": name[i], "b": name[j], "cos": float(cs), "band": float(b)}
                           for b, cs, i, j in add],
        "note": "Legible disagreement (extracted band vs drawn wiring), gap#2 — examine, NOT auto-apply. "
                "Method = the band-satisfaction landscape the O4(b) gradient descends.",
    }
    (D / "edit-proposals.json").write_text(json.dumps(out, indent=2))
    print(f"\nwrote {D/'edit-proposals.json'}")


if __name__ == "__main__":
    main()
