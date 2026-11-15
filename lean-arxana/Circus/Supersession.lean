import Circus.LateWrites

/-!
# VERIFY pre-specification: retained interpretations and reader corrections

Extends LateWrites.SourceRecord (including its revision and retraction payload)
and Freshness.ViewState. Generation snapshots retain references, not another
copy of the source-record model. The journal is projected by rebuildGraph;
delivery time, attempts and job id remain excluded from semantic comparison.

The generation pointer and asynchronous artifact are deliberately distinct.
Freshness.advance still uses step/applyEvent/rebuild: incremental_eq_rebuilt
and primary_up_to_date_top_ten concern the logical view, not the file consumer.
Document/landing behavior remains docChange_stales, readingLanded_unstales and
giraffe_firing_rule, with Freshness's source-digest guard. This module adds the
missing citation/status consumer adapter; it does not assert live conformance.
In particular, an operator-withdrawn error string alone is not this adapter.

Generation publication in corpus_generation.clj retains old directories and
refuses duplicate item ids. Here immutable snapshots retain historical current
sets; the active ledger changes their statuses without rewriting those snapshots.
The per-key source revision allocator remains the obligation from LateWrites.
-/
namespace Circus.Supersession
open Arxana LateWrites Freshness

inductive ReadingStatus
  | current
  | superseded (replacement : SourceRecord) (reason : String)
  | rejected (reason : String)
  | withdrawn (operator : String) (reason : String)
  deriving DecidableEq, Repr
structure Reading where
  item : EntityId
  digest : Nat
  record : SourceRecord
  status : ReadingStatus
  deriving DecidableEq, Repr
structure Generation where
  run : Nat
  readings : List Reading
  deriving DecidableEq, Repr
structure Store where
  rows : List Reading
  ever : List SourceRecord
  journal : List Delivery
  generations : List Generation
  run : Nat
  deriving DecidableEq, Repr

def empty : Store := ⟨[], [], [], [⟨0, []⟩], 0⟩
def lookup (s : Store) (r : SourceRecord) : Option Reading :=
  s.rows.find? (fun row => row.record == r)
def currentRows (s : Store) : List Reading := s.rows.filter (·.status == .current)
def projection (s : Store) : SemanticGraph := rebuildGraph ⟨[], 0⟩ s.journal
/-- An independent ever-landed/rejected inventory detects deletion from history.
The source record AND its latest status/reason must remain retrievable. -/
def Queryable (s : Store) : Prop :=
  ∀ r ∈ s.ever, (∃ row, lookup s r = some row ∧ row.record = r) ∧
    (projection s).history r = true

def commitGeneration (s : Store) : Store :=
  { s with run := s.run + 1
           generations := s.generations ++ [⟨s.run + 1, currentRows s⟩] }
def appendRecord (s : Store) (r : SourceRecord) : Store :=
  { s with ever := s.ever ++ [r]
           journal := s.journal ++ [⟨r, ⟨0, 0, "abstract-publication"⟩⟩] }
def freshRecord (s : Store) (r : SourceRecord) : Bool :=
  r.key.animal == .giraffe && r.key.slot == .known .giraffeReading &&
  s.journal.all (fun d => d.source.key != r.key || d.source.revision < r.revision)

def land (s : Store) (i : EntityId) (digest : Nat) (r : SourceRecord)
    (why : String) : Store :=
  match r.payload with
  | .success _ =>
    if freshRecord s r then
      commitGeneration (appendRecord { s with rows :=
        (s.rows.map fun old => if old.item == i && old.status == .current then
          { old with status := .superseded r why } else old) ++
        [⟨i, digest, r, .current⟩] } r)
    else s
  | _ => s

def reject (s : Store) (i : EntityId) (digest : Nat) (r : SourceRecord) : Store :=
  match r.payload with
  | .failure why =>
    if freshRecord s r then
      commitGeneration (appendRecord { s with rows := s.rows ++ [⟨i, digest, r, .rejected why⟩] } r)
    else s
  | _ => s

/-- Retraction is a newer source tombstone, never deletion. Its operator and
reason are structured status on the withdrawn reading, not an opaque error. -/
def withdraw (s : Store) (old : SourceRecord) (operator why : String) : Store :=
  match lookup s old with
  | none => s
  | some row =>
    let rev := s.journal.foldl (fun n d =>
      if d.source.key == old.key then max n d.source.revision else n) old.revision
    let tombstone : SourceRecord := ⟨old.key, rev + 1, .retract why⟩
    if row.status == .current then
      commitGeneration { (appendRecord s tombstone) with
        -- Tombstones are retained sources, not newly landed/rejected readings.
        ever := s.ever
        rows := s.rows.map fun q => if q.record == old then
          { q with status := .withdrawn operator why } else q }
    else s

