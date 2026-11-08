/-!
# Bounded recursive reads (DERIVE-core §17, C3)

Reads suppress visited ids and take caller-visible `maxDepth` and
`maxNodes`; a read that stops early says so with a typed reason.
-/
namespace Arxana

inductive Truncation | maxDepth | maxNodes
deriving DecidableEq, Repr

inductive ReadResult
  | complete (nodes : List Nat)
  | truncated (nodes : List Nat) (reason : Truncation)
deriving DecidableEq, Repr

def ReadResult.nodes : ReadResult → List Nat
  | .complete n => n
  | .truncated n _ => n

/-- Add unseen ids from `xs` to `vis` until `maxNodes` is reached.
Returns the new visited list, the ids added, and whether it hit the cap. -/
def addBounded (maxNodes : Nat) : List Nat → List Nat → List Nat × List Nat × Bool
  | vis, [] => (vis, [], false)
  | vis, x :: xs =>
    if x ∈ vis then addBounded maxNodes vis xs
    else if vis.length < maxNodes then
      let r := addBounded maxNodes (vis ++ [x]) xs
      (r.1, x :: r.2.1, r.2.2)
    else (vis, [], true)

theorem addBounded_nodup (m : Nat) (xs vis : List Nat) (h : vis.Nodup) :
    (addBounded m vis xs).1.Nodup := by
  induction xs generalizing vis with
  | nil => simpa [addBounded] using h
  | cons x xs ih =>
    unfold addBounded
    by_cases hx : x ∈ vis
    · simp [hx]; exact ih vis h
    · by_cases hl : vis.length < m
      · simp [hx, hl]
        apply ih
        exact List.nodup_append.mpr ⟨h, by simp, by simp; intro a ha he; exact hx (he ▸ ha)⟩
      · simp [hx, hl]; exact h

theorem addBounded_bound (m : Nat) (xs vis : List Nat) (h : vis.length ≤ m) :
    (addBounded m vis xs).1.length ≤ m := by
  induction xs generalizing vis with
  | nil => simpa [addBounded] using h
  | cons x xs ih =>
    unfold addBounded
    by_cases hx : x ∈ vis
    · simp [hx]; exact ih vis h
    · by_cases hl : vis.length < m
      · simp [hx, hl]; apply ih; simp; omega
      · simp [hx, hl]; exact h

/-- Breadth-first walk with depth fuel. Structural recursion on depth: always terminates. -/
def walk (nbrs : Nat → List Nat) (m : Nat) : Nat → List Nat → List Nat → List Nat × Option Truncation
  | 0, vis, [] => (vis, none)
  | 0, vis, _ :: _ => (vis, some .maxDepth)
  | _ + 1, vis, [] => (vis, none)
  | d + 1, vis, f@(_ :: _) =>
    let r := addBounded m vis (f.flatMap nbrs)
    if r.2.2 then (r.1, some .maxNodes) else walk nbrs m d r.1 r.2.1

theorem walk_nodup (nbrs : Nat → List Nat) (m d : Nat) (vis f : List Nat) (h : vis.Nodup) :
    (walk nbrs m d vis f).1.Nodup := by
  induction d generalizing vis f with
  | zero => cases f <;> simpa [walk] using h
  | succ d ih =>
    cases f with
    | nil => simpa [walk] using h
    | cons y ys =>
      simp only [walk]
      split
      · exact addBounded_nodup _ _ _ h
      · exact ih _ _ (addBounded_nodup _ _ _ h)

theorem walk_bound (nbrs : Nat → List Nat) (m d : Nat) (vis f : List Nat) (h : vis.length ≤ m) :
    (walk nbrs m d vis f).1.length ≤ m := by
  induction d generalizing vis f with
  | zero => cases f <;> simpa [walk] using h
  | succ d ih =>
    cases f with
    | nil => simpa [walk] using h
    | cons y ys =>
      simp only [walk]
      split
      · exact addBounded_bound _ _ _ h
      · exact ih _ _ (addBounded_bound _ _ _ h)

/-- The read port: start id, `maxDepth`, `maxNodes`. -/
def read (nbrs : Nat → List Nat) (maxDepth maxNodes start : Nat) : ReadResult :=
  if maxNodes = 0 then .truncated [] .maxNodes
  else match walk nbrs maxNodes maxDepth [start] [start] with
    | (vis, none) => .complete vis
    | (vis, some r) => .truncated vis r

/-- A read never returns more than `maxNodes` ids. -/
theorem read_bounded (nbrs : Nat → List Nat) (d m s : Nat) :
    (read nbrs d m s).nodes.length ≤ m := by
  unfold read
  split
  · simp [ReadResult.nodes]
  · have hb := walk_bound nbrs m d [s] [s] (by simp; omega)
    split <;> simp_all [ReadResult.nodes]

/-- A read never returns an id twice, even on a cyclic graph. -/
theorem read_nodup (nbrs : Nat → List Nat) (d m s : Nat) :
    (read nbrs d m s).nodes.Nodup := by
  unfold read
  split
  · simp [ReadResult.nodes]
  · have hn := walk_nodup nbrs m d [s] [s] (by simp)
    split <;> simp_all [ReadResult.nodes]

/-- Example: a 3-cycle read with generous bounds completes with each node once. -/
example : read (fun n => [(n + 1) % 3]) 10 10 0 = .complete [0, 1, 2] := by decide

/-- Example: the same cycle with `maxNodes = 2` says it was cut short. -/
example : (match read (fun n => [(n + 1) % 3]) 10 2 0 with
    | .truncated _ .maxNodes => true | _ => false) = true := by decide

end Arxana
