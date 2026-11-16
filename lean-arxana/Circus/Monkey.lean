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

private theorem written_guards (i : RunInput) (result : ReaderResult) (f : FocusRecord)
    (h : monkeyRun i result = .written f) :
    i.settled = true ∧ i.replyLanded = true ∧ EvidenceValid i ∧
    ∃ p, Located i p ∧ f = ⟨⟨i.outputId, .houFocus, some i.turn⟩,
      p.relations, i.readRecords, pin i.snapshot i.readRecords⟩ := by
  unfold monkeyRun at h
  split at h <;> try contradiction
  split at h <;> try contradiction
  cases result <;> simp only at h <;> try contradiction
  split at h <;> try contradiction
  split at h <;> simp_all
  exact ⟨_, ‹Located i _›, h.symm⟩

def WaitsForReply (i : RunInput) (out : Outcome) : Prop :=
  ∀ f, out = .written f → i.settled = true ∧ i.replyLanded = true
/-- VERIFY obligation 2, "live operator turn through the chain": 猴 must wait
for settlement AND the reply. A settled flag alone is not permission to write. -/
theorem monkey_waits_for_reply (i : RunInput) (result : ReaderResult) :
    WaitsForReply i (monkeyRun i result) := by
  intro f h
  have hg := written_guards i result f h
  exact ⟨hg.1, hg.2.1⟩

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
    f.record.kind = .houFocus ∧ f.record.turn = some i.turn ∧ FocusLocated i f := by
  obtain ⟨_, _, _, p, located, rfl⟩ := written_guards i result f written
  refine ⟨rfl, rfl, located.2.1, ?_⟩
  intro target ht
  have nonempty : focusItems i.snapshot.gcc p.relations ≠ [] := by
    intro he; rw [he] at ht; cases ht
  obtain ⟨anchor, ha⟩ := List.exists_mem_of_ne_nil _ (located.2.2.1 nonempty)
  refine ⟨anchor, ha, ?_⟩
  have ha' : anchor ∈ turnItems i.snapshot.gcc i.turn (focusItems i.snapshot.gcc p.relations) := by
    simp only [turnItems, List.mem_filter, List.contains_nil, Bool.false_or] at ha
    simp only [turnItems, List.mem_filter, Bool.or_eq_true]
    exact ⟨ha.1, Or.inr ha.2⟩
  have ht' : target ∈ turnItems i.snapshot.gcc i.turn (focusItems i.snapshot.gcc p.relations) := by
    refine List.mem_filter.mpr ⟨(List.mem_filter.mp ht).1, ?_⟩
    simp [ht]
  have path := gcc_turn_navigable_at_most_d _ _ _ _ located.2.2.2 anchor target ha' ht'
  have existsPath : ∃ n, i.snapshot.gcc.Reach n (.item anchor) (.item target) := ⟨_, path⟩
  unfold Navigable navigate
  simp only [dif_pos existsPath]
  exact ⟨existsPath.choose, existsPath.choose_spec, rfl⟩

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

/-- Published fixed-arity links must agree exactly with the header. The supplied
link list is complete for this header: every stored houFocusRelation addressing
it occurs once, with an item endpoint and the corresponding relation payload.
The header is not item-bearing; each link is. -/
structure FocusLink where
  record : GCCRecord
  item : Item
  relation : Relation
  deriving DecidableEq, Repr

def HeaderLinkAgreement (g : GCC) (f : FocusRecord) (links : List FocusLink) : Prop :=
  f.record ∈ g.records ∧ f.record.kind = .houFocus ∧
  (links.map (·.record)).Nodup ∧
  (links.map (fun l => (l.item.id, l.relation))).Perm f.relations ∧
  (∀ l ∈ links, l.record ∈ g.records ∧ l.record.kind = .houFocusRelation ∧
    l.item ∈ g.items ∧ g.endpoint l.record .item = some (.item l.item) ∧
    g.endpoint l.record .subject = some (.record f.record)) ∧
  (∀ r ∈ g.records, r.kind = .houFocusRelation →
    g.endpoint r .subject = some (.record f.record) → ∃ l ∈ links, l.record = r)

