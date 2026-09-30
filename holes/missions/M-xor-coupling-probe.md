# Mission: XOR Coupling Probe

**Date:** 2026-02-18
**Status:** OPEN — OPEN — DERIVE (XOR operator implemented, probe results in, wired into TPG)
**Blocked by:** None

## Acceptance checklist (2026-09-30)

- [x] `scripts/probe_xor_vs_add.clj` records XOR and carry-chain mutual-information and diversity measurements sufficient to test the stated tradeoff. (evidence: this file, Summary)
- [x] `sigil-xor` is implemented in `src/futon5/xenotype/generator.clj`, registered in `resources/xenotype-generator-components.edn`, and represented by `data/wiring-rules/hybrid-110-xorself.edn`. (evidence: this file, Key Files)
- [ ] A completed 20-generation XOR-enabled TPG run has a durable result record containing its coupling, diversity, and evolution outcome.
- [ ] This mission records an adopt, revise, or reject decision for XOR coupling by comparing the completed evolution run with the baseline and carry-chain results.

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
