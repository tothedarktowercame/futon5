# Propagator-space orbit gate

**Decision: index 20,256 left–right mirror orbits, covering all 40,320 σ. Do
not quotient by 0↔1 complement.**

The legacy truth-table order is `[000 001 010 100 011 101 110 111]`. Spatial
reflection therefore acts on its indices as `[0 3 2 1 6 5 4 7]`, and on a
propagator by conjugation `m ∘ σ ∘ m`.

This was checked pathwise, not inferred from similar noisy scores. For six σ ×
four seeds, the probe:

1. generated an explicit initial genotype and phenotype;
2. reflected space and every rule's neighbourhood-response bits;
3. conjugated σ;
4. reflected every scripted mutation choice, including the original engine's
   unusual head, tail, then interior evaluation order; and
5. compared every genotype and phenotype row for 24 generations.

All 24 mirror checks were byte-exact for both fields. This supplies an explicit
conjugacy witness for the original 2014 engine, so mirror-related σ have the
same distribution under its reflection-invariant random initialization. Their
composition censuses differ only by a known ECA rule-axis relabelling.

The identical test for neighbourhood/state complement failed in all 24 runs:
the genotype first differs at generation 1 every time, and the phenotype at
generation 1–3. The reason is structural, not sampling noise: the original
engine uses fixed Rule-0 genotype and state-0 phenotype boundaries, which are
not invariant under 0↔1 complement. Complement is therefore not an admissible
quotient.

The mirror involution fixes 192 permutations. Burnside's lemma gives
`(40320 + 192) / 2 = 20256` orbits. The machine-readable evidence is
`data/propagator-index/orbit-proof.edn`; the executable witness is
`scripts/propagator_orbit_probe.el`.
