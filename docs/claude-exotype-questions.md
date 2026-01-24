# Exotype follow-ups for Claude

Date: 2026-01-24

## Open question: `:bit-bias`

We added `:template-strictness` (probabilistic gating of template overrides) but did not interpret `:bit-bias` yet.

Current options considered:
- Bias output bits toward 1/0 (yang vs preserve).
- Bias toward previous generation (momentum) or current sigil bits (selective).

None of these mappings are clearly justified. Need a principled semantic mapping for:
`:yang`, `:preserve`, `:selective`, `:momentum`.

Where should this live (kernel spec vs context), and how should it affect CA evolution?
