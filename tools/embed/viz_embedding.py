#!/usr/bin/env python3
"""viz_embedding.py — picture(s) of the pattern+mission embedding (SVG, no matplotlib).

Emits two views:
  - pattern-mission-embedding.svg            : all 1062 patterns + 197 missions
  - pattern-mission-embedding-no-iiching.svg : iiching EXCLUDED + RE-PROJECTED — the iiching
    patterns form a disconnected island (189 pts) that distorts the PCA; removing them reveals
    the real design-pattern + mission landscape (Joe's observation, 2026-06-02).
claude-6.  futon3a/.venv/bin/python viz_embedding.py
"""
import json, colorsys, hashlib, pathlib
import numpy as np

N = pathlib.Path("/home/joe/code/futon3a/resources/notions")
OUT = pathlib.Path("/home/joe/code/futon5/out/dataviz"); OUT.mkdir(parents=True, exist_ok=True)
W, H, PAD = 1100, 800, 40


def fam_color(s):
    h = int(hashlib.md5(str(s).encode()).hexdigest()[:6], 16) / 0xFFFFFF
    r, g, b = colorsys.hsv_to_rgb(h, 0.55, 0.85)
    return f"#{int(r*255):02x}{int(g*255):02x}{int(b*255):02x}"


def render(rows, fname, title):
    X = np.asarray([v for _, _, v in rows], float)
    Xc = X - X.mean(0)
    U, S, Vt = np.linalg.svd(Xc, full_matrices=False)
    P = Xc @ Vt[:2].T
    var = (S[:2] ** 2).sum() / (S ** 2).sum()
    mn, mx = P.min(0), P.max(0)
    sx = (W - 2 * PAD) / (mx[0] - mn[0]); sy = (H - 2 * PAD) / (mx[1] - mn[1])
    xy = np.column_stack([PAD + (P[:, 0] - mn[0]) * sx, PAD + (mx[1] - P[:, 1]) * sy])
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" '
         f'font-family="sans-serif">', f'<rect width="{W}" height="{H}" fill="#0d1117"/>',
         f'<text x="14" y="24" fill="#e6edf3" font-size="15">{title} · PCA-2D {var:.0%} var · '
         f'patterns=dots by family, missions=◆</text>']
    for (k, lab), (x, y) in zip([(k, l) for k, l, _ in rows], xy):
        if k == "pattern":
            s.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="2.6" fill="{fam_color(lab)}" fill-opacity="0.7"/>')
    for (k, lab), (x, y) in zip([(k, l) for k, l, _ in rows], xy):
        if k == "mission":
            s.append(f'<path d="M{x:.1f},{y-5:.1f} L{x+5:.1f},{y:.1f} L{x:.1f},{y+5:.1f} '
                     f'L{x-5:.1f},{y:.1f} Z" fill="#ff7b72" stroke="#fff" stroke-width="0.5"/>')
    s.append("</svg>")
    (OUT / fname).write_text("\n".join(s))
    return var


def main():
    pats = json.loads((N / "minilm_pattern_embeddings.json").read_text())
    miss = json.loads((N / "minilm_mission_embeddings.json").read_text())
    rows = [("pattern", str(p.get("id", "")).split("/")[0], p["vector"]) for p in pats] + \
           [("mission", str(m.get("phase", "?")), m["vector"]) for m in miss]
    v1 = render(rows, "pattern-mission-embedding.svg", "pattern+mission (ALL)")
    rows2 = [r for r in rows if not (r[0] == "pattern" and r[1] == "iiching")]
    n_ii = len(rows) - len(rows2)
    v2 = render(rows2, "pattern-mission-embedding-no-iiching.svg",
                f"pattern+mission (iiching {n_ii} EXCLUDED + re-projected)")
    print(f"ALL: {len(rows)} pts, PCA {v1:.1%} var -> pattern-mission-embedding.svg")
    print(f"NO-IICHING: {len(rows2)} pts ({n_ii} iiching dropped), PCA {v2:.1%} var "
          f"-> pattern-mission-embedding-no-iiching.svg")


if __name__ == "__main__":
    main()