inductive ReachableStore : Store → Prop
  | initial : ReachableStore empty
  | landed {s} (h : ReachableStore s) (i digest r why) : ReachableStore (land s i digest r why)
  | rejected {s} (h : ReachableStore s) (i digest r) : ReachableStore (reject s i digest r)
  | withdrawn {s} (h : ReachableStore s) (r operator why) : ReachableStore (withdraw s r operator why)

/-- Obligation 4: "Rejected and superseded readings must remain queryable".
Generation switches preserve the source inventory and structured status. -/
theorem history_queryable (s : Store) (h : ReachableStore s) : Queryable s := by sorry

/-- Obligation 2: "showing how it supersedes the earlier interpretation".
Every displaced current reading remains, with the replacement pointer/reason. -/
theorem superseded_stays_queryable (s : Store) (old : Reading)
    (present : lookup s old.record = some old) (active : old.status = .current)
    (i digest : Nat) (sameItem : old.item = i) (r : SourceRecord) (why value : String)
    (fresh : freshRecord s r = true) (success : r.payload = .success value) :
    lookup (land s i digest r why) old.record =
      some { old with status := .superseded r why } := by sorry

inductive Recovery
  | unexplored
  | explored (history : List Reading)
  deriving DecidableEq, Repr

def recover (s : Store) (i : EntityId) : Recovery :=
  let rows := s.rows.filter (·.item == i)
  if rows.isEmpty then .unexplored else .explored rows

def denied (r : Reading) : Bool := match r.status with
  | .rejected _ | .withdrawn _ _ => true
  | _ => false
/-- Source changes permit another attempt, but never erase the explored label. -/
def retryAsNew (s : Store) (i : EntityId) (digest : Nat) : Bool :=
  !(s.rows.any fun row => row.item == i && row.digest == digest && denied row)

/-- Obligation 4: "distinguish unexplored paths from previously rejected ones".
Rejection/withdrawal retains explored history and blocks a fresh-path retry at
that digest; changing the digest is necessary, not sufficient, for retry. -/
theorem rejected_distinguishes_from_unexplored (s : Store) (row : Reading)
    (present : row ∈ s.rows) (deniedRow : denied row = true) :
    recover s row.item ≠ .unexplored ∧ retryAsNew s row.item row.digest = false ∧
    (∀ digest, retryAsNew s row.item digest = true → digest ≠ row.digest) := by sorry

/-- Citation comes from the artifact's captured generation, never the latest
pointer substituted into an old artifact. Missing citations fail closed. -/
def citation (s : Store) (run : Nat) (i : EntityId) : Option SourceRecord := do
  let gen ← s.generations.find? (·.run == run)
  let row ← gen.readings.find? (·.item == i)
  pure row.record

def citationCurrent (s : Store) (run : Nat) (i : EntityId) : Bool :=
  ((citation s run i).bind (lookup s)).any (·.status == .current)
structure Reader where
  view : ViewState
  history : List Reading

/-- All noncurrent rows are explicit qualified history, with status/reason.
Freshness's original stale/failure annotations stay intact. An invalid citation
adds a stale mark, so Freshness.Consumable itself rejects it. -/
def renderReader (s : Store) (v : ViewState) : Reader :=
  let base := render v
  let blocked := match base.artifact with
    | none => []
    | some a => a.entries.filterMap fun e =>
      if citationCurrent s a.runId e.item then none else some (e.item, StaleReason.sourceChangedSincePass)
  ⟨{ base with marks := base.marks ++ blocked }, s.rows.filter (·.status != .current)⟩

def ReaderSafe (s : Store) (reader : Reader) : Prop :=
  (∀ a e, Consumable reader.view a e → citationCurrent s a.runId e.item = true) ∧
  (∀ row ∈ s.rows, row.status ≠ .current → row ∈ reader.history)

/-- Obligation 2: "reaches the reader", amended: only CONSUMABLE ranking
citations must be current. Superseded/withdrawn readings remain qualified
history; no immediate successful refresh is asserted. -/
theorem reader_sees_replacement (s : Store) (v : ViewState) :
    ReaderSafe s (renderReader s v) := by sorry

inductive Correction
  | replace (item : EntityId) (digest : Nat) (record : SourceRecord) (reason : String)
  | withdraw (record : SourceRecord) (operator reason : String)
def correct (s : Store) : Correction → Store
  | .replace i d r why => land s i d r why
  | .withdraw r operator why => withdraw s r operator why

