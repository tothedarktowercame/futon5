#!/usr/bin/env python3
"""The boundary-invasion rescue figure (fable, 2026-08-08).

Four arms at beta=16, kappa=0.1, seed 2026102000 (the paper's pinned seed),
from sheets dumped by scripts/boundary_invasion_dump.clj into figs/.
House style follows mmca-clj/scripts/exotype_figures.py exactly: blue live,
warm-grey frozen, red frame for never-absorbs, integer-replicated columns so
matplotlib never rescales a tall array (see the shear note there).

Usage (MUST use the pinned figure environment -- system matplotlib 3.6.3
emits malformed one-bit PDF image streams, see mmca-clj/requirements-figures.txt):
  /home/joe/code/mmca-clj/.venv-figures/bin/python scripts/boundary_invasion_figure.py \
      figs/ holes/tech-notes/paper/figures/
"""
import sys, os, numpy as np, matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap
from matplotlib.patches import Patch

SURFACE, LIVE, FROZEN, INK, MUTED = '#fcfcfb', '#2a78d6', '#e8e6dd', '#0b0b0b', '#52514e'
RED = '#b8291f'
W = 15
XSCALE = 8

ARMS = [
    ("blast",       "blast boundary, dwell 15-25"),
    ("yoked-blast", "its yoke"),
    ("blast-fast",  "blast boundary, dwell 5-10"),
    ("yoked-fast",  "its yoke"),
]

def load(d, arm, expect=3000):
    p = os.path.join(d, f"blast-{arm}-phe.txt")
    rows = [l.rstrip('\n') for l in open(p)]
    widths = set(len(r) for r in rows)
    if len(widths) != 1:
        raise ValueError(f"{p}: ragged sheet, widths {sorted(widths)}")
    if expect and len(rows) != expect:
        raise ValueError(f"{p}: {len(rows)} rows, expected {expect}")
    return rows, np.array([[1 if c == '1' else 0 for c in r] for r in rows], dtype=np.int8)

def expand(M, k=XSCALE):
    return np.repeat(M, k, axis=1)

def frozen_map(P, w=W):
    same = (P[1:] == P[:-1])
    F = np.zeros((P.shape[0]-1, P.shape[1]), np.int8)
    for t in range(w, F.shape[0]):
        F[t] = (same[t-w:t].sum(0) == w)
    return F[w:]

def absorbs_at(rows):
    for t in range(len(rows)-1, 0, -1):
        if rows[t] != rows[t-1]:
            return t if t < len(rows)-50 else None
    return None

def main(d, out):
    data = {}
    for arm, _ in ARMS:
        rows, P = load(d, arm)
        F = frozen_map(P)
        data[arm] = dict(rows=rows, F=F, t_abs=absorbs_at(rows),
                         traj=F.mean(1), late=F[-500:].mean())

    cmap = ListedColormap([LIVE, FROZEN])
    # Tall enough that at dpi=300 the panel raster carries >= the sheet's rows
    # (the check_exotype_pdf.py no-crush condition), and interpolation='nearest'
    # so the downscale happens once, in matplotlib, not in the PDF viewer.
    fig = plt.figure(figsize=(15.2, 14.6))
    fig.patch.set_facecolor(SURFACE)
    gs = fig.add_gridspec(2, 4, height_ratios=[3.4, 1.0],
                          left=0.055, right=0.925, top=0.905, bottom=0.085,
                          hspace=0.26, wspace=0.10)

    for i, (arm, title) in enumerate(ARMS):
        ax = fig.add_subplot(gs[0, i])
        dd = data[arm]
        ax.imshow(expand(dd['F']), cmap=cmap, aspect='auto', vmin=0, vmax=1,
                  interpolation='nearest',
                  extent=[0, dd['F'].shape[1], dd['F'].shape[0] + 2*W, 2*W])
        ax.set_title(title, fontsize=13.5, pad=8, fontweight='bold', color=INK)
        t_abs = dd['t_abs']
        label = (f"absorbs at t={t_abs}\nfrozen {data[arm]['late']:.3f}" if t_abs
                 else f"never absorbs\nfrozen {data[arm]['late']:.3f}")
        ax.set_xlabel(label, fontsize=10.5, color=(MUTED if t_abs else RED), labelpad=6)
        ax.set_xticks([])
        if i == 0:
            ax.set_ylabel('time step', fontsize=11.5, color=INK)
            ax.tick_params(axis='y', labelsize=9, colors=MUTED)
        else:
            ax.set_yticks([])
        for s in ax.spines.values():
            s.set_edgecolor('#cfcdc4' if t_abs else RED)
            s.set_linewidth(0.7 if t_abs else 2.4)

    # frozen-fraction trajectories: colour = fate (red freezes, blue survives),
    # linestyle + direct end-labels carry arm identity, so identity is never
    # colour-alone.
    axt = fig.add_subplot(gs[1, :])
    styles = {"blast": (RED, 'solid'), "yoked-blast": (RED, (0, (4, 2))),
              "blast-fast": (LIVE, 'solid'), "yoked-fast": (LIVE, (0, (4, 2)))}
    short = {"blast": "blast 15-25", "yoked-blast": "yoke", "blast-fast": "blast 5-10", "yoked-fast": "yoke "}
    for arm, title in ARMS:
        c, ls = styles[arm]
        ts = np.arange(len(data[arm]['traj'])) + 2*W
        axt.plot(ts, data[arm]['traj'], lw=1.6, color=c, ls=ls, alpha=0.9)
        axt.annotate(short[arm], (ts[-1], data[arm]['traj'][-1]),
                     xytext=(7, {"blast": 5, "yoked-blast": -6,
                                 "blast-fast": -8, "yoked-fast": 7}[arm]),
                     textcoords='offset points', fontsize=9.5, color=c,
                     va='center', annotation_clip=False)
    axt.set_xlim(0, 3000)
    axt.set_ylim(-0.02, 1.04)
    axt.set_xlabel('time step', fontsize=11, color=INK)
    axt.set_ylabel('frozen fraction', fontsize=11, color=INK)
    axt.tick_params(labelsize=9, colors=MUTED)
    axt.margins(x=0)
    for sp in axt.spines.values():
        sp.set_edgecolor('#cfcdc4')
    axt.spines['top'].set_visible(False)
    axt.spines['right'].set_visible(False)

    fig.suptitle('Random writes at the boundary ($\\beta$=16, $\\kappa$=0.1)',
                 fontsize=16, y=0.978, color=INK)
    fig.text(0.5, 0.945,
             'Frozen := unchanged for 15 steps.  Seed 2026102000.  '
             'Overrides begin at t=40: one random operator write per firing, '
             'mixed-locale (boundary) cells on their dwell clock; yokes match the count at random cells.',
             ha='center', fontsize=10.5, color=MUTED)
    fig.legend(handles=[Patch(facecolor=LIVE, label='live (changing)'),
                        Patch(facecolor=FROZEN, edgecolor='#cfcdc4',
                              label=r'frozen ($\geq$15 steps)'),
                        Patch(facecolor='none', edgecolor=RED, lw=2.2,
                              label='never absorbs')],
               loc='lower center', bbox_to_anchor=(0.5, 0.005),
               ncol=3, frameon=False, fontsize=10.5)
    for ext in ('png', 'pdf'):
        plt.savefig(os.path.join(out, 'boundary-blast.' + ext), dpi=300,
                    facecolor=SURFACE)
    plt.close()
    print("wrote blast-rescue.{png,pdf}")
    for arm, _ in ARMS:
        print(f"  {arm}: absorbs_at={data[arm]['t_abs']}  late_frozen={data[arm]['late']:.3f}")

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
