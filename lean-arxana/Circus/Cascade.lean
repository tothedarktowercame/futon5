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

STATUS: all six theorems proved. Revised after codex-13's statement review
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

private inductive Trail (needs : Needs) (root : EntityId) : List EntityId → Prop
  | root : Trail needs root [root]
  | step {x xs y} : Trail needs root (x :: xs) → y ∈ needs x → Trail needs root (y :: x :: xs)

private theorem trail_pulled {needs : Needs} {root : EntityId} {l : List EntityId}
    (h : Trail needs root l) : ∀ k (hk : k < l.length), l[k] ≠ root →
      ∃ hk1 : k + 1 < l.length, l[k] ∈ needs l[k+1] := by
  induction h with
  | root => intro k hk hn; have : k = 0 := by simp at hk; omega
            subst k; simp at hn
  | @step x xs y ht he ih =>
    intro k hk hn
    cases k with
    | zero => exact ⟨by simp, he⟩
    | succ k =>
      have hk' : k < (x :: xs).length := by simpa using hk
      obtain ⟨hb, hm⟩ := ih k hk' (by simpa using hn)
      exact ⟨by simpa using hb, by simpa using hm⟩

private theorem trail_adj {needs : Needs} {root : EntityId} {l : List EntityId}
    (h : Trail needs root l) : ∀ k (hk : k + 1 < l.length), l[k] ∈ needs l[k+1] := by
  induction h with
  | root => intro k hk; simp at hk
  | @step x xs y ht he ih =>
    intro k hk
    cases k with
    | zero => exact he
    | succ k => exact ih k (by simpa using hk)

private theorem chain_trace (needs : Needs) (choose : List EntityId → EntityId)
    (hchoose : ChooseMember choose) (seen : List EntityId) (root : EntityId) :
    ∀ fuel acc x c, Trail needs root (x :: acc) → x ∉ seen →
      chainOf needs choose seen fuel acc x = .chain c →
      Trail needs root c.reverse ∧ ∃ d ds, c.reverse = d :: ds ∧
        (needs d = [] ∨ ∃ y ∈ seen, y ∈ needs d) := by
  intro fuel
  induction fuel with
  | zero => simp [chainOf]
  | succ fuel ih =>
    intro acc x c ht hx hc
    simp only [chainOf] at hc
    split at hc
    · simp_all
    · split at hc
      · contradiction
      · split at hc
        · next hstop =>
          cases hc
          simp only [List.reverse_reverse]
          refine ⟨ht, x, acc, rfl, ?_⟩
          simp only [Bool.or_eq_true, List.isEmpty_iff, List.any_eq_true] at hstop
          rcases hstop with hnil | ⟨y, hy, hys⟩
          · exact Or.inl hnil
          · exact Or.inr ⟨y, by simpa using hys, hy⟩
        · next hgo =>
          have hn : needs x ≠ [] := by intro h; simp [h] at hgo
          have hy := hchoose (needs x) hn
          have hys : choose (needs x) ∉ seen := by
            intro hm
            have : (needs x).any seen.contains = true := List.any_eq_true.mpr ⟨_, hy, by simpa using hm⟩
            simp [this] at hgo
          exact ih (x :: acc) _ c (Trail.step ht hy) hys hc

private theorem index_value (x : EntityId) (l : List EntityId) (hx : x ∈ l) :
    l[l.idxOf x]'(List.idxOf_lt_length_of_mem hx) = x := by
  induction l with
  | nil => simp at hx
  | cons y ys ih =>
    by_cases he : x = y
    · subst y; simp
    · have hm : x ∈ ys := (List.mem_cons.mp hx).resolve_left he
      have hbe : (y == x) = false := by exact beq_eq_false_iff_ne.mpr (Ne.symm he)
      simpa only [List.idxOf_cons, hbe, Bool.false_eq_true, Bool.cond_false, List.getElem_cons_succ] using ih hm

private theorem trail_forward {needs : Needs} {root : EntityId} {l : List EntityId}
    (ht : Trail needs root l) (i : Nat) (hi : i + 1 < l.reverse.length) :
    l.reverse[i+1] ∈ needs l.reverse[i] := by
  have hlen : i + 1 < l.length := by simpa using hi
  have he := trail_adj ht (l.length - 2 - i) (by omega)
  simpa only [List.getElem_reverse,
    show l.length - 1 - (i + 1) = l.length - 2 - i by omega,
    show l.length - 2 - i + 1 = l.length - 1 - i by omega] using he

