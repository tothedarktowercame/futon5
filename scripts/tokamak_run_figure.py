#!/usr/bin/env python3
"""The tokamak run figure: the controller's choices coupled to the actual evolution.

This is the figure asked for in M-propagators: couple the run to the diagram, in the
idiom of propp_playoff.png, but with the system's real evolution underneath rather than
a summary line floating on its own.

Layout -- ONE shared time axis, 126 generations, top to bottom:

  panel 0   the Propp lines: per-window genotype transport for every arm. Window w is
            drawn as a step spanning the generations it actually governs, so a feature
            in the line sits directly above the generations that produced it.
  panel 1+  one strip per arm: 256-colour genotype (top) over black/white phenotype
            (bottom), time flowing LEFT TO RIGHT so it registers with the line above.
            Window boundaries are dashed; the action chosen for each window is printed
            in the band it governs.

Time flows rightward rather than the CA-conventional downward. That is deliberate and it
is the whole point of the figure: it is what lets you read a controller decision and the
evolution it caused off the same x coordinate.

Inputs are the artefacts the run already wrote -- no re-run needed:
  /tmp/tok4.json                 per-window transport + chosen actions per arm
  <LAB>/tok4-trace-<arm>.png     121x126 panel from futon5.mmca.render (the lab's own
                                 renderer, via .ppm): 60 genotype | 1 white | 60 phenotype

Usage:  python3 scripts/tokamak_run_figure.py [tok4.json]
"""
import json
import sys
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from PIL import Image

LAB = "holes/labs/M-aif-tokamak"
SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/tok4.json"
WINDOW_ROWS = 21          # run-wiring emits gen 0..20 inclusive per 20-generation window
GEN_W = 60                # genotype columns; then 1 white separator; then phenotype

# Arms to show: the two controllers, the best fixed arm, and the trap as reference.
# rotate+1 earns its place -- it is the arm whose evolution explains the whole result.
ARMS = ["greedy", "advantage", "rotate+2", "rotate+1"]
TITLES = {
    "greedy":    "greedy (one-step probe, no memory)  — WINS, and picks the trap 3/6",
    "advantage": "advantage A(a|s)=r+V(s')-V(s)  — degenerates to rotate+2, never switches",
    "rotate+2":  "rotate+2 (best fixed arm)",
    "rotate+1":  "rotate+1 = FIGURE 8 (the trap, held)  — highest transport, then death",
}

with open(SRC) as fh:
    d = json.load(fh)
arms = {a["mode"]: a for a in d["arms"]}

present = [m for m in ARMS if m in arms]
if not present:
    sys.exit("no usable arms in %s" % SRC)

nrow = 1 + len(present)
fig, axes = plt.subplots(nrow, 1, figsize=(15, 3.0 + 2.25 * len(present)),
                         gridspec_kw={"height_ratios": [2.4] + [1.6] * len(present)})

nwin = len(arms[present[0]]["per-window"][0])
T = nwin * WINDOW_ROWS
bounds = [w * WINDOW_ROWS for w in range(nwin + 1)]

# ---------------- panel 0: Propp lines, on the shared time axis ----------------
ax = axes[0]
HEAVY = {"greedy": ("#1f77b4", 2.6), "advantage": ("#d62728", 2.6)}
FIXED = {"rotate+2": "#2ca02c", "three+five": "#9467bd",
         "sigma-5127": "#8c564b", "rotate+1": "#ff7f0e", "identity": "#7f7f7f"}
allv = []
for mode, arm in arms.items():
    y = np.array(arm["per-window"], dtype=float).mean(axis=0)
    allv.extend(y.tolist())
    # step across the generations each window actually governs
    xs, ys = [], []
    for w, v in enumerate(y):
        xs += [bounds[w], bounds[w + 1]]
        ys += [v, v]
    if mode in HEAVY:
        c, lw = HEAVY[mode]
        ax.plot(xs, ys, color=c, lw=lw, zorder=5, label="%s (T=%.4f)" % (mode, arm["transport"]))
    else:
        ax.plot(xs, ys, color=FIXED.get(mode, "#ccc"), lw=1.2, ls="--", alpha=0.85, zorder=2,
                label="%s (T=%.4f)" % (mode, arm["transport"]))
lo, hi = min(allv), max(allv)
pad = max(0.02, (hi - lo) * 0.15)
ax.set_ylim(min(0.0, lo) - pad * 0.6, hi + pad)   # never crop an arm to the floor
ax.axhline(0.0, color="k", lw=0.8, alpha=0.5)
for b in bounds[1:-1]:
    ax.axvline(b, color="k", ls=":", lw=0.7, alpha=0.4)
ax.set_xlim(0, T)
ax.set_ylabel("transport\n(per window)")
ax.set_title("Tokamak — controller choices coupled to the actual evolution\n"
             "held-out seed; per-window genotype transport (above) over the run it governs (below)",
             fontsize=12)
ax.legend(fontsize=7.5, ncol=4, loc="lower left")
ax.grid(alpha=0.2)

# ---------------- panels 1..n: the evolution itself ----------------
for k, mode in enumerate(present):
    ax = axes[k + 1]
    img = np.asarray(Image.open("%s/tok4-trace-%s.png" % (LAB, mode)).convert("RGB"))
    # rows = time, cols = [genotype | white | phenotype] -> put time on x
    strip = np.transpose(img, (1, 0, 2))
    ax.imshow(strip, aspect="auto", interpolation="nearest",
              extent=[0, img.shape[0], strip.shape[0], 0])
    # Label the strips with explicit ticks at their centres. A rotated multi-line
    # ylabel reads bottom-to-top and silently inverts these two -- render-history-phenotype
    # emits [genotype | white | phenotype] per row, so after the transpose the GENOTYPE is
    # the upper strip. Ticks make that unambiguous.
    ax.set_ylabel(mode, fontsize=9)
    ax.set_yticks([GEN_W / 2, GEN_W + 1 + GEN_W / 2])
    ax.set_yticklabels(["genotype\n(256 colours)", "phenotype\n(b/w)"], fontsize=7)
    ax.set_title(TITLES.get(mode, mode), fontsize=9, loc="left", pad=3)
    for b in bounds[1:-1]:
        ax.axvline(b, color="w", ls="--", lw=1.0, alpha=0.9)
        ax.axvline(b, color="k", ls="--", lw=0.5, alpha=0.5)
    # the action governing each band, printed in the band
    acts = arms[mode].get("actions") or [mode] * nwin
    for w, a in enumerate(acts):
        ax.text((bounds[w] + bounds[w + 1]) / 2.0, 4, a, ha="center", va="top", fontsize=7.5,
                color="#ffe680" if a == "rotate+1" else "w",
                bbox=dict(boxstyle="round,pad=0.18", fc="k", ec="none", alpha=0.55))
    ax.set_xlim(0, T)

axes[-1].set_xlabel("generation   —   dashed = window boundary (where the controller may switch);"
                    " gold action label = rotate+1 (Figure 8), the trap")
fig.tight_layout()
out = "%s/tokamak_run_figure.png" % LAB
fig.savefig(out, dpi=170)
print("wrote", out)
