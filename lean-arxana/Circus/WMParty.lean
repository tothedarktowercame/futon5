import Circus.Party

/-!
# Party scores at the WM outer class comparison

Retro-specifies `futon2/src/futon2/aif/outer_select.clj:555–619`:
non-stopping policy-equivalence classes, forbidden-pick admission, then a
strict unique minimum, then a strict stopping comparison. In particular,
ties are refused BEFORE the stopping test. Alignment reference (not imported):
`DarkTower/WarMachine/Requirements.lean`, comparisonFloor, Q2 and Q11.

Inputs have one representative per equivalence class. Scores are per CLASS,
not per policy alias or selected task (entry-map §6); callers must preserve
this invariant when supplying rankings. The full class list and G remain
inputs even for forbidden classes: admission removes winning eligibility,
not the scored evidence. Stopping-equivalent classes have already been
partitioned out, as in the Clojure code. Empty input means allStopping.

D uses Party's integer scaling convention. Stopping has neutral habit in
Seam A. Seam B preserves the original refusal when no admissible class
beats stopping, otherwise compares D only over those which do.
-/
namespace Circus.WMParty
open Circus.Party
variable {C : Type}

inductive WMOutcome (C : Type) where
  | chosen (c : C)
  | tieAtMinimum
  | notBelowStopping
  | allStopping
  | noAdmissiblePick
  deriving DecidableEq, Repr

/-- All minimisers, in input order. Order does NOT resolve ties. -/
def minima (f : C → Int) (xs : List C) : List C :=
  xs.filter fun x => decide (∀ y ∈ xs, f x ≤ f y)

def uniqueMinimum (f : C → Int) (xs : List C) : WMOutcome C :=
  match minima f xs with
  | [] => .noAdmissiblePick
  | [x] => .chosen x
  | _ :: _ :: _ => .tieAtMinimum

def admissible (forbidden : C → Bool) (classes : List C) : List C :=
  classes.filter fun c => !forbidden c

def eligible (G : C → Int) (gStop : Int) (forbidden : C → Bool)
    (classes : List C) : List C :=
  (admissible forbidden classes).filter fun c => decide (G c < gStop)

/-- Matches the code's refusal precedence, including no-admissible-pick.
The argument `forbidden` is a predicate on class representatives' picks. -/
def wmChoose (G : C → Int) (gStop : Int) (forbidden : C → Bool)
    (classes : List C) : WMOutcome C :=
  if classes.isEmpty then .allStopping
  else
    match minima G (admissible forbidden classes) with
    | [] => .noAdmissiblePick
    | [x] => if G x < gStop then .chosen x else .notBelowStopping
    | _ :: _ :: _ => .tieAtMinimum

def decisionCost (s : Scores C) (w : Weights) (β : Int) (G : C → Int) : C → Int :=
  fun c => G c - β * lnE s w c