private theorem suffix_cycle {needs : Needs} {l : List EntityId} {x : EntityId}
    (hx : x ∈ l)
    (hedge : ∀ i (hi : i < l.length), (l ++ [x])[i+1]'(by simp; omega) ∈ needs l[i]) :
    IsCycle needs (l.drop (l.idxOf x)) := by
  have hidx := List.idxOf_lt_length_of_mem hx
  have hpos : 0 < (l.drop (l.idxOf x)).length := by simp; omega
  constructor
  · intro he; simp [he] at hpos
  · intro i hi
    have hlen : i < l.length - l.idxOf x := by simpa using hi
    have hp : l.idxOf x + i < l.length := by omega
    have hh := hedge (l.idxOf x + i) hp
    by_cases hnext : i + 1 < (l.drop (l.idxOf x)).length
    · simp only [Nat.mod_eq_of_lt hnext]
      simp only [List.getElem_drop]
      rw [List.getElem_append_left (show l.idxOf x + i + 1 < l.length by simp at hnext; omega)] at hh
      simpa only [Nat.add_assoc] using hh
    · have he : i + 1 = (l.drop (l.idxOf x)).length := by omega
      simp only [he, Nat.mod_self]
      simp only [List.getElem_drop, Nat.add_zero]
      rw [index_value x l hx]
      have hend : l.idxOf x + i + 1 = l.length := by simp at he; omega
      simp only [hend, List.getElem_append_right (show l.length ≤ l.length by omega)] at hh
      simpa using hh

private theorem chain_cycle (needs : Needs) (choose : List EntityId → EntityId)
    (hchoose : ChooseMember choose) (seen : List EntityId) (root : EntityId) :
    ∀ fuel acc x c, Trail needs root (x :: acc) →
      chainOf needs choose seen fuel acc x = .cycle c → IsCycle needs c := by
  intro fuel
  induction fuel with
  | zero => simp [chainOf]
  | succ fuel ih =>
    intro acc x c ht hc
    simp only [chainOf] at hc
    split at hc
    · contradiction
    · split at hc
      · next hrepeat =>
        cases hc
        have hx : x ∈ acc.reverse := by simpa using hrepeat
        apply suffix_cycle hx
        intro i hi
        have he := trail_forward ht i (by simpa using hi)
        simp only [List.reverse_cons] at he
        rw [List.getElem_append_left hi] at he
        exact he
      · split at hc
        · contradiction
        · next hgo =>
          have hn : needs x ≠ [] := by intro he; simp [he] at hgo
          exact ih (x :: acc) _ c (Trail.step ht (hchoose _ hn)) hc

private theorem vote_cycles (needs : Needs) (choose : List EntityId → EntityId)
    (hchoose : ChooseMember choose) (fuel : Nat) :
    ∀ turns budget seen os, ∀ e ∈ (cascadeVote needs choose fuel budget turns seen os).2,
      IsCycle needs e.2 := by
  intro turns
  induction turns with
  | zero => intro budget seen os; cases budget <;> simp [cascadeVote]
  | succ turns ih =>
    intro budget seen os
    cases budget with
    | zero => simp [cascadeVote]
    | succ budget =>
      simp only [cascadeVote]
      split
      · simp
      · simp
      · next a x a' l' rest hs =>
        split
        · next c hc =>
          intro e he
          rcases List.mem_cons.mp he with rfl | he
          · exact chain_cycle needs choose hchoose seen x fuel [] x c Trail.root hc
          · exact ih _ _ _ e he
        · exact ih _ _ _
        · split <;> exact ih _ _ _

private def Pulled (needs : Needs) (rows : List Row) : Prop :=
  ∀ k (hk : k < rows.length), ∀ x, rows[k].2.2 = some x →
    ∃ hk1 : k + 1 < rows.length,
      rows[k+1].1 = rows[k].1 ∧ rows[k].2.1 ∈ needs rows[k+1].2.1
      ∧ (rows[k+1].2.2 = some x ∨ (rows[k+1].2.1 = x ∧ rows[k+1].2.2 = none))

private theorem pulled_append {needs : Needs} {as bs : List Row}
    (ha : Pulled needs as) (hb : Pulled needs bs) : Pulled needs (as ++ bs) := by
  intro k hk x hx
  by_cases hka : k < as.length
  · rw [List.getElem_append_left hka] at hx
    obtain ⟨hn, hh⟩ := ha k hka x hx
    refine ⟨by simp only [List.length_append]; omega, ?_⟩
    simpa only [List.getElem_append_left hka, List.getElem_append_left hn] using hh
  · have hkb : k - as.length < bs.length := by simp only [List.length_append] at hk; omega
    rw [List.getElem_append_right (by omega)] at hx
    obtain ⟨hn, hh⟩ := hb (k - as.length) hkb x hx
    refine ⟨by simp only [List.length_append]; omega, ?_⟩
    simpa only [List.getElem_append_right (show as.length ≤ k by omega),
      List.getElem_append_right (show as.length ≤ k + 1 by omega),
      show k + 1 - as.length = k - as.length + 1 by omega] using hh

private theorem pulled_place {needs : Needs} {root : EntityId} {c : List EntityId}
    (a : Agent) (ht : Trail needs root c.reverse) : Pulled needs (placeChain a root c) := by
  intro k hk x hx
  have hk' : k < c.reverse.length := by simpa [placeChain] using hk
  simp only [placeChain, List.getElem_map] at hx
  have hn : c.reverse[k] ≠ root := by intro he; simp [he] at hx
  have he : x = root := by
    rw [if_neg hn] at hx
    exact (Option.some.inj hx).symm
  subst x
  obtain ⟨hnext, hedge⟩ := trail_pulled ht k hk' hn
  refine ⟨by simpa [placeChain] using hnext, ?_⟩
  simp only [placeChain, List.getElem_map]
  refine ⟨trivial, hedge, ?_⟩
  by_cases he : c.reverse[k+1] = root
  · right; exact ⟨he, if_pos he⟩
  · left; exact if_neg he

private theorem vote_pulled (needs : Needs) (choose : List EntityId → EntityId)
    (hchoose : ChooseMember choose) (fuel : Nat) :
    ∀ turns budget seen os, Pulled needs (cascadeVote needs choose fuel budget turns seen os).1 := by
  intro turns
  induction turns with
  | zero => intro budget seen os; cases budget <;> simp [cascadeVote, Pulled]
  | succ turns ih =>
    intro budget seen os
    cases budget with
    | zero => simp [cascadeVote, Pulled]
    | succ budget =>
      simp only [cascadeVote]
      split
      · simp [Pulled]
      · simp [Pulled]
      · next a x a' l' rest hs =>
        have hx := sweep_fresh seen os a x ((a', l') :: rest) hs
        split
        · apply pulled_append (as := [(a, x, none)])
          · simp [Pulled]
          · exact ih _ _ _
        · exact ih _ _ _
        · next chain hc =>
          split
          · apply pulled_append
            · exact pulled_place a (chain_trace needs choose hchoose seen x fuel [] x chain Trail.root hx hc).1
            · exact ih _ _ _
          · exact ih _ _ _

private def Good (needs : Needs) (ex : List Exception) (seen : List EntityId) (x : EntityId) : Prop :=
  needs x = [] ∨ (∃ y ∈ seen, y ∈ needs x) ∨ x ∈ ex.map (·.1)

private def Ordered (needs : Needs) (ex : List Exception) : List EntityId → List EntityId → Prop
  | _, [] => True
  | seen, x :: xs => Good needs ex seen x ∧ Ordered needs ex (x :: seen) xs

private theorem ordered_mono {needs : Needs} {ex ex' : List Exception} {seen seen' xs : List EntityId}
    (he : ∀ e ∈ ex, e ∈ ex') (hs : ∀ x ∈ seen, x ∈ seen')
    (h : Ordered needs ex seen xs) : Ordered needs ex' seen' xs := by
  induction xs generalizing seen seen' with
  | nil => trivial
  | cons x xs ih =>
    obtain ⟨hg, ht⟩ := h
    refine ⟨?_, ih (by intro y hy; simp only [List.mem_cons] at *; rcases hy with rfl | hy; exact Or.inl rfl; exact Or.inr (hs y hy)) ht⟩
    rcases hg with hn | ⟨y, hy, hyn⟩ | hm
    · exact Or.inl hn
    · exact Or.inr (Or.inl ⟨y, hs y hy, hyn⟩)
    · right; right
      obtain ⟨e, hem, hex⟩ := List.mem_map.mp hm
      exact List.mem_map.mpr ⟨e, he e hem, hex⟩

private theorem ordered_append {needs : Needs} {ex : List Exception} {seen as bs : List EntityId}
    (ha : Ordered needs ex seen as) (hb : Ordered needs ex (as ++ seen) bs) :
    Ordered needs ex seen (as ++ bs) := by
  induction as generalizing seen with
  | nil => simpa using hb
  | cons x xs ih =>
    refine ⟨ha.1, ih ha.2 ?_⟩
    apply ordered_mono (ex := ex) (seen := (x :: xs) ++ seen) (by simp) _ hb
    intro y hy
    simpa only [List.mem_append, List.mem_cons, or_assoc, or_left_comm, or_comm] using hy

private theorem trail_ordered {needs : Needs} {root : EntityId} {l : List EntityId}
    (ht : Trail needs root l) : ∀ seen ex,
      (∀ d ds, l = d :: ds → Good needs ex seen d) → Ordered needs ex seen l := by
  induction ht with
  | root => intro seen ex hg; exact ⟨hg _ _ rfl, trivial⟩
  | @step x xs y ht hedge ih =>
    intro seen ex hg
    refine ⟨hg _ _ rfl, ih (y :: seen) ex ?_⟩
    intro d ds he
    cases he
    exact Or.inr (Or.inl ⟨y, by simp, hedge⟩)

private theorem vote_ordered (needs : Needs) (choose : List EntityId → EntityId)
    (hchoose : ChooseMember choose) (fuel : Nat) :
    ∀ turns budget seen os,
      let r := cascadeVote needs choose fuel budget turns seen os
      Ordered needs r.2 seen (itemsOf r.1) := by
  intro turns
  induction turns with
  | zero => intro budget seen os; cases budget <;> simp [cascadeVote, itemsOf, Ordered]
  | succ turns ih =>
    intro budget seen os
    cases budget with
    | zero => simp [cascadeVote, itemsOf, Ordered]
    | succ budget =>
      simp only [cascadeVote]
      split
      · trivial
      · trivial
      · next a x a' l' rest hs =>
        split
        · next c hc =>
          refine ⟨Or.inr (Or.inr (by simp)), ?_⟩
          exact ordered_mono (by intro e he; exact List.mem_cons_of_mem _ he) (by simp) (ih _ _ _)
        · exact ih _ _ _
        · next chain hc =>
          split
          · have ht := chain_trace needs choose hchoose seen x fuel [] x chain Trail.root
                (sweep_fresh seen os a x ((a', l') :: rest) hs) hc
            have hmap : itemsOf (placeChain a x chain) = chain.reverse := by
              simp [itemsOf, placeChain, List.map_map, Function.comp_def]
            change Ordered needs _ seen (itemsOf (placeChain a x chain ++ _))
            simp only [itemsOf, List.map_append] at *
            rw [hmap]
            apply ordered_append
            · apply trail_ordered ht.1
              intro d ds he
              obtain ⟨z, zs, hz, hready⟩ := ht.2
              rw [he] at hz
              cases hz
              rcases hready with hn | hw
              · exact Or.inl hn
              · exact Or.inr (Or.inl hw)
            · exact ordered_mono (by simp) (by simp) (ih _ _ _)
          · exact ih _ _ _

private theorem ordered_strong {needs : Needs} {ex : List Exception} {seen xs : List EntityId}
    (h : Ordered needs ex seen xs) : ∀ k (hk : k < xs.length),
      Good needs ex (xs.take k ++ seen) xs[k] := by
  induction xs generalizing seen with
  | nil => simp
  | cons x xs ih =>
    intro k hk
    cases k with
    | zero => simpa using h.1
    | succ k =>
      have hg := ih h.2 k (by simpa using hk)
      rcases hg with hn | ⟨y, hy, hyn⟩ | hm
      · exact Or.inl hn
      · right; left; refine ⟨y, ?_, hyn⟩
        simpa only [List.take_succ_cons, List.mem_append, List.mem_cons, or_assoc, or_left_comm, or_comm] using hy
      · exact Or.inr (Or.inr hm)

private theorem chain_fresh (needs : Needs) (choose : List EntityId → EntityId) (seen : List EntityId) :
    ∀ fuel acc x c, acc.Nodup → (∀ y ∈ acc, y ∉ seen) →
      chainOf needs choose seen fuel acc x = .chain c → c.Nodup ∧ ∀ y ∈ c, y ∉ seen := by
  intro fuel
  induction fuel with
  | zero => simp [chainOf]
  | succ fuel ih =>
    intro acc x c hn hd hc
    simp only [chainOf] at hc
    split at hc
    · cases hc
      exact ⟨hn.perm (List.reverse_perm acc).symm, by simpa using hd⟩
    · split at hc
      · contradiction
      · next hx =>
        have hxn : x ∉ acc := by simpa using hx
        have hxs : x ∉ seen := by simpa using ‹¬seen.contains x = true›
        have hn' : (x :: acc).Nodup := List.nodup_cons.mpr ⟨hxn, hn⟩
        have hd' : ∀ y ∈ x :: acc, y ∉ seen := by
          intro y hy
          rcases List.mem_cons.mp hy with rfl | hy
          · exact hxs
          · exact hd y hy
        split at hc
        · cases hc
          exact ⟨hn'.perm (List.reverse_perm (x :: acc)).symm, by
            intro y hy
            exact hd' y (by simpa only [List.mem_reverse] using hy)⟩
        · exact ih _ _ _ hn' hd' hc

private theorem vote_fresh (needs : Needs) (choose : List EntityId → EntityId) (fuel : Nat) :
    ∀ turns budget seen os,
      (itemsOf (cascadeVote needs choose fuel budget turns seen os).1).Nodup ∧
      ∀ y ∈ itemsOf (cascadeVote needs choose fuel budget turns seen os).1, y ∉ seen := by
  intro turns
  induction turns with
  | zero => intro budget seen os; cases budget <;> simp [cascadeVote, itemsOf]
  | succ turns ih =>
    intro budget seen os
    cases budget with
    | zero => simp [cascadeVote, itemsOf]
    | succ budget =>
      simp only [cascadeVote]
      split
      · simp [itemsOf]
      · simp [itemsOf]
      · next a x a' l' rest hs =>
        have hx := sweep_fresh seen os a x ((a', l') :: rest) hs
        split
        · have hr := ih budget (x :: seen) (rest ++ [(a', l')])
          simp only [itemsOf, List.map_cons]
          constructor
          · exact List.nodup_cons.mpr ⟨by
              intro hm
              exact hr.2 x hm (by simp), hr.1⟩
          · intro y hy
            rcases List.mem_cons.mp hy with rfl | hy
            · exact hx
            · exact fun hm => hr.2 y hy (by simp [hm])
        · exact ih _ _ _
        · next chain hc =>
          have hch := chain_fresh needs choose seen fuel [] x chain (by simp) (by simp) hc
          split
          · have hr := ih (budget + 1 - chain.length) (chain ++ seen) (rest ++ [(a', l')])
            have he : itemsOf (placeChain a x chain) = chain.reverse := by
              simp [itemsOf, placeChain, List.map_map, Function.comp_def]
            change (itemsOf (placeChain a x chain ++ _)).Nodup ∧ _
            simp only [itemsOf, List.map_append] at *
            rw [show List.map (fun r : Row => r.2.1) (placeChain a x chain) = chain.reverse from he]
            constructor
            · apply List.nodup_append.mpr
              refine ⟨hch.1.perm (List.reverse_perm chain).symm, hr.1, ?_⟩
              intro y hy z hz heq
              subst z
              exact hr.2 y hz (List.mem_append_left _ (by simpa using hy))
            · intro y hy
              rcases List.mem_append.mp hy with hy | hy
              · exact hch.2 y (by simpa using hy)
              · exact fun hm => hr.2 y hy (List.mem_append_right _ hm)
          · exact ih _ _ _

private theorem vote_budget (needs : Needs) (choose : List EntityId → EntityId) (fuel : Nat) :
    ∀ turns budget seen os, (cascadeVote needs choose fuel budget turns seen os).1.length ≤ budget := by
  intro turns
  induction turns with
  | zero => intro budget seen os; cases budget <;> simp [cascadeVote]
  | succ turns ih =>
    intro budget seen os
    cases budget with
    | zero => simp [cascadeVote]
    | succ budget =>
      simp only [cascadeVote]
      split
      · simp
      · simp
      · next a x a' l' rest hs =>
        split
        · simpa using Nat.succ_le_succ (ih budget (x :: seen) (rest ++ [(a', l')]))
        · exact ih _ _ _
        · next chain hc =>
          split
          · next hfit =>
            have h := ih (budget + 1 - chain.length) (chain ++ seen) (rest ++ [(a', l')])
            simp only [List.length_append, placeChain, List.length_map, List.length_reverse]
            omega
          · exact ih _ _ _

private theorem no_needs_eq (choose : List EntityId → EntityId) (fuel : Nat) (hfuel : 0 < fuel) :
    ∀ budget seen os,
      (cascadeVote (fun _ => []) choose fuel budget budget seen os).1.map (fun r => (r.1, r.2.1)) =
        partyVote budget seen os := by
  intro budget
  induction budget with
  | zero => intro seen os; simp [cascadeVote, partyVote]
  | succ budget ih =>
    intro seen os
    cases hf : fuel with
    | zero => omega
    | succ f =>
      cases hs : sweep seen os with
      | none => simp [cascadeVote, partyVote, hs]
      | some p =>
        obtain ⟨a, x, rest⟩ := p
        cases rest with
        | nil => simp [cascadeVote, partyVote, hs]
        | cons q rest =>
          obtain ⟨a', l'⟩ := q
          have hx := sweep_fresh seen os a x ((a', l') :: rest) hs
          simp [cascadeVote, partyVote, hs, chainOf, hx, placeChain]
          simpa [hf] using ih (x :: seen) (rest ++ [(a', l')])

/-- MAIN: the cascade vote's output is a strengthened cascade, for any
member-choosing `choose`, starting from nothing shown. -/
theorem cascadeVote_strong (needs : Needs) (choose : List EntityId → EntityId)
    (hchoose : ChooseMember choose) (fuel budget turns : Nat) (os : Orderings) :
    let r := cascadeVote needs choose fuel budget turns [] os
    CascadeStrong needs r.2 (itemsOf r.1) := by
  intro r k hk _
  have h := ordered_strong (vote_ordered needs choose hchoose fuel turns budget [] os) k hk
  rcases h with hn | ⟨y, hy, hyn⟩ | hm
  · exact Or.inr (Or.inl hn)
  · left
    simp only [List.append_nil] at hy
    obtain ⟨j, hj, he⟩ := List.mem_iff_getElem.mp hy
    have hjk : j < k := by simp only [List.length_take] at hj; omega
    refine ⟨j, hjk, ?_⟩
    have hjr : j < (itemsOf r.1).length := by omega
    rw [getElem!_pos (itemsOf r.1) j hjr]
    rw [← he] at hyn
    simpa only [List.getElem_take] using hyn
  · exact Or.inr (Or.inr hm)

/-- Every recorded exception is a real cycle (no fabricated exceptions). -/
theorem cascadeVote_exceptions_real (needs : Needs) (choose : List EntityId → EntityId)
    (hchoose : ChooseMember choose) (fuel budget turns : Nat) (os : Orderings) :
    ∀ e ∈ (cascadeVote needs choose fuel budget turns [] os).2, IsCycle needs e.2 := by
  exact vote_cycles needs choose hchoose fuel turns budget [] os

/-- No item is shown twice. -/
theorem cascadeVote_nodup (needs : Needs) (choose : List EntityId → EntityId)
    (fuel budget turns : Nat) (os : Orderings) :
    (itemsOf (cascadeVote needs choose fuel budget turns [] os).1).Nodup := by
  exact (vote_fresh needs choose fuel turns budget [] os).1

/-- The budget is respected. -/
theorem cascadeVote_budget (needs : Needs) (choose : List EntityId → EntityId)
    (fuel budget turns : Nat) (os : Orderings) :
    (cascadeVote needs choose fuel budget turns [] os).1.length ≤ budget := by
  exact vote_budget needs choose fuel turns budget [] os

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
  exact vote_pulled needs choose hchoose fuel turns budget [] os

/-- With no needs at all (and positive fuel), the cascade vote is the old
party vote. -/
theorem cascadeVote_no_needs (choose : List EntityId → EntityId)
    (fuel budget : Nat) (os : Orderings) (hfuel : 0 < fuel) :
    (cascadeVote (fun _ => []) choose fuel budget budget [] os).1.map (fun r => (r.1, r.2.1)) =
      partyVote budget [] os := by
  exact no_needs_eq choose fuel hfuel budget [] os

end Circus.Cascade
