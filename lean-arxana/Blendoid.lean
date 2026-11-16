/-!
# Blendoids: the 2015 blend, subcubes, operators (core Lean, no Mathlib)

claude-1 (Lucy), 2026-11-15, at Joe's request via claude-4. Formalises the outline
`exotype/DESIGN-blendoid.md` (main b75b3d6). Vocabulary reference for §7 (not used
here): `DarkTower/WarMachine/ThreeHalvesBlend.lean` on Zone.

The boundary between proved and stated is listed at the end of the file
(section "Ledger") and is the point of this module: every `theorem` below is proved
without `sorry`; statements that are NOT proved are `def … : Prop` and say so.
`native_decide` is used only in section 6 (counts over S8), and is marked there.
-/
namespace Blendoid

/-! ## 1. Carrier -/

/-- Neighbourhood `n = 4l + 2c + r` (Wolfram order). -/
abbrev Nbhd := Fin 8
/-- An elementary CA rule: output bit per neighbourhood. -/
abbrev Rule := Nbhd → Bool

/-- The neighbourhood index of the bit triple `(l, c, r)`. -/
def idx (l c r : Bool) : Nbhd :=
  ⟨4 * l.toNat + 2 * c.toNat + r.toNat, by cases l <;> cases c <;> cases r <;> decide⟩

/-- The 2015 blend (futon5 `grid/blend-rule`): where L and R agree their shared bit
passes; where they conflict, C arbitrates by reading its own table at the column
triple. -/
def b (L C R : Rule) : Rule := fun n =>
  if L n = R n then L n else C (idx (L n) (C n) (R n))

/-- Bitwise majority. -/
def maj (x y z : Rule) : Rule := fun n => (x n && y n) || (y n && z n) || (x n && z n)

/-- A rule from its Wolfram code. -/
def ofCode (k : Nat) : Rule := fun n => k.testBit n.val

/-! ## 2. Laws of b -/

theorem b_idem (x : Rule) : b x x x = x := by
  funext n; simp [b]

/-- Near-unanimity in the outer arguments. -/
theorem b_outer (x y : Rule) : b x y x = x := by
  funext n; simp [b]

/-- `b x x y = x` fails: x = rule 1, y = rule 0 (at neighbourhood 0). -/
theorem b_left_nu_fails : b (ofCode 1) (ofCode 1) (ofCode 0) ≠ ofCode 1 := by
  intro h
  have := congrFun h 0
  revert this; decide

/-- The mirror law `b x y z = b z y x` fails: (x, y, z) = rules (0, 2, 1). -/
theorem b_mirror_fails : b (ofCode 0) (ofCode 2) (ofCode 1) ≠ b (ofCode 1) (ofCode 2) (ofCode 0) := by
  intro h
  have := congrFun h 0
  revert this; decide

theorem maj_left (x y : Rule) : maj x x y = x := by
  funext n; simp only [maj]; cases x n <;> cases y n <;> rfl
theorem maj_mid (x y : Rule) : maj x y x = x := by
  funext n; simp only [maj]; cases x n <;> cases y n <;> rfl
theorem maj_right (x y : Rule) : maj y x x = x := by
  funext n; simp only [maj]; cases x n <;> cases y n <;> rfl

/-! ## 3. Subcubes (over any coordinate type) -/

/-- A subcube: `S a = some v` fixes coordinate `a` to `v`; `none` leaves it free. -/
def Subcube (α : Type) := α → Option Bool

/-- Membership of a rule in a subcube. -/
def Mem {α : Type} (S : Subcube α) (R : α → Bool) : Prop :=
  ∀ a v, S a = some v → R a = v

/-- Any free coordinates filled with the constant `d`: a member of `S`. -/
def fill {α : Type} (S : Subcube α) (d : Bool) : α → Bool := fun a =>
  match S a with
  | some v => v
  | none => d

theorem fill_mem {α : Type} (S : Subcube α) (d : Bool) : Mem S (fill S d) := by
  intro a v h; simp [fill, h]

/-- **Lemma A.** Every subcube is closed under `b` (structural proof). -/
theorem lemmaA (S : Subcube Nbhd) {x y z : Rule}
    (hx : Mem S x) (_hy : Mem S y) (hz : Mem S z) : Mem S (b x y z) := by
  intro a v h
  simp [b, hx a v h, hz a v h]

/-- The median object M: copy the centre on every edge neighbourhood
(001 ↦ 0, 011 ↦ 1, 100 ↦ 0, 110 ↦ 1). -/
def M : Subcube Nbhd := fun n =>
  match n.val with
  | 1 => some false
  | 3 => some true
  | 4 => some false
  | 6 => some true
  | _ => none

theorem mem_M {R : Rule} (h : Mem M R) :
    R 1 = false ∧ R 3 = true ∧ R 4 = false ∧ R 6 = true :=
  ⟨h 1 false rfl, h 3 true rfl, h 4 false rfl, h 6 true rfl⟩

/-- On M the blend is bitwise majority (structural proof). -/
theorem b_eq_maj_on_M {x y z : Rule} (_hx : Mem M x) (hy : Mem M y) (_hz : Mem M z) :
    b x y z = maj x y z := by
  obtain ⟨h1, h3, h4, h6⟩ := mem_M hy
  funext n
  simp only [b, maj]
  cases hxn : x n <;> cases hyn : y n <;> cases hzn : z n <;>
    simp [idx, h1, h3, h4, h6] <;> rfl

/-- M is closed under majority. -/
theorem maj_mem {α : Type} (S : Subcube α) {x y z : α → Bool}
    (hx : Mem S x) (hy : Mem S y) (hz : Mem S z) :
    Mem S (fun n => (x n && y n) || (y n && z n) || (x n && z n)) := by
  intro a v h; simp [hx a v h, hy a v h, hz a v h]

/-! ## 4. Operators -/

/-- The write operator `w_{f,s} R := R[f s ↦ ¬ R s]` (futon5 `gen/rule-permute`). -/
def w {α : Type} [DecidableEq α] (f : α → α) (s : α) (R : α → Bool) : α → Bool :=
  fun n => if n = f s then !(R s) else R n

/-- The family `{w_{f,s}}_s` keeps the subcube. -/
def Keeps {α : Type} [DecidableEq α] (f : α → α) (S : Subcube α) : Prop :=
  ∀ s R, Mem S R → Mem S (w f s R)

/-- **Lemma B** (structural, any coordinate type, any map `f`). The family keeps
`(D, v)` iff D is closed under preimages of f and v alternates along f inside D. -/
theorem lemmaB {α : Type} [DecidableEq α] (f : α → α) (S : Subcube α) :
    Keeps f S ↔
      (∀ s v, S (f s) = some v → (S s).isSome) ∧
      (∀ s u v, S s = some u → S (f s) = some v → v = !u) := by
  constructor
  · intro hk
    refine ⟨?_, ?_⟩
    · intro s v hfs
      cases hs : S s with
      | some u => rfl
      | none =>
        exfalso
        have hm := hk s (fill S v) (fill_mem S v) (f s) v hfs
        simp [w, fill, hs] at hm
    · intro s u v hs hfs
      have hm := hk s (fill S false) (fill_mem S false) (f s) v hfs
      simp only [w, if_pos rfl, fill, hs] at hm
      exact hm.symm
  · rintro ⟨hpre, halt⟩ s R hR a v ha
    by_cases hafs : a = f s
    · subst hafs
      simp only [w, if_pos rfl]
      have hsome := hpre s v ha
      cases hs : S s with
      | none => simp [hs] at hsome
      | some u =>
        rw [hR s u hs, halt s u v hs ha]; simp
    · simp only [w, if_neg hafs]
      exact hR a v ha

