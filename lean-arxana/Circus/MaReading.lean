import Circus.Basic

/-!
# 馬 reads every proposed pattern on an operator turn

This is the model-level obligation corresponding to futon3c's
`chain_record.clj` `:ma-reading` block and the generated
`ma_read_every_proposed_pattern` runtime-certificate theorem.

All examples use invented pattern ids.
-/
namespace Circus.MaReading

inductive Origin
  | operator
  | other
  deriving DecidableEq, Repr

inductive BatchOutcome
  | ok
  | failed (kind : String)
  | legacyUnknown
  deriving DecidableEq, Repr

/-- A part is complete when it is grounded with all four evidence fields, or
is explicitly void with a reason. -/
structure PartReading where
  element : Option String := none
  turn : Option String := none
  quote : Option String := none
  who : Option String := none
  why : Option String := none
  deriving DecidableEq, Repr

def nonempty : Option String → Bool
  | some s => !s.isEmpty
  | none => false

def PartReading.complete (p : PartReading) : Bool :=
  (nonempty p.element && nonempty p.turn && nonempty p.quote && nonempty p.who) ||
    nonempty p.why

structure PatternRow where
  i1 : PartReading
  i2 : PartReading
  g : PartReading
  b : PartReading
  deriving DecidableEq, Repr

def PatternRow.complete (r : PatternRow) : Bool :=
  r.i1.complete && r.i2.complete && r.g.complete && r.b.complete

structure PatternReading where
  pattern : String
  batch : BatchOutcome
  row : PatternRow
  deriving DecidableEq, Repr

inductive AbsenceReason
  | nonOperatorOrigin
  | noReply
  | noReplyText
  | noProposedPatterns
  deriving DecidableEq, Repr

structure TurnReading where
  origin : Origin
  replyPresent : Bool
  replyTextPresent : Bool
  proposed : List String
  readings : List PatternReading
  deriving DecidableEq, Repr

def TurnReading.legitimateAbsence (r : TurnReading) : Option AbsenceReason :=
  if r.origin != .operator then some .nonOperatorOrigin
  else if !r.replyPresent then some .noReply
  else if !r.replyTextPresent then some .noReplyText
  else if r.proposed.isEmpty then some .noProposedPatterns
  else none

def TurnReading.readingFor (r : TurnReading) (p : String) : Option PatternReading :=
  r.readings.find? (fun row => row.pattern == p)

def TurnReading.completed (r : TurnReading) (p : String) : Bool :=
  match r.readingFor p with
  | some row => row.batch == .ok && row.row.complete
  | none => false

def TurnReading.readerFailure (r : TurnReading) (p : String) : Bool :=
  match r.readingFor p with
  | some ⟨_, .failed _, _⟩ | some ⟨_, .legacyUnknown, _⟩ => true
  | _ => false

def TurnReading.obligationMet (r : TurnReading) : Prop :=
  r.legitimateAbsence.isSome ∨ ∀ p ∈ r.proposed, r.completed p = true

structure CertificateView where
  proposed : List String
  completed : List String
  absence : String
  deriving DecidableEq, Repr

def AbsenceReason.code : AbsenceReason → String
  | .nonOperatorOrigin => "non-operator-origin"
  | .noReply => "no-reply"
  | .noReplyText => "no-reply-text"
  | .noProposedPatterns => "no-proposed-patterns"

def TurnReading.certificateView (r : TurnReading) : CertificateView :=
  { proposed := r.proposed
    completed := r.proposed.filter r.completed
    absence := r.legitimateAbsence.map AbsenceReason.code |>.getD "" }

/-- This is exactly the Bool proposition emitted by the runtime certificate. -/
def CertificateView.obligationBool (v : CertificateView) : Bool :=
  !v.absence.isEmpty || v.proposed.all v.completed.contains

theorem reader_failure_is_not_absence (r : TurnReading) (p : String)
    (hop : r.origin = .operator) (hr : r.replyPresent = true)
    (ht : r.replyTextPresent = true) (hp : p ∈ r.proposed)
    (hf : r.readerFailure p = true) : ¬r.obligationMet := by
  intro hmet
  rcases hmet with habsent | hall
  · simp [TurnReading.legitimateAbsence, hop, hr, ht] at habsent
    rw [habsent] at hp
    simp at hp
  · have hc := hall p hp
    simp only [TurnReading.completed, TurnReading.readerFailure,
      TurnReading.readingFor] at hc hf
    split at hf <;> simp_all

