# Excursion 2: Hexagram to Sigil Promotion

**Thesis**: The 256 sigils can be systematically constructed as 64 hexagram
families × 4 variants, where the promotion rule is predictive and interpretable.

**Dependency**: Excursion 1 (validated hexagram families, CT interpretation pipeline).

**Outcome**: Full 256-sigil library with two-level interpretation (hexagram + variant).

---

## Conceptual Framework

### The Promotion Structure

```
Hexagram (6 bits) → 64 patterns (semantic core)
    +
Variant (2 bits)  → 4 modes per hexagram
    =
Sigil (8 bits)    → 256 operational patterns
```

Each sigil has a **hexagram parent** that provides semantic meaning, plus a
**variant mode** that specializes its operational behavior.

### Candidate Promotion Rules

#### Option A: Mode Bits (Recommended for first test)

| Bits | Mode | Interpretation |
|------|------|----------------|
| 00 | Observe | Hexagram as passive classifier (recognize regime) |
| 01 | Nudge | Hexagram as soft constraint (bias toward regime) |
| 10 | Steer | Hexagram as active controller (drive toward regime) |
| 11 | Gate | Hexagram as filter (block non-conforming states) |

#### Option B: Temporal Phase Bits

| Bits | Phase | Interpretation |
|------|-------|----------------|
| 00 | Genesis | Apply during initialization/early dynamics |
| 01 | Sustain | Apply during stable regime maintenance |
| 10 | Transition | Apply during regime shifts |
| 11 | Terminal | Apply during endgame/evaluation |

#### Option C: Polarity/Intensity Bits

| Bits | Polarity | Interpretation |
|------|----------|----------------|
| 00 | Yin-soft | Minimal intervention, receptive |
| 01 | Yin-firm | Moderate intervention, structured receptivity |
| 10 | Yang-soft | Moderate intervention, gentle action |
| 11 | Yang-firm | Maximal intervention, decisive action |

#### Option D: Spatial Context Bits

| Bits | Context | Interpretation |
|------|---------|----------------|
| 00 | Local-narrow | Consider immediate neighbors only |
| 01 | Local-wide | Consider extended neighborhood |
| 10 | Global-sample | Sample global state |
| 11 | Global-aggregate | Use global statistics |

---

## Stage 6b: Promotion Protocol Design

**Claim**: A principled promotion rule can generate 4 sigil variants from each
hexagram, where variants behave as predicted.

### Steps

1. **Select promotion rule**
   - Start with Option A (Mode bits) — most operationally distinct
   - Store in `futon5/resources/excursion-2-promotion-rule.edn`
   ```clojure
   {:rule :mode-bits
    :variants [{:bits "00" :mode :observe :behavior "classify regime"}
               {:bits "01" :mode :nudge :behavior "soft constraint"}
               {:bits "10" :mode :steer :behavior "active control"}
               {:bits "11" :mode :gate :behavior "filter/block"}]}
   ```

2. **Implement variant behaviors**
   - `futon5/src/futon5/xenotype/variants.clj`
   ```clojure
   (defmulti apply-variant (fn [mode hexagram-zone state] mode))

   (defmethod apply-variant :observe [_ zone state]
     ;; Return regime classification, no state change
     {:regime (classify-regime zone state)
      :action :none})

   (defmethod apply-variant :nudge [_ zone state]
     ;; Soft bias toward zone
     {:regime (classify-regime zone state)
      :action :bias
      :strength 0.3})

   (defmethod apply-variant :steer [_ zone state]
     ;; Active steering toward zone
     {:regime (classify-regime zone state)
      :action :steer
      :strength 0.7})

   (defmethod apply-variant :gate [_ zone state]
     ;; Block if outside zone
     {:regime (classify-regime zone state)
      :action (if (in-zone? zone state) :pass :block)})
   ```

3. **Select test hexagrams**
   - Use top 3-5 hexagrams from Excursion 1 Stage 4
   - Include tai (healthy), pi (stuck), qian (collapse) for contrast
   - Store in `futon5/resources/excursion-2-test-hexagrams.edn`

