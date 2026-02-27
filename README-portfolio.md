# Portfolio Inference — How To Use

The portfolio inference system answers **"what should we work on?"** by
running an Active Inference loop over your mission portfolio. It tracks
what you planned (bids), what you actually did (clears), and uses the
discrepancy to improve future recommendations.

## Architecture

Three servers, one JVM (`make dev` in futon3c):

| Service | Port | What it does |
|---------|------|--------------|
| futon3c | 7070 | AIF loop, HTTP+WS transport, mission control |
| futon1a | 7071 | Evidence persistence (XTDB) |
| futon5  | 7072 | Heartbeat bid/clear persistence (SQLite) |

## Weekly Cycle

**Monday: Bid** — declare what you intend to work on this week.

```
POST http://localhost:7072/api/heartbeat/bid
Content-Type: application/json

{
  "week-id": "2026-W09",
  "bids": [
    {"action": "work-on", "mission": "M-portfolio-inference", "effort": "hard"},
    {"action": "review",  "mission": "M-futon-stack",          "effort": "easy"}
  ],
  "mode-prediction": "BUILD"
}
```

**Sunday: Clear** — record what actually happened.

```
POST http://localhost:7072/api/heartbeat/clear
Content-Type: application/json

{
  "week-id": "2026-W09",
  "clears": [
    {"action": "work-on", "mission": "M-portfolio-inference", "effort": "medium", "outcome": "complete"},
    {"action": "work-on", "mission": "M-improve-irc",         "effort": "easy",   "outcome": "partial"}
  ],
  "mode-observed": "CONSOLIDATE"
}
```

The system computes prediction errors from the bid/clear diff:
- **Action mismatches** — planned but not taken, or taken but not planned
- **Effort mismatches** — actual effort band differs from predicted
- **Outcome mismatches** — partial or abandoned vs. complete
- **Mode mismatch** — predicted BUILD but observed CONSOLIDATE

These errors feed back into the AIF loop (T-7 channels) to improve
future effort estimates and mode predictions.

## Effort Bands

| Band | Hours (configurable) | Description |
|------|---------------------|-------------|
| trivial | < 1h | Quick fix, config change |
| easy | 1–3h | Small feature, focused session |
| medium | 3–8h | Substantial work, one day |
| hard | 8–20h | Multi-day effort |
| epic | 20h+ | Major undertaking |

The thresholds are configurable in Emacs via `nonstarter-effort-band-thresholds`.

## Running an AIF Step

Ask the system for a recommendation:

```
POST http://localhost:7070/api/alpha/portfolio/step
Content-Type: application/json

{}
```

Response:

```json
{
  "action": "review",
  "diagnostics": {
    "mode": "CONSOLIDATE",
    "urgency": 0.54,
    "tau": 1.38,
    "free-energy": 0.0444
  },
  "policy": {
    "policies": [
      {"action": "review",       "G": -0.520, "probability": 0.237},
      {"action": "consolidate",  "G": -0.340, "probability": 0.208},
      {"action": "wait",         "G": -0.277, "probability": 0.199}
    ]
  }
}
```

## Running a Heartbeat (Bid+Clear → AIF Step)

Combine bid/clear data with an AIF step:

```
POST http://localhost:7070/api/alpha/portfolio/heartbeat
Content-Type: application/json

{
  "bids": [{"action": "work-on", "mission": "M-foo", "effort": "hard"}],
  "clears": [{"action": "work-on", "mission": "M-foo", "effort": "medium", "outcome": "complete"}],
  "mode-prediction": "BUILD"
}
```

This runs the full pipeline: compute action errors → enrich observation
channels → run AIF step → return recommendation with prediction error
analysis.

## Querying Current State

```
GET http://localhost:7070/api/alpha/portfolio/state
```

Returns the current belief state: mode, urgency, tau, sensory predictions,
precision weights.

## Modes

The system operates in one of three modes:

| Mode | When | Recommends |
|------|------|------------|
| BUILD | Low coverage, many gaps | work-on, review |
| MAINTAIN | Good coverage, few stalls | review, wait |
| CONSOLIDATE | High spinoff pressure, stale reviews | consolidate, review |

Transitions require the condition to hold for **2 consecutive observations**
(hysteresis prevents oscillation).

## Observation Channels (15)

The AIF loop observes 15 normalized [0,1] channels:

**Portfolio state (12):**
mission-complete-ratio, coverage-pct, coverage-trajectory,
mana-available, blocked-ratio, evidence-velocity,
dependency-depth, gap-count, stall-count,
spinoff-pressure, pattern-reuse, review-age

**Heartbeat-derived (3, added T-7):**
effort-prediction-error, bid-completion-rate, unplanned-work-ratio

When no heartbeat data is available, the 3 heartbeat channels default to
neutral values (0.0, 0.5, 0.0) — the system degrades gracefully.

## Emacs UI (nonstarter.el)

In the Nonstarter dashboard:

| Key | Command | What it does |
|-----|---------|--------------|
| `p` | `nonstarter-portfolio-step` | Run AIF step, show recommendation |
| `P` | `nonstarter-portfolio-bid-form` | Interactive bid form (mission, action, effort) |
| `C` | `nonstarter-portfolio-clear-form` | Interactive clear form (pre-populated from bids) |

The dashboard shows a Portfolio section with current AIF mode, urgency,
recent heartbeat activity, and top action recommendations.

The personal bid form (`M-x nonstarter-personal-bid-form`) automatically
emits portfolio bids via T-8 compression when
`nonstarter-portfolio-category-mission-map` is configured.

## Configuration (futon5a)

In `config/nonstarter-personal.el`:

```elisp
;; Map personal categories to portfolio missions
(setq nonstarter-portfolio-category-mission-map
      '(("systems"    . "M-futon-stack")
        ("creative"   . "M-creative-practice")
        ("consulting" . "M-consulting")))

;; Available missions for bid forms
(setq nonstarter-portfolio-missions
      '(("M-portfolio-inference" . "Portfolio AIF loop")
        ("M-mission-control"     . "Mission Control peripheral")))
```

## Starting Up

```bash
cd futon3c
make dev
```

This starts all three services in one JVM. You'll see:

```
[dev] futon1a: http://localhost:7071 (XTDB persistence)
[dev] futon5 heartbeat API: http://localhost:7072
[dev] futon3c: http://localhost:7070
```

After a restart, the portfolio state atom resets to defaults (BUILD mode,
all predictions at 0.5). Run a `portfolio-step!` to re-observe current
portfolio state and the system will converge within 2-3 steps.

## Troubleshooting

**CONSOLIDATE won't transition to BUILD:**
The exit condition is `gap-count > 0.5 AND spinoff-pressure < 0.3`.
If spinoff-pressure is saturated (too many "no mission" gaps relative
to the cap of 5), either triage missions to reduce spinoffs or raise
the spinoff-cap in `observe/default-priors`.

**coverage-pct reads 0:**
mc-coverage's mission-to-devmap matching may not be working. Check
`mc-backend/build-portfolio-review` output for non-zero coverage data.

**review-age stuck at 1.0:**
No prior review evidence in the store. Run a portfolio step with
evidence emission enabled — future steps will have a baseline.