def seamA (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (gStop : Int) (forbidden : C → Bool) (classes : List C) : WMOutcome C :=
  wmChoose (decisionCost s w β G) gStop forbidden classes

def seamB (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (gStop : Int) (forbidden : C → Bool) (classes : List C) : WMOutcome C :=
  let E := eligible G gStop forbidden classes
  if E.isEmpty then wmChoose G gStop forbidden classes
  else uniqueMinimum (decisionCost s w β G) E

private theorem mem_minima (f : C → Int) (xs : List C) (x : C) :
    x ∈ minima f xs ↔ x ∈ xs ∧ ∀ y ∈ xs, f x ≤ f y := by
  simp [minima]

private theorem minima_nonempty (f : C → Int) (xs : List C) (h : xs ≠ []) :
    minima f xs ≠ [] := by
  cases xs with
  | nil => contradiction
  | cons x xs =>
    have hs := argmin_spec f ⟨x, xs⟩
    have hm := (mem_minima f (x :: xs) _).mpr hs
    intro hn
    rw [hn] at hm
    exact List.not_mem_nil hm

private theorem minima_congr (f g : C → Int) (xs : List C)
    (h : ∀ x ∈ xs, ∀ y ∈ xs, (f x ≤ f y ↔ g x ≤ g y)) :
    minima f xs = minima g xs := by
  apply List.filter_congr
  intro x hx
  congr 1
  apply propext
  constructor <;> intro hxy y hy
  · exact (h x hx y hy).mp (hxy y hy)
  · exact (h x hx y hy).mpr (hxy y hy)

private theorem minima_shift (f g : C → Int) (xs : List C) (k : Int)
    (h : ∀ x ∈ xs, f x = g x + k) : minima f xs = minima g xs := by
  apply minima_congr
  intro x hx y hy
  have h1 := h x hx
  have h2 := h y hy
  omega

/-- Restricting to classes below stopping preserves every raw-G minimiser,
provided at least one such class exists. -/
theorem minima_below (G : C → Int) (gStop : Int) (xs : List C)
    (existsBelow : ∃ x ∈ xs, G x < gStop) :
    minima G xs = minima G (xs.filter fun x => decide (G x < gStop)) := by
  unfold minima
  rw [List.filter_filter]
  apply List.filter_congr
  intro x hx
  apply Bool.eq_iff_iff.mpr
  simp only [Bool.and_eq_true, decide_eq_true_eq, List.mem_filter, decide_eq_true_eq]
  constructor
  · intro h
    obtain ⟨z, hz, hbelow⟩ := existsBelow
    have hbound := h z hz
    exact ⟨fun y hy => h y hy.1, by omega⟩
  · rintro ⟨h, hbelow⟩ y hy
    by_cases hb : G y < gStop
    · exact h y ⟨hy, hb⟩
    · omega

private theorem eligible_witness (G : C → Int) (gStop : Int)
    (forbidden : C → Bool) (classes : List C)
    (h : eligible G gStop forbidden classes ≠ []) :
    ∃ x ∈ admissible forbidden classes, G x < gStop := by
  obtain ⟨x, hx⟩ := List.exists_mem_of_ne_nil _ h
  exact ⟨x, by simpa [eligible] using hx⟩

/-- With any eligible class, the WM compares exactly the raw minima of E;
all of those minima already strictly beat stopping. -/
theorem wm_on_eligible (G : C → Int) (gStop : Int) (forbidden : C → Bool)
    (classes : List C) (h : eligible G gStop forbidden classes ≠ []) :
    wmChoose G gStop forbidden classes =
      uniqueMinimum G (eligible G gStop forbidden classes) := by
  have witness := eligible_witness G gStop forbidden classes h
  have hc : classes.isEmpty = false := by
    cases classes with
    | nil => simp [eligible, admissible] at h
    | cons _ _ => rfl
  have heq := minima_below G gStop (admissible forbidden classes) witness
  change minima G (admissible forbidden classes) =
    minima G (eligible G gStop forbidden classes) at heq
  simp only [wmChoose, hc, Bool.false_eq_true, ↓reduceIte, heq, uniqueMinimum]
  generalize hm : minima G (eligible G gStop forbidden classes) = ms
  cases ms with
  | nil => rfl
  | cons x xs =>
    cases xs with
    | nil =>
      have hx : x ∈ minima G (eligible G gStop forbidden classes) := by rw [hm]; simp
      have hb := (mem_minima _ _ _).mp hx
      have ht : G x < gStop := by simpa [eligible] using (List.mem_filter.mp hb.1).2
      simp [ht]
    | cons _ _ => rfl

/-- Zero weights preserve all original outcomes for Seam A. -/
theorem seamA_nesting (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (gStop : Int) (forbidden : C → Bool) (classes : List C)
    (zero : ∀ k, w k = 0) :
    seamA s w β G gStop forbidden classes = wmChoose G gStop forbidden classes := by
  have cost : decisionCost s w β G = G := by
    funext c; simp [decisionCost, lnE, zero]
  simp only [seamA, cost]

/-- Zero weights preserve all original outcomes for amended Seam B. -/
theorem seamB_nesting (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (gStop : Int) (forbidden : C → Bool) (classes : List C)
    (zero : ∀ k, w k = 0) :
    seamB s w β G gStop forbidden classes = wmChoose G gStop forbidden classes := by
  dsimp only [seamB]
  split
  · rfl
  · rename_i h
    have hn : eligible G gStop forbidden classes ≠ [] := by simpa using h
    rw [wm_on_eligible G gStop forbidden classes hn]
    have cost : decisionCost s w β G = G := by
      funext c; simp [decisionCost, lnE, zero]
    rw [cost]

/-- Constant lnE on admissible classes preserves every WM outcome in B.
No positivity hypothesis is needed for this cancellation. -/
theorem seamB_cancellation (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (gStop : Int) (forbidden : C → Bool) (classes : List C) (v : Int)
    (constant : ∀ c ∈ admissible forbidden classes, lnE s w c = v) :
    seamB s w β G gStop forbidden classes = wmChoose G gStop forbidden classes := by
  dsimp only [seamB]
  split
  · rfl
  · rename_i h
    have hn : eligible G gStop forbidden classes ≠ [] := by simpa using h
    rw [wm_on_eligible G gStop forbidden classes hn]
    unfold uniqueMinimum
    rw [minima_shift (decisionCost s w β G) G _ (-(β * v))]
    intro c hc
    have hc' := (List.mem_filter.mp hc).1
    simp [decisionCost, constant c hc', Int.sub_eq_add_neg]


/-- These are the three stopping/admission outcomes, excluding tie refusal. -/
def StoppingOutcome : WMOutcome C → Prop
  | .notBelowStopping | .allStopping | .noAdmissiblePick => True
  | _ => False

private theorem unique_not_stopping (f : C → Int) (xs : List C) (h : xs ≠ []) :
    ¬StoppingOutcome (uniqueMinimum f xs) := by
  have hn := minima_nonempty f xs h
  unfold uniqueMinimum
  split
  · contradiction
  · simp [StoppingOutcome]
  · simp [StoppingOutcome]

/-- B preserves stopping/admission outcomes exactly, including their reason.
It may still create or resolve a tie among eligible classes. -/
theorem seamB_stopping (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (gStop : Int) (forbidden : C → Bool) (classes : List C) :
    (StoppingOutcome (seamB s w β G gStop forbidden classes) ↔
      StoppingOutcome (wmChoose G gStop forbidden classes)) ∧
    (StoppingOutcome (wmChoose G gStop forbidden classes) →
      seamB s w β G gStop forbidden classes = wmChoose G gStop forbidden classes) := by
  dsimp only [seamB]
  split
  · exact ⟨Iff.rfl, fun _ => rfl⟩
  · rename_i h
    have hn : eligible G gStop forbidden classes ≠ [] := by simpa using h
    rw [wm_on_eligible G gStop forbidden classes hn]
    have h1 := unique_not_stopping (decisionCost s w β G) _ hn
    have h2 := unique_not_stopping G _ hn
    exact ⟨iff_of_false h1 h2, fun h => False.elim (h2 h)⟩

private theorem strict_minimum (f : C → Int) (xs : List C) (x : C)
    (distinct : xs.Nodup) (mem : x ∈ xs)
    (strict : ∀ y ∈ xs, y ≠ x → f x < f y) : minima f xs = [x] := by
  have hx : x ∈ minima f xs := by
    apply (mem_minima _ _ _).mpr
    refine ⟨mem, ?_⟩
    intro y hy
    by_cases he : y = x
    · subst y; omega
    · have h := strict y hy he; omega
  have hall : ∀ y ∈ minima f xs, y = x := by
    intro y hy
    have h := (mem_minima _ _ _).mp hy
    by_cases he : y = x
    · exact he
    · have h1 := strict y h.1 he
      have h2 := h.2 x mem
      omega
  have hn : (minima f xs).Nodup := distinct.filter _
  cases hm : minima f xs with
  | nil => simp [hm] at hx
  | cons z zs =>
    have hz : z = x := hall z (by simp [hm])
    subst z
    have hzs : zs = [] := by
      cases zs with
      | nil => rfl
      | cons y ys =>
        have hy := hall y (by simp [hm])
        have hxnot : x ∉ y :: ys := (List.nodup_cons.mp (hm ▸ hn)).1
        simp [hy] at hxnot
    simp [hzs]

/-- First-ranked among E is stated by first-occurrence indices, exactly as in
Party.FirstRanked; unranked classes have index equal to ranking length.
Distinctness is the one-representative-per-class input invariant. -/
theorem seamB_thor_transfer [BEq C] [LawfulBEq C]
    (ranked : Party.Character → List C) (w : Weights) (β : Int) (G : C → Int)
    (gStop : Int) (forbidden : C → Bool) (classes : List C) (x : C)
    (distinct : classes.Nodup)
    (admitted : x ∈ admissible forbidden classes) (below : G x < gStop)
    (first : x ∈ ranked .thor ∧ ∀ y ∈ eligible G gStop forbidden classes, y ≠ x →
      (ranked .thor).idxOf x < (ranked .thor).idxOf y)
    (flat : ∀ y ∈ eligible G gStop forbidden classes, G y = G x)
    (positive : 0 < w .thor) (betaPositive : 0 < β)
    (onlyThor : ∀ k, k ≠ .thor → w k = 0) :
    seamB (rankedScores ranked) w β G gStop forbidden classes = .chosen x := by
  have hx : x ∈ eligible G gStop forbidden classes := by
    simp [eligible, admitted, below]
  have hn : (eligible G gStop forbidden classes).isEmpty = false :=
    by simpa using (List.ne_nil_of_mem hx)
  dsimp only [seamB]
  rw [hn]
  simp only [Bool.false_eq_true, ↓reduceIte, uniqueMinimum]
  have hd : (eligible G gStop forbidden classes).Nodup := (distinct.filter _).filter _
  rw [strict_minimum _ _ x hd hx]
  intro y hy hne
  have hi := first.2 y hy hne
  have hs : rankScore (ranked .thor) y < rankScore (ranked .thor) x := by
    unfold rankScore; omega
  have hw := Int.mul_lt_mul_of_pos_left hs positive
  have hb := Int.mul_lt_mul_of_pos_left hw betaPositive
  simp only [decisionCost, lnE, rankedScores, onlyThor .merlin (by decide),
    onlyThor .thyra (by decide), onlyThor .questor (by decide),
    Int.zero_mul, Int.zero_add, flat y hy]
  omega

/-- A D-tie in nonempty E witnesses two distinct equal-cost minimisers.
It is never resolved by list order. -/
theorem seamB_tie_witness (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (gStop : Int) (forbidden : C → Bool) (classes : List C)
    (distinct : classes.Nodup) (nonempty : eligible G gStop forbidden classes ≠ [])
    (tie : seamB s w β G gStop forbidden classes = .tieAtMinimum) :
    ∃ x ∈ eligible G gStop forbidden classes,
      ∃ y ∈ eligible G gStop forbidden classes, x ≠ y ∧
        decisionCost s w β G x = decisionCost s w β G y ∧
        ∀ z ∈ eligible G gStop forbidden classes,
          decisionCost s w β G x ≤ decisionCost s w β G z := by
  have hn : (eligible G gStop forbidden classes).isEmpty = false := by simpa using nonempty
  simp only [seamB, hn, Bool.false_eq_true, ↓reduceIte, uniqueMinimum] at tie
  split at tie
  · contradiction
  · contradiction
  · rename_i x y ys hm
    have hx := (mem_minima (decisionCost s w β G) _ x).mp (by rw [hm]; simp)
    have hy := (mem_minima (decisionCost s w β G) _ y).mp (by rw [hm]; simp)
    have hd : (minima (decisionCost s w β G) (eligible G gStop forbidden classes)).Nodup :=
      ((distinct.filter _).filter _).filter _
    rw [hm] at hd
    have hne : x ≠ y := by
      intro h; subst y; simpa using (List.nodup_cons.mp hd).1
    refine ⟨x, hx.1, y, hy.1, hne, ?_, hx.2⟩
    have h1 := hx.2 y hy.1
    have h2 := hy.2 x hx.1
    omega


private theorem wm_chosen_spec (G : C → Int) (gStop : Int)
    (forbidden : C → Bool) (classes : List C) (x : C)
    (chosen : wmChoose G gStop forbidden classes = .chosen x) :
    x ∈ admissible forbidden classes ∧ G x < gStop := by
  unfold wmChoose at chosen
  split at chosen
  · contradiction
  · split at chosen
    · contradiction
    · rename_i y hm
      split at chosen
      · rename_i hb
        have he : y = x := WMOutcome.chosen.inj chosen
        subst y
        exact ⟨((mem_minima _ _ _).mp (by rw [hm]; simp)).1, hb⟩
      · contradiction
    · contradiction

/-- Nonnegative integer weights and scores make the habit nonnegative. -/
theorem lnE_nonnegative (s : Scores C) (w : Weights) (c : C)
    (weights : ∀ k, 0 ≤ w k) (scores : ∀ k, 0 ≤ s k c) : 0 ≤ lnE s w c := by
  have h1 := Int.mul_nonneg (weights .merlin) (scores .merlin)
  have h2 := Int.mul_nonneg (weights .thyra) (scores .thyra)
  have h3 := Int.mul_nonneg (weights .questor) (scores .questor)
  have h4 := Int.mul_nonneg (weights .thor) (scores .thor)
  unfold lnE
  omega

/-- In particular rank-derived scores are nonnegative, including absences. -/
theorem rankScore_nonnegative [BEq C] [LawfulBEq C] (ranked : List C) (c : C) :
    0 ≤ rankScore ranked c := by
  have h := List.idxOf_le_length (l := ranked) (a := c)
  unfold rankScore
  omega

/-- A nonnegative habit lowers or preserves every candidate decision cost. -/
theorem decisionCost_le (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (c : C) (beta : 0 ≤ β) (weights : ∀ k, 0 ≤ w k)
    (scores : ∀ k, 0 ≤ s k c) : decisionCost s w β G c ≤ G c := by
  have h := Int.mul_nonneg beta (lnE_nonnegative s w c weights scores)
  unfold decisionCost
  omega

/-- Seam A's acting bias: if raw G chooses, nonnegative party scores cannot
produce a stopping/admission outcome. A newly created D-tie remains possible.
This is stronger than merely ruling out notBelowStopping, not a no-ties claim. -/
theorem seamA_nonnegative_bias (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (gStop : Int) (forbidden : C → Bool) (classes : List C) (x : C)
    (chosen : wmChoose G gStop forbidden classes = .chosen x)
    (beta : 0 ≤ β) (weights : ∀ k, 0 ≤ w k)
    (scores : ∀ c ∈ admissible forbidden classes, ∀ k, 0 ≤ s k c) :
    ¬StoppingOutcome (seamA s w β G gStop forbidden classes) := by
  have hx := wm_chosen_spec G gStop forbidden classes x chosen
  have hle := decisionCost_le s w β G x beta weights (scores x hx.1)
  have hm : x ∈ eligible (decisionCost s w β G) gStop forbidden classes := by
    simp only [eligible, List.mem_filter, decide_eq_true_eq]
    exact ⟨hx.1, by omega⟩
  have hn := List.ne_nil_of_mem hm
  unfold seamA
  rw [wm_on_eligible _ _ _ _ hn]
  exact unique_not_stopping _ _ hn

/-! ## Counterexamples: Seam A does not preserve the stopping boundary

All fixtures below are invented two-class integer examples. No experiment
or run data is encoded. Scores and weights are nonnegative in the positive
habit examples; beta is strictly positive.
-/
namespace Examples
private def never (_ : Bool) : Bool := false
private def thorOnly (k : Party.Character) : Int := if k = .thor then 1 else 0
private def scores (k : Party.Character) (c : Bool) : Int :=
  if k = .thor then (if c then 1 else 0) else 0
private def constantScores (k : Party.Character) (_ : Bool) : Int :=
  if k = .thor then 2 else 0
private def ranked (k : Party.Character) : List Bool :=
  if k = .thor then [false, true] else []

-- A: constant positive lnE does not cancel against a neutral stopping score.
-- This refutes both stopping-outcome preservation and constant cancellation.
example : (∀ c : Bool, lnE constantScores thorOnly c = 2) ∧
    wmChoose (fun _ : Bool => 1) 0 never [false] = .notBelowStopping ∧
    seamA constantScores thorOnly 1 (fun _ : Bool => 1) 0 never [false] = .chosen false := by
  decide

-- A: the first-ranked among E need not win; a raw-ineligible class can enter
-- after tilting. E=[true], G is flat there, and true is ranked/admissible.
example : eligible (fun c : Bool => if c then 0 else 2) 1 never [false, true] = [true] ∧
    (true ∈ ranked .thor ∧ ∀ y ∈ eligible (fun c : Bool => if c then 0 else 2)
      1 never [false, true], y ≠ true →
        (ranked .thor).idxOf true < (ranked .thor).idxOf y) ∧
    seamA (rankedScores ranked) thorOnly 3 (fun c : Bool => if c then 0 else 2)
      1 never [false, true] = .chosen false ∧
    seamB (rankedScores ranked) thorOnly 3 (fun c : Bool => if c then 0 else 2)
      1 never [false, true] = .chosen true := by decide

-- Both seams can resolve a raw-G tie at a class boundary.
example : wmChoose (fun _ : Bool => 0) 10 never [false, true] = .tieAtMinimum ∧
    seamA scores thorOnly 1 (fun _ : Bool => 0) 10 never [false, true] = .chosen true ∧
    seamB scores thorOnly 1 (fun _ : Bool => 0) 10 never [false, true] = .chosen true := by decide

/-! ## Counterexamples refuting the FIRST specification of Seam B

The first packet filtered before retaining any original outcome. That lost
above-stopping tie refusals, even at zero weights. The revised definition
explicitly falls back to wmChoose on empty E. The original "never changes
whether WM acts" slogan also ignored party-created ties.
-/
private def originalB (s : Scores C) (w : Weights) (β : Int) (G : C → Int)
    (gStop : Int) (forbidden : C → Bool) (classes : List C) : WMOutcome C :=
  if classes.isEmpty then .allStopping
  else if (admissible forbidden classes).isEmpty then .noAdmissiblePick
  else if (eligible G gStop forbidden classes).isEmpty then .notBelowStopping
  else uniqueMinimum (decisionCost s w β G) (eligible G gStop forbidden classes)

-- Refutes original nesting, cancellation and stopping-outcome equivalence.
example : (∀ c : Bool, lnE scores (fun _ => 0) c = 0) ∧
    wmChoose (fun _ : Bool => 1) 0 never [false, true] = .tieAtMinimum ∧
    originalB scores (fun _ => 0) 1 (fun _ : Bool => 1) 0 never [false, true] =
      .notBelowStopping ∧
    seamB scores (fun _ => 0) 1 (fun _ : Bool => 1) 0 never [false, true] =
      .tieAtMinimum := by decide

-- Party creates a D-tie from a unique raw minimum, with E unchanged.
example : wmChoose (fun c : Bool => if c then 1 else 0) 10 never [false, true] =
      .chosen false ∧
    seamB scores thorOnly 1 (fun c : Bool => if c then 1 else 0) 10 never [false, true] =
      .tieAtMinimum := by decide

-- The fifth outcome is required: forbidden is not stopping-equivalent.
example : wmChoose (fun _ : Bool => 0) 10 (fun _ => true) [false] =
    .noAdmissiblePick := by decide
end Examples
end Circus.WMParty
