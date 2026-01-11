# CT Examples (MMCA)

These sketches connect the hand-crafted MMCA operators to the minimal
CT DSL in `src/futon5/ct/dsl.clj`. They are intentionally small and
executable as data, not proofs.

## Conventions

- Objects are symbolic shapes: `:genotype`, `:phenotype`, `:kernel`,
  `:operator-set`, `:selector`, `:motif`.
- Morphisms are operator names such as `:blend-hand` or `:genotype-gate`.
- Diagrams are built with `primitive`, `compose`, and `tensor` forms.

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