/-- Storage readers validate the complete link set before the existing pin
and freshness guard. consume still models the pre-publication boundary. -/
noncomputable def consumePublished (cs : List Circus.Character) (snap : Snapshot)
    (v : ViewState) (f : FocusRecord) (links : List FocusLink) : Consumer := by
  classical
  exact if HeaderLinkAgreement snap.gcc f links then consume cs snap v (.written f)
    else consume cs snap v (.failed .invalidEvidence)

theorem disagreeing_links_refused (cs : List Circus.Character) (snap : Snapshot)
    (v : ViewState) (f : FocusRecord) (links : List FocusLink)
    (bad : ¬HeaderLinkAgreement snap.gcc f links) :
    (consumePublished cs snap v f links).active = none := by
  simp [consumePublished, bad, consume]

private theorem pin_complete (snap : Snapshot) (rs : List GCCRecord)
    (valid : ∀ r ∈ rs, r ∈ snap.gcc.records ∧ ∃ d, snap.digest r = some d) :
    (pin snap rs).map Prod.fst = rs ∧
    ∀ p ∈ pin snap rs, p.1 ∈ snap.gcc.records ∧ snap.digest p.1 = some p.2 := by
  constructor
  · induction rs with
    | nil => rfl
    | cons r rs ih =>
      obtain ⟨_, d, hd⟩ := valid r (by simp)
      have tail := ih (by intro q hq; exact valid q (by simp [hq]))
      simpa [pin, hd] using congrArg (List.cons r) tail
  · intro p hp
    obtain ⟨r, hr, he⟩ := List.mem_filterMap.mp hp
    cases hd : snap.digest r with
    | none => simp [hd] at he
    | some d =>
      simp only [hd, Option.map_some, Option.some.injEq] at he
      subst p
      exact ⟨(valid r hr).1, hd⟩

/-- VERIFY obligation 3, "pass's input digest ... newest focus": the pin's
record list equals the audited read set exactly. Changing ANY pinned digest
invalidates it and produces sourceChangedSincePass, never active focus. -/
theorem focus_pins_its_evidence (i : RunInput) (result : ReaderResult) (f : FocusRecord)
    (written : monkeyRun i result = .written f) :
    f.readRecords = i.readRecords ∧ PinFresh i.snapshot f ∧
    (∀ later r d, (r, d) ∈ f.evidencePin → later.digest r ≠ some d →
      ¬PinFresh later f ∧ ∀ cs v,
        (consume cs later v (.written f)).active = none ∧
        (consume cs later v (.written f)).view.header = some .sourceChangedSincePass) := by
  have hg := written_guards i result f written
  obtain ⟨p, _, hf⟩ := hg.2.2.2
  refine ⟨by rw [hf], ?_, ?_⟩
  · rw [hf]
    exact pin_complete i.snapshot i.readRecords hg.2.2.1.2.2.2
  · intro later r d mem changed
    have stale : ¬PinFresh later f := by
      intro h
      exact changed (h.2 (r, d) mem).2
    refine ⟨stale, ?_⟩
    intro cs v
    simp [consume, stale, markFocus]

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

private theorem band_order_injective (a b : Band) : a.order = b.order ↔ a = b := by
  cases a <;> cases b <;> decide

private theorem rowLE_iff (a b : PriorityRow) : rowLE a b = true ↔
    a.band.order < b.band.order ∨ (a.band.order = b.band.order ∧
      (a.passRank < b.passRank ∨ (a.passRank = b.passRank ∧ a.sourceIndex ≤ b.sourceIndex))) := by
  simp [rowLE, band_order_injective]

private theorem rowLE_total (a b : PriorityRow) : rowLE a b = true ∨ rowLE b a = true := by
  simp only [rowLE_iff]
  omega

private theorem rowLE_trans (a b c : PriorityRow)
    (ab : rowLE a b = true) (bc : rowLE b c = true) : rowLE a c = true := by
  simp only [rowLE_iff] at *
  omega

