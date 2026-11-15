import Circus.Freshness

/-!
# VERIFY pre-specification: reconciliation after settlement

Extends Circus.State with a delivery journal and a semantic projection;
settlement still uses Circus.step/applyEvent/rebuild. It is NOT replayed for
each animal delivery: duplicate settlement must not bump activation twice.
The earlier incremental_eq_rebuilt and primary_up_to_date_top_ten concern the
logical ranking, not the completeness of an asynchronous source journal.
Document changes and accepted landings remain governed by docChange_stales,
readingLanded_unstales and giraffe_firing_rule (Freshness supplies the digest
guard). This module does not invent a payload-to-Inputs/GCC adapter.

Evidence: dog_provider.clj handle-event/start! submit asynchronous work;
prepare-use-check! writes dog_use and subsequently starts dog_buried. Basic's
RecordKind has dogUse but no dogBuried, so Slot extends it without changing
Basic. Distinct output slots coexist; a revision replaces only its own slot.

Revision numbers must be allocated by the source per (turn, animal, slot),
not from arrival time. Equal-revision divergent payloads are visible conflicts,
never an arbitrary tie-break. Retractions are tombstones, not journal deletion.
The semantic graph is a finite-support membership view, not an ordered list:
list journals represent delivered multisets; multiplicity is deliberately
irrelevant to semantic membership. Rejected/superseded records remain in history.
-/
namespace Circus.LateWrites

inductive Slot
  | known (kind : RecordKind)
  | dogBuried
  deriving DecidableEq, Repr
structure Key where
  turn : TurnId
  animal : Agent
  slot : Slot
  deriving DecidableEq, Repr
inductive Payload
  | success (value : String)
  | failure (reason : String)
  | retract (reason : String)
  deriving DecidableEq, Repr
structure SourceRecord where
  key : Key
  revision : Nat
  payload : Payload
  deriving DecidableEq, Repr
structure Operational where
  deliveredAt : Time
  attemptCount : Nat
  jobId : String
  deriving DecidableEq, Repr
structure Delivery where
  source : SourceRecord
  operational : Operational
  deriving DecidableEq, Repr

/-- The comparison erases exactly delivery time, attempt count and job id.
Turn/animal/slot, source revision and payload (including failure/retraction
reasons) remain semantic. A job id must not be used to allocate a revision. -/
def eraseOperational (d : Delivery) : SourceRecord := d.source

def semanticRecords (delivered : List Delivery) : List SourceRecord :=
  delivered.map eraseOperational

structure ExpectedJob where
  key : Key
  deadline : Time
  deriving DecidableEq, Repr

/-- A declared finite job inventory is necessary to detect jobs that never
return. The inventory has one deadline per key; it is pinned, not renewed on
retry. Comparison uses the SAME inventory and evaluation time on both sides:
passing a deadline changes the graph, even with no new delivery. A live
consumer must therefore reconcile at deadlines as well as on record revisions;
this pure model does not claim that such a timer/watchdog is deployed. -/
structure ComparisonBasis where
  expected : List ExpectedJob
  now : Time
  deriving DecidableEq, Repr

def deadline (b : ComparisonBasis) (k : Key) : Option Time :=
  (b.expected.find? (fun j => j.key == k)).map (·.deadline)

inductive Absence
  | pending
  | deadlineExpired
  | failed (reason : String)
  | retracted (reason : String)
  | revisionConflict
  deriving DecidableEq, Repr

/-- Functional membership avoids making list order or duplicate count part of
the semantic graph. `heads` exposes all maximal records; `usable` excludes
conflicting revisions. Failed jobs/tombstones are not usable successful values. -/
structure SemanticGraph where
  history : SourceRecord → Bool
  heads : SourceRecord → Bool
  usable : Key → Nat → String → Bool
  absence : Key → Absence → Bool

def maximal (rs : List SourceRecord) (r : SourceRecord) : Bool :=
  rs.contains r && rs.all (fun q => q.key != r.key || q.revision ≤ r.revision)

def conflict (rs : List SourceRecord) (k : Key) : Bool :=
  rs.any fun r => r.key == k && maximal rs r &&
    rs.any (fun q => q.key == k && maximal rs q && q.payload != r.payload)

/-- A rebuild from the retained sources. No settled flag is consulted. -/
def rebuildGraph (b : ComparisonBasis) (delivered : List Delivery) : SemanticGraph :=
  let rs := semanticRecords delivered
  { history := rs.contains
    heads := maximal rs
    usable := fun k rev value => maximal rs ⟨k, rev, .success value⟩ && !conflict rs k
    absence := fun k a =>
      match a with
      | .pending => !(rs.any fun r => r.key == k) &&
          (deadline b k).any (fun due => b.now < due)
      | .deadlineExpired => !(rs.any fun r => r.key == k) &&
          (deadline b k).any (fun due => due ≤ b.now)
      | .failed why => rs.any fun r => r.key == k && maximal rs r && r.payload == .failure why
      | .retracted why => rs.any fun r => r.key == k && maximal rs r && r.payload == .retract why
      | .revisionConflict => conflict rs k }

