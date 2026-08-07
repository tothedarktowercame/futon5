# Wanted: a policy-specific epistemic quantity for a three-layer cellular automaton

**Self-contained.** No prior context with this codebase is assumed. Standard cellular-automata
and active-inference terminology. Written 2026-08-04.

**The ask:** propose, implement and *measure* a local quantity `X(i, π)` that predicts the
consequences of policy π at cell *i*. §5 states a trap that kills the obvious candidates, and
§6 is the falsifier. A well-argued "no such quantity exists, and here is why" is a good answer.

---

## 1. The system

A ring of *N* cells. Each cell carries:

- **rule** — an 8-bit elementary cellular automaton rule;
- **state** — one bit;
- **policy layer**: a permutation σ of the eight truth-table positions, drawn from a vocabulary
  of 12.

Per step: each cell applies its own rule to its three-cell state neighbourhood (a normal ECA
step, but with a different rule per cell); then each cell's rule is transformed by its σ.

**The rule transform.** One position *k* is chosen uniformly at random. If `bit[σ(k)] == bit[k]`
then `bit[k]` is flipped; otherwise nothing happens. So σ determines both how fast a rule
changes and which rules are stationary under it. Two exact consequences, both verified:

- `rate(σ) = 0.5 + fix(σ)/16`, where `fix(σ)` counts fixed points of σ. **The change rate is a
  function of the fixed-point count alone**, and takes only eight achievable values.
- A rule is stationary under σ iff `bit[σ(k)] ≠ bit[k]` for every *k* — a proper 2-colouring
  around each cycle, which exists iff every cycle of σ is even. So **stationary rules exist only
  when fix(σ) = 0**, i.e. only at rate exactly 0.5000.

## 2. The decision we are trying to inform

Each cell chooses, every step, among exactly three policies:

- **hold** — keep my own σ
- **adopt-left** — take my left neighbour's σ
- **adopt-right** — take my right neighbour's σ

The choice is made by minimising a score over the *candidate σ*. The score currently has a
term penalising the predicted rule-change rate against a target, and a term penalising the
predicted entropy of the next local observation. It has **no epistemic / information-gain
term**, which is the gap we are trying to fill.

Note carefully: **a policy changes which σ transforms the rule. It does not change the rule.**
The consequences of a policy are therefore one step removed, and that indirection is the crux
of this problem.

## 3. What we already have, and why it does not fit

For the *rule-blending* operator (a separate mechanism where a cell's rule is blended with
its neighbours' by bitwise agreement) we validated a first-order quantity:

    X_blend(i) = Σ_n w(n) · [ rule_i(n) ≠ blended_i(n) ]

with *n* ranging over the eight three-cell patterns, `w(n)` the locally observed frequency of
pattern *n* over an 11-cell window, and `blended_i` the rule the cell would have after blending.
In words: **how often would adopting this actually change what I do, given the patterns I
actually encounter.**

Measured against damage from *actually adopting the blend* (perturb, run a twin from the same
seed, count differing state cells at horizon 60): **r = +0.22** on one 720-cell sample and
**+0.14** on an independent one, with monotone terciles in both. The control — correlation with
damage from an *unrelated* single-bit flip — is null (+0.03, −0.07). So it is a real signal,
and a weak one.

**It cannot be used for the decision in §2.** `X_blend(i)` has no σ argument: it is one scalar
per cell. Subtracting `κ·X_blend(i)` from all three candidate scores subtracts the same constant
from each, so `argmin` is unchanged for every κ. Its spread across the candidates being compared
is exactly zero.

## 4. What is wanted

A quantity `X(i, π)` defined for each of the three policies, that predicts

    Y(i, π) = divergence at horizon T between the trajectory from state S,
              and the trajectory from S with cell i's σ replaced by the σ that
              policy π would adopt

— both trajectories advanced from the same state with the same seed, so the only difference is
the policy taken. Y is directly measurable and the harness for it exists.

`X` must be **local** (readable from cell *i*'s own neighbourhood), **cheap** (no forward
simulation per candidate per step), and **actually vary across the three policies**.

## 5. The trap, which kills the obvious candidates

The score already contains a term that is a function of `rate(σ_candidate)`. And by §1,
`rate(σ) = 0.5 + fix(σ)/16` — **rate is a function of σ alone**.

So any proposed `X(i, π)` that reduces to a function of `rate(σ_π)` is **redundant**: it adds
a rescaled copy of a term already present, and cannot change the ordering in any way the
existing term could not. This kills, for example:

- "expected Hamming change to my rule under the candidate σ" — that is exactly `rate(σ)`;
- anything averaging over rules or over patterns uniformly, since the σ-dependence collapses
  back to the fixed-point count.

**A useful `X` must depend jointly on σ AND on the cell's current rule AND on which truth-table
entries are live locally.** The σ-dependence has to survive conditioning on the actual rule and
the actual local pattern distribution. If it does not, it is `rate` wearing a hat.

One candidate shape, offered only so the trap is concrete — not as a recommendation:
σ can flip position *k* only when `bit[σ(k)] == bit[k]`, which for a *given* rule picks out a
specific set of positions; weighting those by `w(n)` gives a quantity depending on σ, the rule
and the local patterns together. Whether that survives §6 is exactly the question.

## 6. Falsifier, and what a good answer looks like

Measure `r(X, Y)` per policy and pooled, on at least two disjoint seed sets, and report both.
Also report:

- **the control**: `r(X, Y_unrelated)` where `Y_unrelated` is damage from a perturbation the
  policy does not cause. A quantity that correlates with everything is measuring activity, not
  the policy.
- **the redundancy check**: `r(X, rate(σ_π))`. If that is near 1, §5 applies and the quantity is
  the existing term in disguise, whatever its correlation with Y.
- **the spread check**: the standard deviation of X *across the three candidates within a cell*,
  averaged over cells. If that is ~0, X cannot influence the decision no matter how well it
  correlates with Y — this is precisely how the previous candidate failed.

`r ≈ 0`, or a redundancy check near 1, is a real and useful result. **A reasoned argument that
no such local quantity can exist — because the policy's effect is mediated by a stochastic
transform and is therefore not predictable from the current state — would be at least as
valuable as a working quantity**, and should be made if that is what the measurements show.
