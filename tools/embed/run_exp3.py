#!/usr/bin/env python3
"""run_exp3.py — Experiment #3: does as-of-T CALL-SIGNATURE structure forecast real code-edge
additions better than names + classical graph baselines? (leak-free; all signals as-of T.)

Inputs (both as-of the same OLD commit):
  /tmp/f3c-temporal.json        (snapshot graph as-of T + future_edges added since)
  /tmp/f3c-callsig.json         (per-ns call-signature bag as-of T)
Runs the shared graph-agnostic harness. The institutionalized rule: call-signature only "wins"
if it beats pref-attachment / common-neighbours / adamic-adar AND a name-token baseline.
claude-6 (E2).  python3 run_exp3.py
"""
import json, math, sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
from forecast_harness import forecast


def main():
    temp = json.loads(open("/tmp/f3c-temporal.json").read())
    csig = json.loads(open("/tmp/f3c-callsig.json").read())
    snap_nodes = set(temp["snapshot"]["nodes"])
    snap_edges = set(frozenset(e) for e in temp["snapshot"]["edges"])
    # forecastable positives: future edges between nodes that EXISTED at T (leak-free)
    future = set(frozenset(e) for e in temp["future_edges"]
                 if e[0] in snap_nodes and e[1] in snap_nodes)
    nodes = list(snap_nodes)
    print(f"[exp3] snapshot {len(snap_nodes)} nodes / {len(snap_edges)} edges; "
          f"forecastable future edges (both endpoints existed @T): {len(future)}")

    def toks(ns):
        return set(t for t in ns.replace("-", ".").split(".") if t)

    def cossig(a, b):
        va, vb = csig.get(a, {}), csig.get(b, {})
        if not va or not vb:
            return 0.0
        keys = set(va) & set(vb)
        dot = sum(va[k] * vb[k] for k in keys)
        na = math.sqrt(sum(v * v for v in va.values()))
        nb = math.sqrt(sum(v * v for v in vb.values()))
        return dot / (na * nb) if na and nb else 0.0

    def name_jac(a, b):
        ta, tb = toks(a), toks(b)
        return len(ta & tb) / max(1, len(ta | tb))

    signals = {"call-signature(structure)": cossig, "name-token(lexical)": name_jac}
    res = forecast(nodes, snap_edges, future, signals=signals, neg_ratio=10, seed=7)
    print("\n  signal                        AP      p@10    p@20   lift")
    for name, m in res.items():
        if name == "_meta":
            continue
        lift = m.get("lift_over_best_baseline")
        print(f"  {name:28s} {m['ap']:.3f}   {m.get('p@10')}   {m.get('p@20')}   "
              f"{('%+.3f'%lift) if lift is not None else '(baseline)'}")
    print(f"\n  {res['_meta']}")
    best_base = res["_meta"]["best_baseline_ap"]
    cs = res["call-signature(structure)"]["ap"]
    nm = res["name-token(lexical)"]["ap"]
    print(f"\n[verdict] call-signature AP {cs:.3f} vs best-baseline {best_base:.3f} vs name-token "
          f"{nm:.3f} -> structure {'BEATS' if cs > best_base and cs > nm else 'does NOT beat'} "
          f"baselines+names. {'Structural signal earns its keep on code-edge forecasting.' if cs>best_base and cs>nm else 'Same lesson as the symbol smoke test, now with a clean temporal target.'}")


if __name__ == "__main__":
    main()