private theorem insert_perm (r : PriorityRow) (xs : List PriorityRow) :
    (insertRow r xs).Perm (r :: xs) := by
  induction xs with
  | nil => exact .refl _
  | cons x xs ih =>
    simp only [insertRow]
    split
    · exact .refl _
    · exact (ih.cons x).trans (.swap _ _ _)

private theorem sort_perm (xs : List PriorityRow) : (sortRows xs).Perm xs := by
  induction xs with
  | nil => exact .refl _
  | cons x xs ih => exact (insert_perm x _).trans (ih.cons x)

private theorem insert_sorted (r : PriorityRow) (xs : List PriorityRow)
    (sorted : xs.Pairwise (fun a b => rowLE a b = true)) :
    (insertRow r xs).Pairwise (fun a b => rowLE a b = true) := by
  induction xs with
  | nil => simp [insertRow]
  | cons x xs ih =>
    obtain ⟨head, tail⟩ := List.pairwise_cons.mp sorted
    simp only [insertRow]
    split
    next hr =>
      apply List.pairwise_cons.mpr
      refine ⟨?_, List.pairwise_cons.mpr ⟨head, tail⟩⟩
      intro y hy
      simp only [List.mem_cons] at hy
      rcases hy with rfl | hy
      · exact hr
      · exact rowLE_trans r x y hr (head y hy)
    next hr =>
      apply List.pairwise_cons.mpr
      refine ⟨?_, ih tail⟩
      intro y hy
      have mem := (insert_perm r xs).mem_iff.mp hy
      simp only [List.mem_cons] at mem
      rcases mem with rfl | mem
      · exact (rowLE_total y x).resolve_left hr
      · exact head y mem

private theorem sort_sorted (xs : List PriorityRow) :
    (sortRows xs).Pairwise (fun a b => rowLE a b = true) := by
  induction xs with
  | nil => exact .nil
  | cons x xs ih => exact insert_sorted x _ ih

private theorem projection_rows (rows : List PassRow) (f : Option FocusRecord) (cutoff : Nat) :
    (priorityProjection rows f cutoff).map (·.row) = sortRows (rawRows rows f) := by
  apply List.ext_getElem?
  intro i
  simp [priorityProjection, Option.map_map, Function.comp_def]

/-- VERIFY obligation 2, "pass/priorities ... reaches the reader": Giraffe
orders focus before associated before elsewhere before unknown, preserving
pass rank within bands. Missing relation is explicitly unknown, never band 0. -/
theorem giraffe_band_respects_focus (rows : List PassRow) (f : Option FocusRecord) (cutoff : Nat) :
    ProjectionCorrect rows f cutoff (priorityProjection rows f cutoff) := by
  refine ⟨?_, ?_, ?_, ?_⟩
  · rw [projection_rows]
    exact sort_perm _
  · rw [projection_rows]
    exact sort_sorted _
  · intro r hr missing
    have mem : r.row ∈ (priorityProjection rows f cutoff).map (·.row) := List.mem_map.mpr ⟨r, hr, rfl⟩
    rw [projection_rows] at mem
    have raw := (sort_perm _).mem_iff.mp mem
    simp only [rawRows, List.mem_mapIdx] at raw
    obtain ⟨index, hi, he⟩ := raw
    rw [← he] at missing ⊢
    simp only at missing
    simp [missing, band]
  · intro index r hr
    simp only [priorityProjection, List.getElem?_mapIdx] at hr
    cases he : (sortRows (rawRows rows f))[index]? with
    | none => simp [he] at hr
    | some row =>
      simp only [he, Option.map_some, Option.some.injEq] at hr
      rw [← hr]

def FailureVisible (why : Failure) (consumer : Consumer) : Prop :=
  consumer.active = none ∧ consumer.failure = some why ∧
    consumer.view.header = some .focusAbsent ∧ consumer.view.sources.focusVersion = none

/-- VERIFY objection 5, "show how the consumer sees that condition": failed
split/retry/readiness/validation runs are typed absences with focusAbsent,
not successful empty focus. A stale pin separately shows sourceChangedSincePass. -/
theorem monkey_failure_is_typed (cs : List Circus.Character) (snap : Snapshot)
    (v : ViewState) (why : Failure) :
    FailureVisible why (consume cs snap v (.failed why)) := by
  simp [FailureVisible, consume, markFocus, render]