4. **Generate variant sigils**
   - For each test hexagram, generate 4 sigil variants
   - Total: 12-20 sigils for initial test
   ```bash
   bb -cp futon5/src:futon5/resources futon5/scripts/excursion_2_generate_variants.clj \
     --hexagrams futon5/resources/excursion-2-test-hexagrams.edn \
     --promotion-rule futon5/resources/excursion-2-promotion-rule.edn \
     --out futon5/resources/excursion-2-test-sigils.edn
   ```

### Artifacts

- `futon5/resources/excursion-2-promotion-rule.edn`
- `futon5/src/futon5/xenotype/variants.clj`
- `futon5/resources/excursion-2-test-hexagrams.edn`
- `futon5/resources/excursion-2-test-sigils.edn`

---

## Stage 6c: Variant Validation

**Claim**: The 4 variants of a hexagram form a coherent family with predictable
behavioral differences.

### Steps

1. **Run all test sigils on anchor seeds**
   - Use same 20 seeds from Excursion 1
   ```bash
   bb -cp futon5/src:futon5/resources futon5/scripts/excursion_2_stage_6c.clj \
     --seeds futon5/resources/excursion-1-anchor-seeds.edn \
     --sigils futon5/resources/excursion-2-test-sigils.edn \
     --out-dir /tmp/excursion-2-stage-6c
   ```

2. **Compute per-variant metrics**
   - 4-component health scores
   - Regime classification accuracy (for Observe mode)
   - Intervention frequency (for Nudge/Steer/Gate modes)
   - Regime recovery rate (for Steer mode)

3. **Test variant predictions**

   | Variant | Prediction | Test |
   |---------|------------|------|
   | Observe | No health improvement, accurate classification | health ≈ baseline, classification > 80% |
   | Nudge | Mild health improvement, low intervention | health > baseline, intervention < 30% |
   | Steer | Strong health improvement, moderate intervention | health >> baseline, intervention 40-60% |
   | Gate | Variable health, high filtering | depends on initial state distribution |

4. **Within-family coherence**
   - Do variants of the same hexagram cluster together?
   - Is cross-hexagram variance > within-hexagram variance?
   - ANOVA: hexagram effect vs variant effect vs interaction

5. **Ablation: swap variants**
   - Take tai-Steer, replace variant bits with kun-Steer
   - Does performance degrade?
   - This tests: is the hexagram or the variant doing the work?

### Success Criteria

- [ ] Variant behaviors match predictions (≥3 of 4 variants)
- [ ] Within-hexagram variants more similar than across-hexagram
- [ ] Ablation: swapping hexagram degrades more than swapping variant
- [ ] Promotion rule is predictive, not just organizational

### Artifacts

- `/tmp/excursion-2-stage-6c/`
- `/tmp/excursion-2-stage-6c/variant-analysis.edn`
- `/tmp/excursion-2-stage-6c/coherence-anova.edn`

---

## Stage 6d: Full 256 Generation

**Claim**: The validated promotion rule can generate all 256 sigils with
interpretable two-level structure.

### Steps

1. **Verify all 64 hexagrams have parameter mappings**
   - From Excursion 1 Stage 4 + extension
   - Fill gaps with interpolation or hexagram structure analysis
   ```bash
   bb -cp futon5/src:futon5/resources futon5/scripts/complete_hexagram_mappings.clj \
     --taxonomy /tmp/excursion-1-stage-4/hexagram-taxonomy.edn \
     --out futon5/resources/excursion-2-full-hexagram-mappings.edn
   ```

2. **Generate all 256 sigils**
   ```bash
   bb -cp futon5/src:futon5/resources futon5/scripts/generate_full_sigil_library.clj \
     --hexagrams futon5/resources/excursion-2-full-hexagram-mappings.edn \
     --promotion-rule futon5/resources/excursion-2-promotion-rule.edn \
     --out futon5/resources/sigil-library-256.edn
   ```

3. **Generate CT templates for all 256**
   - Compositional: `sigil-CT = hexagram-CT ∘ variant-functor`
   ```bash
   bb -cp futon5/src:futon5/resources futon5/scripts/generate_sigil_ct_templates.clj \
     --sigils futon5/resources/sigil-library-256.edn \
     --out-dir futon3/library/iiching
   ```

