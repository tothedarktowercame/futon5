import Circus.Monkey
import Circus.Supersession

/-!
# VERIFY: one complete chain to Joe's Mojira view

This composes the existing models, not a claim of live conformance. The named
reader asks "what should Joe look at next, and why". The War Machine is a second
promised reader; Joe deferred it ("let's save that integration for later").
This file does NOT discharge that integration. WMParty's Seam B is recommended.

Decoder agreement with the published payload schema is a conformance-script
obligation, like giraffe_party_conformance.py, NOT a Lean theorem. One mention
record per item is required by the one-endpoint-per-role model. The live source
must also allocate distinct keys for distinct records: LateWrites keys only have
turn/animal/slot today. Same-key simultaneous divergent writes become visible
revisionConflict, never multiple silently selected mentions. Key granularity is
therefore an outstanding producer conformance question, not invented here.
-/
namespace Circus.LiveChain
open Arxana LateWrites

structure Decoded where
  kind : RecordKind
  endpoints : List (EndpointRole × GNode)
  deriving DecidableEq, Repr
structure Bridge where
  decode : Slot → String → Option Decoded
  recordId : LateWrites.Key → Nat
/-- Named, independently ingested scope inventory. Record endpoints can name
accepted-reading records in this inventory; other record references are unknown
until the corpus supplies them. The bridge never manufactures inventory nodes. -/
structure Corpus where
  generation : Nat
  items : List Item
  entities : List EntityId
  benefitLines : List Nat
  acceptedReadings : List AcceptedReading
  deriving DecidableEq, Repr
inductive BridgeAbsence
  | malformedPayload (key : LateWrites.Key)
  | unknownEndpoint (key : LateWrites.Key)
  | invalidEndpoints (key : LateWrites.Key)
  deriving DecidableEq, Repr

def knownNode (c : Corpus) : GNode → Bool
  | .item i => c.items.contains i
  | .entity i => c.entities.contains i
  | .benefitLine i => c.benefitLines.contains i
  | .record r => c.acceptedReadings.any (·.record == r)
def fits : EndpointRole → GNode → Bool
  | .item, .item _ | .entity, .entity _ | .benefitLine, .benefitLine _ => true
  | .reading, .record r => r.kind == .giraffeReading
  | .subject, .record _ => true
  | _, _ => false
theorem fits_iff (role : EndpointRole) (node : GNode) :
    fits role node = true ↔ EndpointFits role node := by
  cases role <;> cases node <;> simp [fits, EndpointFits]
def validEndpoints (d : Decoded) : Bool :=
  decide (d.endpoints.map Prod.fst |>.Nodup) && d.endpoints.all (fun ep => fits ep.1 ep.2)
def KindAgreement (b : Bridge) : Prop :=
  ∀ k s, (b.decode (.known k) s).map (·.kind) ∈ [none, some k]
def IdentityInjective (b : Bridge) : Prop :=
  ∀ a c, b.recordId a = b.recordId c → a = c

def gccRecord (b : Bridge) (r : SourceRecord) (d : Decoded) : GCCRecord :=
  ⟨b.recordId r.key, d.kind, some r.key.turn⟩
/-- Deduplicate delivery, then consult the SAME rebuildGraph usable predicate.
Only usable success heads enter; all other source states remain in that graph. -/
def usableHeads (basis : ComparisonBasis) (ds : List Delivery) : List SourceRecord :=
  (semanticRecords ds).eraseDups.filter fun r => match r.payload with
    | .success s => (rebuildGraph basis ds).usable r.key r.revision s
    | _ => false

def inspect (b : Bridge) (c : Corpus) (r : SourceRecord) :
    Option (Except BridgeAbsence Decoded) :=
  match r.key.slot, r.payload with
  | .dogBuried, _ => none
  | _, .success s => match b.decode r.key.slot s with
    | none => some (.error (.malformedPayload r.key))
    | some d => if !validEndpoints d then some (.error (.invalidEndpoints r.key))
      else if !(d.endpoints.all (fun ep => knownNode c ep.2)) then
        some (.error (.unknownEndpoint r.key))
      else some (.ok d)
  | _, _ => none

def accepted (b : Bridge) (c : Corpus) (basis : ComparisonBasis) (ds : List Delivery) :
    List (SourceRecord × Decoded) :=
  (usableHeads basis ds).filterMap fun r => match inspect b c r with
    | some (.ok d) => some (r, d)
    | _ => none

def bridgeGCC (b : Bridge) (c : Corpus) (basis : ComparisonBasis) (ds : List Delivery) :
    GCC × List BridgeAbsence :=
  let rows := accepted b c basis ds
  let records := rows.map (fun p => gccRecord b p.1 p.2)
  let graph : GCC := ⟨c.items, records, c.entities, c.benefitLines,
    fun r role => (rows.find? (fun p => gccRecord b p.1 p.2 == r)).bind
      (fun p => (p.2.endpoints.find? (·.1 == role)).map Prod.snd),
    c.acceptedReadings⟩
  (graph, (usableHeads basis ds).filterMap fun r => match inspect b c r with
    | some (.error why) => some why
    | _ => none)

