#!/usr/bin/env python3
"""viz_stack_evolution.py — the FUTON stack (futon3c dep-graph) as an EVOLVING SURFACE.

Fixed spring layout; nodes colored by git BIRTH date (blue=old core → red=new); faint edges.
Emits a static birth-colored SVG + growth FRAMES (graph as-of birth quantiles, same layout) +
quantifies "grows at the edges" = corr(node age, distance-from-centroid). claude-6, for Joe.
  futon3a/.venv/bin/python viz_stack_evolution.py
"""
import json, pathlib
import numpy as np
import networkx as nx

G_IN = pathlib.Path("/tmp/f3c-stack-graph.json")
OUT = pathlib.Path("/home/joe/code/futon5/out/dataviz"); OUT.mkdir(parents=True, exist_ok=True)
W, H, PAD = 1100, 850, 60


def lerp_color(t):  # blue(old) -> red(new)
    a, b = (59, 130, 246), (239, 68, 68)
    r, g, bl = (int(a[i] + (b[i] - a[i]) * t) for i in range(3))
    return f"#{r:02x}{g:02x}{bl:02x}"


def main():
    d = json.loads(G_IN.read_text())
    nodes = [n for n in d["nodes"] if n["birth"]]
    birth = {n["id"]: n["birth"] for n in nodes}
    G = nx.Graph()
    G.add_nodes_from(birth)
    G.add_edges_from([tuple(e) for e in d["edges"] if e[0] in birth and e[1] in birth])
    pos = nx.spring_layout(G, seed=42, k=2.4 / np.sqrt(len(G)), iterations=300)
    deg = dict(G.degree())
    nodes_l = list(G.nodes())
    cen = np.mean([pos[n] for n in nodes_l], 0)
    ang = {n: float(np.arctan2(pos[n][1] - cen[1], pos[n][0] - cen[0])) for n in nodes_l}
    dist0 = {n: float(np.linalg.norm(np.array(pos[n]) - cen)) for n in nodes_l}
    # radius-RANK-transform: spread the crowded core uniformly across radii (decompress),
    # keeping each node's connectivity-angle. Monotonic in dist0 -> the age↔edge corr survives.
    order = sorted(nodes_l, key=lambda n: dist0[n])
    rrank = {n: 0.06 + 0.94 * (i / max(1, len(order) - 1)) for i, n in enumerate(order)}
    bs = np.array([birth[n] for n in nodes_l], float)
    rs = np.array([rrank[n] for n in nodes_l], float)
    edge_corr = float(np.corrcoef(bs, rs)[0, 1])   # >0 => newer nodes farther out = grows at edges

    bmin, bmax = bs.min(), bs.max()
    def nb(n): return (birth[n] - bmin) / (bmax - bmin + 1e-9)
    R = min(W, H) / 2 - PAD
    cx0, cy0 = W / 2, H / 2
    xy = {n: (cx0 + R * rrank[n] * np.cos(ang[n]), cy0 - R * rrank[n] * np.sin(ang[n]))
          for n in nodes_l}

    def render(cutoff_ct, title, fname):
        keep = {n for n in G.nodes() if birth[n] <= cutoff_ct}
        s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
             f'viewBox="0 0 {W} {H}" font-family="sans-serif">',
             f'<rect width="{W}" height="{H}" fill="#0d1117"/>']
        for u, v in G.edges():
            if u in keep and v in keep:
                (x1, y1), (x2, y2) = xy[u], xy[v]
                s.append(f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" '
                         f'stroke="#30363d" stroke-width="0.6"/>')
        for n in keep:
            x, y = xy[n]
            r = 3 + 1.6 * np.sqrt(deg.get(n, 0))
            s.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.1f}" fill="{lerp_color(nb(n))}" '
                     f'fill-opacity="0.85" stroke="#0d1117" stroke-width="0.5"/>')
        s.append(f'<text x="14" y="26" fill="#e6edf3" font-size="15">{title}</text>')
        s.append(f'<text x="14" y="{H-16}" fill="#8b949e" font-size="12">blue=old core → '
                 f'red=new · node size=degree · {len(keep)} ns</text>')
        s.append("</svg>")
        (OUT / fname).write_text("\n".join(s))

    render(bmax, f"FUTON stack (futon3c) — evolving surface, colored by birth · "
                 f"corr(age,edge-distance)={edge_corr:+.2f}", "stack-evolution.svg")
    qs = np.quantile(bs, [0.33, 0.66, 1.0])
    import datetime
    for i, q in enumerate(qs):
        dt = datetime.datetime.utcfromtimestamp(q).strftime("%Y-%m-%d")
        render(q, f"FUTON stack growth — frame {i+1}/3 as-of {dt}", f"stack-frame-{i+1}.svg")

    print(f"nodes {len(G)} edges {G.number_of_edges()}")
    print(f"[GROWS AT THE EDGES] corr(node-age, distance-from-centroid) = {edge_corr:+.3f}  "
          f"({'POSITIVE → newer nodes sit farther out = grows at the edges' if edge_corr>0.1 else 'weak/none'})")
    print(f"wrote {OUT}/stack-evolution.svg + 3 growth frames")


if __name__ == "__main__":
    main()
