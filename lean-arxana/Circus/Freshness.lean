import Circus.Party

/-!
# VERIFY pre-specification: freshness at the file consumer

Proposed design, not conformance of today's party.json. The existing State, Event, applyEvent, step and rebuild remain the logical ranking model; this
layer adds publication, a digest inventory, failure visibility and coalescing.
`incremental_eq_rebuilt` and `primary_up_to_date_top_ten` still concern
`loop.shown`, not an asynchronously published file. Failure banners below are
outside the ranked ten, so displacement from the ranking cannot hide failure.

Digest tokens are Nat equality witnesses, not timestamps. A document token
also serves as Basic's abstract version. `docChange_stales` and
`giraffe_firing_rule` apply to the reused logical step. A matching landing
uses `readingLanded_unstales`; mismatched landings never enter that step.
A consumer failure latch is NEW: reread_queue/requeue! clears last_error, so that queue row alone cannot implement failure persistence.

Observation times below are consumer delivery times. DERIVE still owes a
numerical wall-clock bound from source mutation to consumer delivery and an
independent watchdog when the service stops. The model must not be reported
as proving that missing live integration. Failure visibility is immediate
on delivery; successful regeneration additionally requires successful workers.
-/
namespace Circus.Freshness
open Arxana

/-- Complete canonical inventory actually read, including VERIFY's focus version. -/
structure DisplayBasis where
  runId : Nat
  passInputs : Nat
  documents : List (EntityId × Nat)
  pendingIndex : Nat
  lexicon : Nat
  focusVersion : Option Nat
  deriving DecidableEq, Repr

inductive StaleReason
  | sourceChangedSincePass | readingPending | readingFailedAfterMaxAttempts
  | readerSeatStopped | focusAbsent | refreshFailed
  deriving DecidableEq, Repr
inductive RequestState | pending | claimed | landed | failed | superseded
  deriving DecidableEq, Repr
structure Request where
  item : EntityId
  source : Nat
  state : RequestState
  attempts : Nat
  maxAttempts : Nat
  firstRequested : Time
  deadline : Time
  leaseUntil : Time
  deriving DecidableEq, Repr
structure Artifact where
  runId : Nat
  generatedAt : Time
  basis : Option DisplayBasis -- none admits the legacy, incomplete producer
  entries : List Entry
  readItems : List EntityId := []
structure Capture where
  basis : DisplayBasis
  inputs : Inputs
structure ViewState where
  loop : State
  sources : DisplayBasis
  artifact : Option Artifact
  requests : List Request
  failed : List EntityId
  stopped : List EntityId
  refreshFailed : Bool
  marks : List (EntityId × StaleReason)
  header : Option StaleReason
  inFlight : Option Capture
  dirty : Bool
  regenerations : Nat

/-- Basis currency alone is not permission to consume a failed/pending reading. -/
def Current (s : ViewState) (a : Artifact) : Prop := a.basis = some s.sources
def publishedCurrent (s : ViewState) : Bool :=
  s.artifact.any fun a => a.basis == some s.sources

def readingMatches (s : ViewState) (i : EntityId) : Bool :=
  s.sources.documents.any fun p => p.1 == i && s.loop.inputs.readings.contains p

def reason (s : ViewState) (i : EntityId) : Option StaleReason :=
  if s.failed.contains i then some .readingFailedAfterMaxAttempts
  else if s.stopped.contains i then some .readerSeatStopped
  else if !readingMatches s i then some .readingPending
  else if s.sources.focusVersion.isNone then some .focusAbsent
  else if s.refreshFailed then some .refreshFailed
  else match s.artifact with
    | none => some .sourceChangedSincePass
    | some a => if a.basis == some s.sources then none else some .sourceChangedSincePass

/-- Visible per-entry annotations AND persistent failure banners outside the
ranked ten. A header remains visible even if no artifact has been published. -/
def render (s : ViewState) : ViewState :=
  { s with
    marks := (match s.artifact with
      | none => []
      | some a => a.entries.filterMap fun e => (reason s e.item).map (e.item, ·)) ++
        s.failed.map (fun i => (i, .readingFailedAfterMaxAttempts))
    header := if s.refreshFailed then some .refreshFailed
      else match s.artifact with
        | none => some .sourceChangedSincePass
        | some a => if a.basis == some s.sources then none else some .sourceChangedSincePass }

def Visible (s : ViewState) (i : EntityId) (r : StaleReason) : Prop := (i, r) ∈ s.marks
def Consumable (s : ViewState) (a : Artifact) (e : Entry) : Prop :=
  s.artifact = some a ∧ e ∈ a.entries ∧ Current s a ∧
  readingMatches s e.item = true ∧ reason s e.item = none ∧ ∀ r, ¬Visible s e.item r
