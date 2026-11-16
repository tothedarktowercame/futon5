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
`X` alone and records it as an exception.

The prerequisite chosen at each step is a parameter `choose`; the producer's
rule (earliest position in any character's list, then id) is one instance.
The theorems hold for every `choose` that returns a member of the list.

STATUS: statements only (`sorry`), for review against the producer before
proving (Joe delegated the statement read to codex-13, 2026-11-16).
-/

namespace Circus.Cascade

open Arxana Circus.Retro

/-- Each item's supplied needs. -/
abbrev Needs := EntityId → List EntityId

/-- `NeedsSupplied`: the supplied lists say exactly what the basis says each
shown item needs, over the inventory. -/
def NeedsSupplied (inp : Inputs) (needs : Needs) (shown : List EntityId) : Prop :=
  ∀ x ∈ shown, ∀ j, j ∈ needs x ↔ (inp.items.any (·.id == j) = true ∧ inp.needs x j = true)

/-- The strengthened cascade on a shown list of items: every entry after the
first either needs some earlier entry, or needs nothing. `exceptions` are the
cycle roots the producer records (`cascade_exceptions`). -/
def CascadeStrong (needs : Needs) (exceptions : List EntityId) (es : List EntityId) : Prop :=
  ∀ k (hk : k < es.length), 0 < k →
    (∃ j, j < k ∧ es[j]! ∈ needs es[k]) ∨ needs es[k] = [] ∨ es[k] ∈ exceptions

/-- The prerequisite chain for root `x`, as the producer walks it: from `x`,
while the current item is not shown, add it; stop when it needs nothing or
one of its needs is shown; otherwise step to `choose` of its needs. Returns
`some chain` (root first, deepest last) or `none` on a cycle or when `fuel`
runs out (treated as a cycle). -/
def chainOf (needs : Needs) (choose : List EntityId → EntityId)
    (seen : List EntityId) : Nat → List EntityId → EntityId → Option (List EntityId)
  | 0, _, _ => none
  | fuel + 1, acc, x =>
      if seen.contains x then some acc.reverse
      else if acc.contains x then none
      else
        let acc' := x :: acc
        let pre := needs x
        if pre.isEmpty || pre.any seen.contains then some acc'.reverse
        else chainOf needs choose seen fuel acc' (choose pre)

/-- One output row: (character, item, pulled for). -/
abbrev Row := Agent × EntityId × Option EntityId

/-- Place a chain deepest-first; every item but the root is pulled for it. -/
def placeChain (a : Agent) (root : EntityId) (chain : List EntityId) : List Row :=
  chain.reverse.map fun i => (a, i, if i = root then none else some root)

/-- The cascade vote. `budget` is the remaining capacity, `turns` bounds the
number of round-robin turns (each turn consumes one fresh pick, placed or
deferred), `fuel` bounds a chain walk. Returns the rows and the exceptions. -/
def cascadeVote (needs : Needs) (choose : List EntityId → EntityId) (fuel : Nat) :
    Nat → Nat → List EntityId → Orderings → List Row × List EntityId
  | 0, _, _, _ => ([], [])
  | _, 0, _, _ => ([], [])
  | budget + 1, turns + 1, seen, os =>
      match sweep seen os with
      | none => ([], [])
      | some (_, _, []) => ([], [])
      | some (a, x, (a', l') :: rest) =>
          let os' := rest ++ [(a', l')]
          match chainOf needs choose seen fuel [] x with
          | none =>
              -- cycle: place the root alone, record it
              let (rows, ex) := cascadeVote needs choose fuel budget turns (x :: seen) os'
              ((a, x, none) :: rows, x :: ex)
          | some chain =>
              if chain.length ≤ budget + 1 then
                let (rows, ex) := cascadeVote needs choose fuel (budget + 1 - chain.length)
                    turns (chain ++ seen) os'
                (placeChain a x chain ++ rows, ex)
              else
                -- deferred: the turn is consumed, nothing placed
                cascadeVote needs choose fuel (budget + 1) turns (x :: seen) os'

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

/-- A pulled row sits in a complete run immediately before its root, and each
item of the run is needed by the next (the last by the root). -/
theorem cascadeVote_pulled_chain (needs : Needs) (choose : List EntityId → EntityId)
    (hchoose : ChooseMember choose) (fuel budget turns : Nat) (os : Orderings) :
    let rows := (cascadeVote needs choose fuel budget turns [] os).1
    ∀ k (hk : k + 1 < rows.length), ∀ x, rows[k].2.2 = some x →
      rows[k].2.1 ∈ needs rows[k+1].2.1 ∧
      (rows[k+1].2.2 = some x ∨ (rows[k+1].2.1 = x ∧ rows[k+1].2.2 = none)) := by
  sorry

/-- With no needs at all, the cascade vote is the old party vote. -/
theorem cascadeVote_no_needs (choose : List EntityId → EntityId)
    (fuel budget : Nat) (os : Orderings) (hfuel : 0 < fuel) :
    (cascadeVote (fun _ => []) choose fuel budget budget [] os).1.map (fun r => (r.1, r.2.1)) =
      partyVote budget [] os := by
  sorry

end Circus.Cascade
