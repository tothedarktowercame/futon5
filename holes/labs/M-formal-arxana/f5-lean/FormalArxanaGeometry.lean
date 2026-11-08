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

theorem cube_fibre_neg_one_card_complex : {z : ℂ | z ^ 3 = -1}.ncard = 3 := by
  have hset : {z : ℂ | z ^ 3 = -1} =
      Neg.neg '' (↑(Polynomial.nthRootsFinset 3 (1 : ℂ)) : Set ℂ) := by
    ext z
    simp only [Set.mem_setOf_eq, Set.mem_image, Finset.mem_coe,
      Polynomial.mem_nthRootsFinset (by norm_num : 0 < 3)]
    constructor
    · intro h; exact ⟨-z, (cube_fibre_negation_equation z).mp h, neg_neg z⟩
    · rintro ⟨w, hw, rfl⟩; rw [cube_fibre_negation_equation, neg_neg]; exact hw
  rw [hset, Set.ncard_image_of_injective _ neg_injective, Set.ncard_coe_finset,
    (Complex.isPrimitiveRoot_exp 3 (by norm_num)).card_nthRootsFinset]

theorem cube_fibre_on_unit_circle {z : ℂ} (h : z ^ 3 = -1) : ‖z‖ = 1 := by
  have : ‖z‖ ^ 3 = 1 := by rw [← norm_pow, h, norm_neg, norm_one]
  exact (pow_eq_one_iff_of_nonneg (norm_nonneg z) (by norm_num)).mp this

/-- Cubing on the unit circle sends exactly three points to -1. -/
theorem cube_fibre_neg_one_card : {z : Circle | (z : ℂ) ^ 3 = -1}.ncard = 3 := by
  have h := Set.ncard_image_of_injective {z : Circle | (z : ℂ) ^ 3 = -1}
    (Subtype.val_injective : Function.Injective ((↑) : Circle → ℂ))
  rw [← h]
  convert cube_fibre_neg_one_card_complex using 2
  ext w
  constructor
  · rintro ⟨z, hz, rfl⟩; exact hz
  · intro hw
    refine ⟨⟨w, ?_⟩, hw, rfl⟩
    simpa [Submonoid.unitSphere] using cube_fibre_on_unit_circle hw

theorem inversion_is_conjugation (z : Circle) :
    ((↑z⁻¹ : ℂ)) = starRingEnd ℂ (z : ℂ) := Circle.coe_inv_eq_conj z
