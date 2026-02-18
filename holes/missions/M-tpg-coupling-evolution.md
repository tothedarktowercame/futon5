# Mission: TPG Coupling Evolution

**Date:** 2026-02-15
**Status:** MAP (production run complete, diversity-coupling tradeoff identified)
**Blocked by:** None

## Summary

Verifier-guided TPG (Tangled Program Graphs) for MMCA meta-evolution.
Replaces flat xenotype layer with conditional operator routing based on
locally computed diagnostic features.

Production run (50 gen, 16+16 pop) achieved best satisfaction 0.753 but
revealed fundamental diversity-coupling tradeoff (Pivot 13): coupling
easily scales (MI=1.0 by gen 12) but suppresses diversity (0.89 to 0.36).
Phenotypic barcode collapse persists.

XOR coupling probe (M-xor-coupling-probe) demonstrated the tradeoff is
carry-chain-specific, not fundamental. XOR now wired into the TPG
operator set for the next evolution run.

## Key Files

- `docs/technote-verifier-guided-tpg.md`
- `scripts/tpg_coupling_evolve.clj`
- `scripts/tpg_coupling_report.clj`
- `scripts/tpg_coupling_stability.clj`
- `scripts/tpg_evolve_production.clj`
- `scripts/tpg_evidence_bundle.clj`
- `out/tpg-evo-production/`
