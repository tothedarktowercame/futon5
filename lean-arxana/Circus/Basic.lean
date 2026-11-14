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

/-! ## The GCC as a graph (as the loop builds it) -/

/-- The GCC: records reference items (never evidence bodies); reads go
through these references. -/
structure GCC where
  items : List Item
  records : List Record
  refs : Record → List Item

/-- A GCC the loop built: every record points at a stored item, and any two
items are co-referenced by some record (the loop ties related items —
`needs`, `serves` — together through records). This is the hypothesis that
makes point-to-point navigation true; an arbitrary record graph need not
satisfy it. -/
def LoopBuilt (g : GCC) : Prop :=
  (∀ r ∈ g.records, ∃ i ∈ g.refs r, i ∈ g.items)
  ∧ (∀ i ∈ g.items, ∀ j ∈ g.items, ∃ r ∈ g.records, i ∈ g.refs r ∧ j ∈ g.refs r)

/-- A node of the GCC: an item or a record. -/
inductive GNode
  | item (i : Item)
  | record (r : Record)
deriving Repr

/-- Membership: which nodes a GCC actually contains. -/
def GCC.Mem (g : GCC) : GNode → Prop
  | .item i => i ∈ g.items
  | .record r => r ∈ g.records

/-- Adjacency: a record is adjacent to each item it references
(undirected). -/
def GCC.adj (g : GCC) : GNode → GNode → Prop
  | .record r, .item i => i ∈ g.refs r
  | .item i, .record r => i ∈ g.refs r
  | _, _ => False

/-- A path of at most `n` hops from `a` to `b`. -/
inductive GCC.Reach (g : GCC) : Nat → GNode → GNode → Prop
  | refl (a : GNode) : g.Reach 0 a a
  | step (n : Nat) (a c b : GNode) (h : g.adj a c) (t : g.Reach n c b) :
      g.Reach (n + 1) a b
  | more (n : Nat) (a b : GNode) (t : g.Reach n a b) :
      g.Reach (n + 1) a b

/-- A path of `m` hops is a path of any larger length. -/
theorem GCC.Reach.pad {g : GCC} {a b : GNode} :
    ∀ {m n : Nat}, m ≤ n → g.Reach m a b → g.Reach n a b := by
  intro m n hn r
  induction hn with
  | refl => exact r
  | step _ ih => exact .more _ _ _ ih

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

/-- The inputs a ranking is computed from at a moment.

`mana` is a TYPED HOLE (Joe, 2026-11-14: the attention meter "may turn out to
be a typed hole", futon3 `futon3/library/proof-search/typed-hole-as-frontier.flexiarg`):
a parameter of known type with NO producer — no `Event` writes it and no
animal's record feeds it. It threads through `applyEvent` untouched; the
design must still say what, if anything, produces it. -/
structure Inputs where
  items : List Item
  readings : List (EntityId × Nat)        -- item, document version read
  activation : EntityId → Nat
  needs : EntityId → EntityId → Bool
  serves : EntityId → List BenefitLine
  focus : List (EntityId × Nat)            -- 猴: weight per item
  mana : Nat

/-- A character orders open items by its own rule (unblocking, strategy,
quick, harm reduction). -/
structure Character where
  who : Agent
  order : Inputs → List Entry

/-! The party view is concrete in `Circus/Loop.lean` (`rebuild`, over the
retro-specified round robin); the draft's `opaque party` placeholder is
gone. -/

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

/-! `step` and `rebuild` are concrete in `Circus/Loop.lean`; the statements
about them (`incremental_eq_rebuilt`, `quiescent_shows_nothing_new`,
`top_ten_is_a_cascade`, and the primary theorem) live there too. -/

/-! ## Statements for Joe to read (restated 2026-11-14; the vacuous or false
drafts are replaced. Provable-now statements are proved; the rest carry real
statements under `sorry` per the statement-registry policy, each with a
witness that the statement has content). -/

/-! ### `every_read_has_a_writer`

Restated (the draft was trivially true: a handoff witnesses itself): every
record kind any agent reads is written by some *other* agent in the wiring.
Giraffe's own reading is the one self-read in the design — giraffe rereads
its own record — so it is stated as an explicit exception, not hidden. -/

