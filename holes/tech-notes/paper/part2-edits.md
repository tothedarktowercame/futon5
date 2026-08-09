# Proposed Part II edits

These are proposals only. The `OLD` blocks reproduce `draft9.tex` verbatim; no
proposal below has been applied to the manuscript.

1. **Violation:** “A larger river spacetime plate.” — `:opaque-compression`

   **OLD**

   ```tex
\caption{\textbf{A larger river spacetime plate.} One pinned representative
   ```

   **NEW**

   ```tex
\caption{\textbf{Phenotype-reading composition sustains structured phenotype.} One pinned representative
   ```

   **Rationale:** Removes the ungrounded comparator “larger” and makes the caption title state the measured claim.

2. **Violation:** “four-candidate, first-match template construction” — `:opaque-compression`

   **OLD**

   ```tex
The next construction crosses that boundary by allowing the update to depend
on the live phenotype. Figure~\ref{fig:river} shows a representative run at
$L=240$: alternating phenotype domains branch, merge and reorganise for the
length of the run rather than resolving to a homogeneous or short-period field.\suppnote{\supptheorytext{Definition~7}{River} \suppfindingtext{7}{The river sustains structured phenotype}}
   ```

   **NEW**

   ```tex
The phenotype-reading step begins from a four-bit context: the local
three-cell phenotype neighbourhood before the update and the cell's newly
computed phenotype bit. From that context it constructs four ordered
candidates: the observed four bits, their full complement, the observed bits
with the second bit flipped, and the observed bits with the first, third and
fourth bits flipped. At each truth-table position it compares the three rule
bits supplied by the left, centre and right cells with the first three bits of
those candidates; the fourth bit of the first match becomes the new rule bit,
and an unmatched position takes the all-zero fallback. We call the composition
of this step with the offset-$+2$ propagator the \emph{river}.
Figure~\ref{fig:river} shows a representative run at $L=240$: alternating
phenotype domains branch, merge and reorganise for the length of the run rather
than resolving to a homogeneous or short-period field, whereas the bare
rotations collapse or cycle.\suppnote{\supptheorytext{Definition~7}{River} \suppfindingtext{7}{The river sustains structured phenotype}}
   ```

   **Rationale:** Defines the candidates, match, output and fallback in body prose—and names the river—before the caption uses any of them.

3. **Violation:** “The river appears in both rows, at $12.97$ with its edge” — `:opaque-compression`

   **OLD**

   ```tex
The river appears in both rows, at $12.97$ with its edge
and $5.51$ without, so the arrow between them is one construction moved by
adding an edge rather than by changing a parameter.
   ```

   **NEW**

   ```tex
The river composition defined in \secref{sec:feedback-construction} appears in
both rows: its reach is $12.97$ when the genotype update reads the live
phenotype and $5.51$ in the matched control without that live edge. The arrow
therefore compares one construction with and without the measured causal
connection.
   ```

   **Rationale:** Resolves the name explicitly and replaces “with its edge” with the concrete intervention being compared.

4. **Violation:** “contains the flip. It cuts the \emph{currency}” — `:opaque-compression`

   **OLD**

   ```tex
The
$\gamma = 0$ endpoint is $1.28$, not the $5.51$ of the ablated control: that
control freezes the phenotype the genotype reads but still presents a field
which, in the perturbed branch, contains the flip. It cuts the \emph{currency}
of the read, not the read. The doubling reported for it is therefore the smaller
part of a coupling effect that spans an order of magnitude.
   ```

   **NEW**

   ```tex
The two frozen conditions capture their reference fields at different times.
For the $\gamma$ dial, one phenotype field is captured before the fork and
supplied unchanged to both branches; at $\gamma=0$, neither branch's genotype
update can read the flipped bit, and reach is $1.28$. The ablated control instead
captures a separate frozen field at the start of each post-fork continuation,
after the perturbation has been applied. Its perturbed branch therefore reads a
static field containing the flip while its unperturbed branch reads a static
field without it, and reach is $5.51$. Thus $5.51$ removes temporal updating but
retains a branch-specific static difference, whereas $1.28$ removes current
phenotype information from the comparison; the full measured span is $1.28$ to
$12.97$.
   ```

   **Rationale:** Capture time and branch sharing directly reconcile the two endpoints without relying on the currency/read antithesis.

