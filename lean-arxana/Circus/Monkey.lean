import Circus.Freshness

/-!
# VERIFY pre-specification: 猴 writes focus; Giraffe receives it

No live 猴 implementation is claimed. The abstract reader may split its window
and retry; its final proposal or exhausted failure is input to monkeyRun.
The publication guard adds typed validation failures rather than manufacturing
a focus when evidence or location cannot be established. Pattern-verdict output
is a separate companion contract; this file specifies the focus consumed by
priority.clj, not a replacement verdict schema or 馬's tensions.

The existing GCC, turnItems, LoopBuilt and Navigation are reused. The audited
read set must be recorded by the reader runner, not guessed from its prose.
Digest tokens stand for content equality, not elapsed time. Late writes change
those tokens and require revalidation, even after settlement.

Freshness.advance supplies the focus event to the existing State/step/rebuild
loop. incremental_eq_rebuilt and primary_up_to_date_top_ten remain logical
ranking results; docChange_stales, readingLanded_unstales and giraffe_firing_rule
are unchanged. This module supplies missing focus absence/citation handling.
-/
namespace Circus.Monkey
open Arxana Freshness

inductive Relation | focus | associated | usefulElsewhere
  deriving DecidableEq, Repr
inductive Failure
  | windowTooLargeAfterSplit | readerFailedAfterRetries
  | turnNotSettled | replyNotLanded | invalidEvidence | focusNotLocated
  | evidenceChanged
  deriving DecidableEq, Repr
abbrev EvidencePin := List (GCCRecord × Nat)
structure Snapshot where
  gcc : GCC
  digest : GCCRecord → Option Nat
structure RunInput where
  snapshot : Snapshot
  turn : TurnId
  turnRecords : List GCCRecord
  readRecords : List GCCRecord
  settled : Bool
  replyLanded : Bool
  outputId : Nat
structure Proposal where
  relations : List (EntityId × Relation)
  hopBound : Nat
  deriving DecidableEq, Repr
inductive ReaderResult
  | proposed (p : Proposal)
  | windowTooLargeAfterSplit
  | readerFailedAfterRetries
  deriving DecidableEq, Repr
structure FocusRecord where
  record : GCCRecord
  relations : List (EntityId × Relation)
  readRecords : List GCCRecord
  evidencePin : EvidencePin
  deriving DecidableEq, Repr
inductive Outcome
  | written (focus : FocusRecord)
  | failed (reason : Failure)
  deriving DecidableEq, Repr

def relation (rs : List (EntityId × Relation)) (id : EntityId) : Option Relation :=
  (rs.find? (fun p => p.1 == id)).map Prod.snd

def pin (snap : Snapshot) (records : List GCCRecord) : EvidencePin :=
  records.filterMap fun r => (snap.digest r).map (r, ·)
def PinFresh (snap : Snapshot) (f : FocusRecord) : Prop :=
  f.evidencePin.map Prod.fst = f.readRecords ∧
    ∀ p ∈ f.evidencePin, p.1 ∈ snap.gcc.records ∧ snap.digest p.1 = some p.2

def EvidenceValid (i : RunInput) : Prop :=
  i.readRecords.Nodup ∧
  i.turnRecords = i.snapshot.gcc.records.filter (fun r => r.turn == some i.turn) ∧
  (∀ r ∈ i.turnRecords, r ∈ i.readRecords) ∧
  (∀ r ∈ i.readRecords, r ∈ i.snapshot.gcc.records ∧ ∃ d, i.snapshot.digest r = some d)

def focusItems (g : GCC) (rs : List (EntityId × Relation)) : List Item :=
  g.items.filter (fun it => relation rs it.id == some .focus)
