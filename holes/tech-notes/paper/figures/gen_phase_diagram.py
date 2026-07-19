#!/usr/bin/env python3
"""Generate eoc_phase.png: the offset+2 feedforward finite-size scan as a
phase-behaviour figure (no sharp critical point; a finite-width crossover band)
plus paired illustrative spacetime realizations across the propagator duty cycle q.

Reads only committed data in figures/data/ (see PROVENANCE.md). No /tmp, no network.
Run from the paper dir: python3 figures/gen_phase_diagram.py
"""
import os, re
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "phase_data")

# --- parse the committed scan table ---
rows = []
for ln in open(os.path.join(DATA, "E2b-scan-results.md")):
    m = re.match(r"\|\s*(\d+)\s*\|\s*([\d.]+)\s*\|\s*([\d.]+)\s*\|\s*([\d.]+)\s*\|"
                 r"\s*([\d.]+)\s*\|\s*([-\d.]+)\s*\|\s*([-\d.]+)\s*\|\s*([\d.]+)\s*\|", ln)
    if m:
        rows.append([float(x) for x in m.groups()])
d = np.array(rows)                      # cols: L q a_G a_X expH chi Binder Pcollapse
Ls = sorted(set(d[:, 0]))
cmap = {30: "#4575b4", 60: "#74add1", 120: "#f46d43", 240: "#a50026"}

# --- isolated-rule activity score for the tint ---
score = {}
for l in open(os.path.join(DATA, "rule_scores.txt")):
    r, s = l.split()
    score[int(r)] = float(s)
sv = np.vectorize(lambda r: score[int(r)])
def load(p):
    return np.array([[int(x) for x in l.split()] for l in open(p) if l.strip()])

fig = plt.figure(figsize=(13, 8))
gs = fig.add_gridspec(2, 4, height_ratios=[1.15, 1], hspace=0.34, wspace=0.28)
axA, axB, axC, axD = (fig.add_subplot(gs[0, i]) for i in range(4))
for L in Ls:
    s = d[d[:, 0] == L]; c = cmap[L]
    axA.plot(s[:, 1], s[:, 2], "o-", color=c, ms=3, label=f"L={int(L)}")
    axB.plot(s[:, 1], s[:, 5], "o-", color=c, ms=3)
    axC.plot(s[:, 1], s[:, 7], "o-", color=c, ms=3)
    axD.plot(s[:, 1], s[:, 6], "o-", color=c, ms=3)
axA.axvspan(0.10, 0.50, color="0.85", zorder=0)                 # the operational crossover band
axA.set_title(r"(a) genotype activity $a_G$", fontsize=9); axA.legend(fontsize=7)
axA.annotate("intermediate-activity\nband (operational)", (0.30, 0.12), fontsize=6.5, ha="center", color="0.35")
axB.set_title(r"(b) susceptibility $L\,\mathrm{Var}(a_G)$", fontsize=9)
axB.annotate("no convergent interior maximum;\nlarge-L maxima at the sampled boundary", (0.02, 2.5),
             fontsize=6.5, color="#a50026", ha="left")
axC.set_title("(c) finite-horizon P(collapse)", fontsize=9)
axC.annotate("vanishes with size", (0.25, 0.35), fontsize=6.5, color="0.35")
axD.set_title("(d) Binder cumulant (no crossing)", fontsize=9); axD.axhline(0, color="gray", lw=.5, ls=":")
for ax in (axA, axB, axC, axD):
    ax.set_xlabel("propagator duty cycle  $q$", fontsize=8); ax.tick_params(labelsize=7); ax.grid(alpha=.25)

panels = [(0, "$q=0$: blend-dominated (low activity)"),
          (50, "$q=0.05$: sparse"),
          (250, "$q=0.25$: intermediate (coexisting domains)"),
          (750, "$q=0.75$: propagator-dominated (high activity)")]
for i, (q, lbl) in enumerate(panels):
    ax = fig.add_subplot(gs[1, i])
    ax.imshow(sv(load(os.path.join(DATA, f"phase_q{q:03d}.txt"))),
              cmap="coolwarm", vmin=0, vmax=1, aspect="auto", interpolation="nearest")
    ax.set_xticks([]); ax.set_yticks([]); ax.set_title(lbl, fontsize=7.5)
fig.suptitle("offset$+2$ feedforward finite-size scan ($L=30$–$240$, 32 seeds): no sharp critical point, "
             "a finite-width crossover band\n(bottom: paired illustrative realization, width 240, single seed, shared IC)",
             fontsize=10)
out = os.path.join(HERE, "eoc_phase.png")
fig.savefig(out, dpi=115, bbox_inches="tight", facecolor="white")
print("wrote", out)
