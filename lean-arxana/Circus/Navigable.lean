import Circus.Basic

/-!
# Navigability of the Circus/GCC schema and its UIs

`S a b` says a role-reverse-capable graph query can move from kind `a` to
kind `b`. Each direction of a stored edge type is represented separately.
`U a b` says a UI exposes one link or key making that move.
-/
namespace Circus.Navigable

/-- The small enumeration contract needed by the executable reachability
checker. It is local because lean-arxana deliberately has no Mathlib
dependency. -/
class Fintype (α : Type) where
  elems : List α
  complete : ∀ a : α, a ∈ elems

variable {Kind : Type} [DecidableEq Kind] [Fintype Kind]

/-- A path of at most `n` moves. `more` accounts for unused budget. -/
inductive Reach (R : Kind → Kind → Prop) : Nat → Kind → Kind → Prop
  | refl (a : Kind) : Reach R 0 a a
  | step (n : Nat) (a c b : Kind) (head : R a c) (tail : Reach R n c b) :
      Reach R (n + 1) a b
  | more (n : Nat) (a b : Kind) (path : Reach R n a b) : Reach R (n + 1) a b

theorem Reach.pad {R : Kind → Kind → Prop} {a b : Kind} :
    ∀ {m n}, m ≤ n → Reach R m a b → Reach R n a b := by
  intro m n h path
  induction h with
  | refl => exact path
  | step _ ih => exact .more _ _ _ ih

theorem Reach.comp {R : Kind → Kind → Prop} {a b c : Kind} {m n : Nat}
    (ab : Reach R m a b) (bc : Reach R n b c) : Reach R (m + n) a c := by
  induction ab with
  | refl => simpa using bc
  | step p a d b edge tail ih =>
      simpa [Nat.add_assoc, Nat.add_comm n 1] using
        Reach.step (p + n) a d c edge (ih bc)
  | more p a b path ih =>
      simpa [Nat.add_assoc, Nat.add_comm n 1] using
        Reach.more (p + n) a c (ih bc)

/-- Every schema move expands to at most `k` UI moves, so an `n`-move schema
path expands to at most `k*n` UI moves. -/
theorem cover_bound {S U : Kind → Kind → Prop} {k n : Nat} {a b : Kind}
    (covers : ∀ x y, S x y → Reach U k x y)
    (path : Reach S n a b) : Reach U (k * n) a b := by
  induction path with
  | refl => exact .refl _
  | step p a c b edge tail ih =>
      have joined := Reach.comp (covers a c edge) ih
      simpa [Nat.mul_add, Nat.add_comm] using joined
  | more p a b path ih =>
      exact Reach.pad (Nat.mul_le_mul_left k (Nat.le_succ p)) ih

/-- A missing UI route stays missing; the schema edge itself is nevertheless
one-step reachable. No claim about any other pair is assumed. -/
theorem missing_edge {S U : Kind → Kind → Prop} {a b : Kind}
    (edge : S a b) (absent : ¬∃ n, Reach U n a b) :
    Reach S 1 a b ∧ ¬∃ n, Reach U n a b := by
  exact ⟨.step 0 a b b edge (.refl b), absent⟩

/-- A set of kinds that contains `a` and that no UI move leaves cannot reach a
kind outside it, at any bound. This is the witness a certificate gives for an
unreachable pair: the closed set (for example, everything the UI reaches from
`a`), with the schema edge that leaves it. -/
theorem closed_blocks {U : Kind → Kind → Prop} (C : Kind → Prop)
    (closed : ∀ x y, C x → U x y → C y) :
    ∀ {n a b}, Reach U n a b → C a → C b := by
  intro n a b path
  induction path with
  | refl => exact id
  | step _ a c b edge _ ih => exact fun ha => ih (closed a c ha edge)
  | more _ _ _ _ ih => exact ih

/-- A schema edge that leaves a UI-closed set containing `a` is a real gap:
one schema step, never reachable through the UI. -/
theorem gap_of_closed {S U : Kind → Kind → Prop} (C : Kind → Prop)
    (closed : ∀ x y, C x → U x y → C y) {a b : Kind}
    (ha : C a) (hb : ¬C b) (edge : S a b) :
    Reach S 1 a b ∧ ¬∃ n, Reach U n a b :=
  ⟨.step 0 a b b edge (.refl b), fun ⟨_, path⟩ => hb (closed_blocks C closed path ha)⟩

/-- If every UI move is realised by some schema path, a UI path cannot
fabricate schema connectivity. Its schema bound may grow by varying amounts. -/
theorem ui_only_within_schema {S U : Kind → Kind → Prop} {n : Nat} {a b : Kind}
    (sound : ∀ x y, U x y → ∃ k, Reach S k x y)
    (path : Reach U n a b) : ∃ k, Reach S k a b := by
  induction path with
  | refl => exact ⟨0, .refl _⟩
  | step p a c b edge tail ih =>
      obtain ⟨m, head⟩ := sound a c edge
      obtain ⟨q, rest⟩ := ih
      exact ⟨m + q, Reach.comp head rest⟩
  | more _ _ _ _ ih => exact ih