5. **Violation:** “remain mobile and propagate damage at effective diversities well above $135$” — `:opaque-compression`

   **OLD**

   ```tex
It establishes existence: fields can
remain mobile and propagate damage at effective diversities well above $135$
without mutation.
   ```

   **NEW (a)—retain the threshold with provenance**

   ```tex
It establishes existence: in the original $76$-configuration survey, measured
reach falls to zero above sustained diversity $135$, and every point in that
range obtains its diversity through replacement mutation or holding. The
conservative runs remain mobile and propagate damage above that observed
cutoff without either mechanism.\suppfinding{12}{Sustained diversity does not determine causal reach; conservative transport breaks the apparent interior optimum}
   ```

   **NEW (b)—rescope without the threshold**

   ```tex
It establishes existence: under the tested width and response horizon,
conservative runs remain mobile and propagate damage across the high-diversity
range without mutation.
   ```

   **Rationale:** Alternative (a) identifies 135 as the original survey's observed zero-reach cutoff; alternative (b) avoids making the number carry an unstated comparison.

6. **Violation:** “What this scale does and does not license needs saying plainly.” — `:belongs-elsewhere`

   **OLD**

   ```tex
What this scale does and does not license needs saying plainly. It separates
systems that propagate a perturbation from those that do not, and that
separation is sharp: frozen rules return exactly zero. It is \emph{not} a
regime coordinate. Wolfram class~III rules bracket the range at both ends---rule
$90$ at $8.00$ and rule $30$ at $37.50$---with the class~IV rules $54$ and
$110$ between them, so a position on this axis does not by itself classify a
system as ordered, complex or chaotic, and we make no such classification. What
we claim is narrower and causal: the live phenotype-to-genotype edge roughly
doubles how far a single-cell perturbation reaches, on a measure that involves
no region-drawing and therefore cannot fail in the way the withdrawn measure of
the Supplement~4 analysis failed. It concerns the phenotype-coupled construction, not the bare operators;
no comparable measurement of the family is attempted here.
   ```

   **NEW**

   Move the following replacement to the end of
   `\section{Feedback Roughly Doubles How Far a Flip Travels}`, immediately
   after the damage-cone paragraph; at the present location, **CUT**.

   ```tex
The reach scale separates propagating from non-propagating responses: frozen
rules return exactly zero. It is not a regime coordinate. Wolfram class~III
rules bracket the measured range---rule $90$ at $8.00$ and rule $30$ at
$37.50$---with the class~IV rules $54$ and $110$ between them, so position on
this axis does not classify a system as ordered, complex or chaotic. What the
comparison supports is narrower: on the same fork-and-flip measure, allowing
the river's genotype update to read the live phenotype changes reach from
$5.51$ to $12.97$. Because the protocol draws no observational region, that
comparison does not depend on selecting a region from the field being measured.
   ```

   **Rationale:** The paragraph calibrates the causal instrument as a whole, so it belongs with that instrument rather than inside the transport-versus-diversity argument.

7. **Violation:** “It concerns the phenotype-coupled construction, not the bare operators;” — `:redundant`

   **OLD**

   ```tex
It concerns the phenotype-coupled construction, not the bare operators;
no comparable measurement of the family is attempted here.
   ```

   **NEW:** CUT. (This is already removed if proposal 6 is accepted.)

   **Rationale:** The Part II opening already states that these results neither classify nor measure the $8!$ family, so repeating the disclaimer adds no scope information.

8. **Violation:** “Part~II identifies, recovered here from a construction sharing none of” — `:belongs-elsewhere`

   **OLD**

   ```tex
What the reading gate
exploits is the causal currency of the phenotype it reads---the coordinate
Part~II identifies, recovered here from a construction sharing none of
Part~II's dials.
   ```

   **NEW**

   ```tex
The live--blind--frozen ordering therefore recurs in a conditional composition
that uses neither the transport-rate dial nor the river's $\gamma$ dial.
   ```

   **Rationale:** States the in-part comparison directly and removes the caption's external vantage on Part II.

## Optional claim-matching title edits

- Part title: replace `Beyond the Permutation Core: Feedback and Causal Propagation`
  with `Causally Current Phenotype Reads Govern Propagation`.
- First section title: replace `A Construction That Reads Its Own Phenotype`
  with `A Phenotype-Reading Composition Sustains Structured Phenotype`.

Both alternatives state the local claim rather than naming only the topic.