def writtenByAnother (h : Handoff) : Bool :=
  wiring.any fun w =>
    decide (w.kind = h.kind ∧ w.reader = h.reader ∧ w.writer ≠ h.reader)

def readHasWriter (h : Handoff) : Bool :=
  (h.kind == RecordKind.giraffeReading) || writtenByAnother h

theorem every_read_has_a_writer : wiring.all readHasWriter = true := by
  decide

theorem every_read_has_a_writer_of_mem {h : Handoff} (hh : h ∈ wiring)
    (hkind : h.kind ≠ RecordKind.giraffeReading) :
    ∃ w ∈ wiring, w.kind = h.kind ∧ w.reader = h.reader ∧ w.writer ≠ h.reader := by
  have hall := List.all_eq_true.mp every_read_has_a_writer h hh
  simp [readHasWriter, hkind, writtenByAnother, List.any_eq_true] at hall
  obtain ⟨w, hw_mem, hw⟩ := hall
  exact ⟨w, hw_mem, hw⟩

/-! ### `handoffs_follow_the_chain`

Restated (the draft was `True`; a plain "earliest trigger" version is also
too weak, because a reading entry drags the minimum down itself): for every
turn-triggered kind there is a first entry written by the producer (writer ≠
reader), every entry of the kind has that same writer, and no entry's read
happens before the first entry's rank. A consumer getting the kind from
anyone else, or before it enters the chain, fails this. -/

/-- The rank of a trigger; an untriggered handoff sits after the whole
chain. -/
def triggerRank (o : Option TurnEvent) : Nat :=
  o.elim 5 TurnEvent.rank

/-- The rank at which a handoff reads. -/
def entryRank (h : Handoff) : Nat :=
  triggerRank h.trigger

def kindEntries (k : RecordKind) : List Handoff :=
  wiring.filter fun h => h.kind == k

/-- The first entry of a kind in the chain: minimal `entryRank`. -/
def firstOf (k : RecordKind) : Option Handoff :=
  (kindEntries k).foldl (fun acc h =>
    match acc with
    | none => some h
    | some w => if entryRank h < entryRank w then some h else acc) none

def followsChain (fo : Option Handoff) (h : Handoff) : Bool :=
  match fo with
  | none => false
  | some w =>
      (w.writer == h.writer) && decide (w.writer ≠ w.reader)
        && decide (entryRank w ≤ triggerRank h.trigger)

def chainHandoffs (h : Handoff) : Bool :=
  h.trigger.elim true fun _ => followsChain (firstOf h.kind) h

theorem handoffs_follow_the_chain : wiring.all chainHandoffs = true := by
  decide

private theorem pick_mem :
    ∀ (l : List Handoff) (acc : Option Handoff) (w : Handoff),
      (l.foldl (fun a h =>
        match a with
        | none => some h
        | some v => if entryRank h < entryRank v then some h else a) acc
        = some w) → w ∈ l ∨ acc = some w := by
  intro l
  induction l with
  | nil => intro acc w h; exact Or.inr h
  | cons x xs ih =>
    intro acc w h
    simp only [List.foldl_cons] at h
    cases ih _ w h with
    | inl hm => exact Or.inl (List.mem_cons_of_mem _ hm)
    | inr hc =>
      cases acc with
      | none =>
        simp only at hc
        have hxw : w = x := Option.some.inj hc.symm
        exact Or.inl (hxw ▸ List.mem_cons_self ..)
      | some v =>
        by_cases hxv : entryRank x < entryRank v
        · simp only at hc
          rw [if_pos hxv] at hc
          have hxw : w = x := Option.some.inj hc.symm
          exact Or.inl (hxw ▸ List.mem_cons_self ..)
        · simp only at hc
          rw [if_neg hxv] at hc
          exact Or.inr hc

theorem firstOf_mem {k : RecordKind} {w : Handoff} (h : firstOf k = some w) :
    w ∈ wiring ∧ w.kind = k := by
  simp only [firstOf] at h
  cases pick_mem (kindEntries k) none w h with
  | inr hn => exact absurd hn (by simp)
  | inl hm =>
    simp only [kindEntries, List.mem_filter] at hm
    exact ⟨hm.1, of_decide_eq_true hm.2⟩

