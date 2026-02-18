# Mission: Coupling as Constraint

**Date:** 2026-02-18
**Status:** Ready
**Blocked by:** M-xor-coupling-probe, M-tpg-coupling-evolution

## Summary

Reformulate TPG fitness so coupling is a floor constraint (>= 0.05) rather
than a maximized band; make diversity the primary objective. Test whether
the Pareto front structure changes under constrained formulation.

This is a follow-up to the diversity-coupling tradeoff resolution. The XOR
probe showed coupling and diversity can coexist; this mission tests whether
the verifier framework can be reconfigured to exploit that finding.

Estimated compute: 2-3 hours.

## Key Files

- `scripts/tpg_coupling_evolve.clj` (modify verifier-spec)
- `docs/technote-verifier-guided-tpg.md` (constraint formulation)