/-! ### Theorem C: permutations of a finite coordinate set -/

/-- A duplicate-free list whose elements all lie in `m` is no longer than `m`. -/
theorem length_le_of_nodup_subset {β : Type} [DecidableEq β] :
    ∀ (l m : List β), l.Nodup → (∀ x ∈ l, x ∈ m) → l.length ≤ m.length
  | [], _, _, _ => Nat.zero_le _
  | x :: l, m, hnd, hsub => by
    have hx : x ∈ m := hsub x (by simp)
    have hnd' := List.nodup_cons.1 hnd
    have ih := length_le_of_nodup_subset l (m.erase x) hnd'.2 (by
      intro y hy
      have hyx : y ≠ x := fun h => hnd'.1 (h ▸ hy)
      exact (List.mem_erase_of_ne hyx).2 (hsub y (by simp [hy])))
    rw [List.length_erase_of_mem hx] at ih
    have : 0 < m.length := List.length_pos_of_mem hx
    simp; omega

theorem nodup_finRange (n : Nat) : (List.finRange n).Nodup := by
  induction n with
  | zero => simp
  | succ n ih =>
    rw [List.finRange_succ]
    refine List.nodup_cons.2 ⟨?_, ?_⟩
    · simp [Fin.succ_ne_zero]
    · exact List.Pairwise.map Fin.succ (fun a b h hs => h (Fin.succ_inj.1 hs)) ih

/-- Pigeonhole on `Fin n`: an injective map sending a decidable set into itself
sends it onto itself. -/
theorem onto_of_into {n : Nat} (g : Fin n → Fin n) (hinj : ∀ a b, g a = g b → a = b)
    (P : Fin n → Prop) [DecidablePred P] (hinto : ∀ a, P a → P (g a)) :
    ∀ a, P a → ∃ c, P c ∧ g c = a := by
  intro a ha
  refine Classical.byContradiction fun hno => ?_
  let l := (List.finRange n).filter (fun x => decide (P x))
  have hnd : l.Nodup := List.Pairwise.filter _ (nodup_finRange n)
  have hmapnd : (l.map g).Nodup :=
    List.Pairwise.map g (fun x y hxy hg => hxy (hinj x y hg)) hnd
  have hsub : ∀ y ∈ l.map g, y ∈ l.erase a := by
    intro y hy
    obtain ⟨x, hx, rfl⟩ := List.mem_map.1 hy
    have hPx : P x := by simpa [l] using (List.mem_filter.1 hx).2
    have hne : g x ≠ a := fun h => hno ⟨x, hPx, h⟩
    exact (List.mem_erase_of_ne hne).2 (by simp [l, List.mem_finRange, hinto x hPx])
  have hle := length_le_of_nodup_subset _ _ hmapnd hsub
  have hal : a ∈ l := by simp [l, List.mem_finRange, ha]
  rw [List.length_map, List.length_erase_of_mem hal] at hle
  have : 0 < l.length := List.length_pos_of_mem hal
  omega

/-- **Theorem C, structural part.** For a permutation σ of `Fin n` (given with its
inverse τ), the family keeps `(D, v)` iff σ maps D onto D and swaps the 0-set and
the 1-set of v. -/
theorem theoremC {n : Nat} (σ τ : Fin n → Fin n)
    (hστ : ∀ a, σ (τ a) = a) (_hτσ : ∀ a, τ (σ a) = a) (S : Subcube (Fin n)) :
    Keeps σ S ↔
      (∀ a, (S a).isSome = (S (σ a)).isSome) ∧
      (∀ a u v, S a = some u → S (σ a) = some v → v = !u) := by
  rw [lemmaB]
  constructor
  · rintro ⟨hpre, halt⟩
    refine ⟨?_, halt⟩
    -- preimage-closed: (S (σ s)).isSome → (S s).isSome
    have hpre' : ∀ s, (S (σ s)).isSome = true → (S s).isSome = true := by
      intro s h
      cases hs : S (σ s) with
      | none => simp [hs] at h
      | some v => exact hpre s v hs
    -- so D is closed under τ; τ is injective, hence onto D
    have hinto : ∀ a, (S a).isSome = true → (S (τ a)).isSome = true := by
      intro a h; apply hpre'; rw [hστ]; exact h
    have hinj : ∀ a b, τ a = τ b → a = b := by
      intro a b h; have := congrArg σ h; simpa [hστ] using this
    intro a
    cases hA : (S a).isSome
    · -- a ∉ D, so σ a ∉ D by preimage closure
      cases hB : (S (σ a)).isSome
      · rfl
      · have := hpre' a hB; rw [hA] at this; exact absurd this (by simp)
    · obtain ⟨c, hc, hτc⟩ := onto_of_into τ hinj (fun a => (S a).isSome = true) hinto a hA
      have : σ a = c := by rw [← hτc, hστ]
      rw [this, hc]
  · rintro ⟨hD, halt⟩
    refine ⟨?_, halt⟩
    intro s v h
    rw [hD s, h]; rfl

/-- Corollary (structural): a subcube fixing exactly one coordinate is never kept by
a permutation. -/
theorem single_never_kept {n : Nat} (σ τ : Fin n → Fin n)
    (hστ : ∀ a, σ (τ a) = a) (hτσ : ∀ a, τ (σ a) = a) (a₀ : Fin n) (v₀ : Bool) :
    ¬ Keeps σ (fun a => if a = a₀ then some v₀ else none) := by
  intro hk
  obtain ⟨hD, halt⟩ := (theoremC σ τ hστ hτσ _).1 hk
  have h1 := hD a₀
  simp only [if_pos rfl, Option.isSome_some] at h1
  have hfix : σ a₀ = a₀ := by
    by_cases h : σ a₀ = a₀
    · exact h
    · simp [h] at h1
  have := halt a₀ v₀ v₀ (by simp) (by simp [hfix])
  cases v₀ <;> simp at this

/-! ### Defects (ams-claude-1: exotype results/s9-boundary-moves.txt, scripts/s9_defects.py)

σ is a map on `Fin n` (injective, or a permutation where stated). A *defect* of R
is a position k with `R k = R (σ k)`. The write `w σ s` sets `R (σ s) := ¬ R s`. -/

section Defects

/-- Sum over a list of a `Nat`-valued function. -/
theorem lsum_le {β : Type} (l : List β) (f g : β → Nat) (h : ∀ k, f k ≤ g k) :
    (l.map f).sum ≤ (l.map g).sum := by
  induction l with
  | nil => simp
  | cons x xs ih => simp only [List.map_cons, List.sum_cons]; exact Nat.add_le_add (h x) ih

theorem lsum_add {β : Type} (l : List β) (f g : β → Nat) :
    (l.map (fun k => f k + g k)).sum = (l.map f).sum + (l.map g).sum := by
  induction l with
  | nil => simp
  | cons x xs ih => simp only [List.map_cons, List.sum_cons, ih]; omega

