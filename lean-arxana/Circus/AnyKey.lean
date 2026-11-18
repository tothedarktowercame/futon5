import Circus.Basic

/-! # ※ reachability

Spaces displayed by the Emacs prompt are not keys. A complete code exits
immediately unless another code properly extends it; RET resolves exactly
that ambiguous complete-code state. -/
namespace Circus.AnyKey

variable {Target Key : Type} [DecidableEq Target] [DecidableEq Key]

structure Table (Target Key : Type) [DecidableEq Target] where
  targets : List Target
  code : Target → List Key
  decode : List Key → Option Target
  nodup : targets.Nodup
  injective : ∀ {a b}, a ∈ targets → b ∈ targets → code a = code b → a = b
  decode_code : ∀ t ∈ targets, decode (code t) = some t

def properPrefix (a b : List Key) : Bool := decide (a <+: b ∧ a ≠ b)

def Table.hasLonger (q : Table Target Key) (s : List Key) : Bool :=
  q.targets.any fun t => properPrefix s (q.code t)

def Table.costNoRet (q : Table Target Key) (t : Target) : Nat := (q.code t).length

def Table.costRet (q : Table Target Key) (t : Target) : Nat :=
  (q.code t).length + if q.hasLonger (q.code t) then 1 else 0

inductive Input (Key : Type) | key (k : Key) | ret
  deriving DecidableEq, Repr

structure Prompt (Target Key : Type) where
  typed : List Key := []
  result : Option Target := none
  deriving DecidableEq, Repr

/-- The event-level transition implemented by `mojira--maybe-exit`. -/
def Table.step (q : Table Target Key) (p : Prompt Target Key) (i : Input Key) :
    Prompt Target Key :=
  if p.result.isSome then p else
    match i with
    | .key k =>
        let s := p.typed ++ [k]
        match q.decode s with
        | some t => if q.hasLonger s then ⟨s, none⟩ else ⟨s, some t⟩
        | none => ⟨s, none⟩
    | .ret => ⟨p.typed, q.decode p.typed⟩

def keysOf : List (Input Key) → List Key
  | [] => []
  | .key k :: xs => k :: keysOf xs
  | .ret :: xs => keysOf xs

@[simp] theorem keysOf_map_key (xs : List Key) :
    keysOf (xs.map Input.key) = xs := by
  induction xs <;> simp [keysOf, *]

@[simp] theorem keysOf_map_key_ret (xs : List Key) :
    keysOf (xs.map Input.key ++ [Input.ret]) = xs := by
  induction xs <;> simp [keysOf, *]

def sawRet (xs : List (Input Key)) : Bool := xs.any (· == .ret)

/-- Batch observation of the same prompt rule, used by the certificate. -/
def Table.run (q : Table Target Key) (xs : List (Input Key)) : Prompt Target Key :=
  let s := keysOf xs
  let r := q.decode s
  if q.hasLonger s then
    if sawRet xs then ⟨s, r⟩ else ⟨s, none⟩
  else ⟨s, r⟩

def Table.keysRet (q : Table Target Key) (t : Target) : List (Input Key) :=
  (q.code t).map .key ++ if q.hasLonger (q.code t) then [.ret] else []

def Table.keysNoRet (q : Table Target Key) (t : Target) : List (Input Key) :=
  (q.code t).map .key

theorem reaches (q : Table Target Key) {t : Target} (ht : t ∈ q.targets) :
    (q.run (q.keysRet t)).result = some t ∧
      (q.keysRet t).length = q.costRet t := by
  by_cases h : q.hasLonger (q.code t) = true
  · simp [Table.run, Table.keysRet, Table.costRet, keysOf, sawRet, h,
      q.decode_code t ht]
  · have hf : q.hasLonger (q.code t) = false := by
      cases hh : q.hasLonger (q.code t) <;> simp_all
    simp [Table.run, Table.keysRet, Table.costRet, keysOf, sawRet, hf,
      q.decode_code t ht]