4. **Spot-check validation**
   - Sample 16 sigils (1 per quadrant × 4 variants)
   - Run on 10 seeds each
   - Verify regime matches prediction

### Success Criteria

- [ ] All 256 sigils generated with valid structure
- [ ] All 256 have CT templates
- [ ] Spot-check: ≥75% match predicted regime

### Artifacts

- `futon5/resources/excursion-2-full-hexagram-mappings.edn`
- `futon5/resources/sigil-library-256.edn`
- `futon3/library/iiching/exotype-*.flexiarg` (256 files)

---

## Stage 6e: Library Documentation

**Claim**: The 256-sigil library is documented with two-level interpretation
suitable for use in transfer experiments.

### Steps

1. **Generate library index**
   ```markdown
   # Sigil Library Index

   ## Hexagram Families

   ### 1. Qian (Creative) - Collapse regime
   - qian-observe (0x00): Classify collapse states
   - qian-nudge (0x01): Soft push toward collapse (diagnostic)
   - qian-steer (0x02): Active collapse induction (stress test)
   - qian-gate (0x03): Block non-collapse states

   ### 11. Tai (Peace) - Healthy regime
   - tai-observe (0x2C): Classify healthy states
   - tai-nudge (0x2D): Soft push toward health
   - tai-steer (0x2E): Active health maintenance
   - tai-gate (0x2F): Block unhealthy states

   ...
   ```

2. **Generate per-sigil documentation**
   - For each sigil: hexagram parent, variant mode, CT template, parameter zone
   - PSR template: "Use this sigil when..."
   - Known failure modes

3. **Generate transfer hints**
   - For each hexagram family: candidate mappings to other domains
   - tai → ant exploration/exploitation balance
   - qian → agent overcommitment detection
   - etc.

### Artifacts

- `futon5/docs/sigil-library-index.md`
- `futon5/docs/sigil-library-transfer-hints.md`

---

## Relationship to P2 and P3

### P2 (Abstract spec instantiates to MMCA)

The two-level sigil structure is the abstract spec:
- Hexagram = semantic pattern (domain-independent)
- Variant = operational mode (domain-specific instantiation)

P2 test: can the same hexagram + different variant functor produce equivalent
behavior in a different domain?

### P3 (Same spec instantiates to ants)

The transfer hints from Stage 6e become hypotheses for P3:
- tai-Steer in MMCA → exploration/exploitation balance in ants
- Does ant-tai-Steer improve foraging?

---

## Codex Action Items

### After Excursion 1 completes

- [ ] Implement `futon5/src/futon5/xenotype/variants.clj`
- [ ] Create promotion rule config
- [ ] Select test hexagrams from E1 Stage 4 results

### Stage 6c

- [ ] Implement variant validation script
- [ ] Run variant comparison experiments
- [ ] Analyze within-family coherence

### Stage 6d

- [ ] Complete hexagram mappings for all 64
- [ ] Generate full 256 sigil library
- [ ] Generate all CT templates

### Stage 6e

- [ ] Write library documentation
- [ ] Generate transfer hints for P3

---

## Risk Mitigation

### Risk: Promotion rule is not predictive

**Mitigation**: Stage 6c explicitly tests predictions. If variants don't behave
as predicted, try alternative promotion rules (Options B, C, D) or learn the
promotion rule from data.

### Risk: Some hexagrams don't map to useful regimes

**Mitigation**: Excursion 1 Stage 4 identifies which hexagrams are useful.
Non-useful hexagrams can still have sigils, but marked as "diagnostic" or
"contrast" rather than "healthy."

### Risk: 256 is too many to validate individually

**Mitigation**: Validate at the hexagram level (64) + spot-check at sigil level.
The promotion rule provides structure that reduces validation burden.

---

## Summary: Excursion 2 Deliverables

1. **Validated promotion rule** (Stage 6b)
2. **Variant behavior characterization** (Stage 6c)
3. **Full 256-sigil library** (Stage 6d)
4. **Two-level documentation** (Stage 6e)
5. **Transfer hints for P3** (Stage 6e)

**Estimated effort**: ~30% of Excursion 1 (leverages E1 infrastructure and results).
