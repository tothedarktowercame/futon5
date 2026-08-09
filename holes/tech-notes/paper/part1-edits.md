# Proposed Part I edits

Scope: proposals only for Part I of `draft9.tex`, from `\part{The Permutation Core}` up to (but not including) `\part{Beyond the Permutation Core: Feedback and Causal Propagation}`. Items 1--9 correspond one-for-one to the violations in `part1-argument.edn`; item 10 addresses the argument map's additional section-title finding. No manuscript edit is applied here.

## 1. Either support or demote the Rule 110 interpretation

- Violation quote: “The operator does not merely stir rule space”
- Kind: `:unsupported-digression`

OLD:

```tex
The operator does not merely stir rule space: it
drives it toward particular, computationally distinguished rules.
```

NEW, alternative (a) — retain the interpretation and argue it in the text:

```tex
The two operators therefore produce different rule-frequency distributions: Rule~110 reaches $39\%$ at the representative seed ($35\%$ over six seeds) under $\sig=16250374$, but remains below $2\%$ under $\sig=10275364$.
```

Also insert after the paragraph ending “the adjacent constructions in the literature”:

```tex
Figure~\ref{fig:regimes} gives one measured example of this selectivity: under $\sig=16250374$, Rule~110 occupies $39\%$ of the genotype at the displayed seed and $35\%$ over six seeds, whereas under $\sig=10275364$ it remains below $2\%$. These frequencies establish operator-dependent concentration on Rule~110 in the tested runs; they do not establish a general preference for computationally distinguished rules.
```

NEW, alternative (b) — make the caption purely descriptive:

```tex
The two tested operators produce different rule-frequency distributions, including a large difference in the observed frequency of Rule~110.
```

Rationale: Alternative (a) moves the evidential argument into the body and scopes it to the measured operators and seeds; alternative (b) removes the untested generalisation while preserving what the figure directly shows.

## 2. Replace the repeated fixed-point derivation with a cross-reference

- Violation quote: “each even cycle admits two alternating”
- Kind: `:redundant`

OLD:

```tex
Under an all-even $\sig$, the fixed points of $P_\sig$---the rules it maps to
themselves---number $2^k$, where $k$ is the number of cycles of $\sig$
(\secref{sec:classification}): each even cycle admits two alternating
colourings, chosen independently across cycles.
```

NEW:

```tex
Under an all-even $\sig$, the fixed points of $P_\sig$---the rules it maps to themselves---number $2^k$, where $k$ is the number of cycles of $\sig$ (\secref{sec:classification}).
```

Rationale: The classification section already proves the independent two-colouring count, so this paragraph needs only its consequence.

## 3. Name the two observables instead of invoking unspecified metrics

- Violation quote: “the metric measures tested do not separate the two”
- Kind: `:opaque-compression`

OLD:

```tex
Diversity of the observed population and singularity of its activity thus
coexist; the metric measures tested do not separate the two
(see the opening of \secref{sec:causal}).
```

NEW:

```tex
The observed fields can therefore contain several distinct rules even though every fixed rule has the same activity value, $\lambda=1/2$. Terminal rule diversity and fixed-rule activity describe different properties of the field; the causal tests of \secref{sec:causal} examine whether either predicts perturbation reach.
```

Rationale: The replacement names the concrete quantities, states their logical relationship, and defers the actual causal comparison to the section that reports it.

## 4. Define neighbour blending at first use

- Violation quote: “stochastic elementary writes and, in the census, neighbour”
- Kind: `:opaque-compression`

OLD:

```tex
Theorem~1--2 concern fixed bytes; the spatial
dynamics adds stochastic elementary writes and, in the census, neighbour
blending.
```

NEW:

```tex
Theorem~1--2 concern fixed bytes; the spatial dynamics adds stochastic elementary writes and, in the census, neighbour blending. In a neighbour-blend step, each truth-table coordinate of a cell's rule is updated from the corresponding coordinates of the left, centre, and right rules: agreeing outer values are copied, while disagreement is resolved by applying the centre rule to that three-bit neighbourhood.
```

