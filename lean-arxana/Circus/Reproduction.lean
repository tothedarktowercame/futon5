import Circus.LiveChain

namespace Circus.Reproduction
open LiveChain LateWrites

/-- Recorded semantic data, not a new invocation of an animal. Every ChainInput
field except the published Bridge is retained, including the reader proposal,
pre-existing deliveries, settle state, in-flight capture and refresh outcome.
Inputs contains functions: these are extensional snapshots in this model, NOT a
claim that arbitrary Lean functions have a finite wire encoding. A live receipt
must supply a faithful snapshot codec (including activation/needs/serves and the
GCC digest table); inability to recover it is missingContent replaySnapshot.
Published Characters and Bridge are fixed parameters of reproduction. -/
structure ReplaySnapshot where
  corpus : Corpus
  basis : ComparisonBasis
  projector : Projector
  deliveries : List Delivery
  turn : TurnId
  replyLanded : Bool
  view : Freshness.ViewState
  store : Supersession.Store
  digest : GCCRecord → Option Nat
  readRecords : List GCCRecord
  focusId : Nat
  proposal : Monkey.ReaderResult
  passInputDigest : Nat
  refreshSucceeded : Bool
  cutoff : Nat

def snapshot (i : ChainInput) : ReplaySnapshot :=
  ⟨i.corpus, i.basis, i.projector, i.deliveries, i.turn, i.replyLanded,
   i.view, i.store, i.digest, i.readRecords, i.focusId, i.proposal,
   i.passInputDigest, i.refreshSucceeded, i.cutoff⟩
def restore (b : Bridge) (s : ReplaySnapshot) : ChainInput :=
  ⟨b, s.corpus, s.basis, s.projector, s.deliveries, s.turn, s.replyLanded,
   s.view, s.store, s.digest, s.readRecords, s.focusId, s.proposal,
   s.passInputDigest, s.refreshSucceeded, s.cutoff⟩
theorem restore_snapshot (i : ChainInput) : restore i.bridge (snapshot i) = i := by
  cases i; rfl

/-- The observable reproduction comparison: priorities (including pins), citation
and notices. Internal ViewState function closures are not consumer receipts.
The retained snapshot nevertheless includes that state to recompute the view. -/
structure Output where
  priorities : List Monkey.RankedRow
  citation : Option Citation
  notices : List ChainAbsence
  deriving DecidableEq, Repr

def observe (m : Mojira) : Output := ⟨m.priorities, m.citation, m.notices⟩
structure Receipt where
  corpusGeneration : Nat
  sources : List SourceRecord
  comparison : ComparisonBasis
  focus : Option (GCCRecord × Monkey.EvidencePin)
  passId : Nat
  passInputDigest : Nat
  cutoff : Nat
  replayDigest : Nat
  output : Output

/-- Operational fields are excluded from the semantic journal comparison by
LateWrites.eraseOperational; the full replay snapshot also preserves them so
no undocumented operational independence assumption enters restoration. -/
def recordReceipt (hash : ReplaySnapshot → Nat) (i : ChainInput) (m : Mojira) : Receipt :=
  ⟨i.corpus.generation, semanticRecords (i.projector.delivered ++ i.deliveries),
   i.basis, m.citation.map (fun c => (c.focusRecord, c.focusPin)), i.store.run,
   i.passInputDigest, i.cutoff, hash (snapshot i), observe m⟩

inductive Field where
  | replaySnapshot
  deriving DecidableEq, Repr
inductive Failure where
  | missingContent (field : Field)
  | digestMismatch (field : Field)
  deriving DecidableEq, Repr
abbrev Lookup := Nat → Option ReplaySnapshot

/-- Hash faithfulness is an explicit assumption about this receipt's lookup,
not a theorem about collision resistance. It identifies the recovered semantic
snapshot, including recorded LLM output, with the snapshot actually recorded. -/
def DigestFaithful (hash : ReplaySnapshot → Nat) (lookup : Lookup)
    (original : ReplaySnapshot) : Prop :=
  ∀ recovered, lookup (hash original) = some recovered →
    hash recovered = hash original → recovered = original

