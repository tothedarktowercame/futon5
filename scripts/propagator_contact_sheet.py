#!/usr/bin/env python3
"""Contact sheet for the propagator cluster exemplars (M-propagators).

Assembles the 10 replayed exemplar panels (genotype | white | phenotype, from
scripts/propagator_render_selection.clj) plus the cluster map into one sheet, so Joe can
see the variety of the space without looking at 20,000 runs.

NO EoC CLAIM. Clusters are structural (k=2, silhouette .44 -- weak, labelled weak).
Each panel is captioned sigma / cluster / role / cluster-size only. There is no class
label, no aliveness ranking, no EoC verdict -- the instruments are pincered
(M-propagators 4b) and none of these features certifies edge-of-chaos.

Coverage is stamped on the sheet: this is a shuffled-prefix sample of N/20,256, not the
whole space. Every prefix is a uniform random sample (seeded-shuffle build order), so the
cluster proportions are unbiased estimates -- but they are estimates.
"""
import csv
import glob
import os
import re
from collections import Counter
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import image as mpimg

LAB = "holes/labs/M-aif-tokamak/propagator-clusters"

# cluster sizes + coverage from the feature table
rows = list(csv.DictReader(open("%s/features.csv" % LAB)))
sizes = Counter(r["cluster"] for r in rows)
coverage = len(rows)

panels = sorted(glob.glob("%s/panels/*.png" % LAB))
# parse "c<cluster>-<role>-sigma-<perm>.png"
def meta(p):
    m = re.search(r"c(\d+)-:?([a-z-]+)-sigma-(\d+)", os.path.basename(p))
    return (m.group(1), m.group(2), m.group(3)) if m else ("?", "?", "?")

panels.sort(key=lambda p: (meta(p)[0], meta(p)[1] != "medoid"))  # medoid first per cluster

ncol = 3
nrow = (len(panels) + ncol - 1) // ncol + 1   # +1 row for the cluster map
fig = plt.figure(figsize=(4.2 * ncol, 3.0 * nrow))
gs = fig.add_gridspec(nrow, ncol)

# cluster map spans the top row
axm = fig.add_subplot(gs[0, :])
cmap_img = "%s/cluster-map.png" % LAB
if os.path.exists(cmap_img):
    axm.imshow(mpimg.imread(cmap_img))
axm.axis("off")
axm.set_title(
    "Propagator regime clusters — %d/20,256 orbits (shuffled prefix; unbiased sample)\n"
    "k=2, silhouette 0.44 (WEAK: one body + one tail, not clean structure).  "
    "cluster 0 = %s orbits, cluster 1 = %s orbits.\n"
    "Panels below: genotype (256-colour) | phenotype (b/w). Captions are structural — "
    "NO edge-of-chaos / class claim." % (coverage, sizes.get("0", "?"), sizes.get("1", "?")),
    fontsize=11)

for i, p in enumerate(panels):
    c, role, sig = meta(p)
    ax = fig.add_subplot(gs[1 + i // ncol, i % ncol])
    ax.imshow(mpimg.imread(p), interpolation="nearest", aspect="auto")
    ax.set_title("cluster %s (%s orbits) — %s\nsigma %s" % (c, sizes.get(c, "?"), role, sig),
                 fontsize=9)
    ax.set_xticks([]); ax.set_yticks([])
    ax.set_xlabel("genotype        phenotype", fontsize=7)

fig.tight_layout()
out = "%s/contact_sheet.png" % LAB
fig.savefig(out, dpi=150)
print("wrote", out, "(%d panels)" % len(panels))
