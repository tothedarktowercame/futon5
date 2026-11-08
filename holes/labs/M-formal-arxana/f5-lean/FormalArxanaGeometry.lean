import Mathlib.Analysis.Complex.Circle
import Mathlib.RingTheory.RootsOfUnity.Complex

open Complex Set

def circleI : Circle := ⟨I, by simp [Submonoid.unitSphere]⟩
def circleNegI : Circle := ⟨-I, by simp [Submonoid.unitSphere]⟩

theorem square_fibre_neg_one :
    {z : Circle | (z : ℂ) ^ 2 = -1} = {circleI, circleNegI} := by
  ext ⟨z, hz⟩
  constructor
  · intro h
    have h' : z = I ∨ z = -I := by
      have : z ^ 2 = I ^ 2 := by simpa using h
      exact (sq_eq_sq_iff_eq_or_eq_neg (R := ℂ)).mp this
    rcases h' with rfl | rfl
    · left; rfl
    · right; rfl
  · intro h
    simp only [Set.mem_insert_iff, Set.mem_singleton_iff] at h
    rcases h with h | h
    · have : z = I := Subtype.ext_iff.mp h; simp [this]
    · have : z = -I := Subtype.ext_iff.mp h; simp [this]

/-- Negation identifies the cube fibre over -1 with cube roots of unity. -/
theorem cube_fibre_negation_equation (z : ℂ) :
    z ^ 3 = -1 ↔ (-z) ^ 3 = 1 := by
  constructor <;> intro h
  · rw [neg_pow, h]; norm_num
  · rw [neg_pow] at h
    norm_num at h
    calc
      z ^ 3 = -(-z ^ 3) := by ring
      _ = -1 := by rw [h]

/-- Hence the corresponding root set has exactly three elements. -/
theorem cube_fibre_model_card : Fintype.card (rootsOfUnity 3 ℂ) = 3 :=
  Complex.card_rootsOfUnity 3

theorem inversion_is_conjugation (z : Circle) :
    ((↑z⁻¹ : ℂ)) = starRingEnd ℂ (z : ℂ) := Circle.coe_inv_eq_conj z
