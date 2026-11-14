import Circus.Basic
/-!
# Retro-specification of two pieces of running code (M-repl-circus, step 2)

Joe, 2026-11-14: much of the Circus already exists in Clojure, so first write
a retro-specification of existing code, then derive the main theorems. This
module mirrors, as Lean definitions, and proves the lemmas of:

(a) the War Machine handoff contract and exchange — futon2
    `src/futon2/aif/handoff_contract.clj`: `contract`, `exchange`, `outcome`;
(b) the party view's round robin — futon3c `scripts/giraffe_party.py`
    `party_vote`, per `~/notes/claude-13/party-contract.md`.
-/
namespace Circus.Retro

open Arxana

/-! ## (a) The War Machine handoff contract (futon2 `handoff_contract.clj`)

The Clojure `contract` throws `:contract-invalid` on a value without a
nonempty id, positive version and named, positively-versioned validator;
here `contract` returns `none` instead (the throw is the same fact). -/

structure HandoffContractValue where
  id : String
  version : Nat
  response : String
  validatorId : String
  validatorVersion : Nat
deriving DecidableEq, Repr

def validContract (c : HandoffContractValue) : Bool :=
  !c.id.isEmpty && 0 < c.version && !c.response.isEmpty
    && !c.validatorId.isEmpty && 0 < c.validatorVersion

def contract (c : HandoffContractValue) : Option HandoffContractValue :=
  if validContract c then some c else none

theorem contract_invalid_is_none {c : HandoffContractValue}
    (h : validContract c = false) : contract c = none := by
  simp [contract, h]

theorem contract_valid_is_some {c : HandoffContractValue}
    (h : validContract c = true) : contract c = some c := by
  simp [contract, h]

/-- `exchange` (Clojure): one handoff through the common contact boundary.
`contactAvailable` false is the `:not-contacted` / `contact-unavailable`
shape; an answer that never came back done is a typed refusal; a decoded
value the consumer's validator rejects is a refusal; only a validated value
is `validated`. Exactly one status per run, by construction. -/
inductive ExchangeStatus (α : Type)
  | notContacted (reason : String)
  | refused (reason : String)
  | validated (value : α)
deriving Repr

def exchange {α : Type} (contactAvailable : Bool) (answer : Option α)
    (valid : α → Bool) : ExchangeStatus α :=
  if !contactAvailable then .notContacted "contact-unavailable"
  else
    match answer with
    | none => .refused "job-not-done"
    | some v => if valid v then .validated v else .refused "response-invalid"

/-- What a consumer may use: only a validated value. -/
def consumed {α : Type} : ExchangeStatus α → Option α
  | .validated v => some v
  | _ => none

/-- Every exchange ends in exactly one typed outcome: whatever is consumed
from an exchange is a value the consumer's validator accepted. -/
theorem exchange_consumed_is_validated {α : Type}
    (contactAvailable : Bool) (answer : Option α) (valid : α → Bool)
    {v : α} (h : consumed (exchange contactAvailable answer valid) = some v) :
    valid v = true := by
  cases contactAvailable with
  | false => simp [exchange, consumed] at h
  | true =>
    cases answer with
    | none => simp [exchange, consumed] at h
    | some w =>
      by_cases hw : valid w = true
      · rw [exchange, hw] at h
        have hwv : w = v := Option.some.inj h
        subst hwv
        exact hw
      · rw [exchange, if_neg hw] at h
        exact absurd h (by simp [consumed])

/-- A refused exchange is never consumed (futon2: only `:validated` carries
`:value`). -/
theorem refused_not_consumed {α : Type} (r : String) :
    consumed (ExchangeStatus.refused (α := α) r) = none := rfl

/-- A not-contacted exchange is never consumed. -/
theorem notContacted_not_consumed {α : Type} (r : String) :
    consumed (ExchangeStatus.notContacted (α := α) r) = none := rfl

/-- Only a validated exchange is consumed. -/
theorem consumed_only_validated {α : Type} (s : ExchangeStatus α)
    (h : consumed s ≠ none) :
    ∃ v, s = .validated v := by
  cases s with
  | notContacted _ => exact absurd h (by simp [consumed])
  | refused _ => exact absurd h (by simp [consumed])
  | validated v => exact ⟨v, rfl⟩

/-- `outcome` (Clojure): a typed record of an exchange's state; the Clojure
throws `:outcome-state-invalid` on a state outside the seven; here `none`. -/
inductive OutcomeState
  | notContacted | contacted | answered | refused | validated | published
  | consumed
deriving DecidableEq, Repr

def validOutcomeState : OutcomeState → Bool
  | .notContacted | .contacted | .answered | .refused | .validated
  | .published | .consumed => true

def outcome (c : HandoffContractValue) (requestId : String)
    (state : OutcomeState) (detail : String) :
    Option (HandoffContractValue × String × String × String) :=
  if validContract c && validOutcomeState state then
    some (c, requestId, stateName state, detail)
  else none
where stateName : OutcomeState → String
  | .notContacted => "not-contacted" | .contacted => "contacted"
  | .answered => "answered" | .refused => "refused"
  | .validated => "validated" | .published => "published"
  | .consumed => "consumed"

/-- A well-formed outcome always names its contract; an invalid contract or
state yields nothing. -/
theorem outcome_valid_is_some {c : HandoffContractValue} (requestId : String)
    (state : OutcomeState) (detail : String) (hc : validContract c = true) :
    ∃ r, outcome c requestId state detail = some r := by
  cases state <;>
    simp only [outcome, hc, validOutcomeState, Bool.true_and, if_true] <;>
    exact ⟨_, rfl⟩

theorem outcome_invalid_is_none {c : HandoffContractValue} {requestId state detail}
    (hc : validContract c = false) :
    outcome c requestId state detail = none := by
  unfold outcome
  rw [hc]
  simp

