# Nonstarter Mission 1: Pattern Economy + AIF Transfer

Date: 2026-01-25

## Mission intent

Build a first in-house integration between futon3a (portal + sidecar + memes)
and futon3 (MUSN/HUD), with futon5/nonstarter as the policy simulator and
governance layer. Replace money with **mana** as the internal currency.

This mission assumes DP sequencing is available as a planning option.

## Core assumptions

- futon3 remains the authoritative MUSN session log.
- futon3a provides richer candidate retrieval + meme/proposal workflow.
- nonstarter can simulate policy outcomes and gate actions by mana.
- DP can be used to sequence pattern choices across a short horizon.

## Integration agenda (baseline)

- Pattern selection: use futon3a Portal to retrieve richer candidates (ANN + flexiarg fields) and feed them into futon3’s MUSN session/HUD.
- Logging: keep futon3’s HTTP MUSN as the authoritative session log; mirror futon3a’s sidecar events (proposals/promotions/arrows) as adjunct metadata.
- Meme layer: adopt futon3a’s proposal/promotion workflow for new memes and decisions; futon3 can emit these as a post-turn action.
- Compass/GFE path: use futon3a’s preference extraction as the AIF state, then plug futon5 nonstarter as the policy simulator (per compass-demonstrator-plan.md).

## Mana economy (replacement for money)

### Ledger

- Per-session mana budget with explicit credits and debits.
- Credits: validated evidence, successful pattern use, accepted proposals.
- Debits: tool calls, off-trail actions, ungrounded updates, failed evidence.

### Gating

- Phase 1: soft penalties (warnings, reduced priority).
- Phase 2: hard gates (block actions below a mana floor).

### Audit

- Every action in MUSN should include mana delta and reason.
- Summary report includes mana balance and top drains/earners.

## DP sequencing (optional)

Use DP to select a short sequence of patterns (2–3 steps):

- State: belief summary + constraints + mana.
- Action: pattern choice.
- Cost: expected free energy + context switch + mana penalty.
- Output: ranked sequence + softmax for first step.

## Deliverables (Mission 1)

1) Draft adapter contract between futon3a portal output and futon3 MUSN input.
2) Sidecar mirror format for proposals/promotions/arrows (adjunct metadata).
3) nonstarter mana ledger schema and summary format (no gating yet).
4) A minimal DP selector stub using existing AIF signals.
5) One end-to-end demo trace showing: portal retrieval → MUSN PSR/PUR → sidecar proposal → nonstarter ledger update.

## Success criteria

- futon3 sessions ingest futon3a candidates without breaking HUD flows.
- Sidecar events are mirrored in futon3 logs or attached as adjunct metadata.
- Mana ledger records deltas for at least plan/select/action/use per turn.
- DP selector can propose a sequence without overriding human choice.

## Risks / open questions

- How to reconcile Drawbridge-only futon3a with HTTP MUSN in futon3.
- Evidence quality: what qualifies as a mana credit vs a penalty.
- Pattern retrieval drift between ANN and explicit pattern selection.
- How to keep the integration minimal while still proving value.
