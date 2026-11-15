import Circus.Characters
import Init.Data.Rat

/-!
The proposed §3 habit channel of `TN-gauntlet-party-aif.md`, not an assertion
that the proposed experiment has run. All energies use a common integer unit.
β is an integer multiplier; rational β = b/d is represented by scaling G by
d and using b here (d > 0). No division or rounding enters choice. Ties retain
the first occurrence in menu order, including when character rankings disagree.
-/
namespace Circus.Party

inductive Character | merlin | thyra | questor | thor
  deriving DecidableEq, Repr

structure Menu (Cand : Type) where
  head : Cand
  tail : List Cand

def Menu.items (m : Menu C) : List C := m.head :: m.tail

abbrev Scores (C : Type) := Character → C → Int
abbrev Weights := Character → Int

def lnE (s : Scores C) (w : Weights) (c : C) : Int :=
  w .merlin * s .merlin c + w .thyra * s .thyra c +
  w .questor * s .questor c + w .thor * s .thor c

/-- Scan left to right; replace only on strict improvement. -/
def scan (f : C → Int) (x : C) : List C → C
  | [] => x
  | y :: ys => scan f (if f y < f x then y else x) ys

def argmin (f : C → Int) (m : Menu C) : C := scan f m.head m.tail

def choice (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (m : Menu C) : C := argmin (fun c => G c - β * lnE s w c) m

private theorem scan_mem (f : C → Int) (x : C) (xs : List C) :
    scan f x xs ∈ x :: xs := by
  induction xs generalizing x with
  | nil => simp [scan]
  | cons y ys ih =>
    simp only [scan]
    split
    · exact List.mem_cons_of_mem x (ih y)
    · have h := ih x
      simp only [List.mem_cons] at h ⊢
      rcases h with h | h
      · exact Or.inl h
      · exact Or.inr (Or.inr h)

private theorem scan_congr (f g : C → Int) (x : C) (xs : List C)
    (h : ∀ a ∈ x :: xs, ∀ b ∈ x :: xs, (f a < f b ↔ g a < g b)) :
    scan f x xs = scan g x xs := by
  induction xs generalizing x with
  | nil => rfl
  | cons y ys ih =>
    have hxy := h y (by simp) x (by simp)
    simp only [scan, hxy]
    apply ih
    intro a ha b hb
    apply h a _ b _ <;> split at * <;> simp_all <;> grind

/-- Any constant shift of all menu energies preserves even the tie decision. -/
theorem argmin_shift (f g : C → Int) (m : Menu C) (k : Int)
    (h : ∀ c ∈ m.items, f c = g c + k) : argmin f m = argmin g m := by
  apply scan_congr
  intro a ha b hb
  have ha' := h a ha
  have hb' := h b hb
  omega

/-- P-005 is nested exactly, with the same tie-breaking rule. -/
theorem nesting (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (m : Menu C) (hw : ∀ k, w k = 0) : choice s w β G m = argmin G m := by
  simp [choice, lnE, hw]

/-- A constant habit score on the menu cannot alter choice. -/
theorem constant_habit (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (m : Menu C) (v : Int) (h : ∀ c ∈ m.items, lnE s w c = v) :
    choice s w β G m = argmin G m := by
  apply argmin_shift _ _ m (-(β * v))
  intro c hc
  rw [h c hc]
  omega

/-- Cancellation when all four scores factor through one class map. -/
theorem cancellation (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (m : Menu C) (cls : C → K) (q : Character → K → Int)
    (factor : ∀ k c, s k c = q k (cls c))
    (same : ∀ c ∈ m.items, cls c = cls m.head) :
    choice s w β G m = argmin G m := by
  apply constant_habit s w β G m (lnE s w m.head)
  intro c hc
  simp [lnE, factor, same c hc]

/-- An override requires a class boundary, not merely two distinct candidates. -/
theorem boundary_decisions (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (m : Menu C) (cls : C → K) (q : Character → K → Int)
    (factor : ∀ k c, s k c = q k (cls c))
    (changed : choice s w β G m ≠ argmin G m) :
    ∃ c ∈ m.items, cls c ≠ cls m.head := by
  classical
  apply Classical.byContradiction
  intro h
  apply changed (cancellation s w β G m cls q factor ?_)
  intro c hc
  apply Classical.byContradiction
  intro hn
  exact h ⟨c, hc, hn⟩

/-- Flat C is modelled by a constant Thyra score, not by deleting Thyra. -/
theorem thyra_silent (s : Scores C) (w w' : Weights) (β : Int) (G : C → Int)
    (m : Menu C) (v : Int) (flat : ∀ c ∈ m.items, s .thyra c = v)
    (other : ∀ k, k ≠ .thyra → w k = w' k) :
    choice s w β G m = choice s w' β G m := by
  apply argmin_shift _ _ m (β * (w' .thyra * v - w .thyra * v))
  intro c hc
  simp only [lnE, flat c hc, other .merlin (by decide),
    other .questor (by decide), other .thor (by decide), Int.mul_add, Int.mul_sub]
  omega

def overrides [DecidableEq C] (s : Scores C) (w : Weights) (β : Int)
    (G : C → Int) (m : Menu C) : Bool := choice s w β G m != argmin G m

/-- Exact rational proportion, with the empty sample assigned rate zero.
Filter to boundary menus before calling this for the note's boundary statistic. -/
def overrideRate [DecidableEq C] (s : Scores C) (w : Weights) (β : Int)
    (G : C → Int) (menus : List (Menu C)) : Rat :=
  (menus.filter (overrides s w β G)).length / (menus.length : Rat)

theorem overrideRate_zero [DecidableEq C] (s : Scores C) (w : Weights)
    (β : Int) (G : C → Int) (menus : List (Menu C))
    (h : ∀ m ∈ menus, choice s w β G m = argmin G m) :
    overrideRate s w β G menus = 0 := by
  have hf : menus.filter (overrides s w β G) = [] := by
    apply List.filter_eq_nil_iff.mpr
    intro m hm
    simp [overrides, h m hm]
  simp [overrideRate, hf, Rat.div_def, Rat.zero_mul]

theorem nesting_rate_zero [DecidableEq C] (s : Scores C) (w : Weights)
    (β : Int) (G : C → Int) (menus : List (Menu C)) (hw : ∀ k, w k = 0) :
    overrideRate s w β G menus = 0 :=
  overrideRate_zero s w β G menus (fun m _ => nesting s w β G m hw)

theorem cancellation_rate_zero [DecidableEq C] (s : Scores C) (w : Weights)
    (β : Int) (G : C → Int) (menus : List (Menu C)) (cls : C → K)
    (q : Character → K → Int) (factor : ∀ k c, s k c = q k (cls c))
    (same : ∀ m ∈ menus, ∀ c ∈ m.items, cls c = cls m.head) :
    overrideRate s w β G menus = 0 :=
  overrideRate_zero s w β G menus
    (fun m hm => cancellation s w β G m cls q factor (same m hm))

/-- First occurrence rank: length - zero-based index, hence n,...,1;
unranked candidates have index n and score 0. Duplicate ranks use the first. -/
def rankScore [BEq C] (ranked : List C) (c : C) : Int :=
  (ranked.length : Int) - (ranked.idxOf c : Int)

/-- Adapter of the four actual step-5 orderings (including their eligibility gates).
The inputs retain step 5's documented pre-sorting obligations. -/
def characterRankings (pf : Circus.Characters.PassFacts)
    (ps : List Circus.Characters.Pending) (rows : List Circus.Characters.MerlinRow)
    (best : Circus.Characters.Pending → Option (Circus.Characters.FeatureId × Nat))
    (top : Nat) : Character → List Arxana.EntityId
  | .merlin => (Circus.Characters.merlinOrder rows top).map Circus.Entry.item
  | .thyra => (Circus.Characters.thyraOrder pf ps top).map Circus.Entry.item
  | .questor => (Circus.Characters.questorOrder ps best top).map Circus.Entry.item
  | .thor => (Circus.Characters.thorOrder pf top).map Circus.Entry.item

def rankedScores [BEq C] (ranked : Character → List C) : Scores C :=
  fun k => rankScore (ranked k)

/-- The first-ranked menu item: it occurs in the ranking, occurs in the menu,
and has an earlier first occurrence than every other menu item (unranked
items have index equal to ranking length). Independent of menu tie order. -/
def FirstRanked [BEq C] (ranked : List C) (m : Menu C) (x : C) : Prop :=
  x ∈ ranked ∧ x ∈ m.items ∧
  ∀ y ∈ m.items, y ≠ x → ranked.idxOf x < ranked.idxOf y

private theorem scan_min (f : C → Int) (x : C) (xs : List C) :
    ∀ y ∈ x :: xs, f (scan f x xs) ≤ f y := by
  induction xs generalizing x with
  | nil => simp [scan]
  | cons z zs ih =>
    intro y hy
    simp only [scan]
    split
    · rename_i hz
      have h := ih z z (by simp)
      rcases List.mem_cons.mp hy with rfl | hy
      · omega
      · exact ih z y hy
    · rename_i hz
      have h := ih x x (by simp)
      rcases List.mem_cons.mp hy with rfl | hy
      · exact h
      · rcases List.mem_cons.mp hy with rfl | hy
        · omega
        · exact ih x y (by simp [hy])

/-- The algorithm returns a member and minimises its declared objective. -/
theorem argmin_spec (f : C → Int) (m : Menu C) :
    argmin f m ∈ m.items ∧ ∀ y ∈ m.items, f (argmin f m) ≤ f y :=
  ⟨scan_mem f m.head m.tail, scan_min f m.head m.tail⟩

/-- Positive β and Thor weight transfer Thor's first-ranked menu item into
choice when G is flat. Zero/negative β does not support this conclusion. -/
theorem thor_transfer [BEq C] [LawfulBEq C] (ranked : Character → List C)
    (w : Weights) (β : Int) (G : C → Int) (m : Menu C) (x : C)
    (first : FirstRanked (ranked .thor) m x)
    (positive : 0 < w .thor) (betaPositive : 0 < β)
    (onlyThor : ∀ k, k ≠ .thor → w k = 0)
    (flat : ∀ y ∈ m.items, G y = G x) :
    choice (rankedScores ranked) w β G m = x := by
  let f := fun c => G c - β * lnE (rankedScores ranked) w c
  have hm := scan_mem f m.head m.tail
  have hmin := scan_min f m.head m.tail x first.2.1
  change choice (rankedScores ranked) w β G m ∈ m.items at hm
  change f (choice (rankedScores ranked) w β G m) ≤ f x at hmin
  apply Classical.byContradiction
  intro hn
  have hi := first.2.2 _ hm hn
  have hs : rankScore (ranked .thor) (choice (rankedScores ranked) w β G m) <
      rankScore (ranked .thor) x := by unfold rankScore; omega
  have hw := Int.mul_lt_mul_of_pos_left hs positive
  have hb := Int.mul_lt_mul_of_pos_left hw betaPositive
  simp only [f, lnE, rankedScores, onlyThor .merlin (by decide),
    onlyThor .thyra (by decide), onlyThor .questor (by decide),
    Int.zero_mul, Int.zero_add] at hmin
  rw [flat _ hm] at hmin
  omega

/-- The zero score for an unranked candidate is an absence, not a last-place
positive rank. -/
theorem rankScore_unranked [BEq C] [LawfulBEq C] (ranked : List C) (x : C)
    (h : x ∉ ranked) : rankScore ranked x = 0 := by
  simp [rankScore, List.idxOf_eq_length h]

/-- Concrete step-5 transfer: on Thor's nonempty ranked menu, choice is its
head and unblocks at least as many items as any later candidate. The other
three rankings are still produced by their actual step-5 functions. -/
theorem thor_choice_unblocks (pf : Circus.Characters.PassFacts)
    (ps : List Circus.Characters.Pending) (rows : List Circus.Characters.MerlinRow)
    (best : Circus.Characters.Pending → Option (Circus.Characters.FeatureId × Nat))
    (top : Nat) (x : Arxana.EntityId) (xs : List Arxana.EntityId)
    (head : (Circus.Characters.thorOrder pf top).map Circus.Entry.item = x :: xs)
    (sorted : pf.order.Pairwise (Circus.Characters.passLE pf))
    (w : Weights) (β : Int) (G : Arxana.EntityId → Int)
    (positive : 0 < w .thor) (betaPositive : 0 < β)
    (onlyThor : ∀ k, k ≠ .thor → w k = 0)
    (flat : ∀ y ∈ x :: xs, G y = G x) :
    let chosen := choice (rankedScores (characterRankings pf ps rows best top))
      w β G ⟨x, xs⟩
    chosen = x ∧ ∀ y ∈ xs, pf.unblocks y ≤ pf.unblocks chosen := by
  have first : FirstRanked (characterRankings pf ps rows best top .thor) ⟨x, xs⟩ x := by
    change FirstRanked ((Circus.Characters.thorOrder pf top).map Circus.Entry.item) _ _
    rw [head]
    refine ⟨by simp, by simp [Menu.items], ?_⟩
    intro y _ hy
    have hb : (x == y) = false := by
      apply Bool.eq_false_iff.mpr
      intro he
      exact hy (beq_iff_eq.mp he).symm
    simp [List.idxOf_cons, hb]
  have chosen := thor_transfer (characterRankings pf ps rows best top) w β G
    ⟨x, xs⟩ x first positive betaPositive onlyThor flat
  refine ⟨chosen, ?_⟩
  rw [chosen]
  have ordered := Circus.Characters.thor_monotone_unblocks pf top sorted
  have mapped : ((Circus.Characters.thorOrder pf top).map Circus.Entry.item).Pairwise
      (Circus.Characters.passLE pf) := by
    simpa only [List.pairwise_map] using ordered
  rw [head] at mapped
  intro y hy
  have h := (List.pairwise_cons.mp mapped).1 y hy
  rcases h with h | ⟨h, _⟩ <;> omega

/-- Actual frequency-only null: negative fixed-point log frequency. The caller
supplies its declared integer log quantizer and observed class counts; no
Dirichlet information is consulted. Weighting belongs to lnE, not this score. -/
def frequencyNull (logFrequency : Nat → Int) (frequency : K → Nat)
    (cls : C → K) (c : C) : Int := -logFrequency (frequency (cls c))

/-- Empirical obligation, NOT established: scaled Merlin EIG approaches
(|O|-1)/(2n) within a declared tolerance once counts exceed a threshold.
Cross multiplication avoids rounding the rational rarity prediction. -/
def MerlinRarityClaim (eig : Nat → Int) (outcomes scale threshold : Nat)
    (tolerance : Int) : Prop :=
  0 < scale ∧ 0 ≤ tolerance ∧ ∀ n, threshold ≤ n → 0 < n →
    Int.natAbs (2 * (n : Int) * eig n - (scale : Int) * ((outcomes : Int) - 1)) ≤
      (2 * (n : Int) * tolerance).toNat

/-- Empirical obligation: a paired run's integer-scaled outcome statistics
match Merlin and the frequency-only null to a declared tolerance. These
statistics must come from runs using frequencyNull, not inferred from rarity. -/
def MerlinFrequencyNullClaim (run : (C → Int) → Nat → Int)
    (merlin : C → Int) (logFrequency : Nat → Int) (frequency : K → Nat)
    (cls : C → K) (tolerance : Nat) : Prop :=
  ∀ seed, Int.natAbs (run merlin seed -
    run (frequencyNull logFrequency frequency cls) seed) ≤ tolerance

/-- Empirical obligation: Thor approximates independently measured depth-k
value, on common integer units and an explicitly supplied tolerance. -/
def ThorDepthClaim (thor : C → Int) (depthValue : Nat → C → Int)
    (k tolerance : Nat) : Prop :=
  ∀ c, Int.natAbs (thor c - depthValue k c) ≤ tolerance

/-- Empirical/dynamics obligation: absent a mutation source, a class with
zero population stays extinct. Channel scores alone do not prove a dynamics
property; population and mutation must be supplied by the experiment. -/
def AbsorptionClaim (population : Nat → K → Nat) (mutation : Nat → K → Bool) : Prop :=
  ∀ t k, population t k = 0 → mutation t k = false → population (t + 1) k = 0

end Circus.Party