private theorem absence_code_nonempty (r : TurnReading) :
    (!r.certificateView.absence.isEmpty) = r.legitimateAbsence.isSome := by
  simp only [TurnReading.certificateView]
  cases h : r.legitimateAbsence with
  | none => simp
  | some reason =>
      cases reason <;> simp only [Option.map_some, Option.getD_some,
        Option.isSome_some] <;> decide

theorem obligation_met_iff (r : TurnReading) :
    r.certificateView.obligationBool = true ↔ r.obligationMet := by
  rw [CertificateView.obligationBool, Bool.or_eq_true, absence_code_nonempty]
  simp only [TurnReading.obligationMet]
  apply or_congr Iff.rfl
  simp only [TurnReading.certificateView, List.all_eq_true,
    List.contains_iff_mem, List.mem_filter]
  constructor
  · intro h p hp
    exact (h p hp).2
  · intro h p hp
    exact ⟨hp, h p hp⟩

theorem absence_set_is_exactly_four (r : TurnReading) (reason : AbsenceReason)
    : r.legitimateAbsence = some reason ↔
      (reason = .nonOperatorOrigin ∧ r.origin ≠ .operator) ∨
      (reason = .noReply ∧ r.origin = .operator ∧ r.replyPresent = false) ∨
      (reason = .noReplyText ∧ r.origin = .operator ∧ r.replyPresent = true ∧
        r.replyTextPresent = false) ∨
      (reason = .noProposedPatterns ∧ r.origin = .operator ∧
        r.replyPresent = true ∧ r.replyTextPresent = true ∧ r.proposed = []) := by
  cases ho : r.origin <;> cases hr : r.replyPresent <;>
    cases ht : r.replyTextPresent <;> cases reason <;>
    simp [TurnReading.legitimateAbsence, ho, hr, ht]

/-! ## Teeth and concrete witnesses -/

def grounded : PartReading :=
  { element := some "element", turn := some "turn-invented"
    quote := some "five invented words ground this part", who := some "operator" }

def completeRow : PatternRow := ⟨grounded, grounded, grounded, grounded⟩

def voidRow : PatternRow :=
  let v : PartReading := { why := some "model reader failed" }
  ⟨v, v, v, v⟩

def sixPatterns : List String := ["p1", "p2", "p3", "p4", "p5", "p6"]

def preFixWitness : TurnReading :=
  { origin := .operator, replyPresent := true, replyTextPresent := true
    proposed := sixPatterns
    readings := sixPatterns.map fun p => ⟨p, .failed "model/truncated-reply", voidRow⟩ }

def postFixWitness : TurnReading :=
  { origin := .operator, replyPresent := true, replyTextPresent := true
    proposed := sixPatterns
    readings := sixPatterns.map fun p => ⟨p, .ok, completeRow⟩ }

example : preFixWitness.certificateView.proposed.length = 6 := by decide
example : preFixWitness.certificateView.completed = [] := by decide
example : preFixWitness.legitimateAbsence = none := by decide
example : postFixWitness.certificateView.proposed.length = 6 := by decide
example : postFixWitness.certificateView.completed = sixPatterns := by decide

theorem pre_fix_not_met : ¬preFixWitness.obligationMet := by
  rw [← obligation_met_iff]
  decide

theorem post_fix_met : postFixWitness.obligationMet := by
  rw [← obligation_met_iff]
  decide

example : ¬preFixWitness.obligationMet :=
  reader_failure_is_not_absence preFixWitness "p1" (by rfl) (by rfl) (by rfl)
    (by decide) (by decide)

/-- Bad producer: it disguises reader failure as a typed absence. -/
def badAbsenceProducer (_ : TurnReading) : Option AbsenceReason := some .noReply

example : badAbsenceProducer preFixWitness ≠ preFixWitness.legitimateAbsence := by decide

/-- Bad producer: it claims every failed pattern completed. Its certificate
Bool passes, but therefore no longer represents the model obligation. -/
def badCompletedProducer : CertificateView :=
  { proposed := sixPatterns, completed := sixPatterns, absence := "" }

example : badCompletedProducer.obligationBool = true := by decide
example : ¬preFixWitness.obligationMet := pre_fix_not_met
example : ¬(badCompletedProducer.obligationBool = true ↔ preFixWitness.obligationMet) := by
  intro h
  exact pre_fix_not_met (h.mp (by decide))

#print axioms reader_failure_is_not_absence
#print axioms obligation_met_iff
#print axioms absence_set_is_exactly_four

end Circus.MaReading