theorem lsum_eq_zero {β : Type} (l : List β) (f : β → Nat) :
    (l.map f).sum = 0 ↔ ∀ x ∈ l, f x = 0 := by
  induction l with
  | nil => simp
  | cons x xs ih => simp only [List.map_cons, List.sum_cons, List.mem_cons]; constructor
                    · intro h y hy
                      rcases hy with rfl | hy
                      · omega
                      · exact ih.1 (by omega) y hy
                    · intro h
                      have h1 := h x (Or.inl rfl)
                      have h2 := ih.2 (fun y hy => h y (Or.inr hy))
                      omega

theorem lsum_zero {β : Type} [DecidableEq β] (l : List β) (a : β) (c : Nat) (ha : a ∉ l) :
    (l.map (fun k => if k = a then c else 0)).sum = 0 :=
  (lsum_eq_zero _ _).2 fun x hx => by
    have : x ≠ a := fun h => ha (h ▸ hx)
    simp [this]

theorem lsum_single {β : Type} [DecidableEq β] (l : List β) (hl : l.Nodup) (a : β) (c : Nat)
    (ha : a ∈ l) : (l.map (fun k => if k = a then c else 0)).sum = c := by
  induction l with
  | nil => simp at ha
  | cons x xs ih =>
    obtain ⟨hxn, hnd⟩ := List.nodup_cons.1 hl
    by_cases hx : x = a
    · subst hx
      simp [lsum_zero xs x c hxn]
    · have hxs : a ∈ xs := by
        rcases List.mem_cons.1 ha with h | h
        · exact absurd h.symm hx
        · exact h
      simp only [List.map_cons, List.sum_cons, if_neg hx, ih hnd hxs, Nat.zero_add]

variable {n : Nat}

/-- Sum over `Fin n`. -/
def fsum (f : Fin n → Nat) : Nat := ((List.finRange n).map f).sum

/-- Exchange: if `f` plus `c` at `s` is pointwise below `g` plus `d` at `t`, then
`fsum f + c ≤ fsum g + d`. -/
theorem fsum_exchange (f g : Fin n → Nat) (s t : Fin n) (c d : Nat)
    (h : ∀ k, f k + (if k = s then c else 0) ≤ g k + (if k = t then d else 0)) :
    fsum f + c ≤ fsum g + d := by
  have h1 := lsum_le (List.finRange n) (fun k => f k + (if k = s then c else 0))
    (fun k => g k + (if k = t then d else 0)) h
  rw [lsum_add, lsum_add, lsum_single _ (nodup_finRange n) s c (List.mem_finRange s),
    lsum_single _ (nodup_finRange n) t d (List.mem_finRange t)] at h1
  exact h1

theorem le_fsum (f : Fin n → Nat) (k : Fin n) : f k ≤ fsum f := by
  have := fsum_exchange (fun _ => 0) f k k (f k) 0 (fun j => by
    by_cases hj : j = k
    · subst hj; simp
    · simp [hj])
  simp [fsum] at this ⊢
  omega

theorem fsum_eq_zero (f : Fin n → Nat) : fsum f = 0 ↔ ∀ k, f k = 0 := by
  unfold fsum; rw [lsum_eq_zero]; simp [List.mem_finRange]

/-- Defect indicator at k: bit k equals bit σ k. -/
def dI (σ : Fin n → Fin n) (R : Fin n → Bool) (k : Fin n) : Nat :=
  if R k = R (σ k) then 1 else 0

/-- The defect count `#{k : R k = R (σ k)}`. -/
def defects (σ : Fin n → Fin n) (R : Fin n → Bool) : Nat := fsum (dI σ R)

/-- R alternates along σ: no defects anywhere. -/
def Alternating (σ : Fin n → Fin n) (R : Fin n → Bool) : Prop := ∀ k, R (σ k) = !R k

theorem dI_zero_iff (σ : Fin n → Fin n) (R : Fin n → Bool) (k : Fin n) :
    dI σ R k = 0 ↔ R (σ k) = !R k := by
  unfold dI; cases R k <;> cases R (σ k) <;> simp

theorem defects_zero_iff (σ : Fin n → Fin n) (R : Fin n → Bool) :
    defects σ R = 0 ↔ Alternating σ R := by
  unfold defects Alternating; rw [fsum_eq_zero]; simp only [dI_zero_iff]

/-- A write changes R iff it is applied at a defect. -/
theorem w_eq_self_iff (σ : Fin n → Fin n) (s : Fin n) (R : Fin n → Bool) :
    w σ s R = R ↔ R (σ s) = !R s := by
  constructor
  · intro h; have := congrFun h (σ s); simp only [w, if_pos rfl] at this
    cases h1 : R s <;> cases h2 : R (σ s) <;> simp_all
  · intro h; funext k; unfold w; split
    · next hk => subst hk; exact h.symm
    · rfl

/-- Zero defects iff every write leaves R unchanged (R is a fixed point). -/
theorem defects_zero_iff_fixed (σ : Fin n → Fin n) (R : Fin n → Bool) :
    defects σ R = 0 ↔ ∀ s, w σ s R = R := by
  rw [defects_zero_iff]; simp only [w_eq_self_iff]; rfl

/-- Local picture: a write at a defect s with σ s ≠ s removes the defect at s and
toggles the one at σ s (so it removes it, or moves the defect from s to σ s). -/
theorem write_local (σ : Fin n → Fin n) (hinj : ∀ a b, σ a = σ b → a = b)
    (s : Fin n) (R : Fin n → Bool) (hd : R s = R (σ s)) (hs : σ s ≠ s) :
    dI σ (w σ s R) s = 0 ∧ dI σ (w σ s R) (σ s) = 1 - dI σ R (σ s) := by
  have h2 : σ (σ s) ≠ σ s := fun h => hs (hinj _ _ h)
  have h3 : s ≠ σ s := fun h => hs h.symm
  refine ⟨?_, ?_⟩
  · simp only [dI, w, if_neg h3, if_pos rfl]; cases R s <;> simp
  · simp only [dI, w, if_neg h2, if_pos rfl]; rw [← hd]
    cases R s <;> cases R (σ (σ s)) <;> simp

/-- **1. Monotone.** A write never raises the defect count (σ injective). -/
theorem defects_mono (σ : Fin n → Fin n) (hinj : ∀ a b, σ a = σ b → a = b)
    (s : Fin n) (R : Fin n → Bool) : defects σ (w σ s R) ≤ defects σ R := by
  -- away from s and σ s nothing changes
  have hfar : ∀ k, k ≠ s → k ≠ σ s → dI σ (w σ s R) k = dI σ R k := by
    intro k hks hkt
    have : σ k ≠ σ s := fun h => hks (hinj _ _ h)
    simp only [dI, w, if_neg hkt, if_neg this]
  by_cases hd : R s = R (σ s)
  · by_cases hs : σ s = s
    · -- fixed point: dI is unchanged everywhere
      have := fsum_exchange (dI σ (w σ s R)) (dI σ R) s s 0 0 (fun k => by
        by_cases hk : k = s
        · subst hk; simp [dI, hs]
        · have hkt : k ≠ σ s := by rw [hs]; exact hk
          simp [hfar k hk hkt])
      exact Nat.le_of_add_le_add_right this
    · obtain ⟨h1, h2⟩ := write_local σ hinj s R hd hs
      have hds : dI σ R s = 1 := by simp [dI, hd]
      have := fsum_exchange (dI σ (w σ s R)) (dI σ R) s (σ s) 1 1 (fun k => by
        by_cases hk : k = s
        · subst hk; simp [h1, hds, Ne.symm hs]
        · by_cases hkt : k = σ s
          · subst hkt; simp [hk, h2]; omega
          · simp [hfar k hk hkt, hk, hkt])
      exact Nat.le_of_add_le_add_right this
  · -- not a defect: the write does nothing
    have : w σ s R = R := (w_eq_self_iff σ s R).2 (by cases h : R s <;> simp_all)
    rw [this]; exact Nat.le_refl _

