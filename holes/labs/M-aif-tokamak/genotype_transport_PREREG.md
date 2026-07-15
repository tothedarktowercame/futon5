# Genotype transport: preregistered rejection probe

**Headline limitation:** this probe cannot certify edge-of-chaos behavior.
An ECA has one fixed rule and therefore supplies no genotype spacetime or
positive Wolfram-class anchor.  The probe can only reject candidate regimes
which fail to distinguish directed rule-space transport from null dynamics.

This protocol was committed before measuring the identity-propagator null.
No definition, aggregation, gate, seed, or horizon below will be changed after
that observation.  A failure at the decisive identity gate is a bankable
negative, not an invitation to tune the statistic.

## Fixed representation and statistic

Each MetaCA genotype sigil is decoded by the legacy
`get-genotype-from-sigil` function into its canonical eight-bit rule string.
The eight truth-table bit-planes are kept separate.  On each plane, use the
already banked bilateral innovation statistic unchanged:

- temporal innovation is `D[t,x] = X[t+1,x] XOR X[t,x]`;
- measure absolute lag-one phi correlation at velocities `-3..-1, 1..3`;
- the plane score is the smaller of the strongest left and right correlation;
- use 20-row windows at stride 10;
- the genotype window score is the arithmetic mean of the eight plane scores.

The result also retains every per-plane score.  The mean innovation density is
the per-bit Hamming-change rate and is reported only as an activity diagnostic.
We explicitly reject the rule-changed indicator: it would collapse all 255
non-identical rule transitions together, recreating the coarse-graining
confound which invalidated the local-causal-state result.

Each run is summarized by the median of its window scores, while the full
window profile remains the primary observation.  All regimes use width 60,
120 evolution steps (121 recorded rows), and matched seeds `[0 1 2 3 4]`.

## Gates, in order

1. **Frozen l0 arithmetic check (trivial).** Repeat the actual heterogeneous
   l0-baseline initial genotype for all 121 rows.  Every plane innovation,
   window score, and median must be exactly zero.  This verifies plumbing only;
   it is not evidence for the probe.
2. **Identity null (decisive baseline).** Measure ordinary mutation,
   `sigma = [0 1 2 3 4 5 6 7]`.  There is no post-hoc absolute cutoff: its five
   matched-seed profiles define the busy, undirected null distribution.
3. **Live-set separation (decisive verdict).** Measure rotate+2
   `[2 3 4 5 6 7 0 1]`, observed sigma `[5 1 2 7 6 0 4 3]`, and
   `(0 1 2)(3 4 5 6 7)` `[1 2 0 4 5 6 7 3]`.  The probe survives only if
   (a) every live median strictly exceeds the identity median at its matched
   seed, and (b) the minimum median over all live runs strictly exceeds the
   maximum identity median.  Otherwise it is banked as measuring durable
   activity/noise rather than transport.
4. **Dead-set description.** Only after passing Gate 3, measure rotate+1
   `[1 2 3 4 5 6 7 0]` and `(0 1 2 3)(4 5 6 7)`
   `[1 2 3 0 5 6 7 4]`.  Report their profiles without adding another
   acceptance threshold; in particular inspect whether Figure 8 decays toward
   the null as its genotype freezes.

Gate 3 is intentionally stronger than a mean comparison and contains no tuned
numeric boundary.  Passing would establish separation from these registered
nulls only—not an EoC label, a universal transport detector, or positive ground
truth.
