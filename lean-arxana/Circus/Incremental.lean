import Circus.Loop

/-!
# Incremental entries (CL-56c)
The old Character.order API evaluates whole orderings. EntryCharacter supplies
per-item evaluation and adapts it to that API. IncState stores finite computed
entry lists, not duplicate inputs. This is a contract for local producers, not
a claim that every existing character is local. Detection scans the population;
we prove selective evaluation, not a sublinear detector or wall-clock bound.
-/
namespace Circus.Incremental
open Arxana

/-- Own inputs, including declared needs edges, not other items' readings.
A readiness/cone producer using neighbours must expose their derived summary as
this item's input or use a larger dependency cone; it is not automatically local. -/
structure ItemInputs where
  item : List Item
  readings : List (EntityId × Nat)
  activation : Nat
  needs : List EntityId
  serves : List BenefitLine
  focus : List (EntityId × Nat)
  mana : Nat
  deriving DecidableEq

def itemInputs (inp : Inputs) (i : EntityId) : ItemInputs :=
  ⟨inp.items.filter (fun it => it.id == i), inp.readings.filter (fun p => p.1 == i),
    inp.activation i, (inp.items.map Item.id).filter (inp.needs i),
    inp.serves i, inp.focus.filter (fun p => p.1 == i), inp.mana⟩

/-- Exact changed own-inputs in the population; focus changes include every
item whose own focus weight changes. -/
def affected (inp : Inputs) (e : Event) : List EntityId :=
  (inp.items.map Item.id).filter fun i => decide (itemInputs inp i ≠ itemInputs (applyEvent inp e) i)

structure EntryCharacter where
  who : Agent
  entry : Inputs → EntityId → Option Entry

/-- Equal own-item inputs imply equal entries, including priorities and reasons. -/
def LocalCharacter (c : EntryCharacter) : Prop :=
  ∀ inp inp' i, itemInputs inp i = itemInputs inp' i → c.entry inp i = c.entry inp' i

abbrev Entries := List (EntityId × Option Entry)
abbrev Row := EntryCharacter × Entries

def ordered (es : Entries) : List Entry :=
  (es.filterMap Prod.snd).mergeSort (fun a b => a.priority ≥ b.priority)

def evaluate (c : EntryCharacter) (inp : Inputs) : Entries :=
  (inp.items.map Item.id).map fun i => (i, c.entry inp i)

def asCharacter (c : EntryCharacter) : Character :=
  ⟨c.who, fun inp => ordered (evaluate c inp)⟩

def cachedCharacters (rows : List Row) : List Character :=
  rows.map fun r => ⟨r.1.who, fun _ => ordered r.2⟩

structure IncState extends State where
  rows : List Row

/-- Finite entries match current inputs and character order. No assumption on
shown is necessary: both steps construct their next shown view. -/
def Valid (cs : List EntryCharacter) (s : IncState) : Prop :=
  s.rows.map Prod.fst = cs ∧ ∀ r ∈ s.rows, r.2 = evaluate r.1 s.inputs

/-- The else branch reads the previous stored value. Only affected IDs call
entry on new inputs; the updater never calls Character.order. -/
def updateEntries (c : EntryCharacter) (inp : Inputs) (e : Event) (es : Entries) : Entries :=
  es.map fun p => (p.1, if p.1 ∈ affected inp e then c.entry (applyEvent inp e) p.1 else p.2)

def updateRows (inp : Inputs) (e : Event) (rows : List Row) : List Row :=
  rows.map fun r => (r.1, updateEntries r.1 inp e r.2)

def stepInc (_cs : List EntryCharacter) (s : IncState) (e : Event) : IncState :=
  let inp := applyEvent s.inputs e
  let rows := updateRows s.inputs e s.rows
  { toState := { s.toState with
      now := s.now + 1
      inputs := inp
      shown := rebuild (cachedCharacters rows) inp
      lastInputChange := match e with
        | .turn _ _ | .tick => s.lastInputChange
        | .docChange .. | .readingLanded .. | .focus .. => s.lastInputChange + 1
      lastRecompute := s.now + 1 }
    rows := rows }

private theorem items_unchanged (inp : Inputs) (e : Event) :
    (applyEvent inp e).items = inp.items := by
  cases e with
  | turn t ev => cases ev <;> rfl
  | _ => rfl

theorem outside_affected (inp : Inputs) (e : Event) (i : EntityId)
    (hi : i ∈ inp.items.map Item.id) (ha : i ∉ affected inp e) :
    itemInputs inp i = itemInputs (applyEvent inp e) i := by
  simpa [affected, List.mem_filter, hi] using ha

