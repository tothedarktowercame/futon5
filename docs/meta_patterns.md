# Meta-Pattern Rosetta Stone

Each entry pairs a human narrative with a tensor-operator signature and a
minimal empirical signature. This is a starting draft; add exemplars as
we accumulate runs.

## BlendHand (手)

**Intent (human)**  
Weave two rule behaviors into one evolving field; create gradual shifts
instead of abrupt phase switches.

**Tensor signature (math)**  
`F : (G, P, K_a, K_b, α) → (G', P')`

**Mechanism (operator)**  
`K_eff = α * K_a + (1-α) * K_b` (soft)  
or `K_eff = choose(K_a, K_b)` (hard), then evolve with `K_eff`.

**Empirical signature (metrics)**  
Mid-range compressibility and temporal autocorr; motifs drift without
collapsing or freezing.

---

## Freeze Genotype (止)

**Intent (human)**  
Hold the rule field fixed so the phenotype must adapt within a stable
physics.

**Tensor signature (math)**  
`F : (G, P) → (G, P')`

**Mechanism (operator)**  
`G' = G`  
`P' = evolve(P | G)`

**Empirical signature (metrics)**  
High stability, low genotype change; often brittle under lesion (large
lesion discrepancy), especially for stripe-like attractors.

---

## Genotype Gate (屯)

**Intent (human)**  
Let phenotype regulate how much the genotype can change; slow drift
without freezing.

**Tensor signature (math)**  
`F : (G, P, K) → (G', P')`  
`G' = gate(P) ⊙ G + (1 - gate(P)) ⊙ G_new`

**Mechanism (operator)**  
Compute `G_new` from `K`, then mask updates using a phenotype-derived
gate (default keep-signal = 1).

**Empirical signature (metrics)**  
Lower lesion discrepancy than frozen runs; phenotype remains dynamic
while genotype drift is slowed.

---

## Uplift Operator (升)

**Intent (human)**  
Promote recurring motifs into executable operators; lift patterns into
new transformation hooks.

**Tensor signature (math)**  
`F : (G, P, O, Θ) → (G', P', O')`

**Mechanism (operator)**  
Scan genotype windows, count motifs, and promote those above a frequency
threshold into an operator-set; optionally reinforce motifs back into
the field. `Θ` includes `window_size`, `freq_thresh`, `learning_rate`.

**Empirical signature (metrics)**  
Motif counts rise and stabilize; `:motif_counts` and `:uplift/promoted`
appear in metrics; repeated motifs propagate across generations.

---

## Use Operators (术)

**Intent (human)**  
Enable operator hooks during the run so meta-patterns can act on the
world state.

**Tensor signature (math)**  
`F : (G, P, K, O) → (G', P')`

**Mechanism (operator)**  
Set `:use-operators true`, compile `pattern-sigils` into operator hooks,
and apply them across init/observe/decide/act stages.

**Empirical signature (metrics)**  
Operator-derived metrics appear; kernel or grid changes trace to
operator actions; run summaries list active operators.

---

## Phenotype On (生)

**Intent (human)**  
Introduce a phenotype register so genotype and phenotype can co-evolve.

**Tensor signature (math)**  
`F : (G, P?, K) → (G', P')`

**Mechanism (operator)**  
Set `:phenotype? true` to initialize a phenotype (if missing) and evolve
it each generation against the genotype.

**Empirical signature (metrics)**  
Non-empty phenotype history and phenotype metrics; phenotype dynamics
track or counterpoint genotype motifs.

---

## Lock Kernel (门)

**Intent (human)**  
Prevent kernel switches mid-run so operator activity cannot change the
rule family once the run starts.

**Tensor signature (math)**  
`F : (G, P, K, O) → (G', P')` with `K` fixed

**Mechanism (operator)**  
Set `:lock-kernel true` so rule deltas from operators are ignored; only
grid updates apply.

**Empirical signature (metrics)**  
Kernel stays constant across generations; operator metrics can appear,
but kernel change events do not.

---

## Global Rule Genotype (专)

**Intent (human)**  
Initialize genotype as a single repeated rule sigil to emulate a
classic uniform CA baseline.

**Tensor signature (math)**  
`F : (R, N) → G` where `G = repeat(rule->sigil(R), N)`

**Mechanism (operator)**  
Set `:genotype-mode :global-rule` and seed the genotype by repeating the
selected rule sigil across the width.

**Empirical signature (metrics)**  
Initial genotype is uniform; early dynamics reflect the chosen rule
before other operators perturb the field.

---

## Random Genotype (凡)

**Intent (human)**  
Seed the run with a stochastic baseline to explore behaviors without
prior structure.

**Tensor signature (math)**  
`F : (N, RNG) → G`

**Mechanism (operator)**  
Set `:genotype-mode :random` and sample sigils uniformly to build the
initial genotype.

**Empirical signature (metrics)**  
High initial diversity; early metrics show a wide sigil spread before
later operators impose structure.

---

## White Space Scout (川)

**Intent (human)**  
Probe sparse or inactive regions and trigger exploratory pulses where
the field is under-expressed.

**Tensor signature (math)**  
`F : (G, P, Θ) → (G', P')`

**Mechanism (operator)**  
Draft: detect low-activity windows and inject perturbations or kernel
swaps to re-seed variation. (Pattern lift stub; no concrete MMCA hook
yet.)

**Empirical signature (metrics)**  
Localized spikes in change-rate or diversity following sparse patches;
activity re-enters previously quiet regions.

---

## Hunger Precision (义)

**Intent (human)**  
Map scarcity or reserve pressure onto precision/temperature controls so
exploration tightens or loosens based on resource signals.

**Tensor signature (math)**  
`F : (G, P, Θ, ρ) → (G', P')`

**Mechanism (operator)**  
Draft: compute a global or regional “hunger” metric `ρ` and adjust
mutation/selection intensity accordingly. (Pattern lift stub; no
concrete MMCA hook yet.)

**Empirical signature (metrics)**  
Metric-driven shifts in variability; exploration accelerates under high
hunger and stabilizes as reserves recover.

---

## Facet (面)

**Intent (human)**  
Bind a generic operator scaffold to a specific domain context; make
reusable inference loops concrete.

**Tensor signature (math)**  
`F : (G, P, O, C) → (G', P')`

**Mechanism (operator)**  
Draft: wrap operator sets with a context adaptor `C` that shapes inputs
and outputs into domain-specific channels. (Pattern lift stub; no
concrete MMCA hook yet.)

**Empirical signature (metrics)**  
Operator metrics reflect domain-specific channels; behavior becomes
repeatable across runs with the same facet.

---

## HyperAnt (代)

**Intent (human)**  
Chain exploratory agents into a higher-order scout that composes facets
and emits new operator proposals.

**Tensor signature (math)**  
`F : (O, C, Θ) → O'`

**Mechanism (operator)**  
Draft: aggregate multiple exploration outputs into candidate operator
bundles, then feed them back as pattern-sigils. (Pattern lift stub; no
concrete MMCA hook yet.)

**Empirical signature (metrics)**  
Operator set grows via proposals; new pattern-sigils appear across runs.

---

## Accumulator (付)

**Intent (human)**  
Maintain a shared energy or credit pool that modulates collective
operator activity.

**Tensor signature (math)**  
`F : (G, P, E, Θ) → (G', P', E')`

**Mechanism (operator)**  
Draft: track a global resource `E` and allocate operator intensity based
on it. (Pattern lift stub; no concrete MMCA hook yet.)

**Empirical signature (metrics)**  
Operator intensity correlates with resource levels; high `E` phases
permit richer variation, low `E` phases dampen change.