/-! ## (b) The party view's round robin (`giraffe_party.py` `party_vote`)

The script: ids in order; a pointer cycles; each turn pops the head of the
character's list, skipping items already chosen (a duplicate does not burn
the turn — it keeps drawing from the same character until a fresh item or an
empty list); a character whose list is exhausted is skipped; stop at the
budget. Modelled with `seen` (already-chosen items) threaded explicitly. -/

abbrev Orderings := List (Agent × List EntityId)

/-- `drawFresh`: the first fresh item of a list, with the rest after it. -/
def drawFresh (seen : List EntityId) : List EntityId → Option (EntityId × List EntityId)
  | [] => none
  | i :: rest =>
      if seen.contains i then drawFresh seen rest else some (i, rest)

/-- `sweep`: the characters act in turn; a character with no fresh item is
skipped; the contributing character is returned at the head of the round so
the caller can rotate it to the back (the script's `ptr + 1`). -/
def sweep (seen : List EntityId) : Orderings → Option (Agent × EntityId × Orderings)
  | [] => none
  | (a, l) :: rest =>
      match drawFresh seen l with
      | some (i, l') => some (a, i, (a, l') :: rest)
      | none => sweep seen rest

/-- `partyVote`: round robin with budget `count`, skipping chosen items. -/
def partyVote (count : Nat) (seen : List EntityId) (os : Orderings) :
    List (Agent × EntityId) :=
  match count with
  | 0 => []
  | n + 1 =>
      match sweep seen os with
      | none => []
      | some (a, i, (a', l') :: rest) =>
          (a, i) :: partyVote n (i :: seen) (rest ++ [(a', l')])
      | some (_, _, []) => []   -- unreachable: sweep returns a nonempty list

/-! ### Lemmas -/

/-- `drawFresh` only returns items that were not chosen yet. -/
theorem drawFresh_fresh : ∀ (seen l : List EntityId) (i : EntityId) (l' : List EntityId),
    drawFresh seen l = some (i, l') → i ∉ seen := by
  intro seen l
  induction l with
  | nil => intro i l' h; exact absurd h (by simp [drawFresh])
  | cons x xs ih =>
    intro i l' h
    by_cases hc : seen.contains x
    · rw [drawFresh, if_pos hc] at h; exact ih i l' h
    · rw [drawFresh, if_neg hc] at h
      simp only [Option.some.injEq, Prod.mk.injEq] at h
      obtain ⟨e1, e2⟩ := h
      subst e1
      intro hmem
      rw [List.contains_iff_mem] at hc
      exact hc hmem

/-- `sweep` returns an item that was not chosen yet (each character
contributes its first fresh item). -/
theorem sweep_fresh : ∀ (seen : List EntityId) (os : Orderings) (a : Agent)
    (i : EntityId) (os' : Orderings), sweep seen os = some (a, i, os') → i ∉ seen := by
  intro seen os
  induction os with
  | nil => intro a i os' h; exact absurd h (by simp [sweep])
  | cons p ps ih =>
    intro a i os' h
    rw [sweep] at h
    split at h
    · next j lj hp =>
      simp only [Option.some.injEq, Prod.mk.injEq] at h
      obtain ⟨_, e2, _⟩ := h
      subst e2
      exact drawFresh_fresh seen p.2 j lj hp
    · exact ih a i os' h

/-- `sweep` picks the head character of its returned round (the contributor
acts in turn: everything before it was skipped for having no fresh item). -/
theorem sweep_head_contributes : ∀ (seen : List EntityId) (os : Orderings) (a : Agent)
    (i : EntityId) (os' : Orderings), sweep seen os = some (a, i, os') →
    ∃ l, os'.head? = some (a, l) := by
  intro seen os
  induction os with
  | nil => intro a i os' h; exact absurd h (by simp [sweep])
  | cons p ps ih =>
    intro a i os' h
    rw [sweep] at h
    split at h
    · next j lj hp =>
      simp only [Option.some.injEq, Prod.mk.injEq] at h
      obtain ⟨e1, e2, e3⟩ := h
      subst e3
      subst e1
      exact ⟨lj, rfl⟩
    · exact ih a i os' h

/-- The party list never repeats an item and never picks an already-chosen
item (party.json: `skip items already chosen`). -/
theorem partyVote_fresh_and_nodup :
    ∀ (count : Nat) (seen : List EntityId) (os : Orderings),
      (∀ x ∈ partyVote count seen os, x.2 ∉ seen)
      ∧ ((partyVote count seen os).map Prod.snd).Nodup := by
  intro count
  induction count with
  | zero => intro _ _; simp [partyVote]
  | succ n ih =>
    intro seen os
    cases hs : sweep seen os with
    | none => simp [partyVote, hs]
    | some p =>
      obtain ⟨a, i, os''⟩ := p
      cases hos : os'' with
      | nil => simp [partyVote, hs, hos]
      | cons q rest =>
        have hfresh : i ∉ seen := sweep_fresh seen os a i os'' hs
        have hrec := ih (i :: seen) (rest ++ [q])
        constructor
        · intro x hx
          rw [partyVote, hs, hos] at hx
          cases hx with
          | head => exact hfresh
          | tail _ hmem =>
            intro hmemSeen
            exact hrec.1 x hmem (List.mem_cons_of_mem _ hmemSeen)
        · rw [partyVote, hs, hos, List.map_cons]
          refine List.nodup_cons.mpr ⟨?_, hrec.2⟩
          intro hmem
          obtain ⟨x, hx, hxe⟩ := List.mem_map.mp hmem
          have hxseen := hrec.1 x hx
          rw [hxe] at hxseen
          exact hxseen (List.mem_cons_self ..)

end Circus.Retro
