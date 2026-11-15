import Circus.Loop

/-!
# The four Gauntlet characters, retro-specified (M-repl-circus, step 5)

`futon3c scripts/giraffe_party.py` defines four orderings. Here each is
retro-specified as a Lean function over the DATA the script reads — modelled
as explicit structures (`PassFacts`, `Pending`, `Lexicon`, `MerlinRow`), not
as opaque functions of `Inputs`. (FINDING: `Inputs` in `Circus/Basic.lean`
does not itself carry pass rank, unblocks, cone, convergence, harm line or
lexicon fields; per `wiring` these arrive as `activation`/`ordering` records
handed to each character. The structures below are those records' content.)

* `thor_order` — Giraffe's own pass order (unblocks, then cone), first `top`.
* `merlin_order` — convergence of the feature the item IS plus half the
  convergence of the strongest unfinished feature it names; "may be no longer
  wanted" items dropped; an item with no lexicon feature is a typed absence.
* `questor_order` — "ready (one small handoff)" items only (a cost gate),
  strongest named feature first.
* `thyra_order` — items on a harm line H1..H7, most ready first (Giraffe pass
  rank; items outside the pass last, by pending level).

Reasons are modelled as TERMS (`Term`) with a `render` that reproduces the
display fragments. Source faithfulness is independent of vocabulary membership.

## Benefit monotonicity (Joe, 2026-11-14)

"harm reduction is a benefit, so party order does imply at least some benefit
thanks to Thyra. Arguably strategy can imply a specific benefit of improved
understanding of how the system works."

* Thyra: never ranks an item on no harm line at all (`thyra_only_harm_items`),
  and among harm items follows readiness — pass rank first, then pending
  level (`thyra_monotone`).
* Merlin: never ranks a lower SCORE above a higher (`merlin_monotone_score`)
  — but the score is own convergence plus HALF the strongest named
  convergence, so it is NOT monotone in the item's own convergence:
  `merlin_convergence_inversion` is the witness (a finding, not hidden).
* Thor: never ranks fewer unblocks above more, cone breaking ties
  (`thor_monotone_unblocks`); the unblocks count is the number of open items
  that `needs` this item (`unblocksOf`, benefit enabled through `needs`).
* Questor: a cost statement, not a benefit one — only the cheap class (one
  small handoff) is ever listed (`questor_cost_gate`,
  `questor_never_lists_unready`); no unready item is ranked at all.

## Computed reasons
Computed values need no approval: typed terms must report the cited facts.
The live check is futon3c scripts/giraffe_party_conformance.py.

-/

namespace Circus.Characters

open Circus Arxana

/-! ## The data the characters read -/

abbrev FeatureId := String
abbrev HarmLine := String

inductive PendingLabel
  | readyOneHandoff
  | partly
  | hypothesised
  | mayBeNoLongerWanted
deriving DecidableEq, Repr

/-- One pending item as the script reads it (`giraffe_party.py` `pending`
rows): its label, the lexicon features it names, its harm line (none = not on
one), its pending level. -/
structure Pending where
  item : EntityId
  label : PendingLabel
  names : List FeatureId
  harm : Option HarmLine
  level : Nat
deriving DecidableEq, Repr

/-- The lexicon: feature slugs, the unfinished ones, and each feature's
convergence score. `convergence f = none` is the typed absence. -/
structure Lexicon where
  all : List FeatureId
  unfinished : List FeatureId
  convergence : FeatureId → Option Nat

/-- Giraffe's own pass: the pass order over the open items, with each item's
unblocks count and cone size (the fields `thor_order` reads; the ranks in
`thyra_order`'s reason strings come from the same order). -/
structure PassFacts where
  order : List EntityId
  unblocks : EntityId → Nat
  cone : EntityId → Nat
  /-- The pass's stored rank, not the zero-based position in order. -/
  rank : EntityId → Option Nat

/-- Thor's benefit is enabled through `needs`: the number of OPEN items that
need `x`. (The script computes this over its own adjacency; the model makes
the dependence on `needs` explicit.) -/
def unblocksOf (inp : Inputs) (x : EntityId) : Nat :=
  ((inp.items.filter (·.open_)).filter fun it => inp.needs it.id x).length

/-! ## Reason terms -/

/-- Distinguish literal text from computed terms. -/
def Term.isLiteral : Term → Bool
  | .lit _ => true
  | _ => false

/-- Turn a taken, already-ordered list into `Entry`s: descending priorities,
the character's rendered terms as the reason. -/
def toEntries {α : Type} (who : Agent) (termsOf : α → List Term)
    (itemOf : α → EntityId) : Nat → List α → List Entry
  | _, [] => []
  | p, x :: xs =>
    { item := itemOf x, priority := p,
      reason := ⟨(termsOf x).map Term.render, who, termsOf x, who⟩ } ::
      toEntries who termsOf itemOf (p - 1) xs

theorem toEntries_items {α : Type} (who : Agent) (termsOf : α → List Term)
    (itemOf : α → EntityId) :
    ∀ (p : Nat) (l : List α), (toEntries who termsOf itemOf p l).map Entry.item = l.map itemOf := by
  intro p l
  induction l generalizing p with
  | nil => rfl
  | cons x xs ih => simp [toEntries, ih]

/-! ## Thor: the pass order (unblocks, then cone) -/

/-- The producer defaults a missing rank to zero when rendering Thor. This
is a reported default, not a reconstructed position in the pass order. -/
def thorTerms (pf : PassFacts) (x : EntityId) : List Term :=
  [.unblocksCount (pf.unblocks x), .coneCount (pf.cone x),
    match pf.rank x with
    | some r => .passRank r
    | none => .noPassRank]

/-- Thor's order: Giraffe's pass order, first `top` (`thor_order`). -/
def thorOrder (pf : PassFacts) (top : Nat) : List Entry :=
  toEntries .thor (thorTerms pf) (fun x => x) top (pf.order.take top)

/-- The pass order is sorted by unblocks descending, cone breaking ties (the
script's sort before the pass is handed to Thor). -/
def passLE (pf : PassFacts) (a b : EntityId) : Prop :=
  pf.unblocks b < pf.unblocks a ∨
    (pf.unblocks a = pf.unblocks b ∧ pf.cone b ≤ pf.cone a)

/-- THOR'S BENEFIT MONOTONICITY: never fewer unblocks above more, cone
breaking ties — an item that unblocks strictly less never ranks above one
that unblocks more. -/
theorem thor_monotone_unblocks (pf : PassFacts) (top : Nat)
    (hsorted : pf.order.Pairwise (passLE pf)) :
    (thorOrder pf top).Pairwise (fun e1 e2 => passLE pf e1.item e2.item) := by
  have hmap : (thorOrder pf top).map Entry.item = pf.order.take top := by
    simp only [thorOrder]
    rw [toEntries_items]; simp
  have h1 : (pf.order.take top).Pairwise (passLE pf) := List.Pairwise.take hsorted
  rw [← hmap, List.pairwise_map] at h1
  exact h1

/-! ## Thyra: harm lines, most ready first -/

def thyraKey (pf : PassFacts) (p : Pending) : Nat × Nat × EntityId :=
  (match pf.rank p.item with | some r => r | none => 10 ^ 6, p.level, p.item)

/-- Lexicographic ≤ on Thyra's (rank, level, item) key. -/
def lex3LE (a b : Nat × Nat × EntityId) : Prop :=
  a.1 < b.1 ∨ (a.1 = b.1 ∧
    (a.2.1 < b.2.1 ∨ (a.2.1 = b.2.1 ∧ a.2.2 ≤ b.2.2)))

theorem lex3LE_first {a b : Nat × Nat × EntityId} (h : lex3LE a b) : a.1 ≤ b.1 := by
  rcases h with h | ⟨h, _⟩
  · omega
  · omega

/-- Readiness in rank terms: x's pass rank (10^6 when outside the pass) is
at most y's. -/
def thyraRankLE (pf : PassFacts) (x y : EntityId) : Prop :=
  (match pf.rank x with | some r => r | none => 10 ^ 6) ≤
    (match pf.rank y with | some r => r | none => 10 ^ 6)

def thyraTerms (pf : PassFacts) (p : Pending) : List Term :=
  [.harmLine (p.harm.getD "")] ++
    (match pf.rank p.item with
      | some r => [.passRank r]
      | none => [.notInPass p.level])

/-- Thyra's order: items on a harm line, most ready first (`thyra_order`).
The input `ps` is already in the script's sorted order (the hypothesis of
`thyra_monotone` says exactly what that sort guarantees). -/
def thyraOrder (pf : PassFacts) (ps : List Pending) (top : Nat) : List Entry :=
  toEntries .thyra (thyraTerms pf) (·.item) top
    ((ps.filter fun p => p.harm.isSome).take top)

/-- THYRA'S BENEFIT GATE: every listed item is on a harm line — an item with
no harm line is never ranked at all. -/
theorem thyra_only_harm_items (pf : PassFacts) (ps : List Pending) (top : Nat) :
    ∀ e ∈ thyraOrder pf ps top, ∃ p ∈ ps, p.item = e.item ∧ p.harm.isSome = true := by
  intro e he
  have hm : e.item ∈ ((ps.filter fun p => p.harm.isSome).take top).map (·.item) := by
    rw [← toEntries_items]
    exact List.mem_map_of_mem (f := Entry.item) he
  obtain ⟨p, hp, hpitem⟩ := List.mem_map.mp hm
  exact ⟨p, (List.mem_filter.mp (List.mem_of_mem_take hp)).1, hpitem,
    (List.mem_filter.mp (List.mem_of_mem_take hp)).2⟩

/-- THYRA'S BENEFIT MONOTONICITY: among harm items, readiness rules — the
pass-rank component of the sort key never increases along the list (pending
level and item id break ties behind it). -/
theorem thyraKey_rank_le {pf : PassFacts} {a b : Pending}
    (h : lex3LE (thyraKey pf a) (thyraKey pf b)) : thyraRankLE pf a.item b.item :=
  lex3LE_first h

theorem thyra_monotone (pf : PassFacts) (ps : List Pending) (top : Nat)
    (hsorted : ((ps.filter fun p => p.harm.isSome).take top).Pairwise
      fun a b => lex3LE (thyraKey pf a) (thyraKey pf b)) :
    (thyraOrder pf ps top).Pairwise fun e1 e2 => thyraRankLE pf e1.item e2.item := by
  have hrank : ((ps.filter fun p => p.harm.isSome).take top).Pairwise
      fun a b => thyraRankLE pf a.item b.item :=
    List.Pairwise.imp (fun {_ _} h => thyraKey_rank_le h) hsorted
  have hmap : (thyraOrder pf ps top).map Entry.item
      = ((ps.filter fun p => p.harm.isSome).take top).map (·.item) :=
    toEntries_items _ _ _ _ _
  have hrank2 : (((ps.filter fun p => p.harm.isSome).take top).map (·.item)).Pairwise
      (fun x y => thyraRankLE pf x y) := by
    rw [List.pairwise_map]
    exact hrank
  rw [← hmap] at hrank2
  exact (List.pairwise_map (f := Entry.item)
    (R := fun x y => thyraRankLE pf x y)).mp hrank2

/-! ## Merlin: convergence, half-weighted -/

/-- A row of Merlin's input: the feature the item IS (none = typed absence,
dropped), that feature's convergence, and the convergence of the strongest
unfinished feature the item names (with its slug). The script's score is
own + named/2, which can be half-integral (e.g. 27 + 27/2 = 40.5 for a live
item, found by scripts/giraffe_party_conformance.py, futon3c 12dd5516d), so the
model keeps the DOUBLED score 2·own + named: exact in Nat, same order. -/
structure MerlinRow where
  item : EntityId
  label : PendingLabel
  feature : Option FeatureId
  own : Nat
  named : Nat
  namedBest : Option FeatureId
deriving DecidableEq, Repr

/-- Twice the script's score (own + named/2), so half-points are exact. -/
def MerlinRow.score (r : MerlinRow) : Nat := 2 * r.own + r.named

/-- The script drops "may be no longer wanted" rows and rows whose item is no
lexicon feature (the typed absence, recorded separately, never rank 0). -/
def merlinKept (r : MerlinRow) : Bool :=
  r.label != .mayBeNoLongerWanted && r.feature.isSome

def merlinTerms (r : MerlinRow) : List Term :=
  [.featureIs (r.feature.getD ""), .convergence r.own, .withinTwoHops] ++
    (if r.named = 0 then [] else match r.namedBest with
      | some f => [.namesHalved f r.named]
      | none => [])

/-- Merlin's order: kept rows, first `top` (`merlin_order`); the input is
already in the script's sorted order (score descending, item ascending). -/
def merlinOrder (rows : List MerlinRow) (top : Nat) : List Entry :=
  toEntries .merlin merlinTerms (·.item) top ((rows.filter merlinKept).take top)

def merlinLE (a b : MerlinRow) : Prop :=
  b.score < a.score ∨ (a.score = b.score ∧ a.item ≤ b.item)

/-- MERLIN'S BENEFIT MONOTONICITY: never a lower score above a higher. -/
theorem merlin_monotone_score (rows : List MerlinRow) (top : Nat)
    (hsorted : (rows.filter merlinKept).Pairwise merlinLE) :
    ((rows.filter merlinKept).take top).Pairwise merlinLE :=
  List.Pairwise.take hsorted

/-- FINDING: Merlin is NOT monotone in the item's own convergence. The score
adds HALF the strongest named convergence, so an item with lower own
convergence but a strong named feature outranks it. -/
private def rowLow : MerlinRow := ⟨3, .partly, some "g", 3, 10, some "g"⟩  -- own 3, script score 8 (doubled 16)
private def rowHigh : MerlinRow := ⟨5, .partly, some "f", 5, 0, none⟩       -- own 5, script score 5 (doubled 10)

theorem merlin_convergence_inversion :
    [rowLow, rowHigh].Pairwise merlinLE ∧ rowLow.own < rowHigh.own := by
  refine ⟨?_, by decide⟩
  refine List.Pairwise.cons ?_ (List.Pairwise.cons ?_ List.Pairwise.nil)
  · intro b hb
    cases hb with
    | head => left; decide
    | tail _ h => cases h
  · intro b hb; cases hb

/-! ## Questor: the cheap class (a cost statement) -/

def questorTerms (best : Pending → Option (FeatureId × Nat)) (p : Pending) : List Term :=
  [.readyOneHandoff] ++
    (match best p with
      | some (f, n) => [.namesConv f n]
      | none => [.noScoredFeature])

/-- Questor's order: "ready (one small handoff)" items only, strongest named
feature first (`questor_order`). COST, not benefit: the cheap class is all
that is ever listed. -/
def questorOrder (ps : List Pending)
    (best : Pending → Option (FeatureId × Nat)) (top : Nat) : List Entry :=
  toEntries .questor (questorTerms best) (·.item) top
    ((ps.filter fun p => p.label == .readyOneHandoff).take top)

/-- QUESTOR'S COST GATE: every listed item is a one-small-handoff item. -/
theorem questor_cost_gate (ps : List Pending)
    (best : Pending → Option (FeatureId × Nat)) (top : Nat) :
    ∀ e ∈ questorOrder ps best top,
      ∃ p ∈ ps, p.item = e.item ∧ p.label = .readyOneHandoff := by
  intro e he
  have hm : e.item ∈ ((ps.filter fun p => p.label == .readyOneHandoff).take top).map (·.item) := by
    rw [← toEntries_items]
    exact List.mem_map_of_mem (f := Entry.item) he
  obtain ⟨p, hp, hpitem⟩ := List.mem_map.mp hm
  have hpm := List.mem_of_mem_take hp
  have hb : p.label = .readyOneHandoff := by
    simpa using (List.mem_filter.mp hpm).2
  exact ⟨p, (List.mem_filter.mp hpm).1, hpitem, hb⟩

/-- An item that is not ready is never listed: no expensive item is ranked.
`hdet` says the label is determined by the item id (items are one per
document, the 2026-11-12/13 dedup). -/
theorem questor_never_lists_unready (ps : List Pending)
    (best : Pending → Option (FeatureId × Nat)) (top : Nat) (p : Pending)
    (hne : p.label ≠ .readyOneHandoff)
    (hp : p ∈ ps)
    (hdet : ∀ q ∈ ps, q.item = p.item → q.label = p.label) :
    p.item ∉ (questorOrder ps best top).map Entry.item := by
  intro hmem
  obtain ⟨e, he, hitem⟩ := List.mem_map.mp hmem
  obtain ⟨p', hp', hpitem, hlab⟩ := questor_cost_gate ps best top e he
  rw [hitem] at hpitem
  have hlabp : p'.label = p.label := hdet p' hp' hpitem
  rw [hlabp] at hlab
  exact hne hlab

/-! ## Thor satisfies the endgame cover hypothesis -/

/-- Thor as a `Character` over its pass facts. -/
def thorCharacter (pf : PassFacts) : Character := ⟨.thor, fun _ => thorOrder pf budgetTen⟩

/-- THOR SATISFIES THE ENDGAME COVER: when the pass order IS the open items
(the pass covers every open item and nothing else), Thor lists
`min 10 (open count)` distinct open items — exactly the primary theorem's
`hcover` hypothesis. -/
theorem thor_endgame_cover (inp : Inputs) (pf : PassFacts)
    (heq : pf.order = (inp.items.filter (·.open_)).map Item.id)
    (hnd : ((inp.items.filter (·.open_)).map Item.id).Nodup) :
    ∃ l : List EntityId, l.Nodup
      ∧ l.length = min budgetTen ((inp.items.filter (·.open_)).length)
      ∧ (∀ x ∈ l, ∃ it ∈ inp.items, it.open_ = true ∧ it.id = x)
      ∧ (∀ x ∈ l, x ∈ ((thorCharacter pf).order inp).map (·.item)) := by
  refine ⟨(pf.order).take budgetTen, ?_, ?_, ?_, ?_⟩
  · rw [heq]; exact hnd.sublist (List.take_sublist _ _)
  · rw [heq, List.length_take, List.length_map]
  · intro x hx
    have hxmem : x ∈ pf.order := List.mem_of_mem_take hx
    rw [heq] at hxmem
    obtain ⟨it, hit, hitx⟩ := List.mem_map.mp hxmem
    exact ⟨it, (List.mem_filter.mp hit).1, (List.mem_filter.mp hit).2, hitx⟩
  · intro x hx
    have hmap : ((thorCharacter pf).order inp).map (·.item) = pf.order.take budgetTen := by
      show (thorOrder pf budgetTen).map Entry.item = _
      simp only [thorOrder]
      rw [toEntries_items]; simp
    rw [hmap]
    exact hx

/-! ## Source faithfulness (restated 2026-11-15)
OLD: computed strings fail vocabulary membership.
NEW: computed strings are welcome; their typed values must equal source facts.
-/

def thorFacts (pf : PassFacts) (x : EntityId) : ReasonFacts :=
  { unblocks := pf.unblocks x, cone := pf.cone x, rank := pf.rank x }
def thyraFacts (pf : PassFacts) (p : Pending) : ReasonFacts :=
  { rank := pf.rank p.item, level := p.level, harm := p.harm }
def merlinFacts (r : MerlinRow) : ReasonFacts :=
  { feature := r.feature, convergence := r.own,
    named := r.namedBest.map (fun f => (f, r.named)), twoHopMetric := true }
def questorFacts (best : Pending → Option (FeatureId × Nat)) (p : Pending) : ReasonFacts :=
  { named := best p, ready := p.label == .readyOneHandoff }

theorem thor_terms_faithful (pf : PassFacts) (x : EntityId) :
    ∀ t ∈ thorTerms pf x, t.faithful (thorFacts pf x) := by
  intro t ht
  cases hr : pf.rank x <;> simp [thorTerms, hr] at ht <;>
    rcases ht with rfl | rfl | rfl <;> simp [Term.faithful, thorFacts, hr]

theorem thyra_terms_faithful (pf : PassFacts) (p : Pending) (harm : p.harm.isSome = true) :
    ∀ t ∈ thyraTerms pf p, t.faithful (thyraFacts pf p) := by
  intro t ht
  cases hh : p.harm with
  | none => simp [hh] at harm
  | some h =>
    cases hr : pf.rank p.item <;> simp [thyraTerms, hh, hr] at ht <;>
      rcases ht with rfl | rfl
    all_goals simp [Term.faithful, thyraFacts, hh, hr]

theorem merlin_terms_faithful (r : MerlinRow) (kept : merlinKept r = true) :
    ∀ t ∈ merlinTerms r, t.faithful (merlinFacts r) := by
  intro t ht
  have hf : r.feature.isSome = true := by
    cases h : r.feature <;> simp_all [merlinKept]
  cases h : r.feature with
  | none => simp [h] at hf
  | some f =>
    by_cases hz : r.named = 0
    · simp [merlinTerms, h, hz] at ht
      rcases ht with rfl | rfl | rfl <;> simp [Term.faithful, merlinFacts, h]
    · cases hn : r.namedBest with
      | none =>
        simp [merlinTerms, h, hz, hn] at ht
        rcases ht with rfl | rfl | rfl <;> simp [Term.faithful, merlinFacts, h]
      | some name =>
        simp [merlinTerms, h, hz, hn] at ht
        rcases ht with rfl | rfl | rfl | rfl <;> simp [Term.faithful, merlinFacts, h, hn]

theorem questor_terms_faithful (best : Pending → Option (FeatureId × Nat))
    (p : Pending) (ready : p.label = .readyOneHandoff) :
    ∀ t ∈ questorTerms best p, t.faithful (questorFacts best p) := by
  intro t ht
  cases hb : best p with
  | none =>
    simp [questorTerms, hb] at ht
    rcases ht with rfl | rfl <;> simp [Term.faithful, questorFacts, ready, hb]
  | some pair =>
    rcases pair with ⟨f,n⟩
    simp [questorTerms, hb] at ht
    rcases ht with rfl | rfl <;> simp [Term.faithful, questorFacts, ready, hb]

/-- Every emitted entry renders faithful terms from the same source row.
This connects the term proofs to the actual character output. -/
theorem toEntries_faithful {α : Type} (who : Agent) (termsOf : α → List Term)
    (itemOf : α → EntityId) (facts : α → ReasonFacts) (rows : List α)
    (hfaith : ∀ x ∈ rows, ∀ t ∈ termsOf x, t.faithful (facts x)) :
    ∀ priority e, e ∈ toEntries who termsOf itemOf priority rows →
      ∃ x ∈ rows, e.item = itemOf x ∧
        e.reason.explainable (fun _ _ => facts x) e.item := by
  induction rows with
  | nil => intro priority e he; cases he
  | cons x xs ih =>
    intro priority e he
    rcases List.mem_cons.mp he with rfl | he
    · exact ⟨x, List.mem_cons_self .., rfl, rfl, hfaith x (List.mem_cons_self ..)⟩
    · obtain ⟨y, hy, hi, hf⟩ := ih
        (fun y hy => hfaith y (List.mem_cons_of_mem _ hy)) (priority - 1) e he
      exact ⟨y, List.mem_cons_of_mem _ hy, hi, hf⟩

theorem thor_reasons_faithful (pf : PassFacts) (top : Nat) :
    ∀ e ∈ thorOrder pf top, ∃ x ∈ pf.order.take top, e.item = x ∧
      e.reason.explainable (fun _ _ => thorFacts pf x) e.item :=
  toEntries_faithful .thor (thorTerms pf) id (thorFacts pf) _
    (fun x _ => thor_terms_faithful pf x) top

theorem thyra_reasons_faithful (pf : PassFacts) (ps : List Pending) (top : Nat) :
    ∀ e ∈ thyraOrder pf ps top,
      ∃ p ∈ (ps.filter fun p => p.harm.isSome).take top, e.item = p.item ∧
        e.reason.explainable (fun _ _ => thyraFacts pf p) e.item := by
  apply toEntries_faithful
  intro p hp
  exact thyra_terms_faithful pf p (List.mem_filter.mp (List.mem_of_mem_take hp)).2

theorem merlin_reasons_faithful (rows : List MerlinRow) (top : Nat) :
    ∀ e ∈ merlinOrder rows top,
      ∃ r ∈ (rows.filter merlinKept).take top, e.item = r.item ∧
        e.reason.explainable (fun _ _ => merlinFacts r) e.item := by
  apply toEntries_faithful
  intro r hr
  exact merlin_terms_faithful r (List.mem_filter.mp (List.mem_of_mem_take hr)).2

theorem questor_reasons_faithful (ps : List Pending)
    (best : Pending → Option (FeatureId × Nat)) (top : Nat) :
    ∀ e ∈ questorOrder ps best top,
      ∃ p ∈ (ps.filter fun p => p.label == .readyOneHandoff).take top, e.item = p.item ∧
        e.reason.explainable (fun _ _ => questorFacts best p) e.item := by
  apply toEntries_faithful
  intro p hp
  apply questor_terms_faithful
  simpa using (List.mem_filter.mp (List.mem_of_mem_take hp)).2

-- TEETH: the rendered number itself is correct for its typed claim, but the
-- claim is false of the pass. This is not explainable even with a familiar string.
example : ¬ Reason.explainable (fun _ _ => { unblocks := 2 }) 17
    ⟨["unblocks 3 item(s)"], .thor, [.unblocksCount 3], .thor⟩ := by
  intro h
  have bad := h.2 (.unblocksCount 3) (by simp)
  change 3 = 2 at bad
  omega

/- OLD: rank := order.idxOf?, and Thyra emitted withinTwoHops.
NEW: retain the pass's own rank; Thyra emits only harm and rank/level.
These invented examples preserve the two producer/model counterexamples. -/
private def exPass : PassFacts := ⟨[17], fun _ => 2, fun _ => 0, fun _ => some 1⟩
private def exPending : Pending := ⟨17, .readyOneHandoff, [], some "H-toy", 1⟩
example : exPass.order.idxOf? 17 = some 0 ∧ exPass.rank 17 = some 1 := by decide
example : (thyraTerms exPass exPending).map Term.render =
    ["on harm line H-toy", "Giraffe pass rank 1"] := by decide
example : Term.withinTwoHops ∉ thyraTerms exPass exPending := by decide

/-- Every term Thor writes is computed (not a literal vocabulary word). -/
theorem thor_terms_all_computed (pf : PassFacts) (x : EntityId) :
    ∀ t ∈ thorTerms pf x, t.isLiteral = false := by
  intro t ht
  cases hr : pf.rank x <;> simp [thorTerms, hr] at ht <;>
    rcases ht with rfl | rfl | rfl <;> rfl

-- Thor with no pass rank says so (claude-13 review 2026-11-15: the producer
-- printed "Giraffe pass rank 0", a false claim; producer and model fixed to
-- "no Giraffe pass rank"). Merlin suppresses the named phrase at zero even
-- when the best-named feature exists.
example : Term.noPassRank ∈ thorTerms ⟨[], fun _ => 0, fun _ => 0, fun _ => none⟩ 17 ∧
    Term.passRank 0 ∉ thorTerms ⟨[], fun _ => 0, fun _ => 0, fun _ => none⟩ 17 := by decide
-- TEETH: the old default would claim rank 0 against a basis with no rank.
example : ¬ (Term.passRank 0).faithful (thorFacts ⟨[], fun _ => 0, fun _ => 0, fun _ => none⟩ 17) := by
  simp [Term.faithful, thorFacts]
example : Term.namesHalved "toy" 0 ∉
    merlinTerms ⟨17, .partly, some "own-toy", 2, 0, some "toy"⟩ := by decide

end Circus.Characters