/-- The projector extends the existing loop. The journal is authoritative;
any materialized graph cache is recoverable by rebuildGraph at a pinned basis. -/
structure Projector where
  loop : State
  settled : List (TurnId × Time)
  delivered : List Delivery

def settle (cs : List Character) (p : Projector) (t : TurnId) : Projector :=
  if p.settled.any (fun x => x.1 == t) then p
  else { p with loop := step cs p.loop (.turn t .settled)
                settled := (t, p.loop.now) :: p.settled }

/-- Revision notifications append even AFTER settlement. Publication/reconcile
must be triggered on these deliveries, not exclusively on turn settlement. -/
def deliver (p : Projector) (d : Delivery) : Projector :=
  { p with delivered := p.delivered ++ [d] }

def graph (b : ComparisonBasis) (p : Projector) : SemanticGraph :=
  rebuildGraph b p.delivered

def replay (p : Projector) (records : List Delivery) : Projector :=
  records.foldl deliver p

/-- Obligation 4: "Record the comparison basis and any excluded operational
fields." Re-stamping delivery metadata does not change the graph; the pinned
inventory, time, source revisions and semantic payloads remain unchanged. -/
theorem operational_fields_excluded (b : ComparisonBasis) (ds : List Delivery)
    (restamp : Operational → Operational) :
    rebuildGraph b (ds.map fun d => { d with operational := restamp d.operational }) =
      rebuildGraph b ds := by sorry

/-- Objection 3: "Settlement is not the end of all writes." Every late record
reaches retained history; if its revision strictly supersedes earlier records
for its slot it also reaches the active heads. An already superseded late
record is retained, not allowed to undo a newer reading or retraction. -/
theorem late_write_reaches_graph (b : ComparisonBasis) (p : Projector)
    (d : Delivery) (settledAt : Time)
    (settled : (d.source.key.turn, settledAt) ∈ p.settled)
    (late : settledAt < d.operational.deliveredAt) :
    (graph b (deliver p d)).history d.source = true ∧
    ((∀ old ∈ p.delivered, old.source.key = d.source.key →
        old.source.revision < d.source.revision) →
      (graph b (deliver p d)).heads d.source = true) := by sorry

/-- Obligation 4: "late output ... converge to the same semantic graph as a
rebuild from those sources, including supersession and retraction." Any
permutation has the same graph, including explicit equal-revision conflicts. -/
theorem delivery_order_irrelevant (b : ComparisonBasis) (xs ys : List Delivery)
    (permutation : xs.Perm ys) : rebuildGraph b xs = rebuildGraph b ys := by sorry

/-- Obligation 4: "duplicate delivery ... converge to the same semantic graph".
A repeated delivery has one semantic identity, not an additional fact count. -/
theorem duplicate_delivery_idempotent (b : ComparisonBasis) (ds : List Delivery)
    (d : Delivery) : rebuildGraph b (ds ++ [d, d]) =
      rebuildGraph b (ds ++ [d]) := by sorry

/-- Obligation 4: "interruption and replay converge to the same semantic graph
as a rebuild from those sources". Start from an empty checkpoint, stop after
ANY prefix, then replay the full journal (including that prefix). Same pinned
comparison basis, no duplicate settlement/activation side effects. This assumes
the authoritative journal survives interruption; it does not prove disk durability. -/
theorem interrupted_replay_converges (b : ComparisonBasis) (p : Projector)
    (before after : List Delivery) (empty : p.delivered = []) :
    graph b (replay (replay p before) (before ++ after)) =
      rebuildGraph b (before ++ after) ∧
    (replay (replay p before) (before ++ after)).loop = p.loop := by sorry

/-- Objection 3: "demonstrate convergence for late replies and failed animal
jobs as well". A declared job without a return is pending before its fixed
deadline and overdue at/after it. A maximal failed record is an explicit
absence. A later STRICTLY newer success replaces that absence, while the old
failure remains queryable. Same-revision disagreement is a conflict, not success. -/
theorem failed_job_is_typed_absence (b : ComparisonBasis) (ds : List Delivery)
    (k : Key) (due : Time) (declared : deadline b k = some due) :
    ((∀ d ∈ ds, d.source.key ≠ k) →
      (rebuildGraph b ds).absence k (if b.now < due then .pending else .deadlineExpired) = true) ∧
    (∀ rev why, maximal (semanticRecords ds) ⟨k, rev, .failure why⟩ = true →
      (rebuildGraph b ds).absence k (.failed why) = true) ∧
    (∀ d value, d.source.key = k → d.source.payload = .success value →
      (∀ old ∈ ds, old.source.key = k → old.source.revision < d.source.revision) →
      (rebuildGraph b (ds ++ [d])).usable k d.source.revision value = true ∧
      (∀ why, (rebuildGraph b (ds ++ [d])).absence k (.failed why) = false) ∧
      ∀ old ∈ ds, (rebuildGraph b (ds ++ [d])).history old.source = true) := by sorry