/-- Every relation names a stored item. Only primary focus is required to be
in the touched component; useful-elsewhere is deliberately not forced into it.
A nonempty primary focus needs an actual touched anchor. -/
def Located (i : RunInput) (p : Proposal) : Prop :=
  (p.relations.map Prod.fst).Nodup ∧
  (∀ pair ∈ p.relations, ∃ it ∈ i.snapshot.gcc.items, it.id = pair.1) ∧
  (focusItems i.snapshot.gcc p.relations ≠ [] → turnItems i.snapshot.gcc i.turn [] ≠ []) ∧
  LoopBuilt i.snapshot.gcc
    (turnItems i.snapshot.gcc i.turn (focusItems i.snapshot.gcc p.relations)) p.hopBound

/-- Abstract runner boundary, not an LLM algorithm: readiness first, then
reader outcome, then audited evidence and measured location checks. -/
noncomputable def monkeyRun (i : RunInput) (result : ReaderResult) : Outcome := by
  classical
  exact if !i.settled then .failed .turnNotSettled
    else if !i.replyLanded then .failed .replyNotLanded
    else match result with
      | .windowTooLargeAfterSplit => .failed .windowTooLargeAfterSplit
      | .readerFailedAfterRetries => .failed .readerFailedAfterRetries
      | .proposed p =>
        if ¬EvidenceValid i then .failed .invalidEvidence
        else if ¬Located i p then .failed .focusNotLocated
        else .written ⟨⟨i.outputId, .houFocus, some i.turn⟩, p.relations,
          i.readRecords, pin i.snapshot i.readRecords⟩

def WaitsForReply (i : RunInput) (out : Outcome) : Prop :=
  ∀ f, out = .written f → i.settled = true ∧ i.replyLanded = true
/-- VERIFY obligation 2, "live operator turn through the chain": 猴 must wait
for settlement AND the reply. A settled flag alone is not permission to write. -/
theorem monkey_waits_for_reply (i : RunInput) (result : ReaderResult) :
    WaitsForReply i (monkeyRun i result) := by sorry

/-- Navigation is witnessed, not merely absence of an error. -/
def Navigable (g : GCC) (a b : Item) : Prop :=
  ∃ n, ∃ h : g.Reach n (.item a) (.item b), navigate g (.item a) (.item b) = .path n h

def FocusLocated (i : RunInput) (f : FocusRecord) : Prop :=
  (∀ pair ∈ f.relations, ∃ it ∈ i.snapshot.gcc.items, it.id = pair.1) ∧
  (∀ target ∈ focusItems i.snapshot.gcc f.relations,
    ∃ anchor ∈ turnItems i.snapshot.gcc i.turn [], Navigable i.snapshot.gcc anchor target)

/-- VERIFY obligation 2, "reaches the reader": finalising the cascade AND
locating it within the GCC. Unknown and disconnected targets are not focus. -/
theorem focus_located_in_gcc (i : RunInput) (result : ReaderResult) (f : FocusRecord)
    (written : monkeyRun i result = .written f) :
    f.record.kind = .houFocus ∧ f.record.turn = some i.turn ∧ FocusLocated i f := by sorry

/-- Add a focus annotation even if another Freshness reason has precedence.
No stale/absent focus remains active, and focusVersion is cleared. -/
def markFocus (v : ViewState) (why : StaleReason) : ViewState :=
  let v' := render { v with sources := { v.sources with focusVersion := none } }
  { v' with header := some why
            marks := v'.marks ++ (match v'.artifact with
              | none => []
              | some a => a.entries.map (fun e => (e.item, why))) }
structure Consumer where
  active : Option FocusRecord
  failure : Option Failure
  view : ViewState

/-- Only a fresh pin reaches the ranking loop. Failure clears focus rather
than representing failure as a successful empty relations map. -/
noncomputable def consume (cs : List Circus.Character) (snap : Snapshot)
    (v : ViewState) (out : Outcome) : Consumer := by
  classical
  exact match out with
    | .failed why => ⟨none, some why, markFocus v .focusAbsent⟩
    | .written f =>
      if PinFresh snap f then
        ⟨some f, none, advance cs v (.logical (.focus
          ((f.relations.filter (·.2 == .focus)).map (fun p => (p.1, 1)))))⟩
      else ⟨none, some .evidenceChanged, markFocus v .sourceChangedSincePass⟩

