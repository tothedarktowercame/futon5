# E-alpha-next-locus — RESULT (genotype 52c97bb)

**Run:** 2026-06-02 · n_episodes=111 (37 missions) · churn_window=30d · B=2000 · arm c (Newman+curvature) DEFERRED (κ not pre-registered).

## MAP per signal
- recency: 0.132  ← FLOOR
- churn: 0.080
- pref-attachment: 0.077
- common-neighbours: 0.097
- raw-co-mention(a): 0.053
- newman(b): 0.053  ← E1 (b)

## Gate
lift(newman(b) − floor[recency]) = **-0.079**, episode-bootstrap 95% CI [-0.128, -0.039].
PASS = CI excludes 0 AND point-lift ≥ 0.05 → **FAIL** (expected at pilot n — predictive verdict awaits going-forward data per genotype).

## Inert-diagnosis (why b == a ≈ random)
empty F_M(T): 30/111 episodes; flat-newman: 81/111; NON-DEGENERATE (mention signal usable): 0/111. The as-of-T mention signal is largely **inert at pilot scale** — F_M(T) is empty/static for most episodes (claude-3 caveat: 130/183 missions single-version), so newman(b) and raw-co-mention(a) both collapse to ~random, below the recency floor.

## Descriptive (GUARANTEED deliverable)
**INCONCLUSIVE** — 0 non-degenerate episodes; the mention signal is inert on this pilot, so intent-bridge extension cannot be characterized yet. Needs the temporally-rich mention subset (53 evolving missions) and/or going-forward data.

*Floor includes raw-co-mention (a), so newman(b) is tested as the INCREMENT over plain co-mention. The prior '1.00' descriptive was a zero-tie artifact (now fixed: strict-`<`, non-degenerate episodes only).*
