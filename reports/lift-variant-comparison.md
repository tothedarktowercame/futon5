# Exotype lift variant comparison

Fixed seed `20260803`; `N=200` independently sampled 36-bit neighbourhoods;
width `80`; `120` MetaCA steps. The hexagram is treated only as a situation key.

| variant | occupancy | flip-locality | within | between | ratio |
|---|---:|---:|---:|---:|---:|
| eigen-sign | 32 | 1.327361 | 1.332958 | 1.314760 | 0.986348 |
| eigen-ordering | 17 | 1.432778 | 1.320089 | 1.315050 | 0.996183 |
| eigen-magnitude | 28 | 1.533889 | 1.270582 | 1.320901 | 1.039604 |
| symmetrised | 5 | 0.209167 | 1.318458 | 1.313222 | 0.996028 |
| random | 60 | 2.971250 | 1.309440 | 1.315748 | 1.004817 |

## Method

Each neighbourhood supplies four eight-bit initial genotype rules
(`LEFT/EGO/RIGHT/NEXT`), repeated across the 80-cell ring, plus the four-bit
phenotype-family repeated as the initial phenotype. The existing
`futon5.ca.core/run-for-generations-3` machinery evolves each state. Its
behavioural signature concatenates the 120-step genotype-diversity, phenotype
change-rate, and genotype mutation-rate trajectories.

Every signature coordinate is z-scored across the 200 neighbourhoods (constant
coordinates become zero). Distances are root-mean-square Euclidean distances in
that normalised 360-coordinate space. `within` averages pairs sharing a key;
`between` averages pairs with different keys; `ratio = between / within`.
Occupancy counts distinct keys. Flip locality averages line Hamming distance over
all 36 single-bit flips of all sampled neighbourhoods.

The `eigen-sign` arm calls the existing lift unchanged. For `eigen-ordering`,
Commons Math eigenvalues retain decomposition positions, are ranked by numeric
value with original position as the deterministic tie-break, and the upper three
ranks are yang. `eigen-magnitude` thresholds absolute values at the median (the
mean of the middle pair). `symmetrised` applies `(M + M^T) / 2` before taking
signs. `random` takes six bits from SHA-256 of the seed and all 36 bits.

Two fresh runs produced byte-identical EDN output, SHA-256
`f08686812045c6bb3fe1a7573c0844ea8b4878b3a0139f712fce68d1d8f5099e`.
