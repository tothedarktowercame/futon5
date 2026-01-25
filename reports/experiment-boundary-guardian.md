# Experiment: Boundary Guardian Wiring

Date: 2026-01-25
Author: Claude Code

## Configuration

- **Legacy baseline**: 工 (update-prob=0.5, match-threshold=0.44)
- **Prototype-001**: creative-peng graph-based wiring
- **Boundary Guardian**: stability-biased hybrid (legacy base + creative XOR in moderate diversity zone)
- **Seeds**: 4242, 238310129, 352362012
- **Length**: 100, **Generations**: 100

## Key Finding: All Systems Start Hot, Then Settle

Every wiring starts with change rate ~0.98 (initial randomization), then settles:

| Wiring | Final change rate | Settling behavior |
|--------|-------------------|-------------------|
| Legacy 工 | 0.22-0.26 | Moderate activity |
| Boundary Guardian | 0.19-0.24 | Similar to legacy |
| Prototype-001 | 0.10-0.14 | **Settles lower** than legacy |

### Contradiction with Codex's Report

Codex reported prototype-001 having avg-change=0.995 (99.5% chaotic). My runs show it **settles to 0.10-0.14** by the end — actually MORE frozen than legacy.

Possible explanations:
1. Different run parameters (length/generations)
2. Different measurement point (early vs late)
3. Different metrics (per-generation vs overall)

## Vertical Band Analysis

| Wiring (seed 4242) | Moderate % | Chaotic % | Band Score |
|--------------------|------------|-----------|------------|
| Legacy 工 | 7% | 14% | 0.070 |
| Boundary Guardian | 8% | 13% | 0.080 |
| Prototype-001 | **26%** | 0% | **0.260** |

Surprisingly, **prototype-001 has the best band metrics** on seed 4242:
- Highest moderate column percentage (26% vs 7-8%)
- Zero chaotic columns
- Highest band score (0.260)

On seed 352362012:
- Prototype-001: 31% moderate, band score 0.310 ("has-active-bands")
- Legacy: 18% moderate, band score 0.180
- Boundary Guardian: 7% moderate, band score 0.070

## Interpretation

The Boundary Guardian performs **similarly to legacy 工** — which makes sense since it uses 工 as its base. The creative XOR path may not be triggering often enough to differentiate.

The prototype-001 wiring, contrary to expectations, produces **more moderate columns** and **lower chaotic activity** than legacy. It settles into more structured patterns, not chaos.

## What We Learned

1. **"Hot" is transient**: All systems start hot but settle. Early-generation metrics can be misleading.

2. **Prototype-001 isn't broken**: It actually produces better band metrics than legacy on these seeds.

3. **Boundary Guardian needs tuning**: The diversity threshold (0.3-0.7) may not be optimal. It's behaving almost identically to pure legacy.

4. **Metrics timing matters**: Per-generation change rate vs final-state analysis give very different pictures.

## Next Steps

1. **Investigate early vs late dynamics**: What happens in the first 20 generations that causes the cooling?

2. **Tune Boundary Guardian thresholds**: The 0.3-0.7 diversity band may need adjustment.

3. **Visual comparison**: These metrics suggest prototype-001 might actually be producing EoC — need visual confirmation.

4. **Reconcile with Codex**: Different results suggest different experimental conditions — need to align.
