import ArxanaCore.Identity
/-!
# The append-only store: claims, attestations, assessments, successors

Evidence and acceptance are separate records citing a claim's key
(DERIVE-core §6, §17). The store is an append-only log.
-/
namespace Arxana

inductive Basis | exact | parser | model | human | kernel | composite
deriving DecidableEq, Repr
inductive EStanding | unverified | verified | contradicted | disputed
deriving DecidableEq, Repr
inductive Coverage | none | «partial» | complete
deriving DecidableEq, Repr

structure Attestation where
  subject : Key
  producer : String
  basis : Basis
  standing : EStanding
  coverage : Coverage
deriving DecidableEq, Repr

inductive Verdict | accepted | rejected
deriving DecidableEq, Repr

structure Assessment where
  subject : Key
  actor : String
  verdict : Verdict
deriving DecidableEq, Repr

inductive Record
  | clusion (c : Clusion)
  | attest (a : Attestation)
  | assess (a : Assessment)
  | supersede (old new : Key)
deriving DecidableEq, Repr


/-- Append one record to the log. -/
def push (s : List Record) (r : Record) : List Record := s ++ [r]

def claimOf : Record → Option Key
  | .clusion c => some c.key
  | _ => none

def claims (s : List Record) : List Key := s.filterMap claimOf

def attestOf (k : Key) : Record → Option Attestation
  | .attest a => if a.subject = k then some a else none
  | _ => none

def evidence (s : List Record) (k : Key) : List Attestation := s.filterMap (attestOf k)

def assessOf (k : Key) : Record → Option Assessment
  | .assess a => if a.subject = k then some a else none
  | _ => none

def assessments (s : List Record) (k : Key) : List Assessment := s.filterMap (assessOf k)

inductive Acceptance | candidate | accepted | rejected
deriving DecidableEq, Repr

/-- Selecting one acceptance from a claim's assessments needs a **named
policy** (DERIVE-core §18, codex-21 round 10): append order alone is not
semantic authority, and conflicting assessments may coexist unresolved. A
policy sees every assessment of the claim; the only constraint on it is that
with no assessment the claim is a candidate. -/
structure AcceptancePolicy where
  name : String
  decide : List Assessment → Acceptance
  empty_is_candidate : decide [] = .candidate

def acceptance (p : AcceptancePolicy) (s : List Record) (k : Key) : Acceptance :=
  p.decide (assessments s k)

/-! ## Append-only -/

theorem append_extends (s : List Record) (r : Record) : s <+: push s r :=
  List.prefix_append s [r]

theorem append_keeps (s : List Record) (r r' : Record) (h : r' ∈ s) : r' ∈ push s r :=
  List.mem_append_left _ h

/-! ## Evidence and acceptance never rewrite claims -/

theorem attest_keeps_claims (s : List Record) (a : Attestation) :
    claims (push s (.attest a)) = claims s := by
  simp [push, claims, claimOf]

theorem assess_keeps_claims (s : List Record) (a : Assessment) :
    claims (push s (.assess a)) = claims s := by
  simp [push, claims, claimOf]

/-- A successor does not erase the claim it supersedes. -/
theorem supersede_keeps_claims (s : List Record) (o n : Key) :
    claims (push s (.supersede o n)) = claims s := by
  simp [push, claims, claimOf]

/-- An assessment never rewrites evidence (standing ≠ acceptance). -/
theorem assess_keeps_evidence (s : List Record) (a : Assessment) (k : Key) :
    evidence (push s (.assess a)) k = evidence s k := by
  simp [push, evidence, attestOf]

/-- New evidence never changes acceptance. -/
theorem attest_keeps_acceptance (p : AcceptancePolicy) (s : List Record) (a : Attestation) (k : Key) :
    acceptance p (push s (.attest a)) k = acceptance p s k := by
  simp [acceptance, assessments, push, assessOf]

/-- With no assessment, a claim is a candidate: never accepted by default. -/
theorem unassessed_is_candidate (p : AcceptancePolicy) (s : List Record) (k : Key)
    (h : assessments s k = []) : acceptance p s k = .candidate := by
  simp [acceptance, h, p.empty_is_candidate]

/-- Assessing one claim leaves every other claim's acceptance alone
(stage-local criticism at the record level, F4). -/
theorem assess_is_local (p : AcceptancePolicy) (s : List Record) (a : Assessment) (k : Key)
    (h : a.subject ≠ k) : acceptance p (push s (.assess a)) k = acceptance p s k := by
  simp [acceptance, assessments, push, assessOf, h]

/-- Conflicting assessments by different actors coexist: appending a second
never removes the first. -/
theorem conflicting_assessments_coexist (s : List Record) (a₁ a₂ : Assessment) (k : Key)
    (h₁ : a₁.subject = k) (h₂ : a₂.subject = k) :
    a₁ ∈ assessments (push (push s (.assess a₁)) (.assess a₂)) k ∧
    a₂ ∈ assessments (push (push s (.assess a₁)) (.assess a₂)) k := by
  constructor <;> simp [assessments, push, assessOf, h₁, h₂]

theorem evidence_push_attest (s : List Record) (a : Attestation) (k : Key) :
    evidence (push s (.attest a)) k = evidence s k ++ (if a.subject = k then [a] else []) := by
  by_cases h : a.subject = k <;> simp [evidence, push, attestOf, h]

/-- Two interpreters making the same claim: one key, both attestations kept. -/
theorem two_interpreters_one_claim (s : List Record) (a₁ a₂ : Attestation) (k : Key)
    (h₁ : a₁.subject = k) (h₂ : a₂.subject = k) :
    a₁ ∈ evidence (push (push s (.attest a₁)) (.attest a₂)) k ∧
    a₂ ∈ evidence (push (push s (.attest a₁)) (.attest a₂)) k := by
  rw [evidence_push_attest, evidence_push_attest]
  simp [h₁, h₂]

/-! ## Merging replicas keeps both sides (no last-writer erasure) -/

def merge (a b : List Record) : List Record := a ++ b.filter (fun r => r ∉ a)

theorem merge_keeps_left (a b : List Record) (r : Record) (h : r ∈ a) : r ∈ merge a b :=
  List.mem_append_left _ h

theorem merge_keeps_right (a b : List Record) (r : Record) (h : r ∈ b) : r ∈ merge a b := by
  by_cases ha : r ∈ a
  · exact List.mem_append_left _ ha
  · exact List.mem_append_right _ (List.mem_filter.mpr ⟨h, by simp [ha]⟩)

theorem merge_only_from_inputs (a b : List Record) (r : Record) (h : r ∈ merge a b) :
    r ∈ a ∨ r ∈ b := by
  rcases List.mem_append.mp h with h | h
  · exact .inl h
  · exact .inr (List.mem_filter.mp h).1

/-- Merge order does not change what survives. -/
theorem merge_comm_mem (a b : List Record) (r : Record) : r ∈ merge a b ↔ r ∈ merge b a := by
  constructor
  · intro h
    rcases merge_only_from_inputs a b r h with h | h
    · exact merge_keeps_right b a r h
    · exact merge_keeps_left b a r h
  · intro h
    rcases merge_only_from_inputs b a r h with h | h
    · exact merge_keeps_right a b r h
    · exact merge_keeps_left a b r h

end Arxana