/-- Exact origin and endpoint preservation, not merely a matching kind label.
The accepted-reading inventory is retained, but needs only uses its records
when they are present in GCC.records (Basic.GCC.needs). -/
def BridgeFaithful (b : Bridge) (c : Corpus) (basis : ComparisonBasis)
    (ds : List Delivery) (out : GCC × List BridgeAbsence) : Prop :=
  out.1.items = c.items ∧ out.1.entities = c.entities ∧ out.1.benefitLines = c.benefitLines ∧
  (∀ record ∈ out.1.records, ∃ r d,
    r ∈ usableHeads basis ds ∧ inspect b c r = some (.ok d) ∧ gccRecord b r d = record ∧
    (∀ ep ∈ d.endpoints, out.1.endpoint record ep.1 = some ep.2) ∧
    (∀ r' d', r' ∈ usableHeads basis ds → inspect b c r' = some (.ok d') →
      gccRecord b r' d' = record → r' = r ∧ d' = d)) ∧
  (∀ r ∈ usableHeads basis ds, r.key.slot ≠ .dogBuried →
    (∃ d, inspect b c r = some (.ok d) ∧ gccRecord b r d ∈ out.1.records) ∨
    (∃ why, inspect b c r = some (.error why) ∧ out.2.count why = 1)) ∧
  (∀ why ∈ out.2, ∃ r ∈ usableHeads basis ds, inspect b c r = some (.error why))

private theorem head_facts (basis : ComparisonBasis) (ds : List Delivery) (r : SourceRecord)
    (hr : r ∈ usableHeads basis ds) :
    ∃ value, r.payload = .success value ∧ maximal (semanticRecords ds) r = true ∧
      conflict (semanticRecords ds) r.key = false := by
  have hf := (List.mem_filter.mp hr).2
  cases hp : r.payload <;> simp only [hp] at hf <;> try contradiction
  rename_i value
  refine ⟨value, rfl, ?_⟩
  have eq : (⟨r.key, r.revision, .success value⟩ : SourceRecord) = r := by
    cases r; simp_all
  simpa only [rebuildGraph, eq, Bool.and_eq_true, Bool.not_eq_true'] using hf

private theorem head_key_unique (basis : ComparisonBasis) (ds : List Delivery)
    (r q : SourceRecord) (hr : r ∈ usableHeads basis ds) (hq : q ∈ usableHeads basis ds)
    (hk : r.key = q.key) : r = q := by
  obtain ⟨rv, rp, rm, rc⟩ := head_facts basis ds r hr
  obtain ⟨qv, qp, qm, _⟩ := head_facts basis ds q hq
  have rm' := rm
  have qm' := qm
  simp only [maximal, Bool.and_eq_true, List.contains_iff_mem, List.all_eq_true] at rm' qm'
  have le₁ := rm'.2 q qm'.1
  have le₂ := qm'.2 r rm'.1
  simp only [hk, bne_self_eq_false, Bool.false_or, decide_eq_true_eq] at le₁ le₂
  have rev : r.revision = q.revision := Nat.le_antisymm le₂ le₁
  have payload : r.payload = q.payload := by
    by_cases he : r.payload = q.payload
    · exact he
    · have bad : conflict (semanticRecords ds) r.key = true := by
        apply List.any_eq_true.mpr
        refine ⟨r, rm'.1, ?_⟩
        simp only [beq_self_eq_true, rm, Bool.true_and]
        apply List.any_eq_true.mpr
        exact ⟨q, qm'.1, by simp [hk, qm, Ne.symm he]⟩
      rw [rc] at bad
      cases bad
  cases r; cases q; simp_all

private theorem accepted_mem (b : Bridge) (c : Corpus) (basis : ComparisonBasis)
    (ds : List Delivery) (r : SourceRecord) (d : Decoded) :
    (r, d) ∈ accepted b c basis ds ↔ r ∈ usableHeads basis ds ∧ inspect b c r = some (.ok d) := by
  unfold accepted
  simp only [List.mem_filterMap]
  constructor
  · rintro ⟨q, hq, he⟩
    cases hi : inspect b c q with
    | none => simp [hi] at he
    | some result =>
      cases result with
      | error why => simp [hi] at he
      | ok value =>
        simp only [hi, Option.some.injEq, Prod.mk.injEq] at he
        obtain ⟨rfl, rfl⟩ := he
        exact ⟨hq, hi⟩
  · rintro ⟨hr, hi⟩
    exact ⟨r, hr, by simp [hi]⟩

private theorem accepted_unique (b : Bridge) (c : Corpus) (basis : ComparisonBasis)
    (ds : List Delivery) (ids : IdentityInjective b)
    (r q : SourceRecord) (d e : Decoded)
    (hr : (r, d) ∈ accepted b c basis ds) (hq : (q, e) ∈ accepted b c basis ds)
    (he : gccRecord b r d = gccRecord b q e) : r = q ∧ d = e := by
  have rh := (accepted_mem b c basis ds r d).mp hr
  have qh := (accepted_mem b c basis ds q e).mp hq
  have hk := ids r.key q.key (congrArg GCCRecord.id he)
  have eq := head_key_unique basis ds r q rh.1 qh.1 hk
  subst q
  have de : d = e := by rw [rh.2] at qh; exact Except.ok.inj (Option.some.inj qh.2)
  exact ⟨rfl, de⟩

private theorem inspect_valid (b : Bridge) (c : Corpus) (r : SourceRecord) (d : Decoded)
    (hi : inspect b c r = some (.ok d)) : (d.endpoints.map Prod.fst).Nodup := by
  unfold inspect at hi
  split at hi <;> try (simp at hi)
  split at hi <;> try (simp at hi)
  split at hi <;> try (simp at hi)
  split at hi <;> try (simp at hi)
  subst d
  rename_i valid known
  simp only [Bool.not_eq_false] at valid
  have hv := Bool.and_eq_true_iff.mp valid
  exact of_decide_eq_true hv.1

private theorem find_unique {α : Type} (xs : List α) (p : α → Bool) (x : α)
    (mem : x ∈ xs) (yes : p x = true) (unique : ∀ y ∈ xs, p y = true → y = x) :
    xs.find? p = some x := by
  cases he : xs.find? p with
  | none => exact False.elim (List.find?_eq_none.mp he x mem yes)
  | some y =>
    have eq := unique y (List.mem_of_find?_eq_some he) (List.find?_some he)
    subst y
    rfl

private theorem role_unique (eps : List (EndpointRole × GNode))
    (nd : (eps.map Prod.fst).Nodup) (a b : EndpointRole × GNode)
    (ha : a ∈ eps) (hb : b ∈ eps) (eq : a.1 = b.1) : a = b := by
  induction eps with
  | nil => cases ha
  | cons x xs ih =>
    simp only [List.map_cons, List.nodup_cons] at nd
    simp only [List.mem_cons] at ha hb
    rcases ha with rfl | ha <;> rcases hb with rfl | hb
    · rfl
    · exact False.elim (nd.1 (List.mem_map.mpr ⟨b, hb, eq.symm⟩))
    · exact False.elim (nd.1 (List.mem_map.mpr ⟨a, ha, eq⟩))
    · exact ih nd.2 ha hb

private theorem eraseDups_nodup {α : Type} [BEq α] [LawfulBEq α] (xs : List α) : xs.eraseDups.Nodup := by
  cases xs with
  | nil => simp
  | cons x xs =>
    rw [List.eraseDups_cons]
    apply List.nodup_cons.mpr
    refine ⟨by simp, eraseDups_nodup _⟩
termination_by xs.length
decreasing_by
  have h := List.length_filter_le (fun b => !b == x) xs
  simp only [List.length_cons]
  omega

private def absence_key : BridgeAbsence → LateWrites.Key
  | .malformedPayload k | .unknownEndpoint k | .invalidEndpoints k => k

private theorem inspect_error_key (b : Bridge) (c : Corpus) (r : SourceRecord) (why : BridgeAbsence)
    (hi : inspect b c r = some (.error why)) :
    absence_key why = r.key := by
  cases why <;> simp only [absence_key] <;> unfold inspect at hi
  all_goals split at hi <;> try (simp at hi)
  all_goals split at hi <;> try (simp_all)
  all_goals split at hi <;> try (simp_all)
  all_goals split at hi <;> simp_all

private theorem count_filterMap_unique {α β : Type} [BEq β] [LawfulBEq β]
    (xs : List α) (nd : xs.Nodup) (f : α → Option β) (x : α) (y : β)
    (mem : x ∈ xs) (yes : f x = some y)
    (unique : ∀ z ∈ xs, f z = some y → z = x) : (xs.filterMap f).count y = 1 := by
  induction xs with
  | nil => cases mem
  | cons z zs ih =>
    obtain ⟨notmem, tail⟩ := List.nodup_cons.mp nd
    by_cases eq : z = x
    · subst z
      have absent : y ∉ zs.filterMap f := by
        rintro hy
        obtain ⟨q, hq, hfy⟩ := List.mem_filterMap.mp hy
        have hqx := unique q (by simp [hq]) hfy
        subst q
        exact notmem hq
      simp [yes, List.count_eq_zero.mpr absent]
    · have hx : x ∈ zs := (List.mem_cons.mp mem).resolve_left (Ne.symm eq)
      have hy := ih tail hx (by intro q hq; exact unique q (by simp [hq]))
      cases hf : f z with
      | none => simpa [hf] using hy
      | some value =>
        have ne : value ≠ y := by
          intro ev
          subst value
          exact eq (unique z (by simp) hf)
        simpa [hf, ne, Ne.symm ne] using hy

/-- VERIFY obligation 2, "meaningful GCC change": usable decoded heads have
unique provenance; malformed/unknown/invalid endpoints are visible refusals.
Injective IDs and slot-kind agreement are explicit producer contracts. -/
theorem bridge_faithful (b : Bridge) (c : Corpus) (basis : ComparisonBasis)
    (ds : List Delivery) (kind : KindAgreement b) (ids : IdentityInjective b) :
    BridgeFaithful b c basis ds (bridgeGCC b c basis ds) := by
  refine ⟨rfl, rfl, rfl, ?_, ?_, ?_⟩
  · intro record hm
    obtain ⟨⟨r, d⟩, hd, he⟩ := List.mem_map.mp hm
    have hh := (accepted_mem b c basis ds r d).mp hd
    refine ⟨r, d, hh.1, hh.2, he, ?_, ?_⟩
    · intro ep hep
      have findRow : (accepted b c basis ds).find? (fun p => gccRecord b p.1 p.2 == record) = some (r, d) := by
        apply find_unique _ _ _ hd (by simpa using he)
        intro p hp matchRecord
        have hp' : gccRecord b p.1 p.2 = record := by simpa using matchRecord
        have eq := accepted_unique b c basis ds ids p.1 r p.2 d hp hd (hp'.trans he.symm)
        exact Prod.ext eq.1 eq.2
      have findEndpoint : d.endpoints.find? (fun p => p.1 == ep.1) = some ep := by
        apply find_unique _ _ _ hep (by simp)
        intro p hp matchRole
        exact role_unique d.endpoints (inspect_valid b c r d hh.2) p ep hp hep (by simpa using matchRole)
      change ((accepted b c basis ds).find? _).bind _ = _
      rw [findRow]
      simp [findEndpoint]
    · intro q e hq hqe hrec
      exact accepted_unique b c basis ds ids q r e d
        ((accepted_mem b c basis ds q e).mpr ⟨hq, hqe⟩) hd (hrec.trans he.symm)
  · intro r hr notBuried
    have success := head_facts basis ds r hr
    have existsInspect : ∃ result, inspect b c r = some result := by
      obtain ⟨value, hp, _⟩ := success
      cases slot : r.key.slot with
      | dogBuried => exact False.elim (notBuried slot)
      | known k =>
        simp only [inspect, slot, hp]
        split
        · exact ⟨_, rfl⟩
        · split
          · exact ⟨_, rfl⟩
          · split <;> exact ⟨_, rfl⟩
    obtain ⟨result, hi⟩ := existsInspect
    cases result with
    | ok d =>
      apply Or.inl
      refine ⟨d, hi, ?_⟩
      exact List.mem_map.mpr ⟨(r,d), (accepted_mem b c basis ds r d).mpr ⟨hr,hi⟩, rfl⟩
    | error why =>
      apply Or.inr
      refine ⟨why, hi, ?_⟩
      change ((usableHeads basis ds).filterMap _).count why = 1
      refine count_filterMap_unique _ ?_ _ r why hr (by simp [hi]) ?_
      · exact (eraseDups_nodup (semanticRecords ds)).filter _
      · intro q hq same
        have iq : inspect b c q = some (.error why) := by
          cases hqi : inspect b c q with
          | none => simp [hqi] at same
          | some res =>
            cases res with
            | ok d => simp [hqi] at same
            | error why' =>
              simp only [hqi, Option.some.injEq] at same
              subst why'
              rfl
        exact head_key_unique basis ds q r hq hr
          ((inspect_error_key b c q why iq).symm.trans (inspect_error_key b c r why hi))
  · intro why hw
    obtain ⟨r, hr, he⟩ := List.mem_filterMap.mp hw
    refine ⟨r, hr, ?_⟩
    cases hi : inspect b c r with
    | none => simp [hi] at he
    | some res =>
      cases res with
      | ok d => simp [hi] at he
      | error why' =>
        simp only [hi, Option.some.injEq] at he
        subst why'
        rfl


/-- Enumerate the finite job inventory and delivered keys; query the existing
absence predicate. A job never declared or delivered cannot be detected here.
Burial is excluded from this reader's GCC scope, including its absences. -/
def jobAbsences (basis : ComparisonBasis) (ds : List Delivery) : List (LateWrites.Key × Absence) :=
  let keys := ((semanticRecords ds).map (·.key) ++ basis.expected.map (·.key)).eraseDups
  let reasons := ([Absence.pending, .deadlineExpired, .revisionConflict] ++
    (semanticRecords ds).filterMap (fun (r : SourceRecord) => match r.payload with
      | .failure s => some (Absence.failed s) | .retract s => some (Absence.retracted s) | _ => none)).eraseDups
  keys.flatMap fun key => if key.slot == .dogBuried then [] else
    reasons.filterMap fun why => if (rebuildGraph basis ds).absence key why then some (key, why) else none

inductive ChainAbsence
  | bridge (reason : BridgeAbsence)
  | job (key : LateWrites.Key) (reason : Absence)
  | focus (reason : Monkey.Failure)
  | stale (reason : Freshness.StaleReason)
  | noRankedItems
  | passInputMismatch
  deriving DecidableEq, Repr
structure Citation where
  corpusGeneration : Nat
  sources : List SourceRecord
  focusRecord : GCCRecord
  focusPin : Monkey.EvidencePin
  passId : Nat
  deriving DecidableEq, Repr
structure Mojira where
  reader : Supersession.Reader
  priorities : List Monkey.RankedRow
  citation : Option Citation
  notices : List ChainAbsence

/-- A chain run carries existing module inputs. Digest lookup comes from the
same published snapshot's content inventory; its live correctness, like decode,
is conformance work. Supersession's run key indexes the displayed pass/generation
in this abstract model; no equality of production filesystem identifiers is claimed. -/
structure ChainInput where
  bridge : Bridge
  corpus : Corpus
  basis : ComparisonBasis
  projector : Projector
  deliveries : List Delivery
  turn : TurnId
  replyLanded : Bool
  view : Freshness.ViewState
  store : Supersession.Store
  digest : GCCRecord → Option Nat
  readRecords : List GCCRecord
  focusId : Nat
  proposal : Monkey.ReaderResult
  passInputDigest : Nat
  refreshSucceeded : Bool
  cutoff : Nat

/-- Settle once, then deliver even the late records; never re-settle per write. -/
def collected (cs : List Circus.Character) (i : ChainInput) : Projector :=
  i.deliveries.foldl deliver (settle cs i.projector i.turn)
def bridged (cs : List Circus.Character) (i : ChainInput) : GCC × List BridgeAbsence :=
  bridgeGCC i.bridge i.corpus i.basis (collected cs i).delivered

def monkeyInput (cs : List Circus.Character) (i : ChainInput) : Monkey.RunInput :=
  let g := (bridged cs i).1
  ⟨⟨g, i.digest⟩, i.turn, g.records.filter (·.turn == some i.turn), i.readRecords,
    (collected cs i).settled.any (fun p => p.1 == i.turn), i.replyLanded, i.focusId⟩
noncomputable def focused (cs : List Circus.Character) (i : ChainInput) : Monkey.Consumer :=
  let mi := monkeyInput cs i
  Monkey.consume cs mi.snapshot { i.view with loop := (collected cs i).loop }
    (Monkey.monkeyRun mi i.proposal)

def passRows (cs : List Circus.Character) (inp : Inputs) : List Monkey.PassRow :=
  (rebuild cs inp).mapIdx (fun index e => ⟨e.item, some (index + 1)⟩)
noncomputable def ranked (cs : List Circus.Character) (i : ChainInput) : List Monkey.RankedRow :=
  Monkey.priorityProjection (passRows cs (focused cs i).view.loop.inputs) (focused cs i).active i.cutoff
/-- Request publication using Freshness's existing flight/coalescing state.
One completion need not catch up; the renderer below then shows typed staleness. -/
noncomputable def published (cs : List Circus.Character) (i : ChainInput) : Supersession.Reader :=
  let v := (focused cs i).view
  let requested := Freshness.advance cs v (.metadata i.store.run i.passInputDigest
    v.sources.pendingIndex v.sources.lexicon v.sources.focusVersion)
  Supersession.renderReader i.store (Freshness.finish cs i.refreshSucceeded requested)

/-- Existing pass inputs must actually reflect the bridged item/needs view.
This is a check, not an invented input decoder. Serves acceptance and other
pass fields remain their existing independently supplied inputs. A mismatch
is visible passInputMismatch, never a claimed update against the new graph. -/
def PassInputsMatch (g : GCC) (inp : Inputs) : Prop :=
  inp.items = g.items ∧ ∀ a ∈ g.items, ∀ b ∈ g.items,
    inp.needs a.id b.id = g.needs a b

noncomputable def ready (cs : List Circus.Character) (i : ChainInput) : Prop :=
  ∃ f a, (focused cs i).active = some f ∧
    (published cs i).view.artifact = some a ∧ Freshness.Current (published cs i).view a ∧
    a.runId = i.store.run ∧ a.entries = rebuild cs (focused cs i).view.loop.inputs ∧
    a.entries ≠ [] ∧ ∀ e ∈ a.entries, Freshness.Consumable (published cs i).view a e

/-- The notices panel is part of Mojira's displayed result, not a log. Empty
computed rankings get noRankedItems rather than pretending focus failed. This
additional typed absence resolves the valid empty-corpus case without silent
emptiness. Existing stale/history entries can remain visibly blocked. -/
noncomputable def renderChain (cs : List Circus.Character) (i : ChainInput) : Mojira := by
  classical
  let errors := (bridged cs i).2.map ChainAbsence.bridge ++
    (jobAbsences i.basis (collected cs i).delivered).map (fun p => .job p.1 p.2) ++
    ((focused cs i).failure.toList.map ChainAbsence.focus) ++
    (if (ranked cs i).isEmpty then [.noRankedItems] else []) ++
    (if PassInputsMatch (bridged cs i).1 (focused cs i).view.loop.inputs then [] else [.passInputMismatch])
  exact if errors = [] ∧ ready cs i then
    ⟨published cs i, ranked cs i, (focused cs i).active.map (fun f =>
      ⟨i.corpus.generation, semanticRecords (collected cs i).delivered,
        f.record, f.evidencePin, i.store.run⟩), []⟩
  else ⟨published cs i, ranked cs i, none,
    if errors = [] then [.stale .sourceChangedSincePass] else errors⟩

/-- Citation is shared by the displayed priority rows; reading citations and
qualified history remain Supersession's. No citation can hide a missing focus.
The view's entries are the logical pass; priorities annotate/reorder that pass
through Monkey.priorityProjection, not a newly inferred focus. -/
def CitesBasis (basis : ReasonBasis) (corpus : Corpus) (store : Supersession.Store)
    (out : Mojira) : Prop :=
  ∃ c a, out.citation = some c ∧ c.corpusGeneration = corpus.generation ∧
    c.focusRecord.kind = .houFocus ∧ c.passId = store.run ∧
    out.reader.view.artifact = some a ∧ Freshness.Current out.reader.view a ∧
    a.entries ≠ [] ∧ (∀ e ∈ a.entries, Freshness.Consumable out.reader.view a e ∧
      e.reason.explainable basis e.item) ∧ out.priorities ≠ []
def ReaderOutcome (basis : ReasonBasis) (c : Corpus) (store : Supersession.Store) (out : Mojira) : Prop :=
  CitesBasis basis c store out ∨ out.notices ≠ []

private theorem active_focus_kind (cs : List Circus.Character) (i : ChainInput)
    (f : Monkey.FocusRecord) (active : (focused cs i).active = some f) :
    f.record.kind = .houFocus := by
  unfold focused at active
  cases ho : Monkey.monkeyRun (monkeyInput cs i) i.proposal with
  | failed why => simp [ho, Monkey.consume] at active
  | written focus =>
    simp only [ho, Monkey.consume] at active
    split at active
    · simp only [Option.some.injEq] at active
      subst f
      exact (Monkey.focus_located_in_gcc _ _ _ ho).1
    · cases active

private theorem rebuild_faithful (cs : List Circus.Character) (inp : Inputs) (basis : ReasonBasis)
    (hfaith : ∀ c ∈ cs, ∀ e ∈ c.order inp, e.reason.explainable basis e.item) :
    ∀ e ∈ rebuild cs inp, e.reason.explainable basis e.item := by
  intro e he
  unfold rebuild at he
  obtain ⟨a, i, hi, hr⟩ := entriesFrom_reason (reasonFor cs inp)
    (Retro.partyVote budgetTen [] (orderingsOf cs inp)) budgetTen e he
  rw [hr, hi]
  exact reasonFor_faithful cs inp basis a i hfaith

/-- VERIFY obligation 2, "One complete live chain": Mojira asks "what should
Joe look at next, and why". Settlement/reply permit work, not guaranteed success.
Missing decode, job, focus, publication or reading evidence is a visible absence.
OLD: reason strings belonged to approved vocabulary. NEW: typed reason values
must be true of their cited basis; no approval step. Joe: "I think the reasons
at any given point in time can just be stated, they don't have to be approved.
We're talking about "explainable AI" not "by-fiat AI"".
The live source-value check is futon3c scripts/giraffe_party_conformance.py. -/
theorem chain_turn_to_reader (cs : List Circus.Character) (i : ChainInput) (basis : ReasonBasis)
    (reply : i.replyLanded = true) (kind : KindAgreement i.bridge) (ids : IdentityInjective i.bridge)
    (hfaith : ∀ c ∈ cs, ∀ e ∈ c.order (focused cs i).view.loop.inputs,
      e.reason.explainable basis e.item) :
    ReaderOutcome basis i.corpus i.store (renderChain cs i) ∧
    (∀ citation, (renderChain cs i).citation = some citation → ∃ f,
      (focused cs i).active = some f ∧
      citation.sources = semanticRecords (collected cs i).delivered ∧
      citation.focusRecord = f.record ∧ citation.focusPin = f.evidencePin ∧
      PassInputsMatch (bridged cs i).1 (focused cs i).view.loop.inputs) := by
  classical
  unfold renderChain
  dsimp only
  generalize herr : ((bridged cs i).2.map ChainAbsence.bridge ++
    (jobAbsences i.basis (collected cs i).delivered).map (fun p => .job p.1 p.2) ++
    ((focused cs i).failure.toList.map ChainAbsence.focus) ++
    (if (ranked cs i).isEmpty then [.noRankedItems] else []) ++
    (if PassInputsMatch (bridged cs i).1 (focused cs i).view.loop.inputs then [] else [.passInputMismatch])) = errors
  split
  next good =>
    have matchInputs : PassInputsMatch (bridged cs i).1 (focused cs i).view.loop.inputs := by
      by_cases hm : PassInputsMatch (bridged cs i).1 (focused cs i).view.loop.inputs
      · exact hm
      · have hx := herr.trans good.1
        simp [hm] at hx
    have nonempty : ranked cs i ≠ [] := by
      intro he
      have hx := herr.trans good.1
      simp [he] at hx
    obtain ⟨f, a, hf, ha, current, run, entries, ne, consumable⟩ := good.2
    refine ⟨Or.inl ?_, ?_⟩
    · refine ⟨⟨i.corpus.generation, semanticRecords (collected cs i).delivered,
        f.record, f.evidencePin, i.store.run⟩, a, ?_, rfl,
        active_focus_kind cs i f hf, rfl, ha, current, ne, ?_, nonempty⟩
      · simp [hf]
      · intro e he
        exact ⟨consumable e he, rebuild_faithful cs _ basis hfaith e (entries ▸ he)⟩
    · intro citation hc
      simp only [hf, Option.map_some, Option.some.injEq] at hc
      subst citation
      exact ⟨f, hf, rfl, rfl, rfl, matchInputs⟩
  next bad =>
    constructor
    · apply Or.inr
      dsimp only
      split <;> simp_all
    · intro citation hc
      cases hc

/-- Focus-dependent bands come ONLY from a received fresh focus. Comparing the
same pass targets before/after mention-only delivery with no new active focus
cannot promote any of them. Rank changes are deliberately not called bands. -/
def Bands (rows : List Monkey.PassRow) (focus : Option Monkey.FocusRecord) : List (EntityId × Monkey.Band) :=
  (Monkey.rawRows rows focus).map (fun r => (r.target, r.band))
/-- VERIFY: "Mention records alone do not establish readiness, focus or an
accountable next action". No fresh FocusRecord means all rows stay unknown.
The mention-only premise records the causal scope; mentions never enter banding. -/
theorem chain_meaningful_change (rows : List Monkey.PassRow) (ds : List Delivery)
    (mentions : ∀ d ∈ ds, d.source.key.slot = .known .mention) :
    Bands rows none = rows.map (fun r => (r.target, .unknown)) ∧
    (∀ cs i, i.deliveries = ds → (focused cs i).active = none →
      ∀ r ∈ (renderChain cs i).priorities,
        r.row.band = .unknown ∧ r.row.reason = some .focusRelationMissing) := by
  classical
  constructor
  · apply List.ext_getElem?
    intro index
    simp [Bands, Monkey.rawRows, Monkey.band, Option.map_map, Function.comp_def]
  · intro cs i deliveries inactive r hr
    have he : (renderChain cs i).priorities = ranked cs i := by
      unfold renderChain
      dsimp only
      generalize ((bridged cs i).2.map ChainAbsence.bridge ++
    (jobAbsences i.basis (collected cs i).delivered).map (fun p => .job p.1 p.2) ++
    ((focused cs i).failure.toList.map ChainAbsence.focus) ++
    (if (ranked cs i).isEmpty then [.noRankedItems] else []) ++
    (if PassInputsMatch (bridged cs i).1 (focused cs i).view.loop.inputs then [] else [.passInputMismatch])) = errors
      split <;> rfl
    rw [he] at hr
    exact (Monkey.giraffe_band_respects_focus _ _ _).2.2.1 r hr (by simp [inactive])

/-- VERIFY: "Exercise a correction or rejection ... reaches the reader".
Uses the accepted Supersession correction path and the SAME Timely hypothesis.
Operator rejection of a current interpretation is a withdrawal with its reason;
Supersession.reject alone is a rejected candidate and does not revoke an active
reading. We do not silently change those accepted semantics. -/
theorem chain_correction_reaches_reader (cs : List Circus.Character) (out : Mojira)
    (store : Supersession.Store) (correction : Supersession.Correction)
    (T occurred observed : Time)
    (timely : Freshness.Timely T [⟨occurred, observed,
      Supersession.correctionEvent (Supersession.correct store correction) out.reader.view⟩]) :
    observed ≤ occurred + T ∧
    Supersession.CorrectionVisible store (Supersession.correct store correction)
      (Supersession.observeCorrection cs store out.reader.view correction) ∧
    Supersession.ReaderSafe (Supersession.correct store correction)
      (Supersession.observeCorrection cs store out.reader.view correction) := by
  have h := Supersession.correction_reaches_reader cs store out.reader.view correction T occurred observed timely
  exact ⟨h.1, h.2, Supersession.reader_sees_replacement _ _⟩

/-- VERIFY navigation: d is a measured snapshot HYPOTHESIS, never a constant.
claude-1, 2026-11-15: corpus generation 1102dde4, futon1b watermark 3733,
convention A (benefit line is its own node), diameter 18 Reach hops over the
largest component (463 of 656 items); 184 isolated items require typed noPath.
These measurements are recorded evidence, not a proof about today's graph.
CAVEAT (claude-13 / codex-13, 2026-11-15): measure.py:160-164 joined a
multi-item turn through ONE hub node (item-hub-item = 2 hops). This model has
no such hub: an endpoint is a partial function per role (Basic.lean:162-186),
so separate item-only mentions give noPath between the turn's items, and a
shared entity endpoint would give 4 hops. The 18 is therefore not yet a
measurement of this model; re-measuring needs the mention endpoint contract
and a saved inventory+readings snapshot (~/notes/codex-13/nav-remeasure/). -/
theorem chain_navigation_bound (g : GCC) (turn : TurnId) (focus : List Item) (d : Nat)
    (measured : LoopBuilt g (turnItems g turn focus) d)
    (a b : Item) (ha : a ∈ turnItems g turn focus) (hb : b ∈ turnItems g turn focus) :
    g.Reach d (.item a) (.item b) := by
  exact gcc_turn_navigable_at_most_d g turn focus d measured a b ha hb


/-! ## TEETH: invented defective bridges and consumers -/
namespace Teeth
private def a : Item := ⟨1, .mission, "M-example-a", true⟩
private def b : Item := ⟨2, .ticket, "T-example-b", true⟩
private def corpus : Corpus := ⟨1, [a, b], [], [], []⟩
private def source : SourceRecord := ⟨⟨7, .xiang, .known .mention⟩, 1, .success "example"⟩
private def ds : List Delivery := [⟨source, ⟨1, 1, "example-job"⟩⟩]
private def basis : ComparisonBasis := ⟨[], 1⟩
private def decoder (target : Item) : Slot → String → Option Decoded
  | .known kind, _ => some ⟨kind, [(.item, .item target)]⟩
  | .dogBuried, _ => none
private def bridge : Bridge := ⟨decoder a, fun key => key.turn⟩
private def gcc : GCC := (bridgeGCC bridge corpus basis ds).1
private def record : GCCRecord := ⟨7, .mention, some 7⟩
private def outsider : Item := ⟨99, .mission, "M-outside", true⟩
private def unknownBridge : Bridge := { bridge with decode := decoder outsider }
-- 1a. Inventing an item to accommodate an unknown endpoint violates inventory preservation.
example : ¬BridgeFaithful unknownBridge corpus basis ds
    ({ gcc with items := outsider :: corpus.items }, []) := by
  intro h
  have bad := h.1
  have lengths := congrArg List.length bad
  change 3 = 2 at lengths
  cases lengths
example : (bridgeGCC unknownBridge corpus basis ds).1.records = [] ∧
    (bridgeGCC unknownBridge corpus basis ds).2 = [.unknownEndpoint source.key] := by decide
private def malformed : Bridge := { bridge with decode := fun _ _ => none }
-- 1b. Silently dropping malformed input fails the per-head accounting clause.
example : ¬BridgeFaithful malformed corpus basis ds
    ((bridgeGCC malformed corpus basis ds).1, []) := by
  intro h
  have covered := h.2.2.2.2.1 source (by decide) (by decide)
  rcases covered with ⟨d, hd, _⟩ | ⟨why, _, count⟩
  · have actual : inspect malformed corpus source = some (.error (.malformedPayload source.key)) := rfl
    rw [actual] at hd
    cases hd
  · cases count
private def duplicate : Bridge := { bridge with decode := fun slot _ => match slot with
  | .known kind => some ⟨kind, [(.item, .item a), (.item, .item b)]⟩
  | .dogBuried => none }
-- 1c. Keeping the first endpoint loses data and cannot be faithful.
example : ¬BridgeFaithful duplicate corpus basis ds (gcc, []) := by
  intro h
  obtain ⟨r, d, hr, hd, _, _⟩ := h.2.2.2.1 record (by decide)
  have heads : usableHeads basis ds = [source] := by decide
  have only : r = source := by simpa [heads] using hr
  subst r
  have actual : inspect duplicate corpus source = some (.error (.invalidEndpoints source.key)) := rfl
  rw [actual] at hd
  cases hd
example : (bridgeGCC duplicate corpus basis ds).1.records = [] ∧
    (bridgeGCC duplicate corpus basis ds).2 = [.invalidEndpoints source.key] := by decide
private def burial : Delivery := ⟨⟨⟨7, .dog, .dogBuried⟩, 1, .success "burial-result"⟩,
  ⟨2, 1, "burial-job"⟩⟩
-- dogBuried has no GCC endpoint; adding its delivery cannot change touched items.
example : turnItems (bridgeGCC bridge corpus basis (ds ++ [burial])).1 7 [] =
    turnItems (bridgeGCC bridge corpus basis ds).1 7 [] := by decide
-- Repeated delivery is one bridge record, not two nodes or two errors.
example : (bridgeGCC bridge corpus basis (ds ++ ds)).1.records = [record] ∧
    (bridgeGCC malformed corpus basis (ds ++ ds)).2 = [.malformedPayload source.key] := by decide

private def inp : Inputs := ⟨[a, b], [(1, 1)], fun _ => 0, fun _ _ => false, fun _ => [], [], 0⟩
private def loop : State := ⟨0, inp, [], 0, 0⟩
private def displayBasis : Freshness.DisplayBasis := ⟨1, 1, [(1, 1), (2, 1)], 1, 1, some 1⟩
private def entry : Entry := ⟨1, 1, ⟨["example-reason"], .thor, [.lit "example-reason"], .thor⟩⟩
private def artifact : Freshness.Artifact := ⟨1, 0, some displayBasis, [entry], [1]⟩
private def view : Freshness.ViewState := { Freshness.initial loop displayBasis with artifact := some artifact }
private def premature : Mojira := ⟨⟨view, []⟩,
  Monkey.priorityProjection [⟨1, some 1⟩] none 1, none, []⟩
-- 2. A rendered row without a focus citation or a visible absence is not a chain outcome.
example : ¬ReaderOutcome (fun _ _ => {}) corpus Supersession.empty premature := by
  intro h
  rcases h with ⟨c, ar, citation, _⟩ | notices
  · cases citation
  · exact notices rfl
-- 3. Mention-only promotion to focus contradicts the received-focus projection.
example : Bands [⟨1, some 1⟩] none ≠ [(1, .focus)] ∧
    Bands [⟨1, some 1⟩] none = [(1, .unknown)] := by decide

private def old : SourceRecord := ⟨⟨7, .giraffe, .known .giraffeReading⟩, 1, .success "old"⟩
private def newer : SourceRecord := { old with revision := 2, payload := .success "replacement" }
private def first := Supersession.land Supersession.empty 1 1 old "initial"
private def replaced := Supersession.land first 1 1 newer "correction"
-- 4. Timely delivery does not excuse a consumer that retains unqualified old citations.
example : Freshness.Timely 5 [⟨10, 11, .metadata 2 2 1 1 (some 1)⟩] ∧
    view.artifact.map Freshness.Artifact.runId = some 1 ∧
    ¬Supersession.CorrectionVisible first replaced premature.reader := by
  refine ⟨?_, rfl, ?_⟩
  · constructor
    · simp
    · intro e he
      simp only [List.mem_singleton] at he
      subst e
      decide
  · intro h
    have missing := (h ⟨1, 1, old, .current⟩ (by decide) rfl
      ⟨1, 1, old, .superseded newer "correction"⟩ (by decide) (by decide)).1
    cases missing

private def disconnected : GCC := ⟨[a, b], [], [], [], fun _ _ => none, []⟩
private theorem no_edges {n : Nat} {x y : GNode} (h : disconnected.Reach n x y) : x = y := by
  induction h with
  | refl => rfl
  | step _ x c _ hadj _ _ =>
    cases x <;> cases c <;> simp [GCC.adj, GCC.incident, disconnected] at hadj
  | more _ _ _ _ ih => exact ih
-- 5. A disconnected focus cannot acquire a finite bound merely by claiming 18.
example : ¬LoopBuilt disconnected (turnItems disconnected 7 [a, b]) 18 := by
  intro h
  have path := h.2.2.2 a (by decide) b (by decide)
  exact (by decide : GNode.item a ≠ .item b) (no_edges path)
-- The same number is a harmless parameter on a singleton toy scope.
example : disconnected.Reach 18 (.item a) (.item a) := GCC.Reach.pad (by decide) (.refl _)

-- A failed job remains visible through the original graph absence predicate.
example : jobAbsences ⟨[⟨source.key, 5⟩], 6⟩ [] = [(source.key, .deadlineExpired)] := by decide
-- Role uniqueness alone is insufficient: item/entity role mismatch is typed too.
private def wrongRole : Bridge := { bridge with decode := fun slot _ => match slot with
  | .known kind => some ⟨kind, [(.item, .entity 4)]⟩
  | .dogBuried => none }
example : (bridgeGCC wrongRole { corpus with entities := [4] } basis ds).2 =
    [.invalidEndpoints source.key] := by decide
-- The source's existing key model exposes simultaneous divergent mentions as
-- a conflict. The bridge must not pretend these are two identified records.
private def collision : Delivery := ⟨{ source with payload := .success "different" }, ⟨2, 1, "other-job"⟩⟩
example : (bridgeGCC bridge corpus basis (ds ++ [collision])).1.records = [] ∧
    jobAbsences basis (ds ++ [collision]) = [(source.key, .revisionConflict)] := by decide
-- A later usable revision changes endpoints without changing record identity.
private def reviseBridge : Bridge := { bridge with decode := fun slot text => match slot with
  | .known kind => some ⟨kind, [(.item, .item (if text == "later" then b else a))]⟩
  | .dogBuried => none }
private def revision : Delivery := ⟨{ source with revision := 2, payload := .success "later" }, ⟨2, 1, "revision-job"⟩⟩
example : (bridgeGCC reviseBridge corpus basis ds).1.records = [record] ∧
    (bridgeGCC reviseBridge corpus basis (ds ++ [revision])).1.records = [record] ∧
    turnItems (bridgeGCC reviseBridge corpus basis ds).1 7 [] = [a] ∧
    turnItems (bridgeGCC reviseBridge corpus basis (ds ++ [revision])).1 7 [] = [b] := by decide

end Teeth

end Circus.LiveChain

/-! ## Why a decoder parameter is necessary (accepted first blocker) -/

-- No registry theorem is used. These are invented records and items.
namespace LiveChainBridgeMismatch
open Circus Circus.LateWrites
private def source : SourceRecord :=
  ⟨⟨7, .xiang, .known .mention⟩, 1, .success "opaque-payload"⟩
private def delivered : List Delivery := [⟨source, ⟨1, 1, "job-example"⟩⟩]
private def semantic : SemanticGraph := rebuildGraph ⟨[], 1⟩ delivered
private def a : Item := ⟨1, .mission, "M-example-a", true⟩
private def b : Item := ⟨2, .mission, "M-example-b", true⟩
private def record : GCCRecord := ⟨10, .mention, some 7⟩

-- Both interpretations agree on the source's known kind and turn.
-- The source has no typed field choosing either item endpoint.
private def interpretation (target : Item) : GCC :=
  ⟨[a, b], [record], [], [],
    fun r role => if r == record && role == .item then some (.item target) else none, []⟩

example : semantic.history source = true ∧
    semantic.usable source.key 1 "opaque-payload" = true := by decide
example : record.turn = some source.key.turn ∧ source.key.slot = .known record.kind := by decide
example : (interpretation a).endpoint record .item = some (.item a) ∧
    (interpretation b).endpoint record .item = some (.item b) ∧
    turnItems (interpretation a) 7 [] = [a] ∧
    turnItems (interpretation b) 7 [] = [b] ∧ a ≠ b := by decide

-- A deterministic bridge given ONLY this same SemanticGraph cannot recover
-- both possible intended endpoints. Choosing one requires extra source data
-- or a specified decoder; the accepted source model supplies neither.
example (bridge : SemanticGraph → GCC) :
    ¬((bridge semantic).endpoint record .item = some (.item a) ∧
      (bridge semantic).endpoint record .item = some (.item b)) := by
  rintro ⟨ha, hb⟩
  have eq : some (GNode.item a) = some (.item b) := ha.symm.trans hb
  have same : a = b := GNode.item.inj (Option.some.inj eq)
  exact (by decide : a ≠ b) same
end LiveChainBridgeMismatch

/-! ## Why endpoint validation is necessary (accepted second blocker) -/

namespace DuplicateRoleMismatch
open Circus Circus.LateWrites

-- The decoder result shape in amendment A, with invented fixture data.
structure Decoded where
  kind : RecordKind
  endpoints : List (EndpointRole × GNode)
private def a : Item := ⟨1, .mission, "M-example-a", true⟩
private def b : Item := ⟨2, .mission, "M-example-b", true⟩
private def endpoints : List (EndpointRole × GNode) :=
  [(.item, .item a), (.item, .item b)]
private def decode : Slot → String → Option Decoded
  | .known k, _ => some ⟨k, endpoints⟩
  | .dogBuried, _ => none
private def record : GCCRecord := ⟨10, .mention, some 7⟩
private def inventory : GCC := ⟨[a, b], [], [], [], fun _ _ => none, []⟩
private def source : SourceRecord := ⟨⟨7, .xiang, .known .mention⟩, 1, .success "example"⟩
private def graph : SemanticGraph := rebuildGraph ⟨[], 1⟩ [⟨source, ⟨1, 1, "example-job"⟩⟩]

-- The decoder satisfies the requested kind-agreement hypothesis for ALL slots.
example : ∀ k s, (decode (.known k) s).map (·.kind) ∈ [none, some k] := by
  intro k s
  simp [decode]
-- This is a usable successful head, not an excluded failure/conflict/retraction.
example : graph.usable source.key 1 "example" = true := by decide
-- Neither allowed decoder/endpoint error applies: decoding succeeds and BOTH
-- targets are stored, well-typed item endpoints. The only defect is role reuse.
example : (decode (.known .mention) "example").isSome = true ∧
    (∀ ep ∈ endpoints, inventory.Mem ep.2 ∧ EndpointFits ep.1 ep.2) := by
  refine ⟨rfl, ?_⟩
  intro ep he
  simp only [endpoints, List.mem_cons, List.not_mem_nil, or_false] at he
  rcases he with rfl | rfl <;> exact ⟨by unfold GCC.Mem; decide, trivial⟩

-- No GCC can retain both decoded endpoints: endpoint is a partial FUNCTION
-- of (record, role). This obstruction is independent of recordId allocation.
example : ¬∃ gcc : GCC, ∀ ep ∈ endpoints, gcc.endpoint record ep.1 = some ep.2 := by
  rintro ⟨gcc, faithful⟩
  have ha := faithful (.item, .item a) (by decide)
  have hb := faithful (.item, .item b) (by decide)
  have eq : some (GNode.item a) = some (.item b) := ha.symm.trans hb
  exact (by decide : a ≠ b) (GNode.item.inj (Option.some.inj eq))
end DuplicateRoleMismatch
