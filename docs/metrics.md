# MMCA Metrics Notes

These metrics are intended as **proxies**, not goals. They describe
different aspects of run behavior so that meta-rules can be compared
without baking in a single aesthetic.

## Core Metrics

**Interestingness (composite)**  
Blend of entropy, change-rate, and diversity. It measures how much the
system varies over time and how varied the symbol set remains.

- `entropy` (genotype): Shannon entropy over sigils.
- `change-rate` (genotype): normalized Hamming distance between steps.
- `diversity` (genotype): unique sigils / length.
- `phe-entropy` / `phe-change`: phenotype-only versions (useful when
  genotype is frozen in classic CA runs).

**Compressibility (LZ78 ratio)**  
Proxy for structure vs noise. Lower ratios are more compressible
(repetition/structure), higher ratios are more random. Extremes in
either direction can be uninteresting; mid-range often indicates
structured motion.

**Autocorrelation (spatial/temporal)**  
Persistence/periodicity proxy.

- `spatial-autocorr`: how often adjacent cells match.
- `temporal-autocorr`: how often a cell remains the same across steps.

High temporal autocorr implies stasis, low implies chaos; mid-range can
indicate persistent structure (gliders, motifs).

## Notes

- These are *heuristics*, not proofs of computational capacity.
- We use multiple signals to avoid collapsing to trivial fixed points or
  pure noise.
- Meta-rule evolution should treat these as descriptive axes, not a
  single objective.