def checkedLookup (hash : ReplaySnapshot → Nat) (lookup : Lookup)
    (digest : Nat) : Except Failure ReplaySnapshot :=
  match lookup digest with
  | none => .error (.missingContent .replaySnapshot)
  | some s => if hash s = digest then .ok s
    else .error (.digestMismatch .replaySnapshot)

noncomputable def reproduce (cs : List Circus.Character) (bridge : Bridge)
    (hash : ReplaySnapshot → Nat) (lookup : Lookup) (receipt : Receipt) : Except Failure Output :=
  match checkedLookup hash lookup receipt.replayDigest with
  | .error e => .error e
  | .ok s => .ok (observe (renderChain cs (restore bridge s)))

/-- VERIFY obligation 5: "reproduces the result using recorded source IDs/digests,
queries, graph records, pass/focus versions and consumer receipts". All remaining
inputs are in ReplaySnapshot; only the published Bridge/Characters are supplied
externally and must be the same versions. This promises reproduction, not truth
of an implementer's claimed output: recordReceipt is applied to the actual run. -/
theorem reproduce_from_receipt (cs : List Circus.Character) (i : ChainInput)
    (hash : ReplaySnapshot → Nat) (lookup : Lookup)
    (available : ∃ s, lookup (hash (snapshot i)) = some s ∧ hash s = hash (snapshot i))
    (faithful : DigestFaithful hash lookup (snapshot i)) :
    reproduce cs i.bridge hash lookup
      (recordReceipt hash i (renderChain cs i)) = .ok (observe (renderChain cs i)) := by
  obtain ⟨recovered, found, digest⟩ := available
  have eq := faithful recovered found digest
  subst recovered
  simp [reproduce, checkedLookup, recordReceipt, found, restore_snapshot]

/-- VERIFY obligation 5, "Independent acceptance": unavailable or corrupt
recorded content is a named failure, never a purported reproduced output. -/
theorem reproduction_mismatch_is_typed (cs : List Circus.Character) (b : Bridge)
    (hash : ReplaySnapshot → Nat) (lookup : Lookup) (r : Receipt) :
    (lookup r.replayDigest = none → reproduce cs b hash lookup r =
      .error (.missingContent .replaySnapshot)) ∧
    (∀ s, lookup r.replayDigest = some s → hash s ≠ r.replayDigest →
      reproduce cs b hash lookup r = .error (.digestMismatch .replaySnapshot)) := by
  constructor
  · intro missing
    simp [reproduce, checkedLookup, missing]
  · intro recovered found mismatch
    simp [reproduce, checkedLookup, found, mismatch]

/-- Agent identities are reader identities, not character roles: two operators
must remain distinguishable. These strings require authentication in live receipts. -/
structure ClosureReport where
  implementer : String
  reproducer : String
  scope : List String
  bounds : List (String × Nat)
  receipt : Receipt
  outcome : Except Failure Output
  remainingFailures : List ChainAbsence

/-- Acceptance checks an actual reproduction result as well as the report.
Every output notice must remain visible in the closure report. -/
def Accepted (cs : List Circus.Character) (b : Bridge) (hash : ReplaySnapshot → Nat)
    (lookup : Lookup) (r : ClosureReport) : Prop :=
  r.reproducer ≠ r.implementer ∧
  r.outcome = reproduce cs b hash lookup r.receipt ∧
  r.outcome = .ok r.receipt.output ∧
  ∀ notice ∈ r.receipt.output.notices, notice ∈ r.remainingFailures

