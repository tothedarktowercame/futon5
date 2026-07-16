#!/usr/bin/env python3
"""Propp lines + trace panels for the tokamak advantage run (M-propagators).

Reads /tmp/tok4.json (written incrementally by scripts/tokamak_advantage.clj) and emits:

  tok4_propp.png   per-window transport per arm -- the Propp lines. The advantage and
                   greedy arms are drawn heavy; fixed arms are the reference field.
                   Action labels are printed under the advantage arm so a switch (or a
                   rotate+1 pick) is readable off the plot.
  tok4_qsa.png     the learned Q[phi][action] table as a heatmap -- this is where the
                   run's predictions live or die (P1: rotate+1 good from :rich;
                   P2: fatal from :collapsing/:dead).

Trace panels (256-colour genotype | white | b/w phenotype) are rendered by the Clojure
side via futon5.mmca.render (the lab standard) to .ppm; convert to .png with:
    for f in tok4-trace-*.ppm; do convert "$f" "${f%.ppm}.png"; done

Axis note: run 1's tokamak_playoff.png had a render bug -- an arm was pinned to the
floor by the axis scale, not by its data. Here the y-limits are set from the data with
explicit headroom, and a zero line is drawn so a genuine 0.0 (rotate+1 held to death)
is visibly ON the axis rather than merely near it.
"""
import json
import sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

LAB = "holes/labs/M-aif-tokamak"
SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/tok4.json"

with open(SRC) as fh:
    d = json.load(fh)

arms = d.get("arms", [])
if not arms:
    sys.exit("no arms in %s (run still in pass 1?)" % SRC)

HEAVY = {"advantage": ("#d62728", 2.8), "greedy": ("#1f77b4", 2.4)}
FIXED = {"rotate+2": "#2ca02c", "three+five": "#9467bd",
         "sigma-5127": "#8c564b", "rotate+1": "#ff7f0e", "identity": "#7f7f7f"}

# ---------- Propp lines ----------
fig, ax = plt.subplots(figsize=(11, 6))
allv = []
for arm in arms:
    mode = arm["mode"]
    # per_window is [seed][window]; average across the held-out seeds
    pw = np.array(arm["per-window"], dtype=float)
    y = pw.mean(axis=0)
    allv.extend(y.tolist())
    x = np.arange(1, len(y) + 1)
    if mode in HEAVY:
        c, lw = HEAVY[mode]
        ax.plot(x, y, color=c, lw=lw, marker="o", ms=6, zorder=5,
                label="%s  (T=%.4f)" % (mode, arm["transport"]))
    else:
        ax.plot(x, y, color=FIXED.get(mode, "#cccccc"), lw=1.3, ls="--", alpha=0.85,
                marker=".", ms=4, zorder=2,
                label="%s  (T=%.4f)" % (mode, arm["transport"]))

# label the advantage arm's choices -- a switch should be readable off the plot
adv = next((a for a in arms if a["mode"] == "advantage"), None)
if adv:
    y = np.array(adv["per-window"], dtype=float).mean(axis=0)
    for i, (a, p) in enumerate(zip(adv["actions"], adv.get("phis", []))):
        ax.annotate("%s\n[%s]" % (a, p), (i + 1, y[i]), textcoords="offset points",
                    xytext=(0, -34), ha="center", fontsize=6.5, color="#d62728")

lo, hi = min(allv), max(allv)
pad = max(0.02, (hi - lo) * 0.18)
ax.set_ylim(min(0.0, lo) - pad * 0.6, hi + pad)   # never crop an arm to the floor
ax.axhline(0.0, color="k", lw=0.8, alpha=0.5)
ax.set_xlabel("window (20 generations each)")
ax.set_ylabel("genotype transport (per-window)")
ax.set_title("Tokamak 4 — Propp lines: per-window transport by arm\n"
             "advantage = state-conditioned A(a|s) = r + V(s') - V(s);  held-out seeds")
ax.legend(fontsize=8, ncol=2, loc="best")
ax.grid(alpha=0.25)
fig.tight_layout()
fig.savefig("%s/tok4_propp.png" % LAB, dpi=150)
print("wrote %s/tok4_propp.png" % LAB)

# ---------- Q[phi][action] heatmap ----------
qsa, qm = d.get("Qsa", {}), d.get("Qm", {})
if qsa:
    bins = ["dead", "collapsing", "lean", "mid", "rich"]
    acts = list(qm.keys())
    M = np.full((len(acts), len(bins)), np.nan)
    N = np.zeros((len(acts), len(bins)), dtype=int)
    for i, a in enumerate(acts):
        for j, b in enumerate(bins):
            c = qsa.get("%s|%s" % (b, a))
            if c and c["n"] >= 3:
                M[i, j], N[i, j] = c["q"], c["n"]
    fig, ax = plt.subplots(figsize=(8, 4.2))
    v = np.nanmax(np.abs(M)) if np.any(~np.isnan(M)) else 1.0
    im = ax.imshow(M, cmap="RdBu_r", vmin=-v, vmax=v, aspect="auto")
    ax.set_xticks(range(len(bins))); ax.set_xticklabels(bins)
    ax.set_yticks(range(len(acts)))
    ax.set_yticklabels([a + ("  <- TRAP" if a == "rotate+1" else "") for a in acts])
    for i in range(len(acts)):
        for j in range(len(bins)):
            ax.text(j, i, "n/a" if np.isnan(M[i, j]) else "%.3f\nn=%d" % (M[i, j], N[i, j]),
                    ha="center", va="center", fontsize=7)
    ax.set_title("Learned Q[state][action] — per-window advantage\n"
                 "red = good move from that state, blue = costs you later")
    fig.colorbar(im, label="A(a|s)")
    fig.tight_layout()
    fig.savefig("%s/tok4_qsa.png" % LAB, dpi=150)
    print("wrote %s/tok4_qsa.png" % LAB)
