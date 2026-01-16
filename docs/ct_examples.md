# CT Examples (MMCA)

These sketches connect the hand-crafted MMCA operators to the minimal
CT DSL in `src/futon5/ct/dsl.clj`. They are intentionally small and
executable as data, not proofs.

## Conventions

- Objects are symbolic shapes: `:genotype`, `:phenotype`, `:kernel`,
  `:operator-set`, `:selector`, `:motif`.
- Morphisms are operator names such as `:blend-hand` or `:genotype-gate`.
- Diagrams are built with `primitive`, `compose`, and `tensor` forms.

## Kernel rationale wiring

Map the 36-bit local kernel into design-pattern roles:

- IF = left sigil
- THEN = current sigil
- HOWEVER = right sigil
- BECAUSE = phenotype context
- NEXT STEPS = output sigil

```clojure
(require '[futon5.ct.dsl :as ct])

(def kernel-step
  (ct/primitive-diagram {:name :kernel-step
                         :domain [:if :then :however :because]
                         :codomain [:next-steps]}))

;; Optional: contextualize a design-pattern into the IF/THEN/HOWEVER frame.
(def frame-pattern
  (ct/primitive-diagram {:name :frame
                         :domain [:pattern]
                         :codomain [:if :then :however]}))

(def justify
  (ct/primitive-diagram {:name :justify
                         :domain [:pattern :rationale]
                         :codomain [:because]}))

(def pattern-to-next
  (ct/compose-diagrams
    (ct/tensor-diagrams frame-pattern justify)
    kernel-step))
```

## Tensor ops ↔ design patterns

Relate the tensor-function operators to design-pattern roles. These are
intentional analogies; they keep the CT diagrams legible across domains.

- BlendHand → Strategy/Policy Mixer (`:kernel ⊗ :kernel ⊗ :selector → :kernel`)
- Genotype Gate → Guard/Filter (`:genotype ⊗ :genotype ⊗ :phenotype → :genotype`)
- EntropyPulse → Temporal Switch / Phase Scheduler (`:kernel ⊗ :selector → :kernel`)
- Uplift Operator → Factory/Observer (`:genotype ⊗ :phenotype ⊗ :operator-set → :operator-set`)
- Exotype Kernel Context → Template Method / Test Harness
  (`[:if :then :however :because] → :next-steps`)

The CT `tensor` captures “fork/join” structure (parallel inputs), while
`compose` captures pipeline updates. When describing new tensor ops, start
with the pattern label, then the CT signature, then the operator name.

## BlendHand then evolve

Blend two kernels into one and evolve the field.

```clojure
(require '[futon5.ct.dsl :as ct])

(def blend-hand
  (ct/primitive-diagram {:name :blend-hand
                         :domain [:kernel :kernel :selector]
                         :codomain [:kernel]}))

(def evolve
  (ct/primitive-diagram {:name :evolve
                         :domain [:genotype :phenotype :kernel]
                         :codomain [:genotype :phenotype]}))

(def blend-then-evolve
  (ct/compose-diagrams
    (ct/tensor-diagrams
      (ct/identity-diagram :genotype)
      (ct/identity-diagram :phenotype)
      blend-hand)
    evolve))
```

## Genotype Gate after evolve

Compute `G_new` then gate it with phenotype bits.

```clojure
(def genotype-gate
  (ct/primitive-diagram {:name :genotype-gate
                         :domain [:genotype :genotype :phenotype]
                         :codomain [:genotype]}))

(def evolve-then-gate
  (ct/compose-diagrams
    evolve
    genotype-gate))
```

## Uplift operator set

Lift motifs into operator proposals.

```clojure
(def uplift
  (ct/primitive-diagram {:name :uplift-operator
                         :domain [:genotype :phenotype :operator-set]
                         :codomain [:operator-set]}))
```

## Freeze genotype / lock kernel

Treat these as identity morphisms on `:genotype` and `:kernel`.

```clojure
(def freeze-genotype (ct/identity-diagram :genotype))
(def lock-kernel (ct/identity-diagram :kernel))
```

## Adapter example (split/merge)

Use CT adaptors to split rule bits into halves and merge later.

```clojure
(require '[futon5.ct.adapters :as adapters])

(def split-rule (get-in (adapters/adaptor :rule/bits :split-half) [:diagram]))
(def merge-rule (get-in (adapters/adaptor :rule/bits :merge-half) [:diagram]))

(def split-then-merge
  (ct/compose-diagrams split-rule merge-rule))
```

## Compiling to a score (sketch)

`futon5.mmca.score/compile-score` converts a diagram into a runnable
MMCA plan, using a map from diagram primitive names to operator specs.

```clojure
(require '[futon5.mmca.score :as score])

(def operators
  {:blend-hand {:name :blend-hand}
   :evolve {:name :evolve}
   :genotype-gate {:name :genotype-gate}
   :uplift-operator {:name :uplift-operator}})

(score/compile-score
  {:diagram blend-then-evolve
   :operators operators
   :genotype "一一一一一"} )
```