/-! The published header/link embedding preserves LoopBuilt. -/
namespace PublishedExample
private def a : Item := ⟨901, .mission, "M-toy-a", true⟩
private def b : Item := ⟨902, .mission, "M-toy-b", true⟩
private def field : GCCRecord := ⟨1, .turnText, some 7⟩
private def header : GCCRecord := ⟨2, .houFocus, some 7⟩
private def ma : GCCRecord := ⟨3, .mention, some 7⟩
private def mb : GCCRecord := ⟨4, .mention, some 7⟩
private def la : GCCRecord := ⟨5, .houFocusRelation, some 7⟩
private def lb : GCCRecord := ⟨6, .houFocusRelation, some 7⟩
private def graph : GCC :=
  ⟨[a,b], [field,header,ma,mb,la,lb], [], [], fun r role =>
    match role with
    | .item => if r.id == 3 || r.id == 5 then some (.item a)
        else if r.id == 4 || r.id == 6 then some (.item b) else none
    | .subject => if r.id == 2 || r.id == 3 || r.id == 4 then some (.record field)
        else if r.id == 5 || r.id == 6 then some (.record header) else none
    | _ => none, []⟩
private theorem ab : graph.Reach 4 (.item a) (.item b) := by
  exact .step 3 _ (.record ma) _ (by unfold GCC.adj; decide)
    (.step 2 _ (.record field) _ (by unfold GCC.adj; decide)
      (.step 1 _ (.record mb) _ (by unfold GCC.adj; decide)
        (.step 0 _ (.item b) _ (by unfold GCC.adj; decide) (.refl _))))
private theorem ba : graph.Reach 4 (.item b) (.item a) := by
  exact .step 3 _ (.record mb) _ (by unfold GCC.adj; decide)
    (.step 2 _ (.record field) _ (by unfold GCC.adj; decide)
      (.step 1 _ (.record ma) _ (by unfold GCC.adj; decide)
        (.step 0 _ (.item a) _ (by unfold GCC.adj; decide) (.refl _))))
private theorem bounds : ∀ x ∈ [a,b], ∀ y ∈ [a,b], graph.Reach 4 (.item x) (.item y) := by
  intro x hx y hy
  simp only [List.mem_cons, List.not_mem_nil, or_false] at hx hy
  rcases hx with rfl | rfl <;> rcases hy with rfl | rfl
  · exact .more _ _ _ (.more _ _ _ (.more _ _ _ (.more _ _ _ (.refl _))))
  · exact ab
  · exact ba
  · exact .more _ _ _ (.more _ _ _ (.more _ _ _ (.more _ _ _ (.refl _))))
example : turnItems graph 7 [] = [a,b] := by decide
example : LoopBuilt graph (turnItems graph 7 []) 4 := by
  change LoopBuilt graph [a,b] 4
  refine ⟨?_, ?_, ⟨?_, ?_⟩, bounds⟩
  · intro r hr hi
    simp only [graph, List.mem_cons, List.not_mem_nil, or_false] at hr
    rcases hr with rfl | rfl | rfl | rfl | rfl | rfl
    · cases hi
    · cases hi
    · exact ⟨a, by simp [graph], by decide⟩
    · exact ⟨b, by simp [graph], by decide⟩
    · exact ⟨a, by simp [graph], by decide⟩
    · exact ⟨b, by simp [graph], by decide⟩
  · intro r hr
    simp only [graph, List.mem_cons, List.not_mem_nil, or_false] at hr
    rcases hr with rfl | rfl | rfl | rfl | rfl | rfl <;>
      refine ⟨by decide, ?_⟩ <;> intro role n hn <;> cases role <;>
      simp [graph, field, header, ma, mb, la, lb] at hn <;> subst n <;>
      simp [EndpointFits, GCC.Mem, GCC.memBool, graph, field, header, ma, mb, la, lb]
  · intro x hx; exact hx
  · intro x hx y hy; exact ⟨4, bounds x hx y hy⟩
end PublishedExample

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
