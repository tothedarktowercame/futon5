import ArxanaCore.Store
/-!
# The REPL Circus as a logic model: datatypes and handoffs (M-repl-circus, VERIFY)

Draft by claude-13, 2026-11-14, at Joe's request: "before we build anything
concrete we need to build a logic model, maybe in Lean. What we put into
VERIFY was a "reading" essentially, but we haven't yet verified the design."
Statements only; every proof is `sorry` until Joe has read the statements
(statement registry policy: statements are approved before they are proved).

Sources: futon3c `holes/missions/M-repl-circus.md` (§DERIVE, its 2026-11-14
amendment on activation, the pot and mana), `holes/labs/M-repl-circus/
MAP-animals-2026-11-11.md` (each animal's trigger, input, output),
`~/notes/claude-13/party-contract.md` (the Gauntlet characters), futon2
`src/futon2/aif/handoff_contract.clj` (the War Machine's handoff contract,
modelled in `Contract`/`Exchange` below so the same validator applies).
-/
namespace Circus
open Arxana

/-! ## Items and the turn chain -/

inductive ItemKind | mission | excursion | ticket
deriving DecidableEq, Repr

/-- A substrate entity: one per document (the 2026-11-12/13 dedup). -/
structure Item where
  id : EntityId
  kind : ItemKind
  externalId : String
  open_ : Bool
deriving DecidableEq, Repr

/-- The turn lifecycle (futon3c `xiang/turn_service.clj`). -/
inductive TurnEvent | recorded | reading | ported | aligned | settled
deriving DecidableEq, Repr

def TurnEvent.rank : TurnEvent → Nat
  | .recorded => 0 | .reading => 1 | .ported => 2 | .aligned => 3 | .settled => 4

abbrev TurnId := Nat
abbrev Time := Nat

/-! ## The animals' records (what each hands on) -/

/-- Who writes or reads a record. -/
inductive Agent
  | lu        -- 驴: pattern candidates for the operator turn
  | xiang     -- 象: the turn reading (acts, intents, obligations)
  | dog       -- 狗: memory recall before the turn, use check after it
  | ma        -- 馬: reply-side alignment and tensions
  | hou       -- 猴: the operator focus
  | giraffe   -- readings, pass, ranking
  | thor | merlin | questor | thyra  -- the Gauntlet characters
  | operator  -- Joe
  | warMachine
deriving DecidableEq, Repr

/-- The kinds of record that change hands. -/
inductive RecordKind
  | luCandidates | xiangReading | dogRecall | dogUse
  | maAlignment | maTension | houFocus
  | docChange | giraffeReading | activation | pass | ordering | ranking
  | benefitLine | serves | needs | mention
deriving DecidableEq, Repr

/-- A handoff: WRITER produces KIND at EVENT (or outside the turn chain),
READER consumes it. -/
structure Handoff where
  writer : Agent
  kind : RecordKind
  reader : Agent
  trigger : Option TurnEvent
deriving DecidableEq, Repr

/-- The wiring as designed (DERIVE + MAP note). 猴's focus has no writer in
code today (MAP: typed absence); the model includes it as designed. -/
def wiring : List Handoff :=
  [ ⟨.lu, .luCandidates, .dog, some .recorded⟩
  , ⟨.lu, .luCandidates, .ma, some .ported⟩
  , ⟨.xiang, .xiangReading, .ma, some .ported⟩
  , ⟨.xiang, .xiangReading, .hou, some .settled⟩
  , ⟨.dog, .dogRecall, .operator, some .recorded⟩
  , ⟨.dog, .dogUse, .giraffe, some .settled⟩
  , ⟨.ma, .maAlignment, .hou, some .aligned⟩
  , ⟨.ma, .maTension, .giraffe, some .aligned⟩
  , ⟨.xiang, .mention, .giraffe, some .settled⟩
  , ⟨.hou, .houFocus, .giraffe, some .settled⟩
  , ⟨.operator, .docChange, .giraffe, none⟩
  , ⟨.giraffe, .giraffeReading, .giraffe, none⟩
  , ⟨.giraffe, .activation, .thor, none⟩, ⟨.giraffe, .activation, .merlin, none⟩
  , ⟨.giraffe, .activation, .questor, none⟩, ⟨.giraffe, .activation, .thyra, none⟩
  , ⟨.thor, .ordering, .giraffe, none⟩, ⟨.merlin, .ordering, .giraffe, none⟩
  , ⟨.questor, .ordering, .giraffe, none⟩, ⟨.thyra, .ordering, .giraffe, none⟩
  , ⟨.giraffe, .ranking, .operator, none⟩
  , ⟨.giraffe, .pass, .warMachine, none⟩ ]

/-! ## Handoff contracts (the War Machine's, reused) -/

/-- futon2 `handoff_contract.clj` `contract`: an id, a version, a response
shape and a named, versioned validator. -/
structure Contract (α : Type) where
  id : String
  version : Nat
  validator : String × Nat
  valid : α → Bool

/-- futon2 `exchange`: every handoff ends in one of these typed outcomes. -/
inductive Exchange (α : Type)
  | notContacted (reason : String)
  | refused (reason : String)
  | accepted (value : α)

/-- A reader consumes only accepted values, and only ones its contract
validates. -/
def consumable {α : Type} (c : Contract α) : Exchange α → Option α
  | .accepted v => if c.valid v then some v else none
  | _ => none

/-! ## The GCC as a graph -/

/-- Every record lives in the GCC (an Arxana store) and points at items or
other records; reads go through it, never through evidence bodies. -/
structure GCC where
  records : List Record
  nodes : List EntityId
  edge : EntityId → EntityId → Bool

/-- A path of at most `n` hops from `a` to `b`. -/
def reachableWithin (g : GCC) : Nat → EntityId → EntityId → Prop
  | 0, a, b => a = b
  | n + 1, a, b => a = b ∨ ∃ c, c ∈ g.nodes ∧ (g.edge a c = true ∨ g.edge c a = true)
                      ∧ reachableWithin g n c b

/-! ## Benefit (virtual), orderings, ranking -/

/-- A benefit line: real but not actual (Deleuze's virtual); an item that
serves it would actualise it. -/
structure BenefitLine where
  code : String
  acceptedByOperator : Bool
deriving DecidableEq, Repr

/-- The vocabulary a reason may use: terms the operator made or accepted
(introductions, word rules, accepted benefit lines) and the characters'
names. Explainable = every term of every reason is in it. -/
structure Vocabulary where
  terms : List String

structure Reason where
  terms : List String
  chosenBy : Agent
deriving Repr

def Reason.explainable (v : Vocabulary) (r : Reason) : Prop :=
  ∀ t ∈ r.terms, t ∈ v.terms

structure Entry where
  item : EntityId
  priority : Nat
  reason : Reason
deriving Repr

/-- The inputs a ranking is computed from at a moment. -/
structure Inputs where
  items : List Item
  readings : List (EntityId × Nat)        -- item, document version read
  activation : EntityId → Nat
  needs : EntityId → EntityId → Bool
  serves : EntityId → List BenefitLine
  focus : List (EntityId × Nat)            -- 猴: weight per item

/-- A character orders open items by its own rule (unblocking, strategy,
quick, harm reduction). -/
structure Character where
  who : Agent
  order : Inputs → List Entry

/-- The party view: round robin over the four characters, skipping items
already chosen (party-contract.md). -/
opaque party : List Character → Inputs → List Entry := fun _ _ => []

/-! ## The state over time -/

inductive Event
  | turn (t : TurnId) (e : TurnEvent)
  | docChange (i : EntityId) (version : Nat)
  | readingLanded (i : EntityId) (version : Nat)
  | focus (weights : List (EntityId × Nat))
  | tick

structure State where
  now : Time
  inputs : Inputs
  shown : List Entry          -- what the operator sees (Mojira)
  lastInputChange : Time
  lastRecompute : Time

/-- One step of the loop; left abstract (the implementation is what is being
specified). -/
opaque step : State → Event → State := fun s _ => s
opaque rebuild : List Character → Inputs → List Entry := fun _ _ => []

/-! ## Statements for Joe to read (proofs to follow once approved) -/

/-- Every record kind some agent reads has a writer in the wiring. -/
theorem every_read_has_a_writer :
    ∀ h ∈ wiring, ∃ w ∈ wiring, w.kind = h.kind ∧ w.reader = h.reader := by
  sorry

/-- Turn-chain handoffs run forward: a reader triggered at an event reads
only records written at or before it. -/
theorem handoffs_follow_the_chain : True := by
  sorry

/-- Key lemma (Joe, 2026-11-14): the GCC is connected and navigable from
point to point in 5 or fewer hops. -/
theorem gcc_navigable_in_five (g : GCC) :
    ∀ a ∈ g.nodes, ∀ b ∈ g.nodes, reachableWithin g 5 a b := by
  sorry

/-- Recomputing after each event agrees with rebuilding from the inputs. -/
theorem incremental_eq_rebuilt (cs : List Character) (s : State) (e : Event) :
    (step s e).shown = rebuild cs (step s e).inputs := by
  sorry

/-- With no events but ticks, nothing new is shown. -/
theorem quiescent_shows_nothing_new (s : State) :
    (step s .tick).shown = s.shown := by
  sorry

/-- PRIMARY (Joe, 2026-11-14): "Giraffe always gives an up to date ranking of
at least the top ten missions by priority order that is recognisable to the
operator"; recognisable = explainable. Within bound B of the last input
change, what is shown is current, has at least ten entries when ten open
items exist, is in priority order, and every reason is explainable in the
operator's vocabulary. -/
theorem primary_up_to_date_top_ten (cs : List Character) (v : Vocabulary)
    (B : Nat) (s : State) (hB : s.now ≥ s.lastInputChange + B) :
    s.shown = rebuild cs s.inputs
    ∧ ((s.inputs.items.filter (·.open_)).length ≥ 10 → s.shown.length ≥ 10)
    ∧ (∀ i j (hij : i < j) (h₁ : j < s.shown.length),
         (s.shown.get ⟨i, Nat.lt_trans hij h₁⟩).priority ≥ (s.shown.get ⟨j, h₁⟩).priority)
    ∧ (∀ e ∈ s.shown, e.reason.explainable v) := by
  sorry

/-- The top ten is a cascade in its own right (Joe: "not just presented as a
ranking but as an up-to-date cascade"): every entry after the first is
linked to an earlier one by `needs`, or names why it stands alone. -/
theorem top_ten_is_a_cascade (s : State) : True := by
  sorry

/-- Focus coherence (Joe, after Salingaros): focus over a few related items
is better than over more unrelated ones. Stated once a coherence measure is
chosen; placeholder. -/
theorem focus_coherence : True := by
  sorry

end Circus