/-- VERIFY obligation 3, "pass's input digest ... newest focus": the pin's
record list equals the audited read set exactly. Changing ANY pinned digest
invalidates it and produces sourceChangedSincePass, never active focus. -/
theorem focus_pins_its_evidence (i : RunInput) (result : ReaderResult) (f : FocusRecord)
    (written : monkeyRun i result = .written f) :
    f.readRecords = i.readRecords ∧ PinFresh i.snapshot f ∧
    (∀ later r d, (r, d) ∈ f.evidencePin → later.digest r ≠ some d →
      ¬PinFresh later f ∧ ∀ cs v,
        (consume cs later v (.written f)).active = none ∧
        (consume cs later v (.written f)).view.header = some .sourceChangedSincePass) := by sorry

inductive Band | focus | associated | elsewhere | unknown
  deriving DecidableEq, Repr
inductive PriorityReason | focusRelationMissing
  deriving DecidableEq, Repr
def band : Option Relation → Band
  | some .focus => .focus
  | some .associated => .associated
  | some .usefulElsewhere => .elsewhere
  | none => .unknown
def Band.order : Band → Nat
  | .focus => 0 | .associated => 1 | .elsewhere => 2 | .unknown => 3
structure PassRow where
  target : EntityId
  rank : Option Nat
  deriving DecidableEq, Repr
structure PriorityRow where
  target : EntityId
  passRank : Nat
  sourceIndex : Nat
  relation : Option Relation
  band : Band
  reason : Option PriorityReason
  evidencePin : EvidencePin
  deriving DecidableEq, Repr
structure RankedRow where
  row : PriorityRow
  withinCutoff : Bool
  deriving DecidableEq, Repr

def rawRows (rows : List PassRow) (focus : Option FocusRecord) : List PriorityRow :=
  rows.mapIdx fun index row =>
    let rel := focus.bind (fun f => relation f.relations row.target)
    ⟨row.target, row.rank.getD (index + 1), index, rel, band rel,
      if rel.isNone then some .focusRelationMissing else none,
      focus.map (·.evidencePin) |>.getD []⟩
/-- Lexicographic band/rank, with original index breaking exact ties stably. -/
def rowLE (a b : PriorityRow) : Bool :=
  a.band.order < b.band.order || (a.band == b.band &&
    (a.passRank < b.passRank || (a.passRank == b.passRank && a.sourceIndex ≤ b.sourceIndex)))
def insertRow (r : PriorityRow) : List PriorityRow → List PriorityRow
  | [] => [r]
  | x :: xs => if rowLE r x then r :: x :: xs else x :: insertRow r xs
def sortRows : List PriorityRow → List PriorityRow
  | [] => []
  | x :: xs => insertRow x (sortRows xs)
/-- priority.clj projection: preserve every pass row, group by received band,
then pass rank; cutoff marks rows and never discards them. Callers pass only
consume.active, not stale historical focus. No focus is inferred here. -/
def priorityProjection (rows : List PassRow) (focus : Option FocusRecord) (cutoff : Nat) : List RankedRow :=
  (sortRows (rawRows rows focus)).mapIdx (fun i r => ⟨r, i < cutoff⟩)
def ProjectionCorrect (rows : List PassRow) (f : Option FocusRecord) (cutoff : Nat)
    (out : List RankedRow) : Prop :=
  (out.map (·.row)).Perm (rawRows rows f) ∧
  (out.map (·.row)).Pairwise (fun a b => rowLE a b = true) ∧
  (∀ r ∈ out, (f.bind (fun focus => relation focus.relations r.row.target)) = none →
    r.row.band = .unknown ∧ r.row.reason = some .focusRelationMissing) ∧
  (∀ index r, out[index]? = some r → r.withinCutoff = decide (index < cutoff))