Rationale: This gives the main text the concrete coordinate-wise operation required to understand “with” and “without” blending without consulting the supplement.

## 5. Promote the braid result into the section hierarchy

- Violation quote: “\subsection{Alternating two collapsing operators sustains a diverse field}”
- Kind: `:scope-creep`

OLD:

```tex
\subsection{Alternating two collapsing operators sustains a diverse field}
```

NEW:

```tex
\section{Alternating Two Collapsing Operators Sustains a Diverse Field}
```

Rationale: The braid is one of Part I's four advertised results and should appear as its own claim in the table of contents rather than as a subordinate consequence of the fixed-point discussion.

## 6. Move the spatial-organisation aside out of the crossover argument

- Violation quote: “The genotype field is also spatially organised”
- Kind: `:belongs-elsewhere`

OLD:

```tex
The genotype field is also spatially organised. Tinting by activity resolves it
into coexisting low- and high-activity domains whose boundaries trace
fractal-like filamentary walls through spacetime; those walls carry elevated
genotype churn, and at matched activity the two sides hold measurably different
rule populations, so they are not an artefact of the tint that draws them. We
report that structure in Supplement~4, together with a measure of
information transport along those boundaries that we built, validated against
three nulls, and then withdrew when it proved to be an artefact of its own
construction.
```

NEW:

```tex
Supplement~4 reports a separate descriptive analysis of spatial domains in the diverse phase. That analysis is not used to infer a critical point.
```

Companion OLD:

```tex
We then ask
whether the sustained diversity is spatially organised into coexisting
dynamical regimes.
```

Companion NEW: CUT

Rationale: The section argues from finite-size diagnostics; a short pointer preserves the separate observation without inserting project history or an unused transport analysis into that argument.

## 7. Replace the ungrounded “third edge” count with a direct scope statement

- Violation quote: “Neither is a third edge, and nothing in the argument below depends”
- Kind: `:opaque-compression`

OLD:

```tex
Neither is a third edge, and nothing in the argument below depends
on them.
```

NEW:

```tex
The spatial-domain observation is descriptive and does not alter the finite-size evidence for a broad crossover.
```

Rationale: The replacement states the argumentative status directly, without requiring the reader to reconstruct an implicit count of “edges.”

## 8. Remove undefined Part II vocabulary from the figure caption

- Violation quote: “Offset $+1$ feedforward”
- Kind: `:opaque-compression`

OLD:

```tex
Offset $+1$ feedforward, $L = 30$--$240$, $32$ seeds per size.
```

NEW:

```tex
Offset $+1$ without phenotype feedback, $L = 30$--$240$, $32$ seeds per size.
```

Rationale: The concrete absence of phenotype feedback is intelligible in Part I and anticipates the later contrast without relying on the undefined label “feedforward.”

## 9. Remove the third restatement of the historical example's status

- Violation quote: “The example of Figure~\ref{fig:fig2pair} is a non-bijective writing”
- Kind: `:redundant`

OLD:

```tex
The example of Figure~\ref{fig:fig2pair} is a non-bijective writing and
therefore lies outside the family classified above; what it shares with the
family is the elementary write, not membership.
```

NEW: CUT

Rationale: The object's non-bijectivity and exclusion from the classified family are already established in `\secref{sec:object}`, so the literature comparison should open directly with the symmetry-group distinction.

## 10. Optional claim-bearing title for the literature section

- Argument-map finding: `:title-matches-claim false`
- Kind: `:title-does-not-state-claim`

OLD:

```tex
\section{Related Constructions}
```

NEW:

```tex
\section{The Permutation-Propagator Family Is Absent from Adjacent Constructions}
```

Rationale: The new title states the section's scoped literature-survey conclusion instead of naming only its topic.
