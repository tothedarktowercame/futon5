import Circus.Retro
/-!
# The concrete loop (M-repl-circus, step 3)

`step` and `rebuild`, left opaque in `Circus/Basic.lean` while the
statements were read, made concrete here:

* `rebuild cs inputs` is the party view: `Circus.Retro.partyVote` (the
  retro-specification of futon3c `scripts/giraffe_party.py` `party_vote`)
  round-robins over the characters' orderings with the top-ten budget; each
  shown entry carries its contributing character's reason for the item.
* `step` updates `Inputs` per event and recomputes `shown` by `rebuild`.

The statements `incremental_eq_rebuilt` and `quiescent_shows_nothing_new`
move here from `Basic.lean` (they are about the concrete loop) and are
proved. Where a statement needed an extra hypothesis to be true, the
hypothesis is added and the reason is in its docstring.
-/
namespace Circus

open Arxana

/-! ## `rebuild`: the party view -/

/-- The top-ten budget (M-repl-circus §DERIVE: the operator's Mojira view). -/
def budgetTen : Nat := 10

/-- The reason a shown entry carries: the terms of the first character's own
entry for that item (its accepted benefit lines, the operator's
introductions/word rules — whatever vocabulary its `order` used), credited
to the character that contributed the item this round. -/
def reasonFor : List Character → Inputs → Agent → EntityId → Reason
  | [], _, a, _ => ⟨[], a⟩
  | c :: cs', inp, a, i =>
      match (c.order inp).find? (fun e => e.item == i) with
      | some e => ⟨e.reason.terms, a⟩
      | none => reasonFor cs' inp a i

/-- Turn a party round into shown entries: the first entry has the top
priority, each later one a step less. -/
def entriesFrom : Nat → (Agent → EntityId → Reason) → List (Agent × EntityId) → List Entry
  | _, _, [] => []
  | p, r, (a, i) :: rest => ⟨i, p, r a i⟩ :: entriesFrom (p - 1) r rest

/-- The shown view: the party round over the characters' orderings, entries
carrying their contributor's reason. -/
def rebuild (cs : List Character) (inp : Inputs) : List Entry :=
  entriesFrom budgetTen (reasonFor cs inp)
    (Retro.partyVote budgetTen [] (cs.map fun c => (c.who, (c.order inp).map (·.item))))

/-! ## `step`: events to inputs, inputs to shown -/

def bump (f : EntityId → Nat) (i : EntityId) : EntityId → Nat :=
  fun x => if x = i then f i + 1 else f x

/-- One event's effect on the loop's inputs.

* `turn _ .settled`: the 2026-11-14 activation amendment — a settled turn
  bumps the activation of every focused item.
* other `turn` events write records (Basic's `wiring`), not inputs.
* `docChange i _`: the document changed, so the last reading of `i` is
  withdrawn — a fresh reading is needed (the giraffe firing rule's cause).
* `readingLanded i v`: the fresh reading of version `v` is on the table.
* `focus`: 猴's weights. `tick`: nothing moves.
-/
def applyEvent (inp : Inputs) : Event → Inputs
  | .turn _ .settled =>
      { inp with activation := inp.focus.foldl (fun acc p => bump acc p.1) inp.activation }
  | .turn _ _ => inp
  | .docChange i _ => { inp with readings := inp.readings.filter (fun p => p.1 != i) }
  | .readingLanded i v =>
      { inp with readings := (i, v) :: inp.readings.filter (fun p => p.1 != i) }
  | .focus w => { inp with focus := w }
  | .tick => inp

/-- One step of the loop: advance the clock, apply the event to the inputs,
recompute what is shown from the new inputs by `rebuild`, and record that an
input-changing event moved the change time. -/
def step (cs : List Character) (s : State) (e : Event) : State :=
  let inp := applyEvent s.inputs e
  { s with
      now := s.now + 1
      inputs := inp
      shown := rebuild cs inp
      lastInputChange := match e with
        | .turn _ _ | .tick => s.lastInputChange
        | .docChange .. | .readingLanded .. | .focus .. => s.lastInputChange + 1
      lastRecompute := s.now + 1 }

/-! ## The two step statements, now proved

`incremental_eq_rebuilt` is true by construction: `step` recomputes `shown`
from the very inputs it just wrote, so the incremental and rebuilt views
cannot drift. That it is `rfl` is the point — it pins the implementation to
the recompute-at-every-step discipline the design names. -/

/-- Recomputing after each event agrees with rebuilding from the inputs:
what is shown after a step is exactly `rebuild` of the inputs after that
step. -/
theorem incremental_eq_rebuilt (cs : List Character) (s : State) (e : Event) :
    (step cs s e).shown = rebuild cs (step cs s e).inputs := rfl

/-- With no events but ticks, nothing new is shown.

NEEDS THE HYPOTHESIS `hs : s.shown = rebuild cs s.inputs` (added in step 3;
the draft had none): the statement is about the loop's invariant — a state
the loop produced shows `rebuild` of its inputs, so a tick that changes no
input shows the same list. For an arbitrary hand-built state it is FALSE,
and the witness below shows a state whose `shown` was never built by
`rebuild` failing it. -/
theorem quiescent_shows_nothing_new (cs : List Character) (s : State)
    (hs : s.shown = rebuild cs s.inputs) :
    (step cs s .tick).shown = s.shown := by
  simp [step, applyEvent, hs]

/-- The hypothesis of `quiescent_shows_nothing_new` has content: this state
was not built by the loop (`shown` empty while a character would rank an
item), and a tick does NOT leave it unchanged. -/
private def exChar : Character :=
  ⟨.thor, fun _ =>
    [{ item := 0, priority := 0, reason := { terms := ["t"], chosenBy := .thor } }]⟩

private def exState : State :=
  ⟨0, ⟨[], [], fun _ => 0, fun _ _ => false, fun _ => [], []⟩, [], 0, 0⟩
example : (step [exChar] exState .tick).shown.length = 1 := by native_decide

example : ¬ (step [exChar] exState .tick).shown = exState.shown := by
  intro h
  have h1 : (step [exChar] exState .tick).shown.length = 1 := by native_decide
  rw [h] at h1
  exact absurd h1 (by decide)

/-! ## `top_ten_is_a_cascade` (statement carried over from `Basic.lean`;
proof is a later step) -/

/-- Every entry after the first either needs an earlier entry, or names why
it stands alone (a nonempty reason). -/
theorem top_ten_is_a_cascade (cs : List Character) (s : State)
    (h : s.shown = rebuild cs s.inputs) :
    Cascade s.inputs s.shown := by
  sorry

end Circus
