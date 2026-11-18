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

private theorem map_records (rows : List Reading) (f : Reading → Reading)
    (hf : ∀ q, (f q).record = q.record) : (rows.map f).map Reading.record = rows.map Reading.record := by
  simp only [List.map_map]
  congr 1
  funext q
  exact hf q

private def Retained (s : Store) : Prop :=
  ∀ r ∈ s.ever, r ∈ s.rows.map Reading.record ∧ r ∈ semanticRecords s.journal

private theorem retained_queryable (s : Store) (h : Retained s) : Queryable s := by
  intro r hr
  obtain ⟨rows, journal⟩ := h r hr
  refine ⟨?_, ?_⟩
  · cases he : lookup s r with
    | none =>
      have hn := List.find?_eq_none.mp he
      obtain ⟨row, hm, eq⟩ := List.mem_map.mp rows
      exact False.elim ((hn row hm) (by simpa using eq))
    | some row =>
      exact ⟨row, rfl, by simpa using List.find?_some he⟩
  · simpa [projection, rebuildGraph] using journal

private theorem retained_transform (s : Store) (h : Retained s) (f : Reading → Reading)
    (hf : ∀ q, (f q).record = q.record) : Retained {s with rows := s.rows.map f} := by
  intro r hr
  simpa only [map_records _ _ hf] using h r hr

private theorem retained_append (s : Store) (h : Retained s) (row : Reading) :
    Retained (appendRecord {s with rows := s.rows ++ [row]} row.record) := by
  intro r hr
  simp only [appendRecord, List.mem_append, List.mem_singleton] at hr
  rcases hr with hr | rfl
  · obtain ⟨hm, hj⟩ := h r hr
    constructor
    · simp only [appendRecord, List.map_append, List.mem_append]
      exact Or.inl hm
    · simp only [appendRecord, semanticRecords, List.map_append, List.mem_append]
      exact Or.inl hj
  · constructor <;> simp [appendRecord, semanticRecords, eraseOperational]

private theorem retained_commit (s : Store) (h : Retained s) : Retained (commitGeneration s) := h

private theorem retained_reachable (s : Store) (h : ReachableStore s) : Retained s := by
  induction h with
  | initial => simp [Retained, empty]
  | @landed s h i digest r why ih =>
    unfold land
    split <;> try exact ih
    split <;> try exact ih
    apply retained_commit
    exact retained_append _ (retained_transform s ih _ (by intro q; split <;> rfl)) _
  | @rejected s h i digest r ih =>
    unfold reject
    split <;> try exact ih
    split <;> try exact ih
    exact retained_commit _ (retained_append s ih _)
  | @withdrawn s h r operator why ih =>
    unfold withdraw
    split <;> try exact ih
    dsimp only
    split <;> try exact ih
    apply retained_commit
    intro x hx
    obtain ⟨hm, hj⟩ := ih x hx
    constructor
    · rw [map_records _ _ (by intro q; split <;> rfl)]
      exact hm
    · simp only [appendRecord, semanticRecords, List.map_append, List.mem_append]
      exact Or.inl hj

/-- Obligation 4: "Rejected and superseded readings must remain queryable".
Generation switches preserve the source inventory and structured status. -/
theorem history_queryable (s : Store) (h : ReachableStore s) : Queryable s := by
  exact retained_queryable s (retained_reachable s h)

