# Meta-Review: Claude Code vs Codex Peer Programming

Date: 2026-01-25

## Overview

Since starting the peer programming experiment, ~36 commits have been made. Roughly:
- **Claude Code**: ~22 commits (identified by "Claude Opus 4.5" co-author tag)
- **Codex**: ~14 commits

## Work Patterns

### Claude Code (Strategy/Design Focus)

**Strengths**:
- Documentation and methodology (AGENTS.md, notebooks, README files)
- Analysis tool creation (barcode_detector, vertical_band_analysis, run_health_report)
- Design principles (wiring-design-principles-from-good-bands.md)
- High-level strategy (wiring ladder, compositional build-up approach)
- Health classification system with persistent reporting

**Characteristic commits**:
- "Add experimental methodology to AGENTS.md"
- "Add notebook: EoC detection and wiring design from first principles"
- "Add wiring ladder for compositional build-up testing"
- "Add universal run health classification"

**Tendency**: Creates frameworks, documents "why", proposes experiments

### Codex (Implementation/Execution Focus)

**Strengths**:
- Runner infrastructure (xenotype_portrait_run.clj, known-good runset harness)
- Actually runs batches and logs results
- Classifier improvements and validation
- Bug fixes and refactoring
- Validation tests for detection tools

**Characteristic commits**:
- "add xenotype portrait runner"
- "known-good runset harness and report"
- "improve classifier hotness and eigen fallback"
- "separate barcode from periodicity; validate bands"

**Tendency**: Builds infrastructure, runs experiments, validates tools

## Collaboration Examples

### Good: Tool Creation → Validation
1. Claude Code created `barcode_detector.clj` with vertical freeze detection
2. Codex modified it (fe1ec24), separating barcode from periodicity and adding validation
3. Result: Better tool with validation tests

### Good: Methodology → Execution
1. Claude Code wrote AGENTS.md with experimental methodology
2. Codex created `mission17a-seq-20-notebook.md` following the format
3. Result: Consistent reporting across experiments

### Gap: Learning Loop
1. Claude Code created wiring outcome registry and health reports
2. Codex running experiments but not yet systematically populating registry
3. Gap: No closed loop connecting outcomes back to wiring design

## Key Observations

### Different Failure Mode Coverage
- **Claude Code** focused on detecting barcode collapse (frozen columns)
- **Codex** worked on hot/entropy detection and eigenvector fallbacks
- **Gap**: We haven't systematically compared runs from both failure modes

### Documentation vs Execution Balance
- Claude Code: ~60% documentation/design, ~40% scripts
- Codex: ~20% documentation, ~80% implementation/running
- This is complementary but means documentation can drift from reality

### The "Wiring Soup" Problem
Both agents have been testing wirings without systematic build-up:
- Codex ran prototype-001, hybrid experiments
- Claude Code created boundary-guardian-001
- Neither decomposed wirings to understand component contributions
- **Wiring ladder** is the proposed fix (not yet run)

## Metrics Comparison

| Aspect | Claude Code | Codex |
|--------|-------------|-------|
| Commits | ~22 | ~14 |
| New scripts | 6 | 4 |
| New reports | 8 | 4 |
| Documentation | High | Low |
| Experiments run | Few | Many |
| Validation tests | Created tools | Validated tools |

## Recommendations

### For Claude Code
1. **Run more experiments** - Don't just design, execute
2. **Check Codex's modifications** - Tools may have evolved
3. **Less documentation sprawl** - Consolidate notebooks

### For Codex
1. **Populate the registries** - Use wiring-outcomes.edn, persist health reports
2. **Document learnings** - What did the experiments teach?
3. **Run the wiring ladder** - Systematic build-up before more complex wirings

### For Both
1. **Close the learning loop** - Outcomes must feed back to design
2. **Share a common vocabulary** - Hot/barcode/EoC classifications
3. **Coordinate on wiring exploration** - Don't test overlapping configurations

## What's Working

1. **Complementary skills** - Design + execution
2. **Tool improvement cycle** - Create → validate → improve
3. **Shared infrastructure** - Both using same scripts and reports
4. **Persistent artifacts** - Reports in git, not just /tmp

## What's Not Working

1. **No actual EoC found** - 0 EoC runs, 2 COOLING, 23+ BARCODE
2. **Learning loop not closed** - Outcomes not driving new designs
3. **Wiring space unexplored** - Testing complex things without understanding components
4. **Hot runs not reproduced** - Codex reported 0.995 change rate, Claude Code couldn't reproduce

## Next Steps

1. Codex runs wiring ladder experiment (systematic build-up)
2. Claude Code analyzes existing wirings for structural features
3. Both populate wiring-outcomes.edn with results
4. Identify first actual EoC or understand why none exist
