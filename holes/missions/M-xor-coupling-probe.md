# Mission: XOR Coupling Probe

**Date:** 2026-02-18
**Status:** DERIVE (XOR operator implemented, probe results in, wired into TPG)
**Blocked by:** None

## Summary

Test XOR-based cross-bitplane coupling as alternative to carry-chain
arithmetic. Hypothesis: XOR couples bitplanes without synchronizing them
(preserves entropy while introducing structure).

Results: XOR produces 4x more coupling (MI=0.019) than baseline with only
modest diversity cost (0.784 vs 0.845). Carry-chain add-self produces
MI=0.005 but collapses diversity to 0.36. The diversity-coupling tradeoff
is carry-chain-specific, not fundamental.

sigil-xor operator implemented and wired into TPG evolution operator set.
20-gen XOR-enabled evolution run in progress.

## Key Files

- `scripts/probe_xor_vs_add.clj`
- `src/futon5/xenotype/generator.clj` (sigil-xor operator)
- `resources/xenotype-generator-components.edn` (sigil-xor component def)
- `data/wiring-rules/hybrid-110-xorself.edn`