/-- Obligation 2: "showing how it supersedes the earlier interpretation".
Every displaced current reading remains, with the replacement pointer/reason. -/
theorem superseded_stays_queryable (s : Store) (old : Reading)
    (present : lookup s old.record = some old) (active : old.status = .current)
    (i digest : Nat) (sameItem : old.item = i) (r : SourceRecord) (why value : String)
    (fresh : freshRecord s r = true) (success : r.payload = .success value) :
    lookup (land s i digest r why) old.record =
      some { old with status := .superseded r why } := by
  let f : Reading → Reading := fun q =>
    if q.item == i && q.status == .current then { q with status := .superseded r why } else q
  have hf : ∀ q, (f q).record = q.record := by intro q; unfold f; split <;> rfl
  have hp : (fun q => (f q).record == old.record) = (fun q => q.record == old.record) := by
    funext q; rw [hf]
  change s.rows.find? (fun q => q.record == old.record) = some old at present
  simp only [land, success, fresh, ↓reduceIte, lookup, commitGeneration, appendRecord]
  change ((s.rows.map f) ++ _).find? _ = _
  rw [List.find?_append, List.find?_map]
  change ((s.rows.find? (fun q => (f q).record == old.record)).map f).or _ = _
  rw [hp, present]
  simp [f, sameItem, active]

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
    (∀ digest, retryAsNew s row.item digest = true → digest ≠ row.digest) := by
  have hm : row ∈ s.rows.filter (·.item == row.item) := by simp [present]
  have hn : (s.rows.filter (·.item == row.item)).isEmpty = false := by
    cases he : s.rows.filter (·.item == row.item) with
    | nil => simp [he] at hm
    | cons x xs => rfl
  have hr : retryAsNew s row.item row.digest = false := by
    simp only [retryAsNew, Bool.not_eq_false', List.any_eq_true]
    exact ⟨row, present, by simp [deniedRow]⟩
  refine ⟨?_, hr, ?_⟩
  · simp [recover, hn]
  · intro digest h eq
    subst digest
    rw [hr] at h
    cases h

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
    ReaderSafe s (renderReader s v) := by
  constructor
  · intro a e hc
    have ha : (render v).artifact = some a := hc.1
    have he := hc.2.1
    by_cases hk : citationCurrent s a.runId e.item = true
    · exact hk
    · exfalso
      apply hc.2.2.2.2.2 .sourceChangedSincePass
      simp only [Visible, renderReader, ha]
      apply List.mem_append_right
      apply List.mem_filterMap.mpr
      exact ⟨e, he, by simp [hk]⟩
  · intro row hr hn
    simp [renderReader, hr, hn]

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
      CorrectionVisible s (correct s c) (observeCorrection cs s v c) := by
  refine ⟨(timely.2 ⟨occurred, observed, correctionEvent (correct s c) v⟩ (by simp)).2, ?_⟩
  intro old ho active row found noncurrent
  have hs := reader_sees_replacement (correct s c) (advance cs v (correctionEvent (correct s c) v))
  refine ⟨hs.2 row (List.mem_of_find?_eq_some found) noncurrent, ?_⟩
  intro a e cited consumed
  have hc := hs.1 a e consumed
  simp [citationCurrent, cited, found, noncurrent] at hc

def OneCurrent (rows : List Reading) : Prop :=
  (rows.filter (·.status == .current) |>.map (·.item)).Nodup

private theorem current_map_sublist (rows : List Reading) (f : Reading → Reading)
    (items : ∀ q, (f q).item = q.item)
    (active : ∀ q, (f q).status = .current → q.status = .current) :
    List.Sublist ((rows.map f).filter (·.status == .current) |>.map (·.item))
      (rows.filter (·.status == .current) |>.map (·.item)) := by
  induction rows with
  | nil => simp
  | cons q qs ih =>
    by_cases hf : (f q).status = .current
    · have hq := active q hf
      simpa [hf, hq, items] using ih.cons₂ q.item
    · by_cases hq : q.status = .current
      · simpa [hf, hq] using ih.cons q.item
      · simpa [hf, hq] using ih

private theorem one_current_map (rows : List Reading) (h : OneCurrent rows)
    (f : Reading → Reading) (items : ∀ q, (f q).item = q.item)
    (active : ∀ q, (f q).status = .current → q.status = .current) :
    OneCurrent (rows.map f) :=
  h.sublist (current_map_sublist rows f items active)

private theorem one_current_filter (s : Store) (h : OneCurrent s.rows) :
    OneCurrent (currentRows s) := by
  simpa [OneCurrent, currentRows, List.filter_filter] using h

private theorem unique_commit (s : Store)
    (h : OneCurrent s.rows ∧ ∀ gen ∈ s.generations, OneCurrent gen.readings) :
    OneCurrent (commitGeneration s).rows ∧
      ∀ gen ∈ (commitGeneration s).generations, OneCurrent gen.readings := by
  refine ⟨h.1, ?_⟩
  intro gen hg
  simp only [commitGeneration, List.mem_append, List.mem_singleton] at hg
  rcases hg with hg | rfl
  · exact h.2 gen hg
  · exact one_current_filter s h.1

private theorem unique_land_rows (rows : List Reading) (h : OneCurrent rows)
    (i digest : Nat) (r : SourceRecord) (why : String) :
    OneCurrent ((rows.map fun old => if old.item == i && old.status == .current then
      {old with status := .superseded r why} else old) ++ [⟨i, digest, r, .current⟩]) := by
  let f : Reading → Reading := fun old => if old.item == i && old.status == .current then
    {old with status := .superseded r why} else old
  have hm : OneCurrent (rows.map f) := one_current_map rows h f
    (by intro q; unfold f; split <;> rfl)
    (by intro q; unfold f; split <;> simp_all)
  have hn : i ∉ ((rows.map f).filter (·.status == .current) |>.map (·.item)) := by
    intro hi
    obtain ⟨q, hq, eq⟩ := List.mem_map.mp hi
    obtain ⟨hq, hc⟩ := List.mem_filter.mp hq
    obtain ⟨old, ho, rfl⟩ := List.mem_map.mp hq
    unfold f at hc eq
    split at hc <;> simp_all
  change OneCurrent (rows.map f ++ _)
  simp only [OneCurrent, List.filter_append, List.filter_cons, beq_self_eq_true,
    List.filter_nil, ↓reduceIte, List.map_append, List.map_cons, List.map_nil]
  apply List.nodup_append.mpr
  refine ⟨hm, by simp, ?_⟩
  intro a ha b hb
  simp only [List.mem_singleton] at hb
  subst b
  intro eq
  exact hn (eq ▸ ha)

/-- Obligation 2's replacement and the corpus duplicate refusal: at most one
current reading per item, both in the active ledger and each saved generation.
Old snapshots remain snapshots; their current rows are not current globally. -/
theorem one_current_per_item (s : Store) (h : ReachableStore s) :
    OneCurrent s.rows ∧ ∀ gen ∈ s.generations, OneCurrent gen.readings := by
  induction h with
  | initial => simp [empty, OneCurrent]
  | @landed s h i digest r why ih =>
    unfold land
    split <;> try exact ih
    split <;> try exact ih
    apply unique_commit
    exact ⟨unique_land_rows s.rows ih.1 i digest r why, ih.2⟩
  | @rejected s h i digest r ih =>
    unfold reject
    split <;> try exact ih
    split <;> try exact ih
    apply unique_commit
    refine ⟨?_, ih.2⟩
    simpa [appendRecord, OneCurrent] using ih.1
  | @withdrawn s h r operator why ih =>
    unfold withdraw
    split <;> try exact ih
    dsimp only
    split <;> try exact ih
    apply unique_commit
    refine ⟨?_, ih.2⟩
    apply one_current_map s.rows ih.1
    · intro q; split <;> rfl
    · intro q; split <;> simp_all

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
-- CL-56: the first publication is triggered by a real version change.
private def seed := initial loop { basis with runId := 0 }
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
