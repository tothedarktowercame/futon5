# Genotype transport separates the registered nulls; it cannot certify EoC

**Result and limitation:** preregistered, bit-plane genotype transport cleanly
separates three live propagators from ordinary-mutation noise, and its windowed
profile tracks the dead regimes into their frozen attractors.  This is evidence
of directed rule-space transport relative to these nulls.  It is **not** an
edge-of-chaos instrument: fixed-rule ECAs have no genotype spacetime, so there
is no positive Wolfram-class ground truth on this axis.

The protocol was frozen in commit `492f5fd` before the identity-propagator
scores were observed.  Full definitions and stopping rules are in
`genotype_transport_PREREG.md`.  Width was 60, each run recorded 121 genotype
rows, and all comparisons used matched seeds 0–4.  Windows were 20 rows at
stride 10.  Each sigil was decoded by the legacy MetaCA mapping; bilateral
innovation transport was measured independently on its eight truth-table bit
planes and averaged without tuning.

## Ordered gates

### A. Frozen l0 arithmetic check

The heterogeneous l0 initial field contained 50 distinct rules.  Repeating it
for 121 rows produced zero innovation and a median transport score of exactly
`0.0`.  This is a plumbing check, not evidence: a frozen genotype must score
zero by construction.

### B–C. Identity null versus the live set

The table reports per-run medians of the windowed score, in seed order 0–4.

| regime | role | seed medians | mean |
|---|---|---|---:|
| identity `[0 1 2 3 4 5 6 7]` | busy random-walk null | 0.1252, 0.1230, 0.1436, 0.1431, 0.1269 | 0.1323 |
| rotate+2 | live | 0.2510, 0.2751, 0.2618, 0.2566, 0.2671 | 0.2623 |
| `[5 1 2 7 6 0 4 3]` | live | 0.2144, 0.2112, 0.2206, 0.2077, 0.2548 | 0.2217 |
| `(0 1 2)(3 4 5 6 7)` | live | 0.1962, 0.2356, 0.2633, 0.2706, 0.2783 | 0.2488 |

All 15 preregistered matched-seed comparisons passed.  The global live floor
was `0.196174`, strictly above the identity ceiling `0.143551`.  Identity also
had *more* per-bit innovation activity than much of the live set (typically
about 0.31–0.38 versus roughly 0.10–0.34), so the score did not merely rank the
busiest genotype highest.  The decisive registered-null gate therefore passed.

### D. Dead-regime profiles

The dead regimes show the transient which a run-level mean concealed.  Mean
window scores at starts `t = 0,10,...,100` were:

- rotate+1 / Figure 8: `0.3193, 0.2855, 0.2847, 0.1693, 0.0845, 0.0231,
  0.0101, 0, 0, 0, 0`;
- two disjoint 4-cycles: `0.3233, 0.2997, 0.2340, 0.1554, 0.0583, 0.0446,
  0.0273, 0.0160, 0, 0, 0`.

Thus Figure 8's early surviving phase has strong genotype transport—initially
stronger than the persistent live regimes—then decays to exactly zero as the
genotype freezes.  Across seeds its last phenotype change was at t=36–47; its
transport profile reached zero by the windows beginning at t=50–70.  The other
dead permutation behaved similarly, with one longer seed dying at t=74.

## Verdict

The frozen barcode and busy identity dynamics do not explain the observed
genotype diagonals.  Within this registered family, the statistic detects
directed rule-space transport and exposes the transient-to-attractor sequence
which the eye identified in Figure 8.  Because there is no positive genotype
anchor outside MetaCA, the only warranted claim is **separation from these
nulls**.  Whether the transported regime is edge-of-chaos or durable structured
dynamics remains open and requires an independent positive criterion.

Reproducer: `scripts/genotype_transport.clj`.  Auditable summary and every
window/bit-plane score: `data/genotype-transport/gates.edn`.  Raw canonical
genotype rows for all 30 runs are under the fingerprinted
`data/genotype-transport/runs/eb5cfcbbf72b6898/` directory.
