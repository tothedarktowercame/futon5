#!/usr/bin/env python3
"""forecast_harness.py — GRAPH-AGNOSTIC temporal link-forecasting harness (#4).

The shared methodology for the next experiments: given a graph snapshot as-of time T and a
per-pair SIGNAL (the representation under test), forecast the edges actually added in (T, T+δ],
scored against classical temporal-graph baselines. Both #3 (code-graph, claude-6) and #5
(mission-graph, claude-3) plug into THIS harness — only the (nodes, snapshot, future, signal)
inputs differ. claude-6 owns the core; claude-3 fits #5's inputs + bridge-score signal.

Contract (what each experiment supplies):
  nodes          : list of node ids
  snapshot_edges : set of frozenset({u,v}) present as-of T   (the graph the model sees)
  future_edges   : set of frozenset({u,v}) ADDED in (T, T+δ]  (ground-truth positives)
  signals        : {name -> fn(u, v) -> float}  (higher = more likely; the representation
                   under test, e.g. #3 call-signature sim, #5 curvature, #2 text-cos)
Built-in baselines (computed from snapshot, always scored alongside): preferential-attachment,
common-neighbours (triadic), adamic-adar. (bridge-score is pluggable — claude-3 supplies E1's.)
Output per signal: average-precision, precision@k, and LIFT over the best baseline.
The institutionalized rule: a representation only "wins" if it beats the baselines here.
"""
from __future__ import annotations
import math, random
from collections import defaultdict


def _adj(nodes, snapshot_edges):
    nb = {n: set() for n in nodes}
    for e in snapshot_edges:
        u, v = tuple(e)
        if u in nb and v in nb:
            nb[u].add(v); nb[v].add(u)
    return nb


def builtin_baselines(nb):
    deg = {n: len(s) for n, s in nb.items()}

    def pref_attachment(u, v):
        return deg.get(u, 0) * deg.get(v, 0)

    def common_neighbours(u, v):
        return len(nb.get(u, set()) & nb.get(v, set()))

    def adamic_adar(u, v):
        return sum(1.0 / math.log(deg[w]) for w in (nb.get(u, set()) & nb.get(v, set()))
                   if deg.get(w, 0) > 1)

    return {"pref-attachment": pref_attachment,
            "common-neighbours": common_neighbours,
            "adamic-adar": adamic_adar}


def _candidates(nodes, snapshot_edges, future_edges, neg_ratio, seed):
    present = set(snapshot_edges)
    pos = [e for e in future_edges if e not in present]          # new edges (not already there)
    pos_set = set(pos)
    rng = random.Random(seed)
    want = max(neg_ratio * len(pos), 100)
    negs, tries = set(), 0
    nodes = list(nodes)
    while len(negs) < want and tries < want * 50:
        tries += 1
        u, v = rng.choice(nodes), rng.choice(nodes)
        if u == v:
            continue
        e = frozenset({u, v})
        if e in present or e in pos_set or e in negs:
            continue
        negs.add(e)
    return pos, list(negs)


def _average_precision(ranked_labels):
    hits, ap = 0, 0.0
    for i, lab in enumerate(ranked_labels, 1):
        if lab:
            hits += 1; ap += hits / i
    return ap / hits if hits else 0.0


def forecast(nodes, snapshot_edges, future_edges, signals=None,
             neg_ratio=10, ks=(5, 10, 20), seed=0):
    nb = _adj(nodes, snapshot_edges)
    sigs = dict(builtin_baselines(nb))
    if signals:
        sigs.update(signals)                                     # representation(s) under test
    pos, negs = _candidates(nodes, snapshot_edges, future_edges, neg_ratio, seed)
    cands = [(e, 1) for e in pos] + [(e, 0) for e in negs]
    random.Random(seed + 1).shuffle(cands)   # break score-ties FAIRLY (else a constant
                                             # signal inherits pos-first input order = bogus AP)
    out = {}
    for name, fn in sigs.items():
        scored = []
        for e, lab in cands:
            u, v = tuple(e)
            scored.append((fn(u, v), lab))
        scored.sort(key=lambda t: t[0], reverse=True)
        labels = [lab for _, lab in scored]
        ap = _average_precision(labels)
        pk = {f"p@{k}": (sum(labels[:k]) / k if k <= len(labels) else None) for k in ks}
        out[name] = {"ap": ap, **pk}
    # lift of each non-baseline signal over the best baseline AP
    base = max(out[b]["ap"] for b in builtin_baselines(nb))
    for name in (signals or {}):
        out[name]["lift_over_best_baseline"] = out[name]["ap"] - base
    out["_meta"] = {"n_pos": len(pos), "n_neg": len(negs), "best_baseline_ap": base}
    return out


# ---- toy self-test: a graph that grows by triadic closure; common-neighbours should win ----
if __name__ == "__main__":
    rng = random.Random(1)
    nodes = list(range(60))
    snap = set()
    for _ in range(120):                                         # random base graph
        u, v = rng.randrange(60), rng.randrange(60)
        if u != v:
            snap.add(frozenset({u, v}))
    nb = _adj(nodes, snap)
    # future edges: close open triads (u,v share a neighbour, not yet connected)
    fut = set()
    for u in nodes:
        for v in nodes:
            if u < v and frozenset({u, v}) not in snap and len(nb[u] & nb[v]) >= 2:
                fut.add(frozenset({u, v}))
    # a deliberately useless signal (constant) to confirm baselines beat noise
    res = forecast(nodes, snap, fut, signals={"useless-constant": lambda u, v: 1.0}, seed=2)
    print("toy forecast (graph grows by triadic closure):")
    for k, v in res.items():
        print(f"  {k:20s} {v}")
    print("expect: common-neighbours/adamic-adar AP >> pref-attachment ~ useless-constant; "
          "harness mechanics validated.")