/-- VERIFY obligation 2, "pass/priorities ... reaches the reader": Giraffe
orders focus before associated before elsewhere before unknown, preserving
pass rank within bands. Missing relation is explicitly unknown, never band 0. -/
theorem giraffe_band_respects_focus (rows : List PassRow) (f : Option FocusRecord) (cutoff : Nat) :
    ProjectionCorrect rows f cutoff (priorityProjection rows f cutoff) := by sorry

def FailureVisible (why : Failure) (consumer : Consumer) : Prop :=
  consumer.active = none ∧ consumer.failure = some why ∧
    consumer.view.header = some .focusAbsent ∧ consumer.view.sources.focusVersion = none

/-- VERIFY objection 5, "show how the consumer sees that condition": failed
split/retry/readiness/validation runs are typed absences with focusAbsent,
not successful empty focus. A stale pin separately shows sourceChangedSincePass. -/
theorem monkey_failure_is_typed (cs : List Circus.Character) (snap : Snapshot)
    (v : ViewState) (why : Failure) :
    FailureVisible why (consume cs snap v (.failed why)) := by sorry

/-! ## TEETH: bad producers fail the consumer contracts -/
namespace Teeth
private def it : Item := ⟨1, .mission, "M-example", true⟩
private def other : Item := ⟨2, .ticket, "T-isolated", true⟩
private def mention : GCCRecord := ⟨1, .mention, some 7⟩
private def graph : GCC := ⟨[it, other], [mention], [], [],
  fun r role => if r == mention && role == .item then some (.item it) else none, []⟩
private def snap : Snapshot := ⟨graph, fun r => if r == mention then some 10 else none⟩
private def input : RunInput := ⟨snap, 7, [mention], [mention], true, false, 2⟩
private def focus : FocusRecord := ⟨⟨2, .houFocus, some 7⟩, [(1, .focus)], [mention], [(mention, 10)]⟩
-- 1. Settlement alone cannot license a written focus record.
example : ¬WaitsForReply input (.written focus) := by
  intro h
  have impossible := (h focus rfl).2
  cases impossible

private theorem no_incidence (r : GCCRecord) : graph.incident r (.item other) = false := by
  by_cases hr : r = mention
  · subst r; decide
  · simp [GCC.incident, graph, hr]
private theorem isolated_only {n : Nat} {x y : GNode} (h : graph.Reach n x y) :
    y = .item other → x = .item other := by
  induction h with
  | refl => exact id
  | step _ x c _ hadj _ ih =>
    intro hy
    have hc := ih hy
    subst c
    cases x <;> simp [GCC.adj, no_incidence] at hadj
  | more _ _ _ _ ih => exact ih
-- 2. A stored but disconnected focus target still fails location.
example : ¬FocusLocated input { focus with relations := [(2, .focus)] } := by
  intro h
  obtain ⟨anchor, ha, n, path, _⟩ := h.2 other (by decide)
  have eq : anchor = it := by simpa [input, snap, turnItems, graph, GCC.incident, GCC.memBool,
    endpointRoles, it, other, mention] using ha
  subst anchor
  have impossible := isolated_only path rfl
  exact (by decide : GNode.item it ≠ .item other) impossible
-- 3. An empty pin cannot certify a nonempty audited read set after a change.
example : ¬PinFresh { snap with digest := fun _ => some 11 } { focus with evidencePin := [] } := by
  intro h
  have impossible := h.1
  cases impossible
-- 4. Band 0 for an unrelated target violates the projection contract.
private def wrongRow : RankedRow :=
  ⟨⟨9, 1, 0, none, .focus, none, []⟩, true⟩
example : ¬ProjectionCorrect [⟨9, none⟩] none 1 [wrongRow] := by
  intro h
  have impossible := (h.2.2.1 wrongRow (by simp) rfl).1
  cases impossible
