# Mission 0: Pre-Part II CHECKPOINT

Date: 2026-01-24

## Status: Invariants Verified

All four invariants from `Mission-0-CHECKPOINT-invariants.md` now pass:

```
=== INVARIANT 1: Exotypes Are Real ===
  36-bit context has all components... PASS
  Context produces valid physics rule... PASS
  Different contexts produce different rules... PASS
  evolve-string-local tracks per-cell rules... PASS

=== INVARIANT 2: 256 Physics Rules ===
  256 rules = 64 hexagrams × 4 energies... PASS
  hexagram+energy -> rule roundtrips correctly... PASS
  Each rule maps to a valid kernel spec... PASS

=== INVARIANT 3: No Mixing Old and New ===
  *exotype-system* dynamic var exists... PASS
  with-local-physics binds correctly... PASS
  assert-local-physics! enforces local physics... PASS
  run-metadata includes exotype-system... PASS

=== INVARIANT 4: Local Physics Mode ===
  run-mmca supports :exotype-mode :local-physics... PASS
  Result includes exotype-metadata... PASS
  Result tracks local-physics-runs... PASS

ALL INVARIANTS PASSED
```

## What Was Implemented

### Hexagram Lift (`hexagram/lift.clj`)
- Eigenvalue-based hexagram extraction (not just diagonal)
- 256 physics rules = 64 hexagrams × 4 primary energies
- `context->physics-rule` for full 36-bit → rule mapping

### Exotype Module (`mmca/exotype.clj`)
- `build-local-context` — assemble 36-bit context
- `rule->kernel-spec` — map rule to kernel + params
- `evolve-sigil-local` — per-cell physics
- `evolve-string-local` — full string with per-cell rules
- Composition operators (sequential, blend, matrix)
- Global + local bending (`make-bent-evolution`)
- Runtime assertions (`*exotype-system*`, `assert-local-physics!`)
- Deprecation on `apply-exotype`

### Runtime (`mmca/runtime.clj`)
- `:exotype-mode :local-physics` support
- `advance-world-local-physics` using new system
- `:global-rule` and `:bend-mode` options
- Run metadata includes `:exotype-system` and `:exotype-metadata`

### Xenoevolve (`mmca/xenoevolve.clj`)
- `evolve-global-rules` — evolve over 256-rule space
- Defaults to new mode (legacy via `--legacy` flag)

## Discovery: Wiring System Now Extended

The cyber-mmca code included a wiring system for scorers. We've now extended it:

| Aspect | Before | After |
|--------|--------|-------|
| Scorer components | ✓ | ✓ |
| Generator components | ✗ | ✓ 50+ components |
| CT vocabulary | Exists, not connected | Still not connected |
| Mermaid rendering | ✗ | ✓ `xenotype/mermaid.clj` |
| Prototype wirings | ✗ | ✓ 1 prototype |

**Next**: Define more xenotype wirings and connect to CT validation.

## Conceptual Shift: Xenotypes as Security Layer

The session revealed that xenotypes are not just "fitness functions" or "global rules" but the **security/escalation layer**:

| Layer | Mode | Purpose |
|-------|------|---------|
| Local exotype | Normal | Per-cell physics, default trust |
| Xenotype dormant | Monitoring | Tripwires watching |
| Xenotype active | Escalation | Validation required |

The four primary energies map to escalation responses:
- Péng → Quarantine (boundary)
- Lǚ → Hold (yield without adopting)
- Jǐ → Scrutinize (mechanism test)
- Àn → Pressure (warrant test)

## Vocabulary Development

This session also established the **operational primitive vocabulary** that xenotypes compose:

### metaca-terminal-vocabulary-v2.md (7 Levels)

| Level | Scope | Contents |
|-------|-------|----------|
| 1 | Context | pred, self, succ, prev, phe, light-cone, hidden-state, global |
| 2 | Sigil Ops | bits, xor, and, or, shift, majority, blend, similarity, mutate |
| 3 | String Ops | entropy, diversity, allele-freq, pattern-match, change-rate |
| 4 | Composition | sequential (;), parallel (⊗), conditional (if/else), blend (α·A + β·B) |
| 5 | Hidden State | accum, threshold, trigger, bias, learn |
| 6 | Xenotype Wirings | hexagram families → primitives, energy → modulation |
| 7 | Evaluation | entropy, temporal, spatial, filament, envelope, regime, composite |

### Key Insight: Dual Evolution

Xenotypes have **two sides**:
- **Generation**: Which primitives compose, how they wire → produces dynamics
- **Evaluation**: Which metrics matter, how combined → scores dynamics

Both sides should be evolvable. Current scorers are hardcoded compositions of
primitives. Future xenoevolve should evolve both generator and scorer wirings.

## Part II Tasks

### Generation
- [ ] Run `evolve-global-rules` and verify it finds meaningful rules
- [ ] Compare local-physics runs to old-system runs
- [ ] Implement light-cone context (grandparents at t-2)
- [ ] Implement hidden state per cell
- [ ] Define 8 prototype xenotype wirings (one per hexagram family)

### Evaluation
- [ ] Audit existing scorers against Level 7 primitives
- [ ] Refactor scorers as primitive compositions
- [ ] Implement filament/envelope/regime primitives
- [ ] Define 4 prototype scorer wirings (one per primary energy)
- [ ] Enable evolution of scorer parameters

### Integration
- [ ] Xenoevolve evolves both generators and scorers
- [ ] Integrate with futon3a security layer (tripwire → xenotype activation)
- [ ] Connect to compass bridge with security awareness

## Vocabulary Documents

| Document | Purpose |
|----------|---------|
| `core-terminal-vocabulary.md` | AIF/GFE structure all domains instantiate |
| `metaca-terminal-vocabulary-v2.md` | MetaCA operational primitives (7 levels) |
| `ct-wiring-vocabulary.md` | CT vocabulary for wiring diagrams (mermaid) |

## Invariant Status

```
EXOTYPE INVARIANTS (1-4):  PASS
XENOTYPE INVARIANTS (5-6): PASS  <- Phase 1 complete!
XENOTYPE INVARIANTS (7-8): FAIL (expected - CT and scorer work)
```

Xenotype invariants:
- Invariant 5: Generator components + prototype wiring exist ✓
- Invariant 6: Wirings render to mermaid diagrams ✓
- Invariant 7: Wirings satisfy CT laws (associativity, identity) ✗
- Invariant 8: Scorer wirings are explicit primitive compositions ✗

## Phase 1 Complete: Generator Components

Created the following files:
- `resources/xenotype-generator-components.edn` - 50+ component definitions (Levels 2-5)
- `src/futon5/xenotype/generator.clj` - Component implementations
- `src/futon5/xenotype/mermaid.clj` - Mermaid diagram renderer
- `resources/xenotype-wirings/prototype-001-creative-peng.edn` - First prototype wiring

The core vocabulary establishes that AIF is the "xenotype to the xenotype" — the fixed
meta-policy within which xenotypes evolve. The MetaCA vocabulary provides the primitives
that xenotypes compose, for both generation and evaluation.

## To Resume

```bash
# Verify invariants still pass
clojure -M -m futon5.mmca.verify-invariants

# Run global rule evolution (new mode)
clojure -M -m futon5.mmca.xenoevolve --runs 100

# Run with local physics mode
clojure -M -e '
(require '[futon5.mmca.runtime :as mmca])
(mmca/run-mmca {:genotype "一二三四五六七八"
                :generations 50
                :exotype-mode :local-physics
                :global-rule :baldwin
                :bend-mode :blend})
'
```
