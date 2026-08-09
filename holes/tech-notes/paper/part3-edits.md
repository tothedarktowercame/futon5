# Proposed Part III edits

These are standoff proposals only. They address all 17 violations in
`part3-argument.edn` against the current `part3-exotype-v9.tex`. No manuscript
text has been changed.

## 1. Define changing and frozen phases at the part opening

- Violation quote: `the changing phase eliminates the frozen phase`
- Kind: `:opaque-compression`

OLD:

```tex
the changing phase eliminates the frozen phase
```

NEW:

```tex
the changing phase (sites whose phenotypes continue to update) eliminates the frozen phase (sites whose phenotypes remain unchanged for fifteen steps)
```

Rationale: The half-clause gloss makes the roadmap intelligible before the formal frozen/live definition appears, while preserving that later definition as the measurement specification.

## 2. Scope the noise result to the frozen-fraction measurement

- Violation quote: `reproducing the found configuration's structure`
- Kind: `:scope-creep`

OLD:

```tex
undirected noise prevents freezing without
reproducing the found configuration's structure.
```

NEW (a) — report only the measured frozen-fraction difference:

```tex
undirected noise prevents freezing but holds a frozen fraction near $0.3$, compared with $0.08$ in the found configuration at the same seed.
```

NEW (b) — preserve the unresolved structural question explicitly:

```tex
undirected noise prevents freezing, but whether the resulting state reproduces the found configuration's spatial structure remains open because no separating structural statistic was computed.
```

Rationale: Either version aligns the roadmap with the closure section; choice (a) foregrounds the measured difference, while choice (b) foregrounds the unresolved comparison.

## 3. Remove the duplicated statement of the part's move

- Violation quote: `This part removes that restriction`
- Kind: `:redundant`

OLD:

```tex
This part removes that restriction.
```

NEW:

```text
CUT
```

Rationale: The part opening already says that the operator becomes a property of the cell, and the following sentence supplies the concrete mechanism without this repeated framing.

## 4. Gloss beta and kappa at first use

- Violation quote: `policy precision $\beta$ and the epistemic weight $\kappa$`
- Kind: `:opaque-compression`

OLD:

```tex
policy precision $\beta$ and the epistemic weight $\kappa$
```

NEW:

```tex
policy precision $\beta$, which controls how strongly selection concentrates on the lowest-scoring candidate, and epistemic weight $\kappa$, which scales the information-seeking term relative to the goal-directed term
```

Rationale: This imports the two operational definitions from Supplement 5 at first use and makes the later high/low parameter descriptions interpretable without leaving Part III.

## 5. Name the observable pair and intrinsic objective

- Violation quote: `a runtime-computable pair of observables`
- Kind: `:opaque-compression`

OLD:

```tex
a runtime-computable pair of observables
tracks an intrinsic objective well
```

NEW:

```tex
two runtime-computable observables, halting share and change rate,
predict the intrinsic local-compressibility objective well
```

Rationale: Naming both predictors and their target makes the reported held-out fit checkable and gives the later run-level reference an unambiguous antecedent.

## 6. Stop calling the short-window objective a persistence score

- Violation quote: `region of non-zero persistence scores`
- Kind: `:opaque-compression`

OLD:

```tex
region of non-zero persistence scores
```

NEW:

```tex
region of non-zero values of the 250-step search objective
```

Rationale: The replacement prevents collision with the long-horizon persistence measure that this section deliberately uses instead of the search objective.

## 7. Repair the orphaned “third sense” back-reference

- Violation quote: `the phenomenon in the third sense`
- Kind: `:opaque-compression`

OLD:

```tex
This configuration is the phenomenon in the third sense distinguished at the
outset---frozen and moving structure coexisting, neither absorbing the other---and so the
one such a search would have been built to find.
```

NEW (a) — remove the taxonomy and name the property directly:

```tex
This configuration has the property the search was intended to locate: frozen and changing regions coexist throughout the observed run, and neither absorbs the other.
```

NEW (b) — minimally restore a third descriptive sense:

Insert after the part-opening transition paragraph:

```tex
Alongside the parameter-space and causal-propagation readings used in Parts~I and~II, Part~III uses a third, descriptive criterion: persistent coexistence of frozen and changing regions, with neither absorbing the other.
```

Then replace the OLD sentence with:

```tex
This configuration meets that third descriptive criterion and is therefore the outcome the search was intended to locate.
```

Rationale: Choice (a) is locally sufficient and avoids reviving a taxonomy; choice (b) restores a valid antecedent at the earliest point where the third sense would do work.

## 8. Replace the ambiguous parameter-grid “cell”

- Violation quote: `thirty-five-cell search of Supplement~5`
- Kind: `:opaque-compression`

OLD:

```tex
thirty-five-cell search of Supplement~5
```

NEW:

```tex
thirty-five-configuration parameter sweep reported in Supplement~5
```

Rationale: “Configuration” distinguishes a sampled parameter pair from the lattice cells discussed everywhere else in the part.

## 9. Remove the repeated externality claim while retaining the inventory

- Violation quote: `operated on whole runs from outside (Supplement~5)`
- Kind: `:redundant`

OLD:

```tex
operated on whole runs from outside (Supplement~5)
```

NEW:

```tex
is described in Supplement~5
```

Rationale: The outcomes section has already established that the apparatus varied and scored runs externally; this section needs the ensuing inventory of unavailable operations, not a second declaration of externality.