/-- Reachability by writes. -/
inductive Reach (σ : Fin n → Fin n) : (Fin n → Bool) → (Fin n → Bool) → Prop
  | refl (R : Fin n → Bool) : Reach σ R R
  | step (s : Fin n) (R R' : Fin n → Bool) : Reach σ (w σ s R) R' → Reach σ R R'

/-- 1'. Defects never rise along any write sequence. -/
theorem defects_reach_mono (σ : Fin n → Fin n) (hinj : ∀ a b, σ a = σ b → a = b)
    {R R' : Fin n → Bool} (h : Reach σ R R') : defects σ R' ≤ defects σ R := by
  induction h with
  | refl => exact Nat.le_refl _
  | step s R R' _ ih => exact Nat.le_trans ih (defects_mono σ hinj s R)

/-- **2 (connection to Theorem C).** For a permutation, zero defects iff the point
subcube `{R}` is kept. -/
theorem defects_zero_iff_kept (σ τ : Fin n → Fin n) (hστ : ∀ a, σ (τ a) = a)
    (hτσ : ∀ a, τ (σ a) = a) (R : Fin n → Bool) :
    defects σ R = 0 ↔ Keeps σ (fun k => some (R k)) := by
  rw [defects_zero_iff, theoremC σ τ hστ hτσ]
  constructor
  · intro h; refine ⟨fun _ => rfl, ?_⟩
    intro a u v hu hv; simp at hu hv; subst hu; subst hv; exact h a
  · rintro ⟨_, h⟩ k; exact h k (R k) (R (σ k)) rfl rfl

/-- Mismatch count against a colouring c. -/
def mis (c R : Fin n → Bool) : Nat := fsum (fun k => if R k = c k then 0 else 1)

/-- **2, ⇐.** If some colouring alternates along σ (σ a permutation), every R reaches
a rule with zero defects. -/
theorem reach_zero_of_alt (σ τ : Fin n → Fin n) (_hστ : ∀ a, σ (τ a) = a)
    (hτσ : ∀ a, τ (σ a) = a) (c : Fin n → Bool) (hc : Alternating σ c) :
    ∀ R, ∃ R', Reach σ R R' ∧ defects σ R' = 0 := by
  have hinj : ∀ a b, σ a = σ b → a = b := fun a b h => by
    have := congrArg τ h; simpa [hτσ] using this
  suffices H : ∀ m R, mis c R ≤ m → ∃ R', Reach σ R R' ∧ defects σ R' = 0 from
    fun R => H _ R (Nat.le_refl _)
  intro m
  induction m with
  | zero => ?_
  | succ m ih => ?_
  all_goals
    intro R hm
    by_cases halt : Alternating σ R
    · exact ⟨R, Reach.refl R, (defects_zero_iff σ R).2 halt⟩
    -- find s agreeing with c whose successor disagrees
    have hex : ∃ s, R s = c s ∧ R (σ s) ≠ c (σ s) := by
      refine Classical.byContradiction fun hno => halt ?_
      have hfwd : ∀ a, R a = c a → R (σ a) = c (σ a) := fun a ha =>
        Classical.byContradiction fun hne => hno ⟨a, ha, hne⟩
      have hback := onto_of_into σ hinj (fun a => R a = c a) hfwd
      intro k
      by_cases hk : R k = c k
      · rw [hfwd k hk, hc k, hk]
      · have hk' : R (σ k) ≠ c (σ k) := fun h => by
          obtain ⟨b', hb, hσb⟩ := hback (σ k) h
          rw [hinj _ _ hσb] at hb; exact hk hb
        have e1 : R k = !c k := by cases h1 : R k <;> cases h2 : c k <;> simp_all
        have e2 : R (σ k) = !c (σ k) := by
          cases h1 : R (σ k) <;> cases h2 : c (σ k) <;> simp_all
        rw [e2, hc k, e1]
    obtain ⟨s, hs1, hs2⟩ := hex
    have hσs : σ s ≠ s := fun h => hs2 (by rw [h]; exact hs1)
    -- the write at s fixes the mismatch at σ s and touches nothing else
    have hdec : mis c (w σ s R) + 1 ≤ mis c R + 0 := by
      apply fsum_exchange _ _ (σ s) (σ s) 1 0
      intro k
      by_cases hk : k = σ s
      · subst hk
        simp only [w, if_pos rfl, hs1, ← hc s]
        simp [hs2]
      · simp [w, hk]
    first
    | omega
    | obtain ⟨R', hr, hz⟩ := ih (w σ s R) (by omega)
      exact ⟨R', Reach.step s R R' hr, hz⟩

/-- **2.** For a permutation σ: every R reaches zero defects iff some colouring
alternates along σ. -/
theorem reach_zero_iff (σ τ : Fin n → Fin n) (hστ : ∀ a, σ (τ a) = a)
    (hτσ : ∀ a, τ (σ a) = a) :
    (∀ R, ∃ R', Reach σ R R' ∧ defects σ R' = 0) ↔ ∃ c, Alternating σ c := by
  constructor
  · intro h
    obtain ⟨R', _, hz⟩ := h (fun _ => false)
    exact ⟨R', (defects_zero_iff σ R').1 hz⟩
  · rintro ⟨c, hc⟩; exact reach_zero_of_alt σ τ hστ hτσ c hc

/-- Iterate: `it σ m k = σ^m k`. -/
def it (σ : Fin n → Fin n) : Nat → Fin n → Fin n
  | 0, k => k
  | m + 1, k => σ (it σ m k)

/-- "Every cycle of σ is even", read as: every return time is even. (Return times of
k are the multiples of its cycle length, so this is the same as every cycle
length being even; that equivalence is not proved here.) -/
def EvenCycles (σ : Fin n → Fin n) : Prop := ∀ k m, it σ m k = k → m % 2 = 0

/-- Along an alternating R, `R (it σ j k)` flips with the parity of j. -/
theorem alt_iterate (σ : Fin n → Fin n) (R : Fin n → Bool) (k : Fin n) :
    ∀ m, (∀ j, j < m → R (σ (it σ j k)) = !R (it σ j k)) →
      R (it σ m k) = if m % 2 = 0 then R k else !R k := by
  intro m
  induction m with
  | zero => intro _; simp [it]
  | succ m ih =>
    intro h
    rw [it, h m (Nat.lt_succ_self m),
      ih (fun j hj => h j (Nat.lt_succ_of_lt hj))]
    rcases Nat.mod_two_eq_zero_or_one m with hm | hm
    · have : (m + 1) % 2 = 1 := by omega
      simp [hm, this]
    · have : (m + 1) % 2 = 0 := by omega
      simp [hm, this]

/-- **3.** On a cycle of odd return time, every R has a defect. So the defect count
never reaches zero, along any write sequence. -/
theorem odd_cycle_defect (σ : Fin n → Fin n) (R : Fin n → Bool) (k : Fin n) (m : Nat)
    (hret : it σ m k = k) (hodd : m % 2 = 1) :
    ∃ j, j < m ∧ dI σ R (it σ j k) = 1 := by
  refine Classical.byContradiction fun hno => ?_
  have hall : ∀ j, j < m → R (σ (it σ j k)) = !R (it σ j k) := by
    intro j hj
    have : dI σ R (it σ j k) ≠ 1 := fun h => hno ⟨j, hj, h⟩
    have h0 : dI σ R (it σ j k) = 0 := by unfold dI at *; split <;> simp_all
    exact (dI_zero_iff σ R _).1 h0
  have := alt_iterate σ R k m hall
  rw [hret, if_neg (by omega)] at this
  cases h : R k <;> simp [h] at this

/-- 2, ⇒ half of the parity reading: an alternating colouring forces every cycle
even. -/
theorem evenCycles_of_alt (σ : Fin n → Fin n) (c : Fin n → Bool) (hc : Alternating σ c) :
    EvenCycles σ := by
  intro k m hret
  refine Classical.byContradiction fun hne => ?_
  obtain ⟨j, _, hj⟩ := odd_cycle_defect σ c k m hret (by omega)
  have := (dI_zero_iff σ c (it σ j k)).2 (hc _)
  omega

/-- **3, consequences.** With an odd cycle, every R has at least one defect, and so
some write changes it: the rule never stops changing. -/
theorem odd_cycle_never_still (σ : Fin n → Fin n) (k : Fin n) (m : Nat)
    (hret : it σ m k = k) (hodd : m % 2 = 1) (R : Fin n → Bool) :
    1 ≤ defects σ R ∧ ∃ s, w σ s R ≠ R := by
  obtain ⟨j, _, hj⟩ := odd_cycle_defect σ R k m hret hodd
  refine ⟨hj ▸ le_fsum _ _, it σ j k, fun h => ?_⟩
  have := (w_eq_self_iff σ _ R).1 h
  have h0 := (dI_zero_iff σ R _).2 this
  omega

/-- **STATED, NOT PROVED.** The converse parity bridge: if every cycle is even, some
colouring alternates along σ. With `reach_zero_iff` and `evenCycles_of_alt` this
would give the bell's form "zero reachable from every R iff every cycle is even". -/
def EvenCyclesAlternate : Prop :=
  ∀ (n : Nat) (σ τ : Fin n → Fin n), (∀ a, σ (τ a) = a) → (∀ a, τ (σ a) = a) →
    EvenCycles σ → ∃ c, Alternating σ c

end Defects

/-! ## 5. Operators on the median object -/

/-- The write operator commutes with bitwise majority, on every rule. -/
theorem w_maj {α : Type} [DecidableEq α] (f : α → α) (s : α) (x y z : α → Bool) :
    w f s (fun n => (x n && y n) || (y n && z n) || (x n && z n)) =
      (fun n => (w f s x n && w f s y n) || (w f s y n && w f s z n) || (w f s x n && w f s z n)) := by
  funext n
  simp only [w]
  split <;> (cases x s <;> cases y s <;> cases z s <;> simp)

/-- On M, every operator that keeps M is a b-homomorphism. -/
theorem w_hom_on_M (f : Nbhd → Nbhd) (hk : Keeps f M) (s : Nbhd) {x y z : Rule}
    (hx : Mem M x) (hy : Mem M y) (hz : Mem M z) :
    w f s (b x y z) = b (w f s x) (w f s y) (w f s z) := by
  rw [b_eq_maj_on_M hx hy hz, b_eq_maj_on_M (hk s x hx) (hk s y hy) (hk s z hz)]
  exact w_maj f s x y z

/-- …but not off M: on the full cube (kept by every map), the operator that flips
bit 0 does not commute with b. So operators are NOT structure maps of the
blendoid in general (DESIGN §5 asks for this; it fails). -/
theorem w_not_hom_full :
    w id 0 (b (ofCode 0) (ofCode 0) (ofCode 1)) ≠
      b (w id 0 (ofCode 0)) (w id 0 (ofCode 0)) (w id 0 (ofCode 1)) := by
  intro h
  have := congrFun h 0
  revert this; decide

theorem full_kept (f : Nbhd → Nbhd) : Keeps f (fun _ => none) := by
  intro s R _ a v h; simp at h

/-! ## 6. Counts over S8 (computation; `native_decide`)

Permutations of `Fin 8` are enumerated as lists in lexicographic order. Checked by
`native_decide`: the enumeration has 40320 entries, each a permutation (length 8,
no repeats), strictly increasing in lex order (hence distinct). NOT proved in Lean:
that 40320 = |S8| (so that the enumeration is all of S8). With that one external
fact, the counts below count the permutations keeping each subcube, via
`theoremC`'s criterion. -/

/-- Lists of `k` distinct elements of `l`, in the order of `l` (fuel `k`). -/
def perms : Nat → List Nat → List (List Nat)
  | 0, _ => [[]]
  | k + 1, l => l.flatMap fun a => (perms k (l.erase a)).map (a :: ·)

/-- Lexicographic strict order on lists of naturals. -/
def lexLt : List Nat → List Nat → Bool
  | [], [] => false
  | [], _ :: _ => true
  | _ :: _, [] => false
  | a :: l, b :: m => a < b || (a == b && lexLt l m)

def sortedStrict : List (List Nat) → Bool
  | a :: b :: rest => lexLt a b && sortedStrict (b :: rest)
  | _ => true

def isPerm8 (σ : List Nat) : Bool :=
  σ.length == 8 && σ.all (· < 8) && (List.range 8).all fun i => σ.count i == 1

/-- The enumeration of S8 used below. -/
def S8 : List (List Nat) := perms 8 (List.range 8)

theorem S8_length : S8.length = 40320 := by native_decide
theorem S8_perms : S8.all isPerm8 = true := by native_decide
theorem S8_distinct : sortedStrict S8 = true := by native_decide

/-- A subcube on `Nat`-indexed coordinates 0..7, for computation. -/
abbrev SubcubeN := Nat → Option Bool

/-- `theoremC`'s criterion as a Boolean function of a listed permutation. -/
def critC (S : SubcubeN) (σ : List Nat) : Bool :=
  (List.range 8).all fun a =>
    let t := σ.getD a 0
    ((S a).isSome == (S t).isSome) &&
      (match S a, S t with
       | some u, some v => v == !u
       | _, _ => true)

def fact : Nat → Nat
  | 0 => 1
  | n + 1 => (n + 1) * fact n

def keeperCount (S : SubcubeN) : Nat := (S8.filter (critC S)).length

def MN : SubcubeN := fun n =>
  match n with | 1 => some false | 3 => some true | 4 => some false | 6 => some true | _ => none

/-- 2!² · 4! = 96 permutations keep M. -/
theorem keepers_M : keeperCount MN = 96 := by native_decide
/-- |D| = 2 balanced: 1!² · 6! = 720. -/
theorem keepers_k1 : keeperCount (fun n => match n with | 0 => some false | 1 => some true | _ => none) = 720 := by
  native_decide
/-- |D| = 6 balanced: 3!² · 2! = 72. -/
theorem keepers_k3 : keeperCount (fun n => if n < 3 then some false else if n < 6 then some true else none) = 72 := by
  native_decide
/-- |D| = 8 balanced: 4!² · 0! = 576. -/
theorem keepers_k4 : keeperCount (fun n => if n < 4 then some false else some true) = 576 := by
  native_decide
/-- |D| = 2, unbalanced: no keeper. -/
theorem keepers_unbalanced : keeperCount (fun n => match n with | 0 => some false | 1 => some false | _ => none) = 0 := by
  native_decide

/-- **Theorem C, counting part — STATED, NOT PROVED.** A subcube is kept by some
permutation iff v is balanced, and when |D| = 2k the number of keepers is
(k!)² · (8 − 2k)!. Only the instances above are checked (by computation). -/
def CountFormula : Prop :=
  ∀ S : SubcubeN, ∀ k : Nat,
    ((List.range 8).filter fun a => S a == some false).length = k →
    ((List.range 8).filter fun a => S a == some true).length = k →
    keeperCount S = (fact k) ^ 2 * fact (8 - 2 * k)

/-! ## 7. The blendoid structure -/

/-- A **blendoid** (DESIGN §5, as formalised here): a preorder of objects (the
structure maps are inclusions), a carrier per object with a ternary operation that
is idempotent and near-unanimous in its outer arguments, inclusion maps that are
functorial and commute with the operation, and a designated median object on which
the operation is a majority operation.

Departure from DESIGN §5: operator maps are NOT structure maps here, because they
do not commute with `b` in general (`w_not_hom_full`). They act on the median
object instead (`MedianAction`), where they do (`w_hom_on_M`). -/
structure Blendoid where
  Obj : Type
  le : Obj → Obj → Prop
  le_refl : ∀ X, le X X
  le_trans : ∀ {X Y Z}, le X Y → le Y Z → le X Z
  Carrier : Obj → Type
  op : (X : Obj) → Carrier X → Carrier X → Carrier X → Carrier X
  op_idem : ∀ X (x : Carrier X), op X x x x = x
  op_outer : ∀ X (x y : Carrier X), op X x y x = x
  map : ∀ {X Y}, le X Y → Carrier X → Carrier Y
  map_id : ∀ X (x : Carrier X), map (le_refl X) x = x
  map_comp : ∀ {X Y Z} (h₁ : le X Y) (h₂ : le Y Z) (x : Carrier X),
    map (le_trans h₁ h₂) x = map h₂ (map h₁ x)
  map_op : ∀ {X Y} (h : le X Y) (x y z : Carrier X),
    map h (op X x y z) = op Y (map h x) (map h y) (map h z)
  median : Obj
  median_left : ∀ x y : Carrier median, op median x x y = x
  median_right : ∀ x y : Carrier median, op median y x x = x

/-- Inclusion of subcubes: every rule of `S` is a rule of `T`. -/
def SubLe (S T : Subcube Nbhd) : Prop := ∀ a v, T a = some v → S a = some v

/-- **The subcube blendoid** of the 2015 blend: subcubes ↦ (rules in the subcube,
`b` restricted). Everything here is proved. -/
def subcubeBlendoid : Blendoid where
  Obj := Subcube Nbhd
  le := SubLe
  le_refl := fun _ _ _ h => h
  le_trans := fun h₁ h₂ a v h => h₁ a v (h₂ a v h)
  Carrier := fun S => {R : Rule // Mem S R}
  op := fun S x y z => ⟨b x.1 y.1 z.1, lemmaA S x.2 y.2 z.2⟩
  op_idem := fun _ x => Subtype.ext (b_idem x.1)
  op_outer := fun _ x y => Subtype.ext (b_outer x.1 y.1)
  map := fun h x => ⟨x.1, fun a v hT => x.2 a v (h a v hT)⟩
  map_id := fun _ _ => rfl
  map_comp := fun _ _ _ => rfl
  map_op := fun _ _ _ _ => rfl
  median := M
  median_left := fun x y => Subtype.ext (by
    show b x.1 x.1 y.1 = x.1
    rw [b_eq_maj_on_M x.2 x.2 y.2]; exact maj_left x.1 y.1)
  median_right := fun x y => Subtype.ext (by
    show b y.1 x.1 x.1 = x.1
    rw [b_eq_maj_on_M y.2 x.2 x.2]; exact maj_right x.1 y.1)

/-- Operators acting on the median object by homomorphisms of the operation. -/
structure MedianAction (B : Blendoid) where
  Act : Type
  act : Act → B.Carrier B.median → B.Carrier B.median
  act_op : ∀ g (x y z : B.Carrier B.median),
    act g (B.op B.median x y z) = B.op B.median (act g x) (act g y) (act g z)

/-- The write operators that keep M act on the median object of the subcube
blendoid by b-homomorphisms (proved). -/
def operatorAction : MedianAction subcubeBlendoid where
  Act := {p : (Nbhd → Nbhd) × Nbhd // Keeps p.1 M}
  act := fun g x => ⟨w g.1.1 g.1.2 x.1, g.2 g.1.2 x.1 x.2⟩
  act_op := fun g x y z => Subtype.ext (w_hom_on_M g.1.1 g.2 g.1.2 x.2 y.2 z.2)

/-- The operator action does NOT extend to every object: on the full cube (kept by
every map) some operator is not a b-homomorphism. -/
theorem operators_not_structure_maps :
    ¬ ∀ (f : Nbhd → Nbhd) (s : Nbhd) (x y z : Rule),
      Keeps f (fun _ => none) → w f s (b x y z) = b (w f s x) (w f s y) (w f s z) := by
  intro h
  exact w_not_hom_full (h id 0 _ _ _ (full_kept id))

/-! ## 8. "Fractal": restriction to a subcube is again a table blend

DESIGN §5 asks for a precise statement. The one proved here: call an operation a
**table blend** if outer agreement passes and, on conflict at coordinate `i`, the
output is read from the centre at a coordinate chosen by a table from
`(i, outer bit, centre bit)`, or is a constant. The 2015 blend is a table blend on
`Nbhd`. The restriction of any table blend to any subcube, transported to the free
coordinates, is a table blend there (and the transport is a bijection with the
subcube's rules). So the class is closed under restriction: self-similar. -/

/-- Arbitration table: `inl j` reads the centre at `j`; `inr v` is the constant `v`. -/
def Table (ι : Type) := ι → Bool → Bool → (ι ⊕ Bool)

def tb {ι : Type} (A : Table ι) (x y z : ι → Bool) : ι → Bool := fun i =>
  if x i = z i then x i else
    match A i (x i) (y i) with
    | .inl j => y j
    | .inr v => v

/-- The 2015 blend's table. -/
def A₀ : Table Nbhd := fun _ l c => .inl (idx l c (!l))

theorem b_is_table_blend : b = tb A₀ := by
  funext x y z n
  simp only [b, tb, A₀]
  split
  · rfl
  · rename_i h
    have : z n = !(x n) := by cases hx : x n <;> cases hz : z n <;> simp_all
    rw [this]

/-- Free coordinates of a subcube. -/
def Free {ι : Type} (S : Subcube ι) := {a : ι // S a = none}

/-- Extend an assignment of the free coordinates by the subcube's constants. -/
def ext {ι : Type} (S : Subcube ι) (x : Free S → Bool) : ι → Bool := fun a =>
  match h : S a with
  | some v => v
  | none => x ⟨a, h⟩

def restr {ι : Type} (S : Subcube ι) (R : ι → Bool) : Free S → Bool := fun i => R i.1

theorem ext_mem {ι : Type} (S : Subcube ι) (x : Free S → Bool) : Mem S (ext S x) := by
  intro a v h
  unfold ext; split
  · rename_i v' h'; rw [h] at h'; cases h'; rfl
  · rename_i h'; rw [h] at h'; cases h'

theorem ext_free {ι : Type} (S : Subcube ι) (x : Free S → Bool) (i : Free S) :
    ext S x i.1 = x i := by
  obtain ⟨a, ha⟩ := i
  unfold ext; split
  · rename_i v h'; rw [ha] at h'; cases h'
  · rfl

theorem restr_ext {ι : Type} (S : Subcube ι) (x : Free S → Bool) : restr S (ext S x) = x := by
  funext i; exact ext_free S x i

theorem ext_restr {ι : Type} (S : Subcube ι) (R : ι → Bool) (hR : Mem S R) :
    ext S (restr S R) = R := by
  funext a
  unfold ext; split
  · rename_i v h; exact (hR a v h).symm
  · rfl

/-- The restricted table on the free coordinates. -/
def restrictTable {ι : Type} (S : Subcube ι) (A : Table ι) : Table (Free S) := fun i l c =>
  match A i.1 l c with
  | .inr v => .inr v
  | .inl j =>
    match h : S j with
    | some v => .inr v
    | none => .inl ⟨j, h⟩

/-- **Fractal restriction (proved).** Restricting a table blend to a subcube and
transporting it to the free coordinates gives the table blend of the restricted
table. -/
theorem restrict_table_blend {ι : Type} (S : Subcube ι) (A : Table ι) (x y z : Free S → Bool) :
    restr S (tb A (ext S x) (ext S y) (ext S z)) = tb (restrictTable S A) x y z := by
  funext i
  simp only [restr, tb, restrictTable, ext_free]
  split
  · rfl
  · cases A i.1 (x i) (y i) with
    | inr v => rfl
    | inl j =>
      simp only
      unfold ext; split
      · rename_i v h; simp [h]
      · rename_i h; simp [h]

/-- Corollary: the 2015 blend restricted to any subcube is a table blend on the
free coordinates. -/
theorem b_restrict (S : Subcube Nbhd) (x y z : Free S → Bool) :
    restr S (b (ext S x) (ext S y) (ext S z)) = tb (restrictTable S A₀) x y z := by
  rw [b_is_table_blend]; exact restrict_table_blend S A₀ x y z

/-- **Stronger fractal reading — STATED, NOT PROVED.** The sub-blendoid of
subcubes below `S` is isomorphic, as a blendoid, to the blendoid of subcubes of
`Free S` under the restricted table blend. (The object-level bijection and the
operation-level transport are proved above; the functor-level isomorphism, and
whether the median object survives restriction, are not.) -/
def FractalBlendoid : Prop :=
  ∀ S : Subcube Nbhd, ∀ T : Subcube Nbhd, SubLe T S →
    ∃ T' : Subcube (Free S), ∀ x : Free S → Bool, Mem T (ext S x) ↔ Mem T' x

/-! ## 9. The "same game" as functoriality -/

/-- **Same-game closure — STATED.** A coarse-graining `κ` of `k` cells' rules into
one rule is a blendoid morphism on `S`: coarse-graining commutes with the blend. -/
def SameGame (k : Nat) (κ : (Fin k → Rule) → Rule) (S : Subcube Nbhd) : Prop :=
  ∀ L C R : Fin k → Rule, (∀ i, Mem S (L i)) → (∀ i, Mem S (C i)) → (∀ i, Mem S (R i)) →
    κ (fun i => b (L i) (C i) (R i)) = b (κ L) (κ C) (κ R)

/-- Sanity (proved): reading one cell is a same-game coarse-graining. -/
theorem sameGame_proj (k : Nat) (i₀ : Fin k) (S : Subcube Nbhd) :
    SameGame k (fun L => L i₀) S := fun _ _ _ _ _ _ => rfl

/-- Bitwise majority over three cells. -/
def maj3 (L : Fin 3 → Rule) : Rule := maj (L 0) (L 1) (L 2)

theorem mem_M_of {R : Rule} (h1 : R 1 = false) (h3 : R 3 = true) (h4 : R 4 = false)
    (h6 : R 6 = true) : Mem M R := by
  intro a v h
  match a with
  | ⟨0, _⟩ => simp [M] at h
  | ⟨1, _⟩ => simp [M] at h; subst h; exact h1
  | ⟨2, _⟩ => simp [M] at h
  | ⟨3, _⟩ => simp [M] at h; subst h; exact h3
  | ⟨4, _⟩ => simp [M] at h; subst h; exact h4
  | ⟨5, _⟩ => simp [M] at h
  | ⟨6, _⟩ => simp [M] at h; subst h; exact h6
  | ⟨7, _⟩ => simp [M] at h

/-- **Proved:** bitwise majority over three cells is NOT a same-game
coarse-graining on M (DESIGN §6 expected this). Counterexample: cells' (L, C, R)
= (204, 72, 204), (232, 105, 104), (201, 108, 204); the two sides differ at
neighbourhood 2. -/
theorem mem_M_code (k : Nat)
    (h : (ofCode k 1, ofCode k 3, ofCode k 4, ofCode k 6) = (false, true, false, true)) :
    Mem M (ofCode k) := by
  simp only [Prod.mk.injEq] at h
  exact mem_M_of h.1 h.2.1 h.2.2.1 h.2.2.2

def cellsL : Fin 3 → Rule := fun i => ofCode ([204, 232, 201].getD i.val 0)
def cellsC : Fin 3 → Rule := fun i => ofCode ([72, 105, 108].getD i.val 0)
def cellsR : Fin 3 → Rule := fun i => ofCode ([204, 104, 204].getD i.val 0)

theorem maj3_not_sameGame : ¬ SameGame 3 maj3 M := by
  intro h
  have hL : ∀ i, Mem M (cellsL i) := fun i => mem_M_code _ (by revert i; decide)
  have hC : ∀ i, Mem M (cellsC i) := fun i => mem_M_code _ (by revert i; decide)
  have hR : ∀ i, Mem M (cellsR i) := fun i => mem_M_code _ (by revert i; decide)
  have := congrFun (h cellsL cellsC cellsR hL hC hR) 2
  revert this; decide

/-! ## 10. Pinned walls (exotype results/s18-pinned-walls.txt, DESIGN-general §8.1)

Scores are rationals, lower is better. A cell with operator A in bin b scores
`hold = T A b`; adopting the neighbour's operator B scores `T B b − κ · X A B`.
A wall between an A-cell in bin b and a B-cell in bin b' is *pinned* iff on both
sides holding is no worse than adopting. -/

section Pinned
variable {Op Bin : Type}

/-- The wall is pinned: hold ≤ adopt on both sides. -/
def Pinned (T : Op → Bin → Rat) (X : Op → Op → Rat) (κ : Rat) (A B : Op) (b b' : Bin) : Prop :=
  T A b ≤ T B b - κ * X A B ∧ T B b' ≤ T A b' - κ * X B A

/-- **S18 (2), proved.** A pinned wall (X symmetric) has total score gap at least
2κX across the two bins. -/
theorem pinned_gap (T : Op → Bin → Rat) (X : Op → Op → Rat) (κ : Rat) (A B : Op)
    (b b' : Bin) (hsym : X B A = X A B) (h : Pinned T X κ A B b b') :
    2 * (κ * X A B) ≤ (T B b - T A b) + (T A b' - T B b') := by
  obtain ⟨h1, h2⟩ := h
  rw [hsym] at h2
  grind

/-- **S18 (1), proved.** In the same bin no wall is pinned, for any T, any κ > 0
and any A, B with X A B > 0 (X symmetric). -/
theorem not_pinned_same_bin (T : Op → Bin → Rat) (X : Op → Op → Rat) (κ : Rat) (A B : Op)
    (b : Bin) (hsym : X B A = X A B) (hκ : 0 < κ) (hX : 0 < X A B) :
    ¬ Pinned T X κ A B b b := by
  intro h
  have hg := pinned_gap T X κ A B b b hsym h
  have hp : 0 < κ * X A B := Rat.mul_pos hκ hX
  grind
end Pinned

/-! ## Ledger: what is proved, what is computed, what is only stated

PROVED (structural; axioms at most propext, Quot.sound, Classical.choice):
* §2 laws: `b_idem`, `b_outer` (near-unanimity in the outer arguments);
  `b_left_nu_fails` (b x x y = x fails), `b_mirror_fails` (mirror law fails),
  each by an explicit counterexample. The 34% / 30% frequencies are NOT formalised.
* `lemmaA`: every subcube is closed under b.
* `b_eq_maj_on_M`: on M, b is bitwise majority; `maj_left/mid/right`.
* `lemmaB`: for ANY coordinate type and ANY map f (not just End(8)), the
  family {w_{f,s}} keeps (D, v) iff D is preimage-closed under f and v
  alternates along f inside D.
* `theoremC` (for permutations of Fin n, any n): kept iff σ maps D onto D and
  swaps the 0-set and the 1-set of v. Uses a pigeonhole lemma
  (`onto_of_into`). `single_never_kept`: single-bit subcubes are never kept.
* Defects (§4, after Theorem C; found by ams-claude-1, exotype
  results/s9-boundary-moves.txt, checked there over 387 operators and 792,576
  writes). defects_σ(R) = #{k : R k = R (σ k)}.
  - `defects_mono` (σ injective): a write never raises the count;
    `defects_reach_mono`: nor does any write sequence. `write_local`: a write at
    a defect s with σ s ≠ s removes the defect at s and toggles the one at σ s.
  - `defects_zero_iff_fixed`: zero defects iff every write leaves R unchanged;
    `w_eq_self_iff`: a write changes R iff it is applied at a defect.
  - `defects_zero_iff_kept` (σ a permutation, via `theoremC`): zero defects iff
    the point subcube {R} is kept; so zero-defect rules are kept rules.
  - `reach_zero_iff` (σ a permutation): every R reaches zero defects by writes
    iff some colouring alternates along σ. The ⇐ direction is a descent on the
    mismatch count against the colouring, using the pigeonhole `onto_of_into`.
  - `evenCycles_of_alt`: an alternating colouring forces every return time to be
    even (`EvenCycles`).
  - `odd_cycle_defect`: if σ^m k = k with m odd, then EVERY R has a defect at
    some σ^j k, j < m. So each odd cycle holds a defect in every state, hence
    for ever. `odd_cycle_never_still`: with an odd cycle, defects ≥ 1 and some
    write changes R, in every state.
* `w_hom_on_M`: every operator that keeps M is a b-homomorphism on M.
* `w_not_hom_full` / `operators_not_structure_maps`: off M this fails, already
  on the full cube. So DESIGN §5's "operators are structure maps" is FALSE for
  the subcube blendoid; here operators act only on the median object.
* `subcubeBlendoid`: the subcube blendoid (inclusions as structure maps), with
  M as median object; `operatorAction`: the M-keeping operators act on it.
* Fractal (in the sense defined in §8): `b_is_table_blend`,
  `restrict_table_blend`, `b_restrict`, with the round trips `ext_restr`,
  `restr_ext`. Table blends are closed under restriction to subcubes.
* Same game: `sameGame_proj` (sanity); `maj3_not_sameGame` (bitwise majority
  over 3 cells is not a same-game coarse-graining on M).

* Pinned walls (§10, S18): `pinned_gap`: a pinned wall has
  (T(B,b) − T(A,b)) + (T(A,b') − T(B,b')) ≥ 2κX (X symmetric; any T, κ, X);
  `not_pinned_same_bin`: with b = b', κ > 0 and X(A,B) > 0 no wall is pinned.
  Over `Rat`, by `grind`. Not formalised: the S18 data themselves.

COMPUTED (`native_decide`, which trusts the compiler):
* `S8_length`, `S8_perms`, `S8_distinct`: the enumeration has 40320 distinct
  permutations. That 40320 = |S8| is NOT proved in Lean.
* `keepers_M` = 96, `keepers_k1` = 720, `keepers_k3` = 72, `keepers_k4` = 576,
  `keepers_unbalanced` = 0. These are instances of the count formula, computed
  with `critC`. `critC` is `theoremC`'s criterion written as a Bool function;
  its agreement with the Prop is NOT proved.

STATED ONLY (a `def … : Prop`, not proved):
* `CountFormula`: keepable iff balanced, with (k!)² · (8 − 2k)! keepers.
* `FractalBlendoid`: the blendoid-level isomorphism (only the object-level
  bijection and the operation transport are proved).
* `EvenCyclesAlternate`: every cycle even ⇒ some colouring alternates. With
  `reach_zero_iff` and `evenCycles_of_alt` this would give "zero reachable
  from every R iff every cycle is even" exactly as in the bell; as it stands
  the ⇐ direction is proved only from an alternating colouring. Also not
  proved: that `EvenCycles` (all return times even) equals "all cycle lengths
  even", and the count form "at least one defect PER odd cycle" summed over
  distinct odd cycles (proved: each odd cycle contains a defect in every state).
* `SameGame`: same-game closure, as a predicate; no κ other than a projection is
  shown to satisfy it. Classifying the κ on M is NOT done.

NOT ATTEMPTED:
* DESIGN §4 Corollary (3^(even cycles); 3774 families).
* DESIGN §7: b as a 3/2-colimit (ThreeHalvesBlend vocabulary). Not attempted.
* Decidability of `SameGame` on M as an instance.
-/

end Blendoid