def correctionEvent (s : Store) (v : ViewState) : InputEvent :=
  .metadata s.run (v.sources.passInputs + 1) v.sources.pendingIndex
    v.sources.lexicon v.sources.focusVersion

def observeCorrection (cs : List Circus.Character) (s : Store) (v : ViewState)
    (c : Correction) : Reader :=
  let next := correct s c
  renderReader next (advance cs v (correctionEvent next v))

/-- Every displaced reading has its explicit correction status visible and
cannot be consumed through the retained old artifact. This also holds while
refresh fails; successful replacement publication uses Freshness.finish. -/
def CorrectionVisible (before after : Store) (reader : Reader) : Prop :=
  ∀ old ∈ before.rows, old.status = .current →
    ∀ row, lookup after old.record = some row → row.status ≠ .current →
      row ∈ reader.history ∧
      ∀ a e, citation after a.runId e.item = some old.record →
        ¬Consumable reader.view a e

/-- Obligation 2: "a correction or rejection ... reaches the reader";
obligation 3's freshness deadline is reused verbatim via Timely. At delivery,
structured correction/withdrawal status blocks the old basis. This guarantees
visibility within T, not successful regeneration within T. The adapter must be
called for every correction; Timely is still owed by the live integration. -/
theorem correction_reaches_reader (cs : List Circus.Character) (s : Store)
    (v : ViewState) (c : Correction) (T occurred observed : Time)
    (timely : Timely T [⟨occurred, observed, correctionEvent (correct s c) v⟩]) :
    observed ≤ occurred + T ∧
      CorrectionVisible s (correct s c) (observeCorrection cs s v c) := by sorry

def OneCurrent (rows : List Reading) : Prop :=
  (rows.filter (·.status == .current) |>.map (·.item)).Nodup

/-- Obligation 2's replacement and the corpus duplicate refusal: at most one
current reading per item, both in the active ledger and each saved generation.
Old snapshots remain snapshots; their current rows are not current globally. -/
theorem one_current_per_item (s : Store) (h : ReachableStore s) :
    OneCurrent s.rows ∧ ∀ gen ∈ s.generations, OneCurrent gen.readings := by sorry

/-! ## TEETH: defective stores/adapters violate the registered predicates
These invented bad states are outside the transition discipline. -/
namespace Teeth
private def key : LateWrites.Key := ⟨7, .giraffe, .known .giraffeReading⟩
private def old : SourceRecord := ⟨key, 1, .success "old"⟩
private def new : SourceRecord := ⟨key, 2, .success "corrected"⟩
private def bad : SourceRecord := ⟨key, 3, .failure "review rejected"⟩
private def first := land empty 1 10 old "first"
private def replaced := land first 1 10 new "correction"
private def rejected := reject empty 1 10 bad
private def withdrawn := withdraw first old "operator-example" "withdrawal"
private def deleted : Store := { replaced with rows := replaced.rows.filter (·.record != old) }

-- 1. Keeping files/journal but deleting the status index is not queryability.
example : ¬Queryable deleted := by
  intro h
  have found := (h old (by decide)).1
  obtain ⟨row, hr, _⟩ := found
  have absent : lookup deleted old = none := by decide
  rw [absent] at hr
  cases hr
-- 2. Deleting the old row loses the replacement pointer and reason.
example : lookup deleted old = none ∧
    lookup replaced old = some ⟨1, 10, old, .superseded new "correction"⟩ := by decide
-- 3. A deleted rejected history becomes indistinguishable from never explored.
example : recover { rejected with rows := [] } 1 = .unexplored ∧
    recover rejected 1 ≠ .unexplored ∧ retryAsNew rejected 1 10 = false ∧
    retryAsNew rejected 1 11 = true := by decide

private def inp : Inputs := ⟨[], [(1, 10)], fun _ => 0, fun _ _ => false, fun _ => [], [], 0⟩
private def basis : DisplayBasis := ⟨1, 1, [(1, 10)], 1, 1, some 1⟩
private def entry : Entry := ⟨1, 1, ⟨["old"], .thor, [.lit "old"], .thor⟩⟩
private def artifact : Artifact := ⟨1, 0, some basis, [entry], [1]⟩
private def view : ViewState := { initial ⟨0, inp, [], 0, 0⟩ basis with artifact := some artifact }
private def badReader : Reader := ⟨view, []⟩
-- 4. A digest-current file can still cite a superseded interpretation.
example : ¬ReaderSafe replaced badReader := by
  intro h
  have hc : Consumable badReader.view artifact entry := by
    refine ⟨rfl, by simp [artifact], rfl, rfl, rfl, ?_⟩
    intro r hr
    cases hr
  have no : citationCurrent replaced artifact.runId entry.item = false := by decide
  have yes := h.1 artifact entry hc
  rw [no] at yes
  cases yes
