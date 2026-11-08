import ArxanaCore.Store
/-!
# Projections are attributable; layout is local; meaning propagates (DERIVE-core §4, F5, F6)

A projection rule renders some claims into fragments. Each fragment names
the claim it came from. Layout (coordinates) is presentation state.
-/
namespace Arxana

structure Fragment where
  source : Key
  text : String
  pos : Nat × Nat
deriving DecidableEq, Repr

def renderOne (render : Clusion → Option String) (layout : Key → Nat × Nat) :
    Record → Option Fragment
  | .clusion c => (render c).map fun t => ⟨c.key, t, layout c.key⟩
  | _ => none

def project (render : Clusion → Option String) (layout : Key → Nat × Nat)
    (s : List Record) : List Fragment :=
  s.filterMap (renderOne render layout)

/-- Every fragment leads back to a claim in the store: no orphan pictures. -/
theorem project_attributable (render : Clusion → Option String) (layout : Key → Nat × Nat)
    (s : List Record) (f : Fragment) (hf : f ∈ project render layout s) :
    f.source ∈ claims s := by
  simp only [project, List.mem_filterMap] at hf
  obtain ⟨r, hr, hrf⟩ := hf
  cases r with
  | clusion c =>
    simp only [renderOne, Option.map_eq_some_iff] at hrf
    obtain ⟨t, _, rfl⟩ := hrf
    exact List.mem_filterMap.mpr ⟨.clusion c, hr, rfl⟩
  | attest _ => simp [renderOne] at hrf
  | assess _ => simp [renderOne] at hrf
  | supersede _ _ => simp [renderOne] at hrf

/-- Moving things on the page changes no record identity in the view. -/
theorem layout_is_local (render : Clusion → Option String) (l₁ l₂ : Key → Nat × Nat)
    (s : List Record) :
    (project render l₁ s).map (·.source) = (project render l₂ s).map (·.source) := by
  induction s with
  | nil => rfl
  | cons r rs ih =>
    cases r with
    | clusion c =>
      cases hc : render c <;> simp [project, renderOne, hc] at ih ⊢ <;> exact ih
    | attest _ => simpa [project, renderOne] using ih
    | assess _ => simpa [project, renderOne] using ih
    | supersede _ _ => simpa [project, renderOne] using ih

/-- A new claim appears in every projection whose rule renders it. -/
theorem meaning_propagates (render : Clusion → Option String) (layout : Key → Nat × Nat)
    (s : List Record) (c : Clusion) (t : String) (h : render c = some t) :
    ⟨c.key, t, layout c.key⟩ ∈ project render layout (push s (.clusion c)) := by
  simp [project, push, renderOne, h]

/-- Evidence and assessments do not change what a view shows. -/
theorem attest_keeps_projection (render : Clusion → Option String) (layout : Key → Nat × Nat)
    (s : List Record) (a : Attestation) :
    project render layout (push s (.attest a)) = project render layout s := by
  simp [project, push, renderOne]

end Arxana
