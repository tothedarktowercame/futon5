/-!
# Composition graphs are DAGs (DERIVE-core §17, F4)

C1 declares the cycle policy: a composite 'clusion's wiring must admit a
rank on stages that strictly increases along every wire. The validator is
`checkRanked`; a checked composition has no cycles. Cyclic composition
would need a type-declared fixed-point operator, which this mission does
not provide.
-/
namespace Arxana

structure Composition where
  stages : List Nat
  wires : List (Nat × Nat)  -- (from-stage, to-stage)
deriving DecidableEq, Repr

def Ranked (rank : Nat → Nat) (c : Composition) : Prop :=
  ∀ w ∈ c.wires, rank w.1 < rank w.2

/-- The validator C1/F4 would run at `clusion/assert` time. -/
def checkRanked (rank : Nat → Nat) (c : Composition) : Bool :=
  c.wires.all (fun w => decide (rank w.1 < rank w.2))

theorem checkRanked_sound (rank : Nat → Nat) (c : Composition)
    (h : checkRanked rank c = true) : Ranked rank c := by
  intro w hw
  have := List.all_eq_true.mp h w hw
  simpa using this

inductive Path (c : Composition) : Nat → Nat → Prop
  | wire {a b} : (a, b) ∈ c.wires → Path c a b
  | trans {a b d} : Path c a b → Path c b d → Path c a d

theorem path_rank_lt (rank : Nat → Nat) (c : Composition) (h : Ranked rank c)
    {a b : Nat} (p : Path c a b) : rank a < rank b := by
  induction p with
  | wire hw => exact h _ hw
  | trans _ _ ih₁ ih₂ => exact Nat.lt_trans ih₁ ih₂

/-- A validated composition has no cycle through any stage. -/
theorem ranked_acyclic (rank : Nat → Nat) (c : Composition) (h : Ranked rank c) (a : Nat) :
    ¬ Path c a a := fun p => Nat.lt_irrefl _ (path_rank_lt rank c h p)

/-- A self-loop can never be ranked, so the validator refuses it. -/
theorem self_loop_refused (rank : Nat → Nat) (c : Composition) (a : Nat)
    (hw : (a, a) ∈ c.wires) : checkRanked rank c = false := by
  cases hc : checkRanked rank c
  · rfl
  · exact absurd (Path.wire hw) (ranked_acyclic rank c (checkRanked_sound rank c hc) a)

end Arxana
