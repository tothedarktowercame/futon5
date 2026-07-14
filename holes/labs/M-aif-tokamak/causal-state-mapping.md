# Causal-State Generative-Model Mapping (M-aif-tokamak Slice 4.5)

**Status:** DERIVE. Mapping produced; build STOPPED at a genuine modeling question.
**Date:** 2026-07-15
**Scope:** How the local-causal-state field maps to the AIF loop, per owner
decision (path B: upgrade belief/generative model before S5).

---

## The mapping

The local causal states ARE the AIF generative model (EVALUATOR-SPEC §3.9,
AIF-COMPLIANCE §47): the causal states are the minimal sufficient statistics of
the past for predicting the future — exactly the belief-states an AIF controller
maintains. Below is how each AIF role maps to the causal-state substrate.

### R1 / BELIEF μ → the causal-state field

The causal-state field (`local_causal_states.clj:causal-state-field`, L156) labels
every valid spacetime point with its local causal state (an equivalence class of
past light-cones with the same conditional future distribution). This IS the
belief representation — it is the minimal summary of the past that is sufficient
for predicting the future.

**API:** `causal-state-field grid model opts` → 2D field of state labels (integers
or nil for unsupported/unseen pasts).

**Model construction:** `reconstruct grid opts` (L196) → `{:model {:states
:past->state :future-probabilities ...} :field field}`. The model's
`:future-probabilities` per state ARE the forward-model predictions (see R4 below).

**v1 summary:** The raw causal-state field is high-dimensional (a 2D grid of state
labels). For the AIF controller, we need a summary vector. The natural summary:
the histogram of causal states (how many cells are in each state), plus the
spatial arrangement signal (domain/particle structure — see R2).

### R2 / OBSERVATION o → domain/particle statistics (the SeparatesEoC-valid EoC signal)

The controller observes the causal-state field's STRUCTURE each window. The
EVALUATOR-SPEC §3.8–3.9 defines the EoC signature as a CONJUNCTION read off the
causal-state field:

- **domainCoverage** — the fraction of the spacetime covered by large homogeneous
  causal-state regions (the regular spatiotemporal background). High for complex
  and frozen; low for chaotic.
- **particleSparsity** — the presence of sparse coherent boundaries between
  domains (gliders, walls). Nonzero for complex; zero for frozen; irrelevant for
  chaotic (no domains to bound).

**EoC signal = domainCoverage × particleSparsity** (the conjunction):
- complex (EoC): high coverage AND nonzero particles → high
- chaotic: low coverage → low
- frozen: high coverage but zero particles → low

This is the SeparatesEoC-valid signal the EVALUATOR-SPEC prescribes — NOT
`classify-regime` (which the evaluator-spec's fail-bank shows is presumed-invalid).

**API gap (the blocker — see below).** The causal-state field is produced by
`local_causal_states.clj`, but the domain/particle DECOMPOSITION (extracting
domainCoverage and particleSparsity from the field) is NOT yet implemented. The
EVALUATOR-SPEC §3.9 describes it as INSTANTIATE step (2), pending.

### R19 / PREFERENCE C → EoC confinement over causal-state structure

C targets the EoC conjunction: high domainCoverage AND nonzero particleSparsity.
The preference vector:

```
C = {:domain-coverage {:mean 0.7 :sd 0.15}
     :particle-sparsity {:mean 0.3 :sd 0.15}}
```

This replaces the macro-feature mid-band (pressure=0.5, selectivity=0.4, etc.)
with a structurally-grounded EoC target. The g-efe KL-risk measures how far the
predicted causal-state structure is from this EoC preference.

### R4 / FORWARD MODEL → causal-state conditional future morphs

The reconstructed model's `:future-probabilities` per state (L140) ARE the
forward-model predictions: for each causal state, the conditional distribution
over future light-cone values. This is the generative model's prediction.

**API:** `(:future-probabilities state)` → `{future-cone-value probability}`.

**Forward prediction:** to predict the next window's causal-state observation,
advance the CA step (via `runtime/run-mmca`), extract the new gen-history, run
`reconstruct` on the new spacetime, and read the domain/particle statistics from
the new field. This is tractable but EXPENSIVE (full CSSR reconstruction each
window).

**Cheaper proxy (v1):** apply the existing model's `past->state` map to the new
spacetime's light-cones (no re-clustering), then read the domain/particle
statistics. This is a fixed-model forward prediction — the model is trained once,
then applied to predict future windows. This mirrors how the evaluator uses the
field (train on a time-range, label the whole grid).

---

## THE MODELING QUESTION (escape hatch — build STOPPED)

The causal-state field is available (`local_causal_states.clj`), but the
**domain/particle decomposition** (the EoC signal the controller needs) is NOT
yet built. The EVALUATOR-SPEC §3.9 describes this as INSTANTIATE step (2), and
§3.8 flags a specific difficulty:

> For MetaCA — 256-valued, per-cell rules, evolving — the domains are UNKNOWN and
> must be DISCOVERED (ε-machine / regular-language inference), the genuinely hard
> part.

The existing `local_causal_states.clj` API:
- ✅ `reconstruct` — produces the causal-state model + field (DONE)
- ✅ `causal-state-field` — labels each point with its state (DONE)
- ❌ domain decomposition — identifying homogeneous causal-state regions (NOT BUILT)
- ❌ particle detection — finding coherent boundaries between domains (NOT BUILT)
- ❌ `domainCoverage × particleSparsity` — the EoC conjunction signal (NOT BUILT)
- ❌ SeparatesEoC validation — has not been run on MetaCA (spec says "INSTANTIATE
  ECA-first, then MetaCA domain-discovery as its own slice")

### Three specific questions for the owner:

1. **Should the EoC signal use a CAUSAL-STATE-derived metric (domain/particle
   decomposition) or a simpler causal-state summary (e.g. number of distinct
   causal states, spatial entropy of the state field)?** The full decomposition
   is the EVALUATOR-SPEC's prescribed signal, but it requires building domain
   detection on MetaCA (the hard part). A simpler proxy (causal-state count or
   spatial entropy of the field) is immediately available from the existing API
   but hasn't been SeparatesEoC-validated.

2. **Is the EoC signal SeparatesEoC-validated on MetaCA yet?** The EVALUATOR-SPEC
   explicitly says the EoC conjunction must pass the Rule-110 bar on ECAs first,
   then MetaCA domain-discovery as its own slice. If the signal isn't validated
   on MetaCA, building C on it is building on an unvalidated metric — the same
   trap the evaluator-spec was written to prevent.

3. **Should the forward model re-reconstruct the causal-state model each window
   (expensive but principled) or use a fixed trained model (cheaper, a proxy)?**
   The fixed-model approach is a principled approximation (the model captures the
   CA's structure, which is quasi-stationary over short windows), but it degrades
   if the CA's causal structure shifts significantly.

### Recommendation

The honest path: build the domain/particle decomposition on the causal-state
field (the EoC signal), validate it SeparatesEoC on at least one ECA anchor, THEN
wire it into the tokamak's C. Building C on an unvalidated EoC proxy would repeat
the `classify-regime` mistake the evaluator-spec was written to prevent.

This is a genuine modeling question for the owner, not something to force.
