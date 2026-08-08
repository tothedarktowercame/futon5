# Design for review — a local controller that sustains mixed-phase structure

**Status: DESIGN, not run. 2026-08-08.** Written for external review before implementation.

## The system

A one-dimensional cellular automaton with two coupled layers. The **state layer** holds a
binary value per cell and updates by an elementary rule. The **rule layer** holds that
rule per cell, and is itself rewritten each step by an operator drawn from a fixed
twelve-member vocabulary. Which operator a cell applies is its **operating point**.

The lattice exhibits two absorbing tendencies. Under some settings every cell eventually
stops changing and the sheet is dead. Under others nothing ever stays still and the sheet
is uniformly agitated. Between them is a mixed regime in which regions that have stopped
changing coexist indefinitely with regions that have not, neither eliminating the other.

Today the operating point is set **globally and externally**: two scalars, a precision
`β` and an exploration weight `κ`, chosen by the experimenter and identical for every
cell. The mixed regime occupies a ridge in that plane. **The question is whether each
cell can find and hold its own operating point using only quantities it can compute
locally, with no global view and no externally supplied objective.**

## Why this is now worth attempting

Every previous objective for this system failed in a way we can name.

1. An objective scoring how *structured a fixed 250-step window looks* ranked
   configurations **inverted** against whether they survived at all — its two maxima
   were a configuration that dies and one that lives, ranked in that order.
2. A causal measure (perturb one cell, compare against an unperturbed twin) works, but
   requires forking the run. **No cell can fork the lattice**, so it is unavailable to a
   controller by construction. Measured across a sweep: internal observables predict an
   intrinsic objective at held-out R²=0.73 and this twin-run measure at 0.02.
3. A transfer-entropy measure across the boundary between still and active regions was
   withdrawn for **circularity** — the boundary was defined so as to maximise the
   quantity being measured, and the effect grew monotonically with an arbitrary
   threshold (+0.065 at 0.25 → +0.173 at 0.50).

## The observable

**Interface density.** A cell is *settled* if its state has been unchanged for `w = 15`
consecutive steps. A cell is *interfacial* if its settled-bit differs from either
neighbour's. Interface density `S` is the fraction of interfacial cells.

It escapes each failure above:

- computable from a cell's own 15-step history plus two neighbours' settled bits — no
  twin, no global view;
- the region is defined by *persistence* and the measurement is *adjacency*, so the
  region is not drawn from the quantity being measured;
- **degenerate in neither direction, by construction.** Maximise by rapid alternation
  and nothing stays unchanged for 15 steps, so nothing is settled and there is no
  interface. Maximise by settling everything and there are no active neighbours, so
  again none. It cannot be satisfied by either phase winning.

## Evidence so far

Preregistered before the data existed (`mmca-clj/holes/PREREG-interface-abundance-2026-08-08.edn`,
committed `fc8248d`), then evaluated on the full 7×5 grid — 26 of whose 35 cells had never
been generated. `S` = mean interface density over the last 500 steps of 3000.

The label it was tested against is *(never absorbs) AND (final settled fraction in
[0.02, 0.98])*, which mentions no interface.

| prediction | result |
|---|---|
| S ≥ 0.05 on every mixed-regime cell | **PASS** (min 0.0984) |
| no overlap between groups | **PASS** — mixed [0.0984, 0.1617] vs rest [0.0000, 0.0362] |
| S < 0.02 on every failure-mode cell | **FAIL** — two cells at 0.036, 0.025 |
| corr(S, activity) < 0 across cells | **FAIL** — +0.001, i.e. orthogonal, not anti-correlated |

Top-14 by `S` contains 14/14 of the mixed-regime cells. The two P1 violations sit 3–4×
below the mixed-regime floor, so the threshold was miscalibrated rather than the
separation being poor; per the preregistration's no-rescue clause the threshold was not
adjusted afterwards.

Two further observations, from single runs:

- **Spatially**, within one run, interface density and activity correlate at **−0.969**
  across 25 vertical bands. Interface peaks where activity is 0.27; activity peaks where
  interface is 0.009. It is close to the inverse of an activity detector.
- **Temporally**, the instantaneous *level* is misleading early — the two configurations
  scoring highest in the first 200 steps both die. What discriminates is the **trend**:
  the surviving configuration is the only one whose interface density stops falling and
  recovers. The controller's target should therefore be the derivative, not the level.

## The proposed experiment

Each cell selects its operating point from the twelve-member vocabulary at each step,
minimising an expected-free-energy objective whose preference term is over **local
interface density in its own neighbourhood**, not over any global or externally supplied
quantity. Concretely this adds an `:interface` channel to the existing predictive model
alongside rule-change rate, activity and diversity, and a preference target in `C`.

Two design choices follow from the evidence rather than from taste:

- the preference is over the **trend** in local interface density, not its level;
- the target is a **band**, not a maximum — a cell that has driven local interface as high
  as possible has almost certainly fragmented the domains that make it meaningful.

**Primary outcome.** Starting from initial conditions under which the matched *global*
setting collapses to an absorbing state, does the locally controlled lattice instead
reach and hold the mixed regime for 3000 steps?

**Controls, each targeting a specific way this could be fake:**

1. **Blind control** — identical adoption rate and identical vocabulary, but the choice
   ignores the observable. If local control does not beat this, the observable is not
   doing the work and the result is about churn.
2. **Stale-observable control** — the cell reads interface density from a snapshot frozen
   at the start rather than the current field, with everything else matched. This is the
   only control that isolates *currency* from correlation, and it is the one that carried
   the analogous result elsewhere in this project.
3. **Best-global control** — the best fixed (β, κ) from the grid, run at the same seeds.
   Local control should at minimum match it, and the interesting claim is that it holds
   the regime from initial conditions where the global setting does not.
4. **Fragmentation check** — report the distribution of settled-domain *widths*, not only
   the interface count. A controller that satisfies its preference by shredding the field
   into single-cell stripes has gamed the measure, and the domain-width distribution is
   what exposes it.

**Falsification.** If (1) matches local control, the observable is inert. If (2) matches
it, what matters is the spatial statistics of the neighbourhood and not the currency of
the reading. If (4) shows domains collapsing to width ~1 while `S` stays high, the
preference is satisfiable by fragmentation and the band must be re-specified — before any
positive result is reported, not after.

## Questions for the reviewer

1. Is a preference over the *trend* of a local observable coherent within an
   expected-free-energy formulation, or does it require restating as a preference over a
   latent whose dynamics the model already tracks?
2. The observable has an interior optimum. Does that argue for expressing the preference
   as a target distribution over interface density rather than as a scalar target?
3. Controls (1) and (2) are the ones we trust. Is there a way this could pass both and
   still be an artifact?
4. Is there a cheaper decisive experiment than the full lattice run — something that would
   falsify the design before it is built?