/-! ## TEETH: invented counter-projectors, fully checked
These violate the desired laws; they are not counterexamples to the proposed
rebuildGraph. No example invokes an unproved main statement.
-/
namespace Teeth
private def key : Key := ⟨7, .dog, .known .dogUse⟩
private def b : ComparisonBasis := ⟨[⟨key, 10⟩], 12⟩
private def old : Delivery := ⟨⟨key, 1, .success "old"⟩, ⟨3, 1, "job-a"⟩⟩
private def newer : Delivery := ⟨⟨key, 2, .success "new"⟩, ⟨8, 1, "job-b"⟩⟩
private def failed : Delivery := ⟨⟨key, 1, .failure "worker-failed"⟩, ⟨8, 3, "job-f"⟩⟩
private def tombstone : Delivery := ⟨⟨key, 3, .retract "withdrawn"⟩, ⟨9, 1, "job-r"⟩⟩
private def inp : Inputs := ⟨[], [], fun _ => 0, fun _ _ => false, fun _ => [], [], 0⟩
private def p : Projector := ⟨⟨5, inp, [], 0, 0⟩, [(7, 5)], []⟩

-- 1. Comparing raw delivery records makes an operational re-stamp semantic.
example : let restamped := { old with operational := ⟨99, 9, "retry"⟩ }
    old ≠ restamped ∧ eraseOperational old = eraseOperational restamped := by decide

-- 2. A settle-once projector drops dog_use delivered at 8 after settlement at 5.
private def settleOnce (p : Projector) (d : Delivery) : Projector :=
  if p.settled.any (fun x => x.1 == d.source.key.turn) then p else deliver p d
example : (graph b (settleOnce p newer)).history newer.source = false ∧
    (graph b (deliver p newer)).heads newer.source = true := by decide

-- 3. Arrival-last-wins disagrees on two permutations of the same records.
private def arrivalLast (ds : List Delivery) : Option SourceRecord :=
  ds.getLast?.map eraseOperational
example : [old, newer].Perm [newer, old] ∧
    arrivalLast [old, newer] ≠ arrivalLast [newer, old] ∧
    (rebuildGraph b [newer, old]).usable key 2 "new" = true := by
  exact ⟨List.Perm.swap _ _ _, by decide, by decide⟩

-- 4. A counting projector changes the graph on duplicate delivery.
example : ([old, old] : List Delivery).length ≠ [old].length ∧
    (rebuildGraph b [old, old]).heads old.source = true := by decide

-- 5. Append-only replay counts the prefix twice after interruption.
example : (replay (replay p [old]) [old, newer]).delivered.length = 3 ∧
    ([old, newer] : List Delivery).length = 2 ∧
    (graph b (replay (replay p [old]) [old, newer])).usable key 2 "new" = true := by decide

-- 6. Dropping failures yields silently nothing, whereas the graph records why.
private def successesOnly (ds : List Delivery) : List Delivery :=
  ds.filter fun d => match d.source.payload with | .success _ => true | _ => false
example : successesOnly [failed] = [] ∧
    (rebuildGraph b [failed]).absence key (.failed "worker-failed") = true ∧
    (rebuildGraph b []).absence key .deadlineExpired = true ∧
    (rebuildGraph b [failed, newer]).absence key (.failed "worker-failed") = false ∧
    (rebuildGraph b [failed, newer]).usable key 2 "new" = true := by decide

-- Retraction survives an old record replayed AFTER its tombstone; history remains.
example : (rebuildGraph b [tombstone, old]).absence key (.retracted "withdrawn") = true ∧
    (rebuildGraph b [tombstone, old]).usable key 1 "old" = false ∧
    (rebuildGraph b [tombstone, old]).history old.source = true := by decide

-- Equal-revision divergent payloads cannot be resolved by list order.
example : let conflictRecord := { newer with source := { newer.source with payload := .success "other" } }
    (rebuildGraph b [newer, conflictRecord]).absence key .revisionConflict = true ∧
    (rebuildGraph b [newer, conflictRecord]).usable key 2 "new" = false := by decide

-- dog_buried does not supersede dog_use just because it is from the same animal.
example : let buried := { newer with source := { newer.source with key := { key with slot := .dogBuried } } }
    (rebuildGraph b [newer, buried]).heads newer.source = true ∧
    (rebuildGraph b [newer, buried]).heads buried.source = true := by decide
end Teeth
end Circus.LateWrites
