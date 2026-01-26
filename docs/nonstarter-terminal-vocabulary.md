# Nonstarter Terminal Vocabulary (Draft)

Date: 2026-01-25

This vocabulary defines the **terminal actions and signals** for the Nonstarter
domain. It complements `docs/nonstarter-mission-1.md` and provides a common
surface for futon3a ⇄ futon3 ⇄ futon5 integration.

The goal is to make Nonstarter auditable: every run can be decomposed into
these terminal events, even if higher-level reasoning is richer.

## Scope

- Applies to the Nonstarter simulation / policy layer in futon5.
- Works with futon3 (MUSN/HUD) and futon3a (portal/sidecar/meme).
- Uses **mana** as the internal currency (no money in this layer).

## Core Cycle (one turn)

```
Session start
  → intent + scope
  → proposal + cost estimate (step 0)
  → candidate retrieval (portal)
  → pattern selection (PSR)
  → policy simulation (nonstarter)
  → action execution (pattern action)
  → evidence + outcome
  → belief update (AIF / compass)
  → pattern use (PUR)
  → turn end
```

## Terminals

Each terminal is a minimal event with required fields.

### Session / Scope

- `session/start`
  - Fields: `:session/id`, `:intent`, `:scope/in`, `:scope/out`, `:scope/exit`
- `session/intent-handshake`
  - Fields: `:session/id`, `:intent`, `:constraints`, `:success-criteria`

### Candidate Retrieval (Portal)

- `portal/candidates`
  - Fields: `:session/id`, `:intent`, `:candidates` (vector of pattern ids)
  - Optional: `:candidate-details` (flexiarg summaries, ANN scores)

### Pattern Selection / Use

- `psr/select` (Pattern Selection Record)
  - Fields: `:session/id`, `:turn`, `:candidates`, `:chosen`
  - Optional: `:aif` (G scores, τ), `:reason`

- `pur/use` (Pattern Use Record)
  - Fields: `:session/id`, `:turn`, `:pattern/id`
  - Optional: `:anchors`, `:note`, `:aif` (prediction error, τ update)

### Policy Simulation (Nonstarter)

- `policy/simulate`
  - Fields: `:session/id`, `:turn`, `:policy/id`, `:inputs`
  - Optional: `:outputs`, `:score` (G, pragmatic, epistemic)

- `policy/recommend`
  - Fields: `:session/id`, `:turn`, `:policy/id`, `:confidence`

### Proposal + Cost Estimation (Step 0)

- `proposal/estimate`
  - Fields: `:session/id`, `:proposal/id`, `:proposal/title`
  - Optional: `:estimate/mana`, `:estimate/breakdown`, `:estimate/method`

- `proposal/submit`
  - Fields: `:proposal/id`, `:proposal/title`, `:proposal/ask`
  - Optional: `:proposal/description`, `:proposal/sigil`, `:proposal/proposer`

Notes:
- `:proposal/ask` is **mana**, not money, in this layer.

### Evidence / Outcomes

- `evidence/add`
  - Fields: `:session/id`, `:turn`, `:pattern/id`, `:files`
  - Optional: `:note`, `:tool-uses`

- `outcome/observe`
  - Fields: `:session/id`, `:turn`, `:result`
  - Optional: `:tests`, `:lint`, `:diff`

### Meme / Sidecar

- `meme/proposal`
  - Fields: `:proposal/id`, `:proposal/kind`, `:proposal/target-id`,
    `:proposal/score`, `:proposal/method`, `:proposal/evidence`

- `meme/promotion`
  - Fields: `:promotion/id`, `:proposal/id`, `:promotion/kind`,
    `:promotion/decided-by`, `:promotion/rationale`

- `meme/arrow`
  - Fields: `:arrow/id`, `:arrow/source`, `:arrow/target`,
    `:arrow/mode`, `:arrow/confidence`

#### Sidecar mirror envelope (EDN)

All sidecar events are wrapped in a single envelope so they can be stored
and audited uniformly:

```clojure
{:event/id "evt-123"
 :event/type :meme/proposal
 :session/id "sess-2026-01-25-01"
 :turn 3
 :source {:system :portal :method "ann"}
 :payload {:proposal/id "prop-001"
           :proposal/kind :pattern
           :proposal/target-id "futon3a/P1"
           :proposal/score 0.72
           :proposal/method "ann"
           :proposal/evidence {:intent "draft plan"}}}
```

The adapter should store `:event/type` as the sidecar `event_type` and the
entire map as the `payload` (EDN string) for lossless auditing.

### Hypotheses / Studies

- `hypothesis/register`
  - Fields: `:hypothesis/id`, `:hypothesis/title`, `:hypothesis/statement`
  - Optional: `:hypothesis/context`, `:hypothesis/status`

- `hypothesis/update`
  - Fields: `:hypothesis/id`, `:hypothesis/status`

- `study/preregister`
  - Fields: `:study/id`, `:hypothesis/id`, `:study/name`
  - Optional: `:study/design`, `:study/metrics`, `:study/seeds`, `:study/status`

- `study/result`
  - Fields: `:study/id`
  - Optional: `:study/results`, `:study/status`, `:study/notes`

### Mana (Nonstarter Currency)

- `mana/credit`
  - Fields: `:session/id`, `:turn`, `:amount`, `:reason`
  - Typical reasons: `:evidence-validated`, `:proposal-accepted`, `:pattern-complete`

- `mana/debit`
  - Fields: `:session/id`, `:turn`, `:amount`, `:reason`
  - Typical reasons: `:tool-call`, `:off-trail`, `:unanchored-update`

- `mana/balance`
  - Fields: `:session/id`, `:turn`, `:balance`, `:earned`, `:spent`

### Turn End

- `turn/end`
  - Fields: `:session/id`, `:turn`, `:summary`
  - Optional: `:mana/balance`, `:policy/recommendation`, `:open-questions`

## DP Sequencing (optional)

If dynamic programming is enabled, emit:

- `dp/sequence`
  - Fields: `:session/id`, `:turn`, `:sequence`, `:costs`, `:horizon`

Where:
`sequence` = vector of pattern ids, `costs` = per-step G + penalties,
`horizon` = steps evaluated (e.g., 2–3).

## Required Minimal Fields

For interoperability, the minimum required identifiers are:

- `:session/id`
- `:turn`
- `:pattern/id` (for PSR/PUR/evidence)
- `:proposal/id` (for meme events)

## Notes

- Nonstarter uses **mana** as an internal budget; money is not modeled here.
- Use portal candidates as priors; do not overwrite MUSN logs.
- This vocabulary intentionally mirrors futon3 PSR/PUR so the logs can align.
