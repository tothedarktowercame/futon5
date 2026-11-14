import Circus.Retro
/-!
# The concrete loop (M-repl-circus, step 3)

`step` and `rebuild`, left opaque in `Circus/Basic.lean` while the
statements were read, made concrete here:

* `rebuild cs inputs` is the party view: `Circus.Retro.partyVote` (the
  retro-specification of futon3c `scripts/giraffe_party.py` `party_vote`)
  round-robins over the characters' orderings with the top-ten budget; each
  shown entry carries its contributing character's reason for the item.
* `step` updates `Inputs` per event and recomputes `shown` by `rebuild`.

The statements `incremental_eq_rebuilt` and `quiescent_shows_nothing_new`
move here from `Basic.lean` (they are about the concrete loop) and are
proved. Where a statement needed an extra hypothesis to be true, the
hypothesis is added and the reason is in its docstring.
-/
namespace Circus

open Arxana

/-! ## `rebuild`: the party view -/

/-- The top-ten budget (M-repl-circus §DERIVE: the operator's Mojira view). -/
def budgetTen : Nat := 10

/-- The first character's own entry for an item, if it has one. -/
def firstEntryFor (i : EntityId) : List Entry → Option Entry
  | [] => none
  | e :: es => if e.item = i then some e else firstEntryFor i es

/-- The reason a shown entry carries: the terms of the first character's own
entry for that item (its accepted benefit lines, the operator's
introductions/word rules — whatever vocabulary its `order` used), credited
to the character that contributed the item this round. -/
def reasonFor : List Character → Inputs → Agent → EntityId → Reason
  | [], _, a, _ => ⟨[], a⟩
  | c :: cs', inp, a, i =>
      match firstEntryFor i (c.order inp) with
      | some e => ⟨e.reason.terms, a⟩
      | none => reasonFor cs' inp a i

/-- Turn a party round into shown entries: the first entry has the top
priority, each later one a step less. -/
def entriesFrom : Nat → (Agent → EntityId → Reason) → List (Agent × EntityId) → List Entry
  | _, _, [] => []
  | p, r, (a, i) :: rest => ⟨i, p, r a i⟩ :: entriesFrom (p - 1) r rest

/-- The shown view: the party round over the characters' orderings, entries
carrying their contributor's reason. -/
def orderingsOf (cs : List Character) (inp : Inputs) : Retro.Orderings :=
  cs.map fun c => (c.who, (c.order inp).map (·.item))

def rebuild (cs : List Character) (inp : Inputs) : List Entry :=
  entriesFrom budgetTen (reasonFor cs inp) (Retro.partyVote budgetTen [] (orderingsOf cs inp))

/-! ## `step`: events to inputs, inputs to shown -/

def bump (f : EntityId → Nat) (i : EntityId) : EntityId → Nat :=
  fun x => if x = i then f i + 1 else f x

/-- One event's effect on the loop's inputs.

* `turn _ .settled`: the 2026-11-14 activation amendment — a settled turn
  bumps the activation of every focused item.
* other `turn` events write records (Basic's `wiring`), not inputs.
* `docChange i _`: the document changed, so the last reading of `i` is
  withdrawn — a fresh reading is needed (the giraffe firing rule's cause).
* `readingLanded i v`: the fresh reading of version `v` is on the table.
* `focus`: 猴's weights. `tick`: nothing moves.
-/
def applyEvent (inp : Inputs) : Event → Inputs
  | .turn _ .settled =>
      { inp with activation := inp.focus.foldl (fun acc p => bump acc p.1) inp.activation }
  | .turn _ _ => inp
  | .docChange i _ => { inp with readings := inp.readings.filter (fun p => p.1 != i) }
  | .readingLanded i v =>
      { inp with readings := (i, v) :: inp.readings.filter (fun p => p.1 != i) }
  | .focus w => { inp with focus := w }
  | .tick => inp

/-- One step of the loop: advance the clock, apply the event to the inputs,
recompute what is shown from the new inputs by `rebuild`, and record that an
input-changing event moved the change time. -/
def step (cs : List Character) (s : State) (e : Event) : State :=
  let inp := applyEvent s.inputs e
  { s with
      now := s.now + 1
      inputs := inp
      shown := rebuild cs inp
      lastInputChange := match e with
        | .turn _ _ | .tick => s.lastInputChange
        | .docChange .. | .readingLanded .. | .focus .. => s.lastInputChange + 1
      lastRecompute := s.now + 1 }

/-! ## The two step statements, now proved

`incremental_eq_rebuilt` is true by construction: `step` recomputes `shown`
from the very inputs it just wrote, so the incremental and rebuilt views
cannot drift. That it is `rfl` is the point — it pins the implementation to
the recompute-at-every-step discipline the design names. -/

/-- Recomputing after each event agrees with rebuilding from the inputs:
what is shown after a step is exactly `rebuild` of the inputs after that
step. -/
theorem incremental_eq_rebuilt (cs : List Character) (s : State) (e : Event) :
    (step cs s e).shown = rebuild cs (step cs s e).inputs := rfl

/-- With no events but ticks, nothing new is shown.

NEEDS THE HYPOTHESIS `hs : s.shown = rebuild cs s.inputs` (added in step 3;
the draft had none): the statement is about the loop's invariant — a state
the loop produced shows `rebuild` of its inputs, so a tick that changes no
input shows the same list. For an arbitrary hand-built state it is FALSE,
and the witness below shows a state whose `shown` was never built by
`rebuild` failing it. -/
theorem quiescent_shows_nothing_new (cs : List Character) (s : State)
    (hs : s.shown = rebuild cs s.inputs) :
    (step cs s .tick).shown = s.shown := by
  simp [step, applyEvent, hs]

/-- The hypothesis of `quiescent_shows_nothing_new` has content: this state
was not built by the loop (`shown` empty while a character would rank an
item), and a tick does NOT leave it unchanged. -/
private def exChar : Character :=
  ⟨.thor, fun _ =>
    [{ item := 0, priority := 0, reason := { terms := ["t"], chosenBy := .thor } }]⟩

private def exState : State :=
  ⟨0, ⟨[], [], fun _ => 0, fun _ _ => false, fun _ => [], [], 0⟩, [], 0, 0⟩
example : (step [exChar] exState .tick).shown.length = 1 := by native_decide

example : ¬ (step [exChar] exState .tick).shown = exState.shown := by
  intro h
  have h1 : (step [exChar] exState .tick).shown.length = 1 := by native_decide
  rw [h] at h1
  exact absurd h1 (by decide)

/-! ## `top_ten_is_a_cascade` (statement carried over from `Basic.lean`;
proof is a later step) -/

/-- Every entry after the first either needs an earlier entry, or names why
it stands alone (a nonempty reason). -/
theorem top_ten_is_a_cascade (cs : List Character) (s : State)
    (h : s.shown = rebuild cs s.inputs) :
    Cascade s.inputs s.shown := by
  sorry

/-! ## The primary theorem (step 4)

Lemmas about the party round (`Retro.partyVote`), then the four parts of
Joe's statement under explicit hypotheses about the characters. -/

/-- `drawFresh` skips only already-chosen items before the fresh one it
returns: every other fresh item of the list survives into the suffix. -/
theorem drawFresh_survivor {seen : List EntityId} :
    ∀ (l : List EntityId) (i : EntityId) (l' : List EntityId) (x : EntityId),
      Retro.drawFresh seen l = some (i, l') → x ∈ l → x ∉ seen → x = i ∨ x ∈ l' := by
  intro l
  induction l with
  | nil => intro i l' x h; exact absurd h (by simp [Retro.drawFresh])
  | cons a as ih =>
    intro i l' x h hx hxseen
    rw [Retro.drawFresh] at h
    by_cases hc : seen.contains a
    · rw [if_pos hc] at h
      rcases List.mem_cons.mp hx with rfl | hxas
      · exact absurd (List.contains_iff_mem.mp hc) hxseen
      · exact ih i l' x h hxas hxseen
    · rw [if_neg hc] at h
      simp only [Option.some.injEq, Prod.mk.injEq] at h
      obtain ⟨rfl, rfl⟩ := h
      rcases List.mem_cons.mp hx with rfl | hxas
      · exact Or.inl rfl
      · exact Or.inr hxas

/-- A list with no fresh item has only already-chosen items. -/
theorem drawFresh_none {seen : List EntityId} :
    ∀ (l : List EntityId), Retro.drawFresh seen l = none → ∀ x ∈ l, x ∈ seen := by
  intro l
  induction l with
  | nil => intro _ x hx; cases hx
  | cons a as ih =>
    intro h x hx
    rw [Retro.drawFresh] at h
    by_cases hc : seen.contains a
    · rw [if_pos hc] at h
      rcases List.mem_cons.mp hx with rfl | hxas
      · exact List.contains_iff_mem.mp hc
      · exact ih h x hxas
    · rw [if_neg (by simpa using hc)] at h
      exact absurd h (by simp)

/-! ### Fresh availability through a round -/

/-- n fresh items are available in the round: distinct, none already chosen,
each present in some character's list. -/
def FreshAvail (seen : List EntityId) (os : Retro.Orderings) (n : Nat) : Prop :=
  ∃ fs : List EntityId, fs.Nodup ∧ n ≤ fs.length
    ∧ ∀ x ∈ fs, x ∉ seen ∧ ∃ a l, (a, l) ∈ os ∧ x ∈ l

theorem FreshAvail.mono {seen : List EntityId} {os : Retro.Orderings} {m n : Nat}
    (h : m ≤ n) (hf : FreshAvail seen os n) : FreshAvail seen os m := by
  obtain ⟨fs, hnd, hlen, hmem⟩ := hf
  exact ⟨fs, hnd, Nat.le_trans h hlen, hmem⟩

/-- A fresh item present in some character's list makes `sweep` succeed. -/
theorem sweep_some_of_mem {seen : List EntityId} :
    ∀ (os : Retro.Orderings) (a0 : Agent) (l0 : List EntityId) (x : EntityId),
      (a0, l0) ∈ os → x ∈ l0 → x ∉ seen → Retro.sweep seen os ≠ none := by
  intro os
  induction os with
  | nil => intro a0 l0 x hm; cases hm
  | cons p ps ih =>
    intro a0 l0 x hm hx hxseen
    rcases hd : Retro.drawFresh seen p.2 with _ | ⟨j, l'⟩
    · simp only [Retro.sweep, hd]
      rcases List.mem_cons.mp hm with hp | hm'
      · subst hp
        exact absurd (drawFresh_none l0 hd x hx) hxseen
      · exact ih a0 l0 x hm' hx hxseen
    · intro hcon
      simp only [Retro.sweep, hd] at hcon
      exact absurd hcon (by simp)

/-- Presence transfers across a sweep: a fresh item other than the one
chosen stays present in the rotated round. -/
theorem sweep_presence {seen : List EntityId} :
    ∀ (os : Retro.Orderings) (a : Agent) (i : EntityId) (q : Agent × List EntityId)
      (rest : Retro.Orderings) (x : EntityId),
      Retro.sweep seen os = some (a, i, q :: rest) → x ∉ seen → x ≠ i →
      ∀ b l, (b, l) ∈ os → x ∈ l → ∃ l', (b, l') ∈ rest ++ [q] ∧ x ∈ l' := by
  intro os
  induction os with
  | nil => intro a i q rest x hs _ _ b l hm _; cases hm
  | cons p ps ih =>
    intro a i q rest x hs hxseen hne b l hm hl
    rcases hd : Retro.drawFresh seen p.2 with _ | ⟨j, l'⟩
    · simp only [Retro.sweep, hd] at hs
      rcases List.mem_cons.mp hm with he | hm'
      · subst he
        exact absurd (drawFresh_none l hd x hl) hxseen
      · exact ih a i q rest x hs hxseen hne b l hm' hl
    · simp only [Retro.sweep, hd, Option.some.injEq, Prod.mk.injEq,
                  List.cons.injEq] at hs
      obtain ⟨rfl, rfl, rfl, rfl⟩ := hs
      rcases List.mem_cons.mp hm with he | hm'
      · subst he
        rcases drawFresh_survivor l _ l' x hd hl hxseen with rfl | hxl
        · exact absurd rfl hne
        · exact ⟨l', List.mem_append_right _ (List.mem_cons_self ..), hxl⟩
      · exact ⟨l, List.mem_append_left _ hm', hl⟩

/-! ### List helpers (EntityId = Nat) -/

theorem nodup_erase (b : EntityId) : ∀ (l : List EntityId), l.Nodup → (l.erase b).Nodup := by
  intro l
  induction l with
  | nil => intro hn; exact hn
  | cons a as ih =>
    intro hn
    by_cases hab : a = b
    · subst hab
      rw [List.erase_cons_head]
      exact (List.nodup_cons.mp hn).2
    · rw [List.erase_cons_tail (by simpa using hab)]
      refine List.nodup_cons.mpr ⟨?_, ih ((List.nodup_cons.mp hn).2)⟩
      intro hmem
      have hxa : a ∈ as := (List.mem_erase_of_ne hab).mp hmem
      exact (List.nodup_cons.mp hn).1 hxa

theorem mem_erase (b : EntityId) {x : EntityId} :
    ∀ (l : List EntityId), l.Nodup → x ∈ l.erase b → x ∈ l ∧ x ≠ b := by
  intro l
  induction l with
  | nil => intro _ h; cases h
  | cons a as ih =>
    intro hnd h
    by_cases hab : a = b
    · rw [hab, List.erase_cons_head] at h
      refine ⟨List.mem_cons_of_mem _ h, ?_⟩
      intro he
      have haa : a ∈ as := by
        rw [hab]
        rw [he] at h
        exact h
      exact (List.nodup_cons.mp hnd).1 haa
    · rw [List.erase_cons_tail (by simpa using hab)] at h
      rcases List.mem_cons.mp h with rfl | h'
      · exact ⟨List.mem_cons_self .., fun he => hab he⟩
      · obtain ⟨h1, h2⟩ := ih (List.nodup_cons.mp hnd).2 h'
        exact ⟨List.mem_cons_of_mem _ h1, h2⟩

/-! ### `partyVote` fills the budget when fresh items are available -/

/-- One round of `partyVote` keeps enough fresh items available: it consumes
exactly the one item it chose. -/
theorem freshAvail_step {seen : List EntityId} :
    ∀ (os : Retro.Orderings) (a : Agent) (i : EntityId) (q : Agent × List EntityId)
      (rest : Retro.Orderings) (n : Nat),
      Retro.sweep seen os = some (a, i, q :: rest) → FreshAvail seen os (n + 1) →
      FreshAvail (i :: seen) (rest ++ [q]) n := by
  intro os a i q rest n hs hfresh
  obtain ⟨fs, hnd, hlen, hmem⟩ := hfresh
  refine ⟨fs.erase i, nodup_erase i fs hnd, ?_, ?_⟩
  · -- n ≤ (fs.erase i).length, since at most the chosen item i is removed
    by_cases him : i ∈ fs
    · rw [List.length_erase_of_mem him]
      omega
    · rw [List.erase_of_not_mem him]
      omega
  · intro x hx
    obtain ⟨h1, h2⟩ := mem_erase i fs hnd hx
    obtain ⟨hxseen, b, l, hmem', hxl⟩ := hmem x h1
    refine ⟨?_, ?_⟩
    · intro hc
      rcases List.mem_cons.mp hc with rfl | hc'
      · exact h2 rfl
      · exact hxseen hc'
    have hne : x ≠ i := h2
    obtain ⟨l', hmem'', hxl'⟩ := sweep_presence os a i q rest x hs hxseen hne b l hmem' hxl
    exact ⟨b, l', hmem'', hxl'⟩

theorem sweep_ne_none {seen : List EntityId} {os : Retro.Orderings} {n : Nat}
    (h : FreshAvail seen os (n + 1)) : Retro.sweep seen os ≠ none := by
  obtain ⟨fs, _, hlen, hmem⟩ := h
  cases fs with
  | nil => exact absurd hlen (by simp)
  | cons x xs =>
    obtain ⟨hxseen, b, l, hmem', hxl⟩ := hmem x (List.mem_cons_self ..)
    exact sweep_some_of_mem os b l x hmem' hxl hxseen

/-- The party round fills its budget when enough fresh items are available. -/
theorem partyVote_length :
    ∀ (n : Nat) (seen : List EntityId) (os : Retro.Orderings),
      FreshAvail seen os n → (Retro.partyVote n seen os).length = n := by
  intro n
  induction n with
  | zero => intro _ _ _; rfl
  | succ n ih =>
    intro seen os h
    rcases hs : Retro.sweep seen os with _ | ⟨a, i, os''⟩
    · exact absurd hs (sweep_ne_none h)
    · cases hos : os'' with
      | nil =>
        obtain ⟨l, hl⟩ := Retro.sweep_head_contributes seen os a i os'' hs
        rw [hos] at hl
        exact absurd hl (by simp)
      | cons q rest =>
        rw [hos] at hs
        have hf := freshAvail_step os a i q rest n hs h
        simp only [Retro.partyVote, hs, List.length_cons]
        have hlen := ih (i :: seen) (rest ++ [(q.fst, q.snd)]) hf
        omega

/-! ### `entriesFrom` and `reasonFor` -/

theorem entriesFrom_length (r : Agent → EntityId → Reason) :
    ∀ (l : List (Agent × EntityId)) (p : Nat), (entriesFrom p r l).length = l.length := by
  intro l
  induction l with
  | nil => intro p; rfl
  | cons x xs ih => intro p; simp [entriesFrom, ih (p - 1)]

theorem entriesFrom_priority (r : Agent → EntityId → Reason) :
    ∀ (l : List (Agent × EntityId)) (p : Nat) (k : Nat)
      (hk : k < (entriesFrom p r l).length),
      ((entriesFrom p r l)[k]'hk).priority = p - k := by
  intro l
  induction l with
  | nil => intro p k hk; exact absurd hk (Nat.not_lt_zero k)
  | cons x xs ih =>
    intro p k hk
    cases k with
    | zero => rfl
    | succ k' =>
      have hk' : k' < (entriesFrom (p - 1) r xs).length := by
        have h1 := entriesFrom_length r (x :: xs) p
        rw [entriesFrom_length r (x :: xs) p] at hk
        rw [entriesFrom_length]
        simp only [List.length_cons] at hk ⊢
        omega
      simp only [entriesFrom, List.getElem_cons_succ, ih (p - 1) k' hk']
      omega

theorem entriesFrom_reason (r : Agent → EntityId → Reason) :
    ∀ (l : List (Agent × EntityId)) (p : Nat) (e : Entry),
      e ∈ entriesFrom p r l → ∃ a i, e.reason = r a i := by
  intro l
  induction l with
  | nil => intro p e he; cases he
  | cons x xs ih =>
    intro p e he
    rcases List.mem_cons.mp he with rfl | he'
    · exact ⟨x.1, x.2, rfl⟩
    · exact ih (p - 1) e he'

theorem firstEntryFor_some :
    ∀ (i : EntityId) (es : List Entry) (e : Entry),
      firstEntryFor i es = some e → e ∈ es ∧ e.item = i := by
  intro i es
  induction es with
  | nil => intro e h; exact absurd h (by simp [firstEntryFor])
  | cons x xs ih =>
    intro e h
    rw [firstEntryFor] at h
    by_cases hxi : x.item = i
    · rw [if_pos hxi] at h
      simp only [Option.some.injEq] at h
      subst h
      exact ⟨List.mem_cons_self .., hxi⟩
    · rw [if_neg hxi] at h
      obtain ⟨h1, h2⟩ := ih e h
      exact ⟨List.mem_cons_of_mem _ h1, h2⟩

/-- Every term of a shown entry's reason comes from some character's own
entry for that item. -/
theorem reasonFor_terms (cs : List Character) (inp : Inputs) (a : Agent) (i : EntityId) :
    ∀ t ∈ (reasonFor cs inp a i).terms,
      ∃ c ∈ cs, ∃ e ∈ c.order inp, e.item = i ∧ t ∈ e.reason.terms := by
  induction cs with
  | nil => intro t ht; cases ht
  | cons c cs' ih =>
    intro t ht
    rcases hf : firstEntryFor i (c.order inp) with _ | e
    · rw [reasonFor, hf] at ht
      obtain ⟨c', hc', e, he1, he2, he3⟩ := ih t ht
      exact ⟨c', List.mem_cons_of_mem _ hc', e, he1, he2, he3⟩
    · rw [reasonFor, hf] at ht
      obtain ⟨hmem, hitem⟩ := firstEntryFor_some i (c.order inp) e hf
      exact ⟨c, List.mem_cons_self .., e, hmem, hitem, ht⟩

/-! ### Coverage: open items are fresh -/

theorem open_fresh (cs : List Character) (inp : Inputs)
    (hopen : ((inp.items.filter (·.open_)).map Item.id).Nodup)
    (hcover : ∀ i ∈ inp.items, i.open_ = true →
      ∃ c ∈ cs, ∃ e ∈ c.order inp, e.item = i.id) :
    FreshAvail [] (orderingsOf cs inp)
      ((inp.items.filter (·.open_)).map Item.id).length := by
  refine ⟨(inp.items.filter (·.open_)).map Item.id, hopen, Nat.le_refl _, ?_⟩
  intro x hx
  refine ⟨fun h => absurd h (List.not_mem_nil), ?_⟩
  obtain ⟨it, hit, rfl⟩ := List.mem_map.mp hx
  have hop : it.open_ = true := List.mem_filter.mp hit |>.2
  obtain ⟨c, hc, e, he, hitem⟩ := hcover it (List.mem_filter.mp hit |>.1) hop
  refine ⟨c.who, (c.order inp).map (·.item), ?_, ?_⟩
  · refine List.mem_map.mpr ⟨c, hc, ?_⟩
    rfl
  · exact List.mem_map.mpr ⟨e, he, hitem⟩

/-! ### The primary theorem -/

/-- PRIMARY (Joe, 2026-11-14): "Giraffe always gives an up to date ranking of
at least the top ten missions by priority order that is recognisable to the
operator"; recognisable = explainable.

The hypotheses it needs about the characters (each says why it is here):

* `hs` — the state is one the loop produced: what is shown was built by
  `rebuild` from the current inputs. Without it a hand-built state showing
  anything fails part (a); the witness under `quiescent_shows_nothing_new`
  is one.
* `hopen` — the dedup invariant (2026-11-12/13: one substrate entity per
  document) restricted to the open items; `erase`-based availability
  counting needs it (a duplicated id would let one document count twice).
* `hcover` — each character set covers every open item: some character's
  ordering contains it. This is what makes "at least ten when ten are open"
  true; a party that skips an open item shows fewer.
* `hvoc` — every term of every character's reason comes from the operator's
  vocabulary (the item's accepted benefit lines, the operator's
  introductions/word rules, or a name already in the vocabulary). This is
  the whole content of "recognisable".

Priority order is rank in the party list, made numeric: the first party
entry has priority `budgetTen`, each later one a step less (so priorities
strictly descend along the shown list). -/
theorem primary_up_to_date_top_ten (cs : List Character) (v : Vocabulary) (s : State)
    (hs : s.shown = rebuild cs s.inputs)
    (hopen : ((s.inputs.items.filter (·.open_)).map Item.id).Nodup)
    (hcover : ∀ i ∈ s.inputs.items, i.open_ = true →
      ∃ c ∈ cs, ∃ e ∈ c.order s.inputs, e.item = i.id)
    (hvoc : ∀ c ∈ cs, ∀ e ∈ c.order s.inputs, ∀ t ∈ e.reason.terms, t ∈ v.terms) :
    -- current
    s.shown = rebuild cs s.inputs
    -- at least ten when ten are open
    ∧ ((s.inputs.items.filter (·.open_)).length ≥ 10 → s.shown.length ≥ 10)
    -- in priority order
    ∧ (∀ i j (hij : i < j) (h₁ : j < s.shown.length),
         (s.shown.get ⟨i, Nat.lt_trans hij h₁⟩).priority ≥ (s.shown.get ⟨j, h₁⟩).priority)
    -- explainable
    ∧ (∀ e ∈ s.shown, e.reason.explainable v) := by
  refine ⟨hs, ?_, ?_, ?_⟩
  · intro hlen
    have hfresh := open_fresh cs s.inputs hopen hcover
    have hfl : ((s.inputs.items.filter (·.open_)).map Item.id).length
        = (s.inputs.items.filter (·.open_)).length := by simp
    rw [hfl] at hfresh
    have hfresh10 : FreshAvail [] (orderingsOf cs s.inputs) budgetTen :=
      hfresh.mono hlen
    have hvote := partyVote_length budgetTen [] (orderingsOf cs s.inputs) hfresh10
    have hl : s.shown.length =
        (Retro.partyVote budgetTen [] (orderingsOf cs s.inputs)).length := by
      rw [hs, rebuild, entriesFrom_length]
    rw [hl, hvote]
    exact Nat.le_refl budgetTen
  · intro i j hij h₁
    have hkey : ∀ k (hk : k < s.shown.length),
        (s.shown.get ⟨k, hk⟩).priority = budgetTen - k := by
      intro k hk
      have hl : s.shown.length =
          (Retro.partyVote budgetTen [] (orderingsOf cs s.inputs)).length := by
        rw [hs, rebuild, entriesFrom_length]
      have hk2 : k < (entriesFrom budgetTen (reasonFor cs s.inputs)
          (Retro.partyVote budgetTen [] (orderingsOf cs s.inputs))).length := by
        rw [entriesFrom_length]
        rw [hl] at hk
        exact hk
      have heq : s.shown = entriesFrom budgetTen (reasonFor cs s.inputs)
          (Retro.partyVote budgetTen [] (orderingsOf cs s.inputs)) := by
        rw [hs, rebuild]
      show (s.shown[k]'hk).priority = _
      rw [List.getElem_of_eq heq hk]
      exact entriesFrom_priority _ _ budgetTen k hk2
    rw [hkey i (Nat.lt_trans hij h₁), hkey j h₁]
    omega
  · intro e he
    rw [hs, rebuild] at he
    obtain ⟨a, i, hreason⟩ := entriesFrom_reason (reasonFor cs s.inputs)
      (Retro.partyVote budgetTen [] (orderingsOf cs s.inputs)) budgetTen e he
    intro t ht
    obtain ⟨c, hc, e', he', hitem, hterms⟩ := reasonFor_terms cs s.inputs a i t (by
      rw [← hreason]; exact ht)
    exact hvoc c hc e' he' t hterms

/-! ### The Giraffe firing rule, as a corollary -/

/-- An item is stale when no current reading of it is on the table (a
`docChange` withdraws the reading; a `readingLanded` restores one). -/
def staleItem (inp : Inputs) (i : EntityId) : Bool :=
    !inp.readings.any (fun p => p.1 == i)

/-- Giraffe fires a fresh reading at exactly the items that are stale AND
entered the shown top ten (this IS the firing rule; the corollary below
supplies its cause and its exactness). -/
def needsFreshReading (s : State) (x : EntityId) : Prop :=
  ∃ e ∈ s.shown, e.item = x ∧ staleItem s.inputs x = true

theorem filter_any_ne : ∀ (l : List (EntityId × Nat)) (x : EntityId),
    ((l.filter fun p => p.1 != x).any fun p => p.1 == x) = false := by
  intro l
  induction l with
  | nil => intro _; rfl
  | cons p as ih =>
    intro x
    by_cases hp : (p.1 != x) = true
    · have hx : (p.1 == x) = false := by
        have hne : p.1 ≠ x := by simpa using hp
        simp [hne]
      simp [List.filter_cons, hp, hx, ih x]
    · simp [List.filter_cons, hp, ih x]

theorem filter_any_keep : ∀ (l : List (EntityId × Nat)) (i x : EntityId), x ≠ i →
    ((l.filter fun p => p.1 != i).any fun p => p.1 == x)
      = (l.any fun p => p.1 == x) := by
  intro l
  induction l with
  | nil => intro _ _ _; rfl
  | cons p as ih =>
    intro i x hne
    by_cases hp : (p.1 != i) = true
    · simp [List.filter_cons, hp, ih i x hne]
    · have hxi : p.1 = i := by simpa using hp
      have hb : (p.1 == x) = false := by
        have : p.1 ≠ x := by rw [hxi]; exact hne.symm
        simp [this]
      simp [List.filter_cons, hp, hb, ih i x hne]

/-- A document change makes exactly that item stale: its reading is
withdrawn, every other item's staleness is untouched. -/
theorem docChange_stales (inp : Inputs) (i : EntityId) (v : Nat) :
    staleItem (applyEvent inp (.docChange i v)) i = true
    ∧ ∀ x ≠ i, staleItem (applyEvent inp (.docChange i v)) x = staleItem inp x := by
  constructor
  · unfold staleItem applyEvent
    rw [filter_any_ne]
    rfl
  · intro x hne
    unfold staleItem applyEvent
    rw [filter_any_keep inp.readings i x hne]

/-- A landed reading un-stales exactly the item it read. -/
theorem readingLanded_unstales (inp : Inputs) (i : EntityId) (v : Nat) :
    staleItem (applyEvent inp (.readingLanded i v)) i = false := by
  unfold staleItem applyEvent
  simp

/-- MANA, the typed hole: no event produces it. `applyEvent` threads the old
value through untouched (every branch either returns `inp` or a `{inp with
…}` copy); nothing writes `mana`. -/
theorem applyEvent_mana (inp : Inputs) (e : Event) :
    (applyEvent inp e).mana = inp.mana := by
  cases e <;>
    (try rfl) <;>
    (first
      | rfl
      | (cases ‹TurnEvent› <;> rfl))

/-- The firing rule, as a corollary of the primary design: what needs a
fresh reading after a document change to `i` is exactly the stale items that
entered the newly shown top ten (Joe: "the Giraffe firing rule will probably
become a corollary"). -/
theorem giraffe_firing_rule (cs : List Character) (s : State) (i : EntityId) (v : Nat)
    (x : EntityId) :
    needsFreshReading (step cs s (.docChange i v)) x ↔
      (∃ e ∈ (step cs s (.docChange i v)).shown, e.item = x
        ∧ (x = i ∨ staleItem s.inputs x = true)) := by
  constructor
  · intro ⟨e, heshown, hitem, hstale⟩
    refine ⟨e, heshown, hitem, ?_⟩
    by_cases hx : x = i
    · exact Or.inl hx
    · exact Or.inr (by
        simp only [step] at hstale
        have hkeep := (docChange_stales s.inputs i v).2 x hx
        rw [hkeep] at hstale
        exact hstale)
  · intro ⟨e, heshown, hitem, hstale⟩
    refine ⟨e, heshown, hitem, ?_⟩
    rcases hstale with hx | hstale
    · rw [hx]
      simp only [step]
      exact (docChange_stales s.inputs i v).1
    · by_cases hxi : x = i
      · rw [hxi]
        simp only [step]
        exact (docChange_stales s.inputs i v).1
      · simp only [step]
        rw [(docChange_stales s.inputs i v).2 x hxi]
        exact hstale

end Circus
