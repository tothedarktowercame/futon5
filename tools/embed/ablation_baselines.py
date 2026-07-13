#!/usr/bin/env python3
"""ablation_baselines.py — does the differentiable/embedding/curvature apparatus EARN ITS
KEEP, or reduce to trivial baselines? (gap #2's stop-condition test, before any consent.)

Tests:
  ADD: differentiable rank (band x shared) vs (a) common-neighbours alone (graph link-pred,
       NO embedding) and (b) lexical name-token Jaccard (NO embedding/graph). Overlap of top-K.
  REVIEW: what fraction of the low-band 'near-duplicate' edges are just STRING-COMPANION pairs
       (dst name = src name + suffix / share all-but-last token) — i.e. string-matchable?
High baseline overlap / high string-companion fraction => the apparatus is decoration.
claude-6 (E2), read-only.  futon6/.venv/bin/python ablation_baselines.py
"""
import json, pathlib
import numpy as np

D = pathlib.Path("/home/joe/code/futon5/data/code-embeddings")
C, W, K = 0.60, 0.12, 20


def band(x): return np.exp(-((x - C) / W) ** 2)


def toks(nm):  # name tokens: split on . and -
    return set(t for p in nm.replace("-", ".").split(".") for t in [p] if t)


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

    # ---- ADD ablation: differentiable (band x shared) vs baselines ----
    diff, common, lexical = [], [], []
    for i in range(M):
        for j in range(i + 1, M):
            if (i, j) in edge_set:
                continue
            shared = len(nbr[i] & nbr[j])
            if shared < 1:
                continue
            b = band(cos[i, j])
            diff.append((b * shared, i, j))
            common.append((shared, i, j))                       # graph-only link prediction
            jac = len(toks(name[i]) & toks(name[j])) / max(1, len(toks(name[i]) | toks(name[j])))
            lexical.append((jac, i, j))                          # name-only
    def topset(lst):
        return set((i, j) for _, i, j in sorted(lst, reverse=True)[:K])
    dset, cset, lset = topset(diff), topset(common), topset(lexical)
    print(f"[ADD ablation] top-{K} overlap:")
    print(f"   differentiable(band x shared)  vs  common-neighbours-only : {len(dset & cset)}/{K}")
    print(f"   differentiable(band x shared)  vs  lexical-name-Jaccard   : {len(dset & lset)}/{K}")
    print(f"   => if ~{K}/{K} overlap with common-neighbours, the embedding/band add ~nothing to ADD.")

    # ---- REVIEW ablation: are low-band 'near-duplicate' edges just string companions? ----
    revs = sorted(edges, key=lambda ed: band(cos[ed]))[:K]
    def companion(a, b):
        na, nb = name[a], name[b]
        if na.startswith(nb) or nb.startswith(na):
            return True
        ta, tb = na.split("."), nb.split(".")
        return ta[:-1] == tb[:-1]                                # same namespace path, differ in last seg
    n_comp = sum(1 for x, y in revs if companion(x, y))
    print(f"\n[REVIEW ablation] of the {K} lowest-band edges, {n_comp}/{K} are STRING-COMPANION "
          f"pairs (shared namespace path / prefix) — detectable with NO embedding.")
    print("   examples:", [f"{name[x].split('.')[-1]}->{name[y].split('.')[-1]}"
                           for x, y in revs[:5]])

    verdict = (len(dset & cset) >= 0.7 * K) and (n_comp >= 0.7 * K)
    print(f"\n[VERDICT] {'APPARATUS LARGELY REDUCES TO BASELINES' if verdict else 'apparatus adds signal beyond baselines'}: "
          f"ADD overlaps common-neighbours {len(dset&cset)}/{K}; REVIEW is {n_comp}/{K} string-companion. "
          f"{'gap#2 stop-condition is knocking — rethink the loss before any apply.' if verdict else 'some genuine signal — but examine where.'}")
    (D / "ablation-baselines.json").write_text(json.dumps({
        "add_overlap_common_neighbours": f"{len(dset & cset)}/{K}",
        "add_overlap_lexical": f"{len(dset & lset)}/{K}",
        "review_string_companion_fraction": f"{n_comp}/{K}",
        "verdict": "reduces-to-baselines" if verdict else "adds-signal",
    }, indent=2))
    print(f"wrote {D/'ablation-baselines.json'}")


if __name__ == "__main__":
    main()