-- 5. Recording withdrawal without informing the consumer leaves old knowledge usable.
example : ¬CorrectionVisible first withdrawn badReader := by
  intro h
  have missing := (h ⟨1, 10, old, .current⟩ (by decide) rfl
    ⟨1, 10, old, .withdrawn "operator-example" "withdrawal"⟩ (by decide) (by decide)).1
  cases missing
-- 6. Two successful records for one item violate duplicate refusal.
example : ¬OneCurrent [⟨1, 10, old, .current⟩, ⟨1, 10, new, .current⟩] := by
  unfold OneCurrent
  decide
-- Retraction uses the existing semantic projection and retains its old source.
example : (projection withdrawn).history old = true ∧
    (projection withdrawn).absence key (.retracted "withdrawal") = true := by decide
-- The corrected adapter exposes the status and blocks the old citation even
-- when the underlying artifact still passes the original digest checks.
example : citationCurrent replaced 1 1 = false ∧
    Visible (renderReader replaced view).view 1 .sourceChangedSincePass ∧
    ⟨1, 10, old, .superseded new "correction"⟩ ∈ (renderReader replaced view).history := by
  unfold Visible
  decide
private def characters : List Circus.Character := [⟨.thor, fun _ => [entry]⟩]
private def notified := observeCorrection characters first view (.replace 1 10 new "correction")
private def refreshed := renderReader replaced (finish characters true notified.view)
-- Only after successful refresh does the artifact cite the replacement run.
example : notified.view.artifact.map Artifact.runId = some 1 ∧
    refreshed.view.artifact.map Artifact.runId = some 2 ∧
    citation replaced 2 1 = some new ∧ citationCurrent replaced 2 1 = true ∧
    refreshed.view.marks = [] := by decide
end Teeth
end Circus.Supersession

/-! ## Counterexamples refuting the first wording of statements 4 and 5
Immediate replacement visibility contradicts the accepted asynchronous model.
-/

-- Invented one-item example. No unproved registry theorem is used.
namespace SupersessionTiming
open Circus Circus.Freshness
private def inp : Inputs :=
  ⟨[⟨1, .mission, "M-example", true⟩], [(1, 1)], fun _ => 0,
    fun _ _ => false, fun _ => [], [], 0⟩
private def loop : State := ⟨0, inp, [], 0, 0⟩
private def basis : DisplayBasis := ⟨1, 1, [(1, 1)], 1, 1, some 1⟩
private def cs : List Circus.Character :=
  [⟨.thor, fun _ => [⟨1, 1, ⟨["old reading"], .thor, [.lit "old reading"], .thor⟩⟩]⟩]
private def seed := initial loop basis
private def begun := advance cs seed (.metadata 1 1 1 1 (some 1))
private def published := advance cs begun (.refreshDone true)
private def changed := advance cs published (.metadata 2 2 1 1 (some 1))

-- This state is actually reachable; no manually forged ViewState.
example : Reachable cs changed := by
  apply Reachable.next
  apply Reachable.next
  apply Reachable.next
  apply Reachable.initial
  · intro it hi
    simp [loop, inp] at hi
    subst it
    exact ⟨1, by decide⟩
  · decide

-- The new run is current at the source, but the displayed run and its entry
-- still belong to run 1. The consumer sees a stale mark, not the replacement.
example : changed.sources.runId = 2 ∧
    changed.artifact.map Artifact.runId = some 1 ∧
    changed.artifact.map (fun a => a.entries.map (·.item)) = some [1] ∧
    changed.artifact.map (fun a => a.entries.map (fun e => e.reason.terms)) =
      some [["old reading"]] ∧
    changed.header = some .sourceChangedSincePass ∧
    Visible changed 1 .sourceChangedSincePass := by
  unfold Visible
  decide

-- Timely bounds delivery, not successful refresh. A failed refresh retains
-- the old run too, with a typed failure rather than replacement knowledge.
example : Timely 5 [⟨10, 11, .metadata 2 2 1 1 (some 1)⟩] := by
  constructor
  · simp
  · intro e he
    simp at he
    subst e
    decide
example : (advance cs changed (.refreshDone false)).artifact.map Artifact.runId = some 1 ∧
    (advance cs changed (.refreshDone false)).header = some .refreshFailed := by
  decide
end SupersessionTiming