def DisplaySafe (s : ViewState) : Prop :=
  ∀ a, s.artifact = some a → ∀ e ∈ a.entries, Consumable s a e ∨ ∃ r, Visible s e.item r
def InventoryCovers (inp : Inputs) (b : DisplayBasis) : Prop :=
  ∀ it ∈ inp.items, ∃ digest, (it.id, digest) ∈ b.documents

def BasisComplete (s : ViewState) : Prop :=
  ∀ a, s.artifact = some a → ∃ b, a.basis = some b ∧ a.runId = b.runId ∧
    ∀ i ∈ a.readItems, ∃ digest, (i, digest) ∈ b.documents

def notify (s : ViewState) : ViewState :=
  match s.inFlight with
  | none => { s with inFlight := some ⟨s.sources, s.loop.inputs⟩, dirty := false, regenerations := s.regenerations + 1 }
  | some _ => { s with dirty := true }

/-- A follow-up captures the LATEST inventory. Failure preserves the old
artifact and produces a visible error, not a success stamp. -/
def finish (cs : List Character) (ok : Bool) (s : ViewState) : ViewState :=
  match s.inFlight with
  | none => render s
  | some captured =>
    let s' := { s with inFlight := none, refreshFailed := !ok, artifact := if ok then some { runId := captured.basis.runId, generatedAt := s.loop.now, basis := some captured.basis, entries := rebuild cs captured.inputs, readItems := captured.inputs.items.map Item.id } else s.artifact }
    render (if s.dirty then notify { s' with dirty := false } else s')

inductive InputEvent
  | logical (event : Event)
  | metadata (run pass pending lex : Nat) (focus : Option Nat)
  | request (row : Request)
  | renew (item : EntityId) (lease : Time)
  | requeue (item : EntityId)
  | exhausted (item : EntityId)
  | seatStopped (item : EntityId)
  | leaveOpen (item : EntityId)
  | refreshDone (ok : Bool)

/-- A matching landing is a new completion, including a retry of the same
source digest; "newer" means later completion, not a greater hash value. -/
def advance (cs : List Character) (s : ViewState) : InputEvent → ViewState
  | .logical (.readingLanded i v) =>
    if s.sources.documents.contains (i, v) then
      render (notify { s with loop := step cs s.loop (.readingLanded i v), failed := s.failed.filter (· != i), requests := s.requests.map fun q => if q.item == i then { q with state := .landed } else q })
    else render s
  | .logical (.docChange i v) =>
    render (notify { s with loop := step cs s.loop (.docChange i v), sources := { s.sources with documents := (i, v) :: s.sources.documents.filter (fun p => p.1 != i) } })
  | .logical (.focus weights) => render (notify { s with
      loop := step cs s.loop (.focus weights)
      sources := { s.sources with focusVersion := some (s.sources.focusVersion.getD 0 + 1) } })
  | .logical e => render (notify { s with loop := step cs s.loop e })
  | .metadata run pass pending lex focus =>
    render (notify { s with sources := { s.sources with runId := run, passInputs := pass, pendingIndex := pending, lexicon := lex, focusVersion := focus } })
  | .request q => render { s with requests := q :: s.requests }
  | .renew i lease => render { s with requests := s.requests.map fun q =>
      if q.item == i then { q with state := .claimed, leaseUntil := lease } else q }
  | .requeue i => render { s with requests := s.requests.map fun q =>
      if q.item == i then { q with state := .pending, attempts := 0 } else q }
  | .exhausted i => render { s with failed := i :: s.failed, requests := s.requests.map fun q => if q.item == i then
        { q with state := .failed, attempts := q.maxAttempts } else q }
  | .seatStopped i => render { s with stopped := i :: s.stopped }
  | .leaveOpen i => render { s with failed := s.failed.filter (· != i), loop := { s.loop with inputs := { s.loop.inputs with
        items := s.loop.inputs.items.filter (fun it => it.id != i) } } }
  | .refreshDone ok => finish cs ok s

def initial (s : State) (b : DisplayBasis) : ViewState :=
  render { loop := s, sources := b, artifact := none, requests := [], failed := [], stopped := [], refreshFailed := false, marks := [], header := none, inFlight := none, dirty := false, regenerations := 0 }
inductive Reachable (cs : List Character) : ViewState → Prop
  | initial (s : State) (b : DisplayBasis) (inventory : InventoryCovers s.inputs b)
      (uniqueDocuments : (b.documents.map Prod.fst).Nodup) :
      Reachable cs (Freshness.initial s b)
  | next {s : ViewState} (h : Reachable cs s) (e : InputEvent) :
      Reachable cs (advance cs s e)

@[simp] theorem render_artifact (s : ViewState) : (render s).artifact = s.artifact := rfl
@[simp] theorem render_sources (s : ViewState) : (render s).sources = s.sources := rfl
@[simp] theorem render_failed (s : ViewState) : (render s).failed = s.failed := rfl
@[simp] theorem render_loop (s : ViewState) : (render s).loop = s.loop := rfl
@[simp] theorem render_reason (s : ViewState) (i : EntityId) : reason (render s) i = reason s i := rfl
@[simp] theorem render_readingMatches (s : ViewState) (i : EntityId) :
    readingMatches (render s) i = readingMatches s i := rfl

/-- A failed item has a banner even when no ranked entry mentions it. -/
theorem render_failure (s : ViewState) (i : EntityId) (hi : i ∈ s.failed) :
    Visible (render s) i .readingFailedAfterMaxAttempts := by
  unfold Visible render
  apply List.mem_append_right
  exact List.mem_map.mpr ⟨i, hi, rfl⟩

/-- Render always makes basis staleness visible, including an empty artifact. -/
theorem render_resolved (s : ViewState) :
    (∃ a, (render s).artifact = some a ∧ Current (render s) a) ∨
      (render s).header.isSome = true := by
  cases h : s.artifact with
  | none => right; simp [render, h]; split <;> rfl
  | some a =>
    by_cases hc : a.basis = some s.sources
    · left; exact ⟨a, h, hc⟩
    · right; simp [render, h, hc]; split <;> rfl

/-- A renderer annotates every nonconsumable entry, not just failed requests. -/
theorem render_safe (s : ViewState) : DisplaySafe (render s) := by
  intro a ha e he
  change s.artifact = some a at ha
  cases hr : reason s e.item with
  | some why =>
    right
    refine ⟨why, ?_⟩
    unfold Visible render
    apply List.mem_append_left
    simp only [ha]
    exact List.mem_filterMap.mpr ⟨e, he, by simp [hr]⟩
  | none =>
    left
    have facts : ¬ e.item ∈ s.failed ∧ readingMatches s e.item = true ∧
        a.basis = some s.sources := by
      simp only [reason, ha] at hr
      split at hr <;> try simp_all
      split at hr <;> try simp_all
      split at hr <;> try simp_all
      split at hr <;> try simp_all
      split at hr <;> try simp_all
    refine ⟨ha, he, facts.2.2, facts.2.1, hr, ?_⟩
    intro why hw
    simp only [Visible, render, ha, List.mem_append] at hw
    rcases hw with hw | hw
    · obtain ⟨other, _, ho⟩ := List.mem_filterMap.mp hw
      cases hother : reason s other.item with
      | none => simp [hother] at ho
      | some r =>
        simp only [hother, Option.map_some, Option.some.injEq, Prod.mk.injEq] at ho
        have hn := hr
        rw [← ho.1, hother] at hn
        cases hn
    · obtain ⟨j, hj, heq⟩ := List.mem_map.mp hw
      exact facts.1 ((Prod.mk.inj heq).1 ▸ hj)

@[simp] theorem notify_failed (s : ViewState) : (notify s).failed = s.failed := by
  cases h : s.inFlight <;> simp [notify, h]
@[simp] theorem finish_failed (cs : List Character) (ok : Bool) (s : ViewState) :
    (finish cs ok s).failed = s.failed := by
  cases h : s.inFlight <;> simp [finish, h]
  split <;> simp

@[simp] theorem notify_loop (s : ViewState) : (notify s).loop = s.loop := by
  cases h : s.inFlight <;> simp [notify, h]
@[simp] theorem notify_sources (s : ViewState) : (notify s).sources = s.sources := by
  cases h : s.inFlight <;> simp [notify, h]
@[simp] theorem finish_loop (cs : List Character) (ok : Bool) (s : ViewState) :
    (finish cs ok s).loop = s.loop := by
  cases h : s.inFlight <;> simp [finish, h]
  split <;> simp
@[simp] theorem finish_sources (cs : List Character) (ok : Bool) (s : ViewState) :
    (finish cs ok s).sources = s.sources := by
  cases h : s.inFlight <;> simp [finish, h]
  split <;> simp

@[simp] theorem notify_readingMatches (s : ViewState) (i : EntityId) :
    readingMatches (notify s) i = readingMatches s i := by simp [readingMatches]
@[simp] theorem finish_readingMatches (cs : List Character) (ok : Bool)
    (s : ViewState) (i : EntityId) : readingMatches (finish cs ok s) i = readingMatches s i := by
  simp [readingMatches]

theorem readingMatches_iff (s : ViewState) (i : EntityId) :
    readingMatches s i = true ↔ ∃ v, (i, v) ∈ s.sources.documents ∧
      (i, v) ∈ s.loop.inputs.readings := by
  simp only [readingMatches, List.any_eq_true, Prod.exists, Bool.and_eq_true,
    beq_iff_eq, List.contains_iff_mem]
  constructor
  · rintro ⟨j, v, hd, he, hr⟩
    subst j
    exact ⟨v, hd, hr⟩
  · rintro ⟨v, hd, hr⟩
    exact ⟨i, v, hd, rfl, hr⟩

/-- Every transition ends at the same annotation boundary. -/
theorem advance_rendered (cs : List Character) (s : ViewState) (e : InputEvent) :
    ∃ raw, advance cs s e = render raw := by
  cases e with
  | logical event =>
    cases event with
    | readingLanded i v => simp only [advance]; split <;> exact ⟨_, rfl⟩
    | turn t ev => cases ev <;> exact ⟨_, rfl⟩
    | docChange i v => exact ⟨_, rfl⟩
    | focus w => exact ⟨_, rfl⟩
    | tick => exact ⟨_, rfl⟩
  | refreshDone ok =>
    simp only [advance, finish]
    cases s.inFlight <;> exact ⟨_, rfl⟩
  | _ => exact ⟨_, rfl⟩

/-- Coverage must hold at capture time as well as at publication time. -/
def InventoryInvariant (s : ViewState) : Prop :=
  InventoryCovers s.loop.inputs s.sources ∧
  (∀ captured, s.inFlight = some captured → InventoryCovers captured.inputs captured.basis) ∧
  BasisComplete s

theorem render_inventory (s : ViewState) (h : InventoryInvariant s) :
    InventoryInvariant (render s) := h

theorem transfer_inventory (s t : ViewState) (h : InventoryInvariant s)
    (covers : InventoryCovers t.loop.inputs t.sources)
    (flight : t.inFlight = s.inFlight) (artifact : t.artifact = s.artifact) :
    InventoryInvariant t := by
  refine ⟨covers, ?_, ?_⟩
  · intro captured hc
    exact h.2.1 captured (flight ▸ hc)
  · intro a ha
    exact h.2.2 a (artifact ▸ ha)

theorem notify_inventory (s : ViewState) (h : InventoryInvariant s) :
    InventoryInvariant (notify s) := by
  cases hf : s.inFlight with
  | none =>
    simp only [notify, hf]
    refine ⟨h.1, ?_, h.2.2⟩
    intro captured hc
    have he : captured = ⟨s.sources, s.loop.inputs⟩ := by
      simpa [notify, hf] using hc.symm
    subst captured
    exact h.1
  | some captured =>
    simp only [notify, hf]
    exact transfer_inventory s _ h h.1 hf.symm rfl

theorem artifact_inventory (captured : Capture)
    (covered : InventoryCovers captured.inputs captured.basis) :
    ∀ i ∈ captured.inputs.items.map Item.id, ∃ digest, (i, digest) ∈ captured.basis.documents := by
  intro i hi
  obtain ⟨item, hm, rfl⟩ := List.mem_map.mp hi
  exact covered item hm

theorem finish_inventory (cs : List Character) (ok : Bool) (s : ViewState)
    (h : InventoryInvariant s) : InventoryInvariant (finish cs ok s) := by
  cases hf : s.inFlight with
  | none => simpa [finish, hf] using render_inventory s h
  | some captured =>
    let next := { s with inFlight := none, refreshFailed := !ok, artifact := if ok then some { runId := captured.basis.runId, generatedAt := s.loop.now, basis := some captured.basis, entries := rebuild cs captured.inputs, readItems := captured.inputs.items.map Item.id } else s.artifact }
    have hn : InventoryInvariant next := by
      refine ⟨h.1, ?_, ?_⟩
      · intro c hc; cases hc
      · intro a ha
        cases ok with
        | false => exact h.2.2 a ha
        | true =>
          have he : a = { runId := captured.basis.runId, generatedAt := s.loop.now, basis := some captured.basis, entries := rebuild cs captured.inputs, readItems := captured.inputs.items.map Item.id } := by
            simpa [next] using ha.symm
          subst a
          exact ⟨captured.basis, rfl, rfl,
            artifact_inventory captured (h.2.1 captured hf)⟩
    simp only [finish, hf]
    change InventoryInvariant (render (if s.dirty then notify { next with dirty := false } else next))
    apply render_inventory
    split
    · exact notify_inventory _ hn
    · exact hn

@[simp] theorem applyEvent_items (inp : Inputs) (e : Event) :
    (applyEvent inp e).items = inp.items := by
  cases e with
  | turn t ev => cases ev <;> rfl
  | _ => rfl

/-- Replacing a document digest preserves coverage, including the changed item. -/
theorem replace_document_covers (inp : Inputs) (basis : DisplayBasis)
    (h : InventoryCovers inp basis) (i : EntityId) (v : Nat) :
    InventoryCovers inp { basis with documents := (i,v) :: basis.documents.filter (fun p => p.1 != i) } := by
  intro item hi
  by_cases he : item.id = i
  · exact ⟨v, by simp [he]⟩
  · obtain ⟨digest, hd⟩ := h item hi
    exact ⟨digest, by simp [List.mem_filter, he, hd]⟩

theorem advance_inventory (cs : List Character) (s : ViewState) (e : InputEvent)
    (h : InventoryInvariant s) : InventoryInvariant (advance cs s e) := by
  cases e with
  | logical ev =>
    cases ev with
    | readingLanded i v =>
      simp only [advance]
      split
      · apply render_inventory
        apply notify_inventory
        apply transfer_inventory s _ h ?_ rfl rfl
        simpa only [InventoryCovers, step, applyEvent_items] using h.1
      · exact render_inventory s h
    | docChange i v =>
      apply render_inventory
      apply notify_inventory
      refine transfer_inventory s _ h ?_ ?_ ?_
      all_goals try rfl
      simpa only [InventoryCovers, step, applyEvent_items] using
        replace_document_covers s.loop.inputs s.sources h.1 i v
    | turn t ev =>
      cases ev <;> apply render_inventory <;> apply notify_inventory <;>
        apply transfer_inventory s _ h ?_ rfl rfl
      all_goals simpa only [InventoryCovers, step, applyEvent_items] using h.1
    | focus weights =>
      apply render_inventory
      apply notify_inventory
      apply transfer_inventory s _ h ?_ rfl rfl
      simpa only [InventoryCovers, step, applyEvent_items] using h.1
    | tick =>
      apply render_inventory
      apply notify_inventory
      apply transfer_inventory s _ h ?_ rfl rfl
      simpa only [InventoryCovers, step, applyEvent_items] using h.1
  | metadata run pass pending lex focus =>
    apply render_inventory
    apply notify_inventory
    exact transfer_inventory s _ h h.1 rfl rfl
  | leaveOpen i =>
    apply render_inventory
    refine transfer_inventory s _ h ?_ ?_ ?_
    all_goals try rfl
    intro item hi
    exact h.1 item (List.mem_filter.mp hi).1
  | refreshDone ok => exact finish_inventory cs ok s h
  | _ =>
    apply render_inventory
    exact transfer_inventory s _ h h.1 rfl rfl

/-- Obligation 3: "Check current item/document digests, the pass's input digest
and the focus version used for priorities." Published artifacts carry their
complete read inventory; a run id and generated_at alone are insufficient. -/
theorem display_basis_complete (cs : List Character) (s : ViewState)
    (reachable : Reachable cs s) : BasisComplete s := by
  have invariant : InventoryInvariant s := by
    induction reachable with
    | initial loop basis covers unique =>
      apply render_inventory
      refine ⟨covers, ?_, ?_⟩
      · intro c hc; cases hc
      · intro a ha; cases ha
    | next reached event ih => exact advance_inventory cs _ event ih
  exact invariant.2.2

/-- Objection 5: "show how the consumer sees that condition instead of treating
it as current knowledge." Every reachable displayed entry is consumable or
visibly qualified by a closed-set stale reason. -/
theorem display_current_or_typed_stale (cs : List Character) (s : ViewState)
    (reachable : Reachable cs s) : DisplaySafe s := by
  cases reachable with
  | initial loop basis covers unique => exact render_safe _
  | next h e =>
    obtain ⟨raw, hr⟩ := advance_rendered cs _ e
    rw [hr]
    exact render_safe raw

structure Observation where
  observedAt : Time
  deadline : Time
  state : ViewState

def Resolved (s : ViewState) : Prop :=
  (∃ a, s.artifact = some a ∧ Current s a) ∨ s.header.isSome = true
def DeadlineMet (o : Observation) : Prop := o.observedAt ≤ o.deadline ∧ Resolved o.state
def burst (cs : List Character) (s : ViewState) (events : List InputEvent) : ViewState :=
  events.foldl (advance cs) s

def IsSourceEvent : InputEvent → Prop
  | .logical (.docChange ..) | .logical (.focus ..) | .metadata .. => True
  | _ => False

/-- A burst arrives during ONE in-flight refresh; no further source event
arrives during its follow-up. Otherwise more than two runs can be necessary. -/
def BurstGuarantee (cs : List Character) (s : ViewState) (events : List InputEvent) : Prop :=
  let latest := burst cs s events
  let done := finish cs true (finish cs true latest)
  done.regenerations ≤ s.regenerations + 1 ∧
    ∃ a, done.artifact = some a ∧ a.basis = some latest.sources

/-- Actual event and consumer-delivery timestamps, retained independently of
lease times. Timely is an integration assumption to be witnessed by DERIVE. -/
structure TimedEvent where
  occurredAt : Time
  observedAt : Time
  event : InputEvent

def Timely (T : Time) (trace : List TimedEvent) : Prop :=
  trace.Pairwise (fun a b => a.observedAt ≤ b.observedAt) ∧
  ∀ e ∈ trace, e.occurredAt ≤ e.observedAt ∧ e.observedAt ≤ e.occurredAt + T

def observations (T : Time) (cs : List Character) (s : ViewState) :
    List TimedEvent → List Observation
  | [] => []
  | e :: es =>
    let next := advance cs s e.event
    ⟨e.observedAt, e.occurredAt + T, next⟩ :: observations T cs next es

/-- Source notifications during a flight retain its capture and set one dirty bit. -/
theorem source_during_flight (cs : List Character) (s : ViewState) (e : InputEvent)
    (captured : Capture) (flight : s.inFlight = some captured) (source : IsSourceEvent e) :
    (advance cs s e).inFlight = some captured ∧ (advance cs s e).dirty = true ∧
      (advance cs s e).regenerations = s.regenerations := by
  cases e with
  | logical ev => cases ev <;> simp_all [IsSourceEvent, advance, notify, render]
  | metadata run pass pending lex focus => simp [advance, notify, render, flight]
  | _ => cases source

theorem burst_source_flight (cs : List Character) (events : List InputEvent)
    (captured : Capture) (s : ViewState) (flight : s.inFlight = some captured)
    (source : ∀ e ∈ events, IsSourceEvent e) :
    (burst cs s events).inFlight = some captured ∧
    (burst cs s events).regenerations = s.regenerations ∧
    (events ≠ [] → (burst cs s events).dirty = true) := by
  induction events generalizing s with
  | nil => simp [burst, flight]
  | cons e es ih =>
    have hstep := source_during_flight cs s e captured flight (source e (by simp))
    have htail := ih (advance cs s e) hstep.1 (fun e he => source e (by simp [he]))
    refine ⟨htail.1, htail.2.1.trans hstep.2.2, ?_⟩
    intro _
    cases es with
    | nil => exact hstep.2.1
    | cons e es => exact htail.2.2 (by simp)

/-- Exactly the latest capture is installed by the one queued follow-up. -/
theorem two_successful_finishes (cs : List Character) (s : ViewState) (captured : Capture)
    (flight : s.inFlight = some captured) (dirty : s.dirty = true) :
    (finish cs true (finish cs true s)).regenerations = s.regenerations + 1 ∧
    ∃ a, (finish cs true (finish cs true s)).artifact = some a ∧ a.basis = some s.sources := by
  simp [finish, notify, render, flight, dirty]

theorem timely_observations (cs : List Character) (T : Time) (trace : List TimedEvent)
    (s : ViewState) (bounds : ∀ e ∈ trace, e.observedAt ≤ e.occurredAt + T) :
    ∀ o ∈ observations T cs s trace, DeadlineMet o := by
  induction trace generalizing s with
  | nil => simp [observations]
  | cons e es ih =>
    intro o ho
    rcases List.mem_cons.mp ho with rfl | ho
    · refine ⟨bounds e (by simp), ?_⟩
      obtain ⟨raw, hr⟩ := advance_rendered cs s e.event
      change Resolved (advance cs s e.event)
      rw [hr]
      exact render_resolved raw
    · exact ih (advance cs s e.event) (fun e he => bounds e (by simp [he])) o ho

/-- Joe: "party.json is kept up to date"; obligation 3: "numerical deadlines
... priority freshness." Every observation of a timely delivered trace shows
current basis or typed staleness, with the deadline measured from the source
event, not a renewal. Covers source changes even when no mint follows.
Separately, a nonempty burst during one flight, followed by quiescence and
two SUCCESSFUL completions, publishes its last inventory with one follow-up
at most. Failure promises visibility, not successful regeneration. -/
theorem party_kept_up_to_date (cs : List Character) (s : ViewState)
    (reachable : Reachable cs s) (T : Time) (trace : List TimedEvent)
    (timely : Timely T trace) :
    (∀ o ∈ observations T cs s trace, DeadlineMet o) ∧
    (∀ events, s.inFlight.isSome = true →
      (∀ e ∈ events, IsSourceEvent e) → events ≠ [] →
      BurstGuarantee cs s events) := by
  constructor
  · exact timely_observations cs T trace s (fun e he => (timely.2 e he).2)
  · intro events hf hs hne
    obtain ⟨captured, hc⟩ := Option.isSome_iff_exists.mp hf
    have hb := burst_source_flight cs events captured s hc hs
    have done := two_successful_finishes cs (burst cs s events) captured hb.1 (hb.2.2 hne)
    exact ⟨by simp only [done.1, hb.2.1, Nat.le_refl], done.2⟩

/-- This is the digest-checked landing admitted to the old logical model. -/
def MatchingLanding (s : ViewState) (i : EntityId) : InputEvent → Prop
  | .logical (.readingLanded j v) => j = i ∧ s.sources.documents.contains (j, v) = true
  | _ => False

/-- Obligation 3: "Repeatedly renewing a request must not make stalled work
appear healthy." Renewal/requeue preserve basis currency, reading validity, and original request/deadline times; iterating these steps cannot heal a stale
reading. A matching landing is necessary, and publication must still follow. -/
theorem stalled_is_not_healthy (cs : List Character) (s : ViewState)
    (i : EntityId) (lease : Time) :
    publishedCurrent (advance cs s (.renew i lease)) = publishedCurrent s ∧
    publishedCurrent (advance cs s (.requeue i)) = publishedCurrent s ∧
    readingMatches (advance cs s (.renew i lease)) i = readingMatches s i ∧
    readingMatches (advance cs s (.requeue i)) i = readingMatches s i ∧
    ((advance cs s (.renew i lease)).requests.map fun q => (q.firstRequested, q.deadline)) =
      (s.requests.map fun q => (q.firstRequested, q.deadline)) ∧
    ((advance cs s (.requeue i)).requests.map fun q => (q.firstRequested, q.deadline)) =
      (s.requests.map fun q => (q.firstRequested, q.deadline)) ∧
    (∀ e, ¬MatchingLanding s i e → readingMatches (advance cs s e) i = true →
      readingMatches s i = true) := by
  refine ⟨rfl, rfl, rfl, rfl, ?_, ?_, ?_⟩
  · simp only [advance, render, List.map_map]
    apply List.map_congr_left
    intro q hq
    simp only [Function.comp_apply]
    split <;> rfl
  · simp only [advance, render, List.map_map]
    apply List.map_congr_left
    intro q hq
    simp only [Function.comp_apply]
    split <;> rfl
  · intro e hn hnew
    cases e with
    | logical ev =>
      cases ev with
      | readingLanded j v =>
        by_cases hc : (j, v) ∈ s.sources.documents
        · have hne : j ≠ i := fun he => hn ⟨he, by simpa using hc⟩
          simp only [advance, show s.sources.documents.contains (j, v) = true by simpa using hc,
            if_true, render_readingMatches, notify_readingMatches] at hnew
          obtain ⟨w, hd, hr⟩ := (readingMatches_iff _ _).mp hnew
          have old : (i, w) ∈ s.loop.inputs.readings := by
            simpa [step, applyEvent, hne, Ne.symm hne] using hr
          exact (readingMatches_iff _ _).mpr ⟨w, hd, old⟩
        · simpa [advance, hc] using hnew
      | docChange j v =>
        simp only [advance, render_readingMatches, notify_readingMatches] at hnew
        obtain ⟨w, hd, hr⟩ := (readingMatches_iff _ _).mp hnew
        have old : (i, w) ∈ s.loop.inputs.readings ∧ i ≠ j := by
          simpa [step, applyEvent] using hr
        have doc : (i, w) ∈ s.sources.documents := by
          simpa [old.2] using hd
        exact (readingMatches_iff _ _).mpr ⟨w, doc, old.1⟩
      | turn t ev => cases ev <;> simpa [advance, readingMatches, step, applyEvent] using hnew
      | _ => simpa [advance, readingMatches, step, applyEvent] using hnew
    | refreshDone ok => simpa [advance] using hnew
    | _ => simpa [advance, readingMatches] using hnew

def Releases (s : ViewState) (i : EntityId) : InputEvent → Prop
  | .logical (.readingLanded j v) => j = i ∧ s.sources.documents.contains (j, v) = true
  | .leaveOpen j => j = i
  | _ => False

/-- Objection 5: "retries are exhausted ... show how the consumer sees that
condition". Terminal failure remains visible, even outside the ranked ten, until a matching later landing or leave-open event. Queue resets cannot erase
this separate consumer latch. Induction gives persistence over any such trace. -/
theorem terminal_failure_is_visible (cs : List Character) (s : ViewState)
    (i : EntityId) (e : InputEvent) (failed : i ∈ s.failed)
    (noRelease : ¬Releases s i e) :
    i ∈ (advance cs s e).failed ∧
      Visible (advance cs s e) i .readingFailedAfterMaxAttempts := by
  have persists : i ∈ (advance cs s e).failed := by
    cases e with
    | logical ev =>
      cases ev with
      | readingLanded j v =>
        by_cases hc : (j, v) ∈ s.sources.documents
        · have hne : i ≠ j := by
            intro eq
            exact noRelease ⟨eq.symm, by simpa using hc⟩
          simpa [advance, hc, List.mem_filter, hne] using failed
        · simpa [advance, hc] using failed
      | turn t ev => cases ev <;> simpa [advance] using failed
      | _ => simpa [advance] using failed
    | leaveOpen j =>
      have hne : i ≠ j := fun eq => noRelease eq.symm
      simpa [advance, List.mem_filter, hne] using failed
    | exhausted j => simp [advance, failed]
    | refreshDone ok => simpa [advance] using failed
    | _ => simpa [advance] using failed
  refine ⟨persists, ?_⟩
  obtain ⟨raw, hr⟩ := advance_rendered cs s e
  rw [hr] at persists ⊢
  exact render_failure raw i persists

/-! ## TEETH: bad producers outside Reachable violate the contracts
A reachable counterexample would contradict a main statement, not validate it.
All fixtures are invented. These examples use no unproved main statement.
-/
namespace Teeth
private def inp : Inputs := ⟨[], [(1, 1)], fun _ => 0, fun _ _ => false, fun _ => [], [], 0⟩
private def loop : State := ⟨0, inp, [], 0, 0⟩
private def basis : DisplayBasis := ⟨1, 1, [(1, 1)], 1, 1, some 1⟩
private def entry : Entry := ⟨1, 1, ⟨[], .thor, [], .thor⟩⟩
private def artifact : Artifact := ⟨1, 0, some basis, [entry], [1]⟩
private def good : ViewState := { initial loop basis with artifact := some artifact }
private def legacy : ViewState := { good with artifact := some { artifact with basis := none } }
private def changed : ViewState := { good with sources := { basis with documents := [(1, 2)] } }

-- 1. The legacy run-id-only shape cannot provide a complete read inventory.
example : ¬BasisComplete legacy := by
  intro h
  obtain ⟨b, hb, _⟩ := h _ rfl
  cases hb

-- A present basis with a missing document also fails: field presence alone
-- is not enough to cover the inventory supplied to the renderer.
example : ¬BasisComplete { good with artifact := some { artifact with basis := some { basis with documents := [] } } } := by
  intro h
  obtain ⟨b, hb, _, covers⟩ := h _ rfl
  have he : b = { basis with documents := [] } := (Option.some.inj hb).symm
  subst b
  obtain ⟨digest, hd⟩ := covers 1 (by simp [artifact])
  exact List.not_mem_nil hd

-- 2. Same run id after a document change: neither consumable nor marked stale.
example : ¬DisplaySafe changed := by
  intro h
  rcases h artifact rfl entry (by simp [artifact]) with h | ⟨r, hr⟩
  · have hc := h.2.2.1
    have hn : ¬Current changed artifact := by unfold Current; decide
    exact hn hc
  · simp [Visible, changed, good, initial, render, loop] at hr

-- 3. Dropping the dirty bit loses the last event despite a completed refresh.
private def flight : ViewState := { good with inFlight := some ⟨basis, inp⟩, regenerations := 1 }
private def dropLast : ViewState := { flight with sources := { basis with lexicon := 2 } }
example : (finish [] true dropLast).regenerations = 1 ∧
    ¬(∃ a, (finish [] true dropLast).artifact = some a ∧ a.basis = some dropLast.sources) := by
  constructor
  · rfl
  · rintro ⟨a, ha, hb⟩
    have he : a = { runId := 1, generatedAt := 0, basis := some basis, entries := [] } := by
      have hc : (finish [] true dropLast).artifact =
          some { runId := 1, generatedAt := 0, basis := some basis, entries := [] } := rfl
      rw [hc] at ha
      exact (Option.some.inj ha).symm
    subst a
    have hn : some basis ≠ some dropLast.sources := by decide
    exact hn hb

-- A typed stale annotation delivered after the absolute deadline is still late.
example : ¬DeadlineMet ⟨12, 10, render changed⟩ := by
  intro h
  have ht := h.1
  exact (by decide : ¬(12 : Nat) ≤ 10) ht

-- 4. A renewal-only producer fabricating a fresh stamp has no matching reading.
private def forgedRenewal : ViewState := { changed with
  artifact := some { artifact with basis := some changed.sources } }
example : publishedCurrent changed = false ∧ publishedCurrent forgedRenewal = true ∧
    readingMatches forgedRenewal 1 = false ∧
    publishedCurrent (burst [] changed [.renew 1 10, .renew 1 20, .requeue 1]) = false := by decide

-- 5. A queue-driven consumer clearing its failure latch on requeue hides failure.
private def failedView : ViewState := render { good with failed := [1] }
private def badRequeue : ViewState := render { failedView with failed := [] }
example : Visible failedView 1 .readingFailedAfterMaxAttempts ∧
    ¬Visible badRequeue 1 .readingFailedAfterMaxAttempts := by unfold Visible; decide
end Teeth
end Circus.Freshness
