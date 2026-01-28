## Linting and validation

clj-kondo: clj-kondo --lint src
learning-loop: scripts/learning_loop_compliance.sh /tmp/experiment-name

## Sospeso Protocol (confidence-triggered mana generation)

When confidence is not high enough, you must **either rescope** or **pay sospeso**.
This turns uncertainty into funding for future review/assurance work.

### For any non-trivial action:

1. State the action in one sentence
2. Estimate confidence `p ∈ {0.3, 0.6, 0.8, 0.95}` (discrete bins reduce self-deception)
3. If `p < 0.95`, choose exactly one:

**A) Rescope-to-safety** — reduce scope until one becomes true:
- You can add a check/test that makes the move reversible, or
- You can confine it behind a flag, or
- You can reduce it to read-only inspection / notes

Re-estimate `p`. Repeat at most twice.

**B) Pay sospeso** — if you must proceed at lower confidence:
- Spend `p·C` on the action (where C is estimated cost in mana)
- Donate `(1−p)·C` to the pool (pure dana, not earmarked)
- Produce a Blast Radius Note (below)
- Proceed **only if** the action is reversible or guarded

### Blast Radius Note (required when using sospeso)

```
- What I'm doing: (1 sentence)
- Why I'm not fully confident: (1–2 bullets)
- Blast radius: (files/surfaces touched)
- Failure modes: (2–4 concrete ways it could go wrong)
- Containment: (tests/flags/assertions/rollback)
```

### Recording sospeso

After completing work with sospeso, record the gift:

```bash
clj -M -m scripts.nonstarter-mana sospeso \
  --db data/nonstarter.db \
  --action "brief action description" \
  --confidence 0.8 \
  --cost 1.0
```

This donates `(1-p)·C` to the pool (pure dana).

### Non-trivial action gate

Apply the sospeso protocol when the action is non-trivial:
- Schema changes, core parser changes, API changes
- Multi-file refactors or dependency changes
- External side-effects (deploys, data migrations, irreversible actions)

### Claiming p ≥ 0.95

If you claim 95% confidence, state why in one line:
- "validated by test X" or
- "reversible by Y" or
- "read-only inspection"

This prevents habitual inflation.

## MMCA work requires mana

MMCA experiments (hexagram runs, xenotype studies) are gated by available mana.
Before starting MMCA work, check the pool:

```bash
clj -M -m scripts.nonstarter-mana pool --db data/nonstarter.db --format text
```

If insufficient mana, do other work first (which generates mana via sospeso).