private def loop : State := ⟨0, ⟨[], [], fun _ => 0, fun _ _ => false, fun _ => [], [], 0⟩, [], 0, 0⟩
private def view : ViewState := initial loop ⟨1, 1, [], 1, 1, some 1⟩
-- 5. Treating failure as successful empty focus violates the visibility contract.
example : ¬FailureVisible .readerFailedAfterRetries
    ⟨some { focus with relations := [] }, none, view⟩ := by
  intro h
  cases h.1
-- The consumer really clears failed focus and exposes its typed absence.
example : FailureVisible .readerFailedAfterRetries
    (consume [] snap view (.failed .readerFailedAfterRetries)) := by
  simp [FailureVisible, consume, markFocus, render]
private def changed : Snapshot := { snap with digest := fun _ => some 11 }
-- A once-valid pin cannot remain active when one of its source digests changes.
example : (consume [] changed view (.written focus)).active = none ∧
    (consume [] changed view (.written focus)).view.header = some .sourceChangedSincePass := by
  have stale : ¬PinFresh changed focus := by
    intro h
    have impossible := (h.2 (mention, 10) (by decide)).2
    cases impossible
  simp [consume, stale, markFocus]
-- The actual runner refuses the premature reply, regardless of its proposal.
example : monkeyRun input (.proposed ⟨[(1, .focus)], 0⟩) = .failed .replyNotLanded := by
  simp [monkeyRun, input]

-- The actual projection preserves all rows, band order, fallback rank and cutoff.
private def received : FocusRecord := { focus with relations :=
  [(1, .focus), (2, .associated), (3, .usefulElsewhere), (5, .focus)] }
example : (priorityProjection [⟨4, none⟩, ⟨2, some 1⟩, ⟨1, some 9⟩,
    ⟨3, some 1⟩, ⟨5, some 2⟩] (some received) 2).map (fun r => (r.row.target, r.withinCutoff)) =
    [(5, true), (1, true), (2, false), (3, false), (4, false)] := by decide
-- A ready run can publish: the guards are satisfiable, not always-failing.
private def ready : RunInput := { input with replyLanded := true }
private def proposal : Proposal := ⟨[(1, .focus)], 0⟩
private theorem evidence_ok : EvidenceValid ready := by
  simp [EvidenceValid, ready, input, snap, graph, mention]
private theorem located_ok : Located ready proposal := by
  refine ⟨by decide, ?_, ?_, ?_⟩
  · intro pair hp
    have heq : pair = (1, Relation.focus) := by simpa [proposal] using hp
    subst pair
    exact ⟨it, by simp [ready, input, snap, graph], rfl⟩
  · intro _
    decide
  · change LoopBuilt graph [it] 0
    have bounds : ∀ a ∈ [it], ∀ b ∈ [it], graph.Reach 0 (.item a) (.item b) := by
      intro a ha b hb
      simp only [List.mem_singleton] at ha hb
      subst a; subst b
      exact .refl _
    refine ⟨?_, ?_, ⟨?_, ?_⟩, bounds⟩
    · intro r hr _
      have heq : r = mention := by simpa [graph] using hr
      subst r
      exact ⟨it, by simp [graph], by decide⟩
    · intro r hr
      have heq : r = mention := by simpa [graph] using hr
      subst r
      refine ⟨by decide, ?_⟩
      intro role n hn
      cases role <;> simp [graph] at hn
      subst n
      exact ⟨trivial, by unfold GCC.Mem; decide⟩
    · intro a ha
      simp only [List.mem_singleton] at ha
      subst a
      simp [graph]
    · intro a ha b hb
      exact ⟨0, bounds a ha b hb⟩
example : monkeyRun ready (.proposed proposal) = .written focus := by
  have hs : ready.settled = true := rfl
  have hr : ready.replyLanded = true := rfl
  simp [monkeyRun, hs, hr, evidence_ok, located_ok]
  rfl
end Teeth
end Circus.Monkey
