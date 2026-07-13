# PLAN — handoff to Claude Opus (drafted 2026-07-13, claude-6/Fable)

Operator: Joe. Read this top to bottom before dispatching anything.
Canonical protocol docs (do not skip): `~/code/CLAUDE.md` (handoff + park-on-
every-dispatch), `futon5/holes/missions/M-lab-standard.md` (genre contract,
layer table, stochasticity rule, dispatch standard), `M-sci-reproduction.md`
(ledger A1–A7, checkpoints), `M-sci-reproduction-replay-ledger.md` (Tier
ranking + B-series ledger), `futon3c/holes/excursions/E-park-delivery-losses.md`
(park protocol state + open items).

## Where things stand (2026-07-13 end of session)

- **M-sci-reproduction: COMPLETE and published.** nb01–nb04 at
  `hyperreal.enterprises/lab/sci-repro/` (futon5 `notebooks/sci-repro/`).
  Claims C1–C7 measured or machine-checked; four dynamics grid-identical to
  `256ca.el`, including both mutation variants via injected streams.
- **Tier-2 replay #2 (boundary-guardian) in flight.** R1a (exo port +
  statistical cross-check) ACCEPTED, commit `1dcbd51`. R1a.2 dispatched to
  zai-1 (job `invoke-1783981034803-388`): upgrade to grid-identity after
  claude-6's two-process 500-context probe REFUTED the "EigenDecomposition is
  non-deterministic across JVMs" route justification. Its bell will either
  deliver grid-identity or use the first-diff cell to find the real mechanism.
- **Park protocol: hardened and real.** Six bugs fixed live; zai-2's
  independent review confirmed all fixes (verdict table in the excursion).
  `agency_send.py --park` = dispatch+park in one command; wake payloads are
  checklists; treat EVERY wake as check-state-then-act (duplicates possible).

## Review discipline — the one thing that mattered most today

Every slice review caught something the green gates missed: a circular proof
(slice 1), a mixed-width IC cohort (slice 2), a tautology framed as evidence
(slice 3), a stream abstraction that couldn't express the target (4a→4b), an
untested non-determinism assumption (R1a). **Author ≠ reviewer, and the
reviewer independently recomputes the headline numbers.** Re-run every claimed
PASS un-piped (ZU-4: grep silence through a timed-out pipe is not a verdict).
Fix review findings yourself; re-dispatch only substantial new work.

## Work queue, in order

### 1. Close R1a.2 (first wake you receive, most likely)
Review: re-run the grid cross-check TWICE yourself (two JVM invocations);
verify the "where did the belief come from" answer and the B1 correction.
If grid-identity passed → statistical demoted to corroboration, proceed.
If failed → the first-diff mechanism finding is the deliverable; statistical
stays primary with corrected justification.

### 2. R1b — boundary-guardian notebook (dispatch after R1a.2 closes)
The first Tier-2 notebook. Claims (from replay-ledger #2): genotype-layer EoC
reproduction with CIs over ≥30 seeds (target: H≈0.946, Δ≈0.985, ρ≈0.015 vs
frozen L0 baseline); the standard-verifier blind spot (≈0.176) CONFIRMED as a
measured fact; the Codex-vs-local disagreement (99.5% chaotic vs settling)
RECONCILED with explicit metric definitions; the three proposed discriminators
(bitplane MI, diagonal autocorrelation, triangle density) tested against a
true-random null AND a Rule-30 chaos baseline. Genre contract applies in full.
**Publishing:** new series dir `futon7a/lab/replays/` with its own index and
publish script (copy the `publish.sh` pattern; M-lab-standard step 8) — do
NOT append to the sci-repro index.

### 3. Remaining reproduction steps (interleave as capacity allows)
- **R-repro-5: the mutating-template dynamic.** The elisp DEFAULT
  (`evolve-sigil-with-mutating-template`, ground truth for the throttled-
  mutation figures) was never ported — nb04's balance-mutation runs on the
  blend dynamic with an explicit scope note. Port + cross-check closes the
  last fidelity gap with the paper's actual figures.
- **R-repro-6: the Baldwin variant.** `evolve-sigil-with-blending-baldwin`
  (256ca.el:636-686) implements the paper's §5.2 Baldwin-effect open question
  and has never been run in the lab. Port, cross-check, measure — this is
  reproduction and a new experiment at once.
- Optional: the 500×500 Figure-4 showcase panel; A7 archaeology if new
  evidence surfaces.

### 3.5 Tooling: `scirepro.runstore` (charter early — it unblocks everything long)
Per M-lab-standard's Long-run rule (added after R1a.2's timeout thrashing):
incremental keyed run artifacts (grids append rows every K generations,
completion markers), drivers read through the store (re-runs skip completed
seeds, resume partial ones), retrofit the cross-check drivers and the
old-engine headless runner. Until it lands: detached launch (`nohup` + poll
the log) and per-seed artifact splitting are the interim practice — never a
long run inside one `timeout N` tool call. Zai-sized as two slices (store +
retrofit).

