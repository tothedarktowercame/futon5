/-!
# Identity of a 'clusion (DERIVE-core §17, round 9)

A 'clusion is `{type-revision, ordered role-labelled endpoints, asserted content}`.
Its id is a digest of exactly that key; we model the digest as injective, so
two ids are equal iff their keys are. Interpreter, evidence and method
version are not part of the key; they live on attestations (Store.lean).
-/
namespace Arxana

abbrev EntityId := Nat
abbrev Role := String

structure Endpoint where
  role : Role
  target : EntityId
deriving DecidableEq, Repr

structure Clusion where
  typeRev : Nat
  endpoints : List Endpoint
  content : List (String × String)
deriving DecidableEq, Repr

abbrev Key := Nat × List Endpoint × List (String × String)

/-- What the identity digest is computed over. -/
def Clusion.key (c : Clusion) : Key := (c.typeRev, c.endpoints, c.content)

/-- futon1b's default hyperedge identity: endpoint ids, roles dropped (MAP-Q1). -/
def defaultKey (c : Clusion) : List EntityId := c.endpoints.map (·.target)

/-- Swapping two roles over the same endpoints is a different claim. -/
theorem role_swap_changes_id (t : Nat) (a b : EntityId) (r₁ r₂ : Role)
    (cnt : List (String × String)) (h : r₁ ≠ r₂) :
    (Clusion.mk t [⟨r₁, a⟩, ⟨r₂, b⟩] cnt).key ≠ (Clusion.mk t [⟨r₂, a⟩, ⟨r₁, b⟩] cnt).key := by
  intro heq
  simp [Clusion.key] at heq
  exact h heq.1

/-- The role-dropping default identity cannot tell those two claims apart. -/
theorem default_key_merges_role_swap (t : Nat) (a b : EntityId) (r₁ r₂ : Role)
    (cnt : List (String × String)) :
    defaultKey (Clusion.mk t [⟨r₁, a⟩, ⟨r₂, b⟩] cnt) =
      defaultKey (Clusion.mk t [⟨r₂, a⟩, ⟨r₁, b⟩] cnt) := by
  simp [defaultKey]

/-- A new type revision is a new identity family (declarations are never edited in place). -/
theorem type_revision_in_id (c : Clusion) (t : Nat) (h : t ≠ c.typeRev) :
    ({ c with typeRev := t }).key ≠ c.key := by
  intro heq
  simp [Clusion.key] at heq
  exact h heq

end Arxana
