# Technote: Sospeso Protocol for Mana & Confidence

Date: 2026-01-25

## Purpose

Define a **confidence-triggered micro‑protocol** that converts agent caution
into structured action, while funding future review via a suspended pool
("sospeso"). This supports the Nonstarter mana model: **donations (dana) fill
the global pool; votes carry precision**. The sospeso contribution is a labeled
pool donation intended to finance assurance work.

## Core idea

When confidence is not high enough, the agent must **either rescope** or **pay
sospeso**. There are only two exits:

1) Rescope until confidence is high enough, or
2) Proceed with a sospeso tax that funds future correction/review.

This turns “safety/compliance” into an engine for **negative‑space artifacts**
(checks, rollback paths, audit notes) instead of slop.

## Confidence-triggered action policy

For any action with estimated cost `C`:

1. State the action in one sentence.
2. Estimate confidence `p ∈ {0.3, 0.6, 0.8, 0.95}` (discrete bins reduce
   self‑deception).
3. If `p < 0.95`, you must choose exactly one:

### A) Rescope-to-safety

Reduce scope until **one** becomes true:

- You can add a check/test that makes the move reversible, or
- You can confine it behind a flag, or
- You can reduce it to read‑only inspection / notes.

Re‑estimate `p`. Repeat at most twice.

### B) Blast-radius sospeso

- Spend `p·C` on the action.
- Donate `(1−p)·C` into the suspended pool with label: `ASSURANCE_REQUIRED`.
- Produce a Blast Radius Note (below).
- Proceed **only if** the action is reversible or guarded; otherwise defer.

## Blast Radius Note (required when using sospeso)

- **What I’m doing:** (1 sentence)
- **Why I’m not fully confident:** (1–2 bullets)
- **Blast radius:** (files/surfaces touched)
- **Failure modes:** (2–4 concrete ways it could go wrong)
- **Containment:** (tests/flags/assertions/rollback)
- **What the sospeso is for:** (review, alternate approach, additional evidence)

## Why it works (behavioral shape)

- “Estimate confidence” becomes a **compliance hook**.
- “Rescope or pay sospeso” converts hesitation into structure.
- The only way to proceed at full speed is to **make the move safer**.

## Enforcement guardrail

Use a default threshold (e.g., **0.95**). If the agent claims ≥0.95, require a
short justification line: e.g., **“validated by X”** or **“reversible by Y.”**

This prevents habitual “0.99” inflation.

## AIF alignment

- **Donations (dana)** expand feasibility (resource channel).
- **Votes** update precision (preference channel).
- **Sospeso** is a labeled donation that explicitly funds future assurance.

## Minimal event mapping (optional)

```clojure
{:event :mana/credit
 :session/id "sess-2026-01-25-01"
 :turn 3
 :amount 0.2
 :reason :assurance-required
 :note "sospeso: re-review core parser change"}
```

## Status

Draft. Intended to be referenced by fucodex policy prelude or mission templates.