theorem handoffs_follow_the_chain_of_mem {h : Handoff} (hh : h ∈ wiring)
    {t : TurnEvent} (ht : h.trigger = some t) :
    ∃ w ∈ wiring, w.kind = h.kind ∧ w.writer = h.writer ∧ w.writer ≠ w.reader
      ∧ entryRank w ≤ TurnEvent.rank t := by
  have hall := List.all_eq_true.mp handoffs_follow_the_chain h hh
  rw [chainHandoffs, ht] at hall
  have hf0 : followsChain (firstOf h.kind) h = true := hall
  generalize hfo : firstOf h.kind = fo at hf0
  cases fo with
  | none => rw [followsChain] at hf0; exact Bool.noConfusion hf0
  | some w =>
    rw [followsChain] at hf0
    simp only [Bool.and_eq_true, decide_eq_true_eq, beq_iff_eq, ht] at hf0
    have hfm := firstOf_mem hfo
    exact ⟨w, hfm.1, hfm.2, hf0.1.1, hf0.1.2, hf0.2⟩

/-! ### `gcc_navigable_in_five`

Restated (the draft was false for arbitrary graphs): the statement holds for
a GCC *the loop built* — `LoopBuilt` above — and in fact four hops suffice. -/

private theorem reachable_items {g : GCC} (hb : LoopBuilt g) {i j : Item}
    (hi : i ∈ g.items) (hj : j ∈ g.items) :
    g.Reach 2 (.item i) (.item j) := by
  obtain ⟨r, _, hi1, hj1⟩ := hb.2 i hi j hj
  exact .step 1 (.item i) (.record r) (.item j) hi1
    (.step 0 (.record r) (.item j) (.item j) hj1 (.refl _))