def PrefixFree (q : Table Target Key) : Prop :=
  ∀ a ∈ q.targets, ∀ b ∈ q.targets, a ≠ b → ¬q.code a <+: q.code b

theorem prefixFree_cost_eq (q : Table Target Key) (h : PrefixFree q) {t : Target}
    (ht : t ∈ q.targets) : q.costRet t = q.costNoRet t := by
  have hl : q.hasLonger (q.code t) = false := by
    simp only [Table.hasLonger, List.any_eq_false]
    intro u hu
    intro hp
    have hp' : q.code t <+: q.code u ∧ q.code t ≠ q.code u := of_decide_eq_true hp
    exact h t ht u hu (fun e => hp'.2 (congrArg q.code e)) hp'.1
  simp [Table.costRet, Table.costNoRet, hl]

theorem bound (q : Table Target Key) (hfree : PrefixFree q) (n : Nat)
    (hlen : ∀ t ∈ q.targets, (q.code t).length ≤ n) :
    ∀ t ∈ q.targets,
      (q.keysRet t).length ≤ n ∧ (q.run (q.keysRet t)).result = some t := by
  intro t ht
  have hc : q.costRet t ≤ n := by
    rw [prefixFree_cost_eq q hfree ht]
    exact hlen t ht
  exact ⟨(reaches q ht).2 ▸ hc, (reaches q ht).1⟩

theorem boundRet (q : Table Target Key) (n : Nat)
    (hcost : ∀ t ∈ q.targets, q.costRet t ≤ n) :
    ∀ t ∈ q.targets,
      (q.keysRet t).length ≤ n ∧ (q.run (q.keysRet t)).result = some t := by
  intro t ht
  exact ⟨(reaches q ht).2 ▸ hcost t ht, (reaches q ht).1⟩

theorem anyKeyFive (q : Table Target Key)
    (hcost : ∀ t ∈ q.targets, q.costRet t ≤ 5) :
    ∀ t ∈ q.targets,
      (q.keysRet t).length ≤ 5 ∧ (q.run (q.keysRet t)).result = some t :=
  boundRet q 5 hcost

/-- An extension keeps every old target and its old code. -/
def Extension (old new : Table Target Key) : Prop :=
  ∀ t ∈ old.targets, t ∈ new.targets ∧ new.code t = old.code t

theorem stableNoRet {old new : Table Target Key} (h : Extension old new)
    {t : Target} (ht : t ∈ old.targets) :
    new.costNoRet t = old.costNoRet t := by
  simp [Table.costNoRet, (h t ht).2]

def NewCodeExtends (old new : Table Target Key) (t : Target) : Prop :=
  ∃ u ∈ new.targets, u ∉ old.targets ∧
    properPrefix (new.code t) (new.code u) = true

private theorem longer_extension {old new : Table Target Key} (h : Extension old new)
    {t : Target} (ht : t ∈ old.targets) :
    new.hasLonger (new.code t) = true ↔
      old.hasLonger (old.code t) = true ∨ NewCodeExtends old new t := by
  constructor
  · intro hn
    obtain ⟨u, hu, hp⟩ := List.any_eq_true.mp hn
    by_cases ho : u ∈ old.targets
    · left
      apply List.any_eq_true.mpr
      exact ⟨u, ho, by simpa [(h t ht).2, (h u ho).2] using hp⟩
    · right
      exact ⟨u, hu, ho, hp⟩
  · intro hor
    apply List.any_eq_true.mpr
    rcases hor with ho | hn
    · obtain ⟨u, hu, hp⟩ := List.any_eq_true.mp ho
      exact ⟨u, (h u hu).1, by simpa [(h t ht).2, (h u hu).2] using hp⟩
    · obtain ⟨u, hu, _, hp⟩ := hn
      exact ⟨u, hu, hp⟩

theorem costRet_rises_iff {old new : Table Target Key} (h : Extension old new)
    {t : Target} (ht : t ∈ old.targets) :
    old.costRet t < new.costRet t ↔
      old.hasLonger (old.code t) = false ∧ NewCodeExtends old new t := by
  have hc := (h t ht).2
  have hl := longer_extension h ht
  unfold Table.costRet
  cases ho : old.hasLonger (old.code t) <;>
    cases hn : new.hasLonger (new.code t) <;>
    simp [ho, hn] at hl
  all_goals simp [ho, hn, hc, hl]

/-! ## Teeth -/
namespace Teeth

inductive PFTarget | a | b | c deriving DecidableEq

def pfCode : PFTarget → List Char
  | .a => ['a'] | .b => ['b', 'b'] | .c => ['c', 'd', 'e', 'f', 'g']

def pfDecode : List Char → Option PFTarget
  | ['a'] => some .a
  | ['b', 'b'] => some .b
  | ['c', 'd', 'e', 'f', 'g'] => some .c
  | _ => none

def prefixFreeTable : Table PFTarget Char :=
  { targets := [.a, .b, .c], code := pfCode, decode := pfDecode
    nodup := by decide
    injective := by intro a b _ _ h; cases a <;> cases b <;> simp_all [pfCode]
    decode_code := by intro t _; cases t <;> rfl }

example : PrefixFree prefixFreeTable := by
  intro x _ y _ hxy hp
  cases x <;> cases y
  · exact hxy rfl
  · exact (by decide : ¬(['a'] <+: ['b', 'b'])) hp
  · exact (by decide : ¬(['a'] <+: ['c', 'd', 'e', 'f', 'g'])) hp
  · exact (by decide : ¬(['b', 'b'] <+: ['a'])) hp
  · exact hxy rfl
  · exact (by decide : ¬(['b', 'b'] <+: ['c', 'd', 'e', 'f', 'g'])) hp
  · exact (by decide : ¬(['c', 'd', 'e', 'f', 'g'] <+: ['a'])) hp
  · exact (by decide : ¬(['c', 'd', 'e', 'f', 'g'] <+: ['b', 'b'])) hp
  · exact hxy rfl
example : ∀ t ∈ prefixFreeTable.targets, prefixFreeTable.costRet t ≤ 5 := by decide

inductive AmbTarget | short | long | five deriving DecidableEq

def ambCode : AmbTarget → List Char
  | .short => ['c', '1'] | .long => ['c', '1', '3'] | .five => ['z']

def ambDecode : List Char → Option AmbTarget
  | ['c', '1'] => some .short
  | ['c', '1', '3'] => some .long
  | ['z'] => some .five
  | _ => none

def ambiguousTable : Table AmbTarget Char :=
  { targets := [.short, .long, .five], code := ambCode, decode := ambDecode
    nodup := by decide
    injective := by intro a b _ _ h; cases a <;> cases b <;> simp_all [ambCode]
    decode_code := by intro t _; cases t <;> rfl }

def sixCode : AmbTarget → List Char
  | .short => ['a', 'b', 'c', 'd', 'e']
  | .long => ['a', 'b', 'c', 'd', 'e', 'f']
  | .five => ['z']

def sixDecode : List Char → Option AmbTarget
  | ['a', 'b', 'c', 'd', 'e'] => some .short
  | ['a', 'b', 'c', 'd', 'e', 'f'] => some .long
  | ['z'] => some .five
  | _ => none

def sixTable : Table AmbTarget Char :=
  { targets := [.short, .long, .five], code := sixCode, decode := sixDecode
    nodup := by decide
    injective := by intro a b _ _ h; cases a <;> cases b <;> simp_all [sixCode]
    decode_code := by intro t _; cases t <;> rfl }

example : ambiguousTable.costRet .short = 3 := by decide
example : ambiguousTable.costNoRet .short = 2 := by decide
example : (ambiguousTable.run (ambiguousTable.keysRet .short)).result = some .short := by decide
example : sixTable.costNoRet .short = 5 := by decide
example : sixTable.costRet .short = 6 := by decide

end Teeth

#print axioms reaches
#print axioms prefixFree_cost_eq
#print axioms bound
#print axioms boundRet
#print axioms anyKeyFive
#print axioms stableNoRet
#print axioms costRet_rises_iff

end Circus.AnyKey