/-- VERIFY obligation 5: "The implementing agent's account alone is not the
completion witness"; "scope and bounds checked ... any remaining failures".
Scope/bounds are recorded claims, not evidence of additional unchecked scope.
Restated by claude-13 in review: the earlier conclusion repeated the definition
of Accepted word for word. This one says what acceptance buys: if the receipt's
replay digest is that of an actual run, an accepted report's claimed output IS
that run's output, so an implementer cannot get a forged output accepted. -/
theorem implementer_account_not_witness (cs : List Circus.Character) (i : ChainInput)
    (hash : ReplaySnapshot → Nat) (lookup : Lookup) (r : ClosureReport)
    (recorded : r.receipt.replayDigest = hash (snapshot i))
    (faithful : DigestFaithful hash lookup (snapshot i))
    (accepted : Accepted cs i.bridge hash lookup r) :
    r.receipt.output = observe (renderChain cs i) := by
  obtain ⟨_, actual, claimed, _⟩ := accepted
  rw [claimed] at actual
  unfold reproduce checkedLookup at actual
  rw [recorded] at actual
  cases found : lookup (hash (snapshot i)) with
  | none => simp [found] at actual
  | some recovered =>
    by_cases digest : hash recovered = hash (snapshot i)
    · have eq := faithful recovered found digest
      subst recovered
      simpa [found, restore_snapshot] using actual
    · simp [found, digest] at actual

/- Live-store conformance: mapping corpus/source evidence to futon1b, passes to
Giraffe run storage, rendered output to party.json, and deliveries to the turn
store is still work to be done. Published decoder and key granularity questions
from LiveChain carry over unchanged. The receipt format here is semantic, not
an assertion that today's stores already record every field. -/

/- TEETH: an inadequate receipt that drops the pin identifies two different
consumer results. The priorities are produced by the existing Monkey projection,
not invented evidence semantics. -/
private def focusWith (n : Nat) : Monkey.FocusRecord :=
  ⟨⟨9, .houFocus, some 1⟩, [(1, .focus)], [⟨2, .mention, some 1⟩],
    [(⟨2, .mention, some 1⟩, n)]⟩
private def pinRun (n : Nat) : Output :=
  ⟨Monkey.priorityProjection [⟨1, some 1⟩] (some (focusWith n)) 1,
    some ⟨1, [], (focusWith n).record, (focusWith n).evidencePin, 1⟩, []⟩
private def deficientReceipt (o : Output) :=
  (o.priorities.map (fun r => (r.row.target, r.row.band)),
   o.citation.map (fun c => (c.corpusGeneration, c.focusRecord, c.passId)), o.notices)
example : deficientReceipt (pinRun 10) = deficientReceipt (pinRun 11) ∧
    pinRun 10 ≠ pinRun 11 := by decide

-- TEETH: an unchecked lookup would accept the wrong digest; this gate refuses it.
example (s : ReplaySnapshot) : checkedLookup (fun _ => 11) (fun _ => some s) 10 =
    .error (.digestMismatch .replaySnapshot) := by rfl
example : checkedLookup (fun _ => 11) (fun _ => none) 10 =
    .error (.missingContent .replaySnapshot) := by rfl

-- The same rejection is propagated through the actual reproducer, not merely
-- the lookup helper; there is no fallback to the receipt's claimed output.
example (cs : List Circus.Character) (b : Bridge) (s : ReplaySnapshot) (r : Receipt) :
    reproduce cs b (fun _ => 11) (fun _ => some s) { r with replayDigest := 10 } =
      .error (.digestMismatch .replaySnapshot) := by rfl

-- TEETH: the implementer cannot certify their own reproduction, even if it matches.
example (cs : List Circus.Character) (b : Bridge) (hash : ReplaySnapshot → Nat)
    (lookup : Lookup) (r : ClosureReport) :
    ¬ Accepted cs b hash lookup { r with implementer := "writer", reproducer := "writer" } := by
  intro h
  exact h.1 rfl

-- TEETH: hiding a concrete notice defeats independent acceptance.
example (cs : List Circus.Character) (b : Bridge) (hash : ReplaySnapshot → Nat)
    (lookup : Lookup) (r : ClosureReport)
    (noticed : r.receipt.output.notices = [.noRankedItems]) :
    ¬ Accepted cs b hash lookup { r with remainingFailures := [] } := by
  intro h
  have missing := h.2.2.2 .noRankedItems (by simp [noticed])
  exact List.not_mem_nil missing

end Circus.Reproduction