theorem updateEntries_correct (c : EntryCharacter) (inp : Inputs) (e : Event)
    (locality : LocalCharacter c) :
    updateEntries c inp e (evaluate c inp) = evaluate c (applyEvent inp e) := by
  simp only [updateEntries, evaluate, items_unchanged, List.map_map]
  apply List.map_congr_left
  intro i hi
  dsimp
  by_cases ha : i.id ∈ affected inp e
  · simp [ha]
  · have hm : i.id ∈ inp.items.map Item.id := List.mem_map.mpr ⟨i, hi, rfl⟩
    simp [ha, locality inp (applyEvent inp e) i.id (outside_affected inp e i.id hm ha)]

/-- Reuse itself needs no locality hypothesis: even a non-local producer cannot
secretly reevaluate an unaffected ID. Locality makes this reuse correct. -/
theorem unaffected_entry_reused (c : EntryCharacter) (inp : Inputs) (e : Event)
    (i : EntityId) (v : Option Entry) (es : Entries)
    (hm : (i, v) ∈ es) (ha : i ∉ affected inp e) :
    (i, v) ∈ updateEntries c inp e es := by
  apply List.mem_map.mpr
  exact ⟨(i, v), hm, by simp [ha]⟩

/-- Exact list equality for the retained part (including values and order),
stronger than membership of any one reused entry. -/
theorem unaffected_entries_preserved (c : EntryCharacter) (inp : Inputs) (e : Event)
    (es : Entries) :
    (updateEntries c inp e es).filter (fun p => decide (p.1 ∉ affected inp e)) =
      es.filter (fun p => decide (p.1 ∉ affected inp e)) := by
  induction es with
  | nil => rfl
  | cons p es ih =>
    by_cases ha : p.1 ∈ affected inp e <;> simp [updateEntries, ha] at *
    all_goals exact ih

private def freeze (inp : Inputs) (c : Character) : Character :=
  ⟨c.who, fun _ => c.order inp⟩

private theorem reason_freeze (cs : List Character) (inp : Inputs) (a : Agent) (i : EntityId) :
    reasonFor (cs.map (freeze inp)) inp a i = reasonFor cs inp a i := by
  induction cs with
  | nil => rfl
  | cons c cs ih =>
    simp only [List.map_cons, reasonFor, freeze]
    split <;> simp_all

private theorem rebuild_freeze (cs : List Character) (inp : Inputs) :
    rebuild (cs.map (freeze inp)) inp = rebuild cs inp := by
  simp only [rebuild, orderingsOf, List.map_map]
  congr 1
  funext a i
  exact reason_freeze cs inp a i

theorem stepInc_valid (cs : List EntryCharacter) (s : IncState) (e : Event)
    (valid : Valid cs s) (locality : ∀ c ∈ cs, LocalCharacter c) :
    Valid cs (stepInc cs s e) := by
  constructor
  · simpa [stepInc, updateRows, List.map_map] using valid.1
  · intro r hr
    obtain ⟨old, ho, rfl⟩ := List.mem_map.mp hr
    dsimp
    rw [valid.2 old ho]
    apply updateEntries_correct
    apply locality old.1
    rw [← valid.1]
    exact List.mem_map.mpr ⟨old, ho, rfl⟩

private theorem cached_rebuild (cs : List EntryCharacter) (s : IncState)
    (valid : Valid cs s) :
    rebuild (cachedCharacters s.rows) s.inputs = rebuild (cs.map asCharacter) s.inputs := by
  have h : cachedCharacters s.rows = (cs.map asCharacter).map (freeze s.inputs) := by
    rw [← valid.1]
    simp only [cachedCharacters, List.map_map]
    apply List.map_congr_left
    intro r hr
    simp only [Function.comp_apply, freeze, asCharacter]
    rw [valid.2 r hr]
  rw [h, rebuild_freeze]

/-- Equality with the full reference step, not a definitional rebuild.
Sorting/round-robin can still move shown positions; entry stability is not
rank-position stability. -/
theorem stepInc_eq_step (cs : List EntryCharacter) (s : IncState) (e : Event)
    (valid : Valid cs s) (locality : ∀ c ∈ cs, LocalCharacter c) :
    (stepInc cs s e).shown = (Circus.step (cs.map asCharacter) s.toState e).shown := by
  exact cached_rebuild cs (stepInc cs s e) (stepInc_valid cs s e valid locality)

