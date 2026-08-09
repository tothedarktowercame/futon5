#!/usr/bin/env python3
"""The frozen-gate control panel (fable, 2026-08-08).

Data: the seven matched (m, k) gate pairs measured 2026-07-29, recorded in
holes/tech-notes/TN-exotype-placement.md (16 seeds per row).  Departure :=
reach minus the blind-gate line 1.21 + 22.42 f at the gate's own measured
firing fraction.  The panel carries the load-bearing control of the
endogenous-gain result: every live departure is positive, every frozen
departure negative, though the two gates share predicate, width, spatial
statistics and (approximately) rate.

Run with the pinned figure environment (NOT system matplotlib 3.6.3):
  /home/joe/code/mmca-clj/.venv-figures/bin/python scripts/frozen_gate_figure.py \
      holes/tech-notes/paper/figures/
"""
import sys, os, matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.lines import Line2D
import numpy as np

DATA = '/home/joe/code/mmca-clj/data'

def field_rgb(tag, t0=40, t1=118):
    phe = [l.strip() for l in open(f"{DATA}/inset_{tag}_phe.txt")]
    dmg = [l.split() for l in open(f"{DATA}/inset_{tag}_dmg.txt")]
    H = t1 - t0 + 1; Wd = len(phe[0])
    img = np.ones((H, Wd, 3))
    for r in range(H):
        t = t0 + r
        row = phe[min(t, len(phe) - 1)]
        for x in range(Wd):
            if row[x] == '1':
                img[r, x] = (0.82, 0.82, 0.82)
        di = t - 60
        if 0 <= di < len(dmg):
            for x in range(Wd):
                if dmg[di][x] == '1':
                    img[r, x] = (0.77, 0.15, 0.11)
    return np.kron(img, np.ones((4, 4, 1)))

SURFACE, LIVE, INK, MUTED = '#fcfcfb', '#2a78d6', '#0b0b0b', '#52514e'
FROZENC = '#a09e94'   # the frozen-phase grey, darkened enough to read as a dot

# m, k, dep_live, dep_frozen   (TN-exotype-placement.md, 2026-07-29)
PAIRS = [
    (3, 2,  8.26, -4.18),
    (5, 3, 16.63, -3.53),
    (5, 4,  2.78, -2.00),
    (7, 4, 21.07, -3.98),
    (7, 5, 11.98, -2.27),
    (9, 5, 22.82, -3.32),
    (9, 7,  1.20, -0.04),
]

def main(out):
    rows = sorted(PAIRS, key=lambda r: (r[0], r[1]))
    fig = plt.figure(figsize=(11.4, 4.4))
    gs = fig.add_gridspec(2, 2, width_ratios=[8.6, 2.2], height_ratios=[1, 1],
                          wspace=0.06, hspace=0.22)
    ax = fig.add_subplot(gs[:, 0])
    for cell, tag, ttl, col in ((gs[0, 1], 'gatelive', 'live gate $(7,4)$: departure $+21.1$', LIVE),
                                (gs[1, 1], 'gatefrozen', 'frozen gate $(7,4)$: departure $-3.98$', FROZENC)):
        a = fig.add_subplot(cell)
        a.imshow(field_rgb(tag), aspect='equal', interpolation='nearest')
        a.set_xticks([]); a.set_yticks([])
        a.set_title(ttl, fontsize=7.4, color=col if col != FROZENC else '#6b6a63', pad=2.5)
        for sp in a.spines.values(): sp.set_color(col); sp.set_linewidth(1.3)
        a.set_facecolor(SURFACE)
    fig.patch.set_facecolor(SURFACE)
    ax.set_facecolor(SURFACE)
    ys = range(len(rows))
    for y, (m, k, dl, df) in zip(ys, rows):
        ax.plot([df, dl], [y, y], lw=1.4, color='#cfcdc4', zorder=1)
        ax.plot(dl, y, 'o', ms=9, color=LIVE, zorder=3)
        ax.plot(df, y, 'o', ms=9, color=FROZENC, zorder=3)
    ax.axvline(0, color=INK, lw=1.0, ls=(0, (4, 3)))
    ax.text(0, len(rows) - 0.25, ' blind line', fontsize=9.5, color=MUTED,
            ha='left', va='bottom')
    ax.set_yticks(list(ys))
    ax.set_yticklabels([f'agree {k}/{m}' for m, k, _, _ in
                        [(m, k, dl, df) for m, k, dl, df in rows]],
                       fontsize=10.5)
    ax.set_xlabel('departure from the blind line at the gate’s own firing '
                  'fraction (cells of reach)', fontsize=10.5, color=INK)
    ax.tick_params(labelsize=9.5, colors=MUTED)
    ax.set_ylim(-0.7, len(rows) - 0.3 + 0.9)
    for sp in ('top', 'right'):
        ax.spines[sp].set_visible(False)
    for sp in ('left', 'bottom'):
        ax.spines[sp].set_edgecolor('#cfcdc4')
    ax.legend(handles=[Line2D([], [], marker='o', ls='', ms=9, color=LIVE,
                              label='live gate'),
                       Line2D([], [], marker='o', ls='', ms=9, color=FROZENC,
                              label='frozen gate (same predicate, width, statistics)')],
              loc='lower right', frameon=False, fontsize=10)
    fig.tight_layout()
    for ext in ('png', 'pdf'):
        fig.savefig(os.path.join(out, 'frozen-gate.' + ext), dpi=300,
                    facecolor=SURFACE)
    print('wrote frozen-gate.{png,pdf}')

if __name__ == '__main__':
    main(sys.argv[1])
