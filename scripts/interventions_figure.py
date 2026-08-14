#!/usr/bin/env python3
"""Combined local-interventions figure: two intervention families at the
freezing arm (beta=16, kappa=0.1, pinned seed), phenotype spacetimes.
Row 1: writes at phase-uniform cells (the Figure-13 family).
Row 2: writes at phase-boundary cells, two dwell ranges.
Writes figures/interventions.{pdf,png}."""
import numpy as np, matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

D = '/home/joe/code/futon5/figs/'
OUT = '/home/joe/code/futon5/holes/tech-notes/paper/figures/'
ROWS = [
    [("binvg-policy", "unmodified policy\n(absorbs, $t=1722$)"),
     ("binvg-invade-adopt", "boundary adopt\n(absorbs earlier, $t=1484$)"),
     ("binvg-invade-mutate", "pure-cell random write\n(holds a mixture)"),
     ("binvg-yoked", "yoked random write\n(holds a mixture)")],
    [("blastg-blast", "boundary write, dwell 15--25\n(near-frozen, live seam)"),
     ("blastg-yoked-blast", "its yoke\n(coarse patchwork)"),
     ("blastg-blast-fast", "boundary write, dwell 5--10\n(fine mixture)"),
     ("blastg-yoked-fast", "its yoke\n(fine mixture)")],
]
ROWLAB = ["writes at phase-uniform cells", "writes at phase-boundary cells"]

fig, axes = plt.subplots(2, 4, figsize=(13.5, 9.2))
for r, row in enumerate(ROWS):
    for c, (tag, label) in enumerate(row):
        rows_ = [l.rstrip('\n') for l in open(D + tag + '-phe.txt')]
        P = np.array([[1 if ch == '1' else 0 for ch in line] for line in rows_], np.int8)
        ax = axes[r][c]
        ax.imshow(np.repeat(P, 4, axis=1), cmap='binary', aspect='auto',
                  interpolation='nearest')
        ax.set_title(label, fontsize=9.5)
        ax.set_xticks([])
        ax.set_yticks([0, 1000, 2000, 3000] if c == 0 else [])
        if c == 0:
            ax.set_ylabel(ROWLAB[r] + "\ntime step", fontsize=10)
plt.subplots_adjust(left=0.075, right=0.99, top=0.94, bottom=0.02,
                    hspace=0.18, wspace=0.05)
for ext in ('pdf', 'png'):
    plt.savefig(OUT + 'interventions.' + ext, dpi=300)
print("wrote interventions.{pdf,png}")
