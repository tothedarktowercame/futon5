import Circus.Basic
import Circus.Retro

/-!
# The strengthened cascade (E-stands-alone)

Joe, 2026-11-16: "we should go with the strengthened cascade" — the shown top
ten is a cascade with needs REQUIRED: every entry after the first either needs
some earlier shown entry, or needs nothing at all. The weak form
(`Circus.Loop.top_ten_is_a_cascade`, `Basic.Cascade`) accepted any nonempty
reason, so its `needs` half never had to hold.

The party vote that meets it (futon3c `scripts/giraffe_party.py`
`party_vote`, commit bcd007992): the round robin is unchanged, but when a
character's next fresh pick `X` needs something and none of its needs is
shown yet, a chain of prerequisites is placed first (`chainOf`), deepest
first, each marked as pulled for `X`. A chain that does not fit the remaining
budget defers `X` (the turn is consumed, nothing is placed). A cycle places
`X` alone and records it, with the cycle, as an exception. A walk that runs
out of fuel defers `X`; it is never reported as a cycle.

The prerequisite chosen at each step is a parameter `choose`; the producer's
rule (earliest position in any character's list, then id) is one instance.
The theorems hold for every `choose` that returns a member of the list.

STATUS: statements only (`sorry`). Revised after codex-13's statement review
(~/notes/codex-13/cascade-statement-review.md, 2026-11-16): deferral no longer
marks the pick shown (the strong theorem was false), fuel exhaustion is not a
cycle, exceptions carry their cycle (`cascadeVote_exceptions_real`),
NeedsSupplied keeps needs outside the inventory, and the pulled-chain theorem
covers the last row and the character. Exact producer equivalence (rows,
deferral records, preference order) is not stated here; the conformance checks
party_turns / needs_supplied / cascade_strong cover the serialized output.
-/

namespace Circus.Cascade

open Arxana Circus.Retro

/-- Each item's supplied needs. -/
abbrev Needs := EntityId → List EntityId

/-- `NeedsSupplied`: the supplied lists say exactly what the basis says each
shown item needs, including needs on items outside the inventory (the producer
and the `needs_supplied` check keep those, with a null state). -/
def NeedsSupplied (inp : Inputs) (needs : Needs) (shown : List EntityId) : Prop :=
  ∀ x ∈ shown, ∀ j, j ∈ needs x ↔ inp.needs x j = true

/-- A recorded exception: the root placed alone, and the cycle found. -/
abbrev Exception := EntityId × List EntityId

/-- A real cycle: nonempty, each item needs the next, the last needs the first. -/
def IsCycle (needs : Needs) (c : List EntityId) : Prop :=
  c ≠ [] ∧ ∀ i (hi : i < c.length), c[(i + 1) % c.length]'(Nat.mod_lt _ (by omega)) ∈ needs c[i]

/-- The strengthened cascade on a shown list of items: every entry after the
first either needs some earlier entry, or needs nothing, or is the root of a
recorded exception. -/
def CascadeStrong (needs : Needs) (exceptions : List Exception) (es : List EntityId) : Prop :=
  ∀ k (hk : k < es.length), 0 < k →
    (∃ j, j < k ∧ es[j]! ∈ needs es[k]) ∨ needs es[k] = [] ∨ es[k] ∈ exceptions.map (·.1)

/-- Result of walking a prerequisite chain. -/
inductive Walk where
  | chain (items : List EntityId)   -- root first, deepest last
  | cycle (c : List EntityId)       -- an item repeated: the cycle from its first occurrence
  | outOfFuel                       -- the bound ran out; never reported as a cycle
deriving Repr, DecidableEq

/-- The prerequisite chain for root `x`, as the producer walks it
(giraffe_party.py party_vote): while the current item is not shown, add it;
stop when it needs nothing or one of its needs is shown; otherwise step to
`choose` of its needs. A repeat is a cycle; exhausting `fuel` is reported
separately (codex-13 review 2026-11-16: fuel must not fabricate cycles). -/
def chainOf (needs : Needs) (choose : List EntityId → EntityId)
    (seen : List EntityId) : Nat → List EntityId → EntityId → Walk
  | 0, _, _ => .outOfFuel
  | fuel + 1, acc, x =>
      if seen.contains x then .chain acc.reverse
      else if acc.contains x then
        let ch := acc.reverse
        .cycle (ch.drop (ch.idxOf x))
      else
        let acc' := x :: acc
        let pre := needs x
        if pre.isEmpty || pre.any seen.contains then .chain acc'.reverse
        else chainOf needs choose seen fuel acc' (choose pre)

/-- One output row: (character, item, pulled for). -/
abbrev Row := Agent × EntityId × Option EntityId

/-- Place a chain deepest-first; every item but the root is pulled for it. -/
def placeChain (a : Agent) (root : EntityId) (chain : List EntityId) : List Row :=
  chain.reverse.map fun i => (a, i, if i = root then none else some root)

/-- The cascade vote. `budget` is the remaining capacity, `turns` bounds the
number of round-robin turns (each turn consumes one fresh pick, placed or
deferred; the sum of the character list lengths always suffices), `fuel`
bounds a chain walk. Returns the rows and the exceptions. A deferred pick
(its chain does not fit, or the walk ran out of fuel) consumes the turn and is
NOT marked shown (codex-13 review: producer giraffe_party.py:312-314). -/
def cascadeVote (needs : Needs) (choose : List EntityId → EntityId) (fuel : Nat) :
    Nat → Nat → List EntityId → Orderings → List Row × List Exception
  | 0, _, _, _ => ([], [])
  | _, 0, _, _ => ([], [])
  | budget + 1, turns + 1, seen, os =>
      match sweep seen os with
      | none => ([], [])
      | some (_, _, []) => ([], [])
      | some (a, x, (a', l') :: rest) =>
          let os' := rest ++ [(a', l')]
          match chainOf needs choose seen fuel [] x with
          | .cycle c =>
              -- place the root alone, record it with its cycle
              let (rows, ex) := cascadeVote needs choose fuel budget turns (x :: seen) os'
              ((a, x, none) :: rows, (x, c) :: ex)
          | .outOfFuel =>
              cascadeVote needs choose fuel (budget + 1) turns seen os'
          | .chain chain =>
              if chain.length ≤ budget + 1 then
                let (rows, ex) := cascadeVote needs choose fuel (budget + 1 - chain.length)
                    turns (chain ++ seen) os'
                (placeChain a x chain ++ rows, ex)
              else
                -- deferred: the turn is consumed, nothing placed, x not shown
                cascadeVote needs choose fuel (budget + 1) turns seen os'

/-- `choose` picks a member of a nonempty list. -/
def ChooseMember (choose : List EntityId → EntityId) : Prop :=
  ∀ l, l ≠ [] → choose l ∈ l

/-- The shown items of a vote. -/
def itemsOf (rows : List Row) : List EntityId := rows.map (·.2.1)

/-- MAIN: the cascade vote's output is a strengthened cascade, for any
member-choosing `choose`, starting from nothing shown. -/
theorem cascadeVote_strong (needs : Needs) (choose : List EntityId → EntityId)
    (hchoose : ChooseMember choose) (fuel budget turns : Nat) (os : Orderings) :
    let r := cascadeVote needs choose fuel budget turns [] os
    CascadeStrong needs r.2 (itemsOf r.1) := by
  sorry

/-- Every recorded exception is a real cycle (no fabricated exceptions). -/
theorem cascadeVote_exceptions_real (needs : Needs) (choose : List EntityId → EntityId)
    (hchoose : ChooseMember choose) (fuel budget turns : Nat) (os : Orderings) :
    ∀ e ∈ (cascadeVote needs choose fuel budget turns [] os).2, IsCycle needs e.2 := by
  sorry

/-- No item is shown twice. -/
theorem cascadeVote_nodup (needs : Needs) (choose : List EntityId → EntityId)
    (fuel budget turns : Nat) (os : Orderings) :
    (itemsOf (cascadeVote needs choose fuel budget turns [] os).1).Nodup := by
  sorry

/-- The budget is respected. -/
theorem cascadeVote_budget (needs : Needs) (choose : List EntityId → EntityId)
    (fuel budget turns : Nat) (os : Orderings) :
    (cascadeVote needs choose fuel budget turns [] os).1.length ≤ budget := by
  sorry

/-- Every pulled row is followed by a row of the same character whose item
needs it, and that row is either pulled for the same root or is the root
itself, unpulled. So pulled rows form complete runs ending at their root. -/
theorem cascadeVote_pulled_chain (needs : Needs) (choose : List EntityId → EntityId)
    (hchoose : ChooseMember choose) (fuel budget turns : Nat) (os : Orderings) :
    let rows := (cascadeVote needs choose fuel budget turns [] os).1
    ∀ k (hk : k < rows.length), ∀ x, rows[k].2.2 = some x →
      ∃ hk1 : k + 1 < rows.length,
        rows[k+1].1 = rows[k].1 ∧
        rows[k].2.1 ∈ needs rows[k+1].2.1 ∧
        (rows[k+1].2.2 = some x ∨ (rows[k+1].2.1 = x ∧ rows[k+1].2.2 = none)) := by
  sorry

/-- With no needs at all (and positive fuel), the cascade vote is the old
party vote. -/
theorem cascadeVote_no_needs (choose : List EntityId → EntityId)
    (fuel budget : Nat) (os : Orderings) (hfuel : 0 < fuel) :
    (cascadeVote (fun _ => []) choose fuel budget budget [] os).1.map (fun r => (r.1, r.2.1)) =
      partyVote budget [] os := by
  sorry

end Circus.Cascade