## 10. Refer back to the existing frozen definition

- Violation quote: `unchanged for $w = 15$`
- Kind: `:redundant`

OLD:

```tex
a site is
\emph{frozen} when its phenotype has been unchanged for $w = 15$
steps
```

NEW:

```tex
using the preceding section's definition, a site is frozen after fifteen unchanged phenotype steps
```

Rationale: This removes the late symbol `$w$` and marks the sentence as a back-reference rather than a competing definition.

## 11. Give the run-level result an explicit antecedent

- Violation quote: `The run-level detector result`
- Kind: `:opaque-compression`

OLD:

```tex
The run-level detector result is
untouched
```

NEW:

```tex
The run-level result---that halting share and change rate predict local compressibility with held-out $R^2 = 0.73$---is unchanged
```

Rationale: The replacement names the predictors, target, and result instead of asking the reader to bind “detector” to a parenthetical two sections earlier.

## 12. Cut the late beta/kappa gloss

- Violation quote: `(the selection policy's precision and`
- Kind: `:redundant`

OLD:

```tex
 (the selection policy's precision and
epistemic weight),
```

NEW:

```text
CUT
```

Rationale: Proposal 4 places the full operational definitions at first use, so this partial parenthetical is both late and less informative.

## 13. Replace unexplained “yoke” terminology

- Violation quote: `indistinguishable from its randomly placed yoke`
- Kind: `:opaque-compression`

OLD:

```tex
indistinguishable from its randomly placed yoke
```

NEW:

```tex
indistinguishable from its count-matched random-placement control
```

Rationale: The replacement states what is matched and how the control differs, without requiring prior knowledge of yoked-control terminology.

## 14. Define dwell in the body before the caption uses it

- Violation quote: `write per dwell at such cells`
- Kind: `:opaque-compression`

OLD:

```tex
A variant that instead gives
pure-neighbourhood cells one random operator write does prevent absorption on
all six seeds---but so does the same number of writes scattered at random
cells, to within seed noise, so the placement carries nothing.
```

NEW:

```tex
For the random-write intervention, each cell receives a fixed intervention interval sampled uniformly from 15 to 25 steps and a random phase offset; we call that interval its \emph{dwell}. When the interval elapses while the cell is in a pure neighbourhood, the cell receives one random operator write. This prevents absorption on all six seeds, but so does the same number of writes applied to uniformly random cells, to within seed noise, so the placement carries nothing.
```

Rationale: The body now defines dwell as a per-cell trigger interval, including its range and phase offset, before the figure caption uses “per dwell” and the two dwell ranges.

## 15. Name the pinned seed and the run it pins

- Violation quote: `the pinned seed of Figure`
- Kind: `:opaque-compression`

OLD:

```tex
the pinned seed of Figure~\ref{fig:exo-bisection}
```

NEW:

```tex
seed $2026102000$, whose unmodified-policy run is the absorbing $\beta=16$ example in Figure~\ref{fig:exo-bisection}
```

Rationale: The replacement identifies the actual seed and its relationship to the earlier figure instead of pointing to a caption that never labels a seed as pinned.

## 16. Spell out the two no-adoption control groups

- Violation quote: `uncontrolled arm at width $120$`
- Kind: `:opaque-compression`

OLD:

```tex
The same lattice,
started from the same initial conditions as the six absorbing runs above,
with the selection policy removed and its randomly assigned operators held
fixed, neither freezes solid nor dies out on any of the six seeds, holding a
frozen fraction of $0.083$--$0.145$ through the full horizon; the equivalent
uncontrolled arm at width $120$ does the same on twenty-four of twenty-four
seeds, even from a deliberately frozen-leaning start.
```

NEW:

```tex
In the first no-adoption control, six runs use the same initial conditions as the six absorbing runs above, but the randomly assigned operators remain fixed; none freezes solid or dies out, and their frozen fractions remain between $0.083$ and $0.145$ through the full horizon. In a second no-adoption control at width $120$, all twenty-four runs likewise retain both changing and frozen sites, including runs begun from a deliberately frozen-leaning state.
```

Rationale: This names the intervention and distinguishes the six matched runs from the twenty-four width-120 runs without overloading “arm” or “uncontrolled.”

## 17. Remove project archaeology from the scope paragraph

- Violation quote: `seven months of exploration in this substrate family`
- Kind: `:unsupported-digression`

OLD:

```tex
First, its scope is thirty runs, two geometries, and one vocabulary of twelve
operators; it is not a claim about rule-rewriting lattices generally, and
seven months of exploration in this substrate family before the selection
layer existed encountered nothing resembling the valley configuration.
```

NEW:

```tex
First, its scope is thirty runs, two geometries, and one vocabulary of twelve operators; it is not a claim about rule-rewriting lattices generally.
```

Rationale: Elapsed project time is not an assessable sampling protocol; the measured scope already gives the reader the defensible boundary.

## 18. Optional: make the part title state its claim

- Argument-map entry: `:title-matches-claim false`
- Kind: optional title repair

OLD:

```tex
\part{Exotypes}
```

NEW:

```tex
\part{Per-Cell Rewriting Operators Produce Three Outcomes but Cannot Select Coexistence Locally}
```

Rationale: The replacement identifies the object, the three-outcome result, and the negative local-control result instead of relying on an unexplained project term.