/-- With one-move coverage in both directions, UI and schema reachability
coincide at every bound. -/
theorem exact {S U : Kind → Kind → Prop}
    (su : ∀ x y, S x y → Reach U 1 x y)
    (us : ∀ x y, U x y → Reach S 1 x y) :
    ∀ n a b, Reach U n a b ↔ Reach S n a b := by
  intro n a b
  constructor
  · intro path
    simpa using cover_bound us path
  · intro path
    simpa using cover_bound su path

/-- A via-home UI has diameter at most two independently of the schema. This
is why a map's via-home figure is a cheap navigability measurement. -/
theorem hub (U : Kind → Kind → Prop) (home : Kind)
    (toHome : ∀ a, U a home) (fromHome : ∀ b, U home b) :
    ∀ a b, Reach U 2 a b := by
  intro a b
  exact .step 1 a home b (toHome a)
    (.step 0 home b b (fromHome b) (.refl b))

/-! ## Executable finite reachability -/

def reachBool (R : Kind → Kind → Bool) : Nat → Kind → Kind → Bool
  | 0, a, b => a == b
  | n + 1, a, b =>
      reachBool R n a b ||
        Fintype.elems.any fun c => R a c && reachBool R n c b

theorem reachBool_iff (R : Kind → Kind → Bool) :
    ∀ n a b, reachBool R n a b = true ↔ Reach (fun x y => R x y = true) n a b := by
  intro n
  induction n with
  | zero =>
      intro a b
      simp [reachBool]
      constructor
      · intro h; subst b; exact .refl a
      · intro h; cases h; rfl
  | succ n ih =>
      intro a b
      simp only [reachBool, Bool.or_eq_true, List.any_eq_true, Bool.and_eq_true]
      constructor
      · intro h
        rcases h with old | ⟨c, _, edge, tail⟩
        · exact .more n a b ((ih a b).mp old)
        · exact .step n a c b edge ((ih c b).mp tail)
      · intro h
        cases h with
        | step _ _ c _ edge tail =>
            exact Or.inr ⟨c, Fintype.complete c, edge, (ih c b).mpr tail⟩
        | more _ _ _ old => exact Or.inl ((ih a b).mpr old)

def decideReach (R : Kind → Kind → Prop) [DecidableRel R]
    (n : Nat) (a b : Kind) : Bool :=
  reachBool (fun x y => decide (R x y)) n a b

theorem decideReach_iff (R : Kind → Kind → Prop) [DecidableRel R]
    (n : Nat) (a b : Kind) :
    decideReach R n a b = true ↔ Reach R n a b := by
  simpa [decideReach] using reachBool_iff (fun x y => decide (R x y)) n a b

/-! ## Teeth -/
namespace Teeth

inductive K | turn | mission | pattern | home deriving DecidableEq

instance : Fintype K where
  elems := [.turn, .mission, .pattern, .home]
  complete := by intro a; cases a <;> simp

def schema : K → K → Bool
  | .turn, .mission | .mission, .pattern => true
  | _, _ => false

def missingUI : K → K → Bool
  | .mission, .pattern => true
  | _, _ => false

example : reachBool schema 1 .turn .mission = true := by decide
example : reachBool missingUI 4 .turn .mission = false := by decide
-- The same gap at every bound, from a closed set: {turn} is closed under missingUI.
example : Reach (fun x y => schema x y = true) 1 .turn .mission ∧
    ¬∃ n, Reach (fun x y => missingUI x y = true) n .turn .mission :=
  gap_of_closed (fun k => k = .turn) (by intro x y hx h; subst hx; cases y <;> simp_all [missingUI])
    rfl (by decide) rfl

def hubUI (a b : K) : Bool :=
  missingUI a b || decide (b = .home) || decide (a = .home)

example : reachBool hubUI 2 .turn .mission = true := by decide
example : reachBool hubUI 1 .turn .mission = false := by decide

def tightSchema : K → K → Bool
  | .turn, .pattern => true
  | _, _ => false

def tightUI : K → K → Bool
  | .turn, .mission | .mission, .pattern => true
  | _, _ => false

example : reachBool tightSchema 1 .turn .pattern = true := by decide
example : reachBool tightUI 1 .turn .pattern = false := by decide
example : reachBool tightUI 2 .turn .pattern = true := by decide

end Teeth

#print axioms cover_bound
#print axioms missing_edge
#print axioms gap_of_closed
#print axioms ui_only_within_schema
#print axioms exact
#print axioms hub
#print axioms reachBool_iff
#print axioms decideReach_iff

end Circus.Navigable