### 4. Tier-2 continuation (after R1b establishes the replay-series pattern)
Ranked in the replay ledger; key notes per replay:
- **泰-zone (#5)** and **bitplane-MI (#3)**: zai-shaped, engine-local — the
  designated metameso (linode-chicago) pilots. GATED on Joe providing the
  repo-sync path; preflight otherwise done (toolchain + key verified). First
  remote dispatch = one disposable runner + trivial parked bell BEFORE real
  work.
- **Evaluator-population Goodhart guard (#4)**: first xeno-layer module; same
  port-per-replay + cross-check discipline; naive-collapse baseline vs
  evaluator-population arm, seeds + CIs.
- **CyberAnts controlled replay (#1, candidate nb05 of the replay series)**:
  cross-repo (futon2 `ants.compare`, pin SHA ≥ `103ca6b`); random-wiring +
  shuffled-parameter controls; starvation (0.00 scores) handled explicitly;
  per-run EDN persisted in-repo. Codex-sized; keep under close review.

### 5. Infrastructure opens (futon3c — fix as owner, small slices)
From the excursion's open lists, in priority order:
1. **Drawbridge `forbidden`** — appeared evening 2026-07-13; blocks live
   introspection AND the single-defn live-patch pattern. Diagnose first (ask
   Joe if he changed it); everything below needs it or a restart window.
2. **Suppression miss trace** — job `invoke-1783977240719-379` got an
   auto-bellback despite an awaiting park; `:auto-bellback` ledger field null.
   Hypotheses in the excursion.
3. **`GET /parked` hides background parks** — visibility regression from the
   bug-4 fix; add `?mode=all`, update `claude-repl-jobs.el`.
4. **Provenance header** — machine resumes still present as
   `From: joe / Origin: operator` to the agent; harness-side header
   construction not yet located. A wake must not impersonate the operator.
5. **Bootstrap wiring** — `claude-repl-bootstrap.el` exists but is loaded
   from nowhere; needs Joe's placement decision + one line.
6. **JVM restart quiet window (Joe-gated)** — activates zai-15's ZU-4
   tool-failure visibility in `zai_api.clj` AND is the first live test of
   parked-on disk rehydration. Coordinate with Joe; I-0 discipline.

## Guardrails (non-negotiable)
- `futon5/256ca.el` and `futon5/src/futon5/**` are read-and-run-only ground
  truth. Port per-replay into `scirepro.*`; never wholesale.
- Every dispatch: `--park` (45-min deadline for codex, and note zai slices are
  HALF codex size — max-tool-rounds kills oversized zai jobs; recover stranded
  work from the working tree before re-dispatching).
- Publish only rendered notebook artifacts + index to futon7a — never mission
  internals or agent coordination material. Publish happens at review, by the
  reviewer, after acceptance.
- Mechanism claims require a test or a file:line citation — "known to be
  non-deterministic" without either is how today's only route error happened.
- Never restart the futon3c JVM or :7071/:7073 stores outside a Joe-approved
  quiet window. Live-patch via Drawbridge single-defn re-eval (when access is
  restored); never reload the serving namespace.
- Joe's word overrides every default ("no bells or whistles" = do it yourself).

## Joe-gated decisions pending
metameso repo-sync path; JVM restart quiet window; M-lab-standard ratification
(in use, formally DERIVE); bootstrap placement; Drawbridge access question.