/-- An event changing only j's own inputs leaves all other entries stable. -/
theorem single_item_stability (c : EntryCharacter) (inp : Inputs) (e : Event)
    (j : EntityId) (locality : LocalCharacter c)
    (only : ∀ i, i ≠ j → itemInputs inp i = itemInputs (applyEvent inp e) i)
    (i : EntityId) (different : i ≠ j) :
    c.entry inp i = c.entry (applyEvent inp e) i :=
  locality inp (applyEvent inp e) i (only i different)

/-- Evaluate once to establish the cache invariant; subsequent steps reuse it. -/
def seedCache (cs : List EntryCharacter) (s : State) : IncState :=
  { toState := s, rows := cs.map fun c => (c, evaluate c s.inputs) }

theorem seedCache_valid (cs : List EntryCharacter) (s : State) :
    Valid cs (seedCache cs s) := by
  constructor
  · simp [seedCache, List.map_map, Function.comp_def]
  · intro r hr
    obtain ⟨c, _, rfl⟩ := List.mem_map.mp hr
    rfl

namespace Teeth
/-- A nonvacuous local producer: priority comes from the item's own activation. -/
def localProducer : EntryCharacter :=
  ⟨.thor, fun inp i => some ⟨i, inp.activation i, { terms := [], chosenBy := .thor }⟩⟩

theorem localProducer_is_local : LocalCharacter localProducer := by
  intro inp inp' i h
  have he := congrArg ItemInputs.activation h
  change inp.activation i = inp'.activation i at he
  simp [localProducer, he]

private def inputs : Inputs :=
  ⟨[⟨1, .mission, "invented-one", true⟩, ⟨2, .mission, "invented-two", true⟩],
    [(2, 1)], fun _ => 0, fun _ _ => false, fun _ => [], [], 0⟩
private def initialState : State := ⟨0, inputs, [], 0, 0⟩

/-- Item 1 improperly depends on item 2's reading, outside its own inputs. -/
def nonlocalProducer : EntryCharacter :=
  ⟨.thor, fun inp i => if i = 1 ∧ (2, 1) ∈ inp.readings then
    some ⟨1, 0, { terms := ["invented dependency"], chosenBy := .thor }⟩ else none⟩

private def cached : IncState := seedCache [nonlocalProducer] initialState

theorem only_second_affected : affected inputs (.docChange 2 2) = [2] := by decide

theorem nonlocalProducer_not_local : ¬LocalCharacter nonlocalProducer := by
  intro h
  have same : itemInputs inputs 1 = itemInputs (applyEvent inputs (.docChange 2 2)) 1 := by decide
  have impossible := h inputs (applyEvent inputs (.docChange 2 2)) 1 same
  change some _ = none at impossible
  cases impossible

/-- Valid cache alone is insufficient. Incremental reuse keeps item 1 whereas
full evaluation drops it. This concrete failure gives locality its teeth. -/
theorem nonlocal_step_disagrees :
    (stepInc [nonlocalProducer] cached (.docChange 2 2)).shown ≠
      (Circus.step [asCharacter nonlocalProducer] cached.toState (.docChange 2 2)).shown := by
  intro h
  have lengths := congrArg List.length h
  have hl : (stepInc [nonlocalProducer] cached (.docChange 2 2)).shown.length = 1 := by
    simp [stepInc, cached, seedCache, initialState, inputs, updateRows, updateEntries,
      evaluate, affected, itemInputs, applyEvent, nonlocalProducer, cachedCharacters,
      ordered, rebuild, orderingsOf, entriesFrom, budgetTen, Retro.partyVote, Retro.sweep,
      Retro.drawFresh]
  have hr : (Circus.step [asCharacter nonlocalProducer] cached.toState (.docChange 2 2)).shown.length = 0 := by
    simp [cached, seedCache, initialState, inputs, evaluate, applyEvent,
      nonlocalProducer, ordered, rebuild, orderingsOf, entriesFrom, budgetTen,
      Retro.partyVote, Retro.sweep, Retro.drawFresh, asCharacter, Circus.step]
  rw [hl, hr] at lengths
  cases lengths

theorem nonlocal_cache_valid : Valid [nonlocalProducer] cached :=
  seedCache_valid [nonlocalProducer] initialState

/-- Exact focus weights select only the reweighted item. -/
theorem focus_affects_reweighted_item : affected inputs (.focus [(1, 3)]) = [1] := by decide

end Teeth
end Circus.Incremental