/-- Key lemma (Joe, 2026-11-14): a loop-built GCC is navigable point to
point in at most four hops (so five always suffices). -/
theorem gcc_navigable_in_four (g : GCC) (hb : LoopBuilt g) (a b : GNode)
    (ha : g.Mem a) (hbm : g.Mem b) :
    g.Reach 4 a b := by
  match a, b, ha, hbm with
  | .item i, .item j, hi, hj =>
    exact GCC.Reach.pad (by decide) (reachable_items hb hi hj)
  | .item i, .record r, hi, hr =>
    obtain ⟨j, hjr, hj⟩ := hb.1 r hr
    obtain ⟨r', _, hi2, hj2⟩ := hb.2 i hi j hj
    exact GCC.Reach.pad (by decide)
      (.step 2 (.item i) (.record r') (.record r) hi2
        (.step 1 (.record r') (.item j) (.record r) hj2
          (.step 0 (.item j) (.record r) (.record r) hjr (.refl _))))
  | .record r, .item j, hr, hj =>
    obtain ⟨i, hir, hi⟩ := hb.1 r hr
    obtain ⟨r', _, hi2, hj2⟩ := hb.2 i hi j hj
    exact GCC.Reach.pad (by decide)
      (.step 2 (.record r) (.item i) (.item j) hir
        (.step 1 (.item i) (.record r') (.item j) hi2
          (.step 0 (.record r') (.item j) (.item j) hj2 (.refl _))))
  | .record r, .record r', hr, hr' =>
    obtain ⟨i, hir, hi⟩ := hb.1 r hr
    obtain ⟨j, hjr, hj⟩ := hb.1 r' hr'
    obtain ⟨rc, _, hi2, hj2⟩ := hb.2 i hi j hj
    exact .step 3 (.record r) (.item i) (.record r') hir
      (.step 2 (.item i) (.record rc) (.record r') hi2
        (.step 1 (.record rc) (.item j) (.record r') hj2
          (.step 0 (.item j) (.record r') (.record r') hjr (.refl _))))

theorem gcc_navigable_in_five (g : GCC) (hb : LoopBuilt g) (a b : GNode)
    (ha : g.Mem a) (hbm : g.Mem b) :
    g.Reach 5 a b :=
  GCC.Reach.pad (by decide) (gcc_navigable_in_four g hb a b ha hbm)

/-- The `LoopBuilt` hypothesis has teeth: a record-less GCC does not connect
its items at any length. -/
private def emptyGCC : GCC := ⟨[], [], fun _ => []⟩
private def it0 : Item := ⟨0, .mission, "a", true⟩
private def it1 : Item := ⟨1, .mission, "b", true⟩

private theorem empty_reach : ∀ {n : Nat} {a b : GNode},
    emptyGCC.Reach n a b → a = b := by
  intro n a b h
  induction h with
  | refl => rfl
  | @step n' a' c' b' hadj _ _ =>
    cases a' <;> cases c' <;> simp [GCC.adj, emptyGCC] at hadj
  | @more n' a' b' t ih => exact ih

example : ¬ emptyGCC.Reach 5 (.item it0) (.item it1) := by
  intro h
  exact absurd (GNode.item.inj (empty_reach h)) (by decide)

/-! ### `top_ten_is_a_cascade` (statement in `Circus/Loop.lean`; the
definition and witness below stay here)

Restated (the draft was `True`): every entry after the first either needs an
earlier entry, or names why it stands alone (a nonempty reason). -/

def Cascade (inp : Inputs) (es : List Entry) : Prop :=
  ∀ k (hk : k < es.length) (_ : 0 < k),
    (∃ (j : Nat) (hj : j < k),
        inp.needs (es.get ⟨k, hk⟩).item (es.get ⟨j, Nat.lt_of_lt_of_le hj (Nat.le_of_lt hk)⟩).item = true)
    ∨ (es.get ⟨k, hk⟩).reason.terms ≠ []

private def cascadeInp : Inputs :=
  ⟨[], [], fun _ => 0, fun _ _ => false, fun _ => [], [], 0⟩

private def plainEntry (i : EntityId) : Entry :=
  ⟨i, 0, ⟨[], .giraffe⟩⟩

/-- The cascade statement has teeth: a second entry that neither needs an
earlier one nor says why it stands alone is not a cascade. -/
example : ¬ Cascade cascadeInp [plainEntry 0, plainEntry 1] := by
  intro h
  have h1 := h 1 (by decide) (by decide)
  cases h1 with
  | inl he =>
    obtain ⟨j, hj, hn⟩ := he
    have hj0 : j = 0 := Nat.le_zero.mp (Nat.le_of_lt_succ hj)
    subst hj0
    exact absurd hn (by
      have hf : cascadeInp.needs ([plainEntry 0, plainEntry 1].get ⟨1, by decide⟩).item
          ([plainEntry 0, plainEntry 1].get ⟨0, by decide⟩).item = false := rfl
      rw [hf]; exact fun h => Bool.noConfusion h)
  | inr hn => exact hn rfl

/-! ### `primary_up_to_date_top_ten`

The primary theorem (Joe, 2026-11-14): "Giraffe always gives an up to date
ranking of at least the top ten missions by priority order that is
recognisable to the operator"; recognisable = explainable. It is stated and
proved over the concrete loop in `Circus/Loop.lean`, under explicit
hypotheses about the characters. -/

/-! ### `focus_coherence`

Restated (the draft was `True`): a relatedness measure over focused items —
one needs the other, or both serve the same accepted benefit line — and a
coherence score counting related pairs. Joe: focus over three related
missions beats five unrelated ones. -/

/-- Two items are related when one needs the other, or both serve the same
operator-accepted benefit line. -/
def related (inp : Inputs) (i j : EntityId) : Bool :=
  (inp.needs i j || inp.needs j i)
    || (inp.serves i).any (fun b =>
          b.acceptedByOperator && (inp.serves j).contains b)

/-- The coherence of a focus list: its number of ordered related pairs. -/
def relatedPairs (inp : Inputs) (F : List (EntityId × Nat)) : Nat :=
  F.foldl (fun acc x =>
    F.foldl (fun a y => a + (if related inp x.1 y.1 then 1 else 0)) acc) 0

theorem focus_coherence (inp : Inputs) (F G : List (EntityId × Nat))
    (hF : 3 ≤ F.length) (hnd : (F.map Prod.fst).Nodup)
    (hFrel : (F.map Prod.fst).Pairwise (fun i j => related inp i j))
    (hGrel : (G.map Prod.fst).Pairwise (fun i j => !related inp i j)) :
    relatedPairs inp G < relatedPairs inp F := by
  sorry

/-- The coherence statement has teeth: three items with no needs edges and
no shared accepted benefit line score zero, so no focus over them can beat
anything. -/
example :
    relatedPairs ⟨[], [], fun _ => 0, fun _ _ => false, fun _ => [], [], 0⟩
      [(0, 1), (1, 1), (2, 1)] = 0 := by
  rfl

end Circus
